import assert from "node:assert/strict";
import test from "node:test";
import { parseInstallerLogs } from "../emulator/installer-logs.ts";
import { queuedSnapshot, layoutCapture } from "../emulator/functional-observations.ts";
import { readFileSync } from "node:fs";
import { compressTar, logArchive, logMembers, tarBytes } from "./installer-log-fixture.ts";

const identity = { serial: "emulator-5562", version: "1.0.0-beta1", manifestSha256: "a".repeat(64) };
const diagnostic = { agent: "vehicle-drive-forced-ev", event: "acc.observe.changed", outcome: "observed", fields: { source: "callback", value: 0, off_generation: 1 } };
const row = { schemaVersion: 1, sequence: 1, timestamp: "2026-10-08T10:00:00Z", source: { kind: "agent", id: diagnostic.agent }, level: "info", component: "agent", message: "VOBDBG1 " + JSON.stringify(diagnostic), fields: {} };

function archive(changes: { records?: any[]; serial?: string; manifest?: string } = {}): Buffer {
  return logArchive({ ...identity, serial: changes.serial ?? identity.serial, manifestSha256: changes.manifest ?? identity.manifestSha256 }, changes.records ?? [row]);
}

test("ordinary log tar.xz binds exact package/device and exposes actual agent diagnostic fields", () => {
  const result = parseInstallerLogs(archive(), identity);
  assert.equal(result.records.length, 1);
  assert.deepEqual(result.diagnostics[0].fields, diagnostic.fields);
  assert.equal(result.diagnostics[0].record.source.id, diagnostic.agent);
  const noFields = { ...diagnostic } as Record<string, any>;
  delete noFields.fields;
  const rows = [{ ...row, message: "VOBDBG1 " + JSON.stringify(noFields) }, { ...row, sequence: 2, source: { kind: "daemon" }, message: "ordinary daemon message" }];
  assert.deepEqual(parseInstallerLogs(archive({ records: rows }), identity).diagnostics[0].fields, {});
});
test("checked ordinary log envelope retains canonical daemon enqueue proof independently of UI capture", () => {
  const proof = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/queued-layout-proof.json", import.meta.url), "utf8")).record;
  const actual = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/truncated-layout-events.json", import.meta.url), "utf8"));
  const ui = actual.map((record, index) => {
    const diagnostic = JSON.parse(record.message.slice(8));
    if (diagnostic.event === "ui.layout.capture") diagnostic.fields = { captureId: diagnostic.fields.captureId, desiredRevision: proof.fields.documentGeneration, snapshotSha256: proof.fields.snapshotSha256, generationId: proof.fields.generationId, catalogSha256: proof.fields.catalogSha256, effectiveLanguage: "en", count: 8, rootWidth: 1380 };
    return { ...record, sequence: proof.sequence + index + 1, timestamp: "2026-10-08T10:00:01Z", message: "VOBDBG1 " + JSON.stringify(diagnostic) };
  });
  const parsed = parseInstallerLogs(archive({ records: [proof, ...ui] }), identity);
  assert.deepEqual(queuedSnapshot(parsed.records[0]), proof.fields);
  assert.equal(parsed.diagnostics.length, 9);
  assert.equal(parsed.diagnostics.some((row) => row.record.source.kind === "daemon"), false);
  assert.throws(() => layoutCapture(parsed.diagnostics, "en", { RADIO_ITEM_MIN_WIDTH: 120, RADIO_ITEM_PADDING: 26 }, { process: proof.fields.process, generationId: proof.fields.generationId, catalogSha256: proof.fields.catalogSha256, documentGeneration: proof.fields.documentGeneration, targetPid: proof.fields.targetPid, boot: "current", baselineBoot: "current", afterSequence: proof.sequence, records: parsed.records }), /layout capture missing/);
  for (const changed of [{ ...proof, source: { kind: "agent", id: "ui-qinggan" } }, { ...proof, level: "debug" }]) {
    const wrong = parseInstallerLogs(archive({ records: [changed, ...ui] }), identity);
    assert.throws(() => queuedSnapshot(wrong.records[0]), /canonical daemon/);
  }
});

test("foreign envelope/manifest and forged agent attribution cannot become current observations", () => {
  assert.throws(() => parseInstallerLogs(archive({ serial: "1234567" }), identity), /identity/);
  assert.throws(() => parseInstallerLogs(archive({ manifest: "b".repeat(64) }), identity), /identity/);
  assert.throws(() => parseInstallerLogs(archive({ records: [{ ...row, source: { kind: "agent", id: "settings" } }] }), identity), /diagnostic identity/);
  assert.throws(() => parseInstallerLogs(archive({ records: [{ ...row, timestamp: "unavailable" }] }), identity), /canonical/);
});
test("valid XZ integrity does not admit trailing or concatenated archive content", () => {
  const valid = archive();
  assert.throws(() => parseInstallerLogs(Buffer.concat([valid, Buffer.from("hidden XZ suffix")]), identity), /XZ/);
  assert.throws(() => parseInstallerLogs(Buffer.concat([valid, compressTar(Buffer.from("hidden decoded suffix"))]), identity), /TAR/);
  assert.throws(() => parseInstallerLogs(Buffer.concat([valid, valid]), identity), /TAR/);
  const hiddenTar = Buffer.concat([tarBytes(logMembers(identity, [row])), Buffer.alloc(512, 1)]);
  assert.throws(() => parseInstallerLogs(compressTar(hiddenTar), identity), /TAR/);
  // System bsdtar may block-pad the compressed output with valid XZ zero padding.
  assert.equal(parseInstallerLogs(Buffer.concat([valid, Buffer.alloc(512)]), identity).records.length, 1);
});

test("truncated corrupt duplicate traversal nonregular and hidden USTAR entries are rejected", () => {
  const valid = archive();
  assert.throws(() => parseInstallerLogs(valid.subarray(0, valid.length - 1), identity), /XZ/);
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(logMembers(identity, [row])), "none"), identity), /XZ integrity check/);
  const corruptXz = Buffer.from(valid);
  corruptXz[corruptXz.length - 5] ^= 1;
  assert.throws(() => parseInstallerLogs(corruptXz, identity), /XZ/);
  for (const name of ["../manifest.json", "records.jsonl", "unknown.json"]) {
    const members = logMembers(identity, [row]);
    members[0].name = name;
    assert.throws(() => parseInstallerLogs(compressTar(tarBytes(members)), identity), /TAR name/);
  }
  for (const type of ["1", "2", "5", "x", "g"]) {
    const members = logMembers(identity, [row]);
    members[0].type = type;
    assert.throws(() => parseInstallerLogs(compressTar(tarBytes(members)), identity), /USTAR/);
  }
  const corruptHeader = tarBytes(logMembers(identity, [row]));
  corruptHeader[100] ^= 1;
  assert.throws(() => parseInstallerLogs(compressTar(corruptHeader), identity), /checksum/);
  const extra = [...logMembers(identity, [row]), { name: "hidden.json", body: Buffer.from("hidden") }];
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(extra)), identity), /TAR/);
  const missing = logMembers(identity, [row]).slice(0, 2);
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(missing)), identity), /TAR/);
  const oversized = logMembers(identity, [row]);
  oversized[1].size = 400 * 1024 * 1024 + 1;
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(oversized)), identity), /TAR.*size/);
  const largeManifest = logMembers(identity, [row]);
  largeManifest[0].size = 64 * 1024 + 1;
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(largeManifest)), identity), /TAR.*size/);
  const aggregate = logMembers(identity, [row]);
  aggregate[2].size = 400 * 1024 * 1024 + 1;
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(aggregate)), identity), /TAR.*size/);
  const expansion = compressTar(Buffer.alloc(400 * 1024 * 1024 + 64 * 1024 + 4096 + 512));
  assert.throws(() => parseInstallerLogs(expansion, identity), /XZ|bounded/);
  const nonzeroPadding = tarBytes(logMembers(identity, [row]));
  nonzeroPadding[512 + logMembers(identity, [row])[0].body.length] = 1;
  assert.throws(() => parseInstallerLogs(compressTar(nonzeroPadding), identity), /padding/);
  const missingEnd = tarBytes(logMembers(identity, [row])).subarray(0, -512);
  assert.throws(() => parseInstallerLogs(compressTar(missingEnd), identity), /TAR/);
});

test("accepts complete diagnostic archives and rejects incomplete source reports", () => {
  const diagnosticArchive = (envelopeStatus: string, bundleStatus: string): Buffer => {
    const members = logMembers(identity, [row]);
    const context = JSON.parse(members[2].body.toString("utf8"));
    context.diagnosticBundle = { schemaVersion: 1, completeness: bundleStatus };
    members[2].body = Buffer.from(JSON.stringify(context));
    const manifest = JSON.parse(members[0].body.toString("utf8"));
    manifest.header.contextLength = members[2].body.length;
    manifest.hostEnvelope.completeness = envelopeStatus;
    members[0].body = Buffer.from(JSON.stringify(manifest));
    return compressTar(tarBytes(members));
  };
  assert.equal(parseInstallerLogs(diagnosticArchive("complete", "complete"), identity).records.length, 1);
  for (const [envelope, bundle] of [["partial", "partial"], ["failed", "failed"], ["complete", "partial"], ["verified-stream", "partial"]]) {
    assert.throws(() => parseInstallerLogs(diagnosticArchive(envelope, bundle), identity), /identity/);
  }
});

test("observed ADB schema-2 records cannot provide functional evidence", () => {
  const bootId = "11111111-1111-1111-1111-111111111111", writerId = "a".repeat(32);
  const members = logMembers(identity, [{ ...row, schemaVersion: 2, bootId, writerId }]);
  const context = JSON.parse(members[2].body.toString("utf8"));
  context.observedLogSession = { availability: "available", bootId, writerId, highestObservedSequence: 1,
    authority: "observed-open-android-segment" };
  context.diagnosticBundle = { schemaVersion: 1, completeness: "complete" };
  members[2].body = Buffer.from(JSON.stringify(context));
  const manifest = JSON.parse(members[0].body.toString("utf8"));
  manifest.hostEnvelope.completeness = "complete";
  manifest.header.contextLength = members[2].body.length;
  members[0].body = Buffer.from(JSON.stringify(manifest));
  assert.throws(() => parseInstallerLogs(compressTar(tarBytes(members)), identity),
    /causal and negative checks are inconclusive/);
});

test("legacy consumer rejects authenticated session archives even when they contain only history", () => {
  for (const session of [
    { availability: "available", bootId: "11111111-1111-1111-1111-111111111111", writerId: "a".repeat(32), sequence: 0 },
    { availability: "unavailable", reason: "owner-unavailable-or-changed" },
  ]) {
    const members = logMembers(identity, [row]);
    const context = JSON.parse(members[2].body.toString("utf8")); context.currentLogSession = session;
    members[2].body = Buffer.from(JSON.stringify(context));
    const manifest = JSON.parse(members[0].body.toString("utf8")); manifest.header.contextLength = members[2].body.length;
    members[0].body = Buffer.from(JSON.stringify(manifest));
    assert.throws(() => parseInstallerLogs(compressTar(tarBytes(members)), identity), /legacy.*session|session.*consumer/i);
  }
});
