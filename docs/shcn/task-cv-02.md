> **Giao diện ngày 23/11/2026 — owner đã chốt:** không nhập ngày bắt đầu/kết thúc chung. Người dùng chọn ít nhất một khóa/nhóm lớp, nhập đủ hai ngày và hình thức cho mỗi nhóm. Frontend tự tính `khungNgayBatDau = min(từ ngày)` và `khungNgayKetThuc = max(đến ngày)` của các đối tượng đang chọn rồi gửi theo API hiện hành; không đổi cấu trúc DB/contract. Khi sửa bản cũ có ngày nhóm null, form thể hiện lại khoảng ngày chung đã lưu cho nhóm đó. Bộ chọn học kỳ chỉ hiện tên; mã vẫn được giữ nguyên trong request. Không seed, migrate hoặc ghi DB trong lượt sửa UI này. Kiểm 23 ca Node và 21 ca Chrome với API fixtures (SQL NONE); build TypeScript/Vite đạt.

# Bàn giao TASK-CV-02 — backend và frontend

> **Cập nhật22/11/2026:** owner đã áp dụng V18. Đã cấp role Auth và global grant SHCN cho `dev.chuyenvien`, kiểm API200/prepare=true và menu Chrome qua HTTPS DEV. Công cụ cấp quyền có version/guard/audit được lưu trong repo; xem [bàn giao quyền DEV](dev-readiness.md). Danh mục học kỳ của nguồn hiện còn0dòng, nên chưa nghiệm thu toàn luồng tạo đợt với dữ liệu thật. Các bằng chứng unit/fixture bên dưới là snapshot của lượt triển khai21/11, không thay cho kết quả DEV mới.


Ngày 21/11/2026. Repo code: `G:\26-27\PBL6-CTSV\ctsv`. D01: chuyên viên có quyền SHCN toàn trường quản lý cả đợt do chuyên viên khác tạo. Đã bổ sung phần lookup học kỳ SHCN-CV-001 phục vụ màn hình này.

**Source đã triển khai; DB chưa migrate, chưa seed/cấp thêm quyền tài khoản.** Không gọi SQL Server hoặc chạy thử launcher backend mới trong lượt này. Chưa nghiệm thu SQL/luồng DEV thật.

## 1. Phạm vi đã triển khai

| Phần                                                                           | File/thư mục trong repo CTSV                                                                                                                     |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Entity, repository, service, DTO, validation, controller, lỗi và cấu hình SHCN | `src/main/java/pbl6/ctsv/shcn/dotsinhhoat/`                                                                                                      |
| Adapter chỉ đọc quyền/học kỳ                                                   | `shcn/dotsinhhoat/reference/JdbcDotReference.java`; dùng public `FoundationAccess`, `AcademicSemesterSource`; không sửa Entity/schema Foundation |
| Migration mới, **chưa chạy**                                                   | `src/main/resources/db/migration/V18__shcn_draft_round.sql`                                                                                      |
| Màn hình danh sách/tạo/chi tiết/sửa                                            | `frontend/src/features/shcn/pages/RoundsPage.tsx`, `components/RoundEditor.tsx`, `pages/Rounds.module.css`                                       |
| API, DTO và đối chiếu dữ liệu frontend                                         | `frontend/src/features/shcn/api/rounds.ts`, `types/rounds.ts`, `model/round.mjs`                                                                 |
| Tích hợp tối thiểu menu/route                                                  | `ShcnWorkspace.tsx`, public index SHCN, `app/App.tsx`, `app/navigation/modules.mjs`, `featureRegistry.tsx`                                       |
| Kiểm thử backend                                                               | `src/test/java/pbl6/ctsv/shcn/dotsinhhoat/`                                                                                                      |
| Kiểm thử frontend                                                              | `frontend/tests/shcn-rounds.test.mjs`, hồi quy `module-navigation.test.mjs` và `shcn.test.mjs`; browser `frontend/e2e/shcn-rounds-ui.mjs`        |

Giữ màn hình phân công khoa hiện có. Không sửa Foundation/Đào tạo, Auth hoặc Shared. Chỉ đăng ký dữ liệu danh mục quyền SHCN vào các bảng chung qua migration mới; không thay cấu trúc của các bảng đó. Không triển khai đồng bộ phân công, đối tượng/mẫu, duyệt/phát hành, phòng, PDF hoặc ký.

## 2. Bảng và quyền

V18 tạo đúng **một bảng SHCN đã có trong ERD**: `shcn.shcn_dot_sinh_hoat`, 27 cột. Không thêm field nghiệp vụ vào ERD, không thêm FK mã học kỳ sang ID nội bộ Foundation.

- Bảng có PK identity, rowversion, CHECK ngày/JSON/enum/phiên bản và trigger bảo vệ người tạo/ngày tạo, thông tin chung đã khóa.
- Tạo mới: `NHAP`, `CHUA_DONG_BO`, `DANG_CHOT`; các con trỏ hồ sơ/phiên bản/thời điểm workflow đều NULL.
- Sửa: typed PATCH đủ sáu field và `expectedVersion`; khóa/CAS và audit cùng transaction. Version cũ bị409, không tự ghi đè. Chỉ sửa tên không làm mất con trỏ phân công nháp; thay khung/kỳ/hạn làm mất hiệu lực con trỏ nháp như contract.
- Migration đăng ký role canonical `shcn_chuyen_vien` ở hai danh mục hiện hữu; quyền `shcn.dot.read`, `shcn.dot.prepare`, `shcn.reference.read`, mặc định gắn role đó. Không gắn cho account bất kỳ.
- Mỗi request kiểm online identity và **permission Auth + grant Foundation toàn cục của cùng role canonical**. Không nối quyền role A với scope role B, không dùng quyền MGHP/Trưởng khoa/admin thay quyền SHCN.

## 3. Chuẩn bị DEV — chủ dự án tự thực hiện

Các bước dưới đây là hướng dẫn, **chưa được chạy**. V18 còn pending nên backend cũ không có API này; không đổi `ddl-auto`, tắt Flyway hoặc sửa history để bỏ qua migration.

1. Review file V18 và kiểm không trùng số migration với phần nhóm vừa merge. Không sửa V14/V15 đã áp dụng.
2. Khi chủ dự án quyết định migrate: ở root `ctsv`, dùng launcher DEV hiện có theo thứ tự `build` → `migrate` → `start`. Kiểm status/log để xác nhận đúng instance/DB DEV, Flyway và Hibernate validation. Lượt này Codex không chạy các bước ghi DB đó.
3. Dùng màn hình quản trị Auth hiện có gán role `shcn_chuyen_vien` cho tài khoản chuyên viên được chọn, ví dụ `dev.chuyenvien` nếu account đó tồn tại. Giữ role CTSV_USER/quyền cũ; không cần tài khoản mới hoặc mật khẩu mới. Đăng nhập lại sau khi cấp quyền.
4. Người quản trị dữ liệu cấp grant Foundation: đúng `nguoi_dung_id` đã mapping với account/issuer đó; role `vai_tro.ma='shcn_chuyen_vien'`; `ma_phan_he='SHCN'`; `don_vi_id=NULL`; thời gian hiệu lực phù hợp. Chỉ gán role Auth chưa đủ. Không dùng trang phân quyền **MGHP** để tạo scope SHCN hoặc lấy ID số của role Auth thay ID Foundation. Nếu chưa có thao tác quản trị SHCN cho scope này, đây là bước chuẩn bị dữ liệu DEV của owner; task này không bổ sung một màn hình quản trị quyền mới.
5. Bật `ctsv.shcn.persistence.enabled=true` theo cấu hình SHCN hiện có. Nguồn học kỳ dùng `ctsv.shcn.dot.source-code`; nếu không đặt riêng thì kế thừa `ctsv.shcn.phan-cong-khoa.source-code`. Nguồn phải có đúng một `he_thong_ngoai` đang hoạt động và dữ liệu `hoc_ky` hiện diện. Không hardcode mã học kỳ synthetic trong frontend hoặc tự seed Đào tạo/Foundation.

Không có lệnh cấp role/grant hoặc seed tự động được chạy trong lượt này. Tài khoản `dev.truongkhoa`, `dev.giangvien` và chỉ có role MGHP sẽ không tự thấy menu quản lý đợt. Nếu chưa có quyền/học kỳ, UI phải báo đúng lỗi; không coi đó là danh sách trống.

## 4. Thao tác trên giao diện sau khi DEV sẵn sàng

1. Đăng nhập account chuyên viên đã có **cả permission và global grant SHCN**. Chọn **Sinh hoạt chủ nhiệm → Quản lý đợt SHCN**, URL `/shcn/dot-sinh-hoat`.
2. Tra danh sách bằng học kỳ, trạng thái, tên đợt. Bộ lọc khoa chưa mở vì đối tượng thuộc CV-03/05.
3. Chọn **Tạo đợt SHCN**. Chọn học kỳ từ nguồn; nhập tên, ngày đầu/cuối, khoảng tiết và hạn chốt. Ví dụ đặt tên **Đợt thực hành SHCN**, chọn mã học kỳ có thật trong dropdown, khung `01/12/2026–07/12/2026`, tiết `11–12`; cũng có thể thêm `5–6`. Không tự suy năm học từ mã; năm học thiếu sẽ hiện đúng trạng thái thiếu nguồn.
4. Bấm **Lưu nháp**. Sau thành công mở chi tiết với trạng thái Nháp và token mới. Chưa cần phân công GVCN, đối tượng hay mẫu; chưa phát thông báo.
5. Chọn **Sửa thông tin**, sửa tên/khung rồi **Lưu thay đổi**. Trở về danh sách kiểm tên mới; tải lại route kiểm dữ liệu đã lưu.
6. Nếu409: giữ nội dung local, chọn **Đọc bản mới**, xem đối chiếu và chỉ **Dùng bản hệ thống** khi đồng ý. Không tự retry PATCH.
7. Nếu mất phản hồi POST: chọn **Đối chiếu danh sách**; trùng tên không chứng minh cùng lần tạo. Chỉ chủ động cho phép tạo lại sau khi đã đối chiếu. Không tự POST lần hai.

Chưa có xóa/phát hành/trình duyệt ở màn hình đợt. Đợt đã khóa vẫn đọc được; việc sở hữu tác giả không chặn chuyên viên khác có quyền toàn trường.

## 5. Những gì đã kiểm và chưa kiểm

Kết quả máy và lệnh thực chạy: [EVIDENCE](evidence/task-cv-02/results.json), [bảng kiểm chứng](task-cv-02-tests.md). Đây là Chrome thật với **HTTP fixture**, không phải backend/DB thật. Không dùng ảnh fixture để khẳng định đã seed hoặc migrate DEV.

Phải xác minh sau khi owner áp dụng migration/cấp quyền: cú pháp và transaction SQL Server của V18; Hibernate schema validation; CAS khi hai writer thật; rollback audit thật; JOIN permission–scope/thu hồi trên DB thật; học kỳ theo nguồn DEV; smoke giao diện qua launcher DEV. Các mục này hiện **NOT VERIFIED**, không chuyển tất cả 40 ca thiết kế thành PASS.

Hồ sơ workflow dùng chung chưa có public contract kiểm trạng thái hoàn tất trong slice này. Vì vậy root có một trong các `ma_ho_so_*` khác NULL sẽ bị chặn sửa an toàn; chưa tuyên bố hỗ trợ chỉnh lại một hồ sơ đã từng trình và đã kết thúc. Nhánh đó hoàn thiện cùng task workflow, không mặc định hồ sơ cũ là hoàn tất.

## 6. Chi tiết tích hợp UI/API

Không thêm API lấy quyền. Bốn API đợt trả header **`X-SHCN-Can-Prepare: true|false`** từ cùng phép kiểm quyền toàn cục trong transaction. Probe menu dùng GET danh sách `page=0&size=1`, không chỉ dựa vào tên role hoặc các function từ Auth. Header bổ sung không đổi DotDTO; backend vẫn kiểm lại khi ghi. CORS chỉ áp đường dẫn SHCN mới, cho PATCH và expose header này/Location.

CV-001 là GET `/api/shcn/tra-cuu/hoc-ky`: response phân trang `items[{ma,ten,namHoc}]`; `ma` là code nguồn nguyên vẹn, `ten`/`namHoc` nullable. Cần `shcn.reference.read` cùng grant toàn cục. Không yêu cầu các mốc học kỳ đã được MGHP xác minh.

Frontend chỉ sửa feature SHCN và các điểm nối App/menu cần thiết; không thêm dependency, không đổi Auth client hoặc giao diện Foundation. Đã mở và kiểm ảnh desktop/mobile360; ô nhập ngày native theo locale trình duyệt, ngày hiển thị đọc dùng vi-VN.

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
