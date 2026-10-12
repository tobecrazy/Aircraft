package aircraft.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

private fun Project.sdkInt(name: String): Int =
    libs.findVersion(name).get().requiredVersion.toInt()

internal fun Project.configureLibraryCommon(ext: LibraryExtension) = ext.apply {
    compileSdk = sdkInt("compile-sdk")
    defaultConfig.minSdk = sdkInt("min-sdk")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions.unitTests.isIncludeAndroidResources = true
}

internal fun Project.configureAppCommon(ext: ApplicationExtension) = ext.apply {
    compileSdk = sdkInt("compile-sdk")
    defaultConfig { minSdk = sdkInt("min-sdk"); targetSdk = sdkInt("target-sdk") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions.unitTests.isIncludeAndroidResources = true
}
