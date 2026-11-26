package aircraft.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDependencyCheckTest {

    @Test
    fun `finds same catalog module across configurations`() {
        val duplicates = findDuplicateDependencies(
            buildScript = """
                dependencies {
                    testImplementation(libs.junit)
                    androidTestImplementation(libs.junit)
                }
            """.trimIndent(),
            versionCatalog = """
                [libraries]
                junit = { module = "junit:junit", version = "4.13.2" }
            """.trimIndent(),
        )

        assertEquals(listOf("junit:junit"), duplicates.map { it.module })
        assertEquals(listOf("testImplementation", "androidTestImplementation"), duplicates.single().declarations.map { it.configuration })
    }

    @Test
    fun `finds aliases that resolve to the same module`() {
        val duplicates = findDuplicateDependencies(
            buildScript = """
                dependencies {
                    implementation(libs.androidx.appcompat)
                    implementation(libs.androidx.appcompat.richtext)
                }
            """.trimIndent(),
            versionCatalog = """
                [libraries]
                androidx-appcompat = { module = "androidx.appcompat:appcompat", version = "1.8.0" }
                androidx-appcompat-richtext = { module = "androidx.appcompat:appcompat", version = "1.8.0" }
            """.trimIndent(),
        )

        assertEquals(listOf("androidx.appcompat:appcompat"), duplicates.map { it.module })
    }

    @Test
    fun `allows unique dependencies`() {
        val duplicates = findDuplicateDependencies(
            buildScript = """
                dependencies {
                    implementation(libs.junit)
                    testImplementation("org.robolectric:robolectric:4.18")
                }
            """.trimIndent(),
            versionCatalog = """
                [libraries]
                junit = { module = "junit:junit", version = "4.13.2" }
            """.trimIndent(),
        )

        assertTrue(duplicates.isEmpty())
    }
}
