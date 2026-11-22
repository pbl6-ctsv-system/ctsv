// Real DEV browser check. Password arrives only through the parent process environment.
import {chromium,expect} from '@playwright/test';
import {mkdir,writeFile} from 'node:fs/promises';
const password=process.env.SHCN_DEV_PASSWORD;delete process.env.SHCN_DEV_PASSWORD;
if(!password)throw new Error('Protected local DEV password must be provided by the launcher.');
const origin=process.env.SHCN_DEV_ORIGIN??'https://127.0.0.1:5173';
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext({ignoreHTTPSErrors:false});const page=await context.newPage();
const checks=[];const evidence=process.env.SHCN_UI_EVIDENCE;
try{
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill('dev.truongkhoa');
 await page.getByLabel('Mật khẩu',{exact:true}).fill(password);
 const login=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 if((await login).status()!==200)throw new Error('DEV login failed; no automatic retry.');
 checks.push('Real head login succeeds');
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 const group=page.getByRole('button',{name:/Sinh hoạt chủ nhiệm/i});
 await group.click();
 await page.getByRole('link',{name:'Phân công GVCN',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Phân công GVCN',exact:true})).toBeVisible();
 checks.push('Verified SHCN menu opens real page');
 await page.getByLabel('Năm học',{exact:true}).fill('DEV-SHCN-2026');
 await page.getByRole('button',{name:'Xem danh sách',exact:true}).click();
 await page.getByRole('button',{name:'Mở',exact:true}).first().click();
 await expect(page.getByRole('heading',{name:/Phân công GVCN — Phiên bản/})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Phân công theo ngành',exact:true})).toBeVisible();
 checks.push('Real DEV list and draft load, write controls available');

 const programs=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/shcn/khoa/chuong-trinh');
 await page.getByRole('combobox',{name:'Ngành',exact:true}).click();
 if((await programs).status()!==200)throw new Error('Real program lookup did not succeed.');
 const classes=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/shcn/khoa/lop');
 await page.getByRole('option').first().click();
 if((await classes).status()!==200)throw new Error('Real class lookup did not succeed.');
 await expect(page.getByRole('heading',{name:'Lớp thuộc ngành đang chọn',exact:true})).toBeVisible();
 const picker=page.getByRole('combobox',{name:/^GVCN lớp /}).first();await expect(picker).toBeVisible();
 checks.push('Real scoped program and class lookup renders unassigned classes');
 await picker.scrollIntoViewIfNeeded();
 const lecturers=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/shcn/khoa/giang-vien');await picker.click();
 const result=await lecturers;
 if(result.status()!==200||new URL(result.url()).searchParams.has('chuongTrinhId'))throw new Error('Faculty lecturer lookup contract mismatch.');
 await expect(page.getByRole('option').first()).toBeVisible();
 if(evidence){await mkdir(evidence,{recursive:true});await page.screenshot({path:evidence+'/live-industry-picker.png',fullPage:true});}
 await picker.press('Escape');checks.push('Real inline teacher lookup uses faculty scope without industry filter');
 if(evidence){
  await page.screenshot({path:evidence+'/live-desktop.png',fullPage:true});
  await page.setViewportSize({width:360,height:800});await page.getByRole('heading',{name:'Phân công theo ngành',exact:true}).scrollIntoViewIfNeeded();
  await page.screenshot({path:evidence+'/live-mobile.png',fullPage:true});
  if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))throw new Error('Mobile viewport overflows.');
  checks.push('Real DEV mobile editor fits 360px viewport');
  await writeFile(evidence+'/live-menu.json',JSON.stringify({mode:'Real browser, Auth, SHCN API and existing DEV SQL; read-only business actions; no migrations',checks},null,2));
 }
 console.log(JSON.stringify({checks}));
}finally{await context.close();await browser.close();}
