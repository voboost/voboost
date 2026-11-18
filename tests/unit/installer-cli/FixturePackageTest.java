package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FixturePackageTest {
    private static final String BOOT = "abcdefab-cdef-abcd-efab-cdefabcdefab";
    private static String frame(String state) { return "fixture-state-v1\nbootId=" + BOOT + "\npackage=ru.voboost.tests.launcherfixture\nstate=" + state + "\n"; }
    @Test void onlyExactSuccessfulStatesAreClassified() throws Exception {
        assertTrue(FixturePackage.read(frame("present"), BOOT).present());
        assertFalse(FixturePackage.read(frame("absent"), BOOT).present());
        for (String body : new String[]{"", "Error: package service unavailable\n", frame("unknown"), frame("absent").trim(),
                frame("absent") + "\n", frame("absent").replace("\n", "\r\n"), frame("absent").replace("ru.voboost.tests.launcherfixture", "ru.voboost.tests.launcherfixtureer"),
                frame("absent").replace("bootId=", "bootId=\0"), frame("present") + frame("absent")})
            assertThrows(IOException.class, () -> FixturePackage.read(body, BOOT));
    }
    @Test void staleBootAndNoncanonicalBootAreRejected() {
        assertThrows(IOException.class, () -> FixturePackage.read(frame("absent"), "11111111-1111-1111-1111-111111111111"));
        assertThrows(IOException.class, () -> FixturePackage.read(frame("absent").toUpperCase(), BOOT.toUpperCase()));
        assertThrows(IOException.class, () -> FixturePackage.read(frame("absent"), null));
        assertThrows(IOException.class, () -> FixturePackage.read(null, BOOT));
    }
}
