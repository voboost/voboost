import { canonicalRecord, type RuntimeLogRecord } from "./runtime-log.ts";
import type { ProcessResult } from "../../release/emulator-gate.ts";

export function installerRecord(line: string): RuntimeLogRecord {
  const row: unknown = JSON.parse(line);
  // Inspect original keys after grammar validation, before JSON.parse overwrites can grant retry authority.
  const tokens = line.match(/"(?:\\.|[^"\\])*"|[{}\[\]:,]|[^\s{}\[\]:,]+/gu) ?? [];
  const stack: (Set<string> | null)[] = [];
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token === "{") stack.push(new Set());
    else if (token === "[") stack.push(null);
    else if (token === "}" || token === "]") stack.pop();
    else if (token.startsWith('"') && tokens[i + 1] === ":") {
      const key: string = JSON.parse(token), keys = stack.at(-1);
      if (!keys || keys.has(key)) throw new Error("duplicate installer diagnostic field");
      keys.add(key);
    }
  }
  canonicalRecord(row);
  if (row.source.kind !== "installer" || !["desktop", "cli"].includes(row.source.id ?? "") || row.component !== "installer")
    throw new Error("installer canonical diagnostic identity differs");
  return row;
}

/** Installer progress is diagnostic stderr; arm/completion receipts remain stdout protocols. */
export function installerRebootProgress(): (chunk: string) => number[] {
  let pending = "", previous = 0, source = "";
  return chunk => {
    pending += chunk;
    if (Buffer.byteLength(pending) > 8 * 1024 * 1024) throw new Error("installer diagnostic line exceeds bound");
    const lines = pending.split(/\r?\n/);
    pending = lines.pop()!;
    const reboots: number[] = [];
    for (const line of lines) {
      const row = installerRecord(line);
      if (row.sequence <= previous || (source && source !== row.source.id)) throw new Error("installer diagnostic sequence or source differs");
      previous = row.sequence; source = row.source.id!;
      const reboot = /^Restarting the head unit \(reboot ([12])\)\. Waiting for reconnection; install will continue automatically\.$/.exec(row.message);
      if (reboot && row.level === "info" && Object.keys(row.fields).length === 0) reboots.push(Number(reboot[1]));
    }
    return reboots;
  };
}

const ROOT_GUARD = "boot-observation failure reason=guard-root remote_exit=73 stdout_bytes=0 stderr_bytes=36";

/** Decode one failed invocation, retaining its protocol stdout and rejecting mixed/unknown diagnostics. */
export function installerDiagnostic(result: ProcessResult, serial?: string): { stderr: string; connection: boolean; stdoutEmpty: boolean } | null {
  const lines = result.stderr.split(/\r?\n/).filter(Boolean);
  if (!lines.some(line => line.trimStart().startsWith("{"))) {
    // Retained pre-JSONL evidence has its own closed connection envelope.
    const stdout = result.stdout.trimEnd().split(/\r?\n/);
    const timestamp = /^\[([0-9]{2}):([0-9]{2}):([0-9]{2})\.([0-9]{3}) INFO\] /;
    const connection = serial !== undefined && /^[A-Za-z0-9._:-]+$/.test(serial) && stdout.length === 2 && stdout.every(line => {
      const match = timestamp.exec(line);
      return !!match && Number(match[1]) < 24 && Number(match[2]) < 60 && Number(match[3]) < 60;
    }) && stdout[0].replace(timestamp, "") === "Connecting to the head unit over USB" && stdout[1].replace(timestamp, "") === `Connected to the head unit: ${serial}`;
    if (result.stdout.includes("{")) return null;
    return { stderr: result.stderr, connection, stdoutEmpty: result.stdout.trim() === "" };
  }
  if (result.stdout !== "" || !result.stderr.endsWith("\n") || result.stderr.split(/\r?\n/).slice(0, -1).some(line => !line)) return null;
  let previous = 0, source = "", raw = "";
  const rows: RuntimeLogRecord[] = [];
  try {
    for (const line of lines) {
      if (line === ROOT_GUARD && !raw && !rows.some(row => row.level === "error")) { raw = line + "\n"; continue; }
      const row = installerRecord(line);
      if (row.sequence <= previous || (source && source !== row.source.id) || Object.keys(row.fields).length) return null;
      previous = row.sequence; source = row.source.id!; rows.push(row);
    }
  } catch { return null; }
  const error = rows.at(-1);
  if (!error || error.level !== "error" || rows.slice(0, -1).some(row => row.level === "error")) return null;
  const prefix = rows.slice(0, -1);
  let connection = false;
  if (prefix.length) {
    if (prefix[0].level !== "info" || prefix[0].message !== "Connecting to the head unit over USB") return null;
    if (prefix.length > 1) {
      const connected = prefix.at(-1)!;
      if (connected.level !== "info" || !["Connected to the head unit", ...(serial ? [`Connected to the head unit: ${serial}`] : [])].includes(connected.message)) return null;
      const detail = prefix.slice(1, -1);
      if (detail.length && !(detail.length === 1 && serial && detail[0].level === "debug" && detail[0].message === `Connected to the head unit: ${serial}`)) return null;
      connection = true;
    }
  }
  return { stderr: raw + error.message + "\n", connection, stdoutEmpty: true };
}
