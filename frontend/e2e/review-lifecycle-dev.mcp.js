// Uses DEV sessions in RAM from review-lifecycle-login.cjs, attached over loopback CDP.
// No mock API, storageState, token/header/body trace or credential output.
async(page)=>{
 const x=page.__rl,p=x.accounts.staff,origin='https://127.0.0.1:5173',out='D:/7rd_semester/pbl6_src/ctsv/target/review-lifecycle-20261122';
 const assert=(ok,message)=>{if(!ok)throw Error(message);};
 const pass=(id)=>{x.cases=x.cases.filter(c=>c.id!==id);x.cases.push({id,status:'PASS',db:'ctsv_dev'});};
 async function approve(root){
  await p.goto(origin+'/mghp/tiep-nhan-ho-so');await p.locator('[data-application-id="'+root+'"]').click();
  await p.getByRole('button',{name:'Đồng ý PDF',exact:true}).waitFor();
  if(await p.getByRole('button',{name:'Đồng ý PDF',exact:true}).isEnabled()){
   const checked=p.waitForResponse(r=>r.url().includes('/kiem-tra')&&r.status()===201);await p.getByRole('button',{name:'Đồng ý PDF',exact:true}).click();await checked;
  }
  await p.getByRole('button',{name:'Duyệt hồ sơ online',exact:true}).click();
  const d=p.getByRole('dialog',{name:'Duyệt hồ sơ online này?',exact:true});await d.getByRole('button',{name:'Xác nhận kết luận',exact:true}).click();await d.waitFor({state:'hidden'});
  await p.getByText('Đã duyệt V1 — hồ sơ online',{exact:true}).first().waitFor();
 }
 for(const [kind,fixture] of Object.entries(x.fixtures)){
  let root=await x.api('GET','/ho-so/'+fixture.root);
  assert(root.body.code==='RLUI-20261122-'+kind,'Only operate on the marked fixture');
  if(root.body.status==='submitted')await approve(fixture.root);
  root=await x.api('GET','/ho-so/'+fixture.root);
  if(root.body.workflowStatus==='approved_v2'||root.body.workflowStatus==='hard_copy_failed')continue;
  await p.goto(origin+'/mghp/danh-gia?ho-so='+fixture.root);
  await p.getByRole('button',{name:/^(Bắt đầu|Tiếp tục) đối chiếu$/}).click();
  await p.getByRole('heading',{name:'Đối chiếu thông tin sinh viên và Đào tạo',exact:true}).waitFor();
  assert((await p.getByRole('row').filter({hasText:'Số CCCD'}).innerText()).includes('DEV-SYNTHETIC-ID'),'Citizen ID must remain visible text');
  await p.getByRole('button',{name:'Giấy xác nhận synthetic · Bản nộp 1',exact:true}).click();
  await p.locator('iframe[title="Nội dung PDF đang xem"]').waitFor({timeout:20000});
  pass('RL-UI-PDF-'+kind);
  for(const name of ['Đã nhận bản cứng','Hình thức và thẩm quyền phù hợp','Hiệu lực giấy phù hợp','Đúng người được chứng minh']){
   const b=p.getByRole('button',{name,exact:true});
   if(await b.count()&&await b.isEnabled()){
    const saved=p.waitForResponse(r=>/\/(kiem-tra|xac-nhan-ban-cung)$/.test(new URL(r.url()).pathname)&&r.status()===201);
    await b.click();await saved;await p.getByRole('button',{name:'✓ '+name,exact:true}).waitFor();
   }
  }
  pass('RL-UI-PAPER-CHECKS-'+kind);
  const result=kind==='DAT'?'dat':'khong_dat';
  await p.getByRole('combobox',{name:'Kết quả tiêu chí Đủ căn cứ synthetic',exact:true}).selectOption(result);
  if(kind==='FAIL')await p.getByRole('textbox',{name:'Nhận xét tiêu chí',exact:true}).fill('Bản cứng không chứng minh đúng điều kiện bắt buộc.');
  await p.getByRole('button',{name:'Thêm giấy làm căn cứ',exact:true}).click();
  await p.getByRole('combobox',{name:'Kết quả dùng giấy',exact:true}).selectOption(result);
  await p.getByRole('textbox',{name:'Lý do dùng giấy',exact:true}).fill(kind==='DAT'?'Đã xem PDF và đối chiếu bản cứng synthetic.':'Bản cứng không đáp ứng điều kiện.');
  if(kind==='DAT'){
   await p.getByRole('textbox',{name:'Căn cứ mốc nộp và khoảng xét',exact:true}).fill('Chỉ xác nhận khoảng tháng9–10 theo căn cứ thử nghiệm, không kéo dài.');
   await p.getByLabel('Từ ngày',{exact:true}).fill('2026-09-01');await p.getByLabel('Đến ngày',{exact:true}).fill('2026-10-31');
   await p.locator('summary').filter({hasText:'Thời gian đủ điều kiện hưởng'}).click();
   await p.getByRole('button',{name:'Thêm khoảng đủ điều kiện',exact:true}).click();
   await p.getByLabel('Từ ngày đủ điều kiện',{exact:true}).fill('2026-09-01');await p.getByLabel('Đến ngày đủ điều kiện',{exact:true}).fill('2026-10-31');
   await p.getByRole('textbox',{name:'Căn cứ khoảng ngày',exact:true}).fill('Giấy và quá trình học có căn cứ trong khoảng synthetic tháng9–10.');
  }
  assert(await p.getByRole('textbox',{name:'Lý do',exact:true}).count()===0,'Approval must not ask for a reason');
  if(kind==='FAIL'){
   await p.getByRole('button',{name:'Từ chối bản cứng',exact:true}).click();
   await p.getByRole('textbox',{name:'Lý do',exact:true}).fill('Nội dung bản cứng không đáp ứng yêu cầu bắt buộc — SYNTHETIC.');
  }
  const confirmed=p.waitForResponse(r=>r.url().includes('/danh-gia/')&&r.url().endsWith('/xac-nhan'));
  await p.getByRole('button',{name:kind==='DAT'?'Duyệt bản cứng':'Xác nhận từ chối',exact:true}).click();
  assert((await confirmed).status()===200,'Confirm must commit successfully');
  const expected=kind==='DAT'?'approved_v2':'hard_copy_failed';
  await p.getByText(kind==='DAT'?'Đã duyệt V2 — bản cứng':'Đã từ chối bản cứng',{exact:true}).first().waitFor();
  root=await x.api('GET','/ho-so/'+fixture.root);assert(root.body.workflowStatus===expected,'Persisted workflow must match UI');
  if(kind==='FAIL')assert(await p.getByRole('button',{name:'Lập lần đối chiếu tiếp theo',exact:true}).count()===0,'Failure is terminal');
  pass('RL-UI-CONFIRM-'+kind);await p.screenshot({path:out+'/review-'+kind.toLowerCase()+'.png',fullPage:true});
 }
 // Revisit the terminal case after browser refresh, then check direct-URL authorization.
 await p.goto(origin+'/mghp/danh-gia?ho-so=10036');
 await p.getByText('Đã từ chối bản cứng',{exact:true}).first().waitFor();
 assert(await p.getByRole('button',{name:'Xem kết quả đối chiếu',exact:true}).isDisabled(),'Cannot start another assessment after terminal failure');pass('RL-UI-TERMINAL-REFRESH');
 for(const role of ['student','admin']){
  const q=x.accounts[role];await q.goto(origin+'/mghp/danh-gia');await q.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();pass('RL-UI-DENIED-'+role);
 }
 const s=x.accounts.student;await s.goto(origin+'/mghp/ho-so');await s.locator('[data-application-id="10035"]').waitFor();
 assert((await s.locator('main').innerText()).includes('Đã duyệt V2 — bản cứng'),'Student own list reflects stage2');
 assert((await s.locator('main').innerText()).includes('Đã từ chối bản cứng'),'Student own list reflects terminal paper failure');pass('RL-UI-STUDENT-OWN-STATES');
 await p.setViewportSize({width:390,height:844});
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Mobile page must not overflow');await p.screenshot({path:out+'/review-mobile.png',fullPage:true});pass('RL-UI-MOBILE');
 return {cases:x.cases,pageErrors:x.errors};
}
