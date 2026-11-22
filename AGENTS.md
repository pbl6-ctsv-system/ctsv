3. Skills và quy trình làm việc

Bộ kỹ năng Superpowers đã được cài đặt toàn cục. Sử dụng skill using-superpowers để xác định quy trình phù hợp và chỉ tải các skill thực sự cần thiết.

Đối với những thay đổi liên quan đến Java/Spring Boot, sử dụng java-spring-best-practices làm hướng dẫn về ngôn ngữ và framework, kết hợp với quy trình Superpowers phù hợp.

Đối với những thay đổi liên quan đến React/Next.js, chỉ sử dụng vercel-react-best-practices khi frontend thực tế của dự án sử dụng React/Next.js. Việc cài đặt skill không đồng nghĩa với việc đã quyết định công nghệ frontend.

Khi phát triển một tính năng: tìm hiểu nghiệp vụ đã được phê duyệt, lập kế hoạch nếu thay đổi không đơn giản, triển khai thành những phần nhỏ có thể kiểm chứng, chạy kiểm thử và rà soát kết quả.

Khi sửa lỗi: phải tái hiện lỗi và xác định nguyên nhân trước khi sửa code; sau đó xác minh lỗi đã được khắc phục.

Trước khi tuyên bố hoàn thành: phải có bằng chứng kiểm chứng bằng cách thực thi thực tế, không chỉ dựa vào nhận định.

Công nghệ hoặc mẫu thiết kế mà skill khuyến nghị không được tự động thay thế thiết kế hiện tại hoặc thay đổi phiên bản dependency.

## 4. Testing Strategy và Quality Gates bắt buộc

- Áp dụng [Testing Strategy & Test Data](../nghiepvu/08-kiem-thu/chung/testing-strategy.md), [Test Case Standard](../nghiepvu/08-kiem-thu/chung/test-case-standard.md), [Quality Gates & Agent Workflow](../nghiepvu/08-kiem-thu/chung/quality-gates.md); với đồng bộ units dùng [bộ ca Slice A](../nghiepvu/08-kiem-thu/daotao/slice-a-test-cases.md). Lấy đúng bản tài liệu đã duyệt nếu workspace thiếu nguồn. Chạy các kiểm thử bắt buộc trên môi trường local đã xác minh.
- Trước code: xác định nguồn yêu cầu, ID test cases, expected results, acceptance criteria, gate liên quan, fixture và phần thật/mock. Không tự đặt nghiệp vụ hoặc thay Q01–Q08/contract/schema.
- Dùng `test-driven-development` khi phát triển feature/bugfix; `systematic-debugging` khi gặp lỗi; `verification-before-completion` trước khi tuyên bố hoàn thành. Task tài liệu chỉ cần gate tài liệu phù hợp.
- Không tuyên bố PASS nếu chưa chạy. Phân biệt Unit/mock, MockMvc, integration SQL Server thật, HTTP thật và hai backend MVP chạy thật; `contextLoads()` không chứng minh nghiệp vụ/DB/API đúng.
- Dùng DB test riêng đã xác minh đích và quyền sở hữu cho các bộ test tự động cần fixture/trạng thái cố định hoặc thao tác reset, cleanup phá dữ liệu. Smoke Playwright hằng ngày theo §8. Dùng dữ liệu synthetic và che key/token trong bằng chứng. Hạ tầng chưa thiết lập phải ghi rõ, không báo lệnh dự kiến là đã chạy.
- Không skip/disable/xóa assertion hoặc sửa test/expected chỉ để xanh; nếu test sai phải có căn cứ và lý do review. Kiểm số test thực sự chạy, fail/error/skip và report mới; zero test không đạt gate cần test.
- Bàn giao kèm lệnh/cwd, phiên bản, số ca/report/evidence, phạm vi thật–mock; bắt buộc nêu FAIL, BLOCKED, NOT VERIFIED và giới hạn. Chỉ kết luận hoàn thành khi các gate liên quan đạt; nghiệm thu slice áp dụng kiểm độc lập theo G8, ngoại trừ phạm vi được chủ dự án miễn. Với P07–P16 của backlog CTSV/MGHP, [G8 = WAIVED BY OWNER](../nghiepvu/09-trien-khai/master-implementation-backlog.md#g8-waiver-p07-p16); giữ mọi kiểm thử kỹ thuật và evidence theo phạm vi.

## 5. API Design & Contract-First bắt buộc

- Trước khi tạo hoặc chỉnh sửa API, phải đọc API Conventions,
  API Catalog và API Contract hiện hành của chức năng liên quan.
- Thiết kế và triển khai API theo Contract-First; không tự tạo
  endpoint, DTO, request/response, error code hoặc quy tắc
  authorization trái với contract đã duyệt.
- Tuân thủ quy ước URL, HTTP Method, naming, validation,
  status code, ProblemDetail, security và versioning đã chốt.
- Phân biệt API Contract đã phê duyệt với các đề xuất chưa chốt.
  Khi thiếu hoặc mâu thuẫn contract, phải báo rõ trước khi code.
- Khi thay đổi API, phải đánh giá tác động đến provider,
  consumer, OpenAPI, API Catalog và test cases liên quan.
- Thực hiện Contract/API/Security/Integration Test theo
  Testing Strategy và Quality Gates hiện hành.
- Không tuyên bố API hoàn thành nếu chưa đáp ứng các
  acceptance criteria và quality gates liên quan.


## 6. Kiến trúc và phạm vi cộng tác chung — 09/11/2026

- Đọc [Kiến trúc CTSV và quy tắc cộng tác](../nghiepvu/01-thiet-ke/kien-truc-va-quy-tac-cong-tac.md) trước khi lập plan/code. Nhóm4người cùng một CTSV; ba repo ctsv/daotao/taichinh độc lập, Organization không là repo cha.
- CTSV là Modular Monolith: một Spring Boot backend, một React frontend chung; Auth/Foundation/Shared là tài nguyên chung trong ctsv_db, không thuộc riêng MGHP. SIS/Kế toán là backend riêng, không tự thêm application Auth hoặc frontend SIS/Kế toán.
- Phạm vi Codex tại repo này: MGHP và Auth/Foundation/Shared/frontend chung được giao rõ ràng; không sửa module khác để tiện cho MGHP. Không tự tạo task, API, bảng, skeleton, code hoặc UI của SHCN/DRL/Học bổng; chỉ nhắc để xác định ranh giới/ảnh hưởng tích hợp.
- Nếu cần thay đổi Auth/Foundation/Shared hoặc contract chung ngoài task: nêu lý do/phạm vi ảnh hưởng, xin xác nhận và phối hợp owner liên quan; không tự gán owner hoặc sửa module người khác để test xanh.
- Package theo module trước, layer sau; dùng public interface/application service thay repository/entity nội bộ xuyên module; không phụ thuộc vòng. Migration CTSV dùng một Flyway history/version duy nhất, phối hợp trước merge theo nguồn chuẩn.
- Ưu tiên feature branch và giữ `main` ổn định. Dùng PR khi cần tích hợp, review theo cách nhóm thống nhất. Ngoại lệ với `main` cần nhóm xác nhận trước. Codex chỉ commit, push, tạo PR, merge, đổi cấu hình GitHub hoặc deploy khi được giao trực tiếp.
- Người dùng trực tiếp cài công cụ, thiết lập SQL Server test/kết nối và kiểm môi trường P00; Codex chỉ hướng dẫn/kiểm khi được yêu cầu. Không tự kết nối SQL hoặc xin credential trong task tài liệu. Không dùng báo cáo thiết kế làm runtime PASS.

## 7. Persistence Foundation/P05 — refactor được giao13/11/2026

- Đọc [quy tắc Entity/Repository](docs/persistence.md) và [inventory Entity–bảng–FK–owner](docs/persistence-entity-inventory.md) trước khi sửa persistence. Foundation có24 Entity resource +7 bảng hỗ trợ; MGHP P05 có7 Entity. Không tạo Entity cho mọi bảng máy móc hoặc triển khai nghiệp vụ module khác từ projection SIS.
- Giữ Flyway V1–V8 bất biến và schema hiện có; Entity không thay migration. `ddl-auto=validate`, Open Session in View tắt. Backend thông thường dùng Repository/JPA/Entity và DTO/mapper whitelist; không trả Entity ra API hoặc xuyên module.
- Auth và sync engine giữ JDBC chuyên biệt. Không tự chuyển CT/staging/fencing/checkpoint sang `save()`; PK CTSV độc lập ID SIS. SQL Server lock/CAS/rowversion/trigger vẫn là bảo đảm bắt buộc, không thay bằng `@Version Long`.
- JPA/JdbcTemplate dùng chung transaction; flush trước native SQL, clear/refresh sau CAS/bulk/trigger khi cần. Association LAZY không cascade xóa; FK scalar ghi, association read-only đọc. Nạp lại association sau thay FK trong cùng transaction.
- Test refactor chỉ dùng DB riêng `ctsv_jpa_refactor_test_20261113` có guard/marker đã được owner cho phép; không reset/drop hoặc ghi DB DEV/evidence cũ. Lệnh và giới hạn ở [kế hoạch/kết quả refactor](../nghiepvu/09-trien-khai/foundation-jpa-refactor-plan.md).

## 8. Bàn giao DEV cho mỗi slice — 15/11/2026

- Khi có migration, đồng bộ Entity và kiểm Hibernate schema validation. Kiểm version/checksum Flyway giữa source, JAR DEV và `ctsv_dev`; xác minh launcher `.\dev.ps1 start` sau khi cập nhật DEV trong phạm vi được giao. DB DEV đúng đích có migration pending thì dừng launcher, chạy `.\dev.ps1 migrate` rồi `.\dev.ps1 start`. Nếu Flyway báo checksum hoặc history xung đột, đối chiếu trước khi ghi. Giữ migration đã áp dụng và hành vi chỉ kiểm tra của `start`. Xem [runbook DEV](docs/team-handover-p00-p04/03-daily-operations.md).
- Bàn giao tính năng người dùng kèm dữ liệu mẫu SYNTHETIC đủ để thao tác được luồng chính theo từng vai trò. Ghi rõ tài khoản DEV, dữ liệu cần chọn và cách seed hoặc tạo lại. Seed ngoài luồng start, chạy lại không nhân đôi hoặc ghi đè dữ liệu người dùng. Kiểm thử với tài khoản DEV trên dữ liệu mẫu đã chuẩn bị; nếu chưa chạy được, ghi rõ bước còn thiếu.
- Playwright smoke cho luồng UI hằng ngày dùng ứng dụng chạy bằng `.\dev.ps1 start` trên `ctsv_dev`, với tài khoản và dữ liệu synthetic DEV. Ca Playwright tự động cần fixture cố định hoặc thao tác phá hủy dùng DB test riêng theo §4. Bằng chứng ghi URL, DB và dịch vụ REAL/STUB thực tế.
- Giao diện chỉ hiển thị menu, màn hình và thao tác theo quyền hiện hành do backend cấp. Kiểm ít nhất một tài khoản có quyền và một tài khoản thiếu quyền khi thay đổi UI có phân quyền, gồm trường hợp truy cập trực tiếp. Backend tiếp tục kiểm capability và quyền trên từng đối tượng. Xem [quy chuẩn frontend](frontend/SKILL.md#7-state-api-và-bảo-mật).
- Với luồng có dịch vụ ngoài như upload PDF, xác minh API và cấu hình dịch vụ qua launcher DEV hằng ngày trước khi bàn giao. Ghi riêng kết quả của harness và DEV, URL/DB/profile đã dùng và dịch vụ REAL hay STUB. Nếu chưa có cấu hình DEV, ghi rõ upload end-to-end chưa được xác minh trên DEV.

<a id="ctsv-efficient-verification"></a>

## 9. Triển khai và kiểm thử có trọng tâm — quyết định owner 21/11/2026

Áp dụng cho các lượt triển khai/kiểm thử CTSV tiếp theo. Các quy tắc này điều chỉnh cách làm, không miễn kiểm thử kỹ thuật bắt buộc hoặc thay contract/expected result.

1. Trước code, xác định ngắn gọn phạm vi thay đổi, nguyên nhân và các ca cần kiểm. Không mở rộng công việc hoặc đọc lại toàn bộ tài liệu không liên quan.
2. Kiểm thử tăng dần: test tập trung → ca browser tái hiện lỗi → luồng thành công/lỗi/quyền liên quan → một lượt hồi quy cuối. Không chạy lại toàn bộ suite chỉ vì sửa vài dòng frontend, trừ khi dependency hoặc Quality Gates yêu cầu.
3. Kiểm tra locator trên DOM thực trước khi chạy luồng dài. Ưu tiên role, accessible name và locator duy nhất. Không force-click hoặc thay expected để che lỗi.
4. Khi một ca thất bại, giữ browser context và trạng thái để điều tra. Sửa rồi chạy lại đúng ca lỗi trước; không mặc định khởi động lại toàn bộ kịch bản. Chỉ đóng context sau khi đã thu thập bằng chứng và hoàn tất điều tra/lượt kiểm.
5. Đăng nhập một lần cho mỗi tài khoản trong một lượt kiểm thử, tái sử dụng phiên trong bộ nhớ. Không lưu password/token hoặc browser storage state chứa credentials/token vào file để tái sử dụng phiên kiểm thử. Cấu hình DEV riêng ngoài Git theo cơ chế hiện hành được giữ nguyên.
6. Quyết định hiện hành của owner: `ctsv.auth.login-rate-limit-enabled=false` mặc định trên tất cả môi trường. Không tự bật lại khi chưa được owner yêu cầu, kể cả tự chọn override/profile trong lượt kiểm. Giữ nguyên xác thực mật khẩu, JWT, CSRF, CORS và authorization. Không suy từ quyết định này rằng cấu hình đã được áp dụng vào JAR/process đang chạy nếu chưa xác minh.
7. Tái sử dụng fixture synthetic đã xác minh đích và quyền sở hữu. Sau timeout hoặc lỗi công cụ, đọc lại trạng thái backend trước khi gửi lại lệnh ghi; tránh tạo trùng hồ sơ, đợt xét hoặc upload PDF. Không reset/clean/drop DB hoặc xóa dữ liệu ngoài phạm vi được phép.
8. Chỉ đọc response body khi cần. Phân biệt lỗi script/công cụ với lỗi ứng dụng. Kiểm chứng thao tác bằng HTTP status, UI và dữ liệu đã lưu; không chỉ dựa vào screenshot.
9. Kiểm frontend theo hành trình thật: tìm chức năng → mở đúng ngữ cảnh → nhập → gửi → thấy kết quả. Kiểm thêm lỗi sau khi cuộn, sửa lỗi rồi thao tác tiếp, quyền truy cập và mobile.
10. Upload/download dịch vụ thật khi thay đổi có ảnh hưởng tới tích hợp đó; không lặp upload cho các ca chỉ kiểm điều hướng. Không dùng mock rồi báo integration REAL đã PASS.
11. Báo PASS đúng phạm vi đã chạy. Ghi rõ FAIL/BLOCKED/NOT VERIFIED, số ca và bằng chứng. Dataset chỉ có một chủ sở hữu không chứng minh được kiểm soát truy cập chéo.

<a id="ctsv-role-first-ux"></a>

## 10. Thiết kế theo công việc của người dùng — quyết định owner 22/11/2026

- Trước khi code UI, nêu ngắn gọn người dùng/capability, việc họ cần làm, dữ liệu cần xem/nhập, hành động chính và bước tiếp theo/kết thúc. Đối chiếu source/contract hiện hành; không biến mọi field database hoặc endpoint thành một ô nhập/nút. Với sửa nhỏ, ghi ngay trong cập nhật công việc, không tạo plan hay bộ tài liệu mới không cần thiết.
- Rà soát sự cần thiết của từng field/nút và cả màn hình sau khi ghép: bỏ phần trùng, gom phần liên quan, tách rõ danh sách/tạo mới/chi tiết; tên nút phải đúng tác động. Dữ liệu kỹ thuật giữ trong hệ thống; dùng tên dễ hiểu và nguồn đã xác minh. Không tự chọn mặc định khi còn nhiều lựa chọn nghiệp vụ chưa xác định.
- Thiết kế theo trách nhiệm và capability thật từ backend: sinh viên thao tác dữ liệu của mình; chuyên viên có thông tin đối chiếu và đường xử lý; Admin Auth không mặc nhiên có quyền MGHP. Kiểm ẩn/hiện menu cùng route và thao tác, backend tiếp tục kiểm quyền trên đối tượng. Không sao chép một màn hình tác vụ cho mọi vai trò.
- Kiểm toàn bộ đường đi liên quan: tìm chức năng → xem đúng ngữ cảnh → thao tác → thấy kết quả → xử lý tiếp hoặc kết thúc. Với quy trình lặp, phải có đường cho lần tiếp theo; nếu trạng thái chặn thao tác, giải thích lý do và bước hợp lệ. Sau ghi/đọc lại phải đồng bộ cả chi tiết con, trạng thái, danh sách và ETag; không dùng reload trang để che dữ liệu cũ.
- Đơn giản hóa giao diện phải đi kèm khảo sát phụ thuộc. Không tự bỏ validation, quyền, điều kiện P08 hoặc lịch sử để giảm nút; không tự pass hàng loạt kiểm tra. Nếu cần đổi contract/nghiệp vụ ngoài quyền đã giao, báo đúng điểm ảnh hưởng trước khi sửa. Giữ AppShell/navigation/design tokens và ranh giới module hiện hành.
- Áp dụng checklist chi tiết tại [frontend/SKILL.md §12](frontend/SKILL.md#ctsv-ux-review). Kiểm theo tác động và §9: assertion phải chứng minh kết quả người dùng nhận được, dữ liệu đã lưu, quyền và cách tiếp tục khi lỗi; HTTP thành công, iframe tồn tại hay ảnh chụp riêng lẻ chưa đủ. Tách PASS kỹ thuật khỏi nhận xét UX/nghiệm thu owner, ghi rõ giới hạn. Không chạy toàn suite hoặc tạo thêm dữ liệu chỉ vì sửa nhãn.
- Với màn hình mới hoặc thay đổi bố cục lớn, phác ngắn cách sắp xếp thông tin và tác vụ trước code, rồi đối chiếu brief/luồng đã chốt. Kế thừa font, palette, tokens và Navbar hiện hành; không tự đổi theme, thêm hero/animation hoặc tạo bộ thiết kế riêng để làm khác biệt. Sửa nhỏ không cần thêm wireframe hay vòng xin duyệt.
- Áp dụng [frontend/SKILL.md §13](frontend/SKILL.md#ctsv-visual-design): thứ bậc thị giác rõ, khung/đường chia có ý nghĩa, từ ngữ nhất quán từ nút đến phản hồi, chuyển động phục vụ thay đổi trạng thái và có giảm chuyển động. Rà CSS trạng thái và xem ảnh thực tế trước bàn giao phần bố cục; tự loại chi tiết trang trí không giúp tác vụ, không bỏ điều kiện nghiệp vụ để làm gọn.

## 11. Đồng bộ main và migration gọn — quyết định owner 22/11/2026

- Quy trình ưu tiên: bảo toàn working tree → fetch/merge main → backup DB cần nâng → xử lý số migration → kiểm trên DB riêng đã được phép → áp DEV đã được giao → test/smoke liên quan → review secrets → commit/push nhánh riêng khi đã được yêu cầu. Tạo PR khi được giao; không force push/main/reset dữ liệu.
- Migration riêng **chưa áp dụng**: đổi sang số tiếp theo còn trống, giữ SQL của main. Migration **đã áp dụng**: không chạy lại SQL vừa đổi tên; trước hết đối chiếu chính xác script/checksum/schema và backup, rồi chuyển định danh history có guard/audit theo phạm vi owner đã cho phép. Giữ checksum và dấu vết thực thi; không repair bừa hoặc xóa history để qua validation. Chỉ chạy các migration còn thiếu; nếu không khớp thì dừng phần ghi và báo nguyên nhân cụ thể.
- Kiểm đủ và có trọng tâm: chuỗi version duy nhất, Flyway validate, schema/Entity, dữ liệu/tài khoản cũ, idempotency; sau nâng DEV kiểm launcher và smoke hành trình bị tác động. Tái sử dụng bằng chứng vừa đạt nếu source/đích/phạm vi không thay đổi; không chạy lại toàn bộ phase hoặc upload/seed thêm chỉ để tăng số PASS.
- Thực hiện liên tục phần đã được giao, không hỏi lại các phê duyệt còn hiệu lực. Chỉ dừng khi thiếu quyền thực sự, conflict không rõ hoặc rủi ro dữ liệu chưa xử lý. Lỗi được owner cho bỏ qua phải ghi nguyên trạng FAIL/ERROR và phạm vi miễn, không đổi expected hoặc báo PASS. [Ví dụ chuyển DEV V21 có bảo toàn dữ liệu](docs/dev-v21-upgrade-20261122.md).
