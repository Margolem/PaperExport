$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$legacy = (Resolve-Path -LiteralPath (Join-Path $workspace 'viewer')).Path
$replacement = (Resolve-Path -LiteralPath (Join-Path $workspace 'viewer-native')).Path
if ($legacy -ne (Join-Path $workspace 'viewer') -or
    $replacement -ne (Join-Path $workspace 'viewer-native') -or
    -not $legacy.StartsWith($workspace + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not $replacement.StartsWith($workspace + '\', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Viewer paths did not resolve inside the expected workspace.'
}
Remove-Item -LiteralPath $legacy -Recurse -Force
Move-Item -LiteralPath $replacement -Destination $legacy
Write-Host 'Native C# viewer is now the active viewer/ project.'
