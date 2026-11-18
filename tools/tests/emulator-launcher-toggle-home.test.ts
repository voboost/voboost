import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {launcherToggleReturnedHome, waitToggleFocus} from "../../release/emulator-gate.ts";
import {parseUi} from "../emulator/installer-ui.ts";
const xml=readFileSync(new URL("../../testdata/emulator/cycle124-case18-home.xml",import.meta.url),"utf8");
test("actual case18 retained Home is a launcher-only post-confirmation recovery",()=>{
 const nodes=parseUi(xml); assert.equal(launcherToggleReturnedHome("launcher-apps",nodes),true);
 for(const id of ["settings","font-cyrillic","vehicle-drive-forced-ev"]) assert.equal(launcherToggleReturnedHome(id,nodes),false);
 assert.equal(launcherToggleReturnedHome("launcher-apps",[]),false);
 for(const changed of [xml.replaceAll("module-widget","Other"),xml.replaceAll("com.qinggan.app.launcher","foreign.app"),xml.replaceAll(`type="1"`,`type="3"`),xml.replaceAll(`active="true"`,`active="false"`)]) assert.equal(launcherToggleReturnedHome("launcher-apps",parseUi(changed)),false);
});

test("confirmed restart waits for fresh focused tree without input", async () => {
 const focused=parseUi(xml), inactive=parseUi(xml.replaceAll('active="true"','active="false"').replaceAll('input-focused="true"','input-focused="false"'));
 let reads=0,guards=0;
 const result=await waitToggleFocus(inactive,async()=>++reads===2?focused:inactive,async()=>{guards++;});
 assert.equal(result,focused);assert.equal(reads,2);assert.equal(guards,2);
});
test("restart focus waiting rejects absent focus and boot drift",async()=>{
 let reads=0;await assert.rejects(waitToggleFocus([],async()=>{reads++;return [];},async()=>{}),/unavailable/);assert.equal(reads,6);
 await assert.rejects(waitToggleFocus([],async()=>{throw Error("must not read");},async()=>{throw Error("boot drift");}),/boot drift/);
});
test("focused tree needs no recovery and deadline cannot overrun",async()=>{
 const focused=parseUi(xml);assert.equal(await waitToggleFocus(focused,async()=>{throw Error("unexpected");},async()=>{throw Error("unexpected");}),focused);
 let time=0;await assert.rejects(waitToggleFocus([],async()=>{time=15001;return focused;},async()=>{},()=>time),/deadline/);
});

import {launcherRestartUnfocusedHome, recoverToggleHome, type ToggleRestartProof} from "../../release/emulator-gate.ts";
import {nativeTree} from "../emulator/native-tree.ts";
import type {UiNodes, WindowRef} from "../emulator/installer-ui.ts";
const launcher="com.qinggan.app.launcher", settings="com.qinggan.app.setting", agent="font-cyrillic";
function restartProof(): ToggleRestartProof {
 const identity={schemaVersion:1,releaseIdentity:"fixture",manifestSha256:"a".repeat(64),generationId:"b".repeat(64),catalogSha256:"c".repeat(64),startupMode:"auto",state:"ready",killed:false,panic:false};
 return {mode:"restart",targets:[launcher],beforeBoot:"boot",afterBoot:"boot",beforePids:{[launcher]:"10"},afterPids:{[launcher]:"11"},beforeResident:{...identity,injections:[{id:"settings",process:settings,state:"active",documentGeneration:"1"},{id:agent,process:launcher,state:"active"}]},currentResident:{...identity,injections:[{id:"settings",process:settings,state:"active",documentGeneration:"2"}]}};
}
// Minimized actual complete action-159 capture: all window metadata and each
// observed root retained verbatim; child nodes are irrelevant to navigation.
const nativeHomeFixture={"format":"voyemu.ui.v1","source":"android.ui","profile":"free-09-chinese-20260106-6.0.3","session_id":"eb2b83563238962e7d25acca6983784d","android_pid":53429,"boot_id":"fdb72712-f746-4d9a-a1e3-f98409330422","observer_pid":7004,"observation_id":"f6c1809a-2f19-4e3d-ab30-c84ab458e6be","started_uptime_ms":"132751","completed_uptime_ms":"132779","host_interval":{"clock_domain":"host.steady","clock_id":"eb2b83563238962e7d25acca6983784d","end_ns":"469240786927000","start_ns":"469240751223000"},"complete":true,"status":"ready","atomic_ui_state":false,"screens":[{"atomic_ui_state":false,"complete":true,"display_id":0,"height":720,"nodes":[{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":30,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"1","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":21},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":50,"width":1775,"x":145,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"3","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":20},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":true,"bounds":{"height":180,"width":280,"x":218,"y":138},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":true,"clipped_bounds":{"height":180,"width":280,"x":218,"y":138},"description":"","enabled":true,"focused":false,"hint":"","id":"5","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.ivoka","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":6},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":145,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"6","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":18},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"21","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":24}],"scope":"api28_default_display","screen":"primary","sdk_display_id":0,"status":"ok","unavailable_windows":[],"width":1920,"windows":[{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"component":null,"id":21,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":4,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"component":null,"id":20,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":3,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":180,"width":280,"x":218,"y":138},"component":null,"id":6,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":2,"package":"com.qinggan.ivoka","title":"","type":1},{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"component":null,"id":18,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":1,"package":"com.qinggan.app.launcher","title":"","type":-1},{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"component":null,"id":24,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":0,"package":"com.qinggan.app.launcher","title":"module-widget","type":1}]}]};
function unfocusedHome(): UiNodes {
 const v=structuredClone(nativeHomeFixture);
 return nativeTree(v,{sessionId:v.session_id,pid:v.android_pid,bootId:v.boot_id,profile:v.profile});
}
function changedWindow(nodes:UiNodes,id:number,patch:Partial<WindowRef>):UiNodes {
 const refs=nodes.windowRefs!.map(w=>w.id===id?{...w,...patch}:w), next:UiNodes=nodes.map(n=>({...n,windowRef:refs.find(w=>w.id===n.windowRef!.id)}));
 Object.defineProperty(next,"windowRefs",{value:refs});return next;
}
test("restart recovery admits unfocused fullframe Home with inactive foreign popup only after ACK/PID proof",()=>{
 const nodes=unfocusedHome(), proof=restartProof();assert.equal(launcherRestartUnfocusedHome(agent,nodes,proof),true);
 assert.equal(launcherRestartUnfocusedHome(agent,nodes),false);
 for(const mutate of [
  (p:ToggleRestartProof)=>{p.mode="in-place";},(p:ToggleRestartProof)=>{p.afterBoot="other";},
  (p:ToggleRestartProof)=>{p.targets=[];},(p:ToggleRestartProof)=>{p.afterPids[launcher]="10";},
  (p:ToggleRestartProof)=>{delete p.afterPids[launcher];},(p:ToggleRestartProof)=>{p.beforePids[launcher]="0";},
  (p:ToggleRestartProof)=>{p.beforeResident.injections[1].state="waiting";},
  (p:ToggleRestartProof)=>{p.currentResident.injections[0].documentGeneration="1";},
  (p:ToggleRestartProof)=>{p.currentResident.injections.push({...p.beforeResident.injections[1]});},
  (p:ToggleRestartProof)=>{p.targets.push(settings);p.beforePids[settings]="20";p.afterPids[settings]="20";p.beforeResident.injections.push({id:agent,process:settings,state:"active"});}
 ]) {const p=restartProof();mutate(p);assert.equal(launcherRestartUnfocusedHome(agent,nodes,p),false);}
});
test("unfocused recovery rejects foreign focus/active app and incomplete or wrong primary Home",()=>{
 const nodes=unfocusedHome(), p=restartProof(), home=nodes.windowRefs!.find(w=>w.title==="module-widget")!;
 for(const patch of [{title:"Other"},{rootPackage:"foreign"},{type:3},{bounds:[0,0,1919,720]},{inputFocused:true},{rootStatus:"unavailable"},{scopeBasis:"aosp28-default-window-observer" as const}]) assert.equal(launcherRestartUnfocusedHome(agent,changedWindow(nodes,home.id,patch),p),false);
 for(const patch of [{active:true},{inputFocused:true},{type:3,active:true},{type:-1,active:true}]) assert.equal(launcherRestartUnfocusedHome(agent,changedWindow(nodes,nodes.windowRefs!.find(w=>w.rootPackage==="com.qinggan.ivoka")!.id,patch),p),false);
 assert.equal(launcherRestartUnfocusedHome(agent,[...nodes],p),false);
 const noRoot:UiNodes=nodes.filter(n=>!(n.windowRef===home&&n.parent===null));Object.defineProperty(noRoot,"windowRefs",{value:nodes.windowRefs});assert.equal(launcherRestartUnfocusedHome(agent,noRoot,p),false);
});
test("production recovery navigates once after boot guard without replay and returns fresh readback",async()=>{
 const events:string[]=[], nodes=unfocusedHome(), after=parseUi(xml), p=restartProof();
 const result=await recoverToggleHome(agent,nodes,p,async()=>{events.push("readback");return after;},async()=>{events.push("boot");},async()=>{events.push("fixed-open-settings");},()=>0,15000);
 assert.equal(result,after);assert.deepEqual(events,["boot","fixed-open-settings","readback"]);
 let opens=0;await assert.rejects(recoverToggleHome(agent,nodes,p,async()=>after,async()=>{throw Error("boot drift");},async()=>{opens++;},()=>0,15000),/boot drift/);assert.equal(opens,0);
 await assert.rejects(recoverToggleHome(agent,nodes,p,async()=>after,async()=>{},async()=>{opens++;},()=>15000,15000),/deadline/);assert.equal(opens,0);
});
test("production recovery can observe Home settling without replaying the preceding mutation",async()=>{
 const events:string[]=[], home=unfocusedHome(), p=restartProof();let reads=0;
 const result=await recoverToggleHome(agent,[],p,async()=>{events.push("read");return ++reads===1?home:parseUi(xml);},async()=>{events.push("boot");},async()=>{events.push("open");},()=>0,15000);
 assert.ok(result.some(n=>n.windowRef?.inputFocused));assert.deepEqual(events,["boot","read","boot","open","read"]);
});

test("partial native capture never provides restart navigation authority",()=>{
 const v=structuredClone(nativeHomeFixture);v.complete=false;v.status="partial";
 assert.throws(()=>nativeTree(v,{sessionId:v.session_id,pid:v.android_pid,bootId:v.boot_id,profile:v.profile}),/completeness/);
});
test("boot guard cannot consume navigation deadline and then dispatch",async()=>{
 let time=0,opens=0;
 await assert.rejects(recoverToggleHome(agent,unfocusedHome(),restartProof(),async()=>[],async()=>{time=15000;},async()=>{opens++;},()=>time,15000),/deadline/);
 assert.equal(opens,0);
});

import {ensurePrimaryLauncherFocus,knownSecondaryLauncherFocus} from "../../release/emulator-gate.ts";
const secondaryProof=async()=>({boot:"boot",bootCompleted:true,body:"window-focus:\n  mCurrentFocus=Window{abc u0 com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.AllAppActivity}\nandroid-boot-errors:\n"});
function focusHome():UiNodes {
 const nodes=unfocusedHome(),dock=nodes.windowRefs!.find(w=>w.type===-1)!;
 const home={...nodes[0],windowRef:dock,packageName:launcher,resource:launcher+":id/screen_up_home_icon",className:"android.widget.RadioButton",enabled:true,clickable:true,visibleToUser:true,bounds:[14,8,130,98],rawBounds:[14,8,130,98],text:""};
 const result:UiNodes=nodes.filter(n=>n.resource!==home.resource);result.push(home);Object.defineProperty(result,"windowRefs",{value:nodes.windowRefs});return result;
}
function focusedHome(nodes=focusHome()):UiNodes {return changedWindow(nodes,nodes.windowRefs!.find(w=>w.title==="module-widget")!.id,{active:true,inputFocused:true});}
test("primary focus restores once using fresh inactive OEM Home target and confirms actual focused main",async()=>{
 let reads=0;const calls:any[]=[];const before=focusHome();
 await ensurePrimaryLauncherFocus({snapshot:async()=>++reads===1?before:focusedHome(),call:async r=>{calls.push(r);return {};}},
 async()=>"boot",()=>"observer",5000,()=>0,secondaryProof);
 assert.equal(calls.length,1);const r=calls[0];assert.equal(r.action,"tap");assert.deepEqual([r.x,r.y],[72,53]);
 assert.equal(r.expectedWindow.id,before.windowRefs!.find(w=>w.type===1 && w.rootPackage===launcher)!.id);
 assert.equal(r.expectedTarget.resource,launcher+":id/screen_up_home_icon");assert.equal(reads,2);
});
test("already focused primary needs no focus input",async()=>{
 await ensurePrimaryLauncherFocus({snapshot:async()=>focusedHome(),call:async()=>{throw Error("unexpected");}},async()=>"boot",()=>"observer",5000,()=>0,secondaryProof);
});
test("primary focus rejects foreign focus, ambiguous Home and wrong containment before dispatch",async()=>{
 const base=focusHome();const variants=[
 changedWindow(base,base.windowRefs!.find(w=>w.rootPackage==="com.qinggan.ivoka")!.id,{active:true}),
 changedWindow(base,base.windowRefs!.find(w=>w.type===-1)!.id,{bounds:[150,0,295,720]}),
 ];
 const duplicate:UiNodes=[...base,{...base.at(-1)!}];Object.defineProperty(duplicate,"windowRefs",{value:base.windowRefs});variants.push(duplicate);
 for(const nodes of variants) {let taps=0;await assert.rejects(ensurePrimaryLauncherFocus({snapshot:async()=>nodes,call:async()=>{taps++;return {};}},async()=>"boot",()=>"observer",5000,()=>0));assert.equal(taps,0);}
});
test("accepted focus tap is never replayed and cannot substitute for actual focus",async()=>{
 let taps=0;await assert.rejects(ensurePrimaryLauncherFocus({snapshot:async()=>focusHome(),call:async()=>{taps++;return {};}},async()=>"boot",()=>"observer",5000,()=>0,secondaryProof),/not confirmed/);assert.equal(taps,1);
});
test("focus rejects observer and boot drift following a single accepted tap",async()=>{
 for(const kind of ["source","boot"]) {let reads=0,boots=0,taps=0;
 await assert.rejects(ensurePrimaryLauncherFocus({snapshot:async()=>{reads++;return reads===1?focusHome():focusedHome();},call:async()=>{taps++;return {};}},async()=>++boots>3&&kind==="boot"?"other":"boot",()=>reads>1&&kind==="source"?"other":"observer",5000,()=>0,secondaryProof),/crossed/);
 assert.equal(taps,1);
 }
});

import {nativePointerRequest} from "../emulator/installer-ui.ts";
test("inactive Home focus uses real InstallerUi validation then preserves native target fence",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts");
 const {mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(new URL("../../.test-launcher-focus-",import.meta.url).pathname);
 try {
  const v:any=structuredClone(nativeHomeFixture);v.source_instance="observer-epoch";v.revision="revision-1";
  const screen=v.screens[0],dock=screen.windows.find((w:any)=>w.type===-1);
  screen.nodes.push({...screen.nodes[0],id:"999",parent:screen.nodes.find((n:any)=>n.window_id===dock.id&&n.parent===null).id,actionable_candidate:true,window_id:dock.id,package:launcher,resource_id:launcher+":id/screen_up_home_icon",class:"android.widget.RadioButton",clickable:true,visible:true,enabled:true,bounds:{x:14,y:8,width:116,height:90},clipped_bounds:{x:14,y:8,width:116,height:90}});
  const before=nativeTree(v,{sessionId:v.session_id,pid:v.android_pid,bootId:v.boot_id,profile:v.profile});
  let reads=0,taps=0;
  const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/ui",async()=>{throw Error("CLI fallback");},async()=>{},
   async()=>++reads===1?before:changedWindow(before,screen.windows.find((w:any)=>w.title==="module-widget").id,{active:true,inputFocused:true}),
   async request=>{
    taps++;
    const result=nativePointerRequest(request,v,{sessionId:v.session_id,pid:v.android_pid,boot:v.boot_id});
    assert.equal(result.op,"input.tap");const fields=result.fields as any;
    assert.equal((request.expectedWindow as any).windowType,1);
    assert.equal((request.expectedTarget as any).windowType,-1);
    assert.equal(fields.expected.source_instance,v.source_instance);assert.equal(fields.expected.boot_id,v.boot_id);
    assert.equal(fields.expected.window.id,dock.id);assert.equal(fields.expected.target.resource_id,launcher+":id/screen_up_home_icon");
    return {};
   });
  await ensurePrimaryLauncherFocus(ui,async()=>v.boot_id,()=>v.source_instance,5000,()=>0,async()=>({...await secondaryProof(),boot:v.boot_id}));
  assert.equal(taps,1);assert.equal(reads,2);
 } finally {rmSync(directory,{recursive:true,force:true});}
});

import {launcherPrimaryPointerWindow,launcherPointerNode,focusedApplication,uniqueNode} from "../emulator/installer-ui.ts";
import {launcherItems} from "../../release/emulator-gate.ts";
import {launcherHomeGesture} from "../emulator/launcher-entry.ts";
import {launcherCaptureContinuity} from "../emulator/launcher-capture.ts";
test("native launcher pointer admits visible inactive primary without inventing global focus",()=>{
 const nodes=unfocusedHome(),main=launcherPrimaryPointerWindow(nodes);
 assert.equal(main.active,false);assert.equal(main.inputFocused,false);
 assert.equal(focusedApplication(nodes.find(n=>n.windowRef===main)!),false);
 assert.throws(()=>uniqueNode(nodes,n=>n.windowRef===main),/selector/);
 for(const patch of [{active:true},{inputFocused:true}]) assert.throws(()=>launcherPrimaryPointerWindow(changedWindow(nodes,nodes.windowRefs!.find(w=>w.rootPackage==="com.qinggan.ivoka")!.id,patch)),/foreign or overlay/);
 const duplicate=changedWindow(nodes,nodes.windowRefs!.find(w=>w.rootPackage==="com.qinggan.ivoka")!.id,{rootPackage:launcher});
 assert.throws(()=>launcherPrimaryPointerWindow(duplicate),/unique primary/);
});
test("real native inactive dock pointer uses exact target fence and rejects stale or foreign nodes",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts"),{mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(new URL("../../.test-launcher-pointer-",import.meta.url).pathname);
 try {
  const v:any=structuredClone(nativeHomeFixture);v.source_instance="observer";v.revision="revision";
  const screen=v.screens[0],dock=screen.windows.find((w:any)=>w.type===-1);
  const root=screen.nodes.find((n:any)=>n.window_id===dock.id&&n.parent===null);
  screen.nodes.push({...root,id:"998",parent:root.id,actionable_candidate:true,resource_id:launcher+":id/screen_up_all_app_icon",class:"android.widget.RadioButton",enabled:true,visible:true,clickable:true,bounds:{x:14,y:100,width:116,height:90},clipped_bounds:{x:14,y:100,width:116,height:90}});
  let dispatches=0;
  const read=()=>nativeTree(v,{sessionId:v.session_id,pid:v.android_pid,bootId:v.boot_id,profile:v.profile});
  const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/ui",async()=>{throw Error("CLI fallback");},async()=>{},async()=>read(),async request=>{
   const pointer=nativePointerRequest(request,v,{sessionId:v.session_id,pid:v.android_pid,boot:v.boot_id});
   assert.equal(pointer.op,"input.tap");const f=pointer.fields as any;assert.equal(f.expected.window.id,dock.id);
   assert.equal(f.expected.target.resource_id,launcher+":id/screen_up_all_app_icon");dispatches++;return {};
  });
  await ui.openLauncherApps();assert.equal(dispatches,1);
  const nodes=await ui.snapshot(),target=nodes.find(n=>n.resource.endsWith("screen_up_all_app_icon"))!;
  ui.launcherPointerAuthority(target);await ui.snapshot();
  assert.throws(()=>ui.launcherPointerAuthority(target),/stale/);
  assert.throws(()=>ui.launcherPointerAuthority({...target}),/stale/);
  assert.equal(dispatches,1);
 } finally {rmSync(directory,{recursive:true,force:true});}
});
test("native launcher route readback still requires actual grid and named children",()=>{
 const nodes=unfocusedHome();assert.throws(()=>launcherPointerNode(nodes,n=>n.resource.endsWith("all_app_view")),/absent/);
 const main=launcherPrimaryPointerWindow(nodes),grid={...nodes.find(n=>n.windowRef===main)!,resource:launcher+":id/all_app_view",bounds:[145,0,1920,720]};
 const augmented:UiNodes=[...nodes,grid];Object.defineProperty(augmented,"windowRefs",{value:nodes.windowRefs});
 assert.deepEqual(launcherItems(augmented,grid,true),[]);
 const child={...grid,parent:augmented.length-1,clickable:true,resource:"",bounds:[200,100,350,250]};
 augmented.push(child,{...grid,parent:augmented.length,resource:launcher+":id/item_name",text:"FMPlay",bounds:[200,250,350,280]});
 assert.equal(launcherItems(augmented,grid,true)[0].label,"FMPlay");
 assert.deepEqual(launcherItems(augmented,grid),[]);
});

test("real inactive Home swipe dispatches fenced pager and requires independent actual grid readback",async()=>{
 const {InstallerUi}=await import("../emulator/installer-ui.ts"),{openLauncherAppsFromHome}=await import("../emulator/launcher-entry.ts");
 const {mkdtempSync,rmSync}=await import("node:fs"),directory=mkdtempSync(new URL("../../.test-launcher-swipe-",import.meta.url).pathname);
 try {
  const v:any=structuredClone(nativeHomeFixture);v.source_instance="observer";v.revision="revision";
  const screen=v.screens[0],main=screen.windows.find((w:any)=>w.type===1&&w.package===launcher),root=screen.nodes.find((n:any)=>n.window_id===main.id&&n.parent===null);
  const raw=(id:string,parent:string,resource:string,className:string,b:any,scrollable=false)=>({...root,id,parent,resource_id:launcher+":id/"+resource,class:className,bounds:b,clipped_bounds:b,scrollable,actionable_candidate:false});
  screen.nodes.push(raw("1001",root.id,"home_view_pager","androidx.viewpager.widget.ViewPager",{x:145,y:0,width:1775,height:720},true),
   raw("1002","1001","frameLayout_card","android.widget.FrameLayout",{x:558,y:101,width:1362,height:538}));
  let dispatched=0,shown=false;
  const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/ui",async()=>{throw Error("CLI fallback");},async()=>{},async()=>{
   const current=structuredClone(v);
   if(shown)current.screens[0].nodes.push(raw("1003",root.id,"all_app_view","androidx.recyclerview.widget.RecyclerView",{x:145,y:0,width:1775,height:720},true));
   return nativeTree(current,{sessionId:v.session_id,pid:v.android_pid,bootId:v.boot_id,profile:v.profile});
  },async request=>{
   const pointer=nativePointerRequest(request,v,{sessionId:v.session_id,pid:v.android_pid,boot:v.boot_id});
   assert.equal(pointer.op,"input.gesture");assert.equal((pointer.fields as any).expected.target.resource_id,launcher+":id/home_view_pager");
   assert.equal((pointer.fields as any).expected.window.id,main.id);dispatched++;return {};
  });
  await openLauncherAppsFromHome(ui,true);assert.equal(dispatched,1);
  const absent=await ui.snapshot();assert.throws(()=>launcherPointerNode(absent,n=>n.resource.endsWith("all_app_view")),/absent/);
  shown=true;
  assert.equal(launcherPointerNode(await ui.snapshot(),n=>n.resource.endsWith("all_app_view")).windowRef?.inputFocused,false);
  const stale=await ui.snapshot();assert.throws(()=>launcherHomeGesture(stale,true),/ambiguous/);
 } finally {rmSync(directory,{recursive:true,force:true});}
});

test("native image continuity accepts observed inactive main but rejects focus or geometry drift",()=>{
 const base=unfocusedHome(),main=launcherPrimaryPointerWindow(base);
 const grid={...base.find(n=>n.windowRef===main)!,resource:launcher+":id/all_app_view",bounds:[145,0,1920,720]};
 const marker={...grid,resource:launcher+":id/home_page_indicator",bounds:[800,650,1000,700]};
 const nodes:UiNodes=[...base,grid,marker];Object.defineProperty(nodes,"windowRefs",{value:base.windowRefs});
 const capture={},refs=base.windowRefs!.map(w=>({...w,capture,captureStart:main.captureEnd+1,captureEnd:main.captureEnd+2}));
 const afterNodes:UiNodes=nodes.map(n=>({...n,windowRef:refs.find(w=>w.id===n.windowRef!.id)}));Object.defineProperty(afterNodes,"windowRefs",{value:refs});
 const before={nodes,grid},after={nodes:afterNodes,grid:afterNodes.at(-2)!},boot={boot:"same",bootCompleted:true};
 launcherCaptureContinuity(before,after,marker,afterNodes.at(-1)!,boot,boot,1000,true);
 assert.throws(()=>launcherCaptureContinuity(before,after,marker,afterNodes.at(-1)!,boot,boot,1000),/owner/);
 const changed=changedWindow(afterNodes,main.id,{active:true,inputFocused:true});
 assert.throws(()=>launcherCaptureContinuity(before,{nodes:changed,grid:changed.at(-2)!},marker,changed.at(-1)!,boot,boot,1000,true),/semantic/);
});

test("primary recovery requires exact secondary focus and complete same-boot proof",async()=>{
 const good=await secondaryProof();knownSecondaryLauncherFocus(good,"boot");
 for(const bad of [{...good,boot:"other"},{...good,bootCompleted:false},{...good,body:good.body.replace("AllAppActivity","UnknownActivity")},{...good,body:good.body.replace("android-boot-errors:","  mCurrentFocus=null\nandroid-boot-errors:")},{...good,body:""}]) assert.throws(()=>knownSecondaryLauncherFocus(bad,"boot"),/known secondary/);
 let taps=0;await assert.rejects(ensurePrimaryLauncherFocus({snapshot:async()=>focusHome(),call:async()=>{taps++;return {};}},async()=>"boot",()=>"observer",5000,()=>0),/proof required/);assert.equal(taps,0);
});

import {observePrimaryLauncher} from "../../release/emulator-gate.ts";
test("native primary observation accepts visible launcher when global focus remains secondary without input",async()=>{
 for(const nodes of [focusHome(),focusedHome()]) {
  let reads=0,boots=0;
  await observePrimaryLauncher({snapshot:async()=>{reads++;return nodes;}},async()=>{boots++;return "boot";},()=>"observer",5000,()=>0);
  assert.equal(reads,1);assert.equal(boots,2);
 }
});
test("primary observation preserves foreign window, complete scope and exact OEM main fences",async()=>{
 const base=focusHome(),main=base.windowRefs!.find(w=>w.title==="module-widget")!,foreign=base.windowRefs!.find(w=>w.rootPackage==="com.qinggan.ivoka")!;
 for(const nodes of [
  changedWindow(base,foreign.id,{active:true}),changedWindow(base,foreign.id,{inputFocused:true}),
  changedWindow(base,main.id,{title:"Other"}),changedWindow(base,main.id,{rootStatus:"unavailable"}),
  changedWindow(base,main.id,{scope:"other-display"}),changedWindow(base,main.id,{bounds:[0,0,1919,720]}),
  changedWindow(base,foreign.id,{rootPackage:launcher}),
 ]) await assert.rejects(observePrimaryLauncher({snapshot:async()=>nodes},async()=>"boot",()=>"observer",5000,()=>0));
});
test("primary observation rejects boot, observer and deadline drift without dispatch",async()=>{
 for(const drift of ["boot","observer","deadline"]) {
  let boots=0,sources=0,time=0;
  await assert.rejects(observePrimaryLauncher({snapshot:async()=>focusHome()},async()=>{boots++;if(boots===2&&drift==="deadline")time=5000;return boots===2&&drift==="boot"?"other":"boot";},()=>++sources===2&&drift==="observer"?"other":"observer",5000,()=>time),/crossed|deadline/);
 }
 let snapshots=0;
 await assert.rejects(observePrimaryLauncher({snapshot:async()=>{snapshots++;return focusHome();}},async()=>"boot",()=>"observer",5000,()=>5000),/deadline/);
 assert.equal(snapshots,0);
 await assert.rejects(observePrimaryLauncher({snapshot:async()=>focusHome()},async()=>"boot",()=>"",5000,()=>0),/identity absent/);
});
