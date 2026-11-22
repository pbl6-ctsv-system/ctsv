# Sửa hành trình sinh viên MGHP — 21/11/2026

> **Delta tài liệu mới nhất — P17 DEFERRED BY OWNER:** [quyết định trạng thái hồ sơ](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#owner-20261121-application-status) và [ghi chú xét tiếp/giữ lịch sử](#application-status-proposal-20261121) ở cuối. Owner đồng ý hướng giữ lịch sử, chỉ ghi nhận triển khai sau tại P17; chưa cần làm rõ edge case/wire hiện tại. Chưa triển khai delta vào code/DB; các số PASS UI trước đó không là bằng chứng cho trạng thái mới.

> **Quyết định UI mới nhất:** owner yêu cầu khôi phục dropdown “Quá trình học”. Phần chặn nộp khi có nhiều quá trình của delta trước đã được thay thế bằng lựa chọn tường minh của sinh viên; xem mục cuối. Không tự chọn dòng đầu khi có nhiều lựa chọn. Không cần cơ chế mặc định/API/DB mới để xử lý lượt này.

Yêu cầu owner: sửa menu/danh sách dễ hiểu nhầm toàn trường, tìm được đường nộp ngay từ đợt, bố cục rõ; tự rà soát và trình duyệt các bổ sung ngoài phạm vi. Không triển khai P10, không Git write. Dùng using-superpowers → systematic-debugging/TDD và React practices; sửa tối thiểu trong App navigation/MGHP, không thay Auth hay API/DB.

## Chẩn đoán và hướng sửa

- AUTH04 + Foundation menu thực tế trả student=true, mghpSpecialist=false, ctsvRoleAdmin=false. HS02 backend lọc Identity.studentId trước count/page. DEV hiện19 hồ sơ, cả19 cùng studentId1; không có fixture khác owner để kết luận ca404 cross-owner đã chạy.
- Menu student và specialist dùng chung nhãn “Rà soát quyền hưởng”; student view thực ra own-benefits. Đổi nhãn theo capability/view thành “Quyền hưởng của tôi”; student hồ sơ thành “Hồ sơ của tôi”. Không loại bỏ quyền theo dõi hồ sơ của chính SV đã được HS02/HS03 duyệt. Không thêm tên Role để cấp quyền.
- StudentRoundDetails chỉ liệt kê giấy, không có CTA. Giữ route hiện hành, link kê khai mang dot_xet_id đến IntakeWorkspace; tự tải HS01 đúng đợt, hiển thị rõ đợt được chọn; refresh giữ đúng đợt. Backend vẫn kiểm server clock/window/ownership khi nộp.
- Bố cục chi tiết: tên/trạng thái/hạn và hành động trên cùng → hướng dẫn3bước → nhóm M/giấy dạng disclosure; thông tin mã đối chiếu phụ. Đợt chưa đến giờ/đã hết hạn/closed/completed vẫn xem được và phải có lý do chưa nộp rõ ràng. Cửa ở UI chỉ hỗ trợ, không thay server validation.
- Danh sách của tôi dùng tên đợt đã tải hợp lệ và mã hồ sơ ở phần phụ; giữ pagination server, không xóa/ẩn fixture theo mã để giả danh sách đúng. Không thay dữ liệu thật/test cũ.

## Kế hoạch và nghiệm chứng

- [x] RED: 3 Node tests thất bại đúng nhãn cũ và thiếu helper window; audit browser xác nhận menu cũ. Source StudentRoundDetails không có CTA. Browser bắt thêm lỗi Alert cũ che nút sau khi đã sửa lời khai.
- [x] FE: sửa navigation nhãn, StudentRoundDetails/CSS, helper window/link; IntakeWorkspace deep link/own labels/list; giữ request/ETag/operationId/upload/withdraw. Lỗi local validation được xóa khi sửa form, không timeout tự mất; không sửa Shared Feedback.
- [x] GREEN: toàn bộ Node FE suite30PASS; build/typecheck PASS; browser8flow PASS REAL launcher ctsv_devV18: menu theo capability; CTA open/future/closed, refresh/cancel; readonly đối chiếu own IDs với SQL, staffURL403; upload/nộp/download/rút synthetic bằng UI hiện có; lỗi nhìn thấy và mobile360.
- [x] Report evidence, limits, đề xuất cần owner approval riêng. Không nhận screenshot là assertion hoặc test thiếu fixture là PASS.

Nguồn: mghp-application-api HS01/02/03; submission-supplement-api HS05-C/HS06 và window inclusive; Round public projection; AGENTS §4/8, frontend/SKILL §7/11, Testing Strategy, Quality Gates G0/G7. G8 không được gọi PASS; không mở lại phase nghiệm thu.

## Vì sao evidence trước chưa bắt được

RF06 của round-form chỉ kiểm đọc public round, có minh chứng và chặn direct URL chính sách; không bấm đường từ đợt sang kê khai. RF01–RF07 không phải kiểm toàn hành trình sinh viên. Dataset DEV có19 hồ sơ nhưng cùng studentId1 nên chỉ so số lượng giữa tài khoản student/staff sẽ không bắt được lỗi cross-owner nếu có. Lần này đối chiếu SQL thật, capability và projection; không dùng dataset đó để tuyên bố ca sai owner404 đã PASS. Node/API pass không chứng minh bố cục/CTA dễ sử dụng. Đây là thiếu sót coverage và bàn giao của Codex.

## Phạm vi source thay đổi

| File | Thay đổi |
|---|---|
| frontend/src/app/navigation/modules.mjs | Nhãn cá nhân theo capability/view; nhóm quyền lợi cho SV; không đổi quyền/route |
| frontend/src/features/mghp/presentation.mjs | Hướng dẫn cửa nhận inclusive ở UI; server tiếp tục kiểm thời gian thật |
| frontend/src/features/mghp/configuration/StudentRoundDetails.tsx và CSS cạnh file | CTA, hạn, ba bước, nhóm giấy mở/đóng, retry yêu cầu giấy, responsive |
| frontend/src/features/mghp/intake/IntakeWorkspace.tsx | Nhận dot_xet_id, HS01 đúng đợt, refresh/cancel, chỉ chọn đợt trong cửa, tên danh sách cá nhân |
| frontend/src/features/mghp/intake/DeclarationForm.tsx | Bổ sung type từ PublicRound hiện có và xóa lỗi local khi sửa input; không đổi payload |
| frontend/tests/student-journey.test.mjs | 3 test nhãn/student/staff/multi-capability/window |
| frontend/e2e/student-journey-dev.mjs và run-student-journey-dev.ps1 | Guard SQL, login private config, browser/API REAL, PDF mẫu, hash, lỗi/quyền/mobile |
| frontend/e2e/navigation.mjs và scripts P06/P07/P08/modules/ui-workspaces/mghp-navigation-access | Đồng bộ selector nhãn menu mới; không thay expected nghiệp vụ hoặc tuyên bố đã chạy lại các suite đó |
| docs/student-mghp-journey-review.md | Kế hoạch, kết quả, scope và đề xuất cần duyệt |

Không sửa Backend, REST contract, Entity, Repository, Service, migration hay cơ chế JWT/CSRF/CORS. DB chỉ nhận dữ liệu synthetic do thao tác UI kiểm thử; không đổi schema, không reset/clean/drop/xóa dữ liệu cũ. AppShell/router không tái cấu trúc. Giữ mọi thay đổi dang dở trước task.

## Đề xuất cần owner duyệt — chưa triển khai

1. **Chọn học kỳ theo tên thay vì nhập ID/phân tách dấu phẩy.** DeclarationForm vẫn có ô “ID học kỳ đề nghị”. Đề xuất multi-select dùng Foundation semesters API đã có, giữ requestedSemesterIds và validation backend. Không tự hạn chế số kỳ hoặc suy kỳ được hưởng. Phạm vi FE riêng, không chặn bản sửa hiện tại.
2. **Trang quyền hưởng cá nhân dùng nguồn đọc phù hợp.** Hiện ReviewWorkspace own-benefits chỉ có ô “Mã quyền hưởng cần xem”; BenefitController chỉ đọc id và danh sách can-ra-soat dành CV. Không có danh sách riêng SV trong runtime này. Khuyến nghị hoàn thiện hành trình kết quả cá nhân với nguồn Portal ở phase được giao; nếu cần API mới phải duyệt contract trước. Không dựng list giả hoặc lấy list chuyên viên rồi lọc ở FE.
3. **Tách dữ liệu DEV demo và fixture nhiều tài khoản.** Có19 hồ sơ cùng tài khoản1, gồm mã TLX/P07 khó hiểu. Đề xuất bộ demo nhỏ đặt tên rõ và một SV thứ hai để kiểm cross-owner list/detail/document. Chưa xóa/chuyển owner/đổi tên dữ liệu cũ hoặc tự tạo tài khoản thứ hai. Ca HTTP404 khác owner trong lượt này vẫn NOT VERIFIED nếu không có fixture.

Giới hạn bổ sung: selector tên menu của các test cũ đã đồng bộ; test timeline-extended là baseline V17 trước V18, có guard/fixture và bước chọn đợt future cần port lại trước khi tái dùng trên UI mới. Không nhận các test cũ chưa chạy là PASS. Đồng hồ client chỉ quyết định hướng dẫn/hiện CTA; server là nơi chấp nhận hoặc từ chối lệnh nộp.

## Kết quả thực thi và coverage

| Ca | Hành vi được assert | Kết quả mới |
|---|---|---|
| SJ01 | Menu cá nhân, không có nhãn/tác vụ CV; URL tiếp nhận bị chặn; API can-ra-soat trả403; HS02 SV không có StaffApplicationSummary fields | PASS |
| SJ02 | Đợt công khai20033 chưa đến opensAt: lý do rõ/nút disabled; deep link không mở form để nộp sớm | PASS |
| SJ03 | CV tạo và công khai đợt synthetic qua UI, dùng policy locked hiện có,3mốc cũ và8planned null | PASS |
| SJ04 | Chi tiết đợt có CTA → HS01 đúng dot_xet_id → form; refresh giữ đợt; hủy xóa query và refresh không mở lại | PASS |
| SJ05 | Viewport360 không tràn ngang; thiếu họ tên báo lỗi trong viewport; điền lại xóa lỗi local, nút không còn bị toast chặn | PASS |
| SJ06 | Chọn PDF mẫu, POST upload201 Cloudinary REAL; gắn giấy; POST hồ sơ201; GET xác minh đúng round/file và UI đã tiếp nhận | PASS |
| SJ07 | Click tải PDF trong lịch sử, SHA256 trùng gốc; POST rút200; GET/chi tiết/dòng danh sách đều withdrawn | PASS |
| SJ08 | CV đóng nhận200; SV thấy lý do đóng/không CTA nộp; xem thông báo và logout | PASS |
| Cross-owner DEV | Hồ sơ thật thuộc sinh viên thứ hai | NOT VERIFIED — DEV chưa có fixture khác owner; không tính vào8PASS |

`npm.cmd --prefix frontend test`:30PASS/0FAIL/0SKIP. `npm.cmd --prefix frontend run build`: tsc --noEmit + Vite PASS,81modules, giữ dependencies. Không chạy test Backend hoặc toàn P00–P08 vì không đổi implementation BE. SQL thật xác minh owner/marker và các kết nối của PID Backend20548 đều dùng ctsv_dev; Flyway V1–V18 checksum/success không đổi,7account không đổi,19hồ sơ cũ/owner giữ nguyên.

Các lượt thử không bị giấu: attempt1 dừng vì selector exact của select chứa cả option; attempt2 bắt bug toast che nút, đã sửa form và có assert hồi quy; attempt3 upload thật thành công rồi dừng ở selector PDF tương tự; attempt4 nộp/download/rút thật thành công nhưng Chrome không trả response body cho helper sau rút. Helper mới không đọc body không dùng, giữ POST200 và bổ sung GET status + UI/list withdrawn. Lượt cuối8flowPASS,0pageerror; rate limit được chờ đúng Retry-After, không restart BE/đổi limiter. Không force-click, bỏ assertion hay sửa expected nghiệp vụ.

MCP bổ sung: tạo đợt demo20037 bằng click/nhập UI, POST201 và công khai200; đăng nhập SV, mở đúng đợt thấy CTA trỏ `/mghp/ho-so?dot_xet_id=20037` và2nhóm policy. Ảnh MCP đã xem. Một lần thao tác demo quên điền password nên HTML validation không phát request; một lần thử private-config import bị VM từ chối, không có API gọi. Đã hoàn tất lại bằng tài khoản DEV synthetic được owner chấp thuận. Không coi các thao tác thất bại đó là PASS.

## Dữ liệu và cách dùng thử

- Account SV hiện hành `dev.sinhvien`; không tạo/reset account hay credentials.
- **Đợt dùng thử kê khai MGHP — SYNTHETIC**, mã `SV-DEMO-20261121`,id20037, học kỳ1, M1version8/M2version10012 từ bản đã xác nhận; đang nhận đến16:27 ngày28/11/2026,giờ Việt Nam. Có0hồ sơ trước bàn giao. Chọn MIỄN GIẢM HỌC PHÍ → Đợt xét MGHP → đợt này → Kê khai và nộp hồ sơ. Chọn M phù hợp để tập luồng, không coi PDF giả lập là bằng chứng hợp pháp.
- Đợt test20035 còn open và hồ sơ10018 đã rút (lượt thử trước); đợt20036 closed, hồ sơ10019 đã rút (lượt8flowPASS). Không xóa lịch sử để làm số liệu đẹp. Tổng hồ sơ sau kiểm21, tất cả vẫn cùng studentId1.
- File10022 upload thật nhưng chưa gắn hồ sơ do script dừng; file10023/10024 gắn đúng hồ sơ10018/10019. Không tự xóa cloud file/ledger; reconciliation và retention theo implementation hiện hành. Không có CA calls/STUB trong task.
- PDF nguồn `../nghiepvu/05-du-lieu-mau/du lieu test.pdf`; download hash `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46` trùng bytes gốc.

## Lệnh và bằng chứng

CWD CTSV: `npm.cmd --prefix frontend test`; `npm.cmd --prefix frontend run build`; `pwsh -NoProfile -File frontend/e2e/run-student-journey-dev.ps1 verify`. Wrapper đọc credential bằng DPAPI ngoài Git, guard ctsv_dev/server/owner/marker/V18 trước browser. Test này tạo và sửa duy nhất dữ liệu synthetic, không chạy trên DB evidence.

[Report8flow](../target/student-journey-dev-20261121/verify-report.json), [Node tests](../target/student-journey-dev-20261121/frontend-test.log), [build/typecheck](../target/student-journey-dev-20261121/frontend-build.log), [SQL trước](../target/student-journey-dev-20261121/verify-db-before.json)/[sau](../target/student-journey-dev-20261121/db-after.json), [kết nối BE](../target/student-journey-dev-20261121/backend-sql-connections.json), [ảnh desktop](../target/student-journey-dev-20261121/receiving-round-desktop.png)/[mobile](../target/student-journey-dev-20261121/receiving-round-mobile.png), [ảnh demo MCP](../target/student-journey-dev-20261121/demo-student-desktop.png), [manifest](../target/student-journey-dev-20261121/manifest.json).

**Kết luận:** lỗi điều hướng/nhãn/CTA/bố cục và lỗi local validation chặn nút đã được sửa và kiểm chứng trong phạm vi nêu trên. Các đề xuất1–3 chờ owner review. Không thay trạng thái phase/waiver, không mở P10, không stage/commit/push/merge/deploy.

## Delta form đơn giản — owner duyệt21/11/2026

Owner chốt: dropdown “Bạn thuộc diện miễn giảm nào?”; bỏ các ô đơn vị quản lý, ID học kỳ, đã hưởng cùng trình độ; quá trình học tự xác định và không hiển thị. Chỉ sửa FE, giữ payload/DB/API/ownership, replay và history. Nộp mới lấy đơn vị từ HS01, kỳ từ đợt, lời khai đã hưởng=null (không tự false); nộp lại/bổ sung giữ giá trị đã kê khai và danh sách kỳ của bản nền. Một quá trình thì tự chọn; nhiều/thiếu mà không có target xác định thì báo cần CTSV xác minh, không chọn đại dòng đầu. M chọn theo dropdown, tự chọn chỉ khi có đúng1diện hoặc target đã ràng buộc. Người liên quan chưa có phê duyệt thay workflow riêng trong delta này; giữ luồng hiện có.

- [x] Test tập trung defaults: một/nhiều/không process; một/nhiều diện; nullable và giữ dữ liệu lịch sử.4ca RED assertions →4GREEN; cùng5regression liên quan, tổng9PASS/0FAIL/SKIP.
- [x] DeclarationForm + helper: ẩn ô đã chốt, default nguồn đúng; dropdown có label/required; thiếu dữ liệu không gửi request sai. Không còn fetch danh mục units chỉ để dựng ô đã ẩn; lời nhắc lỗi không yêu cầu nhập ID ẩn.
- [x] Browser REAL ctsv_dev:5ca về field ẩn, dropdown đổi diện, guard nhiều process có thông báo trong viewport, mobile360 và direct URL CV bị chặn. Dùng lại đúng browser context/phiên hiện có, không đăng nhập lại, không lưu token/password vào file. Baseline DOM trước sửa có đủ4ô kỹ thuật và label chính sách cũ. Build/typecheck PASS; không chạy toàn P00–P08, không upload và không ghi nghiệp vụ/DB trong lượt này.

**Owner đã trả lời trường hợp nhiều quá trình qua chat:** “Tự chọn nếu chỉ có1; nhiều thì yêu cầu CTSV xác định”. API DEV hiện trả8process cho SV1 (1process DEV,7process TLX từ các fixture); không có trường default/current đáng tin cậy để chọn hộ. Trường hợp này chặn tiếp tục/nộp bằng UI, báo CTSV xác minh; không xóa fixture, không sửa nguồn/Foundation hoặc tự chọn đầu danh sách. Không hiện lại dropdown quá trình học. Khi có target nộp lại/bổ sung thì giữ process đã ràng buộc của root.

**NOT VERIFIED:** nộp mới REAL với form đơn giản trên SV DEV hiện tại, vì guard8process chặn đúng quyết định owner. Không lấy9unit/5browser để tuyên bố đã submit201, không tái dùng8flowPASS của phần trước như evidence delta mới. Logic defaults cho trường hợp1process và giữ payload lịch sử đã được unit test; chưa có browser fixture1process trong lượt này. Lời khai đã hưởng mới=null, vẫn giữ key; bản nộp nền có true/false được bảo toàn. Người liên quan vẫn theo luồng hiện hành, chưa tự thêm suy luận phân loại M từ nhãn tên.

Files delta: DeclarationForm.tsx, declaration-model.mjs mới, declaration-defaults.test.mjs mới, student-form-simple.js mới và mục báo cáo này. Không sửa IntakeWorkspace, Backend/Auth/API contract/migration/database. Limiter mặc địnhfalse được giữ nguyên; launcher hiện dùng BE6896/FE25800 trên https://127.0.0.1:5173 và ctsv_dev. Không restart/migrate/build BE hoặc gọi Cloudinary/CA.

Evidence: [targeted9tests](../target/student-form-simple-20261121/targeted-tests.log), [build/typecheck](../target/student-form-simple-20261121/build.log), [Playwright5ca](../target/student-form-simple-20261121/browser.json), [ảnh mobile](../target/student-form-simple-mobile.png). Tái chạy phần browser bằng Playwright MCP browser_run_code_unsafe filename=`frontend/e2e/student-form-simple.js` trong phiên SV đã đăng nhập. Script dùng fixture DEV20037 có sẵn; không tạo round/PDF/account mới. Các script lịch sử dùng locator của các ô đã bỏ cần được port trước khi tái dùng; không nhận là đã chạy hoặc PASS trong lượt này.

## Khôi phục lựa chọn quá trình học — quyết định owner mới nhất21/11/2026

Owner yêu cầu đưa quá trình học trở lại để tránh phải chỉnh sửa nhiều. Đã thêm dropdown required lấy đúng `studyProcesses` từ HS01, dùng state `process` cho payload như trước. Có1process vẫn tự chọn; nhiều process bắt buộc SV chọn. Nộp lại/bổ sung giữ target và khóa dropdown để không đổi root identity. Không còn setupError chỉ vì có nhiều process; nếu hoàn toàn thiếu dữ liệu quá trình hoặc dữ liệu hệ thống khác thì vẫn báo rõ. Các ô đơn vị quản lý, học kỳ và đã hưởng cùng trình độ tiếp tục ẩn, dropdown diện miễn giảm giữ nguyên.

TDD browser: test mới thất bại `RED: Missing study process dropdown` trên form cũ; sau sửa5ca Playwright PASS trên daily DEV ctsv_dev. SF03 kiểm required khi chưa chọn → lỗi trong viewport → chọn DEV-PROCESS-001 → lỗi được xóa → Tiếp tục vào Người liên quan → quay lại vẫn giữ process1. SF01/02/04/05 kiểm các ô ẩn còn lại, diện miễn giảm, mobile360 không tràn ngang và SV bị chặn route chuyên viên.4unit defaults/history PASS; build/typecheck PASS. Lượt đầu browser gặp phiên cũ hết hiệu lực REFRESH_INVALID nên không có HS01; đã giữ context, đăng nhập lại một lần rồi kiểm đúng ca, không thay Auth/limiter.

Files sửa lượt này: DeclarationForm.tsx, student-form-simple.js và báo cáo này. Không sửa Backend/API/DB/helper defaults, không xóa dữ liệu hoặc tạo fixture/round/account/PDF. Không upload lại để kiểm điều hướng. Không chạy lại toàn suite. Chưa lặp nộp/upload/download end-to-end; bằng chứng mới xác minh bước chọn quá trình và chuyển bước, không gán submit201 cho lượt này.

[Browser mới](../target/restore-process-20261121/browser.json), [unit4ca](../target/restore-process-20261121/unit.log), [build/typecheck](../target/restore-process-20261121/build.log), [ảnh mobile hiện hành](../target/restore-process-20261121/mobile.png). Với tài khoản DEV hiện có, chọn “Quá trình học” → DEV-PROCESS-001, chọn diện, nhập đủ thông tin rồi Tiếp tục. Dữ liệu TLX cũ vẫn được bảo toàn. Không commit/push/merge.

<a id="application-status-proposal-20261121"></a>
## Trạng thái hồ sơ, xét tiếp và giữ lịch sử — 21/11/2026

**P17 — DEFERRED BY OWNER; chỉ ghi chú, chưa sửa code/DB.** [Owner decisions](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#owner-20261121-application-status) là APPROVED BUSINESS. Owner đã đồng ý hướng tránh thao tác lại/giữ lịch sử; cơ chế persistence, command, enum và UI chi tiết bên dưới vẫn là đầu vào dự kiến cho [P17](../../nghiepvu/09-trien-khai/master-implementation-backlog.md#p17-deferred), không yêu cầu làm rõ ngay hoặc chặn P10–P16. Evidence PASS trước mục này thuộc các lượt UI cũ, không chứng minh delta trạng thái mới hoạt động.

### 1. Quyết định đã chốt và hiện trạng nguồn

- Đã duyệt: chuyên viên đồng ý hồ sơ, không gộp kết quả từ chối vào nhãn này; không tự là quyền hưởng chính thức/đã ký/đã công bố.
- Từ chối: kết thúc hồ sơ đó vì không đủ điều kiện, không nộp lại/mở lại/dời để sử dụng tiếp. Giữ quyền tra cứu lịch sử; không suy thành cấm sinh viên mọi hồ sơ hoặc M khác.
- Dời xét sang đợt sau: chuyên viên lấy hồ sơ cũ xét tiếp, không yêu cầu sinh viên tạo lại hồ sơ hay nộp lại toàn bộ chỉ vì dời.
- Phân biệt Cần bổ sung với Sinh viên đã bổ sung. Yêu cầu mở lại chỉ là hướng tương lai, chưa xây chức năng hoặc dùng nó để vượt trạng thái Từ chối.
- **Cách xét đơn giản đã chốt:** SV không chọn số kỳ muốn được miễn; CTSV xác định kỳ/khoảng hưởng dựa trên căn cứ và thời hạn minh chứng. Đủ điều kiện một kỳ thì hồ sơ vẫn Đã duyệt, chỉ hưởng kỳ/khoảng đó. Kỳ tiếp theo vẫn nộp hợp lệ bình thường và phải nộp lại minh chứng của kỳ đó. Không tạo trạng thái duyệt một phần vì SV đề nghị nhiều kỳ; edge case khác để sau.

Đối chiếu source hiện hành: `EvaluationService.confirm()` xác nhận đánh giá; `touchParent()` gọi `repo.change(root, root.status, ...)`, giữ trạng thái root nên có thể vẫn Đã nộp. `SupplementService` ghi vòng `responded` nhưng giữ trạng thái root; khi kết thúc vòng ban đầu mới về `processing`. `ApplicationService` hiện chỉ nộp/nộp lại/rút/mở lại theo baseline. Vì vậy không thể sửa đầy đủ bằng cách đổi tên nhãn frontend. Chưa thực thi runtime cho delta này.

### 2. Hướng tránh sinh viên thao tác lại — owner đã đồng ý, chi tiết để P17

| Phương án | Ưu điểm | Rủi ro / khuyến nghị |
|---|---|---|
| Chỉ đổi trạng thái và đổi `roundId` của hồ sơ sang đợt mới | Ít thay đổi trước mắt | Mất ý nghĩa đợt nộp gốc, có thể sai unique/source/policy và lịch sử; không khuyến nghị. |
| Giữ root, tạo một lượt xét tiếp gắn đợt nhận mới | Không tạo lại toàn bộ chỉ vì dời; giữ bản khai/đánh giá cũ và truy vết hai đợt | Hướng đã được owner đồng ý. Vẫn phải nộp minh chứng từng kỳ khi muốn xét thêm kỳ; wire/schema/guard cụ thể để P17, chưa có bảng/endpoint chính thức. |

Luồng đề xuất:

1. CV chọn Dời xét, ghi lý do; hệ thống ghi sự kiện và ngừng đưa hồ sơ này vào tập nguồn hưởng mới của đợt cũ. Lý do bắt buộc là **khuyến nghị cần duyệt wire**, không tự nhận owner đã duyệt chi tiết này.
2. Có thể chưa chọn đợt đích nếu đợt sau chưa được tạo. Khi đợt phù hợp sẵn sàng, CV mở danh sách hồ sơ đang chờ xét tiếp → chọn hồ sơ → chọn đợt nhận → xác nhận. Đây là thao tác đề xuất, chưa có route/API được duyệt. Không tự chuyển theo đồng hồ.
3. Giữ cùng applicationId, sinh viên, quá trình học và M; giữ đợt gốc/ngày nộp/phiên bản chính sách lúc nộp. Ghi riêng đợt nhận xét tiếp và ngữ cảnh kỳ/phạm vi/phiên bản áp dụng đã kiểm tra. Không copy root hoặc sinh bản nộp giả khi sinh viên chưa gửi gì.
4. CV đọc lại bản khai/PDF đã có khi xét tiếp hồ sơ đang dời. Tái sử dụng tài liệu còn phù hợp trong cùng ngữ cảnh, không bắt tạo lại toàn bộ. **Nếu xét hưởng thêm kỳ thì SV phải nộp lại minh chứng của kỳ đó theo quyết định mới**, không lấy việc dời/tái dùng hồ sơ làm ngoại lệ miễn nộp. Không tự kéo dài hưởng hoặc copy kết luận đạt sang kỳ mới; wire cụ thể để P17.
5. Tạo đánh giá/revision mới khi cần theo quy tắc bất biến đã có; giữ kết luận cũ gắn đúng lượt/nguồn. SV thấy cùng hồ sơ, trạng thái chờ xét tiếp/đang xử lý và lịch sử liên đợt, không có nút tự chuyển đợt.

### 3. Lưu lịch sử tối thiểu

- Hồ sơ gốc + bản khai đã nộp + giấy đã gắn là nguồn lịch sử. Không đổi nội dung, đợt gốc, ngày nộp hoặc PDF để làm lại quá trình xét.
- Mỗi lần duyệt/từ chối/dời/nhận xét tiếp ghi sự kiện append-only: hồ sơ, trạng thái trước/sau, actor, server time, lý do theo quy tắc sẽ duyệt; sự kiện dời/nhận có đợt đi/đến, source version và ngữ cảnh xét. Dữ kiện có kiểu rõ, không giấu toàn bộ trong JSON audit.
- Lượt xét tiếp liên kết bản nộp/đánh giá nguồn thay vì sao chép. Lịch sử một dòng ví dụ: “Đợt A: dời → Đợt B: tiếp nhận xét tiếp → yêu cầu bổ sung → SV phản hồi → CV duyệt”. Ví dụ minh họa, không phải dữ liệu đã ghi.
- Hiển thị lịch sử cho SV chính chủ ở mức phù hợp, CV theo capability; không lộ ghi chú xét nội bộ. PDF tải lại vẫn qua backend kiểm quyền, không phát URL Cloudinary có quyền vượt phạm vi.
- Không xóa hoặc sửa các đánh giá confirmed, nguồn đã Đóng xét, snapshot, PDF/chữ ký/công bố cũ. Nếu đã có quyết định chính thức thì không dùng trạng thái hồ sơ mới để sửa/thu hồi ngầm quyền hưởng; xử lý theo phạm vi quyết định đã chốt.

### 4. Guard và tác động cần thiết khi triển khai

- Một lượt xét đang hoạt động cho một hồ sơ là ràng buộc **đề xuất kỹ thuật**; retry/hai tab không tạo hai lượt hoặc hai kết quả. Mutation + chuyển trạng thái + history/audit cùng TX, có rollback khi ghi lịch sử lỗi.
- Đề xuất chặn nhận xét tiếp nếu đợt đích đã có hồ sơ/lượt xét trùng SV + quá trình học + M; không tự gộp, xóa hoặc chọn một bản để ghi đè. Vòng bổ sung còn open/responded phải được xử lý theo quy tắc hợp lệ trước khi chuyển; không chuyển vòng ngầm hoặc tự hoàn tất phản hồi để vượt guard.
- Backend kiểm owner/capability, trạng thái, nguồn/ETag/CAS và receiving context; lock order thống nhất với Đóng xét/bổ sung/đánh giá. Không dùng kiểm `if` ngoài transaction để chống race.
- Từ chối là terminal cho việc tái sử dụng hồ sơ đó: chặn nộp lại, mở lại, tạo bổ sung, đánh giá/cấp hưởng mới và nhận xét tiếp; không xóa bản ghi/file. Replay kết quả đã commit không được tạo mutation mới hay hồi sinh root.
- Không lấy hồ sơ đang dời hoặc bị từ chối vào nguồn đủ điều kiện của đợt cũ; không nhân đôi nhóm process/kỳ/scope đã công bố. Việc loại khỏi tập hiện tại không được viết lại snapshot lịch sử.
- Không dùng `periodic_update` của quyền hưởng kỳ sau để thay nghiệp vụ dời hồ sơ chưa được duyệt. Root/policyVersion gốc giữ nguyên; khả năng dùng ở đợt mới phải được đặc tả trước khi mở guard hiện hành.
- Đã duyệt/Từ chối/Dời là kết quả xử lý hồ sơ; Cần bổ sung/SV đã bổ sung có thể là projection từ trạng thái vòng. Tên enum và việc lưu hay suy ra từng nhãn chưa chốt. List/detail/filter/count/ETag/action availability phải cùng nguồn server, FE cập nhật sau thành công và refresh không mất kết quả.
- Không bulk-map `completed` cũ thành Đã duyệt/Từ chối; cần đối chiếu kết luận/nguồn hoặc giữ nhãn legacy rõ ràng. Migration nếu cần phải additive, Entity đồng bộ và kiểm schema, không sửa migration đã áp dụng.

### 5. Chốt phạm vi đơn giản; chi tiết hoãn đến P17

**Đã đóng câu hỏi nhiều kỳ:** đủ điều kiện một kỳ vẫn là Đã duyệt, chỉ hưởng kỳ/khoảng được CTSV xác minh; kỳ tiếp theo nộp minh chứng lại. Không yêu cầu owner chọn cách tổng hợp nhiều kỳ hoặc thêm nhãn duyệt một phần. Không dùng điểm này làm blocker trước mắt.

**Không yêu cầu làm rõ thêm lúc này.** Các chi tiết đợt nhận, enum, lý do, ETag/command, xử lý legacy và edge case chỉ giữ làm đầu vào cho P17, sẽ review khi được giao triển khai; không coi hướng đã đồng ý là wire API/schema đã được duyệt. Không tự sửa `requestedSemesterIds`, quy tắc kỳ trước hoặc Q01–Q08 trong baseline runtime hiện có. Quyền hưởng chính thức vẫn theo P10 dữ liệu → P11 ký → P12 công bố → P13 Kế toán.

### 6. Phạm vi tài liệu và kiểm chứng

Đồng bộ owner register, nghiệp vụ/logic dữ liệu, HS/SUB/REV contracts và test specifications; dành mục giữ chỗ **P17 — DEFERRED BY OWNER** trong Backlog/Roadmap, chưa phân rã P17-xx. Cơ sở138task chi tiết P00–P16/PRE-P10-01 không đổi; thêm1mục P17, tổng139mục nếu tính giữ chỗ. **12 ca dự kiến** HS-STATUS-01–04, SUB-STATUS-01–04, REV-STATUS-01–04 để P17 review sau, đều NOT RUN; không là ca bắt buộc mới của P10–P16 và không đổi actual/evidence lịch sử.

Khi được giao code: targeted rule tests → SQL/HTTP authorization/rollback/race thật → Playwright CV/SV theo hành trình và direct URL → regression tập trung nộp/bổ sung/đánh giá/Đóng xét. Browser hằng ngày dùng ctsv_dev/synthetic được cấp phép, các ca fixture cố định/phá dữ liệu dùng DB riêng có guard. Chưa chạy test runtime, ghi DB, gọi storage/CA hoặc đổi source trong lượt tài liệu này.
