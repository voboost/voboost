import test from "node:test";
import { strictEqual, deepStrictEqual, throws } from "node:assert";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { deflateSync } from "node:zlib";
import { InstallerUi, validateRequest, type VoyemuCapture } from "./installer-ui.ts";

const context: VoyemuCapture = { root: "/project/voyemu", node: "/tools/node", profile: "free-09",
  runtimePid: 42, runtimePort: 5562, nodeSha256: "a".repeat(64), cliSha256: "b".repeat(64) };
const request = { action: "screenshot", output: "/project/output.png", display: "secondary", voyemuCapture: context };
test("official Voyemu screenshot context is secondary-only and closed", () => {
  validateRequest(request);
  for (const delta of [{ runtimePid: 0 }, { runtimePort: 5563 }, { node: "relative" }, { profile: "../foreign" },
    { cliSha256: "A".repeat(64) }, { args: ["shell"] }, { root: "/project/\nforeign" }])
    throws(() => validateRequest({ ...request, voyemuCapture: { ...context, ...delta } }));
  throws(() => validateRequest({ ...request, display: "primary" }));
  throws(() => validateRequest({ ...request, command: "shell" }));
});
function png(): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const body = Buffer.concat([Buffer.from(type), data]); let crc = 0xffffffff;
    for (const byte of body) { crc ^= byte; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
    const size = Buffer.alloc(4), checksum = Buffer.alloc(4); size.writeUInt32BE(data.length); checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
    return Buffer.concat([size, body, checksum]);
  };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(1, 0); ihdr.writeUInt32BE(1, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(Buffer.from([0,255,0,0,255]))), chunk("IEND", Buffer.alloc(0))]);
}
test("actual screenshot executor delegates through packaged CLI and retains typed context", async () => {
  const base = join(process.cwd(), "local-builds-new", "screenshot-context-tests");
  mkdirSync(base, { recursive: true }); const work = mkdtempSync(join(base, "case-"));
  try {
    const requests: any[] = []; let ownership = 0;
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(work, "ui"),
      async invocation => {
        strictEqual(invocation.executable.endsWith("/emulator-driver"), true);
        strictEqual(invocation.args[0], "--emulator-check");
        const current = JSON.parse(readFileSync(invocation.args[1], "utf8")); requests.push(current);
        writeFileSync(current.output, png(), { flag: "wx", mode: 0o600 });
        return { exitCode: 0, signal: null, timedOut: false, overflow: false,
          stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", png: current.output }) + "\n", stderr: "" };
      }, async () => { ownership++; });
    await ui.screenshot("secondary", "secondary", context);
    deepStrictEqual(requests[0].voyemuCapture, context); strictEqual(requests[0].display, "secondary");
    await ui.screenshot("primary");
    strictEqual("voyemuCapture" in requests[1], false); strictEqual("display" in requests[1], false);
    strictEqual(ownership, 2);
  } finally { rmSync(work, { recursive: true, force: true }); }
});
