[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# disclose-path-rules — PostToolUse / Read|Edit|Write
# 按 path 命中 .claude/rules/*.md（frontmatter globs），每会话去重后渐进式披露正文。
# 纯 stdout 进模型上下文；fail-open：任何异常 exit 0，绝不阻断工具。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json

    $fp = $d.tool_input.file_path
    $sid = $d.session_id
    if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }
    if ([string]::IsNullOrWhiteSpace($sid)) { exit 0 }

    $root = $env:CLAUDE_PROJECT_DIR
    if ([string]::IsNullOrWhiteSpace($root)) { exit 0 }
    $root = $root -replace '\\', '/'
    $root = $root.TrimEnd('/')

    # 路径归一 + 求 repo-relative
    $rel = $fp -replace '\\', '/'
    if ($rel -like "$root/*") {
        $rel = $rel.Substring($root.Length + 1)
    } elseif ($rel -eq $root) {
        exit 0
    }
    $rel = $rel.TrimStart('/')

    $rulesDir = Join-Path $root '.claude/rules'
    if (-not (Test-Path $rulesDir)) { exit 0 }
    $ruleFiles = Get-ChildItem -Path $rulesDir -Filter '*.md' -File -ErrorAction SilentlyContinue
    if (-not $ruleFiles) { exit 0 }

    $seenBase = Join-Path $env:TEMP "claude-path-rules/$sid"

    foreach ($rf in $ruleFiles) {
        $content = Get-Content -Path $rf.FullName -Raw -ErrorAction SilentlyContinue
        if ([string]::IsNullOrWhiteSpace($content)) { continue }

        # 解析 frontmatter：第一个 ---...--- 块
        $fmMatch = [regex]::Match($content, "(?s)^﻿?---\r?\n(.*?)\r?\n---\r?\n?")
        if (-not $fmMatch.Success) { continue }
        $frontmatter = $fmMatch.Groups[1].Value
        $body = $content.Substring($fmMatch.Length)

        # 取 globs: 行
        $globsMatch = [regex]::Match($frontmatter, '(?im)^\s*globs\s*:\s*(.+?)\s*$')
        if (-not $globsMatch.Success) { continue }
        $globsRaw = $globsMatch.Groups[1].Value.Trim()
        # 去掉可能的引号/方括号包裹
        $globsClean = $globsRaw.Trim('"', "'", '[', ']')
        $globs = $globsClean -split ',' | ForEach-Object { $_.Trim().Trim('"', "'") } | Where-Object { $_ -ne '' }
        if (-not $globs) { continue }

        # glob → regex 匹配 repo-relative 路径
        $matched = $false
        foreach ($g in $globs) {
            $re = [regex]::Escape($g)
            # Escape 会把 * 转为 \* ；还原为 glob 语义
            $re = $re -replace '\\\*\\\*', '.*'
            $re = $re -replace '\\\*', '[^/]*'
            if ($rel -match "^$re$") { $matched = $true; break }
        }
        if (-not $matched) { continue }

        # 去重标记
        $seenFile = Join-Path $seenBase ($rf.BaseName + '.seen')
        if (Test-Path $seenFile) { continue }
        if (-not (Test-Path $seenBase)) {
            New-Item -ItemType Directory -Path $seenBase -Force -ErrorAction SilentlyContinue | Out-Null
        }
        New-Item -ItemType File -Path $seenFile -Force -ErrorAction SilentlyContinue | Out-Null

        Write-Output "[RULE] $($rf.BaseName) — $globsRaw"
        Write-Output $body.TrimEnd()
    }
    exit 0
} catch {
    exit 0
}
