import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    compileSdk = 37

    defaultConfig {
        applicationId = "com.young.aircraft"
        minSdk = 31
        targetSdk = 37
        versionCode = 16
        versionName = "1.4.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("release")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isJniDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    namespace = "com.young.aircraft"
    buildToolsVersion = "37.0.0"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val checkAppLogUsage = tasks.register("checkAppLogUsage") {
    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(productionSources)

    doLast {
        val directLogUsage = Regex(
            """(?m)^\s*import\s+android\.util\.Log(?:\s+as\s+\w+)?\s*$|android\.util\.Log\s*\.|\bLog\s*\."""
        )
        val violations = productionSources.files
            .filterNot { it.invariantSeparatorsPath.endsWith("/utils/AppLog.kt") }
            .filter { directLogUsage.containsMatchIn(it.readText()) }

        check(violations.isEmpty()) {
            "Use AppLog instead of android.util.Log in production sources:\n" +
                violations.joinToString("\n") { " - ${it.relativeTo(projectDir)}" }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("lint")) dependsOn(checkAppLogUsage)
}

// Generates the repo-root app-update.json served to clients as the Remote
// Config fallback (jsDelivr @main). latest_version tracks versionName;
// minimum_version defaults to the last enforced floor and can be raised per
// release with -PminimumVersion=X.Y.Z.
val generateRepoUpdateConfig = tasks.register("generateRepoUpdateConfig") {
    group = "release"
    description = "Generates app-update.json from versionName for the Remote Config fallback."
    val outputFile = rootProject.file("app-update.json")
    val appVersionName = android.defaultConfig.versionName as String
    val floorVersion = (findProperty("minimumVersion") as String?) ?: "1.4.1"
    inputs.property("versionName", appVersionName)
    inputs.property("minimumVersion", floorVersion)
    outputs.file(outputFile)
    doLast {
        val tag = "V$appVersionName"
        outputFile.writeText(
            "{\n" +
                "  \"minimum_version\": \"$floorVersion\",\n" +
                "  \"latest_version\": \"$appVersionName\",\n" +
                "  \"update_url\": \"https://github.com/tobecrazy/Aircraft/releases/tag/$tag\"\n" +
                "}\n"
        )
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(generateRepoUpdateConfig)
}

dependencies {
    implementation(project(":richtexteditor"))
    implementation(project(":supperbanner"))
    implementation(project(":developtools"))
    implementation(libs.androidx.foundation.layout)
    implementation(libs.zxing)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    // Force the latest graphics-path over the older transitive copy pulled in
    // by Compose UI (all LOAD segments 16 KB-aligned; see the 16 KB doc for
    // the residual upstream RELRO note).
    implementation(libs.graphics.path)
    ksp(libs.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.media)
    implementation(libs.preference.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.androidx.window)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}
