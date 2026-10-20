#!/usr/bin/env node
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/MOVE60.html','utf8');
const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1]).filter(Boolean);
for(const code of scripts) new vm.Script(code,{filename:'MOVE60.html'});
const js=scripts.join('\n');
const start=js.indexOf('const plan=');
const end=js.indexOf('\nfunction expandPhases',start);
if(start<0||end<0) throw Error('Missing plan definitions');
const plan=vm.runInNewContext(js.slice(start,end)+'\nplan;');
for(let day=1;day<=5;day++){
  const p=plan[day];if(!p)throw Error('Missing day '+day);
  const total=p.warm+p.cycle+p.strength+p.stretch;
  if(total!==60)throw Error(p.day+' total is '+total+', not 60');
  const phases=p.cyclePhases.flatMap(p=>p.repeat?Array.from({length:p.repeat},()=>p.parts).flat():[p]);
  const cycle=phases.reduce((sum,p)=>sum+p.m,0);
  if(cycle!==p.cycle)throw Error(p.day+' cycle is '+cycle);
  if(day===2&&(phases.length!==18||p.cyclePhases.find(x=>x.repeat)?.repeat!==8))throw Error('Tuesday must have 8 intervals/18 phases');
  console.log('PASS '+p.day+' | '+total+' min | cycle '+cycle+' min | '+phases.length+' phases');
}
if(!html.includes('Move60Native.updateWorkout'))throw Error('Missing native bridge');
if(!html.includes('currentPhase()?.index===ti'))throw Error('Checklist mismatch');
console.log('PASS all 5 days');
