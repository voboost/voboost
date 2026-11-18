import assert from "node:assert/strict";
import { test } from "node:test";
import { vehicleTiming, vehicleTimingSemantics } from "./vehicle-timing.ts";
const boot = "11111111-1111-1111-1111-111111111111";
const fence = { boot, baselineBoot: boot, process: "com.qinggan.systemservice", targetPid: "123", targetStarttime: "200", endpointEpoch: "3", generationId: "11".repeat(32), catalogSha256: "22".repeat(32) };
const identity = Object.fromEntries(Object.entries(fence).filter(([k]) => !["boot", "baselineBoot"].includes(k)));
function record(sequence: number, fields: any, message = "", agent = "vehicle-drive-forced-ev"): any {
  return { schemaVersion: 1, sequence, timestamp: "2026-10-13T00:00:00Z", source: { kind: "agent", id: agent }, level: "info", component: "agent", message, fields };
}
function anchor(vehicle = "fev"): any {
  return { ...record(1, { ...identity, bootId: boot, agentId: vehicle === "fev" ? "vehicle-drive-forced-ev" : "vehicle-drive-pedestrian-warning", key: vehicle === "fev" ? "forced-ev-armed" : "pedestrian-warning-armed", operation: "read", outcome: "value", value: "true", requestId: "1" }, "vehicle-state-observed"), source: { kind: "daemon" }, component: "agent-state" };
}
function row(sequence: number, semantic: string, matched: boolean, baseline: boolean, time: number): any {
  return { record: record(sequence, identity), agent: "vehicle-drive-forced-ev", event: "vehicle.state", outcome: matched ? "MATCH" : "NO_MATCH", fields: { semantic, readStatus: "returned", stateMatched: matched, baseline, clockDomain: "android.elapsedRealtime", observedAtBootMs: time } };
}
const complete = (sequence = 5): any => ({ ...row(sequence, "forced-ev", true, false, 1200), event: "restore", outcome: "verified" });
const fixture = () => [row(2,"forced-ev",false,true,1000),row(3,"forced-ev",true,false,1100),complete()];
test("actual read observation measures boot time and does not invent process latency or causality", () => {
  const got = vehicleTiming([anchor()], fixture(), "fev", fence, ["forced-ev"]);
  assert.equal(got.bootToMatchedObservationMs,1100); assert.equal(got.classification,"observed-nonmatch-to-match");
  assert.equal(got.processStartToMatchMs,null); assert.equal(got.settingsOrder,"unverified"); assert.equal(got.causality,"unverified");
});
test("baseline match is separate from verified restore and Dreamer requires both semantics", () => {
  const rows = [row(2,"forced-ev",true,true,1000),complete()];
  assert.equal(vehicleTiming([anchor()],rows,"fev",fence,["forced-ev"]).classification,"already-matched-baseline");
  assert.throws(()=>vehicleTiming([anchor()],rows,"fev",fence,["forced-ev","drive-electric"]));
  rows.splice(1,0,row(3,"drive-electric",true,true,1150));
  assert.equal(vehicleTiming([anchor()],rows,"fev",fence,["forced-ev","drive-electric"]).bootToMatchedObservationMs,1150);
  assert.throws(()=>vehicleTiming([anchor()],rows.slice(0,2),"fev",fence,["forced-ev","drive-electric"]));
});
test("missing, negative, wall clock and regressing clocks reject timing", () => {
  for (const fields of [{ observedAtBootMs: undefined },{ observedAtBootMs: -1 },{clockDomain:"unix"},{observedAtBootMs:999}]) {
    const rows=fixture(); Object.assign(rows[1].fields,fields); assert.throws(()=>vehicleTiming([anchor()],rows,"fev",fence,["forced-ev"]));
  }
});
test("wrong boot, reused PID lifetime, epoch and generation cannot provide evidence", () => {
  for (const fields of [{bootId:"22222222-2222-2222-2222-222222222222"},{targetStarttime:"201"},{endpointEpoch:"4"},{generationId:"33".repeat(32)}]) {
    const a=anchor(); Object.assign(a.fields,fields); assert.throws(()=>vehicleTiming([a],fixture(),"fev",fence,["forced-ev"]));
  }
  for (const key of ["targetStarttime","endpointEpoch","generationId","catalogSha256","targetPid","process"]) {
    const rows=fixture(); rows.forEach(r=>r.record.fields={...identity,[key]:"foreign"}); assert.throws(()=>vehicleTiming([anchor()],rows,"fev",fence,["forced-ev"]));
  }
});
test("PW baseline alone does not satisfy accepted completion and warm sequence cutoff excludes old success", () => {
  const rows=[row(2,"disabled",true,true,1000),complete()];
  rows.forEach(r=>{ r.agent="vehicle-drive-pedestrian-warning"; r.record.source.id=r.agent; });
  assert.throws(()=>vehicleTiming([anchor("pw")],rows,"pw",fence,["disabled"]));
  rows[1].event="restore.complete";rows[1].outcome="confirmed";
  assert.equal(vehicleTiming([anchor("pw")],rows,"pw",fence,["disabled"]).classification,"already-matched-baseline");
  assert.throws(()=>vehicleTiming([anchor("pw")],rows,"pw",fence,["disabled"],5));
});
test("Settings comparison requires separately boot-fenced same-clock observation", () => {
  const run=(settings:any)=>vehicleTiming([anchor()],fixture(),"fev",fence,["forced-ev"],-1,settings);
  assert.equal(run({bootId:boot,clockDomain:"android.elapsedRealtime",observedAtBootMs:1500}).settingsOrder,"vehicle-observed-before-settings");
  assert.equal(run({bootId:boot,clockDomain:"unix",observedAtBootMs:1500}).settingsOrder,"unverified");
});

test("actual runtime contract selects Free versus Dreamer reads without profile guessing", () => {
  const rows=fixture(); rows.forEach(r=>r.fields.contract="qinggan-canbus");
  assert.deepEqual(vehicleTimingSemantics(rows,"fev",fence),["forced-ev"]);
  rows.forEach(r=>r.fields.contract="dreamer-h56-force-v2");
  assert.deepEqual(vehicleTimingSemantics(rows,"fev",fence),["forced-ev","drive-electric"]);
  rows[0].fields.contract="qinggan-canbus";
  assert.throws(()=>vehicleTimingSemantics(rows,"fev",fence));
});
test("later nonmatch invalidates earlier match before owner verification", () => {
  const rows=[row(2,"forced-ev",true,true,100),row(3,"forced-ev",false,false,200),row(4,"forced-ev",true,false,1100),complete()];
  assert.equal(vehicleTiming([anchor()],rows,"fev",fence,["forced-ev"]).bootToMatchedObservationMs,1100);
});
