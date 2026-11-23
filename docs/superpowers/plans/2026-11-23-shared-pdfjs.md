# Trình xem PDF.js dùng chung CTSV — Implementation Plan

> Thực hiện tuần tự trong checkout hiện hành; không commit/push. Dùng TDD và kiểm chứng theo tác động.

**Goal:** Thay iframe PDF ở các màn hình hiện hành bằng một viewer PDF.js dùng chung cho xem và chọn vùng ký.

**Architecture:** `shared/ui/pdf` sở hữu transport PDF qua AuthClient chung, PDF.js/worker, canvas, toolbar, vòng đời và geometry. Feature sở hữu danh sách/quyền/lệnh nghiệp vụ. Một entry công khai lazy-load renderer; shared không import feature. Tái sử dụng package PDF.js 6.5.438 đã có.

**Tech Stack:** React 19.2.0, TypeScript 5.9.3, Vite 7.1.12, Node test, Playwright.

**Spec:** Thiết kế đã trình bày trong chat và owner xác nhận không cần tìm kiếm chữ/bôi đen/sao chép ngày 23/11/2026. Xem `frontend/SKILL.md`, `docs/shared-pdf-preview-guide.md`.

## Ràng buộc và interface

- Không sửa backend/API/DB, router/Navbar/theme hoặc nghiệp vụ module khác. Không ký/upload CA để kiểm viewer.
- Public `PdfViewer`: `client`, `fileId`, `title`, `fileName?`, `selectable?`, `selection?`, `onSelectionChange?`. Read-only mặc định; vùng ký chỉ là dữ liệu xem trước.
- `PdfGeometry`: page/x/y/width/height theo PDF points. Giữ nguyên dữ liệu command ký và quyền hiện hành của P11.
- `readPdfBytes(client,fileId,signal)` chỉ GET endpoint hiện có, kiểm MIME và hủy request; không tự refresh, retry hoặc lưu bytes/token vào storage.
- `geometryFromPixels` và `pixelsFromGeometry` độc lập nghiệp vụ, dùng PDF viewport, giữ đúng rotation/crop/scale.
- Download từ bytes của chính tài liệu đã load; không GET thêm hoặc tạo một đường auth khác.

## Review focus

1. A→B→C nhanh: response/render cũ không được hiện dưới tên file mới; download luôn đúng file.
2. React StrictMode, unmount, cancel: hủy loading/render tasks, không render đồng thời vào cùng canvas.
3. Thay metadata/ETag không đổi fileId: không fetch PDF lại. Thay file bỏ vùng cũ; PDF ký mới giữ trang hợp lệ đang xem.
4. Read-only không có công cụ ký; P11 không mất quyền/geometry/PP ACK và không phát lệnh ký ngoài ý muốn.
5. Desktop/mobile, lỗi tải và retry: thông báo tại viewer; không tràn toàn trang, không hiển thị bytes cũ khi lỗi.

## Task 1 — Shared source/geometry (RED → GREEN)

Files: `frontend/src/shared/ui/pdf/{pdf-source.mjs,geometry.mjs,types.ts}`, `frontend/tests/shared-pdf.test.mjs`.

- [x] Viết và chạy test RED: GET đúng endpoint/signal; PDF bytes; MIME sai; lỗi auth giữ nguyên; abort trước/sau đọc bytes; rotation/crop/scale, vòng quy đổi geometry, vùng quá nhỏ.
- [x] Triển khai tối thiểu; chạy lại test tập trung GREEN.

## Task 2 — Renderer và consumers

Files: shared `PdfViewer.tsx`, `PdfDocumentView.tsx`, `PdfViewer.module.css`, `index.ts`; feature `IntakeWorkspace`, `StaffDossierReview`, `ReviewWorkspace`, `SigningWorkspace`; signing `model.mjs`/`types.ts`; bỏ viewer/CSS cũ đã thay hết consumer.

- [x] Tách PDF.js và worker vào renderer lazy-load; toolbar/page/zoom/rotation/download dùng chung.
- [x] Read-only và overlay chọn/kéo/resize dùng cùng canvas; hủy stale requests/renders/geometry.
- [x] Chuyển tất cả consumer hiện có; shared không phụ thuộc feature, giữ quyền/command P11.
- [x] Chạy test model liên quan, typecheck/build.

## Task 3 — Browser, tài liệu và hồi quy

- [x] Cập nhật locator iframe trong E2E hiện có sang canvas đã render, không chỉ kiểm canvas tồn tại.
- [x] Thêm Playwright dùng phiên RAM và dữ liệu cũ trên launcher DEV/ctsv_dev: xem hồ sơ SV/CV/đối chiếu; n PDF khác nhau/đổi nhanh; page/zoom/rotation/download; lỗi/retry; readonly/quyền; chọn vùng ký và mobile, không gửi ký.
- [x] Kiểm hash PDF tải thật, canvas thực tế, screenshot desktop/mobile; STUB fault tách khỏi REAL recovery.
- [x] Đồng bộ hướng dẫn dùng chung và frontend skill; ghi kết quả thật/giới hạn dưới đây.
- [x] Một lượt frontend regression cuối theo các model bị tác động; không chạy toàn bộ backend hoặc CA.

## Evidence / kết quả

**Đã hoàn tất phạm vi được giao.** Không commit/push. Không sửa backend/API/DB/package versions hoặc module khác.

| Kiểm chứng | Kết quả thực tế |
| --- | --- |
| TDD source/geometry | RED do chưa có implementation, sau đó GREEN 10 test; thêm 2 test hồi quy keyboard, tổng shared 12 PASS |
| Frontend model regression | 40 test, 0 fail/skip; gồm shared PDF, P11, review lifecycle/flow/context và student journey |
| Build / typecheck | `npm.cmd run build` PASS; renderer/worker được tách khỏi bundle chính, không cài thêm package |
| Browser DEV | 46 assertion PASS trên Chrome, FE/BE launcher thật và datasource `ctsv_dev`; 0 pageerror, 0 request ghi nghiệp vụ |
| N tài liệu | 5 PDF khác nhau, đổi 6 lần và đổi nhanh; bytes tải đều khớp SHA-256 trước; canvas có nội dung thật |
| Consumers | Sinh viên xem hồ sơ của mình; CV tiếp nhận và đối chiếu bản cứng; CV/PP/PHT xem tài liệu ký đã hoàn tất |
| Quyền | SV direct file404, Admin Auth403; cả hai không vào màn hình ký hoặc hiện viewer trái quyền |
| Chọn vùng | Test component harness dùng PDF thật: trang2, kéo/di chuyển/resize, keyboard, zoom/rotation, mobile, đổi file bỏ vùng và giữ trang hợp lệ |
| Lỗi / retry | Chỉ HTTP503 là STUB fault để kiểm UI; lần retry tải backend/SQL/Cloudinary REAL; không dùng stub để tuyên bố tích hợp thật |
| Visual review | Đã mở ảnh PDF desktop, mobile chọn vùng và kiểm lại toolbar trong CSS thực của app; ảnh phải chờ canvas render xong |

Lệnh regression thực thi tại `frontend/`:

```powershell
node --test tests/shared-pdf.test.mjs tests/mghp-signing.test.mjs tests/review-lifecycle.test.mjs tests/review-flow.test.mjs tests/p1-review-context.test.mjs tests/student-journey.test.mjs
npm.cmd run build
```

### Sai sót tìm được và sửa trước bàn giao

- Review/browser RED chứng minh tạm khóa `selectable` làm mất controlled selection; sửa chỉ tắt công cụ/gesture, consumer vẫn sở hữu giá trị. GREEN `temporary-disable-keeps-controlled-selection`.
- Keyboard vùng cao50px vượt mép trang thấp117px; dùng geometry có height giới hạn theo viewport, báo zoom nếu không đủ chỗ. 2 unit regression và browser GREEN.
- Hover primary bị đổi thành nền nhạt/chữ trắng; sửa variant hover, browser so computed color GREEN.
- CSS label của app xếp Trang/input/tổng trang thành cột; viewer đặt flex-direction row rõ ràng, browser RED→GREEN và xem lại ảnh desktop.
- Script browser đầu lấy textContent cả nhãn trạng thái thay vì span tên giấy; sửa locator theo DOM, chạy lại đúng stage consumers. Vite reload làm mất window harness; setup tái tạo harness trong cùng phiên RAM, không đăng nhập hay seed lại để né lỗi. Đây là lỗi kiểm thử, không báo thành lỗi nghiệp vụ.

### Giới hạn và quyết định kỹ thuật

- Không chạy toàn bộ backend/Playwright lịch sử, không upload hoặc ký CA mới; phần CA/transaction/signing command không đổi.
- Dữ liệu P11 hiện đã có tài liệu hoàn tất, nên kiểm chọn vùng chủ động trong component harness với PDF thật; không giả lập assignment để báo đã kiểm một lượt ký CA mới. App thật kiểm riêng các màn hình đọc và quyền CV/PP/PHT/SV/Admin.
- Đã nhận review code lần cuối và sửa các finding; review này không phải G8 hoặc nghiệm thu độc lập nghiệp vụ.
- Shared `selectable` chỉ bật/tắt công cụ, không tự xóa controlled selection khi tạm khóa; đổi page/file vẫn xóa. Module tiếp tục xóa xác nhận theo semantic key file/version/ETag/bước như P11 hiện có.
- Hướng dẫn chung và frontend skill đã đồng bộ. SHCN/DRL có thể import shared entry, nhưng authorization/file policy và workflow ký riêng phải thuộc phạm vi module được giao, không tự có chỉ nhờ viewer.

Evidence local: `target/shared-pdf-20261123/` gồm `unit.log`, `build.log`, `runtime-proof.json`, `browser-result.json`, `review-red-browser.json`, `review-red-unit.log` và ảnh desktop/mobile. Tất cả credential/token chỉ ở RAM/local DEV ngoài Git. Không tạo fixture nghiệp vụ mới, không sửa/xóa dữ liệu cũ.
