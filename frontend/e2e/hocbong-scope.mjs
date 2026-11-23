// T02.8 controlled HTTP acceptance; no SQL writes or screenshot/evidence files.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173',api='/api/ctsv/hoc-bong',url='/hoc-bong/kkht/dot-xet?hocKyNguonId=21';
const term={sourceSemesterId:'21',code:'2610',name:'Học kỳ 1',academicYear:'2026-2027',semesterOrder:1,startDate:'2026-08-01',endDate:'2027-01-15',scholarshipSemesterId:'501',selectable:true,unavailableReason:null};
const revision='A'.repeat(43);
const groups=Array.from({length:23},(_,i)=>({cohortId:i<22?'101':'102',cohortCode:i<22?'K26':'K25',majorId:String(200+i),majorCode:'M'+i,majorName:'Ngành '+i,studentCount:5}));
const initial={id:'1001',sourceSemesterId:'21',number:1,name:'2026/2027 - Học kỳ 1 - Đợt 1',status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2026-2027',semesterOrder:1,startDate:term.startDate,endDate:term.endDate,status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const browser=await chromium.launch({channel:'chrome',headless:true});let passed=0;
async function check(name,run){
 if(process.env.CTSV_HB_CASE&&!name.includes(process.env.CTSV_HB_CASE))return;
 const context=await browser.newContext({viewport:{width:1280,height:900}}),page=await context.newPage();page.setDefaultTimeout(7000);
 const state={sv:false,row:null,scope:null,etag:'"one"',post:[],puts:[],key:null,lost:false,lookup404:false,reject:null,catalogRevision:revision,catalogStatus:200,nullLabels:false,legacy:false,readbackFail:false,errors:[]};
 page.on('pageerror',e=>state.errors.push(e.message));
 await page.route(u=>u.pathname.startsWith('/api/'),async route=>{
  const request=route.request(),u=new URL(request.url()),p=u.pathname,method=request.method();
  const send=(json,status=200,headers={})=>route.fulfill({status,json,headers});
  if(p==='/api/xac-thuc/lam-moi-token'||p==='/api/xac-thuc/dang-nhap')return send({accessToken:'synthetic-hb',expiresIn:900});
  if(p==='/api/xac-thuc/toi')return send({accountId:'synthetic',username:'SYN-HB',functions:[]});
  if(p==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return send({student:state.sv,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(p===api+'/toi/nhom-chuc-nang')return send({student:state.sv,specialist:!state.sv});
  if(state.sv)return send({code:'FORBIDDEN'},403);
  if(p===api+'/hoc-ky-nguon/mac-dinh')return send({semester:term,reason:null});
  if(p===api+'/hoc-ky-nguon/21')return send(term);
  if(p===api+'/hoc-ky-nguon')return send({items:[term],page:0,size:20,totalElements:1});
  if(p===api+'/kkht/pham-vi-nguon'){
   if(state.catalogStatus!==200)return send({code:'SCOPE_SOURCE_UNAVAILABLE'},state.catalogStatus);
   const page=Number(u.searchParams.get('page')),size=Number(u.searchParams.get('size'));
   return send({catalogRevision:state.catalogRevision,items:groups.slice(page*size,(page+1)*size).map(g=>state.nullLabels&&g.cohortId==='102'?{...g,cohortCode:null,majorCode:null,majorName:null}:g),page,size,totalElements:groups.length});
  }
  if(p.startsWith(api+'/kkht/lenh-tao-dot/'))return !state.lookup404&&state.row&&p.endsWith(state.key)?send({requestId:state.key,roundId:state.row.id}):send({code:'NOT_FOUND'},404);
  if(p===api+'/kkht/dot-xet'&&method==='GET')return send({items:state.row?[{...state.row,semesterId:'501',academicYear:'2026-2027',semesterOrder:1}]:[],page:0,size:20,totalElements:state.row?1:0});
  if(p===api+'/kkht/dot-xet'&&method==='POST'){
   const body=request.postDataJSON(),replayed=!!state.row&&state.key===request.headers()['idempotency-key'];state.post.push({body,key:request.headers()['idempotency-key']});state.key=request.headers()['idempotency-key'];
   assert.deepEqual(Object.keys(body).sort(),['catalogRevision','scope','sourceSemesterId']);assert.match(state.key,/^[0-9a-f-]{36}$/);
   if(state.reject)return send({code:state.reject},409);
   if(replayed&&!state.lost)return send(state.row,200,{ETag:state.etag,'Idempotency-Replayed':'true',Location:api+'/kkht/dot-xet/1001'});
   state.row=structuredClone(initial);state.scope={id:'601',revision:1,mode:body.scope.mode,catalogRevision:state.catalogRevision,groups:body.scope.mode==='all'?groups:groups.filter(g=>body.scope.groups.some(v=>v.cohortId===g.cohortId&&v.majorId===g.majorId)),createdAt:'2026-11-23T00:00:00Z',createdBy:'301'};
   if(state.lost)return route.abort('failed');
   return send(state.row,201,{ETag:state.etag,'Idempotency-Replayed':'false',Location:api+'/kkht/dot-xet/1001'});
  }
  if(p===api+'/kkht/dot-xet/1001/pham-vi')return send({roundId:'1001',roundEtag:state.etag,scope:state.legacy?null:state.scope});
  if(p===api+'/kkht/dot-xet/1001'&&method==='PUT'){
   state.puts.push({body:request.postDataJSON(),tag:request.headers()['if-match']});
   if(state.reject)return send({code:state.reject},state.reject==='VERSION_CONFLICT'?412:409);
   const body=request.postDataJSON();state.etag='"two"';state.scope={...state.scope,id:'602',revision:2,mode:body.scope.mode,catalogRevision:body.catalogRevision,groups:body.scope.mode==='all'?groups:groups.filter(g=>body.scope.groups.some(v=>v.cohortId===g.cohortId&&v.majorId===g.majorId))};return send(state.row,200,{ETag:state.etag});
  }
  if(p===api+'/kkht/dot-xet/1001')return state.readbackFail?send({code:'DEPENDENCY_UNAVAILABLE'},503):send(state.row,200,{ETag:state.etag});
  if(p==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  return send({code:'NOT_FOUND'},404);
 });
 const open=async()=>{await page.goto(origin+url);await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();};
 try{await run({page,state,open});assert.deepEqual(state.errors,[]);passed++;console.log(name+' PASS (HTTP STUB)');await context.close();}
 catch(e){console.error(name+' FAIL: '+e.message);console.error(await page.locator('body').innerText());console.error(state.errors);throw e;} // Keep failed context until investigation ends.
}
try{
 await check('T028-create-groups-pages',async({page,state,open})=>{
  await open();await expect(page.getByRole('group',{name:'Phạm vi sinh viên'})).toBeVisible();
  await expect(page.getByLabel('Tên đợt',{exact:true})).toHaveCount(0);await page.getByLabel('Chọn nhóm Khóa + Ngành').check();
  await page.getByLabel('Lọc theo khóa').selectOption('101');await page.getByRole('button',{name:'Chọn tất cả nhóm đang lọc',exact:true}).click();
  await expect(page.getByText('Đã chọn 22 nhóm',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Trang nhóm sau'}).click();
  await expect(page.getByRole('checkbox',{name:/Ngành 21/})).toBeChecked();
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();
  assert.equal(state.post.length,1);assert.equal(state.post[0].body.scope.groups.length,22);await expect(page.getByRole('heading',{name:'Phạm vi đã lưu'})).toBeVisible();
 });
 await check('T028-lost-response-key-recovery',async({page,state,open})=>{
  state.lost=true;await open();await page.getByLabel('Toàn bộ nhóm trong học kỳ').check();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'})).toBeVisible();await page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'}).click();
  await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();assert.equal(state.post.length,1);
 });
 await check('T028-source-stale-preserves-selection',async({page,state,open})=>{
  await open();await page.getByLabel('Chọn nhóm Khóa + Ngành').check();await page.getByRole('checkbox',{name:/Ngành 0$/}).check();state.reject='SCOPE_SOURCE_CHANGED';
  await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByRole('checkbox',{name:/Ngành 0$/})).toBeChecked();
  await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();await page.getByRole('button',{name:'Đọc lại danh mục nhóm'}).click();
  await expect(page.getByRole('checkbox',{name:/Ngành 0$/})).toBeChecked();assert.equal(state.post.length,1);
 });
 await check('T028-edit-conflict-readback',async({page,state})=>{
  state.row=structuredClone(initial);state.scope={id:'601',revision:1,mode:'all',catalogRevision:revision,groups,createdAt:'2026-11-23T00:00:00Z',createdBy:'301'};
  await page.goto(origin+url+'&dotXetId=1001');await page.getByRole('button',{name:'Sửa phạm vi'}).click();await page.getByLabel('Chọn nhóm Khóa + Ngành').check();
  state.reject='VERSION_CONFLICT';await page.getByRole('button',{name:'Lưu phạm vi'}).click();await expect(page.getByRole('button',{name:'Đọc bản mới để đối chiếu'})).toBeVisible();
  await page.getByRole('button',{name:'Đọc bản mới để đối chiếu'}).click();await page.getByRole('button',{name:'Giữ lựa chọn và dùng bản mới'}).click();state.reject=null;
  await page.getByRole('button',{name:'Lưu phạm vi'}).click();await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();
  assert.equal(state.puts.length,2);assert.deepEqual(Object.keys(state.puts[1].body).sort(),['catalogRevision','scope']);await expect(page.getByText('Phiên phạm vi 2',{exact:true})).toBeVisible();
 });
 await check('T028-legacy-readonly',async({page,state})=>{
  state.row=structuredClone(initial);state.legacy=true;await page.goto(origin+url+'&dotXetId=1001');await expect(page.getByText(/Đợt cũ chưa có phạm vi/)).toBeVisible();await expect(page.getByRole('button',{name:'Sửa phạm vi'})).toHaveCount(0);
 });
 await check('T028-source-error-not-empty',async({page,state,open})=>{state.catalogStatus=503;await open();await expect(page.getByRole('button',{name:'Đọc lại danh mục nhóm'})).toBeVisible();await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();});
 await check('T028-student-direct-route',async({page,state})=>{state.sv=true;await page.goto(origin+url);await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();assert.equal(state.post.length,0);});
 await check('T028-mobile-scope',async({page,open})=>{await page.setViewportSize({width:360,height:800});await open();await page.getByLabel('Chọn nhóm Khóa + Ngành').check();await expect(page.getByRole('checkbox',{name:/Ngành 0$/})).toBeVisible();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));});
 await check('T028-unknown-leave-reopen',async({page,state,open})=>{
  state.lost=true;await open();await page.getByLabel('Toàn bộ nhóm trong học kỳ').check();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'})).toBeVisible();const key=state.key;
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'})).toBeVisible();await page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'}).click();
  await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();assert.equal(state.post.length,1);assert.equal(state.key,key);
 });
 await check('T028-null-group-labels',async({page,state,open})=>{
  state.nullLabels=true;await open();await page.getByLabel('Chọn nhóm Khóa + Ngành').check();await page.getByLabel('Lọc theo khóa').selectOption('102');
  await expect(page.getByRole('checkbox',{name:'Khóa chưa có mã · Ngành chưa có tên',exact:true})).toBeVisible();await page.getByRole('button',{name:'Chọn tất cả nhóm đang lọc',exact:true}).click();await expect(page.getByText('Đã chọn 1 nhóm',{exact:true})).toBeVisible();
 });
 await check('T028-retry-same-intent-after-lookup404',async({page,state,open})=>{
  state.lost=true;state.lookup404=true;await open();await page.getByLabel('Toàn bộ nhóm trong học kỳ').check();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await page.getByRole('button',{name:'Kiểm tra kết quả tạo đợt'}).click();await expect(page.getByRole('alert')).toContainText('Chưa thấy yêu cầu đã hoàn tất');assert.equal(state.post.length,1);
  state.lost=false;await page.getByRole('button',{name:'Gửi lại cùng yêu cầu'}).click();await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();assert.equal(state.post.length,2);assert.deepEqual(state.post[0],state.post[1]);
 });
 await check('T028-saved-readback-error-no-new-post',async({page,state,open})=>{
  state.readbackFail=true;await open();await page.getByLabel('Toàn bộ nhóm trong học kỳ').check();await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await expect(page.getByRole('button',{name:'Đọc lại kết quả',exact:true})).toBeVisible();assert.equal(state.post.length,1);state.readbackFail=false;
  await page.getByRole('button',{name:'Đọc lại kết quả',exact:true}).click();await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();assert.equal(state.post.length,1);
 });
 await check('T028-scope-locked-after-use',async({page,state})=>{
  state.row=structuredClone(initial);state.scope={id:'601',revision:1,mode:'all',catalogRevision:revision,groups,createdAt:'2026-11-23T00:00:00Z',createdBy:'301'};
  await page.goto(origin+url+'&dotXetId=1001');await page.getByRole('button',{name:'Sửa phạm vi'}).click();await page.getByLabel('Chọn nhóm Khóa + Ngành').check();state.reject='SCOPE_LOCKED';
  await page.getByRole('button',{name:'Lưu phạm vi'}).click();await expect(page.getByRole('alert')).toContainText('Phạm vi không thể sửa');await page.getByRole('button',{name:'Đọc bản mới để đối chiếu'}).click();
  await expect(page.getByRole('button',{name:'Giữ lựa chọn và dùng bản mới'})).toHaveCount(0);await page.getByRole('button',{name:'Mở bản đang lưu'}).click();await expect(page.getByRole('heading',{name:initial.name,exact:true})).toBeVisible();assert.equal(state.puts.length,1);assert.equal(state.scope.revision,1);
 });
 console.log('T02.8 '+passed+' cases passed (HTTP STUB)');
}finally{await browser.close();}
