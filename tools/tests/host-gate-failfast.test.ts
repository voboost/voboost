import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
const source = readFileSync(process.env.GATE_SOURCE || new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const start = source.indexOf("    try {\n      hostArtifacts = await agentHostGates");
const direct = source.indexOf("    hostArtifacts = await agentHostGates");
const end = source.indexOf("    await fullVerify();", Math.max(start, direct));
assert(end > 0 && (start >= 0 || direct >= 0));
const body = source.slice(start >= 0 ? start : direct, end);
const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
class HostSourceMismatch extends Error {}
for (const failure of [new Error("owning host process exited 1"), new Error("required JUnit skipped"), new HostSourceMismatch("source mismatch"), new Error("copy malformed")]) {
    test(`${failure.message} stops before runtime/device access`, async () => {
      const calls = [];
      const execute = new AsyncFunction("agentHostGates", "settingsCopy", "native", "InstallerUi", "HostSourceMismatch", `let hostArtifacts=[], copy=null, hostFailure, copyFailure, runtime, serial, ui; const c={workspaceRoot:"root",closure:"closure",output:"output",package:"package"}; const evidence={}; const record=()=>{}; const uiAdapter={},ownership={}; const join=(...s)=>s.join("/"); ${stripTypeScriptTypes(body)}`);
      await assert.rejects(execute(async () => { calls.push("host"); if (failure.message !== "copy malformed") throw failure; return ["fresh-host-report"]; }, () => { calls.push("copy"); throw failure; }, { status: async () => { calls.push("device-status"); return {port:5566}; } }, class { constructor() { calls.push("ui-device"); } }, HostSourceMismatch), error => error === failure);
      assert.deepEqual(calls, failure.message === "copy malformed" ? ["host", "copy"] : ["host"]);
    });
}
test("successful owning gates and copy precede runtime and UI admission", async () => {
  const calls=[];
  const execute=new AsyncFunction("agentHostGates","settingsCopy","native","InstallerUi","HostSourceMismatch", `let hostArtifacts=[],copy,hostFailure,copyFailure,runtime,serial,ui; const c={workspaceRoot:"root",closure:"closure",output:"output",package:"package"}; const evidence={}; const record=()=>{};const uiAdapter={},ownership={};const join=(...s)=>s.join("/");${stripTypeScriptTypes(body)}`);
  await execute(async()=>{calls.push("host");return ["fresh"];},()=>{calls.push("copy");return {};},{status:async()=>{calls.push("device-status");return {port:5566};}},class {constructor(){calls.push("ui-device");}},HostSourceMismatch);
  assert.deepEqual(calls,["host","copy","device-status","ui-device"]);
});
test("all public scenarios share executeGate startup admission",()=>{
  const entry = source.slice(source.indexOf("export async function runGate"), source.indexOf("async function executeGate"));
  assert.match(entry,/return executeGate\(c, adapter, review\)/);
  assert.match(entry,/return executeGate\(c, adapter, undefined, scenario, deferred, options\.vehiclePart, options\.settingsPart\)/);
  assert.match(entry,/scenario !== "vehicle" && scenario !== "settings-radio" && scenario !== "launcher"/);
});

import { assertLocalHostCatalog, junit } from "../emulator/host-gates.ts";
test("local host catalog uses real local authority without source-build receipt",()=>{
 const value={schemaVersion:1,authority:"persistent-local-test-only",publication:"forbidden",artifactSet:{artifacts:[{component:"agents",file:"agents.json",sha256:"a".repeat(64)}]}};
 assert.doesNotThrow(()=>assertLocalHostCatalog(value,"a".repeat(64)));
 assert.throws(()=>assertLocalHostCatalog({...value,kind:"source-build",authority:undefined},"a".repeat(64)));
 assert.throws(()=>assertLocalHostCatalog(value,"b".repeat(64)));
});
test("local authority never masks failed JUnit",()=>{
 assert.throws(()=>junit("<testsuite name=\"example\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\"><testcase name=\"bad\"><failure/></testcase></testsuite>","example"));
});

import { AGENT_HOST_ARGS, AGENT_TESTS, assertFreshHostExecutions } from "../emulator/host-gates.ts";
test("host gates rerun both report producers without forcing dependency rebuilds", () => {
  assert.deepEqual(AGENT_HOST_ARGS, ["--offline", "--no-daemon", "--console=plain", ":generateSettingsCopyEvidence", "--rerun", ":test", "--rerun", ...AGENT_TESTS.flatMap(name => ["--tests", name])]);
  assert(!AGENT_HOST_ARGS.includes("--rerun-tasks"));
});
test("fresh host execution excludes cached skipped missing or duplicate reports", () => {
 const fresh="> Task :generateSettingsCopyEvidence\n> Task :test\n";
 assert.doesNotThrow(()=>assertFreshHostExecutions(fresh));
 for (const state of ["UP-TO-DATE","FROM-CACHE","SKIPPED","NO-SOURCE","FAILED"]) {
  for(const task of [":test",":generateSettingsCopyEvidence"]) assert.throws(()=>assertFreshHostExecutions(fresh.replace(`> Task ${task}\n`,`> Task ${task} ${state}\n`)));
 }
 assert.throws(()=>assertFreshHostExecutions("> Task :test\n"));
 assert.throws(()=>assertFreshHostExecutions(fresh+"> Task :test\n"));
 assert.throws(()=>assertFreshHostExecutions("> Task :generateSettingsCopyEvidence\n> Task :testClasses\n"));
});

// Execute the actual runtime admission body, including both local inventory fences.
const policyStart = source.indexOf('  const readDisablePolicy = async () => {');
const policyEnd = source.indexOf('  const checkboxTargets =', policyStart);
assert(policyStart > 0 && policyEnd > policyStart);
const policyBody = stripTypeScriptTypes(source.slice(policyStart, policyEnd));
async function policyFixture(options: { local?: boolean; missing?: boolean; wrongCatalog?: boolean; drift?: boolean; sourceDrift?: boolean; receiptDrift?: boolean; catalogDrift?: boolean; ciMismatch?: boolean; upgrade?: boolean } = {}) {
 const calls: string[] = [], writes: any[] = [];
 const snapshot={name:'agents',head:'original'};
 const receipt:any = options.local ? {schemaVersion:1,authority:'persistent-local-test-only',publication:'forbidden',artifactSet:{artifacts:[{component:'agents',file:'agents.json',sha256:options.wrongCatalog?'wrong':'catalog'}]}} : {schemaVersion:1,kind:'source-build',artifacts:{'agents.json':{sha256:options.wrongCatalog?'wrong':'catalog'}},sources:[options.ciMismatch?{name:'agents',head:'wrong'}:snapshot]};
 if(options.upgrade) {receipt.kind='source-build'; delete receipt.authority; receipt.publication='allowed';}
 let admissions=0, snapshots=0, receiptReads=0, catalogReads=0;
 const execute=new AsyncFunction('c','join','dirname','readFileSync','digest','fullSourceAdmission','assertLocalHostCatalog','snapshotRepository','unavailable','CLASSES','regular','admitStopModes','catalog','writeFileSync',`${policyBody}\nreturn readDisablePolicy();`);
 const result=execute({workspaceRoot:'workspace',closure:'build/closure',output:'out',...(options.local?{localArtifact:{verificationDelta:[]}}:{})},(...parts:string[])=>parts.join('/'),()=> 'build',(path:string)=>{
  calls.push(path);
  if(path.endsWith('receipt.json') || path.endsWith('local-build-evidence.json')) { if(options.missing) throw new Error('ENOENT'); receiptReads++; return Buffer.from(JSON.stringify(options.receiptDrift && receiptReads>1 ? {...receipt,extra:'changed'}:receipt)); }
  if(path.endsWith('agents.json')) {catalogReads++;return Buffer.from(options.catalogDrift && catalogReads>1?'changed':'catalog');}
  return Buffer.from('source');
 },(bytes:Buffer)=>bytes.toString(),async()=>{admissions++;if(options.drift && admissions===2) throw new Error('full inventory drift');return {status:'passed'};},assertLocalHostCatalog,async()=>{snapshots++;return options.sourceDrift && snapshots===2?{...snapshot,head:'changed'}:snapshot;},(message:string)=>{throw new Error(message);},{},()=>{},()=>({modes:{font:'restart'}}),[],(_path:string,body:string)=>writes.push(JSON.parse(body)));
 return {result,calls,writes,get admissions(){return admissions;}};
}
test('disable policy local admission uses real evidence and both full inventories without CI receipt',async()=>{
 const fixture=await policyFixture({local:true});await fixture.result;
 assert.equal(fixture.admissions,2);assert(!fixture.calls.some(path=>path.endsWith('/receipt.json')));
 assert.equal(fixture.writes[0].sourceAuthority,'local-artifact');
 assert.equal(fixture.writes[0].publication,undefined);
});
for(const option of ['missing','wrongCatalog','drift','sourceDrift','receiptDrift','catalogDrift','upgrade'] as const) test(`disable policy local rejects ${option}`,async()=>{
 const fixture=await policyFixture({local:true,[option]:true});await assert.rejects(fixture.result);assert.equal(fixture.writes.length,0);
});
test('disable policy CI still requires exact source-build catalog and source snapshot',async()=>{
 const good=await policyFixture();await good.result;assert.equal(good.admissions,0);assert.equal(good.writes[0].sourceAuthority,'source-build');
 const bad=await policyFixture({ciMismatch:true});await assert.rejects(bad.result,/sources differ/);assert.equal(bad.writes.length,0);
 const wrong=await policyFixture({wrongCatalog:true});await assert.rejects(wrong.result,/source-build catalog/);assert.equal(wrong.writes.length,0);
});
