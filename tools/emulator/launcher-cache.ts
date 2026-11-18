import { isDeepStrictEqual } from "node:util";
import { launcherLifetime } from "./launcher-lifetime.ts";
import { sequence } from "./functional-observations.ts";
import type { AgentDiagnostic, LogRecord } from "./installer-logs.ts";

export class CacheEvidenceUnavailable extends Error {}
export type CacheAuthority = { publicationGeneration?: number; boot: string; generationId: string; catalogSha256: string; targetPid: string; targetStarttime: string; records: LogRecord[]; afterSequence: number; baselineRecord?: LogRecord; beforeWitness?: { rows: AgentDiagnostic[]; authority: CacheAuthority }; nativeLifetime?: { beforeBody: unknown; observed: { boot: string; body: string }; afterBody: unknown } };
export type CachePair = { screen: number; cache_key: string; source_identity: string; style_identity: string; boot: string; generationId: string; catalogSha256: string };
const hex = (value: unknown) => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const decimal = (value: unknown) => typeof value === "string" && /^[1-9][0-9]*$/.test(value);
const fields = ["boot_id", "target_pid", "process_start_ticks", "code_generation", "package_name", "generation", "screen", "cache_key", "source_identity", "style_identity", "elapsed_us"];

/** Admission uses a complete current-lifetime disk publication and its first actual draw per screen. */
export function launcherCache(rows: AgentDiagnostic[], authority: CacheAuthority, packageName: string, previous?: CachePair[]): CachePair[] {
  const last = sequence(authority.records);
  // A reboot may rotate away the pre-boot cursor. Retain and revalidate the
  // independent native-bracketed before witness, rather than requiring that
  // old record to be exported again by the new boot.
  let crossedBoot = false;
  if (authority.beforeWitness) {
    if (!previous || authority.beforeWitness.authority.beforeWitness) throw new Error("launcher cache before witness is unexpected or recursive");
    const before = authority.beforeWitness;
    const verified = launcherCache(before.rows, before.authority, packageName);
    if (JSON.stringify(verified) !== JSON.stringify(previous) || before.authority.boot === authority.boot || authority.afterSequence !== sequence(before.authority.records) || !authority.baselineRecord || !isDeepStrictEqual(authority.baselineRecord,before.authority.records.at(-1))) throw new Error("launcher cache before witness differs from retained baseline or pairs");
    crossedBoot = true;
  }
  if (previous && !crossedBoot) throw new CacheEvidenceUnavailable("launcher cache authenticated before witness missing");
  if (!Number.isSafeInteger(authority.afterSequence) || authority.afterSequence < -1 || (!crossedBoot && last < authority.afterSequence) || (!crossedBoot && authority.baselineRecord && (authority.baselineRecord.sequence !== authority.afterSequence || !authority.records.some(row => isDeepStrictEqual(row,authority.baselineRecord))))) throw new CacheEvidenceUnavailable("launcher cache canonical baseline continuity unavailable");
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(authority.boot) || !hex(authority.generationId) || !hex(authority.catalogSha256) || !decimal(authority.targetPid) || !decimal(authority.targetStarttime) || !/^[A-Za-z][A-Za-z0-9_.]+$/.test(packageName)) throw new Error("launcher cache authority differs");
  if (previous && (previous.length !== 2 || new Set(previous.map(row => row.screen)).size !== 2 || previous.some(row => row.boot === authority.boot || row.generationId !== authority.generationId || row.catalogSha256 !== authority.catalogSha256))) throw new Error("launcher cache reboot or signed identity differs");
  if (!authority.nativeLifetime) throw new CacheEvidenceUnavailable("current independent launcher lifetime proof missing");
  const native = launcherLifetime(authority.nativeLifetime.beforeBody, authority.nativeLifetime.observed, authority.nativeLifetime.afterBody);
  if (["boot", "generationId", "catalogSha256", "targetPid", "targetStarttime"].some(key => native[key as keyof typeof native] !== authority[key as keyof CacheAuthority])) throw new Error("launcher native authority differs from cache authority");
  const relevant = rows.filter(row => row.agent === "launcher-apps" && ["icon.ready", "icon.draw", "icon.prepared-draw"].includes(row.event) && row.fields.package_name === packageName && (crossedBoot || row.record.sequence > authority.afterSequence));
  const result: CachePair[] = [];
  const admitted: { ready: AgentDiagnostic; draw: AgentDiagnostic }[] = [];
  for (const screen of [0, 1]) {
    const current = relevant.filter(row => row.fields.screen === screen && row.fields.boot_id === authority.boot && row.fields.target_pid === Number(authority.targetPid) && row.fields.process_start_ticks === authority.targetStarttime && row.fields.code_generation === authority.generationId && (authority.publicationGeneration === undefined || row.fields.generation === authority.publicationGeneration)).toSorted((a, b) => a.record.sequence - b.record.sequence);
    for (const row of current) {
      // Outer canonical fields are authored by daemon route_log from its tracker
      // endpoint, never by the agent-controlled diagnostic JSON message.
      if ((row.record.fields.process !== "com.qinggan.app.launcher" || row.record.fields.targetPid !== authority.targetPid || row.record.fields.targetStarttime !== authority.targetStarttime || row.record.fields.generationId !== authority.generationId || row.record.fields.catalogSha256 !== authority.catalogSha256 || !decimal(row.record.fields.endpointEpoch))) throw new Error("launcher cache authenticated endpoint envelope differs");
      const embedded = row.event === "icon.prepared-draw";
      const expected = embedded ? [...fields.filter(key => key !== "elapsed_us"), "cache_source", "prepared_at_boot_ms", "draw_started_at_boot_ms"] : row.event === "icon.draw" ? [...fields, "cache_source"] : fields;
      if (embedded && (![row.fields.prepared_at_boot_ms,row.fields.draw_started_at_boot_ms].every(value => Number.isSafeInteger(value) && Number(value) >= 0) || Number(row.fields.prepared_at_boot_ms) > Number(row.fields.draw_started_at_boot_ms))) throw new Error("launcher retained preparation timing differs");
      if (Object.keys(row.fields).sort().join() !== expected.sort().join() || row.record.source.kind !== "agent" || row.record.source.id !== "launcher-apps" || !authority.records.some(record => record.sequence === row.record.sequence && isDeepStrictEqual(record,row.record)) || ![row.fields.cache_key, row.fields.source_identity, row.fields.style_identity].every(hex) || !Number.isSafeInteger(row.fields.generation) || Number(row.fields.generation) < 1 || (!embedded && (!Number.isSafeInteger(row.fields.elapsed_us) || Number(row.fields.elapsed_us) < 0)) || (row.event === "icon.ready" ? !["render", "memory", "disk"].includes(row.outcome) : row.outcome !== "first-draw" || !["render", "memory", "disk"].includes(String(row.fields.cache_source)))) throw new Error("launcher cache canonical receipt differs");
    }
    const draw = current.find(row => ["icon.draw", "icon.prepared-draw"].includes(row.event));
    if (!draw) throw new CacheEvidenceUnavailable(`launcher cache first draw missing for screen ${screen}`);
    // Prefer retained canonical preparation even for embedded first draws. The
    // embedded receipt supplies a fallback when early preparation rotated out;
    // its later draw sequence cannot stand in for the original load ordering.
    const published = current.findLast(row => row.event === "icon.ready" && row.record.sequence < draw.record.sequence && ["generation", "cache_key", "source_identity", "style_identity"].every(key => row.fields[key] === draw.fields[key]));
    const ready = published ?? (draw.event === "icon.prepared-draw" ? {...draw, outcome: String(draw.fields.cache_source)} : undefined);
    if (!ready) throw new CacheEvidenceUnavailable(`launcher cache publication before first draw missing for screen ${screen}`);
    if (ready.outcome !== draw.fields.cache_source) throw new Error("launcher cache draw source differs from publication");
    const pair = {screen, cache_key: String(ready.fields.cache_key), source_identity: String(ready.fields.source_identity), style_identity: String(ready.fields.style_identity), boot: authority.boot, generationId: authority.generationId, catalogSha256: authority.catalogSha256};
    if (previous) {
      const old = previous.find(row => row.screen === screen);
      if (!old || ["cache_key", "source_identity", "style_identity"].some(key => old[key as keyof CachePair] !== pair[key as keyof CachePair])) throw new Error("launcher cache source style or key changed across reboot");

    }
    admitted.push({ready,draw});
    result.push(pair);
  }
  if (previous) for (const {ready,draw} of admitted) {
    // A shared color variant is loaded from disk once, then reused by the other
    // screen from process memory. Require the earlier authenticated disk ready,
    // not a later successful draw or an unrelated key/publication.
    const diskOrigin = ready.outcome === "memory" && relevant.some(row =>
      row.event === "icon.ready" && row.outcome === "disk" &&
      row.record.sequence < ready.record.sequence &&
      ["boot_id","target_pid","process_start_ticks","code_generation","package_name","generation","cache_key","source_identity","style_identity"].every(key=>row.fields[key]===ready.fields[key]) &&
      admitted.some(peer => peer.ready.record.sequence === row.record.sequence && isDeepStrictEqual(peer.ready.record,row.record)));
    if (ready.outcome !== "disk" && !diskOrigin) throw new Error(`launcher cache screen ${draw.fields.screen} rendered or used memory instead of retained disk pixels`);
  }
  return result;
}

/** Select the first new publication, never a later publication chosen for matching pixels. */
export function launcherThemePublication(rows: AgentDiagnostic[], authority: CacheAuthority, packageName: string, previousGeneration: number): number {
  if (!Number.isSafeInteger(previousGeneration) || previousGeneration < 1) throw new CacheEvidenceUnavailable("theme previous publication missing");
  const ready = rows.filter(row => row.agent === "launcher-apps" && row.event === "icon.ready" && row.fields.package_name === packageName && row.record.sequence > authority.afterSequence && row.fields.boot_id === authority.boot && row.fields.target_pid === Number(authority.targetPid) && row.fields.process_start_ticks === authority.targetStarttime && row.fields.code_generation === authority.generationId && Number(row.fields.generation) > previousGeneration).toSorted((a,b)=>a.record.sequence-b.record.sequence)[0];
  if (!ready) throw new CacheEvidenceUnavailable("theme new publication missing");
  return Number(ready.fields.generation);
}

export type SharedColorWitness = { rows: AgentDiagnostic[]; authority: CacheAuthority };
/** Equal keys prove a shared pixel namespace, not a global absence of rendering.
 * The caller must independently establish the Speedy Dash/color UI phase: opaque
 * style hashes cannot prove a theme name. Cold admission additionally revalidates
 * both native-bracketed lifetimes and the accepted first disk draw on each screen.
 */
export function launcherSharedColorCache(current: SharedColorWitness, packageName: string, previous?: SharedColorWitness) {
  const before = previous ? launcherSharedColorCache(previous, packageName) : undefined;
  if (previous && (!current.authority.beforeWitness || JSON.stringify(current.authority.beforeWitness) !== JSON.stringify(previous))) throw new Error("shared color retained witness differs");
  const pairs = launcherCache(current.rows, current.authority, packageName, before?.pairs);
  const [left, right] = pairs;
  for (const key of ["cache_key", "source_identity", "style_identity", "boot", "generationId", "catalogSha256"] as const)
    if (left[key] !== right[key]) throw new Error(`shared color ${key} differs across screens`);
  const draws = pairs.map(pair => current.rows.filter(row => row.agent === "launcher-apps" && ["icon.draw", "icon.prepared-draw"].includes(row.event) && row.fields.package_name === packageName && (current.authority.beforeWitness || row.record.sequence > current.authority.afterSequence) && row.fields.screen === pair.screen && row.fields.boot_id === current.authority.boot && row.fields.target_pid === Number(current.authority.targetPid) && row.fields.process_start_ticks === current.authority.targetStarttime && row.fields.code_generation === current.authority.generationId && (current.authority.publicationGeneration === undefined || row.fields.generation === current.authority.publicationGeneration)).toSorted((a,b)=>a.record.sequence-b.record.sequence)[0]);
  if (draws[0].fields.generation !== draws[1].fields.generation) throw new Error("shared color catalog publication differs across screens");
  return {pairs, sources: draws.map(row => String(row.fields.cache_source)), claim: previous ? "shared-color-first-draw-disk-reuse" as const : "shared-color-namespace" as const};
}

export type CacheWitness = { rows: AgentDiagnostic[]; authority: CacheAuthority };

/** Join real overlapping same-boot exports without renumbering or bridging a gap. */
export function joinCacheWitness(before: CacheWitness, current: CacheWitness): CacheWitness {
  for (const witness of [before,current]) {
    if (!witness.authority.nativeLifetime) throw new CacheEvidenceUnavailable("launcher archive native lifetime missing");
    const native=launcherLifetime(witness.authority.nativeLifetime.beforeBody,witness.authority.nativeLifetime.observed,witness.authority.nativeLifetime.afterBody);
    for (const key of ["boot","generationId","catalogSha256","targetPid","targetStarttime"] as const)
      if (native[key]!==witness.authority[key]) throw new Error("launcher archive native authority differs");
  }
  for (const key of ["boot","generationId","catalogSha256","targetPid","targetStarttime"] as const)
    if (before.authority[key]!==current.authority[key]) throw new CacheEvidenceUnavailable("launcher archive crossed boot or target lifetime");
  const records=new Map(before.authority.records.map(row=>[row.sequence,row]));
  let overlap=false;
  for (const row of current.authority.records) {
    const old=records.get(row.sequence);
    if (old) {
      if (!isDeepStrictEqual(old,row)) throw new Error("launcher overlapping canonical record differs");
      overlap=true;
    }
    records.set(row.sequence,row);
  }
  if (!overlap || sequence(current.authority.records)<sequence(before.authority.records)) throw new CacheEvidenceUnavailable("launcher canonical archives lack monotonic overlap");
  const rows=new Map(before.rows.map(row=>[row.record.sequence,row]));
  for (const row of current.rows) {
    const old=rows.get(row.record.sequence);
    if (old && !isDeepStrictEqual(old,row)) throw new Error("launcher overlapping diagnostic differs");
    rows.set(row.record.sequence,row);
  }
  return { rows:[...rows.values()].toSorted((a,b)=>a.record.sequence-b.record.sequence),authority:{...current.authority,records:[...records.values()].toSorted((a,b)=>a.sequence-b.sequence),afterSequence:before.authority.afterSequence,baselineRecord:before.authority.baselineRecord} };
}
