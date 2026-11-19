#requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('check-package','seed','upgrade','accounts','smoke','start','status')][string]$Action='status',
      [string]$ProjectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')),
      [switch]$UpgradeBootstrap)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Seed.Package.ps1')
$taskPackage=Read-ShcnSeedPackage $PSScriptRoot
if($Action -eq 'check-package'){
 [void](New-ShcnDevSettings $taskPackage)
 Write-Output "Package $($taskPackage.Manifest.packageId) $($taskPackage.Manifest.version): checksums/policy verified; no database connection."
 return
}
$taskRoot=[IO.Path]::GetFullPath($ProjectRoot)
if(-not(Test-Path -LiteralPath (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')) -or -not(Test-Path -LiteralPath (Join-Path $taskRoot 'src/main/java/pbl6/ctsv'))){throw 'ProjectRoot must be a CTSV checkout with its existing DEV launcher'}
. (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')
$taskHome=Get-DevHome $taskRoot
$taskEvidence=Join-Path $taskHome 'shcn/evidence'
$taskConfig=Read-DevConfig $taskHome
$taskIssuer="https://127.0.0.1:$($taskConfig.BackendPort)"
$taskOrigin="https://127.0.0.1:$($taskConfig.FrontendPort)"
$taskPort=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
if($taskPort -lt 1 -or $taskPort -gt 65535){throw 'DEV2022 TCP port unavailable'}
$taskSource=$taskPackage.Manifest.sourceCode
$taskCodes=$taskPackage.Manifest.fixtureCodes
$script:taskTx=$null
function Open-TaskDb([string]$name){
 if($name -notin @('sis_dev','ctsv_dev')){throw 'Only exact owner DEV databases allowed'}
 $c=[Data.SqlClient.SqlConnection]::new("Server=127.0.0.1,$taskPort;Database=$name;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5")
 $c.Open()
 $meta=@(Rows $c "SELECT DB_NAME() dbname,CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')) instance_name,CASE WHEN SUSER_SNAME(owner_sid)=SUSER_SNAME() THEN 1 ELSE 0 END owned,compatibility_level,collation_name FROM sys.databases WHERE database_id=DB_ID()")
 if($meta.Count -ne 1 -or $meta[0].dbname -ne $name -or $meta[0].instance_name -ne 'DEV2022' -or $meta[0].owned -ne 1 -or $meta[0].compatibility_level -ne 160 -or $meta[0].collation_name -ne 'Vietnamese_100_CI_AS_SC'){$c.Dispose();throw 'Unexpected DEV database owner/instance/compat/collation'}
 if($name -eq 'ctsv_dev' -and [int](Scalar $c "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='15' AND success=1") -ne 1){$c.Dispose();throw 'Apply CTSV V15 before seed/accounts/upgrade; no automatic migration'}
 return $c
}
function Command($c,[string]$sql,[object[]]$values=@()){
 $q=$c.CreateCommand();$q.CommandText=$sql;$q.CommandTimeout=20
 if($script:taskTx -and $script:taskTx.Connection -eq $c){$q.Transaction=$script:taskTx}
 for($i=0;$i -lt $values.Count;$i++){[void]$q.Parameters.AddWithValue("@p$i",$(if($null -eq $values[$i]){[DBNull]::Value}else{$values[$i]}))};return $q
}
function Rows($c,[string]$sql,[object[]]$values=@()){
 $q=Command $c $sql $values;try{$r=$q.ExecuteReader();try{while($r.Read()){$row=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$row[$r.GetName($i)]=$(if($r.IsDBNull($i)){$null}else{$r.GetValue($i)})};[pscustomobject]$row}}finally{$r.Dispose()}}finally{$q.Dispose()}
}
function Scalar($c,[string]$sql,[object[]]$values=@()){$q=Command $c $sql $values;try{$q.ExecuteScalar()}finally{$q.Dispose()}}
function Exec($c,[string]$sql,[object[]]$values=@()){$q=Command $c $sql $values;try{[void]$q.ExecuteNonQuery()}finally{$q.Dispose()}}
function Insert($c,[string]$sql,[object[]]$values=@()){[long](Scalar $c ($sql+';SELECT CAST(SCOPE_IDENTITY() AS bigint)') $values)}
function Ledger($c,[string]$name){$value=Scalar $c 'SELECT CONVERT(nvarchar(4000),value) FROM sys.extended_properties WHERE class=0 AND name=@p0' @($name);if($value){$value|ConvertFrom-Json -AsHashtable}else{$null}}
function Save-Ledger($c,[string]$name,$data){
 $encoded=$data|ConvertTo-Json -Depth 6 -Compress;if($encoded.Length -gt 3900){throw 'Fixture ledger too large'}
 Exec $c 'EXEC sys.sp_addextendedproperty @name=@p0,@value=@p1' @($name,$encoded)
}
function Assert-PackageVersion($c,[bool]$AllowMissing=$false){
 $record=Ledger $c 'shcn_dev_seed_package_v1'
 [void](Assert-ShcnSeedRecord $taskPackage $record $AllowMissing)
}
function Record-PackageVersion($c){
 In-TaskTransaction $c {
  Assert-PackageVersion $c $true
  if(-not(Ledger $c 'shcn_dev_seed_package_v1')){
   Save-Ledger $c 'shcn_dev_seed_package_v1' ([ordered]@{PackageId=$taskPackage.Manifest.packageId;Version=$taskPackage.Manifest.version;ManifestSHA256=$taskPackage.ManifestSHA256;SourceCode=$taskSource;RecordedAtUtc=[datetime]::UtcNow.ToString('o')})
  }
 }
}
function Upgrade-PackageVersion($c){
 In-TaskTransaction $c {
  $old=Ledger $c 'shcn_dev_seed_package_v1';[void](Assert-ShcnUpgradeRecord $taskPackage $old)
  if($old.Version -cne $taskPackage.Manifest.version){
   $next=[ordered]@{PackageId=$taskPackage.Manifest.packageId;Version=$taskPackage.Manifest.version;ManifestSHA256=$taskPackage.ManifestSHA256;SourceCode=$taskSource;UpgradedFrom=$old.Version;UpgradedAtUtc=[datetime]::UtcNow.ToString('o')}
   Exec $c 'EXEC sys.sp_updateextendedproperty @name=@p0,@value=@p1' @('shcn_dev_seed_package_v1',($next|ConvertTo-Json -Compress))
  }
 }
}
function Save-Evidence([string]$Name,$Data){
 if(-not(Test-Path -LiteralPath $taskEvidence)){New-Item -ItemType Directory -Path $taskEvidence -Force|Out-Null}
 $Data|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $taskEvidence $Name) -Encoding utf8
}
function In-TaskTransaction($c,[scriptblock]$action){
 $script:taskTx=$c.BeginTransaction([Data.IsolationLevel]::Serializable)
 try{Exec $c "SET XACT_ABORT ON;DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'shcn.dev.fixture.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;IF @r<0 THROW 51901,'DEV fixture lock unavailable',1"; &$action;$script:taskTx.Commit()}
 catch{$script:taskTx.Rollback();throw}finally{$script:taskTx.Dispose();$script:taskTx=$null}
}
function Reject-Unowned($c,[string]$table,[string]$column,[string]$code){
 # table/column names are fixed literals at call sites; all values are parameters.
 if([int](Scalar $c "SELECT COUNT(*) FROM dbo.[$table] WHERE [$column]=@p0" @($code)) -ne 0){throw "Fixture code already exists without ownership: $table/$code"}
}
function Assert-OwnedRow($c,[string]$table,[string]$key,[long]$id,[string]$column,[string]$code){
 if([int](Scalar $c "SELECT COUNT(*) FROM dbo.[$table] WHERE [$key]=@p0 AND [$column]=@p1" @($id,$code)) -ne 1){throw "Fixture ownership mismatch: $table/$code"}
}
function Seed-Sis($c){
 In-TaskTransaction $c {
  $m=Ledger $c 'shcn_dev_catalog_v1'
  if($m){
   foreach($k in @('FacultyA','FacultyB')){Assert-OwnedRow $c 'DonVi' 'ID' $m[$k] 'MaDV' $taskCodes[$k]}
   foreach($k in @('ProgramA','ProgramB')){Assert-OwnedRow $c 'Nganh' 'ID' $m[$k] 'MaNganh' $taskCodes[$k]}
   foreach($k in @('ClassA1','ClassA2','ClassB')){Assert-OwnedRow $c 'Lop' 'ID' $m[$k] 'MaLop' $taskCodes[$k]}
   foreach($k in @('StaffGV','StaffHead','StaffOther')){Assert-OwnedRow $c 'HoSoCanBo' 'ID' $m[$k] 'MaHS' $taskCodes[$k]}
   return $m
  }
  $m=[ordered]@{}
  foreach($k in @('FacultyA','FacultyB')){Reject-Unowned $c 'DonVi' 'MaDV' $taskCodes[$k];$m[$k]=Insert $c 'INSERT dbo.DonVi(MaDV,TenDV) VALUES(@p0,@p1)' @($taskCodes[$k],"SYNTHETIC DEV - Khoa $k")}
  foreach($k in @('ProgramA','ProgramB')){Reject-Unowned $c 'Nganh' 'MaNganh' $taskCodes[$k];$m[$k]=Insert $c 'INSERT dbo.Nganh(MaNganh,TenNganh,TenNganhMoi,SoHK) VALUES(@p0,@p1,@p1,8)' @($taskCodes[$k],"SYNTHETIC DEV - Chuong trinh $k")}
  foreach($k in @('ClassA1','ClassA2','ClassB')){Reject-Unowned $c 'Lop' 'MaLop' $taskCodes[$k];$faculty=if($k -eq 'ClassB'){$m.FacultyB}else{$m.FacultyA};$program=if($k -eq 'ClassB'){$m.ProgramB}else{$m.ProgramA};$m[$k]=Insert $c 'INSERT dbo.Lop(MaLop,TenLop,NganhID,KhoaQLID) VALUES(@p0,@p1,@p2,@p3)' @($taskCodes[$k],"SYNTHETIC DEV - Lop $k",$program,$faculty)}
  foreach($k in @('StaffGV','StaffHead','StaffOther')){Reject-Unowned $c 'HoSoCanBo' 'MaHS' $taskCodes[$k];$faculty=if($k -eq 'StaffOther'){$m.FacultyB}else{$m.FacultyA};$m[$k]=Insert $c 'INSERT dbo.HoSoCanBo(MaHS,DonViID,HoTen) VALUES(@p0,@p1,@p2)' @($taskCodes[$k],$faculty,"SYNTHETIC DEV - Giang vien $k")}
  Save-Ledger $c 'shcn_dev_catalog_v1' $m;return $m
 }
}
function Seed-Ctsv($c,$sis,$sisDb){
 In-TaskTransaction $c {
  $sourceRows=@(Rows $c 'SELECT id FROM dbo.he_thong_ngoai WHERE ma=@p0 AND loai_he_thong=@p1 AND dang_hoat_dong=1' @($taskSource,'SIS'))
  if($sourceRows.Count -ne 1){throw 'Verified existing DEV SIS source unavailable'}
  $sourceId=[long]$sourceRows[0].id
  $m=Ledger $c 'shcn_dev_catalog_v1'
  if($m){
   if($m.Source -ne $sourceId){
    # Repair only the initial owned fixture source, before accounts were provisioned. Reuse the actual
    # SIS registry source so a later ordinary units/classes sync resolves the same external IDs.
    Assert-OwnedRow $c 'he_thong_ngoai' 'id' $m.Source 'ma' 'DEV-SHCN-SIS'
    if(Ledger $c 'shcn_dev_identities_v1'){throw 'Source repair requires pre-account fixture state'}
    foreach($k in @('FacultyA','FacultyB')){Assert-OwnedRow $c 'don_vi' 'id' $m[$k] 'ma' $taskCodes[$k];Exec $c 'UPDATE dbo.don_vi SET he_thong_nguon_id=@p0 WHERE id=@p1 AND he_thong_nguon_id=@p2' @($sourceId,$m[$k],$m.Source)}
    foreach($k in @('ProgramA','ProgramB')){Assert-OwnedRow $c 'chuong_trinh_dao_tao' 'id' $m[$k] 'ma' $taskCodes[$k];Exec $c 'UPDATE dbo.chuong_trinh_dao_tao SET he_thong_nguon_id=@p0 WHERE id=@p1 AND he_thong_nguon_id=@p2' @($sourceId,$m[$k],$m.Source)}
    foreach($k in @('ClassA1','ClassA2','ClassB')){Assert-OwnedRow $c 'lop_sinh_hoat' 'id' $m[$k] 'ma' $taskCodes[$k];Exec $c 'UPDATE dbo.lop_sinh_hoat SET he_thong_nguon_id=@p0 WHERE id=@p1 AND he_thong_nguon_id=@p2' @($sourceId,$m[$k],$m.Source)}
    foreach($k in @('StaffGV','StaffHead','StaffOther')){Assert-OwnedRow $c 'can_bo' 'id' $m[$k] 'ma_cb' $taskCodes[$k];Exec $c 'UPDATE dbo.can_bo SET he_thong_nguon_id=@p0 WHERE id=@p1 AND he_thong_nguon_id=@p2' @($sourceId,$m[$k],$m.Source);Exec $c 'UPDATE dbo.qua_trinh_cong_tac SET he_thong_nguon_id=@p0 WHERE can_bo_id=@p1 AND he_thong_nguon_id=@p2' @($sourceId,$m[$k],$m.Source)}
    Exec $c 'UPDATE dbo.he_thong_ngoai SET dang_hoat_dong=0 WHERE id=@p0 AND ma=@p1' @($m.Source,'DEV-SHCN-SIS')
    $m.RetiredFixtureSource=$m.Source;$m.Source=$sourceId
    Exec $c 'EXEC sys.sp_updateextendedproperty @name=@p0,@value=@p1' @('shcn_dev_catalog_v1',($m|ConvertTo-Json -Depth 6 -Compress))
   }
   Assert-OwnedRow $c 'he_thong_ngoai' 'id' $m.Source 'ma' $taskSource
   foreach($k in @('FacultyA','FacultyB')){Assert-OwnedRow $c 'don_vi' 'id' $m[$k] 'ma' $taskCodes[$k]}
   foreach($k in @('ProgramA','ProgramB')){Assert-OwnedRow $c 'chuong_trinh_dao_tao' 'id' $m[$k] 'ma' $taskCodes[$k]}
   foreach($k in @('ClassA1','ClassA2','ClassB')){Assert-OwnedRow $c 'lop_sinh_hoat' 'id' $m[$k] 'ma' $taskCodes[$k]}
   foreach($k in @('StaffGV','StaffHead','StaffOther')){Assert-OwnedRow $c 'can_bo' 'id' $m[$k] 'ma_cb' $taskCodes[$k]}
   return $m
  }
  $m=[ordered]@{Source=$sourceId}
  foreach($k in @('FacultyA','FacultyB')){Reject-Unowned $c 'don_vi' 'ma' $taskCodes[$k];$m[$k]=Insert $c "INSERT dbo.don_vi(ma,ten,loai_don_vi,dang_hoat_dong,he_thong_nguon_id,ma_dinh_danh_ben_ngoai) VALUES(@p0,@p1,N'khoa',1,@p2,@p3)" @($taskCodes[$k],"SYNTHETIC DEV - Khoa $k",$m.Source,[string]$sis[$k])}
  foreach($k in @('ProgramA','ProgramB')){Reject-Unowned $c 'chuong_trinh_dao_tao' 'ma' $taskCodes[$k];$m[$k]=Insert $c 'INSERT dbo.chuong_trinh_dao_tao(ma,ten,ten_nganh_moi_nguon,so_hoc_ky,he_thong_nguon_id,ma_dinh_danh_ben_ngoai) VALUES(@p0,@p1,@p1,8,@p2,@p3)' @($taskCodes[$k],"SYNTHETIC DEV - Chuong trinh $k",$m.Source,[string]$sis[$k])}
  foreach($k in @('ClassA1','ClassA2','ClassB')){Reject-Unowned $c 'lop_sinh_hoat' 'ma' $taskCodes[$k];$faculty=if($k -eq 'ClassB'){$m.FacultyB}else{$m.FacultyA};$program=if($k -eq 'ClassB'){$m.ProgramB}else{$m.ProgramA};$m[$k]=Insert $c 'INSERT dbo.lop_sinh_hoat(ma,ten,chuong_trinh_dao_tao_id,khoa_quan_ly_id,he_thong_nguon_id,ma_dinh_danh_ben_ngoai) VALUES(@p0,@p1,@p2,@p3,@p4,@p5)' @($taskCodes[$k],"SYNTHETIC DEV - Lop $k",$program,$faculty,$m.Source,[string]$sis[$k])}
  foreach($k in @('StaffGV','StaffHead','StaffOther')){
   $source=@(Rows $sisDb 'SELECT MaHS,HoTen FROM dbo.HoSoCanBo WHERE ID=@p0' @($sis[$k]));if($source.Count -ne 1 -or $source[0].MaHS -ne $taskCodes[$k]){throw 'SIS fixture staff mapping mismatch'}
   # Explicit fixture transport uses the approved wire field; Staff API/P15 itself remains gated.
   $wire=@{staff_code=$source[0].MaHS;full_name=$source[0].HoTen}
   Reject-Unowned $c 'can_bo' 'ma_cb' $wire.staff_code
   $m[$k]=Insert $c 'INSERT dbo.can_bo(ma_cb,ho_ten,la_giang_vien,he_thong_nguon_id,can_bo_ben_ngoai_id,don_vi_nguon_id) VALUES(@p0,@p1,1,@p2,@p3,@p4)' @($wire.staff_code,$wire.full_name,$m.Source,[string]$sis[$k],$(if($k -eq 'StaffOther'){$sis.FacultyB}else{$sis.FacultyA}))
   $faculty=if($k -eq 'StaffOther'){$m.FacultyB}else{$m.FacultyA}
   [void](Insert $c "INSERT dbo.qua_trinh_cong_tac(can_bo_id,don_vi_id,trang_thai_cong_tac,hieu_luc_tu,he_thong_nguon_id) VALUES(@p0,@p1,N'DEV_ACTIVE','2020-01-01',@p2)" @($m[$k],$faculty,$m.Source))
  }
  foreach($role in @('shcn_giang_vien','shcn_truong_khoa')){$roles=@(Rows $c 'SELECT id FROM dbo.vai_tro WHERE ma=@p0 AND dang_hoat_dong=1' @($role));if($roles.Count -ne 1){throw 'Official V15 Foundation role unavailable'};$m[$role]=[long]$roles[0].id}
  Save-Ledger $c 'shcn_dev_catalog_v1' $m;return $m
 }
}
function Save-Settings {
 $settings=New-ShcnDevSettings $taskPackage
 if($settings['ctsv.shcn.phan-cong-khoa.source-code'] -cne $taskSource){throw 'Package source/policy mismatch'}
 $settings|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $taskHome 'shcn.settings.json') -Encoding utf8
}
function Request([string]$method,[string]$path,[string]$token,$body=$null,[hashtable]$extra=@{}){
 $headers=@{Origin=$taskOrigin;'X-CSRF-Protection'='1'};if($token){$headers.Authorization="Bearer $token"};foreach($key in $extra.Keys){$headers[$key]=$extra[$key]}
 $arguments=@{Uri=$taskIssuer+$path;Method=$method;Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
 if($null -ne $body){$arguments.ContentType='application/json';$arguments.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 10 -Compress))}
 $response=Invoke-WebRequest @arguments
 $parsed=if($response.RawContentLength -gt 0){ConvertFrom-DevResponse $response.Content}else{$null}
 return @{Status=[int]$response.StatusCode;Body=$parsed;ETag=([string]$response.Headers['ETag']);Location=([string]$response.Headers['Location'])}
}
function Expect($response,[int]$status,[string]$label){if($response.Status -ne $status){$code=if($response.Body.PSObject.Properties['code']){$response.Body.code}else{'UNEXPECTED'};throw "$label failed HTTP $($response.Status), code=$code"}}
function Login([string]$name,[string]$password){$response=Request 'POST' '/api/xac-thuc/dang-nhap' '' @{username=$name;password=$password};Expect $response 200 "Login $name";return [string]$response.Body.accessToken}
function Accounts($c,$sis,$catalog){
 $existing=Ledger $c 'shcn_dev_identities_v1'
 if($existing){foreach($name in @('dev.giangvien','dev.truongkhoa')){if([int](Scalar $c 'SELECT COUNT(*) FROM dbo.TaiKhoan WHERE ID=@p0 AND TenDangNhap=@p1' @($existing[$name].Account,$name)) -ne 1){throw "Owned account missing: $name"}};return $existing}
 $password=Open-DevSecret $taskConfig.Accounts.admin.Password
 $adminToken=Login 'dev.admin' $password
 $roleInfo=Request 'GET' '/api/xac-thuc/phan-quyen' $adminToken;Expect $roleInfo 200 'Read Auth roles'
 $ctsvRole=@($roleInfo.Body.roles|Where-Object code -eq 'CTSV_USER');if($ctsvRole.Count -ne 1){throw 'CTSV_USER role unavailable'}
 $accounts=@{}
 foreach($name in @('dev.giangvien','dev.truongkhoa')){
  $key=if($name -eq 'dev.giangvien'){'StaffGV'}else{'StaffHead'}
  # Reuse only a matching verified synthetic link if a previous run stopped after Auth committed.
  $found=@(Rows $c 'SELECT CONVERT(varchar(36),ID) id FROM dbo.TaiKhoan WHERE TenDangNhap=@p0' @($name))
  if($found.Count -gt 1){throw 'Ambiguous account'}
  if($found.Count -eq 0){$created=Request 'POST' '/api/xac-thuc/tai-khoan' $adminToken @{username=$name;password=$password;profileLinks=@(@{sourceSystem='SIS_MOCK';profileType='staff';sourceId=[string]$sis[$key]})};Expect $created 201 "Create $name";$account=[string]$created.Body.accountId}
  else{$account=[string]$found[0].id}
  $current=Request 'GET' "/api/xac-thuc/tai-khoan/$account" $adminToken;Expect $current 200 "Read $name"
  $matching=@($current.Body.profileLinks|Where-Object {$_.sourceSystem -eq 'SIS_MOCK' -and $_.profileType -eq 'staff' -and $_.sourceId -eq [string]$sis[$key]})
  if($matching.Count -ne 1 -or @($current.Body.profileLinks).Count -ne 1){throw "Account source link conflicts; no takeover: $name"}
  if(@($current.Body.roles|Where-Object code -eq 'CTSV_USER').Count -eq 0){$grant=Request 'PUT' "/api/xac-thuc/tai-khoan/$account/vai-tro/$($ctsvRole[0].id)" $adminToken $null @{'If-Match'=$current.ETag};Expect $grant 200 "Grant CTSV access $name"}
  $accounts[$name]=@{Account=$account;StaffKey=$key}
 }
 In-TaskTransaction $c {
  $adminUser=Scalar $c 'SELECT nguoi_dung_id FROM dbo.dinh_danh_dang_nhap WHERE ma_nha_cung_cap=@p0 AND ma_chu_the_dang_nhap=@p1 AND thoi_diem_xac_minh IS NOT NULL' @($taskIssuer,([string](Request 'GET' '/api/xac-thuc/toi' $adminToken).Body.accountId))
  if(-not $adminUser){throw 'Admin Foundation identity unavailable'}
  foreach($name in @('dev.giangvien','dev.truongkhoa')){
   $account=$accounts[$name].Account;$key=$accounts[$name].StaffKey;$email="$name.shcn@example.invalid"
   Reject-Unowned $c 'nguoi_dung' 'dia_chi_email' $email
   $user=Insert $c 'INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(@p0,@p1,1)' @($email,"SYNTHETIC DEV - $name")
   Exec $c "INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(@p0,@p1,@p2,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))" @($user,$taskIssuer,$account)
   Exec $c 'UPDATE dbo.can_bo SET nguoi_dung_id=@p0 WHERE id=@p1 AND nguoi_dung_id IS NULL' @($user,$catalog[$key])
   $role=if($name -eq 'dev.truongkhoa'){'shcn_truong_khoa'}else{'shcn_giang_vien'}
   $grant=Insert $c "INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he,don_vi_id,bao_gom_don_vi_con,hieu_luc_tu) VALUES(@p0,@p1,N'SHCN',@p2,0,'2020-01-01')" @($user,$catalog[$role],$catalog.FacultyA)
   Exec $c "INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(@p0,N'user',@p1,N'SHCN_DEV_ROLE_GRANTED',N'phan_quyen_nguoi_dung',@p2,N'Owner requested synthetic DEV lecturer/faculty-head setup',TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))" @([guid]::NewGuid().ToString(),$adminUser,[string]$grant)
   $accounts[$name].User=$user;$accounts[$name].Faculty=$catalog.FacultyA;$accounts[$name].Role=$role
  }
  Save-Ledger $c 'shcn_dev_identities_v1' $accounts
 }
 $password=$null;$adminToken=$null;return $accounts
}
function Ensure-AuthBusinessRoles($c,$sis,$catalog,$accounts){
 $done=Ledger $c 'shcn_dev_auth_roles_v1'
 if($done){foreach($name in @('dev.giangvien','dev.truongkhoa')){if($done.Accounts[$name] -cne $accounts[$name].Account){throw 'Auth role provisioning ownership mismatch'}};return}
 # Verify all targets/scopes first. Do not restore a Foundation grant deliberately removed by an admin.
 foreach($name in @('dev.giangvien','dev.truongkhoa')){
  $item=$accounts[$name];$expected=if($name -eq 'dev.truongkhoa'){'shcn_truong_khoa'}else{'shcn_giang_vien'}
  if($item.Role -cne $expected -or $item.Faculty -ne $catalog.FacultyA){throw 'Unexpected owned business role/scope'}
  $count=Scalar $c "SELECT COUNT(*) FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id WHERE p.nguoi_dung_id=@p0 AND r.ma=@p1 AND r.dang_hoat_dong=1 AND p.ma_phan_he=N'SHCN' AND p.don_vi_id=@p2 AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=SYSUTCDATETIME()) AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>SYSUTCDATETIME())" @($item.User,$expected,$item.Faculty)
  if([int]$count -ne 1){throw 'Owned DEV role/scope was changed; no automatic regrant'}
  if([int](Scalar $c 'SELECT COUNT(*) FROM dbo.can_bo WHERE id=@p0 AND nguoi_dung_id=@p1 AND ma_cb=@p2' @($catalog[$item.StaffKey],$item.User,$taskCodes[$item.StaffKey])) -ne 1){throw 'Owned lecturer identity mismatch'}
 }
 $password=Open-DevSecret $taskConfig.Accounts.admin.Password;$adminToken=Login 'dev.admin' $password;$password=$null
 $record=[ordered]@{Migration='15';Accounts=@{}}
 foreach($name in @('dev.giangvien','dev.truongkhoa')){
  $item=$accounts[$name];$role=@(Rows $c 'SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=@p0 AND TrangThai=1' @($item.Role));if($role.Count -ne 1){throw 'V15 Auth business role unavailable'}
  $current=Request 'GET' "/api/xac-thuc/tai-khoan/$($item.Account)" $adminToken;Expect $current 200 "Read owned $name"
  $matching=@($current.Body.profileLinks|Where-Object {$_.sourceSystem -eq 'SIS_MOCK' -and $_.profileType -eq 'staff' -and $_.sourceId -eq [string]$sis[$item.StaffKey]})
  if($matching.Count -ne 1){throw 'Owned Auth staff link mismatch'}
  if(@($current.Body.roles|Where-Object code -CEQ $item.Role).Count -eq 0){$grant=Request 'PUT' "/api/xac-thuc/tai-khoan/$($item.Account)/vai-tro/$($role[0].ID)" $adminToken $null @{'If-Match'=$current.ETag};Expect $grant 200 "Grant business role $name"}
  $record.Accounts[$name]=$item.Account
 }
 In-TaskTransaction $c {Save-Ledger $c 'shcn_dev_auth_roles_v1' $record};$adminToken=$null
}
function Smoke($c,$catalog,$accounts){
 $password=Open-DevSecret $taskConfig.Accounts.admin.Password
 $head=Login 'dev.truongkhoa' $password;$lecturer=Login 'dev.giangvien' $password;$admin=Login 'dev.admin' $password;$password=$null
 $checks=[Collections.Generic.List[string]]::new()
 $scope=Request 'GET' '/api/shcn/khoa/pham-vi' $head;Expect $scope 200 'Head scope'
 if(@($scope.Body.khoa).Count -ne 1 -or $scope.Body.khoa[0].maKhoa -ne $taskCodes.FacultyA -or -not $scope.Body.khoa[0].duocLapNhap){throw 'Head has unexpected faculty scope'};$checks.Add('Head: only faculty A, prepare=true')
 $classes=Request 'GET' "/api/shcn/khoa/lop?maKhoa=$($taskCodes.FacultyA)" $head;Expect $classes 200 'Class lookup';if($classes.Body.totalElements -ne 2){throw 'Expected two owned faculty A classes'};$checks.Add('Classes: exactly A1/A2')
 $teachers=Request 'GET' "/api/shcn/khoa/giang-vien?maKhoa=$($taskCodes.FacultyA)" $head;Expect $teachers 200 'Teacher lookup';if($teachers.Body.totalElements -ne 2 -or @($teachers.Body.items|Where-Object {-not $_.coTheChon}).Count -ne 0){throw 'Expected two selectable faculty A teachers'};$checks.Add('Teachers: exactly two selectable, MaHS -> ma_cb')
 Expect (Request 'GET' "/api/shcn/khoa/lop?maKhoa=$($taskCodes.FacultyB)" $head) 404 'Outside faculty';$checks.Add('Head cannot access faculty B: 404')
 Expect (Request 'GET' '/api/shcn/khoa/pham-vi' $lecturer) 403 'Lecturer cannot prepare';$checks.Add('Ordinary lecturer: 403')
 Expect (Request 'GET' '/api/shcn/khoa/pham-vi' $admin) 403 'Admin not implicitly faculty head';$checks.Add('Auth admin: 403 for SHCN')
 $draftLedger=Ledger $c 'shcn_dev_draft_v1'
 if(-not $draftLedger){
  $root=Request 'POST' '/api/shcn/khoa/phan-cong' $head @{maKhoa=$taskCodes.FacultyA;namHoc='DEV-SHCN-2026';phienBanDuKien=1;maDanhSachTruoc=$null;hieuLucTu='2026-09-01T00:00:00Z';hieuLucDen=$null;lyDoDieuChinh='SYNTHETIC DEV demonstration'};Expect $root 201 'Create DEV draft'
  $body=@{expectedVersion=$root.Body.version;hieuLucTu='2026-09-01T00:00:00Z';hieuLucDen=$null;lyDoDieuChinh='SYNTHETIC DEV demonstration';cacPhanCong=@(@{maLop=$taskCodes.ClassA1;maGiaoVien=$taskCodes.StaffGV;ghiChu='SYNTHETIC DEV'},@{maLop=$taskCodes.ClassA2;maGiaoVien=$taskCodes.StaffHead;ghiChu='SYNTHETIC DEV'})}
  $saved=Request 'PUT' "/api/shcn/khoa/phan-cong/$($root.Body.id)" $head $body;Expect $saved 200 'Save DEV draft'
  $draftLedger=@{Id=[string]$root.Body.id};In-TaskTransaction $c {Save-Ledger $c 'shcn_dev_draft_v1' $draftLedger};$checks.Add('Create and save draft through real HTTPS API')
 }
 $view=Request 'GET' "/api/shcn/khoa/phan-cong/$($draftLedger.Id)" $head;Expect $view 200 'Read DEV draft';$checks.Add('Read owned draft; repeated setup does not reset content')
 $report=[ordered]@{VerifiedAtUtc=[datetime]::UtcNow.ToString('o');Backend=$taskIssuer;Databases=@('sis_dev','ctsv_dev');Source=$taskSource;Synthetic=$true;Mapping='HoSoCanBo.MaHS -> staff_code -> can_bo.ma_cb';Accounts=$accounts;DraftId=$draftLedger.Id;Checks=$checks.ToArray();StaffApiP15='NOT_IMPLEMENTED; fixture mapping is explicit, not provider API evidence'}
 $report['SeedVersion']=$taskPackage.Manifest.version;$report['ManifestSHA256']=$taskPackage.ManifestSHA256
 Save-Evidence 'DEV_SMOKE.json' $report
 $head=$null;$lecturer=$null;$admin=$null;Write-Output "SHCN DEV smoke passed: $($checks.Count) checks; no tokens/passwords in evidence."
}

if($Action -eq 'start'){
 $guardSis=$null;$guardCtsv=$null
 try{$guardSis=Open-TaskDb 'sis_dev';$guardCtsv=Open-TaskDb 'ctsv_dev';if($UpgradeBootstrap){[void](Assert-ShcnUpgradeRecord $taskPackage (Ledger $guardSis 'shcn_dev_seed_package_v1'));[void](Assert-ShcnUpgradeRecord $taskPackage (Ledger $guardCtsv 'shcn_dev_seed_package_v1'));Save-Settings}else{Assert-PackageVersion $guardSis;Assert-PackageVersion $guardCtsv}}finally{if($guardCtsv){$guardCtsv.Dispose()};if($guardSis){$guardSis.Dispose()}}
 $settings=Join-Path $taskHome 'shcn.settings.json';if(-not(Test-Path -LiteralPath $settings)){throw 'Run seed first to prepare owned SHCN local settings'}
 $previous=[Environment]::GetEnvironmentVariable('SPRING_APPLICATION_JSON','Process')
 $combined=if($previous){$previous|ConvertFrom-Json -AsHashtable}else{@{}}
 $owned=Get-Content -LiteralPath $settings -Raw|ConvertFrom-Json -AsHashtable;foreach($key in $owned.Keys){$combined[$key]=$owned[$key]}
 try{$env:SPRING_APPLICATION_JSON=$combined|ConvertTo-Json -Depth 8 -Compress;& (Join-Path $taskRoot 'dev.ps1') start}finally{[Environment]::SetEnvironmentVariable('SPRING_APPLICATION_JSON',$previous,'Process')};exit
}
$sisDb=$null;$ctsvDb=$null
try{
 $sisDb=Open-TaskDb 'sis_dev';$ctsvDb=Open-TaskDb 'ctsv_dev'
 if($Action -eq 'upgrade'){
  [void](Assert-ShcnUpgradeRecord $taskPackage (Ledger $sisDb 'shcn_dev_seed_package_v1'));[void](Assert-ShcnUpgradeRecord $taskPackage (Ledger $ctsvDb 'shcn_dev_seed_package_v1'))
  $sis=Seed-Sis $sisDb;$catalog=Seed-Ctsv $ctsvDb $sis $sisDb;$accounts=Ledger $ctsvDb 'shcn_dev_identities_v1'
  if(-not $accounts){throw 'Legacy identities missing; no account takeover during upgrade'}
  Ensure-AuthBusinessRoles $ctsvDb $sis $catalog $accounts
  Upgrade-PackageVersion $sisDb;Upgrade-PackageVersion $ctsvDb;Save-Settings
  Write-Output 'Seed upgraded explicitly to 1.1.0; Auth business roles assigned through API, scopes/passwords/drafts preserved.';return
 }
 Assert-PackageVersion $sisDb ($Action -eq 'seed');Assert-PackageVersion $ctsvDb ($Action -eq 'seed')
 if($Action -eq 'seed'){$sis=Seed-Sis $sisDb;$catalog=Seed-Ctsv $ctsvDb $sis $sisDb;Record-PackageVersion $sisDb;Record-PackageVersion $ctsvDb;Save-Settings;Write-Output "Owned synthetic SHCN catalog ready, seed version $($taskPackage.Manifest.version); existing accounts unchanged."}
 else{
  $sis=Ledger $sisDb 'shcn_dev_catalog_v1';$catalog=Ledger $ctsvDb 'shcn_dev_catalog_v1';if(-not $sis -or -not $catalog){throw 'Run guarded seed first'}
  if($Action -eq 'accounts'){$accounts=Accounts $ctsvDb $sis $catalog;Ensure-AuthBusinessRoles $ctsvDb $sis $catalog $accounts;Save-Evidence 'DEV_ACCOUNTS.json' $accounts;Write-Output 'DEV lecturer/head Auth roles and Foundation scopes ready; passwords unchanged on repeat.'}
  elseif($Action -eq 'smoke'){$accounts=Ledger $ctsvDb 'shcn_dev_identities_v1';if(-not $accounts){throw 'Run accounts first'};Smoke $ctsvDb $catalog $accounts}
  else{[ordered]@{Package=(Ledger $ctsvDb 'shcn_dev_seed_package_v1');SisPackage=(Ledger $sisDb 'shcn_dev_seed_package_v1');Source=$taskSource;Sis=$sis;Ctsv=$catalog;Accounts=(Ledger $ctsvDb 'shcn_dev_identities_v1');Draft=(Ledger $ctsvDb 'shcn_dev_draft_v1');LocalEvidenceDirectory=$taskEvidence}|ConvertTo-Json -Depth 7}
 }
}finally{if($ctsvDb){$ctsvDb.Dispose()};if($sisDb){$sisDb.Dispose()}}
