# Sửa lỗi migration SHCN — collation

Ngày21/11/2026. Log owner cung cấp ghi migration SHCN cũ V17 thất bại và rollback. Sau merge, file SHCN là `V18__shcn_draft_round.sql`; V17 hiện là `V17__drl_schema.sql` của module khác. Chỉ sửa V18 chưa áp dụng, không sửa migration đã có trong DB hoặc V17 của DRL.

## Nguyên nhân đã tái hiện

SQL Server báo:

```text
Cannot resolve the collation conflict between "Vietnamese_100_CI_AS_SC"
and "Latin1_General_100_BIN2" in the equal to operation.
```

`dbo.ChucNang.MaChucNang` dùng BIN2. Cột `code` trong bảng biến `@p` chưa khai báo collation nên dùng collation mặc định DB. Phép `c.MaChucNang IN (SELECT code FROM @p)` gây xung đột. Parse-only không phát hiện được lỗi này; kiểm biên dịch NOEXEC đã tái hiện được.

## Sửa source

```sql
DECLARE @p TABLE(
  code nvarchar(128) COLLATE Latin1_General_100_BIN2,
  label nvarchar(512)
);
```

Không đổi tên/bảng/field/quyền, không đổi collation DB, không thay cấu trúc Auth/Foundation hoặc mapping Entity. Không repair Flyway, xóa history, reset DB hay seed account.

## Kiểm chứng thực hiện

- SELECT xác nhận `DEV2022 / ctsv_dev`; lịch sử thành công vẫn tới V16, chưa có bảng đợt SHCN sau rollback.
- Biên dịch batch permission trước sửa với `SET NOEXEC ON`: tái hiện lỗi collation.
- Biên dịch batch permission sau sửa: **PASS**, không thực thi INSERT.
- Biên dịch phần CREATE TABLE sau sửa bằng NOEXEC: **PASS**, không tạo bảng.
- Checker tĩnh API/ERD/Entity/DDL: **PASS**, đủ27field.
- **Chưa chạy lại migrate**, chưa xác minh thực thi trigger hoặc luồng DEV thật. Các kết quả unit/browser ở báo cáo trước vẫn là kết quả cho phạm vi mock/fixture đã ghi, không đổi thành SQL integration PASS.

## Owner chạy tiếp khi sẵn sàng

Ở root `ctsv`:

```powershell
.\dev.ps1 migrate
.\dev.ps1 start
```

Launcher migrate tự build source trước khi áp dụng. Do repo vừa merge, lệnh này sẽ xử lý toàn bộ migration pending theo thứ tự, hiện có V17 DRL và V18 SHCN; không phải lệnh chỉ chạy riêng một file. Không cần chạy setup hoặc Flyway repair.
