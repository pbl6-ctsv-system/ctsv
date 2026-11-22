import {chromium,expect} from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const mode=process.argv[2]??'verify',home=path.resolve('target/round-form-dev-20261121');await fs.mkdir(home,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});const page=await context.newPage();
const report={database:'ctsv_dev',origin:'https://127.0.0.1:5173',mode,cases:[],pageErrors:[]};page.on('pageerror',e=>report.pageErrors.push(e.message));
async function check(id,fn){await fn();report.cases.push({id,status:'PASS'});await page.screenshot({path:path.join(home,id+'.png'),fullPage:true});console.log(id+' PASS');}
async function capture(method,suffix,status,fn){const[r]=await Promise.all([page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix)),fn()]);assert.equal(r.status(),status,method+' '+suffix);return r.json();}
async function expand(text){const s=page.getByText(text,{exact:true});if(!await s.evaluate(e=>e.closest('details').open))await s.click();}
const wall=days=>new Date(Date.now()+days*86400000+7*3600000).toISOString().slice(0,16);
async function findRound(row){
 const loaded=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname.endsWith('/dot-xet')&&new URL(r.url()).searchParams.get('semesterId')===row.semesterId);
 await page.getByLabel('Lọc học kỳ',{exact:true}).selectOption(row.semesterId);await loaded;await expect(page.getByText('Đang tải hoặc xử lý…',{exact:true})).toHaveCount(0);
 for(let p=0;p<30;p++){
  const target=page.getByRole('row').filter({hasText:row.code}).getByRole('button',{name:'Xem đợt',exact:true});if(await target.count()){await target.click();return;}
  const next=page.getByRole('button',{name:'Trang sau',exact:true});await expect(next).toBeEnabled();await capture('GET','/dot-xet',200,()=>next.click());await expect(page.getByText('Đang tải hoặc xử lý…',{exact:true})).toHaveCount(0);
 }throw Error('Owned round not found in paginated list');
}
try{
 await page.goto(report.origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(process.env.RF_SPECIALIST_USER);
 const signIn=async()=>{await page.getByLabel('Mật khẩu',{exact:true}).fill(process.env.RF_SPECIALIST_PASSWORD);const[r]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap'&&r.request().method()==='POST'),page.getByRole('button',{name:'Đăng nhập',exact:true}).click()]);return r;};
 let response=await signIn();if(response.status()===429){const seconds=Number(response.headers()['retry-after']);assert.ok(seconds>0&&seconds<=900);console.log('Respecting Auth Retry-After '+seconds+'s');for(let left=seconds+1;left>0;left-=Math.min(left,30))await new Promise(resolve=>setTimeout(resolve,Math.min(left,30)*1000));response=await signIn();}assert.equal(response.status(),200);await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 await page.goto(report.origin+'/mghp/dot-xet');await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(page.getByLabel('Lọc học kỳ',{exact:true})).toHaveCount(0);await expect(page.getByRole('table',{name:'Chính sách áp dụng trong đợt'})).toBeVisible();
 if(mode==='mobile'){await expect(page.getByText('Đang tải phiên bản chính sách…',{exact:true})).toHaveCount(0);await expect(page.getByRole('table',{name:'Chính sách áp dụng trong đợt'}).getByLabel('Áp dụng M1',{exact:true})).toBeEnabled();await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(home,'mobile-create-form.png'),fullPage:true});report.cases.push({id:'MOBILE_VISUAL',status:'PASS'});}
 else if(mode==='red')report.cases.push({id:'FORM_VISIBLE',status:'PASS'});
 else{
 await context.tracing.start({snapshots:true,screenshots:true,sources:false});
 const table=page.getByRole('table',{name:'Chính sách áp dụng trong đợt'});let row;
 await check('RF01_LAYOUT',async()=>{
  await expect(page.getByLabel('Học kỳ',{exact:true})).toBeVisible();await expect(page.getByLabel('schedule',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Chế độ kỳ trước',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Thay đổi giữa kỳ',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Hướng dẫn kê khai',{exact:true})).toHaveCount(0);await expect(page.getByLabel('ID tệp mẫu đơn đã sẵn sàng',{exact:false})).toHaveCount(0);await expect(page.getByRole('region',{name:'Lịch trình đợt xét cấu hình',exact:true}).getByText('Lịch tiếp nhận',{exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Lịch trình đợt xét cấu hình',exact:true}).getByText('Lịch trình dự kiến · giờ Việt Nam',{exact:true})).toBeVisible();
 });
 await check('RF07_VERSION_REQUIRED_FOR_CHECKED_GROUP',async()=>{
  await table.getByLabel('Áp dụng M2',{exact:true}).check();const select=table.getByLabel('Phiên bản M2',{exact:true});assert.ok(await select.locator('option').count()>2,'DEV fixture M2 has multiple locked versions');await expect(select).toHaveValue('');await expect(page.getByRole('button',{name:'Lưu cấu hình',exact:true})).toBeDisabled();await expect(page.getByText('Chọn đủ phiên bản hợp lệ cho các nhóm đã đánh dấu trước khi lưu.',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Bỏ chọn tất cả',exact:true}).click();
 });
 await check('RF02_MULTI_SELECT_AND_NULL_SCHEDULE',async()=>{
  await page.getByLabel('Mã',{exact:true}).fill('FORM-DEV-'+Date.now());await page.getByLabel('Tên',{exact:true}).fill('Thực hành chọn nhiều nhóm MGHP — SYNTHETIC');const semester=page.getByLabel('Học kỳ',{exact:true});await semester.selectOption(await semester.locator('option').evaluateAll(options=>options.find(x=>x.value)?.value));
  await page.getByRole('button',{name:'Chọn tất cả nhóm có phiên bản',exact:true}).click();assert.ok(await table.getByRole('checkbox',{checked:true}).count()>=2);await page.getByRole('button',{name:'Bỏ chọn tất cả',exact:true}).click();await expect(table.getByRole('checkbox',{checked:true})).toHaveCount(0);
  for(const code of ['M1','M2']){await table.getByLabel('Áp dụng '+code,{exact:true}).check();const select=table.getByLabel('Phiên bản '+code,{exact:true});await select.selectOption(await select.locator('option').evaluateAll(options=>options.find(x=>x.value)?.value));}
  await expand('Lịch trình dự kiến · giờ Việt Nam');await expect(page.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true})).not.toHaveAttribute('required','');
  row=await capture('POST','/dot-xet',201,()=>page.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());report.round={id:row.id,code:row.code,semesterId:row.semesterId,policyVersions:row.policyVersions};assert.equal(row.policyVersions.length,2);assert.ok(Object.values(row.schedule).every(x=>x===null));assert.equal(row.retroactiveMode,'theo_phap_luat');assert.equal(row.midSemesterMode,'khoang_ngay_chuyen_vien_xac_nhan');assert.equal(Object.hasOwn(row,'declarationGuidance'),false);assert.equal(Object.hasOwn(row,'lateSubmissionGuidance'),false);
 });
 await check('RF03_REFRESH_AND_EDIT',async()=>{
  await page.reload();await findRound(row);await expect(table.getByLabel('Áp dụng M1',{exact:true})).toBeChecked();await expect(table.getByLabel('Áp dụng M2',{exact:true})).toBeChecked();await page.getByLabel('Tên',{exact:true}).fill('Thực hành chọn nhiều nhóm MGHP — đã chỉnh sửa');const updated=await capture('PUT','/dot-xet/'+row.id,200,()=>page.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());assert.deepEqual(updated.policyVersions,row.policyVersions);assert.ok(Object.values(updated.schedule).every(x=>x===null));
 });
 await check('RF04_READY_VALIDATION_AND_PUBLISH',async()=>{
  await capture('POST','/'+row.id+'/mo',409,()=>page.getByRole('button',{name:'Công khai đợt',exact:true}).click());await expect(page.getByRole('alert').last()).toBeInViewport();await page.getByRole('button',{name:'Đóng thông báo lỗi',exact:true}).click();await expand('Lịch tiếp nhận');for(const[label,d]of [['Mở nhận lúc',1],['Hạn nộp',7],['Hạn rà soát',15]])await page.getByLabel(label,{exact:true}).fill(wall(d));await capture('PUT','/dot-xet/'+row.id,200,()=>page.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());const opened=await capture('POST','/'+row.id+'/mo',200,()=>page.getByRole('button',{name:'Công khai đợt',exact:true}).click());assert.equal(opened.status,'open');await expect(page.getByRole('button',{name:'Lưu cấu hình',exact:true})).toHaveCount(0);await expect(table.getByRole('checkbox',{checked:true})).toHaveCount(2);
 });
 await check('RF05_MOBILE_AND_LOGOUT',async()=>{await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await expect(table).toBeVisible();await page.screenshot({path:path.join(home,'RF05-mobile-form.png'),fullPage:true});await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();});
 // Stop the specialist trace before filling another account's credentials.
 await context.tracing.stop({path:path.join(home,'round-form-raw-trace.zip')});
 await check('RF06_STUDENT_PUBLIC_AND_PERMISSION',async()=>{
  await page.getByLabel('Tên đăng nhập',{exact:true}).fill(process.env.RF_STUDENT_USER);await page.getByLabel('Mật khẩu',{exact:true}).fill(process.env.RF_STUDENT_PASSWORD);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();await page.goto(report.origin+'/mghp/dot-xet');await findRound(row);await expect(page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Lưu cấu hình',exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:'Minh chứng cần chuẩn bị',exact:true})).toBeVisible();await page.goto(report.origin+'/mghp/chinh-sach');await expect(page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
 });assert.deepEqual(report.pageErrors,[]);
 }
}catch(e){report.error=e.message;await page.screenshot({path:path.join(home,mode+'-failure.png'),fullPage:true});process.exitCode=1;}
finally{await fs.writeFile(path.join(home,mode+'-report.json'),JSON.stringify(report,null,2));await context.close();await browser.close();}
