# Hồ sơ MGHP — kế hoạch sửa trải nghiệm SV/CV 21/11/2026

Phạm vi owner giao: sửa màn hình hồ sơ SV/CV, đọc thông tin khai/PDF/lịch sử, danh sách có trạng thái/học kỳ/MSSV/tên/lớp/M. Nút Xuất Excel chỉ hiển thị disabled có giải thích. Owner đã cho phép triển khai sớm Backend/contract Đồng ý–Từ chối và bổ sung chung chỉ hạn+nội dung. Dời xét/xét tiếp liên đợt vẫn P17 DEFERRED; không triển khai P10 hoặc suy Đã duyệt là quyền hưởng chính thức.

Thực hiện inline theo using-superpowers, systematic-debugging, TDD, vercel-react-best-practices và frontend/SKILL.md. Giữ AppShell/navigation/auth/stack; không sửa module khác.

- [x] Đối chiếu source và tái hiện trên browser: chi tiết CV chỉ có ngày nộp/số bản và disclosure chứa mã; không có thông tin khai/PDF trong Tổng quan. HS02 chưa hỗ trợ semesterId/classCode; HS03 chưa có danh sách yêu cầu bổ sung. Hai phần đổi Backend được owner duyệt qua câu hỏi trong phiên.
- [x] Đặc tả delta nhỏ: HS02 semesterId + thông tin lớp/M; HS03 danh sách yêu cầu theo quyền; HS08 kết luận có If-Match/actor/reason/audit; BS01 nhận shape hạn+nội dung chung, giữ payload typed cũ. Schema hiện tại chứa được status/type; không tạo/sửa migration.
- [x] Test đỏ rules/FE model và assertion UI lỗi gốc; code nhỏ, test xanh. Trạng thái mới approved/rejected/supplement_responded không tự chuyển completed legacy hoặc tạo đánh giá/benefit.
- [x] SV: khai mới nhất → diện/kỳ → giấy → trạng thái/thao tác; bỏ tra cứu mã và disclosure đối chiếu. CV: khai + so sánh nguồn + PDF viewer và kiểm giấy mới nhất ngay Tổng quan; yêu cầu bổ sung chỉ hạn/nội dung; lịch sử thể hiện yêu cầu, bản nền, phản hồi và thay đổi. Backend vẫn xác thực owner/capability.
- [x] Targeted Unit/build/typecheck; SQL/HTTP/browser REAL trên daily ctsv_dev đã guard. Fixture UX riêng, snapshot hồ sơ/bản nộp/credentials cũ giữ nguyên. JAR DEV build/start qua launcher, không migration/reset. Giới hạn kiểm chứng ghi dưới đây; không chạy lại toàn P00–P08.

Quy tắc command kết luận: CV có quyền, source chưa Đóng xét/confirmed/locked; không sửa nguồn đã có kết luận xác nhận. Đồng ý yêu cầu PDF bản mới nhất được kiểm nội dung đạt, không có kết quả kiểm hiện hành thất bại/cần xác minh và không còn vòng chưa xử lý; với phản hồi general được CV đồng ý, ghi kết quả mục/hoàn tất vòng cùng TX. Từ chối là terminal của hồ sơ đó, reason bắt buộc và hủy vòng chưa hoàn tất cùng audit; không cấp/thu hồi quyền hưởng. Mọi mutation ghi history/notification cùng TX và CAS, thiếu tag428/stale412. Chi tiết UX không được tự báo thành công khi request lỗi.

Điểm giao nhau: source lock/ETag, SupplementService và EvaluationService phải hiểu rejected/approved; filter theo học kỳ trên server trước count/pagination; student chỉ đọc own. Không đổi P08 MAX/ngày hưởng hoặc tự duyệt pháp lý. Không thêm generic workflow, thư viện PDF hoặc storage public; PDF lấy qua backend hiện có, Blob URL trong bộ nhớ và thu hồi khi đổi/đóng.

<a id="results"></a>
## Kết quả thực thi

**Đã triển khai và kiểm chứng phạm vi UX được giao; không là nghiệm thu cả P17.** Chạy tại CTSV root, frontend https://127.0.0.1:5173, Backend https://127.0.0.1:8443, SQL instance localhost\DEV2022 thực tế DESKTOP-7PPHP5B\DEV2022, database ctsv_dev V18. Owner/marker và kết nối Backend đến đúng DB đã kiểm trước/sau bằng [guard read-only](../frontend/e2e/intake-dev-db.ps1). Không thay đổi schema/Entity hoặc Flyway, không reset/clean/drop DB, không gọi CA DUT. Auth/Foundation/Shared và module khác không sửa trong lượt này.

### Hành vi đã bàn giao

- Sinh viên: Tổng quan đọc ngay bản khai mới nhất và tên snapshot, diện/học kỳ, các giấy đính kèm có xem/tải PDF, trạng thái/lý do/thao tác hợp lệ. Bỏ Tra cứu yêu cầu bằng mã và Thông tin đối chiếu hồ sơ. Yêu cầu cần trả lời lấy từ chính hồ sơ hoặc thông báo, không nhập mã.
- Chuyên viên: bảng MSSV/tên/lớp/M/trạng thái/Xem hồ sơ; học kỳ và trạng thái lọc tại server trước count/page. Nút “Kiểm tra & kết luận” ở đầu chi tiết đưa thẳng đến PDF, không buộc tìm action cuối trang. Không thêm cột mức giảm/bản cứng. Xuất Excel disabled, có nhãn Sắp có, chưa export giả.
- Tổng quan CV có đối chiếu nguồn, danh sách giấy mới nhất, PDF viewer riêng tư và Đồng ý PDF/Từ chối PDF/Yêu cầu bổ sung. Kết luận hồ sơ có reason và xác nhận, lưu approved/rejected ở Backend. Đồng ý PDF khác kết luận hồ sơ; kết luận không tự tạo dat/benefit. Kiểm giấy chuyên sâu/bản cứng theo chính sách vẫn có trong disclosure gọn để không làm mất điều kiện P08; không còn nút Lưu kiểm giấy.
- Tạo bổ sung mới chỉ nhập hạn + nội dung. Server tạo general item đúng nghĩa, không giả phoneNumber/document. Luồng typed cũ vẫn có API và phần kiểm từng nội dung trong yêu cầu cũ. Khi SV trả lời, status là Sinh viên đã bổ sung; CV thấy yêu cầu đã gửi, bản nền/phản hồi và chênh lệch thông tin/PDF.
- Dời xét, mở lại hồ sơ bị từ chối, yêu cầu mở lại mới và thay wire kê khai theo kỳ không triển khai. P17 còn lại vẫn hoãn; không đổi nghiệp vụ MAX hoặc dữ liệu đã xác nhận.

### Lệnh và kết quả

| Mức | Lệnh/công cụ | Kết quả thực tế |
|---|---|---|
| Backend Unit | `.\mvnw.cmd -q '-Dtest=ApplicationDispositionRulesTest,ApplicationDispositionHttpShapeTest,SupplementRulesTest' test`, sau đó `EvaluationRulesTest` | 10tests:0failure/0error/0skip. HttpShape là controller/JSON với boundary stub, không gọi là HTTP/SQL integration. |
| Frontend Unit | `npm.cmd --prefix frontend test` | 38PASS/0FAIL/0SKIP, gồm4ca dossier mới (thêm thay đổi người liên quan). |
| Build/typecheck | `npm.cmd --prefix frontend run build` | tsc --noEmit + Vite PASS,87modules; dependencies không đổi. |
| DEV JAR | `.\dev.ps1 stop` → `build` → `start` | JAR executable được build không chạy suite; start READY, BE10784/FE6416 tại lần status cuối. Migration V18 giữ nguyên, không setup/seed khi start. |
| Browser/SQL/HTTP REAL | Playwright MCP thao tác trực tiếp, [hồi quy cuối](../frontend/e2e/intake-ux-dev.js) | 13nhóm luồng UI +1nhóm HTTP/security/CAS đạt;14group IDs trong [browser.json](../target/intake-ux-20261121/browser.json). Không cộng số assertion thành số test độc lập. |
| Bảo toàn dữ liệu | `frontend/e2e/intake-dev-db.ps1 before/after` | Snapshot hồ sơ/bản nộp cũ, ID/tên/hash mật khẩu tài khoản và toàn Flyway history giữ nguyên. Chỉ tạo bộ dữ liệu UX riêng. |

Launcher cần đọc config local thực của tài khoản Windows. Trong sandbox của Codex, LOCALAPPDATA bị chuyển sang vùng sandbox nên `dev.ps1 status` ban đầu báo chưa setup; đã dùng đúng C:\Users\USER\AppData\Local trong process tool được cấp quyền, không setup lại/ghi đè config/credentials. Terminal thông thường của owner dùng `.\dev.ps1 status/start` như cũ.

### Ma trận browser và HTTP đã chạy

| ID | Assertion chính | Actual |
|---|---|---|
| UX01 | SV chọn đợt/process/diện, nhập, upload PDF và nộp qua UI; Tổng quan hiện đủ nhóm dữ liệu | PASS; Cloudinary REAL201, HS05-C201 |
| UX02 | SV xem PDF private qua Backend; SHA-256 bytes khớp file mẫu | PASS200 |
| UX03 | CV bảng có lớp/M, filter học kỳ gửi server, count thật; Excel disabled | PASS200 |
| UX04 | Tạo yêu cầu chỉ2field; POST đúng reason/deadline, persisted general target=null | PASS201 |
| UX05 | SV không tra mã, đọc yêu cầu, sửa điện thoại/địa chỉ và gửi lại bằng UI; lỗi cam kết hiện trong viewport, sửa rồi tiếp tục | PASS201; root supplement_responded |
| UX06 | CV thấy nội dung đã yêu cầu, bản nền/phản hồi và thay đổi thực; giấy tái dùng không bị giả thành file mới | PASS |
| UX07 | Đồng ý khi chưa kiểm đủ PDF bị chặn, lỗi hiện ngay trong dialog sau khi cuộn | PASS409 |
| UX08 | CV chọn từng PDF, Đồng ý PDF rồi Đồng ý hồ sơ; cùng commit hoàn tất general responded | PASS200; root approved,2bản,1kết luận |
| UX09 | CV Từ chối hồ sơ bằng UI, reason/history persisted; mã M đúng phiên bản | PASS200; root rejected/M2 |
| HTTP01 | Thiếu tag428/stale412/SV403;2POST cùng tag→200/412,1event; retry tag mới không thêm history; rejected không tạo BS/đánh giá mới | PASS; SQL Server/HTTP thật |
| UX10 | SV/Admin URL chuyên viên hiện từ chối rõ; Admin Auth GET detail/POST kết luận đều403 | PASS |
| UX11 | Hồi quy refresh, lọc approved+semester, M2/classCode đúng, history chênh lệch, mobile CV360px | PASS |
| UX12 | SV sau refresh thấy trạng thái đúng, PDF xem/tải, không thấy đối chiếu CV, mobile360 không tràn, rejected không có nộp lại | PASS |
| UX13 | Thông báo mở đúng yêu cầu không nhập mã; logout qua UI | PASS |

Sau visual review bổ sung đường tắt đầu chi tiết và đối chiếu thay đổi người liên quan (không chỉ claims/PDF). Đã chạy lại38Unit + build/typecheck và kiểm browser đúng phần đổi: đường tắt cuộn đến PDF, history điện thoại vẫn đúng, mobile360 không tràn đều PASS. Ba assertion bổ sung này không được tính thành3luồng E2E mới. So sánh người liên quan có Unit synthetic; không nhận là đã chạy lại mọi trường hợp M6 trên SQL/browser.

PDF download thực qua nút UI lưu ở target, SHA-256 `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46` bằng file `../nghiepvu/05-du-lieu-mau/du lieu test.pdf`. Đã xem [ảnh PDF trong viewport](../target/intake-ux-20261121/pdf-preview-viewport.png): viewer Chrome render được trang PDF, không chỉ iframe tồn tại. Screenshot full-page ban đầu chụp khi viewer ngoài viewport nên vùng PDF đen; không dùng ảnh đó làm bằng chứng render. Assertion tự động kiểm HTTP/hash/DOM, kiểm ảnh render được ghi riêng là visual inspection.

### Lỗi phát hiện và sửa trong lượt triển khai

1. Trước sửa, Overview chỉ ngày/số bản và disclosure chứa code; không fetch/render bản khai hay giấy ở hành trình chính. Tái hiện browser trước code:0heading thông tin khai/0PDF. Sau sửa UX01/06/11/12 xác minh dữ liệu thật.
2. POST kết luận mới đã dùng nhầm query/body guard dành GET, trả400 body. Có Unit controller tái hiện RED (IntakeProblem VALIDATION_ERROR) rồi GREEN; real UI sau sửa trả409 đúng khi chưa đủ kiểm giấy và200 khi đúng.
3. Global feedback ngoài native dialog có thể bị lớp modal che. Thêm lỗi inline trong dialog; UX07 đo bounds trong viewport và phục hồi thao tác.
4. Mapper mã M mới truyền policyId vào hàm nhận versionId, tạo nhãn M9 sai cho version M2. Sửa mapper, không sửa chính sách/DB; UX09/11 kiểm lại list/detail M2. Không gán lỗi này cho dữ liệu mẫu.

Không che lỗi script: một lượt fixture mở đợt409 vì allowRetroactive=false không đáp ứng guard hiện hành; đọc lại draft rồi sửa duy nhất fixture mới, không đổi rule. Một lần tái dùng file sang root khác bị409 đúng guard; kiểm DB không có root dở rồi upload riêng cho mỗi root. Các timeout còn lại do thiếu tick cam kết/DOM bị HMR reset/locator label có giá trị/viewport null; giữ context, kiểm trạng thái rồi chạy tiếp đúng ca, không tạo trùng. Observer token của test từng bắt cả request kiểm quyền nên race thử ban đầu403; đã tách token theo session trong bộ nhớ, race thật sau đó200/412. Những lỗi công cụ này không được tính là bug/PASS ứng dụng.

Node HTTP client chưa tin leaf local nên một lần dừng ở TLS, không tắt TLS validation; các HTTP kiểm chứng sau chạy bằng fetch trong browser tin cert local. Không lưu raw trace/headers/cookies vào evidence. Controller Unit ban đầu thử Mockito inline bị sandbox chặn Java attach named pipe; đã dừng đúng JVM Surefire của lượt đó và dùng boundary stub Java không agent, không đổi dependency hoặc tắt assertion.

### Dữ liệu thử và phạm vi bảo toàn

Chỉ thêm3đợt có code `UX-INTAKE-1795271503790-1/2/3`, ID20038/20039/20040, tên dễ hiểu và SYNTHETIC; tái dùng policy M2/version9 đã khóa, không sửa policy. Hồ sơ10021 approved (2bản, yêu cầu13 completed),10023 rejected,10024 rejected (race). Files10026/10027/10028 upload Cloudinary REAL bằng file owner cung cấp, mỗi root dùng file riêng; không xóa Cloudinary/ledger. ID10022 là identity gap từ transaction đã rollback, không phải hồ sơ còn tồn tại. Không xóa/đổi tên hồ sơ cũ10020 hoặc dữ liệu TLX/P07.

Tài khoản DEV hiện hành không đổi/reset. SV vào Hồ sơ của tôi → hồ sơ của đợt “Thực hành Bổ sung và đồng ý — SYNTHETIC” để xem kết quả/lịch sử; CV vào Tiếp nhận & bổ sung hồ sơ → lọc Đã duyệt hoặc Từ chối. Muốn thử một kết luận mới, tạo hồ sơ synthetic mới theo quy trình; không mở lại root rejected để tái sử dụng.

### Files thuộc lượt này

| Nhóm | Files |
|---|---|
| Backend sửa | ApplicationController, ApplicationReads, ApplicationService, IntakeRules, SupplementService, DocumentReviewService, EvaluationService, ReviewSourceAccess trong src/main/java/pbl6/ctsv/mghp/application |
| Backend mới/test | ApplicationDispositionRules; ApplicationDispositionRulesTest; ApplicationDispositionHttpShapeTest |
| FE sửa | IntakeWorkspace.tsx, SupplementWorkspace.tsx, IntakeWorkspace.module.css; presentation.mjs |
| FE mới | DeclarationSummary.tsx, StaffDossierReview.tsx, PdfPreview.tsx, SupplementHistory.tsx, dossier-types.ts, dossier-model.mjs |
| Kiểm thử | frontend/tests/dossier.test.mjs; frontend/e2e/intake-dev-db.ps1; frontend/e2e/intake-ux-dev.js |
| Tài liệu | Báo cáo này; owner register; HS/SUB/REV contracts và test specifications; API Catalog chung/CTSV; ghi chú delta sớm Backlog/Roadmap. Không đánh lại trạng thái nghiệm thu lịch sử. |

### Giới hạn và đề xuất còn cần duyệt

- **NOT VERIFIED:** cross-owner với hai tài khoản SV riêng, SQL fault injection/rollback và toàn bộ suite P05–P08. Không dùng dataset một SV để chứng minh cross-owner; không nhận controller stub là SQL/HTTP REAL. Hồi quy P05/P06/P07/P08 trong lượt này có reuse policy/round, submit/PDF, supplement/history, rule đánh giá và rejected-source guard, không là nghiệm thu lại các phase.
- Kiểm giấy sâu của P08 vẫn cần thao tác đúng từng loại khi chính sách yêu cầu; Đồng ý hồ sơ không thay thế các kiểm tra đó. Vòng typed cũ có kiểm từng nội dung, còn yêu cầu mới là general đơn giản. Không giả mọi giấy đã đạt để nút xanh.
- Script E2E theo UI cũ cần đối chiếu locator trước khi tái sử dụng; không nhận các suite/harness không chạy ở lượt này là PASS. Bộ regression mới ghi rõ input fixture/session và ctsv_dev; không lưu session/token ra file.
- **Đề xuất để owner quyết định sau:** dời/xét tiếp liên đợt, tổng thể P17; export Excel thật; bộ demo khác owner nếu muốn kiểm chéo đầy đủ. Không tự làm các phần này.
- Không commit/push/merge/deploy. Đây là cập nhật DEV local theo quyền đã giao.
