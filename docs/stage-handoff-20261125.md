# Chốt online → bản cứng → Hội đồng — Detailed Implementation Plan

Nguồn: yêu cầu và ba câu trả lời owner25/11/2026; HS/BS/XET/DOT contracts hiện hành. Dùng writing-plans, executing-plans và TDD; tiếp tục trong checkout hiện có, không commit/push.

## Thiết kế đã thống nhất

- Chốt theo đúng một học kỳ và một đợt; backend kiểm cặp ID, capability MGHP và trạng thái, không tin bộ lọc FE.
- Chốt online khi mọi hồ sơ đã approved/rejected/withdrawn và mọi vòng bổ sung đã xử lý. Submitted/processing/supplementing/supplement_responded/reopened/legacy không rõ là blocker. Nếu còn open thì đóng nhận cùng transaction; không đóng khi preflight còn blocker.
- Marker online mới, actor/time/source hash và lịch sử append-only; không dùng approved V1 thay marker. Chỉ pool đã chốt mới vào bản cứng. Chặn sửa tiếp nhận/bổ sung sau chốt; bản cứng tiếp tục được kiểm đến Đóng xét.
- Chốt bản cứng tái sử dụng DOT08/ReviewSourceAccess hiện có; phải qua chốt online và mọi căn cứ hợp lệ. Giữ can_xac_minh có lý do là excluded, không tự đổi thành không đạt. Sau chốt dùng marker Đóng xét hiện có, P10 chỉ đọc nguồn đã chốt.
- Lịch dự kiến vẫn mềm. Không thêm trạng thái workflow, lịch engine, quyền Auth hay API xuất Excel. Nút Excel chỉ sau chốt bản cứng, disabled và ghi chưa triển khai.
- Dữ liệu lịch sử đã Đóng xét được nhận diện là legacy closure; không bịa ngày chốt online, không sửa history/hash/nguồn cũ.

## Phạm vi và interface

1. DOT11-R/11: GET/POST `/api/ctsv/mghp/dot-xet/{id}/chot-ho-so-online`. GET bắt `semesterId`; POST `{semesterId,reason}` và If-Match context. Counts/hash/blockers toàn tập; chốt và tự đóng nhận atomic.
2. DOT08 GET bắt `semesterId`, POST `{semesterId,reason}`; reuse transaction/khóa hiện có. Missing/stale ETag428/412, mismatch semester400, không đúng trạng thái/nguồn409, thiếu quyền403.
3. HS03-L thêm `stage=hard-copy` cho CV; filter receiving round/semester và marker trước count/paging. Detail có intakeSealed/evaluationAllowed server. XET write guard phải qua marker.
4. V26 additive (kiểm chuỗi trước áp): ba cột marker online trên round/history, FK/check/trigger bất biến; Entity/mapper đồng bộ. Không backfill ngày giả, không sửa migration cũ.
5. FE: `RoundStagePanel` trong feature MGHP dùng chung hai màn hình. Hiển thị ngữ cảnh, counts/blocker, xác nhận chốt, phản hồi và readback. Bản cứng thêm học kỳ/đợt; bỏ link chọn quyền/lookup nâng cao, giữ quy trình quyền hưởng riêng.

## Checklist và acceptance

- [x] T1 Contract/owner/test mapping cập nhật đúng delta; không đổi kết quả nghiệm thu cũ.
- [x] T2 RED→GREEN rules/source/scope; migration/entity/guard online và read pool server.
- [x] T3 DOT11 preflight/chốt/auto-close; DOT08 scope + prerequisite; SQL rollback/CAS/immutable/race.
- [x] T4 FE bộ lọc + hai nút chốt, khóa thao tác đúng stage, bỏ UI thừa, Excel placeholder; focused tests/build.
- [x] T5 SQL/HTTP/security/concurrency trên DB `ctsv_stage_handoff_test_20261125`, owner/marker guard; không reset/drop DB khác.
- [x] T6 backup/migrate/validate DEV, launcher và browser theo vai trò; fixture riêng có đánh dấu, không sửa hồ sơ người dùng để ép qua chốt.
- [x] T7 Review diff/secret và ghi bằng chứng thực chạy, REAL/STUB, giới hạn; không tự chuyển phase.

Gate: G0/G1/G2/G4/G5/G7 và regression P05–P08/P10 liên quan; G8 WAIVED BY OWNER theo phạm vi, không thay test kỹ thuật. Không gọi CA/upload mới chỉ để kiểm chốt.

## Test mapping mục tiêu

STAGE01 unresolved một hồ sơ chặn toàn đợt; STAGE02 supplemental unresolved chặn; STAGE03 close open+seal atomic; STAGE04 wrong/missing scope/ETag/security; STAGE05 chưa seal không vào list/không XET; STAGE06 seal khóa HS/BS nhưng cho bản cứng; STAGE07 đủ bản cứng→Hội đồng, sau chốt immutable; STAGE08 stale nguồn/race/retry; STAGE09 audit fault rollback; STAGE10 legacy closed history và kỳ/đợt khác không bị khóa; STAGE11 filter/count/paging; STAGE12 excluded/unknown giữ nguyên; STAGE13 draft/future/completed; STAGE14 regression P10. UI01–UI12 kiểm desktop/mobile/error/direct URL.

## Đồng bộ migration với main — 25/11/2026

Tên source hiện hành là `V31__mghp_online_stage_handoff.sql`, giữ nguyên SQL/checksum của V26 MGHP trong evidence triển khai dưới đây. Main sử dụng V26–V30 cho DRL/SHCN/Học bổng/Foundation. Không chạy lại SQL handoff trên DB đã có V26 MGHP; cần backup, kiểm bản sao và reconciliation history có guard theo phạm vi owner đã duyệt. Kết quả nâng DB ghi riêng trong báo cáo đồng bộ main, không đổi số version của evidence lịch sử thành kết quả chưa chạy.

## Ledger

Đang triển khai. Các thay đổi chưa commit của token/PDF/tuần tự đợt và bộ lọc tiếp nhận được giữ nguyên, không tính là sản phẩm của task này. Chỉ chạy test liên quan; chưa có kết quả PASS cho delta mới.

## Kết quả thực thi25/11 — TECHNICAL PASS trong phạm vi delta

### Đã triển khai

1. DOT11-R/DOT11 mới tại chot-ho-so-online, bắt semesterId và ETag preflight. Kiểm toàn tập, không phụ thuộc status/page đang hiển thị. Tự đóng nhận trong cùng TX khi còn open; còn blocker không đóng nhận. Marker actor/time/hash và history/audit atomic, bất biến.
2. Hồ sơ V1 chưa chốt không vào inbox bản cứng, không tạo/sửa/xác nhận/revision XET hoặc xác nhận bản cứng qua URL/API trực tiếp. Guard HS/BS sau chốt online; XET tiếp tục tới DOT08. Giữ receiving-round boundary và legacy closure, không bịa ngày mới.
3. DOT08 giữ cơ chế Đóng xét hiện có, thêm scope kỳ/đợt và prerequisite online seal. Sau chốt bản cứng giữ readonly, Chờ Hội đồng cho nguồn đạt; không tạo QĐ/PDF tự động. Chưa triển khai P12/P13.
4. Hai inbox có panel cùng server context; bản cứng thêm lọc kỳ/đợt, bỏ chọn quyền hưởng/lookup nâng cao ở inbox. Giữ nghiệp vụ quyền hưởng riêng. Chỉ có CTA chốt khi chọn đúng một kỳ/đợt phù hợp. Modal nêu khóa dữ liệu và đóng nhận nếu còn open. Excel sau chốt là disabled/chưa triển khai.
5. Sửa bug được browser chứng minh: nút Xem kết quả đối chiếu bị disabled sau chốt. Nay đọc head hiện có trước guard tạo mới, mở readonly; không có nút duyệt/từ chối/revision sau đóng xét.

### Source files của delta

- Configuration: MghpController.java, RoundTimelineService.java, RoundTimelineRepository.java, ConfigurationMapper.java, IntakeConfiguration.java; Entity MghpDotXet/MghpLichSuLichTrinh.
- Application: ApplicationController/Reads/Workflow/Service, SupplementService, DocumentReviewService, EvaluationService, ReviewSourceAccess. Không sửa Auth/Foundation/Shared hoặc module người khác.
- Migration duy nhất V26__mghp_online_stage_handoff.sql; V1–V25 giữ nguyên.
- FE: RoundStagePanel.tsx/module.css mới; IntakeWorkspace, StaffDossierReview, SupplementWorkspace, dossier-types; ReviewWorkspace/model; RoundTimelinePanel chuyển thao tác chốt về đúng inbox, tránh UI ghi trùng.
- Tests: StageHandoffRulesTest, StageHandoffDatabase, StageHandoffHttpIT, stage-handoff.test.mjs; review-lifecycle fixture thêm intakeSealed; e2e/stage-handoff-dev.cjs.
- Tài liệu ctsv: plan này, persistence-entity-inventory, review-lifecycle-implementation-plan. Nghiepvu: owner register, nghiệp vụ chi tiết, logic/dữ liệu,4HS/BS/XET/DOT contracts, API Catalog/readiness,3bộ ca round/submission/review, fixtures, Master Backlog/Roadmap. Không đổi task count/ID hoặc nghiệm thu lịch sử.

### Kiểm thử thực chạy

| Mức | PASS | FAIL cuối | SKIP | Phạm vi |
|---|---:|---:|---:|---|
| Unit |22|0|0|Handoff/lifecycle/evaluation/disposition/supplement/round/timeline rules P05–P08|
| HTTPS/JWT/JPA/SQL Server |14|0|0|STAGE01–14: scope, permissions, immutable, rollback/audit fault, concurrent seal, stale source, legacy, pagination, P10 create/meeting/lock regression|
| FE Node tests |25|0|0|stage/review-lifecycle/review-flow/dossier/p1-review-context; không phải browser|
| Browser |13 assertions/case groups|0 còn tồn tại|0|React/Spring/ctsv_dev thật,3tài khoản CV/SV/AuthAdmin; có desktop/mobile/refresh/error/quyền và2lượt kết luận bản cứng|
| Build/typecheck |PASS|0|—|tsc --noEmit + Vite, launcher build/start và Hibernate validate|

DB integration duy nhất `ctsv_stage_handoff_test_20261125`, DEV2022, exact owner/marker guard. DB được tạo mới, chạy V1–V25 ở RED rồi nâng V26 ở GREEN và Flyway validate; không clean/reset/drop hoặc repair. Fixture HTTP là metadata SQL, không nhận là upload/storage REAL. Browser dùng toàn bộ ctsv_dev qua launcher;4PDF thật đã có được render, không upload hoặc gọi CA mới. Test P10 trên DB riêng chỉ kiểm tương thích nguồn chốt, không triển khai P10 thêm.

Lệnh backend thực chạy (cwd ctsv; P00_SQL_PORT từ registry và native auth DLL trong PATH):

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=pbl6.ctsv.mghp.application.StageHandoffRulesTest,pbl6.ctsv.mghp.application.ReviewLifecycleRulesTest,pbl6.ctsv.mghp.application.EvaluationRulesTest,pbl6.ctsv.mghp.application.ApplicationDispositionRulesTest,pbl6.ctsv.mghp.application.SupplementRulesTest,pbl6.ctsv.mghp.configuration.RoundRulesTest,pbl6.ctsv.mghp.configuration.TimelineRulesTest' '-Dstage.handoff.test.enabled=true' '-Dit.test=pbl6.ctsv.auth.StageHandoffHttpIT' test failsafe:integration-test failsafe:verify
node --test frontend/tests/stage-handoff.test.mjs frontend/tests/review-lifecycle.test.mjs frontend/tests/review-flow.test.mjs frontend/tests/dossier.test.mjs frontend/tests/p1-review-context.test.mjs
npm.cmd --prefix frontend run build
```

Browser script chạy tương tác theo phase inspect → online → paper → security → readonly, giữ context và resume khi lỗi; env CTSV_PLAYWRIGHT_MODULE và NODE_EXTRA_CA_CERTS trỏ dependency/chứng thư local. Chỉ phase lỗi được chạy lại; không chạy lại chốt hoặc upload khi kết quả đã commit. STAGE_START/STAGE_ATTACH phục hồi lượt theo manifest/DB hiện có, token/credentials chỉ RAM. Không storageState/trace chứa token.

RED đã quan sát: Unit chưa chốt vẫn evaluationAllowed=true; API mới404;3FE cases thiếu gate/URL; browser nút readonly bị disabled. GREEN sau sửa như bảng. Lỗi script đã xử lý: double navigation làm ngắt refresh; option cần attached (không visible); observer đọc body đã bị navigation hủy; locator Giấy đã nộp phải theo role/accessible name. Một lần sửa nhãn qua Vite làm reload mất form chưa lưu trong lúc test, đã mở tiếp draft và chạy lại đúng phần. Không đổi Auth/force-click/expected để che lỗi. Lỗi tạo fixture do khóa cấu hình rollback; sửa thứ tự draft→policy binding→closed, không tắt trigger.

### DEV và dữ liệu

- Backup COPY_ONLY/CHECKSUM + RESTORE VERIFYONLY trước migrate: `D:\DevTools\SQL2022Data\MSSQL16.DEV2022\MSSQL\Backup\ctsv_dev_pre_stage_handoff_20261125_105811_352ed1.bak`.
- `dev.ps1 stop → migrate → build → start`, DEV V26 applied một lần, Flyway/Entity validate và launcher READY. Backend16612/frontend26048 tại hậu kiểm, FE5173/BE8443, ctsv_dev/DEV2022.
- Không sửa/xóa10hồ sơ hoặc đợt20040 của owner. Tạo đúng đợt SQL fixture closed20051 `HAND25-DEV-20261125`,2hồ sơ10042/10043 bằng bản sao dữ liệu synthetic, dùng lại4asset PDF20048–20051; không tạo tài khoản/quá trình/chính sách hoặc upload mới. Không tạo thêm đợt open cạnh20040.
- Browser duyệt V1 hai hồ sơ mới, chốt online, hồ sơ10042 đạt V2→Chờ Hội đồng;10043 từ chối bản cứng terminal; chốt bản cứng, không tạo QĐ/PDF/P11 request trên DEV. Fixture được giữ để xem readonly và Excel placeholder; đợt20040 vẫn mở/chưa chốt vì còn hồ sơ cần xử lý.
- Hậu kiểm12table slices ngoài fixture giữ nguyên count/SHA so với snapshot trước seed; gồm10root/13submission/34paper/34file link/1evaluation cũ,6lượt gọi ký,64file và5document version. Không API CA/storage DELETE, không sửa trigger định nghĩa hoặc tạm tắt trigger.
- Giới hạn chứng cứ: script snapshot ban đầu lỗi xử lý mảng, snapshot thành công được lấy **sau migrate, trước seed**. Vì vậy không tuyên bố có hash SQL trước/sau migration. Backup VERIFYONLY đạt, V26 đã review chỉ ADD schema/constraints/trigger (không DML dữ liệu cũ), và12slice trước/sau fixture/browser được đối chiếu thật.

### Evidence và giới hạn

[Backend log](../target/stage-handoff-final-backend.log), [frontend tests](../target/stage-handoff-final-frontend-tests.log), [build](../target/stage-handoff-final-frontend-build.log), [browser13cases](../target/stage-handoff-20261125/browser.json), [fixture](../target/stage-handoff-20261125/dev-fixture.json), [dữ liệu cũ giữ nguyên](../target/stage-handoff-20261125/existing-data-preserved.json), [Flyway](../target/stage-handoff-20261125/migration-preservation.json), [secret review](../target/stage-handoff-20261125/secret-review.json).

Ảnh đã xem:01online blockers/02mobile/03online ready/04paper inbox/05paper ready/07readonly. Ảnh06 chụp lúc context đang tải sau refresh, không dùng nó để chứng minh panel đã chốt; UI09/10 asserts và DB là evidence trạng thái. Không lấy screenshot riêng lẻ làm bằng chứng ghi thành công.

Không chạy toàn bộ P00–P11, toàn frontend suite, các harness lịch sử DecisionHttpIT/TimelineHttpIT hoặc Playwright signing; không suy PASS toàn phase. Các harness lịch sử dùng wire/setup trước delta cần dùng semesterId + bước chốt online khi chạy lại, không đem chúng ghi lên DB evidence cũ. Chưa triển khai export Excel/P12/P13, không ký CA hoặc tự tạo quyền hưởng, không đổi Auth/SHCN/DRL/Học bổng. G8 WAIVED BY OWNER theo phạm vi đã duyệt, không ghi G8 PASS. Không còn blocker kỹ thuật đã biết trong delta được kiểm ở bảng; không thay nghiệm thu owner hoặc tự mở phase.

Không commit/push/merge/deploy. Các thay đổi token/PDF/round-sequence/bộ lọc có sẵn trước task được giữ nguyên, không tính vào kết quả delta này.
