plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "com.young"
version = "1.0.0"

android {
    namespace = "com.young.supperbanner"
    compileSdk = 37

    defaultConfig {
        minSdk = 32
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.coil)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

publishing {
    repositories {
        // Local file repo so `publish` works without credentials. Add a remote `maven { url = ... }`
        // here when the AAR is pushed to an internal Maven server.
        maven {
            name = "buildRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        register<MavenPublication>("release") {
            artifactId = "supperbanner"
            // singleVariant("release") registers the component late, so resolve it after evaluation.
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("SupperBanner")
                description.set("Auto-playing banner view for Aircraft, packaged as an AAR")
            }
        }
    }
}
