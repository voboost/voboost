import {tapDrawerSwitch,prepareDrawerHome} from "../../release/emulator-gate.ts";
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {stripTypeScriptTypes} from "node:module";
import vm from "node:vm";
import {parseUi,focusedApplication,uniqueNode,vehicleLaunchPending} from "../emulator/installer-ui.ts";
import {oemFeature} from "../emulator/functional-observations.ts";
const root=process.env.VEHICLE_FOCUS_SOURCE||resolve(import.meta.dirname,"../..");
const gate=readFileSync(resolve(root,"release/emulator-gate.ts"),"utf8");
const xml=readFileSync(resolve(import.meta.dirname,"../../testdata/emulator/vehicle-focus155/actual531.xml"),"utf8");
const owner="com.qinggan.app.vehiclesetting", SETTINGS="com.qinggan.app.setting";
function focusedXml(value=xml) {return value.replace('id="75" type="1" layer="0" active="false" input-focused="false"','id="75" type="1" layer="0" active="true" input-focused="true"');}
function navigation(snapshots, clock = () => 0) {
 const events=[];let captures=0;
 const ui={call:async request=>events.push(request.action),snapshot:async()=>{events.push('snapshot');assert.ok(captures<snapshots.length,'unexpected extra snapshot');return snapshots[captures++];},tap:async()=>events.push('tap')};
 const begin=gate.indexOf('  const openVehicleMenu = async');const end=gate.indexOf('  const openVehicle =',begin);
 const open=vm.runInNewContext(stripTypeScriptTypes(gate.slice(begin,end))+';openVehicleMenu',{settingsNavigationDeadline:null,vehicleLaunchPending,ui,SETTINGS,focusedApplication,uniqueNode,oemFeature,vehicleNodes:[],ownership:async()=>{},performance:{now:clock},unavailable:message=>{throw Error(message)}});
 return {open,events};
}
test('actual action531 has visible selected FEV menu but no focused default-display window',()=>{
 const nodes=parseUi(xml);assert.equal(nodes.windowRefs?.some(w=>w.inputFocused),false);
 const menu=nodes.find(n=>n.resource===owner+':id/rb_menu_drive_preference');assert.equal(menu?.checked,true);assert.equal(menu?.visibleToUser,true);assert.equal(focusedApplication(menu!),false);
 assert.throws(()=>uniqueNode(nodes,n=>n.resource===owner+':id/menu_container'));
});
test('actual lost-focus capture performs one OEM activity roundtrip with fresh focus readback',async()=>{
 // Secondary display owns focus outside this observer scope. No invented secondary window is admitted.
 // Both transition captures are synthetic derived fixtures with independent parse/capture authority.
 const settings=parseUi(focusedXml().replaceAll(owner,SETTINGS));const vehicle=parseUi(focusedXml());
 const n=navigation([parseUi(xml),settings,vehicle]);const control=await n.open('fev');
 assert.equal(control.checked,false);assert.ok(focusedApplication(control));assert.deepEqual(n.events,['open-vehicle-settings','snapshot','open-settings','snapshot','open-vehicle-settings','snapshot']);
});
test('focus acquisition must be confirmed before opening vehicle again',async()=>{
 for(const scene of [xml.replaceAll(owner,SETTINGS),focusedXml()]) {
 const n=navigation([parseUi(xml),parseUi(scene)]);await assert.rejects(()=>n.open('fev'));
 assert.deepEqual(n.events,['open-vehicle-settings','snapshot','open-settings','snapshot']);
 }
});
test('focused vehicle fast path does not add activity launches or tap selected tab',async()=>{
 const n=navigation([parseUi(focusedXml())]);await n.open('fev');assert.deepEqual(n.events,['open-vehicle-settings','snapshot']);
});
test('foreign focused default-display window is never displaced by lost-focus recovery',async()=>{
 const foreign=xml.replace('id="87" type="-1" layer="3" active="false" input-focused="false"','id="87" type="-1" layer="3" active="true" input-focused="true"');
 const n=navigation([parseUi(foreign)]);await assert.rejects(()=>n.open('fev'));assert.deepEqual(n.events,['open-vehicle-settings','snapshot']);
});
test('one recovery attempt does not authorize an unfocused final vehicle capture',async()=>{
 const n=navigation([parseUi(xml),parseUi(focusedXml().replaceAll(owner,SETTINGS)),parseUi(xml)]);await assert.rejects(()=>n.open('fev'));
 assert.deepEqual(n.events,['open-vehicle-settings','snapshot','open-settings','snapshot','open-vehicle-settings','snapshot']);
});

test('two focused windows cannot certify intermediate Settings focus',async()=>{
 const conflict=focusedXml().replaceAll(owner,SETTINGS).replace('id="87" type="-1" layer="3" active="false" input-focused="false"','id="87" type="-1" layer="3" active="true" input-focused="true"');
 const n=navigation([parseUi(xml),parseUi(conflict)]);await assert.rejects(()=>n.open('fev'));assert.deepEqual(n.events,['open-vehicle-settings','snapshot','open-settings','snapshot']);
});

test('unknown unfocused application, dialog, unavailable root and launcher overlay never authorize recovery',async()=>{
 const end=xml.indexOf('</display>');
 const foreignWindow=(type, status='available',pkg='foreign.app')=>`<window id="999" type="${type}" layer="4" active="false" input-focused="false" accessibility-focused="false" bounds="[0,0][1920,720]" title="" root-status="${status}" root-package="${status==='available'?pkg:''}">${status==='available'?'<node index="0" window-id="999" text="" resource-id="" class="android.widget.FrameLayout" package="'+pkg+'" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" visible-to-user="true" bounds="[0,0][1920,720]"></node>':''}</window>`;
 for(const extra of [foreignWindow(1),foreignWindow(-1),foreignWindow(1,'unavailable'),foreignWindow(-1,'available','com.qinggan.app.launcher')]) {
   const n=navigation([parseUi(xml.slice(0,end)+extra+xml.slice(end))]);await assert.rejects(()=>n.open('fev'));assert.deepEqual(n.events,['open-vehicle-settings','snapshot']);
 }
});
test('replacement unknown launcher decoration cannot impersonate the admitted dock',async()=>{
 const n=navigation([parseUi(xml.replace('screen_up_navigation','unknown_dialog'))]);await assert.rejects(()=>n.open('fev'));assert.deepEqual(n.events,['open-vehicle-settings','snapshot']);
});

test('four-window replacement foreign roots and unavailable application roots cannot acquire focus',async()=>{
 const decoration=/<window id="87"[\s\S]*?<\/window>/u;
 const original=xml.match(decoration)![0];
 const scenes=[
   xml.replace(decoration,original.replace('type="-1"','type="1"').replaceAll('com.qinggan.app.launcher','foreign.app')),
   xml.replace(decoration,original.replaceAll('com.qinggan.app.launcher','foreign.dialog')),
   xml.replace(decoration,original.slice(0,original.indexOf('><node')+1).replace('root-status="available"','root-status="unavailable"').replace('root-package="com.qinggan.app.launcher"','root-package=""')+'</window>'),
   xml.replace(/<window id="75"[\s\S]*?<\/window>/u,xml.match(/<window id="75"[^>]*>/u)![0].replace('root-status="available"','root-status="unavailable"').replace('root-package="'+owner+'"','root-package=""')+'</window>'),
 ];
 for(const scene of scenes) {const parsed=parseUi(scene);assert.equal(parsed.windowRefs!.length,4);const n=navigation([parsed]);await assert.rejects(()=>n.open('fev'));assert.deepEqual(n.events,['open-vehicle-settings','snapshot']);}
});

test("focused OEM window with empty viewport is reread before one menu tap", async () => {
 const ready = parseUi(focusedXml());
 const menu = ready.find(n => n.resource === owner + ":id/rb_menu_drive_preference");
 menu.checked = false; menu.selected = false;
 const empty = ready.map(n => ({...n}));
 const old = menu.windowRef;
 const window = {...old, bounds:[0,0,2640,0]};
 for (const n of empty) if (n.windowRef === old) {n.windowRef = window; n.bounds = [n.bounds[0], n.bounds[1], n.bounds[0], n.bounds[1]];}
 Object.defineProperty(empty, "windowRefs", {value:ready.windowRefs.map(w => w === old ? window : w)});
 const n = navigation([empty,ready]); await n.open("fev");
 assert.deepEqual(n.events,["open-vehicle-settings","snapshot","snapshot","tap"]);
});
test("persistent empty OEM viewport expires without gestures or relaunch", async () => {
 const nodes = parseUi(focusedXml());
 const old = nodes.find(n => n.resource === owner + ":id/rb_menu_drive_preference").windowRef;
 const window = {...old,bounds:[0,0,2640,0]};
 for (const node of nodes) if (node.windowRef === old) node.windowRef=window;
 const empty = Array.from(nodes); Object.defineProperty(empty,"windowRefs",{value:nodes.windowRefs.map(w=>w===old?window:w)});
 let time=0; const n=navigation([empty],()=>time+=5000);
 await assert.rejects(()=>n.open("fev"),/viewport readiness deadline expired/);
 assert.deepEqual(n.events,["open-vehicle-settings","snapshot"]);
});

function warmCycle(observations, powers = [{wakefulness:"Asleep",display_state:"OFF"},{wakefulness:"Awake",display_state:"ON"}]) {
 const begin=gate.indexOf("  const powerCycle = async");const end=gate.indexOf("  const recordVehicleTiming",begin);
 let clock=0;const keys=[];
 const native={can:async()=>{assert.ok(observations.length,"unexpected CAN read");return observations.shift();},system:async()=>({boot_id:"boot",value:{boot_id:"boot",power:{status:"ready",complete:true,display_ready:true,...powers.shift()}}}),call:async(op,r)=>{assert.equal(op,"input.key.press");keys.push(r.key);}};
 const cycle=vm.runInNewContext(stripTypeScriptTypes(gate.slice(begin,end))+";powerCycle",{
 native,bootObservation:async()=>({boot:"boot"}),ui:{call:async r=>keys.push(r.key)},performance:{now:()=>clock++},
 unavailable:m=>{throw Error(m)},setTimeout:f=>{clock+=10000;f();},Set,JSON});
 return {cycle,keys};
}
function canAcc(raw, events=[], extra={}) {
 return {status:"ready",boot_id:"boot",source_instance:"can",acc:{status:"observed",raw},
 history:{next_cursor:{source_instance:"can",after_seq:"3"},gap:null,has_more:false},
 coverage:{vehicle_state_setter:true},events,...extra};
}
const resetEvents=["IVI_SOC_MODESET","HUM_VSP_FUNCTION_SW"].map(s=>({
 source_instance:"can",kind:"state_applied",method:"acc_on_reset",field:"vehicle_state."+s,raw:2
}));
test("warm cycle proves ACC OFF and ON plus profile reset without fixed sleep",async()=>{
 const h=warmCycle([canAcc(2),canAcc(0),canAcc(2,resetEvents)]);await h.cycle();assert.deepEqual(h.keys,["sleep","Power"]);
});
test("SDK sleep completion without ACC OFF is unavailable and never replays key",async()=>{
 const h=warmCycle([canAcc(2),canAcc(2)]);await assert.rejects(h.cycle,/ACC 0 edge not observed/);assert.deepEqual(h.keys,["sleep"]);
});
test("warm cycle rejects source replacement, boot change and journal gaps",async()=>{
 for(const extra of [{source_instance:"new"},{boot_id:"new"},{history:{gap:"overflow"}}]){
  const h=warmCycle([canAcc(2),canAcc(0,[],extra)]);await assert.rejects(h.cycle,/warm power transport/);assert.deepEqual(h.keys,["sleep"]);
 }
});
test("ACC ON alone cannot certify profile reset when journal coverage exists",async()=>{
 const h=warmCycle([canAcc(2),canAcc(0),canAcc(2)]);await assert.rejects(h.cycle,/lacks profile reset history/);assert.deepEqual(h.keys,["sleep","Power"]);
});

test("missing or ambiguous Android sleep proof never dispatches toggle Power",async()=>{
 for(const power of [{}, {wakefulness:"Awake",display_state:"ON"}, {wakefulness:"Asleep",display_state:"OFF",complete:false}]){
  const h=warmCycle([canAcc(2),canAcc(0)],[power]);await assert.rejects(h.cycle,/power is not confirmed/);assert.deepEqual(h.keys,["sleep"]);
 }
});
test("Power completion alone does not certify Android Awake ON",async()=>{
 const h=warmCycle([canAcc(2),canAcc(0),canAcc(2,resetEvents)],[{wakefulness:"Asleep",display_state:"OFF"},{}]);
 await assert.rejects(h.cycle,/power is not confirmed Awake\/ON/);assert.deepEqual(h.keys,["sleep","Power"]);
});

import { drawerOffJournal } from "../../release/emulator-gate.ts";
import { FULL_REQUIRED } from "../emulator/full-semantic.ts";
const batch=(events:any[]=[])=>({status:"ready",boot_id:"boot",source_instance:"source",coverage:{vehicle_state_setter:true},clock:{sampled_ns:"1000000000"},events,history:{gap:null,has_more:false,next_cursor:{source_instance:"source",after_seq:String(events.length)}}});
const event=(seq:number,raw:number)=>({seq:String(seq),source_instance:"source",elapsed_ns:"100000000",kind:"state_applied",field:"vehicle_state.IVI_SOC_MODESET",raw});
test("continuous OFF accepts covered lossless journal",()=>assert.equal(drawerOffJournal([batch([event(1,2)])],"boot","source","0",750).coveredMs,900));
test("transient re-enable cannot disappear in final OFF sample",()=>assert.throws(()=>drawerOffJournal([batch([event(1,2),event(2,5),event(3,2)])],"boot","source","0",750),/re-enabled/));
for(const [name,change] of [
 ["source",(b:any)=>b.source_instance="new"],
 ["boot",(b:any)=>b.boot_id="new"],
 ["coverage",(b:any)=>b.coverage.vehicle_state_setter=false],
 ["gap",(b:any)=>b.history.gap="overflow"],
 ["truncation",(b:any)=>b.history.has_more=true],
 ["short",(b:any)=>b.clock.sampled_ns="200000000"],
 ["cursor",(b:any)=>b.history.next_cursor.after_seq="2"],
] as const) test("OFF proof rejects "+name,()=>{const b=batch([event(1,2)]);change(b);assert.throws(()=>drawerOffJournal([b],"boot","source","0",750));});
test("case13 retains five old checks and requires seven drawer checks",()=>{assert.equal(FULL_REQUIRED[13].length,12);assert.equal(FULL_REQUIRED[14].length,5);for(const id of ["arm","hybrid","save","electric-restore","explicit-off-hold","off-circle","cold-off"]) assert.ok(FULL_REQUIRED[13].includes("fev-drawer-"+id));});

import { regressionSummary, regressionOptions } from "../../release/emulator-gate.ts";
function drawerSubset() {
 const names=["arm","hybrid","save","electric-restore","explicit-off-hold","off-circle","cold-off"];
 return {imageCapture:"deferred",regressionScenario:"vehicle",vehiclePart:"fev-drawer",
 cases:[{number:13,status:"unavailable",subchecks:[...names.map(id=>({id:"fev-drawer-"+id,status:"passed",artifacts:[]})),{id:"vehicle-image-pending",status:"unavailable",artifacts:[]}]}],
 boots:[{status:"unavailable",semanticStatus:"passed",screenshot:null}]};
}
test("drawer-only summary requires exactly seven checks and does not accept excluded baseline",()=>{
 const e=drawerSubset(), summary=regressionSummary(e,"vehicle");
 assert.equal(summary.semanticPassed,true);assert.equal(summary.fullAcceptance,false);
 assert.equal(summary.requiredCheckIds.length,7);assert.equal(summary.excludedChecks.length,5);assert.equal(summary.excludedChecksAccepted,false);
 for(const mutation of [
  e=>e.cases[0].subchecks.splice(0,1),
  e=>e.cases[0].subchecks.push({...e.cases[0].subchecks[0]}),
  e=>e.cases[0].subchecks.push({id:"fev-cold-baseline",status:"passed",artifacts:[]}),
 ]) {const bad=drawerSubset();mutation(bad);assert.equal(regressionSummary(bad,"vehicle").semanticPassed,false);}
 const wrong=drawerSubset();wrong.cases.push({number:14,status:"unavailable",subchecks:[]});
 assert.throws(()=>regressionSummary(wrong,"vehicle"),/another case/);
});
test("drawer-only option is restricted to deferred vehicle mode",()=>{
 assert.doesNotThrow(()=>regressionOptions("vehicle",{imageCapture:"deferred",vehiclePart:"fev-drawer"}));
 for(const [scenario,options] of [["vehicle",{vehiclePart:"fev-drawer"}],["launcher",{imageCapture:"deferred",vehiclePart:"fev-drawer"}],["vehicle",{imageCapture:"deferred",vehiclePart:"other"}]])
  assert.throws(()=>regressionOptions(scenario,options));
});

import {drawerCurrentAuthority} from "../../release/emulator-gate.ts";
const drawerBoot="4a985b4c-d935-4728-90a0-0f26adaab448",drawerHash="a".repeat(64);
function currentDrawerSample() {
 const endpoint={targetPid:"3399",targetStarttime:"1184",endpointEpoch:"4",generationId:drawerHash,catalogSha256:drawerHash,
 desiredDocumentGeneration:"1792039265524493130",acceptedStatus:{documentGeneration:"1792039265524493130",receipt:{sequence:"22",daemonPid:"1682",daemonStarttime:"164",bootId:drawerBoot}}};
 const status={schemaVersion:1,releaseIdentity:"1.0.0-beta1",manifestSha256:drawerHash,generationId:drawerHash,catalogSha256:drawerHash,state:"ready",killed:false,panic:false,
 publisher:{bootId:drawerBoot,daemonPid:"1682",daemonStarttime:"164",publishedAtBootMs:"778720"},
 injections:[{id:"vehicle-drive-forced-ev",process:"com.qinggan.systemservice",state:"active",documentGeneration:"1792039265524493130",currentEndpoint:endpoint}]};
 const process="current-process-identities:\nschemaVersion=1\nbootId="+drawerBoot+"\nenforcing=Enforcing\nprocess=voboost-daemon pid=1682 starttime=164 argv0=/data/voboost/voboost-daemon\nprocess=com.qinggan.systemservice pid=3399 starttime=1184 argv0=com.qinggan.systemservice\nbootIdAfter="+drawerBoot+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n";
 return {status,process,boot:drawerBoot};
}
function serializeDrawer(sample:any) {return {boot:sample.boot,body:"\nresident-status:\n/data/voboost/status.json\n"+JSON.stringify(sample.status)+"\n"+sample.process};}
const drawerCatalog=[{id:"vehicle-drive-forced-ev",targets:[{process:"com.qinggan.systemservice"}]}];
test("drawer current accepted authority survives missing historical queue records",()=>{
 const sample=serializeDrawer(currentDrawerSample());
 const authority=drawerCurrentAuthority(sample,sample,drawerCatalog,drawerHash,drawerHash,"1.0.0-beta1");
 assert.equal(authority.targetPid,"3399");assert.equal(authority.targetStarttime,"1184");assert.equal(authority.endpointEpoch,"4");
 assert.equal(authority.documentGeneration,"1792039265524493130");
});
test("drawer current authority rejects stale PID starttime epoch revision boot and publisher",()=>{
 const before=serializeDrawer(currentDrawerSample());
 for(const change of [
  (s:any)=>s.status.injections[0].currentEndpoint.targetPid="3400",
  (s:any)=>s.status.injections[0].currentEndpoint.targetStarttime="1185",
  (s:any)=>s.status.injections[0].currentEndpoint.endpointEpoch="5",
  (s:any)=>{s.status.injections[0].documentGeneration="1792039265524493131";s.status.injections[0].currentEndpoint.desiredDocumentGeneration="1792039265524493131";s.status.injections[0].currentEndpoint.acceptedStatus.documentGeneration="1792039265524493131";},
  (s:any)=>s.boot="5a985b4c-d935-4728-90a0-0f26adaab448",
  (s:any)=>s.status.publisher.daemonStarttime="165",
  (s:any)=>delete s.status.injections[0].currentEndpoint.acceptedStatus,
  (s:any)=>s.process=s.process.replace("enforcingAfter=Enforcing","enforcingAfter=Permissive"),
 ]) {const sample=currentDrawerSample();change(sample);assert.throws(()=>drawerCurrentAuthority(before,serializeDrawer(sample),drawerCatalog,drawerHash,drawerHash,"1.0.0-beta1"));}
});

test("dependent drawer chain stops before the next mutation when a prerequisite is unavailable",async()=>{
 const start=gate.indexOf("    const drawerCheck = async"),end=gate.indexOf("    const drawerState =",start);
 let mutations=0;
 const evidence={cases:[{number:13,subchecks:[]}]};
 const drawerCheck=vm.runInNewContext(stripTypeScriptTypes(gate.slice(start,end))+";drawerCheck",{
 evidence,subcheck:async(id,action)=>{await action();evidence.cases[0].subchecks.push({id,status:"unavailable",reason:"missing current endpoint"});},
 unavailable:message=>{throw Error(message);}});
 await assert.rejects(async()=>{await drawerCheck("fev-drawer-arm",async()=>{mutations++;});await drawerCheck("fev-drawer-hybrid",async()=>{mutations++;});},/dependent drawer chain stopped/);
 assert.equal(mutations,1);assert.equal(evidence.cases[0].subchecks.length,1);
});
function settlementNodes(bounds=[30,0,50,20],id=42):any {
 const owner="com.qinggan.app.launcher",window:any={id,type:3,active:true,inputFocused:true,rootStatus:"available",rootPackage:owner,scope:"default-display",bounds:[0,0,1224,720],title:"",captureEnd:120201};
 const base:any={...drawerOnNodes(false)[0],packageName:owner,visibleToUser:true,enabled:true,windowRef:window,bounds};
 const nodes:any=[{...base,resource:owner+":id/force_ev_switcher",selected:false},{...base,resource:owner+":id/tv_title",text:"Electric"}];
 Object.defineProperty(nodes,"windowRefs",{value:[window]});return nodes;
}
function extractedSettlement(context:any) {
 const start=gate.indexOf("    const stableDrawer = async"),end=gate.indexOf("    const tapSwitch =",start);
 return vm.runInNewContext(stripTypeScriptTypes(gate.slice(start,end))+";stableDrawer",context);
}
test("drawer reads fresh stable target geometry before mutation without replay",async()=>{
 let reads=0,clock=0;const epoch={boot_id:drawerBoot,source_instance:"epoch",observer_pid:123};
 const snapshot=async()=>{clock=++reads*150;return settlementNodes(reads===1?[0,0,20,20]:[30,0,50,20]);};
 const stable=extractedSettlement({owner:"com.qinggan.app.launcher",performance:{now:()=>clock},probe:async()=>drawerBoot,nativeInputTree:epoch,ui:{snapshot},openDrawer:snapshot,unavailable:message=>{throw Error(message);}});
 const nodes=await stable();assert.equal(reads,4);assert.equal(nodes[0].bounds[0],30);
});
test("drawer reopening beyond former five second budget settles same window without repeated open",async()=>{
 let clock=0,reads=0,opens=0,probes=0;
 const sample=async()=>{reads++;clock=[0,4500,6100,7593][reads-1];assert.notEqual(clock,undefined);return reads===2?[]:settlementNodes(undefined,reads===1?36:38);};
 const stable=extractedSettlement({owner:"com.qinggan.app.launcher",performance:{now:()=>clock},probe:async()=>{probes++;return drawerBoot;},nativeInputTree:{boot_id:drawerBoot,source_instance:"epoch",observer_pid:123},ui:{snapshot:sample},openDrawer:async deadline=>{assert.equal(deadline,10000);opens++;return sample();},unavailable:message=>{throw Error(message);}});
 const nodes=await stable();assert.equal(nodes[0].windowRef.id,38);assert.equal(reads,4);assert.equal(opens,2);assert.equal(probes,1);
});
test("drawer settlement rejects changed boot observer PID or focused window before mutation",async()=>{
 for(const kind of ["boot","source","pid","foreign-window","unfocused","unavailable","wrong-type","missing-source","missing-pid"]) {
  let reads=0;const epoch:any={boot_id:drawerBoot,source_instance:"epoch",observer_pid:123};let clock=0;
  if(kind==="missing-source")epoch.source_instance="";if(kind==="missing-pid")epoch.observer_pid=0;
  const sample=async()=>{clock=++reads*400;const nodes=settlementNodes();
   if(reads===2){if(kind==="boot")epoch.boot_id="foreign";if(kind==="source")epoch.source_instance="foreign";if(kind==="pid")epoch.observer_pid=124;
    const w=nodes[0].windowRef;if(kind==="foreign-window")w.rootPackage="foreign";if(kind==="unfocused")w.inputFocused=false;if(kind==="unavailable")w.rootStatus="unavailable";if(kind==="wrong-type")w.type=1;}
   return nodes;};
  const stable=extractedSettlement({owner:"com.qinggan.app.launcher",performance:{now:()=>clock},probe:async()=>drawerBoot,nativeInputTree:epoch,ui:{snapshot:sample},openDrawer:sample,unavailable:message=>{throw Error(message);}});
  await assert.rejects(stable,/identity|authority/);
 }
});

import {awaitDrawerOn} from "../../release/emulator-gate.ts";
function drawerOnNodes(selected=false,dialog=false) {
 const owner=dialog?"com.qinggan.app.vehiclesetting":"com.qinggan.app.launcher";
 const window:any={id:dialog?69:62,type:dialog?-1:3,active:true,inputFocused:true,rootStatus:"available",rootPackage:owner,bounds:[0,0,1920,720],title:"",captureEnd:120201,scope:"default-display"};
 const base:any={packageName:owner,windowRef:window,visibleToUser:true,enabled:true,clickable:true,text:"",bounds:[450,142,1470,578],selected:false};
 const nodes:any=dialog ? [
 {...base,parent:null,resource:owner+":id/view_bg",className:"android.widget.RelativeLayout"},
 {...base,parent:0,resource:owner+":id/fragment_forceev_dialog",className:"android.widget.RelativeLayout"},
 {...base,parent:1,resource:owner+":id/disclaimer_agree",className:"android.widget.Button",text:"Yes",bounds:[555,447,935,527]},
 ]:[{...base,parent:null,resource:owner+":id/force_ev_switcher",className:"android.view.View",selected}];
 Object.defineProperty(nodes,"windowRefs",{value:[window]});return nodes;
}
test("drawer ON waits for delayed genuine OEM confirmation and clicks it only once",async()=>{
 let reads=0,taps=0,clock=0;
 const scenes=[drawerOnNodes(),drawerOnNodes(false,true),drawerOnNodes(false,true),drawerOnNodes(true)];
 await awaitDrawerOn({snapshot:async()=>scenes[reads++],tap:async node=>{assert.equal(node.resource,"com.qinggan.app.vehiclesetting:id/disclaimer_agree");taps++;}},
 async()=>({status:"ready",boot_id:drawerBoot,values:{"vehicle_state.IVI_SOC_MODESET":{raw:reads===4?5:2}}}),drawerBoot,10,()=>clock++);
 assert.equal(reads,4);assert.equal(taps,1);
});
test("drawer ON timeout retains final UI and CAN diagnostics without replay",async()=>{
 let taps=0,clock=0;
 await assert.rejects(()=>awaitDrawerOn({snapshot:async()=>drawerOnNodes(),tap:async()=>{taps++;}},
 async()=>({status:"ready",boot_id:drawerBoot,values:{"vehicle_state.IVI_SOC_MODESET":{raw:2}}}),drawerBoot,3,()=>clock++),/raw.*2.*selected.*false.*confirmed.*false/);
 assert.equal(taps,0);
});
test("drawer ON rejects foreign focus and malformed confirmation before clicking",async()=>{
 for(const mutation of [(nodes:any)=>nodes[0].windowRef.rootPackage="foreign.pkg",(nodes:any)=>nodes[1].resource="com.qinggan.app.vehiclesetting:id/other_dialog"]) {
  let taps=0;const nodes=drawerOnNodes(false,true);mutation(nodes);
  await assert.rejects(()=>awaitDrawerOn({snapshot:async()=>nodes,tap:async()=>{taps++;}},
   async()=>({status:"ready",boot_id:drawerBoot}),drawerBoot,1,()=>0));
  assert.equal(taps,0);
 }
});

test("drawer ARM obtains control after boot observation using real InstallerUi node membership",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts");
 const {mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(resolve(import.meta.dirname,"../../.test-drawer-membership-"));
 try {
  const events:string[]=[];
  const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/ui",async()=>{throw Error("CLI fallback");},async()=>{},
   async()=>drawerOnNodes(false),async request=>{if(request.action==="open-primary-launcher"){events.push("launch");return {};}assert.equal(request.action,"tap");events.push("tap");return {};});
  // A separately retained node really becomes stale on the next native read.
  const stale=(await ui.snapshot())[0];await ui.snapshot();
  await assert.rejects(()=>ui.tap(stale),/stale or foreign UI node/);
  const start=gate.indexOf('    await drawerCheck("fev-drawer-arm"'),end=gate.indexOf('    await drawerCheck("fev-drawer-hybrid"',start);
  const tapStart=gate.indexOf("    const tapSwitch = async"),tapEnd=gate.indexOf("    const drawerCheck =",tapStart);
  const arm=vm.runInNewContext("(async()=>{"+stripTypeScriptTypes(gate.slice(tapStart,tapEnd)+gate.slice(start,end))+"})",{
   tapDrawerSwitch,prepareDrawerHome,
   drawerCheck:async(_id,action)=>action(),toggle:async()=>events.push("toggle"),VEHICLE:{fev:"vehicle-drive-forced-ev"},ui,
   bootObservation:async()=>{events.push("bootSnapshot");await ui.snapshot();return{boot:drawerBoot};},
   probe:async strict=>{assert.equal(strict,true);events.push("bootProbe");return drawerBoot;},
   stableDrawer:async()=>{events.push("stableSnapshot");return ui.snapshot();},
   findSwitch:nodes=>nodes[0],performance,awaitDrawerOn:async()=>{},native:{can:async()=>({})},
   drawerState:async()=>[],openDrawer:async()=>drawerOnNodes(true),observations:async()=>{throw Error("unexpected diagnostics");}
  });
  await arm();
  assert.deepEqual(events,["toggle","bootProbe","launch","bootProbe","bootSnapshot","stableSnapshot","bootProbe","tap"]);
 } finally {rmSync(directory,{recursive:true,force:true});}
});

import {drawerCloseTarget} from "../../release/emulator-gate.ts";
/** Retained drawer171-19 action254 UI topology, all nodes; no image assets. */
function actualDrawerCloseNodes():any {
 const window={"id":20,"type":3,"active":true,"inputFocused":true,"rootPackage":"com.qinggan.app.launcher","rootStatus":"available","bounds":[0,0,1224,720],"title":"","captureEnd":120201,"scope":"default-display"};
 const rows=[{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":null,"bounds":[0,0,1224,720],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_vehicle_hiboard_new","className":"android.widget.RelativeLayout","text":"","parent":0,"bounds":[0,0,1224,720],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/view_screen_up_vehicle_hiboard_bg","className":"android.widget.ImageView","text":"","parent":1,"bounds":[0,0,1224,720],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_edit_layout","className":"android.view.ViewGroup","text":"","parent":1,"bounds":[60,36,1224,94],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_show_parking_toggle_view","className":"android.view.View","text":"","parent":3,"bounds":[60,44,141,86],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":true,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_show_parking_tip_view","className":"android.widget.TextView","text":"Auto pop up when shifting into P","parent":3,"bounds":[161,47,534,83],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_edit_btn","className":"android.widget.Button","text":"Edit","parent":3,"bounds":[1004,40,1164,90],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/component_added_container","className":"android.widget.FrameLayout","text":"","parent":1,"bounds":[46,129,779,720],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/drag_layer","className":"android.widget.FrameLayout","text":"","parent":7,"bounds":[46,129,779,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/workspace","className":"android.widget.FrameLayout","text":"","parent":8,"bounds":[46,129,779,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.view.ViewGroup","text":"","parent":9,"bounds":[46,129,779,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.view.ViewGroup","text":"","parent":10,"bounds":[46,129,779,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[46,129,206,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":12,"bounds":[46,129,206,289],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":13,"bounds":[56,139,206,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":13,"bounds":[86,145,176,235],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Driving","parent":13,"bounds":[56,240,206,273],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[226,129,386,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":17,"bounds":[226,129,386,289],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":18,"bounds":[236,139,386,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":18,"bounds":[266,145,356,235],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"DRV ST H","parent":18,"bounds":[236,240,386,273],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[439,129,779,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":22,"bounds":[439,129,779,289],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/group_force_ev","className":"android.widget.RelativeLayout","text":"","parent":23,"bounds":[449,139,779,289],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/force_ev_switcher","className":"android.view.View","text":"","parent":24,"bounds":[669,172,750,214],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_force_ev","className":"android.widget.TextView","text":"Forced EV","parent":24,"bounds":[647,240,757,273],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":23,"bounds":[479,145,569,235],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":23,"bounds":[439,240,779,273],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Electric","parent":28,"bounds":[476,240,576,273],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[46,309,206,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":30,"bounds":[46,309,206,469],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":31,"bounds":[56,319,206,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":31,"bounds":[86,325,176,415],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Rest","parent":31,"bounds":[56,420,206,453],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[226,309,386,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":35,"bounds":[226,309,386,469],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":36,"bounds":[236,319,386,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":36,"bounds":[266,325,356,415],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"DRV ST V","parent":36,"bounds":[236,420,386,453],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[439,309,599,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":40,"bounds":[439,309,599,469],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":41,"bounds":[449,319,599,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":41,"bounds":[479,325,569,415],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Open FFC","parent":41,"bounds":[449,420,599,453],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[619,309,779,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":45,"bounds":[619,309,779,469],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":46,"bounds":[629,319,779,469],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":46,"bounds":[659,325,749,415],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Open C. Port","parent":46,"bounds":[629,420,779,453],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[46,489,206,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":50,"bounds":[46,489,206,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":51,"bounds":[56,499,206,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":51,"bounds":[86,505,176,595],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Other","parent":51,"bounds":[56,600,206,633],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[226,489,386,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":55,"bounds":[226,489,386,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":56,"bounds":[236,499,386,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":56,"bounds":[266,505,356,595],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"DRV ST M","parent":56,"bounds":[236,600,386,633],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[439,489,599,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":60,"bounds":[439,489,599,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":61,"bounds":[449,499,599,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":61,"bounds":[479,505,569,595],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Easy Entry","parent":61,"bounds":[449,600,599,633],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":11,"bounds":[619,489,779,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":65,"bounds":[619,489,779,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":66,"bounds":[629,499,779,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":66,"bounds":[659,505,749,595],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Easy Exit","parent":66,"bounds":[629,600,779,633],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rv_added_large","className":"androidx.recyclerview.widget.RecyclerView","text":"","parent":1,"bounds":[826,129,1164,720],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":70,"bounds":[826,129,1164,379],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":71,"bounds":[826,129,1164,379],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":72,"bounds":[826,129,1164,379],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":73,"bounds":[826,151,1164,211],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Nap Mode","parent":74,"bounds":[866,160,1003,201],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":73,"bounds":[836,139,1164,379],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":70,"bounds":[826,399,1164,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":77,"bounds":[826,399,1164,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":78,"bounds":[826,399,1164,649],"visibleToUser":true,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":79,"bounds":[826,421,1164,481],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"S/C","parent":80,"bounds":[866,430,916,471],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/btv","className":"android.widget.TextView","text":"ON","parent":80,"bounds":[926,436,978,465],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":true,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":79,"bounds":[836,409,1164,649],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_pop_anchor","className":"android.view.View","text":"","parent":1,"bounds":[1198,290,1204,430],"visibleToUser":true,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/sv_right","className":"android.widget.ScrollView","text":"","parent":1,"bounds":[1284,0,1284,0],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":true,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.LinearLayout","text":"","parent":85,"bounds":[1284,0,1284,0],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":86,"bounds":[1284,0,1284,0],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rv_library","className":"androidx.recyclerview.widget.RecyclerView","text":"","parent":87,"bounds":[1284,0,1284,0],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1314,23,1314,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":89,"bounds":[1314,23,1314,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":90,"bounds":[1314,23,1314,23],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":91,"bounds":[1324,33,1324,33],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":91,"bounds":[1354,39,1354,39],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Open All Win","parent":91,"bounds":[1324,134,1324,134],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1499,23,1499,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":95,"bounds":[1499,23,1499,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":96,"bounds":[1499,23,1499,23],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":97,"bounds":[1509,33,1509,33],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":97,"bounds":[1539,39,1539,39],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Close All Win","parent":97,"bounds":[1509,134,1509,134],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1684,23,1684,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":101,"bounds":[1684,23,1684,23],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":102,"bounds":[1684,23,1684,23],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":103,"bounds":[1694,33,1694,33],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":103,"bounds":[1724,39,1724,39],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Vent Win","parent":103,"bounds":[1694,134,1694,134],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1314,223,1314,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":107,"bounds":[1314,223,1314,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":108,"bounds":[1314,223,1314,223],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":109,"bounds":[1324,233,1324,233],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":109,"bounds":[1354,239,1354,239],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Lock Doors","parent":109,"bounds":[1324,334,1324,334],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1499,223,1499,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":113,"bounds":[1499,223,1499,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":114,"bounds":[1499,223,1499,223],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":115,"bounds":[1509,233,1509,233],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":115,"bounds":[1539,239,1539,239],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"HDC","parent":115,"bounds":[1509,334,1509,334],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1684,223,1684,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":119,"bounds":[1684,223,1684,223],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":120,"bounds":[1684,223,1684,223],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":121,"bounds":[1694,233,1694,233],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":121,"bounds":[1724,239,1724,239],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"PED Alarm","parent":121,"bounds":[1694,334,1694,334],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":88,"bounds":[1314,423,1314,423],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":125,"bounds":[1314,423,1314,423],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":126,"bounds":[1314,423,1314,423],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":127,"bounds":[1314,534,1314,534],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Trunk","parent":128,"bounds":[1351,534,1351,534],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/btv","className":"android.widget.TextView","text":"ON","parent":128,"bounds":[1451,536,1451,536],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":true,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":127,"bounds":[1324,433,1324,433],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_des","className":"android.widget.ImageView","text":"","parent":127,"bounds":[1354,439,1354,439],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":86,"bounds":[1284,600,1284,600],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rv_library_large","className":"androidx.recyclerview.widget.RecyclerView","text":"","parent":133,"bounds":[1284,600,1284,600],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":134,"bounds":[1314,623,1314,623],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":135,"bounds":[1314,623,1314,623],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":136,"bounds":[1314,623,1314,623],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":137,"bounds":[1314,645,1314,645],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Eco","parent":138,"bounds":[1354,654,1354,654],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":137,"bounds":[1324,633,1324,633],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg_bottom","className":"android.widget.ImageView","text":"","parent":137,"bounds":[1324,720,1324,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_bottom","className":"android.widget.RelativeLayout","text":"","parent":137,"bounds":[1324,720,1324,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_drive_mode2","className":"android.widget.ImageView","text":"","parent":142,"bounds":[1402,720,1402,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_drive_mode3","className":"android.widget.ImageView","text":"","parent":142,"bounds":[1450,720,1450,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_drive_mode4","className":"android.widget.ImageView","text":"","parent":142,"bounds":[1498,720,1498,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_drive_mode5","className":"android.widget.ImageView","text":"","parent":142,"bounds":[1546,720,1546,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_drive_mode6","className":"android.widget.ImageView","text":"","parent":142,"bounds":[1594,720,1594,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":134,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":148,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":149,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":150,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"AMB LTG","parent":151,"bounds":[1354,720,1354,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/btv","className":"android.widget.TextView","text":"OFF","parent":151,"bounds":[1486,720,1486,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":150,"bounds":[1324,720,1324,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.FrameLayout","text":"","parent":134,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/screen_up_component_icon","className":"android.widget.RelativeLayout","text":"","parent":155,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/rl_root","className":"android.widget.RelativeLayout","text":"","parent":156,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":true,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"","className":"android.widget.RelativeLayout","text":"","parent":157,"bounds":[1314,720,1314,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/tv_title","className":"android.widget.TextView","text":"Fragrance","parent":158,"bounds":[1354,720,1354,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/btv","className":"android.widget.TextView","text":"OFF","parent":158,"bounds":[1497,720,1497,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false},{"packageName":"com.qinggan.app.launcher","resource":"com.qinggan.app.launcher:id/iv_bg","className":"android.widget.ImageView","text":"","parent":157,"bounds":[1324,720,1324,720],"visibleToUser":false,"enabled":true,"clickable":false,"scrollable":false,"longClickable":false,"selected":false,"checked":false}];
 const nodes:any=rows.map(n=>({...n,windowRef:window}));Object.defineProperty(nodes,"windowRefs",{value:[window]});return nodes;
}

test("drawer close point is outside every visible child hit region in retained real topology",()=>{
 const nodes=actualDrawerCloseNodes(),target=drawerCloseTarget(nodes);
 assert.ok(target.x2<target.x);assert.ok((target.x2-target.x)*1000/target.milliseconds < -500);
 for(const x of [target.x,target.x2]) assert.ok(x>target.region[0] && x<target.region[2]);
 assert.ok(target.y>target.region[1] && target.y<target.region[3]);
 assert.equal(target.node.parent,null);
 for(const n of nodes.filter(n=>n.visibleToUser && ![target.node,nodes[1],nodes[2]].includes(n)))
   assert.ok(!(target.x>=n.bounds[0] && target.x<n.bounds[2] && target.y>=n.bounds[1] && target.y<n.bounds[3]),"point overlaps "+n.resource);
 for(const change of [
  n=>n.push({...n[0],parent:1,resource:"unknown:id/nonclickable_listener"}),
  n=>n.find(x=>x.resource.endsWith("/screen_up_edit_btn")).text="Done",
  n=>n[2].className="android.view.View",
 ]) {const bad=actualDrawerCloseNodes();change(bad);assert.throws(()=>drawerCloseTarget(bad));}
});
test("drawer ON closes overlay once, confirms exposed OEM dialog, then accepts captured underlying owner",async()=>{
 let reads=0,closes=0,taps=0,clock=0;
 const drawer=actualDrawerCloseNodes(),dialog=drawerOnNodes(false,true),underlying=drawerOnNodes(false);
 underlying[0].windowRef.rootPackage="com.qinggan.app.setting";
 const scenes=[drawer,underlying,dialog,underlying];
 await awaitDrawerOn({snapshot:async()=>scenes[reads++],tap:async node=>{assert.equal(node.resource,"com.qinggan.app.vehiclesetting:id/disclaimer_agree");taps++;}},
 async()=>({status:"ready",boot_id:drawerBoot,values:{"vehicle_state.IVI_SOC_MODESET":{raw:reads===4?5:2}}}),
 drawerBoot,10,()=>clock++,async nodes=>{drawerCloseTarget(nodes);closes++;},"com.qinggan.app.setting");
 assert.equal(closes,1);assert.equal(taps,1);assert.equal(reads,4);
});

test("completed background input cannot claim drawer closed when overridden BlindsWindow handler leaves it open",async()=>{
 let clock=0,closes=0,taps=0;
 // BlindsWindow constructor replaces the earlier ACTION_DOWN listener.
 // Its zero-X-velocity path stays expanded; SDK completion is not a UI close proof.
 const drawer=actualDrawerCloseNodes();
 await assert.rejects(()=>awaitDrawerOn({snapshot:async()=>drawer,tap:async()=>{taps++;}},
 async()=>({status:"ready",boot_id:drawerBoot,values:{"vehicle_state.IVI_SOC_MODESET":{raw:2}}}),
 drawerBoot,6,()=>clock++,async()=>{closes++;}),/closed.*false.*closeRequested.*true/);
 assert.equal(closes,1);assert.equal(taps,0);
});
test("drawer close rejects background strips too narrow for the OEM negative velocity threshold",()=>{
 const nodes=actualDrawerCloseNodes();
 // Unknown child touch regions leave only a narrow non-actionable strip.
 nodes.push({...nodes[0],parent:1,resource:"unknown:id/listener",bounds:[30,0,1224,720]});
 assert.throws(()=>drawerCloseTarget(nodes),/empty background region unavailable/);
});

test("drawer ARM selected precondition performs one real OFF then fresh ON, and never masks OFF failure",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts");
 const {mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(resolve(import.meta.dirname,"../../.test-drawer-off-precondition-"));
 try {
  for(const mode of ["success","off-state-failure","off-reactivated"]) {
   let selected=true,onWaits=0,result:any;
   const taps:boolean[]=[],states:string[]=[];
   const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/"+mode,async()=>{throw Error("CLI fallback");},async()=>{},
    async()=>drawerOnNodes(selected),async request=>{if(request.action==="open-primary-launcher")return {};assert.equal(request.action,"tap");taps.push(selected);selected=!selected;return {};});
   const start=gate.indexOf('    await drawerCheck("fev-drawer-arm"'),end=gate.indexOf('    await drawerCheck("fev-drawer-hybrid"',start);
   const tapStart=gate.indexOf("    const tapSwitch = async"),tapEnd=gate.indexOf("    const drawerCheck =",tapStart);
  const arm=vm.runInNewContext("(async()=>{"+stripTypeScriptTypes(gate.slice(tapStart,tapEnd)+gate.slice(start,end))+"})",{
   tapDrawerSwitch,prepareDrawerHome,
    drawerCheck:async(_id,action)=>{result=await action();},toggle:async()=>{},VEHICLE:{fev:"vehicle-drive-forced-ev"},ui,
    bootObservation:async()=>{await ui.snapshot();return{boot:drawerBoot};},
    probe:async strict=>{assert.equal(strict,true);return drawerBoot;},
    stableDrawer:()=>ui.snapshot(),findSwitch:nodes=>nodes[0],performance,native:{can:async()=>({})},
    awaitDrawerOn:async()=>{onWaits++;},
    drawerState:async(raw,armed)=>{
     states.push(raw+":"+armed);await ui.snapshot();
     if(raw===2 && mode==="off-state-failure") throw Error("OFF saved intent unavailable");
     if(raw===2 && mode==="off-reactivated") selected=true;
     return [raw===2?"off-archive":"on-archive"];
    },
    openDrawer:()=>ui.snapshot(),observations:async()=>{throw Error("unexpected diagnostics");}
   });
   if(mode==="success") {
    await arm();assert.deepEqual(taps,[true,false]);assert.deepEqual(states,["2:false","5:true"]);
    assert.equal(onWaits,1);assert.deepEqual(Array.from(result),["off-archive","on-archive"]);
   } else {
    await assert.rejects(arm,mode==="off-state-failure"?/OFF saved intent unavailable/:/OFF precondition reactivated/);
    assert.deepEqual(taps,[true]);assert.equal(onWaits,0);assert.deepEqual(states,["2:false"]);
   }
  }
 } finally {rmSync(directory,{recursive:true,force:true});}
});

test("drawer settling uses elapsed OEM animation budget with real InstallerUi fresh membership",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts");
 const {mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(resolve(import.meta.dirname,"../../.test-drawer-settle-"));
 try {
  for(const scenario of [
   {name:"three-fast-not-ready",times:[0,50,100,300],expected:4},
   {name:"two-spanning-animation",times:[0,300],expected:2},
   {name:"changed-geometry-resets",times:[0,100,300,400],expected:4,changeAt:2},
   {name:"deadline",times:[0,10000],expected:2,error:true},
   {name:"read-cap",times:Array(65).fill(0),expected:65,error:true},
  ]) {
   let clock=0,reads=0,taps=0;
   const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/"+scenario.name,async()=>{throw Error("CLI fallback");},async()=>{},
    async()=>{clock=scenario.times[reads++];assert.notEqual(clock,undefined,"unexpected extra read");const nodes=settlementNodes();
     if(scenario.changeAt && reads>=scenario.changeAt) nodes[0].bounds=[460,142,1480,578];
     return nodes;},async request=>{assert.equal(request.action,"tap");taps++;return {};});
   const start=gate.indexOf("    const stableDrawer = async"),end=gate.indexOf("    const drawerCheck =",start);
   const stable=vm.runInNewContext(stripTypeScriptTypes(gate.slice(start,end))+";stableDrawer",{
    owner:"com.qinggan.app.launcher",performance:{now:()=>clock},probe:async()=>drawerBoot,nativeInputTree:{boot_id:drawerBoot,source_instance:"epoch",observer_pid:123},ui,openDrawer:()=>ui.snapshot(),unavailable:message=>{throw Error(message);}
   });
   if(scenario.error) {await assert.rejects(stable,/settling deadline|did not settle/);assert.equal(taps,0);}
   else {const nodes=await stable();await ui.tap(nodes[0]);assert.equal(taps,1);}
   assert.equal(reads,scenario.expected);
  }
 } finally {rmSync(directory,{recursive:true,force:true});}
});

function coldDrawerFocusNodes(kind:string):any {
 const launcher="com.qinggan.app.launcher";
 const window:any={id:26,type:1,active:true,inputFocused:true,rootStatus:"available",rootPackage:launcher,scope:"default-display",bounds:[0,0,1920,720]};
 const root:any={parent:null,windowRef:window,packageName:launcher,enabled:true,visibleToUser:true};
 let windows:any[]=[window],nodes:any=[root];
 if(kind==="none") {
  windows=[{...window,id:12,type:3,active:false,inputFocused:false,bounds:[0,0,30,720]},{...window,id:13,type:3,active:false,inputFocused:false,bounds:[145,0,1920,50]},{...window,id:15,type:-1,active:false,inputFocused:false,bounds:[0,0,145,720]}];
  nodes=windows.map(w=>({...root,windowRef:w}));
 }
 if(kind==="foreign") {window.rootPackage="foreign.pkg";root.packageName=window.rootPackage;}
 if(kind==="multiple") windows.push({...window,id:99});
 if(kind==="partial") window.rootStatus="unavailable";
 if(kind==="missing-root") nodes=[];
 if(kind==="foreign-root") root.packageName="foreign.pkg";
 if(kind==="empty") window.bounds=[0,0,1920,0];
 Object.defineProperty(nodes,"windowRefs",{value:windows});return nodes;
}
test("cold OFF launches once and requires fresh unique focused owner before drawer gesture",async()=>{
 const start=gate.indexOf('    await drawerCheck("fev-drawer-cold-off"'),end=gate.indexOf("\n    });",start)+"\n    });".length;
 const program=stripTypeScriptTypes(gate.slice(start,end));
 for(const scenario of [
  {name:"ready-after-chrome",scenes:["none","ready"],pass:true,reads:2},
  {name:"no-owner",scenes:["none"],reads:32},
  {name:"foreign",scenes:["foreign"],reads:32},
  {name:"multiple",scenes:["multiple"],reads:32},
  {name:"partial",scenes:["partial"],reads:32},
  {name:"missing-root",scenes:["missing-root"],reads:32},
  {name:"foreign-root",scenes:["foreign-root"],reads:32},
  {name:"empty",scenes:["empty"],reads:32},
  {name:"deadline",scenes:["ready"],reads:1,timeout:true},
 ]) {
  let reads=0,clock=0;const events:string[]=[];
  const run=vm.runInNewContext("(async()=>{"+program+"})",{
   drawerCheck:async(_id,action)=>action(),reboot:async()=>events.push("reboot"),drawerState:async(raw,armed)=>{assert.equal(raw,2);assert.equal(armed,false);events.push("state");return [];},
   ui:{call:async request=>{assert.deepEqual(Object.keys(request),["action"]);assert.equal(request.action,"open-primary-launcher");events.push("launch");},snapshot:async()=>{
    const kind=scenario.scenes[Math.min(reads++,scenario.scenes.length-1)];events.push("read:"+kind);clock=scenario.timeout?5000:reads*10;return coldDrawerFocusNodes(kind);
   }},
   performance:{now:()=>clock},probe:async()=>drawerBoot,prepareDrawerHome:(ui,probe,deadline)=>prepareDrawerHome(ui,probe,deadline,()=>clock),unavailable:message=>{throw Error(message);},
   openDrawer:async()=>{events.push("gesture");return[{selected:false}];},findSwitch:nodes=>nodes[0]
  });
  if(scenario.pass) {await run();assert.equal(events.at(-1),"gesture");assert.deepEqual(events.slice(0,5),["reboot","state","launch","read:none","read:ready"]);}
  else {await assert.rejects(run,/focused Home owner not ready/);assert.ok(!events.includes("gesture"));}
  assert.equal(events.filter(e=>e==="launch").length,1);assert.equal(reads,scenario.reads);
 }
 // The new launch is restricted to cold-off, not the other six drawer steps.
 const drawerStart=gate.indexOf("  const drawerProgram = async"),coldStart=gate.indexOf('    await drawerCheck("fev-drawer-cold-off"',drawerStart);
 assert.equal(gate.slice(drawerStart,coldStart).includes('action:"open-primary-launcher"'),false);
});

import {awaitDrawerPowerCard,drawerClosedHomeSignature} from "../../release/emulator-gate.ts";
function closedDrawer199():any {
 const owner="com.qinggan.app.launcher";
 const home:any={id:9,type:1,layer:0,active:true,inputFocused:true,rootPackage:owner,rootStatus:"available",scope:"default-display",title:"module-widget",bounds:[0,0,1920,720]};
 const edgeWindow:any={...home,id:11,type:3,layer:4,active:false,inputFocused:false,title:"",bounds:[0,0,30,720]};
 const nodes:any=[
  {packageName:owner,resource:"",className:"android.widget.FrameLayout",parent:null,enabled:true,visibleToUser:true,bounds:home.bounds,windowRef:home},
  {packageName:owner,resource:"",className:"android.widget.FrameLayout",parent:null,enabled:true,visibleToUser:true,bounds:edgeWindow.bounds,windowRef:edgeWindow},
  {packageName:owner,resource:owner+":id/line_view",className:"android.view.View",parent:1,enabled:true,visibleToUser:true,bounds:[10,290,16,430],windowRef:edgeWindow},
 ];
 nodes.windowRefs=[home,edgeWindow];return nodes;
}
test("drawer199 semantic Home signature ignores observation timestamps but rejects changed authority",()=>{
 const baseline=drawerClosedHomeSignature(closedDrawer199());assert.ok(baseline);
 const fresh=closedDrawer199();fresh.windowRefs.forEach(w=>w.captureEnd=123456);
 assert.equal(drawerClosedHomeSignature(fresh),baseline);
 for(const change of [
  n=>n.windowRefs[0].rootPackage="foreign.pkg",
  n=>n.windowRefs[0].type=-1,
  n=>n.windowRefs[0].id=99,
  n=>n[2].bounds=[11,290,17,430],
  n=>n.windowRefs.push({...n.windowRefs[0],id:123,type:-1,active:false,inputFocused:false,rootPackage:"foreign.dialog"}),
 ]) {const n=closedDrawer199();change(n);assert.notEqual(drawerClosedHomeSignature(n),baseline);}
});
test("drawer199 unchanged closed Home permits exactly one fresh opening, not switch or mode replay",async()=>{
 let clock=0,reads=0,opens=0;const baseline=drawerClosedHomeSignature(closedDrawer199());
 const visible=actualDrawerCloseNodes();
 const ui={snapshot:async()=>{clock+=700;reads++;return opens?visible:closedDrawer199();}};
 const result=await awaitDrawerPowerCard(ui,async()=>drawerBoot,drawerBoot,5000,()=>clock,{
  afterMs:2000,reopen:async nodes=>{assert.equal(drawerClosedHomeSignature(nodes),baseline);opens++;}
 });
 assert.equal(result,visible);assert.equal(opens,1);assert.equal(reads,4);
});
test("drawer199 delayed drawer appearance does not dispatch another opening",async()=>{
 let clock=0,reads=0,opens=0;
 await awaitDrawerPowerCard({snapshot:async()=>{clock+=700;return ++reads===2?actualDrawerCloseNodes():closedDrawer199();}},
  async()=>drawerBoot,drawerBoot,5000,()=>clock,{afterMs:2000,reopen:async()=>{opens++;}});
 assert.equal(opens,0);assert.equal(reads,2);
});
test("drawer199 second no-effect opening terminates within original deadline",async()=>{
 let clock=0,opens=0;
 await assert.rejects(()=>awaitDrawerPowerCard({snapshot:async()=>{clock+=700;return closedDrawer199();}},
  async()=>drawerBoot,drawerBoot,5000,()=>clock,{afterMs:2000,reopen:async()=>{opens++;}}),/not visible before deadline/);
 assert.equal(opens,1);assert.equal(clock,5600);
});
test("drawer199 opening retry uses fresh receiver fence and rejects changed epoch or Home",async()=>{
 const begin=gate.indexOf("    const openDrawer = async"),end=gate.indexOf("    const stableDrawer = async",begin);
 for(const mutation of ["none","epoch","pid","boot","window","receiver","dialog"]) {
  let clock=0,reads=0,opens=0,rootFences=0,extraBootProbes=0;const tree:any={boot_id:drawerBoot,source_instance:"epoch199",observer_pid:5622};
  const ui:any={
   snapshot:async()=>{
    reads++;rootFences++;const nodes=closedDrawer199();
    if(reads>1) clock+=700;
    if(reads>=4) {
     if(mutation==="epoch") tree.source_instance="other";
     if(mutation==="pid") tree.observer_pid=999;
     if(mutation==="boot") tree.boot_id="other";
     if(mutation==="window") nodes.windowRefs[0].id=99;
     if(mutation==="receiver") nodes[2].bounds=[11,290,17,430];
     if(mutation==="dialog") nodes.windowRefs.push({...nodes.windowRefs[0],id:999,type:-1,active:false,inputFocused:false,rootPackage:"foreign.dialog"});
    }
    return opens===2?actualDrawerCloseNodes():nodes;
   },
   gestureAuthority:node=>{assert.equal(node,ui.latestReceiver);return {fresh:true};},
   call:async request=>{assert.equal(request.action,"gesture");assert.equal(request.fresh,true);opens++;},
  };
  const snapshot=ui.snapshot;ui.snapshot=async()=>{const nodes=await snapshot();ui.latestReceiver=nodes.find(n=>n.resource.endsWith("line_view"));return nodes;};
  const open=vm.runInNewContext(stripTypeScriptTypes(gate.slice(begin,end))+";openDrawer",{
   owner:"com.qinggan.app.launcher",probe:async()=>{rootFences++;extraBootProbes++;return drawerBoot;},performance:{now:()=>clock},ui,nativeInputTree:tree,
   center:n=>[(n.bounds[0]+n.bounds[2])/2,(n.bounds[1]+n.bounds[3])/2],
   drawerClosedHomeSignature,awaitDrawerPowerCard,unavailable:message=>{throw Error(message);}
  });
  if(mutation==="none") {await open();assert.equal(opens,2);}
  else {await assert.rejects(open,/authority changed|crossed boot/);assert.equal(opens,1);}
  assert.equal(extraBootProbes,1,"only initial boot probe; snapshot already supplies its fresh root fence");
  assert.equal(rootFences,reads+1,"one root fence per snapshot, no duplicate visibility-loop probe");
 }
});


test("drawer observation deadline begins after accepted gesture and retains outer bound", async () => {
 const begin=gate.indexOf("    const openDrawer = async"),end=gate.indexOf("    const stableDrawer =",begin);
 const owner="com.qinggan.app.launcher";let clock=0,dispatches=0,observedDeadline=0;
 const edge={packageName:owner,resource:owner+":id/line_view",className:"android.view.View",visibleToUser:true,enabled:true,parent:0,bounds:[10,290,16,430],windowRef:{type:3,rootPackage:owner,bounds:[0,0,30,720]}};
 const ui={snapshot:async()=>{clock+=4500;return [edge];},gestureAuthority:()=>({}),call:async()=>{dispatches++;clock+=1000;}};
 const run=vm.runInNewContext(stripTypeScriptTypes(gate.slice(begin,end))+";openDrawer",{owner,probe:async()=>"boot",performance:{now:()=>clock},ui,center:()=>[13,360],drawerClosedHomeSignature:()=>null,nativeInputTree:{source_instance:"source",observer_pid:1},awaitDrawerPowerCard:async(_ui,_readBoot,_boot,deadline)=>{observedDeadline=deadline;assert(deadline>clock,"preparation consumed the visibility budget");return [edge];},unavailable:message=>{throw Error(message)}});
 await run(10000);assert.equal(dispatches,1);assert.equal(observedDeadline,10000);assert.equal(observedDeadline-clock,4500);
 clock=6000;dispatches=0;await assert.rejects(run(10000),/before gesture/);assert.equal(dispatches,0);
});

test("OEM window may finish valid focus acquisition after five seconds without repeated launch",async()=>{
 const times=[0,1000,6000];let clock=0;const n=navigation([parseUi(focusedXml())],()=>times[Math.min(clock++,times.length-1)]);
 await n.open("fev");assert.deepEqual(n.events,["open-vehicle-settings","snapshot"]);
});
