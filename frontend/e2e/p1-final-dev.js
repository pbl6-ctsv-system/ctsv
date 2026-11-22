// Read-only final regression; role sessions remain in memory. No API token/header capture.
async(page)=>{
 const x=page.__p1,origin='https://127.0.0.1:5173',out='D:/7rd_semester/pbl6_src/ctsv/target/p1-usability-20261122';
 if(!x?.createdApplication||!x.createdAccountUsername)throw Error('Use recorded P1 fixtures; do not recreate them');
 const {staff:c,student:s,admin:a}=x.accounts,assert=(v,m)=>{if(!v)throw Error(m);};
 const pass=(id,extra={})=>x.cases.push({id,status:'PASS',database:'ctsv_dev',...extra});
 await a.goto(origin+'/quan-tri/tai-khoan');
 await a.locator('summary').filter({hasText:'Tìm trong danh mục'}).first().click();
 await a.getByRole('textbox',{name:'Tìm tài khoản cần quản lý',exact:true}).fill(x.createdAccountUsername);
 await a.getByRole('button',{name:'Tìm',exact:true}).click();
 await a.getByRole('combobox',{name:'Tài khoản cần quản lý',exact:true}).locator('option').filter({hasText:x.createdAccountUsername}).waitFor({state:'attached'});
 const accountOption=a.getByRole('combobox',{name:'Tài khoản cần quản lý',exact:true}).locator('option').filter({hasText:x.createdAccountUsername});
 await a.getByRole('combobox',{name:'Tài khoản cần quản lý',exact:true}).selectOption(await accountOption.getAttribute('value'));
 await a.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();await a.getByRole('heading',{name:x.createdAccountUsername,exact:true}).waitFor();pass('P1-07-SEARCH_READ_PERSISTED_ACCOUNT');
 await c.goto(origin+'/mghp/danh-gia?ho-so='+x.createdApplication);
 const history=c.getByRole('button',{name:/^Đánh giá \d+ · Bản /});await history.first().waitFor();assert(await history.count()===1,'Saved draft is missing or duplicated');await history.first().click();
 await c.getByRole('textbox',{name:'Căn cứ mốc nộp và khoảng xét',exact:true}).waitFor();assert((await c.getByRole('textbox',{name:'Căn cứ mốc nộp và khoảng xét',exact:true}).inputValue()).includes('P1 UX: cập nhật'),'Draft read-back does not match saved data');
 const panel=c.getByRole('region',{name:'PDF và nguồn căn cứ',exact:true});assert(await panel.getByRole('button').count()===3,'Missing named papers');await panel.getByRole('button').first().click();await c.locator('iframe[title="Nội dung PDF đang xem"]').waitFor();
 assert(!(await panel.getByRole('button').allTextContents()).some(n=>/^Giấy #/.test(n)),'Raw document ID in choices');pass('P1-05-FINAL_REFRESH_HISTORY_NAMED_PDF');
 await s.goto(origin+'/mghp/quyen-huong');await s.getByText('Chưa có quyền hưởng được ghi nhận.',{exact:false}).waitFor();assert(await s.getByRole('textbox',{name:'Mã quyền hưởng cần xem',exact:true}).count()===0,'Own list still requires ID');
 await s.goto(origin+'/thong-bao');const notice=s.getByRole('link',{name:'Xem hồ sơ liên quan',exact:true}).first();await notice.waitFor();assert(/^\/mghp\/ho-so\?ho-so=[1-9]\d*$/.test(await notice.getAttribute('href')),'Notice lost its application context');await notice.click();await s.getByRole('heading',{name:'Thông tin đã kê khai',exact:true}).waitFor();pass('P1-06-FINAL_NOTICE_CONTEXT_EMPTY_BENEFITS');
 // A controlled transient storage fault tests the retry UI. Successful recovery is REAL.
 let fault=true,stubReads=0;const pattern='**/api/ctsv/tep/*/noi-dung';
 await s.route(pattern,async route=>{if(fault){stubReads++;await route.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({status:503,code:'STORAGE_UNAVAILABLE',title:'Storage unavailable'})});}else await route.continue();});
 try{
  const fileButton=s.getByRole('button',{name:/^Xem PDF/}).first();await fileButton.click();
  await s.getByRole('button',{name:'Thử mở lại PDF',exact:true}).waitFor();fault=false;
  const pending=s.waitForResponse(r=>r.url().includes('/api/ctsv/tep/')&&r.url().endsWith('/noi-dung')&&r.status()===200);
  await s.getByRole('button',{name:'Thử mở lại PDF',exact:true}).click();await pending;await s.locator('iframe[title="Nội dung PDF đang xem"]').waitFor();pass('P1-PDF-FINAL_RETRY',{stubReads,recovery:'Backend SQL Cloudinary REAL200'});
 }finally{await s.unroute(pattern);}
 for(const [role,p] of Object.entries(x.accounts)){
  await p.setViewportSize({width:360,height:800});await p.getByRole('heading').first().waitFor();
  const dimensions=await p.evaluate(()=>({viewport:innerWidth,page:document.documentElement.scrollWidth}));assert(dimensions.page<=dimensions.viewport+1,role+' page overflows at 360px');
  await p.screenshot({path:out+'/'+role+'-mobile.png'});await p.setViewportSize({width:1280,height:900});await p.screenshot({path:out+'/'+role+'-desktop.png'});pass('P1-08-MOBILE-'+role,{dimensions});
 }
 return x.cases;
}
