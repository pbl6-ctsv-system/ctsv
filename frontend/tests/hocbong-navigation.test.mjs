import test from 'node:test';
import assert from 'node:assert/strict';
import {visibleNavigation,navigationSections,resolveRoute} from '../src/app/navigation/modules.mjs';

const path='/hoc-bong/kkht/dot-xet';
const legacy={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};

test('HB-T021-01: scholarship specialist receives the KKHT route from HB groups alone',()=>{
 const groups={student:false,specialist:true};
 const entries=visibleNavigation([],null,groups);
 assert.equal(entries.find(entry=>entry.path===path)?.feature,'hocbong');
 assert.equal(resolveRoute(path,[],null,groups).kind,'page');
 const menu=navigationSections([],null,groups).find(section=>section.id==='scholarships');
 assert.equal(menu?.kind,'dropdown');
 assert.equal(menu.groups.flatMap(group=>group.items).filter(entry=>entry.path===path).length,1);
});

test('HB-T021-01: student, unrelated specialist and Auth admin cannot enter KKHT management',()=>{
 for(const access of [null,legacy,{...legacy,student:true},{...legacy,mghpSpecialist:true}]){
  for(const groups of [null,{student:true,specialist:false},{student:false,specialist:false},{student:false,specialist:'true'}]){
   assert(!visibleNavigation([{code:'auth.accounts.manage',active:true}],access,groups).some(entry=>entry.path===path));
   assert.equal(resolveRoute(path,[],access,groups).kind,'denied');
  }
 }
});

test('HB-T021-01: combined student and HB specialist still opens management, without duplicate links',()=>{
 const groups={student:true,specialist:true};
 const access={...legacy,student:true};
 const entries=visibleNavigation([],access,groups).filter(entry=>entry.path===path);
 assert.equal(entries.length,1);
 assert.equal(resolveRoute(path+'/',[],access,groups).entry.view,'rounds');
});

test('HB-T021-02: withdrawn or unavailable HB groups remove the menu and direct route',()=>{
 assert.equal(resolveRoute(path,[],legacy,{student:false,specialist:true}).kind,'page');
 for(const groups of [null,{student:false,specialist:false}]){
  assert.equal(resolveRoute(path,[],legacy,groups).kind,'denied');
  assert(!navigationSections([],legacy,groups).some(section=>section.id==='scholarships'));
 }
});
