import test from "node:test";
import assert from "node:assert/strict";
import { startupPreparationEvidence } from "./startup-preparation.ts";
import type { LogRecord } from "./installer-logs.ts";
const hash="a".repeat(64), boot="00000000-0000-0000-0000-000000000001";
const identity={bootId:boot,daemonPid:123,daemonStarttime:"9007199254740993",manifestSha256:hash,generationId:hash,catalogSha256:hash};
const target={process:"backend",pid:234,starttime:"9007199254740994",readyMs:115,batch:1,priority:0,dispatchMs:115,dispatchRank:0,endpointEpoch:"3",adoptedMs:120};
function row(fields:Record<string,unknown>={}):LogRecord {
 return {schemaVersion:1,sequence:1,timestamp:"2026-10-16T00:00:00Z",source:{kind:"daemon"},level:"info",component:"startup",message:"startup.preparation",fields:{
 schemaVersion:"1",clockDomain:"daemon.monotonicSinceOwnerStart",...Object.fromEntries(Object.entries(identity).map(([k,v])=>[k,String(v)])),
 process:"backend",targetPid:"234",targetStarttime:target.starttime,attempt:"1",
 observedScanBeginMs:"10",observedScanEndMs:"12",preflightBeginMs:"15",preflightEndMs:"115",
 outcome:"dispatched",stage:"dispatch",dispatchMs:"115",dispatchAcceptedMs:"116",...fields}};
}
const run=(rows:LogRecord[])=>startupPreparationEvidence(rows,identity,[target]);
test("slow preflight remains visible when legacy ready-to-dispatch is zero",()=>{
 assert.equal(target.dispatchMs-target.readyMs,0);
 const result=run([row()]);assert.equal(result.status,"passed");
 if(result.status==="passed") {
  assert.equal(result.processBirthLatency,"not measured");
  assert.equal(result.intervals[0].preflightMs,100);
  assert.equal(result.intervals[0].observedToDispatchMs,103);
  assert.equal(result.intervals[0].observationUncertaintyMs,2);
 }
});
test("missing legacy logs and a rebind alone are unavailable, never zero",()=>{
 assert.equal(run([]).status,"unavailable");
 assert.equal(run([row({outcome:"rebind",stage:"rebind"})]).status,"unavailable");
});
test("failed preparation cannot masquerade as accepted dispatch",()=>{
 const r=row({outcome:"failed",stage:"payload"});delete r.fields.dispatchMs;delete r.fields.dispatchAcceptedMs;
 assert.equal(run([r]).status,"unavailable");
 assert.throws(()=>run([row({outcome:"failed",stage:"payload"})]),/closed fields/);
});
test("cross-boot daemon and installed generations do not provide current evidence",()=>{
 for(const [k,v] of [["bootId",boot.replace(/1$/,"2")],["daemonPid","124"],["daemonStarttime","7"],["manifestSha256","b".repeat(64)],["generationId","b".repeat(64)],["catalogSha256","b".repeat(64)]]) assert.equal(run([row({[k]:v})]).status,"unavailable");
});
test("PID reuse does not inherit earlier process lifetime timing",()=>{
 assert.equal(run([row({targetStarttime:"42"})]).status,"unavailable");
 assert.equal(run([row({targetPid:"235"})]).status,"unavailable");
});
test("unsafe and out-of-order clocks, unknown fields and versions fail closed",()=>{
 for(const changes of [{preflightEndMs:"14"},{observedScanBeginMs:"13"},{observedScanEndMs:"16"},{dispatchMs:"114"},{dispatchAcceptedMs:"121"},{preflightBeginMs:"01"},{preflightBeginMs:String(Number.MAX_SAFE_INTEGER+1)},{schemaVersion:"2"},{extra:"value"},{attempt:"0"},{targetStarttime:"01"},{stage:"invented"},{clockDomain:"android.elapsedRealtime"}]) assert.throws(()=>run([row(changes)]));
});
test("duplicate attempts and changed first observation fail closed",()=>{
 assert.throws(()=>run([row(),row()]),/duplicate attempt/);
 assert.throws(()=>run([row(),row({attempt:"2",observedScanBeginMs:"11"})]),/first observation/);
});
test("retry does not overwrite the first accepted attempt",()=>{
 const r=run([row({attempt:"2",preflightBeginMs:"20",dispatchMs:"117",dispatchAcceptedMs:"118"}),row()]);
 assert.equal(r.status,"passed");if(r.status==="passed") assert.equal(r.intervals[0].attempt,1);
});
test("agent text and other component cannot impersonate daemon preparation",()=>{
 const a=row();a.source={kind:"agent",id:"backend"};assert.equal(run([a]).status,"unavailable");
 const b=row();b.component="agent";assert.equal(run([b]).status,"unavailable");
});
test("each requested process needs its own qualified dispatch",()=>{
 const other={...target,process:"settings",pid:235};
 assert.equal(startupPreparationEvidence([row()],identity,[target,other]).status,"unavailable");
 const r=startupPreparationEvidence([row(),row({process:"settings",targetPid:"235"})],identity,[target,other]);
 assert.equal(r.status,"passed");
 assert.throws(()=>startupPreparationEvidence([],identity,[target,target]),/duplicate target/);
});

test("failed first attempt cannot reset discovery on a later dispatch",()=>{
 const failed=row({outcome:"failed",stage:"payload"});delete failed.fields.dispatchMs;delete failed.fields.dispatchAcceptedMs;
 assert.throws(()=>run([failed,row({attempt:"2",observedScanBeginMs:"11"})]),/first observation/);
});

test("missing first event cannot be repaired by substituting a later successful retry",()=>{
 assert.equal(run([row({attempt:"2",dispatchMs:"117",dispatchAcceptedMs:"118"})]).status,"unavailable");
});

test("production native log record roundtrips through strict preparation reader",async()=>{
 const {readFileSync}=await import("node:fs");
 const r=JSON.parse(readFileSync(new URL("../tests/fixtures/startup-preparation-native.json",import.meta.url),"utf8"));
 const nativeIdentity={bootId:"11111111-1111-4111-8111-111111111111",daemonPid:42,daemonStarttime:"100",manifestSha256:"a".repeat(64),generationId:"b".repeat(64),catalogSha256:"c".repeat(64)};
 const nativeTarget={...target,process:"target",pid:50,starttime:"200"};
 const result=startupPreparationEvidence([r],nativeIdentity,[nativeTarget]);
 assert.equal(result.status,"passed");
 if(result.status==="passed") {
  assert.equal(result.intervals[0].preflightMs,100);
  assert.equal(result.intervals[0].dispatchMs,115);
  assert.equal(result.intervals[0].dispatchAcceptedMs,116);
  assert.equal(result.intervals[0].observedToDispatchMs,103);
 }
});
