import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {runI18n,readProof,confirmed,resumeProof,main,type Expected} from './i18n-qnx-runtime.ts';
const hash=(n:number)=>n.toString(16).padStart(64,'0');
const expected:Expected={session:{id:'a'.repeat(32),ownership_id:'b'.repeat(32)},serial:'emulator-5554',androidBoot:'aaaaaaaa-1111-2222-3333-444444444444',androidGeneration:hash(1),qnxBoot:'qnx-boot-a',qnxGeneration:hash(2),catalogSha256:hash(3)};
const markers={en:'confirmed English locale and selected dictionary',zh:'confirmed Chinese locale and selected dictionary',ru:'confirmed Russian locale and selected dictionary'};
test('CLI rejects missing log identity before any evidence directory or external process',async()=>{
 const root=path.resolve(import.meta.dirname,'../../../.work162/i18n-driver-config-tests');fs.mkdirSync(root,{recursive:true});const work=fs.mkdtempSync(path.join(root,'missing-logs-'));
 try{const config=path.join(work,'config.json'),evidence=path.join(work,'evidence');fs.writeFileSync(config,JSON.stringify({evidence,installer:'/missing/installer',native:{executable:'/missing/voyemu',root:'/missing/root',session:expected.session},expected,serial:expected.serial}));await assert.rejects(main(config),/log identity required/);assert.equal(fs.existsSync(evidence),false);}finally{fs.rmSync(work,{recursive:true,force:true});}
});
function fixture(options:{partial?:boolean;missing?:boolean;stale?:boolean;restart?:boolean;wrongLocale?:boolean;duplicate?:boolean}={}){
 let e=structuredClone(expected),enabled=false,locale:'en'|'ru'|'zh'='en',page='voboost',config=1,revision=1,desired=1,birth=10,uiRevision=0,lastNodes:any[]=[],renderSeq=1,logReads=0;
 const calls:any[]=[],endpoints:any[]=[];
 const observe=()=>{
  const q={schemaVersion:1,domain:'cluster',snapshotRevision:options.stale?1:++revision,catalogSha256:e.catalogSha256,generationId:e.qnxGeneration,desiredRevision:desired,desiredSha256:hash(desired+100),observationRevision:options.stale?1:revision,bootId:e.qnxBoot,daemonEpoch:hash(e.qnxBoot==='qnx-boot-a'?4:40),deliveryState:'idle',agents:[{id:'i18n-ru',enabled,options:{},state:enabled?'applied':'disabled',reason:'all-selected-targets-applied',process:null,targets:['gauge','light','point'].map((targetProcess,i)=>({id:'i18n-ru',targetProcess,state:enabled?'applied':'disabled',reason:markers[options.wrongLocale?'en':locale],process:{pid:100+birth+i,birthIdentity:hash(birth+i),executableSha256:hash(5),endpointEpoch:hash(1000+birth+i),lifecycleGeneration:1}}))}]};
  if(options.partial)q.agents[0].targets=q.agents[0].targets.slice(0,1);
  if(options.duplicate)q.agents[0].targets[2]=q.agents[0].targets[0];
  const runtime:any={generationId:e.androidGeneration,publisher:{bootId:e.androidBoot},cluster:q};if(options.missing){delete runtime.cluster;runtime.clusterUnavailable='snapshot-unavailable';}
  return {schema:2,source:'voboost',status:'ready',boot_id:e.androidBoot,data:{root_proof:'fixed-path-owner-mode-boot-fenced',files:{config:{status:'present',revision:String(config),data:{agents:{'i18n-ru':{enabled,options:{}}}}},runtime:{status:'present',current:true,provenance:{current:true,proof:'same-boot-live-publisher',age_ms:10},data:runtime}}}};
 };
 const make=(id:string,text:string,parent:any=null,extra:any={})=>({id,text,parent,package:'com.qinggan.app.setting',resource_id:'',window_id:1,class:'android.widget.Button',clickable:true,visible:true,enabled:true,bounds:{x:20,y:20,width:160,height:30},...extra});
 const io={observe:async()=>observe(),pause:async()=>{},endpoint:async(value:any)=>{endpoints.push(value);},logs:async()=>{const fields={defaultLocale:locale+'-'+locale.toUpperCase(),resourceLocale:locale,effectiveLanguage:locale,elapsed_ms:1},record:any={schemaVersion:1,sequence:renderSeq,timestamp:'2026-10-25T00:00:00Z',source:{kind:'agent'},level:'info',component:'agent',message:'diagnostic',fields:{}};return {archive:'logs-'+(++logReads)+'.tar.xz',capture:'logs-'+logReads+'-capture.json',records:[record],diagnostics:[{record,agent:'ui-qinggan',event:'ui.render',outcome:'complete',fields}]};},api:async(op:string,args:any={})=>{
  calls.push({op,args});
  if(op==='session.status')return {ok:true,session_id:e.session.id,android_pid:123,stopped:false,deliveries:{android:{pid:123,serial:e.serial,quickboot:{mode:'restored'}}}};
  if(op==='multimedia.launch')return {};
  if(op==='session.ui'){
   const list=[make('tab-v','Voboost'),make('tab-d','Display')];
   if(page==='voboost')list.push(make('row',''),make('label','Russian language','row'),make('toggle','','row',{checkable:true,checked:enabled,resource_id:'com.qinggan.app.setting:id/hint_switch_button'}));
   else list.push(make('group','',null,{resource_id:'com.qinggan.app.setting:id/display_language_radio_group'}),...(['en','zh','ru'] as const).filter(l=>enabled||l!=='ru').map(l=>make('radio-'+l,l==='ru'?'Русский':l,'group',{checkable:true,checked:locale===l,resource_id:l==='ru'?'':'com.qinggan.app.setting:id/display_language_radio_'+(l==='en'?'english':'chinese')})));
   lastNodes=list.map((n,i)=>({...n,bounds:{...n.bounds,y:20+i*45},clipped_bounds:{...n.bounds,y:20+i*45}}));
   return {session_id:e.session.id,android_pid:123,source:'android.ui',source_instance:'ui-current',revision:String(uiRevision+1),boot_id:e.androidBoot,complete:true,observation_id:String(++uiRevision),screens:[{screen:'primary',width:1920,height:720,windows:[{id:1,type:1,layer:0,package:'com.qinggan.app.setting',title:'Setting',bounds:{x:0,y:0,width:1920,height:720}}],nodes:lastNodes}]};
  }
  if(op==='input.tap'){
   assert.equal(args.expected.boot_id,e.androidBoot);assert.equal(args.expected.revision,String(uiRevision));assert.equal(args.expected.source_instance,'ui-current');assert.equal(args.expected.window.id,1);assert.equal(args.expected.target.window_id,1);assert.equal(args.expected.window.layer,0);assert.deepEqual(args.expected.target.clipped_bounds,args.expected.target.bounds);
   const n=lastNodes.find(n=>args.y===Math.round(n.bounds.y+n.bounds.height/2));assert.ok(n);
   if(n.id==='tab-v'){page='voboost';renderSeq++;}else if(n.id==='tab-d')page='display';else if(n.id==='toggle'){
    enabled=!enabled;config++;desired++;if(!enabled&&options.restart!==false)birth+=10;
   }else if(n.id.startsWith('radio-'))locale=n.id.slice(6) as typeof locale;
   return {};
  }
  throw Error('forbidden operation '+op);
 }};
 return {io,calls,endpoints,observe,get expected(){return e;},reboot(){e={...e,androidBoot:'bbbbbbbb-1111-2222-3333-444444444444',qnxBoot:'qnx-boot-b'};birth+=20;},enable(){enabled=true;locale='ru';},setLocale(l:'en'|'ru'|'zh'){locale=l;}};
}
test('all three current owner ACKs run RU/EN/ZH, disable/restart and arm external reboot',async()=>{
 const f=fixture(),r=await runI18n(f.io,f.expected);assert.equal(r.status,'AWAITING_REBOOT',JSON.stringify(r));
 assert.deepEqual(r.steps.map((s:any)=>s.step),['enable','locale-ru','locale-en','locale-zh','disable-owner-restart','external-reboot']);
 assert.ok(r.checkpoint);assert.equal(r.androidBinderLocale.status,'NOT_RUN');
 assert.ok(f.calls.every(c=>['session.status','multimedia.launch','session.ui','input.tap'].includes(c.op)));
 f.reboot();const resumed=await runI18n(f.io,f.expected,r.checkpoint);assert.equal(resumed.status,'PASS',JSON.stringify(resumed));
});
test('raw endpoints bracket all phases and retain fresh selected picker plus log archive references',async()=>{
 const f=fixture(),r=await runI18n(f.io,f.expected);assert.equal(r.status,'AWAITING_REBOOT');
 assert.deepEqual(f.endpoints.map(e=>e.stage),['enable','locale-ru','locale-en','locale-zh','disable-owner-restart','reboot-armed']);
 for(const endpoint of f.endpoints){readProof(endpoint.beforeObserve,f.expected);readProof(endpoint.afterObserve,f.expected);if(endpoint.stage.startsWith('locale-')||endpoint.stage==='reboot-armed'){assert.match(endpoint.baselineLogs,/^logs-/);assert.match(endpoint.currentLogs,/^logs-/);assert.match(endpoint.baselineLogCapture,/capture.json$/);assert.match(endpoint.currentLogCapture,/capture.json$/);assert.notEqual(endpoint.baselineLogs,endpoint.currentLogs);assert.equal(endpoint.pickerUi.complete,true);const selected=endpoint.pickerUi.screens[0].nodes.filter((n:any)=>n.checked===true);assert.equal(selected.length,1);}}
 f.reboot();await runI18n(f.io,f.expected,r.checkpoint);assert.equal(f.endpoints.at(-1).stage,'reboot-restoration');assert.ok(f.endpoints.at(-1).pickerUi);assert.ok(f.endpoints.at(-1).baselineLogs);
 const disabled=f.endpoints.find(e=>e.stage==='disable-owner-restart');assert.ok(disabled.baselineLogs&&disabled.currentLogs&&disabled.baselineLogCapture&&disabled.currentLogCapture);assert.ok(!disabled.pickerUi.screens[0].nodes.some((n:any)=>n.text==='Русский'));assert.ok(disabled.pickerUi.screens[0].nodes.some((n:any)=>n.resource_id==='com.qinggan.app.setting:id/display_language_radio_chinese'&&n.checked));
});
test('QNX disabled ACK cannot hide a surviving Android Russian contribution',async()=>{
 const f=fixture(),original=f.io.api;f.io.api=async(op,args)=>{const v=await original(op,args);if(op==='session.ui'&&v.screens[0].nodes.some((n:any)=>n.id==='group')&&!f.observe().data.files.runtime.data.cluster.agents[0].enabled){const base=v.screens[0].nodes.find((n:any)=>n.id==='radio-en');v.screens[0].nodes.push({...base,id:'surviving-ru',text:'Русский',resource_id:'',checked:false});}return v;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'FAIL');assert.match(r.reason,/contribution still present/);assert.ok(!f.endpoints.some(e=>e.stage==='disable-owner-restart'));
});
test('QNX disabled ACK cannot replace actual Android locale restoration',async()=>{
 const f=fixture(),original=f.io.logs;f.io.logs=async()=>{const value=await original();if(!f.observe().data.files.runtime.data.cluster.agents[0].enabled)for(const row of value.diagnostics)row.fields.resourceLocale='ru';return value;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'FAIL');assert.match(r.reason,/process\/resource locale differs/);assert.ok(!f.endpoints.some(e=>e.stage==='disable-owner-restart'));
});
test('stale Android diagnostic archives cannot emit a locale endpoint',async()=>{
 const f=fixture(),baseline=await f.io.logs();f.io.logs=async()=>baseline;const r=await runI18n(f.io,f.expected);assert.equal(r.status,'NOT_RUN');assert.match(r.reason,/render locale unavailable/);assert.ok(!f.endpoints.some(e=>e.stage==='locale-ru'));
});
test('fresh native ACK cannot mask wrong Android resource locale',async()=>{
 const f=fixture(),original=f.io.logs;f.io.logs=async()=>{const v=await original();for(const row of v.diagnostics)row.fields.resourceLocale='other';return v;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'FAIL');assert.match(r.reason,/process\/resource locale differs/);
});
test('a textual locale prefix is not a language component',async()=>{
 for(const prefix of ['rubbish','ruRU']){const f=fixture(),original=f.io.logs;f.io.logs=async()=>{const value=await original();for(const row of value.diagnostics)row.fields.resourceLocale=prefix;return value;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'FAIL');assert.match(r.reason,/process\/resource locale differs/);assert.ok(!f.endpoints.some(e=>e.stage==='locale-ru'));}
});
test('a post-action unchecked picker cannot emit native ACK endpoint',async()=>{
 const f=fixture(),original=f.io.api;f.io.api=async(op,args)=>{const v=await original(op,args);if(op==='session.ui'&&v.screens[0].nodes.some((n:any)=>n.text==='Русский'&&n.checked))for(const n of v.screens[0].nodes)n.checked=false;return v;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'NOT_RUN');assert.ok(!f.endpoints.some(e=>e.stage==='locale-ru'));
});
test('old partial ACK is RED even when aggregate state says applied; all three are GREEN',()=>{
 const p=fixture({partial:true});p.enable();const raw:any=p.observe();
 assert.equal(raw.data.files.runtime.data.cluster.agents[0].targets[0].state,'applied');
 assert.throws(()=>readProof(raw,p.expected),/owner ACK/);
 const all=fixture();all.enable();assert.equal(confirmed(readProof(all.observe(),all.expected),'ru'),true);
});
test('missing native API returns NOT_RUN before any UI mutation',async()=>{const f=fixture({missing:true});const r=await runI18n(f.io,f.expected);assert.equal(r.status,'NOT_RUN');assert.equal(f.calls.some(c=>c.op==='input.tap'),false);});
test('duplicate owner proof cannot replace the missing owner',async()=>{const f=fixture({duplicate:true});assert.equal((await runI18n(f.io,f.expected)).status,'FAIL');});
test('stale native observation never passes a locale action',async()=>{const f=fixture({stale:true});assert.equal((await runI18n(f.io,f.expected)).status,'FAIL');});
test('English ACK cannot prove Russian even with Russian radio selected',async()=>{const f=fixture({wrongLocale:true});assert.equal((await runI18n(f.io,f.expected)).status,'FAIL');});
test('disable requires independently new process identities for every previous owner',async()=>{const f=fixture({restart:false});const r=await runI18n(f.io,f.expected);assert.equal(r.status,'FAIL');assert.match(r.reason,/restart/);});
test('wrong generation, QNX boot or historical Android publisher cannot prove readiness',()=>{
 const f=fixture();f.enable();
 for(const field of ['generationId','bootId','catalogSha256']){const raw:any=f.observe();raw.data.files.runtime.data.cluster[field]='foreign';assert.throws(()=>readProof(raw,f.expected));}
 const raw:any=f.observe();raw.data.files.runtime.current=false;assert.throws(()=>readProof(raw,f.expected));
});
test('resume rejects old boot, foreign session, stale locale and reused native identities',async()=>{
 const f=fixture(),r=await runI18n(f.io,f.expected),cp=r.checkpoint;assert.ok(cp);
 assert.throws(()=>resumeProof(f.observe(),cp,f.expected),/changed Android\/QNX boot/);
 f.reboot();const current=structuredClone(f.expected);current.session.id='f'.repeat(32);assert.throws(()=>resumeProof(f.observe(),cp,current),/same owned session/);
 f.setLocale('en');assert.throws(()=>resumeProof(f.observe(),cp,f.expected),/restoration/);f.setLocale('ru');
 const raw:any=f.observe();for(const t of raw.data.files.runtime.data.cluster.agents[0].targets)t.process=cp.processes[t.targetProcess];assert.throws(()=>resumeProof(raw,cp,f.expected),/restoration/);
});
test('official observation device is bound to the already-owned session',async()=>{const f=fixture();assert.equal((await runI18n(f.io,{...f.expected,serial:'1234567'})).status,'NOT_RUN');assert.equal(f.calls.length,1);});

test('malformed checkpoints and unchanged daemon epoch cannot prove reboot',async()=>{const f=fixture(),r=await runI18n(f.io,f.expected);f.reboot();for(const cp of [{}, {...r.checkpoint,expected:null}, {...r.checkpoint,processes:{} }])assert.throws(()=>resumeProof(f.observe(),cp as any,f.expected),/malformed/);const raw:any=f.observe();raw.data.files.runtime.data.cluster.daemonEpoch=r.checkpoint.daemonEpoch;assert.throws(()=>resumeProof(raw,r.checkpoint,f.expected),/restoration/);});
test('missing UI nodes and foreign language tree are unavailable',async()=>{for(const mode of ['missing','foreign']){const f=fixture(),api=f.io.api;let foreign:any[]=[];let foreignTap=false;f.io.api=async(op,args)=>{if(op==='input.tap'&&foreign.some(n=>args.y===Math.round(n.bounds.y+n.bounds.height/2)))foreignTap=true;const r:any=await api(op,args);if(op==='session.ui'){if(mode==='missing')delete r.screens[0].nodes;else for(const n of r.screens[0].nodes)if(n.id==='group'||n.id.startsWith('radio-'))n.package='foreign.package';foreign=(r.screens[0].nodes??[]).filter((n:any)=>n.package==='foreign.package');}return r;};const r=await runI18n(f.io,f.expected);assert.equal(r.status,'NOT_RUN',JSON.stringify(r));assert.equal(foreignTap,false);}});

test('null official/UI records stay unavailable rather than throwing schema errors',async()=>{const f=fixture();for(const scope of ['agent','target']){const raw:any=f.observe();if(scope==='agent')raw.data.files.runtime.data.cluster.agents=[null];else raw.data.files.runtime.data.cluster.agents[0].targets=[null];assert.throws(()=>readProof(raw,f.expected),/missing|schema/);}const api=f.io.api;f.io.api=async(op,args)=>op==='session.ui'?null:api(op,args);assert.equal((await runI18n(f.io,f.expected)).status,'NOT_RUN');});

test('foreign group cannot authorize Setting-package radio descendants',async()=>{const f=fixture(),api=f.io.api;let radios:any[]=[];let tapped=false;f.io.api=async(op,args)=>{if(op==='input.tap'&&radios.some(n=>args.y===Math.round(n.bounds.y+n.bounds.height/2)))tapped=true;const r:any=await api(op,args);if(op==='session.ui'){radios=r.screens[0].nodes.filter((n:any)=>n.id.startsWith('radio-'));for(const n of r.screens[0].nodes)if(n.id==='group')n.package='foreign.package';}return r;};assert.equal((await runI18n(f.io,f.expected)).status,'NOT_RUN');assert.equal(tapped,false);});

test('pointer mutation requires observed source/window/target fields',async()=>{for(const field of ['window','target','source','pid']){const f=fixture(),api=f.io.api;f.io.api=async(op,args)=>{const r:any=await api(op,args);if(op==='session.ui'){if(field==='window')delete r.screens[0].windows;else if(field==='target')delete r.screens[0].nodes[0].clipped_bounds;else if(field==='source')delete r.source_instance;else r.android_pid++;}return r;};assert.equal((await runI18n(f.io,f.expected)).status,'NOT_RUN');assert.equal(f.calls.some(c=>c.op==='input.tap'),false);}});
test('simulated dispatch rejects changed source, moved window and changed target before effect',async()=>{for(const field of ['source','window','target']){const f=fixture(),api=f.io.api;let effects=0;f.io.api=async(op,args)=>{if(op==='input.tap'){assert.ok(args.expected);const actual=structuredClone(args.expected);if(field==='source')actual.source_instance='new-source';else if(field==='window')actual.window.bounds.x++;else actual.target.text='changed';if(JSON.stringify(actual)!==JSON.stringify(args.expected))throw Error('native pointer fence rejected '+field);effects++;}return api(op,args);};assert.equal((await runI18n(f.io,f.expected)).status,'FAIL');assert.equal(effects,0);}});
