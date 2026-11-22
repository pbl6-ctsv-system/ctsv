import {chromium,expect} from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.TIMELINE_DEV_SMOKE_HOME;if(!home)throw Error('Explicit owned TIMELINE_DEV_SMOKE_HOME required');
const cfg=JSON.parse(await fs.readFile(path.join(home,'dev-smoke-config.json'),'utf8'));
assert.equal(cfg.database,'ctsv_dev');assert.equal(cfg.origin,'https://127.0.0.1:5173');
if(!process.env.TIMELINE_DEV_PASSWORD)throw Error('DEV password must be supplied locally through environment, never logged');
const browser=await chromium.launch({channel:'chrome',headless:true});const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});
const page=await context.newPage();page.setDefaultTimeout(20000);
const report={database:cfg.database,origin:cfg.origin,backend:'daily dev.ps1 launcher; REAL HTTPS/SQL',externalServices:'NOT USED',cases:[],pageErrors:[],demoRounds:[]};
page.on('pageerror',e=>report.pageErrors.push(e.message));
const wall=days=>new Date(Date.now()+days*86400000+7*3600000).toISOString().slice(0,16);
async function command(method,suffix,status,action){const [r]=await Promise.all([page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix)),action()]);assert.equal(r.status(),status,method+' '+suffix);return suffix==='/dot-xet'?r.json():null;}
async function check(id,action){await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
async function expand(label){const summary=page.getByText(label,{exact:true});if(!await summary.evaluate(e=>e.closest('details').open))await summary.click();}
async function create(name,code){
 await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await page.getByLabel('Mã',{exact:true}).fill(code);await page.getByLabel('Tên',{exact:true}).fill(name);await page.getByLabel('Học kỳ',{exact:true}).selectOption(cfg.semesterId);
 await expand('Lịch tiếp nhận');await page.getByLabel('Mở nhận lúc',{exact:true}).fill(wall(-1));await page.getByLabel('Hạn nộp',{exact:true}).fill(wall(7));await page.getByLabel('Hạn rà soát',{exact:true}).fill(wall(15));
 await expand('Hướng dẫn và chính sách tiếp nhận');
 await page.getByLabel('Hướng dẫn kê khai',{exact:true}).fill('SYNTHETIC DEV — thực hành lịch trình. Chọn đúng chính sách và khai báo trung thực; dữ liệu này không phải quyết định pháp lý.');
 await expand('Lịch trình dự kiến · giờ Việt Nam');
 for(const [label,days] of [['Bổ sung dự kiến từ',7],['Bổ sung dự kiến đến',12],['Bắt đầu rà soát dự kiến',0],['Bắt đầu ký dự kiến',17],['Hoàn tất ký dự kiến',18],['Bắt đầu công bố dự kiến',19],['Hoàn tất công bố dự kiến',20]])await page.getByLabel(label,{exact:true}).fill(wall(days));
 await page.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true}).fill(wall(16).slice(0,10));
 await page.getByLabel('Chính sách thêm vào đợt',{exact:true}).selectOption(cfg.policyId);await page.getByLabel('Phiên bản thêm vào đợt',{exact:true}).selectOption(cfg.policyVersionId);await page.getByRole('button',{name:'Thêm chính sách vào đợt',exact:true}).click();
 const round=await command('POST','/dot-xet',201,()=>page.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());report.demoRounds.push({id:round.id,name:round.name,code:round.code});
 await expect(page.getByRole('heading',{name:'Lịch trình & tiến độ',exact:true})).toBeVisible();return round;
}
try{
 await check('DEV_LOGIN_HOME',async()=>{await page.goto(cfg.origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(process.env.TIMELINE_DEV_USER??'dev.chuyenvien');await page.getByLabel('Mật khẩu',{exact:true}).fill(process.env.TIMELINE_DEV_PASSWORD);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();await page.goto(cfg.origin+'/mghp/dot-xet');});
 const stamp=Date.now().toString().slice(-7);let round;
 await check('DEV_CREATE_OPEN_EXTEND_EDIT_HISTORY',async()=>{
  if(cfg.reuseRound){await page.getByLabel('Lọc học kỳ',{exact:true}).selectOption(cfg.semesterId);await page.getByRole('row').filter({hasText:cfg.reuseRound.code}).getByRole('button',{name:'Xem đợt',exact:true}).click();round=cfg.reuseRound;report.demoRounds.push(round);}else round=await create('Thực hành lịch trình MGHP · Học kỳ I 2026–2027','DEV-LICH-'+stamp);
  if(!cfg.reuseRound)await command('POST','/'+round.id+'/mo',200,()=>page.getByRole('button',{name:'Công khai đợt',exact:true}).click());const panel=page.getByRole('region',{name:'Lịch trình đợt xét',exact:true});
  await panel.getByText('Gia hạn nhận hồ sơ',{exact:true}).click();await panel.getByLabel('Hạn nộp mới',{exact:true}).fill(wall(cfg.reuseRound?9:8));await panel.getByLabel('Lý do gia hạn',{exact:true}).fill('Gia hạn thực hành thêm một ngày; giữ các mốc kế hoạch còn lại.');await command('POST','/'+round.id+'/gia-han-nhan-ho-so',200,()=>panel.getByRole('button',{name:'Xác nhận gia hạn',exact:true}).click());
  await panel.getByText('Điều chỉnh lịch dự kiến',{exact:true}).click();await panel.getByLabel('Ngày họp Hội đồng dự kiến',{exact:true}).fill(wall(17).slice(0,10));await panel.getByLabel('Lý do thay đổi lịch',{exact:true}).fill('Điều chỉnh ngày họp của dữ liệu thực hành DEV.');await command('PUT','/'+round.id+'/lich-trinh',200,()=>panel.getByRole('button',{name:'Lưu lịch trình',exact:true}).click());await panel.getByText('Lịch sử thay đổi lịch',{exact:true}).click();await expect(panel.locator('article').getByText('Điều chỉnh ngày họp của dữ liệu thực hành DEV.',{exact:true})).toBeVisible();
  report.demoRounds[0].finalState='open';
 });
 await check('DEV_CLOSE_REVIEW_SEPARATE_DEMO',async()=>{
  const demo=await create('Minh họa Đóng xét · Không có hồ sơ','DEV-CHOT-'+stamp);await command('POST','/'+demo.id+'/mo',200,()=>page.getByRole('button',{name:'Công khai đợt',exact:true}).click());
  await page.getByLabel('Lý do thao tác',{exact:true}).fill('Chỉ đóng đợt minh họa rỗng, không thay đổi hồ sơ đã có.');await command('POST','/'+demo.id+'/dong-nhan-ho-so',200,()=>page.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).click());
  const panel=page.getByRole('region',{name:'Lịch trình đợt xét',exact:true});await panel.getByRole('button',{name:'Kiểm tra trước khi đóng xét',exact:true}).click();await panel.getByLabel('Lý do đóng xét',{exact:true}).fill('Chốt tập nguồn rỗng cho minh họa thao tác, không tạo quyết định.');await panel.getByRole('checkbox').check();await command('POST','/'+demo.id+'/dong-xet',200,()=>panel.getByRole('button',{name:'Đóng xét',exact:true}).click());await expect(panel.getByText(/Đã đóng xét lúc/)).toBeVisible();report.demoRounds[1].finalState='closed, reviewClosed';
  await page.screenshot({path:path.join(home,'dev-timeline.png'),fullPage:true});
 });
 await check('DEV_REFRESH_LOGOUT',async()=>{await page.reload();await page.getByRole('heading',{name:'Đợt xét miễn giảm học phí',exact:true}).waitFor();await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await page.getByRole('button',{name:'Đăng nhập',exact:true}).waitFor();});
 assert.deepEqual(report.pageErrors,[]);report.status='PASS';
}catch(error){report.status='FAIL';report.failure={name:error.name,message:error.message};throw error;}
finally{await fs.writeFile(path.join(home,'dev-browser-report.json'),JSON.stringify(report,null,2));await context.close();await browser.close();}
