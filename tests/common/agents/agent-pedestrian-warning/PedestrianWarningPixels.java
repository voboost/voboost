package ru.voboost.tests.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ru.voboost.voyemu.VehicleCan;
import ru.voboost.voyemu.PixelImage;

/** Original OFF artwork and a controlled rendered counterfactual; never a visibility-property oracle. */
public final class PedestrianWarningPixels {
    /** Original A9 OFF Image2D.Visible binding at KZB offset 2347133 is bool(soundEnable). */
    public static final int ORIGINAL_OFF_SOUND_ENABLE = 1;
    private static final String STEM = "light_低速行驶提示音关闭";
    private static final int PIXEL_TOLERANCE = 12;
    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    public record Artwork(String textureUri, PixelImage image) {
        public Artwork {
            if (!List.of("kzb://light_normal/Textures/" + STEM,
                    "kzb://light_normal/Textures/light/" + STEM,
                    "kzb://light_normal/Textures/lightWhite/" + STEM).contains(textureUri)
                    || image == null || image.width() < 8 || image.height() < 8 || image.width() > 128 || image.height() > 128)
                throw new IllegalArgumentException("bounded original PW OFF artwork required");
        }
    }
    public record Region(int x, int y, int width, int height) {}
    public record Result(Region glyph, String textureUri, int comparedPixels, int changedGlyphPixels,
            int unrelatedChangedPixels, String visibleSequence, String hiddenSequence, String restoredSequence) {}
    public static final class Frame {
        private final PixelImage image;
        private final String session, generation, theme, androidBoot;
        private final BigInteger sequence, sourceTime;
        public PixelImage image() { return image; }

        private Frame(PixelImage image, String session, String generation, String theme, String androidBoot, BigInteger sequence, BigInteger sourceTime) {
            this.image=image;this.session=session;this.generation=generation;this.theme=theme;this.androidBoot=androidBoot;this.sequence=sequence;this.sourceTime=sourceTime;
        }
    }
    private static void require(boolean value,String reason)throws IOException { if(!value)throw new IOException(reason); }
    private static boolean yes(JsonObject object,String key) { return object!=null&&object.has(key)&&object.get(key).isJsonPrimitive()
        &&object.getAsJsonPrimitive(key).isBoolean()&&object.get(key).getAsBoolean(); }
    private static String text(JsonObject object,String key)throws IOException {
        require(object.has(key)&&object.get(key).isJsonPrimitive()&&object.getAsJsonPrimitive(key).isString(),"missing text: "+key);
        return object.get(key).getAsString();
    }
    private static int integer(JsonObject object,String key)throws IOException {
        require(object.has(key)&&object.get(key).isJsonPrimitive()&&object.getAsJsonPrimitive(key).isNumber(),"missing integer: "+key);
        try{return object.get(key).getAsBigDecimal().intValueExact();}catch(ArithmeticException e){throw new IOException("invalid integer: "+key,e);}
    }
    private static BigInteger decimal(String value)throws IOException {
        require(value!=null&&value.matches("0|[1-9][0-9]{0,19}"),"unsigned decimal required");BigInteger n=new BigInteger(value);
        require(n.compareTo(MAX_U64)<=0,"unsigned decimal overflow");return n;
    }
    private static boolean unchanged(BasicFileAttributes a,BasicFileAttributes b) {
        return a.fileKey()!=null&&a.fileKey().equals(b.fileKey())&&a.size()==b.size()&&a.lastModifiedTime().equals(b.lastModifiedTime())&&a.creationTime().equals(b.creationTime());
    }
    /** Theme and VSP are supplied by independent owner/CAN observations for this same phase. */
    public static Frame readCapture(Path directory,String expectedSession,String expectedGeneration,String appliedTheme,int actualVsp)throws IOException {
        return readCapture(directory, expectedSession, expectedGeneration, appliedTheme, actualVsp, null);
    }
    /** Accept only the exact region requested from the current QNX framebuffer. */
    public static Frame readCapture(Path directory, String expectedSession, String expectedGeneration,
            String appliedTheme, int actualVsp, ru.voboost.voyemu.VoyemuClient.ClusterRegion region) throws IOException {
        require(directory.isAbsolute()&&directory.equals(directory.toRealPath()),"canonical capture directory required");

        require(expectedSession!=null&&expectedSession.matches("[a-f0-9]{32}")&&decimal(expectedGeneration).signum()>0,"owned capture identity required");
        require(appliedTheme!=null&&!appliedTheme.isBlank()&&!appliedTheme.equals("unknown")&&appliedTheme.length()<=128&&(region == null ? actualVsp==1 : actualVsp==1 || actualVsp==2),"same observed theme and valid actual PW required");
        Path manifest=directory.resolve("manifest.json");require(Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS),"ordinary capture manifest required");
        var before=Files.readAttributes(manifest,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(before.size()>0&&before.size()<=131072,"manifest bound");
        try {
            byte[] metadata;try(var input=Files.newInputStream(manifest)){metadata=input.readNBytes(131073);}require(metadata.length<=131072,"manifest grew beyond bound");
            String json=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(metadata)).toString();
            JsonObject m=JsonParser.parseString(json).getAsJsonObject();
            require(integer(m,"schema")==1,"current capture schema required");
            require(yes(m,"accepted")&&yes(m,"qnx_frame_verified")&&yes(m,"exported_frame_verified")&&yes(m,"cluster_powered"),"unverified QNX capture");
            require(expectedSession.equals(text(m,"session_id"))&&expectedGeneration.equals(text(m,"qnx_generation")),"foreign capture identity");
            JsonObject selected=null;
            for(var element:m.getAsJsonArray("frames")) {var f=element.getAsJsonObject();if("cluster".equals(text(f,"screen"))){require(selected==null,"duplicate cluster frame");selected=f;}}
            require(selected!=null&&"qnx".equals(text(selected,"source"))&&"qnx.frame_transport".equals(text(selected,"source_clock_domain")),"original QNX frame required");
            require(selected.has("derived")&&selected.get("derived").isJsonPrimitive()&&selected.getAsJsonPrimitive("derived").isBoolean()
                &&selected.get("derived").getAsBoolean()==(region!=null)&&"cluster.png".equals(text(selected,"file")),"unexpected derivation or foreign frame");
            require(yes(selected.getAsJsonObject("freshness"),"newer_guest_frame_proven"),"positive after_sequence capture required");
            int width=integer(selected,"width"),height=integer(selected,"height");JsonObject r=selected.getAsJsonObject("rect");
            if (region == null) {
                require(integer(r,"x")==0&&integer(r,"y")==0&&integer(r,"width")==width&&integer(r,"height")==height
                    &&integer(selected,"source_width")==width&&integer(selected,"source_height")==height,"uncropped source raster required");
            } else {
                require(integer(r,"x")==region.x()&&integer(r,"y")==region.y()
                    &&integer(r,"width")==region.width()&&integer(r,"height")==region.height()
                    &&width==region.width()&&height==region.height()
                    &&integer(selected,"source_width")==1920&&integer(selected,"source_height")==720,
                    "exact requested QNX region required");
            }
            BigInteger sequence=decimal(text(selected,"sequence")),stamp=decimal(text(selected,"source_timestamp_ns"));require(sequence.signum()>0&&stamp.signum()>0,"fresh QNX sequence/time required");
            PixelImage image=PixelImage.read(directory.resolve("cluster.png"),width,height);
            require(unchanged(before,Files.readAttributes(manifest,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)),"manifest changed during raster read");
            return new Frame(image,expectedSession,expectedGeneration,appliedTheme,m.has("android_boot_id")?text(m,"android_boot_id"):null,sequence,stamp);
        } catch(IllegalStateException|IllegalArgumentException|NullPointerException e) {throw new IOException("malformed QNX capture",e);}
    }
    public static boolean matchesRegion(PixelImage reference, Frame frame, boolean visible, int tolerance)
            throws IOException {
        return PwGlyph.matches(reference, frame.image, visible, tolerance);
    }

    /** Compare only glyph pixels after independently validating each native capture. */
    public static int compareRegions(PixelImage reference, Frame visible, Frame hidden, Frame restored,
            int tolerance) throws IOException {
        require(visible.session.equals(hidden.session)&&visible.session.equals(restored.session)
            &&visible.generation.equals(hidden.generation)&&visible.generation.equals(restored.generation)
            &&visible.theme.equals(hidden.theme)&&visible.theme.equals(restored.theme)
            &&java.util.Objects.equals(visible.androidBoot,hidden.androidBoot)
            &&java.util.Objects.equals(visible.androidBoot,restored.androidBoot),"PW capture context changed");
        require(visible.sequence.compareTo(hidden.sequence)<0&&hidden.sequence.compareTo(restored.sequence)<0
            &&visible.sourceTime.compareTo(hidden.sourceTime)<0&&hidden.sourceTime.compareTo(restored.sourceTime)<0,
            "PW frames must advance in source order");
        return PwGlyph.compare(reference,visible.image,hidden.image,restored.image,tolerance);
    }
    private static Result readOemRegions(JsonObject proof) throws IOException {
        keys(proof,"schemaVersion","kind","sessionId","androidBootId","qnxGeneration","theme","initialNativeEvidence","region","cycles","artwork","stockBaseline","owner","installBridge","stimulusSource");
        require(integer(proof,"schemaVersion")==1,"PW setting proof schema differs");
        require("virtual-mcu".equals(text(proof,"stimulusSource")),"explicit virtual MCU stimulus required");
        String session=text(proof,"sessionId"),boot=text(proof,"androidBootId"),generation=text(proof,"qnxGeneration"),theme=text(proof,"theme");
        require(session.matches("[0-9a-f]{32}")&&boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"),"PW OEM identities required");
        JsonObject r=object(proof,"region");keys(r,"x","y","width","height");
        var region=new ru.voboost.voyemu.VoyemuClient.ClusterRegion(integer(r,"x"),integer(r,"y"),integer(r,"width"),integer(r,"height"));
        JsonObject source=object(proof,"artwork");keys(source,"textureUri","rgbaPng","width","height");
        Artwork artwork=new Artwork(text(source,"textureUri"),PixelImage.read(path(source,"rgbaPng"),integer(source,"width"),integer(source,"height")));
        var bounds=PwGlyph.bounds(artwork.image());
        require(bounds.width()==region.width()&&bounds.height()==region.height(),"PW region includes padding or truncates artwork");
        require(region.x()==841&&region.y()==102&&region.width()==46&&region.height()==46,"exact PW glyph ROI required");
        JsonObject stock=object(proof,"stockBaseline");
        keys(stock,"sessionId","androidBootId","qnxGeneration","theme","absenceEvidence","phases","owner");
        require(session.equals(text(stock,"sessionId"))&&theme.equals(text(stock,"theme")),"stock source session/theme differs");
        String stockBoot=text(stock,"androidBootId"),stockGeneration=text(stock,"qnxGeneration");
        require(stockBoot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"),"stock boot required");
        requireInstallBridge(proof,stock,session,stockBoot,boot);
        JsonObject stockPhases=object(stock,"phases");keys(stockPhases,"visible","absent","restored");
        List<Frame> stockFrames=new ArrayList<>(); VehicleCan.Snapshot stockCan=null;
        for(String name:List.of("visible","absent","restored")) {
            JsonObject phase=object(stockPhases,name);keys(phase,"captureDir","can","inputEvidence");
            int wanted=name.equals("absent")?2:1;
            VehicleCan.Snapshot can=VehicleCan.read(object(phase,"can"),session,stockBoot);
            require(can.pw()==wanted,"stock OEM raw1 OFF / raw2 ON required");
            if(stockCan!=null)can.requireNext(stockCan);stockCan=can;
            Frame frame=readCapture(path(phase,"captureDir"),session,stockGeneration,theme,wanted,region);
            require(frame.androidBoot==null||stockBoot.equals(frame.androidBoot),"stock frame crossed boot");stockFrames.add(frame);
        }
        compareRegions(artwork.image(),stockFrames.get(0),stockFrames.get(1),stockFrames.get(2),PIXEL_TOLERANCE);
        JsonObject cycles=object(proof,"cycles");keys(cycles,"settings","drawer");
        VehicleCan.Snapshot previousCan=null;
        Frame previousFrame=stockGeneration.equals(generation)?stockFrames.get(2):null;
        JsonObject nativeProcess=null;
        String nativeBoot=null,nativeEpoch=null,nativeGeneration=null,publisher=null;
        BigInteger nativeRevision=BigInteger.ZERO;
        Result result=null;
        for(String control:List.of("settings","drawer")) {
            JsonObject phases=object(cycles,control);keys(phases,"off","on","off-restored");
            List<Frame> frames=new ArrayList<>();
            for(String name:List.of("off","on","off-restored")) {
                JsonObject phase=object(phases,name);
                if(name.equals("on")) {
                    keys(phase,"captureDir","agentEnabled","actualVsp","inputEvidence","logArchive","native","can","restoreRootBefore","restoreRootAfter");
                    require(!ru.voboost.voyemu.VehicleArmed.readPedestrian(object(phase,"restoreRootBefore"),boot)
                        &&!ru.voboost.voyemu.VehicleArmed.readPedestrian(object(phase,"restoreRootAfter"),boot),"PW ON requires fixed-path persisted armed=false around capture");
                } else keys(phase,"captureDir","agentEnabled","actualVsp","inputEvidence","logArchive","native","can");
                int wanted=name.equals("on")?2:1;
                require(yes(phase,"agentEnabled")&&integer(phase,"actualVsp")==wanted,"PW agent must remain ON while OEM PW changes");
                VehicleCan.Snapshot can=VehicleCan.read(object(phase,"can"),session,boot);
                require(can.pw()==wanted,"PW OEM selection lacks independent CAN readback");
                if(previousCan!=null)can.requireNext(previousCan);previousCan=can;
                JsonObject nativeEvidence=object(phase,"native"),cluster=object(nativeEvidence,"snapshot");
                require(integer(nativeEvidence,"observedAdvances")>=2&&"idle".equals(text(cluster,"deliveryState")),"fresh native PW response required");
                String nextBoot=text(nativeEvidence,"nativeBoot"),nextEpoch=text(nativeEvidence,"nativeDaemonEpoch"),nextGeneration=text(nativeEvidence,"nativeGeneration"),nextPublisher=text(nativeEvidence,"publisher");
                require(nativeEvidence.has("observationRevision")&&nativeEvidence.getAsJsonPrimitive("observationRevision").isNumber(),"numeric native revision required");
                BigInteger revision=decimal(nativeEvidence.get("observationRevision").getAsString());
                int order = revision.compareTo(nativeRevision);
                boolean finalFrame = control.equals("drawer") && name.equals("off-restored");
                require(revision.signum() > 0 && order >= 0 && (!finalFrame || order > 0),
                    "native PW evidence regressed or did not advance after the final frame");
                nativeRevision = revision;
                if(nativeBoot!=null)require(nativeBoot.equals(nextBoot)&&nativeEpoch.equals(nextEpoch)
                    &&nativeGeneration.equals(nextGeneration)&&publisher.equals(nextPublisher),"native PW owner changed");
                nativeBoot=nextBoot;nativeEpoch=nextEpoch;nativeGeneration=nextGeneration;publisher=nextPublisher;
                JsonObject pw=null,target=null;
                for(var item:cluster.getAsJsonArray("agents")) {
                    JsonObject candidate=item.getAsJsonObject();
                    if(!"vehicle-drive-pedestrian-warning".equals(text(candidate,"id")))continue;
                    require(pw==null,"duplicate PW native agent");pw=candidate;
                }
                require(pw!=null&&yes(pw,"enabled")&&"applied".equals(text(pw,"state")),"PW aggregate native state is not the expected suppression/restore ACK");
                for(var item:pw.getAsJsonArray("targets")) {
                    JsonObject candidate=item.getAsJsonObject();
                    if(!"light".equals(text(candidate,"targetProcess")))continue;
                    require(target==null,"duplicate PW/light target");target=candidate;
                }
                require(target!=null&&"vehicle-drive-pedestrian-warning".equals(text(target,"id")),"PW/light missing");
                require("applied".equals(text(target,"state"))
                    &&"PW indicator hidden by saved setting".equals(text(target,"reason")),"saved PW setting is not applied");
                JsonObject process=object(target,"process");
                require(integer(process,"pid")>0&&integer(process,"lifecycleGeneration")>0,"live PW/light process lifetime required");
                for(String identity:List.of("birthIdentity","endpointEpoch"))
                    require(text(process,identity).matches("[0-9a-f]{64}"),"current PW/light process identity required");
                if(nativeProcess!=null)require(nativeProcess.equals(process),"PW/light process changed");nativeProcess=process;
                Frame frame=readCapture(path(phase,"captureDir"),session,generation,theme,wanted,region);
                require(frame.androidBoot==null||boot.equals(frame.androidBoot),"PW capture crossed Android boot");
                if(previousFrame!=null)require(frame.sequence.compareTo(previousFrame.sequence)>0
                    &&frame.sourceTime.compareTo(previousFrame.sourceTime)>0,"PW capture replayed");previousFrame=frame;
                frames.add(frame);
            }
            int pixels=0;
            for(Frame frame:frames) pixels=compareStockMasked(artwork.image(),stockFrames.get(0),stockFrames.get(1),frame,PIXEL_TOLERANCE);
            result=new Result(new Region(region.x(),region.y(),region.width(),region.height()),artwork.textureUri(),pixels,pixels,0,
                    stockFrames.get(0).sequence.toString(),stockFrames.get(1).sequence.toString(),stockFrames.get(2).sequence.toString());
        }
        return result;
    }
    private static void requireInstallBridge(JsonObject proof,JsonObject stock,String session,String stockBoot,String boot)throws IOException {
        JsonObject owner=object(proof,"owner"),bridge=object(proof,"installBridge");
        keys(owner,"runId","machine","work","sessionId","machineSeed");
        require(owner.equals(object(stock,"owner"))&&owner.equals(object(bridge,"owner"))
            &&session.equals(text(owner,"sessionId"))&&!text(owner,"machineSeed").isBlank()
            &&Path.of(text(owner,"machine")).isAbsolute(),"stock/installed exclusive machine/run/session differs");
        keys(bridge,"owner","operation","preparation","operationPath","preparationPath","qnxGeneration","restarts");
        JsonObject operation=object(bridge,"operation"),preparation=object(bridge,"preparation");
        Path work=Path.of(text(owner,"work")).toRealPath(),operationPath=path(bridge,"operationPath").toRealPath(),preparationPath=path(bridge,"preparationPath").toRealPath();
        require(operationPath.startsWith(work.resolve("operations"))&&preparationPath.startsWith(work),"installer receipts escaped the owning run");
        boolean retainedPreparation=false;
        for(var evidence:operation.getAsJsonArray("evidence"))if(work.relativize(preparationPath).toString().equals(evidence.getAsString()))retainedPreparation=true;
        require(retainedPreparation,"verified install does not retain its preparation proof");
        require(operation.equals(JsonParser.parseString(Files.readString(path(bridge,"operationPath"))).getAsJsonObject())
            &&preparation.equals(JsonParser.parseString(Files.readString(path(bridge,"preparationPath"))).getAsJsonObject()),"installer epoch bridge differs from retained receipts");
        require("verified".equals(text(operation,"state"))&&"INSTALL".equals(text(operation,"kind"))
            &&text(owner,"runId").equals(text(operation,"runId"))&&session.equals(text(operation,"sessionId"))
            &&stockBoot.equals(text(object(operation,"input"),"priorBoot"))
            &&stockBoot.equals(text(preparation,"priorBoot"))&&!stockBoot.equals(text(preparation,"finalBoot"))
            &&yes(preparation,"readiness")&&yes(preparation,"enforcing")&&yes(preparation,"acceptedJournal")&&integer(preparation,"receipts")>0,
            "verified installer two-boot epoch transition missing or differs");
        String cursor=text(preparation,"finalBoot"),sourceGeneration=text(bridge,"qnxGeneration");
        require(decimal(sourceGeneration).compareTo(decimal(text(stock,"qnxGeneration")))>=0,"install QNX source generation regressed");
        Path previousOperation=operationPath;
        for(var value:bridge.getAsJsonArray("restarts")) {
            JsonObject transition=value.getAsJsonObject();keys(transition,"operation","receipt","operationPath","receiptPath");
            JsonObject restart=object(transition,"operation"),receipt=object(transition,"receipt");
            Path restartPath=path(transition,"operationPath").toRealPath(),receiptPath=path(transition,"receiptPath").toRealPath();
            require(restartPath.startsWith(work.resolve("operations"))&&restartPath.compareTo(previousOperation)>0&&receiptPath.startsWith(work)
                &&restart.equals(JsonParser.parseString(Files.readString(restartPath)).getAsJsonObject())
                &&receipt.equals(JsonParser.parseString(Files.readString(receiptPath)).getAsJsonObject()),"reboot bridge is reordered or differs from retained files");
            previousOperation=restartPath;boolean retained=false;
            for(var evidence:restart.getAsJsonArray("evidence"))if(work.relativize(receiptPath).toString().equals(evidence.getAsString()))retained=true;
            require(retained&&"verified".equals(text(restart,"state"))&&"REBOOT".equals(text(restart,"kind"))
                &&text(owner,"runId").equals(text(restart,"runId"))&&session.equals(text(restart,"sessionId"))
                &&cursor.equals(text(object(restart,"input"),"priorBoot"))&&session.equals(text(receipt,"session_id"))
                &&"completed".equals(text(receipt,"status")),"reboot bridge owner/boot/terminal state differs");
            String next;
            if("guest_boot_changed".equals(text(receipt,"scope"))) {
                require(cursor.equals(text(receipt,"boot_id_before"))&&integer(receipt,"android_pid_before")>0&&integer(receipt,"android_pid")>0,"warm reboot bridge identities differ");
                next=text(receipt,"boot_id");
            } else {
                require("android_qnx_cold_boot_changed".equals(text(receipt,"scope"))&&"cold".equals(text(receipt,"mode"))
                    &&yes(receipt,"authority_retained")&&yes(receipt,"old_processes_stopped")&&yes(receipt,"guest_acknowledged"),"full restart lacks native ownership proof");
                JsonObject after=object(receipt,"after"),qnx=object(after,"qnx");
                require(integer(after,"android_pid")>0&&"owned_cold_incarnation".equals(text(qnx,"kind"))&&integer(qnx,"qemu_pid")>0
                    &&decimal(text(qnx,"generation")).signum()>0,"full restart lacks new owned Android/QNX identities");
                String nextGeneration=text(qnx,"generation");require(decimal(nextGeneration).compareTo(decimal(sourceGeneration))>0,"full restart QNX generation did not advance");sourceGeneration=nextGeneration;
                next=text(after,"android_boot_id");
            }
            require(next.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")&&!cursor.equals(next),"restart did not change Android boot");cursor=next;
        }
        require(boot.equals(cursor)&&text(proof,"qnxGeneration").equals(sourceGeneration),"installed pixels lack a complete verified Android/QNX epoch chain from stock");
    }
    /** Cross-install epochs require readOemRegions' explicit verified installer bridge. */
    public static int compareStockMasked(PixelImage reference,Frame stockVisible,Frame stockAbsent,Frame masked,int tolerance)throws IOException {
        require(stockVisible.session.equals(masked.session)&&stockVisible.theme.equals(masked.theme)
            &&(!stockVisible.generation.equals(masked.generation)||(stockAbsent.sequence.compareTo(masked.sequence)<0&&stockAbsent.sourceTime.compareTo(masked.sourceTime)<0)),"stock/masked source context differs or capture did not advance");
        require(stockVisible.session.equals(stockAbsent.session)&&stockVisible.generation.equals(stockAbsent.generation)
            &&stockVisible.theme.equals(stockAbsent.theme)&&java.util.Objects.equals(stockVisible.androidBoot,stockAbsent.androidBoot)
            &&stockVisible.sequence.compareTo(stockAbsent.sequence)<0&&stockVisible.sourceTime.compareTo(stockAbsent.sourceTime)<0,"stock baseline is stale or crossed context");
        return PwGlyph.compare(reference,stockVisible.image,masked.image,stockVisible.image,tolerance);
    }
    private static JsonObject object(JsonObject value,String key)throws IOException {
        require(value.has(key)&&value.get(key).isJsonObject(),"missing proof object: "+key);return value.getAsJsonObject(key);
    }
    private static void keys(JsonObject value,String... expected)throws IOException {
        require(value!=null&&value.keySet().equals(Set.of(expected)),"PW proof fields differ");
    }
    private static Path path(JsonObject value,String key)throws IOException {
        String location=text(value,key);require(!location.isEmpty()&&location.length()<=4096&&!location.codePoints().anyMatch(Character::isISOControl),"bounded proof path required");return Path.of(location);
    }
    /** Re-evaluates the concrete source evidence. A reported functional=true or stored pixel score is never accepted as proof. */
    public static Result readProof(JsonObject proof)throws IOException {
        try {
            if ("pw-saved-setting-rendered-regions".equals(text(proof, "kind"))) return readOemRegions(proof);
            keys(proof,"schemaVersion","kind","sessionId","androidBootId","qnxGeneration","theme","artwork","phases");
            require(integer(proof,"schemaVersion")==1&&"pw-original-rendered-counterfactual".equals(text(proof,"kind")),"PW rendered proof schema differs");
            String session=text(proof,"sessionId"),boot=text(proof,"androidBootId"),generation=text(proof,"qnxGeneration"),theme=text(proof,"theme");
            require(session.matches("[0-9a-f]{32}")&&boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")&&decimal(generation).signum()>0,"PW proof source identity missing");
            JsonObject reference=object(proof,"artwork");keys(reference,"textureUri","rgbaPng","width","height");int width=integer(reference,"width"),height=integer(reference,"height");
            require(width>=8&&width<=128&&height>=8&&height<=128,"bounded PW artwork dimensions required");
            Artwork artwork=new Artwork(text(reference,"textureUri"),PixelImage.read(path(reference,"rgbaPng"),width,height));
            JsonObject phases=object(proof,"phases");keys(phases,"visible","hidden","restored");List<Frame> frames=new ArrayList<>();VehicleCan.Snapshot prior=null;
            for(String name:List.of("visible","hidden","restored")) {
                JsonObject phase=object(phases,name);keys(phase,"captureDir","can","agentEnabled","soundEnable");
                require(phase.get("agentEnabled").isJsonPrimitive()&&phase.getAsJsonPrimitive("agentEnabled").isBoolean()
                    &&phase.get("agentEnabled").getAsBoolean()==name.equals("hidden")&&integer(phase,"soundEnable")==ORIGINAL_OFF_SOUND_ENABLE,"PW counterfactual phase/stimulus differs");
                VehicleCan.Snapshot can=VehicleCan.read(object(phase,"can"),session,boot);require(can.pw()==1,"PW must remain actually OFF in every phase");if(prior!=null)can.requireNext(prior);prior=can;
                Frame frame=readCapture(path(phase,"captureDir"),session,generation,theme,can.pw());
                require(frame.androidBoot==null||frame.androidBoot.equals(boot),"capture and CAN Android boot differ");frames.add(frame);
            }
            return evaluate(artwork,frames.get(0),frames.get(1),frames.get(2));
        }catch(IllegalArgumentException|IllegalStateException|NullPointerException|UnsupportedOperationException failure){throw new IOException("malformed PW rendered proof",failure);}
    }
    private record BootFence(String session,String machine,String boot,long androidPid,long qnxPid,long generation) {}
    private static long statusNumber(JsonObject value,String key)throws IOException {
        require(value!=null&&value.has(key)&&value.get(key).isJsonPrimitive()&&value.getAsJsonPrimitive(key).isNumber()
            &&value.get(key).getAsString().matches("[1-9][0-9]{0,18}"),"positive owned status field required: "+key);
        try{return Long.parseLong(value.get(key).getAsString());}catch(NumberFormatException e){throw new IOException("owned status overflow",e);}
    }
    private static BootFence bootFence(JsonObject status,JsonObject system)throws IOException {
        require(yes(status,"ok")&&status.has("stopped")&&status.get("stopped").isJsonPrimitive()
            &&status.getAsJsonPrimitive("stopped").isBoolean()&&!status.get("stopped").getAsBoolean(),"owned machine is not running");
        String session=text(status,"session_id"),machine=text(status,"machine_seed"),boot=text(system,"boot_id");
        require(session.matches("[0-9a-f]{32}")&&!machine.isBlank()&&boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
            &&"ready".equals(text(system,"status")),"owned boot fence identity differs");
        long androidPid=statusNumber(status,"android_pid"),qnxPid=statusNumber(status,"qnx_pid");
        require(androidPid>=2&&qnxPid>=2,"owned process identity unavailable");
        JsonObject value=object(system,"value");
        require(boot.equals(text(value,"boot_id"))&&androidPid==statusNumber(value,"owned_android_pid"),"owned Android boot/PID differs");
        long generation=statusNumber(object(object(status,"deliveries"),"qnx"),"generation");
        require(generation<=0xffffffffL,"QNX generation outside protocol bound");
        return new BootFence(session,machine,boot,androidPid,qnxPid,generation);
    }
    public record PostBootResult(Region glyph, String textureUri, int comparedPixels, int changedGlyphPixels,
            int unrelatedChangedPixels, String initialHiddenSequence, String disabledVisibleSequence, String reenabledHiddenSequence) {}
    /** Replays the post-boot hidden -> disabled-visible -> reenabled-hidden raster sequence.
     * This proves only the rendered counterfactual; the caller must independently bind the
     * current native light endpoint, desired state, and changed boot before accepting an agent.
     */
    public static PostBootResult readPostBootProof(JsonObject proof)throws IOException {
        try {
            keys(proof,"schemaVersion","kind","sessionId","androidBootId","qnxGeneration","theme","artwork","phases");
            require(integer(proof,"schemaVersion")==1&&"pw-postboot-rendered-counterfactual".equals(text(proof,"kind")),"PW postboot proof schema differs");
            String session=text(proof,"sessionId"),boot=text(proof,"androidBootId"),generation=text(proof,"qnxGeneration"),theme=text(proof,"theme");
            require(session.matches("[0-9a-f]{32}")&&boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")&&decimal(generation).signum()>0,"PW postboot source identity missing");
            JsonObject reference=object(proof,"artwork");keys(reference,"textureUri","rgbaPng","width","height");
            int width=integer(reference,"width"),height=integer(reference,"height");
            require(width>=8&&width<=128&&height>=8&&height<=128,"bounded PW artwork dimensions required");
            Artwork artwork=new Artwork(text(reference,"textureUri"),PixelImage.read(path(reference,"rgbaPng"),width,height));
            JsonObject phases=object(proof,"phases");keys(phases,"hiddenBeforeMutation","visibleDisabled","hiddenReenabled");
            List<Frame> frames=new ArrayList<>();VehicleCan.Snapshot prior=null;BootFence firstFence=null;
            for(String name:List.of("hiddenBeforeMutation","visibleDisabled","hiddenReenabled")) {
                JsonObject phase=object(phases,name);keys(phase,"captureDir","can","agentEnabled","soundEnable","beforeStatus","beforeSystem","afterSystem","afterStatus");
                BootFence before=bootFence(object(phase,"beforeStatus"),object(phase,"beforeSystem")),after=bootFence(object(phase,"afterStatus"),object(phase,"afterSystem"));
                require(before.equals(after)&&session.equals(before.session())&&boot.equals(before.boot())&&generation.equals(Long.toString(before.generation()))&&(firstFence==null||firstFence.equals(before)),"postboot owned capture fence changed");
                firstFence=before;
                require(phase.get("agentEnabled").isJsonPrimitive()&&phase.getAsJsonPrimitive("agentEnabled").isBoolean()
                    &&phase.get("agentEnabled").getAsBoolean()!=name.equals("visibleDisabled")&&integer(phase,"soundEnable")==ORIGINAL_OFF_SOUND_ENABLE,"PW postboot phase/stimulus differs");
                VehicleCan.Snapshot can=VehicleCan.read(object(phase,"can"),session,boot);require(can.pw()==1,"PW must remain actually OFF in every phase");
                if(prior!=null)can.requireNext(prior);prior=can;
                Frame frame=readCapture(path(phase,"captureDir"),session,generation,theme,can.pw());
                require(frame.androidBoot==null||boot.equals(frame.androidBoot),"postboot capture Android boot identity differs");frames.add(frame);
            }
            return evaluatePostBoot(artwork,frames.get(0),frames.get(1),frames.get(2));
        }catch(IllegalArgumentException|IllegalStateException|NullPointerException|UnsupportedOperationException failure){throw new IOException("malformed PW postboot proof",failure);}
    }
    /** Same-boot frames only: no cross-boot pixel equivalence is assumed. */
    public static PostBootResult evaluatePostBoot(Artwork artwork,Frame hidden,Frame visible,Frame hiddenAgain)throws IOException {
        require(artwork!=null&&hidden!=null&&visible!=null&&hiddenAgain!=null,"three postboot phases and original artwork required");
        for(Frame f:List.of(visible,hiddenAgain))require(f.session.equals(hidden.session)&&f.generation.equals(hidden.generation)&&f.theme.equals(hidden.theme)
            &&f.image.width()==hidden.image.width()&&f.image.height()==hidden.image.height()&&(f.androidBoot==null||hidden.androidBoot==null||f.androidBoot.equals(hidden.androidBoot)),"cross-boot/theme/raster comparison rejected");
        require(hidden.sequence.compareTo(visible.sequence)<0&&visible.sequence.compareTo(hiddenAgain.sequence)<0
            &&hidden.sourceTime.compareTo(visible.sourceTime)<0&&visible.sourceTime.compareTo(hiddenAgain.sourceTime)<0,"cached or reordered postboot phase rejected");
        PixelImage t=artwork.image(),a=hidden.image,b=visible.image,c=hiddenAgain.image;
        require(t.width()<=a.width()&&t.height()<=a.height(),"artwork exceeds frame");
        int ax=-1,ay=-1;outer:for(int y=0;y<t.height();y++)for(int x=0;x<t.width();x++)if((t.pixel(x,y)>>>24)>=240){ax=x;ay=y;break outer;}
        require(ax>=0,"opaque glyph pixels required");List<Result> found=new ArrayList<>();
        for(int y=0;y<=a.height()-t.height();y++)for(int x=0;x<=a.width()-t.width();x++) {
            int ink=b.pixel(x+ax,y+ay),glyph=t.pixel(ax,ay);
            if(distance(ink,composite(glyph,a.pixel(x+ax,y+ay)))>PIXEL_TOLERANCE
                    ||distance(ink,composite(glyph,c.pixel(x+ax,y+ay)))>PIXEL_TOLERANCE)continue;
            int[] first=match(t,b,a,b,x,y),second=match(t,b,c,b,x,y);
            if(first==null||second==null)continue;
            found.add(new Result(new Region(x,y,t.width(),t.height()),artwork.textureUri(),first[0],Math.min(first[1],second[1]),0,hidden.sequence.toString(),visible.sequence.toString(),hiddenAgain.sequence.toString()));
            require(found.size()==1,"ambiguous repeated glyph regions");
        }
        require(found.size()==1,"no unique original OFF glyph postboot counterfactual");Result hit=found.get(0);Region box=hit.glyph();int outside=0;
        for(int y=0;y<a.height();y++)for(int x=0;x<a.width();x++)if(x<box.x()||x>=box.x()+box.width()||y<box.y()||y>=box.y()+box.height())
            if(distance(a.pixel(x,y),b.pixel(x,y))>PIXEL_TOLERANCE||distance(c.pixel(x,y),b.pixel(x,y))>PIXEL_TOLERANCE)outside++;
        require(outside<=Math.max(4,(a.width()*a.height())/10000),"unrelated rendered changes invalidate postboot comparison");
        return new PostBootResult(box,hit.textureUri(),hit.comparedPixels(),hit.changedGlyphPixels(),outside,hit.visibleSequence(),hit.hiddenSequence(),hit.restoredSequence());
    }
    private static int channel(int pixel,int shift){return (pixel>>>shift)&255;}
    private static int distance(int a,int b){return Math.max(Math.abs(channel(a,16)-channel(b,16)),Math.max(Math.abs(channel(a,8)-channel(b,8)),Math.abs(channel(a,0)-channel(b,0))));}
    private static int composite(int foreground,int background) {
        int alpha=foreground>>>24,value=0xff000000;for(int shift:new int[]{16,8,0})value|=((channel(foreground,shift)*alpha+channel(background,shift)*(255-alpha)+127)/255)<<shift;return value;
    }
    private static int[] match(PixelImage template,PixelImage a,PixelImage b,PixelImage c,int x,int y) {
        int bad=0,changed=0,core=0,all=template.width()*template.height();
        for(int yy=0;yy<template.height();yy++)for(int xx=0;xx<template.width();xx++) {
            int t=template.pixel(xx,yy),v=a.pixel(x+xx,y+yy),h=b.pixel(x+xx,y+yy),r=c.pixel(x+xx,y+yy),alpha=t>>>24;
            int expected=composite(t,h);int tolerance=alpha>0&&alpha<240?20:PIXEL_TOLERANCE;
            if(distance(v,expected)>tolerance||distance(r,expected)>tolerance||distance(v,r)>PIXEL_TOLERANCE)bad++;
            if(alpha>=240){core++;if(distance(v,h)>32&&distance(r,h)>32)changed++;}
            if(bad>all/100)return null;
        }
        return core>=16&&changed>=core-core/50?new int[]{all,changed}:null;
    }
    /** Does not authorize a vehicle action or global product PASS. Independent constant-CAN and authority checks remain required. */
    public static Result evaluate(Artwork artwork,Frame visible,Frame hidden,Frame restored)throws IOException {
        require(artwork!=null&&visible!=null&&hidden!=null&&restored!=null,"three rendered phases and original artwork required");
        for(Frame f:List.of(hidden,restored))require(f.session.equals(visible.session)&&f.generation.equals(visible.generation)&&f.theme.equals(visible.theme)
            &&f.image.width()==visible.image.width()&&f.image.height()==visible.image.height(),"cross-boot/theme/raster comparison rejected");
        require(visible.sequence.compareTo(hidden.sequence)<0&&hidden.sequence.compareTo(restored.sequence)<0
            &&visible.sourceTime.compareTo(hidden.sourceTime)<0&&hidden.sourceTime.compareTo(restored.sourceTime)<0,"cached or reordered phase rejected");
        PixelImage t=artwork.image(),a=visible.image,b=hidden.image,c=restored.image;require(t.width()<=a.width()&&t.height()<=a.height(),"artwork exceeds frame");
        int ax=-1,ay=-1;outer:for(int y=0;y<t.height();y++)for(int x=0;x<t.width();x++)if((t.pixel(x,y)>>>24)>=240){ax=x;ay=y;break outer;}
        require(ax>=0,"opaque glyph pixels required");List<Result> found=new ArrayList<>();
        for(int y=0;y<=a.height()-t.height();y++)for(int x=0;x<=a.width()-t.width();x++) {
            int target=composite(t.pixel(ax,ay),b.pixel(x+ax,y+ay));
            if(distance(a.pixel(x+ax,y+ay),target)>PIXEL_TOLERANCE||distance(c.pixel(x+ax,y+ay),target)>PIXEL_TOLERANCE)continue;
            int[] score=match(t,a,b,c,x,y);if(score==null)continue;
            found.add(new Result(new Region(x,y,t.width(),t.height()),artwork.textureUri(),score[0],score[1],0,visible.sequence.toString(),hidden.sequence.toString(),restored.sequence.toString()));
            require(found.size()==1,"ambiguous repeated glyph regions");
        }
        require(found.size()==1,"no unique original OFF glyph disappearance/restoration");Result hit=found.get(0);Region box=hit.glyph();int outside=0;
        for(int y=0;y<a.height();y++)for(int x=0;x<a.width();x++)if(x<box.x()||x>=box.x()+box.width()||y<box.y()||y>=box.y()+box.height())
            if(distance(a.pixel(x,y),b.pixel(x,y))>PIXEL_TOLERANCE||distance(c.pixel(x,y),b.pixel(x,y))>PIXEL_TOLERANCE)outside++;
        require(outside<=Math.max(4,(a.width()*a.height())/10000),"unrelated rendered changes invalidate controlled comparison");
        return new Result(box,hit.textureUri(),hit.comparedPixels(),hit.changedGlyphPixels(),outside,hit.visibleSequence(),hit.hiddenSequence(),hit.restoredSequence());
    }
    private PedestrianWarningPixels() {}
}
