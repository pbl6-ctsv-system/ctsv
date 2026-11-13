# 04 — Kiến trúc và cách tiếp tục phát triển

[Mục lục](README.md) · [Auth/Foundation](05-auth-and-foundation-guide.md) · [Sync](06-sis-ctsv-sync-guide.md)

## Hệ thống đang có trong source

```text
React CTSV (một frontend, feature modules)
  HTTPS /api -> Spring Boot CTSV (một application)
                 auth | foundation | shared | mghp/foundation adapter đọc
                 datasource + transaction manager + Flyway CTSV chung
                               |
                          CTSV database

CTSV Foundation sync client -- GET + X-Service-Key --> SIS Spring Boot
                                                        |
                                                   SIS database
                                              SNAPSHOT + Change Tracking

Kế toán Spring Boot độc lập: mới có starter P00; tích hợp nghiệp vụ ở giai đoạn sau.
```

Ba repo độc lập trong cùng Organization, không có repo cha/monorepo và không có Auth service/DB riêng. Cùng CTSV DB không cho phép mọi module ghi bảng của nhau. Tên DB logic thiết kế `ctsv_db`; đích local launcher hiện `ctsv_dev`. Test/evidence DB dùng tên riêng có guard, không mặc định vận hành ở đó.

Nguồn chuẩn: [kiến trúc đã chốt](../../../nghiepvu/01-thiet-ke/kien-truc-va-quy-tac-cong-tac.md), [backlog](../../../nghiepvu/09-trien-khai/master-implementation-backlog.md). Tài liệu cũ trong `nghiepvu` có đoạn trước implementation nói chưa có FE/Auth migration; lấy trạng thái mới ở P03/P04 execution và source, không coi đoạn lịch sử là blocker nghiệm thu mới.

## Ownership code và data

| Thành phần | Có trong source | Sử dụng / giới hạn |
|---|---|---|
| `auth` |13route, JWT RS256/refresh/admin/audit/bootstrap | Gọi public `AuthAccess`; không join Auth repository hoặc tự làm JWT |
| `foundation/units` | Units client/map/store/guard, baseline preview/confirm | Là sync source identity độc lập local PK; không tạo nguồn mới bằng suy đoán |
| `foundation/sync` | CT26 registry, durable page/seen/presence/fencing/checkpoint |23binding đã kiểm P02; một resource mỗi runner; không có scheduler/UI sync chung |
| `foundation/web` |12route truy vấn/xác minh/local permissions | `FoundationService` hiện package-private; không công bố nó là public data service cho module mới |
| `foundation/api` | `FoundationAccess`, `FoundationUsage` public | Quyền/identity và usage port; **không có sẵn generic lookup/data projection interface đầy đủ** |
| `shared/audit` | `AuditWriter` | Dùng theo transaction đã thiết kế; không đưa logic nghiệp vụ vào Shared |
| `mghp/foundation` | `MghpFoundationUsage` adapter chỉ đọc dữ kiện được dùng | Chặn sửa căn cứ đã đóng băng; không phải implementation đánh giá/QĐ MGHP |
| `frontend/src/shared` | API/Auth client, memory JWT/Web Locks/BroadcastChannel | Dùng lại client; không lưu token ở localStorage hoặc dựng Auth mỗi feature |

Modules SHCN/DRL/Học bổng do các thành viên khác phát triển theo phân công; bộ này chỉ xác định ranh giới. Không có skeleton/API/bảng/UI của chúng được bàn giao. Các class/history dữ liệu nguồn như phân lớp rèn luyện ở Foundation không đồng nghĩa đã code nghiệp vụ DRL.

## Tích hợp một module của thành viên

1. Package theo module trước, layer sau; xác định nhu cầu dữ liệu/quyền được giao, không dùng MGHP role cho module khác chỉ để vượt403.
2. API đọc Bearer và gọi `AuthAccess.verifyAccessToken`. Claim chỉ có danh tính/issuer/tokenVersion, không tự suy quyền từ tên đăng nhập, email hoặc nhãn cán bộ.
3. Khi cần fresh state, gọi `requireCtsvAccess(principal, mappedAccountId)` với account ID từ mapping tin cậy. Backend cùng process không self-HTTP AUTH11. Quyền dữ liệu/local capability phải kiểm riêng trong transaction.
4. `FoundationAccess.authorize(principal, online, capability)` trả local user/student; cần transaction đang mở. Implementation hiện chứa các role/policy P04/MGHP. Không giả định thêm capability khác tự được hỗ trợ; phối hợp thay hợp đồng chung bằng task riêng trước khi sử dụng cho module mới.
5. Đọc dữ liệu nền tảng qua public projection/application interface được thỏa thuận; hiện cần bổ sung port nếu backend module khác cần dữ liệu mà chỉ có API web/package-private service. Frontend có thể gọi API hiện hữu khi phù hợp quyền; không viết vòng HTTP từ backend vào chính nó hay import repository nội bộ để chữa thiếu port.
6. Dữ kiện học kỳ/quá trình đã được nghiệp vụ đóng băng cần usage port/root lock theo contract. Adapter có fixture bảo vệ không chứng minh transaction nghiệp vụ P08/P10 đã triển khai.
7. Thay Shared/Auth/Foundation/migration chung phải nêu ảnh hưởng và phối hợp trước tích hợp; không tự sửa module người khác để làm test xanh.

## Database / migration

- CTSV: một chuỗi Flyway V1–V7 hiện có, không mỗi module bắt đầu V1. SIS: lịch sử riêng V1–V5 và file `.sql.conf` của V2; Kế toán chưa có migrations runtime trong repo.
- Migration đã áp dụng bất biến. Xem history/checksum và thống nhất version trước tích hợp; sửa tiếp bằng migration mới sau khi được giao, không sửa baseline/ERD vì tiện dev.
- Start dev tắt Flyway/Hibernate DDL write, JPA `validate`; schema thay đổi chỉ qua thao tác explicit được duyệt. Không dùng ddl-auto update/create để lấp bảng thiếu.
- ID Auth(UUID), CTSV user/student(bigint), SIS PK, source enrollment code và CA identity không đồng nhất. Mapping issuer/sub verified khác với metadata source sync; bản ghi SIS không tự sinh quyền đăng nhập.

## Quy chuẩn cần nhận cùng source ở vòng chia sẻ

| Tài liệu | Cách dùng |
|---|---|
| [API Conventions](../../../nghiepvu/01-thiet-ke/api-conventions.md) | URL tiếng Việt, JSON/header/status/error/version theo contract đã chốt |
| [Foundation contract](../../../nghiepvu/01-thiet-ke/ctsv/api-contracts/ctsv-foundation-api.md) | Projection, ownership, ETag, quyền và validation |
| [Auth contract](../../../nghiepvu/01-thiet-ke/auth/api-contracts/auth-api.md) | Login/refresh/logout/admin/AUTH11 |
| [Testing Strategy](../../../nghiepvu/08-kiem-thu/chung/testing-strategy.md) | Chọn loại test, synthetic fixture/dev/test riêng |
| [Quality Gates](../../../nghiepvu/08-kiem-thu/chung/quality-gates.md) | Gate theo thay đổi, SQL/HTTP thật và evidence |
| [Test Case Standard](../../../nghiepvu/08-kiem-thu/chung/test-case-standard.md) | Expected/Actual/source/evidence, không báo PASS giả |

Các link `nghiepvu` tồn tại trên workspace audit nhưng chưa có kênh Git đã xác minh. Owner cần đóng gói đúng nguồn đã duyệt; không bắt người mới tìm hết backlog để chạy UI. Git workflow feature branch, review linh hoạt; không yêu cầu CI/PR để chạy local. Không tự commit/push/merge hoặc triển khai module ngoài phần được giao.
