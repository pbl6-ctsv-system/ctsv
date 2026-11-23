// Real DEV smoke after explicit account/scope provisioning. No business writes, migration or fixtures.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
const origin=process.env.SHCN_DEV_ORIGIN??'https://127.0.0.1:5173';
const output=process.env.SHCN_DEV_EVIDENCE;
if(!output||!process.env.SHCN_DEV_SPECIALIST_PASSWORD||!process.env.SHCN_DEV_STUDENT_PASSWORD)throw new Error('DEV credentials/evidence must be supplied in process memory');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const checks=[];
async function login(page,name,password){await page.goto(origin+'/');await page.getByLabel('Tên đăng nhập',{exact:true}).fill(name);await page.getByLabel('Mật khẩu',{exact:true}).fill(password);const signed=page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/dang-nhap')&&r.request().method()==='POST');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await expect(page.getByRole('button',{name:'Đăng xuất',exact:true})).toBeVisible();return (await (await signed).json()).accessToken;}
try{
 const specialist=await browser.newContext();const page=await specialist.newPage();page.setDefaultTimeout(15000);
 await login(page,'dev.chuyenvien',process.env.SHCN_DEV_SPECIALIST_PASSWORD);
 const nav=page.getByRole('button',{name:'Sinh hoạt chủ nhiệm',exact:true});await expect(nav).toBeVisible();await nav.click();
 await page.getByRole('link',{name:'Quản lý đợt SHCN',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Quản lý đợt SHCN',exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'Tạo đợt SHCN',exact:true})).toBeEnabled();
 checks.push('Specialist sees SHCN navigation and can open round management with real backend/global grant');
 await page.screenshot({path:output+'/specialist-shcn-menu.png',fullPage:true});
 await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await expect(page.getByLabel('Tên đợt *',{exact:true})).toBeVisible();
 checks.push('Specialist can open draft creation form; no POST round submitted');
 await specialist.close();
 const student=await browser.newContext();const p=await student.newPage();p.setDefaultTimeout(15000);
 const studentToken=await login(p,'dev.sinhvien',process.env.SHCN_DEV_STUDENT_PASSWORD);const denied=await p.evaluate(async token=>(await fetch('/api/shcn/dot-sinh-hoat?page=0&size=1',{headers:{Authorization:'Bearer '+token}})).status,studentToken);assert.equal(denied,403);checks.push('Student direct backend round request receives HTTP403');
 await p.goto(origin+'/shcn/dot-sinh-hoat');
 await expect(p.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
 assert.equal(await p.getByRole('heading',{name:'Quản lý đợt SHCN',exact:true}).count(),0);
 checks.push('Student cannot access specialist SHCN round route');await p.getByRole('button',{name:'Đăng xuất',exact:true}).click();await expect(p.getByRole('button',{name:'Đăng nhập',exact:true})).toBeVisible();await student.close();
 await writeFile(output+'/specialist-browser.json',JSON.stringify({checks,passed:checks.length,browser:'Chrome REAL',api:'REAL HTTPS DEV',database:'DEV2022/ctsv_dev REAL',businessWrites:0,migrationExecuted:false},null,2));
 console.log(JSON.stringify({passed:checks.length,api:'REAL HTTPS DEV',businessWrites:0,migrationExecuted:false}));
}catch(e){console.error('SHCN DEV smoke failed: '+String(e.message).split('\n')[0]);process.exitCode=1;}finally{await browser.close();}
