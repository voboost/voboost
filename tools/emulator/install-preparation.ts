import type { ProcessResult } from "../../release/emulator-gate.ts";
import { installerRecord } from "./installer-diagnostics.ts";

type Receipt = Record<string, any>;
const KEYS = ["schemaVersion", "deviceSerial", "sequence", "context", "step", "attempt", "transactionId", "manifestSha256", "journal", "bootId", "phase", "rebootCount", "statusSequence", "journalSha256", "result", "exitCode", "error", "errorTruncated", "stdoutSha256", "stdoutBytes", "stderrSha256", "stderrBytes"];
const CONTEXTS = ["preflight", "staging", "admission", "retry", "observation", "post-reboot", "completion", "failure-recovery"];
const STEPS = ["wait-device", "root", "remount", "relax-first-phase", "verify-write-access", "enforce-selinux"];
const PHASES = ["prepare", "policy-backup", "runtime-apply", "app", "policy-apply", "reboot-first", "agents", "reboot-second", "runtime-verify", "inventory-commit", "cleanup", "complete"].map((value) => `install-${value}`);
const sha = (value: unknown) => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const uint64 = (value: unknown) => typeof value === "string" && /^(0|[1-9][0-9]{0,19})$/.test(value) && BigInt(value) <= 18446744073709551615n;
const count = (value: unknown, max = 0xffffffff) => Number.isSafeInteger(value) && Number(value) >= 0 && Number(value) <= max;

/** DEBUG canonical receipts are emitted only after the existing typed operation returns. */
export function preparationReceipts(result: ProcessResult, serial: string, manifestSha256: string): Receipt[] {
  if (result.timedOut || result.signal || result.overflow || result.retained?.overflow || Buffer.byteLength(result.stdout) + Buffer.byteLength(result.stderr) > 8 * 1024 * 1024) throw new Error("incomplete installer preparation transcript");
  if (result.stdout.includes("Install preparation receipt: ")) throw new Error("installer preparation marker is not canonical stderr DEBUG");
  if (result.stderr.includes("Install preparation receipt: ") && !result.stderr.endsWith("\n")) throw new Error("incomplete installer preparation record");
  const rows: Receipt[] = [];
  let previousLogSequence = 0, logSource = "";
  for (const line of result.stderr.split(/\r?\n/).filter((line) => line.includes("Install preparation receipt: "))) {
    const record = installerRecord(line);
    const match = /^Install preparation receipt: (\{.*\})$/.exec(record.message);
    if (!match || record.level !== "debug" || Object.keys(record.fields).length || record.sequence <= previousLogSequence || (logSource && logSource !== record.source.id)) throw new Error("installer preparation canonical DEBUG identity or sequence differs");
    previousLogSequence = record.sequence; logSource = record.source.id!;
    const row = JSON.parse(match[1], (key, value, context) => ["sequence", "statusSequence"].includes(key) && value !== null ? (typeof value === "number" ? context.source : "invalid") : value);
    if (!row || Array.isArray(row) || JSON.stringify(Object.keys(row).sort()) !== JSON.stringify([...KEYS].sort()) || row.schemaVersion !== 1 || row.deviceSerial !== serial || !uint64(row.sequence) || BigInt(row.sequence) < 1n || (rows.length && BigInt(row.sequence) !== BigInt(rows.at(-1)!.sequence) + 1n) || !CONTEXTS.includes(row.context) || !STEPS.includes(row.step) || !count(row.attempt, 30) || row.attempt < 1 || !["success", "failed", "transport-failed"].includes(row.result) || typeof row.errorTruncated !== "boolean" || (row.exitCode !== null && (!Number.isInteger(row.exitCode) || row.exitCode < -2147483648 || row.exitCode > 2147483647))) throw new Error("installer preparation schema, serial or sequence differs");
    if ((row.transactionId !== null && row.transactionId !== manifestSha256) || (row.manifestSha256 !== null && row.manifestSha256 !== manifestSha256) || (row.context === "preflight" && (row.transactionId !== null || row.manifestSha256 !== null)) || (row.context === "staging" && (row.transactionId !== manifestSha256 || row.manifestSha256 !== manifestSha256)) || (row.context !== "staging" && row.context !== "failure-recovery" && row.manifestSha256 !== null)) throw new Error("installer preparation action manifest or transaction differs");
    const reportKeys = ["journal", "bootId", "phase", "rebootCount", "statusSequence", "journalSha256"], report = reportKeys.filter((key) => row[key] !== null).length;
    if (report !== 0 && (report !== reportKeys.length || row.transactionId !== manifestSha256 || row.journal !== `recovery/${manifestSha256}/install-journal.json` || typeof row.bootId !== "string" || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(row.bootId) || !PHASES.includes(row.phase) || !count(row.rebootCount) || !uint64(row.statusSequence) || BigInt(row.statusSequence) < 1n || !sha(row.journalSha256))) throw new Error("installer preparation previous accepted report malformed");
    if (report !== 0) {
      const phase = BigInt(PHASES.indexOf(row.phase) + 1), status = BigInt(row.statusSequence);
      if (status < phase * 3n - 2n || status > phase * 3n) throw new Error("installer preparation previous report phase sequence differs");
    }
    if (["preflight", "staging", "admission"].includes(row.context) && report !== 0) throw new Error("installer preparation claims an unaccepted initial boot");
    const outputs = ["stdoutSha256", "stdoutBytes", "stderrSha256", "stderrBytes"];
    if (row.result === "transport-failed" ? row.exitCode !== null || outputs.some((key) => row[key] !== null) : !sha(row.stdoutSha256) || !sha(row.stderrSha256) || !count(row.stdoutBytes, Number.MAX_SAFE_INTEGER) || !count(row.stderrBytes, Number.MAX_SAFE_INTEGER)) throw new Error("installer preparation typed output differs");
    if (row.result === "success" ? row.exitCode !== 0 || row.error !== null || row.errorTruncated : typeof row.error !== "string" || Buffer.byteLength(row.error) > 1024 || (row.result === "failed" && row.exitCode === 0)) throw new Error("installer preparation exit or real error differs");
    rows.push(row);
  }
  return rows;
}

export function preparationProof(rows: Receipt[]): { readiness: boolean; enforcing: boolean; journal: boolean } {
  const wanted = ["wait-device", "root", "wait-device", "remount", "relax-first-phase", "verify-write-access"];
  const readiness = rows.some((row, index) => {
    const run = rows.slice(index, index + wanted.length);
    return run.length === wanted.length && ["staging", "admission"].includes(row.context) && run.every((value, offset) => value.context === row.context && value.transactionId === row.transactionId && value.manifestSha256 === row.manifestSha256 && value.step === wanted[offset] && value.result === "success" && value.exitCode === 0 && (offset < 3 ? value.attempt === row.attempt : value.attempt === 1));
  }) && !rows.some((row) => ["remount", "relax-first-phase", "verify-write-access"].includes(row.step) && row.result !== "success");
  const enforcing = rows.some((row) => row.step === "enforce-selinux" && row.result === "success" && row.exitCode === 0 && ["observation", "post-reboot", "completion", "retry"].includes(row.context));
  const journal = rows.some((row) => row.journal !== null && row.result === "success");
  return { readiness, enforcing, journal };
}
