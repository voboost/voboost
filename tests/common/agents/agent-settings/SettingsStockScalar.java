package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import ru.voboost.tests.installer.cli.StartupObservation;
import ru.voboost.tests.installer.cli.SystemLocale;
import ru.voboost.voyemu.UiEvidence;

/** One visible OEM Settings string, not a general owner Resources probe or whole-agent verdict. */
public final class SettingsStockScalar {
    private static final String OWNER="com.qinggan.app.setting", RESOURCE=OWNER+":id/btn_factory_reset";
    public record Sample(String profile,String session,String boot,String language,String observation,String source,
            StartupObservation.ProcessIdentity owner,String text) {}
    private static String text(JsonObject o,String key)throws IOException {
        if(o==null||!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString()||o.get(key).getAsString().isBlank())throw new IOException("Settings scalar field missing: "+key);
        return o.get(key).getAsString();
    }
    private static long uptime(JsonObject ui,String key)throws IOException {
        if(!ui.has(key)||!ui.get(key).isJsonPrimitive()||!ui.get(key).getAsString().matches("[0-9]{1,18}"))throw new IOException("Settings source uptime missing");
        return Long.parseLong(ui.get(key).getAsString());
    }
    private static boolean yes(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonPrimitive()&&o.getAsJsonPrimitive(key).isBoolean()&&o.get(key).getAsBoolean();}
    private static StartupObservation.ProcessIdentity stockProcess(String body,String boot)throws IOException {
        String start="current-process-identities:\n",end="current-process-identities-end=complete\n";
        int first=body.indexOf(start),last=body.indexOf(end);
        if(first<0||last<=first||body.lastIndexOf(start)!=first||body.lastIndexOf(end)!=last)throw new IOException("complete process frame required");
        String section=body.substring(first+start.length(),last);
        if(!section.startsWith("schemaVersion=1\nbootId="+boot+"\nenforcing=Enforcing\n")||!section.endsWith("bootIdAfter="+boot+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\n"))throw new IOException("process boot fence differs");
        var pattern=java.util.regex.Pattern.compile("process=([A-Za-z0-9_.:-]+) pid=([1-9][0-9]{0,9}) starttime=([1-9][0-9]{0,19}) argv0=([A-Za-z0-9_./:-]+)");
        StartupObservation.ProcessIdentity found=null;java.util.Set<String> seen=new java.util.HashSet<>();
        for(String line:section.split("\n")) {
            if(!line.startsWith("process="))continue;
            var m=pattern.matcher(line);if(!m.matches()) {
                var unavailable=java.util.regex.Pattern.compile("process=([A-Za-z0-9_.:-]+) unavailable=missing-or-invalid").matcher(line);
                if(!unavailable.matches()||!seen.add(unavailable.group(1)))throw new IOException("malformed or duplicate process frame");
                continue;
            }
            if(!seen.add(m.group(1)))throw new IOException("duplicate process identity");
            if(!OWNER.equals(m.group(1)))continue;
            try {long pid=Long.parseLong(m.group(2));var birth=new java.math.BigInteger(m.group(3));
                if(pid>Integer.MAX_VALUE||birth.bitLength()>64||!OWNER.equals(m.group(4)))throw new IOException("invalid Settings process identity");
                found=new StartupObservation.ProcessIdentity((int)pid,m.group(3),m.group(4));
            }catch(NumberFormatException failure){throw new IOException("invalid Settings process identity",failure);}
        }
        if(found==null)throw new IOException("Settings process identity unavailable");return found;
    }
    /** Runtime collection must bracket these readonly observations and assure no concurrent inputs. */
    public static Sample read(JsonObject ui,String boot,String profile,String session,int androidPid,String localeBody,String processBody)throws IOException {
        if(!"voyemu.ui.v1".equals(text(ui,"format"))||!boot.equals(text(ui,"boot_id"))||!profile.equals(text(ui,"profile"))||!session.equals(text(ui,"session_id"))
                ||!ui.has("android_pid")||!ui.get("android_pid").isJsonPrimitive()||!ui.getAsJsonPrimitive("android_pid").isNumber()
                ||!Integer.toString(androidPid).equals(ui.get("android_pid").getAsString())||androidPid<2)throw new IOException("Settings scalar source scope differs");
        String observation=text(ui,"observation_id"),source=text(ui,"source_instance");text(ui,"revision");
        if(!"android.uptime".equals(text(ui,"source_clock_domain")))throw new IOException("Settings source clock differs");
        long started=uptime(ui,"started_uptime_ms"),ended=uptime(ui,"completed_uptime_ms");
        if(ended<started||ended-started>30000)throw new IOException("Settings source interval differs");
        SystemLocale locale=SystemLocale.read(localeBody,boot);
        var owner=stockProcess(processBody,boot);
        JsonObject surface=UiEvidence.surface(ui,"primary"),found=null;
        for(JsonElement element:surface.getAsJsonArray("nodes")) {
            JsonObject node=element.getAsJsonObject();
            if(!node.has("resource_id")||!node.get("resource_id").isJsonPrimitive()||!RESOURCE.equals(node.get("resource_id").getAsString()))continue;
            if(!OWNER.equals(text(node,"package")))throw new IOException("foreign Settings scalar owner");
            if(!yes(node,"visible")||!yes(node,"present")||!yes(node,"enabled"))throw new IOException("Settings scalar is not observable");
            if(found!=null)throw new IOException("ambiguous Settings scalar");found=node;
        }
        if(found==null)throw new IOException("OEM Settings scalar unavailable");String value=text(found,"text");
        if(value.length()>512||value.codePoints().anyMatch(Character::isISOControl)||!Set.of("en","zh","ru").contains(locale.language()))throw new IOException("bounded Settings scalar required");
        return new Sample(profile,session,boot,locale.language(),observation,source,owner,value);
    }
    /** Only a same-locale exact stock value comparison; this is not a per-agent acceptance result. */
    public static void requireStockRestored(Sample stock,Sample disabled)throws IOException {
        if(stock==null||disabled==null||!stock.profile().equals(disabled.profile())||!stock.session().equals(disabled.session())||!stock.language().equals(disabled.language())||!stock.text().equals(disabled.text())
                ||stock.observation().equals(disabled.observation()))throw new IOException("same-locale stock Settings scalar restoration unavailable");
    }
    private SettingsStockScalar() {}
}
