# 06 — Setup và chạy SIS → CTSV DEV

[Mục lục](README.md) · [Prerequisites](01-prerequisites.md) · [Lỗi/recovery](07-troubleshooting.md)

Công cụ thực: [dev-sync.ps1](../../dev-sync.ps1), [Sync.Common.ps1](../../tools/dev/Sync.Common.ps1), [SyncDev.java](../../tools/dev/java/pbl6/ctsv/dev/SyncDev.java). Dùng lại engine P01 units và P02 CT-26; không tạo giao thức/API/engine thứ hai.

## 1. Điều kiện và tập source

- Hai checkout `ctsv` và `daotao` đầy đủ, thường cùng thư mục cha. Nếu khác: truyền `-SisRoot 'C:\work\daotao'` cho mỗi lệnh.
- Hoàn tất [setup CTSV](02-installation-guide.md), có `ctsv_dev`, private home, classpath/helper đã build và leaf CA=false do máy mình tạo. Review/trust đúng leaf bằng `dev.ps1 certificate/trust` trước start.
- SQL Server2022 instance `localhost\DEV2022`, Windows Authentication; current login phải là DB owner. Dynamic port đọc lại từ registry, không lấy port tác giả.
- Migrations SIS V1–V5 và **V2 .sql.conf** phải có thật. Không sửa các migration này. Maven cache cần chuẩn bị online trên máy mới (cwd daotao):

```powershell
.\mvnw.cmd -B -ntp '-Dmaven.test.skip=true' package dependency:build-classpath
```

Tool chỉ nhận hai DB dev exact `sis_dev`/`ctsv_dev`. Tạo sis_dev nếu thiếu; DB tồn tại phải đúng instance/owner/compat160/collation và marker. DB chưa có marker chỉ được nhận khi hoàn toàn trống. Không chạy fixture test hoặc bỏ guard evidence.

## 2. Setup một lần

Cwd **ctsv**, PowerShell7; entrypoint từ Windows PowerShell5.1 tự chuyển sang PS7 đã cài:

```powershell
.\dev-sync.ps1 setup
```

Setup làm rõ ràng, không chạy ngầm lúc start:

1. Kiểm CTSV bằng helper check/Flyway validate, không seed lại Auth.
2. Tạo/kiểm sis_dev với marker `sis_dev_owner=sis-local-dev-v1`; validate history rồi migrate từ source SIS. V1–V5 thiết lập SNAPSHOT, CT90 DAYS/AUTO_CLEANUP và tracking bảng; RCSI không bị bật.
3. Seed một DonVi mã `DEV-SYNC-UNIT` và một DanToc mã `DEV-SYNC-DT`, label **SYNTHETIC DEV**. ID do SQL identity sinh, lưu ownership ledger bằng extended property `sis_dev_fixture_v1`. Không ghi đè bản ghi khác hoặc nhận nhầm mã đã tồn tại.
4. Tạo một registry `he_thong_ngoai.ma=DEV-SIS-SYNC` trong ctsv_dev; resolve source ID thực, không giả định1. Registry pin HTTPS origin; không chứa service key.
5. Tạo/lưu riêng service key và source epoch trong private `<dev-home>/sync/config.json` (DPAPI). Pin epoch trong sis_dev; mismatch dừng, không tự rebind.
6. Sinh private `provider.properties`, `sync.properties`, Java PKCS12 truststore chỉ chứa **public leaf của máy mình**, spool/log/classes. Dùng TLS leaf cùng setup CTSV cho cổng9443; JWT key vẫn khác TLS key. Không cài trust mới hoặc Root CA mới.
7. Setup lặp chỉ validate/no-op fixtures/registry; không reset account, checkpoint, epoch hay dữ liệu đã đồng bộ.

Private home lấy từ `dev.ps1 status`, thường `%LOCALAPPDATA%\CTSV\dev\<checkout-hash>`. Không copy private home giữa người dùng/checkout. File chứa secrets thực không nằm trong repo.

## 3. Start Provider, Full lần đầu, kiểm trạng thái

Cwd ctsv:

```powershell
.\dev-sync.ps1 start
.\dev-sync.ps1 sync -Mode full
.\dev-sync.ps1 status
```

Expected: provider **https://127.0.0.1:9443**, DB sis_dev. Start chạy Boot JAR thật, không test harness, không migrate/seed/sync. HTTPS readiness yêu cầu401 INVALID_SERVICE_KEY ở units khi không gửi key. Có key được tạo riêng thì consumer gọi được cả units và ethnicities; resource grants chỉ mở hai resource demo.

Full bootstrap ghi mapped rows vào `don_vi`/`dan_toc` của ctsv_dev, checkpoint `epoch:U` sau commit. Units dùng `dot_dong_bo`; ethnicities dùng `dong_bo_trang_thai/dong_bo_luot`. Source ID không phải CTSV PK. Delete về sau đánh dấu vắng nguồn, giữ local history.

`-Mode full` chỉ cho bootstrap khi chưa có checkpoint. Khi đã có checkpoint, dùng `-Mode incremental` hoặc `auto` (mặc định). Không có option xóa checkpoint/ép baseline. Có thể chọn `-Resource units` hoặc `-Resource ethnicities`; mặc định `all` là hai resource demo, không phải cả26.

## 4. Incremental Update / Insert / Delete

Chỉ các bản ghi synthetic nằm trong ownership ledger:

```powershell
.\dev-sync.ps1 stop   # demo liên tiếp: không còn sync active
.\dev-sync.ps1 start
.\dev-sync.ps1 update
.\dev-sync.ps1 sync -Mode incremental
.\dev-sync.ps1 status
# Một cặp bản ghi extra có mã DEV-SYNC-UNIT-X-n / DEV-SYNC-DT-X-n:
.\dev-sync.ps1 stop
.\dev-sync.ps1 start
.\dev-sync.ps1 insert
.\dev-sync.ps1 sync -Mode incremental
.\dev-sync.ps1 stop
.\dev-sync.ps1 start
.\dev-sync.ps1 delete
.\dev-sync.ps1 sync -Mode incremental
```

Update tăng revision label; insert lặp không nhân đôi; lần tạo mới sau delete dùng mã theo generation để không đụng UNIQUE của local history; delete chỉ xóa **extra**, không xóa bản baseline hoặc dữ liệu khác. FK không cho xóa thì dừng/rollback, không cascade/reset. Checkpoint sau mỗi bước nguồn đã commit phải tiến triển; chạy no-op có thể giữ U.

**Giới hạn hiện hành:** export store giữ batch15phút và tối đa2slot. Nhiều sync nhanh có thể nhận503 `EXPORT_CAPACITY_EXCEEDED`. Chờ TTL. Hoặc trên DEV riêng, chắc chắn không còn lượt sync đang chạy rồi `stop`/`start` provider trước lượt tiếp theo. Restart chỉ bỏ export spool tạm theo engine hiện có, không xóa source epoch/checkpoint hoặc dữ liệu. Không tăng quota/đổi TTL để demo. Công cụ không tự restart/retry vô hạn.

## 5. Restart, setup lần hai và stop

```powershell
.\dev-sync.ps1 stop
.\dev-sync.ps1 start
.\dev-sync.ps1 status
.\dev-sync.ps1 sync -Mode incremental
# Setup lại chỉ khi Provider đã dừng:
.\dev-sync.ps1 stop
.\dev-sync.ps1 setup
.\dev-sync.ps1 status
```

So epoch, sourceSystemId, fixture IDs, registryCount=1 và checkpoints trong `<dev-home>/sync/status.json`. Start/restart không tự sync. Stop chỉ dừng PID/creation time/command đã ghi, không kill theo port hoặc tên Java. Các command của tool có mutex riêng; engine vẫn giữ SQL application lock/fencing theo resource.

Launcher `dev.ps1` độc lập, tiếp tục chạy Auth/Foundation BE8443/FE5173 với sync OFF. Consumer command là Java process ngắn, gọi services hiện có rồi thoát; không mở thêm HTTP port/application CTSV.

## 6. Evidence và kiểm tra lại

Log private: `sync/logs/{setup,check,sync,status,provider}.log`, stderr provider riêng. `status.json` chỉ có metadata/fixtures synthetic; `smoke-manifest.json` chứa assertion thật. Không gửi raw config/argfile/key hoặc token.

Kịch bản A–E có script [Sync.Smoke.ps1](../../tools/dev/Sync.Smoke.ps1), chỉ sau Full bootstrap và baseline DEV không bị sửa ngoài tool:

```powershell
# PowerShell7, cwd ctsv; Provider đang chạy:
.\tools\dev\Sync.Tools.Tests.ps1
.\tools\dev\Sync.Smoke.ps1
```

Smoke chủ động update/insert/delete extra và restart Provider giữa lượt nhanh để giữ quota hiện có; kết thúc Provider dừng, không reset DB. Nó không nghiệm thu lại P01/P02. Kết quả local chính xác xem [10](10-handover-review-report.md); clone/máy mới chưa thực hiện.

## 7. Fail closed / phạm vi hỗ trợ

- SOURCE_EPOCH_CHANGED hoặc SOURCE_LINEAGE_INVALID: dừng đúng resource, kiểm restore/identity; không xóa checkpoint.
- FULL_SYNC_REQUIRED: engine recovery bounded hiện có; không thay timestamp/as_of thành U.
- BASELINE_CONFIRMATION_REQUIRED: cần review baseline theo contract; units có preview/confirmation hiện có nhưng wrapper DEV này không tự phê duyệt; P02 không được bypass khi mất checkpoint.
- Script hỗ trợ hai checkout trên **chính máy developer**, HTTPS loopback với leaf riêng. Kết nối máy SIS từ xa cần hostname/SAN/trust/source registry được phối hợp, chưa được script này kiểm chứng.
- 24 binding active toàn dự án (units+23), Staff/CVHT gated. Wrapper demo chỉ chọn DonVi/DanToc, không loại resource khác khỏi phạm vi P02.
