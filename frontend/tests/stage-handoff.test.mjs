import test from 'node:test';
import assert from 'node:assert/strict';
import * as model from '../src/features/mghp/review/model.mjs';

test('online approval without a sealed round cannot offer hard-copy actions',()=>{
 assert.equal(model.canStartApplicationReview({status:'approved',evaluationAllowed:true,intakeSealed:false}),false);
});
test('hard-copy list keeps stage, semester and round filters on every page',()=>{
 assert.equal(typeof model.hardCopyListUrl,'function');
 const url=new URL(model.hardCopyListUrl(2,'1','20040'),'https://ctsv.example');
 assert.equal(url.searchParams.get('stage'),'hard-copy');assert.equal(url.searchParams.get('semesterId'),'1');
 assert.equal(url.searchParams.get('roundId'),'20040');assert.equal(url.searchParams.get('page'),'2');
 assert.equal(url.searchParams.has('status'),false);
});
test('unfiltered hard-copy list still uses the server handoff predicate',()=>{
 assert.equal(typeof model.hardCopyListUrl,'function');
 const url=new URL(model.hardCopyListUrl(0,'',''),'https://ctsv.example');
 assert.equal(url.searchParams.get('stage'),'hard-copy');assert.equal(url.searchParams.has('roundId'),false);
});
