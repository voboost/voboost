import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { parseInstallerLogs } from "./installer-logs.ts";
import { launcherLifetime } from "./launcher-lifetime.ts";
import { launcherFirstPublished } from "./launcher-first-published.ts";
import { CacheEvidenceUnavailable } from "./launcher-cache.ts";
import { logArchive } from "../tests/installer-log-fixture.ts";
const original = JSON.parse(readFileSync(new URL("../../testdata/emulator/cycle125-launcher-cache-envelope.json", import.meta.url), "utf8"));
const identity = {serial:"emulator-5566",version:"1.0.0-beta1",manifestSha256:"a".repeat(64)};

function fixture(mutate: (records:any[])=>void = ()=>{}) {
 const nativeLifetime=structuredClone(original.nativeLifetime);
 const seed=original.rows[0].record;
 const records:any[]=[{...structuredClone(seed), sequence:1, message:"before official install-fixture",source:{kind:"daemon"}}];
 const add=(event:string,outcome:string,fields:any)=>records.push({...structuredClone(seed),sequence:records.length+1,message:"VOBDBG1 "+JSON.stringify({agent:"launcher-apps",event,outcome,fields})});
 add("package.lifecycle.item","reconcile-requested",{package_name:"ru.voboost.tests.launcherfixture",generation:4,at_boot_ms:90});
 for(const screen of [0,1]) {
  add("icon.first-projection","deferred",{package_name:"ru.voboost.tests.launcherfixture",generation:1,screen,icon_ready:false,bean_emitted:false});
  add("icon.first-emitted","emitted",{package_name:"ru.voboost.tests.launcherfixture",generation:2,screen,icon_ready:true,at_boot_ms:110});
  add("item.first-bind","bound",{package_name:"ru.voboost.tests.launcherfixture",generation:2,screen,at_boot_ms:115});
 }
 for(const screen of [0,1]) {
  const fields=structuredClone(original.rows.find((r:any)=>r.event==="icon.draw"&&r.fields.screen===screen).fields);
  delete fields.elapsed_us; fields.package_name="ru.voboost.tests.launcherfixture"; fields.prepared_at_boot_ms=100;fields.draw_started_at_boot_ms=120;
  add("icon.prepared-draw","first-draw",fields);
 }
 mutate(records); records.forEach((record,i)=>record.sequence=i+1);
 const parsed=parseInstallerLogs(logArchive(identity,records),identity);
 const authority={...launcherLifetime(nativeLifetime.beforeBody,nativeLifetime.observed,nativeLifetime.afterBody),nativeLifetime,records:parsed.records,afterSequence:1,baselineRecord:parsed.records[0]};
 return {parsed,authority};
}
function run(f:ReturnType<typeof fixture>) {return launcherFirstPublished(f.parsed.diagnostics,f.authority,"ru.voboost.tests.launcherfixture");}
function edit(records:any[],event:string,screen:number,fn:(row:any)=>void){const record=records.find(r=>{if(!r.message.startsWith("VOBDBG1 "))return false;const row=JSON.parse(r.message.slice(8));return row.event===event&&row.fields.screen===screen;});const row=JSON.parse(record.message.slice(8));fn(row);record.message="VOBDBG1 "+JSON.stringify(row);}
test("actual ordinary tar.xz parser admits first published and bound after safe deferred attempts, with measured latency",()=>{const result=run(fixture());assert.equal(result.surfaces.length,2);assert.equal(result.surfaces[0].packageEventToEmittedMs,20);assert.equal(result.surfaces[0].packageEventToBoundMs,25);assert.equal(result.surfaces[0].firstDecisions[0].outcome,"deferred");assert.equal(result.immediateAppearanceAcceptance,false);});
test("legacy ready/draw only cannot masquerade as first publication",()=>{const f=fixture(records=>{for(let i=records.length-1;i>=0;i--)if(/icon.first-emitted|item.first-bind/.test(records[i].message))records.splice(i,1);});assert.throws(()=>run(f),CacheEvidenceUnavailable);});
test("first failed bind cannot be hidden by later bound generation",()=>{const f=fixture(records=>{edit(records,"item.first-bind",0,row=>row.outcome="failed");const row=structuredClone(records.find(r=>r.message.includes('item.first-bind')));const value=JSON.parse(row.message.slice(8));value.outcome="bound";value.fields.generation=3;row.message="VOBDBG1 "+JSON.stringify(value);records.push(row);});assert.throws(()=>run(f),/first published bind failed/);});
test("first nonready published item cannot be hidden by later ready emission",()=>{const f=fixture(records=>{edit(records,"icon.first-emitted",0,row=>row.fields.icon_ready=false);});assert.throws(()=>run(f),/not icon-ready/);});
test("missing first bind cannot use ordinary later item.bind",()=>{const f=fixture(records=>edit(records,"item.first-bind",0,row=>row.event="item.bind"));assert.throws(()=>run(f),CacheEvidenceUnavailable);});
test("unknown fields duplicate markers and changed generation fail strictly",()=>{for(const mutate of [(rows:any[])=>edit(rows,"icon.first-emitted",0,row=>row.fields.extra=true),(rows:any[])=>rows.push(structuredClone(rows.find(r=>r.message.includes('icon.first-emitted')))),(rows:any[])=>edit(rows,"item.first-bind",0,row=>row.fields.generation=3)])assert.throws(()=>run(fixture(mutate)));});
test("bad canonical outer pid starttime generation epoch or detached record cannot supply current authority",()=>{for(const key of ["targetPid","targetStarttime","generationId","catalogSha256","endpointEpoch"]){const f=fixture(records=>{const row=records.find(r=>r.message.includes('icon.first-emitted'));row.fields[key]="untrusted";});assert.throws(()=>run(f),/authenticated endpoint/);}const f=fixture();f.parsed.diagnostics.find(r=>r.event==="icon.first-emitted")!.record={...f.parsed.records[0]};assert.throws(()=>run(f));});
test("boot and process lifetime crossing rejected by independent native bracket",()=>{const f=fixture();f.authority.nativeLifetime.afterBody=f.authority.nativeLifetime.afterBody.replace('starttime=721','starttime=722');assert.throws(()=>run(f),/crossed/);});
test("noncanonical timing and prep after first published rejected",()=>{for(const mutate of [(rows:any[])=>edit(rows,"item.first-bind",0,row=>row.fields.at_boot_ms=109),(rows:any[])=>edit(rows,"icon.first-emitted",0,row=>row.fields.at_boot_ms="110"),(rows:any[])=>edit(rows,"icon.prepared-draw",0,row=>row.fields.prepared_at_boot_ms=111)])assert.throws(()=>run(fixture(mutate)));});
test("ambiguous package event makes latency unavailable without inventing SLA or failing ready binding",()=>{const f=fixture(records=>records.splice(2,0,structuredClone(records[1])));const result=run(f);assert.equal(result.surfaces[0].packageEventToEmittedMs,null);assert.match(result.surfaces[0].latencyStatus,/unavailable/);});
test("missing package event makes latency unavailable while both first published items remain proven",()=>{const result=run(fixture(records=>records.splice(1,1)));assert.equal(result.surfaces[1].packageEventToBoundMs,null);});
test("first generation can differ by surface but each must match its own earliest prepared draw",()=>{const f=fixture(records=>{for(const event of ["icon.first-emitted","item.first-bind","icon.prepared-draw"])edit(records,event,1,row=>row.fields.generation=3);});assert.deepEqual(run(f).surfaces.map(row=>row.generation),[2,3]);});
test("case12 owns absent-to-install and case18 cannot replace initial published proof with later scan; launcher inventory remains six",()=>{const source=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');assert.match(source,/const installCacheBaseline = await observations\(\);/);assert.match(source,/firstPublishedFixture = launcherFirstPublished/);assert.match(source,/first published fixture ownership missing/);assert.match(source,/original first-published installed-fixture proof missing; later catalog scan cannot replace it/);const required=source.match(/const required = scenario === "launcher" \? \[([^\n]+)\] : scenario === "vehicle"/)![1];assert.equal((required.match(/launcher-visible-page-order-after-reboot|launcher-semantic-surfaces-lifetime|launcher-two-screen-disk-cache-after-reboot|launcher-theme-cache-roundtrip|fresh-package-classification|owned-fixture-cleanup/g)||[]).length,6);});

test("a later-generation emitted marker cannot replace or duplicate the first published witness",()=>{const f=fixture(records=>{const record=structuredClone(records.find(r=>r.message.includes('icon.first-emitted')));const value=JSON.parse(record.message.slice(8));value.fields.generation=3;record.message="VOBDBG1 "+JSON.stringify(value);records.push(record);});assert.throws(()=>run(f),/duplicate/);});

test("stale prepared draw before genuine current draw never supplies selected timing",()=>{for(const key of ["boot_id","target_pid","process_start_ticks","code_generation"]){const f=fixture(records=>{const index=records.findIndex(r=>r.message.includes('icon.prepared-draw'));const forged=structuredClone(records[index]);const value=JSON.parse(forged.message.slice(8));value.fields[key]=key==="target_pid"?999:"stale";value.fields.prepared_at_boot_ms=1;forged.message="VOBDBG1 "+JSON.stringify(value);records.splice(index,0,forged);});const result=run(f);assert.equal(result.surfaces[0].preparedAtBootMs,100);assert.equal(result.surfaces[0].drawSequence,f.parsed.diagnostics.find(r=>r.event==="icon.prepared-draw"&&r.fields.screen===0&&r.fields.prepared_at_boot_ms===100)!.record.sequence);}});
test("forged stale draw alone remains unavailable and wrong endpoint cannot bind current timing",()=>{const missing=fixture(records=>{edit(records,"icon.prepared-draw",0,row=>row.fields.target_pid=999);});assert.throws(()=>run(missing),CacheEvidenceUnavailable);const epoch=fixture(records=>{const row=records.find(r=>r.message.includes('icon.prepared-draw'));row.fields.endpointEpoch="99";});assert.throws(()=>run(epoch),/selected draw authenticated endpoint/);});
