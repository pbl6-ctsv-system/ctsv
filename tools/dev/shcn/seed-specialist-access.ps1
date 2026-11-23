#requires -Version 7.0
[CmdletBinding()]
param(
 [ValidateSet('status','grant','smoke')][string]$Action='status',
 [string]$ProjectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath($ProjectRoot)
. (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')
$taskHome=Get-DevHome $taskRoot
$taskConfig=Read-DevConfig $taskHome
$taskIssuer="https://127.0.0.1:$($taskConfig.BackendPort)"
$taskOrigin="https://127.0.0.1:$($taskConfig.FrontendPort)"
$taskName='dev.chuyenvien';$taskRole='shcn_chuyen_vien'
$taskMarker='shcn_dev_specialist_access_v1'
$taskPort=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
if($taskPort -lt 1 -or $taskPort -gt 65535){throw 'DEV2022 TCP port unavailable'}
$taskDb=[Data.SqlClient.SqlConnection]::new("Server=127.0.0.1,$taskPort;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5")
$script:taskTransaction=$null
function Sql([string]$text,[object[]]$values=@()){
 $c=$taskDb.CreateCommand();$c.CommandText=$text;$c.CommandTimeout=15
 if($script:taskTransaction){$c.Transaction=$script:taskTransaction}
 for($i=0;$i -lt $values.Count;$i++){[void]$c.Parameters.AddWithValue("@p$i",$(if($null -eq $values[$i]){[DBNull]::Value}else{$values[$i]}))}
 return $c
}
function Rows([string]$text,[object[]]$values=@()){
 $c=Sql $text $values
 try{$reader=$c.ExecuteReader();try{while($reader.Read()){$row=@{};for($i=0;$i -lt $reader.FieldCount;$i++){$row[$reader.GetName($i)]=$(if($reader.IsDBNull($i)){$null}else{$reader.GetValue($i)})};[pscustomobject]$row}}finally{$reader.Dispose()}}finally{$c.Dispose()}
}
function Scalar([string]$text,[object[]]$values=@()){$c=Sql $text $values;try{$c.ExecuteScalar()}finally{$c.Dispose()}}
function Exec([string]$text,[object[]]$values=@()){$c=Sql $text $values;try{[void]$c.ExecuteNonQuery()}finally{$c.Dispose()}}
function Api([string]$method,[string]$path,[string]$token='',$body=$null,[hashtable]$extra=@{}){
 $headers=@{Origin=$taskOrigin;'X-CSRF-Protection'='1'}
 if($token){$headers.Authorization="Bearer $token"}
 foreach($key in $extra.Keys){$headers[$key]=$extra[$key]}
 $requestArguments=@{Uri=$taskIssuer+$path;Method=$method;Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
 if($null -ne $body){$requestArguments.ContentType='application/json';$requestArguments.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 8 -Compress))}
 $response=Invoke-WebRequest @requestArguments
 $payload=if($response.RawContentLength -gt 0){ConvertFrom-DevResponse $response.Content}else{$null}
 return @{Status=[int]$response.StatusCode;Body=$payload;ETag=[string]$response.Headers['ETag'];Prepare=[string]$response.Headers['X-SHCN-Can-Prepare']}
}
function Expect($response,[int]$status,[string]$label){if($response.Status -ne $status){$code=if($response.Body -and $response.Body.PSObject.Properties['code']){$response.Body.code}else{'UNEXPECTED'};throw "$label HTTP $($response.Status) / $code"}}
function Login([string]$username,[string]$encrypted){$password=Open-DevSecret $encrypted;try{$r=Api POST '/api/xac-thuc/dang-nhap' '' @{username=$username;password=$password};Expect $r 200 "Login $username";return [string]$r.Body.accessToken}finally{$password=$null}}
function Identity([string]$account){
 $rows=@(Rows "SELECT u.id FROM dbo.dinh_danh_dang_nhap d JOIN dbo.nguoi_dung u ON u.id=d.nguoi_dung_id WHERE d.ma_nha_cung_cap=@p0 AND d.ma_chu_the_dang_nhap=@p1 AND d.thoi_diem_xac_minh IS NOT NULL AND u.dang_hoat_dong=1 AND u.thoi_diem_xoa IS NULL" @($taskIssuer,$account))
 if($rows.Count -ne 1){throw 'Exactly one active verified Foundation identity is required; no identity creation or takeover'}
 return [long]$rows[0].id
}
try{
 $taskDb.Open()
 $meta=@(Rows "SELECT DB_NAME() dbname,CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')) instance_name,CASE WHEN SUSER_SNAME(owner_sid)=SUSER_SNAME() THEN 1 ELSE 0 END owned,compatibility_level,collation_name FROM sys.databases WHERE database_id=DB_ID()")
 if($meta.Count -ne 1 -or $meta[0].dbname -cne 'ctsv_dev' -or $meta[0].instance_name -cne 'DEV2022' -or $meta[0].owned -ne 1 -or $meta[0].compatibility_level -ne 160 -or $meta[0].collation_name -cne 'Vietnamese_100_CI_AS_SC'){throw 'Unexpected DEV database/owner/instance/options'}
 if((Scalar "SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner'") -cne 'ctsv-local-dev-v1'){throw 'DEV owner marker missing'}
 if([int](Scalar "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE script=N'V18__shcn_draft_round.sql' AND success=1") -ne 1){throw 'Apply approved SHCN V18 separately; this script never migrates'}
 $accounts=@(Rows 'SELECT CONVERT(varchar(36),ID) account,TrangThai active FROM dbo.TaiKhoan WHERE TenDangNhap=@p0' @($taskName))
 $authRoles=@(Rows 'SELECT ID id FROM dbo.VaiTro WHERE MaVaiTro=@p0 AND TrangThai=1' @($taskRole))
 $businessRoles=@(Rows 'SELECT id FROM dbo.vai_tro WHERE ma=@p0 COLLATE Latin1_General_100_BIN2 AND dang_hoat_dong=1' @($taskRole))
 if($accounts.Count -ne 1 -or -not $accounts[0].active -or $authRoles.Count -ne 1 -or $businessRoles.Count -ne 1){throw 'Existing DEV account and canonical role catalogues required'}
 $account=[string]$accounts[0].account;$user=Identity $account;$authRole=[long]$authRoles[0].id;$businessRole=[long]$businessRoles[0].id
 $required=@('shcn.dot.read','shcn.dot.prepare','shcn.reference.read')
 $permissions=@(Rows 'SELECT c.MaChucNang code FROM dbo.VaiTro_ChucNang p JOIN dbo.ChucNang c ON c.ID=p.ChucNangID WHERE p.VaiTroID=@p0 AND c.TrangThai=1' @($authRole))
 if(@($required|Where-Object {$_ -cnotin @($permissions.code)}).Count){throw 'Official SHCN permissions missing; no permission catalogue mutation'}
 $assigned=[int](Scalar 'SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=@p0 AND VaiTroID=@p1' @($account,$authRole))
 $grants=@(Rows "SELECT id,hieu_luc_tu,hieu_luc_den,CASE WHEN (hieu_luc_tu IS NULL OR hieu_luc_tu<=SYSUTCDATETIME()) AND (hieu_luc_den IS NULL OR hieu_luc_den>SYSUTCDATETIME()) THEN 1 ELSE 0 END active FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=@p0 AND vai_tro_id=@p1 AND ma_phan_he=N'SHCN' AND don_vi_id IS NULL" @($user,$businessRole))
 $record=Scalar 'SELECT CONVERT(nvarchar(4000),value) FROM sys.extended_properties WHERE class=0 AND name=@p0' @($taskMarker)
 if($Action -eq 'status'){@{package='shcn-dev-specialist-access';version=1;account=$taskName;authRoleAssigned=($assigned -eq 1);activeGlobalGrants=@($grants|Where-Object active -eq 1).Count;permissions=$required}|ConvertTo-Json;return}
 if($Action -eq 'grant'){
  if($grants.Count -gt 1 -or $grants.Count -eq 1 -and -not $grants[0].active){throw 'Ambiguous/expired/future scope requires explicit owner review; no override'}
  if($record){$prior=$record|ConvertFrom-Json;if($prior.accountId -cne $account -or $prior.userId -ne $user -or $prior.role -cne $taskRole){throw 'Provisioning ownership mismatch'};if($assigned -ne 1 -or $grants.Count -ne 1 -or $prior.grantId -ne $grants[0].id){throw 'Previously provisioned permissions changed; no automatic regrant after revocation'}}
  $adminToken=Login 'dev.admin' $taskConfig.Accounts.admin.Password
  $adminSelf=Api GET '/api/xac-thuc/toi' $adminToken;Expect $adminSelf 200 'Verify admin'
  $adminUser=Identity ([string]$adminSelf.Body.accountId)
  if($assigned -ne 1){$current=Api GET "/api/xac-thuc/tai-khoan/$account" $adminToken;Expect $current 200 'Read target account';$r=Api PUT "/api/xac-thuc/tai-khoan/$account/vai-tro/$authRole" $adminToken $null @{'If-Match'=$current.ETag};Expect $r 200 'Grant SHCN Auth role'}
  $script:taskTransaction=$taskDb.BeginTransaction([Data.IsolationLevel]::Serializable)
  try{
   Exec "SET XACT_ABORT ON; DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'shcn.dev.specialist.access.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000; IF @r<0 THROW 51911,'SHCN DEV grant lock unavailable',1"
   $existing=@(Rows "SELECT id FROM dbo.phan_quyen_nguoi_dung WITH(UPDLOCK,HOLDLOCK) WHERE nguoi_dung_id=@p0 AND vai_tro_id=@p1 AND ma_phan_he=N'SHCN' AND don_vi_id IS NULL" @($user,$businessRole))
   if($existing.Count -eq 0){
    $grantId=[long](Scalar "INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he,don_vi_id,bao_gom_don_vi_con,hieu_luc_tu,nguoi_cap_quyen_id) VALUES(@p0,@p1,N'SHCN',NULL,0,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'),@p2);SELECT CONVERT(bigint,SCOPE_IDENTITY())" @($user,$businessRole,$adminUser))
    Exec "INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(@p0,N'user',@p1,N'SHCN_DEV_SPECIALIST_GRANTED',N'phan_quyen_nguoi_dung',@p2,N'Owner approved SHCN global scope for existing DEV specialist',TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))" @([guid]::NewGuid().ToString(),$adminUser,[string]$grantId)
   }elseif($existing.Count -eq 1){$grantId=[long]$existing[0].id}else{throw 'Duplicate SHCN global scope'}
   if(-not $record){$ledger=@{package='shcn-dev-specialist-access';version=1;accountId=$account;userId=$user;role=$taskRole;grantId=$grantId}|ConvertTo-Json -Compress;Exec 'EXEC sys.sp_addextendedproperty @name=@p0,@value=@p1' @($taskMarker,$ledger)}
   $script:taskTransaction.Commit()
  }catch{$script:taskTransaction.Rollback();throw}finally{$script:taskTransaction.Dispose();$script:taskTransaction=$null;$adminToken=$null}
 }
 $token=Login $taskName $taskConfig.Accounts.specialist.Password
 $me=Api GET '/api/xac-thuc/toi' $token;Expect $me 200 'Read specialist permissions'
 if(@($required|Where-Object {$_ -cnotin @($me.Body.functions|Where-Object active|ForEach-Object code)}).Count){throw 'SHCN functions not returned by Auth'}
 $rounds=Api GET '/api/shcn/dot-sinh-hoat?page=0&size=1' $token;Expect $rounds 200 'Read SHCN rounds'
 if($rounds.Prepare -cne 'true'){throw 'Backend did not confirm global prepare permission'}
 $semesters=Api GET '/api/shcn/tra-cuu/hoc-ky?page=0&size=1' $token;Expect $semesters 200 'Read semester lookup'
 $result=@{package='shcn-dev-specialist-access';version=1;account=$taskName;authFunctions=$required;globalScope='SHCN';roundsHttp=$rounds.Status;canPrepare=$true;semesterHttp=$semesters.Status;semesterCount=$semesters.Body.totalElements;migrationExecuted=$false}
 $folder=Join-Path $taskHome 'shcn/evidence';[void][IO.Directory]::CreateDirectory($folder);$result|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $folder 'specialist-access.json') -Encoding utf8
 $result|ConvertTo-Json
}finally{$taskDb.Dispose();$token=$null;$adminToken=$null}
