import test from "node:test";
import assert from "node:assert/strict";
import { evidence } from "../emulator/gate-report-fixture.ts";
import { gateReport } from "../emulator/gate-report.ts";
import { runRegressionReport } from "../../release/emulator-regression.ts";

for (const terminal of ["passed", "failed", "preparation-failed"] as const) {
  test(`partial ${terminal} writes owning formatter report without rewriting cases`, async () => {
    const receipt = evidence();
    if (terminal === "preparation-failed") Object.assign(receipt.preparation, { status: "failed", reason: "retained preparation failure" });
    else Object.assign(receipt.cases[11], { status: "failed", reason: "canonical full acceptance incomplete", subchecks: [{ id: "retained", status: "failed", reason: "retained case", artifacts: [] }] });
    const before = JSON.stringify(receipt);
    const summary = { evidenceType: "installed-kit-partial-regression", scenario: "launcher", fullAcceptance: false, publication: "forbidden", passed: terminal === "passed", invocationId: receipt.invocationId };
    let report = ""; const errors: string[] = [];
    const exit = await runRegressionReport("/run", "launcher", async () => { if (terminal === "preparation-failed") throw new Error("preparation failed"); }, {
      read: path => JSON.stringify(path.endsWith("regression-summary.json") ? summary : receipt),
      write: (path, text) => { assert.equal(path, "/run/report.md"); report = text; }, error: message => errors.push(message),
    });
    assert.equal(exit, terminal === "passed" ? 0 : terminal === "failed" ? 1 : 2);
    assert.ok(report.endsWith(gateReport(receipt, "/run/emulator-runtime-evidence.json")));
    assert.match(report, /Full 27-case acceptance: false. Publication: forbidden/);
    assert.match(report, /Recorded overall result: not passed/);
    assert.equal(JSON.stringify(receipt), before);
    assert.equal(errors.length, terminal === "preparation-failed" ? 1 : 0);
  });
}
test("missing or contradictory report input remains explicit failure", async () => {
  for (const originalFailure of [false, true]) {
    const errors: string[] = [];
    const exit = await runRegressionReport("/run", "launcher", async () => { if (originalFailure) throw new Error("original terminal failure"); }, {
      read: () => "{}", write: () => assert.fail("must not write invalid report"), error: message => errors.push(message),
    });
    assert.equal(exit, 2); assert.match(errors.at(-1)!, /Partial regression report failed/);
    if (originalFailure) assert.match(errors[0], /original terminal failure/);
  }
});

test("lifecycle report retains native crash failure and never grants full acceptance", async () => {
 const receipt:any=evidence();receipt.imageCapture="deferred";receipt.regressionScenario="lifecycle";
 receipt.cases[7].status="failed";receipt.cases[7].reason="native fatal cmd SIGSEGV";receipt.cases[7].subchecks=[{id:"agent-lifecycle-and-keyboard-exclusion",status:"failed",reason:"native fatal cmd SIGSEGV",artifacts:[]}];
 const summary={evidenceType:"installed-kit-partial-regression",scenario:"lifecycle",fullAcceptance:false,publication:"forbidden",passed:false,semanticPassed:false,imageCapture:"deferred",invocationId:receipt.invocationId};
 let report="";const errors:string[]=[];
 const exit=await runRegressionReport("/run","lifecycle",async()=>{throw new Error("native fatal cmd SIGSEGV");},{read:p=>JSON.stringify(p.endsWith("regression-summary.json")?summary:receipt),write:(_p,t)=>{report=t;},error:m=>errors.push(m)},{imageCapture:"deferred"});
 assert.equal(errors.length,1,JSON.stringify(errors));assert.equal(exit,2);assert.match(report,/native fatal cmd SIGSEGV/);assert.match(report,/Full 27-case acceptance: false/);assert.equal(errors.length,1);
});
