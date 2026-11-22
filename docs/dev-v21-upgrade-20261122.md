# DEV đồng bộ V21 — 22/11/2026

**Kết quả: hoàn tất cập nhật `ctsv_dev`, launcher READY.** Owner yêu cầu cập nhật DB DEV đang dùng và kiểm thử sau khi source đã merge/push. Không tạo lại DB, không reset/drop/clean, không nạp fixture test hoặc thay password/account. Không sửa source nghiệp vụ/migration, không commit/push trong lượt bảo trì DB này.

## Nguyên nhân và cách xử lý

DEV trước bảo trì đã áp V17 timeline và V18 retire guidance riêng. Source hợp nhất dùng V17 DRL, V18 SHCN, V19 Học bổng; hai SQL riêng được đổi tên thành V20/V21 và giữ nguyên bytes. Vì vậy chạy migrate thông thường ngay từ đầu sẽ gặp checksum/script mismatch.

Đã kiểm trước ghi:

- Đúng `DESKTOP-7PPHP5B\DEV2022 / ctsv_dev`, Windows owner hiện hành, marker `ctsv-local-dev-v1`, compatibility160/collation đúng.
- V1–V16 trong history khớp chuỗi chuẩn.
- Hai checksum DEV cũ khớp SQL V20/V21 và history DB merge riêng đã kiểm; schema đợt/lịch sử timeline hiện có khớp schema chuẩn. Các bảng DRL/SHCN/Học bổng của V17–V19 chưa tồn tại.

Quy trình bảo trì một lần:

1. `dev.ps1 stop` dừng đúng tiến trình DEV.
2. Backup SQL Server `COPY_ONLY,CHECKSUM`; `RESTORE VERIFYONLY ... WITH CHECKSUM` đạt.
3. Lưu history cũ và hash từng dòng/cột gốc của120 bảng hiện có. Không lưu plaintext password/token vào evidence.
4. Transaction có khóa bảo trì đổi **đúng2 trường `version/script` của2 row**: V17→V20, V18→V21. Giữ nguyên checksum, installed_rank, installed_on, installed_by, execution_time, description và success; không xóa/insert giả history.
5. Flyway `outOfOrder=true` chỉ trong helper bảo trì lần này, thực thi thật V17/V18/V19 của main. Không sửa global launcher/Flyway configuration, không chạy repair hoặc bỏ validation.
6. Validate lại bằng cấu hình Flyway bình thường:21 migration hợp lệ,0 pending. So sánh hash dữ liệu cũ PASS; cho phép thêm role/permission/audit từ SQL main, không cấp role mới cho account DEV.
7. Chạy `dev.ps1 migrate` theo quy trình chuẩn: build JAR, validation PASS, current version21,0 migration cần chạy; không seed/reset accounts.
8. `dev.ps1 start` READY với Hibernate `ddl-auto=validate` và JAR source hợp nhất.

Một lần đầu helper dừng ở application lock vì transaction chưa được kích hoạt; đã rollback và SELECT xác nhận history cũ nguyên trạng. Chỉ sau sửa helper và check lại mới chạy thành công; không gửi lặp migration mù quáng.

## History sau bảo trì

| installed_rank thực tế | Version chuẩn | Script | Checksum |
|---|---|---|---|
|19|17|V17__drl_schema.sql|1459303086|
|20|18|V18__shcn_draft_round.sql|-1935633692|
|21|19|V19__hoc_bong_round_scope.sql|2101540825|
|17|20|V20__mghp_round_timeline.sql|-773093967|
|18|21|V21__mghp_retire_round_guidance.sql|-1109024481|

Thứ tự installed_rank giữ dấu vết thực thi, không ép lại thành thứ tự version. Dòng Flyway khi áp ba migration out-of-order ghi cuối V19; lần migrate chuẩn tiếp theo xác nhận current21 và up-to-date. Không chạy lại SQL V20/V21 vì chúng đã được thực thi trước đổi số. **Không sao chép thao tác đổi history này sang máy khác** nếu chưa kiểm đúng old scripts/checksums/schema và backup.

## Kiểm chứng

| Kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| Backup/VERIFYONLY |PASS|SQL Server REAL, COPY_ONLY, checksum|
| Bảo toàn dữ liệu120 bảng |PASS|So hash dòng qua cột gốc trước/sau migration; chỉ thêm catalog/audit theo SQL|
| Flyway chuẩn |PASS|21 success, maxVersion21,0 failed/pending; không repair|
| `dev.ps1 build/migrate/start/status` |PASS|Executable JAR mới; BE/FE chạy; Hibernate validation đạt|
| HTTP smoke `frontend/e2e/p1-read-http-dev.ps1` |20 PASS|Auth/read/security/query guard; BE/SQL REAL ctsv_dev|
| Playwright MCP |9 PASS|3 login→home; hồ sơ10028; account dev.sinhvien; timeline; refresh giữ phiên; SV bị chặn URL admin; xem/tải PDF|
| SHA-256 PDF |MATCH|Cloudinary authenticated REAL; backend download file10032 trùng PDF owner|
| Page JS errors / HTTP5xx trong smoke |0 /0|Theo listener của3 browser contexts trong lượt này|

Một lỗi locator smoke: kỳ vọng vẫn ở chi tiết đợt sau reload, trong khi màn hình hiện hành giữ selection trong memory nên quay về danh sách. Đã giữ context và sửa ca kiểm thành xác nhận phiên/list; không sửa ứng dụng hoặc nhận ca cũ là PASS. Không phát hiện lỗi mới do chuyển DB trong phạm vi smoke.

Không chạy lại toàn bộ P00–P09 hoặc E2E rộng.4 lỗi navigation và contextLoads đã được owner cho bỏ qua trong lượt Git trước vẫn giữ nguyên; smoke thành công không biến các lỗi đó thành PASS. Quyền hưởng DEV vẫn rỗng; không tạo quyền hoặc sửa hồ sơ xác nhận để lấp dữ liệu test.

## Dữ liệu, launcher và evidence

- Giữ toàn bộ dữ liệu/tài khoản cũ; hồ sơ sinh viên10024/10025/10027/10028/10029 vẫn có trong danh sách khi smoke. Không tạo hồ sơ mới, không upload lại PDF. HTTP/browser login tạo token/audit đăng nhập theo hành vi Auth thông thường; bằng chứng bảo toàn migration được chụp trước lượt login này.
- URL: `https://127.0.0.1:5173`, backend `https://127.0.0.1:8443`. Launcher cuối BE pid27372, FE pid12948; PID có thể thay ở lần chạy sau.
- Backup riêng ngoài Git: `D:\DevTools\SQL2022Data\MSSQL16.DEV2022\MSSQL\Backup\ctsv_dev_pre_v21_20261122_141537_efb8c2.bak`. Chưa thực hiện restore; chỉ VERIFYONLY.
- Evidence local ignored `target/dev-v21-upgrade-20261122/`: backup.json, history-before/after.json, data-before/after.json (hash), preflight.log, upgrade.log, http-results.json, browser-results.json, pdf-hash.json, download.pdf, student-after.png.
- Helpers bảo trì cục bộ `target/DevV21Upgrade.java`, `target/dev-v21-protect.ps1` không thuộc runtime hoặc Git. Guard ngăn chạy apply lần nữa nếu history không còn đúng18 row cũ. Không dùng helper thay daily migrate.

Từ đây dùng `dev.ps1 start` hằng ngày; khi source có migration mới, stop→migrate→start theo runbook. Cấu hình out-of-order đặc biệt không được lưu vào launcher. Không tự chuyển sang P10 hay phase mới.
