# Entity – bảng – FK – module

Nguồn: migrations CTSV V1–V8; không thay schema. 24 resource + 7 bảng hỗ trợ Foundation; 7 bảng P05. Auth 8 bảng và bảng kỹ thuật sync giữ JDBC.

| Module | Entity | Bảng | FK thành quan hệ LAZY | FK giữ ID ngoài tập Entity |
|---|---|---|---|---|
| Foundation | DonVi | `don_vi` | don_vi_cha_id → don_vi; he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | DanToc | `dan_toc` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | QuocTich | `quoc_tich` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | DiaPhuong | `dia_phuong` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | HocKy | `hoc_ky` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | KhoaHoc | `khoa_hoc` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | NganhQuocGia | `nganh_quoc_gia` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | NganhNoiBo | `nganh_noi_bo` | nganh_quoc_gia_id → nganh_quoc_gia; he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | ChuongTrinhDaoTao | `chuong_trinh_dao_tao` | nganh_noi_bo_id → nganh_noi_bo; nganh_quoc_gia_id → nganh_quoc_gia; hoc_ky_bat_dau_id → hoc_ky; hoc_ky_ket_thuc_id → hoc_ky; he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | LopSinhHoat | `lop_sinh_hoat` | chuong_trinh_dao_tao_id → chuong_trinh_dao_tao; khoa_quan_ly_id → don_vi; khoa_hoc_id → khoa_hoc; he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | SinhVien | `sinh_vien` | nguoi_dung_id → nguoi_dung; noi_sinh_id → dia_phuong; dan_toc_id → dan_toc; quoc_tich_id → quoc_tich; lop_hien_tai_id → lop_sinh_hoat; khoa_quan_ly_hien_tai_id → don_vi; khoa_hoc_hien_tai_id → khoa_hoc; chuong_trinh_hien_tai_id → chuong_trinh_dao_tao; hoc_ky_ap_dung_nguon_id → hoc_ky; hoc_ky_bat_dau_nguon_id → hoc_ky; hoc_ky_ket_thuc_nguon_id → hoc_ky; he_thong_nguon_id → he_thong_ngoai; hoc_ky_han_hoc_nguon_id → hoc_ky | — |
| Foundation | DuLieuHocVuDongBo | `du_lieu_hoc_vu_dong_bo` | sinh_vien_id → sinh_vien; hoc_ky_id → hoc_ky; chuong_trinh_dao_tao_id → chuong_trinh_dao_tao; qua_trinh_hoc_id → qua_trinh_theo_hoc; he_thong_nguon_id → he_thong_ngoai | dot_dong_bo_id → dot_dong_bo |
| Foundation | HocPhan | `hoc_phan` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | KhungChuongTrinh | `khung_chuong_trinh` | he_thong_nguon_id → he_thong_ngoai; hoc_phan_id → hoc_phan; hoc_phan_duoc_thay_the_id → hoc_phan | — |
| Foundation | DiemKyHoc | `diem_ky_hoc` | he_thong_nguon_id → he_thong_ngoai; hoc_ky_id → hoc_ky; sinh_vien_id → sinh_vien; hoc_phan_id → hoc_phan; hoc_phan_tuong_duong_id → hoc_phan | — |
| Foundation | DiemMien | `diem_mien` | he_thong_nguon_id → he_thong_ngoai; hoc_ky_id → hoc_ky; hoc_phan_id → hoc_phan; sinh_vien_id → sinh_vien; hoc_phan_tuong_duong_id → hoc_phan | — |
| Foundation | TieuThuc | `tieu_thuc` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | PhongHoc | `phong_hoc` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | LichSuDungPhong | `lich_su_dung_phong` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | KhoaPhong | `khoa_phong` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | ChiTietKhoaPhong | `chi_tiet_khoa_phong` | he_thong_nguon_id → he_thong_ngoai | — |
| Foundation | PhanLopRenLuyen | `phan_lop_ren_luyen` | he_thong_nguon_id → he_thong_ngoai; hoc_ky_id → hoc_ky; sinh_vien_id → sinh_vien; lop_ren_luyen_id → lop_sinh_hoat | — |
| Foundation | GhepLopSinhHoat | `ghep_lop_sinh_hoat` | he_thong_nguon_id → he_thong_ngoai; hoc_ky_id → hoc_ky; lop_goc_id → lop_sinh_hoat; lop_nhan_id → lop_sinh_hoat | — |
| Foundation | TienDoSinhVien | `tien_do_sinh_vien` | he_thong_nguon_id → he_thong_ngoai; hoc_ky_id → hoc_ky; sinh_vien_id → sinh_vien; chuong_trinh_dao_tao_id → chuong_trinh_dao_tao | — |
| Foundation | HeThongNgoai | `he_thong_ngoai` | — | — |
| Foundation | QuaTrinhTheoHoc | `qua_trinh_theo_hoc` | sinh_vien_id → sinh_vien; chuong_trinh_dao_tao_id → chuong_trinh_dao_tao; khoa_hoc_id → khoa_hoc; hoc_ky_bat_dau_id → hoc_ky; hoc_ky_ket_thuc_nguon_id → hoc_ky; hoc_ky_ap_dung_trang_thai_id → hoc_ky; nguoi_xac_minh_id → nguoi_dung; he_thong_nguon_id → he_thong_ngoai | tep_can_cu_id → tep_dinh_kem |
| Foundation | LichSuTrangThaiSv | `lich_su_trang_thai_sv` | qua_trinh_hoc_id → qua_trinh_theo_hoc; hoc_ky_ap_dung_id → hoc_ky; he_thong_nguon_id → he_thong_ngoai; nguoi_xac_minh_id → nguoi_dung | — |
| Foundation | QuaTrinhThuocLop | `qua_trinh_thuoc_lop` | qua_trinh_hoc_id → qua_trinh_theo_hoc; lop_id → lop_sinh_hoat; he_thong_nguon_id → he_thong_ngoai; nguoi_xac_minh_id → nguoi_dung | — |
| Foundation | NguoiDung | `nguoi_dung` | — | — |
| Foundation | VaiTro | `vai_tro` | — | — |
| Foundation | PhanQuyenNguoiDung | `phan_quyen_nguoi_dung` | nguoi_dung_id → nguoi_dung; vai_tro_id → vai_tro; don_vi_id → don_vi; nguoi_cap_quyen_id → nguoi_dung | — |
| MGHP/P05 | MghpChinhSach | `mghp_chinh_sach` | — | — |
| MGHP/P05 | MghpPhienBanChinhSach | `mghp_phien_ban_chinh_sach` | chinh_sach_id → mghp_chinh_sach | nguoi_tao_id → nguoi_dung |
| MGHP/P05 | MghpTieuChiChinhSach | `mghp_tieu_chi_chinh_sach` | phien_ban_chinh_sach_id → mghp_phien_ban_chinh_sach | — |
| MGHP/P05 | MghpNhomYeuCauGiayTo | `mghp_nhom_yeu_cau_giay_to` | phien_ban_chinh_sach_id → mghp_phien_ban_chinh_sach | — |
| MGHP/P05 | MghpGiayToBatBuoc | `mghp_giay_to_bat_buoc` | nhom_id → mghp_nhom_yeu_cau_giay_to | — |
| MGHP/P05 | MghpDotXet | `mghp_dot_xet` | — | hoc_ky_id → hoc_ky; thong_bao_dot_xet_id → thong_bao; nguoi_khoa_id → nguoi_dung; nguoi_chot_hoi_dong_id → nguoi_dung; nguoi_tao_id → nguoi_dung; nguoi_khoa_cau_hinh_id → nguoi_dung |
| MGHP/P05 | MghpChinhSachDotXet | `mghp_chinh_sach_dot_xet` | dot_xet_id → mghp_dot_xet; phien_ban_chinh_sach_id → mghp_phien_ban_chinh_sach | tep_mau_don_id → tep_dinh_kem |
