plugins {
    id("aircraft.android.library")
}

android {
    namespace = "com.young.richtext"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.androidx.appcompat.richtext)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
