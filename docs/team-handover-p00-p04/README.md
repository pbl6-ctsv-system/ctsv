# Bàn giao CTSV/SIS P00–P04

**Persistence13/11 — đọc trước khi bổ sung model:** [Entity/Repository và migration](../persistence.md), [inventory Entity–bảng–FK–owner](../persistence-entity-inventory.md). Foundation/P05 dùng Entity cho luồng nghiệp vụ; Flyway/schema, Auth JDBC và sync engine giữ nguyên. Xem branch/commit Git để xác định source đã phát hành; JAR DEV chưa được thay sang bản refactor. Các trạng thái release lịch sử bên dưới không thay bằng chứng Git hiện hành.

**Cập nhật B01–B03: 13/11/2026.** Điểm bắt đầu cho thành viên và Codex của họ.

| Mục | Trạng thái |
|---|---|
| B01 — source phát hành | **READY TO COMMIT**; chờ owner review, chưa commit/push, clone sạch NOT VERIFIED. Xem [08](08-git-push-safety-audit.md). |
| B02 — credentials DEV | **RESOLVED BY OWNER DECISION — DEV SYNTHETIC CREDENTIAL RISK ACCEPTED**. Chỉ preset synthetic trong private repo/DEV. |
| B03 — setup SIS → CTSV | Đã có công cụ thực: [dev-sync.ps1](../../dev-sync.ps1). Kết quả thực thi và giới hạn ở [10](10-handover-review-report.md). |

HEAD hiện vẫn là starter; chỉ clone HEAD cũ chưa nhận đủ implementation. Không copy private keys/config hoặc DB evidence của tác giả để bù source thiếu.

## Thứ tự onboarding

1. [01 — Prerequisites](01-prerequisites.md): JDK21, Boot3.5.16, Maven3.9.16, Node/npm/PowerShell, SQL2022 DEV2022 và auth DLL.
2. [02 — Installation](02-installation-guide.md): nhận đủ source → tải dependency → setup CTSV → kiểm/trust leaf của chính máy mình.
3. [03 — Daily operations](03-daily-operations.md): `dev.ps1 start/stop/status`; FE https://127.0.0.1:5173, BE https://127.0.0.1:8443.
4. [05 — Auth/Foundation](05-auth-and-foundation-guide.md): thử ba vai trò synthetic.
5. [06 — SIS sync](06-sis-ctsv-sync-guide.md): setup riêng → start provider → Full lần đầu → Incremental các lần sau.
6. [09 — Onboarding checklist](09-onboarding-checklist.md): tự ghi evidence trên máy thành viên. PASS máy tác giả không thay PASS máy mới.

## Nguồn và giới hạn

| Tài liệu | Mục đích |
|---|---|
| [04 — Architecture](04-architecture-and-integration.md) | Ownership, public interfaces và cách tích hợp module |
| [07 — Troubleshooting](07-troubleshooting.md) | Lỗi môi trường, Auth, sync và recovery |
| [08 — Git release safety](08-git-push-safety-audit.md) | Nhóm source cần chia sẻ, ngoại lệ và quy trình Git vòng sau |
| [10 — Review report](10-handover-review-report.md) | B01–B03 trước/sau, evidence, readiness và việc còn lại |

P00–P04 giữ trạng thái nghiệm thu đã chốt; P04 **ACCEPTED WITH OWNER WAIVER**, G8 **WAIVED**, không phải PASS. Không có P05 trong lượt này. P01 units và P02 23 binding khác đã có implementation; Staff/CVHT còn gated, không hứa cả26 resource đã chạy.

Mỗi máy tạo riêng TLS/JWT keys, SIS service key, source epoch và config ngoài Git. `dev.ps1` giữ sync OFF. `dev-sync.ps1` chỉ chạy khi developer chủ động gọi; không thêm scheduler/API/UI mới. Runtime không cần thư mục `nghiepvu` của tác giả vì schemas/catalogs đã nằm trong resources; khi phát triển contract-first, nhận thêm bộ tài liệu đã duyệt theo [04](04-architecture-and-integration.md).
