import assert from "node:assert/strict";
import test from "node:test";
import { parseInstallerLogs } from "../emulator/installer-logs.ts";
import { logArchive } from "./installer-log-fixture.ts";

const identity = { serial: "emulator-5562", version: "1.0.0-beta1", manifestSha256: "a".repeat(64) };
const diagnostic = { agent: "vehicle-drive-forced-ev", event: "acc.observe.changed", outcome: "observed", fields: { source: "callback", value: 0, off_generation: 1 } };

// Real 33 MiB archive: 2800 canonical rows x 12 KiB messages.
// Uncompressed records.jsonl = 2800 * (12 KiB) + per-row overhead > old 32 MiB limit, far below new 400 MiB limit.
const rows = Array.from({ length: 2800 }, (_, index) => {
  const padded = JSON.stringify({ ...diagnostic, blob: "x".repeat(12 * 1024 - JSON.stringify(diagnostic).length - 12) });
  return {
    schemaVersion: 1,
    sequence: index + 1,
    timestamp: "2026-10-08T10:00:00Z",
    source: { kind: "agent", id: diagnostic.agent },
    level: "info",
    component: "agent",
    message: "VOBDBG1 " + padded,
    fields: {},
  };
});

test("valid 33 MiB tar.xz archive between old 32 MiB and new 400 MiB limits parses successfully", () => {
  const archive = logArchive(identity, rows);
  const result = parseInstallerLogs(archive, identity);
  assert.equal(result.records.length, 2800);
  assert.equal(result.records[2799].sequence, 2800);
  assert.equal(result.records[0].sequence, 1);
  assert.ok(archive.length < 1024 * 1024, "compressed fixture stays small on disk");
});
