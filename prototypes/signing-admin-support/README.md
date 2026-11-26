# Prototype hỗ trợ ký số — bản đơn giản hóa, chờ owner duyệt

Prototype độc lập HTML/CSS/JS. Không tích hợp React chính, không gọi backend CTSV, CA, Cloudinary hoặc SQL. Chỉ dùng dữ liệu SYNTHETIC trên origin localhost riêng.

- Chạy từ root repo: node prototypes/signing-admin-support/server.mjs
- Giao diện: http://127.0.0.1:5182/
- Ảnh bản mới theo thao tác: http://127.0.0.1:5182/review.html

## Phạm vi đã chốt trong lượt review mới

Giữ danh sách sự cố. Chi tiết chỉ còn thông tin Admin cần đối chiếu (tài liệu/phiên bản/đợt, role/tài khoản, API/bước/lỗi/thời gian, mã biên nhận), ghi chú và lịch sử xử lý.

Bỏ Tiến độ của lượt ký, PDF lưu tại CTSV và góc nhìn người ký khỏi trang chi tiết sự cố. Không có nút kiểm tra lại, phục hồi, mở lại bước ký hoặc công cụ xử lý riêng từng lỗi.

Khung bên phải: Bắt đầu xử lý → Kết thúc xử lý, giữ Sao chép thông tin hỗ trợ. Kết thúc chỉ ghi nhận phiếu Đã xử lý và actor/time, không tự làm tài liệu ký xong, thay PDF hoặc ghi lại Cloudinary/SQL. Admin xử lý kỹ thuật bên ngoài. Ghi chú không bắt buộc để kết thúc. Cùng cách xử lý cho mọi sự cố Admin, bao gồm HTTP400 do tham số hệ thống.

Các lỗi400 người dùng sửa được vẫn có hướng dẫn tiếng Việt ở góc nhìn người ký. Màn hình chứng thư giữ nguyên. Owner xác nhận khi triển khai thật, Gửi đề nghị gia hạn phải gọi API renew CA DUT; hiện prototype chỉ mô phỏng gửi/chờ/duyệt.

## Cách review

1. Mở danh sách → Xem xử lý một sự cố.
2. Xem thông tin, mở chi tiết chẩn đoán nếu cần → Sao chép thông tin hỗ trợ.
3. Bắt đầu xử lý → nút đổi thành Kết thúc xử lý; thêm ghi chú tùy nhu cầu.
4. Kết thúc xử lý → phiếu nằm trong Đã xử lý; vẫn xem/sao chép thông tin và lịch sử.
5. Thử cùng luồng trên lỗi400, Cloudinary, SQL, chứng thư, hash; xem mobile.
6. Quản lý chứng thư → chọn người ký → Gửi yêu cầu gia hạn → chờ CA (mô phỏng).

## Kiểm chứng và giới hạn

Lệnh browser: node prototypes/signing-admin-support/walkthrough.cjs. Harness nhập tên pha để chạy tiếp trong cùng context; pha bản mới ở review-simplified-steps.mjs. Kết quả bản mới tại review.json và verification.json, ảnh có tiền tố r2- trong screenshots/. Kết quả 63 ảnh/25 ca của bản trước được giữ tại review-before-simplification.json, không tính là kiểm chứng cho bản mới. walkthrough-steps.mjs là kịch bản lịch sử của bản trước.

Phạm vi kiểm: bố cục/nút đúng trạng thái, clipboard, ghi chú/lịch sử, refresh, bộ lọc, không đổi dữ liệu tài liệu khi đóng phiếu, mobile và smoke renew mô phỏng. Harness chặn mọi request ngoài localhost:5182. Chỉ báo PASS theo kết quả thực thi lưu trong hai tệp evidence.

Kết quả bản mới: **23 ảnh, 13 kiểm tra browser mô phỏng PASS**, 0 page error, 0 request ra ngoài prototype, 0 lỗi còn mở. Có thêm một smoke Chrome riêng cho nhãn/vùng chọn của kịch bản người ký400, đạt. Đã xem ảnh desktop/mobile và chụp lại ba ảnh mobile sau khi đóng thông báo tạm để không che thông tin. Các lần lỗi script (selector phạm vi, newline clipboard Windows, nhãn nút gallery và các pha bị dialog chặn) được giữ trong evidence, có nguyên nhân và lần chạy lại thành công; không xóa expected để qua kiểm tra.

Đã giữ log lỗi script và lần chạy lại; đó không phải bằng chứng lỗi nghiệp vụ/backend. Không dùng kết quả prototype để khẳng định SQL, CA, Cloudinary hoặc phân quyền backend đã đạt. Không sửa source hệ thống chính, DB, migration, launcher hay cấu hình. Không commit/push/merge. Chờ owner review trước mọi tích hợp.
