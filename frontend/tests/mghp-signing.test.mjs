import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveRoute,visibleNavigation} from '../src/app/navigation/modules.mjs';
import {signingLabel,signingPhase,canSign,signContent,geometryFromPixels,selectionKey,uploadResultUncertain,uploadFence} from '../src/features/mghp/signing/model.mjs';
import {createSigningApi} from '../src/features/mghp/signing/api.mjs';
import {noticeAction} from '../src/features/mghp/intake/dossier-model.mjs';

test('P11 signing navigation requires a live backend capability, not an Auth role name',()=>{
 const base={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};
 for(const access of [null,base,{...base,student:true},{...base,ctsvRoleAdmin:true}])assert.equal(resolveRoute('/mghp/ky-so',[{code:'mghp_pho_hieu_truong',active:true}],access).kind,'denied');
 for(const access of [{...base,mghpSigning:true},{...base,mghpSpecialist:true,mghpSigning:true,student:true}]){
  assert.equal(resolveRoute('/mghp/ky-so',[],access).entry?.view,'signing');
  assert.equal(visibleNavigation([],access).filter(x=>x.view==='signing').length,1);
 }
});
test('uncertain upload intent survives form remount without storing credentials',()=>{
 const data=new Map(),storage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};
 const fence=uploadFence(storage,'7','bien_ban');assert.equal(fence.pending(),false);fence.start();
 assert.equal(uploadFence(storage,'7','bien_ban').pending(),true);assert.equal(uploadFence(storage,'8','bien_ban').pending(),false);
 assert.deepEqual([...data.values()],['1']);fence.finish();assert.equal(fence.pending(),false);
});
test('signing inbox has a scoped document link, preserving existing dossier links and rejecting arbitrary URLs',()=>{
 assert.deepEqual(noticeAction('/mghp/ky-so?yeu-cau=12'),{href:'/mghp/ky-so?yeu-cau=12',label:'Xem tài liệu ký số'});
 assert.equal(noticeAction('/mghp/ho-so?bo-sung=1').label,'Xem yêu cầu bổ sung');
 for(const url of ['https://external.invalid','javascript:alert(1)','/mghp/ky-so?yeu-cau=1&next=bad'])assert.equal(noticeAction(url),null);
});
test('a lost or server-failed upload response cannot be treated as safe to upload again',()=>{
 assert.equal(uploadResultUncertain(new Error('network failed')),true);
 assert.equal(uploadResultUncertain({status:503}),true);
 assert.equal(uploadResultUncertain({status:400}),false);
 assert.equal(uploadResultUncertain({status:413}),false);
});
test('a CA success or a pending storage step never claims signed completion',()=>{
 assert.equal(signingLabel({status:'completed'}),'Đã ký xong');
 assert.notEqual(signingLabel({status:'processing',phase:'receipt'}),'Đã ký xong');
 assert.match(signingPhase({phase:'receipt'}),/PDF/);
 assert.equal(canSign({allowedActions:['sign'],status:'prepared'}),true);
 assert.equal(canSign({allowedActions:[],status:'prepared'}),false);
 assert.equal(canSign({allowedActions:['sign'],status:'failed'}),false);
});
test('geometry is converted through PDF viewport including rotation, scale and crop offsets',()=>{
 const viewport={convertToPdfPoint:(x,y)=>[20+y/2,30+x/2]};
 assert.deepEqual(geometryFromPixels(viewport,2,{x:100,y:80,width:200,height:60}),{page:2,x:60,y:80,width:30,height:100});
 assert.throws(()=>geometryFromPixels(viewport,2,{x:1,y:1,width:1,height:1}));
});
test('consent binds the exact file, version and step; PP ACK is composed into one sign command',()=>{
 const q={id:'1',inputFile:{id:'2',sha256:'abc'},currentStep:1};
 assert.notEqual(selectionKey(q,'"a"'),selectionKey(q,'"b"'));
 assert.notEqual(selectionKey(q,'"a"'),selectionKey({...q,inputFile:{id:'3',sha256:'def'}},'"a"'));
 const box={page:2,x:20,y:30,width:100,height:40};
 assert.deepEqual(signContent(box,true),{geometry:box,confirmReviewed:true});
 assert.throws(()=>signContent(null,false));
});
test('sign API forwards the exact ETag and single write; upload uses existing file API without technical user inputs',async()=>{
 const calls=[];let fail=false;
 const api=createSigningApi({request:async(path,options)=>{calls.push({path,options});if(fail)throw new Error('lost response');return new Response('{"id":"7"}',{headers:{ETag:'"fresh"'}});}});
 await api.sign('7',{geometry:{page:1,x:0,y:0,width:10,height:10},confirmReviewed:false},'"v1"');
 assert.equal(calls.at(-1).path,'/api/ctsv/yeu-cau-ky/7/gui-ky');assert.equal(calls.at(-1).options.headers['If-Match'],'"v1"');
 await api.prepare('8');assert.equal(calls.at(-1).options.body,'{}');
 const before=calls.length;fail=true;await assert.rejects(()=>api.sign('7',{},'"v1"'));assert.equal(calls.length,before+1);
});
