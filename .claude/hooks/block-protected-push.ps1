[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# A4 — force-push / 删远程分支到受保护线 (BLOCK)
# PreToolUse / Bash|PowerShell。只拦改写共享远程历史(main/develop)的操作；本地销毁操作放行。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json
    $cmd = $d.tool_input.command
    if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

    # 非 git push 一律放行（本地 down -v / rm -rf / reset --hard 等不在此列）
    if ($cmd -notmatch '(?i)\bgit\s+push\b') { exit 0 }

    $reason = $null

    # 删远程分支到受保护线: --delete main|develop 或 :main|develop
    if ($cmd -match '(?i)(--delete\s+(refs/heads/)?(main|develop)\b)' -or
        $cmd -match '(?i)(:\s*(refs/heads/)?(main|develop)\b)') {
        $reason = "删除受保护远程分支(main/develop)"
    }

    # force-push 判定
    $hasForce = ($cmd -match '(?i)(--force\b|--force-with-lease\b|(^|\s)-f(\s|$))')
    if (-not $reason -and $hasForce) {
        if ($cmd -match '(?i)\b(main|develop)\b') {
            $reason = "force-push 到 main/develop"
        } else {
            # 裸 push 检测：push 后除 flag/remote 外无显式分支 refspec → 可能推当前分支，保守拦截
            $tail = $cmd -replace '(?is)^.*?\bgit\s+push\b', ''
            $toks = ($tail -split '\s+') | Where-Object { $_ -ne '' -and $_ -notmatch '^-' }
            if ($toks.Count -le 1) {
                $reason = "force-push 裸 push（未指定分支，可能改写当前/受保护分支历史）"
            }
        }
    }

    if ($reason) {
        [Console]::Error.WriteLine("[BLOCK] $reason：改写受保护发布线(main/develop)的共享历史已拦截，须维护者显式批准。")
        [Console]::Error.WriteLine("本地销毁操作(docker compose down -v / rm -rf / git reset --hard)不在此列，可正常执行。")
        exit 2
    }
    exit 0
} catch {
    exit 0
}
