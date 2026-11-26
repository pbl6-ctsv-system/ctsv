# P11-07 — persistence và vận hành hỗ trợ ký số

Nguồn: [plan](../../nghiepvu/09-trien-khai/signing-admin-support-implementation-plan.md), [contract](../../nghiepvu/01-thiet-ke/ctsv/api-contracts/signing-support-api.md). Delta riêng vì inventory chung đang có conflict của công việc trước; không resolve hoặc ghi đè inventory đó.

V32 additive, không sửa migration lịch sử, request/receipt/artifact cũ hoặc tự grant tài khoản. `ddl-auto=validate`, OSIV off, scalar FK và rowversion/CAS theo persistence hiện hành. Không lưu PDF bytes trong SQL.

| Bảng | Entity | Owner / bất biến |
|---|---|---|
| mghp_su_co_ky | SigningSupportEntities.Incident | MGHP; unique attempt; snapshot whitelist; waiting/working/resolved; rowversion |
| mghp_su_co_ky_lich_su | IncidentHistory | MGHP; event UUID unique; append-only trigger; notes/actor/time |
| mghp_theo_doi_chung_thu | CertificateMonitor | MGHP; email cấu hình unique; last-good observation JSON; rowversion |
| mghp_chung_thu_lich_su | CertificateHistory | MGHP; append-only observations/notes; không sửa lịch sử signer cũ |
| mghp_yeu_cau_gia_han | Renewal | MGHP; operation UUID; một intent/pending/unknown mở trên mỗi monitor; CA request ID nullable |

`mghp_quan_tri_ky` là grant nghiệp vụ global MGHP; capability `mghpSigningAdmin`. Migration chỉ tạo role, DEV grant `dev.admin` thực hiện riêng theo owner. Không cho ký thay hoặc đọc mọi PDF. Menu Foundation chỉ mở đúng capability.

## Disk spool

Property `ctsv.mghp.signing.result-spool-dir`; mặc định `~/.ctsv/signing-results` của tài khoản chạy backend. Deploy đặt vào ổ đĩa bền riêng có ACL chỉ service/Admin, không webroot, không ephemeral container filesystem. Không dùng chung root giữa DEV/test/production. SQL test đặt root riêng theo PID; không đọc/xóa spool DEV.

Tên file UUID operation: `.json` manifest whitelist/context/receipt/checkpoint; `.pdf` bytes; `.verified` proof chỉ ghi sau kiểm hash/revision/crypto. Atomic temp+fsync+move; filesystem Windows không hỗ trợ directory fsync thì giữ atomic replace. Không lưu access/refresh token, client secret, signed delivery URL hay raw exception body.

1. Ghi receipt vào disk trước SQL receipt; tải output vào disk trước verify/cloud.
2. Chỉ normal output chưa từng lỗi được tự dọn sau cloud+SQL commit. `hadError` giữ qua restart/complete cho đến Admin xóa.
3. Disk lỗi vẫn cố ghi incident vào SQL; SQL lỗi còn manifest để replay. Nếu cả disk và SQL đều không ghi được, không thể hứa có evidence bền; cần xử lý hạ tầng và server log an toàn.
4. `hadError` là stop fence trước worker claim/network. Replay chỉ dừng attempt chưa completed và nhập incident; không gọi CA/cloud hay phục hồi nghiệp vụ. Không hạ completed khi SQL đã commit thành công trước lỗi kết nối.
5. Xóa local: audit intent có actor/operation commit trước unlink, sau đó manifest tombstone và audit hoàn tất. Nếu SQL intent lỗi thì giữ PDF. Nếu audit hoàn tất lỗi sau unlink, intent vẫn cho biết actor; Admin đối chiếu tombstone. Không giả định SQL rollback khôi phục được filesystem. Xóa ngoài hệ thống do Admin không thể bị chức năng này kiểm soát.

## Chứng thư và renew

Startup async + mỗi24giờ + thủ công; thực tế CA06 `status=active`; ngày hết hạn lấy từ CA. Lỗi transport giữ last-good/checkFailed, CA trả active list rỗng là quan sát hợp lệ `not_found`, không đoán expired. Dưới10ngày là `soon`, đúng10ngày còn `valid`.

CA04 renew ngoài SQL transaction sau intent; CA05 pending trước gửi; CA05 approved/rejected và CA06 cập nhật sau. Pending/unknown giữ intent mở, không tự POST lại. Approved mà vẫn cert cũ thì chưa sẵn sàng, không gửi thêm renew trước khi đọc cert thay thế. Nếu POST mất response hoặc SQL chưa lưu được request ID, `Kiểm tra kết quả` không tự gán một request bất kỳ: Admin cần đối chiếu CA/SQL bên ngoài. Không dùng `active` cho CA05; đó là filter CA06.

## DEV/test

- DB test `ctsv_signing_support_test_20261125`, localhost\DEV2022, marker `ctsv-signing-support-20261125`; không clean/reset DB khác.
- DEV: `Protect-Dev.ps1 backup` → launcher stop/build/migrate → `verify` → grant đã duyệt → fixture idempotent → launcher start. Không chạy lại backup để ghi đè baseline.
- Fixture `SUPPORT-DEV-20261125` chỉ demo hỗ trợ: failed request và incident mô phỏng, không CA/biên nhận/chữ ký giả. Shell đợt synthetic completed để không chặn đợt nghiệp vụ thật; không tính là evidence hoàn tất MGHP. PDF chờ synthetic chưa verify được dùng kiểm xóa.
- Backup/validation/fingerprint/screenshot và sanitized test results ở `target/signing-support-dev`; không commit dữ liệu bí mật, browser storage state hoặc trace có Authorization.
- Rollback vận hành: dừng service/không gửi renew mới, giữ spool và DB evidence; không DROP bảng V32 hay xóa history. Sửa forward sau đối chiếu, restore backup chỉ theo phê duyệt riêng khi cần.
