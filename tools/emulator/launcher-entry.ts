import { launcherPointerNode, uniqueNode, type InstallerUi, type UiNode } from "./installer-ui.ts";

/** OEM HomeCardFragment is page 0 and AllAppFragment page 1 in MainActivity. */
export function launcherHomeGesture(nodes: UiNode[], nativePointer=false) {
  const select=nativePointer?launcherPointerNode:uniqueNode;
  const owner = "com.qinggan.app.launcher";
  const pager = select(nodes, n => n.packageName === owner && n.resource === `${owner}:id/home_view_pager` && n.visibleToUser === true && ["androidx.viewpager.widget.ViewPager", "androidx.viewpager2.widget.ViewPager2"].includes(n.className));
  const home = select(nodes, n => n.packageName === owner && n.resource === `${owner}:id/frameLayout_card` && n.visibleToUser === true && n.windowRef === pager.windowRef);
  if (home.bounds[0] < pager.bounds[0] || home.bounds[2] > pager.bounds[2] || home.bounds[1] < pager.bounds[1] || home.bounds[3] > pager.bounds[3]) throw new Error("OEM home page containment differs");
  if (nodes.some(n => n.packageName === owner && n.resource === `${owner}:id/all_app_view` && n.visibleToUser === true)) throw new Error("OEM home page is ambiguous");
  const [left, top, right, bottom] = pager.bounds;
  // The lower home margin avoids card buttons while remaining inside the pager.
  const y = Math.floor((home.bounds[3] + bottom) / 2);
  if (home.bounds[3] >= bottom || y <= top) throw new Error("OEM home swipe margin unavailable");
  return { pager, request: { action: "gesture", x1: Math.floor(left + (right-left)*0.8), y1: y, x2: Math.floor(left + (right-left)*0.2), y2: y, milliseconds: 500 } };
}

export async function openLauncherAppsFromHome(ui: Pick<InstallerUi, "snapshot" | "call" | "gestureAuthority"> & Partial<Pick<InstallerUi,"launcherPointerAuthority">>,nativePointer=false): Promise<void> {
  if(nativePointer && !ui.launcherPointerAuthority) throw new Error("native launcher pointer authority unavailable");
  const { pager, request } = launcherHomeGesture(await ui.snapshot(),nativePointer);
  await ui.call({ ...request, ...(nativePointer?ui.launcherPointerAuthority!(pager):ui.gestureAuthority(pager)) });
}
