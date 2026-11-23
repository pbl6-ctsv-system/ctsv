Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Get-DevHome([string]$Root) {
    $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Root.ToLowerInvariant()))).Substring(0,12)
    Join-Path $env:LOCALAPPDATA "CTSV/dev/$hash"
}
function ConvertTo-DevProperty([string]$Value) { $Value.Replace('\','/').Replace("`r",'').Replace("`n",'') }
function ConvertFrom-DevResponse($Content) {
    if($Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($Content)|ConvertFrom-Json}else{$Content|ConvertFrom-Json}
}
function Get-DevJdbcUrl([int]$Port) {
    if($Port -lt 1 -or $Port -gt 65535){throw 'SQL TCP port invalid'}
    "jdbc:sqlserver://localhost:$Port;databaseName=ctsv_dev;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;connectRetryCount=0"
}
function Test-DevProcessIdentity($Saved,$Actual) {
    if($null -eq $Actual){return $false}
    $created=if($Saved.Created -is [datetime]){$Saved.Created.ToUniversalTime().ToString('o')}else{[string]$Saved.Created}
    $Saved.Pid -eq $Actual.Pid -and $created -eq $Actual.Created -and $Saved.Command -eq $Actual.Command
}
function Get-DevProcess([int]$ProcessId) {
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if($p){@{Pid=[int]$p.ProcessId;Created=$p.CreationDate.ToUniversalTime().ToString('o');Command=$p.CommandLine}}
}
function Read-DevConfig([string]$HomePath) {
    $file=Join-Path $HomePath 'config.json'
    if(-not (Test-Path -LiteralPath $file)){throw 'Chua setup: chay .\dev.ps1 setup'}
    Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
}
function Open-DevSecret([string]$Encrypted) {
    $secure=ConvertTo-SecureString $Encrypted
    [System.Net.NetworkCredential]::new('local',$secure).Password
}
function Protect-DevSecret([string]$Plain) { ConvertFrom-SecureString (ConvertTo-SecureString $Plain -AsPlainText -Force) }
function Get-DevCaEnvironment([string]$CredentialDirectory) {
    $path=Join-Path $CredentialDirectory 'credential.clixml'
    if(-not (Test-Path -LiteralPath $path)){return @{}}
    try{
        $credential=Import-Clixml -LiteralPath $path
        if($credential -isnot [pscredential] -or [string]::IsNullOrWhiteSpace($credential.UserName) -or $credential.Password.Length -eq 0){throw 'Invalid local credential'}
        return @{CA_DUT_URL='https://chukyso.dut.udn.vn';CA_DUT_CLIENT_ID=$credential.UserName;CA_DUT_CLIENT_SECRET=$credential.GetNetworkCredential().Password}
    }catch{throw 'Cannot load local CA DUT credential with this Windows account. Existing configuration was kept; no secret was logged.'}
}
function Get-DevCloudinaryEnvironment([string]$HomePath) {
    $values=@{}
    $credentialNames=@('CLOUDINARY_CLOUD_NAME','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET')
    $file=Join-Path $HomePath 'cloudinary.dpapi.json'
    if(Test-Path -LiteralPath $file){
        try{
            $saved=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json -AsHashtable
            foreach($name in $credentialNames){$values[$name]=Open-DevSecret $saved[$name]}
        }catch{throw 'Cannot read CTSV DEV Cloudinary configuration. Re-enter it with .\dev.ps1 storage on this Windows account.'}
    }else{
        foreach($name in ($credentialNames+@('CLOUDINARY_FOLDER','CLOUDINARY_FOLDER_MODE'))){
            $values[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
        }
    }
    if([string]::IsNullOrWhiteSpace($values['CLOUDINARY_FOLDER'])){$values['CLOUDINARY_FOLDER']='ctsv'}
    if([string]::IsNullOrWhiteSpace($values['CLOUDINARY_FOLDER_MODE'])){$values['CLOUDINARY_FOLDER_MODE']='dynamic'}
    return $values
}
function Get-DevFileProperties([hashtable]$Values) {
    $missing=@('CLOUDINARY_CLOUD_NAME','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET','CLOUDINARY_FOLDER','CLOUDINARY_FOLDER_MODE')|
        Where-Object{[string]::IsNullOrWhiteSpace($Values[$_])}
    'ctsv.files.enabled=true'
    'ctsv.files.cloudinary.enabled='+(-not $missing).ToString().ToLowerInvariant()
}
function New-DevSecret {
    [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
}
# Public fixture credential, ONLY for the guarded synthetic ctsv_dev identities.
function Get-DevPresetPassword { 'CtsvDev@2026!' }
function Initialize-DevFiles([string]$Root,[string]$HomePath) {
    if(-not (Test-Path -LiteralPath $HomePath)){New-Item -ItemType Directory -Path $HomePath -Force|Out-Null}
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $HomePath /inheritance:r /grant:r "*$($sid):(OI)(CI)F" | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Cannot secure local dev directory ACL'}
    foreach($folder in @('logs','classes')){New-Item -ItemType Directory -Path (Join-Path $HomePath $folder) -Force|Out-Null}
    $file=Join-Path $HomePath 'config.json'
    if(Test-Path -LiteralPath $file){$config=Read-DevConfig $HomePath;if($config.Root -ne $Root){throw 'Checkout mismatch'};return $config}
    # Leaf only, IP SAN, ServerAuth only; generation does not install Root trust.
    $cert=New-SelfSignedCertificate -Subject 'CN=127.0.0.1' -FriendlyName 'CTSV local DEV leaf HTTPS' -Type Custom -CertStoreLocation Cert:\CurrentUser\My -KeyAlgorithm RSA -KeyLength 2048 -KeyExportPolicy Exportable -KeyUsage DigitalSignature,KeyEncipherment -NotAfter (Get-Date).AddDays(180) -TextExtension @('2.5.29.17={text}IPAddress=127.0.0.1','2.5.29.19={critical}{text}ca=false','2.5.29.37={text}1.3.6.1.5.5.7.3.1')
    $pfxPassword=New-DevSecret
    Export-PfxCertificate -Cert $cert -FilePath (Join-Path $HomePath 'https.pfx') -Password (ConvertTo-SecureString $pfxPassword -AsPlainText -Force)|Out-Null
    [IO.File]::WriteAllText((Join-Path $HomePath 'https-cert.pem'),$cert.ExportCertificatePem())
    $tlsKey=[Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($cert)
    try{[IO.File]::WriteAllText((Join-Path $HomePath 'https-key.pem'),$tlsKey.ExportPkcs8PrivateKeyPem())}finally{$tlsKey.Dispose()}
    $jwt=[Security.Cryptography.RSA]::Create(3072)
    try{
        [IO.File]::WriteAllText((Join-Path $HomePath 'jwt-private.pem'),$jwt.ExportPkcs8PrivateKeyPem())
        [IO.File]::WriteAllText((Join-Path $HomePath 'jwt-public.pem'),$jwt.ExportSubjectPublicKeyInfoPem())
    }finally{$jwt.Dispose()}
    $config=[ordered]@{Root=$Root;Thumbprint=$cert.Thumbprint;BackendPort=8443;FrontendPort=5173;PfxPassword=(Protect-DevSecret $pfxPassword);Accounts=@{admin=@{Username='dev.admin';Password=(Protect-DevSecret (Get-DevPresetPassword))};specialist=@{Username='dev.chuyenvien';Password=(Protect-DevSecret (Get-DevPresetPassword))};student=@{Username='dev.sinhvien';Password=(Protect-DevSecret (Get-DevPresetPassword))}}}
    $config|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $file -Encoding utf8
    Read-DevConfig $HomePath
}
function Get-DevCertificate([string]$HomePath,$Config) {
    $cert=[Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText((Join-Path $HomePath 'https-cert.pem')))
    if($cert.Thumbprint -ne $Config.Thumbprint -or $cert.Subject -ne 'CN=127.0.0.1'){throw 'Certificate mismatch; stop and inspect dev config'}
    if($cert.NotAfter -le (Get-Date).AddMinutes(5)){throw 'HTTPS certificate expired or expiring; see README renewal'}
    $cert
}
function Show-DevCertificate([string]$HomePath,$Config) {
    $cert=Get-DevCertificate $HomePath $Config
    try{
        [pscustomobject]@{Subject=$cert.Subject;Issuer=$cert.Issuer;Thumbprint=$cert.Thumbprint;NotAfter=$cert.NotAfter;SAN=($cert.Extensions|Where-Object{$_.Oid.Value -eq '2.5.29.17'}).Format($false);BasicConstraints=($cert.Extensions|Where-Object{$_.Oid.Value -eq '2.5.29.19'}).Format($false);EKU=($cert.Extensions|Where-Object{$_.Oid.Value -eq '2.5.29.37'}).Format($false);Trusted=(Test-Path -LiteralPath "Cert:\CurrentUser\Root\$($Config.Thumbprint)");Home=$HomePath}|Format-List|Out-Host
    }finally{$cert.Dispose()}
}
function Set-DevEnvironment([string]$HomePath,$Config,[hashtable]$Cloudinary=@{}) {
    $port=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
    if(-not $port){throw 'DEV2022 dynamic TCP port not found; inspect SQL Server TCP settings'}
    $dll=Join-Path $env:USERPROFILE '.m2/repository/com/microsoft/sqlserver/mssql-jdbc_auth/12.10.2.x64'
    if(-not (Test-Path -LiteralPath $dll)){throw 'Missing previously verified SQL integrated authentication DLL'}
    $env:Path=$dll+';'+$env:Path
    $env:NODE_EXTRA_CA_CERTS=Join-Path $HomePath 'https-cert.pem'
    $env:P04_TLS_CERT=$env:NODE_EXTRA_CA_CERTS
    $env:P04_TLS_KEY=Join-Path $HomePath 'https-key.pem'
    $env:P04_BACKEND_URL="https://127.0.0.1:$($Config.BackendPort)"
    $env:CTSV_DEV_HOME=$HomePath
    $props=@(
      'spring.profiles.active=dev',
      'ctsv.mghp.applications.enabled=true',
      ('spring.datasource.url='+(Get-DevJdbcUrl $port)),
      'server.address=127.0.0.1',"server.port=$($Config.BackendPort)",
      'server.ssl.enabled=true','server.ssl.key-store-type=PKCS12',
      ('server.ssl.key-store='+[Uri]::new((Join-Path $HomePath 'https.pfx')).AbsoluteUri),
      ('server.ssl.key-store-password='+(Open-DevSecret $Config.PfxPassword)),
      "ctsv.auth.issuer=https://127.0.0.1:$($Config.BackendPort)",
      'ctsv.auth.audience=ctsv','ctsv.auth.signing-kid=local-dev',
      ('ctsv.auth.private-key='+(ConvertTo-DevProperty (Join-Path $HomePath 'jwt-private.pem'))),
      ('ctsv.auth.public-keys[local-dev]='+(ConvertTo-DevProperty (Join-Path $HomePath 'jwt-public.pem'))),
      "ctsv.auth.origins[0]=https://127.0.0.1:$($Config.FrontendPort)",
      'ctsv.auth.trusted-sources[0]=SIS_MOCK') + @(Get-DevFileProperties $Cloudinary)
    [IO.File]::WriteAllLines((Join-Path $HomePath 'application-dev.properties'),$props,[Text.UTF8Encoding]::new($false))
}
function Get-DevSourceHash([string]$Root) {
    $items=@(Get-ChildItem (Join-Path $Root 'src/main') -Recurse -File)+@(Get-Item (Join-Path $Root 'pom.xml'))
    $values=($items|Sort-Object FullName|ForEach-Object{ $_.FullName+'='+(Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($values)))
}
function Build-DevApplication([string]$Root,[string]$HomePath) {
    Push-Location $Root
    try{
        & .\mvnw.cmd -o -B -ntp '-Dmaven.test.skip=true' package *> (Join-Path $HomePath 'logs/build.log')
        if($LASTEXITCODE -ne 0){throw "Build failed; see $HomePath/logs/build.log"}
        & .\mvnw.cmd -o -B -ntp dependency:build-classpath "-Dmdep.outputFile=$HomePath/classpath.txt" *> (Join-Path $HomePath 'logs/classpath.log')
        if($LASTEXITCODE -ne 0){throw "Dependency classpath failed; see $HomePath/logs/classpath.log"}
        $classpath=(Join-Path $Root 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $HomePath 'classpath.txt') -Raw).Trim()
        $argsFile=Join-Path $HomePath 'javac.args'
        @('-encoding','UTF-8','-cp',('"'+(ConvertTo-DevProperty $classpath)+'"'),'-d',('"'+(ConvertTo-DevProperty (Join-Path $HomePath 'classes'))+'"'),('"'+(ConvertTo-DevProperty (Join-Path $Root 'tools/dev/java/pbl6/ctsv/auth/DevDatabase.java'))+'"'))|Set-Content -LiteralPath $argsFile
        & javac.exe "@$argsFile" *> (Join-Path $HomePath 'logs/dev-tool-compile.log')
        if($LASTEXITCODE -ne 0){throw "Dev helper compile failed; see $HomePath/logs/dev-tool-compile.log"}
        Get-DevSourceHash $Root|Set-Content (Join-Path $HomePath 'build-source.sha256')
    }finally{Pop-Location}
}
function Invoke-DevDatabase([string]$Root,[string]$HomePath,[string]$Mode,$Config) {
    $classPath=(Join-Path $HomePath 'classes')+';'+(Join-Path $Root 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $HomePath 'classpath.txt') -Raw).Trim()
    if($Mode -eq 'setup'){
        foreach($role in @('admin','student','specialist')){[Environment]::SetEnvironmentVariable('CTSV_DEV_'+$role.ToUpperInvariant()+'_PASSWORD',(Open-DevSecret $Config.Accounts.$role.Password),'Process')}
    }
    try{
        $argsFile=Join-Path $HomePath 'db.args'
        @('-cp',('"'+(ConvertTo-DevProperty $classPath)+'"'),'pbl6.ctsv.auth.DevDatabase',$Mode)|Set-Content -LiteralPath $argsFile
        & java.exe "@$argsFile" *> (Join-Path $HomePath "logs/db-$Mode.log")
        if($LASTEXITCODE -ne 0){throw "Dev DB $Mode failed; see $HomePath/logs/db-$Mode.log (no test DB fallback)"}
    }finally{foreach($role in @('admin','student','specialist')){[Environment]::SetEnvironmentVariable('CTSV_DEV_'+$role.ToUpperInvariant()+'_PASSWORD',$null,'Process')}}
}
function Stop-DevOwned($State) {
    foreach($saved in @($State.Frontend,$State.Backend)){
        if(-not $saved){continue}
        $actual=Get-DevProcess $saved.Pid
        if(-not (Test-DevProcessIdentity $saved $actual)){if($actual){Write-Warning "PID $($saved.Pid) changed; refusing stop"};continue}
        # Only descendants of this verified owned root; creation stamp protects against PID reuse.
        $all=@(Get-CimInstance Win32_Process);$owned=@([int]$saved.Pid);$children=@()
        for($i=0;$i -lt $owned.Count;$i++){
            foreach($child in @($all|Where-Object{$_.ParentProcessId -eq $owned[$i]})){
                if($child.CreationDate.ToUniversalTime() -ge [datetime]$saved.Created){$owned+=[int]$child.ProcessId;$children+=@{Pid=[int]$child.ProcessId;Created=$child.CreationDate.ToUniversalTime().ToString('o');Command=$child.CommandLine}}
            }
        }
        [array]::Reverse($children)
        foreach($child in $children){if(Test-DevProcessIdentity $child (Get-DevProcess $child.Pid)){Stop-Process -Id $child.Pid -ErrorAction SilentlyContinue}}
        if(Test-DevProcessIdentity $saved (Get-DevProcess $saved.Pid)){Stop-Process -Id $saved.Pid -ErrorAction SilentlyContinue}
    }
}
