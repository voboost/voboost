package ru.voboost.tests.installer.cli;

import java.io.IOException;

/** Exact package-manager observation; command errors never represent package absence. */
public record FixturePackage(String boot, boolean present) {
    public static FixturePackage read(String body, String expectedBoot) throws IOException {
        if (body == null || body.length() > 160 || expectedBoot == null
                || !expectedBoot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("bounded fixture observation required");
        String prefix = "fixture-state-v1\nbootId=" + expectedBoot + "\npackage=ru.voboost.tests.launcherfixture\nstate=";
        if (body.equals(prefix + "present\n")) return new FixturePackage(expectedBoot, true);
        if (body.equals(prefix + "absent\n")) return new FixturePackage(expectedBoot, false);
        throw new IOException("fixture package observation frame or boot differs");
    }
}
