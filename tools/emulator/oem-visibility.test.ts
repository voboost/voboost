import assert from "node:assert/strict";
import test from "node:test";
import { parseUi } from "./installer-ui.ts";
import { oemFeature } from "./functional-observations.ts";

test("PW control below the viewport requires scrolling before selected-state read or tap", () => {
  const nodes = parseUi('<hierarchy><node package="com.qinggan.app.vehiclesetting" resource-id="com.qinggan.app.vehiclesetting:id/fragment_safe_hum_vsp" bounds="[485,1292][1190,720]"><node package="com.qinggan.app.vehiclesetting" checkable="true" checked="true" bounds="[515,1316][600,720]" /></node></hierarchy>');
  assert.throws(() => oemFeature(nodes, "pw"), /outside the visible page/);
  nodes[0].bounds = [485, 450, 1190, 650];
  nodes[1].bounds = [515, 474, 600, 522];
  assert.equal(oemFeature(nodes, "pw").checked, true);
});
