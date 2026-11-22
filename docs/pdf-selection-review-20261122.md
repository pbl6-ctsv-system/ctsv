# Rà soát thông tin hồ sơ và lựa chọn PDF — 22/11/2026

## Phạm vi và thay đổi

- Owner yêu cầu bỏ thông tin SV lặp ở chuyên viên, giữ bảng đối chiếu với Đào tạo; chọn giấy tờ nào thì vùng dưới phải hiển thị đúng PDF của giấy tờ đó, không giới hạn hai file.
- `DeclarationSummary.tsx`: thêm cờ trình bày `showClaims`, mặc định true. `IntakeWorkspace.tsx` truyền `showClaims={student}`. Chuyên viên không còn khối Thông tin đã kê khai; giữ Diện và học kỳ, lời khai điều kiện/người liên quan và bảng đối chiếu. Sinh viên vẫn thấy kê khai của mình, không thấy bảng đối chiếu nội bộ.
- Không sửa Backend, contract, schema, `PdfPreview` hoặc quy tắc nộp trùng. Owner đã yêu cầu bỏ qua APPLICATION_EXISTS.

## Kết quả kiểm tra PDF trên DEV thật

- URL `https://127.0.0.1:5173`, launcher DEV/SQL Server `ctsv_dev`; không mock tải PDF.
- Hồ sơ10024: ba giấy tờ đều trỏ file10028. Đổi loại giấy vẫn cùng nội dung là đúng với liên kết hiện có; không tự sửa bản nộp đã từ chối.
- Dùng hồ sơ10025 đã tồn tại với ba mục giấy tờ, file10029/10030/10030. File10029 là `bien_ban_hop_xet_MGHP_du_lieu_gia_lap.pdf`, SHA-256 `80fde52411515a34f00cb90bba84991e47b5248f18caf76e07b76d362966c2e3`; file10030 là `du lieu test.pdf`, SHA-256 `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`. Đã đối chiếu hai hash với file gốc local.
- Browser click từng mục, đổi thứ tự 1→2→3→1→2→3→2→1: **8/8 assertion PASS**; hash bytes của blob đang gắn vào iframe khớp đúng file của mục được chọn, trạng thái nút đúng. Không chỉ dựa screenshot hoặc việc iframe tồn tại.
- Bấm nhanh1→2→1→3: **PASS**, nội dung cuối đúng mục3. Viewport360px: **PASS**, không tràn ngang trang. Hai kiểm tra hiển thị theo role: chuyên viên bỏ lặp/giữ đối chiếu và sinh viên giữ kê khai/không lộ đối chiếu đều PASS.
- Chưa tái hiện lỗi chọn sai/không tải PDF bằng dữ liệu khác nội dung trong lượt này, nên không sửa loader khi chưa có căn cứ. `PdfPreview` theo `fileId`, danh sách không hardcode số lượng; chưa có bằng chứng E2E cho bốn PDF khác nội dung.

## Lệnh và evidence

Cwd `ctsv/frontend`: `npm.cmd run build` (TypeScript + Vite) PASS; `node --test tests/dossier.test.mjs`:4PASS,0FAIL/SKIP. Playwright MCP chạy trên DEV, không chạy lại toàn P05–P08. Trước sửa, browser assertion đã tái hiện khối kê khai bị lặp ở chuyên viên; sau sửa assertion tương ứng đạt.

Evidence local ngoài Git: `frontend/target/pdf-selection-review-20261122/report.json`, `pdf-a.png`, `pdf-b.png`. Report phân biệt assertion dữ liệu đang xem với ảnh chụp. Không chứa token hoặc URL Cloudinary có chữ ký.

## Giới hạn và thao tác dữ liệu

Ban đầu phiên browser hết hiệu lực/thay đổi ở tab khác gây401/REFRESH_INVALID; có lỗi locator trong script. Không tính các lần đó là upload/PDF PASS. Sau xác thực lại, tiếp tục đúng trạng thái. Thử chuẩn bị thêm fixture qua API bị409 SUBMISSION_NOT_ALLOWED vì file đã được dùng ở hồ sơ khác; không bỏ guard hoặc sửa dữ liệu để ép chạy. Đã đọc lại trạng thái rồi tái sử dụng hồ sơ10025. Không tạo đợt mới, không sửa/xóa hồ sơ có sẵn hoặc file Cloudinary trong lượt này. Không commit/push.
