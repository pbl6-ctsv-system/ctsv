# PDF scan trắng / tệp mẫu không mở được — 24/11/2026

## 1. Lỗi renderer đã sửa

Phạm vi: frontend shared PDF.js và phân phối tài nguyên ở Vite; không đổi backend/API/DB hoặc package version.

- Tái hiện đúng file10032 trong hồ sơ10028: file35trang, 35 ảnh `/CCITTFaxDecode`; trang1 chỉ còn logo/chữ ký, trang2 thân trắng (0 pixel tối trong vùng20–90% chiều cao).
- Console RED: `Ensure that the wasmUrl API parameter is provided`, import `nulljbig2_nowasm_fallback.js`, `JBig2 failed to initialize`, `Unable to decode image`. Source package6.5.438 dùng `JBig2CCITTFaxImage`/WASM cho cả CCITT Fax.
- Nguyên nhân: trước đây chỉ cấu hình worker, không cấu hình tài nguyên decoder. PDF.js có thể hoàn tất render trong khi bỏ ảnh lỗi, nên kiểm canvas/HTTP/hash hoặc ít pixel ở header không đủ.
- Sửa: plugin `frontend/tools/pdfjs-assets.mjs` đọc đúng package đã cài, phục vụ DEV và emit resource/version/license vào build, không thêm dependency/binary Git/CDN. `pdf-options.mjs` cấp decoder/font/CMap/ICC base URLs cho `getDocument`.
- Sau sửa: trang2 có91810 pixel thân văn bản; **35/35 trang** có nội dung, ít nhất29484 pixel tối ở thân trang; WASM200, không warning giải mã. Đã xem ảnh trang1/2/35 và đối chiếu trang2 với Poppler độc lập.

## 2. Hồ sơ Bảo: lỗi dữ liệu mẫu đã xác minh, chưa sửa dữ liệu

Owner chỉ định `P10UI-20261122-MAIN-BAO-M2`, root10033, tệp10037.

- Browser thật: GET `/api/ctsv/tep/10037/noi-dung` trả **502 FILE_INTEGRITY_FAILED**.
- SQL read-only DEV: tên `P10UI-20261122-MAIN-BAO-M2-metadata.pdf`; size100byte; SHA-256 là64chữ `a`; không asset ID, không resource type. Đây không phải file PDF đã được upload.
- Nguồn: `frontend/e2e/p10-dev-fixture.ps1:131–133` ghi rõ metadata-only, chưa download/upload storage trong test P10. Fixture còn ghi nhận giấy được kiểm/đánh giá dựa trên dữ liệu synthetic; không được dùng đó làm bằng chứng PDF storage REAL.
- Hồ sơ đã thuộc snapshot quyết định version2, state locked, stage after_meeting. Không tự thay hash/asset hoặc ghi đè nguồn đã chốt để làm file giả thành PDF thật.
- Backend đang chặn đúng file thiếu metadata/toàn vẹn. Giữ nguyên guard; chỉ sửa câu báo lỗi viewer thành thông báo dễ hiểu, không dựng PDF thay thế hoặc hiện bytes cũ.
- **CONFIRMED DATA ISSUE / NOT REPAIRED.** Cần lượt xử lý dữ liệu mẫu riêng được owner thống nhất, có PDF thật và bảo toàn snapshot/lịch sử. Lỗi này khác lỗi decoder ở mục1.

Lệnh dò rộng các PDF bằng token phiên bị auto-review từ chối vì phạm vi đọc rộng. Không thực thi lại/đi vòng lệnh đó; sau khi owner chỉ định mã hồ sơ, chỉ kiểm target10033/file10037 qua UI và SQL read-only. Không credential/token trong evidence.

## 3. Kiểm chứng thực tế

CWD `D:/7rd_semester/pbl6_src/ctsv/frontend`; React19.2.0, TS5.9.3, Vite7.1.12, PDF.js6.5.438. DEV launcher: FE `https://127.0.0.1:5173`, BE `https://127.0.0.1:8443`, datasource `ctsv_dev`.

| Kiểm tra | Kết quả |
| --- | --- |
| TDD / unit và model regression | **44 PASS,0fail,0skip**: thêm asset/options/MIME/traversal/license và thông báo toàn vẹn; giữ các test signing/geometry/review/student |
| Typecheck/build | `npm.cmd run build` **PASS** |
| Resources trong build | 6 kiểm hash/bytes khớp package gốc, gồm WASM/fallback/license/font/CMap |
| Browser scan | **35/35trang PASS**, thân trang có nội dung; HTTP/WASM/Cloudinary REAL, không mock |
| Download | 1625263byte, SHA-256 `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`, khớp PDF gốc |
| Student mobile | Trang2 nội dung thật,7337pixel tối ở thân,360px không tràn trang |
| P11 read-only | PDF đã ký file10048/trang2 vẫn render, không nút ký lại; không gọi CA mới |
| Tệp lỗi Bảo | Tái hiện502, thông báo nhìn thấy, canvas ẩn/download disabled; không giả PASS việc mở file |

```powershell
node --test tests/pdfjs-assets.test.mjs tests/shared-pdf.test.mjs tests/mghp-signing.test.mjs tests/review-lifecycle.test.mjs tests/review-flow.test.mjs tests/p1-review-context.test.mjs tests/student-journey.test.mjs
npm.cmd run build
```

Browser regression source: `frontend/e2e/pdf-scan-regression.mcp.js`, dùng các phiên DEV RAM. Không chạy toàn bộ backend hoặc các phase; không tạo/sửa/xóa hồ sơ, tệp hay fixture trong DB. Không upload/ký CA. Không commit/push.

Evidence local: `target/pdf-render-fix-20261124/` gồm render RED/GREEN, `scan-browser.json`, `bao-file-metadata.json`, `bao-source-lock.json`, `bao-http-error.json`, `bao-ui.json`, `other-views.json`, `unit.log`, `build.log`, `build-assets.json`, ảnh trước/sau và ảnh tham chiếu Poppler.

## 4. Điều chỉnh phạm vi bằng chứng cũ

Bộ46assertion ngày23/11 đã chạy thật, nhưng tiêu chí pixel toàn trang có thể được thỏa chỉ bởi logo/chữ ký và chưa phát hiện ảnh scan bị bỏ. Không dùng kết quả đó để tuyên bố PDF scan nhiều trang đã được kiểm đầy đủ. Regression35trang và kiểm warning decoder ở lượt24/11 bổ sung đúng khoảng trống này. Không sửa lại lịch sử test thành kết quả chưa từng chạy.
