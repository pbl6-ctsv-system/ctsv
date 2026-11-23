# V24 SHCN / V25 P11 — reconciliation DEV ngày 23/11/2026

**Reconciliation: PASS trên bản sao trước, sau đó PASS trên `ctsv_dev`.** Owner đã duyệt chuyển định danh history có guard, áp riêng V24 SHCN bằng `outOfOrder` một lần và giữ nguyên dữ liệu ký. SHCN API nghiệp vụ trên DEV: **NOT VERIFIED**, vì cấu hình hiện hành chưa bật module và chưa cấp tài khoản/quyền SHCN; không đánh đồng kiểm SQL/MockMvc với HTTP thật.

## Backup và bản sao

- Đích xác minh: `localhost\DEV2022/ctsv_dev`, owner hiện hành, marker `ctsv-local-dev-v1`, compatibility 160, collation `Vietnamese_100_CI_AS_SC`.
- Backup trước thử nghiệm: `D:/DevTools/SQL2022Data/MSSQL16.DEV2022/MSSQL/Backup/ctsv_dev_pre_v25_20261123_153800_9c24cb.bak`.
- Restore thành DB mới `ctsv_v25_clone_20261123`, marker `ctsv-v25-reconciliation-20261123`; không REPLACE/DROP/RESET. Dữ liệu gốc DEV không bị sửa khi thử.
- Sau khi bản sao đạt, dừng launcher DEV và backup lần nữa: `D:/DevTools/SQL2022Data/MSSQL16.DEV2022/MSSQL/Backup/ctsv_dev_pre_v25_apply_20261123_155519_bf7318.bak`.
- Cả hai backup đều COPY_ONLY/CHECKSUM và RESTORE VERIFYONLY PASS. Bản sao được giữ lại, không tự drop.

## Source và history đã thực hiện

- Chỉ lấy migration SHCN từ `origin/main` commit `d26adf2ebb7476bb2392e3a3477c072ac2821272`: `V24__shcn_round_audiences.sql`, Git blob `d388ee7f2a312586ea79f11541bc935efe3b56d7`.
- P11 đổi tên thành `V25__mghp_signing_pipeline.sql`, không sửa SQL. SHA-256 trước/sau `1ea39bb5940f36ef62d5bb30957ea59a0b246203ab389cdf64fc8de17c9a6ee9`; resolved checksum V25 và checksum P11 đã áp dụng đều **-1373217696**.
- Guard trước ghi: chính xác 24 history row thành công, đúng script/checksum P11 cũ, table audience chưa có, đúng DB/owner/marker. Đối chiếu DEV với baseline bản sao: dữ liệu/schema của 134 bảng, 5 output P11 và 24 history row khớp. Thứ tự property JSON evidence khác giữa JVM không phải khác dữ liệu; so sánh giá trị/hash xác nhận khớp.
- Transaction có maintenance applock chỉ cập nhật `version` và `script` của history rank 24. Giữ nguyên checksum, description, actor, installed rank/time, execution time và success.
- Mỗi DB chỉ chạy thêm **1 migration V24 SHCN** với `outOfOrder=true` cục bộ. **Không chạy lại SQL P11**, không repair hoặc xóa history. Sau đó dùng Flyway instance bình thường `outOfOrder=false`, validate PASS, 25 version duy nhất, 0 pending.

| installed_rank | version | Script | Checksum | Success |
| --- | --- | --- | --- | --- |
| 22 | 22 | `V22__mghp_decision_snapshot.sql` | 1234436035 | true |
| 23 | 23 | `V23__mghp_decision_history_guards.sql` | 858788797 | true |
| 24 | 25 | `V25__mghp_signing_pipeline.sql` | -1373217696 | true |
| 25 | 24 | `V24__shcn_round_audiences.sql` | -1518338238 | true |

Đây là thứ tự thực thi thật, không giả rollback schema về V23 hay giả chạy P11 sau SHCN. Phiên bản lớn nhất hiện tại là V25 dù history row cuối được thực thi là V24 SHCN.

## Bảo toàn dữ liệu và schema

- Snapshot ngay trước/sau reconciliation trên cả clone và DEV: **134 bảng cũ giữ nguyên row count, SHA-256 dữ liệu và hash cấu trúc cột**. Chỉ thêm `shcn.shcn_doi_tuong_ap_dung`; không sửa dữ liệu nghiệp vụ/tài khoản.
- Giữ nguyên 4 request P11 (2 completed), 5 attempt/actor binding/phase/output và 5 receipt/PDF. Hash payload đối chiếu P11 trước/sau: `e3b8ed234b616eff7105d9380af42782786fecc9e8ab6a6a814e545c862a9aa3`.
- SQL schema assertion: **7/7 trên mỗi DB**: history duy nhất/thành công; 10 cột audience; 4 CHECK enabled/trusted; FK enabled/trusted; PK/unique; trigger enabled; 5 attempt/output/receipt khớp.
- SQL Server thật trên clone: **8/8** ca audience hợp lệ, duplicate target, sai loại đối tượng, sai hình thức, thiếu cặp ngày, ngược ngày, JSON không phải array và trigger chặn đợt khóa. Mỗi ca rollback transaction; 0 fixture row lưu lại, không thao tác dữ liệu DEV.
- Xóa đúng resource build lỗi thời `target/classes/db/migration/V24__mghp_signing_pipeline.sql` sau so hash với V25, không xóa source SQL. JAR mới chứa 25 migration, toàn bộ hash khớp source, có V24 SHCN/V25 P11 và không còn V24 P11.

## Build, launcher và kiểm thử

CWD của các lệnh: `D:/7rd_semester/pbl6_src/ctsv`. Java 21, Spring Boot 3.5.16, SQL Server DEV2022.

| Lệnh / evidence | Kết quả thực tế | Phạm vi |
| --- | --- | --- |
| `V25Reconcile ctsv_v25_clone_20261123 apply` | PASS; chỉ chạy V24 SHCN | SQL Server thật, bản sao từ backup |
| `V25Reconcile ctsv_dev apply` | PASS; cùng quy trình đã thử | SQL Server thật, DEV đang dừng |
| `schema-check.ps1 -Database <clone/dev>` | 7/7 mỗi DB | Read-only SQL metadata/data |
| `audience-smoke.ps1` | 8/8, rollback mọi fixture | SQL Server thật, clone |
| `.\dev.ps1 migrate` | BUILD SUCCESS, không còn migration pending | JAR DEV; không reset tài khoản |
| `.\dev.ps1 start` | READY; Hibernate validate/EntityManagerFactory thành công | BE `https://127.0.0.1:8443`, FE `https://127.0.0.1:5173`, DB `ctsv_dev` |
| `V25Reconcile ctsv_dev verify` | Validate PASS, 25 applied, 0 pending, outOfOrder=false | Flyway bình thường sau build/start |
| `http-smoke.ps1` | **26/26 assertion**, 0 fail | HTTP thật, JWT thật, Cloudinary thật; token chỉ RAM |
| Maven test tập trung, agent nạp lúc JVM khởi động | **63 test, 0 failure/error/skip** | 5 SigningRules + 15 DotHttp MockMvc + 27 DotRequests + 16 DotService; không giả HTTP/SQL thật |

Lệnh Maven đã đạt:

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=SigningRulesTest,DotRequestsTest,DotServiceTest,DotHttpTest' '-DargLine=-javaagent:C:/Users/USER/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar' test
```

Lượt Maven đầu trong sandbox **BLOCKED** tại Mockito attach: Windows báo `could not open (5) pipe ... operation load`. Đã kết thúc đúng process test/diagnostic, giữ log và chạy lại đúng bộ trên ngoài sandbox với agent nạp sẵn; không sửa test, expected hoặc code để qua lỗi.

HTTP smoke kiểm: anonymous 401; đăng nhập chuyên viên/sinh viên/admin; danh sách ký; request 1/2 vẫn completed, request 4 vẫn waiting_next; sinh viên/Admin Auth không đọc được request/PDF ký. **5 PDF ID 10040, 10042, 10043, 10044, 10047 tải từ kho CTSV qua backend đều đúng PDF, size và SHA-256 gốc**. Không upload hoặc gọi ký CA mới.

## Giới hạn và phạm vi giữ nguyên

- DEV không có `shcn.settings.json`, chưa bật `ctsv.shcn.persistence.enabled` và không có Foundation SHCN grants. GET `/api/shcn/dot-sinh-hoat` với token CV trả 404 do route chưa được cấu hình. **SHCN HTTP business smoke NOT VERIFIED**; schema/constraint/trigger thật và 58 Unit/MockMvc SHCN đã đạt. Không tự cấp quyền, bật module lâu dài hay nhập feature code SHCN ngoài migration được giao.
- Helper seed cũ `tools/dev/java/pbl6/ctsv/auth/P11DevFixture.java` còn guard schema V24; không chạy lại fixture hoặc sửa helper trong lượt bảo trì migration này. Runtime P11 hiện hành không sử dụng helper đó.
- Không downgrade/drop/reset/clean DB, không sửa migration lịch sử V1–V23, không chạy lại P11 SQL, không `repair`, không ký lại hoặc xóa PDF. `outOfOrder` không được lưu vào cấu hình/source/launcher.
- Không chạy toàn bộ Playwright hoặc toàn bộ backend suite. Không sửa logic backend/frontend, Backlog/acceptance P11 hay lịch sử evidence cũ; không commit/push/merge Git branch.

Evidence local: `target/v25-reconcile-20261123/` có backup/restore proof, clone/dev history/data/P11 before-after và result JSON, `dev-clone-baseline-match.json`, schema checks, `shcn-sql-smoke.json`, `jar-source-match.json`, `dev-final-validate.log`, `http-smoke.json`, `focused-tests.log` (lượt bị chặn) và `focused-tests-native.log` (63 test đạt). Không credential/token trong evidence.
