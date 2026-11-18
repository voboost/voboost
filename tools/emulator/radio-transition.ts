/** Consume only already identity-checked VOBDBG1 diagnostics from the packaged CLI archive.
 * Caller supplies the exact new trace id scoped to a single root event/process/boot.
 * Measurement is observational; the 2s producer bound is not a UI latency SLA.
 */
export function radioTransition(rows: any[], id: number, authority: { targetPid: string; documentGeneration: string; generationId: string; catalogSha256: string }) {
  const selected = rows.filter(r => r.agent === "settings" && r.fields?.id === id);
  const roots = selected.filter(r => r.event === "radio.authority" && r.outcome === "observed");
  if (roots.length !== 1 || roots[0].fields.pid !== authority.targetPid ||
      roots[0].fields.documentGeneration !== authority.documentGeneration ||
      roots[0].fields.generationId !== authority.generationId ||
      roots[0].fields.catalogSha256 !== authority.catalogSha256)
    return { outcome: "unavailable", reason: "independent current root document differs" };
  const trace = selected.filter(r => r.event === "radio.trace");
  const frames = selected.filter(r => r.event === "radio.frame").map(r => r.fields);
  const unavailable = (reason: string) => ({ outcome: "unavailable", reason });
  if (trace.length !== 1 || !["bounded-end", "drawn-end"].includes(trace[0].outcome) || frames.length < 3)
    return unavailable("bounded draw trace incomplete");
  const end = trace[0].fields;
  if (!end.attached || !Number.isFinite(end.captionMs) || end.captionMs < 0)
    return unavailable("caption draw or original row unavailable");
  if (frames.some(f => f.duration !== 400)) return { outcome: "failed", reason: "OEM 400ms baseline duration differs" };
  let previous = -1, frame = 0;
  for (const f of frames) {
    if (!Number.isSafeInteger(f.frame) || f.frame !== ++frame) return unavailable("draw sequence incomplete");
    if (f.group !== end.group || f.key !== end.key || f.value !== end.value ||
        !Number.isFinite(f.ms) || f.ms < previous || !Number.isFinite(f.position) ||
        !Number.isFinite(f.initial) || !Number.isFinite(f.target) ||
        !Number.isFinite(f.duration) || f.duration <= 0)
      return unavailable("draw identity/clock/OEM capability differs");
    previous = f.ms;
  }
  const visible = frames.filter(f => f.visible === true);
  if (visible.length < 3) return unavailable("visible window frames missing");
  const first = visible[0], last = visible.at(-1);
  const lo = Math.min(first.initial, last.target), hi = Math.max(first.initial, last.target);
  if (hi - lo < 1) return unavailable("radio did not require movement");
  if (!visible.some(f => f.position > lo + .01 && f.position < hi - .01))
    return { outcome: "failed", reason: "no intermediate slider position was drawn" };
  if (Math.abs(last.position - last.target) > .01 || !last.caption)
    return { outcome: "failed", reason: "final slider/caption was not drawn" };
  return { outcome: "measured", group: end.group, frames: visible.length,
    oemDurationMs: last.duration, captionAppliedMs: end.captionMs,
    captionFirstDrawMs: visible.find(f => f.caption)?.ms, requestMs: end.requestMs,
    language: end.language };
}
