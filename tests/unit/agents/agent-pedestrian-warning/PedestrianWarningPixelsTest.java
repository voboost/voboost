package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.PixelImage;

class PedestrianWarningPixelsTest {
    @TempDir Path root;
    private int serial;
    private static final String SESSION="a".repeat(32),URI="kzb://light_normal/Textures/light_低速行驶提示音关闭";
    private BufferedImage glyph(){var b=new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);for(int y=2;y<10;y++)for(int x=2;x<10;x++)if(x==2||y==2||x==9||y==9||x==y)b.setRGB(x,y,0xffffbd00);return b;}
    private BufferedImage frame(boolean shown,int left){var b=new BufferedImage(100,50,BufferedImage.TYPE_INT_RGB);for(int y=0;y<50;y++)for(int x=0;x<100;x++)b.setRGB(x,y,0xff102030);if(shown){var g=glyph();for(int y=0;y<12;y++)for(int x=0;x<12;x++)if((g.getRGB(x,y)>>>24)>0)b.setRGB(left+x,10+y,g.getRGB(x,y));}return b;}
    private PedestrianWarningPixels.Artwork artwork()throws Exception {Path p=root.resolve("artwork-"+(serial++)+".png");ImageIO.write(glyph(),"png",p.toFile());return new PedestrianWarningPixels.Artwork(URI,PixelImage.read(p,12,12));}
    private Path capture(BufferedImage image,long seq,boolean fresh)throws Exception {
        Path p=Files.createDirectory(root.resolve("capture-"+(serial++)));ImageIO.write(image,"png",p.resolve("cluster.png").toFile());
        JsonObject m=new JsonObject();m.addProperty("schema",1);for(String k:new String[]{"accepted","qnx_frame_verified","exported_frame_verified","cluster_powered"})m.addProperty(k,true);m.addProperty("session_id",SESSION);m.addProperty("qnx_generation","1");
        JsonObject f=new JsonObject();f.addProperty("screen","cluster");f.addProperty("source","qnx");f.addProperty("source_clock_domain","qnx.frame_transport");f.addProperty("derived",false);f.addProperty("file","cluster.png");f.addProperty("sequence",Long.toString(seq));f.addProperty("source_timestamp_ns",Long.toString(seq*100));
        for(String k:new String[]{"width","source_width"})f.addProperty(k,image.getWidth());for(String k:new String[]{"height","source_height"})f.addProperty(k,image.getHeight());JsonObject r=new JsonObject();r.addProperty("x",0);r.addProperty("y",0);r.addProperty("width",image.getWidth());r.addProperty("height",image.getHeight());f.add("rect",r);JsonObject age=new JsonObject();age.addProperty("newer_guest_frame_proven",fresh);f.add("freshness",age);JsonArray frames=new JsonArray();frames.add(f);m.add("frames",frames);Files.writeString(p.resolve("manifest.json"),m+"\n");return p;
    }
    private PedestrianWarningPixels.Frame read(BufferedImage image,long seq)throws Exception {return PedestrianWarningPixels.readCapture(capture(image,seq,true),SESSION,"1","classic",1);}
    private Path regionCapture(long sequence) throws Exception {
        Path path = capture(glyph(), sequence, true);
        var manifest = com.google.gson.JsonParser.parseString(Files.readString(path.resolve("manifest.json"))).getAsJsonObject();
        var frame = manifest.getAsJsonArray("frames").get(0).getAsJsonObject();
        frame.addProperty("derived", true);
        frame.addProperty("source_width", 1920);
        frame.addProperty("source_height", 720);
        frame.getAsJsonObject("rect").addProperty("x", 841);
        frame.getAsJsonObject("rect").addProperty("y", 102);
        Files.writeString(path.resolve("manifest.json"), manifest + "\n");
        return path;
    }
    private JsonObject rootIntent(boolean armed)throws Exception {
        JsonObject proof=com.google.gson.JsonParser.parseString("{\"schema\":2,\"source\":\"voboost\",\"status\":\"ready\",\"boot_id\":\""+BOOT+"\",\"data\":{\"root\":\"present\",\"root_proof\":\"fixed-path-owner-mode-boot-fenced\",\"files\":{\"state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed\":{\"status\":\"present\",\"current\":true,\"proof\":\"same-boot-retained-file\",\"revision\":\"source-revision\",\"data\":"+armed+"}}}}").getAsJsonObject();
        return proof;
    }
    private JsonObject oemProof() throws Exception {
        JsonObject proof = proof();
        BufferedImage oemGlyph=new BufferedImage(46,46,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<46;y++)for(int x=0;x<46;x++)if(x==y||x==0||y==0||x==45||y==45)oemGlyph.setRGB(x,y,0xffffbd00);
        Path artwork=root.resolve("oem-artwork-"+(serial++)+".png");ImageIO.write(oemGlyph,"png",artwork.toFile());
        proof.getAsJsonObject("artwork").addProperty("rgbaPng",artwork.toString());
        proof.getAsJsonObject("artwork").addProperty("width",46);proof.getAsJsonObject("artwork").addProperty("height",46);
        proof.addProperty("kind", "pw-saved-setting-rendered-regions");
        proof.addProperty("stimulusSource", "virtual-mcu");
        proof.remove("phases");
        proof.addProperty("initialNativeEvidence", root.resolve("native-initial.json").toString());
        JsonObject region = com.google.gson.JsonParser.parseString("{\"x\":841,\"y\":102,\"width\":46,\"height\":46}").getAsJsonObject();
        proof.add("region", region);
        JsonObject cycles = new JsonObject(); int sequence = 4;
        for (String control : java.util.List.of("settings", "drawer")) {
            JsonObject phases = new JsonObject();
            for (String name : java.util.List.of("off", "on", "off-restored")) {
                boolean on = name.equals("on");
                BufferedImage image = new BufferedImage(46,46,BufferedImage.TYPE_INT_RGB);
                Path directory = capture(image, sequence, true);
                JsonObject manifest = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
                JsonObject frame = manifest.getAsJsonArray("frames").get(0).getAsJsonObject();
                frame.addProperty("derived", true); frame.addProperty("source_width",1920); frame.addProperty("source_height",720);
                frame.add("rect", region.deepCopy());
                Files.writeString(directory.resolve("manifest.json"), manifest + "\n");
                JsonObject phase = new JsonObject(); phase.addProperty("captureDir",directory.toString());
                phase.addProperty("agentEnabled",true);phase.addProperty("actualVsp",on?2:1);
                phase.addProperty("inputEvidence",root.resolve("input-"+sequence+".json").toString());
                phase.addProperty("logArchive",root.resolve("logs-"+sequence+".tar.xz").toString());
                JsonObject can = can(30+sequence);
                can.getAsJsonObject("values").getAsJsonObject(ru.voboost.voyemu.VehicleCan.PW).addProperty("raw",on?2:1);
                phase.add("can",can);
                JsonObject nativeEvidence = com.google.gson.JsonParser.parseString("""
                    {"publisher":"owner","nativeBoot":"boot","nativeDaemonEpoch":"epoch","nativeGeneration":"generation",
                     "observationRevision":1,"observedAdvances":2,"snapshot":{"deliveryState":"idle","agents":[
                     {"id":"vehicle-drive-pedestrian-warning","enabled":true,"state":"applied","targets":[{"id":"vehicle-drive-pedestrian-warning",
                     "targetProcess":"light","state":"applied","reason":"effect","process":{"pid":42}}]}]}}
                    """).getAsJsonObject();
                nativeEvidence.addProperty("observationRevision",sequence);                JsonObject process = nativeEvidence.getAsJsonObject("snapshot").getAsJsonArray("agents").get(0).getAsJsonObject()
                        .getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("process");
                process.addProperty("birthIdentity","a".repeat(64));
                process.addProperty("endpointEpoch","b".repeat(64));
                process.addProperty("lifecycleGeneration",1);

                JsonObject target = nativeEvidence.getAsJsonObject("snapshot").getAsJsonArray("agents").get(0).getAsJsonObject()
                        .getAsJsonArray("targets").get(0).getAsJsonObject();
                target.addProperty("reason","PW indicator hidden by saved setting");
                if(on) {phase.add("restoreRootBefore",rootIntent(false));phase.add("restoreRootAfter",rootIntent(false));}
                phase.add("native",nativeEvidence);phases.add(name,phase);sequence++;
            }
            cycles.add(control,phases);
        }
        proof.add("cycles",cycles);
        JsonObject stock=new JsonObject();
        for(String field:java.util.List.of("sessionId","androidBootId","qnxGeneration","theme"))stock.add(field,proof.get(field).deepCopy());
        stock.addProperty("androidBootId","22222222-2222-2222-2222-222222222222");
        stock.addProperty("absenceEvidence",root.resolve("absence.json").toString());JsonObject phases=new JsonObject();int seq=1;
        for(String name:java.util.List.of("visible","absent","restored")) {
            Path directory=capture(name.equals("absent")?new BufferedImage(46,46,BufferedImage.TYPE_INT_RGB):oemGlyph,seq,true);
            JsonObject manifest=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
            JsonObject frame=manifest.getAsJsonArray("frames").get(0).getAsJsonObject();
            frame.addProperty("derived",true);frame.addProperty("source_width",1920);frame.addProperty("source_height",720);frame.add("rect",region.deepCopy());
            Files.writeString(directory.resolve("manifest.json"),manifest+"\n");
            JsonObject phase=new JsonObject();phase.addProperty("captureDir",directory.toString());phase.addProperty("inputEvidence",root.resolve("stock-input-"+seq+".json").toString());
            JsonObject can=can(seq+10);can.addProperty("boot_id",stock.get("androidBootId").getAsString());can.addProperty("guest_boot_id",stock.get("androidBootId").getAsString());can.getAsJsonObject("values").getAsJsonObject(ru.voboost.voyemu.VehicleCan.PW).addProperty("raw",name.equals("absent")?2:1);
            phase.add("can",can);phases.add(name,phase);seq++;
        }
        stock.add("phases",phases);
        JsonObject owner=new JsonObject();owner.addProperty("runId","33333333-3333-3333-3333-333333333333");owner.addProperty("machine",root.toString());owner.addProperty("work",root.toString());owner.addProperty("sessionId",SESSION);owner.addProperty("machineSeed","same-owned-machine");
        stock.add("owner",owner.deepCopy());proof.add("owner",owner.deepCopy());proof.add("stockBaseline",stock);
        JsonObject operation=new JsonObject();operation.addProperty("state","verified");operation.addProperty("kind","INSTALL");operation.add("runId",owner.get("runId"));operation.addProperty("sessionId",SESSION);
        JsonObject input=new JsonObject();input.add("priorBoot",stock.get("androidBootId"));operation.add("input",input);
        JsonObject preparation=new JsonObject();preparation.add("priorBoot",stock.get("androidBootId"));preparation.addProperty("finalBoot",BOOT);
        for(String key:java.util.List.of("readiness","enforcing","acceptedJournal"))preparation.addProperty(key,true);preparation.addProperty("receipts",12);
        Path preparationPath=root.resolve("preparation-"+(serial++)+".json");Files.writeString(preparationPath,preparation.toString());JsonArray evidence=new JsonArray();evidence.add(root.relativize(preparationPath).toString());operation.add("evidence",evidence);
        Path operations=Files.createDirectories(root.resolve("operations"));Path operationPath=operations.resolve("install-"+(serial++)+".json");Files.writeString(operationPath,operation.toString());
        JsonObject bridge=new JsonObject();bridge.add("owner",owner.deepCopy());bridge.add("operation",operation);bridge.add("preparation",preparation);
        bridge.addProperty("qnxGeneration","1");bridge.add("restarts",new JsonArray());bridge.addProperty("operationPath",operationPath.toString());bridge.addProperty("preparationPath",preparationPath.toString());proof.add("installBridge",bridge);return proof;
    }
    @Test void oemEpochBridgeAllowsRestartedSourceButRejectsMissingChainAndLegacyPhaseNames() throws Exception {
        JsonObject changed=oemProof();changed.addProperty("qnxGeneration","2");changed.getAsJsonObject("installBridge").addProperty("qnxGeneration","2");int sequence=1;
        for(String control:java.util.List.of("settings","drawer"))for(String phase:java.util.List.of("off","on","off-restored")) {
            Path directory=Path.of(changed.getAsJsonObject("cycles").getAsJsonObject(control).getAsJsonObject(phase).get("captureDir").getAsString());
            Path file=directory.resolve("manifest.json");JsonObject manifest=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();manifest.addProperty("qnx_generation","2");
            JsonObject frame=manifest.getAsJsonArray("frames").get(0).getAsJsonObject();frame.addProperty("sequence",Integer.toString(sequence));frame.addProperty("source_timestamp_ns",Integer.toString(sequence*100));sequence++;
            Files.writeString(file,manifest.toString());
        }
        assertNotNull(PedestrianWarningPixels.readProof(changed));
        JsonObject missing=changed.deepCopy();missing.remove("installBridge");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing));
        JsonObject foreign=changed.deepCopy();foreign.getAsJsonObject("owner").addProperty("machineSeed","another-machine");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(foreign));
        JsonObject wrongBoot=changed.deepCopy();wrongBoot.addProperty("androidBootId","44444444-4444-4444-4444-444444444444");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(wrongBoot));
        JsonObject legacy=changed.deepCopy();JsonObject cycle=legacy.getAsJsonObject("cycles").getAsJsonObject("settings");cycle.add("visible",cycle.remove("off"));assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(legacy));
        JsonObject legacyStock=changed.deepCopy();JsonObject stock=legacyStock.getAsJsonObject("stockBaseline").getAsJsonObject("phases");stock.add("hidden",stock.remove("absent"));assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(legacyStock));
    }
    @Test void oemEpochBridgeReplaysVerifiedWarmAndFullRestartAndRejectsGaps() throws Exception {
        for(boolean cold:new boolean[]{false,true}) {
            JsonObject proof=oemProof(),bridge=proof.getAsJsonObject("installBridge"),preparation=bridge.getAsJsonObject("preparation");
            String installed="55555555-5555-5555-5555-555555555555";preparation.addProperty("finalBoot",installed);
            Files.writeString(Path.of(bridge.get("preparationPath").getAsString()),preparation.toString());
            JsonObject operation=new JsonObject();operation.addProperty("state","verified");operation.addProperty("kind","REBOOT");operation.add("runId",proof.getAsJsonObject("owner").get("runId"));operation.addProperty("sessionId",SESSION);
            JsonObject input=new JsonObject();input.addProperty("priorBoot",installed);operation.add("input",input);
            JsonObject receipt=new JsonObject();receipt.addProperty("session_id",SESSION);receipt.addProperty("status","completed");
            if(cold) {
                proof.addProperty("qnxGeneration","2");
                for(String control:java.util.List.of("settings","drawer"))for(String phase:java.util.List.of("off","on","off-restored")) {
                    Path capture=Path.of(proof.getAsJsonObject("cycles").getAsJsonObject(control).getAsJsonObject(phase).get("captureDir").getAsString(),"manifest.json");
                    JsonObject manifest=com.google.gson.JsonParser.parseString(Files.readString(capture)).getAsJsonObject();manifest.addProperty("qnx_generation","2");Files.writeString(capture,manifest.toString());
                }
                receipt.addProperty("scope","android_qnx_cold_boot_changed");receipt.addProperty("mode","cold");
                for(String field:java.util.List.of("authority_retained","old_processes_stopped","guest_acknowledged"))receipt.addProperty(field,true);
                JsonObject after=new JsonObject();after.addProperty("android_boot_id",BOOT);after.addProperty("android_pid",100);
                JsonObject qnx=new JsonObject();qnx.addProperty("kind","owned_cold_incarnation");qnx.addProperty("qemu_pid",200);qnx.addProperty("generation","2");after.add("qnx",qnx);receipt.add("after",after);
            } else {
                receipt.addProperty("scope","guest_boot_changed");receipt.addProperty("boot_id_before",installed);receipt.addProperty("boot_id",BOOT);receipt.addProperty("android_pid_before",99);receipt.addProperty("android_pid",100);
            }
            Path receiptPath=root.resolve("restart-"+(serial++)+".json");Files.writeString(receiptPath,receipt.toString());JsonArray evidence=new JsonArray();evidence.add(root.relativize(receiptPath).toString());operation.add("evidence",evidence);
            Path operationPath=root.resolve("operations/restart-"+(serial++)+".json");Files.writeString(operationPath,operation.toString());
            JsonObject transition=new JsonObject();transition.add("operation",operation);transition.add("receipt",receipt);transition.addProperty("operationPath",operationPath.toString());transition.addProperty("receiptPath",receiptPath.toString());bridge.getAsJsonArray("restarts").add(transition);
            assertNotNull(PedestrianWarningPixels.readProof(proof));
            JsonObject gap=proof.deepCopy();gap.getAsJsonObject("installBridge").getAsJsonArray("restarts").remove(0);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(gap));
            operation.getAsJsonObject("input").addProperty("priorBoot","66666666-6666-6666-6666-666666666666");Files.writeString(operationPath,operation.toString());
            assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(proof));
        }
    }
    @Test void savedSettingStaysAppliedWhileAndroidChoiceIsOn() throws Exception {
        JsonObject proof=oemProof();
        assertNotNull(PedestrianWarningPixels.readProof(proof));
        for(String field:java.util.List.of("restoreRootBefore","restoreRootAfter")) {
            JsonObject armed=proof.deepCopy();armed.getAsJsonObject("cycles").getAsJsonObject("settings").getAsJsonObject("on").add(field,rootIntent(true));assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(armed));
            JsonObject missing=proof.deepCopy();missing.getAsJsonObject("cycles").getAsJsonObject("settings").getAsJsonObject("on").remove(field);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing));
        }
        JsonObject noApplied=proof.deepCopy();nativeTarget(noApplied,"settings","off").addProperty("state","waiting-ui");nativeTarget(noApplied,"settings","off").addProperty("reason","PW visual pass-through: live state unknown");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(noApplied));
        JsonObject wrongCan=proof.deepCopy();wrongCan.getAsJsonObject("cycles").getAsJsonObject("settings").getAsJsonObject("on").getAsJsonObject("can").getAsJsonObject("values").getAsJsonObject(ru.voboost.voyemu.VehicleCan.PW).addProperty("raw",1);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(wrongCan));
        for(String state:java.util.List.of("failed","reload-required")) {
            JsonObject failed=proof.deepCopy();nativeTarget(failed,"settings","on").addProperty("state",state);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(failed));
            JsonObject aggregate=proof.deepCopy();aggregate.getAsJsonObject("cycles").getAsJsonObject("settings").getAsJsonObject("on").getAsJsonObject("native").getAsJsonObject("snapshot").getAsJsonArray("agents").get(0).getAsJsonObject().addProperty("state",state);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(aggregate));
        }
    }
    @Test void oemProofRequiresStockBaselineAndRejectsGenericPassThrough() throws Exception {
        JsonObject missing=oemProof();missing.remove("stockBaseline");
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing));
        for(String reason:java.util.List.of("PW visual pass-through or owner transition","PW visual pass-through: stale live observation")) {
            JsonObject unknown=oemProof();nativeTarget(unknown,"settings","on").addProperty("reason",reason);
            assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(unknown));
        }
    }
    @Test void oemProofRejectsVisibleMaskedOffAndUnprovenStockGlyph() throws Exception {
        JsonObject proof=oemProof();
        Path stock=Path.of(proof.getAsJsonObject("stockBaseline").getAsJsonObject("phases").getAsJsonObject("visible").get("captureDir").getAsString(),"cluster.png");
        Path masked=Path.of(proof.getAsJsonObject("cycles").getAsJsonObject("drawer").getAsJsonObject("off").get("captureDir").getAsString(),"cluster.png");
        Files.copy(stock,masked,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(proof));
        JsonObject absent=oemProof();Path visible=Path.of(absent.getAsJsonObject("stockBaseline").getAsJsonObject("phases").getAsJsonObject("visible").get("captureDir").getAsString(),"cluster.png");
        ImageIO.write(new BufferedImage(46,46,BufferedImage.TYPE_INT_RGB),"png",visible.toFile());
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(absent));
    }
    @Test void oemRegionProofRequiresBothControlsAndLiveUnchangedAgent() throws Exception {
        JsonObject proof = oemProof();
        assertEquals(46,PedestrianWarningPixels.readProof(proof).glyph().width());
        var disabled = proof.deepCopy();disabled.getAsJsonObject("cycles").getAsJsonObject("drawer")
                .getAsJsonObject("on").addProperty("agentEnabled",false);
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(disabled));
        var missing = proof.deepCopy();missing.getAsJsonObject("cycles").remove("drawer");
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing));
        var pending = proof.deepCopy();pending.getAsJsonObject("cycles").getAsJsonObject("drawer").getAsJsonObject("on")
                .getAsJsonObject("native").getAsJsonObject("snapshot").addProperty("deliveryState","pending");
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(pending));
    }
    @Test void oemRegionProofRejectsUnchangedIconAndReplayedNativeRevision() throws Exception {
        JsonObject proof = oemProof();
        var settings = proof.getAsJsonObject("cycles").getAsJsonObject("settings");
        Path shown = Path.of(settings.getAsJsonObject("off").get("captureDir").getAsString(),"cluster.png");
        Path hidden = Path.of(settings.getAsJsonObject("on").get("captureDir").getAsString(),"cluster.png");
        ImageIO.write(new BufferedImage(46,46,BufferedImage.TYPE_INT_RGB) {{setRGB(0,0,0xffffbd00);}},"png",hidden.toFile());
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(proof));
        JsonObject replayed = oemProof();replayed.getAsJsonObject("cycles").getAsJsonObject("drawer").getAsJsonObject("off")
                .getAsJsonObject("native").addProperty("observationRevision",1);
        assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(replayed));
    }

    @Test void renderedSeriesRequiresANewNativeReceiptAtItsEnd() throws Exception {
        JsonObject proof = oemProof();
        var cycles = proof.getAsJsonObject("cycles");
        var drawer = cycles.getAsJsonObject("drawer");
        var first = drawer.getAsJsonObject("off").getAsJsonObject("native");
        var second = drawer.getAsJsonObject("on").getAsJsonObject("native");
        var last = drawer.getAsJsonObject("off-restored").getAsJsonObject("native");
        second.add("observationRevision", first.get("observationRevision").deepCopy());
        assertEquals(46, PedestrianWarningPixels.readProof(proof).glyph().width());
        last.add("observationRevision", second.get("observationRevision").deepCopy());
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readProof(proof));
    }

    private JsonObject nativeTarget(JsonObject proof, String control, String phase) {
        return proof.getAsJsonObject("cycles").getAsJsonObject(control).getAsJsonObject(phase)
                .getAsJsonObject("native").getAsJsonObject("snapshot").getAsJsonArray("agents")
                .get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject();
    }
    @Test void oemProofRequiresProcessLifetimeNotOnlyReusedPid() throws Exception {
        for (String field : java.util.List.of("birthIdentity","endpointEpoch","lifecycleGeneration")) {
            JsonObject missing = oemProof();
            for (String control : java.util.List.of("settings","drawer"))
                for (String phase : java.util.List.of("off","on","off-restored"))
                    nativeTarget(missing,control,phase).getAsJsonObject("process").remove(field);
            assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing),field);
            JsonObject changed = oemProof();
            var process = nativeTarget(changed,"drawer","off-restored").getAsJsonObject("process");
            if (field.equals("lifecycleGeneration")) process.addProperty(field,2);
            else process.addProperty(field,"c".repeat(64));
            assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(changed),field);
        }
    }
    @Test void oemProofRejectsForeignOwnerInEveryPhase() throws Exception {
        for (String control : java.util.List.of("settings","drawer")) {
            for (String phase : java.util.List.of("off","on","off-restored")) {
                for (String field : java.util.List.of("publisher","nativeBoot","nativeDaemonEpoch","nativeGeneration")) {
                    JsonObject proof = oemProof();
                    proof.getAsJsonObject("cycles").getAsJsonObject(control).getAsJsonObject(phase)
                            .getAsJsonObject("native").addProperty(field,"foreign");
                    assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(proof),control+"/"+phase+"/"+field);
                }
            }
        }
    }
    @Test void oemProofRejectsSubstitutedCaptureInEitherControl() throws Exception {
        for (String control : java.util.List.of("settings","drawer")) {
            for (String phase : java.util.List.of("off","on","off-restored")) {
                JsonObject proof = oemProof();
                var phases = proof.getAsJsonObject("cycles").getAsJsonObject(control);
                String replacement = phase.equals("on") ? "off" : "on";
                phases.getAsJsonObject(phase).add("captureDir",phases.getAsJsonObject(replacement).get("captureDir").deepCopy());
                assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(proof),control+"/"+phase);
            }
        }
    }

    @Test void regionReaderRequiresExactCoordinatesAndVerifiedSource() throws Exception {
        var region = new ru.voboost.voyemu.VoyemuClient.ClusterRegion(841, 102, 12, 12);
        Path valid = regionCapture(5);
        assertNotNull(PedestrianWarningPixels.readCapture(valid, SESSION, "1", "classic", 2, region));
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readCapture(valid, SESSION, "1", "classic", 1));
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readCapture(valid, SESSION, "1", "classic", 2,
                new ru.voboost.voyemu.VoyemuClient.ClusterRegion(840, 102, 12, 12)));
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readCapture(valid, SESSION, "2", "classic", 2, region));
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readCapture(valid, SESSION, "1", "classic", 0, region));
        var manifest = com.google.gson.JsonParser.parseString(Files.readString(valid.resolve("manifest.json"))).getAsJsonObject();
        manifest.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("freshness")
                .addProperty("newer_guest_frame_proven", false);
        Files.writeString(valid.resolve("manifest.json"), manifest + "\n");
        assertThrows(IOException.class, () -> PedestrianWarningPixels.readCapture(valid, SESSION, "1", "classic", 2, region));
    }

    @Test void originalGlyphDisappearsAndReturnsAtUniqueObservedRegion()throws Exception {
        var result=PedestrianWarningPixels.evaluate(artwork(),read(frame(true,30),1),read(frame(false,30),2),read(frame(true,30),3));assertEquals(new PedestrianWarningPixels.Region(30,10,12,12),result.glyph());assertTrue(result.changedGlyphPixels()>16);assertEquals(0,result.unrelatedChangedPixels());
    }
    @Test void propertyOnlyOrBlankOrWrongRestorationDoesNotPass()throws Exception {
        var reference=artwork();assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(frame(true,30),1),read(frame(true,30),2),read(frame(true,30),3)));
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(frame(false,30),1),read(frame(false,30),2),read(frame(false,30),3)));
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(frame(true,30),1),read(frame(false,30),2),read(frame(true,50),3)));
    }
    @Test void wrongGlyphAndUnrelatedChangedPixelsFail()throws Exception {
        var reference=artwork();var square=frame(false,30);for(int y=12;y<20;y++)for(int x=32;x<40;x++)square.setRGB(x,y,0xffffbd00);
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(square,1),read(frame(false,30),2),read(square,3)));
        var hidden=frame(false,30);for(int y=35;y<45;y++)for(int x=70;x<90;x++)hidden.setRGB(x,y,0xff000000);
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(frame(true,30),1),read(hidden,2),read(frame(true,30),3)));
    }
    @Test void repeatedSymbolsAreAmbiguous()throws Exception {
        var reference=artwork();var two=frame(true,30);var second=frame(true,60);for(int y=10;y<22;y++)for(int x=60;x<72;x++)two.setRGB(x,y,second.getRGB(x,y));
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(reference,read(two,1),read(frame(false,30),2),read(two,3)));
    }
    @Test void staleThemeCrossGenerationAndWrongCanRefuseBeforePixels()throws Exception {
        Path p=capture(frame(true,30),1,false);assertThrows(IOException.class,()->PedestrianWarningPixels.readCapture(p,SESSION,"1","classic",1));
        Path f=capture(frame(true,30),1,true);assertThrows(IOException.class,()->PedestrianWarningPixels.readCapture(f,SESSION,"2","classic",1));assertThrows(IOException.class,()->PedestrianWarningPixels.readCapture(f,SESSION,"1","classic",2));
        var a=read(frame(true,30),1);var b=PedestrianWarningPixels.readCapture(capture(frame(false,30),2,true),SESSION,"1","techno",1);assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(artwork(),a,b,read(frame(true,30),3)));
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(artwork(),a,read(frame(false,30),1),read(frame(true,30),3)));
    }
    @Test void hostDerivedMalformedAndForeignSourcesFailBeforeMatching()throws Exception {
        for(String changed:new String[]{"derived","source","qnx_frame_verified","file","sequence"}) {
            Path p=capture(frame(true,30),1,true);var m=com.google.gson.JsonParser.parseString(Files.readString(p.resolve("manifest.json"))).getAsJsonObject();var f=m.getAsJsonArray("frames").get(0).getAsJsonObject();
            switch(changed){case "derived"->f.addProperty("derived",true);case "source"->f.addProperty("source","host");case "qnx_frame_verified"->m.addProperty("qnx_frame_verified",false);case "file"->f.addProperty("file","../cluster.png");case "sequence"->f.addProperty("sequence","18446744073709551616");default->throw new AssertionError();}
            Files.writeString(p.resolve("manifest.json"),m+"\n");assertThrows(IOException.class,()->PedestrianWarningPixels.readCapture(p,SESSION,"1","classic",1),changed);
        }
    }

    @Test void aPartiallyHiddenGlyphIsNotDisappearance()throws Exception {
        var a=frame(true,30);var partial=frame(false,30);for(int y=10;y<22;y++)for(int x=30;x<36;x++)partial.setRGB(x,y,a.getRGB(x,y));
        assertThrows(IOException.class,()->PedestrianWarningPixels.evaluate(artwork(),read(a,1),read(partial,2),read(a,3)));
    }
    private static final String BOOT="11111111-1111-1111-1111-111111111111";
    private JsonObject can(long sample)throws IOException {
        return com.google.gson.JsonParser.parseString("""
        {"schema":1,"source":"android:qg.canbus","status":"ready","boot_id":"BOOT","guest_boot_id":"BOOT","session_id":"SESSION",
         "atomic_guest_state":false,"source_instance":"source-one","pid":123,"service_started_elapsed_ns":"10","revision":"2",
         "clock":{"domain":"android.elapsedRealtimeNanos","sampled_ns":"SAMPLE"},
         "host_interval":{"domain":"host.steady","clock_id":"host.steady:SESSION","start_ns":"100","end_ns":"110"},
         "values":{"vehicle_state.IVI_SOC_MODESET":{"status":"observed","raw":2,"basis":"ICanBusService.getVehicleState"},
                   "vehicle_state.HUM_VSP_FUNCTION_SW":{"status":"observed","raw":1,"basis":"ICanBusService.getVehicleState"}},
         "acc":{"status":"observed","raw":2,"basis":"ICanBusService.getAccStatus"},
         "transport":{"registration":"registered","binder_alive":true,"client_manager_cache":"not_observed"},
         "coverage":{"begins":"service_construction","delivery":"journal_replay","vehicle_state_setter":true,"other_feedback_paths":"partial","survives_process_death":false},
         "history":{"oldest_seq":"1","latest_seq":"2","dropped_count":"0","next_cursor":{"source_instance":"source-one","after_seq":"2"},"has_more":false,"gap":null},"events":[]}
        """.replace("BOOT",BOOT).replace("SESSION",SESSION).replace("SAMPLE",Long.toString(sample))).getAsJsonObject();
    }
    private JsonObject proof()throws Exception {
        Path png=root.resolve("proof-art-"+(serial++)+".png");ImageIO.write(glyph(),"png",png.toFile());var p=new JsonObject();p.addProperty("schemaVersion",1);p.addProperty("kind","pw-original-rendered-counterfactual");p.addProperty("sessionId",SESSION);p.addProperty("androidBootId",BOOT);p.addProperty("qnxGeneration","1");p.addProperty("theme","classic");
        var art=new JsonObject();art.addProperty("textureUri",URI);art.addProperty("rgbaPng",png.toString());art.addProperty("width",12);art.addProperty("height",12);p.add("artwork",art);var phases=new JsonObject();int seq=1;
        for(String name:java.util.List.of("visible","hidden","restored")){var phase=new JsonObject();phase.addProperty("captureDir",capture(frame(!name.equals("hidden"),30),seq,true).toString());phase.add("can",can(30+seq));phase.addProperty("agentEnabled",name.equals("hidden"));phase.addProperty("soundEnable",1);phases.add(name,phase);seq++;}p.add("phases",phases);return p;
    }
    @Test void typedProofReadsConcreteArtifactsAndDisallowsBooleanPass()throws Exception {
        var p=proof();assertEquals(new PedestrianWarningPixels.Region(30,10,12,12),PedestrianWarningPixels.readProof(p).glyph());var claimed=new JsonObject();claimed.addProperty("functionalAcceptance",true);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(claimed));
        p.addProperty("functionalAcceptance",true);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(p));
    }
    @Test void proofRejectsMissingAndWrongPhaseAndStimulus()throws Exception {
        var missing=proof();missing.getAsJsonObject("phases").remove("hidden");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(missing));
        var wrong=proof();wrong.getAsJsonObject("phases").getAsJsonObject("visible").addProperty("agentEnabled",true);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(wrong));
        var stimulus=proof();stimulus.getAsJsonObject("phases").getAsJsonObject("restored").addProperty("soundEnable",0);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(stimulus));
    }
    @Test void originalOffStimulusIsRequiredInEveryCounterfactualPhase()throws Exception {
        assertEquals(1,PedestrianWarningPixels.ORIGINAL_OFF_SOUND_ENABLE);
        for(String phase:java.util.List.of("visible","hidden","restored")) {
            var p=proof();p.getAsJsonObject("phases").getAsJsonObject(phase).addProperty("soundEnable",0);
            assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(p),phase);
        }
        for(String phase:java.util.List.of("hiddenBeforeMutation","visibleDisabled","hiddenReenabled")) {
            var p=postBootProof();p.getAsJsonObject("phases").getAsJsonObject(phase).addProperty("soundEnable",0);
            assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(p),phase);
        }
    }
    @Test void proofRejectsForeignCanWrongBootSourceAndReplayedSamples()throws Exception {
        for(String field:java.util.List.of("source","session_id","boot_id")){var p=proof();p.getAsJsonObject("phases").getAsJsonObject("hidden").getAsJsonObject("can").addProperty(field,"foreign");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(p),field);}
        var repeated=proof();var phases=repeated.getAsJsonObject("phases");phases.getAsJsonObject("hidden").add("can",phases.getAsJsonObject("visible").get("can").deepCopy());assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(repeated));
        var on=proof();on.getAsJsonObject("phases").getAsJsonObject("hidden").getAsJsonObject("can").getAsJsonObject("values").getAsJsonObject(ru.voboost.voyemu.VehicleCan.PW).addProperty("raw",2);assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(on));
    }
    @Test void proofRejectsReplayedFramePartialMaskAndConflictingCaptureBoot()throws Exception {
        var replay=proof();var phases=replay.getAsJsonObject("phases");phases.getAsJsonObject("hidden").add("captureDir",phases.getAsJsonObject("visible").get("captureDir").deepCopy());assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(replay));
        var partial=proof();Path hidden=Path.of(partial.getAsJsonObject("phases").getAsJsonObject("hidden").get("captureDir").getAsString());var a=frame(true,30);var b=frame(false,30);for(int y=10;y<22;y++)for(int x=30;x<36;x++)b.setRGB(x,y,a.getRGB(x,y));ImageIO.write(b,"png",hidden.resolve("cluster.png").toFile());assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(partial));
        var crossed=proof();Path directory=Path.of(crossed.getAsJsonObject("phases").getAsJsonObject("hidden").get("captureDir").getAsString());Path manifest=directory.resolve("manifest.json");var m=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();m.addProperty("android_boot_id","22222222-2222-2222-2222-222222222222");Files.writeString(manifest,m+"\n");assertThrows(IOException.class,()->PedestrianWarningPixels.readProof(crossed));
    }

    private JsonObject postBootProof()throws Exception {
        JsonObject p=proof();p.addProperty("kind","pw-postboot-rendered-counterfactual");JsonObject phases=new JsonObject();int seq=1;
        for(String name:java.util.List.of("hiddenBeforeMutation","visibleDisabled","hiddenReenabled")) {
            Path directory=capture(frame(name.equals("visibleDisabled"),30),seq,true);Path manifest=directory.resolve("manifest.json");
            JsonObject m=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();m.addProperty("android_boot_id",BOOT);Files.writeString(manifest,m+"\n");
            JsonObject phase=new JsonObject();phase.addProperty("captureDir",directory.toString());phase.add("can",can(30+seq));phase.addProperty("agentEnabled",!name.equals("visibleDisabled"));phase.addProperty("soundEnable",1);
            JsonObject status=new JsonObject();status.addProperty("ok",true);status.addProperty("stopped",false);status.addProperty("session_id",SESSION);status.addProperty("machine_seed","/owned/machine");status.addProperty("android_pid",123);status.addProperty("qnx_pid",456);JsonObject deliveries=new JsonObject(),qnx=new JsonObject();qnx.addProperty("generation",1);deliveries.add("qnx",qnx);status.add("deliveries",deliveries);
            JsonObject system=new JsonObject(),value=new JsonObject();system.addProperty("status","ready");system.addProperty("boot_id",BOOT);value.addProperty("boot_id",BOOT);value.addProperty("owned_android_pid",123);system.add("value",value);
            phase.add("beforeStatus",status);phase.add("beforeSystem",system);phase.add("afterSystem",system.deepCopy());phase.add("afterStatus",status.deepCopy());phases.add(name,phase);seq++;
        }
        p.add("phases",phases);return p;
    }
    @Test void postBootReplaysInitialHiddenAndDisableReenableWithoutCrossBootPixels()throws Exception {
        var p=postBootProof();assertEquals(new PedestrianWarningPixels.Region(30,10,12,12),PedestrianWarningPixels.readPostBootProof(p).glyph());
        var missing=postBootProof();missing.getAsJsonObject("phases").remove("visibleDisabled");assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(missing));
        var replay=postBootProof();var phases=replay.getAsJsonObject("phases");phases.getAsJsonObject("hiddenReenabled").add("captureDir",phases.getAsJsonObject("hiddenBeforeMutation").get("captureDir").deepCopy());assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(replay));
        var wrong=postBootProof();wrong.getAsJsonObject("phases").getAsJsonObject("visibleDisabled").addProperty("agentEnabled",true);assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(wrong));
        var foreign=postBootProof();foreign.addProperty("androidBootId","22222222-2222-2222-2222-222222222222");assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(foreign));
        var stale=postBootProof();Path staleDir=Path.of(stale.getAsJsonObject("phases").getAsJsonObject("hiddenBeforeMutation").get("captureDir").getAsString());Path staleManifest=staleDir.resolve("manifest.json");JsonObject staleJson=com.google.gson.JsonParser.parseString(Files.readString(staleManifest)).getAsJsonObject();staleJson.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("freshness").addProperty("newer_guest_frame_proven",false);Files.writeString(staleManifest,staleJson+"\n");assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(stale));
        var wrongPixels=postBootProof();Path wrongDir=Path.of(wrongPixels.getAsJsonObject("phases").getAsJsonObject("hiddenBeforeMutation").get("captureDir").getAsString());ImageIO.write(frame(true,30),"png",wrongDir.resolve("cluster.png").toFile());assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(wrongPixels));
        var noBoot=postBootProof();Path noBootDir=Path.of(noBoot.getAsJsonObject("phases").getAsJsonObject("hiddenBeforeMutation").get("captureDir").getAsString());Path noBootManifest=noBootDir.resolve("manifest.json");JsonObject noBootJson=com.google.gson.JsonParser.parseString(Files.readString(noBootManifest)).getAsJsonObject();noBootJson.remove("android_boot_id");Files.writeString(noBootManifest,noBootJson+"\n");assertNotNull(PedestrianWarningPixels.readPostBootProof(noBoot));
        var wrongFence=postBootProof();wrongFence.getAsJsonObject("phases").getAsJsonObject("hiddenBeforeMutation").getAsJsonObject("afterStatus").addProperty("qnx_pid",457);assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(wrongFence));
        var wrongSystem=postBootProof();wrongSystem.getAsJsonObject("phases").getAsJsonObject("hiddenBeforeMutation").getAsJsonObject("afterSystem").getAsJsonObject("value").addProperty("owned_android_pid",124);assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(wrongSystem));
        var bool=new JsonObject();bool.addProperty("functionalAcceptance",true);assertThrows(IOException.class,()->PedestrianWarningPixels.readPostBootProof(bool));
    }
    @Test void syntheticFullResolutionPostBootFrameUsesOpaqueAnchor()throws Exception {
        var hidden=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var visible=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);
        var largeGlyph=new BufferedImage(50,50,BufferedImage.TYPE_INT_ARGB);var ink=largeGlyph.createGraphics();ink.drawImage(glyph(),30,30,null);ink.dispose();Path largeFile=root.resolve("synthetic-full-resolution-glyph.png");ImageIO.write(largeGlyph,"png",largeFile.toFile());var largeArtwork=new PedestrianWarningPixels.Artwork(URI,PixelImage.read(largeFile,50,50));
        var g=visible.createGraphics();g.setColor(new java.awt.Color(0x102030));g.fillRect(0,0,1920,720);g.drawImage(largeGlyph,1200,300,null);g.dispose();
        var h=hidden.createGraphics();h.setColor(new java.awt.Color(0x102030));h.fillRect(0,0,1920,720);h.dispose();
        assertTimeout(java.time.Duration.ofSeconds(10),()->assertEquals(new PedestrianWarningPixels.Region(1200,300,50,50),PedestrianWarningPixels.evaluatePostBoot(largeArtwork,read(hidden,1),read(visible,2),read(hidden,3)).glyph()));
    }
}
