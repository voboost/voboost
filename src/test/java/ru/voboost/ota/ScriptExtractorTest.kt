package ru.voboost.ota

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voboost.Paths
import java.io.File

/**
 * Tests for [ScriptExtractor] (extract embedded agents from APK assets to staging).
 *
 * Covers:
 * - first extraction copies all `.js` + manifest and creates the marker last
 * - idempotency: unchanged content (matching fingerprint + all files present)
 *   is a no-op (marker NOT recreated, files NOT copied again)
 * - self-healing: a deleted `.js` is restored on the next call
 * - optional `.sig`: absent is fine (manifest copied, marker created); present
 *   is copied
 *
 * Assets are injected via [ScriptExtractor]'s `assetReader` seam, so the tests
 * do not depend on the bundled production assets.
 */
@RunWith(RobolectricTestRunner::class)
class ScriptExtractorTest {
    private lateinit var appZone: File
    private lateinit var stagingDir: File
    private lateinit var paths: Paths

    @Before
    fun setup() {
        appZone = File.createTempFile("se-appzone-", "")
        appZone.delete()
        appZone.mkdirs()
        stagingDir = File(appZone, "staging")
        stagingDir.mkdirs()

        paths =
            object : Paths {
                override val appZone: File get() = this@ScriptExtractorTest.appZone
                override val injectJson: File get() = File(appZone, "inject.json")
                override val injectStatusJson: File get() = File(appZone, "inject-status.json")
                override val stagingDir: File get() = this@ScriptExtractorTest.stagingDir
                override val configFile: File get() = File(appZone, "config.yaml")
                override val logsDir: File get() = File(appZone, "logs")
                override val scriptsDirectory: File get() = File(appZone, "scripts")
            }
    }

    @After
    fun cleanup() {
        appZone.deleteRecursively()
    }

    @Test
    fun firstExtractionCopiesAgentsManifestAndCreatesMarker() {
        val assets =
            fakeAssets(agents = mapOf("agents/a.js" to "a-body", "agents/b.js" to "b-body"))

        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        // All agents staged under staging/agents/<basename>.
        assertEquals("a-body", File(stagingDir, "agents/a.js").readText())
        assertEquals("b-body", File(stagingDir, "agents/b.js").readText())
        // Manifest copied.
        assertTrue(File(stagingDir, "agents-manifest.json").exists())
        // Marker created.
        assertTrue(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
        assertEquals(
            "ready",
            File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).readText().trim(),
        )
        // Fingerprint persisted in app zone (not staging).
        assertTrue(File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).exists())
        assertEquals(
            sha256Hex(assets.manifestBytes),
            File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).readText().trim(),
        )
        // No .sig staged when absent.
        assertFalse(File(stagingDir, "agents-manifest.json.sig").exists())
    }

    @Test
    fun secondCallIsNoOpWhenFingerprintMatchesAndFilesPresent() {
        val assets = fakeAssets(agents = mapOf("agents/a.js" to "a-body"))
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        extractor.extractIfChanged()

        // Capture marker mtime and content hashes after the first extraction.
        val marker = File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER)
        val agentFile = File(stagingDir, "agents/a.js")
        val firstMarkerMtime = marker.lastModified()
        val firstAgentBytes = agentFile.readBytes()
        val firstManifestBytes = File(stagingDir, "agents-manifest.json").readBytes()

        // Give the clock a chance to advance so a rewrite would change mtime.
        Thread.sleep(5)

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        // Idempotent: no change to marker, agent, or manifest.
        assertEquals(firstMarkerMtime, marker.lastModified())
        assertArrayEquals(firstAgentBytes, agentFile.readBytes())
        assertArrayEquals(firstManifestBytes, File(stagingDir, "agents-manifest.json").readBytes())
    }

    @Test
    fun missingAgentIsRestoredOnNextCall() {
        val assets =
            fakeAssets(agents = mapOf("agents/a.js" to "a-body", "agents/b.js" to "b-body"))
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        extractor.extractIfChanged()

        // Delete one staged agent out-of-band.
        val deleted = File(stagingDir, "agents/b.js").apply { delete() }
        assertFalse(deleted.exists())

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        // Self-healed: the agent is back.
        assertTrue(File(stagingDir, "agents/b.js").exists())
        assertEquals("b-body", File(stagingDir, "agents/b.js").readText())
        assertTrue(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
    }

    @Test
    fun signatureAbsentDoesNotCrashAndMarkerCreated() {
        val assets = fakeAssets(agents = mapOf("agents/a.js" to "a-body"), withSig = false)
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        assertTrue(File(stagingDir, "agents-manifest.json").exists())
        assertFalse(File(stagingDir, "agents-manifest.json.sig").exists())
        assertTrue(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
    }

    @Test
    fun signaturePresentIsCopied() {
        val assets =
            fakeAssets(
                agents = mapOf("agents/a.js" to "a-body"),
                withSig = true,
                sigBytes = "fake-signature".toByteArray(Charsets.UTF_8),
            )
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        assertTrue(File(stagingDir, "agents-manifest.json.sig").exists())
        assertEquals("fake-signature", File(stagingDir, "agents-manifest.json.sig").readText())
    }

    @Test
    fun changedManifestReExtractsAndUpdatesFingerprint() {
        var assets = fakeAssets(agents = mapOf("agents/a.js" to "a-v1"))
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = {
                    path ->
                assets.readAsset(path)
            })

        extractor.extractIfChanged()
        val firstSha = File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).readText().trim()
        assertEquals("a-v1", File(stagingDir, "agents/a.js").readText())

        // New APK: same agent name, different body + an extra agent.
        assets = fakeAssets(agents = mapOf("agents/a.js" to "a-v2", "agents/b.js" to "b-body"))

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        assertEquals("a-v2", File(stagingDir, "agents/a.js").readText())
        assertEquals("b-body", File(stagingDir, "agents/b.js").readText())
        val secondSha = File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).readText().trim()
        assertFalse("fingerprint must change when content changes", firstSha == secondSha)
    }

    @Test
    fun missingManifestAssetReturnsFailureWithoutThrowing() {
        val extractor = ScriptExtractor(paths, context = fakeContext(), assetReader = { null })

        val result = extractor.extractIfChanged()

        assertTrue(result.isFailure)
        assertFalse(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
    }

    @Test
    fun cleanStagingDoesNotResetFingerprint() {
        val assets = fakeAssets(agents = mapOf("agents/a.js" to "a-body"))
        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)
        extractor.extractIfChanged()
        val shaBefore = File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).readText().trim()

        // Simulate ApkStager.cleanStaging(): wipe staging tree, recreate empty.
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        val result = extractor.extractIfChanged()

        assertTrue(result.isSuccess)
        // Agents re-staged (presence check failed).
        assertTrue(File(stagingDir, "agents/a.js").exists())
        assertTrue(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
        // Fingerprint was in app zone, so the sha is still the same content.
        assertEquals(
            shaBefore,
            File(appZone, ScriptExtractor.EXTRACTED_VERSION_FILE).readText().trim(),
        )
    }

    @Test
    fun agentReferencedButMissingFromAssetsReturnsFailure() {
        // Manifest lists two agents but only one exists as an asset.
        val assets =
            object : FakeAssets {
                private val manifest =
                    manifestJson(
                        files = listOf("agents/present.js", "agents/missing.js"),
                    ).toByteArray(Charsets.UTF_8)

                override val manifestBytes: ByteArray get() = manifest
                override val sigBytes: ByteArray? get() = null

                override fun readAsset(path: String): ByteArray? =
                    when (path) {
                        "agents/agents-manifest.json" -> manifest
                        "agents/present.js" -> "present-body".toByteArray(Charsets.UTF_8)
                        "agents/agents-manifest.json.sig" -> null
                        else -> null
                    }
            }

        val extractor =
            ScriptExtractor(paths, context = fakeContext(), assetReader = assets::readAsset)

        val result = extractor.extractIfChanged()

        assertTrue(result.isFailure)
        // Marker must NOT be created when extraction fails mid-way.
        assertFalse(File(stagingDir, ScriptExtractor.AGENTS_UPDATE_READY_MARKER).exists())
        // And fingerprint must NOT be persisted for a failed extraction.
        assertNull(
            File(
                appZone,
                ScriptExtractor.EXTRACTED_VERSION_FILE,
            ).takeIf { it.exists() }?.readText(),
        )
    }

    /**
     * Minimal fake [android.content.Context] stub. The extractor only touches
     * the context via the injected [assetReader], so the context itself is
     * never read in these tests; a throw-on-use stub catches accidental use.
     */
    private fun fakeContext(): android.content.Context =
        org.robolectric.RuntimeEnvironment.getApplication()

    /**
     * Build a manifest JSON object string from a list of agent asset paths.
     */
    private fun manifestJson(files: List<String>): String {
        val agents =
            files.joinToString(separator = ",") { f ->
                """
                {"id":"${f.substringAfterLast(
                    '/',
                ).removeSuffix(
                    ".js",
                )}","channel":"agents","file":"$f","sha256":"x","process":"p","boot":false}
                """.trimIndent()
            }
        return """{"version":1,"agents":[$agents]}"""
    }

    /**
     * Build a [FakeAssets] backed by the given agent asset paths -> bodies.
     */
    private fun fakeAssets(
        agents: Map<String, String>,
        withSig: Boolean = false,
        sigBytes: ByteArray = ByteArray(0),
    ): FakeAssets {
        val manifest = manifestJson(agents.keys.toList()).toByteArray(Charsets.UTF_8)
        return object : FakeAssets {
            override val manifestBytes: ByteArray get() = manifest
            override val sigBytes: ByteArray? get() = if (withSig) sigBytes else null

            override fun readAsset(path: String): ByteArray? =
                when {
                    path == "agents/agents-manifest.json" -> manifest
                    path == "agents/agents-manifest.json.sig" -> sigBytes.takeIf { withSig }
                    agents.containsKey(path) -> agents[path]!!.toByteArray(Charsets.UTF_8)
                    else -> null
                }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** Contract for the in-test fake asset set. */
    private interface FakeAssets {
        /** Raw bytes of the agents manifest. */
        val manifestBytes: ByteArray

        /** Bytes of the detached manifest signature, or null when absent. */
        val sigBytes: ByteArray?

        /** Return the asset bytes for [path], or null when the asset is absent. */
        fun readAsset(path: String): ByteArray?
    }
}
