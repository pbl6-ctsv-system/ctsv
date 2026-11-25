# MGHP — mở đợt tuần tự và dọn fixture DEV, 25/11/2026

## Quyết định và phạm vi

Owner xác nhận các đợt MGHP chạy tuần tự trên toàn hệ thống, kể cả qua học kỳ. Một kỳ được có nhiều đợt; bản nháp chuẩn bị trước không chặn. DOT03 chỉ mở một nháp nếu mọi đợt đã công khai khác đều `completed`; còn `open` hoặc `closed` trả409 `ROUND_PREVIOUS_NOT_COMPLETED`. Đợt lịch sử vẫn đọc theo quyền. Công khai trước opensAt vẫn được phép khi đủ readiness.

DOT05 giữ nguyên chờ predicate P12/P13. Đóng nhận, Đóng xét và ký xong không thay completed. Owner chấp nhận áp guard ngay; không triển khai P12/P13 hoặc tự đổi trạng thái các đợt cũ để vượt guard. [Owner register](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#round-sequence-20261125).

## Thay đổi

- [RoundService](../src/main/java/pbl6/ctsv/mghp/configuration/RoundService.java): kiểm tập đợt trước readiness/mutation khi draft→open. Idempotency và kiểm ETag vẫn giữ.
- [ConfigurationRepository](../src/main/java/pbl6/ctsv/mghp/configuration/ConfigurationRepository.java): truy vấn toàn MGHP, không lọc semester/ID/ngày tạo; `UPDLOCK,HOLDLOCK` trong transaction đã có `ctsv.mghp.configuration` application lock. Không thêm migration/index/DTO/endpoint.
- [ConfigurationWorkspace](../frontend/src/features/mghp/configuration/ConfigurationWorkspace.tsx): thông báo lỗi dễ hiểu, chỉ rõ có thể đang vướng đợt ở học kỳ khác; giữ draft và đường đọc lại.
- Tests mới: `RoundSequenceTest`, `RoundSequenceDatabase`, `RoundSequenceHttpIT`, [browser DEV](../frontend/e2e/round-sequence-dev.cjs). Helper DB chỉ cho đúng instance/database/owner/marker đã được owner cấp phép; không fallback DEV/evidence/clean/reset.
- Đồng bộ nguồn: `nghiepvu`/Owner register, `nghiep-vu-mghp-chi-tiet.md`, `logic-va-du-lieu-he-thong.md`, `mghp-round-api.md`, `api-catalog.md`, `round-test-cases.md`, `fixtures.md`. Ca mới DOT-SEQ01–08; không thay nghiệm thu lịch sử.

## Dữ liệu DEV

Backup trước dọn: `D:\DevTools\SQL2022Data\MSSQL16.DEV2022\MSSQL\Backup\ctsv_dev_pre_round_sequence_20261125_090737_40c8ba.bak`; COPY_ONLY/CHECKSUM và RESTORE VERIFYONLY đạt. Không reset/drop/schema/migration/repair.

Owner cho xóa5đợt `20041,20042,20043,20044,20046`, gồm các bộ P10/P11 thử nghiệm, và hồ sơ/phụ thuộc MGHP. Đã xóa7root `10030–10036` cùng dữ liệu con: tổng214bản ghi trong22bảng MGHP. Dọn thêm4thông báo công khai của chính5đợt cùng4người nhận để không để link đợt đã xóa; hash thông báo còn lại khớp.

Kế hoạch chạy thử toàn bộ trong transaction rồi rollback trước lần commit. Chỉ tạm tắt34trigger trên các bảng MGHP bị dọn; kiểm/khôi phục nguyên trạng và hash định nghĩa trước commit, không sửa định nghĩa/FK. Không mở API xóa hoặc thay logic backend để dọn dữ liệu.

41tập dữ liệu ngoài scope được kiểm số dòng và SHA-256 trước/sau, kiểm lại sau restart/browser. Giữ nguyên tài khoản, người dùng/SV, học kỳ, chính sách, đợt20040 và5hồ sơ của nó, kho văn bản dùng chung và metadata tệp. **6lượt ký,6biên nhận,6kết quả PDF ký khớp hash dữ liệu SQL**. Không gọi API xóa cloud hoặc CA; PDF cloud giữ nguyên. Dữ liệu MGHP P10 đã dọn không còn trong giao diện MGHP; lịch sử ký chung/bytes vẫn được giữ theo phép owner.

Fixture DEV mới:

| ID | Mã | Học kỳ | Trạng thái |
|---|---|---|---|
| 20047 | SEQ25-DEV-HK1-A | 1 | draft |
| 20048 | SEQ25-DEV-HK1-B | 1 | draft |
| 20049 | SEQ25-DEV-HK4-A | 4 | draft |

Chỉ tạo ba nháp, sao chép lựa chọn chính sách hợp lệ của đợt20040; không thêm hồ sơ/PDF/quyết định giả. Đợt20040 `UX-INTAKE-1795271503790-3` vẫn open, nên ba nháp **phải bị chặn công khai**. Không tự đóng/hoàn tất đợt này vì nằm ngoài5đợt được phép xóa. Fixture được đánh dấu SYNTHETIC, chạy lại seed không nhân đôi.

## Kiểm thử thực tế

CWD backend `D:\7rd_semester\pbl6_src\ctsv`; frontend ở `frontend`. Chỉ kiểm các hành vi bị tác động; không chạy toàn bộ MGHP/Playwright/ký/PDF. G0/G3/G4/G5 và phần concurrency G2 được kiểm theo phạm vi; G7 chỉ đổi câu lỗi và kiểm browser. Không có migration mới; không tuyên bố nghiệm thu P12/P13 hay G8 độc lập.

| Lệnh/nhóm | Kết quả cuối | Thật / giả lập |
|---|---|---|
| `mvnw.cmd -o -B -ntp -Dtest=pbl6.ctsv.mghp.configuration.RoundSequenceTest,pbl6.ctsv.mghp.configuration.RoundRulesTest,pbl6.ctsv.mghp.configuration.RoundFormRulesTest test` | 9 PASS,0fail/error/skip | Unit; repository/JDBC test double |
| `mvnw.cmd -o -B -ntp -Dround.sequence.test.enabled=true -Dit.test=pbl6.ctsv.auth.RoundSequenceHttpIT test-compile failsafe:integration-test failsafe:verify` | 9 PASS,0fail/error/skip | HTTPS/JWT/Auth/JPA/SQL Server REAL; DB `ctsv_round_sequence_test_20261125` |
| `npm.cmd run build` | PASS: `tsc --noEmit` + Vite | Build frontend thực tế |
| `dev.ps1 build` → `dev.ps1 start` | READY | Backend/FE thật, `localhost\DEV2022 / ctsv_dev`; không migrate |
| Browser CV desktop, cùng kỳ | PASS:409, toast hiện, refresh vẫn draft | React/Spring/SQL DEV REAL |
| Browser CV mobile, khác kỳ | PASS:409, toast hiện, refresh vẫn draft | React/Spring/SQL DEV REAL |

HTTPS/SQL kiểm: nháp không chặn; open/closed cùng/khác kỳ chặn và không đổi tag/thông báo/audit; mọi đợt trước completed cho mở cùng hoặc kỳ tiếp; hai lệnh mở khác kỳ đồng thời chính xác một200/một409 và một thông báo/audit; SV/Admin Auth403, không token401, thiếu tag428/stale412; DOT05 vẫn409.

Trạng thái completed trong DB test được SQL **mô phỏng chỉ trên fixture SEQ25 thuộc DB riêng**, để kiểm oracle mở đợt tiếp theo. Không gọi đó là bằng chứng DOT05 hoạt động trên DEV. Clock/khóa/HTTP/DB trong bài kiểm đều thực, không stub API nghiệp vụ. Không cần CA/Cloudinary calls cho delta này.

TDD: test mới ban đầu đỏ vì open không bị chặn; sau sửa xanh. Lượt sandbox Unit bị kẹt công cụ Mockito và lượt HTTPS đầu thiếu DLL integrated-auth không được tính PASS; chạy lại đúng ca với môi trường local đã xác minh. Browser desktop đạt ngay; mobile lần đầu kiểm DOM quá sớm sau reload, đã giữ assertion và thêm chờ dòng render, chạy lại **riêng ca mobile** đạt; không sửa code/expected để che lỗi.

Evidence local tại `target/round-sequence-20261125/`: `backup.json`, `cleanup-audit.json`, `cleanup-dry-run.json`, `cleanup-result.json`, `cleanup-notices.json`, `dev-final.json`, `dev-fixtures.json`, `browser-failure.json` (có kết quả desktop đã hoàn tất), `browser-retry.json` (mobile), screenshot desktop/mobile. Log cuối: `target/round-sequence-unit-final.log`, `target/round-sequence-http-final.log`, `target/round-sequence-frontend-build.log`, XML Surefire/Failsafe. Không token/password/storageState trong evidence. Chưa commit/push/merge.
