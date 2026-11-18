package ru.voboost.tests.delivery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared inventory/structure checks for a macOS package host-resource layout.
 *
 * <p>Consumes what the packaging producer emits (see the installer's
 * {@code verifyMacPackageRoot}): every {@code Resources} entry is a regular
 * non-symlink file, host resources are exactly the icon and readable license
 * set, and every remaining file is a closure artifact whose name, size and
 * SHA-256 must match the signed manifest. These checks make no claims about
 * an actual mounted DMG; tests and callers must state that boundary.
 */
public final class DeliveryLayout {
    private DeliveryLayout() {}

    /**
     * Host resources the macOS package adds on top of the closure; mirrors
     * MAC_LICENSE_NAMES + MAC_ICON_NAME of the installer packaging source.
     */
    public static final Set<String> HOST_RESOURCES = Set.of(
            "icon.icns",
            "installer-native-adb-NOTICES.txt",
            "android-jvm-NOTICES.txt",
            "rust-standard-library-NOTICES.html",
            "native-runtime-NOTICES.txt",
            "native-rust-NOTICES.txt",
            "LICENSE",
            "LICENSE.ru.md",
            "yyjson-LICENSE.txt",
            "lsplant-LICENSE.txt",
            "dobby-LICENSE.txt",
            "libsepol-COPYING.txt",
            "installer-frontend-NOTICES.txt",
            "installer-mac-rust-NOTICES.txt");

    public static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Verifies a directory as a product closure: every entry is a regular
     * non-symlink file, manifest artifacts match size and SHA-256, and no
     * file is missing or unexpected (host files are ignored on this side,
     * matching the installer consumer that filters them out).
     *
     * @param allowedFiles extra names permitted beside the closure artifacts
     *                     (for example voboost.json/voboost.json.sig and host
     *                     resources when verifying a Resources directory)
     */
    public static void verifyClosureDirectory(Path directory, DeliveryManifest.Document manifest,
            Set<String> allowedFiles) throws Exception {
        Set<String> expected = new TreeSet<>();
        for (DeliveryManifest.Artifact artifact : manifest.artifacts()) expected.add(artifact.file());
        expected.addAll(allowedFiles);

        Set<String> actual = new TreeSet<>();
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.collect(Collectors.toList())) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("closure contains a non-regular file: " + entry);
                }
                actual.add(entry.getFileName().toString());
            }
        } catch (IOException error) {
            throw new IllegalStateException("read closure " + directory, error);
        }
        if (!actual.equals(expected)) {
            throw new IllegalStateException("closure inventory differs from voboost.json: expected "
                    + expected + ", found " + actual);
        }
        for (DeliveryManifest.Artifact artifact : manifest.artifacts()) {
            Path path = directory.resolve(artifact.file());
            byte[] bytes = Files.readAllBytes(path);
            if (Files.size(path) != artifact.size()) {
                throw new IllegalStateException("artifact " + artifact.file() + " size mismatch");
            }
            if (!sha256Hex(bytes).equals(artifact.sha256())) {
                throw new IllegalStateException("artifact " + artifact.file() + " SHA-256 mismatch");
            }
        }
    }

    /** Verifies a macOS Resources directory: host resources plus closure artifacts. */
    public static void verifyPackageResources(Path resources,
            DeliveryManifest.Document manifest) throws Exception {
        Set<String> hostAndManifest = new TreeSet<>(HOST_RESOURCES);
        hostAndManifest.add("voboost.json");
        hostAndManifest.add("voboost.json.sig");
        verifyClosureDirectory(resources, manifest, hostAndManifest);
    }

    /** Verifies a bare closure directory (voboost.json + sig + artifacts, nothing else). */
    public static void verifyClosureDirectory(Path directory, DeliveryManifest.Document manifest)
            throws Exception {
        verifyClosureDirectory(directory, manifest, Set.of());
    }
}
