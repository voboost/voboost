import assert from "node:assert/strict";
import test from "node:test";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { settingsCopy, panelCopy } from "../emulator/settings-copy.ts";
import { junit, AGENT_HOST_ARGS } from "../emulator/host-gates.ts";
import { spawnAdapter } from "../../release/emulator-gate.ts";

const sha = (value: string) => createHash("sha256").update(value).digest("hex");
function fixture() {
  const root = mkdtempSync(join(tmpdir(), "voboost-copy-host-")), closure = join(root, "closure");
  mkdirSync(join(root, "src/main/java"), { recursive: true }); mkdirSync(join(root, "build/evidence"), { recursive: true }); mkdirSync(closure);
  const source = "production Java fixture", sourceFilesSha256 = { "src/main/java/Policy.java": sha(source) };
  writeFileSync(join(root, "src/main/java/Policy.java"), source);
  const agent = { id: "font-cyrillic", status: "stable", title: { en: "Font", ru: "Font RU", zh: "Font ZH" }, description: { en: "Font description", ru: "Font description RU", zh: "Font description ZH" } };
  const metadata = JSON.stringify({ agents: [agent] }); writeFileSync(join(closure, "agents.json"), metadata); writeFileSync(join(closure, "agent-common.dex"), "DEX fixture");
  const languages = Object.fromEntries(["en", "ru", "zh"].map((locale) => [locale, {
    layoutTopology: { sections: ["startup", "interface", "uninstall"], sectionBodies: ["startup", "interface", "uninstall"].map((key, index) => ({ key, index })), checkboxSwitches: [{ key: agent.id, section: "interface" }], panelTargets: [{ role: "startup", value: "interface", class: "com.pateo.material.widgets.BoldRadioButton" }], dialogTargets: ["cancel", "confirm"].map((value) => ({ role: `dialog.${value}`, value, class: "com.pateo.material.widgets.BoldTextView" })) },
    commonTitle: `Common ${locale}`, startupTitle: `Startup ${locale}`,
    startup: ["interface", "hidden", "none"].map((value) => ({ value, label: `${value} in ${locale}`, description: `${value} description ${locale}` })),
    interfaceGroup: `Interface ${locale}`, vehicleGroup: `Vehicle ${locale}`, unstableGroup: `Unstable ${locale}`,
    checkboxes: [{ id: agent.id, title: agent.title[locale], description: agent.description[locale] }],
    uninstall: Object.fromEntries(["group", "buttonText", "description", "awaitingConfirmation", "started", "retryableError", "confirmLabel", "cancelLabel"].map((key) => [key, `${key} ${locale}`])),
    errors: { mutationFailure: `Mutation failure ${locale}`, unknownApplicabilityWarning: `Unknown applicability ${locale}` },
  }]));
  const value: any = { schemaVersion: 4, authority: "SettingsMenuPolicy, SettingsPanelSpec and UninstallCopy production APIs", sourceFilesSha256, productionSourceSha256: sha(`src/main/java/Policy.java\0${sha(source)}\n`), agentsManifestSha256: sha(metadata), dexSha256: { "agent-common.dex": sha("DEX fixture") }, geometry: { RADIO_ITEM_PADDING: 26 }, languages };
  const save = () => writeFileSync(join(root, "build/evidence/settings-copy.json"), JSON.stringify(value)); save();
  return { root, closure, value, save };
}
test("Settings copy binds exact source inventory source digest signed metadata and delivered DEX", () => {
  const { root, closure } = fixture(), copy = settingsCopy(root, closure);
  assert.ok(panelCopy(copy.languages.ru, "hidden").includes("hidden description ru"));
  assert.ok(!panelCopy(copy.languages.ru, "hidden").includes("none description ru"));
  for (const mutate of [
    (f: ReturnType<typeof fixture>) => { f.value.productionSourceSha256 = "0".repeat(64); },
    (f: ReturnType<typeof fixture>) => { f.value.agentsManifestSha256 = "0".repeat(64); },
    (f: ReturnType<typeof fixture>) => { f.value.dexSha256["agent-common.dex"] = "0".repeat(64); },
    (f: ReturnType<typeof fixture>) => { f.value.languages.en.checkboxes[0].title = "caller caption"; },
    (f: ReturnType<typeof fixture>) => { f.value.languages.en.startup[0].value = "hidden"; },
    (f: ReturnType<typeof fixture>) => { delete f.value.languages.ru.errors.mutationFailure; },
    (f: ReturnType<typeof fixture>) => { f.value.languages.zh.errors.mutationFailure = true; },
    (f: ReturnType<typeof fixture>) => { writeFileSync(join(f.root, "src/main/java/Extra.java"), "new source"); },
    (f: ReturnType<typeof fixture>) => { writeFileSync(join(f.closure, "agent-extra.dex"), "foreign DEX"); },
  ]) { const f = fixture(); mutate(f); f.save(); assert.throws(() => settingsCopy(f.root, f.closure)); }
});
test("Settings copy rejects removed language controls and validates startup copy", () => {
  for (const language of ["en", "ru", "zh"] as const) {
    for (const key of ["language", "languageTitle", "languageDescription"]) {
      const changed = fixture(); changed.value.languages[language][key] = null; changed.save();
      assert.throws(() => settingsCopy(changed.root, changed.closure), /topology/);
    }
    for (const malformed of [undefined, "", false, 4, {}, []]) {
      const changed = fixture(); changed.value.languages[language].startup[0].description = malformed; changed.save();
      assert.throws(() => settingsCopy(changed.root, changed.closure), /caption/);
    }
  }
});
test("fresh owning JUnit report rejects wrong class zero tests failures skips and partial output", () => {
  const xml = '<testsuite name="OwningTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="realScenario"/></testsuite>';
  assert.equal(junit(xml, "OwningTest"), 1);
  for (const value of [xml.replace('failures="0"', 'failures="1"'), xml.replace('skipped="0"', 'skipped="1"'), xml.replace('tests="1"', 'tests="0"'), xml.replace('OwningTest', 'ForeignTest'), xml.slice(0, -12), '<!DOCTYPE x>' + xml]) assert.throws(() => junit(value, "OwningTest"));
});
test("actual owning Gradle JUnit declaration and CDATA retain exact tests and reject hostile XML", () => {
  for (const [file, name, count] of [
    ["settings-copy-junit.xml", "ru.voboost.agents.settings.SettingsCopyEvidenceTest", 1],
    ["forced-ev-junit.xml", "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvDisarmSnapshotRegressionTest", 7],
  ] as const) {
    const xml = readFileSync(new URL(`../../testdata/emulator-cycle64/${file}`, import.meta.url), "utf8");
    assert.equal(junit(xml, name), count);
    for (const value of [
      xml.replace("<![CDATA[", "<![CDATA[<testcase name=\"not-a-test\"/><failure/>"),
    ]) assert.equal(junit(value, name), count);
    for (const value of [xml + "<testsuite/>", xml.replace("</system-out>", "</testcase>"), xml.replace('failures="0"', 'failures="1"'), xml.replace("<![CDATA[", "<!ENTITY external SYSTEM \"file:///etc/passwd\">"), "x".repeat(8 * 1024 * 1024 + 1)])
      assert.throws(() => junit(value, name));
  }
});
test("owning host command is product authored and rejects caller test filters", async () => {
  assert.ok(AGENT_HOST_ARGS.includes("--offline")); assert.equal(AGENT_HOST_ARGS.filter(arg=>arg==="--rerun").length,2);
  for(const task of [":generateSettingsCopyEvidence",":test"]) assert.equal(AGENT_HOST_ARGS[AGENT_HOST_ARGS.indexOf(task)+1],"--rerun");
  await assert.rejects(() => spawnAdapter({ executable: "/host/gradlew", args: ["callerTask"], timeoutMs: 1000, host: "agents" }), /only packaged/);
});

test("source copy requires every section body and signed checkbox switch topology", () => {
  for (const mutate of [
    (topology) => { delete topology.sectionBodies; },
    (topology) => { topology.sectionBodies.pop(); },
    (topology) => { topology.sectionBodies[0].index = 2; },
    (topology) => { topology.sectionBodies[0].extra = true; },
    (topology) => { delete topology.checkboxSwitches; },
    (topology) => { topology.checkboxSwitches = []; },
    (topology) => { topology.checkboxSwitches[0].section = "foreign"; },
    (topology) => { topology.checkboxSwitches[0].key = "caller"; },
  ]) {
    const f = fixture(); mutate(f.value.languages.en.layoutTopology); f.save();
    assert.throws(() => settingsCopy(f.root, f.closure), /topology/);
  }
  const f = fixture();
  f.value.languages.en.layoutTopology.sectionBodies = f.value.languages.en.layoutTopology.sectionBodies.map(({ key, index }) => ({ index, key }));
  f.save(); assert.doesNotThrow(() => settingsCopy(f.root, f.closure));
});
