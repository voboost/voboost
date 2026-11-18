import assert from "node:assert/strict";
import test from "node:test";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { InstallerUi, launcherDock, parseUi } from "./installer-ui.ts";

const xml = readFileSync(new URL("../../testdata/emulator/cycle90-launcher-dock.xml", import.meta.url), "utf8");

test("recorded OEM dock is certified against its focused primary launcher in the same capture", () => {
  const nodes = parseUi(xml), dock = launcherDock(nodes);
  assert.equal(dock.windowRef!.type, -1);
  assert.deepEqual(dock.bounds, [14, 618, 130, 708]);
  assert.throws(() => launcherDock([...nodes, dock]), /unique/);
  assert.throws(() => launcherDock(nodes.filter(node => node.windowRef!.type !== 1)), /focused primary/);
  assert.throws(() => launcherDock(nodes.map(node => node === dock ? { ...node, windowRef: { ...node.windowRef!, frameSha256: "0".repeat(64) } } : node)), /capture or containment/);
  assert.throws(() => launcherDock(nodes.map(node => node === dock ? { ...node, bounds: [150, 618, 260, 708] } : node)), /capture or containment/);
  assert.throws(() => launcherDock(nodes.map(node => node === dock ? { ...node, className: "android.widget.TextView" } : node)), /identity/);
});

test("generic unknown-window tap remains rejected while the closed dock operation uses a fresh capture", async () => {
  const requests: Record<string, unknown>[] = [];
  const ui = new InstallerUi("/fixed/voboost-installer", "emulator-5562", join(mkdtempSync(join(tmpdir(), "dock-")), "ui"), async invocation => {
    const request = JSON.parse(readFileSync(invocation.args[1], "utf8"));
    requests.push(request);
    return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: request.action === "snapshot" ? xml : "" }), stderr: "", timedOut: false };
  }, async () => {});
  await assert.rejects(ui.tap(launcherDock(await ui.snapshot())), /unclassified/);
  await ui.openLauncherApps();
  assert.deepEqual(requests.map(request => request.action), ["snapshot", "snapshot", "tap"]);
  const owner = parseUi(xml).find(node => node.windowRef?.id === 73)!.windowRef!;
  const dock = launcherDock(parseUi(xml));
  assert.deepEqual(requests[2], { action: "tap", x: 72, y: 663, expectedWindow: { id: 73, windowType: owner.type, bounds: `[${owner.bounds[0]},${owner.bounds[1]}][${owner.bounds[2]},${owner.bounds[3]}]`, title: owner.title, packageName: "com.qinggan.app.launcher", captureEndUptimeMs: "107043", displayId: 0 }, expectedTarget: { windowId: 70, windowType: -1, windowBounds: `[${dock.windowRef!.bounds[0]},${dock.windowRef!.bounds[1]}][${dock.windowRef!.bounds[2]},${dock.windowRef!.bounds[3]}]`, windowTitle: dock.windowRef!.title, rootPackage: dock.windowRef!.rootPackage, className: dock.className, packageName: "com.qinggan.app.launcher", bounds: "[14,618][130,708]", resource: "com.qinggan.app.launcher:id/screen_up_all_app_icon", text: "" } });
});
