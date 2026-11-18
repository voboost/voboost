import { rejects, strictEqual, throws } from "node:assert";
import test from "node:test";
import { mkdtempSync, mkdirSync, rmSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { InstallerUi, parseUi, uniqueNode } from "../emulator/installer-ui.ts";


test("window metadata preserves signed layers and unclassified windows without target authority", async () => {
  const { serializedWindows } = await import("../emulator/window-fixture.ts");
  const { focusedApplication } = await import("../emulator/installer-ui.ts");
  const variant = (type: string, layer: string) => serializedWindows.replace('type="1" layer="2"', `type="${type}" layer="${layer}"`);
  for (const type of ["-1", "1", "2", "3", "4", "5"]) {
    for (const layer of ["-2147483648", "-2", "-1", "0", "2147483647"]) {
      const nodes = parseUi(variant(type, layer));
      strictEqual(nodes.length, 4);
      strictEqual(nodes.windowRefs?.length, 5);
      strictEqual(nodes[0].windowRef?.type, Number(type));
      strictEqual(nodes[0].windowRef?.layer, Number(layer));
      strictEqual(focusedApplication(nodes[0]), type === "1" || type === "3");
      if (type === "-1") throws(() => uniqueNode(nodes, n => n.windowRef?.id === 17), /0 visible/);
    }
  }
  for (const layer of ["-2147483649", "2147483648", "-0", "+1", "01", "1.5", "1e2", " -1", "NaN"])
    throws(() => parseUi(variant("1", layer)));
  for (const type of ["-2", "0", "6", "2147483648", "-0", "+1"])
    throws(() => parseUi(variant(type, "0")));
  const build = fileURLToPath(new URL("../../build/", import.meta.url));
  mkdirSync(build, { recursive: true });
  const work = mkdtempSync(join(build, "window-target-"));
  let calls = 0;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(work, "ui"), async () => {
      calls++;
      return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: variant("-1", "-1") }) + "\n", stderr: "" };
    }, async () => {});
    const observed = await ui.snapshot();
    await rejects(ui.tap(observed[0]), /unclassified/);
    strictEqual(calls, 1);
  } finally { rmSync(work, { recursive: true, force: true }); }

});


test("hidden clipped node rectangles retain raw data without target authority", async () => {
  const { serializedWindows } = await import("../emulator/window-fixture.ts");
  const clipped = (bounds: string, visible: boolean) => serializedWindows.replace(/<node\b[^>]*>/u, tag => tag.replace(/bounds="[^"]*"/u, `bounds="${bounds}"`).replace('visible-to-user="true"', `visible-to-user="${visible}"`));
  for (const bounds of ["[175,750][443,720]", "[515,188][775,148]", "[515,1235][600,720]"]) {
    const nodes = parseUi(clipped(bounds, false));
    strictEqual(nodes.length, 4);
    strictEqual(nodes[0].rawBounds?.join(","), bounds.match(/-?\d+/gu)!.join(","));
    throws(() => uniqueNode(nodes, n => n.windowRef?.id === 17), /0 visible/);
    throws(() => parseUi(clipped(bounds, true)), /reversed UI bounds/);
  }
  throws(() => parseUi(serializedWindows.replace('type="1" layer="2" active="true" input-focused="true" accessibility-focused="false" bounds="[0,0][1920,720]"', 'type="1" layer="2" active="true" input-focused="true" accessibility-focused="false" bounds="[175,750][443,720]"')), /reversed UI bounds/);
  const build = fileURLToPath(new URL("../../build/", import.meta.url)); mkdirSync(build, { recursive: true });
  const work = mkdtempSync(join(build, "hidden-node-")); let calls = 0;
  try {
    const ui = new InstallerUi("/package/voboost-installer", "emulator-5562", join(work, "ui"), async () => {
      calls++; return { exitCode: 0, stdout: JSON.stringify({ schemaVersion: 1, serial: "emulator-5562", body: clipped("[175,750][443,720]", false) }) + "\n", stderr: "" };
    }, async () => {});
    const nodes = await ui.snapshot(); await rejects(ui.tap(nodes[0])); strictEqual(calls, 1);
  } finally { rmSync(work, { recursive: true, force: true }); }
});

test("clean cycle106 Settings captures renumber owner91 to106 without stable fingerprint drift", async () => {
  const { readFileSync } = await import("node:fs");
  const { execFileSync } = await import("node:child_process");
  const firstXml = readFileSync(new URL("../../testdata/emulator/cycle106-action411.xml", import.meta.url), "utf8");
  const nextXml = readFileSync(new URL("../../testdata/emulator/cycle106-action415.xml", import.meta.url), "utf8");
  const normalize = (xml: string) => xml.replace(/(?:window-id|id)="[0-9]+"/g, 'id="capture-local"').replace(/capture-(?:start|end)-uptime-ms="[0-9]+"/g, 'capture-time="local"');
  strictEqual(normalize(firstXml), normalize(nextXml));
  const owner = (xml: string) => parseUi(xml).find(node => node.windowRef?.active && node.windowRef.inputFocused)!.windowRef!;
  const before = owner(firstXml), after = owner(nextXml);
  strictEqual(before.id, 91); strictEqual(after.id, 106);
  const work = mkdtempSync(join(fileURLToPath(new URL("../../build/", import.meta.url)), "capture-guard-"));
  try {
    const { writeFileSync } = await import("node:fs");
    const java = `import ru.voboost.installer.observation.WindowMetadata;
public class GuardFixture {
 public static void main(String[] a) {
  WindowMetadata fresh = new WindowMetadata(Integer.parseInt(a[0]), Integer.parseInt(a[1]), 0, true, true, false, a[2], a[3], true, a[4]);
  if (fresh.gestureFocus(Integer.parseInt(a[5]), a[8], a[6], a[7]) != Boolean.parseBoolean(a[9])) throw new AssertionError("fresh foreground fingerprint");
 }
}`;
    writeFileSync(join(work, "GuardFixture.java"), java);
    execFileSync(process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin/javac") : "javac", ["--release", "8", "-d", work, fileURLToPath(new URL("../../../voboost-installer/observation/src/ru/voboost/installer/observation/WindowMetadata.java", import.meta.url)), join(work, "GuardFixture.java")]);
    const bounds = (v: readonly number[]) => `[${v[0]},${v[1]}][${v[2]},${v[3]}]`;
    const args = [String(after.id), String(after.type), bounds(after.bounds), after.title, after.rootPackage, String(before.type), bounds(before.bounds), before.title, before.rootPackage];
    const run = (values: string[], accepted: boolean) => execFileSync(process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin/java") : "java", ["-cp", work, "GuardFixture", ...values, String(accepted)]);
    run(args, true);
    for (const [index, value] of [[1, "3"], [2, "[0,0][100,100]"], [3, "different window"], [4, "com.qinggan.app.music"]] as const) {
      const changed = [...args]; changed[index] = value; run(changed, false);
    }
  } finally { rmSync(work, { recursive: true, force: true }); }
});
