package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded read-only raster for Voyemu PNG frames and lossless WebP references. */
public final class PixelImage {
    private final int width,height;
    private final int[] pixels;
    private PixelImage(int width,int height,int[] pixels) { this.width=width;this.height=height;this.pixels=pixels; }
    public int width(){return width;}
    public int height(){return height;}
    public int pixel(int x,int y){if(x<0||y<0||x>=width||y>=height)throw new IllegalArgumentException("pixel outside image");return pixels[y*width+x];}
    public boolean samePixels(PixelImage other) {
        return width == other.width && height == other.height && Arrays.equals(pixels, other.pixels);
    }

    private static long uint(byte[] bytes,int offset) {
        return ((long)(bytes[offset]&255)<<24)|((long)(bytes[offset+1]&255)<<16)|((long)(bytes[offset+2]&255)<<8)|(bytes[offset+3]&255);
    }
    private static void structure(byte[] bytes,int width,int height)throws IOException {
        if(bytes.length<33||!Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))throw new IOException("PNG signature differs");
        boolean header=false,data=false,ended=false,closedData=false;int offset=8;
        while(offset<bytes.length) {
            if(bytes.length-offset<12)throw new IOException("truncated PNG chunk");long length=uint(bytes,offset);
            if(length>8*1024*1024||offset+12L+length>bytes.length)throw new IOException("PNG chunk exceeds bound");
            int count=(int)length;String kind=new String(bytes,offset+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            if(!kind.matches("[A-Za-z]{4}"))throw new IOException("PNG chunk type differs");
            var crc=new java.util.zip.CRC32();crc.update(bytes,offset+4,count+4);
            if(crc.getValue()!=uint(bytes,offset+8+count))throw new IOException("PNG chunk checksum differs");
            if(!header&&!kind.equals("IHDR"))throw new IOException("PNG header must be first");
            if(kind.equals("IHDR")) {
                if(header||count!=13||uint(bytes,offset+8)!=width||uint(bytes,offset+12)!=height||bytes[offset+16]!=8
                        || bytes[offset+17]!=2&&bytes[offset+17]!=6||bytes[offset+18]!=0||bytes[offset+19]!=0||bytes[offset+20]!=0)throw new IOException("unsupported PNG raster header");header=true;
            } else if(kind.equals("IDAT")) {if(closedData)throw new IOException("noncontiguous PNG data");data=true;}
            else if(kind.equals("IEND")) {if(count!=0||!data||offset+12!=bytes.length)throw new IOException("PNG end or trailer differs");ended=true;}
            else {if((bytes[offset+4]&32)==0)throw new IOException("unsupported critical PNG chunk");if(data)closedData=true;}
            offset+=count+12;
        }
        if(!header||!data||!ended)throw new IOException("complete PNG raster required");
    }
    public static PixelImage read(Path file, int width, int height) throws IOException {
        return read(file, width, height, "PNG");
    }

    public static PixelImage readWebp(Path file, int width, int height) throws IOException {
        return read(file, width, height, "WebP");
    }

    private static void losslessWebp(byte[] bytes) throws IOException {
        if (bytes.length < 26) throw new IOException("truncated lossless WebP");
        ByteBuffer riff = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long chunkSize = Integer.toUnsignedLong(riff.getInt(16));
        if (uint(bytes, 0) != 0x52494646L || uint(bytes, 8) != 0x57454250L
                || uint(bytes, 12) != 0x5650384cL || riff.getInt(4) != bytes.length - 8
                || chunkSize < 5 || 20 + chunkSize + (chunkSize & 1) != bytes.length) {
            throw new IOException("single-frame lossless WebP (VP8L) required");
        }
    }

    private static PixelImage read(Path file, int expectedWidth, int expectedHeight, String format) throws IOException {
        if(expectedWidth<1||expectedHeight<1||expectedWidth>1920||expectedHeight>720||!file.isAbsolute()
                || !file.toRealPath().equals(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("bounded ordinary " + format + " path required");
        BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()<8||before.size()>8*1024*1024||before.fileKey()==null)throw new IOException("bounded stable " + format + " required");
        byte[] bytes;try(var raw=Files.newInputStream(file)){bytes=raw.readNBytes(8*1024*1024+1);}
        if (bytes.length > 8 * 1024 * 1024) throw new IOException(format + " grew beyond bound");
        if ("PNG".equals(format)) structure(bytes, expectedWidth, expectedHeight);
        else losslessWebp(bytes);
        try(var input=new MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException(format + " decoder unavailable");var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                if(!format.equalsIgnoreCase(reader.getFormatName())||reader.getWidth(0)!=expectedWidth||reader.getHeight(0)!=expectedHeight)throw new IOException(format + " dimensions differ from expected region");
                var image=reader.read(0);int[] pixels=image.getRGB(0,0,expectedWidth,expectedHeight,null,0,expectedWidth);
                BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!before.fileKey().equals(after.fileKey())||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!before.creationTime().equals(after.creationTime()))throw new IOException(format + " changed during read");
                return new PixelImage(expectedWidth,expectedHeight,pixels);
            } finally {reader.dispose();}
        }
    }
}
