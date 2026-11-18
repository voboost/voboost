import test from "node:test";
import assert from "node:assert/strict";
import {stripTypeScriptTypes} from "node:module";
import {readFileSync} from "node:fs";
import {launcherItems} from "../../release/emulator-gate.ts";
import {InstallerUi,parseUi,launcherDock,validateRequest} from "../emulator/installer-ui.ts";
const xml=readFileSync(new URL("fixtures/launcher133-187.xml",import.meta.url),"utf8");
const nodes=parseUi(xml);
const grid=nodes.find(n=>n.resource==="com.qinggan.app.launcher:id/all_app_view"&&n.windowRef?.type===1)!;
function ui(){const value=Object.create(InstallerUi.prototype);value.nodes=nodes;value.observed=new WeakSet(nodes);return value;}
test("actual launcher snapshot explicitly binds gesture to app grid, not coordinate-selected pager",()=>{
 const value=ui();const authority=value.gestureAuthority(grid);
 assert.equal(authority.expectedTarget.resource,"com.qinggan.app.launcher:id/all_app_view");
 assert.equal(authority.expectedTarget.packageName,"com.qinggan.app.launcher");
 assert.equal(authority.expectedTarget.windowId,grid.windowRef!.id);
 assert.equal(authority.expectedTarget.bounds,`[${grid.rawBounds![0]},${grid.rawBounds![1]}][${grid.rawBounds![2]},${grid.rawBounds![3]}]`);
});
test("explicit authority rejects foreign clones, consumed captures, disabled and invisible nodes",()=>{
 const value=ui();assert.throws(()=>value.gestureAuthority({...grid}),/stale or foreign/);
 value.observed=new WeakSet();assert.throws(()=>value.gestureAuthority(grid),/stale or foreign/);
 for(const patch of [{enabled:false},{visibleToUser:false}]){const changed={...grid,...patch};const owner=ui();owner.observed=new WeakSet([changed]);assert.throws(()=>owner.gestureAuthority(changed),/stale or foreign/);}
});
test("all launcher page swipes and drag bind their fresh observed target",()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const beginning=source.slice(source.indexOf("  const launcherBeginning ="),source.indexOf("  const launcherGrid ="));
 assert.match(beginning,/milliseconds: 400, \.\.\.\(native\?ui.launcherPointerAuthority\(grid\):ui.gestureAuthority\(grid\)\)/);
 const part=source.slice(source.indexOf("      case 12:"),source.indexOf("      case 13:"));
 assert.equal((part.match(/action:\s*"gesture"/g)||[]).length,3);
 assert.match(part,/milliseconds: 400, \.\.\.\(native\?ui.launcherPointerAuthority\(grid\):ui.gestureAuthority\(grid\)\)/);
 assert.match(part,/milliseconds: 400, \.\.\.\(native\?ui.launcherPointerAuthority\(pageGrid\):ui.gestureAuthority\(pageGrid\)\)/);
 assert.match(part,/launcherPageDrag\(page, freshFm\[0\].node.bounds/);
 assert.match(part,/action: "drag", points, \.\.\.\(native\?ui.launcherPointerAuthority\(freshFm\[0\].node\):ui.gestureAuthority\(freshFm\[0\].node\)\)/);
});

test("production boundary flow reacquires authority after read-only boot consumes nodes",async()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const start=source.indexOf("      const beforeBoot =",source.indexOf("  const launcherBeginning ="));
 const end=source.indexOf("\n    },",start);
 const body=stripTypeScriptTypes("async function boundary(){"+source.slice(start,end)+"}").slice("async function boundary(){".length,-1).replace("(rows: typeof items)","(rows)").replaceAll("windowRef!.","windowRef.");
 const value=ui();let serial=0;let current;
 const page=()=>{const ns=parseUi(xml);const g=ns.find(n=>n.resource===grid.resource&&n.windowRef?.type===1)!;value.nodes=ns;value.observed=new WeakSet(ns);return {grid:g,nodes:ns,items:[{label:"Music",node:g}]};};
 current=page();const initial=current;const states=[];
 const bootObservation=async()=>{value.observed=new WeakSet();states.push("boot");return {boot:"same",bootCompleted:true};};
 const launcherPage=async()=>{serial++;current=page();states.push("snapshot");return current;};
 const uniqueNode=(ns,p)=>{const found=ns.filter(p);assert.equal(found.length,1);return found[0];};
 const continuity=(a,b)=>{assert.notEqual(a.grid,b.grid);assert.equal(a.grid.resource,b.grid.resource);states.push("continuity");};
 const fn=new (Object.getPrototypeOf(async function(){}).constructor)("items","bootObservation","launcherPage","uniqueNode","focusedApplication","screenshot","launcherCaptureContinuity","launcherAppsBeginningPng","readFileSync","initial",`const deferred=false,native=false; let launcherCurrentPage=initial;${body.replace("return launcherAppsBeginningPng(readFileSync(png),freshIndicator.bounds);","return {result:launcherAppsBeginningPng(readFileSync(png),freshIndicator.bounds),page:launcherCurrentPage};")}`);
 const result=await fn(initial.items,bootObservation,launcherPage,uniqueNode,()=>true,async()=>"png",continuity,()=>true,()=>Buffer.alloc(0),initial);
 assert.equal(result.result,true);assert.equal(result.page,current);assert.equal(value.gestureAuthority(result.page.grid).expectedTarget.resource,grid.resource);
 assert.deepEqual(states,["boot","snapshot","boot","continuity","snapshot","continuity"]);
});

test("launcher navigation opens fixed primary without implicit Home resolution",async()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const body=stripTypeScriptTypes(source.slice(source.indexOf("  const launcherGrid ="),source.indexOf("  let vehicleNodes:")));
 const calls=[];
 const ui={call:async request=>calls.push(request.action),openLauncherApps:async()=>calls.push("dock")};
 const fn=new (Object.getPrototypeOf(async function(){}).constructor)("ui","launcherBeginning","launcherPage",`const native=false;${body}; return launcherGrid(true);`);
 assert.deepEqual(await fn(ui,async()=>["stock"],async()=>({items:[]})),["stock"]);
 assert.deepEqual(calls,["open-primary-launcher","dock"]);
});

test("captured Home resolver is rejected without a gesture",async()=>{
 const captured=parseUi(readFileSync(new URL("fixtures/launcher155-home-resolver.xml",import.meta.url),"utf8"));
 assert(captured.some(n=>n.text.includes("Select a Home app")));
 assert.equal(captured.filter(n=>n.text==="module-widget").length,2);
 assert.throws(()=>launcherDock(captured),/unique OEM launcher dock required/);
 const value=Object.create(InstallerUi.prototype); let taps=0;
 value.snapshot=async()=>captured; value.call=async()=>{taps++;};
 await assert.rejects(value.openLauncherApps(),/unique OEM launcher dock required/);
 assert.equal(taps,0);
 validateRequest({action:"open-primary-launcher"});
 for(const field of ["component","display","command","arguments"]) assert.throws(()=>validateRequest({action:"open-primary-launcher",[field]:"foreign"}),/closed UI request fields/);
});

test("order excludes clipped adjacent page labels regardless of visibility and retains full slots",()=>{
 const select=launcherItems;
 const windowRef={id:1,type:1,active:true,inputFocused:true,bounds:[145,0,1920,720]};
 const make=(edgeVisible=true,width=50)=>{
  const base={packageName:"com.qinggan.app.launcher",windowRef,enabled:true,visibleToUser:true};
  const grid={...base,parent:null,bounds:[145,0,1920,720],clickable:false};
  const rows=[grid];
  for(let i=0;i<12;i++){
   const x=195+(i%6)*279,y=i<6?130:369,index=rows.length;
   rows.push({...base,parent:0,clickable:true,bounds:[x,y,x+279,y+189]});
   rows.push({...base,parent:index,resource:"com.qinggan.app.launcher:id/item_name",text:i===0?"Voboost Fixture":"stock-"+i,bounds:[x,y+155,x+279,y+188]});
  }
  const index=rows.length;
  rows.push({...base,parent:0,clickable:true,visibleToUser:edgeVisible,bounds:[1920-width,369,1920,558]});
  rows.push({...base,parent:index,resource:"com.qinggan.app.launcher:id/item_icon",visibleToUser:false,bounds:[1934,369,1920,519]});
  rows.push({...base,parent:index,resource:"com.qinggan.app.launcher:id/item_name",text:"APKPure",visibleToUser:edgeVisible,bounds:[1920-width,524,1920,557]});
  return {rows,grid};
 };
 const before=make(true),after=make(false);
 assert.deepEqual(select(before.rows,before.grid).map(x=>x.label),select(after.rows,after.grid).map(x=>x.label));
 assert.equal(select(before.rows,before.grid).length,12);
 // Equal-width legitimate edge slots must stay; no fixed icon-size heuristic.
 const full=make(true,279);
 assert.equal(select(full.rows,full.grid).at(-1).label,"APKPure");
});
