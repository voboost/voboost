import { fontDiagnosticOnly } from "./font-observations.ts";
/** Functional acceptance never accepts screenshots, animation or visible appearance. */
export const FULL_CASE_ORDER = [1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26] as const;
const locales = ["en", "ru", "zh"];
const vehicle = (name: string) => ["cold-baseline", "warm-baseline", "armed-current", "cold-restore", "warm-restore"].map(id => name + "-" + id);
export const FULL_REQUIRED: Record<number, string[]> = {
  1: [], 2: [], 3: ["same-normal-removal-receipt"], 4: [], 5: [],
  6: ["language-opposite-selected"], 7: ["language-original-restored"],
  8: ["owning-source-stop-policy", "agent-lifecycle-and-keyboard-exclusion"],
  9: ["actual-cyrillic-input"],
  10: ["actual-english-input"], 11: ["actual-mixed-layout-input"],
  12: ["launcher-visible-page-order-after-reboot", "launcher-semantic-surfaces-lifetime", "launcher-two-screen-disk-cache-after-reboot", "launcher-theme-cache-roundtrip"],
  13: [...vehicle("fev"), ...["arm", "hybrid", "save", "electric-restore", "explicit-off-hold", "off-circle", "cold-off"].map(id => "fev-drawer-" + id)], 14: vehicle("pw"),
  15: ["hidden-enabled-agents-tbox-recovery"],
  16: ["none-process-inventory", "none-process-absence-computer-recovery"],
  17: ["settings-uninstall-normal-absence-reboot"],
  18: ["oem-stock-baseline", "fresh-package-classification", "oem-grid-retained", "oem-icon-bind-provenance", "owned-fixture-cleanup"],
  19: ["moscow-and-yo-codepoints", "mixed-ru-en-codepoints"],
  20: locales.map(l => "source-layout-measurements-" + l),
  21: [...locales.map(l => "uninstall-action-measurements-cancel-" + l), "all-action-runtime-coverage"],
  22: ["language-loop-copy-dialog-cancel", "language-process-resource-locale", "i18n-system-picker-multi-owner", "language-reboot-persistence", ...locales.flatMap(l => ["startup-rejection-copy-" + l, "uninstall-retryable-error-copy-" + l])],
  23: ["host-all-startup-description-copy", ...locales.flatMap(l => ["none", "hidden", "interface"].flatMap(mode => ["startup-"+mode+"-native-draw-transition-"+l, "startup-"+mode+"-description-"+l])), "cancel-does-not-uninstall", "startup-rejected-mutation"],
  24: ["host-initial-state-authority", "initial-root-triad", "runtime-clean-absent-default-input-seeding", "other-initial-input-host-scope"],
  25: ["host-bounded-retry-cancel", "current-multi-target-readback", "runtime-disable-cancellation-fev", "runtime-disable-cancellation-pw", "runtime-state-outage-retry-recovery", "runtime-state-outage-retry-cancel"],
  26: [], 27: ["vehicle-startup-current-receipts", "host-passive-acc-listener-contract", "prepared-root-enforcing-package", "root-preparation-authority-contract"],
};
/** Exact visual-only inventory. Unknown unavailable checks remain blockers. */
export function fullVisualOnly(number: number, id: string): boolean {
  const ids: Record<number, string[]> = {
    9:["visual-font-cyrillic"], 12:["visual-launcher-third-party-icons"],
    13:["vehicle-image-pending"], 14:["vehicle-image-pending"],
    18:["visual-oem-stock-icons"], 19:["visual-navi-input-spacing"],
    20:[...locales.flatMap(l=>["panel-captures-"+l,"visual-control-layout-"+l]),"three-locale-panel-captures"],
    21:locales.flatMap(l=>["uninstall-action-captures-cancel-"+l,"visual-action-button-layout-"+l]),
  };
  return (ids[number] ?? []).includes(id);
}
export function semanticBoot(boot: any): boolean {
  return boot?.status === "unavailable" && boot.semanticStatus === "passed" && boot.screenshot === null;
}
export function fullFunctionalCase(row: any): boolean {
  if (!row || !Object.hasOwn(FULL_REQUIRED,row.number) || row.status === "failed" || row.status === "not-run" || !Array.isArray(row.subchecks)) return false;
  const required = [...FULL_REQUIRED[row.number], "full-case-program-completed"];
  if (!required.every(id => row.subchecks.filter((c:any)=>c.id===id && c.status==="passed").length===1)) return false;
  if (new Set(row.subchecks.map((c:any)=>c.id)).size !== row.subchecks.length) return false;
  return row.subchecks.every((c:any)=>fontDiagnosticOnly(row.number,c.id) ? c.status==="passed" || c.status==="unavailable" : fullVisualOnly(row.number,c.id) ? c.status==="unavailable" : c.status==="passed");
}
export function fullSemanticSummary(e: any): any {
  const rows = e.cases ?? [];
  const complete = e.functionalMode === "full27" && e.imageCapture === "deferred" && e.regressionScenario === undefined && e.passed === false &&
    e.preparation?.status === "passed" && e.fixtureCleanup?.status === "passed" && !e.activeOperation &&
    rows.length===27 && new Set(rows.map((r:any)=>r.number)).size===27 && FULL_CASE_ORDER.every(n=>fullFunctionalCase(rows.find((r:any)=>r.number===n))) &&
    Array.isArray(e.boots) && e.boots.length>0 && e.boots.every(semanticBoot) && e.sourceAdmission?.status === "passed";
  return { schemaVersion:1,evidenceType:"emulator-full-functional",invocationId:e.invocationId,identity:e.identity,runtime:e.runtime,
    imageCapture:"deferred",publication:"forbidden",passed:false,fullAcceptance:false,visualAcceptance:false,
    semanticFullAcceptance:complete,requiredCases:[...FULL_CASE_ORDER],requiredChecks:FULL_REQUIRED,
    pendingAcceptance:["images","animation","visible appearance"],
    checks:rows.map((r:any)=>({number:r.number,semanticPassed:fullFunctionalCase(r),subchecks:r.subchecks})) };
}

/** Optional Paint provenance never substitutes for functional or visual proof. */
export function requiredSubchecksPassed(row: any): boolean {
  return !!row && Array.isArray(row.subchecks) && row.subchecks.length > 0 &&
    new Set(row.subchecks.map((s: any) => s?.id)).size === row.subchecks.length &&
    row.subchecks.every((s: any) => typeof s?.id === "string" && s.id.length > 0 &&
      (s.status === "passed" || (s.status === "unavailable" && fontDiagnosticOnly(row.number, s.id))));
}
