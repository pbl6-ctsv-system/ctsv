# P11 — Prototype để owner duyệt

- Chạy `npm ci --ignore-scripts`, `npm start` từ thư mục này.
- Prototype: <http://127.0.0.1:5178/>.
- Toàn bộ **70 ảnh theo trình tự**: <http://127.0.0.1:5178/review.html> hoặc mở `review.html` để xem ảnh offline.
- Ảnh từ01–61; các bước ký có ảnh con xác nhận → loading → kết quả, nên tổng70ảnh.
- Chuyển góc nhìn CV/PP/PHT ở thanh demo phía trên. “Đặt lại bản demo” chỉ xóa fixture synthetic trong localStorage của prototype này.

Đây là giao diện riêng, **không gọi backend/CA/Cloudinary**. Dấu trên PDF và file tải xuống là **MÔ PHỎNG**, không phải chữ ký số. Backend có evidence REAL riêng trong báo cáo P11. Không có Từ chối ký theo quyết định owner mới nhất23/11.

Thứ tự ảnh:01–15 CV chuẩn bị/ký BB;16–19 PHT ký BB;20–24 CV chuẩn bị/chuyển QĐ;25–29 PP xem BB/ACK/ký QĐ;30–37 PHT ký/xem kho/lịch sử/mobile;38–43 thay bản chuẩn bị/crop/rotation;44–52 stale/lỗi/unknown/recovery;53–54 mobile chọn vùng;55 danh sách rỗng;56–57 cho phép ký lại sau khắc phục nhưng không tự ký;58–61 chuyển trang/PDF gốc/tải lịch sử.

Browser scripts `walkthrough.mcp.js`, `faults.mcp.js` chạy qua Playwright MCP. Tổng57assertion được ghi nhận (22luồng tiếp nối +25fault +2filter +2fresh consent +4viewer +2nhãn phase nhận/lưu); `screenshots/browser-result.json` phân biệt phần mô phỏng. Không dùng kết quả này thay E2E React/backend/SQL REAL trên ctsv_dev.

PDF.js6.5.438 hiển thị canvas/viewport; [tài liệu Mozilla](https://mozilla.github.io/pdf.js/examples/) mô tả transform giữa PDF và canvas. pdf-lib1.17.1 **chỉ ở prototype** để lưu dấu minh họa vào PDF download; backend ký thật không dùng nó. Không thêm package/router vào frontend chính trước approval.

**Gate:** chờ owner duyệt prototype trước khi tích hợp React. Chưa nghiệm thu P11-99.
