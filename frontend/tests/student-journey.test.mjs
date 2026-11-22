import test from 'node:test';
import assert from 'node:assert/strict';
import {visibleNavigation} from '../src/app/navigation/modules.mjs';
import * as presentation from '../src/features/mghp/presentation.mjs';

test('student task labels describe personal records, without specialist review wording',()=>{
 const entries=visibleNavigation([],{student:true,mghpSpecialist:false,ctsvRoleAdmin:false});
 assert.equal(entries.find(x=>x.id==='mghp-applications').label,'Hồ sơ của tôi');
 assert.equal(entries.find(x=>x.id==='mghp-benefits').label,'Quyền hưởng của tôi');
 assert(!entries.some(x=>x.view==='staff-applications'||x.view==='evaluations'));
});
const open={id:'42',status:'open',opensAt:'2026-11-21T02:00:00Z',submissionDeadline:'2026-11-21T12:00:00Z'};
test('submission CTA follows the inclusive window, with a reason for unavailable rounds',()=>{
 assert.equal(typeof presentation.submissionWindow,'function');
 for(const now of ['2026-11-21T02:00:00Z','2026-11-21T07:00:00Z','2026-11-21T12:00:00Z'])assert.equal(presentation.submissionWindow(open,Date.parse(now)).canStart,true);
 assert.equal(presentation.submissionWindow(open,Date.parse('2026-11-21T01:59:59Z')).state,'upcoming');
 assert.equal(presentation.submissionWindow(open,Date.parse('2026-11-21T12:00:00.001Z')).state,'expired');
 for(const status of ['closed','completed','draft',null])assert.equal(presentation.submissionWindow({...open,status},Date.parse('2026-11-21T07:00:00Z')).canStart,false);
 for(const dates of [{opensAt:null},{submissionDeadline:null},{opensAt:'bad'},{opensAt:'2026-11-22T02:00:00Z'}])assert.equal(presentation.submissionWindow({...open,...dates},Date.parse('2026-11-21T07:00:00Z')).canStart,false);
});
test('staff and combined capabilities keep distinct personal and professional tasks',()=>{
 const staff=visibleNavigation([],{student:false,mghpSpecialist:true,ctsvRoleAdmin:false});
 assert.equal(staff.find(x=>x.id==='mghp-benefits').label,'Rà soát quyền hưởng');
 const both=visibleNavigation([],{student:true,mghpSpecialist:true,ctsvRoleAdmin:false});
 assert.equal(both.find(x=>x.id==='mghp-applications').label,'Hồ sơ của tôi');
 assert.equal(both.find(x=>x.id==='mghp-benefits').label,'Rà soát quyền hưởng');
});
