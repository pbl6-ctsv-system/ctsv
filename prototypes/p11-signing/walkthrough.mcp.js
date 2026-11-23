async(page)=>{
 const root='D:/7rd_semester/pbl6_src/ctsv/prototypes/p11-signing';
 const shots=[];let checks=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const expect=(value,message)=>{if(!value)throw Error(message);checks++};
 const shot=async(name)=>{await page.screenshot({path:root+'/screenshots/'+name+'.png',fullPage:true});shots.push(name)};
 const canvas=async()=>{await page.waitForFunction(()=>Number(document.querySelector('#pdf-canvas')?.dataset.renderedPage)===window.p11Prototype.page);await page.waitForTimeout(150)};
 const role=async(value)=>{await page.locator('#role').selectOption(value);await page.waitForURL('**/#list')};
 const open=async(kind)=>{await page.goto('http://127.0.0.1:5178/#document/'+kind);await canvas()};
 const region=async({x=70,y=430,w=180,h=58}={})=>{
  await page.locator('#page').fill('2');await page.locator('#page').dispatchEvent('change');await page.waitForFunction(()=>window.p11Prototype.page===2);await page.waitForTimeout(200);
  await page.locator('#choose').click();const box=await page.locator('#overlay').boundingBox();await page.mouse.move(box.x+x,box.y+y);await page.mouse.down();await page.mouse.move(box.x+x+w,box.y+y+h,{steps:12});await page.mouse.up();
  const g=await page.evaluate(()=>window.p11Prototype.geometry);expect(g?.page===2&&g.width>0&&g.height>0,'valid page2 geometry');return g;
 };
 const sign=async(prefix,pp=false)=>{
  await page.locator('#sign').click();await shot(prefix+'-xac-nhan');
  if(pp){await page.locator('#confirm-sign').click();expect(await page.locator('#ack-error').isVisible(),'PP must acknowledge');await shot(prefix+'-thieu-xac-nhan-da-xem');await page.locator('#pp-ack').check()}
  await page.locator('#confirm-sign').click();await shot(prefix+'-dang-xu-ly');await page.waitForFunction(()=>!document.querySelector('dialog').open);await canvas();await shot(prefix+'-da-luu');
 };
 const resume=await page.evaluate(()=>window.p11Prototype?.state.docs.bb.status==='ready'&&window.p11Prototype?.state.docs.qd.status==='draft');
 if(!resume){
 await page.goto('http://127.0.0.1:5178');await page.evaluate(()=>localStorage.removeItem('ctsv-p11-prototype-v1'));await page.reload();await page.setViewportSize({width:1440,height:1100});
 expect(await page.getByRole('heading',{name:'Tài liệu ký số MGHP',exact:true}).count()===1,'list visible');await shot('01-cv-danh-sach');
 await page.getByRole('button',{name:'Chuẩn bị bộ văn bản',exact:true}).click();await shot('02-cv-chon-nguon-da-chot');await page.getByRole('button',{name:'Mở bộ văn bản',exact:true}).click();await shot('03-cv-bo-bien-ban-quyet-dinh');
 await page.getByRole('button',{name:'Chuẩn bị PDF',exact:true}).first().click();await shot('04-cv-upload-bien-ban');
 await page.locator('#save-pdf').click();expect(await page.locator('#toast').isVisible(),'upload error visible');await shot('05-cv-loi-chua-chon-pdf');
 await page.locator('#pdf-file').setInputFiles(root+'/assets/bb.pdf');await page.locator('#content-checked').check();await shot('06-cv-da-chon-pdf');await page.locator('#save-pdf').click();await canvas();expect(await page.locator('#refuse').count()===0,'no refusal CV');await shot('07-cv-xem-bien-ban');
 await page.locator('#sign').click();expect((await page.locator('#toast').textContent()).includes('chưa chọn vị trí'),'missing region visible');await shot('08-cv-loi-chua-chon-vung');
}else{await page.goto('http://127.0.0.1:5178/#document/bb');await page.reload();await page.setViewportSize({width:1440,height:1100});await canvas();await shot('07-cv-xem-bien-ban');await page.locator('#sign').click();await shot('08-cv-loi-chua-chon-vung');}
 const first=await region();await shot('09-cv-chon-vung-trang-2');
 let b=await page.locator('.signature-box').boundingBox();await page.mouse.move(b.x+30,b.y+20);await page.mouse.down();await page.mouse.move(b.x+60,b.y+40,{steps:8});await page.mouse.up();expect((await page.evaluate(()=>window.p11Prototype.geometry.x))>first.x,'move changes geometry');await shot('10-cv-di-chuyen-vung');
 b=await page.locator('.resize').boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.down();await page.mouse.move(b.x+45,b.y+20,{steps:8});await page.mouse.up();expect((await page.evaluate(()=>window.p11Prototype.geometry.width))>first.width,'resize changes width');await shot('11-cv-doi-kich-thuoc');
 const g=await page.evaluate(()=>window.p11Prototype.geometry);await page.locator('#zoom-in').click();await page.waitForTimeout(200);expect(JSON.stringify(await page.evaluate(()=>window.p11Prototype.geometry))===JSON.stringify(g),'zoom preserves PDF points');await shot('12-cv-phong-to');await page.locator('#zoom-out').click();await page.waitForTimeout(150);
 await sign('13-cv-ky-bien-ban');expect(await page.evaluate(()=>window.p11Prototype.state.docs.bb.steps.length)===1,'first stored');expect(await page.locator('#sign').count()===0,'CV cannot sign PHT step');expect(await page.locator('#replace').count()===0,'CV cannot replace signed PDF');
 await page.locator('#view-history').click();await shot('14-cv-lich-su-mot-chu-ky');await page.getByRole('button',{name:'Sau Chuyên viên MGHP ký',exact:true}).click();await page.waitForTimeout(300);await shot('15-cv-xem-ban-mot-chu-ky');await page.getByRole('button',{name:'Đóng',exact:true}).click();
 await role('pht');await shot('16-pht-danh-sach');await open('bb');expect(await page.locator('#refuse').count()===0,'no refusal PHT');await shot('17-pht-nhan-ban-cv-da-ky');await region({x:340,y:430,w:175,h:55});await shot('18-pht-vung-ky-rieng');await sign('19-pht-ky-bien-ban');expect(await page.evaluate(()=>window.p11Prototype.state.docs.bb.status)==='completed','minutes complete');
 await role('cv');await page.goto('http://127.0.0.1:5178/#bundle');await shot('20-cv-bien-ban-xong-chuan-bi-quyet-dinh');await page.getByRole('button',{name:'Chuẩn bị PDF',exact:true}).click();await page.locator('#pdf-file').setInputFiles(root+'/assets/qd.pdf');await page.locator('#content-checked').check();await shot('21-cv-upload-quyet-dinh');await page.locator('#save-pdf').click();await canvas();expect(await page.locator('#sign').count()===0,'CV never signs decision');await shot('22-cv-quyet-dinh-san-sang-gui');await page.locator('#send-pp').click();await shot('23-cv-xac-nhan-gui-pp');await page.locator('#confirm-send').click();await shot('24-cv-da-gui-pp');
 await role('pp');await shot('25-pp-danh-sach');await page.getByRole('link',{name:/THÔNG BÁO/}).click();await shot('26-pp-thong-bao');await page.getByRole('button',{name:'Xem tài liệu',exact:true}).first().click();await canvas();await shot('27-pp-quyet-dinh-va-bien-ban-lien-quan');expect(await page.locator('#refuse').count()===0,'no refusal PP');
 await page.getByRole('button',{name:'Xem biên bản',exact:true}).click();await canvas();await shot('28-pp-xem-bien-ban-lien-quan');await open('qd');await region();await sign('29-pp-ky-quyet-dinh',true);expect(await page.evaluate(()=>window.p11Prototype.state.docs.qd.steps.length)===1,'PP stored step');
 await role('pht');await open('qd');await shot('30-pht-quyet-dinh-da-co-chu-ky-pp');await region({x:340,y:430,w:175,h:55});await sign('31-pht-ky-quyet-dinh');expect(await page.evaluate(()=>window.p11Prototype.state.docs.qd.status)==='completed','decision complete');expect((await page.locator('#app').textContent()).includes('Công bố được thực hiện ở bước riêng'),'does not publish');
 const download=page.waitForEvent('download');await page.locator('#download').click();const file=await download;expect(file.suggestedFilename().endsWith('.pdf'),'downloads PDF');await file.saveAs(root+'/screenshots/demo-decision-two-stamps.pdf');await shot('32-pht-tai-pdf-da-luu');
 await page.reload();await canvas();expect(await page.evaluate(()=>window.p11Prototype.state.docs.qd.steps.length)===2,'refresh persisted');await shot('33-pht-refresh-van-giu-ket-qua');
 await page.locator('#view-history').click();await shot('34-pht-lich-su-hai-nguoi');await page.getByRole('button',{name:'Sau Phó phòng ký',exact:true}).click();await page.waitForTimeout(250);await shot('35-pht-xem-ban-mot-nguoi-trong-lich-su');await page.getByRole('button',{name:'Đóng',exact:true}).click();
 await page.goto('http://127.0.0.1:5178/#list');await page.getByRole('tab',{name:/Tôi đã xử lý/}).click();await shot('36-pht-kho-da-xu-ly');
 await page.setViewportSize({width:390,height:844});await open('qd');await shot('37-mobile-pht-chi-tiet');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no page horizontal overflow');
 await page.setViewportSize({width:1440,height:1100});expect(errors.length===0,'no page errors: '+errors.join(';'));
 return {checks,shots,errors,scope:'PROTOTYPE SIMULATED; no business backend/CA'};
}
