import { test } from "node:test";
import assert from "node:assert/strict";
import { nativeTree, postbootPrimaryObservation, NativeTreePartial, primaryObservation, ownedPrimaryObservation, InitialObservation, BootTransition, reconnectObservation } from "./native-tree.ts";
import { invocation, response, NativeTreeUnavailable, NativeTreeReadTimeout, NativeTreeReconnectStale, NativeSession } from "./native-session.ts";
import { editorValue, editorCaretAtEnd, InstallerUi, center, parseUi } from "./installer-ui.ts";
import { mkdirSync, rmSync, readFileSync } from "node:fs";
import { resolve, join } from "node:path";

const fence = { sessionId: "a".repeat(32), pid: 123, bootId: "aaaaaaaa-1111-2222-3333-444444444444", profile: "free-09" };
function migrated(v: any): any {
 v=structuredClone(v);
 v.format="voyemu.ui.v1";v.source="android.ui";
 // test_api.cpp publishes top-level ready, while a complete screen publishes ok.
 v.status=v.complete?"ready":"partial";
 v.started_uptime_ms=String(v.started_uptime_ms);v.completed_uptime_ms=String(v.completed_uptime_ms);
 v.host_interval={clock_domain:"host.steady",clock_id:v.session_id,start_ns:String(v.host_interval_us[0]*1000),end_ns:String(v.host_interval_us[1]*1000)};
 delete v.host_interval_us;
 v.screens=v.surfaces;delete v.surfaces;
 for(const screen of v.screens) {
  screen.width=1920; screen.height=720;
  // Historical wire used actionable for the AX candidate, not dispatch authority.
  for(const node of screen.nodes) { node.actionable_candidate=node.actionable; node.actionable=false; node.occlusion="unknown"; node.touchability="unknown"; }
  screen.screen=screen.surface==="media_primary"?"primary":"secondary";screen.sdk_display_id=screen.screen==="primary"?0:1;delete screen.surface;
  for(const row of [...screen.windows,...screen.nodes])for(const key of ["bounds","clipped_bounds"])if(Array.isArray(row[key])){const [x,y,r,b]=row[key];row[key]={x,y,width:r-x,height:b-y};}
 }
 return v;
}
function fixture(): any {
  const node = { id: "1", parent: null, window_id: 7, package: "oem.settings", resource_id: "oem.settings:id/root", class: "android.widget.FrameLayout", text: "设置", description: "", text_redacted: false, bounds: [0, 0, 1920, 720], clipped_bounds: [0, 0, 1920, 720], visible: true, enabled: true, clickable: false, checkable: false, checked: false, selected: false, scrollable: false, focused: false, actionable: false, identity_scope: "this_observation_only", actionable_basis: "node_state_and_viewport_only" };
  return migrated({ format: "voyemu.ui-tree.v1", source: "guest_accessibility", profile: fence.profile, session_id: fence.sessionId, android_pid: 123, boot_id: fence.bootId, observer_pid: 456, observation_id: "cccccccc-1111-2222-3333-444444444444", started_uptime_ms: 100, completed_uptime_ms: 120, host_interval_us: [1000, 22000], complete: true, atomic_ui_state: false, surfaces: [{ surface: "media_primary", display_id: 0, status: "ok", complete: true, unavailable_windows: [], scope: "api28_default_display", atomic_ui_state: false, windows: [{ id: 7, type: 1, layer: 3, active: true, input_focused: true, accessibility_focused: false, title: "Settings", bounds: [0, 0, 1920, 720], package: "oem.settings", component: null, identity_basis: "accessibility_window_id_and_metadata" }], nodes: [node, { ...node, id: "2", parent: "1", bounds: [-10, 10, 100, 50], clipped_bounds: [0, 10, 100, 50], text: "Русский", description: "Language", resource_id: "oem.settings:id/radio", class: "android.widget.RadioButton", clickable: true, actionable: true, checkable: true, checked: true, selected: true }] }] });
}

function uiWire(tree: any = fixture()) {
  const { screens, profile, session_id, status, complete, atomic_ui_state, format, started_uptime_ms, completed_uptime_ms, ...metadata } = tree;
  return { format: "voyemu.session.ui.v1", profile, session_id, status, complete, atomic_ui_state, cluster: null,
    multimedia: [{ ...screens[0], ...metadata, source_timestamp_ms: completed_uptime_ms }, null] };
}

test("native tree maps observed parent IDs, clipped geometry, Unicode and radio states", () => {
  const nodes = nativeTree(fixture(), fence);
  assert.equal(nodes[1].parent, 0); assert.deepEqual(nodes[1].bounds, [0, 10, 100, 50]); assert.deepEqual(nodes[1].rawBounds, [-10, 10, 100, 50]);
  assert.equal(nodes[1].text, "Русский"); assert.equal(nodes[1].checked, true); assert.equal(nodes[1].selected, true); assert.equal(nodes[1].checkable, true);
  assert.equal(nodes[0].windowRef, nodes[1].windowRef); assert.equal(nodes.windowRefs?.length, 1); assert.equal(nodes[1].windowRef?.captureEnd, 120);
  assert.equal(nodes[1].windowRef?.scopeBasis, "voyemu-guest-accessibility"); assert.equal(nodes[1].focusable, undefined); assert.equal(nodes[1].index, undefined);
});
test("native tree rejects foreign session/runtime/boot/profile and incomplete or secondary trees", () => {
  for (const change of [{ session_id: "foreign" }, { android_pid: 124 }, { boot_id: "bbbbbbbb-1111-2222-3333-444444444444" }, { profile: "other" }, { complete: false }, { atomic_ui_state: true }, { selector: { resource_id: "x" } }, { completed_uptime_ms: 99 }]) assert.throws(() => nativeTree({ ...fixture(), ...change }, fence));
  for (const change of [{ screen: "secondary", sdk_display_id: 1, display_id: 1 }, { status: "unsupported" }, { complete: false }, { status: "partial" }, { unavailable_windows: [7] }]) { const v = fixture(); Object.assign(v.screens[0], change); assert.throws(() => nativeTree(v, fence)); }
});
test("native tree rejects malformed bounds, leaked passwords, duplicate IDs and foreign parents", () => {
  for (const change of [{ bounds: [100, 20, 10, 30] }, { clipped_bounds: [-10, 10, 100, 50] }, { clipped_bounds: [0, 10, 99, 50] }, { text_redacted: true }, { id: "1" }, { parent: "2" }, { parent: "missing" }, { window_id: 8 }, { visible: false }, { enabled: false }, { actionable_candidate: false }, { actionable: true }, { occlusion: "clear" }, { touchability: "touchable" }]) { const v = fixture(); Object.assign(v.screens[0].nodes[1], change); assert.throws(() => nativeTree(v, fence)); }
  const v = fixture(); Object.assign(v.screens[0].nodes[1], { text_redacted: true, text: "", description: "" }); assert.equal(nativeTree(v, fence)[1].password, true);
});
test("native tree keeps Accessibility type -1 and rejects invented activity tokens", () => {
  const v = fixture(); v.screens[0].windows[0].type = -1; assert.equal(nativeTree(v, fence)[0].windowRef?.type, -1);
  v.screens[0].windows[0].component = "invented.activity"; assert.throws(() => nativeTree(v, fence));
});
test("native invocation requests only primary; TREE errors reject rather than empty tree", () => {
  const config = { executable: "/runtime/voyemu", root: "/runtime", profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } };
  const i = invocation(config, "session.ui", "/runtime/request.json", { screen: "primary", timeout_ms: 5000 });
  assert.equal(i.request.op, "session.ui"); assert.deepEqual(i.request.screen, "primary");
  for (const code of ["TREE_STALE", "TREE_TIMEOUT", "TREE_PROTOCOL", "TEST_UNSUPPORTED"]) assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify({ ok: false, error: { code } }) }, i.request));
});
test("InstallerUi native snapshots preserve gesture authority and serialize observations without CLI snapshots", async () => {
  const out = resolve("native-tree-test-" + process.pid + "-" + Date.now()); mkdirSync(out, { mode: 0o700 });
  let nativeCalls = 0, cliCalls = 0, release: (() => void) | undefined;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5554", join(out, "ui"), async () => { cliCalls++; throw new Error("unexpected CLI"); }, async () => {}, async () => { nativeCalls++; if (nativeCalls === 2) await new Promise<void>(r => { release = r; }); return nativeTree(fixture(), fence); });
    const first = await ui.snapshot(); assert.equal(nativeCalls, 1); assert.equal(cliCalls, 0);
    const authority = ui.gestureAuthority(first[1]); assert.equal((authority.expectedWindow as any).id, 7); assert.equal((authority.expectedTarget as any).bounds, "[-10,10][100,50]");
    const pending = ui.snapshot(); await new Promise(r => setImmediate(r));
    await assert.rejects(ui.snapshot(), /serialize UI actions/); await assert.rejects(ui.call({ action: "snapshot" }), /serialize UI actions/);
    assert.throws(() => ui.gestureAuthority(first[1]), /stale/); release!(); await pending; assert.equal(cliCalls, 0);
  } finally { rmSync(out, { recursive: true, force: true }); }
});
test("native snapshot rejection remains rejection and does not fall back to installer", async () => {
  const out = resolve("native-tree-error-test-" + process.pid + "-" + Date.now()); mkdirSync(out, { mode: 0o700 });
  let calls = 0;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5554", join(out, "ui"), async () => { calls++; throw new Error("unexpected CLI"); }, async () => {}, async () => { throw new Error("TREE_STALE"); });
    await assert.rejects(ui.snapshot(), /TREE_STALE/); assert.equal(calls, 0);
  } finally { rmSync(out, { recursive: true, force: true }); }
});

test("retained native tree proof validates raw response bytes, request correlation and boot context", async () => {
  const { nativeTreeAction } = await import("../../release/emulator-gate.ts");
  const { writeFileSync } = await import("node:fs"), { createHash } = await import("node:crypto");
  const out = resolve("native-tree-proof-test-" + process.pid + "-" + Date.now()); mkdirSync(out, { mode: 0o700 });
  const digest = (s: string) => createHash("sha256").update(s).digest("hex");
  try {
    const request = { schema: 2, request_id: "request", op: "session.ui", profile: fence.profile, session: { id: fence.sessionId }, screen: "primary", timeout_ms: 5000 };
    const response = { schema: 2, request_id: "request", operation_id: "c".repeat(32), ok: true, elapsed_ms: 1, result: uiWire() };
    const requestFile = join(out, "request.json"), stdoutFile = join(out, "stdout.json");
    writeFileSync(requestFile, JSON.stringify(request)); writeFileSync(stdoutFile, JSON.stringify(response));
    const runtime = { sessionId: fence.sessionId, pid: fence.pid, serial: "emulator-5554" };
    const { verificationContract } = await import("./native-session.ts");
    const evidence = { schemaVersion: 4, verificationContract, runtime, voyemuProfile: fence.profile, identity: { package: "kit" } };
    const action = { nativeOperation: "session.ui", nativeRequest: requestFile, args: ["test", "--root", "/root", "--request", requestFile], exitCode: 0, timedOut: false, signal: null, overflow: false, context: { runtime, serial: runtime.serial, kit: "kit", bootId: fence.bootId }, artifacts: [{ kind: "request", path: requestFile, sha256: digest(JSON.stringify(request)) }, { kind: "stdout", path: stdoutFile, sha256: digest(JSON.stringify(response)) }] };
    assert.equal(nativeTreeAction(evidence, action)[1].checked, true);
    for (const change of [{ exitCode: 4 }, { timedOut: true }, { nativeOperation: "session.screenshot" }, { context: { ...action.context, bootId: "bbbbbbbb-1111-2222-3333-444444444444" } }, { context: { ...action.context, runtime: { ...runtime, pid: 999 } } }]) assert.throws(() => nativeTreeAction(evidence, { ...action, ...change }));
    writeFileSync(stdoutFile, JSON.stringify({ ...response, request_id: "foreign" })); assert.throws(() => nativeTreeAction(evidence, action), /retained response differs/);
    action.artifacts[1].sha256 = digest(JSON.stringify({ ...response, request_id: "foreign" })); assert.throws(() => nativeTreeAction(evidence, action), /response authority/);
  } finally { rmSync(out, { recursive: true, force: true }); }
});


test("historical payload migrated to schema 2: native primary tree retains invisible inverted OEM geometry without making it actionable", () => {
  const wire = JSON.parse(readFileSync(new URL("../../testdata/emulator/native161-primary-tree.json", import.meta.url), "utf8"));
  const v = migrated(wire.result), observedFence = { sessionId: v.session_id, pid: v.android_pid, bootId: v.boot_id, profile: v.profile };
  const nodes = nativeTree(v, observedFence);
  assert.equal(nodes.length, 116);
  const hidden = nodes.find(n => n.text === "俄语")!;
  assert.ok(hidden); assert.equal(hidden.visibleToUser, false); assert.deepEqual(hidden.rawBounds, [517, 50, 637, -1006]);
  assert.throws(() => center(hidden), /outside/);
  const uninstall = nodes.find(n => n.text === "卸载 Voboost")!;
  assert.ok(uninstall); assert.equal(uninstall.visibleToUser, true); assert.deepEqual(uninstall.bounds, [515, 585, 758, 655]);
  const forged = structuredClone(v), original = forged.screens[0].nodes.find((n: any) => n.text === "俄语");
  original.visible = true; assert.throws(() => nativeTree(forged, observedFence), /bounds/);
  original.visible = false; original.actionable = true; assert.throws(() => nativeTree(forged, observedFence), /actionable/);
});


test("historical payload migrated to schema 2: sequential native and CLI observations preserve visible semantics without equating local window IDs", () => {
  const v = JSON.parse(readFileSync(new URL("../../testdata/emulator/native161-primary-tree.json", import.meta.url), "utf8")).result;
  const native = nativeTree(migrated(v), { sessionId: v.session_id, pid: v.android_pid, bootId: v.boot_id, profile: v.profile });
  const cli = parseUi(readFileSync(new URL("../../testdata/emulator/native161-primary-cli.xml", import.meta.url), "utf8"));
  const semantic = (node: any) => ({ package: node.packageName, resource: node.resource, class: node.className, text: node.text, description: node.description, rawBounds: node.rawBounds, checked: node.checked, selected: node.selected, checkable: node.checkable, clickable: node.clickable, enabled: node.enabled, scrollable: node.scrollable, focused: node.focused, visible: node.visibleToUser, window: { type: node.windowRef.type, layer: node.windowRef.layer, active: node.windowRef.active, inputFocused: node.windowRef.inputFocused, bounds: node.windowRef.bounds, title: node.windowRef.title, rootPackage: node.windowRef.rootPackage } });
  const signature = (nodes: any[], visibleOnly: boolean) => nodes.filter(n => !visibleOnly || n.visibleToUser).map(n => JSON.stringify({ node: semantic(n), parent: n.parent === null ? null : semantic(nodes[n.parent]) })).sort();
  assert.equal(native.length, 116); assert.equal(cli.length, 116);
  assert.equal(native.filter(n => n.visibleToUser).length, 61); assert.equal(cli.filter(n => n.visibleToUser).length, 61);
  assert.deepEqual(signature(native, true), signature(cli, true));
  assert.deepEqual(signature(native, false), signature(cli, false));
  assert.notEqual(native[0].windowRef?.id, cli[0].windowRef?.id);
  const uninstall = native.find(n => n.text === "卸载 Voboost")!, other = cli.find(n => n.text === "卸载 Voboost")!;
  assert.deepEqual(uninstall.bounds, other.bounds); assert.notEqual(uninstall.windowRef?.capture, other.windowRef?.capture);
});


const rejectedWire = () => ({ ...JSON.parse(readFileSync(new URL("../../testdata/emulator/native161-tree-after-cancel-rejected.json", import.meta.url), "utf8")), schema: 2 });
function actualRejected(): NativeTreeUnavailable {
  const v = rejectedWire();
  try { response({ exitCode: 4, stdout: JSON.stringify(v) }, { schema: 2, request_id: v.request_id, op: "session.ui", profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } }); }
  catch (error) { assert.ok(error instanceof NativeTreeUnavailable); return error; }
  throw new Error("rejection was not propagated");
}
test("historical payload migrated to schema 2: post-cancel rejection retains not_started and retryable false without accepting protocol substitutes", () => {
  const error = actualRejected(); assert.equal(error.code, "TREE_REJECTED"); assert.equal(error.retryable, false); assert.equal(error.outcome, "not_started");
  const v = rejectedWire(), q = { schema: 2 as const, request_id: v.request_id, op: "session.ui", profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } };
  for (const change of [{ code: "TREE_STALE" }, { code: "TREE_TIMEOUT" }, { code: "TREE_PROTOCOL" }, { outcome: "unknown" }, { retryable: true }, { message: "foreign" }, { injected: true }]) {
    const changed = { ...v, error: { ...v.error, ...change } };
    assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify(changed) }, q), e => !(e instanceof NativeTreeUnavailable));
  }
  for (const change of [{ request_id: "foreign" }, { operation_id: "foreign" }, { schema: 1 }, { elapsed_ms: -1 }, { injected: true }]) assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify({ ...v, ...change }) }, q), e => !(e instanceof NativeTreeUnavailable));
  assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify(v), timedOut: true }, q), e => !(e instanceof NativeTreeUnavailable));
});
test("post-transition condition makes fresh bounded reads with epoch guards and never replays input", async () => {
  let clock = 0, reads = 0, guards = 0;
  const observed = await primaryObservation(async remaining => { assert.ok(remaining <= 5000); reads++; if (reads === 1) throw actualRejected(); return nativeTree(fixture(), fence); }, async () => { guards++; }, 5000, () => clock, async ms => { assert.equal(ms, 50); clock += ms; });
  assert.equal(observed.length, 2); assert.equal(reads, 2); assert.equal(guards, 1);
});
test("post-transition read condition uses its one monotonic deadline for transient failures", async () => {
  let reads = 0, clock = 0;
  await assert.rejects(primaryObservation(async () => { reads++; throw actualRejected(); }, async () => {}, 5000, () => clock, async ms => { clock += ms; }), NativeTreeUnavailable);
  assert.equal(reads, 100); assert.equal(clock, 5000); reads = 0; clock = 0;
  await assert.rejects(primaryObservation(async () => { reads++; clock = 5000; throw actualRejected(); }, async () => { throw new Error("unexpected guard"); }, 5000, () => clock), NativeTreeUnavailable);
  assert.equal(reads, 1);
  clock = 0;
  await assert.rejects(primaryObservation(async () => { clock = 6000; return []; }, async () => {}, 5000, () => 0 === clock ? 0 : clock), /deadline/);
});
test("post-transition read condition fails closed on stale boot protocol timeout and runtime lease changes", async () => {
  for (const code of ["TREE_STALE", "TREE_TIMEOUT", "TREE_PROTOCOL", "transport unresolved"]) {
    let reads = 0, guards = 0;
    await assert.rejects(primaryObservation(async () => { reads++; throw new Error(code); }, async () => { guards++; }, 5000, () => 0), new RegExp(code));
    assert.equal(reads, 1); assert.equal(guards, 0);
  }
  for (const reason of ["crossed a boot", "runtime lease changed"]) {
    let reads = 0;
    await assert.rejects(primaryObservation(async () => { reads++; throw actualRejected(); }, async () => { throw new Error(reason); }, 5000, () => 0), new RegExp(reason));
    assert.equal(reads, 1);
  }
});
test("fresh native read requests retain the failed raw operation and use new request IDs", async () => {
  const { writeFileSync } = await import("node:fs");
  const out = resolve("native-tree-fresh-requests-" + process.pid + "-" + Date.now()); mkdirSync(out, { mode: 0o700 });
  try {
    const executable = join(out, "voyemu"); writeFileSync(executable, "host fixture executable");
    const requests: any[] = [], retained: any[] = []; let clock = 0, guards = 0;
    const client = new NativeSession({ executable, root: out, profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } }, join(out, "requests"), async inv => {
      const q = JSON.parse(readFileSync(inv.args[4], "utf8")); requests.push(q);
      const failed = requests.length === 1;
      const raw = rejectedWire();
      const tree = uiWire();
      if (failed) {
        tree.status = "unavailable";
        tree.complete = false;
        tree.multimedia[0] = { screen: "primary", status: "unavailable", complete: false,
          nodes: [], windows: [], error_code: raw.error.code, error: raw.error };
      }
      const wire = { schema: 2, request_id: q.request_id, operation_id: "d".repeat(32), ok: true, elapsed_ms: 1, result: tree };
      const result = { exitCode: 0, stdout: JSON.stringify(wire) }; retained.push(result); return result;
    }, () => ({ width: 1920, height: 720 }));
    const result = await primaryObservation(async remaining => nativeTree(await client.primaryMultimediaUi(remaining), fence), async () => { guards++; }, 5000, () => clock, async ms => { clock += ms; });
    assert.equal(result.length, 2); assert.equal(guards, 1); assert.equal(requests.length, 2);
    assert.notEqual(requests[0].request_id, requests[1].request_id); assert.deepEqual(requests.map(q => q.op), ["session.ui", "session.ui"]);
    assert.equal(retained[0].exitCode, 0); assert.equal(JSON.parse(retained[0].stdout).result.multimedia[0].error.retryable, false); assert.equal(retained[1].exitCode, 0);
  } finally { rmSync(out, { recursive: true, force: true }); }
});


const reconnectWire = () => ({ ...JSON.parse(readFileSync(new URL("../../testdata/emulator/native165-tree-reconnect-stale.json", import.meta.url), "utf8")), schema: 2 });
function actualReconnect(): NativeTreeReconnectStale {
  const wire = reconnectWire();
  const request = { schema: 2 as const, request_id: wire.request_id, op: "session.ui", profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } };
  try { response({ exitCode: 4, stdout: JSON.stringify(wire) }, request); }
  catch (error) { assert.ok(error instanceof NativeTreeReconnectStale); return error; }
  throw new Error("reconnect refusal was not preserved");
}
test("historical payload migrated to schema 2: reconnect refusal requires exact current CLI protocol, never generic stale", () => {
  const wire = reconnectWire(), request = { schema: 2 as const, request_id: wire.request_id, op: "session.ui", profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } };
  assert.equal(actualReconnect().operationId, wire.operation_id);
  for (const change of [{ outcome: "unknown" }, { retryable: true }, { message: "TREE_STALE: session or guest lifetime changed during observation" }, { code: "TREE_TRANSPORT" }, { extra: 1 }])
    assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify({ ...wire, error: { ...wire.error, ...change } }) }, request), e => !(e instanceof NativeTreeReconnectStale));
  for (const change of [{ exitCode: 1 }, { exitCode: 0 }, { timedOut: true }, { signal: "SIGTERM" }, { overflow: true }])
    assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify(wire), ...change }, request), e => !(e instanceof NativeTreeReconnectStale));
  for (const change of [{ request_id: "foreign" }, { operation_id: "foreign" }, { schema: 1 }, { elapsed_ms: -1 }, { injected: true }])
    assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify({ ...wire, ...change }) }, request), e => !(e instanceof NativeTreeReconnectStale));
  assert.throws(() => response({ exitCode: 4, stdout: JSON.stringify(wire) }, { ...request, op: "input.pointer.tap" }), e => !(e instanceof NativeTreeReconnectStale));
  const rejected = rejectedWire();
  assert.throws(() => response({ exitCode: 1, stdout: JSON.stringify(rejected) }, { ...request, request_id: rejected.request_id }), e => !(e instanceof NativeTreeUnavailable));
});
test("transition capability consumes the first attempt and rejects foreign session/PID/boot", () => {
  const old = "bbbbbbbb-1111-2222-3333-444444444444";
  const transition = new BootTransition();
  assert.equal(transition.consume(fence), false);
  assert.throws(() => transition.arm(fence.bootId, fence));
  for (const changed of [{ sessionId: "c".repeat(32) }, { pid: 124 }, { bootId: old }, { profile: "other" }]) {
    transition.arm(old, fence);
    assert.throws(() => transition.consume({ ...fence, ...changed }));
    assert.equal(transition.consume(fence), false);
  }
  transition.arm(old, fence); assert.equal(transition.consume(fence), true); assert.equal(transition.consume(fence), false);
});
test("one new independent read can observe actual recovered NEW boot without reusing failed operation", async () => {
  const wire = JSON.parse(readFileSync(new URL("../../testdata/emulator/native165-tree-after-reconnect.json", import.meta.url), "utf8"));
  assert.equal(wire.ok, true); assert.notEqual(wire.operation_id, reconnectWire().operation_id); assert.notEqual(wire.request_id, reconnectWire().request_id);
  let reads = 0, checks = 0;
  const nodes = await reconnectObservation(async () => {
    if (++reads === 1) throw actualReconnect();
    const v = migrated(wire.result);
    return nativeTree(v, { sessionId: v.session_id, pid: v.android_pid, bootId: v.boot_id, profile: v.profile });
  }, async () => { checks++; }, true, 5000, () => 0);
  assert.equal(reads, 2); assert.equal(checks, 2); assert.equal(nodes.length, 99);
});
test("reconnect capability never authorizes another stale, unknown failure or changed epoch", async () => {
  let reads = 0;
  await assert.rejects(reconnectObservation(async () => { reads++; throw actualReconnect(); }, async () => {}, false, 5000, () => 0), NativeTreeReconnectStale);
  assert.equal(reads, 1);
  reads = 0;
  await assert.rejects(reconnectObservation(async () => { reads++; throw actualReconnect(); }, async () => {}, true, 5000, () => 0), NativeTreeReconnectStale);
  assert.equal(reads, 2);
  for (const message of ["SDK PID changed", "session changed", "boot changed"]) {
    reads = 0;
    await assert.rejects(reconnectObservation(async () => { reads++; throw actualReconnect(); }, async () => { throw new Error(message); }, true, 5000, () => 0), new RegExp(message));
    assert.equal(reads, 1);
  }
  reads = 0;
  await assert.rejects(reconnectObservation(async () => { reads++; throw new Error("unknown"); }, async () => {}, true, 5000, () => 0), /unknown/);
  assert.equal(reads, 1);
});
test("reconnect read keeps original deadline and rejects boot change during successful NEW read", async () => {
  let time = 0, reads = 0, checks = 0;
  await assert.rejects(reconnectObservation(async () => { reads++; throw actualReconnect(); }, async () => { time = 5000; }, true, 5000, () => time), /deadline/);
  assert.equal(reads, 1);
  time = 0; reads = 0;
  await assert.rejects(reconnectObservation(async () => { if (++reads === 1) throw actualReconnect(); return "tree"; }, async () => { if (++checks === 2) throw new Error("boot changed during read"); }, true, 5000, () => time), /boot changed/);
  assert.equal(reads, 2);
  reads = 0; checks = 0;
  await assert.rejects(reconnectObservation(async () => { reads++; time = 5000; return "tree"; }, async () => { checks++; }, true, 5000, () => time), /deadline/);
  assert.equal(reads, 1); assert.equal(checks, 1);
});


test("reconnect recovery dispatches exactly two NEW read UUIDs and retains exact failed action", async () => {
  const { writeFileSync } = await import("node:fs");
  const out = resolve("native-reconnect-requests-" + process.pid + "-" + Date.now()); mkdirSync(out, { mode: 0o700 });
  try {
    const executable = join(out, "voyemu"); writeFileSync(executable, "host fixture executable");
    const requests: any[] = [], retained: any[] = [];
    const client = new NativeSession({ executable, root: out, profile: fence.profile, session: { id: fence.sessionId, ownership_id: "b".repeat(32) } }, join(out, "requests"), async inv => {
      const request = JSON.parse(readFileSync(inv.args[4], "utf8")); requests.push(request);
      const failure = requests.length === 1;
      const wire = failure ? { ...reconnectWire(), request_id: request.request_id } : { schema: 2, request_id: request.request_id, operation_id: "e".repeat(32), ok: true, elapsed_ms: 1, result: uiWire() };
      const result = { exitCode: failure ? 4 : 0, stdout: JSON.stringify(wire) }; retained.push(result); return result;
    }, () => ({ width: 1920, height: 720 }));
    const lease = new InitialObservation(), transitions = new BootTransition();
    let checks = 0;
    const nodes = await ownedPrimaryObservation(async remaining => nativeTree(await client.primaryMultimediaUi(remaining), fence),
      async () => { checks++; }, fence, lease, transitions, 5000, () => 0);
    assert.equal(checks, 3);
    assert.equal(lease.consume(fence), false);
    assert.equal(nodes.length, 2); assert.equal(requests.length, 2); assert.notEqual(requests[0].request_id, requests[1].request_id);
    assert.deepEqual(requests.map(q => q.op), ["session.ui", "session.ui"]);
    assert.equal(retained[0].exitCode, 4); assert.equal(JSON.parse(retained[0].stdout).ok, false);
    assert.equal(JSON.parse(retained[0].stdout).error.retryable, false);
    assert.equal(JSON.parse(retained[0].stdout).operation_id, reconnectWire().operation_id);
    assert.notEqual(JSON.parse(retained[0].stdout).operation_id, JSON.parse(retained[1].stdout).operation_id);
  } finally { rmSync(out, { recursive: true, force: true }); }
});

 test("AX candidates stay non-authoritative with unknown occlusion and explicit screen geometry", () => {
  const v = fixture();
  assert.equal(v.screens[0].nodes[1].actionable_candidate, true);
  assert.equal(v.screens[0].nodes[1].actionable, false);
  v.read_attempts = 2; v.transient_rejections = ["stale_node"];
  assert.equal(nativeTree(v, fence).length, 2);
  for (const geometry of [{ width: 1280 }, { height: 1080 }, { width: undefined }]) {
    const changed = structuredClone(v); Object.assign(changed.screens[0], geometry);
    assert.throws(() => nativeTree(changed, fence), /primary arrays/);
  }
  const hidden = v.screens[0].nodes[1];
  Object.assign(hidden, { visible: false, actionable_candidate: false, bounds: { x: 2000, y: 800, width: -1, height: -1 }, clipped_bounds: { x: 1920, y: 720, width: 0, height: 0 } });
  const nodes = nativeTree(v, fence);
  assert.equal(nodes[1].visibleToUser, false); assert.throws(() => center(nodes[1]), /outside/);
 });

function partialFixture(): any {
  const v = fixture(); v.complete = false; v.status = "partial";
  const s = v.screens[0]; s.complete = false; s.status = "partial";
  s.windows.push({ ...s.windows[0], id: 18 }); s.unavailable_windows = [18];
  return v;
}
test("strictly fenced partial read waits without returning partial input nodes", async () => {
  assert.throws(() => nativeTree(partialFixture(), fence), NativeTreePartial);
  let reads = 0, guards = 0, clock = 0;
  const nodes = await primaryObservation(async () => nativeTree(++reads === 1 ? partialFixture() : fixture(), fence), async () => { guards++; }, 5000, () => clock, async ms => { clock += ms; });
  assert.equal(nodes.length, 2); assert.equal(reads, 2); assert.equal(guards, 1);
});
test("partial read never retries foreign authority, malformed nodes or contradictory status", () => {
  for (const mutate of [(v:any) => { v.session_id = "foreign"; }, (v:any) => { v.screens[0].nodes[1].parent = "missing"; }, (v:any) => { v.status = "unknown"; }, (v:any) => { v.screens[0].complete = true; }, (v:any) => { v.screens[0].unavailable_windows = [999]; }]) {
    const v = partialFixture(); mutate(v);
    assert.throws(() => nativeTree(v, fence), error => error instanceof Error && !(error instanceof NativeTreePartial));
  }
});
test("persistent partial read stops at the original deadline", async () => {
  let reads = 0, clock = 0;
  await assert.rejects(primaryObservation(async () => { reads++; return nativeTree(partialFixture(), fence); }, async () => {}, 5000, () => clock, async ms => { clock += ms; }), NativeTreePartial);
  assert.equal(reads, 100); assert.equal(clock, 5000);
});

test("current producer uses top-level ready and screen ok for a complete primary observation", () => {
  const v = fixture();
  assert.equal(v.status, "ready"); assert.equal(v.screens[0].status, "ok");
  assert.equal(nativeTree(v, fence).length, 2);
  for (const status of ["ok", "partial", "unknown", undefined]) {
    assert.throws(() => nativeTree({ ...v, status }, fence), /completeness/);
  }
});

test("initial owned read consumes admission on success and never grants later reconnect", async () => {
  const initial = new InitialObservation(), transitions = new BootTransition();
  assert.equal(await ownedPrimaryObservation(async () => "ready", async () => {}, fence, initial, transitions, 5000, () => 0), "ready");
  let reads = 0;
  await assert.rejects(ownedPrimaryObservation(async () => { reads++; throw actualReconnect(); },
    async () => {}, fence, initial, transitions, 5000, () => 0), NativeTreeReconnectStale);
  assert.equal(reads, 1);
});
test("initial owned read keeps guards, deadline and exact one-read recovery bound", async () => {
  for (const at of [1, 2, 3]) {
    let checks = 0, reads = 0;
    await assert.rejects(ownedPrimaryObservation(async () => { if (++reads === 1) throw actualReconnect(); return "ready"; },
      async () => { if (++checks === at) throw new Error("owned epoch changed"); }, fence,
      new InitialObservation(), new BootTransition(), 5000, () => 0), /owned epoch changed/);
    assert.equal(reads, at === 1 ? 0 : at === 2 ? 1 : 2);
  }
  let reads = 0;
  await assert.rejects(ownedPrimaryObservation(async () => { reads++; throw actualReconnect(); },
    async () => {}, fence, new InitialObservation(), new BootTransition(), 5000, () => 0), NativeTreeReconnectStale);
  assert.equal(reads, 2);
  let time = 0;
  await assert.rejects(ownedPrimaryObservation(async () => { throw actualReconnect(); },
    async () => { if (time === 1) time = 5000; else time = 1; }, fence,
    new InitialObservation(), new BootTransition(), 5000, () => time), /deadline/);
});

import { stripTypeScriptTypes } from "node:module";
test("actual primary callback keeps fresh probe and rejects external boot before tree", async () => {
 const gate=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const begin=gate.indexOf("        // Keep independent root/Enforcing boot inspection");
 const end=gate.indexOf("      } : undefined,",begin);
 assert(begin>0 && end>begin);
 const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
 const run=new AsyncFunction("currentBoot","probe","ownership","native","runtime","c","ownedPrimaryObservation","initialObservation","bootTransitions","nativeTree","evidence","persist",
 `const firstBootCaptureDeadline=null,settingsNavigationDeadline=null,frozen={package:"kit"},serial="emulator-5574",language="en";let nativeInputTree,snapshotBoot;return ${stripTypeScriptTypes("(async () => {"+gate.slice(begin,end)+"})")}();`);
 for (const known of [true,false]) {
  let probes=0,owned=0,reads=0;
  const initial=new InitialObservation(); initial.consume(fence);
  const evidence={actions:[] as any[]};
  const nodes=await run(known?fence.bootId:null,async()=>{probes++;return fence.bootId;},async()=>{owned++;},
   {primaryMultimediaUi:async()=>{reads++;evidence.actions.push({nativeOperation:"session.ui"});return fixture();}},
   fence,{voyemuProfile:fence.profile},ownedPrimaryObservation,initial,new BootTransition(),nativeTree,evidence,()=>{});
  assert.equal(nodes.length,2);assert.equal(probes,known?1:2);assert.equal(owned,1);assert.equal(reads,1);
  assert.deepEqual(evidence.actions[0].context, { kit: "kit", runtime: fence, serial: "emulator-5574", bootId: fence.bootId, locale: "en" });
 }
 let reads=0;
 await assert.rejects(run(fence.bootId,async()=>"bbbbbbbb-1111-2222-3333-444444444444",async()=>{},
  {primaryMultimediaUi:async()=>{reads++;}},fence,{voyemuProfile:fence.profile},ownedPrimaryObservation,new InitialObservation(),new BootTransition(),nativeTree,{actions:[]},()=>{}),/crossed a boot/);
 assert.equal(reads,0);
 const invoke = (tree: () => Promise<unknown>, evidence: { actions: any[] }, persist = () => {}) => {
  const initial = new InitialObservation(); initial.consume(fence);
  return run(fence.bootId, async () => fence.bootId, async () => {}, { primaryMultimediaUi: tree }, fence,
   { voyemuProfile: fence.profile }, ownedPrimaryObservation, initial, new BootTransition(), nativeTree, evidence, persist);
 };
 const preceding = { nativeOperation: "session.status" };
 const rejectedEvidence = { actions: [preceding] };
 const beforeDispatch = new Error("native tree read deadline differs");
 await assert.rejects(invoke(async () => { throw beforeDispatch; }, rejectedEvidence), error => error === beforeDispatch);
 assert.deepEqual(rejectedEvidence.actions, [preceding]);
 const wrongEvidence = { actions: [preceding] };
 await assert.rejects(invoke(async () => { wrongEvidence.actions.push({ nativeOperation: "session.status" }); throw beforeDispatch; }, wrongEvidence), /native tree action correlation differs/);
 await assert.rejects(invoke(async () => fixture(), { actions: [preceding] }), /native tree action correlation differs/);
 const recordedEvidence: { actions: any[] } = { actions: [preceding] };
 const readFailure = new Error("empty_tree_unverified");
 let persisted = 0;
 await assert.rejects(invoke(async () => { recordedEvidence.actions.push({ nativeOperation: "session.ui", artifacts: ["raw-response"] }); throw readFailure; }, recordedEvidence, () => { persisted++; }), error => error === readFailure);
 assert.equal(persisted, 1);
 assert.deepEqual(recordedEvidence.actions[1].artifacts, ["raw-response"]);
 assert.deepEqual(recordedEvidence.actions[1].context, { kit: "kit", runtime: fence, serial: "emulator-5574", bootId: fence.bootId, locale: "en" });
});

const emptyPostboot = () => new NativeTreeUnavailable("r", "o", "TREE_REJECTED: guest could not verify current UI tree; reason=empty_tree_unverified");
test("proved postboot waits for one fresh valid read without replay", async () => {
 let reads=0, guards=0, clock=0;
 const value={fresh:true};
 assert.equal(await postbootPrimaryObservation(async ms=>{assert(ms<=5000);if(++reads===1)throw emptyPostboot();return value;},async()=>{guards++;},true,1000,()=>clock,async ms=>{clock+=ms;}),value);
 assert.equal(reads,2);assert.equal(guards,4);
});
test("postboot capability required, permanent empty bounded by original deadline", async()=>{
 let reads=0,clock=0;
 await assert.rejects(postbootPrimaryObservation(async()=>{reads++;return 1;},async()=>{},false,1000),/requires closed/);assert.equal(reads,0);
 await assert.rejects(postbootPrimaryObservation(async()=>{reads++;throw emptyPostboot();},async()=>{},true,100,()=>clock,async ms=>{clock+=ms;}),NativeTreeUnavailable);
 assert.equal(reads,2);assert.equal(clock,100);
});
test("postboot rejects epoch changes before and after read and rejects late valid value",async()=>{
 for(const at of [1,2]){let guards=0,reads=0;await assert.rejects(postbootPrimaryObservation(async()=>{reads++;return 1;},async()=>{if(++guards===at)throw Error("epoch changed");},true,1000,()=>0),/epoch changed/);assert.equal(reads,at-1);}
 let clock=0;await assert.rejects(postbootPrimaryObservation(async()=>{clock=1000;return 1;},async()=>{},true,1000,()=>clock),/deadline/);
});
test("postboot never broadens other errors or partial input authority",async()=>{
 for(const error of [new NativeTreePartial(),new Error("malformed"),new NativeTreeUnavailable("r","o"),new NativeTreeUnavailable("r","o","TREE_REJECTED: guest could not verify current UI tree; reason=window_changed")]){
 let reads=0;await assert.rejects(postbootPrimaryObservation(async()=>{reads++;throw error;},async()=>{},true,1000,()=>0),e=>e===error);assert.equal(reads,1);
 }
});

test("owned postboot uses original capture budget, initial admission keeps ordinary bounds",async()=>{
 const transitions=new BootTransition();transitions.arm("bbbbbbbb-1111-2222-3333-444444444444",fence);
 let clock=0,reads=0;
 assert.equal(await ownedPrimaryObservation(async()=>{if(++reads===1){clock=6000;throw emptyPostboot();}return "ready";},async()=>{},fence,new InitialObservation(),transitions,5000,()=>clock,10000),"ready");
 assert.equal(reads,2);
 reads=0;clock=0;
 await assert.rejects(ownedPrimaryObservation(async()=>{reads++;clock=6000;throw emptyPostboot();},async()=>{},fence,new InitialObservation(),new BootTransition(),5000,()=>clock,10000),NativeTreeUnavailable);
 assert.equal(reads,1);
});

test("native editor hint is distinct from a typed identical caption and absent metadata stays unknown", () => {
  const v = fixture(); v.screens[0].windows[0].package = "com.pateonavi.naviapp";
  for (const n of v.screens[0].nodes) n.package = "com.pateonavi.naviapp";
  const n = v.screens[0].nodes[1]; Object.assign(n, {class: "android.widget.EditText", focused: true, text: "Search for a destination", hint: "Search for a destination", showing_hint: true, selection_start: 0, selection_end: 0});
  let node = nativeTree(v, fence)[1]; assert.equal(node.text, n.text); assert.equal(editorValue(node), ""); assert.equal(editorCaretAtEnd(node), true);
  Object.assign(n, {showing_hint: false, selection_start: n.text.length, selection_end: n.text.length});
  node = nativeTree(v, fence)[1]; assert.equal(editorValue(node), n.text); assert.equal(editorCaretAtEnd(node), true);
  for (const key of ["hint", "showing_hint", "selection_start", "selection_end"]) delete n[key];
  node = nativeTree(v, fence)[1]; assert.equal(editorValue(node), n.text); assert.equal(node.showingHint, undefined); assert.equal(editorCaretAtEnd(node), false);
});
test("native editor metadata rejects partial, impossible selection, mismatched hint and password leakage", () => {
  for (const change of [{hint:"x"}, {hint:"x",showing_hint:true,selection_start:0,selection_end:0}, {hint:"",showing_hint:false,selection_start:-1,selection_end:0}, {hint:"",showing_hint:false,selection_start:0,selection_end:100}, {text_redacted:true,text:"",description:"",hint:"secret",showing_hint:false,selection_start:-1,selection_end:-1}]) {
    const v=fixture();Object.assign(v.screens[0].nodes[1],change);assert.throws(()=>nativeTree(v,fence));
  }
});

test("complete observation after more than three transient reads uses remaining deadline", async () => {
  let clock = 0, reads = 0, guards = 0;
  const nodes = await primaryObservation(async remaining => {
    assert.equal(remaining, 5000 - clock);
    reads++; clock += reads === 1 ? 218 : 28;
    if (reads < 5) {
      if (reads % 2) return nativeTree(partialFixture(), fence);
      throw actualRejected();
    }
    return nativeTree(fixture(), fence);
  }, async () => { guards++; clock += 430; }, 5000, () => clock, async ms => { clock += ms; });
  assert.equal(nodes.length, 2); assert.equal(reads, 5); assert.equal(guards, 4);
  assert.ok(clock < 5000);
});
test("epoch verification consuming the deadline prevents another observation", async () => {
  let clock = 0, reads = 0, pauses = 0;
  await assert.rejects(primaryObservation(async () => { reads++; throw new NativeTreePartial(); },
    async () => { clock = 5000; }, 5000, () => clock, async () => { pauses++; }), NativeTreePartial);
  assert.equal(reads, 1); assert.equal(pauses, 0);
});
test("complete read at the exact deadline is too late", async () => {
  let clock = 0;
  await assert.rejects(primaryObservation(async () => { clock = 5000; return nativeTree(fixture(), fence); },
    async () => {}, 5000, () => clock), /deadline exhausted/);
});


test("completed UI timeout permits a fresh bounded read after independent epoch check, without operation lookup or input replay", async () => {
 let now=0,reads=0;const events:string[]=[];
 const value=await primaryObservation(async remaining=>{events.push("read");assert.ok(remaining<=5000);reads++;if(reads===1){now+=5000;throw new NativeTreeReadTimeout("old-request","old-operation");}return "fresh";},async()=>{events.push("epoch");},12000,()=>now,async ms=>{now+=ms;});
 assert.equal(value,"fresh");assert.deepEqual(events,["read","epoch","read"]);
 // UI reads are deliberately non-durable in Voyemu; recovery does not issue operation.get.
 assert.equal(reads,2);
 now=0;reads=0;await assert.rejects(primaryObservation(async()=>{reads++;now+=5000;throw new NativeTreeReadTimeout("r","o");},async()=>{},10000,()=>now,async ms=>{now+=ms;}),NativeTreeReadTimeout);assert.equal(reads,2);
 reads=0;await assert.rejects(primaryObservation(async()=>{reads++;throw new NativeTreeReadTimeout("r","o");},async()=>{throw Error("boot changed");},10000,()=>0,async()=>{}),/boot changed/);assert.equal(reads,1);
 reads=0;await assert.rejects(primaryObservation(async()=>{reads++;throw Error("native operation unresolved");},async()=>{},10000,()=>0,async()=>{}),/unresolved/);assert.equal(reads,1);
});
