package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;

/** Exactly one observed-grid horizontal page gesture. The native page receipt must confirm its effect. */
final class LauncherPageSwipe {
    private LauncherPageSwipe() {}
    static JsonArray points(JsonObject expected,boolean next) {
        JsonObject target=expected.getAsJsonObject("target"),window=expected.getAsJsonObject("window"),b=target.getAsJsonObject("clipped_bounds");
        if(!"com.qinggan.app.launcher".equals(target.get("package").getAsString()) || !"com.qinggan.app.launcher".equals(window.get("package").getAsString())
                || !"com.qinggan.app.launcher:id/all_app_view".equals(target.get("resource_id").getAsString())
                || !Set.of("com.qinggan.launcher.base.allapp.AllAppBarView","androidx.recyclerview.widget.RecyclerView").contains(target.get("class").getAsString())
                || !"1".equals(window.get("type").getAsString()) || !target.get("bounds").equals(b)) throw new IllegalArgumentException("observed primary Launcher grid required");
        for(String field:Set.of("x","y","width","height")) if(!b.has(field) || !b.get(field).isJsonPrimitive() || !b.getAsJsonPrimitive(field).isNumber() || !b.get(field).getAsString().matches("[0-9]{1,4}")) throw new IllegalArgumentException("integer Launcher grid bounds required");
        int x=b.get("x").getAsInt(),y=b.get("y").getAsInt(),w=b.get("width").getAsInt(),h=b.get("height").getAsInt();
        if(w<100 || h<100 || x+w>1920 || y+h>720) throw new IllegalArgumentException("Launcher grid bounds outside supported display");
        JsonArray points=new JsonArray();
        for(int index=0;index<=10;index++) {
            JsonObject point=new JsonObject();point.addProperty("x",x+w*(next?85-7*index:15+7*index)/100);point.addProperty("y",y+h/2);point.addProperty("at_ms",index*40);points.add(point);
        }
        return points;
    }
    static void validate(JsonObject request) {
        if(!request.keySet().equals(Set.of("schema","request_id","op","profile","session","screen","scope","expected","points"))
                || !"input.gesture".equals(request.get("op").getAsString()) || !"primary".equals(request.get("screen").getAsString()) || !"all_attached".equals(request.get("scope").getAsString())
                || !request.getAsJsonObject("expected").keySet().equals(Set.of("boot_id","source_instance","revision","window","target"))) throw new IllegalArgumentException("closed Launcher page gesture required");
        if(!points(request.getAsJsonObject("expected"),true).equals(request.get("points")) && !points(request.getAsJsonObject("expected"),false).equals(request.get("points"))) throw new IllegalArgumentException("finite Launcher page trace differs");
    }
}
