// One metadata check on the recorded synthetic root; do not run on another case.
async(page)=>{
 const x=page.__p1,c=x?.accounts?.staff;
 if(x?.createdApplication!=='10028')throw Error('The owned P1 synthetic root is required');
 await c.goto('https://127.0.0.1:5173/mghp/tiep-nhan-ho-so');await c.locator('[data-application-id="10028"]').click();
 await c.locator('canvas[data-rendered-file][data-rendered-page]').waitFor();
 const details=c.locator('details').filter({has:c.locator('summary').filter({hasText:'Kiểm tra điều kiện giấy tờ khác'})});await details.locator('summary').click();
 const item=details.locator('div').filter({has:c.locator('span').filter({hasText:/^Đúng chủ thể$/})}).last();
 const button=item.getByRole('button',{name:'Xác nhận',exact:true});
 if(!await button.count())throw Error('Already checked: inspect evidence instead of repeating a write');
 let extra=0;const observe=r=>{if(r.method()==='GET'&&/\/api\/ctsv\/tep\/\d+\/noi-dung$/.test(r.url()))extra++;};c.on('request',observe);
 try{
  const response=c.waitForResponse(r=>r.url().endsWith('/kiem-tra')&&r.request().method()==='POST');await button.click();if((await response).status()!==201)throw Error('Metadata check failed');
  await item.getByRole('button',{name:'✓ Đã xác nhận',exact:true}).waitFor();await c.getByText('Đã xác nhận kiểm tra giấy tờ.',{exact:true}).waitFor();
  if(extra!==0)throw Error('Metadata refresh remounted unchanged PDF: '+extra+' GETs');
  x.cases.push({id:'P1-PDF-FINAL-METADATA-NO-REMOUNT',status:'PASS',unexpectedPdfReads:extra,database:'ctsv_dev'});
  return {status:'PASS',extraPdfReads:extra};
 }finally{c.off('request',observe);}
}
