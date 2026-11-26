// Dedicated real-API/SQL browser check. Synthetic credentials enter via stdin only.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const {chromium,expect}=require('@playwright/test');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/application-stage-browser');
let input='',server,browser,fixture;
process.stdin.setEncoding('utf8');process.stdin.on('data',part=>input+=part);
process.stdin.on('end',async()=>{
 const evidence={database:'ctsv_review_lifecycle_test_20261122',backend:'REAL',frontend:'React REAL',files:'METADATA ONLY; no PDF rendering asserted',cases:[],pageErrors:[]};
 const record=id=>{evidence.cases.push(id);console.log('PASS '+id);};
 try{
  fixture=JSON.parse(input);input='';assert.equal(fixture.database,evidence.database);
  fs.mkdirSync(out,{recursive:true});
  const tlsHome=execFileSync('pwsh',['-NoProfile','-Command',". ./tools/dev/Dev.Common.ps1; Get-DevHome (Get-Location).Path"],{cwd:root,encoding:'utf8',windowsHide:true}).trim();
  const {createServer}=await import('vite');
  // Self-signed HTTPS only in this local test harness; production Vite config remains unchanged.
  server=await createServer({configFile:false,root:path.join(root,'frontend'),server:{host:'127.0.0.1',port:5193,strictPort:true,https:{cert:fs.readFileSync(path.join(tlsHome,'https-cert.pem')),key:fs.readFileSync(path.join(tlsHome,'https-key.pem'))},proxy:{'/api':{target:fixture.backend,secure:false,changeOrigin:false}}}});
  await server.listen();browser=await chromium.launch({channel:'chrome',headless:true});
  const origin='https://127.0.0.1:5193';
  async function login(role){
   const context=await browser.newContext({ignoreHTTPSErrors:true,locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:950}}),page=await context.newPage();
   page.on('pageerror',e=>evidence.pageErrors.push(e.message));
   await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(fixture.accounts[role].username);await page.getByLabel('Mật khẩu',{exact:true}).fill(fixture.accounts[role].password);
   await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
   fixture.accounts[role]=null;return page;
  }
  async function selectRound(page,url,heading){
   await page.goto(origin+url);await page.getByRole('heading',{name:heading,exact:true}).waitFor();
   const term=page.getByRole('combobox',{name:'Học kỳ',exact:true});
   await term.locator('option[value="'+fixture.semesterId+'"]').waitFor({state:'attached'});await term.selectOption(fixture.semesterId);
   const round=page.getByRole('combobox',{name:'Đợt xét',exact:true});await round.locator('option[value="'+fixture.roundId+'"]').waitFor({state:'attached'});
   await expect(round).toBeEnabled();
   const filtered=page.waitForResponse(r=>{const u=new URL(r.url());return u.pathname==='/api/ctsv/mghp/ho-so'&&u.searchParams.get('roundId')===fixture.roundId&&u.searchParams.get('semesterId')===fixture.semesterId;});
   await round.selectOption(fixture.roundId);assert.equal((await filtered).status(),200);
  }
  const staff=await login('specialist');
  await selectRound(staff,'/mghp/tiep-nhan-ho-so','Tiếp nhận & bổ sung hồ sơ MGHP');
  const filters=staff.locator('[aria-label="Lọc trạng thái hồ sơ"]');
  assert.deepEqual(await filters.getByRole('button').allTextContents(),['Tất cả','Chờ xử lý','Sinh viên đã bổ sung','Đã duyệt','Từ chối','Đã rút']);
  await filters.getByRole('button',{name:'Đã duyệt',exact:true}).click();
  await staff.getByText('3 hồ sơ phù hợp',{exact:true}).waitFor();
  assert.equal(await staff.getByRole('table',{name:'Danh sách tiếp nhận hồ sơ'}).getByText('Đã duyệt online — V1',{exact:true}).count(),3);
  await expect(filters.getByRole('button',{name:'Đã duyệt',exact:true})).toBeEnabled();
  await staff.screenshot({path:path.join(out,'01-online-v1-preserved.png'),fullPage:true});record('UI-01-online-results-and-six-filters');
  await filters.getByRole('button',{name:'Chờ xử lý',exact:true}).click();await staff.getByText('0 hồ sơ phù hợp',{exact:true}).waitFor();record('UI-02-pending-filter-excludes-all-v1');
  await selectRound(staff,'/mghp/danh-gia','Đối chiếu bản cứng hồ sơ MGHP');
  const paperFilters=staff.locator('[aria-label="Lọc trạng thái bản cứng"]');
  const table=staff.getByRole('table',{name:'Danh sách đối chiếu bản cứng',exact:true});
  await expect(paperFilters.getByRole('button',{name:'Tất cả',exact:true})).toBeEnabled();
  await expect(table.locator('tbody tr')).toHaveCount(3);
  for(const label of ['Chờ đối chiếu','Đã duyệt bản cứng — V2','Đã từ chối bản cứng'])await table.getByText(label,{exact:true}).waitFor();
  await staff.screenshot({path:path.join(out,'02-paper-results.png'),fullPage:true});
  for(const [filter,label]of [['Cần đối chiếu','Chờ đối chiếu'],['Đã duyệt bản cứng','Đã duyệt bản cứng — V2'],['Đã từ chối bản cứng','Đã từ chối bản cứng']]){
   const response=staff.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so'&&r.url().includes('stage=hard-copy'));
   await paperFilters.getByRole('button',{name:filter,exact:true}).click();assert.equal((await response).status(),200);
   await expect(table.locator('tbody tr')).toHaveCount(1);await table.getByText(label,{exact:true}).waitFor();
  }record('UI-03-paper-filters-and-results');
  await expect(paperFilters.getByRole('button',{name:'Đã từ chối bản cứng',exact:true})).toBeEnabled();
  await staff.setViewportSize({width:390,height:844});assert.equal(await staff.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await staff.screenshot({path:path.join(out,'03-mobile.png'),fullPage:true});record('UI-04-mobile-no-page-overflow');
  const student=await login('student');await student.goto(origin+'/mghp/ho-so');
  await student.locator('[data-application-id="'+fixture.approvedId+'"]').click();await student.getByText('Hồ sơ đã được duyệt — chờ kết quả xét MGHP',{exact:true}).first().waitFor();
  assert.equal(await student.getByRole('button',{name:'Kê khai nộp lại',exact:true}).count(),0);
  await student.screenshot({path:path.join(out,'04-student-approved.png'),fullPage:true});
  await student.getByRole('button',{name:'Về danh sách',exact:true}).click();await student.locator('[data-application-id="'+fixture.rejectedId+'"]').click();
  await student.getByText('Lý do từ chối bản cứng: SYNTHETIC confirmed',{exact:true}).waitFor();record('UI-05-student-progress-and-rejection-reason');
  const admin=await login('admin');await admin.goto(origin+'/mghp/tiep-nhan-ho-so');await admin.getByText(/không có quyền/i).first().waitFor();assert.equal(await admin.locator('[aria-label="Lọc trạng thái hồ sơ"]').count(),0);record('UI-06-ungranted-admin-route-denied');
  assert.deepEqual(evidence.pageErrors,[]);evidence.result='PASS';
 }catch(error){
  evidence.result='FAIL';evidence.error=String(error.message).split('\n').slice(0,8).join('\n');process.exitCode=1;console.log('FAIL '+evidence.error);
  evidence.visibleTables=await Promise.all((browser?.contexts().flatMap(c=>c.pages())??[]).map(p=>p.locator('table').allTextContents().catch(()=>[])));
  for(const p of browser?.contexts().flatMap(c=>c.pages())??[])if(!p.url().endsWith('/dang-nhap'))await p.screenshot({path:path.join(out,'failure.png'),fullPage:true}).catch(()=>{});
 }finally{
  fixture=null;fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(evidence,null,2));await browser?.close();await server?.close();
 }
});
