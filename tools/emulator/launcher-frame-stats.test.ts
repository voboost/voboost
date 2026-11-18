import assert from "node:assert/strict";
import test from "node:test";
import { parseFrameStats } from "./launcher-frame-stats.ts";

/**
 * Android 9 framestats PROFILEDATA block (AOSP column names, trailing empty CSV field).
 * NOTE: on Android 9 the SAME marker `---PROFILEDATA---` both opens and closes a block;
 * there is no ---ENDPROFILEDATA--- (verified against
 * plans/2026-10-24/runtime305/launcher-gfxinfo-original298.txt).
 */
function section(header: string[], rows: string[][]): string {
  return [("---PROFILEDATA---"), header.join(","), ...rows.map(r => r.join(",")), "---PROFILEDATA---"].join("\n");
}

const header = [
  "Flags", "IntendedVsync", "Vsync", "OldestInputEvent", "NewestInputEvent",
  "HandleInputStart", "AnimationStart", "PerformTraversalsStart", "DrawStart",
  "SyncQueued", "SyncStart", "IssueDrawCommandsStart", "SwapBuffers", "FrameCompleted",
];

const good = [
  [0, 1000000000, 1000000001, 9223372036854775807, 0, 1016000000, 1016000100, 1016000200, 1016000300, 1016000400, 1016000500, 1016000600, 1016000700, 1016000000], // 16.0 ms
  [0, 1016700000, 1016700001, 9223372036854775807, 0, 1031000000, 1031000100, 1031000200, 1031000300, 1031000400, 1031000500, 1031000600, 1031000700, 1031000000], // 14.3 ms
  [0, 1033400000, 1310000000, 9223372036854775807, 0, 1050000000, 1100000000, 1150000000, 1200000000, 1250000000, 1300000000, 1310000000, 1313000000, 1220000000], // 186.6 ms slow
  [0, 1050100000, 1050100001, 9223372036854775807, 0, 1062000000, 1062000100, 1062000200, 1062000300, 1062000400, 1062000500, 1062000600, 1062000700, 1062000000], // 11.9 ms
];

test("latencyNumbers are ns/1e6: line 1 = 16.0 ms, not 16000000", () => {
  const s = parseFrameStats(section(header, good)).sections[0];
  assert.ok(Math.abs(s.latencyMs!.max - 186.6) < 1e-9);
  assert.ok(Math.abs(s.latencyMs!.median - 14.3) < 1e-9); // nearest-rank n=4 -> 2nd value
  assert.ok(Math.abs(s.latencyMs!.p99 - 186.6) < 1e-9);
  assert.ok(Math.abs(s.latencyMs!.p95 - 186.6) < 1e-9);
});

test("slow-frame compares ms against refreshPeriodNs/1e6 (units regression)", () => {
  // refreshPeriodNs 16666667 -> 16.666667 ms; only 186.6 ms frame is slow.
  const s = parseFrameStats(section(header, good), { refreshPeriodNs: 16666667n }).sections[0];
  assert.equal(s.slowFrameCount, 1);
  assert.ok(Math.abs(s.slowFrameShare! - 0.25) < 1e-12);
  assert.equal(parseFrameStats(section(header, good), { refreshPeriodNs: 200000000n }).sections[0].slowFrameCount, 0);
  assert.equal(parseFrameStats(section(header, good)).sections[0].slowFrameCount, null); // no fabricated 60Hz default
});

test("aggregates HWUI completion latency with nonzero-flag exclusions", () => {
  const dump = section(header, [...good, [1, 1066800000, 1066800001, 9223372036854775807, 0, 1090000000, 1090000100, 1090000200, 1090000300, 1090000400, 1090000500, 1090000600, 1090000700, 1090000000]]);
  const r = parseFrameStats(dump);
  assert.equal(r.sections.length, 1);
  const s = r.sections[0];
  assert.equal(s.nonzeroFlagCount, 1);
  assert.equal(s.validFrameCount, 4);
  assert.equal(s.rows.length, 4);
  assert.equal(s.rowCount, 5);
  assert.equal(s.slowFrameCount, null);
  assert.equal(s.slowFrameShare, null);
  assert.equal(r.frameCompletionRate, null);
});

test("Android 9 marker: BOTH delimiters are ---PROFILEDATA--- (no ENDPROFILEDATA)", () => {
  // Two windows, real-capture shape: each window has a separate opening and closing marker.
  const dump = ["---PROFILEDATA---", header.join(","),
    "0,1000000000,1000000001,9223372036854775807,0,1016000000,1016000100,1016000200,1016000300,1016000400,1016000500,1016000600,1016000700,1016000000",
    "0,1016700000,1016700001,9223372036854775807,0,1031000000,1031000100,1031000200,1031000300,1031000400,1031000500,1031000600,1031000700,1031000000",
    "---PROFILEDATA---",
    "---PROFILEDATA---", header.join(","),
    "0,3000000000,3000000001,9223372036854775807,0,3016000000,3016000100,3016000200,3016000300,3016000400,3016000500,3016000600,3016000700,3025000000", // 25 ms
    "---PROFILEDATA---"].join("\n");
  const r = parseFrameStats(dump);
  assert.equal(r.sections.length, 2); // merged-at-EOF parser bug regression
  assert.equal(r.sections[0].validFrameCount, 2);
  assert.equal(r.sections[1].validFrameCount, 1);
  assert.ok(Math.abs(r.sections[0].latencyMs!.max - 16.0) < 1e-9);
  assert.ok(Math.abs(r.sections[1].latencyMs!.max - 25.0) < 1e-9);
  const partial = parseFrameStats(section(header, good)); // includes trailing closing marker
  assert.equal(partial.sections.length, 1);
  assert.equal(partial.sections[0].validFrameCount, 4);
});

test("reordered and extra columns resolve via header names", () => {
  const h2 = ["FrameCompleted", "Flags", "IntendedVsync", "SurfaceFlingerLatency"];
  const rows = [[1016000000, 0, 1000000000, "x"], [1031000000, 0, 1016700000, "y"]];
  const s = parseFrameStats(section(h2, rows)).sections[0];
  assert.equal(s.validFrameCount, 2);
  assert.equal(s.rows[0].intendedVsyncNs, 1000000000n);
  assert.equal(s.rows[0].frameCompletedNs, 1016000000n);
  assert.ok(Math.abs(s.latencyMs!.max - 16.0) < 1e-9);
});

test("zero / malformed / negative / missing / completed<intended timestamps are invalid", () => {
  const rows = [
    [0, 1000000000, 1000000000, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000000100], // 0.0001 ms valid
    [0, 0, 0, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000000000], // zero IntendedVsync = uninitialized -> invalid
    [0, 1040000000, 1033400000, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1030000000], // completed<intended -> invalid
    [0, "not-a-number", 1050000000, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1050000000], // malformed
    [0, "-5", 1060000000, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1060000000], // negative -> invalid
    [0, "", 1070000000, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1070000000], // missing -> invalid
    [0, 1080000000, 0, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], // zero FrameCompleted -> invalid
  ];
  const s = parseFrameStats(section(header, rows)).sections[0];
  assert.equal(s.validFrameCount, 1);
  assert.equal(s.invalidTimestampCount, 6);
  assert.ok(Math.abs(s.latencyMs!.max - 1e-4) < 1e-12);
});

test("nanosecond precision survives beyond 2^53 where double math would fail", () => {
  const rows = [[0, "9007199254740993", 0, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, "9007199254740996"]];
  const s = parseFrameStats(section(header, rows)).sections[0];
  assert.equal(s.validFrameCount, 1);
  assert.ok(Math.abs(s.latencyMs!.max - 3e-6) < 1e-12); // 3 ns latency, lost with Number subtraction
});

test("refreshPeriodNs validation and empty section", () => {
  const s = parseFrameStats(section(header, [])).sections[0];
  assert.equal(s.validFrameCount, 0);
  assert.equal(s.nonzeroFlagCount, 0);
  assert.equal(s.latencyMs, null);
  assert.throws(() => parseFrameStats("Graphics info for pid 1234\n** No recent data **"), /no PROFILEDATA/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: 0 }), /refreshPeriodNs/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: -100 }), /refreshPeriodNs/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: 1.5 }), /refreshPeriodNs/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: NaN }), /refreshPeriodNs/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: 1e19 }), /refreshPeriodNs/);
  assert.throws(() => parseFrameStats(section(header, good), { refreshPeriodNs: 10000000000000000000n }), /refreshPeriodNs/);
  assert.equal(parseFrameStats(section(header, good), { refreshPeriodNs: 16666667 }).sections[0].slowFrameCount, 1);
});

test("missing required header column is rejected", () => {
  assert.throws(() => parseFrameStats(section(["Flags", "Vsync"], [[0, 1]])), /IntendedVsync/);
});

test("frameCompletionRate needs explicit window, >=2 strictly monotonic rows, explicit single section", () => {
  const base = parseFrameStats(section(header, good.slice(0, 3)), { measurementWindowMs: 250 });
  assert.notEqual(base.frameCompletionRate, null); // 4 rows strictly increasing FrameCompleted
  assert.equal(base.frameCompletionRate!.source, "HWUI");
  assert.ok(Math.abs(base.frameCompletionRate!.framesPerSecond - (3 / 250) * 1000) < 1e-9);

  assert.throws(() => parseFrameStats(section(header, good), { measurementWindowMs: 0 }), /measurementWindowMs/);
  assert.throws(() => parseFrameStats(section(header, good), { measurementWindowMs: -5 }), /measurementWindowMs/);
  assert.throws(() => parseFrameStats(section(header, good), { measurementWindowMs: Infinity }), /measurementWindowMs/);
  assert.throws(() => parseFrameStats(section(header, good), { measurementWindowMs: NaN }), /measurementWindowMs/);

  const single = [[0, 1000000000, 1000000001, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000000000]];
  assert.equal(parseFrameStats(section(header, single), { measurementWindowMs: 100 }).frameCompletionRate, null); // 1 valid row

  const backwards = [
    [0, 1000000000, 1000000001, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2000000000],
    [0, 2000000000, 2000000001, 9223372036854775807, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000000000],
  ];
  assert.equal(parseFrameStats(section(header, backwards), { measurementWindowMs: 50 }).frameCompletionRate, null); // not monotonic
});

test("multi-window rate: never silently from first section (regression)", () => {
  const twoWindows = section(header, good.slice(0, 3)) + "\nWindow: second\n" + section(header, good.slice(0, 2));
  // No rateSectionIndex with multiple sections -> null even when a window is given.
  const silent = parseFrameStats(twoWindows, { measurementWindowMs: 100 });
  assert.equal(silent.sections.length, 2);
  assert.equal(silent.frameCompletionRate, null);
  // Explicit section selection controls which window is measured.
  const first = parseFrameStats(twoWindows, { measurementWindowMs: 250, rateSectionIndex: 0 });
  assert.notEqual(first.frameCompletionRate, null);
  assert.ok(Math.abs(first.frameCompletionRate!.framesPerSecond - (3 / 250) * 1000) < 1e-9);
  const second = parseFrameStats(twoWindows, { measurementWindowMs: 100, rateSectionIndex: 1 });
  assert.notEqual(second.frameCompletionRate, null);
  assert.ok(Math.abs(second.frameCompletionRate!.framesPerSecond - (2 / 100) * 1000) < 1e-9);
  assert.throws(() => parseFrameStats(twoWindows, { measurementWindowMs: 100, rateSectionIndex: 2 }), /out of range/);
  assert.throws(() => parseFrameStats(twoWindows, { measurementWindowMs: 100, rateSectionIndex: -1 }), /out of range/);
});

 test("truncated blocks and impossible measurement windows are rejected", () => {
  assert.throws(() => parseFrameStats(section(header, good).replace(/\n---PROFILEDATA---$/, "")), /unterminated/);
  assert.throws(() => parseFrameStats(section(header, good.slice(0, 2)), { measurementWindowMs: 1 }), /span/);
});

test("window names preserve primary/secondary identity instead of mixing navigation bars", () => {
  const dump = "Window: MainScreenNavigationBar\n" + section(header, good.slice(0, 2))
    + "\nWindow: MainActivity\n" + section(header, good.slice(0, 2));
  const result = parseFrameStats(dump);
  assert.deepEqual(result.sections.map(s => s.window), ["MainScreenNavigationBar", "MainActivity"]);
  assert.equal(parseFrameStats(section(header, good)).sections[0].window, null);
});
