import type { Observation } from "./agent-disable.ts";
/** Observe readiness before changing configuration; a running target must actually become active. */
export async function readyBeforeDisable<T extends Observation>(id: string, targets: string[], read: () => Promise<T>, now: () => number = () => performance.now(), timeoutMs = 15000): Promise<T> {
  if (!id || !targets.length || new Set(targets).size !== targets.length || !Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new Error("invalid disable readiness contract");
  const deadline = now() + timeoutMs;
  let observed = await read();
  const boot = observed.boot, live = new Set<string>();
  if (!boot) throw new Error("disable readiness boot missing");
  for (;;) {
    if (observed.boot !== boot) throw new Error("disable readiness crossed boot");
    let pending = false;
    for (const process of targets) {
      const rows = observed.injections.filter(row => row.id === id && row.process === process);
      if (rows.length !== 1 || !["active", "waiting"].includes(rows[0].state)) throw new Error("disable readiness agent selection failed or ambiguous");
      if (observed.inventory.pids[process]) live.add(process);
      else if (!observed.inventory.completeFor(process)) throw new Error("disable readiness inventory incomplete");
      if (live.has(process)) pending ||= !observed.inventory.pids[process] || rows[0].state !== "active";
      else if (rows[0].state !== "waiting") throw new Error("absent disable target had nonwaiting injection");
    }
    if (!pending) return observed;
    if (now() >= deadline) throw new Error("running target did not regain active readiness before disable");
    observed = await read();
  }
}
