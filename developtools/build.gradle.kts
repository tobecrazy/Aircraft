import aircraft.buildlogic.registerLogCheck

plugins {
    id("aircraft.android.library")
    id("aircraft.android.compose")
    alias(libs.plugins.ksp)
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
    implementation(libs.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.window)
    implementation(libs.datastore.preferences)

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
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.genai.prompt)
    // Structured output parses the @Generable card result class at runtime; KSP
    // emits the schema provider that the Prompt API looks up.
    ksp(libs.mlkit.genai.schema.compiler)

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
