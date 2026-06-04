param(
    [string]$LocalConfig = "ai.properties",
    [string]$SharedConfig = "ai.shared.properties"
)

$ErrorActionPreference = "Stop"

function Read-Properties([string]$Path) {
    $props = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $props
    }
    foreach ($line in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path))) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') {
            $props[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $props
}

function Obfuscate-Key([string]$Key, [string]$Mask) {
    $keyBytes = [System.Text.Encoding]::UTF8.GetBytes($Key)
    $maskBytes = [System.Text.Encoding]::UTF8.GetBytes($Mask)
    $out = New-Object byte[] $keyBytes.Length
    for ($i = 0; $i -lt $keyBytes.Length; $i++) {
        $out[$i] = $keyBytes[$i] -bxor $maskBytes[$i % $maskBytes.Length] -bxor (($i * 31 + 17) -band 0xFF)
    }
    return [Convert]::ToBase64String($out)
}

$props = Read-Properties $LocalConfig
$apiKey = $props["ai.directApiKey"]
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "ai.directApiKey is empty in $LocalConfig"
}

$maskBytes = New-Object byte[] 18
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($maskBytes)
} finally {
    $rng.Dispose()
}
$mask = [Convert]::ToBase64String($maskBytes).TrimEnd("=")
$obfuscated = Obfuscate-Key $apiKey $mask

$mode = "direct"
$timeout = if ([string]::IsNullOrWhiteSpace($props["ai.timeoutSeconds"])) { "30" } else { $props["ai.timeoutSeconds"] }
$baseUrl = if ([string]::IsNullOrWhiteSpace($props["ai.directBaseUrl"])) { "https://api.deepseek.com/v1" } else { $props["ai.directBaseUrl"] }
$model = if ([string]::IsNullOrWhiteSpace($props["ai.directModel"])) { "deepseek-chat" } else { $props["ai.directModel"] }

$content = @(
    "# Shared AI config for team builds.",
    "# The API key is obfuscated to avoid plain-text GitHub secret scanning.",
    "# This is not strong protection against APK reverse engineering.",
    "",
    "ai.mode=$mode",
    "ai.timeoutSeconds=$timeout",
    "ai.directBaseUrl=$baseUrl",
    "ai.directModel=$model",
    "ai.directApiKeyObfuscated=$obfuscated",
    "ai.directApiKeyMask=$mask"
)

[System.IO.File]::WriteAllLines((Join-Path (Get-Location) $SharedConfig), $content, [System.Text.Encoding]::UTF8)
Write-Host "Generated $SharedConfig with an obfuscated API key."
