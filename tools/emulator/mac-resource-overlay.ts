import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { lstatSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { MAC_LICENSE_NAMES, verifyReadableLicenses } from "../../../voboost-installer/src/build/readable-licenses.ts";

/** Admit only the owning packager's exact, byte-verified Mac license overlay. */
export function macDeviceResourceInventory<T extends { path: string }>(directory: string, rows: T[]): T[] {
  verifyReadableLicenses(directory, MAC_LICENSE_NAMES);
  for (const name of MAC_LICENSE_NAMES) {
    const metadata = lstatSync(join(directory, name));
    if ((metadata.mode & 0o7777) !== 0o644)
      throw new Error(`Packaged readable license mode differs: ${name}`);
  }
  const excluded = new Set<string>(["adb", "ADB-LICENSE.txt", ...MAC_LICENSE_NAMES]);
  return rows.filter(row => !excluded.has(row.path));
}

/** Bind admission implementation and the owning packager's exact source bytes. */
export function macResourceOverlaySourceIdentity(): string {
  const inputs = [
    fileURLToPath(import.meta.url),
    resolve(import.meta.dirname, "../../../voboost-installer/src/build/readable-licenses.ts"),
    ...MAC_LICENSE_NAMES.map(name => resolve(import.meta.dirname, "../../../voboost-installer/src-tauri/resources/licenses", name)),
  ];
  return createHash("sha256").update(JSON.stringify(inputs.map(file => {
    const st = lstatSync(file);
    if (!st.isFile() || st.isSymbolicLink()) throw new Error("Mac overlay source must be a regular file");
    return { file, mode: st.mode & 0o7777, sha256: createHash("sha256").update(readFileSync(file)).digest("hex") };
  }))).digest("hex");
}
