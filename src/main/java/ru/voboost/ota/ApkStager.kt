package ru.voboost.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ru.voboost.Logger
import ru.voboost.Paths
import java.io.File
import java.io.FileOutputStream

/**
 * Stages whole APK files to the app-zone staging directory and applies them
 * per channel.
 *
 * APK-level OTA only — there is no per-file or per-resource staging.
 *
 * - **app** channel: stages the verified APK into `staging/` and invokes the
 *   system installer (install intent) to replace the running app. There is no
 *   apply marker for the app channel — the apply ends in the install intent.
 * - **core** channel: stages the verified daemon APK into
 *   `staging/voboost-inject.apk` and creates the single-use `core-update-ready`
 *   marker as the **last** step, signalling voboost-inject to self-update. The
 *   app never installs the daemon — the daemon verifies, replaces, and restarts
 *   itself.
 *
 * @param paths Path resolution for the app zone.
 * @param context Android context used to issue the app-channel install intent.
 *   May be null in tests/JVM where install intents are not exercised.
 */
class ApkStager(
    private val paths: Paths,
    private val context: Context? = null,
) {
    companion object {
        private const val LOG = "ApkStager"

        /** Single-use marker signalling voboost-inject to self-update. */
        const val CORE_UPDATE_READY_MARKER = "core-update-ready"

        /** Fixed staging name for the daemon APK (per the voboost-inject contract). */
        const val CORE_APK_NAME = "voboost-inject.apk"

        /** FileProvider authority for sharing staged APKs with the installer. */
        private const val FILE_PROVIDER_AUTHORITY = "ru.voboost.fileprovider"
    }

    /**
     * Stage an app-channel APK and invoke the system installer.
     *
     * The verified APK is staged into `staging/<entry.path>` and an
     * `ACTION_INSTALL_PACKAGE` (or `ACTION_VIEW` fallback) intent is issued to
     * replace the running app. No marker is created for the app channel.
     *
     * @param apkFile Downloaded, verified APK file
     * @param entry Release file entry describing the APK
     * @return the staged APK file
     * @throws OtaException if staging fails
     */
    fun stageAppApk(
        apkFile: File,
        entry: ReleaseFileEntry,
    ): File {
        Logger.info(LOG, "Staging app APK: ${entry.path}")

        val targetFile = stageApk(apkFile, entry.path)

        invokeAppInstaller(targetFile)

        Logger.info(LOG, "App APK staged and installer invoked: ${targetFile.absolutePath}")
        return targetFile
    }

    /**
     * Stage a core-channel APK and create the single-use marker last.
     *
     * The verified daemon APK is staged into `staging/$CORE_APK_NAME`
     * (per the voboost-inject contract) and the `core-update-ready` marker is
     * created as the **last** step, signalling voboost-inject to self-update.
     * The app does NOT install the daemon.
     *
     * The marker is single-use: the daemon consumes (deletes) it after any
     * apply attempt, so a successful self-update does not loop.
     *
     * @param apkFile Downloaded, verified daemon APK file
     * @param entry Release file entry describing the APK
     * @return the staged daemon APK file
     * @throws OtaException if staging fails
     */
    fun stageCoreApk(
        apkFile: File,
        entry: ReleaseFileEntry,
    ): File {
        Logger.info(LOG, "Staging core APK: ${entry.path}")

        // Stage to the fixed daemon APK name expected by voboost-inject.
        val targetFile = stageApk(apkFile, CORE_APK_NAME)

        Logger.debug(LOG, "Core APK staged: ${targetFile.absolutePath}")

        // Create the single-use marker LAST (atomic, after the APK is fsynced).
        createCoreUpdateReadyMarker()

        Logger.info(LOG, "Core APK staged and marker created")
        return targetFile
    }

    /**
     * Stage an APK to the staging directory atomically (temp + fsync + rename).
     *
     * @param source Downloaded, verified APK file
     * @param targetName File name within the staging directory
     * @return the staged APK file
     */
    private fun stageApk(
        source: File,
        targetName: String,
    ): File {
        val stagingDir = paths.stagingDir
        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }

        val targetFile = File(stagingDir, targetName)
        writeFileAtomically(source, targetFile)
        return targetFile
    }

    /**
     * Write file atomically with fsync.
     *
     * Writes to a temp file in the same directory, fsyncs, then atomically
     * renames over the target. This provides crash consistency: a partially
     * written APK is never visible under the target name.
     */
    private fun writeFileAtomically(
        source: File,
        target: File,
    ) {
        target.parentFile?.mkdirs()

        val tempFile = File(target.parentFile, "${target.name}.${System.currentTimeMillis()}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
                output.fd.sync() // fsync the APK bytes
            }

            // Atomic rename
            if (!tempFile.renameTo(target)) {
                throw OtaException("Failed to rename temp file: $tempFile -> $target")
            }
        } catch (e: OtaException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw OtaException("Failed to stage APK: ${e.message}", e)
        }
    }

    /**
     * Create the core-update-ready marker LAST.
     *
     * The marker is created only after the daemon APK is written and fsynced,
     * so its presence is a reliable signal that a complete, verified daemon APK
     * is staged. Single-use: the daemon consumes (deletes) it after apply.
     *
     * Written atomically (temp + rename) so the marker never appears partially.
     */
    private fun createCoreUpdateReadyMarker() {
        val markerFile = File(paths.stagingDir, CORE_UPDATE_READY_MARKER)
        val tempFile =
            File(paths.stagingDir, "$CORE_UPDATE_READY_MARKER.${System.currentTimeMillis()}.tmp")

        try {
            // Write marker content and fsync in a single stream so the temp
            // file holds the full content before the atomic rename.
            FileOutputStream(tempFile).use { output ->
                output.write("ready".toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!tempFile.renameTo(markerFile)) {
                throw OtaException("Failed to create core-update-ready marker")
            }
            Logger.debug(LOG, "Core-update-ready marker created")
        } catch (e: Exception) {
            tempFile.delete()
            if (e is OtaException) throw e
            throw OtaException("Failed to create marker: ${e.message}", e)
        }
    }

    /**
     * Invoke the system installer for an app-channel APK.
     *
     * Uses an `ACTION_INSTALL_PACKAGE` / `ACTION_VIEW` intent with a
     * FileProvider content URI. Requires a non-null [context]; if no context
     * is available (e.g. tests), the staging succeeds but no intent is issued.
     */
    private fun invokeAppInstaller(apkFile: File) {
        val ctx = context
        if (ctx == null) {
            Logger.debug(LOG, "No context: skipping install intent for ${apkFile.name}")
            return
        }

        try {
            val uri =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(ctx, FILE_PROVIDER_AUTHORITY, apkFile)
                } else {
                    @Suppress("DEPRECATION")
                    Uri.fromFile(apkFile)
                }

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            ctx.startActivity(intent)
            Logger.info(LOG, "Install intent issued for ${apkFile.name}")
        } catch (e: Exception) {
            throw OtaException("Failed to invoke installer: ${e.message}", e)
        }
    }

    /**
     * Check if the core-update-ready marker exists.
     */
    fun hasCoreUpdateReadyMarker(): Boolean {
        return File(paths.stagingDir, CORE_UPDATE_READY_MARKER).exists()
    }

    /**
     * Clear the core-update-ready marker (for testing/cleanup).
     */
    fun clearCoreUpdateReadyMarker() {
        val markerFile = File(paths.stagingDir, CORE_UPDATE_READY_MARKER)
        if (markerFile.exists()) {
            markerFile.delete()
            Logger.debug(LOG, "Core-update-ready marker cleared")
        }
    }

    /**
     * Clean the staging directory.
     */
    fun cleanStaging() {
        paths.stagingDir.deleteRecursively()
        paths.stagingDir.mkdirs()
        Logger.debug(LOG, "Staging directory cleaned")
    }
}
