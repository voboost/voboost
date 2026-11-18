import type { LogRecord } from "./installer-logs.ts";
import type { StartupIdentity, StartupTarget } from "./startup-milestones.ts";

const clock = "daemon.monotonicSinceOwnerStart";
const keys = ["schemaVersion","clockDomain","bootId","daemonPid","daemonStarttime","manifestSha256","generationId","catalogSha256","process","targetPid","targetStarttime","attempt","observedScanBeginMs","observedScanEndMs","preflightBeginMs","preflightEndMs","outcome","stage"];
const stages = new Set(["maps","authority","desired","bootstrap","payload","final-attestation","dispatch","rebind"]);
const fail = (reason: string): never => { throw new Error(`startup preparation ${reason}`); };
const number = (s: unknown, positive = false): number => {
  if (typeof s !== "string" || !/^(0|[1-9][0-9]*)$/.test(s)) fail("noncanonical integer");
  const n = Number(s);
  if (!Number.isSafeInteger(n) || (positive && n === 0)) fail("unsafe integer");
  return n;
};
export type PreparationInterval = {
  process:string; pid:number; starttime:string; attempt:number;
  observedScanBeginMs:number; observedScanEndMs:number;
  preflightBeginMs:number; preflightEndMs:number; dispatchMs:number; dispatchAcceptedMs:number;
  observationUncertaintyMs:number; discoveryToPreflightMs:number;
  preflightMs:number; observedToDispatchMs:number;
};
type Result = {status:"passed"; clockDomain:typeof clock; processBirthLatency:"not measured"; intervals:PreparationInterval[]}
  | {status:"unavailable"; reason:string};
/** Qualified daemon log diagnostics; owner-clock intervals are neither process birth nor CAN latency. */
export function startupPreparationEvidence(records:readonly LogRecord[], identity:StartupIdentity, targets:readonly StartupTarget[]):Result {
  if (!targets.length) return {status:"unavailable",reason:"preparation target inventory absent"};
  const wanted = new Map(targets.map(t=>[t.process,t]));
  if (wanted.size !== targets.length) fail("duplicate target inventory");
  const current = new Map<string,PreparationInterval>();
  const attempts = new Set<string>();
  const observations = new Map<string,string>();
  let count = 0;
  for (const r of records) {
    if (r.source.kind !== "daemon" || r.component !== "startup" || r.message !== "startup.preparation") continue;
    const f = r.fields;
    if (f.bootId !== identity.bootId || f.daemonPid !== String(identity.daemonPid)
        || f.daemonStarttime !== identity.daemonStarttime || f.manifestSha256 !== identity.manifestSha256
        || f.generationId !== identity.generationId || f.catalogSha256 !== identity.catalogSha256) continue;
    if (++count > 4096) fail("record budget exceeded");
    const dispatched = f.outcome === "dispatched" || f.outcome === "rebind";
    const expected = dispatched ? [...keys,"dispatchMs","dispatchAcceptedMs"] : keys;
    if (Object.keys(f).length !== expected.length || expected.some(k=>!Object.hasOwn(f,k))
        || Object.values(f).some(v=>typeof v !== "string")) fail("closed fields differ");
    if (f.schemaVersion !== "1" || f.clockDomain !== clock || !stages.has(String(f.stage))
        || !["dispatched","failed","rebind"].includes(String(f.outcome))
        || (f.outcome === "dispatched" && f.stage !== "dispatch")
        || (f.outcome === "rebind" && f.stage !== "rebind")) fail("contract differs");
    const pid = number(f.targetPid,true), attempt = number(f.attempt,true);
    if (pid < 2 || pid > 2147483647 || attempt > 4294967295
        || typeof f.process !== "string" || !f.process || Buffer.byteLength(f.process)>255
        || typeof f.targetStarttime !== "string" || !/^[1-9][0-9]*$/.test(f.targetStarttime)
        || BigInt(f.targetStarttime)>18446744073709551615n) fail("target identity differs");
    const begin = number(f.observedScanBeginMs), end = number(f.observedScanEndMs);
    const preflightBegin = number(f.preflightBeginMs), preflightEnd = number(f.preflightEndMs);
    if (begin>end || end>preflightBegin || preflightBegin>preflightEnd) fail("clock order differs");
    const dispatch = dispatched ? number(f.dispatchMs) : null;
    const accepted = dispatched ? number(f.dispatchAcceptedMs) : null;
    if (dispatch !== null && (dispatch<preflightEnd || accepted!<dispatch)) fail("dispatch precedes preparation");
    const t = wanted.get(f.process);
    if (!t || t.pid !== pid || t.starttime !== f.targetStarttime) continue;
    const key = `${f.process}:${pid}:${f.targetStarttime}:${attempt}`;
    if (attempts.has(key)) fail("duplicate attempt");
    attempts.add(key);
    const observation = `${begin}:${end}`;
    if (observations.has(f.process) && observations.get(f.process)!==observation)
      fail("first observation changed");
    observations.set(f.process,observation);
    if (f.outcome !== "dispatched") continue;
    // Retried spawns are real diagnostics, but cannot substitute for the first dispatch.
    if (t.dispatchMs === null || dispatch!==t.dispatchMs) continue;
    if (t.adoptedMs !== null && accepted!>t.adoptedMs) fail("attested dispatch differs");
    const previous = current.get(f.process);
    if (previous && (previous.observedScanBeginMs!==begin || previous.observedScanEndMs!==end))
      fail("first observation changed");
    if (previous && previous.attempt<attempt) continue;
    current.set(f.process,{
      process:f.process,pid,starttime:f.targetStarttime,attempt,
      observedScanBeginMs:begin,observedScanEndMs:end,preflightBeginMs:preflightBegin,
      preflightEndMs:preflightEnd,dispatchMs:dispatch!,dispatchAcceptedMs:accepted!,
      observationUncertaintyMs:end-begin,discoveryToPreflightMs:preflightBegin-end,
      preflightMs:preflightEnd-preflightBegin,observedToDispatchMs:dispatch!-end,
    });
  }
  if (current.size!==wanted.size) return {status:"unavailable",reason:"current fresh-dispatch preparation absent"};
  return {status:"passed",clockDomain:clock,processBirthLatency:"not measured",intervals:targets.map(t=>current.get(t.process)!)};
}
