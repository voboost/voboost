import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { join, resolve } from "node:path";
import { launcherFinallyCleanup, assertRegressionInvocation, ownedFixtureCleanupAllowed, regressionSummary, EXECUTION_ORDER } from "../../release/emulator-gate.ts";
const root = resolve(import.meta.dirname, "../..");
const plan = JSON.parse(readFileSync(join(root, "release/beta1-functional-plan.json"), "utf8"));
const receipt = { owned: true, package: plan.fixture.package, source: plan.fixture.source, originalAbsenceAction: 10, installAction: 12 };
test("only exact absence followed by successful owned install permits removal", () => {
 assert.equal(ownedFixtureCleanupAllowed(receipt), true);
 assert.equal(ownedFixtureCleanupAllowed({...receipt, source: "tests/fixtures/launcher"}), true);
 for (const mutation of [{owned:false},{package:"foreign"},{source:"foreign"},{originalAbsenceAction:null},{installAction:null},{installAction:9},{originalAbsenceAction:0}]) assert.equal(ownedFixtureCleanupAllowed({...receipt,...mutation}),false);
});
test("launcher cannot dispatch Voboost install or uninstall", () => {
 for(const flag of ["--install","--uninstall","--uninstall-emergency"]) assert.throws(()=>assertRegressionInvocation({args:[flag]} as any,"launcher"));
 for(const action of ["fault-window","observe-settings-uninstall","install-fixture","remove-fixture"]) {
 const scratch=join(root,"build","launcher-regression-test-"+process.pid+"-"+action);mkdirSync(scratch,{recursive:true});const path=join(scratch,"request.json");writeFileSync(path,JSON.stringify({action}));
 const invocation={args:["--emulator-check",path]} as any;
 if(action.endsWith("fixture")) {assert.doesNotThrow(()=>assertRegressionInvocation(invocation,"launcher"));assert.throws(()=>assertRegressionInvocation(invocation,"vehicle"));}
 else assert.throws(()=>assertRegressionInvocation(invocation,"launcher"));
 rmSync(scratch,{recursive:true});
 }
});
test("full27 remains unchanged and launcher summary cannot assert full acceptance",()=>{
 assert.deepEqual(EXECUTION_ORDER,[1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26]);
 const summary=regressionSummary({cases:[],boots:[]},"launcher");assert.equal(summary.passed,false);assert.equal(summary.fullAcceptance,false);assert.equal(summary.publication,"forbidden");
 assert.throws(()=>regressionSummary({cases:[]},"arbitrary" as any));
});
test("production finally cleanup executes once and preserves original failure even if cleanup fails", async()=>{
 for (const cleanupFails of [false,true]) {
  let calls=0;const original=new Error("case12 drag failed"), cleanupError=new Error("removal failed");let result:any;
  await assert.rejects(async()=>{try {throw original;} finally {
   result=await launcherFinallyCleanup({scenario:"launcher",receipt,activeOperation:null,monitorHealthy:true,leaseStatus:"retained"},async()=>{calls++;if(cleanupFails)throw cleanupError;});
  }},error=>error===original);
  assert.equal(calls,1);assert.equal(result.attempted,true);assert.equal(result.error,cleanupFails?cleanupError:null);
 }
});
test("production cleanup blocks active operation, unhealthy monitor, foreign/missing ownership and released lease",async()=>{
 const admitted={scenario:"launcher" as const,receipt,activeOperation:null,monitorHealthy:true,leaseStatus:"retained"};
 for(const mutation of [{activeOperation:{id:1}},{monitorHealthy:false},{leaseStatus:"released"},{scenario:"vehicle" as const},{receipt:{...receipt,owned:false}},{receipt:{...receipt,package:"foreign"}},{receipt:{...receipt,installAction:null}},{receipt:{...receipt,status:"passed"}}]) {
  let calls=0;const result=await launcherFinallyCleanup({...admitted,...mutation},async()=>{calls++;});assert.equal(calls,0);assert.deepEqual(result,{attempted:false,error:null});
 }
});

import { regressionOptions } from "../../release/emulator-gate.ts";
import { visualChecks } from "../emulator/visual-review.ts";
test("deferred launcher accepts only semantic complete evidence and never visual acceptance",()=>{
 assert.equal(regressionOptions("launcher",{imageCapture:"deferred"}),true);
 assert.throws(()=>regressionOptions("launcher",{imageCapture:"deferred",vehiclePart:"pw"}));
 const check=(id:string,status="passed")=>({id,status,reason:"observed",artifacts:["receipt.json"]});
 const evidence:any={imageCapture:"deferred",regressionScenario:"launcher",cases:[
  {number:12,status:"unavailable",subchecks:[check("launcher-visible-page-order-after-reboot"),check("launcher-semantic-surfaces-lifetime"),check("launcher-two-screen-disk-cache-after-reboot"),check("launcher-theme-cache-roundtrip"),...visualChecks(12).map(c=>check(c.id,"unavailable")),check("launcher-visual-pending","unavailable")]},
  {number:18,status:"unavailable",subchecks:[check("fresh-package-classification"),check("owned-fixture-cleanup"),check("fixture-oem-grid-retained"),...['oem-stock-baseline','oem-grid-retained','oem-icon-bind-provenance'].map(id=>check(id,"unavailable")),...visualChecks(18).map(c=>check(c.id,"unavailable")),check("launcher-visual-pending","unavailable")]}
 ],boots:[{status:"unavailable",semanticStatus:"passed",screenshot:null}]};
 const summary=regressionSummary(evidence,"launcher"); assert.equal(summary.semanticPassed,true);assert.equal(summary.passed,false);assert.equal(summary.fullAcceptance,false);assert.ok(summary.pendingAcceptance.includes("lighting"));
 for(const mutate of [(e:any)=>e.cases[0].subchecks.shift(),(e:any)=>{const pending=e.cases[0].subchecks.filter((c:any)=>c.status==="unavailable");e.cases[0].subchecks=e.cases[0].subchecks.filter((c:any)=>c.id!==pending[0].id);e.cases[0].subchecks.push(structuredClone(pending.at(-1)));},(e:any)=>e.cases[0].subchecks[1].artifacts=[],(e:any)=>e.cases[0].subchecks.push(check("unexpected")),(e:any)=>e.boots[0].screenshot="fake.png",(e:any)=>e.cases[0].subchecks.find((c:any)=>c.id==="launcher-visual-pending").status="passed"]){const altered=structuredClone(evidence);mutate(altered);assert.equal(regressionSummary(altered,"launcher").semanticPassed,false);}
});

import { launcherThemeRoundTrip } from "../../release/emulator-gate.ts";
test("native theme roundtrip restores exact keys from memory or disk without rendering",()=>{
 const pairs=(style:string)=>[0,1].map(screen=>({screen,cache_key:style+screen,source_identity:"source",style_identity:style,boot:"boot",generationId:"generation",catalogSha256:"catalog"}));
 const phases:any=[{label:"Classic",style:"metal",pairs:pairs("metal"),diskSources:["disk","disk"]},{label:"Speedy Dash",style:"color",pairs:pairs("color"),diskSources:["render","render"]},{label:"Classic",style:"metal",pairs:pairs("metal"),diskSources:["disk","disk"]}];
 assert.doesNotThrow(()=>launcherThemeRoundTrip(phases));
 for(const sources of [["memory","memory"],["memory","disk"],["disk","memory"]]) {const warm=structuredClone(phases);warm[2].diskSources=sources;assert.doesNotThrow(()=>launcherThemeRoundTrip(warm));}
 const rightConstant=structuredClone(phases);rightConstant[1].pairs[1]=structuredClone(rightConstant[0].pairs[1]);assert.doesNotThrow(()=>launcherThemeRoundTrip(rightConstant));
 for(const mutate of [(p:any)=>p[1].pairs[0].cache_key=p[0].pairs[0].cache_key,(p:any)=>p[1].pairs[0].style_identity="metal",(p:any)=>p[2].pairs[0].cache_key="stale",(p:any)=>p[1].pairs[0].source_identity="changed",(p:any)=>p[2].pairs[1].boot="newboot",(p:any)=>p[1].style="metal",(p:any)=>p[2].pairs.pop(),(p:any)=>p[2].diskSources[0]="render",(p:any)=>p[2].diskSources[1]="unknown",(p:any)=>p[2].diskSources.pop()]){const p=structuredClone(phases);mutate(p);assert.throws(()=>launcherThemeRoundTrip(p));}
});

import { launcherThemeRestoreAllowed } from "../../release/emulator-gate.ts";
test("theme restoration refuses retained transaction, installer, released lease and unhealthy monitor",()=>{
 const valid={activeOperation:null,installerActive:false,faultLive:false,monitorHealthy:true,leaseStatus:"retained"};assert.equal(launcherThemeRestoreAllowed(valid),true);
 for(const changed of [{activeOperation:{id:"unknown-child"}},{activeOperation:undefined},{installerActive:true},{faultLive:true},{monitorHealthy:false},{leaseStatus:"released"}])assert.equal(launcherThemeRestoreAllowed({...valid,...changed}),false);
});

test("theme proof uses native session.ui and binds the exact final window frame",async()=>{
 const {stripTypeScriptTypes}=await import("node:module");
 const source=readFileSync(join(root,"release/emulator-gate.ts"),"utf8");
 const start=source.indexOf("export function launcherThemeSnapshotProof("),end=source.indexOf("export function selectedLauncherTheme(",start);
 const compile=Function("nativeTreeAction","uniqueNode","selectedLauncherTheme","readFileSync","digest","jsonReceipt","parseUi",stripTypeScriptTypes(source.slice(start,end)).replace("export function","function")+"return launcherThemeSnapshotProof;");
 const w={id:17,rootPackage:"com.qinggan.app.setting",type:1,inputFocused:true,frameSha256:"frame",captureStart:10,captureEnd:20,capture:{source:"native",observationId:"one"}};
 const nodes=[{resource:"com.qinggan.app.setting:id/recyclerview_theme_pic",windowRef:w}];
 let calls=0;
 const native=(e,a)=>{calls++;if(a.invalid)throw new Error("native retained authority differs");return nodes;};
 const digest=(bytes)=>(awaitDigest)(bytes);
 const {createHash}=await import("node:crypto");
 const awaitDigest=bytes=>createHash("sha256").update(bytes).digest("hex");
 const proof=compile(native,(rows,p)=>{const matches=rows.filter(p);assert.equal(matches.length,1);return matches[0];},()=>({label:"Classic",style:"metal"}),readFileSync,digest,JSON.parse,()=>nodes);
 const action={nativeOperation:"session.ui",exitCode:0,context:{bootId:"boot"},artifacts:[{kind:"stdout",path:"native-response",sha256:"hash"}]};
 assert.deepEqual(proof({},action,nodes,"boot"),{path:"native-response",sha256:"hash"});assert.equal(calls,1);
 for(const changed of [{...action,context:{bootId:"other"}},{...action,artifacts:[]},{...action,invalid:true}])assert.throws(()=>proof({},changed,nodes,"boot"));
 for(const change of [{frameSha256:"other"},{id:18},{captureStart:11},{capture:{source:"native",observationId:"other"}}])assert.throws(()=>proof({},action,[{...nodes[0],windowRef:{...w,...change}}],"boot"),/authority differs/);
 const scratch=join(root,"build","theme-proof-test-"+process.pid);mkdirSync(scratch,{recursive:true});
 try{
  const request=join(scratch,"request.json"),response=join(scratch,"response.json");
  writeFileSync(request,JSON.stringify({action:"snapshot"}));writeFileSync(response,JSON.stringify({schemaVersion:1,serial:"emulator-5574",body:"validated-xml"}));
  const legacy={exitCode:0,args:["--emulator-check",request],context:{bootId:"boot"},artifacts:[{kind:"request",path:request,sha256:digest(readFileSync(request))},{kind:"stdout",path:response,sha256:digest(readFileSync(response))}]};
  assert.equal(proof({runtime:{serial:"emulator-5574"}},legacy,nodes,"boot").path,response);
  assert.throws(()=>proof({runtime:{serial:"other"}},legacy,nodes,"boot"),/receipt differs/);
  writeFileSync(response,"tampered");assert.throws(()=>proof({runtime:{serial:"emulator-5574"}},legacy,nodes,"boot"),/bytes differ/);
 }finally{rmSync(scratch,{recursive:true});}
 assert(source.includes("evidence.actions.slice(firstThemeSnapshot).findLast"));
});


test("theme restore caller maps absent diagnostic window to boolean false",()=>{
 const source=readFileSync(join(root,"release/emulator-gate.ts"),"utf8");
 const expression=source.match(/launcherThemeRestoreAllowed\((\{ activeOperation: evidence\.activeOperation[^\n]+? \})\)/)?.[1];assert(expression);
 const call=Function("launcherThemeRestoreAllowed","evidence","installerActive","faultLive","monitorResult","return launcherThemeRestoreAllowed("+expression+");");
 const evidence={activeOperation:null,leaseStatus:"retained"};
 assert.equal(call(launcherThemeRestoreAllowed,evidence,false,null,null),true);
 assert.equal(call(launcherThemeRestoreAllowed,evidence,false,()=>{},null),false);
 assert.equal(call(launcherThemeRestoreAllowed,evidence,true,null,null),false);
});


import { stripTypeScriptTypes } from "node:module";
const inventorySource = readFileSync(join(root,"release/emulator-gate.ts"),"utf8");
const inventoryBody = inventorySource.slice(
 inventorySource.indexOf("  const launcherVisibleInventory = async () => {"),
 inventorySource.indexOf("  let vehicleNodes: UiNode[] = [];"),
);
assert(inventoryBody.startsWith("  const launcherVisibleInventory"));
const inventoryCaseAssertion = inventorySource.match(/const missing = originalOemItems\.filter\(label => !current\.includes\(label\)\);\n\s*if \(missing\.length\) throw new Error\([^\n]+/);
assert(inventoryCaseAssertion);
const assertInventoryRetained = Function("originalOemItems","current",inventoryCaseAssertion[0]);
function visibleInventoryHarness(pages:string[][], options:{cycle?:boolean;windowDrift?:boolean;bootDrift?:boolean;delayedReads?:number;invisible?:string[]}={}) {
 let pageIndex=0, clock=0, bootReads=0, pendingReads=0, pendingIndex=0, gestures=0, snapshots=0;
 let latest:any;
 const harness:any={
  boot:async()=>({boot:options.bootDrift && ++bootReads>1?"changed":"boot"}),
  page:async()=>{
   snapshots++;
   if(pendingReads>0){pendingReads--;if(!pendingReads)pageIndex=pendingIndex;}
   const windowRef={id:options.windowDrift && pageIndex>0?8:7,type:1,bounds:[0,0,1920,720]};
   latest={grid:{windowRef},items:pages[pageIndex].map((label,index)=>({label,node:{bounds:[index*100,0,index*100+90,90]}})),
    // Invisible nodes are not launcherPage.items and must never enter inventory.
    nodes:(options.invisible??[]).map(label=>({text:label,visibleToUser:false}))};
   return latest;
  },
  gesture:async(request:any)=>{
   gestures++;assert.equal(request.authorityGrid,latest.grid);
   assert.equal(request.action,"gesture");assert.equal(request.milliseconds,400);
   assert(request.x1>request.x2);
   const target=pageIndex+1<pages.length?pageIndex+1:options.cycle?0:pageIndex;
   if(options.delayedReads && target!==pageIndex){pendingReads=options.delayedReads;pendingIndex=target;}
   else pageIndex=target;
  },
  now:()=>clock,
  delay:(resolve:()=>void,ms:number)=>{clock+=ms;resolve();},
 };
 const body=stripTypeScriptTypes(
  "(async()=>{let launcherCurrentPage; const native=true;"+
  "const bootObservation=harness.boot;"+
  "const launcherPage=async()=>{launcherCurrentPage=await harness.page();return launcherCurrentPage;};"+
  "const launcherGrid=async(first)=>{if(first!==true)throw new Error('not first page');return(await launcherPage()).items;};"+
  "const unavailable=(message)=>{throw new Error('UNAVAILABLE: '+message);};"+
  "const ui={call:harness.gesture,launcherPointerAuthority:(grid)=>({authorityGrid:grid})};"+
  "const performance={now:harness.now};const setTimeout=harness.delay;"+
  inventoryBody+"return launcherVisibleInventory();})()",
  {mode:"strip"},
 );
 return {run:()=>Function("harness","return "+body)(harness),
  counts:()=>({gestures,snapshots})};
}
test("visible OEM inventory follows displaced Settings onto next page",async()=>{
 const baseline=["Cloud Space","Map","Music","Settings"];
 const h=visibleInventoryHarness([["Voboost Fixture","Cloud Space","Map","Music"],["Settings","APKPure"]]);
 const found=await h.run();assert.deepEqual(found,["Voboost Fixture","Cloud Space","Map","Music","Settings","APKPure"]);
 assert.doesNotThrow(()=>assertInventoryRetained(baseline,found));assert.equal(h.counts().gestures,2);
 assert(inventorySource.includes("const current = await launcherVisibleInventory();"));
 assert(inventorySource.includes("const retained = await launcherVisibleInventory();"));
});
test("visible OEM inventory fails real missing item and ignores invisible neighbor",async()=>{
 for(const invisible of [[],["Settings"]]){
  const h=visibleInventoryHarness([["Voboost Fixture","Cloud Space"],["Music"]],{invisible});
  const found=await h.run();
  assert.throws(()=>assertInventoryRetained(["Cloud Space","Music","Settings"],found),/disappeared: \["Settings"\]/);
 }
});
test("visible OEM inventory rejects nonterminal page cycle",async()=>{
 const h=visibleInventoryHarness([["Cloud Space"],["Music"]],{cycle:true});
 await assert.rejects(h.run(),/UNAVAILABLE: launcher inventory repeated a nonterminal page/);
 assert.equal(h.counts().gestures,2);
});
test("visible OEM inventory keeps twelve page bound",async()=>{
 const h=visibleInventoryHarness(Array.from({length:13},(_,i)=>["page"+i]));
 await assert.rejects(h.run(),/UNAVAILABLE: launcher inventory exceeded bounded twelve-page/);
 assert.equal(h.counts().gestures,12);
});
test("visible OEM inventory rejects boot and window drift",async()=>{
 for(const options of [{bootDrift:true},{windowDrift:true}]){
  const h=visibleInventoryHarness([["Cloud Space"],["Settings"]],options);
  await assert.rejects(h.run(),/UNAVAILABLE: launcher inventory (crossed a boot|window changed)/);
 }
});
test("visible OEM inventory settles delayed animation without input replay",async()=>{
 const h=visibleInventoryHarness([["Cloud Space"],["Settings"]],{delayedReads:3});
 assert.deepEqual(await h.run(),["Cloud Space","Settings"]);
 assert.equal(h.counts().gestures,2);assert(h.counts().snapshots>=5);
});
test("visible OEM inventory cleanup detects removed fixture on another page",async()=>{
 const h=visibleInventoryHarness([["Cloud Space"],["Settings","Voboost Fixture"]]);
 const retained=await h.run();
 const assertion=inventorySource.match(/if \(retained\.some\(\(label\) => \/Voboost Fixture\/i\.test\(label\)\)\) throw new Error\([^\n]+/);
 assert(assertion);
 assert.throws(()=>Function("retained",assertion[0])(retained),/removed fixture remains in fresh OEM grid/);
});
