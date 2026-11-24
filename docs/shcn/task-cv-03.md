# CV-03 — đối tượng SHCN theo học kỳ

Cập nhật source ngày **24/11/2026** theo `API/TASK_CV_03_DOI_TUONG_AP_DUNG.md` và `API/TASK_CV_03_DU_LIEU/THIET_KE_FE_BE_PHAN_NHOM_THEO_HOC_KY.md` trong workspace dữ liệu. OpenAPI hiện hành của slice: `API/TASK_CV_03_DU_LIEU/openapi.json`.

## Hành vi đã triển khai

- Lọc học vụ theo nguồn/học kỳ; tách SV đã có lớp RenLuyen nhận đúng kỳ trước tổng hợp khóa. Bỏ chọn nhóm trễ tiến độ không đưa SV về nhóm khóa. Không tự xét vượt kỳ chuẩn hoặc ghi lại phân lớp nguồn.
- Cờ thôi/ngừng/dừng bằng 1 loại SV khỏi tập thường; null/không nhận diện không được coi là âm tính. Thành viên roster có cờ trái chiều vẫn chỉ để đối chiếu, cần xác minh.
- DTO nhóm có `trangThaiDuLieuHocVu`, `trangThaiPhanNhom`, `canhBao`. Chưa có bằng chứng độ phủ đầy đủ của roster trong schema hiện tại: ứng viên thường vẫn `CAN_XAC_MINH`, không đủ căn cứ gửi thông báo. Có dòng phân lớp không đồng nghĩa toàn kỳ đã đủ.
- Lookup lớp/SV phân trang dùng cùng projection. Mã kỳ/khóa/lớp là mã thật trong nguồn, không cắt mã hoặc tự chuyển 261 thành 2610. Lớp thường theo hồ sơ hiện tại, chưa phải lịch sử lớp đã xác nhận.
- GET `/api/shcn/dot-sinh-hoat/{dotId}/doi-tuong`: preview cấu hình đã lưu, version, count lớp, ngày/hình thức và cảnh báo. Chỉ draft/bị trả lại trong guard hiện có; không phải snapshot đã phát hành. Metadata khoa/chương trình đọc qua FK có sẵn, thiếu trả null.
- POST nháp phải có ít nhất một nhóm, ngày riêng đủ; PATCH gửi array thì thay toàn bộ, bỏ array thì giữ nhóm cũ và vẫn kiểm nguồn. Kết quả 0 nhóm bị từ chối. Server đối chiếu min/max ngày tổng với nhóm hiệu lực. Nhóm lịch sử có ngày null vẫn đọc được, không backfill.
- Giữ RBAC/global scope, CAS rowversion, state guard, transaction root/child/audit. Không thêm permission hoặc cấp quyền DEV.
- Adapter SHCN kiểm mã theo lô để không phát sinh SQL cho mỗi lớp/khóa; hai tập thành viên vẫn được đọc theo kỳ với trần 200.000 dòng mỗi tập. Chưa đo execution plan trên DB thật, không hứa tốc độ production.

## Thao tác giao diện

1. Vào **Sinh hoạt chủ nhiệm → Quản lý đợt SHCN → Tạo đợt SHCN**, hoặc mở đợt nháp và chọn sửa.
2. Chọn học kỳ bằng tên. Danh mục khóa/nhóm tải từ backend; cảnh báo nguồn giữ ngay cạnh nhóm.
3. Có thể bấm **Xem lớp** trước khi tick nhóm. Panel hỗ trợ tìm/phân trang, **Xem SV**, căn cứ và trạng thái cần xác minh. Nhóm trễ tiến độ dùng lớp nhận đúng kỳ.
4. Tick nhóm và nhập ngày/hình thức riêng; lưu nháp như trước. Không có thao tác gửi thông báo trong slice này.
5. Sau lưu, bấm **Xem các lớp áp dụng** để xem cấu hình đã lưu. Response có version khác chi tiết hiện tại bị từ chối hiển thị và yêu cầu mở lại chi tiết; không trộn với thay đổi chưa lưu.
6. Nguồn lỗi/mapping thiếu có thông báo và thử lại GET, giữ input. Lỗi trong modal hiển thị ngay trong modal SHCN; không thay cơ chế feedback shared.

## Kiểm chứng đã chạy

- **126 ca Java**: 13 lớp unit/MockMvc/mock JDBC, 0 fail/error/skip. Không Spring Boot DB integration, không tạo/reset DB.
- **27 ca Node**: model form/ngày/nhóm, route guard và API client.
- **28 hành trình Chrome**: 7 ca mới CV-03, 8 hồi quy nhóm, 13 hồi quy đợt. Chrome thật, toàn bộ `/api` là HTTP fixture synthetic; **SQL NONE**, không gọi lệnh ghi lên backend thật. Đã xem ảnh desktop và modal mobile 360px.
- Build backend JAR và TypeScript/Vite FE: **PASS**. Package backend dùng skipTests sau lượt 126 ca đã đạt; không dùng kết quả skip để tính số ca. Vite còn cảnh báo bundle lớn hiện có, không sửa cấu hình build chung để che cảnh báo.
- Đã khôi phục dependency `pdfjs-dist@6.5.438` vốn khai báo sẵn để build FE; không thay package.json/lockfile hoặc code PDF/shared.

Lệnh kiểm BE tại root CTSV (JDK21):

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=SemesterAudienceResolverTest,JdbcSemesterAudienceSourceTest,DotSemesterAudienceHttpTest,DotHttpTest,DotAudienceServiceTest,DotServiceTest,DotRequestsTest,DotAudienceRequestsTest,SemesterAudiencePolicyTest,DotAudiencePreviewHttpTest,DotAudienceWritePolicyTest,AudiencePreviewResolverTest,JdbcAudienceQueryBudgetTest' test
```

Lệnh kiểm FE tại root CTSV:

```powershell
node --test frontend/tests/shcn-rounds.test.mjs frontend/tests/shcn-round-audiences.test.mjs frontend/tests/shcn-round-date-range.test.mjs frontend/tests/shcn-semester-audience-api.test.mjs
```

Build FE tại `frontend`: `npm.cmd run build`. Browser fixture dùng `frontend/e2e/shcn-cv03-semester-audience.mjs`, `shcn-round-audiences-ui.mjs`, `shcn-rounds-ui.mjs`; chạy với Vite kiểm thử riêng, không dùng để kết luận backend/SQL thật đã đạt.

Bằng chứng trong `%TEMP%/shcn-cv03-*-20261124/` và `%TEMP%/shcn-cv03-*.log`; report Java trong `target/surefire-reports/`. Không chứa token/tài khoản thật hoặc PII thật.

## Giới hạn và bàn giao DEV

- **Không migrate/seed/import/ghi DB thật**, không đổi schema/Entity Foundation/Đào tạo, không restart dịch vụ DEV. Không cần migration mới cho thay đổi source của slice này.
- Chưa xác minh JDBC/plan với dữ liệu SQL thật, pipeline mapping kỳ/khóa và độ phủ roster. Test fixture/mock không chứng minh các dependency này đã sẵn sàng.
- DB local trước đó chưa có học vụ/roster và mapping khóa đầy đủ. Giao diện phải báo thiếu dữ liệu đúng thực tế, không tạo dữ liệu mẫu để lấp chỗ trống.
- Tiến trình backend DEV có thể vẫn dùng JAR cũ. Owner chủ động build/start bằng launcher đã duyệt khi muốn nạp source mới; không cần tự chạy migration/seed cho tính năng này. Nếu launcher báo migration cũ đang pending, xử lý riêng theo quyết định owner, không bỏ kiểm tra.
- Tài khoản/role/global scope dùng cấu hình hiện có, không provision lại trong lượt này. Menu hiện không đồng nghĩa nguồn dữ liệu đã đủ.


### Điều chỉnh giao diện theo owner — 24/11/2026

- Form đối tượng còn 4 cột: Đối tượng / Từ ngày / Đến ngày / Hình thức. Bỏ cột Xem lớp; BE tiếp tục xác định lớp/SV đúng kỳ, không thay projection hoặc quyền.
- Tick các nhóm cần áp dụng, nhập đủ một nhóm rồi mở “Sao chép cấu hình”, chọn nguồn và các nhóm nhận, bấm “Áp dụng cấu hình”. Chỉ sao chép ngày và hình thức một lần; không liên kết các nhóm, không tự chọn nhóm chưa tick, không ghi DB trước Lưu nháp. Giữ nguyên khung tiết riêng nếu có.
- Ghi mới/sửa cấu hình chỉ chấp nhận OFFLINE / ONLINE tại FE và DotRequests BE. Dữ liệu lịch sử không bị sửa hoặc migration; giá trị legacy khác hai loại phải chọn lại khi sửa nhóm.
- Bỏ phần hướng dẫn dài, thống kê lặp và danh sách tên lớp rèn luyện khỏi form. Chỉ giữ lỗi/thiếu dữ liệu cần xử lý và một dòng nhắc xác minh trước gửi thông báo; không bỏ guard nguồn.
- Không migration, seed hoặc thay quyền trong lượt chỉnh giao diện này.
