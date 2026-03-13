param(
  [Parameter(Mandatory = $true)]
  [int]$MediaId,

  [string]$BaseUrl = "http://localhost:8080",
  [string]$Username = "admin",
  [string]$Password = "password"
)

$ErrorActionPreference = "Stop"

Write-Host "== 1) 登录获取 token =="
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginBody

if ($loginResp.code -ne 0 -or -not $loginResp.data.accessToken) {
  throw "登录失败: $($loginResp | ConvertTo-Json -Depth 6)"
}

$token = $loginResp.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

Write-Host "== 2) 请求字幕下载接口 =="
$langs = [uri]::EscapeDataString("Chinese (Simplified),Chinese (Traditional),Mandarin,Chinese,English")
$subtitleUrl = "$BaseUrl/api/media/$MediaId/subtitle?langs=$langs"

$outDir = Join-Path $PSScriptRoot "_tmp"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$outFile = Join-Path $outDir ("media-{0}-subtitle-test.srt" -f $MediaId)

$resp = Invoke-WebRequest -Method Get -Uri $subtitleUrl -Headers $headers -OutFile $outFile -PassThru

Write-Host "HTTP: $($resp.StatusCode)"
Write-Host "Content-Type: $($resp.Headers['Content-Type'])"

if (-not (Test-Path $outFile)) {
  throw "下载失败：文件未生成"
}

$fileSize = (Get-Item $outFile).Length
if ($fileSize -le 0) {
  throw "下载失败：文件大小为 0"
}

Write-Host "== 3) 检查后端缓存目录 =="
$cacheDir = "E:\Code\emby-mvp-backend\subtitles"
if (Test-Path $cacheDir) {
  $matched = Get-ChildItem $cacheDir -File | Where-Object { $_.Name -like "$MediaId-*" }
  if ($matched) {
    Write-Host "缓存命中："
    $matched | ForEach-Object { Write-Host " - $($_.FullName) ($($_.Length) bytes)" }
  } else {
    Write-Warning "缓存目录存在，但未发现 mediaId=$MediaId 对应文件"
  }
} else {
  Write-Warning "缓存目录不存在：$cacheDir"
}

Write-Host "\n✅ 测试通过：字幕接口可下载，文件路径：$outFile"
