import { readFileSync, realpathSync } from "node:fs";
import { runtimeLogger } from "./runtime-log.ts";
import { createHash } from "node:crypto";
import { isAbsolute } from "node:path";
import Ajv2020 from "ajv/dist/2020.js";
import schema from "../../release/emulator-runtime-evidence.schema.json" with { type: "json" };
import plan from "../../release/beta1-functional-plan.json" with { type: "json" };
import { functionalEvidence } from "../../release/emulator-gate.ts";
import { requiredSubchecksPassed } from "./full-semantic.ts";
import { visualChecks } from "./visual-review.ts";

const validate = new Ajv2020({ strict: false, allErrors: true }).compile(schema);
const text = (value: unknown) => String(value ?? "not recorded").replaceAll("\\", "\\\\").replaceAll("[", "\\[").replaceAll("]", "\\]").replaceAll("*", "\\*").replaceAll("_", "\\_").replaceAll("\r", " ").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("|", "&#124;").replaceAll("\n", " ").replaceAll("`", "&#96;");
const link = (path: string) => {
  if (!isAbsolute(path) || /[\x00-\x1f\x7f]/u.test(path)) throw new Error("artifact link requires an absolute path without control characters");
  const target = path.replaceAll("%", "%25").replaceAll("<", "%3C").replaceAll(">", "%3E").replaceAll("#", "%23").replaceAll("?", "%3F").replaceAll("\\", "%5C");
  const label = text(path);
  return `[${label}](<${target}>)`;
};
const bounded = (value: unknown) => text(typeof value === "string" ? value.slice(0, 500) : JSON.stringify(value)?.slice(0, 500));
export function gateReport(e: any, source: string): string {
  if (!validate(e)) throw new Error(`invalid schema3 evidence: ${JSON.stringify(validate.errors)}`);
  if (e.cases.length !== plan.cases.length || plan.cases.some((row, i) => row.number !== e.cases[i].number || row.id !== e.cases[i].id)) throw new Error("required 27 case identities differ");
  const actions = new Map<number, any>();
  const failures: string[] = [];
  let omitted = 0;
  for (const action of e.actions) {
    if (actions.has(action.id) || BigInt(action.finishedMonotonicNs) < BigInt(action.startedMonotonicNs)) throw new Error("duplicate action or reversed timing");
    actions.set(action.id, action);
    for (const artifact of action.artifacts) {
      const bytes = readFileSync(artifact.path);
      if (createHash("sha256").update(bytes).digest("hex") !== artifact.sha256) throw new Error(`artifact bytes differ: ${artifact.path}`);
      if (artifact.kind === "stdout" && bytes.length <= 1024 * 1024) {
        let receipt: any;
        try { receipt = JSON.parse(bytes.toString("utf8")); } catch { continue; }
        if (receipt?.schema !== 1 || receipt.command !== "verify" || receipt.ok !== false || !Array.isArray(receipt.report?.checks)) continue;
        for (const check of receipt.report.checks) {
          if (check?.passed !== false || typeof check.id !== "string") continue;
          if (failures.length === 50) { omitted++; continue; }
          failures.push(`- Action ${action.id}, case ${text(action.caseNumber)}: ${bounded(check.id)} — ${bounded(check.message ?? "check failed")}; expected ${bounded(check.expected)}, actual ${bounded(check.actual)}. ${link(artifact.path)}`);
        }
      }
    }
  }
  for (const row of e.cases) {
    if (row.actions.some((id: number) => !actions.has(id))) throw new Error("missing referenced action");
    if (row.status === "passed" && !requiredSubchecksPassed(row)) throw new Error("passed case contains incomplete subchecks");
    if (new Set(row.subchecks.map((s: any) => s.id)).size !== row.subchecks.length) throw new Error("duplicate subcheck");
    if (row.status === "passed" && visualChecks(row.number).some(v => !row.subchecks.some((s: any) => s.id === v.id && s.status === "passed"))) throw new Error("passed case missing visual review");
  }
  functionalEvidence(e);
  const complete = e.preparation.status === "passed" && e.fixtureCleanup.status === "passed" && e.cases.every((r: any) => r.status === "passed") && e.boots.length > 0 && e.boots.every((b: any) => b.status === "passed");
  if (e.passed !== complete) throw new Error("overall verdict differs from required gates");
  const rows = ["# Voboost emulator functional report", "", `Evidence: ${link(source)}`, `Invocation: ${text(e.invocationId)}; release: ${text(e.releaseIdentity)}; schema: 3.`, `Runtime: ${text(JSON.stringify(e.runtime))}; Voyemu profile: ${text(e.voyemuProfile)}.`, `Recorded overall result: ${e.passed ? "passed" : "not passed"}. Production, vehicle and publication proof: false.`, "", "This report reads retained evidence only. Host tests and staged changes do not establish emulator acceptance. Durations below are sums of recorded action elapsed times; overlapping actions may overlap in time and these are not case wall-clock durations.", "", "## Artifact identity", ""];
  for (const [key, value] of Object.entries(e.identity)) rows.push(`- ${key}: ${text(value)}`);
  rows.push("", `Preparation: ${text(e.preparation.status)} (${text(e.preparation.kind)}); ${text(e.preparation.reason)}; boot ${text(e.preparation.bootId)}.`, `Fixture cleanup: ${text(e.fixtureCleanup.status)}; ${text(e.fixtureCleanup.reason)}.`, "", "## All required cases", "", "| Case | Stable ID | Result | Recorded action time | Visual review |", "| --- | --- | --- | --- | --- |");
  for (const row of e.cases) {
    const used = row.actions.map((id: number) => actions.get(id));
    const ns = used.reduce((sum: bigint, a: any) => sum + BigInt(a.finishedMonotonicNs) - BigInt(a.startedMonotonicNs), 0n);
    const visual = visualChecks(row.number);
    const pending = visual.filter(v => !row.subchecks.some((s: any) => s.id === v.id && s.status === "passed"));
    rows.push(`| ${row.number} | ${text(row.id)} | ${row.status} | ${used.length ? `${ns / 1000000n} ms` : "not recorded"} | ${visual.length ? pending.length ? "pending" : "recorded passed" : "not required"} |`);
  }
  if (failures.length) rows.push("", "## Retained Voyemu verification failures", "", "These are recorded failed checks from hash-verified stdout artifacts; they do not change the gate verdict.", "", ...failures, ...(omitted ? [`${omitted} additional failed checks omitted from this summary; see linked artifacts.`] : []));
  for (const row of e.cases) {
    rows.push("", `## Case ${row.number}: ${text(row.id)} — ${row.status}`, "", text(row.reason || "No reason recorded."));
    for (const check of row.subchecks) rows.push(`- ${text(check.id)}: ${check.status}; ${text(check.reason)}. ${check.artifacts.map(link).join(" ")}`);
    if (row.actions.length) rows.push("", "<details>", "<summary>Recorded action details</summary>", "");
    for (const id of row.actions) {
      const a = actions.get(id);
      rows.push(`- Action ${id}: exit ${text(a.exitCode)}, timeout ${a.timedOut}, signal ${text(a.signal)}, overflow ${a.overflow}; context ${text(JSON.stringify(a.context))}; ${a.artifacts.map((v: any) => `${text(v.kind)} ${link(v.path)} SHA256 ${v.sha256}`).join("; ")}`);
    }
    if (row.actions.length) rows.push("", "</details>");
  }
  rows.push("", "## Boots", "");
  for (const b of e.boots) rows.push(`- ${text(b.id)}: ${b.status}; before ${text(b.before)}; after ${text(b.after)}; verified ${text(b.verifiedBoot)}; ${text(b.reason)}; ${b.screenshot ? link(b.screenshot) : "screenshot not recorded"}.`);
  rows.push("", "Automated: packaged CLI/Voyemu dispatcher, named observations, identity and boot checks. Visual: actual glyphs, spacing, clipping and OEM icons require the existing retained visual-review evidence; missing reviews stay pending. Source snapshots and owning host JUnit reports, when retained, are linked through the action artifacts above.", "");
  return rows.join("\n");
}
if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)) {
  try {
    if (process.argv.length !== 3) throw new Error("usage: gate-report.ts /absolute/emulator-runtime-evidence.json");
    const source = realpathSync(process.argv[2]);
    process.stdout.write(gateReport(JSON.parse(readFileSync(source, "utf8")), source));
  } catch (error) { runtimeLogger("gate-report").error(error); process.exitCode = 2; }
}
