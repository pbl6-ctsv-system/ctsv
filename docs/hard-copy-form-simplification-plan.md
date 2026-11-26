# Đơn giản hóa form đối chiếu bản cứng MGHP — 26/11/2026

> Owner đã duyệt bố cục/đề xuất trong chat và giao thực thi; dùng executing-plans inline + TDD. Không thêm vòng xin duyệt, commit/push hoặc migration.

**Goal:** chuyên viên xem sinh viên/PDF, kiểm giấy và kết luận điều kiện trong một khu vực, nhập thời gian chứng minh mỗi giấy một lần rồi kiểm tra khoảng hưởng trước duyệt.

**Architecture:** React feature MGHP hiện có, tái sử dụng PdfViewer, XET02/XET06/XET03/XET04 và Foundation GET học kỳ/quá trình. Payload EvaluationContent/guard SQL/permission/history giữ nguyên. Frontend gợi ý/preflight không thay backend quyết định. Chỉ đổi nhánh hồ sơ bản cứng; nhánh rà soát quyền hưởng và bản confirmed giữ dữ liệu cũ.

**Spec:** phê duyệt chat26/11; review-benefits-api.md §6.2/6.3, application-stage-status.md; AGENTS §9/10, frontend/SKILL §12/13.

## Bố cục và quy tắc

- Thông tin sinh viên: dùng bảng đối chiếu hiện có; học kỳ/diện M lấy sẵn từ nguồn, không nhập ID/chọn lại.
- Giấy tờ & điều kiện: chọn PDF để xem ngay; checklist bản cứng giữ đúng từng xác nhận, không tự pass. Nhập khoảng chứng minh một lần theo giấy; chọn giấy bên cạnh tiêu chí và kết luận tiêu chí. Liên kết evidence được dựng từ lựa chọn tường minh; không tự gán mọi giấy/tiêu chí đạt.
- Kết quả evidence liên kết theo kết luận tiêu chí do CV chọn; nhận xét ngoại lệ dùng chung, không bắt nhập thêm lý do dùng cùng giấy. Giấy cũ/ngoài bản nguồn không được tự dùng; common evidence draft cũ vẫn giữ, không xóa im lặng.
- Thời gian: gợi ý bằng các mốc đã xác minh và khoảng giấy CV nhập; đủ min yêu cầu của mọi nhóm giấy cho từng khoảng, không lấp gap hoặc kéo dài quá nguồn. Không MAX nhiều M. Một ô căn cứ thời gian phục vụ timeEvidence/eligiblePeriod.evidence sau CV xác nhận; không tự giả nguồn pháp lý.
- Hỗ trợ điều chỉnh khoảng hưởng khi CV cần thu hẹp; chưa đủ dữ liệu thì báo rõ, không tự lấy cả kỳ. Duyệt có tóm tắt xác nhận; từ chối cần lý do/điều kiện không đạt như contract.
- Readiness liệt kê thiếu giấy/check/tiêu chí/ngày/nguồn; chỉ là hướng dẫn, backend kiểm lại với ETag. Dữ liệu draft/confirmed cũ và lịch sử không bị reset.

## Task1 — Model gộp nhập và gợi ý thời gian

Files: frontend/src/features/mghp/review/hard-copy-model.mjs; frontend/tests/hard-copy-form.test.mjs.

- [x] RED/GREEN: liên kết tiêu chí–giấy tường minh; không tự pass tiêu chí; ngày một giấy cập nhật các uses cùng giấy; không xóa dữ liệu chưa sửa.
- [x] RED/GREEN: gợi ý theo term/process/policy đã xác minh; nhóm AND/min/OR, expiry, inclusive dates, gap; thiếu nguồn không gợi ý giả.
- [x] Readiness rõ theo tác vụ; không dùng checklist như thay mọi điều kiện backend.

## Task2 — Form React

Files: HardCopyAssessment.tsx và CSS cùng feature; ReviewWorkspace.tsx nối form/semester read; giữ old branch benefit và confirmed view. Không router/Navbar/dependency mới.

- [x] Gộp viewer/check/evidence/criteria; ẩn technical references, readonly kỳ/diện, chỉ hiện tuổi khi cần.
- [x] Một vùng căn cứ thời gian, proposal + optional edit, checklist còn thiếu, confirm approve và decline reason.
- [x] Giữ state/ETag/readback sau checks/save/confirm, lỗi nhìn thấy sau cuộn, không tự gửi lại write.
- [x] Focused Node tests + TypeScript/build GREEN.

## Task3 — DEV browser, regression và bàn giao

- [x] Xác minh launcher/ctsv_dev; tái dùng fixture synthetic còn đúng trạng thái, không tạo/reset thêm hồ sơ hoặc DB evidence.
- [x] Browser thật: xem PDF, làm đủ giấy/tiêu chí/ngày → lưu/đọc lại → duyệt; từ chối có lý do; thiếu dữ liệu rõ; mobile; tài khoản thiếu quyền/direct URL. Không mock API nghiệp vụ.
- [x] Regression tập trung review lifecycle/read context/stage handoff; không chạy whole suite hoặc gọi CA.
- [x] Đồng bộ tài liệu UX/test liên quan; ghi lệnh/count/ảnh/giới hạn vào chính file này. G8 WAIVED BY OWNER, không PASS.

## Ledger

- Existing working tree codex/p01-slice-a giữ nguyên; không worktree mới vì cần tiếp tục trực tiếp nguồn/DEV đang được owner dùng, không commit/push.
- Không thay BE business/schema mặc định. Nếu thiếu read data, dùng GET đã có; không tự đặt endpoint.
- Trước code đã đọc UI/service/contract: paper checks tách criteria/evidence/periods; confirm dat đòi criteria bắt buộc, evidence dates/checks đủ nhóm và EligiblePeriod/timeEvidence.
- Interface preflight: helper tạo EvaluationContent hiện hành, component chỉ gọi callbacks của workspace; term detail lấy FND03-R đã có. Không thay consumer/service contracts.

## Kết quả thực thi26/11/2026

### Phạm vi source

- Thêm `frontend/src/features/mghp/review/HardCopyAssessment.tsx`, CSS cùng tên và `hard-copy-model.mjs`; nối riêng nhánh application editable trong `ReviewWorkspace.tsx`. Tái sử dụng shared PDF.js, AuthClient và các command/read API hiện có.
- Thêm `frontend/tests/hard-copy-form.test.mjs` (8 ca model) và harness browser tương tác `frontend/e2e/hard-copy-form-dev.cjs`. Harness giữ browser context và phiên trong RAM; không ghi password/JWT/storage state.
- Đồng bộ phần delta UX tại `nghiepvu/01-thiet-ke/ctsv/api-contracts/review-benefits-api.md` và các ca HC tại `nghiepvu/08-kiem-thu/ctsv/review-benefits-test-cases.md`. Không đổi API payload, BE, schema, migration, Backlog hoặc các quyết định nghiệp vụ.

### Bằng chứng và lệnh đã chạy

| Kiểm tra | Thực tế |
|---|---|
| TDD model mới | RED thiếu module → GREEN7 ca; review phát hiện ca giấy liền nhau bị gộp sai, thêm regression và quan sát RED1/8 → sửa predicate coverage → GREEN8/8 |
| Model + regression FE | Cwd `ctsv/frontend`; `node --test tests/hard-copy-form.test.mjs tests/review-lifecycle.test.mjs tests/p1-review-context.test.mjs tests/stage-handoff.test.mjs tests/application-stage-status.test.mjs`: **24 PASS, 0 FAIL, 0 SKIP** |
| TypeScript/Vite | Cwd `ctsv`; `npm --prefix frontend run build`: exit0. Còn warning chunk >500kB hiện có; không thay bundling/dependency trong task này. Log `target/hard-copy-form-build.log` |
| Browser | Chrome158.0.8127.88 qua Playwright CLI tương tác, FE `https://127.0.0.1:5173`, BE `https://127.0.0.1:8443`, SQL `ctsv_dev` trên `DESKTOP-7PPHP5B\DEV2022` (TCP56826), schemaV32. Hai tài khoản dev.chuyenvien/dev.sinhvien, mỗi tài khoản đăng nhập một lần, tái sử dụng context |
| HTTP/SQL thật | Duyệt XET04 200 → evaluation21 confirmed/dat; từ chối XET04 200 → evaluation22 confirmed/khong_dat; SELECT/assert SQL riêng exit0. API nguồn nội bộ của SV403. Không mock API nghiệp vụ |
| Storage | Xem/chuyển hai PDF20052/20053 từ Cloudinary qua backend CTSV thật; canvas có nội dung. Không upload thêm hoặc gọi CA vì không sửa tích hợp tệp/ký |
| SQL bảo toàn | `sqlcmd -S 'tcp:localhost,56826' -d ctsv_dev -E -C -b ...` chạy SELECT/THROW assertion chỉ đọc. Hash root và assessment ngoài10039/10041 trước/sau bằng nhau;10 receipt và10 output ký số giữ nguyên. DB tiếp tụcV32 |

`target/hard-copy-form-20261126/browser-results.json` ghi **15 PASS, 0 lỗi JavaScript trang**; các ảnh cùng thư mục. Đây là harness tương tác có giữ context để điều tra, không phải bộ fixture reset tự động. Không lưu response nhạy cảm/headers vào evidence.

### Coverage browser đã thực thi

| Ca | Kết quả và phạm vi |
|---|---|
| HC-01/02 | PASS — mở form theo ngữ cảnh thật, PDF render, kỳ/diện readonly, không ô ID/tham chiếu, danh sách thiếu và chặn duyệt |
| HC-03/04 | PASS — từng check lưuHTTP201, không tự pass tiêu chí; hai PDF đúng file, chọn giấy cạnh điều kiện, nhập ngày một lần |
| HC-05 | PASS — giấy1 chứng minh09/2026–01/2027 và giấy2 chỉ09–10/2026 → gợi ý đúng09–10/2026 |
| HC-06/07 | PASS — tạo draft21 HTTP201, đọc lại/refresh/mở tiếp giữ hai liên kết giấy và ngày, không tạo trùng |
| HC-08/09 | PASS — mobile390×844 không tràn ngang; đảo ngày giấy chặn duyệt, sửa tại chỗ tiếp tục được |
| HC-10/11 | PASS — dialog duyệt không có lý do; xác nhận200, SQL đúng, reload mở kết quả readonly |
| HC-12/13 | PASS — từ chối trống lý do bị chặn; hủy không tạo draft; có điều kiện không đạt + lý do thì200, terminalrejected, không sinh khoảng hưởng/đối chiếu lại |
| HC-ACCESS | PASS — SV không mở được route nội bộ, API403 |
| HC-14 | PASS — mở lại draft cũ của10037 và xem PDF; không sửa/lưu dữ liệu, layout desktop/mobile vẫn dùng được |

### Dữ liệu thử đã thay đổi

- Tái dùng đúng hai hồ sơ SYNTHETIC trong đợt20040 đã chốt online, chưa đóng xét: `MAU25-003`/10039 và `MAU25-005`/10041. Không seed hoặc xóa dữ liệu.
- 10039: bổ sung các check giấy qua UI; evaluation21 với6 kết luận điều kiện tường minh,2 EvidenceUse; giấy10078 có01/09/2026–31/01/2027, giấy10079 có01/09–31/10/2026. Kết luận dat,1 khoảng hưởng01/09–31/10/2026; timeEvidence khớp căn cứ khoảng, root bản cứng approved.
- 10041: evaluation22 với điều kiện đúng đối tượng khong_dat có nhận xét, EvidenceUse của giấy10083 khong_dat, lý do `SYNTHETIC HC26...`; không khoảng hưởng. Root bản cứng rejected/rejectionStage hard_copy. Giữ kết quả để owner xem, không rollback dữ liệu kiểm thử bằng SQL.
- Không chốt đợt, tạo QĐ, PDF mới hoặc thay hồ sơ/đánh giá khác. SQL assertions tại `sql-persistence.txt`; fingerprint tại `unaffected-before.txt`/`unaffected-after.txt` trong thư mục evidence.

### Review, lỗi script và giới hạn

- Fresh read-only review theo executing-plans phát hiện một lỗi coverage: giấy tháng9 + giấy tháng10 không được tạo một khoảng gộp nếu không có căn cứ bao phủ toàn khoảng. Đã sửa và chạy regression RED/GREEN; reviewer xác nhận finding được khắc phục.
- Một locator sau reload chờ “Bắt đầu đối chiếu” đã timeout vì hồ sơ vừa duyệt chuyển đúng thành “Xem kết quả đối chiếu”. Giữ context, kiểm DOM và đọc kết quả readonly thành công; lỗi script, không gửi lại command ghi.
- Ảnh đầu chụp khi readback/PDF còn loading được chụp lại sau khi form hết busy/canvas có nội dung. Một lần GET bổ sung bằng token cũ của helper trả401 sau token hết hạn; frontend vẫn dùng phiên/refresh hiện hành, không đăng nhập lại hoặc coi đó là lỗi business.
- M3/tuổi, các cấu hình min/OR/AND/gap/expiry có model tests; không chạy browser cho mọi M. Không chạy lại Maven/full P05–P11/concurrency/rollback SQL vì không sửa backend hoặc transaction; không tuyên bố các gate đó đã được chạy mới.
- PDF và kết luận đều SYNTHETIC: bằng chứng xác nhận vận hành giao diện/payload/persistence, không xác nhận đủ căn cứ pháp lý của hồ sơ thật. G8 **WAIVED BY OWNER**; owner nghiệm thu UX trực tiếp riêng.

**Bàn giao:** thay đổi trong phạm vi này đã triển khai và kiểm ở DEV. Có thể mở hồ sơ10037 để xem form đang làm dở,10039 để xem bản đã duyệt và10041 để xem từ chối có lý do. Không tự chốt đợt hoặc triển khai phase tiếp theo. Không commit/push/merge.
