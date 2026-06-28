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
 * Uses MockWebServer to serve the signed release manifest, its detached
 * signature, and the APK bytes. Verifies the full flow:
 * fetch -> verify signature -> compare app/core versions -> download newer
 * APK(s) -> apply per channel (app staged, core staged + marker).
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
     * Build an OtaConfig with the given installed app/daemon versions.
     */
    private fun buildConfig(
        currentAppVersion: String,
        currentDaemonVersion: String?,
    ): OtaConfig {
        return OtaConfig(
            baseUrl = mockWebServer.url("").toString().trimEnd('/'),
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
                version = "1.0.0",
                channel = "core",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost.apk",
                            channel = "app",
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.0.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost-inject.apk",
                            channel = "core",
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
                version = "1.1.0",
                channel = "app",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost.apk",
                            channel = "app",
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
                version = "1.1.0",
                channel = "core",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost-inject.apk",
                            channel = "core",
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
                version = "2.0.0",
                channel = "core",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost.apk",
                            channel = "app",
                            sha256 = OtaTestUtils.calculateSha256(appContent),
                            size = appContent.length.toLong(),
                            version = "1.1.0",
                        ),
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost-inject.apk",
                            channel = "core",
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
                version = "1.1.0",
                channel = "app",
                files = emptyList(),
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
                version = "1.0.0",
                channel = "core",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost-inject.apk",
                            channel = "core",
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
                version = "1.1.0",
                channel = "app",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost.apk",
                            channel = "app",
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
                version = "1.0.0",
                channel = "app",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "/voboost.apk",
                            channel = "app",
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
}
