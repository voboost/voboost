package ru.voboost.ota

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.voboost.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads files from release manifest with integrity verification.
 *
 * Downloads files whole (no binary diff), verifies size pre-check,
 * then verifies sha256 hash.
 *
 * Supports both `https://` (and any HTTP scheme OkHttp accepts) and `file://`
 * URLs. The `file://` scheme reads from the local filesystem (used for local
 * emulator testing with no network and no CDN cache); the same size+sha256
 * enforcement applies to both schemes.
 */
class OtaDownloader(
    val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val LOG = "OtaDownloader"
        private const val DOWNLOAD_TIMEOUT_SECONDS = 30L
        private const val BUFFER_SIZE = 8 * 1024

        // Size caps for downloadBytes (manifest + signature). These bound
        // the in-memory allocation before the verifier's own size check
        // runs, so a malicious server returning a multi-GB "manifest"
        // cannot OOM the app (R3-VBS-03). The manifest cap mirrors
        // ReleaseManifest.MAX_SIZE_BYTES (1 MiB); the signature cap is
        // generous for any Ed25519/PEM signature envelope (256 bytes is
        // 4x a raw Ed25519 sig).
        const val MAX_MANIFEST_BYTES: Long = 1024 * 1024 // 1 MiB
        const val MAX_SIGNATURE_BYTES: Long = 256

        /**
         * Create default HTTP client.
         */
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Download raw bytes from a URL (no integrity verification).
     *
     * Used for fetching the release manifest and its signature. The body
     * is streamed into a bounded buffer: if the source returns more than
     * [maxBytes] the download is aborted mid-stream with an OtaException,
     * so a malicious or compromised server cannot OOM the app by returning
     * a multi-gigabyte "manifest" (R3-VBS-03). OkHttp's `body.bytes()`
     * has no built-in cap, so we stream manually.
     *
     * Both `https://` (HTTP via OkHttp) and `file://` (local filesystem)
     * schemes are supported. For `file://`, the file is read directly with
     * the same [maxBytes] enforcement.
     *
     * @param url Absolute URL (`https://` or `file://`)
     * @param maxBytes Maximum accepted body size in bytes; aborts if exceeded
     * @return Downloaded bytes
     * @throws OtaException if the download fails or the body exceeds [maxBytes]
     */
    fun downloadBytes(
        url: String,
        maxBytes: Long = MAX_MANIFEST_BYTES,
    ): ByteArray {
        Logger.debug(LOG, "Downloading bytes (cap $maxBytes): $url")

        if (isFileUrl(url)) {
            return readFileBytes(url, maxBytes)
        }

        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OtaException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw OtaException("Empty response body")

                // Stream into a bounded buffer, aborting once we exceed
                // maxBytes. ByteArrayOutputStream grows as needed but never
                // past maxBytes because we throw first.
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                body.byteStream().use { input: InputStream ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            Logger.error(
                                LOG,
                                "Body exceeds maxBytes: >$total > $maxBytes",
                            )
                            throw OtaException(
                                "Body exceeds maxBytes: >$total > $maxBytes",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
                return output.toByteArray()
            }
        } catch (e: IOException) {
            Logger.error(LOG, "Download failed: ${e.message}")
            throw OtaException("Download failed: ${e.message}", e)
        }
    }

    /**
     * Whether [url] uses the `file` scheme.
     *
     * Accepts both `file:///path` (canonical) and `file:/path` (the form
     * `File.toURI().toString()` produces on some JVMs). Uses URI parsing
     * rather than a string prefix so both forms are recognized.
     */
    private fun isFileUrl(url: String): Boolean {
        return try {
            URI(url).scheme?.equals("file", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read bytes from a `file://` URL with the same [maxBytes] enforcement
     * as the HTTP path.
     *
     * @throws OtaException if the file cannot be read or exceeds [maxBytes]
     */
    private fun readFileBytes(
        url: String,
        maxBytes: Long,
    ): ByteArray {
        val file = File(URI(url))
        if (!file.exists()) {
            throw OtaException("File not found: $url")
        }
        val fileLen = file.length()
        if (fileLen > maxBytes) {
            Logger.error(LOG, "File exceeds maxBytes: $fileLen > $maxBytes")
            throw OtaException("File exceeds maxBytes: $fileLen > $maxBytes")
        }
        try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            file.inputStream().use { input: InputStream ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        Logger.error(
                            LOG,
                            "File exceeds maxBytes: >$total > $maxBytes",
                        )
                        throw OtaException(
                            "File exceeds maxBytes: >$total > $maxBytes",
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        } catch (e: IOException) {
            Logger.error(LOG, "File read failed: ${e.message}")
            throw OtaException("File read failed: ${e.message}", e)
        }
    }

    /**
     * Download a single APK to target location.
     *
     * Streams the response body (or copies the local file for `file://`) to a
     * temp file, aborting as soon as the downloaded byte count exceeds
     * [entry.size] (so a malicious or compromised server cannot OOM the app
     * by returning a multi-gigabyte body). Then verifies the on-disk size
     * and sha256, and only renames the temp file to [targetFile] when both
     * checks pass. This avoids buffering the entire APK into memory before
     * the size check (VBS-01).
     *
     * The APK is fetched from [ReleaseFileEntry.downloadUrl] (a full URL,
     * `https://` or `file://`).
     *
     * @param entry APK entry from manifest (uses [ReleaseFileEntry.downloadUrl])
     * @param targetFile Target file to write
     * @throws OtaException if download fails or verification fails
     */
    fun downloadFile(
        entry: ReleaseFileEntry,
        targetFile: File,
    ) {
        val url = entry.downloadUrl
        Logger.debug(LOG, "Downloading: $url")

        // Stream into a temp file in the target's parent dir so the final
        // rename is atomic (same filesystem). The temp file is deleted on any
        // failure path so a partial/oversized body never reaches targetFile.
        targetFile.parentFile?.mkdirs()
        val tempFile = File.createTempFile("ota-dl-", ".tmp", targetFile.parentFile)

        try {
            // entry.size is the trusted bound; a server that returns more bytes
            // than declared is rejected mid-stream instead of being buffered
            // into memory first.
            val expected = entry.size
            var written = 0L

            // Open the source stream: HTTP via OkHttp, or local file for
            // file://. The size+sha256 enforcement is identical for both.
            val sourceStream: InputStream =
                if (isFileUrl(url)) {
                    val srcFile = File(URI(url))
                    if (!srcFile.exists()) {
                        throw OtaException("File not found: $url")
                    }
                    srcFile.inputStream()
                } else {
                    val request = Request.Builder().url(url).get().build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        throw OtaException("Download failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw OtaException("Empty response body")
                    body.byteStream()
                }

            sourceStream.use { input: InputStream ->
                FileOutputStream(tempFile).use { output ->
                    // Stream the body to the temp file, aborting once we exceed
                    // the manifest size.
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > expected) {
                            Logger.error(
                                LOG,
                                "Size mismatch: expected $expected, got >$written",
                            )
                            throw OtaException(
                                "Size mismatch: expected $expected, got >$written",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    // fsync the staged APK bytes to stable storage before the
                    // atomic rename below. Without this, a power loss after the
                    // rename could leave a zero-length or partially-flushed APK
                    // even though the directory entry already switched to the
                    // target name (R4-VBS-01, same crash-consistency gap class
                    // as R3-VBS-02 in PlanProducer.writeAtomically). The daemon
                    // re-verifies signature+sha256 before applying, so a torn
                    // write is rejected rather than silently applied, but the
                    // result is a failed self-update the user must wait for the
                    // next OTA cycle to retry. Mirrors ApkStager.writeFileAtomically.
                    output.fd.sync()
                }
            }

            // Size check on the streamed file. Compare as Long: entry.size
            // is a Long and toInt() truncates for sizes > 2^31.
            if (written != expected) {
                Logger.error(
                    LOG,
                    "Size mismatch: expected $expected, got $written",
                )
                throw OtaException("Size mismatch: expected $expected, got $written")
            }

            // Verify sha256 on the temp file (no full in-memory copy).
            val sha256 = calculateSha256(tempFile)
            if (sha256 != entry.sha256) {
                Logger.error(LOG, "SHA256 mismatch for ${entry.path}")
                throw OtaException("SHA256 mismatch for ${entry.path}")
            }

            // Atomically promote the verified temp file to the target.
            if (targetFile.exists() && !targetFile.delete()) {
                throw OtaException("Could not replace existing file: ${targetFile.path}")
            }
            if (!tempFile.renameTo(targetFile)) {
                throw OtaException("Could not move verified file to ${targetFile.path}")
            }
            Logger.debug(LOG, "Downloaded and verified: ${entry.path}")
        } catch (e: IOException) {
            Logger.error(LOG, "Download failed: ${e.message}")
            throw OtaException("Download failed: ${e.message}", e)
        } finally {
            // Clean up the temp file if it still exists (rename succeeded or
            // a failure occurred before the rename).
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Download multiple files.
     *
     * @param entries Files to download (each uses its own [ReleaseFileEntry.downloadUrl])
     * @param targetDir Target directory (files named by the downloadUrl basename)
     * @return Map of entry to downloaded file
     * @throws OtaException if any download fails
     */
    fun downloadFiles(
        entries: List<ReleaseFileEntry>,
        targetDir: File,
    ): Map<ReleaseFileEntry, File> {
        val results = mutableMapOf<ReleaseFileEntry, File>()

        for (entry in entries) {
            val targetFile = File(targetDir, entry.path)
            downloadFile(entry, targetFile)
            results[entry] = targetFile
        }

        Logger.info(LOG, "Downloaded ${results.size} files successfully")
        return results
    }

    /**
     * Calculate SHA256 hash of a file by streaming it (no full in-memory copy).
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
