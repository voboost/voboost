import { secondaryLauncherProof } from "./installer-ui.ts";
import { oemTargetPid } from "./process-observations.ts";
import { inspectionResident } from "./functional-observations.ts";
export function launcherLifetime(beforeBody: unknown, observed: { boot: string; body: string }, afterBody: unknown) {
  const before = secondaryLauncherProof(beforeBody), after = secondaryLauncherProof(afterBody);
  if (before.boot !== after.boot || before.targetPid !== after.targetPid || before.targetStarttime !== after.targetStarttime || observed.boot !== before.boot) throw new Error("launcher observation crossed boot or process lifetime");
  const process = "com.qinggan.app.launcher", resident = inspectionResident(observed.body);
  if (oemTargetPid(observed.body, process) !== before.targetPid) throw new Error("launcher native lifetime and independent current process differ");
  const selected = resident.injections.filter((row: any) => row.id === "launcher-apps" && row.process === process && row.state === "active");
  if (selected.length !== 1) throw new Error("current active launcher injection missing for native lifetime");
  return { boot: before.boot, process, targetPid: before.targetPid, targetStarttime: before.targetStarttime, generationId: resident.generationId, catalogSha256: resident.catalogSha256 };
}
