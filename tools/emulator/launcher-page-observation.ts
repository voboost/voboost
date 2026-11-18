import { inspectionResident } from "./functional-observations.ts";
import { oemTargetPid } from "./process-observations.ts";
import { launcherCaptureContinuity } from "./launcher-capture.ts";
import type { UiNode } from "./installer-ui.ts";

export class LauncherPageUnavailable extends Error {}

const owner = "com.qinggan.app.launcher";
type Page = { grid: UiNode; nodes: UiNode[]; items: { label: string; node: UiNode }[] };
type Boot = { boot: string; body: string; bootCompleted: boolean };
const exact = (value: any, names: string[]) => {
  if (!value || typeof value !== "object" || Array.isArray(value) || Object.keys(value).sort().join() !== names.sort().join()) throw new Error("launcher page closed fields differ");
};
const uint = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) >= 0;
const positive = (value: unknown) => typeof value === "string" && /^[1-9][0-9]*$/.test(value) && BigInt(value) <= 18446744073709551615n;
const hash = (value: unknown) => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const text = (value: unknown, max: number): value is string => typeof value === "string" && value.length > 0 && value.length <= max && !/[\u0000-\u001f\u007f-\u009f]/u.test(value);
const box = (value: any) => Array.isArray(value) && value.length === 4 && value.every(n => Number.isSafeInteger(n) && Math.abs(n) <= 16384) && value[2] > value[0] && value[3] > value[1];
const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

/** Root-attested producer data is a separate authority from Accessibility window IDs. */
const independent = (value: any, boot: Boot) => {
  const resident = inspectionResident(boot.body);
  if (!boot.bootCompleted || boot.boot !== value.bootId || oemTargetPid(boot.body, owner) !== value.targetPid || resident.generationId !== value.generationId || resident.catalogSha256 !== value.catalogSha256) throw new Error("launcher page independent boot/process/catalog differs");
};

export function launcherPageReceipt(body: unknown, requestId: string, boot?: Boot) {
  if (typeof body !== "string" || Buffer.byteLength(body) > 16384) throw new Error("launcher page bounded receipt missing");
  // Validate original JSON grammar before token concatenation can erase whitespace.
  JSON.parse(body);
  const tokens = body.match(/"(?:\\.|[^"\\])*"|[{}\[\]:,]|[^\s{}\[\]:,]+/gu) ?? [];
  const stack: (Set<string> | null)[] = [];
  let documentGeneration: string | undefined;
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token === "{") stack.push(new Set());
    else if (token === "[") stack.push(null);
    else if (token === "}" || token === "]") stack.pop();
    else if (token.startsWith('"') && tokens[i + 1] === ":") {
      const key = JSON.parse(token), keys = stack.at(-1);
      if (!keys || keys.has(key)) throw new Error("duplicate launcher page receipt field");
      keys.add(key);
      if (stack.length === 1 && key === "documentGeneration") {
        const raw = tokens[i + 2];
        if (!/^[1-9][0-9]*$/.test(raw ?? "") || BigInt(raw) > 18446744073709551615n) throw new Error("launcher page document generation differs");
        documentGeneration = raw;
        tokens[i + 2] = JSON.stringify(raw);
      }
    }
  }
  // Only the structurally identified root u64 is normalized before JSON numeric decoding.
  // Joining lexer tokens retains every other JSON value and validates the full grammar.
  const value = JSON.parse(tokens.join(""));
  exact(value, ["schemaVersion", "requestId", "bootId", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "documentGeneration", "captureStartedBootMs", "captureFinishedBootMs", "outcome", "reason", ...(value.outcome === "observed" ? ["observation"] : [])]);
  if (value.schemaVersion !== 1 || BigInt(value.targetPid ?? 0) < 2n || BigInt(value.targetPid ?? 0) > 2147483647n || BigInt(value.daemonPid ?? 0) < 2n || BigInt(value.daemonPid ?? 0) > 2147483647n || !hash(requestId) || value.requestId !== requestId || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.bootId) || !["daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "endpointEpoch"].every(k => positive(value[k])) || (!text(value.generationId, 128) || !hash(value.catalogSha256)) || (!positive(value.documentGeneration) || value.documentGeneration !== documentGeneration) || !uint(value.captureStartedBootMs) || !uint(value.captureFinishedBootMs) || value.captureFinishedBootMs < value.captureStartedBootMs || value.captureFinishedBootMs - value.captureStartedBootMs > 10000) throw new Error("launcher page receipt identity or outcome differs");
  if (boot) independent(value, boot);
  if (value.outcome === "unavailable" && ["no-activity", "wrong-process", "no-grid", "capture-failed", "timeout", "unstable", "binding-mismatch"].includes(value.reason)) throw new LauncherPageUnavailable(`launcher page observation unavailable: ${value.reason}`);
  if (value.outcome !== "observed" || value.reason !== "observed") throw new Error("launcher page outcome differs");
  const o = value.observation;
  exact(o, ["outerCurrentItem", "firstPosition", "pageCount", "offsetX", "itemCount", "scrollState", "computingLayout", "window", "grid", "items"]);
  if (!["outerCurrentItem", "firstPosition", "pageCount", "offsetX", "itemCount", "scrollState"].every(k => uint(o[k])) || o.outerCurrentItem !== 1 || o.scrollState !== 0 || o.computingLayout !== false || o.itemCount === 0 || o.pageCount !== Math.ceil(o.itemCount / 12) || o.firstPosition >= o.itemCount || o.firstPosition % 12 !== 0) throw new Error("launcher page direct metrics differ or layout is busy");
  exact(o.window, ["displayId", "activityClass", "windowType", "focused", "attached", "shown", "token", "bounds"]);
  if (o.window.activityClass !== "com.qinggan.app.launcher.activity.MainActivity" || o.window.windowType !== 1 || o.window.displayId !== 0 || !["focused", "attached", "shown"].every(k => o.window[k] === true) || !text(o.window.token, 256) || !box(o.window.bounds)) throw new Error("launcher page primary window differs");
  exact(o.grid, ["resource", "className", "layoutManagerClass", "bounds"]);
  if (o.grid.resource !== `${owner}:id/all_app_view` || o.grid.className !== "com.qinggan.launcher.base.allapp.AllAppBarView" || o.grid.layoutManagerClass !== "com.qinggan.launcher.base.custom.PagerGridLayoutManager" || !box(o.grid.bounds) || !Array.isArray(o.items) || !o.items.length || o.items.length > 48) throw new Error("launcher page grid differs");
  const positions = new Set<number>();
  let previousPosition = -1;
  for (const item of o.items) {
    exact(item, ["position", "label", "bounds"]);
    if (!uint(item.position) || item.position >= o.itemCount || item.position < o.firstPosition || item.position >= o.firstPosition + 12 || positions.has(item.position) || item.position <= previousPosition || !text(item.label, 256) || !box(item.bounds)) throw new Error("launcher page item differs");
    positions.add(item.position);
    previousPosition = item.position;
  }
  return value;
}

export function launcherPageLifetime(first: ReturnType<typeof launcherPageReceipt>, last: ReturnType<typeof launcherPageReceipt>, boots: Boot[]) {
  for (const key of ["bootId", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "documentGeneration"]) if (first[key] !== last[key]) throw new Error("launcher page observation crossed process or generation lifetime");
  if (first.requestId === last.requestId || last.captureStartedBootMs < first.captureFinishedBootMs || last.captureFinishedBootMs - first.captureStartedBootMs > 180000 || !same(first.observation, last.observation)) throw new Error("launcher page observation freshness or native window differs");
  for (const boot of boots) independent(first, boot);
}

export function launcherPageHierarchy(receipt: ReturnType<typeof launcherPageReceipt>, page: Page) {
  const o = receipt.observation, grid = page.grid, w = grid.windowRef;
  if (!w || w.type !== 1 || !w.active || !w.inputFocused || w.rootPackage !== owner || w.rootStatus !== "available" || !grid.visibleToUser || !page.nodes.includes(grid) || grid.packageName !== owner || grid.resource !== o.grid.resource || grid.className !== "androidx.recyclerview.widget.RecyclerView" || !same(grid.bounds, o.grid.bounds) || !same(w.bounds, o.window.bounds)) throw new Error("launcher page hierarchy window/grid differs");
  const observed = o.items.toSorted((a: any, b: any) => a.position - b.position).map((item: any) => [item.label, item.bounds]);
  if (page.items.some(item => item.node.windowRef !== w || !item.node.visibleToUser) || !same(observed, page.items.map(item => [item.label, item.node.bounds]))) throw new Error("launcher page hierarchy labels/bounds differ");
}

/** Two separately nonced root receipts fence the original hierarchy and fresh gesture authority. */
export async function launcherSemanticBoundary(input: {
  before: Page; beforeBoot: Boot; indicator: UiNode;
  query: () => Promise<{ body: unknown; requestId: string }>;
  page: () => Promise<Page>; boot: () => Promise<Boot>;
  indicatorFor: (page: Page) => UiNode;
}) {
  const one = await input.query(), first = launcherPageReceipt(one.body, one.requestId, input.beforeBoot);
  const after = await input.page(), afterIndicator = input.indicatorFor(after), afterBoot = await input.boot();
  launcherCaptureContinuity(input.before, after, input.indicator, afterIndicator, input.beforeBoot, afterBoot, 180000);
  const two = await input.query(), last = launcherPageReceipt(two.body, two.requestId, afterBoot), finalBoot = await input.boot();
  const fresh = await input.page(), freshIndicator = input.indicatorFor(fresh);
  launcherCaptureContinuity(after, fresh, afterIndicator, freshIndicator, afterBoot, finalBoot, 180000);
  launcherPageLifetime(first, last, [input.beforeBoot, afterBoot, finalBoot]);
  for (const page of [input.before, after, fresh]) launcherPageHierarchy(last, page);
  return { first: last.observation.firstPosition === 0 && last.observation.offsetX === 0 && last.observation.pageCount > 0, page: fresh };
}
