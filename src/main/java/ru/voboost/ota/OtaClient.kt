package ru.voboost.ota

import ru.voboost.BuildConfig
import ru.voboost.Logger
import ru.voboost.Paths
import java.io.File

/**
 * OTA client orchestrator (APK-level).
 *
 * Coordinates the complete OTA update flow for the two voboost APKs:
 *
 * 1. fetch + verify the signed release manifest (ed25519 detached);
 * 2. compare the installed app version ([BuildConfig.VERSION_NAME]) and the
 *    installed daemon version (read from `inject-status.json`) against the
 *    manifest;
 * 3. download each newer APK **whole** (size pre-check + sha256 verify);
 * 4. apply per channel:
 *    - **app** → stage + invoke the system installer to replace the running app;
 *    - **core** → stage + create the single-use `core-update-ready` marker
 *      (the daemon self-updates; the app never installs the daemon).
 *
 * A manifest that fails signature or structural validation is never persisted
 * as the current manifest — the client keeps the last good one.
 *
 * Transient errors are retried with exponential backoff.
 */
class OtaClient(
    private val paths: Paths,
    private val config: OtaConfig,
) {
    companion object {
        private const val LOG = "OtaClient"
        private const val BASE_RETRY_DELAY_MS = 1000L

        /** Current-manifest file name in the app zone (last good manifest). */
        private const val CURRENT_MANIFEST_NAME = "current-release-manifest.json"
    }

    private val verifier: OtaVerifier
    private val downloader: OtaDownloader
    private val stager: ApkStager

    init {
        verifier =
            if (config.publicKeyFile != null) {
                OtaVerifier.fromPublicKeyFile(config.publicKeyFile)
            } else if (config.publicKeyPem != null) {
                OtaVerifier.fromPublicKeyPem(config.publicKeyPem)
            } else {
                throw OtaException("No public key provided")
            }

        downloader = OtaDownloader()
        stager = ApkStager(paths, config.context)
    }

    /**
     * Check for updates and apply if available.
     *
     * @return true if any APK was staged/applied, false if no updates
     * @throws OtaException if an unrecoverable update failure occurs
     */
    fun checkAndUpdate(): Boolean {
        Logger.info(LOG, "Checking for updates...")

        return try {
            // 1. Fetch + verify the signed manifest. A failed manifest is never
            //    persisted (D6): the last good manifest is kept.
            val manifest = fetchAndVerifyManifest()

            // 2. Determine which channels have a newer APK.
            val currentAppVersion = config.currentAppVersion
            val currentDaemonVersion = config.readCurrentDaemonVersion()

            val appEntry = manifest.getEntryByChannel(Channel.APP)
            val coreEntry = manifest.getEntryByChannel(Channel.CORE)

            val appUpdateAvailable = isChannelUpdate(appEntry, currentAppVersion)
            val coreUpdateAvailable = isChannelUpdate(coreEntry, currentDaemonVersion)

            if (!appUpdateAvailable && !coreUpdateAvailable) {
                Logger.info(
                    LOG,
                    "No updates available (app=$currentAppVersion, core=$currentDaemonVersion)",
                )
                // Still persist the verified manifest as the last good one.
                persistCurrentManifest(manifest)
                return false
            }

            // 3. Download + apply each newer APK per channel.
            var applied = false

            if (appUpdateAvailable && appEntry != null) {
                Logger.info(LOG, "App update available: ${appEntry.version} > $currentAppVersion")
                downloadAndApplyApp(appEntry)
                applied = true
            }

            if (coreUpdateAvailable && coreEntry != null) {
                Logger.info(
                    LOG,
                    "Core update available: ${coreEntry.version} > $currentDaemonVersion",
                )
                downloadAndApplyCore(coreEntry)
                applied = true
            }

            // Persist the verified manifest as the last good one.
            persistCurrentManifest(manifest)

            Logger.info(LOG, "Update flow complete (applied=$applied)")
            applied
        } catch (e: Exception) {
            Logger.error(LOG, "Update failed: ${e.message}")
            if (e is OtaException) throw e
            throw OtaException("Update failed: ${e.message}", e)
        }
    }

    /**
     * Check for updates without applying.
     *
     * @return true if any channel has a newer APK
     */
    fun checkForUpdates(): Boolean {
        return try {
            val manifest = fetchAndVerifyManifest()

            val currentAppVersion = config.currentAppVersion
            val currentDaemonVersion = config.readCurrentDaemonVersion()

            val appEntry = manifest.getEntryByChannel(Channel.APP)
            val coreEntry = manifest.getEntryByChannel(Channel.CORE)

            val appUpdateAvailable = isChannelUpdate(appEntry, currentAppVersion)
            val coreUpdateAvailable = isChannelUpdate(coreEntry, currentDaemonVersion)

            appUpdateAvailable || coreUpdateAvailable
        } catch (e: Exception) {
            Logger.error(LOG, "Update check failed: ${e.message}")
            false
        }
    }

    /**
     * Fetch and verify the release manifest.
     *
     * The manifest is verified (signature + structure + bounds) before use.
     * A failed manifest is never persisted.
     */
    private fun fetchAndVerifyManifest(): ReleaseManifest {
        return retryWithBackoff("fetch manifest") {
            val manifestUrl = "${config.baseUrl}/release-manifest.json"
            val signatureUrl = "${config.baseUrl}/release-manifest.sig"

            Logger.debug(LOG, "Fetching manifest from: $manifestUrl")

            val manifestContent = downloader.downloadBytes(manifestUrl)
            // Signature is bounded to MAX_SIGNATURE_BYTES (256 B) at the
            // download layer so a malicious server cannot OOM the app
            // with a multi-GB ".sig" (R3-VBS-03).
            val signatureContent =
                downloader.downloadBytes(
                    signatureUrl,
                    OtaDownloader.MAX_SIGNATURE_BYTES,
                )

            // verify() enforces size bounds, signature, and structural validation.
            // It throws on failure — the manifest is never persisted in that case.
            verifier.verify(manifestContent, signatureContent)
        }
    }

    /**
     * Download and apply an app-channel APK.
     *
     * Stages the verified APK and invokes the system installer to replace the
     * running app. No marker is created for the app channel.
     */
    private fun downloadAndApplyApp(entry: ReleaseFileEntry) {
        val apkFile = downloadApk(entry)
        stager.stageAppApk(apkFile, entry)
        apkFile.delete()
    }

    /**
     * Download and apply a core-channel APK.
     *
     * Stages the verified daemon APK into `staging/voboost-inject.apk` and
     * creates the single-use `core-update-ready` marker last. The app does NOT
     * install the daemon — the daemon self-updates.
     */
    private fun downloadAndApplyCore(entry: ReleaseFileEntry) {
        val apkFile = downloadApk(entry)
        stager.stageCoreApk(apkFile, entry)
        apkFile.delete()
    }

    /**
     * Download a whole APK with size pre-check and sha256 verify.
     *
     * The downloaded APK is written to a temp downloads directory and verified
     * (size before hashing, then sha256) by [OtaDownloader].
     */
    private fun downloadApk(entry: ReleaseFileEntry): File {
        return retryWithBackoff("download APK ${entry.path}") {
            val tempDir = File(paths.stagingDir, "downloads")
            tempDir.mkdirs()
            val targetFile = File(tempDir, entry.path.substringAfterLast('/'))
            downloader.downloadFile(entry, config.baseUrl, targetFile)
            targetFile
        }
    }

    /**
     * Persist the verified manifest as the last good manifest.
     *
     * Only called after the manifest has passed verification. A failed
     * manifest never reaches this method (D6).
     */
    private fun persistCurrentManifest(manifest: ReleaseManifest) {
        try {
            val manifestFile = File(paths.stagingDir, CURRENT_MANIFEST_NAME)
            manifestFile.parentFile?.mkdirs()
            manifestFile.writeText(manifest.toJson().toString(2))
            Logger.debug(LOG, "Persisted current manifest: ${manifest.version}")
        } catch (e: Exception) {
            // Persisting the last-good manifest is best-effort; a failure here
            // does not invalidate an already-applied update.
            Logger.error(LOG, "Failed to persist current manifest: ${e.message}")
        }
    }

    /**
     * Whether the manifest entry's version is newer than the installed version.
     */
    private fun isChannelUpdate(
        entry: ReleaseFileEntry?,
        installedVersion: String?,
    ): Boolean {
        return entry?.let { OtaVersion.isUpdateAvailable(installedVersion, it.version) } ?: false
    }

    /**
     * Retry operation with exponential backoff.
     */
    private fun <T> retryWithBackoff(
        operation: String,
        block: () -> T,
    ): T {
        val maxRetries = config.maxRetries
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Logger.debug(LOG, "$operation failed (attempt $attempt/$maxRetries): ${e.message}")

                if (attempt < maxRetries) {
                    val delay = BASE_RETRY_DELAY_MS * (1 shl (attempt - 1))
                    Logger.debug(LOG, "Retrying in ${delay}ms...")
                    Thread.sleep(delay)
                }
            }
        }

        throw OtaException("$operation failed after $maxRetries attempts", lastException)
    }
}

/**
 * OTA client configuration.
 *
 * @param baseUrl Base URL where `release-manifest.json` / `.sig` and the APKs
 *   are served.
 * @param publicKeyFile Optional file containing the release public key PEM.
 * @param publicKeyPem Optional release public key PEM content (used if no file).
 * @param currentAppVersion The installed app version (BuildConfig.VERSION_NAME).
 * @param daemonVersionReader Optional reader returning the installed daemon
 *   version (extracted from `inject-status.json`'s `daemon` field). May return
 *   null when the daemon is not yet installed.
 * @param context Optional Android context, used by [ApkStager] to issue the
 *   app-channel install intent. Null in JVM tests.
 */
data class OtaConfig(
    val baseUrl: String,
    val publicKeyFile: File? = null,
    val publicKeyPem: String? = null,
    val currentAppVersion: String,
    val daemonVersionReader: () -> String? = { null },
    val context: android.content.Context? = null,
    /** Max retry attempts for transient network errors (default 3). */
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
) {
    /**
     * Read the current installed daemon version, or null if unavailable.
     */
    fun readCurrentDaemonVersion(): String? = daemonVersionReader()

    companion object {
        /** Default retry count for transient network errors. */
        const val DEFAULT_MAX_RETRIES = 3
    }
}
