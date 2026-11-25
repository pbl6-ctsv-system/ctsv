# Duyệt hồ sơ online → đối chiếu bản cứng — Implementation Plan

Owner22/11 cho thực thi; làm tuần tự bằng executing-plans/TDD, không commit/push. Giữ Java21/Boot3.5.16/React hiện hành.

## Quyết định và kiến trúc

- Hồ sơ phải được đồng ý tại tiếp nhận (`approved`, hiển thị Đã duyệt V1 — hồ sơ online) mới tạo/sửa/xác nhận/lập lại đánh giá hồ sơ. Chặn cả submitted/processing/supplementing/reopened; nguồn quyền hưởng có luồng riêng, không tự bỏ.
- Xác nhận không đạt bản cứng là kết thúc hồ sơ: không revision/kiểm giấy lại; không xóa snapshot/lịch sử. Owner đã chọn dừng, không cho đối chiếu lại.
- Giữ evidence/eligiblePeriods và validation có căn cứ. Đổi tên workspace thành Đối chiếu bản cứng; tiếp nhận rõ là duyệt online.
- `status` tiếp nhận được giữ tương thích. Thêm `workflowStatus` server cho tiến trình: approved_v1/approved_v2/hard_copy_failed/awaiting_council/selected/not_selected; chỉ quyết định locked mới cho selected/not_selected. completed cũ không tự thành selected khi thiếu căn cứ.
- Read projection dùng dữ liệu evaluation/round/decision đã có, không sửa nguồn đã Đóng xét để cập nhật nhãn, không thay decision hash/revision. Không cần DDL/Entity mới nếu không thêm cột; giữ V1–V23.
- ETag detail hash cả projection. Filter/count server cùng predicate, không lọc sau phân trang. Mutation giữ lock order hiện hữu, audit/TX/CAS; guard approved kiểm ngay trong transaction. Hard failure chỉ áp nhánh application, không tự khóa đánh giá quyền hưởng khác.

## Checklist thực hiện

- [x] 1. Đồng bộ owner/business/HS/XET/decision read contract, tests theo yêu cầu mới. Không sửa test để che lỗi; fixture mới phải qua duyệt V1.
- [x] 2. RED→GREEN Unit gate/lifecycle/FE; backend source guard và terminal failure; workflow projection/list/detail/filter.
- [x] 3. UI tên dễ hiểu, chỉ danh sách V1 và bước sau; direct URL hồ sơ chưa duyệt xem giải thích, không lệnh đánh giá. Hiển thị bảng đối chiếu SIS/PDF/tiêu chí, giữ căn cứ/khoảng. Readback ETag/status đồng bộ.
- [x] 4. SQL/HTTPS/security/concurrency trên DB riêng ctsv_review_lifecycle_test_20261122 đã được owner cho phép, owner/marker guard, không reset/drop DB khác. Targeted regression P08/P10; build/typecheck/FE tests.
- [x] 5. dev.ps1 stop/build/start và Playwright trên ctsv_dev: reuse fixture đánh dấu, chỉ tạo dữ liệu mới cần thiết; không thay hồ sơ/nguồn đã chốt. Backend/FE/SQL thật, không gọi CA/Cloudinary khi chỉ kiểm trạng thái/điều hướng.
- [x] 6. Tự review, evidence counts/REAL-STUB, giới hạn và báo cáo; không mở phase mới.

## Acceptance và kiểm chứng

- API create/update/confirm/revision từ hồ sơ chưa duyệt bị từ chối, không thay DB; V1 hợp lệ được qua, thiếu quyền vẫn403.
- V2/không đạt/chờ Hội đồng/selected/not_selected đúng sự kiện backend; bản dự thảo QĐ không đổi kết quả chính thức. Lower M không đồng nghĩa giấy không hợp lệ.
- Failed bản cứng terminal, history cũ không sửa. Stale tag/rollback/race không vượt gate. Không phá nguồn/hashes P10 đã chốt.
- UI gọi backend thật; lỗi nhìn thấy, giữ input; menu chung và quyền không đổi. Căn cứ/khoảng vẫn hiện và gửi đúng payload.
- Lệnh mục tiêu: Maven selectors lifecycle Unit/HTTP trên DB guard; frontend node tests liên quan + npm run build; Playwright MCP DEV. Chỉ ghi PASS sau thực thi.

## Kết quả triển khai22/11

### Nghiệp vụ và API

Đã triển khai delta owner, không mở phase mới hoặc nghiệm thu lại P08/P10/P17. Chỉ hồ sơ có intake `approved` (Đã duyệt V1 — hồ sơ online) mới tạo/sửa/xác nhận/lập lại đánh giá. Guard trong transaction dùng cùng fence/lock order với tiếp nhận; FE không là nơi quyết định quyền. Nhánh đánh giá từ quyền hưởng giữ vòng đời riêng.

Bản cứng không đáp ứng: confirmed/current/khong_dat là terminal cho hồ sơ này, không revision hoặc kiểm giấy lại. Không dùng thiếu nguồn/can_xac_minh thay khong_dat. Giữ nguyên evidence/eligiblePeriods/timeEvidence, validation M và source history; không tự pass hàng loạt để bớt ô nhập.

| workflowStatus | Nhãn | Nguồn thực tế |
|---|---|---|
| approved_v1 | Đã duyệt V1 — hồ sơ online | Intake approved, chưa có kết quả dat confirmed của bản nộp hiện tại |
| approved_v2 | Đã duyệt V2 — bản cứng | Đánh giá dat/current/confirmed |
| hard_copy_failed | Bản cứng không đáp ứng | Đánh giá khong_dat/current/confirmed, kết thúc hồ sơ |
| awaiting_council | Chờ Hội đồng | Kết quả dat và receiving round đã Đóng xét |
| selected | Được chọn | Candidate của head locked được chọn |
| not_selected | Không được chọn | Candidate của head locked không được chọn do M/mức/priority |

`status` intake giữ tương thích, không sửa đè root/snapshot nguồn chỉ để cập nhật nhãn. Thêm workflowStatus/evaluationAllowed vào read DTO; strong ETag detail hash cả projection. Filter/count dùng cùng predicate trước phân trang, không lọc trên một trang FE. completed cũ thiếu kết quả QĐ không tự được nâng thành selected; UI chỉ giữ nhãn tương thích Đã kết thúc (dữ liệu cũ), không là trạng thái hoạt động mới.

Theo owner chốt thêm: CV thấy chọn/không chọn sau locked; SV chỉ sau bản ghi công bố đúng phiên bản có actual timestamp. Trước đó cả detail lẫn filter/count của SV trả tiến trình chờ Hội đồng, không tiết lộ kết quả qua số lượng. Chỉ đọc source publication có sẵn; không tạo hoặc triển khai API công bố P12.

Không thêm endpoint, cột, bảng, Entity hoặc migration. Flyway/Entity vẫn V23. `ApplicationWorkflow` là repository read projection SQL chuyên biệt; các aggregate ghi vẫn Entity/Repository hiện hành. Không thay Auth/JWT/capability chung, DRL/SHCN/Học bổng, framework hoặc signing.

### Giao diện

- Navbar giữ MGHP chung, đổi Đánh giá hồ sơ thành **Đối chiếu bản cứng**; duyệt bước đầu mang tên **Duyệt hồ sơ online**.
- Danh sách đánh giá chỉ lấy intake approved; direct URL hồ sơ chưa duyệt hiển thị lý do và không cho Bắt đầu đối chiếu.
- Hiển thị bảng kê khai/SIS, tên danh mục thay ID, PDF và kết quả từng tiêu chí; giữ giấy làm căn cứ và thời gian đủ điều kiện hưởng. Tên accessible của kết luận và từng tiêu chí khác nhau.
- Đối chiếu từng giấy ghi nhận bản cứng/hình thức/nội dung/hiệu lực/chủ thể bằng API kiểm giấy hiện hành. Người dùng xác nhận từng phần đã kiểm, không tự pass khi đồng ý hồ sơ hoặc chọn kết luận Dat. Không khôi phục nút Từ chối PDF.
- Lưu/xác nhận đọc lại source/evaluation/tag. Kết quả không đạt chỉ đọc, không có nút lập lại; intake và trang của SV hiển thị đúng tiến trình mới.

### Kiểm thử đã chạy

| Mức | PASS | FAIL | SKIP | Thật/mock |
|---|---:|---:|---:|---|
| Backend lần regression đầy đủ có trọng tâm | 34 | 0 | 0 | 21 HTTPS/JWT/JPA/SQL thật;13Unit |
| Follow-up privacy/wiring | 7 | 0 | 0 | 5HTTP/SQL +2Unit đã nằm trong34ca; không cộng thành41ca duy nhất |
| Frontend tập trung | 24 | 0 | 0 | Unit, client stubs của suite liên quan; không nhận stub là SQL integration |
| Playwright DEV | 21 | 0 | 0 | Kiểm tra có tên, không phải21workflow độc lập; Chrome/React/Spring/ctsv_dev thật |
| Frontend build/typecheck | PASS | — | — | tsc --noEmit + Vite hiện hành |

Backend DB duy nhất cho lượt test này: `ctsv_review_lifecycle_test_20261122` trên localhost\DEV2022, Windows owner/marker guard, fresh Flyway V1–V23. Không ghi DB evidence P05/P08/P10 cũ. HTTP regression gồm authorization/revoked JWT, create/meeting/confirm races, rollback khi audit lỗi, source hash/metadata/history và MAX không lấp thời gian. Fixture đã điều chỉnh để duyệt V1 qua HS08 thật trước đánh giá theo nghiệp vụ mới; không thay assertion để che lỗi.

Lệnh đã chạy, cwd `ctsv`, P00_SQL_PORT đọc registry đã xác minh và native SQL auth DLL trong môi trường process:

```powershell
mvn -o -q '-Dtest=ReviewLifecycleRulesTest,ApplicationDispositionRulesTest' test
mvn -o -q '-Dreview.lifecycle.test.enabled=true' '-Dtest=DecisionHttpIT,ReviewLifecycleRulesTest,ApplicationDispositionRulesTest,EvaluationRulesTest,TimelineRulesTest' test
mvn -o -q '-Dreview.lifecycle.test.enabled=true' '-Dtest=DecisionHttpIT#reviewLifecycle*+sqlHttpMaxChoosesSingleHigherMWithoutFillingMonths,ReviewLifecycleRulesTest' test
node --test frontend/tests/review-lifecycle.test.mjs frontend/tests/review-flow.test.mjs frontend/tests/p1-review-context.test.mjs frontend/tests/mghp-presentation.test.mjs frontend/tests/mghp-decisions.test.mjs
npm.cmd --prefix frontend run build
```

Browser: local Playwright đăng nhập ba tài khoản từ DPAPI DEV trong RAM, sau đó Playwright MCP nối Chrome qua CDP loopback để thực sự click/nhập/xác nhận. Không password/token/storageState/trace thô trong file test/evidence. Lỗi giữ context để điều tra. PDF10028 đã có được dùng lại, HTTP200/application/pdf/1.625.263bytes và chữ ký `%PDF-` trong Blob thực; không upload mới hoặc gọi CA. Mock/stub không thay kết quả browser.

Các kiểm tra chính: chưa duyệt không bắt đầu được; duyệt online V1; đối chiếu giấy; Dat→V2; Khongdat→terminal; refresh vẫn terminal; SV/Admin direct URL bị chặn; SV chỉ thấy hồ sơ của mình (đối chiếu dataset có chủ2–5); mobile390px; filter V2/failure/chờ Hội đồng/chọn/không chọn đúng UI và HTTP. Các trạng thái Hội đồng trên DEV chỉ đọc fixture P10UI đã chốt sẵn, không tạo QĐ mới trong lượt này. Privacy sau công bố dùng **publication SQL fixture** trên DB test; không phải nghiệm thu P12.

### Dữ liệu và DEV

Tạo đúng hai đợt có mã `RLUI-20261122-DAT/FAIL` (20043/20044) và hai hồ sơ10035/10036, bản nộp10048/10049, giấy10072/10073. Không thêm account, quá trình học hoặc chính sách. Dùng quá trình2 đã verified thuộc SV DEV; quá trình1 cũ còn thiếu mốc/xác minh được giữ nguyên, không sửa để test xanh. File10028 dùng lại đúng owner qua identity mapping. Một hồ sơ kết quả Dat/V2, một hồ sơ Khongdat/terminal để owner xem.

Hash toàn bộ root hồ sơ không có tiền tố RLUI trước/sau trùng khớp. Không xóa/reset/clean hoặc tắt trigger. Chi tiết fixture có guard đích/launcher và persisted outcomes trong manifest. Các snapshot đã confirmed/chốt trước đó không bị sửa; dữ liệu fixture mới thiếu một số label snapshot cũ (ví dụ lớp ở list có thể Chưa ghi nhận), không giả dữ liệu pháp lý hoặc tự backfill nguồn cũ.

DEV đã stop/build/start bằng launcher, không migration pending mới. Runtime PID/JAR hash cuối ghi trong manifest. Dữ liệu mới chỉ phục vụ kiểm thử có đánh dấu, không coi là hồ sơ sinh viên thật.

### Lỗi tìm thấy và sửa

- Thiếu gate approved ở source/locked; bổ sung guard mọi mutation và test nguồn chưa duyệt, không thể vượt qua bằng gọi API trực tiếp.
- SQL read projection mới ban đầu thiếu khoảng trắng trước FROM gây500: tái hiện đúng câu SQL trên DB test, sửa nối chuỗi, chạy lại hai ca lỗi rồi regression đạt.
- CCCD bị nhận nhầm là mã danh mục do kiểm endsWith(Id): thêm Unit RED→GREEN, chỉ resolve bốn field danh mục thật; browser xác minh CCCD giữ nguyên.
- Bảng đối chiếu/lý do chặn được kiểm DOM để bảo đảm đã mount, không chỉ import component. Các xác nhận giấy bắt buộc có đường thao tác thực tế, không tự sửa expected/validation.
- Lỗi fixture và harness tách khỏi app: cột issuer phải là don_vi_cap; owner file là nguoi_dung_id, không phải student ID; process1 chưa verified; chọn lại nguồn đã xác minh với guard còn nguyên. Transaction lỗi rollback và mã cố định chống tạo trùng. Assertion filter ban đầu chạy trước response hoàn tất và Buffer/Uint8Array khác nhau trong transport đã được sửa cách chờ/kiểm byte; không phải lỗi lọc hoặc PDF của ứng dụng.

### Files và giới hạn

Backend mới: ApplicationWorkflow.java, ReviewLifecycleRules.java. Sửa ApplicationReads/Controller, IntakeRepository/Rules, EvaluationService, DocumentReviewService. Tests: ReviewLifecycleDatabase/RulesTest mới, DecisionHttpIT cập nhật fixture và ca lifecycle/privacy.

Frontend: ReviewWorkspace/model, ReviewComparisons mới, presentation, IntakeWorkspace, StaffDossierReview và navigation/modules. Test mới review-lifecycle.test.mjs; e2e review-lifecycle-login.cjs, review-lifecycle-dev-fixture.ps1, review-lifecycle-dev.mcp.js. Tài liệu: plan này, persistence inventory; bên nghiepvu có nghiệp vụ chi tiết, owner register, HS/XET/QD contracts, review tests, Catalog/readiness, Backlog/Roadmap.

Không chạy toàn bộ P00–P10 hoặc full frontend suite. Bốn FAIL navigation ngoài phạm vi đã được owner cho bỏ qua ở lượt trước chưa sửa/chưa chạy lại, không ghi PASS toàn suite. Không chạy lại signing/CA/Portal/Kế toán, không triển khai P11/P12/P17 còn hoãn. Không tự gán G8 PASS hoặc đổi nghiệm thu lịch sử. Publication-read predicate đã kiểm SQL fixture, actual publish E2E vẫn thuộc P12.

[Manifest](../target/review-lifecycle-20261122/manifest.json), [browser cases](../target/review-lifecycle-20261122/browser-cases.json), [kết quả đạt](../target/review-lifecycle-20261122/review-dat.png), [không đáp ứng](../target/review-lifecycle-20261122/review-fail.png), [lọc trạng thái](../target/review-lifecycle-20261122/status-filters.png), [mobile](../target/review-lifecycle-20261122/review-mobile-confirmed.png). Không commit/push/merge.

**Bàn giao cuối:** DEV V23, BE27456/FE14880 RUNNING; JAR SHA-256 C9885C582FB30745F9F81B825928222F0DC8530AC549BDADC25A70B44CE0CE40. Đọc lại UI trên JAR cuối xác nhận hồ sơ10035/V2 và khoảng01/09–31/10 đúng persisted. Delta VERIFIED trong phạm vi trên; không thay trạng thái nghiệm thu P17 hoặc coi G8 là PASS.


**Hậu kiểm owner22/11 — sửa filter Đã nộp:** các tab trạng thái cũ còn lọc raw intake nên lẫn hồ sơ P10 legacy đang ở tiến trình cao hơn. Đã thống nhất mọi tab/UNSPECIFIED theo workflow projection và cùng predicate count/page; chỉ alias approved của inbox đối chiếu giữ raw pool có chủ đích. Không sửa dữ liệu mẫu. Owner yêu cầu không chạy test cho bản sửa này; chỉ build và cập nhật JAR DEV. Kết quả34/24/21 ở trên là lượt trước, không phải lần kiểm lại bản sửa filter này.
# Hậu kiểm thao tác đối chiếu — owner22/11/2026

- [x] Xác minh nguyên nhân: `evaluations.length>0` làm nút chính bị disabled khi đã có draft; confirmation bắt reason cho cả duyệt.
- [x] Nút chính mở tiếp đúng chain policy/kỳ và đọc lại server; bỏ nút draft chưa kết luận khỏi lịch sử ở luồng chính. Giữ lịch sử đã xác nhận.
- [x] Hai thao tác Duyệt bản cứng/Từ chối bản cứng; chỉ hiện Lý do khi từ chối. Duyệt lưu và xác nhận bằng ETag vừa đọc, không tự pass tiêu chí/giấy. Lưu để làm tiếp là thao tác phụ.
- [x] Contract XET04 và BE cho application/dat reason nullable; negative/benefit/revision vẫn cần lý do phù hợp. Không migration, không đổi dữ liệu nguồn.
- [x] Build/typecheck và17 frontend tests (review-lifecycle, review-flow, p1-review-context) PASS. HTTP/SQL5 lifecycle tests PASS/0 fail/0 error/0 skip trên ctsv_review_lifecycle_test_20261122. Ca mới trước sửa trả400, sau sửa xác nhận200 và V2.
- [x] Browser DEV8 assertions PASS: resume draft10 của hồ sơ10028, action/reason/cancel/mobile, không tạo hay sửa draft, student/admin direct URL denied;0 page error. SQL read xác nhận DEV2022/ctsv_dev marker, V23 và10 session của launcher BE28532. FE8472.
- [ ] Browser ghi kết luận trên DEV: NOT VERIFIED. Duyệt tự động chặn thao tác dự kiến tạo revision/xác nhận trên fixture10035 vì ghi thêm lịch sử bất biến trong DEV; script chưa chạy và không retry qua đường khác. Thay bằng browser DEV read-only và HTTP/SQL thật trên DB test được phép. Không thay kết luận hồ sơ người dùng.

Source delta: ReviewWorkspace.tsx/module.css, review/model.mjs, presentation.mjs, EvaluationController/Service; test DecisionHttpIT, review-lifecycle.test.mjs; cập nhật script review-lifecycle-dev và thêm review-actions-dev.mcp.js. API Catalog/review contract/test cases được đồng bộ. [Evidence](../target/review-actions-20261122/manifest.json). Không commit/push, không chạy toàn bộ phase/backend/frontend suites. Các kết quả trước đoạn này giữ đúng phạm vi lượt cũ.

## Superseding delta owner25/11/2026

Quy tắc approved V1 là đủ để vào inbox bản cứng ở các mô tả22/11 phía trên là lịch sử, đã thay bởi approved V1 **và receiving round đã chốt online**. Bước chốt bản cứng dùng lại Đóng xét, không thay evaluation/source history hoặc trạng thái approved_v2/awaiting_council. [Kế hoạch/evidence delta](stage-handoff-20261125.md). Không coi evidence22/11 là bằng chứng delta25/11.
