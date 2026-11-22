// Playwright MCP entry. Uses role pages kept ONLY in memory at page.__p1.accounts.
// No password/token/header/storage-state capture. Run after all HMR edits settle.
async (page) => {
 const x=page.__p1;if(!x?.accounts?.staff||!x.accounts.student)throw Error('Prepare the three DEV role contexts and SQL guard first.');
 const origin='https://127.0.0.1:5173';const assert=(value,message)=>{if(!value)throw Error(message);};
 const s=x.accounts.student,c=x.accounts.staff,a=x.accounts.admin;
 if(x.phase==='foundation'){
  await c.goto(origin+'/du-lieu-nen/xac-minh');await c.getByRole('combobox',{name:'Học kỳ cần xác minh',exact:true}).selectOption('1');await c.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();await c.getByText('DEV-2026-1',{exact:true}).waitFor();
  await c.getByRole('combobox',{name:'Loại dữ liệu',exact:true}).selectOption('process');assert(await c.getByText('DEV-2026-1',{exact:true}).count()===0,'old result remains after target type change');
  await c.getByRole('combobox',{name:'Sinh viên cần xác minh',exact:true}).selectOption('1');await c.getByRole('combobox',{name:'Quá trình học',exact:true}).locator('option[value="1"]').waitFor({state:'attached'});
  x.cases.push({id:'P1-02-FOUNDATION_TARGETS',status:'PASS',database:'ctsv_dev'});
 }
 if(x.phase==='student'){
  await s.goto(origin+'/mghp/quyen-huong');await s.getByRole('heading',{name:'Quyền hưởng đã ghi nhận của bạn',exact:true}).waitFor();assert(await s.getByRole('textbox',{name:'Mã quyền hưởng cần xem',exact:true}).count()===0,'own benefits must not require an ID');
  await s.goto(origin+'/thong-bao');const link=s.getByRole('link',{name:'Xem hồ sơ liên quan',exact:true}).first();await link.waitFor();const href=await link.getAttribute('href');assert(/^\/mghp\/ho-so\?ho-so=[1-9]\d*$/.test(href),'invalid application context link');await link.click();await s.getByRole('heading',{name:'Thông tin đã kê khai',exact:true}).waitFor();
  await s.goto(origin+'/quan-tri/tai-khoan');await s.getByRole('alert').filter({hasText:'không có quyền'}).waitFor();assert(await s.getByRole('heading',{name:'Quản lý tài khoản',exact:true}).count()===0,'student admin route leak');
  x.cases.push({id:'P1-06-OWN_BENEFITS_NOTICE_DENIED_ADMIN',status:'PASS',database:'ctsv_dev'});
 }
 if(x.phase==='admin'){
  await a.goto(origin+'/quan-tri/tai-khoan');await a.getByRole('combobox',{name:'Tài khoản cần quản lý',exact:true}).waitFor();assert(await a.getByPlaceholder('UUID tài khoản cần quản lý').count()===0,'manual UUID remains');
  await a.goto(origin+'/quan-tri/phan-quyen-mghp');await a.getByRole('combobox',{name:'Người dùng cần phân quyền',exact:true}).waitFor();await a.waitForFunction(()=>document.querySelector('select[aria-label="Vai trò MGHP"]')?.options.length>1);
  assert(await a.getByRole('textbox',{name:'ID vai trò MGHP',exact:true}).count()===0,'manual role remains');
  x.cases.push({id:'P1-07-NAMED_ADMIN_PICKERS',status:'PASS',database:'ctsv_dev'});
 }
 return x.cases;
}
