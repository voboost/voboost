import assert from "node:assert/strict";
import test from "node:test";
import { launcherHomeGesture, openLauncherAppsFromHome } from "./launcher-entry.ts";
import type { UiNode } from "./installer-ui.ts";

function home(): UiNode[] {
  const windowRef = { type: 1, active: true, inputFocused: true, rootPackage: "com.qinggan.app.launcher", rootStatus: "available", scope: "default-display" } as UiNode["windowRef"];
  const base = { enabled: true, visibleToUser: true, packageName: "com.qinggan.app.launcher", windowRef };
  return [
    { ...base, resource: "com.qinggan.app.launcher:id/home_view_pager", className: "androidx.viewpager.widget.ViewPager", bounds: [145, 0, 1920, 720] },
    { ...base, resource: "com.qinggan.app.launcher:id/frameLayout_card", className: "android.widget.FrameLayout", bounds: [558, 101, 1920, 639] },
  ] as UiNode[];
}

test("observed OEM home route moves horizontally into page 1 outside card controls", () => {
  const nodes = home(), { pager, request } = launcherHomeGesture(nodes);
  assert.equal(pager, nodes[0]);
  assert.deepEqual(request, { action: "gesture", x1: 1565, y1: 679, x2: 500, y2: 679, milliseconds: 500 });
});

test("home route rejects missing, ambiguous, foreign and geometrically unavailable home", () => {
  assert.throws(() => launcherHomeGesture(home().slice(0, 1)), /selector/);
  assert.throws(() => launcherHomeGesture([...home(), home()[0]]), /selector/);
  const foreign = home(); foreign[1].windowRef = { ...foreign[0].windowRef! };
  assert.throws(() => launcherHomeGesture(foreign), /selector/);
  const edge = home(); edge[1].bounds[3] = 720;
  assert.throws(() => launcherHomeGesture(edge), /margin/);
  const apps = home(); apps.push({ ...apps[1], resource: "com.qinggan.app.launcher:id/all_app_view" });
  assert.throws(() => launcherHomeGesture(apps), /ambiguous/);
});

test("entry snapshots once and sends exact captured pager authority with no retry on uncertain dispatch", async () => {
  const nodes = home(), calls: unknown[] = [], failure = new Error("unknown outcome");
  let snapshots = 0;
  await assert.rejects(openLauncherAppsFromHome({
    snapshot: async () => { snapshots++; return nodes; },
    gestureAuthority: node => { assert.equal(node, nodes[0]); return { expectedWindow: { id: 44 }, expectedTarget: { resource: node.resource } }; },
    call: async request => { calls.push(request); throw failure; },
  }), failure);
  assert.equal(snapshots, 1);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], { ...launcherHomeGesture(nodes).request, expectedWindow: { id: 44 }, expectedTarget: { resource: nodes[0].resource } });
});
