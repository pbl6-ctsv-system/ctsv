import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluationContent} from '../src/features/mghp/review/model.mjs';
import {visibleNavigation,resolveRoute} from '../src/app/navigation/modules.mjs';
test('evaluation write strips server fields and preserves missing evidence without inventing periods',()=>{
 const data=evaluationContent({id:'1',revision:9,result:'can_xac_minh',eligiblePeriods:[],criteria:[],evidence:[]});
 assert(!Object.hasOwn(data,'id'));assert(!Object.hasOwn(data,'revision'));
 assert.deepEqual(data.eligiblePeriods,[]);assert.equal(data.birthDateUsed,null);
});
test('assessment routes require specialist capability and own benefit page stays available to student',()=>{
 const student={student:true,mghpSpecialist:false,ctsvRoleAdmin:false},staff={student:false,mghpSpecialist:true,ctsvRoleAdmin:false};
 assert.equal(resolveRoute('/mghp/danh-gia',[],student).kind,'denied');
 assert.equal(resolveRoute('/mghp/danh-gia',[],staff).entry.view,'evaluations');
 assert.equal(resolveRoute('/mghp/quyen-huong',[],student).entry.view,'own-benefits');
 assert.equal(visibleNavigation([],staff).find(entry=>entry.id==='mghp-benefits').view,'benefits');
 const combined={...staff,student:true};
 const entries=visibleNavigation([],combined).filter(entry=>entry.id==='mghp-benefits');
 assert.equal(entries.length,1);assert.equal(entries[0].view,'benefits');
});
