// Playwright MCP function. Uses existing authenticated pages in memory only.
// Read-only final regression against the recorded P10UI-20261122 DEV fixtures.
// The write/fault/race scenarios are recorded separately in browser-cases.json.
async(page)=>{
 const x=page.__p10,origin='https://127.0.0.1:5173',out='D:/7rd_semester/pbl6_src/ctsv/target/p10-ui-20261122';
 if(!x?.accounts?.staff||x.main?.after!=='2')throw Error('Reuse the recorded DEV fixture and in-memory sessions; do not recreate data.');
 const p=x.accounts.staff,assert=(ok,message)=>{if(!ok)throw Error(message);};
 const pass=(id,extra={})=>{x.cases=x.cases.filter(c=>c.id!==id);x.cases.push({id,status:'PASS',database:'ctsv_dev',...extra});};
 await p.setViewportSize({width:1440,height:1050});
 await p.goto(origin+'/mghp/dot-xet');
 const progressResponse=p.waitForResponse(r=>r.url().includes('/dot-xet/20041/tien-do')&&r.status()===200);
 await p.getByRole('row').filter({hasText:'P10UI-20261122-MAIN'}).getByRole('button',{name:'Xem đợt',exact:true}).click();
 const progress=await (await progressResponse).json(),council=progress.stages.find(s=>s.code==='council');
 assert(council?.status==='completed'&&council.actualTime,'Council progress must come from the committed actual timestamp');
 await p.getByText('Hội đồng',{exact:true}).waitFor();
 assert((await p.getByText('Hội đồng',{exact:true}).locator('..').innerText()).includes('Thực tế:'),'Council actual time must be visible');
 await p.screenshot({path:out+'/real-round-council-progress.png',fullPage:true});
 pass('P10_FINAL_ROUND_ACTUAL_COUNCIL',{actualAt:council.actualTime});

 await p.goto(origin+'/mghp/chinh-sach');
 await p.getByRole('heading',{name:'Chính sách miễn giảm học phí',exact:true}).waitFor();
 await p.getByRole('button',{name:'Xem phiên bản',exact:true}).first().waitFor();
 assert(await p.getByRole('button',{name:'Xem phiên bản',exact:true}).count()>=9,'Existing M1–M9 policy catalogue remains reachable');
 pass('P10_FINAL_POLICY_NAVIGATION_REGRESSION');

 await p.goto(origin+'/mghp/hoi-dong-quyet-dinh?dot-xet=20041&phien-ban=2');
 await p.getByRole('heading',{name:'P10UI-QD-HKI-2026',exact:true}).waitFor();
 const rows=p.getByRole('tabpanel').getByRole('row');
 assert(await rows.count()===4,'Expected 3 winner groups, not 4 source assessments');
 const yen=rows.filter({hasText:'DEV-P10-002'});
 assert((await yen.innerText()).includes('50%')&&(await yen.innerText()).includes('31/10/2026'),'Winning M8 must not borrow the longer M9 period');
 assert(await p.getByRole('button',{name:'Chốt dữ liệu',exact:true}).count()===0,'Locked data must have no confirm action');
 await p.screenshot({path:out+'/real-locked-list.png',fullPage:true});
 await yen.getByRole('button',{name:'Xem căn cứ',exact:true}).click();
 const basis=p.getByRole('dialog',{name:'Căn cứ lựa chọn',exact:true});
 await basis.waitFor();await basis.getByRole('row').nth(2).waitFor();
 assert(await basis.getByRole('row').count()===3,'Both eligible source M records must be visible');
 await p.screenshot({path:out+'/real-basis.png',fullPage:true});
 await p.keyboard.press('Escape');await basis.waitFor({state:'hidden'});
 pass('P10_FINAL_WINNER_BASIS_DIALOG_ESCAPE');

 await p.getByRole('tab',{name:'Cuộc họp & văn bản',exact:true}).click();
 assert((await p.getByRole('tabpanel').innerText()).includes('P10UI-BB-HKI-2026'),'Actual meeting/minutes must be displayed');
 assert((await p.getByRole('tabpanel').innerText()).includes('123/P10-DEV'),'Version-specific document header must persist');
 await p.screenshot({path:out+'/real-meeting.png',fullPage:true});
 await p.getByRole('tab',{name:'Lịch sử phiên bản',exact:true}).click();
 await p.getByRole('button',{name:'Xem bản 1',exact:true}).waitFor();
 await p.screenshot({path:out+'/real-history.png',fullPage:true});
 await p.getByRole('button',{name:'Xem bản 1',exact:true}).click();
 await p.getByText('Bản lịch sử, chỉ đọc',{exact:false}).waitFor();
 assert(await p.getByRole('button',{name:'Ghi kết quả cuộc họp',exact:true}).count()===0,'Historical draft is immutable');
 await p.screenshot({path:out+'/real-before-meeting.png',fullPage:true});
 pass('P10_FINAL_VERSION_TABS_AND_IMMUTABILITY');

 await p.goto(origin+'/mghp/hoi-dong-quyet-dinh?dot-xet=20042&phien-ban=5');
 await p.getByRole('button',{name:'Sửa kết quả cuộc họp',exact:true}).waitFor();
 await p.getByRole('button',{name:'Sửa kết quả cuộc họp',exact:true}).click();
 const form=p.getByRole('dialog',{name:'Sửa kết quả cuộc họp',exact:true});await form.waitFor();
 assert(await form.getByLabel('Mã biên bản',{exact:true}).inputValue()==='P10UI-BB-SECOND-2026','Edit current head must load the latest meeting');
 await p.screenshot({path:out+'/real-meeting-form.png',fullPage:true});
 await p.keyboard.press('Escape');await form.waitFor({state:'hidden'});
 pass('P10_FINAL_CURRENT_HEAD_FORM_READONLY_CHECK');

 for(const [role,q] of Object.entries(x.accounts).filter(([role])=>role!=='staff')){
  await q.goto(origin+'/mghp/hoi-dong-quyet-dinh?dot-xet=20041&phien-ban=2');
  await q.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
  assert(await q.getByRole('heading',{name:'P10UI-QD-HKI-2026',exact:true}).count()===0,role+' must not see decision data');
 }
 pass('P10_FINAL_STUDENT_ADMIN_DIRECT_URL_DENIED');
 const admin=x.accounts.admin;await admin.getByRole('button',{name:'Đăng xuất',exact:true}).click();
 await admin.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).waitFor();
 pass('P10_FINAL_LOGOUT');
 await p.goto(origin+'/mghp/hoi-dong-quyet-dinh?dot-xet=20041&phien-ban=2');
 await p.getByRole('heading',{name:'P10UI-QD-HKI-2026',exact:true}).waitFor();
 assert(x.errors.length===0,'Unexpected page JavaScript errors');
 return {cases:x.cases,errors:x.errors,network:x.network.map(r=>({method:r.method,status:r.status,path:r.path})),screenshots:out};
}
