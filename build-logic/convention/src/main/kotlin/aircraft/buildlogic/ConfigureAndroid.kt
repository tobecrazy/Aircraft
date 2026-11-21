package aircraft.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

private fun Project.sdkInt(name: String): Int =
    libs.findVersion(name).get().requiredVersion.toInt()

/** Reads a `major` or `major.minor` SDK version; the minor part is null when absent. */
private fun Project.sdkVersion(name: String): Pair<Int, Int?> {
    val parts = libs.findVersion(name).get().requiredVersion.split('.')
    return parts[0].toInt() to parts.getOrNull(1)?.toIntOrNull()
}

internal fun Project.configureLibraryCommon(ext: LibraryExtension) = ext.apply {
    val (compileSdkMajor, compileSdkMinorVersion) = sdkVersion("compile-sdk")
    compileSdk = compileSdkMajor
    compileSdkMinor = compileSdkMinorVersion
    defaultConfig.minSdk = sdkInt("min-sdk")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions.unitTests.isIncludeAndroidResources = true
}

internal fun Project.configureAppCommon(ext: ApplicationExtension) = ext.apply {
    val (compileSdkMajor, compileSdkMinorVersion) = sdkVersion("compile-sdk")
    compileSdk = compileSdkMajor
    compileSdkMinor = compileSdkMinorVersion
    defaultConfig { minSdk = sdkInt("min-sdk"); targetSdk = sdkInt("target-sdk") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions.unitTests.isIncludeAndroidResources = true
}
