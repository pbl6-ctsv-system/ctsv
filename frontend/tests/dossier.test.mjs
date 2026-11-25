import test from 'node:test';
import assert from 'node:assert/strict';
import {applicationListUrl,declarationChanges} from '../src/features/mghp/intake/dossier-model.mjs';

test('application query combines semester, round and status before pagination',()=>{
 const q=new URL(applicationListUrl(3,'submitted','1','20040'),'https://localhost').searchParams;
 assert.equal(q.get('semesterId'),'1');assert.equal(q.get('roundId'),'20040');
 assert.equal(q.get('status'),'submitted');assert.equal(q.get('page'),'3');
 assert(!new URL(applicationListUrl(0,'','1',''),'https://localhost').searchParams.has('roundId'));
});

test('semester and status are sent to the server before pagination',()=>{
 const url=new URL(applicationListUrl(2,'supplement_responded','31'),'https://localhost');
 assert.equal(url.pathname,'/api/ctsv/mghp/ho-so');
 assert.equal(url.searchParams.get('semesterId'),'31');assert.equal(url.searchParams.get('status'),'supplement_responded');
 assert.equal(url.searchParams.get('page'),'2');
 assert(!new URL(applicationListUrl(0,'',''),'https://localhost').searchParams.has('status'));
});
test('history identifies changes to claims and PDFs without treating reused files as new uploads',()=>{
 const before={claims:{phoneNumber:'0900000000',fullName:'Sinh viên mẫu'},statements:{firstDegree:null},documents:[{requirementId:'1',fileId:'4'},{requirementId:'2',fileId:'5'}]};
 const after={claims:{phoneNumber:'0900000001',fullName:'Sinh viên mẫu'},statements:{firstDegree:false},documents:[{requirementId:'1',fileId:'4'},{requirementId:'2',fileId:'6'}]};
 const diff=declarationChanges(before,after);
 assert.deepEqual(diff.claims.map(x=>x.field),['phoneNumber']);
 assert.deepEqual(diff.documents.map(x=>x.requirementId),['2']);
 assert.deepEqual(diff.statements,[{field:'firstDegree',before:null,after:false}]);
 assert.equal(before.documents[1].fileId,'5');
});
test('history distinguishes removed PDFs and genuinely unchanged submissions',()=>{
 const before={claims:{fullName:'Mẫu'},documents:[{requirementId:'1',fileId:'4'}]};
 assert.deepEqual(declarationChanges(before,{...before,documents:[{...before.documents[0],id:'NEW-SUBMITTED-DOCUMENT'}]}),{claims:[],documents:[],statements:[],relatedPeople:[]});
 assert.equal(declarationChanges(before,{...before,documents:[]}).documents[0].after,null);
});

test('history includes corrections or removal of related people',()=>{
 const before={relatedPeople:[{relation:'cha',fullName:'Người liên quan giả lập',citizenId:'TEST-1'}]};
 const after={relatedPeople:[{relation:'cha',fullName:'Người liên quan giả lập',citizenId:'TEST-2'}]};
 assert.deepEqual(declarationChanges(before,after).relatedPeople,[{index:0,fields:[{field:'citizenId',before:'TEST-1',after:'TEST-2'}]}]);
 assert(declarationChanges(before,{relatedPeople:[]}).relatedPeople[0].fields.some(f=>f.after===null));
});
