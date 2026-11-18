import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { inflateSync } from "node:zlib";
import type { ProcessAdapter, Invocation } from "../../release/emulator-gate.ts";
import { driverInvocation } from "./driver.ts";
import { installerDiagnostic } from "./installer-diagnostics.ts";

export type VoyemuCapture = Readonly<{
  root: string; node: string; profile: string; runtimePid: number; runtimePort: number;
  nodeSha256: string; cliSha256: string;
}>;

export type UiNode = {
  hint?: string;
  showingHint?: boolean;
  selectionStart?: number;
  selectionEnd?: number;
  focusable?: boolean;
  longClickable?: boolean;
  password?: boolean;
  index?: number;
  rawBounds?: readonly number[];
  windowRef?: WindowRef;
  visibleToUser?: boolean;
  text: string;
  packageName: string;
  focused: boolean;
  checkable: boolean;
  description: string;
  resource: string;
  className: string;
  checked: boolean;
  selected: boolean;
  clickable: boolean;
  enabled: boolean;
  scrollable: boolean;
  bounds: [number, number, number, number];
  parent: number | null;
};
/** AX text remains raw; hint state is evidence, never inferred from a caption. */
export function editorMetadata(rawText: string, hint: unknown, showing: unknown, start: unknown, end: unknown): Pick<UiNode, "hint" | "showingHint" | "selectionStart" | "selectionEnd"> {
  if ([hint, showing, start, end].every(value => value === undefined)) return {};
  if (typeof hint !== "string" || hint.length > 16384 || typeof showing !== "boolean" || !Number.isSafeInteger(start) || !Number.isSafeInteger(end)) throw new Error("invalid editor metadata");
  const a = start as number, b = end as number;
  if (!(a === -1 && b === -1) && !(a >= 0 && b >= a && b <= (showing ? 0 : rawText.length))) throw new Error("invalid editor selection");
  if (showing && (!hint || rawText !== hint)) throw new Error("inconsistent editor hint");
  return { hint, showingHint: showing, selectionStart: a, selectionEnd: b };
}
export function editorValue(node: UiNode): string {
  if (node.password) throw new Error("redacted editor value unavailable");
  if (node.showingHint !== true) return node.text;
  editorMetadata(node.text, node.hint, node.showingHint, node.selectionStart, node.selectionEnd);
  if (!node.enabled || node.password || node.visibleToUser !== true || node.className !== "android.widget.EditText" || node.windowRef?.type !== 1 || !focusedApplication(node)) throw new Error("hint requires a visible application editor");
  return "";
}
export function editorCaretAtEnd(node: UiNode): boolean {
  const value = editorValue(node);
  return node.selectionStart !== undefined && node.selectionEnd !== undefined && node.selectionStart === value.length && node.selectionEnd === value.length;
}
function entity(value: string): string {
  return value.replace(/&(amp|lt|gt|quot|apos|#\d+|#x[0-9a-f]+);/giu, (_, token: string) => {
    const named: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
    if (token in named) return named[token];
    const number = token.startsWith("#x")
      ? parseInt(token.slice(2), 16)
      : parseInt(token.slice(1), 10);
    if (number > 0x10ffff || number === 0) throw new Error("invalid UI XML entity");
    return String.fromCodePoint(number);
  });
}
/** Parse only the closed hierarchy/node format produced by Android UiAutomator. */
function parseLegacyUi(xml: string): UiNode[] {
  if (
    Buffer.byteLength(xml) > 8 * 1024 * 1024 ||
    /<!/u.test(xml) ||
    !xml.includes("<hierarchy") ||
    !xml.trim().endsWith("</hierarchy>")
  )
    throw new Error("invalid bounded UI hierarchy envelope");
  const nodes: UiNode[] = [],
    stack: number[] = [];
  for (const match of xml.matchAll(/<\/?node\b[^>]*>/gu)) {
    const tag = match[0];
    if (tag.startsWith("</")) {
      if (stack.pop() === undefined) throw new Error("unbalanced UI node close");
      continue;
    }
    const attributes: Record<string, string> = {};
    for (const attribute of tag.matchAll(/([a-zA-Z][a-zA-Z0-9_-]*)="([^"]*)"/gu)) {
      if (attribute[1] in attributes) throw new Error("duplicate UI attribute");
      attributes[attribute[1]] = entity(attribute[2]);
    }
    const bounds = /^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$/u.exec(attributes.bounds ?? "");
    if (
      !bounds ||
      bounds.slice(1).some((n) => !Number.isSafeInteger(Number(n)) || Math.abs(Number(n)) > 16384)
    )
      throw new Error("invalid UI bounds");
    nodes.push({
      text: attributes.text ?? "",
      packageName: attributes.package ?? "",
      focused: attributes.focused === "true",
      checkable: attributes.checkable === "true",
      description: attributes["content-desc"] ?? "",
      resource: attributes["resource-id"] ?? "",
      className: attributes.class ?? "",
      checked: attributes.checked === "true",
      selected: attributes.selected === "true",
      clickable: attributes.clickable === "true",
      enabled: attributes.enabled !== "false",
      scrollable: attributes.scrollable === "true",
      bounds: bounds.slice(1).map(Number) as UiNode["bounds"],
      parent: stack.at(-1) ?? null,
    });
    if (nodes.length > 8192) throw new Error("UI hierarchy has too many nodes");
    if (!tag.endsWith("/>")) stack.push(nodes.length - 1);
  }
  if (stack.length || nodes.length === 0) throw new Error("empty or unbalanced UI hierarchy");
  return nodes;
}
export type WindowRef = Readonly<{
  capture: Readonly<object>; id: number; type: number; layer: number; active: boolean; inputFocused: boolean;
  accessibilityFocused: boolean; bounds: readonly number[]; title: string;
  rootStatus: string; rootPackage: string; frameSha256: string;
  captureStart?: number; captureEnd: number; scope: "default-display";
  scopeBasis: "aosp28-default-window-observer" | "voyemu-guest-accessibility";
}>;

export type UiNodes = UiNode[] & { readonly windowRefs?: readonly WindowRef[] };

/** The accepted API28 interval is bounded, not an atomic compositor capture. */
export function parseUi(xml: string): UiNodes {
  if (/^\s*(?:<\?xml[^?]*\?>\s*)?<hierarchy\b/u.test(xml)) return parseLegacyUi(xml);
  if (Buffer.byteLength(xml) > 6 * 1024 * 1024 || /<!/u.test(xml) || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\ud800-\udfff\ufffe\uffff]/u.test(xml)) throw new Error("invalid bounded window XML");
  const input = xml.replace(/^<\?xml version="1.0" encoding="UTF-8"\?>\s*/u, "");
  type Element = { name: string; attrs: Record<string, string>; children: Element[] };
  const stack: Element[] = []; let root: Element | undefined, offset = 0;
  for (const token of input.matchAll(/<[^>]*>/gu)) {
    if (input.slice(offset, token.index).trim()) throw new Error("unexpected UI XML text");
    offset = token.index! + token[0].length;
    const close = /^<\/([a-z][a-z0-9-]*)>$/u.exec(token[0]);
    if (close) { if (stack.pop()?.name !== close[1]) throw new Error("malformed UI nesting"); continue; }
    const start = /^<([a-z][a-z0-9-]*)([\s\S]*?)(\/)?>$/u.exec(token[0]);
    if (!start) throw new Error("invalid UI tag");
    const attrs: Record<string, string> = {}; let consumed = 0;
    for (const attribute of start[2].matchAll(/\s+([a-z][a-z0-9-]*)="([^"<]*)"/gu)) {
      if (attribute.index !== consumed || attribute[1] in attrs || /&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-f]+);)/iu.test(attribute[2])) throw new Error("invalid UI attribute/entity");
      consumed += attribute[0].length; attrs[attribute[1]] = entity(attribute[2]);
      if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\ud800-\udfff]/u.test(attrs[attribute[1]])) throw new Error("invalid XML character");
    }
    if (start[2].slice(consumed).trim()) throw new Error("invalid UI attributes");
    const element = { name: start[1], attrs, children: [] as Element[] };
    if (stack.length) stack.at(-1)!.children.push(element);
    else { if (root) throw new Error("multiple UI roots"); root = element; }
    if (!start[3]) stack.push(element);
    if (stack.length > 84) throw new Error("UI depth bound");
  }
  if (stack.length || input.slice(offset).trim() || !root) throw new Error("incomplete UI XML");
  const fields = (e: Element, names: string[]) => {
    if (Object.keys(e.attrs).sort().join() !== names.sort().join()) throw new Error(`invalid ${e.name} fields`);
  };
  const number = (value: string) => { if (!/^(?:0|[1-9][0-9]*)$/u.test(value) || !Number.isSafeInteger(Number(value))) throw new Error("invalid UI integer"); return Number(value); };
  const signed32 = (value: string) => { const parsed = Number(value); if (!/^(?:0|-?[1-9][0-9]*)$/u.test(value) || !Number.isInteger(parsed) || parsed < -2147483648 || parsed > 2147483647) throw new Error("invalid UI signed integer"); return parsed; };
  const bool = (value: string) => { if (!["true", "false"].includes(value)) throw new Error("invalid UI boolean"); return value === "true"; };
  const bounds = (value: string, hidden = false): UiNode["bounds"] => {
    const match = /^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$/u.exec(value);
    if (!match || match.slice(1).some(v => !Number.isSafeInteger(Number(v)) || Math.abs(Number(v)) > 16384)) throw new Error("invalid UI bounds");
    const box = match.slice(1).map(Number) as UiNode["bounds"];
    if (!hidden && (box[2] < box[0] || box[3] < box[1])) throw new Error("reversed UI bounds"); return box;
  };
  fields(root, ["schema", "sdk", "scope", "scope-basis", "capture-start-uptime-ms", "capture-end-uptime-ms", "window-metadata-stable"]);
  const a = root.attrs, captureStart = number(a["capture-start-uptime-ms"]), captureEnd = number(a["capture-end-uptime-ms"]);
  if (root.name !== "ui-snapshot" || a.schema !== "voboost-ui-windows-v1" || a.sdk !== "28" || a.scope !== "default-display" || a["scope-basis"] !== "aosp28-default-window-observer" || a["window-metadata-stable"] !== "true" || captureEnd < captureStart || captureEnd - captureStart > 30000 || root.children.length !== 1) throw new Error("unsupported UI capture scope/interval");
  const display = root.children[0]; fields(display, ["id"]);
  if (display.name !== "display" || display.attrs.id !== "0" || !display.children.length || display.children.length > 64) throw new Error("invalid UI display");
  const capture = Object.freeze({});
  const nodes: UiNodes = [], windowRefs: WindowRef[] = [], ids = new Set<number>(), frameSha256 = createHash("sha256").update(xml).digest("hex");
  for (const window of display.children) {
    fields(window, ["id", "type", "layer", "active", "input-focused", "accessibility-focused", "bounds", "title", "root-status", "root-package"]);
    const w = window.attrs, id = number(w.id), type = signed32(w.type);
    if (window.name !== "window" || ids.has(id) || ![-1, 1, 2, 3, 4, 5].includes(type) || !["available", "unavailable"].includes(w["root-status"]) || window.children.length !== (w["root-status"] === "available" ? 1 : 0) || (w["root-status"] === "unavailable" && w["root-package"] !== "")) throw new Error("invalid UI window/root");
    ids.add(id);
    const windowRef: WindowRef = Object.freeze({ capture, id, type, layer: signed32(w.layer), active: bool(w.active), inputFocused: bool(w["input-focused"]), accessibilityFocused: bool(w["accessibility-focused"]), bounds: Object.freeze(bounds(w.bounds)), title: w.title, rootStatus: w["root-status"], rootPackage: w["root-package"], frameSha256, captureStart, captureEnd, scope: "default-display", scopeBasis: "aosp28-default-window-observer" });
    windowRefs.push(windowRef);
    const visit = (e: Element, parent: number | null) => {
      fields(e, ["index", "window-id", "text", "resource-id", "class", "package", "content-desc", "checkable", "checked", "clickable", "enabled", "focusable", "focused", "scrollable", "long-clickable", "password", "selected", "visible-to-user", "bounds", ...["hint", "showing-hint", "selection-start", "selection-end"].filter(key => key in e.attrs)]);
      const n = e.attrs; number(n.index);
      if (e.name !== "node" || number(n["window-id"]) !== id || (parent === null && (n.package !== w["root-package"] || n.index !== "0"))) throw new Error("foreign window node");
      for (const key of ["focusable", "long-clickable", "password"]) bool(n[key]);
      if (bool(n.password) && ["hint", "showing-hint", "selection-start", "selection-end"].some(key => key in n)) throw new Error("UI editor privacy differs");
      const visible = bool(n["visible-to-user"]);
      const index = nodes.length, rawBounds = bounds(n.bounds, !visible), wb = windowRef.bounds;
      const visibleBounds: UiNode["bounds"] = [Math.max(rawBounds[0], wb[0]), Math.max(rawBounds[1], wb[1]), Math.min(rawBounds[2], wb[2]), Math.min(rawBounds[3], wb[3])];
      nodes.push({ ...editorMetadata(n.text, n.hint, n["showing-hint"] === undefined ? undefined : bool(n["showing-hint"]), n["selection-start"] === undefined ? undefined : signed32(n["selection-start"]), n["selection-end"] === undefined ? undefined : signed32(n["selection-end"])), index: number(n.index), focusable: bool(n.focusable), longClickable: bool(n["long-clickable"]), password: bool(n.password), rawBounds: Object.freeze(rawBounds), windowRef, visibleToUser: visible, text: n.text, packageName: n.package, focused: bool(n.focused), checkable: bool(n.checkable), description: n["content-desc"], resource: n["resource-id"], className: n.class, checked: bool(n.checked), selected: bool(n.selected), clickable: bool(n.clickable), enabled: bool(n.enabled), scrollable: bool(n.scrollable), bounds: visibleBounds, parent });
      if (nodes.length > 12000) throw new Error("UI node bound");
      for (const [ordinal, child] of e.children.entries()) {
        if (child.attrs.index !== String(ordinal)) throw new Error("invalid UI child index");
        visit(child, index);
      }
    };
    for (const child of window.children) visit(child, null);
  }
  Object.defineProperty(nodes, "windowRefs", { value: Object.freeze(windowRefs), enumerable: false });
  return nodes;
}

/** multimedia.launch acceptance precedes window publication; this grants only another read. */
export function vehicleLaunchPending(nodes: UiNodes): boolean {
  const windows = nodes.windowRefs ?? [];
  const owner = "com.qinggan.app.vehiclesetting";
  // Keep the existing, separately guarded unfocused Vehicle recovery route.
  if (windows.some(window => window.rootPackage === owner && window.rootStatus === "available")) return false;
  const focus = windows.filter(window => window.active && window.inputFocused);
  return focus.length === 1 && [1, 3].includes(focus[0].type) &&
    focus[0].rootStatus === "available" &&
    ["com.qinggan.app.setting", "com.qinggan.app.launcher"].includes(focus[0].rootPackage);
}

export function focusedApplication(node: UiNode): boolean {
  return !node.windowRef || (node.visibleToUser === true && [1, 3].includes(node.windowRef.type) && node.windowRef.inputFocused);
}

/** Only the captured OEM Force EV confirmation can own unclassified gesture focus. */
export function oemForcedEvAgree(nodes: UiNodes): UiNode {
  const owner = "com.qinggan.app.vehiclesetting";
  const focus = new Set((nodes.windowRefs ?? []).filter(w => w.active && w.inputFocused));
  if (focus.size !== 1) throw new Error("unique OEM disclaimer focus required");
  const window = [...focus][0];
  if (window.type !== -1 || window.rootPackage !== owner || window.rootStatus !== "available") throw new Error("OEM disclaimer window differs");
  const owned = nodes.filter(n => n.windowRef === window && n.packageName === owner && n.visibleToUser === true && n.enabled);
  const root = owned.filter(n => n.parent === null && n.resource === `${owner}:id/view_bg` && n.className === "android.widget.RelativeLayout");
  const dialog = owned.filter(n => n.resource === `${owner}:id/fragment_forceev_dialog` && n.className === "android.widget.RelativeLayout" && n.parent === nodes.indexOf(root[0]));
  const agree = owned.filter(n => n.resource === `${owner}:id/disclaimer_agree` && n.className === "android.widget.Button" && n.clickable && n.parent === nodes.indexOf(dialog[0]));
  if (root.length !== 1 || dialog.length !== 1 || agree.length !== 1) throw new Error("OEM disclaimer hierarchy differs");
  center(agree[0]);
  return agree[0];
}

/** Pointer authority is display-local; Accessibility focus may remain on secondary. */
export function launcherPrimaryPointerWindow(nodes: UiNode[]): WindowRef {
  const owner="com.qinggan.app.launcher",windows=(nodes as UiNodes).windowRefs;
  if(!windows || !windows.length || new Set(windows.map(w=>w.id)).size!==windows.length || windows.some(w=>w.scope!=="default-display" || w.scopeBasis!=="voyemu-guest-accessibility" || w.rootStatus!=="available")) throw new Error("launcher pointer complete native primary windows required");
  const mains=windows.filter(w=>w.type===1 && w.rootPackage===owner);
  if(mains.length!==1) throw new Error("launcher pointer unique primary main required");
  const main=mains[0];
  if(windows.some(w=>(w.active || w.inputFocused) && w!==main)) throw new Error("launcher pointer foreign or overlay active window");
  if(main.bounds.length!==4 || main.bounds[0]!==0 || main.bounds[1]!==0 || main.bounds[2]!==1920 || main.bounds[3]!==720 ||
     !nodes.some(n=>n.windowRef===main && n.parent===null && n.packageName===owner && n.enabled && n.visibleToUser===true)) throw new Error("launcher pointer visible primary main required");
  return main;
}
export function launcherPointerNode(nodes:UiNode[],predicate:(n:UiNode)=>boolean):UiNode {
  const main=launcherPrimaryPointerWindow(nodes);
  const matches=nodes.filter(n=>n.windowRef===main && n.packageName===main.rootPackage && n.enabled && n.visibleToUser===true && predicate(n));
  if(matches.length!==1) throw new Error("launcher pointer selector is absent or ambiguous");
  center(matches[0]);return matches[0];
}

/** The OEM left dock uses a vendor window type that AOSP reports as unclassified. */
export function launcherDock(nodes: UiNode[], nativePointer = false): UiNode {
  const owner = "com.qinggan.app.launcher";
  const matches = nodes.filter(node => node.packageName === owner && node.resource === `${owner}:id/screen_up_all_app_icon`);
  if (matches.length !== 1) throw new Error("unique OEM launcher dock required");
  const node = matches[0], dock = node.windowRef;
  if (!dock || ![-1, 3].includes(dock.type) || dock.rootPackage !== owner || dock.rootStatus !== "available" || dock.scope !== "default-display" || !node.enabled || !node.clickable || node.visibleToUser !== true || node.className !== "android.widget.RadioButton")
    throw new Error("OEM launcher dock identity differs");
  center(node);
  const main = new Set(nodes.filter(other => other.windowRef?.type === 1 && other.windowRef.active && other.windowRef.inputFocused && other.windowRef.rootPackage === owner && other.windowRef.rootStatus === "available").map(other => other.windowRef!));
  if (!nativePointer && main.size !== 1) throw new Error("focused primary OEM launcher window required");
  const application = nativePointer ? launcherPrimaryPointerWindow(nodes) : [...main][0], d = dock.bounds, a = application.bounds, n = node.bounds;
  if (application.scope !== "default-display" || dock.capture !== application.capture || dock.frameSha256 !== application.frameSha256 || dock.id === application.id || dock.layer <= application.layer || d[0] !== a[0] || d[2] <= d[0] || d[2] >= a[2] || d[1] < a[1] || d[3] > a[3] || n[0] < d[0] || n[1] < d[1] || n[2] > d[2] || n[3] > d[3])
    throw new Error("OEM launcher dock capture or containment differs");
  return node;
}

/** One accepted launcher launch; wait only for fresh read-only dock readiness. */
export async function awaitLauncherDock(read: () => Promise<UiNode[]>, deadline: number, now = () => performance.now(), nativePointer = false): Promise<UiNode> {
  let last: unknown = null;
  while (now() < deadline) {
    const nodes = await read(); // Native snapshot retains ownership, boot and Enforcing fences.
    if (now() >= deadline) break;
    try { return launcherDock(nodes,nativePointer); }
    catch (error) {
      // Only absent dock/application may be an in-progress launch. Ambiguous dock,
      // invalid geometry, foreign ownership or malformed identity never become input.
      const matches = nodes.filter(n => n.packageName === "com.qinggan.app.launcher" && n.resource === "com.qinggan.app.launcher:id/screen_up_all_app_icon");
      const message = error instanceof Error ? error.message : String(error);
      const main = new Set(nodes.filter(n => n.windowRef?.type === 1 && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootPackage === "com.qinggan.app.launcher" && n.windowRef.rootStatus === "available").map(n => n.windowRef));
      if ((message !== "unique OEM launcher dock required" || matches.length !== 0) && (message !== "focused primary OEM launcher window required" || main.size !== 0)) throw error;
      last = error;
    }
  }
  throw new Error("OEM launcher dock readiness deadline expired: " + String(last));
}

export function center(node: UiNode): [number, number] {
  const [left, top, right, bottom] = node.bounds;
  if (right <= left || bottom <= top || left < 0 || top < 0 || right > 4096 || bottom > 4096)
    throw new Error("UI node is outside the controllable display");
  return [Math.floor((left + right) / 2), Math.floor((top + bottom) / 2)];
}
export function uniqueNode(nodes: UiNode[], predicate: (node: UiNode) => boolean): UiNode {
  const found = nodes.filter(
    (n) => n.enabled && focusedApplication(n) && predicate(n) && n.bounds[2] > n.bounds[0] && n.bounds[3] > n.bounds[1],
  );
  if (found.length !== 1) throw new Error(`UI selector matched ${found.length} visible nodes`);
  return found[0];
}

export function agentControl(nodes: UiNode[], packageName: string, title: string): UiNode | null {
  const labels = nodes.filter((n) => focusedApplication(n) && n.packageName === packageName && n.text === title);
  if (labels.length !== 1) return null;
  const control = uniqueNode(
    nodes,
    (n) => n.packageName === packageName && n.parent === labels[0].parent && n.checkable,
  );
  return control.bounds[3] - control.bounds[1] >= 40 ? control : null;
}

/** Certified helper rejection occurs before ACTION_DOWN; never inferred from generic failure. */
export class GesturePreInputDrift extends Error {}

/** The optional prefix is read-only; the exact terminal guard precedes the single tap DOWN. */
export function safeGestureDiagnostics(stderr: string): boolean {
  const all = stderr.split(/\r?\n/).filter(Boolean);
  // Unknown diagnostics cannot establish that no input occurred.
  if (all.some(line => ![
    /^Emulator acceptance failed: emulator operation failed \(Some\(72\)\): ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0$/u,
    /^ui-observation stage=(active-root-before-windows|windows-before|windows-fresh|nodes-complete|metadata-after) reason=(active-root-waiting|active-root-outside-default-display|timing) kind=none syscall=none errno=none /u,
    /^ui-observation stage=gesture /u,
    /^ui-observation service phase=(initial|root-fallback|after-set) present=true flags=\d+ capabilities=\d+$/u,
    /^ui-observation active-root window-id=\d+ reads=\d+$/u,
    /^ui-observation window id=\d+ type=-?\d+ layer=-?\d+$/u,
    /^ui-capture diagnostic=(run|native)\.log bytes=\d+ sha256=[a-f0-9]{64}$/u,
    /^ui-capture native-window pid=\d+ start=[\d.]+ end=[\d.]+ reader_exit=0$/u,
    /^ui-native --------- beginning of main$/u,
    /^ui-capture timing startup_ms=\d+ handshake_ms=\d+ run_ms=\d+ exit_ms=\d+ cleanup_ms=\d+ total_ms=\d+$/u,
  ].some(pattern => pattern.test(line)))) return false;
  const lines = all.filter(line => line.startsWith("ui-observation stage=gesture "));
  const terminal = /^ui-observation stage=gesture reason=gesture-window-or-target-drift kind=java.lang.IllegalStateException syscall=none errno=none windows=\d+ window-reads=\d+ nodes=\d+ pending-ipc=none pending-ipc-ms=0 refresh-calls=\d+ refresh-ms=\d+ child-calls=\d+ child-ms=\d+$/u;
  const observation = /^ui-observation stage=gesture reason=active-root-outside-default-display kind=none syscall=none errno=none windows=\d+ window-reads=\d+ nodes=\d+ pending-ipc=none pending-ipc-ms=0 refresh-calls=\d+ refresh-ms=\d+ child-calls=\d+ child-ms=\d+$/u;
  const terminalIndex = all.findIndex(line => terminal.test(line));
  if (terminalIndex < 0 || all.slice(terminalIndex + 1).some(line => line.startsWith("ui-observation "))) return false;
  return (lines.length === 1 && terminal.test(lines[0])) || (lines.length === 2 && observation.test(lines[0]) && terminal.test(lines[1]));
}

/** Android actions mapped to the SDK protocol W3C DOM key names. */
export function nativeKey(key: unknown): string {
  const keys: Record<string, string> = { home: "GoHome", back: "GoBack", wake: "WakeUp", sleep: "Standby", enter: "Enter", delete: "Backspace" };
  if (typeof key !== "string" || !Object.hasOwn(keys, key)) throw new Error("native SDK key mapping unavailable");
  return keys[key];
}

/** Product navigation stays in the gate; Voyemu accepts generic package identities. */
export function nativeApp(request: Readonly<Record<string, unknown>>): { package: string; component: string; screen: "primary" | "secondary" } | undefined {
  const apps = {
    "open-settings": ["com.qinggan.app.setting", ".activity.MainActivity", "primary"],
    "open-vehicle-settings": ["com.qinggan.app.vehiclesetting", ".CarSettingActivity", "primary"],
    "open-navigation": ["com.pateonavi.naviapp", "com.pateo.lightnavi.activity.NaviAutoActivity", "primary"],
    "open-primary-launcher": ["com.qinggan.app.launcher", ".activity.MainActivity", "primary"],
    "open-secondary-launcher": ["com.qinggan.app.launcher", "com.qinggan.secondlauncher.activity.AllAppActivity", "secondary"],
  } as const;
  if (typeof request.action !== "string" || !Object.hasOwn(apps, request.action)) return undefined;
  const [packageName, activity, screen] = apps[request.action as keyof typeof apps];
  return { package: packageName, component: packageName + "/" + activity, screen };
}
export function nativeAppReceipt(result: any, expected: { package: string; component: string; screen: "primary" | "secondary"; boot_id: string }): void {
  if (result?.ok !== true || result.accepted !== true || result.package !== expected.package || typeof result.component !== "string" || result.component.replace("/.", "/" + expected.package + ".") !== expected.component.replace("/.", "/" + expected.package + ".") || result.screen !== expected.screen || result.boot_id !== expected.boot_id || !Number.isSafeInteger(result.display_id) || result.display_id < 0 || (expected.screen === "primary" && result.display_id !== 0) || result.application_ready !== false || result.visible_effect_verified !== false)
    throw new Error("native application acceptance differs; inspect operation before replay");
}

/** Explicit pressed samples let OEM interception observe subsequent movement. */
export function gestureTrajectory(from: readonly [number, number], to: readonly [number, number], duration: number, hold = 0): Array<{ x: number; y: number; at_ms: number }> {
  if (![...from, ...to, duration, hold].every(Number.isSafeInteger) || from.length !== 2 || to.length !== 2 || [from, to].some(([x, y]) => x < 0 || x >= 1920 || y < 0 || y >= 720) || duration < 0 || duration > 2000 || hold < 0 || hold > duration || (hold === duration && (from[0] !== to[0] || from[1] !== to[1])))
    throw new Error("invalid bounded gesture trajectory");
  if (duration === 0) return [{ x: from[0], y: from[1], at_ms: 0 }];
  const steps = Math.ceil(duration / 32);
  return Array.from({ length: steps + 1 }, (_, step) => {
    const at_ms = Math.round(duration * step / steps);
    const fraction = hold === duration ? 0 : Math.max(0, (at_ms - hold) / (duration - hold));
    return { x: Math.round(from[0] + (to[0] - from[0]) * fraction), y: Math.round(from[1] + (to[1] - from[1]) * fraction), at_ms };
  });
}

/** Project only identities actually observed in the current native primary tree. */
export function nativePointerRequest(request: Readonly<Record<string, any>>, tree: any, fence: { sessionId: string; pid: number; boot: string }): { op: "input.tap" | "input.gesture"; fields: Record<string, unknown> } {
  if (!["tap", "swipe", "drag", "gesture", "tbox-burst"].includes(request.action)) throw new Error("native pointer action unsupported");
  if (tree?.session_id !== fence.sessionId || tree.android_pid !== fence.pid || tree.boot_id !== fence.boot || tree.source !== "android.ui" || tree.complete !== true || typeof tree.source_instance !== "string" || !tree.source_instance || typeof tree.revision !== "string" || !tree.revision || tree.screens?.length !== 1 || tree.screens[0].screen !== "primary")
    throw new Error("native input source fence differs");
  const surface = tree.screens[0], w = request.expectedWindow, t = request.expectedTarget;
  const rectangle = (r: any) => r && ["x", "y", "width", "height"].every(k => Number.isSafeInteger(r[k])) ? `[${r.x},${r.y}][${r.x+r.width},${r.y+r.height}]` : null;
  if (!w || !t || w.displayId !== 0) throw new Error("native input lacks observed primary target");
  const windows = surface.windows.filter((n: any) => n.id === w.id && n.type === w.windowType && n.package === w.packageName && n.title === w.title && rectangle(n.bounds) === w.bounds);
  const targets = surface.nodes.filter((n: any) => n.window_id === t.windowId && n.package === t.packageName && n.resource_id === t.resource && n.class === t.className && n.text === t.text && rectangle(n.bounds) === t.bounds);
  if (windows.length !== 1 || targets.length !== 1) throw new Error("native input target is stale or ambiguous");
  const target = targets[0];
  const targetWindows = surface.windows.filter((n: any) => n.id === target.window_id && n.type === t.windowType && n.package === t.rootPackage && n.title === t.windowTitle && rectangle(n.bounds) === t.windowBounds);
  if (targetWindows.length !== 1) throw new Error("native input target window differs");
  if (target.visible !== true || target.enabled !== true || (request.action === "gesture" ? false : request.action === "swipe" ? target.scrollable !== true : target.clickable !== true))
    throw new Error("native input target is unavailable");
  const project = (value: any, keys: string[]) => Object.fromEntries(keys.map(key => [key, value[key]]));
  const expected = {
    boot_id: tree.boot_id, source_instance: tree.source_instance, revision: tree.revision,
    window: project(targetWindows[0], ["id", "type", "layer", "bounds", "title", "package"]),
    target: project(target, ["window_id", "resource_id", "package", "class", "text", "bounds", "clipped_bounds"]),
  };
  if (["tap", "tbox-burst"].includes(request.action)) return { op: "input.tap", fields: { screen: "primary", x: request.x, y: request.y, expected, ...(request.action === "tbox-burst" ? { count: 5, interval_ms: 100 } : {}) } };
  if ("points" in request) {
    if (!["drag", "gesture"].includes(request.action)) throw new Error("pressed points action unsupported");
    const points = boundedGesturePoints(request.points);
    const b = target.clipped_bounds;
    if (!b || points[0].x < b.x || points[0].x >= b.x + b.width || points[0].y < b.y || points[0].y >= b.y + b.height) throw new Error("gesture first point outside observed target");
    const route = targetWindows[0].bounds;
    if (points.some(p => p.x < route.x || p.x >= route.x + route.width || p.y < route.y || p.y >= route.y + route.height)) throw new Error("gesture route outside observed window");
    return { op: "input.gesture", fields: { screen: "primary", points, ...(request.action === "drag" ? { intent: "drag" } : {}), expected } };
  }
  const duration = request.milliseconds;
  const hold = request.action === "drag" ? Math.min(600, Math.floor(duration / 2)) : 0;
  const points = gestureTrajectory([request.x1, request.y1], [request.x2, request.y2], duration, hold);
  return { op: "input.gesture", fields: { screen: "primary", points, ...(request.action === "gesture" ? {} : { intent: request.action === "swipe" ? "scroll" : "drag" }), expected } };
}

/** Reboot completion does not prove application readiness or SELinux mode. */
export function nativeRebootReceipt(result: any, expected: { sessionId: string; pid: number; boot: string }): { before: string; after: string } {
  const uuid = (value: unknown) => typeof value === "string" && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);
  if (result?.ok !== true || result.session_id !== expected.sessionId || result.mode !== "warm" || result.status !== "completed" || result.scope !== "guest_boot_changed" || result.boot_id_before !== expected.boot || !uuid(result.boot_id) || result.boot_id === expected.boot || result.android_pid_before !== expected.pid || result.android_pid !== expected.pid || !Number.isSafeInteger(result.generation_before) || result.generation_before < 0 || result.generation !== result.generation_before + 1 || result.application_ready !== false)
    throw new Error("native reboot receipt differs; inspect operation before replay");
  return { before: result.boot_id_before, after: result.boot_id };
}

export type NativeInteraction = (request: Readonly<Record<string, unknown>>) => Promise<Record<string, any>>;

export class InstallerUi {
  private next = 0;
  private observed = new WeakSet<UiNode>();
  private observation = 0;
  private readingPrimary = false;
  private readonly primarySnapshot?: () => Promise<UiNodes>;
  private readonly nativeInteraction?: NativeInteraction;
  private nodes: UiNode[] = [];
  private cli: string;
  private adapter: ProcessAdapter;
  private ownership: () => Promise<void>;
  readonly serial: string;
  readonly output: string;
  readonly records: Array<{ invocation: Invocation; result: unknown }> = [];
  constructor(
    cli: string,
    serial: string,
    output: string,
    adapter: ProcessAdapter,
    ownership: () => Promise<void>,
    primarySnapshot?: () => Promise<UiNodes>,
    nativeInteraction?: NativeInteraction,
  ) {
    this.cli = cli;
    this.serial = serial;
    this.output = output;
    this.adapter = adapter;
    this.ownership = ownership;
    this.primarySnapshot = primarySnapshot;
    this.nativeInteraction = nativeInteraction;
    mkdirSync(output, { mode: 0o700 });
  }
  async call(request: Record<string, unknown>): Promise<Record<string, any>> {
    return this.callObserved(request);
  }
  async callObserved(
    request: Record<string, unknown>,
    onOutput?: Invocation["onOutput"],
  ): Promise<Record<string, any>> {
    if ((request.action === "gesture" || "points" in request) && !this.nativeInteraction) throw new Error("generic gesture requires native fenced interaction");
    if (this.readingPrimary) throw new Error("native primary observation in progress; serialize UI actions");
    if (["tap", "swipe", "drag", "gesture", "tbox-burst"].includes(String(request.action)) && !request.expectedWindow) {
      const first = Array.isArray(request.points) ? request.points[0] : undefined;
      const x = Number(first?.x ?? request.x ?? request.x1), y = Number(first?.y ?? request.y ?? request.y1);
      const targets = this.nodes.filter(node => node.enabled && node.visibleToUser === true && focusedApplication(node) &&
        node.bounds[0] <= x && x < node.bounds[2] && node.bounds[1] <= y && y < node.bounds[3] &&
        (request.action === "gesture" ? true : request.action === "swipe" ? node.scrollable : node.clickable));
      targets.sort((a, b) => (a.bounds[2] - a.bounds[0]) * (a.bounds[3] - a.bounds[1]) - (b.bounds[2] - b.bounds[0]) * (b.bounds[3] - b.bounds[1]));
      if (!targets.length) throw new Error("gesture lacks observed owned target");
      request = { ...request, ...this.gestureAuthority(targets[0], request.action === "gesture") };
    }
    validateRequest(request);
    this.observed = new WeakSet();
    const observation = ++this.observation;
    await this.ownership();
    if (observation !== this.observation) throw new Error("stale UI observation before action");
    if (this.nativeInteraction && (["tap", "swipe", "drag", "gesture", "tbox-burst", "key", "reboot"].includes(String(request.action)) || nativeApp(request))) {
      if (onOutput) throw new Error("native interaction does not stream installer output");
      // Dispatch once. A failed native action never falls back to the installer.
      return this.nativeInteraction(Object.freeze({ ...request }));
    }
    const file = join(
      this.output,
      `${String(++this.next).padStart(5, "0")}-${String(request.action)}.json`,
    );
    writeFileSync(file, JSON.stringify(request) + "\n", { flag: "wx", mode: 0o600 });
    const invocation: Invocation = driverInvocation(this.cli, file, this.serial, {
      timeoutMs: ["reboot", "observe-settings-uninstall"].includes(String(request.action))
        ? 900000
        : request.action === "boot-observation" ? 90000 : 240000,
      onOutput,
    });
    const result = await this.adapter(invocation);
    this.records.push({ invocation, result });
    if (
      result.exitCode !== 0 ||
      result.timedOut ||
      result.signal ||
      result.retained?.overflow ||
      result.overflow
    )
      {
        const diagnostic = installerDiagnostic(result, this.serial);
        if (request.action === "tap" && result.exitCode === 1 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && diagnostic?.connection &&
            /^Emulator acceptance failed: emulator operation failed \(Some\(72\)\): ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0\r?$/m.test(diagnostic.stderr) &&
            safeGestureDiagnostics(diagnostic.stderr) &&
            /^ui-observation stage=gesture reason=gesture-window-or-target-drift kind=java.lang.IllegalStateException syscall=none errno=none /m.test(diagnostic.stderr))
          throw new GesturePreInputDrift("gesture rejected before input: recapture semantic checkbox");
        throw new Error(`installer UI ${String(request.action)} failed: ${result.stderr || result.stdout}`);
      }
    const lines = result.stdout.split(/\r?\n/).filter((l) => l.startsWith("{"));
    if (lines.length !== 1) throw new Error("one CLI JSON receipt required");
    const body = JSON.parse(lines[0]);
    if (body?.schemaVersion !== 1 || body.serial !== this.serial || Array.isArray(body))
      throw new Error("installer UI result identity differs");
    const expected =
      request.action === "reboot"
        ? ["schemaVersion", "serial", "bootIdBefore", "bootIdAfter", "enforcing"]
        : request.action === "screenshot"
          ? ["schemaVersion", "serial", "png"]
          : request.action === "observe-settings-uninstall"
            ? ["schemaVersion", "serial", "bootIdBefore", "absence", "enforcing"]
            : ["schemaVersion", "serial", "body"];
    if (JSON.stringify(Object.keys(body).sort()) !== JSON.stringify(expected.sort()))
      throw new Error("UI receipt has extra or missing fields");
    return body;
  }
  async snapshot(): Promise<UiNode[]> {
    if (this.primarySnapshot) {
      if (this.readingPrimary) throw new Error("native primary observation in progress; serialize UI actions");
      this.readingPrimary = true;
      try {
        this.observed = new WeakSet();
        const observation = ++this.observation;
        await this.ownership();
        if (observation !== this.observation) throw new Error("stale UI snapshot before native read");
        const nodes = await this.primarySnapshot();
        if (observation !== this.observation) throw new Error("stale UI snapshot completion");
        this.observed = new WeakSet(nodes);
        this.nodes = nodes;
        return nodes;
      } finally { this.readingPrimary = false; }
    }
    const observation = this.observation + 1;
    const result = await this.call({ action: "snapshot" });
    if (observation !== this.observation) throw new Error("stale UI snapshot completion");
    if (typeof result.body !== "string") throw new Error("UI snapshot body missing");
    const nodes = parseUi(result.body);
    this.observed = new WeakSet(nodes);
    this.nodes = nodes;
    return nodes;
  }
  async screenshot(name: string, display: "primary" | "secondary" = "primary", voyemuCapture?: VoyemuCapture): Promise<{ file: string; sha256: string }> {
    if (!/^[a-z0-9-]{1,80}$/u.test(name)) throw new Error("unsafe screenshot name");
    const file = join(this.output, `${String(++this.next).padStart(5, "0")}-${name}.png`);
    const result = await this.call({ action: "screenshot", output: file, ...(display === "secondary" ? { display } : {}), ...(voyemuCapture ? { voyemuCapture } : {}) });
    if (result.png !== file) throw new Error("screenshot result path differs");
    const bytes = readFileSync(file);
    validatePng(bytes);
    if (!bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])))
      throw new Error("screenshot is not PNG");
    return { file, sha256: createHash("sha256").update(bytes).digest("hex") };
  }
  gestureAuthority(node: UiNode, ownWindow = false): Record<string, unknown> {
    if (!this.observed.has(node) || !node.enabled || node.visibleToUser !== true) throw new Error("stale or foreign UI node");
    const window = node.windowRef;
    if (!window || node.visibleToUser !== true) throw new Error("gesture requires actual window metadata");
    const vendor = window.type === -1 && node.packageName === "com.qinggan.app.vehiclesetting" && node.resource === "com.qinggan.app.vehiclesetting:id/disclaimer_agree" && node.className === "android.widget.Button" && oemForcedEvAgree(this.nodes) === node;
    const focused = this.nodes.filter(other => other.windowRef && (vendor || [1, 3].includes(other.windowRef.type)) && other.windowRef.active && other.windowRef.inputFocused && other.windowRef.rootStatus === "available");
    const owners = new Set(focused.map(other => other.windowRef!));
    if (owners.size !== 1) throw new Error("gesture requires unique focused owned application window");
    const owner = ownWindow ? window : [...owners][0];
    if (ownWindow && (!this.nativeInteraction || window.type !== 3 || window.scope !== "default-display" || window.rootStatus !== "available" || window.rootPackage !== node.packageName || this.nodes.filter(n => n.windowRef?.id === window.id).some(n => n.windowRef !== window))) throw new Error("generic gesture requires unique current owned overlay window");
    const bounds = node.rawBounds ?? node.bounds;
    return {
      expectedWindow: { id: owner.id, windowType: owner.type, bounds: `[${owner.bounds[0]},${owner.bounds[1]}][${owner.bounds[2]},${owner.bounds[3]}]`, title: owner.title, packageName: owner.rootPackage, captureEndUptimeMs: String(owner.captureEnd), displayId: 0 },
      expectedTarget: { windowBounds: `[${window.bounds[0]},${window.bounds[1]}][${window.bounds[2]},${window.bounds[3]}]`, windowTitle: window.title, rootPackage: window.rootPackage, className: node.className, windowId: window.id, windowType: window.type, packageName: node.packageName, bounds: `[${bounds[0]},${bounds[1]}][${bounds[2]},${bounds[3]}]`, resource: node.resource, text: node.text },
    };
  }
  async tap(node: UiNode): Promise<void> {
    if (!this.observed.has(node) || !node.enabled || node.visibleToUser === false) throw new Error("stale or foreign UI node");
    if (node.windowRef?.type === -1) {
      if (node.packageName !== "com.qinggan.app.vehiclesetting" || node.resource !== "com.qinggan.app.vehiclesetting:id/disclaimer_agree" || node.className !== "android.widget.Button") throw new Error("unclassified UI window cannot be actuated");
      if (oemForcedEvAgree(this.nodes) !== node) throw new Error("unclassified UI window cannot be actuated");
    }
    const [x, y] = center(node);
    await this.call({ action: "tap", x, y, ...this.gestureAuthority(node) });
  }
  launcherPointerAuthority(node:UiNode):Record<string,unknown> {
    if(!this.nativeInteraction) return this.gestureAuthority(node);
    if(!this.observed.has(node) || !this.nodes.includes(node) || !node.enabled || node.visibleToUser!==true) throw new Error("stale launcher pointer target");
    const main=launcherPrimaryPointerWindow(this.nodes),w=node.windowRef;
    if(!w || !(this.nodes as UiNodes).windowRefs?.includes(w) || w.rootPackage!==main.rootPackage || node.packageName!==main.rootPackage ||
       w.capture!==main.capture || w.frameSha256!==main.frameSha256 || !(w===main || launcherDock(this.nodes,true)===node)) throw new Error("launcher pointer target ownership differs");
    const b=node.rawBounds??node.bounds,rect=(r:readonly number[])=>`[${r[0]},${r[1]}][${r[2]},${r[3]}]`;
    center(node);
    if(node.bounds[0]<w.bounds[0] || node.bounds[1]<w.bounds[1] || node.bounds[2]>w.bounds[2] || node.bounds[3]>w.bounds[3]) throw new Error("launcher pointer target containment differs");
    return {
      expectedWindow:{id:main.id,windowType:main.type,bounds:rect(main.bounds),title:main.title,packageName:main.rootPackage,captureEndUptimeMs:String(main.captureEnd),displayId:0},
      expectedTarget:{windowBounds:rect(w.bounds),windowTitle:w.title,rootPackage:w.rootPackage,className:node.className,windowId:w.id,windowType:w.type,packageName:node.packageName,bounds:rect(b),resource:node.resource,text:node.text}
    };
  }
  async openLauncherApps(): Promise<void> {
    const node = await awaitLauncherDock(() => this.snapshot(), performance.now() + 5000,()=>performance.now(),!!this.nativeInteraction);
    const [x, y] = center(node);
    await this.call({ action: "tap", x, y, ...this.launcherPointerAuthority(node) });
  }
  async tapText(text: string): Promise<void> {
    const nodes = await this.snapshot();
    await this.tap(uniqueNode(nodes, (n) => n.text === text || n.description === text));
  }
  async reboot(): Promise<Record<string, unknown>> {
    return this.call({ action: "reboot" });
  }
}

export function validateRequest(request: Record<string, unknown>): void {
  const fields: Record<string, string[]> = {
    "install-acceptance": ["transactionId", "manifestSha256", "attemptToken", "bootId", "rebootCount", "decision"],
    snapshot: [],
    inspect: [],
    "boot-observation": [],
    "resident-status": [],
    "observe-settings-uninstall": [],
    "secondary-capture-proof": [],
    "open-settings": [],
    "open-secondary-launcher": [],
    "open-primary-launcher": [],
    "launcher-page-observe": ["requestId"],
    "open-vehicle-settings": [],
    "open-navigation": [],
    "fixture-state": [],
    screenshot: ["output"],
    tap: ["x", "y", "expectedWindow", "expectedTarget"],
    "tbox-burst": ["x", "y", "expectedWindow", "expectedTarget"],
    swipe: ["x1", "y1", "x2", "y2", "milliseconds", "expectedWindow", "expectedTarget"],
    drag: ["x1", "y1", "x2", "y2", "milliseconds", "expectedWindow", "expectedTarget"],
    gesture: ["x1", "y1", "x2", "y2", "milliseconds", "expectedWindow", "expectedTarget"],
    key: ["key"],
    text: ["value"],
    reboot: [],
    "install-fixture": ["apk", "package"],
    "remove-fixture": ["package"],
  };
  const action = String(request.action),
    pointFields = ["points", "expectedWindow", "expectedTarget"],
    expected = ["gesture", "drag"].includes(action) && "points" in request ? pointFields : action === "screenshot" ? [...fields.screenshot, ...("display" in request ? ["display"] : []), ...("voyemuCapture" in request ? ["voyemuCapture"] : [])] : fields[action];
  if (
    !expected ||
    JSON.stringify(Object.keys(request).sort()) !== JSON.stringify(["action", ...expected].sort())
  )
    throw new Error("unknown or extra closed UI request fields");
  if ("points" in request) boundedGesturePoints(request.points);
  if (action === "launcher-page-observe" && (typeof request.requestId !== "string" || !/^[0-9a-f]{64}$/.test(request.requestId))) throw new Error("invalid launcher page request nonce");
  if (["tap", "swipe", "drag", "gesture", "tbox-burst"].includes(action)) {
    const window = request.expectedWindow as any, target = request.expectedTarget as any;
    if (!window || Array.isArray(window) || Object.keys(window).sort().join() !== "bounds,captureEndUptimeMs,displayId,id,packageName,title,windowType" || !([1, 3].includes(window.windowType) || (action === "tap" && window.windowType === -1 && target?.windowType === -1 && window.id === target?.windowId && window.packageName === "com.qinggan.app.vehiclesetting" && target?.packageName === window.packageName && target?.rootPackage === window.packageName && target?.resource === `${window.packageName}:id/disclaimer_agree` && target?.className === "android.widget.Button" && window.bounds === target?.windowBounds && window.title === target?.windowTitle)) || typeof window.bounds !== "string" || typeof window.title !== "string" || !Number.isSafeInteger(window.id) || window.id < 0 || window.displayId !== 0 || !/^[A-Za-z][A-Za-z0-9_.]+$/.test(window.packageName) || typeof window.captureEndUptimeMs !== "string" || !/^(0|[1-9][0-9]*)$/.test(window.captureEndUptimeMs)) throw new Error("invalid gesture expected window");
    if (!target || Array.isArray(target) || Object.keys(target).sort().join() !== "bounds,className,packageName,resource,rootPackage,text,windowBounds,windowId,windowTitle,windowType" || typeof target.windowBounds !== "string" || typeof target.windowTitle !== "string" || typeof target.rootPackage !== "string" || typeof target.className !== "string" || !target.className || (![-1, 2].includes(target.windowType) && target.windowId !== window.id) || (target.windowType === -1 && target.rootPackage !== window.packageName) || !Number.isSafeInteger(target.windowId) || target.windowId < 0 || ![-1, 1, 2, 3].includes(target.windowType) || typeof target.packageName !== "string" || !/^[A-Za-z][A-Za-z0-9_.]+$/.test(target.packageName) || typeof target.bounds !== "string" || !/^\[-?\d+,-?\d+\]\[-?\d+,-?\d+\]$/.test(target.bounds) || typeof target.resource !== "string" || typeof target.text !== "string" || target.resource.length > 4096 || target.text.length > 4096) throw new Error("invalid gesture expected target");
  }
  if (action === "install-acceptance" && (request.rebootCount !== 1 || !["status", "accept", "reject"].includes(String(request.decision)) || ![request.transactionId, request.manifestSha256, request.attemptToken].every(value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value)) || typeof request.bootId !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(request.bootId))) throw new Error("invalid install acceptance request");
  for (const field of ["x", "y", "x1", "y1", "x2", "y2"])
    if (
      field in request &&
      (!Number.isInteger(request[field]) ||
        Number(request[field]) < 0 ||
        Number(request[field]) >= 4096)
    )
      throw new Error("invalid UI point");
  if (
    "milliseconds" in request &&
    (!Number.isInteger(request.milliseconds) ||
      Number(request.milliseconds) < 100 ||
      Number(request.milliseconds) > 2000)
  )
    throw new Error("invalid gesture duration");
  if (
    action === "key" &&
    !["wake", "sleep", "home", "back", "enter", "delete"].includes(String(request.key))
  )
    throw new Error("unknown key");
  if (
    action === "text" &&
    (typeof request.value !== "string" ||
      !request.value.length ||
      request.value.length > 128 ||
      !/^[A-Za-z0-9 .,:/_-]+$/.test(request.value))
  )
    throw new Error("invalid ASCII text");
  for (const field of ["output", "apk"])
    if (
      field in request &&
      (typeof request[field] !== "string" ||
        !String(request[field]).startsWith("/") ||
        /[\x00-\x1f]/.test(String(request[field])))
    )
      throw new Error("unsafe UI artifact path");
  if (action === "screenshot" && "display" in request && !["primary", "secondary"].includes(String(request.display))) throw new Error("invalid screenshot display");
  if (action === "screenshot" && "voyemuCapture" in request) {
    const capture = request.voyemuCapture as VoyemuCapture;
    if (request.display !== "secondary" || !capture || Array.isArray(capture) ||
        Object.keys(capture).sort().join() !== "cliSha256,node,nodeSha256,profile,root,runtimePid,runtimePort" ||
        ![capture.root, capture.node].every(value => typeof value === "string" && value.startsWith("/") && !/[\x00-\x1f\x7f]/u.test(value)) ||
        !/^[a-z0-9][a-z0-9._-]{0,63}$/u.test(capture.profile) ||
        !Number.isSafeInteger(capture.runtimePid) || capture.runtimePid <= 0 || capture.runtimePid > 0xffffffff ||
        !Number.isSafeInteger(capture.runtimePort) || capture.runtimePort < 1024 || capture.runtimePort > 65534 || capture.runtimePort % 2 !== 0 ||
        ![capture.nodeSha256, capture.cliSha256].every(value => typeof value === "string" && /^[a-f0-9]{64}$/u.test(value)))
      throw new Error("invalid typed Voyemu screenshot context");
  }
  if (action === "screenshot" && !String(request.output).endsWith(".png"))
    throw new Error("PNG path required");
  if (
    action === "install-fixture" &&
    request.package !== "ru.voboost.tests.launcherfixture"
  )
    throw new Error("fixture identity differs");
  if (action === "remove-fixture" && request.package !== "ru.voboost.tests.launcherfixture")
    throw new Error("unknown fixture package");
}

/** Bounded PNG structure/raster and non-black capture check; this is not typography review. */
function pngCrc(bytes: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
export function pngRaster(bytes: Buffer, allowBlack = false): { width: number; height: number; channels: number; pixels: Buffer } {
  if (
    bytes.length > 8 * 1024 * 1024 ||
    !bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
  )
    throw new Error("invalid bounded PNG");
  let offset = 8,
    width = 0,
    height = 0,
    channels = 0,
    ended = false;
  const data: Buffer[] = [];
  while (offset + 12 <= bytes.length) {
    const length = bytes.readUInt32BE(offset);
    if (length > bytes.length - offset - 12) throw new Error("truncated PNG chunk");
    const type = bytes.toString("ascii", offset + 4, offset + 8),
      body = bytes.subarray(offset + 8, offset + 8 + length);
    if (
      pngCrc(bytes.subarray(offset + 4, offset + 8 + length)) !==
      bytes.readUInt32BE(offset + 8 + length)
    )
      throw new Error("PNG chunk CRC differs");
    if (type === "IHDR") {
      if (offset !== 8 || length !== 13) throw new Error("invalid PNG header");
      width = body.readUInt32BE(0);
      height = body.readUInt32BE(4);
      channels = body[9] === 6 ? 4 : body[9] === 2 ? 3 : 0;
      if (
        !width ||
        !height ||
        width > 4096 ||
        height > 4096 ||
        body[8] !== 8 ||
        body[10] !== 0 ||
        body[11] !== 0 ||
        body[12] !== 0 ||
        !channels
      )
        throw new Error("unsupported PNG encoding needs review");
    } else if (type === "IDAT") data.push(body);
    else if (type === "IEND") {
      if (length !== 0) throw new Error("invalid PNG end");
      ended = true;
      offset += 12;
      break;
    }
    offset += length + 12;
  }
  if (!ended || offset !== bytes.length || !width || !data.length)
    throw new Error("incomplete PNG");
  const stride = width * channels,
    raw = inflateSync(Buffer.concat(data), { maxOutputLength: (stride + 1) * height });
  if (raw.length !== (stride + 1) * height) throw new Error("PNG raster length differs");
  const pixels = Buffer.alloc(stride * height);
  let previous = Buffer.alloc(stride),
    nonblack = false;
  const paeth = (a: number, b: number, c: number) => {
    const p = a + b - c,
      pa = Math.abs(p - a),
      pb = Math.abs(p - b),
      pc = Math.abs(p - c);
    return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
  };
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    if (filter > 4) throw new Error("invalid PNG filter");
    const row = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let x = 0; x < stride; x++) {
      const left = x >= channels ? row[x - channels] : 0,
        up = previous[x],
        corner = x >= channels ? previous[x - channels] : 0;
      row[x] =
        (row[x] +
          (filter === 1
            ? left
            : filter === 2
              ? up
              : filter === 3
                ? Math.floor((left + up) / 2)
                : filter === 4
                  ? paeth(left, up, corner)
                  : 0)) &
        255;
    }
    for (let x = 0; x < stride; x += channels)
      if ((channels !== 4 || row[x + 3]) && (row[x] || row[x + 1] || row[x + 2])) nonblack = true;
    row.copy(pixels, y * stride);
    previous = row;
  }
  if (!nonblack && !allowBlack) throw new Error("captured screen is entirely black");
  return { width, height, channels, pixels };
}
export function validatePng(bytes: Buffer, allowBlack = false): { width: number; height: number } {
  const {width,height} = pngRaster(bytes,allowBlack);
  return {width,height};
}

export function secondaryLauncherProof(body: unknown): { boot: string; targetPid: string; targetStarttime: string; windowToken: string; displayId: 1 } {
  if (typeof body !== "string") throw new Error("secondary launcher window proof missing");
  const m = /^bootId=([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})\npid=([1-9][0-9]{0,19})\nstarttime=([1-9][0-9]{0,19})\nwindowToken=([0-9a-f]{1,16})\ndisplayId=1\ncomponent=com\.qinggan\.app\.launcher\/com\.qinggan\.secondlauncher\.activity\.AllAppActivity\nvisible=true\nenforcing=Enforcing\n$/.exec(body);
  if (!m) throw new Error("secondary launcher window identity differs");
  return { boot: m[1], targetPid: m[2], targetStarttime: m[3], windowToken: m[4], displayId: 1 };
}

export function launcherDragDuration(page: number): number {
  if (!Number.isInteger(page) || page < 0 || page > 1) throw new Error("unsupported launcher drag page");
  return page === 1 ? 1500 : 1200;
}

/** Native-only pressed samples; never translated into legacy installer arguments. */
export function boundedGesturePoints(value: unknown): Array<{ x: number; y: number; at_ms: number }> {
  if (!Array.isArray(value) || value.length < 1 || value.length > 64) throw new Error("invalid bounded gesture points");
  let previous = -1;
  return value.map((p, index) => {
    if (!p || Array.isArray(p) || Object.keys(p).sort().join() !== "at_ms,x,y" || ![p.x, p.y, p.at_ms].every(Number.isSafeInteger) || p.x < 0 || p.x >= 1920 || p.y < 0 || p.y >= 720 || p.at_ms < 0 || p.at_ms > 2000 || (index === 0 ? p.at_ms !== 0 : p.at_ms <= previous)) throw new Error("invalid bounded gesture points");
    previous = p.at_ms;
    return { x: p.x, y: p.y, at_ms: p.at_ms };
  });
}

/** Prepared OEM edge-hover route; effect still requires fresh readback. */
export function launcherPageDrag(page: number, item: readonly number[], first: readonly number[], grid: readonly number[]): Array<{ x: number; y: number; at_ms: number }> {
  const valid = (b: readonly number[]) => b.length === 4 && b.every(Number.isSafeInteger) && b[0] >= 0 && b[1] >= 0 && b[2] <= 1920 && b[3] <= 720 && b[2] > b[0] && b[3] > b[1];
  if (!Number.isInteger(page) || page < 0 || page > 2 || ![item, first, grid].every(valid) || [item, first].some(b => b[0] < grid[0] || b[1] < grid[1] || b[2] > grid[2] || b[3] > grid[3])) throw new Error("invalid observed launcher drag bounds");
  const mid = (b: readonly number[]) => ({ x: Math.floor((b[0]+b[2])/2), y: Math.floor((b[1]+b[3])/2) });
  const start = mid(item), finish = mid(first);
  const points = [{ ...start, at_ms: 0 }, { ...start, at_ms: 600 }];
  const edge = { x: grid[0] + Math.max(1, Math.floor((first[0]-grid[0])/2)), y: start.y };
  if (page) {
    // OEM interpolateOutOfBoundsScroll calls smoothPrePage and returns zero,
    // stopping the scroll runnable. Each edge MOVE may request another page;
    // do not interpolate through the edge or send an extra final edge sample.
    // Preserve the 600 ms long press and fit two edge transitions within the
    // API's 2000 ms trajectory limit. 550 ms is a diagnostic settle, not an
    // OEM completion guarantee; the fresh order assertion remains mandatory.
    // The caller must still confirm the resulting order from a fresh tree.
    for (let i = 0; i < page; i++) points.push({ ...edge, at_ms: 650 + i * 550 });
    const drop = 650 + page * 550;
    points.push({ ...finish, at_ms: drop }, { ...finish, at_ms: drop + 200 });
  } else {
    for (let i = 1; i <= 10; i++) points.push({ x: Math.round(start.x+(finish.x-start.x)*i/10), y: Math.round(start.y+(finish.y-start.y)*i/10), at_ms: 600+30*i });
    points.push({ ...finish, at_ms: 1100 }, { ...finish, at_ms: 1300 });
  }
  return boundedGesturePoints(points);
}
