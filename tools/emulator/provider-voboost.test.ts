import assert from "node:assert/strict";
import { test } from "node:test";
import { collect, validateRequest, type CollectRequest } from "./provider-voboost.ts";
const request: CollectRequest = { schema: 2, op: "collect", request_id: "test-1", source_id: "voboost", session: { id: "session-1", profile: "android", guest_serial: "emulator-5554", boot_id: "11111111-1111-1111-1111-111111111111" }, capabilities: { installer: { path: "/official/installer", sha256: "a".repeat(64) } } };
const observation = { schema: 2, source: "voboost", status: "ready", boot_id: request.session.boot_id, revision: "b".repeat(64), data: { root: "present", files: { config: { status: "present", data: { startupMode: "none" } } }, runtime_current: "unavailable" } };
test("none invokes only official passive CLI and retains persistent provenance", () => {
  let calls = 0;
  const result = collect(request, (path, args, options) => { calls++; assert.equal(path, "/official/installer"); assert.deepEqual(args, ["--observe", "--device", "emulator-5554"]); assert.equal(options.shell, false); assert.equal(options.timeout, 15000); return { status: 0, stdout: JSON.stringify(observation) }; }, () => true);
  assert.equal(calls, 1); assert.equal(result.status, "ready"); assert.deepEqual((result as any).data, observation.data);
});
test("digest rejection never starts a child", () => { const result = collect(request, () => { throw new Error("must not spawn"); }, () => false); assert.equal(result.status, "unavailable"); });
test("stale boot and bounded malformed output remain unavailable", () => {
  for (const stdout of [JSON.stringify({ ...observation, boot_id: "22222222-2222-2222-2222-222222222222" }), "x".repeat(1_200_001), "not json"]) assert.equal(collect(request, () => ({ status: 0, stdout }), () => true).status, "unavailable");
});
test("unknown fields and mutable authority are rejected", () => {
  for (const extra of [{ command: "adb" }, { op: "install" }, { capabilities: { installer: request.capabilities.installer, shell: true } }]) assert.throws(() => validateRequest({ ...request, ...extra }));
});

test("provider forwards only the canonical runtime device", () => {
  for (const serial of ["emulator-5554", "emulator-5588", "physical-usb-device"]) {
    const input = { ...request, session: { ...request.session, guest_serial: serial } };
    let calls = 0;
    const result = collect(input, (_path, args) => {
      calls++;
      assert.deepEqual(args, ["--observe", "--device", serial]);
      return { status: 0, stdout: JSON.stringify(observation) };
    }, () => true);
    assert.equal(calls, 1);
    assert.equal(result.status, "ready");
  }
});
