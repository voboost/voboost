import assert from "node:assert/strict";
import test from "node:test";
import { agentToggleReady, accCycle, resetRestore, layoutCapture as consumeLayoutCapture, queuedSnapshot, renderLocale, catalogScan, freshDiagnostics, sequence, vehicleArmed, vehicleRead, oemFeature, rootVehicleRecord, rootVehicleState } from "../emulator/functional-observations.ts";
import type { AgentDiagnostic } from "../emulator/installer-logs.ts";
import { parseUi } from "../emulator/installer-ui.ts";
import { readFileSync } from "node:fs";
import { diagnosticBody } from "../emulator/functional-observations.ts";

const diagnostic = (sequence: number, agent: string, event: string, outcome: string, fields: Record<string, unknown> = {}): AgentDiagnostic => ({
  agent, event, outcome, fields,
  record: { schemaVersion: 1, sequence, timestamp: "2026-10-08T10:00:00Z", source: { kind: "agent", id: agent }, level: "info", component: "agent", message: "", fields: {} },
});
const topology = {
  sections: ["language", "interface", "vehicle", "uninstall"],
  sectionBodies: ["language", "interface", "vehicle", "uninstall"].map((key, index) => ({ key, index })),
  checkboxSwitches: ["font-cyrillic", "keyboard-ru", "keyboard-en", "launcher-apps", "vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning"].map((key) => ({ key, section: key.startsWith("vehicle-") ? "vehicle" : "interface" })),
  panelTargets: [
    ...["en", "ru", "zh"].map((value) => ({ role: "language", value, class: "com.pateo.material.widgets.BoldRadioButton" })),
    ...["interface", "hidden", "none"].map((value) => ({ role: "startup", value, class: "com.pateo.material.widgets.BoldRadioButton" })),
    { role: "uninstall.action", value: "uninstall", class: "android.widget.Button" },
    { role: "uninstall.status", value: "status", class: "android.widget.TextView" },
    ...["language", "interface", "vehicle", "uninstall"].map((value) => ({ role: `section.${value}.title`, value, class: "android.widget.TextView" })),
    ...["language", "startup"].map((value) => ({ role: `${value}.title`, value, class: "android.widget.TextView" })),
    { role: "startup.description", value: "startup", class: "android.widget.TextView" },
    ...["font-cyrillic", "keyboard-ru", "keyboard-en", "launcher-apps", "vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning"].flatMap((value) => ["title", "description"].map((suffix) => ({ role: `checkbox.${value}.${suffix}`, value, class: "android.widget.TextView" }))),
  ],
  dialogTargets: ["confirm", "cancel"].map((value) => ({ role: `dialog.${value}`, value, class: "com.pateo.material.widgets.BoldTextView" })),
};
const layoutCapture = (rows, language, geometry, authority?, expected = topology, surface: "panel" | "dialog" = "panel", phase: "after-layout" | "pre-click" = "after-layout") => consumeLayoutCapture(rows, language, geometry, authority, expected, surface, phase);
const agent = "vehicle-drive-forced-ev";
const state = (fev: string, pw = "true") => `vehicle-agent-state:\n/data/voboost/state/vehicle-drive-forced-ev_forced-ev-armed.json=${fev}\n/data/voboost/state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed.json=${pw}\nblock-protection:\n`;
function rootState() {
  const record = { schemaVersion: 1, sequence: 10, timestamp: "2026-10-08T10:00:00Z", source: { kind: "daemon" }, level: "info", component: "agent-state", message: "vehicle-state-observed", fields: { agentId: agent, key: "forced-ev-armed", operation: "read", outcome: "value", value: "true", process: "com.qinggan.systemservice", targetPid: "101", targetStarttime: "200", endpointEpoch: "3", generationId: "a".repeat(64), catalogSha256: "b".repeat(64), documentGeneration: "18446744073709551614", bootId: "11111111-1111-1111-1111-111111111111", requestId: "20" } };
  const authority = { boot: record.fields.bootId, baselineBoot: record.fields.bootId, generationId: record.fields.generationId, catalogSha256: record.fields.catalogSha256, process: record.fields.process, targetPid: record.fields.targetPid, documentGeneration: record.fields.documentGeneration };
  return { record, authority };
}
test("canonical root vehicle receipt rejects forged frame identity malformed booleans and operation failures", () => {
  const { record } = rootState();
  assert.deepEqual(rootVehicleRecord(record).state, { status: "present", value: true });
  for (const outcome of ["missing", "unavailable"]) {
    const fields = { ...record.fields, outcome }; delete fields.value;
    assert.deepEqual(rootVehicleRecord({ ...record, fields }).state, { status: outcome === "missing" ? "absent" : outcome });
  }
  for (const fields of [{ ...record.fields, value: true }, { ...record.fields, value: '"true"' }, { ...record.fields, outcome: "unavailable" }, { ...record.fields, agentId: "settings" }, { ...record.fields, key: "pedestrian-warning-armed" }, { ...record.fields, operation: "write" }, { ...record.fields, operation: "write-readback" }, { ...record.fields, documentGeneration: 18446744073709551614 }, { ...record.fields, passed: "true" }, { ...record.fields, catalogSha256: undefined }])
    assert.throws(() => rootVehicleRecord({ ...record, fields }));
  for (const changed of [{ ...record, source: { kind: "agent", id: agent } }, { ...record, source: { kind: "daemon", id: agent } }, { ...record, component: "runtime" }, { ...record, level: "debug" }, { ...record, passed: true }]) assert.throws(() => rootVehicleRecord(changed));
  const fields = { ...record.fields, operation: "write-readback" }; delete fields.requestId;
  assert.deepEqual(rootVehicleRecord({ ...record, fields }).state, { status: "present", value: true });
});
test("root vehicle state requires current resident target boot revision and fresh actual operation", () => {
  const { record, authority } = rootState();
  assert.deepEqual(rootVehicleState([record], "fev", authority, 9), { status: "present", value: true });
  assert.deepEqual(rootVehicleState([record], "pw", authority, 9), { status: "unavailable" });
  assert.deepEqual(rootVehicleState([record], "fev", authority, 10), { status: "unavailable" }, "historical event is not an on-demand query");
  for (const changed of [{ ...authority, baselineBoot: "another" }, { ...authority, boot: "another" }, { ...authority, generationId: "c".repeat(64) }, { ...authority, catalogSha256: "c".repeat(64) }, { ...authority, targetPid: "102" }, { ...authority, process: "foreign" }, { ...authority, documentGeneration: "18446744073709551615" }, { ...authority, targetStarttime: "201" }, { ...authority, endpointEpoch: "4" }])
    assert.deepEqual(rootVehicleState([record], "fev", changed, 9), { status: "unavailable" });
  for (const key of ["bootId", "targetStarttime", "generationId", "catalogSha256"]) {
    const fields = { ...record.fields }; delete fields[key]; if (key === "generationId" || key === "catalogSha256") { delete fields.generationId; delete fields.catalogSha256; }
    assert.deepEqual(rootVehicleState([{ ...record, fields }], "fev", authority, 9), { status: "unavailable" });
  }
  const failure = { ...record, sequence: 11, fields: { ...record.fields, outcome: "unavailable" } }; delete failure.fields.value;
  assert.deepEqual(rootVehicleState([record, failure], "fev", authority, 9), { status: "unavailable" });
});
test("carried root read baseline requires complete unchanged same-boot operation window", () => {
  const { record, authority } = rootState();
  const tick = { ...record, sequence: 11, component: "runtime", message: "tick", fields: {} };
  assert.deepEqual(rootVehicleState([record, tick], "fev", authority, 11, [record]), { status: "present", value: true });
  assert.deepEqual(rootVehicleState([tick], "fev", authority, 11, [record]), { status: "unavailable" });
  assert.deepEqual(rootVehicleState([record, { ...tick, sequence: 12 }], "fev", authority, 12, [record]), { status: "unavailable" });
  const replacement = { ...record, sequence: 11, fields: { ...record.fields, endpointEpoch: "4", targetStarttime: "201" } };
  assert.deepEqual(rootVehicleState([record, replacement], "fev", authority, 11, [record]), { status: "unavailable" });
  const unavailable = { ...record, sequence: 11, fields: { ...record.fields, outcome: "unavailable" } }; delete unavailable.fields.value;
  assert.deepEqual(rootVehicleState([record, unavailable], "fev", authority, 11, [record]), { status: "unavailable" });
});
function queuedLayout() {
  const proof = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/queued-layout-proof.json", import.meta.url), "utf8")).record;
  const actual = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/truncated-layout-events.json", import.meta.url), "utf8"));
  const rows = actual.map((record, index) => {
    const row = { ...JSON.parse(record.message.slice(8)), record: { ...record, sequence: 110 + index, timestamp: "2026-10-08T10:00:01Z" } };
    if (row.event === "ui.layout.capture") row.fields = { captureId: row.fields.captureId, desiredRevision: proof.fields.documentGeneration, snapshotSha256: proof.fields.snapshotSha256, generationId: proof.fields.generationId, catalogSha256: proof.fields.catalogSha256, effectiveLanguage: "en", count: 8, rootWidth: 1380 };
    row.record.message = "VOBDBG1 " + JSON.stringify({ agent: row.agent, event: row.event, outcome: row.outcome, fields: row.fields });
    return row;
  });
  const geometry = { RADIO_ITEM_MIN_WIDTH: 120, RADIO_ITEM_PADDING: 26, RADIO_HEIGHT: 70, RADIO_TEXT: 28, RADIO_TITLE_TEXT: 32, RADIO_DESCRIPTION_TEXT: 24, BUTTON_HEIGHT: 70, BUTTON_HORIZONTAL_PADDING: 38, SECTION_GAP: 25, SECTION_HEADER: 98, SECTION_TOP: 40, SECTION_BOTTOM: 2, CHECKBOX_HEIGHT: 42, CHECKBOX_CONTENT_TOP: 0 };
  rows[0].fields = { ...rows[0].fields, count: topology.panelTargets.length, surface: "panel", phase: "after-layout", sections: topology.sections.length };
  const captureId = rows[0].fields.captureId;
  for (const spec of topology.panelTargets) {
    let row = rows.find((row) => row.event === "ui.layout" && row.fields.role === spec.role && row.fields.value === spec.value);
    const radio = ["language", "startup"].includes(spec.role), action = spec.role === "uninstall.action";
    const padding = radio ? 26 : action ? 38 : 0;
    if (!row) { row = diagnostic(0, "ui-qinggan", "ui.layout", "measured", { captureId, role: spec.role, value: spec.value, width: 500, min_width: 0, padding_left: 0, padding_right: 0, caption_px: 200, top: 0, bottom: 40, lines: 1, ellipsis: 0 }); rows.push(row); }
    row.fields = { ...row.fields, padding_left: padding, padding_right: padding, top: 0, bottom: radio || action ? 70 : 40, ellipsis: 0 };
    rows.push(diagnostic(0, "ui-qinggan", "ui.layout.geometry", "measured", { captureId, role: spec.role, value: spec.value, height: row.fields.bottom, left: 0, right: row.fields.width, parentWidth: 1380, class: spec.class, textSize: radio ? 28 : ["language.title", "startup.title"].includes(spec.role) ? 32 : spec.role === "startup.description" ? 24 : 28, paddingTop: 0, paddingBottom: 0, layoutWidth: Number(row.fields.width) - 2 * padding }));
  }
  topology.sections.forEach((key, index) => rows.push(diagnostic(0, "ui-qinggan", "ui.layout.section", "measured", { captureId, key, index, top: index * 225, bottom: index * 225 + 200, width: 1380, height: 200, gapBefore: index === 0 ? 0 : 25, rootHeight: 900 })));
  topology.sectionBodies.forEach(({ key, index }) => rows.push(diagnostic(0, "ui-qinggan", "ui.layout.section.body", "measured", { captureId, key, index, top: index * 225 + 98, bottom: index * 225 + 200, width: 1380, height: 102, paddingTop: 40, paddingBottom: 2, firstChildTop: 40, lastChildBottom: 100, class: "android.widget.LinearLayout" })));
  topology.checkboxSwitches.forEach(({ key, section }) => {
    const top = topology.sections.indexOf(section) * 225 + 138;
    rows.push(diagnostic(0, "ui-qinggan", "ui.layout.checkbox", "measured", { captureId, key, section, width: 90, height: 42, top, bottom: top + 42, parentTop: top, parentBottom: top + 42, parentPaddingTop: 0, rowTop: top, rowBottom: top + 60 }));
  });
  rows.forEach((row, index) => row.record.sequence = 110 + index);
  const authority = { process: proof.fields.process, generationId: proof.fields.generationId, catalogSha256: proof.fields.catalogSha256, documentGeneration: proof.fields.documentGeneration, targetPid: proof.fields.targetPid, boot: "current-boot", baselineBoot: "current-boot", afterSequence: 105, records: [proof, ...rows.map((row) => row.record)] };
  return { proof, rows, authority, geometry };
}
test("no-op locale capture accepts newest independent same-boot enqueue identity retained before baseline", () => {
  const { rows, authority, geometry } = queuedLayout();
  layoutCapture(rows, "en", geometry, authority);
});

test("actual ordinary diagnostic JSON decodes safe progress and binds the selected serial", () => {
  const stdout = readFileSync(new URL("../../testdata/emulator-cycle64/diagnostics-stdout.txt", import.meta.url), "utf8");
  assert.deepEqual(vehicleArmed(diagnosticBody(stdout, "emulator-5562")), { fev: { status: "present", value: false }, pw: { status: "present", value: false } });
  const value = JSON.parse(stdout.split("\n").find((line) => line.startsWith("{"))!);
  for (const changed of [{ ...value, schemaVersion: 2 }, { ...value, deviceSerial: "foreign" }, { ...value, body: false }, { ...value, stderr: null }, { ...value, passed: true }])
    assert.throws(() => diagnosticBody(JSON.stringify(changed), "emulator-5562"));
  for (const changed of [stdout + stdout, "arbitrary prefix\n" + stdout, value.body]) assert.throws(() => diagnosticBody(changed, "emulator-5562"));
});

test("root armed triad preserves absent unavailable and strict JSON boolean separately", () => {
  assert.deepEqual(vehicleArmed(state("absent", "unavailable")), { fev: { status: "absent" }, pw: { status: "unavailable" } });
  assert.deepEqual(vehicleArmed(state("false")).fev, { status: "present", value: false });
  for (const body of [state('"false"'), state('{"value":true}'), state("Permission denied"), state("true") + state("false"), "unavailable"])
    assert.throws(() => vehicleArmed(body));
});
test("fresh diagnostics reject crossed boots sequence reset duplicate and historical readback", () => {
  const old = diagnostic(10, agent, "reconcile.snapshot", "complete", { forced: "TRUE" });
  const current = diagnostic(11, agent, "reconcile.snapshot", "complete", { forced: "FALSE" });
  const baseline = { boot: "boot-a", records: [old.record] };
  const observation = { boot: "boot-a", records: [old.record, current.record], diagnostics: [old, current] };
  assert.deepEqual(freshDiagnostics(baseline, observation), [current]);
  assert.equal(vehicleRead(freshDiagnostics(baseline, observation), "fev"), false);
  assert.equal(vehicleRead(freshDiagnostics(baseline, { ...observation, diagnostics: [old] }), "fev"), null);
  assert.throws(() => freshDiagnostics(baseline, { ...observation, boot: "boot-b" }), /boot/);
  assert.throws(() => freshDiagnostics(baseline, { ...observation, records: [diagnostic(1, agent, "x", "x").record] }), /regressed/);
  assert.throws(() => sequence([old.record, old.record]), /increasing/);
});
test("warm ACC proof requires actual ordered callback OFF ON generation and retained listener", () => {
  const off = diagnostic(2, agent, "acc.observe", "changed", { source: "callback", value: 0, off_generation: 4 });
  const on = diagnostic(4, agent, "acc.observe", "changed", { source: "callback", value: 2, off_generation: 4 });
  accCycle([off, on], "fev");
  for (const rows of [[on], [on, { ...off, record: { ...off.record, sequence: 5 } }], [{ ...off, fields: { ...off.fields, source: "getter" } }, on], [off, { ...on, fields: { ...on.fields, off_generation: 5 } }], [off, diagnostic(3, agent, "acc.listener", "unregistered"), on]])
    assert.throws(() => accCycle(rows, "fev"));
});
test("PW current value accepts only completed authoritative ON2 OFF1 reads", () => {
  const read = (value: unknown, outcome = "completed") => [diagnostic(1, "vehicle-drive-pedestrian-warning", "contract.read", outcome, { value })];
  assert.equal(vehicleRead(read(1), "pw"), false);
  assert.equal(vehicleRead(read(2), "pw"), true);
  for (const rows of [read("1"), read(-1), read(1, "retrying")]) assert.equal(vehicleRead(rows, "pw"), null);
});
test("armed restoration requires ordered actual reset and restored readbacks", () => {
  const reset = diagnostic(2, agent, "reconcile.snapshot", "complete", { forced: "FALSE" });
  const restored = diagnostic(3, agent, "reconcile.snapshot", "complete", { forced: "TRUE" });
  resetRestore([reset, restored], "fev", true);
  assert.throws(() => resetRestore([restored], "fev", true));
  assert.throws(() => resetRestore([restored, { ...reset, record: { ...reset.record, sequence: 4 } }], "fev", true));
});
test("actual full layout preserves original eight roles and source radio widths without substituting for PNG review", () => {
  const { rows, authority, geometry } = queuedLayout();
  layoutCapture(rows, "en", geometry, authority);
  assert.throws(() => layoutCapture(rows.filter((row) => row.fields.role !== "checkbox.keyboard-ru.title"), "en", geometry, authority), /topology/);
  assert.throws(() => layoutCapture(rows, "ru", geometry, authority), /missing/);
  assert.throws(() => layoutCapture(rows.map((row, index) => index === 1 ? { ...row, fields: { ...row.fields, width: 120 } } : row), "en", geometry, authority), /source|geometry/);
});
test("actual truncated snapshot identity is rejected independently of the existing revision guard", () => {
  const records = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/truncated-layout-events.json", import.meta.url), "utf8"));
  const rows = records.map((record) => ({ ...JSON.parse(record.message.slice(8)), record }));
  assert.equal(rows[0].fields.snapshotIdentity.length, 256);
  assert.throws(() => JSON.parse(rows[0].fields.snapshotIdentity));
  assert.throws(() => layoutCapture(rows, "en", { RADIO_ITEM_MIN_WIDTH: 120, RADIO_ITEM_PADDING: 26 }));
  const isolated = rows.map((row) => row.event === "ui.layout.capture" ? { ...row, fields: { ...row.fields, desiredRevision: 4 } } : row);
  assert.throws(() => layoutCapture(isolated, "en", { RADIO_ITEM_MIN_WIDTH: 120, RADIO_ITEM_PADDING: 26 }), /snapshot|layout capture/);
});
test("bounded layout root proof binds lossless uint64 revision hash generation catalog and current target", () => {
  const { proof, rows, authority, geometry } = queuedLayout(), fields = rows[0].fields;
  layoutCapture(rows, "en", geometry, authority);
  assert.throws(() => layoutCapture(rows, "en", geometry), /authority missing/);
  assert.throws(() => layoutCapture(rows, "en", geometry, { ...authority, records: rows.map((row) => row.record) }), /root queued snapshot.*missing/);
  for (const changed of [
    { ...fields, snapshotIdentity: "truncated raw is never an identity" },
    { ...fields, snapshotSha256: "unavailable" }, { ...fields, snapshotSha256: fields.snapshotSha256.toUpperCase() },
    { ...fields, desiredRevision: Number(fields.desiredRevision) }, { ...fields, desiredRevision: "01777777777777777779" },
    { ...fields, desiredRevision: "18446744073709551616" }, { ...fields, desiredRevision: "1777777777777777780" },
    { ...fields, generationId: "d".repeat(64) }, { ...fields, catalogSha256: "e".repeat(64) },
  ]) assert.throws(() => layoutCapture(rows.map((row) => row.event === "ui.layout.capture" ? { ...row, fields: changed } : row), "en", geometry, authority));
  for (const changed of [
    { ...authority, documentGeneration: Number(proof.fields.documentGeneration) }, { ...authority, targetPid: "4243" },
    { ...authority, generationId: "d".repeat(64) }, { ...authority, catalogSha256: "e".repeat(64) },
    { ...authority, baselineBoot: "another-boot" }, { ...authority, afterSequence: 110 },
  ]) assert.throws(() => layoutCapture(rows, "en", geometry, changed));
});
test("canonical enqueue parser rejects wrong source malformed domains partial targets profile pairs and failed admission flags", () => {
  const { proof } = queuedLayout();
  queuedSnapshot(proof);
  queuedSnapshot({ ...proof, fields: { ...proof.fields, frames: "D+A", requestId: "18446744073709551615" } });
  for (const change of [
    (record) => { record.source = { kind: "agent", id: "ui-qinggan" }; },
    (record) => { record.source.id = "daemon"; }, (record) => { record.level = "debug"; },
    (record) => { record.component = "agent"; }, (record) => { record.message = "desired-snapshot-queue-failed"; },
    (record) => { record.fields.documentGeneration = 1777777777777777779; },
    (record) => { record.fields.documentGeneration = "01"; }, (record) => { record.fields.documentGeneration = "18446744073709551616"; },
    (record) => { record.fields.snapshotSha256 = "unavailable"; }, (record) => { record.fields.catalogSha256 = "B".repeat(64); },
    (record) => { record.fields.passed = "true"; }, (record) => { record.fields.outcome = "failed"; },
    (record) => { delete record.fields.targetStarttime; }, (record) => { record.fields.endpointEpoch = "0"; },
    (record) => { delete record.fields.effectiveLanguage; }, (record) => { record.fields.effectiveLanguage = "ru"; },
    (record) => { record.fields.sourceLanguage = "unknown"; }, (record) => { record.fields.frames = "D+A"; },
    (record) => { record.fields.requestId = "1"; },
  ]) { const changed = structuredClone(proof); change(changed); assert.throws(() => queuedSnapshot(changed)); }
});
test("root queued identity never falls back through newer conflict wrong process missing target or ambiguous bytes", () => {
  for (const change of [
    (record) => { record.fields.process = "com.foreign.setting"; },
    (record) => { record.fields.documentGeneration = "1777777777777777780"; },
    (record) => { record.fields.snapshotSha256 = "d".repeat(64); },
    (record) => { record.fields.targetPid = "4243"; },
    (record) => { record.fields.profileLanguage = "ru"; record.fields.effectiveLanguage = "ru"; },
    (record) => { delete record.fields.profileLanguage; delete record.fields.effectiveLanguage; },
    (record) => { delete record.fields.targetPid; delete record.fields.targetStarttime; delete record.fields.endpointEpoch; },
  ]) { const f = queuedLayout(); change(f.proof); assert.throws(() => layoutCapture(f.rows, "en", f.geometry, f.authority)); }
  for (const [sequence, field] of [[109, "snapshotSha256"], [199, "snapshotSha256"], [199, "endpointEpoch"]] as const) {
    const f = queuedLayout(), changed = structuredClone(f.proof); changed.sequence = sequence;
    changed.fields[field] = field === "endpointEpoch" ? "2" : "d".repeat(64);
    f.authority.records.push(changed); f.authority.records.sort((a, b) => a.sequence - b.sequence);
    assert.throws(() => layoutCapture(f.rows, "en", f.geometry, f.authority), /differs|conflicts|ambiguous/);
  }
  const f = queuedLayout();
  f.authority.records.splice(1, 0, { ...structuredClone(f.proof), sequence: 101, fields: { ...f.proof.fields, snapshotSha256: "d".repeat(64) } });
  assert.throws(() => layoutCapture(f.rows, "en", f.geometry, f.authority), /differs|ambiguous/);
});
test("locale observer accepts fresh compatible render and full compose while rejecting stale or malformed render", () => {
  const fields = { effectiveLanguage: "en", defaultLocale: "en-US", resourceLocale: "en-US", elapsed_ms: 192 };
  const compose = diagnostic(1, "ui-qinggan", "ui.compose", "complete", fields), render = diagnostic(2, "ui-qinggan", "ui.render", "complete", fields);
  assert.equal(renderLocale([compose, render]).event, "ui.render");
  assert.equal(renderLocale([compose]).event, "ui.compose");
  assert.throws(() => renderLocale(freshDiagnostics({ boot: "a", records: [render.record] }, { boot: "a", records: [render.record], diagnostics: [render] })), /missing/);
  for (const changed of [{ ...render, outcome: "failed" }, { ...render, fields: { ...fields, elapsed_ms: "192" } }, { ...render, fields: { ...fields, defaultLocale: null } }, { ...render, fields: { ...fields, extra: "pass" } }]) assert.throws(() => renderLocale([compose, changed]), /malformed/);
});
const scan = () => [
  diagnostic(1, "launcher-apps", "catalog.scan", "started", { generation: 5 }),
  diagnostic(2, "launcher-apps", "catalog.candidate", "identity-observed", { package_name: "oem.app", uid: 1000, flags: 129, application_uid: false }),
  diagnostic(3, "launcher-apps", "catalog.candidate", "skipped-non-application-uid", { package_name: "oem.app", uid: 1000, flags: 129 }),
  diagnostic(4, "launcher-apps", "catalog.candidate", "identity-observed", { package_name: "ru.voboost.tests.launcherfixture", uid: 10010, flags: 0, application_uid: true }),
  diagnostic(5, "launcher-apps", "catalog.candidate", "qualified", { package_name: "ru.voboost.tests.launcherfixture", uid: 10010, flags: 0 }),
  diagnostic(6, "launcher-apps", "catalog.scan", "complete", { generation: 5 }),
];
test("fresh catalog scan binds actual UID flags and classifications rather than package-name guesses", () => {
  assert.deepEqual(catalogScan(scan()), { excluded: ["oem.app"], qualified: ["ru.voboost.tests.launcherfixture"] });
  assert.throws(() => catalogScan(scan().slice(1)), /complete/);
  assert.throws(() => catalogScan(scan().filter((row) => row.outcome !== "skipped-non-application-uid")), /classification/);
  assert.throws(() => catalogScan([...scan().slice(0, -1), diagnostic(5.5, "launcher-apps", "catalog.candidate", "qualified", { package_name: "oem.app" }), scan().at(-1)!]), /admitted/);
});
test("OEM feature readback scopes shared switch identity to actual vehicle feature parent", () => {
  const nodes = parseUi('<hierarchy><node package="com.qinggan.app.vehiclesetting" bounds="[0,0][1920,720]"><node resource-id="com.qinggan.app.vehiclesetting:id/fragment_safe_hum_vsp" package="com.qinggan.app.vehiclesetting" bounds="[500,200][1800,400]"><node resource-id="com.qinggan.app.vehiclesetting:id/hint_switch_button" package="com.qinggan.app.vehiclesetting" checkable="true" checked="true" bounds="[510,210][600,260]" /></node><node package="com.qinggan.app.vehiclesetting" checkable="true" checked="false" bounds="[500,500][600,550]" /></node></hierarchy>');
  assert.equal(oemFeature(nodes, "pw").checked, true);
  nodes[2].enabled = false;
  assert.equal(oemFeature(nodes, "pw").checked, true);
  assert.throws(() => oemFeature(nodes, "fev"), /parent/);
});

test("full published topology rejects self-count omissions geometry clipping and section gaps", () => {
  const f = queuedLayout();
  assert.equal(topology.panelTargets.length, 27);
  for (const mutate of [
    (rows) => rows.filter((r) => r.event !== "ui.layout.section"),
    (rows) => rows.filter((r) => r.event !== "ui.layout.geometry"),
    (rows) => rows.map((r) => r.event === "ui.layout.section" && r.fields.index === 1 ? { ...r, fields: { ...r.fields, gapBefore: 24 } } : r),
    (rows) => rows.map((r) => r.event === "ui.layout" && r.fields.role === "checkbox.font-cyrillic.title" ? { ...r, fields: { ...r.fields, ellipsis: 1 } } : r),
    (rows) => rows.map((r) => r.event === "ui.layout.geometry" ? { ...r, fields: { ...r.fields, parentWidth: 1 } } : r),
    (rows) => rows.map((r) => r.event === "ui.layout.geometry" ? { ...r, fields: { ...r.fields, class: "caller.TextView" } } : r),
    (rows) => rows.map((r) => r.event === "ui.layout.capture" ? { ...r, fields: { ...r.fields, count: 8 } } : r),
  ]) assert.throws(() => layoutCapture(mutate(structuredClone(f.rows)), "en", f.geometry, f.authority));
  assert.throws(() => consumeLayoutCapture(f.rows, "en", f.geometry, f.authority), /topology missing/);
  assert.throws(() => layoutCapture(f.rows, "en", f.geometry, f.authority, topology, "dialog", "pre-click"), /missing/);
  for (const role of ["language", "startup"]) {
    const rows = structuredClone(f.rows), target = rows.find((r) => r.event === "ui.layout" && r.fields.role === role);
    target.fields.width += 1;
    assert.throws(() => layoutCapture(rows, "en", f.geometry, f.authority));
  }
});
test("dialog requires separately owned pre-click confirm and cancel capture", () => {
  const f = queuedLayout(), capture = structuredClone(f.rows[0]);
  capture.fields = { ...capture.fields, surface: "dialog", phase: "pre-click", count: 2, sections: 0 };
  const rows = [capture];
  for (const spec of topology.dialogTargets) {
    rows.push(diagnostic(0, "ui-qinggan", "ui.layout", "measured", { captureId: capture.fields.captureId, role: spec.role, value: spec.value, width: 300, min_width: 0, padding_left: 20, padding_right: 20, caption_px: 200, top: 0, bottom: 70, lines: 1, ellipsis: 0 }));
    rows.push(diagnostic(0, "ui-qinggan", "ui.layout.geometry", "measured", { captureId: capture.fields.captureId, role: spec.role, value: spec.value, height: 70, left: 0, right: 300, parentWidth: 1380, class: spec.class, textSize: 32, paddingTop: 0, paddingBottom: 0, layoutWidth: 260 }));
  }
  layoutCapture(rows, "en", f.geometry, f.authority, topology, "dialog", "pre-click");
  assert.throws(() => layoutCapture(rows.filter((r) => r.fields.role !== "dialog.confirm"), "en", f.geometry, f.authority, topology, "dialog", "pre-click"), /topology/);
  assert.throws(() => layoutCapture(rows, "en", f.geometry, f.authority, topology, "panel", "after-layout"), /missing/);
});

test("an earlier incomplete actual capture cannot hide behind a later complete capture", () => {
  const f = queuedLayout(), earlier = structuredClone(f.rows);
  for (const row of earlier) row.fields.captureId = "earlier";
  earlier[0].fields.count = 8;
  assert.throws(() => layoutCapture([...earlier, ...f.rows], "en", f.geometry, f.authority), /topology/);
});

test("actual section body and checkbox switch geometry cannot be omitted or replaced by captions", () => {
  const f = queuedLayout();
  for (const event of ["ui.layout.section.body", "ui.layout.checkbox"]) {
    assert.throws(() => layoutCapture(f.rows.filter((row) => row.event !== event), "en", f.geometry, f.authority), /topology/);
    const row = f.rows.find((row) => row.event === event);
    assert.throws(() => layoutCapture([...f.rows, row], "en", f.geometry, f.authority), /topology/);
    assert.throws(() => layoutCapture(f.rows.map((candidate) => candidate === row ? { ...row, fields: { ...row.fields, captureId: "historical" } } : candidate), "en", f.geometry, f.authority), /topology/);
  }
  for (const [event, field, value] of [
    ["ui.layout.section.body", "paddingTop", 39], ["ui.layout.section.body", "paddingBottom", 1],
    ["ui.layout.section.body", "firstChildTop", 39], ["ui.layout.section.body", "lastChildBottom", 101],
    ["ui.layout.section.body", "class", "caller.LinearLayout"], ["ui.layout.section.body", "top", 0],
    ["ui.layout.checkbox", "height", 43], ["ui.layout.checkbox", "parentPaddingTop", 1],
    ["ui.layout.checkbox", "parentTop", 0], ["ui.layout.checkbox", "section", "language"],
    ["ui.layout.checkbox", "rowBottom", 0],
  ]) {
    const rows = structuredClone(f.rows), row = rows.find((row) => row.event === event);
    row.fields[field] = value;
    assert.throws(() => layoutCapture(rows, "en", f.geometry, f.authority), /body|checkbox/);
  }
  for (const key of ["SECTION_TOP", "SECTION_BOTTOM", "CHECKBOX_HEIGHT", "CHECKBOX_CONTENT_TOP"]) {
    const geometry = { ...f.geometry }; delete geometry[key];
    assert.throws(() => layoutCapture(f.rows, "en", geometry, f.authority), /body|checkbox/);
  }
});

test("agent toggle readiness waits for fresh Settings and selected authority", () => {
  const before = toggleResident("1791665325816374295", true);
  const after = toggleResident("1791665325816374296", false);
  assert.equal(agentToggleReady(before, before, "font-cyrillic", false), false);
  const waiting = structuredClone(after); waiting.injections[0].state = "waiting";
  assert.equal(agentToggleReady(before, waiting, "font-cyrillic", false), false);
  assert.equal(agentToggleReady(before, after, "font-cyrillic", false), true);
  assert.equal(agentToggleReady(before, after, "font-cyrillic", true), false);
  assert.equal(agentToggleReady(after, toggleResident("1791665325816374297", true), "font-cyrillic", true), true);
  const failed = structuredClone(after); failed.injections[0].state = "failed";
  assert.throws(() => agentToggleReady(before, failed, "font-cyrillic", false), /Settings failed/);
});

test("agent toggle readiness rejects foreign kit epochs and imprecise revisions", () => {
  const before = toggleResident("1791665325816374295", true);
  for (const key of ["releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "startupMode"]) {
    const after = toggleResident("1791665325816374296", false); (after as Record<string, unknown>)[key] = "foreign";
    assert.throws(() => agentToggleReady(before, after, "font-cyrillic", false), /identity differs/);
  }
  const numeric = toggleResident("1791665325816374296", false);
  (numeric.injections[0] as Record<string, unknown>).documentGeneration = Number("1791665325816374296");
  assert.throws(() => agentToggleReady(before, numeric, "font-cyrillic", false), /revision differs/);
  assert.throws(() => agentToggleReady(before, toggleResident("18446744073709551615", false), "font-cyrillic", false), /revision differs/);
  const duplicate = toggleResident("1791665325816374296", false); duplicate.injections.push(duplicate.injections[0]);
  assert.throws(() => agentToggleReady(before, duplicate, "font-cyrillic", false), /Settings identity/);
});

function toggleResident(documentGeneration: string, font: boolean) {
  return { schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: "a".repeat(64), generationId: "b".repeat(64), catalogSha256: "c".repeat(64), startupMode: "interface", state: "ready", killed: false, panic: false,
    injections: [{ id: "settings", process: "com.qinggan.app.setting", state: "active", documentGeneration }, ...(font ? [{ id: "font-cyrillic", process: "com.qinggan.app.setting", state: "active", documentGeneration }] : [])] };
}

test("CLI138 ordinary diagnostics validate the safe one-shot observation", () => {
  const base = { schemaVersion: 1, deviceSerial: "emulator-5566", body: "bounded body", stderr: "" };
  const digest = "a".repeat(64);
  const success = { operation: "inspect-operation", execution: "adb-shell-sh-c", success: true, exitCode: 0, stdoutBytes: 767, stdoutSha256: digest, stderrBytes: 0, stderrSha256: digest, failure: null };
  const transport = { operation: "inspect-operation", execution: "adb-shell-sh-c", success: false, class: "transport-failed", errorBytes: 10, errorSha256: digest };
  const failure = { ...success, success: false, exitCode: 1, failure: `ADB inspect-operation failed (exit Some(1)): class=unrecognized metadata=unavailable stdout_bytes=767 stdout_sha256=${digest} stderr_bytes=0 stderr_sha256=${digest}` };
  for (const inspectOperation of [success, transport, failure]) assert.equal(diagnosticBody(JSON.stringify({ ...base, inspectOperation }), base.deviceSerial), base.body);
  for (const inspectOperation of [null, [], {}, { ...success, unknown: true }, { ...success, operation: "restore" }, { ...success, execution: "shell" }, { ...success, stdoutBytes: -1 }, { ...success, stdoutSha256: "secret" }, { ...success, failure: "private output" }, { ...success, exitCode: 1 }, { ...transport, success: true }, { ...transport, errorBytes: 1.5 }, { ...failure, failure: failure.failure.replace("stdout_bytes=767", "stdout_bytes=1") }]) assert.throws(() => diagnosticBody(JSON.stringify({ ...base, inspectOperation }), base.deviceSerial));
});
