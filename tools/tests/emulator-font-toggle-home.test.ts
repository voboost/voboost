import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {launcherToggleReturnedHome, settingsRestartReturnedDefaultTab, recoverToggleHome, type ToggleRestartProof} from "../../release/emulator-gate.ts";
import {parseUi} from "../emulator/installer-ui.ts";
const xml=readFileSync(new URL("../../testdata/emulator/cycle124-case18-home.xml",import.meta.url),"utf8"), home=parseUi(xml);
const settings="com.qinggan.app.setting", launcher="com.qinggan.app.launcher", id="font-cyrillic";
const resident=(revision:string, selected:boolean)=>({schemaVersion:1,releaseIdentity:"1.0.0-beta1",manifestSha256:"a".repeat(64),generationId:"b".repeat(64),catalogSha256:"c".repeat(64),startupMode:"interface",state:"ready",killed:false,panic:false,injections:[{id:"settings",process:settings,state:"active",documentGeneration:revision},...(selected?[{id,process:settings,state:"active"},{id,process:launcher,state:"active"}]:[])]});
const proof=():ToggleRestartProof=>({mode:"restart",targets:[settings,launcher],beforeBoot:"boot",afterBoot:"boot",beforePids:{[settings]:"12",[launcher]:"13"},afterPids:{[settings]:"14",[launcher]:"15"},beforeResident:resident("1",true),currentResident:resident("2",false)});
test("nonlauncher Home needs explicit source-admitted restart proof",()=>assert.equal(launcherToggleReturnedHome(id,home),false));
test("font confirmed sameboot signed host restart admits Home recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,proof()),true));
test("same OEM host PID cannot admit recovery",()=>{const p=proof();p.afterPids[settings]=p.beforePids[settings];assert.equal(launcherToggleReturnedHome(id,home,p),false);});
test("missing replacement host cannot admit recovery",()=>{const p=proof();delete p.afterPids[launcher];assert.equal(launcherToggleReturnedHome(id,home,p),false);});
test("different boot cannot admit recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,{...proof(),afterBoot:"other"}),false));
test("in-place stop cannot admit recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,{...proof(),mode:"in-place"}),false));
test("unsigned host target cannot admit recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,{...proof(),targets:["foreign.app"]}),false));
test("no newer Settings ACK cannot admit recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,{...proof(),currentResident:resident("1",false)}),false));
test("agent still selected cannot admit recovery",()=>assert.equal(launcherToggleReturnedHome(id,home,{...proof(),currentResident:resident("2",true)}),false));
test("inactive prior injection cannot admit recovery",()=>{const p=proof();p.beforeResident.injections=p.beforeResident.injections.map((r:any)=>r.id===id?{...r,state:"waiting"}:r);assert.equal(launcherToggleReturnedHome(id,home,p),false);});
test("foreign foreground remains forbidden with valid proof",()=>assert.equal(launcherToggleReturnedHome(id,parseUi(xml.replaceAll("com.qinggan.app.launcher","foreign.app")),proof()),false));
test("default launcher behavior remains compatible",()=>assert.equal(launcherToggleReturnedHome("launcher-apps",home),true));

// Minimal OEM default-tab fixture; unrelated checkboxes are not recovery authority.
const defaultTab = () => {
 const nodes = parseUi(xml);
 const window = nodes.find(n => n.windowRef?.type === 1 && n.windowRef.inputFocused)!.windowRef!;
 const settingsWindow={...window,rootPackage:settings,title:"Settings"};
 const root=nodes.find(n=>n.windowRef===window)!;
 const tab=(text:string,checked:boolean)=>({...root,windowRef:settingsWindow,packageName:settings,text,checked,checkable:true,clickable:true});
 return [tab("Voboost",false),tab("Network",true)];
};
test("confirmed font host replacement recovers OEM default Settings tab once",async()=>{
 const nodes=defaultTab(), after=defaultTab(); after[0].checked=true; after[1].checked=false;
 assert.equal(settingsRestartReturnedDefaultTab(id,nodes,proof()),true);
 let opens=0, guards=0;
 assert.equal(await recoverToggleHome(id,nodes,proof(),async()=>after,async()=>{guards++;},async()=>{opens++;},()=>0,15000),after);
 assert.equal(opens,1); assert.equal(guards,1);
});
test("default Settings recovery rejects missing or inconsistent restart authority",async()=>{
 const invalid=[undefined, {...proof(),mode:"in-place" as const}, {...proof(),afterBoot:"other"}, {...proof(),targets:[launcher]}, {...proof(),afterPids:{[settings]:"12",[launcher]:"15"}}, {...proof(),currentResident:resident("1",false)}, {...proof(),currentResident:resident("2",true)}];
 const inactive=proof(); inactive.beforeResident.injections=inactive.beforeResident.injections.filter((r:any)=>r.id!==id); invalid.push(inactive);
 for(const p of invalid) {
  const nodes=defaultTab();let opens=0;
  assert.equal(settingsRestartReturnedDefaultTab(id,nodes,p),false);
  assert.equal(await recoverToggleHome(id,nodes,p,async()=>nodes,async()=>{},async()=>{opens++;},()=>0,15000),nodes);
  assert.equal(opens,0);
 }
});
test("default Settings recovery never hides other selected tabs or foreground drift",()=>{
 for(const change of [(n:any[])=>{n[1].text="Display";},(n:any[])=>{n[0].checked=true;},(n:any[])=>{n.shift();},(n:any[])=>{n[0].packageName="foreign.app";},(n:any[])=>{n[0].windowRef.title="Other";}]) {
  const nodes=defaultTab();change(nodes);assert.equal(settingsRestartReturnedDefaultTab(id,nodes,proof()),false);
 }
});
