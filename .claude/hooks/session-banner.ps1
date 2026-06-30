[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# B1 — SessionStart 横幅：活跃 OpenSpec changes + interim 状态（软提醒）
try {
    $raw = [Console]::In.ReadToEnd()
    $cwd = $null
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
        try { $cwd = ($raw | ConvertFrom-Json).cwd } catch { $cwd = $null }
    }
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

    $changesDir = Join-Path $cwd 'openspec/changes'
    if (Test-Path $changesDir) {
        $active = Get-ChildItem -Path $changesDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne 'archive' } |
            Select-Object -ExpandProperty Name
        if ($active -and $active.Count -gt 0) {
            Write-Output "[OpenSpec] 活跃 changes: $($active -join ', ')"
        } else {
            Write-Output "[OpenSpec] 无活跃 change。"
        }
    }
    Write-Output "[INTERIM] AI 层仍直发 Pushover/SMS demo；detection_events 全为 seed，非实时产出；ADR-0015(V1) 待实现。"
    exit 0
} catch {
    exit 0
}
