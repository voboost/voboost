package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FixtureClassificationTest {

    private static final String BOOT = "abcdefab-cdef-abcd-efab-cdefabcdefab";

    private static String present(long flags) {
        return "fixture-classification-v1\nbootId=" + BOOT + "\npackage=ru.voboost.tests.launcherfixture\nstate=present\nuid=10123\nflags=" + flags + "\nsystem=" + ((flags & 1) != 0) + "\nupdatedSystem=" + ((flags & 128) != 0) + "\n";
    }

    @Test

    void platformFlagsAreIndependentAndUnsigned() throws Exception {
        FixtureClassification.read(present(0), BOOT).requireOwnedUserApplication();
        for (long flags : new long[] {
            1, 128, 129, 2147483776L, 4294967295L
        }) {
            var result = FixtureClassification.read(present(flags), BOOT);
            assertEquals(flags, result.flags());
            assertEquals((flags & 1) != 0, result.system());
            assertEquals((flags & 128) != 0, result.updatedSystem());
            assertThrows(IOException.class, result::requireOwnedUserApplication);
        }
    }

    @Test

    void absenceHasNoInventedClassification() throws Exception {
        var result = FixtureClassification.read(present(0).substring(0,
                present(0).indexOf("state=")) + "state=absent\n", BOOT);
        assertFalse(result.present());
        assertNull(result.uid());
        assertNull(result.flags());
        assertNull(result.system());
        assertNull(result.updatedSystem());
        assertThrows(IOException.class, result::requireOwnedUserApplication);
    }

    @Test

    void malformedStaleAndAmbiguousValuesFailClosed() {
        for (String body : new String[] {
            "", present(0).trim(), present(0) + "\n", present(0).replace("\n", "\r\n"),
                    present(0).replace("10123", "010123"), present(0).replace("10123", "100000"),
                    present(0).replace("flags=0", "flags=4294967296"),
                    present(0).replace("system=false", "system=true"),
                    present(0).replace("ru.voboost.tests.launcherfixture", "foreign"),
                    present(0).replace(BOOT, "11111111-1111-1111-1111-111111111111")
        })
            assertThrows(IOException.class, () -> FixtureClassification.read(body, BOOT));
    }
}
