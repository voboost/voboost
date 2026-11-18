import { test } from "node:test";
import assert from "node:assert/strict";
import { baseline, collect, fatal, matchTombstone, preserveFailure, snapshot } from "./native-crash.ts";
const epoch = { sessionId: "a".repeat(32), pid: 1234, bootId: "12345678-1234-1234-1234-123456789abc" };
const line = "10-17 10:25:39.203 16957 16958 F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x130 in tid 16958 (Binder:16957_1), pid 16957 (cmd)";
const prefix = "pid: 16957, tid: 16958, name: Binder:16957_1 >>> /system/bin/cmd <<<\nsignal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x130\nbacktrace:\n #00 pc 123 libbinder.so\n";
const files = (...names: string[]) => names.map(basename => ({ basename, metadata: "new " + basename }));
const sample = (inventory: ReturnType<typeof files> = [], selected?: string) => ({ status: "ready", source: "native", session_id: epoch.sessionId, android_pid: epoch.pid, boot_id: epoch.bootId, inventory: { files: inventory }, crash_buffer: { excerpt: line }, ...(selected ? { tombstone: { basename: selected, excerpt: prefix, timestamp: null } } : {}) });
test("fatal parser retains exact cmd PID, Binder TID, signal, fault and timestamp", () => {
  assert.deepEqual(fatal(line), { pid: 16957, tid: 16958, signal: 11, fault: "0x130", timestamp: "10-17 10:25:39.203" });
  assert.equal(fatal("FATAL EXCEPTION: main"), null);
});
test("surfaceflinger and new fault 0x4 cannot match historical cmd0x130", () => {
  const target = fatal(line)!;
  assert.equal(matchTombstone(prefix.replace("0x130", "0x4"), target).identity, false);
  assert.equal(matchTombstone(prefix.replace("16957", "1646").replace("16958", "1646").replace("11 (SIGSEGV)", "6 (SIGABRT)"), target).identity, false);
  assert.deepEqual(matchTombstone(prefix, target), { identity: true, timestamp: null, confirmed: false, reason: "identity candidate only; comparable timestamp unavailable or differs", bootAssociation: "unknown" });
});
for (const key of ["session_id", "android_pid", "boot_id"] as const) test("rejects changed epoch " + key, () => {
  assert.throws(() => snapshot({ ...sample(), [key]: key === "android_pid" ? 999 : "bad" }, epoch), /epoch/);
});
for (const name of ["../tombstone_00", "tombstone_100", "tombstone_0", "tombstone_00.bak"]) test("rejects inventory basename " + name, () => assert.throws(() => snapshot(sample(files(name)), epoch), /inventory/));
test("rejects oversized and duplicate inventory and wrong selected filename", () => {
  assert.throws(() => snapshot(sample(files("tombstone_00", "tombstone_00")), epoch), /inventory/);
  assert.throws(() => snapshot({ ...sample(), crash_buffer: { excerpt: "a".repeat(32769) } }, epoch), /bounds/);
  assert.throws(() => snapshot(sample([], "tombstone_01"), epoch, "tombstone_00"), /selected/);
});
test("baseline API failure is diagnostic-only", async () => {
  assert.match((await baseline(async () => { throw new Error("offline"); }, epoch)).error!, /offline/);
});
test("only new/changed prefixes are read, then stop at matching identity", async () => {
  const calls: (string | undefined)[] = [];
  const before = { snapshot: snapshot(sample(files("tombstone_00")), epoch) };
  const result = await collect(async (_, name) => { calls.push(name); return sample(files("tombstone_00", "tombstone_01", "tombstone_02"), name); }, epoch, before, [line]);
  assert.deepEqual(calls, [undefined, "tombstone_01"]);
  assert.equal(result.matching, "tombstone_01");
  assert.deepEqual(result.unread, ["tombstone_02"]);
  assert.equal(result.truncated, true);
  assert.equal(result.prefixes[0].matches[0].confirmed, false);
});
test("unchanged historical surfaceflinger inventory is never read", async () => {
  let calls = 0;
  const value = sample(files("tombstone_00"));
  const result = await collect(async (_, name) => { calls++; assert.equal(name, undefined); return value; }, epoch, { snapshot: snapshot(value, epoch) }, [line]);
  assert.equal(calls, 1); assert.equal(result.prefixes.length, 0);
});
test("maximum4 prefix reads preserve remaining names", async () => {
  const inventory = files(...Array.from({ length: 6 }, (_, i) => "tombstone_0" + i));
  const result = await collect(async (_, name) => { const value = sample(inventory, name); if (value.tombstone) value.tombstone.excerpt = "surfaceflinger"; return value; }, epoch, { snapshot: snapshot(sample(), epoch) }, [line]);
  assert.equal(result.prefixes.length, 4); assert.deepEqual(result.unread, ["tombstone_04", "tombstone_05"]);
});
test("missing baseline saves current source without treating retained files as new", async () => {
  const result = await collect(async () => sample(files("tombstone_00")), epoch, { error: "offline baseline" }, [line]);
  assert.equal(result.snapshots.length, 1); assert.equal(result.prefixes.length, 0); assert.match(result.errors[0], /baseline unavailable/);
});
test("malformed or changed epoch aborts collection but preserves exact failure", async () => {
  const failure = new Error("original fresh fatal");
  const result = await collect(async () => ({ ...sample(), boot_id: "changed" }), epoch, { snapshot: snapshot(sample(), epoch) }, [line]);
  assert.match(result.errors[0], /epoch differs/);
  assert.equal(await preserveFailure(failure, async () => { throw new Error("diagnostic persistence failed"); }), failure);
});
test("30second deadline stops new requests without mutation replay", async () => {
  let clock = 0, calls = 0;
  const result = await collect(async () => { calls++; clock = 30001; return sample(files("tombstone_00")); }, epoch, { snapshot: snapshot(sample(), epoch) }, [line], () => clock);
  assert.equal(calls, 1); assert.match(result.errors[0], /after collection deadline/);
});

test("native-only fresh fatal is retained when UI fails before the final inspect", async () => {
  const before = snapshot({ ...sample(), crash_buffer: { excerpt: "" } }, epoch);
  const result = await collect(async (_, name) => sample(files("tombstone_01"), name), epoch, { snapshot: before }, []);
  assert.deepEqual(result.targets, [fatal(line)]);
  assert.equal(result.matching, "tombstone_01");
  assert.equal(result.prefixes[0].matches[0].confirmed, false);
});
test("retained baseline fatal is not promoted to a fresh native target", async () => {
  const before = snapshot(sample(), epoch);
  const result = await collect(async () => sample(), epoch, { snapshot: before }, []);
  assert.deepEqual(result.targets, []);
});
