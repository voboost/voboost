package ru.voboost.ota

import org.json.JSONArray
import org.json.JSONObject

/**
 * Release manifest file entry.
 *
 * Describes a single whole APK in the signed release manifest:
 * - [path] — APK file name (e.g. "voboost.apk", "voboost-inject.apk")
 * - [channel] — APK channel: [Channel.APP] (voboost client) or [Channel.CORE] (voboost-inject daemon)
 * - [sha256] — hex SHA-256 of the APK bytes (authoritative integrity check)
 * - [size] — APK byte size (DoS guard, checked before hashing)
 * - [version] — semver version string ("major.minor.patch")
 */
data class ReleaseFileEntry(
    val path: String,
    val channel: Channel,
    val sha256: String,
    val size: Long,
    val version: String,
) {
    companion object {
        /**
         * Parse a release file entry from JSON.
         *
         * An entry missing any required field, or whose channel is not in
         * {app, core}, is rejected even if the manifest signature is valid.
         *
         * @throws OtaException if required fields are missing or channel is invalid
         */
        fun fromJson(json: JSONObject): ReleaseFileEntry {
            try {
                val path = json.getString("path")
                val channelStr = json.getString("channel")
                val sha256 = json.getString("sha256")
                val size = json.getLong("size")
                val version = json.getString("version")

                val channel =
                    try {
                        Channel.valueOf(channelStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        throw OtaException("Invalid channel: $channelStr")
                    }

                return ReleaseFileEntry(path, channel, sha256, size, version)
            } catch (e: Exception) {
                if (e is OtaException) throw e
                throw OtaException("Invalid file entry: ${e.message}", e)
            }
        }
    }

    /**
     * Convert to JSON object.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("path", path)
            put("channel", channel.name.lowercase())
            put("sha256", sha256)
            put("size", size)
            put("version", version)
        }
    }
}

/**
 * Release manifest.
 *
 * Lists the current whole-APK releases (voboost app + voboost-inject daemon).
 * The client verifies the manifest's detached ed25519 signature before trusting
 * any of its contents.
 */
data class ReleaseManifest(
    val version: String,
    val channel: String,
    val files: List<ReleaseFileEntry>,
) {
    companion object {
        /** Maximum manifest byte size (1 MiB). */
        const val MAX_SIZE_BYTES = 1024 * 1024 // 1 MiB

        /** Maximum entry count. */
        const val MAX_ENTRIES = 4096

        /**
         * Parse a release manifest from JSON.
         *
         * Enforces the maximum entry count ([MAX_ENTRIES]). The byte-size bound
         * ([MAX_SIZE_BYTES]) is enforced by [OtaVerifier] on the raw manifest
         * bytes before parsing.
         *
         * @throws OtaException if manifest is invalid or exceeds entry-count bound
         */
        fun fromJson(json: JSONObject): ReleaseManifest {
            try {
                val version = json.getString("version")
                val channel = json.getString("channel")
                val filesArray = json.getJSONArray("files")

                if (filesArray.length() > MAX_ENTRIES) {
                    throw OtaException(
                        "Manifest exceeds maximum entry count: " +
                            "${filesArray.length()} > $MAX_ENTRIES",
                    )
                }

                val files = mutableListOf<ReleaseFileEntry>()
                for (i in 0 until filesArray.length()) {
                    val entryJson = filesArray.getJSONObject(i)
                    files.add(ReleaseFileEntry.fromJson(entryJson))
                }

                return ReleaseManifest(version, channel, files)
            } catch (e: Exception) {
                if (e is OtaException) throw e
                throw OtaException("Invalid manifest: ${e.message}", e)
            }
        }
    }

    /**
     * Convert to JSON object.
     */
    fun toJson(): JSONObject {
        val filesArray =
            JSONArray().apply {
                files.forEach { entry ->
                    put(entry.toJson())
                }
            }

        return JSONObject().apply {
            put("version", version)
            put("channel", channel)
            put("files", filesArray)
        }
    }

    /**
     * Get file entry by path.
     */
    fun getFileByPath(path: String): ReleaseFileEntry? = files.find { it.path == path }

    /**
     * Get files by channel.
     */
    fun getFilesByChannel(channel: Channel): List<ReleaseFileEntry> =
        files.filter { it.channel == channel }

    /**
     * Get the single entry for a channel, or null if absent.
     */
    fun getEntryByChannel(channel: Channel): ReleaseFileEntry? =
        files.firstOrNull { it.channel == channel }
}

/**
 * Channel enum for release APKs.
 *
 * - [APP] — the voboost client APK (this repo).
 * - [CORE] — the voboost-inject daemon APK (sibling repo).
 *
 * There is intentionally no "agents" channel: every update is a whole APK,
 * never a detached resource file.
 */
enum class Channel {
    APP,
    CORE,
}

/**
 * OTA exception.
 */
class OtaException(message: String, cause: Throwable? = null) : Exception(message, cause)
