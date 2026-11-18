import assert from "node:assert/strict";
import test from "node:test";
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { owningHostReply, fixture, reply } from "./emulator-gate-fixture.ts";
import {
  parseVoyemuStatus,
  prepared,
  runGate,
  voyemuInvocation,
  type Invocation,
} from "../../release/emulator-gate.ts";

test("Voyemu running lease rejects stopped unprepared malformed and foreign identities", () => {
  const c = fixture();
  assert.deepEqual(
    parseVoyemuStatus(
      reply(c, "status", {
        profiles: { [c.voyemuProfile]: { prepared: true, running: true, port: 5562, pid: 333 } },
      }),
      c.voyemuProfile,
    ),
    { port: 5562, pid: 333 },
  );
  for (const state of [
    { prepared: true, running: false },
    { prepared: false, running: true, port: 5562, pid: 333 },
    { prepared: true, running: true, port: 5563, pid: 333 },
    { prepared: true, running: true, port: 5562, pid: 0 },
  ])
    assert.throws(() =>
      parseVoyemuStatus(
        reply(c, "status", { profiles: { [c.voyemuProfile]: state } }),
        c.voyemuProfile,
      ),
    );
  assert.deepEqual(voyemuInvocation(c, "verify").args, [
    join(c.voyemuRoot, "src/cli.ts"),
    "--root",
    c.voyemuRoot,
    "--json",
    "verify",
    c.voyemuProfile,
  ]);
});
test("runtime replacement before product action prevents mutation and never stops borrowed machine", async () => {
  const c = fixture(),
    calls: Invocation[] = [];
  let status = 0;
  const e = await runGate(c, async (inv) => {
    if (inv.host) return owningHostReply(c, inv)!;
    calls.push(inv);
    if (inv.args[0] === "--verify-package")
      return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    if (inv.voyemu) {
      const cmd = inv.args[4];
      return reply(
        c,
        cmd,
        cmd === "status"
          ? {
              profiles: {
                [c.voyemuProfile]: {
                  prepared: true,
                  running: true,
                  port: 5562,
                  pid: ++status === 1 ? 333 : 444,
                },
              },
            }
          : { checks: prepared(c).checks },
      );
    }
    throw new Error("unexpected product mutation");
  });
  assert.equal(e.passed, false);
  assert.match(e.preparation.reason, /lease identity/);
  assert.ok(e.cases.every((c: any) => c.status === "not-run"));
  assert.ok(!calls.some((c) => c.args[0] === "--install" || c.args[4] === "stop"));
});
test("changed immutable package bytes prevent the next product mutation", async () => {
  const c = fixture(),
    calls: Invocation[] = [];
  const e = await runGate(c, async (inv) => {
    if (inv.host) return owningHostReply(c, inv)!;
    calls.push(inv);
    if (inv.args[0] === "--verify-package") {
      writeFileSync(
        join(c.package, "Voboost Installer.app/Contents/Resources/voboost.json"),
        "changed",
      );
      return { exitCode: 0, stdout: "1.0.0-beta1: exact offline closure verified\n", stderr: "" };
    }
    return reply(c, "status", {
      profiles: { [c.voyemuProfile]: { prepared: true, running: true, port: 5562, pid: 333 } },
    });
  });
  assert.equal(e.passed, false);
  assert.match(e.preparation.reason, /immutable/);
  assert.ok(e.cases.every((c: any) => c.status === "not-run"));
  assert.ok(!calls.some((c) => c.args[0] === "--install"));
});
