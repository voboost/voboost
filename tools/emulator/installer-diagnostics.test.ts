import {driverInvocation} from "./driver.ts";
import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { installerRootPending, installerClosedPending, offlineObservation, spawnAdapter } from "../../release/emulator-gate.ts";
import { InstallerUi, GesturePreInputDrift } from "./installer-ui.ts";
import { installerLog, installerFailure } from "../tests/installer-diagnostic-fixture.ts";
import { installerRecord, installerRebootProgress } from "./installer-diagnostics.ts";

const marker = "boot-observation failure reason=guard-root remote_exit=73 stdout_bytes=0 stderr_bytes=36\n";
const root = () => ({ ...installerFailure("Emulator acceptance failed: boot observation guard rejected: root"), stderr: marker + installerLog("Emulator acceptance failed: boot observation guard rejected: root", "error", 4) });
const closed = () => installerFailure(readFileSync(new URL("../tests/fixtures/boot-closed-153-stderr.txt", import.meta.url), "utf8"));
const pending = (value: any) => installerClosedPending(value, "boot-observation", "emulator-5562", true, 120, 1);

test("reboot progress decodes complete canonical INFO messages only and preserves stream boundaries", () => {
  const line = installerLog("Restarting the head unit (reboot 1). Waiting for reconnection; install will continue automatically.", "info", 2);
  const read = installerRebootProgress();
  assert.deepEqual(read(line.slice(0, 17)), []);
  assert.deepEqual(read(line.slice(17)), [1]);
  assert.throws(() => read(line), /sequence/);
  for (const invalid of [line.replace('"kind":"installer"', '"kind":"host"'), "raw reboot 1\n", line.replace('"schemaVersion":1', '"schemaVersion":2')]) assert.throws(() => installerRebootProgress()(invalid));
  for (const ignored of [line.replace('"level":"info"', '"level":"debug"'), line.replace('"fields":{}', '"fields":{"unknown":"value"}'), line.replace("reboot 1", "reboot 3"), line.replace("; install ", "; restore ")]) assert.deepEqual(installerRebootProgress()(ignored), []);
});

test("actual installer canonical root and closed errors retain the narrow reboot retry authority", () => {
  assert.equal(installerRootPending(root(), true, "boot-observation"), true);
  assert.equal(pending(closed()), true);
  const connected = installerLog("Connecting to the head unit over USB", "info", 1) + installerLog("Connected to the head unit", "info", 2);
  assert.equal(installerRootPending({ ...root(), stderr: connected + root().stderr }, true, "boot-observation"), true);
  assert.equal(pending({ ...closed(), stderr: connected + closed().stderr }), true);
  assert.equal(installerRootPending(root(), false, "boot-observation"), false);
  assert.equal(installerClosedPending(closed(), "boot-observation", "emulator-5562", true, 1, 1), false);
  for (const value of [root(), closed()]) for (const mutate of [
    (text: string) => text.replace('"kind":"installer"', '"kind":"host"'),
    (text: string) => text.replace('"level":"error"', '"level":"info"'),
    (text: string) => text.replace('"fields":{}', '"fields":{"unknown":"value"}'),
    (text: string) => text + installerLog("independent failure", "error", 5),
    (text: string) => text + "unknown raw line\n",
    (text: string) => text + "\n",
  ]) {
    const altered = { ...value, stderr: mutate(value.stderr) };
    assert.equal(installerRootPending(altered, true, "boot-observation"), false);
    assert.equal(pending(altered), false);
  }
  assert.equal(installerRootPending({ ...root(), stdout: "{}\n" }, true, "boot-observation"), false);
  assert.equal(installerRootPending({ ...root(), stderr: root().stderr.replace("remote_exit=73", "remote_exit=1") }, true, "boot-observation"), false);
});

test("duplicate canonical diagnostic keys never grant actual retry authority", () => {
  for (const value of [root(), closed(), installerFailure("Emulator acceptance failed: emulator operation failed (Some(1)): adb: device offline")]) {
    for (const mutate of [
      (text: string) => text.replace('"message":', '"message":"permission denied","message":'),
      (text: string) => text.replace('"message":', '"mess\\u0061ge":"permission denied","message":'),
      (text: string) => text.replace('"source":', '"source":{"kind":"host","id":"foreign"},"source":'),
      (text: string) => text.replace('"kind":"installer"', '"kind":"host","kind":"installer"'),
      (text: string) => text.replace('"id":"desktop"', '"id":"foreign","\\u0069d":"desktop"'),
      (text: string) => text.replace('"fields":{}', '"fields":{"error":"permission denied"},"fields":{}'),
      (text: string) => text.replace('"fields":{}', '"fields":{"detail":"permission denied","deta\\u0069l":"retry"}'),
    ]) {
      const altered = { ...value, stderr: mutate(value.stderr) };
      assert.equal(installerRootPending(altered, true, "boot-observation"), false);
      assert.equal(pending(altered), false);
      assert.equal(offlineObservation(altered), false);
      assert.throws(() => installerRecord(altered.stderr.split("\n").find(line => line.startsWith("{"))!), /duplicate/);
    }
  }
});

test("diagnostic decoder preserves Unicode, escaped strings and independent object keys", () => {
  const message = 'Привет 世界 "message": {"id":"value"} \\ \n';
  const line = installerLog(message).replace('"fields":{}', '"fields":{"message":"значение","id":"\\u4e16\\u754c"}');
  assert.equal(installerRecord(line).message, message);
  assert.deepEqual(installerRecord(line).fields, { message: "значение", id: "世界" });
  const escaped = root().stderr.replace('"message":', '"mess\\u0061ge":').replace('"kind":', '"k\\u0069nd":');
  assert.equal(installerRootPending({ ...root(), stderr: escaped }, true, "boot-observation"), true);
});

test("canonical transport errors are classified by the error message without accepting hidden or unrelated errors", () => {
  const value = installerFailure("Emulator acceptance failed: emulator operation failed (Some(1)): adb: device offline\n");
  assert.equal(offlineObservation(value), true);
  assert.equal(offlineObservation(closed()), false);
  for (const stderr of [value.stderr + installerLog("permission denied", "error", 5), value.stderr + "raw\n",
    value.stderr.replace('"kind":"installer"', '"kind":"app"'), installerLog("permission denied", "error").replace('"fields":{}', '"fields":{"detail":"device offline"}')])
    assert.equal(offlineObservation({ ...value, stderr }), false);
});

test("actual canonical pre-input drift gets one typed rejection and malformed protocol remains terminal", async () => {
  const diagnostic = readFileSync(new URL("../../testdata/emulator/drift141/stderr.txt", import.meta.url), "utf8");
  const request = JSON.parse(readFileSync(new URL("../../testdata/emulator/drift141/request.json", import.meta.url), "utf8"));
  const fixtureRoot = join(process.cwd(), "build/unit-fixtures");
  mkdirSync(fixtureRoot, { recursive: true });
  const output = mkdtempSync(join(fixtureRoot, "canonical-drift-"));
  try {
    const current = installerFailure(diagnostic, "emulator-5562");
    for (const [index, value] of [current, { ...current, stderr: current.stderr + installerLog("unknown mutation", "error", 5) },
      { ...current, stdout: "{}\n" }, { ...current, stderr: current.stderr.replace("emulator-5562", "foreign") }].entries()) {
      const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(output, String(index)), async () => value, async () => {});
      await assert.rejects(() => ui.call(request), error => index === 0 ? error instanceof GesturePreInputDrift : !(error instanceof GesturePreInputDrift));
    }
  } finally { rmSync(output, { recursive: true, force: true }); }
});

test("diagnostic driver enables preparation debug only for its install action", async () => {
  const fixtureRoot = join(process.cwd(), "build/unit-fixtures");
  mkdirSync(fixtureRoot, { recursive: true });
  const output = mkdtempSync(join(fixtureRoot, "diagnostic-level-"));
  try {
    const executable = join(output, "voboost-installer");
    writeFileSync(executable, '#!/bin/sh\nprintf "%s" "$VOBOOST_LOG"\n');
    chmodSync(executable, 0o700);
    assert.equal((await spawnAdapter({ executable, args: ["--install"], timeoutMs: 10000 })).stdout, "debug");
    const driver = join(output, "emulator-driver");
    writeFileSync(driver, '#!/bin/sh\nprintf "%s" "$VOBOOST_LOG"\n');
    chmodSync(driver, 0o700);
    const previous = process.env.VOBOOST_EMULATOR_DRIVER;
    try {
      process.env.VOBOOST_EMULATOR_DRIVER = driver;
      assert.equal((await spawnAdapter(driverInvocation(executable, join(output, "request.json"), "emulator-5562", {timeoutMs:10000}))).stdout, "info");
    } finally {
      if (previous === undefined) delete process.env.VOBOOST_EMULATOR_DRIVER;
      else process.env.VOBOOST_EMULATOR_DRIVER = previous;
    }
  } finally { rmSync(output, { recursive: true, force: true }); }
});
