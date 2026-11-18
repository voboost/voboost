package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import ru.voboost.voyemu.SettingsCheckbox;
import ru.voboost.voyemu.UiEvidence;

/** Replays exact stable-catalog checkbox UI snapshots; it does not prove agent teardown/effect. */
public final class CheckboxTransition {
    public record Proof(String agent,String beforeObservation,String afterObservation) {}
    private static String text(JsonObject o,String key)throws IOException {
        if(o==null||!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString()||o.get(key).getAsString().isBlank())throw new IOException("checkbox UI scope missing: "+key);
        return o.get(key).getAsString();
    }
    private static void scope(JsonObject ui,String boot,String profile,String session,int androidPid)throws IOException {
        if(!"voyemu.ui.v1".equals(text(ui,"format"))||!boot.equals(text(ui,"boot_id"))||!profile.equals(text(ui,"profile"))||!session.equals(text(ui,"session_id"))
                ||!ui.has("android_pid")||!ui.get("android_pid").isJsonPrimitive()||!ui.getAsJsonPrimitive("android_pid").isNumber()
                ||!Integer.toString(androidPid).equals(ui.get("android_pid").getAsString())||androidPid<2)throw new IOException("checkbox UI owner/boot differs");
        text(ui,"observation_id");text(ui,"source_instance");text(ui,"revision");
    }
    public static Proof check(JsonObject catalog,String agent,String language,String boot,String profile,String session,int androidPid,
            JsonObject before,JsonObject after,boolean desired)throws IOException {
        var entries=AgentSettings.checkboxes(catalog);JsonObject entry=entries.get(agent);if(entry==null)throw new IOException("exact stable checkbox missing");
        String label=AgentSettings.title(entry,language);scope(before,boot,profile,session,androidPid);scope(after,boot,profile,session,androidPid);
        if(text(before,"observation_id").equals(text(after,"observation_id"))||!text(before,"source_instance").equals(text(after,"source_instance")))throw new IOException("stale or changed checkbox UI source");
        if(SettingsCheckbox.read(UiEvidence.surface(before,"primary"),label).checked()==desired
                ||SettingsCheckbox.read(UiEvidence.surface(after,"primary"),label).checked()!=desired)throw new IOException("actual checkbox transition differs");
        return new Proof(agent,text(before,"observation_id"),text(after,"observation_id"));
    }
    private CheckboxTransition() {}
}
