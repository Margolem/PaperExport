$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path -LiteralPath '.\paper.jar')) { throw 'Run: rtk node scripts/setup-test-server.mjs from the repository root.' }
if (-not (Test-Path -LiteralPath '.\eula.txt')) {
  Write-Host 'Starting once so Paper can create eula.txt. Review it before continuing.'
}
& java -Xms512M -Xmx2G -jar paper.jar --nogui
