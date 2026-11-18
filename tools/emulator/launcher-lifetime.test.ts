import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { launcherLifetime } from './launcher-lifetime.ts';
const fixture=JSON.parse(readFileSync(new URL('../../testdata/emulator/cycle124-launcher-lifetime.json', import.meta.url),'utf8'));
test('actual attempt4 native proof authenticates current launcher without queued logs',()=>{
 const got=launcherLifetime(fixture.proof,fixture.observed,fixture.proof);
 assert.equal(got.targetPid,'2144');assert.equal(got.targetStarttime,'727');
});
test('bracket rejects reboot PID reuse and starttime change',()=>{
 for(const [a,b] of [['pid=2144','pid=2145'],['starttime=727','starttime=728'],['fc4ff630-bff4-4296-833c-9aa98350840d','ac4ff630-bff4-4296-833c-9aa98350840d']]) assert.throws(()=>launcherLifetime(fixture.proof,fixture.observed,fixture.proof.replace(a,b)),/crossed/);
});
test('independent inventory and active injection cannot be replaced by cache self reports',()=>{
 assert.throws(()=>launcherLifetime(fixture.proof,{...fixture.observed,body:fixture.observed.body.replace(/2144(\s+com\.qinggan\.app\.launcher)/,'2145$1')},fixture.proof),/independent current process/);
 assert.throws(()=>launcherLifetime(fixture.proof,{...fixture.observed,body:fixture.observed.body.replaceAll('"id":"launcher-apps"','"id":"other-apps"')},fixture.proof),/active launcher injection/);
});
