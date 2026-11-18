import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { focusedApplication, uniqueNode } from "../emulator/installer-ui.ts";

const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const start = source.indexOf("    let nodes=await ui.snapshot();", source.indexOf("  const readLauncherTheme ="));
const end = source.indexOf("    const theme=selectedLauncherTheme(nodes);", start);
assert(start > 0 && end > start);
const run = new (Object.getPrototypeOf(async function(){}).constructor)("ui", "SETTINGS", "uniqueNode", "focusedApplication", "Unavailable", stripTypeScriptTypes(source.slice(start,end))+"return nodes;");
const settings="com.qinggan.app.setting";
function snapshot(hidden: boolean | "partial"=true, focused=true, duplicate=false) {
 const windowRef={type:1,inputFocused:focused,rootPackage:settings};
 const common={packageName:settings,enabled:true,windowRef};
 const list={...common,resource:settings+":id/recyclerview_theme_pic",visibleToUser:!hidden,bounds:hidden?[485,720,485,720]:[485,200,1849,600],rawBounds:hidden?[485,1558,1849,720]:[485,200,1849,600]};
 const scroll={...common,resource:settings+":id/display_scroll",scrollable:true,visibleToUser:true,bounds:[485,50,1865,720]};
 const nodes=duplicate?[list,{...list},scroll]:[list,scroll];
 if(hidden==="partial"){list.visibleToUser=true;list.bounds=[485,700,1849,720];list.rawBounds=[485,700,1849,720];}
 for(const text of ["Classic","Speedy Dash"]){
   const parent=nodes.length;
   nodes.push({...common,resource:settings+":id/rl_root",clickable:true,visibleToUser:hidden===false,bounds:hidden===false?[500,200,800,600]:[500,700,800,720]});
   nodes.push({...common,resource:settings+":id/tv_title",text,parent,visibleToUser:hidden===false,bounds:hidden===false?[529,244,633,270]:[529,hidden==="partial"?744:1602,633,720],rawBounds:hidden===false?[529,244,633,270]:[529,hidden==="partial"?744:1602,633,720]});
 }
 return nodes;
}
test("actual theme seek scrolls an offscreen recycler in the focused Settings window",async()=>{
 let reads=0, swipes=0;
 const ui={snapshot:async()=>snapshot(reads++===0),gestureAuthority:n=>{assert(n.visibleToUser);return {authority:true};},call:async req=>{assert.equal(req.action,"swipe");assert(req.authority);swipes++;}};
 const nodes=await run(ui,settings,uniqueNode,focusedApplication,Error);
 assert.equal(swipes,1);assert.equal(reads,2);assert(nodes[0].visibleToUser);
});
for (const [label,focused,duplicate] of [["unfocused",false,false],["ambiguous",true,true]]) test("actual theme seek rejects "+label+" recycler before mutation",async()=>{
 let mutated=false;
 await assert.rejects(run({snapshot:async()=>snapshot(true,focused,duplicate),call:async()=>{mutated=true;}},settings,uniqueNode,focusedApplication,Error),/absent or ambiguous/);
 assert.equal(mutated,false);
});

test("actual theme seek continues past a 20px recycler strip until titles and clickable cards are visible",async()=>{
 let reads=0,swipes=0;
 const stages=[true,"partial",false] as const;
 const ui={snapshot:async()=>snapshot(stages[Math.min(reads++,2)]),gestureAuthority:n=>{assert(n.visibleToUser);return {authority:true};},call:async req=>{assert.equal(req.action,"swipe");assert(req.authority);assert(req.y1>req.y2,"hidden titles below viewport require upward swipe");swipes++;}};
 const nodes=await run(ui,settings,uniqueNode,focusedApplication,Error);
 assert.equal(swipes,2);assert.equal(reads,3);
 for(const title of nodes.filter(n=>["Classic","Speedy Dash"].includes(n.text))){assert(title.visibleToUser);assert(nodes[title.parent].visibleToUser);assert(nodes[title.parent].clickable);}
});
