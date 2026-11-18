import { driverInvocation } from "../emulator/driver.ts";
import assert from "node:assert/strict";
import test from "node:test";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { bootPrimaryHomeProof, retainTimedOutDeviceOperation, runRegression, regressionSummary, assertRegressionInvocation, prepared, EXECUTION_ORDER, functionalEvidence, partialBootFailure, installedSemanticRequired, regressionOptions } from "../../release/emulator-gate.ts";
import { owningHostReply, fixture, reply, boot } from "./emulator-gate-fixture.ts";
const sha = (path: string) => createHash("sha256").update(readFileSync(path)).digest("hex");
test("fixed scenarios reject caller selectors before any adapter invocation", async () => {
  let calls = 0;
  await assert.rejects(runRegression({} as any, async () => { calls++; throw new Error("called"); }, [13] as any), /unknown fixed/);
  assert.equal(calls, 0);
  assert.deepEqual(EXECUTION_ORDER, [1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26]);
});
test("actual regression dispatch denies installation, removal, faults and fixtures", () => {
  const c = fixture();
  for (const flag of ["--install", "--uninstall", "--uninstall-emergency"])
    assert.throws(() => assertRegressionInvocation({ executable: "installer", args: [flag], timeoutMs: 1 }), /forbids/);
  for (const action of ["fault-window", "observe-settings-uninstall", "install-fixture", "remove-fixture"]) {
    const file = join(c.workspaceRoot, action + ".json"); writeFileSync(file, JSON.stringify({ action }));
    assert.throws(() => assertRegressionInvocation({ executable: "installer", args: ["--emulator-check", file], timeoutMs: 1 }), /forbids/);
  }
  for (const action of ["snapshot", "resident-status", "boot-observation", "open-settings", "reboot"]) {
    const file = join(c.workspaceRoot, action + ".json"); writeFileSync(file, JSON.stringify({ action }));
    assert.doesNotThrow(() => assertRegressionInvocation({ executable: "installer", args: ["--emulator-check", file], timeoutMs: 1 }));
  }
});
for (const mismatch of ["manifest", "catalog", "generation", "health", "serial", "mode", "boot"]) test("installed regression rejects " + mismatch + " after initial navigation and before case UI mutation", async () => {
  const c = fixture(), mutations: string[] = [], requests: string[] = [];
  const generation = "a".repeat(64);
  const resident: any = { schemaVersion:1, releaseIdentity:"1.0.0-beta1", manifestSha256:sha(join(c.closure,"voboost.json")), catalogSha256:sha(join(c.closure,"agents.json")), generationId:generation, startupMode:"interface", state:"ready", killed:false, panic:false, injections:[], executors:[] };
  const status: any = { deviceSerial:"emulator-5562", installedVersion:"1.0.0-beta1", contentRelation:"same", health:"healthy", daemonStatus:"running", startupMode:"interface", activeGeneration:generation };
  if (mismatch === "manifest") resident.manifestSha256 = "b".repeat(64);
  if (mismatch === "catalog") resident.catalogSha256 = "b".repeat(64);
  if (mismatch === "generation") resident.generationId = "b".repeat(64);
  if (mismatch === "health") status.health = "degraded";
  if (mismatch === "serial") status.deviceSerial = "emulator-9999";
  if (mismatch === "mode") resident.startupMode = "hidden";
  const done = (value: any) => ({ exitCode:0, stdout:JSON.stringify(value)+"\n", stderr:"" });
  const e = await runRegression(c, async inv => {
    if (inv.host) return owningHostReply(c, inv)!;
    if (inv.voyemu) return reply(c, inv.args[4], inv.args[4] === "status" ? { profiles:{[c.voyemuProfile]:{prepared:true,running:true,port:5562,pid:333}} } : { checks:prepared(c).checks });
    if (inv.args[0] === "--verify-package") return { exitCode:0,stdout:"1.0.0-beta1: exact offline closure verified\n",stderr:"" };
    if (inv.args[0] === "--status") return done(status);
    if (inv.args[0] === "--emulator-check") {
      const request = JSON.parse(readFileSync(inv.args[1],"utf8"));
      requests.push(request.action);
      if (!["boot-observation", "resident-status", "inspect"].includes(request.action)) mutations.push(request.action);
      const current = mismatch === "boot" && request.action === "resident-status" ? "22222222-2222-2222-2222-222222222222" : boot;
      const body = request.action === "resident-status" ? `bootCompleted=1\nbootId=${current}\nenforcing=Enforcing\nbootIdAfter=${current}\nresident-status:\n/data/voboost/status.json\n${JSON.stringify(resident)}\n\n` : `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n`;
      return done({schemaVersion:1,serial:"emulator-5562",body});
    }
    return {exitCode:1,stdout:"",stderr:"no diagnostic fixture"};
  }, "vehicle");
  assert.match(e.preparation.reason, /regression admission/);
  // Initial navigation precedes installed admission; a rejection permits no case mutation.
  assert.deepEqual(mutations, ["open-settings"]);
  assert.equal(requests[0], "open-settings");
  // Terminal failure retains its existing readonly forensic Inspect.
  assert.deepEqual(requests.slice(1), ["boot-observation", "resident-status", "inspect"]);
  assert.equal(e.passed, false);
  assert.equal(e.cases.length,27);
  assert.ok(e.cases.every((row:any)=>row.status==="not-run"));
  const summary = JSON.parse(readFileSync(join(c.output,"regression-summary.json"),"utf8"));
  assert.equal(summary.fullAcceptance,false); assert.equal(summary.passed,false);
});

for (const scenario of ["vehicle", "settings-radio"] as const) test("actual summary requires every fixed semantic check: " + scenario, () => {
  const evidence: any = { invocationId:"test", identity:{}, runtime:{}, cases:Array.from({length:27}, (_,i)=>({number:i+1,status:"not-run",subchecks:[]})), boots:[{status:"passed"}] };
  const required = regressionSummary(evidence, scenario).requiredCheckIds;
  assert.equal(required.length, scenario === "vehicle" ? 17 : 26);
  for (const value of required) {
    const [number, id] = value.split(":");
    const row = evidence.cases[Number(number)-1]; row.status = "passed";
    row.subchecks.push({id,status:"passed",reason:"observed",artifacts:[]});
  }
  if (scenario === "settings-radio") {
    evidence.cases[21].status = evidence.cases[22].status = "unavailable";
    evidence.cases[21].subchecks.push({id:"destructive-error-copy-excluded",status:"unavailable"});
    evidence.cases[22].subchecks.push({id:"fault-rejection-excluded",status:"unavailable"},{id:"startup-rejected-mutation",status:"unavailable"});
  }
  assert.equal(regressionSummary(evidence,scenario).passed,true);
  const check = (change: (copy:any)=>void) => {const copy=structuredClone(evidence);change(copy);assert.equal(regressionSummary(copy,scenario).passed,false);};
  check(copy=>copy.cases.forEach((row:any)=>row.subchecks=[]));
  check(copy=>copy.boots=[]);
  check(copy=>copy.boots[0].status="unavailable");
  for (const value of required) {
    const [number,id]=value.split(":"), index=Number(number)-1;
    check(copy=>copy.cases[index].subchecks=copy.cases[index].subchecks.filter((row:any)=>row.id!==id));
    check(copy=>copy.cases[index].subchecks.find((row:any)=>row.id===id).status="failed");
    check(copy=>copy.cases[index].subchecks.find((row:any)=>row.id===id).status="unavailable");
    check(copy=>copy.cases[index].subchecks.push(structuredClone(copy.cases[index].subchecks.find((row:any)=>row.id===id))));
  }
  const index = scenario === "vehicle" ? 12 : 21;
  check(copy=>copy.cases[index].subchecks.push({id:"unexpected-failure",status:"failed"}));
  check(copy=>copy.cases[index].subchecks.push({id:"unexpected-unavailable",status:"unavailable"}));
  // Omission labels cannot hide a failed observation or appear in another case.
  check(copy=>copy.cases[index].subchecks.push({id:"fault-rejection-excluded",status:"failed"}));
  if (scenario === "settings-radio") check(copy=>copy.cases[22].subchecks.find((row:any)=>row.id==="startup-rejected-mutation").status="failed");
});

function failedPartialReboot() {
  const reason = "TREE_REJECTED: empty_tree_unverified";
  return {imageCapture:"deferred",regressionScenario:"settings-radio",passed:false,actions:[{id:1,args:[],artifacts:[{path:"failed-ui.json",kind:"stdout"}]}],
    cases:[{number:22,status:"failed",actions:[1],reason,subchecks:[{id:"terminal-observation",status:"failed",reason,artifacts:[]}]},
      {number:26,status:"failed",actions:[],reason,subchecks:[{id:"intermediate-boot-monitor",status:"failed",reason,artifacts:["failed-ui.json"]}]}],
    boots:[{status:"failed",reason,actions:[1],screenshot:null}]};
}
test("partial reboot failure remains reportable and cannot become acceptance", () => {
  const evidence=failedPartialReboot();
  assert.equal(partialBootFailure(evidence,evidence.cases[1]),true);
  assert.doesNotThrow(()=>functionalEvidence(evidence));
  const result=regressionSummary(evidence,"settings-radio");
  assert.equal(result.semanticPassed,false);assert.equal(result.passed,false);assert.equal(result.fullAcceptance,false);
  assert.equal(result.publication,"forbidden");assert.deepEqual(result.rebootFailures,[evidence.cases[1]]);
  assert.equal(result.checks.some(check=>check.caseNumber===26),false);
  for(const subset of ["postboot","pw","fev-drawer"]){
    const e:any=failedPartialReboot();
    if(subset==="postboot")e.settingsPart=subset;
    else {e.regressionScenario="vehicle";e.vehiclePart=subset;e.cases[0].number=subset==="pw"?14:13;}
    assert.doesNotThrow(()=>functionalEvidence(e));
    assert.equal(regressionSummary(e,e.regressionScenario).semanticPassed,false);
  }
});
test("failed reboot exception rejects other scenarios, fake monitor proofs and passes",()=>{
  for(const mutate of [
    e=>e.cases[1].number=25,e=>e.cases[1].status="passed",e=>e.cases[1].status="unavailable",
    e=>e.cases[1].subchecks[0].status="passed",e=>e.cases[1].subchecks[0].id="extra-scenario",
    e=>e.cases[1].subchecks.push({...e.cases[1].subchecks[0]}),e=>e.cases[1].actions.push(1),
    e=>e.cases[1].reason="other failure",e=>e.cases[1].subchecks[0].reason="other failure",
    e=>e.cases[1].subchecks[0].artifacts=[],e=>e.boots=[],e=>e.boots[0].status="unavailable",e=>e.passed=true
  ]){const e=failedPartialReboot();mutate(e);assert.throws(()=>functionalEvidence(e));}
});


test("input requires actual text and keyboard proofs while preserving optional Paint diagnostics", () => {
  const required = installedSemanticRequired("input")!;
  assert.equal(required.length, 5);
  assert.deepEqual(required, installedSemanticRequired("lifecycle-input")!.filter(id => !id.startsWith("8:")));
  const evidence: any = {imageCapture:"deferred", regressionScenario:"input", invocationId:"input-test", identity:{}, runtime:{}, boots:[],
    cases:Array.from({length:27}, (_,i)=>({number:i+1,status:"not-run",subchecks:[]}))};
  for (const number of [9,19,10,11]) {
    const row = evidence.cases[number-1]; row.status="unavailable";
    row.subchecks=required.filter(id=>id.startsWith(number+":")).map(id=>({id:id.split(":")[1],status:"passed",artifacts:["proof.json"]}));
    row.subchecks.push({id:"installed-visual-pending",status:"unavailable",artifacts:[]});
  }
  const summary = regressionSummary(evidence,"input");
  assert.equal(summary.semanticPassed,true);
  assert.equal(summary.fullAcceptance,false); assert.equal(summary.passed,false);
  assert.equal(summary.publication,"forbidden");
  assert.deepEqual(summary.pendingAcceptance,["images","visual"]);
  assert.deepEqual(summary.checks.map((row:any)=>row.caseNumber),[9,10,11,19,19]);
  for (const requiredId of required) for (const fault of ["omitted","failed","unavailable","artifactless","duplicate"]) {
    const copy=structuredClone(evidence), [number,id]=requiredId.split(":"), row=copy.cases[Number(number)-1];
    const check=row.subchecks.find((entry:any)=>entry.id===id);
    if (fault==="omitted") row.subchecks=row.subchecks.filter((entry:any)=>entry.id!==id);
    else if (fault==="artifactless") check.artifacts=[];
    else if (fault==="duplicate") row.subchecks.push(structuredClone(check));
    else check.status=fault;
    assert.equal(regressionSummary(copy,"input").semanticPassed,false,requiredId+" "+fault);
  }
  const diagnostic=structuredClone(evidence);
  diagnostic.cases[8].subchecks.push({id:"actual-cyrillic-font-provenance",status:"unavailable",artifacts:["diagnostic.json"]});
  const diagnosticSummary=regressionSummary(diagnostic,"input");
  assert.equal(diagnosticSummary.semanticPassed,true);
  assert.equal(diagnosticSummary.optionalDiagnosticChecks.length,1);
  for (const status of ["failed","not-run"]) {
    const rejected=structuredClone(diagnostic); rejected.cases[8].subchecks.at(-1).status=status;
    assert.equal(regressionSummary(rejected,"input").semanticPassed,false);
  }
  const duplicateDiagnostic=structuredClone(diagnostic); duplicateDiagnostic.cases[8].subchecks.push(structuredClone(duplicateDiagnostic.cases[8].subchecks.at(-1)));
  assert.equal(regressionSummary(duplicateDiagnostic,"input").semanticPassed,false);
  const unknownDiagnostic=structuredClone(diagnostic); unknownDiagnostic.cases[8].subchecks.at(-1).id="unknown-font-diagnostic";
  assert.equal(regressionSummary(unknownDiagnostic,"input").semanticPassed,false);
  const wrongCase=structuredClone(evidence); wrongCase.cases[7].status="passed";
  assert.equal(regressionSummary(wrongCase,"input").semanticPassed,false);
  const visualAccepted=structuredClone(evidence); visualAccepted.cases[8].subchecks.at(-1).status="passed";
  assert.equal(regressionSummary(visualAccepted,"input").semanticPassed,false);
  assert.throws(()=>regressionOptions("input"),/require deferred/);
  assert.throws(()=>regressionOptions("input",{imageCapture:"deferred",vehiclePart:"pw"}),/requires deferred vehicle/);
  assert.equal(regressionOptions("input",{imageCapture:"deferred"}),true);
  assert.deepEqual(EXECUTION_ORDER,[1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26]);
});

test("state recovery is a fixed installed-only case25 and never full acceptance", () => {
  const required = installedSemanticRequired("state-recovery")!;
  assert.equal(required.length, 6);
  assert.ok(required.every(id => id.startsWith("25:")));
  assert.equal(regressionOptions("state-recovery", {imageCapture:"deferred"}), true);
  assert.throws(() => regressionOptions("state-recovery", {}), /installed semantic/);
  const evidence:any = {imageCapture:"deferred",regressionScenario:"state-recovery",passed:false,
    invocationId:"recovery",identity:{},runtime:{},boots:[],cases:Array.from({length:27},(_,i)=>({number:i+1,status:"not-run",subchecks:[]}))};
  evidence.cases[24] = {number:25,status:"unavailable",subchecks:[...required.map(value=>({id:value.split(":")[1],status:"passed",artifacts:["actual-observation.json"]})),{id:"installed-visual-pending",status:"unavailable",artifacts:[]}]};
  const summary = regressionSummary(evidence,"state-recovery");
  assert.equal(summary.semanticPassed,true);
  assert.equal(summary.fullAcceptance,false);
  assert.equal(summary.passed,false);
  for (const value of required) {
    const copy = structuredClone(evidence);
    copy.cases[24].subchecks = copy.cases[24].subchecks.filter((row:any)=>row.id!==value.split(":")[1]);
    assert.equal(regressionSummary(copy,"state-recovery").semanticPassed,false);
  }
  evidence.cases[12].status="failed";
  assert.equal(regressionSummary(evidence,"state-recovery").semanticPassed,false);
});

test("state recovery admits only the existing bounded state-reply fault and no installer operation", () => {
  const c=fixture(), file=join(c.output,"recovery-window.json");
  mkdirSync(c.output,{recursive:true});
  const invocation:any={executable:"installer",args:["--emulator-check",file],timeoutMs:120000};
  writeFileSync(file,JSON.stringify({action:"fault-window",kind:"vehicle-state-reply-loss"}));
  assert.doesNotThrow(()=>assertRegressionInvocation(invocation,"state-recovery"));
  assert.throws(()=>assertRegressionInvocation(invocation,"vehicle"),/forbids/);
  writeFileSync(file,JSON.stringify({action:"fault-window",kind:"settings-mutation-reply-loss"}));
  assert.throws(()=>assertRegressionInvocation(invocation,"state-recovery"),/forbids/);
  for (const action of ["--install","--uninstall","--uninstall-emergency"])
    assert.throws(()=>assertRegressionInvocation({...invocation,args:[action]},"state-recovery"),/forbids/);
});


test("vehicle startup requires a fresh boot and both owning proofs without full acceptance", () => {
  const scenario = "vehicle-startup" as const;
  const required = installedSemanticRequired(scenario)!;
  assert.deepEqual(required, ["27:vehicle-startup-current-receipts", "27:host-passive-acc-listener-contract"]);
  assert.equal(regressionOptions(scenario, {imageCapture:"deferred"}), true);
  const e:any = {imageCapture:"deferred",regressionScenario:scenario,passed:false,invocationId:"early",identity:{},runtime:{},boots:[],cases:Array.from({length:27},(_,i)=>({number:i+1,status:"not-run",subchecks:[]}))};
  e.cases[26]={number:27,status:"unavailable",subchecks:[...required.map(value=>({id:value.split(":")[1],status:"passed",artifacts:["receipt.json"]})),{id:"installed-visual-pending",status:"unavailable",artifacts:[]}]};
  assert.equal(regressionSummary(e,scenario).semanticPassed,false);
  e.boots=[{status:"unavailable",semanticStatus:"passed",screenshot:null}];
  const accepted=regressionSummary(e,scenario);
  assert.equal(accepted.semanticPassed,true);assert.equal(accepted.fullAcceptance,false);assert.equal(accepted.passed,false);
  e.cases[26].subchecks[0].status="unavailable";assert.equal(regressionSummary(e,scenario).semanticPassed,false);
});


test("read-only boot timeout never blocks exact negative installation acknowledgement", () => {
  const c=fixture(), file=join(c.workspaceRoot,"timeout-observation.json");
  const inv:any=driverInvocation(join(c.package,"voboost-installer"),file,"emulator-5562",{timeoutMs:1});
  const result:any={stdout:"",stderr:"",exitCode:null,timedOut:true};
  for(const action of ["boot-observation","resident-status","inspect"]){writeFileSync(file,JSON.stringify({action}));assert.equal(retainTimedOutDeviceOperation(inv,result),false);}
  for(const request of [{action:"install-acceptance",decision:"reject"},{action:"reboot"},{action:"boot-observation",unexpected:true}]){writeFileSync(file,JSON.stringify(request));assert.equal(retainTimedOutDeviceOperation(inv,result),true);}
  writeFileSync(file,JSON.stringify({action:"boot-observation"}));
  assert.equal(retainTimedOutDeviceOperation({...inv,args:["--emulator-check",file,"--device-serial","emulator-5562"]},result),true);
  assert.equal(retainTimedOutDeviceOperation({...inv,args:[...inv.args,"--adb-server-port","25562"]},result),true);
  writeFileSync(file,"not-json");assert.equal(retainTimedOutDeviceOperation(inv,result),true);
  assert.equal(retainTimedOutDeviceOperation({...inv,args:["--install"]},result),true);
  assert.equal(retainTimedOutDeviceOperation(inv,{...result,timedOut:false}),false);
  assert.equal(retainTimedOutDeviceOperation({...inv,host:true},result),false);
  assert.equal(retainTimedOutDeviceOperation({...inv,voyemu:true},result),false);
});


test("intermediate install and cache-recovery boots use the existing SDK primary-home proof", () => {
  for(const kind of ["installer-1","installer-2","cache-recovery"]) {
    assert.equal(bootPrimaryHomeProof(true,kind,false),true);
    assert.equal(bootPrimaryHomeProof(false,kind,false),false);
    assert.equal(bootPrimaryHomeProof(true,kind,true),false);
  }
  for(const kind of ["functional","preparation-uninstall","settings-uninstall","installer-0","installer-bad"])assert.equal(bootPrimaryHomeProof(true,kind,false),false);
  const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
  const block=source.slice(source.indexOf("const nativeHome = bootPrimaryHomeProof"),source.indexOf("if (kind === \"installer-1\")",source.indexOf("const nativeHome = bootPrimaryHomeProof")));
  assert(block.includes("primaryHomeCaptureBoot(nativeCaptureAction(evidence, captureAction), post.boot)"));
  assert(block.includes("before: beforeHome!"));assert(block.includes("after: await home()"));
});
