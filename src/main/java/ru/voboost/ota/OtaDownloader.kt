package ru.voboost.ota

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.voboost.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads files from release manifest with integrity verification.
 *
 * Downloads files whole (no binary diff), verifies size pre-check,
 * then verifies sha256 hash.
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
     * is streamed into a bounded buffer: if the server returns more than
     * [maxBytes] the download is aborted mid-stream with an OtaException,
     * so a malicious or compromised server cannot OOM the app by returning
     * a multi-gigabyte "manifest" (R3-VBS-03). OkHttp's `body.bytes()`
     * has no built-in cap, so we stream manually.
     *
     * @param url Absolute URL
     * @param maxBytes Maximum accepted body size in bytes; aborts if exceeded
     * @return Downloaded bytes
     * @throws OtaException if the download fails or the body exceeds [maxBytes]
     */
    fun downloadBytes(
        url: String,
        maxBytes: Long = MAX_MANIFEST_BYTES,
    ): ByteArray {
        Logger.debug(LOG, "Downloading bytes (cap $maxBytes): $url")

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
     * Download a single APK to target location.
     *
     * Streams the response body to a temp file, aborting as soon as the
     * downloaded byte count exceeds [entry.size] (so a malicious or
     * compromised server cannot OOM the app by returning a multi-gigabyte
     * body). Then verifies the on-disk size and sha256, and only renames the
     * temp file to [targetFile] when both checks pass. This avoids buffering
     * the entire APK into memory before the size check (VBS-01).
     *
     * @param entry APK entry from manifest
     * @param baseUrl Base URL for downloads
     * @param targetFile Target file to write
     * @throws OtaException if download fails or verification fails
     */
    fun downloadFile(
        entry: ReleaseFileEntry,
        baseUrl: String,
        targetFile: File,
    ) {
        // Normalize the URL: ensure exactly one '/' between baseUrl and path,
        // regardless of whether baseUrl ends with '/' or path starts with '/'.
        // Without this, baseUrl="https://host" + path="voboost.apk" would
        // produce "https://hostvoboost.apk".
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val url = "$normalizedBase${entry.path.trimStart('/')}"
        Logger.debug(LOG, "Downloading: $url")

        val request = Request.Builder().url(url).get().build()

        // Stream into a temp file in the target's parent dir so the final
        // rename is atomic (same filesystem). The temp file is deleted on any
        // failure path so a partial/oversized body never reaches targetFile.
        targetFile.parentFile?.mkdirs()
        val tempFile = File.createTempFile("ota-dl-", ".tmp", targetFile.parentFile)

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OtaException("Download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw OtaException("Empty response body")

                // Stream the body to the temp file, aborting once we exceed the
                // manifest size. entry.size is the trusted bound; a server that
                // returns more bytes than declared is rejected mid-stream
                // instead of being buffered into memory first.
                val expected = entry.size
                var written = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                body.byteStream().use { input: InputStream ->
                    FileOutputStream(tempFile).use { output ->
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
            }
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
     * @param entries Files to download
     * @param baseUrl Base URL for downloads
     * @param targetDir Target directory (files named by path)
     * @return Map of entry to downloaded file
     * @throws OtaException if any download fails
     */
    fun downloadFiles(
        entries: List<ReleaseFileEntry>,
        baseUrl: String,
        targetDir: File,
    ): Map<ReleaseFileEntry, File> {
        val results = mutableMapOf<ReleaseFileEntry, File>()

        for (entry in entries) {
            val targetFile = File(targetDir, entry.path.substringAfterLast('/'))
            downloadFile(entry, baseUrl, targetFile)
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
