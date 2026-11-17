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
$fileProperties=Get-DevFileProperties @{}
Check ($fileProperties -contains 'ctsv.files.enabled=true') 'DEV exposes the PDF API even when storage is missing'
Check ($fileProperties -contains 'ctsv.files.cloudinary.enabled=false') 'Missing credentials do not start Cloudinary'
$fileProperties=Get-DevFileProperties @{CLOUDINARY_CLOUD_NAME='synthetic';CLOUDINARY_API_KEY='synthetic-key'}
Check ($fileProperties -contains 'ctsv.files.cloudinary.enabled=false') 'Partial credentials never start Cloudinary'
$fileProperties=Get-DevFileProperties @{CLOUDINARY_CLOUD_NAME='synthetic';CLOUDINARY_API_KEY='synthetic-key';CLOUDINARY_API_SECRET='synthetic-secret';CLOUDINARY_FOLDER='ctsv';CLOUDINARY_FOLDER_MODE='dynamic'}
Check ($fileProperties -contains 'ctsv.files.cloudinary.enabled=true') 'A complete configuration enables Cloudinary'
Check (($fileProperties -join '') -notmatch 'synthetic') 'Generated properties omit credential values'
$cloudNames=@('CLOUDINARY_CLOUD_NAME','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET','CLOUDINARY_FOLDER','CLOUDINARY_FOLDER_MODE')
$original=@{}
$fixtureHome=Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
$fixtureFile=Join-Path $fixtureHome 'cloudinary.dpapi.json'
try{
    foreach($name in $cloudNames){
        $original[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
        [Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    [IO.Directory]::CreateDirectory($fixtureHome)|Out-Null
    $saved=@{}
    foreach($name in $cloudNames[0..2]){$saved[$name]=Protect-DevSecret 'synthetic-value'}
    $saved|ConvertTo-Json|Set-Content -LiteralPath $fixtureFile
    $loaded=Get-DevCloudinaryEnvironment $fixtureHome
    Check ($loaded.CLOUDINARY_API_SECRET -eq 'synthetic-value') 'Windows account can reload the local encrypted config'
    Check ($loaded.CLOUDINARY_FOLDER -eq 'ctsv' -and $loaded.CLOUDINARY_FOLDER_MODE -eq 'dynamic') 'Defaults target the approved Dynamic Folder'
    [Environment]::SetEnvironmentVariable('CLOUDINARY_API_KEY','synthetic-override','Process')
    $loaded=Get-DevCloudinaryEnvironment $fixtureHome
    Check ($loaded.CLOUDINARY_API_KEY -eq 'synthetic-value') 'Saved DEV key wins over a stale partial shell configuration'
    foreach($name in $cloudNames){[Environment]::SetEnvironmentVariable($name,'synthetic-override','Process')}
    $loaded=Get-DevCloudinaryEnvironment $fixtureHome
    Check ($loaded.CLOUDINARY_API_SECRET -eq 'synthetic-value') 'Saved DEV credentials win over a complete stale shell configuration'
    Check ($loaded.CLOUDINARY_FOLDER -eq 'ctsv' -and $loaded.CLOUDINARY_FOLDER_MODE -eq 'dynamic') 'Shell folder variables cannot redirect saved DEV credentials'
    Remove-Item -LiteralPath $fixtureFile
    $loaded=Get-DevCloudinaryEnvironment $fixtureHome
    Check ($loaded.CLOUDINARY_API_KEY -eq 'synthetic-override') 'Shell configuration works when no saved file exists'
}finally{
    foreach($name in $original.Keys){[Environment]::SetEnvironmentVariable($name,$original[$name],'Process')}
    if(Test-Path -LiteralPath $fixtureFile){Remove-Item -LiteralPath $fixtureFile}
    if(Test-Path -LiteralPath $fixtureHome){Remove-Item -LiteralPath $fixtureHome}
}
Write-Output "Dev tools checks: $passed PASS"
