import { createHash } from "node:crypto";
import { lstatSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

export type Language = "en" | "ru" | "zh";
export type LayoutTarget = { role: string; value: string; class: string };
export type LayoutTopology = { sections: string[]; sectionBodies: Array<{ key: string; index: number }>; checkboxSwitches: Array<{ key: string; section: string }>; panelTargets: LayoutTarget[]; dialogTargets: LayoutTarget[] };
export type CopyLanguage = {
  layoutTopology: LayoutTopology;
  commonTitle: string;
  startupTitle: string;
  interfaceGroup: string;
  vehicleGroup: string;
  unstableGroup: string;
  startup: Array<{ value: "interface" | "hidden" | "none"; label: string; description: string }>;
  checkboxes: Array<{ id: string; title: string; description: string }>;
  uninstall: Record<"group" | "buttonText" | "description" | "awaitingConfirmation" | "started" | "retryableError" | "confirmLabel" | "cancelLabel", string>;
  errors: { mutationFailure: string; unknownApplicabilityWarning: string };
};
export type SettingsCopy = { languages: Record<Language, CopyLanguage>; geometry: Record<string, number>; sha256: string; path: string };
const sha = (bytes: Buffer | string) => createHash("sha256").update(bytes).digest("hex");

function sources(root: string): Record<string, string> {
  const result: Record<string, string> = {};
  const visit = (relative: string) => {
    for (const entry of readdirSync(join(root, relative), { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const path = `${relative}/${entry.name}`;
      if (entry.isSymbolicLink()) throw new Error("source copy authority contains a symlink");
      if (entry.isDirectory()) visit(path);
      else if (/\.(java|json)$/.test(path)) result[path] = sha(readFileSync(join(root, path)));
    }
  };
  visit("src/main/java");
  return result;
}

/** Read owning-build output and bind it to real source inputs and delivered signed metadata/DEX. */
export function settingsCopy(root: string, closure: string): SettingsCopy {
  const path = join(root, "build/evidence/settings-copy.json");
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 2 * 1024 * 1024) throw new Error("invalid bounded settings copy artifact");
  const bytes = readFileSync(path), value = JSON.parse(bytes.toString("utf8"));
  if (value.schemaVersion !== 4 || value.authority !== "SettingsMenuPolicy, SettingsPanelSpec and UninstallCopy production APIs" || !value.sourceFilesSha256 || !value.dexSha256)
    throw new Error("settings copy producer contract differs");
  const actual = sources(root), expected = value.sourceFilesSha256;
  if (JSON.stringify(Object.keys(actual).sort()) !== JSON.stringify(Object.keys(expected).sort()) || Object.entries(actual).some(([key, hash]) => expected[key] !== hash))
    throw new Error("settings copy production source commitments differ");
  if (value.productionSourceSha256 !== sha(Object.keys(actual).sort().map((path) => `${path}\0${actual[path]}\n`).join(""))) throw new Error("settings copy production source digest differs");
  if (value.agentsManifestSha256 !== sha(readFileSync(join(closure, "agents.json")))) throw new Error("settings copy signed metadata differs from closure");
  const dex = readdirSync(closure).filter((name) => /^agent-.*\.dex$/.test(name)).sort();
  if (!dex.length || JSON.stringify(dex) !== JSON.stringify(Object.keys(value.dexSha256).sort()) || dex.some((name) => value.dexSha256[name] !== sha(readFileSync(join(closure, name)))))
    throw new Error("settings copy delivered DEX differs");
  const catalog = JSON.parse(readFileSync(join(closure, "agents.json"), "utf8")).agents;
  for (const language of ["en", "ru", "zh"] as Language[]) {
    const copy = value.languages?.[language] as CopyLanguage;
    if (!copy || ["language", "languageTitle", "languageDescription"].some(key => Object.hasOwn(copy, key)) || !Array.isArray(copy.startup) || JSON.stringify(copy.startup.map((row) => row.value)) !== JSON.stringify(["interface", "hidden", "none"]))
      throw new Error("settings copy locale/control topology differs");
    const topology = copy.layoutTopology;
    if (!topology || !Array.isArray(topology.sections) || !topology.sections.length || new Set(topology.sections).size !== topology.sections.length || topology.sections.some((key) => typeof key !== "string" || !key)) throw new Error("settings copy section topology missing");
    if (!Array.isArray(topology.sectionBodies) || topology.sectionBodies.some((row) => JSON.stringify(Object.keys(row).sort()) !== JSON.stringify(["index", "key"])) || JSON.stringify(topology.sectionBodies.map((row) => ({ key: row.key, index: row.index }))) !== JSON.stringify(topology.sections.map((key, index) => ({ key, index })))) throw new Error("settings copy body topology differs");
    if (!Array.isArray(topology.checkboxSwitches) || new Set(topology.checkboxSwitches.map((row) => row.key)).size !== topology.checkboxSwitches.length || topology.checkboxSwitches.some((row) => JSON.stringify(Object.keys(row).sort()) !== JSON.stringify(["key", "section"]) || typeof row.key !== "string" || !row.key || !topology.sections.includes(row.section))) throw new Error("settings copy checkbox switch topology malformed");
    for (const targets of [topology.panelTargets, topology.dialogTargets]) {
      if (!Array.isArray(targets) || !targets.length || new Set(targets.map((row) => `${row.role}:${row.value}`)).size !== targets.length || targets.some((row) => JSON.stringify(Object.keys(row).sort()) !== JSON.stringify(["class", "role", "value"]) || Object.values(row).some((value) => typeof value !== "string" || !value))) throw new Error("settings copy layout topology malformed");
    }
    if (JSON.stringify(topology.dialogTargets.map((row) => `${row.role}:${row.value}`).sort()) !== JSON.stringify(["dialog.cancel:cancel", "dialog.confirm:confirm"])) throw new Error("settings copy dialog topology differs");
    const checkboxAgents = catalog.filter((agent: any) => agent.status === "stable" && agent.id !== "settings");
    if (!Array.isArray(copy.checkboxes) || JSON.stringify(copy.checkboxes.map((row) => row.id).sort()) !== JSON.stringify(checkboxAgents.map((agent: any) => agent.id).sort())) throw new Error("settings copy checkbox topology differs");
    if (JSON.stringify(topology.checkboxSwitches.map((row) => row.key).sort()) !== JSON.stringify(copy.checkboxes.map((row) => row.id).sort())) throw new Error("settings copy checkbox switch topology differs from signed controls");
    for (const row of copy.checkboxes) {
      const agent = checkboxAgents.find((agent: any) => agent.id === row.id);
      if (row.title !== agent.title[language] || row.description !== agent.description[language]) throw new Error("settings copy caption differs from signed agent metadata");
    }
    if (!copy.errors || Object.keys(copy.errors).sort().join() !== ["mutationFailure", "unknownApplicabilityWarning"].sort().join() || Object.values(copy.errors).some(text => typeof text !== "string" || !text || text.length > 4096)) throw new Error("settings copy production error states incomplete");
    for (const text of [copy.commonTitle, copy.startupTitle, copy.interfaceGroup, copy.vehicleGroup, copy.unstableGroup, ...copy.startup.flatMap((row) => [row.label, row.description]), ...Object.values(copy.uninstall ?? {})])
      if (typeof text !== "string" || !text || text.length > 4096) throw new Error("settings copy contains an invalid caption");
    for (const key of ["group", "buttonText", "description", "awaitingConfirmation", "started", "retryableError", "confirmLabel", "cancelLabel"])
      if (typeof copy.uninstall?.[key] !== "string") throw new Error("settings copy uninstall states incomplete");
  }
  if (!value.geometry || Object.values(value.geometry).some((number) => !Number.isFinite(number))) throw new Error("settings copy component geometry missing");
  return { languages: value.languages, geometry: value.geometry, path, sha256: sha(bytes) };
}

export function panelCopy(copy: CopyLanguage, startup: string): string[] {
  const selected = copy.startup.find((row) => row.value === startup);
  if (!selected) throw new Error("confirmed startup mode is not represented by source copy");
  return [copy.commonTitle, copy.startupTitle, copy.interfaceGroup, copy.vehicleGroup, ...copy.startup.map((row) => row.label), selected.description, ...copy.checkboxes.flatMap((row) => [row.title, row.description]), copy.uninstall.group, copy.uninstall.buttonText, copy.uninstall.description].filter(Boolean);
}
