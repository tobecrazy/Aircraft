# build-logic 抽取方案（最小粒度，只抽编译配置）

> 状态：方案评审用（R3，已合入 R2 review 意见），未实施。
>
> 范围声明：**依赖版本管理由 `gradle/libs.versions.toml` 承担，build-logic 只收敛编译配置**
> （SDK/Java/单测选项、Compose 开关 + BOM platform、Log 检查复用）。
> 本方案刻意不抽依赖声明与打包逻辑——与最初"依赖与打包抽成一个模块"的目标相比是缩小的，
> 理由见 §0 与 §6。

## 0. 前置：为什么模板不能照抄

| 模板假设 | 本仓库现状 | 结论 |
|---|---|---|
| AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 24 | AGP 9.4.1 / kotlin-compose 2.4.20（root 另有 pin 2.4.10 未对齐）/ compileSdk 37 / minSdk 31 / target 37 | 照抄即降级，先按 §5.0 对齐 Kotlin pin |
| `app + core/* + feature/*` | `:app + :developtools + :supperbanner + :richtexteditor` | 无 `core/feature` 分层，不设 `feature` plugin |
| Library/Application 都 `apply org.jetbrains.kotlin.android` | 红线：勿加该插件（AGP 内置 Kotlin），仅 `app`/`developtools` 加 `kotlin.compose` | convention 不引 `kotlin.android` |
| 签名放 `~/.gradle/gradle.properties` 硬编码 | `app/build.gradle.kts:12-28` 条件读取根 `keystore.properties`，缺文件可构建 | 签名、R8、`buildToolsVersion`、`buildConfig` 留在 app，不进 convention |
| 无 Log 门禁 | `app:checkAppLogUsage`（`app/build.gradle.kts:69-92`）+ `developtools:checkDevLogUsage`（`developtools/build.gradle.kts:31-54`），均为 `lint*` 依赖 | 抽成 typed task 复用，任务名与接线保留（§2.6） |
| 无发布逻辑 | `supperbanner/build.gradle.kts:24-65` 有 `singleVariant("release") + maven-publish` 到 `build/repo` | publishing 块留在模块 |
| 建议开 `TYPESAFE_PROJECT_ACCESSORS` | 仅 4 个模块 | 不开，收益为零 |

不做的事：Camera/Coil/OkHttp/ZXing/Firebase/Room 不进 convention（各模块专属，`supperbanner`/`richtexteditor` 保持纯 View）；不聚 `api` 大模块；`app-update.json` 生成逻辑（`app/build.gradle.kts:114-147`）不动。

## 1. 目录结构

```
build-logic/
├─ settings.gradle.kts
└─ convention/
   ├─ build.gradle.kts
   └─ src/main/kotlin/
      └─ aircraft/buildlogic/
         ├─ LibsExt.kt
         ├─ ConfigureAndroid.kt
         ├─ ConfigureCompose.kt
         ├─ LogCheckTask.kt
         ├─ AircraftAndroidLibraryPlugin.kt
         ├─ AircraftAndroidComposePlugin.kt
         └─ AircraftAndroidApplicationPlugin.kt
```

`settings.gradle.kts` 保持 `FAIL_ON_PROJECT_REPOS`，新仓库只加在 settings，不加在模块。
convention 代码放具名包 `aircraft.buildlogic`，模块脚本侧 `import` 后使用（顶层默认包函数在模块脚本里解析不可靠）。

## 2. 新文件草稿

### 2.1 build-logic/settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
```

### 2.2 build-logic/convention/build.gradle.kts

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { `kotlin-dsl` }

// 不用 jvmToolchain(17)：Gradle 若跑在 JDK 21 上、本机又无 JDK 17 且无 toolchain
// 解析器时，toolchain 会直接失败。用源码兼容级别 + 编译器 jvmTarget，跑在哪个 JDK 上都能编（R2-3）。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // 仅 AGP：Configure* 引用了 api.dsl 类，需要编译期类型。
    // compose 编译器插件只按 id apply、不引用其类，不需要这里的依赖；
    // 运行时插件由主构建根 build.gradle.kts 的 apply false 提供（§4，review-1）。
    compileOnly(libs.android.gradle)
}

gradlePlugin {
    plugins {
        register("androidLibrary") { id = "aircraft.android.library"; implementationClass = "aircraft.buildlogic.AircraftAndroidLibraryPlugin" }
        register("androidCompose") { id = "aircraft.android.compose"; implementationClass = "aircraft.buildlogic.AircraftAndroidComposePlugin" }
        register("androidApplication") { id = "aircraft.android.application"; implementationClass = "aircraft.buildlogic.AircraftAndroidApplicationPlugin" }
    }
}
```

### 2.3 LibsExt.kt

```kotlin
package aircraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
```

### 2.4 ConfigureAndroid.kt（SDK 从 toml 读，不写死；review-6）

```kotlin
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
```

只收敛现状，不新增行为（不加模板里的 `packaging.resources.excludes`）。

### 2.5 ConfigureCompose.kt（只开开关 + BOM，不注入 UI 依赖）

刻意不用 `CommonExtension<*, …>`：该泛型形参列表是 AGP 8.x 形态，9.x 已变，
为避免跨版本泛型对不上，用 `LibraryExtension / ApplicationExtension` 两个重载
（review-2，实施时对着 9.4.1 实际编译确认）：

```kotlin
package aircraft.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

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
```

BOM 只管版本对齐；`compose-ui / material3 / activity-compose / window` 等仍由各模块显式声明。
`androidTestImplementation` 里若有用 BOM 管版本的 Compose 测试依赖，BOM 同样覆盖该配置（review-9）。

### 2.6 LogCheckTask.kt（typed task，兼容 configuration cache；R2-1）

两处 configuration cache 禁区都已避开：源码输入用 `@InputFiles + @PathSensitive(RELATIVE)`
（`@Classpath` 是给 classpath 归一化用的，源码扫描用错了），目录在**配置期**经
`DirectoryProperty` 注入——`@TaskAction` 里不再碰 `Task.project`（含 `project.projectDir`）。
属性统一用 `Property<String>` 受管写法；action 方法命名为 `verify()`，避免与 Kotlin `check()` 同名混淆：

```kotlin
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

abstract class LogCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** 允许直接用 Log 的白名单文件后缀，如 "/utils/AppLog.kt"。 */
    @get:Input
    abstract val allowSuffix: Property<String>

    @get:Input
    abstract val errorMessage: Property<String>

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val allow = allowSuffix.get()
        val base = projectDir.get().asFile
        val regex = Regex("""(?m)^\s*import\s+android\.util\.Log(?:\s+as\s+\w+)?\s*$|android\.util\.Log\s*\.|\bLog\s*\.""")
        val violations = sources.files
            .filterNot { it.invariantSeparatorsPath.endsWith(allow) }
            .filter { regex.containsMatchIn(it.readText()) }
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
    // 与现状接线一致：所有 lint* 任务都依赖检查（含 lintFix、lintAnalyze*、lintVital*）。
    tasks.configureEach { if (name.startsWith("lint")) dependsOn(checkTask) }
}
```

模块侧用法（`app/build.gradle.kts`）：

```kotlin
import aircraft.buildlogic.registerLogCheck
registerLogCheck("checkAppLogUsage", "/utils/AppLog.kt", "Use AppLog instead of android.util.Log in production sources")
```

注：该检查只拦截 `android.util.Log` / `Log.` 引用，不限制 AppLog 自身的调用形式；但 AppLog
调用一律用尾随 lambda `AppLog.d(tag) { … }`（见 CLAUDE.md），以免与
`d(tag, msg: String, tr: Throwable?)` 重载混淆——误写成 `AppLog.d(tag, { … })` 也能编译，review 时不易察觉。

注：该任务无 outputs，每次都会执行；扫描成本只有两次正则，可接受，属预期行为，不是缓存故障。

### 2.7 三个 Convention Plugin（统一新 DSL；review-2/3）

旧草稿混用了旧 DSL（`com.android.build.gradle.AppExtension`）与新 DSL（`api.dsl.*`），
且 `AppExtension` 与 `configureAppCommon(ApplicationExtension)` 类型对不上。
统一用 `com.android.build.api.dsl`，并补上缺失的 `import org.gradle.kotlin.dsl.configure`：

```kotlin
package aircraft.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AircraftAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> { target.configureLibraryCommon(this) }
    }
}
```

```kotlin
package aircraft.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AircraftAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // withPlugin 而非要求顺序：即使 compose 排在 library 之前也能生效（R2-4）。
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> { target.configureCompose(this) }
        }
    }
}
```

```kotlin
package aircraft.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AircraftAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> { target.configureAppCommon(this); target.configureCompose(this) }
    }
}
```

## 3. libs.versions.toml 增补

```toml
[versions]
compile-sdk = "37"
min-sdk = "31"
target-sdk = "37"
# kotlin = "2.4.20"  # 见 §5.0：与 kotlin-compose 合并为单一 ref

[libraries]
android-gradle = { module = "com.android.tools.build:gradle", version.ref = "agp" }
```

说明：

- SDK 三个数字进 toml，`ConfigureAndroid.kt` 经 `libs.findVersion` 读取——
  "版本只在 toml 维护一份"的口径保持成立（review-6）。
- 只有 `android-gradle` 一个新增 library（给 convention `compileOnly` 用）。
  不需要 `kotlin-compose-plugin` 条目：convention 不引用其类（review-8 顺带解决；
  若 `kotlin-dsl` 内嵌 Kotlin 与 compose 插件元数据不兼容，按 id apply 的方式不受影响）。

## 4. 存量文件改动

- 根 `settings.gradle.kts`：`pluginManagement` 加 `includeBuild("build-logic")`，其余原样
  （`FAIL_ON_PROJECT_REPOS` 不动）。
- 根 `build.gradle.kts`：**全部 `apply false` 原样保留**（review-1，Now in Android 做法）。
  convention 的 `pluginManager.apply("com.android.library")` 等按 id 应用，
  运行时插件由主构建的 `apply false` classpath 提供；`compileOnly` 只解决编译期类型。
  删掉它们会导致 `Plugin with id ... not found`，且与保留 ksp/google-services/crashlytics 自相矛盾。
- `richtexteditor` / `supperbanner`：
  `plugins { id("aircraft.android.library") }`（后者再加 `` `maven-publish` ``），
  删 `compileSdk/minSdk/compileOptions/testOptions`，
  `namespace`、`consumerProguardFiles`、`dependencies`、`publishing` 原样保留。
- `developtools`：
  `plugins { id("aircraft.android.library"); id("aircraft.android.compose") }`，
  明确删除以下三处（改由 convention 提供；留下虽无害，但会掩盖真正的生效位置，R2-6）：
  `alias(libs.plugins.kotlin.compose)`、`buildFeatures { compose = true }`、
  `implementation(platform(libs.compose.bom))`；
  现有检查任务块替换为 §2.6 的一行 `registerLogCheck("checkDevLogUsage", "/utils/DevLog.kt", …)`，
  其余 `dependencies` 原样保留。
- `app`：
  `plugins { id("aircraft.android.application"); alias(ksp); alias(google-services); alias(crashlytics) }`，
  明确删除 `alias(libs.plugins.kotlin.compose)`、`buildFeatures` 中的 `compose = true`、
  `implementation(platform(libs.compose.bom))`（compose 开关 + BOM 改由 convention 提供）；
  `buildFeatures` 中的 `buildConfig = true`、签名/R8/`buildToolsVersion`/
  `generateRepoUpdateConfig`/`checkAppLogUsage` 接线原样保留，其余 `dependencies` 原样保留。

## 5. 迁移与验证

### 5.0 先对齐 Kotlin pin（可执行步骤，review-7；R2-2 修正）

1. `gradle/libs.versions.toml` 里把 `kotlin-compose = "2.4.20"` 与根 pin `2.4.10`
   合并为单一 `kotlin = "2.4.20"`（或经评估后共同定一个版本），根 `build.gradle.kts:13-18`
   的 `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:…")` 取同一值并更新注释；
   toml 里 `kotlin-compose` 这个 plugin alias 的 `version.ref` 同步指向合并后的 `kotlin`。
2. 跑 `./gradlew buildEnvironment | grep kotlin-gradle-plugin` 确认实际解析的 KGP 版本。
3. compose 编译器插件版本必须等于 Kotlin 版本（Kotlin 2.0 起 Compose 编译器随 Kotlin 同版本发布）。

### 5.1 先做 spike，再逐模块迁移（R2-5）

0. spike（在迁移 `richtexteditor` 之前单独做一次）：只建 `build-logic` 空壳 +
   一个引用 AGP 类型的最小 convention，验证两件没把握的事——
   (a) 模块脚本里 `import aircraft.buildlogic.registerLogCheck` 能否解析
   （若不行，退路是封装成 `aircraft.android.logcheck` 小插件加 extension）；
   (b) `kotlin-dsl` 内嵌 Kotlin 读取 AGP 9.4.1 类元数据是否正常
   （`compileOnly` 的 AGP 要实际编译一次才算数；§3 只论证了 compose 插件不受影响）。
1. 逐模块迁移，每模块一个 commit：
   `richtexteditor` → `supperbanner` → `developtools` → `app`，每步 `./gradlew :<mod>:assembleDebug`。

### 5.2 全量验证

- CI 等价：`./gradlew assembleDebug lintDebug testDebugUnitTest` + `./gradlew :app:checkAppLogUsage`，
  以 `BUILD SUCCESSFUL` 为准（不以管道后段退出码为准）。
- `assembleRelease`（R8 + 签名最易被误伤；需本机 `keystore.properties`，CI 跑不了，本地跑一次）。
- 迁移前后对比 `:app:dependencies`（或 `androidDependencies`），确认 BOM 注入未改变解析结果。
- `supperbanner` 的 `build/repo` 内 pom/aar 与迁移前对比。
- `generateRepoUpdateConfig` 产物（根 `app-update.json`）与迁移前对比。
- 带 `--configuration-cache` 跑一次全量（覆盖 §2.6 的改动点）。

### 5.3 回滚

回滚即 revert 对应模块的迁移 commit（review-4 修正：只删 `includeBuild` 不能回滚，
因为模块脚本已引用 `aircraft.*` 插件 ID，那样只会构建失败）。

## 6. 取舍提醒

4 个模块，真正抽走的是每模块约 5 行重复配置 + 两份 Log 检查；代价是多一个 included build，
以及 §2 中这类 classpath 与 AGP 9 适配成本。更轻的替代方案：SDK 版本放 toml，
只把 Log 检查抽成小插件——若后续不再加模块，该替代方案性价比更高。
