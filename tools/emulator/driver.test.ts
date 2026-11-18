import test from "node:test";
import assert from "node:assert/strict";
import { driverInvocation, isDriverInvocation } from "./driver.ts";

test("test actions bind the host tool, exact emulator and ordinary candidate", () => {
  const call = driverInvocation("/package/voboost-installer", "/work/request.json", "emulator-5588", { timeoutMs: 1000 });
  assert.equal(call.executable.endsWith("/emulator-driver"), true);
  assert.deepEqual(call.args, ["--emulator-check", "/work/request.json", "--device", "emulator-5588", "--candidate", "/package/voboost-installer"]);
  for (const serial of ["emulator-1024", "emulator-65534"])
    assert.equal(isDriverInvocation(driverInvocation("/package/voboost-installer", "/work/request.json", serial, { timeoutMs: 1000 })), true);
});

test("USB, noncanonical ports and authority extensions never reach the host tool", () => {
  for (const serial of ["1234567", "", "127.0.0.1:5555", "emulator-0", "emulator-05588", "emulator-5589", "emulator-65535", "emulator-65536"])
    assert.throws(() => driverInvocation("/package/voboost-installer", "/work/request.json", serial, { timeoutMs: 1000 }));
  const good = driverInvocation("/package/voboost-installer", "/work/request.json", "emulator-5588", { timeoutMs: 1000 });
  for (const value of [{ ...good, host: true }, { ...good, voyemu: {} },
      { ...good, executable: "/package/voboost-installer" },
      { ...good, args: [...good.args, "--adb-server-port", "25588"] },
      { ...good, args: good.args.map(arg => arg === "--device" ? "--device-serial" : arg) }])
    assert.equal(isDriverInvocation(value as typeof good), false);
});

