import { copyReadableLicenses, MAC_LICENSE_NAMES } from "../../../voboost-installer/src/build/readable-licenses.ts";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
import { AGENT_TESTS, AGENT_HOST_ARGS } from "../emulator/host-gates.ts";
import { deflateSync } from "node:zlib";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, symlinkSync, rmSync } from "node:fs";
import { after } from "node:test";

import { join } from "node:path";
import type { GateConfig, ProcessResult } from "../../release/emulator-gate.ts";

const roots = new Set<string>();
after(() => {
  for (const root of roots) rmSync(root, { recursive: true, force: true });
});

export function fixture(): GateConfig {
  const scratch = join(process.cwd(), "build/unit-fixtures");
  mkdirSync(scratch, { recursive: true });
  const root = mkdtempSync(join(scratch, "voboost-functional-host-"));
  roots.add(root);
  const c: GateConfig = {
    workspaceRoot: join(root, "workspace"),
    voyemuRoot: join(root, "voyemu"),
    voyemuProfile: "host-profile",
    closure: join(root, "closure"),
    package: join(root, "package"),
    dmg: join(root, "kit.dmg"),
    fixture: join(root, "fixture.apk"),
    output: join(root, "evidence"),
  };
  for (const p of [
    c.workspaceRoot,
    c.closure,
    join(c.voyemuRoot, "src"),
    join(c.voyemuRoot, "machines", c.voyemuProfile),
    join(c.package, "Voboost Installer.app/Contents/MacOS"),
    join(c.package, "Voboost Installer.app/Contents/Resources"),
  ])
    mkdirSync(p, { recursive: true });
  writeFileSync(join(c.voyemuRoot, "src/cli.ts"), "// host fake, never executed\n");
  writeFileSync(
    join(c.voyemuRoot, "package.json"),
    JSON.stringify({ name: "voyemu", bin: { voyemu: "src/cli.ts" } }),
  );
  copyReadableLicenses(join(c.package, "Voboost Installer.app/Contents/Resources"), MAC_LICENSE_NAMES);
  writeFileSync(c.dmg, "host fixture");
  // Missing approved APK is an explicit unavailable runtime prerequisite, not a fake fixture.
  c.fixture = null;
  const manifest = JSON.stringify({ releaseIdentity: "1.0.0-beta1" });
  const agents = [
    "settings",
    "font-cyrillic",
    "keyboard-en",
    "keyboard-ru",
    "launcher-apps",
    "vehicle-drive-forced-ev",
    "vehicle-drive-pedestrian-warning",
  ].map((id) => ({
    id,
    status: "stable",
    title: { ru: id, en: id, zh: id },
    description: { ru: `${id} description`, en: `${id} description`, zh: `${id} description` },
  }));
  for (const [name, bytes] of [
    ["voboost.json", manifest],
    ["agents.json", JSON.stringify({ agents })],
  ]) {
    writeFileSync(join(c.closure, name), bytes);
    writeFileSync(join(c.package, "Voboost Installer.app/Contents/Resources", name), bytes);
  }
  writeFileSync(
    join(c.package, "Voboost Installer.app/Contents/Resources/adb"),
    "packaged dependency, never executed",
  );
  writeFileSync(
    join(c.package, "Voboost Installer.app/Contents/Resources/ADB-LICENSE.txt"),
    "license",
  );
  writeFileSync(
    join(c.package, "Voboost Installer.app/Contents/MacOS/voboost-installer"),
    "host adapter, never executed",
  );
  symlinkSync(
    "Voboost Installer.app/Contents/MacOS/voboost-installer",
    join(c.package, "voboost-installer"),
  );
  const checks = [
    ["boot", "1"],
    ["api", "28"],
    ["abi", "arm64-v8a"],
    ["selinux", "Enforcing"],
  ].map(([id, value]) => ({ id, passed: true, expected: value, actual: value }));
  writeFileSync(
    join(c.voyemuRoot, "machines", c.voyemuProfile, "machine.json"),
    JSON.stringify({
      schema: 1,
      profile: c.voyemuProfile,
      abi: "arm64-v8a",
      config: { android: { package: "system-images;android-28;default;arm64-v8a" }, displays: [] },
      installed: [],
      checks,
    }),
  );
  owningHostFixture(c);
  return c;
}
export function reply(c: GateConfig, command: string, report: any): ProcessResult {
  return {
    exitCode: 0,
    stdout:
      JSON.stringify({
        schema: 1,
        command,
        profile: c.voyemuProfile,
        ok: true,
        changed: false,
        stage: command,
        code: "OK",
        message: "host fixture",
        report,
      }) + "\n",
    stderr: "",
  };
}
export const boot = "11111111-1111-1111-1111-111111111111";

export function png(rgb = [17, 51, 85]): Buffer {
  const crc = (bytes: Buffer) => {
    let n = 0xffffffff;
    for (const b of bytes) {
      n ^= b;
      for (let i = 0; i < 8; i++) n = n & 1 ? (n >>> 1) ^ 0xedb88320 : n >>> 1;
    }
    return (n ^ 0xffffffff) >>> 0;
  };
  const chunk = (name: string, bytes: Buffer) => {
    const type = Buffer.from(name),
      out = Buffer.alloc(bytes.length + 12);
    out.writeUInt32BE(bytes.length);
    type.copy(out, 4);
    bytes.copy(out, 8);
    out.writeUInt32BE(crc(Buffer.concat([type, bytes])), bytes.length + 8);
    return out;
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(1);
  header.writeUInt32BE(1, 4);
  header[8] = 8;
  header[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(Buffer.from([0, ...rgb, 255]))),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const sha = (value: Buffer | string) => createHash("sha256").update(value).digest("hex");
function owningHostFixture(c: GateConfig) {
  const root = join(c.workspaceRoot, "voboost-agents");
  mkdirSync(root, { recursive: true });
  writeFileSync(join(root, "gradlew"), "unit mock wrapper; never spawned\n");
  writeFileSync(join(root, ".gitignore"), "build/\n");
  const env = { ...process.env, AISUITE_ALLOW_GIT: "1", GIT_AUTHOR_NAME: "Fixture", GIT_AUTHOR_EMAIL: "fixture@example.invalid", GIT_COMMITTER_NAME: "Fixture", GIT_COMMITTER_EMAIL: "fixture@example.invalid", GIT_AUTHOR_DATE: "2026-10-04T20:53:00+03:00", GIT_COMMITTER_DATE: "2026-10-04T20:53:00+03:00" };
  const git = (...args: string[]) => execFileSync("git", ["-C", root, ...args], { env, encoding: "utf8" }).trim();
  mkdirSync(join(root, "src/main/java"), { recursive: true });
  writeFileSync(join(root, "src/main/java/Policy.java"), "unit production API stand-in\n");
  writeFileSync(join(c.closure, "agent-common.dex"), "unit DEX stand-in");
  writeFileSync(join(c.package, "Voboost Installer.app/Contents/Resources/agent-common.dex"), "unit DEX stand-in");
  git("init", "-q"); git("add", "."); git("-c", "core.hooksPath=/dev/null", "commit", "-qm", "unit owning source");
  const source = { name: "agents", head: git("rev-parse", "HEAD"), tree: git("rev-parse", "HEAD^{tree}"), statusDigest: sha(""), clean: true, submodules: [] };
  writeFileSync(join(c.closure, "../receipt.json"), JSON.stringify({ schemaVersion: 1, kind: "source-build", artifacts: { "agents.json": { sha256: sha(readFileSync(join(c.closure, "agents.json"))) } }, sources: [source] }));
}
/** Emits mock JUnit only when the real gate invokes its exact owning command. */
export function owningHostReply(c: GateConfig, inv: import("../../release/emulator-gate.ts").Invocation): ProcessResult | null {
  if (!inv.host) return null;
  assert.equal(inv.host, "agents");
  assert.equal(inv.executable, join(c.workspaceRoot, "voboost-agents/gradlew"));
  assert.deepEqual(inv.args, AGENT_HOST_ARGS);
  for (const { name, task } of [...AGENT_TESTS.map(name => ({ name, task: "test" })), { name: "ru.voboost.agents.settings.SettingsCopyEvidenceTest", task: "generateSettingsCopyEvidence" }]) {
    const directory = join(c.workspaceRoot, "voboost-agents/build/test-results", task);
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, `TEST-${name}.xml`), `<testsuite name="${name}" tests="1" failures="0" errors="0" skipped="0"><testcase name="unit-mock" classname="${name}"/></testsuite>`);
  }
  writeMockSettingsCopy(c);
  return { exitCode: 0, stdout: "> Task :generateSettingsCopyEvidence\n> Task :test\n", stderr: "" };
}

function writeMockSettingsCopy(c: GateConfig) {
  const root = join(c.workspaceRoot, "voboost-agents"), catalog = readFileSync(join(c.closure, "agents.json"));
  const agents = JSON.parse(catalog.toString()).agents.filter((agent: any) => agent.id !== "settings" && agent.status === "stable");
  const languages = Object.fromEntries(["en", "ru", "zh"].map(locale => [locale, {
    layoutTopology: { sections: ["startup", "interface", "uninstall"], sectionBodies: ["startup", "interface", "uninstall"].map((key,index) => ({key,index})), checkboxSwitches: agents.map((agent: any) => ({ key: agent.id, section: "interface" })), panelTargets: [{role:"startup",value:"interface",class:"com.pateo.material.widgets.BoldRadioButton"}], dialogTargets: ["cancel","confirm"].map(value => ({role:`dialog.${value}`,value,class:"com.pateo.material.widgets.BoldTextView"})) },
    commonTitle: `Common ${locale}`, startupTitle: `Startup ${locale}`,
    startup: ["interface","hidden","none"].map(value => ({value,label:`${value} in ${locale}`,description:`${value} description ${locale}`})),
    interfaceGroup:`Interface ${locale}`,vehicleGroup:`Vehicle ${locale}`,unstableGroup:`Unstable ${locale}`,
    checkboxes: agents.map((agent: any) => ({id:agent.id,title:agent.title[locale],description:agent.description[locale]})),
    uninstall:Object.fromEntries(["group","buttonText","description","awaitingConfirmation","started","retryableError","confirmLabel","cancelLabel"].map(key => [key,`${key} ${locale}`])),
    errors:{mutationFailure:`Mutation failure ${locale}`,unknownApplicabilityWarning:`Unknown applicability ${locale}`}
  }]));
  const source = "src/main/java/Policy.java", sourceHash = sha(readFileSync(join(root,source)));
  const value = {schemaVersion:4,authority:"SettingsMenuPolicy, SettingsPanelSpec and UninstallCopy production APIs",sourceFilesSha256:{[source]:sourceHash},productionSourceSha256:sha(`${source}\0${sourceHash}\n`),agentsManifestSha256:sha(catalog),dexSha256:{"agent-common.dex":sha(readFileSync(join(c.closure,"agent-common.dex")))},geometry:{RADIO_ITEM_PADDING:26},languages};
  mkdirSync(join(root,"build/evidence"),{recursive:true});
  writeFileSync(join(root,"build/evidence/settings-copy.json"),JSON.stringify(value));
}
