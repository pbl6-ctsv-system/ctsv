# Entity, Repository và migration CTSV

Ngày 13/11/2026, owner giao refactor Foundation và persistence MGHP P05 theo hướng Entity → Repository → Service → DTO/Controller, giữ schema và Flyway. [Inventory Entity–bảng–FK–owner](persistence-entity-inventory.md) là nơi tra cứu các class cụ thể. [Kế hoạch/kết quả](../../nghiepvu/09-trien-khai/foundation-jpa-refactor-plan.md) ghi bằng chứng của đợt refactor.

## 1. Phạm vi hiện tại

- 24 Entity resource Foundation: `DonVi` và 23 bảng đã có production binding; thêm 7 Entity hỗ trợ: `HeThongNgoai`, `QuaTrinhTheoHoc`, `LichSuTrangThaiSv`, `QuaTrinhThuocLop`, `NguoiDung`, `VaiTro`, `PhanQuyenNguoiDung`.
- 7 Entity MGHP P05: chính sách, phiên bản, tiêu chí, nhóm giấy, giấy bắt buộc, đợt xét, chính sách của đợt.
- `foundation/entity/` sở hữu các Entity nền tảng; `DonVi` giữ đường dẫn `foundation/units/entity/` để không làm gãy tham chiếu cũ. `mghp/configuration/entity/` chỉ sở hữu dữ liệu P05.
- Auth vẫn là module JDBC đã triển khai; không tạo Entity cho 8 bảng Auth. `NguoiDung`/`VaiTro` của Foundation không phải `TaiKhoan`/`VaiTro` của Auth. Cột mật khẩu legacy của `nguoi_dung` không phải nguồn credentials đăng nhập.
- Bảng kỹ thuật CT/staging/checkpoint, file/audit/notification và các nghiệp vụ chưa triển khai không được tạo Entity máy móc. `PhanLopRenLuyen` là projection dữ liệu SIS dùng chung, không phải triển khai nghiệp vụ DRL.
- Một datasource, một `JpaTransactionManager` do Boot cấu hình, một Flyway history CTSV. Không tạo persistence unit hoặc transaction manager Auth riêng.

## 2. Dữ liệu chảy qua các layer

```text
FoundationController
  → FoundationService.execute: transaction + AuthAccess/FoundationSecurity
  → FoundationService: validation/invariant/ETag
  → FoundationRepository: truy vấn JPA, Entity; khóa/audit SQL khi cần
  → FoundationMapper → FoundationDtos → HTTP

MghpController → ConfigurationAccess → PolicyService / RoundService
  → ConfigurationRepository → Entity P05
  → ConfigurationMapper: projection whitelist đúng contract → HTTP
```

`FoundationService` vẫn ở package `foundation.web` hiện có để hạn chế thay đổi boundary/test; persistence mới nằm trong `foundation.repository`, DTO/mapper có package riêng. `ConfigurationEntities` gán field typed cho INSERT P05; không deserialize request trực tiếp thành Entity. Repository hiện dùng JPA `EntityManager`, không tạo hàng chục interface Spring Data chưa có nhu cầu chỉ để đủ tên layer.

Chỉ Repository chứa SQL của các luồng nghiệp vụ vừa refactor. `FoundationSecurity`, Auth và sync vẫn giữ SQL chuyên biệt vì đây là ranh giới bảo mật/đồng bộ hiện có. Module khác dùng public `FoundationAccess`/`FoundationUsage` và Auth interface, không nhập Entity/Repository nội bộ Foundation để ghi tắt vào bảng.

## 3. Thêm field hoặc quan hệ cho member

1. Xác định owner bảng và contract bị tác động. Kiểm schema thực thi tại `src/main/resources/db/migration/V1__baseline.sql` cộng delta V2–V8; không dùng số bảng ở báo cáo thiết kế cũ thay schema runtime.
2. Nếu cột/FK đã có: bổ sung annotation đúng tên, kiểu, length, precision/scale, NULL và identity. Việc ánh xạ Java không tự thay schema. Giữ `ddl-auto=validate`, `open-in-view=false`.
3. Nếu cần cột/FK/index mới: thống nhất thay đổi thiết kế và thêm migration Flyway tiếp theo trong chuỗi CTSV chung. Không sửa migration đã áp dụng; kiểm version/history/checksum trước khi ghi. Entity và migration cùng phản ánh một thay đổi đã được giao.
4. Quan hệ hiện có dùng `@ManyToOne(fetch=LAZY)`, không cascade xóa. FK scalar là phía ghi; association `insertable=false, updatable=false` là phía đọc. Ví dụ `sinhVien.setDanTocId(idCtsv)`, rồi `EntityManager.refresh` khi cần đọc lại `getDanToc()` trong cùng transaction. Không đồng thời khai báo hai phía cùng ghi một cột.
5. Không dùng ID SIS làm PK CTSV. Association trỏ PK CTSV đã resolve; raw source ID giữ cột riêng. FK ngoài tập Entity vẫn giữ ID scalar và được ghi rõ trong inventory; không tự thêm Entity xuyên module để nối đồ thị.
6. Mapper chỉ trả field DTO được contract cho phép; ID `Long` ra JSON string, decimal ra string đúng scale, ngày `LocalDate`, instant UTC. Không trả Entity/proxy hoặc tự thêm các field CCCD/liên hệ vào response tra cứu.

Ví dụ dùng quan hệ trong transaction của Foundation:

```java
SinhVien sinhVien = entityManager.find(SinhVien.class, idCtsv);
Long idDanToc = sinhVien.getDanTocId();      // FK local, có thể NULL
String tenDanToc = sinhVien.getDanToc() == null ? null : sinhVien.getDanToc().getTen();
// Tạo DTO ở đây; không trả sinhVien ra controller/module khác.
```

## 4. Transaction, version và SQL chuyên biệt

- `JdbcTemplate` và JPA cùng tham gia transaction nghiệp vụ. Auth tiếp tục transaction độc lập theo implementation hiện có; không gộp thu hồi token vào transaction MGHP chỉ vì chung DB.
- Foundation khóa root bằng `UPDLOCK,HOLDLOCK` trước khi đọc/so ETag và sửa Entity. Grant/revoke hoặc append lịch sử flush rồi chạm root bằng CAS; audit cùng transaction. Không dùng trực tiếp Entity setter + flush ngoài luồng khóa/kiểm quyền này.
- P05 giữ `WHERE id=? AND rowversion=?` khi cập nhật root; giữ application lock, thứ tự khóa, bất biến và thứ tự bulk replacement con. INSERT/read dùng Entity; bulk SQL còn lại có lý do trigger/FK/concurrency.
- SQL Server `rowversion` là `byte[8]`, read-only, `JdbcTypeCode(BINARY)`. Không dùng `@Version Long`, không biến `phien_ban` nghiệp vụ thành khóa concurrency, không để Hibernate tự ghi giá trị rowversion.
- JPA flush trước native SQL; sau CAS, bulk replacement hoặc trigger thay root, clear/refresh persistence context trước khi trả body/tag. `@DynamicUpdate` giúp cập nhật field local không ghi đè các cột nguồn SIS đã thay đổi; đây không thay khóa/CAS.
- Không bật cache Entity dùng chung hoặc giữ Entity qua nhiều lượt sync. Quan hệ LAZY chỉ đọc trong transaction; không bật Open Session in View để che lỗi.

## 5. Đồng bộ SIS giữ nguyên

Provider SIS không biết tên cột CTSV. Mapping nằm tại `contracts/foundation-resources.json` và `JdbcFoundationTarget`; units dùng implementation P01. Engine vẫn dùng JDBC connection riêng cho staging/seen-ID/fencing/presence/checkpoint, không chuyển từng item sang `Repository.save()`.

Full bootstrap → incremental CT/SNAPSHOT, transport, checkpoint và recovery không đổi. Entity là cách backend CTSV làm việc với schema hiện có; không phải giao thức sync mới. Một cột đích bị xóa mà mapping chưa sửa vẫn gây lỗi SQL; rollback trang lỗi, giữ checkpoint thành công, không tự bỏ qua cột. First full bị dở có thể cần recovery/baseline confirmation theo contract, không tự xóa checkpoint để chạy tiếp.

## 6. Kiểm lại khi sửa persistence

| Thay đổi | Kiểm tra phù hợp |
|---|---|
| Field/Entity | `FoundationEntityMappingTest`, `FoundationJpaIT`: Hibernate validate và đối chiếu đủ cột/NULL/length/precision/identity trên SQL thật |
| Query/DTO | Mapper tests, Foundation/P05 HTTP regression: NULL, string ID/decimal/date, whitelist, sort/paging/count |
| Ghi dữ liệu | JPA+JDBC rollback, audit fault, ETag 412/428, race, trigger và bất biến |
| Field nhận SIS | `JpaSyncCompatibilityIT`: consumer/target thật + SQL thật; envelope JSON synthetic, không gọi đó là SIS provider/CT runtime |
| Hạ tầng chung | Auth HTTP chọn lọc: login/rotation/reuse/logout/tokenVersion, CSRF/CORS |
| Luồng người dùng | Browser smoke qua HTTPS thật trên DB riêng; nhãn danh sách và chi tiết phải cùng cập nhật sau thao tác thành công |

Đợt refactor dùng duy nhất DB `ctsv_jpa_refactor_test_20261113`, marker `jpa_refactor_owner=ctsv-jpa-refactor-20261113`, DEV2022, owner trùng Windows login, compat160/collation Vietnamese_100_CI_AS_SC. DB phải được người thực hiện chuẩn bị/cho phép trước; `RefactorDatabase` fail-closed nếu sai đích, không tự fallback, drop hoặc reset. Không dùng DB dev/evidence cũ để chạy các test này.

Lệnh tối thiểu từ root CTSV, sau khi xác minh port TCP và có integrated-auth DLL theo runbook hiện có:

```powershell
.\mvnw.cmd test
$env:P00_SQL_PORT = '<port DEV2022 vừa xác minh>'
$env:PATH = (Join-Path $env:USERPROFILE '.m2\repository\com\microsoft\sqlserver\mssql-jdbc_auth\12.10.2.x64') + ';' + $env:PATH
.\mvnw.cmd '-Drefactor.test.enabled=true' '-Dit.test=FoundationJpaIT,JpaSyncCompatibilityIT,FoundationJpaHttpIT,ConfigurationJpaHttpIT' test-compile failsafe:integration-test failsafe:verify
```

Chạy Failsafe trực tiếp giúp không ghi đè executable JAR DEV đang chạy. Browser harness chỉ dành cho kiểm thử có thời hạn: chọn `JpaBrowserHarnessIT#serve`, DB refactor, origin test5175/backend9445, leaf127.0.0.1 đã tin cậy và config TLS local riêng. `frontend/e2e/persistence-refactor.mjs` đọc credentials synthetic từ `P05_BROWSER_HOME` ngoài tập chia sẻ; tuyệt đối không commit file credentials/key/PFX. Harness không thay launcher DEV hằng ngày. Kết quả/lệnh browser cụ thể được ghi ở kế hoạch refactor.
