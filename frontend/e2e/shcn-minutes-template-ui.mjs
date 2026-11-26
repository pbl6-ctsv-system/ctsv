// Browser REAL; all API requests are fixtures. No SQL, migration, seed or real write.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {createTemplate} from '../src/features/shcn/model/minutes-template.mjs';
const origin=process.env.SHCN_UI_ORIGIN??'https://127.0.0.1:5173';
const output=process.env.SHCN_UI_EVIDENCE??'./shcn-cv04-evidence';await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[];
const field=(maTruong,nhan,kieuDuLieu='VAN_BAN',batBuoc=false)=>({maTruong,nhan,kieuDuLieu,batBuoc,nguonDien:'NGUOI_NHAP',maNguonHeThong:null});
const dictionary={kieuDuLieu:['CHUOI','VAN_BAN','SO_NGUYEN','NGAY','BOOLEAN'],nguonHeThong:[{ma:'DOT_TEN',nhan:'Tên đợt',kieuDuLieu:'CHUOI'},{ma:'NGAY_HOP',nhan:'Ngày họp',kieuDuLieu:'NGAY'}],mucHeThong:[
 {maMuc:'DIEM_DANH',tieuDe:'Điểm danh',thuTuHienThi:1,loaiMuc:'DIEM_DANH',batBuoc:true,laMucHeThong:true,cauHinhTruong:[field('TONG_SO_SV','Tổng số sinh viên','SO_NGUYEN',true),field('CO_MAT','Có mặt','SO_NGUYEN',true),field('VANG_MAT','Vắng','SO_NGUYEN',true)]},
 ...[['KIEN_NGHI_SV','Kiến nghị của sinh viên'],['KIEN_NGHI_GV','Kiến nghị của giảng viên']].map(([maMuc,tieuDe],i)=>({maMuc,tieuDe,thuTuHienThi:i+2,loaiMuc:'NOI_DUNG',batBuoc:false,laMucHeThong:true,cauHinhTruong:[field('NOI_DUNG','Nội dung')]}))]};
const initialRound=()=>({id:'7',version:'AAAAAAAAAAE=',maHocKy:'2610',tenDot:'Đợt SHCN thực hành',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hanChotDangKyLich:'2026-11-29T10:00:00Z',trangThai:'NHAP',trangThaiPhanCong:'CHUA_DONG_BO',phienBanPhanCongApDung:null,trangThaiLichTongHop:'DANG_CHOT',phienBanLichTongHop:null,ngayPhatHanh:null,ngayCongBoLich:null,ngayTao:'2026-11-24T00:00:00Z',ngayCapNhat:'2026-11-24T00:00:00Z',doiTuongApDung:[{loaiDoiTuong:'KHOA_HOC',maDoiTuong:'2023',hinhThucChoPhep:'OFFLINE',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:null}]});
async function run(name,fn,options={}){
 if(process.env.SHCN_UI_CASE&&!name.includes(process.env.SHCN_UI_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:true}),page=await context.newPage();page.setDefaultTimeout(10000);const writes=[],reads=[],errors=[];let round=initialRound(),version=1,failed=false,loadFailure=!!options.loadFailure;
 if(options.closed)round.trangThai='DA_PHAT_HANH';
 let template={id:null,dotId:'7',dotVersion:round.version,daCoMau:false,duocSua:!options.readOnly&&!options.closed,tenMau:null,trangThai:null,muc:[],danhMuc:dictionary};
 if(options.existing)template={...template,...createTemplate(dictionary),id:'11',daCoMau:true,trangThai:'HOP_LE'};
 if(options.sourceBinding){const f=template.muc[1].cauHinhTruong[0];f.nguonDien='HE_THONG';f.maNguonHeThong='NGAY_HOP';f.kieuDuLieu='NGAY';}
 page.on('pageerror',e=>errors.push(e.message));
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const r=route.request(),path=new URL(r.url()).pathname;const reply=(json,status=200)=>route.fulfill({json,status,headers:{'X-SHCN-Can-Prepare':String(!options.readOnly),'Cache-Control':'private, no-store'}});
  if(r.method()==='GET')reads.push(path);
  if(path.endsWith('/dang-nhap')||path.endsWith('/lam-moi-token'))return reply({accessToken:'fixture',expiresIn:900});
  if(path.endsWith('/xac-thuc/toi'))return reply({accountId:'fixture',username:'SYNTHETIC specialist',functions:[{code:options.readOnly?'shcn.dot.read':'shcn.dot.prepare',active:true}]});
  if(path.endsWith('/quyen-giao-dien'))return reply({student:false,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(path.endsWith('/khoa/pham-vi'))return reply({code:'FORBIDDEN'},403);
  if(path==='/api/shcn/dot-sinh-hoat')return options.denied?reply({code:'FORBIDDEN'},403):reply({items:[round],page:0,size:25,totalElements:1});
  if(path==='/api/shcn/dot-sinh-hoat/7')return reply(round);
  if(path.endsWith('/tra-cuu/hoc-ky'))return reply({items:[{ma:'2610',ten:'Học kỳ 1 năm học 2026-2027',namHoc:'2026-2027'}],page:0,size:25,totalElements:1});
  if(path.endsWith('/mau-bien-ban')){
   if(r.method()==='GET')return loadFailure?reply({code:'DEPENDENCY_UNAVAILABLE'},503):reply(template);
   const body=r.postDataJSON();writes.push(body);assert.equal(r.method(),'PUT');assert(!('laMucHeThong' in body.muc[0]));
   if(options.readOnly)return reply({code:'FORBIDDEN'},403);
   if(options.conflict&&!failed){failed=true;round={...round,version:'AAAAAAAAAAI='};template={...template,...createTemplate(dictionary),id:'11',daCoMau:true,tenMau:'Bản mới của chuyên viên khác',dotVersion:round.version};version=2;return reply({code:'VERSION_CONFLICT'},409);}
   if(options.validation&&!failed){failed=true;return reply({code:'VALIDATION_ERROR',errors:[{field:'muc[1].tieuDe',code:'INVALID',message:'Kiểm tra nhãn'}]},400);}
   assert.equal(body.expectedVersion,round.version);version++;round={...round,version:version===2?'AAAAAAAAAAI=':'AAAAAAAAAAM=',ngayCapNhat:'2026-11-24T01:00:00Z'};
   template={...template,id:'11',daCoMau:true,tenMau:body.tenMau,trangThai:'HOP_LE',dotVersion:round.version,muc:body.muc.map(m=>({...m,laMucHeThong:dictionary.mucHeThong.some(s=>s.maMuc===m.maMuc)}))};
   if(options.timeout&&!failed){failed=true;return route.abort('failed');}
   return reply(template);
  }
  return reply({code:'NOT_FOUND'},404);
 });
 try{await page.goto(origin+'/shcn/dot-sinh-hoat');await fn({page,writes,reads,release:()=>{loadFailure=false;}});assert.deepEqual(errors,[]);checks.push(name);console.log(name+' PASS (Chrome REAL; API STUB; SQL NONE)');}
 catch(e){await page.screenshot({path:output+'/failed.png',fullPage:true});console.log((await page.locator('body').innerText()).slice(-4500));throw e;}
 finally{await context.close();}
}
async function detail(page){await page.getByRole('button',{name:'Mở',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt SHCN thực hành',exact:true})).toBeVisible();}
async function edit(page){await detail(page);await page.getByRole('button',{name:/^(Tạo mẫu biên bản|Sửa mẫu)$/}).click();await expect(page.getByRole('dialog')).toBeVisible();}
try{
 await run('Empty template GET is read only and has no automatic create',async({page,writes,reads})=>{await detail(page);await expect(page.getByText('Chưa có mẫu biên bản',{exact:true})).toBeVisible();assert.equal(writes.length,0);assert.equal(reads.filter(p=>p.includes('lop-theo-ky')||p.includes('sinh-vien-theo-ky')).length,0);});
 await run('Legacy source binding survives simple editor preview save and reopen',async({page,writes})=>{
  await edit(page);const d=page.getByRole('dialog');await expect(d.getByRole('button',{name:'Xóa mục Kiến nghị của sinh viên',exact:true})).toHaveCount(0);await expect(d.getByRole('button',{name:'Xóa mục Kiến nghị của giảng viên',exact:true})).toHaveCount(0);await expect(d.getByRole('button',{name:'Xóa mục Điểm danh',exact:true})).toHaveCount(0);
  await d.getByLabel('Tên mẫu *',{exact:true}).fill('Biên bản thực hành CV04');await expect(d.getByText('Cấu hình nâng cao',{exact:true})).toHaveCount(0);await expect(d.getByLabel('Kiểu trường 2.1',{exact:true})).toHaveCount(0);
  await d.getByRole('button',{name:'Xem trước',exact:true}).click();await expect(d.getByRole('heading',{name:'Xem trước mẫu — dữ liệu minh họa',exact:true})).toBeVisible();await expect(d.getByLabel('Tổng số sinh viên',{exact:true})).toHaveCount(1);await expect(d.getByLabel('Có mặt',{exact:true})).toHaveCount(1);await expect(d.getByLabel('Vắng',{exact:true})).toHaveCount(1);await d.getByLabel('Hình thức xem trước',{exact:true}).selectOption('ONLINE');await expect(d.getByLabel('Link minh chứng',{exact:true})).toBeVisible();assert.equal(writes.length,0);
  await page.screenshot({path:output+'/preview-online.png',fullPage:true});await d.getByRole('button',{name:'Quay lại chỉnh mẫu',exact:true}).click();await expect(d.getByLabel('Tên mẫu *',{exact:true})).toHaveValue('Biên bản thực hành CV04');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(page.getByText('Đã lưu mẫu biên bản.',{exact:true})).toBeVisible();assert.equal(writes.length,1);assert.equal(writes[0].muc[1].cauHinhTruong[0].maNguonHeThong,'NGAY_HOP');
  await page.getByRole('button',{name:'Sửa mẫu',exact:true}).click();await expect(page.getByLabel('Tên mẫu *',{exact:true})).toHaveValue('Biên bản thực hành CV04');await page.getByRole('button',{name:'Đóng hộp thoại',exact:true}).click();await page.reload();await page.getByRole('button',{name:'Xem mẫu',exact:true}).click();await expect(page.getByRole('heading',{name:'Biên bản thực hành CV04',exact:true})).toBeVisible();
 },{existing:true,sourceBinding:true});
 await run('Custom section required checkbox saves content requirements and close confirms dirty state',async({page,writes})=>{
  await edit(page);const d=page.getByRole('dialog');await d.getByRole('button',{name:'Thêm mục',exact:true}).click();await d.getByLabel('Tiêu đề mục 6',{exact:true}).fill('Trao đổi bổ sung');await d.getByLabel('Bắt buộc mục Trao đổi bổ sung',{exact:true}).check();
  page.once('dialog',x=>x.dismiss());await d.getByRole('button',{name:'Đóng hộp thoại',exact:true}).click();await expect(d).toBeVisible();await page.screenshot({path:output+'/editor-desktop.png',fullPage:true});await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(page.getByText('Đã lưu mẫu biên bản.',{exact:true})).toBeVisible();assert.equal(writes[0].muc[5].tieuDe,'Trao đổi bổ sung');assert.equal(writes[0].muc[5].cauHinhTruong[0].nhan,'Nội dung');assert.equal(writes[0].muc[5].cauHinhTruong[0].batBuoc,true);
 });
 await run('Version conflict keeps input until user reads and chooses server copy',async({page,writes})=>{
  await edit(page);const d=page.getByRole('dialog');await d.getByLabel('Tên mẫu *',{exact:true}).fill('Bản nhập cần giữ');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(d.getByRole('alert')).toBeVisible();await expect(d.getByLabel('Tên mẫu *',{exact:true})).toHaveValue('Bản nhập cần giữ');await expect(d.getByRole('button',{name:'Lưu mẫu',exact:true})).toBeDisabled();await d.getByRole('button',{name:'Đọc bản mới',exact:true}).click();page.once('dialog',x=>x.accept());await d.getByRole('button',{name:'Dùng bản hệ thống',exact:true}).click();await expect(d.getByLabel('Tên mẫu *',{exact:true})).toHaveValue('Bản mới của chuyên viên khác');await d.getByLabel('Tên mẫu *',{exact:true}).fill('Bản sửa sau đối chiếu');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(page.getByText('Đã lưu mẫu biên bản.',{exact:true})).toBeVisible();assert.equal(writes.length,2);assert.equal(writes[1].expectedVersion,'AAAAAAAAAAI=');
 },{conflict:true});
 await run('Backend field error is visible after scrolling and can be corrected without losing draft',async({page,writes})=>{
  await edit(page);const d=page.getByRole('dialog');await d.getByLabel('Tên mẫu *',{exact:true}).fill('Mẫu giữ khi lỗi');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(d.getByRole('alert')).toBeInViewport();await expect(d.getByLabel('Tên mẫu *',{exact:true})).toHaveValue('Mẫu giữ khi lỗi');await expect(d.getByLabel('Tiêu đề mục 2',{exact:true})).toHaveAttribute('aria-invalid','true');await d.getByLabel('Tiêu đề mục 2',{exact:true}).fill('Nội dung đã kiểm tra');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await expect(page.getByText('Đã lưu mẫu biên bản.',{exact:true})).toBeVisible();assert.equal(writes.length,2);
 },{validation:true});
 await run('Unknown PUT result is reconciled by GET without automatic retry',async({page,writes})=>{
  await edit(page);const d=page.getByRole('dialog');await d.getByLabel('Tên mẫu *',{exact:true}).fill('Mẫu đối soát');await d.getByRole('button',{name:'Lưu mẫu',exact:true}).click();await d.getByRole('button',{name:'Đọc bản mới',exact:true}).click();await expect(d.getByText('Nội dung hệ thống khớp bản đang nhập.',{exact:true})).toBeVisible();assert.equal(writes.length,1);page.once('dialog',x=>x.accept());await d.getByRole('button',{name:'Dùng bản hệ thống',exact:true}).click();await expect(d.getByRole('button',{name:'Lưu mẫu',exact:true})).toBeDisabled();
 },{timeout:true});
 await run('Read-only and closed template can be previewed but not edited',async({page,writes})=>{await detail(page);await expect(page.getByRole('button',{name:'Sửa mẫu',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Xem mẫu',exact:true}).click();await expect(page.getByRole('dialog')).toBeVisible();await expect(page.getByRole('button',{name:'Lưu mẫu',exact:true})).toHaveCount(0);assert.equal(writes.length,0);},{existing:true,readOnly:true,closed:true});
 await run('Template source failure does not appear as an empty template and supports retry',async({page,release,writes})=>{await detail(page);await expect(page.getByRole('button',{name:'Tạo mẫu biên bản',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Thử lại mẫu',exact:true})).toBeVisible();release();await page.getByRole('button',{name:'Thử lại mẫu',exact:true}).click();await expect(page.getByRole('button',{name:'Tạo mẫu biên bản',exact:true})).toBeVisible();assert.equal(writes.length,0);},{loadFailure:true});
 await run('Mobile template editor and preview fit viewport',async({page,writes})=>{await page.setViewportSize({width:360,height:800});await edit(page);const d=page.getByRole('dialog');const box=await d.boundingBox();assert(box.x>=0&&box.x+box.width<=360);assert(await d.evaluate(el=>el.scrollWidth<=el.clientWidth));await page.screenshot({path:output+'/editor-mobile.png',fullPage:true});await d.getByRole('button',{name:'Xem trước',exact:true}).click();await expect(d.getByRole('heading',{name:'Xem trước mẫu — dữ liệu minh họa',exact:true})).toBeVisible();await page.screenshot({path:output+'/preview-mobile.png',fullPage:true});assert.equal(writes.length,0);});
 await run('No SHCN permission denies direct route without template data',async({page,reads,writes})=>{await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(reads.filter(p=>p.endsWith('/mau-bien-ban')).length,0);assert.equal(writes.length,0);},{denied:true});
}finally{await writeFile(output+'/results.json',JSON.stringify({browser:'Chrome REAL',api:'HTTP STUB',sql:'NONE',migration:'NOT RUN',passed:checks.length,checks},null,2));await browser.close();}
