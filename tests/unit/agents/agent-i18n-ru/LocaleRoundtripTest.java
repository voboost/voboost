package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocaleRoundtripTest {
    @Test void restoresExactOriginalOnlyAfterConfirmedOpposite() throws Exception {
        for (String original : List.of("en", "zh")) {
            List<String> calls = new ArrayList<>();
            var proof = LocaleRoundtrip.run(original, locale -> {
                calls.add(locale);
                return Path.of("/observed", locale);
            });
            assertEquals(List.of(original.equals("en") ? "zh" : "en", original), calls);
            assertEquals(Path.of("/observed", original), proof.restored());
        }
    }
    @Test void unknownOppositeNeverDispatchesRestoration() {
        List<String> calls = new ArrayList<>();
        assertThrows(IOException.class, () -> LocaleRoundtrip.run("en", locale -> {
            calls.add(locale);
            throw new IOException("unknown input outcome");
        }));
        assertEquals(List.of("zh"), calls);
    }
    @Test void missingEvidenceOrUnsupportedOriginalNeverProducesSuccess() {
        List<String> calls = new ArrayList<>();
        assertThrows(IOException.class, () -> LocaleRoundtrip.run("en", locale -> { calls.add(locale); return null; }));
        assertEquals(List.of("zh"), calls);
        assertThrows(IOException.class, () -> LocaleRoundtrip.run("ru", locale -> { fail("unexpected dispatch"); return null; }));
    }
}
