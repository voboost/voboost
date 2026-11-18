import { createHash } from "node:crypto";
export type Mode = "restart" | "in-place";
export const CLASSES = {
  "font-cyrillic": "ru.voboost.agents.font.cyrillic.FontCyrillicAgent",
  "keyboard-ru": "ru.voboost.agents.keyboard.ru.KeyboardRuAgent",
  "keyboard-en": "ru.voboost.agents.keyboard.en.KeyboardEnAgent",
  "launcher-apps": "ru.voboost.agents.launcher.apps.LauncherAppsAgent",
  "vehicle-drive-forced-ev": "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvAgent",
  "vehicle-drive-pedestrian-warning": "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningAgent",
} as const;
const java = (text: string) => text.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, "").trim();
const hash = (text: string) => createHash("sha256").update(text).digest("hex");
export function admitStopModes(catalog: any[], source: Record<string, string>) {
  const agents = catalog.filter(a => a.activation?.type === "checkbox");
  if (agents.length !== 6 || new Set(agents.map(a => a.id)).size !== 6 || agents.some(a => !Object.hasOwn(CLASSES,a.id))) throw new Error("beta1 checkbox catalog differs");
  if (!/default\s+AgentStopMode\s+stopMode\s*\(\s*\)\s*\{\s*return\s+AgentStopMode\.RESTART_PROCESS\s*;\s*\}/.test(java(source.Agent ?? ""))) throw new Error("owning Agent default stop policy differs");
  if (!/public\s+interface\s+AgentAsync\s+extends\s+Agent\s*\{/.test(java(source.AgentAsync ?? "")) || /\bstopMode\s*\(/.test(java(source.AgentAsync ?? ""))) throw new Error("owning AgentAsync inheritance differs");
  if (!/public\s+interface\s+AgentCacheQuiescence\s*\{/.test(java(source.AgentCacheQuiescence ?? "")) || /\bstopMode\s*\(/.test(java(source.AgentCacheQuiescence ?? ""))) throw new Error("owning cache-only interface stop policy differs");
  const observer = java(source.AgentInputObserver ?? "");
  if (!/public\s+abstract\s+class\s+AgentInputObserver\s+implements\s+AgentAsync\s*\{/.test(observer) || !/public\s+final\s+AgentStopMode\s+stopMode\s*\(\s*\)\s*\{\s*return\s+AgentStopMode\.IN_PLACE\s*;\s*\}/.test(observer) || [...observer.matchAll(/\bstopMode\s*\(/g)].length !== 1) throw new Error("owning input observer stop policy differs");
  const modes: Record<string, Mode> = {}, hashes: Record<string,string> = {Agent:hash(source.Agent),AgentAsync:hash(source.AgentAsync),AgentCacheQuiescence:hash(source.AgentCacheQuiescence),AgentInputObserver:hash(source.AgentInputObserver)};
  for (const agent of agents) {
    const entry = CLASSES[agent.id as keyof typeof CLASSES], text = java(source[entry] ?? ""), name = entry.split(".").at(-1)!;
    const vehicle = agent.id.startsWith("vehicle-drive-");
    const declaration = vehicle ? "extends\\s+AgentInputObserver" : "implements\\s+Agent(?:Async)?(?:, AgentCacheQuiescence)?";
    if (agent.targets?.some((t: any) => t.entryClass !== entry) || !agent.targets?.length || !new RegExp("public\\s+final\\s+class\\s+"+name+"\\s+"+declaration+"\\s*\\{").test(text)) throw new Error("owning checkbox entry class differs");
    if (/\bstopMode\s*\(/.test(text)) throw new Error("unrecognized owning stop policy");
    const override = vehicle;
    modes[agent.id] = override ? "in-place" : "restart"; hashes[entry] = hash(source[entry]);
  }
  return {modes,hashes};
}
export type Inventory = { pids: Record<string,string>; completeFor: (process: string) => boolean };
export class InventoryUnavailable extends Error {}
/** Only a successful bounded complete ps producer can prove a requested target absent. */
export function targetInventory(body: string, targets: string[]): Inventory {
  const matches = [...body.matchAll(/^oem-target-processes:\r?\n([\s\S]*?)\r?\n(?:current-process-identities|android-art-fatal-context):\r?\n/gm)];
  if (matches.length !== 1) throw new InventoryUnavailable("independent OEM inventory missing or ambiguous");
  const text = matches[0][1];
  if (Buffer.byteLength(text) > 131200 || !text.startsWith("inventoryStatus=complete\n") || !text.endsWith("\ninventoryEnd=complete\n") && !text.endsWith("\ninventoryEnd=complete")) throw new InventoryUnavailable("complete OEM inventory marker missing or bounded result unavailable");
  const lines = text.replace(/^inventoryStatus=complete\r?\n/,"").replace(/\r?\ninventoryEnd=complete\r?\n?$/,"").split(/\r?\n/);
  if (!/^\s*PID\s+NAME\s*$/.test(lines.shift() ?? "")) throw new Error("independent OEM inventory header differs");
  const requested = new Set(targets), pids: Record<string,string> = {};
  if (!requested.size || requested.size !== targets.length || targets.some(t => !/^[A-Za-z0-9_.:-]+$/.test(t))) throw new Error("invalid requested OEM targets");
  for (const line of lines) {
    const row = /^\s*([1-9][0-9]*)\s+([^\x00-\x1f\x7f]{1,4096})$/.exec(line);
    if (!row) throw new Error("independent OEM inventory malformed");
    const name = row[2].trimEnd();
    if (!requested.has(name)) continue; // Native/kernel names never become package identities.
    if (Object.hasOwn(pids,name)) throw new Error("requested OEM target has ambiguous processes");
    pids[name] = row[1];
  }
  return {pids,completeFor:(process) => requested.has(process)};
}
export type Observation = { boot: string; inventory: Inventory; injections: { id: string; process: string; state: string }[] };
export type Result = { process: string; status: "passed" | "unavailable"; reason: string };
export function removedTargets(id: string, targets: string[], mode: Mode, before: Observation, after: Observation): Result[] {
  if (!id || !targets.length || new Set(targets).size !== targets.length || !["restart","in-place"].includes(mode)) throw new Error("invalid removal contract");
  if (!before.boot || before.boot !== after.boot) throw new Error("removal observation crossed boot");
  return targets.map(process => {
    const oldRows = before.injections.filter(row => row.id === id && row.process === process), rows = after.injections.filter(row => row.id === id && row.process === process);
    if (oldRows.length !== 1 || rows.length) throw new Error("disabled agent selection differs");
    const old = before.inventory.pids[process], fresh = after.inventory.pids[process];
    if (old === undefined) {
      if (!before.inventory.completeFor(process)) return {process,status:"unavailable",reason:"partial OEM inventory cannot prove dormant target absence"};
      if (oldRows[0].state !== "waiting") throw new Error("absent OEM target had nonwaiting injection");
      if (fresh !== undefined) return {process,status:"unavailable",reason:"dormant target appeared during disable; live teardown not exercised"};
      if (!after.inventory.completeFor(process)) return {process,status:"unavailable",reason:"current partial inventory cannot prove dormant target absence"};
      return {process,status:"passed",reason:"not applicable: independent OEM absence and waiting row prove no injection existed to undo"};
    }
    if (oldRows[0].state !== "active") return {process,status:"unavailable",reason:"running target lacked active agent before disable"};
    if (fresh === undefined) return {process,status:"unavailable",reason:"target exited but independently confirmed replacement has not returned"};
    if (mode === "restart") {
      if (old === fresh) throw new Error("restart-required agent retained OEM target PID");
      return {process,status:"passed",reason:"same boot independent OEM PID changed and disabled agent omitted"};
    }
    if (old !== fresh) throw new Error("in-place stop unexpectedly restarted OEM target");
    return {process,status:"passed",reason:"same independent OEM PID and omitted agent; feature cancellation separately required by case25"};
  });
}
export function reenabledTargets(id: string, targets: string[], observed: Observation): Result[] {
  return targets.map(process => {
    const rows = observed.injections.filter(row => row.id === id && row.process === process);
    if (rows.length !== 1 || rows[0].state === "failed") throw new Error("re-enabled agent selection failed or ambiguous");
    if (!observed.inventory.pids[process]) {
      if (!observed.inventory.completeFor(process)) return {process,status:"unavailable",reason:"partial inventory cannot prove dormant target absence"};
      if (rows[0].state !== "waiting") throw new Error("absent re-enabled target is not waiting");
      return {process,status:"passed",reason:"not applicable: independently absent target waits without an injection"};
    }
    if (rows[0].state !== "active") return {process,status:"unavailable",reason:"running re-enabled target has not regained active readiness"};
    return {process,status:"passed",reason:"independently running OEM target regained active injection"};
  });
}
