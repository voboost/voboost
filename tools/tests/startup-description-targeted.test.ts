import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { startupDescriptionCapture, startupProfiles } from "../../release/emulator-gate.ts";
import type { UiNode, WindowRef } from "../emulator/installer-ui.ts";
import type { Language, SettingsCopy } from "../emulator/settings-copy.ts";
const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const locales: Language[] = ["en", "ru", "zh"], modes = ["interface", "hidden", "none"] as const;
const copy = { languages: Object.fromEntries(locales.map(l => [l, {
 startupTitle: `Startup ${l}`,
 startup: modes.map(value => ({value,label:`Mode ${value} in ${l}`,description:`Description ${value} in ${l}`})),
}])) } as unknown as SettingsCopy;
const windowRef = { type:1,inputFocused:true,active:true,rootPackage:"com.qinggan.app.setting",rootStatus:"available" } as WindowRef;
function snapshot(l: Language, mode: typeof modes[number]): UiNode[] {
 const node = (text: string,checkable=false,checked=false) => ({text,checkable,checked,packageName:"com.qinggan.app.setting",visibleToUser:true,windowRef,enabled:true,bounds:[200,100,500,150]} as UiNode);
 const c=copy.languages[l];
 return [node("Voboost",true,true),node(c.startupTitle),...c.startup.map(r=>node(r.label,true,r.value===mode)),node(c.startup.find(r=>r.value===mode)!.description)];
}
test("case23 targeted production branch covers nine source-copy selections and fails mismatched copy without full-panel traversal", async()=>{
 const branch=source.slice(source.indexOf("await subcheck(`startup-${mode}-description-${l}`"),source.indexOf("}, returnInterface);",source.indexOf("await subcheck(`startup-${mode}-description-${l}`")));
 assert.match(branch,/startupDescriptionCapture\(\(\) => top\(false\)/);
 assert.doesNotMatch(branch,/panel\(|scroll\(/);
 let l: Language="en",mode: typeof modes[number]="interface",reads=0,captures=0,recoveries=0;
 await startupProfiles(async value=>{l=value;},async value=>{mode=value;},async (expectedMode,expectedLocale)=>{
   assert.equal(mode,expectedMode);assert.equal(l,expectedLocale);
   await startupDescriptionCapture(async()=>{reads++;return snapshot(l,mode);},async()=>{captures++;},copy,l,mode);
 },async()=>{recoveries++;mode="interface";});
 assert.deepEqual([reads,captures,recoveries],[9,9,3]);
 for(const mutate of [
   (nodes: UiNode[])=>{nodes.at(-1)!.text="stale description";},
   (nodes: UiNode[])=>{nodes.find(n=>n.text==="Startup ru")!.text="Startup en";},
   (nodes: UiNode[])=>{nodes.find(n=>n.text==="Mode hidden in ru")!.checked=false;nodes.find(n=>n.text==="Mode interface in ru")!.checked=true;},
 ]) {
   const nodes=snapshot("ru","hidden");mutate(nodes);
   await assert.rejects(()=>startupDescriptionCapture(async()=>nodes,async()=>{throw new Error("must not capture mismatched selection");},copy,"ru","hidden"));
 }
 assert.equal(captures,9);
});
