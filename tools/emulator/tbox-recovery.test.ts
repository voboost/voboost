import assert from "node:assert/strict";
import test from "node:test";
import { tboxRecoveryTarget } from "./tbox-recovery.ts";
import type { UiNode } from "./installer-ui.ts";

function row(overrides: Partial<UiNode> = {}): UiNode {
  return { packageName: "com.qinggan.app.setting", resource: "com.qinggan.app.setting:id/kv_tbox_mpu_res_version", className: "android.widget.LinearLayout", text: "", enabled: true, visibleToUser: true, clickable: true, bounds: [1182, 313, 1849, 463], windowRef: { type: 1, active: true, inputFocused: true, rootPackage: "com.qinggan.app.setting", rootStatus: "available", scope: "default-display" }, ...overrides } as UiNode;
}

test("recovery targets the actual clickable OEM row despite translated or empty text", () => {
  for (const text of ["", "T-Box hardware version", "TBOX аппаратная версия", "TBOX硬件版本"]) {
    const target = row({ text });
    const decoy = row({ resource: "com.qinggan.app.setting:id/kv_tbox_vin", text: "TBOX VIN" });
    assert.equal(tboxRecoveryTarget([decoy, target]), target);
  }
});

test("recovery rejects missing, duplicate, disabled, invisible, foreign, unfocused and wrong typed rows", () => {
  assert.throws(() => tboxRecoveryTarget([]), /selector/);
  assert.throws(() => tboxRecoveryTarget([row(), row()]), /selector/);
  for (const overrides of [{ enabled: false }, { visibleToUser: false }, { clickable: false }, { packageName: "other" }, { className: "android.widget.TextView" }, { bounds: [0, 0, 0, 0] }, { windowRef: { ...row().windowRef!, inputFocused: false } }]) {
    assert.throws(() => tboxRecoveryTarget([row(overrides as Partial<UiNode>)]), /selector/);
  }
});
