import { editorMetadata } from "./installer-ui.ts";
import { NativeTreeUnavailable, NativeTreeReconnectStale, NativeTreeReadTimeout } from "./native-session.ts";
import { createHash } from "node:crypto";
import type { UiNodes, UiNode, WindowRef } from "./installer-ui.ts";

export type TreeFence = { sessionId: string; pid: number; bootId: string; profile: string };
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/u;
const integer = (v: unknown): number => {
  if (!Number.isSafeInteger(v)) throw new Error("native tree integer differs");
  return v as number;
};
const text = (v: unknown): string => {
  if (typeof v !== "string" || v.length > 16384) throw new Error("native tree text differs");
  return v;
};
const bool = (v: unknown): boolean => {
  if (typeof v !== "boolean") throw new Error("native tree boolean differs");
  return v;
};
const bounds = (v: unknown, hidden = false): UiNode["bounds"] => {
  if (!v || typeof v !== "object" || Array.isArray(v) || Object.keys(v).sort().join(",") !== "height,width,x,y") throw new Error("native tree bounds differ");
  const r = v as Record<string,unknown>;
  const b = [integer(r.x),integer(r.y),integer(r.x)+integer(r.width),integer(r.y)+integer(r.height)];
  if (b.some(n => Math.abs(n) > 16384) || (!hidden && (b[2] < b[0] || b[3] < b[1]))) throw new Error("native tree bounds differ");
  return b as UiNode["bounds"];
};

export class NativeTreePartial extends Error {
  constructor() { super("native primary observation partial; no input authority"); }
}

/** Full API28 primary observation, never an installer receipt or an atomic frame. */
export function nativeTree(v: any, fence: TreeFence): UiNodes {
  if (!uuid.test(fence.bootId) || v?.format !== "voyemu.ui.v1" || v.source !== "android.ui" || v.profile !== fence.profile || v.session_id !== fence.sessionId || v.android_pid !== fence.pid || v.boot_id !== fence.bootId || integer(v.observer_pid) < 1 || typeof v.complete !== "boolean" || v.atomic_ui_state !== false || !uuid.test(v.observation_id ?? "") || "selector" in v) throw new Error("native tree session/boot/scope fence differs");
  const decimal = (n: unknown) => { if (typeof n !== "string" || !/^[0-9]+$/.test(n)) throw new Error("native tree clock differs"); return BigInt(n); };
  const start = v.started_uptime_ms === undefined ? undefined : integer(Number(decimal(v.started_uptime_ms)));
  const end = integer(Number(decimal(v.completed_uptime_ms)));
  if (end < 0 || (start !== undefined && (start < 0 || end < start || end - start > 30000)) || !v.host_interval || v.host_interval.clock_domain !== "host.steady" || v.host_interval.clock_id !== fence.sessionId || decimal(v.host_interval.end_ns) < decimal(v.host_interval.start_ns)) throw new Error("native tree interval differs");
  if (!Array.isArray(v.screens) || v.screens.length !== 1) throw new Error("native tree primary surface required");
  const surface = v.screens[0];
  const partial = v.complete === false && v.status === "partial" && surface.complete === false && surface.status === "partial";
  if (!partial && (v.complete !== true || v.status !== "ready")) throw new Error("native tree completeness differs");
  if (surface.screen !== "primary" || surface.width !== 1920 || surface.height !== 720 || surface.sdk_display_id !== 0 || surface.display_id !== 0 || surface.scope !== "api28_default_display" || surface.atomic_ui_state !== false || (!partial && (surface.status !== "ok" || surface.complete !== true)) || !Array.isArray(surface.unavailable_windows) || (!partial && surface.unavailable_windows.length > 0) || "selection" in surface || !Array.isArray(surface.windows) || !surface.windows.length || surface.windows.length > 64 || !Array.isArray(surface.nodes) || !surface.nodes.length || surface.nodes.length > 12000) throw new Error("native tree complete primary arrays required");
  const capture = Object.freeze({ source: "voyemu.ui.v1", observationId: v.observation_id });
  const frameSha256 = createHash("sha256").update(JSON.stringify(v)).digest("hex");
  const windows = new Map<number, WindowRef>();
  for (const w of surface.windows) {
    const id = integer(w.id), type = integer(w.type);
    if (id < 0 || windows.has(id) || ![-1, 1, 2, 3, 4, 5].includes(type) || w.component !== null || w.identity_basis !== "accessibility_window_id_and_metadata") throw new Error("native tree window identity differs");
    // type is AccessibilityWindowInfo.getType(), never WindowManager.LayoutParams.type.
    windows.set(id, Object.freeze({ capture, id, type, layer: integer(w.layer), active: bool(w.active), inputFocused: bool(w.input_focused), accessibilityFocused: bool(w.accessibility_focused), bounds: Object.freeze(bounds(w.bounds)), title: text(w.title), rootStatus: "available", rootPackage: text(w.package), frameSha256, captureStart: start, captureEnd: end, scope: "default-display", scopeBasis: "voyemu-guest-accessibility" }));
  }
  const unavailable = new Set<number>();
  for (const value of surface.unavailable_windows) {
    const id = integer(value);
    if (!windows.has(id) || unavailable.has(id)) throw new Error("native tree unavailable window differs");
    unavailable.add(id);
  }
  if (partial && !unavailable.size) throw new Error("native tree partial reason absent");
  const ids = new Map<string, number>(), nodes: UiNodes = [];
  for (const n of surface.nodes) {
    if (typeof n.id !== "string" || !/^[1-9][0-9]*$/u.test(n.id) || ids.has(n.id) || n.identity_scope !== "this_observation_only" || n.actionable_basis !== "node_state_and_viewport_only") throw new Error("native tree node identity differs");
    const window = windows.get(integer(n.window_id));
    if (!window) throw new Error("native tree foreign window");
    const visible = bool(n.visible);
    const raw = bounds(n.bounds, !visible), clipped = bounds(n.clipped_bounds), wb = window.bounds;
    const left = Math.min(1920, Math.max(0, raw[0], wb[0])), top = Math.min(720, Math.max(0, raw[1], wb[1]));
    const right = Math.max(0, Math.min(1920, raw[2], wb[2])), bottom = Math.max(0, Math.min(720, raw[3], wb[3]));
    const expected = right <= left || bottom <= top ? [left, top, left, top] : [left, top, right, bottom];
    if (JSON.stringify(clipped) !== JSON.stringify(expected)) throw new Error("native tree clipping differs");
    const enabled = bool(n.enabled), clickable = bool(n.clickable), password = bool(n.text_redacted);
    // AX state yields a candidate, never dispatch authority: occlusion and touchability remain unknown.
    const candidate = visible && enabled && clickable && clipped[2] > clipped[0] && clipped[3] > clipped[1];
    if (bool(n.actionable_candidate) !== candidate || bool(n.actionable) !== false || n.occlusion !== "unknown" || n.touchability !== "unknown" || (password && (n.text !== "" || n.description !== ""))) throw new Error("native tree privacy/actionable differs");
    const parent = n.parent === null ? null : ids.get(n.parent);
    if (parent === undefined || (parent !== null && nodes[parent].windowRef !== window) || (parent === null && n.package !== window.rootPackage)) throw new Error("native tree parent/window differs");
    ids.set(n.id, nodes.length);
    const metadata = editorMetadata(text(n.text), n.hint, n.showing_hint, n.selection_start, n.selection_end);
    if (password && metadata.hint !== undefined) throw new Error("native tree editor privacy differs");
    nodes.push({ ...metadata, text: text(n.text), description: text(n.description), resource: text(n.resource_id), className: text(n.class), packageName: text(n.package), focused: bool(n.focused), checkable: bool(n.checkable), checked: bool(n.checked), selected: bool(n.selected), scrollable: bool(n.scrollable), enabled, clickable, password, visibleToUser: visible, rawBounds: Object.freeze(raw), bounds: clipped, parent, windowRef: window });
  }
  for (const window of windows.values()) {
    if (nodes.filter(n => n.windowRef === window && n.parent === null).length !== (unavailable.has(window.id) ? 0 : 1)) throw new Error("native tree root/window completeness differs");
  }
  if (partial) throw new NativeTreePartial();
  Object.defineProperty(nodes, "windowRefs", { value: Object.freeze([...windows.values()]), enumerable: false });
  return nodes;
}


/** Wait within the caller deadline for a complete read; never replay input or failed operation IDs.
 * A fixed attempt count can expire during a short window transition while most of the deadline remains. */
export async function primaryObservation<T>(read: (remainingMs: number) => Promise<T>, sameEpoch: () => Promise<void>, deadline: number,
  now: () => number = () => performance.now(), pause: (ms: number) => Promise<void> = ms => new Promise(resolve => setTimeout(resolve, ms))): Promise<T> {
  let unavailable: NativeTreeUnavailable | NativeTreePartial | NativeTreeReadTimeout | undefined;
  for (let attempt = 0; ; attempt++) {
    if (now() >= deadline) throw unavailable ?? new Error("native primary observation condition deadline exhausted");
    if (attempt) {
      await sameEpoch();
      if (now() >= deadline) throw unavailable!;
      await pause(Math.min(50, deadline - now()));
      if (now() >= deadline) throw unavailable!;
    }
    try {
      const observed = await read(Math.min(5000, Math.max(1, Math.floor(deadline - now()))));
      if (now() >= deadline) throw new Error("native primary observation condition deadline exhausted");
      return observed;
    }
    catch (error) {
      if (!(error instanceof NativeTreeUnavailable) && !(error instanceof NativeTreePartial) && !(error instanceof NativeTreeReadTimeout)) throw error;
      unavailable = error;
    }
  }
}


/** One initial owned observation, distinct from a completed reboot. */
export class InitialObservation {
  private consumed = false;
  consume(current: TreeFence): boolean {
    if (this.consumed) return false;
    this.consumed = true;
    if (!uuid.test(current.bootId) || !/^[0-9a-f]{32}$/u.test(current.sessionId) ||
        !Number.isSafeInteger(current.pid) || current.pid < 1 || !current.profile)
      throw new Error("native initial observation fence differs");
    return true;
  }
}

/** Same guarded first-read path for initial admission and closed reboot. */
export async function ownedPrimaryObservation<T>(read: (remainingMs: number) => Promise<T>,
  sameEpoch: () => Promise<void>, current: TreeFence, initial: InitialObservation,
  transitions: BootTransition, deadline: number, now: () => number = () => performance.now(), postbootDeadline?: number): Promise<T> {
  await sameEpoch();
  const transition = transitions.consume(current);
  const bootstrap = initial.consume(current);
  if (transition && postbootDeadline !== undefined) {
    let reconnect = true;
    return primaryObservation(() => postbootPrimaryObservation(async remaining => {
      const recover = reconnect; reconnect = false;
      return reconnectObservation(read, sameEpoch, recover, Math.min(postbootDeadline, now() + remaining), now);
    }, sameEpoch, true, postbootDeadline, now), sameEpoch, postbootDeadline, now);
  }
  let firstRead = true;
  return primaryObservation(async () => {
    const recover = firstRead && (transition || bootstrap);
    firstRead = false;
    return reconnectObservation(read, sameEpoch, recover, deadline, now);
  }, sameEpoch, deadline, now);
}

/** Single-use capability, armed only by the caller's closed, completed reboot proof. */
export class BootTransition {
  private pending: { oldBoot: string; next: TreeFence } | null = null;
  arm(oldBoot: string, next: TreeFence): void {
    if (!uuid.test(oldBoot) || !uuid.test(next.bootId) || oldBoot === next.bootId ||
        !/^[0-9a-f]{32}$/u.test(next.sessionId) || !Number.isSafeInteger(next.pid) || next.pid < 1 || !next.profile)
      throw new Error("native closed boot transition differs");
    this.pending = { oldBoot, next: { ...next } };
  }
  consume(current: TreeFence): boolean {
    const pending = this.pending;
    this.pending = null; // First read consumes it even when successful or rejected.
    if (!pending) return false;
    if (pending.next.sessionId !== current.sessionId || pending.next.pid !== current.pid || pending.next.bootId !== current.bootId || pending.next.profile !== current.profile)
      throw new Error("native transition session/PID/boot differs");
    return true;
  }
}

/** At most one new read after one exact reconnect rejection in a proved new epoch. */
export async function reconnectObservation<T>(read: (remainingMs: number) => Promise<T>,
  sameEpoch: () => Promise<void>, transition: boolean, deadline: number,
  now: () => number = () => performance.now()): Promise<T> {
  const remaining = () => {
    const ms = Math.floor(deadline - now());
    if (ms < 1) throw new Error("native reconnect observation deadline exhausted");
    return Math.min(5000, ms);
  };
  try {
    const value = await read(remaining());
    if (transition) await sameEpoch();
    remaining();
    return value;
  } catch (error) {
    if (!transition || !(error instanceof NativeTreeReconnectStale)) throw error;
    await sameEpoch(); // Independent owned session/PID and closed NEW boot, not the error text.
    const value = await read(remaining()); // Fresh invocation creates a fresh UUID; no failed op replay.
    await sameEpoch();
    remaining();
    return value; // Second stale, malformed result, timeout or changed boot all remain fatal.
  }
}

/** New read-only requests during a proved postboot capture phase; failed receipts stay failed. */
export async function postbootPrimaryObservation<T>(read: (remainingMs: number) => Promise<T>,
  sameEpoch: () => Promise<void>, transition: boolean, deadline: number,
  now: () => number = () => performance.now(),
  pause: (ms: number) => Promise<void> = ms => new Promise(resolve => setTimeout(resolve, ms))): Promise<T> {
  if (!transition) throw new Error("postboot observation requires closed boot transition");
  let last: unknown;
  for (let attempt = 0; attempt < 3601; attempt++) {
    await sameEpoch();
    const remaining = Math.floor(deadline - now());
    if (remaining < 1) throw last ?? new Error("postboot observation deadline exhausted");
    try {
      const value = await read(Math.min(5000, remaining));
      await sameEpoch();
      if (now() >= deadline) throw new Error("postboot observation deadline exhausted");
      return value;
    } catch (error) {
      // The strict response decoder constructs this class only for verified read failures.
      // Exact reason; optional typed diagnostics cannot make another reason recoverable.
      if (!(error instanceof NativeTreeUnavailable) || !/^native primary observation unavailable: TREE_REJECTED: guest could not verify current UI tree; reason=empty_tree_unverified(?:; error_type=[A-Za-z]+Exception)?(?:; cause_type=[A-Za-z]+Exception)? \(not_started; retryable=false\)$/.test(error.message)) throw error;
      last = error;
      await sameEpoch();
      if (now() >= deadline) throw error;
      await pause(Math.min(50, deadline - now()));
    }
  }
  throw last ?? new Error("postboot observation attempt limit exhausted");
}
