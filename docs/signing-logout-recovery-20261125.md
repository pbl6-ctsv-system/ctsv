# Ký nền sau đăng xuất và phục hồi DEV — 25/11/2026

Owner duyệt: lượt ký đã được tiếp nhận không bị hủy chỉ vì đăng xuất; vẫn chặn trước dispatch khi tài khoản mất hoạt động/quyền CTSV/quyền MGHP/ủy quyền hoặc PDF/bước không còn hợp lệ. Chỉ worker dùng kiểm tra account hiện hành; HTTP vẫn kiểm phiên/JWT tokenVersion như cũ. Giữ nguyên tokenVersion lúc xác nhận làm evidence, không sửa nó thành phiên mới.

## Kế hoạch có trọng tâm

- [x] RED/GREEN Unit Auth: khác tokenVersion được phép cho accepted work, HTTP vẫn401; khóa account/mất CTSV vẫn chặn.
- [x] Sửa boundary nội bộ AuthAccess/AuthService; SigningAccess thêm actor cho worker; SigningWorker chỉ đổi check ở dispatch. Không migration, không sửa FE hoặc CA adapter.
- [x] SQL/HTTPS riêng dùng DB stage handoff đã cấp và đúng marker, không migrate DB P11 cũ đang giữ số V24. CA/storage stub, chỉ kiểm các ca logout, quyền bị thu hồi, unknown và chống dispatch trùng.
- [x] Đồng bộ signing contract/design theo quyết định mới; build DEV và launcher.
- [x] Đọc lại request6/attempt10008: lỗi AUTH trước dispatch, không receipt/output/CA document và không unknown; đã dùng KY04 phục hồi có ETag/audit. Không sửa SQL tùy tiện, không enqueue lại và không gọi CA. Giữ output biên bản request5.
- [x] Ghi evidence thật, giới hạn; không commit/push.

## Code và tài liệu thay đổi

- `src/main/java/pbl6/ctsv/auth/AuthAccess.java`: public boundary nội bộ `requireAcceptedWorkAccess`, không endpoint mới; default giữ check nghiêm ngặt cho implementation chưa hỗ trợ.
- `src/main/java/pbl6/ctsv/auth/AuthService.java`: accepted work kiểm trạng thái account/ctsvAccess, không so tokenVersion cũ; HTTP dùng `requireCtsvAccess` vẫn so phiên như cũ.
- `src/main/java/pbl6/ctsv/mghp/application/SigningAccess.java`: `acceptedActor` kiểm Auth account hiện hành rồi resolve binding Foundation còn hoạt động; không bỏ Auth để chỉ gọi `identify(false)`.
- `src/main/java/pbl6/ctsv/mghp/application/SigningWorker.java`: chỉ worker dispatch dùng acceptedActor. Giữ role/assignment/ủy quyền/PDF/hash/step/BB-before-QĐ, lease, unknown guard và phục hồi receipt/output.
- Test mới `AuthAcceptedWorkAccessTest.java`, `SigningLogoutHttpIT.java`; test hiện hữu `SigningHttpIT.workerKeepsAcceptedConsentAfterSessionVersionChanges` đổi expected theo quyết định owner, không nới check role/account hoặc unknown.
- Tài liệu nguồn đồng bộ: `nghiepvu/01-thiet-ke/ctsv/api-contracts/signing-api.md` §10; `09-trien-khai/p11-signing-design.md` §5; `p11-signing-implementation-plan.md` delta25/11; `08-kiem-thu/ctsv/signing-test-cases.md` delta accepted work. Báo cáo thực thi P11 lịch sử không bị sửa thành kết quả mới.

## Kiểm thử thực chạy

CWD: `D:\7rd_semester\pbl6_src\ctsv`. Java21/Boot3.5.16. Lệnh SQL đặt `P00_SQL_PORT` theo registry DEV2022 và DLL auth12.10.2.x64 trong PATH; credentials test synthetic, không dùng CA thật.

1. `./mvnw.cmd -o -q -Dtest=AuthAcceptedWorkAccessTest,SigningRulesTest test`: **10 PASS / 0 fail/error/skip**. Lần RED thực chạy5ca:3PASS/1FAIL/1ERROR do còn check tokenVersion; GREEN5Auth+5Rules.
2. `./mvnw.cmd -o -q -Dp11.test.enabled=true -Dstage.handoff.test.enabled=true -Dit.test=SigningLogoutHttpIT#<method-list> test-compile failsafe:integration-test failsafe:verify`: **9 ca riêng biệt đạt**, gồm:
   - Logout thật qua AuthService sau nhận202, JWT cũ401 nhưng worker lưu output/chờ người sau.
   - Account khóa, mất ctsvAccess, mất role MGHP: không upload/sign.
   - Checkpoint uploaded sau thay phiên tiếp tục ký, không upload lại.
   - Thu hồi authority và expired sign intent: không dispatch/unknown.
   - Hai worker: chỉ một provider sign; người ngoài assignment không đọc file.
   - Lỗi chứng thư trước dispatch: phục hồi phải consent mới.
   - Phó phòng phục hồi rồi ACK cùng PDF lần nữa: không lỗi duplicate, chỉ một sign mới sau xác nhận.

   DB thật `ctsv_stage_handoff_test_20261125` / `localhost\DEV2022`, owner/marker guard `ctsv-mghp-stage-handoff-20261125`; Flyway V26 validate, không thay migration. HTTPS/Auth/JPA/SQL và crypto test thật; CA/storage **STUB**, worker được gọi có kiểm soát trong harness, không CA REAL. Không ghi DB evidence P11 V24. Cách tổng hợp: lượt8ca đầu có7PASS và1ERROR do fixture khóa account thiếu tăng PhienBanToken, trigger51101 chặn đúng. Sửa fixture theo V6/AuthAdministration (khóa và tăng generation nguyên tử), chạy lại đúng ca lỗi đạt1/1. Ca PP retry bổ sung đạt1/1. Không bỏ trigger/assertion hoặc đếm lần0test là PASS.

   Evidence ignored: `target/signing-logout-http-first.xml`, `signing-logout-http-disabled-retest.xml`, `failsafe-reports/TEST-pbl6.ctsv.auth.SigningLogoutHttpIT.xml` (ca PP cuối), `signing-logout-http.log`, `signing-logout-http-retest.log`, `signing-logout-pp-recovery.log`; Unit `surefire-reports` và `signing-logout-unit.log`.
3. DEV: không active job trước restart; `./dev.ps1 stop` → `build` → `start` **PASS**. Không migration. Launcher READY HTTPS5173/8443, backend8276/frontend27580. Source/JAR theo launcher, DB vẫnV26.
4. Browser read-only PP trên DEV: **2 assertion PASS**, 0pageerror, 0POST nghiệp vụ. GET request6 HTTP200/statusprepared/phaseawaiting_consent, allowedActions có sign; thấy nút Ký số và hướng dẫn chọn vùng mới, PDF hiện nội dung. Đã xem ảnh `target/signing-logout-recovery/pp-ready-to-sign.png`; evidence `browser.json`. Nút ký chỉ sáng sau chọn vùng, như hành vi hiện có; không nhấn xác nhận ký thật.
5. `git diff --check` cho các source/test liên quan **PASS**, chỉ cảnh báo LF→CRLF. Kiểm mẫu secret7source/test không thấy secret. JWT/password/refresh chỉ trong RAM, không ghi trace/storageState/log.

## DEV đã phục hồi thế nào

Đích `ctsv_dev` có đúng owner marker `ctsv-local-dev-v1`. Request6 là **Quyết định xét MGHP · abcded**, không phải biên bản5 (đã completed). Guard trước phục hồi: requestfailed/errorSIGNING_AUTHORIZATION_REVOKED, attempt10008 failed, không lease/unknown/CA document/receipt/output, chỉ1attempt.

Dùng specialist hiện hành, đọc KY03 lấy ETag/allowedAction rồi POST `/api/ctsv/yeu-cau-ky/6/doi-soat` với lý do owner. HTTP202:

| Dữ liệu | Trước | Sau |
|---|---|---|
| Request6 | failed / SIGNING_AUTHORIZATION_REVOKED | prepared / errorNULL |
| Attempt10008 | phasefailed | phaseawaiting_consent; vẫn giữ historicalErrorSIGNING_AUTHORIZATION_REVOKED |
| Step11 Phó phòng | failed, vùng ký cũ | waiting; xóa vùng cũ để chọn/xác nhận lại |
| Attempt mới | Không | Không |
| Receipt/output request6 | 0/0 | 0/0 |
| Biên bản5 | completed,2PDF | Giữ đúng2link/file/receipt cũ (20059/20060) |

Có audit `SIGNING_RECOVER` ghi lý do; không sửa tokenVersion cũ, không UPDATE SQL tùy tiện, không xóa/repair history, không auto enqueue hoặc CA sign. Snapshot trước/sau `target/signing-logout-recovery/before.json`, `after.json`; script phục hồi guard nằm trong target ignored, không có secret. Đây là snapshot hẹp, không phải full DB backup; thao tác nghiệp vụ đi qua API/CAS/audit, không phá dữ liệu.

Người dùng đăng nhập Phó phòng, mở `/mghp/ky-so?yeu-cau=6` → đọc lại → chọn vùng ký → tự xác nhận ký. Khi đã nhận202 có thể logout/chuyển chức năng; người sau vẫn phải chủ động ký bước mình.

## Giới hạn

Không gửi thêm chữ ký CA thật trong lượt này. Không kiểm lại toàn bộ P11/FE/Playwright; không sửa UI, JWT/refresh/password/CSRF/CORS hay migration. Token/phiên bị thu hồi đơn thuần không hủy consent đã commit; muốn chặn dispatch cần khóa account hoặc thu hồi quyền/ủy quyền theo nghiệp vụ hiện hành. Lượt unknown vẫn không được phục hồi thành ký lại. Không commit/push/merge.
