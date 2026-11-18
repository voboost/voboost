import { createHash, createPrivateKey, createPublicKey, sign } from "node:crypto";
import { existsSync, lstatSync, readFileSync, realpathSync, readdirSync, writeFileSync } from "node:fs";
import { isAbsolute, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import type { ReviewRequest } from "./visual-review.ts";
import { runtimeLogger } from "./runtime-log.ts";
const encoded = (value: unknown) => Buffer.from(JSON.stringify(value) + "\n");
export const hash = (value: Buffer) => createHash("sha256").update(value).digest("hex");
export function readRegular(path: string): Buffer {
  if (!isAbsolute(path) || realpathSync(path) !== resolve(path)) throw new Error("absolute non-symlink review path required");
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.size > 32 * 1024 * 1024) throw new Error("bounded review file required");
  return readFileSync(path);
}
export function sessionPublicKey(key: string | Buffer, releaseKeys: string): string {
  const publicKey = createPublicKey(key);
  if (publicKey.asymmetricKeyType !== "ed25519") throw new Error("session Ed25519 key required");
  const identity = hash(publicKey.export({type:"spki",format:"der"}));
  if (!isAbsolute(releaseKeys) || realpathSync(releaseKeys) !== resolve(releaseKeys)) throw new Error("absolute release authority directory required");
  let checked = 0;
  for (const name of readdirSync(releaseKeys)) {
    if (!/\.(pem|pub|key)$/.test(name)) continue;
    let candidate;
    try { candidate = createPublicKey(readRegular(join(releaseKeys,name))); } catch { continue; }
    checked++;
    if (hash(candidate.export({type:"spki",format:"der"})) === identity) throw new Error("release authority cannot sign manual review");
  }
  if (checked === 0) throw new Error("release authority exclusion set is empty");
  return publicKey.export({type:"spki",format:"pem"}).toString();
}
export async function waitReceipt(path: string, timeoutMs: number, clock = () => Date.now(), sleep = delay): Promise<string | null> {
  if (!isAbsolute(path) || !Number.isSafeInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 7200000) throw new Error("absolute receipt path and bounded wait required");
  const deadline = clock() + timeoutMs;
  while (clock() < deadline) {
    if (existsSync(path)) { readRegular(path); return path; }
    await sleep(Math.min(500, Math.max(1, deadline-clock())));
  }
  return null;
}
export function signAssessments(request: ReviewRequest, assessment: any, privateKey: string | Buffer, releaseKeys: string): Buffer {
  const key = createPrivateKey(privateKey);
  const publicKey = sessionPublicKey(createPublicKey(key).export({type:"spki",format:"pem"}).toString(), releaseKeys);
  if (request.publicKeySha256 !== hash(createPublicKey(publicKey).export({type:"spki",format:"der"}))) throw new Error("session key differs from request");
  if (assessment.reviewer !== "root" || !Array.isArray(assessment.assessments) || assessment.assessments.length !== request.targets.length || Object.keys(assessment).sort().join() !== "assessments,reviewer") throw new Error("explicit complete root assessments required");
  for (const [index,target] of request.targets.entries()) {
    const actual = assessment.assessments[index];
    if (actual.id !== target.id || Object.keys(actual).sort().join() !== "criteria,id" || !Array.isArray(actual.criteria) || actual.criteria.length !== target.criteria.length) throw new Error("assessment coverage differs");
    for (const [i,criterion] of target.criteria.entries()) {
      const observed = actual.criteria[i];
      if (Object.keys(observed).sort().join() !== "actual,expected,id,verdict" || observed.id !== criterion.id || observed.expected !== criterion.expected || typeof observed.actual !== "string" || observed.actual.trim().length < 8 || observed.actual.length > 4096 || !["passed","failed"].includes(observed.verdict)) throw new Error("explicit observation and verdict required for every criterion");
    }
  }
  const payload = {schemaVersion:1, requestSha256:hash(encoded(request)), reviewer:"root", assessments:assessment.assessments};
  return encoded({payload,signature:sign(null,encoded(payload),key).toString("hex")});
}
if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(new URL(import.meta.url).pathname)) {
  try {
    if (process.argv.length !== 7) throw new Error("usage: manual-review.ts REQUEST ASSESSMENTS SESSION_PRIVATE_KEY RELEASE_AUTHORITY_DIRECTORY RECEIPT");
    const [request,assessment,key,authorities,receipt] = process.argv.slice(2);
    if (!isAbsolute(receipt)) throw new Error("absolute receipt output required");
    const bytes = readRegular(request), parsed = JSON.parse(bytes.toString());
    if (!bytes.equals(encoded(parsed))) throw new Error("noncanonical review request");
    writeFileSync(receipt,signAssessments(parsed,JSON.parse(readRegular(assessment).toString()),readRegular(key),authorities),{flag:"wx",mode:0o600});
  } catch (error) { runtimeLogger("manual-review").error(error); process.exitCode=2; }
}
