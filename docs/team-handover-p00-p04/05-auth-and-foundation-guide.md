# 05 — Dùng Auth và Foundation chung

[Mục lục](README.md) · [Kiến trúc](04-architecture-and-integration.md) · [Troubleshooting](07-troubleshooting.md)

**Phạm vi:** Auth/Foundation P03/P04 đã nghiệm thu. Dùng ba tài khoản synthetic theo [02](02-installation-guide.md); rủi ro preset được owner chấp nhận, không gồm key/token hoặc tài khoản thật.

## Auth không Session

- Access JWT RS25615phút; `iss`, `aud`, `sub`, `iat`, `exp`, `tokenVersion`, header `kid`. Mỗi máy giữ keypair riêng, không sinh lại mỗi restart. Auth DB là các bảng trong datasource CTSV chung.
- Refresh random, rotation một lần, tối đa7ngày, chỉ hash lưu DB. Reuse phát hiện thì revoke toàn tài khoản theo generation; không thêm grace period hoặc blacklist JWT để chữa race.
- Browser giữ access trong memory; refresh là Cookie `ctsv_refresh`, **HttpOnly + Secure + SameSite=Strict**, Path `/api/xac-thuc`. Không lấy refresh bằng JavaScript; không copy Cookie vào hướng dẫn/ảnh/log.
- Web Locks tuần tự hóa các thao tác cookie giữa tab; BroadcastChannel truyền thay đổi trạng thái, không truyền token. `ctsv.auth.uncertain` chỉ là marker không có token. Mất response rotation yêu cầu login lại, không tự retry cookie cũ.
- Logout cookie-only, frontend **không gửi Authorization Bearer**, có CSRF header/Origin như contract. Cookie đã rotate nhưng cùng generation còn hợp lệ vẫn có thể thu hồi generation hiện hành; generation cũ không thu hồi generation mới.
- GET danh mục thông thường kiểm JWT local + quyền/mapping, chấp nhận JWT cũ đến expiration; GET nhạy cảm và mọi ghi kiểm fresh Auth state. Thu hồi quyền MGHP đọc local có hiệu lực ở lần kiểm tiếp theo, không chờ JWT hết hạn.

Một số sync guard dùng SQL `sp_getapplock` với `LockOwner='Session'`: đó là SQL connection lock, **không phải Session đăng nhập**. Không đổi/xóa nó vì Auth đã bỏ Session.

## Các route Auth thực tế

Base /api/xac-thuc; dùng credentials synthetic DEV trên môi trường của mình. 13 route bao gồm:

| Method / suffix | Cách dùng |
|---|---|
| POST `/dang-nhap` | JSON `username,password`, Origin FE đúng, `X-CSRF-Protection: 1`;200 TokenResponse, Set-Cookie |
| POST `/lam-moi-token` | Cookie + CSRF, không body/query;200 access mới + rotate Cookie |
| POST `/dang-xuat` | Cookie + CSRF, không Bearer/body/query;204 revoke/no-op theo contract |
| GET `/toi` | Bearer;200 thông tin tài khoản, không tự có hồ sơ SV |
| GET `/tai-khoan/{idTaiKhoan}` | Admin account management, AccountAdmin + ETag |
| POST `/tai-khoan` | Admin tạo account và profileLinks đã xác minh;201 |
| GET `/phan-quyen` | Capability RBAC; query accountId khi cần account assignments |
| PUT/DELETE `/tai-khoan/{idTaiKhoan}/vai-tro/{idVaiTro}` | Admin, If-Match, audit/version/revocation |
| PATCH `/tai-khoan/{idTaiKhoan}` | Thay profileLinks có review mapping; If-Match |
| PUT `/tai-khoan/{idTaiKhoan}/trang-thai` | Admin khóa/mở;200 + ETag |
| PUT `/tai-khoan/{idTaiKhoan}/mat-khau` | Admin reset, policy≥12ký tự;204 + ETag |
| GET `/noi-bo/tai-khoan/{idTaiKhoan}/trang-thai-bao-mat` | AUTH11: service key/capability riêng; không gọi từ frontend |

Login JSON và response phải theo contract; không ghi JWT vào disk để tiện thử. Lỗi login có thể bị limiter, không brute-force hoặc reset DB. Auth roles `AUTH_ADMIN`,`CTSV_USER` khác local roles của Foundation. Account admin không tự thành chuyên viên toàn trường.

## Foundation12 API thực tế

Base `/api/ctsv/nen-tang`; Bearer, ID **local CTSV**. Các bảng nguồn/field thêm từ SIS không tự được đưa hết vào DTO.

| Method / suffix | Quyền và version |
|---|---|
| GET `/sinh-vien` | Chuyên viên MGHP; q/page/size; projection không CCCD |
| GET `/sinh-vien/{idSinhVien}` | Chuyên viên; chi tiết Foundation, không hồ sơ PII tùy ý |
| GET `/danh-muc` | SV đã mapping hoặc chuyên viên; kind ethnicities/cohorts/classes/units/semesters; GET thường local |
| GET `/hoc-ky/{idHocKy}` | Chuyên viên; ETag representation |
| PUT `/hoc-ky/{idHocKy}/xac-minh` | Căn cứ/order/startDate/endDate; If-Match; chặn in-use |
| GET `/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su` | Chuyên viên; ETag aggregate |
| PUT `/qua-trinh-hoc/{idQuaTrinhHoc}/xac-minh` | Căn cứ, local FK; If-Match; giữ mã quá trình |
| POST `/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su-trang-thai` | If-Match, không chồng sai,201 |
| POST `/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su-lop` | If-Match, local class FK,201 |
| GET `/nguoi-dung/{idNguoiDung}/vai-tro-mghp` | Local capability `ctsv.roles.manage`, ETag tập quyền |
| PUT `/nguoi-dung/{idNguoiDung}/vai-tro-mghp/{idVaiTro}` | Cấp allowlist MGHP, If-Match, no-op không trùng |
| DELETE `/nguoi-dung/{idNguoiDung}/vai-tro-mghp/{idVaiTro}` | Thu hồi giữ lịch sử, If-Match |

SV không dùng API danh sách để xem SV khác. GV/CVHT có hồ sơ cán bộ không được cấp quyền MGHP mặc định. Chuyên viên MGHP quyền toàn trường; không tự thêm ràng buộc khoa/thời hạn. ETag không phải raw rowversion để client tự tạo; đọc GET hiện hành, giữ nguyên tag vào `If-Match`;412 tải lại,428 thiếu tag. Một số PUT chuyển đổi dữ liệu không trả ETag mới, phải GET lại.

## Quy trình thử qua UI sau onboarding

1. Cwd `ctsv`, start launcher; mở FE bằng127.0.0.1. Nhập credential cá nhân được chuẩn bị hợp lệ, không lấy password công khai từ repo để coi là onboarding đã đạt.
2. SV: tab Danh mục, tra Học kỳ; expected200/projection. Thử tra danh sách SV khác phải403, không phải lỗi cần cấp admin cho tất cả.
3. Chuyên viên: tra SV giả theo MSSV/ID trong `identities.json`; xem quá trình và kỳ chưa xác minh. Dữ liệu seed không tự là lịch học chính thức.
4. Quản trị: xem RBAC/account; kiểm ETag khi đọc. Quản trị Auth và quản trị quyền CTSV được gán riêng bởi setup, không suy từ UUID hoặc email.
5. Kiểm Network/DevTools: HTTPS không warning; Cookie HttpOnly/Secure/SameSite/Path đúng; access không nằm storage; không chụp màn hình response chứa accessToken.
6. Reload rồi “Tiếp tục đăng nhập”; mở tab thứ hai, refresh tuần tự không reuse. Logout từ một tab khiến tab khác về màn hình login; request logout không Bearer.
7. Nếu muốn kiểm sửa kỳ/quyền: chỉ dữ liệu SYNTHETIC của máy mình, đọc tag mới trước ghi. Không thao tác hồ sơ thật hoặc dùng sửa quyền làm cách chữa mọi403.

## Dùng interface trong backend module khác

Public signatures có thật:

```text
AuthAccess.verifyAccessToken(String) -> Principal(issuer, accountId, tokenVersion)
AuthAccess.requireCtsvAccess(Principal, UUID mappedAccountId) -> State
FoundationAccess.authorize(Principal, boolean online, String capability) -> Identity
FoundationUsage.semesterUsed(long) / processUsed(long) -> boolean
```

Mapping tin cậy: `dinh_danh_dang_nhap.ma_nha_cung_cap` + `ma_chu_the_dang_nhap` verified → `nguoi_dung.id`; đối soát issuer/sub, active/local quyền trong transaction. Không truyền accountId từ body để xác định owner. `profileLinks` source/type/sourceId là liên kết nguồn được admin xác minh, không tự đổi owner từ email/MSSV/source PK.

`FoundationAccess` cần transaction local; fresh Auth gọi transaction committed riêng theo implementation. Không giả định toàn bộ Auth+nghiệp vụ cùng atomic transaction chỉ vì chung DB. Các race nghiệp vụ sau phải theo contract riêng. Không import package-private Auth repository/credentials/service hoặc Foundation repository xuyên module.

Chưa có public data projection service chung đủ cho mọi module. Cần interface mới thì phối hợp trong task riêng, không công bố một API/class chưa tồn tại. Không tạo Auth riêng, JWT riêng, bảng role riêng hoặc bản sao Foundation cho mỗi thành viên.
