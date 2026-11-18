package ru.voboost.tests.installer.gui;

import java.io.IOException;
import java.nio.file.Path;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.installer.cli.InstallerDriver;
import ru.voboost.voyemu.OwnedSession;

/** Borrows exactly one existing device owner. This class cannot start or acquire a guest session. */
public final class GuiScenario {
    private GuiScenario() {}
    public record Evidence(Path connected, Path installed, Path reopened, Path removed) {}
    public static Evidence execute(Path launcher, Path observer, Path app, InstallerDriver readOnly,
            ProductRun run, OwnedSession existingSession, Path work) throws Exception {
        if (run == null || existingSession == null || !run.owns(existingSession) || existingSession.cleanupVerified() || run.pending() != null)
            throw new IOException("BLOCKED: GUI recipes require the same live idle coordinator-owned session");
        RunFiles.directory(work);
        readOnly.requireAbsent(); String device = readOnly.status().get("deviceSerial").getAsString();
        run.beginGui(work);
        Path connected, installed, reopened, removed;
        try (GuiApplication first = GuiApplication.launch(launcher, observer, app, device, work.resolve("app-first"))) {
            GuiLifecycle route = new GuiLifecycle(first.gui(), readOnly, run, device, work.resolve("route-first"));
            connected = route.connect(); installed = route.install(); first.release();
        }
        try (GuiApplication second = GuiApplication.launch(launcher, observer, app, device, work.resolve("app-second"))) {
            GuiLifecycle route = new GuiLifecycle(second.gui(), readOnly, run, device, work.resolve("route-second"));
            reopened = route.management(); removed = route.uninstall(); second.release();
        }
        readOnly.requireAbsent();
        com.google.gson.JsonObject proof = new com.google.gson.JsonObject();
        proof.addProperty("connected", connected.toString()); proof.addProperty("installed", installed.toString());
        proof.addProperty("reopened", reopened.toString()); proof.addProperty("removed", removed.toString());
        proof.addProperty("firstRelease", work.resolve("app-first/release-receipt.json").toString());
        proof.addProperty("secondRelease", work.resolve("app-second/release-receipt.json").toString());
        Path complete = work.resolve("completed.json"); RunFiles.write(complete, proof); run.completeGui(complete);
        return new Evidence(connected, installed, reopened, removed);
    }
}
