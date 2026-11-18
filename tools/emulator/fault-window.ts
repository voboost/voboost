import type { Invocation, ProcessAdapter } from "../../release/emulator-gate.ts";
import type { LogRecord } from "./installer-logs.ts";

export type FaultKind = "startup-mutation-lock" | "vehicle-state-reply-loss" | "uninstall-transport-loss";
export type FaultRequest = { action: "fault-window"; kind: FaultKind; bootId: string; manifestSha256: string; generationId: string; catalogSha256: string; targetPid: number; targetStarttime: string };
export type FaultReceipt = Omit<FaultRequest, "action"> & { schemaVersion: 1; phase: "armed" | "closed"; token: string; releaseIdentity: string; ownerPid: number; ownerStarttime: string; daemonPid: number; daemonStarttime: string; startedMonotonicMs: string; expiresMonotonicMs: string; cleanup?: true; reason?: "eof" | "expired" };
const fields = (value: any, names: string[], label: string) => {
  if (!value || typeof value !== "object" || Array.isArray(value) || Object.keys(value).sort().join() !== [...names].sort().join()) throw new Error(`${label}: unknown or missing fields`);
};
const sha = (value: unknown) => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const decimal = (value: unknown) => typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
const pid = (value: unknown) => Number.isInteger(value) && Number(value) > 0 && Number(value) <= 2147483647;
export function validateFaultRequest(request: any): asserts request is FaultRequest {
  fields(request, ["action", "kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime"], "fault request");
  if (request.action !== "fault-window" || !["startup-mutation-lock", "vehicle-state-reply-loss", "uninstall-transport-loss"].includes(request.kind) || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(request.bootId) || ![request.manifestSha256, request.generationId, request.catalogSha256].every(sha) || !pid(request.targetPid) || !decimal(request.targetStarttime)) throw new Error("invalid typed fault authority");
}
export function faultReceipt(value: any, request: FaultRequest, serial: string, releaseIdentity: string, phase: "armed" | "closed", armed?: FaultReceipt): FaultReceipt {
  validateFaultRequest(request);
  fields(value, ["schemaVersion", "serial", "phase", "body"], "fault envelope");
  const body = value.body;
  fields(body, ["schemaVersion", "phase", "kind", "token", "bootId", "releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "ownerPid", "ownerStarttime", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "startedMonotonicMs", "expiresMonotonicMs", ...(phase === "closed" ? ["cleanup", "reason"] : [])], "fault receipt");
  if (value.schemaVersion !== 1 || value.serial !== serial || value.phase !== phase || body.schemaVersion !== 1 || body.phase !== phase || body.releaseIdentity !== releaseIdentity || !sha(body.token) || !pid(body.ownerPid) || !pid(body.daemonPid) || ![body.ownerStarttime, body.daemonStarttime, body.startedMonotonicMs, body.expiresMonotonicMs].every(decimal) || BigInt(body.expiresMonotonicMs) - BigInt(body.startedMonotonicMs) !== 90000n) throw new Error("fault receipt authority or maximum lease differs");
  for (const key of ["kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime"] as const) if (body[key] !== request[key]) throw new Error("fault receipt target authority differs");
  if (phase === "closed") {
    if (!armed || body.cleanup !== true || body.reason !== "eof") throw new Error("fault cleanup missing or window expired");
    for (const key of Object.keys(armed)) if (key !== "phase" && body[key] !== (armed as any)[key]) throw new Error("fault cleanup identity or token differs");
  }
  return body;
}

/** stdin carries no command bytes; EOF is the only operator release action. */
export async function withFaultWindow<T>(invocation: Invocation, run: ProcessAdapter, request: FaultRequest, serial: string, releaseIdentity: string, observe: (armed: FaultReceipt, live: () => void) => Promise<T>): Promise<{ value: T; armed: FaultReceipt; closed: FaultReceipt; actionId?: number }> {
  validateFaultRequest(request);
  if (!/^emulator-[1-9][0-9]{3,4}$/.test(serial) || invocation.args.length !== 6 || invocation.args[4] !== "--candidate" || !invocation.args[5]?.startsWith("/") || invocation.args[0] !== "--emulator-check" || invocation.args[2] !== "--device" || invocation.args[3] !== serial || invocation.voyemu || invocation.host || invocation.stdinUntil) throw new Error("fault window requires the exact local emulator and fixed packaged CLI request");
  let release!: () => void, accept!: (value: FaultReceipt) => void, reject!: (reason: unknown) => void, released = false;
  const stdinUntil = new Promise<void>(resolve => { release = () => { released = true; resolve(); }; });
  const ready = new Promise<FaultReceipt>((resolve, fail) => { accept = resolve; reject = fail; });
  const started = performance.now();
  let buffer = "", armed: FaultReceipt | undefined, closed: FaultReceipt | undefined, streamFailure: unknown;
  const timer = setTimeout(() => reject(new Error("fault window never armed within bounded observation")), 30000);
  const complete = run({ ...invocation, stdinUntil, onOutput: (stream, chunk) => {
    invocation.onOutput?.(stream, chunk);
    if (stream !== "stdout") return;
    try {
      buffer += chunk;
      if (Buffer.byteLength(buffer) > 65536) throw new Error("fault receipt stream exceeds bound");
      let newline: number;
      while ((newline = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newline).replace(/\r$/, ""); buffer = buffer.slice(newline + 1);
        if (!line.startsWith("{")) continue;
        if (closed) throw new Error("duplicate fault terminal receipt");
        if (!armed) { armed = faultReceipt(JSON.parse(line), request, serial, releaseIdentity, "armed"); clearTimeout(timer); accept(armed); }
        else {
          if (!released) throw new Error("fault cleanup arrived before operator EOF");
          closed = faultReceipt(JSON.parse(line), request, serial, releaseIdentity, "closed", armed);
        }
      }
    } catch (error) { streamFailure = error; reject(error); release(); }
  } }).then(result => {
    clearTimeout(timer);
    if (streamFailure) throw streamFailure;
    if (result.exitCode !== 0 || result.timedOut || result.signal || result.overflow || result.retained?.overflow || buffer.trim() || !armed || !closed) throw new Error("fault child or exact streamed cleanup did not complete");
    return { closed, actionId: result.actionId };
  });
  // Observe early exit without leaving an unhandled rejection while ordinary UI is running.
  complete.catch(error => { reject(error); release(); });
  let failure: unknown, value!: T;
  const live = () => {
    if (streamFailure) throw streamFailure;
    if (!armed || closed || released || performance.now() - started >= 60000) throw new Error("fault mutation exceeds its live conservative dispatch window");
  };
  try { const current = await ready; live(); value = await observe(current, live); }
  catch (error) { failure = error; }
  finally { clearTimeout(timer); release(); }
  let terminal: Awaited<typeof complete>;
  try { terminal = await complete; }
  catch (error) { if (failure) throw new AggregateError([failure, error], `fault observation failed: ${String(failure)}; cleanup failed: ${String(error)}`); throw error; }
  if (failure) throw failure;
  return { value, armed: armed!, closed: terminal.closed, actionId: terminal.actionId };
}

export function faultEffects(records: LogRecord[], armed: FaultReceipt, source: "settings" | "vehicle-drive-forced-ev" | "vehicle-drive-pedestrian-warning", afterSequence: number): LogRecord[] {
  const expectedEffect = armed.kind === "startup-mutation-lock" ? "startup-mutation-rejected" : armed.kind === "uninstall-transport-loss" ? "uninstall-transport-dropped" : "state-reply-dropped";
  const result: LogRecord[] = [];
  let previous = -1;
  for (const record of records) {
    if (!Number.isSafeInteger(record.sequence) || record.sequence <= previous) throw new Error("fault effect log sequence is not strictly increasing");
    previous = record.sequence;
  }
  for (const record of records.filter(record => record.sequence > afterSequence && record.message === "fault-effect")) {
    fields(record, ["schemaVersion", "sequence", "timestamp", "source", "level", "component", "message", "fields"], "canonical fault record");
    fields(record.source, ["kind"], "canonical fault source");
    fields(record.fields, ["token", "kind", "effect", "source", "requestId", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime", "process"], "canonical fault fields");
    const f = record.fields;
    if (record.schemaVersion !== 1 || record.source.kind !== "daemon" || record.level !== "info" || record.component !== "emulator-diagnostic" || Object.values(f).some(value => typeof value !== "string")) throw new Error("fault effect is not canonical root evidence");
    if (f.token !== armed.token) continue;
    for (const key of ["kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetStarttime"] as const) if (f[key] !== armed[key]) throw new Error("fault effect authority differs");
    if (f.effect !== expectedEffect || f.targetPid !== String(armed.targetPid) || f.process !== (armed.kind === "vehicle-state-reply-loss" ? "com.qinggan.systemservice" : "com.qinggan.app.setting") || (armed.kind === "uninstall-transport-loss" ? !/^[A-Za-z0-9_-]{1,64}$/.test(String(f.requestId)) : !/^(?:0|[1-9][0-9]{0,18})$/.test(String(f.requestId)))) throw new Error("fault effect target or operation differs");
    if (f.source === source) result.push(record);
  }
  return result;
}

