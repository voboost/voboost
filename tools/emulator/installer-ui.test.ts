import { deepStrictEqual, strictEqual, throws } from "node:assert";
import test from "node:test";
import { editorMetadata, editorValue, editorCaretAtEnd, center, parseUi, uniqueNode, validateRequest } from "./installer-ui.ts";

const xml=`<?xml version="1.0"?><hierarchy rotation="0"><node text="" bounds="[0,0][1920,720]" enabled="true"><node text="Русский &amp; English" resource-id="p:id/language" bounds="[50,100][250,150]" checked="true" enabled="true" clickable="true" /></node></hierarchy>`;
test("UI hierarchy preserves actual text selection bounds and parent identity",()=>{
    const nodes=parseUi(xml);
    strictEqual(nodes.length,2);
    strictEqual(nodes[1].text,"Русский & English");
    strictEqual(nodes[1].checked,true);
    strictEqual(nodes[1].parent,0);
    deepStrictEqual(center(uniqueNode(nodes,n=>n.resource==="p:id/language")),[150,125]);
});
test("UI selector never picks an ambiguous or off-display control",()=>{
    const nodes=parseUi(xml);
    throws(()=>uniqueNode([...nodes,nodes[1]],n=>n.resource==="p:id/language"),/2 visible/);
    throws(()=>uniqueNode(nodes,n=>n.text==="absent"),/0 visible/);
    throws(()=>center({...nodes[1],bounds:[-10,0,50,50]}),/outside/);
});
test("UI XML refuses entity declarations duplicate attributes invalid bounds and incomplete trees",()=>{
    for (const bad of [xml.replace("<hierarchy","<!DOCTYPE x><hierarchy"),xml.replace('text="Русский','text="other" text="Русский'),xml.replace("[50,100][250,150]","bad"),xml.replace("</node>",""),"<hierarchy></hierarchy>"])
        throws(()=>parseUi(bad));
});

test("agent selection scrolls past the cycle69 clipped row before tapping", async () => {
    const { readFileSync } = await import("node:fs");
    const { agentControl } = await import("./installer-ui.ts");
    const nodes = parseUi(readFileSync(new URL("../../testdata/emulator/cycle69-clipped-agent.xml", import.meta.url), "utf8"));
    const checkbox = nodes.find(n => n.checkable && n.bounds[1] === 710)!;
    const title = nodes.find(n => n.parent === checkbox.parent && n.text)!;
    deepStrictEqual(checkbox.bounds, [515, 710, 600, 720]);
    strictEqual(agentControl(nodes, title.packageName, title.text), null);
    strictEqual(agentControl(nodes, title.packageName, "absent agent"), null);
    throws(() => agentControl(nodes.filter(n => n !== checkbox), title.packageName, title.text), /0 visible/);
    const visible = nodes.map(n => n === checkbox ? { ...n, bounds: [515, 600, 600, 642] as typeof n.bounds } : n);
    strictEqual(agentControl(visible, title.packageName, title.text), visible[nodes.indexOf(checkbox)]);
    throws(() => agentControl([...visible, visible[nodes.indexOf(checkbox)]], title.packageName, title.text), /2 visible/);
});

test("stock vehicle navigation is a closed zero-argument request", () => {
  validateRequest({ action: "open-vehicle-settings" });
  throws(() => validateRequest({ action: "open-vehicle-settings", component: "other/.Activity" }), /unknown or extra/);
  throws(() => validateRequest({ action: "open-vehicle-settings", value: "shell" }), /unknown or extra/);
});

test("stock Navi navigation is a closed zero-argument request", () => {
  validateRequest({ action: "open-navigation" });
  for (const key of ["component", "intent", "args", "value"])
    throws(() => validateRequest({ action: "open-navigation", [key]: "other/.Activity" }), /unknown or extra/);
});

test("actual host serializer fixture preserves window roots and immutable capture scope", async () => {
    const { serializedWindows } = await import("./window-fixture.ts");
    const nodes = parseUi(serializedWindows);
    strictEqual(nodes.length, 4);
    strictEqual(nodes.windowRefs?.length, 5);
    strictEqual(nodes.windowRefs?.[4].rootStatus, "unavailable");
    strictEqual(Object.isFrozen(nodes.windowRefs), true);
    strictEqual(nodes[1].text, "A & B");
    deepStrictEqual(nodes.map(n => n.parent), [null, null, null, null]);
    strictEqual(nodes[0].windowRef?.capture, nodes[1].windowRef?.capture);
    strictEqual(Object.isFrozen(nodes[0].windowRef), true);
    strictEqual(nodes[0].windowRef?.frameSha256, (await import("node:crypto")).createHash("sha256").update(serializedWindows).digest("hex"));
    strictEqual(parseUi(xml)[0].windowRef, undefined);
});

test("closed window parser rejects malformed scope trees attributes and stale intervals", async () => {
    const { serializedWindows: source } = await import("./window-fixture.ts");
    for (const bad of [
        source.replace('sdk="28"', 'sdk="29"'), source.replace('id="0"', 'id="1"'),
        source.replace('scope="default-display"', 'scope="all-displays"'),
        source.replace('window-metadata-stable="true"', 'window-metadata-stable="false"'),
        source.replace('capture-end-uptime-ms="145"', 'capture-end-uptime-ms="30124"'),
        source.replace('capture-end-uptime-ms="145"', 'capture-end-uptime-ms="122"'),
        source.replace('window-id="17"', 'window-id="18"'), source.replace('id="18"', 'id="17"'),
        source.replace('text="A &amp; B"', 'text="&foreign;"'),
        source.replace('<display id="0">', '<display id="0" foreign="true">'),
        source.replace('</window>', '</display>'), source.replace('</node>', ''),
        source.replace('root-status="available"', 'root-status="unavailable"'),
        source.replace('root-status="unavailable"', 'root-status="available"'),
        source.replace('<ui-snapshot', '<!DOCTYPE x><ui-snapshot'),
    ]) throws(() => parseUi(bad));
});

test("window bounds clip a raw checkbox before agent row actuation", async () => {
    const { windowModel } = await import("./window-fixture.ts");
    const nodes = parseUi(windowModel().replace('bounds="[0,0][1920,720]"></node>', 'bounds="[515,710][600,752]"></node>'));
    deepStrictEqual(nodes[0].rawBounds, [515,710,600,752]);
    deepStrictEqual(nodes[0].bounds, [515,710,600,720]);
});


test("agentControl requires scrolling when actual window clips the raw checkbox row", async () => {
    const { serializedWindows } = await import("./window-fixture.ts");
    const { agentControl } = await import("./installer-ui.ts");
    const root = /<node [^>]*><\/node>/u.exec(serializedWindows)![0];
    const label = root.replace('text=""', 'text="Agent"');
    const checkbox = root.replace('index="0"', 'index="1"').replace('checkable="false"', 'checkable="true"').replace('bounds="[0,0][1920,720]"', 'bounds="[515,710][600,752]"');
    const nodes = parseUi(serializedWindows.replace(root, root.replace('</node>', label + checkbox + '</node>')));
    strictEqual(agentControl(nodes, "fixture.navigation", "Agent"), null);
});


 test("secondary launcher requests are closed and screenshots use a fixed display enum", () => {
  validateRequest({ action: "open-secondary-launcher" });
  validateRequest({ action: "screenshot", output: "/project/secondary.png", display: "secondary" });
  validateRequest({ action: "screenshot", output: "/project/primary.png", display: "primary" });
  for (const display of [1, "1", "default", "secondary;id", null]) throws(() => validateRequest({ action: "screenshot", output: "/project/x.png", display }));
  throws(() => validateRequest({ action: "open-secondary-launcher", component: "arbitrary" }));
});


test("gesture durations match packaged installer contract and reject retained cycle123 drag", async () => {
  const { readFileSync } = await import("node:fs");
  const { launcherDragDuration } = await import("./installer-ui.ts");
  const request = JSON.parse(readFileSync(new URL("../../testdata/emulator/cycle123-rejected-drag.json", import.meta.url), "utf8"));
  strictEqual(request.milliseconds, 3000);
  for (const milliseconds of [99, 2001, 3000, 5000])
    throws(() => validateRequest({ ...request, milliseconds }), /invalid gesture duration/);
  for (const milliseconds of [100, 2000]) validateRequest({ ...request, milliseconds });
  for (const page of [0, 1]) validateRequest({ ...request, milliseconds: launcherDragDuration(page) });
  for (const page of [-1, 2, 0.5]) throws(() => launcherDragDuration(page));
  const producer = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
  strictEqual(producer.includes("launcherPageDrag(page, freshFm[0].node.bounds, children[0].node.bounds, launcherCurrentPage.grid.bounds)"), true);
  strictEqual(producer.includes('action: "drag", points, ...(native?ui.launcherPointerAuthority(freshFm[0].node):ui.gestureAuthority(freshFm[0].node))'), true);
});

 test("native complete slow capture and thirty-second boundary preserve strict scope", async () => {
    const { readFileSync } = await import("node:fs");
    const retained = readFileSync(new URL("../../testdata/emulator/cycle132-valid-slow-windows.xml", import.meta.url), "utf8");
    const actual = parseUi(retained);
    strictEqual(actual.windowRefs?.[0].captureStart, 254811);
    strictEqual(actual.windowRefs?.[0].captureEnd, 270218);
    strictEqual(actual.length > 0, true);
    const { serializedWindows: source } = await import("./window-fixture.ts");
    strictEqual(parseUi(source.replace('capture-end-uptime-ms="145"', 'capture-end-uptime-ms="30123"')).length, 4);
    for (const end of ["30124", "122", "030123", "-1"])
        throws(() => parseUi(source.replace('capture-end-uptime-ms="145"', `capture-end-uptime-ms="${end}"`)));
});


test("native interactions dispatch once and never fall back after a failed mutation", async () => {
  const { InstallerUi } = await import("./installer-ui.ts");
  const { mkdtempSync, rmSync } = await import("node:fs");
  const { join } = await import("node:path");
  const base = new URL("../../.test-native-interaction-", import.meta.url).pathname;
  const directory = mkdtempSync(base);
  let nativeCalls = 0, cliCalls = 0, ownershipCalls = 0;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5574", join(directory, "ui"), async () => {
      cliCalls++; throw new Error("unexpected installer input");
    }, async () => { ownershipCalls++; }, undefined, async request => {
      nativeCalls++;
      strictEqual(Object.isFrozen(request), true);
      strictEqual(request.action, "key");
      throw new Error("native outcome unknown");
    });
    await (await import("node:assert")).rejects(ui.call({ action: "key", key: "home" }), /native outcome unknown/);
    strictEqual(nativeCalls, 1); strictEqual(cliCalls, 0); strictEqual(ownershipCalls, 1);
    await (await import("node:assert")).rejects(ui.call({ action: "key", key: "arbitrary" }), /unknown key/);
    strictEqual(nativeCalls, 1);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test("native interaction receipts do not become installer CLI records", async () => {
  const { InstallerUi } = await import("./installer-ui.ts");
  const { mkdtempSync, rmSync } = await import("node:fs");
  const { join } = await import("node:path");
  const directory = mkdtempSync(new URL("../../.test-native-interaction-", import.meta.url).pathname);
  try {
    const receipt = { native: true, body: "not an installer receipt" };
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5574", join(directory, "ui"), async () => { throw new Error("unexpected installer input"); }, async () => {}, undefined, async () => receipt);
    strictEqual(await ui.call({ action: "key", key: "home" }), receipt);
    strictEqual(ui.records.length, 0);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});


test("native warm reboot requires exact session PID generation and a new boot", async () => {
  const { nativeRebootReceipt } = await import("./installer-ui.ts");
  const before = "12345678-1234-1234-1234-123456789abc", after = "12345678-1234-1234-1234-123456789abd";
  const expected = { sessionId: "a".repeat(32), pid: 4321, boot: before };
  const receipt = { ok: true, session_id: expected.sessionId, mode: "warm", status: "completed", scope: "guest_boot_changed", boot_id_before: before, boot_id: after, generation_before: 2, generation: 3, android_pid_before: 4321, android_pid: 4321, application_ready: false };
  deepStrictEqual(nativeRebootReceipt(receipt, expected), { before, after });
  for (const change of [{ ok: false }, { session_id: "b".repeat(32) }, { mode: "cold" }, { status: "pending" }, { scope: "sdk_dispatch" }, { boot_id_before: after }, { boot_id: before }, { boot_id: "invalid" }, { android_pid: 4322 }, { android_pid_before: 4322 }, { generation: 2 }, { generation_before: -1, generation: 0 }, { application_ready: true }])
    throws(() => nativeRebootReceipt({ ...receipt, ...change }, expected), /inspect operation before replay/);
});


test("native pointer projects actual target window and source provenance, not caller authority", async () => {
  const { nativePointerRequest } = await import("./installer-ui.ts");
  const bounds = { x: 10, y: 20, width: 100, height: 60 };
  const window = { id: 4, type: 1, layer: 2, bounds, title: "Setting", package: "fixture.setting" };
  const node = { window_id: 4, resource_id: "fixture.setting:id/check", package: "fixture.setting", class: "android.widget.CheckBox", text: "Agent", bounds, clipped_bounds: bounds, visible: true, enabled: true, clickable: true, scrollable: true };
  const fence = { sessionId: "a".repeat(32), pid: 4321, boot: "12345678-1234-1234-1234-123456789abc" };
  const tree = { session_id: fence.sessionId, android_pid: fence.pid, boot_id: fence.boot, source: "android.ui", complete: true, source_instance: "observer-epoch", revision: "observation-id", screens: [{ screen: "primary", windows: [window], nodes: [node] }] };
  const expectedWindow = { id: 4, windowType: 1, bounds: "[10,20][110,80]", title: "Setting", packageName: "fixture.setting", captureEndUptimeMs: "123", displayId: 0 };
  const expectedTarget = { windowId: 4, windowType: 1, windowBounds: "[10,20][110,80]", windowTitle: "Setting", rootPackage: "fixture.setting", className: node.class, packageName: node.package, bounds: expectedWindow.bounds, resource: node.resource_id, text: node.text };
  const request = { action: "tap", x: 50, y: 50, expectedWindow, expectedTarget };
  const pointer = nativePointerRequest(request, tree, fence);
  strictEqual(pointer.op, "input.tap");
  deepStrictEqual(pointer.fields, { screen: "primary", x: 50, y: 50, expected: { boot_id: fence.boot, source_instance: "observer-epoch", revision: "observation-id", window, target: Object.fromEntries(["window_id", "resource_id", "package", "class", "text", "bounds", "clipped_bounds"].map(k => [k, node[k as keyof typeof node]])) } });
  for (const change of [{ session_id: "other" }, { android_pid: 4322 }, { boot_id: "other" }, { source_instance: "" }, { revision: "" }, { complete: false }, { screens: [{ ...tree.screens[0], nodes: [node, node] }] }, { screens: [{ ...tree.screens[0], nodes: [{ ...node, enabled: false }] }] }])
    throws(() => nativePointerRequest(request, { ...tree, ...change }, fence));
  const burst = nativePointerRequest({ ...request, action: "tbox-burst" }, tree, fence);
  strictEqual(burst.op, "input.tap");
  strictEqual(burst.fields.count, 5);
  strictEqual(burst.fields.interval_ms, 100);
  const gesture = nativePointerRequest({ ...request, action: "drag", x1: 20, y1: 30, x2: 90, y2: 60, milliseconds: 1800 }, tree, fence);
  const points = gesture.fields.points as { x: number; y: number; at_ms: number }[];
  strictEqual(points.length, 58);
  deepStrictEqual(points[0], { x: 20, y: 30, at_ms: 0 });
  strictEqual(points.filter(p => p.at_ms <= 600).every(p => p.x === 20 && p.y === 30), true);
  deepStrictEqual(points.at(-1), { x: 90, y: 60, at_ms: 1800 });
  const sampled = { action: "drag", points: [{x:20,y:30,at_ms:0},{x:90,y:60,at_ms:2000}], expectedWindow, expectedTarget };
  const sampledResult = nativePointerRequest(sampled,tree,fence);
  deepStrictEqual(sampledResult.fields.points,sampled.points);
  deepStrictEqual(sampledResult.fields.expected,gesture.fields.expected);
  strictEqual(sampledResult.fields.intent,"drag");
  throws(()=>nativePointerRequest({...sampled,points:[{x:9,y:30,at_ms:0}]},tree,fence),/first point/);
  throws(()=>nativePointerRequest({...sampled,points:[{x:20,y:30,at_ms:0},{x:110,y:60,at_ms:2000}]},tree,fence),/route outside/);
  throws(()=>nativePointerRequest(sampled,{...tree,revision:""},fence),/source fence/);
  const receiverTree = { ...tree, screens: [{ ...tree.screens[0], nodes: [{ ...node, clickable: false, scrollable: false }] }] };
  const generic = { ...request, action: "gesture", x1:20, y1:30, x2:90, y2:60, milliseconds:300 };
  const receiver = nativePointerRequest(generic, receiverTree, fence);
  strictEqual(receiver.op, "input.gesture");
  strictEqual("intent" in receiver.fields, false);
  for (const action of ["tap","swipe","drag"]) throws(()=>nativePointerRequest({...generic,action},receiverTree,fence),/target is unavailable/);
  for (const change of [{boot_id:"other"},{source_instance:""},{revision:""},{complete:false}])
    throws(()=>nativePointerRequest(generic,{...receiverTree,...change},fence));

});


test("SDK keys distinguish Android home back sleep and backward delete", async () => {
  const { nativeKey } = await import("./installer-ui.ts");
  deepStrictEqual(["home", "back", "wake", "sleep", "enter", "delete"].map(nativeKey), ["GoHome", "GoBack", "WakeUp", "Standby", "Enter", "Backspace"]);
  for (const key of ["HOME", "Sleep", "Delete", "toString", "", null]) throws(() => nativeKey(key), /mapping unavailable/);
});


test("native trajectories supply continuous pressed samples within SDK limits", async () => {
  const { gestureTrajectory } = await import("./installer-ui.ts");
  for (const duration of [1, 32, 33, 200, 400, 1800, 2000]) {
    const points = gestureTrajectory([100, 600], [100, 100], duration);
    strictEqual(points.length <= 64, true);
    deepStrictEqual(points[0], { x: 100, y: 600, at_ms: 0 });
    deepStrictEqual(points.at(-1), { x: 100, y: 100, at_ms: duration });
    strictEqual(points.slice(1).every((p, i) => p.at_ms > points[i].at_ms && p.at_ms - points[i].at_ms <= 32), true);
    if (duration >= 200) strictEqual(points.length > 2, true);
  }
  deepStrictEqual(gestureTrajectory([1, 2], [1, 2], 0), [{ x: 1, y: 2, at_ms: 0 }]);
  const held = gestureTrajectory([100, 600], [500, 100], 2000, 600);
  strictEqual(held.length, 64);
  strictEqual(held.filter(p => p.at_ms <= 600).every(p => p.x === 100 && p.y === 600), true);
  strictEqual(held.every(p => !("pressed" in p) && !("hover" in p)), true);
  for (const [duration, hold] of [[-1, 0], [2001, 0], [1.5, 0], [200, -1], [200, 201], [0, 0]])
    throws(() => gestureTrajectory([1, 2], [3, 4], duration, hold), /invalid bounded/);
  throws(() => gestureTrajectory([-1, 0], [0, 0], 100));
  throws(() => gestureTrajectory([0, 0], [1920, 720], 100));
});

test("application navigation has closed OEM mappings and no CLI fallback", async () => {
  const { nativeApp, nativeAppReceipt, InstallerUi } = await import("./installer-ui.ts");
  const { mkdtempSync, rmSync } = await import("node:fs");
  const directory = mkdtempSync(new URL("../../.test-native-launch-", import.meta.url).pathname);
  const boot_id = "12345678-1234-1234-1234-123456789abc";
  let calls = 0;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5574", directory + "/ui", async () => { throw new Error("CLI fallback"); }, async () => {}, undefined, async () => { calls++; throw new Error("launch unknown"); });
    for (const action of ["open-settings", "open-vehicle-settings", "open-navigation", "open-primary-launcher", "open-secondary-launcher"]) {
      const app = nativeApp({action})!;
      strictEqual(app.screen, action === "open-secondary-launcher" ? "secondary" : "primary");
      const expected = {...app, boot_id}, result = {...expected,ok:true,accepted:true,display_id:app.screen === "secondary" ? 7 : 0,application_ready:false,visible_effect_verified:false};
      nativeAppReceipt(result,expected);
      for (const change of [{boot_id:"foreign"},{accepted:false},{application_ready:true},{visible_effect_verified:true},{component:"foreign/Activity"}]) throws(() => nativeAppReceipt({...result,...change},expected));
      await (await import("node:assert")).rejects(ui.call({action}), /launch unknown/);
    }
    strictEqual(calls,5); strictEqual(ui.records.length,0);
    strictEqual(nativeApp({action:"install-fixture"}),undefined);
    validateRequest({action:"secondary-capture-proof"});
  } finally { rmSync(directory,{recursive:true,force:true}); }
});


test("native primary snapshot is read-only with and without mutation transport", async () => {
  const { InstallerUi } = await import("./installer-ui.ts");
  const { mkdtempSync, rmSync } = await import("node:fs");
  const { rejects } = await import("node:assert/strict");
  const directory=mkdtempSync(new URL("../../.test-native-snapshot-",import.meta.url).pathname);
  try {
    for (const mutationTransport of [false,true]) {
      let reads=0,owners=0,mutations=0,cli=0;
      const nodes=parseUi(xml);
      const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/"+mutationTransport,
        async()=>{cli++;throw new Error("unexpected CLI snapshot");},
        async()=>{owners++;},
        async()=>{reads++;return nodes;},
        mutationTransport ? async()=>{mutations++;throw new Error("unexpected mutation");} : undefined);
      strictEqual(await ui.snapshot(),nodes);
      strictEqual(await ui.snapshot(),nodes);
      strictEqual(reads,2);strictEqual(owners,2);strictEqual(mutations,0);strictEqual(cli,0);
      if(!mutationTransport) {
        await rejects(ui.call({action:"gesture"}),/generic gesture requires native fenced interaction/);
        strictEqual(await ui.snapshot(),nodes);
        strictEqual(reads,3);strictEqual(mutations,0);strictEqual(cli,0);
      }
    }
  } finally {rmSync(directory,{recursive:true,force:true});}
});


test("Vehicle launch waits for the actual PW171-12 Settings window without granting input authority", async () => {
  const { vehicleLaunchPending } = await import("./installer-ui.ts");
  const windows = [
    { id: 1, type: 3, active: false, inputFocused: false, rootPackage: "com.qinggan.app.launcher" },
    { id: 5, type: 3, active: false, inputFocused: false, rootPackage: "com.qinggan.app.launcher" },
    { id: 7, type: -1, active: false, inputFocused: false, rootPackage: "com.qinggan.app.launcher" },
    { id: 11, type: 1, active: true, inputFocused: true, rootPackage: "com.qinggan.app.setting" },
  ].map(window => ({ ...window, rootStatus: "available" }));
  const tree = (refs: typeof windows) => Object.assign([], { windowRefs: refs }) as unknown as import("./installer-ui.ts").UiNodes;
  strictEqual(vehicleLaunchPending(tree(windows)), true);
  strictEqual(vehicleLaunchPending(tree(windows.map(w => w.id === 11 ? { ...w, rootPackage: "com.qinggan.app.vehiclesetting" } : w))), false);
  strictEqual(vehicleLaunchPending(tree(windows.map(w => w.id === 11 ? { ...w, rootPackage: "unknown.overlay" } : w))), false);
  strictEqual(vehicleLaunchPending(tree(windows.map(w => w.id === 11 ? { ...w, rootPackage: "com.qinggan.app.vehiclesetting", inputFocused: false } : w))), false);
  strictEqual(vehicleLaunchPending(tree([...windows, { ...windows[3], id: 12 } ])), false);
  strictEqual(vehicleLaunchPending(tree([])), false);
});

test("edge gesture uses its observed overlay window instead of focused foreground", async()=>{
 const {InstallerUi,nativePointerRequest}=await import("./installer-ui.ts");
 const {mkdtempSync,rmSync}=await import("node:fs");
 const directory=mkdtempSync(new URL("../../.test-edge-window-",import.meta.url).pathname);
 try {
  const foreground:any={id:24,type:1,active:true,inputFocused:true,rootStatus:"available",rootPackage:"com.qinggan.app.vehiclesetting",bounds:[0,0,1920,720],title:"New Vehicle Settings",captureEnd:120201,scope:"default-display"};
  const overlay:any={id:19,type:3,active:false,inputFocused:false,rootStatus:"available",rootPackage:"com.qinggan.app.launcher",bounds:[0,0,30,720],title:"",captureEnd:120201,scope:"default-display",layer:3};
  const base=parseUi(xml)[0], edge:any={...base,packageName:overlay.rootPackage,resource:overlay.rootPackage+":id/line_view",className:"android.view.View",text:"",bounds:[10,290,16,430],windowRef:overlay,visibleToUser:true,enabled:true,clickable:false};
  const nodes:any=[{...base,windowRef:foreground},edge];
  const ui=new InstallerUi("/package/voboost-installer","emulator-5574",directory+"/ui",async()=>{throw Error("CLI fallback");},async()=>{},async()=>nodes,async()=>({}));
  await ui.snapshot();
  const authority=ui.gestureAuthority(edge,true);
  const request={action:"gesture",x1:13,y1:360,x2:960,y2:360,milliseconds:300,...authority};
  validateRequest(request);
  strictEqual((authority.expectedWindow as any).id,19);
  strictEqual((authority.expectedTarget as any).windowId,19);
  const rect=(b:number[])=>({x:b[0],y:b[1],width:b[2]-b[0],height:b[3]-b[1]});
  const fence={sessionId:"a".repeat(32),pid:4321,boot:"boot"};
  const tree={session_id:fence.sessionId,android_pid:fence.pid,boot_id:fence.boot,source:"android.ui",complete:true,source_instance:"observer",revision:"current",screens:[{screen:"primary",windows:[{id:19,type:3,layer:3,bounds:rect(overlay.bounds),title:"",package:overlay.rootPackage}],nodes:[{window_id:19,resource_id:edge.resource,package:edge.packageName,class:edge.className,text:"",bounds:rect(edge.bounds),clipped_bounds:rect(edge.bounds),visible:true,enabled:true,clickable:false}]}]};
  strictEqual(nativePointerRequest(request,tree,fence).op,"input.gesture");
  throws(()=>validateRequest({...request,...ui.gestureAuthority(edge)}),/invalid gesture expected target/);
  const stale={...edge};throws(()=>ui.gestureAuthority(stale,true),/stale or foreign/);
  overlay.rootPackage="other.owner";throws(()=>ui.gestureAuthority(edge,true),/owned overlay window/);
 } finally {rmSync(directory,{recursive:true,force:true});}
});

import { rejects } from "node:assert";

import {awaitLauncherDock} from "./installer-ui.ts";
const owner="com.qinggan.app.launcher";
function fixture(): any[] {
 const shared={rootPackage:owner,rootStatus:"available",scope:"default-display",capture:"frame",frameSha256:"sha"};
 const main={...shared,id:1,type:1,active:true,inputFocused:true,layer:1,bounds:[0,0,1920,720]};
 const dock={...shared,id:2,type:-1,layer:2,bounds:[0,0,120,720]};
 return [{packageName:owner,windowRef:main},{packageName:owner,resource:owner+":id/screen_up_all_app_icon",windowRef:dock,enabled:true,clickable:true,visibleToUser:true,className:"android.widget.RadioButton",bounds:[10,20,100,100]}];
}
test("one accepted launch can publish absent dock then ready fresh dock without mutation",async()=>{
 const nodes=fixture(); let reads=0,t=0;
 const actual=await awaitLauncherDock(async()=>{reads++;t++;return reads===1?[]:nodes;},10,()=>t);
 strictEqual(actual,nodes[1]);strictEqual(reads,2);
});
test("dock waits for primary owner focus without accepting an unfocused target",async()=>{
 const nodes=fixture(), pending=fixture(); pending[0].windowRef.inputFocused=false;let reads=0,t=0;
 strictEqual(await awaitLauncherDock(async()=>{t++;return ++reads===1?pending:nodes;},10,()=>t),nodes[1]);
});
test("duplicate, malformed and foreign dock fail immediately",async()=>{
 for(const kind of ["duplicate","foreign","geometry"]){const nodes=fixture();if(kind==="duplicate")nodes.push(nodes[1]);if(kind==="foreign")nodes[1].windowRef.rootPackage="foreign";if(kind==="geometry")nodes[1].bounds=[-1,20,100,100];let reads=0;
 await rejects(awaitLauncherDock(async()=>{reads++;return nodes;},10,()=>0));strictEqual(reads,1);}
});
test("late ready result and absent dock exhaust bounded budget without accepting input",async()=>{
 let t=0,reads=0;await rejects(awaitLauncherDock(async()=>{reads++;t=10;return fixture();},10,()=>t),/deadline expired/);strictEqual(reads,1);
 t=0;reads=0;await rejects(awaitLauncherDock(async()=>{reads++;t+=2;return [];},5,()=>t),/deadline expired/);strictEqual(reads,3);
});
test("transport or ownership failures are never retried",async()=>{let reads=0;await rejects(awaitLauncherDock(async()=>{reads++;throw new Error("boot changed");},10,()=>0),/boot changed/);strictEqual(reads,1);});

test("ambiguous focused primary windows fail without reread",async()=>{const nodes=fixture();nodes.push({...nodes[0],windowRef:{...nodes[0].windowRef,id:3}});let reads=0;await rejects(awaitLauncherDock(async()=>{reads++;return nodes;},10,()=>0),/focused primary/);strictEqual(reads,1);});

test("editor metadata preserves unknown and refuses malformed selections and partial hint evidence", () => {
  deepStrictEqual(editorMetadata("caption", undefined, undefined, undefined, undefined), {});
  for (const args of [["caption", undefined, true, 0, 0], ["caption", "caption", true, 1, 1], ["text", "", false, -1, 0], ["text", "", false, 0, 5], ["caption", "different", true, 0, 0]]) throws(() => editorMetadata(...args as [string,unknown,unknown,unknown,unknown]));
});
test("hint cannot turn an unfocused, invisible or non-editor node into an empty editor", async () => {
  const { serializedWindows } = await import("./window-fixture.ts");
  const node = {...parseUi(serializedWindows)[0], className:"android.widget.EditText", focused:true, enabled:true, visibleToUser:true, text:"caption", hint:"caption", showingHint:true, selectionStart:0, selectionEnd:0};
  strictEqual(editorValue({...node,focused:false}), "");
  for (const change of [{visibleToUser:false},{enabled:false},{password:true},{className:"android.widget.TextView"},{windowRef:undefined}]) throws(()=>editorValue({...node,...change}));
  strictEqual(editorCaretAtEnd({...node,showingHint:false,selectionStart:-1,selectionEnd:-1}),false);
});
test("redacted editors cannot prove empty values with omitted or false hint metadata", async () => {
  const {serializedWindows} = await import("./window-fixture.ts");
  const node = {...parseUi(serializedWindows)[0], text:"", password:true};
  throws(()=>editorValue(node), /redacted/);
  throws(()=>editorValue({...node,hint:"",showingHint:false,selectionStart:-1,selectionEnd:-1}), /redacted/);
  throws(()=>editorCaretAtEnd({...node,selectionStart:0,selectionEnd:0}), /redacted/);
  const source=serializedWindows.replace('text="A &amp; B"','text="" hint="" showing-hint="false" selection-start="-1" selection-end="-1"').replace('password="false"','password="true"').replace('password="false"','password="true"');
  throws(()=>parseUi(source), /privacy/);
});
test("closed XML accepts optional AX metadata without altering raw text and rejects partial metadata", async () => {
  const {serializedWindows} = await import("./window-fixture.ts");
  const source=serializedWindows.replace('text="A &amp; B"','text="A &amp; B" hint="A &amp; B" showing-hint="true" selection-start="0" selection-end="0"');
  const node=parseUi(source)[1];strictEqual(node.text,"A & B");strictEqual(node.hint,"A & B");strictEqual(node.showingHint,true);strictEqual(node.selectionStart,0);
  throws(()=>parseUi(source.replace(' selection-end="0"','')));
  strictEqual(parseUi(serializedWindows)[1].showingHint,undefined);
});

test("native pressed routes reject invalid samples and preserve bounded geometry", async () => {
  const { boundedGesturePoints, launcherPageDrag } = await import("./installer-ui.ts");
  const points = [{x:20,y:30,at_ms:0},{x:30,y:40,at_ms:2000}];
  deepStrictEqual(boundedGesturePoints(points),points);
  for (const invalid of [[], [...points,{x:1,y:1,at_ms:2001}], [{x:1,y:1,at_ms:1}], [{x:1,y:1,at_ms:0},{x:2,y:2,at_ms:0}], [{x:1920,y:1,at_ms:0}], [{x:1,y:720,at_ms:0}], [{x:1,y:1,at_ms:0,extra:true}], Array.from({length:65},(_,i)=>({x:1,y:1,at_ms:i}))]) throws(()=>boundedGesturePoints(invalid));
  for (const page of [0,1,2]) {
    const route=launcherPageDrag(page,[1400,360,1600,560],[220,100,420,300],[100,80,1800,650]);
    deepStrictEqual(route[0],{x:1500,y:460,at_ms:0});
    deepStrictEqual(route.at(-1),{x:320,y:200,at_ms:page ? 1400+(page-1)*550 : 1300});
    strictEqual(route.every(p=>p.x>=100&&p.x<1800&&p.y>=80&&p.y<650),true);
    strictEqual(route.every((p,i)=>p.at_ms<=2000 && (i===0 ? p.at_ms===0 : p.at_ms>route[i-1].at_ms)),true);
    deepStrictEqual(route[1],{x:1500,y:460,at_ms:600});
  }
  for (const page of [-1,3,1.5]) throws(()=>launcherPageDrag(page,[1400,360,1600,560],[220,100,420,300],[100,80,1800,650]));
  throws(()=>launcherPageDrag(1,[0,0,20,20],[220,100,420,300],[100,80,1800,650]));
});

test("points require native transport before ownership and never replay unknown dispatch", async () => {
  const {InstallerUi}=await import("./installer-ui.ts");
  const {mkdtempSync,rmSync}=await import("node:fs");
  const dir=mkdtempSync(process.cwd()+"/.bridge-test-");
  let owned=0, legacy=0, native=0;
  const adapter=async()=>{legacy++;throw new Error("legacy must not run");};
  const request={action:"drag",points:[{x:20,y:30,at_ms:0}],expectedWindow:{id:4,windowType:1,bounds:"[10,20][110,80]",title:"Setting",packageName:"fixture.setting",captureEndUptimeMs:"123",displayId:0},expectedTarget:{windowId:4,windowType:1,windowBounds:"[10,20][110,80]",windowTitle:"Setting",rootPackage:"fixture.setting",className:"android.widget.CheckBox",packageName:"fixture.setting",bounds:"[10,20][110,80]",resource:"fixture.setting:id/check",text:"Agent"}};
  try {
    const old=new InstallerUi("/package/voboost-installer","fixture",dir+"/legacy",adapter,async()=>{owned++;});
    await (await import("node:assert")).rejects(old.call(request),/native fenced/);
    strictEqual(owned,0);strictEqual(legacy,0);
    const ui=new InstallerUi("/package/voboost-installer","fixture",dir+"/native",adapter,async()=>{owned++;},undefined,async()=>{native++;throw new Error("unknown input result");});
    await (await import("node:assert")).rejects(ui.call({...request,points:[{x:20,y:30,at_ms:2001}]}),/bounded/);
    strictEqual(owned,0);strictEqual(native,0);
    await (await import("node:assert")).rejects(ui.call(request),/unknown input result/);
    strictEqual(native,1);strictEqual(legacy,0);strictEqual(owned,1);
  } finally {rmSync(dir,{recursive:true});}
});


test("launcher paging geometry does not claim AX scroll capability", async () => {
  const { nativePointerRequest } = await import("./installer-ui.ts");
  const { readFileSync } = await import("node:fs");
  const packageName="com.qinggan.app.launcher", bounds={x:100,y:80,width:1700,height:570};
  const window={id:4,type:1,layer:2,bounds,title:"Launcher",package:packageName};
  const node={window_id:4,resource_id:packageName+":id/all_app_view",package:packageName,class:"android.view.ViewGroup",text:"",bounds,clipped_bounds:bounds,visible:true,enabled:true,clickable:false,scrollable:false};
  const fence={sessionId:"a".repeat(32),pid:4321,boot:"boot"};
  const tree={session_id:fence.sessionId,android_pid:fence.pid,boot_id:fence.boot,source:"android.ui",complete:true,source_instance:"observer",revision:"current",screens:[{screen:"primary",windows:[window],nodes:[node]}]};
  const expectedWindow={id:4,windowType:1,bounds:"[100,80][1800,650]",title:"Launcher",packageName,captureEndUptimeMs:"123",displayId:0};
  const expectedTarget={windowId:4,windowType:1,windowBounds:expectedWindow.bounds,windowTitle:"Launcher",rootPackage:packageName,className:node.class,packageName,bounds:expectedWindow.bounds,resource:node.resource_id,text:""};
  const request={action:"gesture",x1:1550,y1:365,x2:350,y2:365,milliseconds:400,expectedWindow,expectedTarget};
  validateRequest(request);
  const mapped=nativePointerRequest(request,tree,fence);
  strictEqual(mapped.op,"input.gesture");strictEqual("intent" in mapped.fields,false);
  const points=mapped.fields.points as {x:number,y:number,at_ms:number}[];
  deepStrictEqual(points[0],{x:1550,y:365,at_ms:0});deepStrictEqual(points.at(-1),{x:350,y:365,at_ms:400});
  strictEqual((mapped.fields.expected as any).target.resource_id,node.resource_id);
  throws(()=>nativePointerRequest({...request,action:"swipe"},tree,fence),/target is unavailable/);
  throws(()=>nativePointerRequest(request,{...tree,revision:""},fence),/source fence/);
  throws(()=>nativePointerRequest(request,{...tree,screens:[{...tree.screens[0],nodes:[{...node,visible:false}]}]},fence),/target is unavailable/);
  const producer=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
  const routes=producer.split("\n").filter(line=>line.includes("await ui.call({")&& /gestureAuthority\((grid|pageGrid)\)/.test(line)&&line.includes("milliseconds: 400"));
  strictEqual(routes.length,3);
  strictEqual(routes.every(line=>line.includes('action: "gesture"')),true);
  // Actual Settings scrolling keeps the strict scroll intent and AX capability contract.
  strictEqual(producer.includes('action: "swipe"'),true);
});

// OEM AllAppBarView returns zero after smoothPrePage: each edge MOVE can
// request a page, while the stationary scroll runnable stops. This model
// deliberately evaluates overflow, rather than matching route point counts.
test("page drag requests exactly the intended OEM transitions before dropping", async () => {
  const {launcherPageDrag}=await import("./installer-ui.ts");
  const item=[753,369,1032,558], first=[195,129,474,318], grid=[145,80,1800,650];
  const model=(route: Array<{x:number;y:number;at_ms:number}>)=>{
    let requests=0, lastTransition=-Infinity;
    for(const point of route.slice(2)) {
      const overflow=grid[0]-(point.x-(item[2]-item[0])/2);
      if(overflow/(item[2]-item[0])>=0.33) {
        // A bounded diagnostic settle, not a universal OEM duration claim.
        strictEqual(point.at_ms-lastTransition>=550,true);
        requests++;lastTransition=point.at_ms;
      } else if(requests) strictEqual(point.at_ms-lastTransition>=550,true);
    }
    return requests;
  };
  for(const page of [0,1,2]) strictEqual(model(launcherPageDrag(page,item,first,grid)),page);
});
