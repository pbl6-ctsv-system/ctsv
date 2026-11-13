-- P02-01: technical sync state only; no business data update or ID remapping.
CREATE TABLE dbo.dong_bo_trang_thai (
 he_thong_nguon_id bigint NOT NULL, tai_nguyen nvarchar(128) NOT NULL,
 the_he bigint NOT NULL CONSTRAINT df_dong_bo_the_he DEFAULT 0, chu_so_huu uniqueidentifier NULL,
 source_epoch uniqueidentifier NULL, ct_version bigint NULL, luot_thanh_cong uniqueidentifier NULL,
 CONSTRAINT pk_dong_bo_trang_thai PRIMARY KEY(he_thong_nguon_id,tai_nguyen),
 CONSTRAINT fk_dong_bo_nguon FOREIGN KEY(he_thong_nguon_id) REFERENCES dbo.he_thong_ngoai(id),
 CONSTRAINT ck_dong_bo_checkpoint CHECK((source_epoch IS NULL AND ct_version IS NULL) OR (source_epoch IS NOT NULL AND ct_version>=0)),
 CONSTRAINT ck_dong_bo_fence CHECK(the_he>=0)
);
CREATE TABLE dbo.dong_bo_luot (
 id uniqueidentifier NOT NULL PRIMARY KEY, he_thong_nguon_id bigint NOT NULL,tai_nguyen nvarchar(128) NOT NULL,
 the_he bigint NOT NULL, chu_so_huu uniqueidentifier NOT NULL, export_id uniqueidentifier NOT NULL,
 source_epoch uniqueidentifier NOT NULL, che_do varchar(16) NOT NULL, tu_version bigint NULL, den_version bigint NOT NULL,
 as_of datetimeoffset(7) NOT NULL, trang_thai varchar(16) NOT NULL, so_ban_ghi bigint NOT NULL DEFAULT 0,
 source_id_cuoi bigint NOT NULL DEFAULT 0, so_trang int NOT NULL DEFAULT 0,
 thoi_diem_tao datetimeoffset(7) NOT NULL DEFAULT SYSUTCDATETIME(),thoi_diem_ket_thuc datetimeoffset(7) NULL,
 CONSTRAINT fk_dong_bo_luot_nguon FOREIGN KEY(he_thong_nguon_id,tai_nguyen) REFERENCES dbo.dong_bo_trang_thai(he_thong_nguon_id,tai_nguyen),
 CONSTRAINT ck_dong_bo_luot_status CHECK(trang_thai IN('RUNNING','SUCCESS','FAILED','INTERRUPTED')),
 CONSTRAINT ck_dong_bo_luot_window CHECK(den_version>=0 AND ((che_do='full' AND tu_version IS NULL) OR (che_do='incremental' AND tu_version>=0 AND tu_version<=den_version)))
);
CREATE INDEX ix_dong_bo_luot_nguon ON dbo.dong_bo_luot(he_thong_nguon_id,tai_nguyen,the_he);
CREATE TABLE dbo.dong_bo_da_thay (
 luot_id uniqueidentifier NOT NULL, source_id bigint NOT NULL CHECK(source_id>0),
 CONSTRAINT pk_dong_bo_da_thay PRIMARY KEY(luot_id,source_id),
 CONSTRAINT fk_dong_bo_da_thay_luot FOREIGN KEY(luot_id) REFERENCES dbo.dong_bo_luot(id)
);
CREATE TABLE dbo.dong_bo_trang_tam (
 luot_id uniqueidentifier NOT NULL,so_trang int NOT NULL CHECK(so_trang>0),
 cursor_hash char(64) NOT NULL,payload_hash char(64) NOT NULL,payload nvarchar(max) NOT NULL CHECK(ISJSON(payload)=1),
 CONSTRAINT pk_dong_bo_trang_tam PRIMARY KEY(luot_id,so_trang),
 CONSTRAINT uq_dong_bo_cursor UNIQUE(luot_id,cursor_hash),
 CONSTRAINT fk_dong_bo_trang_luot FOREIGN KEY(luot_id) REFERENCES dbo.dong_bo_luot(id)
);
CREATE TABLE dbo.dong_bo_hien_dien (
 he_thong_nguon_id bigint NOT NULL,tai_nguyen nvarchar(128) NOT NULL,source_id bigint NOT NULL CHECK(source_id>0),
 local_id bigint NOT NULL CHECK(local_id>0),dang_hien_dien bit NOT NULL,sha256 char(64) NOT NULL,
 CONSTRAINT pk_dong_bo_hien_dien PRIMARY KEY(he_thong_nguon_id,tai_nguyen,source_id),
 CONSTRAINT uq_dong_bo_hien_dien_local UNIQUE(he_thong_nguon_id,tai_nguyen,local_id),
 CONSTRAINT fk_dong_bo_hien_dien_nguon FOREIGN KEY(he_thong_nguon_id,tai_nguyen) REFERENCES dbo.dong_bo_trang_thai(he_thong_nguon_id,tai_nguyen)
);
