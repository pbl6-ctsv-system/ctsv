# Bàn giao TASK-CV-02 — backend và frontend

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
