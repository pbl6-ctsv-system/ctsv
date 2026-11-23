// Playwright MCP: browser_run_code_unsafe(filename). Read-only final regression.
// Requires an authenticated specialist page and page.__intakeUx containing the
// three owned fixtureApplications, student/admin pages, supplementReason, and
// optional evidenceDir. SQL guard: intake-dev-db.ps1 before. No tokens/passwords
// or browser storage state are saved by this script; fixture IDs are not secrets.
async (page) => {
 const x=page.__intakeUx;
 if(!x?.fixtureApplications?.length||!x.student)throw Error('Prepare the owned UX-INTAKE fixtures and role sessions first; do not select arbitrary user records.');
 const origin='https://127.0.0.1:5173';
 const errors=[];const onError=e=>errors.push(e.message);page.on('pageerror',onError);x.student.on('pageerror',onError);
 const assert=(value,message)=>{if(!value)throw Error(message);};
 await page.goto(origin+'/mghp/tiep-nhan-ho-so');
 await page.getByRole('table',{name:'Danh sách tiếp nhận hồ sơ'}).waitFor();
 assert(await page.getByRole('button',{name:'Xuất Excel',exact:true}).isDisabled(),'Excel must be explicitly unavailable');
 const semesterResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so'&&new URL(r.url()).searchParams.get('semesterId')==='1');
 await page.getByRole('combobox',{name:'Học kỳ',exact:true}).selectOption('1');assert((await semesterResponse).status()===200,'Semester filter');
 const statusResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so'&&new URL(r.url()).searchParams.get('status')==='approved');
 await page.getByRole('button',{name:'Đã duyệt',exact:true}).click();const filtered=await statusResponse;
 const data=await filtered.json();assert(filtered.status()===200&&data.items.every(r=>r.status==='approved'),'Server filters before pagination');
 const owned=data.items.find(r=>r.id===x.fixtureApplications[0]);assert(owned?.policyCode==='M2'&&owned.classCode,'Correct M binding and class snapshot');
 await page.locator('[data-application-id="'+owned.id+'"]').click();await page.getByRole('heading',{name:'Thông tin đã kê khai',exact:true}).waitFor();
 assert((await page.getByRole('region',{name:'Diện và học kỳ'}).innerText()).includes('M2'),'Detail policy code must match its version');
 await page.locator('[aria-label="Nội dung hồ sơ"]').getByRole('button',{name:'Lịch sử & minh chứng',exact:true}).click();
 await page.getByRole('button',{name:'Xem thay đổi sau yêu cầu lần 1',exact:true}).click();await page.getByText('Nội dung thay đổi so với bản nền',{exact:true}).waitFor();
 assert((await page.getByRole('region',{name:'Lịch sử yêu cầu và phản hồi'}).innerText()).includes('0900000001 → 0900000002'),'Persisted response diff');
 if(x.evidenceDir)await page.screenshot({path:x.evidenceDir+'/staff-history.png',fullPage:true});
 await page.setViewportSize({width:360,height:800});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Staff mobile page overflow');
 await page.setViewportSize({width:1280,height:900});
 await page.locator('[aria-label="Nội dung hồ sơ"]').getByRole('button',{name:'Tổng quan',exact:true}).click();
 await page.locator('canvas[data-rendered-file][data-rendered-page]').waitFor();
 if(x.evidenceDir)await page.screenshot({path:x.evidenceDir+'/staff-overview.png',fullPage:true});
 x.cases.push({id:'UX11_FINAL_STAFF_REFRESH_FILTER_HISTORY_MOBILE',status:'PASS',database:'ctsv_dev'});

 const s=x.student;await s.goto(origin+'/mghp/ho-so');await s.locator('[data-application-id="'+owned.id+'"]').click();
 await s.getByRole('heading',{name:'Thông tin đã kê khai',exact:true}).waitFor();await s.getByText('Đã duyệt',{exact:true}).first().waitFor();
 for(const label of ['Tra cứu yêu cầu bằng mã','Thông tin đối chiếu hồ sơ'])assert(await s.getByText(label,{exact:true}).count()===0,'Removed student control '+label);
 assert(await s.getByRole('heading',{name:'Đối chiếu thông tin',exact:true}).count()===0,'Internal review must not appear for student');
 await s.getByRole('button',{name:'Xem PDF 1',exact:true}).click();await s.locator('canvas[data-rendered-file][data-rendered-page]').waitFor();
 if(x.evidenceDir){const pending=s.waitForEvent('download');await s.getByRole('button',{name:'Tải PDF 1',exact:true}).click();await (await pending).saveAs(x.evidenceDir+'/download.pdf');}
 await s.setViewportSize({width:360,height:800});assert(await s.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Student mobile page overflow');
 if(x.evidenceDir)await s.screenshot({path:x.evidenceDir+'/student-overview-mobile.png',fullPage:true});
 await s.setViewportSize({width:1280,height:900});
 await s.getByRole('button',{name:'Về danh sách',exact:true}).click();await s.locator('[data-application-id="'+x.fixtureApplications[1]+'"]').click();
 await s.getByText('Từ chối',{exact:true}).first().waitFor();assert(await s.getByRole('button',{name:'Kê khai nộp lại',exact:true}).count()===0,'Rejected application cannot be resubmitted');
 x.cases.push({id:'UX12_FINAL_STUDENT_DETAILS_PDF_MOBILE_TERMINAL',status:'PASS',database:'ctsv_dev'});
 await s.getByRole('link',{name:/THÔNG BÁO/i}).click();await s.getByRole('heading',{name:'Thông báo của tôi',exact:true}).waitFor();
 const linked=s.locator('a[href="/mghp/ho-so?bo-sung='+x.supplementId+'"]');await linked.click();
 await s.getByRole('heading',{name:'Yêu cầu bổ sung lần 1',exact:true}).waitFor();await s.getByText(x.supplementReason,{exact:true}).waitFor();
 assert(await s.getByText('Tra cứu yêu cầu bằng mã',{exact:true}).count()===0,'Notification must open a request without code entry');
 await s.getByRole('button',{name:'Đăng xuất',exact:true}).click();await s.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
 x.cases.push({id:'UX13_NOTIFICATION_DEEP_LINK_AND_LOGOUT',status:'PASS'});
 page.off('pageerror',onError);s.off('pageerror',onError);assert(errors.length===0,'Unexpected page error in final regression: '+errors.join('; '));
 return {origin,database:'ctsv_dev',services:{backend:'REAL',sqlServer:'REAL',cloudinary:'REAL'},cases:x.cases,fixtureRounds:x.rounds,fixtureApplications:x.fixtureApplications,files:x.files,supplementId:x.supplementId,finalRegressionPageErrors:errors};
}
