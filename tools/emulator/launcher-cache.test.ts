import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
const nativeFixture = JSON.parse(readFileSync(new URL("../../testdata/emulator/cycle125-launcher-cache-envelope.json", import.meta.url), "utf8"));
import { launcherCache, joinCacheWitness, CacheEvidenceUnavailable, type CacheAuthority } from "./launcher-cache.ts";
import type { LogRecord, AgentDiagnostic } from "./installer-logs.ts";
function fixture(boot = "11111111-1111-1111-1111-111111111111", source = "render") {
 const records: LogRecord[] = [], rows: AgentDiagnostic[] = [];
 const authority: CacheAuthority = {boot, generationId:"a".repeat(64), catalogSha256:"b".repeat(64), targetPid:"41", targetStarttime:"514", records, afterSequence:0};
 const replacements: [string,string][] = [["d562bd5c-e7c7-409c-bfe6-7c4136bb8fed",boot],["2145","41"],["721","514"],["1f117a4cbdb7a031d9ffdffdd2a2ed6bc9a33e27bcd1809e47c1aeacb067fb9b",authority.generationId],["53ce8b97f35dfc6c9d930a979a6ec83c3efd27a7e6714b98ee923e4d50c7da0a",authority.catalogSha256]];
 authority.nativeLifetime = JSON.parse(replacements.reduce((text,[from,to])=>text.replaceAll(from,to),JSON.stringify(nativeFixture.nativeLifetime)));
 records.push({schemaVersion:1, sequence:1, timestamp:"2026-10-12T17:00:00Z", source:{kind:"daemon"}, level:"info", component:"runtime", message:"desired-snapshot-queued", fields:{process:"com.qinggan.app.launcher", generationId:authority.generationId, catalogSha256:authority.catalogSha256, documentGeneration:"4", snapshotSha256:"c".repeat(64), frames:"D", sourceLanguage:"en", targetPid:"41", targetStarttime:"514", endpointEpoch:"6"}});
 for (const screen of [0,1]) for (const event of ["icon.ready","icon.draw"]) {
  const fields = {boot_id:boot, target_pid:41, process_start_ticks:"514", code_generation:authority.generationId, package_name:"ru.voboost.tests.launcherfixture", generation:1, screen, cache_key:String(screen+1).repeat(64), source_identity:"d".repeat(64), style_identity:"e".repeat(64), elapsed_us:40, ...(event === "icon.draw" ? {cache_source:source}: {})};
  const record: LogRecord = {schemaVersion:1, sequence:records.length+1, timestamp:"2026-10-12T17:00:01Z", source:{kind:"agent",id:"launcher-apps"}, level:"info", component:"launcher-apps", message:"VOBDBG1 "+JSON.stringify({agent:"launcher-apps", event, outcome:event === "icon.ready" ? source:"first-draw", fields}), fields:{process:"com.qinggan.app.launcher",targetPid:"41",targetStarttime:"514",endpointEpoch:"6",generationId:authority.generationId,catalogSha256:authority.catalogSha256}};
  records.push(record); rows.push({record, agent:"launcher-apps", event, outcome:event === "icon.ready" ? source:"first-draw", fields});
 }
 if (boot !== "11111111-1111-1111-1111-111111111111") {
  const before = fixture();
  // Preserve independent pre-boot witness and cursor; actual sequence stays
  // globally increasing while the old final segment may leave retention.
  authority.beforeWitness = before;
  authority.afterSequence = before.authority.records.at(-1)!.sequence;
  authority.baselineRecord = before.authority.records.at(-1);
  for (const record of records) record.sequence += authority.afterSequence;
 }
 return {rows,authority};
}
const old = () => {const f=fixture(); return launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture");};
const fresh = () => fixture("22222222-2222-2222-2222-222222222222","disk");
test("current lifetime ready and first draw prove both retained disk keys across boot",()=>{const f=fresh(); assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()).length,2);});
test("actual memory or render publication cannot masquerade as retained disk reuse",()=>{for(const source of ["memory","render"]){const f=fixture("22222222-2222-2222-2222-222222222222",source); assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/instead of retained disk/);}});
test("stale pid starttime generation and missing screen remain unavailable",()=>{for(const mutate of [(f:any)=>{f.rows[0].fields.boot_id="33333333-3333-3333-3333-333333333333";},(f:any)=>{f.rows[0].fields.target_pid=42;},(f:any)=>{f.rows[0].fields.process_start_ticks="515";},(f:any)=>{f.rows[0].fields.code_generation="f".repeat(64);},(f:any)=>{f.rows=f.rows.filter((r:any)=>r.fields.screen===0);}]){const f=fresh();mutate(f);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),CacheEvidenceUnavailable);}});
test("wrong retained key source or style fails instead of hiding behind incomplete evidence",()=>{for(const key of ["cache_key","source_identity","style_identity"]){const f=fresh();f.rows.filter(r=>r.fields.screen===0).forEach(r=>r.fields[key]="f".repeat(64));assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/changed across reboot/);}});
test("draw source disagreement unknown fields and wrong canonical envelope fail",()=>{for(const mutate of [(f:any)=>{f.rows[1].fields.cache_source="memory";},(f:any)=>{f.rows[0].fields.extra=true;},(f:any)=>{f.rows[0].record.source={kind:"daemon"};}]){const f=fresh();mutate(f);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()));}});
test("same boot and signed catalog changes cannot prove reboot reuse",()=>{for(const mutate of [(f:any)=>{f.authority.boot="11111111-1111-1111-1111-111111111111";},(f:any)=>{f.authority.catalogSha256="f".repeat(64);}]){const f=fresh();mutate(f);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()));}});

test("receipt may precede daemon D log but must follow retained canonical baseline",()=>{const f=fresh(); const registration=f.authority.records.shift()!; registration.sequence=f.authority.records.at(-1)!.sequence+1;f.authority.records.push(registration); assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()).length,2); f.authority.afterSequence=f.rows[0].record.sequence; f.authority.baselineRecord=f.rows[0].record; assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()));});
test("missing or modified retained baseline prevents historical rows passing",()=>{const f=fixture();f.authority.afterSequence=1;f.authority.baselineRecord={...f.authority.records[0],timestamp:"2025-01-01T00:00:00Z"};assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture"),CacheEvidenceUnavailable);});

test("an initial rendered first draw cannot be hidden by a later successful disk bind",()=>{const f=fixture("22222222-2222-2222-2222-222222222222","render"); for(const original of [...f.rows]){const record={...original.record,sequence:f.authority.records.at(-1)!.sequence+1};f.authority.records.push(record);f.rows.push({...original,record,outcome:original.event==="icon.ready"?"disk":"first-draw",fields:{...original.fields,...(original.event==="icon.draw"?{cache_source:"disk"}:{})}});}assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/instead of retained disk/);});

test("retained queued registration cannot bypass missing authenticated endpoint envelope",()=>{const f=fresh();delete f.rows[0].record.fields.endpointEpoch;assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/authenticated endpoint envelope/);});

test("cross boot retained authenticated witness admits rotation without copying old cursor into current logs", () => {
 const f=fresh();
 assert.ok(!f.authority.records.some(r=>r.sequence===f.authority.afterSequence));
 assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()).length,2);
});
test("cross boot witness rejects replacement, tampering, missing native bracket and wrong cursor", () => {
 for (const mutate of [
  (f:any)=>{delete f.authority.beforeWitness;},
  (f:any)=>{delete f.authority.beforeWitness.authority.nativeLifetime;},
  (f:any)=>{f.authority.beforeWitness.rows[0].record.fields.targetPid="42";},
  (f:any)=>{f.authority.baselineRecord={...f.authority.baselineRecord,timestamp:"2025-01-01T00:00:00Z"};},
  (f:any)=>{f.authority.afterSequence++;},
  (f:any)=>{f.authority.beforeWitness.authority.beforeWitness=f.authority.beforeWitness;},
 ]) { const f=fresh();mutate(f);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old())); }
});

function retained() {
 const f=fresh(); f.rows=f.rows.filter(row=>row.event==="icon.draw");
 for(const row of f.rows) {
  row.event="icon.prepared-draw"; delete row.fields.elapsed_us;
  row.fields.prepared_at_boot_ms=100;row.fields.draw_started_at_boot_ms=101;
  row.record.message="VOBDBG1 "+JSON.stringify({agent:row.agent,event:row.event,outcome:row.outcome,fields:row.fields});
 }
 f.authority.records=f.authority.records.filter(record=>!record.message.includes("icon.ready"));
 return f;
}
test("embedded complete preparation admits lost early ready on both screens",()=>{const f=retained();assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()).length,2);});
test("historical missing ready remains unavailable without embedded preparation",()=>{const f=fresh();f.rows=f.rows.filter(row=>row.event==="icon.draw");assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),CacheEvidenceUnavailable);});
test("embedded preparation rejects absent reversed noncanonical time and extra fields",()=>{for(const mutate of [(r:any)=>delete r.fields.prepared_at_boot_ms,(r:any)=>r.fields.prepared_at_boot_ms=102,(r:any)=>r.fields.draw_started_at_boot_ms="101",(r:any)=>r.fields.extra=1]){const f=retained();mutate(f.rows[0]);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()));}});
test("embedded preparation cannot upgrade an earlier rendered draw",()=>{const f=retained();const row=f.rows[0];const earlier={...row,fields:{...row.fields,cache_source:"render"},record:{...row.record,sequence:row.record.sequence-1}};f.rows.unshift(earlier);f.authority.records.push(earlier.record);f.authority.records.sort((a,b)=>a.sequence-b.sequence);assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/instead of retained disk/);});

import { launcherSharedColorCache } from "./launcher-cache.ts";
function sharedFixture(boot?: string, source = "render") {
 const f=fixture(boot,source);
 for(const row of f.rows) row.fields.cache_key="1".repeat(64);
 if(f.authority.beforeWitness) f.authority.beforeWitness=sharedFixture();
 return f;
}
test("shared color namespace is authenticated but does not claim disk reuse",()=>{
 const f=sharedFixture(); const result=launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture");
 assert.equal(result.claim,"shared-color-namespace");assert.deepEqual(result.sources,["render","render"]);
});
test("shared color disk reuse requires two native-bracketed boots and both first disk draws",()=>{
 const before=sharedFixture(), after=sharedFixture("22222222-2222-2222-2222-222222222222","disk");
 assert.equal(launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before).claim,"shared-color-first-draw-disk-reuse");
});
test("shared color rejects mismatched key source style and publication",()=>{
 for(const field of ["cache_key","source_identity","style_identity","generation"]){const f=sharedFixture();for(const row of f.rows.filter(r=>r.fields.screen===1))row.fields[field]=field==="generation"?2:"f".repeat(64);assert.throws(()=>launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture"),/shared color/);}
});
test("shared color rejects rendering despite a peer disk load",()=>{
 const before=sharedFixture();for(const source of ["render"]){const f=sharedFixture("22222222-2222-2222-2222-222222222222","disk");for(const row of f.rows.filter(r=>r.fields.screen===1)){if(row.event==="icon.ready")row.outcome=source;else row.fields.cache_source=source;}assert.throws(()=>launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture",before),/instead of retained disk/);}
});
test("shared color rejects missing or forged native lifetime and same boot",()=>{
 for(const mutate of [(f:any)=>delete f.authority.nativeLifetime,(f:any)=>f.authority.targetStarttime="999",(f:any)=>f.authority.boot="11111111-1111-1111-1111-111111111111",(f:any)=>delete f.authority.beforeWitness]){const before=sharedFixture(),f=sharedFixture("22222222-2222-2222-2222-222222222222","disk");mutate(f);assert.throws(()=>launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture",before));}
});

test("cross boot reset cursor selects current lifetime first draw without an old numeric floor",()=>{
 const f=fresh();
 for(const record of f.authority.records) record.sequence-=f.authority.afterSequence;
 assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()).length,2);
 f.rows[1].fields.cache_source="render";f.rows[0].outcome="render";
 assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture",old()),/instead of retained disk/);
});

test("same boot overlapping canonical exports retain publication without inventing continuity",()=>{
 const before=fixture(),current=fixture();
 current.authority.records=current.authority.records.slice(3);current.rows=current.rows.slice(2);
 const joined=joinCacheWitness(before,current);
 assert.deepEqual(joined.rows,before.rows);assert.deepEqual(joined.authority.records,before.authority.records);
 assert.equal(launcherCache(joined.rows,joined.authority,"ru.voboost.tests.launcherfixture").length,2);
 for(const mutate of [(f:any)=>{f.authority.records[0]={...f.authority.records[0],message:"changed"};},(f:any)=>{f.authority.records=f.authority.records.map((r:any)=>({...r,sequence:r.sequence+10}));},(f:any)=>{f.authority.nativeLifetime=undefined;}]) {
  const f=fixture();mutate(f);assert.throws(()=>joinCacheWitness(before,f));
 }
 assert.throws(()=>joinCacheWitness(before,fresh()),/crossed boot/);
 const rotated=fixture();rotated.authority.afterSequence=1;rotated.authority.baselineRecord=rotated.authority.records[0];rotated.authority.records=rotated.authority.records.slice(1);
 assert.throws(()=>launcherCache(rotated.rows,rotated.authority,"ru.voboost.tests.launcherfixture"),/baseline continuity/);
});

test("canonical record equality ignores object key order but preserves every value",()=>{
 const f=fixture();f.authority.afterSequence=1;
 f.authority.baselineRecord=Object.fromEntries(Object.entries(f.authority.records[0]).reverse()) as any;
 assert.equal(launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture").length,2);
 f.authority.baselineRecord!.fields={...f.authority.baselineRecord!.fields,extra:"changed"};
 assert.throws(()=>launcherCache(f.rows,f.authority,"ru.voboost.tests.launcherfixture"),/baseline continuity/);
});

import { launcherThemePublication } from "./launcher-cache.ts";
function transitionFixture() {
 const f=fixture();
 for(const row of f.rows) row.fields.generation=5;
 for(const old of [...f.rows]) {
  const fields={...old.fields,generation:7,cache_key:"9".repeat(64),style_identity:"8".repeat(64)};
  const record={...old.record,sequence:f.authority.records.at(-1)!.sequence+1};
  record.message="VOBDBG1 "+JSON.stringify({agent:old.agent,event:old.event,outcome:old.outcome,fields});
  f.authority.records.push(record);f.rows.push({...old,record,fields});
 }
 return f;
}
test("theme phase excludes stale publication 5 draws and admits first publication 7",()=>{
 const f=transitionFixture();
 f.authority.publicationGeneration=launcherThemePublication(f.rows,f.authority,"ru.voboost.tests.launcherfixture",5);
 assert.equal(f.authority.publicationGeneration,7);
 assert.deepEqual(launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture").pairs.map(p=>p.cache_key),["9".repeat(64),"9".repeat(64)]);
});
test("selected new publication unequal keys cannot be hidden by later equal publication",()=>{
 const f=transitionFixture();
 for(const row of f.rows.filter(r=>r.fields.generation===7 && r.fields.screen===1))row.fields.cache_key="f".repeat(64);
 for(const old of [...f.rows.filter(r=>r.fields.generation===7)]) {const fields={...old.fields,generation:9,cache_key:"9".repeat(64)};const record={...old.record,sequence:f.authority.records.at(-1)!.sequence+1};record.message="VOBDBG1 "+JSON.stringify({agent:old.agent,event:old.event,outcome:old.outcome,fields});f.authority.records.push(record);f.rows.push({...old,record,fields});}
 f.authority.publicationGeneration=launcherThemePublication(f.rows,f.authority,"ru.voboost.tests.launcherfixture",5);
 assert.equal(f.authority.publicationGeneration,7);
 assert.throws(()=>launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture"),/cache_key differs/);
});
test("theme phase missing new ready or first draw stays unavailable",()=>{
 const f=transitionFixture();
 assert.throws(()=>launcherThemePublication(f.rows,f.authority,"ru.voboost.tests.launcherfixture",7),CacheEvidenceUnavailable);
 f.authority.publicationGeneration=7;
 f.rows=f.rows.filter(r=>!(r.fields.generation===7 && r.fields.screen===1 && r.event==="icon.draw"));
 assert.throws(()=>launcherSharedColorCache(f,"ru.voboost.tests.launcherfixture"),CacheEvidenceUnavailable);
});

function sharedColdMemory() {
 const before=sharedFixture(), after=sharedFixture("22222222-2222-2222-2222-222222222222","disk");
 for(const row of after.rows.filter(r=>r.fields.screen===1)) {if(row.event==="icon.ready")row.outcome="memory";else row.fields.cache_source="memory";}
 return {before,after};
}
test("shared cold color disk load authenticates subsequent peer memory reuse",()=>{
 const {before,after}=sharedColdMemory();
 const result=launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before);
 assert.deepEqual(result.sources,["disk","memory"]);
 assert.equal(result.claim,"shared-color-first-draw-disk-reuse");
});
test("peer memory reuse requires earlier same publication source style key and lifetime disk ready",()=>{
 for(const mutate of [
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.generation=2;},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.cache_key="f".repeat(64);},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.source_identity="f".repeat(64);},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.style_identity="f".repeat(64);},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.target_pid=42;},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.boot_id="33333333-3333-3333-3333-333333333333";},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.process_start_ticks="999";},
  (f:any)=>{for(const row of f.rows.filter((r:any)=>r.fields.screen===0))row.fields.code_generation="f".repeat(64);},
  (f:any)=>{const r=f.rows.find((r:any)=>r.event==="icon.ready" && r.fields.screen===0);r.record.sequence=f.authority.records.at(-1).sequence+1;},
  (f:any)=>{const r=f.rows.find((r:any)=>r.event==="icon.ready" && r.fields.screen===0);r.record.fields.endpointEpoch="bad";},
 ]) {const {before,after}=sharedColdMemory();mutate(after);assert.throws(()=>launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before));}
});
test("embedded peer memory without retained disk ready stays unproven",()=>{
 const {before,after}=sharedColdMemory();
 after.rows=after.rows.filter(r=>r.event!=="icon.ready");
 for(const row of after.rows) {row.event="icon.prepared-draw";delete row.fields.elapsed_us;row.fields.prepared_at_boot_ms=100;row.fields.draw_started_at_boot_ms=101;}
 assert.throws(()=>launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before),/instead of retained disk/);
});

test("shared disk origin may load on either physical screen",()=>{const {before,after}=sharedColdMemory();for(const row of after.rows)row.fields.screen=1-Number(row.fields.screen);assert.deepEqual(launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before).sources,["memory","disk"]);});

function embeddedSharedColdMemory() {
 const f=sharedColdMemory();
 for(const row of f.after.rows.filter(r=>r.event==="icon.draw")) {row.event="icon.prepared-draw";delete row.fields.elapsed_us;row.fields.prepared_at_boot_ms=100;row.fields.draw_started_at_boot_ms=101;}
 return f;
}
test("embedded shared cold first draw preserves original disk then memory ready ordering",()=>{
 const {before,after}=embeddedSharedColdMemory();assert.deepEqual(launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before).sources,["disk","memory"]);
});
test("embedded receipt cannot turn later disk preparation into earlier origin",()=>{
 const {before,after}=embeddedSharedColdMemory();
 const diskReady=after.rows.find(r=>r.event==="icon.ready" && r.fields.screen===0)!;
 const memoryReady=after.rows.find(r=>r.event==="icon.ready" && r.fields.screen===1)!;
 // Both ready records still precede their own first draw, but memory preceded disk.
 const registration=after.authority.records.find(r=>r.source.kind==="daemon")!;
 const diskDraw=after.rows.find(r=>r.event==="icon.prepared-draw" && r.fields.screen===0)!;
 const memoryDraw=after.rows.find(r=>r.event==="icon.prepared-draw" && r.fields.screen===1)!;
 for(const [i,record] of [registration,memoryReady.record,diskReady.record,diskDraw.record,memoryDraw.record].entries())record.sequence=after.authority.afterSequence+i+1;
 after.authority.records.sort((a,b)=>a.sequence-b.sequence);
 assert.throws(()=>launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before),/instead of retained disk/);
});
test("retained ready disagreement cannot be hidden by embedded cache source",()=>{
 const {before,after}=embeddedSharedColdMemory();after.rows.find(r=>r.event==="icon.ready" && r.fields.screen===1)!.outcome="render";
 assert.throws(()=>launcherSharedColorCache(after,"ru.voboost.tests.launcherfixture",before),/draw source differs/);
});
