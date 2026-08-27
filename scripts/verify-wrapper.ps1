$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wrapperDirectory = Join-Path $repoRoot "gradle\wrapper"
$propertiesPath = Join-Path $wrapperDirectory "gradle-wrapper.properties"
$jarPath = Join-Path $wrapperDirectory "gradle-wrapper.jar"
$requiredFiles = @(
    (Join-Path $repoRoot "gradlew"),
    (Join-Path $repoRoot "gradlew.bat"),
    $propertiesPath,
    $jarPath
)

foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Gradle Wrapper artifact is missing: $([IO.Path]::GetFileName($requiredFile))"
    }
}

$properties = Get-Content -LiteralPath $propertiesPath -Raw
$expectedDistribution = "https\://services.gradle.org/distributions/gradle-8.13-bin.zip"
$expectedDistributionSha256 = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
$expectedJarSha256 = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
$expectedUnixLauncherSha256 = "734b3879d3501dce471cf0522d3bcbafe76873d9fc5129345b67fb43bd15e933"
$expectedWindowsLauncherSha256 = "2209f919a22528af59a2af2ad97e8d056cca18e39f7d87aa3fd549a73b180150"

function Get-NormalizedLauncherSha256([string]$path) {
    $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
    try {
        $launcherText = $strictUtf8.GetString([IO.File]::ReadAllBytes($path))
    } catch {
        throw "$([IO.Path]::GetFileName($path)) must be valid UTF-8."
    }

    # Gradle emits gradlew with LF and gradlew.bat with CRLF. Treat a checkout's
    # line-ending conversion as equivalent while pinning every other byte.
    $normalizedText = $launcherText.Replace("`r`n", "`n")
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($strictUtf8.GetBytes($normalizedText))
    } finally {
        $sha256.Dispose()
    }

    return ([BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
}

if ($properties -notmatch "(?m)^distributionUrl=$([regex]::Escape($expectedDistribution))$") {
    throw "Gradle Wrapper distributionUrl must target Gradle 8.13 bin."
}

if ($properties -notmatch "(?m)^distributionSha256Sum=$expectedDistributionSha256$") {
    throw "Gradle Wrapper distributionSha256Sum is missing or incorrect."
}

$actualJarSha256 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualJarSha256 -ne $expectedJarSha256) {
    throw "Gradle Wrapper JAR SHA-256 mismatch. Expected $expectedJarSha256, got $actualJarSha256."
}

$unixLauncherPath = Join-Path $repoRoot "gradlew"
$windowsLauncherPath = Join-Path $repoRoot "gradlew.bat"
$actualUnixLauncherSha256 = Get-NormalizedLauncherSha256 $unixLauncherPath
$actualWindowsLauncherSha256 = Get-NormalizedLauncherSha256 $windowsLauncherPath
if ($actualUnixLauncherSha256 -ne $expectedUnixLauncherSha256) {
    throw "gradlew normalized SHA-256 mismatch. Expected the official Gradle 8.13 launcher $expectedUnixLauncherSha256, got $actualUnixLauncherSha256."
}
if ($actualWindowsLauncherSha256 -ne $expectedWindowsLauncherSha256) {
    throw "gradlew.bat normalized SHA-256 mismatch. Expected the official Gradle 8.13 launcher $expectedWindowsLauncherSha256, got $actualWindowsLauncherSha256."
}

$unixLauncher = Get-Content -LiteralPath $unixLauncherPath -Raw
$windowsLauncher = Get-Content -LiteralPath $windowsLauncherPath -Raw
if ($unixLauncher -notmatch "org\.gradle\.wrapper\.GradleWrapperMain") {
    throw "gradlew is not an official generated Gradle wrapper launcher."
}
if ($windowsLauncher -notmatch "org\.gradle\.wrapper\.GradleWrapperMain") {
    throw "gradlew.bat is not an official generated Gradle wrapper launcher."
}

Write-Host "Gradle Wrapper 8.13 distribution, JAR, and normalized official launcher checksums verified."
