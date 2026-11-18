package ru.voboost.tests.installer.cli;

import java.io.IOException;

/** Independent fixed-package platform identity; absent carries no invented UID or flags. */
public record FixtureClassification(String boot, boolean present, Integer uid, Long flags,
        Boolean system, Boolean updatedSystem) {

    public static FixtureClassification read(String body, String boot) throws IOException {
        if (body == null || body.length() > 512 || boot == null
                || !boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("bounded fixture classification required");
        String prefix = "fixture-classification-v1\nbootId=" + boot + "\npackage=ru.voboost.tests.launcherfixture\nstate=";
        if (body.equals(prefix + "absent\n"))
            return new FixtureClassification(boot, false, null, null, null, null);
        String[] lines = body.split("\n", - 1);
        try {
            if (!body.startsWith(prefix + "present\n") || lines.length != 9 || !lines[8].isEmpty())
                throw new IllegalArgumentException();
            int uid = Integer.parseInt(lines[4].substring("uid=".length()));
            long flags = Long.parseLong(lines[5].substring("flags=".length()));
            if (uid < 0 || uid >= 100000 || flags < 0 || flags > 0xffffffffL
                    || !lines[4].equals("uid=" + uid) || !lines[5].equals("flags=" + flags)
                    || !lines[6].equals("system=" + ((flags & 1) != 0))
                    || !lines[7].equals("updatedSystem=" + ((flags & 128) != 0)))
                throw new IllegalArgumentException();
            return new FixtureClassification(boot, true, uid, flags, (flags & 1) != 0,
                    (flags & 128) != 0);
        } catch (RuntimeException invalid) {
            throw new IOException("fixture classification identity/frame differs", invalid);
        }
    }

    public void requireOwnedUserApplication() throws IOException {
        if (!present || uid == null || uid < 10000 || uid > 19999 || !Boolean.FALSE.equals(system)
                || !Boolean.FALSE.equals(updatedSystem))
            throw new IOException("owned fixture is not an ordinary user application");
    }
}
