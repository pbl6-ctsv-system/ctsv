
import test from 'node:test';
import assert from 'node:assert/strict';
import {navigationItems,visibleNavigation,navigationSections,resolveRoute} from '../src/app/navigation/modules.mjs';
import {createAuthClient as movedClient} from '../src/shared/auth/auth-client.mjs';
import {createAuthClient as compatibleClient} from '../src/shared/auth-client.mjs';

test('navigation identifies implemented feature entries without publishing empty modules',()=>{
 assert.equal(new Set(navigationItems.map(item=>item.id)).size,navigationItems.length);
 assert.deepEqual(navigationItems.map(item=>[item.id,item.feature]),[
  ['shcn-rounds','shcn'],
  ['shcn-assignments','shcn'],
  ['lookup','foundation'],
  ['students','foundation'],
  ['manage','foundation'],
  ['roles','foundation'],
  ['auth','auth'],
  ['auth-rbac','auth'],
  ['mghp-policies','mghp'],
  ['mghp-rounds','mghp'],
  ['mghp-applications','mghp'],
  ['mghp-casework','mghp'],
  ['mghp-evaluations','mghp'],
  ['mghp-benefits','mghp'],
  ['mghp-decisions','mghp'],
  ['mghp-signing','mghp'],
  ['mghp-inbox','mghp'],
  ['hocbong-rounds','hocbong']
 ]);
 assert(navigationItems.every(item=>typeof item.label==='string'&&item.label.length>0));
});

test('student menu contains only the student workflow',()=>{
 assert.deepEqual(
  visibleNavigation([],{student:true,mghpSpecialist:false,ctsvRoleAdmin:false}).map(item=>item.id),
  ['mghp-rounds','mghp-applications','mghp-benefits','mghp-inbox']
 );
});

test('specialist gets verification and case review without student submission or account administration',()=>{
 const menu=visibleNavigation([],{student:false,mghpSpecialist:true,ctsvRoleAdmin:false});
 assert.deepEqual(menu.map(item=>item.id),[
  'lookup','students','manage','mghp-policies','mghp-rounds',
  'mghp-casework','mghp-evaluations','mghp-benefits','mghp-decisions','mghp-inbox'
 ]);
 assert.equal(menu.find(item=>item.id==='mghp-casework').view,'staff-applications');
 assert.equal(menu.find(item=>item.id==='mghp-casework').label,'Tiếp nhận & bổ sung hồ sơ');
 assert(!menu.some(item=>item.label==='Hồ sơ MGHP'||item.view==='applications'));
});

test('Auth account administration and CTSV permissions are separate and fail closed',()=>{
 const functions=[
  {code:'auth.accounts.manage',active:true},
  {code:'auth.rbac.read',active:false}
 ];
 assert.deepEqual(visibleNavigation(functions,null).map(item=>item.id),['auth']);
 assert.deepEqual(
  visibleNavigation([],{student:false,mghpSpecialist:false,ctsvRoleAdmin:true}).map(item=>item.id),
  ['roles','mghp-inbox']
 );
 assert.deepEqual(
  visibleNavigation([{code:'auth.rbac.read',active:true}],null).map(item=>item.id),
  ['auth-rbac']
 );
});

test('legacy auth import and modular auth import share the same implementation',()=>{
 assert.equal(movedClient,compatibleClient);
});

test('student menu keeps implemented routes and disables only unavailable modules',()=>{
 const access={student:true,mghpSpecialist:false,ctsvRoleAdmin:false};
 const sections=navigationSections([],access);
 assert.deepEqual(sections.map(item=>item.id),['home','drl','mghp','shcn','notifications']);
 assert.deepEqual(
  sections.filter(item=>item.kind==='disabled').map(item=>item.id),
  ['drl','shcn']
 );
 assert.deepEqual(
  sections.find(item=>item.id==='mghp').groups.flatMap(group=>group.items.map(item=>item.path)),
  ['/mghp/dot-xet','/mghp/ho-so','/mghp/quyen-huong']
 );
});

test('direct links and refresh use the same access decisions as the navbar',()=>{
 const student={student:true,mghpSpecialist:false,ctsvRoleAdmin:false};
 assert.equal(resolveRoute('/',[],student).kind,'home');
 assert.equal(resolveRoute('/mghp/ho-so/',[],student).entry.view,'applications');
 assert.equal(resolveRoute('/mghp/tiep-nhan-ho-so',[],student).kind,'denied');

 const specialist={student:false,mghpSpecialist:true,ctsvRoleAdmin:false};
 assert.equal(resolveRoute('/mghp/ho-so',[],specialist).kind,'denied');
 assert.equal(resolveRoute('/mghp/tiep-nhan-ho-so',[],specialist).entry.view,'staff-applications');
 assert.equal(resolveRoute('/mghp/chinh-sach',[],student).kind,'denied');
 assert.equal(resolveRoute('/mghp/ho-so',[],null).kind,'denied');
 assert.equal(resolveRoute('/hoc-bong',[],student).kind,'missing');
});

test('combined permissions union menu sections without duplicating pages',()=>{
 const access={student:true,mghpSpecialist:true,ctsvRoleAdmin:true};
 const functions=[{code:'auth.accounts.manage',active:true}];
 const sections=navigationSections(functions,access);
 const routes=sections.flatMap(item=>
  item.kind==='dropdown'
   ?item.groups.flatMap(group=>group.items.map(entry=>entry.path))
   :item.kind==='link'?[item.path]:[]
 );

 assert.equal(routes.length,new Set(routes).size);
 assert(sections.some(item=>item.id==='foundation'));
 assert(sections.some(item=>item.id==='admin'));
 assert.equal(resolveRoute('/mghp/dot-xet',functions,access).entry.view,'public-rounds');
 assert.equal(resolveRoute('/mghp/chinh-sach',functions,access).entry.view,'policies');
 assert.equal(resolveRoute('/mghp/ho-so',functions,access).entry.view,'applications');
 assert.equal(resolveRoute('/mghp/tiep-nhan-ho-so',functions,access).entry.view,'staff-applications');

 const casePages=visibleNavigation(functions,access)
  .filter(item=>['applications','staff-applications'].includes(item.view));

 assert.equal(casePages.length,2);
 assert.equal(new Set(casePages.map(item=>item.label)).size,2);
});

// SHCN permission tests
test('SHCN faculty and round permissions expose only their respective pages',()=>{
 const facultyAccess={
  student:false,
  mghpSpecialist:false,
  ctsvRoleAdmin:false,
  shcnFacultyRead:true,
  shcnRoundRead:false
 };

 assert.deepEqual(
  visibleNavigation([],facultyAccess).map(item=>item.id),
  ['shcn-assignments']
 );

 const roundAccess={
  student:false,
  mghpSpecialist:false,
  ctsvRoleAdmin:false,
  shcnFacultyRead:false,
  shcnRoundRead:true
 };

 assert.deepEqual(
  visibleNavigation([],roundAccess).map(item=>item.id),
  ['shcn-rounds']
 );
});

test('SHCN and scholarship permissions coexist without overriding each other',()=>{
 const access={
  student:false,
  mghpSpecialist:false,
  ctsvRoleAdmin:false,
  shcnFacultyRead:true,
  shcnRoundRead:true
 };

 const scholarship={student:false,specialist:true};
 const menu=visibleNavigation([],access,scholarship);

 assert.deepEqual(
  menu.map(item=>item.id),
  ['shcn-rounds','shcn-assignments','hocbong-rounds']
 );

 assert.equal(resolveRoute('/shcn/phan-cong-gvcn',[],access,scholarship).kind,'page');
 assert.equal(resolveRoute('/shcn/dot-sinh-hoat',[],access,scholarship).kind,'page');
 assert.equal(resolveRoute('/hoc-bong/kkht/dot-xet',[],access,scholarship).kind,'page');
});

test('scholarship specialist sees KKHT navigation and direct route',()=>{
 const access={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};
 const scholarship={student:false,specialist:true};

 const menu=visibleNavigation([],access,scholarship);

 assert.deepEqual(menu.map(item=>item.id),['hocbong-rounds']);
 assert.equal(resolveRoute('/hoc-bong/kkht/dot-xet',[],access,scholarship).kind,'page');

 const sections=navigationSections([],access,scholarship);
 assert(sections.some(section=>section.id==='scholarships'));
});

test('users without scholarship permission cannot access KKHT routes',()=>{
 const access={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};

 assert.deepEqual(visibleNavigation([],access,null),[]);
 assert.equal(resolveRoute('/hoc-bong/kkht/dot-xet',[],access,null).kind,'denied');
});
