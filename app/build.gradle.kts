import aircraft.buildlogic.registerLogCheck
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("aircraft.android.application")
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

    defaultConfig {
        applicationId = "com.young.aircraft"
        versionCode = 32
        versionName = "1.6.0"
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
    buildFeatures {
        buildConfig = true
    }
    packaging {
        jniLibs {
            // OpenCV only backs the bank-card scanner. Ship its arm64 build and let
            // every other ABI fall back to the pure-Kotlin filters in
            // ImagePreprocessor instead of adding ~110 MB of native code to the APK.
            excludes += listOf(
                "lib/armeabi-v7a/libopencv_java5.so",
                "lib/x86/libopencv_java5.so",
                "lib/x86_64/libopencv_java5.so",
            )
        }
    }
    namespace = "com.young.aircraft"
    buildToolsVersion = "37.0.0"
}

registerLogCheck(
    "checkAppLogUsage",
    "/utils/AppLog.kt",
    "Use AppLog instead of android.util.Log in production sources"
)

// Generates the repo-root app-update.json served to clients as the Remote
// Config fallback (jsDelivr @main). latest_version tracks versionName;
// minimum_version defaults to the last enforced floor and can be raised per
// release with -PminimumVersion=X.Y.Z. The remaining keys mirror the Firebase
// Remote Config logic: app_icon follows the AppIcon 1..5 variant validation,
// welcomeMessage/showWelcome follow the token-config welcome gating, with
// per-release overrides -PappIcon=N, -PwelcomeMessage=..., -PshowWelcome=....
fun String.escapeForJson(): String = buildString {
    for (char in this@escapeForJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
        }
    }
}

val generateRepoUpdateConfig = tasks.register("generateRepoUpdateConfig") {
    group = "release"
    description = "Generates app-update.json from versionName for the Remote Config fallback."
    val outputFile = rootProject.file("app-update.json")
    val appVersionName = android.defaultConfig.versionName as String
    val floorVersion = (findProperty("minimumVersion") as String?) ?: "1.5.6"
    val appIcon = (findProperty("appIcon") as String?) ?: "1"
    // The {{welcome_message}} placeholder keeps app-update.json language-neutral:
    // clients resolve it to the localized R.string.welcome_message at runtime.
    val welcomeMessage = (findProperty("welcomeMessage") as String?)
        ?: "{{welcome_message}}"
    val showWelcome = ((findProperty("showWelcome") as String?) ?: "true").toBooleanStrictOrNull() ?: true
    inputs.property("versionName", appVersionName)
    inputs.property("minimumVersion", floorVersion)
    inputs.property("appIcon", appIcon)
    inputs.property("welcomeMessage", welcomeMessage)
    inputs.property("showWelcome", showWelcome)
    outputs.file(outputFile)
    doLast {
        val tag = "V$appVersionName"
        outputFile.writeText(
            "{\n" +
                "  \"minimum_version\": \"$floorVersion\",\n" +
                "  \"latest_version\": \"$appVersionName\",\n" +
                "  \"update_url\": \"https://github.com/tobecrazy/Aircraft/releases/tag/$tag\",\n" +
                "  \"app_icon\": $appIcon,\n" +
                "  \"welcomeMessage\": \"${welcomeMessage.escapeForJson()}\",\n" +
                "  \"showWelcome\": $showWelcome\n" +
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

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.window)
    implementation(libs.constraintlayout)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.media)
    implementation(libs.material)
    implementation(libs.preference.ktx)
    implementation(libs.viewpager2)

    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.camera.camera2)
    implementation(libs.camera.core)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.zxing)

    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    // Force the latest graphics-path over the older transitive copy pulled in
    // by Compose UI (all LOAD segments 16 KB-aligned; see the 16 KB doc for
    // the residual upstream RELRO note).
    implementation(libs.graphics.path)
    ksp(libs.room.compiler)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.runner)
}
