[CmdletBinding()]
param([string]$Archive)
$ErrorActionPreference = 'Stop'
$expectedZip = 'c0aa8d36a906437162ab99db10aeaa4ada91adab6caf6e041ca0af2992ff7948'
$expectedAar = 'f0cf100714d5fb15468c7e4cca126cef8007825ab7df63300af0e4438f25813c'
$staging = Join-Path ([IO.Path]::GetTempPath()) ('xjicloud-native3dgs-' + [guid]::NewGuid())
New-Item -ItemType Directory $staging | Out-Null
try {
    $zip = Join-Path $staging 'sdk.zip'
    if ($Archive) { Copy-Item -LiteralPath $Archive -Destination $zip } else {
        Invoke-WebRequest 'https://github.com/XJI1234/Native3DGSViewer/releases/download/v0.2.2/Native3DGS-SDK-0.2.2-Android-arm64-v8a.zip' -OutFile $zip
    }
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedZip) { throw 'SDK ZIP hash mismatch' }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $staging 'expanded')
    $package = Join-Path $staging 'expanded/Native3DGS-SDK-0.2.2-Android-arm64-v8a'
    $manifest = Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.version -ne '0.2.2' -or $manifest.files.Count -ne 51) { throw 'Unexpected SDK manifest' }
    foreach ($entry in $manifest.files) {
        $file = [IO.Path]::GetFullPath((Join-Path $package $entry.path))
        if (-not $file.StartsWith([IO.Path]::GetFullPath($package) + [IO.Path]::DirectorySeparatorChar)) { throw 'Manifest path escapes package' }
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw "SDK file hash mismatch: $($entry.path)" }
    }
    if ((Get-FileHash (Join-Path $package 'aar/native3dgs-sdk-release.aar') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedAar) { throw 'AAR hash mismatch' }
    $destination = Join-Path $PSScriptRoot '../vendor/native3dgs/0.2.2'
    New-Item -ItemType Directory -Force $destination | Out-Null
    foreach ($item in @('aar', 'docs', 'licenses', 'manifest.json', 'README.md')) {
        Copy-Item -LiteralPath (Join-Path $package $item) -Destination $destination -Recurse -Force
    }
    Write-Output 'Verified ZIP, all 51 manifest entries and AAR; installed AAR, original documentation and licenses.'
} finally {
    # This path is created by this invocation under the OS temporary directory.
    $resolved = [IO.Path]::GetFullPath($staging)
    if ($resolved.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) -and (Split-Path $resolved -Leaf).StartsWith('xjicloud-native3dgs-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
