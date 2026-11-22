#requires -Version 7.0
# Real DEV HTTP/SQL tests. Authenticate with the approved synthetic preset only.
# Access tokens remain in process memory and are never read from a browser or written out.
$ErrorActionPreference='Stop'
. "$PSScriptRoot/../../tools/dev/Dev.Common.ps1"
$origin='https://127.0.0.1:8443'
$cases=[Collections.Generic.List[object]]::new()
function Login-Dev([string]$Username){
 $payload=@{username=$Username;password=(Get-DevPresetPassword)}|ConvertTo-Json -Compress
 $r=Invoke-WebRequest "$origin/api/xac-thuc/dang-nhap" -Method Post -ContentType 'application/json' -Body $payload -Headers @{Origin='https://127.0.0.1:5173';'X-CSRF-Protection'='1'} -SkipCertificateCheck -SkipHttpErrorCheck
 if([int]$r.StatusCode -ne 200){throw "DEV fixture login failed for $Username; status $([int]$r.StatusCode)"}
 return ($r.Content|ConvertFrom-Json).accessToken
}
function Check-Read([string]$Name,[string]$Path,[string]$Access,[int]$Expected){
 $headers=@{};if($Access){$headers.Authorization='Bearer '+$Access}
 $r=Invoke-WebRequest ($origin+$Path) -Headers $headers -SkipCertificateCheck -SkipHttpErrorCheck
 if([int]$r.StatusCode -ne $Expected){throw "$Name expected $Expected, received $([int]$r.StatusCode)"}
 $cases.Add(@{id=$Name;status='PASS';httpStatus=$Expected;database='ctsv_dev';backend='REAL'})
 if($Expected -eq 200){return ($r.Content|ConvertFrom-Json)}
}
try{
 $adminAccess=Login-Dev 'dev.admin';$staffAccess=Login-Dev 'dev.chuyenvien';$studentAccess=Login-Dev 'dev.sinhvien'
 $accounts=Check-Read 'AUTH_SEARCH_MANAGER' '/api/xac-thuc/tai-khoan?q=dev&page=0&size=2' $adminAccess 200
 if($accounts.items.Count -gt 2 -or $accounts.totalElements -lt $accounts.items.Count){throw 'Account pagination/count mismatch'}
 foreach($row in $accounts.items){if((@($row.psobject.Properties.Name)|Sort-Object)-join ',' -ne 'accountId,active,username'){throw 'Account summary projection changed'}}
 $literal=Check-Read 'AUTH_LITERAL_PERCENT' '/api/xac-thuc/tai-khoan?q=%25' $adminAccess 200
 if($literal.totalElements -ne 0){throw 'Literal percent interpreted as SQL wildcard'}
 $null=Check-Read 'AUTH_DENY_STUDENT' '/api/xac-thuc/tai-khoan' $studentAccess 403
 $null=Check-Read 'AUTH_DENY_STAFF' '/api/xac-thuc/tai-khoan' $staffAccess 403
 $null=Check-Read 'AUTH_ANONYMOUS' '/api/xac-thuc/tai-khoan' '' 401
 $null=Check-Read 'USER_DIRECTORY_MANAGER' '/api/ctsv/nen-tang/nguoi-dung?q=DEV' $adminAccess 200
 $null=Check-Read 'USER_DIRECTORY_DENY_STUDENT' '/api/ctsv/nen-tang/nguoi-dung' $studentAccess 403
 $roles=Check-Read 'MGHP_ROLE_DIRECTORY_MANAGER' '/api/ctsv/nen-tang/vai-tro-mghp' $adminAccess 200
 if(@($roles|Where-Object code -notin @('mghp_chuyen_vien','mghp_pho_phong')).Count){throw 'Role directory exceeded current grant allowlist'}
 $null=Check-Read 'MGHP_ROLE_DIRECTORY_DENY_STAFF' '/api/ctsv/nen-tang/vai-tro-mghp' $staffAccess 403
 $null=Check-Read 'PROFILE_DIRECTORY_MANAGER' '/api/ctsv/nen-tang/ho-so-nguon?profileType=student' $adminAccess 200
 $null=Check-Read 'PROFILE_DIRECTORY_DENY_STUDENT' '/api/ctsv/nen-tang/ho-so-nguon?profileType=student' $studentAccess 403
 $null=Check-Read 'PROFILE_DIRECTORY_BAD_TYPE' '/api/ctsv/nen-tang/ho-so-nguon?profileType=other' $adminAccess 400
 $mine=Check-Read 'OWN_BENEFITS_STUDENT' '/api/ctsv/mghp/toi/quyen-huong' $studentAccess 200
 $null=Check-Read 'OWN_BENEFITS_NO_OWNER_PARAMETER' '/api/ctsv/mghp/toi/quyen-huong?studentId=2' $studentAccess 400
 $null=Check-Read 'OWN_BENEFITS_DENY_AUTH_ADMIN' '/api/ctsv/mghp/toi/quyen-huong' $adminAccess 403
 $null=Check-Read 'OWN_BENEFITS_DENY_STAFF' '/api/ctsv/mghp/toi/quyen-huong' $staffAccess 403
 $null=Check-Read 'BENEFIT_CONTEXT_DENY_STUDENT' '/api/ctsv/mghp/quyen-huong/1/ngu-canh-xet-duyet' $studentAccess 403
 $null=Check-Read 'LOOKUP_LABELS_STUDENT' '/api/ctsv/nen-tang/danh-muc?kind=classes&ids=1' $studentAccess 200
 $null=Check-Read 'LOOKUP_LABELS_INVALID_ID' '/api/ctsv/nen-tang/danh-muc?kind=classes&ids=1,bad' $studentAccess 400
 $null=Check-Read 'LOOKUP_LABELS_MIXED_QUERY' '/api/ctsv/nen-tang/danh-muc?kind=classes&ids=1&q=x' $studentAccess 400
 $result=@{target=$origin;database='ctsv_dev';cases=$cases;ownBenefitRows=$mine.totalElements;scope='HTTP REAL and SQL REAL; no business mutation; fixture login audits only'}
 $result|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$PSScriptRoot/../../target/p1-usability-20261122/http-results.json" -Encoding utf8
 Write-Output "P1 HTTP REAL: $($cases.Count) PASS, ownBenefitRows=$($mine.totalElements). No credentials written to evidence."
}finally{$adminAccess=$null;$staffAccess=$null;$studentAccess=$null}
