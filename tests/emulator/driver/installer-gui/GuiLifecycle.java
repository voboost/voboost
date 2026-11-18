package ru.voboost.tests.installer.gui;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.installer.cli.InstallerDriver;

/** Genuine native UI recipes. Backend access here is exclusively independent readback. */
public final class GuiLifecycle {
    private record Text(String welcome, String consent, String next, String connected, String install,
                        String installed, String finish, String management, String uninstall, String cancel,
                        String removalTitle, String removalBody) {}
    private static final Text EN = new Text("Continue", "I have read the MIT license", "Next", "Connected: ",
        "Start Installation", "Installation complete.", "Finish", "Voboost Management", "Uninstall", "Cancel",
        "Uninstall Voboost?", "Voboost and the changes it made will be removed. Keep the cable connected until removal finishes.");
    private static final Text RU = new Text("Продолжить", "Я ознакомился с лицензией MIT", "Далее", "Подключено: ",
        "Начать установку", "Установка завершена.", "Завершить", "Управление Voboost", "Удалить", "Отмена",
        "Удалить Voboost?", "Voboost и внесённые им изменения будут удалены. Не отключайте кабель до завершения удаления.");
    private final MacGui gui;
    private final InstallerDriver observer;
    private final ProductRun run;
    private final String device;
    private final Path work;
    private Text text;
    private int sequence;

    public GuiLifecycle(MacGui gui, InstallerDriver readOnlyObserver, ProductRun run, String device, Path work) throws IOException {
        GuiApplication.validateDevice(device);
        this.gui = gui; observer = readOnlyObserver; this.run = run; this.device = device; this.work = RunFiles.directory(work);
    }
    private MacGui.Observation waitFor(Predicate<MacGui.Observation> expected, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            MacGui.Observation observation = gui.observe();
            if (expected.test(observation)) return observation;
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("native GUI observation interrupted");
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        throw new IOException("native GUI expected state deadline expired; no action replay authorized");
    }
    private MacGui.Observation press(MacGui.Target target, Predicate<MacGui.Observation> expected, Duration timeout) throws Exception {
        MacGui.Action action;
        try { action = gui.press(target); }
        catch (MacGui.Unknown unknown) { action = gui.pending(); if (action == null) throw unknown; }
        MacGui.Observation after = waitFor(expected, timeout);
        action.confirm(after, expected); return after;
    }
    private JsonObject status() throws Exception {
        JsonObject value = observer.status();
        if (!device.equals(value.get("deviceSerial").getAsString())) throw new IOException("GUI/readback device differs");
        return value;
    }
    private Path proof(String name, MacGui.Observation ui, JsonObject readback) throws IOException {
        Path file = work.resolve(String.format(java.util.Locale.ROOT, "%03d-%s.json", ++sequence, name));
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("device", device);
        value.addProperty("nativeObservation", ui.evidence().toString()); value.add("independentReadback", readback);
        RunFiles.write(file, value); return file;
    }
    /** G01: welcome, unchecked consent gate, actual connected identity and no installation before explicit start. */
    public Path connect() throws Exception {
        observer.requireAbsent(); JsonObject before = status();
        MacGui.Observation welcome = waitFor(ui -> ui.enabled("AXButton", EN.welcome) || ui.enabled("AXButton", RU.welcome), Duration.ofSeconds(60));
        boolean english = welcome.enabled("AXButton", EN.welcome), russian = welcome.enabled("AXButton", RU.welcome);
        if (english == russian) throw new IOException("native GUI language is ambiguous"); text = english ? EN : RU;
        MacGui.Observation terms = press(welcome.target("AXButton", text.welcome), ui -> ui.enabled("AXCheckBox", text.consent), Duration.ofSeconds(30));
        if (terms.enabled("AXButton", text.next)) throw new IOException("license gate accepted without checked consent");
        MacGui.Observation accepted = press(terms.target("AXCheckBox", text.consent), ui -> ui.enabled("AXButton", text.next), Duration.ofSeconds(30));
        MacGui.Observation connection = press(accepted.target("AXButton", text.next), ui -> {
            try { return ui.enabled("AXButton", text.next) && !ui.uniqueTextStarting(text.connected).isBlank(); }
            catch (IOException unavailable) { return false; }
        }, Duration.ofSeconds(90));
        String identified = connection.uniqueTextStarting(text.connected);
        if (identified.length() > 256) throw new IOException("native connected device label exceeds bound");
        observer.requireAbsent(); JsonObject after = status();
        if (!before.equals(after)) throw new IOException("GUI connection changed installed state before installation");
        Path evidence = proof("G01-connected", connection, after);
        press(connection.target("AXButton", text.next), ui -> ui.enabled("AXButton", text.install), Duration.ofSeconds(30));
        return evidence;
    }
    /** G02 install portion; the owning runner must additionally reopen the genuine app and verify management. */
    public Path install() throws Exception {
        if (text == null) throw new IOException("G01 connection required before installation");
        observer.requireAbsent(); MacGui.Observation ready = waitFor(ui -> ui.enabled("AXButton", text.install), Duration.ofSeconds(30));
        JsonObject input = new JsonObject(); input.addProperty("executor", "native-gui"); input.addProperty("device", device); input.addProperty("phase", "gui-install");
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.INSTALL, input)) {
            operation.dispatch();
            MacGui.Observation completed = press(ready.target("AXButton", text.install), ui -> ui.visible(text.installed) && ui.enabled("AXButton", text.next), Duration.ofMinutes(10));
            observer.requireHealthy(); JsonObject readback = status();
            Path evidence = proof("G02-installed", completed, readback);
            MacGui.Observation finish = press(completed.target("AXButton", text.next), ui -> ui.enabled("AXButton", text.finish), Duration.ofSeconds(30));
            operation.complete(evidence, completed.evidence(), finish.evidence()); return evidence;
        }
    }
    public Path management() throws Exception {
        MacGui.Observation state = waitFor(ui -> ui.visible(EN.management) || ui.visible(RU.management), Duration.ofSeconds(90));
        text = state.visible(EN.management) ? EN : RU;
        if (!state.visible(device) || !state.enabled("AXButton", text.uninstall)) throw new IOException("reopened native management device/actions differ");
        observer.requireHealthy(); return proof("G02-reopened-management", state, status());
    }
    private MacGui.Observation dialog() throws Exception {
        MacGui.Observation management = waitFor(ui -> ui.visible(text.management) && ui.enabled("AXButton", text.uninstall), Duration.ofSeconds(30));
        return press(management.target("AXButton", text.uninstall), ui -> ui.visible(text.removalTitle) && ui.visible(text.removalBody) && ui.enabled("AXButton", text.cancel), Duration.ofSeconds(30));
    }
    /** G06: actual cancel, unchanged healthy readback, then one GUI confirmation and independent absence. */
    public Path uninstall() throws Exception {
        if (text == null) throw new IOException("reopened management required");
        observer.requireHealthy(); JsonObject before = status();
        MacGui.Observation dialog = dialog();
        MacGui.Observation canceled = press(dialog.dialogTarget(text.removalTitle, text.removalBody, text.cancel, text.cancel),
            ui -> ui.visible(text.management) && !ui.visible(text.removalTitle), Duration.ofSeconds(30));
        observer.requireHealthy(); JsonObject unchanged = status();
        for (String field : List.of("deviceSerial", "installedVersion", "contentRelation", "activeGeneration", "startupMode", "health", "daemonStatus"))
            if (!before.has(field) || !before.get(field).equals(unchanged.get(field))) throw new IOException("GUI uninstall cancel changed " + field);
        Path canceledProof = proof("G06-canceled", canceled, unchanged);
        MacGui.Observation confirmation = dialog();
        JsonObject input = new JsonObject(); input.addProperty("executor", "native-gui"); input.addProperty("device", device); input.addProperty("phase", "gui-uninstall");
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.UNINSTALL, input)) {
            operation.dispatch();
            MacGui.Observation absent = press(confirmation.dialogTarget(text.removalTitle, text.removalBody, text.uninstall, text.cancel),
                ui -> ui.enabled("AXButton", text.welcome) && !ui.visible(text.management), Duration.ofMinutes(10));
            observer.requireAbsent(); Path evidence = proof("G06-removed", absent, status());
            operation.complete(canceledProof, evidence, absent.evidence()); return evidence;
        }
    }
}
