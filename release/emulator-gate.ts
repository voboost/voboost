import { admitI18nSystemPicker, assertCurrentI18nObservation, I18nEvidenceUnavailable, type I18nEvidenceDirectories } from "../tools/emulator/i18n-system-picker.ts";
import { driverInvocation, isDriverInvocation } from "../tools/emulator/driver.ts";
import { baseline as nativeCrashBaseline, collect as collectNativeCrash, preserveFailure as preserveCrashFailure } from "../tools/emulator/native-crash.ts";
import { seekSecondaryFixture, secondaryPageSwipe } from "../tools/emulator/launcher-fixture.ts";
import { tboxRecoveryTarget } from "../tools/emulator/tbox-recovery.ts";
import { openLauncherAppsFromHome } from "../tools/emulator/launcher-entry.ts";
import { clearEditorByBackspace } from "../tools/emulator/navigation-editor.ts";
import { navigationSafetyConfirm, navigationSearchEntry, visualKey, reviewedKeyboardRoute, committedPrefix, actualKeyboardInput, type KeyboardFrame, type ReviewedLayout } from "../tools/emulator/navigation-editor.ts";
import { admitLocalArtifact, type VerificationDelta } from "../tools/release/local-source-admission.ts";
import { nativeTree, ownedPrimaryObservation, InitialObservation, BootTransition } from "../tools/emulator/native-tree.ts";
import { startupDeferredSelection } from "../tools/emulator/startup-deferred-selection.ts";
import { startupMilestoneEvidence } from "../tools/emulator/startup-milestones.ts";
import { fullSemanticSummary, fullFunctionalCase, semanticBoot, fullVisualOnly, requiredSubchecksPassed } from "../tools/emulator/full-semantic.ts";
import { currentEndpointShape, currentAcceptedEndpoint, stableCurrentAccepted, RESIDENT_STATUS_BYTES, RESIDENT_STATUS_ENVELOPE_BYTES } from "../tools/emulator/current-endpoint.ts";
import { nativeDispatch, nativeReadTransportTimeout, missingInputWindow, NativeSession, allowBlackFrame, verificationContract, ready as nativeReady, primaryHome, primaryHomeCaptureBoot, response as nativeResponse, capture as nativeCapture, type Config as NativeConfig } from "../tools/emulator/native-session.ts";
import { LauncherPageUnavailable, launcherSemanticBoundary } from "../tools/emulator/launcher-page-observation.ts";
import {launcherCaptureContinuity} from "../tools/emulator/launcher-capture.ts";
import {launcherAppsBeginningPng} from "../tools/emulator/launcher-page-indicator.ts";
import { readyBeforeDisable } from "../tools/emulator/agent-disable-readiness.ts";
import { admitStopModes, CLASSES, InventoryUnavailable, targetInventory, removedTargets, reenabledTargets, type Observation as DisableObservation } from "../tools/emulator/agent-disable.ts";
import { snapshotRepository } from "../tools/release/build-closure.ts";
import { launcherLifetime } from "../tools/emulator/launcher-lifetime.ts";
import { launcherFirstPublished } from "../tools/emulator/launcher-first-published.ts";
import { launcherCache, launcherThemePublication, launcherSharedColorCache, joinCacheWitness, CacheEvidenceUnavailable, type CacheWitness } from "../tools/emulator/launcher-cache.ts";
import { observedLauncherOrder } from "../tools/emulator/launcher-order.ts";
import { macDeviceResourceInventory, macResourceOverlaySourceIdentity } from "../tools/emulator/mac-resource-overlay.ts";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  realpathSync,
  writeFileSync,
  appendFileSync,
  rmSync,
} from "node:fs";
import { basename, dirname, isAbsolute, join, relative, sep } from "node:path";
import { oemTargetPid } from "../tools/emulator/process-observations.ts";
import { editorValue, editorCaretAtEnd, GesturePreInputDrift, InstallerUi, nativeApp, nativeAppReceipt, nativeRebootReceipt, nativePointerRequest, nativeKey, gestureTrajectory, launcherPageDrag, launcherPointerNode, launcherPrimaryPointerWindow, uniqueNode, agentControl, center, parseUi, validatePng, pngRaster, focusedApplication, vehicleLaunchPending, oemForcedEvAgree, type UiNode, type UiNodes } from "../tools/emulator/installer-ui.ts";
import { radioTransition } from "../tools/emulator/radio-transition.ts";
import { parseInstallerLogs } from "../tools/emulator/installer-logs.ts";
import { startupPreparationEvidence } from "../tools/emulator/startup-preparation.ts";
import { startupVehicleCanEvidence } from "../tools/emulator/startup-can.ts";
import { vehicleTiming, vehicleTimingSemantics, type TimingFence } from "../tools/emulator/vehicle-timing.ts";
import { agentToggleReady, inspectionResident, residentStatusShape, diagnosticBody, vehicleArmed, rootVehicleState, queuedSnapshot, freshDiagnostics, sequence, vehicleRead, accCycle, resetRestore, layoutCapture, renderLocale, catalogScan, oemFeature, VEHICLE, type Vehicle, type LayoutAuthority, type VehicleAuthority } from "../tools/emulator/functional-observations.ts";
import { fontCapture, actualFont, fontDiagnosticOnly, type FontAuthority } from "../tools/emulator/font-observations.ts";
import { preparationReceipts, preparationProof } from "../tools/emulator/install-preparation.ts";
import { installerDiagnostic, installerRebootProgress } from "../tools/emulator/installer-diagnostics.ts";
import { runtimeLogger } from "../tools/emulator/runtime-log.ts";
import { settingsCopy, panelCopy, type SettingsCopy } from "../tools/emulator/settings-copy.ts";
import { agentHostGates, AGENT_HOST_ARGS, assertLocalHostCatalog } from "../tools/emulator/host-gates.ts";
import { actionCoverage, consumeVisualReview, updateReviewedCases, visualChecks, type ReviewAuthority } from "../tools/emulator/visual-review.ts";
import { faultEffects, withFaultWindow, type FaultKind, type FaultReceipt, type FaultRequest } from "../tools/emulator/fault-window.ts";
import { stockProvenance } from "../tools/emulator/launcher-stock.ts";
import plan from "./beta1-functional-plan.json" with { type: "json" };





/** Local source locations only: no exception messages, function names or request data. */
export function hiddenVoboostMenuScroll(nodes: UiNode[]): UiNode | null {
  const owner = "com.qinggan.app.setting";
  const tabs = nodes.filter(n => n.packageName === owner && n.windowRef?.type === 1 && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootPackage === owner && n.text === "Voboost" && n.className === "android.widget.RadioButton" && n.clickable && n.enabled);
  if (tabs.length !== 1 || tabs[0].visibleToUser !== false) return null;
  const tab = tabs[0];
  const box = uniqueNode(nodes, n => n.packageName === owner && n.resource === `${owner}:id/layout_menu_setting_left` && n.className === "android.widget.ScrollView" && n.visibleToUser === true && n.scrollable && n.windowRef === tab.windowRef);
  let parent = tab.parent;
  while (parent !== null && nodes[parent] !== box) parent = nodes[parent]?.parent ?? null;
  if (parent === null) throw new Error("hidden Voboost tab is outside the owned Settings menu");
  return box;
}

export async function systemTboxRecoveryTarget(ui: Pick<InstallerUi, "snapshot" | "call" | "gestureAuthority">): Promise<UiNode> {
  return seekTboxRecoveryTarget(() => ui.snapshot(), async nodes => {
    const owner = "com.qinggan.app.setting";
    const selected = uniqueNode(nodes, n => n.packageName === owner && n.resource === `${owner}:id/menu_radio_system` && n.className === "android.widget.RadioButton" && n.visibleToUser === true && n.checked && n.clickable);
    const window = selected.windowRef;
    if (!window || window.type !== 1 || !window.active || !window.inputFocused || window.rootPackage !== owner) throw new Error("System recovery requires focused owned Settings window");
    const box = uniqueNode(nodes, n => n.packageName === owner && n.resource === `${owner}:id/system_scroll` && n.className === "android.widget.ScrollView" && n.scrollable && n.visibleToUser === true && n.windowRef === window);
    let parent = box.parent;
    while (parent !== null && nodes[parent]?.resource !== `${owner}:id/main_contentView`) parent = nodes[parent]?.parent ?? null;
    if (parent === null || nodes[parent].windowRef !== window) throw new Error("System recovery scroll outside owned right content");
    const [l, t, r, b] = box.bounds;
    const x = Math.floor((l + r) / 2);
    await ui.call({ action: "swipe", x1: x, x2: x, y1: Math.floor(t + (b - t) * 0.85), y2: Math.floor(t + (b - t) * 0.15), milliseconds: 400, ...ui.gestureAuthority(box) });
  });
}

export async function seekTboxRecoveryTarget(
  snapshot: () => Promise<UiNode[]>,
  scrollDown: (nodes: UiNode[]) => Promise<void>,
): Promise<UiNode> {
  let previousVisibleGeometry: string | null = null;
  for (let attempt = 0; attempt < 8; attempt++) {
    const nodes = await snapshot();
    const rows = nodes.filter(node => node.packageName === "com.qinggan.app.setting" && node.resource === "com.qinggan.app.setting:id/kv_tbox_mpu_res_version");
    if (rows.some(node => node.visibleToUser === true)) {
      const target = tboxRecoveryTarget(nodes);
      const geometry = JSON.stringify([target.windowRef?.id, target.rawBounds, target.bounds]);
      if (geometry === previousVisibleGeometry) return target;
      previousVisibleGeometry = geometry;
      // A fling may expose the row before it settles. Read only until two fresh
      // observations agree; never replay a rejected pointer dispatch.
      continue;
    }
    previousVisibleGeometry = null;
    if (attempt === 7) break;
    await scrollDown(nodes);
  }
  throw new Error("TBOX recovery row not visible after bounded System scrolling");
}

export function caseFailureFrames(error: unknown): string[] {
  if (!(error instanceof Error) || typeof error.stack !== "string") return [];
  const root = decodeURIComponent(new URL("../", import.meta.url).pathname);
  const frames: string[] = [];
  for (const line of error.stack.slice(0, 16384).split("\n").slice(1, 65)) {
    if (!/^\s+at /.test(line)) continue;
    const match = line.match(/(?:\(|\s)((?:file:\/\/)?\/[^()\r\n]+\.ts):(\d+):(\d+)\)?$/);
    if (!match) continue;
    const path = match[1].replace(/^file:\/\//, "");
    if (!path.startsWith(root) || /[?#\x00-\x1f]/.test(path)) continue;
    const source = path.slice(root.length);
    if (!/^(release|tools)\/[a-zA-Z0-9_./-]+\.ts$/.test(source) || source.includes("..")) continue;
    frames.push(`${source}:${match[2]}:${match[3]}`);
    if (frames.length === 32) break;
  }
  return frames;
}

/** BlindsWindow overrides root onTouch: negative X velocity collapses; unknown child hit regions are excluded. */
export function drawerCloseTarget(nodes: UiNode[]): {node:UiNode;x:number;y:number;x2:number;milliseconds:number;region:readonly number[]} {
  const owner="com.qinggan.app.launcher";
  const roots=nodes.filter(n=>n.parent===null && n.packageName===owner && n.className==="android.widget.FrameLayout" &&
    n.resource==="" && n.visibleToUser===true && n.enabled && n.windowRef?.type===3 && n.windowRef.active &&
    n.windowRef.inputFocused && n.windowRef.rootStatus==="available" && n.windowRef.rootPackage===owner);
  if(roots.length!==1) throw new Error("drawer close root unavailable");
  const root=roots[0],same=nodes.filter(n=>n.windowRef===root.windowRef && n.visibleToUser===true);
  const content=same.filter(n=>n.resource===owner+":id/screen_up_vehicle_hiboard_new" && n.parent===nodes.indexOf(root) && n.className==="android.widget.RelativeLayout");
  const background=same.filter(n=>n.resource===owner+":id/view_screen_up_vehicle_hiboard_bg" && n.parent===nodes.indexOf(content[0]) && n.className==="android.widget.ImageView" && !n.clickable && !n.scrollable && !n.longClickable);
  const edit=same.filter(n=>n.resource===owner+":id/screen_up_edit_btn" && n.enabled && n.clickable && ["Edit","编辑"].includes(n.text));
  if(content.length!==1 || background.length!==1 || edit.length!==1 ||
     JSON.stringify(content[0].bounds)!==JSON.stringify(root.bounds) || JSON.stringify(background[0].bounds)!==JSON.stringify(root.bounds))
    throw new Error("drawer normal-mode background topology unavailable");
  const [left,top,right,bottom]=root.bounds;
  const blockers=same.filter(n=>n!==root && n!==content[0] && n!==background[0]).map(n=>n.bounds)
    .filter(b=>b[2]>left && b[0]<right && b[3]>top && b[1]<bottom);
  const xs=[...new Set([left,right,...blockers.flatMap(b=>[Math.max(left,b[0]),Math.min(right,b[2])])])].sort((a,b)=>a-b);
  const ys=[...new Set([top,bottom,...blockers.flatMap(b=>[Math.max(top,b[1]),Math.min(bottom,b[3])])])].sort((a,b)=>a-b);
  let chosen:{x:number;y:number;x2:number;area:number;region:number[]}|null=null;
  for(let x=0;x<xs.length-1;x++) for(let y=0;y<ys.length-1;y++) {
    const x1=xs[x],x2=xs[x+1],y1=ys[y],y2=ys[y+1];
    if(x2-x1<64 || y2-y1<4 || blockers.some(b=>b[0]<x2 && b[2]>x1 && b[1]<y2 && b[3]>y1)) continue;
    const area=(x2-x1)*(y2-y1);
    if(!chosen || area>chosen.area) chosen={x:x2-2,y:Math.floor((y1+y2)/2),x2:x1+2,area,region:[x1,y1,x2,y2]};
  }
  if(!chosen) throw new Error("drawer proven empty background region unavailable");
  return {node:root,x:chosen.x,y:chosen.y,x2:chosen.x2,milliseconds:100,region:chosen.region};
}

/** One switch click may start an asynchronous OEM service/dialog; never replay it. */
export async function awaitDrawerOn(ui: Pick<InstallerUi,"snapshot"|"tap">, readCan:()=>Promise<any>, boot: string, deadline: number, now=()=>performance.now(), closeDrawer?: (nodes:UiNode[])=>Promise<void>, underlyingOwner?: string): Promise<void> {
  const owner="com.qinggan.app.launcher",dialogOwner="com.qinggan.app.vehiclesetting";
  let confirmed=false,closed=false,closeRequested=false,drawerWindowId:number|undefined,lastRaw:unknown=null,lastSelected:unknown=null,lastOwners:string[]=[];
  while(now()<deadline) {
    const nodes=await ui.snapshot();
    const windows=(nodes as UiNodes).windowRefs;
    if(closeRequested && Array.isArray(windows) && !windows.some(w=>w.id===drawerWindowId && w.rootPackage===owner) &&
       !nodes.some(n=>n.packageName===owner && n.resource===owner+":id/screen_up_vehicle_hiboard_new" && n.visibleToUser===true)) closed=true;
    lastOwners=[...new Set(nodes.filter(n=>n.windowRef?.active && n.windowRef.inputFocused).map(n=>n.windowRef!.rootPackage))];
    if(lastOwners.some(name=>name!==owner && name!==dialogOwner && !((closeRequested || confirmed) && name===underlyingOwner && ["com.qinggan.app.setting","com.qinggan.app.vehiclesetting"].includes(name)))) throw new Error("drawer ON unexpected focused owner; switch is not replayed");
    const buttons=nodes.filter(n=>n.packageName===dialogOwner && n.resource===dialogOwner+":id/disclaimer_agree" && n.visibleToUser===true);
    if(buttons.length) {
      if(!confirmed && (!closeRequested || closed)) {const agree=oemForcedEvAgree(nodes);await ui.tap(agree);confirmed=true;}
    } else if(closeDrawer && !closeRequested && !confirmed && nodes.some(n=>n.packageName===owner && n.resource===owner+":id/force_ev_switcher" && n.visibleToUser===true && !n.selected)) {
      const root=drawerCloseTarget(nodes);
      await closeDrawer(nodes);drawerWindowId=root.node.windowRef!.id;closeRequested=true;
    }
    const can=await readCan();
    if(can.status!=="ready" || can.boot_id!==boot) throw new Error("drawer ON CAN boot or availability differs");
    lastRaw=can.values?.["vehicle_state.IVI_SOC_MODESET"]?.raw;
    const switches=nodes.filter(n=>n.packageName===owner && n.resource===owner+":id/force_ev_switcher" && n.visibleToUser===true);
    lastSelected=switches.length===1 ? switches[0].selected : null;
    if(now()>=deadline) break;
    if(lastRaw===5 && (!closeRequested || closed) && (confirmed || (lastSelected===true && lastOwners.length===1 && lastOwners[0]===owner))) return;
  }
  throw new Error("drawer ON effect deadline expired: "+JSON.stringify({raw:lastRaw,selected:lastSelected,focusedOwners:lastOwners,confirmed,closed,closeRequested}));
}

/** Reacquire one semantic drawer switch after a proven non-dispatched missing window. */
export async function tapDrawerSwitch(ui: Pick<InstallerUi,"tap">, initial: UiNode, reacquire:()=>Promise<UiNode>, deadline:number, now=()=>performance.now()):Promise<void> {
  const owner="com.qinggan.app.launcher", resource=owner+":id/force_ev_switcher";
  const valid=(n:UiNode)=>n.packageName===owner && n.resource===resource && n.enabled && n.visibleToUser===true && n.windowRef?.type===3 && n.windowRef.rootPackage===owner && n.windowRef.rootStatus==="available";
  if(!valid(initial)) throw new Error("drawer semantic switch authority differs");
  try {await ui.tap(initial);return;}
  catch(error) {
    const w=initial.windowRef!;
    if(!missingInputWindow(error,{id:w.id,type:w.type,layer:w.layer,bounds:{x:w.bounds[0],y:w.bounds[1],width:w.bounds[2]-w.bounds[0],height:w.bounds[3]-w.bounds[1]}}) || now()>=deadline) throw error;
    const fresh=await reacquire();
    if(now()>=deadline || fresh===initial || !valid(fresh) || fresh.className!==initial.className || fresh.selected!==initial.selected) throw new Error("drawer semantic switch changed during reacquisition; input not replayed");
    // New observation creates a new fenced request. A second rejection is terminal.
    await ui.tap(fresh);
  }
}

/** Bind the power card to its actual descendant caption, never a coordinate overlap. */
export function drawerModeCard(nodes: UiNode[]): {node:UiNode;caption:string} {
  const owner="com.qinggan.app.launcher", modes=new Set(["Electric","Hybrid","Save","纯电优先","油电混合","保电模式"]);
  const matches=nodes.filter(n=>n.packageName===owner && n.resource===owner+":id/tv_title" && n.visibleToUser===true && modes.has(n.text)).flatMap(label=>{
    let node:UiNode|undefined=label;
    const seen=new Set<UiNode>();
    while(node && node.resource!==owner+":id/rl_root") {
      if(seen.has(node)) return [];seen.add(node);
      node=node.parent===null ? undefined : nodes[node.parent];
    }
    return node && node.packageName===owner && node.className==="android.widget.RelativeLayout" && node.clickable && node.enabled && node.visibleToUser===true && node.windowRef?.type===3 && node.windowRef.rootPackage===owner && node.windowRef.rootStatus==="available" ? [{node,caption:label.text}] : [];
  });
  if(matches.length!==1) throw new Error("unique actual drawer power-mode card unavailable");
  return matches[0];
}

/** A mode click is non-idempotent: retry only an exactly correlated non-dispatch. */
export async function tapDrawerModeCard(ui:Pick<InstallerUi,"tap">,initial:ReturnType<typeof drawerModeCard>,reacquire:()=>Promise<ReturnType<typeof drawerModeCard>>,deadline:number,now=()=>performance.now()):Promise<void> {
  if(now()>=deadline) throw new Error("drawer mode tap/reacquisition deadline expired before input");
  try {await ui.tap(initial.node);return;}
  catch(error) {
    const w=initial.node.windowRef!;
    if(!missingInputWindow(error,{id:w.id,type:w.type,layer:w.layer,bounds:{x:w.bounds[0],y:w.bounds[1],width:w.bounds[2]-w.bounds[0],height:w.bounds[3]-w.bounds[1]}})) throw error;
    if(now()>=deadline) throw new Error("drawer mode tap/reacquisition deadline expired; input not replayed");
    const fresh=await reacquire();
    if(now()>=deadline) throw new Error("drawer mode tap/reacquisition deadline expired; input not replayed");
    if(fresh.node===initial.node) throw new Error("drawer mode observation remained stale; input not replayed");
    if(fresh.caption!==initial.caption) throw new Error("drawer semantic mode changed during reacquisition; input not replayed");
    if(fresh.node.packageName!==initial.node.packageName || fresh.node.resource!==initial.node.resource || fresh.node.className!==initial.node.className || !fresh.node.enabled || fresh.node.visibleToUser!==true || !fresh.node.clickable || fresh.node.windowRef?.type!==3 || fresh.node.windowRef.rootPackage!==initial.node.packageName || fresh.node.windowRef.rootStatus!=="available") throw new Error("drawer mode target authority changed during reacquisition; input not replayed");
    await ui.tap(fresh.node);
  }
}

/** A retry may only reopen the same observed closed OEM Home and receiver. */
export function drawerClosedHomeSignature(nodes:UiNode[]):string|null {
  const owner="com.qinggan.app.launcher",windows=(nodes as UiNodes).windowRefs??[];
  const focused=windows.filter(w=>w.active && w.inputFocused);
  if(focused.length!==1 || focused[0].type!==1 || focused[0].title!=="module-widget" ||
     focused[0].rootPackage!==owner || focused[0].rootStatus!=="available" || focused[0].scope!=="default-display") return null;
  if(nodes.some(n=>n.packageName===owner && n.resource===owner+":id/force_ev_switcher" && n.visibleToUser===true)) return null;
  const edges=nodes.filter(n=>n.packageName===owner && n.resource===owner+":id/line_view" &&
    n.className==="android.view.View" && n.visibleToUser===true && n.enabled && n.parent!==null &&
    n.windowRef?.type===3 && n.windowRef.rootPackage===owner && n.windowRef.rootStatus==="available" &&
    n.windowRef.scope==="default-display" && n.windowRef.bounds[0]===0 &&
    n.windowRef.bounds[3]>n.windowRef.bounds[2] && n.bounds[0]>=0 &&
    n.bounds[2]<=n.windowRef.bounds[2] && n.bounds[3]-n.bounds[1]>n.bounds[2]-n.bounds[0]);
  if(edges.length!==1) return null;
  return JSON.stringify({windows:windows.map(w=>[w.id,w.type,w.layer,w.active,w.inputFocused,w.rootPackage,w.rootStatus,w.scope,w.title,w.bounds]).sort((a,b)=>Number(a[0])-Number(b[0])),edge:[edges[0].windowRef!.id,edges[0].bounds]});
}

/** Observe the accepted drawer gesture; never dispatch another input while waiting. */
export async function awaitDrawerPowerCard(ui:Pick<InstallerUi,"snapshot">,readBoot:()=>Promise<string>,boot:string,deadline:number,now=()=>performance.now(),retry?:{afterMs:number;reopen:(nodes:UiNode[])=>Promise<void>}):Promise<UiNode[]> {
  const retryAt=now()+(retry?.afterMs??Infinity);let retried=false;
  const owner="com.qinggan.app.launcher",modes=["Electric","Hybrid","Save","纯电优先","油电混合","保电模式"];
  for(let read=0;read<64 && now()<deadline;read++) {
    const nodes=await ui.snapshot();
    if(await readBoot()!==boot) throw new Error("drawer visibility crossed boot identity");
    const focused=(nodes as UiNodes).windowRefs?.filter(w=>w.active && w.inputFocused) ?? [];
    if(focused.some(w=>w.rootPackage!==owner)) throw new Error("drawer visibility unexpected focused owner; gesture not replayed");
    if(now()>=deadline) break;
    if(focused.length===1 && focused[0].rootStatus==="available" && focused[0].scope==="default-display" && [1,3].includes(focused[0].type) &&
       nodes.some(n=>n.packageName===owner && n.resource===owner+":id/tv_title" && modes.includes(n.text) && n.visibleToUser===true &&
         n.windowRef?.type===3 && n.windowRef.rootPackage===owner && n.windowRef.rootStatus==="available" && n.windowRef.scope==="default-display" && (nodes as UiNodes).windowRefs?.includes(n.windowRef))) return nodes;
    if(retry && !retried && now()>=retryAt) {
      retried=true;
      await retry.reopen(nodes);
    }
  }
  throw new Error("actual drawer power card not visible before deadline after accepted gesture");
}

export function knownSecondaryLauncherFocus(observed:{boot:string;bootCompleted:boolean;body:string},boot:string):void {
  const section = /^window-focus:\r?\n([\s\S]*?)\r?\nandroid-boot-errors:\r?\n/m.exec(observed.body)?.[1] ?? "";
  const rows = section.split("\n").filter(line => /^\s*mCurrentFocus=/.test(line));
  if(observed.boot!==boot || !observed.bootCompleted || rows.length!==1 ||
     !/^\s*mCurrentFocus=Window\{[0-9a-f]+ u0 com\.qinggan\.app\.launcher\/com\.qinggan\.secondlauncher\.activity\.(?:AllAppActivity|SecondMainActivity)\}\s*$/.test(rows[0]))
    throw new Error("launcher focus owner is not the known secondary launcher");
}

/** Observe the primary launcher without treating global secondary focus as readiness.
 * This grants no input authority: every pointer still uses the captured native
 * window/node/source fence and native known-window occlusion check. */
export async function observePrimaryLauncher(ui:Pick<InstallerUi,"snapshot">,readBoot:()=>Promise<string>,readSource:()=>string,deadline:number,now=()=>performance.now()):Promise<void> {
  const boot=await readBoot();
  if(!boot || now()>=deadline) throw new Error("launcher primary observation deadline or boot differs");
  const nodes=await ui.snapshot(),source=readSource();
  if(!source) throw new Error("launcher primary observer identity absent");
  const main=launcherPrimaryPointerWindow(nodes);
  if(main.title!=="module-widget") throw new Error("launcher primary observed main root required");
  if(await readBoot()!==boot || readSource()!==source) throw new Error("launcher primary observation crossed boot or observer identity");
  if(now()>=deadline) throw new Error("launcher primary observation deadline expired");
}

/** One fenced OEM Home tap may restore primary focus after secondary observation. */
export async function ensurePrimaryLauncherFocus(ui:Pick<InstallerUi,"call"|"snapshot">,readBoot:()=>Promise<string>,readSource:()=>string,deadline:number,now=()=>performance.now(),secondaryFocus?:()=>Promise<{boot:string;bootCompleted:boolean;body:string}>):Promise<boolean> {
  const owner="com.qinggan.app.launcher",boot=await readBoot();
  let source:string|undefined,tapped=false;
  for(let read=0;read<16 && now()<deadline;read++) {
    const nodes=await ui.snapshot(),epoch=readSource();
    if(await readBoot()!==boot) throw new Error("launcher focus crossed boot identity");
    if(!epoch || (source!==undefined && epoch!==source)) throw new Error("launcher focus crossed observer identity");
    source=epoch;
    if(now()>=deadline) break;
    const windows=(nodes as UiNodes).windowRefs;
    if(!windows || windows.some(w=>w.rootStatus!=="available" || w.scope!=="default-display")) throw new Error("launcher focus complete primary windows required");
    const engaged=windows.filter(w=>w.active || w.inputFocused);
    if(engaged.some(w=>w.rootPackage!==owner)) throw new Error("launcher focus foreign active window");
    const main=windows.filter(w=>w.type===1 && w.rootPackage===owner);
    if(main.length!==1) throw new Error("launcher focus unique OEM main required");
    if(launcherPrimaryPointerWindow(nodes)!==main[0] || main[0].title!=="module-widget") throw new Error("launcher focus observed main root required");
    if(engaged.length===1 && main[0].active && main[0].inputFocused) return tapped;
    if(engaged.length) throw new Error("launcher focus ambiguous primary owner");
    if(tapped) continue;
    if(!secondaryFocus) throw new Error("launcher focus secondary owner proof required");
    knownSecondaryLauncherFocus(await secondaryFocus(),boot);
    if(await readBoot()!==boot || readSource()!==source || now()>=deadline) throw new Error("launcher focus authority crossed identity or deadline");
    // Inspect does not create new UI authority. The target is still the exact
    // captured dock node, fenced again by native input before dispatch.
    const homes=nodes.filter(n=>n.packageName===owner && n.resource===owner+":id/screen_up_home_icon");
    if(homes.length!==1) throw new Error("launcher focus unique OEM Home required");
    const n=homes[0],w=n.windowRef,a=main[0],b=n.rawBounds??n.bounds;
    if(!w || !windows.includes(w) || ![-1,3].includes(w.type) || w.rootPackage!==owner || w.capture!==a.capture || w.frameSha256!==a.frameSha256 ||
       n.className!=="android.widget.RadioButton" || !n.enabled || !n.clickable || n.visibleToUser!==true ||
       w.bounds[0]!==a.bounds[0] || w.bounds[2]<=w.bounds[0] || w.bounds[2]>=a.bounds[2] || w.layer<=a.layer ||
       n.bounds[0]<w.bounds[0] || n.bounds[1]<w.bounds[1] || n.bounds[2]>w.bounds[2] || n.bounds[3]>w.bounds[3])
      throw new Error("launcher focus Home identity or containment differs");
    const [x,y]=center(n),rect=(r:readonly number[])=>`[${r[0]},${r[1]}][${r[2]},${r[3]}]`;
    // Explicit target authority permits an inactive dock; native expected fencing
    // still checks boot/source/window/node identity and known-window occlusion.
    await ui.call({action:"tap",x,y,
      expectedWindow:{id:a.id,windowType:a.type,bounds:rect(a.bounds),title:a.title,packageName:owner,captureEndUptimeMs:String(a.captureEnd),displayId:0},
      expectedTarget:{windowBounds:rect(w.bounds),windowTitle:w.title,rootPackage:owner,className:n.className,windowId:w.id,windowType:w.type,packageName:owner,resource:n.resource,text:n.text,bounds:rect(b)}});
    tapped=true;
  }
  throw new Error("launcher focus not confirmed after one fenced Home tap");
}

/** Bring the OEM Home forward before requiring its drawer receiver. Acceptance is not readiness. */
export async function prepareDrawerHome(ui:Pick<InstallerUi,"call"|"snapshot">,readBoot:()=>Promise<string>,deadline:number,now=()=>performance.now()):Promise<void> {
  const boot=await readBoot();
  if(now()>=deadline) throw new Error("drawer Home preparation deadline expired");
  await ui.call({action:"open-primary-launcher"});
  for(let read=0;read<32 && now()<deadline;read++) {
    const nodes=await ui.snapshot();
    if(await readBoot()!==boot) throw new Error("drawer Home preparation crossed boot identity");
    if(now()>=deadline) break;
    const focused=(nodes as UiNodes).windowRefs?.filter(w=>w.active && w.inputFocused) ?? [];
    if(focused.length===1 && focused[0].rootPackage==="com.qinggan.app.launcher" &&
       focused[0].rootStatus==="available" && focused[0].scope==="default-display" &&
       [1,3].includes(focused[0].type) && focused[0].bounds[2]>focused[0].bounds[0] && focused[0].bounds[3]>focused[0].bounds[1] &&
       nodes.some(n=>n.parent===null && n.windowRef===focused[0] && n.packageName===focused[0].rootPackage && n.enabled && n.visibleToUser===true)) return;
  }
  throw new Error("drawer OEM focused Home owner not ready");
}

/** Current accepted endpoints survive log rotation; OS identities remain independent. */
export function drawerCurrentAuthority(before: {boot:string;body:string}, after: {boot:string;body:string}, catalog: any[], catalogSha256: string, manifestSha256: string, releaseIdentity: string): VehicleAuthority {
  if (before.boot !== after.boot) throw new Error("drawer authority crossed boot");
  const observe = (sample: {boot:string;body:string}) => {
    const status=inspectionResident(sample.body);
    if (status.releaseIdentity!==releaseIdentity || status.manifestSha256!==manifestSha256 || status.catalogSha256!==catalogSha256 ||
        status.state!=="ready" || status.killed!==false || status.panic!==false) throw new Error("drawer resident authority differs");
    const row=vehicleRootTarget(status.injections,catalog,"fev");
    const sections=[...sample.body.matchAll(/^current-process-identities:\n([\s\S]*?)^current-process-identities-end=complete$/gm)];
    if(sections.length!==1) throw new Error("drawer independent process section unavailable");
    const text=sections[0][1];
    if(!text.startsWith("schemaVersion=1\nbootId="+sample.boot+"\nenforcing=Enforcing\n") ||
       !text.endsWith("bootIdAfter="+sample.boot+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\n")) throw new Error("drawer process boot fence differs");
    const processes=new Map<string,{pid:string;starttime:string;argv0:string}>();
    for(const line of text.split("\n").filter(l=>l.startsWith("process="))) {
      const match=/^process=([A-Za-z0-9_.:-]+) pid=([1-9][0-9]*) starttime=([1-9][0-9]*) argv0=([A-Za-z0-9_./:-]+)$/.exec(line);
      if(!match) {if(/^process=[A-Za-z0-9_.:-]+ unavailable=missing-or-invalid$/.test(line)) continue;throw new Error("drawer process identity malformed");}
      if(processes.has(match[1])) throw new Error("drawer process duplicate");
      processes.set(match[1],{pid:match[2],starttime:match[3],argv0:match[4]});
    }
    const daemon=processes.get("voboost-daemon"),target=processes.get(row.process),publisher=status.publisher;
    if(!daemon || !target || daemon.argv0!=="/data/voboost/voboost-daemon" || target.argv0!==row.process ||
       !publisher || publisher.bootId!==sample.boot || publisher.daemonPid!==daemon.pid || publisher.daemonStarttime!==daemon.starttime)
      throw new Error("drawer publisher or OS process identity unavailable");
    const endpoint=row.currentEndpoint;
    const authority={targetPid:target.pid,targetStarttime:target.starttime,endpointEpoch:endpoint?.endpointEpoch,
      generationId:status.generationId,catalogSha256,desiredDocumentGeneration:row.documentGeneration,
      bootId:sample.boot,daemonPid:daemon.pid,daemonStarttime:daemon.starttime};
    if(!currentAcceptedEndpoint(endpoint,authority)) throw new Error("drawer current accepted endpoint differs");
    return {authority,endpoint,row};
  };
  const first=observe(before),last=observe(after);
  if(!stableCurrentAccepted(first.endpoint,last.endpoint,first.authority)) throw new Error("drawer endpoint incarnation or desired revision changed");
  return {boot:after.boot,baselineBoot:before.boot,generationId:last.authority.generationId,catalogSha256,process:last.row.process,
    targetPid:last.authority.targetPid,targetStarttime:last.authority.targetStarttime,endpointEpoch:last.authority.endpointEpoch,
    documentGeneration:last.authority.desiredDocumentGeneration};
}

/** Lossless CAN coverage proves no re-enable; sampled UI alone cannot. */
export function drawerOffJournal(batches: any[], boot: string, source: string, cursor: string, durationMs: number): { cursor: string; coveredMs: number } {
  let previous = BigInt(cursor), offAt: bigint | null = null, sampled = 0n;
  for (const batch of batches) {
    if (batch?.status !== "ready" || batch.boot_id !== boot || batch.source_instance !== source ||
        batch.coverage?.vehicle_state_setter !== true || batch.history?.gap !== null ||
        batch.history?.next_cursor?.source_instance !== source || !Array.isArray(batch.events))
      throw new Error("drawer CAN continuous coverage unavailable");
    for (const event of batch.events) {
      const seq = BigInt(event.seq);
      if (event.source_instance !== source || seq !== previous + 1n) throw new Error("drawer CAN sequence gap");
      previous = seq;
      if (event.kind === "state_applied" && event.field === "vehicle_state.IVI_SOC_MODESET") {
        if (offAt === null && event.raw === 2) offAt = BigInt(event.elapsed_ns);
        else if (offAt !== null && event.raw === 5) throw new Error("FEV re-enabled after explicit OFF");
      }
    }
    if (BigInt(batch.history.next_cursor.after_seq) !== previous) throw new Error("drawer CAN cursor gap");
    sampled = BigInt(batch.clock.sampled_ns);
  }
  const last = batches.at(-1);
  if (offAt === null || last?.history.has_more !== false || sampled < offAt ||
      sampled - offAt < BigInt(durationMs) * 1000000n) throw new Error("drawer CAN OFF interval unavailable");
  return { cursor: previous.toString(), coveredMs: Number(sampled-offAt)/1000000 };
}

/** Settings itself is the panel/tab, never one of its agent checkboxes. */
export function visibleAgentChecks(nodes: UiNode[], catalog: any[], language: string): Map<string, boolean> {
  const enabled = new Map<string, boolean>();
  for (const agent of catalog) {
    if (agent.activation?.type !== "checkbox") continue;
    const title = agent.title?.[language];
    if (typeof title !== "string" || !title) throw new Error(`checkbox agent ${agent.id} has no ${language} title`);
    const control = agentControl(nodes, SETTINGS, title);
    if (control) enabled.set(agent.id, control.checked);
  }
  return enabled;
}

/** A clipped label remains a navigation hint, never a tappable checkbox. */
export function agentScrollDirection(nodes: UiNode[], packageName: string, title: string, viewport: UiNode): "up" | "down" {
  // Offscreen labels are read-only direction anchors, never tap authority.
  const labels = nodes.filter(n => n.packageName === packageName && n.text === title &&
    (!n.windowRef || (n.windowRef===viewport.windowRef && [1,3].includes(n.windowRef.type) && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootStatus==="available" && n.windowRef.rootPackage===packageName)));
  if (labels.length > 1) throw new Error("ambiguous agent navigation label");
  if (labels.length === 0) return "down";
  return (labels[0].bounds[1] + labels[0].bounds[3]) / 2 < (viewport.bounds[1] + viewport.bounds[3]) / 2 ? "up" : "down";
}

export const CASES = plan.cases;
export const EXECUTION_ORDER = [
  1, 2, 3, 4, 5, 13, 14, 12, 6, 7, 8, 9, 19, 10, 11, 18, 24, 25, 15, 16, 20, 21, 22, 23, 27, 17, 26,
] as const;
export type GateConfig = {
  workspaceRoot: string;
  localArtifact?: { verificationDelta: VerificationDelta[] };
  i18nRuntimeEvidence?: I18nEvidenceDirectories;
  reviewedKeyboardMaps?: string; // Explicit local reviewed JSON; absent means no canvas fallback.
  voyemuRoot: string;
  voyemuProfile: string;
  nativeSession?: { executable: string; receiptFile: string };
  closure: string;
  package: string;
  dmg: string;
  fixture: string | null;
  output: string;
};
export type Invocation = {
  executable: string;
  args: string[];
  timeoutMs: number;
  voyemu?: boolean;
  host?: "agents";
  onOutput?: (stream: "stdout" | "stderr", chunk: string) => void;
  stdinUntil?: Promise<void>;
  stdinReply?: (stream: "stdout" | "stderr", chunk: string) => Promise<string | null>;
};
export type ProcessResult = {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  timedOut?: boolean;
  signal?: string | null;
  processId?: number;
  overflow?: boolean;
  actionId?: number;
  retained?: { overflow: boolean };
};
export type ProcessAdapter = (invocation: Invocation) => Promise<ProcessResult>;
export type VoyemuIdentity = { port: number; pid: number };
export type Verdict = "passed" | "failed" | "unavailable" | "not-run";
type Subcheck = { id: string; status: Verdict; reason: string; artifacts: string[] };
type Case = {
  number: number;
  id: string;
  status: Verdict;
  reason: string;
  actions: number[];
  subchecks: Subcheck[];
};
type Boot = {
  id: string;
  caseNumber: number;
  kind: string;
  status: Verdict;
  reason: string;
  before: string | null;
  after: string | null;
  verifiedBoot: string | null;
  actions: number[];
  screenshot: string | null;
  homeProof?: { before: number; after: number; capture: number };
  semanticStatus?: "passed";
  observation?: { actionId: number; finishedMonotonicNs: string; rebootCount: number | null };
};
/** Root observations invalidate UI tokens, so prepare the checkbox only after reading root. */
export async function agentToggleInput(ui: InstallerUi, title: string): Promise<{ before: any; check: UiNode; boot: string }> {
  const initial = await ui.call({ action: "resident-status" });
  const { boot } = parseResidentStatus(initial.body);
  const before = inspectionResident(initial.body);
  const check = agentControl(await ui.snapshot(), "com.qinggan.app.setting", title);
  if (!check) throw new Error("agent toggle fresh checkbox unavailable");
  return { before, check, boot };
}

/** Retry only a certified pre-input rejection, reacquiring root authority and semantic checkbox. */
export async function agentToggleTap(ui: InstallerUi, title: string, expectedChecked: boolean, validate: (before: any) => void): Promise<any> {
  let authority: string | undefined;
  for (let attempt = 0; attempt < 2; attempt++) {
    const { before, check, boot } = await agentToggleInput(ui, title);
    validate(before);
    const settings = before.injections.filter((row: any) => row.id === "settings" && row.process === "com.qinggan.app.setting");
    if (settings.length !== 1 || settings[0].state !== "active" || typeof settings[0].documentGeneration !== "string") throw new Error("agent toggle Settings authority differs");
    const current = JSON.stringify([boot, before.releaseIdentity, before.manifestSha256, before.generationId, before.catalogSha256, before.startupMode, settings[0].documentGeneration, settings[0].lifecycleGeneration]);
    if (authority !== undefined && authority !== current) throw new Error("agent toggle authority changed during recapture");
    authority = current;
    if (check.checked !== expectedChecked) throw new Error("agent toggle checkbox changed before input");
    try { await ui.tap(check); return before; }
    catch (error) { if (!(error instanceof GesturePreInputDrift) || attempt !== 0) throw error; }
  }
  throw new Error("agent toggle recapture exhausted");
}

/** A confirmed launcher restart may foreground OEM Home; no other drift is recoverable. */
export type ToggleRestartProof = {
  mode: "restart" | "in-place"; targets: string[];
  beforeBoot: string; afterBoot: string;
  beforePids: Record<string,string>; afterPids: Record<string,string>;
  beforeResident: any; currentResident: any;
};
export function launcherToggleReturnedHome(id: string, nodes: UiNode[], proof?: ToggleRestartProof): boolean {
  if (id !== "launcher-apps") {
    if (!proof || proof.mode !== "restart" || !proof.beforeBoot || proof.beforeBoot !== proof.afterBoot || !agentToggleReady(proof.beforeResident, proof.currentResident, id, false)) return false;
    const hosts = proof.targets.filter(process => process === "com.qinggan.app.setting" || process === "com.qinggan.app.launcher");
    const running = hosts.filter(process => proof.beforePids[process]);
    if (!running.length || running.some(process => !proof.beforeResident.injections.some((row:any)=>row.id===id && row.process===process && row.state==="active") || !/^[1-9][0-9]*$/.test(proof.beforePids[process]) || !/^[1-9][0-9]*$/.test(proof.afterPids[process] ?? "") || proof.beforePids[process] === proof.afterPids[process])) return false;
  }
  const focused = nodes.filter(node => focusedApplication(node));
  return focused.length > 0 && focused.every(node =>
    node.packageName === "com.qinggan.app.launcher" &&
    node.windowRef?.type === 1 && node.windowRef.active &&
    node.windowRef.rootPackage === "com.qinggan.app.launcher" &&
    node.windowRef?.title === "module-widget");
}

/** Unfocused primary Home permits fixed navigation only, never pointer authority. */
export function launcherRestartUnfocusedHome(id: string, nodes: UiNodes, proof?: ToggleRestartProof): boolean {
  const launcher = "com.qinggan.app.launcher";
  if (!proof || proof.mode !== "restart" || !proof.beforeBoot || proof.beforeBoot !== proof.afterBoot || !proof.targets.includes(launcher) || !agentToggleReady(proof.beforeResident, proof.currentResident, id, false)) return false;
  const running = proof.targets.filter(process => ["com.qinggan.app.setting", launcher].includes(process) && proof.beforePids[process]);
  if (!running.includes(launcher) || running.some(process =>
    !proof.beforeResident.injections.some((row: any) => row.id === id && row.process === process && row.state === "active") ||
    !/^[1-9][0-9]*$/.test(proof.beforePids[process]) || !/^[1-9][0-9]*$/.test(proof.afterPids[process] ?? "") || proof.beforePids[process] === proof.afterPids[process])) return false;
  const windows = nodes.windowRefs;
  if (!windows?.length || windows.some(w => w.scopeBasis !== "voyemu-guest-accessibility" || w.scope !== "default-display" || w.rootStatus !== "available" || w.inputFocused || !nodes.some(n => n.windowRef === w && n.parent === null))) return false;
  const homes = windows.filter(w => w.type === 1 && w.rootPackage === launcher && w.title === "module-widget" && JSON.stringify(w.bounds) === "[0,0,1920,720]");
  if (homes.length !== 1 || windows.some(w => w !== homes[0] && w.active)) return false;
  return nodes.some(n => n.windowRef === homes[0] && n.parent === null && n.packageName === launcher && n.visibleToUser === true);
}

/** OEM Settings may restart directly on its default Network tab after removal. */
export function settingsRestartReturnedDefaultTab(id: string, nodes: UiNodes, proof?: ToggleRestartProof): boolean {
  const settings = "com.qinggan.app.setting";
  if (!proof || proof.mode !== "restart" || !proof.beforeBoot || proof.beforeBoot !== proof.afterBoot || !proof.targets.includes(settings) || !agentToggleReady(proof.beforeResident, proof.currentResident, id, false)) return false;
  const hosts = proof.targets.filter(process => [settings, "com.qinggan.app.launcher"].includes(process) && proof.beforePids[process]);
  if (!hosts.includes(settings) || hosts.some(process =>
    !proof.beforeResident.injections.some((row: any) => row.id === id && row.process === process && row.state === "active") ||
    !/^[1-9][0-9]*$/.test(proof.beforePids[process]) || !/^[1-9][0-9]*$/.test(proof.afterPids[process] ?? "") || proof.beforePids[process] === proof.afterPids[process])) return false;
  const focused = nodes.filter(node => focusedApplication(node));
  if (!focused.length || focused.some(node => node.packageName !== settings || node.windowRef?.title !== "Settings" || node.windowRef.rootPackage !== settings || node.windowRef.type !== 1 || !node.windowRef.active || !node.windowRef.inputFocused)) return false;
  const tabs = focused.filter(node => node.checkable && node.clickable);
  const voboost = tabs.filter(node => node.text === "Voboost");
  const selected = tabs.filter(node => node.parent === voboost[0]?.parent && node.checked);
  return voboost.length === 1 && !voboost[0].checked && selected.length === 1 && selected[0].text === "Network";
}

/** Preserve the delivered toggle; only fixed Settings navigation may recover Home. */
export async function recoverToggleHome(id: string, nodes: UiNodes, proof: ToggleRestartProof | undefined, snapshot: () => Promise<UiNodes>, sameBoot: () => Promise<void>, openSettings: () => Promise<void>, now = () => performance.now(), deadline = Infinity): Promise<UiNodes> {
  if (proof) nodes = await waitToggleFocus(nodes, snapshot, sameBoot, now, deadline, n => launcherRestartUnfocusedHome(id, n, proof));
  if (launcherRestartUnfocusedHome(id, nodes, proof) || launcherToggleReturnedHome(id, nodes, proof) || settingsRestartReturnedDefaultTab(id, nodes, proof)) {
    if (now() >= deadline) throw new Error("agent toggle restart focus deadline exhausted");
    await sameBoot();
    if (now() >= deadline) throw new Error("agent toggle restart focus deadline exhausted");
    await openSettings();
    nodes = await snapshot();
  }
  return nodes;
}

/** Read-only settling after independently confirmed host replacement. */
export async function waitToggleFocus(nodes: UiNode[], snapshot: () => Promise<UiNode[]>, sameBoot: () => Promise<void>, now = () => performance.now(), deadline = now() + 15000, navigationReady: (nodes: UiNodes) => boolean = () => false): Promise<UiNodes> {
  for (let reads = 0; reads < 6 && !nodes.some(focusedApplication) && !navigationReady(nodes); reads++) {
    if (now() >= deadline) throw new Error("agent toggle restart focus deadline exhausted");
    await sameBoot();
    nodes = await snapshot();
  }
  if (now() >= deadline) throw new Error("agent toggle restart focus deadline exhausted");
  if (!nodes.some(focusedApplication) && !navigationReady(nodes)) throw new Error("agent toggle restart focused application unavailable");
  return nodes;
}

const MAX_CAPTURE = 8 * 1024 * 1024;
const SETTINGS = "com.qinggan.app.setting";
/** Owned selected-tab authority; foreground drift is an error, never recovery evidence. */
export function settingsTab(nodes: UiNode[]): UiNode {
  const tab = uniqueNode(nodes, (node) => node.packageName === SETTINGS && focusedApplication(node) && node.text === "Voboost" && node.clickable);
  const window = tab.windowRef;
  if (!window || window.rootPackage !== SETTINGS || !window.active || !window.inputFocused || window.type !== 1)
    throw new Error("focused Settings application window differs");
  return tab;
}
/** Use only the existing exact OEM dialog contract for vendor-owned FEV focus. */
export async function confirmOemDialog(ui: Pick<InstallerUi, "tap">, vehicle: Vehicle, nodes: UiNode[]): Promise<void> {
  const owner = "com.qinggan.app.vehiclesetting";
  const vendor = nodes.filter(node => node.packageName === owner && node.resource === `${owner}:id/disclaimer_agree` && node.windowRef?.type === -1);
  if (vehicle === "fev" && vendor.length) {
    await ui.tap(oemForcedEvAgree(nodes));
    return;
  }
  const confirm = nodes.filter(node => node.packageName === owner && focusedApplication(node) && node.resource === `${owner}:id/disclaimer_agree` && node.clickable);
  if (confirm.length === 1) await ui.tap(confirm[0]);
}

export function selectedSettingsTab(nodes: UiNode[]): boolean {
  try { return settingsTab(nodes).checked; } catch { return false; }
}
export type InstallAcceptance = { schemaVersion: 1; transactionId: string; manifestSha256: string; attemptToken: string };
export function installAcceptanceArmed(text: string, manifestSha256: string): InstallAcceptance | null {
  const lines = text.split(/\r?\n/).filter(line => line.startsWith("install-acceptance-armed "));
  if (!lines.length) return null;
  if (lines.length !== 1) throw new Error("install acceptance armed identity duplicated");
  const row = JSON.parse(lines[0].slice("install-acceptance-armed ".length));
  exactKeys(row, ["schemaVersion", "transactionId", "manifestSha256", "attemptToken"], "install acceptance armed");
  if (row.schemaVersion !== 1 || row.transactionId !== manifestSha256 || row.manifestSha256 !== manifestSha256 || ![row.transactionId, row.manifestSha256, row.attemptToken].every(value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value)))
    throw new Error("install acceptance armed identity differs");
  return row;
}
type InstallAcceptanceRequest = InstallAcceptance & { bootId: string; rebootCount: 1; decision: "status" | "accept" | "reject" };
export function installAcceptanceAction(request: InstallAcceptanceRequest) {
  return { action: "install-acceptance" as const, transactionId: request.transactionId,
    manifestSha256: request.manifestSha256, attemptToken: request.attemptToken,
    bootId: request.bootId, rebootCount: request.rebootCount, decision: request.decision };
}
export function installAcceptanceReceipt(body: unknown, request: InstallAcceptance & { bootId: string; rebootCount: 1; decision: "status" | "accept" | "reject" }) {
  if (typeof body !== "string") throw new Error("install acceptance receipt body missing");
  const row = JSON.parse(body);
  exactKeys(row, [...Object.keys(request), "state", "expiresMonotonicMs"], "install acceptance receipt");
  if (Object.entries(request).some(([key, value]) => row[key] !== value) || !["waiting", "accepted", "rejected"].includes(row.state) || typeof row.expiresMonotonicMs !== "string" || !/^[1-9][0-9]*$/.test(row.expiresMonotonicMs) || BigInt(row.expiresMonotonicMs) > 18446744073709551615n)
    throw new Error("install acceptance receipt identity differs");
  return row;
}
export async function rejectFailedInstall(
  armed: InstallAcceptance,
  before: string,
  observe: () => Promise<{ boot: string; bootCompleted: boolean; rebootCount: number | null }>,
  call: (request: ReturnType<typeof installAcceptanceAction>) => Promise<{ body?: unknown }>,
): Promise<void> {
  // A failed UI observation never authorizes acceptance. Acquire a separate,
  // current boot identity only to terminate the exact pending transaction.
  const current = await observe();
  if (!uuid(current.boot) || current.boot === before || current.rebootCount !== 1)
    throw new Error("failed install rejection lacks current intermediate boot");
  const status = { ...armed, bootId: current.boot, rebootCount: 1 as const, decision: "status" as const };
  const waiting = installAcceptanceReceipt((await call(installAcceptanceAction(status))).body, status);
  if (waiting.state !== "waiting") throw new Error("failed install rejection is not waiting");
  const request = { ...status, decision: "reject" as const };
  const rejected = installAcceptanceReceipt((await call(installAcceptanceAction(request))).body, request);
  if (rejected.state !== "rejected" || rejected.expiresMonotonicMs !== waiting.expiresMonotonicMs)
    throw new Error("failed install rejection receipt differs");
}

const STABLE = [
  "settings",
  "font-cyrillic",
  "keyboard-en",
  "keyboard-ru",
  "launcher-apps",
  "vehicle-drive-forced-ev",
  "vehicle-drive-pedestrian-warning",
];
type Language = "en" | "ru" | "zh";
class Unavailable extends Error {}
/** CAN/root state belongs to SystemService; the FEV UI target only records intent. */
export function vehicleRootTarget(injections: any[], catalog: any[], vehicle: Vehicle): any {
  const process = "com.qinggan.systemservice";
  const targets = catalog.find((agent: any) => agent.id === VEHICLE[vehicle])?.targets;
  if (!targets?.some((target: any) => target.process === process)) throw new Unavailable("signed vehicle catalog does not include root state owner");
  const active = injections.filter((row: any) => row.id === VEHICLE[vehicle] && row.state === "active" && row.process === process);
  if (active.length !== 1) throw new Unavailable("independent unique active signed vehicle root target missing");
  return active[0];
}
export class ObservationPending extends Error {}
export const offlineObservation = (result: ProcessResult) => {
  const diagnostic = installerDiagnostic(result);
  return !!diagnostic && diagnostic.stdoutEmpty && result.exitCode !== null && result.exitCode !== 0 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && /device[^\n]*(?:offline|not found|unavailable|not connected)|no devices|connection (?:reset|closed)|transport[^\n]*closed|(?:^|\n)(?:Emulator acceptance failed: (?:emulator operation failed \(Some\([0-9]+\)\)|emulator capture failed): )?error: closed\s*(?:\n|$)/i.test(diagnostic.stderr);
};
export class UiWarmupDeadline extends Error {}
export function uiWarmupDeadline(result: ProcessResult, serial?: string): boolean {
  const diagnostic = installerDiagnostic(result, serial);
  if (!diagnostic || (!diagnostic.stdoutEmpty && !diagnostic.connection)) return false;
  result = { ...result, stdout: "", stderr: diagnostic.stderr };
  const connectionOnly = diagnostic.connection;

  // Coordinator expiry can win the race with the helper exception/exit.
  // Accept only the complete known empty read-only envelope, never generic 74.
  const coordinatorRows = result.stderr.endsWith("\\n\n") ? result.stderr.slice(0, -3).split("\\n") : [];
  const coordinatorStats = "syscall=none errno=none windows=[0-9]+ window-reads=[0-9]+ nodes=[0-9]+ pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=[0-9]+ child-ms=[0-9]+";
  const coordinatorGrammar = [
    /^Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some\(74\) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=deadline exit=74 helper_exit=none xml_type=missing xml_bytes=0$/,
    /^ui-observation service phase=(?:initial present=true flags=65554|after-set present=true flags=65618) capabilities=15$/,
    /^ui-observation stage=active-root-before-windows reason=active-root-waiting kind=none syscall=none errno=none windows=0 window-reads=0 nodes=0 pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=0 child-ms=0$/,
    /^ui-observation active-root window-id=[0-9]+ reads=[1-9][0-9]*$/,
    /^ui-observation window id=[0-9]+ type=-?[0-9]+ layer=[0-9]+$/,
    new RegExp("^ui-observation stage=nodes-complete reason=timing kind=none " + coordinatorStats + "$"),
    new RegExp("^ui-observation stage=deadline reason=elapsed kind=none " + coordinatorStats + "$"),
    new RegExp("^ui-observation stage=metadata-after reason=deadline kind=java[.]lang[.]IllegalStateException " + coordinatorStats + "$"),
    /^ui-capture diagnostic=run\.log bytes=[1-9][0-9]* sha256=[a-f0-9]{64}$/,
    /^ui-capture timing startup_ms=[0-9]+ handshake_ms=[0-9]+ run_ms=0 exit_ms=0 cleanup_ms=[0-9]+ total_ms=[0-9]+$/,
  ];
  const coordinatorDeadline = coordinatorRows.length >= 10 &&
    coordinatorGrammar[0].test(coordinatorRows[0]) &&
    coordinatorRows.every((row) => coordinatorGrammar.some((pattern) => pattern.test(row))) &&
    [5, 6, 7, 8, 9].every((index) => coordinatorRows.filter((row) => coordinatorGrammar[index].test(row)).length === 1) &&
    coordinatorGrammar[9].test(coordinatorRows[coordinatorRows.length - 1]);
  if (coordinatorDeadline) return result.exitCode === 1 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && (result.stdout.trim() === "" || connectionOnly);

  return result.exitCode === 1 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && (result.stdout.trim() === "" || connectionOnly) &&
    /UI capture failed: reason=process-failed exit=Some\(72\) bytes=0 /.test(result.stderr) &&
    (/ui-observation stage=(?:nodes|metadata(?:-before|-after)?) reason=deadline kind=java\.lang\.IllegalStateException/.test(result.stderr) ||
      /ui-observation stage=deadline reason=elapsed kind=none syscall=none errno=none windows=[0-9]+ window-reads=[0-9]+ nodes=[0-9]+ pending-ipc=(?:none|get-child|refresh-after) pending-ipc-ms=[0-9]+ refresh-calls=[0-9]+ refresh-ms=[0-9]+ child-calls=[0-9]+ child-ms=[0-9]+(?:\\n|\r?\n|$)/.test(result.stderr)) &&
    !/permission|denied|foreign|identity|owner|malformed|offline|transport/i.test(result.stderr);
}
export function uiTraversalInconsistent(result: ProcessResult, serial?: string): boolean {
  const diagnostic = installerDiagnostic(result, serial);
  if (!diagnostic || (!diagnostic.stdoutEmpty && !diagnostic.connection)) return false;
  result = { ...result, stdout: "", stderr: diagnostic.stderr };
  // Accept only the complete known single-window helper failure envelope.
  // Extra diagnostics or contradictory XML metadata remain terminal.
  const rows = result.stderr.endsWith("\\n\n") ? result.stderr.slice(0, -3).split("\\n") : [];
  const grammar = [
    /^Emulator acceptance failed: UI capture failed: reason=process-failed exit=Some\(72\) bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 utf8=valid xml_prefix=false hierarchy_suffix=false; stdout=; stderr=ui-capture stage=helper exit=72 helper_exit=1 xml_type=missing xml_bytes=0$/,
    /^ui-observation service phase=initial present=true flags=65554 capabilities=15$/,
    /^ui-observation active-root window-id=[0-9]+ reads=[1-9][0-9]*$/,
    /^ui-observation service phase=after-set present=true flags=65618 capabilities=15$/,
    /^ui-observation window id=[0-9]+ type=1 layer=0$/,
    /^ui-observation stage=nodes reason=inconsistent kind=java\.lang\.IllegalStateException syscall=none errno=none windows=1 window-reads=1 nodes=[1-9][0-9]* pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=[0-9]+ child-ms=[0-9]+$/,
    /^ui-capture diagnostic=run\.log bytes=[1-9][0-9]* sha256=[a-f0-9]{64}$/,
    /^ui-capture diagnostic=native\.log bytes=[1-9][0-9]* sha256=[a-f0-9]{64}$/,
    /^ui-capture native-window pid=[1-9][0-9]* start=[0-9]+\.[0-9]+ end=[0-9]+\.[0-9]+ reader_exit=0$/,
    /^ui-capture timing startup_ms=[0-9]+ handshake_ms=[0-9]+ run_ms=[0-9]+ exit_ms=[0-9]+ cleanup_ms=[0-9]+ total_ms=[0-9]+$/,
  ];
  if (rows.length !== grammar.length || grammar.some((pattern, index) => !pattern.test(rows[index]))) return false;
  if (/window-id=([0-9]+)/.exec(rows[2])![1] !== /window id=([0-9]+)/.exec(rows[4])![1]) return false;
  const connectionOnly = diagnostic.connection;

  return result.exitCode === 1 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && (result.stdout.trim() === "" || connectionOnly) &&
    /UI capture failed: reason=process-failed exit=Some\(72\) bytes=0 /.test(result.stderr) &&
    /ui-observation stage=nodes reason=inconsistent kind=java\.lang\.IllegalStateException syscall=none errno=none windows=[0-9]+ window-reads=[0-9]+ nodes=[0-9]+ pending-ipc=none pending-ipc-ms=0 refresh-calls=0 refresh-ms=0 child-calls=[0-9]+ child-ms=[0-9]+(?:\\n|\r?\n|$)/.test(result.stderr) &&
    !/permission|denied|foreign|identity|owner|malformed|offline|transport/i.test(result.stderr);
}
export async function captureFirstBoot(capture: (attempt: number) => Promise<void>, guard: () => Promise<void>, retryAllowed: () => boolean): Promise<void> {
  for (let attempt = 1; attempt <= 2; attempt++) {
    await guard();
    try {
      await capture(attempt);
      await guard();
      return;
    } catch (error) {
      if (!(error instanceof UiWarmupDeadline) || attempt === 2 || !retryAllowed()) throw error;
    }
  }
}
/** Retry only a read-only snapshot deadline; mutations and unknown failures stay terminal. */
export async function snapshotReadback(
  action: string, invoke: (remainingMs: number) => Promise<ProcessResult>,
  guard: (first: boolean) => Promise<void>, serial: string, now: () => number = () => performance.now(),
  deadline: number = now() + 180000,
): Promise<ProcessResult> {
  if (action !== "snapshot") return invoke(240000);
  const remaining = () => {
    const ms = deadline - now();
    if (ms <= 0) throw new Error("snapshot readback budget exhausted");
    return ms;
  };
  for (let attempt = 1; attempt <= 2; attempt++) {
    remaining();
    await guard(attempt === 1);
    const result = await invoke(remaining());
    if ((uiWarmupDeadline(result, serial) || uiTraversalInconsistent(result, serial)) && attempt === 1 && remaining() > 45000) continue;
    // A failed helper never supplies nodes, and a successful observation must
    // remain bound to the original boot, Enforcing state and runtime owner.
    await guard(false);
    remaining();
    return result;
  }
  throw new Error("snapshot readback attempts exhausted");
}
export const installerRootPending = (result: ProcessResult, active: boolean, action: string) => {
  const diagnostic = installerDiagnostic(result);
  return !!diagnostic && (diagnostic.stdoutEmpty || diagnostic.connection) && active && action === "boot-observation" && result.exitCode === 1 && !result.timedOut && !result.signal && !result.overflow && !result.retained?.overflow && diagnostic.stderr === "boot-observation failure reason=guard-root remote_exit=73 stdout_bytes=0 stderr_bytes=36\nEmulator acceptance failed: boot observation guard rejected: root\n";
};
// Exact terminal ADB transport loss is pending only inside the authorized
// installer reboot monitor. It is never a receipt or a successful boot.
export function installerClosedPending(result: ProcessResult, action: string, serial: string, activeReboot: boolean, deadline: number | null, now: number): boolean {
  if (!activeReboot || action !== "boot-observation" || deadline === null || now >= deadline || result.exitCode !== 1 || result.timedOut || result.signal || result.overflow || result.retained?.overflow) return false;
  const diagnostic = installerDiagnostic(result, serial);
  if (!diagnostic || (!diagnostic.stdoutEmpty && !diagnostic.connection)) return false;
  result = { ...result, stderr: diagnostic.stderr };
  if (result.stderr !== "Emulator acceptance failed: boot-observation process failed: exit=Some(1) stdout_bytes=0 stdout_kind=empty stderr_bytes=14 stdout_sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 stderr_sha256=8f0acf19b9cbb9670c5e483a0fd471ca6603474e6fc4558565f14ba5cefe4c57 stderr=error: closed\n") return false;
  // These are the source-owned connection logs emitted outside the empty
  // binary observer stdout. No JSON, partial receipt or unknown log is allowed.
  return true;
}
/** Read-only retry inside a native reboot readiness lease; never repeats reboot. */
export async function nativeBootReadback<T>(read: () => Promise<T>, ownership: () => Promise<void>, pause: () => Promise<void>, deadline: number, now: () => number = () => performance.now()): Promise<T> {
  for (;;) {
    if (now() >= deadline) throw new Unavailable("native boot readback deadline expired");
    await ownership();
    try { return await read(); }
    catch (error) {
      if (!(error instanceof ObservationPending)) throw error;
      await ownership();
      if (now() >= deadline) throw new Unavailable("native boot readback deadline expired");
      await pause();
    }
  }
}
/** A changed boot receipt does not imply Android has completed that boot. */
export async function nativeBootReady<T extends { boot: string; bootCompleted: boolean }>(read: () => Promise<T>, before: string | null, after: string | undefined, ownership: () => Promise<void>, pause: () => Promise<void>, deadline: number, now: () => number = () => performance.now()): Promise<T> {
  let expected = after;
  return nativeBootReadback(async () => {
    const value = await read();
    if (!before || value.boot === before || (expected !== undefined && value.boot !== expected)) throw new Unavailable("native readiness boot identity differs");
    expected ??= value.boot;
    if (!value.bootCompleted) throw new ObservationPending("native boot completion pending");
    return value;
  }, ownership, pause, deadline, now);
}
export async function installerBootObservation<T extends { boot: string; bootCompleted: boolean; rebootCount: number | null }>(
  number: number,
  before: string | null,
  read: () => Promise<T>,
  pause: () => Promise<void>,
  observed: (value: T) => void = () => {},
): Promise<T> {
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const value = await read();
      if (value.boot !== before && value.rebootCount === number) observed(value);
      if (value.rebootCount !== null && value.rebootCount > number) throw new Unavailable("expected intermediate boot window was missed; a later boot cannot substitute");
      if (value.boot !== before && value.bootCompleted && value.rebootCount === number) return value;
    } catch (error) { if (!(error instanceof ObservationPending)) throw error; }
    await pause();
  }
  throw new Unavailable("expected intermediate boot never exposed new identity, completion, Enforcing and exact durable reboot count");
}
export function exactKeys(
  value: unknown,
  keys: string[],
  label: string,
): asserts value is Record<string, any> {
  if (
    !value ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    JSON.stringify(Object.keys(value).sort()) !== JSON.stringify([...keys].sort())
  )
    throw new Error(`${label}: unknown or missing fields`);
}
function regular(path: string, directory = false): void {
  if (!isAbsolute(path)) throw new Error("path must be absolute");
  const st = lstatSync(path);
  if (st.isSymbolicLink() || (directory ? !st.isDirectory() : !st.isFile()))
    throw new Error("unsafe file identity");
}
function digest(bytes: Buffer | string): string {
  return createHash("sha256").update(bytes).digest("hex");
}
function inventory(root: string): unknown[] {
  regular(root, true);
  const rows: any[] = [];
  const visit = (path: string) => {
    const st = lstatSync(path);
    if (st.isSymbolicLink()) throw new Error("inventory rejects symlinks");
    if (st.isDirectory()) for (const name of readdirSync(path).sort()) visit(join(path, name));
    else {
      regular(path);
      rows.push({ path: relative(root, path), size: st.size, sha256: digest(readFileSync(path)) });
    }
  };
  visit(root);
  return rows;
}
function packageInventory(c: GateConfig): string {
  return digest(
    JSON.stringify(inventory(join(c.package, "Voboost Installer.app"))) +
      readlinkSync(join(c.package, "voboost-installer")),
  );
}
export function validateConfig(raw: unknown): GateConfig {
  exactKeys(
    raw,
    [
      "workspaceRoot",
      ...(raw && typeof raw === "object" && "i18nRuntimeEvidence" in raw ? ["i18nRuntimeEvidence"] : []),
      ...(raw && typeof raw === "object" && "localArtifact" in raw ? ["localArtifact"] : []),
      "voyemuRoot",
      "voyemuProfile",
      ...(raw && typeof raw === "object" && "nativeSession" in raw ? ["nativeSession"] : []),
      ...(raw && typeof raw === "object" && "reviewedKeyboardMaps" in raw ? ["reviewedKeyboardMaps"] : []),
      "closure",
      "package",
      "dmg",
      "fixture",
      "output",
    ],
    "config",
  );
  const c = raw as GateConfig;
  for (const key of ["workspaceRoot", "voyemuRoot", "closure", "package"] as const)
    regular(c[key], true);
  regular(c.dmg);
  if (c.fixture !== null) regular(c.fixture);
if (c.reviewedKeyboardMaps !== undefined) regular(c.reviewedKeyboardMaps);
  if (c.i18nRuntimeEvidence !== undefined) {
    exactKeys(c.i18nRuntimeEvidence,["runDirectory","resumeDirectory"],"i18n runtime evidence");
    if (!c.nativeSession) throw new Error("i18n runtime evidence requires owned native session");
    regular(c.i18nRuntimeEvidence.runDirectory,true); regular(c.i18nRuntimeEvidence.resumeDirectory,true);
  }
  if (!/^[a-z0-9][a-z0-9._-]{0,63}$/.test(c.voyemuProfile)) throw new Error("unsafe profile");
  if (c.nativeSession) nativeSessionFromConfig(c);
  else {
  regular(join(c.voyemuRoot, "src/cli.ts"));
  const vp = JSON.parse(readFileSync(join(c.voyemuRoot, "package.json"), "utf8"));
  if (vp.name !== "voyemu" || vp.bin?.voyemu !== "src/cli.ts")
    throw new Error("foreign Voyemu package");
  }
  const link = join(c.package, "voboost-installer");
  if (
    !lstatSync(link).isSymbolicLink() ||
    readlinkSync(link) !== "Voboost Installer.app/Contents/MacOS/voboost-installer"
  )
    throw new Error("invalid packaged CLI link");
  if (
    JSON.stringify(readdirSync(c.package).sort()) !==
    JSON.stringify(["Voboost Installer.app", "voboost-installer"])
  )
    throw new Error("package root differs");
  regular(realpathSync(link));
  if (c.fixture !== null && (basename(c.fixture) !== "voboost-launcher-fixture.apk" || realpathSync(c.fixture) !== realpathSync(join(c.workspaceRoot, "voboost", plan.fixture.apk))))
    throw new Error("fixture must be the root-built first-party test APK");
  if (!isAbsolute(c.output) || existsSync(c.output) || basename(c.output).length > 100)
    throw new Error("output must be new absolute path");
  const output = join(realpathSync(dirname(c.output)), basename(c.output));
  for (const input of [c.voyemuRoot, c.closure, c.package])
    if (output === realpathSync(input) || output.startsWith(realpathSync(input) + sep))
      throw new Error("output overlaps inputs");
  if (
    output.startsWith(realpathSync(c.workspaceRoot) + sep) &&
    !output.startsWith(join(realpathSync(c.workspaceRoot), "voboost/build") + sep) &&
    !output.startsWith(join(realpathSync(c.workspaceRoot), "plans") + sep)
  )
    throw new Error("output inside source");
  if (!c.nativeSession) prepared(c);
  return c;
}
/** Private receipt is read only; ownership token never enters public evidence. */
export function nativeSessionFromConfig(c: GateConfig): NativeConfig {
  if (!c.nativeSession) throw new Error("native session config absent");
  exactKeys(c.nativeSession, ["executable", "receiptFile"], "native config");
  regular(c.nativeSession.executable); regular(c.nativeSession.receiptFile);
  if ((lstatSync(c.nativeSession.receiptFile).mode & 0o077) !== 0) throw new Error("native receipt must be private");
  const receipt = JSON.parse(readFileSync(c.nativeSession.receiptFile, "utf8"));
  if (receipt.schema !== 2 || receipt.ok !== true || receipt.result?.profile !== c.voyemuProfile) throw new Error("native start receipt differs");
  const session = receipt.result.session;
  if (!session || ![session.id, session.ownership_id].every(v => typeof v === "string" && /^[a-f0-9]{32}$/.test(v))) throw new Error("native lease differs");
  let selected: Pick<NativeConfig,"machine" | "machineManifestSha256"> = {};
  const identity = receipt.result;
  if (["machine","machine_seed","machine_manifest_sha256"].some(key => identity[key] !== undefined)) {
    if (typeof identity.machine !== "string" || !/^[a-z0-9][a-z0-9._-]{0,127}$/.test(identity.machine) || !/^[a-f0-9]{64}$/.test(identity.machine_manifest_sha256 ?? "")) throw new Error("native selected machine receipt differs");
    const root = realpathSync(c.voyemuRoot), seed = join(root,"machines",identity.machine), manifest = join(seed,"runtime.json");
    regular(seed,true); regular(manifest);
    if (root !== c.voyemuRoot || realpathSync(seed) !== seed || identity.machine_seed !== seed) throw new Error("native selected machine seed differs");
    const bytes = readFileSync(manifest);
    if (digest(bytes) !== identity.machine_manifest_sha256 || JSON.parse(bytes.toString()).profile !== c.voyemuProfile) throw new Error("native selected machine manifest differs");
    selected = {machine:identity.machine,machineManifestSha256:identity.machine_manifest_sha256};
  }
  return { executable: c.nativeSession.executable, root: c.voyemuRoot, profile: c.voyemuProfile, session, ...selected };
}
export function prepared(c: GateConfig): { file: string; sha256: string; checks: any[] } {
  const file = join(c.voyemuRoot, "machines", c.voyemuProfile, "machine.json");
  regular(file);
  const bytes = readFileSync(file);
  const m = JSON.parse(bytes.toString());
  if (
    m.schema !== 1 ||
    m.profile !== c.voyemuProfile ||
    !m.config ||
    !Array.isArray(m.installed) ||
    !Array.isArray(m.checks) ||
    !m.checks.length
  )
    throw new Error("unverified prepared machine");
  const ids = m.checks.map((x: any) => x.id);
  if (
    new Set(ids).size !== ids.length ||
    m.checks.some((x: any) => typeof x.id !== "string" || !x.id || x.passed !== true) ||
    !m.checks.some((x: any) => x.id === "selinux" && x.expected === "Enforcing")
  )
    throw new Error("prepared check inventory differs");
  // Cross-check IDs against the prepared configuration, not only a mutable check count.
  const required = ["boot", "api", "abi", "selinux"];
  for (const i of m.installed) {
    if (i.mutable) continue;
    required.push(`artifact:${i.artifact}:${i.target}`);
    if (i.mode) required.push(`mode:${i.target}`);
  }
  for (const [space, values] of Object.entries(m.config.configure?.settings ?? {}))
    for (const key of Object.keys(values as object)) required.push(`setting:${space}:${key}`);
  for (const [kind, key] of [
    ["property", "properties"],
    ["boot-property", "bootProperties"],
  ] as const)
    for (const k of Object.keys(m.config.configure?.[key] ?? {})) required.push(`${kind}:${k}`);
  for (const [kind, key] of [
    ["disabled", "disabled"],
    ["uninstalled", "uninstalled"],
    ["overlay", "overlays"],
  ] as const)
    for (const k of m.config.configure?.[key] ?? []) required.push(`${kind}:${k}`);
  if (m.config.configure?.home) required.push("home");
  for (const b of m.config.verify?.bindings ?? []) required.push(`binding:${b}`);
  for (const s of m.config.verify?.services ?? []) required.push(`service:${s}`);
  for (const a of m.config.verify?.activities ?? [])
    required.push(`activity:${a.component}:${a.display}`);
  m.config.displays.forEach((_: unknown, i: number) => required.push(`display:${i}`));
  const windows = m.config.verify?.windows;
  for (const [title, display] of Object.entries({ ...windows?.required, ...windows?.optional }))
    required.push(`routing:${display === 1 ? "second" : "main"}:${title}`);
  if (JSON.stringify([...required].sort()) !== JSON.stringify([...ids].sort()))
    throw new Error("prepared IDs do not match profile inputs");
  return { file, sha256: digest(bytes), checks: m.checks };
}
export function voyemuInvocation(c: GateConfig, command: "status" | "verify"): Invocation {
  if (c.nativeSession) throw new Error("legacy Voyemu invocation forbidden in native mode");
  return {
    executable: process.execPath,
    args: [
      join(c.voyemuRoot, "src/cli.ts"),
      "--root",
      c.voyemuRoot,
      "--json",
      command,
      c.voyemuProfile,
    ],
    timeoutMs: 600000,
    voyemu: true,
  };
}
function envelope(
  result: ProcessResult,
  profile: string,
  command: string,
  requirePass = true,
): any {
  if (
    (result.exitCode !== 0 && requirePass) ||
    result.timedOut ||
    result.signal ||
    result.overflow ||
    result.retained?.overflow
  )
    throw new Error("Voyemu process failed");
  const v = JSON.parse(result.stdout);
  exactKeys(
    v,
    ["schema", "command", "profile", "ok", "changed", "stage", "code", "message", "report"],
    "Voyemu envelope",
  );
  if (
    v.schema !== 1 ||
    v.command !== command ||
    v.stage !== command ||
    v.profile !== profile ||
    typeof v.changed !== "boolean" ||
    typeof v.ok !== "boolean"
  )
    throw new Error("Voyemu envelope identity differs");
  if (requirePass && (v.ok !== true || v.code !== "OK")) throw new Error("Voyemu verdict failed");
  return v.report;
}
export function parseVoyemuStatus(result: ProcessResult, profile: string): VoyemuIdentity {
  const report = envelope(result, profile, "status");
  exactKeys(report, ["profiles"], "status");
  exactKeys(report.profiles, [profile], "profiles");
  const s = report.profiles[profile];
  exactKeys(s, ["prepared", "running", "port", "pid"], "running status");
  if (
    s.prepared !== true ||
    s.running !== true ||
    !Number.isInteger(s.pid) ||
    s.pid < 1 ||
    !Number.isInteger(s.port) ||
    s.port < 1024 ||
    s.port > 65534 ||
    s.port % 2
  )
    throw new Error("prepared running leased runtime required");
  return { pid: s.pid, port: s.port };
}
export function verifyVoyemu(result: ProcessResult, profile: string, expected: any[]): void {
  const report = envelope(result, profile, "verify");
  exactKeys(report, ["checks"], "verification");
  if (!Array.isArray(report.checks)) throw new Error("checks missing");
  const ids = report.checks.map((x: any) => x.id);
  if (
    new Set(ids).size !== ids.length ||
    JSON.stringify([...ids].sort()) !== JSON.stringify(expected.map((x) => x.id).sort())
  )
    throw new Error("partial, duplicate or unexpected checks");
  for (const row of expected) {
    const actual = report.checks.find((x: any) => x.id === row.id);
    exactKeys(
      actual,
      Object.hasOwn(actual, "message")
        ? ["id", "passed", "expected", "actual", "message"]
        : ["id", "passed", "expected", "actual"],
      "Voyemu check",
    );
    if (
      typeof actual.id !== "string" ||
      typeof actual.passed !== "boolean" ||
      (Object.hasOwn(actual, "message") &&
        actual.message !== null &&
        typeof actual.message !== "string")
    )
      throw new Error("invalid check shape");
    if (
      actual.passed !== true ||
      JSON.stringify(actual.expected) !== JSON.stringify(row.expected ?? null)
    )
      throw new Error(`check failed or expected differs: ${row.id}`);
    const equal =
      /^(boot$|api$|abi$|selinux$|artifact:|mode:|setting:|property:|boot-property:|disabled:|uninstalled:|home$|overlay:|binding:)/.test(
        row.id,
      );
    if (equal && JSON.stringify(actual.actual) !== JSON.stringify(row.expected ?? null))
      throw new Error(`source scalar observation differs: ${row.id}`);
  }
}
// A failed snapshot can describe an OEM second screen that is still starting.
// This classifies a retry only; acceptance still requires an untouched full pass.
export function secondScreenStarting(result: ProcessResult, profile: string, expected: any[]): boolean {
  try {
    if (result.exitCode !== 6 || expected.length !== 220) return false;
    const report = envelope(result, profile, "verify", false);
    const wire = JSON.parse(result.stdout);
    if (wire.ok !== false || wire.changed !== false || wire.code !== "VERIFY_FAILED") return false;
    exactKeys(report, ["checks"], "verification");
    const missing = ["SecondScreenNavigationBar", "SecondHiboardDelegate", "SecondVehicleHiboardBar"].map(title => `routing:second:${title}`);
    if (!Array.isArray(report.checks)) return false;
    const failed = report.checks.filter((row: any) => row.passed !== true);
    if (failed.length < 1 || failed.length > 3 || failed.some((row: any) => !missing.includes(row.id) || row.actual !== null || row.expected !== 1 || row.message !== `Window "${row.id.slice("routing:second:".length)}" not found in dumpsys window`)) return false;
    if (report.checks.some((row: any) => row.id?.startsWith("routing:") && row.passed === true && row.actual !== null && (!Array.isArray(row.actual) || row.actual.length === 0 || row.actual.some((display: any) => display !== row.expected)))) return false;
    const activity = report.checks.find((row: any) => row.id === "activity:com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.SecondMainActivity:1");
    if (activity?.passed !== true || typeof activity.actual !== "string" || !activity.actual.includes("Splash Screen com.qinggan.app.launcher") || !/mDisplayId=1\b/.test(activity.actual)) return false;
    const validated = { ...wire, ok: true, code: "OK", report: { checks: report.checks.map((row: any) => missing.includes(row.id) ? { ...row, passed: true, actual: [1] } : row) } };
    verifyVoyemu({ ...result, exitCode: 0, stdout: JSON.stringify(validated) }, profile, expected);
    return true;
  } catch { return false; }
}
export function jsonReceipt(stdout: string): Record<string, any> {
  const rows = stdout.split(/\r?\n/).filter((x) => x.startsWith("{"));
  if (rows.length !== 1) throw new Error("exactly one CLI JSON receipt required");
  const result = JSON.parse(rows[0]);
  if (!result || typeof result !== "object" || Array.isArray(result))
    throw new Error("invalid CLI receipt");
  return result;
}
function readNativeRequest(action:any): any {
  const request=action.artifacts.find((a:any)=>a.kind==="request" && a.path===action.nativeRequest);
  if(!request || action.args[0]!=="test" || action.args[1]!=="--root" || action.args[3]!=="--request") throw new Error("native request action missing");
  const bytes=readFileSync(request.path);
  if(digest(bytes)!==request.sha256) throw new Error("native request bytes differ");
  const row=JSON.parse(bytes.toString("utf8"));
  if(row.op!==action.nativeOperation) throw new Error("native action operation differs");
  return row;
}
/** Validate retained native semantic evidence independently of the live reader. */
export function nativeTreeAction(evidence: any, action: any): UiNodes {
  if (evidence.schemaVersion !== 4 || JSON.stringify(evidence.verificationContract) !== JSON.stringify(verificationContract) || action.nativeOperation !== "session.ui" || action.exitCode !== 0 || action.timedOut || action.signal || action.overflow) throw new Error("native tree action not successful");
  const request = readNativeRequest(action);
  if (request.session?.id !== evidence.runtime.sessionId || request.profile !== evidence.voyemuProfile || request.screen !== "primary" || "selector" in request) throw new Error("native tree request scope differs");
  const stdout = action.artifacts.find((a: any) => a.kind === "stdout");
  if (!stdout) throw new Error("native tree response absent");
  const bytes = readFileSync(stdout.path);
  if (digest(bytes) !== stdout.sha256) throw new Error("native tree retained response differs");
  const result = nativeResponse({ exitCode: action.exitCode, stdout: bytes.toString("utf8") }, request);
  if (action.context?.runtime?.sessionId !== evidence.runtime.sessionId || action.context?.runtime?.pid !== evidence.runtime.pid || action.context?.serial !== evidence.runtime.serial || action.context?.kit !== evidence.identity.package) throw new Error("native tree context differs");
  return nativeTree(result, { sessionId: evidence.runtime.sessionId, pid: evidence.runtime.pid, bootId: action.context.bootId, profile: evidence.voyemuProfile });
}
export function nativeCaptureAction(evidence:any,action:any):any {
  if(evidence.schemaVersion!==4 || JSON.stringify(evidence.verificationContract)!==JSON.stringify(verificationContract) || action.nativeOperation!=="session.screenshot" || action.exitCode!==0 || action.timedOut || action.signal || action.overflow) throw new Error("native capture action not successful");
  const request=readNativeRequest(action);
  if(request.session?.id!==evidence.runtime.sessionId) throw new Error("native capture request session differs");
  const stdout=action.artifacts.find((a:any)=>a.kind==="stdout"), bytes=readFileSync(stdout.path);
  if(digest(bytes)!==stdout.sha256) throw new Error("native capture response differs");
  const wire=JSON.parse(bytes.toString("utf8"));
  const result=nativeResponse({exitCode:action.exitCode,stdout:bytes.toString("utf8")},request);
  nativeCapture(result,request.session,evidence.runtime.pid,500,request.screens ?? (request.screen ? [request.screen] : ["primary","secondary"]),request.rect);
  const authority=action.nativeAuthority;
  if(!authority || authority.requestId!==wire.request_id || authority.operationId!==wire.operation_id || authority.sessionId!==evidence.runtime.sessionId || result.session_id!==authority.sessionId || request.session.id!==result.session_id || result.android_boot_id!==action.context?.bootId || action.context?.runtime?.sessionId!==authority.sessionId) throw new Error("native capture correlation differs");
  const retained=action.artifacts.find((a:any)=>a.path===authority.manifest), manifestBytes=readFileSync(authority.manifest);
  if(!retained || digest(manifestBytes)!==retained.sha256) throw new Error("retained native manifest differs");
  const manifest=JSON.parse(manifestBytes.toString("utf8"));
  for(const key of ["schema","session_id","android_pid","android_boot_id","screens","qnx_generation","capture_mode","accepted","frames_consistent","cluster_powered","qnx_frame_verified","frames"]) if(JSON.stringify(result[key])!==JSON.stringify(manifest[key])) throw new Error("retained native capture authority differs");
  for(const frame of manifest.frames) {
    if(!["cluster.png","primary.png","secondary.png"].includes(frame.file)) throw new Error("native frame path differs");
    const artifact=action.artifacts.find((a:any)=>a.path===join(dirname(authority.manifest),frame.file) && a.kind==="png");
    if(!artifact || artifact.sha256!==frame.sha256) throw new Error("retained native frame missing");
    const image=readFileSync(artifact.path); if(digest(image)!==frame.sha256) throw new Error("retained native frame bytes differ"); const decoded=validatePng(image,allowBlackFrame(result,frame)); if(frame.source==="android_sdk" && (decoded.width!==frame.width || decoded.height!==frame.height)) throw new Error("native frame raster dimensions differ");
  }
  const selected=manifest.frames.find((f:any)=>f.screen===authority.display);
  const alias=action.artifacts.find((a:any)=>a.kind==="png" && a.path===authority.output);
  if(!selected || !alias || alias.sha256!==selected.sha256 || digest(readFileSync(alias.path))!==selected.sha256) throw new Error("native selected surface alias differs");
  return manifest;
}
/** Current OEM card authority; numeric theme constants and skin guesses are not used. */
export function launcherThemeSnapshotProof(evidence: any, action: any, nodes: UiNodes, boot: string): {path:string;sha256:string} {
  if(!action || action.exitCode!==0 || action.timedOut || action.signal || action.overflow || action.context?.bootId!==boot) throw new Error("selected launcher theme snapshot action differs");
  const artifact=action.artifacts.find((a:any)=>a.kind==="stdout");
  if(!artifact) throw new Error("selected launcher theme snapshot artifact missing");
  let retained:UiNodes;
  if(action.nativeOperation==="session.ui") retained=nativeTreeAction(evidence,action);
  else {
    if(action.args[0]!=="--emulator-check") throw new Error("selected launcher theme snapshot operation differs");
    const request=action.artifacts.find((a:any)=>a.kind==="request"&&a.path===action.args[1]);
    if(!request || digest(readFileSync(request.path))!==request.sha256 || JSON.parse(readFileSync(request.path,"utf8")).action!=="snapshot") throw new Error("selected launcher theme snapshot request differs");
    const bytes=readFileSync(artifact.path);
    if(digest(bytes)!==artifact.sha256) throw new Error("selected launcher theme snapshot bytes differ");
    const receipt=jsonReceipt(bytes.toString("utf8"));
    if(receipt.schemaVersion!==1 || receipt.serial!==evidence.runtime.serial || typeof receipt.body!=="string") throw new Error("selected launcher theme snapshot receipt differs");
    retained=parseUi(receipt.body);
  }
  const list=(rows:UiNodes)=>uniqueNode(rows,n=>n.resource==="com.qinggan.app.setting:id/recyclerview_theme_pic"&&n.windowRef?.rootPackage==="com.qinggan.app.setting"&&n.windowRef.type===1&&n.windowRef.inputFocused);
  const live=list(nodes).windowRef!, replay=list(retained).windowRef!;
  if(live.id!==replay.id || live.frameSha256!==replay.frameSha256 || live.captureStart!==replay.captureStart || live.captureEnd!==replay.captureEnd || JSON.stringify(live.capture)!==JSON.stringify(replay.capture) || JSON.stringify(selectedLauncherTheme(nodes))!==JSON.stringify(selectedLauncherTheme(retained))) throw new Error("selected launcher theme snapshot authority differs");
  return {path:artifact.path,sha256:artifact.sha256};
}
export function selectedLauncherTheme(nodes: UiNode[]): {label:string;skin:string|null;style:"metal"|"color"} {
  const owner="com.qinggan.app.setting", id=(name:string)=>owner+":id/"+name;
  const owned=(n:UiNode)=>n.packageName===owner && n.enabled && n.windowRef?.rootPackage===owner && n.windowRef.type===1 && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootStatus==="available";
  const list=uniqueNode(nodes,n=>owned(n)&&n.resource===id("recyclerview_theme_pic"));
  const roots=nodes.filter(n=>owned(n)&&n.parent===nodes.indexOf(list)&&n.resource===id("rl_root")&&n.clickable);
  if(roots.length!==3) throw new Error("three native theme cards required");
  const cards=roots.map(root=>{
    const children=nodes.filter(n=>owned(n)&&n.parent===nodes.indexOf(root));
    const title=children.filter(n=>n.resource===id("tv_title")),skin=children.filter(n=>n.resource===id("skin_name"));
    if(title.length!==1 || !title[0].text || skin.length>1 || root.selected!==title[0].selected) throw new Error("native theme card identity differs");
    return {label:title[0].text,skin:skin[0]?.text??null,selected:root.selected};
  });
  if(new Set(cards.map(c=>c.label)).size!==3 || cards.filter(c=>c.selected).length!==1) throw new Error("unique selected native theme required");
  const selected=cards.find(c=>c.selected)!;
  const style=selected.label==="Classic"?"metal":selected.label==="Speedy Dash"?"color":null;
  if(!style) throw new Unavailable("selected native theme has no reviewed launcher style oracle");
  return {label:selected.label,skin:selected.skin,style};
}
export function launcherThemeRestoreAllowed(state: { activeOperation: unknown; installerActive: boolean; faultLive: boolean; monitorHealthy: boolean; leaseStatus: string }): boolean {
  return state.activeOperation === null && state.installerActive === false && state.faultLive === false && state.monitorHealthy === true && state.leaseStatus === "retained";
}
/** Validated cache pairs only: no bitmap or visible styling claim is inferred. */
export function launcherThemeRoundTrip(phases: { label: string; style: string; pairs: ReturnType<typeof launcherCache>; diskSources: string[] }[]): void {
  if (phases.length !== 3 || phases[0].label !== phases[2].label || phases[0].style !== phases[2].style || new Set(phases.slice(0,2).map(p=>p.style)).size !== 2 || phases.some(p => !["Classic","Speedy Dash"].includes(p.label) || p.style !== (p.label === "Classic" ? "metal" : "color") || p.pairs.length !== 2 || new Set(p.pairs.map(row=>row.screen)).size !== 2 || p.pairs.some(row=>![0,1].includes(row.screen)))) throw new Error("launcher theme roundtrip authority differs");
  for (const screen of [0,1]) {
    if (phases[2].diskSources.length !== 2 || phases[2].diskSources.some(source=>!["memory","disk"].includes(source))) throw new Error("restored theme did not reuse retained cached pixels");
    const [first, changed, restored] = phases.map(p => p.pairs.find(row=>row.screen===screen)!);
    if ([first,changed,restored].some(row=>row.boot!==first.boot || row.generationId!==first.generationId || row.catalogSha256!==first.catalogSha256 || row.source_identity!==first.source_identity)) throw new Error("launcher theme roundtrip crossed source, boot or signed identity");
    // Same-process theme restoration may reuse memory before disk. Cold-boot disk
    // admission remains independently enforced by launcherCache. Screen 0 changes
    // metal/color; screen 1 keeps color pixels and its cache identity.
    if ((screen === 0 && (first.cache_key===changed.cache_key || first.style_identity===changed.style_identity)) || first.cache_key!==restored.cache_key || first.style_identity!==restored.style_identity) throw new Error("launcher theme cache invalidation or restoration differs");
  }
}
/** A failed shared reboot monitor is diagnostic evidence, never another executed scenario. */
export function partialBootFailure(evidence: any, row: Case): boolean {
  if (row.number !== 26 || row.status !== "failed" || !row.reason || row.actions.length !== 0 || row.subchecks.length !== 1) return false;
  const check = row.subchecks[0];
  const failedBoots = evidence.boots.filter((boot: Boot) => boot.status === "failed");
  const artifacts = failedBoots.flatMap((boot: Boot) => boot.actions.flatMap((id) => evidence.actions.find((action: any) => action.id === id)?.artifacts.map((artifact: any) => artifact.path) ?? []));
  return failedBoots.some((boot: Boot) => boot.reason === row.reason) && check.id === "intermediate-boot-monitor" && check.status === "failed" && check.reason === row.reason && JSON.stringify(check.artifacts) === JSON.stringify(artifacts);
}
/** Missing IME is observable startup state, never permission to replay an editor tap. */
export function keyboardCanvasNode(nodes: UiNode[]): UiNode | null {
  const editors=nodes.filter(n=>n.packageName==="com.pateonavi.naviapp" && focusedApplication(n) && n.className==="android.widget.EditText" && n.focused && !n.password && n.enabled && n.visibleToUser);
  if(editors.length!==1) throw new Unavailable("unique focused editor unavailable while awaiting keyboard");
  const candidates=nodes.filter(n=>/^com\.qinggan\.app\.qgime(?:\.second)?:id\/alpha_view1$/.test(n.resource));
  if(!candidates.length) return null;
  const keyboards=candidates.filter(n=>n.windowRef?.type===2 && n.windowRef.rootPackage===n.packageName && n.enabled && n.visibleToUser && n.windowRef.capture===editors[0].windowRef?.capture && JSON.stringify(n.bounds)===JSON.stringify(n.rawBounds));
  if(candidates.length!==1 || keyboards.length!==1) throw new Unavailable("unique unclipped live keyboard canvas/editor unavailable");
  return keyboards[0];
}
export async function awaitKeyboardCanvas(snapshot:()=>Promise<UiNode[]>, deadline:number, now:()=>number=()=>performance.now()):Promise<UiNode> {
  while(now()<deadline) {
    const node=keyboardCanvasNode(await snapshot());
    if(now()>=deadline) break;
    if(node) return node;
  }
  throw new Unavailable("keyboard canvas did not appear before observation deadline");
}
/** Diagnostic pixels route canvas keys; they never establish visual acceptance. */
export function keyboardDiagnosticRequest(request: any, scope: any): boolean {
  return !!scope && request.op === "session.screenshot" && request.artifact_dir === scope.directory &&
    JSON.stringify(request.screens) === JSON.stringify(["primary"]) &&
    JSON.stringify(request.rect) === JSON.stringify(scope.rect);
}
export function keyboardDiagnosticEvidence(evidence: any, action: any): void {
  const proof = action.keyboardDiagnostic;
  // Normal full runs have no deferred-mode marker. Admit their canvas routing
  // only after enablement and with the complete closed case inventory.
  const normalFull = evidence.imageCapture === undefined && evidence.functionalMode === undefined &&
    evidence.regressionScenario === undefined && Array.isArray(evidence.cases) &&
    evidence.cases.length === EXECUTION_ORDER.length &&
    new Set(evidence.cases.map((row: Case) => row.number)).size === EXECUTION_ORDER.length &&
    evidence.cases.every((row: Case) => EXECUTION_ORDER.includes(row.number)) &&
    evidence.cases.some((row: Case) => row.number === 8 && row.status === "passed");
  if (!proof || ![9,10,11,19].includes(action.caseNumber) ||
      !(normalFull || evidence.functionalMode === "full27" || ["input","lifecycle-input"].includes(evidence.regressionScenario) || evidence.regressionScenario === "keyboards" && [10,11].includes(action.caseNumber))) throw new Error("keyboard diagnostic scope differs");
  const request = readNativeRequest(action), manifest = nativeCaptureAction(evidence, action);
  if (request.profile !== evidence.voyemuProfile || action.context?.kit !== evidence.identity.package || action.context?.serial !== evidence.runtime.serial || action.context?.runtime?.pid !== evidence.runtime.pid) throw new Error("keyboard diagnostic identity differs");
  const before = evidence.actions.find((a:any) => a.id === proof.before);
  const after = evidence.actions.find((a:any) => a.id === proof.after);
  if (!before || !after || before.caseNumber !== action.caseNumber || after.caseNumber !== action.caseNumber ||
      BigInt(before.finishedMonotonicNs) > BigInt(action.startedMonotonicNs) ||
      BigInt(action.finishedMonotonicNs) > BigInt(after.startedMonotonicNs)) throw new Error("keyboard diagnostic chronology differs");
  const canvas = (a:any) => {
    const nodes = nativeTreeAction(evidence,a);
    const editors = nodes.filter(n => n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) && n.className === "android.widget.EditText" && n.focused && !n.password && n.enabled && n.visibleToUser);
    const keyboards = nodes.filter(n => /^com\.qinggan\.app\.qgime(?:\.second)?:id\/alpha_view1$/.test(n.resource) && n.windowRef?.type === 2 && n.windowRef.rootPackage === n.packageName && n.enabled && n.visibleToUser && n.windowRef.capture === editors[0]?.windowRef?.capture && JSON.stringify(n.bounds) === JSON.stringify(n.rawBounds));
    if (editors.length !== 1 || keyboards.length !== 1 || a.context.bootId !== manifest.android_boot_id) throw new Error("keyboard diagnostic live canvas differs");
    const n = keyboards[0]; return {package:n.packageName,resource:n.resource,bounds:n.bounds,window:n.windowRef!.id};
  };
  const first = canvas(before), last = canvas(after), b = first.bounds;
  if (JSON.stringify(first) !== JSON.stringify(last) || !keyboardDiagnosticRequest(request,{directory:request.artifact_dir,rect:{x:b[0],y:b[1],width:b[2]-b[0],height:b[3]-b[1]}}) ||
      action.artifacts.filter((a:any) => a.kind === "png" || /\.(png|jpe?g|webp)$/i.test(a.path)).some((a:any) => a.path !== action.nativeAuthority.output) ||
      evidence.cases.some((row:Case) => row.subchecks.some((check:Subcheck) => check.status === "passed" && /captures(?:-|$)/.test(check.id) && check.artifacts.includes(action.nativeAuthority.output)))) throw new Error("keyboard diagnostic crop or acceptance differs");
}
/** Cross-reference actual capture actions; structural JSON alone cannot establish a capture. */
export function functionalEvidence(evidence: any): void {
  const deferred = evidence.imageCapture === "deferred";
  if (evidence.functionalMode !== undefined && (evidence.functionalMode !== "full27" || !deferred || evidence.regressionScenario !== undefined)) throw new Error("invalid full27 functional evidence mode");
  if (evidence.imageCapture !== undefined && !deferred) throw new Error("unknown image capture mode");
  if (evidence.vehiclePart !== undefined && (!["pw","fev-drawer"].includes(evidence.vehiclePart) || !deferred || evidence.regressionScenario !== "vehicle" || evidence.cases.some((row: Case) => row.number !== (evidence.vehiclePart === "pw" ? 14 : 13) && row.status !== "not-run" && !partialBootFailure(evidence, row)))) throw new Error("invalid vehicle subset evidence");
  if (evidence.settingsPart !== undefined && (evidence.settingsPart !== "postboot" || evidence.vehiclePart !== undefined || !deferred || evidence.regressionScenario !== "settings-radio" || evidence.cases.some((row: Case) => ![22,23].includes(row.number) && row.status !== "not-run" && !partialBootFailure(evidence, row)))) throw new Error("invalid postboot subset evidence");
  if (deferred) {
    const allowed = evidence.functionalMode === "full27" && evidence.regressionScenario === undefined ? [...EXECUTION_ORDER] : evidence.regressionScenario === "vehicle" ? [13,14] : evidence.regressionScenario === "settings-radio" ? [6,7,22,23] : evidence.regressionScenario === "launcher" ? [12,18] : installedSemanticRequired(evidence.regressionScenario) ? REGRESSION_ORDER[evidence.regressionScenario as RegressionScenario] : null;
    if (!allowed || evidence.passed !== false || evidence.cases.some((row: Case) => row.status === "passed" || (!allowed.includes(row.number) && row.status !== "not-run" && !partialBootFailure(evidence, row)))) throw new Error("deferred mode cannot claim full acceptance or run other cases");
    for (const action of evidence.actions) {
      if (action.nativeOperation === "session.screenshot" && action.keyboardDiagnostic) keyboardDiagnosticEvidence(evidence,action);
      else if (action.nativeOperation === "session.screenshot" || action.artifacts.some((a:any) => a.kind === "png" || /\.(png|jpe?g|webp)$/i.test(a.path))) throw new Error("deferred mode contains image action/artifact");
      if (action.args[0] === "--emulator-check") {
        const request = action.artifacts.find((a:any) => a.kind === "request" && a.path === action.args[1]);
        if (!request || digest(readFileSync(request.path)) !== request.sha256) throw new Error("deferred request authority missing");
        if (JSON.parse(readFileSync(request.path,"utf8")).action === "screenshot") throw new Error("deferred installer screenshot forbidden");
      }
    }
    if (evidence.boots.some((boot: Boot) => boot.status === "passed" || boot.screenshot !== null || (boot.semanticStatus !== undefined && (boot.semanticStatus !== "passed" || boot.status !== "unavailable")))) throw new Error("deferred boot cannot claim visual pass");
  }
  if(evidence.schemaVersion===4) {
    if(JSON.stringify(evidence.verificationContract)!==JSON.stringify(verificationContract) || evidence.preparedCheckInventory.length!==0) throw new Error("native verification contract differs");
    for(const action of evidence.actions.filter((a:any)=>a.nativeOperation==="session.screenshot" && a.exitCode===0 && a.nativeAuthority)) nativeCaptureAction(evidence,action);
  }
  const fixture = evidence.fixtureCleanup;
  if (fixture?.owned || fixture?.status === "passed") {
    const accepted = (id: number) => evidence.actions.find((action: any) => action.id === id && action.args[0] === "--emulator-check" && action.exitCode === 0 && !action.timedOut && !action.signal && !action.overflow && action.context?.kit === evidence.identity.package && action.context?.serial === evidence.runtime?.serial && action.context?.runtime?.pid === evidence.runtime?.pid && action.context?.runtime?.port === evidence.runtime?.port && uuid(action.context?.bootId));
    const decode = (action: any) => {
      if (!action) throw new Error("owned fixture lacks actual successful action");
      const request = action.artifacts.find((artifact: any) => artifact.kind === "request" && artifact.path === action.args[1]), output = action.artifacts.find((artifact: any) => artifact.kind === "stdout");
      if (!request || !output) throw new Error("owned fixture action receipt artifacts missing");
      const read = (artifact: any) => { const bytes = readFileSync(artifact.path); if (digest(bytes) !== artifact.sha256) throw new Error("fixture receipt bytes differ"); return bytes.toString("utf8"); };
      const payload = JSON.parse(read(request)), receipt = jsonReceipt(read(output));
      exactKeys(receipt, ["schemaVersion", "serial", "body"], "fixture receipt");
      if (receipt.schemaVersion !== 1 || receipt.serial !== evidence.runtime.serial || typeof receipt.body !== "string") throw new Error("fixture receipt serial differs");
      return { payload, receipt };
    };
    const absent = accepted(fixture.originalAbsenceAction), install = accepted(fixture.installAction), a = decode(absent), i = decode(install);
    if (absent.caseNumber !== 12 || install.caseNumber !== 12 || absent.id >= install.id || absent.context.bootId !== install.context.bootId || a.payload.action !== "fixture-state" || i.payload.action !== "install-fixture" || !fixtureOwned(a.receipt.body, i.receipt.body, fixture.package, i.payload.package) || !launcherFixtureSourceMatches(fixture.source)) throw new Error("owned fixture original identity or receipt differs");
    if (fixture.status === "passed") {
      const removal = accepted(fixture.removalAction), r = decode(removal);
      if (removal.caseNumber !== 18 || removal.id <= install.id || r.payload.action !== "remove-fixture" || r.payload.package !== plan.fixture.package || r.receipt.body.trim() !== "Success" || fixture.bootId !== removal.context.bootId || !fixture.artifacts.length) throw new Error("fixture cleanup lacks ordered actual removal proof");
      const after = evidence.actions.filter((action: any) => action.caseNumber === 18 && action.id > removal.id && action.context?.bootId === fixture.bootId && action.artifacts.some((artifact: any) => fixture.artifacts.includes(artifact.path))).map((action: any) => accepted(action.id)).filter(Boolean);
      if (!after.some((action: any) => { const value = decode(action); return value.payload.action === "fixture-state" && value.receipt.body === "fixture-absent\n"; })) throw new Error("fixture cleanup lacks fresh same-boot absence proof");
    }
  }
  const fixtureCase = evidence.cases.find((row: Case) => row.number === 18);
  if (fixtureCase?.status === "passed" && !fixtureCase.subchecks.some((check: Subcheck) => check.id === "fixture-oem-grid-retained" && check.status === "passed")) throw new Error("case18 lacks post-cleanup OEM grid integrity proof");
  for (const row of evidence.cases) {
    if (row.status !== "not-run" && !row.subchecks.length) throw new Error(`case${row.number} lacks named terminal observations`);
    for (const check of row.subchecks.filter((check: Subcheck) => check.status === "passed" && /captures(?:-|$)/.test(check.id))) {
      const locales = check.id === "three-locale-panel-captures" ? ["en", "ru", "zh"] : [/(en|ru|zh)$/.exec(check.id)?.[1]];
      if (locales.some((locale) => !locale) || !row.actions.length || !check.artifacts.length) throw new Error("capture pass lacks real locale actions and artifacts");
      for (const locale of locales) {
        const actions = evidence.actions.filter((action: any) => row.actions.includes(action.id) && action.caseNumber === row.number && action.exitCode === 0 && !action.timedOut && !action.signal && !action.overflow && action.context?.locale === locale && action.context?.kit === evidence.identity.package && action.context?.serial === evidence.runtime?.serial && action.context?.runtime?.pid === evidence.runtime?.pid && action.context?.runtime?.port === evidence.runtime?.port && uuid(action.context?.bootId));
        let pngBoot: string | null = null, snapshotBoot: string | null = null;
        for (const action of actions) {
          if (action.nativeOperation === "session.ui") {
            if (action.artifacts.some((a: any) => a.kind === "stdout" && check.artifacts.includes(a.path))) {
              nativeTreeAction(evidence, action); snapshotBoot = action.context.bootId;
            }
            continue;
          }
          if (action.nativeOperation === "session.screenshot") {
            const manifest=nativeCaptureAction(evidence,action);
            for(const artifact of action.artifacts.filter((a:any)=>a.kind==="png" && check.artifacts.includes(a.path))) {
              const bytes=readFileSync(artifact.path);
              if(digest(bytes)!==artifact.sha256 || artifact.boot!==action.context.bootId || artifact.locale!==locale || artifact.kit!==evidence.identity.package) throw new Error("native locale capture bytes/context differ");
              validatePng(bytes); pngBoot=manifest.android_boot_id;
            }
            continue;
          }
          if (action.args[0] !== "--emulator-check") continue;
          const requestArtifact = action.artifacts.find((artifact: any) => artifact.kind === "request" && artifact.path === action.args[1]);
          if (!requestArtifact) continue;
          const requestBytes = readFileSync(requestArtifact.path);
          if (digest(requestBytes) !== requestArtifact.sha256) throw new Error("capture request bytes differ");
          const request = JSON.parse(requestBytes.toString("utf8"));
          for (const artifact of action.artifacts.filter((artifact: any) => check.artifacts.includes(artifact.path))) {
            const bytes = readFileSync(artifact.path);
            if (digest(bytes) !== artifact.sha256) throw new Error("capture artifact bytes differ");
            if (request.action === "screenshot" && artifact.kind === "png" && request.output === artifact.path && artifact.boot === action.context.bootId && artifact.locale === locale && artifact.kit === evidence.identity.package && artifact.runtime?.pid === evidence.runtime.pid && artifact.runtime?.port === evidence.runtime.port) {
              validatePng(bytes); pngBoot = artifact.boot;
            }
            if (request.action === "snapshot" && artifact.kind === "stdout") {
              const receipt = jsonReceipt(bytes.toString("utf8"));
              exactKeys(receipt, ["schemaVersion", "serial", "body"], "capture hierarchy receipt");
              if (receipt.schemaVersion !== 1 || receipt.serial !== evidence.runtime.serial || typeof receipt.body !== "string") throw new Error("capture hierarchy identity differs");
              parseUi(receipt.body); snapshotBoot = action.context.bootId;
            }
          }
        }
        if (!pngBoot || pngBoot !== snapshotBoot) throw new Error("capture pass lacks successful same-boot PNG and hierarchy actions");
      }
    }
  }
  for (const boot of (evidence.boots ?? []).filter((boot: Boot) => boot.status === "passed" || (deferred && boot.semanticStatus === "passed"))) {
    const observed = evidence.actions.find((action: any) => action.id === boot.observation?.actionId);
    const verify = evidence.actions.findLast((action: any) => boot.actions.includes(action.id) && (evidence.schemaVersion===4 ? action.nativeOperation==="session.wait" : action.executable === process.execPath && action.args[4] === "verify"));
    if (!observed || !boot.actions.includes(observed.id) || observed.exitCode !== 0 || observed.timedOut || observed.signal || observed.overflow || observed.context?.bootId !== boot.after || boot.after !== boot.verifiedBoot || !verify || verify.exitCode !== 0 || verify.timedOut || verify.signal || verify.overflow || boot.observation.finishedMonotonicNs !== observed.finishedMonotonicNs || BigInt(observed.finishedMonotonicNs) > BigInt(verify.startedMonotonicNs)) throw new Error("passed boot lacks its independent observation before full verification");
    const verifyOutput = verify.artifacts.find((artifact: any) => artifact.kind === "stdout");
    const verifyBytes = readFileSync(verifyOutput.path);
    if (digest(verifyBytes) !== verifyOutput.sha256) throw new Error("boot verification bytes differ");
    if(evidence.schemaVersion===4) {
      const request=readNativeRequest(verify);
      const identity=nativeReady(nativeResponse({exitCode:verify.exitCode,stdout:verifyBytes.toString("utf8"),timedOut:verify.timedOut,overflow:verify.overflow,signal:verify.signal},request),request.session);
      if(identity.pid!==evidence.runtime.pid || identity.sessionId!==evidence.runtime.sessionId || identity.serial!==evidence.runtime.serial) throw new Error("native boot readiness session differs");
    } else verifyVoyemu({ exitCode: verify.exitCode, stdout: verifyBytes.toString("utf8"), stderr: "", timedOut: verify.timedOut, overflow: verify.overflow, signal: verify.signal }, evidence.voyemuProfile, evidence.preparedCheckInventory);
    const stdout = observed.artifacts.find((artifact: any) => artifact.kind === "stdout");
    const bytes = readFileSync(stdout.path);
    if (digest(bytes) !== stdout.sha256) throw new Error("boot observation bytes differ");
    const receipt = jsonReceipt(bytes.toString("utf8"));
    exactKeys(receipt, ["schemaVersion", "serial", "body"], "boot observation receipt");
    if (receipt.schemaVersion !== 1 || receipt.serial !== evidence.runtime.serial || typeof receipt.body !== "string" || /^bootId=(.*)$/m.exec(receipt.body)?.[1] !== boot.after || !/^bootCompleted=1$/m.test(receipt.body) || !/^enforcing=Enforcing$/m.test(receipt.body)) throw new Error("passed boot observation body differs");
    if (evidence.schemaVersion === 4 && boot.kind === "installer-1" && !deferred) {
      const proof = boot.homeProof;
      if (!proof) throw new Error("first boot lacks current HOME capture proof");
      const actions = [proof.before, proof.capture, proof.after].map(id => evidence.actions.find((a:any) => a.id === id && boot.actions.includes(id)));
      if (actions.some((a:any) => !a)) throw new Error("HOME proof action absent");
      const [beforeHome, image, afterHome] = actions;
      for (const action of [beforeHome, afterHome]) {
        if (action.nativeOperation !== "session.status" || action.context?.bootId !== boot.after || action.exitCode !== 0 || action.timedOut || action.signal || action.overflow) throw new Error("HOME read receipt differs");
        const request = readNativeRequest(action), artifact = action.artifacts.find((a:any) => a.kind === "stdout"), bytes = readFileSync(artifact.path);
        if (digest(bytes) !== artifact.sha256 || request.op !== "session.status" || request.profile !== evidence.voyemuProfile || request.session.id !== evidence.runtime.sessionId || action.context?.serial !== evidence.runtime.serial || action.context?.runtime?.sessionId !== evidence.runtime.sessionId || action.context?.runtime?.pid !== evidence.runtime.pid || action.context?.kit !== evidence.identity.package) throw new Error("HOME retained receipt differs");
        primaryHome(nativeResponse({exitCode:0,stdout:bytes.toString("utf8")},request),request.session,evidence.runtime,boot.after);
      }
      if (BigInt(beforeHome.finishedMonotonicNs) > BigInt(image.startedMonotonicNs) || BigInt(image.finishedMonotonicNs) > BigInt(afterHome.startedMonotonicNs) || nativeCaptureAction(evidence,image).android_boot_id !== boot.after) throw new Error("HOME image fence differs");
    }
    if (deferred) {
      const snapshots = evidence.actions.filter((a:any) => boot.actions.includes(a.id) && (a.args[0] === "--emulator-check" || a.nativeOperation === "session.ui") && a.exitCode === 0 && !a.timedOut && !a.signal && !a.overflow && a.context?.bootId === boot.after);
      let hierarchy = false, fenced = false;
      for (const action of snapshots) {
        if (action.nativeOperation === "session.ui") {
          nativeTreeAction(evidence, action);
          if (BigInt(action.startedMonotonicNs) > BigInt(verify.finishedMonotonicNs)) { hierarchy = true; fenced = false; }
          continue;
        }
        const request = action.artifacts.find((a:any) => a.kind === "request" && a.path === action.args[1]);
        const output = action.artifacts.find((a:any) => a.kind === "stdout");
        if (!request || !output) continue;
        const requestBytes = readFileSync(request.path), outputBytes = readFileSync(output.path);
        if (digest(requestBytes) !== request.sha256 || digest(outputBytes) !== output.sha256) throw new Error("semantic boot bytes differ");
        const payload = JSON.parse(requestBytes.toString("utf8")), value = jsonReceipt(outputBytes.toString("utf8"));
        if (value.serial !== evidence.runtime.serial || value.schemaVersion !== 1 || typeof value.body !== "string") throw new Error("semantic boot receipt differs");
        if (payload.action === "snapshot" && BigInt(action.startedMonotonicNs) > BigInt(verify.finishedMonotonicNs)) { parseUi(value.body); hierarchy = true; fenced = false; }
        if (hierarchy && payload.action === "boot-observation" && /^bootCompleted=1$/m.test(value.body) && /^enforcing=Enforcing$/m.test(value.body) && /^bootId=(.*)$/m.exec(value.body)?.[1] === boot.after) fenced = true;
      }
      if (!hierarchy || !fenced) throw new Error("semantic boot lacks fresh hierarchy and final boot fence");
    }
    if (/^installer-[0-9]+$/.test(boot.kind)) {
      const count = Number(boot.kind.slice(10));
      const documents = receipt.body.split("\n").filter((line: string) => line.startsWith("{")).map((line: string) => JSON.parse(line));
      if (boot.observation.rebootCount !== count || !documents.some((document: any) => document.operation === "install" && document.bootId === boot.after && document.rebootCount === count)) throw new Error("passed intermediate boot durable count differs");
    }
  }
}
function absenceReceipt(result: ProcessResult, preparation = false): any {
  if (result.exitCode !== 0 || result.timedOut || result.signal || result.overflow)
    throw new Error("uninstall process failed");
  const r = jsonReceipt(result.stdout);
  if (Object.hasOwn(r, "absence")) {
    exactKeys(
      r,
      ["schemaVersion", "serial", "bootIdBefore", "absence", "enforcing"],
      "Settings absence wrapper",
    );
    if (r.schemaVersion !== 1 || r.enforcing !== true || !uuid(r.bootIdBefore))
      throw new Error("Settings absence authority differs");
  }
  const body = r.absence ?? r;
  exactKeys(
    body,
    Object.hasOwn(body, "transactionId")
      ? ["transactionId", "bootId", "checks"]
      : ["state", "bootId", "checks"],
    "absence receipt",
  );
  if (
    (Object.hasOwn(body, "transactionId") && !/^[0-9a-f]{64}$/.test(body.transactionId)) ||
    (Object.hasOwn(body, "state") && body.state !== "already-absent" && !(preparation && body.state === "emergency-removed"))
  )
    throw new Error("normal absence identity differs");
  const checks = [
    "root",
    "app-data",
    "init-file",
    "package",
    "app-op",
    "mounts",
    "processes",
    "init-services",
    "policy",
  ];
  if (
    (!preparation && /emergency/i.test(result.stdout)) ||
    !Array.isArray(body.checks) ||
    JSON.stringify(body.checks) !== JSON.stringify(checks) ||
    !uuid(body.bootId)
  )
    throw new Error("normal nine-check absence receipt required");
  return body;
}
export function normalAbsence(result: ProcessResult): any {
  return absenceReceipt(result);
}
export function preparationAbsence(result: ProcessResult): { kind: "normal" | "already-absent" | "emergency"; receipt: any } {
  const receipt = absenceReceipt(result, true);
  return { kind: receipt.state === "emergency-removed" ? "emergency" : receipt.state === "already-absent" ? "already-absent" : "normal", receipt };
}
function uuid(s: unknown): s is string {
  return (
    typeof s === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(s)
  );
}
export async function captureBootObservation(ui: Pick<InstallerUi, "call">): Promise<Record<string, any>> {
  const receipt = await ui.call({ action: "boot-observation" });
  if (typeof receipt.body !== "string") throw new Error("mandatory boot observation body missing");
  parseBootObservation(receipt.body);
  return receipt;
}
export function parseBootObservation(body: string): { boot: string; bootCompleted: boolean } {
  if (Buffer.byteLength(body) > 16640) throw new Error("boot observation byte envelope differs");
  const match = /^bootCompleted=([01]?)\nbootId=([0-9a-f-]+)\nenforcing=(Enforcing|Permissive)\noperation:\n([\s\S]*)$/.exec(body);
  if (!match || !uuid(match[2])) throw new Error("mandatory boot observation malformed");
  if (match[4]) {
    if (Buffer.byteLength(match[4]) > 16384 || !match[4].endsWith("\n")) throw new Error("boot operation byte envelope differs");
    const operation = JSON.parse(match[4]);
    exactKeys(operation, ["schemaVersion", "operation", "transactionId", "journal", "phase", "outcome", "rebootCount", "bootId", "statusSequence", "journalSha256", "error", "installedApk"], "boot operation");
    const sha = (value: unknown) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
    if (operation.schemaVersion !== 1 || !["install", "restore", "update", "uninstall"].includes(operation.operation) || !sha(operation.transactionId) || !sha(operation.journalSha256) || !uuid(operation.bootId) || !Number.isInteger(operation.rebootCount) || operation.rebootCount < 0 || operation.rebootCount > 16 || !Number.isSafeInteger(operation.statusSequence) || operation.statusSequence < 1 || operation.journal !== `recovery/${operation.transactionId}/${operation.operation === "update" ? "product-proposal.json" : `${operation.operation}-journal.json`}` || typeof operation.phase !== "string" || !["running", "reboot-required", "failed", "complete"].includes(operation.outcome)) throw new Error("boot operation identity malformed");
  }
  if (match[3] === "Permissive") throw new ObservationPending("closed boot observation has not yet attested Enforcing");
  return { boot: match[2], bootCompleted: match[1] === "1" };
}
export function parseResidentStatus(body: string): { boot: string; bootCompleted: true; body: string } {
  const match = /^bootCompleted=1\nbootId=([0-9a-f-]{36})\nenforcing=Enforcing\nbootIdAfter=\1\nresident-status:\n\/data\/voboost\/status.json\n(\{[\s\S]*\})\n\n$/.exec(body);
  if (Buffer.byteLength(body) > RESIDENT_STATUS_ENVELOPE_BYTES || !match || !uuid(match[1])) throw new Error("resident status boot envelope differs");
  if (Buffer.byteLength(match[2]) + 1 > RESIDENT_STATUS_BYTES) throw new Error("resident status document byte envelope differs");
  return { boot: match[1], bootCompleted: true, body };
}

export function installOperation(body: string, boot: string, transactionId: string, preparation = false): any | null {
  const documents: any[] = [];
  const otherOperations: any[] = [];
  let operationSection = false;
  for (const line of body.split("\n")) {
    if (line === "operation:") { operationSection = true; continue; }
    if (operationSection && /^[a-zA-Z][\w-]*:$/.test(line)) operationSection = false;
    if (!line.startsWith("{")) continue;
    let document: any;
    try { document = JSON.parse(line); }
    catch {
      if (operationSection || /"operation"\s*:/.test(line)) throw new Error("malformed durable installer operation observation");
      continue;
    }
    if (document?.operation === "install") documents.push(document);
    else if (document?.operation === "restore") {
      exactKeys(document, ["schemaVersion", "operation", "transactionId", "journal", "phase", "outcome", "rebootCount", "bootId", "statusSequence", "journalSha256", "error", "installedApk"], "completed historical restore operation");
      const sha = (value: unknown) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
      if (document.schemaVersion !== 1 || !sha(document.transactionId) || !sha(document.journalSha256) || !uuid(document.bootId) || document.journal !== `recovery/${document.transactionId}/restore-journal.json` || document.phase !== "restore-complete" || document.outcome !== "complete" || document.error !== null || document.installedApk !== null || !Number.isInteger(document.rebootCount) || document.rebootCount < 1 || document.rebootCount > 16 || !Number.isSafeInteger(document.statusSequence) || document.statusSequence < 1) throw new Error("malformed durable completed restore observation");
      // A completed Restore is history, never evidence of the current install.
      otherOperations.push(document);
    }
    else if (document?.operation === "uninstall") otherOperations.push(document);
    else if (operationSection && document?.operation !== "uninstall") throw new Error("malformed durable installer operation observation");
  }
  if (documents.length > 1 || otherOperations.length > 1 || (documents.length && otherOperations.length)) throw new Error("conflicting durable installer operation observations");
  const operation = documents[0];
  if (!operation) return null;
  if (!uuid(operation.bootId) || !Number.isInteger(operation.rebootCount) || operation.rebootCount < 0 || operation.rebootCount > 16)
    throw new Error("foreign or malformed durable installer operation observation");
  if (operation.transactionId === transactionId) return operation.bootId === boot ? operation : null;
  const sha = (value: unknown) => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
  if (!preparation || !sha(operation.transactionId) || operation.schemaVersion !== 1 || typeof operation.phase !== "string" || !operation.phase || !["complete", "failed"].includes(operation.outcome) || !Number.isSafeInteger(operation.statusSequence) || operation.statusSequence < 1 || !sha(operation.journalSha256) || operation.journal !== `recovery/${operation.transactionId}/install-journal.json`)
    throw new Error("foreign or malformed durable installer operation observation");
  exactKeys(operation, ["schemaVersion", "operation", "transactionId", "journal", "phase", "outcome", "rebootCount", "bootId", "statusSequence", "journalSha256", "error", "installedApk"], "terminal historical install operation");
  if (operation.outcome === "failed") {
    // A failed previous-boot journal is recovery input, never current boot evidence.
    if (operation.bootId === boot || operation.installedApk !== null) throw new Error("foreign or malformed failed historical install operation");
    exactKeys(operation.error, ["code", "message"], "failed historical install error");
    if (typeof operation.error.code !== "string" || !operation.error.code || typeof operation.error.message !== "string" || !operation.error.message || Buffer.byteLength(operation.error.message) > 512) throw new Error("malformed failed historical install error");
    return null;
  }
  if (operation.phase !== "install-complete" || operation.error !== null || operation.rebootCount === 0) throw new Error("malformed completed historical install operation");
  const apk = operation.installedApk;
  exactKeys(apk, ["certificateSha256", "packageName", "path", "sha256", "size", "versionCode"], "completed historical installed APK");
  if (apk.packageName !== "ru.voboost" || typeof apk.path !== "string" || !/^\/data\/app\/ru\.voboost-[^/\s]+\/base\.apk$/.test(apk.path) || !sha(apk.sha256) || !sha(apk.certificateSha256) || !Number.isSafeInteger(apk.size) || apk.size < 1 || !Number.isSafeInteger(apk.versionCode) || apk.versionCode < 1)
    throw new Error("malformed completed historical installed APK observation");
  return null;
}
export function cacheRecoveryBootEvent(line: string): { schemaVersion: 1; operation: "cache-recovery-boot"; attemptToken: string; priorBootId: string; bootId: string; state: "waiting" } {
  const prefix = "VBI_CACHE_RECOVERY_BOOT|";
  if (!line.startsWith(prefix) || Buffer.byteLength(line) > 1024) throw new Error("recovery acceptance event bound/prefix invalid");
  const encoded = line.slice(prefix.length);
  const row = JSON.parse(encoded);
  if (JSON.stringify(row) !== encoded) throw new Error("noncanonical recovery event or duplicate keys");
  exactKeys(row, ["schemaVersion", "operation", "attemptToken", "priorBootId", "bootId", "state"], "recovery acceptance event");
  if (row.schemaVersion !== 1 || row.operation !== "cache-recovery-boot" || row.state !== "waiting" || typeof row.attemptToken !== "string" || !/^[a-f0-9]{64}$/.test(row.attemptToken) || !uuid(row.priorBootId) || !uuid(row.bootId) || row.priorBootId === row.bootId) throw new Error("recovery acceptance event identity invalid");
  return row;
}
export async function recoveryBootAck(identity: ReturnType<typeof cacheRecoveryBootEvent>, checks: {
  ownership: () => Promise<void>;
  functionalMode?: "full27";
  verify: () => Promise<{status: string; reason: string; after: string | null; verifiedBoot: string | null; semanticStatus?: string; screenshot?: string | null}>;
  current: () => Promise<{boot: string; bootCompleted: boolean}>;
}): Promise<string> {
  let decision: "accept" | "reject" = "reject";
  try {
    await checks.ownership();
    const row = await checks.verify();
    if ((row.status !== "passed" && !(checks.functionalMode === "full27" && semanticBoot(row))) || row.after !== identity.bootId || row.verifiedBoot !== identity.bootId) throw new Error(`recovery boot proof rejected: ${row.reason}`);
    await checks.ownership();
    const current = await checks.current();
    if (current.boot !== identity.bootId || !current.bootCompleted) throw new Error("recovery ACK boot changed");
    decision = "accept";
  } catch { /* A negative receipt leaves recovery authority intact. */ }
  const { state: _state, ...proof } = identity;
  return JSON.stringify({ ...proof, decision }) + "\n";
}
export function observerArmed(chunk: string): boolean {
  return chunk.split(/\r?\n/).slice(0, -1).includes("settings-uninstall-observer-armed-v1");
}
export const spawnAdapter: ProcessAdapter = async (inv) =>
  new Promise((resolve) => {
    if (
      inv.host
        ? basename(inv.executable) !== "gradlew" || JSON.stringify(inv.args) !== JSON.stringify(AGENT_HOST_ARGS)
        : inv.voyemu
        ? !nativeDispatch(inv) && !(inv.executable === process.execPath && inv.args.length === 6 && isAbsolute(inv.args[2]) && inv.args[0] === join(inv.args[2], "src/cli.ts") && inv.args[1] === "--root" && inv.args[3] === "--json" && ["status", "verify"].includes(inv.args[4]) && /^[a-z0-9][a-z0-9._-]{0,63}$/.test(inv.args[5]))
        : inv.args[0] === "--emulator-check" ? !isDriverInvocation(inv) : basename(inv.executable) !== "voboost-installer"
    )
      throw new Error("only packaged installer or official Voyemu allowed");
    const child = spawn(inv.executable, inv.args, {
      stdio: [inv.stdinUntil || inv.stdinReply ? "pipe" : "ignore", "pipe", "pipe"],
      env: { ...process.env, VOBOOST_LOG: !inv.host && !inv.voyemu && inv.args[0] === "--install" ? "debug" : "info" },
      cwd: inv.host ? dirname(inv.executable) : undefined,
    });
    if (inv.stdinUntil) inv.stdinUntil.then(() => child.stdin?.end(), () => child.stdin?.end());
    let stdout = "",
      stderr = "",
      overflow = false,
      done = false;
    const finish = (r: ProcessResult) => {
      if (!done) {
        done = true;
        clearTimeout(timer);
        resolve(r);
      }
    };
    child.stdin?.on("error", error => finish({ exitCode: null, stdout, stderr: stderr + String(error), processId: child.pid, overflow }));
    for (const [name, stream] of [
      ["stdout", child.stdout],
      ["stderr", child.stderr],
    ] as const)
      stream.on("data", (b) => {
        const text = String(b);
        inv.onOutput?.(name, text);
        if (inv.stdinReply) void inv.stdinReply(name, text).then(reply => {
          if (reply !== null) child.stdin?.end(reply);
        }, error => {
          child.stdin?.end();
          finish({ exitCode: null, stdout, stderr: stderr + String(error), processId: child.pid, overflow });
        });
        if (name === "stdout") stdout += text;
        else stderr += text;
        if (Buffer.byteLength(stdout) + Buffer.byteLength(stderr) > MAX_CAPTURE) {
          overflow = true;
          stdout = stdout.slice(-MAX_CAPTURE / 2);
          stderr = stderr.slice(-MAX_CAPTURE / 2);
        }
      });
    // Observation deadlines do not interrupt a device transaction. No TERM/KILL fault cuts.
    const timer = setTimeout(
      () =>
        finish({ exitCode: null, stdout, stderr, timedOut: true, processId: child.pid, overflow }),
      inv.timeoutMs,
    );
    child.on("error", (e) =>
      finish({
        exitCode: null,
        stdout,
        stderr: stderr + String(e),
        processId: child.pid,
        overflow,
      }),
    );
    child.on(inv.stdinUntil || inv.stdinReply ? "close" : "exit", (exitCode, signal) =>
      finish({ exitCode, signal, stdout, stderr, processId: child.pid, overflow }),
    );
  });
export function resultOk(r: ProcessResult): void {
  if (r.exitCode !== 0 || r.timedOut || r.signal || r.overflow || r.retained?.overflow) {
    // Redact before truncation so a capture boundary cannot expose a partial credential.
    const stderr = r.stderr
      .replace(/\b(Bearer\s+)\S+/gi, "$1[redacted]")
      .replace(/((?:["']?)(?:[\w-]*(?:token|secret|password|credential|api[-_]?key)[\w-]*)(?:["']?)\s*(?:=|:|\s)\s*)(?:"[^"]*"|'[^']*'|[^\s,;]+)/gi, "$1[redacted]")
      .replace(/(https?:\/\/)[^\s/@]+:[^\s/@]+@/gi, "$1[redacted]@")
      .replace(/[\u0000-\u001f\u007f]+/g, " ")
      .trim()
      .slice(0, 512);
    const signal = r.signal ? (/^SIG[A-Z0-9]{1,16}$/.test(r.signal) ? r.signal : "unknown") : "none";
    throw new Error(`process failed capture contract: exitCode=${r.exitCode} signal=${signal} timedOut=${r.timedOut === true} overflow=${r.overflow === true || r.retained?.overflow === true}${stderr ? ` stderr=${stderr}` : ""}`);
  }
}

export function noneProcesses(body: string): void {
  const part = body.split("processes:\n")[1]?.split("\ncontexts:")[0];
  if (part === undefined) throw new Unavailable("process inventory not exposed");
  const rows = part.trim().split("\n");
  if (!/^\s*PID\s+NAME\s*$/.test(rows[0]))
    throw new Unavailable("trusted process inventory header missing");
  for (const row of rows.slice(1)) {
    if (!row.trim()) continue;
    if (!/^\s*\d+\s+[A-Za-z0-9_.:-]+\s*$/.test(row))
      throw new Unavailable("process inventory unavailable or malformed");
    if (/(?:^|\s)(voboost[^\s]*|ru[.]voboost[^\s]*)/.test(row))
      throw new Error("none has a resident Voboost process");
  }
}
export function fixtureOwned(absence: string, installed: string, packageName: string, requestedPackage: string): boolean {
  const lines = installed.trim().split(/\r?\n/);
  return absence === "fixture-absent\n" && packageName === plan.fixture.package && requestedPackage === plan.fixture.package && lines.at(-1) === "Success" && lines.filter((line) => line === "Success").length === 1 && !lines.some((line) => /failure|error|denied/i.test(line));
}
type StartupMode = "interface" | "hidden" | "none";
/** Observe only the selected startup copy; full-panel copy belongs to case22. */
export async function startupDescriptionCapture(
  top: () => Promise<UiNode[]>, capture: () => Promise<unknown>,
  copy: SettingsCopy, language: Language, mode: StartupMode,
): Promise<void> {
  const nodes = await top();
  const captions = copy.languages[language];
  const owned = nodes.filter(node => node.packageName === SETTINGS && focusedApplication(node));
  const tab = uniqueNode(owned, node => node.text === "Voboost" && node.checkable && node.checked);
  const visible = owned.filter(node => node.windowRef === tab.windowRef);
  uniqueNode(visible, node => node.text === captions.startupTitle);
  for (const choice of captions.startup) {
    const radio = uniqueNode(visible, node => node.checkable && node.text === choice.label);
    if (radio.checked !== (choice.value === mode)) throw new Error("startup description selected mode differs");
  }
  const selected = captions.startup.find(choice => choice.value === mode);
  if (!selected) throw new Error("startup description mode absent from source copy");
  uniqueNode(visible, node => node.text === selected.description);
  await capture();
}
export async function startupProfiles(selectLanguage: (language: Language) => Promise<void>, selectMode: (mode: StartupMode) => Promise<void>, observe: (mode: StartupMode, language: Language) => Promise<void>, returnFromNone: () => Promise<void>): Promise<void> {
  for (const language of ["en", "ru", "zh"] as Language[]) {
    await selectLanguage(language);
    for (const mode of ["interface", "hidden", "none"] as StartupMode[]) {
      await selectMode(mode);
      await observe(mode, language);
      if (mode === "none") await returnFromNone();
    }
  }
}
export async function startupReboot(mode: "hidden" | "none", select: (mode: StartupMode) => Promise<void>, observe: (mode: StartupMode) => Promise<void>, reboot: () => Promise<void>): Promise<void> {
  await select(mode);
  await observe(mode);
  await reboot();
}
export function emptyEditor(nodes: UiNode[]): UiNode {
  const editor = nodes.filter(
    (n) =>
      n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
      n.focused && n.windowRef?.type === 1 &&
      n.className === "android.widget.EditText",
  );
  if (editor.length !== 1 || editorValue(editor[0]) !== "")
    throw new Unavailable("exact empty focused editor not observed; caret is not assumed");
  return editor[0];
}
export function keyboardKey(nodes: UiNode[], character: string): UiNode {
  const editors = nodes.filter(
    (n) =>
      n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
      n.focused && n.windowRef?.type === 1 &&
      n.className === "android.widget.EditText",
  );
  if (editors.length !== 1) throw new Unavailable("fresh focused editor not uniquely observed");
  const alphabet = /[А-Яа-яЁё]/.test(character) ? /[А-Яа-яЁё]/ : /[A-Za-z]/;
  const keys = nodes.filter(
    (n) => /^com.qinggan.app.qgime(?:\.second)?$/.test(n.packageName) && n.clickable && n.enabled && n.visibleToUser === true && n.windowRef?.type === 2 && n.windowRef.rootPackage === n.packageName && n.windowRef.capture === editors[0].windowRef?.capture,
  );
  const letters = new Set(
    keys
      .map((n) => n.text || n.description)
      .filter((text) => text.length === 1 && alphabet.test(text)),
  );
  if (letters.size < 8)
    throw new Unavailable("live keyboard layout not proven from actual visible keys");
  const matches = keys.filter((n) => n.text === character || n.description === character);
  if (matches.length !== 1)
    throw new Unavailable("actual keyboard key is not a unique semantic node");
  return matches[0];
}
export async function launcherFirstPage<T extends { node: UiNode; label: string }>(read: () => Promise<T[]>, swipeRight: (items: T[]) => Promise<void>, isBeginning: (items: T[]) => Promise<boolean>): Promise<T[]> {
  let items = await read();
  const key = (rows: T[]) => JSON.stringify(rows.map(row => [row.label, row.node.bounds]));
  const seen = new Set<string>([key(items)]);
  for (let step = 0; step <= 12; step++) {
    if (!items.length) throw new Unavailable("empty launcher viewport during first-page normalization");
    if (await isBeginning(items)) return items;
    if (step === 12) break;
    await swipeRight(items);
    const next = await read();
    const nextKey = key(next);
    if (seen.has(nextKey)) throw new Unavailable("launcher normalization stalled or cycled before independent first-page witness");
    seen.add(nextKey);
    items = next;
  }
  throw new Unavailable("launcher first-page boundary not reached within bounded navigation");
}
export function launcherItems(
  nodes: UiNode[],
  grid: UiNode,
  nativePointer=false,
): Array<{ node: UiNode; label: string }> {
  if(nativePointer && launcherPrimaryPointerWindow(nodes)!==grid.windowRef) throw new Error("launcher items pointer owner differs");
  const parent = nodes.indexOf(grid);
  if (parent < 0) throw new Error("foreign launcher grid");
  return nodes
    .filter(
      (n) =>
        n.packageName === "com.qinggan.app.launcher" &&
        n.parent === parent &&
        n.clickable &&
        n.enabled && n.visibleToUser !== false && (nativePointer || focusedApplication(n)) && n.windowRef === grid.windowRef,
    )
    .map((node) => {
      const root = nodes.indexOf(node);
      const descendants = nodes.filter((n) => {
        let p = n.parent;
        while (p !== null) {
          if (p === root) return true;
          p = nodes[p]?.parent ?? null;
        }
        return false;
      });
      if (grid.windowRef) {
        const actualLabels = descendants.filter(n => n.packageName === node.packageName && n.windowRef === grid.windowRef && n.resource === "com.qinggan.app.launcher:id/item_name");
        if (actualLabels.length !== 1 || !actualLabels[0].text) throw new Unavailable("OEM app icon has no unique actual item_name label");
        const labels = actualLabels.filter(n => n.visibleToUser === true);
        // Adjacent page labels can remain visible while their clipped icon is offscreen.
        const [left, , right] = node.bounds;
        const viewportLeft = Math.max(grid.bounds[0], grid.windowRef.bounds[0]);
        const viewportRight = Math.min(grid.bounds[2], grid.windowRef.bounds[2]);
        const fullPeerWidths = nodes.filter(n => n.parent === parent && n.packageName === node.packageName && n.windowRef === grid.windowRef && n.visibleToUser === true && n.bounds[0] > viewportLeft && n.bounds[2] < viewportRight && nodes.some(label => label.parent === nodes.indexOf(n) && label.resource === "com.qinggan.app.launcher:id/item_name" && label.packageName === n.packageName && label.windowRef === grid.windowRef && label.visibleToUser === true && label.text)).map(n => n.bounds[2] - n.bounds[0]);
        if (fullPeerWidths.length && (left === viewportLeft || right === viewportRight) && right > left && right - left < Math.min(...fullPeerWidths)) return null;
        if (labels.length !== 1) {
          throw new Unavailable("OEM app icon has no unique actual item_name label");
        }
        return { node, label: labels[0].text };
      }
      return {
        node,
        label: [
          node.text,
          node.description,
          ...descendants
            .filter((n) => n.packageName === node.packageName)
            .map((n) => n.text || n.description),
        ]
          .filter(Boolean)
          .join(" "),
      };
    })
    .filter((item): item is { node: UiNode; label: string } => item !== null)
    // OEM selection scales an item around its center; its top edge is not its row.
    .sort((a, b) => (a.node.bounds[1] + a.node.bounds[3]) - (b.node.bounds[1] + b.node.bounds[3]) || (a.node.bounds[0] + a.node.bounds[2]) - (b.node.bounds[0] + b.node.bounds[2]));
}
export function bootPrimaryHomeProof(native: boolean, kind: string, deferred: boolean): boolean {
  return native && !deferred && (/^installer-[1-9][0-9]*$/.test(kind) || kind === "cache-recovery");
}

export function retainTimedOutDeviceOperation(inv: Invocation, result: ProcessResult): boolean {
  if (!result.timedOut || inv.voyemu || inv.host) return false;
  if (isDriverInvocation(inv)) {
    try {
      const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
      if (Object.keys(request).length === 1 && ["boot-observation", "resident-status", "inspect"].includes(request.action)) return false;
    } catch { /* Unknown requests keep the device mutation fence. */ }
  }
  return true;
}

export function assertRegressionInvocation(inv: Invocation, scenario?: RegressionScenario): void {
        if (inv.args.some(arg => ["--install", "--uninstall", "--uninstall-emergency"].includes(arg)))
          throw new Error("regression forbids Voboost install/removal");
        if (inv.args[0] === "--emulator-check") {
          const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
          const stateRecoveryFault = scenario === "state-recovery" && request.action === "fault-window" && request.kind === "vehicle-state-reply-loss";
          if (((request.action === "fault-window" && !stateRecoveryFault || request.action === "observe-settings-uninstall" && scenario !== "final-uninstall") || (scenario !== "launcher" && ["install-fixture", "remove-fixture"].includes(request.action))))
            throw new Error("regression forbids destructive/fault/fixture operation");
        }
      }

function launcherFixtureSourceMatches(source: unknown): boolean {
  return source === plan.fixture.source || source === "tests/fixtures/launcher";
}

export function ownedFixtureCleanupAllowed(receipt: any): boolean {
  return receipt?.owned === true && receipt.package === plan.fixture.package && launcherFixtureSourceMatches(receipt.source) && Number.isInteger(receipt.originalAbsenceAction) && receipt.originalAbsenceAction > 0 && Number.isInteger(receipt.installAction) && receipt.installAction > receipt.originalAbsenceAction;
}
export async function launcherFinallyCleanup(state: { scenario?: RegressionScenario; receipt: any; activeOperation: unknown; monitorHealthy: boolean; leaseStatus: string }, cleanup: () => Promise<void>): Promise<{ attempted: boolean; error: unknown }> {
  if (state.scenario !== "launcher" || state.receipt?.status === "passed" || !ownedFixtureCleanupAllowed(state.receipt) || state.activeOperation !== null || !state.monitorHealthy || state.leaseStatus !== "retained") return { attempted: false, error: null };
  try { await cleanup(); return { attempted: true, error: null }; }
  catch (error) { return { attempted: true, error }; }
}
export type RegressionOptions = { imageCapture?: "deferred"; vehiclePart?: "pw" | "fev-drawer"; settingsPart?: "postboot" };
export function regressionOptions(scenario: RegressionScenario, options: RegressionOptions = {}): boolean {
  if (Object.keys(options).some(key => key !== "imageCapture" && key !== "vehiclePart" && key !== "settingsPart") || (options.imageCapture !== undefined && options.imageCapture !== "deferred")) throw new Error("unknown regression options");
  if (options.imageCapture === "deferred" && scenario !== "vehicle" && scenario !== "settings-radio" && scenario !== "launcher" && !installedSemanticRequired(scenario)) throw new Error("deferred images require a fixed regression");
  if (options.vehiclePart !== undefined && (!["pw","fev-drawer"].includes(options.vehiclePart) || scenario !== "vehicle" || options.imageCapture !== "deferred")) throw new Error("vehicle subset requires deferred vehicle regression");
  if (options.settingsPart !== undefined && (options.settingsPart !== "postboot" || scenario !== "settings-radio" || options.imageCapture !== "deferred" || options.vehiclePart !== undefined)) throw new Error("postboot subset requires deferred settings-radio regression only");
  if (installedSemanticRequired(scenario) && (options.imageCapture !== "deferred" || options.vehiclePart !== undefined || options.settingsPart !== undefined)) throw new Error("installed semantic subsets require deferred images and no other subset options");
  return options.imageCapture === "deferred";
}
export type RegressionScenario = "vehicle" | "settings-radio" | "launcher" | "lifecycle" | "lifecycle-input" | "input" | "keyboards" | "startup" | "final-uninstall" | "state-recovery" | "vehicle-startup";
const INSTALLED_SEMANTIC_SCENARIOS = ["lifecycle", "lifecycle-input", "input", "keyboards", "startup", "final-uninstall", "state-recovery", "vehicle-startup"] as const;
export function installedSemanticRequired(scenario: RegressionScenario): string[] | null {
  switch (scenario) {
    case "lifecycle": return ["8:agent-lifecycle-and-keyboard-exclusion"];
    case "lifecycle-input": return ["8:agent-lifecycle-and-keyboard-exclusion", "9:actual-cyrillic-input", "19:moscow-and-yo-codepoints", "19:mixed-ru-en-codepoints", "10:actual-english-input", "11:actual-mixed-layout-input"];
    case "input": return ["9:actual-cyrillic-input", "19:moscow-and-yo-codepoints", "19:mixed-ru-en-codepoints", "10:actual-english-input", "11:actual-mixed-layout-input"];
    case "keyboards": return ["10:actual-english-input", "11:actual-mixed-layout-input"];
    case "startup": return ["15:hidden-enabled-agents-tbox-recovery", "16:none-process-absence-computer-recovery"];
    case "final-uninstall": return ["17:settings-uninstall-normal-absence-reboot"];
    case "vehicle-startup": return ["27:vehicle-startup-current-receipts", "27:host-passive-acc-listener-contract"];
    case "state-recovery": return ["25:host-bounded-retry-cancel", "25:current-multi-target-readback", "25:runtime-disable-cancellation-fev", "25:runtime-disable-cancellation-pw", "25:runtime-state-outage-retry-recovery", "25:runtime-state-outage-retry-cancel"];
    default: return null;
  }
}
function installedSemanticSummary(evidence: any, scenario: RegressionScenario): any {
  const order = REGRESSION_ORDER[scenario], required = installedSemanticRequired(scenario)!;
  const selected = evidence.cases.filter((row: Case) => order.includes(row.number as never));
  const pendingIds = order.map(number => number + ":installed-visual-pending");
  const knownVisualIds = order.flatMap(number => visualChecks(number).map(check => number + ":" + check.id));
  const omittedIds = new Set([...pendingIds, ...knownVisualIds]);
  const rows = selected.flatMap((row: Case) => row.subchecks.map((check: Subcheck) => ({caseNumber:row.number,...check})));
  const optionalDiagnosticChecks = rows.filter((check:any) => fontDiagnosticOnly(check.caseNumber, check.id));
  const checks = rows.filter((check:any) => !omittedIds.has(check.caseNumber + ":" + check.id) && !fontDiagnosticOnly(check.caseNumber, check.id));
  const omitted = rows.filter((check:any) => omittedIds.has(check.caseNumber + ":" + check.id));
  const exactRequired = required.every(id => checks.filter((check:any) => check.caseNumber + ":" + check.id === id).length === 1);
  const exactPending = pendingIds.every(id => omitted.filter((check:any) => check.caseNumber + ":" + check.id === id).length === 1);
  const wrongCase = evidence.cases.some((row:Case) => !order.includes(row.number as never) && row.status !== "not-run");
  return {schemaVersion:1,evidenceType:"installed-kit-partial-regression",scenario,publication:"forbidden",fullAcceptance:false,passed:false,
    imageCapture:"deferred",invocationId:evidence.invocationId,identity:evidence.identity,runtime:evidence.runtime,
    pendingAcceptance:["images","visual"],requiredCheckIds:required,checks,optionalDiagnosticChecks,omittedFullAcceptanceChecks:omitted,
    rebootFailures: evidence.cases.filter((row: Case) => partialBootFailure(evidence, row)),
    semanticPassed: evidence.imageCapture === "deferred" && evidence.regressionScenario === scenario && !wrongCase && exactRequired && exactPending &&
      optionalDiagnosticChecks.every((check:any) => check.status === "passed" || check.status === "unavailable") &&
      selected.every((row:Case) => new Set(row.subchecks.map(check => check.id)).size === row.subchecks.length) &&
      selected.length === order.length && selected.every((row:Case) => row.status === "unavailable") &&
      checks.every((check:any) => check.status === "passed" && check.artifacts.length > 0) && omitted.every((check:any) => check.status === "unavailable") &&
      (scenario === "lifecycle" || scenario === "lifecycle-input" || scenario === "input" || scenario === "keyboards" || scenario === "state-recovery" || evidence.boots.length > 0) && evidence.boots.every((boot:Boot) => boot.status === "unavailable" && boot.semanticStatus === "passed" && boot.screenshot === null)};
}
const REGRESSION_ORDER = { launcher: [12, 18], vehicle: [13, 14], "settings-radio": [6, 7, 22, 23], lifecycle: [8], "lifecycle-input": [8, 9, 19, 10, 11], input: [9, 19, 10, 11], keyboards: [10, 11], startup: [15, 16], "final-uninstall": [17], "state-recovery": [25], "vehicle-startup": [27] } as const;
export function regressionSummary(evidence: any, scenario: RegressionScenario): any {
  const deferred = evidence.imageCapture === "deferred";
  if (deferred && evidence.regressionScenario !== scenario) throw new Error("deferred scenario differs from evidence");
  if (deferred) regressionOptions(scenario, { imageCapture: "deferred" });
  if (scenario !== "vehicle" && scenario !== "settings-radio" && scenario !== "launcher" && !installedSemanticRequired(scenario)) throw new Error("unknown fixed regression scenario");
  regressionOptions(scenario, { ...(deferred ? { imageCapture: "deferred" } : {}), ...(evidence.vehiclePart !== undefined ? { vehiclePart: evidence.vehiclePart } : {}), ...(evidence.settingsPart !== undefined ? { settingsPart: evidence.settingsPart } : {}) });
  if (installedSemanticRequired(scenario)) return installedSemanticSummary(evidence, scenario);
  const order = evidence.settingsPart === "postboot" ? [22, 23] : evidence.vehiclePart === "pw" ? [14] : evidence.vehiclePart === "fev-drawer" ? [13] : REGRESSION_ORDER[scenario];
  if (evidence.settingsPart === "postboot" && evidence.cases.some((row: Case) => !order.includes(row.number) && row.status !== "not-run" && !partialBootFailure(evidence, row))) throw new Error("postboot subset ran another case");
  if (evidence.vehiclePart === "pw" && evidence.cases.some((row: Case) => row.number !== 14 && row.status !== "not-run" && !partialBootFailure(evidence, row))) throw new Error("PW subset ran another case");
  if (evidence.vehiclePart === "fev-drawer" && evidence.cases.some((row: Case) => row.number !== 13 && row.status !== "not-run" && !partialBootFailure(evidence, row))) throw new Error("FEV drawer subset ran another case");
  const selected = evidence.cases.filter((row: Case) => order.includes(row.number as never));
  const omitted = new Set(deferred && scenario === "vehicle" ? ["13:vehicle-image-pending", "14:vehicle-image-pending"] : scenario === "launcher" ? ["18:oem-stock-baseline", "18:oem-grid-retained", "18:oem-icon-bind-provenance", ...visualChecks(12).map(check => "12:" + check.id), ...visualChecks(18).map(check => "18:" + check.id)] : ["22:destructive-error-copy-excluded", "23:fault-rejection-excluded", "23:startup-rejected-mutation"]);
  const checks = selected.flatMap((row: Case) => row.subchecks.filter((check: Subcheck) => !omitted.has(row.number + ":" + check.id)).map((check: Subcheck) => ({ caseNumber: row.number, ...check })));
  const required = scenario === "launcher" ? ["12:launcher-visible-page-order-after-reboot", (deferred ? "12:launcher-semantic-surfaces-lifetime" : "12:launcher-icons-paired-surfaces"), "12:launcher-two-screen-disk-cache-after-reboot", "12:launcher-theme-cache-roundtrip", "18:fresh-package-classification", "18:owned-fixture-cleanup", "18:fixture-oem-grid-retained"] : scenario === "vehicle"
    ? (evidence.vehiclePart === "fev-drawer" ? ["arm","hybrid","save","electric-restore","explicit-off-hold","off-circle","cold-off"].map(id=>"13:fev-drawer-"+id) : [...(evidence.vehiclePart === "pw" ? ["pw"] : ["fev", "pw"]).flatMap((vehicle, index) => ["cold-baseline", "warm-baseline", "armed-current", "cold-restore", "warm-restore"].map(id => (vehicle === "pw" ? 14 : 13) + ":" + vehicle + "-" + id)), ...(evidence.vehiclePart === "pw" ? [] : ["arm","hybrid","save","electric-restore","explicit-off-hold","off-circle","cold-off"].map(id=>"13:fev-drawer-"+id))])
    : [...(evidence.settingsPart === "postboot" ? ["22:language-reboot-persistence"] : ["6:language-opposite-selected", "7:language-original-restored", ...["language-loop-copy-dialog-cancel", "language-process-resource-locale", "i18n-system-picker-multi-owner", "language-reboot-persistence"].map(id => "22:" + id)]),
       "23:host-all-startup-description-copy", "23:cancel-does-not-uninstall",
       ...["en", "ru", "zh"].flatMap(locale => ["interface", "hidden", "none"].flatMap(mode => ["23:startup-" + mode + "-native-draw-transition-" + locale, "23:startup-" + mode + "-description-" + locale]))];
  if (deferred && scenario === "settings-radio") for (const number of order) omitted.add(number + ":settings-radio-visual-pending");
  if (deferred && scenario === "launcher") for (const number of order) omitted.add(number + ":launcher-visual-pending");
  // Recompute the semantic rows after adding pending markers; canonical required IDs stay unchanged.
  const semanticChecks = checks.filter((check: any) => !omitted.has(check.caseNumber + ":" + check.id));
  const complete = required.every(id => semanticChecks.filter((check: any) => check.caseNumber + ":" + check.id === id).length === 1);
  const omittedChecks = selected.flatMap((row: Case) => row.subchecks.filter((check: Subcheck) => omitted.has(row.number + ":" + check.id)).map((check: Subcheck) => ({ caseNumber: row.number, ...check })));
  const launcherPendingComplete = scenario !== "launcher" || [...omitted].every(id => omittedChecks.filter((check: any) => check.caseNumber + ":" + check.id === id).length === 1);
  return {
    schemaVersion: 1, evidenceType: "installed-kit-partial-regression", scenario,
    publication: "forbidden", fullAcceptance: false, invocationId: evidence.invocationId,
    identity: evidence.identity, runtime: evidence.runtime,
    ...(evidence.vehiclePart !== undefined ? { vehiclePart: evidence.vehiclePart } : {}),
    ...(evidence.vehiclePart === "fev-drawer" ? { excludedChecks: ["cold-baseline","warm-baseline","armed-current","cold-restore","warm-restore"].map(id=>"13:fev-"+id), excludedChecksAccepted: false } : {}),
    ...(evidence.settingsPart === "postboot" ? { settingsPart: "postboot" } : {}),
    ...(deferred && scenario === "settings-radio" ? { pendingAcceptance: ["images", "animation", "visual"], semanticScope: "XML, source-bound captions, selection, ACK telemetry and reboot state; draw traces do not accept visual animation" } : {}),
    ...(deferred && scenario === "launcher" ? { pendingAcceptance: ["images", "geometry", "rounding", "lighting", "inversion"], semanticScope: "Actual order, process lifetime, selected theme, two-surface cache telemetry and owned fixture cleanup; no visible appearance accepted" } : {}),
    ...(deferred ? { imageCapture: "deferred", semanticPassed: complete && launcherPendingComplete && selected.length === order.length && selected.every((row: Case) => row.status === "unavailable") && semanticChecks.length === required.length && semanticChecks.every((check: Subcheck) => check.status === "passed" && ((scenario !== "settings-radio" && scenario !== "launcher") || check.artifacts.length > 0)) && omittedChecks.length === (scenario === "launcher" ? omitted.size : order.length + (scenario === "settings-radio" ? 3 : 0)) && omittedChecks.every((check: Subcheck) => check.status === "unavailable") && evidence.boots.length > 0 && evidence.boots.every((boot: Boot) => boot.status === "unavailable" && boot.semanticStatus === "passed" && boot.screenshot === null) } : {}),
    passed: !deferred && required.length > 0 && complete && selected.length === REGRESSION_ORDER[scenario].length &&
      selected.every((row: Case) => row.status !== "not-run" && row.status !== "failed") &&
      checks.length >= required.length && checks.every((check: Subcheck) => check.status === "passed") &&
      omittedChecks.every((check: Subcheck) => check.status === "unavailable") &&
      evidence.boots.length > 0 && evidence.boots.every((boot: Boot) => boot.status === "passed"),
    requiredCheckIds: required, checks, omittedFullAcceptanceChecks: omittedChecks,
    rebootFailures: evidence.cases.filter((row: Case) => partialBootFailure(evidence, row)),
  };
}
export type FullRunPermit = { schemaVersion: 1; operation: "full27-install-uninstall"; packageSha256: string; dmgSha256: string; sessionReceiptSha256: string; sourceReceiptSha256: string };
/** Explicit destructive admission is separate from installed-kit regressions. */
export async function runFullSemanticGate(c: GateConfig, adapter: ProcessAdapter, permit: FullRunPermit): Promise<any> {
  exactKeys(permit, ["schemaVersion", "operation", "packageSha256", "dmgSha256", "sessionReceiptSha256", "sourceReceiptSha256"], "full27 permit");
  if (!c.nativeSession || permit.schemaVersion !== 1 || permit.operation !== "full27-install-uninstall" ||
      permit.packageSha256 !== packageInventory(c) || permit.dmgSha256 !== digest(readFileSync(c.dmg)) ||
      permit.sessionReceiptSha256 !== digest(readFileSync(c.nativeSession.receiptFile)) ||
      permit.sourceReceiptSha256 !== digest(readFileSync(join(dirname(c.closure), c.localArtifact ? "local-build-evidence.json" : "receipt.json")))) throw new Error("full27 destructive admission differs");
  return executeGate(c, adapter, undefined, undefined, true, undefined, undefined, true);
}
export async function runGate(c: GateConfig, adapter: ProcessAdapter, review?: ReviewAuthority): Promise<any> {
  return executeGate(c, adapter, review);
}
export async function runRegression(c: GateConfig, adapter: ProcessAdapter, scenario: RegressionScenario, options: RegressionOptions = {}): Promise<any> {
  const deferred = regressionOptions(scenario, options);
  if (scenario !== "vehicle" && scenario !== "settings-radio" && scenario !== "launcher" && !installedSemanticRequired(scenario)) throw new Error("unknown fixed regression scenario");
  return executeGate(c, adapter, undefined, scenario, deferred, options.vehiclePart, options.settingsPart);
}
async function executeGate(c: GateConfig, adapter: ProcessAdapter, review?: ReviewAuthority, regression?: RegressionScenario, deferred = false, vehiclePart?: "pw" | "fev-drawer", settingsPart?: "postboot", fullSemantic = false): Promise<any> {
  c = validateConfig(c);
  const nativeConfig = c.nativeSession ? nativeSessionFromConfig(c) : null;
  const machine = nativeConfig ? { file: c.nativeSession!.receiptFile, sha256: digest(readFileSync(c.nativeSession!.receiptFile)), checks: [] } : prepared(c);
  const manifest = JSON.parse(readFileSync(join(c.closure, "voboost.json"), "utf8"));
  const catalog = JSON.parse(readFileSync(join(c.closure, "agents.json"), "utf8")).agents;
  if (
    !Array.isArray(catalog) ||
    JSON.stringify(
      catalog
        .filter((a: any) => a.status === "stable")
        .map((a: any) => a.id)
        .sort(),
    ) !== JSON.stringify([...STABLE].sort())
  )
    throw new Error("exact seven stable catalog required");
  const resourceDirectory = join(c.package, "Voboost Installer.app/Contents/Resources");
  const resources = macDeviceResourceInventory(resourceDirectory, inventory(resourceDirectory) as { path: string }[]);
  if (JSON.stringify(resources) !== JSON.stringify(inventory(c.closure)))
    throw new Error("closure differs from package resources");
  const screenshotNodeSha256 = digest(readFileSync(process.execPath));
  const frozen = {
    package: packageInventory(c),
    macOverlaySources: macResourceOverlaySourceIdentity(),
    closure: digest(JSON.stringify(inventory(c.closure))),
    dmg: digest(readFileSync(c.dmg)),
    machine: machine.sha256,
    cli: digest(readFileSync(nativeConfig ? nativeConfig.executable : join(c.voyemuRoot, "src/cli.ts"))),
  };
  mkdirSync(c.output, { mode: 0o700 });
  mkdirSync(join(c.output, "logs"), { mode: 0o700 });
  const evidence: any = {
    schemaVersion: nativeConfig ? 4 : 3,
    ...(nativeConfig ? { verificationContract } : {}),
    evidenceType: "emulator-functional",
    ...(deferred ? { imageCapture: "deferred", ...(fullSemantic ? { functionalMode: "full27" } : { regressionScenario: regression }) } : {}),
    ...(vehiclePart ? { vehiclePart } : {}),
    ...(settingsPart ? { settingsPart } : {}),
    invocationId: randomUUID(),
    releaseIdentity: manifest.releaseIdentity,
    publication: "forbidden",
    productionEvidence: false,
    vehicleEvidence: false,
    passed: false,
    identity: frozen,
    voyemuProfile: c.voyemuProfile,
    preparedCheckInventory: machine.checks.map(({ id, expected }: any) => ({
      id,
      expected: expected ?? null,
    })),
    runtime: null,
    leaseStatus: "retained",
    activeOperation: null,
    preparation: { status: "not-run", reason: "preparation not started", kind: null, actionId: null, bootId: null, context: null, actions: [], artifacts: [] },
    fixtureCleanup: { package: plan.fixture.package, source: plan.fixture.source, owned: false, status: "not-run", reason: "fixture not installed", originalAbsenceAction: null, installAction: null, removalAction: null, removalAttempted: false, removalBootId: null, bootId: null, artifacts: [] },
    cases: CASES.map((x) => ({
      number: x.number,
      id: x.id,
      status: "not-run",
      reason: "",
      actions: [],
      subchecks: [],
    })),
    actions: [],
    boots: [],
  };
  let runtime: VoyemuIdentity | null = null,
    serial = "",
    caseNumber = 0,
    language: Language = "en";
  const enabled = new Map<string, boolean>();
  let observerCompletion: Promise<unknown> | null = null;
  let monitor = Promise.resolve();
  let monitorResult: Error | null = null;
  let currentBoot: string | null = null;
  let faultLive: (() => void) | null = null;
  let snapshotBoot: string | null = null;
  let nativeInputTree: any = null;
  let copy: SettingsCopy | null = null;
  let copyFailure = "source-bound copy artifact was not generated";
  let startup = "interface";
  let initialLanguage: Language | null = null;
  let originalOemItems: string[] | null = null;
  let originalOemCapture: string[] = [];
  let fixtureOemItems: string[] | null = null;
  let firstPublishedFixture: ReturnType<typeof launcherFirstPublished> | null = null;
  let firstPublishedFixtureArtifact: string | null = null;
  let initialVehicle: Awaited<ReturnType<typeof observations>> | null = null;
  let initialCan: { fev: boolean; pw: boolean; artifacts: string[] } | null = null;
  let normalRemoval: { result: ProcessResult; boot: Boot; actionId: number; artifact: string } | null = null;
  let hostArtifacts: string[] = [];
  let hostFailure = "owning host gates were not executed";
  const installPreparation: Array<{ actionId: number; rows: ReturnType<typeof preparationReceipts>; artifacts: string[] }> = [];
  const fullSourceReceiptSha256 = (fullSemantic || c.localArtifact) ? digest(readFileSync(join(dirname(c.closure), c.localArtifact ? "local-build-evidence.json" : "receipt.json"))) : null;
  const fullSourceAdmission = async () => {
    if (c.localArtifact) {
      exactKeys(c.localArtifact, ["verificationDelta"], "local artifact admission");
      if (!Array.isArray(c.localArtifact.verificationDelta)) throw new Error("verification delta missing");
      if (digest(readFileSync(join(dirname(c.closure), "local-build-evidence.json"))) !== fullSourceReceiptSha256) throw new Error("local build evidence changed");
      return admitLocalArtifact(c.workspaceRoot, c.closure, c.dmg, c.localArtifact.verificationDelta);
    }
    const path = join(dirname(c.closure), "receipt.json"), receipt = JSON.parse(readFileSync(path, "utf8"));
    if (digest(readFileSync(path)) !== fullSourceReceiptSha256) throw new Error("full27 source receipt changed");
    const names = ["product", "app", "agents", "inject", "installer", "logo", "codestyle"];
    if (receipt.kind !== "source-build" || receipt.schemaVersion !== 1 || receipt.sources?.length !== names.length) throw new Error("full27 exact source-build receipt unavailable");
    for (const name of names) {
      const expected = receipt.sources.filter((row:any)=>row.name===name);
      const actual = await snapshotRepository(name, join(c.workspaceRoot, name === "product" ? "voboost" : "voboost-" + name));
      if (expected.length !== 1 || JSON.stringify(actual) !== JSON.stringify(expected[0])) throw new Error("full27 current source differs: " + name);
    }
    return { status: "passed", identity: frozen, receiptSha256: digest(readFileSync(path)) };
  };
  const proof = (id: string, status: Verdict, reason: string, artifacts: string[] = []) => {
    evidence.cases.find((row: Case) => row.number === caseNumber).subchecks.push({ id, status, reason, artifacts });
  };
  const prerequisite = (number: number, id: string, reason: string) => evidence.cases.find((row: Case) => row.number === number).subchecks.push({ id, status: "unavailable", reason, artifacts: [] });
  const subcheck = async (id: string, check: () => Promise<string[] | void>) => {
    if (fullSemantic && fullVisualOnly(caseNumber, id)) { proof(id, "unavailable", "Image-only acceptance deferred by explicit no-images mode"); return; }
    try { proof(id, "passed", "independent observations passed", (await check()) ?? []); }
    catch (error) {
      proof(id, error instanceof Unavailable ? "unavailable" : "failed", String(error));
      if (!(error instanceof Unavailable)) throw error;
    }
  };
  const persist = () =>
    writeFileSync(
      join(c.output, "emulator-runtime-evidence.json"),
      JSON.stringify(evidence, null, 2) + "\n",
      { mode: 0o600 },
    );
  const bootTiming = (event: string, details: Record<string, unknown> = {}) => {
    appendFileSync(join(c.output, "boot-timings.jsonl"), JSON.stringify({ event, caseNumber, monotonicNs: process.hrtime.bigint().toString(), ...details }) + "\n", { mode: 0o600 });
  };
  let firstBootCaptureDeadline: number | null = null;
  let nativeBootReadDeadline: number | null = null;
  let snapshotReadbackDeadline: number | null = null;
  let settingsNavigationDeadline: number | null = null;
  let bootBudgetPhase: "readiness" | "capture" = "readiness";
  let keyboardCaptureScope: {directory:string;rect:{x:number;y:number;width:number;height:number}} | null = null;
  const record = async (inv: Invocation) => {
    if (deferred && ((inv.args[0] === "--emulator-check" && JSON.parse(readFileSync(inv.args[1],"utf8")).action === "screenshot"))) throw new Error("image dispatch prohibited in deferred mode");
    if (firstBootCaptureDeadline !== null) {
      const remaining = firstBootCaptureDeadline - performance.now();
      if (remaining <= 0) throw new Unavailable(`boot ${bootBudgetPhase} budget exhausted`);
      inv.timeoutMs = nativeReadTransportTimeout(inv, remaining);
    }
    if (snapshotReadbackDeadline !== null) {
      const remaining = snapshotReadbackDeadline - performance.now();
      if (remaining <= 0) throw new Unavailable("snapshot readback budget exhausted");
      inv.timeoutMs = nativeReadTransportTimeout(inv, remaining);
    }
    if (settingsNavigationDeadline !== null) {
      const remaining = settingsNavigationDeadline - performance.now();
      if (remaining <= 0) throw new Unavailable("Settings navigation deadline expired");
      inv.timeoutMs = nativeReadTransportTimeout(inv, remaining);
    }
    const id = evidence.actions.length + 1;
    const stamp = process.hrtime.bigint().toString();
    const row: any = {
      id,
      caseNumber,
      startedMonotonicNs: stamp,
      finishedMonotonicNs: stamp,
      executable: inv.executable,
      args: inv.args,
      exitCode: null,
      signal: null,
      timedOut: false,
      overflow: false,
      artifacts: [],
    };
    if (nativeConfig && inv.executable === nativeConfig.executable && inv.args[0] === "test") {
      const file=inv.args[4]; regular(file);
      const bytes=readFileSync(file), request=JSON.parse(bytes.toString("utf8"));
      if (deferred && request.op === "session.screenshot" && !keyboardDiagnosticRequest(request,keyboardCaptureScope)) throw new Error("native image dispatch prohibited in deferred mode");
      row.nativeOperation=request.op;
      const retained=join(c.output,"native-request-"+id+".json");
      const redacted={...request,session:{id:request.session.id}};
      const publicBytes=Buffer.from(JSON.stringify(redacted)+"\n");
      writeFileSync(retained,publicBytes,{flag:"wx",mode:0o600});
      row.nativeRequest=retained;
      row.artifacts.push({path:retained,sha256:digest(publicBytes),kind:"request"});
    }
    if (inv.args[0] === "--emulator-check") {
      regular(inv.args[1]);
      row.artifacts.push({ path: inv.args[1], sha256: digest(readFileSync(inv.args[1])), kind: "request" });
    }
    evidence.actions.push(row);
    evidence.cases.find((x: Case) => x.number === caseNumber)?.actions.push(id);
    let r: ProcessResult;
    try {
      if (regression) assertRegressionInvocation(inv, regression);
      r = await adapter(inv);
    } catch (e) {
      r = { exitCode: null, stdout: "", stderr: String(e) };
    }
    if (Buffer.byteLength(r.stdout) + Buffer.byteLength(r.stderr) > MAX_CAPTURE) {
      r = {
        ...r,
        overflow: true,
        stdout: r.stdout.slice(-MAX_CAPTURE / 2),
        stderr: r.stderr.slice(-MAX_CAPTURE / 2),
      };
    }
    if (retainTimedOutDeviceOperation(inv, r) && !evidence.activeOperation) {
      evidence.activeOperation = {
        actionId: id,
        processId: r.processId ?? null,
        serial,
        status: "observation-timeout-not-released",
      };
    }
    row.finishedMonotonicNs = process.hrtime.bigint().toString();
    Object.assign(row, {
      exitCode: r.exitCode,
      signal: r.signal ?? null,
      timedOut: r.timedOut === true,
      overflow: r.overflow === true || r.retained?.overflow === true,
    });
    for (const stream of ["stdout", "stderr"] as const) {
      const file = join(c.output, `action-${id}-${stream}.txt`);
      writeFileSync(file, r[stream], { mode: 0o600 });
      row.artifacts.push({ path: file, sha256: digest(r[stream]), kind: stream });
    }
    persist();
    return { ...r, actionId: id };
  };
  const immutable = () => {
    if (
      packageInventory(c) !== frozen.package ||
      macResourceOverlaySourceIdentity() !== frozen.macOverlaySources ||
      digest(JSON.stringify(inventory(c.closure))) !== frozen.closure ||
      digest(readFileSync(c.dmg)) !== frozen.dmg ||
      (nativeConfig ? digest(readFileSync(c.nativeSession!.receiptFile)) : prepared(c).sha256) !== frozen.machine ||
      digest(readFileSync(nativeConfig ? nativeConfig.executable : join(c.voyemuRoot, "src/cli.ts"))) !== frozen.cli
    )
      throw new Error("immutable identity changed");
  };
  const native = nativeConfig ? new NativeSession(nativeConfig, join(dirname(c.output), basename(c.output) + "-private-native-requests"), record, (bytes,allowBlack)=>validatePng(bytes,allowBlack)) : null;
  const ownership = async () => {
    immutable();
    const current = native ? await native.status() : parseVoyemuStatus(await record(voyemuInvocation(c, "status")), c.voyemuProfile);
    if (!runtime || JSON.stringify(current) !== JSON.stringify(runtime))
      throw new Error("runtime lease identity changed");
  };
  const cli = async (args: string[], onOutput?: Invocation["onOutput"], stdinReply?: Invocation["stdinReply"]) => {
    if (evidence.activeOperation && !["--status", "--diagnostics", "--logs"].includes(args[0]))
      throw new Error("active transaction lease retained; further mutations forbidden");
    await ownership();
    return record({
      executable: join(c.package, "voboost-installer"),
      args: [...args, "--device", serial],
      timeoutMs: 1800000,
      onOutput,
      stdinReply,
    });
  };
  let initialSettingsNavigation = false;
  let installerActive = false;
  let installerRebootCapture = false;
  let installerMonitorStopped = false;
  const monitorLive = () => { if (installerMonitorStopped) throw new Unavailable("installer monitor stopped after terminal CLI failure"); };
  const probe = async (ownershipCheckedForThisAction = false) => {
    if (!ownershipCheckedForThisAction) await ownership();
    const file = join(c.output, `probe-${evidence.actions.length + 1}.json`);
    writeFileSync(file, JSON.stringify({ action: "boot-observation" }) + "\n", { flag: "wx", mode: 0o600 });
    const r = await record({
      ...driverInvocation(join(c.package, "voboost-installer"), file, serial, { timeoutMs: 90000 }),
    });
    if (installerRootPending(r, installerActive, "boot-observation")) {
      await ownership();
      throw new ObservationPending("active installer root observation is not ready");
    }
    if (offlineObservation(r)) throw new ObservationPending("closed Inspect transport temporarily offline");
    resultOk(r);
    const b = jsonReceipt(r.stdout);
    exactKeys(b, ["schemaVersion", "serial", "body"], "probe receipt");
    if (b.schemaVersion !== 1 || b.serial !== serial || typeof b.body !== "string")
      throw new Error("probe identity differs");
    const { boot } = parseBootObservation(b.body);
    return boot;
  };
  const uiAdapter: ProcessAdapter = async (inv) => {
    const request = JSON.parse(readFileSync(inv.args[1], "utf8"));
    if (evidence.activeOperation && !["inspect", "boot-observation", "resident-status", "snapshot", "screenshot"].includes(request.action))
      throw new Error("active transaction lease retained; UI mutation/capture forbidden");
    const normalSnapshot = request.action === "snapshot" && firstBootCaptureDeadline === null;
    if (normalSnapshot) snapshotReadbackDeadline = performance.now() + 180000;
    try {
      // Both observation requests already capture a boot; a second read can consume its window.
      if (initialSettingsNavigation) {
        if (request.action !== "open-settings" || currentBoot !== null || installerActive || evidence.activeOperation || faultLive)
          throw new Error("initial Settings navigation requires no host writer and no prior boot observation");
        await ownership();
      } else if (!["inspect", "boot-observation", "resident-status"].includes(request.action)) currentBoot = await probe(true);
      if (
        ["tap", "swipe", "drag", "gesture", "text", "tbox-burst"].includes(request.action) &&
        snapshotBoot !== currentBoot
      )
        throw new Error("fresh UI observation belongs to another boot");
      if (installerActive && request.action === "boot-observation") await ownership();
      if (["tap", "swipe", "drag", "gesture", "text", "key", "reboot", "tbox-burst"].includes(request.action)) faultLive?.();
      const invoke = async (remainingMs: number) => {
        const result = await record({ ...inv, timeoutMs: Math.min(inv.timeoutMs, remainingMs) });
        const action = evidence.actions.find((a: any) => a.id === result.actionId);
        action.context = { kit: frozen.package, runtime, serial, bootId: currentBoot, locale: language };
        return result;
      };
      const snapshotOwnerBoot = currentBoot;
      const guardSnapshot = async (first: boolean) => {
        // InstallerUi already checked ownership; reuse the initial bounded probe.
        if (first) return;
        await ownership();
        const boot = await probe(true);
        if (boot !== snapshotOwnerBoot) throw new Error("snapshot readback crossed a boot");
      };
      // First-boot capture already owns a guarded two-attempt phase; never nest retries.
      const r = normalSnapshot
        ? await snapshotReadback(request.action, invoke, guardSnapshot, serial, () => performance.now(), snapshotReadbackDeadline!)
        : await invoke(inv.timeoutMs);
      const row = evidence.actions.find((a: any) => a.id === r.actionId);
      if (installerClosedPending(r, request.action, serial, installerActive && installerRebootCapture && !installerMonitorStopped, firstBootCaptureDeadline, performance.now())) {
        await ownership();
        monitorLive();
        throw new ObservationPending("authorized installer reboot transport closed; failed action retained");
      }
      if (installerClosedPending(r, request.action, serial, !!native && nativeBootReadDeadline !== null, nativeBootReadDeadline, performance.now())) {
        await ownership();
        throw new ObservationPending("authorized native reboot transport closed; failed action retained");
      }
      if (installerRootPending(r, installerActive, request.action)) {
        await ownership();
        throw new ObservationPending("active installer root observation is not ready");
      }
      if (["inspect", "boot-observation"].includes(request.action) && offlineObservation(r)) throw new ObservationPending("closed Inspect transport temporarily offline");
      if (["inspect", "boot-observation", "resident-status"].includes(request.action) && r.exitCode === 0 && !r.timedOut && !r.signal && !r.overflow) {
        const receipt = jsonReceipt(r.stdout);
        exactKeys(receipt, ["schemaVersion", "serial", "body"], "Inspect receipt");
        if (receipt.schemaVersion !== 1 || receipt.serial !== serial || typeof receipt.body !== "string") throw new Error("Inspect identity differs");
        const observedBoot = /^bootId=(.*)$/m.exec(receipt.body)?.[1];
        if (!uuid(observedBoot)) throw new Error("Inspect boot identity differs");
        if (request.action === "boot-observation") parseBootObservation(receipt.body);
        if (request.action === "resident-status") parseResidentStatus(receipt.body);
        currentBoot = observedBoot;
      }
      row.context = { kit: frozen.package, runtime, serial, bootId: currentBoot, locale: language };
      if (request.action === "snapshot" && r.exitCode === 0) snapshotBoot = currentBoot;
      if (firstBootCaptureDeadline !== null && request.action === "snapshot" && uiWarmupDeadline(r, serial)) throw new UiWarmupDeadline("first-boot Accessibility metadata traversal deadline; failed action retained");
      return r;
    } finally { if (normalSnapshot) snapshotReadbackDeadline = null; }
  };
  let ui: InstallerUi;
  const inspect = async (preparation = false, required = false) => {
    const r = required ? await captureBootObservation(ui) : await ui.call({ action: "inspect" });
    if (typeof r.body !== "string") throw new Error("inspect body missing");
    const boot = /^bootId=(.*)$/m.exec(r.body)?.[1];
    if (!uuid(boot)) throw new Error("boot identity observation differs");
    if (/^enforcing=Permissive$/m.test(r.body)) throw new ObservationPending("closed Inspect has not yet attested Enforcing");
    if (!/^enforcing=Enforcing$/m.test(r.body)) throw new Error("Enforcing observation malformed");
    if (required) parseBootObservation(r.body);
    const operation = installOperation(r.body, boot, digest(readFileSync(join(c.closure, "voboost.json"))), preparation);
    const actionId = (ui.records.at(-1)?.result as ProcessResult)?.actionId;
    const action = evidence.actions.find((row: any) => row.id === actionId);
    return { boot, body: r.body, bootCompleted: /^bootCompleted=1$/m.test(r.body), rebootCount: operation?.rebootCount ?? null, actionId: actionId!, finishedMonotonicNs: action.finishedMonotonicNs };
  };
  const bootObservation = () => inspect(false, true);
  const nativeBootObservation = async (deadline: number) => {
    const previous = nativeBootReadDeadline;
    nativeBootReadDeadline = deadline;
    try { return await nativeBootReadback(bootObservation, ownership, () => new Promise(resolve => setTimeout(resolve, 100)), deadline); }
    finally { nativeBootReadDeadline = previous; }
  };
  const readLauncherTheme = async () => {
    const before=await bootObservation();
    await ui.call({action:"open-settings"});
    const tabs=await ui.snapshot();
    const tab=uniqueNode(tabs,n=>n.packageName===SETTINGS&&n.clickable&&n.enabled&&n.visibleToUser&&focusedApplication(n)&&/^(Display|显示|Экран|Дисплей)$/.test(n.text));
    await ui.tap(tab);
    const firstThemeSnapshot=evidence.actions.length;
    let nodes=await ui.snapshot();
    const seen=new Set<string>();
    for(let attempt=0;attempt<20;attempt++) {
      const lists=nodes.filter(n=>n.packageName===SETTINGS&&n.resource===SETTINGS+":id/recyclerview_theme_pic"&&n.enabled&&n.windowRef?.type===1&&n.windowRef.inputFocused&&n.windowRef.rootPackage===SETTINGS);
      if(lists.length!==1) throw new Unavailable("native Display theme list absent or ambiguous");
      const list=lists[0];
      const titles=nodes.filter(n=>n.packageName===SETTINGS&&n.resource===SETTINGS+":id/tv_title"&&["Classic","Speedy Dash"].includes(n.text)&&n.enabled&&n.windowRef===list.windowRef);
      const visible=(n:UiNode)=>n.visibleToUser===true&&n.bounds[2]>n.bounds[0]&&n.bounds[3]>n.bounds[1];
      const ready=titles.length===2&&titles.every(title=>{
        const card=nodes[title.parent!];
        return visible(title)&&card&&card.resource===SETTINGS+":id/rl_root"&&card.enabled&&card.clickable&&card.windowRef===list.windowRef&&visible(card);
      });
      if(ready) break;
      const scroll=uniqueNode(nodes,n=>n.packageName===SETTINGS&&n.resource===SETTINGS+":id/display_scroll"&&n.scrollable&&n.visibleToUser&&focusedApplication(n));
      const [l,t,r,b]=scroll.bounds, anchor=titles.find(n=>!visible(n))??list, raw=anchor.rawBounds??anchor.bounds;
      const up=raw[1]>=b,down=raw[3]<=t;
      if((!up&&!down)||list.windowRef!==scroll.windowRef) throw new Unavailable("theme list hidden without usable scroll direction");
      const signature=JSON.stringify([raw,scroll.bounds]);
      if(seen.has(signature)||attempt===19) throw new Unavailable("native theme list bounded seek stalled");
      seen.add(signature);
      const distance=up?raw[1]-b:t-raw[3],height=b-t,step=Math.floor(height*(distance<height*.5?.18:.4)),x=Math.floor(l+Math.min(16,(r-l)*.01)),mid=Math.floor((t+b)/2);
      await ui.call({action:"swipe",x1:x,x2:x,y1:Math.floor(mid+(up?step/2:-step/2)),y2:Math.floor(mid+(up?-step/2:step/2)),milliseconds:400,...ui.gestureAuthority(scroll)});
      nodes=await ui.snapshot();
    }
    const theme=selectedLauncherTheme(nodes);
    const action=evidence.actions.slice(firstThemeSnapshot).findLast((a:any)=>a.nativeOperation==="session.ui" || (a.args[0]==="--emulator-check" && JSON.parse(readFileSync(a.args[1],"utf8")).action==="snapshot"));
    const artifact=launcherThemeSnapshotProof(evidence,action,nodes,before.boot);
    const after=await bootObservation();
    if(before.boot!==after.boot || !before.bootCompleted || !after.bootCompleted) throw new Error("selected launcher theme snapshot boot differs");
    return {theme,boot:before.boot,actionId:action.id,artifact:{path:artifact.path,sha256:artifact.sha256}};
  };
  const selectLauncherTheme = async (label: "Classic" | "Speedy Dash") => {
    const before = await readLauncherTheme();
    if (before.theme.label !== label) {
      const nodes = await ui.snapshot();
      const title = uniqueNode(nodes, n => n.packageName === SETTINGS && n.resource === SETTINGS + ":id/tv_title" && n.text === label && n.enabled && focusedApplication(n));
      const card = nodes[title.parent!];
      if (!card || card.resource !== SETTINGS + ":id/rl_root" || !card.clickable || !card.visibleToUser || card.windowRef !== title.windowRef) throw new Unavailable("requested native theme card is not safely tappable");
      await ui.tap(card);
    }
    const selected = await readLauncherTheme();
    if (selected.boot !== before.boot || selected.theme.label !== label) throw new Error("native theme selection readback differs");
    return selected;
  };
  const screenshot = async (name: string, display: "primary" | "secondary" = "primary") => {
    if (deferred) return null;
    if (native) {
      await ownership();
      const before = await bootObservation();
      const source = join(c.voyemuRoot, ".local/build/tmp", "voboost-capture-" + randomUUID());
      const bundle = await native.capture(source);
      const action = evidence.actions.findLast((a:any)=>a.nativeOperation==="session.screenshot");
      const after = await bootObservation();
      if (!before.bootCompleted || !after.bootCompleted || before.boot !== after.boot || bundle.manifest.android_boot_id !== before.boot) throw new Error("native screenshot crossed guest boot");
      const target = join(c.output, "native-captures", String(action.id) + "-" + name);
      mkdirSync(target, {recursive:true,mode:0o700});
      const manifest = bundle.manifest;
      const files = ["manifest.json", ...manifest.frames.map((f:any)=>f.file)];
      for (const file of files) {
        const sourceFile = join(source,file), stat=lstatSync(sourceFile);
        if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 32*1024*1024 || realpathSync(sourceFile) !== sourceFile) throw new Error("native retained source file unsafe");
        const bytes = readFileSync(sourceFile), dest=join(target,file);
        writeFileSync(dest, bytes, {flag:"wx",mode:0o600});
        action.artifacts.push({path:dest,sha256:digest(bytes),kind:file.endsWith(".png")?"png":"archive",boot:before.boot,locale:language,kit:frozen.package,runtime});
      }
      action.context={kit:frozen.package,runtime,serial,bootId:before.boot,locale:language};
      action.nativeOperation="session.screenshot";
      const responseArtifact = action.artifacts.find((a:any)=>a.kind==="stdout");
      const wire=JSON.parse(readFileSync(responseArtifact.path,"utf8"));
      const image = join(target, String(action.id) + "-" + name + ".png");
      const selected=join(target,display==="primary"?"primary.png":"secondary.png");
      const imageBytes=readFileSync(selected);writeFileSync(image,imageBytes,{flag:"wx",mode:0o600});
      action.artifacts.push({path:image,sha256:digest(imageBytes),kind:"png",boot:before.boot,locale:language,kit:frozen.package,runtime});
      action.nativeAuthority={requestId:wire.request_id,operationId:wire.operation_id,sessionId:manifest.session_id,manifest:join(target,"manifest.json"),output:image,display};
      persist();
      return image;
    }
    const shot = await ui.screenshot(name, display, display === "secondary" ? {
      root: c.voyemuRoot, node: process.execPath, profile: c.voyemuProfile,
      runtimePid: runtime.pid, runtimePort: runtime.port,
      nodeSha256: screenshotNodeSha256, cliSha256: frozen.cli,
    } : undefined);
    evidence.actions
      .findLast(
        (a: any) =>
          a.args[0] === "--emulator-check" &&
          JSON.parse(readFileSync(a.args[1], "utf8")).output === shot.file,
      )
      .artifacts.push({
        path: shot.file,
        sha256: shot.sha256,
        kind: "png",
        boot: (await bootObservation()).boot,
        locale: language,
        kit: frozen.package,
        runtime,
      });
    return shot.file;
  };
  const fullVerify = async () => {
    await ownership();
    if (native) { await native.waitAndroid(); const action=evidence.actions.at(-1); action.nativeOperation="session.wait"; persist(); return; }
    const r = await record(voyemuInvocation(c, "verify"));
    verifyVoyemu(r, c.voyemuProfile, machine.checks);
  };
  const absentStatus = async () => {
    const result = await cli(["--status"]);
    resultOk(result);
    const status = jsonReceipt(result.stdout);
    if (status.route !== "absent" || status.deviceSerial !== serial || status.installerVersion !== manifest.releaseIdentity)
      throw new Error("clean absence status prerequisite differs");
  };
  const absenceReadback = async (boot: string, name: string) => {
    const before = await bootObservation();
    if (before.boot !== boot || !before.bootCompleted) throw new Error("absence readback boot differs or is incomplete");
    await absentStatus();
    await fullVerify();
    const after = await bootObservation();
    if (after.boot !== boot || !after.bootCompleted) throw new Error("absence verification crossed a boot");
    await ui.call({ action: "key", key: "home" });
    await ui.call({ action: "open-settings" });
    await screenshot(name);
    await ui.snapshot();
    const captured = await bootObservation();
    if (captured.boot !== boot || !captured.bootCompleted) throw new Error("absence UI capture crossed a boot");
  };
  const bootReadinessBudgetMs = () => native ? 600000 : voyemuInvocation(c, "verify").timeoutMs;
  const bootTransitions = new BootTransition();
  const initialObservation = new InitialObservation();
  const bootCheck = async (kind: string, before: string | null, exactAfter?: string) => {
    const row: Boot = {
      id: `boot-${evidence.boots.length + 1}`,
      caseNumber,
      kind,
      status: "unavailable",
      reason: "capture pending",
      before,
      after: null,
      verifiedBoot: null,
      actions: [],
      screenshot: null,
    };
    evidence.boots.push(row);
    bootTiming("boot-check-start", { kind, before });
    const first = evidence.actions.length;
    // Readiness includes the existing bounded Voyemu verify contract, not UI capture.
    bootBudgetPhase = "readiness";
    const readinessDeadline = performance.now() + (native ? 600000 : voyemuInvocation(c, "verify").timeoutMs);
    firstBootCaptureDeadline = readinessDeadline;
    installerRebootCapture = /^installer-[0-9]+$/.test(kind);
    try {
      const pre = /^installer-[0-9]+$/.test(kind)
        ? await installerBootObservation(Number(kind.slice(10)), before, bootObservation, () => new Promise((resolve) => setTimeout(resolve, 1000)), (observed) => {
          // Retain a real incomplete boot identity; its missing verification remains unavailable.
          row.after = observed.boot;
          row.observation = { actionId: observed.actionId, finishedMonotonicNs: observed.finishedMonotonicNs, rebootCount: observed.rebootCount };
          persist();
        })
        : await (native ? nativeBootReady(() => nativeBootObservation(readinessDeadline), before, exactAfter, ownership, () => new Promise(resolve => setTimeout(resolve, 100)), readinessDeadline) : bootObservation());
      row.after = pre.boot;
      row.observation = { actionId: pre.actionId, finishedMonotonicNs: pre.finishedMonotonicNs, rebootCount: pre.rebootCount };
      if (!pre.bootCompleted) throw new Unavailable("guest boot completion was not observed");
      if (/^installer-[0-9]+$/.test(kind) && pre.rebootCount !== Number(kind.slice(10)))
        throw new Unavailable("installer durable phase does not identify this intermediate boot");
      if (!before || pre.boot === before || (exactAfter && pre.boot !== exactAfter))
        throw new Unavailable("new boot was not independently captured");
      let post = pre;
      for (let attempt = 1; ; attempt++) {
        if (performance.now() >= readinessDeadline) throw new Unavailable("boot verification readiness deadline expired");
        if (/^installer-[0-9]+$/.test(kind) || kind === "cache-recovery") monitorLive();
        await ownership();
        const fence = await (native ? nativeBootObservation(readinessDeadline) : bootObservation());
        if (fence.boot !== pre.boot || !fence.bootCompleted || (/^installer-[0-9]+$/.test(kind) && fence.rebootCount !== pre.rebootCount))
          throw new Unavailable("verify crossed an installer/guest reboot boundary");
        bootTiming("verify-start", { kind, bootId: pre.boot, attempt });
        if (native) {
          await native.waitAndroid();
          const action=evidence.actions.at(-1); action.nativeOperation="session.wait";
          post=await nativeBootObservation(readinessDeadline);
          if(post.boot!==pre.boot || !post.bootCompleted || (/^installer-[0-9]+$/.test(kind) && post.rebootCount!==pre.rebootCount)) throw new Unavailable("native readiness crossed guest boot");
          bootTiming("native-readiness-complete",{kind,bootId:pre.boot,attempt});
          break;
        }
        const r = await record(voyemuInvocation(c, "verify"));
        // Do not mask a timed-out verify with the next exhausted-deadline probe.
        if (r.timedOut) throw new Unavailable("boot verification readiness process timed out");
        post = await bootObservation();
        if (post.boot !== pre.boot || !post.bootCompleted || (/^installer-[0-9]+$/.test(kind) && post.rebootCount !== pre.rebootCount))
          throw new Unavailable("verify crossed an installer/guest reboot boundary");
        try {
          verifyVoyemu(r, c.voyemuProfile, machine.checks);
          bootTiming("verify-complete", { kind, bootId: pre.boot, attempt });
          break;
        } catch (error) {
          if (!secondScreenStarting(r, c.voyemuProfile, machine.checks)) throw error;
          bootTiming("verify-second-screen-starting", { kind, bootId: pre.boot, attempt });
        }
      }
      row.verifiedBoot = post.boot;
      // Only this closed completed reboot path arms recovery; ordinary probe() never does.
      if (native) {
        await ownership();
        bootTransitions.arm(before!, { sessionId: runtime!.sessionId, pid: runtime!.pid, bootId: post.boot, profile: c.voyemuProfile });
      }
      // Capture owns its own finite budget only after verified same-boot readiness.
      bootBudgetPhase = "capture";
      const captureDeadline = performance.now() + 180000;
      firstBootCaptureDeadline = captureDeadline;
      bootTiming("ui-capture-budget-start", { kind, bootId: post.boot, budgetMs: 180000 });
      if (!/^installer-[0-9]+$/.test(kind) && kind !== "cache-recovery") {
        await ui.call({ action: "key", key: "home" });
        await ui.call({ action: "open-settings" });
      }
      const guard = async () => {
        if (/^installer-[0-9]+$/.test(kind) || kind === "cache-recovery") monitorLive();
        await ownership();
        const captured = await bootObservation();
        if (captured.boot !== post.boot || !captured.bootCompleted || (/^installer-[0-9]+$/.test(kind) && captured.rebootCount !== pre.rebootCount))
          throw new Unavailable("UI capture crossed a reboot");
      };
      const capture = async (attempt: number) => {
        if (/^installer-[0-9]+$/.test(kind) || kind === "cache-recovery") monitorLive();
        bootTiming("ui-capture-start", { kind, bootId: post.boot, attempt });
        try {
          const home = async () => {
            const value = await native!.call("session.status");
            primaryHome(value, nativeConfig!.session, runtime!, post.boot);
            const action = evidence.actions.findLast((a:any) => a.nativeOperation === "session.status");
            action.context = { kit:frozen.package, runtime, serial, bootId:post.boot, locale:language };
            persist();
            return action.id as number;
          };
          const nativeHome = bootPrimaryHomeProof(native !== null, kind, deferred);
          const beforeHome = nativeHome ? await home() : null;
          row.screenshot = await screenshot(attempt === 1 ? row.id : `${row.id}-retry-${attempt}`);
          if (nativeHome) {
            const captureAction = evidence.actions.findLast((a:any) => a.nativeOperation === "session.screenshot");
            primaryHomeCaptureBoot(nativeCaptureAction(evidence, captureAction), post.boot);
            row.homeProof = { before: beforeHome!, capture: captureAction.id, after: await home() };
          } else {
            const nodes = await ui.snapshot();
            if (kind === "installer-1" && nodes.some(node => node.windowRef?.type === 1 && (node.windowRef.active || node.windowRef.inputFocused) && node.windowRef.rootPackage !== "com.qinggan.app.launcher"))
              throw new Error("first-boot UI foreground owner differs");
          }
        } finally {
          bootTiming("ui-capture-complete", { kind, bootId: post.boot, attempt });
        }
      };
      if (kind === "installer-1") {
        firstBootCaptureDeadline = captureDeadline;
        try {
          await captureFirstBoot(capture, guard, () => performance.now() < captureDeadline - 45000);
        } finally { firstBootCaptureDeadline = null; }
      } else { await capture(1); await guard(); }
      if (deferred) { row.semanticStatus = "passed"; row.status = "unavailable"; row.reason = "Readiness, Enforcing, boot identity and fresh UI hierarchy passed; image capture deferred"; }
      else { row.status = "passed"; row.reason = native ? "new boot, owned native session readiness, Enforcing and fresh UI capture" : "new boot, exact prepared inventory, Enforcing and fresh UI capture"; }
    } catch (e) {
      row.reason = String(e);
      row.status =
        e instanceof Unavailable ||
        /owns the selected device transaction|offline|closed/i.test(String(e)) ||
        (/^installer-[0-9]+$/.test(kind) && String(e).endsWith("differs: boot"))
          ? "unavailable"
          : "failed";
      if (row.status === "failed") monitorResult = e as Error;
    } finally {
      installerRebootCapture = false;
      firstBootCaptureDeadline = null;
      row.actions = evidence.actions.slice(first).map((a: any) => a.id);
      bootTiming("boot-check-complete", { kind, bootId: row.after, status: row.status, reason: row.reason });
      persist();
    }
    return row;
  };
  const reboot = async () => {
    const pre = await bootObservation();
    const r = native ? await (async () => {
      if (installerActive || evidence.activeOperation) throw new Error("active transaction lease retained; native reboot forbidden");
      faultLive?.();
      let result: any;
      try { result = await native.reboot("warm"); }
      finally {
        const action = evidence.actions.at(-1);
        if (action?.nativeOperation !== "multimedia.reboot") throw new Error("native reboot action correlation differs");
        action.context = { kit: frozen.package, runtime, serial, bootId: pre.boot, locale: language };
        persist();
      }
      const receipt = nativeRebootReceipt(result, { sessionId: runtime!.sessionId, pid: runtime!.pid, boot: pre.boot });
      // The reboot receipt proves a new boot, not product readiness or Enforcing.
      const observed = await nativeBootObservation(performance.now() + bootReadinessBudgetMs());
      if (observed.boot !== receipt.after) throw new Error("native reboot post-observation crossed a boot");
      return { bootIdBefore: receipt.before, bootIdAfter: receipt.after, enforcing: true };
    })() : await ui.reboot();
    if (
      r.bootIdBefore !== pre.boot ||
      !uuid(r.bootIdAfter) ||
      r.bootIdAfter === pre.boot ||
      r.enforcing !== true
    )
      throw new Error("reboot identity receipt differs");
    const b = await bootCheck("functional", pre.boot, r.bootIdAfter as string);
    if (b.status !== "passed" && !(deferred && b.status === "unavailable" && b.semanticStatus === "passed" && b.screenshot === null)) throw new Error(b.reason);
  };
  const uninstall = async () => {
    installerMonitorStopped = false;
    let pending = "", seen = false;
    let work = Promise.resolve();
    const result = await cli(["--uninstall", "--emulator-cache-recovery-acceptance"], undefined, async (stream, chunk) => {
      if (stream !== "stderr") return null;
      pending += chunk;
      if (Buffer.byteLength(pending) > 65536) throw new Error("recovery acceptance stderr line exceeds bound");
      const lines = pending.split(/\r?\n/); pending = lines.pop()!;
      const events = lines.filter(line => line.startsWith("VBI_CACHE_RECOVERY_BOOT|"));
      if (!events.length) return null;
      if (seen || events.length !== 1) throw new Error("recovery acceptance event repeated");
      seen = true;
      const identity = cacheRecoveryBootEvent(events[0]);
      let reply = "";
      work = (async () => {
        reply = await recoveryBootAck(identity, {
          ownership,
          ...(fullSemantic ? { functionalMode: "full27" as const } : {}),
          verify: () => bootCheck("cache-recovery", identity.priorBootId, identity.bootId),
          current: bootObservation,
        });
      })();
      await work;
      return reply;
    });
    if (result.exitCode !== 0 || result.signal || result.timedOut || result.overflow || result.retained?.overflow) installerMonitorStopped = true;
    await work.catch(() => {});
    if (monitorResult) throw monitorResult;
    return result;
  };
  const install = async () => {
    const before = (await bootObservation()).boot;
    const manifestSha256 = digest(readFileSync(join(c.closure, "voboost.json")));
    let text = "", armed: InstallAcceptance | null = null;
    const progress = installerRebootProgress();
    const seen = new Set<number>();
    installerActive = true;
    installerMonitorStopped = false;
    let result: ProcessResult;
    try { result = await cli(["--install", "--eula=yes", "--emulator-install-acceptance"], (stream, chunk) => {
      let reboots: number[];
      try {
        if (stream === "stderr") reboots = progress(chunk);
        else {
          text = (text + chunk).slice(-MAX_CAPTURE);
          // Only complete lines are parsed; chunks may split the typed prearmed identity.
          const complete = text.slice(0, text.lastIndexOf("\n") + 1);
          const identity = installAcceptanceArmed(complete, manifestSha256);
          if (identity) {
            if (armed && JSON.stringify(armed) !== JSON.stringify(identity)) throw new Error("install acceptance identity changed");
            armed = identity;
          }
          // Retained pre-JSONL installer progress remains a closed historical input.
          reboots = [...complete.matchAll(/Restarting the head unit \(reboot (\d+)\)/g)].map(match => Number(match[1]));
        }
      } catch (error) {
        monitorResult = error as Error;
        monitor = Promise.reject(error);
        monitor.catch(() => {});
        return;
      }
      for (const n of reboots) {
        if (seen.has(n)) continue;
        seen.add(n);
        const acceptance = armed;
        bootTiming("installer-reboot-progress", { rebootCount: n });
        monitor = monitor.then(async () => {
          monitorLive();
          if (!acceptance) throw new Error("install reboot preceded armed acceptance identity");
          const previous = evidence.boots.findLast(
            (b: Boot) => b.caseNumber === caseNumber && b.kind === `installer-${n - 1}`,
          );
          const row = await bootCheck(`installer-${n}`, n === 1 ? before : (previous?.after ?? null));
          monitorLive();
          if (n !== 1) return;
          if (!row.after) {
            try { await rejectFailedInstall(acceptance, before, bootObservation, request => ui.call(request)); }
            catch (error) { throw new Error("install observation failed; negative acceptance unavailable; writer remains bounded by native deadline", { cause: error }); }
            throw new Error(`install acceptance rejected after failed boot observation: ${row.reason}`);
          }
          const request = { ...acceptance, bootId: row.after, rebootCount: 1 as const, decision: row.status === "passed" || (fullSemantic && semanticBoot(row)) ? "accept" as const : "reject" as const };
          if (row.status !== "passed" && !(fullSemantic && semanticBoot(row))) {
            try { await ui.call(installAcceptanceAction(request)); } catch {}
            throw new Error(`install acceptance rejected: ${row.reason}`);
          }
          // Status is independently correlated before consumption; command success alone is never acceptance.
          try {
            const statusRequest = { ...request, decision: "status" as const };
            const waiting = installAcceptanceReceipt((await ui.call(installAcceptanceAction(statusRequest))).body, statusRequest);
            if (waiting.state !== "waiting") throw new Error("install acceptance was not waiting after verification");
            monitorLive();
            const accepted = installAcceptanceReceipt((await ui.call(installAcceptanceAction(request))).body, request);
            if (accepted.state !== "accepted" || accepted.expiresMonotonicMs !== waiting.expiresMonotonicMs) throw new Error("install acceptance consumption differs");
            bootTiming("install-acceptance-consumed", { bootId: row.after, transactionId: acceptance.transactionId });
          } catch (error) {
            if (!installerMonitorStopped) { try { await ui.call(installAcceptanceAction({ ...request, decision: "reject" })); } catch {} }
            throw error;
          }
        });
        // Preserve rejection without an unhandled promise while the install child is completing.
        monitor.catch(error => { monitorResult = error; });
      }
    });
    } catch (error) {
      installerMonitorStopped = true;
      await monitor.catch(() => {});
      throw error;
    } finally {
      // The child and its progress monitor share one lifecycle. No monitor may
      // navigate or send acceptance after this function returns to finalization.
      if (result! && (result.exitCode !== 0 || result.signal || result.timedOut || result.overflow || result.retained?.overflow)) installerMonitorStopped = true;
      await monitor.catch(() => {});
      installerActive = false;
    }
    if (monitorResult) {
      try { resultOk(result); }
      catch (error) { throw new Error(`${monitorResult}; installer child: ${error}`, { cause: monitorResult }); }
      throw monitorResult;
    }
    resultOk(result);
    if (!armed) throw new Error("install acceptance armed receipt absent");
    const rows = preparationReceipts(result, serial, digest(readFileSync(join(c.closure, "voboost.json"))));
    installPreparation.push({ actionId: result.actionId!, rows, artifacts: evidence.actions.find((action: any) => action.id === result.actionId).artifacts.map((artifact: any) => artifact.path) });
    if (
      !result.stdout.includes(
        `Install completed: transaction ${armed.transactionId}`,
      )
    )
      throw new Error("install completion identity differs");
    await monitor;
    const final = await bootCheck("installer-terminal", before);
    if (final.status !== "passed" && !(fullSemantic && semanticBoot(final))) throw new Error(final.reason);
    const completed = await bootObservation();
    if (completed.rebootCount === null || completed.rebootCount < 1 || completed.rebootCount > 16) {
      evidence.boots.push({
        id: `boot-${evidence.boots.length + 1}`,
        caseNumber,
        kind: "installer-boot-count-unavailable",
        status: "unavailable",
        reason: "current durable install document does not expose bounded reboot count",
        before,
        after: completed.boot,
        verifiedBoot: null,
        actions: [],
        screenshot: null,
      });
    } else
      for (let n = 1; n <= completed.rebootCount; n++)
        if (!seen.has(n)) {
          evidence.boots.push({
            id: `boot-${evidence.boots.length + 1}`,
            caseNumber,
            kind: `installer-${n}`,
            status: "unavailable",
            reason:
              "durable reboot count identifies this boundary, but progress window was not independently captured",
            before: null,
            after: null,
            verifiedBoot: null,
            actions: [],
            screenshot: null,
          });
        }
    const readStatus = async () => {
      const result = await cli(["--status"]);
      resultOk(result);
      const status = jsonReceipt(result.stdout);
      if (status.deviceSerial !== serial || status.installedVersion !== manifest.releaseIdentity || status.contentRelation !== "same") throw new Error("installed status differs");
      return status;
    };
    const initial = await readStatus();
    if (initial.health === "healthy") return;
    if (initial.health !== "degraded" || initial.diagnostic !== "resident status: status.json contains an unhealthy selected injection" || initial.daemonStatus !== "running" || initial.startupMode !== startup || !/^[a-f0-9]{64}$/.test(initial.activeGeneration)) throw new Error("installed status differs");
    const deadline = performance.now() + 10000;
    for (let attempt = 0; attempt < 20 && performance.now() < deadline; attempt++) {
      await ownership();
      const receipt = await ui.call({ action: "resident-status" });
      const observed = parseResidentStatus(receipt.body);
      await ownership();
      if (performance.now() >= deadline) throw new Error("post-install readiness exhausted");
      if (observed.boot !== completed.boot || !observed.bootCompleted) throw new Error("post-install readiness boot differs");
      const residentDocument = observed.body.split("\nresident-status:\n/data/voboost/status.json\n")[1];
      if (!residentDocument || !/^[^\r\n]+\n\n$/.test(residentDocument)) throw new Error("post-install resident status document envelope differs");
      const resident = inspectionResident(observed.body);
      residentStatusShape(resident);
      if (resident.schemaVersion !== 1 || resident.releaseIdentity !== manifest.releaseIdentity || resident.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || resident.generationId !== initial.activeGeneration || resident.catalogSha256 !== digest(readFileSync(join(c.closure, "agents.json"))) || resident.startupMode !== startup || resident.state !== "ready" || resident.killed !== false || resident.panic !== false || !Array.isArray(resident.injections) || !resident.injections.length) throw new Error("post-install resident authority differs");
      if (!Array.isArray(resident.executors) || resident.executors.length !== 1 || resident.executors.some((row: any) => !row || row.id !== "ui-qinggan" || JSON.stringify(row.processes) !== JSON.stringify([SETTINGS]) || JSON.stringify(row.surfaces) !== JSON.stringify(["settings"]) || Object.keys(row).sort().join(",") !== "id,processes,surfaces")) throw new Error("post-install executors malformed");
      let pending = false;
      const identities = new Set<string>();
      for (const row of resident.injections) {
        if (!STABLE.includes(row.id) || !/^[A-Za-z][A-Za-z0-9_.:-]{0,254}$/.test(row.process)) throw new Error("post-install injection malformed");
        const identity = `${row.id}:${row.process}`;
        if (identities.has(identity)) throw new Error("post-install injection duplicated");
        identities.add(identity);
        if (row.state === "active" && row.reason === "ready" || row.state === "waiting" && row.reason === "target-waiting" || row.state === "skipped-coexist" && row.reason === "foreign-footprint") continue;
        if (row.state === "waiting" && ["owned-worker-waiting", "native-init-pending"].includes(row.reason)) { pending = true; continue; }
        throw new Error("post-install injection hard fault");
      }
      if (pending) continue;
      const final = await readStatus();
      const confirmed = await bootObservation();
      if (confirmed.boot !== completed.boot || !confirmed.bootCompleted) throw new Error("post-install readiness boot differs");
      if (performance.now() >= deadline) throw new Error("post-install readiness exhausted");
      if (final.health !== "healthy" || final.activeGeneration !== initial.activeGeneration || final.startupMode !== startup || final.daemonStatus !== "running") throw new Error("installed status differs after readiness");
      return;
    }
    throw new Error("post-install readiness exhausted");
  };
  const settingsForeground = async (nodes: UiNode[], operation: string) => {
    try { settingsTab(nodes); }
    catch (error) {
      await screenshot(`foreground-failure-${caseNumber}`);
      throw new Error(`${operation}: ${String(error)}`);
    }
  };
  const open = async () => {
    // One existing snapshot readback budget encloses navigation and recovery.
    const previousDeadline = settingsNavigationDeadline;
    const deadline = Math.min(performance.now() + 180000, firstBootCaptureDeadline ?? Infinity, previousDeadline ?? Infinity);
    settingsNavigationDeadline = deadline;
    let reads = 0;
    const snapshot = async () => {
      if (++reads > 32 || performance.now() >= deadline) throw new Error("Settings navigation read/deadline budget exhausted");
      return ui.snapshot();
    };
    try {
    const before = await bootObservation();
    if (!before.bootCompleted) throw new Error("Settings navigation requires completed boot");
    if (native) {
      let system;
      try { system = await native.system({ timeout_ms: 5000 }); }
      catch { throw new Unavailable("Settings navigation power preflight unavailable"); }
      const power = system.value?.power;
      const state = { boot: system.boot_id, valueBoot: system.value?.boot_id,
        status: power?.status, complete: power?.complete, displayReady: power?.display_ready,
        wakefulness: power?.wakefulness, display: power?.display_state };
      if (system.boot_id !== before.boot || system.value?.boot_id !== before.boot ||
          power?.status !== "ready" || power.complete !== true || power.display_ready !== true ||
          power.wakefulness !== "Awake" || power.display_state !== "ON") {
        throw new Unavailable("Settings navigation power preflight rejected: " + JSON.stringify(state));
      }
    }
    let recovered = false;
    const sameBoot = async () => {
      await ownership();
      const current = await bootObservation();
      if (!current.bootCompleted || current.boot !== before.boot) throw new Error("Settings navigation crossed boot");
      if (performance.now() >= deadline) throw new Error("Settings navigation deadline expired");
    };
    const recoverPrimaryFocus = async (nodes: UiNode[]) => {
      const selected = nodes.filter(n => n.packageName === SETTINGS && n.text === "Voboost" && n.clickable && n.checked && n.windowRef?.type === 1 && n.windowRef.rootPackage === SETTINGS && !n.windowRef.active && !n.windowRef.inputFocused);
      if (selected.length !== 1) throw new Error("unfocused selected Voboost pane unavailable");
      if (recovered) throw new Error("Settings primary focus recovery already consumed");
      await sameBoot();
      const owner = await inspect();
      const focuses = owner.body.split("\n").filter(line => /^  mCurrentFocus=/.test(line));
      if (owner.boot !== before.boot || !owner.bootCompleted || focuses.length !== 1 || !/^  mCurrentFocus=Window\{[0-9a-f]+ u0 com\.qinggan\.app\.launcher\/com\.qinggan\.secondlauncher\.activity\.SecondMainActivity\}$/.test(focuses[0])) throw new Error("Settings navigation foreground owner is not the known secondary launcher");
      await sameBoot();
      recovered = true;
      await ui.call({ action: "key", key: "home" });
      await ui.call({ action: "open-settings" });
      await sameBoot();
    };
    let current = await snapshot();
    if(current.some(n=>n.packageName==="com.qinggan.app.launcher" && n.resource==="com.qinggan.app.launcher:id/force_ev_switcher" && n.visibleToUser===true)) {
      const target=drawerCloseTarget(current);
      const closedWindow=target.node.windowRef!.id;
      await ui.call({action:"gesture",x1:target.x,y1:target.y,x2:target.x2,y2:target.y,milliseconds:target.milliseconds,...ui.gestureAuthority(target.node,true)});
      const closeDeadline=Math.min(deadline,performance.now()+10000);
      while(performance.now()<closeDeadline) {
        current=await ui.snapshot();
        if(!(current as UiNodes).windowRefs?.some(w=>w.id===closedWindow && w.rootPackage==="com.qinggan.app.launcher")) break;
      }
      if((current as UiNodes).windowRefs?.some(w=>w.id===closedWindow && w.rootPackage==="com.qinggan.app.launcher"))
        throw new Unavailable("Settings navigation drawer did not close after accepted gesture");
      await sameBoot();
    }
    if (selectedSettingsTab(current)) { await sameBoot(); return; }
    const selectedUnfocused = current.some(n => n.packageName === SETTINGS && n.text === "Voboost" && n.checked && n.windowRef?.type === 1 && !n.windowRef.active && !n.windowRef.inputFocused);
    if (selectedUnfocused) await recoverPrimaryFocus(current);
    else await ui.call({ action: "open-settings" });
    for (let i = 0; i < 32 && reads < 32 && performance.now() < deadline; i++) {
      // Root boot probes invalidate InstallerUi tokens: guard before the fresh UI read.
      await sameBoot();
      const nodes = await snapshot();
      current = nodes;
      const hiddenMenu = hiddenVoboostMenuScroll(nodes);
      if (hiddenMenu) {
        const [l, t, r, b] = hiddenMenu.bounds;
        const x = Math.floor((l + r) / 2);
        await ui.call({ action: "swipe", x1: x, x2: x, y1: Math.floor(t + (b - t) * 0.15), y2: Math.floor(t + (b - t) * 0.85), milliseconds: 400, ...ui.gestureAuthority(hiddenMenu) });
        await sameBoot();
        continue;
      }
      const tab = nodes.filter(n => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost" && n.clickable && n.visibleToUser === true);
      if (tab.length === 1) {
        if (!tab[0].checked) await ui.tap(tab[0]);
        current = await snapshot();
        if (selectedSettingsTab(current)) { await sameBoot(); return; }
        // Input delivery is not selection completion. Allow bounded read-only
        // readback while the focused pane changes; never replay the tap.
        while (reads < 32 && performance.now() < deadline && !selectedSettingsTab(current)) {
          const unfocusedSelection = current.some(n => n.packageName === SETTINGS && n.text === "Voboost" && n.checked && n.windowRef?.type === 1 && !n.windowRef.active && !n.windowRef.inputFocused);
          if (unfocusedSelection) break;
          const pendingTab = current.filter(n => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost" && n.clickable);
          if (pendingTab.length !== 1) throw new Error("Settings selection lost focused Voboost tab");
          await sameBoot();
          current = await snapshot();
        }
        if (selectedSettingsTab(current)) { await sameBoot(); return; }
        const unfocusedSelection = current.some(n => n.packageName === SETTINGS && n.text === "Voboost" && n.checked && n.windowRef?.type === 1 && !n.windowRef.active && !n.windowRef.inputFocused);
        if (!unfocusedSelection) throw new Error("Settings selection not observed within read/deadline budget");
        await recoverPrimaryFocus(current);
        current = await snapshot();
        if (selectedSettingsTab(current)) { await sameBoot(); return; }
        // A fresh focused unchecked tab after HOME/open is a new navigation target.
        const freshTab = current.filter(n => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost" && n.clickable && !n.checked);
        if (freshTab.length !== 1) throw new Error("Settings primary focus recovery did not yield fresh selected or selectable Voboost tab");
        await ui.tap(freshTab[0]);
        current = await snapshot();
        if (!selectedSettingsTab(current)) throw new Error("Settings primary focus recovery failed selected focused readback");
        await sameBoot();
        return;
      }

    }
    await settingsForeground(current, "open Settings");
    throw new Error("Voboost tab absent after bounded Settings startup");
    } finally { settingsNavigationDeadline = previousDeadline; }
  };
  const settingsScrollBox = (nodes: UiNode[]) => uniqueNode(nodes, (n) => {
      if (n.packageName !== SETTINGS || !n.scrollable) return false;
      let parent = n.parent;
      while (parent !== null) {
        if (nodes[parent]?.resource === `${SETTINGS}:id/main_contentView`) return true;
        parent = nodes[parent]?.parent ?? null;
      }
      return false;
    });
  const scroll = async (direction: "up" | "down", current?: UiNode[], fraction = 0.70) => {
    const nodes = current ?? await ui.snapshot();
    await settingsForeground(nodes, `scroll ${direction}`);
    const box = settingsScrollBox(nodes);
    const [l, t, r, b] = box.bounds;
    const x = Math.floor((l + r) / 2);
    await ui.call({
      action: "swipe",
      x1: x,
      y1: Math.floor(direction === "down" ? t + (b - t) * (0.5 + fraction / 2) : t + (b - t) * (0.5 - fraction / 2)),
      x2: x,
      y2: Math.floor(direction === "down" ? t + (b - t) * (0.5 - fraction / 2) : t + (b - t) * (0.5 + fraction / 2)),
      milliseconds: 400,
      ...ui.gestureAuthority(box),
    });
  };
  const seekAgentControl = async (title: string, initial?: UiNode[]) => {
    let nodes = initial ?? await ui.snapshot(), previous = "", stationary = 0;
    for (let attempt = 0; attempt < 32; attempt++) {
      await settingsForeground(nodes, "agent checkbox navigation");
      if (agentControl(nodes, SETTINGS, title)) return nodes;
      const box = settingsScrollBox(nodes);
      const direction = agentScrollDirection(nodes, SETTINGS, title, box);
      const signature = JSON.stringify(nodes.filter(n => n.packageName === SETTINGS).map(n => [n.text,n.bounds,n.checked]));
      stationary = signature === previous ? stationary + 1 : 0;
      if (stationary >= 2) break;
      previous = signature;
      await scroll(direction, nodes, 0.25);
      nodes = await ui.snapshot();
    }
    throw new Unavailable("agent checkbox unavailable after bounded target-aware navigation");
  };
  const top = async (openFirst = true) => {
    if (!copy) throw new Unavailable(`source-bound language labels unavailable: ${copyFailure}`);
    if (openFirst) await open();
    for (let i = 0; i < 8; i++) {
      const nodes = await ui.snapshot();
      await settingsForeground(nodes, "panel top");
      if (
        nodes.some(
          (n) =>
            n.packageName === SETTINGS && focusedApplication(n) &&
            Object.values(copy!.languages).some((captions) => captions.startup.some((choice) => choice.label === n.text)) &&
            n.checkable &&
            n.bounds[3] - n.bounds[1] >= 50,
        )
      )
        return nodes;
      await scroll("up", nodes);
    }
    throw new Error("startup controls not visible");
  };
  const tapText = async (text: string, packageName = SETTINGS) => {
    const nodes = await ui.snapshot();
    await ui.tap(
      uniqueNode(nodes, (n) => n.packageName === packageName && n.text === text && n.clickable),
    );
  };
  const systemMenu = async () => {
    for (let attempt = 0; attempt < 8; attempt++) {
      const nodes = await ui.snapshot();
      const row = nodes.filter(
        (n) =>
          n.packageName === SETTINGS && focusedApplication(n) &&
          n.clickable &&
          (n.resource === `${SETTINGS}:id/menu_radio_system` || n.text === "System"),
      );
      if (row.length === 1) {
        await ui.tap(row[0]);
        return;
      }
      const menus = nodes.filter(
        (n) =>
          n.packageName === SETTINGS && focusedApplication(n) &&
          n.scrollable &&
          nodes.some((child) => {
            let parent = child.parent;
            while (parent !== null) {
              if (nodes[parent] === n)
                return child.resource.startsWith(`${SETTINGS}:id/menu_radio_`);
              parent = nodes[parent]?.parent ?? null;
            }
            return false;
          }),
      );
      if (menus.length !== 1) unavailable("unique scrollable Settings menu not exposed");
      const [l, t, r, b] = menus[0].bounds;
      await ui.call({
        action: "swipe",
        x1: Math.floor((l + r) / 2),
        y1: Math.floor(t + (b - t) * 0.85),
        x2: Math.floor((l + r) / 2),
        y2: Math.floor(t + (b - t) * 0.15),
        milliseconds: 400,
      });
    }
    throw new Error("System menu not found after bounded live scrolling");
  };
  // Language is owned by the OEM Display picker. Voboost has no language radio.
  const observedLanguage = async (): Promise<Language> => {
    await top();
    const current = await observations();
    let observed: ReturnType<typeof renderLocale>;
    try { observed = renderLocale(current.logData.diagnostics); }
    catch (error) { throw new Unavailable(`system locale render observation unavailable: ${error}`); }
    const actual = observed.fields.effectiveLanguage;
    const system = observed.fields.defaultLocale?.toLowerCase().replace(/_/g, "-");
    if (!["en", "ru", "zh"].includes(actual) || !system ||
        (actual === "zh" ? !/^zh(?:-cn|-hans(?:-cn)?)?$/.test(system) : actual === "ru" ? !/^ru(?:-|$)/.test(system) : /^zh(?:-cn|-hans(?:-cn)?)?$/.test(system)))
      throw new Unavailable("effective UI language has no matching observed system locale");
    language = actual as Language;
    return language;
  };
  let admittedI18n: ReturnType<typeof admitI18nSystemPicker> | null = null;
  const setLanguage = async (want: Language) => {
    if (!copy) throw new Unavailable(`source-bound language copy unavailable: ${copyFailure}`);
    if (want === "ru" && !admittedI18n) throw new Unavailable("OEM Russian picker contribution requires admitted agent-i18n-ru runtime");
    const baseline = await observations();
    await ui.call({ action: "open-settings" });
    let nodes = await ui.snapshot();
    const menu = uniqueNode(nodes, node => node.packageName === SETTINGS && focusedApplication(node) && node.resource === `${SETTINGS}:id/menu_radio_display` && node.clickable);
    await ui.tap(menu);
    const resource = `${SETTINGS}:id/display_language_radio_${want === "zh" ? "chinese" : "english"}`;
    const choices = (tree: UiNode[]) => {
      const groups = tree.filter(node => node.packageName === SETTINGS && focusedApplication(node) && node.resource === `${SETTINGS}:id/display_language_radio_group`);
      if (groups.length > 1) throw new Error("OEM language picker group is ambiguous");
      const inside = (node: UiNode) => {
        const seen = new Set<number>(); let parent = node.parent;
        while (parent !== null) {
          if (seen.has(parent) || !tree[parent]) return false;
          if (tree[parent] === groups[0]) return true;
          seen.add(parent); parent = tree[parent].parent;
        }
        return false;
      };
      return tree.filter(node => node.packageName === SETTINGS && focusedApplication(node) && inside(node) && node.checkable);
    };
    let selected = false;
    for (let attempt = 0; attempt < 8; attempt++) {
      nodes = await ui.snapshot();
      const matches = (node: UiNode) => want === "ru" ? node.text === "Русский" : node.resource === resource;
      const candidates = choices(nodes).filter(matches);
      if (candidates.length > 1) throw new Error("OEM language picker identity is ambiguous");
      if (candidates.length === 1) {
        if (!candidates[0].checked) await ui.tap(candidates[0]);
        const checked = choices(await ui.snapshot()).filter(node => node.checked);
        if (checked.length !== 1 || !matches(checked[0])) throw new Unavailable("OEM language picker selection lacks fresh checked readback");
        selected = true; break;
      }
      await scroll("down", nodes);
    }
    if (!selected) throw new Unavailable("proven OEM Display language radio is not exposed");
    await open();
    await top(false);
    const current = await observations();
    const observed = renderLocale(fresh(baseline, current));
    const system = observed.fields.defaultLocale?.toLowerCase();
    if (observed.fields.effectiveLanguage !== want || !system || !new RegExp("^"+want+"(?:[-_]|$)").test(system) || !new RegExp("^"+want+"(?:[-_]|$)").test(String(observed.fields.resourceLocale).toLowerCase()))
      throw new Unavailable("OEM selection lacks fresh matching process locale and Voboost render readback");
    language = want;
  };
  const selectStartup = async (want: StartupMode) => {
    if (!copy) unavailable(`source-bound startup labels unavailable: ${copyFailure}`);
    const baseline = await inspect(), before = baseline.boot, caption = copy.languages[language].startup.find((row) => row.value === want)!.label;
    await top();
    const nodes = await ui.snapshot(), radio = uniqueNode(nodes, (node) => node.packageName === SETTINGS && focusedApplication(node) && node.checkable && node.text === caption);
    if (!radio.checked) await ui.tap(radio);
    for (let attempt = 0; attempt < 10; attempt++) {
      const observed = await inspect();
      if (observed.boot !== before) throw new Error("startup radio mutation crossed a boot");
      const statuses = observed.body.split("\n").filter((line) => line.startsWith("{")).flatMap((line) => { try { return [JSON.parse(line)]; } catch { return []; } }).filter((row: any) => Array.isArray(row?.injections));
      if (statuses.length !== 1 || statuses[0].releaseIdentity !== manifest.releaseIdentity || statuses[0].manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json")))) unavailable("fresh root startup confirmation identity missing");
      const current = await ui.snapshot(), selected = current.filter((node) => node.packageName === SETTINGS && focusedApplication(node) && node.checkable && node.text === caption && node.checked);
      const savedResult = await cli(["--status"]); resultOk(savedResult);
      const saved = jsonReceipt(savedResult.stdout);
      startupDeferredSelection(baseline, observed, saved, { serial, releaseIdentity: manifest.releaseIdentity, manifestSha256: digest(readFileSync(join(c.closure, "voboost.json"))), want });
      if (!current.some((node) => node.packageName === SETTINGS && focusedApplication(node) && node.text === "Voboost" && node.checked)) throw new Error("startup radio selection hid the current Voboost tab");
      if (saved.startupMode === want && selected.length === 1) {
        startup = want;
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
    throw new Error("startup radio/root selected mode did not confirm");
  };
  const returnInterface = async () => {
    resultOk(await cli(["--mode", "interface"]));
    startup = "interface";
    await open();
    await selectStartup("interface");
  };
  const panel = async (completeCopy = false, openFirst = true) => {
    await top(openFirst);
    const texts = new Set<string>();
    let last = "";
    for (let page = 0; page < 12; page++) {
      const nodes = await ui.snapshot();
      await settingsForeground(nodes, "panel capture");
      for (const n of nodes) if (n.packageName === SETTINGS) texts.add(n.text);
      await screenshot(`${language}-panel-${caseNumber}-${page}`);
      const fp = JSON.stringify(nodes.map((n) => [n.text, n.bounds, n.checked]));
      if (fp === last) break;
      last = fp;
      await scroll("down");
    }
    for (const a of catalog.filter((a: any) => a.status === "stable" && a.id !== "settings"))
      for (const key of ["title", "description"])
        if (!texts.has(a[key]?.[language]))
          throw new Error(`translated metadata missing: ${a.id}/${key}/${language}`);
    if (completeCopy) {
      if (!copy) throw new Unavailable(`owning-build source-bound Settings copy unavailable: ${copyFailure}`);
      for (const text of panelCopy(copy.languages[language], startup))
        if (!texts.has(text)) throw new Error(`source-bound panel caption missing for ${language}: ${text}`);
    }
    return texts;
  };
  const captureWindow = async (action: () => Promise<unknown>) => {
    const before = await bootObservation(), first = evidence.actions.length;
    await action();
    const after = await bootObservation();
    if (before.boot !== after.boot || !before.bootCompleted || !after.bootCompleted) unavailable("locale capture window crossed a boot or was incomplete");
    const artifacts = evidence.actions.slice(first).flatMap((row: any) => {
      if (row.exitCode !== 0 || row.timedOut || row.signal || row.overflow || row.context?.bootId !== before.boot || row.context?.locale !== language || (row.args[0] !== "--emulator-check" && !["session.screenshot", "session.ui"].includes(row.nativeOperation))) return [];
      if (row.nativeOperation === "session.ui") { nativeTreeAction(evidence, row); return row.artifacts.filter((a: any) => a.kind === "stdout").map((a: any) => a.path); }
      if(row.nativeOperation==="session.screenshot") return row.artifacts.filter((a:any)=>a.kind==="png" && a.path===row.nativeAuthority.output).map((a:any)=>a.path);
      const request = JSON.parse(readFileSync(row.args[1], "utf8"));
      return row.artifacts.filter((artifact: any) => ((request.action === "screenshot" || row.nativeOperation === "session.screenshot") && artifact.kind === "png") || (request.action === "snapshot" && artifact.kind === "stdout")).map((artifact: any) => artifact.path);
    });
    const row = evidence.cases.find((row: Case) => row.number === caseNumber);
    if (!fullSemantic) functionalEvidence({ ...evidence, boots: [], cases: [{ ...row, subchecks: [{ id: `captures-${language}`, status: "passed", artifacts }] }] });
    return artifacts;
  };
  let admittedStopModes: Record<string, "restart" | "in-place"> | null = null;
  const toggle = async (id: string, want: boolean) => {
    const title = catalog.find((a: any) => a.id === id)?.title?.[language];
    if (!title) throw new Error("signed agent title missing");
    let nodes = await ui.snapshot();
    if (!agentControl(nodes, SETTINGS, title)) {
      await top();
      nodes = await ui.snapshot();
    }
    nodes = await seekAgentControl(title, nodes);
    {
      const check = agentControl(nodes, SETTINGS, title);
      if (check) {
        let restartProof: ToggleRestartProof | undefined;
        let toggleDeadline = Infinity;
        if (check.checked !== want) {
          const restartTargets = !want && admittedStopModes?.[id] === "restart" ? catalog.find((agent:any)=>agent.id===id).targets.map((target:any)=>target.process) as string[] : [];
          const recoverable = restartTargets.some(process=>process===SETTINGS || process==="com.qinggan.app.launcher");
          const prior = recoverable ? await inspect() : null;
          let priorInventory;
          if (prior) {
            try { priorInventory = targetInventory(prior.body,restartTargets); }
            catch(error) { if(error instanceof InventoryUnavailable) unavailable(error.message); throw error; }
          }
          const before = await agentToggleTap(ui, title, check.checked, (resident) => {
            if (resident.releaseIdentity !== manifest.releaseIdentity || resident.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json")))) throw new Error("agent toggle product identity differs");
          });
          const deadline = performance.now() + 15000;
          toggleDeadline = deadline;
          let confirmed = false;
          while (performance.now() < deadline) {
            const receipt = await ui.call({ action: "resident-status" });
            parseResidentStatus(receipt.body);
            if (performance.now() >= deadline) break;
            const currentResident = inspectionResident(receipt.body);
            if (agentToggleReady(before, currentResident, id, want)) {
              if (prior && priorInventory) {
                const current = await inspect();
                let currentInventory;
                try { currentInventory = targetInventory(current.body,restartTargets); }
                catch(error) { if(error instanceof InventoryUnavailable) unavailable(error.message); throw error; }
                if(current.boot!==prior.boot) throw new Error("confirmed agent host restart crossed boot");
                const runningHosts=restartTargets.filter(process=>(process===SETTINGS || process==="com.qinggan.app.launcher") && priorInventory.pids[process]);
                if(runningHosts.some(process=>!currentInventory.pids[process] || currentInventory.pids[process]===priorInventory.pids[process])) continue;
                restartProof = {mode:"restart",targets:restartTargets,beforeBoot:prior.boot,afterBoot:current.boot,beforePids:priorInventory.pids,afterPids:currentInventory.pids,beforeResident:before,currentResident};
              }
              confirmed = true;
              break;
            }
          }
          if (!confirmed) throw new Error("agent toggle Settings confirmation exhausted");
        }
        let fresh = await ui.snapshot();
        // ACK and independent PID replacement admit fixed navigation from observed
        // primary Home, including when primary accessibility reports no input focus.
        fresh = await recoverToggleHome(id, fresh, restartProof, () => ui.snapshot(), async () => {
          if (!restartProof) return;
          const current = await bootObservation();
          if (!current.bootCompleted || current.boot !== restartProof.afterBoot) throw new Error("agent toggle restart focus crossed boot");
        }, open, () => performance.now(), toggleDeadline);
        const retained = () => {
          const tab = uniqueNode(fresh, (n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost" && n.checkable);
          if (!tab.checked) throw new Error("agent toggle lost selected Voboost tab");
        };
        retained();
        if (!agentControl(fresh, SETTINGS, title)) {
          await top(false); fresh = await ui.snapshot(); retained();
          fresh = await seekAgentControl(title, fresh); retained();
        }
        const label = uniqueNode(fresh, (n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === title);
        const after = uniqueNode(
          fresh,
          (n) => n.packageName === SETTINGS && focusedApplication(n) && n.parent === label.parent && n.checkable,
        );
        if (
          after.checked !== want ||
          !fresh.some((n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost")
        )
          throw new Error("agent readback or retained tab differs");
        enabled.set(id, want);
        if (want)
          for (const other of catalog.find((a: any) => a.id === id)?.disables ?? []) {
            const title = catalog.find((a: any) => a.id === other)?.title?.[language];
            const label = fresh.filter((n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === title);
            if (label.length === 1) {
              const check = uniqueNode(
                fresh,
                (n) => n.packageName === SETTINGS && focusedApplication(n) && n.parent === label[0].parent && n.checkable,
              );
              if (check.checked) throw new Error("signed conflict exclusion readback differs");
              enabled.set(other, false);
            } else enabled.delete(other);
          }
        return;
      }
    }
    throw new Error(`agent control unavailable: ${id}`);
  };
  const observations = async () => {
    const observed = await inspect();
    const diagnostic = await cli(["--diagnostics"]);
    if (
      diagnostic.exitCode !== 0 ||
      diagnostic.timedOut ||
      diagnostic.signal ||
      diagnostic.overflow
    )
      throw new Unavailable("ordinary diagnostic observation unavailable; raw failure retained");
    const logs = await cli(["--logs", join(c.output, "logs")]);
    if (logs.exitCode !== 0 || logs.timedOut || logs.signal || logs.overflow)
      throw new Unavailable("ordinary log archive unavailable; raw failure retained");
    const lines = logs.stdout
      .split(/\r?\n/)
      .filter((l) => l && !/^\[[0-9:. ]+ (INFO|DEBUG|WARN|ERROR)\]/.test(l));
    if (lines.length !== 1 || !isAbsolute(lines[0]))
      throw new Error("exact log archive path receipt missing");
    const archive = lines[0];
    regular(archive);
    if (dirname(realpathSync(archive)) !== realpathSync(join(c.output, "logs")))
      throw new Error("log archive escaped destination");
    evidence.actions
      .find((a) => a.id === logs.actionId)
      .artifacts.push({ path: archive, sha256: digest(readFileSync(archive)), kind: "archive" });
    const logData = parseInstallerLogs(readFileSync(archive), {
      serial,
      version: manifest.releaseIdentity,
      manifestSha256: digest(readFileSync(join(c.closure, "voboost.json"))),
    });
    if ((await bootObservation()).boot !== observed.boot)
      throw new Unavailable("ordinary log observation crossed a guest boot");
    const body = diagnosticBody(diagnostic.stdout, serial);
    const resident = inspectionResident(observed.body);
    if (resident.releaseIdentity !== manifest.releaseIdentity || resident.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || resident.catalogSha256 !== digest(readFileSync(join(c.closure, "agents.json")))) throw new Error("persistent vehicle snapshot independent release identity differs");
    const observedState = { ...observed, logData, archive, diagnostics: body, diagnosticArmed: vehicleArmed(body, { boot: observed.boot, generationId: resident.generationId, catalogSha256: resident.catalogSha256 }) };
    const armed = { ...observedState.diagnosticArmed };
    for (const vehicle of ["fev", "pw"] as Vehicle[]) {
      if (armed[vehicle].status !== "unavailable") continue;
      try { armed[vehicle] = rootVehicleState(logData.records, vehicle, vehicleAuthority(observedState, observedState, vehicle), -1); }
      catch (error) { if (!(error instanceof Unavailable)) throw error; }
    }
    return { ...observedState, armed };
  };
  const launcherObservations = async () => {
    const before = await ui.call({ action: "open-secondary-launcher" });
    const observed = await observations();
    const after = await ui.call({ action: "open-secondary-launcher" });
    return { ...observed, canonicalArchives:[observed.archive], launcherLifetime: launcherLifetime(before.body, observed, after.body), nativeLifetime: { beforeBody: before.body, observed: { boot: observed.boot, body: observed.body }, afterBody: after.body } };
  };

  const cacheAuthority = (current: Awaited<ReturnType<typeof launcherObservations>>, baseline: Awaited<ReturnType<typeof observations>>) => ({
    ...current.launcherLifetime, nativeLifetime:current.nativeLifetime, records:current.logData.records, afterSequence:sequence(baseline.logData.records), baselineRecord:baseline.logData.records.at(-1),
  });
  const seekSecondaryCache = async (baseline:Awaited<ReturnType<typeof observations>>, seed?:Awaited<ReturnType<typeof launcherObservations>>, previous?:ReturnType<typeof launcherCache>, beforeWitness?:CacheWitness, themePreviousGeneration?:number) => {
    if(!native) throw new Unavailable("secondary physical page navigation requires the owned native session");
    let retained=seed;
    return seekSecondaryFixture(async()=>{
      const current=await launcherObservations();
      if(retained) {
        const joined=joinCacheWitness({rows:retained.logData.diagnostics,authority:cacheAuthority(retained,baseline)},{rows:current.logData.diagnostics,authority:cacheAuthority(current,baseline)});
        current.logData={...current.logData,records:joined.authority.records,diagnostics:joined.rows};
        current.canonicalArchives=[...new Set([...retained.canonicalArchives,...current.canonicalArchives])];
      }
      retained=current;
      try {const authority=cacheAuthority(current,baseline); const publicationGeneration=themePreviousGeneration===undefined?undefined:launcherThemePublication(current.logData.diagnostics,authority,plan.fixture.package,themePreviousGeneration);launcherCache(current.logData.diagnostics,{...authority,...(beforeWitness?{beforeWitness}:{}),...(publicationGeneration===undefined?{}:{publicationGeneration})},plan.fixture.package,previous);return current;}
      catch(error) {if(error instanceof CacheEvidenceUnavailable && ["launcher cache first draw missing for screen 1","launcher cache first draw missing for screen 0","theme new publication missing"].includes(error.message)) return null;throw error;}
    },async()=>{
      const witness=await secondaryPageSwipe(async()=> (await launcherObservations()).launcherLifetime,async()=>{
        // Explicit owned-display physical navigation; no semantic target/window fence is claimed.
        try {return await native.call("input.gesture",{screen:"secondary",points:gestureTrajectory([1632,360],[288,360],400)});}
        finally {
          const action=evidence.actions.at(-1);
          if(action?.nativeOperation!=="input.gesture") throw new Error("secondary physical swipe action correlation differs");
          action.context={kit:frozen.package,runtime,serial,bootId:currentBoot,locale:language};
          action.inputProvenance={kind:"owned-profile-physical-swipe",targetWindowPreDispatchFenced:false};
          persist();
        }
      },runtime!.sessionId);
      const action=evidence.actions.findLast((row:any)=>row.nativeOperation==="input.gesture" && row.inputProvenance?.kind==="owned-profile-physical-swipe");
      if(!action) throw new Error("secondary physical swipe provenance missing");
      action.inputProvenance={kind:witness.provenance,targetWindowPreDispatchFenced:false,before:witness.before,after:witness.after};persist();
    });
  };

  const unavailable = (reason: string): never => {
    throw new Unavailable(reason);
  };
  const window = (observed: Awaited<ReturnType<typeof observations>>) => ({ boot: observed.boot, ...observed.logData });
  const fresh = (baseline: Awaited<ReturnType<typeof observations>>, current: Awaited<ReturnType<typeof observations>>) => freshDiagnostics(window(baseline), window(current));
  const layoutAuthority = (baseline: Awaited<ReturnType<typeof observations>>, observed: Awaited<ReturnType<typeof observations>>): LayoutAuthority => {
    const status = inspectionResident(observed.body), catalogSha256 = digest(readFileSync(join(c.closure, "agents.json")));
    if (status.releaseIdentity !== manifest.releaseIdentity || status.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || status.catalogSha256 !== catalogSha256) throw new Error("independent current layout release or catalog identity differs");
    const selected = status.injections.filter((row: any) => row.id === "settings" && row.process === SETTINGS && row.state === "active");
    if (selected.length !== 1 || typeof selected[0].documentGeneration !== "string") unavailable("independent current Settings desired revision missing");
    const pids = [oemTargetPid(observed.body, SETTINGS)];
    return { process: SETTINGS, generationId: status.generationId, catalogSha256, documentGeneration: selected[0].documentGeneration, targetPid: pids[0], boot: observed.boot, baselineBoot: baseline.boot, afterSequence: sequence(baseline.logData.records), records: observed.logData.records };
  };
  const fontAuthority = (baseline: Awaited<ReturnType<typeof observations>>, observed: Awaited<ReturnType<typeof observations>>): FontAuthority => {
    if (!copy) unavailable(`source-bound font producer unavailable: ${copyFailure}`);
    const process = "com.pateonavi.naviapp", catalogSha256 = digest(readFileSync(join(c.closure, "agents.json")));
    if (!catalog.find((agent: any) => agent.id === "font-cyrillic")?.targets?.some((target: any) => target.process === process)) throw new Error("signed font target does not include actual Navi process");
    const documents = observed.body.split("\n").filter((line: string) => line.startsWith("{")).flatMap((line: string) => { try { return [JSON.parse(line)]; } catch { return []; } });
    const statuses = documents.filter((document: any) => Array.isArray(document?.injections));
    if (statuses.length !== 1) unavailable("independent current font generation status missing");
    const status = statuses[0];
    if (status.releaseIdentity !== manifest.releaseIdentity || status.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || status.catalogSha256 !== catalogSha256) throw new Error("current font release or catalog identity differs");
    const selected = status.injections.filter((row: any) => row.id === "font-cyrillic" && row.process === process && row.state === "active");
    if (selected.length !== 1) unavailable("unique active signed Navi font endpoint missing");
    const endpoint = selected[0].currentEndpoint, publisher = status.publisher;
    if (!publisher || publisher.bootId !== observed.boot || !endpoint) unavailable("independent current font publisher or endpoint missing");
    currentEndpointShape(endpoint);
    const pids = [oemTargetPid(observed.body, process)];
    const focusSection = /^window-focus:\r?\n([\s\S]*?)\r?\nandroid-boot-errors:\r?\n/m.exec(observed.body)?.[1] ?? "";
    const focus = [...focusSection.matchAll(/^\s*mCurrentFocus=Window\{[0-9a-f]+ u0 com\.pateonavi\.naviapp\/([A-Za-z0-9_.$]+)\}\s*$/gm)];
    if (focus.length !== 1) unavailable("independent current focused Navi Activity missing");
    const activityClass = focus[0][1].startsWith(".") ? process + focus[0][1] : focus[0][1];
    const queued = observed.logData.records.filter((record) => record.message === "desired-snapshot-queued").map(queuedSnapshot).findLast((fields) => fields.process === process);
    if (!queued?.targetStarttime || !queued.endpointEpoch || queued.targetPid !== pids[0] || queued.generationId !== status.generationId || queued.catalogSha256 !== catalogSha256) unavailable("current registered Navi font endpoint lifetime missing");
    if (endpoint.targetPid !== pids[0] || endpoint.targetStarttime !== queued.targetStarttime || endpoint.endpointEpoch !== queued.endpointEpoch || endpoint.generationId !== status.generationId || endpoint.catalogSha256 !== catalogSha256) unavailable("independent current font endpoint differs from registration");
    return { process, targetPid: pids[0], targetStarttime: queued.targetStarttime, endpointEpoch: queued.endpointEpoch, generationId: status.generationId, catalogSha256, daemonPid: publisher.daemonPid, daemonStarttime: publisher.daemonStarttime, desiredDocumentGeneration: endpoint.desiredDocumentGeneration, activityClass, boot: observed.boot, baselineBoot: baseline.boot, afterSequence: sequence(baseline.logData.records), records: observed.logData.records };
  };
  const vehicleAuthority = (baseline: { boot: string }, observed: { boot: string; body: string; logData: ReturnType<typeof parseInstallerLogs> }, vehicle: Vehicle): VehicleAuthority => {
    const status = inspectionResident(observed.body), catalogSha256 = digest(readFileSync(join(c.closure, "agents.json")));
    if (status.releaseIdentity !== manifest.releaseIdentity || status.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || status.catalogSha256 !== catalogSha256) throw new Error("vehicle release or signed catalog differs");
    const selected = vehicleRootTarget(status.injections, catalog, vehicle);
    const pids = [oemTargetPid(observed.body, selected.process)];
    const queued = observed.logData.records.filter((record) => record.message === "desired-snapshot-queued").map(queuedSnapshot).findLast((fields) => fields.process === selected.process);
    if (!queued?.targetStarttime || !queued.endpointEpoch || queued.targetPid !== pids[0] || queued.generationId !== status.generationId || queued.catalogSha256 !== catalogSha256 || (selected.documentGeneration !== undefined && queued.documentGeneration !== selected.documentGeneration)) unavailable("current resident vehicle endpoint, lifetime or desired revision missing");
    return { boot: observed.boot, baselineBoot: baseline.boot, generationId: queued.generationId, catalogSha256, process: selected.process, targetPid: pids[0], targetStarttime: queued.targetStarttime, endpointEpoch: queued.endpointEpoch, documentGeneration: selected.documentGeneration ?? queued.documentGeneration };
  };
  const currentVehicle = async (vehicle: Vehicle, want: boolean, baseline: Awaited<ReturnType<typeof observations>>, carryRead = false, armedWant = true) => {
    let observed = baseline, read: boolean | null = null, state = baseline.diagnosticArmed[vehicle];
    for (let attempt = 0; attempt < 10; attempt++) {
      observed = await observations();
      state = observed.diagnosticArmed[vehicle];
      if (state.status === "unavailable") {
        try { state = rootVehicleState(observed.logData.records, vehicle, vehicleAuthority(baseline, observed, vehicle), sequence(baseline.logData.records), carryRead ? baseline.logData.records : undefined); }
        catch (error) { if (!(error instanceof Unavailable)) throw error; }
      }
      read = vehicleRead(fresh(baseline, observed), vehicle);
      if (read === want && state.status === "present" && state.value === armedWant) return observed;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    if (read === null) unavailable(`fresh current CAN read unavailable for ${vehicle}`);
    if (state.status === "unavailable") unavailable("actual root vehicle operation unavailable; historical values are not fresh queries");
    throw new Error(`CAN or root armed readback differs for ${vehicle}`);
  };
  let cachedDisablePolicy: Awaited<ReturnType<typeof readDisablePolicy>> | null = null;
  const disablePolicy = async () => cachedDisablePolicy ??= await readDisablePolicy();
  const readDisablePolicy = async () => {
    const root = join(c.workspaceRoot, "voboost-agents"), receiptPath = join(dirname(c.closure), c.localArtifact ? "local-build-evidence.json" : "receipt.json");
    const receiptBytes = readFileSync(receiptPath), receiptSha256 = digest(receiptBytes);
    const receipt = JSON.parse(receiptBytes.toString("utf8")), catalogSha256 = digest(readFileSync(join(c.closure, "agents.json")));
    if (c.localArtifact) {
      await fullSourceAdmission();
      assertLocalHostCatalog(receipt, catalogSha256);
    } else if (receipt.schemaVersion !== 1 || receipt.kind !== "source-build" || receipt.artifacts?.["agents.json"]?.sha256 !== catalogSha256) unavailable("disable policy owning source-build catalog receipt unavailable");
    const expected = receipt.sources?.find((row: any) => row.name === "agents"), before = await snapshotRepository("agents", root);
    if (!c.localArtifact && JSON.stringify(before) !== JSON.stringify(expected)) unavailable("disable policy owning sources differ from installed closure");
    const source: Record<string,string> = {};
    for (const entry of ["ru.voboost.agents.Agent", "ru.voboost.agents.AgentAsync", "ru.voboost.agents.AgentCacheQuiescence", "ru.voboost.agents.AgentInputObserver", ...Object.values(CLASSES)]) {
      const file = join(root, "src/main/java", entry.replaceAll(".", "/") + ".java");
      regular(file); source[entry.startsWith("ru.voboost.agents.Agent") ? entry.split(".").at(-1)! : entry] = readFileSync(file,"utf8");
    }
    const admitted = admitStopModes(catalog, source);
    if (c.localArtifact) await fullSourceAdmission();
    if (digest(readFileSync(receiptPath)) !== receiptSha256 || digest(readFileSync(join(c.closure, "agents.json"))) !== catalogSha256) throw new Error("disable policy receipt or delivered catalog changed during admission");
    const after = await snapshotRepository("agents", root);
    if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error("disable policy owning sources changed during admission");
    const file = join(c.output, "agent-disable-owning-policy.json");
    writeFileSync(file, JSON.stringify({schemaVersion:1, sourceAuthority:c.localArtifact ? "local-artifact" : "source-build", sourceReceiptSha256:receiptSha256, source:before, ...admitted},null,2)+"\n", {flag:"wx",mode:0o600});
    return {...admitted,file};
  };
  const checkboxTargets = (id: string) => {
    const agent = catalog.find((row: any) => row.id === id && row.activation?.type === "checkbox");
    if (!agent || !Array.isArray(agent.targets) || !agent.targets.length) throw new Error("signed checkbox target list missing");
    const targets = agent.targets.map((row: any) => row.process);
    if (targets.some((process: any) => typeof process !== "string") || new Set(targets).size !== targets.length) throw new Error("signed checkbox targets malformed");
    return targets as string[];
  };
  const toggleObservation = async (targets: string[]): Promise<DisableObservation & { file: string }> => {
    const first = evidence.actions.length, observed = await inspect(), resident = inspectionResident(observed.body);
    residentStatusShape(resident);
    if (resident.releaseIdentity !== manifest.releaseIdentity || resident.manifestSha256 !== digest(readFileSync(join(c.closure,"voboost.json"))) || resident.catalogSha256 !== digest(readFileSync(join(c.closure,"agents.json"))) || resident.state !== "ready" || resident.panic || resident.killed || resident.injections.some((row:any)=>row.state==="failed")) throw new Error("disable observation resident identity or health differs");
    let inventory;
    try { inventory = targetInventory(observed.body, targets); }
    catch(error) { if(error instanceof InventoryUnavailable) unavailable(error.message); throw error; }
    const file = join(c.output, `agent-toggle-observation-${first}.json`);
    writeFileSync(file, JSON.stringify({boot:observed.boot, pids:inventory.pids, resident, actions:evidence.actions.slice(first).map(row=>row.id)},null,2)+"\n",{flag:"wx",mode:0o600});
    return {boot:observed.boot,inventory,injections:resident.injections,file};
  };
  const checkDisable = async (id: string, mode: "restart" | "in-place", action: () => Promise<void>, prefix = "disable", required = false) => {
    const targets = checkboxTargets(id), before = await readyBeforeDisable(id, targets, () => toggleObservation(targets));
    await action();
    const deadline = performance.now()+15000;
    let after = await toggleObservation(targets);
    const retained = before.injections.filter(row=>row.id!==id && row.state==="active" && targets.includes(row.process));
    const pending = () => targets.some(process => before.inventory.pids[process] && before.injections.some(row=>row.id===id && row.process===process && row.state==="active") && (!after.inventory.pids[process] || mode==="restart" && before.inventory.pids[process]===after.inventory.pids[process])) || retained.some(row=>!after.injections.some(current=>current.id===row.id && current.process===row.process && current.state==="active"));
    while(performance.now()<deadline && pending()) after = await toggleObservation(targets);
    if(retained.some(row=>!after.injections.some(current=>current.id===row.id && current.process===row.process && current.state==="active"))) unavailable("unaffected shared-target agents did not regain active readiness after removal");
    for(const result of removedTargets(id,targets,mode,before,after)) {
      if (required && result.status !== "passed") unavailable(result.reason);
      proof(`${prefix}-${id}-${result.process}`,result.status,result.reason,[before.file,after.file]);
    }
  };
  const checkReenable = async (id: string, prefix = "re-enable") => {
    const targets=checkboxTargets(id);
    await toggle(id,true);
    const deadline=performance.now()+15000;
    let observed=await toggleObservation(targets);
    while(performance.now()<deadline && targets.some(process=>observed.inventory.pids[process] && !observed.injections.some(row=>row.id===id && row.process===process && row.state==="active"))) observed=await toggleObservation(targets);
    for(const result of reenabledTargets(id,targets,observed)) proof(`${prefix}-${id}-${result.process}`,result.status,result.reason,[observed.file]);
  };
  const vehicleDisableCancellation = async (vehicle: Vehicle) => {
    const id=VEHICLE[vehicle], desired=vehicle==="fev";
    await toggle(id,true);
    await selectOem(vehicle,!desired);
    const baseline=await observations();
    await selectOem(vehicle,desired);
    const armed=await currentVehicle(vehicle,desired,baseline,true);
    const before=await toggleObservation(checkboxTargets(id));
    await toggle(id,false);
    const stopped=await toggleObservation(checkboxTargets(id));
    for(const result of removedTargets(id,checkboxTargets(id),"in-place",before,stopped)) if(result.status!=="passed") unavailable(result.reason);
    await powerCycle();
    const reset=await observations();
    if(reset.boot!==stopped.boot || inspectionResident(reset.body).injections.some((row:any)=>row.id===id)) throw new Error("disabled vehicle agent reappeared during same-boot OEM reset");
    if(await oemValue(vehicle)!==!desired) throw new Error("disabled vehicle agent still restores saved preference after genuine OEM power reset");
    await screenshot(`${vehicle}-disabled-cancellation-reset`);
    await toggle(id,true);
    // PW deliberately adopts the current OEM choice after an explicit clean stop.
    // It must not mistake that stop for an abrupt process/vehicle restart.
    if (vehicle === "pw") {
      const adopted=await currentVehicle(vehicle,!desired,armed,true,false);
      if(await oemValue(vehicle)!==!desired) throw new Error("re-enabled PW failed to adopt the current OEM choice after clean stop");
      await selectOem(vehicle,desired);
      const rearmed=await currentVehicle(vehicle,desired,adopted,true);
      await screenshot(`${vehicle}-reenabled-cancellation-rearm`);
      return [baseline.archive,armed.archive,before.file,stopped.file,reset.archive,adopted.archive,rearmed.archive];
    }
    const restored=await currentVehicle(vehicle,desired,armed,true);
    if(await oemValue(vehicle)!==desired) throw new Error("re-enabled vehicle agent failed to restore retained preference");
    await screenshot(`${vehicle}-reenabled-cancellation-restore`);
    return [baseline.archive,armed.archive,before.file,stopped.file,reset.archive,restored.archive];
  };
  const launcherStockDisabled = async () => {
    const title = catalog.find((agent: any) => agent.id === "launcher-apps")?.title?.[language];
    if (!title) unavailable("signed launcher checkbox title unavailable for stock baseline");
    await top();
    const nodes = await seekAgentControl(title);
    const check = agentControl(nodes, SETTINGS, title);
    if (check) {
      if (check.checked) unavailable("launcher agent already enabled; preserve original state instead of claiming a stock baseline");
      return;
    }
    unavailable("observed disabled launcher checkbox unavailable for stock baseline");
  };
  const launcherPage = async () => {
    const nodes = await ui.snapshot();
    const grid = (native?launcherPointerNode:uniqueNode)(nodes, (node) => node.packageName === "com.qinggan.app.launcher" && node.resource === "com.qinggan.app.launcher:id/all_app_view" && node.windowRef?.type === 1);
    const items = launcherItems(nodes, grid,!!native);
    if (!items.length || new Set(items.map(item => item.label)).size !== items.length || items.some(item => !item.label)) unavailable("unique observed OEM app icons unavailable");
    const page = { grid, items, nodes };
    launcherCurrentPage = page;
    return page;
  };
  let launcherCurrentPage: Awaited<ReturnType<typeof launcherPage>>;
  const launcherBeginning = async () => {
    await launcherFirstPage(
    async () => { launcherCurrentPage = await launcherPage(); return launcherCurrentPage.items; },
    async (items) => {
      const grid = launcherCurrentPage.grid;
      const [l,t,r,b] = grid.windowRef!.bounds;
      await ui.call({ action: "gesture", x1: Math.floor(l+(r-l)*0.15), y1: Math.floor((t+b)/2), x2: Math.floor(l+(r-l)*0.85), y2: Math.floor((t+b)/2), milliseconds: 400, ...(native?ui.launcherPointerAuthority(grid):ui.gestureAuthority(grid)) });
    },
    async (items) => {
      const beforeBoot = deferred ? await inspect() : await bootObservation();
      const before = launcherCurrentPage;
      const indicator = (native?launcherPointerNode:uniqueNode)(before.nodes, n => n.packageName === "com.qinggan.app.launcher" && n.resource === "com.qinggan.app.launcher:id/home_page_indicator" && n.visibleToUser === true && (native || focusedApplication(n)));
      const signature = (rows: typeof items) => JSON.stringify(rows.map(row => [row.label,row.node.bounds]));
      if(indicator.windowRef!.id!==before.grid.windowRef!.id||signature(before.items)!==signature(items)||before.grid.windowRef!.id!==items[0].node.windowRef!.id) throw new Error("launcher page changed before indicator capture");
      if (deferred) {
        try {
          const result = await launcherSemanticBoundary({
            before, beforeBoot, indicator,
            query: async () => {
              const requestId = randomBytes(32).toString("hex");
              const response = await ui.call({ action: "launcher-page-observe", requestId });
              return { body: response.body, requestId };
            },
            page: launcherPage, boot: () => inspect(),
            indicatorFor: page => (native?launcherPointerNode:uniqueNode)(page.nodes, n => n.packageName === indicator.packageName && n.resource === indicator.resource && n.visibleToUser === true && (native || focusedApplication(n))),
          });
          launcherCurrentPage = result.page;
          return result.first;
        } catch (error) {
          if (error instanceof LauncherPageUnavailable) throw new Unavailable(error.message);
          throw error;
        }
      }
      const png = await screenshot("launcher-page-boundary");
      const after = await launcherPage();
      const afterIndicator = (native?launcherPointerNode:uniqueNode)(after.nodes,n=>n.packageName===indicator.packageName&&n.resource===indicator.resource&&n.visibleToUser===true&&(native || focusedApplication(n)));
      const afterBoot = await bootObservation();
      launcherCaptureContinuity(before, after, indicator, afterIndicator, beforeBoot, afterBoot, 180000,!!native);
      // The boot observation consumes UI authority. Reacquire nodes before any gesture.
      const fresh = await launcherPage();
      const freshIndicator = (native?launcherPointerNode:uniqueNode)(fresh.nodes,n=>n.packageName===indicator.packageName&&n.resource===indicator.resource&&n.visibleToUser===true&&(native || focusedApplication(n)));
      launcherCaptureContinuity(after, fresh, afterIndicator, freshIndicator, afterBoot, afterBoot, 180000,!!native);
      launcherCurrentPage = fresh;
      return launcherAppsBeginningPng(readFileSync(png),freshIndicator.bounds);
    },
  ); return launcherCurrentPage.items; };
  const launcherGrid = async (firstPage = false, route: "dock" | "home-swipe" = "dock") => {
    // Select the primary OEM activity without mutating or relying on Home preferences.
    await ui.call({ action: "open-primary-launcher" });
    if(native) {
      const identity=()=>JSON.stringify([nativeInputTree?.session_id,nativeInputTree?.android_pid,nativeInputTree?.source_instance,nativeInputTree?.observer_pid]);
      const sameBoot=async()=>{await ownership();const observed=await bootObservation();if(!observed.bootCompleted) throw new Error("launcher focus boot incomplete");return observed.boot;};
      await observePrimaryLauncher(ui,sameBoot,identity,performance.now()+15000);
    }
    if (route === "home-swipe") await openLauncherAppsFromHome(ui,!!native);
    else await ui.openLauncherApps();
    if(native) {
      const page=await launcherPage();
      if(launcherPrimaryPointerWindow(page.nodes)!==page.grid.windowRef || page.grid.windowRef.title!=="module-widget") throw new Error("launcher grid primary window not confirmed after entry");
    }
    return firstPage ? launcherBeginning() : (await launcherPage()).items;
  };
  const launcherVisibleInventory = async () => {
    const beforeBoot = await bootObservation();
    let items = await launcherGrid(true);
    const seenPages = new Set<string>();
    const labels = new Set<string>();
    const signature = (rows: typeof items) =>
      JSON.stringify(rows.map(row => [row.label, row.node.bounds]));
    const owner = launcherCurrentPage.grid.windowRef!;
    for (let page = 0; page < 12; page++) {
      const key = signature(items);
      if (seenPages.has(key)) unavailable("launcher inventory repeated a nonterminal page");
      seenPages.add(key);
      for (const item of items) labels.add(item.label);
      const grid = launcherCurrentPage.grid;
      const window = grid.windowRef!;
      if (window.id !== owner.id || window.type !== owner.type ||
          JSON.stringify(window.bounds) !== JSON.stringify(owner.bounds))
        unavailable("launcher inventory window changed between pages");
      const [l,t,r,b] = window.bounds;
      await ui.call({
        action: "gesture",
        x1: Math.floor(l+(r-l)*0.85), y1: Math.floor((t+b)/2),
        x2: Math.floor(l+(r-l)*0.15), y2: Math.floor((t+b)/2),
        milliseconds: 400,
        ...(native ? ui.launcherPointerAuthority(grid) : ui.gestureAuthority(grid)),
      });
      // Read after SDK completion, allowing page animation without replaying input.
      const deadline = performance.now() + 1000;
      let next = await launcherPage();
      while (signature(next.items) === key && performance.now() < deadline) {
        await new Promise(resolve => setTimeout(resolve, 100));
        next = await launcherPage();
      }
      const nextWindow = next.grid.windowRef!;
      if (nextWindow.id !== owner.id || nextWindow.type !== owner.type ||
          JSON.stringify(nextWindow.bounds) !== JSON.stringify(owner.bounds))
        unavailable("launcher inventory window changed after page gesture");
      if (signature(next.items) === key) {
        // Same settled visible page after a completed forward gesture is the
        // existing case12 endpoint contract. Offscreen nodes are never counted.
        const afterBoot = await bootObservation();
        if (beforeBoot.boot !== afterBoot.boot)
          unavailable("launcher inventory crossed a boot");
        return [...labels];
      }
      items = next.items;
    }
    unavailable("launcher inventory exceeded bounded twelve-page traversal");
  };
  let vehicleNodes: UiNode[] = [];
  const openVehicleMenu = async (vehicle: Vehicle) => {
    const deadline = Math.min(performance.now() + 15000, settingsNavigationDeadline ?? Infinity);
    const owner = "com.qinggan.app.vehiclesetting";
    const snapshot = async () => {
      const previousDeadline = settingsNavigationDeadline;
      settingsNavigationDeadline = deadline;
      try { for (;;) {
        if (performance.now() >= deadline) unavailable("OEM vehicle window viewport readiness deadline expired");
        await ownership();
        const observed = await ui.snapshot();
        await ownership();
        if (performance.now() >= deadline) unavailable("OEM vehicle window viewport readiness deadline expired");
        if (vehicleLaunchPending(observed as UiNodes)) continue;
        const focused = (observed as UiNodes).windowRefs?.filter(window => window.active && window.inputFocused) ?? [];
        const window = focused.length === 1 ? focused[0] : undefined;
        // Activity launch may publish focus before the window has a viewport.
        // Empty geometry is not a gesture target; only reread this owned transition.
        if (!window || window.rootPackage !== owner || window.rootStatus !== "available" || window.scope !== "default-display" ||
            ![1, 3].includes(window.type) || (window.bounds[2] > window.bounds[0] && window.bounds[3] > window.bounds[1])) return observed;
        if (performance.now() >= deadline) unavailable("OEM vehicle window viewport readiness deadline expired");
      } } finally { settingsNavigationDeadline = previousDeadline; }
    };
    await ui.call({ action: "open-vehicle-settings" });
    let nodes = await snapshot();
    const menu = vehicle === "fev" ? "rb_menu_drive_preference" : "rb_menu_safety_maintenance";
    // A warm wake can leave the already resumed left activity without input focus.
    // Start a different OEM left activity once, prove its focus, then reopen this one.
    // Never dispatch a gesture against an unfocused window or retry an unknown input.
    const windows = (nodes as UiNodes).windowRefs ?? [];
    const visibleOwners = new Set(nodes.filter(node => node.packageName === owner && node.visibleToUser === true &&
      node.windowRef?.rootPackage === owner && node.windowRef.rootStatus === "available" &&
      [1, 3].includes(node.windowRef.type)).map(node => node.windowRef));
    const chromeOwner = "com.qinggan.app.launcher";
    const chromeBounds = new Set<string>();
    const dockResources = new Set(["", "screen_up_navigation", "screen_up_radio_group", "screen_up_home_icon",
      "screen_up_icon_1", "screen_up_icon_2", "screen_up_icon_3", "screen_up_icon_4", "screen_up_all_app_icon",
      "screen_up_temperature_content", "screen_up_temperature_integer_text", "screen_up_temperature_vice_layout",
      "screen_up_temperature_fraction_text", "temperature_unit"]);
    const knownLeftWindows = windows.length === 4 && windows.every(window => {
      const members = nodes.filter(node => node.windowRef === window);
      if (visibleOwners.has(window)) return members.every(node => node.packageName === owner);
      if (window.type !== -1 || window.active || window.inputFocused || window.rootStatus !== "available" ||
        window.rootPackage !== chromeOwner || !members.length || members.some(node => node.packageName !== chromeOwner)) return false;
      const root = members.find(node => node.parent === null);
      if (!root || root.className !== "android.widget.FrameLayout" || root.resource !== "") return false;
      const bounds = window.bounds.join(",");
      if (chromeBounds.has(bounds)) return false;
      chromeBounds.add(bounds);
      if (["0,0,30,720", "145,0,1920,50"].includes(bounds))
        return members.length === 2 && members[1].resource === `${chromeOwner}:id/line_view` && members[1].className === "android.view.View";
      if (bounds !== "0,0,145,720") return false;
      return members.length === 15 && members.some(node => node.resource === `${chromeOwner}:id/screen_up_navigation` && node.className === "android.widget.FrameLayout") &&
        members.some(node => node.resource === `${chromeOwner}:id/screen_up_radio_group` && node.className === "android.widget.RadioGroup") &&
        members.every(node => dockResources.has(node.resource.replace(`${chromeOwner}:id/`, "")));
    });
    if (knownLeftWindows && !windows.some(window => window.inputFocused) && visibleOwners.size === 1) {
      await ui.call({ action: "open-settings" });
      const settingsNodes = await ui.snapshot();
      if ((settingsNodes as UiNodes).windowRefs?.filter(window => window.inputFocused).length !== 1)
        unavailable("unique OEM Settings focus acquisition not confirmed");
      uniqueNode(settingsNodes, node => node.parent === null && node.packageName === SETTINGS &&
        node.windowRef?.rootPackage === SETTINGS && node.windowRef.rootStatus === "available" &&
        node.windowRef.active && focusedApplication(node));
      await ui.call({ action: "open-vehicle-settings" });
      nodes = await snapshot();
    }
    let menus = nodes.filter((node) => node.packageName === owner && focusedApplication(node) && node.resource === `${owner}:id/${menu}` && node.clickable);
    for (let page = 0; menus.length !== 1 && page < 8; page++) {
      const box = uniqueNode(nodes, (node) => node.packageName === owner && node.resource === `${owner}:id/menu_container` && node.scrollable);
      const [l, t, r, b] = box.bounds;
      await ui.call({ action: "swipe", x1: Math.floor((l + r) / 2), y1: Math.floor(t + (b - t) * 0.85), x2: Math.floor((l + r) / 2), y2: Math.floor(t + (b - t) * 0.15), milliseconds: 400 });
      nodes = await snapshot();
      menus = nodes.filter((node) => node.packageName === owner && focusedApplication(node) && node.resource === `${owner}:id/${menu}` && node.clickable);
    }
    if (menus.length !== 1) unavailable(`unique OEM ${vehicle} menu not exposed`);
    if (menus[0].checked || menus[0].selected) {
      try {
        const control = oemFeature(nodes, vehicle);
        if (focusedApplication(control) && control.windowRef && control.windowRef === menus[0].windowRef && control.bounds[3] > control.bounds[1]) {
          vehicleNodes = nodes;
          return control;
        }
      } catch { /* A selected menu alone does not prove the feature scene. */ }
    }
    await ui.tap(menus[0]);
    return null;
  };
  const openVehicle = async (vehicle: Vehicle) => {
    const owner = "com.qinggan.app.vehiclesetting";
    try { const selected = await openVehicleMenu(vehicle); if (selected) return selected; }
    catch (error) {
      if (!(error instanceof GesturePreInputDrift)) throw error;
      await ui.call({ action: "open-settings" });
      const selected = await openVehicleMenu(vehicle); if (selected) return selected;
    }
    let nodes: UiNode[];
    for (let page = 0; page < 8; page++) {
      nodes = await ui.snapshot();
      try {
        const control = oemFeature(nodes, vehicle);
        if (!focusedApplication(control) || control.bounds[3] <= control.bounds[1]) throw new Error(`OEM ${vehicle} control is outside the visible page`);
        vehicleNodes = nodes;
        return control;
      } catch (error) {
        const views = nodes.filter((node) => node.packageName === owner && focusedApplication(node) && node.scrollable && node.resource === `${owner}:id/${vehicle === "fev" ? "fragment_drive_preference_scroll" : "fragment_scrollview"}`);
        if (views.length !== 1) unavailable(String(error));
        const [l, t, r, b] = views[0].bounds;
        await ui.call({ action: "swipe", x1: Math.floor((l + r) / 2), y1: Math.floor(t + (b - t) * 0.85), x2: Math.floor((l + r) / 2), y2: Math.floor(t + (b - t) * 0.15), milliseconds: 400 });
      }
    }
    unavailable(`OEM ${vehicle} selected-state control not found after bounded scrolling`);
  };
  const oemValue = async (vehicle: Vehicle) => (await openVehicle(vehicle)).checked;
  const selectOem = async (vehicle: Vehicle, want: boolean) => {
    let selected = await openVehicle(vehicle);
    if (vehicle === "fev" && want) {
      const electric = vehicleNodes.filter((node) => node.packageName === "com.qinggan.app.vehiclesetting" && focusedApplication(node) && node.resource === "com.qinggan.app.vehiclesetting:id/fragment_radio_button_power_mode_electric" && node.clickable && node.enabled);
      if (electric.length === 1 && !electric[0].checked) {
        await ui.tap(electric[0]);
        selected = oemFeature(await ui.snapshot(), vehicle);
      }
    }
    if (selected.checked !== want) {
      if (!selected.enabled) unavailable(`OEM ${vehicle} control is disabled; no synthetic CAN input is supplied`);
      await ui.tap(selected);
      const nodes = await ui.snapshot();
      await confirmOemDialog(ui, vehicle, nodes);
      selected = oemFeature(await ui.snapshot(), vehicle);
    }
    if (selected.checked !== want) throw new Error(`OEM ${vehicle} selected-state mutation did not confirm`);
    await screenshot(`${vehicle}-${want ? "on" : "off"}`);
  };
  const powerCycle = async () => {
    if (!native) unavailable("warm power transport requires native CAN observation");
    const boot = (await bootObservation()).boot;
    const states = ["IVI_SOC_MODESET", "HUM_VSP_FUNCTION_SW"];
    const readCan = async (fields: Record<string, unknown>) => {
      try { return await native.can(fields); }
      catch { unavailable("warm power transport CAN read failed; accepted keys are not replayed"); }
    };
    const baseline = await readCan({ states, limit: 32 });
    if (baseline.status !== "ready" || baseline.boot_id !== boot || typeof baseline.source_instance !== "string" ||
        baseline.acc?.status !== "observed" || baseline.acc.raw !== 2 || !baseline.history?.next_cursor) {
      unavailable("warm power transport baseline ACC ON is unavailable");
    }
    const source = baseline.source_instance;
    let cursor = baseline.history.next_cursor;
    const resets = new Set<string>();
    const waitAcc = async (raw: number) => {
      const deadline = performance.now() + 10000;
      let last: unknown = null;
      while (performance.now() < deadline) {
        const current = await readCan({ states, cursor, limit: 32,
          timeout_ms: Math.max(1, Math.min(1000, Math.ceil(deadline - performance.now()))) });
        if (current.status !== "ready" || current.boot_id !== boot || current.source_instance !== source) {
          unavailable("warm power transport CAN boot/source changed or unavailable");
        }
        if (current.history?.gap !== null || !current.history?.next_cursor ||
            current.history.next_cursor.source_instance !== source || !Array.isArray(current.events)) {
          unavailable("warm power transport CAN history is incomplete");
        }
        cursor = current.history.next_cursor;
        for (const event of current.events) {
          if (event.source_instance === source && event.kind === "state_applied" &&
              event.method === "acc_on_reset" && event.raw === 2) resets.add(event.field);
        }
        last = current.acc;
        if (current.acc?.status === "observed" && current.acc.raw === raw && !current.history.has_more) {
          if (raw === 2 && current.coverage?.vehicle_state_setter === true &&
              states.some((state) => !resets.has("vehicle_state." + state))) {
            unavailable("warm power transport ACC ON lacks profile reset history");
          }
          return;
        }
        if (!current.history.has_more) await new Promise((resolve) => setTimeout(resolve, 100));
      }
      unavailable("warm power transport ACC " + raw + " edge not observed: " + JSON.stringify(last));
    };
    const requirePower = async (wakefulness: string, display: string) => {
      let system;
      try { system = await native.system({ timeout_ms: 5000 }); }
      catch (error) { unavailable("warm power transport Android power read failed: " + String(error)); }
      const power = system.value?.power;
      if (system.boot_id !== boot || system.value?.boot_id !== boot || power?.status !== "ready" ||
          power.complete !== true || power.display_ready !== true ||
          power.wakefulness !== wakefulness || power.display_state !== display) {
        unavailable("warm power transport Android power is not confirmed " + wakefulness + "/" + display);
      }
    };
    await ui.call({ action: "key", key: "sleep" });
    await waitAcc(0);
    await requirePower("Asleep", "OFF");
    await native.call("input.key.press", { key: "Power" });
    await waitAcc(2);
    await requirePower("Awake", "ON");
    if ((await bootObservation()).boot !== boot) unavailable("warm power transport changed guest boot");
  };
  const recordVehicleTiming = (vehicle: Vehicle, cycle: "cold" | "warm", baseline: Awaited<ReturnType<typeof observations>>, current: Awaited<ReturnType<typeof observations>>) => {
    const authority = vehicleAuthority(baseline, current, vehicle);
    if (!authority.targetStarttime || !authority.endpointEpoch) unavailable("vehicle timing lifetime missing");
    const fence = authority as TimingFence;
    const semantics = vehicleTimingSemantics(current.logData.diagnostics, vehicle, fence);
    let timing;
    try {
      timing = vehicleTiming(current.logData.records, current.logData.diagnostics, vehicle, fence, semantics,
        cycle === "warm" ? sequence(baseline.logData.records) : -1);
    } catch (error) { unavailable(`actual vehicle timing evidence unavailable: ${String(error)}`); }
    const path = join(c.output, `${vehicle}-${cycle}-timing.json`);
    writeFileSync(path, JSON.stringify({ schemaVersion: 1, measurement: "actual-backend-read-observation", cycle,
      latencySla: null, earlyRecordsComplete: "unverified", archiveSha256: digest(readFileSync(current.archive)),
      authority: fence, ...timing }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
    return path;
  };
  const vehicleProgram = async (vehicle: Vehicle) => {
    const id = VEHICLE[vehicle], desired = vehicle === "fev";
    await toggle(id, false);
    await subcheck(`${vehicle}-cold-baseline`, async () => {
      await selectOem(vehicle, desired);
      await reboot();
      if ((await oemValue(vehicle)) !== !desired) throw new Error(`OEM ${vehicle} cold default differs with agent disabled`);
    });
    await subcheck(`${vehicle}-warm-baseline`, async () => {
      await selectOem(vehicle, desired);
      await powerCycle();
      if ((await oemValue(vehicle)) !== !desired) throw new Error(`OEM ${vehicle} power default differs with agent disabled`);
    });
    await toggle(id, true);
    await subcheck(`${vehicle}-armed-current`, async () => {
      const baseline = await observations();
      await selectOem(vehicle, desired);
      const current = await currentVehicle(vehicle, desired, baseline, true);
      return [baseline.archive, current.archive];
    });
    await subcheck(`${vehicle}-cold-restore`, async () => {
      await reboot();
      const baseline = await observations();
      const current = await currentVehicle(vehicle, desired, baseline, true);
      if ((await oemValue(vehicle)) !== desired) throw new Error(`OEM ${vehicle} cold restore differs`);
      const timing = recordVehicleTiming(vehicle, "cold", baseline, current);
      return [baseline.archive, current.archive, timing];
    });
    await subcheck(`${vehicle}-warm-restore`, async () => {
      const baseline = await observations();
      await powerCycle();
      const current = await currentVehicle(vehicle, desired, baseline, true);
      try { accCycle(fresh(baseline, current), vehicle); resetRestore(fresh(baseline, current), vehicle, desired); }
      catch (error) { unavailable(String(error)); }
      if ((await oemValue(vehicle)) !== desired) throw new Error(`OEM ${vehicle} warm restore differs`);
      const timing = recordVehicleTiming(vehicle, "warm", baseline, current);
      return [baseline.archive, current.archive, timing];
    });
  };

  const drawerProgram = async () => {
    if (!native) unavailable("FEV drawer requires native UI and CAN");
    const owner = "com.qinggan.app.launcher";
    const findSwitch = (nodes: UiNode[]) => uniqueNode(nodes, n => n.packageName === owner &&
      n.resource === owner + ":id/force_ev_switcher" && n.visibleToUser === true && n.enabled);
    const openDrawer = async (operationDeadline=Infinity) => {
      const boot=await probe(true);
      let nodes = await ui.snapshot();
      if (nodes.some(n => n.packageName === owner && n.resource === owner + ":id/tv_title" && ["Electric","Hybrid","Save","纯电优先","油电混合","保电模式"].includes(n.text) && n.visibleToUser)) return nodes;
      const edges = nodes.filter(n => n.packageName === owner && n.resource === owner + ":id/line_view" &&
        n.className === "android.view.View" && n.visibleToUser === true && n.enabled && n.parent !== null &&
        n.windowRef?.type === 3 && n.windowRef.rootPackage === owner && n.windowRef.bounds[0] === 0 &&
        n.windowRef.bounds[3] > n.windowRef.bounds[2] && n.bounds[0] >= 0 &&
        n.bounds[2] <= n.windowRef.bounds[2] && n.bounds[3]-n.bounds[1] > n.bounds[2]-n.bounds[0]);
      if (edges.length !== 1) unavailable("unique actual OEM left drawer receiver unavailable");
      const edge = edges[0], [x,y] = center(edge), screenRight = Math.max(...nodes.map(n => n.windowRef?.bounds[2] ?? n.bounds[2]));
      const homeSignature=drawerClosedHomeSignature(nodes);
      const observer={source:nativeInputTree?.source_instance,pid:nativeInputTree?.observer_pid};
      const dispatchOpen=async(receiver:UiNode)=>ui.call({ action: "gesture", x1:x, y1:y, x2:Math.floor(screenRight/2), y2:y, milliseconds:300, ...ui.gestureAuthority(receiver, true) });
      if (performance.now() >= operationDeadline) unavailable("drawer preparation deadline expired before gesture");
      await dispatchOpen(edge);
      const deadline=Math.min(operationDeadline,performance.now()+5000);
      try {return await awaitDrawerPowerCard(ui,async()=>nativeInputTree?.boot_id??"",boot,deadline,()=>performance.now(),homeSignature?{
        afterMs:2000,
        reopen:async fresh=>{
          if(performance.now()>=deadline || nativeInputTree?.boot_id!==boot ||
             !observer.source || !Number.isSafeInteger(observer.pid) || observer.pid<1 ||
             nativeInputTree?.source_instance!==observer.source || nativeInputTree?.observer_pid!==observer.pid ||
             drawerClosedHomeSignature(fresh)!==homeSignature)
            throw new Error("drawer reopen authority changed; gesture not replayed");
          const receiver=fresh.find(n=>n.resource===edge.resource && n.packageName===edge.packageName &&
            n.windowRef?.id===edge.windowRef?.id && JSON.stringify(n.bounds)===JSON.stringify(edge.bounds));
          if(!receiver) throw new Error("drawer reopen receiver changed; gesture not replayed");
          await dispatchOpen(receiver);
        }
      }:undefined);}
      catch(error) {unavailable(String(error));}
    };

    const stableDrawer = async () => {
      // Preparation and settlement share a finite bound; each snapshot independently
      // fences the owned session, boot and Enforcing state before native UI readback.
      const boot=await probe(true),settleMs=300,deadline=performance.now()+10000;
      let previous="",stableSince:number|null=null;
      let nodes=await openDrawer(deadline);
      // Baseline belongs to the first accepted owned read, never a preceding UI phase.
      const sourceInstance=nativeInputTree?.source_instance,observerPid=nativeInputTree?.observer_pid;
      if(typeof boot!=="string" || !boot || nativeInputTree?.boot_id!==boot ||
         typeof sourceInstance!=="string" || !sourceInstance ||
         !Number.isSafeInteger(observerPid) || observerPid<1)
        unavailable("drawer settlement initial observer identity unavailable");
      for(let attempt=0;attempt<64 && performance.now()<deadline;attempt++) {
        const observedAt=performance.now();
        if(nativeInputTree?.boot_id!==boot || nativeInputTree?.source_instance!==sourceInstance ||
           nativeInputTree?.observer_pid!==observerPid) unavailable("drawer settlement crossed boot or observer identity");
        const card=nodes.filter(n=>n.packageName===owner && n.resource===owner+":id/tv_title" &&
          ["Electric","Hybrid","Save","纯电优先","油电混合","保电模式"].includes(n.text) && n.visibleToUser===true);
        if(card.length!==1) {
          previous="";stableSince=null;
          nodes=await openDrawer(deadline);
          continue;
        }
        const window=card[0].windowRef;
        if(!window || window.type!==3 || !window.active || !window.inputFocused ||
           window.rootPackage!==owner || window.rootStatus!=="available" || window.scope!=="default-display")
          unavailable("drawer settlement lost focused window authority");
        const relevant=nodes.filter(n=>n.windowRef?.id===window.id && n.windowRef.rootPackage===owner && n.windowRef.type===window.type &&
          JSON.stringify(n.windowRef.bounds)===JSON.stringify(window.bounds) && n.packageName===owner && n.visibleToUser===true &&
          [owner+":id/rl_root",owner+":id/tv_title",owner+":id/force_ev_switcher"].includes(n.resource));
        const signature=JSON.stringify(relevant.map(n=>[n.resource,n.text,n.selected,n.bounds,window.id,window.bounds]));
        if(signature!==previous || stableSince===null) {previous=signature;stableSince=observedAt;}
        else if(observedAt-stableSince>=settleMs) return nodes;
        // Do not re-open a visible drawer or add redundant CLI probes between samples.
        nodes=await ui.snapshot();
      }
      unavailable("drawer target geometry did not settle within bounded preparation/readback");
    };
    const tapSwitch = async (control:UiNode) => {
      // inspect/bootObservation use ui.call and invalidate held UI nodes.
      // Read the existing root/Enforcing probe directly without replacing the UI observation.
      const boot=await probe(true);
      await tapDrawerSwitch(ui,control,async()=>{
        const fresh=findSwitch(await stableDrawer());
        if(await probe(true)!==boot) throw new Error("drawer reacquisition crossed boot identity");
        return fresh;
      },performance.now()+5000);
    };
    const drawerCheck = async (id: string, action: () => Promise<any>) => {
      await subcheck(id,action);
      const row=evidence.cases.find((row:Case)=>row.number===13);
      const check=row?.subchecks.find((check:Subcheck)=>check.id===id);
      if(check?.status!=="passed") unavailable("dependent drawer chain stopped after "+id+": "+(check?.reason??"missing result"));
    };
    const drawerState = async (raw: number, armed: boolean) => {
      const deadline = performance.now()+10000;
      while (performance.now()<deadline) {
        const before=await observations(),can=await native.can({states:["IVI_SOC_MODESET"],limit:32}),after=await observations();
        let authority;
        try {authority=drawerCurrentAuthority(before,after,catalog,digest(readFileSync(join(c.closure,"agents.json"))),digest(readFileSync(join(c.closure,"voboost.json"))),manifest.releaseIdentity);}
        catch(error) {unavailable(String(error));}
        let state=after.diagnosticArmed.fev;
        if(state.status==="unavailable") {
          try {state=rootVehicleState(after.logData.records,"fev",authority,sequence(before.logData.records),before.logData.records);}
          catch(error) {if(!(error instanceof Unavailable)) throw error;}
        }
        if(can.status==="ready" && can.boot_id===before.boot && can.boot_id===after.boot &&
           can.values?.["vehicle_state.IVI_SOC_MODESET"]?.raw===raw && state.status==="present" && state.value===armed)
          return [before.archive,after.archive];
        await new Promise(resolve=>setTimeout(resolve,100));
      }
      unavailable("drawer CAN and saved intent did not confirm");
    };
    const cycle = async (raw: number, armed: boolean) => {
      const boot=await probe(true);
      const initial=drawerModeCard(await stableDrawer());
      // Preparation settles separately. The bounded operation includes one reopen and fresh geometry.
      const deadline=performance.now()+10000;
      const sourceInstance=nativeInputTree?.source_instance,observerPid=nativeInputTree?.observer_pid;
      if(typeof sourceInstance!=="string" || !sourceInstance || !Number.isSafeInteger(observerPid) || observerPid<1) throw new Error("drawer mode observer epoch unavailable");
      await tapDrawerModeCard(ui,initial,async()=>{
        const fresh=drawerModeCard(await stableDrawer());
        if(await probe(true)!==boot) throw new Error("drawer mode reacquisition crossed boot identity");
        if(nativeInputTree?.source_instance!==sourceInstance || nativeInputTree?.observer_pid!==observerPid) throw new Error("drawer mode reacquisition crossed observer epoch");
        return fresh;
      },deadline);
      return drawerState(raw,armed);
    };
    await drawerCheck("fev-drawer-arm", async()=>{
      await toggle(VEHICLE.fev,true);
      await prepareDrawerHome(ui,()=>probe(true),performance.now()+5000);
      const boot=(await bootObservation()).boot;
      const underlyingNodes=await ui.snapshot();
      const underlyingOwners=[...new Set(underlyingNodes.filter(n=>n.windowRef?.active && n.windowRef.inputFocused).map(n=>n.windowRef!.rootPackage))];
      const underlyingOwner=underlyingOwners.length===1 && ["com.qinggan.app.setting","com.qinggan.app.vehiclesetting"].includes(underlyingOwners[0]) ? underlyingOwners[0] : undefined;
      const preconditionPaths:string[]=[];
      let control=findSwitch(await stableDrawer());
      if(control.selected) {
        await tapSwitch(control);
        preconditionPaths.push(...await drawerState(2,false));
        control=findSwitch(await stableDrawer());
        if(control.selected) throw new Error("drawer ARM OFF precondition reactivated FEV");
      }
      if(!control.selected) {

        const deadline=performance.now()+10000;
        await tapSwitch(control);
        try {await awaitDrawerOn(ui,()=>native.can({states:["IVI_SOC_MODESET"],limit:32}),boot,deadline,()=>performance.now(),async nodes=>{
          const target=drawerCloseTarget(nodes);
          await ui.call({action:"gesture",x1:target.x,y1:target.y,x2:target.x2,y2:target.y,milliseconds:target.milliseconds,...ui.gestureAuthority(target.node,true)});
        },underlyingOwner);}
        catch(error) {await observations();throw error;}
      }
      const paths=await drawerState(5,true);
      if(!findSwitch(await openDrawer()).selected) throw new Error("drawer FEV ON not selected");
      return [...preconditionPaths,...paths];
    });
    await drawerCheck("fev-drawer-hybrid",()=>cycle(3,true));
    await drawerCheck("fev-drawer-save",()=>cycle(4,true));
    await drawerCheck("fev-drawer-electric-restore",async()=>{
      const paths=await cycle(5,true);
      if(!findSwitch(await openDrawer()).selected) throw new Error("drawer Electric FEV restore not selected");
      return paths;
    });
    await drawerCheck("fev-drawer-explicit-off-hold",async()=>{
      const source=readFileSync(join(c.workspaceRoot,"voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/forcedev/VehicleDriveForcedEvAgent.java"),"utf8");
      const attempts=/private static final int RECONCILE_ATTEMPTS = (\d+);/.exec(source);
      const delay=/private static final long RECONCILE_DELAY_MS = (\d+)L;/.exec(source);
      if(!attempts||!delay) unavailable("source-bound reconcile interval unavailable");
      const duration=Number(attempts[1])*Number(delay[1]);
      if(!Number.isSafeInteger(duration)||duration<1||duration>10000) unavailable("source reconcile interval invalid");
      const initial=await native.can({states:["IVI_SOC_MODESET"],limit:32});
      if(initial.status!=="ready"||!initial.history?.next_cursor) unavailable("drawer CAN initial cursor unavailable");
      let cursor=initial.history.next_cursor;
      const begin=cursor.after_seq, batches:any[]=[];
      const control=findSwitch(await stableDrawer());
      if(!control.selected) throw new Error("drawer explicit OFF requires selected FEV");
      await tapSwitch(control);
      const paths=await drawerState(2,false), deadline=performance.now()+duration+5000;
      while(performance.now()<deadline) {
        const batch=await native.can({states:["IVI_SOC_MODESET"],cursor,limit:32}); batches.push(batch);
        cursor=batch.history?.next_cursor;
        try { drawerOffJournal(batches,initial.boot_id,initial.source_instance,begin,duration); break; }
        catch(error) {
          if(String(error).includes("re-enabled")) throw error;
          if(String(error).includes("gap")||String(error).includes("coverage")) unavailable(String(error));
          await new Promise(resolve=>setTimeout(resolve,100));
        }
      }
      try { drawerOffJournal(batches,initial.boot_id,initial.source_instance,begin,duration); }
      catch(error) { if(String(error).includes("re-enabled")) throw error; unavailable(String(error)); }
      if(findSwitch(await openDrawer()).selected) throw new Error("drawer FEV OFF not selected");
      const path=join(c.output,"fev-drawer-off-can-journal.json");
      writeFileSync(path,JSON.stringify({schemaVersion:1,boot:initial.boot_id,source:initial.source_instance,startCursor:begin,durationMs:duration,batches},null,2)+"\n",{flag:"wx",mode:0o600});
      return [...paths,path];
    });
    await drawerCheck("fev-drawer-off-circle",async()=>{
      const paths=[...await cycle(3,false),...await cycle(4,false),...await cycle(2,false)];
      if(findSwitch(await openDrawer()).selected) throw new Error("drawer OFF circle rearmed FEV");
      return paths;
    });
    await drawerCheck("fev-drawer-cold-off",async()=>{
      await reboot();
      const paths=await drawerState(2,false);
      await prepareDrawerHome(ui,()=>probe(true),performance.now()+5000);
      if(findSwitch(await openDrawer()).selected) throw new Error("cold reboot rearmed drawer FEV");
      return paths;
    });
  };

  const uninstallDialog = async () => {
    const first = evidence.actions.length;
    if (!copy) unavailable("source-bound uninstall copy unavailable");
    const captions = copy.languages[language].uninstall;
    await panel(true);
    await tapText(captions.buttonText);
    const nodes = await ui.snapshot(), texts = new Set(nodes.filter((node) => node.packageName === SETTINGS && focusedApplication(node)).map((node) => node.text));
    for (const text of [captions.description, captions.confirmLabel, captions.cancelLabel])
      if (!texts.has(text)) throw new Error("source-bound uninstall dialog caption missing");
    await screenshot(`${language}-uninstall-dialog`);
    await tapText(captions.cancelLabel);
    const status = await cli(["--status"]); resultOk(status);
    if (jsonReceipt(status.stdout).installedVersion !== manifest.releaseIdentity) throw new Error("cancelled uninstall changed installed version");
    await panel(true);
    return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path));
  };
  const switchKeyboard = async (want: "ru" | "en") => {
    const nodes = await ui.snapshot();
    const labels = want === "en" ? /^(EN|English|英语|Английский)$/ : /^(RU|Русский|Russian|俄语)$/;
    const editors = nodes.filter(node => node.packageName === "com.pateonavi.naviapp" && node.focused && node.className === "android.widget.EditText" && focusedApplication(node) && node.windowRef?.type === 1);
    if (editors.length !== 1) unavailable("fresh focused editor required for keyboard layout switch");
    const keys = nodes.filter((node) => node.visibleToUser === true && node.windowRef?.type === 2 && node.windowRef.capture === editors[0].windowRef?.capture && node.windowRef.rootPackage === node.packageName && /^com.qinggan.app.qgime(?:\.second)?$/.test(node.packageName) && node.clickable && node.enabled && labels.test(node.text || node.description));
    if (keys.length === 0) { await canvasKey(undefined, want); return; }
    if (keys.length !== 1) unavailable("requested layout switch is not a unique actual keyboard key");
    await ui.tap(keys[0]);
  };
  // A canvas has no semantic key nodes. Only explicit reviewed live-pixel evidence may supply coordinates.
  const canvasKey = async (character?: string, language?: "en" | "ru", expectedPrefix?: string): Promise<void> => {
    if (!native || !c.reviewedKeyboardMaps) unavailable("canvas keyboard requires explicit reviewedKeyboardMaps evidence");
    const mapFile = c.reviewedKeyboardMaps!;
    if (!isAbsolute(mapFile) || !mapFile.startsWith(dirname(c.output)+sep) || realpathSync(mapFile) !== mapFile || !lstatSync(mapFile).isFile() || lstatSync(mapFile).size > 1024*1024) unavailable("reviewed keyboard map path unsafe");
    const document = JSON.parse(readFileSync(mapFile,"utf8"));
    if (document.schema !== 1 || !Array.isArray(document.layouts) || !document.layouts.length || document.layouts.length > 16 || typeof document.theme !== "string" || typeof document.sourceRevision !== "string") unavailable("reviewed keyboard map envelope differs");
    const baselines = new Map<string,Uint8Array>();
    const rgba = (bytes: Buffer) => {
      const image=pngRaster(bytes), pixels=new Uint8Array(image.width*image.height*4);
      for(let i=0;i<image.width*image.height;i++) { for(let ch=0;ch<3;ch++)pixels[i*4+ch]=image.pixels[i*image.channels+ch]; pixels[i*4+3]=image.channels===4?image.pixels[i*4+3]:255; }
      return { ...image, rgba:pixels };
    };
    for(const row of document.layouts) {
      if(typeof row.baselinePng !== "string" || !/^[a-f0-9]{64}$/.test(row.baselinePngSha256 ?? "")) unavailable("reviewed baseline descriptor differs");
      const file=join(dirname(mapFile),row.baselinePng);
      if(isAbsolute(row.baselinePng) || relative(dirname(mapFile),file).startsWith("..") || realpathSync(file)!==file || !lstatSync(file).isFile() || lstatSync(file).size>32*1024*1024) unavailable("reviewed baseline path unsafe");
      const bytes=readFileSync(file), decoded=rgba(bytes);
      if(digest(bytes)!==row.baselinePngSha256 || digest(Buffer.from(decoded.rgba))!==row.baselineSha256 || decoded.width!==row.width || decoded.height!==row.height) unavailable("reviewed baseline bytes differ");
      baselines.set(row.baselineSha256,decoded.rgba);
    }
    const canvas = (nodes: UiNode[]) => {
      const node=keyboardCanvasNode(nodes);
      if(!node) unavailable("unique unclipped live keyboard canvas/editor unavailable");
      return node;
    };
    let postCaptureEditorNodes: UiNode[] | null = null;
    let postCaptureDispatch: { nodes: UiNode[]; frame: KeyboardFrame } | null = null;
    const take = async (): Promise<KeyboardFrame> => {
      postCaptureEditorNodes=null;
      postCaptureDispatch=null;
      const before=canvas(await ui.snapshot()), bounds=before.bounds, boot=snapshotBoot;
      if(!boot) unavailable("keyboard capture boot observation absent");
      const source=join(c.voyemuRoot,".local/build/tmp","keyboard-capture-"+randomUUID());
      const beforeAction=evidence.actions.findLast((a:any)=>a.nativeOperation==="session.ui")!;
      const rect={x:bounds[0],y:bounds[1],width:bounds[2]-bounds[0],height:bounds[3]-bounds[1]};
      keyboardCaptureScope={directory:source,rect};
      let capture;
      const observedAt=performance.now(); // Conservative start of native capture, after preparation.
      try { capture=await native!.capture(source,500,["primary"],rect); }
      finally { keyboardCaptureScope=null; }
      const action=evidence.actions.findLast((a:any)=>a.nativeOperation==="session.screenshot")!;
      const afterNodes=await ui.snapshot(), after=canvas(afterNodes);
      const afterAction=evidence.actions.findLast((a:any)=>a.nativeOperation==="session.ui")!;
      if(capture.manifest.android_boot_id!==boot || JSON.stringify([before.packageName,before.resource,before.bounds,before.windowRef!.id])!==JSON.stringify([after.packageName,after.resource,after.bounds,after.windowRef!.id])) unavailable("keyboard capture geometry/window drift");
      const decoded=rgba(readFileSync(capture.screens.primary!));
      const target=join(c.output,"keyboard-captures",basename(source));
      retainKeyboardCapture(c.voyemuRoot,source,target);
      for (const file of ["manifest.json","primary.png"]) {
        const path=join(target,file),bytes=readFileSync(path);
        action.artifacts.push({path,sha256:digest(bytes),kind:file.endsWith(".png")?"png":"archive",boot:boot,locale:language,kit:frozen.package,runtime});
      }
      const wire=JSON.parse(readFileSync(action.artifacts.find((a:any)=>a.kind==="stdout").path,"utf8"));
      action.context={kit:frozen.package,runtime,serial,bootId:boot,locale:language};
      action.nativeAuthority={requestId:wire.request_id,operationId:wire.operation_id,sessionId:capture.manifest.session_id,manifest:join(target,"manifest.json"),output:join(target,"primary.png"),display:"primary"};
      action.keyboardDiagnostic={before:beforeAction.id,after:afterAction.id};
      keyboardDiagnosticEvidence(evidence,action);
      persist();
      // Consumed once by the immediately following prefix check; never a timed cache.
      postCaptureEditorNodes=afterNodes;
      const frame: KeyboardFrame = {boot:boot,frameBoot:capture.manifest.android_boot_id,session:capture.manifest.session_id,pid:capture.manifest.android_pid,window:after.windowRef!.id,resource:after.resource,bounds:bounds as readonly [number,number,number,number],geometryVerified:true,observedAt,width:decoded.width,height:decoded.height,rgba:decoded.rgba};
      postCaptureDispatch={nodes:afterNodes,frame};
      return frame;
    };
    const editorCheck = (nodes: UiNode[], expected: string) => {
      const editors=nodes.filter(n=>n.packageName==="com.pateonavi.naviapp" && focusedApplication(n) && n.className==="android.widget.EditText" && n.focused);
      if(editors.length!==1) unavailable("unique focused editor absent for canvas control");
      const n=editors[0];
      committedPrefix({password:n.password,focused:n.focused,visible:n.visibleToUser===true,enabled:n.enabled,value:editorValue(n),showingHint:n.showingHint,selectionStart:n.selectionStart,selectionEnd:n.selectionEnd},expected);
    };
    const initial=await ui.snapshot(), editors=initial.filter(n=>n.packageName==="com.pateonavi.naviapp" && focusedApplication(n) && n.className==="android.widget.EditText" && n.focused);
    if(editors.length!==1) unavailable("focused editor unavailable for canvas transition");
    const prefix=expectedPrefix ?? editorValue(editors[0]);editorCheck(initial,prefix);
    await awaitKeyboardCanvas(()=>ui.snapshot(),performance.now()+5000);
    await reviewedKeyboardRoute(document.layouts as ReviewedLayout[],sha=>{const bytes=baselines.get(sha);if(!bytes)throw new Error("reviewed baseline absent");return bytes;},
      async()=>[await take(),await take()],async(point,second)=>{
        // take() already validated the fresh post-image snapshot and owned boot.
        // Consume its exact frame once; gestureAuthority rejects invalidated nodes.
        const observation=postCaptureDispatch;
        postCaptureDispatch=null;
        if(!observation || observation.frame!==second) unavailable("keyboard post-capture dispatch observation absent");
        const nodes=observation.nodes, current=canvas(nodes);
        if(snapshotBoot!==second.boot || current.windowRef!.id!==second.window || current.resource!==second.resource || JSON.stringify(current.bounds)!==JSON.stringify(second.bounds)) unavailable("keyboard identity changed before dispatch");
        const captureAge=performance.now()-second.observedAt;
        if(captureAge<0 || captureAge>2000) unavailable("keyboard capture age before dispatch: "+Math.round(captureAge)+"ms");
        editorCheck(nodes,prefix);
        await ui.call({action:"gesture",x1:point.x,y1:point.y,x2:point.x,y2:point.y,milliseconds:100,...ui.gestureAuthority(current)});
      },async()=>{
        const observed=postCaptureEditorNodes;
        postCaptureEditorNodes=null;
        editorCheck(observed ?? await ui.snapshot(),prefix);
      },
      {theme:document.theme,sourceRevision:document.sourceRevision,now:()=>performance.now()},
      {language:character==="backspace" ? undefined : language ?? (/[А-Яа-яЁё]/.test(character ?? "")?"ru":"en"),label:character});
  };
  const input = async (text: string, reuse = false, prefix = "") => {
    let baseline = await observations();
    const firstAction = evidence.actions.length;
    let nodes: UiNode[];
    if (!reuse) {
      await ui.call({ action: "open-navigation" });
      nodes = await ui.snapshot();
      const safetyConfirm = navigationSafetyConfirm(nodes);
      if (safetyConfirm) {
        await ui.tap(safetyConfirm);
        nodes = await ui.snapshot();
        if (navigationSafetyConfirm(nodes)) unavailable("Navi safety confirmation has not dismissed; no input is replayed");
      }
    } else nodes = await ui.snapshot();
    if (!reuse) {
      const searchEntry = navigationSearchEntry(nodes);
      if (searchEntry) {
        await ui.tap(searchEntry);
        nodes = await ui.snapshot();
      }
    }
    const fields = nodes.filter(
      (n) => n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) && n.className === "android.widget.EditText",
    );
    if (fields.length !== 1)
      unavailable("Navi focused editor setup not exposed as unique semantic control");
    const focusValue=editorValue(fields[0]);
    if (!prefix && (!focusValue || !fields[0].focused || !editorCaretAtEnd(fields[0]))) {
      // A center tap can move an existing caret into the text. End placement is verified below.
      const field=fields[0], [left,top,right,bottom]=field.bounds;
      if(focusValue && (right-left<=16 || bottom<=top || JSON.stringify(field.rawBounds ?? field.bounds)!==JSON.stringify(field.bounds))) unavailable("unclipped editor end-focus geometry unavailable");
      const [x,y]=focusValue ? [right-8,Math.floor((top+bottom)/2)] : center(field);
      await ui.call({action:"tap",x,y,...ui.gestureAuthority(field)});
    }
    nodes = await ui.snapshot();
    let editor = nodes.filter(
      (n) =>
        n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
        n.focused &&
        n.className === "android.widget.EditText",
    );
    if (editor.length !== 1) unavailable("fresh focused editor not uniquely observed");
    if(!prefix && focusValue && (editorValue(editor[0])!==focusValue || !editorCaretAtEnd(editor[0]))) unavailable("editor end-focus did not preserve exact text and observed end caret");
    if (prefix && editorValue(editor[0]) !== prefix) throw new Error("fresh mixed-input prefix differs");
    if (!prefix && editorValue(editor[0]) !== "") {
      const clear = nodes.filter(
        (n) =>
          n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
          n.clickable &&
          n.parent === editor[0].parent &&
          /^(Clear|清除|Очистить)$/.test(n.text || n.description),
      );
      if (clear.length > 1) unavailable("ambiguous editor clear controls");
      if (clear.length === 1) await ui.tap(clear[0]);
      else await clearEditorByBackspace(async()=>{
        const observed=await ui.snapshot();
        const candidates=observed.filter(n=>n.packageName==="com.pateonavi.naviapp" && focusedApplication(n) && n.className==="android.widget.EditText" && n.focused);
        if(candidates.length!==1) unavailable("unique focused editor absent before deletion");
        const n=candidates[0];
        return {identity:JSON.stringify([snapshotBoot,n.packageName,n.resource,n.className,n.windowRef?.id,n.bounds]),password:n.password,focused:n.focused,visible:n.visibleToUser===true,enabled:n.enabled,value:editorValue(n),showingHint:n.showingHint,selectionStart:n.selectionStart,selectionEnd:n.selectionEnd};
      },async(expected)=>{
        if(native && c.reviewedKeyboardMaps) {
          await awaitKeyboardCanvas(()=>ui.snapshot(),performance.now()+5000);
          await canvasKey("backspace",undefined,expected);
        } else await ui.call({action:"key",key:"delete"});
      });
      nodes = await ui.snapshot();
      editor = nodes.filter(
        (n) =>
          n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
          n.focused &&
          n.className === "android.widget.EditText",
      );
      if (editor.length !== 1 || editorValue(editor[0]) !== "")
        unavailable("clear did not prove an exact empty focused field");
    }
    if (!prefix) emptyEditor(nodes);
    if (prefix && !editorCaretAtEnd(editor[0])) unavailable("mixed input requires observed caret at end");
    const codepoints = Array.from(text);
    for (const [index, ch] of codepoints.entries()) {
      if (index === codepoints.length - 1) baseline = await observations();
      nodes = await ui.snapshot();
      if (
        !nodes.some(
          (n) =>
            n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
            n.focused &&
            n.className === "android.widget.EditText",
        )
      )
        unavailable("focused editor not proven before actual keyboard key");
      await actualKeyboardInput(()=>keyboardKey(nodes,ch),key=>ui.tap(key),()=>canvasKey(ch,undefined,prefix+codepoints.slice(0,index).join("")),error=>error instanceof Unavailable && /live keyboard layout not proven from actual visible keys/.test(error.message));
      const committed=(await ui.snapshot()).filter(n=>n.packageName==="com.pateonavi.naviapp" && focusedApplication(n) && n.className==="android.widget.EditText" && n.focused);
      if(committed.length!==1) unavailable("unique focused editor absent after actual key");
      committedPrefix({password:committed[0].password,focused:committed[0].focused,visible:committed[0].visibleToUser===true,enabled:committed[0].enabled,value:editorValue(committed[0]),showingHint:committed[0].showingHint,selectionStart:committed[0].selectionStart,selectionEnd:committed[0].selectionEnd},prefix+codepoints.slice(0,index+1).join(""));
    }
    const final = await ui.snapshot();
    if (
      !final.some(
        (n) =>
          n.packageName === "com.pateonavi.naviapp" && focusedApplication(n) &&
          n.className === "android.widget.EditText" &&
          editorValue(n) === prefix + text,
      )
    )
      throw new Error("exact committed input codepoints differ");
    const focused = final.filter((node) => node.packageName === "com.pateonavi.naviapp" && focusedApplication(node) && node.className === "android.widget.EditText" && node.focused && editorValue(node) === prefix + text);
    if (focused.length !== 1) unavailable("exact committed input has no unique fresh focused editor");
    const png = await screenshot(`input-${caseNumber}`);
    const currentBoot = await bootObservation();
    if (currentBoot.boot !== baseline.boot) unavailable("actual input capture crossed a boot");
    const artifacts = evidence.actions.slice(firstAction).flatMap((action: any) => action.artifacts.filter((artifact: any) => artifact.kind === "stdout" || artifact.kind === "png").map((artifact: any) => artifact.path));
    return { baseline, editor: focused[0], boot: currentBoot.boot, png, artifacts };
  };
  const inputFont = async (id: string, inputProof: Awaited<ReturnType<typeof input>>) => {
    let current = inputProof.baseline, parts: ReturnType<typeof fontCapture> | null = null;
    // Optional byte provenance gets one fresh observation; real input and PNG review are mandatory.
    for (let attempt = 0; attempt < 1; attempt++) {
      current = await observations();
      if (current.boot !== inputProof.boot) unavailable("font observation crossed the actual input boot");
      const rows = fresh(inputProof.baseline, current);
      if (!rows.some((row) => row.agent === "font-cyrillic" && row.event === "font.visual.view" && row.fields.text_length === inputProof.editor.text.length)) continue;
      try { parts = fontCapture(rows, inputProof.editor); break; }
      catch (error) {
        if (/missing|provenance unavailable/.test(String(error))) continue;
        throw error;
      }
    }
    const artifacts = [...inputProof.artifacts, ...(inputProof.png ? [inputProof.png] : []), inputProof.baseline.archive, current.archive, ...(copy ? [copy.path] : [])];
    if (!parts) {
      proof(id, "unavailable", "complete byte-qualified actual focused Paint capture unavailable after bounded read-only observations", artifacts);
      return;
    }
    try {
      const authority = fontAuthority(inputProof.baseline, current);
      actualFont(fresh(inputProof.baseline, current), inputProof.editor, authority);
      proof(id, "passed", "Actual focused Paint uses a byte-qualified corrected Typeface; fresh canonical capture matches current native Navi endpoint and focused Activity. Applied desired state and PNG appearance are separate claims", artifacts);
    } catch (error) {
      if (error instanceof Unavailable || /missing|unavailable/.test(String(error))) proof(id, "unavailable", String(error), artifacts);
      else throw error;
    }
  };
  const faultAuthority = (kind: FaultKind, baseline: Awaited<ReturnType<typeof observations>>): FaultRequest => {
    const status = inspectionResident(baseline.body), process = kind === "vehicle-state-reply-loss" ? "com.qinggan.systemservice" : SETTINGS;
    if (status.releaseIdentity !== manifest.releaseIdentity || status.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) || status.catalogSha256 !== digest(readFileSync(join(c.closure, "agents.json")))) throw new Error("fault current installed authority differs");
    const targetPid = oemTargetPid(baseline.body, process);
    const endpoint = baseline.logData.records.filter(record => record.message === "desired-snapshot-queued").map(queuedSnapshot).findLast(fields => fields.process === process && fields.targetPid === targetPid && fields.generationId === status.generationId && fields.catalogSha256 === status.catalogSha256);
    if (!endpoint?.targetStarttime) unavailable("fault window requires the actual registered current target lifetime");
    return { action: "fault-window", kind, bootId: baseline.boot, manifestSha256: status.manifestSha256, generationId: status.generationId, catalogSha256: status.catalogSha256, targetPid: Number(targetPid), targetStarttime: endpoint.targetStarttime };
  };
  const faultWindow = async <T>(kind: FaultKind, baseline: Awaited<ReturnType<typeof observations>>, observe: (armed: FaultReceipt) => Promise<T>) => {
    if (evidence.activeOperation || faultLive) throw new Error("fault window requires no active transaction or diagnostic lease");
    immutable(); await ownership();
    const before = await bootObservation();
    if (before.boot !== baseline.boot) unavailable("fault baseline is no longer on the current boot");
    const request = faultAuthority(kind, baseline), file = join(c.output, `fault-${caseNumber}-${evidence.actions.length + 1}.json`), first = evidence.actions.length, locale = language;
    writeFileSync(file, JSON.stringify(request) + "\n", { flag: "wx", mode: 0o600 });
    let result: Awaited<ReturnType<typeof withFaultWindow<T>>> | undefined, failure: unknown;
    try {
      result = await withFaultWindow(driverInvocation(join(c.package, "voboost-installer"), file, serial, { timeoutMs: 120000 }), async invocation => {
        const result = await record(invocation), action = evidence.actions.find((row: any) => row.id === result.actionId);
        action.context = { kit: frozen.package, runtime, serial, bootId: baseline.boot, locale };
        return result;
      }, request, serial, manifest.releaseIdentity, async (armed, live) => {
        faultLive = live;
        try { return await observe(armed); }
        finally { faultLive = null; }
      });
    } catch (error) { failure = error; }
    try {
      immutable(); await fullVerify();
      const after = await bootObservation();
      if (after.boot !== baseline.boot || !after.bootCompleted || after.body !== before.body) throw new Error("fault window changed its boot or durable installer operation");
    } catch (error) { failure = failure ? new AggregateError([failure, error], `fault observation failed: ${String(failure)}; boundary failed: ${String(error)}`) : error; }
    finally { persist(); }
    if (failure) throw failure instanceof Unavailable ? new Error(`armed fault observation incomplete; abort before further mutations: ${String(failure)}`, { cause: failure }) : failure;
    if (!result) throw new Error("fault window result missing");
    return { ...result, artifacts: [baseline.archive, file, ...evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path))] };
  };
  const waitSettingsText = async (caption: string, limit = 35000) => {
    const deadline = performance.now() + limit;
    while (performance.now() < deadline) {
      const nodes = await ui.snapshot();
      if (nodes.some(node => node.packageName === SETTINGS && focusedApplication(node) && node.text === caption)) return nodes;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    unavailable("actual source-bound runtime error text was not visible within its bounded window");
  };
  const rejectedStartup = async () => {
    if (!copy) unavailable("source-bound mutation failure copy unavailable");
    await selectStartup("interface"); await top();
    const baseline = await observations(), first = evidence.actions.length, selected = copy.languages[language].startup.find(row => row.value === "hidden")!.label;
    const result = await faultWindow("startup-mutation-lock", baseline, async () => {
      const nodes = await ui.snapshot(), hidden = uniqueNode(nodes, node => node.packageName === SETTINGS && focusedApplication(node) && node.checkable && node.text === selected);
      if (hidden.checked) throw new Error("rejected startup attempted value is already confirmed");
      await ui.tap(hidden);
      const rejected = await waitSettingsText(copy!.languages[language].errors.mutationFailure);
      const original = copy!.languages[language].startup.find(row => row.value === "interface")!.label;
      if (!rejected.some(node => node.packageName === SETTINGS && focusedApplication(node) && node.checkable && node.text === original && node.checked) || rejected.some(node => node.packageName === SETTINGS && focusedApplication(node) && node.checkable && node.text === selected && node.checked)) throw new Error("rejected startup did not retain its actual confirmed selection");
      await screenshot(`${language}-startup-rejection`);
    });
    const current = await observations(), effects = faultEffects(current.logData.records, result.armed, "settings", sequence(baseline.logData.records));
    if (!effects.length || !fresh(baseline, current).some(row => row.agent === "voboost-settings-mutations" && row.event === "mutation.ack" && row.outcome === "timeout" && row.fields.key === "radio/voboost-startup:hidden")) unavailable("actual root startup rejection and correlated terminal timeout were not both observed");
    if (inspectionResident(current.body).startupMode !== "interface") throw new Error("rejected startup changed canonical mode");
    await selectStartup("hidden"); await selectStartup("interface");
    return [copy.path, current.archive, ...result.artifacts, ...evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path))];
  };
  const retryableUninstall = async () => {
    if (!copy) unavailable("source-bound retryable uninstall copy unavailable");
    await panel(true);
    const captions = copy.languages[language].uninstall, baseline = await observations();
    const result = await faultWindow("uninstall-transport-loss", baseline, async () => {
      await tapText(captions.buttonText);
      await waitSettingsText(captions.confirmLabel, 10000);
      await tapText(captions.confirmLabel);
      await waitSettingsText(captions.retryableError);
      await screenshot(`${language}-uninstall-retryable-error`);
    });
    const current = await observations();
    if (faultEffects(current.logData.records, result.armed, "settings", sequence(baseline.logData.records)).length !== 1) throw new Error("actual one-shot authenticated pre-durable uninstall transport loss was not observed; abort before any further uninstall");
    const status = await cli(["--status"]); resultOk(status);
    const health = jsonReceipt(status.stdout);
    if (health.installedVersion !== manifest.releaseIdentity || health.deviceSerial !== serial || health.contentRelation !== "same" || health.health !== "healthy" || current.boot !== baseline.boot) throw new Error("retryable uninstall did not retain the exact healthy installed kit and boot");
    // The real retry must reopen its ordinary dialog; cancellation returns to idle.
    const first = evidence.actions.length;
    await tapText(captions.buttonText); await waitSettingsText(captions.cancelLabel, 10000);
    await screenshot(`${language}-uninstall-retry-dialog`); await tapText(captions.cancelLabel);
    await panel(true);
    const retained = await cli(["--status"]); resultOk(retained);
    const retainedHealth = jsonReceipt(retained.stdout);
    if (retainedHealth.installedVersion !== manifest.releaseIdentity || retainedHealth.deviceSerial !== serial || retainedHealth.contentRelation !== "same" || retainedHealth.health !== "healthy") throw new Error("cancel after real retry did not retain the exact healthy installed kit");
    return [copy.path, current.archive, ...result.artifacts, ...evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path))];
  };
  const stateOutage = async (cancel: boolean) => {
    const id = "vehicle-drive-pedestrian-warning";
    await toggle(id, false);
    const baseline = await observations();
    const result = await faultWindow("vehicle-state-reply-loss", baseline, async armed => {
      await toggle(id, true);
      const deadline = performance.now() + 35000;
      let retried: Awaited<ReturnType<typeof observations>> | null = null;
      while (performance.now() < deadline) {
        const current = await observations(), rows = fresh(baseline, current), effects = faultEffects(current.logData.records, armed, id, sequence(baseline.logData.records));
        const attempts = new Set(rows.filter(row => row.agent === id && row.event === "agent.start.state-read" && row.outcome === "unavailable-retry").map(row => row.fields.attempt));
        if (effects.length && attempts.size >= 2 && [...attempts].every(value => Number.isSafeInteger(value) && Number(value) > 0)) { retried = current; break; }
      }
      if (!retried) unavailable("actual UNAVAILABLE read and two bounded Java startup retries were not observed");
      if (!cancel) return [retried.archive];
      await toggle(id, false);
      const stopped = await observations();
      const terminal = fresh(retried, stopped).find(row => row.agent === id && row.event === "readiness.cancel" && row.outcome === "canceled");
      if (!terminal || inspectionResident(stopped.body).injections.some((row: any) => row.id === id)) unavailable("actual pending startup retry cancellation and removed desired target were not observed");
      const drained = await observations(), quiet = await observations();
      if (faultEffects(quiet.logData.records, armed, id, sequence(drained.logData.records)).length || fresh(drained, quiet).some(row => row.agent === id && row.event === "agent.start.state-read" && row.outcome === "unavailable-retry")) throw new Error("cancelled outage worker continued new state reads or retry attempts");
      return [retried.archive, stopped.archive, drained.archive, quiet.archive];
    });
    if (cancel) await toggle(id, true);
    const deadline = performance.now() + 35000;
    while (performance.now() < deadline) {
      const current = await observations(), status = inspectionResident(current.body);
      if (status.injections.some((row: any) => row.id === id && row.state === "active") && rootVehicleState(current.logData.records, "pw", vehicleAuthority(baseline, current, "pw"), sequence(baseline.logData.records)).status === "present") return [...result.artifacts, ...result.value, current.archive];
    }
    unavailable("released state outage did not regain actual active readiness and a fresh present root read");
  };
  const languageRadioProgram = async () => {
        const localeEndpoints: Array<{ baseline: Awaited<ReturnType<typeof observations>>; current: Awaited<ReturnType<typeof observations>>; language: Language }> = [];
        if (settingsPart !== "postboot") {
        await subcheck("i18n-system-picker-multi-owner", async () => {
          if (!c.i18nRuntimeEvidence || !nativeConfig) unavailable("NOT_RUN: official raw system-picker and Android/QNX runtime directories are not supplied");
          const current = await observations(), resident = inspectionResident(current.body);
          const observation = await cli(["--observe"]);
          if (observation.exitCode !== 0 || observation.timedOut || observation.signal || observation.overflow) unavailable("NOT_RUN: current official i18n observation unavailable");
          const official = JSON.parse(observation.stdout), cluster = official?.data?.files?.runtime?.data?.cluster;
          if ((await bootObservation()).boot !== current.boot || !cluster) unavailable("NOT_RUN: i18n observation crossed a boot or lacks native authority");
          const expected = { session: nativeConfig!.session, serial, androidBoot: current.boot, androidGeneration: resident.generationId, qnxBoot: cluster.bootId, qnxGeneration: cluster.generationId, catalogSha256: digest(readFileSync(join(c.closure,"agents.json"))) };
          try {
            admittedI18n = admitI18nSystemPicker(c.i18nRuntimeEvidence!, {
              expected,
              official,
              logIdentity: { serial, version: manifest.releaseIdentity, manifestSha256: digest(readFileSync(join(c.closure,"voboost.json"))) },
            });
            const latest = await cli(["--observe"]);
            if (latest.exitCode !== 0 || latest.timedOut || latest.signal || latest.overflow) unavailable("NOT_RUN: final official i18n observation unavailable");
            assertCurrentI18nObservation(JSON.parse(latest.stdout),official,expected);
            if ((await bootObservation()).boot !== current.boot) unavailable("NOT_RUN: i18n raw evidence validation crossed a boot");
          } catch (error) { admittedI18n=null; if (error instanceof I18nEvidenceUnavailable) unavailable("NOT_RUN: "+error.message); throw error; }
          return [...admittedI18n!.artifacts,current.archive];
        });
        await subcheck("language-loop-copy-dialog-cancel", async () => {
          for (const l of ["en", "zh", "ru", "zh", "en"] as Language[]) {
            const baseline = await observations();
            await setLanguage(l);
            await uninstallDialog();
            localeEndpoints.push({ baseline, current: await observations(), language: l });
          }
          return localeEndpoints.flatMap(({ baseline, current }) => [baseline.archive, current.archive]);
        });
        await subcheck("language-process-resource-locale", async () => {
          if (localeEndpoints.length !== 5) unavailable("full language loop lacks independently collected locale endpoints");
          for (const { baseline, current, language: selected } of localeEndpoints) {
            let latest: ReturnType<typeof renderLocale>;
            try { latest = renderLocale(fresh(baseline, current)); }
            catch (error) { if (/missing/.test(String(error))) unavailable(String(error)); throw error; }
            if (latest.fields.effectiveLanguage !== selected) throw new Error("fresh effective UI language differs from requested language");
            if (![latest.fields.defaultLocale,latest.fields.resourceLocale].every(value => new RegExp("^"+selected+"(?:[-_]|$)").test(String(value).toLowerCase()))) throw new Error("OEM system selection differs from fresh process/resource locale readback");
          }
          return localeEndpoints.flatMap(({ baseline, current }) => [baseline.archive, current.archive]);
        });
        // Voboost no longer owns a language radio or its animation trace.
        // Process rendering alone cannot prove system picker admission or QNX owners.
        // The separate raw-runtime subcheck above must pass before contributed RU input is admitted.
        }
        await subcheck("language-reboot-persistence", async () => {
          const expectedLanguage = await observedLanguage();
          const beforeBoot = await bootObservation();
          await reboot();
          const afterBoot = await bootObservation();
          if (!afterBoot.bootCompleted || afterBoot.boot === beforeBoot.boot) throw new Error("language persistence requires a new completed boot");
          await top();
          await panel(true);
          if (deferred && await observedLanguage() !== expectedLanguage) throw new Error("selected language did not persist across reboot");
          const current = await observations();
          const status = inspectionResident(current.body);
          if (!status || status.releaseIdentity !== manifest.releaseIdentity || status.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json")))) unavailable("current desired-agent snapshot identity unavailable");
          const active = STABLE.filter((id) => id !== "settings" && enabled.get(id));
          if (!active.length || active.some((id) => !status.injections.some((row: any) => row.id === id && row.state === "active"))) unavailable("enabled-agent active proof after language ACK/reboot incomplete");
          return [current.archive];
        });
  };
  const cleanupOwnedFixture = async (mode: "removal-and-grid" | "removal-only" = "removal-and-grid") => {
    if (evidence.fixtureCleanup.owned && evidence.fixtureCleanup.status !== "passed") {
          await subcheck("owned-fixture-cleanup", async () => {
            const before = await bootObservation(), first = evidence.actions.length;
            try {
              if (!fixtureOemItems?.length || !ownedFixtureCleanupAllowed(evidence.fixtureCleanup)) unavailable("fixture/OEM ownership baseline or completed case12 evidence missing");
              const current = await ui.call({ action: "fixture-state" });
              if (current.body === "fixture-present\n") {
                if (evidence.fixtureCleanup.removalAction !== null || evidence.fixtureCleanup.removalAttempted === true) unavailable("fixture removal already dispatched; refusing a second mutation");
                evidence.fixtureCleanup.removalAttempted = true;
                persist();
                const removed = await ui.call({ action: "remove-fixture", package: plan.fixture.package });
                if (removed.body.trim() !== "Success") throw new Error("owned fixture removal receipt differs");
                evidence.fixtureCleanup.removalAction = (ui.records.at(-1)!.result as ProcessResult).actionId;
                evidence.fixtureCleanup.removalBootId = before.boot;
              } else if (current.body !== "fixture-absent\n") unavailable("fixture state receipt differs");
              else if (!Number.isInteger(evidence.fixtureCleanup.removalAction)) unavailable("fixture absent without this cycle acknowledged removal");
              if (evidence.fixtureCleanup.removalBootId !== before.boot) unavailable("acknowledged fixture removal belongs to another boot");
              const absent = await ui.call({ action: "fixture-state" });
              if (absent.body !== "fixture-absent\n") throw new Error("owned fixture remains after removal");
              const after = await bootObservation();
              if (before.boot !== after.boot) unavailable("fixture cleanup observation crossed a boot");
              Object.assign(evidence.fixtureCleanup, { status: "passed", reason: "owned exact fixture removed; fresh same-boot absence verified", bootId: after.boot });
            } catch (error) {
              evidence.fixtureCleanup.status = error instanceof Unavailable ? "unavailable" : "failed";
              evidence.fixtureCleanup.reason = String(error);
              throw error;
            } finally {
              evidence.fixtureCleanup.artifacts = [...new Set([...evidence.fixtureCleanup.artifacts, ...evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path))])];
              persist();
            }
            return evidence.fixtureCleanup.artifacts;
          });
        }
    if (mode === "removal-and-grid") await subcheck("fixture-oem-grid-retained", async () => {
      const before = await bootObservation(), first = evidence.actions.length;
      if (evidence.fixtureCleanup.status !== "passed" || !fixtureOemItems?.length) unavailable("owned removal or OEM baseline unavailable");
              const retained = await launcherVisibleInventory();
              for (const label of new Set(fixtureOemItems)) if (retained.filter((item) => item === label).length < fixtureOemItems.filter((item) => item === label).length) throw new Error("observed OEM grid item lost after fixture cleanup");
              if (retained.some((label) => /Voboost Fixture/i.test(label))) throw new Error("removed fixture remains in fresh OEM grid");
              await screenshot("fixture-removed-oem-retained");
      const after = await bootObservation();
      if (before.boot !== after.boot || after.boot !== evidence.fixtureCleanup.bootId) unavailable("post-cleanup OEM integrity observation crossed a boot");
      return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path));
    });
  };
  const caseProgram = async (number: number) => {
    switch (number) {
      case 1: {
        if (evidence.preparation.status !== "passed" || !evidence.preparation.bootId)
          throw new Unavailable("complete initial preparation absence proof missing");
        if ((await bootObservation()).boot !== evidence.preparation.bootId) throw new Error("prepared absence boot changed before first install");
        await absentStatus();
        try {
          const first = evidence.actions.length;
          originalOemItems = (await launcherGrid()).map((item) => item.label).filter(Boolean);
          await screenshot("oem-grid-stock-baseline");
          originalOemCapture = evidence.actions.slice(first).flatMap((action: any) => action.artifacts.filter((artifact: any) => artifact.kind === "png" || artifact.kind === "stdout").map((artifact: any) => artifact.path));
        }
        catch (error) { prerequisite(18, "initial-oem-grid", String(error)); }
        if ((await bootObservation()).boot !== evidence.preparation.bootId) throw new Error("prepared absence boot changed before first install");
        await install();
        try {
          initialVehicle = await observations();
          const fev = await oemValue("fev"), pw = await oemValue("pw");
          const current = await observations(), rows = fresh(initialVehicle, current);
          if (vehicleRead(rows, "fev") !== fev || vehicleRead(rows, "pw") !== pw) unavailable("fresh initial OEM/CAN source readback unavailable");
          initialCan = { fev, pw, artifacts: [initialVehicle.archive, current.archive] };
        }
        catch (error) { prerequisite(24, "initial-vehicle-observations", String(error)); }
        break;
      }
      case 2: {
        const before = (await bootObservation()).boot;
        const result = await uninstall();
        const r = normalAbsence(result);
        const b = await bootCheck("normal-uninstall", before, r.bootId);
        if (b.status !== "passed" && !(fullSemantic && semanticBoot(b))) throw new Error(b.reason);
        normalRemoval = { result, boot: b, actionId: result.actionId!, artifact: evidence.actions.find((a: any) => a.id === result.actionId).artifacts.find((a: any) => a.kind === "stdout").path };
        break;
      }
      case 3: {
        if (!normalRemoval || (normalRemoval.boot.status !== "passed" && !(fullSemantic && semanticBoot(normalRemoval.boot)))) throw new Error("normal removal boot proof missing");
        const receipt = normalAbsence(normalRemoval.result);
        if (normalRemoval.boot.caseNumber !== 2 || normalRemoval.boot.after !== receipt.bootId || normalRemoval.boot.verifiedBoot !== receipt.bootId)
          throw new Error("exact normal removal boot proof differs");
        evidence.cases.find((c: Case) => c.number === 3).actions.push(normalRemoval.actionId);
        await absenceReadback(receipt.bootId, "normal-removal-absence");
        proof("same-normal-removal-receipt", "passed", "Exact case-2 normal receipt with fresh same-boot absence, prepared verification and UI capture", [normalRemoval.artifact, ...(normalRemoval.boot.screenshot ? [normalRemoval.boot.screenshot] : [])]);
        break;
      }
      case 4:
        await install();
        break;
      case 5:
        await open();
        await screenshot("settings-tab");
        if (copy) initialLanguage = await observedLanguage();
        break;
      case 6:
        initialLanguage ??= await observedLanguage();
        if (initialLanguage !== "en" && initialLanguage !== "zh")
          unavailable("OEM EN/ZH baseline requires an observed English or Chinese system language; Russian belongs to admitted i18n coverage");
        await setLanguage(initialLanguage === "en" ? "zh" : "en");
        await panel(true, false);
        proof("language-opposite-selected", "passed", "Actual opposite OEM EN/ZH system selection and source-bound panel readback completed", deferred ? [(await observations()).archive] : []);
        break;
      case 7:
        if (!initialLanguage) unavailable("initial confirmed language unavailable; no return language is assumed");
        await setLanguage(initialLanguage);
        await panel(true, false);
        proof("language-original-restored", "passed", "Actual original OEM system language restored with source-bound panel readback", deferred ? [(await observations()).archive] : []);
        break;
      case 8: {
        const baselineObservation = await inspect(), baseline = baselineObservation.body;
        const crashEpoch = runtime ? { sessionId: runtime.sessionId, pid: runtime.pid, bootId: baselineObservation.boot } : null;
        const readNativeCrash = async (remainingMs: number, tombstone?: string) => {
          if (!native) throw new Error("native crash source unavailable");
          const deadline = performance.now() + remainingMs;
          await ownership();
          const budget = Math.floor(deadline - performance.now());
          if (budget < 1) throw new Error("native crash collection deadline exhausted before source read");
          const value = await native.call("session.logs", { source: "native", timeout_ms: Math.min(10000, budget), ...(tombstone ? { tombstone } : {}) });
          if (performance.now() >= deadline) throw new Error("native crash collection deadline exhausted before ownership readback");
          await ownership();
          return value;
        };
        const nativeBaseline = native && crashEpoch ? await nativeCrashBaseline(readNativeCrash, crashEpoch) : { error: "native owned session unavailable" };
        let crashDiagnosticSaved = false;
        try {
        const policy = await disablePolicy();
        admittedStopModes = policy.modes;
        proof("owning-source-stop-policy", "passed", "Exact six signed checkbox entry classes and owning Java default/overrides are bound to installed source receipt", [policy.file]);
        for (const agent of catalog.filter((row: any) => row.activation?.type === "checkbox")) {
          await checkReenable(agent.id, "re-enable-before");
          await checkDisable(agent.id, policy.modes[agent.id], () => toggle(agent.id, false));
          await checkReenable(agent.id, "re-enable-after");
        }
        for (const id of ["keyboard-ru", "keyboard-en"]) {
          const other = id === "keyboard-ru" ? "keyboard-en" : "keyboard-ru";
          await checkReenable(other, "re-enable-conflict-prepare");
          await checkDisable(other, policy.modes[other], () => toggle(id, true), "keyboard-conflict");
          const currentKeyboard = await toggleObservation(checkboxTargets(id));
          for (const result of reenabledTargets(id, checkboxTargets(id), currentKeyboard)) proof(`keyboard-conflict-active-${id}-${result.process}`, result.status, result.reason, [currentKeyboard.file]);
          const title = catalog.find((a: any) => a.id === other).title[language];
          const nodes = await ui.snapshot();
          const label = uniqueNode(nodes, (n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === title);
          if (uniqueNode(nodes, (n) => n.parent === label.parent && n.checkable).checked)
            throw new Error("keyboard agents coexist");
        }
        const current = await observations();
        const statusResult = await cli(["--status"]);
        resultOk(statusResult);
        const health = jsonReceipt(statusResult.stdout);
        if (health.health !== "healthy" || health.deviceSerial !== serial)
          throw new Error("agent cycle status not healthy for exact serial");
        const beforeCrashes = new Set(
          (baseline.split("android-crash-tail:")[1]?.split("profile-inputs:")[0] ?? "").split("\n"),
        );
        const newCrashes = (
          current.body.split("android-crash-tail:")[1]?.split("profile-inputs:")[0] ?? ""
        )
          .split("\n")
          .filter(
            (l) =>
              !beforeCrashes.has(l) &&
              /FATAL EXCEPTION|Fatal signal|Caused by:.*(IllegalStateException|NullPointerException)/.test(
                l,
              ),
          );
        if (newCrashes.length) {
          const failure = new Error("new crash during agent cycle: " + newCrashes.join(" "));
          if (native && crashEpoch && newCrashes.some(line => /Fatal signal/.test(line))) {
            await preserveCrashFailure(failure, async () => {
              const diagnostic = await collectNativeCrash(readNativeCrash, crashEpoch, nativeBaseline, newCrashes);
              writeFileSync(join(c.output, "case-8-native-crash.json"), JSON.stringify(diagnostic, null, 2) + "\n", { flag: "wx", mode: 0o600 });
              crashDiagnosticSaved = true;
            });
          }
          throw failure;
        }
        proof("agent-lifecycle-and-keyboard-exclusion", "passed", "All signed checkbox disable/re-enable target checks, mutual exclusion both directions and crash/status checks completed", [policy.file, current.archive]);
        } catch (failure) {
          // An early UI/transport failure must not discard a coincident native crash.
          // Preserve the exact original error; diagnostics never turn the case green.
          if (native && crashEpoch && !crashDiagnosticSaved) await preserveCrashFailure(failure, async () => {
            const diagnostic = await collectNativeCrash(readNativeCrash, crashEpoch, nativeBaseline, []);
            writeFileSync(join(c.output, "case-8-native-crash.json"), JSON.stringify(diagnostic, null, 2) + "\n", { flag: "wx", mode: 0o600 });
          });
          throw failure;
        }
        break;
      }
      case 9:
        await toggle("font-cyrillic", true);
        await toggle("keyboard-ru", true);
        const cyrillicInput = await input("Москва");
        await inputFont("actual-cyrillic-font-provenance", cyrillicInput);
        proof("actual-cyrillic-input", "passed", "Real focused Navi editor contains exact committed Cyrillic codepoints; appearance remains separate", cyrillicInput.artifacts);
        for (const check of visualChecks(9)) proof(check.id, "unavailable", "Actual source-bound PNG awaits authenticated reviewer observations");
        break;
      case 10:
        await toggle("keyboard-en", true);
        { const actual = await input("moscow"); proof("actual-english-input", "passed", "Actual EN keys committed exact focused Navi codepoints", actual.artifacts); }
        break;
      case 11:
        await toggle("keyboard-ru", true);
        await input("Москва");
        await switchKeyboard("en");
        { const actual = await input("moscow", true, "Москва"); proof("actual-mixed-layout-input", "passed", "Actual RU/EN keys retained exact focused Navi prefix and committed Latin suffix", actual.artifacts); }
        break;
      case 12: {
        if (c.fixture === null)
          unavailable("approved Voboost Fixture APK not supplied; no fabricated fixture");
        const fixture = await ui.call({ action: "fixture-state" });
        evidence.fixtureCleanup.originalAbsenceAction = (ui.records.at(-1)!.result as ProcessResult).actionId;
        if (fixture.body !== "fixture-absent\n") {
          evidence.fixtureCleanup.status = "unavailable";
          evidence.fixtureCleanup.reason = "fixture preexists; this cycle has no removal authority; prior-cycle obligation requires its original ownership evidence";
          unavailable("fixture already present; preserve initial state rather than overwrite");
        }
        const stockPolicy = await disablePolicy();
        admittedStopModes = stockPolicy.modes;
        const stockTitle = catalog.find((agent: any) => agent.id === "launcher-apps")?.title?.[language];
        if (!stockTitle) unavailable("signed launcher stock preparation title missing");
        await top();
        const stockBeforeNodes = await seekAgentControl(stockTitle);
        const stockBeforeCheck = agentControl(stockBeforeNodes, SETTINGS, stockTitle);
        if (!stockBeforeCheck) unavailable("initial launcher checkbox unavailable");
        const launcherPreviouslyEnabled = stockBeforeCheck.checked;
        proof("launcher-stock-prior-state", "passed", `Observed launcher checkbox before stock preparation: ${launcherPreviouslyEnabled}`);
        if (launcherPreviouslyEnabled) await checkDisable("launcher-apps", stockPolicy.modes["launcher-apps"], () => toggle("launcher-apps", false), "stock-disable", true);
        await launcherStockDisabled();
        const stockUnhooked = await toggleObservation(checkboxTargets("launcher-apps"));
        if (stockUnhooked.injections.some(row => row.id === "launcher-apps")) unavailable("launcher injection remains during stock preparation");
        proof("launcher-stock-unhooked", "passed", "Unchecked launcher and independently observed omitted injection after source-admitted stop", [stockUnhooked.file, stockPolicy.file]);
        fixtureOemItems = (await launcherGrid(true)).map((item) => item.label).filter(Boolean);
        const cacheBaseline = await observations();
        if (inspectionResident(cacheBaseline.body).injections.some((row: any) => row.id === "launcher-apps")) unavailable("launcher runtime injection remains selected during stock baseline");
        await toggle("launcher-apps", true);
        const installCacheBaseline = await observations();
        evidence.fixtureCleanup.status = "unavailable";
        evidence.fixtureCleanup.reason = "fixture installation attempted; actual successful receipt and ownership are pending";
        persist();
        let installed: Record<string, any>;
        const installStartedAtHostMs = Date.now();
        let installFinishedAtHostMs = installStartedAtHostMs;
        try { installed = await ui.call({ action: "install-fixture", apk: c.fixture, package: plan.fixture.package }); }
        finally {
          installFinishedAtHostMs = Date.now();
          const last = ui.records.at(-1);
          if (last && JSON.parse(readFileSync(last.invocation.args[1], "utf8")).action === "install-fixture") evidence.fixtureCleanup.installAction = (last.result as ProcessResult).actionId;
          persist();
        }
        if (!fixtureOwned(fixture.body, installed.body, plan.fixture.package, plan.fixture.package)) throw new Error("fixture ownership receipt differs");
        evidence.fixtureCleanup.owned = true;
        evidence.fixtureCleanup.reason = "owned fixture installed; cleanup obligation remains until case12 and case18 observations finish";
        persist();
        // Retain the first publication window before routes, drag or secondary-page work.
        const firstPublicationCapture=await launcherObservations();
        await subcheck("launcher-entry-routes-after-install", async () => {
          const first = evidence.actions.length;
          const dockItems = await launcherGrid(true);
          const swipeItems = await launcherGrid(true, "home-swipe");
          observedLauncherOrder(dockItems, swipeItems);
          await screenshot("launcher-home-swipe-after-install");
          return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path));
        });
        let children = await launcherGrid(true);
        let page = 0;
        const seenPages = new Set<string>();
        while (!children.some(item => /Voboost Fixture/i.test(item.label))) {
          const key = JSON.stringify(children.map(item => item.label));
          if (seenPages.has(key) || page >= 11) unavailable("fixture absent from bounded unique launcher pages");
          seenPages.add(key);
          const grid = launcherCurrentPage.grid;
          const bounds = grid.windowRef!.bounds;
          await ui.call({ action: "gesture", x1: Math.floor(bounds[0] + (bounds[2] - bounds[0]) * 0.85), y1: Math.floor((bounds[1] + bounds[3]) / 2), x2: Math.floor(bounds[0] + (bounds[2] - bounds[0]) * 0.15), y2: Math.floor((bounds[1] + bounds[3]) / 2), milliseconds: 400, ...(native?ui.launcherPointerAuthority(grid):ui.gestureAuthority(grid)) });
          children = (await launcherPage()).items;
          page++;
        }
        const lastPageKey = JSON.stringify(children.map(item => item.label));
        const pageGrid = launcherCurrentPage.grid;
        const pageBounds = pageGrid.windowRef!.bounds;
        await ui.call({ action: "gesture", x1: Math.floor(pageBounds[0] + (pageBounds[2] - pageBounds[0]) * 0.85), y1: Math.floor((pageBounds[1] + pageBounds[3]) / 2), x2: Math.floor(pageBounds[0] + (pageBounds[2] - pageBounds[0]) * 0.15), y2: Math.floor((pageBounds[1] + pageBounds[3]) / 2), milliseconds: 400, ...(native?ui.launcherPointerAuthority(pageGrid):ui.gestureAuthority(pageGrid)) });
        children = (await launcherPage()).items;
        if (JSON.stringify(children.map(item => item.label)) !== lastPageKey) throw new Error("fixture is not on the final launcher page");
        const fm = children.filter((n) => /Voboost Fixture/i.test(n.label));
        if (fm.length !== 1 || !children.length)
          unavailable("fixture icon and stock grid item identity not exposed");
        if (children.at(-1) !== fm[0]) throw new Error("fixture is not last icon");
        if (page > 2) unavailable("fixture drag exceeds bounded three-page launcher route");
        const firstPublishedObservation=await seekSecondaryCache(installCacheBaseline,firstPublicationCapture);
        if (installCacheBaseline.boot !== firstPublishedObservation.launcherLifetime.boot) unavailable("first published installation crossed boot");
        try { firstPublishedFixture = launcherFirstPublished(firstPublishedObservation.logData.diagnostics, cacheAuthority(firstPublishedObservation, installCacheBaseline), plan.fixture.package); }
        catch (error) { if (error instanceof CacheEvidenceUnavailable) unavailable(error.message); throw error; }
        if (!evidence.fixtureCleanup.owned || !fixtureOwned(fixture.body, installed.body, plan.fixture.package, plan.fixture.package)) unavailable("first published fixture ownership missing");
        firstPublishedFixtureArtifact = join(c.output, "launcher-first-published-fixture.json");
        writeFileSync(firstPublishedFixtureArtifact, JSON.stringify({ ...firstPublishedFixture, fixtureSource:plan.fixture.source, hostInstallOperation:{startedAtUtc:new Date(installStartedAtHostMs).toISOString(),finishedAtUtc:new Date(installFinishedAtHostMs).toISOString(),durationMs:installFinishedAtHostMs-installStartedAtHostMs,clock:"host Date.now; operation interval, not Android presentation"}, originalAbsenceAction:evidence.fixtureCleanup.originalAbsenceAction, installAction:evidence.fixtureCleanup.installAction, archives:[installCacheBaseline.archive,...firstPublishedObservation.canonicalArchives] }, null, 2)+"\n", {flag:"wx",mode:0o600});
        // Secondary observations consume primary UI authority. Reacquire the real fixture page.
        children=await launcherGrid(true);
        for(let currentPage=0;currentPage<page;currentPage++) {
          const grid=launcherCurrentPage.grid,[l,t,r,b]=grid.windowRef!.bounds;
          await ui.call({action:"gesture",x1:Math.floor(l+(r-l)*0.85),y1:Math.floor((t+b)/2),x2:Math.floor(l+(r-l)*0.15),y2:Math.floor((t+b)/2),milliseconds:400,...(native?ui.launcherPointerAuthority(grid):ui.gestureAuthority(grid))});
          children=(await launcherPage()).items;
        }
        const freshFm=children.filter(item=>/Voboost Fixture/i.test(item.label));
        if(freshFm.length!==1 || children.at(-1)!==freshFm[0]) unavailable("fresh primary fixture last-slot authority unavailable after secondary navigation");
        const points = launcherPageDrag(page, freshFm[0].node.bounds, children[0].node.bounds, launcherCurrentPage.grid.bounds);
        await ui.call({ action: "drag", points, ...(native?ui.launcherPointerAuthority(freshFm[0].node):ui.gestureAuthority(freshFm[0].node)) });
        const movedItems = await launcherBeginning();
        if (!/Voboost Fixture/i.test(movedItems[0]?.label ?? ""))
          throw new Error("fixture first-slot drag readback differs");
        const observedBeforeReboot = movedItems.map(item => ({ label: item.label }));
        const beforeCache = await seekSecondaryCache(installCacheBaseline,firstPublishedObservation);
        let beforeCachePairs: ReturnType<typeof launcherCache>;
        try { beforeCachePairs = launcherCache(beforeCache.logData.diagnostics, cacheAuthority(beforeCache, installCacheBaseline), plan.fixture.package); }
        catch (error) { if (error instanceof CacheEvidenceUnavailable) unavailable(error.message); throw error; }
        await subcheck("launcher-visible-page-order-after-reboot", async () => {
          const first = evidence.actions.length;
          await reboot();
          const retainedItems = await launcherGrid(true);
          observedLauncherOrder(observedBeforeReboot, retainedItems);
          const swipeItems = await launcherGrid(true, "home-swipe");
          observedLauncherOrder(observedBeforeReboot, swipeItems);
          await screenshot("fixture-first-after-reboot");
          return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.filter((artifact: any) => artifact.kind === "png" || artifact.kind === "stdout").map((artifact: any) => artifact.path));
        });
        await subcheck(deferred ? "launcher-semantic-surfaces-lifetime" : "launcher-icons-paired-surfaces", async () => {
          const first = evidence.actions.length, process = "com.qinggan.app.launcher";
          const lifetime = (observed: Awaited<ReturnType<typeof launcherObservations>>) => {
            const { boot, process, targetPid, targetStarttime } = observed.launcherLifetime;
            return { boot, process, targetPid, targetStarttime };
          };
          const selectedBefore=await readLauncherTheme();
          const before = lifetime(await launcherObservations());
          await launcherGrid();
          await ui.snapshot();
          const home = await screenshot("launcher-icons-home");
          await ui.call({ action: "open-secondary-launcher" });
          const grid = await screenshot("launcher-icons-grid", "secondary");
          const selectedAfter=await readLauncherTheme();
          const after = lifetime(await launcherObservations());
          if(selectedBefore.boot!==before.boot || selectedAfter.boot!==before.boot || JSON.stringify(selectedBefore.theme)!==JSON.stringify(selectedAfter.theme)) throw new Error("launcher selected theme changed across paired captures");
          if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error("launcher visual pair crossed boot or process lifetime");
          const binding = join(c.output, `${String(evidence.actions.length).padStart(5, "0")}-launcher-icons-lifetime.json`);
          writeFileSync(binding, JSON.stringify({ ...before, ...(deferred ? { imageCapture: "deferred", visualAcceptance: false } : { pngs: [home, grid] }),themeSelection:{before:selectedBefore,after:selectedAfter} }) + "\n", { flag: "wx", mode: 0o600 });
          return [binding, ...evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path))];
        });
        for (const check of visualChecks(12)) proof(check.id, "unavailable", "Actual same-lifetime primary/secondary PNGs require authenticated assessment against actual Cloud Space/HiCar native frame and full-color references for the selected theme");
        await subcheck("launcher-two-screen-disk-cache-after-reboot", async () => {
          const beforeWitness={rows:beforeCache.logData.diagnostics,authority:cacheAuthority(beforeCache,installCacheBaseline)};
          const current = await seekSecondaryCache(beforeCache,undefined,beforeCachePairs,beforeWitness);
          try { launcherCache(current.logData.diagnostics, { ...cacheAuthority(current, beforeCache), beforeWitness }, plan.fixture.package, beforeCachePairs); }
          catch (error) { if (error instanceof CacheEvidenceUnavailable) unavailable(error.message); throw error; }
          if (!firstPublishedFixture || !firstPublishedFixtureArtifact) unavailable("first published fixture proof missing");
          return [firstPublishedFixtureArtifact, installCacheBaseline.archive,...beforeCache.canonicalArchives,...current.canonicalArchives];
        });
        await subcheck("launcher-theme-cache-roundtrip", async () => {
          const original = await readLauncherTheme();
          const originalLabel = original.theme.label as "Classic" | "Speedy Dash";
          const opposite = originalLabel === "Classic" ? "Speedy Dash" : "Classic";
          const phases: { label: string; style: string; pairs: ReturnType<typeof launcherCache>; diskSources: string[] }[] = [];
          const artifacts: string[] = [original.artifact.path];
          const sharedColorProofs: ReturnType<typeof launcherSharedColorCache>[] = [];
          let primaryFailure: unknown = null;
          const phase = async (label: "Classic" | "Speedy Dash") => {
            const baseline = await launcherObservations();
            const previousGeneration = Math.max(0,...baseline.logData.diagnostics.filter(row=>row.agent==="launcher-apps" && row.fields.package_name===plan.fixture.package && row.fields.boot_id===baseline.launcherLifetime.boot && row.fields.target_pid===Number(baseline.launcherLifetime.targetPid) && row.fields.process_start_ticks===baseline.launcherLifetime.targetStarttime && row.fields.code_generation===baseline.launcherLifetime.generationId && ["icon.ready","icon.draw","icon.prepared-draw"].includes(row.event)).map(row=>Number(row.fields.generation)));
            const selection = await selectLauncherTheme(label);
            await launcherGrid();
            await ui.call({ action: "open-secondary-launcher" });
            const current = await seekSecondaryCache(baseline,undefined,undefined,undefined,previousGeneration);
            const phaseAuthority={...cacheAuthority(current,baseline),publicationGeneration:launcherThemePublication(current.logData.diagnostics,cacheAuthority(current,baseline),plan.fixture.package,previousGeneration)};
            const selectedAfter = await readLauncherTheme();
            if (selection.boot !== current.launcherLifetime.boot || selectedAfter.boot !== selection.boot || JSON.stringify(selectedAfter.theme) !== JSON.stringify(selection.theme)) throw new Error("theme cache observation crossed boot or selected theme");
            let pairs: ReturnType<typeof launcherCache>;
            try { pairs = launcherCache(current.logData.diagnostics, phaseAuthority, plan.fixture.package); }
            catch (error) { if (error instanceof CacheEvidenceUnavailable) unavailable(error.message); throw error; }
            if (selection.theme.label === "Speedy Dash") sharedColorProofs.push(launcherSharedColorCache({rows: current.logData.diagnostics, authority: phaseAuthority}, plan.fixture.package));
            artifacts.push(selection.artifact.path, selectedAfter.artifact.path, baseline.archive, ...current.canonicalArchives);
            const diskSources = pairs.map(pair => {
              const draw = current.logData.diagnostics.filter(row => row.agent === "launcher-apps" && ["icon.draw","icon.prepared-draw"].includes(row.event) && row.fields.package_name === plan.fixture.package && row.fields.screen === pair.screen && row.record.sequence > sequence(baseline.logData.records) && row.fields.cache_key === pair.cache_key && row.fields.generation === phaseAuthority.publicationGeneration && row.fields.boot_id === current.launcherLifetime.boot && row.fields.target_pid === Number(current.launcherLifetime.targetPid) && row.fields.process_start_ticks === current.launcherLifetime.targetStarttime && row.fields.code_generation === current.launcherLifetime.generationId).toSorted((a,b)=>a.record.sequence-b.record.sequence)[0];
              if (!draw) throw new CacheEvidenceUnavailable("validated theme first-draw source missing");
              return String(draw.fields.cache_source);
            });
            phases.push({ label: selection.theme.label, style: selection.theme.style, pairs, diskSources });
          };
          try {
            // Force a real transition before each measured phase; a no-op card tap cannot supply fresh first-draw telemetry.
            await selectLauncherTheme(opposite);
            await phase(originalLabel);
            await phase(opposite);
            await phase(originalLabel);
            launcherThemeRoundTrip(phases);
            if (!sharedColorProofs.length) throw new CacheEvidenceUnavailable("shared color Speedy Dash observation missing");
          } catch (error) { primaryFailure = error; throw error; }
          finally {
            if (launcherThemeRestoreAllowed({ activeOperation: evidence.activeOperation, installerActive, faultLive: faultLive !== null, monitorHealthy: !monitorResult, leaseStatus: evidence.leaseStatus })) {
              try { await selectLauncherTheme(originalLabel); }
              catch (error) { throw new AggregateError(primaryFailure === null ? [error] : [primaryFailure,error], "native theme restore failed"); }
            } else { proof("launcher-theme-restore-pending", "unavailable", "Native theme restoration requires resolved operation and healthy monitor"); persist(); }
          }
          const path = join(c.output, "launcher-theme-cache-roundtrip.json");
          writeFileSync(path, JSON.stringify({ visualAcceptance: false, phases, sharedColorProofs }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
          return [path, ...artifacts];
        });
        break;
      }
      case 13:
        if (vehiclePart !== "fev-drawer") await vehicleProgram("fev");
        await drawerProgram();
        if (deferred) proof("vehicle-image-pending", "unavailable", "Image capture deferred for this partial run; vehicle visual acceptance remains pending");
        break;
      case 14:
        await vehicleProgram("pw");
        if (deferred) proof("vehicle-image-pending", "unavailable", "Image capture deferred for this partial run; vehicle visual acceptance remains pending");
        break;
      case 15:
        await startupReboot("hidden", selectStartup, async (mode) => { await panel(true); await screenshot(`${mode}-selected-description`); }, reboot);
        await ui.call({ action: "open-settings" });
        if ((await ui.snapshot()).some((n) => n.packageName === SETTINGS && focusedApplication(n) && n.text === "Voboost"))
          throw new Error("hidden tab remains visible");
        const whileHidden = await observations();
        const hiddenStatus = inspectionResident(whileHidden.body);
        const missingHidden = STABLE.filter(
          (id) =>
            id !== "settings" &&
            enabled.get(id) === true &&
            (!hiddenStatus ||
              !hiddenStatus.injections.some((i: any) => i.id === id && i.state === "active")),
        );
        const hiddenBound =
          hiddenStatus?.startupMode === "hidden" &&
          hiddenStatus?.releaseIdentity === manifest.releaseIdentity &&
          hiddenStatus?.manifestSha256 === digest(readFileSync(join(c.closure, "voboost.json")));
        await systemMenu();
        {
          const row = await systemTboxRecoveryTarget(ui);
          const [x, y] = center(row);
          await ui.call({ action: "tbox-burst", x, y });
        }
        await open();
        const hiddenObservation = await observations();
        const status = inspectionResident(hiddenObservation.body);
        if (!status || status.startupMode !== "interface")
          throw new Error("TBOX recovery did not confirm interface");
        if (
          !hiddenBound ||
          missingHidden.length ||
          STABLE.some((id) => id !== "settings" && !enabled.has(id))
        )
          unavailable(
            `hidden mode actual enabled-agent proof incomplete: ${missingHidden.join(",")}; waiting/unknown is not active proof`,
          );
        startup = "interface";
        proof("hidden-enabled-agents-tbox-recovery", "passed", "Exact installed manifest, all enabled agents active while hidden, no tab and real TBOX recovery to interface", [whileHidden.archive, hiddenObservation.archive]);
        break;
      case 16:
        await startupReboot("none", selectStartup, async (mode) => { await panel(true); await screenshot(`${mode}-selected-description`); }, reboot);
        {
          const b = await inspect();
          noneProcesses(b.body);
          proof("none-process-inventory", "passed", "Fresh independent full process inventory has no Voboost daemon or APK process", evidence.actions.at(-1).artifacts.map((artifact:any)=>artifact.path));
        }
        await returnInterface();
        proof("none-process-absence-computer-recovery", "passed", "None selected through Settings, guest reboot verified, daemon/APK process inventory absent and packaged CLI restored interface", [(await observations()).archive]);
        break;
      case 17: {
        if (evidence.fixtureCleanup.owned && evidence.fixtureCleanup.status !== "passed") unavailable("owned fixture cleanup obligation remains before final Settings uninstall");
        if (!copy) throw new Unavailable("source-bound final uninstall captions unavailable");
        await open();
        await panel();
        let armed = false,
          buffer = "";
        let observerError: unknown = null,
          observerFinished = false;
        const observer = ui
          .callObserved({ action: "observe-settings-uninstall" }, (stream, chunk) => {
            if (stream === "stderr") {
              buffer = (buffer + chunk).slice(-MAX_CAPTURE);
              if (observerArmed(buffer)) armed = true;
            }
          })
          .then(
            (r) => {
              observerFinished = true;
              return r;
            },
            (e) => {
              observerError = e;
              observerFinished = true;
              return null;
            },
          );
        observerCompletion = observer;
        for (let n = 0; n < 100 && !armed && !observerFinished; n++)
          await new Promise((r) => setTimeout(r, 100));
        if (!armed || observerFinished) {
          await observer;
          throw new Error(`Settings observer not actively armed: ${String(observerError)}`);
        }
        await tapText(copy.languages[language].uninstall.buttonText);
        await tapText(copy.languages[language].uninstall.confirmLabel);
        const observed = await observer;
        if (observerError || !observed)
          throw new Error(`Settings observer failed: ${String(observerError)}`);
        normalAbsence({ exitCode: 0, stdout: JSON.stringify(observed), stderr: "" });
        const b = await bootCheck(
          "settings-uninstall",
          observed.bootIdBefore as string,
          observed.absence?.bootId,
        );
        if (b.status !== "passed" && !(deferred && b.semanticStatus === "passed" && b.status === "unavailable" && b.screenshot === null)) throw new Error(b.reason);
        proof("settings-uninstall-normal-absence-reboot", "passed", "Armed official observer received Settings button uninstall, complete nine-check normal absence and independently verified reboot", evidence.actions.filter((action:any)=>action.caseNumber===17).flatMap((action:any)=>action.artifacts.map((artifact:any)=>artifact.path)));
        break;
      }
      case 18: {
        let stockBaseline: Awaited<ReturnType<typeof observations>> | null = null;
        proof("oem-stock-baseline", originalOemCapture.length ? "passed" : "unavailable", "Actual pre-install stock grid PNG and hierarchy are retained independently of token/icon provenance", originalOemCapture);
        await subcheck("fresh-package-classification", async () => {
          if (!firstPublishedFixture || !firstPublishedFixtureArtifact || firstPublishedFixture.packageName !== plan.fixture.package || !evidence.fixtureCleanup.owned) unavailable("original first-published installed-fixture proof missing; later catalog scan cannot replace it");
          const baseline = await observations();
          stockBaseline = baseline;
          await toggle("launcher-apps", false);
          await toggle("launcher-apps", true);
          let current = baseline;
          for (let attempt = 0; attempt < 8; attempt++) {
            current = await observations();
            try { catalogScan(fresh(baseline, current)); return [firstPublishedFixtureArtifact, baseline.archive, current.archive]; }
            catch (error) {
              if (/system package admitted|classification identity/.test(String(error))) throw error;
              await new Promise((resolve) => setTimeout(resolve, 1000));
            }
          }
          unavailable("fresh full scan with UID/flags, exclusions and positive Voboost Fixture not observed");
        });
        await subcheck("oem-grid-retained", async () => {
          const first = evidence.actions.length;
          if (!originalOemItems?.length) unavailable("initial OEM grid baseline unavailable");
          const current = await launcherVisibleInventory();
          const missing = originalOemItems.filter(label => !current.includes(label));
          if (missing.length) throw new Error("observed original OEM grid items disappeared: " + JSON.stringify(missing));
          await screenshot("oem-grid-classification");
          return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.filter((artifact: any) => artifact.kind === "png" || artifact.kind === "stdout").map((artifact: any) => artifact.path));
        });
        await subcheck("oem-icon-bind-provenance", async () => {
          if (regression === "launcher") unavailable("Pre-install stock baseline unavailable in installed-kit regression");
          if (!stockBaseline) unavailable("current stock bind baseline missing");
          const current = await launcherObservations();
          try { stockProvenance(fresh(stockBaseline, current), { ...current.launcherLifetime, baselineBoot: stockBaseline.boot, afterSequence: sequence(stockBaseline.logData.records), records: current.logData.records }); }
          catch (error) { if (/missing|partial|invalidated/.test(String(error))) unavailable(String(error)); throw error; }
          return [stockBaseline.archive, current.archive];
        });
        for (const check of visualChecks(18)) proof(check.id, "unavailable", "Actual baseline/current stock grid PNGs await authenticated reviewer observations");
        await cleanupOwnedFixture();
        break;
      }
      case 19:
        await toggle("font-cyrillic", true);
        await toggle("keyboard-ru", true);
        await inputFont("moscow-font-provenance", await input("Москва"));
        await inputFont("yo-font-provenance", await input("Ёё"));
        proof("moscow-and-yo-codepoints", "passed", "Real RU keyboard input and exact editor codepoints observed independently of PNG spacing", [(await observations()).archive]);
        await inputFont("mixed-prefix-font-provenance", await input("Москва"));
        await switchKeyboard("en");
        await inputFont("mixed-latin-font-provenance", await input("moscow", true, "Москва"));
        await switchKeyboard("ru");
        await inputFont("mixed-yo-font-provenance", await input("ё", true, "Москваmoscow"));
        proof("mixed-ru-en-codepoints", "passed", "Continuous real-key mixed-layout input preserved the focused exact prefix and committed exact codepoints without inserted spaces", [(await observations()).archive]);
        for (const check of visualChecks(19)) proof(check.id, "unavailable", "Actual source-bound Navi PNGs await authenticated typography and spacing observations");
        break;
      case 20:
        await open();
        for (let repeat = 0; repeat < 3; repeat++) {
          const nodes = await ui.snapshot();
          await settingsForeground(nodes, "selected Voboost tab repeat");
          const tab = settingsTab(nodes);
          if (!tab.checked) throw new Error("repeated Voboost tab was not selected");
          await screenshot(`selected-tab-before-${repeat}`);
          // Screenshot invalidates node tokens; obtain a fresh target for the actual gesture.
          await ui.tap(settingsTab(await ui.snapshot()));
          const after = await ui.snapshot();
          await settingsForeground(after, "selected Voboost tab repeat result");
          if (!settingsTab(after).checked) throw new Error("repeated Voboost tab lost selection");
          await screenshot(`selected-tab-after-${repeat}`);
        }
        for (const l of ["en", "ru", "zh"] as Language[]) {
          let captures: string[] = [];
          await subcheck(`source-layout-measurements-${l}`, async () => {
            if (!copy) unavailable("source-bound component measurements unavailable");
            const baseline = await observations();
            await setLanguage(l); captures = await captureWindow(() => panel(true));
            const current = await observations();
            try { layoutCapture(fresh(baseline, current), l, copy.geometry, layoutAuthority(baseline, current), copy.languages[l].layoutTopology); }
            catch (error) { if (/missing/.test(String(error))) unavailable(String(error)); throw error; }
            return [baseline.archive, current.archive, copy.path];
          });
          await subcheck(`panel-captures-${l}`, async () => {
            if (!captures.length) unavailable(copy ? "current locale panel PNG and hierarchy were not independently collected" : `source-bound panel capture prerequisite unavailable: ${copyFailure}`);
            return captures;
          });
          proof(`visual-control-layout-${l}`, "unavailable", "Actual source-bound locale panel PNGs await authenticated typography, spacing and clipping observations");
        }
        await subcheck("three-locale-panel-captures", async () => {
          const captures = evidence.cases.find((row: Case) => row.number === 20).subchecks.filter((check: Subcheck) => /^panel-captures-(en|ru|zh)$/.test(check.id));
          if (captures.length !== 3 || captures.some((check: Subcheck) => check.status !== "passed" || !check.artifacts.length)) unavailable("one or more source-bound locale capture prerequisites unavailable");
          return captures.flatMap((check: Subcheck) => check.artifacts);
        });
        break;
      case 21:
        for (const l of ["en", "ru", "zh"] as Language[]) {
          let captures: string[] = [];
          await subcheck(`uninstall-action-measurements-cancel-${l}`, async () => {
            if (!copy) unavailable("source-bound action measurements unavailable");
            const baseline = await observations(); await setLanguage(l); captures = await captureWindow(uninstallDialog);
            const current = await observations();
            try {
              layoutCapture(fresh(baseline, current), l, copy.geometry, layoutAuthority(baseline, current), copy.languages[l].layoutTopology, "panel", "pre-click");
              layoutCapture(fresh(baseline, current), l, copy.geometry, layoutAuthority(baseline, current), copy.languages[l].layoutTopology, "dialog", "pre-click");
            }
            catch (error) { if (/missing/.test(String(error))) unavailable(String(error)); throw error; }
            return [baseline.archive, current.archive, copy.path];
          });
          await subcheck(`uninstall-action-captures-cancel-${l}`, async () => {
            if (!captures.length) unavailable(copy ? "current locale action PNG and hierarchy were not independently collected" : `source-bound action capture prerequisite unavailable: ${copyFailure}`);
            return captures;
          });
          proof(`visual-action-button-layout-${l}`, "unavailable", "Actual source-bound locale panel/dialog PNGs await authenticated padding, wrapping and clipping observations");
        }
        proof("all-action-runtime-coverage", "unavailable", "Three-locale panel/cancel observations require the genuine case17 final confirm and normal uninstall before action coverage can pass");
        break;
      case 22: {
        await languageRadioProgram();
        if (regression) {
          proof("destructive-error-copy-excluded", "unavailable", "Partial regression excludes actual uninstall confirmation and fault injection; full case22 is not accepted");
          break;
        }
        for (const locale of ["en", "ru", "zh"] as Language[]) {
          await setLanguage(locale);
          await subcheck(`startup-rejection-copy-${locale}`, rejectedStartup);
          await subcheck(`uninstall-retryable-error-copy-${locale}`, retryableUninstall);
        }
        break;
      }
      case 23: {
        let startupBaseline: Awaited<ReturnType<typeof observations>>;
        if (copy) proof("host-all-startup-description-copy", "passed", "Fresh source-bound actual production APIs emitted all three selected descriptions in all three locales", [copy.path, ...hostArtifacts]);
        await startupProfiles(setLanguage, async (mode) => {
          // Require a genuine changed selection even for interface, which is the entry state.
          if (mode === "interface" && startup === "interface") await selectStartup("hidden");
          startupBaseline = await observations();
          await selectStartup(mode);
        }, async (mode, l) => {
          await subcheck(`startup-${mode}-native-draw-transition-${l}`, async () => {
            const current = await observations(), rows = fresh(startupBaseline, current);
            const traces = rows.filter((row) => row.agent === "settings" && row.event === "radio.trace" && row.fields.key === "startup" && row.fields.value === mode);
            if (traces.length !== 1 || !Number.isSafeInteger(traces[0].fields.id)) unavailable("unique fresh startup radio trace missing");
            const authority = layoutAuthority(startupBaseline, current);
            const measured = radioTransition(rows, Number(traces[0].fields.id), authority);
            if (measured.outcome === "unavailable") unavailable(measured.reason);
            if (measured.outcome !== "measured") throw new Error(measured.reason);
            const path = join(c.output, `startup-radio-${l}-${mode}.json`);
            writeFileSync(path, JSON.stringify({ measurement: "window-draw-traversal", latencySla: null, ...measured }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
            return [path, startupBaseline.archive, current.archive];
          });
          await subcheck(`startup-${mode}-description-${l}`, async () => {
            const first = evidence.actions.length;
            if (!copy) unavailable(`source-bound startup copy unavailable: ${copyFailure}`);
            await startupDescriptionCapture(() => top(false), () => screenshot(`${l}-${mode}-startup-description`), copy, l, mode);
            return evidence.actions.slice(first).flatMap((action: any) => action.artifacts.map((artifact: any) => artifact.path));
          });
        }, returnInterface);
        await subcheck("cancel-does-not-uninstall", uninstallDialog);
        if (regression) proof("fault-rejection-excluded", "unavailable", "Partial regression excludes fault/error-copy acceptance");
        await subcheck("startup-rejected-mutation", async () => {
          const rejected = evidence.cases.find((row: Case) => row.number === 22).subchecks.filter((check: Subcheck) => /^startup-rejection-copy-(en|ru|zh)$/.test(check.id));
          if (rejected.length !== 3 || rejected.some((check: Subcheck) => check.status !== "passed" || !check.artifacts.length)) unavailable("actual source-bound three-locale startup rejection/timeout/copy observations are incomplete");
          return rejected.flatMap((check: Subcheck) => check.artifacts);
        });
        break;
      }
      case 24:
        proof("host-initial-state-authority", hostArtifacts.length ? "passed" : "unavailable", hostArtifacts.length ? "Fresh source-bound owning startup/state tests; host inputs remain distinct from emulator seeding" : hostFailure, hostArtifacts);
        await subcheck("initial-root-triad", async () => {
          if (!initialVehicle) unavailable("first clean install ordinary root state not captured");
          for (const state of Object.values(initialVehicle.armed))
            if (state.status !== "present" || state.value !== false) unavailable("initial false root readback unavailable; absent and unavailable remain distinct");
          return [initialVehicle.archive];
        });
        await subcheck("runtime-clean-absent-default-input-seeding", async () => {
          if (evidence.preparation.status !== "passed" || !evidence.preparation.artifacts.length || !initialCan || !initialVehicle) unavailable("clean nine-check absence, current OEM/CAN or initial root seeding evidence missing");
          if (initialCan.fev !== false || initialCan.pw !== true) throw new Error("ordinary prepared OEM initial defaults differ");
          for (const state of Object.values(initialVehicle.armed)) if (state.status !== "present" || state.value !== false) throw new Error("default initial input did not seed false root intent");
          return [...evidence.preparation.artifacts, ...initialCan.artifacts];
        });
        proof("other-initial-input-host-scope", hostArtifacts.length ? "passed" : "unavailable", hostArtifacts.length ? "Other initial source-policy inputs are covered only by fresh owning host tests; no emulator ABSENT+true input is claimed" : hostFailure, hostArtifacts);
        break;
      case 25:
        proof("host-bounded-retry-cancel", hostArtifacts.length ? "passed" : "unavailable", hostArtifacts.length ? "Fresh source-bound owning UNAVAILABLE recovery and stop/cancel tests" : hostFailure, hostArtifacts);
        await subcheck("current-multi-target-readback", async () => {
          const current = await observations();
          const status = inspectionResident(current.body);
          const processes = new Set(status?.injections.filter((row: any) => row.state === "active").map((row: any) => row.process));
          if (processes.size < 2 || processes.has(undefined)) unavailable("two real active target processes not independently identified");
          return [current.archive];
        });
        for (const vehicle of ["fev", "pw"] as Vehicle[]) await subcheck(`runtime-disable-cancellation-${vehicle}`, () => vehicleDisableCancellation(vehicle));
        await subcheck("runtime-state-outage-retry-recovery", () => stateOutage(false));
        await subcheck("runtime-state-outage-retry-cancel", () => stateOutage(true));
        break;
      case 26: {
        if (!evidence.boots.length || evidence.boots.some((b: Boot) => b.status !== "passed" && !(fullSemantic && semanticBoot(b))))
          unavailable(
            "one or more guest boots, including intermediate installer boots, lack complete independent evidence",
          );
        break;
      }
      case 27:
        await subcheck("vehicle-startup-current-receipts", async () => {
          // Prepare retained user intent before the dedicated boot. The typed
          // receipt validator remains generic over every enabled vehicle member.
          for (const vehicle of ["fev", "pw"] as Vehicle[]) {
            await toggle(VEHICLE[vehicle], true);
            const baseline = await observations();
            await selectOem(vehicle, vehicle === "fev");
            await currentVehicle(vehicle, vehicle === "fev", baseline, true);
          }
          const before = await observations(), previous = inspectionResident(before.body);
          residentStatusShape(previous);
          const enabledIds = [...enabled.entries()].filter(([,value]) => value).map(([id]) => id);
          if (!enabledIds.length) unavailable("independent enabled checkbox inventory missing");
          const startupDeadline = performance.now() + bootReadinessBudgetMs();
          await reboot();
          let observed = await observations(), resident = inspectionResident(observed.body);
          residentStatusShape(resident);
          if (observed.boot === before.boot || resident.releaseIdentity !== manifest.releaseIdentity ||
              resident.manifestSha256 !== digest(readFileSync(join(c.closure,"voboost.json"))) ||
              resident.generationId !== previous.generationId || resident.catalogSha256 !== digest(readFileSync(join(c.closure,"agents.json"))))
            throw new Error("startup fresh boot or installed identity differs");
          const accepted = resident.injections.map((row:any) => row.currentEndpoint?.acceptedStatus?.receipt).find((row:any) => row?.bootId === observed.boot);
          if (!accepted || !/^[1-9][0-9]*$/.test(accepted.daemonPid) || !Number.isSafeInteger(Number(accepted.daemonPid))) unavailable("current daemon accepted status receipt absent");
          const authority = {
            bootId:observed.boot, daemonPid:Number(accepted.daemonPid), daemonStarttime:accepted.daemonStarttime,
            manifestSha256:resident.manifestSha256, generationId:resident.generationId, catalogSha256:resident.catalogSha256,
          };
          let result = startupMilestoneEvidence(resident, authority, catalog, enabledIds);
          // API reads already consume time; no arbitrary sleep or latency SLA.
          while (result.status === "unavailable" && performance.now() < startupDeadline) {
            observed = await observations(); resident = inspectionResident(observed.body);
            residentStatusShape(resident);
            if (observed.boot !== authority.bootId || resident.releaseIdentity !== manifest.releaseIdentity) throw new Error("startup receipt observation crossed boot or release");
            result = startupMilestoneEvidence(resident, authority, catalog, enabledIds);
          }
          if (result.status !== "passed") unavailable(result.reason);
          const processes = new Set(result.intervals.map(row=>row.process));
          const preparation = startupPreparationEvidence(observed.logData.records, authority,
            resident.startupMilestones.targets.filter((target:any)=>processes.has(target.process)));
          if (preparation.status !== "passed") unavailable(preparation.reason);
          if (!native || !runtime) unavailable("independent startup CAN requires the owned native session");
          const canSample = await native.can({states:["IVI_SOC_MODESET","HUM_VSP_FUNCTION_SW"],timeout_ms:5000});
          const canAction = evidence.actions.at(-1);
          if (!canAction) throw new Error("startup CAN action correlation missing");
          canAction.nativeOperation = "vehicle.can.get";
          canAction.context = {kit:frozen.package,runtime,serial,bootId:authority.bootId,locale:language};
          const independentCan = startupVehicleCanEvidence(canSample,{
            bootId:authority.bootId,sessionId:runtime.sessionId,
            afterBootMs:Math.max(...result.intervals.map(row=>row.androidObservedAtBootMs)),
          });
          if (independentCan.status !== "passed") unavailable(independentCan.reason);
          const path = join(c.output,"vehicle-startup-current-receipts.json");
          writeFileSync(path,JSON.stringify({schemaVersion:1,measurement:"daemon-receipt-intervals",latencySla:null,
            backendExecutionLatency:"not measured",authority,bootId:observed.boot,archiveSha256:digest(readFileSync(observed.archive)),preparation,independentCan,canSample,canActionId:canAction.id,...result},null,2)+"\n",{flag:"wx",mode:0o600});
          return [before.archive,observed.archive,path];
        });
        proof("host-passive-acc-listener-contract", hostArtifacts.length ? "passed" : "unavailable", hostArtifacts.length ? "Fresh source-bound public ACC lifecycle host tests preserve the owning listener contract; this is not su/runtime preparation proof" : hostFailure, hostArtifacts);
        if (regression === "vehicle-startup") break;
        await subcheck("prepared-root-enforcing-package", async () => {
          immutable(); await fullVerify();
          const current = await observations();
          if (!/^enforcing=Enforcing$/m.test(current.body)) throw new Error("current Enforcing observation missing");
          const hidden = evidence.cases.find((row: Case) => row.number === 15), none = evidence.cases.find((row: Case) => row.number === 16);
          if (fullSemantic ? !fullFunctionalCase(hidden) || !fullFunctionalCase(none) : hidden.status !== "passed" || none.status !== "passed") unavailable("actual hidden/none authority behavior not independently passed");
          return [current.archive, machine.file];
        });
        await subcheck("root-preparation-authority-contract", async () => {
          if (installPreparation.length !== 2) unavailable("both actual install preparation transcripts are required");
          for (const install of installPreparation) {
            const proof = preparationProof(install.rows), action = evidence.actions.find((row: any) => row.id === install.actionId);
            if (!action || action.args[0] !== "--install" || action.exitCode !== 0 || action.timedOut || action.signal || action.overflow || !proof.readiness || !proof.enforcing || !proof.journal) unavailable("same-action successful Root/Remount/Permissive/system write and accepted journal preparation proof incomplete");
            const boots = evidence.boots.filter((boot: Boot) => boot.caseNumber === action.caseNumber && /^installer-/.test(boot.kind));
            if (!boots.length || boots.some((boot: Boot) => boot.status !== "passed" && !(fullSemantic && semanticBoot(boot)))) unavailable("independent install Enforcing boot observations incomplete");
          }
          return installPreparation.flatMap((install) => install.artifacts);
        });
        break;
      default:
        throw new Error("unknown case ID");
    }
  };
  try {
    immutable();
    if (fullSemantic || c.localArtifact) evidence.sourceAdmission = await fullSourceAdmission();
    const verification = await record({
      executable: join(c.package, "voboost-installer"),
      args: ["--verify-package"],
      timeoutMs: 120000,
    });
    resultOk(verification);
    if (
      !verification.stdout.includes(`${manifest.releaseIdentity}: exact offline closure verified`)
    )
      throw new Error("package verification identity differs");
    hostArtifacts = await agentHostGates(c.workspaceRoot, c.closure, c.output, record, c.localArtifact ? { dmg: c.dmg, verificationDelta: c.localArtifact.verificationDelta } : undefined);
    copy = settingsCopy(join(c.workspaceRoot, "voboost-agents"), c.closure);
    runtime = native ? await native.status() : parseVoyemuStatus(await record(voyemuInvocation(c, "status")), c.voyemuProfile);
    serial = `emulator-${runtime.port}`;
    evidence.runtime = { ...runtime, serial };
    ui = new InstallerUi(
      join(c.package, "voboost-installer"),
      serial,
      join(c.output, "ui"),
      uiAdapter,
      ownership,
      native ? async () => {
        // Keep independent root/Enforcing boot inspection; the tree is UI evidence only.
        // ownedPrimaryObservation always invokes sameEpoch before the multimedia UI read.
        // Reuse only expected identity; sameEpoch still performs a fresh root/Enforcing probe.
        const ownerBoot = currentBoot ?? await probe(true);
        currentBoot = ownerBoot;
        const deadline = Math.min(performance.now() + 15000, firstBootCaptureDeadline ?? Infinity, settingsNavigationDeadline ?? Infinity);
        const sameEpoch = async () => {
          await ownership();
          if (await probe(true) !== ownerBoot) throw new Error("native primary observation condition crossed a boot");
        };
        const nodes = await ownedPrimaryObservation(async remainingMs => {
            let result: any;
            const actionCount = evidence.actions.length;
            let completed = false;
            try { result = await native.primaryMultimediaUi(remainingMs); completed = true; }
            finally {
              // A guard can reject before record() creates an action. Preserve that
              // original failure instead of correlating an unrelated preceding read.
              if (completed || evidence.actions.length !== actionCount) {
                const action = evidence.actions.at(-1);
                if (evidence.actions.length !== actionCount + 1 || action?.nativeOperation !== "session.ui") throw new Error("native tree action correlation differs");
                action.context = { kit: frozen.package, runtime, serial, bootId: ownerBoot, locale: language };
                persist(); // Failed native reads remain failed actions with their raw JSON.
              }
            }
            const nodes = nativeTree(result, { sessionId: runtime!.sessionId, pid: runtime!.pid, bootId: ownerBoot, profile: c.voyemuProfile });
            nativeInputTree = result;
            return nodes;
        }, sameEpoch, { sessionId: runtime!.sessionId, pid: runtime!.pid, bootId: ownerBoot, profile: c.voyemuProfile },
          initialObservation, bootTransitions, deadline, () => performance.now(), firstBootCaptureDeadline ?? undefined);
        snapshotBoot = ownerBoot;
        return nodes;
      } : undefined,
      native ? async request => {
        if (installerActive || evidence.activeOperation) throw new Error("active transaction lease retained; native UI mutation forbidden");
        currentBoot = await probe(true);
        faultLive?.();
        const dispatch = async (op: "input.tap" | "input.gesture" | "input.key.press" | "multimedia.launch", fields: Record<string, unknown>) => {
          try { return await native.call(op, fields); }
          finally {
            const action = evidence.actions.at(-1);
            if (action?.nativeOperation !== op) throw new Error("native input action correlation differs");
            action.context = { kit: frozen.package, runtime, serial, bootId: currentBoot, locale: language };
            persist();
          }
        };
        const app = nativeApp(request);
        if (app) {
          const expected = { ...app, boot_id: currentBoot! };
          const result = await dispatch("multimedia.launch", expected);
          nativeAppReceipt(result, expected);
          // Acceptance is not a scene proof. Existing callers still wait for UI.
          // The secondary lifetime proof is passive; it never launches an app.
          if (request.action === "open-secondary-launcher") return ui.call({ action: "secondary-capture-proof" });
          return { native: true, result };
        }
        if (request.action === "key") {
          // SDK field4 uses W3C DOM keys, not Android keycodes or uppercase labels.
          const key = nativeKey(request.key);
          const result = await dispatch("input.key.press", { key });
          if (result.ok !== true || result.session_id !== runtime!.sessionId || result.sdk_completed !== true || result.key !== key || result.phase !== "press" || result.visible_effect_verified !== false)
            throw new Error("native key completion differs; inspect operation before replay");
          return { native: true, result };
        }
        if (!["tap", "swipe", "drag", "gesture", "tbox-burst"].includes(String(request.action)))
          throw new Unavailable("native interaction has no verified backend contract: " + request.action);
        if (snapshotBoot !== currentBoot) throw new Error("native input observation belongs to another boot");
        const pointer = nativePointerRequest(request, nativeInputTree, { sessionId: runtime!.sessionId, pid: runtime!.pid, boot: currentBoot! });
        const result = await dispatch(pointer.op, pointer.fields);
        if (result.ok !== true || result.session_id !== runtime!.sessionId || result.sdk_completed !== true || result.pointer_released !== true || result.screen !== "primary" || result.sdk_display_id !== 0 || result.pre_dispatch_fenced !== true || result.atomic_ui_input !== false || result.occlusion !== "known_window_bounds_only" || result.touchability !== "unknown" || result.visible_effect_verified !== false)
          throw new Error("native pointer completion differs; inspect operation before replay");
        if (pointer.op === "input.tap" && (result.requested_count !== (request.action === "tbox-burst" ? 5 : 1) || result.dispatched_count !== result.requested_count))
          throw new Error("native tap batch incomplete; inspect operation before replay");
        return { native: true, result };
      } : undefined,
    );
    await fullVerify();
    // Native navigation observes its boot before dispatch; legacy mode retains its initial CLI guard.
    // The first mandatory boot read follows before any case or preparation mutation.
    if (installerActive || evidence.activeOperation || faultLive || currentBoot !== null)
      throw new Error("initial Settings navigation requires no host writer and no prior boot observation");
    initialSettingsNavigation = !native;
    try { await ui.call({ action: "open-settings" }); }
    finally { initialSettingsNavigation = false; }
    if (regression) {
      const before = await bootObservation();
      const result = await cli(["--status"]); resultOk(result);
      const installed = jsonReceipt(result.stdout);
      const receipt = await ui.call({ action: "resident-status" });
      const observed = parseResidentStatus(receipt.body), resident = inspectionResident(receipt.body);
      residentStatusShape(resident);
      if (!before.bootCompleted || observed.boot !== before.boot || !observed.bootCompleted ||
          installed.deviceSerial !== serial || installed.installedVersion !== manifest.releaseIdentity ||
          installed.contentRelation !== "same" || installed.health !== "healthy" ||
          installed.daemonStatus !== "running" || installed.startupMode !== "interface" ||
          resident.releaseIdentity !== manifest.releaseIdentity ||
          resident.manifestSha256 !== digest(readFileSync(join(c.closure, "voboost.json"))) ||
          resident.catalogSha256 !== digest(readFileSync(join(c.closure, "agents.json"))) ||
          resident.generationId !== installed.activeGeneration || resident.startupMode !== "interface" ||
          resident.state !== "ready" || resident.panic || resident.killed || resident.injections.some((row:any)=>row.state==="failed"))
        throw new Error("installed regression admission identity/readiness differs");
      if ((await bootObservation()).boot !== before.boot) throw new Error("regression admission crossed boot");
      evidence.preparation.reason = "Installed-kit regression: absence/install preparation intentionally not executed";
      // Panel navigation and checkbox reads follow current root/package admission.
      await open(); language = await observedLanguage(); initialLanguage = language;
      // Default radio/vehicle inventory remains complete. PW needs only its signed
      // control and exclusions; enabled-map consumers exist only in cases 6/15.
      // Launcher case12 retains its own ordinary checkbox mutation/ACK/readback.
      if (regression !== "launcher") {
        const requiredInventory = vehiclePart
          ? [VEHICLE[vehiclePart === "pw" ? "pw" : "fev"], ...(catalog.find((agent: any) => agent.id === VEHICLE[vehiclePart === "pw" ? "pw" : "fev"])?.disables ?? [])]
          : STABLE.filter(id => id !== "settings");
        let nodes = await top();
        for (let page = 0; page < 12; page++) {
          for (const [id, checked] of visibleAgentChecks(nodes, catalog, language))
            enabled.set(id, checked);
          if (requiredInventory.every(id => enabled.has(id))) break;
          await scroll("down", nodes);
          nodes = await ui.snapshot();
        }
        await top();
      }
    } else {
    const firstPreparationAction = evidence.actions.length;
    evidence.preparation.status = "unavailable";
    evidence.preparation.reason = "preparation capture pending";
    try {
      const before = (await inspect(true, true)).boot;
      const result = await uninstall();
      const { kind, receipt } = preparationAbsence(result);
      Object.assign(evidence.preparation, { kind, actionId: result.actionId, bootId: receipt.bootId, context: { kit: frozen.package, runtime: { ...runtime, serial }, serial, bootId: receipt.bootId } });
      if (receipt.bootId !== before) {
        const boot = await bootCheck("preparation-uninstall", before, receipt.bootId);
        if (boot.status !== "passed" && !(fullSemantic && semanticBoot(boot))) throw new Error(boot.reason);
      }
      await absenceReadback(receipt.bootId, "prepared-absence");
      evidence.preparation.status = "passed";
      evidence.preparation.reason = "Complete nine-check preparation receipt, fresh absent status, exact boot, prepared inventory, Enforcing and UI capture";
    } catch (error) {
      evidence.preparation.status = error instanceof Unavailable ? "unavailable" : "failed";
      evidence.preparation.reason = String(error);
      throw error;
    } finally {
      evidence.preparation.actions = evidence.actions.slice(firstPreparationAction).map((a: any) => a.id);
      evidence.preparation.artifacts = evidence.actions.slice(firstPreparationAction).flatMap((a: any) => a.artifacts.map((artifact: any) => artifact.path));
      persist();
    }
    }
    for (const number of regression ? (settingsPart === "postboot" ? [22, 23] : vehiclePart === "pw" ? [14] : vehiclePart === "fev-drawer" ? [13] : REGRESSION_ORDER[regression]) : EXECUTION_ORDER) {
      caseNumber = number;
      const row = evidence.cases.find((x: Case) => x.number === number);
      try {
        if (number === 17 && review) {
          let reviewBoot: string | null = null;
          await consumeVisualReview(evidence, c.output, copy ? [copy.path, join(c.closure, "agents.json"), join(c.closure, "voboost.json")] : [], review, async () => {
            if (evidence.activeOperation) throw new Error("visual review requires no active transaction lease");
            immutable();
            const before = await bootObservation();
            if (reviewBoot !== null && reviewBoot !== before.boot) throw new Error("visual review changed the guest boot");
            await fullVerify();
            const after = await bootObservation();
            if (before.boot !== after.boot || !before.bootCompleted || !after.bootCompleted) throw new Error("visual review boundary crossed an incomplete or changed boot");
            reviewBoot = after.boot;
          });
          persist();
        }
        await caseProgram(number);
        if (fullSemantic) proof("full-case-program-completed", "passed", "Original full case program reached its terminal boundary");
        if (deferred && regression === "launcher") proof("launcher-visual-pending", "unavailable", "Images deferred by this partial regression mode; icon geometry, rounding, frame lighting and inversion remain pending");
        if (deferred && regression === "settings-radio") proof("settings-radio-visual-pending", "unavailable", "Images deferred by this partial regression mode; image, animation and visual acceptance remain pending");
        if (deferred && regression && installedSemanticRequired(regression)) proof("installed-visual-pending", "unavailable", "Images deferred by this partial regression mode; actual codepoint/state/lifecycle checks do not prove visible appearance");
        row.status = fullSemantic ? "unavailable" : row.subchecks.some((check: Subcheck) => check.status === "unavailable") ? "unavailable" : "passed";
        row.reason = row.status === "passed" ? "required implemented observations passed" : "one or more required independent observations unavailable";
        if (!row.subchecks.length) row.subchecks.push({ id: "runtime-observations", status: "passed", reason: row.reason, artifacts: [] });
      } catch (e) {
        row.status = e instanceof Unavailable ? "unavailable" : "failed";
        row.reason = String(e);
        const stackBytes = JSON.stringify({ caseNumber: number, frames: caseFailureFrames(e) }, null, 2) + "\n";
        const stackSha = createHash("sha256").update(stackBytes).digest("hex");
        const stackPath = join(c.output, `case-${number}-stack-${stackSha}.json`);
        writeFileSync(stackPath, stackBytes, { flag: "wx", mode: 0o600 });
        row.subchecks.push({
          id: e instanceof Unavailable ? "missing-observable" : "runtime-observations",
          status: row.status,
          reason: row.reason,
          artifacts: [stackPath],
        });
        if (row.status === "failed") break;
      }
      if (number === 17 && (row.status === "passed" || (fullSemantic && fullFunctionalCase(row))) && copy) {
        const artifacts = actionCoverage(evidence, copy, normalAbsence, fullSemantic);
        if (artifacts) {
          const check = evidence.cases.find((item: Case) => item.number === 21).subchecks.find((item: Subcheck) => item.id === "all-action-runtime-coverage");
          if (!check) throw new Error("case21 required action coverage subcheck missing");
          Object.assign(check, { status: "passed", reason: "Actual three-locale panel/cancel captures and genuine final-language confirm/normal uninstall with independent boot verification passed", artifacts });
          if (!fullSemantic) updateReviewedCases(evidence);
        }
      }
      persist();
    }
  } catch (e) {
    if (caseNumber === 0) {
      evidence.preparation.status = e instanceof Unavailable ? "unavailable" : "failed";
      evidence.preparation.reason = String(e);
    } else {
      const row = evidence.cases.find((x: Case) => x.number === caseNumber);
      row.status = e instanceof Unavailable ? "unavailable" : "failed";
      row.reason = String(e);
      row.subchecks.push({ id: "terminal-observation", status: row.status, reason: row.reason, artifacts: [] });
    }
  } finally {
    if (observerCompletion) await observerCompletion;
    try { await monitor; } catch (error) { monitorResult = error as Error; }
    if (monitorResult) {
      const c26 = evidence.cases.find((x: Case) => x.number === 26);
      c26.status = "failed";
      c26.reason = String(monitorResult);
      c26.subchecks.push({ id: "intermediate-boot-monitor", status: "failed", reason: c26.reason, artifacts: evidence.boots.filter((boot: Boot) => boot.status === "failed").flatMap((boot: Boot) => boot.actions.flatMap((id) => evidence.actions.find((action: any) => action.id === id)?.artifacts.map((artifact: any) => artifact.path) ?? [])) });
    }
    const cleanupResult = await launcherFinallyCleanup({ scenario: regression, receipt: evidence.fixtureCleanup, activeOperation: evidence.activeOperation, monitorHealthy: !monitorResult, leaseStatus: evidence.leaseStatus }, async () => {
      const priorCase = caseNumber; caseNumber = 18;
      const cleanupRow = evidence.cases.find((row: Case) => row.number === 18);
      try { await cleanupOwnedFixture("removal-only"); if (cleanupRow.status === "not-run") { cleanupRow.status = "unavailable"; cleanupRow.reason = "Finally cleanup only; classification/visual checks did not run"; } }
      catch (error) { if (cleanupRow.status !== "failed") { cleanupRow.status = error instanceof Unavailable ? "unavailable" : "failed"; cleanupRow.reason = String(error); } throw error; }
      finally { caseNumber = priorCase; persist(); }
    });
    // The cleanup row retains its own error; the original case failure is never overwritten.
    if (cleanupResult.attempted) persist();
    if (serial && (evidence.preparation.status === "failed" || evidence.cases.some((x: Case) => x.status === "failed"))) {
      for (const args of [["--diagnostics"], ["--logs", join(c.output, "logs")]])
        try {
          await cli(args);
        } catch {}
      try {
        await inspect();
        await fullVerify();
      } catch {}
    }
    try { functionalEvidence(evidence); }
    catch (error) {
      evidence.passed = false;
      persist();
      throw error;
    }
    if (c.localArtifact) { immutable(); evidence.sourceAdmission = await fullSourceAdmission(); }
    evidence.passed =
      evidence.preparation.status === "passed" &&
      evidence.fixtureCleanup.status === "passed" &&
      evidence.cases.length === 27 &&
      evidence.cases.every(
        (x: Case) => x.status === "passed" && requiredSubchecksPassed(x),
      ) &&
      evidence.boots.length > 0 &&
      evidence.boots.every((b: Boot) => b.status === "passed");
    if (fullSemantic) {
      evidence.passed = false;
      immutable();
      evidence.sourceAdmission = await fullSourceAdmission();
      const summary = fullSemanticSummary(evidence);
      writeFileSync(join(c.output, "full-semantic-summary.json"), JSON.stringify(summary, null, 2) + "\n", { flag: "wx", mode: 0o600 });
    }
    if (regression) {
      evidence.passed = false;
      const summary = regressionSummary(evidence, regression);
      writeFileSync(join(c.output, "regression-summary.json"), JSON.stringify(summary, null, 2) + "\n", { flag: "wx", mode: 0o600 });
    }
    persist();
  }
  return evidence;
}
export function retainKeyboardCapture(root: string, source: string, target: string): void {
  const temporary = join(root, ".local/build/tmp");
  if (!isAbsolute(root) || realpathSync(temporary) !== temporary || dirname(source) !== temporary ||
      !/^keyboard-capture-[0-9a-f-]{36}$/.test(basename(source)) || realpathSync(source) !== source ||
      !lstatSync(source).isDirectory() || readdirSync(source).sort().join() !== "manifest.json,primary.png")
    throw new Error("keyboard capture cleanup ownership differs");
  const files = ["manifest.json", "primary.png"];
  const bytes = files.map(file => {
    const path = join(source, file);
    if (realpathSync(path) !== path || !lstatSync(path).isFile()) throw new Error("keyboard capture source differs");
    return readFileSync(path);
  });
  const manifest = JSON.parse(bytes[0].toString("utf8"));
  if (manifest.frames?.length !== 1 || manifest.frames[0].file !== "primary.png" || manifest.frames[0].sha256 !== digest(bytes[1]))
    throw new Error("keyboard capture manifest hash differs");
  mkdirSync(target, {recursive:true, mode:0o700});
  for (let i=0; i<files.length; i++) {
    const path=join(target,files[i]);
    writeFileSync(path,bytes[i],{flag:"wx",mode:0o600});
    if (digest(readFileSync(path)) !== digest(bytes[i])) throw new Error("keyboard capture retained hash differs");
  }
  // Only delete after every artifact is recoverable and byte-verified. Failures retain the source.
  rmSync(source,{recursive:true});
}
async function main() {
  if (process.argv.length !== 3 || !isAbsolute(process.argv[2]))
    throw new Error("usage: emulator-gate.ts /absolute/config.json");
  const c = validateConfig(JSON.parse(readFileSync(process.argv[2], "utf8")));
  const e = await runGate(c, spawnAdapter);
  if (!e.passed) process.exitCode = 1;
}
if (
  process.argv[1] &&
  realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)
)
  main().catch((e) => {
    runtimeLogger("emulator-gate").error(e);
    process.exitCode = 2;
  });
