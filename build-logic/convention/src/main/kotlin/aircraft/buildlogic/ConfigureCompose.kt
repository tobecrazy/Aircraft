package aircraft.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

// Deliberately no CommonExtension<*, ...>: that generic parameter list is the AGP 8.x
// shape and changed in 9.x, so two overloads avoid coupling to it.
internal fun Project.configureCompose(ext: LibraryExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    ext.buildFeatures.compose = true
    dependencies { add("implementation", platform(libs.findLibrary("compose-bom").get())) }
}

internal fun Project.configureCompose(ext: ApplicationExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    ext.buildFeatures.compose = true
    dependencies { add("implementation", platform(libs.findLibrary("compose-bom").get())) }
}
