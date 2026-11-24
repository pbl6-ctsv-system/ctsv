// Real Chrome UI + HTTP fixtures. No SQL, Flyway, seed or real write request.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
const origin=process.env.SHCN_UI_ORIGIN??'http://127.0.0.1:5187';
const evidence=process.env.SHCN_UI_EVIDENCE??'./shcn-cv03-evidence';await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[];
const semester={ma:'0010',ten:'Học kỳ thực hành',namHoc:'2026-2027'};
const initial=()=>({id:'7',version:'AAAAAAAAAAE=',maHocKy:'0010',tenDot:'Đợt sinh hoạt thực hành',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hanChotDangKyLich:'2026-11-28T10:00:00Z',trangThai:'NHAP',trangThaiPhanCong:'CHUA_DONG_BO',phienBanPhanCongApDung:null,trangThaiLichTongHop:'DANG_CHOT',phienBanLichTongHop:null,ngayPhatHanh:null,ngayCongBoLich:null,ngayTao:'2026-11-21T00:00:00Z',ngayCapNhat:'2026-11-21T00:00:00Z'});
async function run(name,fn,options={}){
 if(process.env.SHCN_UI_CASE&&!name.includes(process.env.SHCN_UI_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:true});const page=await context.newPage();page.setDefaultTimeout(12000);const errors=[];page.on('pageerror',e=>errors.push(e.message));let d={...initial(),doiTuongApDung:options.withAudiences?[{loaiDoiTuong:'KHOA_HOC',maDoiTuong:'2023',hinhThucChoPhep:'OFFLINE',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:null}]:[]};const writes=[],reads=[];let classFailureActive=!!options.classFailure;
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const r=route.request(),u=new URL(r.url()),path=u.pathname;reads.push(path+u.search);
  const reply=(json,status=200)=>route.fulfill({json,status,headers:{'X-SHCN-Can-Prepare':String(!options.readOnly),'Cache-Control':'private, no-store'}});
  if(path.endsWith('/dang-nhap')||path.endsWith('/lam-moi-token'))return reply({accessToken:'synthetic-token',expiresIn:900});
  if(path.endsWith('/xac-thuc/toi'))return reply({accountId:'fixture',username:'SYNTHETIC specialist',functions:[{code:options.readOnly?'shcn.dot.read':'shcn.dot.prepare',active:true},{code:'shcn.reference.read',active:true}]});
  if(path.endsWith('/quyen-giao-dien'))return reply({student:false,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(path.endsWith('/pham-vi'))return reply({code:'FORBIDDEN'},403);
  if(path.endsWith('/tra-cuu/nhom-lop')&&classFailureActive)return reply({code:'DEPENDENCY_UNAVAILABLE'},503);
  if(path.endsWith('/tra-cuu/nhom-lop'))return reply({khoaHoc:options.noAcademic?[]:[{ma:'2023',ten:'Khóa 2023',soLop:26},{ma:'2024',ten:'Khóa 2024',soLop:1},{ma:'2025',ten:'Khóa 2025',soLop:1}],lopTreTienDo:options.noRoster?[]:[{ma:'RL-T',ten:'Lớp RenLuyen CNTT',soSinhVien:1}],soLopChuaCoKhoa:0,trangThaiLopTreTienDo:options.noRoster?'CHUA_CO_DU_LIEU':'CO_DU_LIEU',trangThaiDuLieuHocVu:options.noAcademic?'CHUA_CO_DU_LIEU':'CO_DU_LIEU',trangThaiPhanNhom:'CAN_XAC_MINH',canhBao:[{ma:'CHUA_XAC_MINH_DO_PHU_PHAN_LOP',thongDiep:'Danh sách phân lớp của kỳ này chưa được xác minh đầy đủ.'}]});
  if(path.endsWith('/tra-cuu/lop-theo-ky')){
   if(classFailureActive)return reply({code:'DEPENDENCY_UNAVAILABLE'},503);
   assert.equal(u.searchParams.get('maHocKy'),'0010');
   const ren=u.searchParams.get('loaiDoiTuong')==='LOP';if(ren)assert.equal(u.searchParams.get('maDoiTuong'),'RL-T');else assert.equal(u.searchParams.get('maDoiTuong'),'2023');
   let all=ren?[{ma:'RL-T',ten:'Lớp RenLuyen CNTT',loaiLop:'TRE_TIEN_DO',maKhoaHoc:null,tenKhoaHoc:null,soSinhVien:1,soSinhVienCanXacMinh:1}]:Array.from({length:26},(_,i)=>({ma:'L'+(i+1),ten:'Lớp thường '+(i+1),loaiLop:'THUONG',maKhoaHoc:'2023',tenKhoaHoc:'Khóa 2023',soSinhVien:1,soSinhVienCanXacMinh:1}));
   const q=u.searchParams.get('q')??'',p=Number(u.searchParams.get('page')??0);all=all.filter(x=>x.ten.includes(q)||x.ma.includes(q));
   return reply({items:all.slice(p*25,(p+1)*25),page:p,size:25,totalElements:all.length});
  }
  if(path.endsWith('/tra-cuu/sinh-vien-theo-ky')){
   const ma=u.searchParams.get('maLop');assert.equal(u.searchParams.get('maHocKy'),'0010');
   const ren=ma==='RL-T';return reply({items:[{id:ren?'2':'1',maSinhVien:ren?'SV-RL':'SV-A',hoTen:ren?'SV trễ tiến độ minh họa':'SV thường minh họa',maLop:ma,tenLop:ren?'Lớp RenLuyen CNTT':'Lớp thường 1',loaiLop:ren?'TRE_TIEN_DO':'THUONG',canCu:ren?'PHAN_LOP_REN_LUYEN_DUNG_KY':'HOC_VU_DUNG_KY',canCuLop:ren?'LOP_NHAN_DUNG_KY':'LOP_HIEN_TAI_NGUON',trangThaiXacMinh:'CAN_XAC_MINH',duCanCuNguon:false}],page:0,size:25,totalElements:1});
  }
  if(path==='/api/shcn/dot-sinh-hoat/7/doi-tuong')return reply({dotId:'7',dotVersion:options.stalePreview?'AAAAAAAAAAI=':d.version,items:d.doiTuongApDung,tongHop:{soBoChon:1,tongSoLop:1,soLopThuong:1,soLopTreTienDo:0},lopApDung:{items:[{maLop:'L1',tenLop:'Lớp thường 1',loaiLop:'THUONG',tenKhoaHoc:'Khóa 2023',maKhoa:null,tenKhoa:null,maChuongTrinh:null,tenChuongTrinh:null,tuNgay:'2026-12-01',denNgay:'2026-12-07',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hinhThucChoPhep:'OFFLINE',boChonNguon:[{loaiDoiTuong:'KHOA_HOC',maDoiTuong:'2023'}],soSinhVien:1,soSinhVienCanXacMinh:1}],page:0,size:25,totalElements:1},trangThaiPhanNhom:'CAN_XAC_MINH',canhBao:[{ma:'CHUA_XAC_MINH_DO_PHU_PHAN_LOP',thongDiep:'Danh sách phân lớp của kỳ này chưa được xác minh đầy đủ.'}]});
  if(path.endsWith('/mau-bien-ban'))return reply({id:null,dotId:d.id,dotVersion:d.version,daCoMau:false,duocSua:false,tenMau:null,trangThai:null,muc:[],danhMuc:{kieuDuLieu:[],nguonHeThong:[],mucHeThong:[]}});
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
 try{await page.goto(origin+'/shcn/dot-sinh-hoat');await fn({page,writes,reads,releaseClassFailure:()=>{classFailureActive=false;}});assert.deepEqual(errors,[]);checks.push(name);console.log(name+' PASS (browser REAL; API STUB; SQL NONE)');}
 catch(e){console.log((await page.locator('body').innerText()).slice(0,2800));throw e;}
 finally{await context.close();}
}

async function start(page,expectGroups=true){await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await page.getByRole('combobox',{name:'Học kỳ *',exact:true}).click();await page.getByRole('option',{name:'Học kỳ thực hành',exact:true}).click();if(expectGroups)await expect(page.getByLabel('Chọn Lớp trễ tiến độ',{exact:true})).toBeVisible();}
try{
 await run('Compact audience table has only Offline Online and makes no class or student lookup',async({page,writes,reads})=>{
  await start(page);const section=page.getByRole('region',{name:'Đối tượng, thời gian & hình thức áp dụng'});
  await expect(section.getByRole('columnheader')).toHaveCount(4);await expect(section.getByRole('button',{name:/Xem lớp/})).toHaveCount(0);
  await expect(section.getByText('Danh sách cần đối chiếu',{exact:true})).toHaveCount(0);
  await page.getByLabel('Chọn Khóa 2023',{exact:true}).check();
  const options=await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).locator('option').allTextContents();assert.deepEqual(options,['Chọn…','Offline','Online']);
  assert.equal(reads.filter(p=>p.includes('lop-theo-ky')||p.includes('sinh-vien-theo-ky')).length,0);assert.equal(writes.length,0);
 });
 await run('Copy one complete configuration to cohort and delayed group then edit independently',async({page,writes,reads})=>{
  await start(page);
  for(const name of ['Khóa 2023','Khóa 2024','Khóa 2025','Lớp trễ tiến độ'])await page.getByLabel('Chọn '+name,{exact:true}).check();
  const copy=page.locator('details').filter({has:page.locator('summary').filter({hasText:'Sao chép cấu hình'})});await copy.locator('summary').click();
  await expect(page.getByRole('button',{name:'Áp dụng cấu hình',exact:true})).toBeDisabled();
  await expect(page.getByLabel('Sao chép từ',{exact:true}).locator('option')).toHaveCount(1);
  await page.getByLabel('Từ ngày – Khóa 2023',{exact:true}).fill('2026-12-01');await page.getByLabel('Đến ngày – Khóa 2023',{exact:true}).fill('2026-12-07');await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('OFFLINE');
  await page.getByLabel('Sao chép từ',{exact:true}).selectOption('KHOA_HOC:2023');
  for(const name of ['Khóa 2024','Lớp trễ tiến độ'])await page.getByLabel('Sao chép đến '+name,{exact:true}).check();
  await page.getByRole('button',{name:'Áp dụng cấu hình',exact:true}).click();
  for(const name of ['Khóa 2024','Lớp trễ tiến độ']){await expect(page.getByLabel('Từ ngày – '+name,{exact:true})).toHaveValue('2026-12-01');await expect(page.getByLabel('Đến ngày – '+name,{exact:true})).toHaveValue('2026-12-07');await expect(page.getByLabel('Hình thức – '+name,{exact:true})).toHaveValue('OFFLINE');}
  await expect(page.getByLabel('Từ ngày – Khóa 2025',{exact:true})).toHaveValue('');
  await page.getByLabel('Từ ngày – Khóa 2024',{exact:true}).fill('2026-12-03');await expect(page.getByLabel('Từ ngày – Khóa 2023',{exact:true})).toHaveValue('2026-12-01');
  await page.getByLabel('Chọn Khóa 2025',{exact:true}).uncheck();
  await page.getByLabel('Tên đợt *',{exact:true}).fill('Đợt sao chép thực hành');await page.getByLabel('Tiết đầu 1',{exact:true}).fill('11');await page.getByLabel('Tiết cuối 1',{exact:true}).fill('12');await page.getByLabel('Hạn chốt lịch (giờ Việt Nam) *',{exact:true}).fill('2026-11-29T17:00');
  await page.screenshot({path:evidence+'/copy-desktop.png',fullPage:true});
  await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt sao chép thực hành',exact:true})).toBeVisible();
  assert.equal(writes.length,1);const groups=writes[0].body.doiTuongApDung;assert.equal(groups.length,3);assert.equal(groups.find(g=>g.maDoiTuong==='2024').khungNgayBatDau,'2026-12-03');assert.equal(groups.find(g=>g.loaiDoiTuong==='LOP').hinhThucChoPhep,'OFFLINE');
  await page.reload();await expect(page.getByRole('cell',{name:'Offline',exact:true})).toHaveCount(3);
  assert.equal(reads.filter(p=>p.includes('lop-theo-ky')||p.includes('sinh-vien-theo-ky')).length,0);
 });
 await run('Group catalog retry preserves draft inputs',async({page,writes,releaseClassFailure})=>{
  await start(page,false);await page.getByLabel('Tên đợt *',{exact:true}).fill('Nháp cần giữ');await expect(page.getByRole('button',{name:'Tải lại nhóm lớp',exact:true})).toBeVisible();releaseClassFailure();await page.getByRole('button',{name:'Tải lại nhóm lớp',exact:true}).click();
  await expect(page.getByLabel('Chọn Khóa 2023',{exact:true})).toBeVisible();await expect(page.getByLabel('Tên đợt *',{exact:true})).toHaveValue('Nháp cần giữ');assert.equal(writes.length,0);
 },{classFailure:true});
 await run('Mobile copy controls fit and missing roster cannot be selected',async({page,writes})=>{
  await page.setViewportSize({width:360,height:800});await start(page);await expect(page.getByLabel('Chọn Lớp trễ tiến độ',{exact:true})).toBeDisabled();
  await expect(page.getByText('Chưa có lớp trễ tiến độ trong học kỳ này.',{exact:true})).toBeVisible();
  for(const name of ['Khóa 2023','Khóa 2024'])await page.getByLabel('Chọn '+name,{exact:true}).check();
  await page.getByLabel('Từ ngày – Khóa 2023',{exact:true}).fill('2026-12-01');await page.getByLabel('Đến ngày – Khóa 2023',{exact:true}).fill('2026-12-07');await page.getByLabel('Hình thức – Khóa 2023',{exact:true}).selectOption('ONLINE');
  await page.getByText('Sao chép cấu hình',{exact:true}).click();await page.getByLabel('Sao chép từ',{exact:true}).selectOption('KHOA_HOC:2023');await page.getByLabel('Sao chép đến Khóa 2024',{exact:true}).check();await page.getByRole('button',{name:'Áp dụng cấu hình',exact:true}).click();await expect(page.getByLabel('Hình thức – Khóa 2024',{exact:true})).toHaveValue('ONLINE');
  const bounds=await page.getByRole('region',{name:'Đối tượng, thời gian & hình thức áp dụng'}).evaluate(el=>({width:el.scrollWidth,client:el.clientWidth}));assert(bounds.width<=bounds.client);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:evidence+'/copy-mobile.png',fullPage:true});assert.equal(writes.length,0);
 },{noRoster:true});
 await run('Saved preview is lazy, read-only and version checked',async({page,writes,reads})=>{
  await page.getByRole('button',{name:'Mở',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt sinh hoạt thực hành',exact:true})).toBeVisible();assert.equal(reads.filter(p=>p.endsWith('/doi-tuong')).length,0);
  await page.getByRole('button',{name:'Xem các lớp áp dụng',exact:true}).click();await expect(page.getByRole('cell',{name:'Lớp thường 1',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Sửa thông tin',exact:true})).toHaveCount(0);
  await page.screenshot({path:evidence+'/cv03-desktop.png',fullPage:true});assert.equal(writes.length,0);
 },{readOnly:true,withAudiences:true});
 await run('Saved preview refuses to mix a newer root version with the current detail',async({page,writes})=>{
  await page.getByRole('button',{name:'Mở',exact:true}).click();await page.getByRole('button',{name:'Xem các lớp áp dụng',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'Đợt đã thay đổi'})).toBeVisible();await expect(page.getByRole('cell',{name:'Lớp thường 1',exact:true})).toHaveCount(0);assert.equal(writes.length,0);
 },{withAudiences:true,stalePreview:true});
 await run('Denied role cannot open the specialist round route',async({page,writes})=>{await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(writes.length,0);},{denied:true});
}finally{await writeFile(evidence+'/results.json',JSON.stringify({browser:'Chrome REAL',api:'HTTP STUB',sql:'NONE',migration:'NOT RUN',checks,passed:checks.length},null,2));await browser.close();}
