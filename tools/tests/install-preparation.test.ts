import assert from "node:assert/strict";
import test from "node:test";
import { preparationReceipts, preparationProof } from "../emulator/install-preparation.ts";
import { installerLog } from "./installer-diagnostic-fixture.ts";
const hash = "a".repeat(64), serial = "emulator-5562", empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
const receipt = (step = "root", sequence = 1) => ({ schemaVersion: 1, deviceSerial: serial, sequence, context: "staging", step, attempt: 1, transactionId: hash, manifestSha256: hash, journal: null, bootId: null, phase: null, rebootCount: null, statusSequence: null, journalSha256: null, result: "success", exitCode: 0, error: null, errorTruncated: false, stdoutSha256: empty, stdoutBytes: 0, stderrSha256: empty, stderrBytes: 0 });
const line = (row: object) => installerLog("Install preparation receipt: " + JSON.stringify(row), "debug", Number((row as any).sequence));
const result = (rows: object[]) => ({ exitCode: 0, stdout: "", stderr: rows.map(line).join("") });
test("current installer DEBUG canonical stderr retains typed preparation proof", () => {
  const value = receipt();
  const current = { exitCode: 0, stdout: "", stderr: installerLog("Install preparation receipt: " + JSON.stringify(value), "debug") };
  assert.equal(preparationReceipts(current, serial, hash)[0].sequence, "1");
});
test("installer preparation accepts only exact typed same-action serial receipts and preserves real failures", () => {
  const r = receipt();
  assert.equal(preparationReceipts(result([r]), serial, hash)[0].sequence, "1");
  for (const altered of [{ ...r, schemaVersion: 2 }, { ...r, deviceSerial: "foreign" }, { ...r, passed: true }, { ...r, manifestSha256: "b".repeat(64) }, { ...r, transactionId: "b".repeat(64) }, { ...r, sequence: "1" }, { ...r, attempt: 31 }, { ...r, step: "shell" }, { ...r, stdoutBytes: null }, { ...r, result: "success", exitCode: 1 }, { ...r, error: "false success" }, { ...r, errorTruncated: true }, { ...r, bootId: "current boot" }, { ...r, journal: `recovery/${hash}/install-journal.json` }]) assert.throws(() => preparationReceipts(result([altered]), serial, hash));
  assert.throws(() => preparationReceipts(result([r, r]), serial, hash), /sequence/);
  assert.throws(() => preparationReceipts({ ...result([r]), stderr: line(r).replace('"level":"debug"', '"level":"info"') }, serial, hash));
  assert.throws(() => preparationReceipts({ ...result([r]), stdout: line(r) }, serial, hash));
  const failed = { ...r, result: "failed", exitCode: 1, error: "root denied" };
  assert.equal(preparationReceipts(result([failed]), serial, hash)[0].result, "failed");
  const transport = { ...failed, result: "transport-failed", exitCode: null, stdoutSha256: null, stdoutBytes: null, stderrSha256: null, stderrBytes: null };
  assert.equal(preparationReceipts(result([transport]), serial, hash)[0].result, "transport-failed");
  for (const altered of [{ ...transport, stdoutSha256: empty }, { ...failed, error: "Ё".repeat(513) }, { ...failed, error: null }]) assert.throws(() => preparationReceipts(result([altered]), serial, hash));
});
test("installer preparation ordered readiness and previous journal correlation never infer fresh boot or raw output", () => {
  const ready = ["wait-device", "root", "wait-device", "remount", "relax-first-phase", "verify-write-access"].map((step, i) => receipt(step, i + 1));
  const enforce = { ...receipt("enforce-selinux", 7), context: "completion", manifestSha256: null, journal: `recovery/${hash}/install-journal.json`, bootId: "11111111-1111-1111-1111-111111111111", phase: "install-complete", rebootCount: 2, statusSequence: 36, journalSha256: "b".repeat(64) };
  const admitted = preparationReceipts(result([...ready, enforce]), serial, hash);
  assert.deepEqual(preparationProof(admitted), { readiness: true, enforcing: true, journal: true });
  assert.equal(admitted[0].bootId, null);
  assert.equal(preparationProof(preparationReceipts(result([enforce]), serial, hash)).readiness, false);
  for (const rows of [ready.slice(1), [ready[1], ready[0], ...ready.slice(2)], ready.map((row, i) => i === 4 ? { ...row, result: "failed", exitCode: 1, error: "setenforce denied" } : row), ready.map((row, i) => i === 2 ? { ...row, attempt: 2 } : row)]) assert.equal(preparationProof(preparationReceipts(result(rows.map((row, i) => ({ ...row, sequence: i + 1 }))), serial, hash)).readiness, false);
  for (const altered of [{ ...enforce, journal: `recovery/${hash}/restore-journal.json` }, { ...enforce, phase: "uninstall-complete" }, { ...enforce, journalSha256: null }, { ...enforce, statusSequence: -1 }, { ...enforce, statusSequence: 33 }, { ...enforce, statusSequence: "36" }, { ...enforce, manifestSha256: hash }]) assert.throws(() => preparationReceipts(result([altered]), serial, hash));
  assert.deepEqual(preparationProof(preparationReceipts(result([]), serial, hash)), { readiness: false, enforcing: false, journal: false });
});
