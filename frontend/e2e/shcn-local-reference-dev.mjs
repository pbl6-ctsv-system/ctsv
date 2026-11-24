import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
const origin='https://127.0.0.1:5173',output=process.env.SHCN_DEV_EVIDENCE;
if(!output||!process.env.SHCN_DEV_SPECIALIST_PASSWORD||!process.env.SHCN_DEV_STUDENT_PASSWORD)throw Error('Supply credentials in process memory');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[],errors=[],facultyRequests=[];
async function login(page,user,password){
 await page.goto(origin+'/');await page.getByLabel('Tên đăng nhập',{exact:true}).fill(user);await page.getByLabel('Mật khẩu',{exact:true}).fill(password);
 const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap'&&r.request().method()==='POST');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();const signed=await response;assert.equal(signed.status(),200);
 await expect(page.getByRole('button',{name:'Đăng xuất',exact:true})).toBeVisible({timeout:40000});return (await signed.json()).accessToken;
}
try{
 const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(40000);
 page.on('request',r=>{if(new URL(r.url()).pathname==='/api/shcn/khoa/pham-vi')facultyRequests.push(r.method());});
 page.on('pageerror',e=>errors.push(e.message));
 await login(page,'dev.chuyenvien',process.env.SHCN_DEV_SPECIALIST_PASSWORD);
 await page.getByRole('button',{name:'Sinh hoạt chủ nhiệm',exact:true}).click();await page.getByRole('link',{name:'Quản lý đợt SHCN',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Quản lý đợt SHCN',exact:true})).toBeVisible();checks.push('Specialist navigation and round page');
 await page.getByRole('button',{name:'Tạo đợt SHCN',exact:true}).click();await expect(page.getByLabel('Tên đợt *',{exact:true})).toBeVisible();
 const loaded=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/shcn/tra-cuu/hoc-ky'&&r.status()===200);
 await page.getByRole('combobox',{name:'Học kỳ *',exact:true}).click();const semesters=await (await loaded).json();assert.equal(semesters.totalElements,20);checks.push('Semester lookup HTTP200 returns 20 CSV source rows');
 const option=page.getByRole('option').first();await expect(option).toBeVisible();const selected=await option.innerText();await option.click();
 await expect(page.getByRole('combobox',{name:'Học kỳ *',exact:true})).toHaveValue(selected);checks.push('Creation form can select actual imported semester');
 assert.equal(facultyRequests.length,0);checks.push('Specialist login/navigation does not call faculty scope API');
 await page.screenshot({path:output+'/specialist-semester.png',fullPage:true});assert.deepEqual(errors,[]);
 await context.close();
 const studentContext=await browser.newContext();const student=await studentContext.newPage();student.setDefaultTimeout(40000);
 const token=await login(student,'dev.sinhvien',process.env.SHCN_DEV_STUDENT_PASSWORD);
 const denied=await student.evaluate(async token=>(await fetch('/api/shcn/khoa/pham-vi',{headers:{Authorization:'Bearer '+token}})).status,token);assert.equal(denied,403);checks.push('Backend still rejects faculty scope access without permission');
 await student.goto(origin+'/shcn/dot-sinh-hoat');await expect(student.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();checks.push('Student direct specialist route remains forbidden');await studentContext.close();
 await writeFile(output+'/result.json',JSON.stringify({date:'2026-11-23',passed:checks.length,checks,semesterCount:20,unsolicitedFacultyRequests:facultyRequests.length,api:'REAL HTTPS DEV',database:'DEV2022/ctsv_dev REAL',roundBusinessWrites:0,migrationExecuted:false},null,2));
 console.log(JSON.stringify({passed:checks.length,semesterCount:20,unsolicitedFacultyRequests:facultyRequests.length}));
}catch(e){console.error('DEV verification failed: '+String(e.message).split('\n')[0]);process.exitCode=1;}finally{await browser.close();}
