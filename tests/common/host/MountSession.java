package ru.voboost.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Keeps work whenever an attachment outcome or cleanup is uncertain. */
public final class MountSession {
    public interface Backend {
        void attach() throws Exception;
        boolean mounted() throws Exception;
        void detach() throws Exception;
        void removeWork() throws Exception;
    }
    private final Backend backend;
    private final Path work;
    private boolean attachStarted;
    private boolean attachConfirmed;
    public MountSession(Path work, Backend backend) { this.work = work.toAbsolutePath(); this.backend = backend; }
    public static MountSession readOnlyDmg(Path work, Path dmg) throws IOException {
        if (!dmg.isAbsolute() || !Files.isRegularFile(dmg)) throw new IOException("Absolute candidate DMG required");
        Path volume = work.resolve("volume");
        return new MountSession(work, new Backend() {
            public void attach() throws Exception {
                command("attach", List.of("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse",
                        "-mountpoint", volume.toString(), dmg.toString()));
            }
            public boolean mounted() throws IOException {
                return Files.exists(volume) && !Files.getAttribute(volume, "unix:dev")
                        .equals(Files.getAttribute(work, "unix:dev"));
            }
            public void detach() throws Exception {
                command("detach", List.of("/usr/bin/hdiutil", "detach", volume.toString()));
            }
            public void removeWork() throws IOException {
                Files.deleteIfExists(volume);
            }
            private void command(String step, List<String> args) throws Exception {
                Path log = work.resolve(step + ".log");
                if (HostProcess.run(log, args, 60000).exit() != 0) {
                    throw new IOException("Candidate mount " + step + " failed. See " + log);
                }
            }
        });
    }

    public void claimAttached() throws Exception {
        if (!backend.mounted()) throw new IOException("Prepared candidate mount is unavailable: " + work);
        attachStarted = true;
        attachConfirmed = true;
    }

    public void attach() throws Exception {
        attachStarted = true;
        backend.attach();
        if (!backend.mounted()) throw new IllegalStateException("attach returned without mount");
        attachConfirmed = true;
    }
    public void cleanup() throws Exception {
        try {
            boolean mounted = backend.mounted();
            if (mounted) {
                backend.detach();
                if (backend.mounted()) throw new IllegalStateException("detach did not remove mount");
            }
            if (attachStarted && !attachConfirmed) throw new IOException("attach outcome remains uncertain");
            backend.removeWork();
        } catch (Exception error) {
            throw new IOException("BLOCKED: retained work at " + work, error);
        }
    }
}
