# 02 — Setup trên máy thành viên

[Mục lục](README.md) · [Dependencies/Git](08-git-push-safety-audit.md) · [Chạy hằng ngày](03-daily-operations.md)

**Runbook từ source hiện hành.** B02 đã được owner chấp nhận cho credentials synthetic; SIS DEV có công cụ riêng ở [06](06-sis-ctsv-sync-guide.md). Source cần owner commit/push trước clone. Máy mới vẫn NOT VERIFIED.

## 1. Nhận source đầy đủ

Sau khi owner review và cho phép phát hành các commit mới, chọn thư mục cha do bạn quản lý, ví dụ `C:\work\pbl6`:

```powershell
# cwd: thư mục cha của workspace trên máy thành viên; ví dụ sau khi source được phát hành
git clone https://github.com/pbl6-ctsv-system/ctsv.git ctsv
git clone https://github.com/pbl6-ctsv-system/daotao.git daotao
# Chỉ cần khi làm Tài chính; chưa là dependency chạy Auth/Foundation:
git clone https://github.com/pbl6-ctsv-system/ketoan.git taichinh
```

**Chưa có branch/SHA phát hành P00–P04 được xác minh.** Owner phải cung cấp cặp commit CTSV/SIS tương thích ở vòng sau. Không dùng lệnh checkout feature branch remote chưa được xác minh tồn tại. HEAD local hiện tại chỉ có starter, xem ma trận [08](08-git-push-safety-audit.md). Trước setup: kiểm `git rev-parse HEAD`, `git status --short`, các file `dev.ps1`, `tools/dev/Dev.Common.ps1`, `tools/dev/java/pbl6/ctsv/auth/DevDatabase.java`, `frontend/package-lock.json`, migrations V1–V7 phải có thật.

```text
<workspace>/
  ctsv/       # backend + frontend + docs này
  daotao/     # SIS backend
  taichinh/   # tên local tùy chọn cho repo ketoan
  nghiepvu/   # chỉ nhận gói tài liệu/contract/evidence đã lọc riêng nếu cần phát triển/review
```

`nghiepvu` hiện không có Git repository đã xác minh. Không bịa URL clone cho nó. Runtime launcher CTSV không cần đọc cả thư mục này; source đã có schema/catalog trong classpath. Những tài liệu thiết kế/Test Strategy được source AGENTS tham chiếu cần owner đóng gói bản đã duyệt có checksum, không gửi toàn bộ `.analysis`/CSV/log chưa lọc.

## 2. Dependency — trước khi setup offline

Cwd `ctsv`, với source đầy đủ, Java21/PATH/internet đã chuẩn bị:

```powershell
.\mvnw.cmd -v
.\mvnw.cmd -B -ntp '-Dmaven.test.skip=true' package dependency:build-classpath
Set-Location frontend
npm.cmd ci
Set-Location ..
```

Lệnh Maven online này là bước chuẩn bị đề xuất từ implementation, **SOURCE-INSPECTED, chưa thử cold-cache trong audit**. Nó không chủ động start app hoặc chạy test. Nếu proxy/repository thiếu, dừng và xử lý mạng/cấu hình Maven riêng; không chuyển sang dependency/phiên bản khác. Khi cần Browser E2E: cwd `ctsv/frontend/e2e`, `npm.cmd ci`; dùng Chrome đã cài, Playwright1.56.1 từ lockfile. Không cần package E2E để dùng UI hằng ngày.

Với SIS, cwd `daotao`: kiểm wrapper/version và package theo POM bằng lệnh Maven tương tự khi tới phần sync; chưa dùng test harness làm server. Chuẩn bị DLL Windows Authentication theo [01](01-prerequisites.md). Expected DLL đúng chỗ và cùng x64 với JVM.

## 3. SQL Server trước khi có bất kỳ lệnh ghi

Trên máy mới của bạn, mở SSMS bằng Windows Authentication vào `localhost\DEV2022`. Chỉ truy vấn xác minh:

```sql
SELECT @@VERSION AS sql_version,
       SERVERPROPERTY('InstanceName') AS instance_name,
       SUSER_SNAME() AS windows_login;
SELECT name, compatibility_level, collation_name, state_desc,
       SUSER_SNAME(owner_sid) AS database_owner
FROM sys.databases WHERE name=N'ctsv_dev';
```

Instance phải là DEV2022, engine16.x. `ctsv_dev` chưa tồn tại là bình thường; **không tự tạo bằng DDL chạy rời** rồi đánh dấu migration xong. Setup hiện tạo DB khi chưa có, compat mặc định phải160, collation Vietnamese_100_CI_AS_SC; xác minh default SQL2022 đáp ứng. Nếu DB tồn tại: đúng login owner, marker/schema/history phải được kiểm bởi tool trước migrate. Không nhận bừa DB có dữ liệu; không sửa owner, history hoặc checksum để vượt guard.

Xác minh TCP/IP/IPAll dynamic port trong SQL Server Configuration Manager/registry theo [01](01-prerequisites.md). Bật service/TCP do chủ máy thực hiện có kiểm soát, không dùng cổng56826 của tác giả. Mỗi người dùng local DB riêng trên máy mình; không trỏ tới SQL Server của người khác.

DB evidence `sis_test_20261111`, `ctsv_test_20261111`, các DB `p01/p02/p03/p04` không phải target setup. Không sửa tên guard/marker của test để biến chúng thành dev.

## 4. Credentials DEV — owner đã chấp nhận

**OWNER-ACCEPTED DEV CREDENTIAL RISK**, chỉ trong private repo và database ctsv_dev do setup quản lý:

| Vai trò | Username | Mật khẩu synthetic DEV |
|---|---|---|
| Sinh viên | dev.sinhvien | CtsvDev@2026! |
| Chuyên viên | dev.chuyenvien | CtsvDev@2026! |
| Quản trị tài khoản | dev.admin | CtsvDev@2026! |

Đây không phải tài khoản thật/production. Argon2id, RBAC, profileLinks và quyền CTSV giữ nguyên. Admin tài khoản không mặc nhiên có quyền chuyên viên MGHP. Setup kiểm account/mapping/password trước seed; chạy lại với credentials đúng là no-op, không nhân đôi/reset. Nếu mật khẩu đã đổi thủ công không còn khớp config thì dừng, không ép reset DB.

Quyết định không áp dụng cho TLS/JWT private key, SIS service key, DB credentials hoặc tokens. Những giá trị đó được tạo riêng ngoài Git. Không copy DPAPI/config tác giả.

## 5. Setup CTSV và review chứng thư riêng

Cwd `ctsv`, PowerShell7 đã cài (entrypoint từ5.1 tự chuyển nếu tìm được runtime):

```powershell
.\dev.ps1 setup
.\dev.ps1 certificate
```

Hiện `setup` sẽ:

1. Tạo thư mục `%LOCALAPPDATA%\CTSV\dev\<mã-checkout>` và ACL cho SID hiện tại; mã phụ thuộc đường dẫn checkout, không dùng mã cá nhân trong README cũ.
2. Tạo leaf RSA2048, CA=false, SAN IP127.0.0.1, EKU Server Authentication,180ngày vào Personal; xuất `https.pfx`, `https-cert.pem`, `https-key.pem`. **Chưa trust Root**.
3. Tạo khóa JWT RSA3072 riêng `jwt-private.pem`/`jwt-public.pem`, issuer local8443, kid local-dev; không tái tạo khi config đã có.
4. Build không chạy test; compile helper ở ngoài app source; đọc current dynamic SQL port; kết nối master để xác minh/create đúng `ctsv_dev`, rồi kiểm owner/compat/collation/marker.
5. Validate history trước khi migrate; áp dụng migrations CTSV V1–V7 hiện có, không sửa chúng, không clean/baseline-on-migrate. Dựng runtime properties ngoài Git.
6. Bootstrap admin qua `AuthAdministration.bootstrap`; cấp Auth role và identity/local permissions tường minh; tạo SV/cán bộ/đơn vị/kỳ/quá trình DEV hợp lệ. Account UUID, local user/student PK độc lập. Xem `identities.json` để biết ID thực; không hardcode số ID.

Nếu lỗi giữa setup, giữ file/log và trạng thái DB để chẩn đoán; không xóa config/keys/DB rồi chạy lại như cách chữa chung. Tool có nhiều transaction, không hứa toàn bộ setup là một transaction. Lỗi sau commit DB nhưng trước ghi config có thể cần đối soát có kiểm soát; không sửa password bằng SQL thủ công để che lỗi.

Kiểm trên **chứng thư của bạn**: Subject/Issuer, SAN127.0.0.1, Basic Constraints CA=false, EKU, hạn dùng và thumbprint. Chỉ sau khi đồng ý, thay placeholder bằng đúng thumbprint vừa xem:

```powershell
.\dev.ps1 trust -ConfirmThumbprint '<THUMBPRINT_LEAF_CUA_BAN>'
```

Lệnh không có dấu `< >` trong giá trị thực. Không dùng thumbprint cũ của tác giả. Windows có thể yêu cầu chủ máy xác nhận. CurrentUser Root là trust store, không có hostname-scoped trust; leaf SAN/CA=false giới hạn công dụng của cert, không biến nó thành CA. Không tắt SAC hoặc TLS validation.

## 6. Kiểm DB/khóa trước start

SSMS chọn đúng **ctsv_dev** trên máy mình, chỉ đọc:

```sql
SELECT DB_NAME() AS target, SERVERPROPERTY('InstanceName') AS instance;
SELECT name,CONVERT(nvarchar(128),value) AS marker
FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner';
SELECT version,description,checksum,success FROM dbo.flyway_schema_history
ORDER BY installed_rank;
SELECT COUNT(*) AS synthetic_accounts FROM dbo.TaiKhoan
WHERE TenDangNhap IN (N'dev.admin',N'dev.sinhvien',N'dev.chuyenvien');
```

Expected V1–V7 success, không duplicate/failed/checksum mismatch,3account dev. Không SELECT `MatKhauHash`, refresh hash hoặc token để paste vào report. Schema107bảng Auth+CTSV baseline còn có bảng kỹ thuật sync; không suy số bảng từ ERD cũ thay cho catalog/migration actual.

JWT/private HTTPS key phải ở thư mục local có ACL; không gửi key qua Git/chat. DPAPI config chỉ dùng trên Windows account đã tạo nó. Đổi đường dẫn checkout sẽ tạo mã thư mục khác: phải có kế hoạch giữ cấu hình/credential đúng ownership, không giả định clone sang đường dẫn khác tự nhận config cũ.

## 7. Hoàn tất và bảo trì

Sau các cửa chặn: theo [03](03-daily-operations.md) để start, [05](05-auth-and-foundation-guide.md) để kiểm login/API. Start không migrate hoặc seed; thay migration cần thao tác explicit được owner duyệt, không dùng launcher start để tự nâng DB.

Gia hạn certificate hiện **chưa có lệnh renew được triển khai**. Giữ DB/JWT/Accounts, stop có kiểm soát, tạo leaf thay thế bằng công cụ Windows với cùng SAN/CA=false, xuất HTTPS files và cập nhật Thumbprint/PFX DPAPI; review rồi trust thumbprint mới, kiểm HTTPS rồi gỡ đúng cert cũ. Đây là việc vận hành thủ công **NOT VERIFIED**, nên đề nghị bổ sung quy trình/command an toàn ở vòng sau. Không xóa config để ép sinh mới vì có thể mất credentials/issuer mapping.

Khi ngừng dùng: xem thumbprint của máy mình, dừng launcher, dùng `certutil -user -delstore Root <thumbprint>` và gỡ đúng Personal cert/private key theo metadata đó. Xác minh Root/My đều không còn cert mục tiêu. Không xóa cả store, không drop DB; exported key files vẫn cần quản lý/xóa đúng phạm vi khi chắc chắn không dùng.
