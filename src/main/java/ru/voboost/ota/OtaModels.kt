package ru.voboost.ota

import org.json.JSONArray
import org.json.JSONObject

/**
 * Release manifest file entry (unified schema).
 *
 * Describes a single whole APK in the signed unified release manifest:
 * - [downloadUrl] — full URL to the APK (GitHub Release asset `https://` or
 *   local `file://` for testing). Replaces the old `path`+`baseUrl`
 *   concatenation.
 * - [path] — APK file name derived from [downloadUrl]'s last path segment
 *   (e.g. "voboost.apk", "voboost-inject.apk"). Kept for `ApkStager` and
 *   apply logic compatibility.
 * - [channel] — internal APK channel: [Channel.APP] (voboost client) or
 *   [Channel.CORE] (voboost-inject daemon). Mapped from the unified schema's
 *   `component` field (`app`→APP, `inject`→CORE).
 * - [track] — release track (`dev`/`testing`/`production`). The client filters
 *   entries by track before version comparison.
 * - [sha256] — hex SHA-256 of the APK bytes (authoritative integrity check)
 * - [size] — APK byte size (DoS guard, checked before hashing)
 * - [version] — semver version string ("major.minor.patch")
 */
data class ReleaseFileEntry(
    val downloadUrl: String,
    val channel: Channel,
    val track: String,
    val sha256: String,
    val size: Long,
    val version: String,
) {
    /**
     * APK file name derived from [downloadUrl]'s last path segment.
     *
     * Used by `ApkStager` to name the staged APK. For a URL like
     * `https://host/path/voboost.apk` this is `voboost.apk`; for
     * `file:///tmp/voboost-inject.apk` it is `voboost-inject.apk`.
     */
    val path: String
        get() = downloadUrl.substringAfterLast('/')

    companion object {
        /**
         * Parse a release entry from a unified-schema JSON object.
         *
         * An entry missing any required field, or whose `component` is not in
         * {app, inject}, is rejected even if the manifest signature is valid.
         *
         * @throws OtaException if required fields are missing or component is invalid
         */
        fun fromJson(json: JSONObject): ReleaseFileEntry {
            try {
                val component = json.getString("component")
                val track = json.getString("track")
                val downloadUrl = json.getString("downloadUrl")
                val sha256 = json.getString("sha256")
                val size = json.getLong("size")
                val version = json.getString("version")

                val channel = componentToChannel(component)

                return ReleaseFileEntry(downloadUrl, channel, track, sha256, size, version)
            } catch (e: Exception) {
                if (e is OtaException) throw e
                throw OtaException("Invalid release entry: ${e.message}", e)
            }
        }
    }

    /**
     * Convert to a unified-schema JSON object.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("component", channelToComponent(channel))
            put("track", track)
            put("downloadUrl", downloadUrl)
            put("sha256", sha256)
            put("size", size)
            put("version", version)
        }
    }
}

/**
 * Map a unified-schema `component` string to the internal [Channel].
 *
 * `app` → [Channel.APP]; `inject` → [Channel.CORE] (the daemon APK, renamed
 * from the old `core` value to match the `voboost-inject` repo name). Any
 * other value is rejected.
 *
 * @throws OtaException if [component] is not `app` or `inject`
 */
private fun componentToChannel(component: String): Channel {
    return when (component) {
        "app" -> Channel.APP
        "inject" -> Channel.CORE
        else -> throw OtaException("Invalid component: $component")
    }
}

/**
 * Map an internal [Channel] back to the unified-schema `component` string.
 */
private fun channelToComponent(channel: Channel): String {
    return when (channel) {
        Channel.APP -> "app"
        Channel.CORE -> "inject"
    }
}

/**
 * Release manifest (unified schema).
 *
 * Lists the current whole-APK releases (voboost app + voboost-inject daemon)
 * across one or more release tracks. The client verifies the manifest's
 * detached ed25519 signature before trusting any of its contents, then
 * filters [releases] by track before version comparison.
 */
data class ReleaseManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val releases: List<ReleaseFileEntry>,
) {
    companion object {
        /** Maximum manifest byte size (1 MiB). */
        const val MAX_SIZE_BYTES = 1024 * 1024 // 1 MiB

        /** Maximum entry count. */
        const val MAX_ENTRIES = 4096

        /** Unified schema version this parser accepts. */
        const val SCHEMA_VERSION = 1

        /**
         * Parse a unified-schema release manifest from JSON.
         *
         * Enforces the maximum entry count ([MAX_ENTRIES]). The byte-size
         * bound ([MAX_SIZE_BYTES]) is enforced by [OtaVerifier] on the raw
         * manifest bytes before parsing.
         *
         * @throws OtaException if manifest is invalid or exceeds entry-count bound
         */
        fun fromJson(json: JSONObject): ReleaseManifest {
            try {
                val schemaVersion = json.optInt("schemaVersion", SCHEMA_VERSION)
                val generatedAt = json.optString("generatedAt", "")
                val releasesArray = json.getJSONArray("releases")

                if (releasesArray.length() > MAX_ENTRIES) {
                    throw OtaException(
                        "Manifest exceeds maximum entry count: " +
                            "${releasesArray.length()} > $MAX_ENTRIES",
                    )
                }

                val releases = mutableListOf<ReleaseFileEntry>()
                for (i in 0 until releasesArray.length()) {
                    val entryJson = releasesArray.getJSONObject(i)
                    releases.add(ReleaseFileEntry.fromJson(entryJson))
                }

                return ReleaseManifest(schemaVersion, generatedAt, releases)
            } catch (e: Exception) {
                if (e is OtaException) throw e
                throw OtaException("Invalid manifest: ${e.message}", e)
            }
        }
    }

    /**
     * Convert to a unified-schema JSON object.
     *
     * Emits the unified schema so the persisted
     * `current-release-manifest.json` round-trips through [fromJson].
     */
    fun toJson(): JSONObject {
        val releasesArray =
            JSONArray().apply {
                releases.forEach { entry ->
                    put(entry.toJson())
                }
            }

        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("generatedAt", generatedAt)
            put("releases", releasesArray)
        }
    }

    /**
     * Get the single entry for a channel, or null if absent.
     *
     * Operates on the full [releases] list; the caller is expected to have
     * filtered by track first (see [OtaClient]).
     */
    fun getEntryByChannel(channel: Channel): ReleaseFileEntry? =
        releases.firstOrNull { it.channel == channel }
}

/**
 * Channel enum for release APKs (internal representation).
 *
 * - [APP] — the voboost client APK (this repo). Mapped from `component:"app"`.
 * - [CORE] — the voboost-inject daemon APK (sibling repo). Mapped from
 *   `component:"inject"`.
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
