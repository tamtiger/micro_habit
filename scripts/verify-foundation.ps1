$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Read-Text {
    param([string]$Path)
    Assert-True (Test-Path -LiteralPath $Path) "Missing required file: $Path"
    return Get-Content -Raw -LiteralPath $Path
}

$settings = Read-Text "settings.gradle.kts"
foreach ($module in @(":app", ":ui", ":domain", ":data", ":platform")) {
    Assert-True ($settings.Contains("include(`"$module`")")) "settings.gradle.kts missing module $module"
}

$versions = Read-Text "gradle/libs.versions.toml"
Assert-True ($versions.Contains('minSdk = "26"')) "minSdk baseline must stay 26"
Assert-True ($versions.Contains('targetSdk = "36"')) "targetSdk baseline must stay 36"
Assert-True ($versions.Contains('compileSdk = "36"')) "compileSdk baseline must stay 36"

$appBuild = Read-Text "app/build.gradle.kts"
foreach ($dependency in @('project(":ui")', 'project(":domain")', 'project(":data")', 'project(":platform")')) {
    Assert-True ($appBuild.Contains($dependency)) "app module missing dependency $dependency"
}

$domainBuild = Read-Text "domain/build.gradle.kts"
Assert-True ($domainBuild.Contains('alias(libs.plugins.kotlin.jvm)')) "domain must remain Kotlin/JVM-only"
Assert-True (-not $domainBuild.Contains('android.library')) "domain must not apply Android plugin"

foreach ($pair in @(
    @("ui/build.gradle.kts", 'project(":domain")'),
    @("data/build.gradle.kts", 'project(":domain")'),
    @("platform/build.gradle.kts", 'project(":domain")')
)) {
    $text = Read-Text $pair[0]
    Assert-True ($text.Contains($pair[1])) "$($pair[0]) must depend on :domain"
}

$domainImports = ""
if (Test-Path "domain/src/main/kotlin") {
    $domainImports = (Get-ChildItem -Recurse -File "domain/src/main/kotlin" | ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
}
Assert-True (-not ($domainImports -match 'import\s+android\.')) "domain source must not import android.*"

$manifest = Read-Text "app/src/main/AndroidManifest.xml"
$declaredPermissions = [regex]::Matches($manifest, '<uses-permission\s+android:name="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
$allowedPermissions = @("android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED")
foreach ($permission in $declaredPermissions) {
    Assert-True ($allowedPermissions -contains $permission) "Manifest declares forbidden permission: $permission"
}
foreach ($forbidden in @("android.permission.INTERNET", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACTIVITY_RECOGNITION", "android.permission.CALL_PHONE", "com.android.vending.BILLING")) {
    Assert-True (-not $manifest.Contains($forbidden)) "Manifest contains forbidden permission or service: $forbidden"
}

$mainActivity = Read-Text "app/src/main/kotlin/vn/nhip2phut/app/MainActivity.kt"
$flagIndex = $mainActivity.IndexOf("FLAG_SECURE")
$setContentIndex = $mainActivity.IndexOf("setContent {")
Assert-True ($flagIndex -ge 0) "MainActivity must set FLAG_SECURE"
Assert-True ($setContentIndex -ge 0) "MainActivity must call setContent"
Assert-True ($flagIndex -lt $setContentIndex) "FLAG_SECURE must be set before setContent"

$events = Read-Text "domain/src/main/kotlin/vn/nhip2phut/domain/events/EventContracts.kt"
$eventTokenCount = ([regex]::Matches($events, '\("[a-z_]+\"\)')).Count
Assert-True ($eventTokenCount -eq 48) "EventNameV1 must define exactly 48 wire tokens; found $eventTokenCount"
Assert-True ($events.Contains("EventEnvelopeMaskV1")) "Event envelope mask contract is missing"

$schedule = Read-Text "domain/src/main/kotlin/vn/nhip2phut/domain/schedule/ScheduleTime.kt"
Assert-True ($schedule.Contains('^(?:[01][0-9]|2[0-3]):[0-5][0-9]$')) "Schedule time must use canonical HH:mm regex"

$duplicateGuard = Read-Text "data/src/main/kotlin/vn/nhip2phut/data/codec/DuplicateKeyJsonGuard.kt"
Assert-True ($duplicateGuard.Contains("DuplicateJsonKeyException")) "Duplicate JSON key guard is missing"
Assert-True (Test-Path "data/src/test/kotlin/vn/nhip2phut/data/codec/DuplicateKeyJsonGuardTest.kt") "Duplicate JSON key guard test is missing"
Assert-True (Test-Path "domain/src/test/kotlin/vn/nhip2phut/domain/schedule/ScheduleTimeTest.kt") "Schedule time test is missing"
Assert-True (Test-Path "domain/src/test/kotlin/vn/nhip2phut/domain/events/EventContractRegistryV1Test.kt") "Event registry test is missing"

$changelog = Read-Text "CHANGELOG.md"
$firstHeading = [regex]::Match($changelog, '(?m)^##\s+(.+)$')
Assert-True ($firstHeading.Success) "CHANGELOG.md must contain a level-2 entry"
Assert-True ($firstHeading.Groups[1].Value.Contains("DEL-01")) "CHANGELOG top entry must document DEL-01"

Write-Output "DEL-01 foundation verification passed."
