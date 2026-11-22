import test from 'node:test';
import assert from 'node:assert/strict';
import {relatedPersonFields,removeRelatedPerson,processLabel,attachUploadedFile} from '../src/features/mghp/intake/declaration-model.mjs';
test('student-only evidence never asks for staff/location identifiers',()=>{
 const p={documentGroups:[{documents:[{evidenceSubject:'sinh_vien'}]}]};
 assert.deepEqual(relatedPersonFields(p),[]);
});
test('parent evidence asks only applicable information, not a staff/source identifier',()=>{
 const p={documentGroups:[{documents:[{evidenceSubject:'cha_hoac_me'}]}]};
 const fields=relatedPersonFields(p);assert.ok(fields.includes('relation'));assert.ok(fields.includes('fullName'));
 assert.equal(fields.includes('matchingStaffCode'),false);assert.equal(fields.includes('workplaceUnitId'),false);
});
test('removing a person never rebinds an existing paper to someone else',()=>{
 const result=removeRelatedPerson([{fullName:'A'},{fullName:'B'},{fullName:'C'}],{x:{relatedPersonIndex:1},y:{relatedPersonIndex:2},z:{relatedPersonIndex:null}},1);
 assert.deepEqual(result.people.map(p=>p.fullName),['A','C']);assert.equal(result.docs.x.relatedPersonIndex,null);assert.equal(result.docs.y.relatedPersonIndex,1);assert.equal(result.docs.z.relatedPersonIndex,null);
});
test('study process uses supplied program/cohort names without inventing a default',()=>{
 assert.equal(processLabel({code:'DEV-PROCESS',programName:'Công nghệ thông tin',cohortCode:'K2026'}),'Công nghệ thông tin · K2026 · DEV-PROCESS');
});
test('upload binds only the chosen requirement and one file can serve several requirements',()=>{
 const docs={a:{requirementId:'a',fileId:'old'},b:{requirementId:'b',fileId:'kept'}};
 const a=attachUploadedFile(docs,'a',{id:'new',fileName:'PDF mẫu.pdf'});assert.equal(a.a.fileId,'new');assert.equal(a.b.fileId,'kept');assert.equal(docs.a.fileId,'old');
 const b=attachUploadedFile(a,'c',{id:'new',fileName:'PDF mẫu.pdf'});assert.equal(b.c.fileId,'new');assert.equal(b.a.fileId,b.c.fileId);assert.equal(b.c.relatedPersonIndex,null);
});
