# Đơn giản hóa xử lý hồ sơ — 22/11/2026

Owner giao bốn thay đổi: bỏ Từ chối PDF/nhận xét PDF; trạng thái giấy rõ và tự chuyển giấy chưa đồng ý tiếp theo; lý do chỉ bắt buộc khi từ chối hồ sơ; mở được bổ sung tiếp sau phản hồi chưa đáp ứng. Giữ nguyên P08 kiểm hình thức/nội dung/thời hạn/chủ thể/bản cứng, quyền/ETag, source lock và lịch sử. Không migration, không xóa cột note/history cũ.

## Plan và acceptance

1. Contract HS08: reason optional/nullable với approved, required không trắng≤4000 với rejected. Giữ lỗi/quyền/CAS/TX/audit; không tự đổi reason lịch sử hoặc tạo quyền hưởng.
2. UI kiểm PDF: bỏ ô nhận xét và Từ chối PDF; pass gửi note=null theo API hiện có. Danh sách có nhãn Đã đồng ý/Chưa đồng ý/Cần kiểm tra lại, tiến độ và nút không gửi lặp. Đồng ý thành công rồi mới chọn mục chưa đồng ý tiếp theo (bất kỳ số giấy, kể cả nhiều giấy dùng chung một file); lỗi không đổi lựa chọn hoặc đánh dấu thành công. Không hiển thị disclosure lịch sử kiểm PDF, DB history vẫn giữ. Kiểm giấy khác vẫn độc lập, không tự pass các loại chỉ vì đồng ý nội dung.
3. Dialog kết luận: đồng ý không có ô lý do; từ chối mới hiện ô required. Server kiểm tương ứng.
4. Bổ sung general responded: Đã đáp ứng yêu cầu → BS04 satisfied + BS06; Yêu cầu bổ sung tiếp → nhập nội dung/hạn, BS04 unsatisfied + BS06 rồi BS01 với root tag mới. Ghi actor/reason/history qua service hiện có, không tự từ chối hồ sơ. Đây là các TX riêng; nếu lỗi dừng, đọc lại trạng thái, giữ input và cho tiếp tục an toàn, không POST lại mù. Vòng open/expired chưa trả lời không mở vòng song song; typed cũ giữ review từng mục. Không thêm endpoint hoặc giới hạn tổng vòng.
5. Test tăng dần: model/workflow Unit đỏ→xanh (thứ tự chọn n giấy; fresh ETag; fail/partial retry); controller validation Unit; FE build; build JAR DEV không full suite; HTTP/SQL và browser thật trên ctsv_dev với synthetic riêng, không sửa hồ sơ user đã duyệt/từ chối. Test auto-next/list/readback, lỗi/stale, approve không reason, reject thiếu reason, BS lần1→phản hồi→lần2, nhận xét/ghi chú không xuất hiện ở kiểm PDF, mobile. Evidence và giới hạn ghi sau khi chạy, không suy PASS từ plan.

Không commit/push, không mở P10 hoặc phần P17 còn hoãn. Tái dùng AppShell, Auth client, API và Cloudinary authenticated hiện có; không thêm framework.

## Kết quả thực thi

- Đã thực hiện bốn thay đổi. Riêng kiểm điều kiện khác được giữ bằng các nút xác nhận rõ theo loại, không tự pass toàn bộ và không bắt nhận xét. Không hiển thị lịch sử kiểm PDF tại vùng thao tác; dữ liệu cũ vẫn giữ.
- Phát hiện thêm bug thật khi CV Đọc lại hồ sơ: root đã supplement_responded nhưng `StaffSupplement` chỉ tải detail theo ID vòng, nên vẫn giữ open. Browser assertion tái hiện đỏ; thêm dependency rootTag. Ca SV trả lời lần2 → CV Đọc lại ngay tại tab hiện tại đã xanh, không cần đổi tab/reload trang để che lỗi.
- FE Unit `node --test frontend/tests/review-flow.test.mjs frontend/tests/dossier.test.mjs`:10PASS/0FAIL/0SKIP. Sáu ca review-flow gồm chọn tiếp n giấy (5mục), latest check, tuần tự If-Match, lỗi chặn bước sau, đọc lại completed không ghi, stale412 và bảo vệ typed/open.
- BE `mvnw.cmd -o -q -Dtest=ApplicationDispositionHttpShapeTest,ApplicationDispositionRulesTest,SupplementRulesTest test`:10tests,0failure/0error/0skip. Controller shape dùng boundary stub, không gọi là SQL/HTTP thật. Trước sửa hai ca approved thiếu/null reason trả VALIDATION_ERROR; sau sửa đạt. Rejected vẫn bắt lý do.
- `npm.cmd run build` tại frontend: TypeScript/Vite PASS,89modules. Đã build executable JAR qua `dev.ps1 build` không chạy suite, stop/start đúng launcher. Source/JAR DEV mới; DB vẫn FlywayV18, không migration/reset/seed.
- Playwright MCP:12nhóm browser/HTTP PASS trên `https://127.0.0.1:5173`, Spring Boot/SQL Server `ctsv_dev` thật. Gồm ba giấy auto-next/readback, dialog lý do, CV gửi BS1, SV phản hồi1, tạo BS2 qua BS04/06/01, SV phản hồi2, refresh chi tiết theo ETag, hoàn tất general chưa tự duyệt, HTTP400/412/403, duyệt không reason200, lịch sử3bản và mobile360px, SV đọc own và direct URL thiếu quyền. Không dùng mock API nghiệp vụ.
- Cloudinary: một PDF synthetic `du lieu test.pdf` upload REAL qua Spring Boot để chuẩn bị fixture (không gọi là upload E2E qua UI); file10031. Không gọi CA DUT, không đổi storage/Cloudinary config.

### Dữ liệu/evidence

Fixture mới là hồ sơ10027, process synthetic2, đợt20040 có sẵn. BS15(lần1)=completed/unsatisfied; BS16(lần2)=completed/satisfied; ba bản nộp vẫn còn. Trạng thái cuối approved, không lý do tự bịa. SQL có6check content/pass (bản đầu và bản phản hồi cuối); phản hồi mới không thừa hưởng check cũ. Hồ sơ10024 vẫn rejected,10025 vẫn supplement_responded, yêu cầu14 của10025 không bị xử lý bởi bài test này. Không xóa dữ liệu cũ hay tạm tắt trigger.

Evidence local ngoài Git: `target/intake-review-20261122/report.json` và `staff-overview.png`. Báo cáo không có credentials/URL ký. Các lỗi script trung gian (đọc danh sách trước khi tải xong, nhầm expected200 với XET06 contract201, HMR trả về danh sách, HTTP client Node không tin TLS DEV) không tính là lỗi ứng dụng hoặc PASS; đã giữ phiên/trạng thái và tiếp tục đúng ca, không POST lại mù. HTTP âm tính cuối chạy bằng fetch trong browser có trust TLS hiện hành, không tắt validation.

### File thay đổi trong task

- Backend: `ApplicationController.java` (HS08 reason theo result), `ApplicationDispositionHttpShapeTest.java`.
- Frontend: `StaffDossierReview.tsx`, `SupplementWorkspace.tsx`, `IntakeWorkspace.tsx`, `dossier-types.ts`, `IntakeWorkspace.module.css`; mới `review-model.mjs`, `supplement-workflow.mjs`, `tests/review-flow.test.mjs`.
- Tài liệu: plan/report này; contract `mghp-application-api.md`, `submission-supplement-api.md`; `ctsv/api-catalog.md`; `application-test-cases.md`, `submission-test-cases.md` đồng bộ delta22/11. Không sửa Backlog/Roadmap hoặc nghiệm thu P17.

### Giới hạn

Chuỗi đóng/mở vòng là các TX độc lập, không hứa rollback toàn bộ nếu bước cuối lỗi. Lỗi/retry đã kiểm bằng Unit boundary stub; chưa inject SQL fault thật giữa ba command. Browser dùng3mục giấy; model kiểm5mục, không tuyên bố E2E5PDF khác nội dung. Không chạy lại toàn P05–P08, không tự nghiệm thu phase mới. Không commit/push.
