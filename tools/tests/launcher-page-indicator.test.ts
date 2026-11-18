import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {deflateSync} from "node:zlib";
import {launcherAppsBeginningPng} from "../emulator/launcher-page-indicator.ts";
const crc=(b:Buffer)=>{let c=0xffffffff;for(const v of b){c^=v;for(let i=0;i<8;i++)c=c&1?(c>>>1)^0xedb88320:c>>>1}return(c^0xffffffff)>>>0};
const chunk=(type:string,b:Buffer)=>{const t=Buffer.from(type);const x=Buffer.alloc(b.length+12);x.writeUInt32BE(b.length);t.copy(x,4);b.copy(x,8);x.writeUInt32BE(crc(Buffer.concat([t,b])),8+b.length);return x};
function png(selected:number[],animated=false,speedy=false){const raw=Buffer.alloc((88*3+1)*4);for(let y=0;y<4;y++)for(let x=0;x<88;x++){const o=y*(88*3+1)+1+x*3;raw.set(speedy?[138,145,177]:[34,36,45],o)}let x=8;for(let i=0;i<3;i++){const size=selected.includes(i)?animated?25:40:10;for(let y=0;y<4;y++)for(let px=x;px<x+size&&px<88;px++)raw.set(selected.includes(i)?[73,179,253]:speedy?[175,180,201]:[86,88,94],y*(88*3+1)+1+px*3);x+=size+6}const h=Buffer.alloc(13);h.writeUInt32BE(88);h.writeUInt32BE(4,4);h[8]=8;h[9]=2;return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk("IHDR",h),chunk("IDAT",deflateSync(raw)),chunk("IEND",Buffer.alloc(0))])}
test("actual OEM first apps page is second global indicator after Home",()=>assert.equal(launcherAppsBeginningPng(readFileSync(new URL("fixtures/launcher132-first-apps-page.png",import.meta.url)),[916,670,1004,674]),true));
test("first and later global dots cannot claim first apps page",()=>{assert.equal(launcherAppsBeginningPng(png([0]),[0,0,88,4]),false);assert.equal(launcherAppsBeginningPng(png([2]),[0,0,88,4]),false);assert.equal(launcherAppsBeginningPng(png([1]),[0,0,88,4]),true)});
test("ambiguous animation geometry and unsupported theme fail closed",()=>{assert.throws(()=>launcherAppsBeginningPng(png([0,1]),[0,0,88,4]));assert.throws(()=>launcherAppsBeginningPng(png([1],true),[0,0,88,4]));assert.throws(()=>launcherAppsBeginningPng(png([]),[0,0,88,4]));assert.throws(()=>launcherAppsBeginningPng(png([1]),[0,0,87,4]))});

// Exact 88x4 crop of the Speedy Dash OEM marker captured in native-v4.
const speedy=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAFgAAAAECAYAAAAK2fi4AAAA2klEQVR4nM2UsQ7CIBCGrzS20VgX3Yxz46u6+Sg+TdPZOBh10A6mpYA59JqzQJxM+KYLxw/3X+CS3f5gYIRIBWilbbwoprBaFpCmYsgnQoDR77xSGq63Bh7N06s/TTZQ51vokgxiIjMdlG0Fa3l0clj/fJY7vrn/XvaOb59/wRcJSiJ0CR5KUHMRzOGekL6OsLkI1oS1EeP6fY+K+yffIf2w7lv0wZvq45c+dv7l/+v9K62cGL8BjgF7CLtEj0ZESF+2lf2OsUEjgsPrP1/ug2+C+5efERHSU/wCg4mPVjU/Q7QAAAAASUVORK5CYII=","base64");
test("actual Speedy Dash tinted indicator identifies the first apps page",()=>assert.equal(launcherAppsBeginningPng(speedy,[0,0,88,4]),true));

test("Speedy Dash geometry rejects Home, later pages and animation",()=>{assert.equal(launcherAppsBeginningPng(png([0],false,true),[0,0,88,4]),false);assert.equal(launcherAppsBeginningPng(png([2],false,true),[0,0,88,4]),false);assert.throws(()=>launcherAppsBeginningPng(png([1],true,true),[0,0,88,4]));assert.throws(()=>launcherAppsBeginningPng(png([0,1],false,true),[0,0,88,4]));});
