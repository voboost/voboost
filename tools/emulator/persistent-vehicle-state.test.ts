import assert from "node:assert/strict";
import test from "node:test";
import { persistentVehicleArmed, vehicleArmed, residentStatusShape } from "./functional-observations.ts";
const authority = { boot: "11111111-1111-1111-1111-111111111111", generationId: "11".repeat(32), catalogSha256: "22".repeat(32) };
const fixture = () => ({ schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: "33".repeat(32), startupMode: "interface", killed: false, injections: [], executors: [], state: "ready", panic: false, generationId: authority.generationId, catalogSha256: authority.catalogSha256, persistentVehicleState: { bootId: authority.boot, observedAtBootMs: 99000, forcedEv: { outcome: "value", value: true }, pedestrianWarning: { outcome: "value", value: false } } });
const body = (value: any) => `vehicle-agent-state:\n/data/voboost/state/vehicle-drive-forced-ev_forced-ev-armed.json=unavailable\n/data/voboost/state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed.json=unavailable\npersistent-vehicle-state-snapshot:\nsource=daemon-secure-root-store-read; freshness=compare-boot-id-and-observedAtBootMs; unavailable-is-not-false\ncurrent-boot-id=${authority.boot}\ncurrent-boot-uptime=100.00 50.00\n${JSON.stringify(value)}\nblock-protection:\n`;
test("secure persistent projection survives unavailable private shell probes", () => {
 assert.deepEqual(vehicleArmed(body(fixture()), authority), { fev: { status: "present", value: true }, pw: { status: "present", value: false } });
 assert.throws(() => vehicleArmed(body(fixture())), /independent authority/);
});
test("persistent projection rejects stale, future, foreign boot and generation", () => {
 for (const mutate of [(v: any) => v.persistentVehicleState.observedAtBootMs = 94999, (v: any) => v.persistentVehicleState.observedAtBootMs = 100001, (v: any) => v.persistentVehicleState.bootId = "22222222-2222-2222-2222-222222222222", (v: any) => v.generationId = "33".repeat(32), (v: any) => v.catalogSha256 = "44".repeat(32)]) { const value = fixture(); mutate(value); assert.throws(() => persistentVehicleArmed(body(value), authority)); }
});
test("persistent projection preserves triads and rejects corrupt boolean shapes", () => {
 const value: any = fixture(); value.persistentVehicleState.forcedEv = { outcome: "missing" }; value.persistentVehicleState.pedestrianWarning = { outcome: "unavailable" };
 assert.deepEqual(persistentVehicleArmed(body(value), authority), { fev: { status: "absent" }, pw: { status: "unavailable" } });
 for (const invalid of [{ outcome: "value", value: "false" }, { outcome: "missing", value: false }, { outcome: "value" }, { outcome: "unavailable", extra: 1 }]) { value.persistentVehicleState.forcedEv = invalid; assert.throws(() => persistentVehicleArmed(body(value), authority), /triad|shape/); }
 assert.throws(() => persistentVehicleArmed(body(fixture()) + body(fixture()), authority), /duplicated/);
});

test("post-install closed status admits typed optional diagnostics and rejects unknown fields", () => {
 const value: any = fixture(); residentStatusShape(value);
 value.lastUnsafeNativeFailure = { process: "com.qinggan.app.setting", targetPid: 123, targetStarttime: 1234, generationId: authority.generationId, catalogSha256: authority.catalogSha256, documentGeneration: 17, diagnostic: { result: "unsafe-cleanup", stage: "restore", restore_part: "pstate", operation: "set", reason: "mismatch", pstate_expected: null, pstate_observed: null, pstate_xor: null, first_mismatch: null } };
 residentStatusShape(value);
 value.unknown = true; assert.throws(() => residentStatusShape(value), /closed shape/); delete value.unknown;
 value.lastUnsafeNativeFailure.diagnostic.unknown = true; assert.throws(() => residentStatusShape(value), /closed shape/); delete value.lastUnsafeNativeFailure.diagnostic.unknown;
 value.lastUnsafeNativeFailure.targetPid = "123"; assert.throws(() => residentStatusShape(value), /typed shape/);
});
test("diagnostic upper bound admits atomic replacement after the former lower bound", () => {
 const value = fixture(); value.persistentVehicleState.observedAtBootMs = 100050;
 const current = body(value).replace("current-boot-uptime=100.00", "current-boot-uptime=100.10");
 assert.equal(persistentVehicleArmed(current, authority).fev.status, "present");
 assert.throws(() => persistentVehicleArmed(body(value), authority), /future/);
});


test("centisecond producer stamps avoid false future and decimal rounding", () => {
 for (const [observed, current] of [[123450, "123.45"], [129260, "129.26"]] as const) {
  const value = fixture(); value.persistentVehicleState.observedAtBootMs = observed;
  assert.equal(persistentVehicleArmed(body(value).replace("100.00 50.00", `${current} 50.00`), authority).fev.status, "present");
 }
});
test("proc precision keeps a conservative five-second bound and rejects future", () => {
 for (const [observed, accepted] of [[95010, true], [95000, false], [100010, false]] as const) {
  const value = fixture(); value.persistentVehicleState.observedAtBootMs = observed;
  if (accepted) assert.equal(persistentVehicleArmed(body(value), authority).fev.status, "present");
  else assert.throws(() => persistentVehicleArmed(body(value), authority), /stale or from the future/);
 }
 for (const uptime of ["129.2", "129.260", "129", "9007199254741.00"]) {
  assert.throws(() => persistentVehicleArmed(body(fixture()).replace("100.00 50.00", `${uptime} 50.00`), authority));
 }
});
