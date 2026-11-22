async(page)=>{
 const x=page.__p1,c=x?.accounts?.staff;if(!c||!x.createdApplication)throw Error('Use the owned P1 synthetic application');
 const id=x.createdApplication,assert=(v,m)=>{if(!v)throw Error(m);};
 if(x.reviewPhase==='approve'){
  await c.goto('https://127.0.0.1:5173/mghp/tiep-nhan-ho-so');await c.locator('[data-application-id="'+id+'"]').click();
  await c.getByText('0 / 3 giấy tờ đã đồng ý nội dung',{exact:true}).waitFor();assert(await c.getByRole('button',{name:'Đồng ý hồ sơ',exact:true}).isDisabled(),'Approval must explain pending PDFs');
  await c.getByRole('button',{name:'Xem giấy cần kiểm tra',exact:true}).click();
  for(let i=1;i<=3;i++){
   const pending=c.waitForResponse(r=>r.url().includes('/giay-to-da-nop/')&&r.url().endsWith('/kiem-tra')&&r.request().method()==='POST');
   await c.getByRole('button',{name:'Đồng ý PDF',exact:true}).click();const response=await pending;assert(response.status()===201,'PDF check failed');await c.getByText(i+' / 3 giấy tờ đã đồng ý nội dung',{exact:true}).waitFor();
  }
  await c.getByRole('button',{name:'Đồng ý hồ sơ',exact:true}).click();assert(await c.getByRole('dialog').getByRole('textbox').count()===0,'Approving must not require rejection reason');
  const pending=c.waitForResponse(r=>r.url().endsWith('/ho-so/'+id+'/ket-luan')&&r.request().method()==='POST');await c.getByRole('button',{name:'Xác nhận kết luận',exact:true}).click();const result=await pending;assert(result.status()===200,'Conclusion request failed');await c.getByRole('status').filter({hasText:'Đã đồng ý hồ sơ.'}).waitFor();
  await c.getByRole('link',{name:'Tiếp tục đánh giá căn cứ và thời gian hưởng',exact:true}).waitFor();x.cases.push({id:'P1-04-APPROVAL_BLOCKERS_AND_SUCCESS',status:'PASS',applicationId:id,database:'ctsv_dev'});
 }
 if(x.reviewPhase==='evaluation'){
  const link=c.getByRole('link',{name:'Tiếp tục đánh giá căn cứ và thời gian hưởng',exact:true});await link.click();await c.getByRole('button',{name:'Chuẩn bị đánh giá',exact:true}).waitFor();assert(c.url().includes('ho-so='+id),'Wrong application context');
  await c.getByRole('button',{name:'Chuẩn bị đánh giá',exact:true}).click();await c.getByRole('region',{name:'PDF và nguồn căn cứ',exact:true}).waitFor();
  const choices=c.getByRole('region',{name:'PDF và nguồn căn cứ',exact:true}).getByRole('button');assert(await choices.count()===3,'Missing named documents');await choices.first().click();await c.locator('iframe[title="Nội dung PDF đang xem"]').waitFor();
  await c.getByRole('button',{name:'Thêm giấy làm căn cứ',exact:true}).click();const papers=c.getByRole('combobox',{name:'Giấy đã nộp',exact:true});const names=await papers.locator('option').allTextContents();assert(names.some(n=>n.includes('Bản nộp 1'))&&!names.some(n=>/^Giấy #/.test(n)),'Raw document IDs remain');
  await c.getByRole('textbox',{name:'Lý do dùng giấy',exact:true}).fill('Căn cứ synthetic P1 đang chờ xác minh; chỉ lưu bản nháp, chưa xác nhận kết quả.');
  await c.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).click();await c.getByRole('status').filter({hasText:'Đã lưu đánh giá và đọc lại dữ liệu mới'}).waitFor();
  x.cases.push({id:'P1-05-CONTEXT_PDF_AND_SAVED_DRAFT',status:'PASS',applicationId:id,database:'ctsv_dev'});
 }
 return x.cases;
}
