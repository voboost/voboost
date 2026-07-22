package ru.voboost.ota

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voboost.Paths
import java.io.File

/**
 * Tests for [ApkStager] (APK-level staging + apply per channel).
 *
 * Covers:
 * - app channel: stages APK to `staging/<path>` (no marker)
 * - inject channel: stages payload to `staging/voboost-inject` + creates
 *   `voboost-inject-update-ready` marker LAST
 * - marker is single-use (clearable)
 * - cleanStaging
 */
@RunWith(RobolectricTestRunner::class)
class ApkStagerTest {
    private lateinit var testDir: File
    private lateinit var paths: Paths
    private lateinit var stager: ApkStager

    @Before
    fun setup() {
        testDir = File.createTempFile("apk-stager-", "")
        testDir.delete()
        testDir.mkdirs()

        // Paths rooted at the temp dir; stagingDir resolves to testDir/staging.
        // context is null so the app-channel install intent is skipped (staging
        // still succeeds).
        paths = Paths(testDir)

        stager = ApkStager(paths, context = null)
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun testStageAppApkStagesToEntryPath() {
        val content = "fake app apk"
        val apkFile = File.createTempFile("app-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost.apk",
                channel = Channel.APP,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.0.0",
            )

        stager.stageAppApk(apkFile, entry)

        val staged = File(paths.stagingDir, "voboost.apk")
        assertTrue(staged.exists())
        assertEquals(content, staged.readText())

        // No marker for the app channel.
        assertFalse(stager.hasInjectUpdateReadyMarker())
        assertFalse(File(paths.stagingDir, ApkStager.INJECT_UPDATE_READY_MARKER).exists())
    }

    @Test
    fun testStageInjectApkStagesToFixedDaemonName() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("inject-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                // Even if the downloadUrl basename differs, the daemon APK is
                // staged to the fixed name expected by voboost-inject.
                downloadUrl = "https://host/voboost-inject-1.2.3",
                channel = Channel.INJECT,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.2.3",
            )

        stager.stageInjectApk(apkFile, entry)

        val staged = File(paths.stagingDir, ApkStager.INJECT_FILE_NAME)
        assertTrue(staged.exists())
        assertEquals(content, staged.readText())

        // The original manifest path is NOT used as the staging name.
        assertFalse(File(paths.stagingDir, "voboost-inject-1.2.3").exists())
    }

    @Test
    fun testStageInjectApkCreatesMarkerLast() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("inject-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject",
                channel = Channel.INJECT,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.0.0",
            )

        // Marker should not exist before staging.
        assertFalse(stager.hasInjectUpdateReadyMarker())

        stager.stageInjectApk(apkFile, entry)

        // Marker should exist after staging (created LAST).
        assertTrue(stager.hasInjectUpdateReadyMarker())

        val markerFile = File(paths.stagingDir, ApkStager.INJECT_UPDATE_READY_MARKER)
        assertTrue(markerFile.exists())
        assertEquals("ready", markerFile.readText().trim())
    }

    @Test
    fun testMarkerIsSingleUseClearable() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("inject-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject",
                channel = Channel.INJECT,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.0.0",
            )

        stager.stageInjectApk(apkFile, entry)
        assertTrue(stager.hasInjectUpdateReadyMarker())

        // Daemon consumes (deletes) the marker after apply.
        stager.clearInjectUpdateReadyMarker()
        assertFalse(stager.hasInjectUpdateReadyMarker())

        // The staged APK remains after the marker is consumed.
        assertTrue(File(paths.stagingDir, ApkStager.INJECT_FILE_NAME).exists())
    }

    @Test
    fun testStageInjectApkOverwritesPreviousStagedApk() {
        val content1 = "daemon v1"
        val apkFile1 = File.createTempFile("inject1-", ".apk")
        apkFile1.writeText(content1)

        val entry1 =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject",
                channel = Channel.INJECT,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content1),
                size = content1.length.toLong(),
                version = "1.0.0",
            )
        stager.stageInjectApk(apkFile1, entry1)
        stager.clearInjectUpdateReadyMarker()

        val content2 = "daemon v2"
        val apkFile2 = File.createTempFile("inject2-", ".apk")
        apkFile2.writeText(content2)

        val entry2 =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject",
                channel = Channel.INJECT,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content2),
                size = content2.length.toLong(),
                version = "1.0.1",
            )
        stager.stageInjectApk(apkFile2, entry2)

        val staged = File(paths.stagingDir, ApkStager.INJECT_FILE_NAME)
        assertEquals(content2, staged.readText())
        assertTrue(stager.hasInjectUpdateReadyMarker())
    }

    @Test
    fun testCleanStaging() {
        val testFile = File(paths.stagingDir, "voboost-inject")
        testFile.writeText("test")

        val marker = File(paths.stagingDir, ApkStager.INJECT_UPDATE_READY_MARKER)
        marker.writeText("ready")

        assertTrue(testFile.exists())
        assertTrue(marker.exists())

        stager.cleanStaging()

        // Only empty staging directory should remain.
        assertTrue(paths.stagingDir.exists())
        assertFalse(testFile.exists())
        assertFalse(marker.exists())
    }
}
