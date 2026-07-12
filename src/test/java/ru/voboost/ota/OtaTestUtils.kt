package ru.voboost.ota

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.File
import java.io.StringWriter
import java.security.SecureRandom

/**
 * Test utilities for APK-level OTA tests.
 */
object OtaTestUtils {
    /**
     * Generate a test ed25519 keypair as (privateKeyPem, publicKeyPem).
     */
    fun generateTestKeyPair(): Pair<String, String> {
        val secureRandom = SecureRandom()
        val privateKeyBytes = ByteArray(32)
        secureRandom.nextBytes(privateKeyBytes)

        val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        val publicKeyBytes = privateKeyParams.generatePublicKey().encoded

        val privateKeyPem = encodePrivateKeyPem(privateKeyBytes)
        val publicKeyPem = encodePublicKeyPem(publicKeyBytes)

        return Pair(privateKeyPem, publicKeyPem)
    }

    /**
     * Encode a private key to PEM.
     */
    private fun encodePrivateKeyPem(keyBytes: ByteArray): String {
        val oid = ASN1ObjectIdentifier("1.3.101.112")
        val algorithmIdentifier = AlgorithmIdentifier(oid, null)
        val privateKeyOctetString = DEROctetString(keyBytes)
        val keyInfo = PrivateKeyInfo(algorithmIdentifier, privateKeyOctetString)
        val pemObject = PemObject("PRIVATE KEY", keyInfo.encoded)

        val writer = StringWriter()
        PemWriter(writer).use { it.writeObject(pemObject) }
        return writer.toString().trimIndent()
    }

    /**
     * Encode a public key to PEM.
     */
    private fun encodePublicKeyPem(keyBytes: ByteArray): String {
        val oid = ASN1ObjectIdentifier("1.3.101.112")
        val algorithmIdentifier = AlgorithmIdentifier(oid, null)
        val keyInfo = SubjectPublicKeyInfo(algorithmIdentifier, keyBytes)
        val pemObject = PemObject("PUBLIC KEY", keyInfo.encoded)

        val writer = StringWriter()
        PemWriter(writer).use { it.writeObject(pemObject) }
        return writer.toString().trimIndent()
    }

    /**
     * Sign a manifest string with a private key (detached ed25519).
     */
    fun signManifest(
        manifest: String,
        privateKeyPem: String,
    ): ByteArray {
        val privateKey = decodePrivateKeyPem(privateKeyPem)
        val manifestBytes = manifest.toByteArray(Charsets.UTF_8)

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(manifestBytes, 0, manifestBytes.size)

        return signer.generateSignature()
    }

    /**
     * Decode a private key from PEM.
     */
    internal fun decodePrivateKeyPem(pem: String): Ed25519PrivateKeyParameters {
        val pemReader =
            org.bouncycastle.util.io.pem.PemReader(
                java.io.StringReader(pem.trim()),
            )
        val pemObject = pemReader.readPemObject()

        val keyInfo = PrivateKeyInfo.getInstance(pemObject.content)
        val key = org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(keyInfo)

        if (key is Ed25519PrivateKeyParameters) {
            return key
        }
        throw IllegalArgumentException("Invalid private key type")
    }

    /**
     * Decode a public key from PEM.
     */
    internal fun decodePublicKeyPem(pem: String): Ed25519PublicKeyParameters {
        val pemReader =
            org.bouncycastle.util.io.pem.PemReader(
                java.io.StringReader(pem.trim()),
            )
        val pemObject = pemReader.readPemObject()

        val keyInfo = SubjectPublicKeyInfo.getInstance(pemObject.content)
        val key = org.bouncycastle.crypto.util.PublicKeyFactory.createKey(keyInfo)

        if (key is Ed25519PublicKeyParameters) {
            return key
        }
        throw IllegalArgumentException("Invalid public key type")
    }

    /**
     * Create a test unified-schema release manifest.
     *
     * @param schemaVersion Manifest schema version (default 1)
     * @param generatedAt Manifest generation timestamp (default empty)
     * @param releases APK release entries
     */
    fun createTestManifest(
        schemaVersion: Int = 1,
        generatedAt: String = "",
        releases: List<TestApkEntry> = emptyList(),
    ): ReleaseManifest {
        val entries =
            releases.map { entry ->
                ReleaseFileEntry(
                    downloadUrl = entry.downloadUrl,
                    channel =
                        when (entry.component) {
                            "inject" -> Channel.CORE
                            "app" -> Channel.APP
                            else -> throw IllegalArgumentException(
                                "Invalid component: ${entry.component}",
                            )
                        },
                    track = entry.track,
                    sha256 = entry.sha256,
                    size = entry.size,
                    version = entry.version,
                )
            }

        return ReleaseManifest(
            schemaVersion = schemaVersion,
            generatedAt = generatedAt,
            releases = entries,
        )
    }

    /**
     * A test APK release entry for building a unified-schema manifest.
     *
     * @param component `app` (voboost client) or `inject` (voboost-inject daemon)
     * @param track release track: `dev`/`testing`/`production`
     * @param downloadUrl full URL to the APK (https:// or file://)
     */
    data class TestApkEntry(
        val component: String,
        val track: String,
        val downloadUrl: String,
        val sha256: String,
        val size: Long,
        val version: String,
    )

    /**
     * Create a test APK file with the given content.
     */
    fun createTestApkFile(content: String): File {
        val tempFile = File.createTempFile("ota-test-apk-", ".apk")
        tempFile.writeBytes(content.toByteArray(Charsets.UTF_8))
        return tempFile
    }

    /**
     * Calculate SHA256 of a string (UTF-8).
     */
    fun calculateSha256(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculate SHA256 of bytes.
     */
    fun calculateSha256Bytes(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
