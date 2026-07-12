package ru.voboost.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [OtaVersion] (semver compare + daemon version extraction).
 *
 * Covers: newer/older/equal for app and core channels, missing daemon
 * version, and extraction of the version from the inject-status.json `daemon`
 * field (e.g. "voboost-inject 1.0.0" -> "1.0.0").
 */
@RunWith(RobolectricTestRunner::class)
class OtaVersionTest {
    @Test
    fun testParseValidSemver() {
        val v = OtaVersion.parse("1.2.3")
        assertEquals(OtaVersion.SemVer(1, 2, 3), v)
    }

    @Test
    fun testParseToleratesPrefix() {
        // The daemon field in inject-status.json is "voboost-inject 1.0.0".
        val v = OtaVersion.parse("voboost-inject 1.0.0")
        assertEquals(OtaVersion.SemVer(1, 0, 0), v)
    }

    @Test
    fun testParseInvalidReturnsNull() {
        assertNull(OtaVersion.parse("not-a-version"))
        assertNull(OtaVersion.parse("1.2"))
        assertNull(OtaVersion.parse(""))
        assertNull(OtaVersion.parse("1.2.3.4"))
    }

    @Test
    fun testCompareNewer() {
        assertTrue(OtaVersion.isNewer("1.2.3", "1.2.2"))
        assertTrue(OtaVersion.isNewer("1.3.0", "1.2.9"))
        assertTrue(OtaVersion.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun testCompareOlder() {
        assertFalse(OtaVersion.isNewer("1.2.2", "1.2.3"))
        assertFalse(OtaVersion.isNewer("1.2.9", "1.3.0"))
        assertFalse(OtaVersion.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun testCompareEqual() {
        assertFalse(OtaVersion.isNewer("1.2.3", "1.2.3"))
        assertEquals(0, OtaVersion.compare("1.2.3", "1.2.3"))
    }

    @Test
    fun testIsUpdateAvailableAppChannel() {
        // Manifest app version newer than installed -> update available.
        assertTrue(OtaVersion.isUpdateAvailable("1.0.0", "1.0.1"))
        // Manifest app version equal -> no update.
        assertFalse(OtaVersion.isUpdateAvailable("1.0.0", "1.0.0"))
        // Manifest app version older -> no update.
        assertFalse(OtaVersion.isUpdateAvailable("1.0.1", "1.0.0"))
    }

    @Test
    fun testIsUpdateAvailableCoreChannel() {
        // Same logic applies to the core channel.
        assertTrue(OtaVersion.isUpdateAvailable("1.0.0", "1.1.0"))
        assertFalse(OtaVersion.isUpdateAvailable("1.1.0", "1.0.0"))
    }

    @Test
    fun testIsUpdateAvailableMissingDaemonVersion() {
        // No installed daemon version -> any manifest version is an update.
        assertTrue(OtaVersion.isUpdateAvailable(null, "1.0.0"))
        assertTrue(OtaVersion.isUpdateAvailable("", "1.0.0"))
    }

    @Test
    fun testIsUpdateAvailableMissingManifestVersion() {
        // No manifest version for the channel -> skip (no update).
        assertFalse(OtaVersion.isUpdateAvailable("1.0.0", null))
        assertFalse(OtaVersion.isUpdateAvailable("1.0.0", ""))
    }

    @Test
    fun testExtractDaemonVersionFromInjectStatus() {
        // The daemon field is "voboost-inject 1.2.3".
        assertEquals("1.2.3", OtaVersion.extractDaemonVersion("voboost-inject 1.2.3"))
        assertEquals("1.0.0", OtaVersion.extractDaemonVersion("voboost-inject 1.0.0"))
    }

    @Test
    fun testExtractDaemonVersionBareVersion() {
        // A bare version string is also accepted.
        assertEquals("1.2.3", OtaVersion.extractDaemonVersion("1.2.3"))
    }

    @Test
    fun testExtractDaemonVersionNullAndEmpty() {
        assertNull(OtaVersion.extractDaemonVersion(null))
        assertNull(OtaVersion.extractDaemonVersion(""))
        assertNull(OtaVersion.extractDaemonVersion("voboost-inject"))
    }

    @Test
    fun testCompareThrowsOnInvalid() {
        try {
            OtaVersion.compare("not-a-version", "1.0.0")
            fail("Should throw for invalid version")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Invalid version"))
        }
    }

    // --- Pre-release suffix handling (H1) ---

    @Test
    fun testParsePreReleaseSuffix() {
        val v = OtaVersion.parse("1.0.0-beta1")
        assertEquals(OtaVersion.SemVer(1, 0, 0, "beta1"), v)
    }

    @Test
    fun testParsePreReleaseWithDots() {
        val v = OtaVersion.parse("1.0.0-beta.2")
        assertEquals(OtaVersion.SemVer(1, 0, 0, "beta.2"), v)
    }

    @Test
    fun testReleaseGreaterThanPreRelease() {
        // 1.0.0 > 1.0.0-beta1 (release > pre-release)
        assertTrue(OtaVersion.isNewer("1.0.0", "1.0.0-beta1"))
        assertFalse(OtaVersion.isNewer("1.0.0-beta1", "1.0.0"))
    }

    @Test
    fun testHigherBetaNumberIsNewer() {
        // 1.0.0-beta2 > 1.0.0-beta1
        assertTrue(OtaVersion.isNewer("1.0.0-beta2", "1.0.0-beta1"))
        assertFalse(OtaVersion.isNewer("1.0.0-beta1", "1.0.0-beta2"))
    }

    @Test
    fun testAlphaLessThanBeta() {
        // 1.0.0-alpha1 < 1.0.0-beta1 (alpha < beta)
        assertTrue(OtaVersion.isNewer("1.0.0-beta1", "1.0.0-alpha1"))
        assertFalse(OtaVersion.isNewer("1.0.0-alpha1", "1.0.0-beta1"))
    }

    @Test
    fun testMajorVersionBeatsPreRelease() {
        // 2.0.0 > 1.0.0 (major version)
        assertTrue(OtaVersion.isNewer("2.0.0", "1.0.0"))
    }

    @Test
    fun testPreReleaseEqualVersions() {
        assertEquals(0, OtaVersion.compare("1.0.0-beta1", "1.0.0-beta1"))
        assertFalse(OtaVersion.isNewer("1.0.0-beta1", "1.0.0-beta1"))
    }

    @Test
    fun testPreReleaseUpdateAvailable() {
        // Installed beta1, manifest beta2 -> update available.
        assertTrue(OtaVersion.isUpdateAvailable("1.0.0-beta1", "1.0.0-beta2"))
        // Installed beta2, manifest beta1 -> no update.
        assertFalse(OtaVersion.isUpdateAvailable("1.0.0-beta2", "1.0.0-beta1"))
        // Installed beta1, manifest release -> update available.
        assertTrue(OtaVersion.isUpdateAvailable("1.0.0-beta1", "1.0.0"))
    }

    @Test
    fun testExtractDaemonVersionWithPreRelease() {
        assertEquals("1.0.0-beta1", OtaVersion.extractDaemonVersion("voboost-inject 1.0.0-beta1"))
        assertEquals("1.2.3-beta.4", OtaVersion.extractDaemonVersion("voboost-inject 1.2.3-beta.4"))
    }

    // --- extractDaemonVersion strictness (F2) ---

    @Test
    fun testExtractDaemonVersionPicksTrailingToken() {
        // A build-id or path before the real version must not be picked.
        assertEquals(
            "1.0.0",
            OtaVersion.extractDaemonVersion("voboost-inject 1.0.0 (build 0.9.0)"),
        )
        assertEquals(
            "2.3.4-rc1",
            OtaVersion.extractDaemonVersion("voboost-inject /path/2.3.4-rc1"),
        )
    }

    @Test
    fun testExtractDaemonVersionRejectsFourPart() {
        // "1.2.3.4" is not a valid semver token; the strict lookarounds reject it.
        assertNull(OtaVersion.extractDaemonVersion("voboost-inject 1.2.3.4"))
    }

    @Test
    fun testExtractDaemonVersionRoundTripsThroughParse() {
        // The extracted string must be acceptable by parse() (round-trip).
        val extracted = OtaVersion.extractDaemonVersion("voboost-inject 1.0.0-beta1")
        assertNotNull(extracted)
        assertNotNull(OtaVersion.parse(extracted!!))
    }

    // --- leading-zero numeric identifiers (F3) ---

    @Test
    fun testLeadingZeroIdentifierNotNumeric() {
        // "01" is not a valid SemVer numeric identifier; it is treated as
        // alphanumeric, so "1.0.0-01" != "1.0.0-1".
        assertNotEquals(0, OtaVersion.compare("1.0.0-01", "1.0.0-1"))
    }

    @Test
    fun testZeroIdentifierIsNumeric() {
        // A single "0" is a valid numeric identifier.
        assertEquals(0, OtaVersion.compare("1.0.0-0", "1.0.0-0"))
        assertTrue(OtaVersion.isNewer("1.0.0-1", "1.0.0-0"))
    }
}
