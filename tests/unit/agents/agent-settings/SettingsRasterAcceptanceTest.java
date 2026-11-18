package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class SettingsRasterAcceptanceTest {
    @TempDir Path work;
    private static final String CAPTION="恢复出厂设置";
    private record Fixture(SettingsRasterAcceptance.Inventory inventory,StockFontReference reference,List<SettingsRasterAcceptance.Sample> samples){}
    private Fixture fixture()throws Exception {
        var host=new StockFontReferenceTest();host.product=work;host.setup();
        var registry=RunFiles.read(host.directory().resolve("reference.json"));var samples=registry.getAsJsonArray("samples");var original=samples.get(0).deepCopy();
        for(String family:List.of("body","dialog-action","dialog-message")){var sample=original.getAsJsonObject().deepCopy();sample.addProperty("model",family);sample.addProperty("family",family);samples.add(sample);}RunFiles.write(host.directory().resolve("reference.json"),registry);
        var reference=StockFontReference.load(work.toRealPath(),"host-fixture","profile",host.inputs());
        var language=SettingsOemMeasurementsTest.object("uninstall",SettingsOemMeasurementsTest.object("buttonText",CAPTION,"description",CAPTION,"confirmLabel",CAPTION,"cancelLabel",CAPTION));
        JsonArray panel=new JsonArray();for(String role:List.of("uninstall.action","uninstall.status"))panel.add(SettingsOemMeasurementsTest.object("role",role,"value",role.endsWith("status")?"status":"uninstall","class",role.equals("uninstall.action")?"android.widget.Button":"android.widget.TextView"));
        JsonArray dialog=new JsonArray();for(String value:List.of("confirm","cancel"))dialog.add(SettingsOemMeasurementsTest.object("role","dialog."+value,"value",value,"class","android.widget.TextView"));
        language.add("layoutTopology",SettingsOemMeasurementsTest.object("panelTargets",panel,"dialogTargets",dialog));
        var source=SettingsOemMeasurementsTest.object("languages",SettingsOemMeasurementsTest.object("zh",language),"geometry",SettingsOemMeasurementsTest.object("BUTTON_HEIGHT",70,"BUTTON_HORIZONTAL_PADDING",38));
        var inventory=SettingsRasterAcceptance.inventory(source,work.resolve("source-copy.json"),"b".repeat(64),21,"zh","interface");
        var frame=host.frame();var measurements=new ArrayList<SettingsRasterAcceptance.Sample>();
        for(String key:inventory.keys()) {
            String role=key.split("\n")[0],value=key.split("\n")[1];boolean isDialog=role.startsWith("dialog.");
            var before=frame.scope();before.remove("target");before.remove("role");before.remove("value");
            before.addProperty("caseNumber",21);before.addProperty("language","zh");before.addProperty("sourceRootObserved",true);
            before.addProperty("generationId","a".repeat(64));before.addProperty("catalogIdentity","b".repeat(64));before.addProperty("documentGeneration","1");before.addProperty("captureId","actual-capture");
            before.addProperty("targetPid","42");before.addProperty("targetStarttime","4");before.addProperty("endpointEpoch","5");
            before.addProperty("surface",isDialog?"dialog":"panel");before.addProperty("phase",isDialog?"pre-click":"after-layout");before.addProperty("windowId",isDialog?"dialog":"main");before.addProperty("rootIdentity",isDialog?"android.widget.FrameLayout@2222":"android.widget.FrameLayout@1111");
            before.addProperty("observationId","before");before.add("hostInterval",frame.scope().get("hostInterval"));
            var row=frame.scope().getAsJsonObject("target").deepCopy();row.addProperty("role",role);row.addProperty("value",value);row.addProperty("nodeId",(isDialog?"dialog":"main")+":"+(role.equals("uninstall.action")?"ru.voboost.ui.qinggan.Button$ActionButton":"android.widget.TextView")+"@"+Integer.toHexString(measurements.size()+1));row.add("sourceCaptionMetrics",SettingsOemMeasurementsTest.object("padding_left",38,"padding_right",38));if(role.equals("uninstall.action")){var nativeLayout=row.getAsJsonObject("textLayout");nativeLayout.addProperty("width",174);nativeLayout.addProperty("padding_left",38);nativeLayout.addProperty("padding_right",38);var line=nativeLayout.getAsJsonArray("lines").get(0).getAsJsonObject();line.addProperty("left",3);line.addProperty("right",171);}var rows=new JsonArray();rows.add(row);before.add("rows",rows);
            var after=before.deepCopy();after.addProperty("observationId","after");var interval=SettingsRasterCaptureTest.interval("1200000000","1210000000");interval.addProperty("clock_id","a".repeat(32));after.add("hostInterval",interval);
            var capture=RunFiles.read(host.captureDirectory().resolve("manifest.json"));capture.addProperty("artifact_dir",host.captureDirectory().toString());capture.addProperty("manifest",host.captureDirectory().resolve("manifest.json").toString());String model=role.equals("uninstall.action")?"dfp28":role.equals("uninstall.status")?"body":role.equals("dialog.message")?"dialog-message":"dialog-action";
            measurements.add(new SettingsRasterAcceptance.Sample(capture,host.captureDirectory(),before,after,role,value,model));
        }
        return new Fixture(inventory,reference,measurements);
    }
    @Test void fullOwningActionAndSeparateDialogInventoryRunsExecutableRasterCriteria()throws Exception {
        var fixture=fixture();var result=SettingsRasterAcceptance.verify(fixture.inventory,fixture.reference,null,fixture.samples);
        assertTrue(result.accepted());assertEquals(5,result.proof().get("roleCount").getAsInt());assertFalse(result.proof().get("publicationApproval").getAsBoolean());assertFalse(result.proof().get("hardwareFidelityAccepted").getAsBoolean());
        Path output=work.resolve("verified.json");SettingsRasterAcceptance.write(output,fixture.inventory,fixture.reference,null,fixture.samples);assertTrue(SettingsRasterAcceptance.readProof(output,fixture.inventory,fixture.reference,null).accepted());
        var image=fixture.samples.get(0).directory().resolve("primary.png");var pixels=javax.imageio.ImageIO.read(image.toFile());var g=pixels.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect(556,84,28,70);g.dispose();javax.imageio.ImageIO.write(pixels,"png",image.toFile());assertThrows(IOException.class,()->SettingsRasterAcceptance.readProof(output,fixture.inventory,fixture.reference,null));
    }
    @Test void onePassingButtonMissingMessageDuplicateRoleOrMixedEndpointCannotAcceptWholeCase()throws Exception {
        var fixture=fixture();for(String mutation:List.of("missing","duplicate","endpoint","root","borrowedDialog","wrongFamily","padding","alignment","catalog")){
            var samples=new ArrayList<>(fixture.samples);
            if(mutation.equals("missing"))samples.remove(0);else if(mutation.equals("duplicate"))samples.add(samples.get(0));else {
                var selected=samples.get(0);var before=selected.before();var after=selected.after();String model=selected.model();
                switch(mutation){case"endpoint"->{before.addProperty("endpointEpoch","6");after.addProperty("endpointEpoch","6");}
                    case"catalog"->{before.addProperty("catalogIdentity","foreign");after.addProperty("catalogIdentity","foreign");}
                    case"alignment"->{for(var side:List.of(before,after))side.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("textLayout").addProperty("gravity",17);}
                    case"root"->{before.addProperty("sourceRootObserved",false);after.addProperty("sourceRootObserved",false);}
                    case"borrowedDialog"->{before.addProperty("surface","panel");after.addProperty("surface","panel");before.addProperty("phase","after-layout");after.addProperty("phase","after-layout");before.addProperty("windowId","main");after.addProperty("windowId","main");}
                    case"wrongFamily"->model="dfp28";
                    case"padding"->{for(var side:List.of(before,after))side.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("sourceCaptionMetrics").addProperty("padding_left",0);}}
                if(mutation.equals("borrowedDialog")){int at=-1;for(int i=0;i<samples.size();i++)if(samples.get(i).role().startsWith("dialog.")){at=i;break;}selected=samples.get(at);before=selected.before();after=selected.after();before.addProperty("windowId","main");after.addProperty("windowId","main");samples.set(at,new SettingsRasterAcceptance.Sample(selected.capture(),selected.directory(),before,after,selected.role(),selected.value(),selected.model()));}
                else if(mutation.equals("wrongFamily")){int at=-1;for(int i=0;i<samples.size();i++)if(!samples.get(i).role().equals("uninstall.action")){at=i;break;}selected=samples.get(at);samples.set(at,new SettingsRasterAcceptance.Sample(selected.capture(),selected.directory(),selected.before(),selected.after(),selected.role(),selected.value(),"dfp28"));}
                else if(mutation.equals("padding")){int at=-1;for(int i=0;i<samples.size();i++)if(samples.get(i).role().equals("uninstall.action")){at=i;break;}selected=samples.get(at);before=selected.before();after=selected.after();for(var side:List.of(before,after))side.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("sourceCaptionMetrics").addProperty("padding_left",0);samples.set(at,new SettingsRasterAcceptance.Sample(selected.capture(),selected.directory(),before,after,selected.role(),selected.value(),selected.model()));}
                else samples.set(0,new SettingsRasterAcceptance.Sample(selected.capture(),selected.directory(),before,after,selected.role(),selected.value(),model));
            }
            assertThrows(IOException.class,()->SettingsRasterAcceptance.verify(fixture.inventory,fixture.reference,null,samples),mutation);
        }
    }
    @Test void storedBooleanCannotReplaceRecomputedNativeRasterVerdict()throws Exception {
        var fixture=fixture();Path output=work.resolve("verified.json");SettingsRasterAcceptance.write(output,fixture.inventory,fixture.reference,null,fixture.samples);
        var retained=RunFiles.read(output);retained.getAsJsonObject("result").addProperty("roleCount",1);RunFiles.write(output,retained);assertThrows(IOException.class,()->SettingsRasterAcceptance.readProof(output,fixture.inventory,fixture.reference,null));
    }
    @Test void completeControlCaseAcceptsFreshProductionCaptureIdsAndRequiresActualSectionGeometry()throws Exception {
        Fixture base=fixture();Path registryPath=work.resolve("tests/emulator/agents/agent-settings/reference/host-fixture/reference.json");JsonObject registry=RunFiles.read(registryPath);var radio=registry.getAsJsonArray("samples").get(0).getAsJsonObject().deepCopy();radio.addProperty("model","radio");radio.addProperty("family","radio");registry.getAsJsonArray("samples").add(radio);RunFiles.write(registryPath,registry);
        var sourceHost=new StockFontReferenceTest();sourceHost.product=work;StockFontReference reference=StockFontReference.load(work.toRealPath(),"host-fixture","profile",sourceHost.inputs());
        JsonArray targets=new JsonArray(),options=new JsonArray(),switches=new JsonArray(),bodies=new JsonArray();
        for(String mode:List.of("interface","hidden","none")){targets.add(SettingsOemMeasurementsTest.object("role","startup","value",mode,"class","com.pateo.material.widgets.BoldRadioButton"));options.add(SettingsOemMeasurementsTest.object("value",mode,"label",CAPTION,"description",CAPTION));}
        targets.add(SettingsOemMeasurementsTest.object("role","startup.description","value","startup","class","android.widget.TextView"));switches.add(SettingsOemMeasurementsTest.object("key","font-cyrillic","section","startup"));bodies.add(SettingsOemMeasurementsTest.object("key","startup","index",0));
        var topology=SettingsOemMeasurementsTest.object("panelTargets",targets,"checkboxSwitches",switches,"sectionBodies",bodies);
        var language=SettingsOemMeasurementsTest.object("startup",options,"layoutTopology",topology);
        var geometry=SettingsOemMeasurementsTest.object("RADIO_HEIGHT",70,"RADIO_ITEM_MIN_WIDTH",120,"RADIO_ITEM_PADDING",26,"RADIO_TEXT",28,"CHECKBOX_HEIGHT",42,"SECTION_TOP",40,"SECTION_BOTTOM",2,"SECTION_GAP",25,"SECTION_HEADER",98);
        var inventory=SettingsRasterAcceptance.inventory(SettingsOemMeasurementsTest.object("languages",SettingsOemMeasurementsTest.object("zh",language),"geometry",geometry),work.resolve("control-source.json"),"b".repeat(64),20,"zh","interface");
        List<SettingsRasterAcceptance.Sample> samples=new ArrayList<>();var seed=base.samples.get(0);
        var section=SettingsOemMeasurementsTest.object("key","startup","index",0,"class","android.widget.LinearLayout","top",98,"bottom",298,"width",1364,"height",200,"paddingTop",40,"paddingBottom",2,"firstChildTop",40,"lastChildBottom",198,"actualLayout",SettingsOemMeasurementsTest.object("measured_width",1364,"measured_height",200,"padding_left",0,"padding_right",0,"padding_top",40,"padding_bottom",2,"scroll_x",0,"scroll_y",0,"params_type","basic","layout_width",-1,"layout_height",-2));JsonArray sectionRows=new JsonArray();sectionRows.add(section);
        for(String key:inventory.keys()){
            String role=key.split("\n")[0],value=key.split("\n")[1];boolean checkbox=role.startsWith("checkbox."),isRadio=role.equals("startup");var before=seed.before();before.addProperty("caseNumber",20);before.addProperty("surface","panel");before.addProperty("phase","after-layout");before.addProperty("windowId","main");before.addProperty("captureId","production-capture-"+samples.size());before.addProperty("observationId","before-"+samples.size());before.add("rootBounds",SettingsOemMeasurementsTest.object("x",485,"y",50-120*samples.size(),"width",1364,"height",2000));before.add("sectionBodies",sectionRows.deepCopy());before.getAsJsonArray("sectionBodies").get(0).getAsJsonObject().addProperty("captureId",before.get("captureId").getAsString());
            var row=before.getAsJsonArray("rows").get(0).getAsJsonObject();row.addProperty("role",role);row.addProperty("value",value);row.addProperty("nodeId","main:"+(checkbox?"com.pateo.material.widgets.QGCheckBox":isRadio?"com.pateo.material.widgets.BoldRadioButton":"android.widget.TextView")+"@"+Integer.toHexString(samples.size()+1));
            // One independent full-frame host fixture is reused as pixels; each row retains its own native viewport.
            var nativeText=SettingsTextLayoutTest.target(CAPTION);nativeText.getAsJsonObject("textLayout").getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("left",41);nativeText.getAsJsonObject("textLayout").getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("right",209);row.add("textLayout",nativeText.get("textLayout"));row.add("layout",nativeText.get("layout"));
            if(isRadio){row.getAsJsonObject("bounds").addProperty("x",530);row.getAsJsonObject("bounds").addProperty("width",220);row.getAsJsonObject("clippedBounds").addProperty("x",530);row.getAsJsonObject("clippedBounds").addProperty("width",220);row.getAsJsonObject("layout").addProperty("measured_width",220);row.getAsJsonObject("textLayout").addProperty("width",220);var line=row.getAsJsonObject("textLayout").getAsJsonArray("lines").get(0).getAsJsonObject();line.addProperty("left",26);line.addProperty("right",194);row.add("sourceCaptionMetrics",SettingsOemMeasurementsTest.object("caption_px",168));}
            Path directory=seed.directory();JsonObject capture=seed.capture();
            if(checkbox){row.addProperty("checked",false);row.remove("actualText");row.remove("textLayout");row.add("textStyle",new JsonObject());for(String rect:List.of("bounds","clippedBounds")){row.getAsJsonObject(rect).addProperty("width",83);row.getAsJsonObject(rect).addProperty("height",42);}row.getAsJsonObject("layout").addProperty("measured_width",83);row.getAsJsonObject("layout").addProperty("measured_height",42);
                directory=RunFiles.directory(work.resolve("checkbox-frame"));var image=new java.awt.image.BufferedImage(1920,720,java.awt.image.BufferedImage.TYPE_INT_RGB);var hardware=javax.imageio.ImageIO.read(Path.of("../voboost-components/src/demo-pixel/java/ru/voboost/components/demo/pixel/MainActivity.screenshots/checkbox-01-free-dark_1original.png").toFile());var g=image.createGraphics();g.drawImage(hardware,515,84,598,126,516,191,599,233,null);g.dispose();javax.imageio.ImageIO.write(image,"png",directory.resolve("primary.png").toFile());var manifest=capture.deepCopy();manifest.remove("artifact_dir");manifest.remove("manifest");RunFiles.write(directory.resolve("manifest.json"),manifest);capture.addProperty("artifact_dir",directory.toString());capture.addProperty("manifest",directory.resolve("manifest.json").toString());}
            var after=before.deepCopy();after.addProperty("observationId","after-"+samples.size());after.add("hostInterval",seed.after().get("hostInterval"));samples.add(new SettingsRasterAcceptance.Sample(capture,directory,before,after,role,value,checkbox?"none":isRadio?"radio":"body"));
        }
        var calibration=GlyphRasterOracle.calibrate(Path.of("../voboost-components").toAbsolutePath().normalize().toRealPath());assertTrue(SettingsRasterAcceptance.verify(inventory,reference,calibration,samples).accepted());
        Path retained=work.resolve("control-accepted.json");SettingsRasterAcceptance.write(retained,inventory,reference,calibration,samples);assertTrue(SettingsRasterAcceptance.readProof(retained,inventory,reference,calibration).accepted());
        assertTrue(samples.stream().map(sample->sample.before().get("captureId").getAsString()).distinct().count()>1);
        assertTrue(samples.stream().map(sample->sample.before().getAsJsonObject("rootBounds").get("y").getAsInt()).distinct().count()>1);
        for(String mutation:List.of("width","after-width","capture-before","capture-after","duplicate")){
            var changed=new ArrayList<>(samples);var original=samples.get(1);var before=original.before();var after=original.after();
            if(mutation.equals("width")||mutation.equals("after-width"))for(var side:mutation.equals("width")?List.of(before,after):List.of(after)){
                var body=side.getAsJsonArray("sectionBodies").get(0).getAsJsonObject();body.addProperty("width",1365);body.getAsJsonObject("actualLayout").addProperty("measured_width",1365);
            }
            if(mutation.startsWith("capture-"))(mutation.endsWith("before")?before:after).getAsJsonArray("sectionBodies").get(0).getAsJsonObject().addProperty("captureId","foreign-capture");
            if(mutation.equals("duplicate"))before.getAsJsonArray("sectionBodies").add(before.getAsJsonArray("sectionBodies").get(0).deepCopy());
            changed.set(1,new SettingsRasterAcceptance.Sample(original.capture(),original.directory(),before,after,original.role(),original.value(),original.model()));
            assertThrows(IOException.class,()->SettingsRasterAcceptance.verify(inventory,reference,calibration,changed),mutation);
        }
        var withoutCheckbox=samples.stream().filter(sample->!sample.role().startsWith("checkbox.")).toList();assertThrows(IOException.class,()->SettingsRasterAcceptance.verify(inventory,reference,calibration,withoutCheckbox));
        for(String field:List.of("paddingTop","paddingBottom","firstChildTop","lastChildBottom","index")){
            var changed=new ArrayList<SettingsRasterAcceptance.Sample>();for(var sample:samples){var before=sample.before();var after=sample.after();for(var side:List.of(before,after))side.getAsJsonArray("sectionBodies").get(0).getAsJsonObject().addProperty(field,99);changed.add(new SettingsRasterAcceptance.Sample(sample.capture(),sample.directory(),before,after,sample.role(),sample.value(),sample.model()));}assertThrows(IOException.class,()->SettingsRasterAcceptance.verify(inventory,reference,calibration,changed),field);
        }
    }

}
