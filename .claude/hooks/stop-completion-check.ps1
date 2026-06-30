[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# C1 — Stop 收尾检查 (软提醒)
# Stop 事件。stop_hook_active 防重入。绝不 exit 2。
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $d = $raw | ConvertFrom-Json

    if ($d.stop_hook_active -eq $true) { exit 0 }

    $cwd = $d.cwd
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

    $status = & git -C "$cwd" status --porcelain 2>$null
    if ($LASTEXITCODE -ne 0) { exit 0 }
    if ([string]::IsNullOrWhiteSpace($status)) { exit 0 }

    $n = (($status -split "`n") | Where-Object { $_.Trim() -ne '' }).Count
    Write-Output "[STOP] 工作树有 $n 处未提交改动，收尾前核查：1) 改过 db/initdb/ 须 ./gradlew cleanTest test；2) 验证/测试是否已跑(verification-before-completion)；3) commit message 末尾含 Co-Authored-By；4) PR body 含 Generated with Claude Code。"
    exit 0
} catch {
    exit 0
}
