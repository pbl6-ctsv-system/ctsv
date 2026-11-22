async(page)=>{
 page.setDefaultTimeout(6000);
 await page.setViewportSize({width:1440,height:1000});
 await page.goto('http://127.0.0.1:5180/');
 const captures=[],errors=[],api=[];
 page.on('pageerror',e=>errors.push(e.message));
 page.on('request',r=>{if(r.url().includes('/api/'))api.push(r.url());});
 const root='docs/prototypes/p10/';
 async function shot(file,title,check){
  if(!await check())throw Error('Missing expected content before screenshot: '+title);
  const toast=page.getByRole('button',{name:'Đóng thông báo',exact:true});
  if(await toast.count())await toast.click();
  await page.screenshot({path:root+file,fullPage:true,scale:'css'});
  captures.push({file,title,contentAsserted:true,viewport:page.viewportSize()});
 }
 await shot('review-01-nguon-da-dong-xet.png','Nguồn đã Đóng xét — trước khi tạo bộ',
  async()=>await page.getByText('Đã chốt nguồn',{exact:true}).isVisible()&&await page.getByRole('button',{name:'Tạo bộ xét',exact:true}).isVisible());
 await page.getByRole('button',{name:'Tạo bộ xét',exact:true}).click();
 await page.getByRole('textbox',{name:'Mã bộ quyết định',exact:true}).fill('MGHP-HKI-2026-DEMO');
 await shot('review-02-tao-bo-xet.png','Form tạo bộ xét',
  async()=>await page.getByRole('dialog').getByRole('heading',{name:'Tạo bộ xét Hội đồng',exact:true}).isVisible());
 await page.getByRole('dialog').getByRole('button',{name:'Tạo bộ xét',exact:true}).click();
 await shot('review-03-danh-sach-truoc-hop.png','Bản 1 — danh sách trước cuộc họp',
  async()=>await page.getByRole('heading',{name:'Danh sách trước cuộc họp',exact:true}).isVisible()&&await page.locator('#students tr').count()===3);
 await page.getByRole('row').filter({hasText:'Trần Hải Yến'}).getByRole('button',{name:'Xem căn cứ',exact:true}).click();
 await shot('review-04-can-cu-lua-chon.png','Căn cứ nguồn — M8 được chọn, M9 không được chọn',
  async()=>await page.getByRole('dialog').getByText('Được chọn',{exact:true}).isVisible()&&await page.getByRole('dialog').getByText('Không chọn',{exact:true}).isVisible());
 await page.getByRole('dialog').getByRole('button',{name:'Đóng',exact:true}).click();
 await page.getByRole('tab',{name:'Cuộc họp & văn bản',exact:true}).click();
 await shot('review-05-cuoc-hop-chua-ghi.png','Bản 1 — tab Cuộc họp & văn bản trước khi ghi',
  async()=>await page.getByRole('heading',{name:'Chưa ghi kết quả cuộc họp',exact:true}).isVisible());
 await page.getByRole('button',{name:'Ghi kết quả cuộc họp',exact:true}).click();
 await page.getByRole('textbox',{name:'Mã biên bản',exact:true}).fill('BB-MGHP-HKI-2026-01');
 await page.getByRole('textbox',{name:'Thời gian họp thực tế',exact:true}).fill('2026-11-22T09:00');
 await page.getByRole('textbox',{name:'Địa điểm',exact:true}).fill('Phòng họp CTSV — dữ liệu thử nghiệm');
 await page.getByRole('textbox',{name:'Kết luận và căn cứ',exact:true}).fill('Hội đồng thống nhất 3 nhóm đủ căn cứ; mức và thời gian hưởng theo dữ liệu đã xác minh.');
 await page.getByRole('textbox',{name:'Số quyết định (nếu có)',exact:true}).fill('123/QĐ-ĐHBK');
 await page.getByRole('textbox',{name:'Ngày quyết định (nếu có)',exact:true}).fill('2026-11-22');
 await shot('review-06-form-ghi-cuoc-hop.png','Form Ghi kết quả cuộc họp — đã nhập dữ liệu',
  async()=>await page.getByRole('textbox',{name:'Mã biên bản',exact:true}).inputValue()==='BB-MGHP-HKI-2026-01');
 await page.getByRole('button',{name:'Lưu bản sau họp',exact:true}).click();
 await shot('review-07-cuoc-hop-van-ban-sau-luu.png','Bản 2 — Cuộc họp & văn bản sau khi lưu',
  async()=>await page.getByRole('heading',{name:'Cuộc họp Hội đồng',exact:true}).isVisible()&&await page.getByText('BB-MGHP-HKI-2026-01',{exact:true}).isVisible()&&await page.getByText('123/QĐ-ĐHBK',{exact:true}).isVisible());
 await page.getByRole('tab',{name:/Danh sách hưởng/}).click();
 await shot('review-08-danh-sach-sau-hop.png','Bản 2 — danh sách sau họp, trước chốt',
  async()=>await page.getByRole('heading',{name:'Sẵn sàng kiểm tra và chốt',exact:true}).isVisible()&&await page.getByRole('button',{name:'Chốt dữ liệu',exact:true}).isVisible());
 await page.getByRole('tab',{name:'Lịch sử phiên bản',exact:true}).click();
 await shot('review-09-lich-su-sau-hop.png','Lịch sử sau họp — bản 1 và bản 2',
  async()=>await page.locator('.version').count()===2&&await page.getByRole('button',{name:'Xem bản 1',exact:true}).isVisible());
 await page.getByRole('button',{name:'Chốt dữ liệu',exact:true}).click();
 await shot('review-10-xac-nhan-chot.png','Hộp xác nhận Chốt dữ liệu',
  async()=>await page.getByRole('button',{name:'Xác nhận chốt dữ liệu',exact:true}).isVisible());
 await page.getByRole('button',{name:'Xác nhận chốt dữ liệu',exact:true}).click();
 await page.getByRole('tab',{name:/Danh sách hưởng/}).click();
 await shot('review-11-da-chot-danh-sach.png','Đã chốt — tab Danh sách hưởng',
  async()=>await page.getByRole('heading',{name:'Dữ liệu đã chốt',exact:true}).isVisible()&&await page.locator('#students tr').count()===3&&await page.getByRole('button',{name:'Chốt dữ liệu',exact:true}).count()===0);
 await page.getByRole('tab',{name:'Cuộc họp & văn bản',exact:true}).click();
 await shot('review-12-da-chot-cuoc-hop-van-ban.png','Đã chốt — tab Cuộc họp & văn bản',
  async()=>await page.getByRole('heading',{name:'Cuộc họp Hội đồng',exact:true}).isVisible()&&await page.getByText('BB-MGHP-HKI-2026-01',{exact:true}).isVisible()&&await page.getByRole('button',{name:'Sửa kết quả cuộc họp',exact:true}).count()===0);
 await page.getByRole('tab',{name:'Lịch sử phiên bản',exact:true}).click();
 await shot('review-13-da-chot-lich-su.png','Đã chốt — tab Lịch sử phiên bản',
  async()=>await page.locator('.version').count()===2&&await page.getByText('Bản 2 · Hiện hành',{exact:true}).isVisible());
 await page.getByRole('button',{name:'Xem bản 1',exact:true}).click();
 await shot('review-14-ban-cu-readonly.png','Mở lại bản 1 lịch sử — chỉ đọc',
  async()=>await page.getByText(/Bản lịch sử, chỉ đọc/).isVisible()&&await page.getByRole('button',{name:'Ghi kết quả cuộc họp',exact:true}).count()===0);
 await page.getByRole('tab',{name:'Lịch sử phiên bản',exact:true}).click();
 await page.getByRole('button',{name:'Xem bản 2',exact:true}).click();
 await page.getByRole('button',{name:'← Danh sách bộ xét',exact:true}).click();
 await shot('review-15-danh-sach-bo-da-chot.png','Danh sách bộ xét — bản hiện hành đã chốt',
  async()=>await page.getByRole('button',{name:'Xem bộ xét',exact:true}).isVisible()&&await page.getByText('Đã chốt dữ liệu',{exact:true}).isVisible());
 await page.getByRole('button',{name:'Xem bộ xét',exact:true}).click();
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('tab',{name:'Cuộc họp & văn bản',exact:true}).click();
 await shot('review-16-mobile-cuoc-hop-van-ban.png','Mobile — Cuộc họp & văn bản',
  async()=>await page.getByText('BB-MGHP-HKI-2026-01',{exact:true}).isVisible()&&await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.getByRole('tab',{name:'Lịch sử phiên bản',exact:true}).click();
 await shot('review-17-mobile-lich-su.png','Mobile — Lịch sử phiên bản',
  async()=>await page.locator('.version').count()===2&&await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.getByRole('tab',{name:/Danh sách hưởng/}).click();
 await shot('review-18-mobile-danh-sach.png','Mobile — Danh sách hưởng',
  async()=>await page.locator('#students tr').count()===3&&await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 if(errors.length||api.length)throw Error('Prototype has errors or business API calls: '+JSON.stringify({errors,api}));
 await page.setViewportSize({width:1440,height:1000});
 await page.getByRole('tab',{name:'Cuộc họp & văn bản',exact:true}).click();
 page.__p10FullReview={capturedAt:new Date().toISOString(),url:page.url(),mode:'SYNTHETIC prototype STUB; no API/SQL',fixture:{students:3,versions:2,code:'MGHP-HKI-2026-DEMO',minutes:'BB-MGHP-HKI-2026-01',meetingAtLocal:'2026-11-22T09:00',decisionNumber:'123/QĐ-ĐHBK'},captures,errors,businessApiCalls:api.length};
 return page.__p10FullReview;
}
