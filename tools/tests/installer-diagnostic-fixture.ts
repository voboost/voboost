/** Matches the installer runtime_log::Record serializer; no native execution claim. */
export function installerLog(message: string, level = "error", sequence = 3): string {
  return JSON.stringify({ schemaVersion: 1, sequence, timestamp: "2026-10-26T07:00:00.123Z",
    source: { kind: "installer", id: "desktop" }, level, component: "installer", message, fields: {} }) + "\n";
}

export function installerFailure(stderr: string, serial?: string) {
  return { exitCode: 1, stdout: "", stderr: (serial ? installerLog("Connecting to the head unit over USB", "info", 1) +
    installerLog(`Connected to the head unit: ${serial}`, "debug", 2) + installerLog("Connected to the head unit", "info", 3) : "") + installerLog(stderr.replace(/\n$/, ""), "error", 4) };
}
