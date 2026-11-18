package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.PixelImage;

class GenericAssertViewTest {
    private static final String HIDDEN = "agent-pedestrian-warning-off-hidden";
    private static final String VARIANT = "free-09-chinese-20260106-6.0.3-classic";
    private static final int SIZE = 46;
    private static final Path REFERENCES = Path.of("tests/emulator/agents/agent-pedestrian-warning").toAbsolutePath();

    @TempDir Path temp;

    @Test
    void comparesEveryDecodedPixelAcrossFormats() throws Exception {
        var assertion = new ViewAssertion(REFERENCES, VARIANT, SIZE, SIZE, HIDDEN);
        PixelImage reference = PixelImage.readWebp(REFERENCES.resolve(HIDDEN + "-" + VARIANT + ".webp"), SIZE, SIZE);
        var actual = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) actual.setRGB(x, y, reference.pixel(x, y));
        }
        Path png = temp.toRealPath().resolve("actual.png");
        assertTrue(ImageIO.write(actual, "png", png.toFile()));
        assertion.assertView(HIDDEN, png, PixelImage.read(png, SIZE, SIZE));

        actual.setRGB(0, 0, actual.getRGB(0, 0) ^ 1);
        assertTrue(ImageIO.write(actual, "png", png.toFile()));
        var failure = assertThrows(AssertionError.class,
                () -> assertion.assertView(HIDDEN, png, PixelImage.read(png, SIZE, SIZE)));
        assertTrue(failure.getMessage().contains(png.toString()));
    }

    @Test
    void rejectsMissingWrongSizedAndDamagedReferences() throws Exception {
        Path directory = temp.toRealPath();
        assertThrows(IOException.class, () -> new ViewAssertion(directory, VARIANT, SIZE, SIZE, HIDDEN));
        assertThrows(IOException.class, () -> new ViewAssertion(REFERENCES, VARIANT, SIZE - 1, SIZE, HIDDEN));
        Path file = directory.resolve(HIDDEN + "-" + VARIANT + ".webp");
        Files.writeString(file, "not a WebP");
        assertThrows(IOException.class, () -> new ViewAssertion(directory, VARIANT, SIZE, SIZE, HIDDEN));

        byte[] lossyTag = Files.readAllBytes(REFERENCES.resolve(file.getFileName()));
        lossyTag[15] = ' ';
        Files.write(file, lossyTag);
        assertThrows(IOException.class, () -> new ViewAssertion(directory, VARIANT, SIZE, SIZE, HIDDEN));
    }
}
