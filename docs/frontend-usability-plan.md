# Cải thiện thao tác frontend CTSV — 18/11/2026

Theo yêu cầu của chủ dự án, chỉnh frontend MGHP, chuyên viên và quản trị Auth hiện có. Kế thừa palette và Navbar. Dữ liệu và hợp đồng API hiện hành là nguồn chuẩn.

- [x] Danh sách và chi tiết MGHP tách theo tác vụ. Form kê khai chia phần, nút nộp rõ ràng. Mã kỹ thuật và thao tác rút/gia hạn chỉ mở khi cần.
- [x] Lỗi luôn thấy trong viewport tới khi đóng hoặc sửa. Feedback dùng chung cho MGHP/Auth/Foundation, giữ xử lý ETag hiện tại.
- [x] Cấu hình policy/đợt chia nhóm. Quản trị có chế độ xem/tạo/sửa riêng. Bảo toàn các chức năng và quyền hiện có.
- [x] Build/typecheck/Node test và Playwright DEV gồm nộp/bổ sung/kiểm giấy, cấu hình, admin, lỗi sau cuộn và mobile 360px. Dùng PDF mẫu đã được chỉ định.

Phạm vi file gồm features MGHP/Auth/Foundation, shared UI/styles, provider trong App.tsx, E2E và frontend/SKILL.md. Không thêm thư viện. Shared feedback phục vụ các màn hiện có. Tên thân thiện cho mã DEV tự sinh là cách trình bày, giữ mã đối chiếu và dữ liệu request. Bằng chứng ghi dịch vụ REAL/STUB và lỗi thực tế.

## Phạm vi bàn giao

| Nhóm | File chính | Thay đổi |
| --- | --- | --- |
| Hồ sơ sinh viên/chuyên viên | `frontend/src/features/mghp/intake/{IntakeWorkspace,DeclarationForm,SupplementWorkspace}.tsx`, CSS cùng thư mục | Tách danh sách/chi tiết, kê khai ba phần, lịch sử, nhóm bổ sung và kiểm giấy |
| Chính sách và đợt | `frontend/src/features/mghp/configuration/ConfigurationWorkspace.tsx`, CSS, `StudentRoundDetails.tsx` | Danh sách/phiên bản/form riêng; cấu hình theo nhóm; bản khóa đọc như thông tin |
| Quản trị và dữ liệu nền | `frontend/src/features/auth/pages/AuthAdminPage.*`, `features/foundation/pages/FoundationWorkspace.*` | Chọn tác vụ thay vì mở tất cả editor; thông tin gọn hơn |
| UI dùng chung | `frontend/src/shared/ui/{Feedback,Alert,Button,RecordFields,RecordView}.*`, `Controls.module.css`, `app/App.tsx` | Lỗi cố định trong viewport, loại nút, bỏ card lồng, thu gọn danh sách con; sửa implicit submit của nút thêm/bỏ dòng |
| Tên và dữ liệu mẫu | `frontend/src/features/mghp/presentation.mjs` | Nhãn dễ đọc cho mẫu nhận diện được; giữ nguyên ID, dữ liệu nguồn và payload |
| Quy chuẩn và kiểm chứng | `frontend/SKILL.md` §11, `frontend/README.md`, `frontend/tests/mghp-presentation.test.mjs`, hai runner DEV | Quy tắc thao tác, test nhãn và browser thật |

## Kiểm chứng

Chạy từ root CTSV:

```powershell
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend test
node frontend/e2e/p07-dev.mjs
node frontend/e2e/ui-workspaces-dev.mjs
```

Hai lệnh Node cuối cần môi trường private DEV theo `frontend/README.md`; chúng tạo dữ liệu synthetic qua UI/API trên `ctsv_dev`. Không chạy trên DB khác hoặc đưa cấu hình DPAPI/password vào log. Browser Chrome, HTTPS frontend/backend thật, SQL Server thật, Cloudinary thật cho upload/download. Không dùng mock để thay kết quả integration.

Build/typecheck PASS; 19/19 test Node PASS, 0 fail/skip. Nhóm Node là kiểm helper/model/Auth client mô phỏng, không là bằng chứng SQL hoặc browser. Luồng hồ sơ 4/4 PASS tại `target/ui-usability-20261118-verified/browser-report.json`, gồm nộp, phản hồi, kiểm giấy, download đúng SHA-256, rút sau xử lý ETag stale và logout. PDF mẫu `du lieu test.pdf`, 1.625.263 byte, SHA-256 gốc/tải về đều `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`.

Các lượt điều chỉnh test trước đó đã gặp selector trùng, nhầm bản thông báo stale giữa hai màn và lỗi đọc response body không cần thiết của Chrome. Đã sửa test, giữ assertion HTTP và trạng thái nghiệp vụ. HS06 stale vẫn phải trả 412; người dùng đọc lại trước khi gửi với ETag mới. Không đổi backend/contract để làm test xanh. Khi Auth trả 429, runner chờ `Retry-After`, không vô hiệu hóa giới hạn.

Hash `src/main`/cấu hình backend và POM theo helper DEV vẫn trùng JAR đang chạy: `B6748B0F8BF9049BD1D4F81DB6B42E863CD83944A34D6A18DB744C52CD2E1ECD`. Các thay đổi backend/P07 đã có trong working tree trước nhiệm vụ này được giữ nguyên. Không chạy migration hoặc backend suite trong nhiệm vụ UI.

## Kết quả cuối

### Bổ sung: tách menu hồ sơ theo tác vụ ngày 18/11/2026

Nguồn: phản hồi chủ dự án về menu chuyên viên; HS02/HS03-D/V tại `nghiepvu/01-thiet-ke/ctsv/api-contracts/mghp-application-api.md` và BS01–BS07 tại `submission-supplement-api.md` đã duyệt. Chuyên viên MGHP được đọc hồ sơ toàn trường, tiếp nhận/bổ sung/kiểm giấy; nộp/rút cá nhân thuộc sinh viên. Lỗi FE là gộp hai tác vụ vào cùng một entry/tên “Hồ sơ MGHP”, khiến người dùng hiểu nhầm và tài khoản có cả hai capability chỉ vào được view sinh viên.

Đã tách entry sinh viên `/mghp/ho-so` (`applications`) và entry chuyên viên `/mghp/tiep-nhan-ho-so` (`staff-applications`, tên “Tiếp nhận & bổ sung hồ sơ”). Dùng capability hiện có, giữ component/backend/API. Chuyên viên thuần bị chặn route sinh viên, sinh viên bị chặn route tiếp nhận; Auth-admin không tự có quyền cả hai. Header chuyên viên đổi cùng tên tác vụ. Đồng bộ helper E2E liên quan, không chạy lại bộ P06/P07.

Kiểm chứng: navigation test RED 4/8 trước sửa → GREEN 8/8 sau sửa; toàn frontend 19/19 PASS, build/typecheck PASS. `frontend/e2e/mghp-navigation-access-dev.mjs` chạy 3/3 PASS trên HTTPS launcher DEV → backend/SQL Server `ctsv_dev` thật: chuyên viên vào màn tiếp nhận/kiểm giấy và refresh, sinh viên giữ màn nộp cá nhân, admin không có cả hai; URL trực tiếp bị chặn theo capability, 0 pageErrors. Evidence `target/mghp-navigation-access-20261118/report.json`. Chỉ đọc hồ sơ synthetic sẵn có, không ghi dữ liệu nghiệp vụ. Tổ hợp nhiều capability được kiểm ở navigation unit, chưa tạo tài khoản đa quyền mới để kiểm browser. Gate G0/G7 và kiểm hiển thị/guard áp dụng; không sửa cơ chế AuthZ backend hoặc mở lại nghiệm thu P07/G8.

Tái chạy từ root CTSV: `node frontend/e2e/mghp-navigation-access-dev.mjs`, với `CTSV_DEV_HOME`, `CTSV_DEV_SMOKE_PASSWORDS`, `NODE_EXTRA_CA_CERTS` như runner DEV trên; thêm `CTSV_NAV_EVIDENCE` trỏ thư mục output. Cần ít nhất một hồ sơ synthetic đã nộp trong DEV cho bước mở chi tiết/kiểm giấy. Không dùng runner này để ghi fixture hoặc thay quyền tài khoản.

| Kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Build/typecheck | PASS: 75 module, exit 0 | `npm.cmd --prefix frontend run build` |
| Node tests | 19 PASS, 0 FAIL, 0 SKIP | `npm.cmd --prefix frontend test` |
| Hồ sơ trên DEV | 4/4 PASS | `target/ui-usability-20261118-verified/browser-report.json` |
| Quản trị/chính sách/đợt/feedback/quyền | 5/5 PASS | `target/ui-workspaces-20261118-verified/ui-report.json` |
| Màn đợt chỉ đọc sau chỉnh trình bày | 1/1 PASS | `target/ui-workspaces-20261118-verified/readonly-report.json` |

10 kịch bản browser dùng Chrome 157, FE HTTPS 5173 → BE HTTPS 8443 → SQL Server `localhost\DEV2022/ctsv_dev`. Không route interception/STUB trong các runner này; PDF dùng Cloudinary REAL. Hai report chính ghi 0 `pageErrors`. Có ảnh desktop và mobile 360px cùng thư mục report, đã mở kiểm trực quan. Thực hiện build trước browser smoke; tránh chủ động thay source/build trong khi đang chạy tương tác browser.

Luồng đã kiểm: login/trang chủ/logout; giữ lời khai qua các phần; upload/nộp/bổ sung/đọc lịch sử/tải PDF/kiểm giấy/rút; thông báo mở đúng yêu cầu; stale 412 và đọc lại; admin tạo/đọc/sửa liên kết/khóa/mở khóa; chính sách tạo/sửa/xác nhận và nhãn danh sách cập nhật; đợt tạo/sửa/mở/đóng/hoàn tất; tra cứu Foundation; tài khoản thiếu quyền bị chặn URL quản trị/hồ sơ; lỗi 404 hiện trong viewport và đóng được; reflow mobile.

Lỗi FE đã chứng minh và sửa riêng: `RecordFields.tsx` có hai nút thêm/bỏ liên kết thiếu `type="button"`. Browser kiểm RED trước sửa tại `target/ui-workspaces-20261118-red-button/ui-report.json`, GREEN trong UI01 sau sửa. Không sửa Auth hoặc bỏ kiểm tra request để hợp thức hóa.

Hồ sơ mẫu cuối `10010` đã rút, vòng bổ sung `9` đã hoàn tất; có thể xem lịch sử/tài liệu bằng tài khoản DEV sinh viên hoặc chuyên viên. Chính sách M9 có phiên bản thực hành `10010` đã xác nhận; đợt thực hành `20015` đã hoàn tất. Đây là dữ liệu synthetic tạo mới qua API/UI hiện có, không sửa chính sách pháp lý hoặc xóa/reset dữ liệu cũ. Tên đợt/chính sách mới là tên nghiệp vụ dễ đọc, mã chạy chỉ để đối chiếu.

Giới hạn: API Auth hiện có tra cứu tài khoản bằng UUID, chưa có API danh sách tài khoản; giữ cách tra cứu đó và không tạo endpoint mới. Danh mục kê khai gợi ý từ trang đầu tối đa 100 mục theo API hiện có, vẫn cho nhập mã chuẩn ngoài trang này. Chưa kiểm mọi tổ hợp chính sách/phân quyền, thao tác cấp/thu hồi từng vai trò hoặc đặt lại mật khẩu trong lượt smoke này, hay mọi trình duyệt; không thay kết quả nghiệm thu lịch sử P00–P07.

## PRE-P10-01 — lịch đợt, Gate A owner approved / Gate B triển khai

Nguồn [Owner timeline](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#timeline-20261120), [Detailed Plan §4/7/8](../../nghiepvu/09-trien-khai/pre-p10-timeline-implementation-plan.md). Đã thêm RoundTimelinePanel và PlannedFields trong feature cấu hình hiện có; không đổi router/navbar/Auth. Browser4luồng PASS trên DB timeline riêng, Chrome HTTPS có kiểm TLS, gồm clock browser America/New_York nhưng nhập/hiển thị giờ Việt Nam. [Kết quả/giới hạn](../../nghiepvu/09-trien-khai/pre-p10-timeline-implementation-plan.md#11-kết-quả-gate-b).

Dùng trang tạo/quản lý đợt hiện có trong MGHP/AppShell, không Navbar/router/calendar riêng. Nhóm rõ Lịch tiếp nhận; Bổ sung & rà soát; Hội đồng; Ký & công bố. Hiển thị planned khác actual, múi giờ Việt Nam Asia/Ho_Chi_Minh, legacy Chưa lập lịch; tiến độ lấy server asOf, không tự suy quyền bằng Date.now.

CV có capability mới thấy Sửa lịch/Gia hạn/Đóng xét; admin Auth không tự có MGHP, SV chỉ projection/hạn vòng của mình; PHT/PP assignment UI ở P11. Gia hạn là dialog/form hẹp kèm old/new/reason, không unlock cấu hình; đóng xét có preflight counts/blockers/readback và xác nhận, không chọn winner ở FE.

Error toast nhìn thấy khi cuộn + lỗi gần field/action, loading và ngăn gửi đôi, giữ input khi stale và yêu cầu GET mới. Sau mutation đọc lại detail/list/history liên quan; không nhãn cũ sau thành công. Review/sign/publication overdue là cảnh báo mềm, không tự tắt hành động hợp lệ. Browser test role/direct URL/stale/mobile/timezone dùng BE+DB thật sau Gate B, không nhận build là UI PASS.
