package ru.voboost.ota

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
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
 * Tests for [OtaClient] (APK-level orchestrator).
 *
 * Uses MockWebServer to serve the signed unified manifest, its detached
 * signature, and the APK bytes. Verifies the full flow:
 * fetch -> verify signature -> filter by track -> compare app/core versions
 * -> download newer APK(s) -> apply per channel (app staged, core staged +
 * marker).
 */
@RunWith(RobolectricTestRunner::class)
class OtaClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var testDir: File
    private lateinit var paths: Paths
    private lateinit var testPrivateKeyPem: String
    private lateinit var testPublicKeyPem: String

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        testDir = File.createTempFile("ota-client-", "")
        testDir.delete()
        testDir.mkdirs()

        paths =
            object : Paths {
                override val appZone: File get() = testDir
                override val injectJson: File get() = File(appZone, "inject.json")
                override val injectStatusJson: File get() = File(appZone, "inject-status.json")
                override val stagingDir: File get() = File(appZone, "staging").also { it.mkdirs() }
                override val configFile: File get() = File(appZone, "config.yaml")
                override val logsDir: File get() = File(appZone, "logs")
                override val scriptsDirectory: File get() = File(appZone, "scripts")
            }

        val (privateKeyPem, publicKeyPem) = OtaTestUtils.generateTestKeyPair()
        testPrivateKeyPem = privateKeyPem
        testPublicKeyPem = publicKeyPem
    }

    @After
    fun cleanup() {
        mockWebServer.shutdown()
        testDir.deleteRecursively()
    }

    /**
     * Build an OtaConfig with the given installed app/daemon versions and
     * track. The manifest URL points at the mock server's `/manifest.json`;
     * the signature URL is derived by the client (replacing `.json` with
     * `.sig`).
     */
    private fun buildConfig(
        currentAppVersion: String,
        currentDaemonVersion: String?,
        track: String = "production",
    ): OtaConfig {
        return OtaConfig(
            manifestUrl = mockWebServer.url("/manifest.json").toString(),
            track = track,
            publicKeyPem = testPublicKeyPem,
            currentAppVersion = currentAppVersion,
            daemonVersionReader = { currentDaemonVersion },
            // skip install intent in tests (no Android context)
            context = null,
            // single attempt so failure tests don't block on retries
            maxRetries = 1,
        )
    }

    /**
     * Enqueue the signed manifest + signature (and optionally APK responses).
     *
     * The manifest is served at `/manifest.json` and the signature at
     * `/manifest.sig` (the client derives the signature URL by replacing
     * `.json` with `.sig`).
     */
    private fun enqueueManifest(manifest: ReleaseManifest) {
        val manifestJson = manifest.toJson().toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(manifestJson)
                .setResponseCode(200),
        )
        // Signature is raw bytes; use a Buffer to avoid UTF-8 corruption.
        mockWebServer.enqueue(
            MockResponse()
                .setBody(Buffer().write(signature))
                .setResponseCode(200),
        )
    }

    private fun enqueueApk(content: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200),
        )
    }

    @Test
    fun testNoUpdateWhenBothChannelsUpToDate() {
        val appContent = "app apk"
        val coreContent = "core apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.0.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(coreContent),
                            size = coreContent.length.toLong(),
                            version = "1.0.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)

        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        val applied = client.checkAndUpdate()

        assertFalse(applied)
        // Nothing staged.
        assertFalse(File(paths.stagingDir, "voboost.apk").exists())
        assertFalse(File(paths.stagingDir, ApkStager.CORE_APK_NAME).exists())
        assertFalse(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    @Test
    fun testAppUpdateDownloadedAndStaged() {
        val appContent = "new app apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(appContent)

        // Installed app is older (1.0.0 < 1.1.0).
        val client =
            OtaClient(paths, buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = null))
        val applied = client.checkAndUpdate()

        assertTrue(applied)
        // App APK staged (no marker for app channel).
        val stagedApp = File(paths.stagingDir, "voboost.apk")
        assertTrue(stagedApp.exists())
        assertEquals(appContent, stagedApp.readText())
        assertFalse(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    @Test
    fun testCoreUpdateDownloadedStagedAndMarkerCreated() {
        val coreContent = "new daemon apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(coreContent),
                            size = coreContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(coreContent)

        // Installed daemon is older (1.0.0 < 1.1.0).
        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        val applied = client.checkAndUpdate()

        assertTrue(applied)
        // Daemon APK staged to the fixed name + marker created.
        val stagedCore = File(paths.stagingDir, ApkStager.CORE_APK_NAME)
        assertTrue(stagedCore.exists())
        assertEquals(coreContent, stagedCore.readText())
        assertTrue(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    @Test
    fun testBothChannelsUpdated() {
        val appContent = "new app apk"
        val coreContent = "new core apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(coreContent),
                            size = coreContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(appContent)
        enqueueApk(coreContent)

        // Both installed versions older.
        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        val applied = client.checkAndUpdate()

        assertTrue(applied)
        assertTrue(File(paths.stagingDir, "voboost.apk").exists())
        assertTrue(File(paths.stagingDir, ApkStager.CORE_APK_NAME).exists())
        assertTrue(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    @Test
    fun testInvalidSignatureRejectsAndDoesNotPersist() {
        val manifest =
            OtaTestUtils.createTestManifest(
                releases = emptyList(),
            )

        val manifestJson = manifest.toJson().toString()
        // Enqueue manifest + an INVALID signature.
        mockWebServer.enqueue(
            MockResponse()
                .setBody(manifestJson)
                .setResponseCode(200),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setBody(String(ByteArray(64) { 0x00 }, Charsets.ISO_8859_1))
                .setResponseCode(200),
        )

        val client =
            OtaClient(paths, buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = null))

        var threw = false
        try {
            client.checkAndUpdate()
        } catch (e: OtaException) {
            threw = true
        }
        assertTrue("Should throw on invalid signature", threw)

        // The failed manifest is NOT persisted as the current manifest.
        val currentManifest = File(paths.stagingDir, "current-release-manifest.json")
        assertFalse(currentManifest.exists())
    }

    @Test
    fun testMissingDaemonVersionTreatsCoreAsUpdate() {
        val coreContent = "daemon apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(coreContent),
                            size = coreContent.length.toLong(),
                            version = "1.0.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(coreContent)

        // No installed daemon version -> core update available.
        val client =
            OtaClient(paths, buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = null))
        val applied = client.checkAndUpdate()

        assertTrue(applied)
        assertTrue(File(paths.stagingDir, ApkStager.CORE_APK_NAME).exists())
        assertTrue(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    @Test
    fun testCheckForUpdatesDetectsAvailableUpdate() {
        val appContent = "new app apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)

        val client =
            OtaClient(paths, buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = null))
        assertTrue(client.checkForUpdates())
    }

    @Test
    fun testCheckForUpdatesReturnsFalseWhenUpToDate() {
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = "abc",
                            size = 1,
                            version = "1.0.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)

        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        assertFalse(client.checkForUpdates())
    }

    /**
     * Channel isolation: when the app channel succeeds but the core channel
     * fails (sha mismatch), the app APK is still staged and applied==true.
     * The core failure is logged but does NOT abort the app update (C3).
     */
    @Test
    fun testAppSucceedsEvenWhenCoreChannelFails() {
        val appContent = "new app apk"
        val coreContent = "corrupted core apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            // Wrong sha -> core download will fail verification.
                            sha256 = OtaTestUtils.calculateSha256("different content"),
                            size = coreContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(appContent)
        enqueueApk(coreContent)

        // Both channels have updates available; core will fail sha verify.
        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        val applied = client.checkAndUpdate()

        // App channel succeeded -> applied is true despite core failure.
        assertTrue(applied)
        // App APK is staged.
        val stagedApp = File(paths.stagingDir, "voboost.apk")
        assertTrue(stagedApp.exists())
        assertEquals(appContent, stagedApp.readText())
        // Core APK is NOT staged (sha mismatch -> discarded).
        assertFalse(File(paths.stagingDir, ApkStager.CORE_APK_NAME).exists())
        // No core marker (core channel failed).
        assertFalse(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    /**
     * Channel isolation: when the core channel succeeds but the app channel
     * fails (sha mismatch), the core APK is still staged + marker created.
     */
    @Test
    fun testCoreSucceedsEvenWhenAppChannelFails() {
        val appContent = "corrupted app apk"
        val coreContent = "new core apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            // Wrong sha -> app download will fail verification.
                            sha256 = OtaTestUtils.calculateSha256("different content"),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(coreContent),
                            size = coreContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(appContent)
        enqueueApk(coreContent)

        val client =
            OtaClient(
                paths,
                buildConfig(currentAppVersion = "1.0.0", currentDaemonVersion = "1.0.0"),
            )
        val applied = client.checkAndUpdate()

        // Core channel succeeded -> applied is true despite app failure.
        assertTrue(applied)
        // App APK is NOT staged (sha mismatch -> discarded).
        assertFalse(File(paths.stagingDir, "voboost.apk").exists())
        // Core APK is staged + marker created.
        val stagedCore = File(paths.stagingDir, ApkStager.CORE_APK_NAME)
        assertTrue(stagedCore.exists())
        assertEquals(coreContent, stagedCore.readText())
        assertTrue(ApkStager(paths, null).hasCoreUpdateReadyMarker())
    }

    /**
     * Track filtering: a client configured for `track=production` ignores
     * entries on `track=testing`, even when their version is newer. No update
     * is applied and no APK is staged.
     */
    @Test
    fun testTrackFilterIgnoresOtherTracks() {
        val appContent = "new app apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            // different track
                            track = "testing",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)

        // Client is on production; the testing entry must be ignored.
        val client =
            OtaClient(
                paths,
                buildConfig(
                    currentAppVersion = "1.0.0",
                    currentDaemonVersion = null,
                    track = "production",
                ),
            )
        val applied = client.checkAndUpdate()

        assertFalse(applied)
        assertFalse(File(paths.stagingDir, "voboost.apk").exists())
    }

    /**
     * Track filtering: a client configured for `track=testing` sees the
     * testing entry and applies it.
     */
    @Test
    fun testTrackFilterAppliesMatchingTrack() {
        val appContent = "new app apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "testing",
                            downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        enqueueApk(appContent)

        // Client is on testing; the testing entry must be applied.
        val client =
            OtaClient(
                paths,
                buildConfig(
                    currentAppVersion = "1.0.0",
                    currentDaemonVersion = null,
                    track = "testing",
                ),
            )
        val applied = client.checkAndUpdate()

        assertTrue(applied)
        assertTrue(File(paths.stagingDir, "voboost.apk").exists())
    }

    /**
     * Track filtering: a manifest with entries on multiple tracks applies
     * only the entry matching the configured track.
     */
    @Test
    fun testTrackFilterPicksOnlyMatchingTrack() {
        val prodContent = "prod app apk"
        val testingContent = "testing app apk"
        val manifest =
            OtaTestUtils.createTestManifest(
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "production",
                            downloadUrl = mockWebServer.url("/voboost-prod.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(prodContent),
                            size = prodContent.length.toLong(),
                            version = "1.0.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            component = "app",
                            track = "testing",
                            downloadUrl = mockWebServer.url("/voboost-testing.apk").toString(),
                            sha256 = OtaTestUtils.calculateSha256(testingContent),
                            size = testingContent.length.toLong(),
                            version = "1.2.0",
                        ),
                    ),
            )

        enqueueManifest(manifest)
        // Client is on production; only the production entry is considered.
        // The production version (1.0.0) is not newer than installed (1.0.0),
        // so no APK is downloaded.
        val client =
            OtaClient(
                paths,
                buildConfig(
                    currentAppVersion = "1.0.0",
                    currentDaemonVersion = null,
                    track = "production",
                ),
            )
        val applied = client.checkAndUpdate()

        assertFalse(applied)
        assertFalse(File(paths.stagingDir, "voboost-prod.apk").exists())
        assertFalse(File(paths.stagingDir, "voboost-testing.apk").exists())
    }
}
