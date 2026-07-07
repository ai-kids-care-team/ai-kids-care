[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# A1 — 已发布 Flyway migration 不可改 (BLOCK)
# PreToolUse / Edit|Write。已被 git 追踪的 db/migration/V<digits>__*.sql 视为"已发布"，拒改。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json
    $fp = $d.tool_input.file_path
    if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }

    $norm = ($fp -replace '\\', '/')
    if ($norm -notmatch '(?i)db/migration/V[0-9]+__[^/]*\.sql$') { exit 0 }

    $cwd = $d.cwd
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

    # 是否已被 git 追踪 = 已发布
    # 传归一后的 $norm（正斜杠）；Windows 下 git 对反斜杠路径 ls-files 可能误判未追踪 → 静默放行 baseline
    $null = & git -C "$cwd" ls-files --error-unmatch -- "$norm" 2>$null
    if ($LASTEXITCODE -eq 0) {
        [Console]::Error.WriteLine("[BLOCK] 已发布的 Flyway migration 不可修改: $norm")
        [Console]::Error.WriteLine("请新增更高版本号的 V*.sql 迁移，并经维护者批准；不要编辑已追踪的 baseline。")
        exit 2
    }
    # 未追踪 = 新建迁移，放行
    exit 0
} catch {
    exit 0
}
