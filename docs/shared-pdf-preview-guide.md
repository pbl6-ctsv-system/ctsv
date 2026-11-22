# Hướng dẫn xem PDF private dùng chung — CTSV

Áp dụng cho mọi module cần xem hoặc tải PDF đã lưu trên hệ thống. Hướng dẫn này thống nhất cách tích hợp; không tự mở thêm quyền, endpoint hoặc thay đổi nghiệp vụ của module.

## 1. Luồng chuẩn

```text
Người dùng chọn tài liệu
  → Frontend lấy fileId từ dữ liệu backend
  → Auth client gọi GET /api/ctsv/tep/{idTep}/noi-dung kèm JWT
  → Backend kiểm danh tính, quyền trên file và trạng thái
  → Backend tải bytes private, kiểm kích thước và SHA-256
  → Frontend nhận Blob application/pdf
  → URL.createObjectURL(blob)
  → iframe hiển thị blob URL
```

Chỉ backend kết nối private storage. Frontend không nhận storage credentials hoặc URL có chữ ký của provider. Blob URL tồn tại tạm trong browser, không phải URL công khai của file.

## 2. Những phần đã có và giới hạn cần biết

| Phần | Hiện trạng |
|---|---|
| API trả PDF | Đã có `GET /api/ctsv/tep/{idTep}/noi-dung`, không query/body; trả `application/pdf`, `Cache-Control: no-store` |
| Authentication client | Đã có [auth-client.mjs](../frontend/src/shared/auth/auth-client.mjs), dùng instance được ứng dụng truyền xuống |
| File backend | Dùng chung `shared.files`: controller, service, metadata, storage và spool |
| Component viewer công khai trong `shared/ui` | **Chưa có** tại thời điểm viết hướng dẫn; cần đưa viewer hiện hành ra boundary dùng chung trong một thay đổi riêng được giao |
| Quyền trên file của module mới | **Không tự có** chỉ vì dùng cùng endpoint; cần xác minh nguồn quyền và quan hệ file–đối tượng đã được backend hỗ trợ |

`FileMetadata.links()` và `FileService.canRead()` hiện xử lý những loại liên kết đã triển khai, chưa phải bộ phân giải quyền tổng quát cho mọi module. Không mặc định rằng có JWT, biết fileId hoặc tự lưu thêm một FK là đủ được xem. Đối với loại liên kết mới, phải đóng contract/authorization và phối hợp phần shared trước; không mở quyền chung để làm viewer hoạt động.

Header hiện tại là `Content-Disposition: attachment`. Frontend gọi API lấy Blob rồi đưa Blob URL vào iframe nên vẫn xem được; không cần đổi thành `inline` chỉ để dùng luồng này.

## 3. Cách module tích hợp

1. API nghiệp vụ của module trả fileId và tên tài liệu mà caller được phép thấy. Lấy fileId từ response, không yêu cầu người dùng gõ mã và không coi ID là bằng chứng được cấp quyền.
2. Dùng Auth client chung. Không tạo fetch wrapper, refresh token hoặc kho JWT riêng.
3. Xác minh endpoint đọc file chấp nhận đúng identity/capability và quan hệ đối tượng của module. Nếu chưa hỗ trợ, ghi dependency; không tự tạo endpoint tải file song song.
4. Khi viewer đã được xuất công khai ở shared, module chỉ truyền fileId/tên và quản lý lựa chọn tài liệu. Không import nội bộ feature khác hoặc copy viewer vào mỗi module.
5. Khi ghi metadata/thao tác nghiệp vụ thành công, đọc lại thông tin liên quan. Giữ viewer nếu fileId chưa đổi; không tải lại bytes chỉ vì ETag/trạng thái đối tượng cha đổi.

Ví dụ gọi **API hiện có** từ một handler/effect đã có Auth client:

```ts
const response = await client.request(
  '/api/ctsv/tep/' + fileId + '/noi-dung',
  { signal: controller.signal },
);
const blob = await response.blob();
if (!blob.type.toLowerCase().includes('application/pdf')) {
  throw new Error('Nội dung nhận được không phải PDF.');
}
const objectUrl = URL.createObjectURL(blob);
// Viewer đặt objectUrl vào iframe.src và thu hồi bằng URL.revokeObjectURL.
```

Đây là minh họa giao thức, **không phải component hoàn chỉnh để copy vào từng feature**. `controller`, việc bỏ response cũ, state và cleanup thuộc viewer dùng chung. `client.request` hiện đã kiểm status và ném `ApiError` khi request thất bại.

## 4. Ranh giới component để tránh trùng lặp

Khi được giao tách viewer dùng chung, chỉ đưa phần kỹ thuật ra `frontend/src/shared/ui/`, giữ nguyên hành vi và consumers đang hoạt động. Không thay kiến trúc navigation/router.

| Viewer dùng chung chịu trách nhiệm | Module chịu trách nhiệm |
|---|---|
| Fetch PDF qua Auth client; Blob URL; iframe; loading/error/retry; cleanup; bố cục responsive | Danh sách tài liệu, file được chọn, tên/ngữ cảnh và quyền hiển thị tác vụ |
| Hiển thị đúng bytes của fileId mới nhất | Trạng thái nghiệp vụ, nút đồng ý/từ chối, lịch sử và các command |

Đầu vào tối thiểu dự kiến: `client`, `fileId`, `title`; nếu cần tải xuống thì thống nhất một hành vi/callback chung. Đây là đề xuất boundary, **chưa phải interface đã tồn tại để import**. Shared không import feature hoặc chứa điều kiện theo tên module/role.

Không dùng component này để quyết định authorization. Backend vẫn kiểm từng request, kể cả người dùng sửa URL/fileId hoặc quyền bị thu hồi.

## 5. Quy tắc triển khai viewer

- Effect phụ thuộc client ổn định, fileId và lần thử lại. Không phụ thuộc toàn bộ object cha/ETag hoặc dùng key thay đổi theo metadata khiến viewer remount.
- Khi đổi file: bỏ URL/lỗi cũ, hủy request cũ bằng AbortController; response cũ đến muộn không được thay file mới. Kiểm cả sau khi đọc Blob vì request có thể đã hoàn tất trước lệnh abort.
- Thu hồi Blob URL khi đổi file hoặc unmount. Không lưu Blob URL, PDF bytes/base64 hoặc JWT vào localStorage/sessionStorage, log hay URL điều hướng.
- Hiển thị loading → PDF hoặc lỗi có nút **Thử mở lại PDF**. Thử lại là GET do người dùng chủ động; không lặp vô hạn hoặc tự gửi lại upload/command.
- Mỗi tài liệu có tên và mục đang chọn rõ ràng. Hỗ trợ n tài liệu; nhiều mục cùng fileId có thể dùng chung bytes nhưng tên/ngữ cảnh phải cập nhật đúng.
- Iframe có title truy cập được, kích thước responsive; dùng CSS Modules/design tokens. Có nút tải PDF khi browser không hỗ trợ xem hoặc người dùng muốn mở bên ngoài.
- Không đặt API URL cần Bearer trực tiếp vào iframe: iframe không tự gắn JWT trong bộ nhớ của Auth client. Không truyền JWT qua query string.
- Giữ cấu hình bảo mật hiện hành; không nới CORS/CSP, CSRF hoặc TLS để chữa lỗi viewer.

## 6. Xử lý lỗi

| HTTP / code | Cách xử lý UI |
|---|---|
| 401 | Theo cơ chế Auth chung; không tự tạo vòng refresh/retry riêng |
| 403 | Thông báo không có quyền; không mở link storage thay thế |
| 404 `NOT_FOUND` | Thông báo không thể truy cập tài liệu; backend có thể che trường hợp khác chủ bằng404 |
| 409 `FILE_NOT_READY` | File chưa sẵn sàng; không hiển thị bytes cũ như file mới |
| 502 `FILE_INTEGRITY_FAILED` | Không hiển thị dữ liệu không qua kiểm toàn vẹn; báo lỗi và giữ ngữ cảnh |
| 503 `STORAGE_UNAVAILABLE` | Thông báo lỗi tải tạm thời, cho thử lại; mã này một mình không chứng minh credential sai |
| Hủy request khi đổi file | Bỏ qua kết quả cũ; không báo lỗi giả cho tài liệu mới |

Feedback phải nhìn thấy tại vị trí đang thao tác, giữ tài liệu được chọn và không biến request lỗi thành thành công.

## 7. Checklist bàn giao của mỗi module

- [ ] Đúng file/tên đã chọn; A→B→C và đổi nhanh không hiện response cũ. Dùng các PDF có nội dung phân biệt để kiểm bytes khác nhau.
- [ ] Nhiều giấy dùng cùng fileId vẫn có tên/ngữ cảnh đúng; cập nhật metadata không tạo GET PDF thừa.
- [ ] Người được phép xem/tải được; người thiếu quyền hoặc khác chủ bị từ chối qua API trực tiếp, không chỉ ẩn nút. Dataset phải có nhiều chủ khi kiểm truy cập chéo.
- [ ] Loading, mất kết nối,503/thử lại,401/403/404 và chuyển trang khi đang tải được xử lý; không có lỗi console do Blob URL đã thu hồi quá sớm.
- [ ] Download qua backend có SHA-256 trùng file synthetic gốc khi kiểm tích hợp bytes/storage thật.
- [ ] Desktop/mobile360px xem được nội dung PDF sau khi renderer tải xong; không tràn ngang. Có đường tải xuống phù hợp.
- [ ] Dùng đúng môi trường/DB được phép. Chỉ chạy kiểm thử theo tác động; không upload lại cho ca chỉ kiểm navigation.
- [ ] Ghi URL, DB, storage REAL hay STUB và kết quả thực thi. STUB503 có thể kiểm retry UI nhưng không chứng minh storage REAL đạt.

## 8. Điểm đọc source backend

- [FileController](../src/main/java/pbl6/ctsv/shared/files/FileController.java): route, JWT và response PDF.
- [FileService](../src/main/java/pbl6/ctsv/shared/files/FileService.java): authorization, ownership, trạng thái và kiểm toàn vẹn.
- [FileMetadata](../src/main/java/pbl6/ctsv/shared/files/FileMetadata.java): metadata và liên kết được hỗ trợ.
- [PrivateFileStorage](../src/main/java/pbl6/ctsv/shared/files/PrivateFileStorage.java), [CloudinaryFileStorage](../src/main/java/pbl6/ctsv/shared/files/CloudinaryFileStorage.java): interface và implementation storage private.
- [PdfSpool](../src/main/java/pbl6/ctsv/shared/files/PdfSpool.java): file tạm, giới hạn tài nguyên, SHA-256 và cleanup.

Khi nhiều thành viên cùng tích hợp: thống nhất một thay đổi shared và public interface, mỗi người chỉ nối feature của mình vào đó. Thêm loại quyền/liên kết phải có test riêng và phối hợp owner phần chung; không sửa rải rác controller/service dùng chung ở từng nhánh. Cách này giảm code lặp và xung đột, không bảo đảm Git sẽ không bao giờ có conflict.
