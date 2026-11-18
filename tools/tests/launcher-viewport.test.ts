import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {parseUi} from "../emulator/installer-ui.ts";
import {launcherFirstPage,launcherItems} from "../../release/emulator-gate.ts";
const api={launcherFirstPage,launcherItems};
const xml=i=>readFileSync(new URL(`fixtures/launcher132-action-${i}.xml`,import.meta.url),"utf8");
const rows=i=>{const nodes=parseUi(xml(i));const grid=nodes.find(n=>n.resource==="com.qinggan.app.launcher:id/all_app_view"&&n.windowRef?.type===1);return api.launcherItems(nodes,grid)};
test("actual drag capture excludes clipped Voboost Fixture and requires viewport navigation",async()=>{
 const before=rows(225), moved=rows(233); assert.equal(before[0].label,"User Center");assert.equal(moved[0].label,"Settings");assert.ok(!moved.some(x=>x.label==="Voboost Fixture"));
 let swipes=0;const normalized=[{...moved[0],label:"Voboost Fixture",node:{...moved[0].node,bounds:[195,130,474,319]}}];
 const result=await api.launcherFirstPage(async()=>swipes?normalized:moved,async items=>{assert.ok(items[0].node.visibleToUser!==false);swipes++},async()=>swipes>0);
 assert.equal(swipes,1);assert.equal(result[0].label,"Voboost Fixture");
});
test("stable wrong page cannot substitute for independent first-page witness",async()=>{const moved=rows(233);await assert.rejects(api.launcherFirstPage(async()=>moved,async()=>{},async()=>false),/stalled or cycled/)});
test("normalization rejects navigation cycles and bounded nonconvergence",async()=>{
 const base=rows(233);let step=0;await assert.rejects(api.launcherFirstPage(async()=>base.map(x=>({...x,label:x.label+step%2})),async()=>{step++},async()=>false),/cycled/);
 step=0;await assert.rejects(api.launcherFirstPage(async()=>base.map(x=>({...x,label:x.label+step})),async()=>{step++},async()=>false),/bounded/);assert.equal(step,12);
});
