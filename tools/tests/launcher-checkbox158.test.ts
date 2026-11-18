import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {agentScrollDirection} from '../../release/emulator-gate.ts';
import {parseUi,agentControl,uniqueNode} from '../emulator/installer-ui.ts';
const source=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
const direction=agentScrollDirection;
const start=source.indexOf('  const seekAgentControl = async');
const seekBody=source.slice(start,source.indexOf('  const top = async',start));
const scrollStart=source.indexOf('  const settingsScrollBox =');
const scrollBody=source.slice(scrollStart,source.indexOf('  const scroll = async',scrollStart));
const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
const run=new AsyncFunction('ui','settingsForeground','agentControl','SETTINGS','uniqueNode','agentScrollDirection','scroll','Unavailable','initial',stripTypeScriptTypes(scrollBody+seekBody)+';return seekAgentControl("Приложения лаунчера",initial);');
const pkg='com.qinggan.app.setting';
const xml=(n:number)=>readFileSync(new URL('./fixtures/launcher158-checkbox/'+n+'.xml',import.meta.url),'utf8');
const nodes=(n:number)=>parseUi(xml(n));
const viewport=(rows:ReturnType<typeof parseUi>)=>uniqueNode(rows,n=>n.packageName===pkg&&n.scrollable&&n.resource==='');
test('actual clipped row reverses navigation without weakening 40px admission',()=>{
 assert.equal(agentControl(nodes(1254),pkg,'Приложения лаунчера'),null);
 assert.equal(agentControl(nodes(1267),pkg,'Приложения лаунчера'),null);
 const before=nodes(1254),clipped=nodes(1267);
 assert.equal(direction(before,pkg,'Приложения лаунчера',viewport(before)),'down');
 assert.equal(direction(clipped,pkg,'Приложения лаунчера',viewport(clipped)),'up');
 // Fully offscreen invalid bounds are excluded by parseUi; no direction is inferred from absent text.
 assert.equal(agentControl(nodes(1407),pkg,'Приложения лаунчера')!.checked,true);
});
test('actual production seek recovers overshoot with bounded 25 percent swipes',async()=>{
 const calls:any[]=[];let current=nodes(1254);
 const ui={snapshot:async()=>current};
 const result=await run(ui,async()=>{},agentControl,pkg,uniqueNode,direction,async(d:string,_n:any,f:number)=>{calls.push([d,f]);current=nodes(d==='down'?1267:1407);},Error,current);
 assert.deepEqual(calls,[['down',0.25],['up',0.25]]);
 assert.equal(agentControl(result,pkg,'Приложения лаунчера')!.checked,true);
 // The former fixed downward loop remains clipped/offscreen on these actual captures.
 assert.equal(agentControl(nodes(1280),pkg,'Приложения лаунчера'),null);
});
test('unchanged end position terminates instead of twelve repeated gestures',async()=>{
 let calls=0;const current=nodes(1280);
 await assert.rejects(run({snapshot:async()=>current},async()=>{},agentControl,pkg,uniqueNode,direction,async()=>{calls++;},Error,current),/bounded target-aware/);
 assert.equal(calls,2);
});
test('wrong focus and ambiguous labels never yield an admitted control',()=>{
 const wrong=parseUi(xml(1407).replaceAll('input-focused="true"','input-focused="false"'));
 assert.equal(agentControl(wrong,pkg,'Приложения лаунчера'),null);
 const original=nodes(1407);const label=original.find(n=>n.text==='Приложения лаунчера')!;
 assert.throws(()=>direction([...original,{...label}],pkg,'Приложения лаунчера',viewport(original)),/ambiguous/);
});
test('stock preparation requires source-admitted stop and actual false guard before stock capture',()=>{
 const own=source.slice(source.indexOf('      case 12: {'),source.indexOf('      case 13:',source.indexOf('      case 12: {')));
 assert.ok(own.indexOf('await disablePolicy()')<own.indexOf('await checkDisable("launcher-apps"'));
 assert.ok(own.includes('stockPolicy.modes["launcher-apps"], () => toggle("launcher-apps", false), "stock-disable", true'));
 assert.ok(own.indexOf('await checkDisable("launcher-apps"')<own.indexOf('await launcherStockDisabled()'));
 assert.ok(own.indexOf('stockUnhooked.injections.some')<own.indexOf('fixtureOemItems ='));
 assert.ok(own.indexOf('fixtureOemItems =')<own.indexOf('await toggle("launcher-apps", true)'));
 assert.ok(source.includes('if (required && result.status !== "passed") unavailable(result.reason)'));
 assert.ok(source.includes('if (check.checked) unavailable("launcher agent already enabled;'));
});

const preparationStart=source.indexOf('        const stockPolicy = await disablePolicy();',source.indexOf('      case 12: {'));
const preparationEnd=source.indexOf('        fixtureOemItems =',preparationStart);
const prepare=new AsyncFunction('disablePolicy','catalog','language','top','seekAgentControl','agentControl','SETTINGS','proof','checkDisable','toggle','launcherStockDisabled','toggleObservation','checkboxTargets','unavailable',stripTypeScriptTypes('let admittedStopModes;'+source.slice(preparationStart,preparationEnd)));
for(const initial of [true,false]) test('production stock setup observes and retains prior '+initial+' before capture',async()=>{
 const calls:string[]=[];
 await prepare(async()=>({modes:{'launcher-apps':'restart'},file:'policy'}),[{id:'launcher-apps',title:{ru:'Launcher'}}],'ru',async()=>{calls.push('top');},async()=>[],()=>({checked:initial}),pkg,()=>{},
 async(id:string,mode:string,action:()=>Promise<void>,prefix:string,required:boolean)=>{assert.deepEqual([id,mode,prefix,required],['launcher-apps','restart','stock-disable',true]);calls.push('restart-verified');await action();},
 async(id:string,value:boolean)=>{assert.deepEqual([id,value],['launcher-apps',false]);calls.push('toggle-off');},
 async()=>{calls.push('actual-checkbox-false');},async()=>{calls.push('independent-absence');return {injections:[],file:'after'};},()=>['launcher'],(message:string)=>{throw new Error(message);});
 assert.deepEqual(calls,initial?['top','restart-verified','toggle-off','actual-checkbox-false','independent-absence']:['top','actual-checkbox-false','independent-absence']);
});
test('production stock setup rejects a retained injection before stock capture',async()=>{
 await assert.rejects(prepare(async()=>({modes:{'launcher-apps':'restart'},file:'policy'}),[{id:'launcher-apps',title:{ru:'Launcher'}}],'ru',async()=>{},async()=>[],()=>({checked:false}),pkg,()=>{},async()=>{},async()=>{},async()=>{},async()=>({injections:[{id:'launcher-apps'}]}),()=>['launcher'],(message:string)=>{throw new Error(message);}),/injection remains/);
});

test('actual former stock navigation fails on persisted skipped-row sequence',async()=>{
 const before=readFileSync(new URL('./fixtures/launcher158-checkbox/former-stock-helper.txt',import.meta.url),'utf8');
 const a=before.indexOf('  const launcherStockDisabled = async');
 const block=before.slice(a);
 const old=new AsyncFunction('catalog','language','top','ui','agentControl','SETTINGS','scroll','unavailable',stripTypeScriptTypes(block)+';return launcherStockDisabled();');
 let page=0;const observed=[nodes(1254),nodes(1267),nodes(1280)];
 await assert.rejects(old([{id:'launcher-apps',title:{ru:'Приложения лаунчера'}}],'ru',async()=>{}, {snapshot:async()=>observed[Math.min(page,2)]},agentControl,pkg,async()=>{page++;},(m:string)=>{throw new Error(m);}),/observed disabled launcher checkbox unavailable/);
 assert.equal(page,11);
});
