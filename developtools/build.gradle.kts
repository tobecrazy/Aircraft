plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.young.developtools"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Mirrors :app's checkAppLogUsage: android.util.Log is only legal inside DevLog.kt.
val checkDevLogUsage = tasks.register("checkDevLogUsage") {
    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(productionSources)

    doLast {
        val directLogUsage = Regex(
            """(?m)^\s*import\s+android\.util\.Log(?:\s+as\s+\w+)?\s*$|android\.util\.Log\s*\.|\bLog\s*\."""
        )
        val violations = productionSources.files
            .filterNot { it.invariantSeparatorsPath.endsWith("/utils/DevLog.kt") }
            .filter { directLogUsage.containsMatchIn(it.readText()) }

        check(violations.isEmpty()) {
            "Use DevLog instead of android.util.Log in developtools production sources:\n" +
                violations.joinToString("\n") { " - ${it.relativeTo(projectDir)}" }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("lint")) dependsOn(checkDevLogUsage)
}

dependencies {
    implementation(project(":richtexteditor"))
    implementation(project(":supperbanner"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.window)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.zxing)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
}
