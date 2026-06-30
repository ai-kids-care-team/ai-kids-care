[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# B2 — db/initdb seed 改动提醒 cleanTest（软提醒）
# PostToolUse / Edit|Write
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json
    $fp = $d.tool_input.file_path
    if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }

    $norm = ($fp -replace '\\', '/')
    if ($norm -match '(?i)db/initdb/') {
        Write-Output "[SEED] db/initdb/ 已改动 → 须 ./gradlew cleanTest test（缺 cleanTest 会被判 UP-TO-DATE 跳过）；本机无 Java 用 DooD（见 memory/backend-test-dood-invocation.md）。"
    }
    exit 0
} catch {
    exit 0
}
