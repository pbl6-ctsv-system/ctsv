# Kiểm chứng TASK-CV-02

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
