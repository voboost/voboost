import assert from "node:assert/strict";
import test from "node:test";
import { inspectionResident } from "./functional-observations.ts";

test("inspection selects the resident file and ignores other pretty-printed JSON", () => {
  const body = 'bootId=fixture\nresident-status:\n/data/voboost/status.json\n{"schemaVersion":1,"injections":[{"documentGeneration":9007199254740993}]}\n\n/data/voboost/uninstall/policy.meta.json\n{\n  "injections": "unrelated policy metadata"\n}\nprofile-inputs:\n';
  assert.equal(inspectionResident(body).injections[0].documentGeneration, "9007199254740993");
  assert.throws(() => inspectionResident(body + body), /duplicated/);
  assert.throws(() => inspectionResident(body.replace('"schemaVersion":1', '"schemaVersion":2')), /document differs/);
  assert.throws(() => inspectionResident(body.replace(']}\n', ']\n')), SyntaxError);
  assert.throws(() => inspectionResident('{"schemaVersion":1,"injections":[]}'), /section missing/);
});
