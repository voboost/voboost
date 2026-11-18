import { readFileSync, realpathSync } from "node:fs";
import { isAbsolute, join } from "node:path";
import { runFullSemanticGate, validateConfig, spawnAdapter } from "./emulator-gate.ts";
/** Opt-in destructive full27 runner; the ordinary visual runner is unchanged. */
export async function main(args = process.argv.slice(2)): Promise<number> {
  if (args.length !== 2 || args.some(path=>!isAbsolute(path))) throw new Error("usage: emulator-full-semantic.ts /absolute/config.json /absolute/full-run-permit.json");
  const config=validateConfig(JSON.parse(readFileSync(args[0],"utf8")));
  const permit=JSON.parse(readFileSync(args[1],"utf8"));
  await runFullSemanticGate(config,spawnAdapter,permit);
  const summary=JSON.parse(readFileSync(join(config.output,"full-semantic-summary.json"),"utf8"));
  return summary.semanticFullAcceptance === true && summary.fullAcceptance === false && summary.visualAcceptance === false ? 0 : 1;
}
if (process.argv[1] && realpathSync(process.argv[1])===realpathSync(new URL(import.meta.url).pathname)) main().then(code=>{process.exitCode=code;}).catch(error=>{console.error(String(error));process.exitCode=2;});
