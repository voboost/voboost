package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;

/** One finite left-edge opening trace. Only its first point grants input authority; no retry is implied. */
final class DrawerGesture {
    private DrawerGesture(){}
    static JsonArray points(JsonObject expected,int viewportWidth) {
        JsonObject target=expected.getAsJsonObject("target"),window=expected.getAsJsonObject("window"),bounds=target.getAsJsonObject("clipped_bounds"),w=window.getAsJsonObject("bounds");
        if(!VehicleDrawer.OWNER.equals(target.get("package").getAsString())||!(VehicleDrawer.OWNER+":id/line_view").equals(target.get("resource_id").getAsString())||!"android.view.View".equals(target.get("class").getAsString())||!VehicleDrawer.OWNER.equals(window.get("package").getAsString())||!"3".equals(window.get("type").getAsString())||w.get("x").getAsInt()!=0||w.get("height").getAsInt()<=w.get("width").getAsInt()||!target.get("bounds").equals(target.get("clipped_bounds")))throw new IllegalArgumentException("actual left vertical drawer receiver required");
        int x=bounds.get("x").getAsInt()+bounds.get("width").getAsInt()/2,y=bounds.get("y").getAsInt()+bounds.get("height").getAsInt()/2,end=viewportWidth/2;
        if(viewportWidth<2||viewportWidth>3840||x<0||end<=x||y<0||y>=2160)throw new IllegalArgumentException("observed drawer viewport/receiver geometry differs");
        JsonArray result=new JsonArray();for(int i=0;i<=10;i++){JsonObject point=new JsonObject();point.addProperty("x",x+(end-x)*i/10);point.addProperty("y",y);point.addProperty("at_ms",i*30);result.add(point);}return result;
    }
    static JsonArray closePoints(int start,int y,int end) {
        if(start-end<60||end<0||start>3840||y<0||y>=2160)throw new IllegalArgumentException("empty drawer region extent differs");JsonArray points=new JsonArray();
        for(int i=0;i<=10;i++){JsonObject p=new JsonObject();p.addProperty("x",start+(end-start)*i/10);p.addProperty("y",y);p.addProperty("at_ms",i*10);points.add(p);}return points;
    }
    private static Set<String> fields(JsonObject request)throws IOException {
        if(request.has("scope")&&(!request.get("scope").isJsonPrimitive()||!request.getAsJsonPrimitive("scope").isString()
                ||!"all_attached".equals(request.get("scope").getAsString())))throw new IOException("drawer observation scope differs");
        return request.has("scope")?Set.of("schema","request_id","op","profile","session","screen","scope","expected","points")
            :Set.of("schema","request_id","op","profile","session","screen","expected","points");
    }
    static void validateClose(JsonObject request)throws IOException {
        try {
            JsonObject target=request.getAsJsonObject("expected").getAsJsonObject("target"),window=request.getAsJsonObject("expected").getAsJsonObject("window");JsonArray points=request.getAsJsonArray("points");
            if(!request.keySet().equals(fields(request))||!"input.gesture".equals(request.get("op").getAsString())||!"primary".equals(request.get("screen").getAsString())||!VehicleDrawer.OWNER.equals(target.get("package").getAsString())||!target.get("resource_id").getAsString().isEmpty()||!"android.widget.FrameLayout".equals(target.get("class").getAsString())||!VehicleDrawer.OWNER.equals(window.get("package").getAsString())||!"3".equals(window.get("type").getAsString())||!target.get("bounds").equals(window.get("bounds"))||!target.get("bounds").equals(target.get("clipped_bounds"))||points.size()!=11)throw new IOException("closed drawer background request differs");
            int start=points.get(0).getAsJsonObject().get("x").getAsInt(),y=points.get(0).getAsJsonObject().get("y").getAsInt(),end=points.get(10).getAsJsonObject().get("x").getAsInt();JsonObject b=target.getAsJsonObject("bounds");
            if(end<b.get("x").getAsInt()||start>=b.get("x").getAsInt()+b.get("width").getAsInt()||y<b.get("y").getAsInt()||y>=b.get("y").getAsInt()+b.get("height").getAsInt()||!closePoints(start,y,end).equals(points))throw new IOException("finite in-background drawer close trace differs");
        }catch(RuntimeException malformed){throw new IOException("malformed drawer close trace",malformed);}
    }
    static void validate(JsonObject request)throws IOException {
        try {
            if(!request.keySet().equals(fields(request))||!"input.gesture".equals(request.get("op").getAsString())||!"primary".equals(request.get("screen").getAsString())||request.getAsJsonArray("points").size()!=11)throw new IOException("closed drawer request shape differs");
            int end=request.getAsJsonArray("points").get(10).getAsJsonObject().get("x").getAsInt();
            if(!points(request.getAsJsonObject("expected"),Math.multiplyExact(end,2)).equals(request.get("points")))throw new IOException("finite drawer trace differs");
        }catch(RuntimeException malformed){throw new IOException("malformed drawer trace",malformed);}
    }
}
