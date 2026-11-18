import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { spawnAdapter } from "../../release/emulator-gate.ts";
import { faultEffects, faultReceipt, validateFaultRequest, withFaultWindow, type FaultReceipt, type FaultRequest } from "./fault-window.ts";
import type { LogRecord } from "./installer-logs.ts";

const request: FaultRequest = { action: "fault-window", kind: "startup-mutation-lock", bootId: "11111111-1111-1111-1111-111111111111", manifestSha256: "a".repeat(64), generationId: "b".repeat(64), catalogSha256: "c".repeat(64), targetPid: 41, targetStarttime: "514" };
const serial = "emulator-5554", releaseIdentity = "1.0.0-beta1";
const armed: FaultReceipt = { schemaVersion: 1, phase: "armed", kind: request.kind, token: "d".repeat(64), bootId: request.bootId, releaseIdentity, manifestSha256: request.manifestSha256, generationId: request.generationId, catalogSha256: request.catalogSha256, ownerPid: 12, ownerStarttime: "451", daemonPid: 13, daemonStarttime: "452", targetPid: request.targetPid, targetStarttime: request.targetStarttime, startedMonotonicMs: "100000", expiresMonotonicMs: "190000" };
const envelope = (body: FaultReceipt) => ({ schemaVersion: 1, serial, phase: body.phase, body });
const closed = (): FaultReceipt => ({ ...armed, phase: "closed", cleanup: true, reason: "eof" });
function child() {
  const work = "/Users/vitaly/voboost/local-builds-new/emulator-gate-work"; mkdirSync(work, { recursive: true });
  const root = mkdtempSync(join(work, "fault-child-")), executable = join(root, "emulator-driver"), file = join(root, "request.json");
  writeFileSync(file, JSON.stringify(request) + "\n");
  writeFileSync(executable, `#!${process.execPath}\nconst armed=${JSON.stringify(envelope(armed))};\nprocess.stdout.write('[INFO] Synthetic host protocol fixture\\n');\nprocess.stdout.write(JSON.stringify(armed)+'\\n');\nprocess.stdin.resume();\nprocess.stdin.on('data',()=>{process.exitCode=9;});\nprocess.stdin.on('end',()=>{process.stdout.write(JSON.stringify(${JSON.stringify(envelope(closed()))})+'\\n');});\n`);
  chmodSync(executable, 0o700);
  return { executable, args: ["--emulator-check", file, "--device", serial, "--candidate", "/package/voboost-installer"], timeoutMs: 3000 };
}

test("fault window closes the real host child on observation completion and failure", async () => {
  let observations = 0;
  let releasedGuard!: () => void;
  const result = await withFaultWindow(child(), spawnAdapter, request, serial, releaseIdentity, async (current, live) => { releasedGuard = live; live(); observations++; assert.equal(current.token, armed.token); return "actual host observation"; });
  assert.equal(observations, 1); assert.equal(result.value, "actual host observation"); assert.equal(result.closed.cleanup, true); assert.equal(result.closed.reason, "eof");
  assert.throws(releasedGuard, /live conservative/);
  const original = new Error("original UI observation failed");
  await assert.rejects(() => withFaultWindow(child(), spawnAdapter, request, serial, releaseIdentity, async () => { throw original; }), error => error === original);
  // Completion is proved by the real EOF-triggered child terminal, never a sleep or caller pass flag.
});

test("fault window rejects foreign expiry replay and canonical effect substitution", async () => {
  const exact = faultReceipt(envelope(armed), request, serial, releaseIdentity, "armed");
  faultReceipt(envelope(closed()), request, serial, releaseIdentity, "closed", exact);
  for (const mutate of [
    (value: any) => value.serial = "emulator-5556",
    (value: any) => value.body.bootId = "22222222-2222-2222-2222-222222222222",
    (value: any) => value.body.targetPid++,
    (value: any) => value.body.targetStarttime = "0514",
    (value: any) => value.body.generationId = "e".repeat(64),
    (value: any) => value.body.releaseIdentity = "foreign",
    (value: any) => value.body.expiresMonotonicMs = "190001",
    (value: any) => value.body.extra = true,
  ]) { const value = structuredClone(envelope(armed)); mutate(value); assert.throws(() => faultReceipt(value, request, serial, releaseIdentity, "armed")); }
  for (const mutate of [
    (value: any) => value.body.token = "e".repeat(64),
    (value: any) => value.body.ownerStarttime = "999",
    (value: any) => value.body.cleanup = false,
    (value: any) => value.body.reason = "expired",
  ]) { const value = envelope(closed()); mutate(value); assert.throws(() => faultReceipt(value, request, serial, releaseIdentity, "closed", armed)); }
  assert.throws(() => validateFaultRequest({ ...request, targetPath: "/arbitrary" }));
  assert.throws(() => validateFaultRequest({ ...request, targetPid: "41" }));
  await assert.rejects(() => withFaultWindow({ ...child(), args: ["--emulator-check", "/request.json", "--device", "physical-serial"] }, spawnAdapter, request, "physical-serial", releaseIdentity, async () => {}), /exact local emulator/);
  const record: LogRecord = { schemaVersion: 1, sequence: 2, timestamp: "2026-10-11T17:00:00Z", source: { kind: "daemon" }, level: "info", component: "emulator-diagnostic", message: "fault-effect", fields: { token: armed.token, kind: armed.kind, effect: "startup-mutation-rejected", source: "settings", requestId: "52", bootId: request.bootId, manifestSha256: request.manifestSha256, generationId: request.generationId, catalogSha256: request.catalogSha256, targetPid: "41", targetStarttime: "514", process: "com.qinggan.app.setting" } };
  assert.deepEqual(faultEffects([record], armed, "settings", 1), [record]);
  assert.deepEqual(faultEffects([record], armed, "settings", 2), []);
  assert.deepEqual(faultEffects([{ ...record, fields: { ...record.fields, token: "f".repeat(64) } }], armed, "settings", 1), []);
  for (const mutate of [
    (value: any) => value.source.kind = "agent",
    (value: any) => value.fields.targetPid = "42",
    (value: any) => value.fields.targetStarttime = "515",
    (value: any) => value.fields.process = "foreign",
    (value: any) => value.fields.effect = "state-reply-dropped",
    (value: any) => value.fields.bootId = "foreign",
    (value: any) => value.fields.requestId = "01",
    (value: any) => value.fields.extra = "untrusted",
  ]) { const value = structuredClone(record); mutate(value); assert.throws(() => faultEffects([value], armed, "settings", 1)); }
  for (const mode of ["early-exit", "duplicate-arm", "expired", "no-cleanup"]) {
    const invocation = child(); let observed = false;
    await assert.rejects(() => withFaultWindow(invocation, async inv => {
      if (mode === "early-exit") return { exitCode: 1, stdout: "", stderr: "actual early failure" };
      inv.onOutput!("stdout", JSON.stringify(envelope(armed)) + "\n");
      if (mode === "duplicate-arm") inv.onOutput!("stdout", JSON.stringify(envelope(armed)) + "\n");
      await inv.stdinUntil;
      if (mode === "expired") inv.onOutput!("stdout", JSON.stringify(envelope({ ...closed(), reason: "expired" })) + "\n");
      return { exitCode: 0, stdout: "", stderr: "" };
    }, request, serial, releaseIdentity, async () => { observed = true; }));
    if (mode === "early-exit") assert.equal(observed, false);
  }
});


test("fault window rejects legacy and extra device arguments before dispatch", async () => {
  let calls = 0;
  for (const args of [
    ["--emulator-check", "/request.json", "--device-serial", serial],
    ["--emulator-check", "/request.json", "--device", "emulator-5588"],
    ["--emulator-check", "/request.json", "--device", serial, "--adb-server-port", "25554"],
  ]) {
    await assert.rejects(withFaultWindow({ executable: "/installer", args, timeoutMs: 1000 },
      async () => { calls++; throw new Error("must not dispatch"); },
      request, serial, releaseIdentity, async () => {}), /exact local emulator/);
  }
  assert.equal(calls, 0);
});
