import {nativeReadTransportTimeout} from "../emulator/native-session.ts";
import {stripTypeScriptTypes} from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {captureFirstBoot, UiWarmupDeadline, nativeBootReady, bootPrimaryHomeProof} from "../../release/emulator-gate.ts";
const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
const start=source.indexOf("  const bootCheck = async");
const end=source.indexOf("    return row;",start)+"    return row;\n  };".length;
const body=stripTypeScriptTypes(source.slice(start,end)).replace("kind: string, before: string | null, exactAfter?: string","kind, before, exactAfter").replace("const row: Boot", "const row").replace(" as Error", "").replace("attempt: number", "attempt");
function fixture({verifyMs=130000, timedOut=false, changedBoot=false, firstCaptureMs=0, foreignOnRetry=false, nativeMode=false, nativeError=false, deferred=false}={}) {
 let now=0, screenshots=0, observations=0, verifyFinished=0, snapshots=0, nativeWaits=0, legacyVerifies=0;
 const events=[];const transitions=[];const evidence={boots:[],actions:[]};
 class Unavailable extends Error {}
 const scope={bootPrimaryHomeProof,nativeReadTransportTimeout,bootTransitions:{arm:(oldBoot,next)=>transitions.push({oldBoot,next,observations,nativeWaits})},runtime:{sessionId:"a".repeat(32),pid:123},deferred,native:nativeMode?{waitAndroid:async()=>{nativeWaits++;now+=verifyMs;evidence.actions.push({id:"native-wait"});if(nativeError)throw new Unavailable("native readiness deadline expired");}}:null,evidence,caseNumber:0,performance:{now:()=>now},bootTiming:(...v)=>events.push(v),voyemuInvocation:()=>({timeoutMs:600000}),installerBootObservation:async()=>{now+=1000;return {boot:"new",bootCompleted:true,actionId:"initial",finishedMonotonicNs:"1",rebootCount:1}},bootObservation:async()=>{observations++;now+=1000;return {boot:(changedBoot&&observations>2)||(foreignOnRetry&&screenshots===1&&snapshots===1)?"foreign":"new",bootCompleted:true,actionId:observations,finishedMonotonicNs:"1",rebootCount:1}},Unavailable,monitorLive:()=>{},ownership:async()=>{now+=500},record:async()=>{legacyVerifies++;now+=verifyMs;verifyFinished=now;return {timedOut}},verifyVoyemu:()=>{},secondScreenStarting:()=>false,c:{voyemuProfile:"profile"},machine:{checks:[]},ui:{call:async()=>{now+=1000},snapshot:async()=>{snapshots++;now+=firstCaptureMs||35000;if(firstCaptureMs&&snapshots===1)throw new UiWarmupDeadline("firstUiWarmupDeadline");return []}},screenshot:async()=>{if(deferred)return null;screenshots++;now+=firstCaptureMs?17276:1000;return "png"},captureFirstBoot,persist:()=>{}};
 Object.assign(scope,{nativeBootObservation:scope.bootObservation,nativeBootReady:async(read,before,after,ownership,pause,deadline)=>{
  try {return await nativeBootReady(read,before,after,ownership,pause,deadline,()=>now);}
  catch(error) {if(error instanceof Error && error.constructor.name==="Unavailable") throw new Unavailable(error.message);throw error;}
 }});
 const run=Function(...Object.keys(scope),"let firstBootCaptureDeadline=null,bootBudgetPhase=null,installerRebootCapture=false,monitorResult=null;"+body+"return bootCheck;")(...Object.values(scope));
 return {run,evidence,events,transitions,stats:()=>({now,screenshots,observations,verifyFinished,nativeWaits,legacyVerifies})};
}
test("130 second real readiness leaves a fresh independent UI budget",async()=>{const f=fixture();const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"passed");assert.equal(row.verifiedBoot,"new");assert.equal(f.stats().screenshots,1);const event=f.events.find(e=>e[0]==="ui-capture-budget-start");assert.equal(event[1].budgetMs,180000);assert.ok(f.stats().now>165000);});
test("readiness timeout is retained without a masking post verify observation or screenshot",async()=>{const f=fixture({verifyMs:600000,timedOut:true});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/verification readiness process timed out/);assert.equal(row.verifiedBoot,null);assert.equal(f.stats().observations,2);assert.equal(f.stats().screenshots,0);});
test("foreign boot after slow verification still rejects before capture",async()=>{const f=fixture({changedBoot:true});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/reboot boundary/);assert.equal(f.stats().screenshots,0);});
test("phase invocations remain capped to finite remaining budget",()=>{const a=source.indexOf("    if (firstBootCaptureDeadline !== null) {");const b=source.indexOf("    const id =",a);const clip=Function("nativeReadTransportTimeout","return function(firstBootCaptureDeadline,bootBudgetPhase,performance,Unavailable,inv,snapshotReadbackDeadline,settingsNavigationDeadline=null){"+source.slice(a,b)+"return inv;}")(nativeReadTransportTimeout);assert.equal(clip(600000,"readiness",{now:()=>130000},Error,{timeoutMs:600000},null).timeoutMs,470000);assert.equal(clip(250000,"capture",{now:()=>200000},Error,{timeoutMs:180000},null).timeoutMs,50000);assert.throws(()=>clip(200000,"capture",{now:()=>200001},Error,{timeoutMs:180000},null),/capture budget exhausted/);assert.equal(clip(null,"capture",{now:()=>200000},Error,{timeoutMs:180000},230000).timeoutMs,30000);assert.equal(clip(250000,"capture",{now:()=>200000},Error,{timeoutMs:180000},230000).timeoutMs,30000);assert.equal(clip(250000,"capture",{now:()=>200000},Error,{timeoutMs:180000},230000,210000).timeoutMs,10000);assert.throws(()=>clip(null,"capture",{now:()=>230001},Error,{timeoutMs:180000},null,230000),/Settings navigation deadline expired/);assert.throws(()=>clip(null,"capture",{now:()=>230001},Error,{timeoutMs:180000},230000),/snapshot readback budget exhausted/);});

test("two observed slow first boot captures fit the independent phase",async()=>{const f=fixture({firstCaptureMs:58721});const row=await f.run("installer-1","old");assert.equal(row.status,"passed",row.reason);assert.equal(f.stats().screenshots,2);assert.equal(row.verifiedBoot,"new");const events=f.events.filter(e=>e[0]==="ui-capture-start");assert.deepEqual(events.map(e=>e[1].attempt),[1,2]);});
test("foreign boot on slow capture retry remains rejected",async()=>{const f=fixture({firstCaptureMs:58721,foreignOnRetry:true});const row=await f.run("installer-1","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/crossed a reboot/);assert.equal(f.stats().screenshots,1);});
test("exhausted phase never permits the warm retry",async()=>{const f=fixture({firstCaptureMs:170000});const row=await f.run("installer-1","old");assert.equal(row.status,"failed");assert.match(row.reason,/firstUiWarmupDeadline/);assert.equal(f.stats().screenshots,1);});

test("native readiness uses native wait and grants capture an independent budget",async()=>{const f=fixture({nativeMode:true,verifyMs:590000});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"passed",row.reason);assert.equal(f.stats().nativeWaits,1);assert.equal(f.stats().legacyVerifies,0);assert.equal(f.stats().screenshots,1);assert.ok(f.stats().now>600000);assert.equal(f.evidence.actions[0].nativeOperation,"session.wait");assert.deepEqual(row.actions,["native-wait"]);assert.equal(f.events.find(e=>e[0]==="ui-capture-budget-start")[1].budgetMs,180000);});
test("native readiness rejects a changed boot before capture",async()=>{const f=fixture({nativeMode:true,changedBoot:true});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/native readiness crossed guest boot/);assert.equal(f.stats().nativeWaits,1);assert.equal(f.stats().legacyVerifies,0);assert.equal(f.stats().screenshots,0);assert.equal(row.verifiedBoot,null);});
test("native readiness failure is retained without legacy verification or capture",async()=>{const f=fixture({nativeMode:true,nativeError:true});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/native readiness deadline expired/);assert.equal(f.stats().nativeWaits,1);assert.equal(f.stats().legacyVerifies,0);assert.equal(f.stats().screenshots,0);assert.equal(row.verifiedBoot,null);});

test("deferred native boot retains semantic readiness without claiming visual acceptance",async()=>{const f=fixture({nativeMode:true,deferred:true,verifyMs:130000});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable",row.reason);assert.equal(row.semanticStatus,"passed");assert.equal(row.verifiedBoot,"new");assert.equal(row.screenshot,null);assert.match(row.reason,/image capture deferred/);assert.equal(f.stats().screenshots,0);assert.equal(f.stats().nativeWaits,1);assert.equal(f.stats().legacyVerifies,0);assert.ok(f.stats().now>165000);assert.equal(f.events.find(e=>e[0]==="ui-capture-budget-start")[1].budgetMs,180000);});
test("deferred mode does not mask native readiness failure",async()=>{const f=fixture({nativeMode:true,deferred:true,nativeError:true});const row=await f.run("preparation-uninstall","old");assert.equal(row.status,"unavailable");assert.match(row.reason,/native readiness deadline expired/);assert.equal(row.semanticStatus,undefined);assert.equal(row.verifiedBoot,null);assert.equal(row.screenshot,null);assert.equal(f.stats().screenshots,0);});


test("known completed native reboot arms only after independent readiness and before capture",async()=>{
 const f=fixture({nativeMode:true});const row=await f.run("preparation-uninstall","old","new");
 assert.equal(row.status,"passed",row.reason);assert.equal(f.transitions.length,1);
 assert.deepEqual(f.transitions[0].next,{sessionId:"a".repeat(32),pid:123,bootId:"new",profile:"profile"});
 assert.equal(f.transitions[0].oldBoot,"old");assert.equal(f.transitions[0].nativeWaits,1);
 assert.equal(f.transitions[0].observations,3);
});
test("unknown prior boot and crossed native reboot never arm tree recovery",async()=>{
 const unknown=fixture({nativeMode:true});const absent=await unknown.run("preparation-uninstall",null);
 assert.equal(absent.status,"unavailable");assert.equal(unknown.transitions.length,0);
 const crossed=fixture({nativeMode:true,changedBoot:true});await crossed.run("preparation-uninstall","old");
 assert.equal(crossed.transitions.length,0);
 const failed=fixture({nativeMode:true,nativeError:true});await failed.run("preparation-uninstall","old");
 assert.equal(failed.transitions.length,0);
});
