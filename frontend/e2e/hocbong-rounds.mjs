// Controlled HTTP fixtures: browser/UI acceptance only, no SQL or business writes.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';

const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173';
const browser=await chromium.launch({channel:'chrome',headless:true});
const routePath='/hoc-bong/kkht/dot-xet';
const api='/api/ctsv/hoc-bong';
const cases=[];
const rows=Array.from({length:21},(_,i)=>({id:i===0?'9007199254740993':String(1001+i),sourceSemesterId:'21',semesterId:i===20?'502':'501',academicYear:i===20?'2024-2025':'2025-2026',semesterOrder:2,number:i+1,name:`Đợt thực hành KKHT ${i+1}`,status:'draft'}));
const roundDetail=row=>({id:row.id,sourceSemesterId:row.sourceSemesterId,number:row.number,name:row.name,status:row.status,createdBy:'301',semester:{id:row.semesterId,academicYear:row.academicYear,semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null});

async function check(id,run){
 if(process.env.CTSV_HB_CASE&&!id.includes(process.env.CTSV_HB_CASE))return;
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});
 const page=await context.newPage();
 page.setDefaultTimeout(7000);
 const state={groups:{student:false,specialist:true},groupStatus:200,legacyStatus:200,holdGroups:null,holdList:null,listStatus:200,detailStatus:200,records:[...rows],calls:[],cancelled:[],errors:[],slowId:null,releaseSlow:null};
 page.on('pageerror',error=>state.errors.push(error.message));
 page.on('requestfailed',request=>{const url=new URL(request.url());if(url.pathname.startsWith(api+'/'))state.cancelled.push({path:url.pathname,query:Object.fromEntries(url.searchParams)});});
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const request=route.request(),url=new URL(request.url()),pathname=url.pathname;
  if(['/api/xac-thuc/dang-nhap','/api/xac-thuc/lam-moi-token'].includes(pathname))return route.fulfill({json:{accessToken:'synthetic-browser-token',expiresIn:900}});
  if(pathname==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  if(pathname==='/api/xac-thuc/toi')return route.fulfill({json:{accountId:'synthetic-hb-account',username:'SYNTHETIC-HB',functions:[]}});
  if(pathname==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return route.fulfill({status:state.legacyStatus,json:state.legacyStatus===200?{student:state.groups?.student===true,mghpSpecialist:false,ctsvRoleAdmin:false}:{code:state.legacyStatus===403?'FORBIDDEN':'DEPENDENCY_UNAVAILABLE'}});
  if(pathname===api+'/toi/nhom-chuc-nang'){
   if(state.holdGroups)await state.holdGroups;
   return route.fulfill({status:state.groupStatus,json:state.groupStatus===200?state.groups:{code:state.groupStatus===401?'UNAUTHENTICATED':'DEPENDENCY_UNAVAILABLE'}}).catch(()=>{});
  }
  if(pathname.startsWith(api+'/')){
   state.calls.push({method:request.method(),path:pathname,query:Object.fromEntries(url.searchParams)});
   assert.equal(request.method(),'GET','T02.1 must not mutate scholarship data');
   if(pathname===api+'/kkht/dot-xet'){
    if(state.holdList)await state.holdList;
    if(state.listStatus!==200)return route.fulfill({status:state.listStatus,json:{code:state.listStatus===403?'FORBIDDEN':'DEPENDENCY_UNAVAILABLE'}});
    let items=state.records;
    if(url.searchParams.has('number'))items=items.filter(row=>row.number===Number(url.searchParams.get('number')));
    if(url.searchParams.has('semesterId'))items=items.filter(row=>row.semesterId===url.searchParams.get('semesterId'));
    const p=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);
    return route.fulfill({json:{items:items.slice(p*size,(p+1)*size),page:p,size,totalElements:items.length}}).catch(()=>{});
   }
   const row=state.records.find(row=>pathname===api+'/kkht/dot-xet/'+row.id);
   const status=state.detailStatus!==200?state.detailStatus:row?200:404;
   if(row?.id===state.slowId)await new Promise(resolve=>{state.releaseSlow=resolve;});
   return route.fulfill({status,headers:{ETag:'"AAAAAAAAAAE="'},json:status===200?roundDetail(row):{code:status===404?'NOT_FOUND':status===403?'FORBIDDEN':'DEPENDENCY_UNAVAILABLE'}}).catch(()=>{});
  }
  throw new Error('Unexpected fixture request '+pathname);
 });
 async function open(url=routePath){
  await page.goto(origin+url);
  await expect(page.getByRole('heading',{name:'Đợt xét học bổng KKHT',exact:true})).toBeVisible();
 }
 try{
  await run({page,state,open,context});
  assert.deepEqual(state.errors,[],'No uncaught page errors');
  cases.push({id,status:'PASS'});
  console.log(id+' PASS (STUB)');
 }catch(error){
  cases.push({id,status:'FAIL',error:error.message});
  throw error;
 }finally{state.releaseSlow?.();await context.close();}
}

try{
 await check('HB-T021-B01-list-detail',async({page,state,open})=>{
  await open();
  await page.getByRole('button',{name:'Học bổng',exact:true}).click();
  await expect(page.getByRole('link',{name:'Đợt xét KKHT',exact:true})).toBeVisible();
  await expect(page.getByText('21 đợt xét',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Xem chi tiết Đợt thực hành KKHT 1',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Đợt thực hành KKHT 1',exact:true})).toBeVisible();
  await expect(page.getByText('16/01/2026',{exact:true})).toBeVisible();
  await expect(page.getByText('Chưa cấu hình',{exact:true}).first()).toBeVisible();
  await expect(page.getByRole('button',{name:'Sửa đợt xét',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:/Tạo đợt|Mở đợt/})).toHaveCount(0);
  assert(state.calls.some(call=>call.path.endsWith('/9007199254740993')));
 });
 await check('HB-T021-B02-student-denied',async({page,state})=>{
  state.groups={student:true,specialist:false};
  await page.goto(origin+routePath+'?dotXetId=9007199254740993');
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Học bổng',exact:true})).toHaveCount(0);
  assert.equal(state.calls.length,0);
 });
 await check('HB-T021-B03-wait-groups-combined',async({page,state})=>{
  state.groups={student:true,specialist:true};
  let release;
  state.holdGroups=new Promise(resolve=>{release=resolve;});
  await page.goto(origin+routePath);
  await expect(page.getByText('Đang tải quyền CTSV…',{exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Đợt xét học bổng KKHT',exact:true})).toHaveCount(0);
  assert.equal(state.calls.length,0);
  release();
  await expect(page.getByRole('heading',{name:'Đợt xét học bổng KKHT',exact:true})).toBeVisible();
 });
 await check('HB-T021-B04-groups-unavailable',async({page,state})=>{
  state.groupStatus=503;
  await page.goto(origin+routePath);
  await expect(page.getByText('Chưa tải được quyền Học bổng. Hãy tải lại trang để thử lại.',{exact:true})).toBeVisible();
  assert.equal(state.calls.length,0);
 });
 await check('HB-T021-B05-revocation',async({page,state,open})=>{
  await open();
  await expect(page.getByText('Đợt thực hành KKHT 1',{exact:true})).toBeVisible();
  state.groups.specialist=false;
  await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByText('Đợt thực hành KKHT 1',{exact:true})).toHaveCount(0);
 });
 await check('HB-T021-B06-empty-error-retry',async({page,state,open})=>{
  state.records=[];
  await open();
  await expect(page.getByText('Không có đợt xét phù hợp.',{exact:true})).toBeVisible();
  state.listStatus=503;
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Chưa thể kiểm tra trạng thái bảo mật');
  await expect(page.getByText('Không có đợt xét phù hợp.',{exact:true})).toHaveCount(0);
  state.listStatus=200;state.records=[rows[0]];
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
 });
 await check('HB-T021-B07-pagination-filter-back',async({page,state,open})=>{
  await open();
  await page.getByRole('button',{name:'Trang sau',exact:true}).click();
  await expect(page.getByText(rows[20].name,{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Xem chi tiết '+rows[20].name,exact:true}).click();
  await expect(page.getByRole('heading',{name:rows[20].name,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await expect(page.getByText('Trang 2 / 2',{exact:true})).toBeVisible();
  await page.getByLabel('Số đợt',{exact:true}).fill('1');
  await page.getByRole('button',{name:'Lọc danh sách',exact:true}).click();
  await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Lọc học kỳ 2 năm học 2025-2026',exact:true}).click();
  await expect(page.getByText('Học kỳ 2 · 2025-2026',{exact:true})).toBeVisible();
  assert(state.calls.some(call=>call.query.semesterId==='501'&&call.query.number==='1'&&call.query.page==='0'));
 });
 await check('HB-T021-B08-detail-errors-stale-response',async({page,state,open})=>{
  await open();
  state.detailStatus=404;
  await page.getByRole('button',{name:'Xem chi tiết '+rows[0].name,exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Không tìm thấy dữ liệu');
  state.detailStatus=200;
  await page.getByRole('button',{name:'Đọc lại chi tiết',exact:true}).click();
  await expect(page.getByRole('heading',{name:rows[0].name,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  state.slowId=rows[0].id;
  await page.getByRole('button',{name:'Xem chi tiết '+rows[0].name,exact:true}).click();
  await expect(page.getByText('Đang tải chi tiết đợt xét…',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await page.getByRole('button',{name:'Xem chi tiết '+rows[1].name,exact:true}).click();
  await expect(page.getByRole('heading',{name:rows[1].name,exact:true})).toBeVisible();
  await expect.poll(()=>state.cancelled.some(call=>call.path.endsWith('/'+rows[0].id))).toBe(true);
  state.releaseSlow?.();
  await expect(page.getByRole('heading',{name:rows[0].name,exact:true})).toHaveCount(0);
 });
 await check('HB-T021-B09-reload-keyboard-responsive',async({page,open})=>{
  await open(routePath+'?dotXetId='+rows[0].id);
  await expect(page.getByRole('heading',{name:rows[0].name,exact:true})).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading',{name:rows[0].name,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
  await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  const button=page.getByRole('button',{name:'Xem chi tiết '+rows[0].name,exact:true});
  await button.focus();await page.keyboard.press('Enter');
  await expect(page.getByRole('heading',{name:rows[0].name,exact:true})).toBeVisible();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
 });
 await check('HB-T021-B10-independent-legacy-permissions',async({page,state,open})=>{
  state.legacyStatus=403;
  await open();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
  state.legacyStatus=503;
  await page.reload();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
 });
 await check('HB-T021-B11-malformed-groups',async({page,state})=>{
  state.groups={student:false,specialist:'true'};
  await page.goto(origin+routePath);
  await expect(page.getByText('Chưa tải được quyền Học bổng. Hãy tải lại trang để thử lại.',{exact:true})).toBeVisible();
  assert.equal(state.calls.length,0);
 });
 await check('HB-T021-B12-read-forbidden-clears-data',async({page,state,open})=>{
  await open();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
  state.listStatus=403;
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Bạn không có quyền truy cập các đợt xét KKHT');
  await expect(page.getByText(rows[0].name,{exact:true})).toHaveCount(0);
 });
 await check('HB-T021-B13-deadline-restores-retry',async({page,state,open})=>{
  await open();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
  await page.evaluate(()=>{const timeout=AbortSignal.timeout.bind(AbortSignal);AbortSignal.timeout=ms=>timeout(ms===20000?100:ms);});
  let release;
  state.holdList=new Promise(resolve=>{release=resolve;});
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.');
  await expect(page.getByRole('button',{name:'Đọc lại danh sách',exact:true})).toBeEnabled();
  state.holdList=null;release();
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByText(rows[0].name,{exact:true})).toBeVisible();
 });
 await check('HB-T021-B14-slow-list-cannot-replace-filter',async({page,state,open})=>{
  await open();
  await expect(page.getByText('21 đợt xét',{exact:true})).toBeVisible();
  let release;
  state.holdList=new Promise(resolve=>{release=resolve;});
  await page.getByRole('button',{name:'Đọc lại danh sách',exact:true}).click();
  await expect(page.getByText('Đang tải danh sách đợt xét…',{exact:true})).toBeVisible();
  state.holdList=null;
  await page.getByLabel('Số đợt',{exact:true}).fill('2');
  await page.getByRole('button',{name:'Lọc danh sách',exact:true}).click();
  await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  await expect.poll(()=>state.cancelled.some(call=>call.path.endsWith('/dot-xet')&&!call.query.number)).toBe(true);
  release();
  await expect(page.getByText(rows[1].name,{exact:true})).toBeVisible();
  await expect(page.getByText(rows[0].name,{exact:true})).toHaveCount(0);
 });
}finally{
 console.log(`HB browser STUB: ${cases.filter(item=>item.status==='PASS').length} PASS, ${cases.filter(item=>item.status==='FAIL').length} FAIL`);
 await browser.close();
}
