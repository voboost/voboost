package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import voyemu.journal.Journal;

class LauncherPageSwipeTest {
    @TempDir Path directory;
    private static final VoyemuClient.Session SESSION=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));
    private static JsonObject ui()throws Exception {
        JsonObject ui=LauncherDragTest.observation(),node=ui.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject();
        node.addProperty("resource_id","com.qinggan.app.launcher:id/all_app_view");node.addProperty("class","androidx.recyclerview.widget.RecyclerView");node.addProperty("text","");
        for(String field:new String[]{"bounds","clipped_bounds"}) { JsonObject b=node.getAsJsonObject(field);b.addProperty("x",100);b.addProperty("y",100);b.addProperty("width",1700);b.addProperty("height",550); }return ui;
    }
    private static UiTarget grid(JsonObject ui)throws IOException{return UiTarget.selectLauncherPage(ui,"profile",SESSION);}
    @Test void exactObservedGridProducesOnlyOneFiniteForwardOrReverseTrace()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder cannot dispatch");});
        for(boolean next:new boolean[]{true,false}) {
            JsonObject request=RunFiles.parse(client.pageLauncher("c".repeat(32),"profile",SESSION,grid(ui()),next).json());LauncherPageSwipe.validate(request);
            var points=request.getAsJsonArray("points");assertEquals(11,points.size());assertEquals(400,points.get(10).getAsJsonObject().get("at_ms").getAsInt());
            assertEquals(next?1545:355,points.get(0).getAsJsonObject().get("x").getAsInt());assertEquals(next?355:1545,points.get(10).getAsJsonObject().get("x").getAsInt());
        }
        assertThrows(IllegalArgumentException.class,()->client.tap("c".repeat(32),"profile",SESSION,grid(ui())));
    }
    @Test void foreignGridPartialCoverageAndAlteredTraceFailClosed()throws Exception {
        JsonObject foreign=ui();foreign.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("resource_id","com.qinggan.app.launcher:id/other");assertThrows(IOException.class,()->grid(foreign));
        JsonObject partial=ui();partial.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("complete",false);assertThrows(IOException.class,()->grid(partial));
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder cannot dispatch");});
        JsonObject request=RunFiles.parse(client.pageLauncher("c".repeat(32),"profile",SESSION,grid(ui()),true).json());request.getAsJsonArray("points").get(5).getAsJsonObject().addProperty("x",1000);
        assertThrows(IllegalArgumentException.class,()->LauncherPageSwipe.validate(request));
    }
    @Test void retainedPageMovementOnlyAllowsExplicitReadOnlyLookup()throws Exception {
        AtomicInteger reads=new AtomicInteger();VoyemuClient client=new VoyemuClient((bytes,timeout)->{assertEquals("operation.get",RunFiles.parse(bytes).get("op").getAsString());reads.incrementAndGet();return new VoyemuClient.ProcessResult(1,false,"","unavailable");});
        var original=client.pageLauncher("c".repeat(32),"profile",SESSION,grid(ui()),true);String scope="session:"+SESSION.id();Path path=Files.createDirectory(directory.resolve("journal"));
        try(Journal journal=Journal.open(path)){journal.reserve(scope,"machine",original.requestId(),original.json().getBytes(StandardCharsets.UTF_8));}
        try(Journal journal=Journal.open(path)) { var retained=client.reserved(journal,scope,"machine");assertEquals(original.json(),retained.json());
            assertEquals(VoyemuClient.State.UNKNOWN,client.executeReserved(retained,Duration.ofSeconds(1),journal,"machine").state());assertEquals(0,reads.get());
            client.execute(client.lookupReserved("d".repeat(32),journal,"machine",retained),Duration.ofSeconds(1));assertEquals(1,reads.get()); }
    }
}
