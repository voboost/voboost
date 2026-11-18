import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { launcherLifetime } from './launcher-lifetime.ts';
import { launcherCache } from './launcher-cache.ts';
const original=JSON.parse(readFileSync(new URL('../../testdata/emulator/cycle125-launcher-cache-envelope.json',import.meta.url),'utf8'));
function fixture(){const f=structuredClone(original);return {rows:f.rows, authority:{...launcherLifetime(f.nativeLifetime.beforeBody,f.nativeLifetime.observed,f.nativeLifetime.afterBody),nativeLifetime:f.nativeLifetime,records:f.records,afterSequence:-1}};}
test('actual125 current native bracket and daemon envelope admit both screens without queued log',()=>{const f=fixture();assert.equal(launcherCache(f.rows,f.authority,'ru.fmplay').length,2);});
test('native proof is required when bounded logs lost registration',()=>{const f=fixture();delete f.authority.nativeLifetime;assert.throws(()=>launcherCache(f.rows,f.authority,'ru.fmplay'),/lifetime proof missing/);});
test('inner cache identity cannot supply or forge daemon envelope authority',()=>{for(const key of ['process','targetPid','targetStarttime','endpointEpoch','generationId','catalogSha256']){const f=fixture();delete f.rows[0].record.fields[key];assert.throws(()=>launcherCache(f.rows,f.authority,'ru.fmplay'),/authenticated endpoint envelope/);}});
test('changed outer current lifetime and mismatched native bracket fail',()=>{const f=fixture();f.rows[0].record.fields.targetStarttime='722';assert.throws(()=>launcherCache(f.rows,f.authority,'ru.fmplay'),/authenticated endpoint envelope/);const g=fixture();g.authority.nativeLifetime.afterBody=g.authority.nativeLifetime.afterBody.replace('starttime=721','starttime=722');assert.throws(()=>launcherCache(g.rows,g.authority,'ru.fmplay'),/crossed/);});
