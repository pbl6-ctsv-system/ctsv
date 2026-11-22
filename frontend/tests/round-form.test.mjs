import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyRound,roundContent} from '../src/features/mghp/configuration/model.mjs';

test('round wire retires guidance and keeps the nullable timeline',()=>{
 const body=roundContent({...emptyRound(),declarationGuidance:'old',lateSubmissionGuidance:'old'});
 assert.equal(Object.hasOwn(body,'declarationGuidance'),false);
 assert.equal(Object.hasOwn(body,'lateSubmissionGuidance'),false);
 assert.equal(Object.keys(body.schedule).length,8);
 assert.ok(Object.values(body.schedule).every(x=>x===null));
});
test('hidden modes always use the approved values, not stale/null form values',()=>{
 const body=roundContent({...emptyRound(),retroactiveMode:null,midSemesterMode:null,allowRetroactive:false});
 assert.equal(body.retroactiveMode,'theo_phap_luat');
 assert.equal(body.midSemesterMode,'khoang_ngay_chuyen_vien_xac_nhan');
 assert.equal(body.allowRetroactive,false);
});
test('multiple selections retain template bindings but do not leak UI policy IDs into the API',()=>{
 const body=roundContent({...emptyRound(),policyVersions:[{policyId:'1',policyVersionId:'8',applicationTemplateFileId:'44',order:1},{policyId:'2',policyVersionId:'9',applicationTemplateFileId:null,order:2}]});
 assert.deepEqual(body.policyVersions,[{policyVersionId:'8',applicationTemplateFileId:'44',order:1},{policyVersionId:'9',applicationTemplateFileId:null,order:2}]);
});
