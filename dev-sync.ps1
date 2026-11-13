#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('setup','start','stop','status','sync','update','insert','delete')][string]$Action='status',
      [string]$SisRoot,
      [ValidateSet('all','units','ethnicities')][string]$Resource='all',
      [ValidateSet('auto','full','incremental')][string]$Mode='auto')
$ErrorActionPreference='Stop'
# PS5.1 does not initialize PSScriptRoot reliably inside a parameter default expression.
if(-not $SisRoot){$SisRoot=Join-Path $PSScriptRoot '../daotao'}
if($PSVersionTable.PSVersion.Major -lt 7){
    # Reuse the regular launcher's verified PS7 runtime pointer; no policy bypass.
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$hash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($PSScriptRoot.ToLowerInvariant())))).Replace('-','').Substring(0,12)}finally{$sha.Dispose()}
    $pointer=Join-Path $env:LOCALAPPDATA "CTSV/dev/$hash/powershell-runtime.txt"
    $candidates=@();if(Test-Path $pointer){$candidates+=(Get-Content $pointer -Raw).Trim()}
    $cmd=Get-Command pwsh.exe -ErrorAction SilentlyContinue;if($cmd){$candidates+=$cmd.Source};$candidates+=(Join-Path $env:ProgramFiles 'PowerShell/7/pwsh.exe')
    foreach($candidate in $candidates|Select-Object -Unique){if(Test-Path -LiteralPath $candidate -PathType Leaf){& $candidate -NoProfile -File $PSCommandPath -Action $Action -SisRoot $SisRoot -Resource $Resource -Mode $Mode;exit $LASTEXITCODE}}
    throw 'PowerShell7 required. Install Microsoft PS7, then run .\dev.ps1 status once.'
}
. "$PSScriptRoot/tools/dev/Dev.Common.ps1"
. "$PSScriptRoot/tools/dev/Sync.Common.ps1"
$root=$PSScriptRoot;$devHome=Get-DevHome $root;$syncHome=Join-Path $devHome 'sync'
$stateFile=Join-Path $syncHome 'provider-process.json'
$mutex=[Threading.Mutex]::new($false,'Local\CTSV-dev-sync-DEV2022');$locked=$false
try{
    try{$locked=$mutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$locked=$true}
    if(-not $locked){throw 'Another DEV sync command is active; wait. No simultaneous setup/mutation/sync.'}
    $devConfig=Read-DevConfig $devHome
    if($Action -eq 'stop'){
        if(Test-Path $stateFile){$state=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable;Stop-DevOwned @{Frontend=$null;Backend=$state};Remove-Item -LiteralPath $stateFile}
        Write-Output 'Stopped only the owned SIS Provider.';return
    }
    $SisRoot=(Resolve-Path -LiteralPath $SisRoot).Path
    if(-not (Test-Path (Join-Path $SisRoot 'src/main/java/pbl6/daotao/DaotaoApplication.java'))){throw 'SisRoot is not the daotao checkout'}
    Set-DevEnvironment $devHome $devConfig
    $cfgFile=Join-Path $syncHome 'config.json'
    if($Action -eq 'setup'){
        if(Test-Path $stateFile){$saved=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable;if(Test-DevProcessIdentity $saved (Get-DevProcess $saved.Pid)){throw 'Stop SIS Provider before setup'}}
        Invoke-DevDatabase $root $devHome check $devConfig
        foreach($folder in @($syncHome,(Join-Path $syncHome 'logs'),(Join-Path $syncHome 'classes'),(Join-Path $syncHome 'spool'))){New-Item -ItemType Directory -Path $folder -Force|Out-Null}
        if(-not (Test-Path $cfgFile)){
            @{SisRoot=$SisRoot;SourceEpoch=[guid]::NewGuid().ToString();ProviderPort=9443;ServiceKey=(Protect-DevSecret (New-DevSecret));TrustPassword=(Protect-DevSecret (New-DevSecret))}|ConvertTo-Json|Set-Content $cfgFile
        }
        $config=Get-Content $cfgFile -Raw|ConvertFrom-Json
        if($config.SisRoot -ne $SisRoot){throw 'SIS checkout differs from persisted sync identity; inspect, do not overwrite'}
        Write-SyncRuntime $root $SisRoot $devHome $syncHome $devConfig $config
        Build-SyncTools $root $SisRoot $devHome $syncHome
        Invoke-SyncTool $root $devHome $syncHome setup
        Write-Output 'Setup complete. No trust installed, no sync run. Next: .\dev-sync.ps1 start';return
    }
    if(-not (Test-Path $cfgFile)){throw 'Run .\dev-sync.ps1 setup first'}
    $config=Get-Content $cfgFile -Raw|ConvertFrom-Json
    if($config.SisRoot -ne $SisRoot){throw 'SIS checkout mismatch'}
    Write-SyncRuntime $root $SisRoot $devHome $syncHome $devConfig $config
    if($Action -eq 'start'){
        $cert=Get-DevCertificate $devHome $devConfig;$cert.Dispose()
        if(-not(Test-Path "Cert:\CurrentUser\Root\$($devConfig.Thumbprint)")){throw 'Review/trust own DEV leaf using dev.ps1; no automatic trust'}
        if((Get-Content (Join-Path $syncHome 'sis-source.sha256') -Raw).Trim() -ne (Get-DevSourceHash $SisRoot)){throw 'SIS source changed: stop provider then repeat dev-sync setup'}
        if(Get-NetTCPConnection -LocalPort $config.ProviderPort -State Listen -ErrorAction SilentlyContinue){throw 'Provider port occupied; never kill another owner'}
        Invoke-SyncTool $root $devHome $syncHome check
        $argsFile=Join-Path $syncHome 'provider.args'
        # CLI values outrank ambient SPRING_* variables; private @argfile keeps service/PFX secrets out of the OS command line.
        $launchArgs=@('-jar',('"'+(ConvertTo-DevProperty (Join-Path $SisRoot 'target/daotao-0.0.1-SNAPSHOT.jar'))+'"'),('--spring.config.location='+[uri]::new((Join-Path $syncHome 'provider.properties')).AbsoluteUri),'--spring.config.import=')
        $launchArgs+=Get-Content (Join-Path $syncHome 'provider.properties')|ForEach-Object{'"--'+$_+'"'}
        $launchArgs|Set-Content $argsFile
        $proc=Start-Process java.exe -ArgumentList ('"@'+$argsFile+'"') -WorkingDirectory $SisRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $syncHome 'logs/provider.log') -RedirectStandardError (Join-Path $syncHome 'logs/provider.err.log') -PassThru
        $saved=Get-DevProcess $proc.Id;$saved|ConvertTo-Json|Set-Content $stateFile
        try{
            $ready=$false
            for($i=0;$i -lt 90;$i++){
                if($proc.HasExited){throw 'SIS Provider exited; inspect private provider logs'}
                try{$r=Invoke-WebRequest (Get-SyncEndpoint "https://127.0.0.1:$($config.ProviderPort)" units) -SkipHttpErrorCheck -TimeoutSec 2
                    if($r.StatusCode -eq 401 -and (ConvertFrom-DevResponse $r.Content).code -eq 'INVALID_SERVICE_KEY'){$ready=$true;break}}catch{}
                Start-Sleep -Milliseconds 400
            }
            if(-not $ready){throw 'Provider HTTPS readiness timeout'}
        }catch{Stop-DevOwned @{Frontend=$null;Backend=$saved};Remove-Item -LiteralPath $stateFile;throw}
        Write-Output "SIS READY https://127.0.0.1:$($config.ProviderPort) | DB sis_dev | logs $syncHome/logs";return
    }
    if($Action -eq 'status'){
        $running=$false;if(Test-Path $stateFile){$saved=Get-Content $stateFile -Raw|ConvertFrom-Json -AsHashtable;$running=Test-DevProcessIdentity $saved (Get-DevProcess $saved.Pid)}
        Write-Output "SIS Provider owned process RUNNING=$running"
    }
    Invoke-SyncTool $root $devHome $syncHome $Action $Resource $Mode
}catch{Write-Error $_.Exception.Message;exit 1}
finally{if($locked){$mutex.ReleaseMutex()};$mutex.Dispose()}
