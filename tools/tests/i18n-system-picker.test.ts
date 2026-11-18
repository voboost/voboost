import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {admitI18nSystemPicker,assertCurrentI18nObservation,checkedSystemPicker} from '../emulator/i18n-system-picker.ts';
import {readProof,type Expected} from '../emulator/i18n-qnx-runtime.ts';
import {logArchive} from './installer-log-fixture.ts';
const hash=(n:number)=>n.toString(16).padStart(64,'0');
const marker={en:'confirmed English locale and selected dictionary',zh:'confirmed Chinese locale and selected dictionary',ru:'confirmed Russian locale and selected dictionary'};
function fixture(){
 const parent=path.resolve(import.meta.dirname,'../../../.work162');fs.mkdirSync(parent,{recursive:true});const root=fs.mkdtempSync(path.join(parent,'i18n-ingestion-'));
 const directories={runDirectory:path.join(root,'run'),resumeDirectory:path.join(root,'resume')};Object.values(directories).forEach(p=>fs.mkdirSync(p));
 const old:Expected={session:{id:'a'.repeat(32),ownership_id:'b'.repeat(32)},serial:'emulator-5582',androidBoot:'android-old',androidGeneration:hash(1),qnxBoot:'qnx-old',qnxGeneration:hash(2),catalogSha256:hash(3)};
 const current:Expected={...old,androidBoot:'android-new',qnxBoot:'qnx-new'};
 const logIdentity={serial:old.serial,version:'1.0.0-beta1',manifestSha256:hash(4)};let rev=1,desired=1;
 const write=(dir:string,name:string,v:any)=>{fs.writeFileSync(path.join(dir,name),Buffer.isBuffer(v)?v:JSON.stringify(v)+'\n');return name;};
 const snapshot=(e:Expected,locale:'en'|'zh'|'ru',enabled:boolean,birth:number)=>({schema:2,source:'voboost',status:'ready',boot_id:e.androidBoot,data:{root_proof:'fixed-path-owner-mode-boot-fenced',files:{config:{status:'present',revision:String(desired),data:{agents:{'i18n-ru':{enabled}}}},runtime:{status:'present',current:true,provenance:{current:true,proof:'same-boot-live-publisher',age_ms:10},data:{generationId:e.androidGeneration,publisher:{bootId:e.androidBoot},cluster:{schemaVersion:1,domain:'cluster',snapshotRevision:++rev,observationRevision:rev,desiredRevision:desired,desiredSha256:hash(desired+100),catalogSha256:e.catalogSha256,generationId:e.qnxGeneration,bootId:e.qnxBoot,daemonEpoch:hash(e===old?5:6),agents:[{id:'i18n-ru',enabled,state:enabled?'applied':'disabled',targets:['gauge','light','point'].map((targetProcess,i)=>({id:'i18n-ru',targetProcess,state:enabled?'applied':'disabled',reason:marker[locale],process:{pid:100+birth+i,birthIdentity:hash(birth+i),executableSha256:hash(9),endpointEpoch:hash(birth+100+i),lifecycleGeneration:1}}))}]}}}}}});
 const ui=(e:Expected,locale:'en'|'zh'|'ru')=>({session_id:e.session.id,boot_id:e.androidBoot,source:'android.ui',source_instance:'real-source',observation_id:String(++rev),complete:true,screens:[{windows:[{id:1,package:'com.qinggan.app.setting',type:1}],nodes:[{id:'group',parent:null,package:'com.qinggan.app.setting',resource_id:'com.qinggan.app.setting:id/display_language_radio_group',window_id:1,visible:true},...(['en','zh','ru'] as const).map(l=>({id:l,parent:'group',package:'com.qinggan.app.setting',resource_id:l==='ru'?'':'com.qinggan.app.setting:id/display_language_radio_'+(l==='en'?'english':'chinese'),text:l==='ru'?'Русский':l,window_id:1,visible:true,enabled:true,checkable:true,checked:l===locale}))]}]});
 const row=(sequence:number,locale:string)=>({schemaVersion:1,sequence,timestamp:'2026-10-08T10:00:00Z',source:{kind:'agent',id:'ui-qinggan'},level:'INFO',component:'agent',message:'VOBDBG1 '+JSON.stringify({agent:'ui-qinggan',event:'ui.compose',outcome:'complete',fields:{defaultLocale:locale+'-RU',effectiveLanguage:locale,resourceLocale:locale,elapsed_ms:2}}),fields:{}});
 const logs=(dir:string,name:string,sequence:number,locale:string)=>{
  const folder=name+'-logs';fs.mkdirSync(path.join(dir,folder));const archive=write(dir,folder+'/logs.tar.xz',logArchive(logIdentity,[row(sequence,locale)]));
  const stdout=write(dir,name+'-stdout.log',Buffer.from(path.join(dir,archive)+'\n')),stderr=write(dir,name+'-stderr.log',Buffer.alloc(0));
  const capture=write(dir,name+'-capture.json',{schema:1,operation:'installer-logs',startedAtUtc:'2026-10-08T10:00:00Z',completedAtUtc:'2026-10-08T10:00:02Z',exitCode:0,stdout,stderr,archive,argv:['--logs',path.join(dir,folder),'--device',old.serial],deviceSerial:old.serial});return {archive,capture};
 };
 let last:any,armed:any;
 const endpoint=(dir:string,stage:string,e:Expected,locale:'en'|'zh'|'ru',before:any,after:any,index:number)=>{
  const value:any={stage,beforeObserve:write(dir,stage+'-before.json',before),afterObserve:write(dir,stage+'-after.json',after)};
  if(stage.startsWith('locale-')||stage.startsWith('reboot-')||stage==='disable-owner-restart'){const picker=ui(e,locale);if(stage==='disable-owner-restart')picker.screens[0].nodes=picker.screens[0].nodes.filter((n:any)=>n.id!=='ru');value.pickerUi=write(dir,stage+'-ui.json',picker);const b=logs(dir,stage+'-baseline',100+index*10,locale),c=logs(dir,stage+'-current',101+index*10,locale);Object.assign(value,{baselineLogs:b.archive,currentLogs:c.archive,baselineLogCapture:b.capture,currentLogCapture:c.capture});}return value;
 };
 const stages=['enable','locale-ru','locale-en','locale-zh','disable-owner-restart','reboot-armed'];
 const endpoints=stages.map((stage,i)=>{const locale=stage==='locale-en'?'en':stage==='locale-zh'||stage==='disable-owner-restart'?'zh':'ru';const before=last??snapshot(old,'en',false,10);if(['enable','disable-owner-restart','reboot-armed'].includes(stage))desired++;const after=snapshot(old,locale,stage!=='disable-owner-restart',i<4?10:20);last=after;if(stage==='reboot-armed')armed=readProof(after,old);return endpoint(directories.runDirectory,stage,old,locale,before,after,i);});
 write(directories.runDirectory,'I18N-ENDPOINTS.json',{schema:1,expected:old,endpoints});
 const checkpoint={schema:1,stage:'external-reboot',daemonEpoch:armed.cluster.daemonEpoch,expected:old,processes:armed.processes,observationRevision:armed.cluster.observationRevision,desiredRevision:armed.cluster.desiredRevision,desiredSha256:armed.cluster.desiredSha256};write(directories.runDirectory,'CHECKPOINT.json',checkpoint);
 const before=snapshot(current,'ru',true,30),after=snapshot(current,'ru',true,30);write(directories.resumeDirectory,'I18N-ENDPOINTS.json',{schema:1,expected:current,endpoints:[endpoint(directories.resumeDirectory,'reboot-restoration',current,'ru',before,after,9)]});
 const authority={expected:current,official:snapshot(current,'ru',true,30),logIdentity};
 return {root,directories,authority,old,ui,write,mutate(dir:string,file:string,edit:(v:any)=>void){const p=path.join(dir,file),value=JSON.parse(fs.readFileSync(p,'utf8'));edit(value);fs.writeFileSync(p,JSON.stringify(value));},cleanup(){fs.rmSync(root,{recursive:true,force:true});}};
}
function run(change?:(f:ReturnType<typeof fixture>)=>void){const f=fixture();try{change?.(f);return admitI18nSystemPicker(f.directories,f.authority);}finally{f.cleanup();}}
test('actual raw multi-owner/picker/log transcript admits without reading RESULT flags',()=>{const admitted=run(f=>{f.write(f.directories.runDirectory,'RESULT.json',{status:'FAIL'});});assert.ok(admitted.artifacts.length>30);assert.match(admitted.reason,/fresh Android process/);});
test('native PASS summary alone does not supply required raw evidence',()=>assert.throws(()=>run(f=>fs.rmSync(path.join(f.directories.runDirectory,'I18N-ENDPOINTS.json'))),/unavailable/));
test('old selected UI, wrong locale and duplicate checked children cannot prove picker',()=>{const f=fixture();try{for(const mutate of [(u:any)=>u.boot_id='old',(u:any)=>u.screens[0].nodes[3].checked=false,(u:any)=>u.screens[0].nodes[1].checked=true]){const u=f.ui(f.authority.expected,'ru');mutate(u);assert.throws(()=>checkedSystemPicker(u,f.authority.expected,'ru'));}}finally{f.cleanup();}});
test('missing Android logs remains unavailable despite full native owner ACKs',()=>assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'I18N-ENDPOINTS.json',v=>delete v.endpoints[1].currentLogs)),/Android checked picker and raw log endpoints missing/));
test('old archive collected outside actual invocation is rejected',()=>assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'locale-ru-current-capture.json',v=>{v.startedAtUtc='2026-10-09T10:00:00Z';v.completedAtUtc='2026-10-09T10:00:02Z';})),/not collected during/));
test('archive receipt path cannot point to another retained operation',()=>assert.throws(()=>run(f=>f.write(f.directories.runDirectory,'locale-ru-current-stdout.log',Buffer.from('/different/archive.tar.xz\n'))),/receipt path differs/));
test('changed checkpoint cannot be substituted for actual armed native transcript',()=>assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'CHECKPOINT.json',v=>v.desiredRevision++)),/checkpoint differs/));
test('current gate session and generation cannot come from retained evidence config',()=>assert.throws(()=>run(f=>{f.authority.expected.session={...f.authority.expected.session,id:'f'.repeat(32)};}),/session\/generation/));
test('stale native resume before/after cannot claim new observation',()=>assert.throws(()=>run(f=>fs.copyFileSync(path.join(f.directories.resumeDirectory,'reboot-restoration-before.json'),path.join(f.directories.resumeDirectory,'reboot-restoration-after.json'))),/not fresh/));
test('symlink artifact and parent traversal are rejected without following',()=>{assert.throws(()=>run(f=>{const p=path.join(f.directories.runDirectory,'locale-ru-ui.json');fs.unlinkSync(p);fs.symlinkSync('../resume/reboot-restoration-ui.json',p);}),/unavailable/);assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'I18N-ENDPOINTS.json',v=>v.endpoints[1].pickerUi='../resume/reboot-restoration-ui.json')),/unsafe i18n raw/);});
test('late live owner/config change during archive validation revokes admission',()=>{const f=fixture();try{const before=f.authority.official;assertCurrentI18nObservation(before,before,f.authority.expected);const changed=structuredClone(before);changed.data.files.runtime.data.cluster.agents[0].targets[0].process.birthIdentity=hash(999);assert.throws(()=>assertCurrentI18nObservation(changed,before,f.authority.expected),/changed during/);}finally{f.cleanup();}});
test('QNX disabled/restarted alone cannot replace Android disable readback',()=>assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'I18N-ENDPOINTS.json',v=>delete v.endpoints[4].currentLogs)),/Android checked picker and raw log endpoints missing/));
test('disabled native owners cannot hide a still-visible Russian contribution',()=>assert.throws(()=>run(f=>f.mutate(f.directories.runDirectory,'disable-owner-restart-ui.json',v=>{v.screens[0].nodes.push({id:'stale-ru',parent:'group',package:'com.qinggan.app.setting',window_id:1,text:'Русский',visible:true,enabled:true,checkable:true,checked:false});})),/disabled Russian contribution remains/));

test("i18n log proof rejects legacy and foreign device arguments", () => {
  assert.ok(run().artifacts.length > 30);
  for (const edit of [
    (v: any) => { v.argv[2] = "--device-serial"; },
    (v: any) => { v.argv[3] = "emulator-5588"; },
    (v: any) => { v.argv.push("--adb-server-port", "25582"); },
  ]) assert.throws(() => run(f => f.mutate(f.directories.runDirectory, "locale-ru-current-capture.json", edit)), /log collection identity/);
});
