import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { closeSync, constants, fstatSync, lstatSync, openSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** One-shot schema2 file IPC. Only the pinned official CLI has guest read authority. */
export type CollectRequest = {
  schema: 2; op: "collect"; request_id: string; source_id: "voboost";
  session: { id: string; profile: string; guest_serial: string; boot_id: string };
  capabilities: { installer: { path: string; sha256: string } };
};
type ChildResult = { status: number | null; stdout: string | Buffer; stderr?: string | Buffer; error?: Error; signal?: string | null };
export type Run = (path: string, args: string[], options: { timeout: number; maxBuffer: number; encoding: "utf8"; shell: false; windowsHide: true }) => ChildResult;
const limit = 1_200_000;
const digest = (bytes: Buffer | string) => createHash("sha256").update(bytes).digest("hex");
function exact(value: unknown, keys: string[]): asserts value is Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value) || Object.keys(value).sort().join() !== keys.sort().join()) throw new Error("provider request fields differ");
}
function boundedRead(path: string, max: number): Buffer {
  const fd = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const before = fstatSync(fd);
    if (!before.isFile() || before.size > max) throw new Error("provider file bound differs");
    const bytes = readFileSync(fd);
    const after = fstatSync(fd);
    if (bytes.length > max || before.ino !== after.ino || before.size !== after.size || before.mtimeMs !== after.mtimeMs) throw new Error("provider file changed");
    return bytes;
  } finally { closeSync(fd); }
}
export function validateRequest(input: unknown): CollectRequest {
  exact(input, ["schema", "op", "request_id", "source_id", "session", "capabilities"]);
  exact(input.session, ["id", "profile", "guest_serial", "boot_id"]);
  exact(input.capabilities, ["installer"]);
  exact(input.capabilities.installer, ["path", "sha256"]);
  const r = input as unknown as CollectRequest;
  if (r.schema !== 2 || r.op !== "collect" || r.source_id !== "voboost" || !/^[a-zA-Z0-9._-]{1,128}$/.test(r.request_id)
      || !/^[a-zA-Z0-9._-]{1,128}$/.test(r.session.id) || !/^[a-zA-Z0-9._-]{1,64}$/.test(r.session.profile)
      || !/^[a-zA-Z0-9._:-]{1,128}$/.test(r.session.guest_serial) || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(r.session.boot_id)
      || !isAbsolute(r.capabilities.installer.path) || !/^[0-9a-f]{64}$/.test(r.capabilities.installer.sha256)) throw new Error("provider request identity differs");
  return r;
}
export function collect(input: unknown, run: Run = spawnSync, verify = (path: string, sha: string) => digest(boundedRead(path, 256 * 1024 * 1024)) === sha) {
  const r = validateRequest(input);
  const start = process.hrtime.bigint().toString();
  const base = { schema: 2, request_id: r.request_id, source: "voboost", boot_id: r.session.boot_id,
    source_instance: `${r.session.id}:${r.session.boot_id}:${r.capabilities.installer.sha256}`, coverage: "sampled", observed: { clock: "host-monotonic-ns", start } };
  const unavailable = (code: string) => ({ ...base, status: "unavailable", revision: digest(code), errors: [{ code }], observed: { ...base.observed, end: process.hrtime.bigint().toString() } });
  try { if (!verify(r.capabilities.installer.path, r.capabilities.installer.sha256)) return unavailable("INSTALLER_DIGEST_MISMATCH"); } catch { return unavailable("INSTALLER_UNAVAILABLE"); }
  let result: ChildResult;
  try { result = run(r.capabilities.installer.path, ["--observe", "--device", r.session.guest_serial], { timeout: 15000, maxBuffer: limit, encoding: "utf8", shell: false, windowsHide: true }); } catch { return unavailable("OFFICIAL_OBSERVER_UNAVAILABLE"); }
  if (result.error || result.signal || result.status !== 0 || Buffer.byteLength(result.stdout) > limit || (result.stderr && result.stderr.length)) return unavailable("OFFICIAL_OBSERVER_UNAVAILABLE");
  let observation;
  try { observation = JSON.parse(result.stdout.toString()); } catch { return unavailable("OBSERVER_PROTOCOL_INVALID"); }
  if (observation.schema !== 2 || observation.source !== "voboost" || !["ready", "unavailable"].includes(observation.status)) return unavailable("OBSERVER_PROTOCOL_INVALID");
  if (observation.status !== "ready") return unavailable("PASSIVE_READ_UNAVAILABLE");
  if (observation.boot_id !== r.session.boot_id) return unavailable("BOOT_CHANGED");
  if (typeof observation.revision !== "string" || !/^[0-9a-f]{64}$/.test(observation.revision) || !observation.data || typeof observation.data !== "object") return unavailable("OBSERVER_PROTOCOL_INVALID");
  return { ...base, status: "ready", revision: observation.revision, data: observation.data, observed: { ...base.observed, end: process.hrtime.bigint().toString() } };
}
export function main(args: string[]) {
  if (args.length !== 4 || args[0] !== "--request" || args[2] !== "--response") throw new Error("expected scoped request/response file IPC");
  const requestPath = args[1], responsePath = args[3];
  if (![requestPath, responsePath].every(isAbsolute) || dirname(requestPath) !== dirname(responsePath) || requestPath === responsePath) throw new Error("provider job scope differs");
  const job = lstatSync(dirname(requestPath));
  if (!job.isDirectory() || job.isSymbolicLink() || (job.mode & 0o777) !== 0o700 || job.uid !== process.getuid?.()) throw new Error("provider job must be private and owned");
  const result = collect(JSON.parse(boundedRead(requestPath, 16384).toString("utf8")));
  const staged = join(dirname(responsePath), ".result-pending.json");
  writeFileSync(staged, JSON.stringify(result) + "\n", { flag: "wx", mode: 0o600 });
  renameSync(staged, responsePath);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2));
