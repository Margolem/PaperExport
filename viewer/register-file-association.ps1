$ErrorActionPreference = 'Stop'
$exe = Join-Path (Split-Path $PSScriptRoot -Parent) 'dist\PaperExport-Viewer-Windows.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw "Build the viewer first: $exe" }
$exe = (Resolve-Path -LiteralPath $exe).Path
$class = 'HKCU:\Software\Classes\PaperExport.Package'
$extension = 'HKCU:\Software\Classes\.paperexport'
New-Item -Path "$class\shell\open\command" -Force | Out-Null
Set-Item -Path $class -Value 'PaperExport Package'
Set-Item -Path "$class\shell\open\command" -Value ('"' + $exe + '" "%1"')
New-Item -Path "$extension\OpenWithProgids" -Force | Out-Null
New-ItemProperty -Path "$extension\OpenWithProgids" -Name 'PaperExport.Package' -Value '' -PropertyType String -Force | Out-Null
Write-Host 'Registered PaperExport Viewer under Open with for .paperexport files.'
