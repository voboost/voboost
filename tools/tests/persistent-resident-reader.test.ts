import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { inspectionResident, persistentVehicleArmed, residentStatusShape } from "../emulator/functional-observations.ts";

test("persistent and inspection readers share exact producer endpoint revisions", () => {
  const injectRoot = process.env.VOBOOST_INJECT_CONTRACT_ROOT || fileURLToPath(new URL("../../../voboost-inject/", import.meta.url));
  const endpoint = JSON.parse(readFileSync(resolve(injectRoot, "daemon/tests/fixtures/current-endpoint146.json"), "utf8")).accepted;
  const authority = { boot: endpoint.acceptedStatus.receipt.bootId, generationId: endpoint.generationId, catalogSha256: endpoint.catalogSha256 };
  const status = { schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: "c".repeat(64), generationId: authority.generationId, catalogSha256: authority.catalogSha256, startupMode: "interface", state: "ready", killed: false, panic: false,
    injections: [{ id: "vehicle-drive-forced-ev", process: "com.qinggan.systemservice", state: "active", reason: "", details: {}, documentGeneration: endpoint.desiredDocumentGeneration, currentEndpoint: endpoint }], executors: [],
    persistentVehicleState: { bootId: authority.boot, observedAtBootMs: 1000, forcedEv: { outcome: "value", value: true }, pedestrianWarning: { outcome: "value", value: false } } };
  // The Rust producer emits the legacy row revision as an integer and endpoint identities as strings.
  const text = JSON.stringify(status).replace('"documentGeneration":"' + endpoint.desiredDocumentGeneration + '"', '"documentGeneration":' + endpoint.desiredDocumentGeneration);
  assert.throws(() => residentStatusShape(JSON.parse(text)), /current endpoint resident identity differs/);
  residentStatusShape(inspectionResident("\nresident-status:\n/data/voboost/status.json\n" + text + "\n"));
  const envelope = (json: string) => `persistent-vehicle-state-snapshot:\nsource=daemon-secure-root-store-read; freshness=compare-boot-id-and-observedAtBootMs; unavailable-is-not-false\ncurrent-boot-id=${authority.boot}\ncurrent-boot-uptime=1.00 1.00\n${json}\nblock-protection:\n`;
  assert.deepEqual(persistentVehicleArmed(envelope(text), authority), { fev: { status: "present", value: true }, pw: { status: "present", value: false } });
  assert.throws(() => persistentVehicleArmed(envelope(text + " trailing"), authority));
  assert.throws(() => persistentVehicleArmed(envelope(text), { ...authority, generationId: "d".repeat(64) }));
});

test("actual persistent chain entrypoint accepts exact numeric producer revision", () => {
  const injectRoot = process.env.VOBOOST_INJECT_CONTRACT_ROOT || fileURLToPath(new URL("../../../voboost-inject/", import.meta.url));
  const endpoint = JSON.parse(readFileSync(resolve(injectRoot, "daemon/tests/fixtures/current-endpoint146.json"), "utf8")).accepted;
  endpoint.generationId = "11".repeat(32); endpoint.catalogSha256 = "22".repeat(32);
  const status = { schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: "c".repeat(64), generationId: endpoint.generationId, catalogSha256: endpoint.catalogSha256, startupMode: "interface", state: "ready", killed: false, panic: false,
    injections: [{ id: "vehicle-drive-forced-ev", process: "com.qinggan.systemservice", state: "active", reason: "", details: {}, documentGeneration: endpoint.desiredDocumentGeneration, currentEndpoint: endpoint }], executors: [],
    persistentVehicleState: { bootId: "11111111-1111-1111-1111-111111111111", observedAtBootMs: 100000, forcedEv: { outcome: "value", value: true }, pedestrianWarning: { outcome: "value", value: false } } };
  const text = JSON.stringify(status).replace('"documentGeneration":"' + endpoint.desiredDocumentGeneration + '"', '"documentGeneration":' + endpoint.desiredDocumentGeneration);
  const run = (input: string) => spawnSync(process.execPath, [fileURLToPath(new URL("../emulator/persistent-vehicle-chain.ts", import.meta.url))], { input, encoding: "utf8" });
  const result = run(text); assert.equal(result.status, 0, result.stderr); assert.match(result.stdout, /production consumer PASS/);
  assert.notEqual(run(text + " trailing").status, 0);
});
