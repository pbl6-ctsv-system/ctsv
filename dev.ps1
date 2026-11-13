#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('setup','build','start','stop','status','certificate','trust','credentials')][string]$Action='start',
      [string]$ConfirmThumbprint,[ValidateSet('admin','student','specialist')][string]$Account)
$ErrorActionPreference='Stop'
# Windows PowerShell is the default Windows terminal. Delegate before loading .NET7-dependent tooling.
if($PSVersionTable.PSVersion.Major -lt 7){
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$checkoutHash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($PSScriptRoot.ToLowerInvariant())))).Replace('-','').Substring(0,12)}finally{$sha.Dispose()}
    $runtimePointer=Join-Path $env:LOCALAPPDATA "CTSV/dev/$checkoutHash/powershell-runtime.txt"
    $candidates=@()
    if(Test-Path -LiteralPath $runtimePointer){$candidates+=(Get-Content -LiteralPath $runtimePointer -Raw).Trim()}
    $found=Get-Command pwsh.exe -ErrorAction SilentlyContinue
    if($found){$candidates+=$found.Source}
    $candidates+=(Join-Path $env:ProgramFiles 'PowerShell/7/pwsh.exe')
    foreach($candidate in $candidates|Select-Object -Unique){
        if(-not (Test-Path -LiteralPath $candidate -PathType Leaf)){continue}
        $major=& $candidate -NoProfile -NonInteractive -Command '$PSVersionTable.PSVersion.Major'
        if($LASTEXITCODE -ne 0 -or [int]$major -lt 7){continue}
        $forward=@('-NoProfile','-File',$PSCommandPath,'-Action',$Action)
        if($PSBoundParameters.ContainsKey('ConfirmThumbprint')){$forward+=@('-ConfirmThumbprint',$ConfirmThumbprint)}
        if($PSBoundParameters.ContainsKey('Account')){$forward+=@('-Account',$Account)}
        & $candidate @forward
        exit $LASTEXITCODE
    }
    Write-Error 'PowerShell7 not found. Open an installed PowerShell7 terminal, then run .\dev.ps1 status once; or install PowerShell7 from Microsoft. No security policy was changed.'
    exit 1
}
. "$PSScriptRoot/tools/dev/Dev.Common.ps1"
$root=$PSScriptRoot
$devHome=Get-DevHome $root
# Remember the verified current runtime for a later PS5.1 shell whose PATH may differ.
if(Test-Path -LiteralPath $devHome){[IO.File]::WriteAllText((Join-Path $devHome 'powershell-runtime.txt'),(Join-Path $PSHOME 'pwsh.exe'))}
$stateFile=Join-Path $devHome 'processes.json'
$mutex=[Threading.Mutex]::new($false,'Local\CTSV-dev-'+(Split-Path $devHome -Leaf))
$locked=$false
try{
    try{$locked=$mutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$locked=$true}
    if(-not $locked){throw 'Another dev launcher command is running. Wait for it to finish.'}
    if($Action -in @('setup','build','start') -and (Test-Path -LiteralPath $stateFile)){
        $existing=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable
        foreach($saved in @($existing.Backend,$existing.Frontend)){
            if($saved -and (Test-DevProcessIdentity $saved (Get-DevProcess $saved.Pid))){throw 'Launcher already running. Use .\dev.ps1 stop before setup/build/start.'}
        }
    }
    if($Action -eq 'setup'){
        $config=Initialize-DevFiles $root $devHome
        Set-DevEnvironment $devHome $config
        Build-DevApplication $root $devHome
        Invoke-DevDatabase $root $devHome setup $config
        # DB setup explicitly applies the approved fixture password; daily start never resets it.
        foreach($role in @('admin','student','specialist')){$config.Accounts.$role.Password=Protect-DevSecret (Get-DevPresetPassword)}
        $config|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $devHome 'config.json') -Encoding utf8
        Show-DevCertificate $devHome $config
        Write-Output 'Setup DB/keys/accounts complete. Review certificate before: .\dev.ps1 trust -ConfirmThumbprint <exact-thumbprint>'
        return
    }
    $config=Read-DevConfig $devHome
    switch($Action){
      'certificate'{Show-DevCertificate $devHome $config;return}
      'trust'{
        $cert=Get-DevCertificate $devHome $config
        if(-not $ConfirmThumbprint -or $ConfirmThumbprint -ne $cert.Thumbprint){throw 'Read .\dev.ps1 certificate and supply the approved exact -ConfirmThumbprint'}
        if(-not (Test-Path -LiteralPath "Cert:\CurrentUser\Root\$ConfirmThumbprint")){[IO.File]::WriteAllBytes((Join-Path $devHome 'https.cer'),$cert.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert));Import-Certificate -FilePath (Join-Path $devHome 'https.cer') -CertStoreLocation Cert:\CurrentUser\Root|Out-Null}
        $cert.Dispose()
        if(-not (Test-Path -LiteralPath "Cert:\CurrentUser\Root\$ConfirmThumbprint")){throw 'Certificate trust not installed'}
        Write-Output "Trusted exact leaf: $ConfirmThumbprint. Start does not install certificates.";return
      }
      'credentials'{
        if(-not $Account){foreach($role in @('student','specialist','admin')){Write-Output "$role : $($config.Accounts.$role.Username)"};Write-Output 'Copy one password locally: .\dev.ps1 credentials -Account student ; paste into login, then clear clipboard.';return}
        Set-Clipboard -Value (Open-DevSecret $config.Accounts.$Account.Password)
        Write-Output "Password copied locally for $($config.Accounts.$Account.Username). Not printed or logged. Clear clipboard after login: Set-Clipboard -Value ''";return
      }
      'stop'{if(Test-Path -LiteralPath $stateFile){$state=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable;Stop-DevOwned $state;Remove-Item -LiteralPath $stateFile};Write-Output 'Stopped owned dev processes only.';return}
      'status'{
        if(-not (Test-Path -LiteralPath $stateFile)){Write-Output 'STOPPED (no launcher state)';return}
        $state=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable
        foreach($name in @('Backend','Frontend')){$saved=$state[$name];Write-Output "$name : $(if($saved -and (Test-DevProcessIdentity $saved (Get-DevProcess $saved.Pid))){'RUNNING pid='+$saved.Pid}else{'STOPPED / identity mismatch'})"}
        Write-Output "FE https://127.0.0.1:$($config.FrontendPort) | BE https://127.0.0.1:$($config.BackendPort) | DB localhost\DEV2022 / ctsv_dev | logs $devHome/logs";return
      }
    }
    Set-DevEnvironment $devHome $config
    if($Action -eq 'build'){Build-DevApplication $root $devHome;Write-Output 'Dev application built without test execution or migration.';return}
    $cert=Get-DevCertificate $devHome $config;$cert.Dispose()
    if(-not (Test-Path -LiteralPath "Cert:\CurrentUser\Root\$($config.Thumbprint)")){throw 'Certificate not trusted; review .\dev.ps1 certificate, then explicitly approve trust'}
    if(-not (Test-Path (Join-Path $devHome 'build-source.sha256')) -or (Get-Content (Join-Path $devHome 'build-source.sha256') -Raw).Trim() -ne (Get-DevSourceHash $root)){throw 'Backend source changed or not built: .\dev.ps1 build (no migration)'}
    foreach($port in @($config.BackendPort,$config.FrontendPort)){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "Port $port occupied; use status/stop or select a free port in local config. No unrelated process is killed."}}
    if(-not (Test-Path (Join-Path $root 'frontend/node_modules/vite/bin/vite.js'))){throw 'Missing frontend dependencies: cd frontend; npm ci'}
    Invoke-DevDatabase $root $devHome check $config
    $state=@{Backend=$null;Frontend=$null}
    try{
        $properties=[Uri]::new((Join-Path $devHome 'application-dev.properties')).AbsoluteUri
        $jar=Join-Path $root 'target/ctsv-0.0.1-SNAPSHOT.jar'
        $jdbc=(Get-Content -LiteralPath (Join-Path $devHome 'application-dev.properties')|Where-Object{$_ -like 'spring.datasource.url=*'}).Substring('spring.datasource.url='.Length)
        # Explicit safe command-line overrides prevent ambient SPRING_* variables selecting a test/other DB or migration.
        $backendArgs=@("-Dspring.config.additional-location=$properties",'-jar',('"'+$jar+'"'),'--spring.profiles.active=dev',"--spring.datasource.url=$jdbc",'--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration','--spring.flyway.enabled=false','--spring.jpa.hibernate.ddl-auto=validate','--ctsv.auth.bootstrap-enabled=false','--foundation.units.enabled=false','--foundation.resources.enabled=false')
        $backend=Start-Process java.exe -ArgumentList $backendArgs -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $devHome 'logs/backend.log') -RedirectStandardError (Join-Path $devHome 'logs/backend.err.log') -PassThru
        $state.Backend=Get-DevProcess $backend.Id;$state|ConvertTo-Json -Depth 4|Set-Content $stateFile
        $ready=$false;$until=(Get-Date).AddSeconds(90)
        while((Get-Date) -lt $until){
            if($backend.HasExited){throw "Backend exited; see $devHome/logs/backend.log"}
            try{$r=Invoke-WebRequest "https://127.0.0.1:$($config.BackendPort)/api/xac-thuc/toi" -SkipHttpErrorCheck -TimeoutSec 2;if($r.StatusCode -eq 401 -and (ConvertFrom-DevResponse $r.Content).code -eq 'UNAUTHENTICATED'){$ready=$true;break}}catch{}
            Start-Sleep -Milliseconds 300
        }
        if(-not $ready){throw "Backend HTTPS readiness timeout; see $devHome/logs/backend.log"}
        $vite=Join-Path $root 'frontend/node_modules/vite/bin/vite.js'
        $frontend=Start-Process node.exe -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1','--port',"$($config.FrontendPort)",'--strictPort') -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $devHome 'logs/frontend.log') -RedirectStandardError (Join-Path $devHome 'logs/frontend.err.log') -PassThru
        $state.Frontend=Get-DevProcess $frontend.Id;$state|ConvertTo-Json -Depth 4|Set-Content $stateFile
        $r=$null
        for($i=0;$i -lt 40;$i++){try{$r=Invoke-WebRequest "https://127.0.0.1:$($config.FrontendPort)" -TimeoutSec 2;break}catch{if($frontend.HasExited){throw 'Frontend exited; see frontend.err.log'};Start-Sleep -Milliseconds 300}}
        if(-not $r -or $r.StatusCode -ne 200){throw 'Frontend HTTPS readiness failed'}
        Write-Output "READY: https://127.0.0.1:$($config.FrontendPort) | backend https://127.0.0.1:$($config.BackendPort) | DB ctsv_dev | logs $devHome/logs"
    }catch{Stop-DevOwned $state;if(Test-Path $stateFile){Remove-Item -LiteralPath $stateFile};throw}
}catch{Write-Error $_.Exception.Message;exit 1}
finally{if($locked){$mutex.ReleaseMutex()};$mutex.Dispose()}
