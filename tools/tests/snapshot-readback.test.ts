import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { installerDiagnostic } from "../emulator/installer-diagnostics.ts";
import { nativeReadTransportTimeout } from "../emulator/native-session.ts";
import { installerFailure } from "./installer-diagnostic-fixture.ts";
const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
function production(name: string, next: string, scope: Record<string, unknown> = {}) {
  const start = source.indexOf(`export ${name === "snapshotReadback" ? "async " : ""}function ${name}(`);
  assert.ok(start >= 0, `missing production ${name}`);
  const end = source.indexOf(next, start);
  return Function(...Object.keys(scope), stripTypeScriptTypes(source.slice(start, end)).replace(/^export /, "") + `;return ${name};`)(...Object.values(scope));
}
const uiWarmupDeadline = production("uiWarmupDeadline", "export function uiTraversalInconsistent", { installerDiagnostic });
const uiTraversalInconsistent = production("uiTraversalInconsistent", "export async function captureFirstBoot", { installerDiagnostic });
const snapshotReadback = production("snapshotReadback", "export const installerRootPending", { uiWarmupDeadline, uiTraversalInconsistent });
// Retained action 147 from the failed launcher132 run; helper ran 32990 ms.
const failure = {"exitCode": 1, "signal": null, "timedOut": false, "overflow": false, "stdout": "[07:10:09.500 INFO] Connecting to the head unit over USB\n[07:10:09.619 INFO] Connected to the head unit: emulator-5562\n", "stderr": "Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some(72) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0\\nui-observation service phase=initial present=true flags=65554 capabilities=15\\nui-observation stage=active-root-before-windows reason=active-root-waiting kind=none syscall=none errno=none windows=0 window-reads=0 nodes=0 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=0 child-ms=0\\nui-observation active-root window-id=710 reads=5\\nui-observation service phase=after-set present=true flags=65618 capabilities=15\\nui-observation window id=706 type=-1 layer=3\\nui-observation window id=707 type=-1 layer=2\\nui-observation window id=705 type=-1 layer=1\\nui-observation window id=710 type=1 layer=0\\nui-observation stage=deadline reason=elapsed kind=none syscall=none errno=none windows=4 window-reads=1 nodes=68 pending-ipc=get-child pending-ipc-ms=181 refresh-calls=51 refresh-ms=23087 child-calls=107 child-ms=2121\\nui-capture diagnostic=run.log bytes=834 sha256=c8920b5ddf8cb8ea5e66d948a3193971c84aef7cc2d1698197a5e9faccccc478\\nui-capture diagnostic=native.log bytes=28 sha256=7eac3f15731263e7cb1e1baf9ba133c9f946d0b9fb34c1d0e9871fc95fa20444\\nui-capture native-window pid=21749 start=1791864615.423867615 end=1791864649.310449089 reader_exit=0\\nui-capture timing startup_ms=2030 handshake_ms=1210 run_ms=32990 exit_ms=250 cleanup_ms=4060 total_ms=43950\\n\n"};
const ok = { exitCode: 0, signal: null, timedOut: false, overflow: false, stdout: "receipt", stderr: "" };
function harness(results: any[], foreignAt = 0) {
  let calls = 0, guards = 0, now = 0;
  const budgets: number[] = [];
  return {
    run: (action = "snapshot") => snapshotReadback(action, async (remaining: number) => {
      calls++; budgets.push(remaining); now += 48130; return results[Math.min(calls - 1, results.length - 1)];
    }, async () => { guards++; now += 1000; if (guards === foreignAt) throw new Error("foreign boot/owner or non-Enforcing"); }, "emulator-5562", () => now),
    stats: () => ({ calls, guards, budgets, now }),
  };
}
test("retained elapsed helper deadline is recognized only for the selected serial", () => {
  assert.equal(uiWarmupDeadline(failure, "emulator-5562"), true);
  assert.equal(uiWarmupDeadline(failure, "emulator-9999"), false);
});
test("retained launcher failure permits one fenced readonly retry", async () => {
  const h = harness([failure, ok]); assert.equal(await h.run(), ok);
  assert.equal(h.stats().calls, 2); assert.equal(h.stats().guards, 3);
  assert.ok(h.stats().budgets[1] < h.stats().budgets[0]);
});
test("foreign boot owner or non-Enforcing prevents a second observation", async () => {
  const h = harness([failure, ok], 2); await assert.rejects(h.run(), /foreign boot/);
  assert.equal(h.stats().calls, 1);
});
test("a second deadline remains terminal without a third observation", async () => {
  const h = harness([failure]); assert.equal(await h.run(), failure); assert.equal(h.stats().calls, 2);
});
test("unknown failures are returned once and cannot trigger retry", async () => {
  for (const result of [{ ...failure, stderr: "unknown helper failure" }, { ...failure, timedOut: true }, { ...failure, stderr: failure.stderr + "permission denied" }]) {
    const h = harness([result, ok]); assert.equal(await h.run(), result); assert.equal(h.stats().calls, 1);
  }
});
test("mutating input is invoked once even with the same helper deadline", async () => {
  for (const action of ["tap", "swipe", "drag", "text", "reboot"]) {
    const h = harness([failure, ok]); assert.equal(await h.run(action), failure); assert.equal(h.stats().calls, 1); assert.equal(h.stats().guards, 0);
  }
});
test("expired guard budget rejects before issuing another snapshot", async () => {
  let calls = 0, now = 0;
  await assert.rejects(snapshotReadback("snapshot", async () => { calls++; return failure; }, async () => { now = 180001; }, "emulator-5562", () => now), /budget exhausted/);
  assert.equal(calls, 0);
});
test("successful response is rejected if the final fence changes", async () => {
  const h = harness([ok], 2); await assert.rejects(h.run(), /foreign boot/); assert.equal(h.stats().calls, 1);
});
function adapterHarness(action = "snapshot", capturePhase = false) {
  const start = source.indexOf("  const uiAdapter: ProcessAdapter = async");
  const end = source.indexOf("  let ui: InstallerUi;", start);
  const code = stripTypeScriptTypes(source.slice(start, end));
  let calls = 0, owners = 0;
  // Request retention belongs to record(), not uiAdapter; execute its real source block.
  const retentionStart = source.indexOf('    if (inv.args[0] === "--emulator-check") {', source.indexOf("  const record = async"));
  const retentionEnd = source.indexOf("    evidence.actions.push(row);", retentionStart);
  const retainRequest = Function("inv", "row", "regular", "digest", "readFileSync", stripTypeScriptTypes(source.slice(retentionStart, retentionEnd)));
  const evidence = { actions: [] as any[] };
  const scope = {
    native:null,nativeBootReadDeadline:null, evidence, readFileSync: () => JSON.stringify({ action }), digest: () => "hash", frozen: { package: "kit" }, runtime: { pid: 1, port: 2 }, serial: "emulator-5562", language: "ru",
    performance: { now: () => 0 }, ownership: async () => { owners++; }, probe: async () => "boot",
    record: async (inv: any) => { calls++; const result = { ...(calls === 1 ? failure : ok), actionId: calls }, row = { id: calls, artifacts: [] }; retainRequest(inv, row, () => {}, () => "request-hash", () => "request-bytes"); evidence.actions.push(row); return result; },
    snapshotReadback, installerClosedPending: () => false, installerRootPending: () => false, offlineObservation: () => false, uiWarmupDeadline,
    UiWarmupDeadline: class extends Error {},
  };
  const adapter = Function(...Object.keys(scope), `let initialSettingsNavigation=false,currentBoot="boot", snapshotBoot="boot", snapshotReadbackDeadline=null,firstBootCaptureDeadline=${capturePhase ? "180000" : "null"},installerActive=false,installerRebootCapture=false,installerMonitorStopped=false,faultLive=null;` + code + "return uiAdapter;")(...Object.values(scope));
  return { run: () => adapter({ args: ["--emulator-check", "snapshot.json"], timeoutMs: 240000 }), stats: () => ({ calls, owners, evidence }) };
}
test("production UI adapter retains failed request artifacts and retries only ordinary snapshots", async () => {
  const h = adapterHarness(); assert.equal((await h.run()).exitCode, 0);
  assert.equal(h.stats().calls, 2);
  assert.equal(h.stats().evidence.actions[0].artifacts[0].kind, "request");
  assert.equal(h.stats().evidence.actions[1].artifacts[0].kind, "request");
  assert.deepEqual(h.stats().evidence.actions.map(row => row.artifacts[0]), Array(2).fill({path:"snapshot.json",sha256:"request-hash",kind:"request"}));
  assert.equal(h.stats().owners, 2);
  const mutation = adapterHarness("tap"); assert.equal((await mutation.run()).exitCode, 1); assert.equal(mutation.stats().calls, 1);
});
test("production first-boot UI adapter delegates its deadline without a nested retry", async () => {
  const h = adapterHarness("snapshot", true); await assert.rejects(h.run(), /first-boot Accessibility/); assert.equal(h.stats().calls, 1);
});

function initialProbeHarness(fail = false) {
  const start = source.indexOf("  const uiAdapter: ProcessAdapter = async");
  const end = source.indexOf("  let ui: InstallerUi;", start);
  const code = stripTypeScriptTypes(source.slice(start, end));
  const clipStart = source.indexOf("    if (firstBootCaptureDeadline !== null) {");
  const clipEnd = source.indexOf("    const id = evidence.actions.length + 1;", clipStart);
  const clip = Function("nativeReadTransportTimeout", "firstBootCaptureDeadline", "snapshotReadbackDeadline", "performance", "Unavailable", "inv", "settingsNavigationDeadline = null", stripTypeScriptTypes(source.slice(clipStart, clipEnd)) + "return inv;").bind(null, nativeReadTransportTimeout);
  let deadline: () => number | null = () => null, calls = 0, probes = 0, owners = 0;
  const initial: any[] = [], evidence = { actions: [] as any[] };
  const scope = {
    native:null,nativeBootReadDeadline:null, evidence, readFileSync: () => JSON.stringify({ action: "snapshot" }), digest: () => "hash", frozen: { package: "kit" }, runtime: { pid: 1, port: 2 }, serial: "emulator-5562", language: "ru", performance: { now: () => 0 },
    ownership: async () => { owners++; }, probe: async () => { probes++; initial.push({ deadline: deadline(), clipped: clip(null, deadline(), { now: () => 0 }, Error, { timeoutMs: 600000 }).timeoutMs }); if (fail) throw new Error("initial probe failure"); return "boot"; },
    record: async () => { calls++; const result = { ...ok, actionId: calls }; evidence.actions.push({ id: calls, artifacts: [] }); return result; },
    snapshotReadback, installerClosedPending: () => false, installerRootPending: () => false, offlineObservation: () => false, uiWarmupDeadline, UiWarmupDeadline: class extends Error {},
  };
  const instance = Function(...Object.keys(scope), 'let initialSettingsNavigation=false,currentBoot="boot",snapshotBoot="boot",snapshotReadbackDeadline=null,firstBootCaptureDeadline=null,installerActive=false,installerRebootCapture=false,installerMonitorStopped=false,faultLive=null;' + code + "return {adapter:uiAdapter,deadline:()=>snapshotReadbackDeadline};")(...Object.values(scope));
  deadline = instance.deadline;
  return { run: () => instance.adapter({ args: ["--emulator-check", "snapshot.json"], timeoutMs: 240000 }), stats: () => ({ deadline: deadline(), calls, probes, owners, initial }) };
}
test("production initial snapshot probe shares the phase and is not repeated before the first read", async () => {
  const h = initialProbeHarness(); assert.equal((await h.run()).exitCode, 0);
  assert.equal(h.stats().initial[0].deadline, 180000);
  assert.equal(h.stats().initial[0].clipped, 180000);
  assert.equal(h.stats().probes, 2); // Initial proof and final proof only.
  assert.equal(h.stats().owners, 1); // Initial ownership is supplied by InstallerUi.call.
  assert.equal(h.stats().calls, 1); assert.equal(h.stats().deadline, null);
});
test("production snapshot deadline is cleared when its initial probe fails", async () => {
  const h = initialProbeHarness(true); await assert.rejects(h.run(), /initial probe failure/);
  assert.equal(h.stats().calls, 0); assert.equal(h.stats().probes, 1);
  assert.equal(h.stats().initial[0].deadline, 180000); assert.equal(h.stats().deadline, null);
});

// Retained calibration action11: readonly serialization was in refresh-after.
const calibrationFailure = {"exitCode": 1, "signal": null, "timedOut": false, "overflow": false, "stdout": "[07:48:14.363 INFO] Connecting to the head unit over USB\n[07:48:14.497 INFO] Connected to the head unit: emulator-5562\n", "stderr": "Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some(72) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0\\nui-observation service phase=initial present=true flags=65554 capabilities=15\\nui-observation stage=active-root-before-windows reason=active-root-waiting kind=none syscall=none errno=none windows=0 window-reads=0 nodes=0 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=0 child-ms=0\\nui-observation active-root window-id=11 reads=6\\nui-observation service phase=after-set present=true flags=65618 capabilities=15\\nui-observation window id=9 type=-1 layer=3\\nui-observation window id=8 type=-1 layer=2\\nui-observation window id=10 type=-1 layer=1\\nui-observation window id=11 type=1 layer=0\\nui-observation stage=deadline reason=elapsed kind=none syscall=none errno=none windows=4 window-reads=1 nodes=61 pending-ipc=refresh-after pending-ipc-ms=148 refresh-calls=42 refresh-ms=19891 child-calls=89 child-ms=4573\\nui-capture diagnostic=run.log bytes=830 sha256=877452b0dfc7fbe627cf08423decd1fb58bedb95f10e0661ca7be0a47ad8762c\\nui-capture diagnostic=native.log bytes=28 sha256=7eac3f15731263e7cb1e1baf9ba133c9f946d0b9fb34c1d0e9871fc95fa20444\\nui-capture native-window pid=4655 start=1791866908.439999212 end=1791866944.841041729 reader_exit=0\\nui-capture timing startup_ms=9680 handshake_ms=820 run_ms=34920 exit_ms=300 cleanup_ms=5000 total_ms=54400\\n\n"};
test("actual calibration refresh-after deadline permits exactly one readonly snapshot retry", async () => {
  assert.equal(uiWarmupDeadline(calibrationFailure, "emulator-5562"), true);
  const h = harness([calibrationFailure, ok]); assert.equal(await h.run(), ok); assert.equal(h.stats().calls, 2);
});
test("elapsed snapshot deadlines accept the complete readonly IPC enum and reject mutation or unknown phases", () => {
  for (const phase of ["none", "get-child", "refresh-after"]) {
    const result = { ...calibrationFailure, stderr: calibrationFailure.stderr.replace("pending-ipc=refresh-after", `pending-ipc=${phase}`) };
    assert.equal(uiWarmupDeadline(result, "emulator-5562"), true, phase);
  }
  for (const phase of ["refresh", "gesture-target-refresh", "vendor-dialog-root-refresh", "vendor-dialog-container-refresh", "unknown", "refresh-after-extra", "refresh-after|none", ""]) {
    const result = { ...calibrationFailure, stderr: calibrationFailure.stderr.replace("pending-ipc=refresh-after", `pending-ipc=${phase}`) };
    assert.equal(uiWarmupDeadline(result, "emulator-5562"), false, phase);
  }
  assert.equal(uiWarmupDeadline(calibrationFailure, "foreign-serial"), false);
  assert.equal(uiWarmupDeadline({ ...calibrationFailure, stderr: calibrationFailure.stderr.replace("reason=elapsed", "reason=hard-halt") }, "emulator-5562"), false);
});

// Actual launcher133 action402: failed traversal yielded no XML.
const transient = {
  "exitCode": 1,
  "signal": null,
  "timedOut": false,
  "overflow": false,
  "stdout": "[09:45:36.120 INFO] Connecting to the head unit over USB\n[09:45:36.250 INFO] Connected to the head unit: emulator-5562\n",
  "stderr": "Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some(72) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0\\nui-observation service phase=initial present=true flags=65554 capabilities=15\\nui-observation active-root window-id=935 reads=1\\nui-observation service phase=after-set present=true flags=65618 capabilities=15\\nui-observation window id=935 type=1 layer=0\\nui-observation stage=nodes reason=inconsistent kind=java.lang.IllegalStateException syscall=none errno=none windows=1 window-reads=1 nodes=52 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=52 child-ms=29\\nui-capture diagnostic=run.log bytes=483 sha256=c07f89c0e6639da1d912bb6b08b1ad7a555ce14a1125f2503c53ff4817062944\\nui-capture diagnostic=native.log bytes=28 sha256=7eac3f15731263e7cb1e1baf9ba133c9f946d0b9fb34c1d0e9871fc95fa20444\\nui-capture native-window pid=24606 start=1791873939.354456273 end=1791873943.843452733 reader_exit=0\\nui-capture timing startup_ms=1580 handshake_ms=1380 run_ms=4410 exit_ms=20 cleanup_ms=3530 total_ms=12700\\n\n"
};
test("actual rejected traversal gets exactly one guarded fresh readonly retry", async () => { assert.equal(uiTraversalInconsistent(transient, "emulator-5562"), true); const h = harness([transient,ok]); assert.equal(await h.run(),ok); assert.equal(h.stats().calls,2); assert.equal(h.stats().guards,3); });
test("mixed deadline and traversal share two attempts total", async () => { for (const pair of [[failure,transient],[transient,failure],[transient,transient]]) {const h=harness(pair); assert.equal(await h.run(),pair[1]); assert.equal(h.stats().calls,2);} });
test("traversal retry cannot repeat mutations or bypass fresh guard", async () => { const mutation=harness([transient,ok]); assert.equal(await mutation.run("tap"),transient); assert.equal(mutation.stats().calls,1); const stale=harness([transient,ok],2); await assert.rejects(stale.run(),/foreign boot/); assert.equal(stale.stats().calls,1); });
test("unknown, identity, partial payload, pending IPC and foreign serial remain terminal", async () => { assert.equal(uiTraversalInconsistent(transient,"wrong"),false); for(const r of [{...transient,stdout:transient.stdout+"{}\n"},{...transient,stderr:transient.stderr.replace("reason=inconsistent","reason=node-identity-drift")},{...transient,stderr:transient.stderr+"permission denied"},{...transient,stderr:transient.stderr.replace("pending-ipc=none","pending-ipc=get-child")},{...transient,timedOut:true},{...transient,overflow:true}]) {assert.equal(uiTraversalInconsistent(r,"emulator-5562"),false); const h=harness([r,ok]); assert.equal(await h.run(),r); assert.equal(h.stats().calls,1); }});

test("complete failure grammar rejects appended unknown failures and contradictory XML envelope", async () => { for(const stderr of [transient.stderr+"unknown helper failure",transient.stderr.replace("xml_type=missing xml_bytes=0","xml_type=regular xml_bytes=123"),transient.stderr.replace("reason=inconsistent","reason=inconsistentX"),transient.stderr.replace("reader_exit=0","reader_exit=1"),transient.stderr.replace("ui-observation window id=935","ui-observation window id=936"),transient.stderr.replace("ui-capture diagnostic=native.log","unknown diagnostic=native.log")]) {const r={...transient,stderr}; assert.equal(uiTraversalInconsistent(r,"emulator-5562"),false); const h=harness([r,ok]); assert.equal(await h.run(),r); assert.equal(h.stats().calls,1); }});

const coordinatorFailure = {"exitCode": 1, "signal": null, "timedOut": false, "overflow": false, "stdout": "[11:38:36.845 INFO] Connecting to the head unit over USB\n[11:38:36.975 INFO] Connected to the head unit: emulator-5562\n", "stderr": "Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some(74) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=deadline exit=74 helper_exit=none xml_type=missing xml_bytes=0\\nui-observation service phase=initial present=true flags=65554 capabilities=15\\nui-observation stage=active-root-before-windows reason=active-root-waiting kind=none syscall=none errno=none windows=0 window-reads=0 nodes=0 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=0 child-ms=0\\nui-observation active-root window-id=399 reads=6\\nui-observation service phase=after-set present=true flags=65618 capabilities=15\\nui-observation window id=404 type=-1 layer=3\\nui-observation window id=403 type=-1 layer=2\\nui-observation window id=402 type=-1 layer=1\\nui-observation window id=399 type=1 layer=0\\nui-observation window id=404 type=-1 layer=3\\nui-observation window id=403 type=-1 layer=2\\nui-observation window id=402 type=-1 layer=1\\nui-observation window id=399 type=1 layer=0\\nui-observation stage=nodes-complete reason=timing kind=none syscall=none errno=none windows=4 window-reads=2 nodes=90 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=172 child-ms=8779\\nui-observation window id=404 type=-1 layer=3\\nui-observation window id=403 type=-1 layer=2\\nui-observation window id=402 type=-1 layer=1\\nui-observation window id=399 type=1 layer=0\\nui-observation stage=deadline reason=elapsed kind=none syscall=none errno=none windows=4 window-reads=3 nodes=90 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=172 child-ms=8779\\nui-observation stage=metadata-after reason=deadline kind=java.lang.IllegalStateException syscall=none errno=none windows=4 window-reads=3 nodes=90 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=172 child-ms=8779\\nui-capture diagnostic=run.log bytes=1631 sha256=531d1e28e17eb423677f0175bc07c23ec5700d668f8639165deb47812911d62d\\nui-capture timing startup_ms=6290 handshake_ms=1510 run_ms=0 exit_ms=0 cleanup_ms=4610 total_ms=52510\\n\n"};

test("actual135 coordinator elapsed metadata-after deadline receives one fenced retry", async () => {
  assert.equal(uiWarmupDeadline(coordinatorFailure, "emulator-5562"), true);
  const h = harness([coordinatorFailure, ok]); assert.equal(await h.run(), ok);
  assert.equal(h.stats().calls, 2); assert.equal(h.stats().guards, 3);
});
test("coordinator74 cancellation unknown payload foreign or contradictory envelope never retries", async () => {
  for (const result of [
    {...coordinatorFailure, stderr:coordinatorFailure.stderr.replace("stage=deadline exit=74", "stage=cancelled exit=74")},
    {...coordinatorFailure, stderr:coordinatorFailure.stderr.replace("stage=metadata-after reason=deadline", "stage=metadata-after reason=unknown")},
    {...coordinatorFailure, stderr:coordinatorFailure.stderr.replace("xml_bytes=0", "xml_bytes=1")},
    {...coordinatorFailure, stdout:coordinatorFailure.stdout+"payload"},
    {...coordinatorFailure, stderr:coordinatorFailure.stderr+"unknown"},
    {...coordinatorFailure, stderr:coordinatorFailure.stderr.replace("helper_exit=none", "helper_exit=1")},
    {...coordinatorFailure, timedOut:true},
  ]) {
    assert.equal(uiWarmupDeadline(result, "emulator-5562"), false);
    const h=harness([result,ok]); assert.equal(await h.run(),result); assert.equal(h.stats().calls,1);
  }
  assert.equal(uiWarmupDeadline(coordinatorFailure,"emulator-9999"),false);
  const h=harness([coordinatorFailure,ok],2); await assert.rejects(h.run(),/foreign boot/); assert.equal(h.stats().calls,1);
});
test("mixed helper72 coordinator74 failures share exactly two attempts; mutation never retries", async () => {
  for (const pair of [[failure,coordinatorFailure],[coordinatorFailure,failure]]) {
    const h=harness(pair);assert.equal(await h.run(),pair[1]);assert.equal(h.stats().calls,2);
  }
  const h=harness([coordinatorFailure,ok]);assert.equal(await h.run("tap"),coordinatorFailure);assert.equal(h.stats().calls,1);
});

test("current installer canonical envelopes preserve warmup and traversal classifiers and reject mixed errors", () => {
  for (const [retained, classify] of [[failure, uiWarmupDeadline], [calibrationFailure, uiWarmupDeadline],
    [coordinatorFailure, uiWarmupDeadline], [transient, uiTraversalInconsistent]] as const) {
    const current = installerFailure(retained.stderr, "emulator-5562");
    assert.equal(classify(current, "emulator-5562"), true);
    const withoutTransportDetail = { ...current, stderr: current.stderr.split("\n").filter(line => !line.includes('"level":"debug"')).join("\n") };
    assert.equal(classify(withoutTransportDetail, "emulator-5562"), true);
    assert.equal(classify(current, "foreign-serial"), false);
    for (const altered of [
      { ...current, stdout: "{}\n" }, { ...current, stderr: current.stderr + "unknown raw line\n" },
      { ...current, stderr: current.stderr.replace('"kind":"installer"', '"kind":"host"') },
      { ...current, stderr: current.stderr + current.stderr },
      { ...current, stderr: current.stderr.replace('"fields":{}', '"fields":{"extra":"unknown"}') },
      { ...current, stderr: current.stderr.replace('"level":"error"', '"level":"info"') },
      { ...current, timedOut: true }, { ...current, overflow: true },
    ]) assert.equal(classify(altered, "emulator-5562"), false);
  }
});
