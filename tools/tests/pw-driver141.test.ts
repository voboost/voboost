import test from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";
import { createHash } from "node:crypto";
import { stripTypeScriptTypes } from "node:module";
import vm from "node:vm";
const root = process.env.PW_SOURCE || path.resolve(import.meta.dirname, "../..");
const gate = fs.readFileSync(path.join(root,"release/emulator-gate.ts"),"utf8");
function extract(start, end, result, context = {}) {
 const begin=gate.indexOf(start); assert.notEqual(begin,-1);
 return vm.runInNewContext(stripTypeScriptTypes(gate.slice(begin,gate.indexOf(end,begin)).replaceAll("export ","")+";"+result),context);
}
const { regressionOptions: options, regressionSummary: summary, installedSemanticRequired, retainTimedOutDeviceOperation } = await import(path.join(root,"release/emulator-gate.ts"));
const digest=bytes=>createHash("sha256").update(bytes).digest("hex");
test("fixed PW subset selects exact five IDs and requires deferred vehicle",()=>{
 assert.equal(options("vehicle",{imageCapture:"deferred",vehiclePart:"pw"}),true);
 for(const value of [{vehiclePart:"pw"},{imageCapture:"deferred",vehiclePart:"fev"},{imageCapture:"deferred",filter:[14]}]) assert.throws(()=>options("vehicle",value));
 assert.throws(()=>options("launcher",{imageCapture:"deferred",vehiclePart:"pw"}));
 const e={vehiclePart:"pw",imageCapture:"deferred",regressionScenario:"vehicle",cases:[{number:13,status:"not-run",subchecks:[]},{number:14,status:"unavailable",subchecks:[...["cold-baseline","warm-baseline","armed-current","cold-restore","warm-restore"].map(id=>({id:"pw-"+id,status:"passed"})),{id:"vehicle-image-pending",status:"unavailable"}]}],boots:[{status:"unavailable",semanticStatus:"passed",screenshot:null}]};
 const s=summary(e,"vehicle"); assert.equal(s.semanticPassed,true); assert.equal(s.fullAcceptance,false);assert.equal(s.passed,false);
 assert.deepEqual(Array.from(s.requiredCheckIds),["cold-baseline","warm-baseline","armed-current","cold-restore","warm-restore"].map(id=>"14:pw-"+id));
 for (const regressionScenario of [undefined,"settings-radio","unknown"]) assert.throws(()=>summary({...e,regressionScenario},"vehicle"),/deferred scenario differs/);
 e.cases[0].status="unavailable";assert.throws(()=>summary(e,"vehicle"));
});
test("real record retains failed request before dispatch and rejects subsequent modification",async()=>{
 const temporary=path.join(root,"build/pw-record-tests");fs.mkdirSync(temporary,{recursive:true});
 const output=fs.mkdtempSync(path.join(temporary,"pw-record-"));
 try {
 const file=path.join(output,"request.json");fs.writeFileSync(file,JSON.stringify({action:"tap"}));const before=digest(fs.readFileSync(file));
 const evidence={imageCapture:"deferred",regressionScenario:"vehicle",passed:false,actions:[],cases:[],boots:[]};
 let dispatched=0;
 const record=extract("  const record = async","  const immutable =","record",{deferred:true,firstBootCaptureDeadline:null,snapshotReadbackDeadline:null,settingsNavigationDeadline:null,retainTimedOutDeviceOperation,evidence,caseNumber:14,process,Buffer,nativeConfig:null,regular:()=>{},readFileSync:fs.readFileSync,join:path.join,c:{output},writeFileSync:fs.writeFileSync,digest,persist:()=>{},regression:"vehicle",assertRegressionInvocation:()=>{},MAX_CAPTURE:1048576,serial:"serial",adapter:async()=>{dispatched++;assert.equal(evidence.actions[0].artifacts.filter(a=>a.kind==="request").length,1);assert.equal(evidence.actions[0].artifacts[0].sha256,before);return {exitCode:1,stdout:"",stderr:"original tap failure"};}});
 const result=await record({executable:"installer",args:["--emulator-check",file],timeoutMs:1000}); assert.equal(result.exitCode,1);assert.equal(dispatched,1);assert.equal(evidence.actions[0].artifacts.filter(a=>a.kind==="request").length,1);
 const validate=extract("export function functionalEvidence","function absenceReceipt","functionalEvidence",{digest,readFileSync:fs.readFileSync,installedSemanticRequired});
 assert.doesNotThrow(()=>validate(evidence));fs.writeFileSync(file,JSON.stringify({action:"screenshot"}));assert.throws(()=>validate(evidence),/request authority missing/);
 } finally {fs.rmSync(output,{recursive:true,force:true});}
});
test("fresh navigation recovery retries once only certified pre-input drift",async()=>{
 const {GesturePreInputDrift}=await import(path.join(root,"tools/emulator/installer-ui.ts"));
 for (const kind of ["safe","unknown","twice"]) {
 const events=[],first={packageName:"com.qinggan.app.vehiclesetting",resource:"com.qinggan.app.vehiclesetting:id/rb_menu_safety_maintenance",clickable:true},fresh={...first};let snaps=0,taps=0;
 const ui={call:async request=>events.push(request.action),snapshot:async()=>[++snaps===1?first:fresh],tap:async node=>{events.push(node===first?"tap-first":"tap-fresh");if(++taps===1||kind==="twice")throw kind==="unknown"?new Error("unknown mutation"):new GesturePreInputDrift("guard");}};
 const open=extract("  const openVehicleMenu = async","  const oemValue =","openVehicle",{performance,settingsNavigationDeadline:null,ownership:async()=>{},vehicleLaunchPending:()=>false,ui,focusedApplication:()=>true,uniqueNode:()=>{throw new Error("unexpected scroll");},unavailable:message=>{throw new Error(message);},GesturePreInputDrift,oemFeature:()=>({bounds:[0,0,1,1]}),vehicleNodes:[]});
 if(kind==="safe") {await open("pw");assert.deepEqual(events,["open-vehicle-settings","tap-first","open-settings","open-vehicle-settings","tap-fresh"]);} else {await assert.rejects(()=>open("pw"));assert.equal(taps,kind==="twice"?2:1);}
 }
});
test("known diagnostic prefix admitted; mixed reordered and post-input errors refused",async()=>{
 const m=await import(path.join(root,"tools/emulator/installer-ui.ts"));assert.equal(typeof m.safeGestureDiagnostics,"function");
 const terminal="ui-observation stage=gesture reason=gesture-window-or-target-drift kind=java.lang.IllegalStateException syscall=none errno=none windows=1 window-reads=4 nodes=45 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=88 child-ms=3";
 const prefix="ui-observation stage=gesture reason=active-root-outside-default-display kind=none syscall=none errno=none windows=1 window-reads=4 nodes=45 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=88 child-ms=3";
 assert.equal(m.safeGestureDiagnostics(terminal),true);assert.equal(m.safeGestureDiagnostics(prefix+"\n"+terminal),true);
 for(const text of [prefix,terminal+"\n"+prefix,prefix+"\n"+terminal+"\n"+terminal,terminal.replace("gesture-window-or-target-drift","input-injection-failed"),terminal+"\nunknown mutation",terminal+"\nui-observation stage=input reason=dispatch kind=none syscall=none errno=none windows=1"])assert.equal(m.safeGestureDiagnostics(text),false);
});

test("selected menu skips input only with a fresh same-window focused feature",async()=>{
 const {GesturePreInputDrift}=await import(path.join(root,"tools/emulator/installer-ui.ts"));
 for(const mode of ["selected","checked","unselected","other-window","no-feature"]) {
 const win={};const menu={packageName:"com.qinggan.app.vehiclesetting",resource:"com.qinggan.app.vehiclesetting:id/rb_menu_safety_maintenance",clickable:true,selected:mode!=="unselected"&&mode!=="checked",checked:mode==="checked",windowRef:win};let taps=0;
 const feature={windowRef:mode==="other-window"?{}:win,bounds:[0,0,1,1]};
 const open=extract("  const openVehicleMenu = async","  const openVehicle =","openVehicleMenu",{performance,settingsNavigationDeadline:null,ownership:async()=>{},vehicleLaunchPending:()=>false,ui:{call:async()=>{},snapshot:async()=>[menu],tap:async()=>{taps++;}},focusedApplication:()=>true,unavailable:message=>{throw new Error(message);},oemFeature:()=>{if(mode==="no-feature")throw new Error("absent");return feature;},vehicleNodes:[]});
 await open("pw");assert.equal(taps,["selected","checked"].includes(mode)?0:1);
 }
});

test("reporter refuses missing or mismatched PW selection",async()=>{
 const source=fs.readFileSync(path.join(root,"release/emulator-regression.ts"),"utf8");const begin=source.indexOf("export async function runRegressionReport");
 const reporter=vm.runInNewContext(stripTypeScriptTypes(source.slice(begin,source.indexOf("if (process.argv[1]",begin)).replace("export ","")+";runRegressionReport"),{regressionOptions:options,join:path.join,gateReport:()=>"report"});
 const evidence={passed:false,invocationId:"id",imageCapture:"deferred",vehiclePart:"pw"};const summary={...evidence,evidenceType:"installed-kit-partial-regression",scenario:"vehicle",fullAcceptance:false,publication:"forbidden",semanticPassed:true};
 const io={read:file=>JSON.stringify(file.endsWith("regression-summary.json")?summary:evidence),write:()=>{},error:()=>{}};
 assert.equal(await reporter("/out","vehicle",async()=>{},io,{imageCapture:"deferred",vehiclePart:"pw"}),0);
 assert.equal(await reporter("/out","vehicle",async()=>{},io,{imageCapture:"deferred"}),2);
 evidence.vehiclePart=undefined;assert.equal(await reporter("/out","vehicle",async()=>{},io,{imageCapture:"deferred",vehiclePart:"pw"}),2);
});
test("actual InstallerUi only certifies real action691 pre-input abort",async()=>{
 const {InstallerUi,GesturePreInputDrift}=await import(path.join(root,"tools/emulator/installer-ui.ts"));
 const fixtureRoot=path.join(root,"testdata/emulator/drift141");
 const request=JSON.parse(fs.readFileSync(path.join(fixtureRoot,"request.json"),"utf8"));const stderr=fs.readFileSync(path.join(fixtureRoot,"stderr.txt"),"utf8");
 const output=fs.mkdtempSync(path.join(process.env.TMPDIR||"/tmp","pw-classify-"));
 try {
 for(const [i,result] of [{exitCode:1,stderr,stdout:"[18:55:20.019 INFO] Connecting to the head unit over USB\n[18:55:20.155 INFO] Connected to the head unit: emulator-5562\n"},{exitCode:1,stderr:stderr+"unknown mutation\n",stdout:"[18:55:20.019 INFO] Connecting to the head unit over USB\n[18:55:20.155 INFO] Connected to the head unit: emulator-5562\n"},{exitCode:1,stderr,stdout:"{}\n"},{exitCode:1,stderr,stdout:"",timedOut:true},{exitCode:1,stderr,stdout:""},{exitCode:1,stderr,stdout:"\n\r\n"}].entries()) {
 const ui=new InstallerUi("/package/voboost-installer","emulator-5562",path.join(output,String(i)),async()=>result,async()=>{});
 await assert.rejects(()=>ui.call(request),error=>i===0?error instanceof GesturePreInputDrift:!(error instanceof GesturePreInputDrift));
 }
 } finally {fs.rmSync(output,{recursive:true,force:true});}
});
