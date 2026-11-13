$ErrorActionPreference='Stop'
. "$PSScriptRoot/Dev.Common.ps1"
. "$PSScriptRoot/Sync.Common.ps1"
$passed=0
function Check($ok,$message){if(-not $ok){throw $message};$script:passed++}
Check ((Get-SyncJdbcUrl 56826 sis_dev) -match 'databaseName=sis_dev;') 'Dedicated SIS dev target'
Check ((Get-SyncJdbcUrl 56826 ctsv_dev) -eq (Get-DevJdbcUrl 56826)) 'Existing CTSV dev target'
foreach($db in @('sis_test_20261111','ctsv_test_20261111','master','other')){
    $blocked=$false;try{Get-SyncJdbcUrl 56826 $db|Out-Null}catch{$blocked=$true}
    Check $blocked "Reject non-dev database $db"
}
Check ((Get-SyncEndpoint 'https://127.0.0.1:9443' units) -eq 'https://127.0.0.1:9443/api/dao-tao/don-vi') 'Units approved path'
Check ((Get-SyncEndpoint 'https://127.0.0.1:9443' ethnicities) -eq 'https://127.0.0.1:9443/api/dao-tao/dan-toc') 'DanToc approved path'
$blocked=$false;try{Get-SyncEndpoint 'http://127.0.0.1:9443' units|Out-Null}catch{$blocked=$true}
Check $blocked 'No TLS downgrade'
$blocked=$false;try{Get-SyncEndpoint 'https://untrusted.example' units|Out-Null}catch{$blocked=$true}
Check $blocked 'Loopback-only dev origin'
Write-Output "Sync tools checks: $passed PASS"
