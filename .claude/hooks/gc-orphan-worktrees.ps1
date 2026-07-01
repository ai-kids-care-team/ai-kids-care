[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# SessionStart GC（零风险自动清扫）：只清 git 都不认的 worktree 残骸。
#   1) git worktree prune —— 删"目录已消失"的失效注册项（100% 安全）。
#   2) .claude/worktrees/ 下未注册的孤儿目录（如 Windows worktree 删除失败留下的 `;W` 空壳）：
#        - 非自身工作树的散落 cruft：仅当目录为空时静默删除；非空则保留并提示，交给 /gc。
#        - 自身是完整工作树（未注册）：仅当工作树干净时删除；脏则保留并提示。
# 绝不触碰任何已注册的 active worktree，绝不删任何分支。
#
# 注意：对没有 .git 的目录直接跑 `git status` 会向上跳到父仓库、返回父仓库状态，
#       因此必须用 `show-toplevel == 自身` 判定它是否真是独立工作树，不能拿 status 当判据。
function Resolve-Norm($p) {
    if ([string]::IsNullOrWhiteSpace($p)) { return '' }
    try { $full = [System.IO.Path]::GetFullPath($p) } catch { $full = $p }
    return ($full -replace '/', '\').TrimEnd('\')
}

try {
    $raw = [Console]::In.ReadToEnd()
    $cwd = $null
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
        try { $cwd = ($raw | ConvertFrom-Json).cwd } catch { $cwd = $null }
    }
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

    Push-Location $cwd
    try {
        $null = git rev-parse --git-dir 2>$null
        if ($LASTEXITCODE -ne 0) { return }

        # (1) 失效注册项：纯安全，静默
        git worktree prune 2>$null | Out-Null

        $wtDir = Join-Path $cwd '.claude/worktrees'
        if (-not (Test-Path $wtDir)) { return }

        # 已注册 worktree 的归一化路径集合（一律不碰）
        $registered = @{}
        foreach ($line in (git worktree list --porcelain 2>$null)) {
            if ($line -like 'worktree *') {
                $registered[(Resolve-Norm $line.Substring(9).Trim())] = $true
            }
        }

        $deleted = @()
        $kept = @()
        Get-ChildItem -LiteralPath $wtDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $dir = $_.FullName
            $self = Resolve-Norm $dir
            if ($registered.ContainsKey($self)) { return }   # 已注册 → 跳过

            # 它是否是“以自身为根”的独立工作树？
            $top = Resolve-Norm (git -C "$dir" rev-parse --show-toplevel 2>$null)
            $isOwnWorktree = ($LASTEXITCODE -eq 0 -and $top -ieq $self)

            $safeToDelete = $false
            if ($isOwnWorktree) {
                # 真·独立工作树：干净才删
                $status = git -C "$dir" status --porcelain 2>$null
                if ($LASTEXITCODE -eq 0 -and [string]::IsNullOrWhiteSpace(($status | Out-String).Trim())) {
                    $safeToDelete = $true
                }
            } else {
                # 散落 cruft（git 跳到父仓库或不认）：仅当目录为空才删
                $hasAny = Get-ChildItem -LiteralPath $dir -Force -Recurse -ErrorAction SilentlyContinue |
                          Select-Object -First 1
                if (-not $hasAny) { $safeToDelete = $true }
            }

            if ($safeToDelete) {
                try {
                    Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction Stop
                    $deleted += $_.Name
                } catch {
                    $kept += $_.Name
                }
            } else {
                $kept += $_.Name
            }
        }

        git worktree prune 2>$null | Out-Null   # 删后再 self-heal 一次

        if ($deleted.Count -gt 0) {
            Write-Output "[GC] 已清理孤儿 worktree 目录($($deleted.Count)): $($deleted -join ', ')"
        }
        if ($kept.Count -gt 0) {
            Write-Output "[GC] 保留含改动/无法删除的孤儿目录($($kept.Count)): $($kept -join ', ')；运行 /gc 处置。"
        }
    } finally {
        Pop-Location
    }
    exit 0
} catch {
    exit 0
}
