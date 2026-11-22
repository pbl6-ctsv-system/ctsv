# Frontend CTSV

Trước khi tạo, sửa hoặc review file trong thư mục này, đọc [SKILL.md](SKILL.md).
Áp dụng cấu trúc module, quy chuẩn UI, hướng phụ thuộc và kiểm chứng trong file đó.
Giữ các ràng buộc của AGENTS.md tại root; chỉ sửa module/thành phần chung thuộc task được giao.

Khi thiết kế/sửa/review UI, bắt buộc áp dụng [thiết kế theo công việc người dùng](../AGENTS.md#ctsv-role-first-ux) và [checklist UX theo vai trò](SKILL.md#ctsv-ux-review). Xác định tác vụ trước khi chọn field/nút, tự rà soát phần trùng/thừa và đường đi tiếp; báo PASS đúng hành trình đã kiểm. Đây là quy tắc trong repo để các phiên sau đọc lại, không thay contract/quyền hoặc cho phép triển khai module ngoài phạm vi.

Khi làm bố cục, câu chữ hoặc trạng thái hiển thị, áp dụng [thiết kế thị giác có mục đích](SKILL.md#ctsv-visual-design): phác bố cục cho thay đổi lớn, phân cấp thông tin, dùng từ nhất quán và tự review ảnh. Giữ theme/Navbar đã chấp nhận; không áp nguyên mẫu marketing hoặc tự tạo style riêng cho từng module.

Áp dụng [11 quy tắc triển khai và kiểm thử có trọng tâm của owner](../AGENTS.md#ctsv-efficient-verification): kiểm locator trước luồng dài, giữ context khi lỗi, tái sử dụng phiên trong bộ nhớ, chạy lại đúng ca lỗi trước và chỉ hồi quy phạm vi bị ảnh hưởng. Không tự bật lại login rate limit; quyết định mặc định mọi môi trường là `ctsv.auth.login-rate-limit-enabled=false`.
