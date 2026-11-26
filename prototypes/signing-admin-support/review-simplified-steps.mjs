// Prototype-only UX checks. No backend, CA, Cloudinary or SQL.
import fs from 'node:fs';
export async function mobilePictures(page,h){
 const saved=await h.state();
 await page.evaluate(()=>{const key='ctsv.prototype.signing-admin.v1',s=JSON.parse(localStorage.getItem(key));const i=s.incidents.find(i=>i.id==='SC-004');i.status='waiting';delete i.startedAt;delete i.closedAt;i.history=i.history.filter(h=>!h.text.startsWith('Admin '));s.role='admin';s.page='incidents';s.incident='SC-004';localStorage.setItem(key,JSON.stringify(s));});
 await page.setViewportSize({width:390,height:844});await page.reload();
 const shoot=async title=>{const button=page.locator('[data-action="dismiss-toast"]');if(await button.count())await button.click();const step=JSON.parse(fs.readFileSync(new URL('./review.json',import.meta.url),'utf8')).steps.find(s=>s.title===title);h.assert(step);await page.screenshot({path:new URL('./'+step.file,import.meta.url).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});};
 await simpleDetail(page,h,'waiting');await shoot('Mobile thông tin sự cố và thao tác');
 await h.act('start','SC-004');await simpleDetail(page,h,'working');await shoot('Mobile nút Kết thúc dễ tìm');
 await h.act('finish','SC-004');await simpleDetail(page,h,'resolved');await shoot('Mobile đã kết thúc vẫn giữ lịch sử');
 await page.evaluate(s=>localStorage.setItem('ctsv.prototype.signing-admin.v1',JSON.stringify(s)),saved);await page.setViewportSize({width:1440,height:1050});await page.reload();
 console.log('Retook three mobile images after dismissing transient toast; prototype-only fixture restored.');
}
export async function resume(page,h){
 console.log('Retained DOM: '+JSON.stringify({dialogs:await page.getByRole('dialog').count(),open:await page.getByRole('dialog').isVisible(),buttons:await page.getByRole('dialog').getByRole('button').allTextContents()}));
 if(await page.getByRole('dialog').isVisible())await page.getByRole('button',{name:'Quay lại',exact:true}).click();
 await page.setViewportSize({width:1440,height:1050});
}
const unchanged=i=>({ca:i.ca,download:i.download,cloud:i.cloud,sql:i.sql,local:i.local,resolution:i.resolution});
async function simpleDetail(page,h,state){
 h.assert.deepEqual(await page.locator('.split > div.stack > section > h2, .split > div.stack > section > .card-head h2, .error-title h2').allTextContents(),['Thông tin sự cố','Ghi chú xử lý','Lịch sử xử lý']);
 const actions=await page.locator('.side button').allTextContents();
 h.assert.deepEqual(actions.map(v=>v.trim()),state==='waiting'?['Bắt đầu xử lý','Sao chép thông tin hỗ trợ']:state==='working'?['Kết thúc xử lý','Sao chép thông tin hỗ trợ']:['Sao chép thông tin hỗ trợ']);
 h.assert.equal(await page.locator('[data-action="check-incident"], [data-action="recover"], [data-action="refresh-incident"], [data-action="view-signer"]').count(),0);
}
export async function inspect(page,h){
 const g='01 · Danh sách và thông tin sự cố';
 h.assert.equal(await page.locator('tr[data-incident]').count(),6);
 h.assert.deepEqual(await page.locator('thead th').allTextContents(),['Văn bản / Đợt xét','Người thao tác','Bước gặp lỗi','Thời điểm','Trạng thái','']);
 await h.capture(g,'Danh sách sự cố giữ nguyên bố cục','Giữ bảng, bộ lọc trạng thái và tìm kiếm mà owner đã duyệt.');h.pass('Danh sách giữ cột và sáu sự cố mô phỏng');
 await h.openIncident('SC-001');await inspectDetail(page,h);
}
export async function inspectDetail(page,h){
 const g='01 · Danh sách và thông tin sự cố';
 await simpleDetail(page,h,'waiting');
 h.assert((await page.locator('.split').innerText()).includes('NoSuchKey'));
 await h.capture(g,'Chi tiết gọn ba phần và hai nút','Chỉ còn thông tin sự cố, ghi chú, lịch sử; Bắt đầu xử lý và Sao chép.');
 await page.getByText('Thông tin chẩn đoán và tài liệu',{exact:true}).click();
 h.assert(await page.getByText('DEMO-DOC-001 / DEMO-SIGN-001',{exact:true}).isVisible());
 await h.capture(g,'Thông tin kỹ thuật cần gửi CA','Mở chi tiết mã tài liệu, phiên bản và biên nhận để Admin trao đổi bên ngoài.');
 await page.getByText('Thông tin chẩn đoán và tài liệu',{exact:true}).click();h.pass('Chi tiết bỏ tiến độ ký PDF và toàn bộ nút phục hồi riêng');
}
export async function incidents(page,h){
 const g='02 · Bắt đầu ghi chú và kết thúc';
 const before=unchanged((await h.state()).incidents[0]);
 await h.act('start','SC-001');await simpleDetail(page,h,'working');
 h.assert.equal((await h.state()).incidents[0].history.filter(x=>x.text==='Admin bắt đầu xử lý sự cố.').length,1);
 await h.capture(g,'Bắt đầu đổi ngay sang Kết thúc xử lý','Ghi trạng thái Đang xử lý và thời điểm vào lịch sử.');h.pass('Bắt đầu chuyển trạng thái và ghi lịch sử một lần');
 await h.act('copy','SC-001');
 const text=await page.getByRole('textbox',{name:'Thông tin hỗ trợ',exact:true}).inputValue();
 for(const value of ['NoSuchKey','chuyenvien.demo','BB-MGHP-2026-01','DEMO-SIGN-001','Thời điểm','API'])h.assert(text.includes(value));
 await h.capture(g,'Sao chép thông tin hỗ trợ','Thông tin đủ người thao tác tài liệu API lỗi và thời điểm; Admin gửi bằng ứng dụng khác.');
 await page.getByRole('button',{name:'Sao chép nội dung',exact:true}).click();
 h.assert.equal((await page.evaluate(()=>navigator.clipboard.readText())).replaceAll('\r\n','\n'),text);
 await page.getByRole('button',{name:'Quay lại',exact:true}).click();h.pass('Sao chép vào clipboard đủ ngữ cảnh và biên nhận gốc');
 await h.act('note','SC-001');
 await page.getByRole('textbox',{name:'Nội dung ghi chú',exact:true}).fill('Đã trao đổi với CA DUT qua ứng dụng ngoài; Admin đang xử lý trực tiếp theo kết quả kiểm tra.');
 await page.getByRole('textbox',{name:'Tham chiếu trao đổi',exact:true}).fill('Trao đổi CA ngày 25/11');
 await h.capture(g,'Nhập ghi chú xử lý bên ngoài','Không bắt nhập thông số khôi phục hoặc thực hiện thao tác kỹ thuật trong UI.');
 await page.getByRole('button',{name:'Lưu ghi chú',exact:true}).click();
 h.assert.equal((await h.state()).incidents[0].notes.length,1);
 await h.capture(g,'Lưu ghi chú kèm lịch sử','Giữ nội dung ghi chú người ghi và thời điểm; phiếu vẫn Đang xử lý.');
 await page.reload();await simpleDetail(page,h,'working');
 h.assert(await page.getByText('Đã trao đổi với CA DUT qua ứng dụng ngoài; Admin đang xử lý trực tiếp theo kết quả kiểm tra.',{exact:true}).isVisible());
 h.pass('Ghi chú trạng thái và lịch sử giữ sau refresh');
 await h.act('finish','SC-001');await simpleDetail(page,h,'resolved');
 const done=(await h.state()).incidents[0];h.assert.equal(done.status,'resolved');h.assert(done.closedAt);h.assert.deepEqual(unchanged(done),before);
 await h.capture(g,'Kết thúc xử lý chỉ đóng phiếu','Đã xử lý, có thời gian kết thúc; thông tin lỗi gốc vẫn giữ để tra cứu.');
 await page.reload();await simpleDetail(page,h,'resolved');h.assert.equal((await h.state()).incidents[0].history.filter(x=>x.text==='Admin kết thúc xử lý sự cố.').length,1);
 h.pass('Kết thúc lưu phiếu và thời điểm không tự đổi ký Cloudinary SQL');
 await h.act('copy','SC-001');h.assert((await page.getByRole('textbox',{name:'Thông tin hỗ trợ',exact:true}).inputValue()).includes('NoSuchKey'));await page.getByRole('button',{name:'Quay lại',exact:true}).click();
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Đã xử lý/}).click();
 h.assert.equal(await page.locator('tr[data-incident="SC-001"]').count(),1);
 await h.capture(g,'Tra cứu sự cố đã kết thúc','Sự cố chuyển sang mục Đã xử lý, vẫn mở lại xem thông tin và lịch sử.');h.pass('Bộ lọc danh sách cập nhật sau kết thúc');
 await h.role('cv');await h.act('user-open','SC-001');
 h.assert(await page.getByRole('heading',{name:'Tiến trình ký',exact:true}).isVisible());
 h.assert.equal(await page.locator('[data-action="user-sign"]').count(),0);
 h.assert.equal(await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).count(),0);
 h.assert((await page.locator('.page-head').innerText()).includes('Cần xử lý thêm'));
 await h.capture('03 · Trạng thái tài liệu độc lập','Đóng phiếu không tự làm tài liệu ký xong','Kết quả ký và PDF lấy từ dữ liệu thực tế; nút Kết thúc chỉ ghi nhận xử lý của Admin.');h.pass('Góc nhìn người ký không bị giả hoàn tất khi chỉ đóng phiếu');
 await h.role('admin');
}
export async function variants(page,h){
 const g='04 · Cùng một cách xử lý mọi sự cố';
 for(const id of ['SC-002','SC-003','SC-004','SC-005','SC-006']){
  await h.openIncident(id);await simpleDetail(page,h,'waiting');
  const i=(await h.state()).incidents.find(i=>i.id===id),before=unchanged(i);
  await h.capture(g,id==='SC-005'?'Lỗi400 cũng chỉ Bắt đầu và Kết thúc':'Thông tin lỗi '+i.stage,'Cùng bố cục thông tin ghi chú lịch sử; không có công cụ phục hồi theo từng loại lỗi.');
  if(id==='SC-004'||id==='SC-006')continue;
  await h.act('start',id);await simpleDetail(page,h,'working');
  if(id==='SC-005')await h.capture(g,'Lỗi400 đang xử lý','Không có nút mở lại bước ký hoặc sửa tham số; Admin xử lý bên ngoài rồi kết thúc.');
  await h.act('finish',id);await simpleDetail(page,h,'resolved');
  h.assert.deepEqual(unchanged((await h.state()).incidents.find(i=>i.id===id)),before);
  if(id==='SC-005')await h.capture(g,'Kết thúc lỗi400','Chỉ ghi nhận phiếu Đã xử lý, không tự gọi upload ký hay thay đổi dữ liệu tài liệu.');
 }
 h.pass('Cloudinary SQL chứng thư hash và400 dùng cùng giao diện đơn giản');
 h.pass('Kết thúc sự cố không tự sửa dữ liệu PDF hoặc mở lại ký');
}
export async function mobile(page,h){
 if(await page.getByRole('dialog').isVisible())await page.getByRole('button',{name:'Quay lại',exact:true}).click();
 const g='05 · Mobile';
 await page.setViewportSize({width:390,height:844});await h.openIncident('SC-004');
 h.assert(await page.locator('.error-title').boundingBox());
 h.assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await simpleDetail(page,h,'waiting');await h.capture(g,'Mobile thông tin sự cố và thao tác','Thông tin lỗi đứng trước thao tác, có đầy đủ ghi chú và lịch sử bên dưới.');
 await h.act('start','SC-004');await simpleDetail(page,h,'working');
 await h.capture(g,'Mobile nút Kết thúc dễ tìm','Sau khi bắt đầu chỉ còn Kết thúc xử lý và Sao chép ở khung hành động.');
 await h.act('finish','SC-004');await simpleDetail(page,h,'resolved');
 await h.capture(g,'Mobile đã kết thúc vẫn giữ lịch sử','Không thêm bước kiểm tra hoặc hộp nhập bắt buộc.');
 h.pass('Mobile bắt đầu kết thúc và không tràn ngang');await page.setViewportSize({width:1440,height:1050});
}
export async function certificates(page,h){
 const g='06 · Chứng thư giữ nguyên';
 await page.getByRole('button',{name:'Quản lý chứng thư',exact:true}).click();
 h.assert.equal(await page.locator('tr[data-cert]').count(),2);await h.capture(g,'Danh sách chứng thư không thay đổi','Giữ các chức năng kiểm tra và gửi yêu cầu gia hạn đã trao đổi.');
 await h.act('open-cert','e');const before=(await h.state()).certificates[0].validTo;
 await h.act('renew','e');await h.capture(g,'Xác nhận gửi đề nghị renew','Chứng thư vẫn có luồng gửi đề nghị gia hạn chờ CA duyệt; lần này chỉ mô phỏng.');
 await page.getByRole('button',{name:'Xác nhận gửi yêu cầu',exact:true}).click();await h.settled();
 const c=(await h.state()).certificates[0];h.assert.equal(c.renew.state,'pending');h.assert.equal(c.validTo,before);
 await h.capture(g,'Đã gửi đề nghị chờ CA duyệt','Ngày hết hạn chưa đổi; không có API CA thật được gọi.');h.pass('Chứng thư renew mô phỏng vẫn gửi chờ và không tự gia hạn');
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Tất cả/}).click();
}
export async function gallery(page,h){
 await page.goto(h.origin+'/review.html');await page.getByRole('button',{name:'Xem từ ảnh đầu tiên',exact:true}).click();
 await page.locator('#full-image').evaluate(img=>img.decode());h.assert(await page.locator('#full-image').evaluate(img=>img.complete&&img.naturalWidth>0));
 await page.locator('#image-next').click();h.assert((await page.locator('#image-step').innerText()).startsWith('Ảnh 2 /'));
 await page.locator('#image-prev').click();h.assert((await page.locator('#image-step').innerText()).startsWith('Ảnh 1 /'));
 await page.getByRole('button',{name:'Đóng ảnh',exact:true}).click();h.pass('Gallery mới mở ảnh và đi tới lui đúng trình tự');
}

export async function incidentsFinish(page,h){
 const g='02 · Bắt đầu ghi chú và kết thúc';
 await h.openIncident('SC-001');
 const before=unchanged((await h.state()).incidents[0]);
 await h.act('copy','SC-001');
 const text=await page.getByRole('textbox',{name:'Thông tin hỗ trợ',exact:true}).inputValue();
 await page.getByRole('button',{name:'Sao chép nội dung',exact:true}).click();
 h.assert.equal((await page.evaluate(()=>navigator.clipboard.readText())).replaceAll('\r\n','\n'),text);
 await page.getByRole('button',{name:'Quay lại',exact:true}).click();h.pass('Sao chép vào clipboard đủ ngữ cảnh và biên nhận gốc');
 await h.act('note','SC-001');
 await page.getByRole('textbox',{name:'Nội dung ghi chú',exact:true}).fill('Đã trao đổi với CA DUT qua ứng dụng ngoài; Admin đang xử lý trực tiếp theo kết quả kiểm tra.');
 await page.getByRole('textbox',{name:'Tham chiếu trao đổi',exact:true}).fill('Trao đổi CA ngày 25/11');
 await h.capture(g,'Nhập ghi chú xử lý bên ngoài','Không bắt nhập thông số khôi phục hoặc thực hiện thao tác kỹ thuật trong UI.');
 await page.getByRole('button',{name:'Lưu ghi chú',exact:true}).click();
 h.assert.equal((await h.state()).incidents[0].notes.length,1);
 await h.capture(g,'Lưu ghi chú kèm lịch sử','Giữ nội dung ghi chú người ghi và thời điểm; phiếu vẫn Đang xử lý.');
 await page.reload();await simpleDetail(page,h,'working');
 h.assert(await page.getByText('Đã trao đổi với CA DUT qua ứng dụng ngoài; Admin đang xử lý trực tiếp theo kết quả kiểm tra.',{exact:true}).isVisible());
 h.pass('Ghi chú trạng thái và lịch sử giữ sau refresh');
 await h.act('finish','SC-001');await simpleDetail(page,h,'resolved');
 const done=(await h.state()).incidents[0];h.assert.equal(done.status,'resolved');h.assert(done.closedAt);h.assert.deepEqual(unchanged(done),before);
 await h.capture(g,'Kết thúc xử lý chỉ đóng phiếu','Đã xử lý, có thời gian kết thúc; thông tin lỗi gốc vẫn giữ để tra cứu.');
 await page.reload();await simpleDetail(page,h,'resolved');h.assert.equal((await h.state()).incidents[0].history.filter(x=>x.text==='Admin kết thúc xử lý sự cố.').length,1);
 h.pass('Kết thúc lưu phiếu và thời điểm không tự đổi ký Cloudinary SQL');
 await h.act('copy','SC-001');h.assert((await page.getByRole('textbox',{name:'Thông tin hỗ trợ',exact:true}).inputValue()).includes('NoSuchKey'));await page.getByRole('button',{name:'Quay lại',exact:true}).click();
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Đã xử lý/}).click();
 h.assert.equal(await page.locator('tr[data-incident="SC-001"]').count(),1);
 await h.capture(g,'Tra cứu sự cố đã kết thúc','Sự cố chuyển sang mục Đã xử lý, vẫn mở lại xem thông tin và lịch sử.');h.pass('Bộ lọc danh sách cập nhật sau kết thúc');
 await h.role('cv');await h.act('user-open','SC-001');
 h.assert(await page.getByRole('heading',{name:'Tiến trình ký',exact:true}).isVisible());
 h.assert.equal(await page.locator('[data-action="user-sign"]').count(),0);
 h.assert.equal(await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).count(),0);
 h.assert((await page.locator('.page-head').innerText()).includes('Cần xử lý thêm'));
 await h.capture('03 · Trạng thái tài liệu độc lập','Đóng phiếu không tự làm tài liệu ký xong','Kết quả ký và PDF lấy từ dữ liệu thực tế; nút Kết thúc chỉ ghi nhận xử lý của Admin.');h.pass('Góc nhìn người ký không bị giả hoàn tất khi chỉ đóng phiếu');
 await h.role('admin');
}

export async function galleryFinish(page,h){
 await page.locator('#image-next').click();h.assert((await page.locator('#image-step').innerText()).startsWith('Ảnh 2 /'));
 await page.locator('#image-prev').click();h.assert((await page.locator('#image-step').innerText()).startsWith('Ảnh 1 /'));
 await page.getByRole('button',{name:'Đóng ảnh',exact:true}).click();h.pass('Gallery mới mở ảnh và đi tới lui đúng trình tự');
}
