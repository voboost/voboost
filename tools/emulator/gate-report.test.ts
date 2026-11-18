import test from "node:test";
import assert from "node:assert/strict";
import plan from "../../release/beta1-functional-plan.json" with { type: "json" };
import { gateReport } from "./gate-report.ts";
import { evidence } from "./gate-report-fixture.ts";
test("reports all 27 stable cases and missing time without inventing runtime pass", () => {
  const report = gateReport(evidence(), "/evidence.json");
  for (const row of plan.cases) assert.ok(report.includes(`| ${row.number} | ${row.id} | not-run | not recorded |`));
  assert.match(report, /Recorded overall result: not passed/);
});
test("failure and unavailable remain distinct and visual review remains pending", () => {
  const e = evidence();
  for (const [index, status] of [[0, "failed"], [8, "unavailable"]] as const) Object.assign(e.cases[index], { status, reason: "actual observation", subchecks: [{ id: "terminal-observation", status, reason: "actual observation", artifacts: [] }] });
  const report = gateReport(e, "/evidence.json");
  assert.match(report, /cli-install-first \| failed/);
  assert.match(report, /font-cyrillic \| unavailable \| not recorded \| pending/);
});
test("schema and missing/duplicate case failures close the report", () => {
  const e = evidence(); e.schemaVersion = 2;
  assert.throws(() => gateReport(e, "/evidence.json"), /invalid schema3/);
  e.schemaVersion = 3; e.cases.pop();
  assert.throws(() => gateReport(e, "/evidence.json"), /invalid schema3|required 27/);
});
test("fixture cleanup rejects plan-only label and APK fields", () => {
  for (const key of ["label", "apk"]) {
    const e = evidence(); e.fixtureCleanup[key] = plan.fixture[key];
    assert.throws(() => gateReport(e, "/evidence.json"), /invalid schema3/);
  }
});
test("a forged overall pass and missing visual subcheck are rejected", () => {
  const e = evidence(); e.passed = true;
  assert.throws(() => gateReport(e, "/evidence.json"));
  e.passed = false; Object.assign(e.cases[8], { status: "passed", subchecks: [{ id: "observation", status: "passed", reason: "claimed", artifacts: [] }] });
  assert.throws(() => gateReport(e, "/evidence.json"), /visual review/);
});
test("recorded action duration is derived from monotonic receipts, reversed clocks rejected", () => {
  const e = evidence();
  e.actions.push({ id: 1, caseNumber: 1, startedMonotonicNs: "1000000", finishedMonotonicNs: "5000000", executable: "/fixture-cli", args: ["--status"], exitCode: 1, signal: null, timedOut: false, overflow: false, artifacts: [] });
  Object.assign(e.cases[0], { status: "failed", actions: [1], subchecks: [{ id: "actual-status", status: "failed", reason: "exit 1", artifacts: [] }] });
  assert.match(gateReport(e, "/evidence.json"), /cli-install-first \| failed \| 4 ms/);
  e.actions[0].finishedMonotonicNs = "0";
  assert.throws(() => gateReport(e, "/evidence.json"), /reversed timing/);
});

import { mkdirSync, mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { createHash } from "node:crypto";
import { resolve } from "node:path";
test("absolute file links preserve spaces and escape destination and label injection", () => {
  const report = gateReport(evidence(), "/project/a [x](evil) >#?.json");
  assert.match(report, /\(<\/project\/a /);
  assert.ok(report.includes("%3E%23%3F.json>"));
  assert.ok(report.includes("\\[x\\]"));
  assert.ok(!report.includes("file://"));
  for (const source of ["relative.json", "https://evil/x", "/path\n<script>", "/path\0x"]) assert.throws(() => gateReport(evidence(), source), /absolute path/);
});
function withReceipt(run: (e: any, path: string) => void, receipt: any) {
  const work = resolve(import.meta.dirname, "../../../local-builds-new/report-tests"); mkdirSync(work, { recursive: true });
  const dir = mkdtempSync(resolve(work, "receipt-"));
  const path = resolve(dir, "stdout.json"); const bytes = JSON.stringify(receipt); writeFileSync(path, bytes);
  const e = evidence(); e.actions.push({ id: 1308, caseNumber: 14, startedMonotonicNs: "1", finishedMonotonicNs: "2", executable: "/node", args: [], exitCode: 6, signal: null, timedOut: false, overflow: false, artifacts: [{ path, kind: "stdout", sha256: createHash("sha256").update(bytes).digest("hex") }] });
  Object.assign(e.cases[13], { status: "failed", actions: [1308], reason: "Voyemu process failed", subchecks: [{ id: "terminal", status: "failed", reason: "exit 6", artifacts: [] }] });
  try { run(e, path); } finally { rmSync(dir, { recursive: true }); }
}
test("hash-verified retained verify failures precede collapsed details without changing verdict", () => {
  withReceipt((e, path) => {
    const r = gateReport(e, "/evidence.json");
    assert.ok(r.indexOf("Retained Voyemu verification failures") < r.indexOf("## Case 1:"));
    assert.match(r, /routing:second:SecondScreenNavigationBar/);
    assert.ok(r.includes('Window "SecondScreenNavigationBar" not found'));
    assert.match(r, /<details>/); assert.match(r, /Recorded overall result: not passed/);
    writeFileSync(path, "{}"); assert.throws(() => gateReport(e, "/evidence.json"), /artifact bytes differ/);
  }, { schema: 1, command: "verify", ok: false, report: { checks: [{ id: "routing:second:SecondScreenNavigationBar", passed: false, expected: 1, actual: null, message: 'Window "SecondScreenNavigationBar" not found in dumpsys window' }] } });
});
test("summary bounds untrusted text and check count, and does not render injected links", () => {
  withReceipt(e => {
    const r = gateReport(e, "/evidence.json");
    assert.ok(r.includes("10 additional failed checks omitted"));
    assert.ok(!r.includes("[evil](https://evil)"));
    assert.ok(!r.includes("x".repeat(501)));
  }, { schema: 1, command: "verify", ok: false, report: { checks: Array.from({ length: 60 }, () => ({ id: "bad", passed: false, message: "[evil](https://evil)\n<script>" + "x".repeat(1000) })) } });
});
test("non-verify or successful receipts cannot inject a failed-check summary", () => {
  for (const changes of [{ command: "other" }, { ok: true }, { schema: 2 }]) withReceipt(e => assert.ok(!gateReport(e, "/evidence.json").includes("Retained Voyemu verification failures")), { schema: 1, command: "verify", ok: false, report: { checks: [{ id: "forged", passed: false }] }, ...changes });
});

test("Mac overlay source digest is strict while retained pre-overlay evidence remains readable", () => {
  const e = evidence();
  assert.doesNotThrow(() => gateReport(e, "/evidence.json"));
  for (const malformed of ["", "a".repeat(63), "A".repeat(64), "g".repeat(64), 644, null, { sha256: "a".repeat(64), mode: 420 }, ["a".repeat(64)]]) {
    const invalid = structuredClone(e); invalid.identity.macOverlaySources = malformed;
    assert.throws(() => gateReport(invalid, "/evidence.json"), /invalid schema3/);
  }
  const historic = structuredClone(e); delete historic.identity.macOverlaySources;
  assert.doesNotThrow(() => gateReport(historic, "/evidence.json"));
  const foreign = structuredClone(e); foreign.identity.macOverlaySourceMode = 420;
  assert.throws(() => gateReport(foreign, "/evidence.json"), /invalid schema3/);
});

test("passed case retains optional unavailable Paint diagnostic without granting full acceptance", () => {
 const e=evidence(); const row=e.cases.find((r:any)=>r.number===9);
 Object.assign(row,{status:"passed",subchecks:["actual-cyrillic-input","visual-font-cyrillic"].map(id=>({id,status:"passed",reason:"actual observation",artifacts:[]})).concat([{id:"actual-cyrillic-font-provenance",status:"unavailable",reason:"optional diagnostic",artifacts:[]}])});
 assert.match(gateReport(e,"/evidence.json"),/Recorded overall result: not passed/);
 const duplicate=structuredClone(e);duplicate.cases[8].subchecks.push({...duplicate.cases[8].subchecks[2]}); assert.throws(()=>gateReport(duplicate,"/evidence.json"),/incomplete subchecks|duplicate/);
 const missing=structuredClone(e);missing.cases[8].subchecks=missing.cases[8].subchecks.filter((s:any)=>s.id!=="visual-font-cyrillic");assert.throws(()=>gateReport(missing,"/evidence.json"),/visual review/);
 const wrong=structuredClone(e);Object.assign(wrong.cases[0],{status:"passed",subchecks:[{...row.subchecks[2]}]});assert.throws(()=>gateReport(wrong,"/evidence.json"),/incomplete subchecks/);
});
