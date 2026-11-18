import type { AgentDiagnostic, LogRecord } from "./installer-logs.ts";
import { rootVehicleRecord, type Vehicle, type VehicleAuthority } from "./functional-observations.ts";

const AGENTS = { fev: "vehicle-drive-forced-ev", pw: "vehicle-drive-pedestrian-warning" };
const CLOCK = "android.elapsedRealtime";
export type TimingFence = VehicleAuthority & { targetStarttime: string; endpointEpoch: string };
export type VehicleTiming = {
  clockDomain: typeof CLOCK;
  bootId: string;
  bootToMatchedObservationMs: number;
  ownerVerifiedAtBootMs: number;
  classification: "already-matched-baseline" | "observed-nonmatch-to-match" | "match-observed-baseline-missing";
  semantics: string[];
  processStartTicks: string;
  processStartToMatchMs: null;
  settingsOrder: "unverified" | "vehicle-observed-before-settings" | "settings-observed-before-or-with-vehicle";
  causality: "unverified";
};
/** Uses only typed actual-read diagnostics after an independently boot-bound root anchor.
 * The owner success fence is required separately. Neither timestamps nor transitions prove causality.
 */
export function vehicleTiming(records: LogRecord[], diagnostics: AgentDiagnostic[], vehicle: Vehicle,
  fence: TimingFence, requiredSemantics: readonly string[], afterSequence = -1,
  settings?: { bootId: string; clockDomain: string; observedAtBootMs: number }): VehicleTiming {
  if (!fence.boot || !/^[1-9][0-9]*$/.test(fence.targetStarttime) || !/^[1-9][0-9]*$/.test(fence.endpointEpoch) ||
    requiredSemantics.length === 0 || new Set(requiredSemantics).size !== requiredSemantics.length ||
    requiredSemantics.some(s => !(vehicle === "pw" ? ["disabled"] : ["forced-ev", "drive-electric"]).includes(s)))
    throw new Error("vehicle timing authority or required semantics incomplete");
  const identity = (f: Record<string, unknown>) => ["process", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256"]
    .every(key => typeof f[key] === "string" && f[key] === fence[key as keyof TimingFence]);
  const anchors = records.filter(r => r.message === "vehicle-state-observed").filter(r => {
    const { fields } = rootVehicleRecord(r);
    return fields.agentId === AGENTS[vehicle] && fields.bootId === fence.boot && identity(fields);
  });
  // The existing closed root-operation parser supplies the boot anchor, never the ARMED value as actual match.
  const anchor = anchors.sort((a, b) => a.sequence - b.sequence)[0];
  if (!anchor) throw new Error("current boot vehicle lifetime anchor missing");
  const rows = diagnostics.filter(r => r.agent === AGENTS[vehicle] && identity(r.record.fields) &&
    r.record.sequence > Math.max(anchor.sequence, afterSequence)).sort((a,b) => a.record.sequence-b.record.sequence);
  const relevant = rows.filter(r => r.event === "vehicle.state" || vehicle === "fev" && r.event === "restore" && r.outcome === "verified" || vehicle === "pw" && r.event === "restore.complete" && r.outcome === "confirmed");
  let last = -1;
  for (const row of relevant) {
    if (row.record.fields.bootId !== undefined && row.record.fields.bootId !== fence.boot) throw new Error("foreign boot timing diagnostic");
    const f = row.fields, n = f.observedAtBootMs;
    if (f.clockDomain !== CLOCK || !Number.isSafeInteger(n) || Number(n) < 0) throw new Error("actual vehicle boot clock unavailable");
    if (Number(n) < last) throw new Error("vehicle event boot clock regressed");
    last = Number(n);
  }
  const state = new Map<string, AgentDiagnostic>();
  const initial = new Map<string, AgentDiagnostic>();
  let matched: number | undefined;
  let verified: number | undefined;
  for (const row of relevant) {
    const f = row.fields;
    if (row.event === "vehicle.state") {
      if (!requiredSemantics.includes(String(f.semantic))) continue;
      if (typeof f.baseline !== "boolean" || typeof f.stateMatched !== "boolean" ||
        !["returned", "unavailable", "null", "exception"].includes(String(f.readStatus)) ||
        !["MATCH", "NO_MATCH", "UNKNOWN"].includes(row.outcome) || f.stateMatched !== (row.outcome === "MATCH") ||
        f.stateMatched && f.readStatus !== "returned" || f.readStatus !== "returned" && row.outcome !== "UNKNOWN") throw new Error("vehicle actual-read semantic shape differs");
      const semantic = String(f.semantic);
      if (!initial.has(semantic)) initial.set(semantic, row);
      state.set(semantic, row);
      if (!f.stateMatched) matched = undefined;
      if (requiredSemantics.every(s => state.get(s)?.fields.stateMatched === true) && matched === undefined)
        matched = Math.max(...requiredSemantics.map(s => Number(state.get(s)!.fields.observedAtBootMs)));
    } else if (f.stateMatched === true && matched !== undefined && Number(f.observedAtBootMs) >= matched &&
      requiredSemantics.every(s => state.get(s)?.fields.stateMatched === true)) {
      verified = Number(f.observedAtBootMs); break;
    }
  }
  if (matched === undefined || verified === undefined) throw new Error("actual semantic match and owner verification missing");
  const baselines = requiredSemantics.map(s => initial.get(s)!);
  const classification = baselines.every(r => r.fields.baseline === true && r.fields.stateMatched === true)
    ? "already-matched-baseline" : baselines.some(r => r.outcome === "NO_MATCH")
      ? "observed-nonmatch-to-match" : "match-observed-baseline-missing";
  const sameSettingsClock = settings?.bootId === fence.boot && settings?.clockDomain === CLOCK &&
    Number.isSafeInteger(settings.observedAtBootMs) && settings.observedAtBootMs >= 0;
  return { clockDomain: CLOCK, bootId: fence.boot, bootToMatchedObservationMs: matched,
    ownerVerifiedAtBootMs: verified, classification, semantics: [...requiredSemantics],
    processStartTicks: fence.targetStarttime, processStartToMatchMs: null,
    settingsOrder: !sameSettingsClock ? "unverified" : matched < settings!.observedAtBootMs
      ? "vehicle-observed-before-settings" : "settings-observed-before-or-with-vehicle", causality: "unverified" };
}

/** Required reads follow the selected runtime contract, not a guessed vehicle profile. */
export function vehicleTimingSemantics(rows: AgentDiagnostic[], vehicle: Vehicle, fence: TimingFence): string[] {
  if (vehicle === "pw") return ["disabled"];
  const ids = new Set(rows.filter(r => r.agent === AGENTS.fev && r.event === "vehicle.state" &&
    ["process", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256"]
      .every(key => r.record.fields[key] === fence[key as keyof TimingFence])).map(r => r.fields.contract));
  if (ids.size !== 1) throw new Error("unique selected FEV timing contract missing");
  const id = [...ids][0];
  if (id === "qinggan-canbus") return ["forced-ev"];
  if (id === "dreamer-h56-force-v2") return ["forced-ev", "drive-electric"];
  throw new Error("unsupported FEV timing contract");
}
