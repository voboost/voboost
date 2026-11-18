package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.PixelImage;
import ru.voboost.voyemu.VoyemuClient.ClusterRegion;

class PwGlyphTest {
    @TempDir Path root;
    private int serial;

    private PixelImage read(BufferedImage image) throws Exception {
        Path path = root.resolve("glyph-" + serial++ + ".png");
        assertTrue(ImageIO.write(image, "png", path.toFile()));
        return PixelImage.read(path, image.getWidth(), image.getHeight());
    }

    private BufferedImage reference() {
        var image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xffffbd00);
        image.setRGB(3, 3, 0xffffbd00);
        return image;
    }

    private BufferedImage shown() {
        var image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xffffbd00);
        image.setRGB(2, 2, 0xffffbd00);
        return image;
    }

    @Test void extractedPartialAlphaRgbIsNotMultipliedTwice() throws Exception {
        var source = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0x5d543f00);
        var visible = new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
        visible.setRGB(0,0,0xff574304);
        var hidden = new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
        hidden.setRGB(0,0,0xff050505);
        assertEquals(1,PwGlyph.compare(read(source),read(visible),read(hidden),read(visible),12));
    }

    @Test void exactBoundsExcludePaddingAndIgnoreBackground() throws Exception {
        var reference = read(reference());
        assertEquals(new ClusterRegion(1, 1, 3, 3), PwGlyph.bounds(reference));
        var hidden = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        hidden.setRGB(1, 1, 0xffffffff);
        assertEquals(2, PwGlyph.compare(reference, read(shown()), read(hidden), read(shown()), 0));
    }

    @Test void unchangedAndPartiallyHiddenGlyphsFail() throws Exception {
        var reference = read(reference());
        var visible = read(shown());
        assertThrows(IOException.class, () -> PwGlyph.compare(reference, visible, visible, visible, 12));
        var partial = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        partial.setRGB(2, 2, 0xffffbd00);
        assertThrows(IOException.class, () -> PwGlyph.compare(reference, visible, read(partial), visible, 12));
    }

    @Test void paddingAndWrongRestorationFail() throws Exception {
        var reference = read(reference());
        var visible = read(shown());
        var hidden = read(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB));
        assertThrows(IOException.class, () -> PwGlyph.compare(reference, visible, hidden, hidden, 12));
        var padded = read(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB));
        assertThrows(IOException.class, () -> PwGlyph.compare(reference, padded, hidden, visible, 12));
    }

    @Test void deliveryDoesNotMakeAStaleFrameReady() throws Exception {
        var reference = read(reference());
        var hidden = read(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB));
        var visible = read(shown());
        assertFalse(PwGlyph.matches(reference, hidden, true, 12));
        assertTrue(PwGlyph.matches(reference, visible, true, 12));
        assertFalse(PwGlyph.matches(reference, visible, false, 12));
        assertTrue(PwGlyph.matches(reference, hidden, false, 12));
    }

    @Test void blankReferenceFails() throws Exception {
        assertThrows(IOException.class, () -> PwGlyph.bounds(read(
                new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB))));
    }
}
