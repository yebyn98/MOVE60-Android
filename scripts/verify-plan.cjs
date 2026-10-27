const fs = require('node:fs');
const assert = require('node:assert/strict');
const source = JSON.parse(fs.readFileSync('app/src/main/assets/workouts.json', 'utf8'));
for (let day = 1; day <= 5; day++) {
  const p = source.plan[day];
  assert.ok(p, `Day ${day} exists`);
  assert.ok(p.cardioOptions.length >= 3, `${p.day} multiple cardio options`);
  assert.ok(p.cardioOptions.some(x => x !== "실내 사이클"), `${p.day} non-cycling cardio`);
  assert.equal(p.warm + p.cycle + p.strength + p.stretch, 60, `${p.day} duration`);
  const expanded = p.cyclePhases.flatMap(x => x.repeat ? Array.from({length:x.repeat}, () => x.parts).flat() : [x]);
  assert.equal(expanded.reduce((sum,p) => sum+p.m, 0), p.cycle, `${p.day} cycle minutes`);
  if (day === 2) {
    assert.equal(expanded.length, 18, 'Tuesday 18 cycle segments');
    assert.equal(p.cyclePhases.find(x => x.repeat)?.repeat, 8, 'Tuesday 8 intervals');
  }
  assert.equal(source.pools[day].length, source.reps[day].length, `${p.day} strength slots`);
  for (const slot of source.pools[day]) for (const id of slot) assert.ok(source.exercise[id], `Missing ${id}`);
  console.log(`PASS ${p.day}: 60분 / 사이클 ${p.cycle}분 / 구간 ${expanded.length}개`);
}
console.log('PASS: weekday durations, cardio alternatives, exercise references and strength pools.');
