// Playwright MCP browser_run_code_unsafe filename; reuses the current authenticated
// page. No credentials/storage state are written and no business writes occur here.
async (page) => {
 const report={database:'ctsv_dev',origin:'https://127.0.0.1:5173',cases:[]};
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/toi/ngu-canh-ke-khai'));
 await page.goto(report.origin+'/mghp/ho-so?dot_xet_id=20037');
 const response=await pending;if(response.status()!==200)throw Error('HS01 must return200');
 const context=await response.json();await page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true}).waitFor();
 for(const name of ['Đơn vị quản lý','ID học kỳ đề nghị, cách nhau bằng dấu phẩy','Đã hưởng ở cùng trình độ'])if(await page.getByLabel(name).count())throw Error('Technical input remains: '+name);
 report.cases.push({id:'SF01_HIDDEN_FIELDS',status:'PASS'});
 const policy=page.getByRole('combobox',{name:'Bạn thuộc diện miễn giảm nào?',exact:true});
 if(await policy.locator('option').count()!==context.policyVersions.length+1)throw Error('Missing policy options');
 if(context.policyVersions.length>1&&await policy.inputValue()!=='')throw Error('Unexpected default policy');
 await policy.selectOption(context.policyVersions[0].id);
 if(await policy.inputValue()!==context.policyVersions[0].id)throw Error('Policy choice not preserved');
 report.cases.push({id:'SF02_POLICY_SELECTION',status:'PASS'});
 const process=page.getByRole('combobox',{name:'Quá trình học',exact:true});
 if(await process.count()!==1)throw Error('Missing study process dropdown');
 if(await process.locator('option').count()!==context.studyProcesses.length+1)throw Error('Missing study process options');
 if(context.studyProcesses.length>1&&await process.inputValue()!=='')throw Error('Do not silently select a process');
 if(!await process.evaluate(e=>e.required))throw Error('Study process must be required');
 await page.getByLabel('Địa chỉ thường trú',{exact:true}).fill('Địa chỉ thực hành chọn quá trình học — SYNTHETIC');
 if(context.studyProcesses.length>1){await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();const alert=page.getByRole('alert').filter({hasText:'Quá trình học'});await alert.waitFor();const bounds=await alert.boundingBox();if(!bounds||bounds.y<0||bounds.y+bounds.height>page.viewportSize().height)throw Error('Validation outside viewport');}
 await process.selectOption(context.studyProcesses[0].id);
 if(await process.inputValue()!==context.studyProcesses[0].id)throw Error('Process choice not preserved');
 if(await page.getByRole('button',{name:'Tiếp tục',exact:true}).isDisabled())throw Error('Process selection still blocked');
 if(await page.getByRole('alert').count())throw Error('Corrected process still has an error');
 await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();await page.getByRole('group',{name:'Người liên quan',exact:true}).waitFor();
 await page.getByRole('button',{name:'Quay lại',exact:true}).click();if(await process.inputValue()!==context.studyProcesses[0].id)throw Error('Process lost on back navigation');
 report.cases.push({id:'SF03_SELECT_STUDY_PROCESS',status:'PASS'});
 await page.setViewportSize({width:360,height:800});if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))throw Error('Mobile horizontal overflow');
 await page.screenshot({path:'D:/7rd_semester/pbl6_src/ctsv/target/student-form-simple-mobile.png',fullPage:true});
 report.cases.push({id:'SF04_MOBILE',status:'PASS'});
 await page.setViewportSize({width:1440,height:1000});await page.goto(report.origin+'/mghp/tiep-nhan-ho-so');await page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
 report.cases.push({id:'SF05_STUDENT_FORBIDDEN_STAFF_ROUTE',status:'PASS'});
 return report;
}
