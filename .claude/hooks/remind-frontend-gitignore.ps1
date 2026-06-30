[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# C3 — 前端 gitignore 搜索盲区 (软提醒)
# UserPromptSubmit。prompt 命中前端关键词即提醒。exit 0。
# 显式以 UTF-8 解码 stdin：PS 5.1 的 [Console]::In 按系统码页解码，会把 harness
# 投喂的 UTF-8 中文字节 mangle，导致"前端"漏匹配；用 StreamReader 强制 UTF-8 解码。
try {
    $reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
    $raw = $reader.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json

    $prompt = $d.prompt
    if ([string]::IsNullOrWhiteSpace($prompt)) { exit 0 }

    if ($prompt -match '(?i)(前端|frontend|Front[- ]?End|src/app|components|RTK|redux|Next\.js|next\.js)') {
        Write-Output "[FRONTEND] frontend/ 在 .gitignore，Grep/Glob 静默跳过；搜前端代码用 Bash 'rg --no-ignore -n <pat> frontend/'；next-env.d.ts 构建后须还原"
    }
    exit 0
} catch {
    exit 0
}
