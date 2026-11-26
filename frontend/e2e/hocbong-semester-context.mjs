// HB-T024-006: browser with controlled HTTP; no SQL, screenshots or evidence files.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173',path='/hoc-bong/kkht/dot-xet',api='/api/ctsv/hoc-bong';
const term=(id,code,order=1)=>({sourceSemesterId:id,code,name:'Học kỳ '+order,academicYear:'2025-2026',semesterOrder:order,startDate:'2025-08-01',endDate:'2026-06-30',scholarshipSemesterId:null,selectable:true,unavailableReason:null});
const sources=[term('21','2510'),...Array.from({length:20},(_,i)=>term(String(100+i),'25'+String(30+i))),term('24','2520',2),term('9007199254740993','2410'),{...term('25','2521',2),selectable:false,unavailableReason:'SUMMER_MERGED_INTO_SEMESTER_2'}];
const row=(id,sourceId='24')=>({id,sourceSemesterId:sourceId,semesterId:sourceId==='21'?'501':'502',academicYear:'2025-2026',semesterOrder:sourceId==='21'?1:2,number:1,name:'Đợt thực hành '+id,status:'draft'});
const detail=r=>({...r,createdBy:'301',semester:{id:r.semesterId,academicYear:r.academicYear,semesterOrder:r.semesterOrder,startDate:'2025-08-01',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null});
const browser=await chromium.launch({channel:'chrome',headless:true});let count=0;
async function check(id,run){
 if(process.env.CTSV_HB_CASE&&!process.env.CTSV_HB_CASE.split(',').some(filter=>id.includes(filter)))return;
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}}),page=await context.newPage();page.setDefaultTimeout(6500);
 const state={default:sources[21],defaultStatus:200,directStatus:200,sourceStatus:200,groups:{student:false,specialist:true},rows:[row('1001'),row('1002','21')],calls:[],errors:[],holdDefault:null,holdDirect:null,sourceOverride:null,holdSearch:new Map()};
 page.on('pageerror',e=>state.errors.push(e.message));
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const req=route.request(),u=new URL(req.url()),p=u.pathname;
  const send=(body,status=200)=>route.fulfill({status,json:body,headers:{ETag:'"AAAAAAAAAAE"'}}).catch(()=>{});
  if(['/api/xac-thuc/dang-nhap','/api/xac-thuc/lam-moi-token'].includes(p))return send({accessToken:'synthetic-hb-context',expiresIn:900});
  if(p==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  if(p==='/api/xac-thuc/toi')return send({accountId:'synthetic-hb-context',username:'SYNTHETIC',functions:[]});
  if(p==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return send({student:state.groups.student,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(p===api+'/toi/nhom-chuc-nang')return send(state.groups);
  state.calls.push({path:p,method:req.method(),query:Object.fromEntries(u.searchParams),body:req.method()==='POST'?req.postDataJSON():null});
  if(p===api+'/hoc-ky-nguon/mac-dinh'){
   if(state.holdDefault)await state.holdDefault;
   return send(state.defaultStatus===200?{semester:state.default,reason:state.default?null:'NO_ELIGIBLE_SEMESTER'}:{code:'DEPENDENCY_UNAVAILABLE'},state.defaultStatus);
  }
  if(p===api+'/hoc-ky-nguon'){
   if(state.holdSearch.has(u.searchParams.get('q')))await state.holdSearch.get(u.searchParams.get('q'));
   if(state.sourceStatus!==200)return send({code:'DEPENDENCY_UNAVAILABLE'},state.sourceStatus);
   const found=sources.filter(s=>(s.code+' '+s.name).includes(u.searchParams.get('q')??'')),n=Number(u.searchParams.get('page')),size=Number(u.searchParams.get('size'));
   return send({items:found.slice(n*size,(n+1)*size),page:n,size,totalElements:found.length});
  }
  if(p.startsWith(api+'/hoc-ky-nguon/')){
   if(state.holdDirect)await state.holdDirect;
   const found=state.sourceOverride??sources.find(s=>s.sourceSemesterId===p.split('/').at(-1));
   return send(state.directStatus!==200?{code:'DEPENDENCY_UNAVAILABLE'}:found??{code:'NOT_FOUND'},state.directStatus!==200?state.directStatus:found?200:404);
  }
  if(p===api+'/kkht/dot-xet'&&req.method()==='POST'){
   const b=req.postDataJSON(),r={...row('1003',b.sourceSemesterId),name:b.name,number:b.number};state.rows.push(r);return send(detail(r),201);
  }
  if(p===api+'/kkht/dot-xet'){
   let items=state.rows;
   if(u.searchParams.has('sourceSemesterId'))items=items.filter(r=>r.sourceSemesterId===u.searchParams.get('sourceSemesterId'));
   if(u.searchParams.has('semesterId'))items=items.filter(r=>r.semesterId===u.searchParams.get('semesterId'));
   if(u.searchParams.has('number'))items=items.filter(r=>r.number===Number(u.searchParams.get('number')));
   return send({items,page:0,size:20,totalElements:items.length});
  }
  const r=state.rows.find(r=>p===api+'/kkht/dot-xet/'+r.id);
  if(r)return send(detail(r));
  if(p.startsWith(api+'/kkht/dot-xet/'))return send({code:'NOT_FOUND'},404);
  throw new Error('Unexpected fixture path '+p);
 });
 const open=async(search='')=>{await page.goto(origin+path+search);await expect(page.getByRole('heading',{name:'Đợt xét học bổng KKHT',exact:true})).toBeVisible();};
 const choose=async(code)=>{await page.getByRole('combobox',{name:'Tìm học kỳ',exact:true}).fill(code);await page.getByRole('option',{name:new RegExp('^'+code+' ·')}).click();};
 try{await run({page,state,open,choose});assert.deepEqual(state.errors,[]);count++;console.log(id+' PASS (HTTP STUB)');}finally{await context.close();}
}
try{
 await check('HB-LIVE-01-click-selection',async({page,state,open})=>{
  await open('?hocKyNguonId=21');await expect(page.getByText('Đợt thực hành 1002',{exact:true})).toBeVisible();
  const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2520');
  await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();
  assert(state.calls.some(c=>c.path===api+'/hoc-ky-nguon'&&c.query.q==='2520'));
  await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByRole('button',{name:'Tìm học kỳ',exact:true})).toHaveCount(0);
  await page.getByRole('option',{name:/^2520 ·/}).click();await expect(page).toHaveURL(/hocKyNguonId=24/);
  await expect(input).toHaveAttribute('aria-expanded','false');await expect(input).toHaveValue('2520 · Học kỳ 2');await expect(page.getByText('Đợt thực hành 1001',{exact:true})).toBeVisible();
 });
 await check('HB-LIVE-02-debounce',async({page,state,open})=>{
  await open('?hocKyNguonId=21');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2');await input.fill('25');await input.fill('2520');
  await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();
  assert.deepEqual(state.calls.filter(c=>c.path===api+'/hoc-ky-nguon'&&c.query.q).map(c=>c.query.q),['2520']);
 });
 await check('HB-LIVE-03-stale-response',async({page,state,open})=>{
  let release;state.holdSearch.set('2510',new Promise(r=>release=r));
  await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2510');
  await expect.poll(()=>state.calls.some(c=>c.path===api+'/hoc-ky-nguon'&&c.query.q==='2510')).toBe(true);
  await input.fill('2520');await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();release();
  await input.press('ArrowDown');await input.press('Enter');await expect(page).toHaveURL(/hocKyNguonId=24/);
  await expect(page.getByText('Đợt thực hành 1001',{exact:true})).toBeVisible();
 });
 await check('HB-LIVE-04-keyboard-and-dismiss',async({page,open})=>{
  await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2520');
  await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();await input.press('Escape');
  await expect(input).toHaveValue('2510 · Học kỳ 1');await expect(page).toHaveURL(/hocKyNguonId=21/);
  await input.press('ArrowDown');await expect(page.getByRole('listbox')).toBeVisible();await input.fill('2520');
  await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();await input.press('ArrowDown');
  const active=await input.getAttribute('aria-activedescendant');assert(active);await expect(page.locator('[id="'+active+'"]')).toContainText('2520');
  await input.press('Enter');await expect(page).toHaveURL(/hocKyNguonId=24/);await expect(input).toBeFocused();
 });
 await check('HB-LIVE-05-unavailable-and-empty',async({page,open})=>{
  await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2521');
  await expect(page.getByRole('option',{name:/^2521 ·/})).toHaveAttribute('aria-disabled','true');
  await expect(page.getByText('Học kỳ hè không tạo đợt riêng; chọn học kỳ 2.',{exact:true})).toBeVisible();
  await input.press('ArrowDown');await input.press('Enter');await expect(page).toHaveURL(/hocKyNguonId=21/);
  await input.fill('không tồn tại');await expect(page.getByText('Không có học kỳ phù hợp.',{exact:true})).toBeVisible();
  await input.press('Tab');await expect(input).toHaveAttribute('aria-expanded','false');await expect(input).toHaveValue('2510 · Học kỳ 1');
 });
 await check('HB-LIVE-06-error-retry',async({page,state,open})=>{
  await open('?hocKyNguonId=21');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();state.sourceStatus=503;
  await page.getByRole('combobox',{name:'Tìm học kỳ',exact:true}).fill('2520');await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByText('Đợt thực hành 1002',{exact:true})).toBeVisible();
  state.sourceStatus=200;await page.getByRole('button',{name:'Thử lại tìm học kỳ',exact:true}).click();
  await page.getByRole('option',{name:/^2520 ·/}).click();await expect(page).toHaveURL(/hocKyNguonId=24/);
 });
 await check('HB-LIVE-07-paging',async({page,open})=>{
  await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.click();
  await expect(page.getByRole('option',{name:/^2510 ·/})).toHaveAttribute('aria-selected','true');
  await page.getByRole('button',{name:'Trang học kỳ sau',exact:true}).click();await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();
  await page.getByRole('option',{name:/^2520 ·/}).click();await expect(page).toHaveURL(/hocKyNguonId=24/);
 });
 await check('HB-LIVE-08-mobile',async({page,open})=>{
  await page.setViewportSize({width:360,height:800});await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill('2520');
  const option=page.getByRole('option',{name:/^2520 ·/});await expect(option).toBeVisible();
  const inputBox=await input.boundingBox(),optionBox=await option.boundingBox();assert(optionBox.y>=inputBox.y+inputBox.height);assert(optionBox.x>=0&&optionBox.x+optionBox.width<=360);
  await option.click();await expect(page).toHaveURL(/hocKyNguonId=24/);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 });
 await check('HB-LIVE-09-type-after-selection',async({page,open,choose})=>{
  await open('?hocKyNguonId=21');await choose('2520');await expect(page).toHaveURL(/hocKyNguonId=24/);
  const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await expect(input).toBeFocused();
  await input.pressSequentially('2510');await expect(page.getByRole('option',{name:/^2510 ·/})).toBeVisible();
  await input.press('ArrowDown');await input.press('Enter');await expect(page).toHaveURL(/hocKyNguonId=21/);
 });
 await check('HB-T026-B01-default',async({page,state,open})=>{
  await open();await expect(page).toHaveURL(/hocKyNguonId=24/);await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  assert(state.calls.some(c=>c.path.endsWith('/mac-dinh')));assert(state.calls.filter(c=>c.path===api+'/kkht/dot-xet').every(c=>c.query.sourceSemesterId==='24'));
  await expect(page.getByText('Học kỳ đã chọn: 2520 · Học kỳ 2',{exact:true})).toBeVisible();
 });
 await check('HB-T026-B02-explicit-bigint-unlinked',async({page,state,open})=>{
  await open('?hocKyNguonId=9007199254740993');await expect(page.getByText('0 đợt xét',{exact:true})).toBeVisible();assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')));
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByText('2410 · Học kỳ 1',{exact:true})).toBeVisible();
  assert.equal(state.calls.filter(c=>c.method==='POST').length,0);
 });
 await check('HB-T026-B03-change-reset-and-inherit',async({page,state,open,choose})=>{
  await open();await expect(page).toHaveURL(/hocKyNguonId=24/);await page.getByLabel('Số đợt',{exact:true}).fill('77');await page.getByRole('button',{name:'Lọc danh sách',exact:true}).click();
  await choose('2510');await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByLabel('Số đợt',{exact:true})).toHaveValue('');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByText('2510 · Học kỳ 1',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(page).toHaveURL(/hocKyNguonId=21/);
 });
 await check('HB-T026-B04-history-reload-detail',async({page,open,choose})=>{
  await open('?hocKyNguonId=21');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();await choose('2520');await expect(page).toHaveURL(/hocKyNguonId=24/);
  await page.goBack();await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByText('Đợt thực hành 1002',{exact:true})).toBeVisible();
  await page.goForward();await expect(page.getByText('Đợt thực hành 1001',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Xem chi tiết Đợt thực hành 1001',exact:true}).click();await expect(page).toHaveURL(/hocKyNguonId=24.*dotXetId=1001/);
  await page.reload();await expect(page.getByRole('heading',{name:'Đợt thực hành 1001',exact:true})).toBeVisible();await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
 });
 await check('HB-T026-B05-legacy-detail',async({page,state,open})=>{await open('?dotXetId=1002');await expect(page.getByRole('heading',{name:'Đợt thực hành 1002',exact:true})).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21/);assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')));});
 await check('HB-T026-B06-mismatched-detail',async({page,open})=>{await open('?hocKyNguonId=21&dotXetId=1001');await expect(page.getByRole('alert')).toContainText('không thuộc học kỳ');await expect(page.getByRole('button',{name:'Sửa đợt xét',exact:true})).toHaveCount(0);await expect(page).toHaveURL(/hocKyNguonId=21/);});
 await check('HB-T026-B07-invalid-url',async({page,state,open})=>{
  for(const search of ['?hocKyNguonId=','?hocKyNguonId=01','?hocKyNguonId=21&hocKyNguonId=24','?dotXetId=bad']){await open(search);await expect(page.getByRole('alert')).toContainText('Đường dẫn');await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();}
  assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')||c.path===api+'/kkht/dot-xet'));
 });
 await check('HB-T026-B08-missing-source-recover',async({page,state,open,choose})=>{await open('?hocKyNguonId=999');await expect(page.getByRole('alert')).toBeVisible();assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')));await choose('2510');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21/);});
 await check('HB-T026-B09-empty-default',async({page,state,open,choose})=>{state.default=null;await open();await expect(page.getByText(/Chưa có học kỳ phù hợp để chọn mặc định/)).toBeVisible();assert(!state.calls.some(c=>c.path===api+'/kkht/dot-xet'));await choose('2510');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();});
 await check('HB-T026-B10-source-error-retry',async({page,state,open})=>{state.directStatus=503;await open('?hocKyNguonId=21');await expect(page.getByRole('alert')).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21/);state.directStatus=200;await page.getByRole('button',{name:'Đọc lại kỳ đã chọn',exact:true}).click();await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')));});
 await check('HB-T026-B11-late-default',async({page,state,open,choose})=>{let release;state.holdDefault=new Promise(r=>release=r);await open();await choose('2510');release();await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByText('Đợt thực hành 1002',{exact:true})).toBeVisible();});
 await check('HB-T026-B12-late-direct',async({page,state,open,choose})=>{let release;state.holdDirect=new Promise(r=>release=r);await open('?hocKyNguonId=24');await choose('2510');release();await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByText('Đợt thực hành 1002',{exact:true})).toBeVisible();});
 await check('HB-T026-B13-picker-paging-search',async({page,open})=>{await open();await expect(page).toHaveURL(/hocKyNguonId=24/);await page.getByRole('combobox',{name:'Tìm học kỳ',exact:true}).click();await page.getByRole('button',{name:'Trang học kỳ sau',exact:true}).click();await expect(page.getByRole('option',{name:/^2520 ·/})).toHaveAttribute('aria-selected','true');await page.getByRole('button',{name:'Trang học kỳ trước',exact:true}).click();await expect(page.getByText('Học kỳ đã chọn: 2520 · Học kỳ 2',{exact:true})).toBeVisible();});
 await check('HB-T026-B14-create-readback',async({page,state,open})=>{await open('?hocKyNguonId=21');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await page.getByLabel('Tên đợt',{exact:true}).fill('Đợt kế thừa kỳ');await page.getByLabel('Số đợt',{exact:true}).fill('3');await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByRole('heading',{name:'Đợt kế thừa kỳ',exact:true})).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21.*dotXetId=1003/);const posts=state.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.equal(posts[0].body.sourceSemesterId,'21');});
 await check('HB-T026-B15-student',async({page,state})=>{state.groups={student:true,specialist:false};await page.goto(origin+path+'?hocKyNguonId=21');await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(state.calls.length,0);});
 await check('HB-T026-B16-mobile-keyboard',async({page,open})=>{await page.setViewportSize({width:360,height:800});await open('?hocKyNguonId=21');const input=page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await expect(input).toHaveValue('2510 · Học kỳ 1');await input.fill('2520');await expect(page.getByRole('option',{name:/^2520 ·/})).toBeVisible();await input.press('ArrowDown');await input.press('Enter');await expect(page).toHaveURL(/hocKyNguonId=24/);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));});
 await check('HB-T026-B17-default-unavailable',async({page,state,open,choose})=>{state.defaultStatus=503;await open();await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByText(/Chưa có học kỳ phù hợp để chọn mặc định/)).toHaveCount(0);assert(!state.calls.some(c=>c.path===api+'/kkht/dot-xet'));await choose('2510');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();});
 await check('HB-T026-B18-summer-read-only',async({page,state,open})=>{
  state.sourceOverride={...sources[21],code:'2521',name:'Học kỳ hè',selectable:false,unavailableReason:'SUMMER_MERGED_INTO_SEMESTER_2'};
  await open('?hocKyNguonId=24');await expect(page.getByRole('alert')).toContainText('Học kỳ hè không tạo đợt riêng');
  await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();
  assert(state.calls.some(c=>c.path===api+'/kkht/dot-xet'&&c.query.sourceSemesterId==='24'));
 });
 await check('HB-T026-B19-source-role-revoked',async({page,state,open})=>{state.directStatus=403;await open('?hocKyNguonId=21');await expect(page.getByRole('alert')).toContainText('Bạn không có quyền truy cập các đợt xét KKHT');await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toHaveCount(0);});
 await check('HB-T026-B20-search-error-keeps-context',async({page,state,open})=>{await open('?hocKyNguonId=21');await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();state.sourceStatus=503;await page.getByRole('combobox',{name:'Tìm học kỳ',exact:true}).fill('2520');await expect(page.getByRole('alert')).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21/);await expect(page.getByText('Học kỳ đã chọn: 2510 · Học kỳ 1',{exact:true})).toBeVisible();await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();});
 await check('HB-T026-B21-legacy-missing-detail',async({page,state,open})=>{await open('?dotXetId=999');await expect(page.getByRole('alert')).toContainText('Không tìm thấy dữ liệu');await expect(page.getByText('Đang xác định học kỳ xét…',{exact:true})).toHaveCount(0);assert(!state.calls.some(c=>c.path.endsWith('/mac-dinh')||c.path===api+'/kkht/dot-xet'));});
 console.log('HB-T024-006 browser: '+count+' PASS');
}catch(e){console.error(e.message);process.exitCode=1;}finally{await browser.close();}
