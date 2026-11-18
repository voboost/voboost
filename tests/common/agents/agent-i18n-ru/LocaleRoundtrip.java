package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** Restore an observed OEM locale only after the opposite selection has been confirmed. */
public final class LocaleRoundtrip {
    @FunctionalInterface
    public interface Selection {
        /** Select and independently confirm the requested locale, retaining its evidence. */
        Path select(String locale) throws Exception;
    }

    public record Proof(Path opposite, Path restored) {}

    public static Proof run(String original, Selection selection) throws Exception {
        if (!Set.of("en", "zh").contains(original)) throw new IOException("observed OEM English or Chinese locale required");
        Path opposite = selection.select(original.equals("en") ? "zh" : "en");
        if (opposite == null) throw new IOException("opposite locale evidence unavailable");
        Path restored = selection.select(original);
        if (restored == null) throw new IOException("restored locale evidence unavailable");
        return new Proof(opposite, restored);
    }

    private LocaleRoundtrip() {}
}
