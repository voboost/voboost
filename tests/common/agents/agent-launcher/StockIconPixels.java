package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.voboost.voyemu.PixelImage;
import ru.voboost.voyemu.UiEvidence;

/** Exact primary OEM icon retention on one independently verified visible page. */
public final class StockIconPixels {
    private static String text(JsonObject value,String key) { JsonElement e=value.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()?e.getAsString():""; }
    private static IOException bad(String reason) { return new IOException("stock icon pixels unavailable: "+reason); }
    private static List<Integer> icon(JsonObject ui,LauncherGrid.Item item)throws IOException {
        JsonObject surface=UiEvidence.surface(ui,"primary");
        if(!surface.has("complete")||!surface.get("complete").getAsBoolean()||!"ok".equals(text(surface,"status")))throw bad("incomplete primary hierarchy");
        JsonObject root=item.node(), selected=null;int roots=0;String rootId=text(root,"id"),window=text(root,"window_id");
        for(JsonElement element:surface.getAsJsonArray("nodes")) {
            JsonObject node=element.getAsJsonObject();
            if(rootId.equals(text(node,"id"))) { roots++; if(!root.equals(node))throw bad("grid item differs from hierarchy"); }
            if(!rootId.equals(text(node,"parent"))||!(LauncherPage.OWNER+":id/item_icon").equals(text(node,"resource_id")))continue;
            if(selected!=null||!LauncherPage.OWNER.equals(text(node,"package"))||!window.equals(text(node,"window_id"))||!"android.widget.ImageView".equals(text(node,"class"))||!node.has("visible")||!node.get("visible").getAsBoolean()||!node.has("clipped_bounds")||!node.get("bounds").equals(node.get("clipped_bounds")))throw bad("ambiguous, clipped or foreign icon");
            selected=node;
        }
        if(selected==null||roots!=1)throw bad("icon or verified item missing");List<Integer> box=LauncherGrid.rectangle(selected),outer=LauncherGrid.rectangle(root);
        if(box.get(0)<outer.get(0)||box.get(1)<outer.get(1)||box.get(2)>outer.get(2)||box.get(3)>outer.get(3))throw bad("icon outside labelled item");return box;
    }
    /** Inputs must be the grid and tree bracketed around their respective original capture. */
    public static void compare(JsonObject beforeTree,LauncherGrid before,PixelImage beforeImage,JsonObject afterTree,LauncherGrid after,PixelImage afterImage)throws IOException {
        try {
            if(beforeImage.width()!=1920||beforeImage.height()!=720||afterImage.width()!=1920||afterImage.height()!=720||before.items().isEmpty()||after.items().size()<before.items().size()||after.items().size()>before.items().size()+1)throw bad("image or page coverage");
            if(after.items().size()>before.items().size()&&!"Voboost Fixture".equals(after.items().get(after.items().size()-1).label()))throw bad("unexpected appended item");
            Set<String> labels=new HashSet<>();
            for(int i=0;i<before.items().size();i++) {
                var a=before.items().get(i);var b=after.items().get(i);
                if(a.position()!=b.position()||a.label().isBlank()||"Voboost Fixture".equalsIgnoreCase(a.label())||!labels.add(a.label())||!a.label().equals(b.label()))throw bad("label or order differs");
                List<Integer> r=icon(beforeTree,a);if(!r.equals(icon(afterTree,b)))throw bad("icon position differs");
                int first=beforeImage.pixel(r.get(0),r.get(1));boolean nonuniform=false;
                for(int y=r.get(1);y<r.get(3);y++)for(int x=r.get(0);x<r.get(2);x++) {
                    int pixel=beforeImage.pixel(x,y);if(pixel!=first)nonuniform=true;if(pixel!=afterImage.pixel(x,y))throw bad("stock icon pixels differ for "+a.label());
                }
                if(!nonuniform)throw bad("blank icon region");
            }
        }catch(IllegalStateException|NullPointerException|IllegalArgumentException malformed){throw new IOException("malformed stock icon observation",malformed);}
    }
    private StockIconPixels() {}
}
