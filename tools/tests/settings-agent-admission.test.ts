import { deepStrictEqual, strictEqual, throws } from "node:assert";
import { readFileSync } from "node:fs";
import test from "node:test";
import { visibleAgentChecks } from "../../release/emulator-gate.ts";
import { agentControl, parseUi } from "../emulator/installer-ui.ts";
const xml = readFileSync(new URL("../../testdata/emulator/cycle124-settings-checkbox-admission.xml", import.meta.url), "utf8");
const nodes = parseUi(xml);
const settings = { id: "settings", activation: {type: "config"}, title: { ru: "Voboost" } };
test("actual cycle124 tab is not a checkbox agent: five OEM sibling radios stay unselected", () => {
  throws(() => agentControl(nodes, "com.qinggan.app.setting", "Voboost"), /matched 5 visible nodes/);
  strictEqual(visibleAgentChecks(nodes, [settings], "ru").size, 0);
});
test("actual focused panel scroll is already unique and unrelated to the admission refusal", () => {
  const main = nodes.findIndex(n => n.resource === "com.qinggan.app.setting:id/main_contentView");
  strictEqual(nodes.filter(n => n.scrollable && n.parent === main).length, 1);
});
test("missing checkbox agents remain absent rather than assumed enabled", () => {
  strictEqual(visibleAgentChecks(nodes, [settings, {id: "font", activation: {type: "checkbox"}, title: {ru: "Absent test title"}}], "ru").size, 0);
});
test("ambiguous real checkbox remains rejected", () => {
  throws(() => visibleAgentChecks(nodes, [{id: "font", activation: {type: "checkbox"}, title: {ru: "Voboost"}}], "ru"), /matched 5 visible nodes/);
});

const catalog = JSON.parse(readFileSync(new URL("../../testdata/emulator/cycle124-agent-catalog.json", import.meta.url), "utf8")).agents;
test("actual full eight-entry catalog admits only its six checkbox agents in every language", () => {
  strictEqual(catalog.length, 8);
  const checkboxes = catalog.filter((agent: any) => agent.activation?.type === "checkbox");
  strictEqual(checkboxes.length, 6);
  strictEqual(catalog.find((agent: any) => agent.id === "ui-qinggan").title, undefined);
  for (const language of ["en", "ru", "zh"])
    deepStrictEqual([...visibleAgentChecks(nodes, catalog, language)], [...visibleAgentChecks(nodes, checkboxes, language)]);
});
test("missing checkbox title fails with an explicit diagnostic", () => {
  throws(() => visibleAgentChecks(nodes, [{id: "broken", activation: {type: "checkbox"}}], "ru"), /checkbox agent broken has no ru title/);
});
