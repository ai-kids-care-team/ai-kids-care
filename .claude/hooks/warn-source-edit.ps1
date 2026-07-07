[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# C4+C5+C6 — 源码编辑软提醒 (合并)
# PostToolUse / Edit|Write。三项独立检查，各自命中才输出。exit 0。
#   C4 安全配置弱化 (限 SecurityConfig.java)
#   C5 日志 PII
#   C6 源码硬编码密钥
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

    # 仅源码扩展名
    if ($fp -notmatch '(?i)\.(java|py|ts|tsx|yml|yaml)$') { exit 0 }

    $whitelistPath = '(?i)(^db/initdb/|/db/initdb/|/test/|/tests/|\.example$|\.template$|\.sample$)'
    $isWhitelisted = ($fp -match $whitelistPath)

    $out = @()

    # C4 安全配置弱化 — 限 SecurityConfig.java
    if (-not $isWhitelisted -and $fp -match '(?i)SecurityConfig\.java$') {
        # 注：anyRequest().authenticated() 是期望的 default-deny 写法(invariant 3)，非弱化，不列为命中项
        if ($content -match '(?i)csrf\(\)?\s*[^;]*\.disable\(\)' -or
            $content -match '(?i)\bpermitAll\b' -or
            $content -match '(?i)SessionCreationPolicy\.STATELESS' -or
            $content -match '(?i)\bjwt\b|\bbearer\b') {
            $out += "[SEC-CONFIG] 疑似弱化安全防线，复核 invariant 1/2/3(default-deny/CSRF/会话式认证)"
        }
    }

    # C5 日志 PII
    if (-not $isWhitelisted) {
        if ($content -match '(?i)(log|logger|System\.out|console\.(log|error)|LOGGER)\.[a-z]+\([^)]*(rrn|getRrn|password|getPassword|token|sessionId|getSession|requestBody|payload|rawIdentifier|loginId)' -and
            $content -notmatch '(?i)(rrn_hash|rrnHash)') {
            $out += "[LOG-PII] 疑似把 PII/secret 写入日志，invariant 4/5：RRN/token/请求体绝不入日志"
        }
    }

    # C6 源码硬编码密钥
    if (-not $isWhitelisted) {
        $whitelistVal = '(?i)(test-pepper-not-secret-2026|admin123|changeme|example|placeholder|your-)'
        if ($content -match '(?i)(pepper|aesKey|secret|token|password)\s*[:=]\s*["'']([^"''$]{16,})["'']') {
            $val = $Matches[2]
            if ($val -notmatch $whitelistVal) {
                $out += "[HARDCODED-SECRET] 疑似硬编码密钥，invariant 5：密钥须 `${ENV}` 注入"
            }
        }
    }

    foreach ($l in $out) { Write-Output $l }
    exit 0
} catch {
    exit 0
}
