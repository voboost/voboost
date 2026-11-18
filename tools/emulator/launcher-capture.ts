import {launcherPrimaryPointerWindow, type UiNode, type WindowRef} from "./installer-ui.ts";
type Page = { grid: UiNode; nodes: UiNode[] };
const descriptor = (w: WindowRef) => [w.type,w.layer,w.active,w.inputFocused,w.accessibilityFocused,w.bounds,w.title,w.rootStatus,w.rootPackage,w.scope,w.scopeBasis];
/** Correlate semantic UI observations across separate Accessibility registrations.
 * Window IDs remain authoritative only within each captured tree. This is a bounded
 * stability observation, not proof that the compositor/window instance was atomic. */
export function launcherCaptureContinuity(before: Page, after: Page, indicator: UiNode, afterIndicator: UiNode, beforeBoot: {boot:string;bootCompleted:boolean}, afterBoot: {boot:string;bootCompleted:boolean}, budgetMs: number, nativePointer=false): void {
  const owner="com.qinggan.app.launcher";
  const checked = (page:Page, marker:UiNode) => {
    const w=page.grid.windowRef;
    if(nativePointer && launcherPrimaryPointerWindow(page.nodes)!==w) throw new Error("launcher capture pointer owner differs");
    if(!w || !page.nodes.includes(page.grid) || !page.nodes.includes(marker) || marker.windowRef!==w || !page.grid.visibleToUser || !marker.visibleToUser || page.grid.packageName!==owner || marker.packageName!==owner || w.rootPackage!==owner || w.type!==1 || (!nativePointer && (!w.active || !w.inputFocused)) || w.rootStatus!=="available") throw new Error("launcher capture owner/window differs");
    const tree=page.nodes.filter(n=>n.windowRef===w).map(n=>{
      const {windowRef,...properties}=n;
      return properties;
    });
    return {w, tree};
  };
  const a=checked(before,indicator), b=checked(after,afterIndicator);
  if(!beforeBoot.boot || beforeBoot.boot!==afterBoot.boot || !beforeBoot.bootCompleted || !afterBoot.bootCompleted) throw new Error("launcher capture boot differs");
  if(!Number.isFinite(budgetMs)||budgetMs<=0||a.w.capture===b.w.capture || b.w.captureStart<=a.w.captureEnd || b.w.captureEnd-a.w.captureStart>budgetMs) throw new Error("launcher capture freshness interval differs");
  if(JSON.stringify(descriptor(a.w))!==JSON.stringify(descriptor(b.w)) || JSON.stringify(a.tree)!==JSON.stringify(b.tree)) throw new Error("launcher capture semantic window/tree differs");
}
