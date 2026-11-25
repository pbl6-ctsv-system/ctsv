// Real Chrome UI + HTTP fixtures. No SQL, Flyway, seed or real write request.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
const origin=process.env.SHCN_UI_ORIGIN??'https://127.0.0.1:5173';
const evidence=process.env.SHCN_UI_EVIDENCE??'./shcn-round-audiences-evidence';await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[];
const semester={ma:'0010',ten:'Học kỳ thực hành',namHoc:'2026-2027'};
const initial=()=>({id:'7',version:'AAAAAAAAAAE=',maHocKy:'0010',tenDot:'Đợt sinh hoạt thực hành',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hanChotDangKyLich:'2026-11-28T10:00:00Z',trangThai:'NHAP',trangThaiPhanCong:'CHUA_DONG_BO',phienBanPhanCongApDung:null,trangThaiLichTongHop:'DANG_CHOT',phienBanLichTongHop:null,ngayPhatHanh:null,ngayCongBoLich:null,ngayTao:'2026-11-21T00:00:00Z',ngayCapNhat:'2026-11-21T00:00:00Z'});
async function run(name,fn,options={}){
 if(process.env.SHCN_UI_CASE&&!name.includes(process.env.SHCN_UI_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:true});const page=await context.newPage();page.setDefaultTimeout(12000);const errors=[];page.on('pageerror',e=>errors.push(e.message));let d={...initial(),doiTuongApDung:options.withAudiences?[{loaiDoiTuong:'KHOA_HOC',maDoiTuong:'2023',hinhThucChoPhep:'OFFLINE',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:null}]:[]};const writes=[];
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const r=route.request(),u=new URL(r.url()),path=u.pathname;
  const reply=(json,status=200)=>route.fulfill({json,status,headers:{'X-SHCN-Can-Prepare':String(!options.readOnly),'Cache-Control':'private, no-store'}});
  if(path.endsWith('/dang-nhap')||path.endsWith('/lam-moi-token'))return reply({accessToken:'synthetic-token',expiresIn:900});
  if(path.endsWith('/xac-thuc/toi'))return reply({accountId:'fixture',username:'SYNTHETIC specialist',functions:[{code:options.readOnly?'shcn.dot.read':'shcn.dot.prepare',active:true},{code:'shcn.reference.read',active:true}]});
  if(path.endsWith('/quyen-giao-dien'))return reply({student:false,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(path.endsWith('/pham-vi'))return reply({code:'FORBIDDEN'},403);
  if(path.endsWith('/tra-cuu/nhom-lop'))return options.groupsFailure?reply({code:'DEPENDENCY_UNAVAILABLE'},503):reply({khoaHoc:[{ma:'2023',ten:'Khóa 2023',soLop:2},{ma:'2025',ten:'Khóa 2025',soLop:1}],lopTreTienDo:options.noRoster?[]:[{ma:'RL-T',ten:'Lớp rèn luyện khoa Công nghệ thông tin',soSinhVien:18},{ma:'RL-C',ten:'Lớp rèn luyện khoa Cơ khí',soSinhVien:12}],soLopChuaCoKhoa:0,trangThaiLopTreTienDo:options.noRoster?'CHUA_CO_DU_LIEU':'CO_DU_LIEU'});
  if(path.endsWith('/tra-cuu/hoc-ky'))return options.semesterFailure?reply({code:'DEPENDENCY_UNAVAILABLE'},503):reply({items:options.twoSemesters?[semester,{ma:'0020',ten:'Học kỳ tiếp theo',namHoc:'2026-2027'}]:[semester],page:0,size:25,totalElements:options.twoSemesters?2:1});
  if(path==='/api/shcn/dot-sinh-hoat'&&r.method()==='GET'){
   if(options.denied)return reply({code:'FORBIDDEN'},403);
   if(options.scopeFailure)return reply({code:'DEPENDENCY_UNAVAILABLE'},503);
   const q=u.searchParams.get('q')??'',state=u.searchParams.get('trangThai');const items=d.tenDot.includes(q)&&(!state||state===d.trangThai)?[d]:[];
   return reply({items,page:Number(u.searchParams.get('page')??0),size:Number(u.searchParams.get('size')??25),totalElements:items.length});
  }
  if(path==='/api/shcn/dot-sinh-hoat'&&r.method()==='POST'){
   const b=r.postDataJSON();writes.push({method:r.method(),body:b});assert.equal(Object.keys(b).length,Array.isArray(b.doiTuongApDung)?7:6);assert.equal(b.maHocKy,'0010');assert(!('expectedVersion'in b));
   if(options.timeoutCreate)return route.abort('failed');
   if(options.serverValidation)return reply({code:'VALIDATION_ERROR',errors:[{field:'tenDot',code:'INVALID',message:'Invalid'}]},400);
   d={...d,...b,id:'8',version:'AAAAAAAAAAI='};return reply(d,201);
  }
  if(/^\/api\/shcn\/dot-sinh-hoat\/\d+$/.test(path)){
   if(r.method()==='GET')return reply(options.closed?{...d,trangThai:'DA_PHAT_HANH'}:d);
   const b=r.postDataJSON();writes.push({method:r.method(),body:b});assert.equal(r.method(),'PATCH');assert.equal(Object.keys(b).length,Array.isArray(b.doiTuongApDung)?8:7);assert.equal(b.expectedVersion,'AAAAAAAAAAE=');
   if(options.conflict){d={...d,version:'AAAAAAAAAAI=',tenDot:'Bản mới của chuyên viên khác'};return reply({code:'VERSION_CONFLICT'},409);}
   if(options.stateConflict){d={...d,version:'AAAAAAAAAAI=',trangThai:'CHO_DUYET'};return reply({code:'STATE_CONFLICT'},409);}
   const {expectedVersion,...input}=b;d={...d,...input,version:'AAAAAAAAAAI=',ngayCapNhat:'2026-11-21T01:00:00Z'};return reply(d);
  }
  return reply({code:'NOT_FOUND'},404);
 });
 try{await page.goto(origin+'/shcn/dot-sinh-hoat');await fn({page,writes});assert.deepEqual(errors,[]);checks.push(name);console.log(name+' PASS (browser REAL; API STUB; SQL NONE)');}
 catch(e){console.log((await page.locator('body').innerText()).slice(0,2800));throw e;}
 finally{await context.close();}
}
async function open(page){await page.getByRole('button',{name:'Mở',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt sinh hoạt thực hành',exact:true})).toBeVisible();}
async function fill(page){await page.getByLabel('Tên đợt *',{exact:true}).fill('Đợt mới thực hành');const s=page.getByRole('combobox',{name:'Học kỳ *',exact:true});await s.click();await s.press('ArrowDown');await expect(page.getByRole('option',{name:'Học kỳ thực hành',exact:true})).toBeVisible();await s.press('ArrowDown');await s.press('Enter');await expect(page.getByLabel('Ngày bắt đầu *',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Ngày kết thúc *',{exact:true})).toHaveCount(0);await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();await page.getByLabel('Từ ngày – Khóa 2023',{exact:true}).fill('2026-12-01');await page.getByLabel('Đến ngày – Khóa 2023',{exact:true}).fill('2026-12-07');await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('OFFLINE');await page.getByLabel('Tiết đầu 1',{exact:true}).fill('5');await page.getByLabel('Tiết cuối 1',{exact:true}).fill('6');await page.getByLabel('Hạn chốt lịch (giờ Việt Nam) *',{exact:true}).fill('2026-12-09T17:00');}
try{
 await run('Audience dates are disabled until selected and semester picker hides business codes',async({page,writes})=>{
  await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();
  const picker=page.getByRole('combobox',{name:'Học kỳ *',exact:true});await picker.click();const option=page.getByRole('option',{name:'Học kỳ thực hành',exact:true});await expect(option).toBeVisible();await expect(page.getByRole('option').filter({hasText:'0010'})).toHaveCount(0);await option.click();
  await expect(page.getByLabel('Ngày bắt đầu *',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Từ ngày – Khóa 2023',{exact:true})).toBeDisabled();await expect(page.getByLabel('Đến ngày – Khóa 2023',{exact:true})).toBeDisabled();
  await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();await expect(page.getByLabel('Từ ngày – Khóa 2023',{exact:true})).toBeEnabled();await expect(page.getByLabel('Từ ngày – Khóa 2023',{exact:true})).toHaveValue('');await expect(page.getByLabel('Hình thức – Khóa 2023',{exact:true})).toBeEnabled();assert.equal(writes.length,0);
  await page.screenshot({path:evidence+'/audience-controls-desktop.png',fullPage:true});
 });
 await run('Save independent cohort windows and expand delayed group into receiving classes',async({page,writes})=>{
  await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await fill(page);
  await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('OFFLINE');
  await page.getByLabel('Chọn Khóa 2025',{exact:true}).check();await page.getByLabel('Từ ngày – Khóa 2025',{exact:true}).fill('2027-03-02');await page.getByLabel('Đến ngày – Khóa 2025',{exact:true}).fill('2027-03-04');await page.getByLabel('Hình thức – Khóa 2025',{exact:true}).selectOption('ONLINE');
  await page.getByLabel('Chọn Lớp trễ tiến độ',{exact:true}).check();await page.getByLabel('Từ ngày – Lớp trễ tiến độ',{exact:true}).fill('2026-12-02');await page.getByLabel('Đến ngày – Lớp trễ tiến độ',{exact:true}).fill('2026-12-06');await page.getByLabel('Hình thức – Lớp trễ tiến độ',{exact:true}).selectOption('ONLINE');
  await page.screenshot({path:evidence+'/groups-desktop.png',fullPage:true});
  await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt mới thực hành',exact:true})).toBeVisible();
  assert.equal(writes.length,1);assert.equal(writes[0].body.khungNgayBatDau,'2026-12-01');assert.equal(writes[0].body.khungNgayKetThuc,'2027-03-04');const groups=writes[0].body.doiTuongApDung;assert.equal(groups.length,4);assert.deepEqual(groups.filter(g=>g.loaiDoiTuong==='LOP').map(g=>g.maDoiTuong),['RL-T','RL-C']);
  assert.equal(groups.find(g=>g.maDoiTuong==='2025').khungNgayBatDau,'2027-03-02');assert.equal(groups.find(g=>g.maDoiTuong==='2023').hinhThucChoPhep,'OFFLINE');
  await expect(page.getByRole('cell',{name:'Lớp RL-T',exact:true})).toBeVisible();await page.reload();await expect(page.getByRole('cell',{name:'Lớp RL-C',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'← Danh sách đợt'}).click();await expect(page.getByRole('cell',{name:'Đợt mới thực hành',exact:true})).toBeVisible();
 });
 await run('Removing last saved cohort blocks save until at least one group is selected',async({page,writes})=>{
  await open(page);await page.getByRole('button',{name:'Sửa thông tin'}).click();await page.getByLabel('Chọn Khóa 2023',{exact:true}).uncheck();await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'Hãy chọn ít nhất một khóa hoặc nhóm lớp trước khi lưu nháp.'})).toBeVisible();assert.equal(writes.length,0);
 },{withAudiences:true});
 await run('Missing current semester roster disables delayed selection without borrowing previous semester',async({page,writes})=>{
  await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await fill(page);await expect(page.getByText('Chưa có lớp trễ tiến độ trong học kỳ này.',{exact:true})).toBeVisible();
  await expect(page.getByLabel('Chọn Lớp trễ tiến độ',{exact:true})).toBeDisabled();await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('OFFLINE');await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();assert.equal(writes[0].body.doiTuongApDung.length,1);assert.equal(writes[0].body.doiTuongApDung[0].loaiDoiTuong,'KHOA_HOC');
 },{noRoster:true});
 await run('Changing semester confirms before clearing selected groups',async({page})=>{
  await open(page);await page.getByRole('button',{name:'Sửa thông tin'}).click();await expect(page.getByLabel('Chọn Khóa 2023',{exact:true})).toBeChecked();
  const choose=page.getByRole('combobox',{name:'Học kỳ *',exact:true});await choose.click();await choose.fill('');await expect(page.getByRole('option',{name:'Học kỳ tiếp theo',exact:true})).toBeVisible();
  page.once('dialog',d=>d.dismiss());await page.getByRole('option',{name:'Học kỳ tiếp theo',exact:true}).click();await expect(page.getByLabel('Chọn Khóa 2023',{exact:true})).toBeChecked();
  await choose.click();await choose.fill('');await expect(page.getByRole('option',{name:'Học kỳ tiếp theo',exact:true})).toBeVisible();page.once('dialog',d=>d.accept());await page.getByRole('option',{name:'Học kỳ tiếp theo',exact:true}).click();await expect(page.getByLabel('Chọn Khóa 2023',{exact:true})).not.toBeChecked();
 },{withAudiences:true,twoSemesters:true});
 await run('Lookup failure preserves saved audience and prevents unverified group write',async({page,writes})=>{
  await open(page);await page.getByRole('button',{name:'Sửa thông tin'}).click();await page.getByLabel('Tên đợt *',{exact:true}).fill('Nội dung đang soạn');await expect(page.getByRole('button',{name:'Tải lại nhóm lớp',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Lưu thay đổi',exact:true})).toBeDisabled();assert.equal(writes.length,0);await expect(page.getByLabel('Tên đợt *',{exact:true})).toHaveValue('Nội dung đang soạn');
 },{withAudiences:true,groupsFailure:true});
 await run('Backend validation after scrolling preserves group selection and mobile keyboard layout',async({page,writes})=>{
  await page.setViewportSize({width:360,height:800});await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await fill(page);await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('ONLINE');await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'Trường cần kiểm tra: tenDot'})).toBeVisible();await expect(page.getByLabel('Chọn Khóa 2023',{exact:true})).toBeChecked();await expect(page.getByLabel('Hình thức – Khóa 2023',{exact:true})).toHaveValue('ONLINE');assert.equal(writes.length,1);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:evidence+'/groups-mobile-error.png',fullPage:true});
 },{serverValidation:true});
 await run('Missing permission denies direct round route',async({page,writes})=>{await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(writes.length,0);},{denied:true});
}finally{await writeFile(evidence+'/results.json',JSON.stringify({browser:'Chrome REAL',api:'HTTP STUB',sql:'NONE',migration:'NOT RUN',checks,passed:checks.length},null,2));await browser.close();}
