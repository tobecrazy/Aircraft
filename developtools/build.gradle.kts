import aircraft.buildlogic.registerLogCheck

plugins {
    id("aircraft.android.library")
    id("aircraft.android.compose")
}

android {
    namespace = "com.young.developtools"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

// Mirrors :app's checkAppLogUsage: android.util.Log is only legal inside DevLog.kt.
registerLogCheck(
    "checkDevLogUsage",
    "/utils/DevLog.kt",
    "Use DevLog instead of android.util.Log in developtools production sources"
)

dependencies {
    implementation(project(":richtexteditor"))
    implementation(project(":supperbanner"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.window)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.material)
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
    implementation(libs.mlkit.text.recognition)
    // Bundled Chinese model: ID cards print Chinese glyphs, which the Latin model cannot read.
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.coroutines.play.services)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.zxing)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.runner)
}
