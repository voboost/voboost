import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {parseUi} from "../emulator/installer-ui.ts";
import {launcherCaptureContinuity as guard} from "../emulator/launcher-capture.ts";
const boot={boot:"same-boot",bootCompleted:true};
function pair(){
 const pages=[187,199].map(id=>{
 const nodes=parseUi(readFileSync(new URL("fixtures/launcher133-"+id+".xml",import.meta.url),"utf8"));
 const grid=nodes.find(n=>n.resource.endsWith(":id/all_app_view"))!;
 const marker=nodes.find(n=>n.resource.endsWith(":id/home_page_indicator"))!;
 return {nodes,grid,marker};
 }); return pages;
}
test("actual133 pair changes IDs437 to447 but preserves semantic tree",()=>{
 const [a,b]=pair(); assert.equal(a.grid.windowRef!.id,437); assert.equal(b.grid.windowRef!.id,447);
 assert.notEqual(a.grid.windowRef!.id,b.grid.windowRef!.id);
 guard(a,b,a.marker,b.marker,boot,boot,180000);
});
for(const field of ["rootPackage","bounds","title","inputFocused","layer"]){
 test("reject semantic descriptor drift "+field,()=>{const[a,b]=pair();const w={...b.grid.windowRef!,[field]:field==="bounds"?[0,0,100,100]:field==="inputFocused"?false:field==="layer"?9:"foreign"}; const old=b.grid.windowRef;for(const n of b.nodes)if(n.windowRef===old)n.windowRef=w; b.grid.windowRef=w;b.marker.windowRef=w;assert.throws(()=>guard(a,b,a.marker,b.marker,boot,boot,180000));});
}
test("reject changed labels and geometry",()=>{for(const field of ["text","bounds"]){const[a,b]=pair(); b.nodes.find(n=>n.text && n.windowRef===b.grid.windowRef)! [field]=field==="text"?"other":[0,0,1,1];assert.throws(()=>guard(a,b,a.marker,b.marker,boot,boot,180000));}});
test("reject boot change or incomplete boot",()=>{for(const bb of [{boot:"other",bootCompleted:true},{boot:"same-boot",bootCompleted:false}]){const[a,b]=pair();assert.throws(()=>guard(a,b,a.marker,b.marker,boot,bb,180000));}});
test("reject reused capture and nonfresh or overbudget interval",()=>{for(const change of [{captureStart:233488},{captureStart:233400},{captureEnd:500000}]){const[a,b]=pair();const w={...b.grid.windowRef!,...change};const old=b.grid.windowRef;for(const n of b.nodes)if(n.windowRef===old)n.windowRef=w;b.grid.windowRef=w;b.marker.windowRef=w;assert.throws(()=>guard(a,b,a.marker,b.marker,boot,boot,180000));} const[a]=pair();assert.throws(()=>guard(a,a,a.marker,a.marker,boot,boot,180000));});
test("reject foreign marker capture within a frame",()=>{const[a,b]=pair();b.marker.windowRef=a.marker.windowRef;assert.throws(()=>guard(a,b,a.marker,b.marker,boot,boot,180000));});
