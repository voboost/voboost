package ru.voboost.tests.delivery;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.DeliveryChecks;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.MountSession;
import ru.voboost.tests.delivery.DeliveryLayout;

class GenericCandidateDmgTest {
    // Host resources are centrally owned by DeliveryLayout (packaging contract:
    // icon + readable licenses); everything else is sent to the release verifier.
    private static final Set<String> HOST_RESOURCES = DeliveryLayout.HOST_RESOURCES;
    private static Path input(String name) {
        String value = System.getProperty(name);
        assertTrue(value != null && Path.of(value).isAbsolute(), "BLOCKED: absolute -P" + name + " required");
        Path path = Path.of(value);
        assertTrue(Files.exists(path), "BLOCKED: missing " + name);
        return path;
    }
    private static HostProcess.Result run(Path log, List<String> command) throws Exception {
        Path retained = Files.createTempFile(log.getParent(), "action-", ".log");
        return HostProcess.run(retained, command, 60000);
    }
    private static Set<String> names(Path directory) throws IOException {
        try (var paths = Files.list(directory)) { return paths.map(p -> p.getFileName().toString()).collect(Collectors.toSet()); }
    }
    private static boolean mounted(Path mount) throws IOException {
        return !Files.getAttribute(mount, "unix:dev").equals(Files.getAttribute(mount.getParent(), "unix:dev"));
    }
    @Test void verifiesMountedCandidateAndRejectsMissingClosureFile() throws Exception {
        Path dmg = input("candidateDmg");
        Path root = input("productPublicRoot");
        Path verifier = Path.of(System.getProperty("releaseVerifier"));
        Path workRoot = Path.of(System.getProperty("testWorkDir"));
        Files.createDirectories(workRoot);
        Path work = Files.createTempDirectory(workRoot, "candidate-");
        Path mount = work.resolve("mount");
        MountSession session = new MountSession(work, new MountSession.Backend() {
            public void attach() throws Exception {
                HostProcess.Result result = run(work.resolve("command.log"), List.of("hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", mount.toString(), dmg.toString()));
                if (result.exit() != 0) throw new IOException("attach failed: " + result.output());
            }
            public boolean mounted() throws Exception { return Files.exists(mount) && GenericCandidateDmgTest.mounted(mount); }
            public void detach() throws Exception {
                HostProcess.Result result = run(work.resolve("detach.log"), List.of("hdiutil", "detach", mount.toString()));
                if (result.exit() != 0) throw new IOException("detach failed: " + result.output());
            }
            public void removeWork() throws Exception {
                Path copy = work.resolve("closure");
                if (Files.exists(copy)) try (var paths = Files.walk(copy)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                Files.deleteIfExists(mount);
                Files.deleteIfExists(work.resolve("wrong-root.pub"));
            }
        });
        try {
            Files.createDirectory(mount);
            Path closure = Files.createDirectory(work.resolve("closure"));
            Path log = work.resolve("command.log");
            session.attach();
            DeliveryChecks.requireDirectory(mount);
            DeliveryChecks.requireExactNames(names(mount), Set.of("Voboost Installer.app", "voboost-installer"));
            Path app = mount.resolve("Voboost Installer.app");
            DeliveryChecks.requireDirectory(app);
            assertEquals("Voboost Installer.app/Contents/MacOS/voboost-installer", Files.readSymbolicLink(mount.resolve("voboost-installer")).toString());
            DeliveryChecks.requireExactNames(names(app), Set.of("Contents"));
            Path contents = app.resolve("Contents");
            DeliveryChecks.requireDirectory(contents);
            DeliveryChecks.requireDirectory(contents.resolve("MacOS"));
            DeliveryChecks.requireDirectory(contents.resolve("Resources"));
            DeliveryChecks.requireDirectory(contents.resolve("_CodeSignature"));
            DeliveryChecks.requireRegular(contents.resolve("Info.plist"));
            DeliveryChecks.requireRegular(contents.resolve("MacOS/voboost-installer"));
            DeliveryChecks.requireRegular(contents.resolve("_CodeSignature/CodeResources"));
            DeliveryChecks.requireExactNames(names(contents), Set.of("Info.plist", "MacOS", "Resources", "_CodeSignature"));
            DeliveryChecks.requireExactNames(names(contents.resolve("MacOS")), Set.of("voboost-installer"));
            DeliveryChecks.requireExactNames(names(contents.resolve("_CodeSignature")), Set.of("CodeResources"));
            HostProcess.Result packaged = run(log, List.of(contents.resolve("MacOS/voboost-installer").toString(), "--verify-package"));
            assertEquals(0, packaged.exit(), packaged.output());
            Path executable = contents.resolve("MacOS/voboost-installer");
            HostProcess.Result help = run(log, List.of(executable.toString(), "--help"));
            assertEquals(0, help.exit(), help.output());
            assertTrue(help.output().contains("--device"), "packaged help must expose explicit device selection");
            for (List<String> invalidArguments : List.of(List.of("--not-a-valid-option"), List.of("--install", "--uninstall"),
                    List.of("--device"), List.of("--mode", "not-a-mode"))) {
                List<String> command = new java.util.ArrayList<>(List.of(executable.toString())); command.addAll(invalidArguments);
                HostProcess.Result rejected = run(log, command);
                assertEquals(1, rejected.exit(), "packaged CLI must reject invalid arguments before device work: " + rejected.output());
                assertFalse(rejected.output().isBlank());
            }
            Path resources = contents.resolve("Resources");
            assertTrue(names(resources).containsAll(HOST_RESOURCES));
            try (var entries = Files.list(resources)) {
                for (Path entry : entries.toList()) {
                    assertTrue(Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS), "non-regular resource: " + entry);
                    if (!HOST_RESOURCES.contains(entry.getFileName().toString())) Files.copy(entry, closure.resolve(entry.getFileName()), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            List<String> verify = List.of(verifier.toString(), "verify-product-closure", closure.toString(), root.toString());
            HostProcess.Result valid = run(log, verify);
            assertEquals(0, valid.exit(), valid.output());
            ru.voboost.tests.RunFiles.write(workRoot.resolve("candidate-agents.json"), ru.voboost.tests.RunFiles.read(closure.resolve("agents.json")));
            byte[] publicKey = Files.readAllBytes(root);
            assertEquals(32, publicKey.length, "expected raw public key");
            publicKey[0] ^= 1;
            Path wrongRoot = work.resolve("wrong-root.pub");
            Files.write(wrongRoot, publicKey);
            HostProcess.Result wrong = run(log, List.of(verifier.toString(), "verify-product-closure", closure.toString(), wrongRoot.toString()));
            assertEquals(1, wrong.exit(), wrong.output());
            assertTrue(wrong.output().contains("verify product manifest signature"), wrong.output());
            Path artifact = closure.resolve("voboost.rc");
            byte[] original = Files.readAllBytes(artifact);
            byte[] modified = original.clone();
            modified[0] ^= 1;
            Files.write(artifact, modified);
            HostProcess.Result corrupted = run(log, verify);
            assertEquals(1, corrupted.exit(), corrupted.output());
            assertTrue(corrupted.output().contains("artifact voboost.rc SHA-256 mismatch"), corrupted.output());
            Files.write(artifact, original);
            Path extra = Files.writeString(closure.resolve("unexpected"), "extra");
            HostProcess.Result added = run(log, verify);
            DeliveryChecks.requireInventoryRejection(added.exit(), added.output());
            Files.delete(extra);
            Files.delete(closure.resolve("voboost.json.sig"));
            HostProcess.Result invalid = run(log, verify);
            DeliveryChecks.requireInventoryRejection(invalid.exit(), invalid.output());
        } finally {
            session.cleanup();
        }
        try (var logs = Files.list(work)) {
        ru.voboost.tests.CaseEvidence.record(new ru.voboost.tests.AcceptancePlan.Key("I01", "product"),
            new ru.voboost.tests.AcceptanceReport.Result(ru.voboost.tests.GateStatus.PASS,
                "Verified actual DMG, packaged CLI validation, signed closure and corrupt/missing/extra input rejection",
                logs.filter(path -> path.getFileName().toString().endsWith(".log")).map(Path::toAbsolutePath).toList()));
        }
    }
}
