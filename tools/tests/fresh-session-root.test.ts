import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {stripTypeScriptTypes} from "node:module";
const candidate=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
function fixture(source,{initial=true,action="open-settings",active=false,writer=false,fault=false,boot=null,ownFailure=false,cliFailure=false}={}) {
 const events=[];const evidence={activeOperation:active?{}:null,actions:[]};
 const scope={native:null,nativeBootReadDeadline:null,initialSettingsNavigation:initial,ownership:async()=>{events.push("ownership");if(ownFailure)throw Error("lease changed");},readFileSync:()=>JSON.stringify({action}),evidence,probe:async()=>{events.push("probe");throw Error("guard-root exit73");},performance:{now:()=>0},record:async()=>{events.push("cli");if(cliFailure)throw Error("CLI failed");evidence.actions.push({id:1});return{exitCode:0,actionId:1};},frozen:{package:"kit"},runtime:{},serial:"emulator-5554",language:"en",installerClosedPending:()=>false,installerRootPending:()=>false,offlineObservation:()=>false};
 const adapter=source.slice(source.indexOf("  const uiAdapter: ProcessAdapter"),source.indexOf("  let ui: InstallerUi;"));
 const run=Function(...Object.keys(scope),`let currentBoot=${JSON.stringify(boot)},snapshotBoot=null,firstBootCaptureDeadline=null,snapshotReadbackDeadline=null,installerActive=${writer},installerRebootCapture=false,installerMonitorStopped=false,faultLive=${fault?'()=>{}':'null'};`+stripTypeScriptTypes(adapter)+"return uiAdapter;")(...Object.values(scope));
 return{events,run:()=>run({args:["--emulator-check","request"],timeoutMs:240000})};
}
test("initial purposeful Settings navigation reaches closed CLI exactly once",async()=>{const f=fixture(candidate);await f.run();assert.deepEqual(f.events,["ownership","cli"]);});
for(const opts of [{active:true},{writer:true},{fault:true},{boot:"boot-a"},{action:"key"},{action:"snapshot"}]) test("initial phase refuses "+JSON.stringify(opts),async()=>{const f=fixture(candidate,opts);await assert.rejects(f.run(),/requires no host writer|active transaction/);assert.deepEqual(f.events,[]);});
test("ownership failure refuses CLI",async()=>{const f=fixture(candidate,{ownFailure:true});await assert.rejects(f.run(),/lease changed/);assert.deepEqual(f.events,["ownership"]);});
test("CLI failure remains terminal without root retries",async()=>{const f=fixture(candidate,{cliFailure:true});await assert.rejects(f.run(),/CLI failed/);assert.deepEqual(f.events,["ownership","cli"]);});
test("normal post-preparation navigation retains mandatory boot probe",async()=>{const f=fixture(candidate,{initial:false});await assert.rejects(f.run(),/guard-root/);assert.deepEqual(f.events,["probe"]);});
test("one initial call precedes both existing admission branches",()=>{const s=candidate.slice(candidate.indexOf("    await fullVerify();",candidate.indexOf("ui = new InstallerUi")),candidate.indexOf("    for (const number of regression ?"));assert.equal((s.match(/initialSettingsNavigation = !native/g)||[]).length,1);assert.match(s,/finally \{ initialSettingsNavigation = false; \}/);assert.ok(s.indexOf('await ui.call({ action: "open-settings" })')<s.indexOf('if (regression)'));assert.match(s,/if \(regression\) \{\n      const before = await bootObservation\(\);/);assert.match(s,/const before = \(await inspect\(true, true\)\).boot;/);});
