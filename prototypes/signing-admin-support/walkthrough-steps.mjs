export async function inspect(page,h){
 await page.getByRole('tab',{name:/^Chờ xử lý/}).waitFor();h.assert.equal(await page.locator('[data-incident]').count(),6);
 await h.capture('01 · Sự cố và liên hệ CA','Danh sách sự cố ký số','Admin mở Xử lý sự cố ký số. Sáu loại lỗi, không có phân công nhiều Admin.');
 await h.openIncident('SC-001');await page.getByRole('heading',{name:'Cần Admin xem xét',exact:true}).waitFor();
 await h.capture('01 · Sự cố và liên hệ CA','Chi tiết NoSuchKey và thông tin thao tác','Nhấn Xem xử lý: thấy role, tài khoản, văn bản, API, lỗi và thời gian.');
 h.assert.equal(await page.getByRole('button',{name:'Bắt đầu xử lý',exact:true}).count(),1);h.pass('Admin tìm được sự cố và thấy đúng ngữ cảnh');
 console.log('BUTTONS '+JSON.stringify(await page.getByRole('button').allTextContents()));
}

export async function incidents(page,h){
 const g='01 · Sự cố và liên hệ CA';
 await h.act('start');h.assert.equal((await h.state()).incidents[0].status,'working');
 await h.capture(g,'Bắt đầu xử lý NoSuchKey','Admin bấm Bắt đầu xử lý. Chỉ đổi trạng thái phiếu, không gửi ký.');
 await h.act('copy','SC-001');await page.getByRole('dialog').waitFor();
 const text=await page.getByRole('textbox',{name:'Thông tin hỗ trợ',exact:true}).inputValue();
 for(const expected of ['Chuyên viên MGHP','chuyenvien.demo','BB-MGHP-2026-01','GET /api/v1/documents/','NoSuchKey','14:05'])h.assert(text.includes(expected));
 await h.capture(g,'Nội dung sao chép gửi CA hoặc kỹ thuật','Có vai trò, tài khoản, tài liệu, phiên bản, API, thời gian và lỗi. Không tự kết luận bên chịu trách nhiệm.');
 await h.act('copy-text');h.assert((await page.evaluate(()=>navigator.clipboard.readText())).includes('DEMO-SIGN-001'));
 await h.capture(g,'Đã sao chép thông tin hỗ trợ','Admin có thể dán nội dung sang email hoặc ứng dụng trao đổi bên ngoài.');
 await page.getByRole('button',{name:'Đóng hộp thoại',exact:true}).click();h.pass('Sao chép đủ ngữ cảnh sự cố, không cần nhập ID thủ công');
 await h.act('note','SC-001');await page.getByRole('textbox',{name:'Nội dung ghi chú',exact:true}).fill('Đã liên hệ CA DUT qua email. CA xác nhận đang kiểm tra kho tệp, chờ phản hồi.');
 await page.getByRole('textbox',{name:'Tham chiếu trao đổi',exact:true}).fill('Trao đổi với CA ngày 25/11');
 await h.capture(g,'Nhập ghi chú liên hệ CA','Ghi nội dung xử lý và tham chiếu trao đổi; không cần chat tích hợp.');
 await page.getByRole('button',{name:'Lưu ghi chú',exact:true}).click();
 h.assert.equal((await h.state()).incidents[0].notes.length,1);await h.capture(g,'Ghi chú và lịch sử đã lưu','Phiếu vẫn Đang xử lý. Lưu ghi chú không tự làm văn bản ký xong.');
 await h.act('check-incident','SC-001');await h.settled();h.assert.equal((await h.state()).incidents[0].ready,false);
 h.assert.equal(await page.locator('[data-action="recover"]').isDisabled(),true);
 await h.capture(g,'CA chưa khắc phục vẫn giữ chờ','Kiểm tra lại khi CA vẫn lỗi. Không cho ký lại hoặc ép hoàn tất.');h.pass('NoSuchKey giữ biên nhận và không mở bước ký khi CA chưa khắc phục');
 await h.act('view-signer','SC-001');h.assert.equal(await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).count(),0);
 h.assert.equal(await page.getByText('NoSuchKey',{exact:true}).count(),0);await h.capture('02 · Góc nhìn người ký','Chuyên viên chỉ thấy Cần xử lý thêm','CV thấy thông báo chờ quản trị và tiến trình, không thấy lỗi kỹ thuật hoặc nút phục hồi.');
 await h.role('pp');await h.capture('02 · Góc nhìn người ký','Danh sách tài liệu của Phó phòng','Phó phòng chỉ có danh sách tài liệu, không có menu quản trị.');await h.act('user-open','SC-002');
 await h.capture('02 · Góc nhìn người ký','Phó phòng chờ Admin hỗ trợ','Không hiển thị mã lỗi Cloudinary/SQL cho người ký.');
 await h.role('pht');await h.act('user-open','SC-001');await h.capture('02 · Góc nhìn người ký','Phó Hiệu trưởng xem bước trước đang chờ','PHT xem trạng thái, chưa được ký khi bước trước chưa có PDF lưu đủ.');h.pass('CV PP PHT không thấy chẩn đoán và thao tác Admin');
 await h.role('admin');await h.openIncident('SC-001');await h.scenario('fixed');await h.act('check-incident','SC-001');await h.settled();h.assert.equal((await h.state()).incidents[0].ready,true);
 await h.capture(g,'CA đã khắc phục đủ điều kiện nhận lại PDF','Chọn kịch bản mô phỏng Dịch vụ đã khắc phục rồi bấm Kiểm tra lại.');
 await h.act('recover','SC-001');await h.capture(g,'Xác nhận phục hồi PDF không ký lại','Hộp xác nhận chỉ tiếp tục tải kiểm lưu của lượt ký cũ.');
 await page.getByRole('dialog').getByRole('button',{name:'Tiếp tục lưu kết quả',exact:true}).click();await page.locator('.busy-bar').waitFor();
 await h.capture(g,'Đang phục hồi PDF mô phỏng','Có loading rõ, chặn bấm trùng trong thao tác phục hồi.');await h.settled();
 const restored=(await h.state()).incidents[0];h.assert.equal(restored.status,'resolved');h.assert(restored.cloud&&restored.sql);h.assert.equal(restored.ca,'success');
 await h.capture(g,'NoSuchKey đã phục hồi đúng kết quả','PDF đã lưu đủ, tự kết thúc phiếu; không có nút đánh dấu ký xong tùy ý.');h.pass('Phục hồi receipt thành công chuyển bước, giữ PDF chờ Admin xóa');
 await h.act('view-signer','SC-001');await h.capture('02 · Góc nhìn người ký','Chuyên viên thấy đã chuyển chờ PHT','Người ký gốc nhận tiến trình sau Admin phục hồi. Không tự ký bước tiếp theo.');
 await h.role('admin');
}

export async function storage(page,h){
 const g='03 · Cloudinary SQL và PDF lưu chờ';
 await h.openIncident('SC-002');await h.capture(g,'Lỗi Cloudinary PDF còn trên server','Admin phân biệt bản chờ trên server với Cloudinary và SQL chưa hoàn tất.');
 await h.act('delete-file','F-002');await h.capture(g,'Được phép xóa dù chưa lưu đủ','Hộp xác nhận nêu rủi ro; nút Xóa vẫn được phép theo cơ chế linh hoạt. Chưa xóa ở bước này.');
 h.assert.equal(await page.getByRole('dialog').getByRole('button',{name:'Xóa bản chờ',exact:true}).isEnabled(),true);
 await page.getByRole('button',{name:'Giữ bản chờ',exact:true}).click();h.pass('Không khóa Admin xóa theo trạng thái Cloudinary hoặc SQL');
 await h.act('start');await h.act('check-incident','SC-002');await h.settled();await h.act('recover','SC-002');
 await h.capture(g,'Tiếp tục lưu PDF có sẵn trên server','Không yêu cầu chuyên viên tải lại hoặc tạo chữ ký mới.');await page.getByRole('dialog').getByRole('button',{name:'Tiếp tục lưu kết quả',exact:true}).click();await h.settled();
 let s=await h.state();h.assert.equal(s.incidents[1].status,'resolved');h.assert(s.files.find(f=>f.id==='F-002'&&!f.deleted));
 await h.capture(g,'Cloudinary SQL thành công vẫn giữ bản chờ','File từng lỗi không bị tự dọn sau khi phục hồi.');h.pass('PDF từng lỗi vẫn giữ sau khi Cloudinary và SQL hoàn tất');
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^PDF lưu chờ/}).click();
 await h.capture(g,'Danh sách PDF trên ổ đĩa server','Có file chờ lưu, file đã lưu đủ và file cần đối chiếu; Admin tự quản lý.');
 await h.act('delete-file','F-002');await h.capture(g,'Xác nhận dọn bản chờ đã lưu đủ','Chỉ xóa bản trên server, không xóa bản Cloudinary hoặc lịch sử.');
 await page.getByRole('dialog').getByRole('button',{name:'Xóa bản chờ',exact:true}).click();s=await h.state();h.assert(s.files.find(f=>f.id==='F-002').deleted);h.assert(s.incidents[1].cloud&&s.incidents[1].sql);
 await h.capture(g,'Đã dọn bản chờ giữ nguyên kết quả ký','Dòng bản chờ được bỏ khỏi danh sách đang giữ, bản ký Cloudinary/SQL vẫn còn.');h.pass('Xóa bản chờ không thay đổi kết quả ký và lịch sử');
 await h.openIncident('SC-003');await h.capture(g,'SQL lỗi sau khi Cloudinary đã có PDF','Admin thấy Cloudinary đã lưu, SQL chưa ghi đủ và bản server vẫn còn.');
 await h.act('start');await h.act('check-incident','SC-003');await h.settled();await h.act('recover','SC-003');await h.capture(g,'Xác nhận chỉ hoàn thiện phần SQL','Thông tin chỉ rõ tiếp tục liên kết và trạng thái SQL, không ký hoặc upload trùng.');
 await page.getByRole('dialog').getByRole('button',{name:'Tiếp tục lưu kết quả',exact:true}).click();await h.settled();h.assert((await h.state()).incidents[2].sql);
 await h.capture(g,'Đã hoàn thiện SQL kết quả được bảo toàn','Phiếu tự kết thúc theo kết quả; bản server từng lỗi tiếp tục được giữ.');h.pass('SQL sau upload phục hồi đúng phần còn thiếu');
 await h.openIncident('SC-006');await h.scenario('faults');await h.act('start');await h.act('check-incident','SC-006');await h.settled();
 h.assert.equal(await page.locator('[data-action="recover"]').isDisabled(),true);
 await h.capture(g,'Hash không khớp không được bỏ qua','Không quy ngay lỗi cho CA hay CTSV, không có nút bỏ qua hash; giữ file để đối chiếu.');h.pass('Hash không đạt giữ chờ hỗ trợ, không giả hoàn tất');
}

export async function certificates(page,h){
 const g='04 · Quản lý chứng thư và gia hạn';
 await page.getByRole('button',{name:'Quản lý chứng thư',exact:true}).click();await h.capture(g,'Danh sách chứng thư và hạn giả lập','Hai email TEST, một dòng cho mỗi email; có chứng thư sắp hết hạn và hết hạn để review.');
 await h.act('open-cert','e');await h.capture(g,'Chi tiết chứng thư còn chín ngày','Thấy chủ chứng thư, cách dùng, ngày hết hạn và lần kiểm. Cảnh báo không tự chặn ký.');
 const before=(await h.state()).certificates[0].validTo;await h.act('check-cert','e');await h.settled();h.assert.equal((await h.state()).certificates[0].validTo,before);
 await h.capture(g,'Kiểm tra lại không tự gia hạn','Kịch bản CA chưa cập nhật: hạn vẫn như cũ.');h.pass('Kiểm tra lại chứng thư không tự tăng thời hạn');
 await h.act('renew-help','e');await h.capture(g,'Hướng dẫn gia hạn đầy đủ các bước','Nêu rõ liên hệ hoặc gửi đề nghị, chờ CA duyệt, đọc lại cert; không tự cấp hạn.');await page.getByRole('button',{name:'Đã hiểu',exact:true}).click();
 await h.act('cert-note','e');await page.getByRole('textbox',{name:'Nội dung ghi chú',exact:true}).fill('Đã liên hệ chủ chứng thư và CA DUT để chuẩn bị gia hạn trước ngày hết hạn.');
 await h.capture(g,'Ghi nhận đã liên hệ CA về chứng thư','Ghi chú không làm chứng thư hợp lệ hoặc thay hạn.');await page.getByRole('button',{name:'Lưu ghi chú',exact:true}).click();
 await h.capture(g,'Lịch sử liên hệ chứng thư','Thông tin liên hệ được lưu và ngày hết hạn vẫn giữ nguyên.');
 await h.act('renew','e');await h.capture(g,'Gửi đề nghị gia hạn từ CTSV','Dialog mô phỏng cho thấy cert cũ, hạn hiện tại, lý do và tác động khi CA duyệt.');
 await page.getByRole('button',{name:'Xác nhận gửi yêu cầu',exact:true}).click();await page.locator('.busy-bar').waitFor();await h.capture(g,'Đang gửi đề nghị gia hạn mô phỏng','Nút được khóa trong khi tiếp nhận, không có API thật.');await h.settled();
 h.assert.equal((await h.state()).certificates[0].renew.state,'pending');h.assert.equal((await h.state()).certificates[0].validTo,before);
 await h.capture(g,'Đã gửi và chờ CA DUT duyệt','Không đổi ngày hết hạn chỉ vì gửi thành công.');
 await h.act('check-renew','e');await h.settled();h.assert.equal((await h.state()).certificates[0].renew.state,'pending');
 await h.capture(g,'Kiểm tra yêu cầu vẫn đang chờ','Khi CA chưa duyệt thì giữ pending, không gửi một yêu cầu mới.');h.pass('Gia hạn pending không đổi hạn hoặc tạo yêu cầu trùng');
 await h.scenario('fixed');await h.act('check-renew','e');await h.settled();const c=(await h.state()).certificates[0];h.assert.equal(c.renew.state,'approved');h.assert.equal(c.state,'valid');
 await h.capture(g,'CA đã duyệt và có chứng thư mới','Chỉ kịch bản CA đã cập nhật mới ghi nhận cert mới. Serial cũ có trong lịch sử.');h.pass('Chứng thư mới chỉ có sau kết quả CA và lần kiểm lại');
 await page.getByRole('link',{name:'Danh sách chứng thư',exact:true}).click();await h.act('open-cert','c');await h.capture(g,'Chứng thư đã hết hạn cần xử lý','Cảnh báo hết hạn dùng dữ liệu mô phỏng, không yêu cầu người ký tự sửa kỹ thuật.');
 await h.scenario('offline');await h.act('check-cert','c');await h.settled();h.assert.equal((await h.state()).certificates[1].checkFailed,true);
 await h.capture(g,'CA không kết nối được giữ quan sát cũ','Lần kiểm mới thất bại không tự kết luận chứng thư thay đổi trạng thái.');h.pass('Lỗi kết nối không bị hiểu thành hết hạn hoặc đã gia hạn');
 await page.getByRole('link',{name:'Danh sách chứng thư',exact:true}).click();await h.scenario('fixed');await page.getByRole('button',{name:'Kiểm tra tất cả',exact:true}).click();await h.settled();
 h.assert((await h.state()).certificates.every(c=>c.state==='valid'));await h.capture(g,'Kiểm tra tất cả cập nhật hai nguồn','Hai email duy nhất được kiểm lại trong mô phỏng.');h.pass('Kiểm tra tất cả cập nhật đúng tập người ký');
}

export async function users(page,h){
 await page.reload();
 const g='05 · Lỗi 400 và mở lại cho người ký';
 await h.role('admin');await h.openIncident('SC-005');await h.capture(g,'Lỗi 400 category chuyển Admin','Người ký không nhập category, nên đây là sự cố cần Admin kiểm tra dù HTTP400.');
 await h.act('start');await h.act('check-incident','SC-005');await h.settled();await h.act('recover','SC-005');
 await h.capture(g,'Mở lại bước ký sau khắc phục cấu hình','Admin không ký thay. Người được giao phải chọn vùng và xác nhận lại.');
 await page.getByRole('dialog').getByRole('button',{name:'Mở lại bước ký',exact:true}).click();await h.settled();h.assert.equal((await h.state()).incidents[4].resolution,'reopened');
 await h.capture(g,'Đã mở bước nhưng chưa có chữ ký mới','Phiếu đã xử lý, còn tài liệu chờ chính người ký.');
 await h.act('view-signer','SC-005');await h.capture(g,'Người ký thấy đã được mở lại','Không có nút phục hồi kỹ thuật; chuyên viên có thể thực hiện lại bước của mình.');
 await h.scenario('input');await page.getByRole('button',{name:'Chọn vị trí ký',exact:true}).click();await page.getByRole('button',{name:'Ký số',exact:true}).click();
 await h.capture(g,'Người ký xác nhận đúng tài liệu và vùng','Đây chỉ mô phỏng thao tác ký, không có CA hoặc chữ ký thật.');
 await page.getByRole('button',{name:'Xác nhận ký',exact:true}).click();await h.settled();await page.getByRole('alert').filter({hasText:'Vùng ký đang vượt ra ngoài'}).first().waitFor();
 await h.capture(g,'Lỗi 400 người dùng sửa được bằng tiếng Việt','Sai vùng ký: chỉ rõ cần chọn lại vùng trong trang. Không đưa mã lỗi hệ thống vào thông báo.');
 await page.getByRole('button',{name:'Chọn lại vị trí ký',exact:true}).click();h.assert.equal((await h.state()).userError,null);
 await h.capture(g,'Sửa vùng ký và tiếp tục không nhập lại hết','Thông báo lỗi biến mất sau chọn lại, dữ liệu tài liệu vẫn giữ.');
 await page.getByRole('button',{name:'Ký số',exact:true}).click();await page.getByRole('button',{name:'Xác nhận ký',exact:true}).click();await h.settled();h.assert((await h.state()).userAccepted);
 await h.capture(g,'Đã tiếp nhận ký nền người dùng có thể rời trang','Mô phỏng nhận yêu cầu; không giả chữ ký đã hoàn tất.');h.pass('Lỗi400 có hướng sửa và tiếp tục được, accepted không bị ghi thành completed');
 await h.role('admin');await h.openIncident('SC-004');await h.act('start');await h.scenario('fixed');await usersFinish(page,h);
}

export async function usersFinish(page,h){
 await h.scenario('fixed');await h.act('check-incident','SC-004');await h.settled();await h.act('recover','SC-004');
 await h.capture('04 · Quản lý chứng thư và gia hạn','Chứng thư đã khắc phục mở lại đúng bước PHT','Chỉ mở cho đúng người được giao, không sửa chữ ký lịch sử hoặc tự ký.');
 await page.getByRole('dialog').getByRole('button',{name:'Mở lại bước ký',exact:true}).click();await h.settled();
 await h.act('view-signer','SC-004');h.assert.equal(await page.getByRole('button',{name:'Ký số',exact:true}).count(),1);
 await h.capture('02 · Góc nhìn người ký','PHT thấy quyền ký lại sau xử lý chứng thư','Nút cần chọn vùng mới. Không có thông tin kỹ thuật Admin trên trang người ký.');h.pass('Khắc phục chứng thư trả đúng người ký, không tự ký');
}

export async function extras(page,h){
 const g='06 · Mobile và các trạng thái bổ sung';
 await h.role('admin');await h.openIncident('SC-006');await h.act('delete-file','F-006');
 await h.capture(g,'Admin có thể xóa bản chờ chưa lưu đủ','Không khóa nút xóa theo Cloudinary hoặc SQL. Có thông báo tác động trước khi xác nhận.');
 await page.getByRole('dialog').getByRole('button',{name:'Xóa bản chờ',exact:true}).click();
 const s=await h.state();h.assert(s.files.find(f=>f.id==='F-006').deleted);h.assert.equal(s.incidents[5].status,'working');h.assert.equal(s.incidents[5].sql,false);
 await h.capture(g,'Bản chờ bị xóa sự cố vẫn chưa hoàn tất','Giữ lịch sử và cảnh báo không còn bản trên server. Không tự coi sự cố đã giải quyết.');h.pass('Xóa bản chưa lưu đủ được phép và không giả kết quả thành công');
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Đã xử lý/}).click();
 h.assert.equal(await page.locator('[data-incident]').count(),5);await h.capture(g,'Tab Đã xử lý chỉ có các sự cố đã khắc phục','Lọc phiếu sự cố tách khỏi kết quả ký của tài liệu.');h.pass('Count và bộ lọc phiếu khớp trạng thái sau xử lý');
}

export async function gallery(page,h){
 await page.goto(h.origin+'/review.html');await page.getByRole('heading',{name:'Review prototype quản trị ký số',exact:true}).waitFor();
 await page.getByRole('button',{name:'Xem từ ảnh đầu tiên',exact:true}).click();await page.getByRole('dialog').waitFor();
 h.assert.equal(await page.locator('#image-title').textContent(),'Danh sách sự cố ký số');
 await page.getByRole('button',{name:'Ảnh tiếp →',exact:true}).click();h.assert.equal(await page.locator('#image-title').textContent(),'Chi tiết NoSuchKey và thông tin thao tác');
 await page.getByRole('button',{name:'← Ảnh trước',exact:true}).click();await page.getByRole('button',{name:'Đóng ảnh',exact:true}).click();
 const images=await page.locator('.image-card').count();h.assert(images>=55);h.pass('Gallery mở từng ảnh và điều hướng theo trình tự');
}

export async function details(page,h){
 await h.role('admin');await h.openIncident('SC-001');await page.getByText('Thông tin chẩn đoán và tài liệu',{exact:true}).click();
 await h.capture('01 · Sự cố và liên hệ CA','Chẩn đoán tài liệu và biên nhận đúng lượt','Mở phần chi tiết kỹ thuật: mã văn bản, phiên bản, CA document và receipt; không có thông tin bí mật.');
 await page.getByRole('button',{name:'Quản lý chứng thư',exact:true}).click();await h.act('open-cert','e');await page.getByText('Thông tin kỹ thuật công khai',{exact:true}).click();
 await h.capture('04 · Quản lý chứng thư và gia hạn','Chi tiết kỹ thuật công khai của chứng thư','Mở phần serial và truy vấn status active; không có private key hoặc mật khẩu.');h.pass('Các phần chi tiết mở được và không cần nhập thông tin kỹ thuật');
}

export async function presentation(page,h){
 await page.screenshot({path:'prototypes/signing-admin-support/gallery-overview.png',fullPage:false});
 await page.goto(h.origin);await page.evaluate(()=>localStorage.setItem('prototype-test-unrelated','keep'));
 await page.getByRole('button',{name:'Đặt lại demo',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Đặt lại demo',exact:true}).click();
 h.assert.equal(await page.locator('[data-incident]').count(),6);h.assert.equal(await page.evaluate(()=>localStorage.getItem('prototype-test-unrelated')),'keep');
 await page.evaluate(()=>localStorage.removeItem('prototype-test-unrelated'));
 await page.screenshot({path:'prototypes/signing-admin-support/preview.png',fullPage:true});h.pass('Đặt lại demo chỉ đổi dữ liệu prototype, không xóa storage khác');
}

export async function mobileRefine(page,h){
 // Recreate the same synthetic pre-deletion review state, never application/DB data.
 const finalState=await h.state(),reviewState=JSON.parse(JSON.stringify(finalState));
 const file=reviewState.files.find(f=>f.id==='F-006');file.deleted=false;delete file.deletedAt;
 reviewState.incidents[5].history=reviewState.incidents[5].history.filter(event=>!event.text.includes('Admin đã xóa bản chờ'));
 reviewState.role='admin';reviewState.page='incidents';reviewState.incident='SC-006';reviewState.tab='all';
 await page.evaluate(value=>localStorage.setItem('ctsv.prototype.signing-admin.v1',JSON.stringify(value)),reviewState);await page.reload();await page.setViewportSize({width:390,height:844});
 await page.getByRole('heading',{name:'Cần Admin xem xét',exact:true}).waitFor();
 const summary=await page.locator('.error-title').boundingBox(),action=await page.getByRole('button',{name:'Kiểm tra lại',exact:true}).boundingBox();h.assert(summary.y<action.y);
 h.assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:'prototypes/signing-admin-support/screenshots/53-mobile-chi-tiet-su-co-va-thao-tac.png',fullPage:true});
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^PDF lưu chờ/}).click();await page.getByRole('combobox',{name:'Lọc bản chờ',exact:true}).selectOption('all');
 await page.screenshot({path:'prototypes/signing-admin-support/screenshots/54-mobile-pdf-luu-cho.png',fullPage:true});
 await page.getByRole('button',{name:'Quản lý chứng thư',exact:true}).click();await page.screenshot({path:'prototypes/signing-admin-support/screenshots/56-mobile-quan-ly-chung-thu.png',fullPage:true});
 h.pass('Mobile đã rà lại: thông tin lỗi đứng trước thao tác, trường API dễ đọc');
 await page.evaluate(value=>localStorage.setItem('ctsv.prototype.signing-admin.v1',JSON.stringify(value)),finalState);await page.reload();await page.setViewportSize({width:1440,height:1050});
}

export async function mobile(page,h){
 const g='06 · Mobile và các trạng thái bổ sung';
 await h.role('admin');await h.openIncident('SC-006');await page.setViewportSize({width:390,height:844});
 h.assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await h.capture(g,'Mobile chi tiết sự cố và thao tác','Khung xử lý và nút chính lên trước; không tràn ngang khi API và tên file dài.');
 await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^PDF lưu chờ/}).click();
 h.assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await h.capture(g,'Mobile PDF lưu chờ','Bảng đổi sang thông tin theo từng file, nút xóa rõ ngữ cảnh.');
 await page.getByRole('combobox',{name:'Lọc bản chờ',exact:true}).selectOption('deleted');h.assert.equal(await page.locator('[data-file="F-002"]').count(),1);
 await h.capture(g,'Lịch sử bản chờ đã xóa','Admin vẫn tìm được bản đã dọn trong demo; không có nút khôi phục giả.');
 await page.getByRole('button',{name:'Quản lý chứng thư',exact:true}).click();h.assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await h.capture(g,'Mobile quản lý chứng thư','Thông tin chứng thư và hành động vẫn dễ thao tác.');h.pass('Mobile390 không tràn ngang ở ba màn Admin');
 await page.setViewportSize({width:1440,height:1050});await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Tất cả/}).click();
 await page.getByRole('textbox',{name:'Tìm sự cố',exact:true}).fill('không tồn tại 999');await page.getByRole('button',{name:'Tìm',exact:true}).click();await page.getByRole('heading',{name:'Không có sự cố trong mục này',exact:true}).waitFor();
 await h.capture(g,'Tìm kiếm không có kết quả','Empty state có hướng tiếp tục, khác lỗi tải hệ thống.');
 await page.getByRole('button',{name:'Xóa tìm kiếm',exact:true}).click();h.assert.equal(await page.locator('[data-incident]').count(),6);
 await page.reload();h.assert.equal(await page.locator('[data-incident]').count(),6);h.assert.equal((await h.state()).incidents[0].status,'resolved');h.pass('Tìm kiếm sửa được và refresh giữ trạng thái mô phỏng');
 await h.capture(g,'Tổng kết các sự cố sau xử lý','Danh sách phản ánh đúng sự cố đã xử lý và hash vẫn chờ đối chiếu.');
}
