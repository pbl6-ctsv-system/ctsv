// Read-only browser regression on the existing DEV draft; sessions remain in RAM.
// Run after review-lifecycle-login.cjs and attach its pages to page.__actions.accounts.
async(page)=>{
 const x=page.__actions,p=x.accounts.staff,base='https://127.0.0.1:5173';
 const assert=(ok,message)=>{if(!ok)throw Error(message);};
 const pass=id=>x.cases.push({id,status:'PASS',database:'ctsv_dev'});
 const context=()=>p.evaluate(async auth=>{
  const r=await fetch('/api/ctsv/mghp/ho-so/10028/ngu-canh-xet-duyet',{headers:{Authorization:auth}});
  if(!r.ok)throw Error('Context status '+r.status());
  const b=await r.json();return b.evaluations.map(e=>({id:e.id,status:e.status,result:e.result}));
 },x.staffAuth);
 const before=await context();x.cases=[];
 await p.goto(base+'/mghp/danh-gia?ho-so=10028');
 const start=p.getByRole('button',{name:'Tiếp tục đối chiếu',exact:true});await start.waitFor();
 assert(await start.isEnabled(),'Approved V1 with an existing draft must be resumable');
 assert(await p.getByRole('button',{name:/Lần đối chiếu.*Chưa kết luận/}).count()===0,'No redundant draft history entry');pass('resume-existing-draft');
 const read=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/danh-gia/10'&&r.status()===200);
 await start.click();await read;
 await p.getByRole('button',{name:'Duyệt bản cứng',exact:true}).waitFor();
 assert(await p.getByRole('button',{name:'Duyệt bản cứng',exact:true}).isEnabled(),'Approval action available');
 assert(await p.getByRole('button',{name:'Từ chối bản cứng',exact:true}).isEnabled(),'Decline action available');
 assert(await p.getByRole('textbox',{name:'Căn cứ xác nhận',exact:true}).count()===0,'Old confirmation field removed');
 assert(await p.getByRole('textbox',{name:'Lý do',exact:true}).count()===0,'No reason input on approval');pass('explicit-actions-no-approval-reason');
 await p.getByRole('button',{name:'Từ chối bản cứng',exact:true}).click();
 const region=p.getByRole('region',{name:'Từ chối bản cứng',exact:true});await region.waitFor();
 assert(await region.getByRole('textbox',{name:'Lý do',exact:true}).isVisible(),'Reason shown only on decline');
 assert(await region.getByRole('button',{name:'Xác nhận từ chối',exact:true}).isDisabled(),'Empty reason blocks decline');
 await region.getByRole('textbox',{name:'Lý do',exact:true}).fill('SYNTHETIC UI-only: không gửi kết luận');
 assert(await region.getByRole('button',{name:'Xác nhận từ chối',exact:true}).isEnabled(),'Reason enables explicit decline confirmation');pass('decline-requires-reason');
 await region.getByRole('button',{name:'Hủy từ chối',exact:true}).click();
 assert(await p.getByRole('textbox',{name:'Lý do',exact:true}).count()===0,'Cancel hides reason');
 assert(await p.getByRole('button',{name:'Duyệt bản cứng',exact:true}).isEnabled(),'Can return to approval');pass('cancel-decline');
 await p.getByRole('button',{name:'Duyệt bản cứng',exact:true}).scrollIntoViewIfNeeded();
 await p.screenshot({path:'D:/7rd_semester/pbl6_src/ctsv/target/review-actions-20261122/actions-desktop.png'});
 await p.setViewportSize({width:390,height:844});
 await p.getByRole('button',{name:'Từ chối bản cứng',exact:true}).click();await region.scrollIntoViewIfNeeded();
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Mobile does not overflow');
 await p.screenshot({path:'D:/7rd_semester/pbl6_src/ctsv/target/review-actions-20261122/actions-mobile.png'});
 await region.getByRole('button',{name:'Hủy từ chối',exact:true}).click();pass('mobile-actions');
 const after=await context();assert(JSON.stringify(before)===JSON.stringify(after),'Resume/cancel preserves the same draft and result');pass('no-duplicate-or-mutated-draft');
 for(const role of ['student','admin']){
  const q=x.accounts[role];await q.goto(base+'/mghp/danh-gia?ho-so=10028');
  await q.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();pass('direct-url-denied-'+role);
 }
 assert(x.errors.length===0,'No page errors');
 return {cases:x.cases,pageErrors:x.errors,before,after,scope:'DEV browser read-only; confirmation HTTP/SQL on guarded lifecycle test DB'};
}
