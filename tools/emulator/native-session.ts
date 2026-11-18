import { createHash, randomUUID } from "node:crypto";
import { dirname, isAbsolute, join, sep } from "node:path";
import { lstatSync, statSync, realpathSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";

export type Session = { id: string; ownership_id: string };
export type Config = { executable: string; root: string; profile: string; session: Session; machine?: string; machineManifestSha256?: string };
export type Result = { exitCode: number | null; stdout: string; timedOut?: boolean; overflow?: boolean; signal?: string | null };
export type Request = { schema: 2; request_id: string; op: string; profile: string; session: Session; [key: string]: unknown };
export const verificationContract = { kind: "native-session", schema: 2, api: "voyemu.test", readiness: ["session.reachable", "android.ready"], proof: ["owned-session", "android-sdk-pid", "android-delivery", "primary-home", "both-guest-displays"], excluded: ["legacy-prepared-check-inventory", "scene-oracle", "process-inventory", "forced-cold-boot"] } as const;
const token = (v: unknown) => typeof v === "string" && /^[0-9a-f]{32}$/.test(v);
export type Operation = "info" | "profile.list" | "profile.get" | "session.start" | "session.attach" | "session.stop" | "multimedia.reboot" | "multimedia.launch" | "session.status" | "vehicle.fields" | "vehicle.get" | "vehicle.set" | "cluster.power" | "cluster.reboot" | "input.tap" | "input.gesture" | "input.key.press" | "input.wheel.buttons" | "input.wheel.press" | "input.wheel.release" | "input.voice.trigger" | "session.ui" | "vehicle.can.get" | "multimedia.system" | "session.observe" | "session.screenshot" | "session.logs" | "session.watch.start" | "session.watch.read" | "session.watch.stop" | "session.wait" | "operation.get" | "test.run";
const operations: Operation[] = ["info","profile.list","profile.get","session.start","session.attach","session.stop","multimedia.reboot","multimedia.launch","session.status","vehicle.fields","vehicle.get","vehicle.set","cluster.power","cluster.reboot","input.tap","input.gesture","input.key.press","input.wheel.buttons","input.wheel.press","input.wheel.release","input.voice.trigger","session.ui","vehicle.can.get","multimedia.system","session.observe","session.screenshot","session.logs","session.watch.start","session.watch.read","session.watch.stop","session.wait","operation.get","test.run"];
export type Screen = "primary" | "secondary";
export type Rect = { x: number; y: number; width: number; height: number };
export type Condition = { op: string; [key: string]: unknown };
export function invocation(c: Config, op: Operation, requestFile: string, extra: Record<string, unknown> = {}) {
  if (!operations.includes(op)) throw new Error("native operation outside session contract");
  if (![c.executable,c.root,requestFile].every(isAbsolute) || !token(c.session.id) || !token(c.session.ownership_id) || !/^[a-z0-9][a-z0-9._-]{0,63}$/.test(c.profile)) throw new Error("native session configuration differs");
  if ((c.machine !== undefined || c.machineManifestSha256 !== undefined) && (typeof c.machine !== "string" || !/^[a-z0-9][a-z0-9._-]{0,127}$/.test(c.machine) || !/^[a-f0-9]{64}$/.test(c.machineManifestSha256 ?? ""))) throw new Error("native machine configuration differs");
  if (Object.keys(extra).some(k => ["schema","request_id","op","profile","session",...(c.machine ? ["machine"] : [])].includes(k))) throw new Error("reserved native request field");
  if (op === "multimedia.reboot" && (!["warm","cold"].includes(extra.mode as string) || (extra.timeout_ms !== undefined && (!Number.isInteger(extra.timeout_ms) || (extra.timeout_ms as number) < 1 || (extra.timeout_ms as number) > 180000)))) throw new Error("native reboot request differs");
  if (op === "multimedia.launch" && (typeof extra.package !== "string" || !/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(extra.package) || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(String(extra.boot_id)) || (extra.screen !== undefined && !["primary","secondary"].includes(String(extra.screen))) || (extra.component !== undefined && (typeof extra.component !== "string" || !extra.component.startsWith(extra.package + "/") || !/^[A-Za-z0-9_.$/]+$/.test(extra.component))))) throw new Error("native application launch request differs");
  const request: Request = { ...extra, ...(op === "session.start" && c.machine ? {machine:c.machine} : {}), schema: 2, request_id: randomUUID(), op, profile: c.profile, session: c.session };
  return { executable: c.executable, args: ["test","--root",c.root,"--request",requestFile], timeoutMs: op === "multimedia.reboot" ? Number(extra.timeout_ms ?? 180000) + 5000 : (op === "session.wait" || op === "session.watch.read" || op === "session.observe" || op === "multimedia.system") ? 65000 : (op === "session.start" || op === "test.run") ? 120000 : 15000, voyemu: true, request };
}
const treeReasons = new Set(["accessibility_service","application_window_unverified","application_window_missing","application_window_ambiguous","window_coverage_unavailable","window_focus_unavailable","window_focus_ambiguous","boot_changed","boot_identity","child_limit","cursor_bounds","deadline","display_changed","display_unavailable","duplicate_surface","empty_tree_unverified","event_reader_limit","incomplete_request","node_limit","node_window_changed","observer_unavailable","output_limit","owned_api28_observer_required","peer_owner","request_limit","request_owner","secondary_display_ambiguous","selector_changed","selector_key","selector_limit","selector_package","selector_resource","selector_unavailable","snapshot_busy","source_restart","stale_child","stale_match","stale_node","subscription_cancelled","subscription_expired","subscription_limit","surface_count","timeout_bounds","unsupported_operation","unsupported_surface","window_changed","window_limit"]);
const treeTypes = new Set(["ClassNotFoundException","IOException","IllegalArgumentException","IllegalStateException","IndexOutOfBoundsException","InvocationTargetException","JSONException","NoSuchMethodException","NullPointerException","RemoteException","RuntimeException","SecurityException","UnsupportedOperationException"]);
const activeRootStatuses = new Set(["absent","stale","window_absent","owner_mismatch","window_mismatch","matched","unavailable"]);
function rejectedTreeMessage(message: unknown): message is string {
  if (typeof message !== "string") return false;
  const fields = message.split("; ");
  if (fields.shift() !== "TREE_REJECTED: guest could not verify current UI tree") return false;
  const order = ["reason","error_type","cause_type","active_root"];
  let reason: string | undefined;
  let previous = -1;
  for (const field of fields) {
    const match = /^(reason|error_type|cause_type|active_root)=([a-zA-Z0-9_.$-]{1,160})$/.exec(field);
    if (!match) return false;
    const index = order.indexOf(match[1]);
    if (index <= previous || !(index === 0 ? treeReasons : index === 3 ? activeRootStatuses : treeTypes).has(match[2])) return false;
    if (index === 0) reason = match[2];
    if (index === 3 && reason !== "application_window_missing") return false;
    previous = index;
  }
  return true;
}

/** A failed read, retained as failed; never permission to replay its operation. */
export class NativeTreeUnavailable extends Error {
  readonly code = "TREE_REJECTED";
  readonly outcome = "not_started";
  readonly retryable = false;
  readonly requestId: string;
  readonly operationId: string;
  constructor(requestId: string, operationId: string, diagnostic = "TREE_REJECTED: guest could not verify current UI tree") {
    super("native primary observation unavailable: " + diagnostic + " (not_started; retryable=false)");
    this.requestId = requestId; this.operationId = operationId;
  }
}
/** Completed synchronous read attempt; no durable record or mutation replay authority. */
export class NativeTreeReadTimeout extends Error {
  readonly requestId: string;
  readonly operationId: string;
  constructor(requestId: string, operationId: string) {
    super("native primary read completed with observer timeout");
    this.requestId = requestId; this.operationId = operationId;
  }
}
/** Exact failed reconnect read; generic stale remains fatal. Not permission to replay. */
export class NativeTreeReconnectStale extends Error {
  readonly requestId: string;
  readonly operationId: string;
  readonly code = "TREE_STALE";
  readonly outcome = "not_started";
  readonly retryable = false;
  constructor(requestId: string, operationId: string) {
    super("native observer reconnect crossed an epoch (not_started; retryable=false)");
    this.requestId = requestId; this.operationId = operationId;
  }
}
export class NativeAppUnlaunchable extends Error {
  readonly code = "APP_UNLAUNCHABLE";
  readonly outcome = "not_started";
  readonly retryable = false;
  readonly requestId: string;
  readonly operationId: string;
  constructor(requestId: string, operationId: string, diagnostic: string) {
    super("native application launch not started: " + diagnostic + " (not_started; retryable=false)");
    this.requestId = requestId; this.operationId = operationId;
  }
}
export type InputFenceWindow = { id?: number; type?: number; layer?: number; bounds?: Rect };
export type InputFenceDetails = { reason: "boot_changed" | "source_changed" | "window_missing" | "window_changed" | "target_missing" | "target_ambiguous" | "target_changed" | "point_outside" | "coverage_unavailable"; expected_window?: InputFenceWindow; observed_window?: InputFenceWindow; target_count?: number };
const inputFenceReasons = new Set(["boot_changed","source_changed","window_missing","window_changed","target_missing","target_ambiguous","target_changed","point_outside","coverage_unavailable"]);
const record = (v: unknown): v is Record<string, any> => v !== null && typeof v === "object" && !Array.isArray(v);
const int32 = (v: unknown) => Number.isInteger(v) && (v as number) >= -2147483648 && (v as number) <= 2147483647;
function inputFenceDetails(v: unknown): v is InputFenceDetails {
  if (!record(v) || Buffer.byteLength(JSON.stringify(v), "utf8") >= 512 || !inputFenceReasons.has(v.reason) || Object.keys(v).some(k => !["reason","expected_window","observed_window","target_count"].includes(k))) return false;
  if ("target_count" in v && (!int32(v.target_count) || v.target_count < 0)) return false;
  for (const key of ["expected_window","observed_window"]) if (key in v) {
    const w = v[key];
    if (!record(w) || !Object.keys(w).length || Object.keys(w).some(k => !["id","type","layer","bounds"].includes(k))) return false;
    for (const k of ["id","type","layer"]) if (k in w && !int32(w[k])) return false;
    if ("bounds" in w && (!record(w.bounds) || Object.keys(w.bounds).length !== 4 || ["x","y","width","height"].some(k => !int32(w.bounds[k])))) return false;
  }
  return true;
}
function inputFenceDiagnostic(details: InputFenceDetails): string {
  // Only the closed enum and numeric public geometry reach the error message.
  return "; details=" + JSON.stringify(details);
}
/** Definitive pre-dispatch rejection; never permission to retry or replay input. */
export class NativeInputRejected extends Error {
  readonly code: "TEST_INPUT_STALE" | "TEST_INPUT_BLOCKED";
  readonly details?: InputFenceDetails;
  readonly outcome = "not_started";
  readonly retryable = false;
  readonly requestId: string;
  readonly operationId: string;
  constructor(requestId: string, operationId: string, diagnostic: string, code: "TEST_INPUT_STALE" | "TEST_INPUT_BLOCKED" = "TEST_INPUT_STALE", details?: InputFenceDetails) {
    super("native pointer input rejected: " + diagnostic + (details ? inputFenceDiagnostic(details) : "") + " (not_started; retryable=false)");
    this.code = code; this.details = details;
    this.requestId = requestId; this.operationId = operationId;
  }
}
/** An authoritative empty source read; never a successful system observation. */
export class NativeSystemUnavailable extends Error {
  readonly requestId: string;
  readonly operationId: string;
  constructor(requestId: string, operationId: string) {
    super("native system source returned an empty unavailable sample");
    this.requestId = requestId; this.operationId = operationId;
  }
}
function emptySystemSample(v: any): boolean {
  const keys = (value: any, expected: string[]) => record(value) && JSON.stringify(Object.keys(value).sort()) === JSON.stringify(expected.sort());
  return keys(v, ["boot_id", "coverage", "host_interval", "revision", "source_instance", "status", "value"]) &&
    v.status === "unavailable" && v.value === null && v.boot_id === "" && v.source_instance === "" && v.revision === "" &&
    keys(v.coverage, ["continuous", "gap"]) && v.coverage.continuous === false && v.coverage.gap === true &&
    keys(v.host_interval, ["clock", "start_ms", "end_ms"]) && v.host_interval.clock === "host.steady" &&
    /^\d+$/.test(v.host_interval.start_ms) && typeof v.host_interval.start_ms === "string" &&
    /^\d+$/.test(v.host_interval.end_ms) && typeof v.host_interval.end_ms === "string" &&
    BigInt(v.host_interval.end_ms) >= BigInt(v.host_interval.start_ms);
}
/** Repeated reads share one deadline; mutation dispatch is deliberately absent. */
export async function readSystemSample(read: (remainingMs: number) => Promise<any>, timeoutMs: number,
    clock = () => performance.now(), pause = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms))) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000) throw new Error("native system read deadline differs");
  const deadline = clock() + timeoutMs;
  while (true) {
    try { return await read(Math.max(1, Math.ceil(deadline - clock()))); }
    catch (error) {
      if (!(error instanceof NativeSystemUnavailable)) throw error;
      const remaining = deadline - clock();
      if (remaining <= 0) throw error;
      await pause(Math.min(100, remaining));
      if (clock() >= deadline) throw error;
    }
  }
}
const multimediaScreens = ["primary", "secondary"] as const;
const treeMetadata = ["source", "source_clock_domain", "host_interval", "boot_id", "android_pid", "observer_pid",
  "source_instance", "revision", "observation_id", "vehicle_revision", "selector", "read_attempts",
  "transient_rejections", "observer_epoch", "source_timestamp_ms", "started_uptime_ms", "completed_uptime_ms"];

function rejectUiSlot(slot: any, requestId: string, operationId: string): never {
  const error = slot.error;
  if (record(error) && Object.keys(error).sort().join(",") === "code,message,outcome,retryable" && error.code === slot.error_code) {
    if (error.outcome === "not_started" && error.retryable === false) {
      if (error.code === "TREE_REJECTED" && rejectedTreeMessage(error.message))
        throw new NativeTreeUnavailable(requestId, operationId, error.message);
      if (error.code === "TREE_STALE" && error.message === "TREE_STALE: session or boot changed while reconnecting observer")
        throw new NativeTreeReconnectStale(requestId, operationId);
    }
    if (error.outcome === "unknown" && error.retryable === true && error.code === "TREE_TIMEOUT" &&
        error.message === "TREE_TIMEOUT: observer deadline elapsed")
      throw new NativeTreeReadTimeout(requestId, operationId);
  }
  throw new Error("native multimedia UI observation unavailable");
}

function sessionUi(v: any, q: Request, operationId: string) {
  const selected = q.screens ?? [q.screen];
  if (!Array.isArray(selected) || !selected.length || new Set(selected).size !== selected.length ||
      selected.some(screen => !multimediaScreens.includes(screen)) ||
      v.format !== "voyemu.session.ui.v1" || v.profile !== q.profile || v.session_id !== q.session.id ||
      v.atomic_ui_state !== false || v.cluster !== null || !Array.isArray(v.multimedia) || v.multimedia.length !== 2)
    throw new Error("native session UI authority or selection differs");
  const slots: any[] = [];
  let identity: any;
  let unavailable: any;
  for (const [index, screen] of multimediaScreens.entries()) {
    const slot = v.multimedia[index];
    if (!selected.includes(screen)) {
      if (slot !== null) throw new Error("unselected native UI slot populated");
      continue;
    }
    if (!record(slot) || slot.screen !== screen || !Array.isArray(slot.nodes) || !Array.isArray(slot.windows) ||
        slot.complete !== (slot.status === "ok")) throw new Error("native multimedia UI slot differs");
    if (slot.status === "unsupported" || slot.status === "unavailable") {
      if (slot.nodes.length || slot.windows.length) throw new Error("unavailable native UI slot contains a tree");
      if (!unavailable || screen === "primary") unavailable = slot;
    } else {
      if (!["ok", "partial"].includes(slot.status) || slot.source !== "android.ui" || "error_code" in slot || "error" in slot)
        throw new Error("native multimedia UI source differs");
      if (identity && ["boot_id", "android_pid", "observer_pid", "source_instance", "observation_id"]
          .some(key => identity[key] === undefined || identity[key] !== slot[key]))
        throw new Error("native multimedia UI slots came from different observations");
      identity ??= slot;
    }
    slots.push({ ...slot, ...(slot.observer_source === undefined ? {} : { source: slot.observer_source }) });
  }
  const complete = slots.every(slot => slot.complete);
  if (v.complete !== complete || v.status !== (complete ? "ready" : identity ? "partial" : "unavailable"))
    throw new Error("native session UI completeness differs");
  if (!identity || unavailable?.screen === "primary") rejectUiSlot(unavailable, q.request_id, operationId);
  const metadata = Object.fromEntries(treeMetadata.filter(key => key in identity).map(key => [key, identity[key]]));
  metadata.completed_uptime_ms ??= identity.source_timestamp_ms;
  return { ...metadata, format: "voyemu.ui.v1", profile: v.profile, session_id: v.session_id,
    status: v.status, complete, atomic_ui_state: false, screens: slots };
}

export function response(r: Result, q: Request): any {
  if ((q.op === "session.ui" || q.op === "multimedia.launch" || q.op === "input.tap" || q.op === "input.gesture") && r.exitCode === 4 && !r.timedOut && !r.overflow && !r.signal) {
    let v: any; try { v = JSON.parse(r.stdout); } catch { /* Generic failure below. */ }
    if (v?.schema === 2 && v.request_id === q.request_id && token(v.operation_id) && v.ok === false && Number.isFinite(v.elapsed_ms) && v.elapsed_ms >= 0 &&
        JSON.stringify(Object.keys(v).sort()) === JSON.stringify(["elapsed_ms", "error", "ok", "operation_id", "request_id", "schema"]) &&
        record(v.error) && (JSON.stringify(Object.keys(v.error).sort()) === JSON.stringify(["code", "message", "outcome", "retryable"]) ||
          ((q.op === "input.tap" || q.op === "input.gesture") && JSON.stringify(Object.keys(v.error).sort()) === JSON.stringify(["code", "details", "message", "outcome", "retryable"]) && inputFenceDetails(v.error.details))) &&
        v.error.outcome === "not_started" && v.error.retryable === false) {
      if ((q.op === "input.tap" || q.op === "input.gesture") && ((v.error.code === "TEST_INPUT_STALE" && v.error.message === "TEST_INPUT_STALE: target identity or geometry changed before dispatch") ||
          (v.error.code === "TEST_INPUT_BLOCKED" && v.error.message === "TEST_INPUT_BLOCKED: complete actionable input coverage unavailable")))
        throw new NativeInputRejected(v.request_id, v.operation_id, v.error.message, v.error.code, v.error.details);
      if (q.op === "multimedia.launch" && v.error.code === "APP_UNLAUNCHABLE" && ["APP_UNLAUNCHABLE: package has no unique launcher activity", "APP_UNLAUNCHABLE: resolved component differs from request"].includes(v.error.message))
        throw new NativeAppUnlaunchable(v.request_id, v.operation_id, v.error.message);
      if (q.op === "session.ui" && v.error.code === "TREE_REJECTED" && rejectedTreeMessage(v.error.message))
        throw new NativeTreeUnavailable(v.request_id, v.operation_id, v.error.message);
      if (q.op === "session.ui" && v.error.code === "TREE_STALE" && v.error.message === "TREE_STALE: session or boot changed while reconnecting observer")
        throw new NativeTreeReconnectStale(v.request_id, v.operation_id);
    }
  }
  // session.ui is not recorded by Voyemu. Only a complete, correlated synchronous
  // rejection ends this query attempt; transport uncertainty remains fatal.
  if (q.op === "session.ui" && r.exitCode === 4 && !r.timedOut && !r.overflow && !r.signal) {
    let v: any; try { v = JSON.parse(r.stdout); } catch { /* Generic failure below. */ }
    if (record(v) && v.schema === 2 && v.request_id === q.request_id && token(v.operation_id) && v.ok === false &&
        Number.isFinite(v.elapsed_ms) && v.elapsed_ms >= 0 &&
        JSON.stringify(Object.keys(v).sort()) === JSON.stringify(["elapsed_ms", "error", "ok", "operation_id", "request_id", "schema"]) &&
        record(v.error) && JSON.stringify(Object.keys(v.error).sort()) === JSON.stringify(["code", "message", "outcome", "retryable"]) &&
        v.error.code === "TREE_TIMEOUT" && v.error.message === "TREE_TIMEOUT: observer deadline elapsed" &&
        v.error.outcome === "unknown" && v.error.retryable === true)
      throw new NativeTreeReadTimeout(v.request_id, v.operation_id);
  }
  if (r.exitCode !== 0 || r.timedOut || r.overflow || r.signal) throw new Error("native operation unresolved; inspect durable operation receipt before replay");
  const v = JSON.parse(r.stdout);
  if (v.schema !== 2 || v.request_id !== q.request_id || !token(v.operation_id) || v.ok !== true || !Number.isFinite(v.elapsed_ms) || v.elapsed_ms < 0 || !v.result || typeof v.result !== "object" || v.error) throw new Error("native response authority differs");
  if (q.op === "session.ui") return sessionUi(v.result, q, v.operation_id);
  if (q.op === "multimedia.system" &&
      JSON.stringify(Object.keys(v).sort()) === JSON.stringify(["elapsed_ms", "ok", "operation_id", "request_id", "result", "schema"]) &&
      emptySystemSample(v.result)) throw new NativeSystemUnavailable(v.request_id, v.operation_id);
  if (v.result.accepted === false || v.result.timed_out === true || (q.op !== "operation.get" && ["failed","unavailable","running"].includes(v.result.status))) throw new Error("native operation not accepted");
  return v.result;
}
export function status(v: any, s: Session, config?: Config) {
  const a = v?.deliveries?.android, port = Number(a?.serial?.slice(9));
  if (v?.session_id !== s.id || v.ok !== true || v.stopped !== false || !Number.isSafeInteger(v.android_pid) || v.android_pid < 1 || !/^emulator-[0-9]+$/.test(a?.serial ?? "") || !Number.isInteger(port) || port < 5554 || port > 5682 || port % 2 || a.pid !== v.android_pid || typeof a.quickboot?.mode !== "string") throw new Error("native live session identity differs");
  let machineIdentity: {machine?:string;machineSeed?:string;machineManifestSha256?:string} = {};
  if (config?.machine !== undefined) {
    invocation(config,"session.status",join(config.root,"identity-check.json"));
    const root=realpathSync(config.root), seed=join(root,"machines",config.machine), manifest=join(seed,"runtime.json");
    if (config.root !== root || realpathSync(seed) !== seed || !lstatSync(seed).isDirectory() || v.machine !== config.machine || v.machine_seed !== seed || v.machine_manifest_sha256 !== config.machineManifestSha256 || hashFile(manifest) !== config.machineManifestSha256 || JSON.parse(readFileSync(manifest,"utf8")).profile !== config.profile) throw new Error("native selected machine identity differs");
    machineIdentity={machine:config.machine,machineSeed:seed,machineManifestSha256:config.machineManifestSha256};
  }
  return { ...machineIdentity, sessionId: s.id, pid: v.android_pid as number, port, serial: a.serial as string, actualBoot: a.quickboot.mode as string, verificationContract };
}
export function ready(v: any, s: Session, config?: Config) {
  if (v?.predicate !== "android.ready" || v.ready !== true) throw new Error("native Android readiness unavailable");
  const identity = status(v.last_observation,s,config), a = v.last_observation.deliveries.android;
  if (a.guest_acknowledged !== true || a.home?.status !== "visible_on_primary" || a.displays?.status !== "both_guest_displays_ready") throw new Error("native readiness scopes differ");
  return identity;
}
/** Current OEM HOME delivery proof; deliberately not an accessibility tree proof. */
export class NativeHomeDeliveryPending extends Error {}
export function primaryHome(v: any, s: Session, owned: { sessionId: string; pid: number; serial: string }, bootId: string) {
  const identity = status(v, s);
  const android = v.deliveries.android;
  const clean = (value: unknown) => value === undefined || value === null || (record(value) && Object.keys(value).length === 0);
  if (!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(bootId) || identity.sessionId !== owned.sessionId || identity.pid !== owned.pid || identity.serial !== owned.serial ||
      android.external_reboot?.boot_id !== bootId || !clean(v.stage_errors) || !clean(v.last_error) || !clean(android.stage_errors) || !clean(android.last_error))
    throw new Error("native current primary HOME proof differs");
  // A supervisor refresh may omit a delivery scope briefly. Identity, boot and
  // explicit failures remain fatal; absence is never accepted as ready.
  if ((android.guest_acknowledged !== undefined && android.guest_acknowledged !== true) ||
      (android.home !== undefined && (android.home?.status !== "visible_on_primary" || android.home?.component !== "com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity" || android.home?.display_id !== 0)) ||
      (android.displays !== undefined && android.displays?.status !== "both_guest_displays_ready"))
    throw new Error("native current primary HOME proof differs");
  if (android.guest_acknowledged === undefined || android.home === undefined || android.displays === undefined)
    throw new NativeHomeDeliveryPending("native HOME delivery refresh pending");
  ready({ predicate: "android.ready", ready: true, last_observation: v }, s);
  if (android.home.component !== "com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity" || android.home.display_id !== 0)
    throw new Error("native current primary HOME proof differs");
  return { kind: "native-primary-home", sessionId: identity.sessionId, pid: identity.pid, serial: identity.serial, bootId, component: android.home.component, displayId: 0 };
}

/** Retry only missing delivery scopes; every attempt must recheck caller lifecycle. */
export async function waitPrimaryHome(check: () => Promise<unknown>, timeoutMs = 10000,
  clock = () => performance.now(), pause = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms))) {
  if (!Number.isFinite(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000) throw new Error("native HOME deadline differs");
  const deadline = clock() + timeoutMs;
  while (clock() < deadline) {
    try { return await check(); }
    catch (error) { if (!(error instanceof NativeHomeDeliveryPending)) throw error; }
    const remaining = deadline - clock();
    if (remaining > 0) await pause(Math.min(250, remaining));
  }
  throw new Error("native HOME delivery did not recover before deadline");
}

export function primaryHomeCaptureBoot(manifest: any, bootId: string) {
  if (manifest?.android_boot_id !== bootId) throw new Error("HOME capture boot differs");
}

export function capture(v: any, s: Session, pid: number, maxSkewMs: number, screens: Screen[] = ["primary","secondary"], rect?: Rect) {
  if (!Number.isFinite(maxSkewMs) || maxSkewMs < 1 || maxSkewMs > 10000 || v?.schema !== 2 || v.session_id !== s.id || v.android_pid !== pid || v.accepted !== true || v.frames_consistent !== true || v.capture_mode !== "parallel_fenced" || v.atomic !== false || v.android_boot_identity_verified !== true || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(v.android_boot_id ?? "") || v.android_frames_verified !== true || !Number.isFinite(v.skew_upper_bound_ms) || v.skew_upper_bound_ms < 0 || v.skew_upper_bound_ms > maxSkewMs || !Array.isArray(v.frames) || v.frames.length !== screens.length || JSON.stringify([...v.screens].sort()) !== JSON.stringify([...screens].sort())) throw new Error("native capture fence differs");
  const seen = new Set<string>();
  for (const f of v.frames) {
    const r = rect ?? {x:0,y:0,width:1920,height:720};
    if (!screens.includes(f.screen) || seen.has(f.screen) || f.source !== "android_sdk" || f.sdk_display_id !== (f.screen === "primary" ? 0 : 1) || f.display_id !== null || f.derived !== Boolean(rect) || f.source_width !== 1920 || f.source_height !== 720 || f.width !== r.width || f.height !== r.height || !f.rect || ["x","y","width","height"].some(k => f.rect[k] !== r[k as keyof Rect]) || f.file !== f.screen + ".png" || !/^[0-9a-f]{64}$/.test(f.sha256 ?? "") || !/^\d+$/.test(f.sequence ?? "") || !/^\d+$/.test(f.source_timestamp_ns ?? "") || typeof f.source_clock_domain !== "string" || !f.host_interval || f.host_interval.clock_domain !== "host.steady" || f.host_interval.clock_id !== s.id || !/^\d+$/.test(f.host_interval.start_ns ?? "") || !/^\d+$/.test(f.host_interval.end_ns ?? "") || BigInt(f.host_interval.end_ns) < BigInt(f.host_interval.start_ns) || f.freshness?.source_clock_mapped !== false || f.freshness?.newer_guest_frame_proven !== false) throw new Error("native original screen authority differs");
    seen.add(f.screen);
  }
  return v;
}

/** Validate retained native artifacts separately from session/manifest authority. */
export function captureFiles(v: any, directory: string, read: (path: string) => Buffer, hash: (bytes: Buffer) => string, png: (bytes: Buffer, allowBlack: boolean) => { width: number; height: number }) {
  if (!isAbsolute(directory) || v.artifact_dir !== directory || v.manifest !== directory + "/manifest.json") throw new Error("native capture artifact directory differs");
  const manifest = JSON.parse(read(directory + "/manifest.json").toString("utf8"));
  for (const key of ["schema","session_id","android_pid","android_boot_id","screens","capture_mode","accepted","frames_consistent","cluster_powered","qnx_frame_verified","frames"])
    if (JSON.stringify(manifest[key]) !== JSON.stringify(v[key])) throw new Error("native retained manifest differs");
  for (const f of v.frames) {
    if (!["primary.png","secondary.png"].includes(f.file) || !/^[0-9a-f]{64}$/.test(f.sha256 ?? "")) throw new Error("native frame path/hash differs");
    const bytes = read(directory + "/" + f.file);
    if (hash(bytes) !== f.sha256) throw new Error("native frame bytes differ");
    const decoded = png(bytes, false);
    if (decoded.width !== f.width || decoded.height !== f.height) throw new Error("native decoded frame dimensions differ");
  }
  return manifest;
}

type Dispatch = { executable: string; args: string[]; timeoutMs: number; voyemu?: boolean };
const dispatches = new WeakMap<object, () => boolean>();
const treeReads = new WeakSet<object>();
/** Admit only an unchanged private request created by this pinned native session. */
export function nativeDispatch(inv: Dispatch): boolean {
  try { return dispatches.get(inv)?.() === true; } catch { return false; }
}

/** Keep the UI server deadline distinct from process completion/receipt delivery.
 * Only an authenticated live read receives grace; no mutation gains time or replay authority.
 * Callers must still reject an observation completed after their semantic deadline. */
export function nativeReadTransportTimeout(inv: Dispatch, remainingMs: number): number {
  if (!Number.isFinite(remainingMs) || remainingMs <= 0) throw new Error("native transport deadline exhausted");
  const grace = treeReads.has(inv) && nativeDispatch(inv) ? 5000 : 0;
  return Math.min(inv.timeoutMs, remainingMs + grace);
}

export type Run = (invocation: { executable: string; args: string[]; timeoutMs: number; voyemu: boolean }) => Promise<Result>;
export class NativeSession {
  private readonly executableHash: string;
  private readonly config: Config;
  private readonly requests: string;
  private readonly run: Run;
  private readonly png: (bytes: Buffer, allowBlack: boolean) => { width: number; height: number };
  constructor(config: Config, requests: string, run: Run, png: (bytes: Buffer, allowBlack: boolean) => { width: number; height: number }) {
    invocation(config,"session.status",join(requests,"validation.json"));
    this.config=config; this.requests=requests; this.run=run; this.png=png;
    if (!isAbsolute(requests)) throw new Error("native private request directory must be absolute");
    mkdirSync(requests, { recursive: true, mode: 0o700 });
    if (lstatSync(requests).isSymbolicLink() || (statSync(requests).mode & 0o077) !== 0) throw new Error("native request directory must be private");
    this.executableHash = hashFile(config.executable);
  }
  async call(op: Parameters<typeof invocation>[1], extra: Record<string,unknown> = {}) {
    if (hashFile(this.config.executable) !== this.executableHash) throw new Error("native executable changed");
    const path = join(this.requests, randomUUID() + ".json");
    const i = invocation(this.config,op,path,extra);
    writeFileSync(path, JSON.stringify(i.request) + "\n", { flag: "wx", mode: 0o600 });
    // Never pass the request object or ownership token to action recorders.
    const dispatch = { executable:i.executable,args:[...i.args],timeoutMs:i.timeoutMs,voyemu:true };
    const expected = JSON.stringify(i.request) + "\n";
    dispatches.set(dispatch, () => {
      const root = realpathSync(this.config.root);
      const executable = this.config.executable;
      const installedLayout = executable === join(root,"voyemu") || executable === join(root,".local","native-cpp","bin","voyemu");
      const directory = lstatSync(this.requests), file = lstatSync(path);
      return this.config.root === root && installedLayout &&
        realpathSync(executable) === executable && hashFile(executable) === this.executableHash &&
        dispatch.executable === executable && dispatch.voyemu === true &&
        Number.isFinite(dispatch.timeoutMs) && dispatch.timeoutMs > 0 && dispatch.timeoutMs <= i.timeoutMs && JSON.stringify(dispatch.args) === JSON.stringify(i.args) &&
        realpathSync(this.requests) === this.requests && directory.isDirectory() && !directory.isSymbolicLink() && (directory.mode & 0o077) === 0 &&
        file.isFile() && !file.isSymbolicLink() && (file.mode & 0o077) === 0 && file.size <= 65536 &&
        readFileSync(path,"utf8") === expected;
    });
    if (op === "session.ui") treeReads.add(dispatch);
    let result: Result;
    try { result = await this.run(dispatch); } finally { dispatches.delete(dispatch); treeReads.delete(dispatch); }
    return response(result,i.request);
  }
  async launch(packageName: string, bootId: string, screen: Screen = "primary", component?: string) {
    return this.call("multimedia.launch", {package:packageName,boot_id:bootId,screen,...(component ? {component} : {})});
  }
  async reboot(mode: "warm" | "cold", timeoutMs = 180000) {
    return this.call("multimedia.reboot", {mode,timeout_ms:timeoutMs});
  }
  async primaryMultimediaUi(timeoutMs = 5000) {
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 5000) throw new Error("native tree read deadline differs");
    return this.call("session.ui", { screen: "primary", timeout_ms: timeoutMs });
  }
  async multimediaUi(screens: Screen[] = ["primary","secondary"], extra: Record<string,unknown> = {}) { return this.call("session.ui", {...extra,screens}); }
  async can(extra: Record<string,unknown> = {}) { return this.call("vehicle.can.get",extra); }
  async system(extra: Record<string,unknown> = {}) {
    return readSystemSample(remaining => this.call("multimedia.system", {...extra, timeout_ms: remaining}), Number(extra.timeout_ms ?? 1000));
  }
  async state(extra: Record<string,unknown> = {}) { return this.call("session.observe",extra); }
  async watch(condition: Condition, extra: Record<string,unknown> = {}) { return this.call("session.watch.start",{...extra,condition}); }
  async readWatch(watch: {watch_id:string;epoch:string;cursor:string}, timeoutMs = 1000) { return this.call("session.watch.read",{...watch,timeout_ms:timeoutMs}); }
  async stopWatch(watch: {watch_id:string;epoch:string}) { return this.call("session.watch.stop",watch); }
  async wait(condition: Condition, timeoutMs = 10000) { return this.call("session.wait",{condition,timeout_ms:timeoutMs}); }
  async operation(target: {operation_id:string} | {request_id:string}) {
    if (Object.keys(target).length !== 1 || !("operation_id" in target || "request_id" in target)) throw new Error("native receipt target differs");
    return this.call("operation.get",{target});
  }
  async status() { return status(await this.call("session.status"),this.config.session,this.config); }
  async waitAndroid(timeoutMs = 60000) {
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000) throw new Error("native readiness deadline differs");
    return ready(await this.call("session.wait",{predicate:"android.ready",timeout_ms:timeoutMs}),this.config.session,this.config);
  }
  async capture(directory: string, maxSkewMs = 500, screens: Screen[] = ["primary","secondary"], rect?: Rect) {
    if (!screens.length || new Set(screens).size !== screens.length || screens.some(s => !["primary","secondary"].includes(s)) || (rect && (screens.length !== 1 || Object.values(rect).some(n => !Number.isSafeInteger(n)) || rect.x < 0 || rect.y < 0 || rect.width < 1 || rect.height < 1 || rect.x + rect.width > 1920 || rect.y + rect.height > 720))) throw new Error("native capture selection differs");
    const allowed = join(realpathSync(this.config.root),".local","build","tmp");
    if (!isAbsolute(directory) || !directory.startsWith(allowed + sep)) throw new Error("native capture directory outside owned runtime artifacts");
    const before = await this.status();
    const v = capture(await this.call("session.screenshot",{artifact_dir:directory,timeout_ms:10000,max_skew_ms:maxSkewMs,screens,...(rect ? {rect} : {})}),this.config.session,before.pid,maxSkewMs,screens,rect);
    const after = await this.status();
    if (before.sessionId !== after.sessionId || before.pid !== after.pid || before.serial !== after.serial) throw new Error("native session changed around capture");
    const read = (path:string) => {
      if (!path.startsWith(directory + sep) || realpathSync(dirname(path)) !== realpathSync(directory)) throw new Error("native artifact escaped directory");
      const info=lstatSync(path);
      if (!info.isFile() || info.isSymbolicLink() || info.size > 40*1024*1024) throw new Error("native artifact is not bounded ordinary file");
      return readFileSync(path);
    };
    captureFiles(v,directory,read,bytes=>createHash("sha256").update(bytes).digest("hex"),this.png);
    return { manifest:v, screens: { ...Object.fromEntries(screens.map(screen => [screen,join(directory,screen+".png")])) }, identity:after };
  }
}
function hashFile(path:string) {
  const info=lstatSync(path);
  if (!info.isFile() || info.isSymbolicLink()) throw new Error("native executable is not ordinary file");
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

export function allowBlackFrame(_manifest: any, _frame: any): boolean { return false; }

/** Only a correlated, definitive missing-window rejection permits semantic reacquisition. */
export function missingInputWindow(error: unknown, window: InputFenceWindow): boolean {
  return error instanceof NativeInputRejected && error.code === "TEST_INPUT_STALE" && error.outcome === "not_started" && error.retryable === false && error.details?.reason === "window_missing" && sameInputWindow(error.details.expected_window, window);
}

function sameInputWindow(actual: InputFenceWindow | undefined, expected: InputFenceWindow): boolean {
  if (!actual || Object.keys(actual).sort().join() !== Object.keys(expected).sort().join()) return false;
  return (["id","type","layer"] as const).every(key=>actual[key]===expected[key]) &&
    (actual.bounds === undefined ? expected.bounds === undefined : expected.bounds !== undefined &&
      (["x","y","width","height"] as const).every(key=>actual.bounds![key]===expected.bounds![key]));
}
