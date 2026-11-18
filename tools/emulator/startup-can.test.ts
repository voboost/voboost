import {test} from "node:test";
import assert from "node:assert/strict";
import {startupVehicleCanEvidence} from "./startup-can.ts";
const boot="00000000-0000-0000-0000-000000000001", session="a".repeat(32);
const authority={bootId:boot,sessionId:session,afterBootMs:1000};
function fixture():any {return {schema:2,source:"android:qg.canbus",status:"ready",boot_id:boot,guest_boot_id:boot,session_id:session,source_instance:"service:1",pid:333,revision:"2",clock:{domain:"android.elapsedRealtimeNanos",sampled_ns:"1000000001"},service_started_elapsed_ns:"1",transport:{registration:"registered",binder_alive:true},atomic_guest_state:false,values:{"vehicle_state.IVI_SOC_MODESET":{status:"observed",raw:5},"vehicle_state.HUM_VSP_FUNCTION_SW":{status:"observed",raw:1}}};}
test("independent getter proves both restored values only after backend readback",()=>{
 const r=startupVehicleCanEvidence(fixture(),authority);assert.equal(r.status,"passed");
 if(r.status==="passed") {assert.equal(r.coverage,"sampled-current-getters");assert.equal(r.writeAttribution,"not inferred");}
});
test("wrong boot, session and clock reject stale samples",()=>{
 for(const mutate of [(v:any)=>v.boot_id="other",(v:any)=>v.guest_boot_id="other",(v:any)=>v.session_id="other",(v:any)=>v.clock.sampled_ns="999999999",(v:any)=>v.clock.domain="host.monotonic",(v:any)=>v.service_started_elapsed_ns="2000000000"]) {const s=fixture();mutate(s);assert.throws(()=>startupVehicleCanEvidence(s,authority));}
});
test("stock reset values cannot pass as restored preferences",()=>{
 for(const name of Object.keys(fixture().values)) {const s=fixture();s.values[name].raw=2;assert.throws(()=>startupVehicleCanEvidence(s,authority),/restored state differs/);}
});
test("missing or unavailable getter is not a boolean false success",()=>{
 const s=fixture();delete s.values["vehicle_state.HUM_VSP_FUNCTION_SW"];assert.equal(startupVehicleCanEvidence(s,authority).status,"unavailable");
 assert.equal(startupVehicleCanEvidence({...fixture(),status:"not-ready"},authority).status,"unavailable");
});
test("dead or unregistered binder cannot provide ready evidence",()=>{
 for(const change of [{registration:"failed",binder_alive:true},{registration:"registered",binder_alive:false}]) assert.throws(()=>startupVehicleCanEvidence({...fixture(),transport:change},authority));
});
test("numeric or noncanonical nanoseconds and unsupported source fail closed",()=>{
 for(const mutate of [(s:any)=>s.clock.sampled_ns=1000000001,(s:any)=>s.clock.sampled_ns="01",(s:any)=>s.source="voboost-state",(s:any)=>s.atomic_guest_state=true]) {const s=fixture();mutate(s);assert.throws(()=>startupVehicleCanEvidence(s,authority));}
});
