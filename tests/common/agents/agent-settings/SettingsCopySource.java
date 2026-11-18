package ru.voboost.tests.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Versioned expected Settings data and the selected verified runtime catalog identity. */
public final class SettingsCopySource {
    public static final class Proof {
        private final Path evidence;
        private final JsonObject copy;
        private final String catalogIdentity;
        private Proof(Path evidence, JsonObject copy, String catalogIdentity) {
            this.evidence=evidence; this.copy=copy.deepCopy(); this.catalogIdentity=catalogIdentity;
        }
        public Path evidence(){return evidence;}
        public JsonObject copy(){return copy.deepCopy();}
        public String catalogIdentity(){return catalogIdentity;}
        public void requireCatalogIdentity(String actual)throws IOException {
            if(!catalogIdentity.equals(actual))throw new IOException("Settings expected data and measurement catalog identities differ");
        }
        public String startupDescription(String mode,String language)throws IOException {
            if(!Set.of("interface","hidden","none").contains(mode)||!Set.of("en","ru","zh").contains(language))throw new IOException("closed startup language and mode required");
            for(JsonElement element:copy.getAsJsonObject("languages").getAsJsonObject(language).getAsJsonArray("startup")) {
                JsonObject row=element.getAsJsonObject(); if(mode.equals(text(row,"value")))return required(row,"description");
            }
            throw new IOException("expected startup description missing");
        }
        public JsonObject language(String language)throws IOException {
            if(!Set.of("en","ru","zh").contains(language))throw new IOException("closed expected language required");
            return copy.getAsJsonObject("languages").getAsJsonObject(language).deepCopy();
        }
    }
    /** selectedCatalogIdentity is the identity from the caller's normal signed-catalog verification. */
    public static Proof readExpected(Path fixture,String softwareContract,String selectedCatalogIdentity)throws IOException {
        if(!Files.isRegularFile(fixture)||Files.size(fixture)>2*1024*1024)throw new IOException("bounded Settings expected-data file required");
        return decode(RunFiles.read(fixture),fixture,softwareContract,selectedCatalogIdentity);
    }
    static Proof decode(JsonObject value,Path fixture,String softwareContract,String catalog)throws IOException {
        try {
            keys(value,Set.of("schemaVersion","softwareContract","copy"));
            if(!value.get("schemaVersion").isJsonPrimitive()||!value.getAsJsonPrimitive("schemaVersion").isNumber()||!"1".equals(value.get("schemaVersion").getAsString())||!"1.0.0-beta1".equals(text(value,"softwareContract"))||!text(value,"softwareContract").equals(softwareContract))throw new IOException("Settings expected software contract differs");
            if(catalog==null||catalog.isBlank()||catalog.length()>256)throw new IOException("selected catalog identity required");
            JsonObject copy=value.getAsJsonObject("copy"); keys(copy,Set.of("languages","geometry"));
            JsonObject geometry=copy.getAsJsonObject("geometry");
            Set<String> geometryKeys=Set.of("RADIO_DESCRIPTION_TOP","RADIO_BOTTOM","CHECKBOX_BOTTOM","RADIO_TOP","BUTTON_HEIGHT","RADIO_DESCRIPTION_BOTTOM","RADIO_TITLE_GAP","RADIO_DESCRIPTION_ABOVE_BOTTOM","RADIO_TITLELESS_DESCRIPTION_TOP","RADIO_ITEM_MIN_WIDTH","SECTION_TOP","CHECKBOX_TOP","BUTTON_DESCRIPTION_BOTTOM","SECTION_GAP","RADIO_TITLE_START","SECTION_BOTTOM","BUTTON_DESCRIPTION_TOP","BUTTON_DESCRIPTION_GAP","RADIO_TEXT","SECTION_HEADER","RADIO_TITLE_TEXT","BUTTON_BOTTOM","CHECKBOX_DESCRIPTION_GAP","CHECKBOX_CONTENT_TOP","BUTTON_HORIZONTAL_PADDING","CHECKBOX_DESCRIPTION_BOTTOM","CHECKBOX_HEIGHT","RADIO_ITEM_PADDING","RADIO_DESCRIPTION_TEXT","BUTTON_TOP","PANEL_BOTTOM","RADIO_DESCRIBED_BOTTOM","BUTTON_DESCRIPTION_TEXT","RADIO_HEIGHT");
            keys(geometry,geometryKeys); for(JsonElement n:geometry.asMap().values())if(!n.isJsonPrimitive()||!n.getAsJsonPrimitive().isNumber()||!Double.isFinite(n.getAsDouble()))throw new IOException("invalid expected geometry");
            JsonObject languages=copy.getAsJsonObject("languages");keys(languages,Set.of("en","ru","zh"));
            for(String language:List.of("en","ru","zh")) {
                JsonObject profile=languages.getAsJsonObject(language);keys(profile,Set.of("vehicleGroup","checkboxes","uninstall","startup","startupTitle","layoutTopology","commonTitle","interfaceGroup","errors","unstableGroup"));
                for(String key:List.of("vehicleGroup","commonTitle","interfaceGroup","unstableGroup"))required(profile,key);
                JsonObject uninstall=profile.getAsJsonObject("uninstall");Set<String> uninstallKeys=Set.of("group","buttonText","description","awaitingConfirmation","started","retryableError","confirmLabel","cancelLabel");keys(uninstall,uninstallKeys);for(String key:uninstallKeys)required(uninstall,key);
                JsonObject errors=profile.getAsJsonObject("errors");keys(errors,Set.of("mutationFailure","unknownApplicabilityWarning"));for(String key:errors.keySet())required(errors,key);
                Set<String> modes=new HashSet<>();for(JsonElement item:profile.getAsJsonArray("startup")){JsonObject row=item.getAsJsonObject();keys(row,Set.of("value","label","description"));if(!modes.add(required(row,"value")))throw new IOException("duplicate startup mode");required(row,"label");required(row,"description");}if(!modes.equals(Set.of("interface","hidden","none")))throw new IOException("startup modes differ");
                Set<String> checkboxes=new HashSet<>();for(JsonElement item:profile.getAsJsonArray("checkboxes")){JsonObject row=item.getAsJsonObject();keys(row,Set.of("id","title","description"));if(!checkboxes.add(required(row,"id")))throw new IOException("duplicate checkbox");required(row,"title");required(row,"description");}if(checkboxes.isEmpty())throw new IOException("checkboxes missing");
                JsonObject topology=profile.getAsJsonObject("layoutTopology");keys(topology,Set.of("sections","sectionBodies","checkboxSwitches","panelTargets","dialogTargets"));
                JsonArray sections=topology.getAsJsonArray("sections"),bodies=topology.getAsJsonArray("sectionBodies");Set<String> sectionIds=new HashSet<>();for(JsonElement section:sections)if(!section.isJsonPrimitive()||!section.getAsJsonPrimitive().isString()||section.getAsString().isBlank()||!sectionIds.add(section.getAsString()))throw new IOException("invalid sections");if(sectionIds.isEmpty()||sections.size()!=bodies.size())throw new IOException("section bodies differ");
                for(int i=0;i<bodies.size();i++){JsonObject row=bodies.get(i).getAsJsonObject();keys(row,Set.of("key","index"));if(!sections.get(i).getAsString().equals(required(row,"key"))||!row.get("index").isJsonPrimitive()||!row.getAsJsonPrimitive("index").isNumber()||!Integer.toString(i).equals(row.get("index").getAsString()))throw new IOException("section body differs");}
                Set<String> switches=new HashSet<>();for(JsonElement item:topology.getAsJsonArray("checkboxSwitches")){JsonObject row=item.getAsJsonObject();keys(row,Set.of("key","section"));if(!switches.add(required(row,"key"))||!sectionIds.contains(required(row,"section")))throw new IOException("checkbox topology differs");}if(!switches.equals(checkboxes))throw new IOException("checkbox inventory differs");
                for(String targetKey:List.of("panelTargets","dialogTargets")){JsonArray targets=topology.getAsJsonArray(targetKey);Set<String> seen=new HashSet<>();for(JsonElement item:targets){JsonObject row=item.getAsJsonObject();keys(row,Set.of("role","value","class"));if(!seen.add(required(row,"role")+"\u0000"+required(row,"value")))throw new IOException("duplicate target");required(row,"class");}if(seen.isEmpty())throw new IOException("layout targets missing");if(targetKey.equals("dialogTargets")&&!seen.equals(Set.of("dialog.cancel\u0000cancel","dialog.confirm\u0000confirm")))throw new IOException("dialog targets differ");}
            }
            return new Proof(fixture,copy,catalog);
        }catch(RuntimeException malformed){throw new IOException("malformed Settings expected data",malformed);}
    }
    private static void keys(JsonObject object,Set<String> expected)throws IOException {if(object==null||!object.keySet().equals(expected))throw new IOException("Settings expected data fields differ");}
    private static String required(JsonObject object,String key)throws IOException {String value=text(object,key);if(value.isBlank()||value.length()>4096)throw new IOException("bounded expected copy missing: "+key);return value;}
    private static String text(JsonObject object,String key){return object!=null&&object.has(key)&&object.get(key).isJsonPrimitive()&&object.getAsJsonPrimitive(key).isString()?object.get(key).getAsString():"";}
    private SettingsCopySource(){}
}
