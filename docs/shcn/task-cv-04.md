# TASK-CV-04 — Mẫu biên bản của đợt SHCN

## UI mới nhất — chỉ checkbox Bắt buộc

- Đã bỏ toàn bộ cấu hình nâng cao khỏi editor. Mỗi mục tùy biến có checkbox Bắt buộc cạnh tiêu đề; không còn UI chỉnh nhãn/kiểu/nguồn hoặc thêm-xóa trường.
- Checkbox đồng bộ batBuoc ở mục và các trường nội dung hiện có. Giữ mã, nhãn, kiểu, nguồn và số trường của mẫu cũ; mục mới mặc định một trường Nội dung dạng văn bản dài người dùng nhập.
- Hai mục kiến nghị vẫn giữ cố định tiêu đề và tùy chọn theo V27; checkbox không chọn và không cho đổi. Điểm danh giữ bắt buộc. Không khôi phục V28.
- Lượt UI này không sửa BE/API/schema, không truy vấn/ghi DB, không migrate/seed/restart/package JAR hoặc push. Kiểm 12 Node tests, 22 luồng Chrome dùng API fixture, FE build đạt; ảnh desktop/mobile đã xem. BE dùng validation hiện có đã kiểm ở lượt trước, không chạy lại suite BE cho việc bỏ UI.
- Evidence: `API/TASK_CV_04_MAU_BIEN_BAN/verification-checkbox.json`, `EVIDENCE_CHECKBOX_2026_11_24`, `EVIDENCE_CHECKBOX_HOI_QUY_2026_11_24`. Quyết định này thay các mô tả giữ mục nâng cao bên dưới.


## Quyết định cuối: tiêu đề hệ thống cố định, không có V28

- Điểm danh và hai tiêu đề kiến nghị được giữ cố định theo V27. FE để read-only đúng hai ô tiêu đề kiến nghị; BE từ chối đổi tên dù gọi API trực tiếp. Các mục tùy biến vẫn sửa/xóa được, hai mục kiến nghị vẫn đổi thứ tự và nội dung không bắt buộc.
- Giữ toàn bộ bố cục gọn, phần nâng cao, soạn mẫu trước ID và lưu chung đợt/đối tượng/mẫu đã triển khai.
- V28 đã thu hồi khỏi source và target/classes để tránh resource cũ bị đóng gói lại. V27 không sửa, không có migration thay thế; không sửa history/trigger hoặc dữ liệu DB.
- SELECT history tại DEV2022/ctsv_dev xác nhận V27 đã thành công, không có V28. Máy khác chưa được kiểm tra; không suy lịch sử DB chung từ local.
- Kiểm cuối: 46 Java tests unit/MockMvc, 9 Node model tests, 11 luồng Chrome API fixture; 0 lỗi. FE TypeScript/Vite build đạt. SQL/DEV ghi thật, triển khai JAR mới chưa kiểm; không migrate, seed, restart, commit hoặc push.
- Evidence mới: `API/TASK_CV_04_MAU_BIEN_BAN/verification-v27-lock.json`, `EVIDENCE_V27_TIEU_DE_CO_DINH_2026_11_24`. Các evidence cho sửa tiêu đề trước đó là lịch sử đã bị quyết định này thay thế.

## Bản điều chỉnh owner 24/11/2026 — lưu chung, trình soạn gọn, giữ quy tắc V27

- Tạo/sửa đợt hiển thị cả thông tin chung, đối tượng và mẫu. Thiết kế mẫu trước khi có ID đợt; **Áp dụng mẫu** chỉ cập nhật state trên FE. **Lưu nháp/Lưu thay đổi** gửi một POST/PATCH lưu toàn bộ trong một transaction.
- Editor gọn: tên mẫu, Điểm danh, các dòng tiêu đề, lên/xuống, xóa mục tùy biến, thêm mục. Các cấu hình trường cũ nằm trong phần nâng cao, được giữ nguyên khi chỉ sửa tiêu đề. Preview vẫn dùng dữ liệu minh họa.
- `KIEN_NGHI_SV` và `KIEN_NGHI_GV` luôn tồn tại, tiêu đề cố định theo V27; mã/loại/trường tùy chọn được giữ. Đổi thứ tự không đổi mã. Điểm danh vẫn cố định đầu tiên, ba số tổng hợp.
- GET `/api/shcn/tra-cuu/cau-truc-mau-bien-ban` trả danh mục trước khi có ID đợt, kiểm Auth/RBAC/global scope; không truy cập hay tạo mẫu trong DB.
- POST/PATCH đợt nhận tùy chọn `mauBienBan: {tenMau,muc}`. Không có expectedVersion lồng nhau; PATCH dùng version ngoài của đợt. Bỏ thuộc tính giữ mẫu cũ, null không hợp lệ. Giới hạn tổng body 1MiB và toàn bộ validation trường/nguồn giữ nguyên.
- Backend kiểm dữ liệu, khóa/CAS root khi sửa, ghi đợt/đối tượng/mẫu/mục/audit cùng transaction; lỗi ghi con rollback toàn bộ. GET/PUT mẫu cũ vẫn được giữ.
- FE giữ nháp mẫu và dữ liệu đợt khi lỗi; không tự gửi lại sau conflict/timeout. Đọc cả root và mẫu, kiểm cùng version trước khi người dùng chọn bản hệ thống. Hủy modal giữ bản đã áp dụng; rời trang có cảnh báo thay đổi mẫu.
- Lỗi trường nâng cao tự mở đúng nhóm để sửa; modal không submit nhầm form cha, giữ theme và bố cục mobile hiện hành. Không đổi Auth/Foundation/Đào tạo, không tạo bảng, không làm duyệt/phát hành/PDF/ký/thông báo.

### Migration và môi trường

**Quyết định cuối owner: giữ V27, thu hồi V28.** Đã xóa V28 khỏi source và resource build cũ; không tạo migration mới hoặc chỉnh V27. Đọc lịch sử DEV2022/ctsv_dev xác nhận V27 đã áp dụng thành công, chưa có V28. Không thay đổi DB. Chưa seed, restart, package JAR, cấp quyền, commit hoặc push trong task này.

FE được kiểm trên server hiện có bằng Chrome với API fixture. BE được compile/test; backend đang chạy chưa được thay bằng source mới. Đợt kiểm cuối chỉ đọc history: local đã có V27, không cần V28. Owner build/khởi động backend bản mới theo launcher khi muốn dùng thay đổi code; không tự chạy start/migrate. Không khẳng định SQL trigger, persistence/rollback/concurrency thật hoặc runtime DEV đã đạt từ kết quả mock.

### Kiểm chứng trước quyết định thu hồi V28 (lịch sử)

- 120 Java tests, 0 fail/error/skip: unit/MockMvc + mock repository/transaction manager; có HTTP tạo chung, lỗi ghi con, quyền, stale version, khóa mẫu và contract lồng nhau.
- 43 Node tests, đều đạt: model/validation/payload/API, gồm mã kiến nghị không đổi và chỉ một POST/PATCH.
- FE TypeScript/Vite build đạt; còn cảnh báo bundle lớn hiện có.
- Chrome: 11 luồng lưu chung + 10 hồi quy mẫu + 7 hồi quy CV03; API STUB, SQL NONE. Hình desktop/mobile đã được xem. Evidence và contract cập nhật nằm ở workspace API/TASK_CV_04_MAU_BIEN_BAN.
- Chi tiết thiết kế mới: `API/TASK_CV_04_MAU_BIEN_BAN/04_DIEU_CHINH_LUU_CHUNG.md`; OpenAPI CV02 và CV04 được cập nhật cùng request.

Lệnh kiểm tập trung tại root CTSV (JDK21; không dùng package hay migrate):

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=MauRequestsTest,MauServiceTest,MauHttpTest,RoundTemplateSaveTest,DotRequestsTest,DotServiceTest,DotAudienceRequestsTest,DotHttpTest,DotAudienceServiceTest' test
node --test frontend/tests/shcn-minutes-template.test.mjs frontend/tests/shcn-rounds.test.mjs frontend/tests/shcn-round-audiences.test.mjs frontend/tests/shcn-round-date-range.test.mjs frontend/tests/shcn-semester-audience-api.test.mjs
npm --prefix frontend run build
```

## Bàn giao lần triển khai ban đầu (lịch sử)


Source triển khai ngày **24/11/2026** theo hai tài liệu `API/TASK_CV_04_MAU_BIEN_BAN` trong workspace dữ liệu. OpenAPI của slice nằm tại `API/TASK_CV_04_MAU_BIEN_BAN/openapi.json`.

## Hành vi

- GET/PUT `/api/shcn/dot-sinh-hoat/{dotId}/mau-bien-ban`, giữ Auth/RBAC/global scope và guard đợt của CV-02. Đọc mẫu chưa tồn tại trả 200 empty DTO và danh mục, không ghi DB.
- PUT thay toàn bộ mẫu/mục theo root rowversion; khóa root, CAS, upsert mẫu/mục và audit chung transaction. Giữ ID mẫu/mã mục, tránh UNIQUE khi đổi thứ tự. Không sửa phân công hoặc đối tượng CV-03.
- Một mẫu cho một đợt; hai Entity/bảng mới thuộc SHCN. Không sửa bảng/Entity Foundation hoặc Đào tạo.
- Owner chốt luôn giữ Điểm danh + Kiến nghị của SV + Kiến nghị của GV. Điểm danh ba số bắt buộc, không danh sách SV. Hai mục kiến nghị có nội dung tùy chọn, mã/tên/trường cố định; đổi vị trí được sau Điểm danh.
- FE tạo/sửa mục và trường tùy biến, chọn nguồn tự điền trong whitelist, xem trước web form Offline/Online bằng dữ liệu minh họa; Online hiện link minh chứng. Không sinh PDF/biên bản lớp hoặc gửi thông báo.
- Panel ở chi tiết đợt đã lưu. Sửa mẫu và sửa thông tin đợt không mở đồng thời; sau lưu/đối soát làm mới root version. Conflict/timeout giữ input, không tự retry PUT.

## Migration và giới hạn DEV

**V27__shcn_minutes_template.sql mới chỉ được viết, chưa chạy.** Không migrate, seed, cấp quyền, cấu hình hoặc restart trong lượt triển khai này. Không đóng gói đè JAR backend đang chạy.

- `mvn test` đã compile BE mới; chưa package/deploy JAR mới vào tiến trình DEV.
- TypeScript/Vite build đã đạt, nhưng FE mới cần backend mới và hai bảng được owner áp dụng để chạy với DB thật.
- Không báo luồng SQL/DEV hoàn tất khi chỉ có MockMvc hoặc API fixture. Hibernate validate/DDL/trigger, UNIQUE/reorder/rollback và race trên SQL Server thật vẫn **NOT VERIFIED**.
- Đã có quyền chuyên viên từ V18; task này không tạo account, gán lại role/permission hoặc nạp dữ liệu mẫu.

Khi owner cho phép áp dụng môi trường, phải dừng launcher trước build/migrate để tránh JAR đang dùng. Kiểm lại số migration so với main và lịch sử DB trước khi áp V27; không đổi checksum migration đã áp dụng. Sau đó khởi động qua launcher SHCN có nguồn đã cấu hình, kiểm account được phép/thiếu quyền và một luồng lưu/đọc lại trên DB được owner cho phép. Không tự chạy các bước này từ hướng dẫn.

## Kiểm chứng đã chạy

- **82 Java tests**: 32 CV-04 và 50 hồi quy validation/HTTP đợt; 0 fail/error/skip. Unit/MockMvc, repository và transaction manager giả lập, không SQL. Có kiểm CORS PUT từ origin hợp lệ và từ chối origin lạ.
- **39 Node tests**: model/API SHCN, gồm cấu hình mẫu, mục cố định, nguồn tự điền và body PUT; đều đạt.
- **17 luồng Chrome**: 10 CV-04 + 7 hồi quy CV-03; API HTTP fixtures, SQL NONE. Kiểm tạo/sửa/xem trước/lưu/mở lại, lỗi sau cuộn, conflict, timeout đối soát, quyền và mobile. Đã xem ảnh desktop/mobile.
- FE TypeScript/Vite build PASS; còn cảnh báo bundle lớn hiện có. Git diff whitespace check PASS.

Lệnh tại root CTSV, JDK21:

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=MauRequestsTest,MauServiceTest,MauHttpTest,DotRequestsTest,DotAudienceRequestsTest,DotHttpTest' test
node --test frontend/tests/shcn-rounds.test.mjs frontend/tests/shcn-round-audiences.test.mjs frontend/tests/shcn-round-date-range.test.mjs frontend/tests/shcn-semester-audience-api.test.mjs frontend/tests/shcn-minutes-template.test.mjs
npm --prefix frontend run build
```

Browser harness: `frontend/e2e/shcn-minutes-template-ui.mjs`; dùng `SHCN_UI_ORIGIN`, `SHCN_UI_EVIDENCE` và tùy chọn `SHCN_UI_CASE`. Tất cả `/api` được route fixture; không dùng kết quả này làm bằng chứng backend/SQL DEV thật.

Bằng chứng tại workspace dữ liệu: `API/TASK_CV_04_MAU_BIEN_BAN/EVIDENCE_BROWSER_2026_11_24`, `EVIDENCE_REGRESSION_CV03_2026_11_24`; Java reports ở `target/surefire-reports`. Không chứa token, password hoặc PII thật.

## Thao tác sau khi owner áp dụng DB/backend

1. Đăng nhập tài khoản có quyền chuyên viên SHCN, vào Quản lý đợt.
2. Mở đợt nháp đã lưu → Mẫu biên bản → Tạo mẫu biên bản.
3. Đặt tên, chỉnh các mục/trường tùy biến; ba mục cố định không xóa được.
4. Xem trước Offline/Online, quay lại chỉnh → Lưu mẫu.
5. Mở lại mẫu hoặc sửa thông tin đợt, xác nhận version mới được dùng. Không yêu cầu nguồn SV hoặc phân công hiệu lực để lưu cấu trúc mẫu.
