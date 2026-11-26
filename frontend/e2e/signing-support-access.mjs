import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'../../target/signing-support-dev');
await fs.mkdir(path.join(root,'screenshots'),{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const results=[];
try{
 for(const role of ['CV','SV']){
  const username=process.env['SUPPORT_'+role+'_USER'],password=process.env['SUPPORT_'+role+'_PASSWORD'];
  assert.ok(username&&password,'Credentials must be supplied only in memory from local DEV configuration');
  const context=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1280,height:900}});const page=await context.newPage();let authorization='';
  page.on('request',r=>{const value=r.headers().authorization;if(value)authorization=value;});
  await page.goto('https://127.0.0.1:5173');await page.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(username);await page.getByRole('textbox',{name:'Mật khẩu',exact:true}).fill(password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
  if(role==='CV'){
   await page.getByRole('button',{name:'Miễn giảm học phí',exact:true}).click();await page.getByRole('link',{name:'Tài liệu ký số MGHP',exact:true}).click();
   const row=page.getByRole('row').filter({hasText:'SUPPORT-DEV-20261125'});await row.waitFor();assert.match(await row.innerText(),/Cần xử lý thêm/);await row.getByRole('button',{name:'Xem tài liệu',exact:true}).click();await page.getByRole('heading',{name:/SUPPORT-DEV-20261125/}).waitFor();
   assert.match(await page.locator('main').innerText(),/bộ phận quản trị/);assert.doesNotMatch(await page.locator('main').innerText(),/CA_DOWNLOAD_UNAVAILABLE|Kiểm tra & phục hồi|Chưa xác định kết quả ký/);
   assert.equal(await page.getByRole('button',{name:'Ký số',exact:true}).count(),0);
   await page.screenshot({path:path.join(root,'screenshots','08-signer-needs-support.png'),fullPage:true});results.push({case:'CV public support message, no technical diagnosis or recovery/sign',status:'PASS'});
  }
  const apiStatus=await page.evaluate(async({authorization})=>(await fetch('/api/ctsv/mghp/quan-tri-ky/su-co',{headers:{Authorization:authorization}})).status,{authorization});assert.equal(apiStatus,403);
  await page.goto('https://127.0.0.1:5173/quan-tri/ky-so/su-co');await page.getByRole('alert').filter({hasText:'Bạn không có quyền truy cập trang này.'}).waitFor();assert.equal(await page.getByRole('heading',{name:'Xử lý sự cố ký số',exact:true}).count(),0);
  await page.screenshot({path:path.join(root,'screenshots','09-'+role.toLowerCase()+'-admin-denied.png'),fullPage:true});results.push({case:role+' direct Admin URL denied + backend403',status:'PASS'});
  await context.close();
 }
}finally{await browser.close();}
await fs.writeFile(path.join(root,'access-browser-results.json'),JSON.stringify({environment:'DEV launcher https://127.0.0.1:5173 → Spring8443 → localhost\\DEV2022/ctsv_dev V32',mockedBusinessApi:false,results},null,2));
console.log(JSON.stringify({passed:results.length,failed:0,results}));
