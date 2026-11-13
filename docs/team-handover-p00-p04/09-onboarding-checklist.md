# 09 — Checklist thành viên tự kiểm

[Mục lục](README.md) · [Report](10-handover-review-report.md)

**Tất cả trạng thái ban đầu là NOT TESTED.** Đây là checklist cho máy thành viên sau khi owner giải quyết blocker; không chép trạng thái PASS của tác giả. Dùng PASS/FAIL/NOT TESTED, ghi BLOCKED trong ghi chú nếu thiếu điều kiện. Các trạng thái này dành riêng cho máy thành viên; kết quả thực thi máy tác giả ở report10.

Người thực hiện: … · Máy/Windows account local (không ghi credential): … · Ngày: …

## 1. Source và công cụ

| Bước | Thao tác/lệnh (cwd) | Kết quả mong đợi | PASS/FAIL/NOT TESTED | Ghi chú/bằng chứng không secret |
|---|---|---|---|---|
| E01 | Nhận cặp SHA release owner đã review; `git rev-parse HEAD`, `git status --short` mỗi repo | Có code thực, không chỉ SHA starter; working tree phù hợp release | NOT TESTED | B01 còn chặn |
| E02 | So inventory08, Test-Path launcher/common/helper/POM/FE locks/migrations/catalogs | File thiết yếu đều có, HEAD chứa chúng | NOT TESTED | |
| E03 | Java/javac/wrapper version; root từng backend | Java21, Maven3.9.16, Boot3.5.16 | NOT TESTED | |
| E04 | node/npm version, cwd frontend/npm ci | Baseline Node24.22.1/npm11.19.1, lockfile không đổi | NOT TESTED | |
| E05 | PowerShell7 command; thử entry từ5.1 | Có runtime phù hợp, không Bypass policy | NOT TESTED | |
| E06 | SQL Server version/instance và registry dynamic port | SQL2022/DEV2022 đúng ràng buộc tool hoặc bản tổng quát đã duyệt | NOT TESTED | |
| E07 | DLL Windows auth chính thức đúng architecture/version/path | Không thiếu native auth DLL | NOT TESTED | |
| E08 | Online Maven preparation trên cache mới theo02 | Sau đó setup offline không thiếu artifact/plugin | NOT TESTED | Chưa clone-cold-cache PASS |
| E09 | Nhận đủ contract/conventions/Test Strategy đã duyệt qua kênh share an toàn | Không phụ thuộc thư mục bí mật tác giả | NOT TESTED | |

## 2. DB, HTTPS, JWT và account

| Bước | Thao tác/lệnh | Kết quả mong đợi | PASS/FAIL/NOT TESTED | Ghi chú |
|---|---|---|---|---|
| D01 | SSMS Windows Auth; metadata02 trước setup | Đúng server/DB/owner; không test/evidence DB | NOT TESTED | |
| D02 | Đọc quyết định owner về preset synthetic ở02 §4 | Chỉ DEV/private repo; không copy DPAPI/key tác giả | NOT TESTED | B02 RESOLVED BY OWNER DECISION |
| D03 | `dev.ps1 setup` root ctsv sau điều kiện đầy đủ | DB riêng, marker, migrationsV1–V7 đúng checksum | NOT TESTED | |
| D04 | Đọc history/schema/counts synthetic (02) |3account, local IDs/FK đúng; không dump hash/token | NOT TESTED | |
| D05 | `dev.ps1 certificate` | Subject/Issuer/SAN IP127/CA=false/EKU/hạn/thumbprint đúng máy mình | NOT TESTED | Chỉ lưu metadata công khai |
| D06 | Chủ máy duyệt rồi trust exact thumbprint | HTTPS không warning, SAC ON | NOT TESTED | Không trust cert tác giả |
| D07 | Kiểm file JWT/TLS và ACL, không xem/copy private contents vào report | Hai keypair khác nhau, tồn tại bền ngoài Git | NOT TESTED | |
| D08 | Setup chạy lại explicit khi app đã dừng; so IDs/counts/hash fingerprint nội bộ | Không trùng account/mapping/quyền, không reset credential | NOT TESTED | Không in password hash |
| D09 | Kiểm env/config/actual SQL session theo PID app | App chỉ kết nối DB dev của mình | NOT TESTED | |

## 3. Vận hành/Auth/Foundation

| Bước | Thao tác/lệnh | Kết quả mong đợi | PASS/FAIL/NOT TESTED | Ghi chú |
|---|---|---|---|---|
| W01 | Rootctsv `dev.ps1 start`, status | BE ready rồi FE HTTPS200, URL chính xác | NOT TESTED | Không chạy tests/migrate/start SIS |
| W02 | Login SV bằng credentials synthetic DEV |200login, danh mục đúng projection | NOT TESTED | Không chụp response JWT |
| W03 | SV thử tra SV khác |403, không lộ danh sách | NOT TESTED | |
| W04 | Login chuyên viên, xem SV/process/kỳ synthetic | API thật, local IDs đúng, có ETag khi thiết kế yêu cầu | NOT TESTED | |
| W05 | Login admin xem RBAC/quyền local | Auth và CTSV/MGHP rights tách bạch | NOT TESTED | |
| W06 | DevTools cookie/storage/Origin/header | HttpOnly/Secure/Strict/Path, memory JWT; CSRF/CORS đúng | NOT TESTED | Không copy cookie values |
| W07 | Reload/tiếp tục login, hai tab refresh; lost response nếu test được giao | Không reuse sai, uncertain yêu cầu login | NOT TESTED | Không dùng stub thay lượt thật |
| W08 | Logout ở một tab | Cookie-only, không Bearer, tab khác nhận thay đổi | NOT TESTED | |
| W09 | Read→mutate synthetic→read ETag, thử stale khi được phép |409nghiệp vụ khác412version; rollback/error đúng | NOT TESTED | Chỉ fixture của mình |
| W10 | Stop/start có kiểm soát, so cert/JWT/account IDs và login lại | Cùng keys/cert/account, không30phút auto-stop | NOT TESTED | Không reset DB |
| W11 | Stop process launcher; giữ process khác | Chỉ owned PID/tree bị dừng | NOT TESTED | Không kill all Java/Node |
| W12 | Xem log location/hash source build | Biết tìm lỗi, không lộ secret | NOT TESTED | |

## 4. SIS → CTSV trên DB dev riêng

Theo [06](06-sis-ctsv-sync-guide.md), cwd root ctsv. Máy thành viên tự điền evidence; không chép PASS máy tác giả.

| Bước | Thao tác | Expected | Trạng thái |
|---|---|---|---|
| S01 | dev-sync setup | sis_dev đúng owner/marker, V1–V5, SNAPSHOT/CT; registry một nguồn | NOT TESTED |
| S02 | dev-sync start | HTTPS9443 ready; service key riêng ngoài Git, không harness | NOT TESTED |
| S03 | sync -Mode full lần đầu | DonVi/DanToc vào CTSV, checkpoint epoch:U | NOT TESTED |
| S04 | update rồi incremental | Giá trị mới, U tiến lên | NOT TESTED |
| S05 | insert/delete rồi incremental | Local PK độc lập, tombstone giữ lịch sử | NOT TESTED |
| S06 | stop/start/status rồi incremental | Epoch/checkpoint bền; không Full tự phát | NOT TESTED |
| S07 | stop/setup lần hai | IDs/registry/accounts không trùng; checkpoint không reset | NOT TESTED |
| S08 | Epoch/retention/confirmation lỗi | Giữ checkpoint và fail closed, không sửa guard/history | NOT TESTED |
| S09 | dev-sync stop; dev status | Chỉ SIS owned process dừng; BE/FE độc lập | NOT TESTED |

## 5. Sẵn sàng phát triển module

| Bước | Thao tác | Kết quả mong đợi | PASS/FAIL/NOT TESTED | Ghi chú |
|---|---|---|---|---|
| M01 | Đọc04/05, xác nhận phần việc nhóm giao | Dùng Auth/Foundation chung, không tự build lại | NOT TESTED | |
| M02 | Chỉ rõ identity/capability/data port định dùng | Có interface thật, không import repository nội bộ | NOT TESTED | Data-service generic còn gap |
| M03 | Kiểm API conventions/contract/test-case source | DTO/quyền/expected có nguồn | NOT TESTED | |
| M04 | Đối chiếu Flyway history/version trước tích hợp | Một chuỗi CTSV, không sửa migration đã apply | NOT TESTED | |
| M05 | Chọn tests/gates theo thay đổi | SQL/HTTP thật khi cần; không đòi CI bắt buộc | NOT TESTED | Waiver cũ không tự miễn gate task mới |
| M06 | Review source/secret/ignore trước yêu cầu commit | Không push keys/password/DBdump/log thô | NOT TESTED | Chỉ owner cho phép mới commit/push |

Kết luận của thành viên sau thực hiện: … · Mục FAIL/BLOCKED: … · Việc cần owner phối hợp: …

**Không đánh dấu toàn onboarding PASS khi chỉ FE mở được hoặc khi còn S01–S09 chưa kiểm nhưng đang yêu cầu đầy đủ sync.** Có thể xác nhận riêng phần Auth/Foundation đã chạy, giữ phần SIS BLOCKED; đó không mở lại P00–P04 đã được chủ dự án nghiệm thu.
