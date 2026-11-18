package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetInventoryTest {
    private static final String BOOT = "12345678-1234-1234-1234-123456789abc";
    private static final List<TargetInventory.Package> PACKAGES = List.of(
        new TargetInventory.Package("com.example.one", List.of("com.example.one", "com.example.other:remote")));
    private static TargetInventory.Port port(List<TargetInventory.Package> first, List<TargetInventory.Package> second,
            String after) {
        return new TargetInventory.Port() {
            int boots, reads;
            public String bootId() { return boots++ == 0 ? BOOT : after; }
            public List<TargetInventory.Package> installedPackages() { return reads++ == 0 ? first : second; }
        };
    }
    @Test void retainsExactComponentProcessNames() throws Exception {
        var snapshot = TargetInventory.collect(port(PACKAGES, PACKAGES, BOOT));
        assertEquals(PACKAGES, snapshot.packages());
        assertEquals(BOOT, snapshot.bootId());
    }
    @Test void captureRequiresRunSessionAndRetainedFrame(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        String run = "12345678-1234-1234-1234-123456789abc";
        var source = java.nio.file.Files.writeString(dir.resolve("inventory.txt"),
            "bootId=" + BOOT + "\npackage-inventory-v1\ncom.example.one\tcom.example.one\nend\n");
        var capture = TargetInventory.capture(run, "session-1", "emulator-5554", BOOT, source);
        assertEquals(run, capture.runId());
        assertEquals(source, capture.source());
        assertThrows(IOException.class, () -> TargetInventory.capture("other", "session-1", "emulator-5554", BOOT, source));
        assertThrows(IOException.class, () -> TargetInventory.capture(run, "", "emulator-5554", BOOT, source));
        assertThrows(IOException.class, () -> TargetInventory.capture(run, "session-1", "physical-device", BOOT, source));
        assertThrows(IOException.class, () -> TargetInventory.capture(run, "session-1", "emulator-5554", "bad", source));
    }
    @Test void crossCheckRejectsPartialDuplicateAndStaleSystemList(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var primary = java.nio.file.Files.writeString(dir.resolve("inventory"), "bootId=" + BOOT + "\npackage-inventory-v1\nandroid\tandroid\ncom.example.one\tcom.example.one\nend\n");
        var capture = TargetInventory.capture("12345678-1234-1234-1234-123456789abc", "session", "emulator-5554", BOOT, primary);
        String prefix = "bootId=" + BOOT + "\npackage-list-v1\n";
        var system = dir.resolve("system-list");
        java.nio.file.Files.writeString(system, prefix + "package:com.example.one\npackage:android\nend\n");
        var checked = TargetInventory.crossCheck(capture, system);
        assertEquals(system, checked.source());
        assertEquals(TargetInventory.Applicability.REQUIRED, TargetInventory.applicability(checked,
            new AcceptancePlan.Key("agent/font", "android:com.example.one")));
        assertEquals(TargetInventory.Applicability.UNKNOWN, TargetInventory.applicability(checked,
            new AcceptancePlan.Key("agent/font", "android:com.missing.app")));
        assertEquals(TargetInventory.Applicability.UNKNOWN, TargetInventory.applicability(checked,
            new AcceptancePlan.Key("agent/font", "qnx:gauge")));
        assertEquals(TargetInventory.Applicability.UNKNOWN, TargetInventory.applicability(checked,
            new AcceptancePlan.Key("agent/font", "android:system_server")));
        for (String rows : List.of("package:android\n", "package:android\npackage:android\n", "package:android\npackage:com.other\n")) {
            java.nio.file.Files.writeString(system, prefix + rows + "end\n");
            assertThrows(IOException.class, () -> TargetInventory.crossCheck(capture, system));
        }
        java.nio.file.Files.writeString(system, prefix.replace(BOOT, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") + "package:android\npackage:com.example.one\nend\n");
        assertThrows(IOException.class, () -> TargetInventory.crossCheck(capture, system));
    }
    @Test void frameParsingBindsBootAndRejectsTruncation() throws Exception {
        String frame = "bootId=" + BOOT + "\npackage-inventory-v1\ncom.example.one\tcom.example.one\nend\n";
        assertEquals("com.example.one", TargetInventory.parse(frame, BOOT).packages().get(0).name());
        assertThrows(IOException.class, () -> TargetInventory.parse(frame.replace("end\n", ""), BOOT));
        assertThrows(IOException.class, () -> TargetInventory.parse(frame, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThrows(IOException.class, () -> TargetInventory.parse(frame.replace("end\n", "com.example.one\tcom.example.one\nend\n"), BOOT));
    }
    @Test void incompleteChangedOrDuplicateObservationsFailClosed() {
        assertThrows(IOException.class, () -> TargetInventory.collect(port(PACKAGES, List.of(), BOOT)));
        assertThrows(IOException.class, () -> TargetInventory.collect(port(PACKAGES, PACKAGES, "other")));
        assertThrows(IOException.class, () -> TargetInventory.collect(port(List.of(PACKAGES.get(0), PACKAGES.get(0)), PACKAGES, BOOT)));
        assertThrows(IOException.class, () -> TargetInventory.collect(port(PACKAGES,
            List.of(new TargetInventory.Package("com.example.one", List.of("com.example.one"))), BOOT)));
    }
}
