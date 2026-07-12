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
 * - core channel: stages APK to `staging/voboost-inject.apk` + creates
 *   `core-update-ready` marker LAST
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

        // Paths with a temp staging dir; context is null so the app-channel
        // install intent is skipped (staging still succeeds).
        paths =
            object : Paths {
                override val appZone: File get() = testDir
                override val injectJson: File get() = File(appZone, "inject.json")
                override val injectStatusJson: File get() = File(appZone, "inject-status.json")
                override val stagingDir: File get() = testDir
                override val configFile: File get() = File(appZone, "config.yaml")
                override val logsDir: File get() = File(appZone, "logs")
                override val scriptsDirectory: File get() = File(appZone, "scripts")
            }

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

        val staged = File(testDir, "voboost.apk")
        assertTrue(staged.exists())
        assertEquals(content, staged.readText())

        // No marker for the app channel.
        assertFalse(stager.hasCoreUpdateReadyMarker())
        assertFalse(File(testDir, ApkStager.CORE_UPDATE_READY_MARKER).exists())
    }

    @Test
    fun testStageCoreApkStagesToFixedDaemonName() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("core-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                // Even if the downloadUrl basename differs, the daemon APK is
                // staged to the fixed name expected by voboost-inject.
                downloadUrl = "https://host/voboost-inject-1.2.3.apk",
                channel = Channel.CORE,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.2.3",
            )

        stager.stageCoreApk(apkFile, entry)

        val staged = File(testDir, ApkStager.CORE_APK_NAME)
        assertTrue(staged.exists())
        assertEquals(content, staged.readText())

        // The original manifest path is NOT used as the staging name.
        assertFalse(File(testDir, "voboost-inject-1.2.3.apk").exists())
    }

    @Test
    fun testStageCoreApkCreatesMarkerLast() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("core-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject.apk",
                channel = Channel.CORE,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.0.0",
            )

        // Marker should not exist before staging.
        assertFalse(stager.hasCoreUpdateReadyMarker())

        stager.stageCoreApk(apkFile, entry)

        // Marker should exist after staging (created LAST).
        assertTrue(stager.hasCoreUpdateReadyMarker())

        val markerFile = File(testDir, ApkStager.CORE_UPDATE_READY_MARKER)
        assertTrue(markerFile.exists())
        assertEquals("ready", markerFile.readText().trim())
    }

    @Test
    fun testMarkerIsSingleUseClearable() {
        val content = "fake daemon apk"
        val apkFile = File.createTempFile("core-", ".apk")
        apkFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject.apk",
                channel = Channel.CORE,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content),
                size = content.length.toLong(),
                version = "1.0.0",
            )

        stager.stageCoreApk(apkFile, entry)
        assertTrue(stager.hasCoreUpdateReadyMarker())

        // Daemon consumes (deletes) the marker after apply.
        stager.clearCoreUpdateReadyMarker()
        assertFalse(stager.hasCoreUpdateReadyMarker())

        // The staged APK remains after the marker is consumed.
        assertTrue(File(testDir, ApkStager.CORE_APK_NAME).exists())
    }

    @Test
    fun testStageCoreApkOverwritesPreviousStagedApk() {
        val content1 = "daemon v1"
        val apkFile1 = File.createTempFile("core1-", ".apk")
        apkFile1.writeText(content1)

        val entry1 =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject.apk",
                channel = Channel.CORE,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content1),
                size = content1.length.toLong(),
                version = "1.0.0",
            )
        stager.stageCoreApk(apkFile1, entry1)
        stager.clearCoreUpdateReadyMarker()

        val content2 = "daemon v2"
        val apkFile2 = File.createTempFile("core2-", ".apk")
        apkFile2.writeText(content2)

        val entry2 =
            ReleaseFileEntry(
                downloadUrl = "https://host/voboost-inject.apk",
                channel = Channel.CORE,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(content2),
                size = content2.length.toLong(),
                version = "1.0.1",
            )
        stager.stageCoreApk(apkFile2, entry2)

        val staged = File(testDir, ApkStager.CORE_APK_NAME)
        assertEquals(content2, staged.readText())
        assertTrue(stager.hasCoreUpdateReadyMarker())
    }

    @Test
    fun testCleanStaging() {
        val testFile = File(testDir, "voboost-inject.apk")
        testFile.writeText("test")

        val marker = File(testDir, ApkStager.CORE_UPDATE_READY_MARKER)
        marker.writeText("ready")

        assertTrue(testFile.exists())
        assertTrue(marker.exists())

        stager.cleanStaging()

        // Only empty staging directory should remain.
        assertTrue(testDir.exists())
        assertFalse(testFile.exists())
        assertFalse(marker.exists())
    }
}
