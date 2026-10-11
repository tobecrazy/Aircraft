plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// KGP and the Compose compiler plugin share one version (see `kotlin` in
// libs.versions.toml; Kotlin 2.0+ ships the Compose compiler with Kotlin itself).
// The pin below puts that KGP on the buildscript classpath so AGP's compose
// mapping tasks can resolve a matching compose-group-mapping artifact.
buildscript {
    dependencies {
        //noinspection UseTomlInstead
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
