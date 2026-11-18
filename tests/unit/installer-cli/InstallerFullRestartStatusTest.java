package ru.voboost.tests.installer.cli;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
class InstallerFullRestartStatusTest {
    private static final String BOOT="11111111-1111-1111-1111-111111111111";
    private static JsonObject receipt(){return JsonParser.parseString("{\"status\":\"completed\",\"mode\":\"cold\",\"scope\":\"android_qnx_cold_boot_changed\",\"session_id\":\"current\",\"guest_acknowledged\":true,\"old_processes_stopped\":true,\"authority_retained\":true,\"after\":{\"android_pid\":126,\"android_boot_id\":\""+BOOT+"\",\"qnx\":{\"kind\":\"owned_cold_incarnation\",\"generation\":\"4\",\"qemu_pid\":125}}}").getAsJsonObject();}
    private static JsonObject status(){return JsonParser.parseString("{\"session_id\":\"current\",\"android_pid\":126,\"qnx_pid\":125,\"deliveries\":{\"qnx\":{\"pid\":125,\"generation\":4},\"android\":{\"pid\":126}}}").getAsJsonObject();}
    @Test void fullColdReceiptDoesNotRequireOldAndroidRebootDelivery(){assertTrue(InstallerDriver.fullRestartStatesBoot(receipt(),status(),BOOT));}
    @Test void wrongColdGenerationProcessAndSessionFail(){for(String mutation:new String[]{"generation","pid","androidpid","session","boot","partial","ack","kind"}){JsonObject r=receipt(),s=status();switch(mutation){case "generation"->s.getAsJsonObject("deliveries").getAsJsonObject("qnx").addProperty("generation",3);case "pid"->s.addProperty("qnx_pid",126);case "androidpid"->s.addProperty("android_pid",127);case "session"->s.addProperty("session_id","other");case "boot"->r.getAsJsonObject("after").addProperty("android_boot_id","22222222-2222-2222-2222-222222222222");case "partial"->r.addProperty("old_processes_stopped",false);case "ack"->r.addProperty("guest_acknowledged",false);case "kind"->r.getAsJsonObject("after").getAsJsonObject("qnx").addProperty("kind","unknown");}assertFalse(InstallerDriver.fullRestartStatesBoot(r,s,BOOT),mutation);}}
}
