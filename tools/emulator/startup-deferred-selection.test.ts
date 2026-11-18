import test from "node:test";
import assert from "node:assert/strict";
import {startupDeferredSelection} from "./startup-deferred-selection.ts";
const boot="11111111-1111-1111-1111-111111111111",hash="a".repeat(64);
const expected={serial:"emulator-5566",releaseIdentity:"beta1",manifestSha256:hash,want:"none"};
const resident=()=>({schemaVersion:1,releaseIdentity:"beta1",manifestSha256:hash,generationId:hash,catalogSha256:hash,startupMode:"interface",state:"ready",killed:false,panic:false,injections:[{id:"settings",process:"com.qinggan.app.setting",state:"active",lifecycleGeneration:"17"}]});
function observation(r=resident(), pid="23",start="345"){return{boot,body:`\nresident-status:\n/data/voboost/status.json\n${JSON.stringify(r)}\n\ncurrent-process-identities:\nschemaVersion=1\nbootId=${boot}\nenforcing=Enforcing\nprocess=voboost-daemon pid=21 starttime=343 argv0=/data/voboost/voboost-daemon\nprocess=voboost-inject pid=${pid} starttime=${start} argv0=/data/voboost/voboost-inject\nprocess=com.qinggan.app.setting pid=42 starttime=456 argv0=com.qinggan.app.setting\nbootIdAfter=${boot}\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n`};}
const saved=()=>({deviceSerial:expected.serial,installedVersion:"beta1",contentRelation:"same",daemonStatus:"running",activeGeneration:hash,startupMode:"none"});
test("all next-boot choices preserve effective interface and live incarnation",()=>{for(const want of ["none","hidden","interface"])startupDeferredSelection(observation(),observation(),{...saved(),startupMode:want},{...expected,want});});
test("reject immediate effective none, daemon stop/restart/PID reuse, injection removal and root identity drift",()=>{
 const bad=resident();bad.startupMode="none";assert.throws(()=>startupDeferredSelection(observation(),observation(bad),saved(),expected));
 for(const current of [observation(resident(),"24"),observation(resident(),"23","346"),{...observation(),boot:"other"}]) assert.throws(()=>startupDeferredSelection(observation(),current,saved(),expected));
 const removed=resident();removed.injections=[];assert.throws(()=>startupDeferredSelection(observation(),observation(removed),saved(),expected));
 for(const drift of [{daemonStatus:"stopped"},{deviceSerial:"foreign"},{activeGeneration:"b".repeat(64)}])assert.throws(()=>startupDeferredSelection(observation(),observation(),{...saved(),...drift},expected));
});
test("reject missing independent process section and effective hidden mutation",()=>{assert.throws(()=>startupDeferredSelection(observation(),{...observation(),body:observation().body.split("current-process-identities:")[0]},saved(),expected));const r=resident();r.startupMode="hidden";assert.throws(()=>startupDeferredSelection(observation(),observation(r),saved(),expected));});
