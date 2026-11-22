import test from 'node:test';
import assert from 'node:assert/strict';
import {pdfAccepted,nextPendingPaper,approvalBlockers} from '../src/features/mghp/intake/review-model.mjs';
import {finishGeneralSupplement} from '../src/features/mghp/intake/supplement-workflow.mjs';

const paper=(id,result)=>({id,checks:result?[{kind:'content',result}]:[]});
test('approval explains pending PDFs and other failed checks without pretending they are accepted',()=>{
 const values=[paper('a','pass'),paper('b')];values[0].checks.push({kind:'validity',result:'needs_verification'});
 assert.deepEqual(approvalBlockers(values,[]),{pendingPdfIds:['b'],otherCheckIds:['a'],supplementIds:[]});
 values[0].checks.push({kind:'validity',result:'pass'});values[1].checks.push({kind:'content',result:'pass'});
 assert.deepEqual(approvalBlockers(values,[]),{pendingPdfIds:[],otherCheckIds:[],supplementIds:[]});
});
test('an open or expired supplement blocks approval; a general reply may be concluded by the existing command',()=>{
 const result=approvalBlockers([paper('a','pass')],[{id:'1',status:'open'},{id:'2',status:'responded'},{id:'3',status:'completed'},{id:'4',status:'expired'}]);
 assert.deepEqual(result.supplementIds,['1','4']);
});
test('next PDF works with any list length, skips accepted papers and wraps without selecting the current paper',()=>{
 const papers=[paper('a','pass'),paper('b'),paper('c','pass'),paper('d'),paper('e')];
 assert.equal(nextPendingPaper(papers,'a'),'b');assert.equal(nextPendingPaper(papers,'d'),'e');
 assert.equal(nextPendingPaper(papers,'e'),'b');assert.equal(nextPendingPaper([paper('a')],'a'),null);
 assert.equal(nextPendingPaper([paper('a','pass'),paper('b','pass')],'a'),null);
});
test('PDF state comes from its most recent check and unresolved checks remain visible',()=>{
 assert.equal(pdfAccepted({checks:[{kind:'content',result:'fail'},{kind:'content',result:'pass'}]}),true);
 assert.equal(pdfAccepted({checks:[{kind:'content',result:'pass'},{kind:'content',result:'fail'}]}),false);
 assert.equal(pdfAccepted(paper('a')),false);
});
function fixture({state='responded',type='general',failReview=false}={}){
 let version=1;const writes=[];const item={id:'11',type,reviewResult:null};
 return {writes,client:{async request(path,options={}){
  if(options.method){assert.equal(options.headers['If-Match'],'"v'+version+'"');
   writes.push({path,body:JSON.parse(options.body)});if(failReview&&path.includes('/danh-gia'))throw Error('offline');
   version++;if(path.endsWith('/danh-gia'))item.reviewResult=JSON.parse(options.body).result;
   if(path.endsWith('/hoan-tat'))state='completed';
  }
  return {headers:new Headers({ETag:'"v'+version+'"'}),async json(){return {id:'7',status:state,items:[item]};}};
 }}};
}
test('general response is explicitly reviewed then completed with fresh ETags',async()=>{
 const f=fixture();await finishGeneralSupplement(f.client,'7','unsatisfied','Cần bản rõ hơn');
 assert.equal(f.writes.length,2);assert.equal(f.writes[0].body.result,'unsatisfied');
 assert.equal(f.writes[1].path,'/api/ctsv/mghp/yeu-cau-bo-sung/7/hoan-tat');
});
test('review failure stops completion; completed response is safe to read again',async()=>{
 const failed=fixture({failReview:true});await assert.rejects(finishGeneralSupplement(failed.client,'7','satisfied','Đã kiểm tra'),/offline/);
 assert.equal(failed.writes.length,1);
 const done=fixture({state:'completed'});await finishGeneralSupplement(done.client,'7','satisfied','Đã kiểm tra');assert.equal(done.writes.length,0);
});
test('a pending student reply or legacy typed request is never auto-graded by the general workflow',async()=>{
 for(const args of [{state:'open'},{type:'document'}]){
  const f=fixture(args);await assert.rejects(finishGeneralSupplement(f.client,'7','unsatisfied','Cần bổ sung'));assert.equal(f.writes.length,0);
 }
});
test('a changed response is not silently accepted with a fresh tag',async()=>{
 const f=fixture();await assert.rejects(finishGeneralSupplement(f.client,'7','satisfied','Đã kiểm tra','"old"'),e=>e.status===412);
 assert.equal(f.writes.length,0);
});
