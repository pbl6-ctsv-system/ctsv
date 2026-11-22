# Đơn giản hóa form đợt MGHP — 21/11/2026

Owner đã duyệt qua chat: ẩn lọc học kỳ trong form, gom lịch tiếp nhận và8planned trong một khung; planned vẫn nullable; bảng chọn nhiều M và phiên bản; ẩn hai mode cố định và ô schedule/ID tệp mẫu. Giữ lựa chọn tiếp nhận đề nghị kỳ trước và quy tắc backend. Bỏ declarationGuidance/lateSubmissionGuidance khỏi DB/API/Entity theo yêu cầu đã xác nhận; thông báo mở đợt dùng tên đợt, không phụ thuộc guidance. Không thay nghiệp vụ xét, Auth, navigation, P10.

Thực hiện tuần tự, TDD, không subagent/Git write:

- [x] RED: serializer không còn guidance, modes cố định,8planned nullable; browser tái hiện field schedule sai và form cũ.
- [x] FE: RoundPolicySelection cạnh ConfigurationWorkspace; bảng checkbox theo M, dropdown locked version, chọn hàng loạt; pending/version load lỗi chặn lưu, preserve template IDs và ETag. Khung lịch chung, không field object/generic, không lọc list trong form.
- [x] BE/DB: V18 mới, drop đúng2cột guidance sau kiểm dependency; đồng bộ Entity/mapper/insert/rules, thông báo và FE public/form context. Không sửa V1–V17. Contract/schema delta có owner approval rõ.
- [x] Targeted Java rules + FE tests/build; guard owner/marker/server ctsv_dev/V17, snapshot retained columns/rows/history. stop → migrate/build theo launcher → start. Không ghi DB evidence cũ.
- [x] Browser REAL DEV: multi-M/version, save nullable, refresh/edit giữ lựa chọn, publish/lock, error, quyền SV, mobile. SQL xác minh V18 và dữ liệu còn lại giữ nguyên. Ghi actual evidence, không mở P10/commit/push.

Wire mới bỏ hai key guidance, client cũ gửi chúng nhận400 theo strict shape; FE/BE triển khai đồng thời trong local DEV. Lịch sử/thông báo đã phát không sửa lại. Snapshot và migration chỉ xóa dữ liệu hai trường được owner yêu cầu, không reset DB/schema/account hoặc sửa các migration đã áp dụng.

## Kết quả

- Node RED3FAIL → GREEN3PASS; toàn frontend **27PASS,0FAIL/SKIP**, build `tsc --noEmit && vite build` PASS. Không nâng dependency.
- Java RED2FAIL → GREEN:9test MGHP liên quan PASS (RoundFormRules2, RoundRules2, TimelineRules5). Lệnh dùng tên RoundRulesTest còn khớp25test Học bổng, nên log tổng thực tế34PASS/0FAIL/ERROR/SKIP; không sửa module đó. Không chạy toàn suite P00–P08.
- **7flow Playwright PASS**, backend HTTPS/SQL Server REAL trên `ctsv_dev`: RF01 form/field ẩn; RF02 chọn nhiều M/nullable; RF03 refresh/sửa/persist; RF04 thiếu mốc409, nhập3mốc cũ rồi công khai200 với8planned vẫn null; RF05 mobile390px/logout; RF06 SV public/direct URL; RF07 chọn M chưa chọn phiên bản phải chặn lưu.0pageerror. Không dùng Cloudinary/CA/STUB vì task này không upload file.
- Browser RED thấy filter còn trong form. Lượt verify đầu qua tạo đợt nhưng dừng ở script tìm đợt nháp ngoài trang1; đã sửa script đi qua pagination, không đổi backend/expected. Sau đó6flow và7flow đều chạy thật PASS. Lượt bổ sung ảnh gặp Auth rate limit; script đã chờ đúng Retry-After, không reset/restart BE để lách. Kiểm tra mobile riêng PASS ở390px, chờ tải đủ phiên bản rồi chụp ảnh; không có pageerror hoặc tràn ngang. File verify-report.json là lượt bổ sung bị giới hạn đăng nhập, không phải báo cáo7flow PASS; dùng verify-attempt3.json để đối chiếu kết quả chức năng.
- SQL guard xác minh `DESKTOP-7PPHP5B\DEV2022`, `ctsv_dev`, owner `DESKTOP-7PPHP5B\USER`, marker `ctsv_dev_owner=ctsv-local-dev-v1`, TCP động56826. `before` V17 có2cột; `after` V18 có0cột. Digest dữ liệu giữ lại của round/binding/application/account/timeline history trùng nhau ngay trước/sau migration. Flyway V1–V17 version/checksum giữ nguyên; chỉ thêm V18. Hibernate validate và launcher start PASS. JAR18/18migration byte khớp source, build-source pointer khớp.
- Dữ liệu browser mới: đợt20031 nháp (lượt script dừng),20032/20033 open SYNTHETIC, học kỳ1, M1version8/M2version10012. Không xóa fixture hoặc dữ liệu cũ. Không phát sinh hồ sơ/PDF/P10. Không thay Auth/nav, thuật toán xét, SQL locks/CAS/timeline source guards.

## Evidence và lệnh

CWD repo CTSV. `node --test frontend/tests/round-form.test.mjs`; `npm --prefix frontend test`; `npm --prefix frontend run build`; `mvnw.cmd -Dtest=RoundFormRulesTest,TimelineRulesTest,RoundRulesTest test`; `pwsh -File frontend/e2e/round-form-db.ps1 before|after` chạy hai bước riêng ở hai phía migration; `dev.ps1 stop` → `dev.ps1 migrate` → `dev.ps1 start`; `pwsh -File frontend/e2e/run-round-form-dev.ps1 verify`.

[Test đỏ BE](../target/round-form-red.log), [test xanh BE](../target/round-form-green.log), [Playwright7flow](../target/round-form-dev-20261121/verify-attempt3.json), [mobile](../target/round-form-dev-20261121/mobile-report.json), [ảnh form mobile](../target/round-form-dev-20261121/mobile-create-form.png), [snapshot trước](../target/round-form-dev-20261121/db-before.json)/[sau](../target/round-form-dev-20261121/db-after.json), [JAR](../target/round-form-dev-20261121/jar.json), [manifest](../../nghiepvu/.analysis/runtime/round-form/20261121/manifest.json). Ảnh RF01…RF07 và trace đã lọc token/cookie tại target/round-form-dev-20261121; evidence này không stage/push runtime secrets. Ảnh chụp hỗ trợ kiểm tra bố cục; các assertion của từng flow nằm trong script, không xem ảnh riêng là bằng chứng nghiệp vụ PASS.

## File liên quan

- FE: ConfigurationWorkspace.tsx/CSS, RoundPolicySelection.tsx mới, model.mjs, StudentRoundDetails.tsx, DeclarationForm.tsx; không sửa AppShell/navigation/Auth.
- BE: RoundRules, ConfigurationEntities/Mapper/Repository, RoundService (notification content), Entity MghpDotXet và V18 mới.
- Test: round-form.test.mjs, RoundFormRulesTest.java, request builders P05HttpIT/TimelineHttpIT/TimelineRulesTest theo wire mới; round-form-dev.mjs/run-round-form-dev.ps1/round-form-db.ps1 mới.
- Tài liệu: plan này, persistence inventory, round contract/catalog/test cases, canonical ctsv_db.sql (schema hiện hành; không dùng thay migration V1), Backlog/Roadmap ghi delta; báo cáo extended timeline cũ được chú thích là baseline V17 trước thay đổi này. Không thay state nghiệm thu/P10 dependency.

Giới hạn: không chạy lại toàn24timeline E2E/7SQL barrier race hoặc toàn P05–P08 vì không đổi cơ chế nguồn/khóa/window. Trường hướng dẫn đã rút làm wire cũ không tương thích; cần dùng FE/BE source cùng phiên bản. Không khôi phục hai cột bằng sửa migration cũ khi rollback; muốn thay đổi tiếp cần migration mới.
