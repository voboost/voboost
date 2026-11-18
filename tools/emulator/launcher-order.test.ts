import assert from "node:assert/strict";
import test from "node:test";
import { observedLauncherOrder } from "./launcher-order.ts";
// Shape is the actual launcherItems projection: ordered visible item_name labels.
// This fixture is synthetic input to the analyzer, never a device acceptance receipt.
const items = (labels: string[]) => labels.map(label => ({ label }));
const baseline = items(["Voboost Fixture", "Music", "Navi", "Cloud Space", "HiCar"]);
test("every observed slot survives; independently created item objects are accepted", () => {
  assert.deepEqual(observedLauncherOrder(baseline, items(baseline.map(row => row.label))), baseline.map(row => row.label));
});
test("retaining Voboost Fixture first cannot hide reordered, lost, added or duplicated suffix", () => {
  for (const labels of [
    ["Voboost Fixture", "Navi", "Music", "Cloud Space", "HiCar"],
    ["Voboost Fixture", "Music", "Cloud Space", "HiCar"],
    ["Voboost Fixture", "Music", "Navi", "Cloud Space", "HiCar", "SpeedCN"],
    ["Voboost Fixture", "Music", "Music", "Cloud Space", "HiCar"],
  ]) assert.throws(() => observedLauncherOrder(baseline, items(labels)), /order differs|ambiguous/);
});
test("unobservable and ambiguous page identities cannot be accepted", () => {
  for (const labels of [[], [""], ["Navi", "Navi"], ["x".repeat(257)]]) {
    assert.throws(() => observedLauncherOrder(items(labels), baseline), /unavailable|ambiguous/);
    assert.throws(() => observedLauncherOrder(baseline, items(labels)), /unavailable|ambiguous/);
  }
});
