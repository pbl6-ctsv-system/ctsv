# P1 usability — kết quả thực hiện 22/11/2026

Phạm vi là nhóm ưu tiên **P1 của audit UI ba vai trò**, không phải phase P01. Owner cho thực hiện tuần tự không cần duyệt plan lần nữa. [Detailed Plan và checklist](superpowers/plans/2026-11-22-p1-usability.md) đã cập nhật theo bằng chứng. Không triển khai P2/P3, P10+, PRE-P14 hoặc phần P17 đang hoãn.

## Kết quả theo task

| Task | Kết quả source | Đã kiểm | Giới hạn |
|---|---|---|---|
| P1-01 Contract/readiness | Hoàn tất read delta trước code và liên kết nguồn/catalog | SQL owner/marker/runtime; baseline Flyway V18 | Không nghiệm thu lại các phase cũ |
| P1-02 Foundation | Đổi target bỏ body/ETag cũ; chọn người, học kỳ, quá trình, role theo tên | RED→GREEN stale target, late-response Unit; browser CV/Admin; HTTP quyền | Không mở rộng các form xác minh nâng cao thuộc P2 |
| P1-03 Kê khai SV | Danh mục có search/page; quá trình có tên nguồn; người liên quan theo ngữ cảnh và xóa an toàn | Browser chọn danh mục/quá trình, bỏ người; nộp REAL; model binding/defaults | Nhánh chỉ có subject `sinh_vien` kiểm ở model, chưa có fixture browser riêng |
| P1-04 PDF/duyệt | Upload ngay mỗi giấy, dùng lại file; nêu rõ điều kiện còn thiếu; viewer giữ nguyên khi metadata đổi; thử tải lại | REAL upload201/nộp201/kiểm PDF201/duyệt200/download200; SHA256; retry STUB→REAL;0 GET thừa | Bản mới dùng cùng một PDF cho3 giấy; không nhận đây là ca đổi3 file có bytes khác nhau |
| P1-05 Đánh giá | Link từ hồ sơ đúng context; tên giấy/bản nộp, PDF, nguồn trạng thái; phân biệt ngày của giấy và khoảng đủ điều kiện; history đọc mới | Lưu draft201, sửa200, refresh/mở lại đúng draft và history1; model ngày/lý do | Không xác nhận kết quả thật hoặc xét lại eligibility M1–M9; benefit-backed context nhánh có dữ liệu chưa chạy REAL |
| P1-06 Quyền hưởng/thông báo | Own-list từ mapping server; notice mở đúng hồ sơ; allowlist link | HTTP own200 rỗng/owner-param400/thiếu quyền403; browser empty/notice/direct URL | DEV có0 quyền hưởng: chưa kiểm populated list và cross-owner bằng dataset REAL |
| P1-07 Admin | Search username, typed form, picker nguồn/role, xác nhận đúng target; đổi nguồn bỏ xác nhận identity cũ | Tạo account201/read-back; validation không gửi POST; hủy dialog0 mutation; HTTP security; checkbox RED→GREEN | Danh bạ nguồn trusted DEV rỗng; chưa ghi liên kết identity thành công; không tự thêm nguồn tin cậy |
| P1-08 Bàn giao | Build/typecheck, JAR/launcher DEV cuối, review diff/ảnh, guard sau |43 FE tests,21 BE tests,20 HTTP REAL,24 nhóm browser PASS | Không nhận các giới hạn bên trên là PASS hoặc owner ACCEPTED phase |

## Thay đổi chính

- SV chọn dân tộc/lớp/khóa bằng tên và tìm theo trang; không nhập ID tự do. Một quá trình tự chọn, nhiều quá trình vẫn phải chọn; không đoán mặc định từ8 fixture DEV.
- Các thuộc tính người liên quan kỹ thuật không chen vào luồng chính. Công tác/đơn vị còn trong nhóm mở theo nhu cầu; giá trị cũ được giữ. Đã đối chiếu `IntakeRules`/`IntakeMapping`/`ApplicationService` và các consumers Evaluation: `localityCode`, `workplaceGroup`, `matchingStaffCode` là snapshot tùy chọn, không phải input bắt buộc của engine hiện hành. Không tự suy identity/căn cứ từ tên.
- Giấy → upload/chọn file → thấy tên đã gắn. Một PDF được tái sử dụng cho nhiều yêu cầu; lỗi upload không gắn nhầm giấy. CV thấy count/lý do chặn duyệt và đường xử lý, giữ precondition backend.
- Tiếp nhận → đánh giá đi đúng root; chọn giấy theo tên và revision, xem PDF cả vùng đánh giá dùng chung; ngày của giấy và khoảng kết luận là hai nhóm riêng. Lưu/read lại tải lại history từ server.
- Admin chọn đúng đối tượng theo tên, giữ role Auth tách role MGHP. Không cấp quyền từ tên role UI; không thêm PHT runtime. Picker identity chỉ dùng nguồn đã đăng ký trong Auth; checkbox xác minh phải chọn lại sau thay đổi nguồn/người.

## API/kiến trúc

[Contract delta](p1-read-contract-delta.md) và Auth/CTSV Catalog ghi6 read API: AUTH08-L, FND07-LU, FND07-LR, FND-LINK-L, QH01-L, QH01-C. Có projection tương thích cho FND student/process, HS form/detail và link thông báo; FND03 có batch `ids` bounded để giải tên.

Authorization hiện hành vẫn ở backend; read mới có pagination/count/whitelist/no-store. `AuthDirectoryAccess` là boundary công khai hẹp cho Foundation, không join bảng Auth hoặc suy quyền MGHP từ Auth Admin. Không thêm router, Navbar, framework, dependency, Entity hay migration. Không đổi command/nghiệp vụ/ETag; URL command cập nhật quyền hưởng cũ đã được regression bảo vệ.

## Bug thực tế và regression

| Bug | RED / nguyên nhân | GREEN |
|---|---|---|
| Foundation giữ dữ liệu target cũ | Đổi semester→process vẫn còn1 kết quả; response chưa bị vô hiệu |0 kết quả cũ; request generation invalidation; bỏ tag/edit cũ |
| Viewer tải PDF thừa khi kiểm giấy | `review=null` khi rootTag thay đổi làm unmount viewer;2 GET thừa | Giữ viewer cùng root, fence mutation tới context/tag mới;2 lần kiểm sau sửa đều0 GET thừa |
| History đánh giá không cập nhật | Save201/read-back có draft nhưng trở về source vẫn0 history | `readEvaluation` nạp lại source/context;1 history và đúng nội dung draft sau refresh |
| URL yêu cầu cập nhật bị mất prefix | Thay controller base làm POST URL cũ404 | Khôi phục `/quyen-huong/{id}/yeu-cau-cap-nhat`; MockMvc401 expected/404 actual RED→GREEN |
| Xác nhận identity giữ lại sau đổi nguồn | Checkbox vẫn checked sau student→staff | Reset input theo tập link; browser unchecked,0 mutation |

Đã gặp503 tải PDF tự nhiên trong lúc điều tra viewer churn; **chưa đủ bằng chứng quy lỗi cho credential hoặc provider**. Bản sửa không thay cấu hình Cloudinary/limiter. Ca fault có kiểm soát dùng STUB503, sau nhấn thử lại tải REAL200; không tính STUB là integration thật.

## Kiểm thử đã thực thi

CWD mặc định `D:\7rd_semester\pbl6_src\ctsv`; build FE tại `frontend`. Maven offline, Java21; phiên bản framework/dependency giữ nguyên.

| Lệnh / cách chạy | Kết quả cuối | Phân loại |
|---|---|---|
| `node --test frontend/tests/request-gate.test.mjs frontend/tests/p1-account.test.mjs frontend/tests/p1-declaration.test.mjs frontend/tests/p1-review-context.test.mjs frontend/tests/review-flow.test.mjs frontend/tests/dossier.test.mjs frontend/tests/declaration-defaults.test.mjs frontend/tests/module-navigation.test.mjs frontend/tests/auth-client.test.mjs` |43 PASS,0 fail/skip | Node model/Auth/navigation regression; không phải SQL integration |
| `mvn -o -q '-Dtest=*P1*Test,P1OwnBenefitMvcTest,FoundationMapperTest,FoundationMvcTest,ApplicationDispositionRulesTest,ApplicationDispositionHttpShapeTest' test` |21 PASS,0 failure/error/skip | Unit/MockMvc, dependencies mock |
| `pwsh -NoProfile -File frontend/e2e/p1-read-http-dev.ps1` |20 PASS | HTTP Spring Boot +SQL Server REAL,3 tài khoản DEV; chỉ read và Auth login audit |
| `npm run build` | TypeScript +Vite PASS;94 modules | Source FE cuối, gồm reset xác nhận identity |
| `dev.ps1 stop` → `dev.ps1 build` → `dev.ps1 start` / `status` | READY; BE/FE RUNNING;V18 | Executable JAR DEV source cuối; không migration |
| Playwright MCP, scripts P1 dưới đây và các bước RED/GREEN có ghi manifest |24 nhóm PASS;4 RED lịch sử đã sửa;4 lỗi script được giữ lại;1 NOT_VERIFIED directory positive | Browser thật; FE/BE/SQL `ctsv_dev` REAL; fault PDF riêng là STUB |
| `p1-dev-guard.ps1 -Stage after -CreatedAccount p1.ux.20261122.1795323491856` | PASS | Read-only owner/marker/backend DB/Flyway và checksum baseline |

8 backend suites tương ứng: AuthP1ReadMvcTest1; AuthP1DirectorySecurityTest2; FoundationP1DirectoryMvcTest2; P1OwnBenefitMvcTest4; FoundationMapperTest2; FoundationMvcTest3; ApplicationDispositionRulesTest3; ApplicationDispositionHttpShapeTest4.

Lỗi script được phân biệt: draft thiếu lý do nên400 rollback; locator username thiếu hậu tố trạng thái; đợi lựa chọn trong directory rỗng; HMR reload làm mất form. Đã sửa locator/input hoặc mở lại đúng trạng thái, không force-click/nới validation/đổi expected HTTP. Một lần CDP không lấy được body POST sau UI cập nhật thì kiểm GET persisted, không gửi lặp POST. Không có lỗi test ứng dụng chưa xử lý trong phạm vi đã chạy.

## DEV/data và evidence

- Target đã guard: `DESKTOP-7PPHP5B\DEV2022`, `ctsv_dev`, owner hiện hành, marker `ctsv-local-dev-v1`, FlywayV18. FE `https://127.0.0.1:5173`, BE `https://127.0.0.1:8443`.
- Tạo root **10028**, round20040/process3; lời khai “Sinh viên thử giao diện P1”; file **10032**; kiểm3 PDF và đồng ý hồ sơ. Tạo/sửa draft đánh giá **10**, chưa xác nhận. Không sửa confirmed cũ.
- Tạo account synthetic **p1.ux.20261122.1795323491856** bằng UI, chưa role/profile; password ngẫu nhiên chỉ ở memory, không ghi báo cáo. Hủy dialog khóa/thu hồi:0 mutation.
- Upload từ file owner `nghiepvu/05-du-lieu-mau/du lieu test.pdf`,1.625.263 byte. Download qua backend có SHA256 trùng `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`. Cloudinary authenticated REAL, không CA DUT.
- Không xóa/reset/drop DB, không migrate, không đổi password/tài khoản cũ. Guard baseline không bao phủ các round UX-INTAKE đã bị loại ngay từ trước test; không nhận checksum này là bằng chứng bảo vệ toàn bộ fixture UX. Các thao tác ghi bài test đều chỉ root/account synthetic mới được ghi rõ.
- Browser sessions tái sử dụng trong memory, không export storage state/token. Automatic review từng từ chối script capture header; script đó không chạy, đã bỏ cách đó. HTTP test dùng normal login với synthetic DEV preset và JWT ở memory, không lấy credential browser.

Evidence tại `target/p1-usability-20261122/` (được ignore): `browser-manifest.json`, `http-results.json`, `db-before.json`, `db-after.json`, `download.pdf`; screenshot desktop/mobile3 vai trò và `student-form-*`, `student-pdf-mobile-final.png`. Đã xem ảnh, không chỉ chụp; PDF native mobile render thấy nội dung, không tràn ngang360px. Đây là screenshot +automated assertions, **không có trace mới và không phải G8 độc lập**. Surefire report ở `target/surefire-reports/`.

## File của lượt P1

| Nhóm | Files |
|---|---|
| Plan/contract/report | `docs/superpowers/plans/2026-11-22-p1-usability.md`, `docs/p1-read-contract-delta.md`, báo cáo này |
| Shared FE | `shared/api/request-gate.mjs`, `shared/hooks/useRecordApi.ts`, `useApiTask.ts`, `shared/ui/DirectoryPicker.tsx`, `DirectoryPicker.module.css` |
| Auth FE | `AuthAdminPage.tsx`, `AccountProfileFields.tsx`, `account-model.mjs` |
| Foundation FE | `FoundationWorkspace.tsx`, `FoundationWorkspace.module.css` |
| MGHP FE | `DeclarationForm.tsx`, `declaration-model.mjs`, `IntakeWorkspace.tsx`, `dossier-types.ts`, `StaffDossierReview.tsx`, `review-model.mjs`, `PdfPreview.tsx`, `ReviewWorkspace.tsx`, `review/model.mjs` |
| Auth BE | `AuthController`, `AuthAdministration`, `AuthRepository`, `AuthViews`, mới `AuthDirectoryAccess` |
| Foundation BE | `FoundationController/Service/Errors`, `FoundationRepository`, `FoundationDtos`, `FoundationMapper`, mới `SourceProfileController/Service` |
| MGHP BE | `IntakeRepository`, `ApplicationReads`, `BenefitController/Service`, `EvaluationRepository`, `InboxService` |
| Tests | mới `AuthP1ReadMvcTest`, `AuthP1DirectorySecurityTest`, `FoundationP1DirectoryMvcTest`, `P1OwnBenefitMvcTest`; sửa whitelist `FoundationMapperTest` theo projection; FE `request-gate`, `p1-account`, `p1-declaration`, `p1-review-context`, `review-flow` |
| Browser/HTTP/guard | `frontend/e2e/p1-usability-dev.js`, `p1-declaration-dev.js`, `p1-review-dev.js`, `p1-admin-dev.js`, `p1-final-dev.js`, `p1-pdf-metadata-dev.js`, `p1-read-http-dev.ps1`, `p1-dev-guard.ps1` |
| Nguồn nghiepvu | Auth/Foundation/Review/Application contracts; Auth/CTSV API catalogs; `api-data-readiness.md` — chỉ delta/cross-reference P1 |

Các tên FE ở bảng nằm dưới `frontend/src/features/` hoặc `frontend/src/` theo nhóm. Working tree đã có nhiều thay đổi trước lượt P1; bảng này không gán các migration V17/V18, Auth rate-limit, Navbar, timeline hay simplification trước đó thành thay đổi của P1.

**Kết luận:** Source nhóm P1 đã triển khai và bàn giao DEV với phạm vi kiểm chứng nêu trên. Các nhánh thiếu dataset REAL vẫn NOT VERIFIED; không nâng thành PASS bằng mock/waiver. Không commit/push/merge và không chuyển phase tiếp theo.
