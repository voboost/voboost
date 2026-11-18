import { basename, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";
import type { Invocation } from "../../release/emulator-gate.ts";

export function driverExecutable(): string {
  return process.env.VOBOOST_EMULATOR_DRIVER ??
    fileURLToPath(new URL("../../../.build-cache/installer-desktop-cargo/aarch64-apple-darwin/release/emulator-driver", import.meta.url));
}

export function driverInvocation(candidate: string, request: string, serial: string,
    options: Omit<Invocation, "executable" | "args">): Invocation {
  const invocation = { ...options, executable: driverExecutable(),
    args: ["--emulator-check", request, "--device", serial, "--candidate", candidate] };
  if (!isDriverInvocation(invocation)) throw new Error("an exact host driver, candidate and emulator request are required");
  return invocation;
}

export function isDriverInvocation(invocation: Invocation): boolean {
  if (invocation.host || invocation.voyemu) return false;
  const args = invocation.args;
  if (!isAbsolute(invocation.executable) || basename(invocation.executable) !== "emulator-driver"
      || args.length !== 6 || args[0] !== "--emulator-check" || args[2] !== "--device"
      || args[4] !== "--candidate" || !isAbsolute(args[1]) || !isAbsolute(args[5])
      || basename(args[5]) !== "voboost-installer" || !/^emulator-[1-9][0-9]{3,4}$/.test(args[3])) return false;
  const port = Number(args[3].slice("emulator-".length));
  return port >= 1024 && port <= 65534 && port % 2 === 0;
}

