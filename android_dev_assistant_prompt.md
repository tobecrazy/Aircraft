# Android 开发者辅助工具 — 功能开发详细提示词

> **目标**：开发一款面向 Android 开发者的多功能辅助工具 App，覆盖反编译、布局分析、颜色取样、Activity 监控、Manifest 查看、应用信息管理、开发者选项快捷开关、系统信息查看等核心模块。
>
> **技术栈建议**：Android (Kotlin / Java)，minSdk ≥ 21，targetSdk 跟进最新稳定版，支持无障碍服务（AccessibilityService）。

---

## 一、整体架构要求

### 1.1 模块化设计
- 每个功能模块独立成一个 Feature Module（或独立 Activity/Fragment），保证低耦合、高内聚。
- 公共能力（权限管理、文件 IO、悬浮窗管理、无障碍服务通信）抽取到 `core` 模块。
- 使用 MVVM + Repository 架构，ViewModel 持有 UI 状态，Repository 负责数据获取与缓存。

### 1.2 权限管理
- 运行时动态申请：`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`（API < 29）、`MANAGE_EXTERNAL_STORAGE`（API ≥ 30）。
- 悬浮窗权限：`SYSTEM_ALERT_WINDOW`，使用 `Settings.canDrawOverlays()` 检测，未授权时引导用户到系统设置页。
- 无障碍服务权限：继承 `AccessibilityService`，在 `AndroidManifest.xml` 注册，未授权时弹出说明对话框并跳转 `Settings.ACTION_ACCESSIBILITY_SETTINGS`。
- 所有权限申请需提供清晰的「为什么需要此权限」说明文案，满足 Google Play 政策。

### 1.3 悬浮窗机制
- 封装统一的 `FloatWindowManager`，支持拖拽定位、最小化为悬浮球、跨 Activity 保持状态。
- 悬浮窗内容用 `ComposeView` 或传统 `View` 渲染，支持透明度调节。

### 1.4 快捷方式与 Widget
- `ShortcutManager`（API ≥ 25）动态注册每个工具的 Pinned Shortcut，长按图标弹出最多 4 个常用工具。
- Android 7.1+ App Shortcut：`shortcuts.xml` 静态定义 + 动态更新最近使用记录（最多 4 条）。
- AppWidgetProvider 实现桌面 Widget，展示工具快速入口，支持 1×1 / 2×2 尺寸。
- Android 7.0+ Quick Settings Tile：继承 `TileService`，在通知栏快速打开 App。

---

## 二、功能模块详细描述

---

### 模块 1：反编译查看器（Decompile Viewer）

#### 功能目标
无需 PC 端工具，直接在 Android 设备上查看任意已安装 App 的 Java 源码、资源文件及其他文件内容。

#### 技术实现

**1. APK 获取**
- 通过 `PackageManager.getApplicationInfo()` 获取目标 App 的 `sourceDir`（APK 路径）及 `splitSourceDirs`（分包路径）。
- 复制 APK 到应用私有目录（`context.cacheDir`）后再处理，避免权限问题。

**2. APK 解析**
- 集成 `jadx-core` 库（或同等 Java 反编译库）实现 `.dex` → Java 伪代码转换。
- 使用 `ZipFile` API 解析 APK 内部结构，列出所有条目（classes.dex、res/、assets/、lib/ 等）。
- 解析 `resources.arsc` 以还原资源 ID 与资源值的映射关系。
- 解析 `AndroidManifest.xml`（二进制 XML）使用 `AXMLPrinter` 或 `apktool` 相关库转换为可读文本。

**3. 文件树展示**
- 使用 `RecyclerView` + 树形展开数据结构（`TreeNode<T>`）展示 APK 文件目录树。
- 支持按类型过滤（Java/Kotlin 类、XML、图片、so 库、其他）。
- 支持关键词搜索（文件名 + 代码内容全文搜索）。

**4. 代码查看**
- 集成语法高亮库（如 `Highlight.js` 通过 WebView，或 `CodeView` 原生库）实现 Java、XML、JSON、SMALI 等多语言高亮。
- 支持字体大小调节、行号显示、代码折叠（方法级）。
- 长文件分页加载，避免 OOM。

**5. 文件分享**
- 通过 `FileProvider` 生成 URI，调用系统分享 Intent（`ACTION_SEND`）支持分享到微信、钉钉、QQ 等。
- 支持将反编译结果导出为 ZIP 包保存到用户指定目录（SAF `DocumentsContract` API）。

#### 边界条件
- 针对加固（Dex 加固、VMP、抽取壳等）的 APK，提示用户「该 App 已加固，反编译结果可能不完整」，仍展示 Smali 代码。
- 大型 APK（> 100MB）使用后台线程（`WorkManager` 或 `CoroutineScope(Dispatchers.IO)`）异步解析，前台展示进度条。

---

### 模块 2：布局查看器（Layout Inspector）

#### 功能目标
实时查看任意正在运行的 App 的 UI 布局层级，获取控件详细信息，支持导出布局文件。

#### 技术实现

**1. 布局树获取**
- 通过 `AccessibilityService` 的 `rootInActiveWindow` 获取当前窗口的 `AccessibilityNodeInfo` 树。
- 递归遍历节点树，提取以下属性：
  - `className`（控件类型，如 `TextView`、`RecyclerView`）
  - `viewIdResourceName`（View ID，如 `com.example:id/btn_submit`）
  - `getBoundsInScreen(Rect)`（屏幕坐标及宽高）
  - `getBoundsInParent(Rect)`（父容器内坐标）
  - `contentDescription`、`text`、`isClickable`、`isEnabled`、`isVisibleToUser`
  - 父子节点关系（深度、索引）

**2. 悬浮层叠加**
- 在目标 App 上方绘制透明悬浮窗（`TYPE_ACCESSIBILITY_OVERLAY`），使用 Canvas 绘制控件边界框（不同层级用不同颜色区分）。
- 点击悬浮层某区域时，计算落点坐标，找出所有包含该坐标的节点（支持多层叠加选择）。
- 当同一位置有多个 View 时，弹出列表让用户选择目标 View。

**3. 控件详情面板**
- 选中控件后，侧边或底部滑出详情面板，展示：
  - View ID（含 R.id 名称）
  - 控件类型及完整类名
  - 屏幕坐标（left, top, right, bottom）
  - 尺寸（宽 × 高，px 和 dp 双显）
  - 父控件 / 子控件列表（可点击跳转）
  - 文字内容、可点击性、可见性等状态属性

**4. 布局文件导出**
- 将 `AccessibilityNodeInfo` 树转换为标准 Android XML 布局格式（近似还原，使用 `ConstraintLayout` 或 `LinearLayout` 包装）。
- 保存为 `.xml` 文件，支持通过 SAF 或 `FileProvider` 分享。

**5. 小窗模式**
- 布局查看器支持以悬浮小窗形式常驻，不遮挡目标 App 主要内容区域，可拖拽定位。

#### 注意事项
- 部分系统应用或使用 `FLAG_SECURE` 的 App，`AccessibilityNodeInfo` 可能返回空，需提示用户。
- Android 11+ 对 `TYPE_ACCESSIBILITY_OVERLAY` 的窗口有限制，需适配 `WindowManager.LayoutParams` 相关参数。

---

### 模块 3：屏幕取色器（Color Picker）

#### 功能目标
类似专业取色器工具，可在任意 App 界面实时取色，获取精确色值并支持复制。

#### 技术实现

**1. 屏幕截图获取**
- 使用 `MediaProjection` API（需用户授权）持续捕获屏幕帧（`ImageReader` + `VirtualDisplay`），以 `Bitmap` 形式缓存最新帧。
- 在悬浮窗上展示一个可拖拽的「取色准星」（十字光标），配合放大镜预览（以准星为中心放大 10×10 像素区域）。

**2. 颜色计算**
- 获取准星中心像素的 ARGB 值（`Bitmap.getPixel(x, y)`）。
- 实时转换并显示以下格式：
  - **HEX**（#AARRGGBB 或 #RRGGBB）
  - **ARGB**（A: 0~255, R: 0~255, G: 0~255, B: 0~255）
  - **CMYK**（C, M, Y, K 百分比，按标准公式计算）
  - **HSV / HSL**（色相 Hue、饱和度 Saturation、明度 Value/Lightness）

**3. 坐标信息**
- 显示准星当前在屏幕上的绝对坐标（px）及相对屏幕尺寸的百分比坐标。

**4. 颜色复制 & 历史**
- 点击确认按钮，将当前色值（可选格式）复制到剪贴板。
- 维护最近取色历史（最多 20 条），以色块列表展示，支持再次复制或分享。

**5. 放大镜实现细节**
- 放大镜视图为圆形，半径约 60dp，内部展示以取色点为中心的 11×11 像素区域放大效果（每个像素渲染为约 10dp 正方形格子），中心格子用白色/黑色边框标记当前取色像素。

---

### 模块 4：每日开源项目（Daily Open Source）

#### 功能目标
每日推送精选 Android 开源项目，帮助开发者保持技术视野。

#### 技术实现

**1. 数据源**
- 对接 GitHub Trending API（`https://api.github.com/search/repositories?q=topic:android&sort=stars&order=desc`）或自建精选数据源接口（推荐后端每日人工/自动筛选后发布）。
- 本地缓存今日数据（`Room` 数据库），离线可查看。

**2. 展示**
- 卡片式列表，每个卡片展示：项目名称、作者、简介（中文翻译，可调用翻译 API）、Star 数、今日新增 Star、语言标签、License。
- 支持按语言、Star 数过滤；支持收藏/标记已读。

**3. 推送**
- 使用 `WorkManager` 每日定时（如早上 9 点）后台拉取最新数据，通过 `NotificationCompat` 推送一条摘要通知。

**4. 详情页**
- 点击项目跳转内置 WebView 或 Chrome Custom Tab 查看 GitHub 项目主页。
- 支持查看 README（Markdown 渲染，使用 `Markwon` 库）。

---

### 模块 5：Activity 监控（Activity Monitor）

#### 功能目标
实时显示当前栈顶 Activity 信息，支持历史记录查看，方便开发者调试页面流转。

#### 技术实现

**1. 栈顶 Activity 获取**
- **方案一（推荐）**：通过 `AccessibilityService` 监听 `TYPE_WINDOW_STATE_CHANGED` 事件，`event.packageName` + `event.className` 即为当前 Activity。
- **方案二（备选，需 Root）**：执行 `adb shell dumpsys activity top` 解析输出。
- 记录每次 Activity 切换的时间戳，计算停留时长。

**2. 信息展示**
- **包名**（packageName）
- **类名**（完整 Activity 类名）
- **打开时间**（时间戳，格式 HH:mm:ss.SSS）
- **停留时长**（离开时计算）
- **App 名称 + 图标**（通过 `PackageManager` 获取）

**3. 小窗模式**
- 悬浮小窗展示当前 Activity 信息，支持展开/收起，可设置透明度与停靠位置。
- 小窗拖拽后记住位置（`SharedPreferences` 存储）。

**4. 历史记录**
- 本地存储最近 200 条 Activity 切换记录（`Room` 数据库）。
- 列表展示，支持按包名过滤、关键词搜索、导出为 CSV 或 TXT。

---

### 模块 6：Manifest 查看器（Manifest Viewer）

#### 功能目标
查看任意已安装 App 的 `AndroidManifest.xml` 内容，支持搜索与导出。

#### 技术实现

**1. Manifest 获取与解析**
- 从 APK `ZipFile` 中读取二进制 `AndroidManifest.xml`。
- 使用 `AXMLParser`（Android Binary XML Parser）或集成 `apktool` 相关解码模块，将二进制 XML 转为人类可读文本。

**2. 展示**
- XML 语法高亮展示（标签、属性、属性值分色）。
- 支持折叠/展开各大节点（`<application>`、`<activity>`、`<service>`、`<receiver>`、`<provider>`、`<permission>` 等）。
- 侧边快速跳转导航栏（列出所有一级子节点）。

**3. 搜索**
- 全文关键词搜索，高亮匹配结果，支持上一个/下一个导航。

**4. 导出**
- 支持保存为 `.txt` 或 `.html` 文件（HTML 版本包含语法高亮样式）到用户选择的目录（SAF API）。
- 支持通过分享 Intent 发送给其他 App。

---

### 模块 7：应用信息管理（App Info Manager）

#### 功能目标
展示设备上所有已安装 App 的详细信息，支持多维度浏览与分析。

#### 技术实现

**1. 应用列表**
- 通过 `PackageManager.getInstalledPackages(PackageManager.GET_META_DATA | PackageManager.GET_ACTIVITIES | ...)` 获取全量 App 列表。
- 分类展示：**全部 App**、**最近使用**（读取 `UsageStatsManager`，需 `PACKAGE_USAGE_STATS` 权限）、**最近安装**（按 `firstInstallTime` 排序）。
- 支持 Grid 模式（图标为主）和 List 模式（信息为主）切换。
- 支持按名称、包名、安装时间、更新时间、大小排序；支持关键词过滤。

**2. 详情信息**
点击某 App 展示详情页，包含：
- App 名称、图标、包名
- **版本号**（versionName + versionCode）
- **UID**（`applicationInfo.uid`）
- **APK 路径**（`sourceDir`，多 APK 展示 `splitSourceDirs`）
- **So 库目录**（`nativeLibraryDir`）
- **数据目录**（`dataDir`）
- **首次安装时间 / 最近更新时间**
- **启动 Activity**（从 `PackageManager.getLaunchIntentForPackage` 获取 Launcher Activity 类名）
- **加固检测**：检测常见加固特征（如 `libjiagu.so`、`libsecexe.so` 等 so 文件名，或特定 Dex 类名），显示加固厂商名称（梆梆、360、腾讯乐固、网易易盾等）。
- **组件信息**：列出所有 Activity、Service、BroadcastReceiver、ContentProvider，标注是否 exported。
- **权限信息**：已声明权限、已授予的危险权限。
- **签名信息**：证书 MD5 / SHA1 / SHA256。

**3. 快捷操作**
- 启动 App、卸载 App（跳转系统卸载页）、打开系统 App 信息页、提取 APK（跳转模块 8）。

---

### 模块 8：APK & So 文件提取（File Extractor）

#### 功能目标
一键提取任意 App 的 APK 文件和 So 动态库文件，支持保存与分享。

#### 技术实现

**1. APK 提取**
- 读取 `applicationInfo.sourceDir`（主 APK）和 `splitSourceDirs`（分包 APK，如 Android App Bundle 分包场景）。
- 将 APK 文件复制到用户可访问目录（`Download/DevAssist/apk/`），文件命名规则：`{appName}_{versionName}.apk`。
- 批量提取多个 App 时，使用协程并行处理 + 进度通知。

**2. So 文件提取**
- 读取 `applicationInfo.nativeLibraryDir`，列出所有 `.so` 文件。
- 支持选择单个或全部 So 文件导出，打包为 ZIP（`java.util.zip`）。
- 展示每个 So 的文件大小、ABI 架构（armeabi-v7a、arm64-v8a、x86 等，从目录路径解析）。

**3. 分享**
- 通过 `FileProvider` + `ACTION_SEND` 或 `ACTION_SEND_MULTIPLE` Intent 分享提取的文件。
- 对超大文件（> 50MB），提示用户直接在文件管理器中找到并手动分享。

---

### 模块 9：开发者选项快捷开关（Dev Options Quick Toggle）

#### 功能目标
将繁琐的「设置 → 开发者选项」多步操作简化为单次点击，快速开关常用开发调试选项。

#### 技术实现

**1. 实现原理**
- 通过 `AccessibilityService` 实现自动化 UI 操作（类似 UI Automator 逻辑）。
- 当用户点击某个快捷开关时，App 在后台自动打开系统开发者选项页面，找到对应 Toggle 控件并点击，操作完成后自动返回。
- 整个流程对用户透明，仅展示一个操作结果提示（`Snackbar` 或悬浮 Toast）。

**2. 支持的开关列表**

| 开关名称 | 系统设置项 Key（参考）|
|---|---|
| 显示布局边界 | `debug_layout` |
| 显示 GPU 过度绘制 | `show_overdraw_areas` |
| 显示布局更新（闪烁） | `show_hw_screen_updates` |
| 强制 GPU 渲染 | `force_hw_ui` |
| 显示 GPU 视图更新 | `show_gpu_view_updates` |
| GPU 渲染模式分析（柱状图）| `profile_gpu_rendering` |
| 显示指针位置 | `pointer_location` |
| 严格模式 | `strict_mode_enabled` |
| 不保留活动 | `always_finish_activities` |
| 不锁定屏幕 | `stay_on_while_plugged_in` |
| 正在运行的服务 | 跳转 `APP_OPS_SUMMARY` 或 `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` |
| 系统界面调节器 | 通过特定 Intent 启动 |

**3. 状态同步**
- 每次进入模块时读取当前各选项实际状态（`Settings.Global.getInt` 或解析 `dumpsys`），与 UI 开关状态同步。
- 操作后延迟 500ms 重新读取状态，确认操作生效。

**4. 说明文案**
- 每个开关附带简短的「功能说明」和「适用场景」（如过度绘制用于检测 UI 层叠渲染问题）。

---

### 模块 10：系统信息查看（System Info）

#### 功能目标
快速获取当前设备的全维度系统信息，方便调试和问题定位。

#### 技术实现

分组展示以下信息（使用 `TabLayout` + `ViewPager2` 或 `ExpandableList` 分类）：

**版本信息**
- Android 版本（`Build.VERSION.RELEASE`）、API Level（`Build.VERSION.SDK_INT`）
- 安全补丁日期（`Build.VERSION.SECURITY_PATCH`）
- Build 号（`Build.DISPLAY`）、Fingerprint（`Build.FINGERPRINT`）

**硬件信息**
- 设备品牌（`Build.BRAND`）、型号（`Build.MODEL`）、硬件（`Build.HARDWARE`）
- SOC 型号（`Build.SOC_MODEL`，API ≥ 31；低版本解析 `/proc/cpuinfo`）
- RAM 总量（`ActivityManager.MemoryInfo.totalMem`）、可用 RAM
- 存储总量 / 可用量（`StatFs`，区分内置存储和 SD 卡）

**屏幕信息**
- 分辨率（px）、密度（dpi）、密度类型（mdpi / hdpi / xxhdpi 等）
- 屏幕尺寸（英寸，计算值）、宽高比、刷新率（`Display.getSupportedRefreshRates()`）
- 屏幕方向、缺口 / 刘海信息（API ≥ 28 `DisplayCutout`）

**CPU 信息**
- 核心数（`Runtime.getRuntime().availableProcessors()`）
- CPU 架构（ABI 列表：`Build.SUPPORTED_ABIS`）
- CPU 频率（解析 `/sys/devices/system/cpu/cpu0/cpufreq/`）

**虚拟机信息**
- VM 类型（ART）、堆大小限制（`ActivityManager.getLargeMemoryClass()`）
- JIT 编译开关状态

**网络信息**
- IP 地址（WiFi / 移动网络，IPv4 + IPv6）
- MAC 地址（注：Android 10+ 随机 MAC，展示说明）
- 网络类型（WiFi SSID、移动网络运营商、网络制式 4G/5G 等）
- DNS 服务器地址

**设备 ID 信息**
- Android ID（`Settings.Secure.ANDROID_ID`）
- IMEI（API < 29 可读；≥ 29 需特殊权限，展示说明）
- 广告 ID（`AdvertisingIdClient.getAdvertisingIdInfo()`，异步获取）
- Build Serial（`Build.getSerial()`，需 `READ_PHONE_STATE` 权限）

> 所有信息支持「一键复制」单条值和「导出全部」为 TXT 文件。

---

### 模块 11：常用设置快速入口（Quick Settings Entry）

#### 功能目标
提供系统常用设置页面的快速跳转入口，减少多级菜单导航。

#### 实现

用卡片/列表展示以下跳转入口，点击即通过对应 `Intent` 跳转：

| 入口名称 | Intent Action |
|---|---|
| 设置主页 | `Settings.ACTION_SETTINGS` |
| 语言与输入法 | `Settings.ACTION_LOCALE_SETTINGS` |
| 系统界面调节器 | 特定 Action（厂商可能不同，做兼容处理）|
| 开发者选项 | `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` |
| 我的应用（应用管理）| `Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS` |
| 无障碍设置 | `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| 通知设置 | `Settings.ACTION_APP_NOTIFICATION_SETTINGS` |
| 电池优化 | `Settings.ACTION_BATTERY_SAVER_SETTINGS` |
| WLAN 设置 | `Settings.ACTION_WIFI_SETTINGS` |
| 蓝牙设置 | `Settings.ACTION_BLUETOOTH_SETTINGS` |

---

### 模块 12：Android 招聘信息（Job Board）

#### 功能目标
聚合展示国内主流互联网公司的 Android 开发岗位招聘信息，方便开发者关注职场动态。

#### 技术实现

**1. 数据来源**
- 对接 Boss 直聘 / 拉勾网等平台开放接口（或通过自建后端抓取并结构化存储）。
- 数据字段：公司名称、岗位名称、薪资范围、工作地点、学历要求、工作年限、公司规模、发布时间、职位链接。

**2. 展示**
- 支持按城市、薪资范围、公司规模筛选。
- 支持收藏岗位，离线可查看已收藏。
- 点击岗位跳转 Chrome Custom Tab 查看详情。

**3. 目标公司范围（初期）**
字节跳动、腾讯、阿里、百度、美团、京东、快手、小红书、滴滴、哔哩哔哩、网易、携程、58同城、OPPO、vivo、小米、华为等。

---

## 三、通用 UI/UX 规范

### 3.1 主题与设计
- 支持 **Material You 动态主题**（Android 12+，`DynamicColors.applyToActivitiesIfAvailable`）及自定义主题色（深色/浅色模式）。
- 主色调：深邃蓝（#1565C0）或绿色科技感（#00897B），可在设置中修改。
- 所有界面遵循 Material Design 3 规范：圆角卡片、适当 Elevation、规范间距（8dp 基准网格）。

### 3.2 主页导航
- 底部导航栏（最多 5 个 Tab）+ 侧边抽屉，主功能模块在首页以卡片 Grid 展示。
- 支持自定义首页模块排序（长按拖拽）和隐藏不常用模块。
- 搜索功能：全局搜索所有模块（模块名称 + 内部关键词）。

### 3.3 性能要求
- 首页冷启动时间 < 1.5 秒（中端设备）。
- 列表滚动帧率保持 60fps（启用 `RecyclerView` 预取、`DiffUtil`、ViewHolder 复用）。
- 所有磁盘 IO / 网络操作必须在子线程（`Dispatchers.IO`）执行，禁止在主线程阻塞。
- 大图片通过 `Glide` / `Coil` 异步加载并压缩，避免 OOM。

### 3.4 兼容性
- minSdk 21（Android 5.0），对 API 23 / 26 / 29 / 30 / 31 / 33 关键变更做分支适配。
- 适配不同屏幕尺寸：手机、折叠屏（`WindowSizeClass`）、平板（双栏布局）。
- 针对国内主流厂商（MIUI、ColorOS、Funtouch OS、EMUI/HarmonyOS）做兼容测试，特别是无障碍服务白名单和后台保活策略。

---

## 四、安全与隐私要求

1. **无网络数据上传**：所有用户数据（提取的 APK、截图、颜色历史、Activity 记录等）仅存储在本地，不上传任何服务器。
2. **权限最小化原则**：仅申请实际需要的权限，每个权限在 `AndroidManifest.xml` 中附加 `android:maxSdkVersion` 限制（如适用）。
3. **无障碍服务声明**：在首次启动时通过专门的「权限说明」界面向用户完整说明无障碍服务的使用目的（仅用于自动化开发者选项操作和获取 Activity 类名），承诺不收集任何用户信息。
4. **加密存储**：涉及敏感信息（如设备 ID）的本地缓存使用 `EncryptedSharedPreferences`（Jetpack Security）存储。
5. **ProGuard / R8 混淆**：Release 包开启代码混淆，防止自身被反编译后泄露业务逻辑。

---

## 五、测试要求

| 测试类型 | 工具 / 框架 | 覆盖目标 |
|---|---|---|
| 单元测试 | JUnit5 + MockK | Repository、ViewModel、工具类逻辑 ≥ 70% 覆盖率 |
| UI 测试 | Espresso + UI Automator | 各模块主流程冒烟测试 |
| 兼容性测试 | Firebase Test Lab | Android 7.0 ~ 14，主流机型 |
| 性能测试 | Android Studio Profiler | 内存泄漏检测、帧率分析、APK 解析耗时 |
| 权限测试 | 手动 | 每个权限拒绝后 App 表现正常，不崩溃 |

---

## 六、发布要求

- **包名**：`com.yourcompany.devassist`（根据实际情况替换）
- **签名**：使用独立 Release 签名证书，keystore 不提交 Git
- **分发**：Google Play（需确认功能合规性）+ 国内应用市场（华为、小米、OPPO、vivo 等）
- **版本策略**：语义化版本号 `MAJOR.MINOR.PATCH`，Changelog 同步维护
- **崩溃监控**：集成 Firebase Crashlytics 或 Bugly，线上崩溃率目标 < 0.1%

---

*本提示词文档版本：v1.0 | 最后更新：2025 年*
