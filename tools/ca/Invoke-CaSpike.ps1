#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('ReadOnly','SignEOnce','VerifyDownload')][string]$Mode = 'ReadOnly',
    [string]$RunId = 'p09-20261119'
)
$ErrorActionPreference = 'Stop'
if ($RunId -notmatch '^[a-zA-Z0-9_-]{1,60}$') { throw 'RunId must contain letters, digits, underscore or hyphen.' }
$caPrivateDir = Join-Path $env:LOCALAPPDATA 'CTSV/ca-dut'
$caCredentialFile = Join-Path $caPrivateDir 'credential.clixml'
$caEnvNames = @('CA_DUT_URL','CA_DUT_CLIENT_ID','CA_DUT_CLIENT_SECRET','CA_DUT_RUN_DIR','CA_DUT_REAL_MODE')
$caPrevious = @{}
foreach ($caName in $caEnvNames) { $caPrevious[$caName] = [Environment]::GetEnvironmentVariable($caName,'Process') }
try {
    if (-not $env:CA_DUT_CLIENT_ID -or -not $env:CA_DUT_CLIENT_SECRET) {
        if (-not (Test-Path -LiteralPath $caCredentialFile)) { throw 'Missing CA environment/config. See docs/ca-dut.md; do not paste secrets into chat or logs.' }
        $caCredential = Import-Clixml -LiteralPath $caCredentialFile
        if ($caCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Invalid private CA credential file.' }
        $env:CA_DUT_CLIENT_ID = $caCredential.UserName
        $env:CA_DUT_CLIENT_SECRET = $caCredential.GetNetworkCredential().Password
    }
    if (-not $env:CA_DUT_URL) { $env:CA_DUT_URL = 'https://chukyso.dut.udn.vn' }
    $env:CA_DUT_REAL_MODE = @{ReadOnly='READ_ONLY';SignEOnce='SIGN_E_ONCE';VerifyDownload='VERIFY_DOWNLOAD'}[$Mode]
    $env:CA_DUT_RUN_DIR = Join-Path $caPrivateDir $RunId
    Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..'))
    try {
        & .\mvnw.cmd -o -B -ntp test-compile '-Dit.test=CaDutRealIT' failsafe:integration-test failsafe:verify
        if ($LASTEXITCODE -ne 0) { throw 'CA spike failed. Inspect safe Failsafe report; do not retry a side-effecting operation.' }
        Write-Output "CA evidence directory: $env:CA_DUT_RUN_DIR"
    } finally { Pop-Location }
} finally {
    foreach ($caName in $caEnvNames) { [Environment]::SetEnvironmentVariable($caName,$caPrevious[$caName],'Process') }
    $caCredential = $null
}
