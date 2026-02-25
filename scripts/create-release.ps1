# Create GitHub Release v1.0.0 and upload APK
# Requires: $env:GITHUB_TOKEN set to a GitHub Personal Access Token (repo scope)
# Or run manually: https://github.com/darkolacen/tv-app/releases/new?tag=v1.0.0

$ErrorActionPreference = "Stop"
$repo = "darkolacen/tv-app"
$tag = "v1.0.0"
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not $env:GITHUB_TOKEN) {
    Write-Host "GITHUB_TOKEN not set. Create a release manually:"
    Write-Host "  1. Open https://github.com/darkolacen/tv-app/releases/new?tag=v1.0.0"
    Write-Host "  2. Title: Release v1.0.0"
    Write-Host "  3. Attach: $apkPath"
    exit 1
}

if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found. Run: .\gradlew.bat assembleDebug"
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
$uploadUrl = $create.upload_url -replace "\{\?name,label\}", "?name=app-debug.apk"
$apkBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $apkPath))
Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{
    "Authorization" = "Bearer $env:GITHUB_TOKEN"
    "Accept"        = "application/vnd.github+json"
    "Content-Type"  = "application/vnd.android.package-archive"
} -Body $apkBytes
Write-Host "Release created: $($create.html_url)"
Write-Host "APK attached: app-debug.apk"
