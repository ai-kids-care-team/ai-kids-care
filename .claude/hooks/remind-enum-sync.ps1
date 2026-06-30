[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# C2 — enum 三处同步提醒 (软提醒)
# PostToolUse / Edit|Write。命中 backend type/ enum 即提醒。exit 0。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json

    $fp = $d.tool_input.file_path
    if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }
    $fp = $fp -replace '\\', '/'

    if ($fp -match '(?i)backend/src/main/java/.*/v1/type/.*\.java$') {
        Write-Output "[ENUM] 改了 backend type/ enum，须同步：DB seed(db/initdb/) | 前端 i18n/enums | GET /api/v1/enums/{name} 契约"
    }
    exit 0
} catch {
    exit 0
}
