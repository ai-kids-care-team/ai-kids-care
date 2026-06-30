[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# A3 — git commit 含 secret 拦截 (BLOCK)
# PreToolUse / Bash|PowerShell。扫描 staged diff 新增行里的真实密钥值；额外拦 git add -f .env。
# 绝不回显 secret 值本身，只列 文件:行号。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json
    $cmd = $d.tool_input.command
    if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

    $cwd = $d.cwd
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

    # 额外拦截: git add -f / --force ... .env
    if ($cmd -match '(?i)\bgit\s+add\b.*(-f|--force)\b.*\.env') {
        [Console]::Error.WriteLine("[BLOCK] 检测到 git add -f .env：禁止强制添加被忽略的 .env（可能含真实凭据）。")
        [Console]::Error.WriteLine("如确需提交凭据样例，请用 .env.example 占位文件。")
        exit 2
    }

    # 仅对 git commit 扫描
    if ($cmd -notmatch '(?i)\bgit\s+commit\b') { exit 0 }

    $diff = & git -C "$cwd" diff --cached -U0 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($diff)) { exit 0 }

    $keyRe = '(?i)(RRN_HASH_PEPPER|CAMERA_STREAM_AES_KEY_V\d+|AI_SERVICE_TOKEN|POSTGRES_PASSWORD|NEO4J_PASSWORD|REDIS_PASSWORD)\s*[:=]\s*["'']?([^"''\s#]+)'
    $whitelistVal = '(?i)(test-pepper-not-secret-2026|admin123|changeme|example|placeholder|your-)'
    $whitelistPath = '(?i)(^db/initdb/|/test/|/tests/|\.example$|\.template$|\.sample$|^docs/|/docs/)'

    $curFile = ''
    $curFileSafe = $false
    $newLine = 0
    $hits = @()

    foreach ($line in ($diff -split "`n")) {
        if ($line -match '^\+\+\+\s+b/(.+)$') {
            $curFile = ($Matches[1] -replace '\\', '/').Trim()
            $curFileSafe = ($curFile -match $whitelistPath)
            continue
        }
        if ($line -match '^---') { continue }
        if ($line -match '^@@\s+-\d+(?:,\d+)?\s+\+(\d+)') {
            $newLine = [int]$Matches[1]
            continue
        }
        if ($line -match '^\+' -and $line -notmatch '^\+\+\+') {
            $content = $line.Substring(1)
            if (-not $curFileSafe -and $content -match $keyRe) {
                $val = $Matches[2]
                $isPlaceholder = ($val -match '^\$\{.*\}$') -or ($val -match $whitelistVal)
                $isSecretShape = ($val.Length -gt 8) -or ($val -match '^[A-Za-z0-9+/=_-]{12,}$')
                if (-not $isPlaceholder -and $isSecretShape) {
                    $hits += "  $curFile : 行 $newLine"
                }
            }
            $newLine++
            continue
        }
    }

    if ($hits.Count -gt 0) {
        [Console]::Error.WriteLine("[BLOCK] staged diff 中检测到疑似真实密钥（未回显具体值）：")
        foreach ($h in ($hits | Select-Object -Unique)) { [Console]::Error.WriteLine($h) }
        [Console]::Error.WriteLine("请改用 `${ENV}` 注入或占位符；secret/PII 绝不入库。确属误报请用占位字面量。")
        exit 2
    }
    exit 0
} catch {
    exit 0
}
