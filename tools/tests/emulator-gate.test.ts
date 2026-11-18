import {isDriverInvocation} from "../emulator/driver.ts";
import {agentScrollDirection} from "../../release/emulator-gate.ts";
import { systemTboxRecoveryTarget } from "../../release/emulator-gate.ts";
import { nativeTree as systemNativeTree } from "../emulator/native-tree.ts";
import { nativePointerRequest as systemNativePointer } from "../emulator/installer-ui.ts";
import { rmSync as removeSystemFixture } from "node:fs";
import { hiddenVoboostMenuScroll } from "../../release/emulator-gate.ts";
import { seekTboxRecoveryTarget } from "../../release/emulator-gate.ts";
import { caseFailureFrames, tapDrawerSwitch, drawerModeCard, tapDrawerModeCard, prepareDrawerHome, awaitDrawerPowerCard } from "../../release/emulator-gate.ts";
import { NativeInputRejected } from "../emulator/native-session.ts";
import { windowModel } from "../emulator/window-fixture.ts";
import assert from "node:assert/strict";
import test, { mock } from "node:test";
import { stripTypeScriptTypes } from "node:module";
import { createHash } from "node:crypto";
import { installerLog } from "./installer-diagnostic-fixture.ts";
import {
  mkdtempSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  symlinkSync,
  chmodSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Ajv2020 from "ajv/dist/2020.js";
import {
  vehicleRootTarget,
  secondScreenStarting,
  captureFirstBoot,
  UiWarmupDeadline,
  uiWarmupDeadline,
  installAcceptanceArmed,
  installAcceptanceAction,
  installAcceptanceReceipt,
  settingsTab,
  selectedSettingsTab,
  parseBootObservation,
  captureBootObservation,
  CASES,
  EXECUTION_ORDER,
  validateConfig,
  normalAbsence,
  preparationAbsence,
  observerArmed,
  jsonReceipt,
  spawnAdapter,
  resultOk,
  runGate,
  verifyVoyemu,
  prepared,
  keyboardKey,
  emptyEditor,
  launcherItems,
  noneProcesses,
  ObservationPending,
  installerBootObservation,
  installOperation,
  offlineObservation,
  installerRootPending,
  installerClosedPending,
  nativeBootReadback,
  nativeBootReady,
  functionalEvidence,
  fixtureOwned,
  agentToggleInput,
  startupProfiles,
  startupReboot,
  type GateConfig,
  type Invocation,
  type ProcessResult,
} from "../../release/emulator-gate.ts";
import { InstallerUi, validateRequest, parseUi, validatePng, uniqueNode } from "../emulator/installer-ui.ts";

const hash = (v: string | Buffer) => createHash("sha256").update(v).digest("hex");
test("capture failure distinguishes native installer exit from timeout and sanitizes bounded diagnostics", () => {
  const failure = { exitCode: 1, stdout: "stdout-secret", stderr: "Install failed: install acceptance requires root Enforcing Android QEMU\n--token top-secret Authorization: Bearer auth-secret password=pass-secret https://user:url-secret@example.test/\n" + "x".repeat(2048) };
  assert.throws(() => resultOk(failure), (error: Error) => {
    assert.match(error.message, /exitCode=1 signal=none timedOut=false overflow=false/);
    assert.match(error.message, /Install failed: install acceptance requires root Enforcing Android QEMU/);
    assert.doesNotMatch(error.message, /stdout-secret|top-secret|auth-secret|pass-secret|url-secret/);
    assert.ok(error.message.length < 650);
    return true;
  });
  for (const [changed, expected] of [
    [{ exitCode: null, timedOut: true }, /exitCode=null signal=none timedOut=true/],
    [{ signal: "SIGTERM" }, /signal=SIGTERM/],
    [{ overflow: true }, /overflow=true/],
    [{ retained: { overflow: true } }, /overflow=true/],
    [{ exitCode: null }, /exitCode=null/],
  ] as const) assert.throws(() => resultOk({ exitCode: 0, stdout: "", stderr: "", ...changed }), expected);
  assert.doesNotThrow(() => resultOk({ exitCode: 0, stdout: "", stderr: "ordinary output" }));
});
import { owningHostReply as baseOwningHostReply, fixture, reply, boot, png } from "./emulator-gate-fixture.ts";
// Public dispatcher fixtures model freshly executed Gradle report producers.
// Shared helper retains command/filter assertions and fresh JUnit/source-copy setup.
function owningHostReply(...args: Parameters<typeof baseOwningHostReply>): ReturnType<typeof baseOwningHostReply> {
  const result = baseOwningHostReply(...args);
  return result?.exitCode === 0 ? { ...result, stdout: "> Task :generateSettingsCopyEvidence\n> Task :test\nBUILD SUCCESSFUL\n" } : result;
}

const absence = () => ({
  transactionId: "a".repeat(64),
  bootId: boot,
  checks: [
    "root",
    "app-data",
    "init-file",
    "package",
    "app-op",
    "mounts",
    "processes",
    "init-services",
    "policy",
  ],
});
const xml =
  '<hierarchy><node package="com.qinggan.app.setting" class="android.widget.FrameLayout" bounds="[0,0][1920,720]"><node package="com.qinggan.app.setting" text="Voboost" clickable="true" bounds="[175,50][443,150]" /></node></hierarchy>';

test("functional inventory preserves all IDs and executes critical vehicle and launcher cases before locales", () => {
  assert.equal(CASES.length, 27);
  assert.deepEqual(
    CASES.slice(0, 17).map((x) => x.id),
    [
      "cli-install-first",
      "cli-uninstall",
      "cli-absence",
      "cli-install-second",
      "settings-tab",
      "language-opposite",
      "language-return",
      "agent-enablement-conflict",
      "font-cyrillic",
      "keyboard-en",
      "keyboard-ru-en",
      "launcher-persistence",
      "forced-ev-persistence",
      "pedestrian-warning-persistence",
      "hidden-tbox-recovery",
      "none-no-processes",
      "settings-uninstall",
    ],
  );
  assert.equal(new Set(CASES.map((x) => x.id)).size, 27);
  assert.equal(new Set(EXECUTION_ORDER).size, 27);
  assert.deepEqual(
    EXECUTION_ORDER.filter((n) => n <= 17),
    [1, 2, 3, 4, 5, 13, 14, 12, 6, 7, 8, 9, 10, 11, 15, 16, 17],
  );
  assert.ok(EXECUTION_ORDER.indexOf(25) < EXECUTION_ORDER.indexOf(17));
  assert.ok(EXECUTION_ORDER.indexOf(26) > EXECUTION_ORDER.indexOf(17));
});
test("intermediate boot observation retries expected offline and Permissive before exact completed boundary", async () => {
  const states = [new ObservationPending("Permissive"), new ObservationPending("offline"), { boot: "before", bootCompleted: true, rebootCount: 0 }, { boot: "after", bootCompleted: false, rebootCount: 1 }, { boot: "after", bootCompleted: true, rebootCount: 1 }];
  let reads = 0, pauses = 0;
  const result = await installerBootObservation(1, "before", async () => { const state = states[reads++]; if (state instanceof Error) throw state; return state; }, async () => { pauses++; });
  assert.equal(result.boot, "after"); assert.equal(reads, 5); assert.equal(pauses, 4);
});
test("intermediate boot observation never borrows a later boot or retries foreign identity", async () => {
  let reads = 0;
  await assert.rejects(() => installerBootObservation(1, "before", async () => { reads++; return { boot: "later", bootCompleted: true, rebootCount: 2 }; }, async () => {}), /later boot cannot substitute/);
  assert.equal(reads, 1); reads = 0;
  await assert.rejects(() => installerBootObservation(1, "before", async () => { reads++; throw new Error("runtime lease identity changed"); }, async () => {}), /runtime lease/);
  assert.equal(reads, 1);
  await assert.rejects(() => installerBootObservation(1, "before", async () => { throw new ObservationPending("offline"); }, async () => {}), /never exposed/);
});
test("intermediate observer retains its actual incomplete identity without substituting the later boot", async () => {
  const first = { boot: "actual-first", bootCompleted: false, rebootCount: 1 }, later = { boot: "actual-second", bootCompleted: true, rebootCount: 2 }, retained: typeof first[] = [];
  let count = 0;
  await assert.rejects(() => installerBootObservation(1, "before", async () => count++ === 0 ? first : later, async () => {}, (observed) => retained.push(observed)), /later boot cannot substitute/);
  assert.deepEqual(retained, [first]);
  assert.equal(retained[0].bootCompleted, false, "an actual identity does not claim completed boot proof");
});
test("actual closed Inspect transition retries only a completed bounded transport failure", () => {
  const result = { exitCode: 1, stdout: "", stderr: readFileSync(new URL("../../testdata/emulator-cycle64/inspect-closed-stderr.txt", import.meta.url), "utf8") };
  assert.equal(offlineObservation(result), true);
  assert.equal(offlineObservation({ ...result, stderr: "Emulator acceptance failed: emulator operation failed (Some(1)): adb: device offline\n" }), true);
  for (const changed of [{ ...result, timedOut: true }, { ...result, overflow: true }, { ...result, signal: "SIGTERM" }, { ...result, exitCode: null }, { ...result, exitCode: 0 }, { ...result, stderr: "root read failed: permission denied" }, { ...result, stderr: "runtime lease identity changed" }]) assert.equal(offlineObservation(changed), false);
});
test("actual closed boot capture retries only terminal bounded transport loss", () => {
  const result = { exitCode: 1, stdout: "", stderr: readFileSync(new URL("../../testdata/emulator-cycle79/boot-capture-closed-stderr.txt", import.meta.url), "utf8") };
  assert.equal(offlineObservation(result), true);
  for (const changed of [
    { ...result, exitCode: 0 }, { ...result, exitCode: null },
    { ...result, timedOut: true }, { ...result, signal: "SIGTERM" },
    { ...result, overflow: true }, { ...result, retained: { overflow: true } },
    { ...result, stderr: "Emulator acceptance failed: boot completion observation malformed\n" },
    { ...result, stderr: "Emulator acceptance failed: emulator capture failed: permission denied\n" },
    { ...result, stderr: "runtime lease identity changed\n" },
  ]) assert.equal(offlineObservation(changed), false);
});
test("root observation pending requires exact failure and active installer scope", () => {
  const result = { exitCode: 1, stdout: "", stderr: "boot-observation failure reason=guard-root remote_exit=73 stdout_bytes=0 stderr_bytes=36\nEmulator acceptance failed: boot observation guard rejected: root\n" };
  assert.equal(installerRootPending(result, true, "boot-observation"), true);
  assert.equal(installerRootPending(result, false, "boot-observation"), false);
  assert.equal(installerRootPending(result, true, "inspect"), false);
  assert.equal(installerRootPending(result, true, "resident-status"), false);
  for (const changed of [{ ...result, exitCode: 0 }, { ...result, exitCode: null }, { ...result, timedOut: true }, { ...result, signal: "SIGTERM" }, { ...result, overflow: true }, { ...result, retained: { overflow: true } }, { ...result, stderr: result.stderr + "extra\n" }, { ...result, stderr: result.stderr.replace("guard-root", "guard-identity") }, { ...result, stderr: result.stderr.replace("remote_exit=73", "remote_exit=1") }, { ...result, stdout: JSON.stringify({ schemaVersion: 1 }) + "\n" }]) assert.equal(installerRootPending(changed, true, "boot-observation"), false);
});
test("functional config rejects caller scripts coordinates passed flags and foreign fixture paths", () => {
  const c = fixture();
  assert.deepEqual(validateConfig(c), c);
  for (const key of ["steps", "script", "shell", "coordinates", "passed", "expectedChecks"])
    assert.throws(() => validateConfig({ ...c, [key]: true }), /unknown/);
  writeFileSync(join(dirname(c.output), "foreign.apk"), "foreign");
  assert.throws(
    () => validateConfig({ ...c, fixture: join(dirname(c.output), "foreign.apk") }),
    /root-built first-party/,
  );
});
function dirname(p: string) {
  return p.slice(0, p.lastIndexOf("/"));
}
test("prepared exact inventory rejects partial duplicate unexpected expected mismatch and missing actual", () => {
  const c = fixture(),
    checks = prepared(c).checks;
  verifyVoyemu(reply(c, "verify", { checks }), c.voyemuProfile, checks);
  for (const changed of [
    checks.slice(1),
    [...checks, checks[0]],
    [...checks, { id: "foreign", passed: true, expected: null, actual: null }],
    checks.map((r, i) => (i === 0 ? { ...r, expected: "wrong" } : r)),
    checks.map((r, i) => (i === 0 ? { id: r.id, passed: true, expected: r.expected } : r)),
    checks.map((r, i) => (i === 0 ? { ...r, actual: "wrong" } : r)),
    checks.map((r, i) => (i === 0 ? { ...r, forged: true } : r)),
  ])
    assert.throws(() =>
      verifyVoyemu(reply(c, "verify", { checks: changed }), c.voyemuProfile, checks),
    );
  const complex = [...checks, { id: "routing:main:Bar", passed: true, expected: 0, actual: [0] }];
  verifyVoyemu(reply(c, "verify", { checks: complex }), c.voyemuProfile, complex);
});
test("normal removal rejects emergency incomplete absence and unsuccessful process receipts", () => {
  const r = {
    exitCode: 0,
    stdout: JSON.stringify(absence()) + "\n",
    stderr: "INFO ordinary progress",
  };
  assert.equal(normalAbsence(r).bootId, boot);
  for (const altered of [
    { ...r, stdout: JSON.stringify({ ...absence(), state: "emergency-removed" }) },
    { ...r, stdout: JSON.stringify({ ...absence(), checks: ["root"] }) },
    { ...r, exitCode: 1 },
    { ...r, timedOut: true },
  ])
    assert.throws(() => normalAbsence(altered));
});
test("observer armed event accepts only an exact marker line amid ordinary diagnostics", () => {
  assert.equal(
    observerArmed("warning\nsettings-uninstall-observer-armed-v1\nmore diagnostics\n"),
    true,
  );
  for (const s of [
    "armed",
    "INFO settings-uninstall-observer-armed-v1",
    "settings-uninstall-observer-armed-v1-extra",
    "not settings-uninstall-observer-armed-v1",
    "settings-uninstall-observer-armed-v1",
  ])
    assert.equal(observerArmed(s), false);
});
test("closed UI requests reject shell arbitrary APK SHA keys and out of range coordinates", () => {
  for (const request of [
    { action: "shell", value: "x" },
    { action: "inspect", shell: "x" },
    { action: "tap", x: 4096, y: 1 },
    { action: "key", key: "unknown" },
    { action: "text", value: "Москва" },
    { action: "install-fixture", apk: "/tmp/f.apk", sha256: "0".repeat(64) },
    { action: "remove-fixture", package: "foreign" },
  ])
    assert.throws(() => validateRequest(request));
  validateRequest({ action: "tap", x: 200, y: 50, expectedWindow: { id: 1, windowType: 1, title: "", bounds: "[0,0][400,400]", packageName: "com.qinggan.app.setting", captureEndUptimeMs: "1000", displayId: 0 }, expectedTarget: { windowId: 1, windowType: 1, windowTitle: "", windowBounds: "[0,0][400,400]", rootPackage: "com.qinggan.app.setting", className: "android.widget.FrameLayout", packageName: "com.qinggan.app.setting", bounds: "[0,0][400,400]", resource: "", text: "" } });
  assert.throws(() => jsonReceipt("{}\n{}\n"));
  assert.deepEqual(jsonReceipt('INFO progress\n{"value":1}\n'), { value: 1 });
});
test("fresh semantic UI nodes reject stale foreign snapshots before sending a tap", async () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-ui-fresh-"));
  const calls: Invocation[] = [];
  const ui = new InstallerUi(
    "/host/voboost-installer",
    "emulator-5562",
    join(root, "ui"),
    async (inv) => {
      calls.push(inv);
      const req = JSON.parse(readFileSync(inv.args[1], "utf8"));
      return {
        exitCode: 0,
        stdout:
          JSON.stringify({
            schemaVersion: 1,
            serial: "emulator-5562",
            body: req.action === "snapshot" ? xml : "",
          }) + "\n",
        stderr: "",
      };
    },
    async () => {},
  );
  const nodes = await ui.snapshot();
  assert.equal(nodes[1].packageName, "com.qinggan.app.setting");
  await ui.snapshot();
  const before = calls.length;
  await assert.rejects(() => ui.tap(nodes[1]), /stale/);
  assert.equal(calls.length, before);
  await assert.rejects(() => ui.tap({ ...nodes[1] }), /stale/);
});
for (const timedOut of [true, false]) test(`public dispatcher reports installer ${timedOut ? "timeout and retains lease" : "native nonzero exit cause"}`, async () => {
  const c = fixture(),
    calls: Invocation[] = [];
  let bootReads = 0;
  const e = await runGate(c, async (inv) => {
    if (inv.host) return owningHostReply(c, inv)!;
    calls.push(inv);
    if (inv.voyemu) {
      const command = inv.args[4];
      return reply(
        c,
        command,
        command === "status"
          ? {
              profiles: {
                [c.voyemuProfile]: { prepared: true, running: true, port: 5562, pid: 333 },
              },
            }
          : { checks: prepared(c).checks },
      );
    }
    if (inv.args[0] === "--verify-package")
      return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    if (inv.args[0] === "--status")
      return {
        exitCode: 0,
        stdout:
          '{"route":"absent","deviceSerial":"emulator-5562","installerVersion":"1.0.0-beta1"}\n',
        stderr: "",
      };
    if (inv.args[0] === "--uninstall")
      return { exitCode: 0, stdout: JSON.stringify(absence()) + "\n", stderr: "" };
    if (inv.args[0] === "--install")
      return timedOut
        ? { exitCode: null, stdout: "progress\n", stderr: "", timedOut: true, processId: 777 }
        : { exitCode: 1, stdout: "", stderr: "Install failed: install acceptance requires root Enforcing Android QEMU\n--token dispatcher-secret", processId: 777 };
    if (inv.args[0] === "--emulator-check") {
      bootReads++;
      const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
      if (request.action === "screenshot") {
        writeFileSync(request.output, png());
        return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", png: request.output }) + "\n", stderr: "" };
      }
      return {
        exitCode: 0,
        stdout:
          JSON.stringify({
            schemaVersion: 1,
            serial: "emulator-5562",
            body: request.action === "snapshot" ? xml : request.action === "boot-observation" ? `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n` : `bootId=${boot}\nenforcing=Enforcing\nbootCompleted=1\n`,
          }) + "\n",
        stderr: "",
      };
    }
    return { exitCode: 1, stdout: "", stderr: "diagnostics unavailable" };
  });
  assert.equal(e.passed, false);
  if (timedOut) {
    assert.equal(e.activeOperation.processId, 777);
    assert.equal(e.leaseStatus, "retained");
    assert.match(e.cases[0].reason, /exitCode=null signal=none timedOut=true/);
  } else {
    assert.match(e.cases[0].reason, /exitCode=1 signal=none timedOut=false/);
    assert.match(e.cases[0].reason, /Install failed: install acceptance requires root Enforcing Android QEMU/);
    assert.doesNotMatch(e.cases[0].reason, /dispatcher-secret/);
    assert.equal(e.activeOperation, null);
  }
  assert.equal(e.cases[0].status, "failed");
  assert.equal(e.cases[1].status, "not-run");
  assert.ok(bootReads > 0);
  assert.ok(
    calls.every((inv) =>
      inv.voyemu
        ? inv.executable === process.execPath
        : (inv.executable.endsWith("/voboost-installer") || isDriverInvocation(inv)),
    ),
  );
  assert.ok(
    !calls.some(
      (inv) =>
        inv.args.includes("kill") ||
        inv.args.includes("shell") ||
        ["start", "stop"].includes(inv.args[4]),
    ),
  );
  const schema = JSON.parse(
    readFileSync(
      new URL("../../release/emulator-runtime-evidence.schema.json", import.meta.url),
      "utf8",
    ),
  );
  const validate = new Ajv2020({ strict: true, strictTypes: false, strictTuples: false }).compile(
    schema,
  );
  assert.equal(validate(e), true, JSON.stringify(validate.errors));
});
test("dispatcher joins failed installer monitor without navigation or acceptance", async () => {
  const c = fixture();
  const sha = createHash("sha256").update(readFileSync(join(c.closure, "voboost.json"))).digest("hex");
  const after = "11111111-1111-4111-8111-111111111111";
  let started = false, failed = false, verifyEntered = false;
  let finishInstall: (result: any) => void = () => {};
  const postStartActions: string[] = [];
  const e = await runGate(c, async inv => {
    if (inv.host) return owningHostReply(c, inv)!;
    if (inv.voyemu) {
      const command = inv.args[4];
      if (command === "verify" && started && !failed) {
        verifyEntered = true;
        failed = true;
        finishInstall({ exitCode: 1, stdout: "", stderr: "Install failed: private install directory changed /data/voboost" });
        // Let the install child terminal continuation race with in-flight verification.
        await new Promise(resolve => setImmediate(resolve));
      }
      return reply(c, command, command === "status" ? { profiles: { [c.voyemuProfile]: { prepared: true, running: true, port: 5562, pid: 333 } } } : { checks: prepared(c).checks });
    }
    if (inv.args[0] === "--verify-package") return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    if (inv.args[0] === "--status") return { exitCode: 0, stdout: JSON.stringify({route:"absent",deviceSerial:"emulator-5562",installerVersion:"1.0.0-beta1"}) + "\n", stderr:"" };
    if (inv.args[0] === "--uninstall") return {exitCode:0,stdout:JSON.stringify(absence()) + "\n",stderr:""};
    if (inv.args[0] === "--install") {
      started = true;
      const pending = new Promise<any>(resolve => { finishInstall = resolve; });
      inv.onOutput!("stdout", `install-acceptance-armed ${JSON.stringify({schemaVersion:1,transactionId:sha,manifestSha256:sha,attemptToken:"a".repeat(64)})}\nRestarting the head unit (reboot 1)\n`);
      return pending;
    }
    if (inv.args[0] === "--emulator-check") {
      const r = JSON.parse(readFileSync(inv.args[1], "utf8"));
      if (started) postStartActions.push(r.action);
      if (r.action === "screenshot") { writeFileSync(r.output,png()); return {exitCode:0,stdout:JSON.stringify({schemaVersion:1,serial:"emulator-5562",png:r.output}) + "\n",stderr:""}; }
      const current = started ? after : boot;
      const body = r.action === "snapshot" ? xml : `bootCompleted=1\nbootId=${current}\nenforcing=Enforcing\noperation:\n${started ? JSON.stringify({schemaVersion:1,operation:"install",transactionId:sha,journal:`recovery/${sha}/install-journal.json`,phase:"install-agents",outcome:"running",bootId:after,rebootCount:1,statusSequence:1,journalSha256:sha,error:null,installedApk:null}) + "\n" : ""}`;
      return {exitCode:0,stdout:JSON.stringify({schemaVersion:1,serial:"emulator-5562",body}) + "\n",stderr:""};
    }
    return {exitCode:1,stdout:"",stderr:"diagnostics unavailable"};
  });
  assert.equal(verifyEntered,true, JSON.stringify({preparation:e.preparation,cases:e.cases,boots:e.boots}));
  assert.match(e.cases[0].reason,/private install directory changed/);
  assert.ok(!postStartActions.includes("open-settings"));
  assert.ok(!postStartActions.includes("key"));
  assert.ok(!postStartActions.includes("install-acceptance"));
  assert.equal(e.boots.filter((b:any)=>b.kind === "installer-2").length,0);
  const count = postStartActions.length;
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(postStartActions.length,count,"monitor must be joined before return");
});
test("functional schema rejects old phase receipts caller extras and unavailable overall pass", () => {
  const schema = JSON.parse(
    readFileSync(
      new URL("../../release/emulator-runtime-evidence.schema.json", import.meta.url),
      "utf8",
    ),
  );
  const validate = new Ajv2020({ strict: true, strictTypes: false, strictTuples: false }).compile(
    schema,
  );
  assert.equal(validate({ schemaVersion: 1, steps: [] }), false);
  const fake = {
    schemaVersion: 3,
    evidenceType: "emulator-functional",
    invocationId: "host",
    releaseIdentity: "1.0.0-beta1",
    publication: "forbidden",
    productionEvidence: false,
    vehicleEvidence: false,
    passed: false,
    identity: Object.fromEntries(
      ["package", "closure", "dmg", "machine", "cli"].map((k) => [k, "0".repeat(64)]),
    ),
    voyemuProfile: "host-profile",
    preparedCheckInventory: [{ id: "boot", expected: "1" }],
    runtime: null,
    leaseStatus: "retained",
    activeOperation: null,
    preparation: { status: "not-run", reason: "not started", kind: null, actionId: null, bootId: null, context: null, actions: [], artifacts: [] },
    fixtureCleanup: { package: "ru.voboost.tests.launcherfixture", source: "tests/emulator/agents/agent-launcher/app", owned: false, status: "not-run", reason: "not installed", originalAbsenceAction: null, installAction: null, removalAction: null, bootId: null, artifacts: [] },
    cases: CASES.map((c) => ({
      number: c.number,
      id: c.id,
      status: "unavailable",
      reason: "missing review",
      actions: [],
      subchecks: [{ id: "review", status: "unavailable", reason: "not performed", artifacts: [] }],
    })),
    actions: [],
    boots: [],
  };
  assert.equal(validate(fake), true, JSON.stringify(validate.errors));
  assert.equal(validate({ ...fake, schemaVersion: 2 }), false);
  const { preparation, ...missingPreparation } = fake;
  assert.equal(validate(missingPreparation), false);
  assert.equal(validate({ ...fake, preparation: { ...preparation, callerPassed: true } }), false);
  assert.equal(validate({ ...fake, preparation: { ...preparation, status: "passed" } }), false);
  assert.equal(validate({ ...fake, passed: true }), false);
  const { fixtureCleanup, ...missingFixture } = fake;
  assert.equal(validate(missingFixture), false);
  assert.equal(validate({ ...fake, fixtureCleanup: { ...fixtureCleanup, status: "passed" } }), false);
  assert.equal(validate({ ...fake, fixtureCleanup: { ...fixtureCleanup, package: "other.package" } }), false);
  assert.equal(validate({ ...fake, callerPassed: true }), false);
  assert.equal(validate({ ...fake, cases: fake.cases.slice(1) }), false);
  const captures = structuredClone(fake);
  captures.cases[19].subchecks = [{ id: "three-locale-panel-captures", status: "passed", reason: "claimed captures", artifacts: [] }];
  assert.equal(validate(captures), false, "capture pass needs actual actions and artifacts");
  const terminal = structuredClone(fake);
  terminal.cases[25].status = "failed";
  terminal.cases[25].subchecks = [];
  assert.equal(validate(terminal), false, "terminal case26 needs a named subcheck");
});
test("actual cycle64 semantic false capture and empty terminal proof are rejected", () => {
  const evidence = JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle64/semantic-discrepancies.json", import.meta.url), "utf8"));
  assert.throws(() => functionalEvidence(evidence), /capture pass lacks/);
  assert.throws(() => functionalEvidence({ ...evidence, cases: [evidence.cases[2]] }), /case26 lacks/);
});
test("capture consumer requires real successful same-boot PNG and hierarchy bytes for every claimed locale", () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-capture-proof-")), kit = "a".repeat(64), runtime = { pid: 1, port: 5562, serial: "emulator-5562" };
  const actions = ["screenshot", "snapshot"].map((action, index) => {
    const requestPath = join(root, `${action}.json`), artifactPath = join(root, action === "screenshot" ? "capture.png" : "snapshot-stdout.txt");
    const request = JSON.stringify(action === "screenshot" ? { action, output: artifactPath } : { action });
    const bytes = action === "screenshot" ? png() : Buffer.from(JSON.stringify({ schemaVersion: 1, serial: runtime.serial, body: xml }));
    writeFileSync(requestPath, request); writeFileSync(artifactPath, bytes);
    return { id: index + 1, caseNumber: 20, exitCode: 0, signal: null, timedOut: false, overflow: false, args: ["--emulator-check", requestPath], context: { kit, runtime, serial: runtime.serial, bootId: boot, locale: "en" }, artifacts: [{ path: requestPath, kind: "request", sha256: hash(request) }, { path: artifactPath, kind: action === "screenshot" ? "png" : "stdout", sha256: hash(bytes), boot, locale: "en", kit, runtime }] };
  });
  const check = { id: "panel-captures-en", status: "passed", artifacts: actions.map((action) => action.artifacts[1].path) };
  const evidence = { identity: { package: kit }, runtime, actions, cases: [{ number: 20, status: "unavailable", actions: [1, 2], subchecks: [check] }] };
  assert.doesNotThrow(() => functionalEvidence(evidence));
  for (const mutate of [
    (e: any) => { e.actions[0].exitCode = 1; },
    (e: any) => { e.actions[0].context.kit = "foreign"; },
    (e: any) => { e.actions[0].context.runtime = { ...e.runtime, pid: 2 }; },
    (e: any) => { e.actions[1].context.bootId = "22222222-1111-1111-1111-111111111111"; },
    (e: any) => { e.cases[0].subchecks[0].id = "three-locale-panel-captures"; },
    (e: any) => { e.cases[0].subchecks[0].artifacts = []; },
    (e: any) => { e.actions[0].artifacts[1].sha256 = "0".repeat(64); },
  ]) { const changed = structuredClone(evidence); mutate(changed); assert.throws(() => functionalEvidence(changed)); }
  assert.equal(check.status, "passed", "capture validation does not create a visual review result");
});

test("installer observation timeout leaves its owned host child alive until natural completion", async () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-observation-timeout-"));
  const file = join(root, "voboost-installer"),
    marker = join(root, "complete");
  writeFileSync(
    file,
    `#!${process.execPath}\nimport {writeFileSync} from 'node:fs'; setTimeout(()=>writeFileSync(${JSON.stringify(marker)},'complete'),150);\n`,
  );
  chmodSync(file, 0o755);
  const result = await spawnAdapter({ executable: file, args: [], timeoutMs: 30 });
  assert.equal(result.timedOut, true);
  assert.ok(result.processId);
  assert.doesNotThrow(() => process.kill(result.processId!, 0));
  for (let n = 0; n < 200 && !existsSync(marker); n++) await new Promise((r) => setTimeout(r, 10));
  assert.equal(readFileSync(marker, "utf8"), "complete");
});

test("keyboard actuation requires exact empty focused editor and a live unique layout key", () => {
  const xml = windowModel();
  const nodes = parseUi(xml);
  emptyEditor(nodes);
  assert.equal(keyboardKey(nodes, "a").text, "a");
  assert.throws(
    () => emptyEditor(nodes.map((n) => (n.focused ? { ...n, text: "old" } : n))),
    /empty/,
  );
  assert.throws(() => emptyEditor(nodes.map((n) => ({ ...n, focused: false }))), /focused/);
  assert.throws(() => keyboardKey(nodes.slice(0, 2), "a"), /layout/);
  assert.throws(() => keyboardKey([...nodes, nodes.find(n => n.text === "a")!], "a"), /unique/);
  assert.throws(
    () =>
      keyboardKey(
        nodes.map((n) => ({ ...n, packageName: "foreign" })),
        "a",
      ),
    /editor/,
  );
});
test("launcher first slot derives live visual order and descendant labels", () => {
  const nodes = parseUi(
    '<hierarchy><node package="com.qinggan.app.launcher" class="android.widget.GridView" bounds="[0,0][600,300]"><node package="com.qinggan.app.launcher" clickable="true" bounds="[300,0][500,100]"><node package="com.qinggan.app.launcher" text="Voboost Fixture" bounds="[310,50][480,90]" /></node><node package="com.qinggan.app.launcher" clickable="true" text="Stock" bounds="[0,0][200,100]" /></node></hierarchy>',
  );
  assert.deepEqual(
    launcherItems(nodes, nodes[0]).map((x) => x.label),
    ["Stock", "Voboost Fixture"],
  );
  assert.throws(() => launcherItems(nodes, { ...nodes[0] }), /foreign/);
});
test("launcher first slot follows centered OEM scale rather than shifted top edges", () => {
  const nodes = parseUi(
    '<hierarchy><node package="com.qinggan.app.launcher" class="android.widget.GridView" bounds="[145,0][1920,720]"><node package="com.qinggan.app.launcher" clickable="true" text="Telegram X" bounds="[471,125][756,324]" /><node package="com.qinggan.app.launcher" clickable="true" text="Second row" bounds="[195,369][474,558]" /><node package="com.qinggan.app.launcher" clickable="true" text="Cloud Space" bounds="[753,130][1032,319]" /><node package="com.qinggan.app.launcher" clickable="true" text="Voboost Fixture" bounds="[195,130][474,319]" /></node></hierarchy>',
  );
  assert.deepEqual(launcherItems(nodes, nodes[0]).map(item => item.label), ["Voboost Fixture", "Telegram X", "Cloud Space", "Second row"]);
  // A fixture genuinely located after the selected item must not become first.
  nodes.find(node => node.text === "Voboost Fixture")!.bounds = [1032,130,1311,319];
  assert.deepEqual(launcherItems(nodes, nodes[0]).map(item => item.label), ["Telegram X", "Cloud Space", "Voboost Fixture", "Second row"]);
});
test("owned fixture cleanup requires original absence exact approved identity and real install success", () => {
  const sha = "ru.voboost.tests.launcherfixture";
  assert.equal(fixtureOwned("fixture-absent\n", "Performing Streamed Install\nSuccess\n", "ru.voboost.tests.launcherfixture", sha), true);
  for (const row of [["fixture-present\n", "Success\n", "ru.voboost.tests.launcherfixture", sha], ["permission denied", "Success\n", "ru.voboost.tests.launcherfixture", sha], ["fixture-absent\n", "Failure [denied]\n", "ru.voboost.tests.launcherfixture", sha], ["fixture-absent\n", "Success\nFailure [denied]\n", "ru.voboost.tests.launcherfixture", sha], ["fixture-absent\n", "Success\n", "other.package", sha], ["fixture-absent\n", "Success\n", "ru.voboost.tests.launcherfixture", "a".repeat(64)]]) assert.equal(fixtureOwned(...row), false);
  assert.throws(() => functionalEvidence({ fixtureCleanup: { owned: true, status: "passed", originalAbsenceAction: 1, installAction: 2, removalAction: 3 }, actions: [], cases: [], boots: [], identity: { package: "host" }, runtime: null }), /actual successful action/);
});
test("startup profile program observes nine actual selected descriptions and returns from none only by computer", async () => {
  const calls: string[] = [];
  await startupProfiles(async (language) => { calls.push(`language:${language}`); }, async (mode) => { calls.push(`radio:${mode}`); }, async (mode, language) => { calls.push(`description:${mode}:${language}`); }, async () => { calls.push("computer:interface"); });
  assert.deepEqual(calls, ["en", "ru", "zh"].flatMap((language) => [`language:${language}`, "radio:interface", `description:interface:${language}`, "radio:hidden", `description:hidden:${language}`, "radio:none", `description:none:${language}`, "computer:interface"]));
  const failed: string[] = [];
  await assert.rejects(() => startupProfiles(async () => {}, async (mode) => { failed.push(mode); }, async (mode) => { if (mode === "hidden") throw new Error("old profile description"); }, async () => { failed.push("computer"); }), /old profile/);
  assert.deepEqual(failed, ["interface", "hidden"], "a failed description cannot execute later mode mutations");
});
test("startup reboot program selects and observes real hidden and none radios before guest reboot", async () => {
  for (const mode of ["hidden", "none"] as const) {
    const calls: string[] = [];
    await startupReboot(mode, async (selected) => { calls.push(`radio:${selected}`); }, async (selected) => { calls.push(`description:${selected}`); }, async () => { calls.push("guest-reboot"); });
    assert.deepEqual(calls, [`radio:${mode}`, `description:${mode}`, "guest-reboot"]);
    await assert.rejects(() => startupReboot(mode, async () => {}, async () => { throw new Error("unconfirmed selected description"); }, async () => { assert.fail("reboot after unconfirmed radio"); }), /unconfirmed/);
  }
});
test("none process absence rejects permission denial malformed inventory and resident names", () => {
  noneProcesses("processes:\n  PID NAME\n\ncontexts:\n");
  for (const body of [
    "missing",
    "processes:\npermission denied\ncontexts:",
    "processes:\n PID NAME\n denied\ncontexts:",
  ])
    assert.throws(() => noneProcesses(body), /unavailable|header|exposed/);
  assert.throws(
    () => noneProcesses("processes:\n PID NAME\n 123 voboost-daemon\ncontexts:"),
    /resident/,
  );
});
test("PNG capture refuses incomplete streams instead of accepting only a signature", () => {
  assert.throws(() => validatePng(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), /incomplete/);
  assert.throws(() => validatePng(Buffer.alloc(32)), /invalid/);
  assert.deepEqual(validatePng(png()), { width: 1, height: 1 });
  assert.throws(() => validatePng(png([0, 0, 0])), /black/);
  const broken = png();
  broken[broken.length - 1] ^= 1;
  assert.throws(() => validatePng(broken), /CRC/);
});

async function lifecycle(options: { canonicalProgress?: boolean; residentDiagnostics?: "typed" | "unknown" | "malformed"; residentEndpoint?: "accepted" | "numeric-document" | "unknown-field"; cacheRecovery?: boolean; secondScreenRecovery?: boolean; uiDeadline?: "once" | "persistent" | "unsupported" | "boot"; verifyFailure?: boolean; secondScreen?: "transient" | "persistent" | "wrong-display" | "boot"; focusResetRequired?: boolean; homeFailure?: boolean; preparation?: "normal" | "already-absent" | "emergency"; removal?: "emergency"; stale?: "boot" | "status"; incompletePreparation?: boolean; historicalOperation?: any; retainHistorical?: boolean; readiness?: string; closedTransport?: "active" | "inactive"; rootGuard?: "active" | "inactive" | "terminal" } = {}) {
  const c = fixture(),
    calls: Invocation[] = [];
  let number = 1,
    installed = false,
    removals = 0, installRebootCount = 0, acceptanceRelease: (() => void) | null = null, statusReads = 0, residentReads = 0, rootProgress = false, rootIssued = false, homeObserved = false;
  let deadlineAttempts = 0, screenAttempts = 0;
  let recoveryWriterHeld = false, recoveryAck: any = null;
  const heldActions: string[] = [];
  const actualNow = performance.now.bind(performance);
  const clock = options.secondScreen === "persistent" ? mock.method(performance, "now", () => actualNow() + screenAttempts * 40000) : null;
  if (options.secondScreen) {
    const path = join(c.voyemuRoot, "machines", c.voyemuProfile, "machine.json");
    const machine = JSON.parse(readFileSync(new URL("./fixtures/cycle112-second-screen/prepared.json", import.meta.url), "utf8"));
    machine.profile = c.voyemuProfile;
    writeFileSync(path, JSON.stringify(machine));
  }
  const currentBoot = () => `${String(number).padStart(8, "0")}-1111-1111-1111-111111111111`;
  const e = await runGate(c, async (inv) => {
    if (inv.host) return owningHostReply(c, inv)!;
    calls.push(inv);
    if (inv.voyemu) {
      const cmd = inv.args[4];
      if (cmd === "verify" && ((installed && installRebootCount === 1 && !options.secondScreenRecovery) || (recoveryWriterHeld && options.secondScreenRecovery)) && options.secondScreen) {
        screenAttempts++;
        if (screenAttempts === 1 || options.secondScreen === "persistent") {
          const wire = JSON.parse(readFileSync(new URL(options.secondScreenRecovery ? "./fixtures/cycle121-second-screen/verify-1308.json" : "./fixtures/cycle112-second-screen/verify-140.json", import.meta.url), "utf8"));
          wire.profile = c.voyemuProfile;
          if (options.secondScreen === "wrong-display") wire.report.checks.find((r: any) => r.id === "routing:second:SecondHiboardDelegate").actual = [0];
          if (options.secondScreen === "boot") number++;
          return { exitCode: 6, stdout: JSON.stringify(wire), stderr: "" };
        }
      }
      if (cmd === "verify" && installed && installRebootCount === 1 && options.verifyFailure) return { exitCode: 1, stdout: "", stderr: "strict inventory test failure" };
      return reply(
        c,
        cmd,
        cmd === "status"
          ? {
              profiles: {
                [c.voyemuProfile]: { prepared: true, running: true, port: 5562, pid: options.readiness === "vm" && residentReads > 0 ? 334 : 333 },
              },
            }
          : { checks: prepared(c).checks },
      );
    }
    const done = (value: any) => ({
      exitCode: 0,
      stdout: JSON.stringify(value) + "\n",
      stderr: "",
    });
    if (inv.args[0] === "--verify-package")
      return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    if (inv.args[0] === "--status") {
      if (removals === 2 && !installed && options.stale === "boot") number++;
      if (installed) statusReads++;
      return done(
        installed || (removals === 2 && options.stale === "status")
          ? {
              deviceSerial: "emulator-5562",
              installedVersion: "1.0.0-beta1",
              health: options.readiness && (!["pending", "coexist"].includes(options.readiness) || statusReads === 1) ? "degraded" : "healthy",
              diagnostic: "resident status: status.json contains an unhealthy selected injection",
              activeGeneration: "a".repeat(64), startupMode: "interface", daemonStatus: "running",
              contentRelation: "same",
            }
          : { route: "absent", deviceSerial: "emulator-5562", installerVersion: "1.0.0-beta1" },
      );
    }
    if (inv.args[0] === "--uninstall") {
      removals++;
      const kind = removals === 1 ? options.preparation : options.removal;
      if (options.cacheRecovery && removals === 1) {
        assert.ok(inv.args.includes("--emulator-cache-recovery-acceptance"));
        assert.ok(inv.stdinReply);
        const priorBootId = currentBoot();
        number++;
        recoveryWriterHeld = true;
        const event = {schemaVersion:1, operation:"cache-recovery-boot", attemptToken:"a".repeat(64), priorBootId, bootId:currentBoot(), state:"waiting"};
        const answer = await inv.stdinReply!("stderr", "VBI_CACHE_RECOVERY_BOOT|" + JSON.stringify(event) + "\n");
        recoveryAck = JSON.parse(answer!);
        assert.equal(recoveryAck.decision, options.secondScreenRecovery && options.secondScreen !== "transient" ? "reject" : "accept", "actual composed consumer must require complete capture-only held boot");
        assert.equal(recoveryAck.bootId, currentBoot());
        recoveryWriterHeld = false;
      }
      if (installed || kind === "emergency") number++;
      installed = false;
      const receipt = kind === "emergency" || kind === "already-absent"
        ? { state: kind === "emergency" ? "emergency-removed" : "already-absent", bootId: currentBoot(), checks: absence().checks }
        : { ...absence(), bootId: currentBoot() };
      if (removals === 1 && options.incompletePreparation) receipt.checks = receipt.checks.slice(1);
      return done(receipt);
    }
    if (inv.args[0] === "--install") {
      number++;
      installed = true;
      installRebootCount = 1;
      const manifestSha256 = hash(readFileSync(join(c.closure, "voboost.json")));
      const armed = `install-acceptance-armed ${JSON.stringify({ schemaVersion: 1, transactionId: manifestSha256, manifestSha256, attemptToken: "a".repeat(64) })}\n`;
      inv.onOutput?.("stdout", armed);
      rootProgress = true;
      const acceptance = new Promise<void>(resolve => { acceptanceRelease = resolve; });
      let progressText = "";
      const progress = (reboot: number) => {
        if (!options.canonicalProgress) { inv.onOutput?.("stdout", `Restarting the head unit (reboot ${reboot})\n`); return; }
        const line = installerLog(`Restarting the head unit (reboot ${reboot}). Waiting for reconnection; install will continue automatically.`, "info", reboot);
        progressText += line;
        inv.onOutput?.("stderr", line.slice(0, 31));
        inv.onOutput?.("stderr", line.slice(31));
      };
      progress(1);
      if (options.closedTransport || options.canonicalProgress) {
        let expiry: ReturnType<typeof setTimeout>;
        try { await Promise.race([acceptance, new Promise<void>(resolve => { expiry = setTimeout(resolve, 10000); })]); }
        finally { clearTimeout(expiry!); }
      } else await acceptance;
      number++;
      installRebootCount = 2;
      progress(2);
      return {
        exitCode: 0,
        stdout: armed + `Install completed: transaction ${manifestSha256}\n`,
        stderr: progressText,
      };
    }
    if (inv.args[0] === "--emulator-check") {
      const req = JSON.parse(readFileSync(inv.args[1], "utf8"));
      if (recoveryWriterHeld) {
        heldActions.push(req.action);
        if (["key", "open-settings"].includes(req.action)) return {exitCode:1, stdout:"", stderr:"device transaction writer lock held"};
      }
      if (req.action === "install-acceptance") {
        assert.deepEqual(Object.keys(req).sort(), ["action", "transactionId", "manifestSha256", "attemptToken", "bootId", "rebootCount", "decision"].sort());
        const { action, ...fields } = req;
        const identity = { schemaVersion: 1, ...fields };
        if (req.decision !== "status") acceptanceRelease?.();
        return done({ schemaVersion: 1, serial: "emulator-5562", body: JSON.stringify({ ...identity, state: req.decision === "status" ? "waiting" : req.decision === "accept" ? "accepted" : "rejected", expiresMonotonicMs: "999999" }) });
      }
      if (req.action === "key" && req.key === "home") {
        if (options.homeFailure) return { exitCode: 1, stdout: "", stderr: "Home input failed" };
        homeObserved = true;
      }
      if (req.action === "snapshot" && installed && installRebootCount === 1 && options.uiDeadline && (deadlineAttempts++ === 0 || options.uiDeadline === "persistent")) {
        if (options.uiDeadline === "boot") number++;
        return { exitCode: 1, stdout: "", stderr: options.uiDeadline === "unsupported" ? "permission denied" : warmupFailure };
      }
      if (req.action === "snapshot" && options.focusResetRequired && !homeObserved) return { exitCode: 1, stdout: "", stderr: "UI capture: application root unavailable until ordinary Home navigation" };

      if (req.action === "boot-observation" && (options.closedTransport === "inactive" || options.closedTransport === "active" && rootProgress && !rootIssued)) {
        rootIssued = true;
        return closedBootResult();
      }
      if (req.action === "boot-observation" && (options.rootGuard === "inactive" || options.rootGuard === "active" && rootProgress && !rootIssued)) {
        rootIssued = true;
        return { exitCode: 1, stdout: "", stderr: "boot-observation failure reason=guard-root remote_exit=73 stdout_bytes=0 stderr_bytes=36\nEmulator acceptance failed: boot observation guard rejected: root\n" };
      }
      if (req.action === "boot-observation" && rootProgress && installed) {
        const transactionId = hash(readFileSync(join(c.closure, "voboost.json")));
        const operation = { ...historicalInstall(), transactionId, journal: `recovery/${transactionId}/install-journal.json`, bootId: currentBoot(), rebootCount: installRebootCount };
        return done({ schemaVersion: 1, serial: "emulator-5562", body: `bootCompleted=1\nbootId=${currentBoot()}\nenforcing=Enforcing\noperation:\n${JSON.stringify(operation)}\n` });
      }
      if (["inspect", "resident-status"].includes(req.action) && installed && options.readiness && statusReads > 0) {
        residentReads++;
        const state = options.readiness === "fault" ? "failed" : ["pending", "coexist"].includes(options.readiness) && residentReads > 1 ? "active" : "waiting";
        const resident = { schemaVersion: 1, releaseIdentity: "1.0.0-beta1", manifestSha256: hash(readFileSync(join(c.closure, "voboost.json"))), generationId: options.readiness === "generation" ? "b".repeat(64) : "a".repeat(64), catalogSha256: hash(readFileSync(join(c.closure, "agents.json"))), startupMode: "interface", state: "ready", killed: false, panic: false, executors: [{ id: "ui-qinggan", processes: ["com.qinggan.app.setting"], surfaces: ["settings"] }], injections: [{ id: "settings", process: "com.qinggan.app.setting", state, reason: state === "active" ? "ready" : "owned-worker-waiting", details: {} }] };
        if (options.residentDiagnostics) {
          Object.assign(resident, { persistentVehicleState: { bootId: currentBoot(), observedAtBootMs: 1000, forcedEv: { outcome: "value", value: true }, pedestrianWarning: { outcome: "value", value: false } }, lastUnsafeNativeFailure: { process: "com.qinggan.app.setting", targetPid: 123, targetStarttime: 1234, generationId: resident.generationId, catalogSha256: resident.catalogSha256, documentGeneration: 17, diagnostic: { result: "unsafe-cleanup", stage: "restore", restore_part: "pstate", operation: "set", reason: "mismatch", pstate_expected: null, pstate_observed: null, pstate_xor: null, first_mismatch: null } } });
          if (options.residentDiagnostics === "unknown") Object.assign(resident, { unknown: true });
          if (options.residentDiagnostics === "malformed") (resident as any).lastUnsafeNativeFailure.targetPid = "123";
        }
        if (options.residentEndpoint) {
          const root = process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/", import.meta.url).pathname;
          const endpoint = JSON.parse(readFileSync(join(root, "daemon/tests/fixtures/current-endpoint146.json"), "utf8")).accepted;
          Object.assign(resident.injections[0], {documentGeneration:endpoint.desiredDocumentGeneration,lifecycleGeneration:"17",currentEndpoint:endpoint});
          if (options.residentEndpoint === "numeric-document") endpoint.acceptedStatus.documentGeneration = Number(endpoint.acceptedStatus.documentGeneration);
          if (options.residentEndpoint === "unknown-field") endpoint.extra = true;
        }
        const mutations: Record<string, () => void> = {
          manifest: () => { resident.manifestSha256 = "b".repeat(64); },
          catalog: () => { resident.catalogSha256 = "b".repeat(64); },
          startup: () => { resident.startupMode = "none"; },
          panic: () => { resident.panic = true; },
          killed: () => { resident.killed = true; },
          degraded: () => { resident.state = "degraded"; },
          foreign: () => { resident.injections[0].state = "foreign"; },
          quarantined: () => { resident.injections[0].state = "quarantined"; },
          unknown: () => { resident.injections[0].reason = "unknown-wait"; },
          malformed: () => { resident.injections[0].details = null; },
          duplicate: () => { resident.injections.push(resident.injections[0]); },
          healthy: () => { resident.injections[0].state = "active"; resident.injections[0].reason = "ready"; },
        };
        mutations[options.readiness]?.();
        if (options.readiness === "coexist") resident.injections.push({ id: "font-cyrillic", process: "com.qinggan.app.setting", state: "skipped-coexist", reason: "foreign-footprint", details: {} });
        if (options.readiness === "transport") return { exitCode: 1, stdout: "", stderr: "transport unavailable" };
        const observedBoot = options.readiness === "boot" ? "ffffffff-1111-1111-1111-111111111111" : currentBoot();
        const after = options.readiness === "after-boot" ? "ffffffff-1111-1111-1111-111111111111" : observedBoot;
        const header = req.action === "resident-status"
          ? `bootCompleted=${options.readiness === "incomplete" ? 0 : 1}\nbootId=${observedBoot}\nenforcing=Enforcing\nbootIdAfter=${after}\n`
          : `bootId=${observedBoot}\nenforcing=Enforcing\nbootCompleted=1\n`;
        const statusBytes = options.readiness === "tail" ? JSON.stringify(resident) + "\n\n{}" : JSON.stringify(resident);
        return done({ schemaVersion: 1, serial: "emulator-5562", body: `${header}resident-status:\n/data/voboost/status.json\n${statusBytes}\n\n` });
      }
      if (req.action === "screenshot") {
        writeFileSync(req.output, png());
        return done({ schemaVersion: 1, serial: "emulator-5562", png: req.output });
      }
      return done({
        schemaVersion: 1,
        serial: "emulator-5562",
        body:
          req.action === "snapshot"
            ? '<hierarchy><node package="com.qinggan.app.launcher" bounds="[0,0][600,300]" /></hierarchy>'
            : req.action === "boot-observation"
              ? `bootCompleted=1\nbootId=${currentBoot()}\nenforcing=Enforcing\noperation:\n${options.historicalOperation && (removals === 0 || options.retainHistorical) ? JSON.stringify(options.historicalOperation) + "\n" : ""}`
            : req.action === "inspect"
              ? `bootId=${currentBoot()}\nenforcing=Enforcing\nbootCompleted=1\n${options.historicalOperation && (removals === 0 || options.retainHistorical) ? "operation:\n" + JSON.stringify(options.historicalOperation) + "\n\nplan:\n" : ""}`
              : "",
      });
    }
    return { exitCode: 1, stdout: "", stderr: "read observation unavailable" };
  });
  clock?.mock.restore();
  return { e, calls, heldActions, recoveryAck };
}

test("public dispatcher executes original install removal reinstall before a genuine missing-tab failure", async () => {
  const { e, calls } = await lifecycle();
  assert.deepEqual(
    e.cases.slice(0, 5).map((x: any) => x.status),
    ["passed", "passed", "passed", "passed", "failed"],
  );
  assert.match(e.cases[4].reason, /matched 0|tab absent/);
  assert.equal(e.passed, false);
  assert.equal(e.cases[25].status, "not-run");
  assert.ok(e.boots.some((b: any) => b.status === "passed" && b.kind === "installer-1"));
  assert.equal(calls.filter((c) => c.args[0] === "--install").length, 2);
  assert.equal(calls.filter((c) => c.args[0] === "--uninstall").length, 2);
  assert.equal(e.preparation.status, "passed");
  for (const number of [1, 3]) assert.ok(!e.actions.some((a: any) => a.caseNumber === number && a.args[0] === "--uninstall"));
  const removal = e.actions.find((a: any) => a.caseNumber === 2 && a.args[0] === "--uninstall");
  assert.ok(e.cases[2].actions.includes(removal.id));
  assert.ok(e.cases[2].subchecks[0].artifacts.includes(removal.artifacts[0].path));
  const reads = calls.filter((call) => call.args[0] === "--emulator-check");
  for (let index = 1; index < reads.length; index++) {
    if (reads[index].args[1].endsWith("-inspect.json")) assert.ok(!reads[index - 1].args[1].includes("/probe-"), "a full Inspect must not be preceded by another full probe");
  }
  for (const row of e.boots.filter((row: any) => row.status === "passed")) {
    const observed = e.actions.find((action: any) => action.id === row.observation.actionId);
    assert.equal(row.observation.finishedMonotonicNs, observed.finishedMonotonicNs);
    assert.equal(observed.context.bootId, row.after);
    assert.ok(row.actions.includes(observed.id));
  }
  for (const mutate of [
    (row: any) => { row.observation.finishedMonotonicNs = "0"; },
    (row: any) => { row.verifiedBoot = "22222222-1111-1111-1111-111111111111"; },
    (row: any) => { row.kind = "installer-9"; row.observation.rebootCount = 1; },
  ]) { const changed = structuredClone(e); mutate(changed.boots.find((row: any) => row.status === "passed")); assert.throws(() => functionalEvidence(changed), /boot|count/); }
});

test("public dispatcher retries root observation only during its active install without replay", async () => {
  const active = await lifecycle({ rootGuard: "active" });
  const first = active.e.boots.find((row: any) => row.kind === "installer-1" && row.caseNumber === 1);
  assert.equal(first?.status, "passed", first?.reason);
  assert.equal(active.calls.filter((call) => call.args[0] === "--install").length, 2);
  const guard = active.e.actions.filter((row: any) => row.artifacts.some((artifact: any) => artifact.kind === "stderr" && readFileSync(artifact.path, "utf8").includes("reason=guard-root")));
  assert.equal(guard.length, 1);
  assert.ok(first.actions.includes(guard[0].id));
  assert.ok(first.after && first.verifiedBoot === first.after);
  const install = active.e.actions.find((a: any) => a.caseNumber === 1 && a.args[0] === "--install");
  const observed = active.e.actions.filter((a: any) => first.actions.includes(a.id) && a.args[0] === "--emulator-check");
  const png = observed.find((a: any) => JSON.parse(readFileSync(a.args[1], "utf8")).action === "screenshot");
  const navigation = observed.filter((a: any) => JSON.parse(readFileSync(a.args[1], "utf8")).action === "open-settings");
  if (BigInt(install.finishedMonotonicNs) > BigInt(png.startedMonotonicNs)) assert.equal(navigation.length, 0);
  else assert.equal(navigation.length, 1);
  const inactive = await lifecycle({ rootGuard: "inactive" });
  assert.equal(inactive.e.preparation.status, "failed");
  assert.equal(inactive.calls.filter((call) => call.args[0] === "--install").length, 0);
});

test("public dispatcher monitors chunked canonical stderr reboot progress while preserving stdout acceptance", async () => {
  const current = await lifecycle({ canonicalProgress: true });
  const first = current.e.boots.find((row: any) => row.kind === "installer-1" && row.caseNumber === 1);
  assert.equal(first?.status, "passed", first?.reason);
  assert.ok(first.after && first.verifiedBoot === first.after);
  assert.equal(current.calls.filter(call => call.args[0] === "--install").length, 2);
  const action = current.e.actions.find((row: any) => row.args[0] === "--install");
  const trace = action.artifacts.find((row: any) => row.kind === "stderr");
  assert.match(readFileSync(trace.path, "utf8"), /"level":"info".*reboot 1/);
});

test("intermediate boot verification and captures precede exact acceptance consumption", async () => {
  const { e, calls } = await lifecycle();
  const row = e.boots.find((boot: any) => boot.caseNumber === 1 && boot.kind === "installer-1");
  assert.equal(row?.status, "passed", row?.reason);
  const acceptance = e.actions.filter((a: any) => a.caseNumber === 1 && a.args[0] === "--emulator-check")
    .map((action: any) => ({ action, request: JSON.parse(readFileSync(action.args[1], "utf8")) }))
    .filter((entry: any) => entry.request.action === "install-acceptance");
  assert.deepEqual(acceptance.map((entry: any) => entry.request.decision), ["status", "accept"]);
  const lastProof = e.actions.find((action: any) => action.id === row.actions.at(-1));
  assert.ok(BigInt(lastProof.finishedMonotonicNs) <= BigInt(acceptance[0].action.startedMonotonicNs));
  assert.equal(acceptance[1].request.bootId, row.verifiedBoot);
  const install = e.actions.find((a: any) => a.caseNumber === 1 && a.args[0] === "--install");
  assert.ok(BigInt(install.finishedMonotonicNs) >= BigInt(acceptance[1].action.startedMonotonicNs));
  assert.ok(calls.find(call => call.args[0] === "--install")!.args.includes("--emulator-install-acceptance"));
});

test("boot UI pairs follow recorded stock Settings navigation and preserve boot authority", async () => {
  const { e } = await lifecycle();
  const boots = e.boots.filter((row: any) => row.status === "passed");
  assert.ok(boots.length > 0);
  for (const row of boots) {
    const commands = e.actions.filter((a: any) => row.actions.includes(a.id) && a.args[0] === "--emulator-check")
      .map((a: any) => ({ action: a, request: JSON.parse(readFileSync(a.args[1], "utf8")) }));
    const navigation = commands.findIndex((c: any) => c.request.action === "open-settings");
    const png = commands.findIndex((c: any) => c.request.action === "screenshot");
    const hierarchy = commands.findIndex((c: any) => c.request.action === "snapshot");
    const intermediate = /^installer-[0-9]+$/.test(row.kind);
    if (intermediate) assert.equal(navigation, -1, "an intermediate installer boot permits only read-only observation even after child exit");
    else assert.ok(navigation >= 0 && navigation < png && png < hierarchy, "post-writer boot UI must be foregrounded before both captures");
    assert.equal(row.verifiedBoot, row.after);
    assert.equal(commands[png].action.artifacts.find((a: any) => a.kind === "png").boot, row.after);
    assert.equal(commands[hierarchy].action.context.bootId, row.after);
  }
});

test("preparation emergency never satisfies normal CLI or Settings removal", async () => {
  const { e } = await lifecycle({ preparation: "emergency", removal: "emergency" });
  assert.equal(e.preparation.status, "passed");
  assert.equal(e.preparation.kind, "emergency");
  assert.equal(e.preparation.context.kit, e.identity.package);
  assert.deepEqual(e.preparation.context.runtime, e.runtime);
  assert.equal(e.cases[0].status, "passed");
  assert.equal(e.cases[1].status, "failed");
  assert.equal(e.cases[2].status, "not-run");
  assert.equal(e.cases[16].status, "not-run");
  const receipt = { state: "emergency-removed", bootId: boot, checks: absence().checks };
  const result = { exitCode: 0, stdout: JSON.stringify(receipt), stderr: "" };
  assert.equal(preparationAbsence(result).kind, "emergency");
  assert.throws(() => normalAbsence(result));
  assert.throws(() => normalAbsence({ ...result, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", bootIdBefore: boot, absence: receipt, enforcing: true }) }));
});

test("preparation retains complete already-absent proof and rejects incomplete cleanup before case one", async () => {
  const { e } = await lifecycle({ preparation: "already-absent" });
  assert.equal(e.preparation.status, "passed");
  assert.equal(e.preparation.kind, "already-absent");
  assert.ok(e.preparation.artifacts.length > 0);
  assert.equal(e.preparation.bootId, e.preparation.context.bootId);
  const rejected = await lifecycle({ incompletePreparation: true });
  assert.equal(rejected.e.preparation.status, "failed");
  assert.ok(rejected.e.cases.every((c: any) => c.status === "not-run"));
  assert.equal(rejected.calls.filter((c) => c.args[0] === "--install").length, 0);
});

test("case three reuses normal removal proof only with fresh absent status and the same boot", async () => {
  for (const stale of ["boot", "status"] as const) {
    const { e, calls } = await lifecycle({ stale });
    assert.equal(e.cases[1].status, "passed");
    assert.equal(e.cases[2].status, "failed");
    assert.equal(e.cases[3].status, "not-run");
    assert.equal(calls.filter((c) => c.args[0] === "--uninstall").length, 2);
    assert.match(e.cases[2].reason, /absence|boot/);
  }
});

const historicalInstall = () => JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle65/completed-old-install.json", import.meta.url), "utf8"));

test("preparation accepts retained completed older installation only before normal CLI cleanup", async () => {
  const { e, calls } = await lifecycle({ historicalOperation: historicalInstall() });
  assert.equal(e.preparation.status, "passed");
  assert.equal(e.cases[0].status, "passed");
  assert.equal(calls.filter((c) => c.args[0] === "--uninstall").length, 2);
});

const failedHistoricalInstall = () => JSON.parse(readFileSync(new URL("../../testdata/emulator-cycle109/failed-old-install.json", import.meta.url), "utf8"));

test("preparation cleans failed previous-boot install through packaged CLI without borrowing reboot evidence", async () => {
  const failed = failedHistoricalInstall();
  assert.equal(installOperation(`operation:\n${JSON.stringify(failed)}`, boot, "b".repeat(64), true), null);
  const { e, calls } = await lifecycle({ historicalOperation: failed, preparation: "emergency" });
  assert.equal(e.preparation.status, "passed");
  assert.equal(e.preparation.kind, "emergency");
  assert.equal(e.cases[0].status, "passed");
  assert.equal(calls.filter(c => c.args[0] === "--uninstall").length, 2);
  for (const patch of [{ outcome: "running" }, { outcome: "reboot-required" }, { error: null }, { error: { code: "", message: "failure" } }, { error: { code: "failed", message: "" } }, { error: { code: "failed", message: "x".repeat(513) } }, { error: { code: "failed", message: "failure", extra: true } }, { installedApk: historicalInstall().installedApk }, { phase: "" }, { bootId: "00000001-1111-1111-1111-111111111111" }]) {
    const result = await lifecycle({ historicalOperation: { ...failed, ...patch } });
    assert.equal(result.e.preparation.status, "failed", JSON.stringify(patch));
    assert.equal(result.calls.filter(c => ["--uninstall", "--install"].includes(c.args[0])).length, 0);
  }
  assert.throws(() => installOperation(`operation:\n${JSON.stringify(failed)}`, boot, "b".repeat(64)), /foreign/);
  const retained = await lifecycle({ historicalOperation: failed, retainHistorical: true });
  assert.equal(retained.e.preparation.status, "failed");
  assert.equal(retained.calls.filter(c => c.args[0] === "--uninstall").length, 1);
  assert.equal(retained.calls.filter(c => c.args[0] === "--install").length, 0);
});

test("historical preparation rejects live failed malformed duplicate and later retained install operations", async () => {
  for (const patch of [{ phase: "install-active" }, { outcome: "failed" }, { error: "failed" }, { bootId: "invalid" }, { transactionId: "invalid" }, { journal: "foreign" }, { journalSha256: "invalid" }, { rebootCount: 17 }, { rebootCount: 0 }, { statusSequence: 0 }, { installedApk: null }, { extra: true }]) {
    const { e, calls } = await lifecycle({ historicalOperation: { ...historicalInstall(), ...patch } });
    assert.equal(e.preparation.status, "failed", JSON.stringify(patch));
    assert.equal(calls.filter((c) => ["--uninstall", "--install"].includes(c.args[0])).length, 0);
  }
  const { e, calls } = await lifecycle({ historicalOperation: historicalInstall(), retainHistorical: true });
  assert.equal(e.preparation.status, "failed");
  assert.equal(calls.filter((c) => c.args[0] === "--uninstall").length, 1);
  assert.equal(calls.filter((c) => c.args[0] === "--install").length, 0);
  const historical = JSON.stringify(historicalInstall()), current = "b".repeat(64);
  assert.throws(() => installOperation(`operation:\n${historical}\n${historical}`, boot, current, true), /conflicting/);
  assert.throws(() => installOperation('operation:\n{"operation":"install",', boot, current, true), /malformed/);
  assert.throws(() => installOperation(`operation:\n${historical}`, boot, current), /foreign/);
  assert.equal(installOperation(`operation:\n${historical}`, boot, current, true), null);
  const matching = { ...historicalInstall(), transactionId: current, bootId: boot };
  assert.equal(installOperation(JSON.stringify(matching), boot, current)?.rebootCount, 2);
  assert.equal(installOperation(JSON.stringify(matching), "00000002-1111-1111-1111-111111111111", current), null);
});

test("preparation admits older completed installation on the current boot but rejects concurrent foreign uninstall", async () => {
  const completed = { ...historicalInstall(), bootId: "00000001-1111-1111-1111-111111111111" };
  const { e, calls } = await lifecycle({ historicalOperation: completed });
  assert.equal(e.preparation.status, "passed");
  assert.equal(e.cases[0].status, "passed");
  assert.equal(calls.filter((c) => c.args[0] === "--uninstall").length, 2);
  const live = await lifecycle({ historicalOperation: { ...completed, phase: "install-active", outcome: "running" } });
  assert.equal(live.e.preparation.status, "failed");
  assert.equal(live.calls.filter((c) => c.args[0] === "--uninstall").length, 0);
  assert.throws(() => installOperation(`operation:\n${JSON.stringify(completed)}\n${JSON.stringify({ operation: "uninstall", transactionId: "c".repeat(64), phase: "active" })}`, boot, "b".repeat(64), true), /conflicting/);
});

 test("required boot observation excludes optional forensic dependency and rejects malformed mandatory state", () => {
  const body = `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n`;
  assert.deepEqual(parseBootObservation(body), { boot, bootCompleted: true });
  for (const bad of [body + "plan:\n", body.replace("bootCompleted=1", "bootCompleted=2"), body.replace(boot, "caller-boot"), body.replace("Enforcing", "unknown"), body.replace("operation:", "operation:\n{}"), body.replace("operation:", "operation:\n" + "x".repeat(16385))]) assert.throws(() => parseBootObservation(bad));
});

test("production mandatory boot capture survives closed full Inspect timeout without borrowing its proof", async () => {
  const directory = mkdtempSync(join(tmpdir(), "boot-capture-"));
  const actions: string[] = [];
  const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(directory, "ui"), async (inv) => {
    const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
    actions.push(request.action);
    if (request.action === "inspect") return { exitCode: null, stdout: "", stderr: "optional forensic command blocked", timedOut: true, processId: 777 };
    assert.equal(request.action, "boot-observation");
    assert.equal(inv.timeoutMs, 90000);
    return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n` }) + "\n", stderr: "" };
  }, async () => {});
  await assert.rejects(() => ui.call({ action: "inspect" }), /optional forensic command blocked/);
  const receipt = await captureBootObservation(ui);
  assert.equal(parseBootObservation(receipt.body).boot, boot);
  assert.deepEqual(actions, ["inspect", "boot-observation"]);
  for (const request of [{ action: "boot-observation", shell: "id" }, { action: "boot-observation", path: "/x" }, { action: "boot-observation", bootId: boot }]) assert.throws(() => validateRequest(request));
});


test("mandatory boot capture rejects unknown operation fields transaction digests counts and wrong-boot substitution", () => {
  const current = "b".repeat(64), operation = { ...historicalInstall(), transactionId: current, journal: `recovery/${current}/install-journal.json`, bootId: boot };
  const body = (row: any) => `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n${JSON.stringify(row)}\n`;
  parseBootObservation(body(operation));
  for (const patch of [{ extra: true }, { transactionId: "x" }, { journalSha256: "x" }, { rebootCount: -1 }, { rebootCount: 17 }, { rebootCount: 1.5 }, { bootId: "x" }, { journal: "foreign" }]) assert.throws(() => parseBootObservation(body({ ...operation, ...patch })));
  assert.throws(() => installOperation(body({ ...operation, transactionId: "a".repeat(64) }), boot, current), /foreign/);
  assert.equal(installOperation(body(operation), "00000002-1111-1111-1111-111111111111", current), null);
});

test("production mandatory capture retries exact transient Permissive before completed Enforcing", async () => {
  let reads = 0;
  const ui = { call: async (request: any) => {
    assert.deepEqual(request, { action: "boot-observation" });
    return { body: `bootCompleted=1\nbootId=${boot}\nenforcing=${++reads === 1 ? "Permissive" : "Enforcing"}\noperation:\n` };
  } };
  const value = await installerBootObservation(1, "before", async () => {
    const receipt = await captureBootObservation(ui);
    return { ...parseBootObservation(receipt.body), rebootCount: 1 };
  }, async () => {});
  assert.equal(reads, 2);
  assert.equal(value.boot, boot);
  for (const mode of ["Disabled", "permissive", "Enforcing\nextra=x"]) assert.throws(() => parseBootObservation(`bootCompleted=1\nbootId=${boot}\nenforcing=${mode}\noperation:\n`));
  assert.throws(() => parseBootObservation(`bootCompleted=1\nbootId=${boot}\nenforcing=Permissive\noperation:\n{}\n`), (error) => !(error instanceof ObservationPending));
});

test("mandatory capture failure remains explicit without full Inspect or healthy fallback", async () => {
  for (const result of [{ exitCode: 1, stdout: "", stderr: "mandatory read failed" }, { exitCode: null, stdout: "", stderr: "mandatory read deadline", timedOut: true, processId: 999 }]) {
    const actions: string[] = [], directory = mkdtempSync(join(tmpdir(), "mandatory-failure-"));
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(directory, "ui"), async (inv) => {
      actions.push(JSON.parse(readFileSync(inv.args[1], "utf8")).action);
      return result;
    }, async () => {});
    await assert.rejects(() => captureBootObservation(ui), /mandatory read/);
    assert.deepEqual(actions, ["boot-observation"]);
  }
});


test("unset boot completion remains pending with exact UUID and durable count before completion", async () => {
  const current = "b".repeat(64);
  const operation = { ...historicalInstall(), transactionId: current, journal: `recovery/${current}/install-journal.json`, bootId: boot, rebootCount: 1 };
  const raw = (completed: string, row = operation) => `bootCompleted=${completed}\nbootId=${row.bootId}\nenforcing=Enforcing\noperation:\n${JSON.stringify(row)}\n`;
  let reads = 0, pauses = 0;
  const seen: any[] = [];
  const ui = { call: async (request: any) => {
    assert.deepEqual(request, { action: "boot-observation" });
    return { body: raw(++reads === 1 ? "" : "1") };
  } };
  const pending = parseBootObservation(raw(""));
  assert.deepEqual(pending, { boot, bootCompleted: false });
  const value = await installerBootObservation(1, "before", async () => {
    const receipt = await captureBootObservation(ui);
    const parsed = parseBootObservation(receipt.body);
    return { ...parsed, rebootCount: installOperation(receipt.body, parsed.boot, current)?.rebootCount ?? null };
  }, async () => { pauses++; }, (observation) => seen.push(observation));
  assert.equal(reads, 2);
  assert.equal(pauses, 1);
  assert.deepEqual(seen, [{ boot, bootCompleted: false, rebootCount: 1 }, { boot, bootCompleted: true, rebootCount: 1 }]);
  assert.deepEqual(value, seen[1]);
  const later = { ...operation, bootId: "00000002-1111-1111-1111-111111111111", rebootCount: 2 };
  reads = 0;
  await assert.rejects(() => installerBootObservation(1, "before", async () => {
    const body = ++reads === 1 ? raw("") : raw("1", later);
    const parsed = parseBootObservation(body);
    return { ...parsed, rebootCount: installOperation(body, parsed.boot, current)?.rebootCount ?? null };
  }, async () => {}), /intermediate boot window was missed/);
  assert.equal(reads, 2);
  for (const bad of [raw("2"), raw(" "), raw("").replace("bootId=", "bootCompleted=1\nbootId="), raw("").replace("Enforcing", "Disabled"), raw("").slice(0, -2), raw("") + JSON.stringify(operation) + "\n", raw("", { ...operation, extra: true } as any)]) assert.throws(() => parseBootObservation(bad));
});

test("separate actual capture editor and IME reject legacy stale foreign hidden and duplicate owners", () => {
  const xml = windowModel(), nodes = parseUi(xml);
  assert.equal(keyboardKey(nodes, "a").windowRef?.id, 18);
  assert.equal(emptyEditor(nodes).windowRef?.id, 17);
  const old = parseUi(xml), editor = nodes.filter(n => n.windowRef?.id === 17);
  assert.throws(() => keyboardKey([...editor, ...old.filter(n => n.windowRef?.id === 18)], "a"), /layout/);
  assert.throws(() => keyboardKey(parseUi('<hierarchy><node package="com.pateonavi.naviapp" class="android.widget.EditText" focused="true" bounds="[0,0][20,20]"/></hierarchy>'), "a"), /editor/);
  assert.throws(() => keyboardKey(parseUi(xml.replaceAll('visible-to-user="true"', 'visible-to-user="false"')), "a"), /editor/);
  assert.throws(() => keyboardKey(parseUi(xml.replace('id="18" type="2"', 'id="18" type="1"')), "a"), /layout/);
  assert.throws(() => keyboardKey([...nodes, ...nodes.filter(n => n.text === "a")], "a"), /unique/);
});

test("focused dialog window selects confirm instead of same-caption underlying app", () => {
  const source = windowModel();
  const first = /<window id="17"[\s\S]*?<\/window>/u.exec(source)![0];
  const base = first.replace('input-focused="true"', 'input-focused="false"').replace('text=""', 'text="Uninstall"').replace('clickable="false"', 'clickable="true"');
  const modal = first.replaceAll('id="17"', 'id="22"').replace('type="1"', 'type="3"').replace('text=""', 'text="Uninstall"').replace('clickable="false"', 'clickable="true"');
  const nodes = parseUi(source.replace(first, base + modal));
  assert.equal(uniqueNode(nodes, n => n.text === "Uninstall").windowRef?.id, 22);
  assert.throws(() => uniqueNode(parseUi(source.replace(first, base + modal.replace('visible-to-user="true"', 'visible-to-user="false"'))), n => n.text === "Uninstall"), /0 visible/);
});

test("multiwindow model uses real tap lifecycle and invalidates both owner windows", async () => {
  const calls: Record<string, unknown>[] = [];
  let text = "";
  const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(mkdtempSync(join(tmpdir(), "windows-ui-")), "ui"), async invocation => {
    const request = JSON.parse(readFileSync(invocation.args[1], "utf8")); calls.push(request);
    if (request.action === "tap") text += "a";
    const snapshot = windowModel().replace('window-id="17" text=""', `window-id="17" text="${text}"`);
    return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: request.action === "snapshot" ? snapshot : "" }) + "\n", stderr: "" };
  }, async () => {});
  const before = await ui.snapshot(), key = keyboardKey(before, "a");
  await ui.tap(key);
  await assert.rejects(() => ui.tap(key), /stale/);
  await assert.rejects(() => ui.tap(before[0]), /stale/);
  const after = await ui.snapshot();
  assert.equal(after.find(n => n.windowRef?.id === 17)?.text, "a");
  assert.equal(calls.filter(call => call.action === "tap").length, 1);
  const request = calls.find(call => call.action === "tap") as any;
  assert.equal(request.expectedWindow.packageName, "com.pateonavi.naviapp");
  assert.equal(request.expectedWindow.id, 17);
  assert.equal(request.expectedTarget.packageName, "com.qinggan.app.qgime");
  assert.equal(request.expectedTarget.windowId, 18);
  assert.equal(request.expectedTarget.windowType, 2);
  assert.equal(calls.some(call => call.action === "text"), false);
  await ui.tap(keyboardKey(after, "a"));
});

test("actual window launcher model binds app icons to the inner grid and exact native labels", () => {
  const source = windowModel();
  const root = /<node [^>]*window-id="17"[^>]*><\/node>/u.exec(source)![0];
  const label = root.replace('text=""', 'text="Stock"').replace('resource-id="com.pateonavi.naviapp:id/root"', 'resource-id="com.qinggan.app.launcher:id/item_name"');
  const icon = root.replace('clickable="false"', 'clickable="true"').replace('</node>', label + '</node>');
  const grid = root.replace('resource-id="com.pateonavi.naviapp:id/root"', 'resource-id="com.qinggan.app.launcher:id/all_app_view"').replace('</node>', icon + '</node>');
  const nodes = parseUi(source.replace(root, grid).replaceAll('com.pateonavi.naviapp', 'com.qinggan.app.launcher'));
  assert.deepEqual(launcherItems(nodes, nodes[0]).map(item => item.label), ["Stock"]);
  assert.throws(() => launcherItems(parseUi(source.replace(root, grid.replace('text="Stock"', 'text=""')).replaceAll('com.pateonavi.naviapp', 'com.qinggan.app.launcher')), nodes[0]), /foreign/);
  const absent = parseUi(source.replace(root, grid.replace('text="Stock"', 'text=""')).replaceAll('com.pateonavi.naviapp', 'com.qinggan.app.launcher'));
  assert.throws(() => launcherItems(absent, absent[0]), /item_name/);
});

test("launcher inventory excludes only a proven clipped uniquely labelled fringe", () => {
  const owner = "com.qinggan.app.launcher";
  const windowRef = { type: 1, active: true, inputFocused: true, rootPackage: owner, rootStatus: "available", bounds: [0, 0, 1920, 720] };
  const node = (parent: number | null, bounds: number[], resource = "", text = "", visibleToUser = true) => ({ packageName: owner, parent, bounds, resource, text, visibleToUser, clickable: true, enabled: true, windowRef } as any);
  const nodes = [node(null, [145, 0, 1920, 720], owner + ":id/all_app_view"), node(0, [195,130,474,319]), node(1,[195,285,474,318],owner + ":id/item_name","Cloud Space"), node(0,[1867,125,1920,324]), node(3,[1872,280,1920,323],owner + ":id/item_name","User Center",false)];
  assert.deepEqual(launcherItems(nodes,nodes[0]).map(item=>item.label),["Cloud Space"]);
  const malformed = (change: (copy: any[]) => void) => { const copy = nodes.map(n=>({...n,bounds:[...n.bounds]})); change(copy); assert.throws(()=>launcherItems(copy,copy[0]),/item_name/); };
  malformed(copy=>{copy[3].bounds=[1590,130,1869,319];copy[4].bounds=[1590,285,1869,318];});
  malformed(copy=>{copy[4].resource="";});
  malformed(copy=>{copy.push({...copy[4]});});
  malformed(copy=>{copy[2].visibleToUser=false;});
  malformed(copy=>{copy[3].bounds=[1641,130,1920,319];});
  // Actual202 actions639/827: a clipped adjacent-page label can be visible
  // while its image is entirely beyond the viewport. It is not another slot.
  const visibleFringe = nodes.map(n=>({...n,bounds:[...n.bounds]}));
  visibleFringe[3].bounds=[1870,369,1920,558];
  visibleFringe[4]={...visibleFringe[4],bounds:[1870,524,1920,557],visibleToUser:true,text:"APKPure"};
  visibleFringe.push(node(3,[1934,369,1920,519],owner+":id/item_icon","",false));
  assert.deepEqual(launcherItems(visibleFringe,visibleFringe[0]).map(item=>item.label),["Cloud Space"]);
  const hiddenFringe=visibleFringe.map(n=>({...n}));
  hiddenFringe[3].visibleToUser=false; hiddenFringe[4].visibleToUser=false;
  assert.deepEqual(launcherItems(hiddenFringe,hiddenFringe[0]).map(item=>item.label),["Cloud Space"]);
  malformed(copy=>{copy.push({...copy[4],visibleToUser:true});});
  malformed(copy=>{copy[4].text="";});
  const fullEdge=visibleFringe.map(n=>({...n,bounds:[...n.bounds]}));
  fullEdge[3].bounds=[1641,369,1920,558]; fullEdge[4].bounds=[1641,524,1920,557];
  assert.deepEqual(launcherItems(fullEdge,fullEdge[0]).map(item=>item.label),["Cloud Space","APKPure"]);
  const leftFringe = nodes.map(n=>({...n})); leftFringe[3]={...leftFringe[3],bounds:[145,125,194,324]}; leftFringe[4]={...leftFringe[4],bounds:[145,280,189,323]};
  assert.deepEqual(launcherItems(leftFringe,leftFringe[0]).map(item=>item.label),["Cloud Space"]);
  malformed(copy=>{copy[4].visibleToUser=true;copy.push({...copy[4]});});
  const lastRow = nodes.map(n=>({...n})); lastRow[3]={...lastRow[3],bounds:[753,369,1032,558]}; lastRow[4]={...lastRow[4],bounds:[753,524,1032,557],visibleToUser:true,text:"Voboost Fixture"};
  assert.deepEqual(launcherItems(lastRow,lastRow[0]).map(item=>item.label),["Cloud Space","Voboost Fixture"]);
});

test("a late multiwindow snapshot cannot revalidate nodes after a newer observation", async () => {
  const pending: Array<(result: any) => void> = [];
  const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(mkdtempSync(join(tmpdir(), "windows-race-")), "ui"), async () => new Promise(resolve => pending.push(resolve)), async () => {});
  const first = ui.snapshot(); await new Promise(resolve => setImmediate(resolve));
  const second = ui.snapshot(); await new Promise(resolve => setImmediate(resolve));
  const result = { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: windowModel() }) + "\n", stderr: "" };
  pending[1](result); const current = await second;
  pending[0](result); await assert.rejects(first, /stale/);
  const tap = ui.tap(keyboardKey(current, "a")); await new Promise(resolve => setImmediate(resolve));
  pending[2]({ ...result, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: "" }) + "\n" }); await tap;
});

test("post-install readiness uses narrow status observation without forensic Inspect", async () => {
  const { e } = await lifecycle({ readiness: "pending" });
  assert.equal(e.cases[0].status, "passed", e.cases[0].reason);
  const actions = e.actions.filter((a: any) => a.caseNumber === 1);
  const statuses = actions.filter((a: any) => a.args[0] === "--status");
  const first = statuses.find((a: any) => readFileSync(a.artifacts.find((x: any) => x.kind === "stdout").path, "utf8").includes("degraded"));
  const final = statuses.find((a: any) => a.id > first.id && readFileSync(a.artifacts.find((x: any) => x.kind === "stdout").path, "utf8").includes("healthy"));
  const reads = actions.filter((a: any) => a.id > first.id && a.id < final.id && a.args[0] === "--emulator-check").map((a: any) => JSON.parse(readFileSync(a.args[1], "utf8")).action);
  assert.deepEqual(reads, ["resident-status", "resident-status"]);
});

test("post-install readiness dispatcher waits only for current owned publication and requires fresh healthy CLI", async () => {
  const { e } = await lifecycle({ readiness: "pending" });
  assert.equal(e.cases[0].status, "passed", e.cases[0].reason);
  const actions = e.actions.filter((a: any) => a.caseNumber === 1);
  const first = actions.findIndex((a: any) => a.args[0] === "--status" && readFileSync(a.artifacts.find((r: any) => r.kind === "stdout").path, "utf8").includes("degraded"));
  const reads = actions.slice(first).filter((a: any) => a.args[0] === "--status" || a.args[0] === "--emulator-check").slice(0, 4);
  assert.deepEqual(reads.map((a: any) => a.args[0]), ["--status", "--emulator-check", "--emulator-check", "--status"]);
  assert.ok(reads.every((a: any) => a.args[0] === "--status" || JSON.parse(readFileSync(a.args[1], "utf8")).action === "resident-status"));
});

test("post-install readiness dispatcher bounds pending and rejects hard fault generation and boot changes", async () => {
  for (const readiness of ["exhausted", "fault", "generation", "boot", "manifest", "catalog", "startup", "panic", "killed", "degraded", "foreign", "quarantined", "unknown", "malformed", "duplicate", "transport", "healthy", "vm", "after-boot", "incomplete", "tail"]) {
    const { e, calls } = await lifecycle({ readiness });
    assert.equal(e.cases[0].status, "failed", readiness);
    assert.equal(calls.filter((a) => a.args[0] === "--install").length, 1);
    const reads = e.actions.filter((a: any) => a.caseNumber === 1 && a.args[0] === "--emulator-check" && JSON.parse(readFileSync(a.args[1], "utf8")).action === "resident-status");
    assert.equal(reads.length, readiness === "exhausted" ? 20 : 1, readiness);
  }
});

test("post-install readiness dispatcher preserves healthy coexist beside owned pending publication", async () => {
  const { e } = await lifecycle({ readiness: "coexist" });
  assert.equal(e.cases[0].status, "passed", e.cases[0].reason);
  const reads = e.actions.filter((a: any) => a.caseNumber === 1 && a.args[0] === "--status");
  assert.equal(reads.length, 3);
  assert.match(readFileSync(reads.at(-1).artifacts.find((a: any) => a.kind === "stdout").path, "utf8"), /"health":"healthy"/);
});

test("preparation foregrounds Home then Settings before its UI evidence and stops on failed Home", async () => {
  const { e } = await lifecycle({ focusResetRequired: true });
  assert.equal(e.preparation.status, "passed", e.preparation.reason);
  const actions = e.actions.filter((a: any) => e.preparation.actions.includes(a.id) && a.args[0] === "--emulator-check").map((a: any) => ({ action: a, request: JSON.parse(readFileSync(a.args[1], "utf8")) }));
  const home = actions.findIndex((a: any) => a.request.action === "key" && a.request.key === "home");
  const settings = actions.findIndex((a: any) => a.request.action === "open-settings");
  const png = actions.findIndex((a: any) => a.request.action === "screenshot");
  const snapshot = actions.findIndex((a: any) => a.request.action === "snapshot");
  assert.ok(home >= 0 && home < settings && settings < png && png < snapshot);
  const rejected = await lifecycle({ focusResetRequired: true, homeFailure: true });
  assert.equal(rejected.e.preparation.status, "failed");
  assert.equal(rejected.calls.filter((a: any) => a.args[0] === "--install").length, 0);
});

test("agent toggle input reacquires a fresh node after root resident observation", async () => {
  const directory = mkdtempSync(join(tmpdir(), "voboost-toggle-input-"));
  const actions: string[] = [];
  const source = windowModel().replaceAll("com.pateonavi.naviapp", "com.qinggan.app.setting");
  const root = /<node [^>]*window-id="17"[^>]*><\/node>/u.exec(source)![0];
  const label = root.replace('text=""', 'text="Font"').replace('index="0"', 'index="0"');
  const control = root.replace('index="0"', 'index="1"').replace('checkable="false"', 'checkable="true"').replace('checked="false"', 'checked="true"').replace('clickable="false"', 'clickable="true"');
  const panel = source.replace(root, root.replace('</node>', label + control + '</node>'));

  const resident = `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\nbootIdAfter=${boot}\nresident-status:\n/data/voboost/status.json\n{"schemaVersion":1,"injections":[{"id":"settings","documentGeneration":1791665325816374295}]}\n\n`;
  const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(directory, "ui"), async (invocation) => {
    const request = JSON.parse(readFileSync(invocation.args[1], "utf8")); actions.push(request.action);
    return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: request.action === "snapshot" ? panel : request.action === "resident-status" ? resident : "" }) + "\n", stderr: "" };
  }, async () => {});
  const old = (await ui.snapshot()).find(row => row.checkable)!;
  const input = await agentToggleInput(ui, "Font");
  assert.equal(input.before.injections[0].documentGeneration, "1791665325816374295");
  await assert.rejects(() => ui.tap(old), /stale/);
  assert.deepEqual(actions, ["snapshot", "resident-status", "snapshot"]);
  await ui.tap(input.check);
  assert.deepEqual(actions, ["snapshot", "resident-status", "snapshot", "tap"]);
});


test("Settings open idempotency requires actual focused owned selected tab", () => {
  const nodes = parseUi(windowModel().replaceAll("com.pateonavi.naviapp", "com.qinggan.app.setting").replace('text=""', 'text="Voboost"').replace('clickable="false"', 'clickable="true"').replace('checked="false"', 'checked="true"'));
  assert.equal(selectedSettingsTab(nodes), true);
  assert.equal(settingsTab(nodes).text, "Voboost");
  assert.equal(selectedSettingsTab(nodes.map(node => ({ ...node, checked: false }))), false);
  const music = nodes.map(node => ({ ...node, packageName: "com.qinggan.app.music" }));
  assert.equal(selectedSettingsTab(music), false);
  assert.throws(() => settingsTab(music), /matched 0/);
  assert.equal(selectedSettingsTab(nodes.map(node => ({ ...node, windowRef: { ...node.windowRef!, inputFocused: false } }))), false);
  assert.equal(selectedSettingsTab(nodes.map(node => ({ ...node, windowRef: { ...node.windowRef!, rootPackage: "com.qinggan.app.music" } }))), false);
});


test("install acceptance binds every armed and consumed identity field", () => {
  const armed = { schemaVersion: 1 as const, transactionId: "b".repeat(64), manifestSha256: "b".repeat(64), attemptToken: "c".repeat(64) };
  assert.deepEqual(installAcceptanceArmed(`install-acceptance-armed ${JSON.stringify(armed)}\n`, armed.manifestSha256), armed);
  assert.equal(installAcceptanceArmed("ordinary progress\n", armed.manifestSha256), null);
  assert.throws(() => installAcceptanceArmed(`install-acceptance-armed ${JSON.stringify(armed)}\n`.repeat(2), armed.manifestSha256), /duplicated/);
  const request = { ...armed, bootId: boot, rebootCount: 1 as const, decision: "accept" as const };
  const accepted = { ...request, state: "accepted", expiresMonotonicMs: "123456" };
  assert.equal(installAcceptanceReceipt(JSON.stringify(accepted), request).state, "accepted");
  for (const key of Object.keys(request)) {
    assert.throws(() => installAcceptanceReceipt(JSON.stringify({ ...accepted, [key]: "foreign" }), request), /identity/);
  }
  assert.throws(() => installAcceptanceReceipt(JSON.stringify({ ...accepted, extra: true }), request), /fields/);
  assert.throws(() => installAcceptanceReceipt(JSON.stringify({ ...accepted, expiresMonotonicMs: 123 }), request), /identity/);
});


test("failed independent intermediate boot proof sends reject and never accept", async () => {
  const { e, calls } = await lifecycle({ verifyFailure: true });
  const row = e.boots.find((row: any) => row.caseNumber === 1 && row.kind === "installer-1");
  assert.equal(row.status, "failed");
  assert.equal(e.cases[0].status, "failed");
  const requests = calls.filter(call => call.args[0] === "--emulator-check").map(call => JSON.parse(readFileSync(call.args[1], "utf8"))).filter(request => request.action === "install-acceptance");
  assert.deepEqual(requests.map(request => request.decision), ["reject"]);
  assert.equal(requests[0].bootId, row.after);
});

test("dialog gesture anchors the actual focused dialog while preserving target window", async () => {
  const source = windowModel();
  const first = /<window id="17"[\s\S]*?<\/window>/u.exec(source)![0];
  const base = first.replace('input-focused="true"', 'input-focused="false"').replace('active="true"', 'active="false"');
  const modal = first.replaceAll('id="17"', 'id="22"').replace('type="1"', 'type="3"').replace('text=""', 'text="Uninstall"').replace('clickable="false"', 'clickable="true"');
  const snapshot = source.replace(first, base + modal);
  const calls: any[] = [];
  const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(mkdtempSync(join(tmpdir(), "dialog-gesture-")), "ui"), async invocation => {
    const request = JSON.parse(readFileSync(invocation.args[1], "utf8")); calls.push(request);
    return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: request.action === "snapshot" ? snapshot : "" }) + "\n", stderr: "" };
  }, async () => {});
  await ui.tap(uniqueNode(await ui.snapshot(), node => node.text === "Uninstall"));
  assert.equal(calls[1].expectedWindow.id, 22);
  assert.equal(calls[1].expectedTarget.windowId, 22);
  assert.equal(calls[1].expectedTarget.windowType, 3);
});

const warmupFailure = "Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some(72) bytes=0 sha256=empty; stderr=ui-observation stage=nodes reason=deadline kind=java.lang.IllegalStateException";
test("first-boot UI warming recaptures same boot and retains failed evidence before acceptance", async () => {
  const { e } = await lifecycle({ uiDeadline: "once" });
  const boot = e.boots.find((b: any) => b.kind === "installer-1");
  assert.equal(boot.status, "passed", boot.reason);
  const captures = e.actions.filter((a: any) => boot.actions.includes(a.id) && a.args[0] === "--emulator-check").map((a: any) => ({ a, request: JSON.parse(readFileSync(a.args[1], "utf8")) }));
  assert.deepEqual(captures.filter(x => x.request.action === "snapshot").map(x => x.a.exitCode), [1, 0]);
  assert.equal(captures.filter(x => x.request.action === "screenshot").length, 2);
  assert.equal(new Set(captures.map(x => x.a.context?.bootId).filter(Boolean)).size, 1);
  assert.ok(captures.find(x => x.request.action === "snapshot" && x.a.exitCode === 1).a.artifacts.some((x: any) => x.kind === "stderr"));
});
test("first-boot UI warming exhaustion boot change and unsupported failures never accept", async () => {
  for (const uiDeadline of ["persistent", "boot", "unsupported"] as const) {
    const { e } = await lifecycle({ uiDeadline });
    const boot = e.boots.find((b: any) => b.kind === "installer-1");
    assert.notEqual(boot.status, "passed", uiDeadline);
    const requests = e.actions.filter((a: any) => a.args[0] === "--emulator-check").map((a: any) => JSON.parse(readFileSync(a.args[1], "utf8")));
    assert.equal(requests.filter(r => r.action === "install-acceptance" && r.decision === "accept").length, 0);
    assert.ok(requests.some(r => r.action === "install-acceptance" && r.decision === "reject"));
  }
});
test("first-boot UI warming refuses foreign ownership and exhausted budget before another capture", async () => {
  for (const foreign of [true, false]) {
    let attempts = 0, guards = 0;
    await assert.rejects(captureFirstBoot(async () => { attempts++; throw new UiWarmupDeadline("deadline"); }, async () => { if (++guards === 2 && foreign) throw new Error("foreign owner"); }, () => foreign), foreign ? /foreign owner/ : /deadline/);
    assert.equal(attempts, 1);
  }
  for (const stderr of [warmupFailure + " permission denied", warmupFailure + " foreign foreground", "malformed identity", "Accessibility deadline"]) {
    assert.equal(uiWarmupDeadline({ exitCode: 1, stdout: "", stderr }), false);
  }
});


test("install acceptance CLI action matches actual serde fixture without leaking native schema", () => {
  const fixture = JSON.parse(readFileSync(new URL("./fixtures/install-acceptance-cli.json", import.meta.url), "utf8"));
  const native = { schemaVersion: 1 as const, transactionId: fixture.transactionId, manifestSha256: fixture.manifestSha256, attemptToken: fixture.attemptToken, bootId: fixture.bootId, rebootCount: 1 as const, decision: "accept" as const };
  assert.deepEqual(installAcceptanceAction(native), fixture);
  validateRequest(installAcceptanceAction(native));
  assert.throws(() => validateRequest({ ...fixture, schemaVersion: 1 }));
  assert.throws(() => validateRequest({ ...fixture, foreign: true }));
  assert.equal(Object.hasOwn(installAcceptanceAction(native), "schemaVersion"), false);
  for (const decision of ["status", "accept", "reject"] as const)
    assert.deepEqual(installAcceptanceAction({ ...native, decision }), { ...fixture, decision });
});

// Shape reduced from retained cycle112 action140/action151: no OEM dumps copied.
test("second-screen readiness recognizes only the exact observed missing windows", () => {
  const failed = JSON.parse(readFileSync(new URL("./fixtures/cycle112-second-screen/verify-140.json", import.meta.url), "utf8"));
  const passed = JSON.parse(readFileSync(new URL("./fixtures/cycle112-second-screen/verify-151.json", import.meta.url), "utf8"));
  const expected = passed.report.checks.map((row: any) => ({ id: row.id, expected: row.expected }));
  const result = (wire: any, extra = {}) => ({ exitCode: 6, stdout: JSON.stringify(wire), stderr: "", ...extra });
  assert.equal(secondScreenStarting(result(failed), failed.profile, expected), true);
  assert.equal(secondScreenStarting(result(passed, { exitCode: 0 }), failed.profile, expected), false);
  for (const mutate of [
    (v: any) => { v.report.checks.find((r: any) => r.id === "routing:second:SecondHiboardDelegate").actual = [0]; },
    (v: any) => { v.report.checks.find((r: any) => r.id === "boot").passed = false; },
    (v: any) => { v.report.checks.pop(); },
    (v: any) => { v.report.checks.push(v.report.checks[0]); },
    (v: any) => { v.report.checks.find((r: any) => r.id.includes("SecondMainActivity")).actual = "real activity"; },
    (v: any) => { v.profile = "different"; },
  ]) {
    const changed = structuredClone(failed); mutate(changed);
    assert.equal(secondScreenStarting(result(changed), failed.profile, expected), false);
  }
  for (const extra of [{ timedOut: true }, { overflow: true }, { signal: "SIGTERM" }, { exitCode: 1 }])
    assert.equal(secondScreenStarting(result(failed, extra), failed.profile, expected), false);
});

test("dispatcher waits for actual-shaped second-screen snapshot without skipping full proof", async () => {
  const { e, calls } = await lifecycle({ secondScreen: "transient" });
  assert.equal(e.cases[0].status, "passed");
  const failed = e.actions.filter((a: any) => a.args[4] === "verify" && a.exitCode === 6);
  assert.equal(failed.length, 1);
  assert.ok(e.boots.some((b: any) => b.kind === "installer-1" && b.status === "passed" && b.screenshot));
  for (const secondScreen of ["persistent", "wrong-display", "boot"] as const) {
    const result = await lifecycle({ secondScreen });
    assert.notEqual(result.e.cases[0].status, "passed", secondScreen);
    assert.equal(result.calls.filter((i: any) => i.args[0] === "--emulator-install-acceptance" && JSON.parse(readFileSync(i.args[1], "utf8")).decision === "accept").length, 0);
  }
});

const closedBootResult = () => ({ exitCode: 1, stdout: "", stderr: "Emulator acceptance failed: boot-observation process failed: exit=Some(1) stdout_bytes=0 stdout_kind=empty stderr_bytes=14 stdout_sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 stderr_sha256=8f0acf19b9cbb9670c5e483a0fd471ca6603474e6fc4558565f14ba5cefe4c57 stderr=error: closed\n" });
test("closed boot diagnostic preserves captured Rust formatter bytes and digests", () => {
  const raw = closedBootResult();
  assert.equal(raw.stderr, readFileSync(new URL("./fixtures/boot-closed-153-stderr.txt", import.meta.url), "utf8"));
  assert.ok(raw.stderr.includes(`stdout_sha256=${createHash("sha256").update("").digest("hex")}`));
  assert.ok(raw.stderr.includes(`stderr_sha256=${createHash("sha256").update("error: closed\n").digest("hex")}`));
  for (const altered of [raw.stderr.replace("8f0acf", "9f0acf"), raw.stderr.replace("e3b0c4", "f3b0c4"), raw.stderr.replace("stderr_bytes=14", "stderr_bytes=13"), raw.stderr.replace(/ stdout_sha256=[a-f0-9]+ stderr_sha256=[a-f0-9]+/, "")]) {
    assert.equal(installerClosedPending({ ...raw, stderr: altered }, "boot-observation", "emulator-5562", true, 120, 1), false);
  }
});
test("exact closed transport pending is restricted to live authorized installer reboot and existing deadline", () => {
  const r = closedBootResult();
  const match = (v: any = r, active = true, action = "boot-observation", deadline: number | null = 120, now = 1) => installerClosedPending(v, action, "emulator-5562", active, deadline, now);
  assert.equal(match(), true);
  assert.equal(match({ ...r, stdout: "[11:29:30.895 INFO] Connecting to the head unit over USB\n[11:29:37.487 INFO] Connected to the head unit: emulator-5562\n" }), true);
  assert.equal(match(r, false), false);
  for (const action of ["inspect", "snapshot", "resident-status", "install"]) assert.equal(match(r, true, action), false);
  for (const deadline of [null, 0, 1]) assert.equal(match(r, true, "boot-observation", deadline), false);
  for (const v of [{ ...r, exitCode: 0 }, { ...r, exitCode: null }, { ...r, signal: "SIGTERM" }, { ...r, timedOut: true }, { ...r, overflow: true }, { ...r, retained: { overflow: true } }, { ...r, stdout: "unexpected\n" }, { ...r, stdout: '{"schemaVersion":1}\n' }, { ...r, stderr: r.stderr.replace("stdout_bytes=0", "stdout_bytes=1") }, { ...r, stderr: r.stderr.replace("empty", "boot-prefix") }, { ...r, stderr: r.stderr.replace("error: closed", "permission denied") }, { ...r, stderr: r.stderr + "extra\n" }, { ...r, stderr: "boot-observation guard failed: root\n" }]) assert.equal(match(v), false);
  assert.equal(match({ ...r, stdout: "[11:29:30.895 INFO] Connecting to the head unit over USB\n[11:29:37.487 INFO] Connected to the head unit: emulator-5563\n" }), false);
  assert.equal(offlineObservation(r), false, "new diagnostic must not become a global retry");
});
test("exact closed failure is retained then fresh completed boot is required; later boot and deadline remain failures", async () => {
  const raw = closedBootResult(), retained: any[] = [];
  let reads = 0;
  const read = async () => {
    if (++reads === 1) {
      retained.push(raw);
      if (installerClosedPending(raw, "boot-observation", "emulator-5562", true, 120, 1)) throw new ObservationPending("closed");
      throw new Error("not pending");
    }
    return { boot: "fresh", bootCompleted: true, rebootCount: 1 };
  };
  assert.deepEqual(await installerBootObservation(1, "old", read, async () => {}), { boot: "fresh", bootCompleted: true, rebootCount: 1 });
  assert.equal(reads, 2); assert.deepEqual(retained, [raw]);
  reads = 0;
  await assert.rejects(() => installerBootObservation(1, "old", async () => {
    if (++reads === 1) throw new ObservationPending("closed");
    return { boot: "later", bootCompleted: true, rebootCount: 2 };
  }, async () => {}), /later boot cannot substitute/);
  await assert.rejects(() => installerBootObservation(1, "old", async () => {
    if (installerClosedPending(raw, "boot-observation", "emulator-5562", true, 120, 120)) throw new ObservationPending("closed");
    throw new Error("first-boot UI capture budget exhausted");
  }, async () => {}), /budget exhausted/);
});

test("public dispatcher retains exact closed reboot failure and requires fresh same-boot verification before acceptance", async () => {
  const active = await lifecycle({ closedTransport: "active" });
  const first = active.e.boots.find((row: any) => row.kind === "installer-1" && row.caseNumber === 1);
  assert.equal(first?.status, "passed", first?.reason);
  const failures = active.e.actions.filter((row: any) => row.artifacts.some((artifact: any) => artifact.kind === "stderr" && readFileSync(artifact.path, "utf8") === closedBootResult().stderr));
  assert.equal(failures.length, 1); assert.equal(failures[0].exitCode, 1);
  assert.ok(first.actions.includes(failures[0].id));
  assert.ok(first.after && first.verifiedBoot === first.after);
  assert.equal(active.calls.filter((call) => call.args[0] === "--install").length, 2);
  const inactive = await lifecycle({ closedTransport: "inactive" });
  assert.equal(inactive.e.preparation.status, "failed");
  assert.equal(inactive.calls.filter((call) => call.args[0] === "--install").length, 0);
});

test("actual composed cache recovery boot is capture-only under uninstall writer lock", async () => {
  const {e, heldActions, recoveryAck} = await lifecycle({cacheRecovery:true, preparation:"emergency"});
  assert.equal(recoveryAck.decision,"accept");
  assert.ok(heldActions.includes("screenshot"));
  assert.ok(heldActions.includes("snapshot"));
  assert.ok(!heldActions.includes("key"));
  assert.ok(!heldActions.includes("open-settings"));
  const boot=e.boots.find((row:any)=>row.kind==="cache-recovery");
  assert.equal(boot.status,"passed",boot.reason);
  assert.equal(boot.after,recoveryAck.bootId);
  assert.equal(boot.verifiedBoot,recoveryAck.bootId);
  assert.ok(boot.actions.some((id:string)=>e.actions.find((row:any)=>row.id===id)?.args[4] === "verify"));
  assert.equal(e.preparation.status,"passed",e.preparation.reason);
});

test("post-install dispatcher admits typed persistent and native diagnostics while rejecting unknown or malformed additions", async () => {
 const accepted = await lifecycle({ readiness: "pending", residentDiagnostics: "typed" });
 assert.equal(accepted.e.cases[0].status, "passed", accepted.e.cases[0].reason);
 for (const residentDiagnostics of ["unknown", "malformed"] as const) {
  const rejected = await lifecycle({ readiness: "pending", residentDiagnostics });
  assert.equal(rejected.e.cases[0].status, "failed", residentDiagnostics);
 }
});

test("vehicle root authority selects signed SystemService among active FEV roles", () => {
  const id = "vehicle-drive-forced-ev", process = "com.qinggan.systemservice";
  const root = { id, process, state: "active", documentGeneration: "9007199254740993" };
  const ui = { id, process: "com.qinggan.app.vehiclesetting", state: "active", details: { armed: false } };
  const catalog = [{ id, targets: [{ process }, { process: ui.process }] }];
  assert.equal(vehicleRootTarget([ui, root], catalog, "fev"), root);
  assert.equal(vehicleRootTarget([root, ui], catalog, "fev"), root);
  assert.throws(() => vehicleRootTarget([ui], catalog, "fev"), /unique active signed vehicle root target/);
  assert.throws(() => vehicleRootTarget([{ ...root, process: "foreign" }], catalog, "fev"), /unique active signed vehicle root target/);
  assert.throws(() => vehicleRootTarget([root, { ...root }], catalog, "fev"), /unique active signed vehicle root target/);
  assert.throws(() => vehicleRootTarget([root], [{ id, targets: [{ process: ui.process }] }], "fev"), /catalog does not include root state owner/);
  assert.throws(() => vehicleRootTarget([{ ...root, state: "waiting" }, ui], catalog, "fev"), /unique active signed vehicle root target/);
  const pw = { ...root, id: "vehicle-drive-pedestrian-warning" };
  assert.equal(vehicleRootTarget([ui, pw], [{ id: pw.id, targets: [{ process }] }], "pw"), pw);
  assert.throws(() => vehicleRootTarget([root], [{ id: pw.id, targets: [{ process }] }], "pw"), /unique active signed vehicle root target/);
});

 test("public dispatcher retries actual cycle121 missing windows during held recovery boot and still requires full verification", async () => {
  const {e, recoveryAck} = await lifecycle({cacheRecovery:true, preparation:"emergency", secondScreenRecovery:true, secondScreen:"transient"});
  assert.equal(recoveryAck.decision, "accept");
  const row = e.boots.find((b: any) => b.kind === "cache-recovery");
  assert.equal(row?.status, "passed");
  const verify = e.actions.filter((a: any) => row.actions.includes(a.id) && a.args[4] === "verify");
  assert.equal(verify[0].exitCode, 6);
  assert.equal(verify.at(-1).exitCode, 0);
  assert.equal(row.after, row.verifiedBoot);
});
 test("cycle121 missing-window readiness rejects foreign failures and admits only exact known subsets", () => {
  const failed = JSON.parse(readFileSync(new URL("./fixtures/cycle121-second-screen/verify-1308.json", import.meta.url), "utf8"));
  const expected = failed.report.checks.map((r: any) => ({id:r.id, expected:r.expected}));
  const result = (wire: any) => ({exitCode:6, stdout:JSON.stringify(wire), stderr:""});
  assert.equal(secondScreenStarting(result(failed), failed.profile, expected), true);
  const subset = structuredClone(failed);
  const row = subset.report.checks.find((r: any) => r.id === "routing:second:SecondHiboardDelegate");
  Object.assign(row,{passed:true,actual:[1],message:null});
  assert.equal(secondScreenStarting(result(subset), failed.profile, expected), true);
  for(const mutate of [
    (w:any) => {w.report.checks.find((r:any)=>r.id === "routing:second:SecondScreenNavigationBar").message="different failure";},
    (w:any) => {w.report.checks.find((r:any)=>r.id === "selinux").actual="Permissive";},
    (w:any) => {w.report.checks.find((r:any)=>r.id === "routing:main:MainScreenNavigationBar").actual=[1];},
    (w:any) => {w.report.checks.find((r:any)=>r.id.includes("SecondMainActivity")).actual=w.report.checks.find((r:any)=>r.id.includes("SecondMainActivity")).actual.replace("mDisplayId=1", "mDisplayId=0");},
  ]) { const wire=structuredClone(failed); mutate(wire); assert.equal(secondScreenStarting(result(wire),failed.profile,expected),false); }
});

 test("public recovery dispatcher rejects missing-window timeout misrouting and changed boot", async () => {
  for (const secondScreen of ["persistent", "wrong-display", "boot"] as const) {
    const {e, recoveryAck} = await lifecycle({cacheRecovery:true, preparation:"emergency", secondScreenRecovery:true, secondScreen});
    assert.equal(recoveryAck.decision,"reject",secondScreen);
    assert.notEqual(e.boots.find((b:any)=>b.kind === "cache-recovery")?.status,"passed",secondScreen);
  }
});

test("first boot deadline retries only the exact selected USB connection log envelope", async () => {
  const stdout = "[04:24:23.181 INFO] Connecting to the head unit over USB\n[04:24:23.299 INFO] Connected to the head unit: emulator-5562\n";
  const failure = { exitCode: 1, stdout, stderr: warmupFailure };
  assert.equal(uiWarmupDeadline(failure, "emulator-5562"), true);
  assert.equal(uiWarmupDeadline(failure), false);
  for (const foreign of [stdout + "unexpected\n", stdout + stdout, stdout.replace("5562", "5564"), stdout.replace("04:24", "99:24"), stdout.replace("Connecting", "Installing"), JSON.stringify({body: ""})])
    assert.equal(uiWarmupDeadline({...failure, stdout: foreign}, "emulator-5562"), false);
  for (const mutation of [{timedOut: true}, {signal: "SIGTERM"}, {overflow: true}, {stderr: warmupFailure + " permission denied"}])
    assert.equal(uiWarmupDeadline({...failure, ...mutation}, "emulator-5562"), false);
  let attempts = 0, guards = 0;
  await captureFirstBoot(async () => { if (++attempts === 1 && uiWarmupDeadline(failure, "emulator-5562")) throw new UiWarmupDeadline("retained first boot failure"); }, async () => { guards++; }, () => true);
  assert.equal(attempts, 2); assert.equal(guards, 3);
});

test("post-install readiness admits actual producer endpoint strings and rejects malformed endpoint", async () => {
  const accepted = await lifecycle({readiness:"pending",residentEndpoint:"accepted"});
  assert.equal(accepted.e.cases[0].status, "passed", accepted.e.cases[0].reason);
  for (const residentEndpoint of ["numeric-document", "unknown-field"] as const) {
    const rejected = await lifecycle({readiness:"pending",residentEndpoint});
    assert.equal(rejected.e.cases[0].status, "failed", residentEndpoint);
  }
});

test("native reboot closed read is bounded, ownership guarded and never replays mutation", async () => {
  let now = 0, reads = 0, guards = 0;
  const value = { boot: "fresh", bootCompleted: true };
  const result = await nativeBootReadback(async () => { reads++; if (reads === 1) { assert.equal(installerClosedPending(closedBootResult(), "boot-observation", "emulator-5562", true, 10, now), true); throw new ObservationPending("closed"); } return value; }, async () => { guards++; }, async () => { now++; }, 10, () => now);
  assert.equal(result, value); assert.equal(reads, 2); assert.equal(guards, 3);
  await assert.rejects(() => nativeBootReadback(async () => { throw Error("permission denied"); }, async () => {}, async () => {}, 10, () => 0), /permission denied/);
  await assert.rejects(() => nativeBootReadback(async () => { throw new ObservationPending("closed"); }, async () => {}, async () => { now = 10; }, 10, () => now), /deadline/);
  await assert.rejects(() => nativeBootReadback(async () => value, async () => { throw Error("lease changed"); }, async () => {}, 20, () => 0), /lease changed/);
});

test("native boot readiness waits for completion of the captured boot within one deadline", async () => {
  let now = 0, reads = 0;
  const complete = { boot: "new", bootCompleted: true };
  const invoke = (read: any, after = "new") => nativeBootReady(read, "old", after, async () => {}, async () => { now++; }, 3, () => now);
  assert.equal(await invoke(async () => ++reads === 1 ? { boot: "new", bootCompleted: false } : complete), complete);
  assert.equal(reads, 2);
  now = 0; reads = 0;
  await assert.rejects(() => invoke(async () => { reads++; return { boot: "new", bootCompleted: false }; }), /deadline/);
  assert.equal(reads, 3);
  now = 0; reads = 0;
  await assert.rejects(() => invoke(async () => ++reads === 1 ? { boot: "new", bootCompleted: false } : { boot: "third", bootCompleted: true }), /boot identity/);
  await assert.rejects(() => invoke(async () => { throw Error("fatal permission denied"); }), /fatal permission denied/);
});

test("HOME capture proof retains three closed receipt references without accepting arbitrary evidence",()=>{
 const schema=JSON.parse(readFileSync(new URL("../../release/emulator-runtime-evidence.schema.json",import.meta.url),"utf8"));
 const validate=new Ajv2020({strict:false}).compile(schema.$defs.boot.properties.homeProof);
 assert.equal(validate({before:1,capture:2,after:3}),true);
 for(const value of [{before:1,capture:2},{before:1,capture:0,after:3},{before:1,capture:2,after:3,treeVerified:true}]) assert.equal(validate(value),false);
});

test("drawer switch reacquires semantic target only after definitive missing window",async()=>{
 const node=(id:number,selected=false)=>({packageName:"com.qinggan.app.launcher",resource:"com.qinggan.app.launcher:id/force_ev_switcher",enabled:true,visibleToUser:true,className:"android.view.View",selected,windowRef:{id,type:3,layer:0,bounds:[0,0,1224,720],rootPackage:"com.qinggan.app.launcher",rootStatus:"available"}} as any);
 const old=node(24),fresh=node(25),window={id:24,type:3,layer:0,bounds:{x:0,y:0,width:1224,height:720}};
 const rejection=()=>new NativeInputRejected("r","o","missing","TEST_INPUT_STALE",{reason:"window_missing",expected_window:window});
 let taps:any[]=[],reads=0;
 await tapDrawerSwitch({tap:async n=>{taps.push(n);if(taps.length===1) throw rejection();}},old,async()=>{reads++;return fresh;},100,()=>1);
 assert.deepEqual(taps,[old,fresh]);assert.equal(reads,1);
 for(const failure of [new Error("delivered or unknown"),new NativeInputRejected("r","o","source","TEST_INPUT_STALE",{reason:"source_changed"}),new NativeInputRejected("r","o","wrong","TEST_INPUT_STALE",{reason:"window_missing",expected_window:{...window,id:99}})]) {
  reads=0;taps=[];
  await assert.rejects(tapDrawerSwitch({tap:async n=>{taps.push(n);throw failure;}},old,async()=>{reads++;return fresh;},100,()=>1));
  assert.equal(taps.length,1);assert.equal(reads,0);
 }
 for(const changed of [old,node(25,true),{...fresh,packageName:"foreign"},{...fresh,className:"foreign"}]) {
  taps=[];await assert.rejects(tapDrawerSwitch({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>changed,100,()=>1));assert.equal(taps.length,1);
 }
 taps=[];await assert.rejects(tapDrawerSwitch({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>fresh,100,()=>1));assert.equal(taps.length,2);
 taps=[];await assert.rejects(tapDrawerSwitch({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>{throw new Error("drawer reacquisition crossed boot identity");},100,()=>1),/crossed boot/);assert.equal(taps.length,1);
 taps=[];reads=0;await assert.rejects(tapDrawerSwitch({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>{reads++;return fresh;},1,()=>1));assert.equal(reads,0);
});

test("drawer held switch boot fences do not invalidate InstallerUi observation",()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const start=source.indexOf("    const tapSwitch = async");
 const body=source.slice(start,source.indexOf("    const drawerCheck = async",start)).replace(/\/\/[^\n]*/g,"");
 assert.equal((body.match(/await probe\(true\)/g)||[]).length,2);
 assert.doesNotMatch(body,/bootObservation\(|inspect\(|ui\.call\(/);
 assert.match(body,/drawer reacquisition crossed boot identity/);
});

test("actual InstallerUi rejects diagnostic-invalidated drawer node and accepts direct boot read",async()=>{
 const directory=mkdtempSync(join(tmpdir(),"drawer-node-lifetime-"));
 const owner="com.qinggan.app.launcher";
 const makeNode=()=>({text:"",packageName:owner,resource:owner+":id/force_ev_switcher",className:"android.view.View",enabled:true,visibleToUser:true,selected:false,bounds:[669,172,750,214],windowRef:{capture:{},id:24,type:3,layer:0,active:true,inputFocused:true,rootStatus:"available",rootPackage:owner,bounds:[0,0,1224,720],scope:"default-display",captureEnd:1,title:""}} as any);
 let dispatches=0;
 const ui=new InstallerUi("/package/voboost-installer","emulator-5562",join(directory,"ui"),async()=>({exitCode:0,stdout:JSON.stringify({schemaVersion:1,serial:"emulator-5562",body:"boot"})+"\n",stderr:""}),async()=>{},async()=>[makeNode()],async()=>{dispatches++;return {};});
 const stale=(await ui.snapshot())[0];
 await ui.call({action:"boot-observation"});
 await assert.rejects(tapDrawerSwitch(ui,stale,async()=>{throw new Error("must not reacquire local stale guard");},100,()=>1),/stale or foreign UI node/);
 assert.equal(dispatches,0);
 const current=(await ui.snapshot())[0];
 // A direct root/Enforcing read is outside InstallerUi.callObserved.
 const readBoot=async()=>"same-boot";assert.equal(await readBoot(),"same-boot");
 await tapDrawerSwitch(ui,current,async()=>{throw new Error("no rejection");},100,()=>1);
 assert.equal(dispatches,1);
 await assert.rejects(ui.tap(current),/stale or foreign UI node/);
});

// Exercise the actual closed-over navigation implementation with a deterministic
// transport. No alternate implementation of the readback state machine.
const settings185Source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const settings185Start = settings185Source.indexOf("  const open = async () => {");
const settings185End = settings185Source.indexOf("\n  const ", settings185Start + 20);
const settings185Block = settings185Source.slice(settings185Start, settings185End);
const settings185Executable = stripTypeScriptTypes(settings185Block, { mode: "strip" });
const Settings185AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;

const settings185Tab = (checked = false, focused = true, pane = false) => ({
  packageName: "settings", text: "Voboost", clickable: true, visibleToUser: true, checked, pane,
  windowRef: { type: 1, rootPackage: "settings", active: focused, inputFocused: focused },
});
async function runSettings185(frames: any[][], changeBootAt = Infinity, powerOverride: any = {}) {
  let reads = 0, taps = 0, recovery = 0, bootReads = 0;
  const fn = new Settings185AsyncFunction("ui", "bootObservation", "ownership", "inspect", "selectedSettingsTab", "focusedApplication", "settingsForeground", "SETTINGS", "native", "hiddenVoboostMenuScroll", "Unavailable", `let settingsNavigationDeadline; let firstBootCaptureDeadline; ${settings185Executable}; await open();`);
  const promise = fn({ snapshot: async () => frames[Math.min(reads++, frames.length - 1)], tap: async () => { taps++; }, call: async (request: any) => { if (request.action === "key") recovery++; } },
    async () => ({ bootCompleted: true, boot: ++bootReads >= changeBootAt ? "other" : "same" }), async () => {},
    async () => ({ boot: "same", bootCompleted: true, body: "  mCurrentFocus=Window{abc u0 com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.SecondMainActivity}" }),
    (nodes: any[]) => nodes.some(n => n.checked && n.pane && n.windowRef.active && n.windowRef.inputFocused),
    (n: any) => n.windowRef.active && n.windowRef.inputFocused,
    async () => { throw new Error("foreground unavailable"); }, "settings",
    { system: async () => { if (powerOverride.fail) throw new Error("private transport details"); return {
      boot_id: powerOverride.boot ?? "same", value: { boot_id: powerOverride.valueBoot ?? "same",
        power: { status: "ready", complete: true, display_ready: true, wakefulness: "Awake", display_state: "ON", ...powerOverride } },
    }; } }, hiddenVoboostMenuScroll, class SettingsPowerUnavailable extends Error {});
  return { promise, counts: () => ({ reads, taps, recovery }) };
}
test("focused unchecked readback waits for real checked pane without replay", async () => {
  const r = await runSettings185([[settings185Tab()], [settings185Tab()], [settings185Tab()], [settings185Tab(true)], [settings185Tab(true, true, true)]]);
  await r.promise;
  assert.deepEqual(r.counts(), { reads: 5, taps: 1, recovery: 0 });
});
test("frozen unchecked pane exhausts reads and never becomes focus recovery", async () => {
  const r = await runSettings185([[settings185Tab()]]);
  await assert.rejects(r.promise, /selection not observed/);
  // The production snapshot budget is 32; exhaustion must never replay input.
  assert.deepEqual(r.counts(), { reads: 32, taps: 1, recovery: 0 });
});
test("selection readback fails on foreign focused target without replay", async () => {
  const r = await runSettings185([[settings185Tab()], [settings185Tab()], [{ ...settings185Tab(), packageName: "foreign" }]]);
  await assert.rejects(r.promise, /lost focused/);
  assert.equal(r.counts().taps, 1);
});
test("selection readback preserves boot authority", async () => {
  const r = await runSettings185([[settings185Tab()]], 2);
  await assert.rejects(r.promise, /crossed boot/);
  // The fresh boot fence precedes target acquisition; no input may be sent.
  assert.equal(r.counts().taps, 0);
});
test("only a genuinely selected unfocused pane uses known secondary focus recovery", async () => {
  const r = await runSettings185([[settings185Tab()], [settings185Tab()], [settings185Tab(true, false, true)], [settings185Tab(true, true, true)]]);
  await r.promise;
  assert.deepEqual(r.counts(), { reads: 4, taps: 1, recovery: 1 });
});

test("Settings power preflight rejects asleep, incomplete, foreign boot before any UI action", async () => {
  for (const override of [
    { wakefulness: "Asleep", display_state: "OFF" }, { complete: false },
    { display_ready: false }, { status: "unavailable" },
    { boot: "other" }, { valueBoot: "other" },
  ]) {
    const r = await runSettings185([[settings185Tab()]], Infinity, override);
    await assert.rejects(r.promise, error => error.constructor.name === "SettingsPowerUnavailable" && /power preflight rejected/.test(error.message));
    assert.deepEqual(r.counts(), { reads: 0, taps: 0, recovery: 0 });
  }
});
test("Settings power preflight transport failure is typed and sanitizes transport details", async () => {
  const r = await runSettings185([[settings185Tab()]], Infinity, { fail: true });
  await assert.rejects(r.promise, error => error.constructor.name === "SettingsPowerUnavailable" && error.message === "Settings navigation power preflight unavailable");
  assert.deepEqual(r.counts(), { reads: 0, taps: 0, recovery: 0 });
});


test("case failure stack artifact excludes messages, requests and external frames", () => {
  const error = new Error("secret token request body");
  const root = decodeURIComponent(new URL("../../", import.meta.url).pathname);
  error.stack = ["Error: secret token", `    at privateFunction (file://${root}release/emulator-gate.ts:123:45)`,
    "    at external (/unrelated/secret.ts:1:2)", `    at injected (file://${root}release/evil?token.ts:1:2)`,
    `    at safe (file://${root}tools/emulator/installer-ui.ts:8:9)`].join("\n");
  assert.deepEqual(caseFailureFrames(error), ["release/emulator-gate.ts:123:45", "tools/emulator/installer-ui.ts:8:9"]);
  assert.deepEqual(caseFailureFrames("private data"), []);
  error.stack = "Error\n" + Array.from({length:80}, () => `    at safe (file://${root}release/emulator-gate.ts:1:2)`).join("\n");
  assert.equal(caseFailureFrames(error).length,32);
  const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
  assert.ok(source.includes("case-${number}-stack-${stackSha}.json"));
  assert.ok(source.includes("artifacts: [stackPath]"));
});


test("drawer mode card reacquires only unchanged semantic mode after correlated non-dispatch",async()=>{
 const owner="com.qinggan.app.launcher";
 const nodes=(id:number,text="Electric")=>[
  {packageName:owner,resource:owner+":id/rl_root",className:"android.widget.RelativeLayout",clickable:true,enabled:true,visibleToUser:true,parent:null,bounds:[439,129,779,289],windowRef:{id,type:3,layer:0,bounds:[0,0,1224,720],rootPackage:owner,rootStatus:"available"}},
  {packageName:owner,resource:owner+":id/tv_title",className:"android.widget.TextView",text,visibleToUser:true,parent:0,bounds:[450,140,600,180]}
 ] as any;
 const old=drawerModeCard(nodes(24)),fresh=drawerModeCard(nodes(25));
 const rejection=()=>new NativeInputRejected("r","o","missing","TEST_INPUT_STALE",{reason:"window_missing",expected_window:{id:24,type:3,layer:0,bounds:{x:0,y:0,width:1224,height:720}}});
 let taps:any[]=[],reads=0;
 await tapDrawerModeCard({tap:async n=>{taps.push(n);if(taps.length===1)throw rejection();}},old,async()=>{reads++;return fresh;},100,()=>1);
 assert.deepEqual(taps,[old.node,fresh.node]);assert.equal(reads,1);
 for(const failure of [Object.assign(rejection(),{outcome:"delivered"}),Object.assign(rejection(),{retryable:true}),new Error("unknown dispatch"),new NativeInputRejected("r","o","blocked","TEST_INPUT_BLOCKED",{}),new NativeInputRejected("r","o","source","TEST_INPUT_STALE",{reason:"source_changed"}),new NativeInputRejected("r","o","uncorrelated","TEST_INPUT_STALE",{reason:"window_missing",expected_window:{id:99,type:3,layer:0}})]) {
  taps=[];reads=0;await assert.rejects(tapDrawerModeCard({tap:async n=>{taps.push(n);throw failure;}},old,async()=>{reads++;return fresh;},100,()=>1));assert.equal(taps.length,1);assert.equal(reads,0);
 }
 for(const changed of [old,drawerModeCard(nodes(25,"Hybrid")),{...fresh,node:{...fresh.node,className:"foreign"}},{...fresh,node:{...fresh.node,packageName:"foreign"}}]) {
  taps=[];await assert.rejects(tapDrawerModeCard({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>changed,100,()=>1));assert.equal(taps.length,1);
 }
 taps=[];await assert.rejects(tapDrawerModeCard({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>fresh,100,()=>1));assert.equal(taps.length,2);
 taps=[];await assert.rejects(tapDrawerModeCard({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>{throw Error("boot or epoch changed");},100,()=>1));assert.equal(taps.length,1);
 reads=0;await assert.rejects(tapDrawerModeCard({tap:async()=>{throw rejection();}},old,async()=>{reads++;return fresh;},1,()=>1));assert.equal(reads,0);
 let clock=1;taps=[];await assert.rejects(tapDrawerModeCard({tap:async n=>{taps.push(n);throw rejection();}},old,async()=>{clock=100;return fresh;},100,()=>clock));assert.equal(taps.length,1);
 assert.throws(()=>drawerModeCard(nodes(24,"Unknown")),/unique/);
 assert.throws(()=>drawerModeCard([...nodes(24),...nodes(25)]),/unique/);
});


test("actual drawer mode cycle checks boot before replay without invalidating UI observation",async()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const start=source.indexOf("    const cycle = async (raw: number, armed: boolean) => {");
 const body=source.slice(start,source.indexOf("    await drawerCheck(",start)).replace("const cycle = async (raw: number, armed: boolean)","const cycle = async (raw, armed)");
 assert.doesNotMatch(body,/ui\.tap\(|bootObservation\(|ui\.call\(/);
 const run=new (Object.getPrototypeOf(async function(){}).constructor)("probe","stableDrawer","drawerModeCard","tapDrawerModeCard","ui","drawerState","nativeInputTree",body+"return cycle(3,true);");
 const owner="com.qinggan.app.launcher",node={packageName:owner,resource:owner+":id/rl_root",className:"android.widget.RelativeLayout",clickable:true,enabled:true,visibleToUser:true,windowRef:{id:24,type:3,layer:0,bounds:[0,0,1224,720],rootPackage:owner,rootStatus:"available"}};
 const failure=()=>new NativeInputRejected("r","o","missing","TEST_INPUT_STALE",{reason:"window_missing",expected_window:{id:24,type:3,layer:0,bounds:{x:0,y:0,width:1224,height:720}}});
 for(const change of ["none","boot","source","observer"]) {
  const nativeInputTree={source_instance:"original",observer_pid:123,revision:1};
  let reads=0,taps=0,probes=0,states=0;
  const result=run(async()=>++probes===2 && change==="boot"?"new":"old",async()=>{reads++;nativeInputTree.revision++;if(reads===2 && change==="source")nativeInputTree.source_instance="new";if(reads===2 && change==="observer")nativeInputTree.observer_pid=456;return {node:reads===1?node:{...node,windowRef:{...node.windowRef,id:25}},caption:"Electric"};},x=>x,tapDrawerModeCard,{tap:async()=>{if(++taps===1)throw failure();}},async(raw,armed)=>{states++;assert.equal(raw,3);assert.equal(armed,true);return "confirmed";},nativeInputTree);
  if(change!=="none") {await assert.rejects(result,/crossed boot|observer epoch/);assert.equal(taps,1);assert.equal(states,0);}
  else {assert.equal(await result,"confirmed");assert.equal(taps,2);assert.equal(states,1);}
  assert.equal(reads,2);assert.equal(probes,2);
 }
});


test("drawer entry explicitly prepares observed OEM Home instead of assuming Settings owns a receiver",async()=>{
 const owner="com.qinggan.app.launcher";
 const frame=(ownerName=owner,active=true)=>{const w={rootPackage:ownerName,rootStatus:"available",scope:"default-display",type:1,active,inputFocused:active,bounds:[0,0,1920,720]};const nodes=[{parent:null,windowRef:w,packageName:ownerName,enabled:true,visibleToUser:true}] as any;nodes.windowRefs=[w];return nodes;};
 let calls:any[]=[],reads=0;
 await prepareDrawerHome({call:async q=>{calls.push(q);},snapshot:async()=>++reads===1?frame("com.qinggan.app.setting"):frame()} as any,async()=>"same",100,()=>1);
 assert.deepEqual(calls,[{action:"open-primary-launcher"}]);assert.equal(reads,2);
 for(const bad of [frame("foreign"),frame(owner,false),Object.assign(frame(),{windowRefs:[...frame().windowRefs,...frame().windowRefs]})]) {
  let clock=0,taps=0;await assert.rejects(prepareDrawerHome({call:async()=>{taps++;},snapshot:async()=>bad} as any,async()=>"same",3,()=>clock++),/not ready/);assert.equal(taps,1);
 }
 let probes=0;await assert.rejects(prepareDrawerHome({call:async()=>{},snapshot:async()=>frame()} as any,async()=>++probes===1?"old":"new",100,()=>1),/crossed boot/);
 await assert.rejects(prepareDrawerHome({call:async()=>{},snapshot:async()=>{throw Error("observer epoch changed");}} as any,async()=>"same",100,()=>1),/observer epoch/);
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const arm=source.slice(source.indexOf('await drawerCheck("fev-drawer-arm"'),source.indexOf('await drawerCheck("fev-drawer-hybrid"'));
 assert.ok(arm.indexOf("await toggle(VEHICLE.fev,true)")<arm.indexOf("await prepareDrawerHome("));
 assert.ok(arm.indexOf("await prepareDrawerHome(")<arm.indexOf("findSwitch(await stableDrawer())"));
});


test("accepted drawer gesture awaits a fenced power card without additional input",async()=>{
 const owner="com.qinggan.app.launcher";
 const frame=(card=false,focusedOwner=owner)=>{
  const w={id:4,rootPackage:owner,rootStatus:"available",scope:"default-display",type:3,active:true,inputFocused:true,bounds:[0,0,1920,720]};
  const nodes=(card?[{packageName:owner,resource:owner+":id/tv_title",text:"Electric",visibleToUser:true,windowRef:w}]:[]) as any;
  nodes.windowRefs=[focusedOwner===owner?w:{...w,rootPackage:focusedOwner}];return nodes;
 };
 let reads=0,inputs=0;
 const visible=frame(true);
 const ui={snapshot:async()=>++reads<3?frame():visible,call:async()=>{inputs++;}};
 assert.equal(await awaitDrawerPowerCard(ui as any,async()=>"same","same",100,()=>1),visible);
 assert.equal(reads,3);assert.equal(inputs,0);
 let clock=0;
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>frame()} as any,async()=>"same","same",4,()=>clock++),/not visible before deadline/);
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>frame(true,"foreign")} as any,async()=>"same","same",100,()=>1),/unexpected focused owner/);
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>frame(true)} as any,async()=>"new","same",100,()=>1),/crossed boot/);
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>{throw Error("observer epoch changed");}} as any,async()=>"same","same",100,()=>1),/observer epoch/);
 const invalid=frame(true);invalid[0].windowRef={...invalid[0].windowRef,rootStatus:"unavailable"};
 let invalidClock=0;
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>invalid} as any,async()=>"same","same",4,()=>invalidClock++),/not visible before deadline/);
 let boundedReads=0;
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>{boundedReads++;return frame();}} as any,async()=>"same","same",100,()=>1),/not visible before deadline/);
 assert.equal(boundedReads,64);
 let lateClock=0;
 await assert.rejects(awaitDrawerPowerCard({snapshot:async()=>{lateClock=100;return visible;}} as any,async()=>"same","same",100,()=>lateClock),/not visible before deadline/);
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const open=source.slice(source.indexOf("const openDrawer = async"),source.indexOf("const stableDrawer = async"));
 assert.equal((open.match(/action: "gesture"/g)??[]).length,1);
 assert.ok(open.includes("awaitDrawerPowerCard(ui"));
});

test("drawer mode operation excludes delayed preparation and admits one 6.2 second reacquisition",async()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const start=source.indexOf("    const cycle = async (raw: number, armed: boolean) => {");
 const body=source.slice(start,source.indexOf("    await drawerCheck(",start)).replace("const cycle = async (raw: number, armed: boolean)","const cycle = async (raw, armed)");
 const run=new (Object.getPrototypeOf(async function(){}).constructor)("probe","stableDrawer","drawerModeCard","tapDrawerModeCard","ui","drawerState","nativeInputTree","performance",body+"return cycle(4,true);");
 const owner="com.qinggan.app.launcher",node={packageName:owner,resource:owner+":id/rl_root",className:"android.widget.RelativeLayout",clickable:true,enabled:true,visibleToUser:true,windowRef:{id:26,type:3,layer:0,bounds:[0,0,1224,720],rootPackage:owner,rootStatus:"available"}};
 const rejection=()=>new NativeInputRejected("r","o","missing","TEST_INPUT_STALE",{reason:"window_missing",expected_window:{id:26,type:3,layer:0,bounds:{x:0,y:0,width:1224,height:720}}});
 for(const delay of [6200,10001]) {
  let clock=0,reads=0,taps=0,confirmed=0;
  const result=run(async()=>"same",async()=>{clock+=++reads===1?6200:delay;return {node:reads===1?node:{...node,windowRef:{...node.windowRef,id:28}},caption:"Hybrid"};},x=>x,(ui,initial,reacquire,deadline)=>tapDrawerModeCard(ui,initial,reacquire,deadline,()=>clock),{tap:async()=>{if(++taps===1)throw rejection();}},async()=>{confirmed++;return "confirmed";},{source_instance:"same",observer_pid:123},{now:()=>clock});
  if(delay===6200) {assert.equal(await result,"confirmed");assert.equal(taps,2);assert.equal(confirmed,1);}
  else {await assert.rejects(result,/deadline expired/);assert.equal(taps,1);assert.equal(confirmed,0);}
  assert.equal(reads,2);
 }
 let reads=0,taps=0;
 await tapDrawerModeCard({tap:async()=>{taps++;}},{node,caption:"Hybrid"} as any,async()=>{reads++;throw Error("must not reacquire accepted input");},10000,()=>1);
 assert.equal(taps,1);assert.equal(reads,0);
 await assert.rejects(tapDrawerModeCard({tap:async()=>{taps++;}},{node,caption:"Hybrid"} as any,async()=>{throw Error("must not read");},1,()=>1),/deadline expired before input/);
 assert.equal(taps,1);
});

test("fixture first publication is captured before route and drag work and completed only after both draws",()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const installed=source.indexOf('installed = await ui.call({ action: "install-fixture"');
 const capture=source.indexOf("const firstPublicationCapture=await launcherObservations()",installed);
 const routes=source.indexOf('await subcheck("launcher-entry-routes-after-install"',installed);
 const seek=source.indexOf("const firstPublishedObservation=await seekSecondaryCache",installed);
 const completed=source.indexOf("firstPublishedFixture = launcherFirstPublished",installed);
 const drag=source.indexOf('action: "drag", points',installed);
 assert.ok(installed<capture && capture<routes && routes<seek && seek<completed && completed<drag);
 assert.match(source.slice(completed,drag),/firstPublishedObservation\.canonicalArchives/);
 assert.match(source,/joinCacheWitness\(\{rows:retained\.logData\.diagnostics/);
});

test("secondary physical input provenance is closed and cannot claim a semantic fence",()=>{
 const schema=JSON.parse(readFileSync(new URL("../../release/emulator-runtime-evidence.schema.json",import.meta.url),"utf8"));
 const validate=new Ajv2020({strict:true,strictTypes:false}).compile(schema.$defs.action.properties.inputProvenance);
 const provenance={kind:"owned-profile-physical-swipe",targetWindowPreDispatchFenced:false};
 assert.equal(validate(provenance),true);
 for(const changed of [{...provenance,targetWindowPreDispatchFenced:true},{...provenance,kind:"semantic"},{...provenance,unknown:true}]) assert.equal(validate(changed),false);
});

// Canvas routing diagnostics remain distinct from visual acceptance.
{
const source=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
const start=source.indexOf('export function keyboardDiagnosticRequest');
const end=source.indexOf('\nexport function normalAbsence',start);
const text=stripTypeScriptTypes(source.slice(start,end).replaceAll('export function ','function '));
const {keyboardDiagnosticRequest,keyboardDiagnosticEvidence,functionalEvidence}=new Function('readNativeRequest','nativeCaptureAction','nativeTreeAction','focusedApplication','EXECUTION_ORDER','REGRESSION_ORDER','installedSemanticRequired','verificationContract',text+';return {keyboardDiagnosticRequest,keyboardDiagnosticEvidence,functionalEvidence};')(
 a=>a.request,(e,a)=>{assert.equal(a.validCapture,true);return {android_boot_id:'boot'};},(e,a)=>{assert.equal(a.validTree,true);return a.nodes;},n=>n.owned,[8,9,10,11,19],{input:[9,19,10,11],'lifecycle-input':[8,9,19,10,11],keyboards:[10,11]},s=>['input','lifecycle-input','keyboards'].includes(s),{});
const rect={x:0,y:400,width:1920,height:320};
function fixture(){
 const window={id:8,type:2,rootPackage:'com.qinggan.app.qgime',capture:'frame'};
 const nodes=[{packageName:'com.pateonavi.naviapp',className:'android.widget.EditText',owned:true,focused:true,password:false,enabled:true,visibleToUser:true,windowRef:{capture:'frame'}},{packageName:'com.qinggan.app.qgime',resource:'com.qinggan.app.qgime:id/alpha_view1',enabled:true,visibleToUser:true,windowRef:window,bounds:[0,400,1920,720],rawBounds:[0,400,1920,720]}];
 const tree=(id,start,end)=>({id,caseNumber:9,validTree:true,nodes:structuredClone(nodes),context:{bootId:'boot'},startedMonotonicNs:start,finishedMonotonicNs:end,args:[],artifacts:[]});
 const action={id:2,caseNumber:9,validCapture:true,nativeOperation:'session.screenshot',context:{kit:'kit',serial:'serial',runtime:{pid:42}},request:{profile:'profile',op:'session.screenshot',artifact_dir:'/project/.local/tmp/keyboard',screens:['primary'],rect},keyboardDiagnostic:{before:1,after:3},nativeAuthority:{output:'/retained/primary.png'},artifacts:[{kind:'png',path:'/retained/primary.png'}],args:['test'],startedMonotonicNs:'20',finishedMonotonicNs:'30'};
 return {voyemuProfile:'profile',identity:{package:'kit'},runtime:{serial:'serial',pid:42},imageCapture:'deferred',regressionScenario:'input',passed:false,cases:[],boots:[],actions:[tree(1,'1','10'),action,tree(3,'40','50')]};
}
test('only scoped exact primary crop dispatch is admitted',()=>{
 const request=fixture().actions[1].request, scope={directory:request.artifact_dir,rect};
 assert.equal(keyboardDiagnosticRequest(request,scope),true);
 for(const changed of [null,{directory:'/other',rect},{directory:request.artifact_dir,rect:{...rect,width:1919}}])assert.equal(keyboardDiagnosticRequest(request,changed),false);
 assert.equal(keyboardDiagnosticRequest({...request,screens:['secondary']},scope),false);
 assert.equal(keyboardDiagnosticRequest({...request,op:'session.ui'},scope),false);
});
test('keyboards admits only keyboard-en and keyboard-ru/en diagnostic cases',()=>{
 for(const number of [10,11]) {const e=fixture();e.regressionScenario='keyboards';for(const a of e.actions)a.caseNumber=number;assert.doesNotThrow(()=>functionalEvidence(e));}
 for(const number of [8,9,19]) {const e=fixture();e.regressionScenario='keyboards';for(const a of e.actions)a.caseNumber=number;assert.throws(()=>functionalEvidence(e),/keyboard diagnostic scope/);}
});
test('lifecycle-input admits the same owned keyboard diagnostic scope',()=>{
 const e=fixture();e.regressionScenario='lifecycle-input';assert.doesNotThrow(()=>functionalEvidence(e));assert.equal(e.passed,false);
});
test('lifecycle-input never admits keyboard captures in enablement case8',()=>{
 const e=fixture();e.regressionScenario='lifecycle-input';for(const action of e.actions)action.caseNumber=8;
 assert.throws(()=>functionalEvidence(e),/keyboard diagnostic scope/);
});
test('normal full run admits source-bound keyboard routing after completed enablement',()=>{
 const e=fixture();delete e.imageCapture;delete e.regressionScenario;
 e.cases=[8,9,10,11,19].map(number=>({number,status:number===8?'passed':'not-run',subchecks:[]}));
 assert.doesNotThrow(()=>keyboardDiagnosticEvidence(e,e.actions[1]));
 assert.equal(e.passed,false);
 for(const mutate of [
  value=>value.cases.pop(),
  value=>value.cases[4].number=9,
  value=>value.cases[0].status='not-run',
  value=>value.regressionScenario='launcher',
  value=>value.imageCapture='unknown',
  value=>value.actions[1].validCapture=false,
  value=>value.actions[2].context.bootId='other',
 ]) {
  const invalid=structuredClone(e);mutate(invalid);
  assert.throws(()=>keyboardDiagnosticEvidence(invalid,invalid.actions[1]));
 }
});
test('owned canvas diagnostic composes with deferred functionalEvidence without visual acceptance',()=>{
 const e=fixture(); assert.doesNotThrow(()=>functionalEvidence(e)); assert.equal(e.passed,false);
});
for(const [name,mutate] of [
 ['no diagnostic proof',e=>delete e.actions[1].keyboardDiagnostic],
 ['other scenario',e=>e.regressionScenario='startup'],
 ['other case',e=>e.actions[1].caseNumber=12],
 ['invalid capture authority',e=>e.actions[1].validCapture=false],
 ['invalid tree authority',e=>e.actions[0].validTree=false],
 ['different profile',e=>e.actions[1].request.profile='other'],
 ['different PID',e=>e.actions[1].context.runtime.pid=43],
 ['cross boot',e=>e.actions[2].context.bootId='other'],
 ['window drift',e=>e.actions[2].nodes[1].windowRef.id=9],
 ['clipped canvas',e=>e.actions[2].nodes[1].rawBounds[0]=1],
 ['different crop',e=>e.actions[1].request.rect={...rect,width:1919}],
 ['extra image',e=>e.actions[1].artifacts.push({kind:'png',path:'/retained/other.png'})],
 ['disguised extra image',e=>e.actions[1].artifacts.push({kind:'archive',path:'/retained/other.jpg'})],
 ['out of sequence',e=>e.actions[0].finishedMonotonicNs='21'],
 ['visual acceptance attempt',e=>{e.cases=[{number:9,status:'unavailable',subchecks:[{id:'panel-captures-en',status:'passed',artifacts:['/retained/primary.png']}]}];}],
 ['overall visual pass',e=>e.passed=true],
]) test(name+' is rejected',()=>{const e=fixture();mutate(e);assert.throws(()=>functionalEvidence(e));});

}


test("TBOX seek scrolls hidden OEM hardware row into view without using its invalid offscreen bounds", async () => {
  const base = { packageName: "com.qinggan.app.setting", resource: "com.qinggan.app.setting:id/kv_tbox_mpu_res_version", className: "android.widget.LinearLayout", enabled: true, clickable: true, windowRef: { type: 1, active: true, inputFocused: true, rootPackage: "com.qinggan.app.setting", rootStatus: "available", scope: "default-display" } };
  const hidden = { ...base, visibleToUser: false, bounds: [1182, 1311, 1849, 720] } as any;
  const shown = { ...base, visibleToUser: true, bounds: [1182, 313, 1849, 463] } as any;
  let reads = 0, scrolls = 0;
  assert.equal(await seekTboxRecoveryTarget(async () => ++reads < 3 ? [hidden] : [shown], async nodes => { assert.equal(nodes[0], hidden); scrolls++; }), shown);
  assert.equal(reads, 4); assert.equal(scrolls, 2);
  const moving = { ...shown, bounds: [1182, 627, 1849, 720] } as any;
  reads = 0;
  assert.equal(await seekTboxRecoveryTarget(async () => ++reads === 1 ? [moving] : [shown], async () => { throw new Error("must not scroll during visible settling"); }), shown);
  assert.equal(reads, 3);
  reads = 0; scrolls = 0;
  await assert.rejects(seekTboxRecoveryTarget(async () => { reads++; return [hidden]; }, async () => { scrolls++; }), /bounded System scrolling/);
  assert.equal(reads, 8); assert.equal(scrolls, 7);
  for (const bad of [{ ...shown, clickable: false }, { ...shown, enabled: false }, { ...shown, className: "android.widget.TextView" }]) {
    await assert.rejects(seekTboxRecoveryTarget(async () => [bad], async () => { throw new Error("must not scroll past invalid visible row"); }), /selector/);
  }
  await assert.rejects(seekTboxRecoveryTarget(async () => [shown, shown], async () => {}), /selector/);
});

import {keyboardCanvasNode,awaitKeyboardCanvas} from '../../release/emulator-gate.ts';
function keyboardAppearanceFixture() {
 const capture='capture';
 const editor:any={packageName:'com.pateonavi.naviapp',className:'android.widget.EditText',focused:true,password:false,enabled:true,visibleToUser:true,windowRef:{type:1,active:true,inputFocused:true,rootStatus:'available',rootPackage:'com.pateonavi.naviapp',capture}};
 const keyboard:any={packageName:'com.qinggan.app.qgime',resource:'com.qinggan.app.qgime:id/alpha_view1',enabled:true,visibleToUser:true,bounds:[0,368,1920,720],rawBounds:[0,368,1920,720],windowRef:{type:2,rootPackage:'com.qinggan.app.qgime',capture}};
 return {editor,keyboard};
}
test('keyboard appearance awaits observations without replaying input',async()=>{
 const f=keyboardAppearanceFixture();let now=0,reads=0;
 const k=await awaitKeyboardCanvas(async()=>{now+=100;return ++reads<3?[f.editor]:[f.editor,f.keyboard];},1000,()=>now);
 assert.equal(k,f.keyboard);assert.equal(reads,3);
});
test('keyboard absence stays bounded and unavailable without input',async()=>{
 const f=keyboardAppearanceFixture();let now=0,reads=0;
 await assert.rejects(awaitKeyboardCanvas(async()=>{reads++;now+=100;return [f.editor];},300,()=>now),/did not appear/);assert.equal(reads,3);
});
test('keyboard await never treats ambiguous editor or clipped foreign IME as pending',async()=>{
 const f=keyboardAppearanceFixture();assert.throws(()=>keyboardCanvasNode([f.editor,f.editor]),/unique focused editor/);
 assert.throws(()=>keyboardCanvasNode([f.editor,{...f.keyboard,rawBounds:[1,368,1920,720]}]),/unclipped/);
 assert.throws(()=>keyboardCanvasNode([f.editor,f.keyboard,f.keyboard]),/unclipped/);
});
test('actual canvas prefix callback consumes exact post-capture tree once without a TTL cache',async()=>{
 const gate=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
 const begin=gate.indexOf('},async()=>{',gate.indexOf('await reviewedKeyboardRoute(document.layouts'))+2;
 const end=gate.indexOf('},\n      {theme:document.theme',begin)+1;
 assert.ok(begin>1 && end>begin);
 const code=stripTypeScriptTypes('const callback='+gate.slice(begin,end)+';');
 const retained=[{id:'actual-post-capture'}],fresh=[{id:'next-observation'}];let reads=0;const checked:any[]=[];
 const callback=new Function('nodes','ui','editorCheck','prefix','let postCaptureEditorNodes=nodes;'+code+'return callback;')(retained,{snapshot:async()=>{reads++;return fresh;}},(nodes:any,prefix:string)=>{assert.equal(prefix,'prefix');checked.push(nodes);},'prefix');
 await callback();assert.equal(reads,0);assert.equal(checked[0],retained);
 await callback();assert.equal(reads,1);assert.equal(checked[1],fresh);
});


test("hidden Voboost menu seek uses actual run6 offscreen tab and only its owned left ScrollView", () => {
  // Exact normalized run6 target geometry; no retained local-log dependency in CI.
  const windowRef = { type: 1, active: true, inputFocused: true, rootPackage: "com.qinggan.app.setting", rootStatus: "available" };
  const nodes = [
    { packageName: windowRef.rootPackage, resource: "com.qinggan.app.setting:id/layout_menu_setting_left", className: "android.widget.ScrollView", scrollable: true, enabled: true, visibleToUser: true, bounds: [175, 0, 443, 720], parent: null, windowRef },
    { packageName: windowRef.rootPackage, resource: "", className: "android.widget.LinearLayout", parent: 0, windowRef },
    { packageName: windowRef.rootPackage, text: "Voboost", className: "android.widget.RadioButton", clickable: true, enabled: true, visibleToUser: false, bounds: [175, 0, 175, 0], rawBounds: [175, 0, 443, -415], parent: 1, windowRef },
  ] as any;
  const box = hiddenVoboostMenuScroll(nodes);
  assert.ok(box); assert.equal(box.resource, "com.qinggan.app.setting:id/layout_menu_setting_left");
  assert.deepEqual(box.bounds, [175, 0, 443, 720]);
  const tab = nodes.find(n => n.text === "Voboost")!;
  assert.equal(tab.visibleToUser, false);
  tab.visibleToUser = true;
  assert.equal(hiddenVoboostMenuScroll(nodes), null);
  tab.visibleToUser = false;
  assert.equal(hiddenVoboostMenuScroll(nodes.filter(n => n !== tab)), null);
  tab.parent = null;
  assert.throws(() => hiddenVoboostMenuScroll(nodes), /outside the owned Settings menu/);
});

const actualRun12SystemTree = {"android_pid":42715,"atomic_ui_state":false,"boot_id":"00102e35-3342-4f37-af18-b803d0633380","complete":true,"completed_uptime_ms":"40485","format":"voyemu.ui.v1","host_interval":{"clock_domain":"host.steady","clock_id":"85d64ba7dea78711c12d403f6f93ce65","end_ns":"517204511157000","start_ns":"517204476328000"},"observation_id":"ac0511ee-0db8-4bd2-8bb8-5d3f377f2604","observer_epoch":"c162b146-7ec3-4d9c-8ab7-52195b02ab40","observer_pid":5826,"profile":"free-09-chinese-20260106-6.0.3","read_attempts":1,"revision":"ac0511ee-0db8-4bd2-8bb8-5d3f377f2604","screens":[{"atomic_ui_state":false,"complete":true,"display_id":0,"height":720,"nodes":[{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":30,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"1","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":16},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":50,"width":1775,"x":145,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"3","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":14},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":145,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"5","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":13},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"20","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"21","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"20","resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"22","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"21","resource_id":"android:id/content","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"23","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"22","resource_id":"com.qinggan.app.setting:id/root_layout","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"24","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"23","resource_id":"com.qinggan.app.setting:id/main_space","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"26","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"24","resource_id":"com.qinggan.app.setting:id/main_menu","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"27","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"26","resource_id":"com.qinggan.app.setting:id/content_main_left_menu_layout","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.ScrollView","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"28","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"27","resource_id":"com.qinggan.app.setting:id/layout_menu_setting_left","screen":"primary","scrollable":true,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"29","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"28","resource_id":"com.qinggan.app.setting:id/content_main_left_menu","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":595,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.RadioGroup","clickable":false,"clipped_bounds":{"height":595,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"30","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"29","resource_id":"com.qinggan.app.setting:id/menu_radio_group","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":true,"bounds":{"height":100,"width":268,"x":175,"y":465},"checkable":true,"checked":true,"class":"android.widget.RadioButton","clickable":true,"clipped_bounds":{"height":100,"width":268,"x":175,"y":465},"description":"","enabled":true,"focused":false,"hint":"","id":"38","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"30","resource_id":"com.qinggan.app.setting:id/menu_radio_system","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"System","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":670,"width":1380,"x":485,"y":50},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":670,"width":1380,"x":485,"y":50},"description":"","enabled":true,"focused":false,"hint":"","id":"43","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"24","resource_id":"com.qinggan.app.setting:id/main_contentView","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":670,"width":1380,"x":485,"y":50},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":670,"width":1380,"x":485,"y":50},"description":"","enabled":true,"focused":false,"hint":"","id":"44","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"43","resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":670,"width":1380,"x":485,"y":50},"checkable":false,"checked":false,"class":"android.widget.ScrollView","clickable":false,"clipped_bounds":{"height":670,"width":1380,"x":485,"y":50},"description":"","enabled":true,"focused":false,"hint":"","id":"45","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"44","resource_id":"com.qinggan.app.setting:id/system_scroll","screen":"primary","scrollable":true,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":670,"width":1364,"x":485,"y":50},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":670,"width":1364,"x":485,"y":50},"description":"","enabled":true,"focused":false,"hint":"","id":"46","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"45","resource_id":"com.qinggan.app.setting:id/system_root","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":105,"width":1364,"x":485,"y":615},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":105,"width":1364,"x":485,"y":615},"description":"","enabled":true,"focused":false,"hint":"","id":"63","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"46","resource_id":"com.qinggan.app.setting:id/ll_info_root","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":9,"width":1334,"x":515,"y":711},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":9,"width":1334,"x":515,"y":711},"description":"","enabled":true,"focused":false,"hint":"","id":"67","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"63","resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":-591,"width":1334,"x":515,"y":1311},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":0,"width":0,"x":515,"y":720},"description":"","enabled":true,"focused":false,"hint":"","id":"104","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"67","resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":false,"window_id":3},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":-591,"width":667,"x":1182,"y":1311},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":true,"clipped_bounds":{"height":0,"width":0,"x":1182,"y":720},"description":"","enabled":true,"focused":false,"hint":"","id":"109","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"104","resource_id":"com.qinggan.app.setting:id/kv_tbox_mpu_res_version","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":false,"window_id":3}],"scope":"api28_default_display","screen":"primary","sdk_display_id":0,"status":"ok","unavailable_windows":[],"width":1920,"windows":[{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"component":null,"id":16,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":3,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"component":null,"id":14,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":2,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"component":null,"id":13,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":1,"package":"com.qinggan.app.launcher","title":"","type":-1},{"accessibility_focused":false,"active":true,"bounds":{"height":720,"width":1920,"x":0,"y":0},"component":null,"id":3,"identity_basis":"accessibility_window_id_and_metadata","input_focused":true,"layer":0,"package":"com.qinggan.app.setting","title":"Settings","type":1}]}],"session_id":"85d64ba7dea78711c12d403f6f93ce65","source":"android.ui","source_clock_domain":"android.uptime","source_instance":"c162b146-7ec3-4d9c-8ab7-52195b02ab40","source_timestamp_ms":"40485","started_uptime_ms":"40459","status":"ready","transient_rejections":{},"vehicle_revision":"137"};

test("System TBOX seek callback routes actual run12 tree through observed authority and native scroll", async () => {
  const hidden = structuredClone(actualRun12SystemTree), shown = structuredClone(hidden);
  const shownRow = shown.screens[0].nodes.find(n => n.resource_id.endsWith(":id/kv_tbox_mpu_res_version"))!;
  shownRow.visible = true; shownRow.actionable_candidate = true; shownRow.bounds = { x: 1182, y: 313, width: 667, height: 150 }; shownRow.clipped_bounds = { ...shownRow.bounds };
  const directory = mkdtempSync("/Users/vitaly/voboost/.work162/system-tbox-route-");
  let current = hidden, reads = 0, deliveries = 0;
  const fence = { sessionId: hidden.session_id, pid: hidden.android_pid, bootId: hidden.boot_id, profile: hidden.profile };
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5574", join(directory, "ui"), async () => { throw new Error("must not use legacy CLI"); }, async () => {}, async () => {
      current = ++reads === 1 ? hidden : shown;
      return systemNativeTree(current, fence);
    }, async request => {
      const delivery = systemNativePointer(request, current, { sessionId: fence.sessionId, pid: fence.pid, boot: fence.bootId });
      const expected = delivery.fields.expected as any;
      // Mirrors Voyemu test_input_fence.hpp exact expected-target matching.
      const matches = current.screens[0].nodes.filter(node => Object.entries(expected.target).every(([key, value]) => JSON.stringify((node as any)[key]) === JSON.stringify(value)));
      assert.equal(matches.length, 1);
      if (request.action === "tbox-burst") {
        assert.equal(delivery.op, "input.tap"); assert.equal(delivery.fields.count, 5);
        assert.equal(expected.target.resource_id, "com.qinggan.app.setting:id/kv_tbox_mpu_res_version");
      } else {
        assert.equal(delivery.op, "input.gesture");
        assert.equal(expected.target.resource_id, "com.qinggan.app.setting:id/system_scroll");
        assert.ok(Number(request.y1) > Number(request.y2));
      }
      deliveries++;
      return {};
    });
    const target = await systemTboxRecoveryTarget(ui);
    assert.equal(target.resource, "com.qinggan.app.setting:id/kv_tbox_mpu_res_version");
    assert.equal(reads, 3); assert.equal(deliveries, 1);
    await ui.call({ action: "tbox-burst", x: 1515, y: 388, ...ui.gestureAuthority(target) });
    assert.equal(deliveries, 2);
    assert.equal(hidden.screens[0].nodes.some(n => n.text === "Voboost"), false);
    const bad = structuredClone(hidden); bad.screens[0].nodes.find(n => n.resource_id.endsWith(":id/menu_radio_system"))!.checked = false;
    const badUi = new InstallerUi("/package/voboost-installer", "emulator-5574", join(directory, "bad-ui"), async () => { throw new Error("legacy"); }, async () => {}, async () => systemNativeTree(bad, fence), async () => { throw new Error("must not dispatch for unchecked System"); });
    await assert.rejects(systemTboxRecoveryTarget(badUi), /selector/);
  } finally { removeSystemFixture(directory, { recursive: true }); }
});

const actualRun6OpenTree = {"android_pid":90778,"atomic_ui_state":false,"boot_id":"9e6608d8-5fa8-4d63-8a93-02dd0e7651a6","complete":true,"completed_uptime_ms":"238862","format":"voyemu.ui.v1","host_interval":{"clock_domain":"host.steady","clock_id":"715c5b461bb71e05342a6a6485537d08","end_ns":"514073661956000","start_ns":"514073603356000"},"observation_id":"4630fc0d-0b4b-4e37-a2ef-2b54bd1a9ce9","observer_epoch":"04f0c084-0429-4358-a981-74581c115f1b","observer_pid":13560,"profile":"free-09-chinese-20260106-6.0.3","read_attempts":1,"revision":"4630fc0d-0b4b-4e37-a2ef-2b54bd1a9ce9","screens":[{"atomic_ui_state":false,"complete":true,"display_id":0,"height":720,"nodes":[{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":30,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"1","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":31},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":50,"width":1775,"x":145,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"3","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":32},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":145,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"5","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.launcher","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":30},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"20","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":null,"resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"21","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"20","resource_id":"","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.FrameLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"22","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"21","resource_id":"android:id/content","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"23","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"22","resource_id":"com.qinggan.app.setting:id/root_layout","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":1920,"x":0,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":1920,"x":0,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"24","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"23","resource_id":"com.qinggan.app.setting:id/main_space","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"26","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"24","resource_id":"com.qinggan.app.setting:id/main_menu","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.RelativeLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"27","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"26","resource_id":"com.qinggan.app.setting:id/content_main_left_menu_layout","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.ScrollView","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"28","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"27","resource_id":"com.qinggan.app.setting:id/layout_menu_setting_left","screen":"primary","scrollable":true,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.LinearLayout","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"29","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"28","resource_id":"com.qinggan.app.setting:id/content_main_left_menu","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":720,"width":268,"x":175,"y":0},"checkable":false,"checked":false,"class":"android.widget.RadioGroup","clickable":false,"clipped_bounds":{"height":720,"width":268,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"30","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"29","resource_id":"com.qinggan.app.setting:id/menu_radio_group","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"","text_redacted":false,"touchability":"unknown","visible":true,"window_id":22},{"actionable":false,"actionable_basis":"node_state_and_viewport_only","actionable_candidate":false,"bounds":{"height":-415,"width":268,"x":175,"y":0},"checkable":true,"checked":false,"class":"android.widget.RadioButton","clickable":true,"clipped_bounds":{"height":0,"width":0,"x":175,"y":0},"description":"","enabled":true,"focused":false,"hint":"","id":"31","identity_scope":"this_observation_only","occlusion":"unknown","package":"com.qinggan.app.setting","parent":"30","resource_id":"com.qinggan.app.setting:layout/layout_menu_with_icon","screen":"primary","scrollable":false,"selected":false,"selection_end":-1,"selection_start":-1,"showing_hint":false,"text":"Voboost","text_redacted":false,"touchability":"unknown","visible":false,"window_id":22}],"scope":"api28_default_display","screen":"primary","sdk_display_id":0,"status":"ok","unavailable_windows":[],"width":1920,"windows":[{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":30,"x":0,"y":0},"component":null,"id":31,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":3,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":50,"width":1775,"x":145,"y":0},"component":null,"id":32,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":2,"package":"com.qinggan.app.launcher","title":"","type":3},{"accessibility_focused":false,"active":false,"bounds":{"height":720,"width":145,"x":0,"y":0},"component":null,"id":30,"identity_basis":"accessibility_window_id_and_metadata","input_focused":false,"layer":1,"package":"com.qinggan.app.launcher","title":"","type":-1},{"accessibility_focused":false,"active":true,"bounds":{"height":720,"width":1920,"x":0,"y":0},"component":null,"id":22,"identity_basis":"accessibility_window_id_and_metadata","input_focused":true,"layer":0,"package":"com.qinggan.app.setting","title":"Settings","type":1}]}],"session_id":"715c5b461bb71e05342a6a6485537d08","source":"android.ui","source_clock_domain":"android.uptime","source_instance":"04f0c084-0429-4358-a981-74581c115f1b","source_timestamp_ms":"238862","started_uptime_ms":"238832","status":"ready","transient_rejections":{},"vehicle_revision":"125"};

test("actual open callback probes boot before fresh hidden-menu authority and completes tab selection", async () => {
  const gate = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
  const body = gate.slice(gate.indexOf("  const open = async () => {"), gate.indexOf("  const settingsScrollBox ="));
  const executable = stripTypeScriptTypes(body, { mode: "strip" });
  const directory = mkdtempSync("/Users/vitaly/voboost/.work162/open-menu-route-");
  let scrolled = false, selected = false, reads = 0, rootProbes = 0, swipes = 0;
  const tree = structuredClone(actualRun6OpenTree);
  const fence = { sessionId: tree.session_id, pid: tree.android_pid, bootId: tree.boot_id, profile: tree.profile };
  let current = tree;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5574", join(directory, "ui"), async inv => {
      const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
      assert.equal(request.action, "boot-observation"); rootProbes++;
      return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5574", body: "boot" }), stderr: "" };
    }, async () => {}, async () => {
      reads++; current = structuredClone(tree);
      const tab = current.screens[0].nodes.find(n => n.text === "Voboost")!;
      if (scrolled) { tab.visible = true; tab.bounds = { x: 175, y: 45, width: 268, height: 100 }; tab.clipped_bounds = { ...tab.bounds }; tab.actionable_candidate = true; }
      tab.checked = selected;
      return systemNativeTree(current, fence);
    }, async request => {
      if (request.action === "open-settings") return {};
      const delivery = systemNativePointer(request, current, { sessionId: fence.sessionId, pid: fence.pid, boot: fence.bootId });
      if (request.action === "swipe") { assert.equal((delivery.fields.expected as any).target.resource_id, "com.qinggan.app.setting:id/layout_menu_setting_left"); scrolled = true; swipes++; }
      else { assert.equal(request.action, "tap"); selected = true; }
      return {};
    });
    const bootObservation = async () => { await ui.call({ action: "boot-observation" }); return { boot: fence.bootId, bootCompleted: true }; };
    const make = new Function("ui", "bootObservation", "ownership", "hiddenVoboostMenuScroll", "selectedSettingsTab", "focusedApplication", "SETTINGS", "Unavailable", "inspect", "screenshot", "drawerCloseTarget", "settingsForeground", "caseNumber", "let settingsNavigationDeadline=null, firstBootCaptureDeadline=null; const native=null; " + executable + "; return open;");
    const focused = (n: any) => n.visibleToUser === true && n.windowRef?.inputFocused;
    const isSelected = (nodes: any[]) => nodes.some(n => n.text === "Voboost" && focused(n) && n.checked);
    const open = make(ui, bootObservation, async () => {}, hiddenVoboostMenuScroll, isSelected, focused, "com.qinggan.app.setting", Error, async () => { throw new Error("unexpected focus recovery"); }, async () => {}, () => { throw new Error("drawer"); }, async () => { throw new Error("navigation failed"); }, 15);
    await open();
    assert.equal(swipes, 1); assert.equal(selected, true); assert.ok(rootProbes >= 4); assert.ok(reads >= 4);
  } finally { removeSystemFixture(directory, { recursive: true }); }
});

test('hidden owned checkbox label remains a read-only upward navigation anchor',()=>{
 const w:any={type:1,active:true,inputFocused:true,rootStatus:'available',rootPackage:'com.qinggan.app.setting'};
 const viewport:any={bounds:[485,50,1865,720],windowRef:w};
 const label:any={packageName:'com.qinggan.app.setting',text:'target',visibleToUser:false,bounds:[630,50,630,50],windowRef:w};
 assert.equal(agentScrollDirection([label],'com.qinggan.app.setting','target',viewport),'up');
 assert.equal(agentScrollDirection([{...label,windowRef:{...w}}],'com.qinggan.app.setting','target',viewport),'down');
});

import {installedSemanticRequired,regressionOptions,regressionSummary} from '../../release/emulator-gate.ts';
test('keyboards selection reuses exact existing semantic IDs and excludes other cases',()=>{
 assert.deepEqual(installedSemanticRequired('keyboards'),['10:actual-english-input','11:actual-mixed-layout-input']);
 assert.equal(regressionOptions('keyboards',{imageCapture:'deferred'}),true);
 assert.throws(()=>regressionOptions('keyboards',{}),/deferred/);
 assert.throws(()=>regressionOptions('keyboards',{imageCapture:'deferred',vehiclePart:'pw'}),/vehicle subset/);
 const gate=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
 assert.match(gate,/keyboards: \[10, 11\]/);
 const e:any={regressionScenario:'keyboards',imageCapture:'deferred',passed:false,cases:[{number:9,status:'unavailable',subchecks:[]}],actions:[],boots:[]};
 assert.throws(()=>functionalEvidence(e),/run other cases/);
});

test('keyboards exact semantic summary needs only two existing checks and no unrelated reboot',()=>{
 const required=installedSemanticRequired('keyboards')!;
 const fixture=()=>({imageCapture:'deferred',regressionScenario:'keyboards',passed:false,boots:[],cases:Array.from({length:27},(_,i)=>({number:i+1,status:[10,11].includes(i+1)?'unavailable':'not-run',subchecks:[10,11].includes(i+1)?[...required.filter(id=>id.startsWith((i+1)+':')).map(id=>({id:id.split(':')[1],status:'passed',artifacts:['actual-retained-readback']})),{id:'installed-visual-pending',status:'unavailable',artifacts:[]}]:[]}))});
 const e=fixture();assert.equal(regressionSummary(e,'keyboards').semanticPassed,true);assert.equal(regressionSummary(e,'keyboards').passed,false);
 for(const mutate of [(x:any)=>x.cases[9].subchecks.shift(),(x:any)=>x.cases[9].subchecks.push({...x.cases[9].subchecks[0]}),(x:any)=>x.cases[9].subchecks[0].status='failed',(x:any)=>x.cases[8].status='unavailable']){const bad=fixture();mutate(bad);assert.equal(regressionSummary(bad,'keyboards').semanticPassed,false);}
});
test('actual nonempty-editor focus stage uses right interior and verifies text/caret',async()=>{
 const gate=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');const begin=gate.indexOf('    const focusValue=editorValue(fields[0]);'),end=gate.indexOf('    if (prefix && editorValue(editor[0]) !== prefix)',begin);assert.ok(begin>0&&end>begin);
 const code=stripTypeScriptTypes('const callback=async()=>{let nodes;'+gate.slice(begin,end)+'return editor;};');
 const original:any={packageName:'com.pateonavi.naviapp',className:'android.widget.EditText',bounds:[275,50,815,150],rawBounds:[275,50,815,150],focused:false,text:'Москваmoscowё',selectionStart:13,selectionEnd:13};
 for(const caret of [13,12]){const requests:any[]=[];const cb=new Function('fields','prefix','editorValue','editorCaretAtEnd','center','ui','focusedApplication','unavailable',code+'return callback;')([original],'',(n:any)=>n.text,(n:any)=>n.selectionStart===n.text.length&&n.selectionEnd===n.text.length,()=>{throw Error('center forbidden');},{gestureAuthority:()=>({}),call:async(r:any)=>{requests.push(r);},snapshot:async()=>[{...original,focused:true,selectionStart:caret,selectionEnd:caret}]},()=>true,(m:string)=>{throw Error(m);});
 if(caret===13)await cb();else await assert.rejects(cb(),/end-focus/);assert.equal(requests.length,1);assert.equal(requests[0].x,807);assert.equal(requests[0].y,100);}
});


test("completed actual Restore is historical state, while live or malformed Restore blocks install observation", () => {
  const row = {schemaVersion:1,operation:"restore",transactionId:"a".repeat(64),journal:`recovery/${"a".repeat(64)}/restore-journal.json`,phase:"restore-complete",outcome:"complete",rebootCount:2,bootId:boot,statusSequence:33,journalSha256:"c".repeat(64),error:null,installedApk:null};
  const body = (value:any) => `operation:\n${JSON.stringify(value)}\n`;
  assert.equal(installOperation(body(row),boot,"b".repeat(64),true),null);
  assert.equal(installOperation(body(row),boot,"b".repeat(64)),null);
  for (const patch of [{outcome:"running"},{outcome:"failed"},{phase:"restore-policy-backup"},{error:{code:"io",message:"denied"}},{journal:"foreign"},{bootId:"invalid"},{transactionId:"invalid"},{journalSha256:"invalid"},{rebootCount:0},{rebootCount:17},{statusSequence:0},{installedApk:{}},{extra:true}]) assert.throws(()=>installOperation(body({...row,...patch}),boot,"b".repeat(64),true));
  assert.throws(()=>installOperation(body(row)+JSON.stringify(row),boot,"b".repeat(64),true),/conflicting/);
  assert.throws(()=>installOperation(body(row)+JSON.stringify(historicalInstall()),boot,"b".repeat(64),true),/conflicting/);
});

import keyboard214Vm from "node:vm";
const keyboard214Source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
function keyboard214Harness(options: Record<string,unknown>={}) {
  const start=keyboard214Source.indexOf("    const take = async (): Promise<KeyboardFrame>");
  const tail=keyboard214Source.indexOf("      const frame: KeyboardFrame =",start);
  const tailEnd=keyboard214Source.indexOf("\n    };",tail);
  assert.ok(start>=0 && tail>start && tailEnd>tail,"actual take() second-frame assignment absent");
  const begin=keyboard214Source.indexOf("async(point,second)=>{",tailEnd);
  const end=keyboard214Source.indexOf("\n      },async()=>{",begin);
  assert.ok(begin>tailEnd && end>begin,"keyboard214 actual dispatch callback absent");
  const node={windowRef:{id:7},resource:"ime:alpha_view1",bounds:[0,100,1920,720]};
  const nodes=[node], calls: unknown[]=[];
  const state: Record<string,any>={postCaptureDispatch:null,boot:"boot",snapshotBoot:"boot",
    capture:{manifest:{android_boot_id:"boot",session_id:"session",android_pid:123}},
    after:node,afterNodes:nodes,bounds:node.bounds,observedAt:100,
    decoded:{width:1920,height:620,rgba:new Uint8Array(0)},prefix:"Москва",
    performance:{now:()=>1600},
    unavailable:(message:string)=>{throw Error(message);},
    canvas:(actual:unknown)=>{assert.equal(actual,nodes);return node;},
    editorCheck:(actual:unknown,prefix:string)=>{assert.equal(actual,nodes);assert.equal(prefix,"Москва");if(options.badPrefix)throw Error("prefix mismatch");},
    ui:{
      snapshot:async()=>{throw Error("redundant snapshot prohibited");},
      gestureAuthority:(actual:unknown)=>{assert.equal(actual,node);if(options.stale)throw Error("stale or foreign UI node");return {expectedWindow:{id:7}};},
      call:async(request:unknown)=>{calls.push(request);},
    },
  };
  const context=keyboard214Vm.createContext(state);
  const take=keyboard214Vm.runInContext(stripTypeScriptTypes("()=>{"+keyboard214Source.slice(tail,tailEnd)+"}"),context);
  const dispatch=keyboard214Vm.runInContext(stripTypeScriptTypes(keyboard214Source.slice(begin,end)+"\n}"),context);
  const first=take(),second=take();
  return {state,node,nodes,calls,first,second,dispatch};
}
test("keyboard214 actual second-frame dispatch reuses post-image observation without another snapshot",async()=>{
  const h=keyboard214Harness();await h.dispatch({x:20,y:200},h.second);
  assert.equal(h.calls.length,1);assert.equal(h.state.postCaptureDispatch,null);
});
test("keyboard214 actual dispatch consumes observation once",async()=>{
  const h=keyboard214Harness();await h.dispatch({x:20,y:200},h.second);
  await assert.rejects(()=>h.dispatch({x:20,y:200},h.second),/observation absent/);
  assert.equal(h.calls.length,1);
});
for(const [name,change,pattern] of [
  ["expired age",(h:any)=>h.state.performance.now=()=>2101,/capture age/],
  ["future age",(h:any)=>h.state.performance.now=()=>99,/capture age/],
  ["first frame instead of second",(h:any)=>h.second=h.first,/observation absent/],
  ["absent observation",(h:any)=>h.state.postCaptureDispatch=null,/observation absent/],
  ["boot drift",(h:any)=>h.state.snapshotBoot="other",/identity changed/],
  ["window drift",(h:any)=>h.node.windowRef.id=8,/identity changed/],
  ["resource drift",(h:any)=>h.node.resource="other",/identity changed/],
  ["bounds drift",(h:any)=>h.node.bounds=[0,101,1920,720],/identity changed/],
] as const) test("keyboard214 actual dispatch rejects "+name+" without gesture",async()=>{
  const h=keyboard214Harness();change(h);await assert.rejects(()=>h.dispatch({x:20,y:200},h.second),pattern);assert.equal(h.calls.length,0);
});
test("keyboard214 actual dispatch rejects committed prefix mismatch without gesture",async()=>{
  const h=keyboard214Harness({badPrefix:true});await assert.rejects(()=>h.dispatch({x:20,y:200},h.second),/prefix mismatch/);assert.equal(h.calls.length,0);
});
test("keyboard214 actual dispatch retains gestureAuthority stale-node rejection",async()=>{
  const h=keyboard214Harness({stale:true});await assert.rejects(()=>h.dispatch({x:20,y:200},h.second),/stale or foreign/);assert.equal(h.calls.length,0);
});
