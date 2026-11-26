// DEV smoke only: real launcher/SQL/API, existing marked fixtures, no business writes.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const {chromium,expect}=require('@playwright/test');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/application-stage-dev-20261126');
const origin='https://127.0.0.1:5173',backend='https://127.0.0.1:8443',base='/api/ctsv/mghp';
const evidence={database:'ctsv_dev',instance:'DEV2022',frontend:origin,backend,services:'REAL; no CA signing/upload invoked',cases:[],failures:[],pageErrors:[]};
let accounts,secrets=[],browser,step=Number(process.env.CTSV_STAGE_START??0);
if(step!==0){
 assert.ok([2,3].includes(step),'Only a targeted remaining phase may be resumed');
 const prior=JSON.parse(fs.readFileSync(path.join(out,'browser.json'),'utf8'));
 assert.equal(prior.database,'ctsv_dev');assert.ok(prior.cases.length>=step);
 evidence.cases=prior.cases.slice(0,step);evidence.failures=prior.failures;
}
const sessions=new Map();
const save=()=>fs.writeFileSync(path.join(out,'browser.json'),JSON.stringify(evidence,null,2));
const safe=message=>secrets.reduce((text,secret)=>text.replaceAll(secret,'[REDACTED]'),String(message));
async function login(role){
 if(sessions.has(role))return sessions.get(role);
 const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:950}}),page=await context.newPage();
 page.setDefaultTimeout(15000);page.on('pageerror',e=>evidence.pageErrors.push(e.message));
 await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(accounts[role].username);await page.getByLabel('Mật khẩu',{exact:true}).fill(accounts[role].password);
 const logged=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap'&&r.request().method()==='POST');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();const response=await logged;assert.equal(response.status(),200);
 const token=(await response.json()).accessToken;await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
 const session={context,page,token};sessions.set(role,session);accounts[role]=null;return session;
}
async function api(role,method,url,body,tag){
 const {context,token}=await login(role);
 const response=await context.request.fetch(backend+base+url,{method,headers:{Authorization:'Bearer '+token,...(tag?{'If-Match':tag}:{})},...(body?{data:body}:{})});
 return response;
}
async function get(role,url){const r=await api(role,'GET',url);assert.equal(r.status(),200,url);return r.json();}
async function roundFilter(page,url,round){
 await page.goto(origin+url);
 const term=page.getByRole('combobox',{name:'Học kỳ',exact:true});await term.locator('option[value="1"]').waitFor({state:'attached'});await term.selectOption('1');
 const select=page.getByRole('combobox',{name:'Đợt xét',exact:true});await select.locator('option[value="'+round+'"]').waitFor({state:'attached'});await expect(select).toBeEnabled();
 const done=page.waitForResponse(r=>{const u=new URL(r.url());return u.pathname===base+'/ho-so'&&u.searchParams.get('roundId')===String(round)&&u.searchParams.get('semesterId')==='1';});
 await select.selectOption(String(round));assert.equal((await done).status(),200);
}
const stages=[
 ['DEV01-current-projection-and-frozen-guards',async()=>{
  const closed=await get('specialist','/dot-xet/20051');assert.equal(closed.code,'HAND25-DEV-20261125');assert.equal(closed.status,'closed');
  const approved=await get('specialist','/ho-so/10042'),rejected=await get('specialist','/ho-so/10043');
  assert.equal(approved.code,'HAND25-DEV-20261125-10037');assert.equal(rejected.code,'HAND25-DEV-20261125-10038');
  assert.equal(approved.status,'approved');assert.equal(approved.onlineStatus,'approved');assert.equal(approved.hardCopyStatus,'approved');
  assert.equal(rejected.status,'rejected');assert.equal(rejected.onlineStatus,'approved');assert.equal(rejected.hardCopyStatus,'rejected');assert.equal(rejected.rejectionStage,'hard_copy');assert.ok(rejected.rejectionReason);
  assert.equal(approved.evaluationAllowed,false);assert.equal(rejected.evaluationAllowed,false);
  evidence.projections=[approved,rejected].map(a=>({id:a.id,status:a.status,onlineStatus:a.onlineStatus,hardCopyStatus:a.hardCopyStatus,rejectionStage:a.rejectionStage,intakeSealed:a.intakeSealed,evaluationAllowed:a.evaluationAllowed}));
  const detail=await api('specialist','GET','/ho-so/10042');
  // Expected rejection only; source is already sealed. No accepted business write.
  assert.equal((await api('specialist','POST','/ho-so/10042/ket-luan',{result:'approved'},detail.headers()['etag'])).status(),409);
  assert.equal((await api('specialist','POST','/ho-so/10042/mo-lai',{reason:'DEV smoke retired endpoint'})).status(),410);
 }],
 ['DEV02-online-filter-keeps-v1-after-paper-rejection',async()=>{
  const {page}=await login('specialist');await roundFilter(page,'/mghp/tiep-nhan-ho-so',20051);
  const filters=page.locator('[aria-label="Lọc trạng thái hồ sơ"]');assert.deepEqual(await filters.getByRole('button').allTextContents(),['Tất cả','Chờ xử lý','Sinh viên đã bổ sung','Đã duyệt','Từ chối','Đã rút']);
  await filters.getByRole('button',{name:'Đã duyệt',exact:true}).click();await page.getByText('2 hồ sơ phù hợp',{exact:true}).waitFor();
  const table=page.getByRole('table',{name:'Danh sách tiếp nhận hồ sơ',exact:true});await expect(table.locator('tbody tr')).toHaveCount(2);await expect(table.getByText('Đã duyệt online — V1',{exact:true})).toHaveCount(2);await expect(filters.getByRole('button',{name:'Đã duyệt',exact:true})).toBeEnabled();
  await page.screenshot({path:path.join(out,'01-online-v1.png'),fullPage:true});
  await filters.getByRole('button',{name:'Chờ xử lý',exact:true}).click();await page.getByText('0 hồ sơ phù hợp',{exact:true}).waitFor();
 }],
 ['DEV03-paper-filters-passed-rejected-pending',async()=>{
  const {page}=await login('specialist');await roundFilter(page,'/mghp/danh-gia',20051);
  const filters=page.locator('[aria-label="Lọc trạng thái bản cứng"]'),table=page.getByRole('table',{name:'Danh sách đối chiếu bản cứng',exact:true});
  await expect(table.locator('tbody tr')).toHaveCount(2);await expect(filters.getByRole('button',{name:'Tất cả',exact:true})).toBeEnabled();
  for(const [filter,label]of [['Đã duyệt bản cứng','Đã duyệt bản cứng — V2'],['Đã từ chối bản cứng','Đã từ chối bản cứng']]){
   const done=page.waitForResponse(r=>new URL(r.url()).pathname===base+'/ho-so'&&new URL(r.url()).searchParams.get('stage')==='hard-copy');await filters.getByRole('button',{name:filter,exact:true}).click();assert.equal((await done).status(),200);await expect(table.locator('tbody tr')).toHaveCount(1);await table.getByText(label,{exact:true}).waitFor();
  }
  await expect(filters.getByRole('button',{name:'Đã từ chối bản cứng',exact:true})).toBeEnabled();await page.screenshot({path:path.join(out,'02-paper-rejected.png'),fullPage:true});
  const pending=await get('specialist','/ho-so?stage=hard-copy&semesterId=1&roundId=20040&status=pending&size=100');assert.equal(pending.totalElements,8);assert.ok(pending.items.every(a=>a.status==='processing'&&a.onlineStatus==='approved'&&['pending','in_progress'].includes(a.hardCopyStatus)));
  // SQL inventory verified two existing drafts; Cần đối chiếu includes both unfinished states.
  assert.equal(pending.items.filter(a=>a.hardCopyStatus==='pending').length,6);
  assert.deepEqual(pending.items.filter(a=>a.hardCopyStatus==='in_progress').map(a=>a.id).sort(),['10028','10037']);
  const root=await get('specialist','/dot-xet/20040');assert.equal(root.code,'UX-INTAKE-1795271503790-3');
  await roundFilter(page,'/mghp/danh-gia',20040);await expect(table.locator('tbody tr')).toHaveCount(8);
  await page.screenshot({path:path.join(out,'03-paper-pending.png'),fullPage:true});
 }],
 ['DEV04-student-progress-and-object-permission',async()=>{
  const {page}=await login('student');const own=await get('student','/ho-so/10029');assert.equal(own.status,'processing');assert.equal(own.onlineStatus,'approved');assert.equal(own.hardCopyStatus,'pending');assert.equal(own.councilResult,null);
  assert.equal((await api('student','GET','/ho-so/10042')).status(),404);
  assert.equal((await api('student','GET','/ho-so?stage=online')).status(),403);
  await page.goto(origin+'/mghp/ho-so');await page.locator('[data-application-id="10029"]').click();
  const detail=page.getByRole('region',{name:'Chi tiết hồ sơ',exact:true});await detail.waitFor();
  await expect(detail.getByText('Hồ sơ online đã được duyệt — chờ đối chiếu bản cứng',{exact:true})).toHaveCount(2);assert.equal(await detail.getByRole('button',{name:'Kê khai nộp lại',exact:true}).count(),0);await page.screenshot({path:path.join(out,'04-student-progress.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await expect.poll(()=>page.evaluate(()=>{const panel=document.querySelector('[aria-label="Chi tiết hồ sơ"]');if(!panel)return false;const right=panel.getBoundingClientRect().right,badges=[...panel.querySelectorAll('span')].filter(e=>e.textContent==='Hồ sơ online đã được duyệt — chờ đối chiếu bản cứng');return badges.length===2&&badges.every(e=>e.getBoundingClientRect().right<=right);})).toBe(true);
  const layout=await page.evaluate(()=>{const panel=document.querySelector('[aria-label="Chi tiết hồ sơ"]');return{viewport:innerWidth,scroll:document.documentElement.scrollWidth,panelRight:panel.getBoundingClientRect().right,badges:[...panel.querySelectorAll('span')].filter(e=>e.textContent==='Hồ sơ online đã được duyệt — chờ đối chiếu bản cứng').map(e=>({right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width,whiteSpace:getComputedStyle(e).whiteSpace}))}});
  fs.writeFileSync(path.join(out,'mobile-after.json'),JSON.stringify(layout,null,2));
  await page.screenshot({path:path.join(out,'05-student-mobile.png'),fullPage:true});
  const admin=await login('admin');await admin.page.goto(origin+'/mghp/tiep-nhan-ho-so');await admin.page.getByText(/không có quyền/i).first().waitFor();assert.equal((await api('admin','GET','/ho-so?stage=online')).status(),403);
 }]
];
async function advance(){
 while(step<stages.length){const [id,action]=stages[step];try{await action();evidence.cases.push({id,result:'PASS'});save();console.log('PASS '+id);step++;}catch(error){
  evidence.failures.push({id,message:safe(error.message)});save();for(const {page}of sessions.values())await page.screenshot({path:path.join(out,'failure-'+id+'.png'),fullPage:true}).catch(()=>{});
  console.log('FAIL '+id+' '+safe(error.message).split('\n').slice(0,5).join('\n'));console.log('Context retained. Send retry to rerun this phase, or stop to close.');return;
 }}
 assert.deepEqual(evidence.pageErrors,[]);evidence.result='PASS';for(const failure of evidence.failures)failure.resolved=evidence.cases.some(c=>c.id===failure.id&&c.result==='PASS');save();await browser.close();accounts=null;secrets=[];console.log('DONE '+evidence.cases.length+' DEV flows PASS');process.exit(0);
}
(async()=>{
 const baseline=JSON.parse(fs.readFileSync(path.join(out,'before.json'),'utf8').replace(/^\uFEFF/,''));assert.equal(baseline.database,'ctsv_dev');assert.equal(baseline.instance,'DEV2022');
 accounts=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',String.raw`. ./tools/dev/Dev.Common.ps1
$c=Read-DevConfig (Get-DevHome (Get-Location).Path)
$a=@{};foreach($role in @('specialist','student','admin')){$a[$role]=@{username=$c.Accounts.$role.Username;password=(Open-DevSecret $c.Accounts.$role.Password)}};$a|ConvertTo-Json -Compress`],{cwd:root,encoding:'utf8',windowsHide:true}));
 secrets=Object.values(accounts).map(a=>a.password);browser=await chromium.launch({channel:'chrome',headless:true,args:['--remote-debugging-address=127.0.0.1','--remote-debugging-port=9354']});
 await advance();const lines=require('node:readline').createInterface({input:process.stdin});for await(const line of lines){if(line.trim()==='retry')await advance();if(line.trim()==='stop'){await browser.close();process.exit(1);}}
})().catch(async error=>{console.log('SETUP FAILED '+safe(error.message));evidence.result='FAIL';evidence.setupError=safe(error.message);save();await browser?.close();process.exitCode=1;});
