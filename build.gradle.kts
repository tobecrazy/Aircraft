plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Raise AGP's built-in Kotlin (KGP) from 2.2.10 to match the Compose compiler plugin (2.4.10),
// so AGP's compose mapping tasks can resolve org.jetbrains.kotlin:compose-group-mapping:2.4.10
// (that artifact is only published for KGP 2.3.0+).
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
