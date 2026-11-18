package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MachineLeaseTest {
    @TempDir Path directory;
    @Test void aliasesShareTheLeaseAndItsInodeSurvivesRelease() throws Exception {
        Path machine = Files.createDirectory(directory.resolve("machine"));
        Path alias = Files.createSymbolicLink(directory.resolve("alias"), machine);
        Path root = directory.resolve("leases"), inode;
        Object key;
        try (MachineLease first = MachineLease.acquire(root, machine)) {
            inode = first.directory().resolve("machine.lock"); key = Files.getAttribute(inode, "unix:ino");
            assertThrows(IOException.class, () -> MachineLease.acquire(root, alias));
        }
        try (MachineLease next = MachineLease.acquire(root, alias)) {
            assertEquals(inode, next.directory().resolve("machine.lock"));
            assertEquals(key, Files.getAttribute(inode, "unix:ino"));
        }
    }
    @Test void symlinkAndAdditionalHardLinkAreNotAcceptedAsLeaseFiles() throws Exception {
        Path machine = Files.createDirectory(directory.resolve("machine")), root = directory.resolve("leases"), lock;
        try (MachineLease first = MachineLease.acquire(root, machine)) { lock = first.directory().resolve("machine.lock"); }
        Path foreign = Files.writeString(directory.resolve("foreign"), "untouched");
        Files.delete(lock); Files.createSymbolicLink(lock, foreign);
        assertThrows(IOException.class, () -> MachineLease.acquire(root, machine));
        Files.delete(lock); Files.createLink(lock, foreign);
        assertThrows(IOException.class, () -> MachineLease.acquire(root, machine));
        assertEquals("untouched", Files.readString(foreign));
    }
}
