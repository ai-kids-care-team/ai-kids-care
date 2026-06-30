[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# A2 — 真值 secret 写入 .env/源码拦截 (BLOCK)
# PreToolUse / Edit|Write。仅对已知密钥键名右值做真值检测；占位/白名单/白名单路径放行。
# 不回显具体值。解析失败一律 exit 0。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json

    $tool = $d.tool_name
    $fp = $d.tool_input.file_path
    if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }
    $fp = $fp -replace '\\', '/'

    if ($tool -eq 'Write') {
        $content = $d.tool_input.content
    } else {
        $content = $d.tool_input.new_string
    }
    if ([string]::IsNullOrWhiteSpace($content)) { exit 0 }

    $whitelistPath = '(?i)(^db/initdb/|/db/initdb/|/test/|/tests/|\.example$|\.template$|\.sample$|^docs/|/docs/)'
    if ($fp -match $whitelistPath) { exit 0 }

    $keyRe = '(?i)(RRN_HASH_PEPPER|CAMERA_STREAM_AES_KEY_V\d+|AI_SERVICE_TOKEN|POSTGRES_PASSWORD|NEO4J_PASSWORD|REDIS_PASSWORD)\s*[:=]\s*["'']?([^"''\s#]+)'
    $whitelistVal = '(?i)(test-pepper-not-secret-2026|admin123|changeme|example|placeholder|your-)'

    $hits = @()
    foreach ($line in ($content -split "`n")) {
        if ($line -match $keyRe) {
            $key = $Matches[1]
            $val = $Matches[2]
            $isPlaceholder = ($val -match '^\$\{.*\}$') -or ($val -match $whitelistVal)
            $isSecretShape = ($val.Length -gt 8) -or ($val -match '^[A-Za-z0-9+/=_-]{12,}$')
            if (-not $isPlaceholder -and $isSecretShape) {
                $hits += "  $key"
            }
        }
    }

    if ($hits.Count -gt 0) {
        [Console]::Error.WriteLine("[BLOCK] 检测到向 $fp 写入疑似真实密钥（未回显具体值）：")
        foreach ($h in ($hits | Select-Object -Unique)) { [Console]::Error.WriteLine($h) }
        [Console]::Error.WriteLine("禁止写入真实密钥，请用 `${ENV}` 注入或占位符。")
        exit 2
    }
    exit 0
} catch {
    exit 0
}
