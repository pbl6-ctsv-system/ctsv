#requires -Version 7.0
# Daily launcher HTTP smoke. One owned synthetic rule; no reset/delete/credential files.
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
. (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')
$taskDevHome=Get-DevHome $taskRoot
$taskConfig=Read-DevConfig $taskDevHome
$taskState=Get-Content -LiteralPath (Join-Path $taskDevHome 'processes.json') -Raw | ConvertFrom-Json
foreach($taskProcess in @($taskState.Backend,$taskState.Frontend)){
 if(-not(Test-DevProcessIdentity $taskProcess (Get-DevProcess $taskProcess.Pid))){throw 'Daily DEV launcher not running.'}
}
$taskOrigin='https://127.0.0.1:'+$taskConfig.FrontendPort
$taskBase=$taskOrigin+'/api/ctsv/hoc-bong/kkht/phien-quy-tac'
$taskPassed=0
$taskFailed=$false
$taskActors=@()
$taskMarker='SYNTHETIC — T03.3 quy tắc KKHT 26/11/2026'
$taskAlternate=$taskMarker+' · đã kiểm sửa'
$taskTemplate=Get-Content -LiteralPath (Join-Path $taskRoot 'src/test/resources/fixtures/hocbong/rules/valid-rule.json') -Raw -Encoding utf8 | ConvertFrom-Json
$taskTemplate.legalBasis=$taskMarker
function Assert-Hb([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Pass-Hb([string]$Name){$script:taskPassed++;Write-Output ($Name+' PASS (REAL DEV HTTP)')}
function Request-Hb([string]$Method,[string]$Suffix='',$Actor=$null,$Body=$null,[hashtable]$Extra=@{}){
 $taskHeaders=@{Origin=$taskOrigin};if($Actor){$taskHeaders.Authorization='Bearer '+$Actor.Token}
 foreach($key in $Extra.Keys){$taskHeaders[$key]=$Extra[$key]}
 $taskArgs=@{Uri=$taskBase+$Suffix;Method=$Method;Headers=$taskHeaders;SkipHttpErrorCheck=$true;TimeoutSec=20;MaximumRetryCount=0}
 if($null -ne $Body){$taskArgs.ContentType='application/json';$taskArgs.Body=[Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress))}
 $taskResponse=Invoke-WebRequest @taskArgs
 $taskJson=if($taskResponse.Content.Length -gt 0){ConvertFrom-DevResponse $taskResponse.Content}else{$null}
 [pscustomobject]@{Status=[int]$taskResponse.StatusCode;Body=$taskJson;Tag=[string]($taskResponse.Headers['ETag'] | Select-Object -First 1);Cache=[string]($taskResponse.Headers['Cache-Control'] | Select-Object -First 1)}
}
function Login-Hb([string]$Role){
 $taskSession=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
 $taskPassword=Open-DevSecret $taskConfig.Accounts.$Role.Password
 try{
  $taskBody=@{username=$taskConfig.Accounts.$Role.Username;password=$taskPassword} | ConvertTo-Json -Compress
  $taskResponse=Invoke-WebRequest -Uri ($taskOrigin+'/api/xac-thuc/dang-nhap') -Method Post -WebSession $taskSession -Headers @{Origin=$taskOrigin;'X-CSRF-Protection'='1'} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($taskBody)) -SkipHttpErrorCheck -TimeoutSec 20 -MaximumRetryCount 0
  Assert-Hb ($taskResponse.StatusCode -eq 200) ('DEV '+$Role+' login status '+[int]$taskResponse.StatusCode)
  $taskLogin=ConvertFrom-DevResponse $taskResponse.Content
  [pscustomobject]@{Token=$taskLogin.accessToken;Session=$taskSession;Role=$Role}
 }finally{$taskPassword=$null;$taskBody=$null}
}
function Body-Hb($Detail){
 $taskBody=[ordered]@{}
 foreach($key in @('legalBasis','effectiveFrom','effectiveTo','conditions','levels','priority','tuitionBasis','tiePolicy','lastSlot')){$taskBody[$key]=$Detail.$key}
 [pscustomobject]$taskBody
}
function Assert-OwnedHbRule($Detail,$Template,[string]$Marker,[string]$Alternate){
 Assert-Hb ($Detail.legalBasis -ceq $Marker -or $Detail.legalBasis -ceq $Alternate) 'Fixture ownership changed after list; not overwritten.'
 $candidate=Body-Hb $Detail;$candidate.legalBasis=$Marker
 Assert-Hb (($candidate | ConvertTo-Json -Depth 30 -Compress) -ceq ($Template | ConvertTo-Json -Depth 30 -Compress)) 'Owned fixture differs from approved synthetic configuration; not overwritten.'
}
try{
 $r=Request-Hb GET
 Assert-Hb ($r.Status -eq 401 -and $r.Body.code -eq 'UNAUTHENTICATED') 'No-token API must return401';Pass-Hb 'HB-RULE-DEV01-no-token'
 $cv=Login-Hb specialist;$taskActors+=,$cv
 $r=Request-Hb GET '?page=0&size=20' $cv
 Assert-Hb ($r.Status -eq 200 -and $r.Cache -eq 'no-store') 'CV list200/no-store';Pass-Hb 'HB-RULE-DEV02-specialist-list'
 $found=@();$taskPage=0
 do{
  $items=if($taskPage -eq 0){$r}else{Request-Hb GET ('?page='+$taskPage+'&size=20') $cv}
  Assert-Hb ($items.Status -eq 200) 'Cannot inspect owned synthetic fixture list'
  $found+=@($items.Body.items | Where-Object {$_.legalBasis -ceq $taskMarker -or $_.legalBasis -ceq $taskAlternate})
  $taskPage++
  Assert-Hb ($taskPage*20 -le 100000) 'Review fixture list offset limit'
 }while($taskPage*20 -lt $items.Body.totalElements)
 Assert-Hb ($found.Count -le 1) 'Multiple synthetic fixture matches; inspect before writing, no automatic POST retry.'
 if($found.Count -eq 0){
  $created=Request-Hb POST '' $cv $taskTemplate
  Assert-Hb ($created.Status -eq 201 -and $created.Tag -match '^"[A-Za-z0-9_-]+"$') 'Create201 with strong tag'
  $taskId=$created.Body.id;Pass-Hb 'HB-RULE-DEV03-create-synthetic'
 }else{$taskId=$found[0].id;Write-Output 'HB-RULE-DEV03 fixture reused; POST not repeated.'}
 Assert-Hb ($taskId -is [string] -and $taskId -match '^[1-9][0-9]*$') 'ID must be string'
 $view=Request-Hb GET ('/'+$taskId) $cv
 Assert-Hb ($view.Status -eq 200 -and $view.Body.status -eq 'draft') 'Owned fixture must be unused; leave used/user-modified data untouched.'
 Assert-OwnedHbRule $view.Body $taskTemplate $taskMarker $taskAlternate
 Assert-Hb ($view.Body.levels.A.tuitionMultiplier -is [string] -and $view.Body.levels.A.tuitionMultiplier -ceq '1.5000') 'Decimal string readback';Pass-Hb 'HB-RULE-DEV04-readback'
 $update=Body-Hb $view.Body;$update.legalBasis=if($view.Body.legalBasis -ceq $taskMarker){$taskAlternate}else{$taskMarker}
 $saved=Request-Hb PUT ('/'+$taskId) $cv $update @{'If-Match'=$view.Tag}
 Assert-Hb ($saved.Status -eq 200 -and $saved.Tag -ne $view.Tag -and $saved.Body.version -eq $view.Body.version) 'Update200/fresh tag/same version';Pass-Hb 'HB-RULE-DEV05-update-unused'
 $again=Request-Hb GET ('/'+$taskId) $cv
 Assert-Hb ($again.Status -eq 200 -and $again.Tag -eq $saved.Tag -and $again.Body.legalBasis -ceq $update.legalBasis) 'Fresh GET must match persisted update';Pass-Hb 'HB-RULE-DEV06-update-readback'
 $stale=Request-Hb PUT ('/'+$taskId) $cv $update @{'If-Match'=$view.Tag}
 Assert-Hb ($stale.Status -eq 412 -and $stale.Body.code -eq 'VERSION_CONFLICT') 'Stale update412';Pass-Hb 'HB-RULE-DEV07-stale-tag'
 $missing=Request-Hb PUT ('/'+$taskId) $cv $update
 Assert-Hb ($missing.Status -eq 428) 'Missing If-Match428';Pass-Hb 'HB-RULE-DEV08-missing-tag'
 $bad=$taskTemplate | ConvertTo-Json -Depth 30 | ConvertFrom-Json;$bad | Add-Member -NotePropertyName version -NotePropertyValue 999
 $r=Request-Hb POST '' $cv $bad;Assert-Hb ($r.Status -eq 400) 'Client version rejected';Pass-Hb 'HB-RULE-DEV09-forged-metadata'
 $bad=$taskTemplate | ConvertTo-Json -Depth 30 | ConvertFrom-Json;$bad.lastSlot.comparison='GTE'
 $r=Request-Hb POST '' $cv $bad;Assert-Hb ($r.Status -eq 400) 'Strict>50% policy cannot become>=';Pass-Hb 'HB-RULE-DEV10-strict-last-slot'
 $bad=$taskTemplate | ConvertTo-Json -Depth 30 | ConvertFrom-Json;$bad.tuitionBasis.rounding.mode='DOWN'
 $r=Request-Hb POST '' $cv $bad;Assert-Hb ($r.Status -eq 400) 'Rounding HALF_UP required';Pass-Hb 'HB-RULE-DEV11-rounding-policy'
 $r=Request-Hb DELETE ('/'+$taskId) $cv;Assert-Hb ($r.Status -eq 405) 'No delete endpoint';Pass-Hb 'HB-RULE-DEV12-no-delete'
 $after=Request-Hb GET ('/'+$taskId) $cv;Assert-Hb ($after.Status -eq 200 -and $after.Tag -eq $saved.Tag) 'Rejected requests must not mutate rule';Pass-Hb 'HB-RULE-DEV13-rejected-writes-preserve'
 $sv=Login-Hb student;$taskActors+=,$sv
 foreach($method in @('GET','POST','PUT')){
  $suffix=if($method -eq 'PUT'){'/'+$taskId}else{''}
  $r=if($method -eq 'GET'){Request-Hb GET $suffix $sv}else{Request-Hb $method $suffix $sv $taskTemplate @{'If-Match'=$saved.Tag}}
  Assert-Hb ($r.Status -eq 403 -and $r.Body.code -eq 'FORBIDDEN') ('Student '+$method+' must403');Pass-Hb ('HB-RULE-DEV14-student-'+$method)
 }
 Write-Output ('Synthetic DEV rule id='+$taskId+'; accounts dev.chuyenvien/dev.sinhvien; no external services; wire decimals ungrouped. Money display belongs to the future UI.')
 Write-Output ('REAL '+$taskOrigin+' -> https://127.0.0.1:'+$taskConfig.BackendPort+' / DEV2022/ctsv_dev')
}catch{
 $taskMessage=[string]$_.Exception.Message
 foreach($taskActor in $taskActors){if($taskActor.Token){$taskMessage=$taskMessage.Replace($taskActor.Token,'[REDACTED]')}}
 Write-Output ('FAIL: '+$taskMessage);$script:taskFailed=$true
}finally{
 foreach($taskActor in $taskActors){
  try{$taskLogout=Invoke-WebRequest -Uri ($taskOrigin+'/api/xac-thuc/dang-xuat') -Method Post -WebSession $taskActor.Session -Headers @{Origin=$taskOrigin;'X-CSRF-Protection'='1'} -SkipHttpErrorCheck -TimeoutSec 10 -MaximumRetryCount 0;Assert-Hb ($taskLogout.StatusCode -eq 204) 'Logout must204';Pass-Hb ('HB-RULE-DEV-logout-'+$taskActor.Role)}catch{Write-Output ('NOT VERIFIED logout '+$taskActor.Role);$script:taskFailed=$true}
  $taskActor.Token=$null
 }
 Write-Output ('T03.3 REAL DEV HTTP: '+$taskPassed+' checks PASS; no screenshot/evidence/storage state.')
}
if($taskFailed){exit 1}
