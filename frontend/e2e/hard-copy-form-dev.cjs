// Interactive DEV harness: keeps sessions/context in RAM on failure for targeted investigation.
const {chromium}=require('@playwright/test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/hard-copy-form-20261126');
exports.start=async function(){
 fs.mkdirSync(out,{recursive:true});const cfg=JSON.parse(fs.readFileSync(path.join(process.env.CTSV_DEV_HOME,'config.json'),'utf8').replace(/^\uFEFF/,''));
 const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
 const origin='https://127.0.0.1:'+cfg.FrontendPort,browser=await chromium.launch({channel:'chrome',headless:true}),sessions=new Map();
 const report={db:'ctsv_dev',instance:'DEV2022',origin,backend:'https://127.0.0.1:'+cfg.BackendPort,businessApi:'REAL',storage:'existing Cloudinary PDFs REAL; no new upload/CA',cases:[],pageErrors:[]};
 const save=()=>fs.writeFileSync(path.join(out,'browser-results.json'),JSON.stringify(report,null,2));
 async function login(role){if(sessions.has(role))return sessions.get(role);const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:950}}),page=await context.newPage();page.setDefaultTimeout(15000);page.on('pageerror',e=>{report.pageErrors.push(e.name);save();});
  await page.goto(origin);await page.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(cfg.Accounts[role].Username);await page.getByRole('textbox',{name:'Mật khẩu',exact:true}).fill(passwords[role]);
  const pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap'&&r.request().method()==='POST');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();const response=await pending;assert.equal(response.status(),200,'DEV login');const token=(await response.json()).accessToken;
  await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();const session={context,page,token};sessions.set(role,session);return session;
 }
 async function api(method,url,body,tag,role='specialist'){
  const {page,token}=await login(role);return page.evaluate(async({method,url,body,tag,token})=>{const r=await fetch(url,{method,headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{}),...(tag?{'If-Match':tag}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,tag:r.headers.get('ETag'),body:await r.json()};},{method,url,body,tag,token});
 }
 const staff=await login('specialist');
 return {page:staff.page,browser,api,report,assert,out,origin,sessions,
  pass(id,details={}){report.cases=report.cases.filter(x=>x.id!==id);report.cases.push({id,status:'PASS',...details});save();},
  async inspect(id){const a=await api('GET','/api/ctsv/mghp/ho-so/'+id),c=await api('GET','/api/ctsv/mghp/ho-so/'+id+'/ngu-canh-xet-duyet'),p=await api('GET','/api/ctsv/mghp/phien-ban-chinh-sach/'+a.body.policyVersionId),process=await api('GET','/api/ctsv/nen-tang/qua-trinh-hoc/'+a.body.studyProcessId+'/lich-su');
   return {status:a.status,code:a.body.code,online:a.body.onlineStatus,paper:a.body.hardCopyStatus,allowed:a.body.evaluationAllowed,policyVersion:a.body.policyVersionId,policy:{name:p.body.name,effectiveFrom:p.body.effectiveFrom,verified:p.body.ruleVerificationStatus,criteria:p.body.criteria,documentGroups:p.body.documentGroups},process:process.body,documents:c.body.documents,evaluations:c.body.evaluations};},
  async open(id){await staff.page.goto(origin+'/mghp/danh-gia?ho-so='+id);await staff.page.getByRole('button',{name:/^(Bắt đầu|Tiếp tục) đối chiếu$/}).click();await staff.page.getByRole('heading',{name:'Giấy tờ & điều kiện xét',exact:true}).waitFor();return {headings:await staff.page.locator('main').getByRole('heading').allTextContents(),missing:await staff.page.getByLabel('Việc còn thiếu để duyệt',{exact:true}).innerText()};},
  async shot(name){await staff.page.screenshot({path:path.join(out,name+'.png'),fullPage:true});},
  async checkAccess(){const s=await login('student');await s.page.goto(origin+'/mghp/danh-gia');await s.page.getByRole('alert').filter({hasText:'Bạn không có quyền truy cập trang này.'}).waitFor();const r=await api('GET','/api/ctsv/mghp/ho-so/10039/ngu-canh-xet-duyet',null,null,'student');assert.equal(r.status,403);this.pass('HC-ACCESS-student-direct-route-and-api');},
  async close(){save();for(const s of sessions.values())await s.context.close();await browser.close();}
 };
};
