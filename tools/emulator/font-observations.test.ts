import { strictEqual, throws } from "node:assert";
import test from "node:test";
import { fontCapture, fontDiagnosticOnly } from "./font-observations.ts";
import type { AgentDiagnostic } from "./installer-logs.ts";
import type { UiNode } from "./installer-ui.ts";

function fixture() {
  const editor = { packageName: "com.pateonavi.naviapp", focused: true, className: "android.widget.EditText", resource: "com.pateonavi.naviapp:id/search", text: "Москва", bounds: [10, 20, 210, 60] } as UiNode;
  let sequence = 10;
  const row = (kind: string, fields: Record<string, unknown>): AgentDiagnostic => ({ agent: "font-cyrillic", event: `font.visual.${kind}`, outcome: "captured", fields: { capture_id: "1:1", epoch: 1, ...fields }, record: { schemaVersion: 1, sequence: sequence++, timestamp: "2026-10-09T00:00:00Z", source: { kind: "agent", id: "font-cyrillic" }, level: "info", component: "agent", message: "", fields: {} } });
  const rows = [
    row("view", { activity_class: "com.pateonavi.naviapp.SearchActivity", view_class: editor.className, view_id: editor.resource, text_length: 6, script: "cyrillic", attached: true, focused: true, layout_width: 200, layout_height: 40, spanned: true }),
    row("layout", { accessibility_class: editor.className, screen_x: 10, screen_y: 20, width: 200, height: 40, line_count: 1, ellipsis_count: 0, ellipsis_scan_complete: true }),
    row("paint", { provenance: "corrected", loader: "asset-regular", source_sha256: "b90b145194f1206b3d515e05d2bc2f27f2411aa0b6897f61755d7b812fe083c1", output_sha256: "4f03e27101265730adf4634746350eb44aef725e8d8601a48a366a03cf8b6bd0", text_size: 24, scale_x: 1, letter_spacing: 0, skew_x: 0, fake_bold: false }),
    ...["cyrillic", "yo", "latin"].map((sample_id) => row("sample", { sample_id, width: 50, bounds_left: 0, bounds_right: 50, bounds_top: -20, bounds_bottom: 3 })),
  ];
  return { editor, rows };
}

test("structural actual Paint capture binds six parts to the focused editor but supplies no endpoint or visual proof", () => {
  const { rows, editor } = fixture();
  strictEqual(fontCapture(rows, editor).length, 6);
});
test("font capture rejects incomplete, duplicate, cross-epoch and unrelated parts", () => {
  for (const mutate of [
    (rows: AgentDiagnostic[]) => rows.pop(),
    (rows: AgentDiagnostic[]) => rows.push(rows[1]),
    (rows: AgentDiagnostic[]) => rows[2].fields.epoch = 2,
    (rows: AgentDiagnostic[]) => rows[2].fields.capture_id = "1:2",
    (rows: AgentDiagnostic[]) => rows[5].fields.sample_id = "yo",
    (rows: AgentDiagnostic[]) => rows[0].fields.user_text = "private",
  ]) {
    const { rows, editor } = fixture(); mutate(rows); throws(() => fontCapture(rows, editor));
  }
});
test("font capture rejects wrong UI identity, focus, text length, script and screen bounds", () => {
  for (const mutate of [
    (rows: AgentDiagnostic[]) => rows[0].fields.text_length = 5,
    (rows: AgentDiagnostic[]) => rows[0].fields.focused = false,
    (rows: AgentDiagnostic[]) => rows[0].fields.script = "non-cyrillic",
    (rows: AgentDiagnostic[]) => rows[0].fields.view_id = "unknown",
    (rows: AgentDiagnostic[]) => rows[1].fields.screen_x = 11,
    (rows: AgentDiagnostic[]) => rows[1].fields.ellipsis_scan_complete = false,
    (rows: AgentDiagnostic[]) => rows[1].fields.ellipsis_count = 1,
  ]) {
    const { rows, editor } = fixture(); mutate(rows); throws(() => fontCapture(rows, editor));
  }
  const { rows, editor } = fixture(); throws(() => fontCapture(rows, { ...editor, packageName: "untrusted" }));
});
test("font capture rejects unregistered provenance, wrong bytes and malformed metrics", () => {
  for (const [part, key, value] of [[2, "provenance", "unknown"], [2, "output_sha256", "0".repeat(64)], [2, "scale_x", 0], [2, "text_size", NaN], [2, "letter_spacing", Infinity], [3, "width", -1], [3, "bounds_top", 10]] as const) {
    const { rows, editor } = fixture(); rows[part].fields[key] = value; throws(() => fontCapture(rows, editor));
  }
});

import { actualFont, type FontAuthority } from "./font-observations.ts";
function authenticated() {
  const { rows, editor } = fixture();
  const endpoint = { process: "com.pateonavi.naviapp", targetPid: "42", targetStarttime: "123", endpointEpoch: "9", generationId: "a".repeat(64), catalogSha256: "b".repeat(64) };
  const native = { ...endpoint, bootId: "boot-1", daemonPid: "21", daemonStarttime: "9007199254740993", desiredDocumentGeneration: "9007199254740995" };
  for (const row of rows) row.record.fields = { ...native };
  const queue = { ...rows[0].record, sequence: 1, source: { kind: "daemon" }, component: "runtime", message: "desired-snapshot-queued", fields: { ...endpoint, documentGeneration: "5", snapshotSha256: "c".repeat(64), frames: "D", sourceLanguage: "ru" } };
  const authority: FontAuthority = { ...endpoint, daemonPid: native.daemonPid, daemonStarttime: native.daemonStarttime, desiredDocumentGeneration: native.desiredDocumentGeneration, boot: "boot-1", baselineBoot: "boot-1", afterSequence: 5, records: [queue, ...rows.map((row) => row.record)], activityClass: "com.pateonavi.naviapp.SearchActivity" };
  return { rows, editor, authority };
}

test("actual font binds fresh Paint to immutable native endpoint, root registration and actual Activity without claiming applied snapshot", () => {
  const { rows, editor, authority } = authenticated();
  strictEqual(actualFont(rows, editor, authority).length, 6);
  // An observer lifecycle epoch is deliberately independent of the native endpoint epoch.
  strictEqual(rows[0].fields.epoch, 1);
  strictEqual(authority.endpointEpoch, "9");
  authority.records[0].fields.documentGeneration = "999";
  strictEqual(actualFont(rows, editor, authority).length, 6);
});
test("actual font rejects stale PID, lifetime, native epoch, generation, catalog and untrusted process", () => {
  for (const [key, value] of [["targetPid", "43"], ["targetStarttime", "124"], ["endpointEpoch", "10"], ["generationId", "d".repeat(64)], ["catalogSha256", "e".repeat(64)], ["process", "untrusted.app"]]) {
    const { rows, editor, authority } = authenticated();
    rows[2].record.fields[key] = value;
    throws(() => actualFont(rows, editor, authority));
  }
});
test("actual font rejects missing tuple, crossboot, stale sequence, unrelated archive, focus and conflicting root registration", () => {
  for (const mutate of [
    (a: ReturnType<typeof authenticated>) => a.rows[0].record.fields = {},
    (a: ReturnType<typeof authenticated>) => a.authority.boot = "boot-2",
    (a: ReturnType<typeof authenticated>) => a.authority.afterSequence = 10,
    (a: ReturnType<typeof authenticated>) => a.authority.records = a.authority.records.slice(0, -1),
    (a: ReturnType<typeof authenticated>) => a.authority.activityClass = "another.Activity",
    (a: ReturnType<typeof authenticated>) => a.authority.records[0].fields.targetPid = "43",
    (a: ReturnType<typeof authenticated>) => a.authority.records.push({ ...a.authority.records[0], sequence: 20, fields: { ...a.authority.records[0].fields, endpointEpoch: "10" } }),
    (a: ReturnType<typeof authenticated>) => a.rows[0].record.fields.applied = true,
  ]) {
    const a = authenticated(); mutate(a); throws(() => actualFont(a.rows, a.editor, a.authority));
  }
});

test("actual font rejects stale observer epoch independently of the native epoch", () => {
  const { rows, editor, authority } = authenticated();
  const lifecycle = { ...rows[0], event: "font.visual.lifecycle", outcome: "awaiting-resume", fields: { epoch: 2 }, record: { ...rows[0].record, sequence: 9 } };
  rows.unshift(lifecycle);
  authority.records.splice(1, 0, lifecycle.record);
  throws(() => actualFont(rows, editor, authority), /lifecycle/);
});

test("Paint Java subclasses bind through their actual accessibility class without losing view identity", () => {
  const { rows, editor } = fixture();
  rows[0].fields.view_class = "com.pateo.lightnavi.CustomInput";
  strictEqual(fontCapture(rows, editor).length, 6);
  rows[1].fields.accessibility_class = "android.widget.Button";
  throws(() => fontCapture(rows, editor));
});

test("only the six exact Paint identity diagnostics are optional", () => {
  const inventory: Array<[number, string]> = [[9, "actual-cyrillic-font-provenance"], ...["moscow", "yo", "mixed-prefix", "mixed-latin", "mixed-yo"].map(prefix => [19, prefix + "-font-provenance"] as [number, string])];
  for (const [number, id] of inventory) { strictEqual(fontDiagnosticOnly(number, id), true); strictEqual(fontDiagnosticOnly(number + 1, id), false); strictEqual(fontDiagnosticOnly(number, id + "-unknown"), false); }
  for (const id of ["actual-cyrillic-input", "visual-font-cyrillic", "moscow-and-yo-codepoints", "mixed-ru-en-codepoints", "visual-navi-input-spacing"]) { strictEqual(fontDiagnosticOnly(9, id), false); strictEqual(fontDiagnosticOnly(19, id), false); }
});

// Native agent_log.rs stamps these ten fields; legacy six-field fixtures are invalid.
test("actual font rejects native boot, daemon lifetime, desired revision and unknown fields", () => {
  for (const [key, value] of [["bootId", "other-boot"], ["daemonPid", "22"], ["daemonStarttime", "9007199254740992"], ["desiredDocumentGeneration", "9007199254740994"], ["unknown", "extra"]]) {
    const a = authenticated(); a.rows[2].record.fields[key] = value;
    throws(() => actualFont(a.rows, a.editor, a.authority));
  }
  for (const key of ["bootId", "daemonPid", "daemonStarttime", "desiredDocumentGeneration"]) {
    const a = authenticated(); delete a.rows[2].record.fields[key];
    throws(() => actualFont(a.rows, a.editor, a.authority));
  }
  for (const value of ["01", "-1", "18446744073709551616", 9007199254740992]) {
    const a = authenticated(); a.authority.desiredDocumentGeneration = value as string;
    throws(() => actualFont(a.rows, a.editor, a.authority));
  }
});

test("captured native ten-field envelope passes with independently supplied root authority", () => {
  const a = authenticated();
  // Real endpoint envelope from the 216 Navi capture; Paint remains explicitly
  // qualified fixture data, not a claim that the captured unknown Paint passed.
  const native = { process: "com.pateonavi.naviapp", targetPid: "4924", targetStarttime: "2114", endpointEpoch: "6", generationId: "e6c006b1833b77ae7c211b7362ee4a28d1251150b04aad56a7b9790691053c57", catalogSha256: "d0d28cffc4150323ccf1ea1884775ce9ce03fb4323a8de2ac1b86d4da15b717b", bootId: "5dd22730-e620-4aef-bc7e-a068c383fb48", daemonPid: "1686", daemonStarttime: "155", desiredDocumentGeneration: "1792234810427238298" };
  for (const row of a.rows) row.record.fields = { ...native };
  Object.assign(a.authority, native, { boot: native.bootId, baselineBoot: native.bootId });
  Object.assign(a.authority.records[0].fields, { process: native.process, targetPid: native.targetPid, targetStarttime: native.targetStarttime, endpointEpoch: native.endpointEpoch, generationId: native.generationId, catalogSha256: native.catalogSha256 });
  strictEqual(actualFont(a.rows, a.editor, a.authority).length, 6);
  a.rows[2].fields.provenance = "unknown";
  throws(() => actualFont(a.rows, a.editor, a.authority), /provenance unavailable/);
});
