package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallerDriverTest {
    private static InstallerDriver.BootData captureBoot(String id, boolean ready, boolean enforcing) {
        return new InstallerDriver.BootData(id, ready, enforcing, new JsonObject(), null);
    }

    @Test void compositeReadUsesOneBeforeAndOneAfterBootBoundary() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var initial = new InstallerDriver.BootData("boot", true, true, new com.google.gson.JsonObject(), java.nio.file.Path.of("before"));
        String result = InstallerDriver.inReadyBoot(() -> { calls.incrementAndGet(); return initial; }, before -> {
            assertSame(initial, before);
            return before.id();
        });
        assertEquals("boot", result);
        assertEquals(2, calls.get());
    }
    @Test void captureReadsAllEvidenceWithinOneReadyBootBoundary() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        String result = InstallerDriver.inReadyBoot("boot", () -> {
            reads.incrementAndGet();
            return captureBoot("boot", true, true);
        }, () -> {
            assertEquals(1, reads.get());
            return "verified evidence";
        });
        assertEquals("verified evidence", result);
        assertEquals(2, reads.get());
    }

    @Test void captureRejectsForeignOrUnreadyInitialBootBeforeReadingEvidence() {
        for (var boundary : List.of(captureBoot("foreign", true, true),
                captureBoot("boot", false, true), captureBoot("boot", true, false))) {
            AtomicInteger body = new AtomicInteger();
            assertThrows(IOException.class, () -> InstallerDriver.inReadyBoot("boot", () -> boundary, () -> body.incrementAndGet()));
            assertEquals(0, body.get());
        }
    }

    @Test void captureDiscardsEvidenceWhenTheFinalBootOrPolicyChanges() {
        for (var boundary : List.of(captureBoot("foreign", true, true),
                captureBoot("boot", false, true), captureBoot("boot", true, false))) {
            AtomicInteger reads = new AtomicInteger();
            assertThrows(IOException.class, () -> InstallerDriver.inReadyBoot("boot", () ->
                    reads.incrementAndGet() == 1 ? captureBoot("boot", true, true) : boundary,
                    () -> "must not be accepted"));
            assertEquals(2, reads.get());
        }
    }

    @Test void observationRetriesTransientReadsUnderOneDeadline() throws Exception {
        var reads = new AtomicInteger();
        var deadline = new java.util.concurrent.atomic.AtomicLong();
        var owner = new java.util.concurrent.atomic.AtomicReference<CliProcess.Control>();
        String value = InstallerDriver.awaitObservation("PW observation", Duration.ofSeconds(5), (scope, end) -> {
            int read = reads.getAndIncrement();
            if (read == 0) {
                owner.set(scope);
                deadline.set(end);
                throw new ru.voboost.voyemu.UiEvidence.Unavailable("durable state unavailable");
            }
            assertSame(owner.get(), scope);
            assertEquals(deadline.get(), end);
            if (read == 1) throw new ru.voboost.tests.agents.ClusterObservation.Pending("delivery pending");
            if (read == 2) return null;
            return "applied";
        });
        assertEquals("applied", value);
        assertEquals(4, reads.get());
        assertThrows(IOException.class, owner.get()::check);
    }

    @Test void observationStopsOnOwnerChange() {
        var reads = new AtomicInteger();
        var foreignOwner = new IOException("owner changed");
        var failure = assertThrows(IOException.class, () -> InstallerDriver.awaitObservation(
                "PW observation", Duration.ofSeconds(5), (scope, deadline) -> {
                    if (reads.incrementAndGet() == 1)
                        throw new ru.voboost.tests.agents.ClusterObservation.Pending("delivery pending");
                    throw foreignOwner;
                }));
        assertSame(foreignOwner, failure);
        assertEquals(2, reads.get());
    }

    @Test void sdkPowerToggleRequiresCurrentKnownOppositeDisplayAndAccBeforeInput()throws Exception {
        String boot="12345678-1234-1234-1234-123456789abc",session="a".repeat(32);
        for(boolean awake:new boolean[]{true,false}) {
            JsonObject system=new JsonObject(),value=new JsonObject(),power=new JsonObject(),receipt=new JsonObject(),acc=new JsonObject();
            system.addProperty("boot_id",boot);value.addProperty("boot_id",boot);system.add("value",value);value.add("power",power);
            power.addProperty("status","ready");power.addProperty("complete",true);power.addProperty("display_ready",true);
            power.addProperty("wakefulness",awake?"Awake":"Asleep");power.addProperty("display_state",awake?"ON":"OFF");
            acc.addProperty("raw",awake?2:0);receipt.add("acc",acc);
            var can=new ru.voboost.voyemu.VehicleCan.PowerSnapshot(boot,session,"source",42,"1","2","1",awake?2:0,receipt);
            InstallerDriver.requirePowerKeyPrior(system,can,session,boot,awake);
            assertThrows(IOException.class,()->InstallerDriver.requirePowerKeyPrior(system,can,session,boot,!awake));
            assertThrows(IOException.class,()->InstallerDriver.requirePowerKeyPrior(system,can,"b".repeat(32),boot,awake));
            JsonObject foreign=system.deepCopy();foreign.getAsJsonObject("value").addProperty("boot_id","foreign");
            assertThrows(IOException.class,()->InstallerDriver.requirePowerKeyPrior(foreign,can,session,boot,awake));
            JsonObject partial=system.deepCopy();partial.getAsJsonObject("value").getAsJsonObject("power").addProperty("complete",false);
            assertThrows(IOException.class,()->InstallerDriver.requirePowerKeyPrior(partial,can,session,boot,awake));
            acc.addProperty("raw",awake?0:2);
            var wrongAcc=new ru.voboost.voyemu.VehicleCan.PowerSnapshot(boot,session,"source",42,"1","2","1",awake?0:2,receipt);
            assertThrows(IOException.class,()->InstallerDriver.requirePowerKeyPrior(system,wrongAcc,session,boot,awake));
        }
    }

    @Test void iconOnlyRouteKeepsNormalPreparationAndVehicleOwnership()throws Exception {
        InstallerDriver.requireVehicleStartAt("full",false,"require-absent");
        InstallerDriver.requireVehicleStartAt("pw-icon",true,"verify-installed");
        InstallerDriver.requireVehicleStartAt("pw-readiness",true,"verify-installed");
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("pw-readiness",false,"verify-installed"));
        InstallerDriver.requireVehicleStartAt("fev",true,"verify-installed");
        InstallerDriver.requireVehicleStartAt("fev-oem",true,"verify-installed");
        InstallerDriver.requireVehicleStartAt("fev-finish",true,"verify-installed");
        InstallerDriver.requireVehicleStartAt("remove",true,"verify-installed");
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("remove",false,"verify-installed"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("remove",true,"require-absent"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev-finish",false,"verify-installed"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev-finish",true,"require-absent"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev-oem",false,"verify-installed"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev-oem",true,"require-absent"));
        for(String preparation:List.of("require-absent","uninstall","reapply"))
            assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev",true,preparation));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("fev",false,"verify-installed"));
        for(String preparation:List.of("require-absent","uninstall","reapply"))
            InstallerDriver.requireVehicleStartAt("pw-icon",true,preparation);
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("pw-icon",false,"verify-installed"));
        assertThrows(IOException.class,()->InstallerDriver.requireVehicleStartAt("foreign",true,"verify-installed"));
    }

    @Test void firstInstallBootCanBeObservedBeforeAndroidIsReadyOrAfterSchedulingRebootTwo() throws Exception {
        String boot = "12345678-1234-1234-1234-123456789abc";
        String manifest = "a".repeat(64);
        JsonObject report = new JsonObject();
        report.addProperty("operation", "install");
        report.addProperty("transactionId", manifest);
        report.addProperty("bootId", boot);
        report.addProperty("phase", "install-agents");
        report.addProperty("outcome", "running");
        report.addProperty("rebootCount", 1);
        InstallerDriver.requireFirstInstallBoot(report, manifest, boot);

        report.addProperty("rebootCount", 2);
        assertThrows(IOException.class, () -> InstallerDriver.requireFirstInstallBoot(report, manifest, boot));
        report.addProperty("phase", "install-reboot-second");
        report.addProperty("outcome", "reboot-required");
        InstallerDriver.requireFirstInstallBoot(report, manifest, boot);
        assertThrows(IOException.class, () -> InstallerDriver.requireFirstInstallBoot(report, manifest, "foreign-boot"));
        assertThrows(IOException.class, () -> InstallerDriver.requireFirstInstallBoot(report, "other-candidate", boot));
        report.addProperty("phase", "install-complete");
        report.addProperty("outcome", "complete");
        assertThrows(IOException.class, () -> InstallerDriver.requireFirstInstallBoot(report, manifest, boot));
    }

    @Test void installedContinuationRequiresTheActualNativeOperationToBeComplete()throws Exception {
        JsonObject operation=new JsonObject();operation.addProperty("schemaVersion",1);operation.addProperty("operation","update");
        operation.addProperty("phase","update-product-complete");operation.addProperty("outcome","complete");
        operation.addProperty("transactionId","a".repeat(64));operation.addProperty("bootId","12345678-1234-1234-1234-123456789abc");
        operation.add("error",com.google.gson.JsonNull.INSTANCE);InstallerDriver.requireInstalledTerminal(operation);
        JsonObject install=operation.deepCopy();install.addProperty("operation","install");install.addProperty("phase","install-complete");InstallerDriver.requireInstalledTerminal(install);
        for(String field:List.of("schemaVersion","operation","phase","outcome","transactionId","bootId","error")) {
            JsonObject invalid=operation.deepCopy();invalid.addProperty(field,"not-complete");
            assertThrows(IOException.class,()->InstallerDriver.requireInstalledTerminal(invalid),field);
        }
        JsonObject pending=operation.deepCopy();pending.addProperty("phase","update-product-system-helper-requested");pending.addProperty("outcome","running");
        assertThrows(IOException.class,()->InstallerDriver.requireInstalledTerminal(pending));
        assertThrows(IOException.class,()->InstallerDriver.requireInstalledTerminal(new JsonObject()));
    }

    @Test void normalUpdateBindsItsReceiptToTheActualRebootCount() throws Exception {
        String before = "12345678-1234-1234-1234-123456789abc";
        String after = "22345678-1234-1234-1234-123456789abc";
        String tx = "a".repeat(64);
        String stdout = "Update completed: transaction " + tx + "\n";
        JsonObject report = json("{\"schemaVersion\":1,\"operation\":\"update\","
                + "\"phase\":\"update-product-complete\",\"outcome\":\"complete\","
                + "\"transactionId\":\"" + tx + "\",\"bootId\":\"" + before
                + "\",\"rebootCount\":0,\"error\":null}");
        assertEquals(tx, InstallerDriver.updateTerminal(stdout, before, before, report));
        assertThrows(IOException.class, () -> InstallerDriver.updateTerminal(stdout, after, before, report));
        report.addProperty("rebootCount", 1);
        report.addProperty("bootId", after);
        assertEquals(tx, InstallerDriver.updateTerminal(stdout, before, after, report));
        assertThrows(IOException.class, () -> InstallerDriver.updateTerminal(
                stdout.replace("Update", "Install"), before, after, report));
    }

    @Test void normalReapplyIsVehicleScopedAndBindsTheRealUpdateTransactionToItsChangedBoot()throws Exception {
        InstallerDriver.requirePreparationMode("reapply",true);
        assertThrows(IOException.class,()->InstallerDriver.requirePreparationMode("reapply",false));
        assertThrows(IOException.class,()->InstallerDriver.requirePreparationMode("unrecognized",true));
        String before="12345678-1234-1234-1234-123456789abc",after="22345678-1234-1234-1234-123456789abc",tx="a".repeat(64);
        String stdout="Install completed: transaction "+tx+"\n";
        JsonObject report=json("{\"schemaVersion\":1,\"operation\":\"update\",\"outcome\":\"complete\",\"transactionId\":\""+tx+"\",\"bootId\":\""+after+"\",\"rebootCount\":2,\"error\":null}");
        assertEquals(tx,InstallerDriver.reapplyTerminal(stdout,before,after,report));
        for(String invalid:List.of(stdout+stdout,stdout.replace(tx,"b".repeat(64)),stdout.replace("\n","")))
            assertThrows(IOException.class,()->InstallerDriver.reapplyTerminal(invalid,before,after,report));
        assertThrows(IOException.class,()->InstallerDriver.reapplyTerminal(stdout,after,after,report));
        for(String field:List.of("operation","outcome","transactionId","bootId","error")) {
            JsonObject invalid=report.deepCopy();invalid.addProperty(field,"foreign");
            assertThrows(IOException.class,()->InstallerDriver.reapplyTerminal(stdout,before,after,invalid));
        }
    }

    @Test void retainedReapplyRequiresItsKnownSuccessfulProcessWithoutLaunchingTheDetachedBinary(@TempDir Path directory)throws Exception {
        String before="12345678-1234-1234-1234-123456789abc",after="22345678-1234-1234-1234-123456789abc",tx="a".repeat(64);
        Path detached=directory.resolve("detached.app/Contents/MacOS/voboost-installer");
        JsonObject input=new JsonObject();input.addProperty("phase","vehicle-reapply");input.addProperty("binary",detached.toString());input.addProperty("device","emulator-5588");input.addProperty("priorBoot",before);
        JsonArray command=new JsonArray();for(String arg:List.of(detached.toString(),"--install","--force","--device","emulator-5588"))command.add(arg);
        JsonObject process=new JsonObject();process.add("command",command);input.add("process",process);
        JsonObject completed=new JsonObject();completed.addProperty("schemaVersion",1);completed.add("command",command.deepCopy());completed.addProperty("exit",0);
        String stdout="Install completed: transaction "+tx+"\n";
        JsonObject operation=json("{\"schemaVersion\":1,\"operation\":\"update\",\"outcome\":\"complete\",\"transactionId\":\""+tx+"\",\"bootId\":\""+after+"\",\"rebootCount\":2,\"error\":null}");
        assertFalse(Files.exists(detached));
        assertEquals(tx,InstallerDriver.reapplyReceipt(input,completed,stdout,after,operation));
        assertFalse(Files.exists(detached));
        for(int index:java.util.stream.IntStream.range(0,command.size()).toArray()) {
            JsonObject foreign=input.deepCopy();foreign.getAsJsonObject("process").getAsJsonArray("command").set(index,new com.google.gson.JsonPrimitive("foreign"));
            assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(foreign,completed,stdout,after,operation));
        }
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,new JsonObject(),stdout,after,operation));
        for(int exit:List.of(-1,1)) {
            JsonObject invalid=completed.deepCopy();invalid.addProperty("exit",exit);
            assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,invalid,stdout,after,operation));
        }
        JsonObject unknown=completed.deepCopy();unknown.remove("exit");
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,unknown,stdout,after,operation));
        JsonObject textualExit=completed.deepCopy();textualExit.addProperty("exit","0");
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,textualExit,stdout,after,operation));
        JsonObject extra=completed.deepCopy();extra.addProperty("unknown",true);
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,extra,stdout,after,operation));
        JsonObject foreignExit=completed.deepCopy();foreignExit.getAsJsonArray("command").set(0,new com.google.gson.JsonPrimitive("/another/installer"));
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,foreignExit,stdout,after,operation));
        JsonObject foreignTx=operation.deepCopy();foreignTx.addProperty("transactionId","b".repeat(64));
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,completed,stdout,after,foreignTx));
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,completed,stdout,before,operation));
        assertThrows(IOException.class,()->InstallerDriver.reapplyReceipt(input,completed,stdout,"32345678-1234-1234-1234-123456789abc",operation));
    }

    @Test void scopedVehicleInstallUsesAnAdmittedLifecyclePhase() throws Exception {
        InstallerDriver.requireInstallPhase(InstallerDriver.VEHICLE_INSTALL_PHASE);
        for (String phase : List.of("first", "second", "previous", "reinstall", "after-settings")) {
            InstallerDriver.requireInstallPhase(phase);
        }
        assertFalse(InstallerDriver.uninstallPreparation("updated"));
        assertThrows(IOException.class, () -> InstallerDriver.requireInstallPhase("vehicle-only"));
    }

    @Test void retainedInstallRequiresItsSuccessfulCliAndVerifiedBootMonitor(@TempDir Path directory) throws Exception {
        String prior = "12345678-1234-1234-1234-123456789abc";
        String boot = "22345678-1234-1234-1234-123456789abc";
        String manifest = "a".repeat(64);
        JsonObject input = new JsonObject();
        input.addProperty("manifest", manifest);
        input.addProperty("device", "emulator-5588");
        input.addProperty("priorBoot", prior);
        JsonArray command = new JsonArray();
        for (String argument : List.of(directory.resolve("detached-installer").toString(),
                "--install", "--device", "emulator-5588")) command.add(argument);
        JsonObject process = new JsonObject();
        process.add("command", command);
        input.add("process", process);
        JsonObject completed = new JsonObject();
        completed.addProperty("schemaVersion", 1);
        completed.add("command", command.deepCopy());
        completed.addProperty("exit", 0);
        completed.addProperty("acceptanceVerified", true);
        JsonObject nativeOperation = json("{\"schemaVersion\":1,\"operation\":\"install\","
                + "\"phase\":\"install-complete\",\"outcome\":\"complete\","
                + "\"transactionId\":\"" + manifest + "\",\"bootId\":\"" + boot
                + "\",\"rebootCount\":2,\"error\":null}");
        String stdout = "Install completed: transaction " + manifest + "\n";
        assertEquals(nativeOperation, InstallerDriver.installReceipt(input, completed, stdout, boot, nativeOperation));
        assertFalse(Files.exists(Path.of(command.get(0).getAsString())));
        for (String field : List.of("exit", "acceptanceVerified", "command", "schemaVersion")) {
            JsonObject missing = completed.deepCopy();
            missing.remove(field);
            assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, missing, stdout, boot, nativeOperation));
        }
        for (int exit : List.of(-1, 1, 124)) {
            JsonObject failed = completed.deepCopy();
            failed.addProperty("exit", exit);
            assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, failed, stdout, boot, nativeOperation));
        }
        JsonObject unaccepted = completed.deepCopy();
        unaccepted.addProperty("acceptanceVerified", false);
        assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, unaccepted, stdout, boot, nativeOperation));
        JsonObject textualExit = completed.deepCopy();
        textualExit.addProperty("exit", "0");
        assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, textualExit, stdout, boot, nativeOperation));
        JsonObject legacy = input.deepCopy();
        legacy.getAsJsonObject("process").getAsJsonArray("command").add("--emulator-install-acceptance");
        assertThrows(IOException.class, () -> InstallerDriver.installReceipt(legacy, completed, stdout, boot, nativeOperation));
        assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, completed, "", boot, nativeOperation));
        assertThrows(IOException.class, () -> InstallerDriver.installReceipt(input, completed, stdout, prior, nativeOperation));
    }

    @Test void exactEmulatorEndpointNeverAcceptsPhysicalOrMalformedDevice() {
        for (String device : List.of("emulator-5588", "emulator-1024", "emulator-65534")) assertTrue(InstallerDriver.emulator(device));
        for (String device : List.of("1234567", "127.0.0.1:5589", "emulator-5589", "emulator-05588", "emulator-0", "emulator-65535", "emulator-65536", "emulator-+5588"))
            assertFalse(InstallerDriver.emulator(device), device);
    }
    @Test void normalAbsenceRequiresEveryExactCheckAndRejectsEmergencyReceipt() throws Exception {
        String receipt = "{\"transactionId\":\"" + "a".repeat(64) + "\",\"bootId\":\"12345678-1234-1234-1234-123456789abc\",\"checks\":[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}";
        InstallerDriver.requireAbsence(json(receipt));
        for (String invalid : List.of(receipt.replace(",\"policy\"", ""), receipt.replace(",\"localization-overlays\"", ""), receipt.replace("\"package\"", "\"emergency\""),
                receipt.replace("transactionId", "state").replace("a".repeat(64), "emergency-removed")))
            assertThrows(IOException.class, () -> InstallerDriver.requireAbsence(json(invalid)));
    }
    @Test void emergencyAbsenceIsAllowedOnlyForExplicitPreparation() throws Exception {
        String receipt = "{\"state\":\"emergency-removed\",\"bootId\":\"12345678-1234-1234-1234-123456789abc\",\"checks\":[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}";
        InstallerDriver.requirePreparationAbsence(json(receipt));
        assertThrows(IOException.class, () -> InstallerDriver.requireAbsence(json(receipt)));
        for (String invalid : List.of(receipt.replace(",\"policy\"", ""), receipt.replace("12345678-1234-1234-1234-123456789abc", "garbage"), receipt.replace("\"state\":", "\"unknown\":1,\"state\":")))
            assertThrows(IOException.class, () -> InstallerDriver.requirePreparationAbsence(json(invalid)));
    }
    @Test void rawAbsenceRejectsDuplicateFieldsAndFinalAlreadyAbsent() throws Exception {
        String before = "12345678-1234-1234-1234-123456789abc", after = "22345678-1234-1234-1234-123456789abc";
        String checks = "[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]";
        String normal = "{\"transactionId\":\"" + "a".repeat(64) + "\",\"bootId\":\"" + after + "\",\"checks\":" + checks + "}";
        InstallerDriver.absenceReceipt(normal, false, before);
        String absent = "{\"state\":\"already-absent\",\"bootId\":\"" + before + "\",\"checks\":" + checks + "}";
        InstallerDriver.absenceReceipt(absent, true, before);
        assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(absent, false, before));
        assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(normal.replace(after, before), false, before));
        for (boolean preparation : List.of(true, false)) {
            assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(normal.replace("\"bootId\":", "\"bootId\":\"garbage\",\"bootId\":"), preparation, before));
            assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(normal.replace("\"checks\":", "\"checks\":[],\"checks\":"), preparation, before));
        }
    }
    @Test void duplicateReceiptKeysAreRejectedBeforeGsonCollapsesThem() throws Exception {
        String raw = "{\"transactionId\":\"" + "a".repeat(64) + "\",\"bootId\":\"bad\",\"bootId\":\"22345678-1234-1234-1234-123456789abc\",\"checks\":[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}";
        assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(raw, true, "12345678-1234-1234-1234-123456789abc"));
    }
    @Test void recoveryEventIsClosedCanonicalAndBindsAChangedBoot() throws Exception {
        String event = "VBI_CACHE_RECOVERY_BOOT|{\"schemaVersion\":1,\"operation\":\"cache-recovery-boot\",\"attemptToken\":\"" + "a".repeat(64)
            + "\",\"priorBootId\":\"12345678-1234-1234-1234-123456789abc\",\"bootId\":\"22345678-1234-1234-1234-123456789abc\",\"state\":\"waiting\"}";
        InstallerDriver.recoveryEvent(event);
        for (String invalid : List.of(event.replace("22345678", "12345678"), event.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                event.replace("\"state\":\"waiting\"", "\"state\":\"waiting\",\"unknown\":1"), event.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")))
            assertThrows(IOException.class, () -> InstallerDriver.recoveryEvent(invalid));
    }
    @Test void cyclicAndFinalRemovalRequireActualNormalRemovalInsteadOfEmergencyAbsence() throws Exception {
        String before = "12345678-1234-1234-1234-123456789abc", after = "22345678-1234-1234-1234-123456789abc";
        String checks = "[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]";
        String normal = "{\"transactionId\":\"" + "a".repeat(64) + "\",\"bootId\":\"" + after + "\",\"checks\":" + checks + "}";
        String emergency = "{\"state\":\"emergency-removed\",\"bootId\":\"" + after + "\",\"checks\":" + checks + "}";
        for (String phase : List.of("cycle", "final")) {
            InstallerDriver.absenceReceipt(normal, InstallerDriver.uninstallPreparation(phase), before);
            assertThrows(IOException.class, () -> InstallerDriver.absenceReceipt(emergency, InstallerDriver.uninstallPreparation(phase), before));
        }
        InstallerDriver.absenceReceipt(emergency, InstallerDriver.uninstallPreparation("preparation"), before);
    }
    @Test void settingsObserverRequiresExactEndpointPriorBootAndNormalNewBootAbsence() throws Exception {
        String before = "12345678-1234-1234-1234-123456789abc", after = "22345678-1234-1234-1234-123456789abc";
        String absence = "{\"transactionId\":\"" + "a".repeat(64) + "\",\"bootId\":\"" + after + "\",\"checks\":[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}";
        String receipt = "{\"schemaVersion\":1,\"serial\":\"emulator-5588\",\"bootIdBefore\":\"" + before + "\",\"absence\":" + absence + ",\"enforcing\":true}";
        assertEquals(after, InstallerDriver.settingsAbsence(receipt, "emulator-5588", before).get("bootId").getAsString());
        for (String invalid : List.of(receipt.replace("emulator-5588", "emulator-5590"), receipt.replace(after, before),
                receipt.replace("true", "false"), receipt.replace(",\"policy\"", ""), receipt.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                receipt.replace("\"checks\":", "\"checks\":[],\"checks\":"), receipt.replace("\"enforcing\":true", "\"enforcing\":true,\"extra\":1")))
            assertThrows(IOException.class, () -> InstallerDriver.settingsAbsence(invalid, "emulator-5588", before));
        assertThrows(IOException.class, () -> InstallerDriver.settingsAbsence(receipt, "emulator-5588", after));
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }    @Test void fixedFixtureReceiptsRejectMixedFailureEmptyAndUnboundedSuccessNoise() throws Exception {
        InstallerDriver.fixtureSuccess("Success\n",true);InstallerDriver.fixtureSuccess("Performing Streamed Install\nSuccess\n",true);InstallerDriver.fixtureSuccess("Success\n",false);
        for(String body:List.of("","Success","Success\nFailure [denied]\n","permission denied\n","Success\r\n","prefix\nSuccess\n"))
            for(boolean install:List.of(false,true)) assertThrows(IOException.class,()->InstallerDriver.fixtureSuccess(body,install));
        assertThrows(IOException.class,()->InstallerDriver.fixtureSuccess("Performing Streamed Install\nSuccess\n",false));
    }
    @Test void fixtureCleanupRequiresOriginalAbsenceAndSameExactOwnerWithoutReplay() throws Exception {
        JsonObject owner=json("{\"schemaVersion\":1,\"id\":\"12345678-1234-1234-1234-123456789abc\",\"sessionId\":\"session\",\"serial\":\"emulator-5554\",\"manifest\":\"manifest\",\"package\":\"ru.voboost.tests.launcherfixture\",\"initialBoot\":\"12345678-1234-1234-1234-123456789abc\",\"initialPresent\":false,\"state\":\"installed\",\"installReceipt\":\"Success\\n\"}");
        InstallerDriver.requireFixtureOwner(owner,"session","emulator-5554","manifest");
        for(String field:List.of("sessionId","serial","manifest","package","id","initialBoot","installReceipt")) {
            JsonObject wrong=owner.deepCopy();wrong.addProperty(field,"foreign");assertThrows(IOException.class,()->InstallerDriver.requireFixtureOwner(wrong,"session","emulator-5554","manifest"),field);
        }
        for(String state:List.of("installing","removing","removed","unknown")) {JsonObject wrong=owner.deepCopy();wrong.addProperty("state",state);assertThrows(IOException.class,()->InstallerDriver.requireFixtureOwner(wrong,"session","emulator-5554","manifest"));}
        JsonObject preexisting=owner.deepCopy();preexisting.addProperty("initialPresent",true);assertThrows(IOException.class,()->InstallerDriver.requireFixtureOwner(preexisting,"session","emulator-5554","manifest"));
        JsonObject string=owner.deepCopy();string.addProperty("initialPresent","false");assertThrows(IOException.class,()->InstallerDriver.requireFixtureOwner(string,"session","emulator-5554","manifest"));
    }
    @Test void fixtureMustAppendThenMoveAndRetainTheWholeObservedOrder() throws Exception {
        List<String> stock=List.of("OEM A","OEM B","OEM C"),installed=List.of("OEM A","OEM B","OEM C","Voboost Fixture");
        List<String> expected=InstallerDriver.movedFixtureOrder(stock,installed);assertEquals(List.of("Voboost Fixture","OEM A","OEM B","OEM C"),expected);
        InstallerDriver.requireFixtureFirst(expected,List.copyOf(expected));
        assertThrows(IOException.class,()->InstallerDriver.requireFixtureFirst(expected,installed));
        assertThrows(IOException.class,()->InstallerDriver.requireFixtureFirst(expected,List.of("Voboost Fixture","OEM B","OEM A","OEM C")));
        assertThrows(IOException.class,()->InstallerDriver.movedFixtureOrder(stock,List.of("Voboost Fixture","OEM A","OEM B","OEM C")));
        assertThrows(IOException.class,()->InstallerDriver.movedFixtureOrder(List.of("Voboost Fixture"),List.of("Voboost Fixture","Voboost Fixture")));
        assertThrows(IOException.class,()->InstallerDriver.movedFixtureOrder(stock,List.of("OEM A","OEM B","Voboost Fixture")));
    }
    @Test void fixtureMetadataPreflightRejectsMissingForeignOrRelativeInputBeforeExperiment(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        JsonObject fixture=json("{\"package\":\"ru.voboost.tests.launcherfixture\",\"label\":\"Voboost Fixture\",\"source\":\"tests/emulator/agents/agent-launcher/app\",\"apk\":\"build/test-fixtures/launcher/voboost-launcher-fixture.apk\"}");
        java.nio.file.Path file=java.nio.file.Files.write(directory.resolve("fixture.apk"),new byte[]{1});InstallerDriver.requireLauncherFixture(file,fixture);
        assertThrows(IOException.class,()->InstallerDriver.requireLauncherFixture(directory.resolve("missing.apk"),fixture));
        assertThrows(IOException.class,()->InstallerDriver.requireLauncherFixture(java.nio.file.Path.of("relative.apk"),fixture));
        JsonObject foreign=fixture.deepCopy();foreign.addProperty("package","foreign.app");assertThrows(IOException.class,()->InstallerDriver.requireLauncherFixture(file,foreign));
        JsonObject empty=fixture.deepCopy();empty.addProperty("label","");assertThrows(IOException.class,()->InstallerDriver.requireLauncherFixture(file,empty));
    }
    @Test void nativeObservationFailurePreservesTheActualRejectionAndRawEvidence() {
        JsonObject response=json("""
            {"ok":false,"error":{"code":"TREE_REJECTED","outcome":"not_started","retryable":false,
            "message":"TREE_REJECTED: guest could not verify current UI tree; reason=application_window_unverified; error_type=IllegalStateException"}}
            """);
        var reply=new ru.voboost.voyemu.VoyemuClient.Reply(ru.voboost.voyemu.VoyemuClient.State.RESPONSE,null,4,response,"private transport diagnostics");
        var evidence=java.nio.file.Path.of("/private-run/settings-ui-806.json");
        String failure=InstallerDriver.nativeObservationFailure("settings-ui",evidence,reply).getMessage();
        assertTrue(failure.contains("native observation unavailable: settings-ui"));
        assertTrue(failure.contains("state=RESPONSE"));
        assertTrue(failure.contains("code=TREE_REJECTED"));
        assertTrue(failure.contains("outcome=not_started"));
        assertTrue(failure.contains("reason=application_window_unverified"));
        assertTrue(failure.contains(evidence.toString()));
        assertFalse(failure.contains(reply.diagnostic()));
    }
    @Test void unknownObservationKeepsItsStateWithoutCopyingTransportDiagnostics() {
        var reply=new ru.voboost.voyemu.VoyemuClient.Reply(ru.voboost.voyemu.VoyemuClient.State.UNKNOWN,null,null,null,"private ownership material");
        String failure=InstallerDriver.nativeObservationFailure("settings-ui",java.nio.file.Path.of("/private-run/settings-ui-807.json"),reply).getMessage();
        assertTrue(failure.contains("state=UNKNOWN"));
        assertFalse(failure.contains("code=TREE_REJECTED"));
        assertFalse(failure.contains(reply.diagnostic()));
    }
    @Test void rejectionSummaryIsBoundedAndCannotInsertLogLines() {
        JsonObject response=json("{\"ok\":false,\"error\":{}}");
        JsonObject error=response.getAsJsonObject("error");
        error.addProperty("code","X".repeat(1000));error.addProperty("outcome","Y".repeat(1000));
        error.addProperty("message","before\nforged\r\tline\u0000"+"Z".repeat(10000));
        var reply=new ru.voboost.voyemu.VoyemuClient.Reply(ru.voboost.voyemu.VoyemuClient.State.RESPONSE,null,4,response,"");
        String failure=InstallerDriver.nativeObservationFailure("settings-ui",java.nio.file.Path.of("/private-run/settings-ui-808.json"),reply).getMessage();
        assertTrue(failure.contains("message=before forged  line "));
        assertFalse(failure.chars().anyMatch(Character::isISOControl));
        assertTrue(failure.length()<1000);
        assertFalse(failure.contains("X".repeat(65)));assertFalse(failure.contains("Y".repeat(65)));
        assertFalse(failure.contains("Z".repeat(513)));
    }
    @Test void onlyTheObservedTransientTreeRejectionPermitsReadonlyResampling()throws Exception {
        JsonObject frame=json("""
            {"state":"RESPONSE","response":{"ok":false,"error":{"code":"TREE_REJECTED","outcome":"not_started",
            "message":"TREE_REJECTED: guest could not verify current UI tree; reason=application_window_unverified; error_type=IllegalStateException"}}}
            """);
        assertTrue(InstallerDriver.pendingUiObservation(frame));
        for(String field:List.of("code","outcome","message")) {
            JsonObject wrong=frame.deepCopy();wrong.getAsJsonObject("response").getAsJsonObject("error").addProperty(field,"different");
            assertFalse(InstallerDriver.pendingUiObservation(wrong),field);
        }
        JsonObject unknown=frame.deepCopy();unknown.addProperty("state","UNKNOWN");assertFalse(InstallerDriver.pendingUiObservation(unknown));
        JsonObject ok=frame.deepCopy();ok.getAsJsonObject("response").addProperty("ok",true);assertFalse(InstallerDriver.pendingUiObservation(ok));
        JsonObject noError=frame.deepCopy();noError.getAsJsonObject("response").remove("error");assertFalse(InstallerDriver.pendingUiObservation(noError));
        assertFalse(InstallerDriver.pendingUiObservation(new JsonObject()));
    }

}
