import { uniqueNode, type UiNode } from "./installer-ui.ts";

/** The OEM recovery hook is attached to the hardware-version row, not its translated label. */
export function tboxRecoveryTarget(nodes: UiNode[]): UiNode {
  const owner = "com.qinggan.app.setting";
  return uniqueNode(nodes, node => node.packageName === owner && node.resource === `${owner}:id/kv_tbox_mpu_res_version` && node.className === "android.widget.LinearLayout" && node.visibleToUser === true && node.clickable === true);
}
