import { startupMilestonesShape } from "./startup-milestones.ts";
import { RESIDENT_STATUS_BYTES, currentEndpointShape, stableCurrentAccepted, type CurrentEndpointAuthority } from "./current-endpoint.ts";
import type { LayoutTopology } from "./settings-copy.ts";
import type { AgentDiagnostic, LogRecord } from "./installer-logs.ts";
import type { UiNode } from "./installer-ui.ts";

export const VEHICLE = {
  fev: "vehicle-drive-forced-ev",
  pw: "vehicle-drive-pedestrian-warning",
} as const;
export type Vehicle = keyof typeof VEHICLE;
export type Armed = { status: "present"; value: boolean } | { status: "absent" | "unavailable" };

/** Read only the fixed resident-status file, never unrelated pretty-printed diagnostics. */
export function inspectionResident(body: string): any {
  const marker = "\nresident-status:\n/data/voboost/status.json\n";
  const sections = body.split(marker);
  if (sections.length !== 2) throw new Error("resident status section missing or duplicated");
  const text = sections[1].split("\n")[0];
  if (!text || Buffer.byteLength(text) + 1 > RESIDENT_STATUS_BYTES) throw new Error("resident status line is missing or oversized");
  return parseResidentDocument(text);
}

/** Parse complete resident JSON while preserving exact legacy injection revisions. */
export function parseResidentDocument(text: string): any {
  if (!text || Buffer.byteLength(text) + 1 > RESIDENT_STATUS_BYTES) throw new Error("resident status line is missing or oversized");
  const legacyRevisions = new WeakMap<object, Record<string,string>>();
  const value = JSON.parse(text, function (key, item, context) {
    if (["documentGeneration", "lifecycleGeneration"].includes(key) && typeof item === "number" && context?.source) legacyRevisions.set(this, { ...legacyRevisions.get(this), [key]:context.source });
    return item;
  });
  if (!value || Array.isArray(value) || value.schemaVersion !== 1 || !Array.isArray(value.injections)) throw new Error("resident status document differs");
  for (const row of value.injections) if (row && typeof row === "object" && legacyRevisions.has(row)) Object.assign(row, legacyRevisions.get(row));
  return value;
}

/** A mutation may replace Settings; only its newer confirmed projection permits UI readback. */
export function agentToggleReady(before: any, current: any, id: string, want: boolean): boolean {
  for (const key of ["releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "startupMode"]) {
    if (typeof before?.[key] !== "string" || before[key] !== current?.[key]) throw new Error("agent toggle resident identity differs");
  }
  for (const key of ["manifestSha256", "generationId", "catalogSha256"]) if (!digestIdentity(before[key])) throw new Error("agent toggle digest differs");
  if (before.schemaVersion !== 1 || current.schemaVersion !== 1 || current.state !== "ready" || current.killed !== false || current.panic !== false || !Array.isArray(before.injections) || !Array.isArray(current.injections)) throw new Error("agent toggle resident authority differs");
  const settings = (value: any) => {
    const rows = value.injections.filter((row: any) => row.id === "settings" && row.process === "com.qinggan.app.setting");
    if (rows.length !== 1) throw new Error("agent toggle Settings identity differs");
    return rows[0];
  };
  const old = settings(before), fresh = settings(current);
  const exactRevision = (value: unknown): value is string => revision(value) && BigInt(value) <= 9223372036854775807n;
  if (!exactRevision(old.documentGeneration)) throw new Error("agent toggle prior revision differs");
  if (fresh.state === "failed") throw new Error("agent toggle Settings failed");
  if (fresh.state === "waiting") return false;
  if (fresh.state !== "active" || !exactRevision(fresh.documentGeneration)) throw new Error("agent toggle current revision differs");
  if (BigInt(fresh.documentGeneration) <= BigInt(old.documentGeneration)) return false;
  const selected = current.injections.filter((row: any) => row.id === id);
  if (selected.some((row: any) => row.state === "failed")) throw new Error("agent toggle selected target failed");
  return (selected.length > 0) === want;
}

/** Ordinary diagnostics use their own serial-bound JSON envelope after human progress. */
export function diagnosticBody(stdout: string, serial: string): string {
  if (Buffer.byteLength(stdout) > 8 * 1024 * 1024) throw new Error("invalid bounded diagnostic receipt");
  const lines = stdout.split(/\r?\n/).filter((line) => line.trim() && !/^\[[0-9:. ]+ (INFO|DEBUG|WARN|ERROR)\] /.test(line));
  if (lines.at(-1) === "Diagnostics completed.") lines.pop();
  if (lines.length !== 1 || !lines[0].startsWith("{")) throw new Error("exactly one ordinary diagnostic receipt required");
  const value = JSON.parse(lines[0]);
  if (!value || Array.isArray(value) || JSON.stringify(Object.keys(value).filter((key) => key !== "inspectOperation").sort()) !== JSON.stringify(["body", "deviceSerial", "schemaVersion", "stderr"]) || value.schemaVersion !== 1 || value.deviceSerial !== serial || typeof value.body !== "string" || typeof value.stderr !== "string")
    throw new Error("ordinary diagnostic receipt identity differs");
  if (Object.hasOwn(value, "inspectOperation")) diagnosticObservation(value.inspectOperation);
  return value.body;
}

/** Validate only the producer's redacted, closed observation; never accept raw operation output. */
function diagnosticObservation(value: any): void {
  const fail = () => { throw new Error("ordinary diagnostic observation differs"); };
  const keys = (expected: string[]) => JSON.stringify(Object.keys(value).sort()) === JSON.stringify(expected.sort());
  const bytes = (item: unknown) => typeof item === "number" && Number.isSafeInteger(item) && item >= 0;
  const hash = (item: unknown) => typeof item === "string" && /^[0-9a-f]{64}$/.test(item);
  if (!value || typeof value !== "object" || Array.isArray(value) || value.operation !== "inspect-operation" || value.execution !== "adb-shell-sh-c" || typeof value.success !== "boolean") fail();
  if (Object.hasOwn(value, "class")) {
    if (!keys(["operation", "execution", "success", "class", "errorBytes", "errorSha256"]) || value.success !== false || value.class !== "transport-failed" || !bytes(value.errorBytes) || !hash(value.errorSha256)) fail();
    return;
  }
  if (!keys(["operation", "execution", "success", "exitCode", "stdoutBytes", "stdoutSha256", "stderrBytes", "stderrSha256", "failure"]) || !bytes(value.stdoutBytes) || !bytes(value.stderrBytes) || !hash(value.stdoutSha256) || !hash(value.stderrSha256) || !(value.exitCode === null || (Number.isInteger(value.exitCode) && value.exitCode >= -2147483648 && value.exitCode <= 2147483647))) fail();
  if (value.success) {
    if (value.exitCode !== 0 || value.failure !== null) fail();
    return;
  }
  if (typeof value.failure !== "string" || value.failure.length > 1024) fail();
  const exit = value.exitCode === null ? "None" : `Some(${value.exitCode})`;
  const prefix = `ADB inspect-operation failed (exit ${exit}): `;
  const suffix = ` stdout_bytes=${value.stdoutBytes} stdout_sha256=${value.stdoutSha256} stderr_bytes=${value.stderrBytes} stderr_sha256=${value.stderrSha256}`;
  if (!value.failure.startsWith(prefix) || !value.failure.endsWith(suffix)) fail();
  const summary = value.failure.slice(prefix.length, -suffix.length);
  if (!/^class=(empty|transport-closed|transport-offline|transport-absent|operation-metadata|operation-read|unrecognized) metadata=(unavailable|[0-9]{1,20}:[0-9]{1,20}:[0-9]{1,20}:[0-9]{1,20}:[0-9a-fA-F]{1,20})$/.test(summary)) fail();
}

/** The packaged diagnostic reader has already checked root ownership and mode. */
/** Closed status shape shared by post-install readiness and persistent state diagnostics. */
export function residentStatusShape(value: any): void {
  const object = (row: any, required: string[], optional: string[] = []) => {
    if (!row || Array.isArray(row) || typeof row !== "object" || required.some((key) => !Object.hasOwn(row, key)) || Object.keys(row).some((key) => !required.includes(key) && !optional.includes(key))) throw new Error("resident status closed shape differs");
  };
  const unsigned = (item: any) => typeof item === "number" && Number.isInteger(item) && item >= 0 && item <= 18446744073709551615;
  object(value, ["schemaVersion", "releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "startupMode", "state", "killed", "panic", "injections", "executors"], ["persistentVehicleState", "lastUnsafeNativeFailure", "startupMilestones", "publisher", "processTrace"]);
  if (value.schemaVersion !== 1 || typeof value.releaseIdentity !== "string" || !["manifestSha256", "generationId", "catalogSha256"].every((key) => digestIdentity(value[key])) || !["interface", "hidden", "none"].includes(value.startupMode) || typeof value.state !== "string" || typeof value.killed !== "boolean" || typeof value.panic !== "boolean" || !Array.isArray(value.injections) || !Array.isArray(value.executors)) throw new Error("resident status typed shape differs");
  for (const row of value.injections) {
    object(row, ["id", "process", "state", "reason", "details"], ["documentGeneration", "lifecycleGeneration", "currentEndpoint"]);
    if (!["id", "process", "state", "reason"].every((key) => typeof row[key] === "string") || !row.details || Array.isArray(row.details) || typeof row.details !== "object" || ["documentGeneration", "lifecycleGeneration"].some((key) => Object.hasOwn(row, key) && !(unsigned(row[key]) || revision(row[key])))) throw new Error("resident injection typed shape differs");
    if (Object.hasOwn(row, "currentEndpoint")) {
      currentEndpointShape(row.currentEndpoint);
      if (row.documentGeneration !== row.currentEndpoint.desiredDocumentGeneration) throw new Error("current endpoint resident identity differs");
    }
  }
  for (const row of value.executors) {
    object(row, ["id", "processes", "surfaces"]);
    if (typeof row.id !== "string" || ![row.processes, row.surfaces].every((items) => Array.isArray(items) && items.every((item: any) => typeof item === "string"))) throw new Error("resident executor typed shape differs");
  }
  if (Object.hasOwn(value, "publisher")) {
    const row = value.publisher;
    object(row, ["bootId", "daemonPid", "daemonStarttime", "publishedAtBootMs"]);
    const decimal = (item: any, minimum: bigint, maximum: bigint) =>
      typeof item === "string" && /^(0|[1-9][0-9]{0,19})$/.test(item) && BigInt(item) >= minimum && BigInt(item) <= maximum;
    if (typeof row.bootId !== "string" || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(row.bootId) ||
        !decimal(row.daemonPid, 2n, 2147483647n) ||
        !decimal(row.daemonStarttime, 1n, 18446744073709551615n) ||
        !decimal(row.publishedAtBootMs, 0n, 18446744073709551615n)) throw new Error("resident publisher typed shape differs");
  }
  if (Object.hasOwn(value, "processTrace")) {
    const trace = value.processTrace;
    const decimal = (item: any, maximum: bigint, minimum = 0n) =>
      typeof item === "string" && /^(0|[1-9][0-9]*)$/.test(item) && item.length <= 39 && BigInt(item) >= minimum && BigInt(item) <= maximum;
    const u64 = 18446744073709551615n;
    const integer = (item: any, minimum: number, maximum: number) => Number.isInteger(item) && item >= minimum && item <= maximum;
    const operations: Record<string, readonly string[]> = {
      "/system/bin/cmd": ["user.list", "package.list", "package.enable", "package.disable-user", "jobscheduler.cancel", "jobscheduler.get-job-state", "activity.force-stop", "activity.get-uid-state", "activity.broadcast"],
      "/system/bin/pm": ["path", "list.packages", "install"],
    };
    object(trace, ["identityScope", "dropped", "overwritten", "omitted", "records"]);
    if (!value.publisher || trace.identityScope !== "direct-child" || !decimal(trace.dropped, u64) || !decimal(trace.overwritten, u64) || !integer(trace.omitted, 0, 128) || !Array.isArray(trace.records) || trace.records.length + trace.omitted > 128 || Buffer.byteLength(JSON.stringify(trace)) > 8192) throw new Error("resident process trace typed shape differs");
    for (const row of trace.records) {
      object(row, ["operationId", "program", "operation", "parentPid", "childPid", "monotonicNs", "elapsedMs", "outcome"]);
      if (!Object.hasOwn(operations, row.program) || !operations[row.program].includes(row.operation) || !decimal(row.operationId, u64, 1n) || !integer(row.parentPid, 1, 4294967295) || String(row.parentPid) !== value.publisher.daemonPid || !integer(row.childPid, 2, 4294967295) || !(row.monotonicNs === null || decimal(row.monotonicNs, u64)) || !decimal(row.elapsedMs, 340282366920938463463374607431768211455n)) throw new Error("resident process record typed shape differs");
      if (typeof row.outcome === "string") {
        if (!["started", "error", "timeout"].includes(row.outcome)) throw new Error("resident process outcome differs");
      } else {
        const key = row.outcome && Object.hasOwn(row.outcome, "exit") ? "exit" : "signal";
        object(row.outcome, [key]);
        if (!integer(row.outcome[key], key === "exit" ? 0 : 1, key === "exit" ? 255 : 128)) throw new Error("resident process outcome differs");
      }
    }
  }
  if (Object.hasOwn(value, "startupMilestones")) {
    startupMilestonesShape(value.startupMilestones);
    if (["manifestSha256", "generationId", "catalogSha256"].some(key => value[key] !== value.startupMilestones.identity[key])) throw new Error("startup milestone resident identity differs");
  }
  if (Object.hasOwn(value, "persistentVehicleState")) {
    const row = value.persistentVehicleState;
    object(row, ["bootId", "observedAtBootMs", "forcedEv", "pedestrianWarning"]);
    if (!(row.bootId === null || typeof row.bootId === "string" && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(row.bootId)) || !(row.observedAtBootMs === null || unsigned(row.observedAtBootMs))) throw new Error("resident persistent snapshot typed shape differs");
    for (const flag of [row.forcedEv, row.pedestrianWarning]) {
      object(flag, ["outcome"], flag?.outcome === "value" ? ["value"] : []);
      if (!["value", "missing", "unavailable"].includes(flag.outcome) || flag.outcome === "value" && typeof flag.value !== "boolean") throw new Error("resident persistent triad typed shape differs");
    }
  }
  if (Object.hasOwn(value, "lastUnsafeNativeFailure")) {
    const row = value.lastUnsafeNativeFailure;
    object(row, ["process", "targetPid", "targetStarttime", "generationId", "catalogSha256", "documentGeneration", "diagnostic"]);
    if (typeof row.process !== "string" || !unsigned(row.targetPid) || row.targetPid <= 1 || row.targetPid > 2147483647 || !unsigned(row.targetStarttime) || !digestIdentity(row.generationId) || !digestIdentity(row.catalogSha256) || !(unsigned(row.documentGeneration) || revision(row.documentGeneration))) throw new Error("resident native failure typed shape differs");
    const diagnostic = (item: any, depth: number): void => {
      if (depth > 1) throw new Error("resident native failure nesting differs");
      object(item, ["result", "stage", "restore_part", "operation", "reason", "pstate_expected", "pstate_observed", "pstate_xor", "first_mismatch"], ["initial_failure"]);
      if (!["result", "stage", "restore_part", "operation", "reason"].every((key) => typeof item[key] === "string") || ["pstate_expected", "pstate_observed", "pstate_xor", "first_mismatch"].some((key) => item[key] !== null && !unsigned(item[key]))) throw new Error("resident native diagnostic typed shape differs");
      if (Object.hasOwn(item, "initial_failure")) diagnostic(item.initial_failure, depth + 1);
    };
    diagnostic(row.diagnostic, 0);
  }
}

export type PersistentVehicleAuthority = { boot: string; generationId: string; catalogSha256: string };

/** Accept a bounded root-owned daemon projection only within this boot and resident generation. */
export function persistentVehicleArmed(body: string, authority: PersistentVehicleAuthority): Record<Vehicle, Armed> {
  const marker = "persistent-vehicle-state-snapshot:\n";
  const chunks = body.split(marker);
  if (chunks.length !== 2) throw new Error("persistent vehicle snapshot section missing or duplicated");
  const lines = chunks[1].split("block-protection:\n")[0].trimEnd().split(/\r?\n/);
  if (lines.length !== 4 || lines[0] !== "source=daemon-secure-root-store-read; freshness=compare-boot-id-and-observedAtBootMs; unavailable-is-not-false") throw new Error("persistent vehicle snapshot envelope differs");
  const boot = lines[1].replace(/^current-boot-id=/, ""), uptime = lines[2].match(/^current-boot-uptime=([0-9]+\.[0-9]{2}) [0-9]+\.[0-9]{2}$/);
  if (boot !== authority.boot || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(boot) || !uptime || Buffer.byteLength(lines[3]) + 1 > RESIDENT_STATUS_BYTES) throw new Error("persistent vehicle snapshot boot authority differs");
  const status = parseResidentDocument(lines[3]), snapshot = status?.persistentVehicleState;
  residentStatusShape(status);
  if (status?.schemaVersion !== 1 || status?.state !== "ready" || status?.panic !== false || !digestIdentity(authority.generationId) || !digestIdentity(authority.catalogSha256) || status.generationId !== authority.generationId || status.catalogSha256 !== authority.catalogSha256 || !snapshot || JSON.stringify(Object.keys(snapshot).sort()) !== JSON.stringify(["bootId", "forcedEv", "observedAtBootMs", "pedestrianWarning"]) || snapshot.bootId !== boot || !Number.isSafeInteger(snapshot.observedAtBootMs) || snapshot.observedAtBootMs < 0) throw new Error("persistent vehicle snapshot resident authority differs");
  // Parse the kernel centisecond decimal exactly; binary fractional seconds
  // can round below an equal producer timestamp (e.g. 129.26).
  const [seconds, centiseconds] = uptime[1].split(".");
  const nowMs = BigInt(seconds) * 1000n + BigInt(centiseconds) * 10n;
  const age = nowMs - BigInt(snapshot.observedAtBootMs);
  // The current integer-ms time can be 9ms above its proc lower bound.
  // Expire conservatively so accepted observations remain within 5000ms;
  // keep future stamps rejected without tolerance.
  if (nowMs > BigInt(Number.MAX_SAFE_INTEGER) || age < 0n || age + 9n > 5000n) throw new Error("persistent vehicle snapshot is stale or from the future");
  const triad = (item: any): Armed => {
    if (!item || Array.isArray(item)) throw new Error("persistent vehicle snapshot triad differs");
    const keys = JSON.stringify(Object.keys(item).sort());
    if (item.outcome === "value" && keys === JSON.stringify(["outcome", "value"]) && typeof item.value === "boolean") return { status: "present", value: item.value };
    if (keys === JSON.stringify(["outcome"]) && ["missing", "unavailable"].includes(item.outcome)) return { status: item.outcome === "missing" ? "absent" : "unavailable" };
    throw new Error("persistent vehicle snapshot triad differs");
  };
  return { fev: triad(snapshot.forcedEv), pw: triad(snapshot.pedestrianWarning) };
}

export function vehicleArmed(body: string, authority?: PersistentVehicleAuthority): Record<Vehicle, Armed> {
  if (body.includes("persistent-vehicle-state-snapshot:\n")) {
    if (!authority) throw new Error("persistent vehicle snapshot independent authority missing");
    return persistentVehicleArmed(body, authority);
  }
  if (body.split("vehicle-agent-state:\n").length !== 2) throw new Error("vehicle root state section duplicated or missing");
  const section = body.split("vehicle-agent-state:\n")[1]?.split("block-protection:\n")[0];
  if (section === undefined) throw new Error("vehicle root state observation missing");
  const result = {} as Record<Vehicle, Armed>;
  for (const id of ["fev", "pw"] as Vehicle[]) {
    const key = `/data/voboost/state/${VEHICLE[id]}_${id === "fev" ? "forced-ev" : "pedestrian-warning"}-armed.json=`;
    const lines = section.split(/\r?\n/).filter((line) => line.startsWith(key));
    if (lines.length !== 1) throw new Error("vehicle root state identity duplicated or missing");
    const value = lines[0].slice(key.length);
    if (value === "absent" || value === "unavailable") result[id] = { status: value };
    else {
      const parsed = JSON.parse(value);
      if (typeof parsed !== "boolean") throw new Error("vehicle root state is not a JSON boolean");
      result[id] = { status: "present", value: parsed };
    }
  }
  return result;
}

export type VehicleAuthority = { boot: string; baselineBoot: string; generationId: string; catalogSha256: string; process: string; targetPid: string; documentGeneration?: string; targetStarttime?: string; endpointEpoch?: string };
export function rootVehicleRecord(record: LogRecord): { fields: Record<string, string>; state: Armed } {
  const required = ["agentId", "key", "operation", "outcome", "process", "targetPid", "endpointEpoch"],
    optional = ["value", "generationId", "catalogSha256", "targetStarttime", "documentGeneration", "bootId", "requestId"], fields = record.fields;
  if (JSON.stringify(Object.keys(record).sort()) !== JSON.stringify(["component", "fields", "level", "message", "schemaVersion", "sequence", "source", "timestamp"]) || record.schemaVersion !== 1 || !Number.isSafeInteger(record.sequence) || record.sequence < 0 || !Number.isFinite(Date.parse(record.timestamp)) || !record.source || JSON.stringify(Object.keys(record.source)) !== JSON.stringify(["kind"]) || record.source.kind !== "daemon" || record.level !== "info" || record.component !== "agent-state" || record.message !== "vehicle-state-observed" || !fields || Array.isArray(fields) || required.some((key) => !Object.hasOwn(fields, key)) || Object.keys(fields).some((key) => ![...required, ...optional].includes(key)) || Object.values(fields).some((value) => typeof value !== "string")) throw new Error("malformed canonical root vehicle operation receipt");
  const value = fields as Record<string, string>, vehicle = value.agentId === VEHICLE.fev ? "fev" : value.agentId === VEHICLE.pw ? "pw" : null;
  if (!vehicle || value.key !== (vehicle === "fev" ? "forced-ev-armed" : "pedestrian-warning-armed") || !["read", "write-readback"].includes(value.operation) || !["value", "missing", "unavailable"].includes(value.outcome) || !/^[A-Za-z][A-Za-z0-9_.:-]{0,254}$/.test(value.process) || !positiveIdentity(value.targetPid) || !positiveIdentity(value.endpointEpoch) || (value.operation === "read" ? !revision(value.requestId) : Object.hasOwn(value, "requestId")) || (Object.hasOwn(value, "targetStarttime") && !positiveIdentity(value.targetStarttime)) || (Object.hasOwn(value, "documentGeneration") && !revision(value.documentGeneration)) || (Object.hasOwn(value, "bootId") && !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.bootId))) throw new Error("invalid root vehicle operation identity");
  const generation = Object.hasOwn(value, "generationId"), catalog = Object.hasOwn(value, "catalogSha256");
  if (generation !== catalog || (generation && (!digestIdentity(value.generationId) || !digestIdentity(value.catalogSha256))) || (value.outcome === "value" ? !["true", "false"].includes(value.value) : Object.hasOwn(value, "value"))) throw new Error("invalid root vehicle boolean triad or resident identity");
  return { fields: value, state: value.outcome === "value" ? { status: "present", value: value.value === "true" } : { status: value.outcome === "missing" ? "absent" : "unavailable" } };
}

/** This admits actual operations, never turns historical records into a fresh query. */
export function rootVehicleState(records: LogRecord[], vehicle: Vehicle, authority: VehicleAuthority, afterSequence: number, baseline?: LogRecord[]): Armed {
  const unavailable: Armed = { status: "unavailable" };
  sequence(records);
  if (authority.boot !== authority.baselineBoot || !digestIdentity(authority.generationId) || !digestIdentity(authority.catalogSha256) || !positiveIdentity(authority.targetPid) || (authority.documentGeneration !== undefined && !revision(authority.documentGeneration))) return unavailable;
  const operations = records.filter((record) => record.message === "vehicle-state-observed").map((record) => ({ record, ...rootVehicleRecord(record) })).filter((row) => row.fields.agentId === VEHICLE[vehicle]);
  const current = operations.at(-1);
  const matches = (fields: Record<string, string>) => fields.bootId === authority.boot && fields.generationId === authority.generationId && fields.catalogSha256 === authority.catalogSha256 && fields.process === authority.process && fields.targetPid === authority.targetPid && !!fields.targetStarttime && (authority.targetStarttime === undefined || fields.targetStarttime === authority.targetStarttime) && (authority.endpointEpoch === undefined || fields.endpointEpoch === authority.endpointEpoch) && (authority.documentGeneration === undefined || fields.documentGeneration === undefined || fields.documentGeneration === authority.documentGeneration);
  if (!current || !matches(current.fields)) return unavailable;
  if (current.record.sequence > afterSequence) return current.state;
  if (!baseline) return unavailable;
  sequence(baseline);
  const read = baseline.filter((record) => record.message === "vehicle-state-observed").map((record) => ({ record, ...rootVehicleRecord(record) })).findLast((row) => row.fields.agentId === VEHICLE[vehicle]);
  if (!read || read.fields.operation !== "read" || read.state.status !== "present" || !matches(read.fields) || baseline.some((record) => record.sequence >= read.record.sequence && !records.some((row) => row.sequence === record.sequence && JSON.stringify(row) === JSON.stringify(record)))) return unavailable;
  const window = records.filter((record) => record.sequence >= read.record.sequence);
  if (window.some((record, index) => index > 0 && record.sequence !== window[index - 1].sequence + 1) || operations.some((row) => row.record.sequence >= read.record.sequence && (!matches(row.fields) || row.fields.endpointEpoch !== read.fields.endpointEpoch || row.fields.targetStarttime !== read.fields.targetStarttime || row.state.status !== "present" || row.state.value !== read.state.value))) return unavailable;
  return read.state;
}

export function sequence(records: LogRecord[]): number {
  let last = -1;
  for (const record of records) {
    if (record.sequence <= last) throw new Error("canonical log sequence is not strictly increasing");
    last = record.sequence;
  }
  return last;
}

/** Sequence freshness is valid only inside independently observed same-boot endpoints. */
export function freshDiagnostics(
  baseline: { boot: string; records: LogRecord[] },
  current: { boot: string; records: LogRecord[]; diagnostics: AgentDiagnostic[] },
): AgentDiagnostic[] {
  if (baseline.boot !== current.boot) throw new Error("diagnostic window crossed a guest boot");
  const before = sequence(baseline.records), after = sequence(current.records);
  if (after < before) throw new Error("canonical log sequence regressed");
  return current.diagnostics.filter((row) => row.record.sequence > before);
}

export function vehicleRead(rows: AgentDiagnostic[], vehicle: Vehicle): boolean | null {
  const agent = VEHICLE[vehicle];
  const candidates = rows.filter((row) => row.agent === agent);
  for (const row of candidates.toReversed()) {
    if (vehicle === "fev" && /^(activation|reconcile)\.snapshot$/.test(row.event) && row.outcome === "complete") {
      if (row.fields.forced === "TRUE") return true;
      if (row.fields.forced === "FALSE") return false;
    }
    if (vehicle === "pw" && row.event === "contract.read" && row.outcome === "completed") {
      if (row.fields.value === 1) return false;
      if (row.fields.value === 2) return true;
    }
  }
  return null;
}

/** Accepted S brackets fresh CAN; root L metadata proves the same daemon incarnation. */
export function currentVehicleRead(
  vehicle: Vehicle, beforeEndpoint: unknown, afterEndpoint: unknown,
  authority: CurrentEndpointAuthority & { process: string },
  baseline: { boot: string; records: LogRecord[] },
  current: { boot: string; records: LogRecord[]; diagnostics: AgentDiagnostic[] },
): boolean | null {
  if (baseline.boot !== authority.bootId || current.boot !== authority.bootId || !stableCurrentAccepted(beforeEndpoint, afterEndpoint, authority)) return null;
  if ([...baseline.records, ...current.records].some(record => !Number.isSafeInteger(record.sequence) || record.sequence < 0)) return null;
  const candidates = freshDiagnostics(baseline, current).filter(row => row.agent === VEHICLE[vehicle] &&
    (vehicle === "fev" ? /^(activation|reconcile)\.snapshot$/.test(row.event) && row.outcome === "complete" : row.event === "contract.read" && row.outcome === "completed"));
  if (!candidates.length) return null;
  const keys = ["process", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "desiredDocumentGeneration", "daemonPid", "daemonStarttime", "bootId"];
  if (candidates.some(row => row.record.source.kind !== "agent" || row.record.source.id !== row.agent ||
    !current.records.some(record => record === row.record) || keys.some(key => row.record.fields[key] !== authority[key]))) return null;
  return vehicleRead(candidates, vehicle);
}

export function accCycle(rows: AgentDiagnostic[], vehicle: Vehicle): void {
  const observations = rows.filter((row) => row.agent === VEHICLE[vehicle] && row.event === "acc.observe" && row.fields.source === "callback");
  const off = observations.find((row) => row.fields.value === 0 && Number.isSafeInteger(row.fields.off_generation) && Number(row.fields.off_generation) > 0);
  const on = off && observations.find((row) => row.record.sequence > off.record.sequence && row.fields.value === 2 && row.fields.off_generation === off.fields.off_generation);
  if (!off || !on) throw new Error("fresh OEM callback OFF/ON generation missing");
  if (rows.some((row) => row.agent === VEHICLE[vehicle] && row.event === "acc.listener" && row.outcome === "unregistered" && row.record.sequence > off.record.sequence && row.record.sequence < on.record.sequence))
    throw new Error("ACC listener was replaced during warm power cycle");
}

export function resetRestore(rows: AgentDiagnostic[], vehicle: Vehicle, desired: boolean): void {
  const reset = rows.find((row) => vehicleRead([row], vehicle) === !desired);
  const restored = reset && rows.find((row) => row.record.sequence > reset.record.sequence && vehicleRead([row], vehicle) === desired);
  if (!reset || !restored) throw new Error("fresh authoritative default reset then restore readback missing");
}

export type LayoutAuthority = { generationId: string; catalogSha256: string; process: string; documentGeneration: string; targetPid: string; boot: string; baselineBoot: string; afterSequence: number; records: LogRecord[] };
const digestIdentity = (value: unknown): value is string => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const revision = (value: unknown): value is string => typeof value === "string" && /^(0|[1-9][0-9]{0,19})$/.test(value) && BigInt(value) <= 18446744073709551615n;
const positiveIdentity = (value: unknown): value is string => revision(value) && BigInt(value) > 0n;

/** Only the daemon emits this metadata after successful actual D or atomic D+A queue admission. */
export function queuedSnapshot(record: LogRecord): Record<string, string> {
  const required = ["process", "generationId", "catalogSha256", "documentGeneration", "snapshotSha256", "frames", "sourceLanguage"],
    targets = ["targetPid", "targetStarttime", "endpointEpoch"], languages = ["profileLanguage", "effectiveLanguage"],
    allowed = [...required, ...targets, ...languages, "requestId"], fields = record.fields;
  if (record.schemaVersion !== 1 || !Number.isSafeInteger(record.sequence) || record.sequence < 0 || !Number.isFinite(Date.parse(record.timestamp)) || !record.source || JSON.stringify(Object.keys(record.source)) !== JSON.stringify(["kind"]) || record.source.kind !== "daemon" || record.level !== "info" || record.component !== "runtime" || record.message !== "desired-snapshot-queued" || !fields || Array.isArray(fields) || required.some((key) => !Object.hasOwn(fields, key)) || Object.keys(fields).some((key) => !allowed.includes(key)) || Object.values(fields).some((value) => typeof value !== "string")) throw new Error("malformed canonical daemon queued snapshot proof");
  const value = fields as Record<string, string>;
  if (!/^[A-Za-z][A-Za-z0-9_.:-]{0,254}$/.test(value.process) || !digestIdentity(value.generationId) || !digestIdentity(value.catalogSha256) || !digestIdentity(value.snapshotSha256) || !revision(value.documentGeneration) || !["D", "D+A"].includes(value.frames) || !["en", "ru", "zh"].includes(value.sourceLanguage)) throw new Error("malformed queued snapshot identity fields");
  const targetCount = targets.filter((key) => Object.hasOwn(value, key)).length, languageCount = languages.filter((key) => Object.hasOwn(value, key)).length;
  if ((targetCount !== 0 && targetCount !== 3) || (targetCount === 3 && targets.some((key) => !positiveIdentity(value[key]))) || (languageCount !== 0 && languageCount !== 2) || (languageCount === 2 && (!["en", "ru", "zh"].includes(value.profileLanguage) || value.effectiveLanguage !== value.profileLanguage)) || (value.frames === "D+A" ? !revision(value.requestId) : Object.hasOwn(value, "requestId"))) throw new Error("malformed queued snapshot target, language or frame identity");
  return value;
}

/** A complete compatible refresh and a full composition each expose the actual process locales. */
export function renderLocale(rows: AgentDiagnostic[]): AgentDiagnostic {
  const row = rows.findLast((row) => row.agent === "ui-qinggan" && ["ui.compose", "ui.render"].includes(row.event));
  if (!row) throw new Error("current UI locale render observation missing");
  const fields = row.fields;
  if (row.outcome !== "complete" || JSON.stringify(Object.keys(fields).sort()) !== JSON.stringify(["defaultLocale", "effectiveLanguage", "elapsed_ms", "resourceLocale"]) || !["en", "ru", "zh"].includes(String(fields.effectiveLanguage)) || [fields.defaultLocale, fields.resourceLocale].some((value) => typeof value !== "string" || !value || value.length > 256) || !Number.isSafeInteger(fields.elapsed_ms) || Number(fields.elapsed_ms) < 0) throw new Error("current UI locale render fields malformed");
  return row;
}

export function layoutCapture(rows: AgentDiagnostic[], language: string, geometry: Record<string, number>, authority?: LayoutAuthority, topology?: LayoutTopology, surface: "panel" | "dialog" = "panel", phase: "after-layout" | "pre-click" = "after-layout"): void {
  const captures = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.capture" && row.fields.effectiveLanguage === language && row.fields.surface === surface && row.fields.phase === phase);
  if (!captures.length) throw new Error("complete current source-bound layout capture missing");
  for (const capture of captures) validateLayoutCapture(rows.filter((row) => row.event === "ui.layout.capture" ? row === capture : row.fields.captureId === capture.fields.captureId), language, geometry, authority, topology, surface, phase);
}

function validateLayoutCapture(rows: AgentDiagnostic[], language: string, geometry: Record<string, number>, authority: LayoutAuthority | undefined, topology: LayoutTopology | undefined, surface: "panel" | "dialog", phase: "after-layout" | "pre-click"): void {
  const capture = rows.findLast((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.capture" && row.fields.effectiveLanguage === language && row.fields.surface === surface && row.fields.phase === phase);
  if (!capture || capture.outcome !== "measured" || JSON.stringify(Object.keys(capture.fields).sort()) !== JSON.stringify(["captureId", "catalogSha256", "count", "desiredRevision", "effectiveLanguage", "generationId", "phase", "rootWidth", "sections", "snapshotSha256", "surface"]) || !Number.isSafeInteger(capture.fields.count) || Number(capture.fields.count) <= 0 || typeof capture.fields.captureId !== "string" || !capture.fields.captureId || capture.fields.captureId.length > 64 || !digestIdentity(capture.fields.snapshotSha256) || !digestIdentity(capture.fields.generationId) || !digestIdentity(capture.fields.catalogSha256) || !revision(capture.fields.desiredRevision) || !Number.isSafeInteger(capture.fields.rootWidth) || Number(capture.fields.rootWidth) <= 0) throw new Error("complete current source-bound layout capture missing");
  if (!authority || !authority.boot || authority.boot !== authority.baselineBoot || !Number.isSafeInteger(authority.afterSequence) || capture.record.sequence <= authority.afterSequence || !digestIdentity(authority.generationId) || !digestIdentity(authority.catalogSha256) || !revision(authority.documentGeneration) || !positiveIdentity(authority.targetPid)) throw new Error("independent current layout authority missing or crossed a boot");
  sequence(authority.records);
  if (capture.fields.generationId !== authority.generationId || capture.fields.catalogSha256 !== authority.catalogSha256 || BigInt(capture.fields.desiredRevision) !== BigInt(authority.documentGeneration)) throw new Error("layout snapshot generation, catalog or current revision differs");
  const proofs = authority.records.filter((record) => record.message === "desired-snapshot-queued").map((record) => ({ record, fields: queuedSnapshot(record) })).filter((proof) => proof.fields.process === authority.process);
  const proof = proofs.findLast((proof) => proof.record.sequence < capture.record.sequence);
  if (!proof) throw new Error("independent root queued snapshot proof before current capture missing");
  if (!proof.fields.profileLanguage || !proof.fields.targetPid) throw new Error("independent queued Settings profile or target registration missing");
  const matches = (fields: Record<string, string>) => fields.generationId === authority.generationId && fields.catalogSha256 === authority.catalogSha256 && fields.documentGeneration === authority.documentGeneration && fields.snapshotSha256 === capture.fields.snapshotSha256 && fields.sourceLanguage === language && fields.profileLanguage === language && fields.effectiveLanguage === language && fields.targetPid === authority.targetPid && fields.targetStarttime === proof.fields.targetStarttime && fields.endpointEpoch === proof.fields.endpointEpoch;
  if (!matches(proof.fields)) throw new Error("latest root queued snapshot or target identity differs from current UI capture");
  if (proofs.some((candidate) => candidate.record.sequence > capture.record.sequence && !matches(candidate.fields))) throw new Error("newer root queued snapshot or target identity conflicts with captured UI");
  if (proofs.some((candidate) => candidate.fields.generationId === proof.fields.generationId && candidate.fields.catalogSha256 === proof.fields.catalogSha256 && candidate.fields.documentGeneration === proof.fields.documentGeneration && candidate.fields.targetPid === proof.fields.targetPid && candidate.fields.targetStarttime === proof.fields.targetStarttime && candidate.fields.endpointEpoch === proof.fields.endpointEpoch && candidate.fields.snapshotSha256 !== proof.fields.snapshotSha256)) throw new Error("ambiguous queued snapshot bytes at the same desired revision and target");
  const targets = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout" && row.fields.captureId === capture.fields.captureId);
  if (!topology) throw new Error("source-bound layout topology missing");
  const expected = surface === "panel" ? topology.panelTargets : topology.dialogTargets;
  const identities = (items: Array<{ role: unknown; value: unknown }>) => items.map((row) => `${row.role}:${row.value}`).sort();
  if (capture.fields.count !== expected.length || JSON.stringify(identities(targets.map((row) => row.fields as any))) !== JSON.stringify(identities(expected))) throw new Error("complete source-bound layout target topology differs");
  const measuredGeometry = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.geometry" && row.fields.captureId === capture.fields.captureId);
  if (JSON.stringify(identities(measuredGeometry.map((row) => row.fields as any))) !== JSON.stringify(identities(expected))) throw new Error("complete layout geometry topology differs");
  const sections = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.section" && row.fields.captureId === capture.fields.captureId);
  const sectionKeys = surface === "panel" ? topology.sections : [];
  if (capture.fields.sections !== sectionKeys.length || JSON.stringify(sections.map((row) => row.fields.key)) !== JSON.stringify(sectionKeys)) throw new Error("complete layout section topology differs");
  let previousBottom = 0;
  for (const [index, row] of sections.entries()) {
    const f = row.fields;
    if (row.outcome !== "measured" || JSON.stringify(Object.keys(f).sort()) !== JSON.stringify(["bottom", "captureId", "gapBefore", "height", "index", "key", "rootHeight", "top", "width"]) || ["bottom", "gapBefore", "height", "index", "rootHeight", "top", "width"].some((key) => !Number.isSafeInteger(f[key]) || Number(f[key]) < 0) || f.index !== index || f.width !== capture.fields.rootWidth || Number(f.height) <= 0 || Number(f.bottom) - Number(f.top) !== f.height || Number(f.bottom) > Number(f.rootHeight) || f.gapBefore !== Number(f.top) - previousBottom || f.gapBefore !== (index === 0 ? 0 : geometry.SECTION_GAP)) throw new Error("actual section spacing differs from bound component source");
    previousBottom = Number(f.bottom);
  }

  const bodies = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.section.body" && row.fields.captureId === capture.fields.captureId);
  const switches = rows.filter((row) => row.agent === "ui-qinggan" && row.event === "ui.layout.checkbox" && row.fields.captureId === capture.fields.captureId);
  const bodySpecs = surface === "panel" ? topology.sectionBodies : [], switchSpecs = surface === "panel" ? topology.checkboxSwitches : [];
  if (!Array.isArray(bodySpecs) || JSON.stringify(bodies.map((row) => ({ key: row.fields.key, index: row.fields.index }))) !== JSON.stringify(bodySpecs.map((row) => ({ key: row.key, index: row.index })))) throw new Error("complete section body topology differs");
  if (!Array.isArray(switchSpecs) || JSON.stringify(switches.map((row) => ({ key: row.fields.key, section: row.fields.section }))) !== JSON.stringify(switchSpecs.map((row) => ({ key: row.key, section: row.section })))) throw new Error("complete checkbox switch topology differs");
  for (const row of bodies) {
    const f = row.fields, section = sections.find((row) => row.fields.key === f.key)?.fields;
    const titled = expected.some((target) => target.role === `section.${f.key}.title`);
    if (!section || row.outcome !== "measured" || JSON.stringify(Object.keys(f).sort()) !== JSON.stringify(["bottom", "captureId", "class", "firstChildTop", "height", "index", "key", "lastChildBottom", "paddingBottom", "paddingTop", "top", "width"]) || ["bottom", "firstChildTop", "height", "index", "lastChildBottom", "paddingBottom", "paddingTop", "top", "width"].some((key) => !Number.isSafeInteger(f[key]) || Number(f[key]) < 0) || f.class !== "android.widget.LinearLayout" || f.width !== section.width || Number(f.height) <= 0 || Number(f.bottom) - Number(f.top) !== f.height || f.bottom !== section.bottom || f.top !== Number(section.top) + (titled ? geometry.SECTION_HEADER : 0) || f.paddingTop !== geometry.SECTION_TOP || f.paddingBottom !== geometry.SECTION_BOTTOM || Number(f.firstChildTop) < Number(f.paddingTop) || Number(f.lastChildBottom) < Number(f.firstChildTop) || Number(f.height) - Number(f.lastChildBottom) < Number(f.paddingBottom)) throw new Error("actual section body geometry differs from bound component source");
  }
  for (const row of switches) {
    const f = row.fields, body = bodies.find((row) => row.fields.key === f.section)?.fields;
    if (!body || row.outcome !== "measured" || JSON.stringify(Object.keys(f).sort()) !== JSON.stringify(["bottom", "captureId", "height", "key", "parentBottom", "parentPaddingTop", "parentTop", "rowBottom", "rowTop", "section", "top", "width"]) || ["bottom", "height", "parentBottom", "parentPaddingTop", "parentTop", "rowBottom", "rowTop", "top", "width"].some((key) => !Number.isSafeInteger(f[key]) || Number(f[key]) < 0) || Number(f.width) <= 0 || Number(f.width) > Number(body.width) || f.height !== geometry.CHECKBOX_HEIGHT || Number(f.bottom) - Number(f.top) !== f.height || f.parentPaddingTop !== geometry.CHECKBOX_CONTENT_TOP || Number(f.top) - Number(f.parentTop) !== geometry.CHECKBOX_CONTENT_TOP || Number(f.rowTop) < Number(body.top) + Number(body.paddingTop) || Number(f.rowBottom) > Number(body.bottom) - Number(body.paddingBottom) || Number(f.rowBottom) <= Number(f.rowTop) || Number(f.parentTop) < Number(f.rowTop) || Number(f.parentBottom) > Number(f.rowBottom) || Number(f.parentBottom) <= Number(f.parentTop) || Number(f.top) < Number(f.parentTop) || Number(f.bottom) > Number(f.parentBottom)) throw new Error("actual checkbox switch geometry differs from bound component source");
  }

  for (const target of targets) {
    const fields = target.fields;
    if (target.outcome !== "measured" || JSON.stringify(Object.keys(fields).sort()) !== JSON.stringify(["bottom", "caption_px", "captureId", "ellipsis", "lines", "min_width", "padding_left", "padding_right", "role", "top", "value", "width"])) throw new Error("actual layout target schema differs");
    const detail = measuredGeometry.find((row) => row.fields.role === fields.role && row.fields.value === fields.value)!;
    const g = detail.fields, spec = expected.find((row) => row.role === fields.role && row.value === fields.value)!;
    if (detail.outcome !== "measured" || JSON.stringify(Object.keys(g).sort()) !== JSON.stringify(["captureId", "class", "height", "layoutWidth", "left", "paddingBottom", "paddingTop", "parentWidth", "right", "role", "textSize", "value"]) || ["height", "layoutWidth", "left", "paddingBottom", "paddingTop", "parentWidth", "right"].some((key) => !Number.isSafeInteger(g[key]) || Number(g[key]) < 0) || !Number.isFinite(g.textSize) || Number(g.textSize) <= 0 || typeof g.class !== "string" || !(spec.class === "android.widget.TextView" ? ["android.widget.TextView", "com.pateo.material.widgets.BoldTextView"].includes(g.class) : g.class === spec.class) || Number(g.right) - Number(g.left) !== fields.width || g.height !== Number(fields.bottom) - Number(fields.top) || Number(fields.width) > Number(g.parentWidth) || Number(g.layoutWidth) !== Number(fields.width) - Number(fields.padding_left) - Number(fields.padding_right) || Number(g.layoutWidth) <= 0 || fields.ellipsis !== 0 || (fields.lines === 1 && Number(fields.caption_px) > Number(g.layoutWidth))) throw new Error("actual caption geometry or clipping differs");
    const radio = fields.role === "language" || fields.role === "startup";
    const action = fields.role === "uninstall.action";
    const textKey = radio ? "RADIO_TEXT" : ["language.title", "startup.title"].includes(String(fields.role)) ? "RADIO_TITLE_TEXT" : ["language.descriptionAbove", "language.description", "startup.description"].includes(String(fields.role)) ? "RADIO_DESCRIPTION_TEXT" : null;
    if (textKey && g.textSize !== geometry[textKey]) throw new Error("actual default text size differs from bound component source");
    if ((radio && (g.height !== geometry.RADIO_HEIGHT || fields.lines !== 1)) || (action && (Number(g.height) < geometry.BUTTON_HEIGHT || fields.padding_left !== geometry.BUTTON_HORIZONTAL_PADDING || fields.padding_right !== geometry.BUTTON_HORIZONTAL_PADDING))) throw new Error("actual default control geometry differs from bound component source");
    for (const name of ["width", "min_width", "padding_left", "padding_right", "caption_px", "top", "bottom", "lines", "ellipsis"])
      if (!Number.isSafeInteger(fields[name]) || Number(fields[name]) < 0) throw new Error("actual layout measurement invalid");
    if (Number(fields.width) <= 0 || Number(fields.width) > Number(capture.fields.rootWidth) || Number(fields.bottom) <= Number(fields.top) || Number(fields.lines) < 1) throw new Error("actual layout parent/height constraint differs");
    if (fields.role === "language" || fields.role === "startup") {
      const expectedWidth = Math.max(geometry.RADIO_ITEM_MIN_WIDTH, Number(fields.caption_px) + 2 * geometry.RADIO_ITEM_PADDING);
      if (fields.width !== expectedWidth) throw new Error("actual radio content width differs from bound component source");
    }
    if (fields.role === "uninstall.action" && fields.min_width !== 0) throw new Error("actual action retains an OEM minimum width");
  }
}

export function catalogScan(rows: AgentDiagnostic[]): { excluded: string[]; qualified: string[] } {
  const started = rows.findLast((row) => row.agent === "launcher-apps" && row.event === "catalog.scan" && row.outcome === "started");
  const complete = started && rows.find((row) => row.agent === "launcher-apps" && row.event === "catalog.scan" && row.outcome === "complete" && row.record.sequence > started.record.sequence && row.fields.generation === started.fields.generation);
  if (!started || !complete) throw new Error("fresh complete package scan not observed");
  const scan = rows.filter((row) => row.agent === "launcher-apps" && row.record.sequence > started.record.sequence && row.record.sequence < complete.record.sequence && row.event === "catalog.candidate");
  const excluded = new Set<string>(), qualified = new Set<string>();
  for (const row of scan.filter((row) => row.outcome === "identity-observed")) {
    const { package_name: name, uid, flags, application_uid: applicationUid } = row.fields;
    if (typeof name !== "string" || !Number.isSafeInteger(uid) || !Number.isSafeInteger(flags) || typeof applicationUid !== "boolean")
      throw new Error("package classification identity incomplete");
    const reason = !applicationUid ? "skipped-non-application-uid" : (Number(flags) & 129) !== 0 ? "skipped-system-application" : null;
    if (reason) {
      if (!scan.some((candidate) => candidate.outcome === reason && candidate.fields.package_name === name && candidate.fields.uid === uid && candidate.fields.flags === flags))
        throw new Error("system package classification outcome missing");
      if (scan.some((candidate) => candidate.outcome === "qualified" && candidate.fields.package_name === name))
        throw new Error("system package admitted to user catalog");
      excluded.add(name);
    } else if (scan.some((candidate) => candidate.outcome === "qualified" && candidate.fields.package_name === name)) qualified.add(name);
  }
  if (!excluded.size || !qualified.has("ru.voboost.tests.launcherfixture")) throw new Error("system exclusion or positive Voboost Fixture classification missing");
  return { excluded: [...excluded].sort(), qualified: [...qualified].sort() };
}

/** Resource identities are OEM-owned; visible text or historical coordinates are insufficient. */
export function oemFeature(nodes: UiNode[], vehicle: Vehicle): UiNode {
  const owner = "com.qinggan.app.vehiclesetting";
  const group = vehicle === "fev" ? "power_mode_electric_force" : "fragment_safe_hum_vsp";
  const roots = nodes.map((node, index) => ({ node, index })).filter(({ node }) => node.packageName === owner && node.resource === `${owner}:id/${group}`);
  if (roots.length !== 1) throw new Error(`unique OEM ${vehicle} feature parent not observed`);
  const descendants = nodes.filter((node) => {
    if (node.packageName !== owner || !node.checkable) return false;
    let parent = node.parent;
    while (parent !== null) {
      if (parent === roots[0].index) return true;
      parent = nodes[parent]?.parent ?? null;
    }
    return false;
  });
  if (descendants.length !== 1) throw new Error(`unique OEM ${vehicle} selected-state control missing`);
  const control = descendants[0];
  if (control.bounds[2] <= control.bounds[0] || control.bounds[3] <= control.bounds[1] || (control.windowRef && control.visibleToUser !== true))
    throw new Error(`OEM ${vehicle} selected-state control is outside the visible page`);
  return control;
}
