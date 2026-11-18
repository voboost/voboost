import { readFileSync } from "node:fs";

/** Host serializer output is synthetic protocol evidence, never OEM runtime proof. */
export const serializedWindows = readFileSync(new URL("../../testdata/emulator/windows-v1.xml", import.meta.url), "utf8");
export function windowModel(): string {
  const template = /<node [^>]*><\/node>/u.exec(serializedWindows)![0];
  const node = (id: number, pkg: string, text: string, focused: boolean, clickable: boolean) => template
    .replace('window-id="17"', `window-id="${id}"`)
    .replaceAll("fixture.navigation", pkg)
    .replace('class="android.widget.FrameLayout"', 'class="android.widget.EditText"')
    .replace('text=""', `text="${text}"`)
    .replace('focused="true"', `focused="${focused}"`)
    .replace('clickable="false"', `clickable="${clickable}"`);
  return serializedWindows
    .replace(/<node [^>]*><\/node>/u, node(17, "com.pateonavi.naviapp", "", true, false))
    .replace('root-package="fixture.navigation"', 'root-package="com.pateonavi.naviapp"')
    .replace(/(<window id="18"[^>]*>)<node [^>]*><\/node>/u, (_, window) => window + template.replace('window-id="17"', 'window-id="18"').replaceAll("fixture.navigation", "com.qinggan.app.qgime").replace('focused="true"', 'focused="false"').replace('</node>', [..."abcdefghi"].map((c, i) => node(18, "com.qinggan.app.qgime", c, false, true).replace('index="0"', `index="${i}"`)).join("") + '</node>'))
    .replace('root-package="fixture.ime"', 'root-package="com.qinggan.app.qgime"');
}
