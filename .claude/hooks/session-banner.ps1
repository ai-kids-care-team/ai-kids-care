[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# B1 — SessionStart 横幅：活跃 OpenSpec changes（软提醒）
# 注：不再注入硬编码 interim 状态——硬编码必然过期（见 harness 治理 2026-07-07）。
# 活跃 changes 由 openspec/changes 动态枚举，永不过期。
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
    exit 0
} catch {
    exit 0
}
