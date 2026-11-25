# TASK-CV-04 — Mẫu biên bản của đợt SHCN

Source triển khai ngày **24/11/2026** theo hai tài liệu `API/TASK_CV_04_MAU_BIEN_BAN` trong workspace dữ liệu. OpenAPI của slice nằm tại `API/TASK_CV_04_MAU_BIEN_BAN/openapi.json`.

## Hành vi

- GET/PUT `/api/shcn/dot-sinh-hoat/{dotId}/mau-bien-ban`, giữ Auth/RBAC/global scope và guard đợt của CV-02. Đọc mẫu chưa tồn tại trả 200 empty DTO và danh mục, không ghi DB.
- PUT thay toàn bộ mẫu/mục theo root rowversion; khóa root, CAS, upsert mẫu/mục và audit chung transaction. Giữ ID mẫu/mã mục, tránh UNIQUE khi đổi thứ tự. Không sửa phân công hoặc đối tượng CV-03.
- Một mẫu cho một đợt; hai Entity/bảng mới thuộc SHCN. Không sửa bảng/Entity Foundation hoặc Đào tạo.
- Owner chốt luôn giữ Điểm danh + Kiến nghị của SV + Kiến nghị của GV. Điểm danh ba số bắt buộc, không danh sách SV. Hai mục kiến nghị có nội dung tùy chọn, mã/tên/trường cố định; đổi vị trí được sau Điểm danh.
- FE tạo/sửa mục và trường tùy biến, chọn nguồn tự điền trong whitelist, xem trước web form Offline/Online bằng dữ liệu minh họa; Online hiện link minh chứng. Không sinh PDF/biên bản lớp hoặc gửi thông báo.
- Panel ở chi tiết đợt đã lưu. Sửa mẫu và sửa thông tin đợt không mở đồng thời; sau lưu/đối soát làm mới root version. Conflict/timeout giữ input, không tự retry PUT.

## Migration và giới hạn DEV

**V26__shcn_minutes_template.sql mới chỉ được viết, chưa chạy.** Không migrate, seed, cấp quyền, cấu hình hoặc restart trong lượt triển khai này. Không đóng gói đè JAR backend đang chạy.

- `mvn test` đã compile BE mới; chưa package/deploy JAR mới vào tiến trình DEV.
- TypeScript/Vite build đã đạt, nhưng FE mới cần backend mới và hai bảng được owner áp dụng để chạy với DB thật.
- Không báo luồng SQL/DEV hoàn tất khi chỉ có MockMvc hoặc API fixture. Hibernate validate/DDL/trigger, UNIQUE/reorder/rollback và race trên SQL Server thật vẫn **NOT VERIFIED**.
- Đã có quyền chuyên viên từ V18; task này không tạo account, gán lại role/permission hoặc nạp dữ liệu mẫu.

Khi owner cho phép áp dụng môi trường, phải dừng launcher trước build/migrate để tránh JAR đang dùng. Kiểm lại số migration so với main và lịch sử DB trước khi áp V26; không đổi checksum migration đã áp dụng. Sau đó khởi động qua launcher SHCN có nguồn đã cấu hình, kiểm account được phép/thiếu quyền và một luồng lưu/đọc lại trên DB được owner cho phép. Không tự chạy các bước này từ hướng dẫn.

## Kiểm chứng đã chạy

- **82 Java tests**: 32 CV-04 và 50 hồi quy validation/HTTP đợt; 0 fail/error/skip. Unit/MockMvc, repository và transaction manager giả lập, không SQL. Có kiểm CORS PUT từ origin hợp lệ và từ chối origin lạ.
- **39 Node tests**: model/API SHCN, gồm cấu hình mẫu, mục cố định, nguồn tự điền và body PUT; đều đạt.
- **17 luồng Chrome**: 10 CV-04 + 7 hồi quy CV-03; API HTTP fixtures, SQL NONE. Kiểm tạo/sửa/xem trước/lưu/mở lại, lỗi sau cuộn, conflict, timeout đối soát, quyền và mobile. Đã xem ảnh desktop/mobile.
- FE TypeScript/Vite build PASS; còn cảnh báo bundle lớn hiện có. Git diff whitespace check PASS.

Lệnh tại root CTSV, JDK21:

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=MauRequestsTest,MauServiceTest,MauHttpTest,DotRequestsTest,DotAudienceRequestsTest,DotHttpTest' test
node --test frontend/tests/shcn-rounds.test.mjs frontend/tests/shcn-round-audiences.test.mjs frontend/tests/shcn-round-date-range.test.mjs frontend/tests/shcn-semester-audience-api.test.mjs frontend/tests/shcn-minutes-template.test.mjs
npm --prefix frontend run build
```

Browser harness: `frontend/e2e/shcn-minutes-template-ui.mjs`; dùng `SHCN_UI_ORIGIN`, `SHCN_UI_EVIDENCE` và tùy chọn `SHCN_UI_CASE`. Tất cả `/api` được route fixture; không dùng kết quả này làm bằng chứng backend/SQL DEV thật.

Bằng chứng tại workspace dữ liệu: `API/TASK_CV_04_MAU_BIEN_BAN/EVIDENCE_BROWSER_2026_11_24`, `EVIDENCE_REGRESSION_CV03_2026_11_24`; Java reports ở `target/surefire-reports`. Không chứa token, password hoặc PII thật.

## Thao tác sau khi owner áp dụng DB/backend

1. Đăng nhập tài khoản có quyền chuyên viên SHCN, vào Quản lý đợt.
2. Mở đợt nháp đã lưu → Mẫu biên bản → Tạo mẫu biên bản.
3. Đặt tên, chỉnh các mục/trường tùy biến; ba mục cố định không xóa được.
4. Xem trước Offline/Online, quay lại chỉnh → Lưu mẫu.
5. Mở lại mẫu hoặc sửa thông tin đợt, xác nhận version mới được dùng. Không yêu cầu nguồn SV hoặc phân công hiệu lực để lưu cấu trúc mẫu.
