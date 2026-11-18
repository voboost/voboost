import { isDeepStrictEqual } from "node:util";
import { launcherCache, CacheEvidenceUnavailable, type CacheAuthority } from "./launcher-cache.ts";
import type { AgentDiagnostic } from "./installer-logs.ts";

/** First returned managed bean and first bind, not first deferred factory attempt or compositor. */
export function launcherFirstPublished(rows: AgentDiagnostic[], authority: CacheAuthority, packageName: string) {
  launcherCache(rows, authority, packageName); // Native boot/lifetime, archive cursor, prepared draw authority.
  const current = rows.filter(row => row.agent === "launcher-apps" && row.record.sequence > authority.afterSequence);
  const relevant = current.filter(row => ["icon.first-emitted", "item.first-bind", "icon.first-projection", "package.lifecycle.item"].includes(row.event) && row.fields.package_name === packageName);
  for (const row of relevant) {
    const outer = row.record.fields;
    if (row.record.source.kind !== "agent" || row.record.source.id !== "launcher-apps" || outer.process !== "com.qinggan.app.launcher" || outer.targetPid !== authority.targetPid || outer.targetStarttime !== authority.targetStarttime || outer.generationId !== authority.generationId || outer.catalogSha256 !== authority.catalogSha256 || typeof outer.endpointEpoch !== "string" || !/^[1-9][0-9]*$/.test(outer.endpointEpoch) || !authority.records.some(record => isDeepStrictEqual(record,row.record))) throw new Error("first published authenticated endpoint differs");
    if (!Number.isSafeInteger(row.fields.generation) || Number(row.fields.generation) < 0) throw new Error("first published generation differs");
  }
  const events = relevant.filter(row => row.event === "package.lifecycle.item");
  const surfaces = [0, 1].map(screen => {
    const emitted = relevant.filter(row => row.event === "icon.first-emitted" && row.fields.screen === screen).toSorted((a,b) => a.record.sequence-b.record.sequence)[0];
    if (!emitted) throw new CacheEvidenceUnavailable(`first published marker missing for screen ${screen}`);
    const generation = emitted.fields.generation;
    const first = relevant.filter(row => row.event === "icon.first-emitted" && row.fields.screen === screen);
    if (first.length !== 1 || emitted.outcome !== "emitted" || emitted.fields.icon_ready !== true || Object.keys(emitted.fields).sort().join() !== ["package_name", "screen", "generation", "icon_ready", "at_boot_ms"].sort().join()) throw new Error("first published bean is duplicate or not icon-ready");
    const binds = relevant.filter(row => row.event === "item.first-bind" && row.fields.screen === screen).toSorted((a,b) => a.record.sequence-b.record.sequence);
    const bind = binds[0];
    if (!bind) throw new CacheEvidenceUnavailable(`first bind marker missing for screen ${screen}`);
    if (bind.fields.generation !== generation || binds.filter(row => row.fields.generation === generation).length !== 1 || bind.outcome !== "bound" || Object.keys(bind.fields).sort().join() !== ["package_name", "screen", "generation", "at_boot_ms"].sort().join()) throw new Error("first published bind failed duplicate or generation differs");
    const at = Number(emitted.fields.at_boot_ms), boundAt = Number(bind.fields.at_boot_ms);
    if (![emitted.fields.at_boot_ms,bind.fields.at_boot_ms].every(value => Number.isSafeInteger(value) && Number(value) >= 0) || boundAt < at || bind.record.sequence < emitted.record.sequence) throw new Error("first published monotonic timing differs");
    const draw = current.filter(row => row.event === "icon.prepared-draw" && row.fields.package_name === packageName && row.fields.screen === screen && row.fields.boot_id === authority.boot && row.fields.target_pid === Number(authority.targetPid) && row.fields.process_start_ticks === authority.targetStarttime && row.fields.code_generation === authority.generationId).toSorted((a,b) => a.record.sequence-b.record.sequence)[0];
    if (!draw) throw new CacheEvidenceUnavailable("first published retained preparation missing");
    const drawOuter = draw.record.fields;
    if (draw.record.source.kind !== "agent" || draw.record.source.id !== "launcher-apps" || drawOuter.process !== "com.qinggan.app.launcher" || drawOuter.targetPid !== authority.targetPid || drawOuter.targetStarttime !== authority.targetStarttime || drawOuter.generationId !== authority.generationId || drawOuter.catalogSha256 !== authority.catalogSha256 || drawOuter.endpointEpoch !== emitted.record.fields.endpointEpoch || drawOuter.endpointEpoch !== bind.record.fields.endpointEpoch || !authority.records.some(record => isDeepStrictEqual(record,draw.record))) throw new Error("first published selected draw authenticated endpoint differs");
    if (draw.fields.generation !== generation || Number(draw.fields.prepared_at_boot_ms) > at || Number(draw.fields.draw_started_at_boot_ms) < at) throw new Error("first published prepared draw generation or time differs");
    const beforeEvents = events.filter(row => row.record.sequence < emitted.record.sequence);
    const lifecycle = beforeEvents.length === 1 ? beforeEvents[0] : null;
    const eventAt = lifecycle?.fields.at_boot_ms;
    const latencyAvailable = lifecycle !== null && Number.isSafeInteger(eventAt) && Number(eventAt) >= 0 && Number(eventAt) <= at;
    return { screen, generation, emittedSequence: emitted.record.sequence, bindSequence: bind.record.sequence, drawSequence: draw.record.sequence, preparedAtBootMs: Number(draw.fields.prepared_at_boot_ms), emittedAtBootMs: at, boundAtBootMs: boundAt, lifecycleGeneration: latencyAvailable ? lifecycle!.fields.generation : null, packageEventToEmittedMs: latencyAvailable ? at-Number(eventAt) : null, packageEventToBoundMs: latencyAvailable ? boundAt-Number(eventAt) : null, latencyStatus: latencyAvailable ? "measured" : "unavailable: unique same-package lifecycle event missing", firstDecisions: relevant.filter(row => row.event === "icon.first-projection" && row.fields.screen === screen).map(row => ({generation:row.fields.generation,outcome:row.outcome,sequence:row.record.sequence})) };
  });
  return { packageName, boot:authority.boot, targetPid:authority.targetPid, targetStarttime:authority.targetStarttime, generationId:authority.generationId, catalogSha256:authority.catalogSha256, surfaces, acceptance:"first-returned-bean-and-bind", visualAcceptance:false, immediateAppearanceAcceptance:false, latencySla:null };
}
