import {pngRaster} from "./installer-ui.ts";
// OEM MainActivity adds HomeCardFragment before AllAppFragment: first apps page is global index1.
// PageIndicator uses selected40px, normal10px, gap6px, maxVisible4; later indices scroll to the last bar.
export function launcherAppsBeginningPng(bytes: Buffer, bounds: readonly number[]): boolean {
 const {width,height,channels,pixels} = pngRaster(bytes);
 const [l,t,r,b]=bounds;
 if (![l,t,r,b].every(Number.isSafeInteger)||l<0||t<0||r>width||b>height||r-l!==88||b-t!==4) throw new Error("unsupported OEM page indicator geometry");
 const y=t+2;
 const background=Array.from(pixels.subarray((y*width+l)*channels,(y*width+l)*channels+3));
 const kinds:number[]=[];
 for(let x=l;x<r;x++){
  const o=(y*width+x)*channels;const [red,green,blue]=pixels.subarray(o,o+3);
  if(channels===4&&pixels[o+3]!==255) throw new Error("transparent page indicator capture");
  const selected=blue>red+70&&green>red+40 || red>green+20&&green>blue+20;
  // OEM normal bars lighten the surrounding theme color, including Speedy Dash blue-gray.
  const contrast=[red-background[0],green-background[1],blue-background[2]];
  const normal=Math.min(...contrast)>=20&&Math.max(...contrast)-Math.min(...contrast)<=16;
  kinds.push(selected?2:normal?1:0);
 }
 const runs:{kind:number;start:number;end:number}[]=[];
 for(let x=0;x<kinds.length;x++){const k=kinds[x];if(!k)continue;const last=runs.at(-1);if(last&&last.kind===k&&last.end===x)last.end++;else runs.push({kind:k,start:x,end:x+1})}
 const selected=runs.filter(v=>v.kind===2);
 if(selected.length!==1||selected[0].end-selected[0].start<36||selected[0].end-selected[0].start>40||runs.length<2||runs.length>4) throw new Error("ambiguous or animating OEM page indicator");
 for(let i=0;i<runs.length;i++){
  const row=runs[i],size=row.end-row.start;
  if(row.kind===1&&(size<8||size>11))throw new Error("unsupported normal page indicator");
  if(i&& (row.start-runs[i-1].end<5||row.start-runs[i-1].end>9))throw new Error("unsupported page indicator spacing");
 }
 const expectedStart=(88-(40+16*(runs.length-1)))/2;
 if(Math.abs(runs[0].start-expectedStart)>2||Math.abs(runs.at(-1)!.end-(88-expectedStart))>2)throw new Error("page indicator sliding or clipped");
 return runs.indexOf(selected[0])===1;
}
