# Fixtures Slice A — CTSV

Nguồn: nghiepvu/08-kiem-thu/daotao/slice-a-test-cases.md (F0–F9,66SA). **SYNTHETIC**, không CSV/PII.

| Fixture | Test / phạm vi | Setup và cleanup |
|---|---|---|
| F0/F2 | UnitSyncRepositoryIT, UnitSyncRecoveryIT | Source he_thong_ngoai riêng theo P01-REPO-/P01-SYNC-UUID. Local PK do IDENTITY, không gán bằng SIS. |
| F2 FK/history | UnitSyncRepositoryIT | Child don_vi_cha_id + audit typed bất biến. Delete nguồn chỉ inactive; cleanup child/projection do ca tạo, giữ registry/audit/run history. |
| F3/F4/F6 | UnitSyncRecoveryIT | SQL Server thật; EXTERNAL_SIS=STUB cho lỗi/metadata/epoch/replay/bound10001. Không là bằng chứng retention nguồn thật. |
| F5 | UnitSyncRecoveryIT, UnitSyncGuardIT | Hai trigger có latch; cùng physical connection giữ applock và writes; test close connection, không KILL session người khác. |
| Live F0/F1/F3/F4 | UnitSyncHttpIT + ForkedUnitSyncWorker | Hai Spring Boot backend + HTTP + hai DB thật. Child JVM halt86 tại barrier, không dừng process người dùng. SIS mã P01-LIVE-UUID. |
| F7/F8 | UnitFaultRecoveryFT — SQL + HTTP SIS thật | Được phép12/11: chỉ `p01_sis_fault_20261111`, guard owner/marker/instance trước thao tác. MIN_VALID thực sau retention cleanup; backup/restore/reset/reuse-ID thật; checkpoint/projection giữ khi lỗi, replay và scoped confirmation có audit. |
| Fault readiness / F0 | UnitFaultReadinessFT, UnitFaultProjectionFT | Bảy biến thể nguồn thiếu điều kiện, OFFLINE thật và đủ11mã lỗi HTTP; sáu bảng projection fixture cùng DB fault, không migrate CTSV sản phẩm vào đây. |
| G8 smoke | UnitFaultG8SmokeFT | Hai JAR Spring Boot thật, full/delta/replay/delete trên fault DB; tự kiểm kỹ thuật, chưa là G8 độc lập. |
| Manual | setup.sql / cleanup.sql | Registry P01-MANUAL-SIS, guard exact test DB+DEV2022. Cleanup chỉ projection của source đó; không xóa lịch sử bất biến. |

P00SqlTarget/SliceATestDatabaseSupport kiểm profile test, port, instance DEV2022 và DB trước ghi.
Không cleanup toàn DB, không xóa audit/dot_dong_bo/he_thong_ngoai để tạo trạng thái giả.
Live test cần SIS JAR vừa build tại sibling daotao/target; giữ source hash với XML/evidence.
Manifest66SA và hướng dẫn chạy giữ trong nghiepvu/09-trien-khai/slice-a-execution-report.md.

Lượt fault hiện tại **không chạy các IT/manual fixture ghi hai DB gốc**. Chọn rõ `*FT` với `-Dp01.fault.enabled=true`, profile test, port hiện hành; không chạy verify mặc định để lách phạm vi an toàn. FT dùng SQL thật/HTTP thật nhưng **một DB vật lý**, source `DonVi` và target `don_vi` tách bảng; UnitFaultG8SmokeFT chạy hai backend process, recovery gọi production consumer service trong JUnit. Fault hooks gây lỗi có chủ đích, không STUB response SIS. FaultDatabaseGuardTest là UNIT/MOCK riêng.

Fingerprint read-only trước/sau: `protected-databases-fingerprint.sql`, gồm từng bảng ứng dụng và Flyway history. Runbook độc lập: nghiepvu/09-trien-khai/slice-a-g8-runbook.md. Không drop fault DB/backup hoặc xóa audit/history sau kiểm; giữ bằng chứng để review.
