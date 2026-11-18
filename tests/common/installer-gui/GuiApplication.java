package ru.voboost.tests.installer.gui;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

/** Owns the native launcher and returned application separately. No app launch occurs during compilation. */
public final class GuiApplication implements AutoCloseable {
    private final Path work;
    private final JsonObject owner, receipt;
    private final MacGui.Backend launcher;
    private final ProcessHandle application;
    private final MacGui gui;
    private boolean released, closed, releaseAttempted;

    public static GuiApplication launch(Path nativeLauncher, Path nativeObserver, Path app, String device, Path work) throws Exception {
        if (!"Mac OS X".equals(System.getProperty("os.name"))) throw new IOException("BLOCKED: packaged GUI requires macOS");
        app = app.toRealPath();
        if (!app.getFileName().toString().equals("Voboost Installer.app") || !Files.getFileStore(app).isReadOnly()
                || !Files.isExecutable(app.resolve("Contents/MacOS/voboost-installer"))) throw new IOException("BLOCKED: read-only candidate app required");
        validateDevice(device);
        RunFiles.directory(work);
        try (var entries = Files.list(work)) { if (entries.findAny().isPresent()) throw new IOException("fresh GUI app owner required; retained launch cannot replay"); }
        if (HostProcess.run(work.resolve("signature.log"), List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()), 60000).exit() != 0)
            throw new IOException("BLOCKED: candidate signature failed");
        JsonObject owner = configuration(app, device);
        RunFiles.write(work.resolve("launcher.json"), owner);
        JsonObject state = new JsonObject(); state.addProperty("ownerId", owner.get("ownerId").getAsString()); state.addProperty("state", "dispatched");
        RunFiles.write(work.resolve("application.json"), state);
        MacGui.Backend launcher = null;
        try {
            launcher = MacGui.processBackend(nativeLauncher.toRealPath(), work.resolve("launcher.json"), work);
            JsonObject receipt = launcher.receive(Duration.ofSeconds(25));
            ProcessHandle application = validateReceipt(owner, receipt, launcher.process().pid());
            RunFiles.write(work.resolve("launch-receipt.json"), receipt);
            state.addProperty("state", "owned"); state.add("receipt", receipt); RunFiles.write(work.resolve("application.json"), state);
            MacGui gui = MacGui.attach(nativeObserver, app, application, work.resolve("observer"));
            return new GuiApplication(work, owner, receipt, launcher, application, gui);
        } catch (Exception failure) {
            state.addProperty("state", "unknown"); RunFiles.write(work.resolve("application.json"), state);
            if (launcher != null) try { launcher.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private GuiApplication(Path work, JsonObject owner, JsonObject receipt, MacGui.Backend launcher, ProcessHandle application, MacGui gui) {
        this.work = work; this.owner = owner; this.receipt = receipt; this.launcher = launcher; this.application = application; this.gui = gui;
    }
    static void validateDevice(String device) throws IOException {
        if (device == null || !device.matches("emulator-[1-9][0-9]{0,4}")) throw new IOException("owned emulator target required");
        int port = Integer.parseInt(device.substring(9));
        if (port < 1024 || port > 65534 || port % 2 != 0) throw new IOException("canonical emulator target required");
    }
    static JsonObject configuration(Path app, String device) {
        JsonObject config = new JsonObject(); config.addProperty("schemaVersion", 1); config.addProperty("ownerId", UUID.randomUUID().toString());
        config.addProperty("coordinatorPid", ProcessHandle.current().pid()); config.addProperty("appPath", app.toString()); config.addProperty("device", device); return config;
    }
    static ProcessHandle validateReceipt(JsonObject owner, JsonObject receipt, long launcherPid) throws IOException {
        if (!receipt.keySet().equals(Set.of("schemaVersion", "ownerId", "status", "pid", "startedMillis", "appPath", "device"))
                || !receipt.get("schemaVersion").equals(owner.get("schemaVersion")) || !"launched".equals(receipt.get("status").getAsString())) throw new IOException("native app launch receipt differs");
        for (String field : List.of("ownerId", "appPath", "device")) if (!receipt.get(field).equals(owner.get(field))) throw new IOException("native app launch owner differs");
        String rawPid = receipt.get("pid").getAsString(), started = receipt.get("startedMillis").getAsString();
        if (!receipt.getAsJsonPrimitive("pid").isNumber() || !rawPid.matches("[1-9][0-9]{0,9}")
                || !receipt.getAsJsonPrimitive("startedMillis").isString() || !started.matches("[1-9][0-9]{0,18}")) throw new IOException("native app launch process identity unavailable");
        long pid = Long.parseLong(rawPid);
        if (pid > Integer.MAX_VALUE || pid == launcherPid || pid == ProcessHandle.current().pid()) throw new IOException("launcher/coordinator cannot stand in for application");
        ProcessHandle app = ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElseThrow(() -> new IOException("native app launch process ended"));
        Path executable = Path.of(owner.get("appPath").getAsString()).resolve("Contents/MacOS/voboost-installer");
        if (!Path.of(app.info().command().orElseThrow(() -> new IOException("native app command unavailable"))).toRealPath().equals(executable)
                || !started.equals(Long.toString(app.info().startInstant().orElseThrow(() -> new IOException("native app start unavailable")).toEpochMilli())))
            throw new IOException("native app launch path/starttime differs; clock equivalence requires runtime proof");
        return app;
    }
    public MacGui gui() { return gui; }
    public Path receipt() { return work.resolve("launch-receipt.json"); }
    /** Explicit idle release only; unexpected exit or unknown UI action never authorizes termination. */
    public void release() throws Exception {
        if (closed || released || releaseAttempted || gui.pending() != null || !application.isAlive()) throw new IOException("native app cannot release with unknown/pending outcome");
        JsonObject request = new JsonObject(); request.addProperty("schemaVersion", 1); request.add("ownerId", owner.get("ownerId"));
        request.addProperty("op", "release"); request.add("pid", receipt.get("pid")); request.add("startedMillis", receipt.get("startedMillis"));
        releaseAttempted = true; RunFiles.write(work.resolve("release-dispatched.json"), request);
        JsonObject response = launcher.exchange(request, Duration.ofSeconds(8));
        if (!response.keySet().equals(Set.of("schemaVersion", "ownerId", "status", "pid", "startedMillis"))
                || !"released".equals(response.get("status").getAsString())) throw new IOException("native app release unverified");
        for (String field : List.of("schemaVersion", "ownerId", "pid", "startedMillis")) if (!response.get(field).equals(request.get(field))) throw new IOException("native app release identity differs");
        if (application.isAlive()) throw new IOException("native app release process still alive");
        RunFiles.write(work.resolve("release-receipt.json"), response); released = true;
    }
    @Override public void close() throws Exception {
        if (closed) return; closed = true;
        try { gui.close(); } finally { launcher.close(); }
        if (!released) throw new IOException("native app ownership retained for reconciliation at " + work);
    }
}
