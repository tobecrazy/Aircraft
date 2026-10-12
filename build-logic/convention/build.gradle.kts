import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { `kotlin-dsl` }

// Do not use jvmToolchain(17): when Gradle runs on a newer JDK with no JDK 17
// installed and no toolchain resolver, toolchain resolution fails outright.
// Source/target levels plus the compiler jvmTarget build on any host JDK.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // AGP only: Configure* references api.dsl types, so they are needed at compile time.
    // The Compose compiler plugin is applied by id and none of its classes are
    // referenced, so it needs no dependency here; at runtime the plugin comes from
    // the main build's root `apply false` classpath (see §4 of the extraction plan).
    compileOnly(libs.android.gradle)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("androidLibrary") { id = "aircraft.android.library"; implementationClass = "aircraft.buildlogic.AircraftAndroidLibraryPlugin" }
        register("androidCompose") { id = "aircraft.android.compose"; implementationClass = "aircraft.buildlogic.AircraftAndroidComposePlugin" }
        register("androidApplication") { id = "aircraft.android.application"; implementationClass = "aircraft.buildlogic.AircraftAndroidApplicationPlugin" }
    }
}
