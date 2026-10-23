param(
    [switch]$WhatIfOnly
)

$ErrorActionPreference = "Stop"

# 从 Windows 凭据管理器取 GitHub token（不打印）
$cred = ("protocol=https`nhost=github.com`n`n" | git credential fill 2>$null) -split "`n"
$token = ($cred | Where-Object { $_ -like "password=*" } | ForEach-Object { $_ -replace "password=", "" })
if (-not $token) { throw "no github token" }

$headers = @{ Authorization = "token $token"; "User-Agent" = "dsh"; Accept = "application/vnd.github+json" }
$repo = "apiu0809/IntimateTracker"
$base = "https://api.github.com/repos/$repo"

# 解析 CHANGELOG.md
$changelog = [System.IO.File]::ReadAllText((Resolve-Path "CHANGELOG.md").Path, [System.Text.Encoding]::UTF8)
$sections = @{}
$matches = [regex]::Matches($changelog, "(?ms)^## \[(?<ver>[0-9]+\.[0-9]+\.[0-9]+)\][^\n]*\n(?<body>.*?)(?=^## \[|^\[Unreleased\]:|\z)")
foreach ($m in $matches) {
    $body = $m.Groups["body"].Value.Trim()
    $body = ($body -split "`n" | Where-Object { $_ -notmatch '^\[[0-9]+\.[0-9]+\.[0-9]+\]:' }) -join "`n"
    $sections[$m.Groups["ver"].Value] = $body.Trim()
}

$versionCodes = @{
    "1.0.0" = 1; "1.0.1" = 2; "1.0.2" = 3; "1.0.3" = 4; "1.0.4" = 5; "1.0.5" = 6;
    "1.0.6" = 7; "1.0.7" = 8; "1.0.8" = 9; "1.0.9" = 10; "1.1.0" = 11; "1.1.1" = 12;
    "1.1.2" = 13; "1.1.3" = 14; "1.1.4" = 15; "1.1.5" = 16; "1.1.6" = 17; "1.2.0" = 18
}

function Build-Body {
    param([string]$Version, [string]$Changelog, [string]$Digest, [string]$AssetName)

    $lines = @()
    $lines += "### 更新内容"
    $lines += ""
    if ($Changelog) { $lines += $Changelog } else { $lines += "- 首个内部构建（S1–S4 开发期）" }
    $lines += ""
    $lines += "### 安装"
    $lines += ""
    $lines += "1. 下载 ``$AssetName``"
    $lines += "2. 允许「安装未知来源应用」后直接安装（可覆盖旧版本）"
    $lines += "3. 或使用 adb：``adb install -r $AssetName``"
    $lines += ""
    $lines += "### 版本信息"
    $lines += ""
    $code = $versionCodes[$Version]
    $codeText = if ($code) { " (versionCode $code)" } else { "" }
    $lines += "- 版本：``$Version$codeText``"
    $lines += "- 包名：``com.mysecret.intimatetracker``"
    $lines += "- 架构：``arm64-v8a``"
    $lines += "- 系统：Android 8.0+（minSdk 26 / targetSdk 36）"
    if ($Digest) { $lines += "- SHA-256：``$($Digest -replace '^sha256:', '')``" }
    return ($lines -join "`n")
}

$releases = Invoke-RestMethod -Uri "$base/releases?per_page=100" -Headers $headers
$updated = 0
foreach ($rel in $releases) {
    $tag = $rel.tag_name
    $ver = $tag.TrimStart("v")
    $asset = $rel.assets | Select-Object -First 1
    $digest = if ($asset) { $asset.digest } else { $null }
    $body = Build-Body -Version $ver -Changelog $sections[$ver] -Digest $digest -AssetName $(if ($asset) { $asset.name } else { "IntimateTracker-v$ver-arm64-v8a.apk" })
    $title = if ($ver -eq "0.1.0") { "0.1.0 · 亲密记录（开发版）" } else { "$tag · 亲密记录" }

    Write-Host ("[{0}] name='{1}' body={2} chars" -f $tag, $title, $body.Length)
    if (-not $WhatIfOnly) {
        $payload = @{ name = $title; body = $body } | ConvertTo-Json -Depth 3
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
        Invoke-RestMethod -Uri "$base/releases/$($rel.id)" -Method Patch -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8" | Out-Null
    }
    $updated++
}
Write-Host "updated: $updated"
