#requires -Version 7.0
param([ValidateSet('inspect','prepare','seed','snapshot','state','receiving','protected')][string]$Action='inspect')
$ErrorActionPreference='Stop'
[Console]::InputEncoding=[Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$data=([Console]::In.ReadToEnd()|ConvertFrom-Json)
if($Action -ne 'inspect' -and $data.run -notmatch '^TLX-[0-9]{8}-[a-z0-9]{6}$'){throw 'Owned TLX run prefix required'}
$port=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$cn=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$port;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=TLX-owned-fixture")
$cn.Open()
function Query([string]$Sql,[hashtable]$Parameters=@{}) {
 $cmd=$cn.CreateCommand();$cmd.CommandText=$Sql;$cmd.CommandTimeout=30
 foreach($key in $Parameters.Keys){[void]$cmd.Parameters.AddWithValue('@'+$key,$Parameters[$key])}
 $reader=$cmd.ExecuteReader();$result=[System.Collections.Generic.List[object]]::new()
 do{while($reader.Read()){$row=[ordered]@{};for($i=0;$i -lt $reader.FieldCount;$i++){$value=$reader.GetValue($i);$row[$reader.GetName($i)]=if($value -is [DBNull]){$null}elseif($value -is [datetime]){$value.ToString('o')}elseif($value -is [datetimeoffset]){$value.ToString('o')}elseif($value -is [byte[]]){[Convert]::ToHexString($value)}else{$value};};$result.Add([pscustomobject]$row)}}while($reader.NextResult())
 $reader.Close();$cmd.Dispose();return ,$result.ToArray()
}
try{
 $guard=(Query "SELECT @@SERVERNAME serverName,DB_NAME() databaseName,SUSER_SNAME(owner_sid) ownerName FROM sys.databases WHERE name=DB_NAME(); SELECT CONVERT(nvarchar(100),value) marker FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner'; SELECT MAX(CONVERT(int,version)) version,MIN(CONVERT(int,success)) success FROM dbo.flyway_schema_history;")
 if($guard[0].serverName -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard[0].databaseName -ne 'ctsv_dev' -or $guard[0].ownerName -ne [Security.Principal.WindowsIdentity]::GetCurrent().Name -or $guard[1].marker -ne 'ctsv-local-dev-v1' -or $guard[2].version -ne 17 -or $guard[2].success -ne 1){throw 'DEV server/database/owner/marker/history guard failed'}
 $p=@{run=[string]$data.run}
 if($Action -eq 'inspect'){
  $root=(Resolve-Path "$PSScriptRoot/../..").Path
  . "$root/tools/dev/Dev.Common.ps1"
  $homePath=Get-DevHome $root
  $runtime=Get-Content (Join-Path $homePath 'processes.json') -Raw|ConvertFrom-Json
  $sessions=Query 'SELECT session_id,database_id,host_process_id FROM sys.dm_exec_sessions WHERE host_process_id=@pid' @{pid=[int]$runtime.Backend.Pid}
  if(-not ($sessions|Where-Object database_id -eq (Query 'SELECT DB_ID() id')[0].id)){throw 'No launcher JDBC session on ctsv_dev'}
  [pscustomobject]@{guard=$guard;port=$port;backendPid=$runtime.Backend.Pid;sessions=$sessions}|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'protected'){
  Query @'
SELECT N'rounds' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_dot_xet WHERE ma_dot_xet NOT LIKE N'TLX-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest;
SELECT N'applications' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT h.* FROM dbo.mghp_ho_so h JOIN dbo.mghp_dot_xet d ON d.id=h.dot_xet_id WHERE d.ma_dot_xet NOT LIKE N'TLX-%' ORDER BY h.id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest;
SELECT N'evaluations' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT e.* FROM dbo.mghp_danh_gia_dieu_kien e LEFT JOIN dbo.mghp_dot_xet d ON d.id=e.dot_xet_id WHERE d.id IS NULL OR d.ma_dot_xet NOT LIKE N'TLX-%' ORDER BY e.id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest;
SELECT N'policies' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_phien_ban_chinh_sach WHERE ten_tai_thoi_diem_chot NOT LIKE N'%TLX-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest;
SELECT N'dev_student' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.sinh_vien WHERE mssv=N'DEV-SV-001' FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest;
SELECT N'P10 artifacts' checkName,(SELECT COUNT(*) FROM dbo.mghp_quyet_dinh) decisions,(SELECT COUNT(*) FROM dbo.mghp_bien_ban) minutes,(SELECT COUNT(*) FROM dbo.van_ban) documents,(SELECT COUNT(*) FROM dbo.phien_ban_van_ban) documentVersions;
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'prepare'){
  Query @'
SET XACT_ABORT ON;BEGIN TRAN;
IF EXISTS(SELECT 1 FROM dbo.hoc_ky WHERE ma=@run) THROW 51930,N'Run already exists; preserve it and select a new prefix',1;
DECLARE @sv bigint=(SELECT id FROM dbo.sinh_vien WHERE mssv=N'DEV-SV-001'),@user bigint,@actor bigint;
SELECT @user=nguoi_dung_id FROM dbo.sinh_vien WHERE id=@sv;
SELECT @actor=n.id FROM dbo.nguoi_dung n JOIN dbo.dinh_danh_dang_nhap d ON d.nguoi_dung_id=n.id JOIN dbo.TaiKhoan a ON CONVERT(nvarchar(36),a.ID)=d.ma_chu_the_dang_nhap WHERE a.TenDangNhap=N'dev.chuyenvien';
IF @sv IS NULL OR @user IS NULL OR @actor IS NULL THROW 51931,N'DEV identity missing',1;
INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc)
SELECT @run,N'Thực hành lịch MGHP · '+@run,COALESCE(MAX(so_thu_tu),0)+1,'20260901','20270131',N'verified',N'SYNTHETIC TLX calendar' FROM dbo.hoc_ky;
DECLARE @semester bigint=SCOPE_IDENTITY();
INSERT dbo.dan_toc(ten) VALUES(N'Dân tộc giả lập '+@run);DECLARE @ethnic bigint=SCOPE_IDENTITY();
INSERT dbo.don_vi(ma,ten) VALUES(@run,N'Khoa thực hành '+@run);DECLARE @unit bigint=SCOPE_IDENTITY();
INSERT dbo.khoa_hoc(ma) VALUES(@run);DECLARE @cohort bigint=SCOPE_IDENTITY();
INSERT dbo.lop_sinh_hoat(ma,khoa_hoc_id,khoa_quan_ly_id) VALUES(@run,@cohort,@unit);DECLARE @class bigint=SCOPE_IDENTITY();
INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,thoi_diem_bat_dau) VALUES(@sv,@cohort,@semester,@run,N'verified','20260901');DECLARE @process bigint=SCOPE_IDENTITY();
DECLARE @parent bigint=(SELECT id FROM dbo.mghp_chinh_sach WHERE ma=N'M2');
INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,ma_pham_vi,ngay_bat_dau_hieu_luc,trang_thai,tinh_trang_xac_nhan_quy_tac)
SELECT @parent,COALESCE(MAX(phien_ban),0)+1,N'SYNTHETIC · Đối chiếu hồ sơ lịch trình '+@run,0.5,N'hoc_phi_chuong_trinh_chinh','20260101',N'draft',N'da_doi_chieu_nguon' FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=@parent;
DECLARE @policy bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_nhom_yeu_cau_giay_to(phien_ban_chinh_sach_id,ma,ten,so_yeu_cau_toi_thieu_dat) VALUES(@policy,N'TLX',N'Minh chứng thử nghiệm lịch trình',1);DECLARE @g bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung) VALUES(@g,N'TLX',N'Minh chứng synthetic TLX',N'xac_nhan_khuyet_tat',N'sinh_vien');DECLARE @req bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_tieu_chi_chinh_sach(phien_ban_chinh_sach_id,ma,ten,bat_buoc,co_dieu_kien_ap_dung) VALUES(@policy,N'TLX',N'Đối chiếu căn cứ synthetic',1,0);DECLARE @criterion bigint=SCOPE_IDENTITY();
UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'locked',thoi_diem_khoa=SYSUTCDATETIME() WHERE id=@policy;
COMMIT;
SELECT @semester semesterId,@policy policyVersionId,@parent policyId,@req requirementId,@criterion criterionId,@sv studentId,@user studentUserId,@actor actorId,@process processId,@ethnic ethnicityId,@unit unitId,@cohort cohortId,@class classId;
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'seed'){
  foreach($k in @('roundId','policyVersionId','requirementId','criterionId','semesterId','studentId','studentUserId','actorId','processId','ethnicityId','unitId','cohortId','classId')){$p[$k]=[long]$data.$k}
  $p.label=[string]$data.label
  if($p.label -notmatch '^[A-Z][A-Z0-9-]{0,15}$'){throw 'Owned label required'}
  Query @'
SET XACT_ABORT ON;BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.mghp_dot_xet WHERE id=@roundId AND ma_dot_xet LIKE @run+N'%') THROW 51932,N'Round outside owned fixture',1;
DECLARE @parent bigint=(SELECT chinh_sach_id FROM dbo.mghp_phien_ban_chinh_sach WHERE id=@policyVersionId AND ten_tai_thoi_diem_chot LIKE N'%'+@run);
IF @parent IS NULL THROW 51933,N'Policy outside owned fixture',1;
DECLARE @process bigint;
INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,thoi_diem_bat_dau) VALUES(@studentId,@cohortId,@semesterId,@run+N'-'+@label,N'verified','20260901');SET @process=SCOPE_IDENTITY();
INSERT dbo.mghp_ho_so(ma_ho_so,dot_xet_id,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_chinh_sach_id,trang_thai) VALUES(@run+N'-'+@label,@roundId,@studentId,@process,@parent,@policyVersionId,N'submitted');DECLARE @root bigint=SCOPE_IDENTITY();
UPDATE dbo.mghp_ho_so SET thoi_diem_cap_nhat=SYSUTCDATETIME() WHERE id=@root;
INSERT dbo.mghp_phien_ban_ho_so(ho_so_id,lan_sua_doi,nguoi_nop_id,thoi_diem_nop,loai_lan_nop,dot_tiep_nhan_id,phien_ban_chinh_sach_ke_khai_id,ma_thao_tac_nop,ho_ten_khai_bao,so_cccd_khai_bao,ngay_sinh_khai_bao,noi_sinh_khai_bao,gioi_tinh_khai_bao,dan_toc_khai_bao_id,lop_khai_bao_id,mssv_khai_bao,khoa_hoc_khai_bao_id,khoa_quan_ly_khai_bao_id,dia_chi_thuong_tru_khai_bao,so_dien_thoai_khai_bao)
VALUES(@root,1,@studentUserId,SYSUTCDATETIME(),N'initial',@roundId,@policyVersionId,NEWID(),N'Sinh viên lịch trình '+@label,N'000000000000','20050820',N'Đà Nẵng',N'NAM',@ethnicityId,@classId,N'DEV-SV-001',@cohortId,@unitId,N'Địa chỉ synthetic TLX',N'0900000000');DECLARE @sub bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_hoc_ky_de_nghi(phien_ban_ho_so_id,hoc_ky_id,phien_ban_chinh_sach_id) VALUES(@sub,@semesterId,@policyVersionId);
-- Metadata-only source fixture, not a claim of real storage/upload/download. Never requested from Cloudinary.
INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,nguoi_so_huu_id,kieu_phan_phoi,kich_thuoc_tep,sha256,pham_vi_hien_thi) VALUES(@run+N'-metadata-only.pdf',N'application/pdf',N'available',@studentUserId,N'authenticated',100,REPLICATE('a',64),N'minh_chung');DECLARE @file bigint=SCOPE_IDENTITY();
INSERT dbo.luot_tai_tep(id,nguoi_dung_id,ten_cloud,ma_cong_khai,loai_tep,kich_thuoc,sha256,trang_thai,tep_id,ma_tai_nguyen,doi_soat_sau) VALUES(NEWID(),@studentUserId,N'synthetic-no-storage',@run+N'-'+@label,N'evidence',100,REPLICATE('a',64),N'committed',@file,@run+N'-'+@label,'99991231');
INSERT dbo.mghp_giay_to_ho_so(phien_ban_ho_so_id,giay_to_bat_buoc_id,nguoi_nop_id) VALUES(@sub,@requirementId,@studentUserId);DECLARE @doc bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_tep_minh_chung(giay_to_ho_so_id,tep_dinh_kem_id) VALUES(@doc,@file);
COMMIT; SELECT @root rootId,@sub submissionId,@doc documentId,@process processId,@file fileId;
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'state'){
  $p.id=[long]$data.id;$p.state=[string]$data.state
  if($p.state -notin @('completed','expiredFixture','reviewOverdueFixture','futureFixture')){throw 'Only explicit lifecycle fixture operations allowed'}
  Query @'
IF NOT EXISTS(SELECT 1 FROM dbo.mghp_dot_xet WHERE id=@id AND ma_dot_xet LIKE @run+N'%') THROW 51932,N'Round outside owned fixture',1;
IF @state=N'completed' UPDATE dbo.mghp_dot_xet SET trang_thai=N'completed' WHERE id=@id;
ELSE UPDATE dbo.mghp_dot_xet SET thoi_diem_mo=DATEADD(day,-2,SYSUTCDATETIME()),han_nop=DATEADD(day,-1,SYSUTCDATETIME()) WHERE id=@id;
IF @state=N'reviewOverdueFixture' UPDATE dbo.mghp_dot_xet SET han_ra_soat=DATEADD(hour,-12,SYSUTCDATETIME()) WHERE id=@id;
IF @state=N'futureFixture' UPDATE dbo.mghp_dot_xet SET thoi_diem_mo=DATEADD(day,1,SYSUTCDATETIME()),han_nop=DATEADD(day,2,SYSUTCDATETIME()),han_ra_soat=DATEADD(day,15,SYSUTCDATETIME()) WHERE id=@id;
SELECT id,trang_thai status,han_nop deadline FROM dbo.mghp_dot_xet WHERE id=@id;
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'snapshot'){
  Query @'
SELECT id,ma_dot_xet code,trang_thai status,han_nop submissionDeadline,han_ra_soat reviewDeadline,thoi_diem_dong_xet reviewClosedAt,nguoi_dong_xet_id reviewClosedBy,sha256_nguon_dong_xet sourceHash FROM dbo.mghp_dot_xet WHERE ma_dot_xet LIKE @run+N'%' ORDER BY id;
SELECT h.id,h.ma_ho_so code,h.trang_thai status,v.id submissionId,v.thoi_diem_nop submittedAt FROM dbo.mghp_ho_so h JOIN dbo.mghp_phien_ban_ho_so v ON v.ho_so_id=h.id JOIN dbo.mghp_dot_xet d ON d.id=h.dot_xet_id WHERE d.ma_dot_xet LIKE @run+N'%';
SELECT e.id,e.ho_so_nguon_id rootId,e.dot_xet_id roundId,e.trang_thai_xu_ly status,e.ket_qua result,e.la_ban_hien_hanh currentRevision,e.ly_do_ket_luan reason FROM dbo.mghp_danh_gia_dieu_kien e JOIN dbo.mghp_dot_xet d ON d.id=e.dot_xet_id WHERE d.ma_dot_xet LIKE @run+N'%';
SELECT l.dot_xet_id roundId,l.lan_thay_doi revision,l.ly_do reason,l.nguoi_thao_tac_id actorId,l.thoi_diem_ghi_nhan recordedAt FROM dbo.mghp_lich_su_lich_trinh l JOIN dbo.mghp_dot_xet d ON d.id=l.dot_xet_id WHERE d.ma_dot_xet LIKE @run+N'%';
SELECT l.id,l.dot_xet_id roundId,l.lan_thay_doi revision,l.loai_thay_doi action,l.ly_do reason,l.nguoi_thao_tac_id actorId,l.han_nop submissionDeadline,l.han_ra_soat reviewDeadline,l.thoi_diem_ghi_nhan recordedAt,l.nhat_ky_thao_tac_id auditId,a.hanh_dong auditAction,a.nguoi_dung_id auditActorId,a.ly_do auditReason FROM dbo.mghp_lich_su_lich_trinh l JOIN dbo.mghp_dot_xet d ON d.id=l.dot_xet_id LEFT JOIN dbo.nhat_ky_thao_tac a ON a.id=l.nhat_ky_thao_tac_id WHERE d.ma_dot_xet LIKE @run+N'%' ORDER BY l.dot_xet_id,l.lan_thay_doi;
SELECT N'P10 artifacts' checkName,(SELECT COUNT(*) FROM dbo.mghp_quyet_dinh) decisions,(SELECT COUNT(*) FROM dbo.mghp_bien_ban) minutes,(SELECT COUNT(*) FROM dbo.van_ban) documents,(SELECT COUNT(*) FROM dbo.phien_ban_van_ban) documentVersions;
SELECT y.id,y.ho_so_id rootId,y.dot_tiep_nhan_id receivingRoundId,y.trang_thai status,y.han_xu_ly deadline FROM dbo.mghp_yeu_cau_bo_sung y JOIN dbo.mghp_dot_xet d ON d.id=y.dot_tiep_nhan_id WHERE d.ma_dot_xet LIKE @run+N'%';
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }elseif($Action -eq 'receiving'){
  $p.rootId=[long]$data.rootId
  Query @'
SET XACT_ABORT ON;BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.mghp_ho_so WHERE id=@rootId AND ma_ho_so LIKE @run+N'%') THROW 51932,N'Root outside owned fixture',1;
-- Fixture only: not an actual legal award or P10 command. Used solely to exercise receiving-round guard.
INSERT dbo.mghp_quyen_huong(ma_quyen_huong,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_ho_so_nguon_id,phien_ban_chinh_sach_id,ma_pham_vi,trang_thai,ty_le_giam,ngay_cong_nhan_tu,ngay_cong_nhan_den)
SELECT @run+N'-RECEIVING-FIXTURE',h.sinh_vien_id,h.qua_trinh_hoc_id,h.chinh_sach_id,v.id,h.phien_ban_chinh_sach_id,N'hoc_phi_chuong_trinh_chinh',N'active',0.5,'20260901','20270131' FROM dbo.mghp_ho_so h JOIN dbo.mghp_phien_ban_ho_so v ON v.ho_so_id=h.id WHERE h.id=@rootId AND v.lan_sua_doi=1;
DECLARE @benefit bigint=SCOPE_IDENTITY();COMMIT;SELECT @benefit benefitId;
'@ $p|ConvertTo-Json -Depth 8 -Compress
 }
}finally{$cn.Close();$cn.Dispose()}
