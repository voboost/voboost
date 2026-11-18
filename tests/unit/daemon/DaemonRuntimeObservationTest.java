package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class DaemonRuntimeObservationTest {
    static final RuntimeObservation.Identity IDENTITY = new RuntimeObservation.Identity("boot", "candidate", "manifest", "catalog", "generation");
    static JsonObject observation() throws IOException {
        return RunFiles.parse("""
            {"schema":2,"source":"voboost","status":"ready","boot_id":"boot","data":{
              "root":"present","root_proof":"fixed-path-owner-mode-boot-fenced","runtime_current":true,
              "files":{"config":{"status":"present","revision":"config-revision","data":{"agents":{"launcher":{"enabled":true,"options":{}}}}},
              "runtime":{"status":"present","current":true,"provenance":{"current":true,"proof":"same-boot-live-publisher","age_ms":20},
              "data":{"publisher":{"bootId":"boot","daemonPid":100,"daemonStarttime":"18446744073709551615"},
              "releaseIdentity":"candidate","manifestSha256":"manifest","catalogSha256":"catalog","generationId":"generation",
              "state":"ready","killed":false,"panic":false,"injections":[{"id":"launcher","process":"com.qinggan.app.launcher","state":"active"}]}}}}}
            """);
    }
    private static JsonObject runtimeFile(JsonObject input) { return input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime"); }
    @Test void configReadRaceRemainsRetryableOnlyWithValidCurrentRuntimeIdentity() throws Exception {
        var first = RuntimeObservation.read(observation(), IDENTITY);
        JsonObject input = observation();
        input.getAsJsonObject("data").addProperty("complete", false);
        input.getAsJsonObject("data").getAsJsonObject("files").add("config", RunFiles.parse("""
            {"errors":[{"check":"14","code":"PASSIVE_READ_UNAVAILABLE","errno":null,"stage":"read"}],
             "status":"unavailable"}
            """));
        assertThrows(RuntimeObservation.Unavailable.class,
                () -> RuntimeObservation.read(input, IDENTITY, first.publisher()));
        assertThrowsExactly(IOException.class,
                () -> RuntimeObservation.read(input, IDENTITY, "foreign"));
        runtimeFile(input).getAsJsonObject("data").addProperty("generationId", "foreign");
        assertThrowsExactly(IOException.class,
                () -> RuntimeObservation.read(input, IDENTITY, first.publisher()));
        runtimeFile(input).getAsJsonObject("data").addProperty("generationId", "generation");
        input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config")
                .getAsJsonArray("errors").get(0).getAsJsonObject().addProperty("code", "INVALID_DATA");
        assertThrowsExactly(IOException.class,
                () -> RuntimeObservation.read(input, IDENTITY, first.publisher()));
    }
    @Test void retainsExactPublisherIdentityWithoutFloatingPointLoss() throws Exception {
        JsonObject input = observation(); RuntimeObservation result = RuntimeObservation.read(input, IDENTITY);
        assertEquals("boot:100:18446744073709551615", result.publisher());
        assertTrue(result.desired("launcher")); assertFalse(result.desired("absent"));
        input.getAsJsonObject("data").remove("files");
        result.runtime().remove("injections"); result.targets("launcher").get(0).addProperty("state", "failed");
        assertEquals("active", result.targets("launcher").get(0).get("state").getAsString());
    }
    @Test void rejectsStaleOrUnprovenPublicationEvenWhenItsAgentRowSaysActive() throws Exception {
        for (String key : new String[]{"current", "proof", "age_ms"}) {
            JsonObject input = observation(), provenance = runtimeFile(input).getAsJsonObject("provenance");
            if (key.equals("current")) provenance.addProperty(key, false);
            else if (key.equals("proof")) provenance.addProperty(key, "cached");
            else provenance.addProperty(key, 5001);
            assertThrows(IOException.class, () -> RuntimeObservation.read(input, IDENTITY), key);
        }
        JsonObject wrongBoot = observation(); wrongBoot.addProperty("boot_id", "other");
        assertThrows(IOException.class, () -> RuntimeObservation.read(wrongBoot, IDENTITY));
        JsonObject wrongGeneration = observation(); runtimeFile(wrongGeneration).getAsJsonObject("data").addProperty("generationId", "other");
        assertThrows(IOException.class, () -> RuntimeObservation.read(wrongGeneration, IDENTITY));
    }
    @Test void historicalPublicationIsRetryableOnlyAfterMatchingItsKnownOwner() throws Exception {
        var first = RuntimeObservation.read(observation(), IDENTITY);
        JsonObject stale = observation();
        stale.getAsJsonObject("data").addProperty("runtime_current", false);
        stale.getAsJsonObject("data").addProperty("complete", false);
        var file = runtimeFile(stale);
        file.addProperty("current", false);
        file.add("provenance", RunFiles.parse("""
            {"current":false,"proof":"historical-publication","status":"stale",
             "reason":"publication-stale","max_age_ms":5000,"age_ms":8320}
            """));
        assertThrows(RuntimeObservation.Unavailable.class, () -> RuntimeObservation.read(stale, IDENTITY, first.publisher()));
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.read(stale, IDENTITY));
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.read(stale, IDENTITY, "foreign"));
        var admission = assertThrows(RuntimeObservation.Stale.class,
                () -> RuntimeObservation.readForAdmission(stale, IDENTITY, null));
        assertEquals(first.publisher(), admission.publisher());
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.readForAdmission(stale, IDENTITY, "foreign"));
        assertEquals(first.publisher(),
                RuntimeObservation.readForAdmission(observation(), IDENTITY, admission.publisher()).publisher());
        assertEquals(first.publisher(), RuntimeObservation.read(observation(), IDENTITY, first.publisher()).publisher());
        file.getAsJsonObject("data").addProperty("generationId", "foreign");
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.read(stale, IDENTITY, first.publisher()));
    }

    @Test void exactPassiveMetadataRaceCanBeRetriedOnlyInTheExpectedBoot() throws Exception {
        JsonObject input = observation();
        JsonObject data = input.getAsJsonObject("data");
        data.addProperty("complete", false);
        data.addProperty("runtime_current", false);
        data.getAsJsonObject("files").add("runtime", RunFiles.parse("""
            {"status":"unavailable","current":false,
             "provenance":{"status":"unavailable","reason":"runtime-unavailable"},
             "errors":[{"check":"3","code":"PASSIVE_READ_UNAVAILABLE","stage":"metadata"}]}
            """));
        assertThrows(RuntimeObservation.Unavailable.class, () -> RuntimeObservation.read(input, IDENTITY));
        JsonObject foreign = input.deepCopy();
        foreign.addProperty("boot_id", "other");
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.read(foreign, IDENTITY));
        runtimeFile(input).getAsJsonArray("errors").get(0).getAsJsonObject()
                .addProperty("code", "OWNER_CHANGED");
        assertThrowsExactly(IOException.class, () -> RuntimeObservation.read(input, IDENTITY));
    }

    @Test void deferredSelectionAllowsOnlyDesiredStartupToChangeWhileLiveTargetsStayCurrent() throws Exception {
        var input = observation();
        input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").addProperty("startup", "interface");
        runtimeFile(input).getAsJsonObject("data").addProperty("startupMode", "interface");
        runtimeFile(input).getAsJsonObject("data").getAsJsonArray("injections").get(0).getAsJsonObject().addProperty("lifecycleGeneration", 123);
        var before = RuntimeObservation.read(input, IDENTITY);
        input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").addProperty("startup", "none");
        var after = RuntimeObservation.read(input, IDENTITY); after.requireDeferredFrom(before);
        assertEquals("none", after.startup()); assertEquals("interface", after.runningStartup());
        runtimeFile(input).getAsJsonObject("data").getAsJsonArray("injections").get(0).getAsJsonObject().addProperty("lifecycleGeneration", 124);
        assertThrows(IOException.class, () -> RuntimeObservation.read(input, IDENTITY).requireDeferredFrom(before));
        runtimeFile(input).getAsJsonObject("data").getAsJsonArray("injections").get(0).getAsJsonObject().addProperty("state", "waiting");
        assertThrows(IOException.class, () -> RuntimeObservation.read(input, IDENTITY).requireDeferredFrom(before));
    }
    @Test void rejectsDuplicateTargetRowsAndUnboundedPublisherIdentity() throws Exception {
        JsonObject duplicate = observation(); var rows = runtimeFile(duplicate).getAsJsonObject("data").getAsJsonArray("injections");
        rows.add(rows.get(0).deepCopy());
        assertThrows(IOException.class, () -> RuntimeObservation.read(duplicate, IDENTITY));
        JsonObject overflow = observation(); runtimeFile(overflow).getAsJsonObject("data").getAsJsonObject("publisher")
            .addProperty("daemonStarttime", "18446744073709551616");
        assertThrows(IOException.class, () -> RuntimeObservation.read(overflow, IDENTITY));
    }
}
