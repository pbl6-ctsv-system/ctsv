# 03 — Start, stop, status và log

[Mục lục](README.md) · [Setup một lần](02-installation-guide.md) · [Xử lý lỗi](07-troubleshooting.md)

**Điều kiện:** nhận đủ source và hoàn tất [setup](02-installation-guide.md). Auth/Foundation đã có evidence local; máy mới tự kiểm. Sync DEV chạy riêng theo [06](06-sis-ctsv-sync-guide.md).

## Dùng hằng ngày

Cwd **root checkout `ctsv` của bạn**, không phải `System32`, `frontend` hoặc `daotao`:

```powershell
.\dev.ps1 status
.\dev.ps1 start
# Khi thật sự muốn restart:
.\dev.ps1 stop
.\dev.ps1 start
```

Không có action `restart`; dùng stop/start. `dev.ps1` không tham số mặc định là start. Nếu app đã chạy, start báo `Launcher already running`, không tạo instance thứ hai. Mở URL FE mà status trả; mặc định **https://127.0.0.1:5173**, BE **https://127.0.0.1:8443**. Dùng127.0.0.1, không đổi sanglocalhost.

Windows PowerShell5.1 entrypoint tự tìm runtime qua file `powershell-runtime.txt`, PATH, hoặc `Program Files/PowerShell/7`. Máy mới không có cache tác giả: cài PowerShell7 có PATH trước; có thể chạy `pwsh -NoProfile -File .\dev.ps1 status`. Không dùng `ExecutionPolicy Bypass` làm cách chữa mặc định.

## Start làm gì và không làm gì?

1. Đọc config ở `%LOCALAPPDATA%\CTSV\dev\<mã-checkout>`; lưu runtime PowerShell đang dùng; mutex ngăn command cạnh tranh.
2. Đọc lại cổng SQL dynamic từ registry DEV2022, set PATH DLL/auth và environment chỉ cho tiến trình local; dựng properties từ cùng config/keys.
3. Kiểm leaf đúng thumbprint/hạn dùng/trust; kiểm hash source build, dependency Vite và port rảnh.
4. Java helper chạy mode `check`: xác minh instance/DB owner/marker, validate Flyway/check pending. **Không migrate/seed**.
5. Chạy **Java application JAR bình thường**, Hikari/JPA/Auth/Foundation; không dùng class test. Lệnh launch ép profile dev/ctsv_dev/Flyway-off/Hibernate-validate/bootstrap-off/sync-off để tránh biến SPRING_* cũ chọn sai đích.
6. Poll `GET /api/xac-thuc/toi`: expected401 `UNAUTHENTICATED` qua TLS hợp lệ chứng minh route Auth sẵn sàng, không phải login PASS. Backend có90giây để ready; đây là timeout start, **không phải thời hạn sống server**.
7. Chạy Node Vite trực tiếp, chờ HTTPS200 rồi báo READY. FE proxy `/api` đến BE, `secure:true`, giữ Origin; Node tin thêm public cert qua `NODE_EXTRA_CA_CERTS`.

Không chạy test suite, build ngầm, seed/reset, migration, trust certificate hoặc regenerate JWT/account. Không chạy SIS, Kế toán, scheduled sync. Sync flags bị launcher tắt chủ động; xem [06](06-sis-ctsv-sync-guide.md), không bật bằng cách đoán biến môi trường cho launcher này.

## Status/stop có giới hạn gì?

- Status đối chiếu PID + thời điểm tạo UTC + command line, rồi in URL/DB/log path. Nó **không** tự gọi DB hoặc kiểm HTTP liveness liên tục. `RUNNING` chỉ xác nhận process identity, không bảo đảm mọi API đang healthy.
- Stop chỉ dừng root process đã ghi và descendants còn đúng identity/creation-time. Không kill tất cả Java/Node, không kill theo port. Nếu PID đã được tái sử dụng hoặc server được Run ngoài launcher, nó từ chối nhận quyền sở hữu.
- File state `processes.json` nằm ngoài Git; mất state không có nghĩa được kill theo tên. Đối chiếu process riêng của bạn trước khi chủ động dừng qua IDE/task manager. Không xóa state để vượt kiểm tra app đang chạy.
- Đóng terminal không tự dừng BE/FE; không có timer30phút như browser harness. Stop sử dụng dừng process, không phải quy trình graceful shutdown production đã được nghiệm thu.

## Sửa source và dependency

Cwd `ctsv`:

```powershell
.\dev.ps1 stop
.\dev.ps1 build
.\dev.ps1 start
```

Build dành cho thay Java/resource/POM; package không chạy test hay migration. Sau khi sửa source, phải chạy test liên quan riêng theo Quality Gates; build này không thay kiểm thử. Sửa FE bình thường dùng HMR. Nếu package/lockfile thay đổi đã được duyệt: cwd `ctsv/frontend`, `npm.cmd ci`; không tự dùng `npm update`/sửa native binary.

Chỉ chạy lại `setup` khi có việc setup/migration được giao và đã dừng process; không coi đó là start hằng ngày hay cách sửa lỗi login. Preset tài khoản DEV synthetic đã được owner chấp nhận; không áp ngoại lệ này cho khóa TLS/JWT/service hoặc credentials thật. Từ P05, setup áp thêm V8 có kiểm checksum; start vẫn chỉ check, không tự migrate. Xem [runbook DEV/P05](../../frontend/README.md).

## Tự kiểm liveness và xem log

Lấy đường dẫn log chính xác từ status (không dùng mã thư mục tác giả). Các file:

| File local | Dùng để |
|---|---|
| `logs/backend.log`, `backend.err.log` | Startup JPA/Hikari/Tomcat/Auth/exception |
| `logs/frontend.log`, `frontend.err.log` | Vite, proxy/TLS/HMR |
| `logs/db-check.log` | Kết quả guard và Flyway validate của start |
| `logs/build.log`, `classpath.log`, `dev-tool-compile.log` | Lỗi Java/dependency/helper |
| `logs/db-setup.log`, `db-setup-snapshot.json` | Chỉ lần setup explicit; không là log transaction hằng ngày |
| `identities.json` | Local ID synthetic để dùng UI; không suy ID từ MSSV/Auth UUID |

PowerShell7, cwd bất kỳ, sau khi BE đã được launcher báo READY:

```powershell
$r = Invoke-WebRequest 'https://127.0.0.1:8443/api/xac-thuc/toi' -SkipHttpErrorCheck
$r.StatusCode  # 401 khi chưa có Bearer; không dùng -SkipCertificateCheck
```

PowerShell5.1 không hỗ trợ `-SkipHttpErrorCheck`; dùng browser Network tab hoặc chạy khối kiểm trên trong `pwsh`. Không coi401 là TLS lỗi, và không coi401 này chứng minh database/login/authorization đều đúng. Xem log bằng `Get-Content -LiteralPath '<đường-dẫn-log-của-bạn>' -Tail 50`; không đính kèm config/key/token vào nhóm chat.

## IntelliJ tùy chọn

External Tool/Shell Script: program `pwsh.exe`, arguments `-NoProfile -File "$ProjectFileDir$\dev.ps1" start`, cwd `$ProjectFileDir$`. Tạo tool stop/status tương ứng nếu muốn. Không cần Compound Configuration; chưa kiểm GUI trong audit. Không Run thêm BE từ IDE khi launcher đang giữ8443.

Nếu tự Run `pbl6.ctsv.CtsvApplication` ngoài launcher, developer phải tự cung cấp profile/config private/PATH DLL và tự kiểm datasource/Flyway-off/sync flags; các guard command-line của launcher không tự có. Ưu tiên launcher để tránh drift, không copy Run Configuration chứa password vào Git.
