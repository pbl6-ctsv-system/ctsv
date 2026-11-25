# DEV local: sửa nguồn học kỳ và lời gọi API khoa — 23/11/2026

- DB hiện tại đã khởi tạo lại: nguồn `DEV-SIS-SYNC` bị thiếu; danh mục chỉ có một học kỳ fixture MGHP không gắn nguồn. Launcher thường không nạp `shcn.settings.json`, vì vậy SHCN thiếu cả cấu hình nguồn khi gọi tra cứu.
- Đã khôi phục registry theo địa chỉ provider đã lưu `https://127.0.0.1:9443`, nhập 20 học kỳ từ `KeHoach.csv` đã mapping của owner. Đây là **nhập reference thủ công cho DEV**, không phải đã chạy đồng bộ API Đào tạo. Không sửa dữ liệu SIS, fixture MGHP, schema hay Entity chung.
- Công cụ version 1: `tools/dev/shcn/restore-semester-reference.ps1`. Mặc định chỉ kiểm; `-Apply` mới ghi. Guard instance/DB/owner/marker/collation, chống trùng ID/mã, transaction, ledger SHA256 và audit. Khi dữ liệu đã có hoặc đã bị thay đổi, không tự ghi đè. Chạy lại cùng file đã xác nhận: 0 dòng chèn thêm.
- Mapping: `ID → ma_dinh_danh_ben_ngoai`, `MaKH → ma`, `TenKH → ten`, `NgayDay → ngay_day_nguon`, `TuanDau → tuan_dau_nguon`, `SoTuan → so_tuan_nguon`, `NamHoc → nam_hoc_nguon`. ID local của `hoc_ky` do DB cấp. Không gán mốc bắt đầu/kết thúc đã xác minh cho MGHP.
- Frontend chỉ gọi `/api/shcn/khoa/pham-vi` sau khi Auth hiện hành trả capability `shcn.phanCongKhoa.read` hoặc `shcn.phanCongKhoa.prepare` đang hoạt động, đúng account. Đáp ứng scope vẫn do backend quyết định; không nới RBAC hoặc gán role Trưởng khoa cho chuyên viên.

## Lệnh dùng lại

Tại root CTSV, nhập file `KeHoach.csv` **đã mapping** (các cột như trên), không phải CSV gốc có `IDCode`:

```powershell
pwsh -File .\tools\dev\shcn\restore-semester-reference.ps1 -InputCsv 'DUONG_DAN\KeHoach.csv'
pwsh -File .\tools\dev\shcn\restore-semester-reference.ps1 -InputCsv 'DUONG_DAN\KeHoach.csv' -Apply

# Khi backend đã dừng:
pwsh -File .\tools\dev\shcn\start-specialist.ps1
# Khi cần khởi động lại tiến trình DEV hiện tại:
pwsh -File .\tools\dev\shcn\start-specialist.ps1 -Restart
```

Launcher chuyên viên chỉ nạp nguồn học kỳ SHCN từ cấu hình local đã duyệt, rồi gọi launcher DEV chung. Không migration/seed ngầm; không sửa cấu hình MGHP. Dùng launcher này thay cho `dev.ps1 start` khi thao tác SHCN chuyên viên để tránh mất cấu hình nguồn sau lần khởi động tiếp theo.

## Kiểm chứng lượt sửa này

- 17 ca Node SHCN: đạt; build TypeScript/Vite: đạt.
- 6 kiểm tra Chrome + API thật: menu chuyên viên, form tạo, API học kỳ 200 với 20 dòng, chọn học kỳ, **0 lời gọi tự động tới phạm vi khoa**, backend từ chối tài khoản thiếu quyền và route chuyên viên vẫn chặn sinh viên. Không submit tạo đợt.
- Bộ `module-navigation.test.mjs` có 4 ca lỗi riêng ở các dòng 124, 152, 174, 187 (expectation MGHP/Học bổng); test và các module import của bộ đó không bị thay đổi trong task này. Không báo toàn bộ suite đạt và không sửa module khác để che lỗi.
- Bằng chứng thực tế: `%LOCALAPPDATA%/CTSV/dev/<checkout-hash>/shcn/evidence/2026-11-23-reference-fix/result.json`, `specialist-semester.png`.
- Danh mục khóa/lớp/phân lớp RenLuyen chưa được khôi phục trong task sửa lookup này. Việc chọn học kỳ thành công không chứng minh danh sách sinh viên trễ tiến độ đã đầy đủ.

---

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
