import test from 'node:test';
import assert from 'node:assert/strict';
import {decisionContent,meetingContent,meetingErrors,decisionActions,decisionLabel,sameGroup,DecisionInputError,requiresDecisionRead,roundState} from '../src/features/mghp/decisions/model.mjs';
import {createDecisionApi} from '../src/features/mghp/decisions/api.mjs';
import {visibleNavigation,resolveRoute,navigationSections} from '../src/app/navigation/modules.mjs';

test('P10 route, dropdown and direct URL share fresh MGHP capability',()=>{
 const cv={student:false,mghpSpecialist:true,ctsvRoleAdmin:false};
 const path='/mghp/hoi-dong-quyet-dinh';
 assert.equal(resolveRoute(path,[],cv).entry?.view,'decisions');
 for(const access of [null,{student:true,mghpSpecialist:false,ctsvRoleAdmin:false},{student:false,mghpSpecialist:false,ctsvRoleAdmin:true}]){
  assert.equal(resolveRoute(path,[],access).kind,'denied');
  assert(!visibleNavigation([{code:'auth.accounts.manage',active:true}],access).some(i=>i.view==='decisions'));
 }
 const multi={...cv,student:true};assert.equal(visibleNavigation([],multi).filter(i=>i.view==='decisions').length,1);
 const group=navigationSections([],cv).find(s=>s.id==='mghp').groups.find(g=>g.id==='decisions');
 assert.equal(group.items[0].path,path);
});
test('create and meeting send only approved fields, never candidates/rates',()=>{
 assert.deepEqual(decisionContent('8',' QD-DEV '),{roundId:'8',code:'QD-DEV',type:'initial',number:null,date:null,issuingUnitId:null});
 const form={minutesCode:' BB-1 ',reference:'',meetingAt:'2026-11-22T09:00',place:'Phòng CTSV',reason:' Đồng ý ',number:'123',date:'2026-11-22'};
 assert.deepEqual(meetingContent(form,{issuingUnitId:'9'}),{minutesCode:'BB-1',reference:null,meetingAt:'2026-11-22T02:00:00.000Z',place:'Phòng CTSV',reason:'Đồng ý',metadata:{number:'123',date:'2026-11-22',issuingUnitId:'9'}});
 assert(!('selections' in meetingContent(form,{})));
});
test('future actual is rejected, nullable metadata accepted, Vietnam time independent of host',()=>{
 const form={minutesCode:'BB-1',reference:'',meetingAt:'2026-11-22T09:00',place:'',reason:'Đã họp',number:'',date:''};
 assert.deepEqual(meetingErrors(form,Date.parse('2026-11-22T02:00:00Z')),[]);
 assert(meetingErrors({...form,meetingAt:'2099-01-01T09:00'},Date.parse('2026-11-22T02:00:00Z')).some(x=>x.includes('tương lai')));
 assert(meetingErrors({...form,reason:' '}).some(x=>x.includes('kết luận')));
 assert.equal(meetingContent(form,{}).metadata.date,null);
});
test('old/locked versions never offer mutation; source eligibility stays server-owned',()=>{
 assert.deepEqual(decisionActions({id:'1',status:'draft',stage:'before_meeting'},'1'),{meeting:true,confirm:false});
 assert.deepEqual(decisionActions({id:'2',status:'draft',stage:'after_meeting'},'2'),{meeting:true,confirm:true});
 assert.deepEqual(decisionActions({id:'1',status:'draft',stage:'before_meeting'},'2'),{meeting:false,confirm:false});
 assert.deepEqual(decisionActions({id:'2',status:'locked',stage:'after_meeting'},'2'),{meeting:false,confirm:false});
 assert.equal(decisionLabel({status:'locked'}),'Đã chốt dữ liệu');
 assert(sameGroup({processId:'1',semesterId:'2',scopeCode:'hp'},{processId:'1',semesterId:'2',scopeCode:'hp'}));
 assert(!sameGroup({processId:'1',semesterId:'2',scopeCode:'hp'},{processId:'1',semesterId:'3',scopeCode:'hp'}));
});
test('API uses exact detail ETag, never retries writes, and pages candidates without truncation',async()=>{
 const calls=[];let fail=false;
 const client={request:async(path,options)=>{calls.push({path,options});if(fail)throw new Error('lost response');
  const data=path.includes('/ung-vien')?{items:[{id:path.includes('page=1')?'22':'11'}],page:path.includes('page=1')?1:0,size:100,totalElements:2,hasMore:!path.includes('page=1')}:{id:'1'};
  return new Response(JSON.stringify(data),{headers:{ETag:'"detail"'}});
 }};
 const api=createDecisionApi(client);
 assert.equal((await api.version('1')).tag,'"detail"');
 await api.meeting('1',{reason:'test'},'"source-tag"');assert.equal(calls.at(-1).options.headers['If-Match'],'"source-tag"');
 assert.equal(calls.at(-1).options.method,'POST');
 assert.deepEqual((await api.candidates('1')).map(x=>x.id),['11','22']);
 fail=true;const before=calls.length;await assert.rejects(()=>api.confirm('1','Chốt','"tag"'));assert.equal(calls.length,before+1);
});
test('client validation can be corrected directly; uncertain writes and stale state require readback',()=>{
 assert.equal(requiresDecisionRead(new DecisionInputError('Ngày tương lai')),false);
 assert.equal(requiresDecisionRead({status:400}),false);
 assert.equal(requiresDecisionRead({status:412}),true);
 assert.equal(requiresDecisionRead(new Error('lost response')),true);
});
test('round summaries without closure field never claim review is still open',()=>{
 assert.equal(roundState({status:'closed'}),'Đã đóng nhận');
 assert.equal(roundState({status:'closed',reviewClosedAt:'2026-11-22T00:00:00Z'}),'Đã đóng xét');
});
