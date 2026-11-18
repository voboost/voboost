package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Independent TTF raster criterion. The caller must retain and validate owned UI/capture provenance. */
public final class GlyphRasterOracle {
    private static final String FONT_SHA="797e35f7f5d6020a5c6ea13b42ecd668bcfb3bbc4baa0e74773527e5b6cb3174";
    public static final class Calibration {
        private final Font font;private final int radius;private final String reference;private final PixelImage checkbox;private final String checkboxReference;
        private Calibration(Font font,int radius,String reference,PixelImage checkbox,String checkboxReference){this.font=font;this.radius=radius;this.reference=reference;this.checkbox=checkbox;this.checkboxReference=checkboxReference;}
        public int edgeRadius(){return radius;}
    }
    private GlyphRasterOracle(){}
    private static IOException unavailable(String why){return new IOException("glyph raster unavailable: "+why);}
    private static boolean[][] mask(PixelImage image,int x,int y,int width,int height)throws IOException {
        if(width<1||height<1||width>1920||height>720||x<0||y<0||x+width>image.width()||y+height>image.height())throw unavailable("ROI bounds");
        boolean[][] out=new boolean[height][width];int ink=0;
        for(int yy=0;yy<height;yy++)for(int xx=0;xx<width;xx++){int c=image.pixel(x+xx,y+yy),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;if((c>>>24)!=255)throw unavailable("nonopaque ROI");out[yy][xx]=r>=128&&g>=128&&b>=128;if(out[yy][xx])ink++;}
        if(ink<8||ink>width*height/2)throw unavailable("missing glyph or unsupported non-dark ROI");return out;
    }
    private static int count(boolean[][] mask){int n=0;for(boolean[] row:mask)for(boolean v:row)if(v)n++;return n;}
    private static int remote(boolean[][] from,boolean[][] to,int radius){int bad=0;for(int y=0;y<from.length;y++)for(int x=0;x<from[0].length;x++)if(from[y][x]){boolean near=false;for(int yy=Math.max(0,y-radius);yy<=Math.min(to.length-1,y+radius)&&!near;yy++)for(int xx=Math.max(0,x-radius);xx<=Math.min(to[0].length-1,x+radius);xx++)if(to[yy][xx]){near=true;break;}if(!near)bad++;}return bad;}
    private static boolean[][] expected(Font font,String caption,int width,int height)throws IOException {
        if(caption==null||caption.isEmpty()||caption.length()>256||caption.codePoints().anyMatch(c->Character.isISOControl(c))||font.canDisplayUpTo(caption)!=-1)throw unavailable("caption or normative font has unsupported glyphs");
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        try {g.setColor(java.awt.Color.BLACK);g.fillRect(0,0,width,height);g.setColor(java.awt.Color.WHITE);g.setFont(font);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);FontMetrics metrics=g.getFontMetrics();float advance=(float)font.getStringBounds(caption,g.getFontRenderContext()).getWidth();if(advance>width-72)throw unavailable("caption cannot retain the hardware control text margins");float baseline=height/2f-(metrics.getDescent()-metrics.getAscent())/2f;g.drawString(caption,(width-advance)/2f,baseline);}finally{g.dispose();}
        boolean[][] result=new boolean[height][width];for(int y=0;y<height;y++)for(int x=0;x<width;x++)result[y][x]=(image.getRGB(x,y)&255)>=128;return result;
    }
    /** Calibrate only against immutable original hardware, never against a candidate capture. */
    public static Calibration calibrate(Path componentSources)throws IOException {
        try {
            if(!componentSources.isAbsolute()||!componentSources.equals(componentSources.toRealPath()))throw unavailable("canonical owning component sources required");
            Path fontPath=componentSources.resolve("src/main/java/ru/voboost/components/font/Font.ttf");if(!Files.isRegularFile(fontPath,LinkOption.NOFOLLOW_LINKS)||Files.size(fontPath)>4*1024*1024)throw unavailable("bounded normative font");byte[] bytes=Files.readAllBytes(fontPath);if(!FONT_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))throw unavailable("normative font identity differs");
            Font font=Font.createFont(Font.TRUETYPE_FONT,new java.io.ByteArrayInputStream(bytes)).deriveFont(28f);
            Path reference=componentSources.resolve("src/demo-pixel/java/ru/voboost/components/demo/pixel/MainActivity.screenshots/button-01-free-dark_1original.png");PixelImage hardware=PixelImage.read(reference,1920,720);
            int[][] rois={{515,84,250,70},{515,342,150,70}};String[] captions={"Factory reset","Check"};int radius=0;
            for(int i=0;i<rois.length;i++){int[] r=rois[i];boolean[][] actual=mask(hardware,r[0],r[1],r[2],r[3]),expected=expected(font,captions[i],r[2],r[3]);int needed=0;while(needed<=2&&(remote(actual,expected,needed)!=0||remote(expected,actual,needed)!=0))needed++;if(needed>2)throw unavailable("current host rasterizer fails immutable hardware calibration");radius=Math.max(radius,needed);}
            Path checkboxPath=reference.resolveSibling("checkbox-01-free-dark_1original.png");PixelImage checkbox=PixelImage.read(checkboxPath,1920,720);return new Calibration(font,radius,reference.toString(),checkbox,checkboxPath.toString());
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException("normative font calibration failed",error);}
    }
    /** Known-caption raster on a fully visible dark OEM button ROI. Never a whole-screen or publication PASS. */
    public static JsonObject button(PixelImage frame,int x,int y,int width,int height,String expectedCaption,Calibration calibration)throws IOException {
        if(calibration==null||height!=70||width<73||width>1800)throw unavailable("calibrated OEM button contract");
        boolean[][] actual=mask(frame,x,y,width,height),expected=expected(calibration.font,expectedCaption,width,height);int extra=remote(actual,expected,calibration.radius),missing=remote(expected,actual,calibration.radius);
        if(extra!=0||missing!=0)throw unavailable("glyph/font/spacing/clipping differs: extra="+extra+", missing="+missing);
        JsonObject proof=new JsonObject();proof.addProperty("criterion","hardware-calibrated-known-font-glyph-raster");proof.addProperty("rasterMatch",true);proof.addProperty("expectedCaption",expectedCaption);proof.addProperty("fontSha256",FONT_SHA);proof.addProperty("hardwareReference",calibration.reference);proof.addProperty("edgeRadiusPx",calibration.radius);proof.addProperty("actualInkPixels",count(actual));proof.addProperty("normativeInkPixels",count(expected));proof.addProperty("functionalAcceptance",false);proof.addProperty("publicationApproval",false);return proof;
    }
    /** Exact immutable OEM toggle ROI, no fuzzy mask and no language-dependent text. */
    public static JsonObject checkbox(PixelImage frame,int x,int y,boolean checked,Calibration calibration)throws IOException {
        if(calibration==null||x<0||y<0||x+83>frame.width()||y+42>frame.height())throw unavailable("calibrated fully visible checkbox ROI");
        int referenceY=checked?598:191,different=0;for(int yy=0;yy<42;yy++)for(int xx=0;xx<83;xx++)if(frame.pixel(x+xx,y+yy)!=calibration.checkbox.pixel(516+xx,referenceY+yy))different++;
        if(different!=0)throw unavailable("checkbox state/raster differs: pixels="+different);
        JsonObject proof=new JsonObject();proof.addProperty("criterion","immutable-hardware-checkbox-roi");proof.addProperty("rasterMatch",true);proof.addProperty("checked",checked);proof.addProperty("hardwareReference",calibration.checkboxReference);proof.addProperty("width",83);proof.addProperty("height",42);proof.addProperty("ignoredPixels",0);proof.addProperty("functionalAcceptance",false);proof.addProperty("publicationApproval",false);return proof;
    }

}
