$ErrorActionPreference='Stop'
. "$PSScriptRoot/Dev.Common.ps1"
$passed=0
function Check($condition,$message) { if(-not $condition){throw $message}; $script:passed++ }
$homePath=Get-DevHome (Resolve-Path "$PSScriptRoot/../..").Path
Check (-not $homePath.StartsWith((Resolve-Path "$PSScriptRoot/../..").Path)) 'Secrets must live outside checkout'
Check ((Get-DevHome (Resolve-Path "$PSScriptRoot/../..").Path) -eq $homePath) 'Stable per-checkout path'
Check (Test-DevProcessIdentity @{Pid=42;Created='a';Command='b'} @{Pid=42;Created='a';Command='b'}) 'Same process owned'
Check (-not (Test-DevProcessIdentity @{Pid=42;Created='a';Command='b'} @{Pid=42;Created='c';Command='b'})) 'PID reuse rejected'
Check (-not (Test-DevProcessIdentity @{Pid=42;Created='a';Command='b'} @{Pid=42;Created='a';Command='other'})) 'Foreign command rejected'
Check (-not (Test-DevProcessIdentity @{Pid=42;Created='a';Command='b'} $null)) 'Exited process rejected'
$stamp='2026-11-13T02:17:41.0858070Z'
$persisted=@{Pid=42;Created=$stamp;Command='java local-dev'}|ConvertTo-Json|ConvertFrom-Json -AsHashtable
Check (Test-DevProcessIdentity $persisted @{Pid=42;Created=$stamp;Command='java local-dev'}) 'Owned identity survives JSON DateTime roundtrip'
$sample=ConvertTo-DevProperty 'D:\folder with space\key.pem'
Check ($sample -eq 'D:/folder with space/key.pem') 'Java properties Windows path escaping'
Check ((Get-DevJdbcUrl 56789) -match 'databaseName=ctsv_dev;') 'Only named dev DB'
Check ((Get-DevJdbcUrl 56789) -notmatch 'test_') 'Never fallback to test DB'
Check ((ConvertFrom-DevResponse ([Text.Encoding]::UTF8.GetBytes('{"code":"UNAUTHENTICATED"}'))).code -eq 'UNAUTHENTICATED') 'ProblemDetail byte response readiness'
Check ((Get-DevPresetPassword) -ceq 'CtsvDev@2026!') 'Owner-approved synthetic DEV password'
Write-Output "Dev tools checks: $passed PASS"
