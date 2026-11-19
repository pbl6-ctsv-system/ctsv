# SHCN DEV seed — v1.1.0 (permission từ DB)

Gói này nằm trong repo **CTSV**, không cần thư mục PBL6 của người viết. Dùng để tái tạo dữ liệu SYNTHETIC DEV cho giảng viên/Trưởng khoa trên máy từng thành viên. Schema đi qua Flyway; seed DEV chạy riêng và có version/checksum, không phải migration production.

## Chuẩn bị máy thành viên

- Windows, PowerShell 7, Java 21, SQL Server DEV2022 theo runbook chung.
- Checkout CTSV có backend SHCN; chạy setup CTSV và SIS theo [runbook](../../../docs/team-handover-p00-p04/README.md). `ctsv_dev` phải có V15, `sis_dev` có schema Đào tạo. Không gọi setup lặp để reset dữ liệu đã làm.
- DEV Auth hoạt động, tài khoản `dev.admin` và cấu hình mật khẩu local được setup tạo; alias profile nguồn `SIS_MOCK` đã có. Không chép credentials/keys hoặc UUID/ID từ máy người khác.
- Nguồn SIS đang hoạt động có mã `DEV-SIS-SYNC`, được tạo bởi workflow SIS DEV chung. Tool không tự tạo nguồn thay thế hay đoán ID.
- Hai DB thuộc user SQL hiện tại, compat160/collation Vietnamese_100_CI_AS_SC. Tool từ chối DB sai đích; không dùng trên production.

## Chạy từ root repo CTSV

```powershell
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 check-package
pwsh -File .\tools\dev\shcn\Shcn.Tools.Tests.ps1
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 seed

# Khi cần build/start backend với setting SHCN:
.\dev.ps1 stop
.\dev.ps1 build
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 start

pwsh -File .\tools\dev\shcn\dev-shcn.ps1 accounts
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 smoke
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 status
```

Script tự tìm root từ vị trí trong repo; nếu chạy bản được sao chép ra ngoài có thể truyền `-ProjectRoot <đường-dẫn-checkout-ctsv>`. Không yêu cầu ổ G hoặc username của người viết. Các bước seed/accounts/smoke không tự build/start; start không tự seed/migrate. Backend đang chạy bản/cấu hình khác cần stop/build/start như trên.

## Kết quả mẫu và quyền

| Account | Role nghiệp vụ | Scope | Quyền của slice hiện tại |
| --- | --- | --- | --- |
| dev.giangvien | shcn_giang_vien | DEV-SHCN-KHOA-A | Vào CTSV, không lập phân công khoa. |
| dev.truongkhoa | shcn_truong_khoa | DEV-SHCN-KHOA-A | Read/prepare nháp phân công khoa A, không ký/gửi và không truy cập khoa B. |

Mật khẩu hai account mới dùng mật khẩu **dev.admin local trên máy đang setup**; không có password cố định trong gói này. Ba account cũ không bị reset. Seed tạo 2 khoa, 2 chương trình, 3 lớp, 3 cán bộ/công tác; tên/mã có DEV-SHCN. Nháp minh họa được tạo qua API khi smoke lần đầu. ID/UUID sinh riêng trên từng máy; tra bằng mã và ledger, không yêu cầu ID giống máy tác giả.

Auth business role `shcn_truong_khoa` / `shcn_giang_vien` được gán qua API Auth có audit và thu hồi token cũ. Role Foundation cùng mã lưu scope khoa. Hai role catalogue có PK độc lập, nối bằng mã canonical chính xác. Tài khoản mới có thêm CTSV_USER để vào hệ thống. Sau lần nâng cấp/gán role đầu tiên phải đăng nhập lại.

`HoSoCanBo.MaHS → staff_code → can_bo.ma_cb` đã chốt. Staff API/P15 vẫn chưa hoạt động: fixture mapping rõ ràng không phải bằng chứng pipeline tự đồng bộ cán bộ đã triển khai. UI SHCN chưa nằm trong gói này; kiểm bằng backend API.

## Role–permission từ DB

- Auth có `dbo.VaiTro`, `dbo.ChucNang`, `dbo.VaiTro_ChucNang`, `dbo.TaiKhoan_VaiTro`; có ánh xạ role→permission và được Auth sử dụng.
- Foundation có `dbo.vai_tro` và `dbo.phan_quyen_nguoi_dung`: gán business role theo user/khoa/phân hệ/hiệu lực. `vai_tro` có dấu gạch dưới là bảng khác với Auth `VaiTro`.
- **SHCN đọc read/prepare từ VaiTro_ChucNang → ChucNang**, đồng thời kiểm role Auth được gán cho đúng tài khoản và Foundation scope mang đúng mã role ấy. Không ghép permission của role A với scope role B.
- V15 seed hai permission chính thức, mặc định Trưởng khoa có cả hai; giảng viên không có quyền phân công khoa. V15 không chứa account/password hoặc scope DEV, không tạo bảng/cột mới.
- `prepare` bao gồm read. Bỏ permission/đổi trạng thái permission/role trong DB có hiệu lực ở request tiếp theo, không cần sửa cấu hình/restart. Cấp/thu hồi role qua Auth API thu hồi token cũ theo cơ chế chung.
- `shcn.policy.json` chỉ còn setting nguồn/mapping/công tác DEV, không cấp quyền. Các role-list cũ bị loại khỏi nguồn quyết định permission. UI quản trị permission SHCN chưa được xây trong gói này.

Chạy [RBAC_INSPECT.sql](RBAC_INSPECT.sql) để xem các bảng quyền và metadata version trên DB local, chỉ đọc.

## Version, checksum và cách chia sẻ

`seed-manifest.json` có package ID `ctsv.shcn.dev`, version `1.1.0`, mã fixture và SHA-256 các file seed/chính sách. Hash chuẩn hóa LF/CRLF để clone Git trên Windows không làm sai checksum. Tool kiểm checksum trước khi đọc cấu hình DEV hoặc kết nối SQL.

Sau seed thành công, **cả sis_dev và ctsv_dev** có extended property `shcn_dev_seed_package_v1`, ghi package ID/version/manifest hash/source code. Không thêm bảng hoặc sửa Flyway history cho seed. Các ledger `shcn_dev_catalog_v1`, `shcn_dev_identities_v1`, `shcn_dev_draft_v1` giữ ID được tạo trên đúng máy. Có thể nhận fixture legacy v1 chưa có package metadata sau khi kiểm ownership. Seed lại không nhân bản hay khôi phục quyền/nháp mà người dùng đã thay đổi.

File source/manifest/README này cần được nhóm review và **commit vào repo CTSV** qua quy trình của nhóm. Không commit dev-home, password/token/key, JSON kết quả local hoặc DB dump. Gói được chuẩn bị trong working tree; công cụ không tự commit/push thay người dùng. Thành viên khác chỉ nhận được sau khi nhóm chia sẻ branch/commit.

Kết quả local nằm tại `<dev-home>/shcn/evidence/`, setting không secret tại `<dev-home>/shcn.settings.json`. `status` trả version/checksum và ID local; kết quả smoke không chứa token/password.

## Nâng cấp bộ 1.0.0 hiện có

Chỉ nhận 1.0.0 có manifest hash `3F86E56BDCE0F0A398C5E5A3A4FFE7F6B5208F707DC9C3A1488B63F38A584649`. Không dùng seed thường để nâng cấp ngầm.

```powershell
.\dev.ps1 stop
.\dev.ps1 build
.\dev.ps1 migrate
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 start -UpgradeBootstrap
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 upgrade
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 smoke
pwsh -File .\tools\dev\shcn\dev-shcn.ps1 status
```

`-UpgradeBootstrap` chỉ cho khởi động với metadata parent đã được chấp nhận và V15 đã áp dụng; chưa nâng version/gán role. `upgrade` kiểm ownership/profile/scope của cả hai account trước khi gán business role qua Auth API, rồi ghi version 1.1.0 trên cả DB. Không đổi password, ID, scope hoặc nháp. Nếu bị dừng giữa hai DB, chạy lại upgrade hỗ trợ resume trạng thái parent/current hợp lệ. Quyền/role đã bị quản trị cố ý thu hồi sau provisioning không bị seed thường khôi phục.

## Khi có thay đổi seed tiếp theo

Không sửa nội dung phiên bản đã chia sẻ rồi giữ nguyên version. Tạo version mới, changelog, checksum và lệnh upgrade có guard/no-overwrite rõ ràng. Tool **dừng khi metadata DB khác version/hash**, ngoại trừ action upgrade tường minh cho parent đã liệt kê; không clean/reset. Không chạy DELETE/reset để né lỗi version.

Đã có kiểm package và chạy lại trên DEV tác giả; việc setup trên máy thứ hai phải ghi kết quả riêng, không suy PASS từ máy tác giả.
