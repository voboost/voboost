package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunFilesTest {
    @TempDir Path directory;
    @Test void concurrentLanesSharePrivateParentDirectory() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            for (int index = 0; index < 16; index++) {
                Path parent = directory.resolve("shared-" + index);
                var ready = new java.util.concurrent.CyclicBarrier(8);
                var calls = new java.util.ArrayList<java.util.concurrent.Callable<Path>>();
                for (int lane = 0; lane < 8; lane++) {
                    calls.add(() -> {
                        ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        return RunFiles.directory(parent);
                    });
                }
                for (var call : executor.invokeAll(calls)) assertEquals(parent, call.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void privateRecordsSurviveReplacementAndRejectAmbiguousInput() throws Exception {
        Path work = RunFiles.directory(directory.resolve("work")), record = work.resolve("record.json");
        RunFiles.write(record, RunFiles.parse("{\"state\":\"reserved\"}"));
        RunFiles.write(record, RunFiles.parse("{\"state\":\"dispatched\"}"));
        assertEquals("dispatched", RunFiles.read(record).get("state").getAsString());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(record));
        for (String input : new String[] {"{\"state\":1,\"state\":2}", "{\"x\":{\"a\":1,\"a\":2}}", "{}{}", "[]"})
            assertThrows(IOException.class, () -> RunFiles.parse(input));
        try (var entries = Files.list(work)) { assertEquals(1, entries.count()); }
    }
    @Test void remainingVehicleOutputTreeKeepsEveryRecordPrivate()throws Exception {
        Path root=RunFiles.directory(directory.resolve("vehicle"));
        String[][] tree={
            {"pw-rendered-1","qnx-fixture.json","proof.json","qnx-restored.json","authority.json"},
            {"fev-native-2","warm-off.json"},
            {"fev-native-2/cold-off","intent-before-dispatch.json","prepared.json","old-native-tail.json","restart.json","new-kernel-before-native-resume.json"},
            {"","final-absence.raw.json","final-absence.json"}
        };
        for(String[] branch:tree) {
            Path parent=RunFiles.directory(root.resolve(branch[0]));
            assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(parent));
            for(int i=1;i<branch.length;i++) {
                Path record=parent.resolve(branch[i]);RunFiles.write(record,RunFiles.parse("{\"receipt\":\"retained\"}"));
                assertEquals("retained",RunFiles.read(record).get("receipt").getAsString());
                assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(record));
            }
        }
        Path unsafe=Files.createDirectory(root.resolve("unsafe"));
        Files.setPosixFilePermissions(unsafe,PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThrows(IOException.class,()->RunFiles.write(unsafe.resolve("receipt.json"),RunFiles.parse("{}")));
    }
    @Test void recordsNeverReadThroughSymlinks() throws Exception {
        Path file = Files.writeString(directory.resolve("original"), "{}");
        Path link = Files.createSymbolicLink(directory.resolve("link"), file);
        assertThrows(IOException.class, () -> RunFiles.read(link));
    }
}
