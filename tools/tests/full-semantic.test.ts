import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { FULL_CASE_ORDER, FULL_REQUIRED, fullSemanticSummary, fullVisualOnly } from "../emulator/full-semantic.ts";
const fixture = () => ({functionalMode:"full27",imageCapture:"deferred",passed:false,sourceAdmission:{status:"passed"},preparation:{status:"passed"},fixtureCleanup:{status:"passed"},activeOperation:null,
  boots:[{status:"unavailable",semanticStatus:"passed",screenshot:null}],
  cases:FULL_CASE_ORDER.map(number=>({number,status:"unavailable",subchecks:[...FULL_REQUIRED[number],"full-case-program-completed"].map(id=>({id,status:"passed",artifacts:["host-fixture-only"]}))}))});
test("exact 27 functional programs accept only semantic scope",()=>{
 const summary=fullSemanticSummary(fixture());assert.equal(summary.semanticFullAcceptance,true);
 assert.equal(summary.passed,false);assert.equal(summary.fullAcceptance,false);assert.equal(summary.visualAcceptance,false);
 assert.equal(summary.publication,"forbidden");assert.equal(summary.requiredCases.length,27);
});
for(const number of FULL_CASE_ORDER) test(`case ${number}: missing/failed/unavailable required proof blocks acceptance`,()=>{
 for(const id of [...FULL_REQUIRED[number],"full-case-program-completed"]){
  for(const mode of ["missing","failed","unavailable","duplicate"]){const e=fixture(),row=e.cases.find(r=>r.number===number)!;const check=row.subchecks.find(c=>c.id===id)!;
   if(mode==="missing")row.subchecks=row.subchecks.filter(c=>c.id!==id);else if(mode==="duplicate")row.subchecks.push({...check});else check.status=mode;
   assert.equal(fullSemanticSummary(e).semanticFullAcceptance,false,`${number}:${id}:${mode}`);
  }
 }
});
test("unknown unavailable checks cannot be relabelled visual",()=>{
 const e=fixture();e.cases[0].subchecks.push({id:"unimplemented-required-check",status:"unavailable",artifacts:[]});
 assert.equal(fullSemanticSummary(e).semanticFullAcceptance,false);
 assert.equal(fullVisualOnly(27,"prepared-root-enforcing-package"),false);
 assert.equal(fullVisualOnly(25,"runtime-state-outage-retry-recovery"),false);
});
test("visual-only pending remains pending, never silently passes",()=>{
 const e=fixture(),row=e.cases.find(r=>r.number===9)!;row.subchecks.push({id:"visual-font-cyrillic",status:"unavailable",artifacts:[]});
 assert.equal(fullSemanticSummary(e).semanticFullAcceptance,true);row.subchecks.at(-1)!.status="passed";assert.equal(fullSemanticSummary(e).semanticFullAcceptance,false);
});
test("missing or mixed scope, boot, source, active writer and incomplete absence deny acceptance",()=>{
 for(const mutate of [
  (e:any)=>e.cases.pop(),(e:any)=>e.cases[0].number=2,(e:any)=>e.functionalMode=undefined,
  (e:any)=>e.regressionScenario="vehicle",(e:any)=>e.imageCapture=undefined,(e:any)=>e.passed=true,
  (e:any)=>e.sourceAdmission.status="unavailable",(e:any)=>e.activeOperation={},(e:any)=>e.preparation.status="unavailable",
  (e:any)=>e.fixtureCleanup.status="failed",(e:any)=>e.boots=[],(e:any)=>e.boots[0].semanticStatus="unavailable",
  (e:any)=>e.boots[0].status="passed",(e:any)=>e.boots[0].screenshot="fake.png",
 ]){const e=fixture();mutate(e);assert.equal(fullSemanticSummary(e).semanticFullAcceptance,false);}
});
const gate=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
test("full mode preserves original destructive cycle, faults, host gate and boot/source proofs",()=>{
 assert.match(gate,/runFullSemanticGate/);assert.match(gate,/full27-install-uninstall/);
 assert.match(gate,/permit\.sessionReceiptSha256 !== digest/);assert.match(gate,/permit\.packageSha256 !== packageInventory/);
 assert.match(gate,/return executeGate\(c, adapter, undefined, undefined, true, undefined, undefined, true\)/);
 assert.match(gate,/if \(fullSemantic \|\| c\.localArtifact\) evidence\.sourceAdmission = await fullSourceAdmission\(\)/);
 assert.match(gate,/evidence\.sourceAdmission = await fullSourceAdmission\(\)/);
 assert.match(gate,/await agentHostGates/);
 for(const action of ["await install();","const result = await uninstall();","retryableUninstall","root-preparation-authority-contract","intermediate-boot-monitor"])assert.ok(gate.includes(action));
 assert.match(gate,/if \(deferred && request\.op === "session.screenshot" && !keyboardDiagnosticRequest\(request,keyboardCaptureScope\)\) throw/);
 const requestBody=gate.match(/export function keyboardDiagnosticRequest\(request: any, scope: any\): boolean \{([\s\S]*?)\n\}/)![1];
 const admitted=new Function("request","scope",requestBody);
 const guard=gate.match(/if \(deferred && request\.op === "session.screenshot" && !keyboardDiagnosticRequest\(request,keyboardCaptureScope\)\) throw new Error\([^\n]+/ )![0];
 const dispatch=new Function("deferred","request","keyboardCaptureScope","keyboardDiagnosticRequest",guard);
 const scope={directory:"owned-diagnostic",rect:[1,2,3,4]}, request={op:"session.screenshot",artifact_dir:scope.directory,screens:["primary"],rect:scope.rect};
 assert.doesNotThrow(()=>dispatch(true,request,scope,admitted));
 for(const changed of [{...request,artifact_dir:"elsewhere"},{...request,screens:["secondary"]},{...request,rect:[1,2,4,4]}]) assert.throws(()=>dispatch(true,changed,scope,admitted),/image dispatch prohibited/);
 assert.throws(()=>dispatch(true,request,null,admitted),/image dispatch prohibited/);
 assert.doesNotThrow(()=>dispatch(false,request,null,admitted));
 assert.match(gate,/image dispatch prohibited in deferred mode/);
 const subcheck=gate.slice(gate.indexOf("const subcheck ="),gate.indexOf("const persist ="));
 assert.match(subcheck,/fullSemantic && fullVisualOnly\(caseNumber, id\)/);
 assert.ok(!subcheck.includes("regression"));
});

test("optional Paint diagnostics preserve unavailable without masking failure or unknown checks", () => {
  for (const number of [9, 19]) {
    const ids = number === 9 ? ["actual-cyrillic-font-provenance"] : ["moscow", "yo", "mixed-prefix", "mixed-latin", "mixed-yo"].map(prefix => prefix + "-font-provenance");
    for (const id of ids) for (const status of ["passed", "unavailable", "failed", "not-run"]) {
      const e=fixture(), row=e.cases.find(r=>r.number===number)!; row.subchecks.push({id,status,artifacts:["retained-diagnostic"]});
      assert.equal(fullSemanticSummary(e).semanticFullAcceptance, status === "passed" || status === "unavailable");
      row.subchecks.push({...row.subchecks.at(-1)!}); assert.equal(fullSemanticSummary(e).semanticFullAcceptance, false);
    }
    const e=fixture(), row=e.cases.find(r=>r.number===number)!; row.subchecks.push({id:ids[0]+"-unknown",status:"unavailable",artifacts:[]}); assert.equal(fullSemanticSummary(e).semanticFullAcceptance,false);
  }
});

import { requiredSubchecksPassed } from "../emulator/full-semantic.ts";
test("normal aggregate accepts only exact optional unavailable diagnostics in their parent case", () => {
  const ids = [[9, "actual-cyrillic-font-provenance"], ...["moscow", "yo", "mixed-prefix", "mixed-latin", "mixed-yo"].map(x => [19, x + "-font-provenance"])] as const;
  for (const [number,id] of ids) {
    for (const status of ["passed", "unavailable", "failed", "not-run"]) {
      const row = {number, subchecks:[{id, status}, {id:"mandatory-proof",status:"passed"}]};
      assert.equal(requiredSubchecksPassed(row), status === "passed" || status === "unavailable");
      row.subchecks.push({...row.subchecks[0]}); assert.equal(requiredSubchecksPassed(row),false);
    }
    for (const wrong of [1, number === 9 ? 19 : 9]) assert.equal(requiredSubchecksPassed({number:wrong,subchecks:[{id,status:"unavailable"}]}),false);
    assert.equal(requiredSubchecksPassed({number,subchecks:[{id:id+"-extra",status:"unavailable"}]}),false);
  }
  for (const id of ["actual-cyrillic-input", "moscow-and-yo-codepoints", "mixed-ru-en-codepoints", "visual-font-cyrillic", "visual-navi-input-spacing", "unknown"]) for (const number of [9,19]) assert.equal(requiredSubchecksPassed({number,subchecks:[{id,status:"unavailable"}]}),false);
  for (const row of [null,{}, {number:9,subchecks:[]}, {number:9,subchecks:[null]}, {number:9,subchecks:[{status:"passed"}]}]) assert.equal(requiredSubchecksPassed(row),false);
});
