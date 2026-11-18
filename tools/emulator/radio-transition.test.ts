import { test } from "node:test";
import assert from "node:assert/strict";
import { radioTransition } from "./radio-transition.ts";
const frame = (ms, position, caption=false) => ({ agent:"settings",event:"radio.frame",fields:{id:1,key:"language",value:"ru",group:7,frame:ms===16?1:ms===100?2:3,ms,initial:16,target:216,position,duration:400,visible:true,caption} });
const end = () => ({agent:"settings",event:"radio.trace",outcome:"bounded-end",fields:{id:1,key:"language",value:"ru",group:7,attached:true,captionMs:410,requestMs:12,language:"ru"}});
const authority = {targetPid:"42",documentGeneration:"10",generationId:"gen",catalogSha256:"cat"};
const root = () => ({agent:"settings",event:"radio.authority",outcome:"observed",fields:{id:1,pid:"42",...authority}});
const good = () => [frame(16,16),frame(100,120),frame(450,216,true),end(),root()];
test("requires real intermediate draw and returns measured caption timing",()=>{const r=radioTransition(good(),1,authority);assert.equal(r.outcome,"measured");assert.equal(r.captionFirstDrawMs,450);});
test("teleport/reset animator mutation fails",()=>{let r=good();r[1]=frame(100,216);assert.equal(radioTransition(r,1,authority).outcome,"failed");});
test("rebuilt group mutation is unavailable",()=>{let r=good();r[2].fields.group=8;assert.equal(radioTransition(r,1,authority).outcome,"unavailable");});
test("invisible or incomplete frames never pass",()=>{let r=good();r[1].fields.visible=false;assert.equal(radioTransition(r,1,authority).outcome,"unavailable");assert.equal(radioTransition([],1,authority).outcome,"unavailable");});
test("caption flags without visible final position fail",()=>{let r=good();r[2]=frame(450,180,true);assert.equal(radioTransition(r,1,authority).outcome,"failed");});

test("wrong root document rejects otherwise good frames",()=>assert.equal(radioTransition(good(),1,{...authority,documentGeneration:"11"}).outcome,"unavailable"));

test("changed OEM duration rejects draw trace",()=>{let r=good();r[1].fields.duration=0;assert.equal(radioTransition(r,1,authority).outcome,"failed");});


test("completed draw may terminate naturally before resource bound",()=>{let r=good();r[3].outcome="drawn-end";assert.equal(radioTransition(r,1,authority).outcome,"measured");});
test("early completion does not waive intermediate movement or final caption",()=>{let r=good();r[3].outcome="drawn-end";r[1]=frame(100,216);assert.equal(radioTransition(r,1,authority).outcome,"failed");r=good();r[3].outcome="drawn-end";r[2].fields.caption=false;assert.equal(radioTransition(r,1,authority).outcome,"failed");});
test("detachment cannot stand in for a completed draw",()=>{let r=good();r[3].outcome="detached";assert.equal(radioTransition(r,1,authority).outcome,"unavailable");});
