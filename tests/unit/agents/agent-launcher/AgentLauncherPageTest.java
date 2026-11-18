package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

/** Current producer-shaped native receipts; no Launcher execution or application effect is claimed. */
class AgentLauncherPageTest {
    private static final String NONCE = "a".repeat(64), BOOT = "00000000-0000-0000-0000-000000000001";
    static LauncherPage.Identity identity() { return new LauncherPage.Identity(BOOT,"2","3","42","4","5","generation","catalog","18446744073709551615"); }
    private static JsonArray bounds() { JsonArray result = new JsonArray(); for (int value : new int[]{10,20,110,120}) result.add(value); return result; }
    static JsonObject receipt() throws Exception {
        JsonObject value = RunFiles.parse("{\"schemaVersion\":1,\"requestId\":\"" + NONCE + "\",\"bootId\":\"" + BOOT + "\",\"daemonPid\":\"2\",\"daemonStarttime\":\"3\",\"targetPid\":\"42\",\"targetStarttime\":\"4\",\"endpointEpoch\":\"5\",\"generationId\":\"generation\",\"catalogSha256\":\"catalog\",\"documentGeneration\":18446744073709551615,\"captureStartedBootMs\":10,\"captureFinishedBootMs\":20,\"outcome\":\"observed\",\"reason\":\"observed\"}");
        JsonObject observation = RunFiles.parse("{\"outerCurrentItem\":1,\"firstPosition\":0,\"pageCount\":1,\"offsetX\":0,\"itemCount\":2,\"scrollState\":0,\"computingLayout\":false}");
        JsonObject window = RunFiles.parse("{\"activityClass\":\"com.qinggan.app.launcher.activity.MainActivity\",\"windowType\":1,\"displayId\":0,\"focused\":true,\"attached\":true,\"shown\":true,\"token\":\"actual-token\"}"); window.add("bounds", bounds());
        JsonObject grid = RunFiles.parse("{\"resource\":\"com.qinggan.app.launcher:id/all_app_view\",\"className\":\"com.qinggan.launcher.base.allapp.AllAppBarView\",\"layoutManagerClass\":\"com.qinggan.launcher.base.custom.PagerGridLayoutManager\"}"); grid.add("bounds", bounds());
        JsonArray items = new JsonArray();
        for (int index = 0; index < 2; index++) { JsonObject item = new JsonObject(); item.addProperty("position",index); item.addProperty("label",index == 0 ? "Stock" : "Voboost Fixture"); item.add("bounds",bounds()); items.add(item); }
        observation.add("window",window); observation.add("grid",grid); observation.add("items",items); value.add("observation",observation); return value;
    }
    @Test void admitsCurrentClosedProducerFieldsWithoutLosingU64DocumentGeneration() throws Exception {
        LauncherPage page = LauncherPage.read(receipt().toString(), NONCE, identity());
        assertTrue(page.beginning()); assertEquals(2,page.count()); assertEquals("Voboost Fixture",page.items().get(1).label());
        assertEquals("18446744073709551615",page.receipt().get("documentGeneration").getAsString());
        JsonObject escaped = page.receipt(); escaped.remove("observation"); assertNotNull(page.receipt().get("observation"));
    }
    @Test void wrongNonceBootTargetPublisherAndAuthorityCannotBecomeAValidPage() throws Exception {
        for (String field : List.of("requestId","bootId","daemonPid","daemonStarttime","targetPid","targetStarttime","endpointEpoch","generationId","catalogSha256")) {
            JsonObject value = receipt(); value.addProperty(field,"foreign"); assertThrows(IOException.class,()->LauncherPage.read(value.toString(),NONCE,identity()),field);
        }
        JsonObject rounded = receipt(); rounded.addProperty("documentGeneration",18446744073709551616.0);
        assertThrows(IOException.class,()->LauncherPage.read(rounded.toString(),NONCE,identity()));
    }
    @Test void unavailablePartialBusyOrDuplicatePositionsNeverBecomeEmptyOrCompleteOrder() throws Exception {
        JsonObject unavailable = receipt(); unavailable.remove("observation"); unavailable.addProperty("outcome","unavailable"); unavailable.addProperty("reason","no-grid");
        assertThrows(IOException.class,()->LauncherPage.read(unavailable.toString(),NONCE,identity()));
        for (String field : List.of("computingLayout","scrollState","firstPosition","pageCount","itemCount")) {
            JsonObject value = receipt(); JsonObject observation = value.getAsJsonObject("observation");
            if (field.equals("computingLayout")) observation.addProperty(field,true); else observation.addProperty(field,99);
            assertThrows(IOException.class,()->LauncherPage.read(value.toString(),NONCE,identity()),field);
        }
        JsonObject duplicate = receipt(); duplicate.getAsJsonObject("observation").getAsJsonArray("items").get(1).getAsJsonObject().addProperty("position",0);
        assertThrows(IOException.class,()->LauncherPage.read(duplicate.toString(),NONCE,identity()));
        JsonObject partial = receipt(); partial.getAsJsonObject("observation").getAsJsonArray("items").remove(1);
        assertThrows(IOException.class,()->LauncherPage.read(partial.toString(),NONCE,identity()));
    }
    @Test void duplicateJsonFieldsStaleRequestsAndProcessChangesFailBeforeMovement() throws Exception {
        JsonObject firstValue=receipt(); LauncherPage first=LauncherPage.read(firstValue.toString(),NONCE,identity());
        assertThrows(IOException.class,()->first.requireSameLifetime(first));
        String duplicate=firstValue.toString().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");
        assertThrows(IOException.class,()->LauncherPage.read(duplicate,NONCE,identity()));
        JsonObject next=receipt(); next.addProperty("requestId","b".repeat(64)); next.addProperty("captureStartedBootMs",21); next.addProperty("captureFinishedBootMs",22);
        LauncherPage.read(next.toString(),"b".repeat(64),identity()).requireSameLifetime(first);
        next.addProperty("captureStartedBootMs",19); assertThrows(IOException.class,()->LauncherPage.read(next.toString(),"b".repeat(64),identity()).requireSameLifetime(first));
    }    @Test void currentCatalogLauncherIdBindsTheActualPublishedEndpoint() throws Exception {
        JsonObject input=DaemonRuntimeObservationTest.observation();
        JsonObject row=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonArray("injections").get(0).getAsJsonObject();
        row.add("currentEndpoint",RunFiles.parse("{\"targetPid\":\"42\",\"targetStarttime\":\"4\",\"endpointEpoch\":\"5\",\"generationId\":\"generation\",\"catalogSha256\":\"catalog\",\"desiredDocumentGeneration\":\"1\"}"));
        var identity=LauncherPage.identity(RuntimeObservation.read(input,DaemonRuntimeObservationTest.IDENTITY),new ru.voboost.tests.installer.cli.StartupObservation.ProcessIdentity(42,"4",LauncherPage.OWNER));
        assertEquals("42",identity.pid());assertEquals("5",identity.epoch());assertEquals("1",identity.document());
    }

}
