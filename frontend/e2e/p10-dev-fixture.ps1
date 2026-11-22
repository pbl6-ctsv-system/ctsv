#requires -Version 7.0
param([ValidateSet('Inspect','Prepare','Resume','Verify')][string]$Action='Inspect')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot/../..").Path
$out=Join-Path $taskRoot 'target/p10-ui-20261122'
New-Item -ItemType Directory -Path $out -Force|Out-Null
$run='P10UI-20261122'
$sqlPort=(Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$cn=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$sqlPort;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=P10UI-owned-fixture")
$cn.Open()
function Rows([string]$sql,[hashtable]$p=@{}){
 $cmd=$cn.CreateCommand();$cmd.CommandText=$sql;$cmd.CommandTimeout=45
 foreach($k in $p.Keys){[void]$cmd.Parameters.AddWithValue('@'+$k,$p[$k])}
 $r=$cmd.ExecuteReader();$list=[Collections.Generic.List[object]]::new()
 do{while($r.Read()){$x=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$v=$r.GetValue($i);$x[$r.GetName($i)]=if($v -is [DBNull]){$null}elseif($v -is [datetimeoffset]){$v.ToString('o')}else{$v}};$list.Add([pscustomobject]$x)}}while($r.NextResult())
 $r.Close();$cmd.Dispose();return ,$list.ToArray()
}
function Save { $script:manifest|ConvertTo-Json -Depth 12|Set-Content (Join-Path $out 'fixture.json') }
function Protected {
 return Rows @'
SELECT N'rounds' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_dot_xet WHERE ma_dot_xet NOT LIKE N'P10UI-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
SELECT N'applications' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_ho_so WHERE ma_ho_so NOT LIKE N'P10UI-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
SELECT N'evaluations' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT e.* FROM dbo.mghp_danh_gia_dieu_kien e LEFT JOIN dbo.mghp_dot_xet d ON d.id=e.dot_xet_id WHERE d.id IS NULL OR d.ma_dot_xet NOT LIKE N'P10UI-%' ORDER BY e.id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
SELECT N'policies' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_phien_ban_chinh_sach WHERE ten_tai_thoi_diem_chot NOT LIKE N'%P10UI-%' ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
SELECT N'account_credentials' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT ID,TenDangNhap,MatKhauHash FROM dbo.TaiKhoan ORDER BY ID FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
SELECT N'old_decisions' entity,CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT q.* FROM dbo.mghp_quyet_dinh q LEFT JOIN dbo.mghp_dot_xet d ON d.id=q.dot_xet_id WHERE d.id IS NULL OR d.ma_dot_xet NOT LIKE N'P10UI-%' ORDER BY q.id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) sha;
'@
}
try{
 $guard=Rows "SELECT @@SERVERNAME serverName,DB_NAME() databaseName,IIF(owner_sid=SUSER_SID(),1,0) owns FROM sys.databases WHERE database_id=DB_ID();SELECT CONVERT(nvarchar(128),value) marker FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner';SELECT MAX(TRY_CONVERT(int,version)) version,MIN(CONVERT(int,success)) success FROM dbo.flyway_schema_history"
 if($guard[0].serverName -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard[0].databaseName -ne 'ctsv_dev' -or $guard[0].owns -ne 1 -or $guard[1].marker -ne 'ctsv-local-dev-v1' -or $guard[2].version -ne 23 -or $guard[2].success -ne 1){throw 'Wrong instance/DB/owner/marker/Flyway'}
 . "$taskRoot/tools/dev/Dev.Common.ps1"
 $homePath=Get-DevHome $taskRoot
 $state=Get-Content (Join-Path $homePath 'processes.json') -Raw|ConvertFrom-Json
 if(-not (Test-DevProcessIdentity $state.Backend (Get-DevProcess $state.Backend.Pid))){throw 'Launcher backend identity mismatch'}
 $sessions=Rows 'SELECT session_id,DB_NAME(database_id) databaseName FROM sys.dm_exec_sessions WHERE host_process_id=@pid AND is_user_process=1' @{pid=[int]$state.Backend.Pid}
 if(-not @($sessions|Where-Object databaseName -eq 'ctsv_dev').Count -or @($sessions|Where-Object databaseName -ne 'ctsv_dev').Count){throw 'Backend not verified against ctsv_dev'}
 @{guard=$guard;backendPid=$state.Backend.Pid;sessions=$sessions;mode='REAL SQL/launcher'}|ConvertTo-Json -Depth 6|Set-Content "$out/guard.json"
 if($Action -eq 'Inspect'){Write-Output 'Guard PASS: localhost\DEV2022 / ctsv_dev V23, owner/marker and launcher SQL sessions verified.';return}
 if($Action -eq 'Verify'){
  $before=Get-Content "$out/protected-before.json" -Raw|ConvertFrom-Json
  $after=Protected
  if(($before|ConvertTo-Json -Compress) -ne ($after|ConvertTo-Json -Compress)){throw 'Non-P10UI data changed'}
  $after|ConvertTo-Json -Depth 5|Set-Content "$out/protected-after.json"
  Rows "SELECT q.id rootId,q.ma_qd code,v.id versionId,v.lan_sua_doi revision,v.trang_thai status,v.so_dong lineCount,v.thoi_diem_chot lockedAt FROM dbo.mghp_quyet_dinh q JOIN dbo.mghp_dot_xet d ON d.id=q.dot_xet_id JOIN dbo.mghp_phien_ban_qd v ON v.quyet_dinh_id=q.id WHERE d.ma_dot_xet LIKE @prefix ORDER BY q.id,v.lan_sua_doi" @{prefix=$run+'%'}|ConvertTo-Json -Depth 5|Set-Content "$out/persisted.json"
  Write-Output 'Protected non-P10UI data unchanged; P10 versions persisted.';return
 }
 if(Test-Path "$out/fixture.json"){
  $existing=Get-Content "$out/fixture.json" -Raw|ConvertFrom-Json
  if($existing.stage -ne 'complete' -and $Action -ne 'Resume'){throw 'Partial P10UI fixture exists. Inspect recorded state before any retry.'}
  foreach($r in $existing.rounds){if((Rows 'SELECT id FROM dbo.mghp_dot_xet WHERE id=@id AND ma_dot_xet LIKE @prefix' @{id=[long]$r.id;prefix=$run+'%'}).Count -ne 1){throw 'Recorded fixture no longer owned'}}
  if($existing.stage -eq 'complete'){Write-Output "Reused complete marked P10UI fixture: $out/fixture.json";return}
 }
 if($Action -eq 'Resume'){$manifest=Get-Content "$out/fixture.json" -Raw|ConvertFrom-Json -AsHashtable;$refs=$manifest.refs}
 else {
 Protected|ConvertTo-Json -Depth 5|Set-Content "$out/protected-before.json"
 $refs=(Rows @'
SET XACT_ABORT ON;BEGIN TRAN;
IF EXISTS(SELECT 1 FROM dbo.hoc_ky WHERE ma=@run) THROW 51980,N'Partial fixture exists; inspect before retry',1;
DECLARE @actor bigint=(SELECT n.id FROM dbo.nguoi_dung n JOIN dbo.dinh_danh_dang_nhap d ON d.nguoi_dung_id=n.id JOIN dbo.TaiKhoan a ON CONVERT(nvarchar(36),a.ID)=d.ma_chu_the_dang_nhap WHERE a.TenDangNhap=N'dev.chuyenvien');
IF @actor IS NULL THROW 51981,N'DEV specialist missing',1;
INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc)
SELECT @run,N'Học kỳ I 2026–2027 · Thực hành Hội đồng',COALESCE(MAX(so_thu_tu),0)+1,'20260901','20270131',N'verified',N'SYNTHETIC P10UI calendar' FROM dbo.hoc_ky;
DECLARE @semester bigint=SCOPE_IDENTITY();
INSERT dbo.dan_toc(ten) VALUES(N'Dân tộc synthetic '+@run);DECLARE @ethnic bigint=SCOPE_IDENTITY();
INSERT dbo.don_vi(ma,ten) VALUES(@run,N'Khoa thực hành Hội đồng');DECLARE @unit bigint=SCOPE_IDENTITY();
INSERT dbo.khoa_hoc(ma) VALUES(@run);DECLARE @cohort bigint=SCOPE_IDENTITY();
INSERT dbo.lop_sinh_hoat(ma,khoa_hoc_id,khoa_quan_ly_id) VALUES(N'23T-P10',@cohort,@unit);DECLARE @class bigint=SCOPE_IDENTITY();
COMMIT;SELECT @actor actorId,@semester semesterId,@ethnic ethnicityId,@unit unitId,@cohort cohortId,@class classId;
'@ @{run=$run})[0]
 $manifest=@{run=$run;stage='references';refs=$refs;policies=@();rounds=@();sources=@();external='Metadata-only synthetic evidence; no provider calls'};Save
 foreach($spec in @(@{code='M2';rate='1';name='Sinh viên khuyết tật'},@{code='M8';rate='0.5';name='Con cán bộ đang công tác'},@{code='M9';rate='0.25';name='Con cán bộ đã nghỉ hưu'})){
  $p=(Rows @'
SET XACT_ABORT ON;BEGIN TRAN;
DECLARE @parent bigint=(SELECT id FROM dbo.mghp_chinh_sach WHERE ma=@code);
INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,ma_pham_vi,ngay_bat_dau_hieu_luc,trang_thai,tinh_trang_xac_nhan_quy_tac,uu_tien_khi_cung_muc)
SELECT @parent,COALESCE(MAX(phien_ban),0)+1,N'[SYNTHETIC] '+@name+N' · '+@run,@rate,N'hoc_phi_chuong_trinh_chinh','20260101',N'draft',N'da_doi_chieu_nguon',1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=@parent;
DECLARE @policy bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_nhom_yeu_cau_giay_to(phien_ban_chinh_sach_id,ma,ten,so_yeu_cau_toi_thieu_dat) VALUES(@policy,N'P10UI',N'Căn cứ thực hành',1);DECLARE @g bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung) VALUES(@g,N'P10UI',N'Giấy xác nhận synthetic',N'xac_nhan',N'sinh_vien');DECLARE @req bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_tieu_chi_chinh_sach(phien_ban_chinh_sach_id,ma,ten,bat_buoc,co_dieu_kien_ap_dung) VALUES(@policy,N'P10UI',N'Đủ căn cứ synthetic',1,0);DECLARE @criterion bigint=SCOPE_IDENTITY();
UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'locked',thoi_diem_khoa=SYSDATETIMEOFFSET() WHERE id=@policy;
COMMIT;SELECT @policy policyVersionId,@req requirementId,@criterion criterionId,@parent parentId;
'@ @{code=$spec.code;rate=[decimal]$spec.rate;name=$spec.name;run=$run})[0]
  $manifest.policies+=@{code=$spec.code;ids=$p};Save
 }
 }
 $cfg=Read-DevConfig $homePath
 $login=Invoke-WebRequest 'https://127.0.0.1:8443/api/xac-thuc/dang-nhap' -Method POST -ContentType 'application/json' -Headers @{Origin='https://127.0.0.1:5173';'X-CSRF-Protection'='1'} -Body (@{username=$cfg.Accounts.specialist.Username;password=(Open-DevSecret $cfg.Accounts.specialist.Password)}|ConvertTo-Json)
 $token=(ConvertFrom-DevResponse $login).accessToken
 function Api([string]$method,[string]$path,$body=$null,[string]$tag=''){
  $h=@{Authorization='Bearer '+$token};if($tag){$h['If-Match']=$tag}
  $params=@{Uri=('https://127.0.0.1:8443/api/ctsv/mghp'+$path);Method=$method;Headers=$h;SkipHttpErrorCheck=$true}
  if($null -ne $body){$params.ContentType='application/json; charset=utf-8';$params.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 12 -Compress))}
  $r=Invoke-WebRequest @params
  if($r.StatusCode -notin @(200,201)){throw "Fixture $method $path HTTP $($r.StatusCode) $((ConvertFrom-DevResponse $r).code)"}
  return @{data=(ConvertFrom-DevResponse $r);tag=$(if($r.Headers.ContainsKey('ETag')){[string]$r.Headers['ETag'][0]}else{''})}
 }
 foreach($kind in @('MAIN','STALE')){
  $now=[datetimeoffset]::UtcNow
  $body=@{code=$run+'-'+$kind;semesterId=[string]$refs.semesterId;name=$(if($kind -eq 'MAIN'){'Hội đồng MGHP học kỳ I · Thực hành P10'}else{'Hội đồng MGHP · Thử hai tab P10'});description='SYNTHETIC P10UI; chỉ dữ liệu thực hành';opensAt=$now.AddDays(-1).ToString('o');submissionDeadline=$now.AddDays(2).ToString('o');reviewDeadline=$now.AddDays(5).ToString('o');allowRetroactive=$true;retroactiveMode='theo_phap_luat';midSemesterMode='khoang_ngay_chuyen_vien_xac_nhan';policyVersions=@($manifest.policies|ForEach-Object {@{policyVersionId=[string]$_.ids.policyVersionId;applicationTemplateFileId=$null;order=1}})}
  $recorded=@($manifest.rounds|Where-Object kind -eq $kind)
  if($recorded.Count){$roundId=[long]$recorded[0].id;$r=Api GET "/dot-xet/$roundId"}else{
  $r=Api POST '/dot-xet' $body;$roundId=[long]$r.data.id
  $manifest.rounds+=@{kind=$kind;id=$roundId;name=$body.name};Save
  }
  if($r.data.status -eq 'draft'){[void](Api POST "/dot-xet/$roundId/mo" $null $r.tag)}
  $people=if($kind -eq 'MAIN'){@(@{name='Nguyễn Minh Anh';key='ANH';ms='DEV-P10-001';codes=@('M2');to='2027-01-31'},@{name='Trần Hải Yến';key='YEN';ms='DEV-P10-002';codes=@('M8','M9');to='2026-10-31'},@{name='Lê Quốc Bảo';key='BAO';ms='DEV-P10-003';codes=@('M2');to='2027-01-31'})}else{@(@{name='Phạm Thu Hà';key='HA';ms='DEV-P10-004';codes=@('M2');to='2027-01-31'})}
  foreach($person in $people){
   $student=(Rows @'
SET XACT_ABORT ON;BEGIN TRAN;
DECLARE @existing bigint=(SELECT id FROM dbo.qua_trinh_theo_hoc WHERE ma_qua_trinh_hoc=@code);
IF @existing IS NOT NULL BEGIN COMMIT;SELECT sinh_vien_id studentId,id processId FROM dbo.qua_trinh_theo_hoc WHERE id=@existing;RETURN;END;
INSERT dbo.sinh_vien(mssv,ho_ten) VALUES(@mssv,@name);DECLARE @student bigint=SCOPE_IDENTITY();
INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,thoi_diem_bat_dau) VALUES(@student,@cohort,@semester,@code,N'verified','20260901');DECLARE @process bigint=SCOPE_IDENTITY();
COMMIT;SELECT @student studentId,@process processId;
'@ @{mssv=$person.ms;name=$person.name;cohort=[long]$refs.cohortId;semester=[long]$refs.semesterId;code=$run+'-'+$person.key})[0]
   foreach($code in $person.codes){
    $policy=($manifest.policies|Where-Object code -eq $code).ids
    $label=$run+'-'+$kind+'-'+$person.key+'-'+$code
    $p=@{round=$roundId;student=[long]$student.studentId;process=[long]$student.processId;parent=[long]$policy.parentId;policy=[long]$policy.policyVersionId;actor=[long]$refs.actorId;ethnic=[long]$refs.ethnicityId;class=[long]$refs.classId;cohort=[long]$refs.cohortId;unit=[long]$refs.unitId;semester=[long]$refs.semesterId;req=[long]$policy.requirementId;name=$person.name;mssv=$person.ms;label=$label}
    $recordedSource=@($manifest.sources|Where-Object {$_.roundId -eq $roundId -and $_.student -eq $person.ms -and $_.policy -eq $code})
    if($recordedSource.Count){$f=$recordedSource[0].ids}else{
    $f=(Rows @'
SET XACT_ABORT ON;BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.mghp_dot_xet WHERE id=@round AND ma_dot_xet LIKE N'P10UI-%') THROW 51982,N'Not owned P10UI round',1;
INSERT dbo.mghp_ho_so(ma_ho_so,dot_xet_id,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_chinh_sach_id,trang_thai) VALUES(@label,@round,@student,@process,@parent,@policy,N'submitted');DECLARE @root bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_phien_ban_ho_so(ho_so_id,lan_sua_doi,nguoi_nop_id,thoi_diem_nop,loai_lan_nop,dot_tiep_nhan_id,phien_ban_chinh_sach_ke_khai_id,ma_thao_tac_nop,ho_ten_khai_bao,so_cccd_khai_bao,ngay_sinh_khai_bao,noi_sinh_khai_bao,gioi_tinh_khai_bao,dan_toc_khai_bao_id,lop_khai_bao_id,mssv_khai_bao,khoa_hoc_khai_bao_id,khoa_quan_ly_khai_bao_id,dia_chi_thuong_tru_khai_bao,so_dien_thoai_khai_bao)
VALUES(@root,1,@actor,SYSDATETIMEOFFSET(),N'initial',@round,@policy,NEWID(),@name,N'000000000000','20050820',N'Đà Nẵng',N'NAM',@ethnic,@class,@mssv,@cohort,@unit,N'Địa chỉ synthetic P10',N'0900000000');DECLARE @sub bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_hoc_ky_de_nghi(phien_ban_ho_so_id,hoc_ky_id,phien_ban_chinh_sach_id) VALUES(@sub,@semester,@policy);
-- Metadata-only fixture: never downloaded/uploaded to a storage provider in P10 UI tests.
INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,nguoi_so_huu_id,kieu_phan_phoi,kich_thuoc_tep,sha256,pham_vi_hien_thi) VALUES(@label+N'-metadata.pdf',N'application/pdf',N'available',@actor,N'authenticated',100,REPLICATE('a',64),N'minh_chung');DECLARE @file bigint=SCOPE_IDENTITY();
INSERT dbo.luot_tai_tep(id,nguoi_dung_id,ten_cloud,ma_cong_khai,loai_tep,kich_thuoc,sha256,trang_thai,tep_id,ma_tai_nguyen,doi_soat_sau) VALUES(NEWID(),@actor,N'synthetic-no-storage',@label,N'evidence',100,REPLICATE('a',64),N'committed',@file,@label,'99991231');
INSERT dbo.mghp_giay_to_ho_so(phien_ban_ho_so_id,giay_to_bat_buoc_id,nguoi_nop_id) VALUES(@sub,@req,@actor);DECLARE @doc bigint=SCOPE_IDENTITY();
INSERT dbo.mghp_tep_minh_chung(giay_to_ho_so_id,tep_dinh_kem_id) VALUES(@doc,@file);
COMMIT;SELECT @root rootId,@sub submissionId,@doc documentId;
'@ $p)[0]
    $manifest.sources+=@{roundId=$roundId;ids=$f;student=$person.ms;policy=$code};Save
    }
    $until=if($code -eq 'M9'){'2027-01-31'}else{$person.to}
    $e=@{applicationId=[string]$f.rootId;submissionId=[string]$f.submissionId;benefitId=$null;updateSubmissionId=$null;processId=[string]$student.processId;semesterId=[string]$refs.semesterId;policyVersionId=[string]$policy.policyVersionId;scopeCode='hoc_phi_chuong_trinh_chinh';result='dat';reason='Đã đối chiếu đủ căn cứ synthetic P10';timeEvidence='Đúng khoảng minh chứng synthetic, không lấp thời gian';ageReferenceDate=$null;birthDateUsed=$null;statusHistoryId=$null;criteria=@(@{criterionId=[string]$policy.criterionId;result='dat';reasonCode=$null;note='Đủ căn cứ synthetic'});evidence=@(@{documentId=[string]$f.documentId;criterionId=$null;from='2026-09-01';to=$until;result='dat';reason='Đã đối chiếu synthetic'});eligiblePeriods=@(@{from='2026-09-01';to=$until;evidence='Khoảng học kỳ/quá trình/minh chứng synthetic phù hợp'})}
    foreach($k in @('form','content','validity','subject')){if((Rows 'SELECT id FROM dbo.mghp_ket_qua_kiem_tra_giay_to WHERE giay_to_ho_so_id=@id AND loai_kiem_tra=@kind' @{id=[long]$f.documentId;kind=$k}).Count){continue};$h=Api GET "/ho-so/$($f.rootId)";[void](Api POST "/giay-to-da-nop/$($f.documentId)/kiem-tra" @{kind=$k;result='pass';note=$null;replacesCheckId=$null} $h.tag)}
    $prior=Rows 'SELECT id,trang_thai_xu_ly status FROM dbo.mghp_danh_gia_dieu_kien WHERE ho_so_nguon_id=@id' @{id=[long]$f.rootId}
    if($prior.Count -and $prior[0].status -eq 'confirmed'){continue}
    $evaluation=if($prior.Count){Api GET ('/danh-gia/'+$prior[0].id)}else{Api POST '/danh-gia' $e}
    [void](Api POST "/danh-gia/$($evaluation.data.id)/xac-nhan" @{reason='Xác nhận căn cứ synthetic P10'} $evaluation.tag)
   }
  }
  $r=Api GET "/dot-xet/$roundId";if($r.data.status -eq 'open'){[void](Api POST "/dot-xet/$roundId/dong-nhan-ho-so" @{reason='Hoàn tất tiếp nhận synthetic P10'} $r.tag)}
  $pre=Api GET "/dot-xet/$roundId/dong-xet"
  if($pre.data.blockerCount -ne 0){throw 'Unexpected P10UI close-review blocker'}
  [void](Api POST "/dot-xet/$roundId/dong-xet" @{reason='Đã đối chiếu đầy đủ tập nguồn thực hành Hội đồng'} $pre.tag)
 }
 $manifest.stage='complete';Save;$token=$null
 Write-Output "Prepared P10UI on ctsv_dev: two marked rounds, five assessed applications, three policy versions. Manifest $out/fixture.json"
}finally{$cn.Dispose()}

