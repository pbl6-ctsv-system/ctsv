#requires -Version 7.0
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot/../..").Path
$out=Join-Path $taskRoot 'target/review-lifecycle-20261122'
$port=(Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$cn=[Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$port;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=RLUI-owned-fixture")
$cn.Open()
function Rows([string]$sql){$cmd=$cn.CreateCommand();$cmd.CommandText=$sql;$reader=$cmd.ExecuteReader();$values=@();while($reader.Read()){$value=@{};for($i=0;$i -lt $reader.FieldCount;$i++){$value[$reader.GetName($i)]=$reader.GetValue($i)};$values+=$value};$reader.Close();$cmd.Dispose();return ,$values}
try{
 $guard=(Rows "SELECT @@SERVERNAME instance,DB_NAME() db,IIF(owner_sid=SUSER_SID(),1,0) owns,(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner') marker FROM sys.databases WHERE database_id=DB_ID()")[0]
 if($guard.instance -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard.db -ne 'ctsv_dev' -or $guard.owns -ne 1 -or $guard.marker -ne 'ctsv-local-dev-v1'){throw 'Wrong DEV target/owner/marker'}
 . "$taskRoot/tools/dev/Dev.Common.ps1"
 $state=Get-Content (Join-Path (Get-DevHome $taskRoot) 'processes.json') -Raw|ConvertFrom-Json
 if(-not (Test-DevProcessIdentity $state.Backend (Get-DevProcess $state.Backend.Pid))){throw 'Wrong launcher identity'}
 $sessions=Rows "SELECT DB_NAME(database_id) db FROM sys.dm_exec_sessions WHERE host_process_id=$([int]$state.Backend.Pid) AND is_user_process=1"
 if(-not $sessions.Count -or @($sessions|Where-Object db -ne 'ctsv_dev').Count){throw 'Launcher SQL sessions mismatch'}
 $before=Rows "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_ho_so WHERE ma_ho_so NOT LIKE N'RLUI-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha"
 if(-not(Test-Path "$out/dev-roots-before.json")){$before|ConvertTo-Json|Set-Content "$out/dev-roots-before.json"}
 $sql=@'
SET XACT_ABORT ON; BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.qua_trinh_theo_hoc WHERE id=2 AND sinh_vien_id=1 AND trang_thai_doi_chieu=N'verified') THROW 51986,N'Process not verified/owned',1;
IF NOT EXISTS(SELECT 1 FROM dbo.tep_dinh_kem f JOIN dbo.sinh_vien s ON s.nguoi_dung_id=f.nguoi_so_huu_id WHERE f.id=10028 AND s.id=1 AND f.trang_thai_luu_tru=N'available') THROW 51987,N'File not owned/available',1;
DECLARE @round bigint=(SELECT id FROM dbo.mghp_dot_xet WHERE ma_dot_xet=N'RLUI-20261122-' + @kind);
IF @round IS NULL THROW 51988,N'Create marked round through API first',1;
DECLARE @root bigint=(SELECT id FROM dbo.mghp_ho_so WHERE ma_ho_so=N'RLUI-20261122-'+@kind);
IF @root IS NULL BEGIN
 DECLARE @parent bigint=(SELECT chinh_sach_id FROM dbo.mghp_phien_ban_chinh_sach WHERE id=10013 AND trang_thai=N'locked');
 INSERT dbo.mghp_ho_so(ma_ho_so,dot_xet_id,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_chinh_sach_id,trang_thai)
 VALUES(N'RLUI-20261122-'+@kind,@round,1,2,@parent,10013,N'submitted'); SET @root=SCOPE_IDENTITY();
 INSERT dbo.mghp_phien_ban_ho_so(ho_so_id,lan_sua_doi,nguoi_nop_id,thoi_diem_nop,loai_lan_nop,dot_tiep_nhan_id,phien_ban_chinh_sach_ke_khai_id,ma_thao_tac_nop,ho_ten_khai_bao,so_cccd_khai_bao,ngay_sinh_khai_bao,noi_sinh_khai_bao,gioi_tinh_khai_bao,dan_toc_khai_bao_id,lop_khai_bao_id,mssv_khai_bao,khoa_hoc_khai_bao_id,khoa_quan_ly_khai_bao_id,dia_chi_thuong_tru_khai_bao,so_dien_thoai_khai_bao)
 SELECT @root,1,nguoi_nop_id,SYSDATETIMEOFFSET(),N'initial',@round,10013,NEWID(),ho_ten_khai_bao,so_cccd_khai_bao,ngay_sinh_khai_bao,noi_sinh_khai_bao,gioi_tinh_khai_bao,dan_toc_khai_bao_id,lop_khai_bao_id,mssv_khai_bao,khoa_hoc_khai_bao_id,khoa_quan_ly_khai_bao_id,dia_chi_thuong_tru_khai_bao,so_dien_thoai_khai_bao FROM dbo.mghp_phien_ban_ho_so WHERE id=10033;
 DECLARE @sub bigint=SCOPE_IDENTITY();
 INSERT dbo.mghp_hoc_ky_de_nghi(phien_ban_ho_so_id,hoc_ky_id,phien_ban_chinh_sach_id) VALUES(@sub,4,10013);
 INSERT dbo.mghp_doi_chieu_ke_khai(phien_ban_ho_so_id,ma_truong,gia_tri_khai_bao,gia_tri_nguon,ket_qua_doi_chieu,thoi_diem_lay_nguon)
 SELECT @sub,ma_truong,gia_tri_khai_bao,gia_tri_nguon,ket_qua_doi_chieu,thoi_diem_lay_nguon FROM dbo.mghp_doi_chieu_ke_khai WHERE phien_ban_ho_so_id=10033;
 INSERT dbo.mghp_giay_to_ho_so(phien_ban_ho_so_id,giay_to_bat_buoc_id,nguoi_nop_id,so_giay_to,don_vi_cap,ngay_cap,ngay_het_han)
 SELECT @sub,10020,nguoi_nop_id,N'RLUI-SYNTHETIC',N'Cơ quan thử nghiệm','20260901','20270131' FROM dbo.mghp_phien_ban_ho_so WHERE id=@sub;
 DECLARE @doc bigint=SCOPE_IDENTITY();INSERT dbo.mghp_tep_minh_chung(giay_to_ho_so_id,tep_dinh_kem_id) VALUES(@doc,10028);
END;
COMMIT;
SELECT @root rootId,@round roundId,v.id submissionId,d.id documentId FROM dbo.mghp_phien_ban_ho_so v JOIN dbo.mghp_giay_to_ho_so d ON d.phien_ban_ho_so_id=v.id WHERE v.ho_so_id=@root;
'@
 $all=@()
 foreach($kind in @('DAT','FAIL')){$query="DECLARE @kind nvarchar(8)=N'$kind';"+$sql;$all+=Rows $query}
 @{guard=$guard;sessions=$sessions;fixtures=$all;fileReuse=10028;originSubmission=10033;mode='REAL DEV; existing synthetic PDF, no upload'}|ConvertTo-Json -Depth 5|Set-Content "$out/dev-fixture.json"
 $all|ConvertTo-Json -Depth 4
}finally{$cn.Close()}
