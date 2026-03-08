# Create GitHub Release and upload APK
# Usage: .\create-release.ps1 [version]
# Example: .\create-release.ps1 2.0.0
# Requires: $env:GITHUB_TOKEN set to a GitHub Personal Access Token (repo scope)

$ErrorActionPreference = "Stop"
$repo = "darkolacen/tv-app"
$version = if ($args[0]) { $args[0] } else { "1.0.0" }
$tag = "v$version"
$apkPath = "app\build\outputs\apk\release\app-release.apk"
$apkName = "app-release-v$version.apk"

if (-not $env:GITHUB_TOKEN) {
    Write-Host "GITHUB_TOKEN not set. Create a release manually:"
    Write-Host "  1. Open https://github.com/$repo/releases/new?tag=$tag"
    Write-Host "  2. Title: Release $tag"
    Write-Host "  3. Attach: $apkPath"
    exit 1
}

if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at $apkPath. Run: .\gradlew.bat assembleRelease"
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $env:GITHUB_TOKEN"
    "Accept"        = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}
$body = @{
    tag_name         = $tag
    name             = "Release $tag"
    body             = "TV app APK for installation on Android TV / devices."
} | ConvertTo-Json

$create = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $body -ContentType "application/json"
$uploadUrl = $create.upload_url -replace "\{\?name,label\}", "?name=$apkName"
$apkBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $apkPath))
Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{
    "Authorization" = "Bearer $env:GITHUB_TOKEN"
    "Accept"        = "application/vnd.github+json"
    "Content-Type"  = "application/vnd.android.package-archive"
} -Body $apkBytes
Write-Host "Release created: $($create.html_url)"
Write-Host "APK attached: $apkName"
