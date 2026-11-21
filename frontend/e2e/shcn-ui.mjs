// Browser verification with synthetic HTTP fixtures. No SQL, seeds, migrations or credentials.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {mkdir,writeFile} from 'node:fs/promises';
const origin=process.env.SHCN_UI_ORIGIN??'http://127.0.0.1:5173';
const evidence=process.env.SHCN_UI_EVIDENCE??fileURLToPath(new URL('./shcn-evidence/',import.meta.url));await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[];
const faculty={id:'1',maKhoa:'A',tenKhoa:'Khoa thực hành',duocDoc:true,duocLapNhap:true,coTheChon:true,lyDoKhongTheChon:null};
const program={id:'1',ma:'CT',ten:'Chương trình thực hành',tenNganhMoi:'Công nghệ thông tin'};
const program2={...program,id:'2',ma:'CT2',ten:'Chương trình Điện',tenNganhMoi:'Kỹ thuật điện'};
const gv={idCanBo:'1',maGiaoVien:'GV1',hoTen:'Giảng viên thực hành',maKhoaCongTac:'A',coTheChon:true,lyDoKhongTheChon:null};
const cl=n=>({id:n,maLop:'00'+n,tenLop:'Lớp thực hành '+n,maKhoa:'A',chuongTrinh:program,coTheChon:true,lyDoKhongTheChon:null});
const initial=()=>({id:'1',maKhoa:'A',namHoc:'2026-2027',phienBan:1,trangThai:'NHAP',hieuLucTu:'2026-07-31T17:00:00Z',hieuLucDen:null,soLop:2,ngayTao:'2026-08-01T00:00:00Z',ngayCapNhat:'2026-08-01T00:00:00Z',maDanhSachTruoc:null,maNguoiLap:'fixture',lyDoDieuChinh:null,version:'AAAAAAAAAAE=',cacPhanCong:['1','2'].map(n=>({id:n,maLop:cl(n).maLop,maGiaoVien:'GV1',ghiChu:null,lopHienTai:cl(n),giangVienHienTai:gv}))});
async function run(name,fn,options={}){if(process.env.SHCN_UI_CASE&&!name.includes(process.env.SHCN_UI_CASE))return;const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(12000);page.on('pageerror',e=>console.log('BROWSER_ERROR '+e.message));let draft=initial();if(options.orphan)draft.cacPhanCong.push({maLop:'099',maGiaoVien:'GV-OLD',ghiChu:'Dữ liệu lịch sử',lopHienTai:null,giangVienHienTai:null});const calls=[];
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{const request=route.request();const url=new URL(request.url()),path=url.pathname;const reply=(json,status=200)=>route.fulfill({json,status});
  if(path.endsWith('/dang-nhap')||path.endsWith('/lam-moi-token'))return reply({accessToken:'synthetic-token',expiresIn:900});
  if(path.endsWith('/xac-thuc/toi'))return reply({accountId:'fixture',username:'fixture',functions:options.withShcnFunction?[{code:'shcn.phanCongKhoa.read',active:true}]:[]});
  if(path.endsWith('/quyen-giao-dien'))return reply({student:false,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(path.endsWith('/pham-vi'))return options.scopeError?reply({code:'DEPENDENCY_UNAVAILABLE'},503):options.denied?reply({code:'FORBIDDEN'},403):reply({khoa:[{...faculty,duocLapNhap:!options.readOnly}]});
  if(path.endsWith('/chuong-trinh')){
   if(options.lookupDenied)return reply({code:'FORBIDDEN'},403);
   const p=Number(url.searchParams.get('page')||0),q=(url.searchParams.get('q')||'').toLowerCase();
   const programs=options.pagedPrograms?Array.from({length:26},(_,i)=>({...program,id:String(i+1),ma:'CT'+(i+1)})):[program,program2];
   const items=programs.filter(v=>[v.ten,v.tenNganhMoi,v.ma].join(' ').toLowerCase().includes(q));
   return reply({items:items.slice(p*25,p*25+25),page:p,size:25,totalElements:items.length});
  }
  if(path.endsWith('/giang-vien')){assert.equal(url.searchParams.get('maKhoa'),'A');assert(!url.searchParams.has('chuongTrinhId'));return reply({items:[gv,{...gv,idCanBo:'9',maGiaoVien:'GV9',hoTen:'GV không thể chọn',coTheChon:false,lyDoKhongTheChon:'Ngoài phạm vi phân công'}],page:0,size:25,totalElements:2});}
  if(path.endsWith('/lop')){
   assert.equal(url.searchParams.get('maKhoa'),'A');const pid=url.searchParams.get('chuongTrinhId');assert(pid,'Class request needs selected program ID');
   const p=Number(url.searchParams.get('page')||0),q=url.searchParams.get('q')||'';
   if(options.lookupError&&q==='error')return reply({code:'DEPENDENCY_UNAVAILABLE'},503);
   if(options.stale&&q==='slow')await new Promise(r=>setTimeout(r,800));
   let items=pid==='2'?[{...cl('4'),chuongTrinh:program2}]:options.pagedClasses?Array.from({length:26},(_,i)=>cl(String(i+1))):[cl('1'),cl('2'),cl('3')];
   items=items.filter(v=>[v.maLop,v.tenLop].join(' ').includes(q));return reply({items:items.slice(p*25,p*25+25),page:p,size:25,totalElements:items.length});
  }
  if(path.endsWith('/phan-cong')&&request.method()==='GET'){const source=options.inherit&&url.searchParams.get('namHoc')==='2025-2026';return reply({items:draft.trangThai==='DA_HUY'?[]:[source?{...draft,namHoc:'2025-2026',phienBan:4,trangThai:'DA_GUI_CHUYEN_VIEN'}:draft],page:0,size:25,totalElements:draft.trangThai==='DA_HUY'?0:1,phienBanLonNhat:draft.phienBan});}
  if(path.endsWith('/phan-cong')&&request.method()==='POST'){const body=request.postDataJSON();calls.push(body);assert(!('cacPhanCong'in body));draft={...draft,id:'2',phienBan:2,trangThai:'NHAP',maDanhSachTruoc:body.maDanhSachTruoc,soLop:body.maDanhSachTruoc?draft.cacPhanCong.length:0,cacPhanCong:body.maDanhSachTruoc?draft.cacPhanCong:[]};return reply(draft,201);}
  if(/\/phan-cong\/\d+$/.test(path)){
   if(request.method()==='GET')return draft.trangThai==='DA_HUY'?reply({code:'NOT_FOUND'},404):reply(draft);
   if(request.method()==='DELETE'){const body=request.postDataJSON();calls.push(body);if(options.deleteConflict)return reply({code:'VERSION_CONFLICT'},409);assert.equal(body.expectedVersion,draft.version);draft={...draft,trangThai:'DA_HUY'};return route.fulfill({status:204});}
   const body=request.postDataJSON();calls.push(body);assert(!request.headers()['if-match']);
   if(options.conflict)return reply({code:'VERSION_CONFLICT'},409);
   draft={...draft,...body,version:'AAAAAAAAAAI=',soLop:body.cacPhanCong.length,cacPhanCong:body.cacPhanCong.map(r=>({...r,lopHienTai:r.maLop==='099'?null:r.maLop==='004'?{...cl('4'),chuongTrinh:program2}:cl(r.maLop.slice(2)),giangVienHienTai:r.maLop==='099'?null:gv}))};return reply(draft);
  }
  return reply({code:'NOT_FOUND'},404);
 });
 try{await page.goto(origin+'/shcn/phan-cong-gvcn?danhSachId=1');await fn({page,calls});checks.push(name);console.log(name+' PASS (HTTP STUB)');}catch(e){console.log((await page.locator('body').innerText()).slice(0,2500));throw e;}finally{await context.close();}}
async function selectProgram(page,name='Công nghệ thông tin · CT — Chương trình thực hành'){const input=page.getByRole('combobox',{name:'Ngành',exact:true});await input.click();await page.getByRole('option',{name,exact:true}).click();}
try{
 await run('Filter keeps hidden rows; save/reload and desktop/mobile layout',async({page,calls})=>{
  await expect(page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 1'})).toBeVisible();
  await selectProgram(page);await page.getByLabel('Tìm lớp',{exact:true}).fill('001');await page.getByLabel('Ghi chú 001',{exact:true}).fill('Đã điều chỉnh');
  await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();
  assert.equal(calls.length,1);assert.equal(calls[0].cacPhanCong.length,2);assert.equal(calls[0].cacPhanCong[1].maLop,'002');
  await page.getByLabel('Tìm lớp',{exact:true}).fill('');await expect(page.getByLabel('Ghi chú 002',{exact:true})).toBeVisible();
  await page.screenshot({path:evidence+'/desktop.png',fullPage:true});await page.setViewportSize({width:360,height:800});await page.screenshot({path:evidence+'/mobile.png',fullPage:true});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.reload();await selectProgram(page);await expect(page.getByLabel('Ghi chú 001',{exact:true})).toHaveValue('Đã điều chỉnh');
 });
 await run('Conflict retains edit and requires explicit server reconciliation',async({page,calls})=>{
  await selectProgram(page);await page.getByLabel('Ghi chú 001',{exact:true}).fill('Sửa local');await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();await expect(page.getByRole('button',{name:'Dùng bản mới để chỉnh lại'})).toBeVisible();assert.equal(calls.length,1);await expect(page.getByLabel('Ghi chú 001',{exact:true})).toHaveValue('Sửa local');
 },{conflict:true});
 await run('Read-only account has no write controls',async({page})=>{await expect(page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 1'})).toBeVisible();assert.equal(await page.getByRole('button',{name:'Lưu nháp',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Thêm phân công',exact:true}).count(),0);},{readOnly:true});
 await run('Forbidden direct URL exposes no assignment data',async({page})=>{await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(await page.getByText('Lớp thực hành 1',{exact:true}).count(),0);},{denied:true});
 await run('Add class and lecturer preserves existing working rows',async({page,calls})=>{await selectProgram(page);await page.getByRole('combobox',{name:'GVCN lớp 003',exact:true}).click();await page.getByRole('option',{name:'Giảng viên thực hành · GV1',exact:true}).click();await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();assert.equal(calls[0].cacPhanCong.length,3);});
 await run('Create empty next version through real UI actions',async({page,calls})=>{await page.getByRole('button',{name:'← Danh sách phân công'}).click();await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();await page.getByLabel('Bắt đầu đề nghị hiệu lực (giờ Việt Nam)').fill('2026-08-01T00:00');await page.getByRole('dialog').getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();await expect(page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 2'})).toBeVisible();assert.equal(calls[0].phienBanDuKien,2);assert.equal(calls[0].maDanhSachTruoc,null);});
 await run('Scope dependency error stays distinct from forbidden',async({page})=>{await expect(page.getByText('Chưa kiểm tra được quyền SHCN. Vui lòng thử lại sau.',{exact:true})).toBeVisible();assert.equal(await page.getByRole('button',{name:'Lưu nháp',exact:true}).count(),0);},{scopeError:true});
 await run('Home reports SHCN outage instead of silently hiding a granted function',async({page})=>{await page.getByRole('link',{name:'Trang chủ',exact:true}).click();await expect(page.getByText('Chưa kiểm tra được quyền SHCN. Vui lòng thử lại sau.',{exact:true})).toBeVisible();assert.equal(await page.getByRole('link',{name:'Phân công GVCN',exact:true}).count(),0);},{scopeError:true,withShcnFunction:true});
 await run('Create assignment inherits previous year, not current year, and preserves Save draft',async({page,calls})=>{
  await page.getByRole('button',{name:'← Danh sách phân công'}).click();
  await expect(page.getByText('Phiên bản được đánh số riêng theo khoa và năm học.',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();
  await page.getByRole('combobox',{name:/Cách tạo/}).selectOption('inherit');
  const year=page.getByLabel('Năm học trước',{exact:true});await expect(year).toHaveValue('2025-2026');
  await year.fill('2026-2027');await year.press('Enter');await page.getByRole('button',{name:'Tìm phân công năm trước'}).click();
  await expect(page.getByText('Hãy chọn phân công của năm trước, không phải năm đang tạo.',{exact:true})).toBeVisible();
  await year.fill('2025-2026');await year.press('Enter');await page.getByRole('button',{name:'Tìm phân công năm trước'}).click();
  await page.getByRole('radio').check();await page.getByLabel('Bắt đầu đề nghị hiệu lực (giờ Việt Nam)').fill('2026-08-01T00:00');
  await expect(page.getByLabel('Lý do điều chỉnh/kế thừa')).not.toHaveAttribute('required');
  await expect(page.getByLabel('Lý do điều chỉnh/kế thừa')).toHaveValue('');
  await page.getByRole('dialog').getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 2'})).toBeVisible();
  assert.equal(calls[0].lyDoDieuChinh,null);assert.equal(calls[0].namHoc,'2026-2027');assert.equal(calls[0].maDanhSachTruoc,'1');assert.equal(calls[0].phienBanDuKien,2);
  await expect(page.getByRole('button',{name:'Lưu nháp',exact:true})).toBeVisible();
 },{inherit:true});
 await run('Assigned faculty is fixed; year suggestions rank matches; create opens without filter year; controls align',async({page,calls})=>{
  await page.getByRole('button',{name:'← Danh sách phân công'}).click();
  await expect(page.getByLabel('Khoa',{exact:true})).toHaveAttribute('readonly','');
  const year=page.getByRole('combobox',{name:'Năm học',exact:true});await year.fill('');
  await expect(page.getByRole('button',{name:'Tạo phân công GVCN',exact:true})).toBeEnabled();
  await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();
  const dialog=page.getByRole('dialog');const target=dialog.getByRole('combobox',{name:'Năm học',exact:true});
  await expect(target).toHaveValue('');await target.fill('2025-2026');
  await expect(dialog.getByRole('option').first()).toHaveText('2025-2026');
  await dialog.getByRole('option',{name:'2025-2026',exact:true}).click();
  await expect(target).toHaveValue('2025-2026');
  await target.fill('Năm riêng');await expect(target).toHaveValue('Năm riêng');
  assert.equal(calls.length,0);await target.press('Escape');await dialog.getByRole('button',{name:'Đóng hộp thoại'}).click();
  await year.fill('2026-2027');await year.press('Escape');
  const inputBox=await year.boundingBox(),buttonBox=await page.getByRole('button',{name:'Xem danh sách',exact:true}).boundingBox();
  assert(Math.abs(inputBox.y+inputBox.height-buttonBox.y-buttonBox.height)<2,'Filter controls must align at bottom');
  await page.screenshot({path:evidence+'/filters-desktop.png',fullPage:true});
  await page.setViewportSize({width:360,height:800});await page.screenshot({path:evidence+'/filters-mobile.png',fullPage:true});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 });
 await run('Delete draft confirms, removes row and never reuses hidden version',async({page,calls})=>{
  await page.getByRole('button',{name:'← Danh sách phân công'}).click();page.on('dialog',dialog=>dialog.accept());
  await page.getByRole('button',{name:'Xóa',exact:true}).click();await expect(page.getByText('Đã xóa bản nháp.',{exact:true})).toBeVisible();
  await expect(page.getByText('Chưa có danh sách phân công cho khoa và năm học này.',{exact:true})).toBeVisible();assert.equal(calls.length,1);assert.deepEqual(Object.keys(calls[0]),['expectedVersion']);
  await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();await page.getByLabel('Bắt đầu đề nghị hiệu lực (giờ Việt Nam)').fill('2026-08-01T00:00');await page.getByRole('dialog').getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();await expect(page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 2'})).toBeVisible();assert.equal(calls[1].phienBanDuKien,2);
 });
 await run('Deletion conflict keeps row and never retries automatically',async({page,calls})=>{
  await page.getByRole('button',{name:'← Danh sách phân công'}).click();page.on('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Xóa',exact:true}).click();await expect(page.getByRole('button',{name:'Xóa',exact:true})).toBeEnabled();assert.equal(calls.length,1);await expect(page.getByRole('button',{name:'Mở',exact:true})).toBeVisible();
 },{deleteConflict:true});
 await run('Time inputs align in creation and detail while read-only cannot delete',async({page})=>{
  const bounds=async()=>{const a=await page.getByLabel('Bắt đầu đề nghị hiệu lực (giờ Việt Nam)').boundingBox(),b=await page.getByLabel('Kết thúc đề nghị hiệu lực (không bao gồm)').boundingBox();assert(Math.abs(a.y-b.y)<2);};
  await page.getByRole('heading',{name:'Phân công GVCN — Phiên bản 1'}).waitFor();await bounds();await page.screenshot({path:evidence+'/aligned-editor.png',fullPage:true});
  await page.getByRole('button',{name:'← Danh sách phân công'}).click();await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();await bounds();await page.screenshot({path:evidence+'/aligned-create.png',fullPage:true});
 });

 await run('Switch industries and save all assignments, not just visible program; remove is explicit',async({page,calls})=>{
  await expect(page.getByText('Chọn ngành để xem các lớp và phân công GVCN.',{exact:true})).toBeVisible();
  await selectProgram(page);await page.getByRole('combobox',{name:'GVCN lớp 003',exact:true}).click();
  await expect(page.getByRole('option',{name:/GV không thể chọn/})).toHaveAttribute('aria-disabled','true');
  await page.getByRole('combobox',{name:'GVCN lớp 003',exact:true}).press('ArrowDown');await page.getByRole('combobox',{name:'GVCN lớp 003',exact:true}).press('Enter');
  await selectProgram(page,'Kỹ thuật điện · CT2 — Chương trình Điện');
  await page.getByRole('combobox',{name:'GVCN lớp 004',exact:true}).click();await page.getByRole('option',{name:'Giảng viên thực hành · GV1',exact:true}).click();
  await page.getByLabel('Ghi chú 004',{exact:true}).fill('Phân công ngành Điện');
  await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();
  assert.deepEqual(calls[0].cacPhanCong.map(r=>r.maLop),['001','002','003','004']);
  await page.getByRole('button',{name:'Xem toàn bộ phân công'}).click();await expect(page.getByRole('region',{name:'Toàn bộ phân công'}).getByText('003',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Thu gọn phân công'}).click();
  page.once('dialog',d=>d.dismiss());await page.getByRole('button',{name:'Bỏ phân công',exact:true}).click();await expect(page.getByLabel('Ghi chú 004',{exact:true})).toHaveValue('Phân công ngành Điện');
  page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'Bỏ phân công',exact:true}).click();await expect(page.getByRole('combobox',{name:'GVCN lớp 004',exact:true})).toHaveValue('');
  await expect(page.getByLabel('Ghi chú 004',{exact:true})).toBeDisabled();await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();assert.equal(calls[1].cacPhanCong.length,3);
 });
 await run('Class server pagination preserves hidden edits and unassigned catalogue rows are not submitted',async({page,calls})=>{
  await selectProgram(page);await page.getByLabel('Ghi chú 001',{exact:true}).fill('Giữ khi chuyển trang');
  await page.getByRole('button',{name:'Sau',exact:true}).click();await page.getByRole('combobox',{name:'GVCN lớp 0026',exact:true}).click();await page.getByRole('option',{name:'Giảng viên thực hành · GV1',exact:true}).click();
  await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();
  assert.equal(calls[0].cacPhanCong.length,3);assert.equal(calls[0].cacPhanCong[0].ghiChu,'Giữ khi chuyển trang');assert.equal(calls[0].cacPhanCong[2].maLop,'0026');
  await page.getByRole('button',{name:'Trước',exact:true}).click();await expect(page.getByLabel('Ghi chú 001',{exact:true})).toHaveValue('Giữ khi chuyển trang');
 },{pagedClasses:true});
 await run('Program paging preserves distinct IDs even when names match',async({page})=>{
  await page.getByRole('combobox',{name:'Ngành',exact:true}).click();await expect(page.getByRole('option')).toHaveCount(25);
  await page.getByRole('button',{name:'Sau',exact:true}).click();await page.getByRole('option',{name:'Công nghệ thông tin · CT26 — Chương trình thực hành',exact:true}).click();
  await expect(page.getByRole('combobox',{name:'Ngành',exact:true})).toHaveValue('Công nghệ thông tin · CT26 — Chương trình thực hành');
  await expect(page.getByLabel('Ghi chú 003',{exact:true})).toBeDisabled();
 },{pagedPrograms:true});
 await run('Historic unmapped assignments remain in review and full save',async({page,calls})=>{
  await page.getByRole('button',{name:'Xem toàn bộ phân công'}).click();await expect(page.getByText('Kiểm tra lại tham chiếu lớp 099.',{exact:true})).toBeVisible();
  await expect(page.getByText('GV-OLD',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Thu gọn phân công'}).click();
  await selectProgram(page);await page.getByLabel('Ghi chú 001',{exact:true}).fill('Đối chiếu');await page.getByRole('button',{name:'Lưu nháp',exact:true}).click();
  await expect(page.getByText('Đã lưu nháp.',{exact:true})).toBeVisible();assert(calls[0].cacPhanCong.some(r=>r.maLop==='099'&&r.maGiaoVien==='GV-OLD'));
 },{orphan:true});
 await run('Empty lookup and dependency error retain working data; older response cannot replace latest query',async({page})=>{
  await selectProgram(page);const search=page.getByLabel('Tìm lớp',{exact:true});await search.fill('slow');await page.waitForTimeout(250);await search.fill('001');
  await expect(page.getByLabel('Ghi chú 001',{exact:true})).toBeVisible();await page.waitForTimeout(900);await expect(page.getByLabel('Ghi chú 001',{exact:true})).toBeVisible();
  await search.fill('none');await expect(page.getByText('Không có lớp phù hợp. Các phân công đã soạn vẫn được giữ.',{exact:true})).toBeVisible();
  await search.fill('error');await expect(page.getByRole('button',{name:'Tải lại lớp'})).toBeVisible();await page.getByRole('button',{name:'Xem toàn bộ phân công'}).click();await expect(page.getByText('001',{exact:true})).toBeVisible();
 },{lookupError:true,stale:true});
 await run('Lookup permission revocation closes editor and clears restricted data',async({page})=>{
  await page.getByRole('combobox',{name:'Ngành',exact:true}).click();await expect(page.getByText('Không còn quyền truy cập. Hãy quay về danh sách để kiểm tra phạm vi.',{exact:true})).toBeVisible();
  assert.equal(await page.getByRole('combobox',{name:'Ngành',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Lưu nháp',exact:true}).count(),0);
 },{lookupDenied:true});
}finally{await browser.close();await writeFile(evidence+'/browser.json',JSON.stringify({mode:'Synthetic HTTP fixtures; browser real; no DB/migration',checks},null,2));}
