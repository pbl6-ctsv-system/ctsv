# Hướng dẫn xem PDF và chọn vùng ký dùng chung — CTSV

Component đã triển khai tại `frontend/src/shared/ui/pdf/`. Mọi module sử dụng cùng renderer; quyền và command nghiệp vụ vẫn do module/backend quyết định.

## 1. Luồng chuẩn

```text
Module chọn fileId từ API nghiệp vụ
  → AuthClient chung GET /api/ctsv/tep/{idTep}/noi-dung kèm JWT
  → Backend kiểm quyền trên file và kiểm toàn vẹn bytes private
  → Viewer nhận Uint8Array, PDF.js/worker render canvas
  → Người dùng chuyển trang, zoom, xoay hoặc tải chính bytes đang xem
```

Không đưa JWT vào URL, không mở CA/storage URL từ trình xem, không tạo Auth client riêng. Blob URL chỉ tạo tạm khi tải xuống, được thu hồi sau đó; không dùng iframe hoặc lưu PDF/token vào browser storage. PDF.js hiện hành **6.5.438** được tải khi mở PDF, dùng worker local do Vite đóng gói; không CDN và không thêm bản PDF.js khác cho từng module.

Owner đã xác nhận không cần tìm chữ/bôi đen/sao chép trong lần chuyển đổi này. Chọn vùng ký là chế độ tùy chọn, không sửa bytes PDF và không gọi API ký.

## 2. Cấu trúc file và ownership

```text
frontend/src/shared/ui/pdf/
  index.ts                  # entry công khai, chỉ export viewer và types
  types.ts                  # PdfViewerProps / PdfGeometry
  PdfViewer.tsx             # entry React lazy + loading boundary
  PdfDocumentView.tsx        # PDF.js/worker, canvas/toolbar, overlay và vòng đời
  PdfViewer.module.css       # style cô lập, dùng design tokens hiện hành
  pdf-source.mjs            # GET qua AuthClient, MIME/bytes, AbortSignal
  geometry.mjs              # pixel ↔ PDF points qua viewport
```

Module import entry `shared/ui/pdf`; không import renderer nội bộ, không import viewer từ feature khác, không copy worker/geometry/CSS. Shared không import `app` hoặc `features`, không biết tên module, role hay trạng thái nghiệp vụ.

| Viewer dùng chung | Module / backend |
| --- | --- |
| Tải đúng file, cancel/stale guards, loading/error/retry, render, toolbar, download và geometry | Danh sách, file được chọn, tên/ngữ cảnh, quyền và assignment |
| Trả vùng ký theo PDF points, hỗ trợ trang xoay/crop/zoom | Kiểm file/version/hash/ETag, xác nhận và command ký |
| Read-only mặc định, không tự gửi thao tác nghiệp vụ | Thứ tự người ký, CA adapter, lưu PDF sau từng bước, lịch sử và phục hồi |

## 3. Dùng để xem PDF

```tsx
import {PdfViewer} from '../../shared/ui/pdf'; // điều chỉnh mức ../ theo vị trí feature

<PdfViewer
  client={client}
  fileId={selectedFile.id}
  title={selectedFile.title}
  fileName={selectedFile.fileName}
/>
```

`client`, `fileId`, `title` là bắt buộc; `fileName` tùy chọn. Truyền instance AuthClient hiện có, ổn định. Viewer tải xuống bytes đã mở, không tạo GET thứ hai; module không cần truyền callback tải riêng.

Cập nhật tên/ETag hoặc đọc lại dữ liệu cha mà fileId không đổi sẽ giữ PDF đang xem. Không dùng `key` theo ETag/tên/metadata để ép viewer remount. Đổi file hủy request/render cũ, bỏ vùng ký cũ; trang hiện tại được giữ nếu còn hợp lệ, nếu không thì giới hạn theo số trang của file mới.

## 4. Dùng để chọn vùng ký

```tsx
import {useState} from 'react';
import {PdfViewer, type PdfGeometry} from '../../shared/ui/pdf';

const [selection, setSelection] = useState<PdfGeometry | null>(null);

<PdfViewer
  client={client}
  fileId={currentFile.id}
  title={currentFile.title}
  fileName={currentFile.fileName}
  selectable={canPlaceSignature}
  selection={selection}
  onSelectionChange={setSelection}
/>
```

`canPlaceSignature` lấy từ điều kiện quyền/bước/file hiện hành của module, không suy từ tên role. Viewer chỉ hiện công cụ khi có `selectable=true` và callback; **không cấp quyền ký**. Backend phải kiểm lại quyền trên file, assignment, version/hash, ETag và geometry khi nhận command.

```ts
type PdfGeometry = {
  page: number;  // bắt đầu từ 1
  x: number; y: number; width: number; height: number; // PDF points
};
```

Người dùng chuyển trang → chọn vị trí → kéo vùng → di chuyển/resize → module xác nhận và gửi command riêng. Xoay/zoom/resize màn hình chỉ đổi cách hiển thị; geometry giữ theo PDF points. Chuyển trang hoặc file bỏ selection; phiên bản/bước/ETag đổi thì module cũng phải bỏ xác nhận cũ theo nghiệp vụ. Read-only không có overlay hoặc nút chọn vị trí.

Không cho viewer tự upload, ký CA, retry command hoặc quyết định thời điểm chuyển người ký. Việc dùng viewer chung không triển khai chức năng ký cho module chưa có contract/backend.

## 5. Vòng đời và xử lý lỗi

- Fetch phụ thuộc client ổn định, fileId và lần retry; không phụ thuộc object cha.
- Abort request cũ, kiểm abort cả sau khi đọc bytes; không hiện response cũ dưới tên file mới.
- Cancel render cũ và chờ kết thúc trước khi dùng lại canvas; destroy loading task khi đổi file/unmount. Tương thích React StrictMode.
- PDF.js chuyển buffer sang worker; giữ bản bytes riêng cho download. Không cache toàn cục hoặc ghi bytes/base64/JWT vào storage/log.
- Lỗi tải/render hiện ngay trong viewer, có **Thử mở lại PDF**; chỉ GET lại khi người dùng chủ động.
- Lỗi vùng quá nhỏ hiện cạnh công cụ chọn; người dùng chọn lại ngay, không tải lại PDF.
- Màn hình nhỏ cuộn trong vùng PDF khi zoom; không làm tràn toàn trang. Toolbar và trạng thái dùng CSS Modules/design tokens.

| Tình huống | Xử lý |
| --- | --- |
| 401 / token bị thu hồi | AuthClient chung xử lý phiên; không tạo vòng refresh riêng |
| 403 / 404 | Không hiện bytes cũ hay mở storage URL thay thế; giữ thông báo đúng phạm vi |
| File chưa sẵn sàng / kiểm toàn vẹn lỗi / storage lỗi | Không giả thành công; cho đọc lại theo contract và lỗi trả về |
| Đổi file hoặc rời màn hình khi đang tải | Hủy request/render; không hiện lỗi giả cho file tiếp theo |
| Retry thành công | Hiện đúng PDF mới tải, không yêu cầu gửi lại lệnh nghiệp vụ |

## 6. API và quyền của module mới

Endpoint đã có: `GET /api/ctsv/tep/{idTep}/noi-dung`, không query/body; response `application/pdf`, `Cache-Control: no-store`, `Content-Disposition: attachment`. Renderer dùng bytes nên không cần đổi header thành inline.

**Dùng cùng viewer không có nghĩa mọi loại file đã được backend cấp quyền.** Module mới phải có quan hệ file–đối tượng, nguồn capability và policy đọc đúng contract. Nếu chưa có, ghi dependency và phối hợp shared/backend; không mở quyền chung hoặc tạo endpoint tải song song để chữa lỗi viewer.

Điểm đọc source:

- [FileController](../src/main/java/pbl6/ctsv/shared/files/FileController.java): JWT, route và PDF response.
- [FileService](../src/main/java/pbl6/ctsv/shared/files/FileService.java), [FileReadPolicy](../src/main/java/pbl6/ctsv/shared/files/FileReadPolicy.java): kiểm quyền và policy extension hiện hành.
- [FileMetadata](../src/main/java/pbl6/ctsv/shared/files/FileMetadata.java), [ManagedFiles](../src/main/java/pbl6/ctsv/shared/files/ManagedFiles.java): metadata và interface file backend.
- [PrivateFileStorage](../src/main/java/pbl6/ctsv/shared/files/PrivateFileStorage.java), [PdfSpool](../src/main/java/pbl6/ctsv/shared/files/PdfSpool.java): storage private và kiểm toàn vẹn/tài nguyên.

## 7. Checklist cho module tích hợp

- [ ] Lấy fileId/tên từ backend, không bắt người dùng nhập ID.
- [ ] Xem A→B→C→D và đổi nhanh, đúng tên/bytes/canvas; không giới hạn số tài liệu.
- [ ] Download khớp SHA-256 của tài liệu đang xem, không tạo GET PDF thừa.
- [ ] Metadata refresh không remount/tải PDF lại.
- [ ] Loading/error/retry/unmount và StrictMode không để stale response hoặc render cạnh tranh.
- [ ] Read-only không có công cụ ký; chế độ ký chuyển đúng page/point theo zoom/rotation/crop.
- [ ] Backend kiểm cả quyền đối tượng và URL trực tiếp; không suy quyền từ việc ẩn nút.
- [ ] Desktop/mobile xem được nội dung thực; screenshot đã được xem, canvas có nội dung chứ không chỉ tồn tại.
- [ ] Dùng ứng dụng/DB được phép, fixture đã có; không upload/ký lại cho ca chỉ kiểm viewer.
- [ ] Ghi riêng REAL/STUB và phạm vi chưa kiểm; không coi component harness là bằng chứng toàn bộ nghiệp vụ ký của một module.

## 8. Bằng chứng và chạy lại kiểm thử

[Plan và kết quả chuyển đổi](superpowers/plans/2026-11-23-shared-pdfjs.md). Unit: `node --test tests/shared-pdf.test.mjs` tại `frontend/`. Build/typecheck: `npm.cmd run build`.

Browser: `frontend/e2e/shared-pdf-login.cjs` tái sử dụng năm context DEV hiện có trên CDP loopback 9337, credential DPAPI → RAM; module Playwright/cert qua environment local, không storageState. `shared-pdf-setup.mcp.js` gắn các phiên và tạo harness. `shared-pdf-dev.mcp.js` chạy từng `page.__sharedPdf.stage`: source, selection, consumers, permissions, review-fixes. Nếu Vite reload làm mất harness, chạy setup lại trong cùng phiên RAM. Giữ context khi lỗi, sửa và chạy lại đúng stage.

`shared-pdf-harness.html/tsx` chỉ là fixture E2E, không được build thành route sản phẩm. Nó dùng PDF được backend thật cho phép đọc; chế độ chọn vùng ở harness không gọi command ký hoặc chứng minh quyền ký nghiệp vụ. Các màn hình thực được kiểm riêng trong stage consumers/permissions.
