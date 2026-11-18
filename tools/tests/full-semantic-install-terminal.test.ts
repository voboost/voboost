import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
import { caseFailureFrames } from "../../release/emulator-gate.ts";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { semanticBoot, fullFunctionalCase } from "../emulator/full-semantic.ts";

// Execute the actual install terminal tail, including durable reboot coverage and
// healthy installed status, followed by the actual case completion bookkeeping.
// No Android, CLI installation or image capture is performed by this host test.
const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const terminalStart = source.indexOf('    const final = await bootCheck("installer-terminal", before);');
const terminalEnd = source.indexOf('    if (initial.health !== "degraded"', terminalStart);
assert.ok(terminalStart > 0 && terminalEnd > terminalStart);
const terminal = source.slice(terminalStart, terminalEnd);
const bookkeepingStart = source.indexOf("        await caseProgram(number);");
const bookkeepingEnd = source.indexOf("      if (number === 17 &&", bookkeepingStart);
assert.ok(bookkeepingStart > 0 && bookkeepingEnd > bookkeepingStart);
// The production break terminates the outer case loop. Use a one-iteration loop.
const bookkeeping = "for (const number of [1]) { try {\n" + source.slice(bookkeepingStart, bookkeepingEnd) + "}";
class Unavailable extends Error {}
const execute = new Function("context", "semanticBoot", "fullFunctionalCase", "Unavailable", "caseFailureFrames", "createHash", "join", "writeFileSync", "return " + stripTypeScriptTypes(`(async function executeCase() {
  const {fullSemantic, boot, installedStatus, cliFailure} = context;
  const deferred = true, regression = undefined, evidence = {boots:[],actions:[]};
  const row = {number:1,status:"not-run",subchecks:[],reason:""};
  const before = "prior", serial = "emulator-5566", manifest = {releaseIdentity:"1.0.0-beta1"};
  const c = {output:"/host-fixture"};
  const caseNumber = 1, seen = new Set([1]);
  const bootCheck = async () => boot;
  const bootObservation = async () => ({boot:"current",rebootCount:1});
  let statusReads = 0;
  const cli = async () => { statusReads++; if(cliFailure) throw cliFailure; return {stdout:JSON.stringify(installedStatus)}; };
  const resultOk = () => {}, jsonReceipt = JSON.parse;
  const proof = (id,status,reason) => row.subchecks.push({id,status,reason,artifacts:[]});
  const installedSemanticRequired = () => false;
  const caseProgram = async () => { ${terminal} };
  ${bookkeeping}
  return {row, statusReads, semanticPassed:fullFunctionalCase(row)};
})();`));
const run = (context: any) => execute(context, semanticBoot, fullFunctionalCase, Unavailable, caseFailureFrames, createHash, join, (_path,bytes,options)=>{assert.equal(options.flag,"wx");assert.ok(Array.isArray(JSON.parse(bytes).frames));});

const healthy = {deviceSerial:"emulator-5566",installedVersion:"1.0.0-beta1",contentRelation:"same",health:"healthy"};
const deferredBoot = {status:"unavailable",semanticStatus:"passed",screenshot:null,reason:"Readiness, Enforcing, boot identity and fresh UI hierarchy passed; image capture deferred"};
test("full27 installed case reaches completed bookkeeping with verified deferred images", async () => {
  const result = await run({fullSemantic:true,boot:deferredBoot,installedStatus:healthy});
  assert.equal(result.statusReads,1);
  assert.equal(result.row.status,"unavailable");
  assert.equal(result.semanticPassed,true);
  assert.deepEqual(result.row.subchecks.map((c:any)=>[c.id,c.status]), [["full-case-program-completed","passed"]]);
});
for(const [name, boot] of [
  ["genuine failed boot", {...deferredBoot,status:"failed"}],
  ["unavailable semantic observation", {...deferredBoot,semanticStatus:"unavailable"}],
  ["missing semantic observation", {status:"unavailable",screenshot:null,reason:"not observed"}],
  ["unexpected image claim", {...deferredBoot,screenshot:"unexpected.png"}],
]) test(name+" never completes the installed case", async()=>{
  const result=await run({fullSemantic:true,boot,installedStatus:healthy});
  assert.equal(result.statusReads,0);
  assert.equal(result.row.status,"failed");
  assert.equal(result.semanticPassed,false);
  assert.ok(!result.row.subchecks.some((c:any)=>c.id==="full-case-program-completed"));
});
test("deferred images are not accepted outside explicit full27",async()=>{
  const result=await run({fullSemantic:false,boot:deferredBoot,installedStatus:healthy});
  assert.equal(result.row.status,"failed"); assert.equal(result.statusReads,0);
});
for(const [name,context] of [
  ["wrong installed identity", {installedStatus:{...healthy,contentRelation:"different"}}],
  ["unavailable installed status", {cliFailure:new Unavailable("status unavailable")}],
  ["failed installed status", {cliFailure:new Error("status failed")}],
])test(name+" cannot acquire completion merely from a semantic boot",async()=>{
  const result=await run({fullSemantic:true,boot:deferredBoot,installedStatus:healthy,...context});
  assert.equal(result.semanticPassed,false);
  assert.ok(!result.row.subchecks.some((c:any)=>c.id==="full-case-program-completed"));
  assert.equal(result.row.status,name.startsWith("unavailable")?"unavailable":"failed");
});
test("ordinary visual-passed boot retains existing non-full27 case behavior",async()=>{
  const result=await run({fullSemantic:false,boot:{status:"passed"},installedStatus:healthy});
  assert.equal(result.row.status,"passed");assert.equal(result.statusReads,1);
});
