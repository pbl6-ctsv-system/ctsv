# B01–B03 — Implementation & release preparation

**Ngày: 13/11/2026.** Phạm vi: công cụ DEV, kiểm chứng chọn lọc và tài liệu bàn giao. Không stage/commit/push/PR/merge, không P05, không mở lại nghiệm thu P00–P04. P04 **ACCEPTED WITH OWNER WAIVER**; G8 **WAIVED**, không PASS.

## A. B01 — Git Release Preparation

**Trước: BLOCKED → Sau: READY TO COMMIT.** Đây là trạng thái chuẩn bị; **chưa RESOLVED**, chưa phát hành hoặc clone sạch.

- Chuẩn bị theo nhóm source, không yêu cầu review hàng trăm file thông thường: Java/resources, Auth/Foundation/SIS engines, runtime schemas/catalogs, Flyway, Maven wrappers/POM, frontend/lockfiles, tools/setup/launcher/tests và bộ handover. [Inventory theo nhóm](08-git-push-safety-audit.md).
- Bổ sung .gitignore CTSV/SIS: private key/keystore/cert, .env (giữ examples), private properties, log/PID, DBbackup/data, runtime/cache/node_modules/dist. File runtime cần thiết như application-local.properties, lockfiles, JSON schemas và SQL .conf vẫn được giữ.
- Kiểm tracked/index riêng: không có file private extension đã tracked; required-file/ignore probes PASS. Không dùng .gitignore để che file đã tracked.
- Quét tập dự kiến chia sẻ: **172 file CTSV, 88 SIS, 10 Tài chính** (tracked + untracked exclude-standard). Không tìm private-key material, JWT literal, credential URI hoặc file secret/DBbackup rõ ràng trong tập scan. Preset DEV là ngoại lệ synthetic owner đã chấp nhận. Đây không phải chứng nhận mọi secret/remote history đều sạch.
- CTSV HEAD `1796a2a2ae43e764c849676415487293d314eeaa`, SIS `ea7bd1d4bb9c8faf4bb909b21b77e3cc573c35f1`, Tài chính `b6794adb739678f0346d2c7a32336d8bd871df6d` vẫn starter; không có release SHA mới. CTSV/SIS branch local codex/p01-slice-a; remote Tài chính tên ketoan.
- HEAD và raw Git index cả ba repo đối chiếu trước/sau không đổi. Rename SIS đã staged từ trước vẫn giữ nguyên. Migration SQL/.conf hai backend có checksum file không đổi.
- Không còn dependency **runtime bắt buộc** chỉ có trên máy tác giả: keys/config/account/registry/fixture/epoch/Java truststore có generator; JDK/SQL/DLL/PS7/Maven/npm có hướng dẫn cài riêng. Đã bỏ đường evidence nghiepvu cố định khỏi browser smoke.
- Chưa kiểm máy mới/cold-cache, clone sạch hoặc remote mới nhất. Khi phát triển contract-first, nhóm vẫn cần nhận bộ tài liệu nghiepvu đã duyệt qua kênh sạch; runtime không đọc thư mục này. Không copy archive .analysis/CSV/log thô.

**Cần commit vòng sau:** các nhóm ở08, gồm source đã có nhưng chưa tracked và file mới/sửa lượt này. **Không push:** TLS/JWT/service secrets, private home/config/argfiles, token/browser storageState, DBbackup/evidence nhạy cảm, target/node_modules/dist/logs.

## B. B02 — DEV Credentials

**RESOLVED BY OWNER DECISION — DEV SYNTHETIC CREDENTIAL RISK ACCEPTED.**

Áp dụng quyết định mới: private repo được giữ ba tài khoản `dev.sinhvien`, `dev.chuyenvien`, `dev.admin` và preset hiện có; hướng dẫn dùng tại [02 §4](02-installation-guide.md). Không tạo account/password mới, không thay Auth hashing/password policy/RBAC.

- Helper hiện có chỉ nhận ctsv_dev, Windows login đúng owner, instance DEV2022 và managed marker; bootstrap là lệnh setup rõ ràng, daily start bootstrap OFF.
- Argon2id/profileLinks/identity mapping/quyền CTSV hiện có được giữ. Admin Auth không mặc nhiên có quyền chuyên viên MGHP.
- Chạy lại **setup CTSV thực tế**: account IDs/tokenVersion và counts tài khoản/roles/mapping/quyền không đổi. Fingerprint TLS/JWT files trước/sau không đổi; không reset credentials. Browser login đủ ba vai trò PASS.
- Thay đổi cho B02 chủ yếu ở tài liệu; implementation bootstrap Auth/preset hiện có không sửa.
- Rủi ro còn lại được owner chấp nhận: ai đọc private repo biết preset; không dùng cho dữ liệu thật/production. Không mở rộng ngoại lệ tới TLS/JWT private key, SIS service key, token hoặc SQL credentials.

## C. B03 — SIS → CTSV Setup

**RESOLVED trong phạm vi DEV local đã giao.** Onboarding máy khác/clone sạch vẫn NOT VERIFIED.

### Công cụ và cấu hình

- `dev-sync.ps1`: setup/start/stop/status/sync và mutation synthetic update/insert/delete; có bridge Windows PowerShell5.1 → PS7.
- `Sync.Common.ps1`: target URL allowlist, build helper/provider, private config và lệnh vận hành.
- `SyncDev.java`: guarded SIS provision/Flyway/fixture/source registry/Java truststore/status; gọi **UnitSyncService/CtSyncService hiện có**, không thêm engine/API/Spring application.
- `Sync.Tools.Tests.ps1`, `SyncDevChecks.java`, `Sync.Smoke.ps1`: guard và kịch bản A–E thực.
- Private home ngoài Git có ACL CurrentUser; config DPAPI giữ service key, epoch và truststore password. Generated properties/@argfiles có secrets được giữ riêng; OS command line chỉ có path @argfile.
- Provider bind **https://127.0.0.1:9443**, Boot JAR SIS thật; enabled slice-a/ct26, chỉ cấp service resource grants units/ethnicities. Source epoch ổn định và đối chiếu marker DB. CLI values pin đích dev để ambient SPRING_* không đổi datasource/migration.
- Consumer là process ngắn gọi services hiện hữu, không chiếm cổng CTSV, không scheduler. Dùng profile/limits CT-26 từ catalog đã pin. Java TLS dùng PKCS12 truststore public leaf của chính máy developer; không trustAll, không tắt HTTPS validation/SAC, không thêm CA.

### Database và fixture

| Thành phần | Kết quả thực tế |
|---|---|
| SQL | localhost\\DEV2022, Windows Authentication; port động được đọc lại |
| SIS DEV | sis_dev, owner login hiện tại, marker sis-local-dev-v1; compat160/collation Vietnamese_100_CI_AS_SC |
| Migration SIS | V1–V5 success, validate checksum, giữ V2 .sql.conf nontransactional |
| Options | SNAPSHOT ON, RCSI OFF; CT90 DAYS/AUTO_CLEANUP ON, 24 bảng tracked theo migrations hiện có |
| CTSV DEV | ctsv_dev hiện có, marker ctsv-local-dev-v1; không migrate business schema mới |
| Registry | DEV-SIS-SYNC, resolve source ID thực; registryCount=1, không chứa service key |
| Fixture | DonVi/DanToc SYNTHETIC DEV; ownership ledger/IDs ở extended property; chỉ extra được delete |
| Mapping | PK CTSV độc lập source_id; Delete giữ local history và đánh dấu vắng nguồn |
| Đích được ghi | sis_dev và ctsv_dev; master chỉ kiểm identity/tạo đúng sis_dev nếu thiếu; không có lệnh ghi DB evidence/test |

Setup lại không reset checkpoint/source epoch/registry/fixtures/account. Mỗi lần tạo extra mới sau Delete dùng mã theo generation, tránh đụng UNIQUE của local history; không thay constraint để demo.

### Vận hành cho thành viên

Cwd **ctsv**, sau setup CTSV và trust leaf của chính máy mình:

```powershell
.\dev-sync.ps1 setup
.\dev-sync.ps1 start
.\dev-sync.ps1 sync -Mode full          # chỉ bootstrap chưa có checkpoint
.\dev-sync.ps1 status
# Các lần sau:
.\dev-sync.ps1 sync -Mode incremental
.\dev-sync.ps1 stop
```

Mặc định all chỉ gồm units/ethnicities demo; có `-Resource` và `-SisRoot`. Chi tiết fixture/restart/log/checkpoint ở [06](06-sis-ctsv-sync-guide.md).

### Kiểm chứng chọn lọc đã chạy

| Kiểm tra | Thật/mock | Kết quả |
|---|---|---|
| Maven SIS package, javac tool/checks | Source thực, không chạy suite P00–P04 | PASS |
| Sync script target/origin guards | Không DB/mock engine | 10/10 PASS |
| Java DB identity guards, sai owner/DB/instance/compat/collation | Pure checks | 6/6 PASS |
| Regression Dev.Tools.Tests.ps1 | Pure tooling checks hiện có | 12/12 PASS |
| A–E Full/update/insert/delete/restart/setup lặp | **HTTPS thật + SQL Server thật**, hai engine hiện hữu | **30/30 PASS** |
| Setup CTSV lặp, IDs/versions/counts/key fingerprints | SQL/dev files thật | PASS |
| Browser Auth/Foundation/3roles, HttpOnly/Secure Cookie, CSRF/CORS, multitab refresh, cookie-only logout | Chrome + FE HTTPS + Boot dev + SQL thật, không stub | 4/4 PASS |
| JWT/cookie/identity sau launcher restart | Browser/SQL thật | 1/1 PASS |
| Entry Windows PowerShell5.1 | Gọi status thực → PS7; Provider không tự chạy | PASS |
| required source/ignore probes, scan, migration hashes, HEAD/index | Files/Git read-only | PASS |
| Links/bảng handover | Offline trên 11 Markdown | 73 liên kết file/24 bảng, 0 lỗi |

Full ban đầu đã commit U=1 cho cả hai resource, epoch `a404dd15-09bc-4738-a2a5-f68fe0acd567`; lượt kiểm cuối tăng U11 → U12 (update) → U13 (insert) → U14 (delete) cho cả hai resource. Restart không đổi epoch/checkpoint, no-op incremental không đổi U là đúng. Manifest lưu checkpoints trước/sau và từng assertion; không dùng as_of làm checkpoint.

**Evidence mới:** private `<dev-home>/sync/smoke-manifest.json`, `sync/logs/guard-checks.log`, `handover-browser/browser-before.json` và `browser-after.json`; bản sanitized phục vụ đối chiếu tại `nghiepvu/.analysis/runtime/team-handover/20261113/`. Không copy raw configs/keys/tokens vào repo. Token/storage proof tạm của browser đã logout và gỡ đúng hai file sau kiểm.

### Lỗi đã gặp, cách xử lý và giới hạn

- Tool guard đọc SQL compatibility tinyint thành Number: đã có red/green regression và kiểm6guard.
- INSERT OUTPUT trên bảng có trigger: dùng OUTPUT INTO, không sửa trigger/schema.
- PowerShell alias cp và default PSScriptRoot trên5.1: đã sửa, chạy lại kiểm thực.
- Tạo lại mã extra sau delete làm đụng UNIQUE lịch sử: generation code trong ledger, không reset target/history.
- **503 EXPORT_CAPACITY_EXCEEDED là giới hạn hiện hành:** store giữ export15phút, hai slot. Không đổi quota/TTL/engine. Chờ TTL hoặc DEV riêng khi không còn sync active thì stop/start provider; smoke làm restart rõ ràng giữa các lượt nhanh. Resource đã thành công vẫn giữ checkpoint nếu resource khác lỗi; retry tiếp từ trạng thái hiện có.
- Source epoch/lineage/confirmation/recovery vẫn fail closed. Wrapper không tự xóa checkpoint hoặc phê duyệt baseline. Remote SIS host, cold-cache/máy mới, benchmark lớn và mọi resource ngoài hai resource demo chưa được kiểm trong lượt này.
- Hiện cuối lượt: SIS Provider dừng; CTSV Auth/Foundation BE8443/FE5173 vẫn chạy. Daily launcher không bị bật sync hoặc thêm migrate/seed khi start.

## D. Readiness tổng thể

| Hạng mục | Trước triển khai | Sau triển khai | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| CTSV Auth/Foundation local | Đã chạy trên máy tác giả | VERIFIED chọn lọc, launcher giữ nguyên | Setup lặp +5 browser checks | Máy thành viên tự kiểm |
| SIS Provider local | Chưa có dev setup | VERIFIED | sis_dev V1–V5, HTTPS9443 readiness | Cài baseline trên máy mới |
| SIS → CTSV Full | Engine đã nghiệm thu, thiếu command DEV | VERIFIED DonVi/DanToc | U1 và rows/mapping thật | Resource khác dùng đúng dependency khi có task |
| SIS → CTSV Incremental | Thiếu config/registry DEV | VERIFIED I/U/D | 30 checks, checkpoint/history | Lưu ý quota15phút |
| Setup idempotency | Chưa đủ chuỗi hai backend | VERIFIED DEV local | IDs/registry/epoch/checkpoint/account/key không reset | Không copy config giữa checkout |
| Clone reproducibility | HEAD starter | **NOT VERIFIED**; source/tools đã chuẩn bị | Group inventory, generator và runbook | Owner commit/push rồi clone sạch |
| Git release readiness | BLOCKED B01/B02 | **READY TO COMMIT** | Scan/ignore/index checks | Owner review/stage/commit/push khi giao lệnh |
| Documentation readiness | Có blocker/runbook chưa runnable | READY FOR OWNER REVIEW | 11 file đồng bộ công cụ thực | Theo checklist máy mới, không chép PASS |

## E. File thay đổi trong task này

| Repository | File | Tạo mới/Sửa | Lý do |
|---|---|---|---|
| ctsv | dev-sync.ps1 | Tạo mới | Entry vận hành SIS DEV độc lập launcher Auth/Foundation |
| ctsv | tools/dev/Sync.Common.ps1 | Tạo mới | Config/build/target/TLS/command helpers |
| ctsv | tools/dev/java/pbl6/ctsv/dev/SyncDev.java | Tạo mới | Guarded DB setup/fixture/registry/status + dùng engine hiện có |
| ctsv | tools/dev/java/pbl6/ctsv/dev/SyncDevChecks.java | Tạo mới | Sáu guard regression |
| ctsv | tools/dev/Sync.Tools.Tests.ps1 | Tạo mới | Mười guard/origin checks |
| ctsv | tools/dev/Sync.Smoke.ps1 | Tạo mới | A–E real dev assertions |
| ctsv | frontend/e2e/local-dev.mjs | Sửa | Evidence output dùng private home/env, không phụ thuộc nghiepvu tác giả; assertions giữ nguyên |
| ctsv | .gitignore | Sửa | Bảo vệ private/generated files, giữ source cần chia sẻ |
| daotao | .gitignore | Sửa | Cùng quy tắc release safety; không sửa provider/migrations |
| ctsv | docs/team-handover-p00-p04/README.md | Sửa | Điểm vào và trạng thái B01–B03 |
| ctsv | docs/team-handover-p00-p04/01-prerequisites.md | Sửa | sis_dev và Java truststore |
| ctsv | docs/team-handover-p00-p04/02-installation-guide.md | Sửa | Owner-accepted synthetic preset, gỡ blocker cũ |
| ctsv | docs/team-handover-p00-p04/03-daily-operations.md | Sửa | Tách daily launcher và sync command |
| ctsv | docs/team-handover-p00-p04/05-auth-and-foundation-guide.md | Sửa | Credentials mới nhất, giữ Auth policy |
| ctsv | docs/team-handover-p00-p04/06-sis-ctsv-sync-guide.md | Sửa | Runbook thực setup/full/incremental/status/restart/guard |
| ctsv | docs/team-handover-p00-p04/07-troubleshooting.md | Sửa | Lỗi tool/quota/TLS/registry và recovery an toàn |
| ctsv | docs/team-handover-p00-p04/08-git-push-safety-audit.md | Sửa | Thay inventory khổng lồ bằng nhóm source và ngoại lệ |
| ctsv | docs/team-handover-p00-p04/09-onboarding-checklist.md | Sửa | Bước sync thực, B02 owner decision; máy mới vẫn NOT TESTED |
| ctsv | docs/team-handover-p00-p04/10-handover-review-report.md | Sửa | Báo cáo trước/sau/evidence/readiness hiện hành |

04-architecture-and-integration.md giữ nguyên. Không tính các thay đổi Java/POM/AGENTS/source đã tồn tại trước task là do lượt này tạo. Evidence mới ngoài Git không thuộc tập release source.

## F. Việc owner cần làm tiếp

1. Review gói thay đổi và hai giới hạn vận hành: quota export hiện có; clone/cold-cache chưa kiểm. Không còn blocker kỹ thuật mở trong phạm vi DEV local hai resource.
2. Khi đồng ý, giao lệnh stage/commit/push theo nhóm ở08; review riêng staged rename SIS đã có từ trước.
3. Tuyệt đối không stage private home/key/config/service token/DBbackup/node_modules/target/dist hoặc evidence nhạy cảm. Preset synthetic DEV là ngoại lệ đã chốt, không bao gồm secret khác.
4. Sau push, pin hai SHA tương thích, clone vào thư mục sạch/máy thành viên, thực hiện02 →03/05 →06 và điền09. Chỉ lúc đó mới đóng B01 thực sự.
5. Không mở P05 hoặc thêm G8/CI/PR gate. Chờ owner duyệt trước bất kỳ Git write action.
