-- P05 aggregate fences. Existing typed columns/FKs/UNIQUE and immutable guards are retained.
-- Only the sourced nine-policy catalog is seeded; legal versions/rounds remain explicit test fixtures.
ALTER TABLE dbo.mghp_phien_ban_chinh_sach ADD phien_ban_ky_thuat rowversion NOT NULL;
ALTER TABLE dbo.mghp_dot_xet ADD phien_ban rowversion NOT NULL;
ALTER TABLE dbo.mghp_phien_ban_chinh_sach WITH CHECK ADD CONSTRAINT ck_p05_policy_status CHECK(trang_thai IS NULL OR trang_thai IN(N'draft',N'locked'));
ALTER TABLE dbo.mghp_phien_ban_chinh_sach WITH CHECK ADD CONSTRAINT ck_p05_policy_age CHECK((tuoi_toi_thieu IS NULL OR tuoi_toi_thieu>=0) AND (tuoi_toi_da IS NULL OR tuoi_toi_da>=0) AND (tuoi_toi_thieu IS NULL OR tuoi_toi_da IS NULL OR tuoi_toi_thieu<=tuoi_toi_da));
ALTER TABLE dbo.mghp_dot_xet WITH CHECK ADD CONSTRAINT ck_p05_round_status CHECK(trang_thai IS NULL OR trang_thai IN(N'draft',N'open',N'closed',N'completed'));
ALTER TABLE dbo.mghp_dot_xet WITH CHECK ADD CONSTRAINT ck_p05_round_modes CHECK((che_do_truy_linh IS NULL OR che_do_truy_linh=N'theo_phap_luat') AND (cach_xet_thay_doi_giua_ky IS NULL OR cach_xet_thay_doi_giua_ky=N'khoang_ngay_chuyen_vien_xac_nhan'));
CREATE INDEX ix_p05_round_scope_order ON dbo.mghp_dot_xet(hoc_ky_id,trang_thai,thoi_diem_mo DESC,id DESC);

EXEC(N'CREATE TRIGGER dbo.tr_p05_criterion_fence ON dbo.mghp_tieu_chi_chinh_sach AFTER INSERT,UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
UPDATE p SET phien_ban=phien_ban FROM dbo.mghp_phien_ban_chinh_sach p JOIN (SELECT phien_ban_chinh_sach_id id FROM inserted UNION SELECT phien_ban_chinh_sach_id FROM deleted) c ON c.id=p.id; END;');
EXEC(N'CREATE TRIGGER dbo.tr_p05_group_fence ON dbo.mghp_nhom_yeu_cau_giay_to AFTER INSERT,UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
UPDATE p SET phien_ban=phien_ban FROM dbo.mghp_phien_ban_chinh_sach p JOIN (SELECT phien_ban_chinh_sach_id id FROM inserted UNION SELECT phien_ban_chinh_sach_id FROM deleted) c ON c.id=p.id; END;');
EXEC(N'CREATE TRIGGER dbo.tr_p05_requirement_fence ON dbo.mghp_giay_to_bat_buoc AFTER INSERT,UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
UPDATE p SET phien_ban=phien_ban FROM dbo.mghp_phien_ban_chinh_sach p JOIN dbo.mghp_nhom_yeu_cau_giay_to g ON g.phien_ban_chinh_sach_id=p.id JOIN (SELECT nhom_id id FROM inserted UNION SELECT nhom_id FROM deleted) d ON d.id=g.id; END;');
EXEC(N'CREATE TRIGGER dbo.tr_p05_round_policy_fence ON dbo.mghp_chinh_sach_dot_xet AFTER INSERT,UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
IF EXISTS(SELECT 1 FROM (SELECT dot_xet_id id FROM inserted UNION SELECT dot_xet_id FROM deleted) x JOIN dbo.mghp_dot_xet r ON r.id=x.id WHERE r.thoi_diem_khoa_cau_hinh IS NOT NULL OR EXISTS(SELECT 1 FROM dbo.mghp_ho_so h WHERE h.dot_xet_id=r.id)) THROW 51705,N''Round configuration is immutable.'',1;
UPDATE r SET thoi_diem_cap_nhat=SYSUTCDATETIME() FROM dbo.mghp_dot_xet r JOIN (SELECT dot_xet_id id FROM inserted UNION SELECT dot_xet_id FROM deleted) x ON x.id=r.id; END;');
EXEC(N'CREATE TRIGGER dbo.tr_p05_policy_state_immutable ON dbo.mghp_phien_ban_chinh_sach AFTER UPDATE,DELETE AS BEGIN SET NOCOUNT ON;
IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE (d.trang_thai=N''locked'' OR d.thoi_diem_khoa IS NOT NULL) AND (i.id IS NULL OR ISNULL(i.trang_thai,N'''')<>ISNULL(d.trang_thai,N''''))) THROW 51706,N''Locked policy state is immutable.'',1; END;');

-- Sourced policy catalog only. No assumed legal confirmation.
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M1',N'Sinh viên là người có công với cách mạng hoặc thân nhân (con thương binh, bệnh binh, con liệt sĩ, con người bị nhiễm CĐHH)',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M1');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M2',N'Sinh viên khuyết tật',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M2');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M3',N'Sinh viên mồ côi không nơi nương tựa (quá 22 tuổi không được hưởng)',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M3');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M4',N'SV là DTTS có cha/mẹ hoặc ông bà (nếu ở với ông bà) thuộc hộ nghèo, cận nghèo',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M4');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M5',N'SV là DTTS rất ít người ở vùng có điều kiện KT-XH khó khăn/đặc biệt khó khăn',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M5');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M6',N'SV là DTTS ở thôn/bản/xã đặc biệt khó khăn (khu vực vùng III, vùng DTTS)',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M6');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M7',N'Sinh viên là con CBVC, công nhân bị tai nạn lao động hoặc bệnh nghề nghiệp',N'ND_238',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M7');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M8',N'Sinh viên là con của CBVC đang công tác (đương chức) trong ĐHĐN',N'NOI_BO_DHDN',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M8');
INSERT dbo.mghp_chinh_sach(ma,ten,nhom_chinh_sach,dang_hoat_dong) SELECT N'M9',N'Sinh viên là con của CBVC đã nghỉ hưu theo chế độ trong ĐHĐN',N'NOI_BO_DHDN',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M9');
