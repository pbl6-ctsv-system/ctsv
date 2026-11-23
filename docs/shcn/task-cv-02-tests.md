# Kiểm chứng TASK-CV-02

> **Cập nhật22/11/2026:** owner đã áp dụng V18. Đã cấp role Auth và global grant SHCN cho `dev.chuyenvien`, kiểm API200/prepare=true và menu Chrome qua HTTPS DEV. Công cụ cấp quyền có version/guard/audit được lưu trong repo; xem [bàn giao quyền DEV](dev-readiness.md). Danh mục học kỳ của nguồn hiện còn0dòng, nên chưa nghiệm thu toàn luồng tạo đợt với dữ liệu thật. Các bằng chứng unit/fixture bên dưới là snapshot của lượt triển khai21/11, không thay cho kết quả DEV mới.


Ngày 21/11/2026. Chỉ kiểm phạm vi đợt nháp SHCN, lookup học kỳ tối thiểu và các điểm nối menu bị tác động. Không chạy toàn bộ suite của hệ thống, integration test có Flyway, migration hoặc seed.

## Kết quả thực chạy

| Bộ kiểm | Kết quả | Thật / giả lập | Bằng chứng |
| --- | --- | --- | --- |
| Java validation | 27 ca đạt, 0fail/error/skip | Parser thật, request synthetic | `DotRequestsTest`, [log](evidence/task-cv-02/backend-tests.log) |
| Java service/security | 16 ca đạt, 0fail/error/skip | Service/transaction boundary thật; Auth/Foundation/reference/repository/transaction resource giả lập | `DotServiceTest`, [log](evidence/task-cv-02/backend-tests.log) |
| MockMvc | 15 ca đạt, 0fail/error/skip | Controller/parser/service/advice thật; Auth/reference/repository giả lập, không server TLS/SQL | `DotHttpTest`, [log](evidence/task-cv-02/backend-tests.log) |
| Frontend unit + menu/SHCN cũ | 27 ca đạt, 0fail/skip | Node unit, không SQL/backend | [log](evidence/task-cv-02/frontend-tests.log) |
| TypeScript + Vite build | Đạt | Build production frontend thật | [log](evidence/task-cv-02/frontend-build.log) |
| Giao diện Chrome | 13 luồng đạt | Browser/Vite thật; **mọi API HTTP STUB**, SQL NONE | [results.json](evidence/task-cv-02/results.json), [log](evidence/task-cv-02/browser.log) |
| Đối chiếu contract/source | Đạt; 5 operation, 11 cột catalog, 15 schema, 70refs, 40example; đủ 27 field ERD/Entity/DDL | Kiểm tĩnh bằng script, không thực thi SQL | [KIEM_TRA_TAI_LIEU.json](evidence/task-cv-02/KIEM_TRA_TAI_LIEU.json) |

Đã mở và xem ảnh [desktop chi tiết](evidence/task-cv-02/detail-desktop.png), [desktop danh sách](evidence/task-cv-02/list-desktop.png), [form mobile360](evidence/task-cv-02/form-mobile.png). Mobile không tràn ngang toàn trang. Date input native theo locale trình duyệt; ngày đọc hiển thị vi-VN.

Lần chạy đầu MockMvc phát hiện đường dẫn validation khi thiếu expectedVersion quá chung; đã sửa parser, giữ assertion và chạy lại đạt. Lần kiểm browser sau bổ sung lỗi inline phát hiện tên truy cập label bị đổi; đã chuyển mô tả lỗi sang aria-describedby, giữ assertion và chạy lại13 luồng đạt. Không bỏ/skip ca lỗi để làm xanh.

## Lệnh đã chạy

Root backend `G:\26-27\PBL6-CTSV\ctsv`, JDK21:

```powershell
.\mvnw.cmd -o '-Dtest=DotRequestsTest,DotServiceTest,DotHttpTest' test
```

CWD `ctsv\frontend`, Node22.14.0, React19.2.0, TypeScript5.9.3, Vite7.1.12:

```powershell
npm.cmd run build
node --test tests/shcn-rounds.test.mjs tests/module-navigation.test.mjs tests/shcn.test.mjs
```

Browser cần Vite riêng đang chạy tại `http://127.0.0.1:5187` (`npm.cmd run dev -- --port 5187 --strictPort`), CWD `ctsv\frontend\e2e`:

```powershell
$env:SHCN_UI_ORIGIN='http://127.0.0.1:5187'
$env:SHCN_UI_EVIDENCE='<thư mục ghi evidence>'
node shcn-rounds-ui.mjs
```

Browser dùng HTTP loopback riêng với interceptor toàn bộ `/api/`; không phải smoke DEV qua launcher và không dùng backend cũ để giả chứng minh API mới hoạt động. TLS/Auth server thật chưa được kiểm cho task này. Script không in hoặc sử dụng credential thật.

Kiểm tài liệu CWD thư mục tham khảo:

```powershell
python API/TASK_CV_02_DOT_NHAP/kiem_tra_cv02.py
```

Script dùng checker cục bộ cho subset schema đã ghi rõ trong report; không có validator OpenAPI3.1 bên thứ ba. Không chứng nhận đầy đủ semantics SQL bằng việc tên cột trùng nhau.

## Liên hệ với40 ca thiết kế

| Nhóm ca thiết kế | Bằng chứng hiện có | Phần còn thiếu |
| --- | --- | --- |
| CV02-01–03,11: list/count/filter/page/khoa | Parser, unit frontend và browser HTTP fixture; maKhoa chưa có resolver được503 theo code repository | SQL predicate/count/isolation, phân trang thật; nhánh đối tượng thuộc task sau |
| CV02-04–05,15–19,22: validation/mass assignment | Parser và MockMvc, gồm strictJSON/UTF8/byte limit/media/date/code/version | Biên lưu trữ SQL thực tế |
| CV02-06–10,12: quyền, tác giả khác, đọc lịch sử | Service/MockMvc quyền đọc/ghi, thu hồi request sau, giữ source lịch sử; browser direct URL/read-only | Auth thật, JOIN role–permission–scope và thu hồi DB thật; account/issuer mapping DEV |
| CV02-13–14: tạo root độc lập | Service/MockMvc tạo6field, metadata server, browser tạo+đọc+list; code không có child/provider dependency | INSERT/rowversion/NULL/audit thật, hai POST đồng thời trên SQL |
| CV02-20–21: học kỳ | MockMvc nguồn không có/lỗi, browser nullable year/lookup failure; adapter giới hạn nguồn trong source | Duplicate/source presence/mapping trên dữ liệu SQL thật |
| CV02-23–34: sửa, CAS, state, rollback và các nhánh đồng thời | Service/MockMvc stale-before-state, khóa trạng thái, tác giả khác, reset con trỏ khi đổi khung, boundary rollback audit; browser409/đối chiếu | Atomic rollback thật, hai writer/race/deadlock và trigger SQL thật; workflow dùng chung hoàn tất ngoài slice |
| CV02-35–40: UI | 13 luồng Chrome với fixture, tạo/sửa/list-refresh, readonly/403, conflict/timeout, lỗi sau cuộn, keyboard chọn học kỳ, ảnh desktop/mobile | DEV smoke sau owner migrate/cấp quyền; các tổ hợp fixture chưa chạy không tự được đánh dấu đạt |

Không quy đổi58 unit/MockMvc thành40 ca acceptance SQL đã PASS. Danh sách40 ca vẫn giữ expected của thiết kế; bảng trên chỉ nêu mức bằng chứng đã có.

## Chưa xác minh

- V18 trên SQL Server thật, Flyway checksum/history và Hibernate schema validation.
- Lock/CAS/trigger/rollback audit thật và concurrency giữa hai connection/writer.
- DB role–permission/grant toàn cục, source học kỳ và Auth identity trên account DEV thật.
- Luồng UI qua `.\dev.ps1 start` và DB DEV sau migration.
- G8/kiểm độc lập hoặc nghiệm thu runtime toàn slice chưa thực hiện; cần cùng phần SQL/DEV sau khi owner cho chạy.

Không kết nối SQL Server, không khởi động backend mới, không migrate/seed hoặc cấp quyền tài khoản trong lượt này. Giới hạn workflow và cách chuẩn bị dữ liệu xem [bàn giao DEV](task-cv-02.md).

## Bổ sung 22/11/2026 — cấu hình nhóm trong CV-02

Đã bổ sung backend và giao diện cấu hình ngày/hình thức theo khóa và nhóm lớp trễ tiến độ. Đây là phần cấu hình nhóm của CV-03 được owner yêu cầu làm ngay trong CV-02; không đồng nghĩa toàn bộ CV-03 đã có bước phân giải/chốt snapshot lớp. Không triển khai mẫu biên bản, phân công đợt, duyệt/phát hành, phòng hoặc ký/PDF.

- Root và các bộ chọn lưu trong cùng transaction, cùng khóa root/rowversion; khi thay nhóm áp dụng reset con trỏ phân công NHAP. Giữ nguyên mọi guard trạng thái và RBAC hiện có.
- Endpoint bổ sung: `GET /api/shcn/tra-cuu/nhom-lop?maHocKy=...`, permission `shcn.reference.read` + grant SHCN toàn trường. Giữ nguyên các permission/role có sẵn, không gán thêm quyền DEV trong lượt này.
- POST/PATCH thêm `doiTuongApDung` tùy chọn. POST bỏ field tạo nháp chưa có nhóm; PATCH bỏ field giữ nguyên nhóm; gửi `[]` mới xóa nhóm. Đổi kỳ phải kiểm lại nhóm đang giữ. Chi tiết trả array nhóm đã lưu và đọc được lịch sử khi nguồn đổi.
- Khóa lấy từ quan hệ `lop_sinh_hoat.khoa_hoc_id`, không suy từ tên/mã lớp. Loại lớp RenLuyen khỏi nhóm khóa thường. Nhóm trễ tiến độ chỉ lấy lớp có tên chứa renluyen và có phân lớp nguồn hiện diện đúng học kỳ, đúng hệ thống nguồn.
- Hàng “Lớp trễ tiến độ” trên UI mở rộng thành các bộ chọn LOP; không thêm enum/bảng DRL, không tự tính vượt kỳ, không tự chuyển toàn bộ sinh viên lớp gốc hoặc lấy phân lớp kỳ trước.
- Khi đổi học kỳ, xác nhận trước khi bỏ nhóm đang soạn; abort kết quả tra cứu cũ. Lỗi giữ dữ liệu, không tự gửi lại lệnh ghi. Có kiểm 360px/desktop, đọc lại chi tiết/danh sách sau lưu, lỗi sau khi cuộn và account thiếu quyền.

### Migration pending và giới hạn DEV

Chỉ viết **`V19__shcn_round_audiences.sql`**, tạo bảng `shcn.shcn_doi_tuong_ap_dung` đã có trong ERD, FK tới đợt, unique/check và trigger bảo vệ trạng thái. Không sửa V18 hoặc schema/Entity Foundation/Đào tạo. **Chưa chạy migration, kể cả trên DB test.**

Owner tự thực hiện từ root CTSV khi muốn cập nhật DB:

```powershell
.\dev.ps1 build
.\dev.ps1 migrate
.\dev.ps1 start
```

Frontend chạy `npm.cmd run dev` trong `frontend`. Nếu ứng dụng đang chạy cần dừng bằng launcher trước khi cập nhật theo runbook hiện có. Không bỏ kiểm Flyway/Hibernate để chạy khi V19 còn pending.

Nguồn DEV ở lần kiểm quyền trước có 0 học kỳ. Lượt bổ sung này không seed giả danh mục hay ghi dữ liệu Foundation; cần dữ liệu học kỳ, khóa gắn lớp và phân lớp RenLuyen đúng kỳ qua quy trình nhập/đồng bộ của nhóm trước khi thao tác đầy đủ bằng API thật. Không có phân lớp kỳ đang chọn được hiển thị là thiếu dữ liệu, không xác nhận 0 sinh viên trễ tiến độ.

### Kiểm chứng lượt bổ sung

- Backend: `./mvnw.cmd -o -Dtest=Dot*Test test` với JDK21: **74 tests, 0 failures/errors/skips**; parser/service unit và MockMvc, dependency repository/Foundation giả lập. Không chứng minh JPA/trigger/SQL thật.
- Frontend: `node --test frontend/tests/shcn-rounds.test.mjs frontend/tests/shcn-round-audiences.test.mjs`: **18/18**.
- Frontend `npm.cmd run build`: TypeScript và Vite PASS.
- Chrome: **7/7** ca mới `e2e/shcn-round-audiences-ui.mjs` và **13/13** hồi quy `e2e/shcn-rounds-ui.mjs`; Vite thực ở `http://127.0.0.1:5174`, API HTTP STUB, không có SQL/CA/storage thật. Ảnh desktop/mobile đã được mở kiểm tra.
- SQL Server/JPA validation/trigger/CAS/rollback thật sau V19: **NOT VERIFIED**, migration chưa được owner cho chạy. DEV ghi đợt bằng nguồn thật: **NOT VERIFIED**. Không báo toàn luồng production/DEV đã nghiệm thu.

Contract và OpenAPI gốc đã bổ sung tại `G:/26-27/PBL6/DaTa_ThamKhao_BanChinh/API/TASK_CV_02_DOT_NHAP`: `10_BO_SUNG_NHOM_AP_DUNG.md`, `openapi.json`. Công cụ sinh/staging tại thư mục tài liệu là trung gian của lần triển khai, không dùng làm runtime/seed và không chạy lại trên source đã chỉnh tiếp. Source chính thức nằm trong repo CTSV.

### Kết quả đóng gói cuối lượt

Đóng gói executable Spring Boot JAR **PASS trong thư mục kiểm chứng riêng** `BUILD_VERIFY/target` của bộ tài liệu CV-02. Dùng bản sao POM giữ dependency/plugin hiện tại, trỏ nguồn Java/resources thật và chỉ đổi thư mục output. Không sửa POM của repo hoặc chạy ứng dụng/JAR này. Đã kiểm JAR chứa Entity và migration V19 mới.

Lệnh package mặc định trong repo đã gặp lỗi đổi tên JAR vì có hai tiến trình Java đang giữ `target/ctsv-0.0.1-SNAPSHOT.jar`. Chưa dừng các tiến trình đó; không báo package mặc định PASS. Trước khi build/migrate DEV, owner cần dừng đúng ứng dụng/công cụ Java đang dùng JAR bằng launcher tương ứng. Bản package mặc định bị dở không được dùng làm JAR DEV mới. Sau khi giải phóng file, chạy lại build theo runbook, rồi owner tự migrate/start khi sẵn sàng.

Kiểm cuối: OpenAPI có 6 operations trên 4 paths, array đối tượng tùy chọn ở request/bắt buộc ở response; `git diff --check` PASS. Không có thay đổi schema/Entity Foundation hoặc Đào tạo; không có migration/seed/ghi DB trong lượt bổ sung này. Vite phục vụ kiểm thử ở cổng 5174 chỉ dùng API STUB và được dừng sau kiểm thử.
