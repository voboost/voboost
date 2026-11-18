package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PixelImageTest {
    @TempDir Path directory;
    private Path png()throws IOException {
        BufferedImage image=new BufferedImage(4,3,BufferedImage.TYPE_INT_ARGB);image.setRGB(2,1,0xff2455a4);Path path=directory.resolve("source.png");assertTrue(ImageIO.write(image,"png",path.toFile()));return path;
    }
    @Test void exactBoundedRasterReadsWithoutChangingItsSource()throws Exception {
        Path path=png();byte[] before=Files.readAllBytes(path);var image=PixelImage.read(path,4,3);assertEquals(0xff2455a4,image.pixel(2,1));assertEquals(4,image.width());assertEquals(3,image.height());assertArrayEquals(before,Files.readAllBytes(path));assertThrows(IllegalArgumentException.class,()->image.pixel(4,0));
    }
    @Test void wrongDimensionsChecksumTruncationAndTrailerFailBeforeAdmission()throws Exception {
        Path source=png();assertThrows(IOException.class,()->PixelImage.read(source,3,4));byte[] bytes=Files.readAllBytes(source);
        byte[] corrupt=bytes.clone();corrupt[bytes.length-1]^=1;Path bad=Files.write(directory.resolve("crc.png"),corrupt);assertThrows(IOException.class,()->PixelImage.read(bad,4,3));
        Path shortFile=Files.write(directory.resolve("short.png"),java.util.Arrays.copyOf(bytes,bytes.length-1));assertThrows(IOException.class,()->PixelImage.read(shortFile,4,3));
        Path trailer=Files.write(directory.resolve("trailer.png"),java.util.Arrays.copyOf(bytes,bytes.length+1));assertThrows(IOException.class,()->PixelImage.read(trailer,4,3));
    }
    @Test void symlinkAndMissingSourceNeverBecomePixels()throws Exception {
        Path source=png(),link=directory.resolve("link.png");Files.createSymbolicLink(link,source);assertThrows(IOException.class,()->PixelImage.read(link,4,3));assertThrows(IOException.class,()->PixelImage.read(directory.resolve("missing.png"),4,3));
    }
}
