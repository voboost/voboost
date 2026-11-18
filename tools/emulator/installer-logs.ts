import { spawnSync } from "node:child_process";
import { TextDecoder } from "node:util";

const LIMIT = 400 * 1024 * 1024;
const MANIFEST_LIMIT = 64 * 1024;
const TAR_LIMIT = LIMIT + MANIFEST_LIMIT + 4096;
const ARCHIVE_LIMIT = TAR_LIMIT + 64 * 1024;
const NAMES = ["manifest.json", "records.jsonl", "context.json"];
const decoder = new TextDecoder("utf-8", { fatal: true });

export type LogRecord = {
  schemaVersion: number;
  sequence: number;
  timestamp: string;
  source: { kind: string; id?: string };
  level: string;
  component: string;
  message: string;
  fields: Record<string, unknown>;
};
export type AgentDiagnostic = {
  record: LogRecord;
  agent: string;
  event: string;
  outcome: string;
  fields: Record<string, unknown>;
};

function object(value: unknown): value is Record<string, any> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
function octal(header: Buffer, offset: number, length: number): number {
  const field = header.subarray(offset, offset + length).toString("latin1");
  if (!/^ *[0-7]+[ \0]*$/.test(field)) throw new Error("invalid log TAR numeric field");
  const value = Number.parseInt(field.trim(), 8);
  if (!Number.isSafeInteger(value)) throw new Error("oversized log TAR numeric field");
  return value;
}

/** Host verification only: decode all XZ streams, then validate every USTAR byte. */
function entries(bytes: Buffer): Map<string, Buffer> {
  if (bytes.length < 24 || bytes.length > ARCHIVE_LIMIT) throw new Error("invalid bounded log XZ archive");
  if (bytes[7] === 0) throw new Error("log XZ integrity check missing");
  const env = { ...process.env };
  delete env.XZ_OPT;
  delete env.XZ_DEFAULTS;
  const decoded = spawnSync("xz", ["--decompress", "--stdout", "--format=xz", "--memlimit-decompress=128MiB"], {
    input: bytes,
    env,
    maxBuffer: TAR_LIMIT,
    timeout: 30_000,
    killSignal: "SIGKILL",
  });
  if (decoded.error || decoded.status !== 0 || decoded.signal)
    throw new Error("log XZ integrity or decoding failed; an installed xz decoder is required");
  const tar = decoded.stdout;
  if (tar.length > TAR_LIMIT || tar.length % 512 !== 0) throw new Error("invalid bounded log TAR size");
  let at = 0;
  let payload = 0;
  const result = new Map<string, Buffer>();
  while (at + 512 <= tar.length) {
    const header = tar.subarray(at, at + 512);
    if (header.every(byte => byte === 0)) {
      if (result.size !== NAMES.length || tar.length - at < 1024 || !tar.subarray(at).every(byte => byte === 0))
        throw new Error("missing entries or hidden log TAR end content");
      return result;
    }
    if (result.size === NAMES.length) throw new Error("extra log TAR entry");
    const checksum = header.reduce((sum, byte, index) => sum + (index >= 148 && index < 156 ? 32 : byte), 0);
    if (checksum !== octal(header, 148, 8)) throw new Error("log TAR checksum differs");
    if (
      !header.subarray(257, 263).equals(Buffer.from("ustar\0")) ||
      !header.subarray(263, 265).equals(Buffer.from("00")) ||
      ![0, 48].includes(header[156]) ||
      header.subarray(157, 257).some(byte => byte !== 0) ||
      header.subarray(345, 512).some(byte => byte !== 0)
    )
      throw new Error("unsupported log USTAR entry");
    const nameEnd = header.indexOf(0, 0);
    if (nameEnd < 0 || nameEnd >= 100 || header.subarray(nameEnd, 100).some(byte => byte !== 0))
      throw new Error("invalid log TAR name");
    const name = decoder.decode(header.subarray(0, nameEnd));
    if (!NAMES.includes(name) || result.has(name)) throw new Error("unexpected log TAR name");
    const size = octal(header, 124, 12);
    if (name === "manifest.json" ? size > MANIFEST_LIMIT : (payload += size) > LIMIT)
      throw new Error("log TAR member size exceeds limit");
    const bodyAt = at + 512;
    const end = bodyAt + size;
    const paddedEnd = bodyAt + Math.ceil(size / 512) * 512;
    if (paddedEnd > tar.length) throw new Error("truncated log TAR member size");
    if (tar.subarray(end, paddedEnd).some(byte => byte !== 0)) throw new Error("hidden log TAR member padding");
    result.set(name, tar.subarray(bodyAt, end));
    at = paddedEnd;
  }
  throw new Error("missing log TAR end blocks");
}

export function parseInstallerLogs(
  bytes: Buffer,
  expected: { serial: string; version: string; manifestSha256: string },
): { records: LogRecord[]; diagnostics: AgentDiagnostic[]; collectedAt: string } {
  const files = entries(bytes);
  const parse = (name: string) => JSON.parse(decoder.decode(files.get(name)!));
  const manifest = parse("manifest.json");
  const context = parse("context.json");
  const recordsBytes = files.get("records.jsonl")!;
  const envelope = manifest?.hostEnvelope;
  const bundleStatus = context?.diagnosticBundle?.completeness;
  if (
    manifest?.schemaVersion !== 1 ||
    manifest.format !== "VBOOSTLOG1" ||
    JSON.stringify(manifest.files) !== JSON.stringify(["records.jsonl", "context.json"]) ||
    manifest.header?.schemaVersion !== 1 ||
    manifest.header.recordsLength !== recordsBytes.length ||
    manifest.header.contextLength !== files.get("context.json")!.length ||
    envelope?.deviceSerial !== expected.serial ||
    envelope.installerVersion !== expected.version ||
    bundleStatus === "partial" || bundleStatus === "failed" ||
    (envelope.completeness !== "verified-stream" &&
      (envelope.completeness !== "complete" || bundleStatus !== "complete")) ||
    typeof envelope.collectedAtUtc !== "string" ||
    !Number.isFinite(Date.parse(envelope.collectedAtUtc)) ||
    context?.schemaVersion !== 1 ||
    context.manifestSha256 !== expected.manifestSha256 ||
    context.releaseIdentity !== expected.version ||
    context.logSource !== "root-owned-segments"
  )
    throw new Error("log archive identity or payload partition differs");
if (Object.hasOwn(context, "observedLogSession"))
  throw new Error("ADB log observation lacks an authenticated owner watermark; causal and negative checks are inconclusive");
if (Object.hasOwn(context, "currentLogSession"))
    throw new Error("Legacy log consumer cannot establish current session authority; use the Java candidate route");
  const records: LogRecord[] = [];
  const diagnostics: AgentDiagnostic[] = [];
  for (const line of decoder.decode(recordsBytes).split("\n")) {
    if (!line) continue;
    if (Buffer.byteLength(line) > 256 * 1024) throw new Error("oversized canonical log record");
    const record = JSON.parse(line);
    if (
      !object(record) ||
      record.schemaVersion !== 1 ||
      !Number.isSafeInteger(record.sequence) ||
      record.sequence < 0 ||
      typeof record.timestamp !== "string" ||
      !Number.isFinite(Date.parse(record.timestamp)) ||
      !object(record.source) ||
      typeof record.source.kind !== "string" ||
      (record.source.id !== undefined && typeof record.source.id !== "string") ||
      typeof record.level !== "string" ||
      typeof record.component !== "string" ||
      typeof record.message !== "string" ||
      !object(record.fields)
    )
      throw new Error("invalid canonical log record");
    records.push(record as LogRecord);
    if (record.message.startsWith("VOBDBG1 ")) {
      const diagnostic = JSON.parse(record.message.slice(8));
      if (
        !object(diagnostic) ||
        record.source.kind !== "agent" ||
        diagnostic.agent !== record.source.id ||
        typeof diagnostic.event !== "string" ||
        typeof diagnostic.outcome !== "string" ||
        (diagnostic.fields !== undefined && !object(diagnostic.fields))
      )
        throw new Error("agent diagnostic identity differs");
      diagnostics.push({ record: record as LogRecord, ...diagnostic, fields: diagnostic.fields ?? {} } as AgentDiagnostic);
    }
  }
  return { records, diagnostics, collectedAt: envelope.collectedAtUtc };
}
