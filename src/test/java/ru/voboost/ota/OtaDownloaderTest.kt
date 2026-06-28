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
 * and the [downloadBytes] helper used for the manifest/signature.
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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = sha256,
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)

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
                path = "/voboost-inject.apk",
                channel = Channel.CORE,
                sha256 = sha256,
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost-inject.apk")
        downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)

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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = sha256,
                // wrong size triggers size pre-check rejection before hashing
                size = 9999L,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)
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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = "wrongsha256",
                size = content.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)
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
                    path = "/voboost.apk",
                    channel = Channel.APP,
                    sha256 = sha1,
                    size = content1.length.toLong(),
                    version = "1.0.0",
                ),
                ReleaseFileEntry(
                    path = "/voboost-inject.apk",
                    channel = Channel.CORE,
                    sha256 = sha2,
                    size = content2.length.toLong(),
                    version = "1.0.0",
                ),
            )

        val results = downloader.downloadFiles(entries, mockWebServer.url("").toString(), tempDir)

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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = "abc123",
                size = 1024,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        try {
            downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)
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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = OtaTestUtils.calculateSha256(""),
                size = 0,
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")

        // Empty body with size 0 and matching SHA256 is valid.
        downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)

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
                path = "/voboost.apk",
                channel = Channel.APP,
                sha256 = sha256,
                size = largeContent.length.toLong(),
                version = "1.0.0",
            )

        val targetFile = File(tempDir, "voboost.apk")
        downloader.downloadFile(entry, mockWebServer.url("").toString(), targetFile)

        assertTrue(targetFile.exists())
        assertEquals(largeContent.length.toLong(), targetFile.length())
        assertEquals(largeContent, targetFile.readText())
    }

    @Test
    fun testDownloadBytesForManifest() {
        val manifestContent = """{"version":"1.0.0","channel":"core","files":[]}"""

        mockWebServer.enqueue(
            MockResponse()
                .setBody(manifestContent)
                .setResponseCode(200),
        )

        val bytes = downloader.downloadBytes(mockWebServer.url("/release-manifest.json").toString())

        assertEquals(manifestContent, String(bytes, Charsets.UTF_8))
    }

    @Test
    fun testDownloadBytesHttpError() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500),
        )

        try {
            downloader.downloadBytes(mockWebServer.url("/release-manifest.json").toString())
            fail("Should throw exception for HTTP error")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Download failed"))
        }
    }
}
