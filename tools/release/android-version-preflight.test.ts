import { readFileSync } from "node:fs";
import { strictEqual, throws } from "node:assert";
import test from "node:test";
import { requireAndroidVersionCodeAgreement } from "./android-version-preflight.ts";
test("Android source preflight admits matching counter and rejects the observed cross-owner mismatch", () => {
  requireAndroidVersionCodeAgreement(
    "val appVersionCode = 1_000_009\n",
    1000009,
  );
  throws(
    () =>
      requireAndroidVersionCodeAgreement(
        "val appVersionCode = 1_000_009\n",
        1000008,
      ),
    /1000009 differs.*1000008/,
  );
});
test("Android preflight fails closed for ambiguous, dynamic and invalid source declarations", () => {
  for (const source of [
    "",
    "val appVersionCode = nextVersion()\n",
    "val appVersionCode = 0\n",
    "val appVersionCode = 2_100_000_001\n",
    "val appVersionCode = 1\nval appVersionCode = 1\n",
  ])
    throws(() => requireAndroidVersionCodeAgreement(source, 1000009));
});
test("Constructor checks source agreement before release helpers and component builds", () => {
  const source = readFileSync(
    new URL("./build-closure.ts", import.meta.url),
    "utf8",
  );
  const start = source.indexOf("const identity = readReleaseIdentity");
  const preflight = source.indexOf(
    "requireAndroidVersionCodeAgreement(",
    start,
  );
  const releaseHelper = source.indexOf("await releaseCli(", start);
  const observation = source.indexOf("src/build/observation-helper.ts", start);
  strictEqual(
    start >= 0 &&
      preflight > start &&
      preflight < releaseHelper &&
      preflight < observation,
    true,
  );
});
test("current product definition snapshots agree with the app owner", () => {
  const current = readFileSync(
    new URL("../../release/version.toml", import.meta.url),
    "utf8",
  );
  const snapshot = readFileSync(
    new URL("../../release/1.0.0-beta1.toml", import.meta.url),
    "utf8",
  );
  strictEqual(current, snapshot);
  const code = /^\[android\]\nversionCode = ([0-9]+)$/mu.exec(current);
  strictEqual(code !== null, true);
  const app = readFileSync(
    new URL("../../../voboost-app/build.gradle.kts", import.meta.url),
    "utf8",
  );
  requireAndroidVersionCodeAgreement(app, Number(code![1]));
});
