package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

/** Static historical reference is only a host fixture, never a current device observation. */
class NaviMoscowRasterTest {
    private static final Path FIXTURE = Path.of("tests/unit/agents/agent-i18n-ru/navi-raster").toAbsolutePath().normalize();
    private static int[] reference() throws Exception {
        var bounds = RunFiles.read(FIXTURE.resolve("moscow-reference.json")).getAsJsonObject("bounds");
        return new int[] {bounds.get("x").getAsInt(), bounds.get("y").getAsInt(),
            bounds.get("width").getAsInt(), bounds.get("height").getAsInt()};
    }
    private static PixelImage image(Path file) throws IOException { return PixelImage.read(file, 1920, 720); }
    @Test void knownReviewedTextMatchesAndClippedGeometryFails() throws Exception {
        PixelImage reference = image(FIXTURE.resolve("moscow-reference.png")); int[] bounds = reference();
        assertTrue(NaviMoscowRaster.compareMasks(reference, bounds, reference, bounds) >= 40);
        int[] clipped = bounds.clone(); clipped[2]--;
        assertThrows(IOException.class, () -> NaviMoscowRaster.compareMasks(reference, bounds, reference, clipped));
    }
    @Test void missingGlyphPixelFailsButCyanCaretIsIgnored(@TempDir Path temporary) throws Exception {
        Path original = FIXTURE.resolve("moscow-reference.png"); PixelImage reference = image(original); int[] bounds = reference();
        var bitmap = javax.imageio.ImageIO.read(original.toFile());
        int whiteX = -1, whiteY = -1;
        for (int y = bounds[1]; y < bounds[1] + bounds[3] && whiteX < 0; y++)
            for (int x = bounds[0]; x < bounds[0] + bounds[2]; x++) {
                int color = bitmap.getRGB(x, y);
                if ((color >>> 16 & 255) >= 192 && (color >>> 8 & 255) >= 192 && (color & 255) >= 192) { whiteX = x; whiteY = y; break; }
            }
        assertTrue(whiteX >= 0); bitmap.setRGB(whiteX, whiteY, 0xff000000);
        Path changed = temporary.resolve("changed.png").toAbsolutePath(); javax.imageio.ImageIO.write(bitmap, "png", changed.toFile());
        PixelImage damaged = image(changed);
        assertThrows(IOException.class, () -> NaviMoscowRaster.compareMasks(reference, bounds, damaged, bounds));
        bitmap = javax.imageio.ImageIO.read(original.toFile()); bitmap.setRGB(bounds[0], bounds[1], 0xff00ffff);
        Path caret = temporary.resolve("caret.png").toAbsolutePath(); javax.imageio.ImageIO.write(bitmap, "png", caret.toFile());
        assertTrue(NaviMoscowRaster.compareMasks(reference, bounds, image(caret), bounds) >= 40);
    }
}
