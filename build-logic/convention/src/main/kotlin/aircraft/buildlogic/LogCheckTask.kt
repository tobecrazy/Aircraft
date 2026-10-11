package aircraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

internal val directLogUsagePattern =
    Regex("""(?m)^\s*import\s+android\.util\.Log(?:\s+as\s+\w+)?\s*$|android\.util\.Log\s*\.|\bLog\s*\.""")

/**
 * Pure, unit-tested core of [LogCheckTask] (see `LogCheckTest`): no Gradle
 * types involved, so it runs on plain JUnit without TestKit or AGP.
 */
internal fun findDirectLogUsages(files: Collection<File>, allowSuffix: String): List<File> =
    files
        .filterNot { it.invariantSeparatorsPath.endsWith(allowSuffix) }
        .filter { directLogUsagePattern.containsMatchIn(it.readText()) }

abstract class LogCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Whitelisted file suffix allowed to use Log directly, e.g. "/utils/AppLog.kt". */
    @get:Input
    abstract val allowSuffix: Property<String>

    @get:Input
    abstract val errorMessage: Property<String>

    // Injected at configuration time so the action never touches Task.project,
    // which configuration cache forbids at execution time.
    @get:Internal
    abstract val projectDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val base = projectDir.get().asFile
        val violations = findDirectLogUsages(sources.files, allowSuffix.get())
        check(violations.isEmpty()) {
            "${errorMessage.get()}:\n" + violations.joinToString("\n") { " - ${it.relativeTo(base)}" }
        }
    }
}

fun Project.registerLogCheck(taskName: String, allowSuffix: String, errorMessage: String) {
    val checkTask = tasks.register(taskName, LogCheckTask::class.java) {
        sources.from(fileTree("src/main") { include("**/*.kt", "**/*.java") })
        projectDir.set(layout.projectDirectory)
        this.allowSuffix.set(allowSuffix)
        this.errorMessage.set(errorMessage)
    }
    // Same wiring as before: every lint* task depends on the check.
    tasks.configureEach { if (name.startsWith("lint")) dependsOn(checkTask) }
}
