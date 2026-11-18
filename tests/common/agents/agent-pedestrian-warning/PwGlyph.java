package ru.voboost.tests.ui;

import java.io.IOException;
import ru.voboost.voyemu.PixelImage;
import ru.voboost.voyemu.VoyemuClient.ClusterRegion;

/** Compares only the nonblack pixels of a rendered OEM glyph. */
public final class PwGlyph {
    private PwGlyph() {}

    private static boolean nonblack(int pixel) {
        return (pixel >>> 24) != 0 && (pixel & 0xffffff) != 0;
    }

    public static ClusterRegion bounds(PixelImage reference) throws IOException {
        int left = reference.width(), top = reference.height(), right = -1, bottom = -1;
        for (int y = 0; y < reference.height(); y++) {
            for (int x = 0; x < reference.width(); x++) {
                if (!nonblack(reference.pixel(x, y))) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left) throw new IOException("reference has no visible glyph");
        return new ClusterRegion(left, top, right - left + 1, bottom - top + 1);
    }

    /** A delivered input may precede its rendered frame; a mismatch is not readiness. */
    public static boolean matches(PixelImage reference, PixelImage frame, boolean visible, int tolerance)
            throws IOException {
        if (tolerance < 0 || tolerance > 32) throw new IllegalArgumentException("bounded pixel tolerance required");
        ClusterRegion region = bounds(reference);
        if (frame.width() != region.width() || frame.height() != region.height())
            throw new IOException("capture must contain exactly the glyph bounds without padding");
        for (int y = 0; y < region.height(); y++) {
            for (int x = 0; x < region.width(); x++) {
                int expected = reference.pixel(region.x() + x, region.y() + y);
                if (nonblack(expected) && !near(visible ? expected : 0xff000000, frame.pixel(x, y), tolerance))
                    return false;
            }
        }
        return true;
    }

    /** All reference pixels must disappear and return; background pixels are not examined. */
    public static int compare(PixelImage reference, PixelImage visible, PixelImage hidden,
            PixelImage restored, int tolerance) throws IOException {
        if (tolerance < 0 || tolerance > 32) throw new IllegalArgumentException("bounded pixel tolerance required");
        ClusterRegion region = bounds(reference);
        for (PixelImage frame : new PixelImage[] {visible, hidden, restored}) {
            if (frame.width() != region.width() || frame.height() != region.height())
                throw new IOException("capture must contain exactly the glyph bounds without padding");
        }
        int compared = 0;
        for (int y = 0; y < region.height(); y++) {
            for (int x = 0; x < region.width(); x++) {
                int expected = reference.pixel(region.x() + x, region.y() + y);
                if (!nonblack(expected)) continue;
                if (!near(expected, visible.pixel(x, y), tolerance)
                        || !near(expected, restored.pixel(x, y), tolerance))
                    throw new IOException("visible or restored glyph differs from reference");
                if (!near(0xff000000, hidden.pixel(x, y), tolerance))
                    throw new IOException("glyph pixel remains visible");
                compared++;
            }
        }
        return compared;
    }

    private static boolean near(int expected, int actual, int tolerance) {
        for (int shift : new int[] {16, 8, 0}) {
            if (Math.abs(((expected >>> shift) & 255) - ((actual >>> shift) & 255)) > tolerance)
                return false;
        }
        return true;
    }
}
