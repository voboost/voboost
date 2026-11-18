package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class VehicleUiTest {
    static JsonObject tree()throws IOException {
        return RunFiles.parse("""
        {"format":"voyemu.ui.v1","profile":"profile","session_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","boot_id":"boot","source_instance":"source","revision":"1","screens":[{
        "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
        "windows":[{"id":"window","package":"com.qinggan.app.vehiclesetting","title":"Vehicle","type":1,"active":true,"input_focused":true,"read_complete":true,"bounds":{"x":0,"y":0,"width":1920,"height":720}}],
        "nodes":[{"id":"group","window_id":"window","parent":null,"package":"com.qinggan.app.vehiclesetting","resource_id":"com.qinggan.app.vehiclesetting:id/fragment_safe_hum_vsp","class":"android.widget.LinearLayout","visible":true,"enabled":true,"clickable":false,"bounds":{"x":10,"y":20,"width":100,"height":80},"clipped_bounds":{"x":10,"y":20,"width":100,"height":80}},
        {"id":"control","window_id":"window","parent":"group","package":"com.qinggan.app.vehiclesetting","resource_id":"com.qinggan.app.vehiclesetting:id/checkbox","class":"android.widget.CheckBox","text":"","visible":true,"enabled":true,"clickable":true,"checkable":true,"checked":false,"actionable_candidate":true,"bounds":{"x":20,"y":30,"width":20,"height":20},"clipped_bounds":{"x":20,"y":30,"width":20,"height":20}}]}]}
        """);
    }
    static JsonObject surface(JsonObject tree){return tree.getAsJsonArray("screens").get(0).getAsJsonObject();}
    static JsonObject node(JsonObject tree,int index){return surface(tree).getAsJsonArray("nodes").get(index).getAsJsonObject();}
    @Test void actualCheckedDescendantAndOwnFocusedWindowDefineTheFeature()throws Exception {
        JsonObject tree=tree();var state=VehicleUi.feature(surface(tree),VehicleUi.Feature.PEDESTRIAN_WARNING);assertFalse(state.checked());assertEquals("control",state.nodeId());
        node(tree,0).addProperty("resource_id",VehicleUi.OWNER+":id/power_mode_electric_force");node(tree,1).addProperty("checked",true);assertTrue(VehicleUi.feature(surface(tree),VehicleUi.Feature.FORCED_EV).checked());
    }
    @Test void duplicateUnknownClippedOrForeignCheckboxNeverSuppliesAValue()throws Exception {
        for(String bad:List.of("duplicate","checked","clipped","owner","focus","partial")) {
            JsonObject tree=tree();switch(bad) {
                case "duplicate"->{JsonObject other=node(tree,1).deepCopy();other.addProperty("id","other");surface(tree).getAsJsonArray("nodes").add(other);}
                case "checked"->node(tree,1).remove("checked");case "clipped"->node(tree,1).getAsJsonObject("clipped_bounds").addProperty("width",1);
                case "owner"->node(tree,1).addProperty("package","foreign.package");case "focus"->surface(tree).getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("input_focused",false);
                default->surface(tree).addProperty("complete",false);
            }assertThrows(IOException.class,()->VehicleUi.feature(surface(tree),VehicleUi.Feature.PEDESTRIAN_WARNING),bad);
        }
    }
    @Test void electricRadioAndOemConsentRequireActualCheckedStateAndExactAncestry()throws Exception {
        JsonObject radioTree=tree(),radio=node(radioTree,0);radio.addProperty("resource_id",VehicleUi.OWNER+":id/fragment_radio_button_power_mode_electric");radio.addProperty("clickable",true);radio.addProperty("actionable_candidate",true);radio.addProperty("checkable",true);radio.addProperty("checked",false);
        assertFalse(VehicleUi.electricRadio(surface(radioTree)).checked());radio.remove("checked");assertThrows(IOException.class,()->VehicleUi.electricRadio(surface(radioTree)));
        JsonObject consent=tree(),background=node(consent,0),dialog=node(consent,1);background.addProperty("resource_id",VehicleUi.OWNER+":id/view_bg");background.addProperty("class","android.widget.RelativeLayout");dialog.addProperty("resource_id",VehicleUi.OWNER+":id/fragment_forceev_dialog");dialog.addProperty("class","android.widget.RelativeLayout");
        JsonObject button=dialog.deepCopy();button.addProperty("id","agree");button.addProperty("parent","control");button.addProperty("resource_id",VehicleUi.OWNER+":id/disclaimer_agree");button.addProperty("class","android.widget.Button");surface(consent).getAsJsonArray("nodes").add(button);
        assertEquals("agree",VehicleUi.forceConsent(surface(consent)));button.addProperty("parent","group");assertThrows(IOException.class,()->VehicleUi.forceConsent(surface(consent)));
    }
    @Test void closedVehicleScrollResourcesReuseFiniteJournalledTraceAndNeverTap()throws Exception {
        var session=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder must not dispatch");});
        for(String resource:List.of("menu_container","fragment_scrollview","fragment_drive_preference_scroll")) {
            JsonObject tree=tree(),scroll=node(tree,0);scroll.addProperty("class","android.widget.ScrollView");scroll.addProperty("scrollable",true);scroll.addProperty("resource_id",VehicleUi.OWNER+":id/"+resource);
            UiTarget target=UiTarget.selectScrollable(tree,"profile",session,"primary",n->n.get("id").getAsString().equals("group"));
            JsonObject request=RunFiles.parse(client.scrollSettings("c".repeat(32),"profile",session,target).json());assertEquals(11,request.getAsJsonArray("points").size());assertEquals("scroll",request.get("intent").getAsString());assertThrows(IllegalArgumentException.class,()->client.tap("d".repeat(32),"profile",session,target));
            scroll.addProperty("resource_id",VehicleUi.OWNER+":id/unknown");assertThrows(IOException.class,()->UiTarget.selectScrollable(tree,"profile",session,"primary",n->n.get("id").getAsString().equals("group")));
        }
    }    private static JsonObject consentTree()throws Exception {
        var tree=tree();var background=node(tree,0);var dialog=node(tree,1);
        background.addProperty("resource_id",VehicleUi.OWNER+":id/view_bg");background.addProperty("class","android.widget.RelativeLayout");
        dialog.addProperty("resource_id",VehicleUi.OWNER+":id/fragment_forceev_dialog");dialog.addProperty("class","android.widget.RelativeLayout");
        var button=dialog.deepCopy();button.addProperty("id","agree");button.addProperty("parent","control");button.addProperty("class","android.widget.Button");button.addProperty("resource_id",VehicleUi.OWNER+":id/disclaimer_agree");
        surface(tree).getAsJsonArray("nodes").add(button);return tree;
    }
    @Test void preLayoutConsentOnlyWaitsUntilAFreshActualTargetBecomesActionable()throws Exception {
        var session=VoyemuClient.Session.attached("a".repeat(32));var first=consentTree();
        var window=surface(first).getAsJsonArray("windows").get(0).getAsJsonObject();window.addProperty("type",-1);window.getAsJsonObject("bounds").addProperty("height",0);
        var button=node(first,2);button.addProperty("actionable_candidate",false);button.getAsJsonObject("clipped_bounds").addProperty("width",0);button.getAsJsonObject("clipped_bounds").addProperty("height",0);
        assertEquals("agree",VehicleUi.forceConsent(surface(first)));
        assertThrows(IOException.class,()->UiTarget.select(first,"profile",session,"primary",n->"agree".equals(n.get("id").getAsString())));
        var waiting=VehicleUi.forceConsentTarget(first,"profile",session);assertEquals("window",waiting.window());assertNull(waiting.target());
        var settled=consentTree();settled.addProperty("revision","2");
        // Actual API28 OEM Accessibility keeps type=-1 even after the native window and button settle.
        surface(settled).getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("type",-1);
        var ready=VehicleUi.forceConsentTarget(settled,"profile",session);
        assertEquals(waiting.window(),ready.window());assertNotNull(ready.target());assertEquals("2",ready.target().expected().get("revision").getAsString());
        assertEquals(30,ready.target().x());assertEquals(40,ready.target().y());
    }
    @Test void consentWaitNeverGrantsForeignScopeOrUnobservedGeometry()throws Exception {
        var session=VoyemuClient.Session.attached("a".repeat(32));
        var blocked=consentTree();node(blocked,2).addProperty("actionable_candidate",false);assertNull(VehicleUi.forceConsentTarget(blocked,"profile",session).target());
        var clipped=consentTree();node(clipped,2).getAsJsonObject("clipped_bounds").addProperty("width",0);assertNull(VehicleUi.forceConsentTarget(clipped,"profile",session).target());
        var foreign=consentTree();foreign.addProperty("session_id","b".repeat(32));assertThrows(IOException.class,()->VehicleUi.forceConsentTarget(foreign,"profile",session));
        var ancestry=consentTree();node(ancestry,2).addProperty("parent","group");assertThrows(IOException.class,()->VehicleUi.forceConsentTarget(ancestry,"profile",session));
    }
    @Test void firstLayoutMenuCannotBePromotedToAnInputTargetByVisibleAndClickableAlone()throws Exception {
        var tree=tree();var menu=node(tree,1);menu.addProperty("resource_id",VehicleUi.OWNER+":id/rb_menu_drive_preference");
        menu.addProperty("actionable_candidate",false);menu.getAsJsonObject("clipped_bounds").addProperty("width",0);menu.getAsJsonObject("clipped_bounds").addProperty("height",0);
        assertThrows(UiEvidence.Unavailable.class,()->VehicleUi.uniqueResource(surface(tree),VehicleUi.OWNER+":id/rb_menu_drive_preference"));
        menu.add("clipped_bounds",menu.get("bounds").deepCopy());menu.addProperty("actionable_candidate",true);
        assertEquals("control",VehicleUi.uniqueResource(surface(tree),VehicleUi.OWNER+":id/rb_menu_drive_preference"));
    }
    @Test void zeroSizedCheckedControlsDoNotCountAsAnObservedFeature()throws Exception {
        var tree=tree();node(tree,1).getAsJsonObject("bounds").addProperty("height",0);node(tree,1).getAsJsonObject("clipped_bounds").addProperty("height",0);
        assertThrows(UiEvidence.Unavailable.class,()->VehicleUi.feature(surface(tree),VehicleUi.Feature.PEDESTRIAN_WARNING));
    }

    @Test void fevConsentRequiresTheCurrentFocusedCompleteWindow()throws Exception {
        var session=VoyemuClient.Session.attached("a".repeat(32));
        for(String field:List.of("active","input_focused","read_complete")) {
            var tree=consentTree();
            surface(tree).getAsJsonArray("windows").get(0).getAsJsonObject().addProperty(field,false);
            assertThrows(IOException.class,()->VehicleUi.forceConsentTarget(tree,"profile",session),field);
        }
    }
    @Test void fevConsentCannotCrossWindowsOrChooseBetweenDuplicateDialogs()throws Exception {
        var session=VoyemuClient.Session.attached("a".repeat(32));
        for(String mismatch:List.of("dialog-window","background-window","duplicate-button","duplicate-window")) {
            var tree=consentTree();
            switch(mismatch) {
                case "dialog-window"->node(tree,1).addProperty("window_id","another-window");
                case "background-window"->node(tree,0).addProperty("window_id","another-window");
                case "duplicate-button"->{var duplicate=node(tree,2).deepCopy();duplicate.addProperty("id","another-agree");surface(tree).getAsJsonArray("nodes").add(duplicate);}
                default->{var windows=surface(tree).getAsJsonArray("windows");windows.add(windows.get(0).deepCopy());}
            }
            assertThrows(IOException.class,()->VehicleUi.forceConsentTarget(tree,"profile",session),mismatch);
        }
    }
    @Test void fevConsentDisappearanceNeverReusesThePreviousActionTarget()throws Exception {
        var session=VoyemuClient.Session.attached("a".repeat(32));
        var visible=consentTree();
        assertNotNull(VehicleUi.forceConsentTarget(visible,"profile",session).target());
        var dismissed=visible.deepCopy();dismissed.addProperty("revision","2");
        node(dismissed,2).addProperty("visible",false);
        assertNull(VehicleUi.forceConsentTarget(dismissed,"profile",session));
        assertNotNull(VehicleUi.forceConsentTarget(visible,"profile",session).target());
    }

}
