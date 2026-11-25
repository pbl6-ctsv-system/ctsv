# Hội đồng — lọc học kỳ/đợt và lỗi theo thao tác (25/11/2026)

## Phạm vi đã được owner giao

Chuyên viên chọn Học kỳ → Đợt xét, đọc đúng nguồn đóng xét rồi tạo/xem bộ xét. Chuyển từ đối chiếu giữ ngữ cảnh; URL cũ chỉ có đợt vẫn được giải quyết từ học kỳ của đợt trên server. Đổi học kỳ phải bỏ đợt/chi tiết cũ. Không sửa backend, migration, thuật toán chọn hoặc điều kiện chốt.

Tái sử dụng DOT01-L (semesterId, phân trang), DOT01-D, DOT08-R (semesterId bắt buộc), danh mục học kỳ Foundation và QD APIs. Lỗi hiển thị theo thao tác và field backend trả về; không gán lỗi đọc nguồn thành lỗi thời gian họp.

## Kế hoạch và acceptance

- [x] RED: test request DOT08-R có học kỳ; danh mục/đợt phân trang đúng bộ lọc; lỗi source/create/meeting/confirm; URL bàn giao. Lần RED: 7 PASS / 5 FAIL, đúng lỗi thiếu query và chức năng mới chưa tồn tại.
- [x] GREEN: sửa adapter/model/DecisionWorkspace; link từ RoundStagePanel; giữ AppShell và CSS hiện hành, bộ lọc xếp dọc trên mobile. Giữ chữ ký `rounds(page,signal)` cho SigningBundle, bổ sung semester ở tham số thứ ba; có regression tương thích.
- [x] Chạy test tập trung và build/typecheck; browser REAL kiểm nguồn/bộ lọc/đường bàn giao/quyền/mobile. Phạm vi tạo mới và lưu cuộc họp chưa chạy trên DEV, xem giới hạn dưới.
- [x] Review diff, ghi số test, evidence và giới hạn thực tế. Không CA/P11/P12, không commit/push.

## Bằng chứng

## Thay đổi thực tế

| File trong `frontend/` | Nội dung |
|---|---|
| `src/features/mghp/decisions/api.mjs` | DOT08-R gửi semesterId; round list dùng server filter và paging; đọc học kỳ Foundation; lỗi giữ status/fields/operation, không retry lệnh ghi |
| `src/features/mghp/decisions/model.mjs` | URL học kỳ/đợt/version; thông báo theo thao tác và field, không lộ raw exception |
| `src/features/mghp/decisions/DecisionWorkspace.tsx` | Học kỳ → đợt; xóa trạng thái cũ khi đổi bộ lọc; giữ qua refresh, resolve link cũ từ server, chặn mismatch; sửa cả select và readback nguồn |
| `src/features/mghp/decisions/DecisionWorkspace.module.css` | Bộ lọc desktop cùng hàng khi đủ rộng; mobile xếp dọc |
| `src/features/mghp/RoundStagePanel.tsx` | Link từ chốt bản cứng giữ chính xác học kỳ/đợt |
| `tests/mghp-decisions.test.mjs` | 6 ca mới: query nguồn, paging/filter, tương thích SigningBundle, lỗi theo thao tác/field, URL bàn giao |
| `e2e/decision-filters-dev.cjs` | Harness chỉ đọc DEV, browser context/phiên trong RAM, giữ context khi ca lỗi; không trace/HAR/token file |

Không sửa backend, schema, contract hay acceptance nghiệp vụ. Các thay đổi sẵn có trước lượt này được giữ nguyên.

## Kết quả thực thi

CWD Node/build: `D:\7rd_semester\pbl6_src\ctsv\frontend`.

- `node --test tests/mghp-decisions.test.mjs tests/stage-handoff.test.mjs`: **16 PASS, 0 FAIL, 0 SKIP**. Gồm 13 ca decision + 3 ca handoff; API boundary trong bộ này dùng stub, không gọi đó là HTTP REAL.
- `npm run build`: **PASS** TypeScript và Vite; giữ dependency hiện hành.
- `node --check frontend/e2e/decision-filters-dev.cjs` từ root: **PASS**.
- `git diff --check -- frontend/src/features/mghp/decisions frontend/tests/mghp-decisions.test.mjs`: **PASS**; chỉ cảnh báo Git LF→CRLF.
- Browser: `CTSV_PLAYWRIGHT_MODULE=<Playwright local>` rồi `node frontend/e2e/decision-filters-dev.cjs`, chạy các phase `inspect`, `filters`, `handoff`, `security`, `finish`: **11 PASS**, không page error/failure. Chrome/React thật → Spring Boot thật → SQL Server `DESKTOP-7PPHP5B\DEV2022`, `ctsv_dev`, owner marker `ctsv-local-dev-v1` đã SELECT xác minh. Launcher PID BE16612/FE26048, HTTPS FE5173/BE8443. Không mock API nghiệp vụ; không CA/Cloudinary.

| Browser case | Kết quả |
|---|---|
| DF01 đi từ menu, chưa chọn học kỳ thì chưa chọn đợt | PASS |
| DF02 tái hiện GET cũ thiếu semesterId | PASS: HTTP400 VALIDATION_ERROR, field=semesterId |
| DF03 chọn đợt20051 với học kỳ1 | PASS: HTTP200, eligible1/excluded1, không toast lỗi |
| DF04 đổi học kỳ1→4 | PASS: bỏ đợt/nguồn cũ, danh sách đợt server lọc đúng học kỳ |
| DF05 refresh | PASS: giữ cả hai bộ lọc |
| DF06 URL cũ chỉ có dot-xet | PASS: lấy học kỳ đúng từ RoundDetail và chuẩn hóa URL |
| DF07 URL học kỳ4/đợt20051 không khớp | PASS: lỗi rõ, không hiện Tạo bộ xét |
| DF08 viewport390×844 | PASS: không tràn ngang, xem ảnh thực tế |
| DF09 từ đối chiếu bản cứng sang Hội đồng | PASS: giữ học kỳ1/đợt20051 và đọc nguồn200 |
| DF13 sinh viên direct URL | PASS: bị từ chối, không hiện bộ lọc |
| DF13 Admin Auth direct URL | PASS: bị từ chối, không hiện bộ lọc |

Evidence local (ignored): `target/decision-filters-20261125/browser.json`, `frontend-tests.log`, `frontend-build.log`, `01-source-loaded.png`, `02-mobile-filters.png`. Đã mở và xem cả hai ảnh desktop/mobile. Browser ghi nhận **0 request ghi MGHP**; không thêm/sửa dữ liệu nghiệp vụ DEV. Auth login/refresh vẫn hoạt động theo luồng bình thường, credentials/JWT chỉ trong RAM.

## Giới hạn

- Ca tạo bộ xét synthetic bị bộ duyệt tự động chặn vì chưa thấy ủy quyền rõ cho ghi mới DEV trong yêu cầu hiện tại; không thực hiện lại hoặc đi đường vòng. Thay bằng kiểm đọc nguồn, CTA hợp lệ, bộ lọc và quyền; harness cuối chỉ giữ các phase đọc đã chạy. **Tạo mới, lưu cuộc họp và chốt qua browser: NOT VERIFIED trong lượt này.** Không tuyên bố P10 toàn bộ E2E PASS.
- Thông báo lỗi create/meeting/confirm và field được kiểm bằng test tập trung, chưa tiêm lỗi backend trên DEV để ép từng nhánh. Không sửa expected nghiệp vụ hay test nguồn backend.
- Không chạy lại bộ SQL/concurrency/P05–P08 hoặc toàn suite vì không thay backend/schema/transaction; hồi quy tập trung ở handoff, API adapter dùng chung ký số, quyền UI và build.
- Review 8 file thuộc lượt này không thấy secret qua kiểm mẫu nhạy cảm; không quét/ghi lại cấu hình DEV. Không commit/push/merge.
