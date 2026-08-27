param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath
)

$ErrorActionPreference = "Stop"
$resolvedManifest = (Resolve-Path -LiteralPath $ManifestPath).Path
[xml]$manifest = Get-Content -LiteralPath $resolvedManifest -Raw

$androidNamespace = "http://schemas.android.com/apk/res/android"
$namespaceManager = New-Object Xml.XmlNamespaceManager($manifest.NameTable)
$namespaceManager.AddNamespace("android", $androidNamespace)

function Android-Attribute([Xml.XmlElement]$element, [string]$name) {
    return $element.GetAttribute($name, $androidNamespace)
}

function Assert-ExactStringSet(
    [string[]]$actual,
    [string[]]$expected,
    [string]$description
) {
    $actualValues = @($actual)
    $expectedValues = @($expected)
    if ($actualValues.Count -ne $expectedValues.Count) {
        throw "$description must contain exactly $($expectedValues.Count) value(s); found $($actualValues.Count): $($actualValues -join ', ')."
    }

    if ($expectedValues.Count -eq 0) { return }

    $difference = @(
        Compare-Object `
            -ReferenceObject @($expectedValues | Sort-Object) `
            -DifferenceObject @($actualValues | Sort-Object)
    )
    if ($difference.Count -ne 0) {
        throw "$description differs from the exact allowlist: $($difference | Out-String)"
    }
}

function Assert-ExactIntentFilter(
    [Xml.XmlElement]$component,
    [string]$componentDescription,
    [string[]]$expectedActions,
    [string[]]$expectedCategories
) {
    $filters = @($component.SelectNodes("intent-filter", $namespaceManager))
    if ($filters.Count -ne 1) {
        throw "$componentDescription must contain exactly one intent-filter; found $($filters.Count)."
    }

    $filter = $filters[0]
    $actions = @(
        $filter.SelectNodes("action", $namespaceManager) |
            ForEach-Object { Android-Attribute $_ "name" }
    )
    $categories = @(
        $filter.SelectNodes("category", $namespaceManager) |
            ForEach-Object { Android-Attribute $_ "name" }
    )

    Assert-ExactStringSet $actions $expectedActions "$componentDescription intent-filter actions"
    Assert-ExactStringSet $categories $expectedCategories "$componentDescription intent-filter categories"

    $unexpectedElements = @(
        $filter.ChildNodes |
            Where-Object {
                $_.NodeType -eq [Xml.XmlNodeType]::Element -and
                $_.LocalName -notin @("action", "category")
            }
    )
    if ($unexpectedElements.Count -ne 0) {
        throw "$componentDescription intent-filter contains unexpected element(s): $($unexpectedElements.LocalName -join ', ')."
    }
}

$usesSdk = $manifest.SelectSingleNode("/manifest/uses-sdk", $namespaceManager)
if ($null -eq $usesSdk) { throw "Merged manifest does not contain uses-sdk." }
if ((Android-Attribute $usesSdk "minSdkVersion") -ne "26") { throw "Merged minSdkVersion must be 26." }
if ((Android-Attribute $usesSdk "targetSdkVersion") -ne "36") { throw "Merged targetSdkVersion must be 36." }

$allowedPermissions = @(
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED"
)
$sdk23PermissionNodes = @($manifest.SelectNodes("/manifest/uses-permission-sdk-23", $namespaceManager))
if ($sdk23PermissionNodes.Count -ne 0) {
    throw "Merged manifest must not use uses-permission-sdk-23; declare the exact canonical allowlist with uses-permission."
}
$permissionNodes = @($manifest.SelectNodes("/manifest/uses-permission", $namespaceManager))
$permissionNodes | ForEach-Object {
    if ((Android-Attribute $_ "maxSdkVersion") -ne "") {
        throw "Merged permission declarations must not use android:maxSdkVersion."
    }
}
$actualPermissions = @(
    $permissionNodes | ForEach-Object { Android-Attribute $_ "name" }
)
Assert-ExactStringSet $actualPermissions $allowedPermissions "Merged permission declarations"

$rawManifest = Get-Content -LiteralPath $resolvedManifest -Raw
if ($rawManifest -match "ACTION_CALL|android\.intent\.action\.CALL") {
    throw "Merged manifest must not expose ACTION_CALL capability."
}

$application = $manifest.SelectSingleNode("/manifest/application", $namespaceManager)
if ($null -eq $application) { throw "Merged manifest does not contain an application node." }
if ((Android-Attribute $application "allowBackup") -ne "false") { throw "android:allowBackup must be false." }
if ((Android-Attribute $application "usesCleartextTraffic") -ne "false") { throw "android:usesCleartextTraffic must be false." }

$packageName = $manifest.DocumentElement.GetAttribute("package")
function Normalize-ComponentName([string]$name) {
    if ($name.StartsWith(".")) { return "$packageName$name" }
    if ($name.Contains(".")) { return $name }
    return "$packageName.$name"
}

$expectedComponents = @{
    "activity|vn.nhip2phut.app.MainActivity" = "true"
    "receiver|vn.nhip2phut.platform.notification.BootReconcileReceiver" = "false"
}
$actualComponents = @{}
foreach ($node in $application.SelectNodes("activity|activity-alias|receiver|service|provider", $namespaceManager)) {
    $name = Normalize-ComponentName (Android-Attribute $node "name")
    $key = "$($node.LocalName)|$name"
    $actualComponents[$key] = Android-Attribute $node "exported"
}

if ($actualComponents.Count -ne $expectedComponents.Count) {
    throw "Merged manifest contains an unexpected component count: $($actualComponents.Keys -join ', ')."
}
foreach ($key in $expectedComponents.Keys) {
    if (-not $actualComponents.ContainsKey($key)) { throw "Required merged component is missing: $key" }
    if ($actualComponents[$key] -ne $expectedComponents[$key]) { throw "Unexpected exported value for $key." }
}

$activity = $application.SelectSingleNode("activity[@android:name='.MainActivity' or @android:name='vn.nhip2phut.app.MainActivity']", $namespaceManager)
if ($null -eq $activity) { throw "MainActivity is missing from the merged manifest." }
Assert-ExactIntentFilter `
    $activity `
    "MainActivity" `
    @("android.intent.action.MAIN") `
    @("android.intent.category.LAUNCHER")

$bootReceiver = $application.SelectSingleNode("receiver[@android:name='vn.nhip2phut.platform.notification.BootReconcileReceiver']", $namespaceManager)
if ($null -eq $bootReceiver) { throw "BootReconcileReceiver is missing from the merged manifest." }
Assert-ExactIntentFilter `
    $bootReceiver `
    "BootReconcileReceiver" `
    @(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED"
    ) `
    @()

Write-Host "ARC-109 merged manifest SDK, permissions, exact component intent filters, backup and cleartext posture verified."
