package aircraft.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AircraftAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // withPlugin instead of ordering: works even if compose precedes library.
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> { target.configureCompose(this) }
        }
    }
}
