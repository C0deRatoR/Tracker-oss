plugins {
    // Kotlin support is built into AGP 9+; the org.jetbrains.kotlin.android plugin is
    // incompatible with it and must not be applied.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.google.services) apply false
}
