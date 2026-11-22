#requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('plan','seed','status')][string]$Action='plan',
 [string]$ProjectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')))
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
# Separate DEV fixture package, v1.0.0. Never calls Flyway, setup, reset or auth provisioning.
$package='ctsv.shcn.dev.inheritance'
$version='1.0.0'
$property='shcn_dev_inheritance_v1'
$faculty='DEV-SHCN-KHOA-A'
$year='2025-2026'
$reason='SYNTHETIC DEV - Phân công năm 2025-2026 để thực hành kế thừa. Trạng thái gửi chuyên viên là mô phỏng; không có ký số thật.'
$root=[IO.Path]::GetFullPath($ProjectRoot)
if(-not(Test-Path -LiteralPath (Join-Path $root 'tools/dev/shcn/seed-manifest.json'))){throw 'Expected CTSV checkout with existing SHCN DEV fixture package.'}
. (Join-Path $root 'tools/dev/shcn/Seed.Package.ps1')
[void](Read-ShcnSeedPackage (Join-Path $root 'tools/dev/shcn'))
$port=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
if($port -lt 1 -or $port -gt 65535){throw 'Verified DEV2022 TCP port unavailable.'}
$db=[Data.SqlClient.SqlConnection]::new("Server=127.0.0.1,$port;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5")
$script:tx=$null
function Query([string]$sql,[object[]]$values=@()){
 $cmd=$db.CreateCommand();$cmd.CommandText=$sql;$cmd.CommandTimeout=15
 if($script:tx){$cmd.Transaction=$script:tx}
 for($i=0;$i -lt $values.Count;$i++){[void]$cmd.Parameters.AddWithValue("@p$i",$(if($null -eq $values[$i]){[DBNull]::Value}else{$values[$i]}))}
 try{$reader=$cmd.ExecuteReader();try{while($reader.Read()){$record=[ordered]@{};for($i=0;$i -lt $reader.FieldCount;$i++){$record[$reader.GetName($i)]=$(if($reader.IsDBNull($i)){$null}else{$reader.GetValue($i)})};[pscustomobject]$record}}finally{$reader.Dispose()}}finally{$cmd.Dispose()}
}
function Ledger([string]$name){$rows=@(Query 'SELECT CONVERT(nvarchar(4000),value) value FROM sys.extended_properties WHERE class=0 AND name=@p0' @($name));if($rows.Count){return ($rows[0].value|ConvertFrom-Json -AsHashtable)};return $null}
try{
 $db.Open()
 $meta=@(Query "SELECT DB_NAME() dbname,CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')) instance_name,CASE WHEN SUSER_SNAME(owner_sid)=SUSER_SNAME() THEN 1 ELSE 0 END owned,compatibility_level,collation_name FROM sys.databases WHERE database_id=DB_ID()")
 if($meta.Count -ne 1 -or $meta[0].dbname -cne 'ctsv_dev' -or $meta[0].instance_name -cne 'DEV2022' -or $meta[0].owned -ne 1 -or $meta[0].compatibility_level -ne 160 -or $meta[0].collation_name -cne 'Vietnamese_100_CI_AS_SC'){throw 'DEV database target/owner/compatibility/collation mismatch.'}
 $migration=@(Query "SELECT version FROM dbo.flyway_schema_history WHERE version IN ('14','15') AND success=1")
 if($migration.Count -ne 2){throw 'SHCN schema/permission migrations must already exist; this tool never migrates.'}
 $catalog=Ledger 'shcn_dev_catalog_v1';$identities=Ledger 'shcn_dev_identities_v1'
 if(-not $catalog -or -not $identities){throw 'Existing owned SHCN DEV catalog/accounts required.'}
 $head=$identities['dev.truongkhoa']
 if(-not $head -or $head.Faculty -ne $catalog.FacultyA){throw 'Owned faculty head scope mismatch.'}
 $valid=@(Query 'SELECT id FROM dbo.don_vi WHERE id=@p0 AND ma=@p1 AND dang_hoat_dong=1' @($catalog.FacultyA,$faculty))
 if($valid.Count -ne 1){throw 'Owned faculty missing or changed.'}
 $lines=@(@{Class='DEV-SHCN-LOP-A1';ClassKey='ClassA1';Teacher='DEV-SHCN-GV-001';TeacherKey='StaffGV'},@{Class='DEV-SHCN-LOP-A2';ClassKey='ClassA2';Teacher='DEV-SHCN-TK-001';TeacherKey='StaffHead'})
 foreach($line in $lines){
  $classes=@(Query 'SELECT id FROM dbo.lop_sinh_hoat WHERE id=@p0 AND ma=@p1 AND khoa_quan_ly_id=@p2' @($catalog[$line.ClassKey],$line.Class,$catalog.FacultyA))
  $teachers=@(Query 'SELECT id FROM dbo.can_bo WHERE id=@p0 AND ma_cb=@p1' @($catalog[$line.TeacherKey],$line.Teacher))
  if($classes.Count -ne 1 -or $teachers.Count -ne 1){throw 'Owned class/teacher fixture missing or changed.'}
 }
 $created=$false
 if($Action -eq 'seed'){
  $script:tx=$db.BeginTransaction([Data.IsolationLevel]::Serializable)
  try{
   [void](Query "SET XACT_ABORT ON;DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'shcn.dev.fixture.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;IF @r<0 THROW 51901,'DEV fixture lock unavailable',1;")
   $old=Ledger $property
   if(-not $old){
    # Never convert an existing user draft to a signed/sent source. Insert our own new aggregate only.
    $source=@(Query @'
DECLARE @next int=(SELECT COALESCE(MAX(phien_ban),0)+1 FROM shcn.shcn_danh_sach_phan_cong_khoa WITH(UPDLOCK,HOLDLOCK) WHERE ma_khoa=@p0 AND nam_hoc=@p1);
INSERT shcn.shcn_danh_sach_phan_cong_khoa(ma_khoa,nam_hoc,phien_ban,hieu_luc_tu,hieu_luc_den,trang_thai,ma_nguoi_lap,ly_do_dieu_chinh,ngay_tao,ngay_cap_nhat)
VALUES(@p0,@p1,@next,'2025-08-01T00:00:00+07:00','2026-08-01T00:00:00+07:00','NHAP',@p2,@p3,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET());
SELECT CONVERT(bigint,SCOPE_IDENTITY()) id,@next phien_ban;
'@ @($faculty,$year,[string]$head.Account,$reason))
    if($source.Count -ne 1){throw 'Fixture insert returned unexpected identity.'}
    $id=[long]$source[0].id
    foreach($line in $lines){[void](Query "INSERT shcn.shcn_phan_cong_gvcn_khoa(ma_danh_sach_khoa,ma_lop,ma_giao_vien,ghi_chu,ngay_tao,ngay_cap_nhat) VALUES(@p0,@p1,@p2,N'SYNTHETIC DEV - Dòng mẫu kế thừa năm trước',SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())" @($id,$line.Class,$line.Teacher))}
    # This is fixture data, not an application transition or a real signing/integration result.
    [void](Query "UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_GUI_CHUYEN_VIEN',ngay_gui_chuyen_vien=SYSDATETIMEOFFSET(),ngay_cap_nhat=SYSDATETIMEOFFSET() WHERE id=@p0 AND trang_thai='NHAP' AND ly_do_dieu_chinh=@p1" @($id,$reason))
    [void](Query "INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(NEWID(),N'dev_seed',N'SHCN_DEV_INHERITANCE_SOURCE_SEEDED',N'shcn_danh_sach_phan_cong_khoa',@p0,@p1,SYSDATETIMEOFFSET())" @([string]$id,$reason))
    $ledger=@{PackageId=$package;Version=$version;Id=[string]$id;Faculty=$faculty;Year=$year;AssignmentVersion=[int]$source[0].phien_ban;CreatedAtUtc=[datetime]::UtcNow.ToString('o')}
    [void](Query 'EXEC sys.sp_addextendedproperty @name=@p0,@value=@p1' @($property,($ledger|ConvertTo-Json -Compress)))
    $created=$true
   }
   $script:tx.Commit()
  }catch{$script:tx.Rollback();throw}finally{$script:tx.Dispose();$script:tx=$null}
 }
 $ledger=Ledger $property
 if($ledger){
  if($ledger.PackageId -cne $package -or $ledger.Version -cne $version -or $ledger.Faculty -cne $faculty -or $ledger.Year -cne $year){throw 'Inheritance fixture ledger mismatch; no automatic replacement.'}
  $source=@(Query "SELECT id,ma_khoa,nam_hoc,phien_ban,trang_thai,ly_do_dieu_chinh,(SELECT COUNT(*) FROM shcn.shcn_phan_cong_gvcn_khoa c WHERE c.ma_danh_sach_khoa=r.id) so_lop FROM shcn.shcn_danh_sach_phan_cong_khoa r WHERE id=@p0 AND ma_khoa=@p1 AND nam_hoc=@p2" @([long]$ledger.Id,$faculty,$year))
  if($source.Count -ne 1 -or $source[0].ly_do_dieu_chinh -cne $reason -or $source[0].trang_thai -notin @('DA_KY','DA_GUI_CHUYEN_VIEN','DA_THAY_THE') -or $source[0].so_lop -ne 2){throw 'Owned source was changed; preserve user state, do not recreate.'}
  [ordered]@{Package=$package;Version=$version;Database='ctsv_dev';Created=$created;SourceId=[string]$source[0].id;Faculty=$faculty;SourceYear=$year;AssignmentVersion=$source[0].phien_ban;Status=$source[0].trang_thai;Classes=$source[0].so_lop;Synthetic=$true;RealSigning=$false;TargetYear='2026-2027';ExistingDraftsPreserved=$true}|ConvertTo-Json
 }else{[ordered]@{Action=$Action;Database='ctsv_dev';WillCreate='New synthetic source only; no existing draft edits';Faculty=$faculty;SourceYear=$year;TargetYear='2026-2027';Classes=2;Status='DA_GUI_CHUYEN_VIEN (simulated)';Migrations='Never'}|ConvertTo-Json}
}finally{$db.Dispose()}
