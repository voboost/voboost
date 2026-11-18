package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.tests.installer.cli.InstallerDriver.PedestrianWarningView;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;

@Order(3)
@ExtendWith(EmulatorFixture.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentPedestrianWarning {
    private PedestrianWarningView pw;

    private void prepareCase(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the PW cases");
        Path references = Path.of("tests/emulator/agents/agent-pedestrian-warning").toRealPath();
        pw = prepared.installer.pedestrianWarningView(prepared.catalog, references, prepared.machineName);
        pw.prepare();
    }

    @Test
    @Order(1)
    void hidesOffIconFromVehicleSettings(EmulatorPreparation prepared) throws Exception {
        prepareCase(prepared);
        checkHideAndRestore();
    }

    @Test
    @Order(2)
    void hidesOffIconFromDrawer(EmulatorPreparation prepared) throws Exception {
        prepareCase(prepared);
        pw.useDrawer();
        checkHideAndRestore();
    }

    @Test
    @Order(3)
    void hidesAndRestoresOffIconAfterColdRestart(EmulatorPreparation prepared) throws Exception {
        prepareCase(prepared);
        pw.enable();
        pw.restartBoth();
        checkHideAndRestore();
    }

    @Test
    @Order(5)
    void keepsOffIconHiddenAfterColdRestart(EmulatorPreparation prepared) throws Exception {
        prepareCase(prepared);
        pw.enable();
        pw.select(false);
        pw.assertView(PedestrianWarningView.HIDDEN, false);
        pw.restartBoth();
        pw.assertView(PedestrianWarningView.HIDDEN, false);
    }

    private void checkHideAndRestore() throws Exception {
        try {
            pw.enable();
            pw.assertView(PedestrianWarningView.VISIBLE, true);
            pw.select(false);
            pw.assertView(PedestrianWarningView.HIDDEN, false);
            pw.select(true);
            pw.assertView(PedestrianWarningView.VISIBLE, true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception | AssertionError failure) {
            try {
                failure.addSuppressed(new IOException("PW failure logs: " + pw.failureLogs()));
            } catch (InterruptedException interrupted) {
                failure.addSuppressed(interrupted);
            }
            throw failure;
        }
    }

    @Test
    @Order(4)
    void keepsSoundDisabledAndIconsUnchangedAfterMultimediaRestart(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the pedestrian warning case");
        Path references = Path.of("tests/emulator/agents/agent-pedestrian-warning").toRealPath();
        pw = prepared.installer.pedestrianWarningView(prepared.catalog, references, prepared.machineName);
        try {
            pw.prepareSound();
            pw.useDrawer();
            pw.enable();
            var on = pw.captureDrawerIcon("on-before-restart", true);
            pw.select(false);
            pw.assertView(PedestrianWarningView.HIDDEN, false);
            var before = pw.captureDrawerIcon("off-before-restart", false);
            assertFalse(on.image().samePixels(before.image()), "Drawer ON and OFF icons must differ");
            pw.restartMultimedia();
            pw.assertView(PedestrianWarningView.HIDDEN, false);
            var after = pw.captureDrawerIcon("off-after-restart", false);
            assertTrue(before.image().samePixels(after.image()),
                    "Disabled drawer icon changed after restart: " + before.file() + " / " + after.file());
            pw.select(true);
            var restored = pw.captureDrawerIcon("on-after-restart", true);
            assertTrue(on.image().samePixels(restored.image()), "Drawer ON icon was not restored");
            System.out.println("Pedestrian warning drawer OFF before restart: " + before.file());
            System.out.println("Pedestrian warning drawer OFF after restart: " + after.file());
        } catch (Exception | AssertionError failure) {
            try { failure.addSuppressed(new IOException("Pedestrian warning logs: " + pw.failureLogs())); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); failure.addSuppressed(interrupted); }
            throw failure;
        }
    }

    @AfterEach
    void restore() throws Exception {
        if (pw != null) pw.close();
    }
}
