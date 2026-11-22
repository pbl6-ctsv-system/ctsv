# CTSV P1 Usability — Detailed Implementation Plan

> For agentic workers: execute sequentially with `superpowers:executing-plans`, TDD and verification-before-completion. Owner explicitly waived another plan approval on 22/11/2026. Check a task only after its targeted verification; do not commit/push.

**Goal:** Hoàn thiện toàn bộ nhóm ưu tiên P1 trong audit UI ba vai trò ngày22/11, giúp người dùng thao tác theo tên/ngữ cảnh thay vì mã nội bộ.

**Architecture:** Giữ React AppShell/navigation/theme và feature boundaries. Dùng lựa chọn có kiểu, read projection whitelist và các command/ETag hiện hành. Read API mới chỉ phục vụ tìm/chọn dữ liệu đã có, kiểm quyền server, không tạo workflow/quyền mới.

**Tech:** Java21/Spring Boot3.5.16, SQL Server, React19.2/TypeScript5.9.3/Vite7.1.12/Rollup WASM; không thêm dependency.

**Spec:** Audit ba vai trò trong chat + lệnh owner “tạo detailed implement plan cho p1 hoàn chỉnh rồi tự thực hiện tuần tự… cái nào xong đánh dấu tích”. Đây là mức ưu tiên P1 của audit, không phải triển khai lại phase P01. Phạm vi xác định dưới đây là toàn bộ P1; P2/P3, P10+, PRE-P14 và P17 hoãn giữ nguyên.

## Constraints và quyết định thực hiện

- Working tree đang chứa thay đổi P05–P09/timeline/UX đã được owner giữ; không reset/discard/stash/commit hoặc chuyển worktree làm thiếu source chưa commit. Thực hiện tại working tree hiện hành, ghi rõ files của lượt này.
- Không thay JWT/session, capability, source binding, kết luận/tiêu chí/thời gian hưởng, trạng thái, điều kiện PDF hoặc luồng nhiều vòng bổ sung. Backend vẫn quyết định quyền và command hợp lệ.
- Không migration: chỉ đọc schema hiện có. Không reset/drop/clean/đổi dữ liệu cũ. DEV smoke dùng launcher/ctsv_dev; xác minh server/database/owner/marker trước ghi fixture mới. Chỉ dữ liệu synthetic riêng bài test.
- Không lưu token/password vào file; không lặp upload khi chỉ đổi layout. PDF thật dùng file owner đã cung cấp. Không ký CA.
- Hợp đồng read delta được thiết kế trước code theo ủy quyền triển khai nhóm P1, ghi rõ trong API source/catalog; không công bố endpoint chưa tồn tại là PASS.
- Lựa chọn danh mục bắt buộc resolve ID; không ngầm chọn một trong nhiều quá trình/người/role. Có search/pagination; response cũ không ghi đè lựa chọn mới.
- Không đưa tất cả field lên UI. Người liên quan hiển thị theo evidenceSubject của diện chọn; chỉ hỏi thêm thuộc tính khi điều kiện/tiêu chí tương ứng thực sự cần; không xóa dữ liệu lịch sử khi thu gọn form.

## Bố cục được chọn

- SV: đợt/diện/quá trình → thông tin có sẵn → người liên quan cần thiết → từng giấy kèm upload/chọn file. Mỗi file có tên; giữ liên kết một file/nhiều yêu cầu hợp lệ.
- CV: danh sách đủ ngữ cảnh → lời khai/nguồn → PDF/điều kiện còn thiếu → kết luận → link đánh giá đúng hồ sơ. Đánh giá: chọn giấy bằng tên, xem PDF, tiêu chí, khoảng giấy chứng minh và khoảng đủ điều kiện được giải thích riêng.
- Admin: tìm tài khoản/người dùng → chọn đối tượng → thao tác theo tab; role và hồ sơ nguồn chọn bằng tên; xác nhận đúng target trước khóa/thu hồi. Foundation đổi target phải bỏ kết quả/ETag cũ.

## Review focus

1. Đổi target/search nhanh: stale response không điền dữ liệu vào target mới; không tái dùng ETag cũ.
2. Catalog trên100 mục/tên trùng: lựa chọn theo ID, search/page server; không chấp nhận text tùy ý là ID.
3. Giấy dùng chung PDF hoặc giấy từ bản cũ: tên/phiên bản rõ; không thay file đang kiểm bằng response cũ.
4. Admin Auth không có MGHP/SV khác chủ: read mới fail-closed và chỉ trả projection được phép.
5. Dữ liệu thiếu/tài khoản bị thu hồi/command412: giữ input hợp lệ, thông báo có đường xử lý, không chuyển thành thành công.

## Checklist thực hiện tuần tự

### P1-01 — Contract/readiness và guard (dependency: audit)

Files: plan này; contracts Auth/Foundation/Review/Submission; API catalogs và api-data-readiness chỉ phần delta.

- [x] Đóng các read delta: account search; business-user/role directory; source-profile picker; own-benefit page; các nhãn projection cho review/comparison. Giữ GET no-store, page0/size20/max100, q≤128, sort ổn định, parameterized/escaped search và authorization đúng module. `docs/p1-read-contract-delta.md` + liên kết contracts/catalogs.
- [x] Xác minh Git baseline, DEV launcher/SQL đích, test commands. Không thay migration/source phase khác. Guard read-only PASS: DESKTOP-7PPHP5B\DEV2022 / ctsv_dev, owner/marker đúng, FlywayV18, process Backend đúng DB. Evidence target/p1-usability-20261122/db-before.json.

### P1-02 — Foundation target, danh mục và chọn người/role

Files: `shared/hooks/useRecordApi.ts`; `features/foundation/pages/FoundationWorkspace.tsx`; public Foundation picker; `foundation/web`, `repository`, `dto`, `mapper`; targeted tests.

Interfaces: `clearRecord()` hủy hiệu lực request pending và xóa body/tag; lookup/search trả Page có id/code/name. Chỉ module Foundation sở hữu repository/entity nguồn.

- [x] RED: tái hiện đổi loại/ID vẫn hiện kết quả cũ, test late response; GREEN: invalidation toàn bộ record, khóa mutate đến đọc đúng target. Browser REAL trước=1 kết quả học kỳ còn, sau=0; request-gate Unit2/2 PASS.
- [x] Tìm/chọn SV, học kỳ, quá trình, người dùng CTSV và role bằng tên; table row mở đúng detail; projection tên lớp/đơn vị. Không thay quy tắc xác minh. Browser CV named pickers +8 quá trình +row detail REAL PASS; admin role directory kiểm ở P1-07.
- [x] Verify browser đổi ngữ cảnh + chọn đúng đối tượng; Unit/read HTTP quyền; cập nhật kết quả.

### P1-03 — Lựa chọn và người liên quan trong kê khai SV

Files: `intake/DeclarationForm.tsx`, `declaration-model.mjs`; Foundation public picker; targeted declaration tests.

- [x] RED: text catalog chưa resolve không được gửi; policy đổi/người liên quan có subject khác phải giữ binding hợp lệ; GREEN: chọn danh mục có tên, ID nội bộ, query/page và lỗi tại trường.
- [x] Quá trình học có mô tả nguồn sẵn có; một tự chọn/nhiều yêu cầu chọn, không tạo mặc định giả.
- [x] Người liên quan theo subject/tiêu chí đang áp dụng; quan hệ chọn rõ, thuộc tính kỹ thuật có picker hoặc được lấy từ source; cho bỏ người nhập nhầm và cập nhật index tài liệu an toàn.
- [x] Verify browser SV/validation; Unit nhóm chỉ có giấy của sinh viên và payload không đổi contract. Nhánh evidenceSubject=sinh_vien chưa có fixture browser REAL riêng; ghi giới hạn trong báo cáo.

### P1-04 — Upload theo giấy và điều kiện duyệt PDF

Files: `DeclarationForm.tsx`, CSS, `StaffDossierReview.tsx`, `review-model.mjs`, targeted tests.

- [x] RED: upload/chọn file ngay yêu cầu, dùng chung file và lỗi upload không gắn nhầm giấy; GREEN: giấy→file/tên/trạng thái rõ, giữ pool để chọn lại, không upload trùng.
- [x] RED: còn giấy hoặc bổ sung chưa xử lý không mời xác nhận hồ sơ; GREEN: hiển thị lý do/count/link đến giấy hoặc bổ sung. Giữ backend preconditions/ETag và điều kiện giấy khác, không tự pass.
- [x] Verify browser PDF lựa chọn n mục, upload dịch vụ REAL nếu luồng tích hợp thay đổi; không ghi lại kiểm giấy của hồ sơ đã duyệt.

### P1-05 — Nối tiếp nhận → đánh giá và đánh giá theo nguồn

Files: `IntakeWorkspace.tsx`, `review/ReviewWorkspace.tsx`, review model/CSS; `DocumentReviewService` read projection nếu cần.

- [x] RED: từ hồ sơ được phép đi đúng application context, không mã tay; GREEN: deep link trong route hiện có, guard giữ nguyên, danh sách có MSSV/lớp/M/học kỳ/trạng thái.
- [x] Chọn đánh giá từ lịch sử hồ sơ; giấy làm căn cứ có tên + bản nộp + PDF; không nhập documentId/reasonCode tự do làm đường chính.
- [x] Nhóm tiêu chí theo hướng dẫn; field điều kiện theo kết quả; giải thích và kiểm ngày của giấy riêng với khoảng đủ điều kiện. Không tự kết luận/auto chứng minh/cộng MAX.
- [x] Verify targeted model + browser lưu/đọc lại một bản nháp synthetic nếu đích/source hợp lệ; không sửa confirmed cũ.

### P1-06 — Quyền hưởng của tôi và thông báo có ngữ cảnh

Files: `BenefitService`, `ReviewController`, `InboxService`; `ReviewWorkspace`, `IntakeWorkspace`; contracts/tests.

- [x] RED/GREEN own list: chỉ studentId mapping của caller, page/count cùng predicate, không reason nội bộ/CCCD; UI danh sách → detail hoặc empty state, không yêu cầu mã.
- [x] RED/GREEN notice link: nhận/đồng ý/từ chối đi đúng application; bổ sung giữ đúng supplement context; allowlist URL, kiểm ownership hiện hành, không render `/mghp` giả.
- [x] Verify HTTP thật owner/thiếu quyền, UI empty/list; fixture quyền hưởng nếu không có thì ghi rõ giới hạn positive branch, không triển khai công bố P12 để tạo fixture.

### P1-07 — Admin tìm tài khoản, form và phân quyền

Files: `AuthController`, `AuthAdministration`, `AuthViews`; `AuthAdminPage` và components/CSS; Foundation directory; targeted Auth tests.

- [x] RED/GREEN account search theo username, UUID chỉ chi tiết phụ; no secret/hash/token; quyền auth.accounts.manage hiện hành và pagination.
- [x] Tạo tài khoản: validation username/password/profile đúng contract, lỗi tại trường; chọn hồ sơ nguồn đã xác minh qua directory có tên và loại rõ, không gán từ email.
- [x] Role Auth lấy danh mục AUTH06, role MGHP lấy directory Foundation, target có tên; xác nhận khóa/thu hồi nêu đúng account/người và role. Không thêm quyền ngầm.
- [x] Verify browser tạo tài khoản synthetic riêng, tìm/read lại; cancel xác nhận không gửi mutation; test quyền/stale; không khóa/đổi quyền tài khoản DEV đang dùng hoặc tài khoản người khác.

### P1-08 — Targeted regression, bàn giao DEV và review cuối

- [x] Frontend `node --test` các file bị tác động; `npm run build` (typecheck+Vite). Backend test selector theo read delta + package executable JAR; không chạy toàn bộ P00–P09.
- [x] `dev.ps1 stop/build/start` đúng runbook để DEV dùng source cuối; không migrate khi không có migration mới.
- [x] Playwright trên `https://127.0.0.1:5173`, backend/SQL REAL ctsv_dev đã guard: SV/CV/Admin, success/validation/missing permission/direct URL/mobile, screenshot và assertion dữ liệu UI/HTTP.
- [x] Review riêng diff lượt này, kiểm không giảm authorization/ownership/ETag, không sửa module khác; báo REAL/STUB, PASS/FAIL/BLOCKED/NOT VERIFIED. Cập nhật từng checkbox theo bằng chứng, không tự ACCEPTED phase.

## Nhật ký quyết định/kết quả

- 22/11/2026: owner cho thực hiện toàn nhóm P1 không cần duyệt plan lần nữa. Bỏ bước commit và gate hỏi lại của skill theo lệnh owner; kiểm thử/review vẫn bắt buộc. Các hành vi P2/P3 không được kéo vào scope.
- Trạng thái ban đầu: PLANNED. Chưa checkbox triển khai nào hoàn tất.
- P1-02 bước1: sửa `clearRecord` + request generation; Playwright giữ context CV, đổi semester→process bỏ kết quả cũ; không gửi mutation. Unit2 PASS. Launcher status ở shell hiện báo chưa setup; đang đối chiếu LOCALAPPDATA của launcher đang chạy, không chạy setup đè cấu hình.
- Ruling: shell sandbox redirect LOCALAPPDATA; dùng execution ngoài sandbox được duyệt cho Maven/cache, SQL guard và launcher để sử dụng đúng cấu hình hiện có. Maven offline tránh tải dependency mới. Targeted MVC3/3 PASS; frontend typecheck PASS tại mốc P1-02; không tính MockMvc là HTTP/SQL REAL.
- P1-03/04: Unit declaration9/9 PASS, review/request10/10 PASS, review-context2/2 PASS. Đã triển khai catalog select có search/page thu gọn, người liên quan progressive +xóa an toàn binding, upload tại từng giấy, duyệt báo blocker theo đúng command hiện hành. Browser SV trước sửa lần cuối đã quan sát danh mục REAL +quá trình8; chưa đánh dấu luồng upload/nộp PASS. HMR làm mất phiên khi refresh đang chưa hoàn tất; sẽ kiểm lượt cuối khi code ổn định, không nới Auth.
- P1-05/06/07 SOURCE IMPLEMENTED, verification pending: nối đúng hồ sơ, PDF có tên/bản, trường nguồn có lựa chọn; own benefit list +link notice; account/user/role/profile pickers, validation và confirm named target. Backend QH01-L RED thực tế401 expected/404 actual trước implementation. Regression mapper phát hiện whitelist5→7; cập nhật test whitelist chính xác theo delta contract được giao, không nới assertion.
- Automatic review rejected một script định capture Authorization header từ browser; script đó không chạy. Bỏ hoàn toàn capture token/header; chỉ browser UI và status/body được phép. Không dùng cách vòng để lấy credential. Các context vẫn chỉ giữ trạng thái browser trong bộ nhớ, không file storage-state.
- Browser REAL: tạo root10028 (process3, round20040) +file10032 từ PDF owner1625263byte; upload201, nộp201, kiểm3giấy/duyệt200 và GET lại Đã duyệt. Chỉ dữ liệu synthetic mới. File download SHA256 trùng gốc `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`.
- Phát hiện viewer bị unmount khi rootTag đổi vì kiểm giấy:2GET PDF thừa/1thao tác; sửa giữ viewer, fence mutation đến context mới. Browser GREEN0GET thừa, không sửa storage/credentials/backend limiter. Từng gặp503 tự nhiên; chưa kết luận lỗi provider hoặc credentials. Thử phục hồi bằng GET lỗi503 STUB trong StrictMode rồi GET REAL200, iframe có bytes; không tính STUB là integration REAL.
- XET draft lần đầu script bỏ trống lý do cho `can_xac_minh` nên400 rollback (SCRIPT_FAIL); thêm required/helper theo contract. Lần hợp lệ201 +read-back đã PASS. Sau đó tái hiện lỗi lịch sử source vẫn0 mặc dù draft đã lưu: RED. Sửa readEvaluation tải context/list lại từ server sau save/read; GREEN đã PASS: PUT200, đọc lại source có1 đánh giá và mở lại đúng nội dung đã lưu.


## Kết quả cuối — 22/11/2026

- P1-01 → P1-08: IMPLEMENTED; các checkbox đã hoàn tất trong phạm vi bằng chứng ở [báo cáo](../../p1-usability-results-20261122.md). Đây không phải nghiệm thu lại P00–P09 hoặc owner ACCEPTED một phase mới.
- Frontend43/43, backend targeted21/21, HTTP REAL20/20, browser24 nhóm PASS;4 RED lịch sử và4 lỗi script đã sửa/kiểm lại được giữ trong manifest, không cộng vào PASS.
- JAR source cuối đã build bằng `dev.ps1 build`; start/status READY, ctsv_dev V18; không migration.
- Đã rà dependency các field người liên quan: localityCode/workplaceGroup/matchingStaffCode là snapshot tùy chọn, không phải input bắt buộc của engine Evaluation hiện hành. Giữ giá trị cũ khi edit; công tác/đơn vị vẫn mở theo nhu cầu và không tự suy từ tên. Không tuyên bố đã kiểm eligibility M1–M9 toàn bộ.
- Rà cuối phát hiện command yêu cầu cập nhật bị thiếu `/quyen-huong` sau khi đổi base controller. Regression401 expected/404 actual RED; khôi phục URL cũ,4/4 P1OwnBenefitMvcTest GREEN.
- Giới hạn REAL: DEV chưa có quyền hưởng nên populated own-list/cross-owner và benefit-backed evaluation context chưa kiểm nhánh thành công. Không tạo quyền giả hoặc triển khai P12 để lấp test; authorization, query guard và empty state đã kiểm.
- Guard trước/sau giữ Flyway checksum và tài khoản cũ (trừ account synthetic mới được ghi chính xác). Guard baseline loại sẵn các round UX-INTAKE; không nhận nó là checksum bảo vệ toàn bộ fixture UX cũ. Không xóa dữ liệu.
- PDF viewer mobile đã xem ảnh sau khi native PDF render; không kết luận từ ảnh frame trống ban đầu. Desktop/mobile không tràn ngang.
- Không commit/push/merge; giữ toàn bộ thay đổi có trước lượt này.

- Admin source picker hiện0 trusted profiles: chưa kiểm ghi identity link thành công; không dùng identity synthetic không trusted. Checkbox xác minh phải bỏ chọn sau đổi loại/source; RED checked=true → GREEN=false,0 mutation.
