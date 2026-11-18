package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import ru.voboost.tests.installer.cli.StartupObservation;
import ru.voboost.voyemu.UiEvidence;

/** The enabled native vehicle input must have accepted the current desired document before an OEM tap. */
public final class VehicleInputReady {
    private VehicleInputReady() {}
    public static void require(RuntimeObservation runtime,String agent,String process,
            StartupObservation.ProcessIdentity actual)throws IOException {
        if(!Set.of("vehicle-drive-forced-ev","vehicle-drive-pedestrian-warning").contains(agent)
                ||!Set.of("com.qinggan.canbus.service","com.qinggan.app.launcher","com.qinggan.app.vehiclesetting").contains(process)
                ||!runtime.desired(agent))throw new IOException("enabled fixed vehicle input required");
        JsonObject row=null;
        for(JsonObject candidate:runtime.targets(agent))if(process.equals(text(candidate,"process")))row=candidate;
        if(row==null||!"active".equals(text(row,"state"))||!"ready".equals(text(row,"reason"))||!row.has("currentEndpoint"))
            throw new UiEvidence.Unavailable("current vehicle input is not ready: "+agent+" / "+process);
        JsonObject binding=row.getAsJsonObject("currentEndpoint"),source=runtime.runtime();
        String document=text(binding,"desiredDocumentGeneration");
        if(!row.has("documentGeneration")||!document.equals(row.get("documentGeneration").getAsString()))
            throw new UiEvidence.Unavailable("vehicle input has not accepted its current desired document");
        String boot=runtime.publisher().split(":",-1)[0];
        var authority=new RootVehicleState.Authority(boot,text(source,"generationId"),text(source,"catalogSha256"),
            process,Integer.toString(actual.pid()),actual.starttime(),text(binding,"endpointEpoch"),document);
        if(!process.equals(actual.argv0()))throw new IOException("vehicle input executable differs");
        JavaStopAcknowledgement.prior(binding,authority,runtime.publisher());
    }
    private static String text(JsonObject value,String key) {
        return value.has(key)&&value.get(key).isJsonPrimitive()?value.get(key).getAsString():"";
    }
}
