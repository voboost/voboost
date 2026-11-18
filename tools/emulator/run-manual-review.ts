import { existsSync, readFileSync, realpathSync, writeFileSync } from "node:fs";
import { isAbsolute } from "node:path";
import { runGate, validateConfig, spawnAdapter } from "../../release/emulator-gate.ts";
import { runReport } from "./run-report.ts";
import { readRegular, sessionPublicKey, waitReceipt } from "./manual-review.ts";
import { runtimeLogger } from "./runtime-log.ts";
const log = runtimeLogger("run-manual-review");
export async function main(args: string[]): Promise<number> {
  if (args.length !== 5) throw new Error("usage: run-manual-review.ts CONFIG SESSION_PUBLIC_KEY RELEASE_AUTHORITY_DIRECTORY RECEIPT WAIT_MS");
  const [configPath,keyPath,authorities,receipt,timeout] = args;
  if (!isAbsolute(receipt) || existsSync(receipt)) throw new Error("fresh absolute receipt path required");
  const timeoutMs = Number(timeout);
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 7200000) throw new Error("wait must be 1..7200000 milliseconds");
  const publicKey = sessionPublicKey(readRegular(keyPath),authorities);
  const config = validateConfig(JSON.parse(readRegular(configPath).toString()));
  let code = 2;
  try {
    const evidence = await runGate(config,spawnAdapter,{publicKey,receive: async requestPath => {
      log.emit("info", "manual-review-request-ready", {requestPath, receiptPath:receipt, timeoutMs:String(timeoutMs)});
      return waitReceipt(receipt,timeoutMs);
    }});
    code = evidence.passed === true ? 0 : 1;
  } catch(error) { log.error(error); }
  return runReport(configPath,()=>code,{read:path=>readFileSync(path,"utf8"),write:(path,content)=>writeFileSync(path,content,{flag:"wx",mode:0o600}),error:message=>log.error(message)});
}
if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)) main(process.argv.slice(2)).then(code=>{process.exitCode=code;}).catch(error=>{log.error(error);process.exitCode=2;});
