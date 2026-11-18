import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync, realpathSync } from "node:fs";
import { isAbsolute, join } from "node:path";
import { gateReport } from "./gate-report.ts";
import { runtimeLogger } from "./runtime-log.ts";

type ReportIo = { read: (path: string) => string; write: (path: string, content: string) => void; error: (message: string) => void };
/** Run the existing dispatcher once, then report its terminal retained evidence. */
export function runReport(config: string, run: (config: string) => number, io: ReportIo): number {
  if (!isAbsolute(config)) throw new Error("absolute gate config required");
  const parsed = JSON.parse(io.read(config));
  if (typeof parsed.output !== "string" || !isAbsolute(parsed.output)) throw new Error("absolute gate output required");
  const exit = run(config);
  try {
    const source = join(parsed.output, "emulator-runtime-evidence.json");
    const evidence = JSON.parse(io.read(source));
    const report = gateReport(evidence, source);
    io.write(join(parsed.output, "report.md"), report);
    if (exit === 0 && evidence.passed !== true) {
      io.error("Emulator gate exit code 0 contradicts retained evidence passed=false; report written, returning 2");
      return 2;
    }
  } catch (error) {
    io.error(`Emulator report failed: ${String(error)}; gate exit code ${exit}`);
    return exit === 0 ? 2 : exit;
  }
  return exit;
}
if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)) {
  const log = runtimeLogger("run-report");
  try {
    if (process.argv.length !== 3) throw new Error("usage: run-report.ts /absolute/config.json");
    process.exitCode = runReport(process.argv[2], config => {
      const result = spawnSync(process.execPath, [new URL("../../release/emulator-gate.ts", import.meta.url).pathname, config], { stdio: "inherit" });
      if (result.error || result.signal) log.error(result.error ?? `Emulator gate process failed: ${result.signal}`);
      return result.status ?? 2;
    }, { read: path => readFileSync(path, "utf8"), write: (path, content) => writeFileSync(path, content, { flag: "wx", mode: 0o600 }), error: message => log.error(message) });
  } catch (error) { log.error(error); process.exitCode = 2; }
}
