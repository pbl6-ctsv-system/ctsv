import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequestGate} from '../src/shared/api/request-gate.mjs';

test('changing target invalidates an old response even before the next read',()=>{
 const gate=createRequestGate(),old=gate.begin();
 gate.invalidate();
 assert.equal(gate.current(old),false);
 const next=gate.begin();assert.equal(gate.current(next),true);
});
test('latest request wins when responses arrive in reverse order',()=>{
 const gate=createRequestGate(),first=gate.begin(),second=gate.begin();
 assert.equal(gate.current(second),true);assert.equal(gate.current(first),false);
});
