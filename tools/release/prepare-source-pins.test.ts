import { strictEqual, throws } from "node:assert";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { parseReleaseDefinition } from "./release-identity.ts";
import { replaceSourcePins } from "./prepare-source-pins.ts";

const source = readFileSync(new URL("../../release/version.toml", import.meta.url), "utf8");
const pins = { app: "1".repeat(40), agents: "2".repeat(40), inject: "3".repeat(40),
  installer: "4".repeat(40), logo: "6".repeat(40) };

test("updates five component commits while preserving the release and all other inputs", () => {
  const before = parseReleaseDefinition(source);
  const result = replaceSourcePins(source, pins);
  const after = parseReleaseDefinition(result);
  strictEqual(JSON.stringify({ ...before, repositories: { ...before.repositories, ...pins } }), JSON.stringify(after));
  strictEqual(replaceSourcePins(result, pins), result);
});

test("rejects abbreviated commits and duplicate pin lines", () => {
  throws(() => replaceSourcePins(source, { ...pins, app: "1234567" }), /Invalid app commit/);
  throws(() => replaceSourcePins(source.replace(/app = "[0-9a-f]{40}"/, `app = "${pins.app}"\napp = "${pins.app}"`), pins));
});
