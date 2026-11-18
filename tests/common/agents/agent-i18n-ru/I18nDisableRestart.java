package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.installer.cli.StartupObservation;

/** I18nRuAgent inherits Agent.RESTART_PROCESS; proves only one observed disable/restart, not OEM resources. */
public final class I18nDisableRestart {
    public record Proof(String owner, StartupObservation.ProcessIdentity before, StartupObservation.ProcessIdentity after) {}
    public static Proof check(AcceptancePlan.Key key,String boot,RuntimeObservation enabled,RuntimeObservation disabled,
            String processesBefore,String processesAfter)throws IOException {
        if(!"agent/i18n-ru".equals(key.id())||!key.target().startsWith("android:"))throw new IOException("exact i18n target required");
        String owner=key.target().substring("android:".length());
        if(!I18nResourceReadbacks.PROFILES.containsKey(owner))throw new IOException("unknown i18n owner");
        if(!enabled.publisher().equals(disabled.publisher())||!enabled.publisher().startsWith(boot+":"))throw new IOException("disable crossed publisher or boot");
        if(!enabled.desired("i18n-ru")||disabled.desired("i18n-ru")||enabled.revision().equals(disabled.revision()))throw new IOException("confirmed new disabled checkbox state required");
        var resource=I18nResourceReadbacks.readCurrent(enabled,"ru").requireTarget(owner);
        var before=StartupObservation.identity(processesBefore,boot,owner);var after=StartupObservation.identity(processesAfter,boot,owner);
        if(!owner.equals(before.argv0())||!owner.equals(after.argv0())||!Integer.toString(before.pid()).equals(resource.targetPid())||!before.starttime().equals(resource.targetStarttime()))throw new IOException("current source endpoint does not match target process");
        if(before.pid()==after.pid()||before.starttime().equals(after.starttime()))throw new IOException("disabled target process did not acquire new lifetime");
        for(JsonObject row:disabled.targets("i18n-ru"))if(row.has("process")&&owner.equals(row.get("process").getAsString())&&row.has("state")&&"active".equals(row.get("state").getAsString()))throw new IOException("disabled target still active");
        return new Proof(owner,before,after);
    }
    private I18nDisableRestart() {}
}
