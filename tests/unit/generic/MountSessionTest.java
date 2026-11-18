package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MountSessionTest {
    static class Fake implements MountSession.Backend {
        boolean mounted, removed, failAttach, interrupt, failStat, failDetach, failRemove;
        int detaches;
        public void attach() throws Exception {
            if (interrupt) throw new InterruptedException();
            if (failAttach) throw new IOException("attach failure");
            mounted = true;
        }
        public boolean mounted() throws Exception { if (failStat) throw new IOException("stat failure"); return mounted; }
        public void detach() throws Exception { detaches++; if (failDetach) throw new HostProcess.Timeout("detach"); mounted = false; }
        public void removeWork() throws Exception { if (failRemove) throw new IOException("cleanup failure"); removed = true; }
    }
    @Test void lateMountAfterAttachFailureIsDetachedButWorkRetained() throws Exception {
        Fake fake = new Fake(); fake.failAttach = true; MountSession s = new MountSession(Path.of("build/mount-test"), fake);
        assertThrows(IOException.class, s::attach); fake.mounted = true; IOException retained = assertThrows(IOException.class, s::cleanup); assertTrue(retained.getMessage().contains(Path.of("build/mount-test").toAbsolutePath().toString()));
        assertEquals(1, fake.detaches); assertFalse(fake.removed);
    }
    @Test void interruptedAttachRetainsWorkEvenIfNoMountObserved() throws Exception {
        Fake fake = new Fake(); fake.interrupt = true; MountSession s = new MountSession(Path.of("build/mount-test"), fake);
        assertThrows(InterruptedException.class, s::attach); IOException retained = assertThrows(IOException.class, s::cleanup); assertTrue(retained.getMessage().contains(Path.of("build/mount-test").toAbsolutePath().toString())); assertFalse(fake.removed);
    }
    @Test void detachTimeoutAndStatFailureNeverRemoveWork() throws Exception {
        Fake fake = new Fake(); MountSession s = new MountSession(Path.of("build/mount-test"), fake); s.attach(); fake.failDetach = true;
        assertInstanceOf(HostProcess.Timeout.class, assertThrows(IOException.class, s::cleanup).getCause()); assertFalse(fake.removed);
        fake.failDetach = false; fake.failStat = true;
        assertThrows(IOException.class, s::cleanup); assertFalse(fake.removed);
    }
    @Test void cleanupFailureIsReportedAfterSuccessfulDetach() throws Exception {
        Fake fake = new Fake(); MountSession s = new MountSession(Path.of("build/mount-test"), fake); s.attach(); fake.failRemove = true;
        assertThrows(IOException.class, s::cleanup); assertFalse(fake.mounted); assertFalse(fake.removed);
    }
    @Test void confirmedAttachAndDetachCanRemoveWork() throws Exception {
        Fake fake = new Fake(); MountSession s = new MountSession(Path.of("build/mount-test"), fake); s.attach(); s.cleanup();
        assertEquals(1, fake.detaches); assertTrue(fake.removed);
    }
}
