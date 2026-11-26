// Targeted interactive browser checks. DEV fixture only; credentials/tokens/sessions stay in RAM.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process'),{chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/stage-handoff-20261125');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'dev-fixture.json'),'utf8').replace(/^\uFEFF/,''));
const origin='https://127.0.0.1:5173',backend='https://127.0.0.1:8443';
const evidence={database:'ctsv_dev',roundId:fixture.roundId,cases:[],pageErrors:[],pdfs:[],failures:[]};
if(process.env.STAGE_START&&fs.existsSync(path.join(out,'browser.json')))Object.assign(evidence,JSON.parse(fs.readFileSync(path.join(out,'browser.json'),'utf8')));
let browser,context,page,token,accounts;
const pass=(id)=>{evidence.cases=evidence.cases.filter(x=>x.id!==id);evidence.cases.push({id,result:'PASS'});save();};
const save=()=>fs.writeFileSync(path.join(out,'browser.json'),JSON.stringify(evidence,null,2));
function watchPrimary(p){
 p.on('pageerror',error=>evidence.pageErrors.push(error.message));
 // Only the explicit synthetic round's own browser request; no session/storage enumeration.
 p.on('request',request=>{const u=new URL(request.url());if(u.pathname==='/api/ctsv/mghp/ho-so'&&u.searchParams.get('roundId')===''+fixture.roundId){const h=request.headers()['authorization'];if(h?.startsWith('Bearer '))token=h.slice(7);}});
 p.on('response',async response=>{try{const u=new URL(response.url());if(['/api/xac-thuc/dang-nhap','/api/xac-thuc/lam-moi-token'].includes(u.pathname)&&response.status()===200)token=(await response.json()).accessToken;}catch{/* A document navigation may discard a completed response body. No write is retried. */}});
}
async function apiGet(url){
 assert.ok(fixture.applications.some(person=>url==='/api/ctsv/mghp/ho-so/'+person.applicationId)||url==='/api/ctsv/mghp/dot-xet/'+fixture.roundId);
 const response=await context.request.get(backend+url,{headers:{Authorization:'Bearer '+token}});assert.equal(response.status(),200);return response.json();
}
async function signIn(kind){
 const c=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1440,height:1000}}),p=await c.newPage();
 p.on('pageerror',e=>evidence.pageErrors.push(e.message));
 if(kind==='specialist')watchPrimary(p);
 await p.goto(origin);await p.getByLabel('Tên đăng nhập',{exact:true}).fill(accounts[kind].username);await p.getByLabel('Mật khẩu',{exact:true}).fill(accounts[kind].password);
 await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
 accounts[kind]=null;return {context:c,page:p};
}
async function filters(view){
 if(page.url()!==origin+view)await page.goto(origin+view);
 await page.getByRole('heading',{name:view.includes('tiep-nhan')?'Tiếp nhận & bổ sung hồ sơ MGHP':'Đối chiếu bản cứng hồ sơ MGHP',exact:true}).waitFor();
 const back=page.getByRole('button',{name:'Về hồ sơ',exact:true});if(await back.count())await back.click();
 const list=page.getByRole('button',{name:'Về danh sách',exact:true});if(await list.count())await list.click();
 await page.getByRole('combobox',{name:'Học kỳ',exact:true}).selectOption(''+fixture.semesterId);
 const choice=page.getByRole('combobox',{name:'Đợt xét',exact:true});await choice.locator('option[value="'+fixture.roundId+'"]').waitFor({state:'attached'});
 await choice.selectOption(''+fixture.roundId);await page.getByRole('region',{name:view.includes('tiep-nhan')?'Chốt hồ sơ online':'Chốt bản cứng chuyển Hội đồng',exact:true}).waitFor();
}
async function inspect(){
 if(page.url()!==origin+'/mghp/tiep-nhan-ho-so')await page.goto(origin+'/mghp/tiep-nhan-ho-so');await page.getByRole('heading',{name:'Tiếp nhận & bổ sung hồ sơ MGHP',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Chốt hồ sơ online',exact:true}).count(),0);
 pass('UI01-no-seal-without-exact-filters');await filters('/mghp/tiep-nhan-ho-so');
 const panel=page.getByRole('region',{name:'Chốt hồ sơ online',exact:true});await panel.getByText(/2 vướng mắc/).waitFor();
 assert.equal(await panel.getByRole('button',{name:'Chốt hồ sơ online',exact:true}).isDisabled(),true);
 await page.screenshot({path:path.join(out,'01-online-blockers.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:path.join(out,'02-mobile-blockers.png'),fullPage:true});await page.setViewportSize({width:1440,height:1000});pass('UI02-blockers-and-mobile');
 console.log('INSPECT '+JSON.stringify({buttons:await panel.getByRole('button').allTextContents(),rows:await page.getByRole('row').count()}));
}
async function approve(person){
 const before=await apiGet('/api/ctsv/mghp/ho-so/'+person.applicationId);if(before.status==='approved')return;
 assert.equal(before.code,person.code);assert.equal(before.status,'submitted');
 await page.locator('[data-application-id="'+person.applicationId+'"]').click();
 const review=page.locator('#review-pdf-'+person.applicationId);await review.waitFor();
 await review.locator('button[data-review-state]').nth(1).waitFor();
 for(let i=0;i<2;i++){
  const paper=review.locator('button[data-review-state]').nth(i);if(await paper.getAttribute('data-review-state')==='accepted')continue;await paper.click();
  const next=review.getByRole('button',{name:'Đồng ý PDF',exact:true});
  await next.waitFor();
  await page.waitForFunction(file=>{const c=document.querySelector('section[aria-label="PDF đang xem"] canvas');return !c?.hidden&&c?.dataset.renderedFile===''+file&&c.dataset.renderedPage==='1';},before.submittedVersions[0].documents[i].fileId);
  const rendered=await review.locator('canvas').evaluate(c=>({id:c.dataset.renderedFile,body:(()=>{const a=c.getContext('2d').getImageData(0,c.height*.2,c.width,c.height*.65).data;let n=0;for(let k=0;k<a.length;k+=4)if(a[k]<170&&a[k+1]<170&&a[k+2]<170)n++;return n;})()}));assert.ok(rendered.body>500);
  evidence.pdfs.push({fileId:rendered.id,bodyPixels:rendered.body});
  const saved=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/kiem-tra')&&r.request().method()==='POST');
  await next.click();assert.equal((await saved).status(),201);
  await paper.locator('small').getByText('✓ Đã đồng ý PDF',{exact:true}).waitFor();
 }
 await page.getByRole('button',{name:'Duyệt hồ sơ online',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Duyệt hồ sơ online này?',exact:true});
 const posted=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so/'+person.applicationId+'/ket-luan');
 await dialog.getByRole('button',{name:'Xác nhận kết luận',exact:true}).click();assert.equal((await posted).status(),200);await dialog.waitFor({state:'hidden'});
 const after=await apiGet('/api/ctsv/mghp/ho-so/'+person.applicationId);assert.equal(after.onlineStatus,'approved');assert.equal(after.status,'processing');assert.equal(after.evaluationAllowed,false);
 await page.getByRole('button',{name:'Về danh sách',exact:true}).click();
}
async function online(){
 await filters('/mghp/tiep-nhan-ho-so');
 await approve(fixture.applications[0]);const panel=page.getByRole('region',{name:'Chốt hồ sơ online',exact:true});
 await panel.getByText(/1 vướng mắc/).waitFor();assert.equal(await panel.getByRole('button',{name:'Chốt hồ sơ online',exact:true}).isDisabled(),true);pass('UI03-single-pending-blocks');
 await approve(fixture.applications[1]);await panel.getByText(/0 vướng mắc/).waitFor();
 await page.screenshot({path:path.join(out,'03-online-ready.png'),fullPage:true});
 await panel.getByRole('button',{name:'Chốt hồ sơ online',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Chốt hồ sơ online?',exact:true});
 const committed=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/chot-ho-so-online')&&r.request().method()==='POST');
 await dialog.getByRole('button',{name:'Xác nhận chốt',exact:true}).click();assert.equal((await committed).status(),200);
 await panel.getByText('Đã chốt · chỉ xem',{exact:true}).waitFor();await page.reload();await filters('/mghp/tiep-nhan-ho-so');await panel.getByText('Đã chốt · chỉ xem',{exact:true}).waitFor();pass('UI04-online-seal-and-refresh');
 await panel.getByRole('link',{name:'Đến đối chiếu bản cứng',exact:true}).click();
 await page.getByRole('row').filter({hasText:fixture.applications[0].studentCode}).waitFor();
 assert.equal(await page.getByRole('link',{name:'Chọn quyền hưởng cần đánh giá',exact:true}).count(),0);assert.equal(await page.getByText('Tra cứu lịch sử nâng cao',{exact:true}).count(),0);
 assert.equal(await page.getByRole('combobox',{name:'Học kỳ',exact:true}).inputValue(),''+fixture.semesterId);
 assert.equal(await page.getByRole('combobox',{name:'Đợt xét',exact:true}).inputValue(),''+fixture.roundId);pass('UI05-only-transferred-pool-with-filters');
 await page.screenshot({path:path.join(out,'04-paper-inbox.png'),fullPage:true});
}
async function assess(person,passPaper){
 const state=await apiGet('/api/ctsv/mghp/ho-so/'+person.applicationId);if(['approved','rejected'].includes(state.hardCopyStatus))return;
 await filters('/mghp/danh-gia');await page.getByRole('row').filter({hasText:person.studentCode}).getByRole('button',{name:'Xem đối chiếu',exact:true}).click();
 await page.getByRole('button',{name:/^(Bắt đầu|Tiếp tục) đối chiếu$/}).click();await page.getByRole('heading',{name:'Kết quả từng tiêu chí',exact:true}).waitFor();
 if(passPaper&&!evidence.cases.some(c=>c.id==='UI06-incomplete-review-error-after-scroll')){
  const invalid=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/xac-nhan')&&new URL(r.url()).pathname.includes('/danh-gia/'));
  await page.getByRole('button',{name:'Duyệt bản cứng',exact:true}).click();assert.equal((await invalid).status(),409);
  await page.getByRole('alert').filter({hasText:'Chưa đủ điều kiện xác nhận'}).first().waitFor();pass('UI06-incomplete-review-error-after-scroll');
 }
 const criteria=page.getByRole('combobox',{name:/^Kết quả tiêu chí /});
 for(let i=0;i<await criteria.count();i++)await criteria.nth(i).selectOption(passPaper?'dat':'khong_dat');
 if(!passPaper)for(const input of await page.getByRole('textbox',{name:'Nhận xét tiêu chí',exact:true}).all())await input.fill('SYNTHETIC: bản cứng mẫu không chứng minh điều kiện bắt buộc.');
 const pdf=page.getByRole('region',{name:'PDF và nguồn căn cứ',exact:true}),buttons=pdf.locator('button[aria-pressed]');
 for(let i=0;i<await buttons.count();i++){
  await buttons.nth(i).click();await page.locator('canvas[data-rendered-file][data-rendered-page]').waitFor();
  if(passPaper)for(const title of ['Đã nhận bản cứng','Hình thức và thẩm quyền phù hợp','Nội dung đúng yêu cầu','Hiệu lực giấy phù hợp','Đúng người được chứng minh']){
   const button=page.getByRole('button',{name:title,exact:true});if(!await button.count())continue;
   if(await button.isEnabled()){const saved=page.waitForResponse(r=>/\/(kiem-tra|xac-nhan-ban-cung)$/.test(new URL(r.url()).pathname)&&r.request().method()==='POST');await button.click();assert.equal((await saved).status(),201);await page.getByRole('button',{name:'✓ '+title,exact:true}).waitFor();}
  }
  await page.getByRole('button',{name:'Thêm giấy làm căn cứ',exact:true}).click();
  await page.getByRole('combobox',{name:'Giấy đã nộp',exact:true}).nth(i).selectOption({index:i+1});
  await page.getByRole('combobox',{name:'Kết quả dùng giấy',exact:true}).nth(i).selectOption(passPaper?'dat':'khong_dat');
  await page.getByRole('textbox',{name:/^Lý do dùng giấy/}).nth(i).fill(passPaper?'SYNTHETIC: đã xem và đối chiếu PDF mẫu.':'SYNTHETIC: bản mẫu không đáp ứng.');
  if(passPaper){await page.getByLabel('Từ ngày',{exact:true}).nth(i).fill('2026-09-01');await page.getByLabel('Đến ngày',{exact:true}).nth(i).fill('2026-10-31');}
 }
 if(passPaper){
  await page.getByRole('textbox',{name:'Căn cứ mốc nộp và khoảng xét',exact:true}).fill('SYNTHETIC: chỉ xét khoảng tháng9–10, không là kết luận pháp lý thật.');
  await page.locator('summary').filter({hasText:'Thời gian đủ điều kiện hưởng'}).click();await page.getByRole('button',{name:'Thêm khoảng đủ điều kiện',exact:true}).click();
  await page.getByLabel('Từ ngày đủ điều kiện',{exact:true}).fill('2026-09-01');await page.getByLabel('Đến ngày đủ điều kiện',{exact:true}).fill('2026-10-31');
  await page.getByRole('textbox',{name:'Căn cứ khoảng ngày',exact:true}).fill('SYNTHETIC verified interval for UI handoff.');
 }else{await page.getByRole('button',{name:'Từ chối bản cứng',exact:true}).click();await page.getByRole('textbox',{name:'Lý do',exact:true}).fill('SYNTHETIC: bản cứng không đáp ứng, kiểm thử kết thúc hồ sơ.');}
 const confirmed=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/xac-nhan')&&new URL(r.url()).pathname.includes('/danh-gia/'));
 await page.getByRole('button',{name:passPaper?'Duyệt bản cứng':'Xác nhận từ chối',exact:true}).click();assert.equal((await confirmed).status(),200);
 const actual=await apiGet('/api/ctsv/mghp/ho-so/'+person.applicationId);assert.equal(actual.hardCopyStatus,passPaper?'approved':'rejected');assert.equal(actual.onlineStatus,'approved');pass(passPaper?'UI07-paper-approval-persisted':'UI08-paper-refusal-terminal');
}
async function paper(){
 await filters('/mghp/danh-gia');
 for(const [index,person] of fixture.applications.entries())await assess(person,index===0);
 await filters('/mghp/danh-gia');const panel=page.getByRole('region',{name:'Chốt bản cứng chuyển Hội đồng',exact:true});await panel.getByText(/0 vướng mắc/).waitFor();
 await page.screenshot({path:path.join(out,'05-paper-ready.png'),fullPage:true});await panel.getByRole('button',{name:'Chốt bản cứng chuyển Hội đồng',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Chốt bản cứng chuyển Hội đồng?',exact:true}),committed=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/dong-xet')&&r.request().method()==='POST');
 await dialog.getByRole('button',{name:'Xác nhận chốt',exact:true}).click();assert.equal((await committed).status(),200);await panel.getByText('Đã chốt · chỉ xem',{exact:true}).waitFor();
 assert.equal(await panel.getByRole('button',{name:'Xuất Excel',exact:true}).isDisabled(),true);pass('UI09-paper-seal-and-export-placeholder');
 await page.reload();await page.getByRole('combobox',{name:'Học kỳ',exact:true}).selectOption(''+fixture.semesterId);await page.getByRole('combobox',{name:'Đợt xét',exact:true}).selectOption(''+fixture.roundId);
 await page.getByRole('row').filter({hasText:fixture.applications[0].studentCode}).getByText('Đã duyệt bản cứng — V2',{exact:true}).waitFor();
 await page.screenshot({path:path.join(out,'06-paper-sealed.png'),fullPage:true});pass('UI10-review-closure-refresh-and-status');
}
async function security(){
 for(const role of ['student','admin']){
  const account=await signIn(role);await account.page.goto(origin+'/mghp/danh-gia');
  await account.page.getByText(/không có quyền/i).first().waitFor();assert.equal(await account.page.getByRole('button',{name:'Chốt bản cứng chuyển Hội đồng',exact:true}).count(),0);
  await account.page.goto(origin+'/mghp/tiep-nhan-ho-so');await account.page.getByText(/không có quyền/i).first().waitFor();pass('UI11-direct-URL-denied-'+role);
  await account.context.close();
 }
}
async function readonly(){
 await filters('/mghp/danh-gia');await page.getByRole('row').filter({hasText:fixture.applications[0].studentCode}).getByRole('button',{name:'Xem đối chiếu',exact:true}).click();
 const action=page.getByRole('button',{name:'Xem kết quả đối chiếu',exact:true});await action.waitFor();assert.equal(await action.isEnabled(),true);await action.click();
 await page.getByText('Khoảng đủ điều kiện được xác nhận',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Duyệt bản cứng',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Từ chối bản cứng',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Lập lần đối chiếu tiếp theo',exact:true}).count(),0);
 await page.screenshot({path:path.join(out,'07-readonly-assessment.png'),fullPage:true});pass('UI12-sealed-assessment-readable-without-edit-actions');
}
async function finish(){assert.deepEqual(evidence.pageErrors,[]);save();await browser.close();console.log('FINISHED '+JSON.stringify({cases:evidence.cases.length,pageErrors:evidence.pageErrors.length}));process.exit(0);}
const actions={inspect,online,paper,security,readonly,finish};
async function run(name){try{assert.ok(actions[name]);await actions[name]();console.log('PHASE PASS '+name);}catch(error){
 let message=String(error.stack);for(const a of Object.values(accounts??{}))if(a?.password)message=message.replaceAll(a.password,'[REDACTED]');
 evidence.failures.push({phase:name,message});save();await page?.screenshot({path:path.join(out,'failure-'+name+'.png'),fullPage:true}).catch(()=>{});console.log('PHASE FAIL '+name+' '+message.split('\n').slice(0,6).join('\n'));console.log('Context retained; fix/inspect before continuing.');
}}
(async()=>{
 accounts=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',String.raw`. ./tools/dev/Dev.Common.ps1
$handoffRoot=(Get-Location).Path;$handoffHome=Get-DevHome $handoffRoot;$handoffConfig=Read-DevConfig $handoffHome
$handoffAccounts=@{};foreach($kind in @('specialist','student','admin')){$handoffAccounts[$kind]=@{username=$handoffConfig.Accounts.$kind.Username;password=(Open-DevSecret $handoffConfig.Accounts.$kind.Password)}};$handoffAccounts|ConvertTo-Json -Compress`],{cwd:root,encoding:'utf8',windowsHide:true}));
 if(process.env.STAGE_ATTACH==='true'){
  browser=await chromium.connectOverCDP('http://127.0.0.1:9339');context=browser.contexts()[0];page=context.pages()[0];watchPrimary(page);accounts.specialist=null;
 }else{
  browser=await chromium.launch({channel:'chrome',headless:true,args:['--remote-debugging-address=127.0.0.1','--remote-debugging-port=9339']});
  const primary=await signIn('specialist');context=primary.context;page=primary.page;
 }
 await run(process.env.STAGE_START||'inspect');
 const lines=require('node:readline').createInterface({input:process.stdin});for await(const line of lines)if(line.trim())await run(line.trim());
})().catch(error=>{let message=error.message;for(const value of Object.values(accounts??{}))if(value?.password)message=message.replaceAll(value.password,'[REDACTED]');if(token)message=message.replaceAll(token,'[REDACTED]');console.log('Browser setup failed: '+message);process.exitCode=1;});
