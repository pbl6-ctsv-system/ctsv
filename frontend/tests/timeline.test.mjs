import test from 'node:test';
import assert from 'node:assert/strict';
import {roundContent,emptyRound,vietnamInput,vietnamInstant} from '../src/features/mghp/configuration/model.mjs';

test('new rounds have nullable planned dates without invented actual events',()=>{
 const round=emptyRound();assert.equal(Object.keys(round.schedule).length,8);
 assert.ok(Object.values(round.schedule).every(v=>v===null));assert.ok(!('reviewClosedAt' in round));
});
test('Vietnam wall time stays explicit and does not depend on the browser timezone',()=>{
 assert.equal(vietnamInput('2026-11-20T02:00:00Z'),'2026-11-20T09:00:00');
 assert.equal(vietnamInstant('2026-11-20T09:00:00'),'2026-11-20T02:00:00.000Z');
 assert.equal(vietnamInstant(''),null);
});
test('round writes whitelist schedule and preserve a Date without converting it to an instant',()=>{
 const round={...emptyRound(),schedule:{councilPlannedDate:'2026-11-25'},reviewClosedAt:'2026-11-20T02:00:00Z',reviewSourceSha256:'internal'};
 const body=roundContent(round);assert.equal(body.schedule.councilPlannedDate,'2026-11-25');
 assert.equal(Object.keys(body.schedule).length,8);assert.ok(!('reviewClosedAt' in body));assert.ok(!('reviewSourceSha256' in body));
});
