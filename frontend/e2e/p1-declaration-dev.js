// Stateful Playwright MCP entry. Synthetic-only, ctsv_dev guard must pass first.
// Keep the context after any failure. NEVER repeat an upload/submit without reading state.
async (page)=>{
 const x=page.__p1,s=x?.accounts?.student;if(!s)throw Error('Student context required');
 const assert=(value,message)=>{if(!value)throw Error(message);};
 if(x.declarationPhase==='prepare'){
  await s.goto('https://127.0.0.1:5173/mghp/ho-so?dot_xet_id=20040');await s.getByRole('combobox',{name:'Lớp',exact:true}).waitFor();
  for(const [label,value] of [['Họ tên','Sinh viên thử giao diện P1'],['CCCD','000000000001'],['Nơi sinh','Đà Nẵng'],['Giới tính','Nam'],['Địa chỉ thường trú','Địa chỉ synthetic DEV P1'],['Số điện thoại','0900000001']])await s.getByRole('textbox',{name:label,exact:true}).fill(value);
  await s.getByLabel('Ngày sinh',{exact:true}).fill('2005-01-01');
  for(const label of ['Dân tộc','Lớp','Khóa học']){const select=s.getByRole('combobox',{name:label,exact:true});await select.locator('option[value="1"]').waitFor({state:'attached'});await select.selectOption('1');}
  // Owner's synthetic process3, unused by existing DEV roots10024/10025/10027.
  await s.getByRole('combobox',{name:'Quá trình học',exact:true}).selectOption('3');
  await s.getByRole('combobox',{name:'Đang học văn bằng thứ nhất',exact:true}).selectOption('true');await s.getByRole('combobox',{name:'Đang hưởng lương khi đi học',exact:true}).selectOption('false');
  await s.getByRole('button',{name:'Tiếp tục',exact:true}).click();await s.getByRole('button',{name:'Thêm người liên quan',exact:true}).click();
  assert(await s.getByRole('textbox',{name:'Mã cán bộ đối chiếu',exact:true}).count()===0,'Internal staff code must not be a student input');
  await s.getByRole('button',{name:'Bỏ người liên quan 1',exact:true}).click();assert(await s.getByText('Người liên quan 1',{exact:true}).count()===0,'Remove person failed');
  await s.getByRole('button',{name:'Tiếp tục',exact:true}).click();assert(await s.getByLabel(/^Tải PDF cho/).count()===3,'Per-requirement upload controls missing');
  x.cases.push({id:'P1-03-DECLARATION_PICKERS_AND_PEOPLE',status:'PASS',database:'ctsv_dev'});
 }
 if(x.declarationPhase==='upload'){
  if(x.upload)throw Error('An upload already exists. Inspect selections instead of uploading twice.');
  const first=s.getByLabel(/^Tải PDF cho/).first();
  await first.setInputFiles({name:'sai-dinh-dang.txt',mimeType:'text/plain',buffer:Buffer.from('SYNTHETIC P1')});await s.getByRole('alert').filter({hasText:'Chỉ chấp nhận tệp PDF'}).waitFor();
  const pending=s.waitForResponse(r=>r.url().endsWith('/api/ctsv/tep/minh-chung')&&r.request().method()==='POST');
  await first.setInputFiles('D:/7rd_semester/pbl6_src/nghiepvu/05-du-lieu-mau/du lieu test.pdf');const response=await pending;const value=await response.json();
  assert(response.status()===201,'REAL upload failed: '+response.status());x.upload={id:value.id,fileName:value.fileName,sha256:value.sha256,size:value.size};
  const selections=s.getByRole('combobox',{name:/^PDF cho /});assert(await selections.first().inputValue()===value.id,'Upload not bound to clicked requirement');
  for(let i=1;i<await selections.count();i++)await selections.nth(i).selectOption(value.id);
  for(let i=0;i<await selections.count();i++)assert(await selections.nth(i).inputValue()===value.id,'Shared file binding mismatch');
  x.cases.push({id:'P1-04-REAL_PDF_UPLOAD_SHARED_BINDING',status:'PASS',database:'ctsv_dev',storage:'Cloudinary REAL',fileId:value.id});
 }
 if(x.declarationPhase==='submit'){
  if(x.createdApplication)throw Error('Already submitted. Read the recorded root instead of resubmitting.');
  await s.getByRole('checkbox',{name:'Tôi cam kết lời khai đúng sự thật',exact:true}).check();
  const pending=s.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so'&&r.request().method()==='POST');await s.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click();const response=await pending;const result=await response.json();
  assert(response.status()===201,'Synthetic submission failed: '+response.status()+' '+(result.code??''));x.createdApplication=String(result.applicationId??result.id??'');
  await s.getByRole('heading',{name:'Thông tin đã kê khai',exact:true}).waitFor();assert((await s.locator('main').innerText()).includes('Sinh viên thử giao diện P1'),'Saved declaration missing');
  x.cases.push({id:'P1-04-REAL_SUBMISSION',status:'PASS',database:'ctsv_dev',applicationId:x.createdApplication});
 }
 return {phase:x.declarationPhase,cases:x.cases,upload:x.upload,createdApplication:x.createdApplication};
}
