package aircraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal data class DependencyDeclaration(
    val module: String,
    val configuration: String,
    val line: Int,
)

internal data class DuplicateDependency(
    val module: String,
    val declarations: List<DependencyDeclaration>,
)

private val catalogEntryPattern = Regex("""^\s*([A-Za-z0-9_.-]+)\s*=\s*\{([^}]*)}\s*$""")
private val modulePattern = Regex("\\bmodule\\s*=\\s*\"([^\"]+)\"")
private val groupPattern = Regex("\\bgroup\\s*=\\s*\"([^\"]+)\"")
private val namePattern = Regex("\\bname\\s*=\\s*\"([^\"]+)\"")
private val dependencyCallPattern = Regex("""^\s*([A-Za-z][A-Za-z0-9_]*)\s*\((.*)\)\s*$""")
private val catalogAccessorPattern = Regex("""\blibs\.([A-Za-z_][A-Za-z0-9_.]*)""")
private val coordinatePattern = Regex("""["']([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::[^"']*)?["']""")

internal fun findDuplicateDependencies(buildScript: String, versionCatalog: String): List<DuplicateDependency> {
    val aliases = parseLibraryAliases(versionCatalog)
    val declarations = mutableListOf<DependencyDeclaration>()
    var inDependencies = false
    var depth = 0

    buildScript.lineSequence().forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (!inDependencies && trimmed.startsWith("dependencies") && trimmed.contains('{')) {
            inDependencies = true
            depth = 0
        } else if (inDependencies) {
            dependencyCallPattern.matchEntire(line)?.let { match ->
                val configuration = match.groupValues[1]
                val notation = match.groupValues[2]
                val modules = buildSet {
                    catalogAccessorPattern.findAll(notation).forEach { accessor ->
                        aliases[accessor.groupValues[1]]?.let(::add)
                    }
                    coordinatePattern.findAll(notation).forEach { coordinate ->
                        add("${coordinate.groupValues[1]}:${coordinate.groupValues[2]}")
                    }
                }
                modules.forEach { module ->
                    declarations += DependencyDeclaration(module, configuration, index + 1)
                }
            }
        }

        if (inDependencies) {
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (depth == 0) inDependencies = false
        }
    }

    return declarations
        .groupBy { it.module }
        .filterValues { it.size > 1 }
        .map { (module, duplicates) -> DuplicateDependency(module, duplicates) }
        .sortedBy { it.module }
}

private fun parseLibraryAliases(versionCatalog: String): Map<String, String> {
    var inLibraries = false
    return versionCatalog.lineSequence().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            inLibraries = trimmed == "[libraries]"
            return@mapNotNull null
        }
        if (!inLibraries || trimmed.startsWith('#')) return@mapNotNull null

        val entry = catalogEntryPattern.matchEntire(line) ?: return@mapNotNull null
        val fields = entry.groupValues[2]
        val module = modulePattern.find(fields)?.groupValues?.get(1)
            ?: listOfNotNull(
                groupPattern.find(fields)?.groupValues?.get(1),
                namePattern.find(fields)?.groupValues?.get(1),
            ).takeIf { it.size == 2 }?.joinToString(":")
            ?: return@mapNotNull null
        entry.groupValues[1].replace('-', '.') to module
    }.toMap()
}

abstract class DuplicateDependencyCheckTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildScript: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionCatalog: RegularFileProperty

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun verify() {
        val duplicates = findDuplicateDependencies(
            buildScript.get().asFile.readText(),
            versionCatalog.get().asFile.readText(),
        )
        check(duplicates.isEmpty()) {
            "Duplicate dependencies in ${modulePath.get()}:\n" + duplicates.joinToString("\n") { duplicate ->
                " - ${duplicate.module}: " + duplicate.declarations.joinToString { "${it.configuration} (line ${it.line})" }
            }
        }
    }
}

fun Project.registerDuplicateDependencyCheck() {
    val checkTask = tasks.register("checkDuplicateDependencies", DuplicateDependencyCheckTask::class.java) {
        buildScript.set(layout.projectDirectory.file("build.gradle.kts"))
        versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
        modulePath.set(path)
    }
    tasks.configureEach { if (name.startsWith("lint")) dependsOn(checkTask) }
}
