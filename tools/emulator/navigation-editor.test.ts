import { strictEqual, throws } from "node:assert";
import test from "node:test";
import { navigationSafetyConfirm, navigationSearchEntry } from "./navigation-editor.ts";
import type { UiNode } from "./installer-ui.ts";
function fixture(): UiNode[] {
  const windowRef = { type: 1, active: true, inputFocused: true, rootPackage: "com.pateonavi.naviapp" };
  return ["agree_title", "agree_context", "btn_agree"].map((id, i) => ({ packageName: windowRef.rootPackage, resource: `${windowRef.rootPackage}:id/${id}`, className: "android.widget.TextView", visibleToUser: true, enabled: true, clickable: i === 2, text: ["Safety Tips", "Please operate when parked", "Confirm"][i], description: "", windowRef } as UiNode));
}
test("exact observed Navi safety dialog returns its normal UI confirmation", () => { const nodes=fixture(); strictEqual(navigationSafetyConfirm(nodes),nodes[2]); });
test("absence of safety dialog does not invent a confirmation", () => strictEqual(navigationSafetyConfirm([]),null));
test("missing duplicate foreign hidden disabled and cross-window controls cannot authorize confirmation", () => {
  for (const mutate of [(n:UiNode[])=>n.pop(), (n:UiNode[])=>n.push(n[2]), (n:UiNode[])=>n[2].packageName="other", (n:UiNode[])=>n[2].visibleToUser=false, (n:UiNode[])=>n[2].enabled=false, (n:UiNode[])=>n[2].windowRef={...n[2].windowRef!}, (n:UiNode[])=>n[2].clickable=false]) { const nodes=fixture(); mutate(nodes); throws(()=>navigationSafetyConfirm(nodes)); }
});
test("unfocused foreign windows and same labels with unrelated IDs are not onboarding", () => { const nodes=fixture(); for(const n of nodes)n.windowRef={...n.windowRef!,inputFocused:false};strictEqual(navigationSafetyConfirm(nodes),null); for(const n of nodes){n.windowRef={...n.windowRef!,inputFocused:true};n.resource="other:id/btn_agree";}strictEqual(navigationSafetyConfirm(nodes),null); });

function searchFixture(): any[] {const windowRef={type:1,active:true,inputFocused:true,rootPackage:"com.pateonavi.naviapp"};return [{packageName:windowRef.rootPackage,windowRef,visibleToUser:true,enabled:true,className:"android.widget.LinearLayout",resource:"com.pateonavi.naviapp:id/ll_normal_search",clickable:true,description:"搜索;你想去哪里",text:"",parent:null},{packageName:windowRef.rootPackage,windowRef,visibleToUser:true,enabled:true,className:"android.widget.TextView",resource:"com.pateonavi.naviapp:id/tv_normal_search",clickable:true,description:"",text:"Where to go?",parent:0}];}
test("actual map entry returns parent search control",()=>{const n=searchFixture();strictEqual(navigationSearchEntry(n),n[0]);});
test("editor or absent entry needs no search action",()=>{strictEqual(navigationSearchEntry([]),null);const n=searchFixture();n.push({...n[1],className:"android.widget.EditText"});strictEqual(navigationSearchEntry(n),null);});
test("partial duplicate wrong ancestry cross-window disabled and foreign controls fail",()=>{for(const mutate of [(n:any[])=>n.pop(),(n:any[])=>n.push(n[0]),(n:any[])=>n[1].parent=9,(n:any[])=>n[1].windowRef={...n[1].windowRef},(n:any[])=>n[0].enabled=false,(n:any[])=>n[0].packageName="other"]){const n=searchFixture();mutate(n);throws(()=>navigationSearchEntry(n));}});
test("unfocused window cannot authorize input",()=>{const n=searchFixture();for(const v of n)v.windowRef.inputFocused=false;strictEqual(navigationSearchEntry(n),null);});

import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { visualKey, committedPrefix, type KeyboardFrame, type ReviewedLayout } from './navigation-editor.ts';
function visualFixture() {
  const pixels=new Uint8Array(8*8*4).fill(120), sha=createHash('sha256').update(pixels).digest('hex');
  const current: KeyboardFrame={ boot:'boot',session:'session',pid:1,window:2,resource:'com.qinggan.app.qgime:id/alpha_view1',bounds:[0,368,8,376],geometryVerified:true,frameBoot:'boot',observedAt:100,width:8,height:8,rgba:pixels };
  const layout: ReviewedLayout={schema:1,language:'en',theme:'dark',sourceRevision:'apk-sha',baselineSha256:sha,reviewer:'human',reviewedAt:'2026-10-15',bounds:current.bounds,width:8,height:8,keys:[{label:'a',region:[1,1,7,7],center:[4,4]}]};
  const run=(layouts=[layout], fresh={...current})=>visualKey(layouts,()=>pixels,current,fresh,'a',{theme:'dark',sourceRevision:'apk-sha',now:110,language:'en'});
  return {pixels,current,layout,run};
}
test('reviewed exact label pixels produce actual screen coordinate',()=>assert.deepEqual(visualFixture().run(),{x:4,y:372,evidence:'reviewed-pixels-and-live-editor'}));
test('changed glyph is rejected',()=>{ const f=visualFixture(), bytes=new Uint8Array(f.pixels); bytes[(2*8+2)*4]++; assert.throws(()=>f.run([f.layout],{...f.current,rgba:bytes}),/unique/); });
test('suggestion pixels outside reviewed label regions are irrelevant',()=>{const f=visualFixture(),bytes=new Uint8Array(f.pixels);bytes[0]++; assert.equal(f.run([f.layout],{...f.current,rgba:bytes}).x,4);});
test('ambiguous layouts fail',()=>{const f=visualFixture();assert.throws(()=>f.run([f.layout,f.layout]),/unique/);});
for(const key of ['boot','session','pid','window','bounds','frameBoot','geometryVerified','observedAt'] as const) test('stale fence rejects '+key,()=>{const f=visualFixture();const values={boot:'other',session:'other',pid:2,window:3,bounds:[1,368,9,376],frameBoot:'other',geometryVerified:false,observedAt:99};assert.throws(()=>f.run([f.layout],{...f.current,[key]:values[key]}));});
test('centre on border or outside label rejects',()=>{const f=visualFixture();f.layout.keys[0].center=[7,4];assert.throws(()=>f.run(),/geometry/);});
test('baseline bytes are content qualified',()=>{const f=visualFixture();f.layout.baselineSha256='a'.repeat(64);assert.throws(()=>f.run(),/baseline/);});
test('real UTF16 prefix and caret required, password always rejected',()=>{const e={password:false,focused:true,visible:true,enabled:true,value:'a😀',selectionStart:3,selectionEnd:3};committedPrefix(e,'a😀');for(const change of [{password:true},{value:'a'},{selectionStart:2},{focused:false}]) assert.throws(()=>committedPrefix({...e,...change},'a😀'));});
import { actualKeyboardInput } from './navigation-editor.ts';
test('semantic route does not need or consult reviewed canvas evidence',async()=>{const calls:string[]=[];await actualKeyboardInput(()=>7,async k=>{calls.push('tap:'+k);},async()=>{throw new Error('map absent');},()=>false);assert.deepEqual(calls,['tap:7']);});
test('only admitted lookup miss selects real canvas fallback once',async()=>{let count=0;await actualKeyboardInput(()=>{throw new Error('absent AX letters');},async()=>{throw new Error('tap unexpected');},async()=>{count++;},error=>String(error).includes('absent AX'));assert.equal(count,1);});
test('dispatch failure cannot fall through or replay as canvas input',async()=>{let count=0;await assert.rejects(actualKeyboardInput(()=>7,async()=>{throw new Error('unknown receipt');},async()=>{count++;},()=>true),/unknown receipt/);assert.equal(count,0);});
test('unreviewed fallback and unrelated lookup failures stay unavailable',async()=>{await assert.rejects(actualKeyboardInput(()=>{throw new Error('absent AX');},async()=>{},async()=>{throw new Error('reviewed map absent');},()=>true),/map absent/);let count=0;await assert.rejects(actualKeyboardInput(()=>{throw new Error('privacy');},async()=>{},async()=>{count++;},()=>false),/privacy/);assert.equal(count,0);});

import { reviewedKeyboardRoute } from './navigation-editor.ts';
function controlFixture() {
  const f=visualFixture(), bytes=[121,122,123].map(v=>new Uint8Array(8*8*4).fill(v));
  const layouts:ReviewedLayout[]=bytes.map((p,i)=>({...f.layout,language:i===2?'ru':'en',baselineSha256:createHash('sha256').update(p).digest('hex'),keys:[{label:i===0?'a':i===1?'A':'Я',region:[1,1,3,3],center:[2,2]},{label:'shift',region:[3,1,5,3],center:[4,2]},{label:'switch',region:[5,1,7,3],center:[6,2]}]}));
  layouts[0].controls=[{label:'shift',targetBaselineSha256:layouts[1].baselineSha256},{label:'switch',targetBaselineSha256:layouts[2].baselineSha256}];
  layouts[1].controls=[{label:'shift',targetBaselineSha256:layouts[0].baselineSha256}];
  layouts[2].controls=[{label:'switch',targetBaselineSha256:layouts[0].baselineSha256}];
  let at=0, checks=0, taps=0;const targets:number[]=[];
  const capture=async():Promise<[KeyboardFrame,KeyboardFrame]>=>{const frame={...f.current,rgba:bytes[at]};return [frame,{...frame}];};
  const dispatch=async(point:{x:number;y:number})=>{taps++;if(point.x===4)at=at===0?1:0;else if(point.x===6)at=at===2?0:2;targets.push(at);};
  const check=async()=>{checks++;};
  const baseline=(sha:string)=>bytes[layouts.findIndex(l=>l.baselineSha256===sha)];
  const context={theme:'dark',sourceRevision:'apk-sha',now:()=>110};
  return {layouts,bytes,capture,dispatch,check,baseline,context,targets,get taps(){return taps;},get checks(){return checks;},set at(value:number){at=value;}};
}
test('reviewed shift changes case and proves target before exactly one letter',async()=>{const f=controlFixture();await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'A'});assert.deepEqual(f.targets,[1,1]);assert.equal(f.checks,4);});
test('reviewed EN/RU switch leaves prefix unchanged and requires exact target pixels',async()=>{const f=controlFixture();await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'ru'});assert.deepEqual(f.targets,[2]);});
test('two reviewed edges switch then shift, no inferred key coordinates',async()=>{const f=controlFixture();f.at=2;await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'A'});assert.deepEqual(f.targets,[0,1,1]);});
test('already requested language is no-op with live pixel/editor proof',async()=>{const f=controlFixture();await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en'});assert.equal(f.taps,0);assert.equal(f.checks,2);});
test('missing reviewed shift edge fails before dispatch',async()=>{const f=controlFixture();f.layouts[0].controls=[];await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'A'}),/no reviewed route/);assert.equal(f.taps,0);});
test('unknown control receipt is not replayed and never types letter',async()=>{const f=controlFixture();let taps=0;await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,async()=>{taps++;throw new Error('receipt unknown');},f.check,f.context,{language:'en',label:'A'}),/receipt unknown/);assert.equal(taps,1);});
test('unchanged pixels after control cannot be claimed as target',async()=>{const f=controlFixture();let taps=0;await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,async()=>{taps++;},f.check,f.context,{language:'en',label:'A'}),/reviewed target/);assert.equal(taps,1);});
test('prefix or caret drift after shift prevents letter dispatch',async()=>{const f=controlFixture();let checks=0;await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,async()=>{if(++checks===3)throw new Error('caret drift');},f.context,{language:'en',label:'A'}),/caret drift/);assert.equal(f.taps,1);});
test('session change after shift prevents next input',async()=>{const f=controlFixture();let reads=0;await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,async()=>{const pair=await f.capture();if(++reads===2)pair.forEach(p=>p.session='other');return pair;},f.dispatch,f.check,f.context,{language:'en',label:'A'}),/live window/);assert.equal(f.taps,1);});
test('cross-language shift and unknown target map rejected before input',async()=>{for(const sha of ['missing',controlFixture().layouts[2].baselineSha256]){const f=controlFixture();f.layouts[0].controls=[{label:'shift',targetBaselineSha256:sha}];await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'A'}),/edge/);assert.equal(f.taps,0);}});
test('ambiguous control label cannot choose an arbitrary edge',async()=>{const f=controlFixture();f.layouts[0].controls!.push({...f.layouts[0].controls![0]});await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'A'}),/ambiguous reviewed controls/);assert.equal(f.taps,0);});

function emptyHintEditor() {return {password:false,focused:true,visible:true,enabled:true,value:'',showingHint:true,selectionStart:-1,selectionEnd:-1};}
test('empty focused hint admits unknown caret without changing -1/-1 evidence',()=>{const editor=Object.freeze(emptyHintEditor());committedPrefix(editor,'');assert.equal(editor.selectionStart,-1);assert.equal(editor.selectionEnd,-1);});
test('unknown caret requires exact empty value and explicit shown hint',()=>{for(const change of [{value:'М'},{showingHint:false},{showingHint:undefined},{selectionStart:0},{selectionEnd:0},{selectionStart:undefined},{selectionEnd:undefined}])assert.throws(()=>committedPrefix({...emptyHintEditor(),...change},''));assert.throws(()=>committedPrefix(emptyHintEditor(),'М'));});
test('empty hint exception preserves focus visibility enabled and password guards',()=>{for(const change of [{password:true},{focused:false},{visible:false},{enabled:false}])assert.throws(()=>committedPrefix({...emptyHintEditor(),...change},''));});
test('after first character exact nonempty UTF16 prefix and observed caret remain mandatory',()=>{const editor={...emptyHintEditor(),value:'М',showingHint:false,selectionStart:1,selectionEnd:1};committedPrefix(editor,'М');for(const change of [{selectionStart:-1,selectionEnd:-1},{selectionStart:0},{selectionEnd:0},{value:'М '},{value:''}])assert.throws(()=>committedPrefix({...editor,...change},'М'));});
test('reviewed layout control accepts unchanged empty hint but rejects losing hint proof',async()=>{const f=controlFixture();let checks=0;await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,async()=>{checks++;committedPrefix(emptyHintEditor(),'');},f.context,{language:'ru'});assert.equal(checks,4);assert.equal(f.taps,1);const g=controlFixture();let after=0;await assert.rejects(reviewedKeyboardRoute(g.layouts,g.baseline,g.capture,g.dispatch,async()=>{committedPrefix({...emptyHintEditor(),showingHint:++after<3},'');},g.context,{language:'ru'}),/prefix or caret/);assert.equal(g.taps,1);});

import { retainKeyboardCapture } from "../../release/emulator-gate.ts";
import { mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } from "node:fs";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
for (const failure of ["none", "bad-hash", "copy-conflict", "old-root", "sibling-root"] as const) test(`owned keyboard capture retention ${failure}`, () => {
  const root=join(import.meta.dirname,"../../build/unit-fixtures","keyboard-retention-test-"+randomUUID());
  const temporary=join(root,".local/build/tmp");mkdirSync(temporary,{recursive:true});
  const source=join(root,failure==="old-root"?".local/tmp":failure==="sibling-root"?".local/build/tmp-other":".local/build/tmp","keyboard-capture-"+randomUUID()), target=join(root,"retained");
  mkdirSync(source,{recursive:true});
  const png=Buffer.from("retained fixture bytes"), hash=createHash("sha256").update(png).digest("hex");
  writeFileSync(join(source,"primary.png"),png);
  writeFileSync(join(source,"manifest.json"),JSON.stringify({frames:[{file:"primary.png",sha256:failure==="bad-hash"?"0".repeat(64):hash}]}));
  if (failure==="copy-conflict") {mkdirSync(target);writeFileSync(join(target,"manifest.json"),"existing");}
  try {
    if(failure==="none") {retainKeyboardCapture(root,source,target);strictEqual(existsSync(source),false);strictEqual(readFileSync(join(target,"primary.png")).equals(png),true);}
    else {throws(()=>retainKeyboardCapture(root,source,target));strictEqual(existsSync(join(source,"primary.png")),true);}
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('reviewed EN-only language key may reset case with distinct exact target pixels',async()=>{
  const f=controlFixture();f.layouts[2].language='en';f.layouts[2].keys[0].label='B';
  await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en',label:'B'});
  assert.deepEqual(f.targets,[2,2]);assert.equal(f.checks,4);
});
test('same-language switch cannot name its own unchanged baseline',async()=>{
  const f=controlFixture();f.layouts[0].controls=[{label:'switch',targetBaselineSha256:f.layouts[0].baselineSha256}];
  await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'en'}),/unreviewed control edge/);
  assert.equal(f.taps,0);
});
test('unrelated reviewed EN-only reset edge does not invalidate current RU routing',async()=>{
  const f=controlFixture();f.layouts[1].controls=[{label:'switch',targetBaselineSha256:f.layouts[0].baselineSha256}];
  f.at=2;await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'ru'});
  assert.equal(f.taps,0);assert.equal(f.checks,2);
});

test('older comparison frame may exceed 2s when freshest exact frame is current',()=>{
 const f=visualFixture();const fresh={...f.current,observedAt:3000};
 assert.equal(visualKey([f.layout],()=>f.pixels,f.current,fresh,'a',{theme:'dark',sourceRevision:'apk-sha',now:3500,language:'en'}).x,4);
});
test('freshest frame still expires after 2s with an explicit age diagnosis',()=>{
 const f=visualFixture();const fresh={...f.current,observedAt:3000};
 assert.throws(()=>visualKey([f.layout],()=>f.pixels,f.current,fresh,'a',{theme:'dark',sourceRevision:'apk-sha',now:5001,language:'en'}),/fresh capture age exceeds 2000ms: 2001ms/);
});
test('old comparison pixels must still exactly match the fresh reviewed label',()=>{
 const f=visualFixture();const bytes=new Uint8Array(f.pixels);bytes[(2*8+2)*4]++;
 assert.throws(()=>visualKey([f.layout],()=>f.pixels,{...f.current,rgba:bytes},{...f.current,observedAt:3000},'a',{theme:'dark',sourceRevision:'apk-sha',now:3500,language:'en'}),/unique/);
});

function fourHopFixture() {
 const f=controlFixture();const bytes=[121,122,123,124,125].map(v=>new Uint8Array(8*8*4).fill(v));
 const layouts:ReviewedLayout[]=bytes.map((p,i)=>({...f.layouts[0],language:i<3?'en':'ru',baselineSha256:createHash('sha256').update(p).digest('hex'),keys:[{label:['a','A','a','м','М'][i],region:[1,1,3,3],center:[2,2]},{label:'shift',region:[3,1,5,3],center:[4,2]},{label:'switch',region:[5,1,7,3],center:[6,2]}],controls:[]}));
 for(let i=0;i<4;i++)layouts[i].controls=[{label:i===2?'switch':'shift',targetBaselineSha256:layouts[i+1].baselineSha256}];
 let at=0;const points:number[]=[];
 const capture=async():Promise<[KeyboardFrame,KeyboardFrame]>=>{const frame={...visualFixture().current,rgba:bytes[at]};return [frame,{...frame}];};
 const dispatch=async(p:{x:number;y:number})=>{points.push(p.x);if(p.x!==2)at++;};
 return {...f,layouts,baseline:(sha:string)=>bytes[layouts.findIndex(l=>l.baselineSha256===sha)],capture,dispatch,points};
}
test('four explicit reviewed edges reach uppercase Russian before one letter',async()=>{
 const f=fourHopFixture();await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'ru',label:'М'});
 assert.deepEqual(f.points,[4,4,6,4,2]);
});
test('finite reviewed route with missing fourth edge stays unreachable without input',async()=>{
 const f=fourHopFixture();f.layouts[3].controls=[];
 await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,f.dispatch,f.check,f.context,{language:'ru',label:'М'}),/no reviewed route/);assert.deepEqual(f.points,[]);
});
test('four-hop route stops on uncertain second control receipt without replay or letter',async()=>{
 const f=fourHopFixture();let count=0;
 await assert.rejects(reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,async p=>{if(++count===2)throw new Error('receipt unknown');await f.dispatch(p);},f.check,f.context,{language:'ru',label:'М'}),/receipt unknown/);
 assert.equal(count,2);assert.deepEqual(f.points,[4]);
});

import {clearEditorByBackspace} from './navigation-editor.ts';
function clearFixture(value='Москва') {
 let current=value;let deletes=0;
 const read=async()=>({identity:'boot/window/editor',password:false,focused:true,visible:true,enabled:true,value:current,selectionStart:current.length,selectionEnd:current.length});
 const erase=async()=>{deletes++;current=Array.from(current).slice(0,-1).join('');};
 return {read,erase,get deletes(){return deletes;},get value(){return current;}};
}
test('observed end-caret clearing verifies each Backspace and exact empty result',async()=>{const f=clearFixture();await clearEditorByBackspace(f.read,f.erase);assert.equal(f.deletes,6);assert.equal(f.value,'');});
test('unknown caret or selection cannot authorise deletion',async()=>{for(const changed of [{selectionStart:-1,selectionEnd:-1},{selectionStart:0,selectionEnd:6}]){const f=clearFixture();await assert.rejects(clearEditorByBackspace(async()=>({...await f.read(),...changed}),f.erase),/caret/);assert.equal(f.deletes,0);}});
test('uncertain delete receipt escapes once without replay',async()=>{const f=clearFixture();let calls=0;await assert.rejects(clearEditorByBackspace(f.read,async()=>{calls++;throw new Error('receipt unknown');}),/receipt unknown/);assert.equal(calls,1);});
test('changed identity or unexpected deletion stops before next key',async()=>{for(const changed of ['identity','value']){const f=clearFixture();let reads=0;await assert.rejects(clearEditorByBackspace(async()=>{const n=await f.read();if(++reads===2)return {...n,[changed]:'different'};return n;},f.erase));assert.equal(f.deletes,1);}});

test('one successful delete waits for old stable text then accepts exact new prefix',async()=>{
 let value='a',pending=false,reads=0,keys=0,clock=0;
 await clearEditorByBackspace(async()=>{clock+=100;reads++;if(pending&&reads===4)value='';return {identity:'editor',password:false,focused:true,visible:true,enabled:true,value,selectionStart:value.length,selectionEnd:value.length};},async()=>{keys++;pending=true;},()=>clock);
 assert.equal(keys,1);assert.equal(reads,4);assert.equal(value,'');
});
test('successful delete with no visible effect times out without sending another key',async()=>{
 let keys=0,clock=0;await assert.rejects(clearEditorByBackspace(async()=>{clock+=1000;return {identity:'editor',password:false,focused:true,visible:true,enabled:true,value:'a',selectionStart:1,selectionEnd:1};},async()=>{keys++;},()=>clock),/5000ms deadline/);assert.equal(keys,1);
});
test('pending delete rejects changed old caret before another key',async()=>{
 let reads=0,keys=0;await assert.rejects(clearEditorByBackspace(async()=>({identity:'editor',password:false,focused:true,visible:true,enabled:true,value:'a',selectionStart:++reads===1?1:0,selectionEnd:reads===1?1:0}),async()=>{keys++;}),/caret/);assert.equal(keys,1);
});

test('reviewed Backspace uses current Russian layout without language switching',async()=>{
 const f=controlFixture();f.at=2;f.layouts[2].keys.push({label:'backspace',region:[1,1,3,3],center:[2,2]});let points:number[]=[];
 await reviewedKeyboardRoute(f.layouts,f.baseline,f.capture,async p=>{points.push(p.x);},f.check,f.context,{label:'backspace'});
 assert.deepEqual(points,[2]);
});
