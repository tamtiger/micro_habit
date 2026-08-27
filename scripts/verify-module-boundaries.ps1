$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Read-RepoFile([string]$relativePath) {
    return Get-Content -LiteralPath (Join-Path $repoRoot $relativePath) -Raw
}

function Assert-Matches([string]$text, [string]$pattern, [string]$message) {
    if ($text -notmatch $pattern) { throw $message }
}

function Assert-NotMatches([string]$text, [string]$pattern, [string]$message) {
    if ($text -match $pattern) { throw $message }
}

function Get-ProjectDependencyPattern([string]$modulePattern) {
    return 'project\s*\(\s*(?:path\s*=\s*)?":(?:{0})"(?:\s*,[^)]*)?\s*\)' -f $modulePattern
}

$settings = Read-RepoFile "settings.gradle.kts"
foreach ($module in @("app", "ui", "domain", "data", "platform")) {
    $includePattern = 'include\(":{0}"\)' -f $module
    Assert-Matches $settings $includePattern "settings.gradle.kts must include :$module."
}

$appBuild = Read-RepoFile "app/build.gradle.kts"
foreach ($module in @("ui", "domain", "data", "platform")) {
    $dependencyPattern = Get-ProjectDependencyPattern ([regex]::Escape($module))
    Assert-Matches $appBuild $dependencyPattern ":app must depend on :$module."
}

$domainBuild = Read-RepoFile "domain/build.gradle.kts"
Assert-Matches $domainBuild 'libs\.plugins\.kotlin\.jvm' ":domain must use the Kotlin JVM plugin."
$anyProjectDependency = Get-ProjectDependencyPattern '[^"]+'
Assert-NotMatches $domainBuild "libs\.plugins\.android|$anyProjectDependency" ":domain must not depend on Android or another project module."

$uiBuild = Read-RepoFile "ui/build.gradle.kts"
Assert-Matches $uiBuild (Get-ProjectDependencyPattern 'domain') ":ui must depend only on the domain project module."
$uiForbiddenProjects = Get-ProjectDependencyPattern 'data|platform|app'
Assert-NotMatches $uiBuild "$uiForbiddenProjects|androidx-navigation-compose|androidx\.navigation\.compose" ":ui must not own navigation or access data/platform/app modules."

$dataBuild = Read-RepoFile "data/build.gradle.kts"
Assert-Matches $dataBuild (Get-ProjectDependencyPattern 'domain') ":data must depend on :domain."
Assert-NotMatches $dataBuild (Get-ProjectDependencyPattern 'ui|platform|app') ":data must not depend on UI, platform, or app."

$platformBuild = Read-RepoFile "platform/build.gradle.kts"
Assert-Matches $platformBuild (Get-ProjectDependencyPattern 'domain') ":platform must depend on :domain."
Assert-NotMatches $platformBuild (Get-ProjectDependencyPattern 'ui|data|app') ":platform must not depend on UI, data, or app."

$sourceRules = @(
    @{ Path = "domain\src"; Pattern = '(?m)^import\s+(android\.|androidx\.|vn\.nhip2phut\.(app|ui|data|platform))'; Message = ":domain contains a forbidden Android/module import." },
    @{ Path = "ui\src"; Pattern = '(?m)^import\s+(androidx\.navigation\.|androidx\.room\.|android\.database\.|android\.security\.|java\.security\.KeyStore|vn\.nhip2phut\.(app|data|platform))'; Message = ":ui contains a forbidden navigation/storage/platform import." },
    @{ Path = "ui\src"; Pattern = '\bandroid\.app\.(?:AlarmManager|PendingIntent)\b'; Message = ":ui contains forbidden direct Android scheduling access." },
    @{ Path = "data\src"; Pattern = '(?m)^import\s+vn\.nhip2phut\.(app|ui|platform)'; Message = ":data contains a forbidden lateral module import." },
    @{ Path = "platform\src"; Pattern = '(?m)^import\s+vn\.nhip2phut\.(app|ui|data)'; Message = ":platform contains a forbidden lateral module import." }
)

foreach ($rule in $sourceRules) {
    $root = Join-Path $repoRoot $rule.Path
    if (-not (Test-Path -LiteralPath $root)) { continue }
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Include *.kt
    foreach ($file in $files) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ($content -match $rule.Pattern) {
            $relative = $file.FullName.Substring($repoRoot.Length).TrimStart(
                [IO.Path]::DirectorySeparatorChar,
                [IO.Path]::AltDirectorySeparatorChar
            )
            throw "$($rule.Message) File: $relative"
        }
    }
}

Write-Host "ARC-101 module dependencies and source imports verified."
