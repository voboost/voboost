package ru.voboost.ota

/**
 * Semantic versioning utility for version comparison.
 *
 * Parses version strings in "major.minor.patch" format, with optional
 * pre-release suffixes (e.g. "1.0.0-beta1"), and provides comparison
 * operations for update checks. Used to gate whole-APK downloads: an APK
 * is downloaded only when the manifest version for its channel is newer
 * than the installed version.
 *
 * Pre-release comparison follows SemVer 2.0.0:
 * - A release version (no pre-release) is greater than any pre-release of
 *   the same major.minor.patch (e.g. `1.0.0` > `1.0.0-beta1`).
 * - Pre-release identifiers are compared dot-by-dot; numeric identifiers are
 *   compared numerically, alphanumeric identifiers lexically, and numeric
 *   identifiers always rank lower than alphanumeric (e.g. `1.0.0-alpha1` <
 *   `1.0.0-beta1` because "alpha" < "beta").
 * - A pre-release with fewer identifiers ranks lower when all preceding
 *   identifiers are equal (e.g. `1.0.0-beta` < `1.0.0-beta.1`).
 */
object OtaVersion {
    /**
     * Parsed semantic version with an optional pre-release suffix.
     *
     * @param major Major version number.
     * @param minor Minor version number.
     * @param patch Patch version number.
     * @param preRelease Raw pre-release suffix (e.g. "beta1", "alpha.2") or
     *   null for a release version. Stored verbatim; comparison splits it on
     *   dots.
     */
    data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            // Compare major.minor.patch first.
            val core =
                when {
                    major != other.major -> major.compareTo(other.major)
                    minor != other.minor -> minor.compareTo(other.minor)
                    else -> patch.compareTo(other.patch)
                }
            if (core != 0) return core

            // Same core version: a release (null preRelease) is greater than
            // any pre-release.
            return comparePreRelease(preRelease, other.preRelease)
        }

        override fun toString(): String {
            val core = "$major.$minor.$patch"
            return if (preRelease == null) core else "$core-$preRelease"
        }
    }

    /**
     * Compare two pre-release suffixes per SemVer 2.0.0 rules.
     *
     * - A null pre-release (release version) is greater than a non-null one.
     * - Two null pre-releases are equal.
     * - Otherwise split on '.' and compare identifier-by-identifier.
     *
     * @return negative if [a] < [b], zero if equal, positive if [a] > [b].
     */
    private fun comparePreRelease(
        a: String?,
        b: String?,
    ): Int {
        // Release > pre-release.
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1

        val aParts = a.split(".")
        val bParts = b.split(".")
        val common = minOf(aParts.size, bParts.size)
        for (i in 0 until common) {
            val cmp = compareIdentifiers(aParts[i], bParts[i])
            if (cmp != 0) return cmp
        }
        // All common identifiers equal: the version with more identifiers
        // is greater (e.g. "beta.1" > "beta").
        return aParts.size.compareTo(bParts.size)
    }

    /**
     * Compare two pre-release identifiers.
     *
     * Numeric identifiers (all digits, no leading zero unless the identifier is
     * exactly "0") are compared numerically and always rank lower than
     * alphanumeric identifiers. Two numeric identifiers use integer comparison;
     * two alphanumeric use lexical comparison.
     *
     * A digit string with a leading zero (e.g. "01") is NOT treated as numeric:
     * SemVer 2.0.0 forbids leading zeros in numeric identifiers, so such a
     * string is compared lexically as alphanumeric. This keeps comparison
     * deterministic for the (invalid) input without rejecting the whole
     * version, matching the lenient parse-then-compare behavior of [parse].
     */
    private fun compareIdentifiers(
        a: String,
        b: String,
    ): Int {
        val aNumeric = isNumericIdentifier(a)
        val bNumeric = isNumericIdentifier(b)
        return when {
            aNumeric && bNumeric -> {
                val aInt = a.toBigInteger()
                val bInt = b.toBigInteger()
                aInt.compareTo(bInt)
            }
            aNumeric -> -1 // numeric < alphanumeric
            bNumeric -> 1 // alphanumeric > numeric
            else -> a.compareTo(b)
        }
    }

    /**
     * Whether [s] is a SemVer numeric pre-release identifier: non-empty, all
     * digits, and either "0" or without a leading zero.
     */
    private fun isNumericIdentifier(s: String): Boolean =
        s.isNotEmpty() &&
            s.all { it.isDigit() } &&
            (s == "0" || !s.startsWith("0"))

    /**
     * Parse version string to SemVer.
     *
     * Accepts an optional leading non-numeric prefix (e.g. the "voboost-inject "
     * prefix of the `daemon` field in inject-status.json) by taking the first
     * "major.minor.patch" token found in the string. Supports an optional
     * pre-release suffix separated by '-' (e.g. "1.0.0-beta1").
     *
     * @param version Version string in "major.minor.patch[-preRelease]" format
     *   (optionally prefixed)
     * @return Parsed SemVer or null if invalid
     */
    fun parse(version: String): SemVer? {
        return try {
            // Extract a standalone "N.N.N" token optionally followed by a
            // pre-release suffix. The negative lookarounds reject "1.2.3.4"
            // (trailing dot/digit) and tolerate a prefix like
            // "voboost-inject 1.0.0" from inject-status.json's daemon field.
            // The pre-release capture group takes everything up to the first
            // whitespace or end of string, so "1.0.0-beta1" parses fully.
            val match =
                Regex("(?<![\\d.])(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?![\\d.])")
                    .find(version) ?: return null
            val (major, minor, patch, pre) = match.destructured
            SemVer(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                preRelease = pre.takeIf { it.isNotEmpty() },
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
     * The daemon field is a human-readable string like "voboost-inject 1.0.0"
     * (optionally with a pre-release suffix, e.g. "voboost-inject 1.0.0-beta1").
     * A parenthetical build-id annotation (e.g. "voboost-inject 1.0.0 (build
     * 0.9.0)") is stripped first so the build-id version is not mistaken for
     * the daemon version. The version is then the LAST whitespace-separated
     * token of the remaining field, so a leading product name or build path
     * cannot be mistaken for the version. The token is matched with the same
     * strictness as [parse] (rejects "1.2.3.4" via negative lookarounds) so
     * extraction and parsing share one definition of a valid version.
     *
     * @param daemonField The raw `daemon` string from inject-status.json
     * @return The semver version string, or null if it cannot be extracted
     */
    fun extractDaemonVersion(daemonField: String?): String? {
        if (daemonField.isNullOrEmpty()) return null
        // Strip parenthetical annotations (e.g. "(build 0.9.0)") so a build-id
        // version inside the parens is not picked as the daemon version. The
        // daemon version precedes any such annotation.
        val stripped = daemonField.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        if (stripped.isEmpty()) return null
        // Take the trailing token: the daemon version is the last whitespace-
        // separated word of the field (e.g. "voboost-inject 1.0.0-beta1" ->
        // "1.0.0-beta1"). This prevents an earlier version-like substring
        // (build id, path) from being returned as the daemon version.
        val token = stripped.split(Regex("\\s+")).last()
        // Same strict pattern as parse(): standalone N.N.N with an optional
        // pre-release suffix, rejecting a trailing dot/digit (e.g. "1.2.3.4").
        val match =
            Regex("(?<![\\d.])(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?![\\d.])")
                .find(token) ?: return null
        val (major, minor, patch, pre) = match.destructured
        // Reassemble the canonical form so the returned string is exactly what
        // parse() would accept (round-trip consistency).
        val core = "$major.$minor.$patch"
        return if (pre.isEmpty()) core else "$core-$pre"
    }
}
