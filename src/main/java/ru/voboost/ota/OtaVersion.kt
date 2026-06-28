package ru.voboost.ota

/**
 * Semantic versioning utility for version comparison.
 *
 * Parses version strings in "major.minor.patch" format and provides
 * comparison operations for update checks. Used to gate whole-APK
 * downloads: an APK is downloaded only when the manifest version for its
 * channel is newer than the installed version.
 */
object OtaVersion {
    /**
     * Parsed semantic version.
     */
    data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            return when {
                major != other.major -> major.compareTo(other.major)
                minor != other.minor -> minor.compareTo(other.minor)
                else -> patch.compareTo(other.patch)
            }
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    /**
     * Parse version string to SemVer.
     *
     * Accepts an optional leading non-numeric prefix (e.g. the "voboost-inject "
     * prefix of the `daemon` field in inject-status.json) by taking the first
     * "major.minor.patch" token found in the string.
     *
     * @param version Version string in "major.minor.patch" format (optionally prefixed)
     * @return Parsed SemVer or null if invalid
     */
    fun parse(version: String): SemVer? {
        return try {
            // Extract a standalone "N.N.N" token: not preceded or followed by
            // a digit or dot, tolerating a prefix like "voboost-inject 1.0.0"
            // from inject-status.json's daemon field. Rejects "1.2.3.4".
            val match =
                Regex("(?<![\\d.])(\\d+)\\.(\\d+)\\.(\\d+)(?![\\d.])").find(version) ?: return null
            val (major, minor, patch) = match.destructured
            SemVer(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compare two version strings.
     *
     * @return positive if v1 > v2, negative if v1 < v2, 0 if equal
     * @throws OtaException if either version is invalid
     */
    fun compare(
        v1: String,
        v2: String,
    ): Int {
        val semVer1 =
            parse(v1) ?: throw OtaException("Invalid version: $v1")
        val semVer2 =
            parse(v2) ?: throw OtaException("Invalid version: $v2")
        return semVer1.compareTo(semVer2)
    }

    /**
     * Check if v1 is newer than v2.
     *
     * @return true if v1 > v2
     * @throws OtaException if either version is invalid
     */
    fun isNewer(
        v1: String,
        v2: String,
    ): Boolean {
        return compare(v1, v2) > 0
    }

    /**
     * Check if installed version is outdated compared to manifest version.
     *
     * A null/empty manifest version means the channel has no update candidate
     * (skip). A null/empty installed version means nothing is installed yet, so
     * any manifest version is an update.
     *
     * @param installed Current installed version (e.g. BuildConfig.VERSION_NAME for
     *   app, or the daemon field from inject-status.json for core)
     * @param manifest Version from manifest entry
     * @return true if manifest version is newer
     */
    fun isUpdateAvailable(
        installed: String?,
        manifest: String?,
    ): Boolean {
        if (manifest.isNullOrEmpty()) {
            // No manifest version for this channel - no update available
            return false
        }
        if (installed.isNullOrEmpty()) {
            // Nothing installed - treat manifest version as an update
            return true
        }
        return isNewer(manifest, installed)
    }

    /**
     * Extract the daemon version from the inject-status.json `daemon` field.
     *
     * The daemon field is a human-readable string like "voboost-inject 1.0.0";
     * this returns the trailing "major.minor.patch" token, or null if absent.
     *
     * @param daemonField The raw `daemon` string from inject-status.json
     * @return The semver version string, or null if it cannot be extracted
     */
    fun extractDaemonVersion(daemonField: String?): String? {
        if (daemonField.isNullOrEmpty()) return null
        val match = Regex("(\\d+\\.\\d+\\.\\d+)").find(daemonField) ?: return null
        return match.value
    }
}
