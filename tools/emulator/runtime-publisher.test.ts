import assert from "node:assert/strict";
import test from "node:test";
import { residentStatusShape } from "./functional-observations.ts";
const fixture = (): any => ({
  schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: "1".repeat(64),
  generationId: "2".repeat(64), catalogSha256: "3".repeat(64),
  startupMode: "interface", state: "ready", killed: false, panic: false,
  injections: [], executors: [],
});
const publisher = () => ({
  bootId: "11111111-1111-1111-1111-111111111111",
  daemonPid: "42", daemonStarttime: "123", publishedAtBootMs: "9007199254740993",
});
test("legacy status and decimal publisher provenance retain strict wire compatibility", () => {
  residentStatusShape(fixture());
  residentStatusShape({ ...fixture(), publisher: publisher() });
});
test("publisher rejects unknown, missing, malformed and lossy identities", () => {
  for (const mutate of [
    (v: any) => { v.extra = true; },
    (v: any) => { delete v.bootId; },
    (v: any) => { v.bootId = "old"; },
    (v: any) => { v.daemonPid = 42; },
    (v: any) => { v.daemonPid = "042"; },
    (v: any) => { v.daemonPid = "1"; },
    (v: any) => { v.daemonPid = "2147483648"; },
    (v: any) => { v.daemonStarttime = "0"; },
    (v: any) => { v.daemonStarttime = "18446744073709551616"; },
    (v: any) => { v.publishedAtBootMs = 9007199254740992; },
    (v: any) => { v.publishedAtBootMs = "-1"; },
    (v: any) => { v.publishedAtBootMs = "1e3"; },
    (v: any) => { v.publishedAtBootMs = null; },
  ]) {
    const value = publisher(); mutate(value);
    assert.throws(() => residentStatusShape({ ...fixture(), publisher: value }), /publisher|closed shape/);
  }
});

const traceStatus = (): any => ({ ...fixture(), publisher: publisher(), processTrace: {
  identityScope: "direct-child", dropped: "0", overwritten: "12", omitted: 82,
  records: [{ operationId: "48", program: "/system/bin/cmd", operation: "package.list", parentPid: 42,
    childPid: 3696, monotonicNs: "16020593464", elapsedMs: "0", outcome: "started" }],
} });
test("bounded direct-child trace preserves decimal precision and all typed outcomes", () => {
  for (const outcome of ["started", "error", "timeout", { exit: 0 }, { exit: 255 }, { signal: 1 }, { signal: 128 }]) {
    const value = traceStatus();
    Object.assign(value.processTrace.records[0], { operationId: "18446744073709551615", monotonicNs: null,
      elapsedMs: "340282366920938463463374607431768211455", outcome });
    residentStatusShape(value);
  }
  for (const [program, operations] of Object.entries({
    "/system/bin/cmd": ["user.list", "package.list", "package.enable", "package.disable-user", "jobscheduler.cancel", "jobscheduler.get-job-state", "activity.force-stop", "activity.get-uid-state", "activity.broadcast"],
    "/system/bin/pm": ["path", "list.packages", "install"],
  })) for (const operation of operations) {
    const value = traceStatus(); Object.assign(value.processTrace.records[0], { program, operation }); residentStatusShape(value);
  }
});
test("trace rejects unknown fields, unbound parents, noncanonical numbers, limits and outcomes", () => {
  const mutations: ((v: any) => void)[] = [
    v => { delete v.publisher; }, v => { v.processTrace.extra = true; },
    v => { v.processTrace.identityScope = "descendant"; }, v => { v.processTrace.dropped = 0; },
    v => { v.processTrace.overwritten = "01"; }, v => { v.processTrace.dropped = "18446744073709551616"; },
    v => { v.processTrace.omitted = 129; }, v => { v.processTrace.omitted = 0.5; },
    v => { v.processTrace.records = Array(129).fill(v.processTrace.records[0]); },
    v => { v.processTrace.omitted = 128; },
    v => { delete v.processTrace.records[0].monotonicNs; },
  ];
  for (const [key, invalid] of Object.entries({
    argv: "secret", program: "/system/bin/sh", operation: "package.list.secret", parentPid: 43,
    childPid: 1, operationId: "0", monotonicNs: "18446744073709551616", elapsedMs: "340282366920938463463374607431768211456",
  })) mutations.push(v => { v.processTrace.records[0][key] = invalid; });
  for (const invalid of ["exit", null, {}, { exit: 256 }, { exit: -1 }, { signal: 0 }, { signal: 129 }, { exit: "0" }, { exit: 0, signal: 11 }])
    mutations.push(v => { v.processTrace.records[0].outcome = invalid; });
  mutations.push(v => { v.processTrace.omitted = 0; v.processTrace.records = Array(60).fill(v.processTrace.records[0]); });
  for (const mutate of mutations) {
    const value = traceStatus(); mutate(value);
    assert.throws(() => residentStatusShape(value), /process|closed shape/);
  }
});
