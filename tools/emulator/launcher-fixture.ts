/** Search the independently ordered secondary pages without assuming a first-slot fixture. */
export async function seekSecondaryFixture<T>(read:()=>Promise<T|null>,swipe:()=>Promise<void>):Promise<T> {
  for(let page=0;page<=11;page++) {
    const witness=await read();
    if(witness!==null) return witness;
    if(page<11) await swipe();
  }
  throw new Error("fixture first draw absent from bounded secondary launcher pages");
}

type Lifetime={boot:string;generationId:string;catalogSha256:string;targetPid:string;targetStarttime:string};
/** Physical navigation has owned-session/display authority, not a semantic target fence. */
export async function secondaryPageSwipe(readLifetime:()=>Promise<Lifetime>,dispatch:()=>Promise<any>,sessionId:string) {
  const before=await readLifetime();
  const result=await dispatch();
  if(result?.session_id!==sessionId || result?.ok!==true || result.sdk_completed!==true || result.pointer_released!==true || result.screen!=="secondary" || result.sdk_display_id!==1 || result.visible_effect_verified!==false || result.pre_dispatch_fenced===true) throw new Error("secondary physical swipe completion differs");
  const after=await readLifetime();
  for(const key of ["boot","generationId","catalogSha256","targetPid","targetStarttime"] as const)
    if(before[key]!==after[key]) throw new Error("secondary physical swipe crossed boot or launcher lifetime");
  return {provenance:"owned-profile-physical-swipe",targetWindowPreDispatchFenced:false,before,after};
}
