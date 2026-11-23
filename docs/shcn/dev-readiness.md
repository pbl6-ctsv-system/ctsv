# Cấp quyền chuyên viên SHCN trên DEV

Owner đã yêu cầu cấp quyền cho `dev.chuyenvien` và hoàn thiện bước chuẩn bị quyền khi bàn giao các task sau. Ngày22/11/2026.

## Đã thực hiện

- Dùng API quản trị Auth có If-Match để gán role `shcn_chuyen_vien` cho account chuyên viên hiện có. API giữ các role cũ, ghi audit và thu hồi phiên đăng nhập theo cơ chế chung.
- Cấp đúng một global grant Foundation: user đã mapping/xác minh với account và issuer DEV, role canonical `shcn_chuyen_vien`, phân hệ `SHCN`, `don_vi_id=NULL`. Ghi audit cùng transaction với grant.
- Giữ quyền MGHP, tài khoản, mật khẩu và danh mục dữ liệu nguồn. Không chạy migration, tạo bảng hoặc sửa Entity/Foundation/Đào tạo.
- API thật danh sách đợt trả200, `X-SHCN-Can-Prepare=true`; Auth trả đủ `shcn.dot.read`, `shcn.dot.prepare`, `shcn.reference.read`.
- API học kỳ trả200 nhưng **0 học kỳ**. Đây là dữ liệu reference còn thiếu; không giả danh mục hoặc tự coi chức năng tạo đợt đã được nghiệm thu với DB thật.
- Chrome thật qua HTTPS DEV: chuyên viên thấy menu SHCN, mở quản lý đợt và form tạo; sinh viên không vào được URL chuyên viên. Không submit tạo đợt.

Người đang mở phiên `dev.chuyenvien` cần đăng xuất rồi đăng nhập lại vì thao tác gán role làm phiên cũ bị thu hồi.

## Công cụ được lưu trong repo để nhóm sử dụng

Ở root repo CTSV, PowerShell7:

```powershell
pwsh -File .\tools\dev\shcn\seed-specialist-access.ps1 status
pwsh -File .\tools\dev\shcn\seed-specialist-access.ps1 grant
pwsh -File .\tools\dev\shcn\seed-specialist-access.ps1 smoke
```

Package `shcn-dev-specialist-access`, version1. Chỉ tác động account cố định `dev.chuyenvien`, role SHCN trên `DEV2022/ctsv_dev`. Guard owner/marker/compatibility/collation và V18 đã áp dụng. Credential đọc DPAPI cục bộ, không chứa trong source hoặc output. Không tạo account, không đổi password, không gán quyền admin.

Chạy lại không nhân đôi role/global grant. Nếu quyền đã từng được công cụ cấp mà sau đó bị admin thu hồi/đổi, công cụ dừng; không tự khôi phục một quyền đã bị thu hồi. Auth và Foundation có transaction riêng; nếu dừng giữa hai bước, kết quả HTTP kiểm cuối không được coi là PASS cho tới khi cả hai đã đủ.

Bằng chứng runtime an toàn nằm trong `%LOCALAPPDATA%\CTSV\dev\<checkout-hash>\shcn\evidence\specialist-access.json`, `specialist-browser.json`, `specialist-shcn-menu.png`. Không đưa credential/response đăng nhập vào repo.

## Yêu cầu cho các lần bàn giao SHCN tiếp theo

1. Kiểm account đích, role–permission Auth và scope SHCN thực tế, không chỉ thấy role trong migration là coi đã sẵn sàng.
2. Dùng đúng account DEV xác minh API và menu/URL. Kiểm ít nhất một account thiếu quyền. Không mở menu bằng username/role hardcode hoặc nới RBAC để demo.
3. Nếu cần provisioning DEV được owner giao, lưu script/cấu hình có version, guard, audit và kiểm chạy lại không trùng; dữ liệu dùng chính thức vẫn thuộc migration, account/demo không vào migration production.
4. Kiểm lookup/dữ liệu đầu vào. Nếu rỗng, báo chính xác trường hợp đang thiếu. Không báo toàn chức năng sẵn sàng khi chưa có đầu vào cho luồng ghi.
5. Giữ yêu cầu owner: chỉ chạy migrate khi được yêu cầu rõ. Nếu chưa migrate/seed/cấp quyền được phép, ghi cụ thể việc còn thiếu và phạm vi chưa xác minh; không giả kết quả mock là runtime PASS.
