import {chromium,expect} from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.TIMELINE_BROWSER_HOME;if(!home)throw Error('TIMELINE_BROWSER_HOME required');
const cfg=JSON.parse(await fs.readFile(path.join(home,'credentials.json'),'utf8'));
assert.equal(cfg.database,'ctsv_timeline_test_20261120');
const report={database:cfg.database,origin:cfg.origin,backend:'REAL HTTPS Spring Boot',sql:'REAL SQL Server',externalStorage:'NOT USED',cases:[],pageErrors:[]};
const browser=await chromium.launch({channel:'chrome',headless:true}),contexts=[];
async function login(role){const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000},timezoneId:'America/New_York'});contexts.push(context);const page=await context.newPage();page.on('pageerror',e=>report.pageErrors.push(e.message));await page.goto(cfg.origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg[role]);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();return page;}
async function capture(page,method,suffix,status,action){const [r]=await Promise.all([page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix)),action()]);assert.equal(r.status(),status,method+' '+suffix);return suffix==='/dot-xet'?await r.json():null;}
async function check(id,action){try{await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){report.cases.push({id,status:'FAIL'});throw e;}}
async function chooseRound(page){await page.getByLabel('Lọc học kỳ',{exact:true}).selectOption(cfg.semesterId);await page.getByRole('row').filter({hasText:roundName}).getByRole('button',{name:'Xem đợt',exact:true}).click();}
let staff,roundId,roundName='Lịch trình hỗ trợ học phí — thử nghiệm '+Date.now();
try{
 await check('TL-B01_CREATE_PLAN_VIETNAM_TIME',async()=>{
  staff=await login('specialist');await staff.goto(cfg.origin+'/mghp/dot-xet');await staff.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await staff.getByLabel('Mã',{exact:true}).fill('TL-BROWSER-'+Date.now());await staff.getByLabel('Tên',{exact:true}).fill(roundName);await staff.getByLabel('Học kỳ',{exact:true}).selectOption(cfg.semesterId);
  await staff.getByText('Lịch tiếp nhận',{exact:true}).click();
  await staff.getByLabel('Mở nhận lúc',{exact:true}).fill('2026-11-19T09:00');await staff.getByLabel('Hạn nộp',{exact:true}).fill('2026-11-21T09:00');await staff.getByLabel('Hạn rà soát',{exact:true}).fill('2026-11-25T09:00');
  await staff.getByText('Lịch trình dự kiến · giờ Việt Nam',{exact:true}).click();await staff.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true}).fill('2026-11-26');await staff.getByLabel('Bắt đầu ký dự kiến',{exact:true}).fill('2026-11-26T09:00');await staff.getByLabel('Hoàn tất ký dự kiến',{exact:true}).fill('2026-11-27T09:00');
  const select=staff.getByLabel('Chính sách thêm vào đợt',{exact:true});const policyId=await select.locator('option').filter({hasText:/^M2 ·/}).getAttribute('value');await select.selectOption(policyId);await staff.getByLabel('Phiên bản thêm vào đợt',{exact:true}).selectOption(cfg.policyVersionId);await staff.getByRole('button',{name:'Thêm chính sách vào đợt',exact:true}).click();
  const created=await capture(staff,'POST','/dot-xet',201,()=>staff.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());roundId=created.id;
  assert.equal(created.opensAt,'2026-11-19T02:00:00Z');assert.equal(created.schedule.councilPlannedDate,'2026-11-26');await staff.getByRole('heading',{name:'Lịch trình & tiến độ',exact:true}).waitFor();
 });
 await check('TL-B02_OPEN_EXTEND_HISTORY_AND_STALE_ERROR',async()=>{
  await capture(staff,'POST','/'+roundId+'/mo',200,()=>staff.getByRole('button',{name:'Công khai đợt',exact:true}).click());
  const panel=staff.getByRole('region',{name:'Lịch trình đợt xét',exact:true});await panel.getByText('Gia hạn nhận hồ sơ',{exact:true}).click();
  await panel.getByLabel('Hạn nộp mới',{exact:true}).fill('2026-11-22T09:00');await panel.getByLabel('Lý do gia hạn',{exact:true}).fill('Gia hạn thử nghiệm một ngày, không tự dịch lịch khác.');
  await capture(staff,'POST','/'+roundId+'/gia-han-nhan-ho-so',200,()=>panel.getByRole('button',{name:'Xác nhận gia hạn',exact:true}).click());
  await panel.getByText('Điều chỉnh lịch dự kiến',{exact:true}).click();await panel.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true}).fill('2026-11-27');await panel.getByLabel('Lý do thay đổi lịch',{exact:true}).fill('Chuyển ngày họp thử nghiệm.');await expect(panel.getByLabel('Lý do gia hạn',{exact:true})).toHaveValue('');
  await capture(staff,'PUT','/'+roundId+'/lich-trinh',200,()=>panel.getByRole('button',{name:'Lưu lịch trình',exact:true}).click());
  await panel.getByText('Lịch sử thay đổi lịch',{exact:true}).click();await expect(panel.locator('article').getByText('Chuyển ngày họp thử nghiệm.',{exact:true})).toBeVisible();
  // A real second tab changes the round; the first tab retains a stale strong tag.
  const other=await staff.context().newPage();await other.goto(cfg.origin+'/mghp/dot-xet');await chooseRound(other);
  const op=other.getByRole('region',{name:'Lịch trình đợt xét',exact:true});await op.getByText('Điều chỉnh lịch dự kiến',{exact:true}).click();await op.getByLabel('Lý do thay đổi lịch',{exact:true}).fill('Điều chỉnh từ tab khác.');await op.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true}).fill('2026-11-28');await capture(other,'PUT','/'+roundId+'/lich-trinh',200,()=>op.getByRole('button',{name:'Lưu lịch trình',exact:true}).click());await other.close();
  await panel.getByLabel('Lý do thay đổi lịch',{exact:true}).fill('Giữ nội dung nhập khi stale.');await panel.getByRole('button',{name:'Lưu lịch trình',exact:true}).scrollIntoViewIfNeeded();await capture(staff,'PUT','/'+roundId+'/lich-trinh',412,()=>panel.getByRole('button',{name:'Lưu lịch trình',exact:true}).click());
  await expect(staff.getByRole('alert').filter({hasText:'Dữ liệu đã thay đổi'}).first()).toBeVisible();await expect(panel.getByLabel('Lý do thay đổi lịch',{exact:true})).toHaveValue('Giữ nội dung nhập khi stale.');
  await staff.getByRole('button',{name:'Đọc lại dữ liệu',exact:true}).click();
 });
 await check('TL-B03_CLOSE_REVIEW_REFRESH_AND_MOBILE',async()=>{
  await staff.getByLabel('Lý do thao tác',{exact:true}).fill('Kết thúc nhận hồ sơ thử nghiệm.');await capture(staff,'POST','/'+roundId+'/dong-nhan-ho-so',200,()=>staff.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).click());
  const panel=staff.getByRole('region',{name:'Lịch trình đợt xét',exact:true});await panel.getByRole('button',{name:'Kiểm tra trước khi đóng xét',exact:true}).click();await panel.getByLabel('Lý do đóng xét',{exact:true}).fill('Đã kiểm tra tập nguồn thử nghiệm rỗng.');await panel.getByRole('checkbox').check();await capture(staff,'POST','/'+roundId+'/dong-xet',200,()=>panel.getByRole('button',{name:'Đóng xét',exact:true}).click());await expect(panel.getByText(/Đã đóng xét lúc/)).toBeVisible();
  await staff.reload();await chooseRound(staff);await expect(staff.getByText(/Đã đóng xét lúc/)).toBeVisible();await staff.setViewportSize({width:360,height:850});assert.equal(await staff.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await staff.screenshot({path:path.join(home,'timeline-mobile.png'),fullPage:true});
 });
 await check('TL-B04_STUDENT_AND_AUTH_ADMIN_PRIVATE_UI_DENIED',async()=>{
  for(const role of ['student','admin']){const p=await login(role);await p.goto(cfg.origin+'/mghp/dot-xet');if(role==='student'){await chooseRound(p);await expect(p.getByRole('region',{name:'Lịch trình đợt xét',exact:true})).toHaveCount(0);}else{await expect(p.getByText(/không có quyền/i).first()).toBeVisible();}await expect(p.getByRole('button',{name:'Lưu lịch trình',exact:true})).toHaveCount(0);await p.getByRole('button',{name:'Đăng xuất',exact:true}).click();await p.getByRole('button',{name:'Đăng nhập',exact:true}).waitFor();}
 });
 assert.deepEqual(report.pageErrors,[]);await fs.writeFile(path.join(home,'browser.done'),'PASS');
}catch(error){report.failure={name:error.name,message:error.message};if(process.env.TIMELINE_BROWSER_FINAL_FAILURE==='true')await fs.writeFile(path.join(home,'browser.done'),'FAIL');throw error;}
finally{const result=JSON.stringify(report,null,2);await fs.writeFile(path.join(home,'report-'+(process.env.TIMELINE_BROWSER_ATTEMPT??'1')+'.json'),result);await fs.writeFile(path.join(home,'report.json'),result);for(const c of contexts)await c.close();await browser.close();}
