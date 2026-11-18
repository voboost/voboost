package ru.voboost.tests.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import ru.voboost.voyemu.PixelImage;

/** Compares a decoded view with a reviewed lossless reference. */
public final class ViewAssertion {
    private record Reference(Path file, PixelImage image) {}

    private final Map<String, Reference> references;

    public ViewAssertion(Path directory, String variant, int width, int height, String... states) throws IOException {
        if (!variant.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*"))
            throw new IllegalArgumentException("invalid view variant: " + variant);
        Map<String, Reference> loaded = new HashMap<>();
        for (String state : states) {
            if (!state.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || loaded.containsKey(state)) {
                throw new IllegalArgumentException("invalid or duplicate view state: " + state);
            }
            Path file = directory.resolve(state + "-" + variant + ".webp");
            try {
                loaded.put(state, new Reference(file, PixelImage.readWebp(file, width, height)));
            } catch (IOException invalid) {
                throw new IOException("Cannot read view reference " + file, invalid);
            }
        }
        references = Map.copyOf(loaded);
    }

    public void assertView(String state, Path actualFile, PixelImage actual) {
        Reference reference = references.get(state);
        if (reference == null) throw new IllegalArgumentException("unprepared view state: " + state);
        if (!reference.image().samePixels(actual)) {
            throw new AssertionError("View differs for " + state + "\nActual: " + actualFile
                    + "\nReference: " + reference.file());
        }
    }
}
