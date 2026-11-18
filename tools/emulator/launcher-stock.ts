import { queuedSnapshot, sequence } from "./functional-observations.ts";
import type { AgentDiagnostic, LogRecord } from "./installer-logs.ts";

export type StockAuthority = { boot: string; baselineBoot: string; generationId: string; catalogSha256: string; targetPid: string; targetStarttime: string; process: string; afterSequence: number; records: LogRecord[] };
const exact = (value: Record<string, unknown>, fields: string[], label: string) => {
  if (!value || Array.isArray(value) || Object.keys(value).sort().join() !== [...fields].sort().join()) throw new Error(`${label}: unknown or missing fields`);
};
const text = (value: unknown) => typeof value === "string" && value.length > 0 && value.length <= 256;
const integer = (value: unknown) => Number.isSafeInteger(value) && Number(value) >= 0 && Number(value) <= 2147483647;
const same = (a: Record<string, unknown>, b: Record<string, unknown>) => Object.keys(a).every(key => a[key] === b[key]);
const identity = ["code_generation", "target_pid", "process", "launcher_generation", "screen_id", "contract"];
const bean = ["bean_id", "token", "package_name", "icon_resource", "icon_resource_name", "registration"];
const target = ["item_view", "icon_view", "name_view", "item_tag", "icon_tag", "drawable_id", "drawable_class", "geometry", "state", "label", "drawable_state"];
const complete = ["boundary_unchanged", "token_unchanged", "resource_unchanged", "original_completed", "tag_matches_bean", "theme_matches_background"];

/** Runtime admission needs every actual stock token; a sampled successful bind is insufficient. */
export function stockProvenance(rows: AgentDiagnostic[], authority: StockAuthority): string[] {
  sequence(authority.records);
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(authority.boot) || authority.boot !== authority.baselineBoot || !Number.isSafeInteger(authority.afterSequence) || authority.afterSequence < -1 || !/^[0-9a-f]{64}$/.test(authority.generationId) || !/^[0-9a-f]{64}$/.test(authority.catalogSha256) || !/^[1-9][0-9]*$/.test(authority.targetPid) || !/^[1-9][0-9]*$/.test(authority.targetStarttime) || authority.process !== "com.qinggan.app.launcher") throw new Error("stock registered authority missing or differs");
  const stock = rows.filter(row => row.agent === "launcher-apps" && row.event.startsWith("stock.bind.") && row.record.sequence > authority.afterSequence);
  for (const row of stock) if (row.record.source.kind !== "agent" || row.record.source.id !== "launcher-apps" || !authority.records.some(record => record.sequence === row.record.sequence && JSON.stringify(record) === JSON.stringify(row.record))) throw new Error("stock record canonical attribution differs");
  const inventories = stock.filter(row => row.event === "stock.bind.inventory" && row.outcome === "observed");
  const tokens: string[] = [];
  for (const screen of [0, 1]) {
    const inventory = inventories.findLast(row => row.fields.screen_id === screen);
    if (!inventory) throw new Error("complete actual stock screen inventory missing");
    const f = inventory.fields;
    exact(f, ["inventory_id", ...identity, "count"], "stock inventory");
    if (inventory.outcome !== "observed" || !text(f.inventory_id) || f.code_generation !== authority.generationId || f.target_pid !== Number(authority.targetPid) || f.process !== authority.process || !integer(f.launcher_generation) || Number(f.launcher_generation) < 1 || !["qinggan-base-v1", "qinggan-nxp-v1"].includes(String(f.contract)) || !integer(f.count) || Number(f.count) < 1 || Number(f.count) > 64) throw new Error("stock inventory identity or count differs");
    if (stock.some(row => row.event === "stock.bind.inventory" && row.outcome === "invalidated" && row.fields.inventory_id === f.inventory_id && row.record.sequence > inventory.record.sequence)) throw new Error("current stock inventory was invalidated");
    const registration = authority.records.filter(record => record.message === "desired-snapshot-queued" && record.sequence < inventory.record.sequence).map(record => ({ record, fields: queuedSnapshot(record) })).findLast(item => item.fields.process === authority.process && item.fields.targetPid === authority.targetPid && item.fields.targetStarttime === authority.targetStarttime && item.fields.generationId === authority.generationId && item.fields.catalogSha256 === authority.catalogSha256);
    if (!registration) throw new Error("independent current stock target registration missing");
    const items = stock.filter(row => row.event === "stock.bind.inventory.item" && row.fields.inventory_id === f.inventory_id);
    if (items.length !== f.count || new Set(items.map(row => row.fields.index)).size !== f.count || new Set(items.map(row => row.fields.token)).size !== f.count || new Set(items.map(row => row.fields.bean_id)).size !== f.count) throw new Error("stock inventory is partial or duplicated");
    for (const [index, item] of items.toSorted((a, b) => Number(a.fields.index) - Number(b.fields.index)).entries()) {
      exact(item.fields, ["inventory_id", "index", ...bean.filter(key => key !== "registration")], "stock inventory item");
      if (item.outcome !== "observed" || item.fields.index !== index || item.record.sequence >= inventory.record.sequence || item.record.sequence <= registration.record.sequence || ![item.fields.bean_id, item.fields.token, item.fields.icon_resource_name].every(text) || typeof item.fields.package_name !== "string" || item.fields.package_name.length > 256 || !integer(item.fields.icon_resource) || Number(item.fields.icon_resource) < 1) throw new Error("stock inventory item metadata differs");
      const binds = stock.filter(row => row.event === "stock.bind.identity" && row.fields.inventory_id === f.inventory_id && row.record.sequence > inventory.record.sequence);
      let admitted = false;
      for (const bind of binds) {
        exact(bind.fields, ["bind_id", "inventory_id", ...identity], "stock bind identity");
        if (bind.outcome !== "observed" || !text(bind.fields.bind_id) || identity.some(key => bind.fields[key] !== f[key])) throw new Error("stock bind target identity differs");
        const parts = stock.filter(row => row.fields.bind_id === bind.fields.bind_id), get = (event: string, outcome: string) => {
          const matches = parts.filter(row => row.event === event && row.outcome === outcome);
          if (matches.length !== 1) throw new Error("stock original boundary or theme observation missing or duplicated");
          return matches[0];
        };
        const before = parts.find(row => row.event === "stock.bind.bean" && row.outcome === "before");
        if (!before || before.fields.bean_id !== item.fields.bean_id) continue;
        const beans = [get("stock.bind.bean", "before"), get("stock.bind.bean", "boundary"), get("stock.bind.bean", "after")];
        const views = [get("stock.bind.target", "before"), get("stock.bind.target", "boundary"), get("stock.bind.target", "after")];
        const themes = parts.filter(row => row.event === "stock.bind.theme" && row.outcome === "returned"), done = get("stock.bind.complete", "completed");
        for (const value of beans) {
          exact(value.fields, ["bind_id", ...bean], "stock bean");
          if (value.fields.registration !== "oem-unregistered" || bean.filter(key => key !== "registration").some(key => value.fields[key] !== item.fields[key])) throw new Error("stock original bean token or resource differs");
        }
        for (const value of views) {
          exact(value.fields, ["bind_id", ...target], "stock target");
          if (target.some(key => key === "label" ? typeof value.fields[key] !== "string" || String(value.fields[key]).length > 256 : !text(value.fields[key]))) throw new Error("stock target observation missing or unbounded");
        }
        for (const theme of themes) exact(theme.fields, ["bind_id", "icon_resource", "drawable_id", "drawable_class"], "stock native theme");
        exact(done.fields, ["bind_id", ...complete, "managed_drawable"], "stock completion");
        if (!same(beans[0].fields, beans[1].fields) || !same(views[0].fields, views[1].fields) || complete.some(key => done.fields[key] !== true) || done.fields.managed_drawable !== false || !themes.some(theme => theme.fields.icon_resource === item.fields.icon_resource && text(theme.fields.drawable_id) && theme.fields.drawable_id !== "null" && theme.fields.drawable_id === views[2].fields.drawable_id && theme.fields.drawable_class === views[2].fields.drawable_class) || views[2].fields.item_tag !== item.fields.bean_id) throw new Error("stock original resource Drawable or View ownership differs");
        const order = [bind, beans[0], views[0], beans[1], views[1], ...themes, beans[2], views[2], done].map(row => row.record.sequence);
        if (order.some((value, index) => index > 0 && value <= order[index - 1])) throw new Error("stock original boundary order differs");
        admitted = true; break;
      }
      if (!admitted) throw new Error("complete actual stock token bind missing");
      tokens.push(`${screen}:${String(item.fields.token)}`);
    }
  }
  return tokens;
}

