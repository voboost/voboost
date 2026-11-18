import test from "node:test";
import assert from "node:assert/strict";
import {mkdirSync, mkdtempSync, writeFileSync, symlinkSync, rmSync} from "node:fs";
import {join} from "node:path";
import {fileURLToPath} from "node:url";
import {validateConfig} from "../../release/emulator-gate.ts";

function fixture() {
  const scratch = process.env.TMPDIR ?? fileURLToPath(new URL("../../build/test-fixtures/", import.meta.url));
  mkdirSync(scratch, {recursive: true});
  const root = mkdtempSync(join(scratch, "reviewed-keyboard-config-"));
  const directory = (name: string) => {const p = join(root, name); mkdirSync(p, {recursive: true}); return p;};
  const file = (name: string, body = "fixture", mode = 0o600) => {const p = join(root, name); writeFileSync(p, body, {mode}); return p;};
  const packageRoot = directory("package");
  directory("package/Voboost Installer.app/Contents/MacOS");
  const executable = file("package/Voboost Installer.app/Contents/MacOS/voboost-installer");
  symlinkSync("Voboost Installer.app/Contents/MacOS/voboost-installer", join(packageRoot, "voboost-installer"));
  const receiptFile = file("receipt.json", JSON.stringify({schema: 2, ok: true, result: {profile: "fixture", session: {id: "a".repeat(32), ownership_id: "b".repeat(32)}}}));
  const config = {workspaceRoot: directory("workspace"), voyemuRoot: directory("voyemu"), voyemuProfile: "fixture", nativeSession: {executable, receiptFile}, closure: directory("closure"), package: packageRoot, dmg: file("candidate.dmg"), fixture: null, output: join(root, "output")};
  return {root, config, file, close: () => rmSync(root, {recursive: true, force: true})};
}

test("native gate admits explicitly supplied reviewed keyboard map file", () => {
  const f = fixture();
  try {
    const maps = f.file("reviewed.json", "{}");
    assert.equal(validateConfig({...f.config, reviewedKeyboardMaps: maps}).reviewedKeyboardMaps, maps);
    assert.equal(validateConfig(f.config).reviewedKeyboardMaps, undefined);
  } finally {f.close();}
});

test("reviewed keyboard map path must be absolute and cannot be a symlink", () => {
  const f = fixture();
  try {
    assert.throws(() => validateConfig({...f.config, reviewedKeyboardMaps: "reviewed.json"}), /absolute/);
    const maps = f.file("reviewed.json", "{}");
    const link = join(f.root, "map-link.json"); symlinkSync(maps, link);
    assert.throws(() => validateConfig({...f.config, reviewedKeyboardMaps: link}), /unsafe file identity/);
  } finally {f.close();}
});

test("keyboard map admission preserves unknown config field rejection", () => {
  const f = fixture();
  try {assert.throws(() => validateConfig({...f.config, reviewedKeyboardMaps: f.file("reviewed.json", "{}"), guessedKeyboardMaps: true}), /unknown or missing fields/);}
  finally {f.close();}
});
