// Browser acceptance with controlled HTTP responses; no SQL, screenshots or artifacts.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173';
const api='/api/ctsv/hoc-bong',routePath='/hoc-bong/kkht/dot-xet';
const base={sourceSemesterId:'21',code:'2510',name:'Học kỳ 1',academicYear:'2025-2026',semesterOrder:1,startDate:'2025-08-01',endDate:'2026-01-15',scholarshipSemesterId:null,selectable:true,unavailableReason:null};
const sources=[base,{...base,sourceSemesterId:'24',code:'2520',name:'Học kỳ 2',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30'},{...base,sourceSemesterId:'22',code:'2521',name:'Học kỳ hè',semesterOrder:2,startDate:'2026-07-01',endDate:'2026-08-15',selectable:false,unavailableReason:'SUMMER_MERGED_INTO_SEMESTER_2'},{...base,sourceSemesterId:'23',code:'2610',name:null,academicYear:'2026-2027',startDate:'2026-08-01',endDate:'2027-01-15',selectable:false,unavailableReason:'SOURCE_DATES_UNVERIFIED'}];
for(let i=0;i<19;i++){
 const year=2030+i;
 sources.push({...base,sourceSemesterId:String(100+i),code:String(30+i)+'10',name:'Học kỳ nguồn '+i,academicYear:`${year}-${year+1}`,startDate:`${year}-08-01`,endDate:`${year+1}-01-15`});
}
const browser=await chromium.launch({channel:'chrome',headless:true});
const cases=[];
async function check(id,run){
 if(process.env.CTSV_HB_CASE&&!id.includes(process.env.CTSV_HB_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});
 const page=await context.newPage();page.setDefaultTimeout(7000);
 const state={groups:{student:false,specialist:true},rows:[],details:new Map(),calls:[],cancelled:[],errors:[],postStatus:201,postCode:null,lost:false,holdPost:null,changeAfterPost:false,detailStatus:200,sourceStatus:200,directStatus:200,slowQuery:null,releaseSource:null};
 page.on('pageerror',error=>state.errors.push(error.message));
 page.on('requestfailed',request=>{const url=new URL(request.url());if(url.pathname===api+'/hoc-ky-nguon')state.cancelled.push(url.searchParams.get('q'));});
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const request=route.request(),url=new URL(request.url()),p=url.pathname;
  if(['/api/xac-thuc/dang-nhap','/api/xac-thuc/lam-moi-token'].includes(p))return route.fulfill({json:{accessToken:'synthetic-hb-token',expiresIn:900}});
  if(p==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  if(p==='/api/xac-thuc/toi')return route.fulfill({json:{accountId:'synthetic-hb-create',username:'SYNTHETIC-HB',functions:[]}});
  if(p==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return route.fulfill({json:{student:state.groups.student,mghpSpecialist:false,ctsvRoleAdmin:false}});
  if(p===api+'/toi/nhom-chuc-nang')return route.fulfill({json:state.groups});
  state.calls.push({path:p,method:request.method(),query:Object.fromEntries(url.searchParams)});
  if(!state.groups.specialist)return route.fulfill({status:403,json:{code:'FORBIDDEN'}});
  if(p===api+'/hoc-ky-nguon/mac-dinh')return route.fulfill({json:{semester:base,reason:null}});
  if(p.startsWith(api+'/hoc-ky-nguon/'))return route.fulfill({status:state.directStatus,json:state.directStatus===200?sources.find(item=>item.sourceSemesterId===p.split('/').at(-1)):{code:'DEPENDENCY_UNAVAILABLE'}});
  if(p===api+'/hoc-ky-nguon'){
   if(state.sourceStatus!==200)return route.fulfill({status:state.sourceStatus,json:{code:state.sourceStatus===403?'FORBIDDEN':'DEPENDENCY_UNAVAILABLE'}});
   const q=url.searchParams.get('q')??'';
   if(q===state.slowQuery)await new Promise(resolve=>{state.releaseSource=resolve;});
   const found=sources.filter(item=>(item.code+' '+item.name).includes(q));
   const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);
   return route.fulfill({json:{items:found.slice(page*size,(page+1)*size),page,size,totalElements:found.length}}).catch(()=>{});
  }
  if(p===api+'/kkht/dot-xet'&&request.method()==='POST'){
   const body=request.postDataJSON();
   if(state.holdPost)await state.holdPost;
   if(state.postStatus!==201)return route.fulfill({status:state.postStatus,json:{code:state.postCode??(state.postStatus===409?'ROUND_EXISTS':'VALIDATION_ERROR'),errors:[{field:'name',code:'INVALID',message:'Invalid name'}]}});
   const source=sources.find(item=>item.sourceSemesterId===body.sourceSemesterId);
   assert(source?.selectable);
   assert.deepEqual(Object.keys(body).sort(),['name','number','sourceSemesterId']);
   const id=String(1001+state.rows.length),semesterId='501';
   const row={id,sourceSemesterId:body.sourceSemesterId,semesterId,academicYear:source.academicYear,semesterOrder:source.semesterOrder,number:body.number,name:body.name.trim(),status:'draft'};
   state.rows.push(row);
   const detail={id,sourceSemesterId:row.sourceSemesterId,number:row.number,name:row.name,status:'draft',createdBy:'301',semester:{id:semesterId,academicYear:source.academicYear,semesterOrder:source.semesterOrder,startDate:source.startDate,endDate:source.endDate,status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
   state.details.set(id,detail);
   const postBody=structuredClone(detail);
   if(state.changeAfterPost){detail.number++;detail.name='Đợt đã được cập nhật';row.number=detail.number;row.name=detail.name;}
   if(state.lost)return route.abort('failed');
   return route.fulfill({status:201,headers:{ETag:'"AAAAAAAAAAE="',Location:api+'/kkht/dot-xet/'+id},json:postBody});
  }
  if(p===api+'/kkht/dot-xet'){
   let items=state.rows;
   if(url.searchParams.has('number'))items=items.filter(item=>item.number===Number(url.searchParams.get('number')));
   if(url.searchParams.has('semesterId'))items=items.filter(item=>item.semesterId===url.searchParams.get('semesterId'));
   if(url.searchParams.has('sourceSemesterId'))items=items.filter(item=>item.sourceSemesterId===url.searchParams.get('sourceSemesterId'));
   return route.fulfill({json:{items,page:0,size:20,totalElements:items.length}});
  }
  const detail=state.details.get(p.split('/').at(-1));
  if(detail)return route.fulfill({status:state.detailStatus,headers:{ETag:'"AAAAAAAAAAE="'},json:state.detailStatus===200?detail:{code:'DEPENDENCY_UNAVAILABLE'}});
  throw new Error('Unexpected synthetic request '+p);
 });
 const openPicker=async()=>{
  await page.goto(origin+routePath);
  await expect(page.getByRole('radio',{name:'2510 · Học kỳ 1',exact:true})).toBeVisible();
 };
 const open=async()=>{
  await openPicker();
  await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeEnabled();
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Tạo đợt xét',exact:true})).toBeVisible();
 };
 const fill=async(name='Đợt tạo mới',number='3')=>{
  await page.getByLabel('Số đợt',{exact:true}).fill(number);
  await page.getByLabel('Tên đợt',{exact:true}).fill(name);
 };
 try{await run({page,state,open,openPicker,fill});assert.deepEqual(state.errors,[]);cases.push(id);console.log(id+' PASS (STUB)');}
 finally{state.releaseSource?.();await context.close();}
}
try{
 await check('HB-T022-B01-create-readback-list',async({page,state,open,fill})=>{
  await open();await fill();
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt tạo mới',exact:true})).toBeVisible();
  await expect(page.getByText('Đã tạo đợt xét ở trạng thái Nháp.',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByText('Đợt tạo mới',{exact:true})).toBeVisible();
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
  assert(state.calls.some(call=>call.method==='GET'&&call.query.number==='3'&&call.query.sourceSemesterId==='21'));
 });
 await check('HB-T022-B02-unavailable-and-source-search',async({page,state,openPicker})=>{
  await openPicker();
  await expect(page.getByRole('radio',{name:'2521 · Học kỳ hè',exact:true})).toBeDisabled();
  await expect(page.getByText('Học kỳ hè không tạo đợt riêng; chọn học kỳ 2.',{exact:true})).toBeVisible();
  await expect(page.getByRole('radio',{name:'2610 · Chưa có tên',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'Trang học kỳ sau',exact:true}).click();
  await expect(page.getByText('Trang học kỳ 2 / 2',{exact:true})).toBeVisible();
  await page.getByLabel('Tìm học kỳ',{exact:true}).fill('2520');
  await page.getByLabel('Tìm học kỳ',{exact:true}).press('Enter');
  await expect(page.getByRole('radio',{name:'2520 · Học kỳ 2',exact:true})).toBeVisible();
  await expect(page.getByRole('radio',{name:'2510 · Học kỳ 1',exact:true})).toHaveCount(0);
  assert.equal(state.calls.filter(call=>call.method==='POST').length,0,'search Enter must not submit create');
 });
 await check('HB-T022-B03-duplicate-and-validation-preserve-input',async({page,state,open,fill})=>{
  await open();await fill('Giữ tên khi lỗi','7');state.postStatus=409;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Số đợt đã tồn tại trong học kỳ');
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Giữ tên khi lỗi');
  await expect(page.getByLabel('Số đợt',{exact:true})).toHaveValue('7');
  await expect(page.getByText('2510 · Học kỳ 1',{exact:true})).toBeVisible();
  state.postStatus=400;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveAttribute('aria-invalid','true');
 });
 await check('HB-T022-B04-double-submit',async({page,state,open,fill})=>{
  await open();await fill();
  let release;state.holdPost=new Promise(resolve=>{release=resolve;});
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('button',{name:'Đang tạo…',exact:true})).toBeDisabled();
  await page.evaluate(()=>document.querySelector('form[aria-label="Tạo đợt xét KKHT"]').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
  release();
  await expect(page.getByRole('heading',{name:'Đợt tạo mới',exact:true})).toBeVisible();
 });
 await check('HB-T022-B05-created-but-readback-fails',async({page,state,open,fill})=>{
  await open();await fill();state.detailStatus=503;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Đợt đã được tạo nhưng chưa tải lại đầy đủ');
  state.detailStatus=200;
  await page.getByRole('button',{name:'Đọc lại kết quả',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt tạo mới',exact:true})).toBeVisible();
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
 });
 await check('HB-T022-B06-lost-response-check-only',async({page,state,open,fill})=>{
  await open();await fill();state.lost=true;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa xác nhận được kết quả tạo đợt');
  await page.getByRole('button',{name:'Kiểm tra đợt đã lưu',exact:true}).click();
  await expect(page.getByText('Đợt tìm thấy: Đợt tạo mới',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Mở đợt tìm thấy',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt tạo mới',exact:true})).toBeVisible();
  await expect(page.getByText('Đã đối chiếu đợt xét hiện có.',{exact:true})).toBeVisible();
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
  assert(state.calls.some(call=>call.query.sourceSemesterId==='21'&&call.query.number==='3'));
 });
 await check('HB-T022-B07-source-error-empty-cancel-mobile',async({page,state,openPicker})=>{
  await openPicker();
  await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  await page.getByLabel('Tìm học kỳ',{exact:true}).fill('không tồn tại');
  await page.getByRole('button',{name:'Tìm học kỳ',exact:true}).click();
  await expect(page.getByText('Không có học kỳ phù hợp.',{exact:true})).toBeVisible();
  state.sourceStatus=503;
  await page.getByRole('button',{name:'Đọc lại học kỳ',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa thể kiểm tra trạng thái bảo mật');
  // Shared persistent feedback overlays the mobile footer until the user dismisses it.
  await page.getByRole('button',{name:'Đóng thông báo lỗi',exact:true}).click();
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Danh sách đợt xét',exact:true})).toBeVisible();
  assert.equal(state.calls.filter(call=>call.method==='POST').length,0);
 });
 await check('HB-T022-B08-student-no-create',async({page,state})=>{
  state.groups={student:true,specialist:false};await page.goto(origin+routePath);
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toHaveCount(0);
 });
 await check('HB-T022-B09-source-query-race',async({page,state,openPicker})=>{
  await openPicker();state.slowQuery='2510';
  await page.getByLabel('Tìm học kỳ',{exact:true}).fill('2510');
  await page.getByLabel('Tìm học kỳ',{exact:true}).press('Enter');
  await expect(page.getByText('Đang tải học kỳ…',{exact:true})).toBeVisible();
  await page.getByLabel('Tìm học kỳ',{exact:true}).fill('2520');
  await page.getByLabel('Tìm học kỳ',{exact:true}).press('Enter');
  await expect(page.getByRole('radio',{name:'2520 · Học kỳ 2',exact:true})).toBeVisible();
  await expect.poll(()=>state.cancelled.includes('2510')).toBe(true);
  state.releaseSource?.();
  await expect(page.getByRole('radio',{name:'2510 · Học kỳ 1',exact:true})).toHaveCount(0);
 });
 await check('HB-T022-B10-unknown-result-not-found',async({page,state,open,fill})=>{
  await open();await fill('Không gửi lại tự động','19');state.postStatus=503;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa xác nhận được kết quả tạo đợt');
  await page.getByRole('button',{name:'Kiểm tra đợt đã lưu',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa tìm thấy đợt theo học kỳ và số đợt đã gửi');
  await page.getByRole('button',{name:'Quay lại chỉnh sửa',exact:true}).click();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Không gửi lại tự động');
  await expect(page.getByLabel('Số đợt',{exact:true})).toHaveValue('19');
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
 });
 await check('HB-T022-B11-source-permission-revoked',async({page,state,openPicker,fill})=>{
  await openPicker();state.sourceStatus=403;
  await page.getByRole('button',{name:'Đọc lại học kỳ',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Bạn không có quyền truy cập các đợt xét KKHT');
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveCount(0);
  assert.equal(state.calls.filter(call=>call.method==='POST').length,0);
 });
 await check('HB-T022-B12-source-changed-before-create',async({page,state,open,fill})=>{
  await open();await fill('Giữ tên khi kỳ đổi','8');state.postStatus=409;state.postCode='SOURCE_SEMESTER_UNAVAILABLE';
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Học kỳ nguồn chưa đủ điều kiện hoặc đã thay đổi');
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Giữ tên khi kỳ đổi');
  await expect(page.getByText('2510 · Học kỳ 1',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();
  const before=state.calls.filter(call=>call.path===api+'/hoc-ky-nguon/21').length;
  state.directStatus=503;await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByRole('alert')).toBeVisible();await expect(page).toHaveURL(/hocKyNguonId=21/);
  await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();
  assert(state.calls.filter(call=>call.path===api+'/hoc-ky-nguon/21').length>before,'cancel after source conflict must revalidate the selected source');
 });
 await check('HB-T022-B13-current-readback-after-other-writer',async({page,state,open,fill})=>{
  await open();await fill('Tên ban đầu','3');state.changeAfterPost=true;
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt đã được cập nhật',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByText('Đợt đã được cập nhật',{exact:true})).toBeVisible();
  assert(state.calls.some(call=>call.method==='GET'&&call.query.number==='4'&&call.query.sourceSemesterId==='21'));
  assert.equal(state.calls.filter(call=>call.method==='POST').length,1);
 });
}finally{console.log('HB create browser: '+cases.length+' PASS');await browser.close();}
