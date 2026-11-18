package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliProcessTest {
    private static List<String> shell(String script) { return List.of("/bin/sh", "-c", script); }
    @Test void readsFragmentedLinesBeforeExitAndRepliesOnStdin(@TempDir Path work) throws Exception {
        List<String> lines = new ArrayList<>();
        var result = CliProcess.run(shell("printf 'arm'; printf 'ed\\n'; read answer; test \"$answer\" = ACK || exit 9; printf 'done\\n'; printf 'diagnostic\\n' >&2"),
            work.resolve("out"), work.resolve("err"), Duration.ofSeconds(5), (stream, line, control) -> {
                lines.add(stream + ":" + line);
                return stream == CliProcess.Stream.OUT && line.equals("armed") ? "ACK" : null;
            });
        assertEquals(0, result.exit());
        assertEquals(List.of("OUT:armed", "OUT:done"), lines.stream().filter(line -> line.startsWith("OUT:")).toList());
        assertEquals(List.of("ERR:diagnostic"), lines.stream().filter(line -> line.startsWith("ERR:")).toList());
        assertEquals("armed\ndone\n", Files.readString(result.out()));
        assertEquals("diagnostic\n", Files.readString(result.err()));
    }
    @Test void liveObserverReleasesOnEofWithoutSendingACommandAndReceivesCleanup(@TempDir Path work) throws Exception {
        List<String> rows = new ArrayList<>();
        var result = CliProcess.run(shell("printf 'armed\\n'; if read unexpected; then printf 'command-received\\n'; exit 9; fi; printf 'closed-cleanup\\n'"),
            work.resolve("out"), work.resolve("err"), Duration.ofSeconds(5), (stream, line, control) -> {
                rows.add(line); if (line.equals("armed")) control.endInput(); return null;
            });
        assertEquals(0, result.exit()); assertEquals(List.of("armed", "closed-cleanup"), rows);
    }
    @Test void replyAndRepeatedReleaseAfterEofAreRejected(@TempDir Path work) throws Exception {
        for (boolean reply : List.of(false, true)) {
            Path dir = Files.createTempDirectory(work, "eof-");
            assertThrows(IOException.class, () -> CliProcess.run(shell("printf 'armed\\n'; read ignored; printf 'closed\\n'"),
                dir.resolve("out"), dir.resolve("err"), Duration.ofSeconds(5), (stream, line, control) -> {
                    if (line.equals("armed")) { control.endInput(); if (reply) return "forbidden"; control.endInput(); }
                    return null;
                }));
        }
    }
    @Test void nativeControlRequiresCurrentArmedLineAndCannotReplayRelease(@TempDir Path work) throws Exception {
        var request = FaultWindowTest.request(); request.addProperty("kind", "native-state-read-unavailable");
        var window = new FaultWindow(request, "emulator-5588", "candidate");
        var arm = FaultWindowTest.envelope("armed"); arm.getAsJsonObject("body").addProperty("kind", "native-state-read-unavailable");
        arm.getAsJsonObject("body").addProperty("ownerWriterId", "e".repeat(32));
        var receipt = new com.google.gson.JsonObject();
        for (String key : List.of("kind", "token", "bootId", "generationId", "catalogSha256", "ownerWriterId")) receipt.add(key, arm.getAsJsonObject("body").get(key));
        receipt.addProperty("targetPid", 41); receipt.addProperty("targetStarttime", 514); receipt.addProperty("deadlineMonotonicMs", 91000);
        receipt.addProperty("ownerPid", 44); receipt.addProperty("ownerStarttime", 600); receipt.addProperty("observedMonotonicMs", 1001);
        for (String key : List.of("reads", "droppedReads", "writes", "failedWrites", "waitStateSteps", "recoveredValues")) receipt.addProperty(key, 0);
        receipt.add("recoveredValue", com.google.gson.JsonNull.INSTANCE); receipt.add("leaseRevision", com.google.gson.JsonNull.INSTANCE);
        receipt.addProperty("stopped", false); receipt.addProperty("released", false); receipt.addProperty("closed", false);
        var snapshot = new com.google.gson.JsonObject(); snapshot.addProperty("schemaVersion", 1); snapshot.addProperty("serial", "emulator-5588"); snapshot.addProperty("phase", "snapshot"); snapshot.add("body", receipt);
        var released = snapshot.deepCopy(); released.addProperty("phase", "released"); released.getAsJsonObject("body").addProperty("released", true);
        String script = "printf '%s\\n' '" + arm + "'; read first; test \"$first\" = S || exit 9; printf '%s\\n' '" + snapshot + "'; read second; test \"$second\" = R || exit 10; printf '%s\\n' '" + released + "'; if read ignored; then exit 11; fi; printf 'done\\n'";
        var result = CliProcess.run(shell(script), work.resolve("out"), work.resolve("err"), Duration.ofSeconds(5), (stream, line, control) -> {
            if (line.equals(arm.toString())) { control.bindNativeDiagnostic(window); control.sendNativeDiagnostic(window, FaultWindow.NativeControl.SNAPSHOT); }
            if (line.equals(snapshot.toString())) { control.captureNativeDiagnostic(window); control.sendNativeDiagnostic(window, FaultWindow.NativeControl.RELEASE); assertThrows(IOException.class, () -> control.sendNativeDiagnostic(window, FaultWindow.NativeControl.RELEASE)); }
            if (line.equals(released.toString())) { control.captureNativeDiagnostic(window); assertThrows(IOException.class, control::endInput); control.endNativeDiagnostic(window); assertThrows(IOException.class, () -> control.sendNativeDiagnostic(window, FaultWindow.NativeControl.SNAPSHOT)); }
            return null;
        });
        assertEquals(0, result.exit());
        assertTrue(Files.readString(result.out()).endsWith("done\n"));
    }
    @Test void genericArmCannotAuthorizeNativeControl(@TempDir Path work) throws Exception {
        var window = new FaultWindow(FaultWindowTest.request(), "emulator-5588", "candidate");
        var arm = FaultWindowTest.envelope("armed");
        String script = "printf '%s\\n' '" + arm + "'; read ignored";
        assertThrows(IOException.class, () -> CliProcess.run(shell(script), work.resolve("out"), work.resolve("err"), Duration.ofSeconds(5), (stream,line,control) -> {
            control.bindNativeDiagnostic(window); return null;
        }));
    }

    @Test void callbackFailureTerminatesTheChildAndRetainsEvidence(@TempDir Path work) throws Exception {
        IOException failure = assertThrows(IOException.class, () -> CliProcess.run(shell("printf '%s\\narmed\\n' \"$$\"; exec sleep 20"),
            work.resolve("out"), work.resolve("err"), Duration.ofSeconds(5), (stream, line, control) -> { if (line.equals("armed")) throw new IOException("verification failed"); return null; }));
        assertEquals("verification failed", failure.getMessage());
        String[] output = Files.readString(work.resolve("out")).split("\\n");
        assertEquals("armed", output[1]);
        assertFalse(ProcessHandle.of(Long.parseLong(output[0])).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void timesOutWithoutWaitingForTheOriginalSleep(@TempDir Path work) throws Exception {
        long start = System.nanoTime();
        assertThrows(IOException.class, () -> CliProcess.run(shell("exec sleep 20"), work.resolve("out"), work.resolve("err"),
            Duration.ofMillis(100), (stream, line, control) -> null));
        assertTrue(System.nanoTime() - start < Duration.ofSeconds(5).toNanos());
    }
    @Test void rejectsMalformedOrTruncatedProtocolLines(@TempDir Path work) throws Exception {
        for (String script : List.of("printf 'unfinished'", "printf '\\377\\n'")) {
            Path dir = Files.createTempDirectory(work, "case-");
            assertThrows(IOException.class, () -> CliProcess.run(shell(script), dir.resolve("out"), dir.resolve("err"),
                Duration.ofSeconds(5), (stream, line, control) -> null));
        }
    }
    @Test void appliesSeparateLineAndOutputBudgets(@TempDir Path work) throws Exception {
        for (String script : List.of("printf '12345\\n'", "printf '1234\\n1234\\n1234\\n'", "printf '12345\\n' >&2")) {
            Path dir = Files.createTempDirectory(work, "case-");
            assertThrows(IOException.class, () -> CliProcess.run(shell(script), dir.resolve("out"), dir.resolve("err"),
                Duration.ofSeconds(5), (stream, line, control) -> null, new CliProcess.Limits(12, 4)));
        }
    }
    @Test void rejectsUnsafeReplyAndReusedEvidenceFilesBeforeDispatch(@TempDir Path work) throws Exception {
        assertThrows(IOException.class, () -> CliProcess.run(shell("printf 'ready\\n'; exec sleep 20"), work.resolve("out"), work.resolve("err"),
            Duration.ofSeconds(5), (stream, line, control) -> "ACK\nEXTRA"));
        assertThrows(IOException.class, () -> CliProcess.run(shell("exit 0"), work.resolve("out"), work.resolve("newerr"),
            Duration.ofSeconds(5), (stream, line, control) -> null));
    }

    @Test void deadlineBoundsBlockedStdinEvenWhenTheChildDoesNotRead(@TempDir Path work) throws Exception {
        Path pid = work.resolve("pid");
        String script = "echo $$ > '" + pid + "'; i=0; while test $i -lt 100; do printf 'line\\n'; i=$((i+1)); done; exec sleep 20";
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertThrows(IOException.class, () -> CliProcess.run(shell(script), work.resolve("out"), work.resolve("err"),
                    Duration.ofMillis(100), (stream, line, control) -> "x".repeat(4096))));
        } finally {
            if (Files.exists(pid)) ProcessHandle.of(Long.parseLong(Files.readString(pid).trim())).ifPresent(ProcessHandle::destroyForcibly);
        }
    }
    @Test void deadlineBoundsAListenerThatBlocks(@TempDir Path work) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
            assertThrows(IOException.class, () -> CliProcess.run(shell("printf 'line\\n'; exec sleep 20"),
                work.resolve("out"), work.resolve("err"), Duration.ofMillis(100), (stream, line, control) -> {
                    Thread.sleep(20000); return null;
                })));
    }

    @Test void terminatesCapturedChildHelpersOnTimeout(@TempDir Path work) throws Exception {
        try {
            assertThrows(IOException.class, () -> CliProcess.run(shell("sleep 20 & child=$!; printf '%s\\n' \"$child\"; wait"),
                work.resolve("out"), work.resolve("err"), Duration.ofMillis(200), (stream, line, control) -> null));
            long child = Long.parseLong(Files.readString(work.resolve("out")).trim());
            assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false), "owned helper survived CLI timeout");
        } finally {
            if (Files.exists(work.resolve("out"))) {
                String pid = Files.readString(work.resolve("out")).trim();
                if (pid.matches("[0-9]+")) ProcessHandle.of(Long.parseLong(pid)).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    @Test void confirmsCooperativeCallbackFinallyBeforeReturning(@TempDir Path work) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(IOException.class, () -> CliProcess.run(shell("printf 'line\\n'; exec sleep 20"),
            work.resolve("out"), work.resolve("err"), Duration.ofMillis(200), (stream, line, control) -> {
                try { Thread.sleep(20000); return null; } finally { finished.set(true); }
            }));
        assertTrue(finished.get(), "callback finally must finish before caller cleanup");
    }
    @Test void reportsUnconfirmedCallbackInsteadOfClaimingItStopped(@TempDir Path work) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean release = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        try {
            IOException error = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(IOException.class, () ->
                CliProcess.run(shell("printf 'line\\n'; exec sleep 20"), work.resolve("out"), work.resolve("err"),
                    Duration.ofMillis(200), (stream, line, control) -> {
                        try {
                            while (!release.get()) { try { Thread.sleep(10); } catch (InterruptedException ignored) { } }
                            return null;
                        } finally { finished.countDown(); }
                    })));
            assertTrue(error.getMessage().contains("callback termination unconfirmed"));
            assertEquals(1, finished.getCount());
        } finally {
            release.set(true);
            assertTrue(finished.await(2, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void cancelledParentScopeCannotDispatchAnotherChild(@TempDir Path work) throws Exception {
        CliProcess.Control control = new CliProcess.Control(System.nanoTime() + Duration.ofSeconds(5).toNanos(), null);
        control.close();
        Path marker = work.resolve("unexpected");
        assertThrows(IOException.class, () -> CliProcess.run(shell("touch '" + marker + "'"), work.resolve("out"), work.resolve("err"),
            Duration.ofSeconds(5), (stream, line, child) -> null, control));
        assertFalse(Files.exists(marker));
    }
}
