import assert from "node:assert/strict";
import test, { mock } from "node:test";
import childProcess from "node:child_process";
import { syncBuiltinESMExports } from "node:module";
import fs from "node:fs";
import path from "node:path";
import { InstallerUi } from "./installer-ui.ts";
import { main as launcher } from "./launcher-runtime.ts";
import { main as phone } from "./phone-runtime.ts";
import { main as outing } from "./outing-runtime.ts";
import { main as i18n } from "./i18n-qnx-runtime.ts";

const session = { id: "a".repeat(32), ownership_id: "b".repeat(32) };
const serials = ["emulator-5554", "emulator-5588"];
function directory() {
  const parent = path.resolve(import.meta.dirname, "../../build/unit-fixtures");
  fs.mkdirSync(parent, { recursive: true });
  return fs.mkdtempSync(path.join(parent, "installer-device-"));
}

test("runtime drivers forward the selected device with only the canonical flag", async () => {
  const root = directory(), previousExitCode = process.exitCode;
  const calls: string[][] = [];
  let selected = "";
  const spawn = mock.method(childProcess, "spawnSync", (executable: string, args: string[]) => {
    if (executable === path.join(root, "installer")) {
      calls.push(args);
      return { status: 0, stdout: JSON.stringify({ schema: 2, source: "voboost", status: "unavailable" }), stderr: "" };
    }
    assert.equal(executable, path.join(root, "voyemu"));
    assert.deepEqual(args.slice(0, 3), ["test", "--root", root]);
    const request = JSON.parse(fs.readFileSync(args[4], "utf8"));
    assert.equal(request.op, "session.status");
    const result = { ok: true, session_id: session.id, android_pid: 123, stopped: false,
      deliveries: { android: { pid: 123, serial: selected, quickboot: { mode: "restored" } } } };
    return { status: 0, stdout: JSON.stringify({ schema: 2, request_id: request.request_id,
      operation_id: "c".repeat(32), ok: true, elapsed_ms: 1, result }), stderr: "" };
  });
  const log = mock.method(console, "log", () => {});
  syncBuiltinESMExports();
  try {
    fs.mkdirSync(path.join(root, ".local/build/tmp"), { recursive: true });
    for (const serial of serials) for (const [name, run] of Object.entries({ launcher, phone, outing, i18n })) {
      selected = serial;
      const expected = { session, serial, boot: "11111111-1111-1111-1111-111111111111",
        generation: "1".repeat(64), catalog: "2".repeat(64), androidBoot: "11111111-1111-1111-1111-111111111111",
        androidGeneration: "1".repeat(64), qnxBoot: "qnx-boot", qnxGeneration: "3".repeat(64), catalogSha256: "2".repeat(64) };
      const config = { evidence: path.join(root, name + "-" + serial), installer: path.join(root, "installer"),
        cli: path.join(root, "voyemu"), voyemuRoot: root, native: { executable: path.join(root, "voyemu"), root, profile: "fixture", session },
        fixtures: { user: { label: "Host user", identity: "i." + "1".repeat(64) }, stockFolder: { label: "Host original", identity: "o." + "2".repeat(64) } },
        profile: "fixture", session, serial, boot: expected.boot, generation: expected.generation, expected,
        logIdentity: { serial, version: "1.0.0-beta1", manifestSha256: "4".repeat(64) } };
      const input = path.join(root, name + "-" + serial + ".json");
      fs.writeFileSync(input, JSON.stringify(config));
      const before = calls.length;
      await run(input);
      assert.equal(calls.length, before + 1, name + " must reach official observation");
      assert.deepEqual(calls.at(-1), ["--observe", "--device", serial], name);
    }
  } finally {
    spawn.mock.restore(); log.mock.restore(); syncBuiltinESMExports();
    process.exitCode = previousExitCode;
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("Installer UI preserves exact emulator identity and rejects USB test actions", async () => {
  const root = directory();
  try {
    for (const serial of serials) {
      let foreign = false;
      const ui = new InstallerUi("/packaged/voboost-installer", serial, path.join(root, serial), async invocation => {
        assert.equal(invocation.executable.endsWith("/emulator-driver"), true);
        assert.deepEqual(invocation.args.slice(2), ["--device", serial, "--candidate", "/packaged/voboost-installer"]);
        assert.equal(invocation.args.length, 6);
        assert.equal(invocation.args[0], "--emulator-check");
        assert.deepEqual(JSON.parse(fs.readFileSync(invocation.args[1], "utf8")), { action: "boot-observation" });
        return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: foreign ? "foreign" : serial, body: "host protocol fixture" }), stderr: "" };
      }, async () => {});
      assert.equal((await ui.call({ action: "boot-observation" })).serial, serial);
      foreign = true;
      await assert.rejects(ui.call({ action: "boot-observation" }), /result identity differs/);
    }
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
