package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import voyemu.journal.Journal;

class LauncherDragTest {
    @TempDir Path directory;
    private static final VoyemuClient.Session SESSION=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));
    static JsonObject observation() throws Exception {
        return RunFiles.parse("""
            {"format":"voyemu.ui.v1","profile":"profile","session_id":"SESSION","boot_id":"boot","source_instance":"source","revision":"1","screens":[{
             "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
             "windows":[{"id":"window","type":1,"bounds":{"x":0,"y":0,"width":1920,"height":720},"title":"Launcher","package":"com.qinggan.app.launcher"}],
             "nodes":[{"id":"fixture","window_id":"window","resource_id":"com.qinggan.app.launcher:id/item","package":"com.qinggan.app.launcher",
             "class":"android.widget.FrameLayout","text":"Voboost Fixture","visible":true,"enabled":true,"clickable":false,"long_clickable":true,"actionable_candidate":false,
             "bounds":{"x":1200,"y":400,"width":160,"height":100},"clipped_bounds":{"x":1200,"y":400,"width":160,"height":100}}]}]}
            """.replace("SESSION",SESSION.id()));
    }
    private static UiTarget target(JsonObject ui) throws IOException { return UiTarget.selectLauncherDrag(ui,"profile",SESSION,node->true); }
    private static VoyemuClient.Attempt request(VoyemuClient client, int page) throws Exception {
        return client.dragLauncher("b".repeat(32),"profile",SESSION,target(observation()),page,List.of(1200,400,1360,500),List.of(200,200,360,300),List.of(100,100,1800,650));
    }
    @Test void longPressAndAtMostTwoEdgeTransitionsStayWithinThePublicTwoSecondBound() throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder must not dispatch");});
        for(int page=0;page<=2;page++) {
            JsonObject value=RunFiles.parse(request(client,page).json()); LauncherDrag.validate(value);
            var points=value.getAsJsonArray("points"); assertEquals(page==0?14:page+4,points.size());
            assertEquals(600,points.get(1).getAsJsonObject().get("at_ms").getAsInt());
            assertTrue(points.get(points.size()-1).getAsJsonObject().get("at_ms").getAsInt()<=2000);
            assertEquals(1280,points.get(0).getAsJsonObject().get("x").getAsInt());
        }
        assertThrows(IllegalArgumentException.class,()->request(client,3));
        assertThrows(IllegalArgumentException.class,()->client.tap("c".repeat(32),"profile",SESSION,target(observation())));
    }
    @Test void foreignClippedPartialOrNonDraggableNodesCannotAcquireLauncherAuthority() throws Exception {
        for(String field:List.of("package","visible","enabled","long_clickable")) {
            JsonObject ui=observation(); JsonObject node=ui.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject();
            if(field.equals("package")) node.addProperty(field,"foreign.app"); else node.addProperty(field,false);
            assertThrows(IOException.class,()->target(ui));
        }
        JsonObject clipped=observation();clipped.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("clipped_bounds").addProperty("width",100);
        assertThrows(IOException.class,()->target(clipped));
        JsonObject partial=observation();partial.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("complete",false);assertThrows(IOException.class,()->target(partial));
    }
    @Test void retainedFiniteDragCanOnlyLookupItsOriginalOperationAndNeverReplay() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{assertEquals("operation.get",RunFiles.parse(bytes).get("op").getAsString());reads.incrementAndGet();return new VoyemuClient.ProcessResult(1,false,"","unavailable");});
        var original=request(client,2);String scope="session:"+SESSION.id();Path path=Files.createDirectory(directory.resolve("journal"));
        try(Journal journal=Journal.open(path)){journal.reserve(scope,"machine",original.requestId(),original.json().getBytes(StandardCharsets.UTF_8));}
        try(Journal journal=Journal.open(path)) {
            var retained=client.reserved(journal,scope,"machine");assertEquals(original.json(),retained.json());
            assertEquals(VoyemuClient.State.UNKNOWN,client.executeReserved(retained,Duration.ofSeconds(1),journal,"machine").state());
            assertEquals(0,reads.get()); assertFalse(Files.exists(path.resolve("dispatch")));
            client.execute(client.lookupReserved("c".repeat(32),journal,"machine",retained),Duration.ofSeconds(1));
        }
        assertEquals(1,reads.get());
    }
    @Test void changedHoldInterpolationOrPageDropTraceIsRejectedBeforeAnyDispatch() throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("validation must not dispatch");});
        for(int page=0;page<=2;page++) {
            JsonObject value=RunFiles.parse(request(client,page).json());value.getAsJsonArray("points").get(1).getAsJsonObject().addProperty("at_ms",500);
            assertThrows(IllegalArgumentException.class,()->LauncherDrag.validate(value));
        }
    }
}
