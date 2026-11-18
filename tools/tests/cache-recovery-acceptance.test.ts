import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cacheRecoveryBootEvent, recoveryBootAck } from '../../release/emulator-gate.ts';
const identity = {schemaVersion:1,operation:'cache-recovery-boot',attemptToken:'a'.repeat(64),priorBootId:'12345678-1234-1234-1234-123456789abc',bootId:'22345678-1234-1234-1234-123456789abc',state:'waiting'};
const event = (row: unknown) => 'VBI_CACHE_RECOVERY_BOOT|' + JSON.stringify(row);
test('recovery consumer admits only exact bounded waiting event', () => {
 assert.deepEqual(cacheRecoveryBootEvent(event(identity)),identity);
 for (const row of [{...identity,bootId:identity.priorBootId},{...identity,schemaVersion:2},{...identity,state:'accepted'},{...identity,attemptToken:'A'.repeat(64)},{...identity,serial:'emulator-5562'},{...identity,operation:'install'}]) assert.throws(()=>cacheRecoveryBootEvent(event(row)));
 assert.throws(()=>cacheRecoveryBootEvent('bad'));
 assert.throws(()=>cacheRecoveryBootEvent(event(identity).replace('{','{"state":"waiting",'))); 
 assert.throws(()=>cacheRecoveryBootEvent(event({...identity,extra:'x'.repeat(1024)})));
});

test('recovery ACK waits for complete verify and repeated lease/boot fences', async () => {
 const trace: string[]=[];
 const checks={ownership:async()=>{trace.push('lease');},verify:async()=>{trace.push('verify220-ui-enforcing');return{status:'passed',reason:'ok',after:identity.bootId,verifiedBoot:identity.bootId};},current:async()=>{trace.push('current');return{boot:identity.bootId,bootCompleted:true};}};
 const id=cacheRecoveryBootEvent(event(identity));
 assert.equal(JSON.parse(await recoveryBootAck(id,checks)).decision,'accept');
 assert.deepEqual(trace,['lease','verify220-ui-enforcing','lease','current']);
 for (const failure of [
  {...checks,ownership:async()=>{throw new Error('stale lease');}},
  {...checks,verify:async()=>({status:'failed',reason:'partial220',after:identity.bootId,verifiedBoot:identity.bootId})},
  {...checks,verify:async()=>({status:'passed',reason:'foreign',after:identity.priorBootId,verifiedBoot:identity.priorBootId})},
  {...checks,current:async()=>({boot:identity.priorBootId,bootCompleted:true})},
  {...checks,current:async()=>({boot:identity.bootId,bootCompleted:false})},
 ]) assert.equal(JSON.parse(await recoveryBootAck(id,failure)).decision,'reject');
});
