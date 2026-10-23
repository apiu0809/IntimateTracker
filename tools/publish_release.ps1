param(
    [Parameter(Mandatory = $true)][string]$Version,   # 例：v1.2.1
    [string]$Apk,                                      # 默认 releases\IntimateTracker-<ver>-arm64-v8a.apk
    [switch]$Draft,
    [switch]$SkipAsset
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$repo = "apiu0809/IntimateTracker"
$api = "https://api.github.com/repos/$repo"
$ver = $Version.TrimStart("v")

$cred = ("protocol=https`nhost=github.com`n`n" | git credential fill 2>$null) -split "`n"
$token = ($cred | Where-Object { $_ -like "password=*" } | ForEach-Object { $_ -replace "password=", "" })
if (-not $token) { throw "未找到 GitHub 凭据" }
$env:GH_TMP = $token
$headers = @{ Authorization = "token $token"; "User-Agent" = "dsh"; Accept = "application/vnd.github+json" }

# 从 CHANGELOG.md 提取该版本小节
$changelog = [System.IO.File]::ReadAllText((Join-Path $repoRoot "CHANGELOG.md"), [System.Text.Encoding]::UTF8)
$m = [regex]::Match($changelog, "(?ms)^## \[$([regex]::Escape($ver))\][^\n]*\n(?<body>.*?)(?=^## \[|^\[Unreleased\]:|\z)")
$section = if ($m.Success) { $m.Groups["body"].Value.Trim() } else { "- 见仓库 CHANGELOG.md" }

if (-not $Apk) { $Apk = Join-Path $repoRoot "releases\IntimateTracker-$Version-arm64-v8a.apk" }
$apkPath = (Resolve-Path $Apk).Path
$apkName = Split-Path $apkPath -Leaf
$sha = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLower()

# 从 build.gradle.kts 读 versionCode
$gradle = [System.IO.File]::ReadAllText((Join-Path $repoRoot "app\build.gradle.kts"))
$code = [regex]::Match($gradle, "versionCode\s*=\s*(\d+)").Groups[1].Value

$body = @"
### 更新内容

$section

### 安装

1. 下载 ``$apkName``
2. 允许「安装未知来源应用」后直接安装（可覆盖旧版本）
3. 或使用 adb：``adb install -r $apkName``

### 版本信息

- 版本：``$ver (versionCode $code)``
- 包名：``com.mysecret.intimatetracker``
- 架构：``arm64-v8a``
- 系统：Android 8.0+（minSdk 26 / targetSdk 36）
- SHA-256：``$sha``
"@

$title = "$Version · 亲密记录"

# 已存在则更新，否则创建
# 远端必须已有该 tag，否则 GitHub 会创建指向错误提交的 tag，且 Release 可能变成 draft
$remoteTag = git ls-remote --tags origin "refs/tags/$Version" 2>$null
if (-not $remoteTag) {
    throw "远端缺少 tag $Version：请先 git push origin $Version 再发布"
}

$existing = $null
try { $existing = Invoke-RestMethod -Uri "$api/releases/tags/$Version" -Headers $headers } catch { }

$payload = @{ name = $title; body = $body; draft = [bool]$Draft; prerelease = $false }
if (-not $existing) { $payload["tag_name"] = $Version }
$json = $payload | ConvertTo-Json -Depth 3
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

if ($existing) {
    Invoke-RestMethod -Uri "$api/releases/$($existing.id)" -Method Patch -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8" | Out-Null
    $release = $existing
    Write-Host "更新已有 Release: $Version"
} else {
    $release = Invoke-RestMethod -Uri "$api/releases" -Method Post -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8"
    Write-Host "创建 Release: $Version (id=$($release.id))"
}

if (-not $SkipAsset) {
    $hasAsset = $release.assets | Where-Object { $_.name -eq $apkName }
    if (-not $hasAsset) {
        $url = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$apkName"
        # 大文件直传容易被中间链路重置：限速 + 关闭 Expect + 全错误重试（实测 250~800k 都可能被 reset）
        $resp = & curl.exe -sS --http1.1 --retry 5 --retry-delay 5 --retry-all-errors --limit-rate 400k -H "Expect:" -X POST -H "Authorization: token $env:GH_TMP" -H "Accept: application/vnd.github+json" `
            -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$apkPath" $url
        $uploaded = $resp -match '"state"'
        if (-not $uploaded) {
            # curl 偶发 connection reset，但文件可能已经传完：回查资产
            Start-Sleep -Seconds 3
            $check = Invoke-RestMethod -Uri "$api/releases/$($release.id)" -Headers $headers
            $uploaded = [bool]($check.assets | Where-Object { $_.name -eq $apkName })
        }
        if ($uploaded) { Write-Host "已上传 $apkName" } else { Write-Host "上传失败: $resp" }
    } else {
        Write-Host "资产已存在，跳过上传"
    }
}

$env:GH_TMP = ""
Write-Host "完成：https://github.com/$repo/releases/tag/$Version"
