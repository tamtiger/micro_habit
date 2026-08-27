plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("verifyFoundation") {
    group = "verification"
    description = "Runs the DEL-01 foundation verification gate."
    dependsOn(
        ":domain:test",
        ":data:testDebugUnitTest",
        ":platform:testDebugUnitTest",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":app:verifyDebugMergedManifest",
    )
}

tasks.register("verifyFoundationDevice") {
    group = "verification"
    description = "Runs the DEL-01 API-36 connected verification gate."
    dependsOn(":app:connectedDebugAndroidTest", ":data:connectedDebugAndroidTest")
}

