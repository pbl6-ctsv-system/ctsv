# Bàn giao DEV của module SHCN

- Đọc và áp dụng AGENTS.md tại root repo, frontend/SKILL.md khi sửa frontend và `docs/shcn/dev-readiness.md`.
- Khi task SHCN cần người dùng thao tác, kiểm đủ account, role–permission Auth, scope Foundation và dữ liệu lookup; không dừng ở việc viết migration/catalog quyền.
- Nếu owner giao provisioning DEV, thực hiện bằng công cụ có version/guard/audit/idempotency trong tools/dev/shcn; giữ quyền cũ và không tự khôi phục quyền đã bị owner thu hồi.
- Kiểm API và menu/URL bằng account thật có quyền và thiếu quyền khi môi trường đã sẵn sàng; tách rõ SQL/HTTPS REAL khỏi fixture/mock.
- Chỉ chạy migration khi owner yêu cầu rõ. Không cấp thêm quyền ngoài phạm vi task, không sửa schema/Entity Foundation/Đào tạo để làm demo chạy.
- Nếu còn thiếu học kỳ/nguồn/quyền hoặc dịch vụ, báo chính xác và hoàn thành phần độc lập đã được giao; không tuyên bố luồng DEV đầy đủ.
