package ru.voboost.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
