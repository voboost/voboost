import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { once } from "node:events";
import { readFileSync } from "node:fs";
import test from "node:test";
import { canonicalRecord, runtimeLogger } from "./runtime-log.ts";

test("actual host serializer preserves content, UTC identity and three levels", () => {
  const lines: string[] = [];
  const clock = () => new Date("2026-10-26T07:00:00.123Z");
  const log = runtimeLogger("host-test", line => { lines.push(line); }, clock);
  const original = log.emit("debug", 'Русский\n"quoted"\ttab', { reason: "waiting", originalTime: "12" });
  log.emit("info", "completed", { outcome: "success" });
  log.error(new Error("failure"));
  assert.equal(lines.length, 3);
  for (const [index, line] of lines.entries()) {
    assert.equal(line.split("\n").length, 2);
    const record = JSON.parse(line);
    canonicalRecord(record);
    assert.equal(record.sequence, index + 1);
    assert.equal(record.timestamp, clock().toISOString());
    assert.deepEqual(record.source, { kind: "host", id: "host-test" });
  }
  assert.deepEqual(JSON.parse(lines[0]), original);
  assert.equal(JSON.parse(lines[2]).fields.error_type, "Error");
  assert.match(JSON.parse(lines[2]).fields.stack, /failure/);
});

test("broken diagnostic sink is attempted once and cannot replace the original failure", () => {
  let attempts = 0;
  const log = runtimeLogger("failed-sink", () => { attempts++; throw new Error("sink failed"); });
  const original = new Error("original action failure");
  const record = log.error(original);
  canonicalRecord(record);
  assert.equal(attempts, 1);
  assert.equal(record.message, String(original));
  assert.match(record.fields.stack, /original action failure/);
});

test("a closed real stderr pipe preserves the intended action exit without an uncaught sink error", { timeout: 10000 }, async t => {
  const moduleUrl = new URL("runtime-log.ts", import.meta.url).href;
  const child = spawn(process.execPath, ["--input-type=module", "--eval", `
    const {runtimeLogger}=await import(${JSON.stringify(moduleUrl)});
    process.send('ready');
    process.once('message',()=>{runtimeLogger('closed-pipe').error(new Error('action failed'));process.exitCode=2;process.disconnect();});
  `], { stdio: ["ignore", "pipe", "pipe", "ipc"], env: { ...process.env, NODE_NO_WARNINGS: "1" } });
  t.after(() => { if (child.exitCode === null) child.kill(); });
  const exit = once(child, "exit");
  await once(child, "message");
  child.stderr.destroy();
  await once(child.stderr, "close");
  child.send("write");
  assert.deepEqual(await exit, [2, null]);
});

test("canonical validation rejects wrong levels, timestamp and typed field corruption without rewriting", () => {
  const record = runtimeLogger("fixture", () => {}, () => new Date("2026-10-26T07:00:00.123Z")).emit("info", "ok");
  for (const patch of [{ level: "SUCCESS" }, { level: "warn" }, { sequence: 0 }, { timestamp: "2026-10-26T07:00:00Z" },
    { timestamp: "2026-02-30T07:00:00.123Z" }, { fields: { count: 5 } }, { extra: "field" }]) {
    assert.throws(() => canonicalRecord({ ...record, ...patch }));
  }
  const before = JSON.stringify(record);
  canonicalRecord(record);
  assert.equal(JSON.stringify(record), before);
  assert.throws(() => runtimeLogger("invalid", () => {}).emit("SUCCESS" as any, "bad"));
});

test("shared canonical consumer accepts independent producer streams without overwriting provenance", () => {
  const base = runtimeLogger("fixture", () => {}, () => new Date("2026-10-26T07:00:00.123Z")).emit("debug", "original\nmessage", { event: "waiting", attempt: "1000" });
  // Logical cross-format fixture cases, not evidence that the native/JVM serializers ran.
  for (const source of [{ kind: "daemon" }, { kind: "agent", id: "launcher" }, { kind: "app" },
    { kind: "installer", id: "cli" }, { kind: "host", id: "manual-review" }]) {
    const row = { ...base, sequence: 72, source };
    const bytes = JSON.stringify(row);
    canonicalRecord(row);
    assert.equal(JSON.stringify(row), bytes);
    assert.equal(row.timestamp, base.timestamp);
    assert.equal(row.sequence, 72);
  }
});

test("standalone wrapper failure produces canonical stderr and leaves stdout empty", () => {
  for (const filename of ["gate-report.ts", "run-report.ts", "manual-review.ts", "run-manual-review.ts", "../../release/emulator-gate.ts"]) {
    const result = spawnSync(process.execPath, [new URL(filename, import.meta.url).pathname], {
      encoding: "utf8", env: { ...process.env, NODE_NO_WARNINGS: "1" }, timeout: 10_000,
    });
    assert.equal(result.status, 2, filename);
    assert.equal(result.stdout, "", filename);
    const lines = result.stderr.trim().split("\n");
    assert.equal(lines.length, 1, `${filename}: ${result.stderr}`);
    const record = JSON.parse(lines[0]);
    canonicalRecord(record);
    assert.equal(record.level, "error");
    assert.match(record.message, /usage:/);
  }
});

test("actual manual receipt wait remains silent across one thousand pending polls", () => {
  const moduleUrl = new URL("manual-review.ts", import.meta.url).href;
  const missing = new URL("../../../.work162/never-created-log-wait-fixture", import.meta.url).pathname;
  const program = `const {waitReceipt}=await import(${JSON.stringify(moduleUrl)});
    let polls=0; const result=await waitReceipt(${JSON.stringify(missing)},1000,()=>polls,async()=>{polls++;});
    if(result!==null||polls!==1000)throw Error('wait contract changed');`;
  const result = spawnSync(process.execPath, ["--input-type=module", "--eval", program], {
    encoding: "utf8", env: { ...process.env, NODE_NO_WARNINGS: "1" }, timeout: 10_000,
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, "");
  assert.equal(result.stderr, "");
});

// Narrow source-boundary evidence; owner behavioral tests establish actual GUI/wait behavior.
function rawDiagnostics(source: string, protocol = ""): boolean {
  const code = (protocol ? source.replace(protocol, " ") : source)
    .replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, " ");
  return /\bconsole\s*\.\s*(?:error|warn|info|debug|log)\s*\(|\bprocess\s*\.\s*(?:stderr|stdout)\s*\.\s*write\s*\(/.test(code);
}

test("audited runtime wrappers reject added direct diagnostics while retaining protocol stdout", () => {
  for (const filename of ["gate-report.ts", "run-report.ts", "manual-review.ts", "run-manual-review.ts", "../../release/emulator-gate.ts"]) {
    const source = readFileSync(new URL(filename, import.meta.url), "utf8");
    const protocol = filename === "gate-report.ts" ? 'process.stdout.write(gateReport(JSON.parse(readFileSync(source, "utf8")), source))' : "";
    assert.equal(rawDiagnostics(source, protocol), false, filename);
    assert.equal(rawDiagnostics(source + '\nfunction bypass() { console /* multiline */ .error("bad"); }', protocol), true);
    assert.equal(rawDiagnostics(source + '\nprocess.stderr.write("raw");', protocol), true);
    assert.equal(rawDiagnostics(source + '\nprocess.stdout.write("raw");', protocol), true);
    assert.match(source, /runtimeLogger/);
  }
  assert.equal(rawDiagnostics('// console.error("example")\nconst example="console.error(";'), false);
  const report = readFileSync(new URL("gate-report.ts", import.meta.url), "utf8");
  assert.ok(report.includes('process.stdout.write(gateReport(JSON.parse(readFileSync(source, "utf8")), source))'));
  for (const [filename, variable] of [["launcher-runtime.ts", "result"], ["phone-runtime.ts", "r"], ["outing-runtime.ts", "result"]]) {
    assert.ok(readFileSync(new URL(filename, import.meta.url), "utf8").includes(`console.log(JSON.stringify(${variable}))`), filename);
  }
});
