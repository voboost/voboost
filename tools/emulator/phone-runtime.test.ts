import test from 'node:test';
import {strict as assert} from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import {runPhone,phoneContacts,retainPhoneCapture} from './phone-runtime.ts';
const expected={session:'owned',serial:'emulator-5554',boot:'boot',generation:'generation',captureDirectory:'/project/evidence'};
function fixture(change:(op:string,value:any)=>any=(_op,value)=>value,pointerFault?:'stale'|'moved-window'|'changed-target'){
 let connected=false,call=false,incoming=false,seq=0,uiSeq=0,page='empty',pointerEffects=0,lastTree:any;const calls:string[]=[];
 const node=(id:string,rid:string,extra:any={})=>{const n={id,resource_id:'com.qinggan.bluetoothphone:id/'+rid,package:'com.qinggan.bluetoothphone',visible:true,enabled:true,clickable:true,window_id:1,class:'android.view.View',text:'',bounds:{x:400,y:180,width:80,height:40},...extra};return {...n,clipped_bounds:{...n.bounds}};};
 const io={pause:async()=>{},observe:async()=>({schema:2,source:'voboost',boot_id:'boot',status:'ready',data:{root_proof:'fixed-path-owner-mode-boot-fenced',files:{config:{status:'present',revision:'1',data:{agents:{phone:{enabled:true}}}},runtime:{status:'present',current:true,provenance:{current:true,proof:'same-boot-live-publisher',age_ms:0},data:{publisher:{bootId:'boot',daemonPid:'20',daemonStarttime:'50'},generationId:'generation',injections:[{id:'phone',process:'com.qinggan.bluetoothphone',state:'active'}]}}}}}),api:async(op:string,args:any={})=>{
  calls.push(op);let value:any={};
  if(op==='session.status')value={ok:true,session_id:'owned',android_pid:123,stopped:false,deliveries:{android:{pid:123,serial:'emulator-5554',quickboot:{mode:'restored'}}}};
  if(op==='input.tap'){
   const window=lastTree.screens[0].windows[0],target=lastTree.screens[0].nodes.find((n:any)=>n.resource_id===args.expected?.target?.resource_id);
   assert.ok(target);assert.deepEqual(args.expected,{boot_id:lastTree.boot_id,source_instance:lastTree.source_instance,revision:lastTree.revision,window:{id:window.id,type:window.type,layer:window.layer,bounds:window.bounds,title:window.title,package:window.package},target:{window_id:target.window_id,resource_id:target.resource_id,package:target.package,class:target.class,text:target.text,bounds:target.bounds,clipped_bounds:target.clipped_bounds}});
   // Simulated pre-dispatch rejection, not execution of Voyemu's native validator.
   // Voyemu intentionally does not compare revision equality on a fresh reread.
   if(pointerFault){const current=structuredClone(args.expected);if(pointerFault==='stale')current.source_instance='replacement-source';if(pointerFault==='moved-window')current.window.bounds.x+=1;if(pointerFault==='changed-target')current.target.bounds.x+=1;assert.notDeepEqual(args.expected,current);throw Error('TEST_INPUT_STALE: simulated source/window/target mismatch before effects');}pointerEffects++;
  }
  if(op==='multimedia.phone.configure')assert.deepEqual(args.payload.contacts,phoneContacts);
  if(op==='multimedia.phone.connect')connected=true;
  if(op==='multimedia.phone.disconnect')connected=false;
  if(op==='multimedia.phone.hangup'){call=false;incoming=false;}
  if(op==='multimedia.phone.incoming'){assert.equal(args.payload.number,phoneContacts[1].number);incoming=true;}
  if(op==='input.tap'&&args.x===1240)call=true;
  if(op==='input.tap'&&args.x===440&&args.y===200)page='contacts';
  if(op==='input.tap'&&args.x===640)page='detail';
  if(op.startsWith('multimedia.phone.'))seq++;
  if(op==='multimedia.phone.state')value={schema:1,session_id:'owned',boot_id:'boot',provider_pid:12,sequence:seq,data:{ok:true,connected,pbap_connected:connected,sync_state:97,call:{outgoing:call,number:call?phoneContacts[0].number:incoming?phoneContacts[1].number:''},outgoing:call?[phoneContacts[0].number]:[]}};
  if(op==='session.ui')value={session_id:'owned',android_pid:123,source:'android.ui',source_instance:'source',revision:String(uiSeq+1),boot_id:'boot',complete:true,observation_id:String(++uiSeq),screens:[{screen:'primary',width:1920,height:720,windows:[{id:1,type:1,layer:1,package:'com.qinggan.bluetoothphone',title:'Phone',bounds:{x:0,y:0,width:1920,height:720}}],nodes:[node('tab','ll_contact_title'),...(page==='contacts'?[node('row','info_rl',{bounds:{x:600,y:180,width:80,height:40}}),...phoneContacts.map((c,i)=>node('name'+i,'tv_name',{text:c.name,parent:'row',clickable:false}))]:[]),...(page==='detail'?[node('dial','dial_out_rl',{bounds:{x:1200,y:180,width:80,height:40}})]:[])]}]};
  if(op==='session.screenshot'){assert.deepEqual(args.screens,['primary','secondary','cluster']);value={manual:true};}
  if(op==='session.ui')lastTree=structuredClone(value);
  return change(op,value);
 }};return {io,calls,effects:()=>pointerEffects};
}
test('actual documented phone operations and OEM UI route preserve +7; captures never imply QNX acceptance',async()=>{
 const f=fixture();const r=await runPhone(f.io,expected);
 assert.equal(r.status,'NOT_RUN');assert.match(r.reason,/QNX/);
 assert.deepEqual(r.steps.filter((s:any)=>s.status==='PASS').map((s:any)=>s.step),['PBAP-Russian-names','outgoing-plus7','reconnect','cleanup-disconnected']);
 assert.equal(f.calls.filter(x=>x==='session.screenshot').length,5);
 assert.ok(!f.calls.some(x=>/session\.(start|stop|attach)|reboot|restart|phone\.dial/.test(x)));
});
for(const field of ['revision','source_instance','windows'])test('missing pointer '+field+' is NOT_RUN before tap',async()=>{
 const f=fixture((op,v)=>{if(op==='session.ui'){if(field==='windows')delete v.screens[0].windows;else delete v[field];}return v;});const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.equal(f.effects(),0);assert.ok(!f.calls.includes('input.tap'));
});
for(const fault of ['stale','moved-window','changed-target'] as const)test('simulated pre-dispatch pointer rejection '+fault+' has no effects',async()=>{
 const f=fixture(undefined,fault);const r=await runPhone(f.io,expected);assert.equal(r.status,'FAIL');assert.equal(f.effects(),0);assert.match(r.reason,/TEST_INPUT_STALE/);
});
test('cleanup still disconnects and verifies readback if hangup rejects',async()=>{
 let hangups=0,disconnects=0;const f=fixture((op,v)=>{if(op==='multimedia.phone.hangup'&&++hangups>=1)throw Error('hangup rejected');if(op==='multimedia.phone.disconnect')disconnects++;return v;});
 const r=await runPhone(f.io,expected);assert.equal(r.status,'FAIL');assert.ok(disconnects>=1);assert.ok(r.steps.some((s:any)=>s.step==='cleanup-disconnected'&&s.status==='PASS'));
});
test('capture retention copies declared exact bytes before removing only owned capture files',()=>{
 const root=path.resolve(import.meta.dirname,'../../../.work162/phone-driver-tests');fs.mkdirSync(root,{recursive:true});const work=fs.mkdtempSync(path.join(root,'capture-'));
 try{
  const source=path.join(work,'stage'),target=path.join(work,'retained');fs.mkdirSync(source);
  const manifest={schema:2,session_id:'owned',android_boot_id:'boot',accepted:true,frames:['primary','secondary','cluster'].map(screen=>({screen,file:screen+'.png'}))};
  const reply={...manifest,artifact_dir:source,manifest:path.join(source,'manifest.json')};fs.writeFileSync(reply.manifest,JSON.stringify(manifest));for(const f of manifest.frames)fs.writeFileSync(path.join(source,f.file),'pixels-'+f.screen);
  const r=retainPhoneCapture(reply,source,target,expected);assert.equal(r.retained_directory,target);assert.ok(!fs.existsSync(source));assert.equal(fs.readFileSync(path.join(target,'cluster.png'),'utf8'),'pixels-cluster');
  const bad=path.join(work,'bad');fs.mkdirSync(bad);fs.writeFileSync(path.join(bad,'sentinel'),'keep');
  assert.throws(()=>retainPhoneCapture({...reply,artifact_dir:bad,manifest:path.join(bad,'manifest.json'),session_id:'foreign'},bad,path.join(work,'rejected'),expected));assert.equal(fs.readFileSync(path.join(bad,'sentinel'),'utf8'),'keep');
 }finally{fs.rmSync(work,{recursive:true,force:true});}
});
for(const kind of ['stale','partial','wrong-package','spaced-name','wrong-boot','wrong-session','wrong-generation'])test('rejects '+kind,async()=>{
 const f=fixture((op,v)=>{if(op==='session.ui'){
  if(kind==='stale')v.observation_id='same';if(kind==='partial')v.complete=false;
  if(kind==='wrong-package')v.screens[0].nodes.forEach((n:any)=>n.package='foreign');
  if(kind==='spaced-name')v.screens[0].nodes.forEach((n:any)=>{if(n.text)n.text=[...n.text].join(' ');});
 }if(op==='multimedia.phone.state'){if(kind==='wrong-boot')v.boot_id='other';if(kind==='wrong-session')v.session_id='foreign';}return v;});
 if(kind==='wrong-generation'){const original=f.io.observe;f.io.observe=async()=>{const v=await original();v.data.files.runtime.data.generationId='foreign';return v;};}
 const r=await runPhone(f.io,expected);assert.equal(r.status,kind==='wrong-boot'||kind==='wrong-session'?'FAIL':'NOT_RUN');assert.ok(!r.steps.some((s:any)=>s.step==='outgoing-plus7'));
});

for(const defect of ['foreign-source','missing-schema','foreign-root','historical-publisher','stale-publisher','negative-age','wrong-publisher-boot','disabled-phone','missing-runtime-status','duplicate-phone'] as const)test('current phone authority rejects '+defect+' before stimulus',async()=>{
 const f=fixture(),observe=f.io.observe;f.io.observe=async()=>{const v=await observe(),r=v.data.files.runtime;
  if(defect==='foreign-source')v.source='foreign';if(defect==='missing-schema')delete (v as any).schema;if(defect==='foreign-root')v.data.root_proof='foreign';
  if(defect==='historical-publisher')r.provenance.current=false;if(defect==='stale-publisher')r.provenance.age_ms=5001;if(defect==='negative-age')r.provenance.age_ms=-1;
  if(defect==='wrong-publisher-boot')r.data.publisher.bootId='foreign';if(defect==='disabled-phone')v.data.files.config.data.agents.phone.enabled=false;if(defect==='missing-runtime-status')delete (r as any).status;
  if(defect==='duplicate-phone')r.data.injections.push({...r.data.injections[0]});return v;};
 const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.ok(!f.calls.includes('multimedia.phone.configure'));assert.ok(!r.steps.some((s:any)=>s.status==='PASS'));
});
for(const loss of ['revoked','publisher-replaced'] as const)test('outgoing +7 cannot pass after '+loss+' during actual dial tap',async()=>{
 const f=fixture(),api=f.io.api,observe=f.io.observe;let dialed=false;f.io.api=async(op,args={})=>{const v=await api(op,args);if(op==='input.tap'&&args.x===1240)dialed=true;return v;};
 f.io.observe=async()=>{const v=await observe();if(dialed){if(loss==='revoked')v.data.files.runtime.current=false;else v.data.files.runtime.data.publisher.daemonStarttime='51';}return v;};
 const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.match(r.reason,/phone.*(authority|publisher)/);assert.ok(!r.steps.some((s:any)=>s.step==='outgoing-plus7'));assert.ok(f.calls.includes('multimedia.phone.disconnect'));
});
test('late revocation during reconnect capture cannot publish reconnect PASS',async()=>{
 const f=fixture(),api=f.io.api,observe=f.io.observe;let revoked=false;f.io.api=async(op,args={})=>{const v=await api(op,args);if(op==='session.screenshot'&&args.artifact_dir.endsWith('/reconnected'))revoked=true;return v;};
 f.io.observe=async()=>{const v=await observe();if(revoked)v.data.files.config.data.agents.phone.enabled=false;return v;};
 const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.match(r.reason,/phone.*authority/);assert.ok(!r.steps.some((s:any)=>s.step==='reconnect'));assert.ok(f.calls.includes('multimedia.phone.disconnect'));
});
test('cleanup cannot conceal final phone authority revocation',async()=>{
 const f=fixture(),api=f.io.api,observe=f.io.observe;let reconnected=false,revoked=false;f.io.api=async(op,args={})=>{const v=await api(op,args);if(op==='session.screenshot'&&args.artifact_dir.endsWith('/reconnected'))reconnected=true;if(reconnected&&op==='multimedia.phone.disconnect')revoked=true;return v;};
 f.io.observe=async()=>{const v=await observe();if(revoked)v.data.files.runtime.provenance.current=false;return v;};
 const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.match(r.reason,/phone.*authority/);assert.ok(r.steps.some((s:any)=>s.step==='cleanup-disconnected'&&s.status==='PASS'));
});

test('authority lost at configure pre-dispatch cannot trigger untouched phone cleanup',async()=>{
 const f=fixture(),observe=f.io.observe;let reads=0;f.io.observe=async()=>{const v=await observe();if(++reads===2)v.data.files.runtime.current=false;return v;};
 const r=await runPhone(f.io,expected);assert.equal(r.status,'NOT_RUN');assert.ok(!f.calls.some(op=>op.startsWith('multimedia.phone.')));
});
