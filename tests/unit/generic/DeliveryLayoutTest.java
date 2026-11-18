package ru.voboost.tests.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.delivery.DeliveryManifest.Document;

/**
 * Structure checks of a product closure directory against the signed manifest
 * (voboost.json): regular files only, no missing/extra artifacts, size and
 * SHA-256 identity — on a small synthetic fixture derived from the real
 * manifest fixture. This verifies the host-side layout contract; a real DMG
 * layout is only checked by the integration delivery case after mounting.
 */
class DeliveryLayoutTest {
    private static final Path MANIFEST_FIXTURE = Path.of("release", "fixtures", "voboost", "valid", "minimal.json");

    private byte[] manifestBytes() throws Exception {
        byte[] bytes = Files.readAllBytes(MANIFEST_FIXTURE);
        assertTrue(bytes.length > 0 && bytes[bytes.length - 1] == '\n');
        return bytes;
    }

    private List<String> artifactFiles(Document manifest) {
        List<String> files = new ArrayList<>();
        for (var artifact : manifest.artifacts()) files.add(artifact.file());
        return files;
    }

    private void materializeAllArtifacts(Path directory, Document manifest) throws Exception {
        materializeAllArtifacts(directory, manifest, 0);
    }

    /** Writes deterministic bytes whose declared size and digest then match. */
    private void materializeAllArtifacts(Path directory, Document manifest, int flipIndex) throws Exception {
        int index = 0;
        for (var artifact : manifest.artifacts()) {
            byte[] content = deterministicContent(artifact.file(), (int) artifact.size());
            if (index == flipIndex) content[0] ^= 1;
            Files.write(directory.resolve(artifact.file()), content);
            index++;
        }
    }

    private byte[] deterministicContent(String name, int size) {
        // Deterministic per-name bytes; SHA-256 is fixed up below in metadata.
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) (name.hashCode() >>> (i % 32));
        return bytes;
    }

    /** Rewrites declared digest/size fields so they match materialized content. */
    private Document rematerializeManifest(Path work, Path directory, String text) throws Exception {
        Document manifest = parse(text);
        StringBuilder out = new StringBuilder(text);
        for (var artifact : manifest.artifacts()) {
            rematerializeArtifact(out, artifact, directory);
        }
        Path file = Files.createTempFile(work, "voboost-", ".json");
        Files.write(file, out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return DeliveryManifest.verify(file);
    }

    private void rematerializeArtifact(StringBuilder text, DeliveryManifest.Artifact artifact, Path directory)
            throws Exception {
        String filePart = "\"" + artifact.file() + "\"";
        int fileAt = text.indexOf(filePart);
        assertTrue(fileAt >= 0, artifact.file() + " must be declared");
        int objectStart = text.lastIndexOf("{", fileAt);
        int objectEnd = text.indexOf("}", fileAt);
        assertTrue(objectStart >= 0 && objectEnd > fileAt);
        byte[] content = Files.readAllBytes(directory.resolve(artifact.file()));
        String digest = java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        String object = text.substring(objectStart, objectEnd + 1);
        String updated = object;
        updated = updated.replaceFirst("\"size\":[0-9]+", "\"size\":" + content.length);
        updated = updated.replaceFirst("\"sha256\":\"[0-9a-f]{64}\"", "\"sha256\":\"" + digest + "\"");
        text.replace(objectStart, objectEnd + 1, updated);
    }

    private Document parse(String text) throws Exception {
        Path file = Files.createTempFile(work(), "voboost-", ".json");
        Files.write(file, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return DeliveryManifest.verify(file);
    }

    private Path work() throws Exception {
        Path parent = Path.of("build", "layout-test");
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "case-");
    }

    @Test void acceptsExactClosureThenRejectsLayoutTampering() throws Exception {
        Path work = work();
        Path directory = Files.createDirectory(work.resolve("closure"));
        String text = new String(manifestBytes(), java.nio.charset.StandardCharsets.UTF_8);
        materializeAllArtifacts(directory, parse(text));
        Document manifest = rematerializeManifest(work, directory, text);
        byte[] manifestFile = Files.readAllBytes(MANIFEST_FIXTURE);
        Path signedPath = directory.resolve("voboost.json");
        Path signaturePath = directory.resolve("voboost.json.sig");
        // voboost.json.sig is not part of minimal.json artifacts; recreate it as
        // a plain 64-byte placeholder so the closure is complete on disk.
        Files.write(signaturePath, new byte[64]);
        byte[] manifestOriginal = Files.readAllBytes(MANIFEST_FIXTURE);
        Files.write(signedPath, manifestFile);
        DeliveryLayout.verifyClosureDirectory(directory, manifest, java.util.Set.of("voboost.json", "voboost.json.sig"));

        // Missing required artifact.
        Files.delete(directory.resolve("voboost-daemon"));
        assertThrows(IllegalStateException.class,
                () -> DeliveryLayout.verifyClosureDirectory(directory, manifest,
                        java.util.Set.of("voboost.json", "voboost.json.sig")));
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] originalBytes = deterministicContent("voboost-daemon", (int) manifest.artifacts().stream()
                .filter(a -> a.file().equals("voboost-daemon")).findFirst().orElseThrow().size());
        Files.write(directory.resolve("voboost-daemon"), originalBytes);

        // Extra file that no manifest allows.
        Files.writeString(directory.resolve("unexpected"), "extra");
        assertThrows(IllegalStateException.class,
                () -> DeliveryLayout.verifyClosureDirectory(directory, manifest,
                        java.util.Set.of("voboost.json", "voboost.json.sig")));
        Files.delete(directory.resolve("unexpected"));

        // Corrupted artifact content (size same digest differs).
        Path artifact = directory.resolve("voboost-app.apk");
        byte[] content = Files.readAllBytes(artifact);
        content[content.length / 2] ^= 1;
        Files.write(artifact, content);
        assertThrows(IllegalStateException.class,
                () -> DeliveryLayout.verifyClosureDirectory(directory, manifest,
                        java.util.Set.of("voboost.json", "voboost.json.sig")));
    }

    @Test void rejectsSymlinkArtifactAndRenamedArtifact() throws Exception {
        Path work = work();
        Path directory = Files.createDirectory(work.resolve("closure"));
        String text = new String(manifestBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Document baseline = parse(text);
        materializeAllArtifacts(directory, baseline);
        Document manifest = rematerializeManifest(work, directory, text);

        // Symlink replacement for a real artifact must be rejected as non-regular.
        Path target = directory.resolve("voboost-install");
        byte[] content = Files.readAllBytes(target);
        Path outside = Files.write(work.resolve("outside.bin"), content);
        Files.delete(target);
        Files.createSymbolicLink(target, outside);
        assertThrows(IllegalStateException.class,
                () -> DeliveryLayout.verifyClosureDirectory(directory, manifest,
                        java.util.Set.of("voboost.json", "voboost.json.sig")));
        Files.delete(target);
        Files.write(target, content);

        // Renamed artifact leaves both a missing and an unexpected name.
        Path renamed = directory.resolve("voboost-uninstall");
        Files.move(renamed, work.resolve("moved.bin"));
        assertThrows(IllegalStateException.class,
                () -> DeliveryLayout.verifyClosureDirectory(directory, manifest,
                        java.util.Set.of("voboost.json", "voboost.json.sig")));
    }

    @Test void packageResourcesAcceptsHostResourcesAndClosureArtifacts() throws Exception {
        Path work = work();
        Path resources = Files.createDirectory(work.resolve("Resources"));
        String text = new String(manifestBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Document baseline = parse(text);
        materializeAllArtifacts(resources, baseline);
        Document manifest = rematerializeManifest(work, resources, text);
        Files.write(resources.resolve("voboost.json"), manifestBytes());
        Files.write(resources.resolve("voboost.json.sig"), new byte[64]);
        for (String host : DeliveryLayout.HOST_RESOURCES) {
            Files.writeString(resources.resolve(host), "placeholder");
        }
        // Complete host-resource set passes.
        DeliveryLayout.verifyPackageResources(resources, manifest);

        // Extra file masquerading as a resource is still unexpected.
        Files.writeString(resources.resolve("extra-host-resource.txt"), "x");
        assertThrows(IllegalStateException.class, () -> DeliveryLayout.verifyPackageResources(resources, manifest));
        Files.delete(resources.resolve("extra-host-resource.txt"));

        // Missing one host resource is caught by the inventory check.
        Files.delete(resources.resolve("LICENSE"));
        assertThrows(IllegalStateException.class, () -> DeliveryLayout.verifyPackageResources(resources, manifest));
    }

    @Test void hostResourceSetIsThePackagingContract() {
        // Consumer contract of the macOS package: icon plus readable licenses,
        // stable and explicitly enumerated; no wildcard or test-only entries.
        assertEquals(14, DeliveryLayout.HOST_RESOURCES.size());
        assertTrue(DeliveryLayout.HOST_RESOURCES.contains("icon.icns"));
        assertTrue(DeliveryLayout.HOST_RESOURCES.contains("LICENSE"));
        assertTrue(DeliveryLayout.HOST_RESOURCES.contains("installer-mac-rust-NOTICES.txt"));
        assertFalse(DeliveryLayout.HOST_RESOURCES.contains("voboost.json"));
        assertFalse(DeliveryLayout.HOST_RESOURCES.stream().anyMatch(name -> name.endsWith(".dex")));
    }
}
