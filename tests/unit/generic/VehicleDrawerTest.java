package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import voyemu.journal.Journal;

class VehicleDrawerTest {
    @TempDir Path directory;
    private static final VoyemuClient.Session SESSION=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));
    private static JsonObject bounds(int x,int y,int width,int height){JsonObject b=new JsonObject();b.addProperty("x",x);b.addProperty("y",y);b.addProperty("width",width);b.addProperty("height",height);return b;}
    private static JsonObject node(String id,String parent,String resource,String kind,int x,int y,int width,int height,boolean clickable) {
        JsonObject n=new JsonObject();n.addProperty("id",id);if(parent==null)n.add("parent",com.google.gson.JsonNull.INSTANCE);else n.addProperty("parent",parent);n.addProperty("window_id","drawer");n.addProperty("package",VehicleDrawer.OWNER);n.addProperty("resource_id",resource.isEmpty()?"":VehicleDrawer.OWNER+":id/"+resource);n.addProperty("class",kind);n.addProperty("visible",true);n.addProperty("enabled",true);n.addProperty("clickable",clickable);n.addProperty("actionable_candidate",clickable);n.addProperty("text","");n.add("bounds",bounds(x,y,width,height));n.add("clipped_bounds",bounds(x,y,width,height));return n;
    }
    static JsonObject tree()throws IOException {
        JsonObject t=RunFiles.parse("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\""+SESSION.id()+"\",\"boot_id\":\"boot\",\"source_instance\":\"source\",\"revision\":\"1\"}");
        JsonObject s=new JsonObject();s.addProperty("screen","primary");s.addProperty("status","ok");s.addProperty("complete",true);s.addProperty("width",1920);s.addProperty("height",720);
        JsonObject w=RunFiles.parse("{\"id\":\"drawer\",\"package\":\"com.qinggan.app.launcher\",\"type\":3,\"title\":\"Drawer\",\"layer\":3,\"active\":true,\"input_focused\":true,\"read_complete\":true}");w.add("bounds",bounds(0,0,800,720));JsonArray windows=new JsonArray();windows.add(w);JsonObject edge=w.deepCopy();edge.addProperty("id","edge");edge.addProperty("active",false);edge.addProperty("input_focused",false);edge.add("bounds",bounds(0,0,30,720));windows.add(edge);s.add("windows",windows);
        JsonArray nodes=new JsonArray();nodes.add(node("root",null,"","android.widget.FrameLayout",0,0,800,720,false));nodes.add(node("content","root","screen_up_vehicle_hiboard_new","android.widget.RelativeLayout",0,0,800,720,false));nodes.add(node("background","content","view_screen_up_vehicle_hiboard_bg","android.widget.ImageView",0,0,800,720,false));JsonObject edit=node("edit","content","screen_up_edit_btn","android.widget.Button",20,20,100,40,true);edit.addProperty("text","Edit");nodes.add(edit);nodes.add(node("card","content","rl_root","android.widget.RelativeLayout",50,100,650,200,true));JsonObject title=node("title","card","tv_title","android.widget.TextView",60,110,100,40,false);title.addProperty("text","Electric");nodes.add(title);JsonObject control=node("force","card","force_ev_switcher","android.widget.ImageView",650,160,40,40,true);control.addProperty("selected",false);nodes.add(control);JsonObject handle=node("handle",null,"line_view","android.view.View",0,0,30,720,false);handle.addProperty("window_id","edge");nodes.add(handle);s.add("nodes",nodes);JsonArray screens=new JsonArray();screens.add(s);t.add("screens",screens);return t;
    }
    private static JsonObject surface(JsonObject tree){return tree.getAsJsonArray("screens").get(0).getAsJsonObject();}
    private static JsonObject node(JsonObject tree,int index){return surface(tree).getAsJsonArray("nodes").get(index).getAsJsonObject();}
    @Test void geometryIgnoresObservationLocalNodeIdentitiesButDetectsMotion() throws Exception {
        var first=tree();var next=tree();
        for(var item:surface(next).getAsJsonArray("nodes")) {
            var node=item.getAsJsonObject();
            node.addProperty("id","next-"+node.get("id").getAsString());
            if(node.has("parent")&&!node.get("parent").isJsonNull())
                node.addProperty("parent","next-"+node.get("parent").getAsString());
        }
        var prior=VehicleDrawer.read(surface(first));var current=VehicleDrawer.read(surface(next));
        assertNotEquals(prior.cardId(),current.cardId());
        assertEquals(prior.geometry(),current.geometry());
        node(next,4).getAsJsonObject("bounds").addProperty("x",51);
        node(next,4).getAsJsonObject("clipped_bounds").addProperty("x",51);
        assertNotEquals(prior.geometry(),VehicleDrawer.read(surface(next)).geometry());
    }

    @Test void savedAccessibilityReceiverIsAcceptedWithoutViewServerFields() throws Exception {
        var surface=RunFiles.read(Path.of("tests/unit/generic/generic-vehicle-drawer-accessibility.json"));
        assertEquals("2",VehicleDrawer.handle(surface));
        surface.addProperty("complete",false);
        assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.handle(surface));
    }

    @Test void accessibilityCompletenessUsesItsOwnCoverageContract() throws Exception {
        var tree=tree();var surface=surface(tree);
        for(var item:surface.getAsJsonArray("windows"))item.getAsJsonObject().remove("read_complete");
        assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.handle(surface));
        surface.addProperty("coverage_basis","accessibility_application_root");
        surface.addProperty("scope","api28_default_display");
        surface.add("unavailable_windows",new JsonArray());
        assertNotNull(VehicleDrawer.handle(surface));
        assertNotNull(VehicleDrawer.read(surface));
        surface.getAsJsonArray("windows").get(1).getAsJsonObject().addProperty("read_complete",false);
        assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.handle(surface));
        surface.getAsJsonArray("windows").get(1).getAsJsonObject().remove("read_complete");
        surface.getAsJsonArray("unavailable_windows").add("unread-window");
        assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.handle(surface));
    }

    @Test void pedestrianIconRequiresTheExactImageInsideItsOwnCard() throws Exception {
        var surface = surface(tree());
        var nodes = surface.getAsJsonArray("nodes");
        nodes.add(node("pw-card", "content", "rl_root", "android.widget.RelativeLayout", 20, 200, 160, 160, true));
        var title = node("pw-title", "pw-card", "tv_title", "android.widget.TextView", 30, 300, 140, 40, false);
        title.addProperty("text", "PED Alarm");
        nodes.add(title);
        var icon = node("pw-icon", "pw-card", "iv_des", "android.widget.ImageView", 50, 210, 90, 90, false);
        nodes.add(icon);
        assertEquals("pw-icon", VehicleDrawer.pedestrianWarningIcon(surface).get("id").getAsString());
        var window = icon.remove("window_id");
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningIcon(surface));
        icon.add("window_id", window);
        icon.addProperty("parent", "card");
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningIcon(surface));
        icon.addProperty("parent", "pw-card");
        icon.add("bounds", bounds(0, 210, 90, 90));
        icon.add("clipped_bounds", bounds(0, 210, 90, 90));
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningIcon(surface));
        icon.add("bounds", bounds(50, 210, 90, 90));
        icon.add("clipped_bounds", bounds(50, 210, 90, 90));
        var duplicate = icon.deepCopy();
        duplicate.addProperty("id", "duplicate-icon");
        nodes.add(duplicate);
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningIcon(surface));
    }

    @Test void pedestrianAddCannotRemoveAnAlreadyAddedCard() throws Exception {
        var surface = surface(tree());
        var nodes = surface.getAsJsonArray("nodes");
        var library = node("pw-library", "content", "rv_library", "androidx.recyclerview.widget.RecyclerView", 20, 150, 200, 500, false);
        var card = node("pw-card", "pw-library", "rl_root", "android.widget.RelativeLayout", 20, 200, 160, 160, true);
        var title = node("pw-title", "pw-card", "tv_title", "android.widget.TextView", 30, 300, 140, 40, false);
        title.addProperty("text", "PED Alarm");
        var overlay = node("pw-add", "pw-card", "top_view", "android.view.View", 20, 200, 160, 160, true);
        nodes.add(library); nodes.add(card); nodes.add(title); nodes.add(overlay);
        assertEquals("pw-add", VehicleDrawer.pedestrianWarningAdd(surface));
        card.addProperty("parent", "content");
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningAdd(surface));
        card.addProperty("parent", "pw-library");
        overlay.addProperty("visible", false);
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningAdd(surface));
    }

    @Test void pedestrianCardRequiresOemTitleAndClickableAncestry() throws Exception {
        var surface = surface(tree());
        var nodes = surface.getAsJsonArray("nodes");
        var card = node("pw-card", "content", "rl_root", "android.widget.RelativeLayout", 20, 200, 160, 160, true);
        var title = node("pw-title", "pw-card", "tv_title", "android.widget.TextView", 30, 300, 140, 40, false);
        title.addProperty("text", "PED Alarm");
        nodes.add(card); nodes.add(title);
        assertEquals("pw-card", VehicleDrawer.pedestrianWarningCard(surface));
        title.addProperty("text", "行人报警音");
        assertEquals("pw-card", VehicleDrawer.pedestrianWarningCard(surface));
        card.addProperty("enabled", false);
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningCard(surface));
        card.addProperty("enabled", true);
        title.addProperty("text", "PW");
        assertThrows(IOException.class, () -> VehicleDrawer.pedestrianWarningCard(surface));
    }

    @Test void cardModeAndActualSelectedSwitchAreIndependentProjections()throws Exception {
        JsonObject t=tree();var state=VehicleDrawer.read(surface(t));assertEquals(ForcedEvProgram.Mode.ELECTRIC,state.mode());assertEquals(Boolean.FALSE,state.forceSelected());assertEquals("card",state.cardId());
        node(t,5).addProperty("text","Hybrid");surface(t).getAsJsonArray("nodes").remove(6);state=VehicleDrawer.read(surface(t));assertEquals(ForcedEvProgram.Mode.HYBRID,state.mode());assertNull(state.forceSelected());
        node(t,5).addProperty("text","Electric");assertThrows(IOException.class,()->VehicleDrawer.read(surface(t)));
    }
    @Test void partialDuplicateClippedUnknownSelectedAndWrongFocusCannotSupplyInput()throws Exception {
        for(String bad:List.of("partial","duplicate","selected","clipped","focus","ancestry")) {
            JsonObject t=tree();switch(bad){case "partial"->surface(t).addProperty("complete",false);case "duplicate"->{JsonObject other=node(t,5).deepCopy();other.addProperty("id","other");surface(t).getAsJsonArray("nodes").add(other);}case "selected"->node(t,6).remove("selected");case "clipped"->node(t,4).getAsJsonObject("clipped_bounds").addProperty("width",1);case "focus"->surface(t).getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("input_focused",false);default->node(t,5).addProperty("parent","title");}
            assertThrows(IOException.class,()->VehicleDrawer.read(surface(t)),bad);
        }
    }
    @Test void actualHandleAndEmptyRegionProduceClosedFiniteGestureKinds()throws Exception {
        VoyemuClient client=new VoyemuClient((b,t)->{throw new AssertionError("builder must not dispatch");});JsonObject t=tree();
        UiTarget handle=UiTarget.selectDrawerHandle(t,"profile",SESSION);JsonObject open=RunFiles.parse(client.openVehicleDrawer("c".repeat(32),"profile",SESSION,handle).json());DrawerGesture.validate(open);assertEquals(15,open.getAsJsonArray("points").get(0).getAsJsonObject().get("x").getAsInt());assertEquals(960,open.getAsJsonArray("points").get(10).getAsJsonObject().get("x").getAsInt());assertThrows(IllegalArgumentException.class,()->client.tap("d".repeat(32),"profile",SESSION,handle));
        UiTarget close=UiTarget.selectDrawerClose(t,"profile",SESSION);JsonObject shut=RunFiles.parse(client.closeVehicleDrawer("d".repeat(32),"profile",SESSION,close).json());DrawerGesture.validateClose(shut);assertEquals(100,shut.getAsJsonArray("points").get(10).getAsJsonObject().get("at_ms").getAsInt());assertThrows(IllegalArgumentException.class,()->client.openVehicleDrawer("e".repeat(32),"profile",SESSION,close));
        open.getAsJsonArray("points").get(1).getAsJsonObject().addProperty("y",1);assertThrows(IOException.class,()->DrawerGesture.validate(open));
        shut.getAsJsonArray("points").get(1).getAsJsonObject().addProperty("at_ms",200);assertThrows(IOException.class,()->DrawerGesture.validateClose(shut));
    }
    @Test void backgroundTopologyAndFreeRegionAreRequiredBeforeClose()throws Exception {
        JsonObject missing=tree();node(missing,3).addProperty("text","unknown");assertThrows(IOException.class,()->VehicleDrawer.close(surface(missing)));
        JsonObject blocked=tree();surface(blocked).getAsJsonArray("nodes").add(node("blocker","content","blocking","android.view.View",0,0,800,720,false));assertThrows(IOException.class,()->VehicleDrawer.close(surface(blocked)));
    }
    @Test void retainedOpeningAndClosingGesturesCanOnlyLookupNeverReplay()throws Exception {
        AtomicInteger sent=new AtomicInteger();VoyemuClient client=new VoyemuClient((bytes,timeout)->{assertEquals("operation.get",RunFiles.parse(bytes).get("op").getAsString());sent.incrementAndGet();return new VoyemuClient.ProcessResult(1,false,"","unavailable");});JsonObject t=tree();
        for(boolean close:List.of(false,true)) {
            var original=close?client.closeVehicleDrawer("d".repeat(32),"profile",SESSION,UiTarget.selectDrawerClose(t,"profile",SESSION)):client.openVehicleDrawer("c".repeat(32),"profile",SESSION,UiTarget.selectDrawerHandle(t,"profile",SESSION));Path dir=Files.createDirectory(directory.resolve(close?"close":"open"));String scope="session:"+SESSION.id();
            try(Journal journal=Journal.open(dir)){journal.reserve(scope,"machine",original.requestId(),original.json().getBytes(StandardCharsets.UTF_8));}
            try(Journal journal=Journal.open(dir)){var retained=client.reserved(journal,scope,"machine");assertEquals(original.json(),retained.json());assertEquals(VoyemuClient.State.UNKNOWN,client.executeReserved(retained,Duration.ofSeconds(1),journal,"machine").state());assertEquals(close?1:0,sent.get());client.execute(client.lookupReserved("e".repeat(32),journal,"machine",retained),Duration.ofSeconds(1));}
        }assertEquals(2,sent.get());
    }    @Test void drawerGesturesKeepTheExactObservedSourceScope()throws Exception {
        VoyemuClient client=new VoyemuClient((b,t)->{throw new AssertionError("builder must not dispatch");});
        for(boolean attached:List.of(false,true)) {
            JsonObject tree=tree();if(attached)surface(tree).addProperty("scope","all_attached");
            var handle=UiTarget.selectDrawerHandle(tree,"profile",SESSION);var close=UiTarget.selectDrawerClose(tree,"profile",SESSION);
            JsonObject open=RunFiles.parse(client.openVehicleDrawer("c".repeat(32),"profile",SESSION,handle).json());
            JsonObject shut=RunFiles.parse(client.closeVehicleDrawer("d".repeat(32),"profile",SESSION,close).json());
            if(attached){assertEquals("all_attached",open.get("scope").getAsString());assertEquals("all_attached",shut.get("scope").getAsString());}
            else {assertFalse(open.has("scope"),"Accessibility target must not be re-read through all_attached");assertFalse(shut.has("scope"));}
            DrawerGesture.validate(open);DrawerGesture.validateClose(shut);
            open.addProperty("scope","foreign");shut.addProperty("scope","foreign");
            assertThrows(IOException.class,()->DrawerGesture.validate(open));assertThrows(IOException.class,()->DrawerGesture.validateClose(shut));
        }
    }
    @Test void preLayoutCardSwitchAndHandleNeverCountAsReady()throws Exception {
        for(int index:List.of(4,6)) {
            var tree=tree();node(tree,index).getAsJsonObject("bounds").addProperty("height",0);node(tree,index).getAsJsonObject("clipped_bounds").addProperty("height",0);
            assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.read(surface(tree)));
        }
        var tree=tree();node(tree,7).getAsJsonObject("bounds").addProperty("height",0);node(tree,7).getAsJsonObject("clipped_bounds").addProperty("height",0);
        assertThrows(UiEvidence.Unavailable.class,()->VehicleDrawer.handle(surface(tree)));
    }

}
