import assert from "node:assert/strict";
import test from "node:test";
import { stockProvenance, type StockAuthority } from "./launcher-stock.ts";
import type { AgentDiagnostic, LogRecord } from "./installer-logs.ts";

function fixture() {
  const records: LogRecord[] = [], rows: AgentDiagnostic[] = [];
  const authority: StockAuthority = { boot: "11111111-1111-1111-1111-111111111111", baselineBoot: "11111111-1111-1111-1111-111111111111", generationId: "a".repeat(64), catalogSha256: "b".repeat(64), targetPid: "41", targetStarttime: "514", process: "com.qinggan.app.launcher", afterSequence: 0, records };
  records.push({ schemaVersion: 1, sequence: 1, timestamp: "2026-10-11T17:00:00Z", source: { kind: "daemon" }, level: "info", component: "runtime", message: "desired-snapshot-queued", fields: { process: authority.process, generationId: authority.generationId, catalogSha256: authority.catalogSha256, documentGeneration: "4", snapshotSha256: "c".repeat(64), frames: "D", sourceLanguage: "en", targetPid: "41", targetStarttime: "514", endpointEpoch: "6" } });
  const emit = (event: string, outcome: string, fields: Record<string, unknown>) => {
    const record: LogRecord = { schemaVersion: 1, sequence: records.length + 1, timestamp: "2026-10-11T17:00:00Z", source: { kind: "agent", id: "launcher-apps" }, level: "info", component: "launcher-apps", message: "host protocol stock fixture", fields: {} };
    records.push(record); const row = { record, agent: "launcher-apps", event, outcome, fields }; rows.push(row); return row;
  };
  for (const screen of [0, 1]) {
    const inventory = `inventory:${screen}`, identity = { code_generation: authority.generationId, target_pid: 41, process: authority.process, launcher_generation: 1, screen_id: screen, contract: "qinggan-base-v1" };
    const items = [0, 1].map(index => ({ bean_id: `bean@${screen}${index}`, token: `stock-${screen}-${index}`, package_name: `oem.stock${index}`, icon_resource: 2131230000 + index, icon_resource_name: `oem:drawable/stock${index}` }));
    for (const [index, item] of items.entries()) emit("stock.bind.inventory.item", "observed", { inventory_id: inventory, index, ...item });
    emit("stock.bind.inventory", "observed", { inventory_id: inventory, ...identity, count: items.length });
    for (const [index, item] of items.entries()) {
      const bind = `bind:${screen}${index}`, bean = { bind_id: bind, ...item, registration: "oem-unregistered" }, target = { bind_id: bind, item_view: `item@${index}`, icon_view: `icon@${index}`, name_view: `name@${index}`, item_tag: "old-bean@11", icon_tag: "null", drawable_id: "old-drawable@11", drawable_class: "android.graphics.drawable.BitmapDrawable", geometry: "0,0,100,100;10,10,90,90", state: "visible;alpha=1", label: `stock ${index}`, drawable_state: "[0,0,80,80]:0:[]" }, native = { bind_id: bind, icon_resource: item.icon_resource, drawable_id: `native@${screen}${index}`, drawable_class: "android.graphics.drawable.BitmapDrawable" };
      emit("stock.bind.identity", "observed", { bind_id: bind, inventory_id: inventory, ...identity });
      emit("stock.bind.bean", "before", { ...bean }); emit("stock.bind.target", "before", { ...target });
      emit("stock.bind.bean", "boundary", { ...bean }); emit("stock.bind.target", "boundary", { ...target });
      emit("stock.bind.theme", "returned", native);
      emit("stock.bind.bean", "after", { ...bean }); emit("stock.bind.target", "after", { ...target, item_tag: item.bean_id, drawable_id: native.drawable_id });
      emit("stock.bind.complete", "completed", { bind_id: bind, boundary_unchanged: true, token_unchanged: true, resource_unchanged: true, original_completed: true, tag_matches_bean: true, theme_matches_background: true, managed_drawable: false });
    }
  }
  return { rows, authority, emit };
}

test("stock bind provenance requires complete actual inventories and native resource bindings", () => {
  const f = fixture();
  assert.deepEqual(stockProvenance(f.rows, f.authority), ["0:stock-0-0", "0:stock-0-1", "1:stock-1-0", "1:stock-1-1"]);
  for (const mutate of [
    (f: ReturnType<typeof fixture>) => { f.rows = f.rows.filter(row => row.fields.screen_id !== 1); },
    (f: ReturnType<typeof fixture>) => { f.rows.splice(f.rows.findIndex(row => row.event === "stock.bind.inventory.item"), 1); },
    (f: ReturnType<typeof fixture>) => { f.rows.splice(f.rows.findIndex(row => row.event === "stock.bind.theme"), 1); },
    (f: ReturnType<typeof fixture>) => { f.rows.splice(f.rows.findIndex(row => row.event === "stock.bind.complete"), 1); },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.inventory")!.fields.count = 1; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.inventory.item")!.fields.token = "foreign"; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.bean" && row.outcome === "boundary")!.fields.token = "foreign"; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.bean" && row.outcome === "after")!.fields.icon_resource++; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.target" && row.outcome === "boundary")!.fields.geometry = "changed"; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.target" && row.outcome === "after")!.fields.item_tag = "owned-wrapper"; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.theme")!.fields.drawable_id = "foreign"; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.complete")!.fields.original_completed = false; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.complete")!.fields.managed_drawable = true; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.identity")!.fields.target_pid = 42; },
    (f: ReturnType<typeof fixture>) => { f.rows.find(row => row.event === "stock.bind.inventory")!.fields.extra = true; },
    (f: ReturnType<typeof fixture>) => { f.authority.targetStarttime = "515"; },
    (f: ReturnType<typeof fixture>) => { f.authority.baselineBoot = "22222222-2222-2222-2222-222222222222"; },
    (f: ReturnType<typeof fixture>) => { f.rows[0].record.source = { kind: "daemon" }; },
    (f: ReturnType<typeof fixture>) => { const inventory = f.rows.find(row => row.event === "stock.bind.inventory")!; f.emit("stock.bind.inventory", "invalidated", { inventory_id: inventory.fields.inventory_id, screen_id: 0, launcher_generation: 1 }); },
  ]) { const changed = fixture(); mutate(changed); assert.throws(() => stockProvenance(changed.rows, changed.authority)); }
});


 test("OEM internal tokens may retain an empty package name without weakening resource ownership", () => {
  const f = fixture();
  for (const row of f.rows) if (row.fields.bean_id === "bean@00" && Object.hasOwn(row.fields, "package_name")) row.fields.package_name = "";
  assert.equal(stockProvenance(f.rows, f.authority).length, 4);
  for (const invalid of [null, 12, {}, "x".repeat(257)]) {
    const changed = fixture();
    changed.rows.find(row => row.event === "stock.bind.inventory.item")!.fields.package_name = invalid;
    assert.throws(() => stockProvenance(changed.rows, changed.authority), /stock inventory item metadata differs/);
  }
  for (const field of ["bean_id", "token", "icon_resource_name"]) {
    const changed = fixture();
    changed.rows.find(row => row.event === "stock.bind.inventory.item")!.fields[field] = "";
    assert.throws(() => stockProvenance(changed.rows, changed.authority));
  }
});
