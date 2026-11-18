package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SettingsTextLayoutTest {
    static JsonObject target(String text) {
        var t=SettingsOemMeasurementsTest.object("kind","text","actualText",text,"layout",SettingsOemMeasurementsTest.object(
            "measured_width",250,"measured_height",70,"scroll_x",0,"scroll_y",0),"textStyle",SettingsOemMeasurementsTest.object(
            "text_size_px",28,"text_scale_x",1,"letter_spacing",0,"current_text_color_argb",4294967295L));
        var nativeLayout=SettingsOemMeasurementsTest.object("status","available","version",1,"text_length",text.length(),"spanned",false,
            "width",250,"height",42,"baseline",44,"padding_left",0,"padding_right",0,"padding_top",0,"padding_bottom",0,
            "font_style",0,"fake_bold",false,"skew_x",0,"line_count",1,"alignment","ALIGN_NORMAL","gravity",8388659,"include_font_padding",true,"paint_flags",129,"break_strategy",1,"hyphenation",1);
        var lines=new com.google.gson.JsonArray();
        lines.add(SettingsOemMeasurementsTest.object("start",0,"end",text.length(),"left",10,"right",178,"top",0,"baseline",30,"bottom",42,
            "ellipsis_start",0,"ellipsis_count",0,"paragraph_direction",1));nativeLayout.add("lines",lines);t.add("textLayout",nativeLayout);return t;
    }
    @Test void nativeOriginUsesActualBaselineAndCompoundPaddingRatherThanOuterCenter()throws Exception {
        var target=target("恢复出厂设置");var actual=SettingsTextLayout.read(target,"恢复出厂设置");
        assertEquals(10,actual.lineX(actual.lines().get(0)));assertEquals(44,actual.lineBaseline(actual.lines().get(0)));
        target.getAsJsonObject("layout").addProperty("scroll_y",3);
        assertEquals(41,SettingsTextLayout.read(target,"恢复出厂设置").lineBaseline(actual.lines().get(0)));
    }
    @Test void missingLiveGettersResourceCaptionGuessAndMalformedStyleCannotBecomeEvidence() {
        for(String mutation:new String[]{"missing","caption","fractionalColor","transparent","span","truncated","ellipsis","direction","width","nan"}) {
            var target=target("中文");var layout=target.getAsJsonObject("textLayout");
            switch(mutation){case"missing"->target.remove("textLayout");case"caption"->target.addProperty("actualText","别的");
                case"fractionalColor"->target.getAsJsonObject("textStyle").addProperty("current_text_color_argb",4294967294.5);
                case"transparent"->target.getAsJsonObject("textStyle").addProperty("current_text_color_argb",16777215);
                case"span"->layout.addProperty("spanned",true);case"truncated"->layout.addProperty("text_length",1);
                case"ellipsis"->layout.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("ellipsis_count",1);
                case"direction"->layout.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("paragraph_direction",-1);
                case"width"->layout.addProperty("width",249);case"nan"->layout.addProperty("skew_x",Double.NaN);}
            assertThrows(IOException.class,()->SettingsTextLayout.read(target,"中文"),mutation);
        }
    }
    @Test void everyNativeLineMustCoverOwningCaptionWithoutOverlapSurrogateSplitOrClipping() {
        for(String mutation:new String[]{"end","start","right","baseline","bottom","surrogate"}) {
            String text=mutation.equals("surrogate")?"A\ud840\udc00":"中文";var target=target(text);
            var line=target.getAsJsonObject("textLayout").getAsJsonArray("lines").get(0).getAsJsonObject();
            switch(mutation){case"end"->line.addProperty("end",1);case"start"->line.addProperty("start",1);
                case"right"->line.addProperty("right",251);case"baseline"->line.addProperty("baseline",43);
                case"bottom"->line.addProperty("bottom",41);case"surrogate"->line.addProperty("end",2);}
            assertThrows(IOException.class,()->SettingsTextLayout.read(target,text),mutation);
        }
    }
}
