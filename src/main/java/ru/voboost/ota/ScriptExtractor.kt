package ru.voboost.ota

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONObject
import ru.voboost.Logger
import ru.voboost.Paths
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Extracts the Frida agents and the per-agent manifest embedded in the app's
 * APK assets into the app-zone staging directory so the voboost-inject daemon
 * can consume them.
 *
 * The agents ship under `assets/agents/` (populated by the `copyAgents` build
 * task). The daemon cannot read APK assets directly and expects a stable,
 * well-known on-disk location (`staging/agents/` + `staging/agents-manifest.json`).
 * This extractor materializes those assets to disk.
 *
 * ## Idempotency
 *
 * Extraction is gated by a **content fingerprint**: a sha256 over the raw
 * bytes of `assets/agents/agents-manifest.json`, persisted at
 * `appZone/agents-extracted-version`. The manifest's `version` field is a
 * schema version (not bumped on agent edits), so it cannot gate re-extraction.
 *
 * A call is a no-op only when BOTH hold:
 * 1. the persisted fingerprint equals the current manifest hash, and
 * 2. every agent `.js` listed in the manifest exists in `staging/agents/`.
 *
 * The presence check (2) is self-healing: a `.js` deleted out-of-band while
 * the manifest is unchanged triggers a full re-extract.
 *
 * ## Atomic writes
 *
 * Every file (agent `.js`, manifest, signature, marker, fingerprint) is written
 * via temp + fsync + rename in its target directory, so a crash never leaves a
 * half-written file visible under the final name. The `agents-update-ready`
 * marker is created **last**, after all data files are fsynced, so its presence
 * implies a complete staged set.
 *
 * The fingerprint lives under the app zone (not staging) because
 * `ApkStager.cleanStaging()` deletes the staging tree; a staging-local
 * fingerprint would be wiped on every OTA stage.
 *
 * @param paths Path resolution for the app zone and staging directory.
 * @param context Android context used for `assets/`.
 * @param assetReader Returns the bytes of a named asset, or null if the asset is
 *   absent. Defaults to `context.assets`; overridable in tests to inject fake
 *   assets without going through Robolectric's merged-asset resolution.
 */
class ScriptExtractor(
    private val paths: Paths,
    private val context: Context,
    private val assetReader: (String) -> ByteArray? = { path ->
        readAssetFromContext(context, path)
    },
) {
    companion object {
        private const val LOG = "ScriptExtractor"

        /** Path of the agents manifest inside the APK assets. */
        private const val MANIFEST_ASSET = "agents/agents-manifest.json"

        /** Detached signature asset; copied only when present (optional). */
        private const val MANIFEST_SIG_ASSET = "agents/agents-manifest.json.sig"

        /** Staging-relative path for the copied manifest. */
        private const val MANIFEST_STAGING = "agents-manifest.json"

        /** Staging-relative path for the copied manifest signature. */
        private const val MANIFEST_SIG_STAGING = "agents-manifest.json.sig"

        /** Subdirectory of staging holding the agent `.js` files. */
        private const val AGENTS_STAGING_DIR = "agents"

        /**
         * Single-use marker signalling the daemon that a complete, consistent
         * agent set is staged. Created last; the daemon consumes (deletes) it.
         */
        const val AGENTS_UPDATE_READY_MARKER = "agents-update-ready"

        /**
         * Name of the file under the app zone holding the last-extracted
         * manifest fingerprint. Stored outside staging so it survives
         * `ApkStager.cleanStaging()`.
         */
        const val EXTRACTED_VERSION_FILE = "agents-extracted-version"
    }

    /**
     * Extract the embedded agents to staging if the manifest content has
     * changed or any staged `.js` is missing.
     *
     * Idempotent: when the persisted fingerprint matches the current manifest
     * hash and every listed agent exists in staging, this is a no-op (no copy,
     * no marker recreation). Otherwise it re-extracts the full set, creates the
     * `agents-update-ready` marker last, and persists the new fingerprint.
     *
     * The optional `agents-manifest.json.sig` is copied only when present; its
     * absence is not an error. The signature is NOT verified here — that is the
     * daemon's responsibility.
     *
     * @return `Result.success(Unit)` on completion or a no-op;
     *   `Result.failure(...)` if the manifest asset cannot be read or parsing
     *   fails. Never throws.
     */
    fun extractIfChanged(): Result<Unit> {
        return try {
            val manifestBytes = readAssetBytes(MANIFEST_ASSET)
                ?: return Result.failure(
                    OtaException("Agents manifest asset not found: $MANIFEST_ASSET"),
                )

            val assetsSha = sha256Hex(manifestBytes)
            val storedSha = readStoredFingerprint()

            val agentFiles = parseAgentFiles(manifestBytes)
            if (agentFiles.isEmpty()) {
                return Result.failure(
                    OtaException("Agents manifest lists no agents"),
                )
            }

            if (storedSha == assetsSha && allAgentsStaged(agentFiles)) {
                Logger.debug(LOG, "Agents unchanged (sha=$assetsSha), skipping extraction")
                return Result.success(Unit)
            }

            Logger.info(
                LOG,
                "Extracting agents to staging (stored=${storedSha ?: "none"}, assets=$assetsSha)",
            )

            val agentsStagingDir = File(paths.stagingDir, AGENTS_STAGING_DIR)
            agentsStagingDir.mkdirs()

            // Copy each agent .js asset -> staging/agents/<basename>.
            for (assetPath in agentFiles) {
                val basename = assetPath.substringAfterLast('/')
                val assetBytes = readAssetBytes(assetPath)
                if (assetBytes == null) {
                    return Result.failure(
                        OtaException("Agent asset not found: $assetPath"),
                    )
                }
                writeBytesAtomically(assetBytes, File(agentsStagingDir, basename))
            }

            // Copy the manifest.
            writeBytesAtomically(manifestBytes, File(paths.stagingDir, MANIFEST_STAGING))

            // Copy the optional detached signature.
            val sigBytes = readAssetBytes(MANIFEST_SIG_ASSET)
            if (sigBytes != null) {
                writeBytesAtomically(sigBytes, File(paths.stagingDir, MANIFEST_SIG_STAGING))
            } else {
                Logger.debug(LOG, "Agents manifest signature asset absent; skipping")
            }

            // Marker LAST: only present after the full set is fsynced.
            createAgentsUpdateReadyMarker()

            // Persist the fingerprint last of all so a crash before this point
            // leaves storedSha stale and forces a re-extract on next run.
            writeFingerprintAtomically(assetsSha)

            Logger.info(LOG, "Agents extracted and marker created")
            Result.success(Unit)
        } catch (e: OtaException) {
            Logger.error(LOG, "Agent extraction failed: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Logger.error(LOG, "Agent extraction failed: ${e.message}")
            Result.failure(OtaException("Agent extraction failed: ${e.message}", e))
        }
    }

    /**
     * Parse the manifest bytes and return the list of `agents[].file` values
     * (asset paths like `agents/<name>.js`).
     *
     * @throws OtaException if the JSON is malformed or has no `agents` array.
     */
    private fun parseAgentFiles(manifestBytes: ByteArray): List<String> {
        return try {
            val json = JSONObject(String(manifestBytes, Charsets.UTF_8))
            val arr = json.getJSONArray("agents")
            (0 until arr.length()).mapNotNull { i ->
                arr.getJSONObject(i).optString("file").takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            throw OtaException("Invalid agents manifest: ${e.message}", e)
        }
    }

    /**
     * True when every agent's basename exists in `staging/agents/`.
     */
    private fun allAgentsStaged(agentFiles: List<String>): Boolean {
        val agentsStagingDir = File(paths.stagingDir, AGENTS_STAGING_DIR)
        return agentFiles.all { File(agentsStagingDir, it.substringAfterLast('/')).exists() }
    }

    /**
     * Read the persisted manifest fingerprint, or null if absent.
     */
    private fun readStoredFingerprint(): String? {
        val file = File(paths.appZone, EXTRACTED_VERSION_FILE)
        return if (file.exists()) file.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() } else null
    }

    /**
     * Read a raw asset into a byte array, or null if the asset is absent.
     *
     * Delegates to [assetReader] so tests can inject fake assets. Translates an
     * underlying IOException other than absence into an [OtaException].
     *
     * @throws OtaException on a read error other than absence.
     */
    private fun readAssetBytes(assetPath: String): ByteArray? {
        return try {
            assetReader(assetPath)
        } catch (e: java.io.FileNotFoundException) {
            null
        } catch (e: java.io.IOException) {
            throw OtaException("Failed to read asset '$assetPath': ${e.message}", e)
        }
    }

    /**
     * Write [bytes] to [target] atomically: temp file in the target directory,
     * fsync, then rename over the final name. Creates parent directories.
     *
     * @throws OtaException if the write or rename fails.
     */
    private fun writeBytesAtomically(
        bytes: ByteArray,
        target: File,
    ) {
        target.parentFile?.mkdirs()

        val tempFile = File(target.parentFile, "${target.name}.${System.currentTimeMillis()}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.fd.sync() // fsync the bytes
            }
            if (!tempFile.renameTo(target)) {
                throw OtaException("Failed to rename temp file: $tempFile -> $target")
            }
        } catch (e: OtaException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw OtaException("Failed to write $target: ${e.message}", e)
        }
    }

    /**
     * Create the `agents-update-ready` marker atomically (temp + fsync + rename).
     *
     * @throws OtaException if the marker cannot be created.
     */
    private fun createAgentsUpdateReadyMarker() {
        val markerFile = File(paths.stagingDir, AGENTS_UPDATE_READY_MARKER)
        writeBytesAtomically("ready".toByteArray(Charsets.UTF_8), markerFile)
        Logger.debug(LOG, "$AGENTS_UPDATE_READY_MARKER marker created")
    }

    /**
     * Persist the manifest fingerprint under the app zone atomically.
     *
     * @throws OtaException if the write fails.
     */
    private fun writeFingerprintAtomically(sha: String) {
        writeBytesAtomically(sha.toByteArray(Charsets.UTF_8), File(paths.appZone, EXTRACTED_VERSION_FILE))
    }

    /**
     * Compute the lowercase-hex sha256 of [bytes].
     */
    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Read an asset by name from a [Context]'s [AssetManager], or null if absent.
 *
 * Top-level so it can be referenced as the default value of
 * [ScriptExtractor]'s `assetReader` constructor parameter.
 *
 * @throws java.io.IOException on a read error other than absence.
 */
private fun readAssetFromContext(
    context: Context,
    path: String,
): ByteArray? {
    return try {
        context.assets.open(path).use { it.readBytes() }
    } catch (e: java.io.FileNotFoundException) {
        null
    }
}

