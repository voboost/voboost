/** Independent current getter sample. This proves neither continuous state nor write attribution. */
export function startupVehicleCanEvidence(sample:any, authority:{bootId:string;sessionId:string;afterBootMs:number}) {
  const fail=(reason:string):never=>{throw new Error(`startup CAN ${reason}`);};
  const decimal=(v:any):bigint=>{
    if(typeof v!=="string" || !/^(0|[1-9][0-9]*)$/.test(v) || BigInt(v)>18446744073709551615n) fail("invalid clock or revision");
    return BigInt(v);
  };
  if(!Number.isSafeInteger(authority.afterBootMs) || authority.afterBootMs<0) fail("invalid readback anchor");
  if(sample?.schema!==2 || sample.source!=="android:qg.canbus") fail("source differs");
  if(sample.status!=="ready") return {status:"unavailable" as const,reason:"independent CAN source not ready"};
  if(sample.boot_id!==authority.bootId || sample.guest_boot_id!==authority.bootId || sample.session_id!==authority.sessionId) fail("boot or session differs");
  if(typeof sample.source_instance!=="string" || !sample.source_instance || sample.source_instance.length>1024
      || !Number.isInteger(sample.pid) || sample.pid<2 || sample.pid>2147483647
      || sample.clock?.domain!=="android.elapsedRealtimeNanos" || sample.transport?.registration!=="registered"
      || sample.transport.binder_alive!==true || sample.atomic_guest_state!==false) fail("source authority differs");
  const observed=decimal(sample.clock.sampled_ns), started=decimal(sample.service_started_elapsed_ns);
  decimal(sample.revision);
  if(started>observed || observed<BigInt(authority.afterBootMs)*1000000n) fail("sample precedes confirmed application");
  const expected={"vehicle_state.IVI_SOC_MODESET":5,"vehicle_state.HUM_VSP_FUNCTION_SW":1};
  for(const [name,raw] of Object.entries(expected)) {
    const value=sample.values?.[name];
    if(value?.status!=="observed" || !Number.isInteger(value.raw)) return {status:"unavailable" as const,reason:`independent getter unavailable: ${name}`};
    if(value.raw!==raw) fail(`restored state differs: ${name}`);
  }
  return {status:"passed" as const,coverage:"sampled-current-getters",bootId:authority.bootId,
    sourceInstance:sample.source_instance,pid:sample.pid,revision:sample.revision,
    observedAtBootNs:sample.clock.sampled_ns,afterConfirmedApplicationBootMs:authority.afterBootMs,
    forcedEv:true,pedestrianWarning:false,writeAttribution:"not inferred"};
}
