# P11 — sử dụng và kiểm chứng DEV

Runtime: `https://127.0.0.1:5173/mghp/ky-so`, backend `https://127.0.0.1:8443`, SQL `localhost\DEV2022/ctsv_dev`, Flyway V24. Xem [báo cáo nghiệm thu](../../nghiepvu/09-trien-khai/p11-execution-report.md).

- Chuyên viên: `dev.chuyenvien`, chuẩn bị bộ văn bản/BB và gửi QĐ. Chọn bộ **Hội đồng MGHP học kỳ I · Thực hành P10** để xem biên bản và quyết định đã ký đủ.
- Phó phòng: `dev.p11.phophong`, ký bước đầu QĐ và xem BB liên quan được giao.
- Phó Hiệu trưởng: `dev.p11.phohieutruong`, ký bước cuối của từng văn bản được giao.
- Sinh viên/Admin Auth không có quyền ký MGHP tự động.

Hai tài khoản P11 là **TEST synthetic**. Cấu hình dùng chứng thư E cho CV/PP và C cho PHT theo owner; không chứng minh chức danh/thẩm quyền Production. Không chia sẻ mật khẩu trong tài liệu. Credential PP/PHT được DPAPI lưu trong `p11-accounts.dpapi.json` tại DEV home ngoài Git; credential CA ở thư mục local `CTSV/ca-dut`. Launcher chỉ đưa bí mật vào environment của backend, không ghi vào properties/log/source.

## Thao tác chính

1. CV mở Tài liệu ký số → Chuẩn bị bộ văn bản → chọn bộ đã chốt → tải PDF BB lên, xác nhận đã đối chiếu.
2. Chuẩn bị ký BB → chọn trang → Chọn vị trí ký → kéo khung (di chuyển/đổi kích thước nếu cần) → Ký số → Xác nhận ký.
3. Đợi CTSV nhận, kiểm và lưu PDF; PHT tự mở bản đã có chữ ký CV và thực hiện bước của mình.
4. CV tải PDF QĐ và Gửi Phó phòng ký khi BB đã hoàn tất. PP xác nhận đã xem trong hộp ký, sau đó PHT tự ký bước cuối.
5. Mở Lịch sử & phiên bản để xem/tải PDF gốc, sau người1 và sau người2. Đã ký xong chưa phải đã công bố.

PDF từ kho CTSV qua backend kiểm quyền. Không có Từ chối ký/thu hồi tài liệu/P12/P13 trong P11. Lịch ký là kế hoạch mềm; không tự ký theo giờ. Khi chưa rõ kết quả, đọc lại/kiểm tra; không bấm ký lại để thử.

## Kiểm thử có thể chạy lại an toàn

- `frontend`: `npm.cmd run build`; `node --test tests/mghp-signing.test.mjs tests/mghp-decisions.test.mjs tests/student-journey.test.mjs`.
- `frontend/e2e/p11-login.cjs`: đăng nhập một lần qua DPAPI, giữ phiên trong RAM, không lưu storageState. Cần biến `CTSV_PLAYWRIGHT_MODULE` trỏ thư viện Playwright đã cài và `NODE_EXTRA_CA_CERTS` trỏ cert DEV hiện hành.
- `frontend/e2e/p11-acceptance.mcp.js`: regression đọc/xem/tải/quyền/mobile trên dữ liệu DEV đã nghiệm thu; không ký CA lại. Không gọi lại script login khi phiên RAM còn dùng được.
- `tools/ca/Prepare-P11Dev.ps1`: setup hai actor TEST có guard/idempotency và GET danh mục CA, không upload/ký. Không reset mật khẩu tài khoản đã tồn tại.
- `tools/ca/Invoke-P11Real.ps1`: probe riêng có intent guard. Không xóa guard hoặc chạy lại để né timeout/unknown. Không dùng probe này thay bằng chứng Playwright DEV.

Upload chưa rõ kết quả bị giữ intent marker không chứa credential trong `sessionStorage`. Chuyên viên phối hợp kỹ thuật đối chiếu ledger/kho tệp trước khi cho tải lại; không xóa marker để thử mù. Nếu bind PDF bị412, đọc lại bộ, đối chiếu và xác nhận lại; file đã upload được tái sử dụng cho lần bind tiếp theo.

Sau lượt kiểm23/11: request1/2 đã hoàn tất BB/QĐ, request3 là bản synthetic đã bị thay thế, request4 là kiểm chữ ký thật trang2 và đang chờ PHT. Tất cả giữ lịch sử; chưa dọn/xóa dữ liệu hoặc file evidence.
