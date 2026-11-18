import test from "node:test";
import assert from "node:assert/strict";
import { mkdirSync, writeFileSync, readFileSync, chmodSync, rmSync, symlinkSync, realpathSync } from "node:fs";
import { resolve, join } from "node:path";
import { randomUUID } from "node:crypto";
import { createHash } from "node:crypto";
import { stripTypeScriptTypes } from "node:module";
import { NativeSession, invocation, nativeReadTransportTimeout } from "../emulator/native-session.ts";
import { spawnAdapter, voyemuInvocation, retainTimedOutDeviceOperation } from "../../release/emulator-gate.ts";

async function fixture(mutate?: (inv: any, root: string) => void, deadline?: "boot" | "snapshot" | "both") {
  const root = join(realpathSync("."), "native-dispatch-" + randomUUID());
  const executable = join(root, ".local/native-cpp/bin/voyemu");
  mkdirSync(join(root, ".local/native-cpp/bin"), { recursive: true, mode: 0o700 });
  const script = `#!${process.execPath}\nimport fs from 'node:fs';const q=JSON.parse(fs.readFileSync(process.argv[6],'utf8'));console.log(JSON.stringify({schema:2,request_id:q.request_id,operation_id:'c'.repeat(32),ok:true,elapsed_ms:1,result:{ok:true,session_id:q.session.id,android_pid:123,stopped:false,deliveries:{android:{pid:123,serial:'emulator-5554',quickboot:{mode:'restored'}}}}}));\n`;
  writeFileSync(executable, script, { mode: 0o700 });
  const config = { executable, root, profile: "free-09", session: { id: "a".repeat(32), ownership_id: "b".repeat(32) } };
  const adapter = async (inv: any) => {
    mutate?.(inv, root);
    return spawnAdapter(inv);
  };
  let run = adapter;
  if (deadline) {
    const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
    const block = source.slice(source.indexOf("const record = async"), source.indexOf("const immutable =", source.indexOf("const record = async")));
    const output = join(root, "evidence"); mkdirSync(output);
    const evidence = { actions: [], cases: [{ number: 12, actions: [] }], activeOperation: null };
    const deps = { retainTimedOutDeviceOperation, nativeReadTransportTimeout, settingsNavigationDeadline: null, deferred: false, firstBootCaptureDeadline: deadline === "snapshot" ? null : performance.now() + 10000, snapshotReadbackDeadline: deadline === "boot" ? null : performance.now() + 9000, performance, Unavailable: Error, bootBudgetPhase: "capture", evidence, caseNumber: 12, nativeConfig: config, regular: () => {}, readFileSync, join, c: { output }, writeFileSync, digest: (bytes: any) => createHash("sha256").update(bytes).digest("hex"), regression: null, assertRegressionInvocation: () => {}, adapter, MAX_CAPTURE: 1048576, serial: "emulator-5554", persist: () => {} };
    run = new Function(...Object.keys(deps), stripTypeScriptTypes(block) + "return record;")(...Object.values(deps));
  }
  const client = new NativeSession(config, join(root, "requests"), run, () => ({ width: 1920, height: 720 }));
  try { return await client.status(); } finally { rmSync(root, { recursive: true, force: true }); }
}

test("actual NativeSession.status dispatches installed fixture through production spawnAdapter", async () => {
  assert.equal((await fixture()).serial, "emulator-5554");
});

test("runtime native operation allowlist rejects commands outside owned read contract", () => {
  assert.throws(() => invocation({ executable: "/root/.local/native-cpp/bin/voyemu", root: "/root", profile: "free-09", session: { id: "a".repeat(32), ownership_id: "b".repeat(32) } }, "control.stop" as any, "/root/private/request.json"), /outside session contract/);
});

for (const deadline of ["boot", "snapshot", "both"] as const) test(`actual production record preserves native dispatch with ${deadline} deadline`, async () => {
  assert.equal((await fixture(undefined, deadline)).serial, "emulator-5554");
});

for (const [name, mutate] of Object.entries({
  "wrong executable": (i: any) => { i.executable = process.execPath + "-foreign"; },
  "Node executable escape": (i: any) => { i.executable = process.execPath; },
  "timeout extension": (i: any) => { i.timeoutMs = 15001; },
  "invalid timeout": (i: any) => { i.timeoutMs = Number.NaN; },
  "wrong root": (i: any) => { i.args[2] += "-foreign"; },
  "wrong request": (i: any) => { i.args[4] += "-foreign"; },
  "extra argument": (i: any) => { i.args.push("--extra"); },
  "changed operation": (i: any) => { const q = JSON.parse(readFileSync(i.args[4], "utf8")); q.op = "control.stop"; writeFileSync(i.args[4], JSON.stringify(q)); },
  "changed profile": (i: any) => { const q = JSON.parse(readFileSync(i.args[4], "utf8")); q.profile = "foreign"; writeFileSync(i.args[4], JSON.stringify(q)); },
  "changed session": (i: any) => { const q = JSON.parse(readFileSync(i.args[4], "utf8")); q.session.id = "f".repeat(32); writeFileSync(i.args[4], JSON.stringify(q)); },
  "public request": (i: any) => { chmodSync(i.args[4], 0o644); },
  "public directory": (i: any, root: string) => { chmodSync(join(root, "requests"), 0o755); },
  "oversized request": (i: any) => { writeFileSync(i.args[4], " ".repeat(65537)); },
  "symlink request": (i: any, root: string) => { const body = readFileSync(i.args[4]); rmSync(i.args[4]); const other = join(root, "other"); writeFileSync(other, body, { mode: 0o600 }); symlinkSync(other, i.args[4]); },
  "changed executable": (_i: any, root: string) => { writeFileSync(join(root, ".local/native-cpp/bin/voyemu"), "changed"); },
})) test(`production dispatcher rejects ${name} before spawn`, async () => {
  await assert.rejects(() => fixture(mutate), /only packaged installer or official Voyemu allowed/);
});

test("native-looking arbitrary executable receives no admission", async () => {
  await assert.rejects(() => spawnAdapter({ executable: "/untrusted/.local/native-cpp/bin/voyemu", args: ["test", "--root", "/untrusted", "--request", "/private/request.json"], timeoutMs: 100, voyemu: true }), /only packaged installer or official Voyemu allowed/);
});

test("official legacy Node status and verify argv remain dispatchable", async () => {
  const root = join(realpathSync("."), "legacy-dispatch-" + randomUUID());
  mkdirSync(join(root, "src"), { recursive: true });
  writeFileSync(join(root, "src/cli.ts"), "console.log('legacy-official-fixture');\n");
  try {
    for (const command of ["status", "verify"] as const) {
      const result = await spawnAdapter(voyemuInvocation({ voyemuRoot: root, voyemuProfile: "free-09" } as any, command));
      assert.equal(result.exitCode, 0);
      assert.equal(result.stdout, "legacy-official-fixture\n");
    }
  } finally { rmSync(root, { recursive: true }); }
});
