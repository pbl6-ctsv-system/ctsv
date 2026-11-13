# 07 — Chẩn đoán theo triệu chứng

[Mục lục](README.md) · [Cài đặt](02-installation-guide.md) · [Sync](06-sis-ctsv-sync-guide.md)

Các cách xử lý dựa trên source; phần sync DEV mới đã được kiểm có chọn lọc tại máy tác giả, xem report10. Chỉ thao tác DB/process/config của mình; không clean/drop/reset, bỏ guard, disable assertions hoặc tắt SAC/TLS để làm xanh.

## Công cụ, build và launcher

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh | Cách xử lý an toàn | File/log liên quan |
|---|---|---|---|---|
| Java/javac sai version | PATH/JAVA_HOME/JRE khác | `java -version`, `javac -version`, wrapper `-v` | Chọn JDK21 đã chuẩn bị, không nâng Boot | POM, build.log |
| Maven offline báo thiếu artifact/plugin | `.m2` mới, setup dùng `-o` | classpath/build log | Chuẩn bị online theo02; kiểm proxy; không copy `.m2` chứa secret | Dev.Common, classpath.log |
| Wrapper không tải Maven | Mạng/proxy/distribution không có | wrapper properties và log download | Dùng đúng3.9.16, xử lý truy cập nguồn; không tự sửa URL sang version khác | `.mvn/wrapper` |
| Node/npm khác baseline | PATH nhiều bản | `node -v`, `npm.cmd -v`, `Get-Command node` | Chọn bản đã chốt, giữ lockfile | package files |
| npm ci thất bại | package/lock lệch, mạng | Đọc log và diff trước đó | Lấy đúng cặp lockfile; không npm update để bỏ lỗi | package-lock.json |
| Rollup `.node` bị SAC chặn | Cài native Rollup hoặc lockfile cũ | `npm.cmd ls rollup`, resolved package | Dùng alias/override WASM4.68.2 trong source đã duyệt; npm ci trên máy mình; không sửa node_modules hoặc tắt SAC | frontend/package*.json |
| WASM/dev/build thiếu module | node_modules chưa chuẩn bị | file Vite bin/npm ls, build log | npm ci ở đúng frontend; E2E có package riêng | Vite config, frontend log |
| dev.ps1 báo PowerShell version | Entry cũ #requires7 hoặc runtime không có | header file, `$PSVersionTable`, `Get-Command pwsh.exe` | Nhận entrypoint mới5.1; cài/chọn PS7; chạy status từ PS7 để ghi pointer | dev.ps1, powershell-runtime.txt |
| Script bị Execution Policy chặn | Chính sách máy | `Get-ExecutionPolicy -List` chỉ đọc | Theo chính sách của chủ máy/IT; không hướng dẫn Bypass làm mặc định | PowerShell |
| Không thấy dev.ps1/config | Clone HEAD starter, sai cwd, setup chưa xong | `Get-Location`, Test-Path, inventory08 | Nhận đầy đủ release source rồi setup; không viết launcher thay | 08, config local |
| Port occupied | App đang chạy hoặc process khác | status; `Get-NetTCPConnection -LocalPort <port> -State Listen` | Chỉ stop launcher của mình; server ngoài launcher tự đối soát/stop qua IDE | processes.json |
| Launcher already running | Một app đã có state đúng | status+URL | Dùng app hiện có; nếu cần restart stop/start | dev.ps1 |
| BE không start/JPA lỗi bảng | Sai DB/schema/migration/config | backend log, db-check, SQL DB_NAME/history | Dừng, kiểm đích; explicit setup sau review; không ddl-auto update | backend.log |
| Startup log im lặng hoặc sai đường dẫn | FileURI/path/cache private khác checkout | status/read path của máy mình | Không dùng mã thư mục tác giả; xem setup output | application-dev.properties |
| FE không start | Dependency/port/cert/key/PATH | frontend.err.log, Vite bin | Kiểm đúng các file local và Node; không tạo cert mỗi start | frontend.log |
| READY nhưng API sau đó lỗi | Readiness chỉ kiểm401/Auth và FE200 tại start | Network/log + SQL target | Chẩn đoán lỗi thật; status RUNNING không là health tổng thể | backend.log,05 |
| Proxy báo lỗi/FE gọi BE thất bại | Backend không ready, URL/TLS sai | Network status, P04_BACKEND_URL, NODE_EXTRA_CA_CERTS | Khớp config8443/cert và trust Node; giữ secure:true | Vite config/frontend.err.log |
| HMR lỗi | Sai origin/socket hoặc không chạy Vite dev | Browser console/network và frontend log | Dùng đúng HTTPS FE origin; không dùng dist như HMR server | frontend/vite.config.ts |
| Stop từ chối PID/không nhận app | State mất, PID reuse, IDE Run khác launcher | creation-time/command + state | Không kill theo tên; xác minh đúng process mình trước xử lý; giữ log | Dev.Common Stop-DevOwned |
| Log đang bị process giữ | File handle/share mode | FileInfo length, log viewer | Đọc bằng viewer hỗ trợ share; không stop server người khác để lấy log | logs/*.log |

## HTTPS, Auth và quyền

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh | Cách xử lý an toàn | File/log liên quan |
|---|---|---|---|---|
| Cert hết hạn |180ngày đã hết | `dev.ps1 certificate` | Quy trình thay leaf riêng, giữ JWT/account; chưa có auto-renew |02, cert metadata |
| Cert chưa trust | Chưa duyệt/cài CurrentUser Root | Thumbprint và Test-Path đúng store | Review đúng cert máy mình rồi trust một lần; không dùng thumbprint tác giả | config/Root |
| TLS fail dù đã trust | SAN không khớp,127/localhost khác, Node/Java trust khác | Origin/Subject/SAN/issuer và lỗi TLS | Dùng127.0.0.1/cert tương ứng; Java truststore riêng khi SIS |02/06 |
| Browser warning connection | Trust/expiry/SAN | Xem certificate details | Dừng, không click bypass warning | Chrome security |
| Cookie không lưu/gửi | HTTP, wrong Path/SameSite/origin | Application Cookies và Network, không copy value | HTTPS đúng origin; Cookie Path xác thực; credentials:include | auth-client, AuthController |
| Login401 | Credential không khớp/locked | Chỉ kiểm username/state qua admin, không dump hash | Cấp/reset credential riêng bằng luồng đã duyệt; không seed/reset DB để thử | AuthService, DB setup log |
| Login429 | Limiter đang bảo vệ | Retry-After/log mã lỗi | Chờ đúng thời gian, kiểm credential; không retry vô hạn | AuthLoginLimiter |
| GET thường200 sau account revoke | Policy local JWT còn15phút | Xác định đúng endpoint/kind | Hành vi đã chốt, không tuyên bố mọi JWT bị revoke ngay |05 |
| GET nhạy cảm401 | Account/tokenVersion bị revoke | Fresh state và account được mapping | Login lại hợp lệ; không refresh loop che revoke | AuthAccess |
|403 sau login | Thiếu mapping/quyền nghiệp vụ, local inactive | Identity issuer/sub verified; Auth roles khác local roles | Đối soát đúng user/capability, không cấp tất cả làm admin | FoundationSecurity |
| CSRF403 | Thiếu/sai Origin hoặc X-CSRF-Protection | Network headers không lộ token | Dùng origin FE được whitelist, header1 cho cookie actions | AuthController.csrf |
| CORS preflight lỗi | Origin/port chưa trong allowlist | OPTIONS/Access-Control headers | Sửa private config đúng origin sau kiểm tác động; không wildcard credentials | Auth/Foundation Cors |
| Refresh invalid/reused | Hết7ngày, đã dùng, đã revoke hoặc request race | Mã lỗi/state, không đọc token thô | Login lại; giữ Web Locks, không thêm grace/retry cookie cũ | AuthService/auth-client |
| Mất response refresh | Server có thể commit trước mất mạng | Marker uncertain trong storage | Login lại theo UI; không xóa marker để phát lại cookie | auth-client.mjs |
| Logout không có tác dụng như mong đợi | Cookie thiếu/hết hạn/generation cũ hoặc request bị CSRF reject | Status/header/Path, không Bearer | Giữ cookie-only; no-op đúng contract khác lỗi mạng/DB | AUTH03 |
| JWT signature sai sau restart | Key/issuer/kid/path bị đổi | So hash public key/config, không log private key | Khôi phục đúng key bền của máy mình; không copy key tác giả | AuthJwt/private config |
| Profile link đúng nhưng không có local identity | Hai mapping khác trách nhiệm | LienKetHoSo khác dinh_danh_dang_nhap | Quy trình đối soát identity có audit; không gán owner từ email/source PK | Auth DB design,05 |
|412/428 khi lưu | Tag cũ/thiếu hoặc PUT không trả tag mới | ETag GET/If-Match request | GET lại, review dữ liệu rồi gửi; không tự lấy rowversion làm tag | Foundation contract |

## Database

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh | Cách xử lý an toàn | File/log liên quan |
|---|---|---|---|---|
| Không thấy DEV2022/registry port | Instance khác, TCP tĩnh, service chưa có | Registry/Configuration Manager, server metadata | Theo baseline hoặc tổng quát hóa tool ở vòng sau; không đoán port | Dev.Common |
| Windows Auth thất bại | Login/quyền/DLL/architecture sai | SQL Windows connect; DLL path/JDK x64 | Cấp login/quyền máy mình, DLL Microsoft đúng version; không dùng credential tác giả |01/db-check |
| ctsv_dev guard mismatch | DB name/owner/marker/collation/compat khác | Metadata02 | Dừng, bảo toàn dữ liệu, xác nhận ownership; không sửa marker để vượt guard | DevDatabase |
| Flyway checksum mismatch | Migration bị sửa hoặc history không khớp | File hash/history | Khôi phục file đã áp dụng, review migration mới; không repair tự động | V1–V7/history |
| Thiếu history nhưng DB có bảng | Từng chạy DDL rời | sys.tables/history | Cần kế hoạch adoption rõ; không baseline-on-migrate hay drop | DevDatabase guard |
| Thiếu bảng/FK | HEAD starter, thiếu migration hoặc sai DB | Inventory08, catalog/history | Nhận source đúng, áp migration explicit sau review | migration folder |
| Seed trùng/mật khẩu khác | Setup dở, credential local drift | counts/IDs/audit, không hash dump | Giữ state, xác định bước commit; không chạy lại để ép password | DevDatabase.seed |
| App trỏ nhầm DB | Env/IDE config cũ, Run ngoài launcher | SQL session theo host_process_id + DB_NAME | Dừng đúng app mình, sửa target/config; không thao tác dữ liệu |03/config |

## SIS sync

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh | Cách xử lý an toàn | File/log liên quan |
|---|---|---|---|---|
| Provider không chạy/404 | Chỉ starter/local thiếu flags hoặc resource P15 | Binding/path/ct26.enabled/slice-a.enabled | Nhận đủ source/config; không coi staff đã hoạt động | SisResourceBindings |
| Consumer không gọi provider | Runner dev đang tắt, network/key/TLS thiếu | Config/runner flag, log không lộ key | Hoàn thiện setup sync; không bật bừa trong launcher UI |06 |
|401/403 service | Key sai/thiếu grant | Header count,caller/resource allowlist | Đồng bộ private key cấp cho đúng caller; không đưa key vào FE | service filters |
| SYNC_NOT_READY503 | CT/SNAPSHOT/table tracking/PK chưa đúng | sys DB/CT metadata, resource SQL key | Explicit provision đúng source; không bật options từ request API | CtSnapshotReader |
| FULL_SYNC_REQUIRED409 | Checkpoint thấp hơn min valid sau retention | min valid version và checkpoint | Full recovery bounded có lineage đúng; không sửa U tay | CtSyncService |
| SOURCE_EPOCH_CHANGED409 | Epoch khác nguồn hiện hành | Private source config vs checkpoint | Đối soát restore/lineage, baseline confirm khi có cơ chế; không random epoch để qua lỗi | units runner/CT engine |
| SOURCE_LINEAGE_INVALID409 | Checkpoint lớn hơn upper | current CT và source lineage | Dừng, xác minh đúng DB/restore; không tự lấy timestamp thay U | CtSnapshotReader |
| BASELINE_CONFIRMATION_REQUIRED | Target đã có mapping nhưng mất checkpoint | Presence/mapping/run history | Units preview/confirmed khi phù hợp; P02 thiếu runner confirm tương đương, cần xử lý riêng | CtSyncService |
| Full sync dừng giữa trang | TTL/export budget/network/DB lỗi | export/run/page/checkpoint metadata | Giữ checkpoint cũ, theo retry bounded; không xóa lịch sử | disk export/durable store |
| Incremental không thấy thay đổi | Source chưa commit, sai source/resource, CT chưa bật | Request version + transaction + metadata | Commit đúng fixture nguồn, chạy lại resource đúng; không đồng nhất source/local ID |06 |
| Delete không xóa local row | Tombstone chỉ đánh dấu vắng để giữ lịch sử | presence flag/source ID/local ID | Expected; kiểm không hiện diện nguồn thay vì DELETE target | JdbcFoundationTarget |
| Sai mapping/FK | Chạy resource con trước danh mục, dùng PK SIS làm local | Wire source_id vs registry/target mapping | Chạy đúng dependency và mapping; không chỉnh FK để qua test | catalogs/mapper |
| Export expired/capacity503 | TTL/quota/spool/heap bound | Mã lỗi/budget/đĩa | Restart export bounded khi code cho phép; giảm tải theo contract, không tăng quota âm thầm | UnitExportStore/DiskExportStore |

Nếu recovery chưa có command vận hành đã kiểm, ghi BLOCKED đúng phần đó và giữ evidence. Không suy bằng chứng fault test thành quy trình vận hành production đã đầy đủ.

## Công cụ sync DEV

| Triệu chứng | Xử lý |
|---|---|
| Chưa có config/classes/classpath | Setup CTSV trước, rồi dev-sync setup. Không copy LOCALAPPDATA tác giả. |
| Owner/marker/epoch sai | Kiểm DEV2022/sis_dev/ctsv_dev và checkout. Dừng; không sửa marker/xóa checkpoint để vượt guard. |
| EXPORT_CAPACITY_EXCEEDED sau nhiều sync nhanh | Store giữ export15phút, hai slot. Chờ TTL; hoặc DEV riêng khi không có sync active: stop/start provider rồi retry incremental. Không tăng quota/đổi TTL. |
| Full khi đã có checkpoint | Dùng incremental/auto; -Mode full chỉ bootstrap, không ép reset. |
| Java PKIX/TLS lỗi | Kiểm public leaf/hạn dùng/private sis-trust.p12; setup lại rõ ràng khi provider đã dừng. Không trustAll/secure=false. |
| Port9443 bận | Không kill theo port. Dừng đúng owner hoặc phối hợp đổi cấu hình/registry; không sửa source URL âm thầm. |
| Một resource thành công, resource khác lỗi | Checkpoint từng resource độc lập. Giữ nguyên, sửa nguyên nhân rồi retry; không reset cả nguồn. |
| Smoke khác expected fixture | Không reset DB để ép PASS. Kiểm status/log và sửa ngoài tool; smoke chỉ dành cho fixture DEV có ledger. |

Chi tiết lệnh/log: [06](06-sis-ctsv-sync-guide.md). Lỗi tooling ban đầu (metadata tinyint, INSERT OUTPUT với trigger, alias PowerShell) đã sửa và kiểm lại, không thay expected của engine.
