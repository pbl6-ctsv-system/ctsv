import test from 'node:test';
import assert from 'node:assert/strict';
import {declarationDefaults} from '../src/features/mghp/intake/declaration-model.mjs';
const context={round:{semesterId:'12'},studyProcesses:[{id:'71'}],policyVersions:[{id:'81'},{id:'82'}]};
test('new declaration hides technical choices while keeping source IDs and unknown benefit declaration',()=>{
 const value=declarationDefaults(context,null,null);
 assert.equal(value.process,'71');assert.deepEqual(value.semesters,['12']);assert.equal(value.policy,'');
 assert.equal(value.statements.alreadyBenefitedAtSameLevel,null);
});
test('never silently picks the first of multiple processes or invents a missing process',()=>{
 for(const studyProcesses of [[],[{id:'1'},{id:'2'}]])assert.equal(declarationDefaults({...context,studyProcesses},null,null).process,'');
});
test('single policy is selected automatically; multiple policies need an explicit choice',()=>{
 assert.equal(declarationDefaults({...context,policyVersions:[{id:'81'}]},null,null).policy,'81');
 assert.equal(declarationDefaults(context,null,null).policy,'');
});
test('resubmission and supplement preserve the bound process, policy, previous semester requests and statements',()=>{
 const initial={requestedSemesters:[{semesterId:'9'},{semesterId:'10'}],statements:{firstDegree:true,alreadyBenefitedAtSameLevel:true,salariedWhileStudying:false,declarationConfirmed:true}};
 const result=declarationDefaults({...context,studyProcesses:[{id:'77'},{id:'78'}]},initial,{studyProcessId:'78',policyVersionId:'82'});
 assert.equal(result.process,'78');assert.equal(result.policy,'82');assert.deepEqual(result.semesters,['9','10']);assert.equal(result.statements.alreadyBenefitedAtSameLevel,true);
 assert.equal(initial.statements.alreadyBenefitedAtSameLevel,true);
});
