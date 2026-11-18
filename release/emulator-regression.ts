import { readFileSync, realpathSync, writeFileSync } from "node:fs";
import { isAbsolute, join } from "node:path";
import { runRegression, spawnAdapter, validateConfig, regressionOptions } from "./emulator-gate.ts";
import { gateReport } from "../tools/emulator/gate-report.ts";

type Scenario = "vehicle" | "settings-radio" | "launcher" | "lifecycle";
type ReportIo = { read: (path: string) => string; write: (path: string, content: string) => void; error: (message: string) => void };
/** Preserve terminal case evidence and report partial acceptance separately. */
export async function runRegressionReport(output: string, scenario: Scenario, run: () => Promise<unknown>, io: ReportIo, options: { imageCapture?: "deferred"; vehiclePart?: "pw"; settingsPart?: "postboot" } = {}): Promise<number> {
  regressionOptions(scenario, options);
  let exit = 0;
  try { await run(); }
  catch (error) { io.error(String(error)); exit = 2; }
  try {
    const source = join(output, "emulator-runtime-evidence.json");
    const evidence = JSON.parse(io.read(source));
    const summary = JSON.parse(io.read(join(output, "regression-summary.json")));
    if (summary.evidenceType !== "installed-kit-partial-regression" || summary.scenario !== scenario || summary.fullAcceptance !== false || summary.publication !== "forbidden" || typeof summary.passed !== "boolean" || summary.invocationId !== evidence.invocationId || evidence.passed !== false)
      throw new Error("partial regression summary differs from retained evidence");
    if (options.vehiclePart !== summary.vehiclePart || options.vehiclePart !== evidence.vehiclePart || (options.vehiclePart !== undefined && (options.vehiclePart !== "pw" || scenario !== "vehicle" || options.imageCapture !== "deferred"))) throw new Error("explicit PW report option differs");
    if (options.settingsPart !== summary.settingsPart || options.settingsPart !== evidence.settingsPart || (options.settingsPart !== undefined && (options.settingsPart !== "postboot" || scenario !== "settings-radio" || options.imageCapture !== "deferred" || options.vehiclePart !== undefined))) throw new Error("explicit postboot report option differs");
    const deferred = options.imageCapture === "deferred";
    if (deferred && ((scenario !== "vehicle" && scenario !== "settings-radio" && scenario !== "lifecycle") || summary.imageCapture !== "deferred" || evidence.imageCapture !== "deferred" || summary.passed !== false || typeof summary.semanticPassed !== "boolean")) throw new Error("deferred semantic report mode differs");
    if (!deferred && (summary.imageCapture !== undefined || evidence.imageCapture !== undefined)) throw new Error("explicit deferred report option required");
    if (deferred && scenario === "settings-radio" && (JSON.stringify(summary.pendingAcceptance) !== JSON.stringify(["images", "animation", "visual"]) || !summary.semanticScope?.includes("XML"))) throw new Error("settings-radio pending acceptance scope missing");
    const report = gateReport(evidence, source);
    io.write(join(output, "report.md"), `# Partial regression: ${scenario}

Selected scenario: ${deferred ? (summary.semanticPassed ? "semantic checks passed; visual acceptance pending" : "semantic checks not passed; visual acceptance pending") : (summary.passed ? "passed" : "not passed")}. Full 27-case acceptance: false. Publication: forbidden.

This is a partial regression, not release acceptance. Canonical case statuses below are unchanged.

${report}`);
    if (!(deferred ? summary.semanticPassed : summary.passed) && exit === 0) exit = 1;
  } catch (error) { io.error(`Partial regression report failed: ${String(error)}`); if (exit === 0) exit = 2; }
  return exit;
}
if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)) {
  try {
    if (process.argv.length !== 4 || !["vehicle", "settings-radio", "launcher", "lifecycle"].includes(process.argv[2]) || !isAbsolute(process.argv[3]))
      throw new Error("usage: emulator-regression.ts vehicle|settings-radio|launcher|lifecycle /absolute/config.json (already-installed exact kit required; partial regression only)");
    const scenario = process.argv[2] as Scenario;
    const config = validateConfig(JSON.parse(readFileSync(process.argv[3], "utf8")));
    process.exitCode = await runRegressionReport(config.output, scenario, () => runRegression(config, spawnAdapter, scenario, scenario === "lifecycle" ? {imageCapture:"deferred"} : {}), {
      read: path => readFileSync(path, "utf8"), write: (path, content) => writeFileSync(path, content, { flag: "wx", mode: 0o600 }), error: message => console.error(message),
    }, scenario === "lifecycle" ? {imageCapture:"deferred"} : {});
  } catch (error) { console.error(String(error)); process.exitCode = 2; }
}
