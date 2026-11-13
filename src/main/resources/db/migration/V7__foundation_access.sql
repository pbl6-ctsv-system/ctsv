-- DATA-V01: technical concurrency roots; one shared CTSV history.
ALTER TABLE dbo.hoc_ky ADD phien_ban rowversion NOT NULL;
ALTER TABLE dbo.nguoi_dung ADD phien_ban rowversion NOT NULL;
ALTER TABLE dbo.qua_trinh_theo_hoc ADD phien_ban rowversion NOT NULL;
INSERT dbo.vai_tro(ma,ten,dang_hoat_dong)
SELECT v.ma,v.ten,1 FROM (VALUES
 (N'mghp_chuyen_vien',N'Chuyên viên MGHP'),
 (N'mghp_pho_phong',N'Phó phòng MGHP'),
 (N'ctsv.roles.manage',N'Quản trị quyền CTSV')) v(ma,ten)
WHERE NOT EXISTS(SELECT 1 FROM dbo.vai_tro r WHERE r.ma=v.ma);
CREATE UNIQUE INDEX ux_foundation_current_role ON dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he)
WHERE hieu_luc_den IS NULL AND don_vi_id IS NULL;
