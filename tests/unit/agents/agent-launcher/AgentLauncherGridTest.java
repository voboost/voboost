package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AgentLauncherGridTest {
    private static JsonArray box(int left,int top,int right,int bottom) { JsonArray result=new JsonArray();for(int value:new int[]{left,top,right,bottom})result.add(value);return result; }
    private static JsonObject rect(JsonArray box) {
        JsonObject b=new JsonObject();b.addProperty("x",box.get(0).getAsInt());b.addProperty("y",box.get(1).getAsInt());
        b.addProperty("width",box.get(2).getAsInt()-box.get(0).getAsInt());b.addProperty("height",box.get(3).getAsInt()-box.get(1).getAsInt());return b;
    }
    private static JsonObject nativeReceipt() throws Exception {
        JsonObject value=AgentLauncherPageTest.receipt(), page=value.getAsJsonObject("observation");
        page.getAsJsonObject("window").add("bounds",box(0,0,1920,720));page.getAsJsonObject("grid").add("bounds",box(100,100,1800,650));
        for(int index=0;index<2;index++)page.getAsJsonArray("items").get(index).getAsJsonObject().add("bounds",box(200+index*200,200,360+index*200,300));return value;
    }
    private static JsonObject ui(JsonObject receipt) {
        JsonObject result=new JsonObject();result.add("boot_id",receipt.get("bootId"));JsonObject surface=new JsonObject();surface.addProperty("screen","primary");surface.addProperty("status","ok");surface.addProperty("complete",true);
        JsonObject window=new JsonObject();window.addProperty("id","public-window");window.addProperty("package",LauncherPage.OWNER);window.addProperty("type",1);window.addProperty("active",true);window.addProperty("input_focused",true);window.addProperty("read_complete",true);
        window.add("bounds",rect(receipt.getAsJsonObject("observation").getAsJsonObject("window").getAsJsonArray("bounds")));JsonArray windows=new JsonArray();windows.add(window);surface.add("windows",windows);
        JsonArray nodes=new JsonArray();JsonObject grid=node("grid",null,"androidx.recyclerview.widget.RecyclerView",LauncherPage.OWNER+":id/all_app_view","",rect(receipt.getAsJsonObject("observation").getAsJsonObject("grid").getAsJsonArray("bounds")));nodes.add(grid);
        for(int index=0;index<2;index++) {
            JsonObject actual=receipt.getAsJsonObject("observation").getAsJsonArray("items").get(index).getAsJsonObject();JsonObject bounds=rect(actual.getAsJsonArray("bounds"));
            nodes.add(node("icon-"+index,"grid","android.widget.FrameLayout",LauncherPage.OWNER+":id/item","",bounds));
            nodes.add(node("label-"+index,"icon-"+index,"android.widget.TextView",LauncherPage.OWNER+":id/item_name",actual.get("label").getAsString(),bounds));
        }
        surface.add("nodes",nodes);JsonArray screens=new JsonArray();screens.add(surface);result.add("screens",screens);return result;
    }
    private static JsonObject node(String id,String parent,String klass,String resource,String label,JsonObject bounds) {
        JsonObject node=new JsonObject();node.addProperty("id",id);if(parent==null)node.add("parent",com.google.gson.JsonNull.INSTANCE);else node.addProperty("parent",parent);
        node.addProperty("window_id","public-window");node.addProperty("package",LauncherPage.OWNER);node.addProperty("class",klass);node.addProperty("resource_id",resource);node.addProperty("text",label);
        node.addProperty("visible",true);node.addProperty("enabled",true);node.addProperty("clickable",!id.startsWith("label"));node.add("bounds",bounds);node.add("clipped_bounds",bounds.deepCopy());return node;
    }
    private static LauncherPage page(JsonObject receipt)throws IOException{return LauncherPage.read(receipt.toString(),"a".repeat(64),AgentLauncherPageTest.identity());}
    @Test void nativeOrderRequiresActualIndependentLabelsParentsAndBounds() throws Exception {
        JsonObject nativeValue=nativeReceipt();LauncherGrid result=LauncherGrid.read(ui(nativeValue),page(nativeValue));
        assertEquals("Voboost Fixture",result.items().get(1).label());assertEquals("icon-1",result.items().get(1).node().get("id").getAsString());
        JsonObject displayLocal=ui(nativeValue);displayLocal.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("input_focused",false);LauncherGrid.read(displayLocal,page(nativeValue));
        result.items().get(1).node().remove("bounds");assertTrue(result.items().get(1).node().has("bounds"));
    }
    @Test void wrongLabelAncestryGeometryWindowAndPartialCoverageNeverAuthorizeMovement() throws Exception {
        for(String fault:new String[]{"label","parent","bounds","window","partial","duplicate"}) {
            JsonObject nativeValue=nativeReceipt(), actual=ui(nativeValue),surface=actual.getAsJsonArray("screens").get(0).getAsJsonObject();JsonArray nodes=surface.getAsJsonArray("nodes");
            switch(fault) {
                case "label" -> nodes.get(4).getAsJsonObject().addProperty("text","foreign");
                case "parent" -> nodes.get(4).getAsJsonObject().addProperty("parent","icon-0");
                case "bounds" -> nodes.get(3).getAsJsonObject().getAsJsonObject("bounds").addProperty("x",401);
                case "window" -> { JsonObject foreign=surface.getAsJsonArray("windows").get(0).deepCopy().getAsJsonObject();foreign.addProperty("id","foreign");foreign.addProperty("package","foreign.app");surface.getAsJsonArray("windows").add(foreign); }
                case "partial" -> surface.addProperty("complete",false);
                case "duplicate" -> nodes.add(nodes.get(3).deepCopy());
            }
            assertThrows(IOException.class,()->LauncherGrid.read(actual,page(nativeValue)),fault);
        }
    }
}
