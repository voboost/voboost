package ru.voboost.ota

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.voboost.Logger
import java.io.File
import java.io.IOException
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
     * Used for fetching the release manifest and its signature.
     *
     * @param url Absolute URL
     * @return Downloaded bytes
     * @throws OtaException if the download fails
     */
    fun downloadBytes(url: String): ByteArray {
        Logger.debug(LOG, "Downloading bytes: $url")

        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OtaException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw OtaException("Empty response body")
                return body.bytes()
            }
        } catch (e: IOException) {
            Logger.error(LOG, "Download failed: ${e.message}")
            throw OtaException("Download failed: ${e.message}", e)
        }
    }

    /**
     * Download a single APK to target location.
     *
     * Downloads the APK whole, performs a size pre-check (rejecting before
     * hashing if the byte count disagrees with the manifest size), then
     * verifies the sha256. Only a verified APK is written to [targetFile].
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

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OtaException("Download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw OtaException("Empty response body")

                // Size pre-check before hashing. Compare as Long: entry.size is
                // a Long and toInt() truncates for sizes > 2^31, which would
                // silently accept a truncated APK.
                val bytes = body.bytes()
                if (bytes.size.toLong() != entry.size) {
                    Logger.error(
                        LOG,
                        "Size mismatch: expected ${entry.size}, got ${bytes.size}",
                    )
                    throw OtaException("Size mismatch: expected ${entry.size}, got ${bytes.size}")
                }

                // Verify sha256
                val sha256 = calculateSha256(bytes)
                if (sha256 != entry.sha256) {
                    Logger.error(LOG, "SHA256 mismatch for ${entry.path}")
                    throw OtaException("SHA256 mismatch for ${entry.path}")
                }

                // Write to target file
                targetFile.parentFile?.mkdirs()
                targetFile.writeBytes(bytes)
                Logger.debug(LOG, "Downloaded and verified: ${entry.path}")
            }
        } catch (e: IOException) {
            Logger.error(LOG, "Download failed: ${e.message}")
            throw OtaException("Download failed: ${e.message}", e)
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
     * Calculate SHA256 hash of bytes.
     */
    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
