# AGENTS.md

- **中文回复**：所有解释用中文。
- 详情见 `CLAUDE.md`（完整手册）；`docs/puzzle-game-redesign-plan.md` 必读后再改拼图计分；`docs/*-aar-usage.md` 有库集成说明。`.github/copilot-instructions.md` 部分声明已过时，以代码为准。

## 模块

- `:app` 游戏本体 + 设置/关于/隐私等用户屏；`:developtools` 全部工具屏（Develop/助手/API调试/扫码/联系人/PDF/富文本/二维码/图片详情/手电/设备信息 + 门控/日志/历史存储契约，见 `docs/develop-settings-module-plan.md`）；`:richtexteditor`（仅 `RichTextEditorView`）；`:supperbanner`（`SupperBannerView` + config/colors/effect/transition 类型）。后两者是无字符串、无游戏依赖的纯 View 库；`:developtools` 自带字符串（4 套 locale）与主题/基类拷贝，依赖 `:supperbanner` + `:richtexteditor`，禁止反向依赖 `:app`（跨模块经 `DevTools` 4 hook）。
- JDK 17 + 签入的 Gradle wrapper；`compileSdk 37 / minSdk 31`；版本集中在 `gradle/libs.versions.toml`。勿加 `org.jetbrains.kotlin.android`（AGP 内置 Kotlin），app 仍需 `org.jetbrains.kotlin.plugin.compose`。
- `settings.gradle.kts` 为 `FAIL_ON_PROJECT_REPOS`：新仓库只能加在 settings，不能加在模块。ViewBinding 已删除、DataBinding 未用；无 DI 框架。

## 命令（仓库根）

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest   # CI 等价（CI 只跑这三个）
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.ui.GameCoreViewFormulaTest"  # 单测必须挂模块 task，根 task 加 --tests 匹配不到
./gradlew :app:checkAppLogUsage                       # AppLog 专用检查，也是所有 lint* 的依赖
./gradlew :supperbanner:publishReleasePublicationToBuildRepoRepository  # 发布 com.young:supperbanner 到 build/repo
```

- Release 需要根目录 `keystore.properties`（未跟踪）；`assembleRelease`/`bundleRelease` 前自动重写根目录 `app-update.json`（Remote Config 回落源，`latest=versionName`，`minimum` 可用 `-PminimumVersion=` 抬高），release 后记得把它合到 `main`；`connectedAndroidTest` 已死（仅脚手架），勿依赖。
- **版本号按十进制递增**（`versionName`）：正常发版 patch +1（1.4.3→1.4.4）；patch 到 9 后进位到 minor（1.4.9→1.5.0）；minor 进位到 9 后再进位到 major（1.9.9→2.0.0），以此类推。`versionCode` 每次发版固定 +1。

## 架构红线

- 游戏引擎是渲染线程封闭，不是 MVVM：`ui/GameCoreView`（SurfaceView + 30 FPS 循环）拥有组合、碰撞、计时、Boss/升级。UI 线程**必须经 `gameCommands.enqueue` 提交**（pause/resume/advanceToNextLevel 皆如此），`post {}` 只是回 UI 线程的回调，不是进游戏线程的队列。`pendingPlayerTouch` 只保留最新一帧。
- 非游戏屏是普通 Compose + Material3：`gui/` Activity 全 `setContent`；新屏继承 `gui/BaseAircraftActivity`（先 `initializeViewModel`、仅 `if (!isFinishing)` 才 `initializeUI`），`MainActivity` 与 `MandatoryUpdateActivity` 是例外；工具屏用 `:developtools` 内同名同形拷贝。
- 全部进度写入走 `PlayerGameDataDao.replaceForPlayer()`（跨模式合并的 `@Transaction`），勿改回读-删-插；`finish()` 必须放在 suspend 保存**之后**（参考 `MainActivity`/`PuzzleActivity`），否则 Room 写被取消。Room 无 destructive fallback，缺 migration 直接崩。
- `GameStateManager` 当前只承载 LOW_MEMORY，其余完成流走 `GameCoreView` 直连回调。网络直接 OkHttp（Retrofit 依赖虽在但无人用），拼图 Bing 源用正则解析。
- `DebugTools.kt`（在 `:developtools` 内）是 debug/release 双源集（非运行时开关）：加调试屏要同时改两份 + `showDevelopSettings` 门 + 各 Activity 自查 `finish()`；debug 的 `isEnabled` 是 `var`、release 是 `val`，main 里赋值会在 release 编译失败。

## 易错约定

- 日志只用 `AppLog`（`android.util.Log` 仅允许在 `utils/AppLog.kt` 内，`:developtools` 内对应为 `DevLog`/`utils/DevLog.kt`）；lambda 消息用尾随形式，勿写成 `AppLog.d(tag, { … })`。
- 字符串 4 套 locale（`values/values-zh/values-zh-rTW/values-zh-rHK`）必须齐；`StringResourceTest` 还会 fail 未被引用的默认串（Firebase 白名单 8 个除外），删最后引用即破构建。
- Robolectric 屏测用 `createAndroidComposeRule` + `@GraphicsMode(NATIVE)`；长列表用高视口（`w420dp-h2000dp`，屏外点击静默无操作）；有转圈指示器的屏**禁 `waitForIdle()`**（会 60s hang）；碰 `AppLog`/`LogSettings`/`DevLog` 的测试固定 `@Config(sdk=[34], application=Application::class)`。
- 勿用 `!!`；游戏 sprite 保留 `bitmap.density = screenDensity`；QR 解码保留 ZXing 反色 fallback（生成码是亮底暗字）；分享走 `${applicationId}.fileprovider` + `FLAG_GRANT_READ_URI_PERMISSION`。
- `SupperBannerEffect` 11 个值，加 effect 要同步 enum、`SupperBannerTransformers`、DevelopSettings 映射、4 套 locale。`docs/` 下 `*.html`/`visual-check` 是生成物，只改 `.json`。archify 等图表技能的产物（`.json`/`.html`/`.visual-check.*`）一律存到 `docs/`，勿留仓库根目录。
