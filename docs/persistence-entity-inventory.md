# Entity – bảng – FK – module

> **Delta P10 22/11:** `DecisionEntities` map sáu bảng hiện hữu: Root→mghp_quyet_dinh, Version→mghp_phien_ban_qd, Minutes→mghp_bien_ban, Candidate→mghp_ung_vien_phien_ban_qd, Line→mghp_dong_qd, GrantedPeriod→mghp_khoang_huong. FK scalar, không cascade/xuyên module hoặc trả Entity ra API. V22 thêm fence/header/source snapshot/unique; V23 append-only children. Hibernate validate đã chạy trên SQL P10 và DEV V23. Không thay 24 resource/7 bảng P05 trong inventory lịch sử phía dưới.

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
| Foundation | SinhVien | `sinh_vien` | nguoi_dung_id → nguoi_dung; noi_sinh_id → dia_phuong; dan_toc_id → dan_toc; quoc_tich_id → quoc_tich; lop_hien_tai_id → lop_sinh_hoat; khoa_quan_ly_hien_tai_id → don_vi; khoa_hoc_hien_tai_id → khoa_hoc; chuong_trinh_hien_tai_id → chuong_trinh_dao_tao; hoc_ky_ap_dung_nguon_id → hoc_ky; hoc_ky_bat_dau_nguon_id → hoc_ky; hoc_ky_ket_thuc_nguon_id → hoc_ky; he_thong_nguon_id → he_thong_ngoai; hoc_ky_han_hoc_nguon_id → hoc_ky | `ma_chuc_vu_nguon` là mã nguồn nullable do V30 bổ sung. |
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

## MGHP/P06

Các entity sau thuộc `mghp.application.ApplicationEntities`. FK của luồng nộp ghi bằng ID local; repo resolve owner/version trước khi ghi. Root `Application.fence` ánh xạ read-only cột `phien_ban_ky_thuat` của V11.

| Entity | Bảng | Quan hệ dùng trong P06 |
|---|---|---|
| Application | `mghp_ho_so` | Đợt, sinh viên, quá trình, chính sách và phiên bản |
| Submission | `mghp_phien_ban_ho_so` | Root và người nộp; unique operationId |
| Document, EvidenceLink | `mghp_giay_to_ho_so`, `mghp_tep_minh_chung` | Bản nộp, yêu cầu giấy, người liên quan và tệp TEP đã commit |
| SemesterRequest, RelatedPerson, Comparison | `mghp_hoc_ky_de_nghi`, `mghp_nguoi_lien_quan`, `mghp_doi_chieu_ke_khai` | Snapshot của đúng bản nộp |
| ApplicationEvent, Supplement | `mghp_nhat_ky_ho_so`, `mghp_yeu_cau_bo_sung` | Audit chuyển trạng thái và đóng vòng khi rút; wire vòng thuộc P07 |
| Notification, Recipient | `thong_bao`, `nguoi_nhan_thong_bao` | Thông báo và mốc đọc riêng của người nhận |

## MGHP/P08

`mghp.application.EvaluationEntities` bổ sung mapping cho các bảng đã có trong baseline. V13 thêm rowversion và bảo vệ chain; không thay V1–V12 đã áp dụng. Repository JPA ghi Entity, SQL chuyên biệt giữ khóa/CAS/audit cùng transaction. Các FK dưới đây ghi bằng ID CTSV đã kiểm nguồn, không cascade xóa hoặc trả Entity ra API.

| Entity | Bảng | FK / bảo đảm chính |
|---|---|---|
| Evaluation | `mghp_danh_gia_dieu_kien` | XOR hồ sơ+bản nộp hoặc quyền+bản cập nhật; process/kỳ/policy/scope; predecessor và một draft/chain |
| Criterion | `mghp_ket_qua_tieu_chi` | Đánh giá và tiêu chí đúng phiên bản chính sách |
| Evidence | `mghp_can_cu_danh_gia` | Đánh giá, kết quả tiêu chí tùy chọn và giấy đúng bản nguồn |
| Period | `mghp_khoang_du_dieu_kien` | Đánh giá; giữ từng khoảng inclusive, không lấp khoảng trống |
| Benefit | `mghp_quyen_huong` | Sinh viên/process/policy/bản nộp nguồn; QH04 chỉ cập nhật dữ kiện rà soát |
| BenefitEvent | `mghp_bien_dong_quyen_huong` | Quyền hưởng/predecessor; observation append, không ghi lại sự kiện cũ |

`Evaluation.fence` và `Benefit.fence` ánh xạ read-only `rowversion` bằng JDBC BINARY. Strong ETag gắn đúng projection; khóa SQL Server và UNIQUE bảo vệ concurrency. Trigger giữ bất biến evaluation và các bảng con sau confirm, kể cả bản đã superseded. Confirm không cấp quyền hưởng; quyền synthetic chỉ có trong fixture DB P08, công bố thật thuộc P12.

## Học bổng/T01

Module Học bổng sở hữu bốn Entity trong `hocbong.kkht.round.entity` và bốn bảng mới của Flyway V14. Các quan hệ dùng ID scalar, với khóa ngoại trên SQL Server theo bảng dưới.

| Entity | Bảng | Khóa ngoại và ràng buộc |
|---|---|---|
| `ScholarshipSemester` | `hb_hoc_ky` | PK CTSV độc lập. Khóa duy nhất theo `nam_hoc`, `thu_tu_ky`. Thứ tự kỳ dương và ngày bắt đầu ≤ ngày kết thúc |
| `SourceSemesterMapping` | `hb_anh_xa_ky_nguon` | `hoc_ky_id` → `hb_hoc_ky`, `xac_nhan_boi` → `nguoi_dung`. Khóa duy nhất theo hệ thống, loại mã, mã kỳ và mã đợt, gồm ngữ cảnh `NULL`. Trigger bảo vệ alias đã dùng |
| `ScholarshipRound` | `kkht_dot_xet` | `hoc_ky_id` → `hb_hoc_ky`, `nguoi_tao_id` → `nguoi_dung`, cặp `anh_xa_ky_nguon_id`, `hoc_ky_id` → cặp `id`, `hoc_ky_id` của `hb_anh_xa_ky_nguon`. Số đợt dương và duy nhất trong kỳ. `quy_tac_id` chờ bổ sung FK ở T03 |
| `ScholarshipAudit` | `hb_nhat_ky` | `nguoi_dung_id` → `nguoi_dung`, `vai_tro_id` → `vai_tro`. `doi_tuong_id` là tham chiếu đa hình. Snapshot lưu JSON, thời điểm UTC và correlation UUID. Trigger từ chối sửa hoặc xóa bản ghi |

Alias T01 dùng hệ thống `CTSV`, loại mã `ctsv_hoc_ky_id` và PK nguồn Foundation dưới dạng chuỗi. `ScholarshipRound.version` ánh xạ `phien_ban_ky_thuat` dưới dạng `byte[8]` chỉ đọc qua JDBC BINARY.

## PRE-P10-01 — Entity/migration timeline, Gate B20/11

Nguồn [DATA-TL01–05](../../nghiepvu/01-thiet-ke/api-data-readiness.md#timeline-schema-delta), [Detailed Plan §3/7](../../nghiepvu/09-trien-khai/pre-p10-timeline-implementation-plan.md). Mapping đã triển khai: bổ sung Embeddable RoundSchedule và Entity MghpLichSuLichTrinh. V17 đã chạy trên ctsv_timeline_test_20261120; Hibernate validate đạt. Các bảng module khác và lịch sử migration cũ giữ nguyên.

| Owner | Entity dự kiến | Bảng / delta | Quy tắc |
|---|---|---|---|
| MGHP configuration | MghpDotXet hiện có | 8planned nullable scalar + actual close-review actor/time/source hash | Migration additive và Entity cùng thay khi Gate B; rowversion V8/CAS giữ, không planned vào cột actual/khóa cấu hình |
| MGHP configuration | MghpLichSuLichTrinh mới | mghp_lich_su_lich_trinh append-only, UNIQUE(round,revision), typed snapshots/FK round/actor/audit | Không JSON/cascade delete/Entity trả API; scalar FK ghi/LAZY association read-only; JPA/JDBC cùng TX, flush/refresh quanh CAS |

Giữ Auth/sync JDBC và mọi module khác; service dùng Entity/typed parameters/DTO whitelist. V17 additive/history append-only/closure marker, SQL/HTTP runtime và Entity validation có evidence trong Detailed Plan §11. Owner đã cho phép áp V14–V17; ctsv_dev hiện V17 và launcher/smoke4/4PASS, không reset dữ liệu hoặc account.

## Đợt xét: delta owner21/11/2026

V18 bỏ huong_dan_ke_khai/huong_dan_can_cu_nop_muon tại mghp_dot_xet; MghpDotXet, ConfigurationEntities/Mapper/Repository và wire hiện hành cùng bỏ hai field. Các entity/FK/rowversion/timeline/history khác giữ nguyên. Không sửa V1–V17.


**Delta review lifecycle22/11:** không thêm cột/bảng/Entity hoặc migration. ApplicationWorkflow là repository read projection trên hồ sơ/evaluation/receiving-round/candidate/publication hiện có; ReviewLifecycleRules phân loại trạng thái. status intake giữ nguyên, workflowStatus/evaluationAllowed là DTO, không lưu đè snapshot nguồn đã chốt. JPA validation/Flyway vẫn V23.

## P11 — văn bản và pipeline ký23/11

`mghp.application.SigningEntities` thêm5 Entity nghiệp vụ trên bảng đã có: Document→van_ban; DocumentVersion→phien_ban_van_ban; Link→mghp_lien_ket_phien_ban_van_ban; Request→yeu_cau_ky; Step→buoc_ky. FK scalar, không cascade, không trả Entity ra API. V24 bổ sung request rowversion read-only byte[8], checkpoint/lease/principal/output ở lan_goi_dich_vu_ky; không sửa V1–V23.

Attempt/receipt/artifact/certificate observation dùng JDBC chuyên biệt trong SigningRepository để giữ fencing/CAS/short transaction; storage bytes ở Cloudinary authenticated, metadata/ledger Shared Files. Dùng public ManagedFiles/FileReadPolicy, không import repository/entity Shared Files xuyên module. Thêm role PHT vào registry và danh mục role Foundation, không tự grant account. SQL/JPA mapping được validate trên DB P11 riêng; DEV nâng sau gate UI.

## Delta V31 — chốt hồ sơ online (25/11/2026)

Không thêm Entity/table. MghpDotXet và MghpLichSuLichTrinh thêm nguoi_chot_online_id bigint FK NguoiDung, thoi_diem_chot_online datetimeoffset(7), sha256_nguon_chot_online varchar(64). Tất cả nullable cho lịch sử cũ. Root tuple đủ3/không có3, bất biến sau chốt; history append-only hiện có tiếp tục bảo vệ. Migration hiện hành V31 giữ nguyên SQL/checksum của V26 MGHP cũ để nhường V26–V30 cho main; không chạy lại SQL đã áp dụng. Entity/JPA validation và CAS rowversion hiện hành giữ nguyên. Plan/evidence lịch sử: [stage handoff](stage-handoff-20261125.md).

## ĐRL — DRL-02.3

Module ĐRL sở hữu 11 bảng ghi trong schema `dbo`, ánh xạ tại `pbl6.ctsv.drl.entity`. [V17](../src/main/resources/db/migration/V17__drl_schema.sql) tạo các bảng và view. [V26](../src/main/resources/db/migration/V26__drl_dossier_freeze.sql) bổ sung `dbo.RLLopDot.NgayLapHoSo` kiểu `datetimeoffset(7) NULL`, ánh xạ thành `OffsetDateTime` với `@TimeZoneStorage(NATIVE)`. 11 bảng có tổng cộng 75 cột và dùng PK `ID bigint IDENTITY(1,1)` qua `DrlEntity`.

| Entity | Bảng `dbo` | FK giữ ID scalar `Long` → bảng/cột đích |
|---|---|---|
| `RlBieuMau` | `RLBieuMau` | — |
| `RlNhomTieuChi` | `RLNhomTieuChi` | `BieuMauID` → `RLBieuMau.ID` |
| `RlBieuMauCt` | `RLBieuMauCT` | `NhomTieuChiID` → `RLNhomTieuChi.ID`, `TieuChiChaID` → `RLBieuMauCT.ID` (NULL) |
| `RlXepLoai` | `RLXepLoai` | `BieuMauID` → `RLBieuMau.ID` |
| `RlDot` | `RLDot` | `HocKyID` → `hoc_ky.id`, `BieuMauID` → `RLBieuMau.ID` |
| `RlKhoaDot` | `RLKhoaDot` | `DotID` → `RLDot.ID`, `KhoaID` → `don_vi.id`, `DaiDienKhoaID` → `can_bo.id` (NULL) |
| `RlLopDot` | `RLLopDot` | `KhoaDotID` → `RLKhoaDot.ID`, `LopID` → `lop_sinh_hoat.id`, `LopTruongID` → `sinh_vien.id` (NULL), `GVCNID` → `can_bo.id` (NULL), `NguoiXNCTSVID` → `nguoi_dung.id` (NULL) |
| `RlPhieu` | `RLPhieu` | `LopDotID` → `RLLopDot.ID`, `SinhVienID` → `sinh_vien.id` |
| `RlPhieuDiemCt` | `RLPhieuDiemCT` | `PhieuID` → `RLPhieu.ID`, `BieuMauCTID` → `RLBieuMauCT.ID` |
| `RlHopLop` | `RLHopLop` | `LopDotID` → `RLLopDot.ID` |
| `RlVanBanKy` | `RLVanBanKy` | `LopDotID` → `RLLopDot.ID`, `PhienBanVanBanID` → `phien_ban_van_ban.id` |

Cả 21 FK đều giữ ID scalar, không dùng association hay cascade, kể cả các FK tới Foundation và bảng văn bản dùng chung. Giữ Entity và proxy trong nội bộ ĐRL. API và trao đổi xuyên module dùng DTO hoặc projection qua public boundary.

Read-model `RlKetQuaSv` ánh xạ view `dbo.RLKetQuaSV` bằng `@Immutable`, dùng `PhieuID` làm `@Id` và chỉ có truy vấn đọc. View có 9 cột gồm `PhieuID`, `SinhVienID`, `LopDotID`, `DotID`, `HocKyID`, `TongDiemCN`, `TongDiemLop`, `XepLoai` và `DaCongBo`. Các ID trong view là cột chiếu, nằm ngoài 21 FK của bảng ghi.

- `DrlRepository` phục vụ truy cập dữ liệu nội bộ qua `EntityManager`, dùng `JdbcTemplate` để khóa dòng lớp–đợt trong transaction chung của CTSV, và cung cấp truy vấn đọc kết quả từ view. Application service sở hữu transaction và kiểm quyền.
- `DrlConfiguration` đăng ký `DrlPhysicalNamingStrategy`. Strategy giữ nguyên tên PascalCase đã quote của ĐRL trong các danh sách `TABLES` và `COLUMNS` và dùng quy tắc mặc định cho các tên khác. Nếu V27 trở đi bổ sung bảng, view hoặc cột PascalCase cho ĐRL, cập nhật danh sách tương ứng cùng annotation mapping và kiểm lại Hibernate validate.

**Phạm vi Hibernate validate đã ghi nhận ở DRL-02.2**

- **SQL Server test `ctsv_drl_fresh_test`.** `DrlPersistenceIT` chạy `ddl-auto=validate` với scan toàn bộ `pbl6.ctsv` và kiểm đếm 91 mapping gồm ĐRL 12, Foundation 31, MGHP 38, Học bổng 6 và SHCN 4.
- **DEV `ctsv_dev`.** Log khởi động JAR qua launcher với profile `dev` ngày 24/11/2026 ghi nhận `EntityManagerFactory` mặc định khởi tạo thành công và backend đã khởi động với `ddl-auto=validate`. Mốc 91 là số đếm của SQL IT. Lượt DRL-02.3 chỉ đối chiếu tài liệu và bằng chứng đã có.
