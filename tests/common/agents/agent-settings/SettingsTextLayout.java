package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Native TextView/Layout coordinates, never inferred from the control's outer rectangle. */
public final class SettingsTextLayout {
    public record Line(int start,int end,double left,double right,int top,int baseline,int bottom) {}
    public record Style(double size,double scale,double spacing,int fontStyle,boolean fakeBold,double skew,long color) {}
    private final Style style;
    private final List<Line> lines;
    private final int width,height,baseline,paddingLeft,paddingRight,paddingTop,paddingBottom,scrollX,scrollY;
    private final JsonObject source;
    private SettingsTextLayout(Style style,List<Line> lines,int width,int height,int baseline,int left,int right,int top,int bottom,int scrollX,int scrollY,JsonObject source){this.style=style;this.lines=List.copyOf(lines);this.width=width;this.height=height;this.baseline=baseline;paddingLeft=left;paddingRight=right;paddingTop=top;paddingBottom=bottom;this.scrollX=scrollX;this.scrollY=scrollY;this.source=source.deepCopy();}
    public Style style(){return style;}public List<Line> lines(){return lines;}public int width(){return width;}public int height(){return height;}public int paddingLeft(){return paddingLeft;}public int paddingRight(){return paddingRight;}public int paddingTop(){return paddingTop;}public int paddingBottom(){return paddingBottom;}
    public double lineX(Line line){return paddingLeft+line.left()-scrollX;}
    public double lineBaseline(Line line){return baseline-lines.get(0).baseline()+line.baseline()-scrollY;}
    public JsonObject source(){return source.deepCopy();}
    private static IOException invalid(String reason){return new IOException("actual text layout unavailable: "+reason);}
    private static JsonObject object(JsonObject o,String key)throws IOException{if(!o.has(key)||!o.get(key).isJsonObject())throw invalid(key);return o.getAsJsonObject(key);}
    private static double number(JsonObject o,String key)throws IOException{JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||Math.abs(v.getAsDouble())>0xffffffffL)throw invalid(key);return v.getAsDouble();}
    private static int integer(JsonObject o,String key)throws IOException{double n=number(o,key);if(n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw invalid(key);return(int)n;}
    private static boolean flag(JsonObject o,String key)throws IOException{JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw invalid(key);return v.getAsBoolean();}
    public static SettingsTextLayout read(JsonObject target,String caption)throws IOException{
        try{
            if(caption==null||caption.isEmpty()||caption.length()>4096||caption.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'))throw invalid("known source caption");
            String wire=caption.replace("\n","\\n").replace("\r","\\r");if(!target.has("actualText")||!target.get("actualText").isJsonPrimitive()||!target.getAsJsonPrimitive("actualText").isString()||!wire.equals(target.get("actualText").getAsString()))throw invalid("actual caption differs from owning source");
            JsonObject t=object(target,"textLayout"),style=object(target,"textStyle"),layout=object(target,"layout");
            if(!t.has("status")||!"available".equals(t.get("status").getAsString())||integer(t,"version")!=1||integer(t,"text_length")!=caption.length()||flag(t,"spanned"))throw invalid("complete plain native Layout required");
            if(!t.has("alignment")||!java.util.Set.of("ALIGN_NORMAL","ALIGN_CENTER","ALIGN_OPPOSITE").contains(t.get("alignment").getAsString())||integer(t,"gravity")<0||integer(t,"paint_flags")<0||integer(t,"break_strategy")<0||integer(t,"hyphenation")<0)throw invalid("native alignment/paint contract");flag(t,"include_font_padding");
            int count=integer(t,"line_count"),width=integer(t,"width"),height=integer(t,"height"),baseline=integer(t,"baseline"),left=integer(t,"padding_left"),right=integer(t,"padding_right"),top=integer(t,"padding_top"),bottom=integer(t,"padding_bottom");
            if(count<1||count>32||width<1||height<1||width>1920||height>720||left<0||right<0||top<0||bottom<0||width+left+right!=integer(layout,"measured_width")||baseline<top||baseline>integer(layout,"measured_height")-bottom||!t.has("lines")||!t.get("lines").isJsonArray()||t.getAsJsonArray("lines").size()!=count)throw invalid("native text viewport/line inventory");
            double size=number(style,"text_size_px"),scale=number(style,"text_scale_x"),spacing=number(style,"letter_spacing"),skew=number(t,"skew_x");int fontStyle=integer(t,"font_style");double rawColor=number(style,"current_text_color_argb");long color=(long)rawColor;
            if(size<=0||size>256||scale<=0||scale>8||Math.abs(spacing)>2||Math.abs(skew)>4||fontStyle<0||fontStyle>3||rawColor!=Math.rint(rawColor)||color<0||color>0xffffffffL||(color>>>24)!=255)throw invalid("bounded opaque native text style");
            List<Line> lines=new ArrayList<>();int next=0,lastBottom=0;
            for(JsonElement item:t.getAsJsonArray("lines")){JsonObject row=item.getAsJsonObject();int start=integer(row,"start"),end=integer(row,"end"),lineTop=integer(row,"top"),lineBaseline=integer(row,"baseline"),lineBottom=integer(row,"bottom"),direction=integer(row,"paragraph_direction");double lineLeft=number(row,"left"),lineRight=number(row,"right");
                if(start!=next||end<start||end>caption.length()||lineTop<lastBottom||lineBaseline<lineTop||lineBaseline>lineBottom||lineBottom<lineTop||lineLeft<0||lineRight<lineLeft||lineRight>width||integer(row,"ellipsis_count")!=0||integer(row,"ellipsis_start")!=0||direction!=1)throw invalid("clipped/truncated/overlapping or unsupported bidi line");
                if(start>0&&start<caption.length()&&Character.isLowSurrogate(caption.charAt(start))||end<caption.length()&&Character.isLowSurrogate(caption.charAt(end)))throw invalid("split surrogate glyph");
                lines.add(new Line(start,end,lineLeft,lineRight,lineTop,lineBaseline,lineBottom));next=end;lastBottom=lineBottom;
            }
            if(next!=caption.length()||lastBottom!=height)throw invalid("all owning caption characters must be covered");
            return new SettingsTextLayout(new Style(size,scale,spacing,fontStyle,flag(t,"fake_bold"),skew,color),lines,width,height,baseline,left,right,top,bottom,integer(layout,"scroll_x"),integer(layout,"scroll_y"),t);
        }catch(RuntimeException malformed){throw new IOException("malformed actual text layout",malformed);}
    }
}
