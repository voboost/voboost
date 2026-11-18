import test from 'node:test';
import assert from 'node:assert/strict';
import {writeFileSync,readFileSync,mkdirSync,mkdtempSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {after} from 'node:test';
import {regressionOptions,regressionSummary,installedSemanticRequired,assertRegressionInvocation,noneProcesses} from '../../release/emulator-gate.ts';
const parent=join(process.cwd(),'build');mkdirSync(parent,{recursive:true});
const root=mkdtempSync(join(parent,'installed-semantic-test-'));after(()=>rmSync(root,{recursive:true}));
const orders={lifecycle:[8],'lifecycle-input':[8,9,19,10,11],startup:[15,16],'final-uninstall':[17]};
function evidence(scenario){
 const required=installedSemanticRequired(scenario);
 return {imageCapture:'deferred',regressionScenario:scenario,passed:false,cases:Array.from({length:27},(_,index)=>{
  const number=index+1, selected=orders[scenario].includes(number);
  return {number,status:selected?'unavailable':'not-run',subchecks:selected?[...required.filter(id=>id.startsWith(number+':')).map(id=>({id:id.split(':').slice(1).join(':'),status:'passed',artifacts:['retained-readback.json']})),{id:'installed-visual-pending',status:'unavailable',artifacts:[]}]:[]};
 }),boots:['lifecycle','lifecycle-input'].includes(scenario)?[]:[{status:'unavailable',semanticStatus:'passed',screenshot:null}]};
}
for(const scenario of Object.keys(orders)) {
 test(scenario+' exact fixed semantic acceptance',()=>assert.equal(regressionSummary(evidence(scenario),scenario).semanticPassed,true));
 test(scenario+' always partial and no visual PASS',()=>{const r=regressionSummary(evidence(scenario),scenario);assert.equal(r.passed,false);assert.equal(r.fullAcceptance,false);});
 test(scenario+' rejects nondeferred and mixed selectors',()=>{assert.throws(()=>regressionOptions(scenario));assert.throws(()=>regressionOptions(scenario,{imageCapture:'deferred',settingsPart:'postboot'}));assert.equal(regressionOptions(scenario,{imageCapture:'deferred'}),true);});
 for(const mutation of ['missing','duplicate','failed','unavailable','artifact','foreign','pending','boot']) test(scenario+' rejects '+mutation,()=>{
  const e=evidence(scenario),row=e.cases.find(r=>r.subchecks.length),check=row.subchecks[0];
  if(mutation==='missing')row.subchecks.shift();
  if(mutation==='duplicate')row.subchecks.push({...check});
  if(mutation==='failed'||mutation==='unavailable')check.status=mutation;
  if(mutation==='artifact')check.artifacts=[];
  if(mutation==='foreign')e.cases.find(r=>!orders[scenario].includes(r.number)).status='unavailable';
  if(mutation==='pending')row.subchecks.push({...row.subchecks.at(-1)});
  if(mutation==='boot')e.boots.push({status:'passed',screenshot:'fake.png'});
  assert.equal(regressionSummary(e,scenario).semanticPassed,false);
 });
}
const request=root+'/observer-request.json';writeFileSync(request,JSON.stringify({action:'observe-settings-uninstall'}));
test('destructive observer denied in every ordinary subset',()=>{for(const scenario of ['vehicle','launcher','settings-radio','startup','lifecycle','lifecycle-input'])assert.throws(()=>assertRegressionInvocation({args:['--emulator-check',request]},scenario));});
test('final subset admits only official Settings observer',()=>assert.doesNotThrow(()=>assertRegressionInvocation({args:['--emulator-check',request]},'final-uninstall')));
test('final subset still forbids CLI removal and installation',()=>{for(const flag of ['--install','--uninstall','--uninstall-emergency'])assert.throws(()=>assertRegressionInvocation({args:[flag]},'final-uninstall'));});
test('none rejects both daemon and Android APK subprocesses',()=>{for(const name of ['voboostd','ru.voboost','ru.voboost:ota'])assert.throws(()=>noneProcesses('processes:\nPID NAME\n123 '+name+'\ncontexts:\n'));});
test('none never accepts missing malformed inventory',()=>{assert.throws(()=>noneProcesses(''));assert.throws(()=>noneProcesses('processes:\nPID NAME\nunknown\ncontexts:\n'));});
test('case programs reused without prepare install',()=>{const s=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');assert.match(s,/'?lifecycle-input"?: \[8, 9, 19, 10, 11\]/);assert.match(s,/startup: \[15, 16\]/);assert.match(s,/"final-uninstall": \[17\]/);assert.match(s,/Installed-kit regression: absence\/install preparation intentionally not executed/);});

test('existing deferred launcher stays admitted',()=>assert.equal(regressionOptions('launcher',{imageCapture:'deferred'}),true));

test('lifecycle selects only the complete existing case8',()=>assert.deepEqual(installedSemanticRequired('lifecycle'),['8:agent-lifecycle-and-keyboard-exclusion']));

test('case8 re-enable observations retain distinct phases and every assertion',()=>{
 const source=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
 const body=source.slice(source.indexOf('      case 8: {'),source.indexOf('      case 9:',source.indexOf('      case 8: {')));
 assert.match(body,/await checkReenable\(agent.id, "re-enable-before"\);/);
 assert.match(body,/await checkReenable\(agent.id, "re-enable-after"\);/);
 assert.match(body,/await checkReenable\(other, "re-enable-conflict-prepare"\);/);
 assert.match(body,/await checkDisable\(agent.id, policy.modes\[agent.id\], \(\) => toggle\(agent.id, false\)\);/);
 assert.match(body,/if \(newCrashes.length\)/);
 const e=evidence('lifecycle'),row=e.cases[7];
 for(const prefix of ['re-enable-before','re-enable-after','re-enable-conflict-prepare'])row.subchecks.unshift({id:prefix+'-keyboard-ru-com.qinggan.app.qgime',status:'passed',artifacts:['retained-readback.json']});
 assert.equal(regressionSummary(e,'lifecycle').semanticPassed,true);
 const duplicate=structuredClone(e);duplicate.cases[7].subchecks[1].id=duplicate.cases[7].subchecks[0].id;assert.equal(regressionSummary(duplicate,'lifecycle').semanticPassed,false);
 for(const status of ['failed','unavailable']){const bad=structuredClone(e);bad.cases[7].subchecks[0].status=status;assert.equal(regressionSummary(bad,'lifecycle').semanticPassed,false);}
 const absent=structuredClone(e);absent.cases[7].subchecks[0].artifacts=[];assert.equal(regressionSummary(absent,'lifecycle').semanticPassed,false);
});
