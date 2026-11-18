import test from "node:test";
import { strictEqual, throws } from "node:assert";
import { oemTargetPid } from "./process-observations.ts";

const body = "processes:\nPID NAME\n41 voboost-daemon\n\ncontexts:\nprivate context data\n\noem-target-processes:\nPID NAME\n5921 com.qinggan.app.setting\n5930 com.qinggan.systemservice\n6000 com.pateonavi.naviapp\n\nandroid-art-fatal-context:\nretention=bounded\n";
test("OEM targets come from the actual Inspect OEM section, preserving the Voboost-only list", () => {
  strictEqual(oemTargetPid(body, "com.qinggan.app.setting"), "5921");
  strictEqual(oemTargetPid(body, "com.qinggan.systemservice"), "5930");
  strictEqual(oemTargetPid(body, "com.pateonavi.naviapp"), "6000");
  throws(() => oemTargetPid(body, "voboost-daemon"), /missing/);
});
test("OEM target authority rejects malformed, missing and ambiguous inventories", () => {
  throws(() => oemTargetPid(body + body, "com.pateonavi.naviapp"), /ambiguous/);
  throws(() => oemTargetPid(body.replace("6000 com.pateonavi.naviapp", "bad com.pateonavi.naviapp"), "com.pateonavi.naviapp"), /malformed/);
  throws(() => oemTargetPid(body.replace("6000 com.pateonavi.naviapp", "6000 com.pateonavi.naviapp\n6001 com.pateonavi.naviapp"), "com.pateonavi.naviapp"), /ambiguous/);
});
