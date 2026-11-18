import test from "node:test";
import assert from "node:assert/strict";
import { evidence } from "./gate-report-fixture.ts";
import { runReport } from "./run-report.ts";
for (const [status, exit] of [["passed", 1], ["failed", 1], ["unavailable", 1]] as const) {
  test(`existing runner terminal ${status} writes report and preserves exit ${exit}`, () => {
    const receipt = evidence();
    Object.assign(receipt.cases[0], { status, reason: "fixture terminal observation", subchecks: [{ id: "terminal", status, reason: "fixture", artifacts: [] }] });
    const files = new Map([["/config.json", JSON.stringify({ output: "/run" })]]);
    let calls = 0;
    const code = runReport("/config.json", config => {
      assert.equal(config, "/config.json"); calls++;
      files.set("/run/emulator-runtime-evidence.json", JSON.stringify(receipt));
      return exit;
    }, { read: path => { assert.ok(files.has(path)); return files.get(path)!; }, write: (path, content) => { files.set(path, content); }, error: message => assert.fail(message) });
    assert.equal(code, exit); assert.equal(calls, 1);
    assert.match(files.get("/run/report.md")!, new RegExp(`cli-install-first \\| ${status}`));
    assert.match(files.get("/run/report.md")!, /Recorded overall result: not passed/);
  });
}
test("report failures stay explicit and nonzero without hiding original failure", () => {
  for (const gateExit of [0, 1, 2]) {
    const errors: string[] = [];
    const code = runReport("/config.json", () => gateExit, { read: path => path === "/config.json" ? '{"output":"/run"}' : '{"schemaVersion":1}', write: () => assert.fail("invalid report must not write"), error: message => errors.push(message) });
    assert.equal(code, gateExit || 2);
    assert.match(errors[0], /Emulator report failed.*invalid schema3/);
    assert.match(errors[0], new RegExp(`gate exit code ${gateExit}`));
  }
});

test("zero exit with partial or failed acceptance writes report then fails closed", () => {
  for (const status of ["not-run", "failed"]) {
    const receipt = evidence();
    if (status === "failed") Object.assign(receipt.cases[0], { status, subchecks: [{ id: "terminal", status, reason: "actual failure", artifacts: [] }] });
    const errors: string[] = []; let report = "";
    const code = runReport("/config.json", () => 0, { read: path => path === "/config.json" ? JSON.stringify({ output: "/run" }) : JSON.stringify(receipt), write: (_path, content) => { report = content; }, error: message => errors.push(message) });
    assert.equal(code, 2); assert.match(report, /Recorded overall result: not passed/);
    assert.match(errors[0], /exit code 0 contradicts retained evidence passed=false/);
  }
});
test("nonzero original gate exits survive valid partial evidence", () => {
  for (const exit of [1, 2, 7]) {
    let written = false;
    assert.equal(runReport("/config.json", () => exit, { read: path => path === "/config.json" ? JSON.stringify({ output: "/run" }) : JSON.stringify(evidence()), write: () => { written = true; }, error: message => assert.fail(message) }), exit);
    assert.equal(written, true);
  }
});
