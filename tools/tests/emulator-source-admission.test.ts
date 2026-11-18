import assert from "node:assert/strict";
import test from "node:test";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { snapshotRepository } from "../release/build-closure.ts";
import { runGate, type Invocation } from "../../release/emulator-gate.ts";
import { fixture, owningHostReply } from "./emulator-gate-fixture.ts";

test("editing a specification after closure construction rejects the full cycle before device operations", async () => {
  const c = fixture(), root = join(c.workspaceRoot, "voboost-agents");
  mkdirSync(root, { recursive: true });
  const env = { ...process.env, AISUITE_ALLOW_GIT: "1", GIT_AUTHOR_NAME: "Fixture", GIT_AUTHOR_EMAIL: "fixture@example.invalid", GIT_COMMITTER_NAME: "Fixture", GIT_COMMITTER_EMAIL: "fixture@example.invalid", GIT_AUTHOR_DATE: "2026-10-04T20:53:00+03:00", GIT_COMMITTER_DATE: "2026-10-04T20:53:00+03:00" };
  const git = (args: string[], input?: string) => execFileSync("git", ["-C", root, ...args], { env, encoding: "utf8", input }).trim();
  git(["init", "-q"]);
  const tree = git(["mktree"], "");
  const commit = git(["commit-tree", tree], "fixture\n");
  git(["update-ref", "HEAD", commit]);
  writeFileSync(join(root, "gradlew"), "host wrapper fixture\n");
  writeFileSync(join(root, "spec.md"), "original contract\n");
  const sources = [await snapshotRepository("agents", root)];
  const catalog = readFileSync(join(c.closure, "agents.json"));
  writeFileSync(join(dirname(c.closure), "receipt.json"), JSON.stringify({ schemaVersion: 1, kind: "source-build", artifacts: { "agents.json": { sha256: createHash("sha256").update(catalog).digest("hex") } }, sources }));
  writeFileSync(join(root, "spec.md"), "updated contract after construction\n");
  const calls: Invocation[] = [];
  const result = await runGate(c, async invocation => {
    calls.push(invocation);
    assert.equal(invocation.args[0], "--verify-package");
    return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
  });
  assert.equal(result.passed, false);
  assert.match(result.preparation.reason, /owning host source snapshot differs/);
  assert.equal(calls.length, 1);
  assert.ok(!calls.some(call => ["--install", "--uninstall", "--emulator-check"].includes(call.args[0])));
});

for (const failure of ["missing-receipt", "nonzero", "timeout", "source-change"] as const) test(`public dispatcher fails closed on owning host ${failure} before device admission`, async () => {
  const c = fixture(), calls: Invocation[] = [];
  if (failure === "missing-receipt") rmSync(join(dirname(c.closure), "receipt.json"));
  const result = await runGate(c, async invocation => {
    calls.push(invocation);
    if (invocation.args[0] === "--verify-package") return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    assert.equal(invocation.host, "agents", "no device action before successful owning host prerequisites");
    const completed = owningHostReply(c, invocation)!;
    if (failure === "source-change") writeFileSync(join(c.workspaceRoot, "voboost-agents/src/main/java/Policy.java"), "changed during owning tests\n");
    return { ...completed, exitCode: failure === "nonzero" ? 1 : 0, timedOut: failure === "timeout" };
  });
  assert.equal(result.passed, false);
  assert.match(result.preparation.reason, failure === "missing-receipt" ? /source-build receipt/ : failure === "source-change" ? /source changed during tests/ : /host gates did not complete/);
  assert.ok(result.cases.every((row: any) => row.status === "not-run"));
  assert.equal(calls.length, failure === "missing-receipt" ? 1 : 2);
});
