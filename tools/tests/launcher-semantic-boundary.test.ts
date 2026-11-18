import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { InstallerUi, parseUi, uniqueNode, launcherPointerNode, focusedApplication, validateRequest, agentControl } from "../emulator/installer-ui.ts";
import { launcherItems, launcherFirstPage } from "../../release/emulator-gate.ts";
import { LauncherPageUnavailable, launcherSemanticBoundary, launcherPageReceipt, launcherPageLifetime, launcherPageHierarchy } from "../emulator/launcher-page-observation.ts";

const xml = readFileSync(new URL("fixtures/launcher133-187.xml", import.meta.url), "utf8");
const bootId = "11111111-1111-1111-1111-111111111111";
const generationId = "a".repeat(64), catalogSha256 = "b".repeat(64);
function boot() {
  const resident = {schemaVersion:1, releaseIdentity:"1.0.0-beta1",manifestSha256:"c".repeat(64),generationId,catalogSha256,startupMode:"none",state:"ready",killed:false,panic:false,injections:[],executors:[]};
  return {boot:bootId,bootCompleted:true,body:`oem-target-processes:\nPID NAME\n41 com.qinggan.app.launcher\nandroid-art-fatal-context:\n\nresident-status:\n/data/voboost/status.json\n${JSON.stringify(resident)}\n`};
}
function page(serial = 1, native = false) {
  let nodes = parseUi(xml.replace(/capture-start-uptime-ms="[0-9]+"/u, `capture-start-uptime-ms="${serial*100}"`).replace(/capture-end-uptime-ms="[0-9]+"/u, `capture-end-uptime-ms="${serial*100+10}"`));
  // Reuse captured geometry with the explicit native adapter window provenance.
  if (native) {
    const windows = new Map(nodes.windowRefs!.map(window => [window, {...window, scopeBasis: "voyemu-guest-accessibility" as const}]));
    nodes = Object.assign(nodes.map(node => ({...node, windowRef: windows.get(node.windowRef!)})), {windowRefs: [...windows.values()]});
  }
  const grid = uniqueNode(nodes, n => n.resource === "com.qinggan.app.launcher:id/all_app_view");
  return {nodes,grid,items:launcherItems(nodes,grid)};
}
const indicatorFor = p => uniqueNode(p.nodes,n=>n.resource === "com.qinggan.app.launcher:id/home_page_indicator" && focusedApplication(n));
function receipt(requestId = "1".repeat(64), serial = 1, firstPosition = 0) {
  const p = page();
  return {schemaVersion:1,requestId,bootId,daemonPid:"20",daemonStarttime:"50",targetPid:"41",targetStarttime:"100",endpointEpoch:"7",generationId,catalogSha256,documentGeneration:4,captureStartedBootMs:serial*1000,captureFinishedBootMs:serial*1000+10,outcome:"observed",reason:"observed",observation:{outerCurrentItem:1,firstPosition,pageCount:2,offsetX:firstPosition===0?0:1775,itemCount:24,scrollState:0,computingLayout:false,window:{displayId:0,activityClass:"com.qinggan.app.launcher.activity.MainActivity",windowType:1,focused:true,attached:true,shown:true,token:"android.os.BinderProxy@1234",bounds:[...p.grid.windowRef!.bounds]},grid:{resource:p.grid.resource,className:"com.qinggan.launcher.base.allapp.AllAppBarView",layoutManagerClass:"com.qinggan.launcher.base.custom.PagerGridLayoutManager",bounds:p.grid.bounds},items:p.items.map((item,i)=>({position:firstPosition+i,label:item.label,bounds:item.node.bounds}))}};
}
function parse(value = receipt()) { return launcherPageReceipt(JSON.stringify(value),value.requestId); }

// This is the actual production callback, including its image/semantic dispatch.
async function dispatch(deferred: boolean, firstPosition = 0, mode = "observed", native = false) {
  const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
  const start = source.indexOf("      const beforeBoot = deferred ? await inspect() : await bootObservation();", source.indexOf("  const launcherBeginning ="));
  const end = source.indexOf("\n    },", start);
  assert(start >= 0 && end > start, "actual launcher callback boundaries must exist");
  const body = source.slice(start, end).replace("(rows: typeof items)", "(rows)").replaceAll("windowRef!.", "windowRef.");
  const initial = page(1,native); let serial=1,queries=0,screenshots=0,fullInspections=0,bootReads=0;
  const authority = Object.create(InstallerUi.prototype); authority.observed=new WeakSet(initial.nodes); authority.nodes=initial.nodes;
  const ui = {call:async request=>{validateRequest(request);assert.equal(request.action,"launcher-page-observe");queries++;authority.observed=new WeakSet();const value=receipt(request.requestId,queries,firstPosition);
    if(mode === "unavailable"){value.outcome="unavailable";value.reason="no-grid";delete value.observation;}
    if(mode === "partial")value.observation.offsetX=1;
    return {body:JSON.stringify(value)};}};
  const read = async()=>{const next=page(++serial,native);authority.observed=new WeakSet(next.nodes);authority.nodes=next.nodes;return next;};
  const observeBoot = async()=>{bootReads++;authority.observed=new WeakSet();return {...boot(),body:`bootCompleted=1\nbootId=${bootId}\nenforcing=Enforcing\noperation:\n{}\n`};};
  const inspect = async()=>{fullInspections++;authority.observed=new WeakSet();return boot();};
  const Fn=Object.getPrototypeOf(async function(){}).constructor;
  const run=new Fn("native","launcherPointerNode","deferred","items","bootObservation","inspect","launcherPage","uniqueNode","focusedApplication","screenshot","launcherCaptureContinuity","launcherAppsBeginningPng","readFileSync","initial","launcherSemanticBoundary","ui","randomBytes","LauncherPageUnavailable","Unavailable",`let launcherCurrentPage=initial;const result=await(async()=>{${body}})();return {result,page:launcherCurrentPage};`);
  const result=await run(native,launcherPointerNode,deferred,initial.items,observeBoot,inspect,read,uniqueNode,focusedApplication,async()=>{screenshots++;return deferred?null:"png";},(await import("../emulator/launcher-capture.ts")).launcherCaptureContinuity,()=>true,()=>Buffer.alloc(0),initial,launcherSemanticBoundary,ui,()=>Buffer.alloc(32,queries+1),LauncherPageUnavailable,class Unavailable extends Error {});
  assert.equal(authority.gestureAuthority(result.page.grid).expectedTarget.resource,initial.grid.resource);
  return {...result,queries,screenshots,fullInspections,bootReads};
}

for (const native of [false, true]) {
const transport = native ? "native" : "legacy";
test(`${transport}: actual noimages launcherBeginning dispatcher queries semantics without reading a null PNG`, async () => {
  const result=await dispatch(true,0,"observed",native);assert.equal(result.result,true);assert.equal(result.queries,2);assert.equal(result.screenshots,0);assert.equal(result.fullInspections,3);assert.equal(result.bootReads,0);
  assert.deepEqual(result.page.items.map(item=>[item.label,item.node.bounds]),page().items.map(item=>[item.label,item.node.bounds]));
});
test(`${transport}: authorized visual dispatcher preserves PNG first-page observation`,async()=>{
  const result=await dispatch(false,0,"observed",native);assert.equal(result.result,true);assert.equal(result.queries,0);assert.equal(result.screenshots,1);assert.equal(result.fullInspections,0);assert.equal(result.bootReads,2);
});
test(`${transport}: same outer item on later app page cannot establish first page`,async()=>{
  const result=await dispatch(true,12,"observed",native);assert.equal(result.result,false);assert.equal(result.queries,2);
  let reads=0,swipes=0;
  await assert.rejects(launcherFirstPage(async()=>{reads++;return page().items;},async()=>{swipes++;},async()=>false),/stalled or cycled/);
  assert.equal(reads,2);assert.equal(swipes,1);
});
}

test("semantic witness remains available with every feature disabled",()=>{
  const first=parse(),last=parse(receipt("2".repeat(64),2));
  assert.doesNotThrow(()=>launcherPageLifetime(first,last,[boot(),boot(),boot()]));
  launcherPageHierarchy(last,page());
});
test("semantic receipt rejects missing direct metrics busy layout wrong primary and missing fields",()=>{
  const mutations=[v=>{delete v.observation.firstPosition},v=>{delete v.observation.offsetX},v=>{delete v.observation.pageCount},v=>{v.observation.scrollState=1},v=>{v.observation.computingLayout=true},v=>{v.observation.window.displayId=1},v=>{v.observation.window.focused=false},v=>{v.observation.window.attached=false},v=>{v.observation.window.shown=false},v=>{v.observation.window.token=""},v=>{v.observation.window.activityClass="foreign"},v=>{v.observation.window.windowType=2},v=>{v.observation.grid.className="androidx.recyclerview.widget.RecyclerView"},v=>{v.observation.grid.layoutManagerClass="androidx.viewpager.widget.ViewPager"},v=>{v.observation.pageCount=0},v=>{v.observation.itemCount=0},v=>{v.observation.pageCount=3},v=>{v.observation.outerCurrentItem=0},v=>{v.observation.items[1].position=0},v=>{v.observation.items[0].position=24},v=>{v.observation.items[0].bounds=[0,0,0,0]},v=>{v.captureFinishedBootMs=v.captureStartedBootMs-1},v=>{v.outcome="unavailable";delete v.observation},v=>{v.action="snapshot"},v=>{v.observation.items.reverse()},v=>{v.observation.items[0].label="bad\nlabel"},v=>{v.generationId="bad\ngeneration"},v=>{v.observation.window.token="bad\ntoken"}];
  for(const mutate of mutations){const value=receipt();mutate(value);assert.throws(()=>parse(value));}
  for(const key of Object.keys(receipt())){const value=receipt();delete value[key];assert.throws(()=>launcherPageReceipt(JSON.stringify(value),"1".repeat(64)));}
  const raw=JSON.stringify(receipt());assert.throws(()=>launcherPageReceipt(raw.replace('"firstPosition":0','"firstPosition":12,"firstPosition":0'),"1".repeat(64)),/duplicate/);
  assert.throws(()=>launcherPageReceipt(raw.replace('"firstPosition":0','"first\\u0050osition":12,"firstPosition":0'),"1".repeat(64)),/duplicate/);
});
test("semantic receipt fences stale boot PID starttime endpoint generation document and native token",()=>{
  for(const key of ["bootId","daemonPid","daemonStarttime","targetPid","targetStarttime","endpointEpoch","generationId","catalogSha256","documentGeneration"]){const first=parse(),value=receipt("2".repeat(64),2);value[key]=key==="documentGeneration"?5:key==="bootId"?"22222222-2222-2222-2222-222222222222":key.endsWith("Id")||key==="catalogSha256"?"d".repeat(64):"99";assert.throws(()=>launcherPageLifetime(first,parse(value),[boot()]),/lifetime/);}
  const first=parse(),value=receipt("2".repeat(64),2);value.observation.window.token="other";assert.throws(()=>launcherPageLifetime(first,parse(value),[boot()]),/native window/);
  for(const change of [b=>{b.boot="22222222-2222-2222-2222-222222222222"},b=>{b.body=b.body.replace("41 com.qinggan","42 com.qinggan")},b=>{b.body=b.body.replace(generationId,"d".repeat(64))},b=>{b.body=b.body.replace(catalogSha256,"d".repeat(64))},b=>{b.bootCompleted=false}]){const current=boot();change(current);assert.throws(()=>launcherPageLifetime(first,parse(receipt("2".repeat(64),2)),[current]),/independent/);}
});
test("semantic hierarchy preserves exact visible labels bounds and window authority",()=>{
  for(const change of [p=>{p.items[0].label="foreign"},p=>{p.items[0].node.bounds=[1,1,2,2]},p=>{p.grid.className="foreign"},p=>{p.grid.visibleToUser=false},p=>{p.grid.windowRef={...p.grid.windowRef,inputFocused:false}},p=>{p.items.pop()}]){const current=page();change(current);assert.throws(()=>launcherPageHierarchy(parse(),current),/hierarchy/);}
});
test("launcher page request admits only a fresh lower-case 64hex nonce",()=>{
  validateRequest({action:"launcher-page-observe",requestId:"a".repeat(64)});
  for(const request of [{action:"launcher-page-observe"},{action:"launcher-page-observe",requestId:"A".repeat(64)},{action:"launcher-page-observe",requestId:"a".repeat(63)},{action:"launcher-page-observe",requestId:"a".repeat(64),page:0}]) assert.throws(()=>validateRequest(request));
});

for (const native of [false, true]) test(`${native ? "native" : "legacy"}: valid unavailable remains unavailable and partial offset cannot establish first page`,async()=>{
  await assert.rejects(dispatch(true,0,"unavailable",native),error=>error.constructor.name==="Unavailable" && /no-grid/.test(error.message));
  const result=await dispatch(true,0,"partial",native);assert.equal(result.result,false);assert.equal(result.screenshots,0);
  const unavailable=receipt();unavailable.outcome="unavailable";unavailable.reason="timeout";delete unavailable.observation;
  assert.throws(()=>parse(unavailable),LauncherPageUnavailable);
  const stale=boot();stale.boot="22222222-2222-2222-2222-222222222222";
  assert.throws(()=>launcherPageReceipt(JSON.stringify(unavailable),unavailable.requestId,stale),error=>!(error instanceof LauncherPageUnavailable));
  unavailable.requestId="bad";assert.throws(()=>launcherPageReceipt(JSON.stringify(unavailable),"1".repeat(64)),error=>!(error instanceof LauncherPageUnavailable));
});

test("actual case12 captures stock after source-admitted disable and rejects unavailable preparation",async()=>{
  const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
  const helperStart=source.indexOf("  const launcherStockDisabled ="),helperEnd=source.indexOf("  const launcherPage =",helperStart);
  const caseStart=source.indexOf("      case 12: {")+"      case 12: {".length,caseEnd=source.indexOf("        const installCacheBaseline =",caseStart);
  assert(helperStart>=0&&helperEnd>helperStart&&caseEnd>caseStart);
  const helper=stripTypeScriptTypes(source.slice(helperStart,helperEnd));
  const prefix=stripTypeScriptTypes(source.slice(caseStart,caseEnd));
  const settings="com.qinggan.app.systemsettings",title="Launcher apps";
  const settingsNodes=(checked:boolean)=>{
    const original=page().grid,w={...original.windowRef,rootPackage:settings};
    const root={...original,packageName:settings,windowRef:w,parent:null,checkable:false};
    const label={...root,parent:0,text:title,resource:"",bounds:[0,0,100,100]};
    const checkbox={...root,parent:0,text:"",checkable:true,checked,bounds:[100,0,200,100]};
    return [root,label,checkbox];
  };
  const Fn=Object.getPrototypeOf(async function(){}).constructor;
  for(const mode of ["disabled","enabled","missing","runtime-selected","failed-stop"]){
    let checked=mode==="enabled"||mode==="failed-stop";
    const events:string[]=[],ui={records:[{result:{actionId:1}}],call:async request=>{events.push(request.action);return {body:"fixture-absent\n"};}};
    const evidence={fixtureCleanup:{}},unavailable=message=>{throw new (class Unavailable extends Error {})(message);};
    const current=boot();if(mode==="runtime-selected")current.body=current.body.replace('"injections":[]','"injections":[{"id":"launcher-apps","process":"com.qinggan.app.launcher","state":"active","reason":"","details":{}}]');
    const run=new Fn("catalog","language","ui","top","seekAgentControl","agentControl","SETTINGS","unavailable","c","evidence","launcherGrid","observations","inspectionResident","toggle","disablePolicy","proof","checkDisable","toggleObservation","checkboxTargets",`${helper}let fixtureOemItems,admittedStopModes;${prefix}return fixtureOemItems;`);
    const result=run([{id:"launcher-apps",title:{en:title}}],"en",ui,async()=>{events.push("top");},async()=>{if(mode==="missing") unavailable("checkbox missing");return settingsNodes(checked);},agentControl,settings,unavailable,{fixture:"approved"},evidence,async first=>{assert.equal(first,true);events.push("stock-capture");return [{label:"Music"}];},async()=>{events.push("resident");return current;},(await import("../emulator/functional-observations.ts")).inspectionResident,async(id,want)=>{assert.equal(id,"launcher-apps");checked=want;events.push(want?"enable":"disable");},
      async()=>({modes:{"launcher-apps":"restart"},file:"source-policy"}),()=>{},
      async(id,stopMode,action,prefix,required)=>{assert.deepEqual([id,stopMode,prefix,required],["launcher-apps","restart","stock-disable",true]);if(mode==="failed-stop")unavailable("required restart unavailable");await action();events.push("restart-confirmed");},
      async()=>({injections:mode==="runtime-selected"?[{id:"launcher-apps"}]:[],file:"absence"}),()=>["com.qinggan.app.launcher"]);
    if(mode==="disabled"||mode==="enabled"){assert.deepEqual(await result,["Music"]);assert(events.indexOf("stock-capture")<events.indexOf("enable"));assert(events.indexOf("resident")<events.indexOf("enable"));if(mode==="enabled")assert(events.indexOf("restart-confirmed")<events.indexOf("stock-capture"));}
    else {await assert.rejects(result,error=>error.constructor.name==="Unavailable");assert(!events.includes("enable"));assert(!events.includes("stock-capture"));}
  }
});

test("native u64 document generations are lossless and remain lifetime-fenced", () => {
  const wire = (generation: string, nonce = "1".repeat(64), serial = 1) => JSON.stringify(receipt(nonce, serial)).replace(/"documentGeneration":4/u, `"documentGeneration":${generation}`);
  for (const generation of ["4", "9007199254740992", "1792112229328575429", "18446744073709551615"]) {
    const first = launcherPageReceipt(wire(generation), "1".repeat(64));
    assert.equal(first.documentGeneration, generation);
    const last = launcherPageReceipt(wire(generation, "2".repeat(64), 2), "2".repeat(64));
    assert.doesNotThrow(() => launcherPageLifetime(first, last, [boot()]));
  }
  for (const invalid of ["0", "-1", "1.5", "1e3", "01", "18446744073709551616", "null", "true", "1 2", JSON.stringify("1792112229328575429")]) assert.throws(() => launcherPageReceipt(wire(invalid), "1".repeat(64)));
  const first = launcherPageReceipt(wire("1792112229328575429"), "1".repeat(64));
  const changed = launcherPageReceipt(wire("1792112229328575430", "2".repeat(64), 2), "2".repeat(64));
  assert.throws(() => launcherPageLifetime(first, changed, [boot()]), /lifetime/);
  const raw = wire("1792112229328575429");
  assert.throws(() => launcherPageReceipt(raw.replace(/"documentGeneration":/u, '"documentGeneration":4,"documentGeneration":'), "1".repeat(64)), /duplicate/);
  assert.throws(() => launcherPageReceipt(raw.replace(/"documentGeneration":/u, '"document\\u0047eneration":4,"documentGeneration":'), "1".repeat(64)), /duplicate/);
});

test("lightweight boot receipt cannot masquerade as independent resident authority", () => {
  const current = boot(); current.body = `bootCompleted=1\nbootId=${bootId}\nenforcing=Enforcing\noperation:\n{}\n`;
  assert.throws(() => launcherPageReceipt(JSON.stringify(receipt()), "1".repeat(64), current), /resident status section/);
});
