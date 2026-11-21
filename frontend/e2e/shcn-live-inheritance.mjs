// Real DEV verification. No business POST/PUT/DELETE; the user creates their inherited draft.
import {chromium,expect} from '@playwright/test';
import {mkdir,writeFile} from 'node:fs/promises';
const password=process.env.SHCN_DEV_PASSWORD;delete process.env.SHCN_DEV_PASSWORD;
const sourceId=process.env.SHCN_INHERIT_SOURCE_ID;
if(!password||!sourceId)throw new Error('Protected DEV password and owned fixture identity required.');
const origin=process.env.SHCN_DEV_ORIGIN??'https://127.0.0.1:5173',evidence=process.env.SHCN_UI_EVIDENCE;
const browser=await chromium.launch({channel:'chrome',headless:true});const context=await browser.newContext();const page=await context.newPage();
const checks=[];
try{
 await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill('dev.truongkhoa');await page.getByLabel('Mật khẩu',{exact:true}).fill(password);
 const login=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 if((await login).status()!==200)throw new Error('DEV login failed; do not retry automatically.');
 await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();checks.push('Real head login');
 await page.goto(origin+'/shcn/phan-cong-gvcn');await page.getByLabel('Năm học',{exact:true}).fill('2026-2027');await page.getByRole('combobox',{name:'Năm học',exact:true}).press('Escape');
 await page.getByRole('button',{name:'Tạo phân công GVCN',exact:true}).click();const dialog=page.getByRole('dialog');
 await dialog.getByRole('combobox',{name:'Cách tạo',exact:true}).selectOption('inherit');await expect(dialog.getByLabel('Năm học trước',{exact:true})).toHaveValue('2025-2026');
 const request=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/shcn/khoa/phan-cong'&&new URL(r.url()).searchParams.get('namHoc')==='2025-2026');
 await dialog.getByRole('button',{name:'Tìm phân công năm trước'}).click();const response=await request;if(response.status()!==200)throw new Error('Source list unavailable.');
 const data=await response.json(),index=data.items.findIndex(r=>r.id===sourceId);if(index<0||data.items[index].trangThai!=='DA_GUI_CHUYEN_VIEN'||data.items[index].soLop!==2)throw new Error('Fixture source status/count mismatch.');
 checks.push('Real API returns owned 2025-2026 source with sent status and two assignments');
 await dialog.getByRole('radio').nth(index).check();await dialog.getByLabel('Bắt đầu đề nghị hiệu lực (giờ Việt Nam)').fill('2026-08-01T00:00');await expect(dialog.getByLabel('Lý do điều chỉnh/kế thừa')).not.toHaveAttribute('required');await expect(dialog.getByLabel('Lý do điều chỉnh/kế thừa')).toHaveValue('');
 await expect(dialog.getByRole('radio').nth(index)).toBeChecked();await expect(dialog.getByRole('button',{name:'Tạo phân công GVCN',exact:true})).toBeEnabled();checks.push('User can select source and complete inheritance form; no draft submitted');
 if(evidence){await mkdir(evidence,{recursive:true});await page.screenshot({path:evidence+'/inheritance-ready.png',fullPage:true});}
 await dialog.getByRole('button',{name:'Đóng hộp thoại'}).click();await page.goto(origin+'/shcn/phan-cong-gvcn?danhSachId='+encodeURIComponent(sourceId));
 await expect(page.getByRole('heading',{name:'Danh sách phân công',exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Toàn bộ phân công'}).getByText('DEV-SHCN-LOP-A1',{exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Toàn bộ phân công'}).getByText('DEV-SHCN-LOP-A2',{exact:true})).toBeVisible();
 if(await page.getByRole('button',{name:'Lưu nháp',exact:true}).count())throw new Error('Closed source must remain read-only.');checks.push('Real source detail shows both class/teacher rows and stays immutable');
 const report={mode:'Real DEV Auth, SHCN API and SQL; browser reads only; no migrations',sourceId,checks};if(evidence)await writeFile(evidence+'/inheritance-browser.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}finally{await context.close();await browser.close();}
