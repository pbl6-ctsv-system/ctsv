// Controlled HTTP browser tests. No SQL, screenshots or evidence output.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173',api='/api/ctsv/hoc-bong',routePath='/hoc-bong/kkht/dot-xet';
const initial={id:'9007199254740993',sourceSemesterId:'21',number:1,name:'Đợt ban đầu',status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const browser=await chromium.launch({channel:'chrome',headless:true});const passed=[];
async function check(id,run){
 if(process.env.CTSV_HB_CASE&&!id.includes(process.env.CTSV_HB_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});
 const page=await context.newPage();page.setDefaultTimeout(7000);
 const state={record:structuredClone(initial),version:1,tagMissing:false,groups:{student:false,specialist:true},puts:[],calls:[],errors:[],putStatus:200,putCode:null,lost:false,conflictOnce:false,holdPut:null,releasePut:null,readbackStatus:200,holdReadback:false,releaseReadback:null,cancelled:0,saved:false};
 page.on('pageerror',error=>state.errors.push(error.message));
 page.on('requestfailed',request=>{if(new URL(request.url()).pathname===api+'/kkht/dot-xet/'+initial.id)state.cancelled++;});
 const tag=()=>`"v${state.version}"`;
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const request=route.request(),url=new URL(request.url()),p=url.pathname;
  if(['/api/xac-thuc/dang-nhap','/api/xac-thuc/lam-moi-token'].includes(p))return route.fulfill({json:{accessToken:'synthetic-edit-token',expiresIn:900}});
  if(p==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  if(p==='/api/xac-thuc/toi')return route.fulfill({json:{accountId:'synthetic-hb-edit',username:'SYNTHETIC-HB',functions:[]}});
  if(p==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return route.fulfill({json:{student:state.groups.student,mghpSpecialist:false,ctsvRoleAdmin:false}});
  if(p===api+'/toi/nhom-chuc-nang')return route.fulfill({json:state.groups});
  state.calls.push({path:p,method:request.method(),query:Object.fromEntries(url.searchParams)});
  if(!state.groups.specialist)return route.fulfill({status:403,json:{code:'FORBIDDEN'}});
  if(p===api+'/kkht/dot-xet'){
   const d=state.record;
   const items=url.searchParams.has('number')&&Number(url.searchParams.get('number'))!==d.number?[]:[{id:d.id,sourceSemesterId:d.sourceSemesterId,semesterId:d.semester.id,academicYear:d.semester.academicYear,semesterOrder:d.semester.semesterOrder,number:d.number,name:d.name,status:d.status}];
   return route.fulfill({json:{items,page:0,size:20,totalElements:items.length}});
  }
  if(p===api+'/kkht/dot-xet/'+initial.id){
   if(request.method()==='PUT'){
    const body=request.postDataJSON(),ifMatch=request.headers()['if-match'];state.puts.push({body,ifMatch});
    assert.deepEqual(Object.keys(body).sort(),['name','number']);
    if(state.holdPut)await state.holdPut;
    if(state.conflictOnce){state.conflictOnce=false;state.record.number=2;state.record.name='Bản từ tab khác';state.version++;}
    if(state.putStatus!==200){
     if(state.putCode==='ROUND_NOT_EDITABLE')state.record.status='open';
     return route.fulfill({status:state.putStatus,json:{code:state.putCode??(state.putStatus===409?'ROUND_EXISTS':'VALIDATION_ERROR'),errors:[{field:'name',code:'INVALID',message:'Invalid'}]}});
    }
    if(ifMatch!==tag())return route.fulfill({status:412,json:{code:'VERSION_CONFLICT'}});
    if(state.record.number!==body.number||state.record.name!==body.name.trim())state.version++;
    state.record.number=body.number;state.record.name=body.name.trim();state.saved=true;
    if(state.lost)return route.abort('failed');
    return route.fulfill({headers:{ETag:tag()},json:state.record}).catch(()=>{});
   }
   if(state.saved&&state.holdReadback)await new Promise(resolve=>{state.releaseReadback=resolve;});
   if(state.saved&&state.readbackStatus!==200)return route.fulfill({status:state.readbackStatus,json:{code:'DEPENDENCY_UNAVAILABLE'}});
   return route.fulfill({headers:state.tagMissing?{}:{ETag:tag()},json:state.record}).catch(()=>{});
  }
  throw new Error('Unexpected fixture request '+p);
 });
 const detail=async()=>{
  await page.goto(origin+routePath);
  await page.getByRole('button',{name:'Xem chi tiết '+state.record.name,exact:true}).click();
  await expect(page.getByRole('heading',{name:state.record.name,exact:true})).toBeVisible();
 };
 const edit=async()=>{await detail();await page.getByRole('button',{name:'Sửa đợt xét',exact:true}).click();await expect(page.getByRole('heading',{name:'Sửa đợt xét',exact:true})).toBeVisible();};
 const fill=async(name='Đợt đã sửa',number='4')=>{await page.getByLabel('Tên đợt',{exact:true}).fill(name);await page.getByLabel('Số đợt',{exact:true}).fill(number);};
 try{await run({page,state,edit,detail,fill});assert.deepEqual(state.errors,[]);passed.push(id);console.log(id+' PASS (STUB)');}
 finally{state.releasePut?.();state.releaseReadback?.();await context.close();}
}
try{
 await check('HB-T023-B01-save-readback-list',async({page,state,edit,fill})=>{
  await edit();await fill();
  await expect(page.getByLabel('Tìm học kỳ',{exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt đã sửa',exact:true})).toBeVisible();
  await expect(page.getByText('Đã lưu thay đổi đợt xét.',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByText('Đợt đã sửa',{exact:true})).toBeVisible();
  assert.deepEqual(state.puts,[{body:{number:4,name:'Đợt đã sửa'},ifMatch:'"v1"'}]);
 });
 await check('HB-T023-B02-conflict-explicit-new-version',async({page,state,edit,fill})=>{
  await edit();await fill('Giữ nội dung của tôi','3');state.conflictOnce=true;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Dữ liệu đã được người khác thay đổi');
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Giữ nội dung của tôi');
  await page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();
  await expect(page.getByText('Bản từ tab khác',{exact:true})).toBeVisible();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Giữ nội dung của tôi');
  assert.equal(state.puts.length,1);
  await page.getByRole('button',{name:'Giữ nội dung nhập và dùng bản mới',exact:true}).click();
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Giữ nội dung của tôi',exact:true})).toBeVisible();
  assert.equal(state.puts[1].ifMatch,'"v2"');
 });
 await check('HB-T023-B03-duplicate-validation-preserve',async({page,state,edit,fill})=>{
  await edit();await fill('Giữ khi lỗi','7');state.putStatus=409;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Số đợt đã tồn tại trong học kỳ');
  await expect(page.getByLabel('Số đợt',{exact:true})).toHaveValue('7');
  state.putStatus=400;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveAttribute('aria-invalid','true');
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Giữ khi lỗi');
 });
 await check('HB-T023-B04-missing-tag-requires-explicit-refresh',async({page,state,edit,fill})=>{
  state.tagMissing=true;await edit();await fill();
  await expect(page.getByRole('button',{name:'Lưu thay đổi',exact:true})).toBeDisabled();
  state.tagMissing=false;
  await page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();
  await page.getByRole('button',{name:'Giữ nội dung nhập và dùng bản mới',exact:true}).click();
  assert.equal(state.puts.length,0);
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Đợt đã sửa');
 });
 await check('HB-T023-B05-no-longer-draft',async({page,state,edit,fill})=>{
  await edit();await fill();state.putStatus=409;state.putCode='ROUND_NOT_EDITABLE';
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Đợt không còn ở trạng thái Nháp');
  await page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();
  await expect(page.getByRole('button',{name:'Lưu thay đổi',exact:true})).toBeDisabled();
 });
 await check('HB-T023-B06-double-submit-readback-retry-only',async({page,state,edit,fill})=>{
  await edit();await fill();let release;state.holdPut=new Promise(resolve=>{release=resolve;state.releasePut=resolve;});state.readbackStatus=503;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await page.evaluate(()=>document.querySelector('form[aria-label="Sửa đợt xét KKHT"]').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  assert.equal(state.puts.length,1);release();
  await expect(page.getByRole('alert')).toContainText('Thay đổi đã được lưu nhưng chưa tải lại đầy đủ');
  state.readbackStatus=200;
  await page.getByRole('button',{name:'Đọc lại kết quả',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt đã sửa',exact:true})).toBeVisible();
  assert.equal(state.puts.length,1);
 });
 await check('HB-T023-B07-lost-response-check-only',async({page,state,edit,fill})=>{
  await edit();await fill();state.lost=true;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa xác nhận được kết quả sửa đợt');
  await page.getByRole('button',{name:'Kiểm tra kết quả đã lưu',exact:true}).click();
  await expect(page.getByText('Đợt đã sửa',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Xem đợt hiện tại',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt đã sửa',exact:true})).toBeVisible();
  await expect(page.getByText('Đã đối chiếu kết quả sửa đợt.',{exact:true})).toBeVisible();assert.equal(state.puts.length,1);
 });
 await check('HB-T023-B08-nondraft-and-student-hidden',async({page,state,detail})=>{
  state.record.status='open';await detail();await expect(page.getByRole('button',{name:'Sửa đợt xét',exact:true})).toHaveCount(0);
  state.groups={student:true,specialist:false};await page.goto(origin+routePath+'?dotXetId='+initial.id);
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Sửa đợt xét',exact:true})).toHaveCount(0);
 });
 await check('HB-T023-B09-cancel-mobile-keyboard',async({page,state,edit,fill})=>{
  await edit();await fill('Chưa lưu','8');await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  await page.getByRole('button',{name:'Quay lại chi tiết',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt ban đầu',exact:true})).toBeVisible();assert.equal(state.puts.length,0);
  await page.getByRole('button',{name:'Sửa đợt xét',exact:true}).click();await fill('Lưu bằng bàn phím','9');
  await page.getByLabel('Tên đợt',{exact:true}).press('Enter');
  await expect(page.getByRole('heading',{name:'Lưu bằng bàn phím',exact:true})).toBeVisible();
 });
 await check('HB-T023-B10-leave-during-readback',async({page,state,edit,fill})=>{
  await edit();await fill();state.holdReadback=true;
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect.poll(()=>typeof state.releaseReadback).toBe('function');
  await page.goBack();
  await expect(page.getByRole('heading',{name:'Danh sách đợt xét',exact:true})).toBeVisible();
  await expect.poll(()=>state.cancelled).toBeGreaterThan(0);state.releaseReadback();
  await expect(page).toHaveURL(origin+routePath);
 });
 await check('HB-T023-B11-precondition-and-replace-input',async({page,state,edit,fill})=>{
  await edit();await fill('Nội dung chưa lưu','9');state.putStatus=428;state.putCode='PRECONDITION_REQUIRED';
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa xác định được phiên dữ liệu');
  state.record.name='Bản hiện tại để thay';state.record.number=6;state.version++;
  await page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Nội dung chưa lưu');
  await page.getByRole('button',{name:'Thay nội dung nhập bằng bản mới',exact:true}).click();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveValue('Bản hiện tại để thay');
  await expect(page.getByLabel('Số đợt',{exact:true})).toHaveValue('6');assert.equal(state.puts.length,1);
 });
 await check('HB-T023-B12-no-op',async({page,state,edit})=>{
  await edit();await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await expect(page.getByText('Đã lưu thay đổi đợt xét.',{exact:true})).toBeVisible();
  assert.equal(state.version,1);assert.deepEqual(state.puts,[{body:{number:1,name:'Đợt ban đầu'},ifMatch:'"v1"'}]);
 });
 await check('HB-T023-B13-leave-during-put',async({page,state,edit,fill})=>{
  await edit();await fill();state.holdPut=new Promise(resolve=>{state.releasePut=resolve;});
  const pending=page.waitForResponse(response=>response.request().method()==='PUT');
  await page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();
  await page.goBack();await expect(page.getByRole('heading',{name:'Danh sách đợt xét',exact:true})).toBeVisible();
  state.releasePut();await pending;
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByText('Đợt đã sửa',{exact:true})).toBeVisible();
  await expect(page).toHaveURL(origin+routePath);
  await expect(page.getByText('Đã lưu thay đổi đợt xét.',{exact:true})).toHaveCount(0);
 });
}finally{console.log('HB edit browser: '+passed.length+' PASS');await browser.close();}
