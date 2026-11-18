package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Independent font raster calibrated only on reviewed, source-owned, original stock OEM frames. */
public final class StockFontReference {
    private record Model(String id, String family, SettingsTextLayout.Style style, List<Font> fonts, String alignment, int gravity, int radius,
                         double advanceTolerance, String sample) {}
    private final Path registry;
    private final String profile;
    private final Map<String, Model> models;

    private StockFontReference(Path registry, String profile, Map<String, Model> models) {
        this.registry = registry; this.profile = profile; this.models = Map.copyOf(models);
    }
    public Path evidence() { return registry; }
    public String profile() { return profile; }
    private static IOException invalid(String why) { return new IOException("stock font reference unavailable: " + why); }
    private static String text(JsonObject value, String key) throws IOException {
        JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                || item.getAsString().isBlank()) throw invalid(key);
        return item.getAsString();
    }
    private static int integer(JsonObject value, String key, int min, int max) throws IOException {
        JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) throw invalid(key);
        double n = item.getAsDouble();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < min || n > max) throw invalid(key);
        return (int)n;
    }
    private static Path relative(Path root, String value) throws IOException {
        Path path = Path.of(value);
        if (path.isAbsolute() || value.contains("\\") || !path.normalize().equals(path)
                || value.equals(".") || path.startsWith("..")) throw invalid("nonportable reference path");
        Path resolved = root.resolve(path);
        if (!resolved.equals(resolved.toRealPath()) || !resolved.startsWith(root)) throw invalid("reference symlink/escape");
        return resolved;
    }
    private static JsonObject json(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 16 * 1024 * 1024)
            throw invalid("bounded ordinary JSON reference");
        return RunFiles.read(path);
    }
    private static List<Font> fonts(JsonArray rows, Map<String, Path> inputs, Map<String, Font> cache) throws Exception {
        if (rows == null || rows.isEmpty() || rows.size() > 4) throw invalid("font inventory");
        List<Font> result = new ArrayList<>(); Set<String> used = new HashSet<>();
        for (JsonElement item : rows) {
            JsonObject row = item.getAsJsonObject(); String id = text(row, "input");
            if (!used.add(id)) throw invalid("duplicate font input");
            Path path = inputs.get(id);
            if (path == null || !path.isAbsolute() || !path.equals(path.toRealPath())
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 32 * 1024 * 1024)
                throw invalid("canonical existing font input " + id);
            String cacheKey=path+"\n"+row.toString();
            Font cached=cache.get(cacheKey);if(cached!=null){result.add(cached);continue;}
            byte[] bytes = Files.readAllBytes(path);
            if (!text(row, "sha256").equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw invalid("font file identity differs");
            Font[] collection = Font.createFonts(new ByteArrayInputStream(bytes));
            int face = integer(row, "faceIndex", 0, collection.length - 1);
            Font font = collection[face];
            if (!font.getPSName().equals(text(row, "postscriptName"))) throw invalid("font face identity differs");
            cache.put(cacheKey,font);result.add(font);
        }
        return List.copyOf(result);
    }
    private static StockSettingsBaseline.Authority authority(Path path) throws IOException {
        JsonObject row = json(path);
        var result = new StockSettingsBaseline.Authority(text(row,"profile"), text(row,"sessionId"),
            text(row,"bootId"), integer(row,"androidPid",1,Integer.MAX_VALUE), text(row,"quickbootMode"),
            row.getAsJsonObject("status"), row.getAsJsonObject("rootAbsence"), path);
        StockSettingsBaseline.authority(result); return result;
    }
    /** The registry is an ordinary reviewed test golden in the owning repository, never candidate output. */
    public static StockFontReference load(Path productSources, String referenceId, String profile,
                                          Map<String, Path> fontInputs) throws IOException {
        try {
            if (!productSources.isAbsolute() || !productSources.equals(productSources.toRealPath())
                    || referenceId == null || !referenceId.matches("[a-z0-9][a-z0-9-]{0,95}"))
                throw invalid("canonical source-owned registry");
            Path directory = relative(productSources, "tests/emulator/agents/agent-settings/reference/" + referenceId);
            Path registry = relative(directory, "reference.json"); JsonObject spec = json(registry);
            if (integer(spec,"schema",1,1) != 1 || !"emulator-stock-oem-golden".equals(text(spec,"kind"))
                    || !"root".equals(text(spec,"reviewedBy")) || !referenceId.equals(text(spec,"referenceId")))
                throw invalid("reviewed owning stock registry required");
            JsonArray profiles = spec.getAsJsonArray("profiles");
            if (profiles == null || profiles.isEmpty() || profiles.size() > 16
                    || profiles.asList().stream().noneMatch(v -> v.isJsonPrimitive()
                        && v.getAsJsonPrimitive().isString() && profile.equals(v.getAsString())))
                throw invalid("reference is not admitted for this profile");
            JsonArray samples = spec.getAsJsonArray("samples");
            if (samples == null || samples.isEmpty() || samples.size() > 64) throw invalid("reviewed calibration inventory");
            Map<String,Model> models = new HashMap<>();Map<String,Font> fontCache=new HashMap<>();
            for (JsonElement element : samples) {
                JsonObject sample = element.getAsJsonObject(); String id = text(sample,"model");
                if (!id.matches("[a-z0-9][a-z0-9-]{0,95}") || models.containsKey(id)) throw invalid("unique model identity");
                Path captureDirectory = relative(directory,text(sample,"captureDirectory"));
                var beforeAuthority = authority(relative(directory,text(sample,"authorityBefore")));
                var afterAuthority = authority(relative(directory,text(sample,"authorityAfter")));
                if (!beforeAuthority.profile().equals(afterAuthority.profile())
                        || profiles.asList().stream().noneMatch(v -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && beforeAuthority.profile().equals(v.getAsString()))
                        || !beforeAuthority.session().equals(afterAuthority.session())
                        || !beforeAuthority.boot().equals(afterAuthority.boot())
                        || beforeAuthority.androidPid()!=afterAuthority.androidPid()) throw invalid("stock incarnation differs");
                String caption = text(sample,"caption"), resource = text(sample,"resourceId"), family=text(sample,"family");
                if(!Set.of("button","radio","heading","body","dialog-action","dialog-message").contains(family))throw invalid("reviewed control family");
                JsonObject before = StockSettingsBaseline.measureReference(json(relative(directory,text(sample,"uiBefore"))), beforeAuthority,resource,caption);
                JsonObject after = StockSettingsBaseline.measureReference(json(relative(directory,text(sample,"uiAfter"))), afterAuthority,resource,caption);
                JsonObject manifest = json(captureDirectory.resolve("manifest.json"));
                manifest.addProperty("artifact_dir",captureDirectory.toString());
                manifest.addProperty("manifest",captureDirectory.resolve("manifest.json").toString());
                var frame = SettingsRasterCapture.readStockArchive(manifest,captureDirectory,before,after,"stock.reference",caption);
                JsonObject target = frame.scope().getAsJsonObject("target");
                SettingsTextLayout actual = SettingsTextLayout.read(target,caption);
                List<Font> fontSet = fonts(sample.getAsJsonArray("fonts"),fontInputs,fontCache);
                boolean[][] normative = raster(fontSet,caption,actual,frame.width(),frame.height());
                boolean[][] pixels = mask(frame,actual);
                int radius = 0;
                while (radius <= 2 && (remote(normative,pixels,radius)!=0 || remote(pixels,normative,radius)!=0)) radius++;
                if (radius>2) throw invalid("independent font raster disagrees with reviewed stock " + id);
                double advance = advanceError(fontSet,caption,actual);
                if (advance>2) throw invalid("native stock advances disagree with independent font " + id);
                models.put(id,new Model(id,family,actual.style(),fontSet,text(actual.source(),"alignment"),integer(actual.source(),"gravity",0,Integer.MAX_VALUE),radius,Math.max(0.5,advance+0.25),text(sample,"captureDirectory")));
            }
            return new StockFontReference(registry,profile,models);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("malformed source-owned stock reference",error); }
    }
    private static Font styled(Font font,SettingsTextLayout.Style style) throws IOException {
        if (style.fakeBold() || style.fontStyle()!=Font.PLAIN) throw invalid("unmodelled native fake-bold/style");
        Font sized = font.deriveFont((float)style.size());
        if (style.scale()!=1 || style.skew()!=0)
            sized = sized.deriveFont(new AffineTransform(style.scale(),0,-style.skew(),1,0,0));
        return sized;
    }
    private static TextLayout line(List<Font> fonts,String value,SettingsTextLayout.Style style,
                                   java.awt.font.FontRenderContext context) throws IOException {
        if (value.isEmpty()) return null;
        AttributedString attributed = new AttributedString(value);
        attributed.addAttribute(TextAttribute.KERNING,TextAttribute.KERNING_ON);
        attributed.addAttribute(TextAttribute.LIGATURES,TextAttribute.LIGATURES_ON);
        attributed.addAttribute(TextAttribute.TRACKING,(float)style.spacing());
        for (int at=0;at<value.length();) {
            int code=value.codePointAt(at),end=at+Character.charCount(code); Font selected=null;
            for (Font font:fonts) if (font.canDisplay(code)) { selected=styled(font,style);break; }
            if (selected==null) throw invalid("owning normative font lacks U+"+Integer.toHexString(code));
            attributed.addAttribute(TextAttribute.FONT,selected,at,end); at=end;
        }
        return new TextLayout(attributed.getIterator(),context);
    }
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics=image.createGraphics();
        graphics.setColor(Color.BLACK);graphics.fillRect(0,0,image.getWidth(),image.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return graphics;
    }
    private static String lineText(String caption,SettingsTextLayout.Line line) {
        return caption.substring(line.start(),line.end()).stripTrailing();
    }
    static boolean[][] raster(List<Font> fonts,String caption,SettingsTextLayout actual,int width,int height) throws IOException {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=graphics(image);
        try {
            for(var nativeLine:actual.lines()) {
                TextLayout line=line(fonts,lineText(caption,nativeLine),actual.style(),graphics.getFontRenderContext());
                if(line==null)continue;
                var ink=line.getBounds();double x=actual.lineX(nativeLine),baseline=actual.lineBaseline(nativeLine);
                if(x+ink.getMinX()<actual.paddingLeft()-0.5 || x+ink.getMaxX()>width-actual.paddingRight()+0.5
                        || baseline+ink.getMinY()<actual.paddingTop()-0.5
                        || baseline+ink.getMaxY()>height-actual.paddingBottom()+0.5)
                    throw invalid("normative glyph is clipped by actual content viewport");
                line.draw(graphics,(float)x,(float)baseline);
            }
        } finally {graphics.dispose();}
        boolean[][] result=new boolean[height][width];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)result[y][x]=(image.getRGB(x,y)&255)>=128;
        if(count(result)<8)throw invalid("empty normative raster"); return result;
    }
    private static double advanceError(List<Font> fonts,String caption,SettingsTextLayout actual) throws IOException {
        BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=graphics(image);double worst=0;
        try {for(var nativeLine:actual.lines()) {
            TextLayout layout=line(fonts,lineText(caption,nativeLine),actual.style(),graphics.getFontRenderContext());
            if(layout!=null)worst=Math.max(worst,Math.abs(layout.getAdvance()-(nativeLine.right()-nativeLine.left())));
        }}finally{graphics.dispose();}return worst;
    }
    private static boolean[][] mask(SettingsRasterCapture.Frame frame,SettingsTextLayout layout) throws IOException {
        boolean[][] result=new boolean[frame.height()][frame.width()];
        long color=layout.style().color();int r=(int)(color>>16)&255,g=(int)(color>>8)&255,b=(int)color&255;
        if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>8 || r<64)throw invalid("uncalibrated colored/dark glyph paint");
        double threshold=(r+g+b)/6.0;
        for(int y=layout.paddingTop();y<frame.height()-layout.paddingBottom();y++)
            for(int x=layout.paddingLeft();x<frame.width()-layout.paddingRight();x++) {
                int pixel=frame.image().pixel(frame.x()+x,frame.y()+y);
                if((pixel>>>24)!=255)throw invalid("nonopaque actual glyph ROI");
                result[y][x]=(((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255))/3.0>=threshold;
            }
        if(count(result)<8)throw invalid("actual glyph missing");return result;
    }
    private static int count(boolean[][] mask) {int n=0;for(boolean[] row:mask)for(boolean value:row)if(value)n++;return n;}
    private static int remote(boolean[][] from,boolean[][] to,int radius) {
        int bad=0;
        for(int y=0;y<from.length;y++)for(int x=0;x<from[y].length;x++)if(from[y][x]) {
            boolean found=false;
            for(int yy=Math.max(0,y-radius);yy<=Math.min(to.length-1,y+radius)&&!found;yy++)
                for(int xx=Math.max(0,x-radius);xx<=Math.min(to[yy].length-1,x+radius);xx++)
                    if(to[yy][xx]){found=true;break;}
            if(!found)bad++;
        }
        return bad;
    }
    /** Known owning copy and a selected calibrated font model; never calibration from this candidate. */
    public JsonObject verify(String modelId,SettingsRasterCapture.Frame frame,String expectedCaption) throws IOException {
        return verify(modelId,"button",frame,expectedCaption);
    }
    public JsonObject verify(String modelId,String owningFamily,SettingsRasterCapture.Frame frame,String expectedCaption) throws IOException {
        Model model=models.get(modelId);if(model==null)throw invalid("no reviewed font model "+modelId);
        if(!model.family().equals(owningFamily))throw invalid("font model belongs to another owning control family");
        if(!profile.equals(text(frame.scope(),"profile")))throw invalid("candidate reference profile differs");
        SettingsTextLayout actual=SettingsTextLayout.read(frame.scope().getAsJsonObject("target"),expectedCaption);
        if(!model.style().equals(actual.style())||!model.alignment().equals(text(actual.source(),"alignment"))||model.gravity()!=integer(actual.source(),"gravity",0,Integer.MAX_VALUE))throw invalid("native style/alignment differs from reviewed model");
        boolean[][] normative=raster(model.fonts(),expectedCaption,actual,frame.width(),frame.height());
        boolean[][] pixels=mask(frame,actual);
        double advance=advanceError(model.fonts(),expectedCaption,actual);
        int extra=remote(pixels,normative,model.radius()),missing=remote(normative,pixels,model.radius());
        if(advance>model.advanceTolerance()||extra!=0||missing!=0)
            throw invalid("wrong glyph/font/spacing/clipping: extra="+extra+", missing="+missing+", advance="+advance);
        JsonObject proof=new JsonObject();proof.addProperty("criterion","reviewed-emulator-stock-calibrated-font-raster");
        proof.addProperty("reference",registry.toString());proof.addProperty("model",modelId);
        proof.addProperty("family",model.family());proof.addProperty("sample",model.sample());proof.addProperty("edgeRadiusPx",model.radius());
        proof.addProperty("rasterMatch",true);proof.addProperty("advanceErrorPx",advance);
        proof.addProperty("hardwareFidelityAccepted",false);proof.addProperty("publicationApproval",false);
        return proof;
    }
}
