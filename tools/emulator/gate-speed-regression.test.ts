import {driverInvocation} from "./driver.ts";
import test from "node:test";
import {stripTypeScriptTypes} from "node:module";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
const body=source.slice(source.indexOf("  const probe = async"),source.indexOf("  const uiAdapter: ProcessAdapter"));
function fixture({boot="boot-a",receiptSerial="emulator-5562",rejectOwnership=false,pending=false}={}) {
 let ownershipCalls=0,records=0;
 const scope={driverInvocation,ownership:async()=>{ownershipCalls++;if(rejectOwnership)throw Error("runtime lease identity changed");},join:(...v)=>v.join("/"),c:{output:"/evidence",package:"/package/voboost-installer"},evidence:{actions:[]},writeFileSync:()=>{},serial:"emulator-5562",record:async()=>{records++;return{stdout:JSON.stringify({schemaVersion:1,serial:receiptSerial,body:boot})};},installerRootPending:()=>pending,installerActive:false,ObservationPending:Error,offlineObservation:()=>false,resultOk:()=>{},jsonReceipt:JSON.parse,exactKeys:(o,k)=>assert.deepEqual(Object.keys(o),k),parseBootObservation:s=>({boot:s})};
 const probe=Function(...Object.keys(scope),body+"return probe;")(...Object.values(scope));
 return{probe,own:scope.ownership,counts:()=>({ownershipCalls,records})};
}
test("standalone probe retains fresh runtime ownership",async()=>{const f=fixture();assert.equal(await f.probe(),"boot-a");assert.deepEqual(f.counts(),{ownershipCalls:1,records:1});});
test("same-action ownership avoids only the duplicated status",async()=>{const f=fixture();await f.own();assert.equal(await f.probe(true),"boot-a");assert.deepEqual(f.counts(),{ownershipCalls:1,records:1});});
test("runtime mismatch rejects before any device probe",async()=>{const f=fixture({rejectOwnership:true});await assert.rejects(f.probe(),/lease identity/);assert.equal(f.counts().records,0);});
test("receipt identity guard remains enforced",async()=>{const f=fixture({receiptSerial:"foreign"});await assert.rejects(f.probe(true),/probe identity/);});
test("pending root retains new ownership validation before rejection",async()=>{const f=fixture({pending:true});await f.own();await assert.rejects(f.probe(true),/not ready/);assert.deepEqual(f.counts(),{ownershipCalls:2,records:1});});
test("same-action adapter executes one owned boot probe and rejects active mutation",async()=>{
 const start=source.indexOf("  const uiAdapter: ProcessAdapter"), end=source.indexOf("  let ui: InstallerUi;",start);
 const adapterSource=source.slice(start,end);const beforeProbe=adapterSource.slice(0,adapterSource.indexOf("currentBoot = await probe(true);"));assert.match(beforeProbe,/if \(initialSettingsNavigation\)/);assert.match(beforeProbe,/else if/);
 let calls=0,records=0;const evidence={activeOperation:null,actions:[]};
 const scope={readFileSync:()=>JSON.stringify({action:"tap"}),evidence,probe:async owned=>{assert.equal(owned,true);calls++;return "boot-a";},performance:{now:()=>0},record:async()=>{records++;evidence.actions.push({id:records,artifacts:[]});return{exitCode:0,actionId:records};},digest:()=>"hash",frozen:{package:"kit"},runtime:{pid:1,port:2},serial:"serial",language:"ru",native:null,nativeBootReadDeadline:null,installerClosedPending:()=>false,installerRootPending:()=>false,offlineObservation:()=>false,snapshotReadback:()=>{throw Error("mutation must never enter snapshot retry");}};
 const run=Function(...Object.keys(scope),'let initialSettingsNavigation=false,currentBoot=null,snapshotBoot="boot-a",firstBootCaptureDeadline=null,snapshotReadbackDeadline=null,installerActive=false,installerRebootCapture=false,installerMonitorStopped=false,faultLive=null;'+stripTypeScriptTypes(adapterSource)+'return uiAdapter;')(...Object.values(scope));
 const action={args:["--emulator-check","request"],timeoutMs:240000};
 assert.equal((await run(action)).exitCode,0);assert.equal(calls,1);assert.equal(records,1);
 evidence.activeOperation={};await assert.rejects(run(action),/active transaction lease retained/);assert.equal(calls,1);assert.equal(records,1);
 const ui=readFileSync(new URL("./installer-ui.ts",import.meta.url),"utf8");const segment=ui.slice(ui.indexOf("    await this.ownership();"),ui.indexOf("    const result = await this.adapter(invocation);"));assert.equal((segment.match(/await /g)||[]).length,1);assert.match(segment,/stale UI observation before action/);
});

const dialog=source.slice(source.indexOf("  const uninstallDialog = async"),source.indexOf("  const switchKeyboard = async"));
const loop=source.slice(source.indexOf(`        await subcheck("language-loop-copy-dialog-cancel"`),source.indexOf(`        await subcheck("language-process-resource-locale"`));
function loopFixture({missingCaption=false,statusVersion="1.0.0-beta1"}={}) {
 const events=[],endpoints=[];let language=`ru`;
 const captions={buttonText:"remove",description:"description",confirmLabel:"confirm",cancelLabel:"cancel"};
 const scope={evidence:{actions:[]},copy:{languages:Object.fromEntries(["en","ru","zh"].map(l=>[l,{uninstall:captions}]))},unavailable:m=>{throw Error(m)},SETTINGS:"setting",focusedApplication:()=>true,panel:async full=>events.push(["panel",language,full]),tapText:async text=>events.push(["tap",language,text]),ui:{snapshot:async()=>["description","confirm",...(missingCaption?[]:["cancel"])].map(text=>({packageName:"setting",text}))},screenshot:async name=>events.push(["screenshot",name]),cli:async()=>({stdout:JSON.stringify({installedVersion:statusVersion})}),resultOk:()=>{},jsonReceipt:JSON.parse,manifest:{releaseIdentity:"1.0.0-beta1"},observations:async()=>({language}),setLanguage:async l=>{language=l;events.push(["set",l])},subcheck:async(id,f)=>f(),localeEndpoints:endpoints};
 const names=Object.keys(scope),values=Object.values(scope);const i=names.indexOf("setLanguage");names[i]="originalSet";
 return{run:Function(...names,"return async function(){let language=`ru`;"+stripTypeScriptTypes(dialog)+"const setLanguage=async l=>{language=l;await originalSet(l)};"+stripTypeScriptTypes(loop)+"}")(...values),events,endpoints};
}
test("all five switches retain full pre-dialog and post-cancel panels",async()=>{const f=loopFixture();await f.run();assert.deepEqual(f.events.filter(x=>x[0]==="set").map(x=>x[1]),["en","zh","ru","zh","en"]);assert.equal(f.events.filter(x=>x[0]==="panel").length,10);assert.ok(f.events.filter(x=>x[0]==="panel").every(x=>x[2]===true));assert.equal(f.events.filter(x=>x[0]==="tap"&&x[2]==="cancel").length,5);assert.equal(f.endpoints.length,5);});
test("missing dialog caption still fails before endpoint and cancellation",async()=>{const f=loopFixture({missingCaption:true});await assert.rejects(f.run(),/caption missing/);assert.equal(f.endpoints.length,0);assert.equal(f.events.filter(x=>x[0]==="tap"&&x[2]==="cancel").length,0);});
test("changed installed version after cancel still fails",async()=>{const f=loopFixture({statusVersion:"foreign"});await assert.rejects(f.run(),/changed installed version/);assert.equal(f.endpoints.length,0);});
