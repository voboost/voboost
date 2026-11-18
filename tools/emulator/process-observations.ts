import { targetInventory } from "./agent-disable.ts";
/** Read the bounded OEM inventory, independently of the Voboost-only process list. */
export function oemTargetPid(body: string, process: string): string {
  if (/^inventoryStatus=/m.test(body)) {
    const pid = targetInventory(body, [process]).pids[process];
    if (!pid) throw new Error("unique current OEM target PID missing or ambiguous");
    return pid;
  }
  const inventories = [...body.matchAll(/^oem-target-processes:\r?\n([\s\S]*?)\r?\nandroid-art-fatal-context:\r?\n/gm)];
  if (inventories.length !== 1) throw new Error("trusted OEM process inventory missing or ambiguous");
  const rows = inventories[0][1].trim().split(/\r?\n/);
  if (!/^\s*PID\s+NAME\s*$/.test(rows[0])) throw new Error("trusted OEM process inventory header malformed");
  const matches: string[] = [];
  for (const line of rows.slice(1).filter((line) => line.trim())) {
    const row = /^\s*([1-9][0-9]*)\s+([A-Za-z0-9_.:-]+)\s*$/.exec(line);
    if (!row) throw new Error("trusted OEM process inventory row malformed");
    if (row[2] === process) matches.push(row[1]);
  }
  if (matches.length !== 1) throw new Error("unique current OEM target PID missing or ambiguous");
  return matches[0];
}
