import { writeSync } from "node:fs";

/** Host diagnostics only; machine stdout retains its existing contract. */
export type RuntimeLogRecord = {
  schemaVersion: 1;
  sequence: number;
  timestamp: string;
  source: { kind: string; id?: string };
  level: "debug" | "info" | "error";
  component: string;
  message: string;
  fields: Record<string, string>;
};

export function canonicalRecord(value: unknown): asserts value is RuntimeLogRecord {
  const row = value as RuntimeLogRecord;
  if (!row || typeof row !== "object" || Object.keys(row).sort().join() !== "component,fields,level,message,schemaVersion,sequence,source,timestamp" ||
      row.schemaVersion !== 1 || !Number.isSafeInteger(row.sequence) || row.sequence < 1 ||
      typeof row.timestamp !== "string" || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(row.timestamp) ||
      !Number.isFinite(Date.parse(row.timestamp)) || new Date(row.timestamp).toISOString() !== row.timestamp ||
      !["debug", "info", "error"].includes(row.level) || !row.source || typeof row.source !== "object" || Array.isArray(row.source) ||
      typeof row.source.kind !== "string" || !row.source.kind ||
      Object.keys(row.source).some(key => !["kind", "id"].includes(key)) ||
      (row.source.id !== undefined && (typeof row.source.id !== "string" || !row.source.id)) ||
      typeof row.component !== "string" || !row.component || typeof row.message !== "string" ||
      !row.fields || typeof row.fields !== "object" || Array.isArray(row.fields) ||
      Object.values(row.fields).some(field => typeof field !== "string")) throw new Error("invalid canonical runtime log record");
}

export function runtimeLogger(id: string, write = (line: string) => { writeSync(2, line); }, clock = () => new Date()): {
  emit: (level: RuntimeLogRecord["level"], message: string, fields?: Record<string, string>) => RuntimeLogRecord;
  error: (failure: unknown) => RuntimeLogRecord;
} {
  let sequence = 0;
  const emit = (level: RuntimeLogRecord["level"], message: string, fields: Record<string, string> = {}) => {
    const record: RuntimeLogRecord = { schemaVersion: 1, sequence: ++sequence, timestamp: clock().toISOString(),
      source: { kind: "host", id }, level, component: "emulator-gate", message, fields };
    canonicalRecord(record);
    try { write(JSON.stringify(record) + "\n"); } catch { /* A broken diagnostic sink must not replace the action failure. */ }
    return record;
  };
  return { emit, error: failure => emit("error", String(failure).slice(0, 4096), failure instanceof Error
    ? { error_type: failure.name, stack: (failure.stack ?? "").slice(0, 8192) } : {}) };
}
