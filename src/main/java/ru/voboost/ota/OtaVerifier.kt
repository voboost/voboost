package ru.voboost.ota

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import ru.voboost.Logger
import java.io.File
import java.io.StringReader
import java.security.SecureRandom

/**
 * Verifies release manifests using ed25519 signatures.
 *
 * Uses BouncyCastle for ed25519 verification (API 28 compatible).
 * Verifies detached signatures (.sig files) against public keys.
 */
class OtaVerifier private constructor(
    private val publicKey: Ed25519PublicKeyParameters,
) {
    companion object {
        private const val LOG = "OtaVerifier"

        /**
         * Create verifier from public key PEM content.
         */
        fun fromPublicKeyPem(publicKeyPem: String): OtaVerifier {
            val publicKey =
                PemReader(StringReader(publicKeyPem)).use { pemReader ->
                    val pemObject = pemReader.readPemObject()
                    decodePublicKey(pemObject)
                }
            return OtaVerifier(publicKey)
        }

        /**
         * Create verifier from public key file.
         */
        fun fromPublicKeyFile(publicKeyFile: File): OtaVerifier {
            val publicKeyPem = publicKeyFile.readText()
            return fromPublicKeyPem(publicKeyPem)
        }

        /**
         * Decode public key from PEM object.
         */
        private fun decodePublicKey(pemObject: PemObject): Ed25519PublicKeyParameters {
            val keyInfo = SubjectPublicKeyInfo.getInstance(pemObject.content)
            val key = PublicKeyFactory.createKey(keyInfo)
            if (key is Ed25519PublicKeyParameters) {
                return key
            }
            throw OtaException("Invalid public key type: ${key.javaClass.name}")
        }
    }

    /**
     * Verify a release manifest with its detached signature.
     *
     * @param manifestContent Manifest JSON content
     * @param signatureContent Detached signature content
     * @return Verified release manifest
     * @throws OtaException if verification fails
     */
    fun verify(
        manifestContent: ByteArray,
        signatureContent: ByteArray,
    ): ReleaseManifest {
        // Check size bounds before parsing
        if (manifestContent.size > ReleaseManifest.MAX_SIZE_BYTES) {
            Logger.error(LOG, "Manifest too large: ${manifestContent.size} bytes")
            throw OtaException(
                "Manifest exceeds maximum size: " +
                    "${manifestContent.size} > ${ReleaseManifest.MAX_SIZE_BYTES}",
            )
        }

        // Verify signature
        if (!verifySignature(manifestContent, signatureContent)) {
            Logger.error(LOG, "Invalid signature")
            throw OtaException("Invalid signature")
        }

        Logger.debug(LOG, "Signature verified successfully")

        // Parse manifest
        val manifestJson =
            try {
                org.json.JSONObject(String(manifestContent, Charsets.UTF_8))
            } catch (e: Exception) {
                Logger.error(LOG, "Failed to parse manifest JSON: ${e.message}")
                throw OtaException("Failed to parse manifest JSON: ${e.message}", e)
            }

        // Validate and create manifest
        return try {
            ReleaseManifest.fromJson(manifestJson)
        } catch (e: Exception) {
            Logger.error(LOG, "Invalid manifest structure: ${e.message}")
            throw e
        }
    }

    /**
     * Verify signature using ed25519.
     */
    private fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
    ): Boolean {
        return try {
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            Logger.error(LOG, "Signature verification failed: ${e.message}")
            false
        }
    }
}

/**
 * Generate ed25519 keypair for testing/build-time signing.
 */
object OtaKeyGenerator {
    /**
     * Generate a new ed25519 keypair.
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val secureRandom = SecureRandom()
        val privateKey = ByteArray(32)
        val publicKey = ByteArray(32)
        secureRandom.nextBytes(privateKey)

        // Derive public key from private key
        val params = Ed25519PublicKeyParameters(privateKey, 0)
        System.arraycopy(params.encoded, 0, publicKey, 0, 32)

        return Pair(privateKey, publicKey)
    }

    /**
     * Sign data with private key.
     */
    fun sign(
        data: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        val signer = Ed25519Signer()
        val keyParams =
            org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKey, 0)
        signer.init(true, keyParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    /**
     * Convert public key to PEM format.
     */
    fun publicKeyToPem(publicKey: ByteArray): String {
        val oid = org.bouncycastle.asn1.ASN1ObjectIdentifier("1.3.101.112")
        val algorithmIdentifier = org.bouncycastle.asn1.x509.AlgorithmIdentifier(oid, null)
        val keyInfo = SubjectPublicKeyInfo(algorithmIdentifier, publicKey)
        val pemObject = PemObject("PUBLIC KEY", keyInfo.encoded)
        val pemWriter =
            java.io.StringWriter().let { writer ->
                org.bouncycastle.util.io.pem.PemWriter(writer).use { pem ->
                    pem.writeObject(pemObject)
                }
                writer.toString()
            }
        return pemWriter.trimIndent()
    }

    /**
     * Convert private key to PEM format.
     */
    fun privateKeyToPem(privateKey: ByteArray): String {
        val oid = org.bouncycastle.asn1.ASN1ObjectIdentifier("1.3.101.112")
        val algorithmIdentifier = org.bouncycastle.asn1.x509.AlgorithmIdentifier(oid, null)
        val privateKeyOctetString = org.bouncycastle.asn1.DEROctetString(privateKey)
        val keyInfo = PrivateKeyInfo(algorithmIdentifier, privateKeyOctetString)
        val pemObject = PemObject("PRIVATE KEY", keyInfo.encoded)
        val pemWriter =
            java.io.StringWriter().let { writer ->
                org.bouncycastle.util.io.pem.PemWriter(writer).use { pem ->
                    pem.writeObject(pemObject)
                }
                writer.toString()
            }
        return pemWriter.trimIndent()
    }
}
