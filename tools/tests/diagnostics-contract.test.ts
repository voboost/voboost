import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import { diagnosticBody } from "../emulator/functional-observations.ts";

/** The installer owns these bytes and verifies them against the actual CLI serde producer. */
test("actual installer-generated diagnostic contract is accepted by the product consumer", () => {
  const root = process.env.VOBOOST_INSTALLER_ROOT || new URL("../../../voboost-installer/", import.meta.url).pathname;
  const receipts = JSON.parse(readFileSync(resolve(root, "src-tauri/tests/fixtures/diagnostics.json"), "utf8"));
  assert.ok(Array.isArray(receipts) && receipts.length === 3);
  assert.deepEqual(receipts.map((row) => [row.inspectOperation.success, row.inspectOperation.class || "command"]), [[true, "command"], [false, "command"], [false, "transport-failed"]]);
  for (const receipt of receipts) {
    assert.equal(diagnosticBody(JSON.stringify(receipt), "fixture-device"), "fixture diagnostic body\n");
    assert.throws(() => diagnosticBody(JSON.stringify(receipt), "foreign-device"));
    assert.throws(() => diagnosticBody(JSON.stringify({ ...receipt, futureField: true }), "fixture-device"));
    assert.throws(() => diagnosticBody(JSON.stringify({ ...receipt, inspectOperation: { ...receipt.inspectOperation, futureField: true } }), "fixture-device"));
  }
});
