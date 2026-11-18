import assert from "node:assert/strict";
import test from "node:test";
import {seekSecondaryFixture,secondaryPageSwipe} from "./launcher-fixture.ts";

test("secondary fixture search follows independent pages and never accepts opening the first page",async()=>{
 const primary=["Voboost Fixture","OEM"],secondary=[["OEM"],["other"],["Voboost Fixture"]];
 let page=0,swipes=0,reads=0;
 const witness={screen:1,canonicalDraw:42};
 assert.equal(primary[0],"Voboost Fixture");
 assert.equal(await seekSecondaryFixture(async()=>{reads++;return secondary[page].includes("Voboost Fixture")?witness:null;},async()=>{swipes++;page++;}),witness);
 assert.equal(swipes,2);assert.equal(reads,3);
 let absentSwipes=0,absentReads=0;
 await assert.rejects(seekSecondaryFixture(async()=>{absentReads++;return null;},async()=>{absentSwipes++;}),/absent from bounded/);
 assert.equal(absentSwipes,11);assert.equal(absentReads,12);
});

test("secondary physical swipe preserves honest display completion and lifetime authority",async()=>{
 const lifetime={boot:"boot",generationId:"generation",catalogSha256:"catalog",targetPid:"41",targetStarttime:"514"};
 const receipt={session_id:"owned",ok:true,sdk_completed:true,pointer_released:true,screen:"secondary",sdk_display_id:1,visible_effect_verified:false};
 const witness=await secondaryPageSwipe(async()=>lifetime,async()=>receipt,"owned");
 assert.equal(witness.targetWindowPreDispatchFenced,false);assert.equal(witness.provenance,"owned-profile-physical-swipe");
 for(const change of [{session_id:"foreign"},{screen:"primary"},{sdk_display_id:0},{sdk_completed:false},{pointer_released:false},{visible_effect_verified:true},{pre_dispatch_fenced:true}])
  await assert.rejects(secondaryPageSwipe(async()=>lifetime,async()=>({...receipt,...change}),"owned"),/completion differs/);
 for(const key of Object.keys(lifetime)) {
  let reads=0;await assert.rejects(secondaryPageSwipe(async()=>++reads===1?lifetime:{...lifetime,[key]:"changed"},async()=>receipt,"owned"),/crossed boot or launcher lifetime/);
 }
});
