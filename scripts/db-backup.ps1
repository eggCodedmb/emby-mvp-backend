param(
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$Database = "emby_mvp",
  [string]$Username = "postgres",
  [string]$Password = "123456",
  [string]$PgDumpPath = "E:\Program Files\PostgreSQL\18\bin\pg_dump.exe",
  [int]$KeepLatest = 10
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $projectRoot "db-backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

if (-not (Test-Path $PgDumpPath)) {
  throw "pg_dump not found: $PgDumpPath"
}

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$sqlFile = Join-Path $backupDir "$Database-$ts.sql"
$zipFile = Join-Path $backupDir "$Database-$ts.zip"

$env:PGPASSWORD = $Password
& $PgDumpPath -h $HostName -p $Port -U $Username -d $Database -F p -f $sqlFile
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $sqlFile)) {
  throw "Backup failed."
}

if (Test-Path $zipFile) { Remove-Item $zipFile -Force }
Compress-Archive -Path $sqlFile -DestinationPath $zipFile -CompressionLevel Optimal

# Keep only latest N .sql and N .zip files
Get-ChildItem $backupDir -Filter "$Database-*.sql" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -Skip $KeepLatest |
  Remove-Item -Force -ErrorAction SilentlyContinue

Get-ChildItem $backupDir -Filter "$Database-*.zip" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -Skip $KeepLatest |
  Remove-Item -Force -ErrorAction SilentlyContinue

Write-Output "Backup complete"
Write-Output "SQL: $sqlFile"
Write-Output "ZIP: $zipFile"
