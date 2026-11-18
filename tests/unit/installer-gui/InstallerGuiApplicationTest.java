package ru.voboost.tests.installer.gui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Host receipt rejection only. No application, NSWorkspace or AX operation is executed. */
class InstallerGuiApplicationTest {
    @Test void acceptsOnlyCanonicalOwnedEmulatorTargets() throws Exception {
        for (String value : List.of("emulator-1024", "emulator-5554", "emulator-65534")) GuiApplication.validateDevice(value);
        for (String value : List.of("", "usb-device", "127.0.0.1:5555", "emulator-05554", "emulator-5555", "emulator-65536", "emulator-1022", "emulator-5554\n"))
            assertThrows(IOException.class, () -> GuiApplication.validateDevice(value));
        assertThrows(IOException.class, () -> GuiApplication.validateDevice(null));
    }
    private JsonObject receipt(JsonObject owner, long pid) {
        JsonObject result = owner.deepCopy(); result.remove("coordinatorPid"); result.addProperty("status", "launched");
        result.addProperty("pid", pid); result.addProperty("startedMillis", "1000"); return result;
    }
    @Test void launcherAndCoordinatorCannotStandInForOwnedApplication() throws Exception {
        JsonObject owner = GuiApplication.configuration(Path.of("/candidate/Voboost Installer.app"), "emulator-5554");
        assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, receipt(owner, ProcessHandle.current().pid()), 12));
        assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, receipt(owner, 123), 123));
    }
    @Test void foreignOrMalformedReceiptNeverAcquiresApplicationOwnership() throws Exception {
        JsonObject owner = GuiApplication.configuration(Path.of("/candidate/Voboost Installer.app"), "emulator-5554");
        for (String field : List.of("ownerId", "device", "appPath", "status", "startedMillis")) {
            JsonObject result = receipt(owner, 123); result.addProperty(field, "foreign");
            assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, result, 10));
        }
        JsonObject result = receipt(owner, 123); result.addProperty("extra", true);
        assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, result, 10));
        JsonObject spoof = receipt(owner, 123); spoof.addProperty("pid", "123");
        assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, spoof, 10));
    }
    @Test void currentLiveHostProcessWithForeignExecutableCannotBecomePackagedApp() throws Exception {
        Process child = new ProcessBuilder("/bin/sh", "-c", "while :; do :; done").start();
        try {
            JsonObject owner = GuiApplication.configuration(Path.of("/candidate/Voboost Installer.app"), "emulator-5554");
            JsonObject result = receipt(owner, child.pid()); result.addProperty("startedMillis", child.info().startInstant().orElseThrow().toEpochMilli() + "");
            assertThrows(IOException.class, () -> GuiApplication.validateReceipt(owner, result, 10));
            assertTrue(child.isAlive());
        } finally { child.destroyForcibly(); assertTrue(child.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)); }
    }
}
