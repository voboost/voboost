package ru.voboost.ota

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [OtaVerifier] (APK-level release manifest verification).
 *
 * Covers: valid signature trusted, invalid signature rejected, missing field
 * rejected, invalid channel rejected, oversized manifest rejected, too-many-
 * entries rejected, and the {app, core} channels accepted.
 */
@RunWith(RobolectricTestRunner::class)
class OtaVerifierTest {
    private lateinit var testPublicKeyFile: java.io.File
    private lateinit var testPrivateKeyPem: String
    private lateinit var testPublicKeyPem: String

    @Before
    fun setup() {
        val (privateKeyPem, publicKeyPem) = OtaTestUtils.generateTestKeyPair()
        testPrivateKeyPem = privateKeyPem
        testPublicKeyPem = publicKeyPem

        testPublicKeyFile = java.io.File.createTempFile("ota-test-public-", ".pem")
        testPublicKeyFile.writeText(testPublicKeyPem)
    }

    @After
    fun cleanup() {
        testPublicKeyFile.delete()
    }

    @Test
    fun testValidSignatureIsTrusted() {
        val manifest =
            OtaTestUtils.createTestManifest(
                version = "1.0.0",
                channel = "core",
                files =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            path = "voboost-inject.apk",
                            channel = "core",
                            sha256 = "abc123",
                            size = 1024,
                            version = "1.0.0",
                        ),
                    ),
            )

        val manifestJson = manifest.toJson().toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)
        val result =
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )

        assertNotNull(result)
        assertEquals("1.0.0", result.version)
        assertEquals(1, result.files.size)
        assertEquals(Channel.CORE, result.files[0].channel)
    }

    @Test
    fun testInvalidSignatureIsRejected() {
        val manifest =
            OtaTestUtils.createTestManifest(
                version = "1.0.0",
                channel = "core",
                files = emptyList(),
            )

        val manifestJson = manifest.toJson().toString()
        val invalidSignature = ByteArray(64) { 0x00 } // All zeros

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                invalidSignature,
            )
            fail("Should throw exception for invalid signature")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Invalid signature"))
        }
    }

    @Test
    fun testMissingFieldRejectsManifest() {
        // Create manifest with missing "size" field
        val filesArray = JSONArray()
        val fileEntry = JSONObject()
        fileEntry.put("path", "voboost.apk")
        fileEntry.put("channel", "app")
        fileEntry.put("sha256", "abc123")
        // Missing "size" field
        fileEntry.put("version", "1.0.0")
        filesArray.put(fileEntry)

        val invalidJson = JSONObject()
        invalidJson.put("version", "1.0.0")
        invalidJson.put("channel", "core")
        invalidJson.put("files", filesArray)

        val manifestJson = invalidJson.toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )
            fail("Should throw exception for missing field")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Invalid file entry"))
        }
    }

    @Test
    fun testInvalidChannelRejectsManifest() {
        // An entry whose channel is "agents" (dropped) is rejected even with a
        // valid signature.
        val filesArray = JSONArray()
        val fileEntry = JSONObject()
        fileEntry.put("path", "voboost.apk")
        fileEntry.put("channel", "agents") // Invalid channel (dropped)
        fileEntry.put("sha256", "abc123")
        fileEntry.put("size", 1024)
        fileEntry.put("version", "1.0.0")
        filesArray.put(fileEntry)

        val invalidJson = JSONObject()
        invalidJson.put("version", "1.0.0")
        invalidJson.put("channel", "core")
        invalidJson.put("files", filesArray)

        val manifestJson = invalidJson.toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )
            fail("Should throw exception for invalid channel")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Invalid channel"))
        }
    }

    @Test
    fun testOversizedManifestIsRejected() {
        // Create manifest larger than 1 MiB
        val largeContent = "x".repeat(2 * 1024 * 1024) // 2 MiB

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                largeContent.toByteArray(Charsets.UTF_8),
                ByteArray(64),
            )
            fail("Should throw exception for oversized manifest")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("exceeds maximum size"))
        }
    }

    @Test
    fun testTooManyEntriesIsRejected() {
        // Create manifest with more than 4096 entries
        val filesArray = JSONArray()
        for (i in 0..4100) {
            val fileEntry = JSONObject()
            fileEntry.put("path", "voboost-$i.apk")
            fileEntry.put("channel", "app")
            fileEntry.put("sha256", "abc123")
            fileEntry.put("size", 1024)
            fileEntry.put("version", "1.0.0")
            filesArray.put(fileEntry)
        }

        val invalidJson = JSONObject()
        invalidJson.put("version", "1.0.0")
        invalidJson.put("channel", "core")
        invalidJson.put("files", filesArray)

        val manifestJson = invalidJson.toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )
            fail("Should throw exception for too many entries")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("exceeds maximum entry count"))
        }
    }

    @Test
    fun testFromPublicKeyPem() {
        val verifier = OtaVerifier.fromPublicKeyPem(testPublicKeyPem)
        assertNotNull(verifier)

        val manifest =
            OtaTestUtils.createTestManifest(
                version = "1.0.0",
                channel = "core",
                files = emptyList(),
            )

        val manifestJson = manifest.toJson().toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val result =
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )

        assertNotNull(result)
        assertEquals("1.0.0", result.version)
    }

    @Test
    fun testAppAndCoreChannelsAccepted() {
        // Only {app, core} channels are valid for APK-level OTA.
        val channels = listOf("app", "core")

        for (channel in channels) {
            val manifest =
                OtaTestUtils.createTestManifest(
                    version = "1.0.0",
                    channel = "core",
                    files =
                        listOf(
                            OtaTestUtils.TestApkEntry(
                                path = "voboost.apk",
                                channel = channel,
                                sha256 = "abc123",
                                size = 1024,
                                version = "1.0.0",
                            ),
                        ),
                )

            val manifestJson = manifest.toJson().toString()
            val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

            val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)
            val result =
                verifier.verify(
                    manifestJson.toByteArray(Charsets.UTF_8),
                    signature,
                )

            assertNotNull(result)
            assertEquals(1, result.files.size)
            assertEquals(channel.uppercase(), result.files[0].channel.name)
        }
    }
}
