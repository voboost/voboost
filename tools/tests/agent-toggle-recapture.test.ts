import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {InstallerUi} from '../emulator/installer-ui.ts';
import {agentToggleTap} from '../../release/emulator-gate.ts';
import {windowModel} from '../emulator/window-fixture.ts';
const rejection=readFileSync(new URL('./fixtures/settings-target-drift/218-stderr.txt',import.meta.url),'utf8');
const progress=readFileSync(new URL('./fixtures/settings-target-drift/218-stdout.txt',import.meta.url),'utf8');
for (const variant of ['recapture','ambiguous','timeout','double','checkbox','authority','ownership','boot','catalog','overflow','receipt','duplicate','foreign-serial','unexpected-log'] as const) test(`semantic checkbox recapture ${variant}`,async()=>{
 const actions:any[]=[], windows:number[]=[]; let taps=0, snapshots=0, roots=0, fences=0;
 const source=windowModel().replaceAll('com.pateonavi.naviapp','com.qinggan.app.setting');
 const root=/<node [^>]*window-id="17"[^>]*><\/node>/u.exec(source)![0];
 const label=root.replace('text=""','text="Font"');
 const control=root.replace('index="0"','index="1"').replace('checkable="false"','checkable="true"').replace('checked="false"','checked="true"').replace('clickable="false"','clickable="true"');
 const panel=source.replace(root,root.replace('</node>',label+control+'</node>'));
 const ui=new InstallerUi("/package/voboost-installer",'emulator-5562',join(mkdtempSync(join(tmpdir(),'toggle-')),'ui'),async invocation=>{
  const request=JSON.parse(readFileSync(invocation.args[1],'utf8'));actions.push(request.action);
  let body='';
  if(request.action==='resident-status') { roots++;body='bootCompleted=1\nbootId=11111111-1111-4111-8111-111111111111\nenforcing=Enforcing\nbootIdAfter=11111111-1111-4111-8111-111111111111\nresident-status:\n/data/voboost/status.json\n'+JSON.stringify({schemaVersion:1,releaseIdentity:'beta1',manifestSha256:'a'.repeat(64),generationId:'b'.repeat(64),catalogSha256:'c'.repeat(64),startupMode:'interface',injections:[{id:'settings',process:'com.qinggan.app.setting',state:'active',documentGeneration:variant==='authority'&&roots>1?1791813951032378000:1791813951032377097,lifecycleGeneration:1}]})+'\n\n'; }
  if(request.action==='resident-status' && roots>1 && variant==='boot')body=body.replaceAll('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222');
  if(request.action==='resident-status' && roots>1 && variant==='catalog')body=body.replace('c'.repeat(64),'d'.repeat(64));
  if(request.action==='snapshot') { snapshots++;body=panel.replaceAll('="17"',snapshots===1?'="17"':'="117"');if(variant==='checkbox'&&snapshots>1)body=body.replaceAll('checked="true"','checked="false"'); }
  if(request.action==='tap') { taps++;windows.push(request.expectedWindow.id);if(taps===1||variant==='double')return {exitCode:1,stdout:variant==='receipt'?'{}':variant==='foreign-serial'?progress.replace('emulator-5562','emulator-5554'):variant==='unexpected-log'?progress+'unexpected payload\n':progress,stderr:variant==='ambiguous'?'input failed after ACTION_DOWN':variant==='duplicate'?rejection+rejection:rejection,timedOut:variant==='timeout',overflow:variant==='overflow'}; }
  return {exitCode:0,stdout:JSON.stringify({schemaVersion:1,serial:'emulator-5562',body})+'\n',stderr:''};
 },async()=>{fences++;if(variant==='ownership'&&fences===4)throw Error('lease changed');});
 if(variant==='recapture') {await agentToggleTap(ui,'Font',true,()=>{});assert.deepEqual(actions,['resident-status','snapshot','tap','resident-status','snapshot','tap']);assert.deepEqual(windows,[17,117]);}
 else {await assert.rejects(()=>agentToggleTap(ui,'Font',true,()=>{}));assert.equal(taps,variant==='double'?2:1);}
});
