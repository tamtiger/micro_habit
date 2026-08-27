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

function Read-TreeText {
    param([string]$Path)
    Assert-True (Test-Path -LiteralPath $Path) "Missing required tree: $Path"
    return (Get-ChildItem -LiteralPath $Path -Recurse -File -Include *.kt,*.xml,*.kts |
        ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
}

function Assert-ExactGradleAssignment {
    param(
        [string]$Text,
        [string]$Name,
        [string]$ExpectedExpression,
        [string]$Path
    )
    $pattern = '(?m)^\s*{0}\s*=\s*(?<expression>[^\r\n]+?)\s*$' -f [regex]::Escape($Name)
    $assignmentMatches = [regex]::Matches($Text, $pattern)
    Assert-True ($assignmentMatches.Count -eq 1) "$Path must declare exactly one $Name assignment"
    $actualExpression = $assignmentMatches[0].Groups['expression'].Value.Trim()
    Assert-True ($actualExpression -eq $ExpectedExpression) "$Path must source $Name from $ExpectedExpression"
}

function Get-StringResourceNames {
    param([string]$Directory)
    Assert-True (Test-Path -LiteralPath $Directory -PathType Container) "Missing exact locale directory: $Directory"
    $names = @()
    foreach ($file in Get-ChildItem -LiteralPath $Directory -File -Filter *.xml) {
        [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
        $names += @($document.resources.string | ForEach-Object { $_.name })
    }
    return @($names | Sort-Object -Unique)
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
$androidModuleBuilds = [ordered]@{
    "app/build.gradle.kts" = $appBuild
    "ui/build.gradle.kts" = Read-Text "ui/build.gradle.kts"
    "data/build.gradle.kts" = Read-Text "data/build.gradle.kts"
    "platform/build.gradle.kts" = Read-Text "platform/build.gradle.kts"
}
foreach ($entry in $androidModuleBuilds.GetEnumerator()) {
    Assert-ExactGradleAssignment $entry.Value "compileSdk" "libs.versions.compileSdk.get().toInt()" $entry.Key
    Assert-ExactGradleAssignment $entry.Value "minSdk" "libs.versions.minSdk.get().toInt()" $entry.Key
}
Assert-ExactGradleAssignment $appBuild "targetSdk" "libs.versions.targetSdk.get().toInt()" "app/build.gradle.kts"

foreach ($dependency in @('project(":ui")', 'project(":domain")', 'project(":data")', 'project(":platform")')) {
    Assert-True ($appBuild.Contains($dependency)) "app module missing dependency $dependency"
}

$rootBuild = Read-Text "build.gradle.kts"
Assert-True ($rootBuild.Contains('":app:testDebugUnitTest"')) "verifyFoundation must run app unit tests"
Assert-True ($rootBuild.Contains('":app:connectedDebugAndroidTest"')) "verifyFoundationDevice must run app device tests"
Assert-True ($rootBuild.Contains('":data:connectedDebugAndroidTest"')) "verifyFoundationDevice must run data device tests"
Assert-True (Test-Path "app/src/test/kotlin/vn/nhip2phut/app/time/ClockIntegrityRuntimeTest.kt") "Clock integrity runtime tests are missing"
foreach ($apiContractPath in @(
    "app/src/androidTest/kotlin/vn/nhip2phut/app/Api36ContractTest.kt",
    "data/src/androidTest/kotlin/vn/nhip2phut/data/storage/Api36ContractTest.kt"
)) {
    $apiContract = Read-Text $apiContractPath
    Assert-True ($apiContract -match 'assertEquals\(\s*36\s*,\s*Build\.VERSION\.SDK_INT\s*\)') "$apiContractPath must fail closed outside API 36"
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
[xml]$manifestDocument = $manifest
$androidNamespace = "http://schemas.android.com/apk/res/android"
$toolsNamespace = "http://schemas.android.com/tools"
$activePermissionNodes = @(
    $manifestDocument.SelectNodes("/manifest/uses-permission") |
        Where-Object { $_.GetAttribute("node", $toolsNamespace) -ne "remove" }
)
$declaredPermissions = @($activePermissionNodes | ForEach-Object { $_.GetAttribute("name", $androidNamespace) })
$sdk23PermissionNodes = @($manifestDocument.SelectNodes("/manifest/uses-permission-sdk-23"))
Assert-True ($sdk23PermissionNodes.Count -eq 0) "Source manifest must not use uses-permission-sdk-23"
$allowedPermissions = @("android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED")
Assert-True ($activePermissionNodes.Count -eq $allowedPermissions.Count) "Source manifest must declare each allowlisted permission exactly once"
$activePermissionNodes | ForEach-Object {
    Assert-True ($_.GetAttribute("maxSdkVersion", $androidNamespace) -eq "") "Source permission declarations must not use android:maxSdkVersion"
}
$permissionDiff = Compare-Object -ReferenceObject ($allowedPermissions | Sort-Object) -DifferenceObject ($declaredPermissions | Sort-Object -Unique)
Assert-True (-not $permissionDiff) "Manifest permission set must equal the DEL-01 allowlist"
foreach ($forbidden in @("android.permission.INTERNET", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACTIVITY_RECOGNITION", "android.permission.CALL_PHONE", "com.android.vending.BILLING")) {
    Assert-True (-not $manifest.Contains($forbidden)) "Manifest contains forbidden permission or service: $forbidden"
}
Assert-True ($manifest.Contains('android:allowBackup="false"')) "Manifest must disable backup"
Assert-True ($manifest.Contains('android:usesCleartextTraffic="false"')) "Manifest must disable cleartext traffic"

[xml]$dataExtractionRules = Read-Text "app/src/main/res/xml/data_extraction_rules.xml"
foreach ($sectionName in @("cloud-backup", "device-transfer")) {
    $section = $dataExtractionRules.'data-extraction-rules'.$sectionName
    Assert-True ($null -ne $section) "data_extraction_rules.xml is missing $sectionName"
    $rootExclusion = @($section.exclude | Where-Object { $_.domain -eq "root" -and $_.path -eq "." })
    Assert-True ($rootExclusion.Count -eq 1) "$sectionName must exclude root path . exactly once"
}

$mainActivity = Read-Text "app/src/main/kotlin/vn/nhip2phut/app/MainActivity.kt"
$flagIndex = $mainActivity.IndexOf("FLAG_SECURE")
$setContentIndex = $mainActivity.IndexOf("setContent {")
Assert-True ($flagIndex -ge 0) "MainActivity must set FLAG_SECURE"
Assert-True ($setContentIndex -ge 0) "MainActivity must call setContent"
Assert-True ($flagIndex -lt $setContentIndex) "FLAG_SECURE must be set before setContent"
Assert-True ($mainActivity.Contains("Nhip2PhutNavHost")) "MainActivity must call the app-owned navigation host"
Assert-True ($mainActivity.Contains("container")) "MainActivity must pass the application-scoped AppContainer"

$appSource = Read-TreeText "app/src/main"
$uiSource = Read-TreeText "ui/src/main"
Assert-True ($appSource.Contains("sealed interface AppDestination") -or $appSource.Contains("sealed class AppDestination")) "AppDestination closed route contract is missing"
Assert-True ($appSource.Contains("NavHost(")) "App-owned NavHost is missing"
Assert-True (-not ($uiSource -match 'androidx\.navigation\.')) "UI source must not import navigation runtime"

foreach ($module in @("app", "ui")) {
    $defaultNames = Get-StringResourceNames "$module/src/main/res/values"
    $viVnNames = Get-StringResourceNames "$module/src/main/res/values-b+vi+VN"
    $resourceDiff = Compare-Object -ReferenceObject $defaultNames -DifferenceObject $viVnNames
    Assert-True (-not $resourceDiff) "$module default and exact vi-VN string keys must have parity"
}

$domainSource = Read-TreeText "domain/src/main"
$events = Read-Text "domain/src/main/kotlin/vn/nhip2phut/domain/events/EventContracts.kt"
$eventTokenCount = ([regex]::Matches($events, '\("[a-z_]+\"\)')).Count
Assert-True ($eventTokenCount -eq 48) "EventNameV1 must define exactly 48 wire tokens; found $eventTokenCount"
Assert-True ($events.Contains("EventEnvelopeMaskV1")) "Event envelope mask contract is missing"
foreach ($wireType in @("ProfileWireV1", "WorkScheduleWireV1", "CheckInWireV1", "DecisionWireV1", "SessionWireV1", "FeedbackWireV1", "ReminderWireV1", "WeeklySummaryWireV1")) {
    Assert-True ($domainSource.Contains($wireType)) "Closed codec is missing $wireType"
}
foreach ($rootArray in @("profile", "work_schedule", "check_ins", "decisions", "sessions", "feedback", "reminders", "events", "weekly_summaries")) {
    Assert-True ($domainSource.Contains("`"$rootArray`"")) "Dataset root is missing canonical array $rootArray"
}
Assert-True ($domainSource.Contains("ClosedCodecV1")) "Shared ClosedCodecV1 entry point is missing"
Assert-True ($domainSource.Contains("EventSpecV1")) "Typed EventSpecV1 registry is missing"
Assert-True (-not ($domainSource -match 'Map<String,\s*Any\?>')) "Domain production codec must not accept loose property maps"

$schedule = Read-Text "domain/src/main/kotlin/vn/nhip2phut/domain/schedule/ScheduleTime.kt"
Assert-True ($schedule.Contains('^(?:[01][0-9]|2[0-3]):[0-5][0-9]$')) "Schedule time must use canonical HH:mm regex"

$dataSource = Read-TreeText "data/src/main"
foreach ($storageToken in @("@Database", "clock_state", "AES/GCM/NoPadding", "N2PENC01", "nhip2phut_data_v")) {
    Assert-True ($dataSource.Contains($storageToken)) "Encrypted Room foundation is missing token $storageToken"
}
Assert-True (-not $dataSource.Contains("fallbackToDestructiveMigration")) "Destructive Room migration is forbidden"
Assert-True (Test-Path "data/schemas" -PathType Container) "Room schema export directory is missing"
Assert-True ((Get-ChildItem -LiteralPath "data/src/androidTest" -Recurse -File -Filter *.kt).Count -gt 0) "Data instrumented Room/Keystore tests are missing"

$appContainer = Read-Text "app/src/main/kotlin/vn/nhip2phut/app/AppContainer.kt"
foreach ($clockWiringToken in @(
    "Nhip2PhutDatabase.open",
    "EncryptedClockStateRepository",
    "DurableClockStateCoordinator",
    "LoadedClockGenerationSource",
    "AndroidClock(rawClockSource, generationSource)"
)) {
    Assert-True ($appContainer.Contains($clockWiringToken)) "AppContainer is missing durable clock wiring token $clockWiringToken"
}

$androidClock = Read-Text "platform/src/main/kotlin/vn/nhip2phut/platform/time/AndroidClock.kt"
Assert-True ($androidClock.Contains("generationBefore") -and $androidClock.Contains("generationAfter")) "AndroidClock must guard a raw sample with a stable durable generation"
Assert-True (-not ($androidClock -match 'generationSource\s*:\s*ClockGenerationSource\s*=|ClockGenerationSource\s*\{\s*0L\s*\}')) "AndroidClock must not default durable generation to zero"

$clockReceiver = Read-Text "platform/src/main/kotlin/vn/nhip2phut/platform/notification/BootReconcileReceiver.kt"
foreach ($receiverToken in @("Intent.ACTION_TIME_CHANGED", "Intent.ACTION_TIMEZONE_CHANGED", "goAsync()", "pendingResult::finish")) {
    Assert-True ($clockReceiver.Contains($receiverToken)) "Clock receiver is missing $receiverToken"
}

$clockRepository = Read-Text "data/src/main/kotlin/vn/nhip2phut/data/storage/EncryptedClockStateRepository.kt"
Assert-True ($clockRepository.Contains("withTransaction")) "Clock state updates must use a Room transaction"
Assert-True ($clockRepository.Contains("validateMonotonicTransition")) "Clock state repository must reject monotonic regressions"

Assert-True ($appBuild.Contains("libs.androidx.lifecycle.runtime.ktx")) "App must declare its lifecycle/coroutine runtime dependency"
$dataBuild = Read-Text "data/build.gradle.kts"
Assert-True ($dataBuild.Contains('assets.srcDir("$projectDir/schemas")')) "Room schemas must be exposed to MigrationTestHelper as androidTest assets"
$migrationTest = Read-Text "data/src/androidTest/kotlin/vn/nhip2phut/data/storage/Nhip2PhutDatabaseMigrationTest.kt"
Assert-True ($migrationTest.Contains("MigrationTestHelper") -and $migrationTest.Contains("createDatabase")) "Room schema v1 must be exercised by MigrationTestHelper"

Assert-True (Test-Path "domain/src/test/kotlin/vn/nhip2phut/domain/schedule/ScheduleTimeTest.kt") "Schedule time test is missing"
Assert-True (Test-Path "domain/src/test/kotlin/vn/nhip2phut/domain/events/EventContractRegistryV1Test.kt") "Event registry test is missing"
Assert-True ((Get-ChildItem -LiteralPath "domain/src/test/kotlin/vn/nhip2phut/domain/wire" -Recurse -File -Filter *.kt).Count -gt 0) "Closed WireV1 tests are missing"

$changelog = Read-Text "CHANGELOG.md"
$firstHeading = [regex]::Match($changelog, '(?m)^##\s+(.+)$')
Assert-True ($firstHeading.Success) "CHANGELOG.md must contain a level-2 entry"
Assert-True ($firstHeading.Groups[1].Value.Contains("DEL-01")) "CHANGELOG top entry must document DEL-01"

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "verify-wrapper.ps1")
if ($LASTEXITCODE -ne 0) { throw "Gradle Wrapper verification failed" }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "verify-module-boundaries.ps1")
if ($LASTEXITCODE -ne 0) { throw "Module boundary verification failed" }

Write-Output "DEL-01 foundation verification passed."
