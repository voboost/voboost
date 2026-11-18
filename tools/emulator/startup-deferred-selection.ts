import { inspectionResident } from "./functional-observations.ts";
const modes = ["interface", "hidden", "none"];
function identities(body: string, boot: string): Map<string, string> {
  const sections = [...body.matchAll(/^current-process-identities:\n([\s\S]*?)^current-process-identities-end=complete$/gm)];
  if (sections.length !== 1) throw new Error("startup current process identity section missing or ambiguous");
  const text = sections[0][1];
  if (!text.startsWith(`schemaVersion=1\nbootId=${boot}\nenforcing=Enforcing\n`) || !text.endsWith(`bootIdAfter=${boot}\nenforcingAfter=Enforcing\nbootCompletedAfter=1\n`)) throw new Error("startup process identity boot fence differs");
  const rows = new Map<string,string>();
  for (const line of text.split("\n").filter(row => row.startsWith("process="))) {
    const row = /^process=([A-Za-z0-9_.:-]+) pid=([1-9][0-9]*) starttime=([1-9][0-9]*) argv0=([A-Za-z0-9_./:-]+)$/.exec(line);
    if (!row) { if (/^process=[A-Za-z0-9_.:-]+ unavailable=missing-or-invalid$/.test(line)) continue; throw new Error("startup process identity row malformed"); }
    if (rows.has(row[1])) throw new Error("startup process identity duplicate");
    rows.set(row[1], row.slice(2).join(":"));
  }
  for (const name of ["voboost-inject", "com.qinggan.app.setting"]) if (!rows.has(name)) throw new Error("startup required current process identity absent");
  return rows;
}
/** Selecting a radio saves next-boot configuration and must leave this boot's owner, hosts and effective mode intact. */
export function startupDeferredSelection(before: {boot:string;body:string}, after: {boot:string;body:string}, saved: any, expected: {serial:string;releaseIdentity:string;manifestSha256:string;want:string}): void {
  if (before.boot !== after.boot) throw new Error("startup selection crossed boot");
  const old = inspectionResident(before.body), current = inspectionResident(after.body);
  for (const key of ["releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "startupMode"]) if (typeof old[key] !== "string" || old[key] !== current[key]) throw new Error("startup selection changed effective resident identity or mode");
  if (current.releaseIdentity !== expected.releaseIdentity || current.manifestSha256 !== expected.manifestSha256 || !modes.includes(current.startupMode) || current.state !== "ready" || current.killed !== false || current.panic !== false) throw new Error("startup selection resident is not ready");
  const previous = identities(before.body,before.boot), next = identities(after.body,after.boot);
  for (const [name, identity] of previous) if (next.get(name) !== identity) throw new Error("startup selection restarted or stopped an existing process");
  for (const row of old.injections.filter((row:any) => row.state === "active")) {
    const matches=current.injections.filter((other:any)=>other.id===row.id && other.process===row.process);
    if(matches.length!==1 || matches[0].state!=="active" || matches[0].lifecycleGeneration!==row.lifecycleGeneration) throw new Error("startup selection deactivated or replaced an active injection");
  }
  if(saved.deviceSerial!==expected.serial || saved.installedVersion!==expected.releaseIdentity || saved.contentRelation!=="same" || saved.daemonStatus!=="running" || saved.activeGeneration!==current.generationId || !modes.includes(saved.startupMode)) throw new Error("startup saved configuration authority differs");
}
