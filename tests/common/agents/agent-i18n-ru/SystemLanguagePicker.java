package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Probe the actual OEM language group and its unique checked OEM or generated Russian child; this structural probe does not grant all-owner admission. */
public record SystemLanguagePicker(String language, String english, String chinese, String russian) {
    public static final class Unavailable extends IOException {
        public Unavailable(String reason) { super(reason); }
    }
    public static final String OWNER = "com.qinggan.app.setting";
    public static final String GROUP = OWNER + ":id/display_language_radio_group";
    public String target(String locale) {
        return switch (locale) { case "en" -> english; case "zh" -> chinese; case "ru" -> { if(russian==null)throw new IllegalArgumentException("observed generated Russian contribution required");yield russian; } default -> throw new IllegalArgumentException("EN/ZH/RU required"); };
    }
    public static SystemLanguagePicker read(JsonObject surface) throws IOException {
        JsonObject group = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (OWNER.equals(text(node, "package")) && GROUP.equals(text(node, "resource_id")) && yes(node, "visible")) {
                if (group != null) throw new IOException("ambiguous OEM language group");
                group = node;
            }
        }
        if (group == null) throw new Unavailable("OEM language group unavailable");
        int windows = 0;
        for (JsonElement item : surface.getAsJsonArray("windows")) {
            JsonObject window = item.getAsJsonObject();
            if (text(group, "window_id").equals(text(window, "id")) && OWNER.equals(text(window, "package"))
                    && "1".equals(text(window, "type"))) ++windows;
        }
        if (windows != 1) throw new IOException("OEM language window unavailable");
        String english = null, chinese = null, russian = null, language = null; int checked = 0;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (!inside(surface,node,group))continue;
            if((OWNER+":id/display_language_radio_russian").equals(text(node,"resource_id")))throw new IOException("native Russian picker requires its independently validated adapter");
            if(!yes(node,"visible")||!yes(node,"checkable"))continue;
            String resource = text(node, "resource_id"), id = text(node, "id");
            if (id.isBlank()) throw new IOException("language radio node identity missing");
            if (resource.equals(OWNER + ":id/display_language_radio_english")) {
                if (english != null) throw new IOException("ambiguous English radio"); english = id;
                if (yes(node, "checked")) language = "en";
            }
            if (resource.equals(OWNER + ":id/display_language_radio_chinese")) {
                if (chinese != null) throw new IOException("ambiguous Chinese radio"); chinese = id;
                if (yes(node, "checked")) language = "zh";
            }
            if("Русский".equals(text(node,"text"))) {
                if(!resource.isEmpty()||!yes(node,"enabled")||russian!=null||!text(group,"id").equals(text(node,"parent")))throw new IOException("generated Russian picker child differs from the probed contribution");
                russian=id;if(yes(node,"checked"))language="ru";
            }
            if (yes(node, "checked")) ++checked;
        }
        if (checked == 0 || english == null || chinese == null) throw new Unavailable("OEM language radio transition incomplete");
        if (checked != 1 || language == null)
            throw new IOException("unique checked supported radio unavailable; another language is not a substitute");
        return new SystemLanguagePicker(language, english, chinese, russian);
    }
    private static boolean inside(JsonObject surface, JsonObject node, JsonObject group) throws IOException {
        Set<String> seen = new HashSet<>();
        while (true) {
            if (!OWNER.equals(text(node, "package")) || !text(group, "window_id").equals(text(node, "window_id"))) return false;
            String parent = text(node, "parent");
            if (parent.isEmpty()) return false;
            if (!seen.add(parent)) throw new IOException("cyclic language ancestry");
            if (parent.equals(text(group, "id"))) return true;
            JsonObject found = null;
            for (JsonElement item : surface.getAsJsonArray("nodes")) {
                JsonObject candidate = item.getAsJsonObject();
                if (parent.equals(text(candidate, "id"))) {
                    if (found != null) throw new IOException("ambiguous language ancestor"); found = candidate;
                }
            }
            if (found == null) return false;
            node = found;
        }
    }
    private static String text(JsonObject object, String key) { JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsString() : ""; }
    private static boolean yes(JsonObject object, String key) { JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean(); }
}
