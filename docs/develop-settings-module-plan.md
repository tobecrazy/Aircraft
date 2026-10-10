# Develop 工具模块抽取计划（developToolModule / `:developtools`）

> 目标：`app` 只保留游戏相关代码，所有工具类屏幕抽到新模块 `:developtools`。
> package：`com.young.developtools`；依赖方向只允许 `app → developtools → (supperbanner, richtexteditor)`，**禁止模块反向依赖 app**。
> 状态：实施中（按 §8 步骤逐项落地，每步可独立编译验证）。

## 1. 范围：搬入 `:developtools` 的文件

### 1.1 工具屏（Activity，直属 `gui/` → 模块同名包）
| 源文件 | 备注 |
|---|---|
| `gui/DevelopSettingsActivity.kt` | 含 hub 内全部 `@Composable` 私有组件 |
| `gui/AndroidDevAssistantToolsActivity.kt` | 兄弟 hub |
| `gui/AppListDialog.kt` | 仅助手屏用 |
| `gui/ApiDebugToolActivity.kt` / `gui/ApiRequestHistoryActivity.kt` | |
| `gui/CameraScanActivity.kt` | |
| `gui/ContactsActivity.kt` | |
| `gui/PdfReaderActivity.kt` | |
| `gui/RichTextEditorActivity.kt` | 依赖 `:richtexteditor` + `:supperbanner`（Item 跳详情） |
| `gui/QRCodeToolActivity.kt` | 依赖 `:richtexteditor` |
| `gui/ShowImageDetailsActivity.kt` / `gui/BannerDetailsActivity.kt` | 镜像 ViewModel 仅迁被引用的；遗留 `BannerDetailsViewModel` 若无引用则留在 app 并标注 `@Deprecated`（避免搬死代码进新模块） |
| `gui/FlashlightActivity.kt` | |
| `gui/DeviceInfoActivity.kt` | 正常入口（Settings 可达），非 debug 专属，但属工具类，随迁 |

### 1.2 ViewModel（`viewmodel/` → 模块同名包）
`DevelopSettingsViewModel`、`LogSettingsViewModel`、`ApiDebugToolViewModel`（改写：见 §3.3）、
`CameraScanViewModel`、`ContactsViewModel`、`PdfViewModel`、`RichTextEditorViewModel`、
`QRCodeToolViewModel` + `QRCodeToolUiState`、`ShowImageDetailsViewModel`（改写：见 §3.3）、
`FlashlightViewModel`、`DeviceInfoViewModel`（`BuildConfig` 改写：version 经 `PackageManager` 取，`DEBUG` 用模块 `BuildConfig.DEBUG`，`APPLICATION_ID` 用 `context.packageName`）。

### 1.3 数据 / 仓库 / 纯工具
| 源文件 | 处理 |
|---|---|
| `data/ContactsRepository.kt`、`data/DeviceContact*`、联系人校验函数 | 整迁（零 app 依赖） |
| `data/LogSettings.kt` | 整迁，`AppLog` 引用改为模块 `DevLog`（§3.4） |
| `utils/apidebug/*` | 整迁（纯 Kotlin） |
| `utils/DataUriUtils.kt`、`utils/FilePickerHelper.kt` | 整迁；`FileProvider` authority 必须由 `BuildConfig.APPLICATION_ID` 改为 `context.packageName`（library 不能用 app applicationId） |
| `utils/DeveloperMode.kt`、`utils/DebugTools.kt`（debug/release 双源集） | 整迁到模块的 `src/main` + `src/debug` + `src/release`，包名同步改为模块包 |
| `repository/ApiDebugRepository.kt` | 改写：构造参数由 `ApiRequestHistoryDao`（Room，留 app）改为模块 `ApiHistoryStore` 接口（§3.3）；`AppLog` 改为 `DevLog` |
| `service/FlashlightService.kt` | 整迁（含 manifest `<service>` 条目）；`proguard` keep 随之搬到模块 consumer 规则 |

### 1.4 自给自足的基础设施拷贝（与 app 同名、不同包，游戏代码零改动）
`BaseAircraftActivity`、`ThemedMessage`、`ui/theme/AircraftTheme.kt` 全量拷贝、
`ui/WideScreen.kt`（`maxContentWidth`）拷贝、`dialogs/DialogCompose.kt` + `dialogs/ThemedAlertDialog.kt` 拷贝、
`dialogs/InfoDialogContent.kt` 整迁（仅助手屏用）。`GameDialogContent.kt` 留在 app（游戏结算用）。
模块主题拷贝中的 `SettingsRepository` 引用替换为模块 `DevPrefs`（同 prefs 文件、同 key，§3.2）。

### 1.5 资源 / assets / manifest
- 字符串：11 前缀共 328 条 + `invincible_mode_on/off`、`test_crash`、`test_rich_text_editor`、`title_activity_device_info`（仅工具侧引用，随迁），4 套 locale（`values/values-zh/values-zh-rTW/values-zh-rHK`）齐迁；
  被留守 app 代码引用的串**复制**一份到模块（合并时 app 优先，无冲突）：`app_name`、`history_back`、`history_cancel`；
  反向有 6 个串（`qr_code_tool_summary`、`develop_settings_summary`、`develop_settings_assistant_tools_button`、
  `develop_settings_unlocked/already_enabled/taps_remaining`）仅宿主（Settings/About）引用，模块零引用——已移回 app 独有，模块侧删除。
  另有 9 个导航串（`device_info_title/summary`、`qr_code_tool_title`、`flashlight_title/summary`、`develop_settings_title`、
  `device_info_fmt_version` 等）在 app 侧镜像保留（注释标记，双方同步改）。
  主题：模块自带 `values/themes.xml`（`DialogAnimation` 拷贝 + `QrToolBottomSheet` 双 style 搬迁 + `Theme.Aircraft.Common` 同值 fallback，
  合并时 app 同名覆盖，解决模块单测链资源链接问题）与 `values/colors.xml`（`header_back_icon` 同值 fallback）。
  模块 manifest 声明其代码路径所需的全部权限（INTERNET/ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE/CAMERA/FOREGROUND_SERVICE*/
  WAKE_LOCK/READ_CONTACTS/WRITE_CONTACTS/POST_NOTIFICATIONS），合并去重，同时修复模块 lint `MissingPermission`。
- drawable：`develop_settings_*`（5）、`device_info_*`（4）整迁；`qr_tool_*`、`cpu_progress_bar`、`ic_qr_save`、`ic_notification_flashlight` 等工具独占整迁；`ic_header_back` 等游戏共用的**复制**到模块。
- assets：`rich_text_default.html`、`example.json`（若在 `app/src/main/assets`）随迁。
- manifest：12 个工具 Activity + `FlashlightService` 声明迁入模块 `AndroidManifest.xml`（`exported=false` 不变，`Theme.Aircraft.Common` 引用保留，合并时由 app 侧提供）；`CAMERA/READ_CONTACTS/WRITE_CONTACTS/POST_NOTIFICATIONS` 权限两边同时声明（合并去重）；`<queries>` 留 app。

### 1.6 测试
随迁：`DevelopSettingsActivityTest`、`DevelopSettingsViewModelTest`、`AndroidDevAssistantToolsActivityTest`、
`ApiDebugToolActivityTest`、`ApiDebugToolViewModelTest`、`ApiDebugRepositoryTest`（改写为 fake store）、
`ContactsRepositoryTest`、`ContactsValidationTest`、`ContactsViewModelTest`、`DeviceInfoActivityTest`、
`QRCodeToolActivityTest`、`RichTextEditorActivityTest`、`RichTextMarkdownTest`、`ShowImageDetailsActivityTest`、
`ShowImageDetailsViewModelTest`、`FlashlightViewModelTest`、`LogSettingsTest`、`LogSettingsViewModelTest`、
`utils/apidebug/*Test`、`ThemedAlertDialogTest`（若覆盖被拷贝对话框）。另在模块新增资源 parity 测试（§6）。
`StringResourceTest` 留 app（路径只扫 app，搬走后自然收敛）。

## 2. 明确不搬（留在 app）及原因
`SettingsRepository`（游戏设置共用，模块用 `DevPrefs` 同文件读写代替）、`GameStateManager`（游戏引擎状态，模块经 `DevTools` 回调通知）、
`AppDatabase`/`DatabaseProvider`/全部 Room entity+dao（`ApiRequestHistory` 留 app，模块经 `ApiHistoryStore` 接口访问）、
`AircraftConstants`（仅拷贝 2 个 URL 常量进模块，不整迁）、`AppLog`（游戏热循环在用，模块用 `DevLog`）、
`MusicService`、`GameDialogContent`、游戏/设置/隐私/启动屏全套。

## 3. 解耦设计（模块零 app 引用，全经这 4 个机制）

### 3.1 `DevTools` 桥（模块 `com.young.developtools.DevTools`，app 在 `AircraftApplication.onCreate` 装配）
| Hook | app 侧实现 | 模块调用方 |
|---|---|---|
| `openActivityMonitor: ((Context) -> Unit)?` | 打开 `HistoryActivity` | 助手屏 `MODULE_ACTIVITY_MONITOR` 行；未装配则 toast 不可用 |
| `onInvincibleChanged: ((Boolean) -> Unit)?` | 写 `GameStateManager.isInvincible` | `DevelopSettingsActivity.setInvincibleMode` |
| `historyStoreProvider: ((Context) -> ApiHistoryStore)?` | 返回 `RoomApiHistoryStore`（app 新增，含 entity↔record 映射） | `ApiDebugToolViewModel.Factory`；未装配用内存 fallback，保证模块独立可跑 |
| `onLogEnabledChanged: ((Boolean) -> Unit)?` | 写 `AppLog.enabled` | `DevLog.enabled` setter 转发（默认不同步，装配后与全局开关一致） |

### 3.2 `DevPrefs`：同文件同 key 直读写
`PREFS_NAME="aircraft_prefs"` + `KEY_INVINCIBLE_MODE/KEY_DEVELOPER_OPTIONS_UNLOCKED/KEY_THEME` 及 `THEME_*` 常量在模块内复刻，
与 `SettingsRepository` 读写同一文件，零迁移、零数据丢失。代价是常量双份——以 app 侧为源，模块内注释标明。
`DeveloperMode` 逻辑原样搬入，仅把 `SettingsRepository` 调用换成 `DevPrefs`。

### 3.3 `ApiHistoryStore` 接口（模块定义，app 实现）
```kotlin
interface ApiHistoryStore {
    fun observeAll(): Flow<List<ApiHistoryRecord>>
    suspend fun getById(id: Long): ApiHistoryRecord?
    suspend fun insert(record: ApiHistoryRecord)
    suspend fun deleteById(id: Long)
    suspend fun clear()
}
```
`ApiDebugRepository` / `ApiDebugToolViewModel` / `ApiRequestHistoryActivity` 全改为面向该接口；
app 新增 `data/RoomApiHistoryStore.kt`（`ApiRequestHistory` ↔ `ApiHistoryRecord` 映射，Gson 复用）。
`ApiRequestHistoryActivity` 依赖 `EXTRA_HISTORY_ID` 常量随 Activity 整迁。

### 3.4 `DevLog`（模块日志门面，与 `AppLog` 同形：`enabled volatile + d/e/i/w/v`）
- `checkAppLogUsage` 只扫 `app/src/main`，模块内 `android.util.Log` 的唯一合法位置是 `DevLog.kt`；
  在模块 `build.gradle.kts` 新增同名镜像检查任务 `checkDevLogUsage` 并挂到模块 `lint*` 任务。
- lambda 日志保持尾随形式（`DevLog.d(tag) { … }`），沿用 `AppLog` 的 overload 约定，不新增 `tr` 尾参。

## 4. Gradle / manifest / 资源合并
- `settings.gradle.kts`：`include` 追加 `:developtools`。
- 新 `developtools/build.gradle.kts`：仿 `supperbanner`（`namespace=com.young.developtools`，`compileSdk 37/minSdk 31`，
  `consumerProguardFiles("consumer-rules.pro")`，JDK17，`unitTests.isIncludeAndroidResources=true`）＋
  `alias(libs.plugins.kotlin.compose)`（Compose 必需；**不加** `org.jetbrains.kotlin.android`）。
  依赖：`project(":supperbanner")`、`project(":richtexteditor")`、core-ktx、appcompat、material、
  activity-compose、lifecycle-viewmodel-ktx、lifecycle-runtime-compose、compose-bom（ui/foundation/material3/tooling-preview）、
  window（DeviceInfo 折叠屏）、cameraX 全套 + mlkit-barcode、coil-compose/gif/svg（ShowImage）、okhttp+gson（ApiDebug）、
  datastore-preferences（LogSettings）、junit/mockito/robolectric/test-core/test-junit（随迁测试）。
- `app/build.gradle.kts`：加 `implementation(project(":developtools"))`；其余不动（camera/mlkit/coil 等在 app 侧会成为冗余传递依赖，
  待全量验证通过后另起清理，不在本计划内）。
- `FAIL_ON_PROJECT_REPOS`：新仓库只允许加 `settings.gradle.kts`，模块内禁加仓库（沿用现有三模块做法）。
- 资源合并：模块与 app 同名资源以 app 为准，共享串/drawable 的复制策略与此兼容。

## 5. 混淆（R8）方案：不能出问题的关键点
1. **入口 keep**：AGP 会为 manifest 合并后的组件自动生成 keep，但仍在模块 `consumer-rules.pro` 显式声明，
   防止 AGP/R8 版本行为差异导致 release 首屏打不开工具：
   ```proguard
   -keep public class com.young.developtools.gui.*Activity { *; }
   -keep public class com.young.developtools.service.FlashlightService { *; }
   -keepclassmembers class com.young.developtools.utils.DebugTools { *; }
   -keepclassmembers class com.young.developtools.utils.DeveloperMode { *; }
   -keep class com.young.developtools.DevTools { *; }
   -keep interface com.young.developtools.ApiHistoryStore { *; }
   ```
   （app 侧 `proguard-rules.pro` 删除 `FlashlightService` 相关行注释改为指向模块，避免两边重复；`MusicService` 行保留。）
2. **不需要 enum `values/valueOf` keep**：`SupperBannerEffect.entries` 是编译期已知遍历，无字符串持久化/反序列化，
   与 `GameMode/GameDifficulty`（按名持久化）有本质区别——不加无用 keep。
3. **不需要 Gson keep**：模块内 Gson 只编解码 `Map<String,String>` / `Map<String,List<String>>` / `Any`，
   无自定义 POJO 反射；`ApiRequestHistory` entity 留在 app，沿用现有 Room/Gson 规则。
4. **不需要 `ViewModel.Factory` keep**：`create(Class<T>)` 是普通泛型 cast，无按名反射。
5. **无反射**：生产代码经核查无 `Class.forName` / `getDeclaredMethod`（仅测试代码有，不进 release）。
6. **Variant 匹配即安全开关**：app debug→lib debug（`DebugTools.isEnabled=true`），app release→lib release（`val=false`），
   默认 variant 匹配，无需 `publishNonDefault`。release 下 R8 会把 `DebugTools.isEnabled` 常量折叠，
   `DeveloperMode.isEnabled` 退化为纯 `DevPrefs` 解锁判断——**这正是期望行为**（release 靠 About 页 8 连击解锁），不是 bug。
   禁止在模块 `main` 源集中对 `isEnabled` 赋值（debug 是 `var`、release 是 `val`，赋值会在 release 编译失败——沿用现有红线）。
7. **资源收缩**：`shrinkResources` 只保留被引用资源；328 条字符串随迁后 app 侧 `StringResourceTest`（未引用即 fail）继续看护 app，
   模块侧新增同等测试看护模块（§6），防止删引用漏删串导致包体积回潮。
8. **验证**：`assembleRelease` 必须成功；拆包抽查 `mapping` 无 `developtools.gui` 类丢失；
   在 release APK 上走一遍 About 8 连击解锁 → DevelopSettings 打开 → 无敌开关/通知/Crash 确认框可达。

## 6. 测试策略
- 随迁测试包名随文件同步改 Imports；`ApiDebugRepositoryTest` 改用内存 fake store。
- 模块新增 `StringResourceTest`（模块版）：扫描 `developtools/src/main/res` 四套 locale parity +
  `developtools/src/main/{java,res,AndroidManifest.xml}` 引用覆盖（whitelist 为空，Firebase 串不在模块）。
- Robolectric 约束沿用：`createAndroidComposeRule` + `@GraphicsMode(NATIVE)`；长列表高视口；有转圈指示器禁 `waitForIdle()`；
  触及 `DevLog`/DataStore 的测试固定 `@Config(sdk=[34], application=Application::class)`。
- app 侧受影响测试：`SettingsActivityTest`（目标 Activity 包名变更）、`AboutAircraftActivityTest`、
  `ThemedMessageTest`（若引用被迁类则改 import），逐个跑通。

## 7. app 侧改动清单（只改这 7 处，其余游戏文件不动）
1. `SettingsActivity.navigateTo`：`DeviceInfo/QRCodeTool/Flashlight/DevelopSettings/AssistantTools` 的 import 改为模块包。
2. `SettingsViewModel`：`DebugTools` import 改为模块包。
3. `AboutAircraftActivity`：`DeveloperMode` import 改为模块包。
4. `LauncherIconManager`：`DebugTools` import 改为模块包。
5. `PrivacyPolicyActivity` / `PrivacyPolicyAcceptActivity`：`DebugTools.enableWebViewDebugging` import 改为模块包。
6. `AndroidManifest.xml`：删除 12 个 Activity + `FlashlightService` 声明（改由模块 manifest 合并进入）。
7. `AircraftApplication.onCreate`：装配 `DevTools` 4 个 hook（§3.1）；`proguard-rules.pro` 移除 `FlashlightService` keep（转模块）。

## 8. 实施步骤（已执行项打勾）
- [x] 分析 + 本计划文档
- [x] 模块脚手架（settings/`build.gradle.kts`/`consumer-rules.pro`/manifest/空 `DevTools`+`DevLog`+`DevPrefs`+`ApiHistoryStore`）
- [x] 基础设施拷贝（Base/ThemedMessage/Theme/WideScreen/2 dialogs + palette）+ `DebugTools` 双源集 + `DeveloperMode`（`isUnlockTap/remainingTaps` 由 internal 提为 public，供宿主 About 页调用）
- [x] 纯逻辑搬迁（apidebug/Contacts data/LogSettings/DataUri/FilePicker/FlashlightService/ApiDebugRepository 改写为 store 接口）
- [x] ViewModel + Activity 搬迁 + `DevTools` hook 接线（assistant History 行经 bridge；`DevPrefs` 补 `setTheme` 供主题监听/测试）
- [x] 资源（328 串×4 + drawable + assets + anim + style）搬迁 + app 侧删除/镜像 + 两边 `StringResourceTest` 跑通
- [x] app 侧 7 处改动 + manifest 清理 + `AircraftApplication` 装配 + 新增 `RoomApiHistoryStore`
- [x] 测试随迁（24 个文件 + 新增 `FlashlightTorchHeroTest` + 模块版 `StringResourceTest`；`SettingsActivityTest` 补 QR/Develop 两行导航用例）+ `checkDevLogUsage` + 全量验证（`assembleDebug lintDebug testDebugUnitTest` 双模块绿 + `assembleRelease` 成功 + mapping 确认 Activity/Service 类名保留 + 合并 manifest 含 13 Activity + 1 Service）

## 9. 风险与回滚
- 包体重复（theme/snackbar 对话框双份 ~200 行）：接受，计划后续抽 `:ui-kit` 时统一，届时模块与 app 同步删除拷贝。
- `THEME_*`/`PREFS` 常量双份漂移：模块内以注释标明源头，`SettingsRepository` 单测不受影响。
- 遗忘 manifest 条目导致合并后 Activity 缺失：以 §7 第 6 项为 checklist，CI `lintDebug` 会报缺失声明。
- 回滚：按 §8 倒序 `git revert` 即可，app 与模块的耦合点仅 `DevTools` 4 hook + 5 个 import，边界清晰。
