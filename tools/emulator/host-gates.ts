import { admitLocalArtifact, type VerificationDelta } from "../release/local-source-admission.ts";
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, lstatSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { snapshotRepository } from "../release/build-closure.ts";
import type { Invocation, ProcessResult } from "../../release/emulator-gate.ts";

export const AGENT_TESTS = [
  "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvDisarmSnapshotRegressionTest",
  "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvAgentTest",
  "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvUnavailableStateTailsTest",
  "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningReadinessExhaustionLifecycleTest",
  "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningAccLifecycleTest",
  "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningAgentTest",
  "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningRetryMatrixTest",
  "ru.voboost.agents.vehicle.drive.pedestrianwarning.VehicleDrivePedestrianWarningPublicStopDuringHeldStateReadTest",
] as const;
export const AGENT_HOST_ARGS = ["--offline", "--no-daemon", "--console=plain", ":generateSettingsCopyEvidence", "--rerun", ":test", "--rerun", ...AGENT_TESTS.flatMap((name) => ["--tests", name])];
const hash = (value: Buffer | string) => createHash("sha256").update(value).digest("hex");

export class HostSourceMismatch extends Error {
  constructor() {
    super("owning host source snapshot differs from closure source");
    this.name = "HostSourceMismatch";
  }
}

export function junit(xml: string, name: string): number {
  if (Buffer.byteLength(xml) > 8 * 1024 * 1024) throw new Error("invalid bounded JUnit report");
  const stack: string[] = [];
  let fields: Record<string, string> | null = null, tests = 0, offset = 0;
  const entity = (value: string) => value.replace(/&([^;]*);/g, (_, token: string) => {
    const named: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
    if (Object.hasOwn(named, token)) return named[token];
    if (!/^#(?:[0-9]+|x[0-9a-f]+)$/i.test(token)) throw new Error("invalid JUnit entity");
    const number = token.startsWith("#x") ? parseInt(token.slice(2), 16) : Number(token.slice(1));
    if (!Number.isSafeInteger(number) || number <= 0 || number > 0x10ffff || (number >= 0xd800 && number <= 0xdfff)) throw new Error("invalid JUnit entity");
    return String.fromCodePoint(number);
  });
  // Gradle emits CDATA logs. They are text, never testcase or failure markup.
  while (offset < xml.length) {
    if (xml.startsWith("<?xml ", offset) && offset === 0) {
      const declaration = /^<\?xml version="1\.0" encoding="UTF-8"\?>/.exec(xml);
      if (!declaration) throw new Error("invalid JUnit XML declaration");
      offset += declaration[0].length; continue;
    }
    if (xml.startsWith("<![CDATA[", offset)) {
      const end = xml.indexOf("]]>", offset + 9);
      if (!["system-out", "system-err"].includes(stack.at(-1) ?? "") || end < 0) throw new Error("invalid JUnit CDATA");
      offset = end + 3; continue;
    }
    if (xml[offset] !== "<") {
      const end = xml.indexOf("<", offset), text = xml.slice(offset, end < 0 ? xml.length : end);
      if (text.trim() && !["system-out", "system-err"].includes(stack.at(-1) ?? "")) throw new Error("unexpected JUnit text");
      if (/&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-f]+;)/i.test(text)) throw new Error("invalid JUnit entity");
      entity(text); offset += text.length; continue;
    }
    const token = /^<(\/)?([a-z][a-z-]*)(\s[^<>]*?)?(\/?)>/.exec(xml.slice(offset));
    if (!token) throw new Error("invalid JUnit markup");
    const [, closing, tag, raw = "", selfClosing] = token;
    if (["failure", "error", "skipped"].includes(tag)) throw new Error("required host tests failed or skipped");
    if (closing) {
      if (raw.trim() || selfClosing || stack.pop() !== tag) throw new Error("unbalanced JUnit report");
    } else {
      const attributes: Record<string, string> = {};
      let rest = raw;
      while (rest.trim()) {
        const attribute = /^\s+([a-zA-Z][a-zA-Z0-9_.:-]*)="([^"<>]*)"/.exec(rest);
        if (!attribute || Object.hasOwn(attributes, attribute[1]) || /&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-f]+;)/i.test(attribute[2])) throw new Error("invalid JUnit attribute");
        attributes[attribute[1]] = entity(attribute[2]); rest = rest.slice(attribute[0].length);
      }
      const parent = stack.at(-1);
      if (tag === "testsuite") {
        if (fields || parent || selfClosing) throw new Error("duplicate or incomplete JUnit suite");
        fields = attributes;
      } else if (!fields || !(parent === "testsuite" && ["properties", "testcase", "system-out", "system-err"].includes(tag)) && !(parent === "properties" && tag === "property")) throw new Error("unexpected JUnit topology");
      if (tag === "testcase") {
        if (!attributes.name || (attributes.classname !== undefined && attributes.classname !== name)) throw new Error("JUnit testcase belongs to another class");
        tests++;
      }
      if (!selfClosing) stack.push(tag);
    }
    offset += token[0].length;
  }
  if (!fields || stack.length) throw new Error("incomplete JUnit report");
  const count = Number(fields.tests);
  if (fields.name !== name || !/^[0-9]+$/.test(fields.tests) || !Number.isSafeInteger(count) || count < 1 || fields.failures !== "0" || fields.errors !== "0" || fields.skipped !== "0" || tests !== count)
    throw new Error("required host tests failed, skipped or belong to another class");
  return count;
}

export function assertFreshHostExecutions(stdout: string): void {
  const lines = stdout.split(/\r?\n/);
  for (const task of [":generateSettingsCopyEvidence", ":test"]) {
    const rows = lines.filter(line => line.startsWith(`> Task ${task}`) && (line.length === `> Task ${task}`.length || line[`> Task ${task}`.length] === " "));
    if (rows.length !== 1 || rows[0] !== `> Task ${task}`) throw new Error(`required host task did not freshly execute: ${task}`);
  }
}

/** Fixed owning-repository tests, never caller-authored commands or an imported pass receipt. */
export async function agentHostGates(workspace: string, closure: string, output: string, invoke: (invocation: Invocation) => Promise<ProcessResult>, localArtifact?: { dmg: string; verificationDelta: VerificationDelta[] }): Promise<string[]> {
  const root = join(workspace, "voboost-agents"), sourceReceipt = join(dirname(closure), "receipt.json");
  if ((!localArtifact && !existsSync(sourceReceipt)) || !existsSync(join(root, "gradlew"))) throw new Error("source-build receipt or owning host wrapper unavailable");
  const receiptPath = localArtifact ? join(dirname(closure), "local-build-evidence.json") : sourceReceipt;
  const receiptSha256 = hash(readFileSync(receiptPath));
  const receipt = JSON.parse(readFileSync(receiptPath, "utf8"));
  if (localArtifact) {
    admitLocalArtifact(workspace, closure, localArtifact.dmg, localArtifact.verificationDelta);
    assertLocalHostCatalog(receipt, hash(readFileSync(join(closure, "agents.json"))));
  }
  if (!localArtifact && (receipt.schemaVersion !== 1 || receipt.kind !== "source-build" || receipt.artifacts?.["agents.json"]?.sha256 !== hash(readFileSync(join(closure, "agents.json"))))) throw new Error("host gate source-build receipt differs from delivered catalog");
  const before = await snapshotRepository("agents", root), expected = receipt.sources?.find((row: any) => row.name === "agents");
  if (!localArtifact && JSON.stringify(before) !== JSON.stringify(expected)) throw new HostSourceMismatch();
  const result = await invoke({ executable: join(root, "gradlew"), args: [...AGENT_HOST_ARGS], timeoutMs: 1800000, host: "agents" });
  if (result.exitCode !== 0 || result.timedOut || result.signal || result.overflow) throw new Error("fixed owning host gates did not complete successfully");
  assertFreshHostExecutions(result.stdout);
  if (localArtifact) admitLocalArtifact(workspace, closure, localArtifact.dmg, localArtifact.verificationDelta);
  if (hash(readFileSync(receiptPath)) !== receiptSha256) throw new Error("host source receipt changed during tests");
  const after = await snapshotRepository("agents", root);
  if (JSON.stringify(after) !== JSON.stringify(before)) throw new Error("owning host source changed during tests");
  const directory = join(output, "host-gates"); mkdirSync(directory, { mode: 0o700 });
  const artifacts: string[] = [];
  const reports = [...AGENT_TESTS.map((name) => ({ name, task: "test" })), { name: "ru.voboost.agents.settings.SettingsCopyEvidenceTest", task: "generateSettingsCopyEvidence" }];
  const tests = [];
  for (const { name, task } of reports) {
    const path = join(root, "build/test-results", task, `TEST-${name}.xml`), stat = lstatSync(path);
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 8 * 1024 * 1024) throw new Error("unsafe or unbounded owning host report");
    const xml = readFileSync(path, "utf8"), count = junit(xml, name), target = join(directory, `${name}.xml`);
    copyFileSync(path, target); artifacts.push(target);
    tests.push({ class: name, tests: count, sha256: hash(xml) });
  }
  const path = join(directory, "source-bound-host-gates.json");
  writeFileSync(path, JSON.stringify({ schemaVersion: 1, producer: "fixed-owning-host-tests", sourceAuthority: localArtifact ? "local-artifact" : "source-build", sourceReceiptSha256: hash(readFileSync(receiptPath)), before, after, tests, environment: "host", runtimeProof: false }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
  return [...artifacts, path];
}

export function assertLocalHostCatalog(receipt: any, catalogSha256: string): void {
 const rows = receipt.artifactSet?.artifacts?.filter((row:any) => row.component === "agents" && row.file === "agents.json");
 if(receipt.schemaVersion !== 1 || receipt.authority !== "persistent-local-test-only" || receipt.publication !== "forbidden" || !Array.isArray(rows) || rows.length !== 1 || rows[0].sha256 !== catalogSha256) throw new Error("local host catalog differs from delivered catalog");
}
