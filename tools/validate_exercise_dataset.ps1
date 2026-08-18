param(
    [switch]$RequireMedia
)

$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '..\app\src\main\assets\exercise_dataset'
$records = Get-Content -Raw (Join-Path $root 'data\exercises.json') | ConvertFrom-Json
if ($records.Count -ne 1324) { throw "Expected 1324 exercises, got $($records.Count)" }
if (($records.id | Sort-Object -Unique).Count -ne 1324) { throw 'Exercise IDs are not unique' }
$missing = @()
foreach ($record in $records) {
    if ([string]::IsNullOrWhiteSpace($record.attribution)) { $missing += "$($record.id):attribution" }
    if ($RequireMedia) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $record.image))) { $missing += "$($record.id):image" }
        if (-not (Test-Path -LiteralPath (Join-Path $root $record.gif_url))) { $missing += "$($record.id):gif" }
    }
}
if ($missing.Count) { $sample = ($missing | Select-Object -First 10) -join ', '; throw "Dataset validation failed: $sample" }
if ($RequireMedia) {
    Write-Output "Validated: $($records.Count) exercises, 1324 thumbnails, 1324 GIFs, attribution present."
} else {
    Write-Output "Validated metadata: $($records.Count) exercises, unique IDs, attribution present. Media is optional and not included in the public repository."
}
