# Hồ sơ MGHP — trạng thái chung và kết quả từng bước

> **DEV26/11/2026: ĐÃ ÁP DỤNG VÀ SMOKE PASS trên `ctsv_dev`** — xem [bằng chứng DEV bên dưới](#dev-20261126). Các giới hạn NOT VERIFIED ngày25/11 giữ để truy vết, đã được bổ sung bằng lượt DEV này. Không migration/backfill cho delta trạng thái.

**Owner approved 25/11/2026.** Kế hoạch và ledger của lượt thay đổi trạng thái; không thuộc phần quản trị ký số đang triển khai song song.

## Quy tắc đã chốt

- `status`: submitted / processing / supplementing / approved / rejected / withdrawn. Approved chỉ khi bản cứng được xác nhận đạt; không đồng nghĩa được chọn hưởng.
- `onlineStatus`: pending / supplementing / supplement_responded / approved / rejected / withdrawn / unspecified. Giữ kết quả này khi đã chuyển bản cứng.
- `hardCopyStatus`: not_transferred / pending / in_progress / approved / rejected. Chỉ vào inbox bản cứng sau chốt online. Dự thảo hoặc kết luận cần xác minh chưa phải đã duyệt.
- `currentStage`: online / hard_copy / council / ended; kết quả Hội đồng riêng `councilResult` (selected/not_selected/null), SV chỉ nhận sau công bố.
- `workflowStatus` là alias tương thích của trạng thái chung, không còn trộn V1/V2/Hội đồng. Trạng thái legacy không đủ căn cứ không được tự đổi thành approved.
- Chuyên viên đóng nhận: submitted chưa kết luận hiển thị processing, không sửa lịch sử nộp. SV gửi bổ sung: processing + onlineStatus supplement_responded.
- Tiếp nhận lọc `stage=online`: pending (Chờ xử lý), supplement_responded, approved, rejected, withdrawn; cần bổ sung xem trong Tất cả. Nhãn kết quả ở dòng vẫn phân biệt Chờ xét online/Cần bổ sung/Sinh viên đã bổ sung/Đã duyệt online — V1/Từ chối online/Đã rút.
- Bản cứng lọc `stage=hard-copy`: pending gồm pending/in_progress, approved, rejected. Nhãn từng dòng Chờ đối chiếu/Đang đối chiếu/Đã duyệt bản cứng — V2/Đã từ chối bản cứng.
- Không mở lại/nộp lại root cũ hoặc dời đợt trong phạm vi này. HS05/HS07 retire 410 sau kiểm quyền/ownership; giữ lịch sử và bản nộp cũ. Không ngăn phản hồi bổ sung hợp lệ.
- Không đổi thuật toán P08/P10, thứ tự ký, source lock, schema hoặc migration lịch sử. Dùng projection từ SQL hiện có.

## Thực hiện (inline, không commit/push)

- [x] 1. Đồng bộ delta contract/owner/test và test RED cho trạng thái/nhãn/bộ lọc.
- [x] 2. Backend projection chung + từng bước, lọc/count trước pagination, lý do từ chối đúng bước; retire mở lại/nộp lại, không sửa lịch sử.
- [x] 3. React Tiếp nhận/Bản cứng/SV dùng đúng projection, bỏ thao tác mở lại, giữ readonly sau chốt.
- [x] 4. Unit/FE build → SQL/HTTP quyền/lịch sử/pagination trên DB riêng đã được owner cho dùng → browser thật cùng backend/test SQL riêng. DEV launcher của chat chính không thay đổi.

## Acceptance và test IDs

STATUS-01 submitted→processing theo đóng nhận; STATUS-02 bổ sung tách kết quả bước; STATUS-03 online approved chưa approved chung; STATUS-04 bản cứng pending/draft/dat/khong_dat; STATUS-05 online history không đổi khi bản cứng rejected; STATUS-06 Hội đồng không đổi status và SV không thấy sớm; STATUS-07 lọc/count/page đồng nhất theo stage; STATUS-08 HS05/HS07 không mutation; STATUS-09 SV khác không đọc được root; STATUS-10 stale ETag/readonly sau chốt giữ nguyên.

G0/G2/G3/G6/G7 theo tác động; không migration nên G1 migration N/A. SQL/HTTP dùng `ctsv_review_lifecycle_test_20261122` đã được owner cho phép, kiểm marker/owner/đích; không reset. Browser smoke chỉ dữ liệu synthetic đã có trên DEV và không thay launcher đang được chat chính sử dụng khi chưa cần. Không CA hoặc Cloud upload để kiểm nhãn/lọc.

## Ledger

- Đọc source và contract: root.status lưu kết quả tiếp nhận; ApplicationWorkflow đang gộp V1/V2/selection; SQL count/paging cần sửa cùng projection. Chốt online/bản cứng đã có, giữ nguyên guard.
- Ruling: tái sử dụng raw trạng thái tiếp nhận nội bộ và các assessment/round markers, không migration/backfill. API `status` là trạng thái chung; `onlineStatus` là kết quả bước cho consumer, không để FE suy kết quả từ nhãn chung.
- Ruling: không tạo worktree hoặc khởi động lại process dùng chung trong side conversation. Chỉ sửa các file thuộc request; phần SigningSupport của chat chính giữ nguyên; Maven sẽ chạy với thư mục build riêng nếu cần.
- DB test permission: owner cho phép dùng lại DB nêu trên, chỉ thêm synthetic, không reset/xóa dữ liệu.
- Chưa chạy test hoặc sửa runtime tại thời điểm tạo plan; các kết quả được cập nhật sau thực thi.
- RED: ApplicationStageStatusTest 4 FAIL do thiếu projection mới (0 errors); frontend test thiếu exports mới. Sau code: Java projection 4 PASS và related old gate unit PASS, frontend focused 21 PASS, build PASS.
- SQL/HTTP: lần đầu bị sandbox chặn TCP (Permission denied getsockopt), chưa vào nghiệp vụ; chạy với quyền truy cập đích đã được owner cho phép → 3 PASS, sau bổ sung test rút hồ sơ → 4 PASS/0 fail/0 error/0 skip. DB guard instance/owner/marker đạt, Flyway migrate/validate source hiện hành; không DB reset. SQL file fixture chỉ metadata, không kiểm Cloud/PDF/CA.
- Phát hiện trong phạm vi: ETag của representation SV khác CV. Lệnh rút đã đổi sang kiểm đúng representation SV, test thật thiếu428/stale412/đúng200 và bản nộp cũ giữ nguyên.
- Browser vòng đầu: UI01–02 PASS; ca tìm bảng bản cứng bị timeout locator. Giữ ảnh/error để đối chiếu; đã đặt accessible name rõ cho bảng và chạy lại đúng ca browser, không chạy toàn bộ suite. Chưa kết luận browser PASS khi đang kiểm.

## Kết quả sau sửa và giới hạn — 25/11/2026

| Kiểm tra thực chạy | Kết quả | Evidence |
|---|---|---|
| 6 lớp Java unit: ApplicationStageStatus, ReviewLifecycleRules, ApplicationDispositionRules, ApplicationDispositionHttpShape, SupplementRules, EvaluationRules | **18 PASS**, 0 fail/error/skip | target/surefire-reports/TEST-pbl6.ctsv.mghp.application.*Test.xml; lượt 22:20:49 |
| STATUS01–04 qua HTTPS/Auth/JPA/SQL Server | **4 PASS**, 0 lỗi nghiệp vụ; gồm sau publication fixture, root/list, COUNT/page, readonly, ownership, retire, rút ETag | target/application-stage-status-final.log: tổng 5 case, chỉ wrapper browser lỗi harness trong lượt này; 4 case HTTP đạt |
| STATUS05 wrapper browser, chạy lại riêng sau sửa harness | **1 PASS**, 0 fail/error/skip; 6 kiểm tra UI đạt, pageErrors=[] | target/failsafe-reports/TEST-pbl6.ctsv.auth.ApplicationStageStatusHttpIT.xml; target/application-stage-browser/result.json |
| Frontend 4 file test tập trung | **21 PASS**, 0 fail/skip | target/application-stage-frontend.log |
| TypeScript + Vite build | **PASS**; còn cảnh báo bundle >500kB, không đổi dependency | target/application-stage-build.log |
| Diff whitespace phạm vi source của task | **PASS** | git diff --check với danh sách 15 file BE/FE của task; không sửa whitespace của file Signing do chat chính làm |

Các lỗi browser trước là harness chưa chờ đúng response học kỳ/đợt và React cập nhật danh sách, dẫn tới locator không khớp/strict-mode nhiều dòng hoặc đọc 3 dòng cũ thay vì 1. Không đổi expected, không force-click, không mock response; sửa chờ đúng URL/query + số dòng + trạng thái enabled rồi chạy lại riêng browser. Lượt sau sửa đạt UI01–06: sáu bộ lọc online, V1 bảo toàn, bộ lọc bản cứng, mobile, tiến độ/lý do SV, Admin thiếu quyền bị chặn.

Lệnh tại root CTSV (P00_SQL_PORT=56826 + JDBC integrated-auth DLL PATH sau xác minh):

```powershell
.\mvnw.cmd -o -q '-Dtest=ApplicationStageStatusTest,ReviewLifecycleRulesTest,ApplicationDispositionRulesTest,ApplicationDispositionHttpShapeTest,SupplementRulesTest,EvaluationRulesTest' '-Dreview.lifecycle.test.enabled=true' '-Dapplication.stage.browser.enabled=true' '-Dit.test=ApplicationStageStatusHttpIT' test failsafe:integration-test failsafe:verify
# Browser-only rerun sau sửa harness:
.\mvnw.cmd -o -q '-Dreview.lifecycle.test.enabled=true' '-Dapplication.stage.browser.enabled=true' '-Dit.test=ApplicationStageStatusHttpIT#STATUS05_browserShowsEachStageAndStudentProgress' test-compile failsafe:integration-test failsafe:verify
```

Tại frontend: `node --test tests/application-stage-status.test.mjs tests/review-lifecycle.test.mjs tests/review-flow.test.mjs tests/p1-review-context.test.mjs`; `npm run build`.

### File chính

- BE: ApplicationWorkflow (projection + SQL filter), ApplicationReads (summary/detail), ApplicationController (stage + retire), ApplicationService (ACK + retire + đúng ETag rút), IntakeRules (filter enum), ReviewLifecycleRules (giữ điều kiện nguồn, bỏ hàm tổng hợp cũ).
- FE: presentation.mjs; intake/{IntakeWorkspace,StaffDossierReview,SupplementWorkspace,dossier-model,dossier-types}; review/{ReviewWorkspace,ReviewWorkspace.module.css,model}. Không đổi AppShell/router/theme/Auth hoặc signing code.
- Test: thêm ApplicationStageStatusTest/ApplicationStageStatusHttpIT, application-stage-status.test.mjs, application-stage-status.cjs; cập nhật expected lifecycle ở ReviewLifecycleRulesTest/review-lifecycle.test.mjs, StageHandoffHttpIT/DecisionHttpIT và 2 browser script cũ. Các script/suite legacy sau chỉ cập nhật expected theo owner, **chưa chạy lại toàn bộ**; không dùng chúng làm bằng chứng PASS.
- Tài liệu: delta application-stage-status.md, 3 HS/BS/REV contract notices, API Catalog, nghiệp vụ/logic, Owner Decisions, Master Backlog/Roadmap, SUB/REV test notices. Không đổi tổng task/API ID vì chỉ thay semantics/retire chức năng cũ.

### Ranh giới nghiệm thu

- Browser dùng React thật ở https://127.0.0.1:5193, backend test HTTPS cổng ngẫu nhiên, SQL Server **ctsv_review_lifecycle_test_20261122** đã được owner cho phép. Không phải smoke `ctsv_dev`; không sửa launcher, config hoặc nâng DB DEV của chat chính. Kết quả trên bản DEV đang chạy **NOT VERIFIED**, cần build/restart đồng bộ FE/BE khi chat chính sẵn sàng.
- Data synthetic append trong DB test, không xóa/reset dữ liệu. PDF chỉ metadata; không tuyên bố render/upload/download/cloud/CA từ lượt kiểm trạng thái này.
- Không migration mới cho delta; Flyway trên test DB validate 32 migration hiện hành (V32 thuộc công việc SigningSupport của chat chính), Hibernate startup validate đạt. Không thay SQL/migration đã áp dụng.
- Tự review theo yêu cầu từng vai trò, API/SQL predicate, publication gate và lịch sử. Không dùng subagent hoặc tuyên bố review độc lập/G8 PASS. Các quyết định waiver hiện hành giữ nguyên; không nâng trạng thái nghiệm thu phase nào.
- Không commit/push/merge/deploy.

<a id="dev-20261126"></a>
## Bàn giao DEV thực tế — 26/11/2026

Owner đã yêu cầu áp dụng và kiểm thử trên DB chính DEV. Đã xác minh `localhost\\DEV2022:56826 / ctsv_dev`, owner/marker `ctsv-local-dev-v1`; schema V32 đã có từ trước. Không cần chạy migrate, sửa history/checksum hoặc rewrite root status để áp dụng projection mới.

- `dev.ps1 stop` chỉ xử lý state launcher đã dừng; `dev.ps1 build` đạt. Lệnh start gặp mutex do launcher khác đang hoàn tất; không dừng tiến trình khác. Sau đó xác nhận backend9588/frontend14052 READY, hash source trùng build đang dùng, API thật trả đầy đủ projection mới.
- Launcher `check` validate **32 migration** và Hibernate validate khi startup đạt. FE `https://127.0.0.1:5173`, BE `https://127.0.0.1:8443`, profile DEV/DB đích do launcher ép rõ. Cả hai vẫn RUNNING khi bàn giao.
- Không tạo đợt/hồ sơ mới: dùng đợt20051 `HAND25-DEV-20261125` (10042 đạt,10043 từ chối bản cứng) và đợt20040 `UX-INTAKE-1795271503790-3` (8 hồ sơ online approved:6 chờ +2 có bản nháp). Giữ toàn bộ dữ liệu, không reset/drop/delete hoặc gọi ký CA.

| Smoke browser + HTTP thật | Actual |
|---|---|
| DEV01: status chung/bước, source đã chốt không được kết luận lại, mở lại retired | PASS; 10042 approved/online approved/paper approved;10043 rejected/online approved/paper rejected; mutation sau chốt409, mở lại410 |
| DEV02: bộ lọc Tiếp nhận, V1 giữ khi giấy bản cứng bị từ chối | PASS; đúng6 nhãn lọc, approved2, pending0 ở đợt20051 |
| DEV03: lọc bản cứng đạt/từ chối/cần đối chiếu | PASS;20051 approved1/rejected1,20040 cần đối chiếu8 gồm6 pending +2 in_progress (10028/10037 có draft thực trong SQL) |
| DEV04: SV xem trạng thái đúng hồ sơ, ownership, Admin không có quyền CV, mobile | PASS; SV10029 processing/online approved/paper pending; SV xem10042→404, dùng stage nội bộ→403, Admin stage→403; UI chặn tương ứng |

**Lỗi thật phát hiện/sửa trong lượt DEV:** `.badge` của IntakeWorkspace có white-space nowrap làm hai nhãn dài của SV vượt khung: panelRight357, badgeRight376.875 ở viewport390. Đã thêm max-width/wrapping và chặn heading con vượt chiều rộng. Giữ nguyên nội dung trạng thái/nghiệp vụ. Smoke chạy lại flow SV sau sửa, chờ đúng region Chi tiết hồ sơ trước assert/chụp ảnh, xác nhận đủ2 nhãn trong khung và không tràn trang. Build FE sau sửa PASS; chỉ còn cảnh báo bundle >500kB từ build hiện hành.

Các lần dừng harness được giữ trong failure history: giả định sai cả8 đều pending (đã sửa theo SQL2draft), điều hướng reload+goto liên tiếp hủy refresh (đã bỏ điều hướng thừa, không sửa Auth/bỏ guard), và kiểm mobile trước khi layout ổn định. Chỉ chạy lại ca liên quan, giữ context để điều tra. Ảnh/harness không thay cho assertion API/UI. Kết quả cuối **4 flow PASS /0 chưa giải quyết, pageErrors=[]**.

### Bảo toàn dữ liệu và evidence

- Trước/sau: **12 hồ sơ,6 đợt,8 biên nhận ký,8 bản ghi output PDF ký**. Hash toàn bộ `bien_nhan_ky` và `tep_ket_qua_ky` bằng nhau. Không chạm nội dung PDF/cloud; không dùng waiver thay kiểm kỹ thuật.
- `target/application-stage-dev-20261126/{before.json,after.json,browser.json,mobile-before.json,mobile-after.json,frontend-build.log}` và ảnh01–05. `before.json`/`after.json` là read-only snapshot, không phải backup/restore; không cần backup DB vì không có DDL/business-data migration ở lượt này.
- Script mới `frontend/e2e/application-stage-status-dev.cjs`: credentials DPAPI chỉ trong RAM, mỗi role dùng lại session; giữ browser khi fail, retry đúng phase; không lưu token/password/storageState/trace đăng nhập. Dùng marker fixture và đúng URL/DB đã xác minh.
- Lệnh: `dev.ps1 build`, `dev.ps1 status`; nạp public TLS certificate bằng NODE_EXTRA_CA_CERTS rồi `node frontend/e2e/application-stage-status-dev.cjs`. `CTSV_STAGE_START=2/3` chỉ tiếp tục từ ca đã điều tra, giữ evidence các ca đã đạt, không skip hoặc đổi expected nghiệp vụ. Tại frontend `npm run build` sau sửa CSS.
- Không chạy lại toàn bộ suite P05–P11; không gọi CA, upload PDF, renew chứng thư hoặc thay đổi module khác. Không commit/push/merge.
