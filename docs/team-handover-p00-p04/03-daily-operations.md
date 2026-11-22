# 03 — Start, stop, status và log

## Giới hạn đăng nhập — quyết định owner21/11/2026

Mặc định hiện tại `ctsv.auth.login-rate-limit-enabled=false` trong application.properties, áp dụng mọi profile kể cả prod. Không cần sửa config local sau mỗi restart. Chỉ bật lại bằng `true` khi owner yêu cầu rõ ràng, không tự bật theo profile hoặc để chạy lại kiểm thử; khi bật áp ngưỡng5 lần/cặp username+IP và30 lần/IP trong15phút. JWT/hashing/refresh/CSRF/CORS và authorization không thay đổi. Không coi việc tắt limiter là bỏ xác thực hoặc chấp nhận sai mật khẩu. Unit test tập trung: `mvnw.cmd -Dtest=AuthLoginLimiterConfigurationTest,AuthPolicyTest test`. AuthHttpIT hiện có dùng opt-in true để giữ các assertion429 của limiter; chỉ chạy nhánh opt-in khi owner giao kiểm limiter, không đổi expected để test xanh. Quy trình kiểm có trọng tâm theo [AGENTS §9](../../AGENTS.md#ctsv-efficient-verification).

Kiểm chứng21/11:7unit PASS/0FAIL/ERROR/SKIP; build và stop/start launcher PASS, không migration/seed/reset. HTTPS REAL trên DEV:7request cùng username giả liên tục đều401 AUTHENTICATION_FAILED, không429;1login DEV hợp lệ200. [Unit log](../../target/auth-limiter-all-green.log), [HTTP evidence](../../target/auth-limiter-runtime.json). Kiểm mặc định các profile bằng unit; không khởi động/deploy production hoặc chạy lại toàn AuthHttpIT. Lượt HTTP probe đầu dừng do PowerShell trả Content dạng byte[], đã dùng lại ConvertFrom-DevResponse của launcher và chạy lại đủ7request; không đổi expected để bỏ qua lỗi.

## Đề xuất rút ngắn lượt kiểm UI sau sự cố30phút

- Chọn ca theo phần thay đổi: unit/helper → ca browser tái hiện lỗi → kiểm luồng hợp lệ/lỗi/quyền/mobile liên quan. Chỉ chạy hồi quy tổng hợp một lượt sau khi các ca riêng đã đạt; có thay đổi mới mới chạy lại phần bị ảnh hưởng.
- Mỗi tài khoản đăng nhập một lần trong một lượt, tái sử dụng browser context. Sau lỗi giữ trang/context để quan sát và chạy lại bước lỗi; không cần đăng nhập lại/tạo fixture mới từ đầu. Không lưu token/password vào file để tái sử dụng.
- Kiểm locator bằng DOM thực và tên truy cập trước luồng dài. Select có option dễ làm nhãn khác exact string; locator phải duy nhất. Không force-click hoặc sửa expected để lách lỗi.
- Checkpoint fixture theo ID và kiểm marker/ownership trước tái sử dụng, kiểm trạng thái đã commit sau timeout. Không gửi lại lệnh ghi mù hoặc tạo thêm hồ sơ/PDF chỉ vì script dừng.
- Helper HTTP chỉ đọc response body khi cần; command kiểm status và UI/GET persisted state. Lỗi lấy body của công cụ phải tách khỏi lỗi nghiệp vụ.
- Upload/download Cloudinary thật một lần cho phần tích hợp bị ảnh hưởng; dùng fixture đã xác minh cho ca điều hướng không cần upload. Build/typecheck khi source ổn định, không chạy lại backend suite cho thay đổi thuần FE. Ghi coverage và phần chưa kiểm thay vì kéo dài suite chỉ để thêm số PASS.

Các điểm trên là đề xuất cho workflow kiểm thử, không tự thay Acceptance Criteria hoặc Quality Gates. Lượt trước bị kéo dài bởi selector sai, lỗi toast thật, lỗi đọc body Chrome, đăng nhập lặp dẫn tới Retry-After530giây và các thao tác demo lặp. Không có số liệu đủ để cam kết một thời gian cố định cho mọi task; mục tiêu là giảm phần chạy lặp, vẫn giữ bằng chứng cho luồng người dùng.

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

## Bật upload PDF MGHP trên DEV

Dùng `.\dev.ps1 start` hằng ngày. Chỉ dùng các lệnh sau khi máy chưa lưu cấu hình Cloudinary hoặc cần thay khóa. Nhập giá trị DEV từ nơi lưu nội bộ khi terminal hỏi.

```powershell
.\dev.ps1 stop
.\dev.ps1 storage
.\dev.ps1 start
```

`storage` lưu cấu hình mã hóa ngoài Git cho tài khoản Windows và checkout hiện tại. Launcher ưu tiên cấu hình đã lưu, với thư mục `ctsv` ở chế độ Dynamic Folder. Khi chưa có file lưu, launcher có thể nạp đủ ba biến `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` từ shell. Nếu thiếu cấu hình, launcher cảnh báo và API tệp trả `503 STORAGE_UNAVAILABLE` cho một yêu cầu upload PDF hợp lệ. Auth và Foundation vẫn chạy.

## Chuẩn bị dữ liệu kê khai MGHP cho sinh viên DEV

Setup mới tạo dữ liệu mẫu cho `dev.sinhvien` (`DEV-SV-001`). Với DB DEV đã setup trước đó và còn thiếu trường, chạy `.\dev.ps1 stop`, `.\dev.ps1 build`, `.\dev.ps1 seed-student`, rồi `.\dev.ps1 start`. Lệnh bổ sung các trường trống của đúng sinh viên mẫu trên `ctsv_dev`, giữ các giá trị đã có và có thể chạy lại.

Mở lại form để lấy dữ liệu gợi ý. Điền địa chỉ thường trú giả lập vì đây là lời khai của sinh viên. Giữ mã số danh mục được điền sẵn, chọn đợt còn nhận và gắn PDF vào từng yêu cầu giấy của chính sách trước khi nộp.

## Bổ sung hồ sơ MGHP P07 trên DEV

P07 dùng V12. Sau khi source có V12 và được phép cập nhật DEV, dùng `stop` → `migrate` → `start` như phần migration bên dưới. `start` giữ vai trò kiểm tra phiên bản. Auth và cấu hình Cloudinary DEV vẫn lấy từ cấu hình local đã lưu.

- `dev.chuyenvien` vào **Miễn giảm học phí → Hồ sơ MGHP**, chọn hồ sơ, mở **Mở vòng bổ sung**, nhập lý do/hạn/mục cần bổ sung rồi gửi. Mã vòng được trả về trên màn hình. Dùng **Đọc vòng bổ sung** để đọc lại, gia hạn, xử lý từng mục và đóng vòng.
- `dev.sinhvien` mở **Thông báo → Xem yêu cầu bổ sung**, hoặc nhập mã yêu cầu tại **Hồ sơ MGHP → Bổ sung hồ sơ**. Kê khai phản hồi đầy đủ, có thể dùng PDF đã nộp hoặc tải PDF mới. Mỗi vòng nhận một bản phản hồi.
- Chuyên viên đọc **Đối chiếu và giấy đã nộp**, ghi nhận bản cứng/kiểm giấy trên đúng bản mới. Đọc lại khi dữ liệu đổi trước khi thao tác. Tài khoản admin Auth chỉ sử dụng chức năng nghiệp vụ khi được cấp capability phù hợp.

Smoke thực hành chạy trên ứng dụng của launcher và `ctsv_dev`. Script `frontend/e2e/p07-dev.mjs` tạo riêng dữ liệu synthetic qua API/UI hiện có, dùng PDF do caller chỉ định, giữ dữ liệu đã có. Script cần template đợt synthetic `NAV-SYNTH-*` và tài khoản DEV đã setup. Cách nạp cấu hình và các ID dữ liệu mẫu đã kiểm nằm trong [runbook P07](../../../nghiepvu/09-trien-khai/p07-supplement-implementation-plan.md#bàn-giao-dev--18112026). Evidence có URL/DB/storage thực tế trong `browser-report.json`.

## P08 — Đánh giá và rà soát MGHP

DEV hiện đã nâng V13 bằng `dev.ps1 migrate`; `start` tiếp tục chỉ kiểm lịch sử rồi chạy ứng dụng. Entity/Hibernate và migration trong executable JAR đã được đối chiếu. Không chạy lại setup, đổi khóa/chứng thư hoặc reset tài khoản để sử dụng P08.

- `dev.chuyenvien`: **Miễn giảm học phí → Đánh giá hồ sơ** (`/mghp/danh-gia`). Chọn hồ sơ, chuẩn bị/đọc bản nháp, ghi từng tiêu chí và giấy làm căn cứ rồi lưu. Xác nhận phải có nhận định/lý do; bản confirmed chỉ đọc, muốn đổi tạo bản đánh giá lại. Xác nhận cần xác minh cũng chặn rút, nhưng chưa tạo quyền hưởng.
- Dữ liệu thực hành đã chuẩn bị: đợt **Thực hành đánh giá hồ sơ · Tháng 11**, ID20016; hồ sơ10011 của `dev.sinhvien`, bản nộp10020, đánh giá1 confirmed cần xác minh và đánh giá2 draft. Đây là dữ liệu synthetic; không sửa chính sách đã khóa để biến hồ sơ thành đạt.
- Chuyên viên mở **Rà soát quyền hưởng** (`/mghp/quyen-huong`), chọn học kỳ, đọc quyền/lịch sử, mở riêng tác vụ quan sát hoặc yêu cầu cập nhật. Quan sát giữ nguyên tỷ lệ và mốc công nhận/thu hồi. Yêu cầu cập nhật dùng luồng bổ sung P07; sinh viên phản hồi thật, không tạo bản nộp giả.
- Sinh viên chỉ đọc quyền của mình; admin Auth đơn thuần không có màn đánh giá/rà soát nghiệp vụ. Quyền công nhận thật sẽ phát sinh từ công bố P12. Chưa seed quyền giả vào DEV; các ca ghi quan sát với quyền giả có nhãn được kiểm trên DB P08 riêng.

Smoke `frontend/e2e/p08-dev.mjs` dùng launcher DEV và Cloudinary REAL, không tắt xác minh TLS. Cách nạp cấu hình local qua DPAPI, lệnh chạy và kết quả nằm ở [Detailed Plan P08](../../../nghiepvu/09-trien-khai/p08-review-benefits-implementation-plan.md). Script có thể dùng lại hồ sơ synthetic P08 qua `P08_DEV_APPLICATION_ID`, không tạo thêm đợt/tài khoản/file khi chạy ở chế độ đó. Tạo fixture mới cần một đợt mẫu `NAV-SYNTH-*` có policy đã khóa được chuẩn bị qua P05 UI/API; trên máy mới phải chuẩn bị mẫu này trước, không copy DB tác giả hoặc fixture159hàng vào DEV.

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

Khi source có migration mới đang pending trên `ctsv_dev`, chạy `.\dev.ps1 stop`, `.\dev.ps1 migrate`, rồi `.\dev.ps1 start` từ `ctsv`. `migrate` build JAR từ source hiện hành và áp migration cho DB DEV có guard, giữ tài khoản hiện có. Nếu bước này báo lệch checksum hoặc history, dừng và đối chiếu source với Flyway history trước khi ghi. `start` chỉ kiểm tra Flyway và khởi động ứng dụng.

`setup` dành cho lần chuẩn bị môi trường hoặc khi cần cấp lại tài khoản DEV theo quyết định của chủ dự án. Kiểm dữ liệu mẫu SYNTHETIC theo runbook của slice khi bàn giao để từng vai trò chạy được luồng chính. Xem [runbook DEV/P05](../../frontend/README.md).

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

## PRE-P10-01 timeline — trạng thái bàn giao20/11/2026

Timeline đã ACCEPTED WITH OWNER WAIVER; G8 được owner miễn riêng mini, không G8 PASS. DB test riêng đã kiểm kỹ thuật; owner đã cho phép nâng ctsv_dev từ V13 qua V14/V15 SHCN, V16 Học bổng, V17 timeline MGHP. Đã migrate/start/smoke4/4PASS, source/JAR/DB khớp. Không bỏ qua migration/reset/seed lại tài khoản; start vẫn không tự migrate/seed.

DEV đang READY tại https://127.0.0.1:5173 (BE https://127.0.0.1:8443). Chuyên viên vào **Miễn giảm học phí → Đợt xét MGHP → Xem đợt → Lịch trình & tiến độ**. Có thể thiết lập lịch khi tạo đợt, sửa kế hoạch, gia hạn nhận khi còn open, đọc lịch sử và kiểm tra trước Đóng xét. Lý do bắt buộc; stale phải đọc lại/đối chiếu, không tự retry. Đóng xét không là Hoàn tất đợt; Hoàn tất còn nguồn ký/công bố/Portal/handoff của P12/P13, hiện fail closed.

Dữ liệu thực hành: **đợt20017 — Thực hành lịch trình MGHP · Học kỳ I 2026–2027** còn mở để thử; **đợt20018 — Minh họa Đóng xét · Không có hồ sơ** đã đóng xét. Đây là dữ liệu synthetic tạo qua UI, không phải policy pháp lý mới hoặc fixture sao chép từ DB test.

[Plan/evidence/giới hạn hiện hành](../../../nghiepvu/09-trien-khai/pre-p10-timeline-implementation-plan.md#11-kết-quả-gate-b). Không dùng tài khoản/fixture test thay credentials DEV; không gọi test harness làm server hằng ngày.
# Lưu ý đồng bộ main — 22/11/2026

Source hợp nhất dùng chuỗi chung V1–V21. **Owner đã cho chuyển đổi `ctsv_dev` và bảo trì hoàn tất ngày22/11: DEV hiện đủ21 migration, Flyway chuẩn0 pending, migrate/start và smoke đạt.** Xem [báo cáo DEV V21, backup và bảo toàn dữ liệu](../dev-v21-upgrade-20261122.md). Hai migration riêng V17/V18 cũ được đối chiếu chính xác và chuyển định danh thành V20/V21; không dùng quy trình này cho DB khác khi chưa kiểm/backup riêng. [Báo cáo merge](../git-main-sync-20261122.md) ghi trạng thái lịch sử trước chuyển DEV. Các số version trong evidence cũ giữ theo thời điểm đã chạy.
