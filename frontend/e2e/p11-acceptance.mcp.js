async(page)=>{
 const browser=await page.context().browser().browserType().connectOverCDP('http://127.0.0.1:9337');
 const roles={};for(const p of browser.contexts().flatMap(c=>c.pages())){const name=await p.evaluate(()=>window.name);if(name.startsWith('p11-'))roles[name.slice(4)]=p;}
 const checks=[],errors=[];const check=(condition,name)=>{if(!condition)throw new Error(name);checks.push(name);};
 const base='https://127.0.0.1:5173',out='D:/7rd_semester/pbl6_src/ctsv/target/p11-evidence/browser/';
 for(const p of Object.values(roles))p.on('pageerror',e=>errors.push(e.message));
 const cv=roles.cv,pp=roles.pp,pht=roles.pht;
 for(const [name,p] of [['cv',cv],['pp',pp],['pht',pht]]){
  await p.goto(base+'/mghp/ky-so?yeu-cau=2');await p.locator('canvas[data-rendered-page="1"]').waitFor({timeout:10000});
  check((await p.locator('body').innerText()).includes('Đã ký xong'),name+': completed decision survives direct URL/refresh');
  check(await p.getByRole('button',{name:'Ký số',exact:true}).count()===0,name+': cannot sign a completed document again');
  check(await p.getByRole('button',{name:'Từ chối ký',exact:true}).count()===0,name+': no out-of-scope refusal');
 }
 await pp.getByRole('button',{name:'Xem biên bản đã ký',exact:true}).click();await pp.getByRole('heading',{name:'Biên bản MGHP học kỳ I · P11 DEV TEST',exact:true}).waitFor();await pp.locator('canvas[data-rendered-page="1"]').waitFor();
 check((await pp.locator('body').innerText()).includes('Biên bản MGHP học kỳ I · P11 DEV TEST'),'PP reads related completed minutes only');
 check(await pp.getByRole('button',{name:'Ký số',exact:true}).count()===0,'PP cannot sign related minutes');
 await pht.getByRole('tab',{name:'Lịch sử & phiên bản',exact:true}).click();
 check(await pht.getByRole('button',{name:'Xem PDF bản này',exact:true}).count()===3,'Decision history has original and two outputs');
 const hashes=[];
 for(let i=0;i<3;i++){
  await pht.getByRole('button',{name:'Xem PDF bản này',exact:true}).nth(i).click();
  await pht.locator('canvas[data-rendered-page="1"][data-rendered-file]').waitFor();
  const pending=pht.waitForEvent('download');await pht.getByRole('button',{name:'Tải PDF đang xem',exact:true}).click();const d=await pending;
  const value=await pht.evaluate(async url=>{const b=await(await fetch(url)).arrayBuffer();return {pdf:new TextDecoder().decode(b.slice(0,5)),size:b.byteLength,sha256:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),v=>v.toString(16).padStart(2,'0')).join('')};},d.url());
  check(value.pdf==='%PDF-'&&value.size>30000,'History '+i+': exact downloadable PDF bytes');hashes.push(value);
  await pht.getByRole('tab',{name:'Lịch sử & phiên bản',exact:true}).click();
 }
 check(new Set(hashes.map(h=>h.sha256)).size===3,'History: three distinct immutable artifacts');
 await pht.getByRole('button',{name:'← Danh sách tài liệu',exact:true}).click();await pht.locator('[aria-busy="false"]').waitFor();
 const handled=pht.waitForResponse(r=>r.url().includes('/tai-lieu-ky?')&&r.url().includes('view=handled'));await pht.getByRole('tab',{name:'Tôi đã xử lý',exact:true}).click();await handled;await pht.locator('[aria-busy="false"]').waitFor();
 await pht.getByRole('button',{name:'Xem tài liệu',exact:true}).nth(1).waitFor();check(await pht.getByRole('button',{name:'Xem tài liệu',exact:true}).count()===2,'PHT handled archive keeps both completed documents');
 await pht.setViewportSize({width:390,height:844});check(await pht.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'390px archive has no page overflow');
 await pht.getByRole('button',{name:'Xem tài liệu',exact:true}).first().click();await pht.locator('canvas[data-rendered-page="1"]').waitFor();
 check(await pht.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'390px PDF has no page overflow');await pht.screenshot({path:out+'18-final-mobile.png',fullPage:true});await pht.setViewportSize({width:1360,height:1000});
 for(const key of ['sv','admin']){
  const p=roles[key],pending=p.waitForRequest(r=>r.url().includes('/nen-tang/toi/quyen-giao-dien')&&!!r.headers().authorization);
  await p.goto(base+'/mghp/ky-so?yeu-cau=2');const authorization=(await pending).headers().authorization;
  await p.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
  check(await p.getByRole('link',{name:'Tài liệu ký số MGHP',exact:true}).count()===0,key+': route and navigation denied');
  const status=await p.evaluate(async h=>{try{return(await fetch('/api/ctsv/yeu-cau-ky/2',{headers:{Authorization:h}})).status;}catch{return 0;}},authorization);
  check(status===404,key+': real backend rejects direct object');
 }
 await cv.goto(base+'/thong-bao');await cv.getByRole('link',{name:'Xem tài liệu ký số',exact:true}).first().waitFor();check(await cv.getByRole('link',{name:'Xem tài liệu ký số',exact:true}).count()>=2,'Signing inbox links remain actionable');
 await cv.getByRole('link',{name:'Xem tài liệu ký số',exact:true}).first().click();await cv.locator('canvas[data-rendered-page="1"]').waitFor();check(cv.url().endsWith('yeu-cau=2'),'Inbox opens exact associated decision');
 await cv.screenshot({path:out+'19-final-desktop.png',fullPage:true});
 check(errors.length===0,'No browser pageerror in final regression');
 return {database:'ctsv_dev',backend:'https://127.0.0.1:8443',frontend:base,services:'REAL; no CA signing repeated by this read-only regression',checks,hashes,errors};
}
