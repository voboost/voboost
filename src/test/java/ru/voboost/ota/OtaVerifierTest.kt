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
 * rejected, invalid component rejected, oversized manifest rejected, too-many-
 * entries rejected, and the {app, inject} components accepted (mapped to
 * APP/CORE channels).
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
                releases =
                    listOf(
                        OtaTestUtils.TestApkEntry(
                            component = "inject",
                            track = "production",
                            downloadUrl = "https://host/voboost-inject.apk",
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
        assertEquals(1, result.releases.size)
        assertEquals(Channel.CORE, result.releases[0].channel)
    }

    @Test
    fun testInvalidSignatureIsRejected() {
        val manifest =
            OtaTestUtils.createTestManifest(
                releases = emptyList(),
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
        // Create a unified-schema manifest with a missing "size" field.
        val releasesArray = JSONArray()
        val entry = JSONObject()
        entry.put("component", "app")
        entry.put("track", "production")
        entry.put("downloadUrl", "https://host/voboost.apk")
        entry.put("sha256", "abc123")
        // Missing "size" field
        entry.put("version", "1.0.0")
        releasesArray.put(entry)

        val invalidJson = JSONObject()
        invalidJson.put("schemaVersion", 1)
        invalidJson.put("generatedAt", "")
        invalidJson.put("releases", releasesArray)

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
            assertTrue(e.message!!.contains("Invalid release entry"))
        }
    }

    @Test
    fun testInvalidComponentRejectsManifest() {
        // An entry whose component is "installer" (dropped) is rejected even
        // with a valid signature.
        val releasesArray = JSONArray()
        val entry = JSONObject()
        entry.put("component", "installer") // Invalid component (dropped)
        entry.put("track", "production")
        entry.put("downloadUrl", "https://host/voboost.apk")
        entry.put("sha256", "abc123")
        entry.put("size", 1024)
        entry.put("version", "1.0.0")
        releasesArray.put(entry)

        val invalidJson = JSONObject()
        invalidJson.put("schemaVersion", 1)
        invalidJson.put("generatedAt", "")
        invalidJson.put("releases", releasesArray)

        val manifestJson = invalidJson.toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val verifier = OtaVerifier.fromPublicKeyFile(testPublicKeyFile)

        try {
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )
            fail("Should throw exception for invalid component")
        } catch (e: OtaException) {
            assertTrue(e.message!!.contains("Invalid component"))
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
        val releasesArray = JSONArray()
        for (i in 0..4100) {
            val entry = JSONObject()
            entry.put("component", "app")
            entry.put("track", "production")
            entry.put("downloadUrl", "https://host/voboost-$i.apk")
            entry.put("sha256", "abc123")
            entry.put("size", 1024)
            entry.put("version", "1.0.0")
            releasesArray.put(entry)
        }

        val invalidJson = JSONObject()
        invalidJson.put("schemaVersion", 1)
        invalidJson.put("generatedAt", "")
        invalidJson.put("releases", releasesArray)

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
                releases = emptyList(),
            )

        val manifestJson = manifest.toJson().toString()
        val signature = OtaTestUtils.signManifest(manifestJson, testPrivateKeyPem)

        val result =
            verifier.verify(
                manifestJson.toByteArray(Charsets.UTF_8),
                signature,
            )

        assertNotNull(result)
    }

    @Test
    fun testAppAndInjectComponentsAccepted() {
        // Only {app, inject} components are valid; both map to channels.
        val components = listOf("app", "inject")
        val expectedChannels = listOf(Channel.APP, Channel.CORE)

        for ((component, expectedChannel) in components.zip(expectedChannels)) {
            val manifest =
                OtaTestUtils.createTestManifest(
                    releases =
                        listOf(
                            OtaTestUtils.TestApkEntry(
                                component = component,
                                track = "production",
                                downloadUrl = "https://host/voboost.apk",
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
            assertEquals(1, result.releases.size)
            assertEquals(expectedChannel, result.releases[0].channel)
        }
    }
}
