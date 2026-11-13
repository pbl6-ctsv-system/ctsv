Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Get-SyncJdbcUrl([int]$Port,[string]$Database){
    if($Database -notin @('sis_dev','ctsv_dev')){throw 'Only sis_dev and ctsv_dev are permitted'}
    (Get-DevJdbcUrl $Port).Replace('databaseName=ctsv_dev;',"databaseName=$Database;")
}
function Get-SyncEndpoint([string]$Base,[string]$Resource){
    $uri=[uri]$Base
    if($uri.Scheme -ne 'https' -or $uri.Host -ne '127.0.0.1' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/'){throw 'DEV provider must use HTTPS 127.0.0.1 origin'}
    $path=switch($Resource){units{'don-vi'} ethnicities{'dan-toc'} default{throw 'Only units and ethnicities in DEV demo'}}
    "$($Base.TrimEnd('/'))/api/dao-tao/$path"
}
function Build-SyncTools([string]$Root,[string]$SisRoot,[string]$HomePath,[string]$SyncHome){
    Push-Location $SisRoot
    try{
        & .\mvnw.cmd -o -B -ntp '-Dmaven.test.skip=true' package *> (Join-Path $SyncHome 'logs/sis-build.log')
        if($LASTEXITCODE -ne 0){throw "SIS build failed: $SyncHome/logs/sis-build.log. Prepare Maven cache online per guide."}
        Get-DevSourceHash $SisRoot|Set-Content (Join-Path $SyncHome 'sis-source.sha256')
    }finally{Pop-Location}
    $cp=(Join-Path $Root 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $HomePath 'classpath.txt') -Raw).Trim()
    $argsFile=Join-Path $SyncHome 'compile.args'
    @('-encoding','UTF-8','-cp',('"'+(ConvertTo-DevProperty $cp)+'"'),'-d',('"'+(ConvertTo-DevProperty (Join-Path $SyncHome 'classes'))+'"'),('"'+(ConvertTo-DevProperty (Join-Path $Root 'tools/dev/java/pbl6/ctsv/dev/SyncDev.java'))+'"'))|Set-Content $argsFile
    & javac.exe "@$argsFile" *> (Join-Path $SyncHome 'logs/compile.log')
    if($LASTEXITCODE -ne 0){throw "Sync helper compile failed: $SyncHome/logs/compile.log"}
    & javac.exe -encoding UTF-8 -cp ((Join-Path $SyncHome 'classes')+';'+$cp) -d (Join-Path $SyncHome 'classes') (Join-Path $Root 'tools/dev/java/pbl6/ctsv/dev/SyncDevChecks.java') *> (Join-Path $SyncHome 'logs/checks-compile.log')
    if($LASTEXITCODE -ne 0){throw 'Sync guard checks compile failed'}
    & java.exe -cp ((Join-Path $SyncHome 'classes')+';'+$cp) pbl6.ctsv.dev.SyncDevChecks *> (Join-Path $SyncHome 'logs/guard-checks.log')
    if($LASTEXITCODE -ne 0){throw 'Sync guard checks failed'}
}
function Invoke-SyncTool([string]$Root,[string]$HomePath,[string]$SyncHome,[string]$Mode,[string]$Resource='all',[string]$SyncMode='auto'){
    $cp=(Join-Path $SyncHome 'classes')+';'+(Join-Path $Root 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $HomePath 'classpath.txt') -Raw).Trim()
    $argsFile=Join-Path $SyncHome 'command.args'
    $argsList=@('-cp',('"'+(ConvertTo-DevProperty $cp)+'"'),'pbl6.ctsv.dev.SyncDev',$Mode)
    if($Mode -eq 'sync'){$argsList+=@($Resource,$SyncMode)}
    $argsList|Set-Content $argsFile
    $env:CTSV_SYNC_HOME=$SyncHome
    & java.exe "@$argsFile" *> (Join-Path $SyncHome "logs/$Mode.log")
    if($LASTEXITCODE -ne 0){throw "Sync DEV $Mode failed: $SyncHome/logs/$Mode.log. No checkpoint reset or test-DB fallback."}
    # Tool output contains only stage/IDs/checkpoints/counts, never credentials.
    Get-Content (Join-Path $SyncHome "logs/$Mode.log")|Where-Object{$_ -match '^(DEV targets|Source registry|units |ethnicities )'}|Write-Output
}
function Write-SyncRuntime([string]$Root,[string]$SisRoot,[string]$HomePath,[string]$SyncHome,$DevConfig,$Config){
    $sqlPort=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
    $key=Open-DevSecret $Config.ServiceKey
    $pfx=Open-DevSecret $DevConfig.PfxPassword
    $common=@('spring.profiles.active=local','spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration','spring.flyway.enabled=false','spring.jpa.hibernate.ddl-auto=validate','spring.jpa.open-in-view=false','server.address=127.0.0.1',"server.port=$($Config.ProviderPort)",'server.ssl.enabled=true','server.ssl.key-store-type=PKCS12',('server.ssl.key-store='+[uri]::new((Join-Path $HomePath 'https.pfx')).AbsoluteUri),"server.ssl.key-store-password=$pfx",('spring.datasource.url='+(Get-SyncJdbcUrl $sqlPort sis_dev)),'slice-a.enabled=true','ct26.enabled=true',"SIS_SOURCE_EPOCH=$($Config.SourceEpoch)","sis.units.service-keys.dev=$key",'sis.units.service-resources.dev=units',"sis.ct26.service-keys.dev=$key",'sis.ct26.service-resources.dev[0]=ethnicities',('sis.ct26.spool-directory='+(ConvertTo-DevProperty (Join-Path $SyncHome 'spool'))))
    [IO.File]::WriteAllLines((Join-Path $SyncHome 'provider.properties'),$common,[Text.UTF8Encoding]::new($false))
    $props=@(('sis.root='+(ConvertTo-DevProperty $SisRoot)),('ctsv.root='+(ConvertTo-DevProperty $Root)),('sis.jdbc.url='+(Get-SyncJdbcUrl $sqlPort sis_dev)),('ctsv.jdbc.url='+(Get-SyncJdbcUrl $sqlPort ctsv_dev)),"provider.url=https://127.0.0.1:$($Config.ProviderPort)","source.epoch=$($Config.SourceEpoch)","service.key=$key",('trust.password='+(Open-DevSecret $Config.TrustPassword)),('certificate='+(ConvertTo-DevProperty (Join-Path $HomePath 'https-cert.pem'))))
    [IO.File]::WriteAllLines((Join-Path $SyncHome 'sync.properties'),$props,[Text.UTF8Encoding]::new($false))
}
