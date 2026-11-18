package ru.voboost.tests.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.DeliveryChecks;

/**
 * Off-line consumer tests of the real product manifest fixtures produced by
 * the release crate. These prove the host-side contract only; they are not a
 * verification of an actual DMG (nothing is mounted here) and not a proof of
 * production trust (no signatures are checked by this class).
 */
class DeliveryManifestContractTest {
    private static final Path FIXTURES = Path.of("release", "fixtures", "voboost");
    private static final Path VALID = FIXTURES.resolve("valid");
    private static final Path INVALID = FIXTURES.resolve("invalid");

    private static Path tempManifest(Path work, byte[] bytes) throws Exception {
        Path file = Files.createTempFile(work, "voboost-", ".json");
        Files.write(file, bytes);
        return file;
    }

    @Test void splitNativeContractClassifiesInjectSeparatelyFromOwner() throws Exception {
        assertEquals("daemon",DeliveryManifest.classify("voboost-daemon").role().jsonName());
        assertEquals("inject",DeliveryManifest.classify("voboost-inject").role().jsonName());
        assertEquals("native-runtime",DeliveryManifest.classify("voboost-inject").activation().jsonName());
        assertNull(DeliveryManifest.classify("voboost-inject").abi());
        assertTrue(DeliveryManifest.REQUIRED_INVENTORY.containsAll(Set.of("voboost-daemon","voboost-inject")));
    }
    @Test void splitNativeContractRequiresBothArtifactsAndRejectsRoleSubstitution() throws Exception {
        for(Path valid:java.util.List.of(VALID.resolve("minimal.json"),VALID.resolve("version-code-max.json"))) {
            DeliveryManifest.Document document=DeliveryManifest.verify(valid);
            assertEquals(1,document.artifacts().stream().filter(a->a.file().equals("voboost-inject")&&a.role().equals("inject")).count());
            assertEquals(1,document.artifacts().stream().filter(a->a.file().equals("voboost-daemon")&&a.role().equals("daemon")).count());
            for(String required:java.util.List.of("voboost-inject","voboost-daemon")){DocumentBased missing=DocumentBased.of(valid);missing.removeFirst(required);assertThrows(IllegalArgumentException.class,()->missing.verifyDocument(work()));}
            String original=Files.readString(valid);String substituted=original.replace("\"role\":\"inject\"","\"role\":\"daemon\"");assertNotEquals(original,substituted);assertThrows(IllegalArgumentException.class,()->DeliveryManifest.verify(tempManifest(work(),substituted.getBytes(StandardCharsets.UTF_8))));
        }
    }
    @Test void acceptsRealValidFixtures() throws Exception {
        DeliveryManifest.Document minimal = DeliveryManifest.verify(VALID.resolve("minimal.json"));
        assertEquals(1, minimal.schemaVersion());
        assertEquals("1.0.0-beta1", minimal.releaseIdentity());
        assertEquals(21, minimal.artifacts().size());
        assertEquals(2, minimal.compatibility("agentCatalogSchema"));
        DeliveryManifest.Document max = DeliveryManifest.verify(VALID.resolve("version-code-max.json"));
        assertEquals(2_100_000_000L, max.artifacts().get(0).versionCode());
    }

    @Test void rejectsEveryRealInvalidFixture() throws Exception {
        try (var paths = Files.list(INVALID)) {
            var fixtures = paths.filter(Files::isRegularFile).toList();
            assertFalse(fixtures.isEmpty(), "expected invalid fixtures to exist");
            for (Path fixture : fixtures) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> DeliveryManifest.verify(fixture), "fixture " + fixture);
                assertTrue(error.getMessage().startsWith("Product manifest differs: "), fixture.toString());
            }
        }
    }

    @Test void rejectsDuplicateKeysFromRealValidBytes() throws Exception {
        // Producer contract: unique keys inside every object. Duplicate the
        // daemon "size" key from the real fixture bytes; the strict parser
        // must reject the duplicate by itself, not via envelope tampering.
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        String duplicated = text.replace("\"size\":2,", "\"size\":2,\"size\":2,");
        assertFalse(duplicated.equals(text), "fixture must contain the edited size field");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliveryManifest.verify(tempManifest(work(), duplicated.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage().contains("duplicate key size"), error.getMessage());
    }

    @Test void rejectsUnknownExtraFieldsMirroringDenyUnknownFields() throws Exception {
        // The producer deserializes with serde deny_unknown_fields; a manifest
        // with any extra key is rejected by the real verifier, so the host
        // consumer must reject it for that reason, at every object level.
        Path work = work();
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        String root = text.replaceFirst("\\{", "{\"mode\":\"reuse\",");
        assertFalse(root.equals(text));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliveryManifest.verify(tempManifest(work, root.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage().contains("unknown field `mode`"), error.getMessage());
        String artifact = text.replaceFirst("\"activation\":\"package-installer\",",
                "\"activation\":\"package-installer\",\"note\":\"x\",");
        assertFalse(artifact.equals(text));
        error = assertThrows(IllegalArgumentException.class,
                () -> DeliveryManifest.verify(tempManifest(work, artifact.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage().contains("unknown field `note`"), error.getMessage());
        String compatibility = text.replace("\"implantAbi\":1}", "\"implantAbi\":1,\"extraField\":3}");
        assertFalse(compatibility.equals(text));
        error = assertThrows(IllegalArgumentException.class,
                () -> DeliveryManifest.verify(tempManifest(work, compatibility.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage().contains("unknown field `extraField`"), error.getMessage());
    }

    @Test void rejectsNonCanonicalFieldOrderFromRealValidBytes() throws Exception {
        // parse_canonical_json re-serializes through serde: canonical bytes use
        // struct field order, so a reshuffled compatibility object is not a
        // producer manifest even though every key is known.
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        String reordered = text.replaceFirst("\"installerProtocol\":1,\"operationProtocol\":1,",
                "\"operationProtocol\":1,\"installerProtocol\":1,");
        assertFalse(reordered.equals(text));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliveryManifest.verify(tempManifest(work(), reordered.getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.getMessage().contains("field order"), error.getMessage());
    }

    @Test void rejectsResourceApkWithoutDescriptor() throws Exception {
        DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
        fixture.appendArtifact("{\"role\":\"resource-apk\",\"file\":\"resource-i18n-ru-a.apk\",\"size\":18,"
                + "\"sha256\":\"" + "a".repeat(64) + "\",\"activation\":\"agent-generation\"}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.verify(work()));
        assertTrue(error.getMessage().contains("resource APK requires descriptor"), error.getMessage());
    }

    @Test void rejectsResourceDescriptorOnForeignRole() throws Exception {
        DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
        fixture.addResourceDescriptorToFirstAppArtifact();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.verify(work()));
        assertTrue(error.getMessage().contains("resource descriptor on foreign artifact role"), error.getMessage());
    }

    @Test void acceptsValidResourcePairThenRejectsDescriptorTampering() throws Exception {
        // A complete resource APK + ledger pair in the producer shape is
        // accepted; targeted tampering is rejected for the producer's reason.
        DocumentBased validPair = DocumentBased.of(VALID.resolve("minimal.json"));
        validPair.appendArtifact(validResourceApk("a"));
        validPair.appendArtifact(RESOURCE_LEDGER_A);
        assertEquals(23, validPair.verifyDocument(work()).artifacts().size());

        DocumentBased wrongOwner = DocumentBased.of(VALID.resolve("minimal.json"));
        wrongOwner.appendArtifact(validResourceApk("a").replace("\"owner\":\"i18n-ru\"", "\"owner\":\"i18n-en\""));
        wrongOwner.appendArtifact(RESOURCE_LEDGER_A);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> wrongOwner.verify(work()))
                .getMessage().contains("resource APK identity rejected"));

        // The descriptor must reference exactly one declared ledger identity.
        DocumentBased ledgerMismatch = DocumentBased.of(VALID.resolve("minimal.json"));
        ledgerMismatch.appendArtifact(validResourceApk("a")
                .replace("\"size\":10", "\"size\":19"));
        ledgerMismatch.appendArtifact(RESOURCE_LEDGER_A);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ledgerMismatch.verify(work()))
                .getMessage().contains("resource ledger absent or identity differs"));

        // Two descriptors owning the same ledger are rejected by the producer.
        DocumentBased twoOwners = DocumentBased.of(VALID.resolve("minimal.json"));
        twoOwners.appendArtifact(validResourceApk("a"));
        twoOwners.appendArtifact(validResourceApkWithOverlay("b", "b")
                .replace("\"file\":\"resource-i18n-ru-b.json\"", "\"file\":\"resource-i18n-ru-a.json\""));
        twoOwners.appendArtifact(RESOURCE_LEDGER_A);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> twoOwners.verify(work()))
                .getMessage().contains("resource ledger must have exactly one resource APK owner"));

        // A declared ledger without any owning descriptor is orphaned.
        DocumentBased orphanLedger = DocumentBased.of(VALID.resolve("minimal.json"));
        orphanLedger.appendArtifact(validResourceApk("a"));
        orphanLedger.appendArtifact(RESOURCE_LEDGER_A);
        orphanLedger.appendArtifact(RESOURCE_LEDGER_B);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> orphanLedger.verify(work()))
                .getMessage().contains("resource ledger must have exactly one resource APK owner"));

        // Descriptors must not share an overlay package.
        DocumentBased duplicateOverlay = DocumentBased.of(VALID.resolve("minimal.json"));
        duplicateOverlay.appendArtifact(validResourceApk("a"));
        duplicateOverlay.appendArtifact(validResourceApkWithOverlay("b", "a"));
        duplicateOverlay.appendArtifact(RESOURCE_LEDGER_A);
        duplicateOverlay.appendArtifact(RESOURCE_LEDGER_B);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> duplicateOverlay.verify(work()))
                .getMessage().contains("duplicate resource overlay package"));
    }

    @Test void acceptsSixEnglishCorrectionPairsAlongsideTheRussianOwner() throws Exception {
        DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
        String[][] english = {
                {"launcher-a11", "com.qinggan.app.launcher", "launcher.a11"},
                {"launcher-a9", "com.qinggan.app.launcher", "launcher"},
                {"media-a11", "com.qinggan.media", "media.a11"},
                {"media-a9", "com.qinggan.media", "media"},
                {"setting-a11", "com.qinggan.app.setting", "setting.a11"},
                {"systemui-a9", "com.qinggan.systemui", "systemui"}};
        for (String[] owner : english) fixture.appendArtifact(englishResourceApk(owner[0], owner[1], owner[2]));
        fixture.appendArtifact(validResourceApk("a"));
        for (String[] owner : english) {
            fixture.appendArtifact(resourceLedger(owner[0]).replace("resource-i18n-ru-", "resource-i18n-en-"));
        }
        fixture.appendArtifact(RESOURCE_LEDGER_A);
        assertEquals(35, fixture.verifyDocument(work()).artifacts().size());
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.classify("resource-i18n-en-setting-a9.apk"));
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.classify("resource-i18n-en-systemui-a11.json"));
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.classify("qnx-resource-i18n-en-setting-a11.kzb"));
    }

    @Test void rejectsCrossedEnglishOwnerLocaleTargetAndLedgerIdentity() throws Exception {
        String valid = englishResourceApk("setting-a11", "com.qinggan.app.setting", "setting.a11");
        for (String changed : java.util.List.of(
                valid.replace("\"owner\":\"i18n-en\"", "\"owner\":\"i18n-ru\""),
                valid.replace("\"locale\":\"en\"", "\"locale\":\"ru\""),
                valid.replace("com.qinggan.app.setting", "com.qinggan.app.launcher"),
                valid.replace("ru.voboost.translation.setting.a11.en", "ru.voboost.translation.setting.a11"))) {
            DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
            fixture.appendArtifact(changed);
            fixture.appendArtifact(resourceLedger("setting-a11").replace("resource-i18n-ru-", "resource-i18n-en-"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> fixture.verify(work()))
                    .getMessage().contains("resource APK identity rejected"));
        }
        DocumentBased sharedLedger = DocumentBased.of(VALID.resolve("minimal.json"));
        sharedLedger.appendArtifact(valid.replace("resource-i18n-en-setting-a11.json", "resource-i18n-ru-a.json"));
        sharedLedger.appendArtifact(validResourceApk("a"));
        sharedLedger.appendArtifact(RESOURCE_LEDGER_A);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> sharedLedger.verify(work()))
                .getMessage().contains("resource ledger absent or identity differs"));
        DocumentBased foreignLedger = DocumentBased.of(VALID.resolve("minimal.json"));
        foreignLedger.appendArtifact(validResourceApk("a").replace("resource-i18n-ru-a.json", "resource-i18n-en-setting-a11.json"));
        foreignLedger.appendArtifact(resourceLedger("setting-a11").replace("resource-i18n-ru-", "resource-i18n-en-"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> foreignLedger.verify(work()))
                .getMessage().contains("resource ledger absent or identity differs"));
    }

    private static String englishResourceApk(String stem, String target, String overlayStem) {
        return validResourceApkWithOverlay(stem, overlayStem)
                .replace("resource-i18n-ru-", "resource-i18n-en-")
                .replace("\"owner\":\"i18n-ru\"", "\"owner\":\"i18n-en\"")
                .replace("\"locale\":\"ru\"", "\"locale\":\"en\"")
                .replace("\"targetPackage\":\"ru.voboost\"", "\"targetPackage\":\"" + target + "\"")
                .replace("ru.voboost.translation." + overlayStem + "\"", "ru.voboost.translation." + overlayStem + ".en\"");
    }

    private static final String RESOURCE_LEDGER_A =
            resourceLedger("a");

    private static final String RESOURCE_LEDGER_B =
            resourceLedger("b");

    private static String resourceLedger(String stem) {
        return "{\"role\":\"resource-ledger\",\"file\":\"resource-i18n-ru-" + stem + ".json\",\"size\":10,"
                + "\"sha256\":\"" + "b".repeat(64) + "\",\"activation\":\"agent-generation\"}";
    }

    /** Producer-shaped resource APK with its coverage ledger descriptor. */
    private static String validResourceApk(String stem) {
        return validResourceApkWithOverlay(stem, stem);
    }

    private static String validResourceApkWithOverlay(String stem, String overlayStem) {
        // Canonical artifact field order: role, file, size, sha256, activation,
        // packageName, versionCode, certificateSha256, abi(optional), resource.
        String overlayPackage = "ru.voboost.translation." + overlayStem;
        return "{\"role\":\"resource-apk\",\"file\":\"resource-i18n-ru-" + stem + ".apk\",\"size\":19000,"
                + "\"sha256\":\"" + "a".repeat(64) + "\",\"activation\":\"agent-generation\","
                + "\"packageName\":\"" + overlayPackage + "\",\"versionCode\":20022,"
                + "\"certificateSha256\":\"" + "d".repeat(64) + "\","
                + "\"resource\":{\"owner\":\"i18n-ru\",\"locale\":\"ru\",\"targetPackage\":\"ru.voboost\","
                + "\"compatibilitySchema\":1,\"overlayPackage\":\"" + overlayPackage + "\","
                + "\"coverageLedger\":{\"file\":\"resource-i18n-ru-" + stem + ".json\",\"size\":10,"
                + "\"sha256\":\"" + "b".repeat(64) + "\"}}}";
    }


    @Test void rejectsNonCanonicalWhitespaceAndEndings() throws Exception {
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        Path work = work();
        // Pretty-printed whitespace is not canonical compact JSON.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                text.replace("{\"", "{\"").replace(":", " : ").getBytes(StandardCharsets.UTF_8))));
        // Two trailing LFs break the exactly-one-LF envelope.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                (text + "\n").getBytes(StandardCharsets.UTF_8))));
        // No trailing LF at all.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                text.substring(0, text.length() - 1).getBytes(StandardCharsets.UTF_8))));
    }

    @Test void rejectsTruncatedExtraAndBitFlippedRealFixture() throws Exception {
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        Path work = work();
        // Truncated in the middle of the artifacts array.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(
                tempManifest(work, java.util.Arrays.copyOfRange(valid, 0, valid.length / 2))));
        // Extra trailing bytes after the document.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                (new String(valid, StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8))));
        // Flipped byte inside the manifest body; must not validate.
        byte[] flipped = valid.clone();
        int index = flipped.length / 2;
        flipped[index] ^= 1;
        assertThrows(IllegalArgumentException.class, () ->
                DeliveryManifest.verify(tempManifest(work, flipped)));
    }

    @Test void rejectsMissingRequiredFileAndExtraFileInFixtureClosure() throws Exception {
        DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
        // Remove a required file from the declared inventory...
        fixture.removeFirst("voboost-daemon");
        assertThrows(IllegalArgumentException.class, () -> fixture.verify(work()),
                "required artifact voboost-daemon is missing");
        // ...and add a surprising sibling of an allowed resource stem.
        DocumentBased extra = DocumentBased.of(VALID.resolve("minimal.json"));
        extra.addMalformedExtraFile();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> extra.verify(work()));
        // Rejected for the producer's reason: an unpaired same-stem QNX ledger.
        assertTrue(error.getMessage().contains("QNX resource requires exact same-stem pack and ledger"),
                error.getMessage());
        // The pack side of the same-stem peer contract is rejected the same way.
        DocumentBased packAlone = DocumentBased.of(VALID.resolve("minimal.json"));
        packAlone.addQnxPackWithoutLedgerPeer();
        error = assertThrows(IllegalArgumentException.class, () -> packAlone.verify(work()));
        assertTrue(error.getMessage().contains("QNX resource requires exact same-stem pack and ledger"),
                error.getMessage());
        // A complete QNX pack + ledger pair is accepted by the manifest rules.
        DocumentBased pair = DocumentBased.of(VALID.resolve("minimal.json"));
        pair.addQnxResourcePair();
        assertEquals(23, pair.verifyDocument(work()).artifacts().size());
    }

    @Test void rejectsReorderedArtifactsAndRolesFromFixture() throws Exception {
        DocumentBased fixture = DocumentBased.of(VALID.resolve("minimal.json"));
        fixture.swapArtifacts();
        assertThrows(IllegalArgumentException.class, () -> fixture.verify(work()));
    }

    @Test void canonicalizesRealValidBytesToThemselves() throws Exception {
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        String body = text.substring(0, text.length() - 1);
        Object parsed = new DeliveryManifest.Parser(body).parse();
        assertEquals(body, DeliveryManifest.canonicalize(parsed), "canonical round-trip must be byte-stable");
    }

    @Test void rejectsUtf8SurrogateEscapeAndRawControlInStrings() throws Exception {
        Path work = work();
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String unpaired = new String(valid, StandardCharsets.UTF_8).replace("1.0.0-beta1", "1.0.0-\\ud800x");
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                unpaired.getBytes(StandardCharsets.UTF_8))));
        String raw = new String(valid, StandardCharsets.UTF_8).replace("1.0.0-beta1", "1.0.0-b\u0001x");
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                raw.getBytes(StandardCharsets.UTF_8))));
    }

    @Test void rejectsNonIntegerNumbersAndIdentifierEdgeCases() throws Exception {
        Path work = work();
        byte[] valid = Files.readAllBytes(VALID.resolve("minimal.json"));
        String text = new String(valid, StandardCharsets.UTF_8);
        // Float size is not part of the contract.
        assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work, text
                .replace("\"size\":1,", "\"size\":1.5,").getBytes(StandardCharsets.UTF_8))));
        // Leading-zero / zero pre-release identifiers are not canonical SemVer.
        for (String bad : new String[] {"01.0.0-beta1", "1.0.0-01", "1.0.0-beta.01", "1.0.0-beta.00"}) {
            assertThrows(IllegalArgumentException.class, () -> DeliveryManifest.verify(tempManifest(work,
                    text.replace("1.0.0-beta1", bad).getBytes(StandardCharsets.UTF_8))), bad);
        }
        // Canonical spellings must stay accepted.
        DeliveryManifest.verify(tempManifest(work,
                text.replace("1.0.0-beta1", "1.2.3-4.5+build-6").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void sharedDeliveryChecksRangeOverVerifierRejections() {
        // Contract with the release verifier process: inventory mismatch keeps
        // the documented rejection shape.
        assertThrows(IllegalStateException.class,
                () -> DeliveryChecks.requireInventoryRejection(1, "error: unrelated"));
        DeliveryChecks.requireInventoryRejection(1,
                "error: closure inventory differs from voboost.json: expected [a], found [a, b]");
        DeliveryChecks.requireExactNames(Set.of("a", "b"), Set.of("b", "a"));
        assertThrows(IllegalStateException.class,
                () -> DeliveryChecks.requireExactNames(Set.of("a"), Set.of("b")));
    }

    private Path work() throws Exception {
        java.nio.file.Path parent = java.nio.file.Path.of("build", "manifest-test");
        java.nio.file.Files.createDirectories(parent);
        return java.nio.file.Files.createTempDirectory(parent, "case-");
    }

    /** Edits real fixture bytes and re-verifies through the strict consumer. */
    private static final class DocumentBased {
        private String text;

        static DocumentBased of(Path fixture) throws Exception {
            return new DocumentBased(new String(Files.readAllBytes(fixture), StandardCharsets.UTF_8));
        }

        private DocumentBased(String text) {
            // Canonical fixture bytes end with exactly one LF; keep the body
            // only, helpers re-append the envelope when writing temp files.
            this.text = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        }

        Path write(Path work) throws Exception {
            Path file = Files.createTempFile(work, "voboost-", ".json");
            Files.write(file, (text + "\n").getBytes(StandardCharsets.UTF_8));
            return file;
        }

        void removeFirst(String file) {
            int start = text.indexOf("\"file\":\"" + file + "\"");
            assertTrue(start >= 0, "fixture must declare " + file);
            int objectStart = text.lastIndexOf('{', start);
            int objectEnd = text.indexOf('}', start);
            assertTrue(objectStart >= 0 && objectEnd > start);
            String object = text.substring(objectStart, objectEnd + 1);
            text = text.replace(object + ",", "").replace("," + object, "");
        }

        void addMalformedExtraFile() {
            // An unexpected QNX resource ledger references a pack with the same
            // stem, but the producer requires the same-stem .kzb peer to be
            // declared as well. Appended at the end so canonical role order
            // stays intact and only the peer rule proves rejection.
            assertTrue(text.endsWith("]}"));
            text = text.substring(0, text.length() - 2) + ","
                    + "{\"role\":\"qnx-resource-ledger\",\"file\":\"qnx-resource-i18n-ru-extra.json\","
                    + "\"size\":1,\"sha256\":\"" + "0".repeat(64) + "\","
                    + "\"activation\":\"agent-generation\",\"abi\":\"qnx-aarch64\"}]}";
        }

        void addQnxPackWithoutLedgerPeer() {
            // The pack side of the same-stem peer contract.
            assertTrue(text.endsWith("]}"));
            text = text.substring(0, text.length() - 2) + ","
                    + "{\"role\":\"qnx-resource-pack\",\"file\":\"qnx-resource-i18n-ru-extra.kzb\","
                    + "\"size\":1,\"sha256\":\"" + "0".repeat(64) + "\","
                    + "\"activation\":\"agent-generation\",\"abi\":\"qnx-aarch64\"}]}";
        }

        void addQnxResourcePair() {
            assertTrue(text.endsWith("]}"));
            text = text.substring(0, text.length() - 2) + ","
                    + "{\"role\":\"qnx-resource-pack\",\"file\":\"qnx-resource-i18n-ru-extra.kzb\","
                    + "\"size\":1,\"sha256\":\"" + "0".repeat(64) + "\","
                    + "\"activation\":\"agent-generation\",\"abi\":\"qnx-aarch64\"},"
                    + "{\"role\":\"qnx-resource-ledger\",\"file\":\"qnx-resource-i18n-ru-extra.json\","
                    + "\"size\":1,\"sha256\":\"" + "1".repeat(64) + "\","
                    + "\"activation\":\"agent-generation\",\"abi\":\"qnx-aarch64\"}]}";
        }

        void appendArtifact(String artifactJson) {
            assertTrue(text.endsWith("]}"));
            text = text.substring(0, text.length() - 2) + "," + artifactJson + "]}";
        }

        void addResourceDescriptorToFirstAppArtifact() {
            // Splices the descriptor JSON into the app artifact object: the
            // producer rejects any resource field on a non resource-apk role.
            int at = text.indexOf("\"activation\":\"package-installer\"");
            assertTrue(at > 0);
            int objectEnd = text.indexOf('}', at);
            assertTrue(objectEnd > at, "app artifact object must terminate");
            String source = validResourceApkWithOverlay("a", "a");
            int descriptorAt = source.indexOf("\"resource\":");
            assertTrue(descriptorAt > 0, "descriptor helper must contain the resource key");
            String descriptor = source.substring(descriptorAt);
            // Drop the artifact-closing brace: only the resource object is
            // spliced into the app artifact, which keeps its own closing brace.
            descriptor = descriptor.substring(0, descriptor.length() - 1);
            // Spliced at the canonical end of the object: the producer would
            // reject a resource field on role app for that rule, not for order.
            text = new StringBuilder(text).insert(objectEnd, "," + descriptor).toString();
        }

        void swapArtifacts() {
            int first = text.indexOf("\"role\":\"daemon\"");
            int second = text.indexOf("\"role\":\"install\"");
            assertTrue(first > 0 && second > first);
            int firstStart = text.lastIndexOf('{', first);
            int firstEnd = text.indexOf("},", first) + 2;
            String firstObject = text.substring(firstStart, firstEnd);
            int secondStart = text.lastIndexOf('{', second);
            int secondEnd = text.indexOf("},", second) + 2;
            String secondObject = text.substring(secondStart, secondEnd);
            String original=text;
            text=text.substring(0,firstStart)+secondObject+text.substring(firstEnd,secondStart)+firstObject+text.substring(secondEnd);
            assertNotEquals(original,text,"actual swap must not depend on native rows being adjacent");
        }

        DeliveryManifest.Document verifyDocument(Path work) throws Exception {
            return DeliveryManifest.verify(write(work));
        }

        void verify(Path work) throws Exception {
            verifyDocument(work);
        }
    }
}
