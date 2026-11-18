import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes, createRequire } from "node:module";
import vm from "node:vm";
import { regressionOptions as options, regressionSummary as summary, installedSemanticRequired, partialBootFailure, keyboardDiagnosticRequest } from "../../release/emulator-gate.ts";
const base = new URL("../../release/", import.meta.url);
const gate = readFileSync(new URL("emulator-gate.ts", base), "utf8");
function extract(start, end, extra = {}) {
  const source = gate.slice(gate.indexOf(start), gate.indexOf(end, gate.indexOf(start))).replaceAll("export ", "");
  return vm.runInNewContext(stripTypeScriptTypes(source + "\n;({ regressionOptions: typeof regressionOptions==='function'?regressionOptions:null, regressionSummary:typeof regressionSummary==='function'?regressionSummary:null, functionalEvidence:typeof functionalEvidence==='function'?functionalEvidence:null })"), { visualChecks: () => [], ...extra });
}
const validate = extract("export function functionalEvidence", "function absenceReceipt", { installedSemanticRequired, partialBootFailure }).functionalEvidence;
function evidence(deferred = true) {
 return { imageCapture: deferred?"deferred":undefined, regressionScenario:deferred?"vehicle":undefined, schemaVersion:3, passed:false, invocationId:"test", actions:[], cases:[13,14].map((number,index)=>({number,status:deferred?"unavailable":"passed",actions:[],subchecks:[...["cold-baseline","warm-baseline","armed-current","cold-restore","warm-restore"].map(id=>({id:(index?"pw":"fev")+"-"+id,status:"passed",artifacts:[]})),...(index?[]:["arm","hybrid","save","electric-restore","explicit-off-hold","off-circle","cold-off"].map(id=>({id:"fev-drawer-"+id,status:"passed",artifacts:[]}))),...(deferred?[{id:"vehicle-image-pending",status:"unavailable",artifacts:[]}]:[])]})),boots:[{status:deferred?"unavailable":"passed",semanticStatus:deferred?"passed":undefined,screenshot:deferred?null:"real.png"}] };
}
test("only fixed regression scenarios support deferred mode",()=>{ assert.equal(options("vehicle",{}),false);assert.equal(options("vehicle",{imageCapture:"deferred"}),true);assert.equal(options("settings-radio",{imageCapture:"deferred"}),true);assert.equal(options("launcher",{imageCapture:"deferred"}),true);assert.throws(()=>options("foreign",{imageCapture:"deferred"}));assert.throws(()=>options("vehicle",{imageCapture:"none"})); });
test("all seventeen required checks yield semantic-only acceptance",()=>{const result=summary(evidence(),"vehicle");assert.equal(result.passed,false);assert.equal(result.fullAcceptance,false);assert.equal(result.semanticPassed,true);assert.equal(result.requiredCheckIds.length,17);});
test("each of seventeen semantic failures or omitted checks prevents semantic acceptance",()=>{
 const complete=evidence();
 assert.equal(complete.cases.reduce((count,row)=>count+row.subchecks.filter(check=>check.status==="passed").length,0),17);
 for(const [caseIndex,row] of complete.cases.entries())for(const [checkIndex,check] of row.subchecks.entries()){
  if(check.id==="vehicle-image-pending")continue;
  for(const status of ["failed","unavailable"]){const e=evidence();e.cases[caseIndex].subchecks[checkIndex].status=status;assert.equal(summary(e,"vehicle").semanticPassed,false,check.id+" "+status);}
  const e=evidence();e.cases[caseIndex].subchecks.splice(checkIndex,1);assert.equal(summary(e,"vehicle").semanticPassed,false,check.id+" omitted");
 }
});
test("visual pending and semantic boot are mandatory",()=>{for(const mutate of [e=>e.boots[0].semanticStatus=undefined,e=>e.boots[0].status="passed",e=>e.boots[0].screenshot="fake.png",e=>e.cases[0].subchecks.pop()]){const e=evidence();mutate(e);assert.equal(summary(e,"vehicle").semanticPassed,false);}});
test("default regression acceptance unchanged",()=>{const result=summary(evidence(false),"vehicle");assert.equal(result.passed,true);assert.equal(result.semanticPassed,undefined);});
test("validator refuses full pass, foreign case and native image dispatch",()=>{for(const mutate of [e=>e.passed=true,e=>e.cases[0].status="passed",e=>e.cases[0].number=12,e=>e.actions.push({nativeOperation:"session.screenshot",args:[],artifacts:[]}),e=>e.actions.push({args:[],artifacts:[{kind:"png",path:"/real.png"}]})]){const e=evidence();mutate(e);assert.throws(()=>validate(e));}});
test("semantic boot needs independently validated readiness proof",()=>{assert.throws(()=>validate(evidence()),/independent observation/);});
test("screenshot helper returns before native and installer transport",()=>{const begin=gate.indexOf("const screenshot = async");const end=gate.indexOf("const ",begin+30);assert.match(gate.slice(begin,end),/if \(deferred\) return null/);assert.match(gate,/image dispatch prohibited in deferred mode/);});
const require=createRequire(new URL("../../package.json", import.meta.url));
const Ajv=require("ajv/dist/2020.js").default;
test("schema compiles with strict deferred mode and semantic boot",()=>{const schema=JSON.parse(readFileSync(new URL("emulator-runtime-evidence.schema.json",base),"utf8"));new Ajv({strict:true, strictTypes:false, strictTuples:false}).compile(schema);assert.deepEqual(schema.properties.imageCapture,{const:"deferred"});});

const reporterSource = readFileSync(new URL("emulator-regression.ts",base),"utf8");
const reporterCode = reporterSource.slice(reporterSource.indexOf("export async function runRegressionReport"),reporterSource.indexOf("if (process.argv[1]")).replace("export ","");
const reporter = vm.runInNewContext(stripTypeScriptTypes(reporterCode+";runRegressionReport"), {regressionOptions:options,join:(...p)=>p.join("/"),gateReport:()=> "validated report"});
test("reporter requires explicit deferred mode and emits truthful semantic-only text",async()=>{
 const e=evidence(),s=summary(e,"vehicle");const output=[];
 const io={read:path=>JSON.stringify(path.endsWith("regression-summary.json")?s:e),write:(path,text)=>output.push(text),error:()=>{}};
 assert.equal(await reporter("/e","vehicle",async()=>{},io,{imageCapture:"deferred"}),0);
 assert.match(output[0],/semantic checks passed; visual acceptance pending/);
 assert.equal(await reporter("/e","vehicle",async()=>{},io),2);
 s.semanticPassed=false;assert.equal(await reporter("/e","vehicle",async()=>{},io,{imageCapture:"deferred"}),1);
 s.passed=true;assert.equal(await reporter("/e","vehicle",async()=>{},io,{imageCapture:"deferred"}),2);
});

test("actual NativeSession.capture reaches production record guard before adapter", async()=>{
 const fs=await import("node:fs"),path=await import("node:path"),crypto=await import("node:crypto");
 const {NativeSession}=await import(new URL("../emulator/native-session.ts",import.meta.url).href);
 const root=path.resolve("vehicle-deferred-test-"+process.pid+"-"+Date.now());fs.mkdirSync(root,{mode:0o700});
 try {
  const executable=path.join(root,"voyemu");fs.writeFileSync(executable,"fixture native executable");
  const config={root,executable,profile:"fixture",session:{id:"a".repeat(32),ownership_id:"b".repeat(32)}};
  let calls=0;
  const code=gate.slice(gate.indexOf("  const record = async"),gate.indexOf("  const immutable =",gate.indexOf("  const record = async")));
  const record=vm.runInNewContext(stripTypeScriptTypes(code+";record"),{
   // Production record closes over the diagnostic predicate and a null scope outside keyboard routing.
   keyboardDiagnosticRequest,keyboardCaptureScope:null,
   deferred:true,firstBootCaptureDeadline:null,snapshotReadbackDeadline:null,settingsNavigationDeadline:null,evidence:{actions:[],cases:[]},caseNumber:23,
   process,Buffer,nativeConfig:config,regular:()=>{},readFileSync:fs.readFileSync,join:path.join,
   adapter:async()=>{calls++;throw new Error("adapter must not receive capture");},
   c:{output:root},writeFileSync:fs.writeFileSync,digest:bytes=>crypto.createHash("sha256").update(bytes).digest("hex"),
   persist:()=>{},regression:"settings-radio",assertRegressionInvocation:()=>{},MAX_CAPTURE:1048576,serial:"emulator-5554"
  });
  const client=new NativeSession(config,path.join(root,"requests"),record,()=>({width:1920,height:720}));
  // Capture's independent status is already admitted; exercise its real capture dispatch.
  client.status=async()=>({pid:123,sessionId:config.session.id,serial:"emulator-5554"});
  await assert.rejects(()=>client.capture(path.join(root,".local/build/tmp/capture")),/native image dispatch prohibited/);
  assert.equal(calls,0);
 } finally {fs.rmSync(root,{recursive:true,force:true});}
});

function settingsEvidence() {
 const ids=["6:language-opposite-selected","7:language-original-restored",...["language-loop-copy-dialog-cancel","language-process-resource-locale","i18n-system-picker-multi-owner","language-reboot-persistence"].map(id=>"22:"+id),"23:host-all-startup-description-copy","23:cancel-does-not-uninstall",...["en","ru","zh"].flatMap(locale=>["interface","hidden","none"].flatMap(mode=>["23:startup-"+mode+"-native-draw-transition-"+locale,"23:startup-"+mode+"-description-"+locale]))];
 return {...evidence(),regressionScenario:"settings-radio",cases:[6,7,22,23].map(number=>({number,status:"unavailable",actions:[],subchecks:[...ids.filter(id=>id.startsWith(number+":")).map(id=>({id:id.split(":")[1],status:"passed",artifacts:["actual-state.json"]})),{id:"settings-radio-visual-pending",status:"unavailable",artifacts:[]},...(number===22?[{id:"destructive-error-copy-excluded",status:"unavailable",artifacts:[]}]:number===23?[{id:"fault-rejection-excluded",status:"unavailable",artifacts:[]},{id:"startup-rejected-mutation",status:"unavailable",artifacts:[]}]:[])]}))};
}
test("settings-radio summary retains canonical required IDs and pending visual acceptance",()=>{
 const e=settingsEvidence(), result=summary(e,"settings-radio");assert.equal(result.semanticPassed,true);assert.equal(result.passed,false);assert.equal(result.fullAcceptance,false);assert.equal(result.publication,"forbidden");assert.equal(result.requiredCheckIds.length,26);assert.deepEqual(Array.from(result.pendingAcceptance),["images","animation","visual"]);
 for (const mutate of [e=>e.cases[0].subchecks[0].artifacts=[],e=>e.cases[2].subchecks.shift(),e=>e.cases[3].subchecks[0].status="unavailable",e=>e.cases[0].subchecks[1].status="passed",e=>e.boots[0].screenshot="fake.png"]) {const bad=settingsEvidence();mutate(bad);assert.equal(summary(bad,"settings-radio").semanticPassed,false);}
 assert.throws(()=>summary(e,"unknown"));assert.throws(()=>options("unknown",{imageCapture:"deferred"}));
});
test("settings-radio reporter refuses visual claims",async()=>{
 const e=settingsEvidence(),s=summary(e,"settings-radio");const io={read:path=>JSON.stringify(path.endsWith("regression-summary.json")?s:e),write:()=>{},error:()=>{}};
 assert.equal(await reporter("/e","settings-radio",async()=>{},io,{imageCapture:"deferred"}),0);s.pendingAcceptance=[];assert.equal(await reporter("/e","settings-radio",async()=>{},io,{imageCapture:"deferred"}),2);
});

 test("postboot schema requires deferred Settings mode and rejects mixed vehicle scope",()=>{
 const schema=JSON.parse(readFileSync(new URL("emulator-runtime-evidence.schema.json",base),"utf8"));
 const branch=schema.allOf.find(rule=>rule.if?.required?.includes("settingsPart"));
 assert.ok(branch);
 const validate=new Ajv({strict:true,strictTypes:false}).compile({type:"object",properties:{settingsPart:{const:"postboot"},imageCapture:{const:"deferred"},regressionScenario:{enum:["settings-radio","vehicle"]},vehiclePart:{const:"pw"}},additionalProperties:false,allOf:[branch]});
 assert.equal(validate({}),true);
 assert.equal(validate({settingsPart:"postboot",imageCapture:"deferred",regressionScenario:"settings-radio"}),true);
 for(const value of [{settingsPart:"postboot"},{settingsPart:"postboot",imageCapture:"deferred",regressionScenario:"vehicle"},{settingsPart:"postboot",imageCapture:"deferred",regressionScenario:"settings-radio",vehiclePart:"pw"},{settingsPart:"unknown",imageCapture:"deferred",regressionScenario:"settings-radio"}]) assert.equal(validate(value),false);
 });
test("deferred launcher validator rejects native capture, image artifacts and full visual pass",()=>{
 for(const mutate of [(e:any)=>e.actions.push({nativeOperation:"session.screenshot",args:[],artifacts:[]}),(e:any)=>e.actions.push({args:[],artifacts:[{kind:"png",path:"/image.png"}]}),(e:any)=>e.passed=true,(e:any)=>e.cases[0].status="passed"]){
 const e:any={imageCapture:"deferred",regressionScenario:"launcher",passed:false,cases:[{number:12,status:"unavailable"},{number:18,status:"unavailable"}],actions:[],boots:[]};mutate(e);assert.throws(()=>validate(e));
 }
});
