import {chromium} from '@playwright/test';
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {openNav} from './navigation.mjs';
const home=process.env.P08_BROWSER_HOME;
if(!home)throw new Error('P08_BROWSER_HOME required');
const cfg=JSON.parse(await readFile(path.join(home,'credentials.json'),'utf8'));
assert.equal(cfg.database,'ctsv_p08_test_20261118');
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[],report={database:cfg.database,origin:cfg.origin,http:'REAL',sql:'SQL_SERVER_REAL',storage:'STUB fixture bytes, no storage request in UI',cases:[],pageErrors:[]};
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(20000);page.on('pageerror',e=>report.pageErrors.push({name:e.name,message:e.message,stack:e.stack}));
 await page.goto(cfg.origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg[role]);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/dang-nhap'));await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();assert.equal((await pending).status(),200);
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();return page;
}
async function capture(page,method,suffix,expected,action,body=false){
 report.lastAction={method,suffix,expected};
 const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix)),action()]);assert.equal(response.status(),expected,method+' '+suffix);return body?response.json():null;
}
async function check(id,action){try{await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(error){report.cases.push({id,status:'FAIL'});throw error;}}
let staff,evaluation;
try{
 await check('P08-B01_CREATE_EDIT_CONFIRM_UNVERIFIED',async()=>{
  staff=await login('staff');await openNav(staff,'Đánh giá hồ sơ');
  await staff.getByRole('button',{name:'Chọn hồ sơ '+cfg.applicationId,exact:true}).click();
  await staff.getByRole('button',{name:'Chuẩn bị đánh giá',exact:true}).click();
  await staff.locator('label').filter({hasText:/^Kết luận/}).locator('select').selectOption('can_xac_minh');
  await staff.getByLabel('Lý do nhận định',{exact:true}).fill('Thực hành: chờ đối chiếu minh chứng trước khi kết luận.');
  const criterion=staff.locator('div').filter({has:staff.locator('strong').filter({hasText:'SYNTHETIC đối chiếu điều kiện'})}).filter({has:staff.getByLabel('Nhận xét tiêu chí',{exact:true})}).last();
  await staff.getByLabel('Nhận xét tiêu chí',{exact:true}).fill('Chờ xác minh nội dung giấy.');
  await staff.getByRole('combobox',{name:'Kết quả',exact:true}).nth(1).selectOption('can_xac_minh');
  await staff.getByRole('button',{name:'Thêm giấy làm căn cứ',exact:true}).click();
  await staff.getByLabel('Lý do dùng giấy',{exact:true}).fill('Giấy đã nhận, chưa xác minh nội dung.');
  evaluation=await capture(staff,'POST','/danh-gia',201,()=>staff.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).click(),true);
  await staff.getByRole('status').getByText('Đã lưu đánh giá và đọc lại dữ liệu mới.',{exact:true}).waitFor();
  await staff.getByLabel('Lý do nhận định',{exact:true}).fill('Thực hành: đã cập nhật nhận định cần xác minh.');
  await capture(staff,'PUT','/'+evaluation.id,200,()=>staff.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).click());
  await staff.getByRole('status').getByText('Đã lưu đánh giá và đọc lại dữ liệu mới.',{exact:true}).waitFor();
  await staff.getByLabel('Lý do thao tác đánh giá',{exact:true}).fill('Xác nhận nhận định cần xác minh, chưa cấp quyền hưởng.');
  await capture(staff,'POST','/xac-nhan',200,()=>staff.getByRole('button',{name:'Xác nhận đánh giá',exact:true}).click());
  await staff.getByText('Đã xác nhận',{exact:true}).waitFor();
  assert.equal(await staff.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).count(),0);
  await staff.screenshot({path:path.join(home,'evaluation-desktop.png'),fullPage:true});
 });
 await check('P08-B02_REVISION_REFRESH_MOBILE',async()=>{
  await staff.getByLabel('Lý do thao tác đánh giá',{exact:true}).fill('Có thêm nguồn cần kiểm tra ở bản kế tiếp.');
  const next=await capture(staff,'POST','/phien-ban',201,()=>staff.getByRole('button',{name:'Tạo bản đánh giá lại',exact:true}).click(),true);
  assert.equal(next.previousEvaluationId,evaluation.id);assert.equal(next.revision,2);
  await staff.getByText('Bản nháp',{exact:true}).waitFor();
  await staff.reload();await staff.getByLabel('Mã đánh giá cần đọc',{exact:true}).fill(next.id);
  await staff.getByRole('button',{name:'Đọc đánh giá',exact:true}).click();await staff.getByText('Bản nháp',{exact:true}).waitFor();
  await staff.setViewportSize({width:360,height:800});assert(await staff.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await staff.screenshot({path:path.join(home,'evaluation-mobile.png'),fullPage:true});
  await staff.setViewportSize({width:1440,height:1000});
 });
 await check('P08-B03_REVIEW_LIST_AND_FORBIDDEN',async()=>{
  await openNav(staff,'Rà soát quyền hưởng');await staff.getByLabel('Học kỳ rà soát',{exact:true}).selectOption(cfg.semesterId);
  await capture(staff,'GET','/can-ra-soat',200,()=>staff.getByRole('button',{name:'Xem quyền cần rà soát',exact:true}).click());
  await staff.getByRole('button',{name:'Xem quyền '+cfg.benefitId,exact:true}).click();
  await staff.getByText('Ghi nhận rà soát',{exact:true}).click();
  await staff.getByLabel('Lý do và căn cứ rà soát',{exact:true}).fill('Thực hành: chờ xác minh chu kỳ giấy của quyền mẫu.');
  await capture(staff,'POST','/ghi-nhan-ra-soat',201,()=>staff.getByRole('button',{name:'Lưu quan sát rà soát',exact:true}).click());
  await staff.getByRole('status').getByText('Đã ghi nhận rà soát. Mức hưởng và các mốc công nhận được giữ nguyên.',{exact:true}).waitFor();
  for(const role of ['student','admin']){
   const page=await login(role);
   if(role==='student'){
    await openNav(page,'Quyền hưởng của tôi');await page.getByLabel('Mã quyền hưởng cần xem',{exact:true}).fill(cfg.benefitId);
    await page.getByRole('button',{name:'Đọc quyền hưởng',exact:true}).click();await page.getByText('Lịch sử quyền hưởng',{exact:true}).click();
    assert.equal(await page.getByText('Thực hành: chờ xác minh chu kỳ giấy của quyền mẫu.',{exact:true}).count(),0);
    assert.equal(await page.getByRole('button',{name:'Lưu quan sát rà soát',exact:true}).count(),0);
   }
   await page.goto(cfg.origin+'/mghp/danh-gia');await page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).count(),0);
  }
  assert.equal(report.pageErrors.length,0);
 });
}catch(error){report.error={name:error.name,message:String(error.message).replaceAll(cfg.password,'[REDACTED]')};if(staff){await staff.screenshot({path:path.join(home,'failure.png'),fullPage:true}).catch(()=>{});report.visibleText=(await staff.locator('body').innerText()).slice(-2500);}process.exitCode=1;console.error('P08 browser failed, see sanitized report.');}
finally{for(const context of contexts)await context.close();await browser.close();report.status=report.cases.length===3&&report.cases.every(c=>c.status==='PASS')?'PASS':'FAIL';await writeFile(path.join(home,'browser-report.json'),JSON.stringify(report,null,2));await writeFile(path.join(home,'browser.done'),report.status);}
