package ru.voboost.ota

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests for [OtaDownloader] (whole-APK download with size pre-check + sha256).
 *
 * Covers: successful download, size-mismatch rejection (before hashing),
 * sha-mismatch rejection, multiple APKs, HTTP error, empty body, large APK,
 * the [downloadBytes] helper used for the manifest/signature, and `file://`
 * scheme support for local testing.
 */
@RunWith(RobolectricTestRunner::class)
class OtaDownloaderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var downloader: OtaDownloader
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        downloader = OtaDownloader()

        tempDir = File.createTempFile("ota-test-", "")
        tempDir.delete()
        tempDir.mkdirs()
    }

    @After
    fun cleanup() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun testDownloadAppApkSuccess() {
        val content = "fake app apk bytes"
        val sha256 = OtaTestUtils.calculateSha256(content)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = sha256,
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        downloader.downloadFile(entry, targetFile)

        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
    }

    @Test
    fun testDownloadCoreApkSuccess() {
        val content = "fake daemon apk bytes"
        val sha256 = OtaTestUtils.calculateSha256(content)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                channel = Channel.CORE,
                track = "production",
                sha256 = sha256,
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost-inject.apk")
        downloader.downloadFile(entry, targetFile)

        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
    }

    @Test
    fun testDownloadFileSizeMismatchRejectsBeforeHashing() {
        val content = "test content"
        val sha256 = OtaTestUtils.calculateSha256(content)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = sha256,
                // wrong size triggers size pre-check rejection before hashing
                size = 9999L,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, targetFile)
            fail("Should throw exception for size mismatch")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Size mismatch"))
        }

        // Size mismatch rejects before hashing and does not stage the APK.
        assertFalse(targetFile.exists())
    }

    @Test
    fun testDownloadFileShaMismatchRejects() {
        val content = "test content"

        mockWebServer.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = "wrongsha256",
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, targetFile)
            fail("Should throw exception for SHA mismatch")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("SHA256 mismatch"))
        }

        assertFalse(targetFile.exists())
    }

    @Test
    fun testDownloadMultipleApksSuccess() {
        val content1 = "app apk bytes"
        val content2 = "daemon apk bytes"
        val sha1 = OtaTestUtils.calculateSha256(content1)
        val sha2 = OtaTestUtils.calculateSha256(content2)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(content1)
                .setResponseCode(200),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setBody(content2)
                .setResponseCode(200),
        )

        val entries =
            listOf(
                ReleaseFileEntry(
                    downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                    channel = Channel.APP,
                    track = "production",
                    sha256 = sha1,
                    size = content1.length.toLong(),
                    version = "1.0.0",
                ),
                ReleaseFileEntry(
                    downloadUrl = mockWebServer.url("/voboost-inject.apk").toString(),
                    channel = Channel.CORE,
                    track = "production",
                    sha256 = sha2,
                    size = content2.length.toLong(),
                    version = "1.0.0",
                ),
            )

        val results = downloader.downloadFiles(entries, tempDir)

        assertEquals(2, results.size)
        assertEquals(content1, results[entries[0]]?.readText())
        assertEquals(content2, results[entries[1]]?.readText())
    }

    @Test
    fun testDownloadHttpError() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = "abc123",
                size = 1024,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, targetFile)
            fail("Should throw exception for HTTP error")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Download failed"))
        }
    }

    @Test
    fun testDownloadEmptyBody() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(""),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = OtaTestUtils.calculateSha256(""),
                size = 0,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        // Empty body with size 0 and matching SHA256 is valid.
        downloader.downloadFile(entry, targetFile)

        assertTrue(targetFile.exists())
        assertEquals(0L, targetFile.length())
    }

    @Test
    fun testDownloadLargeApk() {
        val largeContent = "x".repeat(100 * 1024) // 100 KB
        val sha256 = OtaTestUtils.calculateSha256(largeContent)

        mockWebServer.enqueue(
            MockResponse()
                .setBody(largeContent)
                .setResponseCode(200),
        )

        val entry =
            ReleaseFileEntry(
                downloadUrl = mockWebServer.url("/voboost.apk").toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = sha256,
                size = largeContent.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        downloader.downloadFile(entry, targetFile)

        assertTrue(targetFile.exists())
        assertEquals(largeContent.length.toLong(), targetFile.length())
        assertEquals(largeContent, targetFile.readText())
    }

    @Test
    fun testDownloadBytesForManifest() {
        val manifestContent =
            """{"schemaVersion":1,"generatedAt":"","releases":[]}"""

        mockWebServer.enqueue(
            MockResponse()
                .setBody(manifestContent)
                .setResponseCode(200),
        )

        val bytes = downloader.downloadBytes(mockWebServer.url("/manifest.json").toString())

        assertEquals(manifestContent, String(bytes, Charsets.UTF_8))
    }

    @Test
    fun testDownloadBytesHttpError() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500),
        )

        try {
            downloader.downloadBytes(mockWebServer.url("/manifest.json").toString())
            fail("Should throw exception for HTTP error")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Download failed"))
        }
    }

    /**
     * R3-VBS-03: a body larger than maxBytes must abort mid-stream with an
     * OtaException instead of buffering the whole thing into memory.
     */
    @Test
    fun testDownloadBytesRejectsOversizedBody() {
        // Body of 64 bytes, cap of 32 bytes -> must abort.
        val oversized = "x".repeat(64)
        mockWebServer.enqueue(
            MockResponse()
                .setBody(oversized)
                .setResponseCode(200),
        )

        try {
            downloader.downloadBytes(
                mockWebServer.url("/manifest.json").toString(),
                maxBytes = 32,
            )
            fail("Should throw exception for oversized body")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("exceeds maxBytes"))
        }
    }

    /**
     * `file://` scheme: downloadBytes reads a local file with the same
     * maxBytes enforcement as the HTTP path.
     */
    @Test
    fun testDownloadBytesFileSchemeSuccess() {
        val content = """{"schemaVersion":1,"generatedAt":"","releases":[]}"""
        val localFile = File(tempDir, "manifest.json")
        localFile.writeText(content)

        val url = localFile.toURI().toString() // file:///.../manifest.json
        val bytes = downloader.downloadBytes(url)

        assertEquals(content, String(bytes, Charsets.UTF_8))
    }

    @Test
    fun testDownloadBytesFileSchemeRejectsOversizedFile() {
        val oversized = "x".repeat(64)
        val localFile = File(tempDir, "manifest.json")
        localFile.writeText(oversized)

        try {
            downloader.downloadBytes(localFile.toURI().toString(), maxBytes = 32)
            fail("Should throw exception for oversized file")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("exceeds maxBytes"))
        }
    }

    @Test
    fun testDownloadBytesFileSchemeMissingFile() {
        val missing = File(tempDir, "nope.json").toURI().toString()
        try {
            downloader.downloadBytes(missing)
            fail("Should throw exception for missing file")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("File not found"))
        }
    }

    /**
     * `file://` scheme: downloadFile copies a local APK to the target with
     * the same size+sha256 enforcement as the HTTP path.
     */
    @Test
    fun testDownloadFileFileSchemeSuccess() {
        val content = "local app apk bytes"
        val sha256 = OtaTestUtils.calculateSha256(content)
        val sourceFile = File(tempDir, "source-voboost.apk")
        sourceFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = sourceFile.toURI().toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = sha256,
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        downloader.downloadFile(entry, targetFile)

        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
    }

    @Test
    fun testDownloadFileFileSchemeShaMismatchRejects() {
        val content = "local app apk bytes"
        val sourceFile = File(tempDir, "source-voboost.apk")
        sourceFile.writeText(content)

        val entry =
            ReleaseFileEntry(
                downloadUrl = sourceFile.toURI().toString(),
                channel = Channel.APP,
                track = "production",
                sha256 = "wrongsha256",
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        try {
            downloader.downloadFile(entry, targetFile)
            fail("Should throw exception for SHA mismatch")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("SHA256 mismatch"))
        }
        assertFalse(targetFile.exists())
    }
}
