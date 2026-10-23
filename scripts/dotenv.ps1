function Read-PrismDotEnv {
    param([Parameter(Mandatory)][string]$Path)
    $values = @{}
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $lineNumber++
        if ($line -match '^\s*(#.*)?$') { continue }
        if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            throw ".env 第 $lineNumber 行格式错误（内容已隐藏）"
        }
        $name = $Matches[1]
        $value = $Matches[2]
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            $quote = $value.Substring(0, 1)
            $end = $value.IndexOf($quote, 1)
            if ($end -lt 0 -or $value.Substring($end + 1) -notmatch '^\s*(#.*)?$') {
                throw ".env 第 $lineNumber 行引号格式错误（内容已隐藏）"
            }
            $value = $value.Substring(1, $end - 1)
        } else {
            $value = ($value -replace '\s+#.*$', '').TrimEnd()
        }
        $values[$name] = $value
    }
    return $values
}

function Import-PrismDotEnv {
    param([string[]]$Paths)
    $allowed = @('DEEPSEEK_API_KEY', 'DEEPSEEK_MODEL', 'DEEPSEEK_BASE_URL', 'ZHIPU_API_KEY',
        'PRISM_DB_HOST', 'PRISM_DB_PORT', 'PRISM_DB_NAME', 'PRISM_DB_USER', 'PRISM_DB_PASSWORD',
        'PRISM_AUTH_MODE', 'PRISM_MODEL_PROVIDER', 'PRISM_REVIEW_AGENT', 'PRISM_FIX_AGENT', 'PRISM_INLINE_AGENT',
        'PRISM_CHAT_AGENT', 'PRISM_TESTCASE_AGENT', 'PRISM_OLLAMA_URL', 'PRISM_EMBEDDING_MODEL',
        'PRISM_EMBEDDING_DIMENSIONS', 'PRISM_KNOWLEDGE_STORAGE', 'PRISM_BIND_ADDRESS', 'PRISM_CORS_ORIGINS')
    $merged = @{}
    foreach ($path in $Paths) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $values = Read-PrismDotEnv -Path $path
        foreach ($name in $allowed) { if ($values.ContainsKey($name)) { $merged[$name] = $values[$name] } }
    }
    foreach ($name in $merged.Keys) {
        if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $merged[$name], 'Process')
        }
    }
}
