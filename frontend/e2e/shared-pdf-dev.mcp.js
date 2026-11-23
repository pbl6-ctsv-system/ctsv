// Run after shared-pdf-login.cjs and attaching existing contexts to page.__sharedPdf.
// x.stage selects a focused stage: source, selection, consumers, permissions, review-fixes.
// The isolated /e2e harness is excluded from production; it sends GETs only, never a signing command.
async(page)=>{
 const x=page.__sharedPdf;if(!x?.roles||!x.harness)throw Error('Existing authenticated DEV contexts and harness required');
 const origin='https://127.0.0.1:5173',out='D:/7rd_semester/pbl6_src/ctsv/target/shared-pdf-20261123/';
 const pass=(ok,name,evidence={})=>{if(!ok)throw Error(name);x.checks=x.checks.filter(c=>c.name!==name);x.checks.push({name,status:'PASS',...evidence});};
 const h=x.harness;
 async function ready(p,id){await p.locator('canvas[data-rendered-file="'+id+'"][data-rendered-page]').waitFor({timeout:20000});}
 async function show(id,selectable=false,title='Tài liệu PDF '+id){await h.evaluate(props=>window.__pdfHarness.show(props),{fileId:id,title,selectable});await ready(h,id);}
 async function download(p){
  const pending=p.waitForEvent('download');await p.getByRole('button',{name:'Tải PDF đang xem',exact:true}).click();const d=await pending;
  return p.evaluate(async url=>{const r=await fetch(url),b=await r.arrayBuffer();return {magic:new TextDecoder().decode(b.slice(0,5)),size:b.byteLength,sha256:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),v=>v.toString(16).padStart(2,'0')).join('')};},d.url());
 }
 async function visibleCanvas(p){return p.locator('canvas[data-rendered-file]').evaluate(el=>{const data=el.getContext('2d').getImageData(0,0,el.width,el.height).data;let dark=0;for(let i=0;i<data.length;i+=64)if(data[i]<190&&data[i+1]<190&&data[i+2]<190&&data[i+3]>0)dark++;return {file:el.dataset.renderedFile,page:el.dataset.renderedPage,width:el.width,height:el.height,dark,visible:!el.hidden};});}
 if(x.stage==='source'){
  const expected={
   '10040':'04fcb34633f437249457eff0da845b999f304e998a1d2d798e2a56f118c974ce',
   '10042':'ea46df751f01cb6ccf3e41093f2ab95882babe0c8d6c53d2d154fba8972aa264',
   '10043':'ebabbced1eeab8bed602b3024acf13982fc39ac82678c46f9834e3bc9a9723f2',
   '10044':'30b8c5ce020e245511a09c18bdc3e11e7b059183bf763ffac6fcd38ef15056a7',
   '10047':'42dfa65986dc46a3a8eeaf375fea2a9e5b2280734ae8bc5d460913422300bf29'
  };
  for(const id of ['10040','10042','10043','10044','10047','10042']){
   await show(id);const rendered=await visibleCanvas(h),reads=await h.evaluate(()=>window.__pdfReads.length),file=await download(h);
   pass(rendered.file===id&&rendered.dark>20&&rendered.visible&&file.magic==='%PDF-'&&file.sha256===expected[id],'render-download-'+id,{file,rendered});
   pass(await h.evaluate(()=>window.__pdfReads.length)===reads,'download-reuses-bytes-'+id);
  }
  const before=await h.evaluate(()=>window.__pdfReads.length);await show('10042',false,'Tên/ngữ cảnh mới của cùng PDF');
  await h.getByText('Tên/ngữ cảnh mới của cùng PDF',{exact:true}).waitFor();pass(await h.evaluate(()=>window.__pdfReads.length)===before,'metadata-does-not-refetch');
  let release;const gate=new Promise(r=>release=r);
  await h.route('**/api/ctsv/tep/10040/noi-dung',async route=>{await gate;try{await route.continue();}catch{}});
  try{
   const pending=h.waitForRequest(r=>r.url().endsWith('/tep/10040/noi-dung'));
   await h.evaluate(()=>window.__pdfHarness.show({fileId:'10040',title:'PDF chậm'}));await pending;
   await show('10043',false,'PDF mới nhất');release();await h.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
   pass(await h.locator('canvas').getAttribute('data-rendered-file')==='10043','rapid-change-cancels-stale-source');
  }finally{release();await h.unroute('**/api/ctsv/tep/10040/noi-dung');}
  let fault=true;const pattern='**/api/ctsv/tep/10044/noi-dung';
  await h.route(pattern,async route=>fault?route.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({status:503,code:'STORAGE_UNAVAILABLE'})}):route.continue());
  try{
   await h.evaluate(()=>window.__pdfHarness.show({fileId:'10044',title:'PDF lỗi tạm thời'}));await h.getByRole('button',{name:'Thử mở lại PDF',exact:true}).waitFor();
   pass(await h.locator('canvas[data-rendered-file]').count()===0&&await h.getByRole('alert').isVisible(),'fault-clears-stale-canvas',{fault:'HTTP503 STUB only'});
   fault=false;await h.getByRole('button',{name:'Thử mở lại PDF',exact:true}).click();await ready(h,'10044');
   pass((await download(h)).sha256===expected['10044'],'retry-gets-real-stored-pdf',{recovery:'HTTP/SQL/Cloudinary REAL'});
  }finally{await h.unroute(pattern);}
  await h.screenshot({path:out+'shared-desktop.png',fullPage:true});
 }
 if(x.stage==='selection'){
  await show('10047',true,'PDF nhiều trang — chọn vùng ký thử giao diện');
  await h.getByRole('spinbutton',{name:'Trang PDF',exact:true}).fill('2');await h.locator('canvas[data-rendered-page="2"]').waitFor();
  await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).click();
  const overlay=h.getByRole('img',{name:'Vùng chọn vị trí ký',exact:true});await overlay.scrollIntoViewIfNeeded();
  let b=await overlay.boundingBox();await h.mouse.move(b.x+40,b.y+50);await h.mouse.down();await h.mouse.move(b.x+42,b.y+52);await h.mouse.up();
  await h.getByText('Vùng ký quá nhỏ. Kéo một khung lớn hơn.',{exact:true}).waitFor();
  pass(await h.locator('canvas[data-rendered-page="2"]').isVisible()&&await h.evaluate(()=>window.__pdfHarness.selection())===null,'small-selection-can-be-corrected-without-reloading');
  await h.mouse.move(b.x+70,b.y+90);await h.mouse.down();await h.mouse.move(b.x+270,b.y+160,{steps:6});await h.mouse.up();
  await h.waitForFunction(()=>window.__pdfHarness.selection()?.page===2);
  const first=await h.evaluate(()=>window.__pdfHarness.selection());pass(first.width>0&&first.height>0,'drag-selects-page-two');
  const selection=overlay.locator('[data-mode="move"]');await selection.scrollIntoViewIfNeeded();b=await selection.boundingBox();
  await h.mouse.move(b.x+b.width/2,b.y+b.height/2);await h.mouse.down();await h.mouse.move(b.x+b.width/2+35,b.y+b.height/2+20,{steps:4});await h.mouse.up();
  const moved=await h.evaluate(()=>window.__pdfHarness.selection());pass(moved.x!==first.x||moved.y!==first.y,'selection-moves');
  const handle=await overlay.locator('[data-mode="resize"]').boundingBox();await h.mouse.move(handle.x+4,handle.y+4);await h.mouse.down();await h.mouse.move(handle.x+35,handle.y+20,{steps:4});await h.mouse.up();
  const resized=await h.evaluate(()=>window.__pdfHarness.selection());pass(resized.width!==moved.width||resized.height!==moved.height,'selection-resizes');
  await h.getByRole('button',{name:'Phóng to PDF',exact:true}).click();await h.locator('canvas[data-rendered-page="2"]').waitFor();
  pass(JSON.stringify(await h.evaluate(()=>window.__pdfHarness.selection()))===JSON.stringify(resized),'zoom-preserves-pdf-geometry');
  await h.getByRole('button',{name:'Xoay trang',exact:true}).click();await h.locator('canvas[data-rendered-page="2"]').waitFor();
  pass(JSON.stringify(await h.evaluate(()=>window.__pdfHarness.selection()))===JSON.stringify(resized),'rotation-preserves-pdf-geometry');
  await h.setViewportSize({width:390,height:844});await h.locator('canvas[data-rendered-page="2"]').waitFor();
  pass(await h.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'mobile-harness-no-page-overflow');
  await h.getByRole('button',{name:'Chọn vùng ký bằng bàn phím ở cuối trang',exact:true}).click();
  pass((await h.evaluate(()=>window.__pdfHarness.selection())).page===2,'keyboard-selection');
  await h.screenshot({path:out+'shared-selection-mobile.png',fullPage:true});await h.setViewportSize({width:1360,height:1000});
  await h.getByRole('button',{name:'Trang PDF sau',exact:true}).click();await h.locator('canvas[data-rendered-page="3"]').waitFor();
  pass(await h.evaluate(()=>window.__pdfHarness.selection())===null,'page-change-clears-selection');
  await h.getByRole('button',{name:'Chọn vùng ký bằng bàn phím ở cuối trang',exact:true}).click();
  await show('10040',true);pass(await h.evaluate(()=>window.__pdfHarness.selection())===null,'file-change-clears-selection');
  await show('10040',false);pass(await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).count()===0&&await h.getByRole('img',{name:'Vùng chọn vị trí ký',exact:true}).count()===0,'readonly-has-no-signing-tools');
  await show('10047',true);await h.getByRole('spinbutton',{name:'Trang PDF',exact:true}).fill('2');await h.locator('canvas[data-rendered-page="2"]').waitFor();
  await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).click();await h.getByRole('button',{name:'Chọn vùng ký bằng bàn phím ở cuối trang',exact:true}).click();
  await show('10048',true);pass(await h.getByRole('spinbutton',{name:'Trang PDF',exact:true}).inputValue()==='2'&&await h.evaluate(()=>window.__pdfHarness.selection())===null,'signed-replacement-preserves-page-clears-geometry');
 }
 if(x.stage==='review-fixes'){
  await h.setViewportSize({width:390,height:844});await show('10048',true,'PDF ngang — kiểm vùng ký sau thu nhỏ');
  await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).click();await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).hover();
  const color=await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).evaluate(el=>{const probe=document.createElement('i');probe.style.backgroundColor='var(--color-primary)';el.append(probe);const expected=getComputedStyle(probe).backgroundColor;probe.remove();return {actual:getComputedStyle(el).backgroundColor,expected};});
  pass(color.actual===color.expected,'primary-hover-keeps-primary-background',color);
  await h.getByRole('button',{name:'Chọn vùng ký bằng bàn phím ở cuối trang',exact:true}).click();const before=await h.evaluate(()=>window.__pdfHarness.selection());
  await show('10048',false,'Đang gửi xác nhận — tạm khóa công cụ');await h.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
  pass(JSON.stringify(await h.evaluate(()=>window.__pdfHarness.selection()))===JSON.stringify(before),'temporary-disable-keeps-controlled-selection');
  await show('10048',true);await h.getByRole('button',{name:'Xoay trang',exact:true}).click();
  await h.getByRole('button',{name:'Thu nhỏ PDF',exact:true}).click();await h.getByRole('button',{name:'Thu nhỏ PDF',exact:true}).click();
  await h.getByRole('button',{name:'Chọn vị trí ký',exact:true}).click();await h.getByRole('button',{name:'Chọn vùng ký bằng bàn phím ở cuối trang',exact:true}).click();
  const bounds=await h.getByRole('img',{name:'Vùng chọn vị trí ký',exact:true}).evaluate(el=>{const box=el.querySelector('[data-mode="move"]');return {height:Number(el.getAttribute('height')),bottom:Number(box.getAttribute('y'))+Number(box.getAttribute('height'))};});
  pass(bounds.bottom<=bounds.height,'keyboard-region-fits-short-page',bounds);
  await h.screenshot({path:out+'short-page-selection-mobile.png',fullPage:true});await h.setViewportSize({width:1360,height:1000});
 }
 if(x.stage==='consumers'){
  const cv=x.roles.cv,sv=x.roles.sv;
  await cv.goto(origin+'/mghp/tiep-nhan-ho-so');await cv.locator('[data-application-id="10028"]').click();await cv.locator('canvas[data-rendered-file]').waitFor();
  const choices=cv.locator('#review-pdf-10028 button[data-review-state]');const names=await choices.locator('span').allTextContents();
  for(const index of [0,1,2,0,2,1]){await choices.nth(index).click();await cv.locator('canvas[data-rendered-file]').waitFor();pass(await cv.getByRole('region',{name:'PDF đang xem',exact:true}).getByText(names[index].split('\n')[0].replace(/^\d+\. /,''),{exact:true}).count()>=1,'staff-paper-choice-'+index);}
  pass((await visibleCanvas(cv)).dark>20&&await cv.locator('iframe').count()===0,'staff-intake-uses-shared-canvas');
  let reads=0;const observe=r=>{if(/\/api\/ctsv\/tep\/\d+\/noi-dung$/.test(r.url()))reads++;};cv.on('request',observe);
  try{await cv.getByRole('button',{name:'Đọc lại hồ sơ',exact:true}).click();await cv.locator('[aria-label="Xử lý hồ sơ chuyên viên"][aria-busy="false"]').waitFor();await cv.locator('canvas[data-rendered-file]').waitFor();pass(reads===0,'staff-metadata-refresh-keeps-loaded-pdf');}finally{cv.off('request',observe);}
  await cv.screenshot({path:out+'staff-intake.png',fullPage:true});
  await sv.goto(origin+'/mghp/ho-so');await sv.locator('[data-application-id="10028"]').click();await sv.getByRole('button',{name:'Xem PDF 1',exact:true}).click();await sv.locator('canvas[data-rendered-file]').waitFor();
  pass((await visibleCanvas(sv)).dark>20&&await sv.getByRole('button',{name:'Chọn vị trí ký',exact:true}).count()===0,'student-own-dossier-readonly-canvas');
  const file=await download(sv);pass(file.magic==='%PDF-'&&file.size>30000,'student-downloads-loaded-pdf');
  await sv.setViewportSize({width:360,height:800});await sv.locator('canvas[data-rendered-file]').waitFor();pass(await sv.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'student-360px-no-overflow');
  await sv.screenshot({path:out+'student-mobile.png',fullPage:true});await sv.setViewportSize({width:1360,height:1000});
  await cv.goto(origin+'/mghp/danh-gia?ho-so=10028');const resume=cv.getByRole('button',{name:/^(Bắt đầu|Tiếp tục) đối chiếu$/});await resume.waitFor();await resume.click();
  const documents=cv.getByRole('region',{name:'PDF và nguồn căn cứ',exact:true}).locator('button[aria-pressed]');await documents.first().click();await cv.locator('canvas[data-rendered-file]').waitFor();
  pass((await visibleCanvas(cv)).dark>20,'paper-review-uses-shared-canvas');
  await cv.screenshot({path:out+'paper-review.png',fullPage:true});
  for(const role of ['cv','pp','pht']){const p=x.roles[role];await p.goto(origin+'/mghp/ky-so?yeu-cau=2');await p.locator('canvas[data-rendered-file]').waitFor();pass((await visibleCanvas(p)).dark>20&&await p.getByRole('button',{name:'Ký số',exact:true}).count()===0,'completed-signing-readonly-'+role);}
  pass(await x.roles.pht.getByRole('spinbutton',{name:'Trang PDF',exact:true}).evaluate(el=>getComputedStyle(el.parentElement).flexDirection)==='row','page-control-keeps-row-in-app-css');
 }
 if(x.stage==='permissions'){
  for(const role of ['sv','admin']){
   const p=x.roles[role],pending=p.waitForRequest(r=>r.url().includes('/nen-tang/toi/quyen-giao-dien')&&!!r.headers().authorization);
   await p.goto(origin+'/mghp/ky-so?yeu-cau=2');const authorization=(await pending).headers().authorization;
   await p.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
   const status=await p.evaluate(async header=>(await fetch('/api/ctsv/tep/10040/noi-dung',{headers:{Authorization:header}})).status,authorization);
   pass(await p.locator('canvas').count()===0&&(status===403||status===404),'direct-viewer-and-file-denied-'+role,{backendStatus:status});
  }
  pass(x.writes.length===0,'no-business-writes-or-ca-signing',{writes:x.writes});pass(x.errors.length===0,'no-page-errors',{errors:x.errors});
 }
 return {stage:x.stage,database:'ctsv_dev',backend:'Spring Boot REAL',storage:'Cloudinary REAL',checks:x.checks,unexpectedWrites:x.writes,errors:x.errors};
}
