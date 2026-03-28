param(
  [Parameter(Mandatory = $true)]
  [string]$BackupFile,

  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$Database = "emby_mvp",
  [string]$Username = "postgres",
  [string]$Password = "123456",

  [string]$PsqlPath = "E:\Program Files\PostgreSQL\18\bin\psql.exe",
  [string]$DropDbPath = "E:\Program Files\PostgreSQL\18\bin\dropdb.exe",
  [string]$CreateDbPath = "E:\Program Files\PostgreSQL\18\bin\createdb.exe",

  [switch]$RecreateDatabase,
  [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
  throw "Backup file not found: $BackupFile"
}

if (-not (Test-Path $PsqlPath)) { throw "psql not found: $PsqlPath" }
if ($RecreateDatabase -and -not (Test-Path $DropDbPath)) { throw "dropdb not found: $DropDbPath" }
if ($RecreateDatabase -and -not (Test-Path $CreateDbPath)) { throw "createdb not found: $CreateDbPath" }

$projectRoot = Split-Path -Parent $PSScriptRoot
$tempDir = Join-Path $projectRoot "db-backups\.restore-tmp"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

$sqlFile = $null

try {
  $ext = [System.IO.Path]::GetExtension($BackupFile).ToLowerInvariant()

  if ($ext -eq ".zip") {
    $extractDir = Join-Path $tempDir ("extract-" + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
    Expand-Archive -Path $BackupFile -DestinationPath $extractDir -Force

    $sqlCandidates = Get-ChildItem -Path $extractDir -Filter *.sql -Recurse | Sort-Object LastWriteTime -Descending
    if (-not $sqlCandidates) {
      throw "No .sql file found inside zip: $BackupFile"
    }
    $sqlFile = $sqlCandidates[0].FullName
  }
  elseif ($ext -eq ".sql") {
    $sqlFile = (Resolve-Path $BackupFile).Path
  }
  else {
    throw "Unsupported backup file type: $ext (only .sql/.zip)"
  }

  if (-not $Force) {
    Write-Host "WARNING: This will restore into database '$Database' on $HostName`:$Port and may overwrite existing data." -ForegroundColor Yellow
    $confirm = Read-Host "Type YES to continue"
    if ($confirm -ne "YES") {
      throw "Restore cancelled by user."
    }
  }

  $env:PGPASSWORD = $Password

  if ($RecreateDatabase) {
    Write-Host "Recreating database '$Database'..." -ForegroundColor Cyan

    & $DropDbPath --if-exists -h $HostName -p $Port -U $Username $Database
    if ($LASTEXITCODE -ne 0) { throw "dropdb failed." }

    & $CreateDbPath -h $HostName -p $Port -U $Username $Database
    if ($LASTEXITCODE -ne 0) { throw "createdb failed." }
  }

  Write-Host "Restoring from: $sqlFile" -ForegroundColor Cyan
  & $PsqlPath -h $HostName -p $Port -U $Username -d $Database -v ON_ERROR_STOP=1 -f $sqlFile
  if ($LASTEXITCODE -ne 0) { throw "psql restore failed." }

  Write-Output "Restore complete"
  Write-Output "Database: $Database"
  Write-Output "Source: $BackupFile"
}
finally {
  Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  if (Test-Path $tempDir) {
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
  }
}
