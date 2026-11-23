# Frontend CTSV

Trước khi tạo, sửa hoặc review file trong thư mục này, đọc [SKILL.md](SKILL.md).
Áp dụng cấu trúc module, quy chuẩn UI, hướng phụ thuộc và kiểm chứng trong file đó.
Giữ các ràng buộc của AGENTS.md tại root; chỉ sửa module/thành phần chung thuộc task được giao.

Khi thiết kế/sửa/review UI, bắt buộc áp dụng [thiết kế theo công việc người dùng](../AGENTS.md#ctsv-role-first-ux) và [checklist UX theo vai trò](SKILL.md#ctsv-ux-review). Xác định tác vụ trước khi chọn field/nút, tự rà soát phần trùng/thừa và đường đi tiếp; báo PASS đúng hành trình đã kiểm. Đây là quy tắc trong repo để các phiên sau đọc lại, không thay contract/quyền hoặc cho phép triển khai module ngoài phạm vi.

Khi làm bố cục, câu chữ hoặc trạng thái hiển thị, áp dụng [thiết kế thị giác có mục đích](SKILL.md#ctsv-visual-design): phác bố cục cho thay đổi lớn, phân cấp thông tin, dùng từ nhất quán và tự review ảnh. Giữ theme/Navbar đã chấp nhận; không áp nguyên mẫu marketing hoặc tự tạo style riêng cho từng module.

Áp dụng [11 quy tắc triển khai và kiểm thử có trọng tâm của owner](../AGENTS.md#ctsv-efficient-verification): kiểm locator trước luồng dài, giữ context khi lỗi, tái sử dụng phiên trong bộ nhớ, chạy lại đúng ca lỗi trước và chỉ hồi quy phạm vi bị ảnh hưởng. Không tự bật lại login rate limit; quyết định mặc định mọi môi trường là `ctsv.auth.login-rate-limit-enabled=false`.
Khi làm ký số MGHP P11, áp dụng [memory UX ký số §14](SKILL.md#p11-signing-memory-20261123) và [thiết kế đã chốt](../../nghiepvu/09-trien-khai/p11-signing-design.md): mỗi role có tác vụ đúng bước, PDF.js chọn vùng, PP ACK gộp trong ký, không CV từ chối ký; output mỗi người phải lưu tại CTSV trước chuyển bước. Đây là ghi nhớ thiết kế, không xác nhận UI đã triển khai hoặc cho phép tự mở P11 từ task tài liệu.

### Quyết định P11 cuối23/11 — không Từ chối ký

Owner đã bỏ **toàn bộ Từ chối ký của PP/PHT trong P11**, CV cũng không có. Không tái dựng nút/API/reason từ chối hoặc case sửa dữ liệu đã chốt. Phần memory cũ về PP/PHT refusal đã superseded. Giữ ký chủ động đúng bước, PP ACK, PDF.js, output từng bước CTSV, lịch sử và phục hồi lỗi; không auto retry unknown. Thu hồi tài liệu chờ ký vẫn DEFERRED. Nguồn: [Owner Decisions](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#p11-no-refusal-20261123).
