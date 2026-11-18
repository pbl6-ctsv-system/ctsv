# Cấu trúc frontend dùng chung CTSV

Đọc [SKILL.md](SKILL.md) trước khi code. [AGENTS.md](AGENTS.md) là điểm nhắc cho Codex; quy tắc chi tiết chỉ có trong SKILL.md.

- src/app: App, AuthProvider, AppShell và cấu hình tích hợp navigation/featureRegistry.
- src/shared: auth-client, hooks API kỹ thuật, UI primitives và styles/tokens; không đặt nghiệp vụ riêng tại đây.
- src/features/auth: đăng nhập và quản trị tài khoản; src/features/foundation: danh mục/sinh viên/xác minh/quyền.
- src/features/mghp/configuration: quản lý chính sách và đợt xét P05 qua registry chung. drl, hocbong, shcn vẫn chỉ có vị trí kiến trúc, chưa có menu/nghiệp vụ giả.
- Mỗi module xuất entry cần dùng từ index.ts. Khi tích hợp, phối hợp sửa hai file trong app/navigation; không sửa code module khác.
- Auth implementation ở src/shared/auth/auth-client.mjs. Entry cũ src/shared/auth-client.mjs chỉ re-export để giữ tests/integrations tương thích.
- CSS riêng dùng .module.css. Tokens ở src/shared/styles/tokens.css; không thêm CSS toàn cục cho module.

## Kiểm tra sau refactor

Cwd frontend: npm.cmd run build và npm.cmd test. Browser DEV: e2e/modules-dev.mjs đọc cấu hình/credentials qua môi trường private như local-dev.mjs, dùng Chrome và launcher dev HTTPS thật. Chỉ đọc dữ liệu nghiệp vụ; login/refresh/logout là Auth thật. Evidence ở <dev-home>/frontend-modules-evidence/browser.json, không đưa credential vào Git.

Phần hướng dẫn setup/vận hành hiện có giữ dưới đây; FE refactor không sửa Vite proxy, origin, HTTPS, BE, database, dependency hoặc lockfile.

# CTSV web — P04
React19.2.0 / TypeScript5.9.3 / Vite7.1.12. Một frontend chung, không tạo frontend SIS/Kế toán hoặc module khác.

## Chạy dev hằng ngày — từ 13/11/2026

Không dùng `FoundationBrowserHarnessIT` làm server dev. Profile `dev` chạy application CTSV thật, Auth/Foundation/P05 chung datasource/transaction manager; không tự dừng sau30phút. P04 đã ACCEPTED WITH OWNER WAIVER, không nghiệm thu lại/G8 mới. P05 DEV đã áp V8 và smoke API13/11/2026; xem mục P05 bên dưới. P05-99 vẫn chờ owner nghiệm thu.

**Đích:** `localhost\DEV2022 / ctsv_dev` (Windows Authentication); FE **https://127.0.0.1:5173**, BE **https://127.0.0.1:8443**. Dùng đúng IP, không đổi sang `localhost` vì SAN/origin khác. Launcher lấy lại cổng TCP động DEV2022 mỗi lần chạy. JDBC giữ cách mã hóa/Windows-auth local đã kiểm chứng; HTTPS browser/proxy luôn xác minh chứng thư, không dùng TLS bypass.

### Setup một lần

Từ PowerShell, cwd `D:\7rd_semester\pbl6_src\ctsv`. `dev.ps1` nhận cả Windows PowerShell5.1 và tự chuyển sang PowerShell7 hiện có; phần chạy ứng dụng vẫn dùng PowerShell7. Không đổi Execution Policy. Trên máy mới chưa có PowerShell7, phải chuẩn bị công cụ đó trước setup:

```powershell
.\dev.ps1 setup
.\dev.ps1 certificate
# Chỉ sau khi chủ máy kiểm và đồng ý đúng leaf:
.\dev.ps1 trust -ConfirmThumbprint ACFF6DDED84813899F4099D8E06D10EB8757D828
```

Máy đã có JDK21/Maven Wrapper/Node và dependencies FE từ P04. Máy/checkout mới cần `cd frontend; npm ci`; không nâng version. Setup build không chạy test suite, migrate đúng V1–V8 hiện có và seed SYNTHETIC tối thiểu trên **ctsv_dev**. DB mới có compat160/collation Vietnamese_100_CI_AS_SC; DB tồn tại phải đúng owner/marker và history/checksum hợp lệ. Setup dùng Flyway migrate với validateOnMigrate=true để vừa kiểm checksum đã áp vừa nhận migration pending; check/start vẫn từ chối pending. DB không rỗng thiếu history hoặc không thuộc setup bị từ chối; không baseline/clean/drop/reset hay tự nhận dữ liệu cũ. Không đụng DB test, không copy159hàng fixture P05 vào dev.

**Quyết định mới13/11:** ba tài khoản DEV dùng mật khẩu cố định **`CtsvDev@2026!`**. Setup áp dụng preset riêng cho đúng danh tính SYNTHETIC trong `ctsv_dev`; password vẫn lưu Argon2id trong DB. Lần chuyển từ mật khẩu ngẫu nhiên sẽ đổi hash + tokenVersion nguyên tử, thu hồi refresh cũ và ghi audit. Setup lặp khi đã đúng preset là no-op về mật khẩu/version, không tạo mapping trùng. Nếu mật khẩu được quản trị đổi và không khớp secret local thì setup dừng, không âm thầm ghi đè. Start không reset password. Dữ liệu dev không phải dữ liệu SIS thật hay căn cứ nghiệp vụ đã xác minh.

Khóa/config/log ở `%LOCALAPPDATA%\CTSV\dev\E73A1C8C2025` trên checkout hiện tại (launcher tính mã theo đường dẫn checkout, không hardcode). ACL chỉ cấp tài khoản Windows hiện tại; mật khẩu/PFX password lưu DPAPI. HTTPS và JWT dùng **hai khóa RSA riêng**; private key và runtime properties nằm ngoài Git. Không sao chép thư mục này sang máy/người dùng khác, không đưa vào VITE_*, log hoặc chat.

Leaf đã được chủ máy duyệt: Subject=Issuer `CN=127.0.0.1`; SAN chỉ IP127.0.0.1; **CA=false**, EKU Server Authentication; thumbprint `ACFF6DDED84813899F4099D8E06D10EB8757D828`, hết hạn12/05/2027. Giữ trust đến khi hết hạn/ngừng dùng; start không cài lại. Đây không phải CA phát hành chứng thư khác; Windows Trusted Root không có cơ chế giới hạn kho trust riêng theo hostname. Smart App Control giữ ON.

### Start / stop / status

```powershell
.\dev.ps1 start       # hoặc chỉ .\dev.ps1
.\dev.ps1 status
.\dev.ps1 stop
```

Start kiểm chứng thư/thời hạn/trust, file build, cổng, DB owner/history/checksum; chờ BE HTTPS sẵn sàng rồi mở FE. **Không migrate, seed, cài trust, chạy test hoặc build ngầm**. Stop chỉ dừng PID+creation-time+command đã ghi và cây process con còn đúng identity, không kill theo tên/cổng. Đóng terminal không dừng app; không có timeout tự tắt. Cổng bị process khác chiếm thì dừng báo lỗi, không tự kill nó.

Windows PowerShell5.1 không chạy trực tiếp các API .NET mới của launcher: entrypoint chuyển nguyên action/option sang `pwsh.exe`. Đường dẫn runtime đã dùng được lưu ngoài Git trong `powershell-runtime.txt` cùng thư mục dev, nên shell mới không cần có cùng PATH với Codex. Nếu runtime đã chuyển chỗ, mở PowerShell7 và chạy `dev.ps1 status` một lần để cập nhật đường dẫn; không setup lại DB/khóa. Start khi app đang chạy sẽ báo `Launcher already running`; dùng `status`, hoặc `stop` rồi `start` khi thật sự muốn restart.

Khi sửa Java/resource/POM: `stop` → `build` → `start`. `build` chỉ compile/package và tool dev, không test/migrate/seed. Sửa frontend dùng HMR; không cần restart BE. Có migration mới được duyệt thì chủ động `stop` → `setup` → `start`; history cũ bất biến. Không dùng `setup` như lệnh start hằng ngày.

### Đăng nhập local

| Tài khoản | Mật khẩu DEV | Chức năng dev |
|---|---|---|
| `dev.sinhvien` | `CtsvDev@2026!` | SV giả `DEV-SV-001`, danh mục Foundation; không được tra SV khác |
| `dev.chuyenvien` | `CtsvDev@2026!` | Quyền Auth CTSV_USER + quyền local mghp_chuyen_vien toàn trường; tra/xác minh P04, quản lý chính sách/đợt P05 |
| `dev.admin` | `CtsvDev@2026!` | AUTH_ADMIN + CTSV_USER; quyền local **ctsv.roles.manage** cấp riêng; không tự có quyền chuyên viên MGHP |

Cứ nhập tài khoản/mật khẩu ở bảng trên vào form HTTPS. Đây là **preset dữ liệu DEV công khai**, không đặt password trong frontend hoặc bypass login. Preset đáp ứng validation tối thiểu12ký tự; tool DEV dùng AuthCredentials.hash hiện có, không có ngoại lệ mật khẩu ngắn và không đổi contract/logic Auth. Có thể dùng lệnh clipboard cũ nếu muốn:

```powershell
.\dev.ps1 credentials                 # chỉ liệt kê username
.\dev.ps1 credentials -Account student # hoặc specialist / admin
# Dán vào form HTTPS, sau đó xóa clipboard:
Set-Clipboard -Value ''
```

Khóa TLS/JWT/PFX password vẫn riêng tư ngoài Git; chỉ mật khẩu giả lập nêu trên được công khai theo quyết định người dùng. Muốn đổi password khác qua Auth-admin vẫn tuân thủ policy Auth và cập nhật secret DPAPI local tương ứng. ID dev xem `identities.json` trong thư mục local; PK Auth/SV/user độc lập.

### IntelliJ (tùy chọn)

Cách đơn giản là tạo **Shell Script/External Tool** chạy `pwsh.exe -NoProfile -File "$ProjectFileDir$\dev.ps1" start`, working directory `$ProjectFileDir$`; dùng cấu hình tương tự cho stop/status. Không chọn JUnit browser host. Backend đã chạy thì không Run thêm application tranh cổng.

Muốn chạy BE riêng từ IntelliJ: main `pbl6.ctsv.CtsvApplication`, profile `dev`, VM option `-Dspring.config.additional-location=file:///C:/Users/USER/AppData/Local/CTSV/dev/E73A1C8C2025/application-dev.properties`, PATH có DLL integrated-auth đã dùng P00. Kiểm không có SPRING_DATASOURCE_URL/SPRING_PROFILES_ACTIVE/Flyway/bootstrap env cũ ghi đè và chỉ dùng ctsv_dev. FE cần `P04_TLS_CERT`, `P04_TLS_KEY`, `NODE_EXTRA_CA_CERTS` và `P04_BACKEND_URL` như file local; chạy `npm run dev -- --port 5173 --strictPort`. Launcher đã thiết lập các giá trị này và guard tốt hơn; IntelliJ không bắt buộc.

### Gia hạn / ngừng dùng

Start từ chối cert hết hạn và không tự gia hạn. Gia hạn phải **giữ nguyên JWT keys, config Accounts/DPAPI và DB**: stop, tạo leaf mới CA=false/SAN127.0.0.1 bằng công cụ Windows, export lại HTTPS PFX/PEM trong thư mục private, cập nhật Thumbprint/PfxPassword bằng DPAPI; xem lại metadata bằng `certificate`, xin xác nhận trước `trust` đúng thumbprint mới. Sau khi kiểm HTTPS mới thành công, gỡ đúng thumbprint cũ. Không xóa config hay rerun generation để gia hạn vì có thể làm mất credentials.

Khi ngừng dùng, `stop`, gỡ chính xác leaf này (Windows có thể hiện hộp thoại cần bấm Yes):

```powershell
certutil -user -delstore Root ACFF6DDED84813899F4099D8E06D10EB8757D828
Remove-Item -LiteralPath Cert:\CurrentUser\My\ACFF6DDED84813899F4099D8E06D10EB8757D828 -DeleteKey
Test-Path Cert:\CurrentUser\Root\ACFF6DDED84813899F4099D8E06D10EB8757D828
```

Không xóa DB hoặc cả kho chứng thư. Private key xuất ra file vẫn tồn tại trong thư mục local có ACL; chỉ xóa đúng file của dev khi chắc chắn ngừng dùng, giữ credential cần thiết nếu vẫn giữ DB. Chứng thư TEST P04 cũ228278…458A đã gỡ; không cài lại nó.

### Phạm vi và kiểm chứng của thay đổi dev

Plan thực thi: (1) thêm profile/tool setup có guard + bootstrap/hash/mapping hiện hành; (2) launcher có readiness/ownership và runbook; (3) kiểm setup lặp, real browser login/refresh/logout và restart; không mở lại P04. Tool Java trong `tools/dev/java` compile ngoài src/main/test, không được đóng gói vào application. Bằng chứng ở `nghiepvu/.analysis/runtime/local-dev/20261113`; chỉ các kiểm liên quan launcher/dev, không chạy lại P00–P04 hoặc tạo G8 mới.

**Kết quả13/11:** setup mới + chạy lại PASS; cùng3accountId, counts/mapping/roles/Flyway checksum và password hash không đổi. Sáu file chứng thư/khóa/config DPAPI ổn định qua setup/restart. 11kiểm PowerShell PASS;5ca browser dev PASS với HTTPS thật (login ba vai trò, API P04, cookie/CSRF/CORS, refresh nhiều tab, logout, JWT+cookie cấp trước restart tiếp tục hợp lệ). Stop đã giữ process sentinel ngoài launcher, từ chối identity sai; cổng bị process khác chiếm thì start từ chối và giữ process đó. Metadata SQL theo PID Java xác nhận các kết nối app đều ở **ctsv_dev**. Smart App Control=1;61file src/main/migration có trước task không đổi. Không phát lệnh ghi vào DB test. Chưa kiểm IntelliJ GUI, gia hạn khi cert hết hạn hoặc chạy liên tục180ngày; đây không phải nghiệm thu lại P04/production.

- `npm ci`, `npm test`, `npm run build`.
- Dev HTTPS: đặt `P04_TLS_CERT` và `P04_TLS_KEY` là đường dẫn chứng thư/key TEST ngoài Git, và `P04_BACKEND_URL` là origin HTTPS backend CTSV. Chứng thư backend phải được Node tin cậy (kho CA hoặc `NODE_EXTRA_CA_CERTS`), không dùng secure=false/TLS bypass.
- `npm run dev`. Backend bật Auth/Foundation và cấu hình `ctsv.auth.origins` chứa đúng origin frontend, giữ cookie Path=/api/xac-thuc. Proxy giữ Origin frontend; không đưa service key, signing key hoặc JDBC vào biến VITE_*.
- Đăng nhập/tiếp tục đăng nhập bằng cookie Secure/HttpOnly; access chỉ memory. Web Locks tuần tự hóa cookie giữa tab, BroadcastChannel chỉ thông báo thay đổi trạng thái; localStorage chỉ lưu marker bất định, không token.
- Refresh timeout/mất response: không phát lại cookie cũ, yêu cầu đăng nhập lại. Logout luôn cookie-only, không Bearer. Browser không hỗ trợ Web Locks bị chặn rõ, không fallback race không an toàn.
- Sửa profileLinks chỉ sau quy trình khóa/đối soát identity đã được duyệt tại Auth DB §9; Auth-admin không tự có quyền nghiệp vụ MGHP.

**Hiện trạng12/11/2026:** `npm ci`, build/typecheck, dev/HMR PASS. Theo yêu cầu người dùng, Rollup dùng alias `npm:@rollup/wasm-node@4.68.2` và override `$rollup`; giữ đúng Rollup4.68.2/Vite7.1.12, không nâng dependency khác. Đây là bản WASM chính thức, không sửa node_modules hoặc tắt Smart App Control.

15/15 Browser E2E trên Chrome thật, HTTPS frontend/backend và SQL Server P04 thật: SV/CV/admin; cookie HttpOnly/Secure, CSRF/CORS, refresh hai tab, mất response, logout cookie-only; UI401/403/404/412/503 và recovery. Sáu Node unit là mô phỏng, được ghi riêng. Hai runner ở `e2e/https-p04.mjs` và `e2e/https-errors-p04.mjs`; `e2e` có lockfile Playwright riêng, dùng Chrome đã cài. [Plan/runbook](../../nghiepvu/09-trien-khai/p04-foundation-implementation-plan.md) ghi môi trường/lệnh/fixture và giới hạn.

Chứng thư TEST thumbprint `228278928395FFD8ED2BF0FD049B5892E235458A` đã gỡ khỏi CurrentUser Root/My sau test; Smart App Control vẫn ON. Muốn chạy lại HTTPS phải chuẩn bị/trust chứng thư TEST theo sự cho phép của người dùng, không bỏ kiểm TLS. P04-99 **ACCEPTED WITH OWNER WAIVER**, G8 **WAIVED riêng P04**, không PASS; đây là mốc lịch sử. Hiện trạng P05 DEV xem bên dưới.

## P05 trên DEV — cập nhật13/11/2026

`ctsv_dev` đã ở FlywayV8; executable JAR chứa P05, launcher Auth/Foundation/P05 chạy bình thường tại URL DEV ở trên. Chỉ `setup` chủ động được migrate, không start ngầm. V1–V7/checksum, account/password hash/tokenVersion, TLS/JWT keys được đối chiếu và giữ nguyên. Không reset/clean/drop, không copy fixture test159hàng; migration chỉ seed9M danh mục có nguồn.

Đăng nhập `dev.chuyenvien` bằng mật khẩu DEV hiện hành, chọn **Chính sách MGHP** hoặc **Đợt xét MGHP**:

- Chính sách **M9**, phiên bản1 đã xác nhận (`quy_uoc_do_an`) và phiên bản2 nháp để thử sửa/xác nhận. Tên/căn cứ đều ghi SYNTHETIC; tỷ lệ demo không là chính sách pháp lý.
- Học kỳ **DEV-2026-1**, ID1: mốc giả lập01/09/2026–31/01/2027, xác minh qua API Foundation với evidence SYNTHETIC, không phải lịch chính thức.
- Đợt **SYNTH-P05-20261113-SMOKE**, ID1: đã thử tạo/sửa/mở/đóng/hoàn tất, hiện completed; SV được đọc projection công khai.
- Đợt **SYNTH-P05-20261113-DRAFT**, ID2: nháp đủ cấu hình để bạn thử sửa/mở/đóng/hoàn tất. Mở nhận cấu hình14/11/2026 lúc15:35 (UTC+7); có thể công khai trước nhưng chưa nhận hồ sơ trước opensAt. Chưa triển khai P06.
- `dev.admin` chỉ có quyền quản trị Auth/role theo phân công, không tự có quyền quản lý MGHP. `dev.sinhvien` chỉ đọc đợt công khai, không đọc nháp hoặc ghi cấu hình.

26HTTP smoke checks qua HTTPS Vite→executable Boot→SQL Server DEV PASS, gồm3login và13route P05. FE/module P05 trả200, TLS validation ON. **Kiểm UI tương tác trên DEV trong lượt này NOT VERIFIED**: công cụ Computer Use dừng vì không xác định được URL Chrome đủ tin cậy; không chuyển sang cách điều khiển browser khác hoặc bỏ kiểm TLS.7Browser E2E P05 đã PASS trước đó là bằng chứng trên DB P05 riêng, không tính thành kiểm UI DEV mới.

[Evidence DEV và giới hạn](../../nghiepvu/.analysis/runtime/p05/20261113/dev/README.md). Không thay đổi nghiệp vụ/API; sửa duy nhất helper DEV để setup chấp nhận migration pending trong khi vẫn kiểm checksum. Start vẫn không migrate/seed. Không commit/push; P05-99 chưa tự ACCEPTED.
### Nghiệm thu lại UI P05 bằng Playwright MCP —13/11/2026

Đã tương tác UI thật: policy/round/quyền sinh viên. Lỗi danh sách version giữ nhãn nháp sau xác nhận đã được sửa và kiểm RED→GREEN; tạo→sửa→xác nhận hiển thị Đã xác nhận ngay, không reload tay, detail readonly/ETag giữ đúng.2test model và build/typecheck PASS; không chạy lại toàn P05. Giới hạn Computer Use trước đó đã được vượt qua bằng Playwright MCP được owner yêu cầu ở nhiệm vụ sau; không dùng bằng chứng API thay browser. [Evidence phần sửa](../../nghiepvu/.analysis/runtime/p05/20261113/dev/policy-confirmation-fix.json). Quy tắc nghiệm thu list/detail sau mutation tại SKILL.md §7; giữ test UI e2e/p05-policy-confirmation.mjs để chạy hồi quy đúng phạm vi.

## Thao tác MGHP và quản trị — 18/11/2026

Menu hồ sơ phân biệt theo capability backend cấp: sinh viên dùng **Hồ sơ MGHP** tại `/mghp/ho-so`; chuyên viên MGHP dùng **Tiếp nhận & bổ sung hồ sơ** tại `/mghp/tiep-nhan-ho-so`. Đây là hai route frontend đến các view đã có, không phải endpoint API mới. Sinh viên không vào route tiếp nhận; chuyên viên chỉ có quyền nghiệp vụ không vào route nộp cá nhân; Auth-admin đơn thuần không vào cả hai. Người được cấp cả hai capability có hai mục riêng. Không còn dùng cùng tên “Hồ sơ MGHP” cho màn chuyên viên.

- Hồ sơ: chọn một dòng để xem chi tiết; dùng các mục Tổng quan, Yêu cầu bổ sung/Bổ sung & kiểm giấy, Lịch sử & minh chứng. “Nộp hồ sơ mới” mở kê khai ba phần, giữ dữ liệu khi chuyển phần hoặc request lỗi. Rút hồ sơ, gia hạn và kiểm giấy được mở riêng khi cần.
- Chính sách/đợt: danh sách, phiên bản và form là những trạng thái riêng. Cấu hình dài chia nhóm; bản đã xác nhận hiển thị thông tin chỉ đọc. Quản trị tài khoản chọn tác vụ xem/tạo/liên kết/quyền/trạng thái thay vì mở mọi form cùng lúc. Tra cứu tài khoản vẫn dùng UUID theo API hiện có.
- `FeedbackProvider` dùng chung hiển thị lỗi cố định trong viewport tới khi người dùng đóng hoặc lỗi được xử lý. Nút chính/phụ/nguy hiểm và bố cục mobile dùng design tokens hiện hành. Tên gợi ý danh mục giữ ID chuẩn trong request; chỉ định dạng nhãn mẫu DEV nhận diện được, không sửa dữ liệu cũ.
- Quy chuẩn cho các màn hình tiếp theo: [SKILL.md §11](SKILL.md#11-thiết-kế-theo-tác-vụ--áp-dụng-sau-review-ui-ngày-18112026). Phạm vi và kết quả: [kế hoạch cải thiện thao tác](../docs/frontend-usability-plan.md).

Smoke được chạy bằng `node frontend/e2e/p07-dev.mjs` và `node frontend/e2e/ui-workspaces-dev.mjs` từ root CTSV, sau khi launcher DEV sẵn sàng. Cả hai yêu cầu `CTSV_DEV_HOME`, `CTSV_DEV_SMOKE_PASSWORDS` (JSON lấy từ cấu hình DPAPI trong bộ nhớ, không lưu file), `NODE_EXTRA_CA_CERTS`; runner hồ sơ thêm `P07_SAMPLE_PDF`, `P07_DEV_EVIDENCE`, runner quản trị thêm `CTSV_UI_EVIDENCE`. Những runner này tạo dữ liệu synthetic mới qua UI/API hiện hành trên `ctsv_dev`; không dùng cho dữ liệu thật hoặc DB test/evidence. Chúng tuân thủ `Retry-After` của Auth khi gặp giới hạn đăng nhập, không vô hiệu hóa TLS hoặc limiter.

## P08 — Đánh giá và quyền hưởng

`features/mghp/review/ReviewWorkspace.tsx` tích hợp vào navigation/AppShell hiện có qua `ConfigurationPage`; không thêm router hay Auth riêng. `/mghp/danh-gia` dành cho chuyên viên MGHP. `/mghp/quyen-huong` cho chuyên viên rà soát hoặc sinh viên đọc quyền chính chủ; tài khoản có cả hai capability giữ màn nghiệp vụ chuyên viên, không tạo menu trùng. Backend tiếp tục kiểm quyền và ownership.

Draft có tiêu chí/căn cứ/khoảng theo từng nguồn; confirmed hiển thị thông tin chỉ đọc và đường tạo revision mới. Mọi write đọc lại view/ETag đúng representation trước thông báo thành công. Lỗi dùng Feedback chung trong viewport. Ghi nhận rà soát và yêu cầu cập nhật nằm trong từng vùng mở theo tác vụ; không đưa toàn bộ form ra trang mặc định.

Kiểm thử P08: 21 Node test và build/typecheck; Browser trên `ctsv_p08_test_20261118` qua `e2e/p08.mjs` (SQL/HTTPS thật, storage STUB), smoke launcher `ctsv_dev` qua `e2e/p08-dev.mjs` (Cloudinary REAL). Giữ bản ghi FAIL đã sửa, không tính lượt bị owner dừng là PASS. [Kế hoạch/kết quả và lệnh chạy](../../nghiepvu/09-trien-khai/p08-review-benefits-implementation-plan.md), [runbook dữ liệu DEV](../docs/team-handover-p00-p04/03-daily-operations.md#p08--đánh-giá-và-rà-soát-mghp).
