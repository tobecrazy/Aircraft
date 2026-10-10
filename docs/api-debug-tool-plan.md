# API Debug Tool Implementation Plan

## Overview
实现一个 API 调试工具页面，允许开发者测试和调试 HTTP API 请求。该工具将集成到 DevelopSettingsActivity 的 Internal Tool Section 中。

## 功能需求

### 1. 入口点
- **位置**: DevelopSettingsActivity 的 Internal Tool Section
- **位置**: QR Scan 按钮下方
- **按钮文本**: "API Tool" / "API 工具"
- **操作**: 点击后导航到新的 ApiDebugToolActivity

### 2. API Debug Tool 页面组件

#### 2.1 页面标题
- **标题**: "API Debug Tool" / "API 调试工具"
- **样式**: 与 DevelopSettingsActivity 一致的 header 设计
- **功能**: 返回按钮

#### 2.2 输入字段

##### API Host 输入框
- **标签**: "API Host" / "API 地址"
- **类型**: OutlinedTextField
- **占位符**: "https://example.com/api/endpoint"
- **验证**: 基本 URL 格式验证
- **默认值**: 空或上次使用的值

##### HTTP Method 下拉列表
- **标签**: "HTTP Method" / "请求方法"
- **选项**: GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE, CONNECT
- **默认值**: GET
- **实现**: DropdownMenu (与 EffectDropdown 类似)

##### Request Body 输入框
- **标签**: "Request Body" / "请求体"
- **类型**: OutlinedTextField (multiline)
- **高度**: 至少 120dp
- **占位符**: "{ \"key\": \"value\" }"
- **验证**: JSON 格式验证（可选）
- **可见性**: 仅当方法支持 body 时显示（POST, PUT, PATCH）

##### Headers 输入区域
- **标签**: "Headers" / "请求头"
- **实现**: 动态 Key-Value 对列表
- **功能**:
  - 添加新 header 按钮
  - 删除现有 header 按钮
  - 每个 header 包含: Key 输入框 + Value 输入框
- **预设 headers**: 
  - Content-Type: application/json (可编辑)
  - Accept: application/json (可编辑)

#### 2.3 操作按钮

##### Send Button
- **文本**: "Send Request" / "发送请求"
- **样式**: 与 ToolButton 一致
- **状态**: 
  - 请求中: 显示加载指示器，禁用按钮
  - 空闲: 正常状态

##### Clear Button
- **文本**: "Clear" / "清除"
- **样式**: OutlinedButton
- **功能**: 清空所有输入字段和响应

#### 2.4 响应显示区域

##### Response Header
- **标签**: "Response" / "响应结果"
- **显示信息**:
  - HTTP 状态码和状态文本
  - 响应时间（毫秒）
  - 响应大小（字节）

##### Response Body
- **类型**: 可滚动的文本区域
- **高度**: 至少 200dp，最大占据剩余空间
- **格式化**: 
  - JSON 自动格式化和语法高亮（如果是 JSON）
  - 纯文本显示（其他类型）
- **字体**: Monospace
- **背景**: 深色面板，与主题一致

##### Response Headers 展开区域（可选）
- **实现**: 可展开/折叠的面板
- **内容**: 显示所有响应头的 Key-Value 对

## 技术实现

### 3.1 网络层
- **选择**: OkHttp（已存在于项目中）
- **原因**: 
  - 项目已经使用 OkHttp
  - 更灵活，适合调试工具
  - 无需额外依赖

### 3.2 架构
- **Activity**: `ApiDebugToolActivity` extends `BaseAircraftActivity`
- **ViewModel**: `ApiDebugToolViewModel`
  - 管理 API 请求状态
  - 保存/恢复输入字段
  - 处理请求逻辑
- **Repository** (可选): 如果需要持久化请求历史

### 3.3 状态管理
```kotlin
data class ApiDebugState(
    val host: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val requestBody: String = "",
    val headers: List<HeaderPair> = defaultHeaders(),
    val isLoading: Boolean = false,
    val response: ApiResponse? = null,
    val error: String? = null
)

data class HeaderPair(
    val key: String = "",
    val value: String = ""
)

data class ApiResponse(
    val statusCode: Int,
    val statusMessage: String,
    val responseTime: Long,
    val responseSize: Long,
    val body: String,
    val headers: Map<String, List<String>>
)

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE, CONNECT
}
```

### 3.4 OkHttp 实现
```kotlin
class ApiDebugRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun executeRequest(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        body: String?
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        // 实现请求逻辑
    }
}
```

### 3.5 JSON 格式化
- 使用 Gson 或 Kotlin Serialization（如果已存在）
- 实现 pretty print
- 错误处理：如果不是 JSON，显示原始文本

## UI/UX 设计

### 4.1 主题一致性
- 使用现有的 AircraftTheme
- 颜色：
  - 背景: `BackgroundDark`
  - 面板: `PanelBg` + `PanelBorder`
  - Header: `HeaderBackground`
  - 强调色: `AccentGreen`
  - 文本: `TextBright`, `TextBody`, `TextSubtle`

### 4.2 布局结构
```
┌─────────────────────────────┐
│ Header (返回 + 标题)         │
├─────────────────────────────┤
│ Scrollable Content:         │
│  ┌──────────────────────┐   │
│  │ API Host Input       │   │
│  └──────────────────────┘   │
│  ┌──────────────────────┐   │
│  │ Method Dropdown      │   │
│  └──────────────────────┘   │
│  ┌──────────────────────┐   │
│  │ Request Body         │   │
│  │ (multiline)          │   │
│  └──────────────────────┘   │
│  ┌──────────────────────┐   │
│  │ Headers (dynamic)    │   │
│  │  Key | Value | [X]   │   │
│  │  [+ Add Header]      │   │
│  └──────────────────────┘   │
│  ┌──────────────────────┐   │
│  │ [Send] [Clear]       │   │
│  └──────────────────────┘   │
│  ┌──────────────────────┐   │
│  │ Response Panel       │   │
│  │  Status: 200 OK      │   │
│  │  Time: 123ms         │   │
│  │  ┌────────────────┐  │   │
│  │  │ Response Body  │  │   │
│  │  │ (scrollable)   │  │   │
│  │  └────────────────┘  │   │
│  └──────────────────────┘   │
└─────────────────────────────┘
```

### 4.3 响应式设计
- 使用 `Modifier.widthIn(max = 640.dp)` 限制最大宽度
- 支持滚动以适应小屏幕
- 响应区域自动扩展以显示所有内容

## 国际化

### 5.1 新增字符串
需要在 4 套 locale 中添加：
- `values/strings.xml` (English)
- `values-zh/strings.xml` (Simplified Chinese)
- `values-zh-rTW/strings.xml` (Traditional Chinese - Taiwan)
- `values-zh-rHK/strings.xml` (Traditional Chinese - Hong Kong)

### 5.2 字符串列表
- `api_debug_tool_title`: "API Debug Tool" / "API 调试工具"
- `api_debug_tool_button`: "API Tool" / "API 工具"
- `api_debug_host_label`: "API Host" / "API 地址"
- `api_debug_host_hint`: "https://example.com/api"
- `api_debug_method_label`: "HTTP Method" / "请求方法"
- `api_debug_request_body_label`: "Request Body" / "请求体"
- `api_debug_request_body_hint`: "{\"key\": \"value\"}"
- `api_debug_headers_label`: "Headers" / "请求头"
- `api_debug_add_header`: "Add Header" / "添加请求头"
- `api_debug_send_button`: "Send Request" / "发送请求"
- `api_debug_clear_button`: "Clear" / "清除"
- `api_debug_response_label`: "Response" / "响应结果"
- `api_debug_status_format`: "Status: %d %s"
- `api_debug_time_format`: "Time: %dms"
- `api_debug_size_format`: "Size: %s"
- `api_debug_no_response`: "No response yet" / "暂无响应"
- `api_debug_error_invalid_url`: "Invalid URL" / "无效的 URL"
- `api_debug_error_network`: "Network error: %s" / "网络错误: %s"
- HTTP 方法标签 (GET, POST, etc.)

## 测试计划

### 6.1 单元测试
- `ApiDebugViewModelTest`
  - 测试状态管理
  - 测试输入验证
  - 测试请求构建

### 6.2 UI 测试
- `ApiDebugToolActivityTest`
  - 测试导航
  - 测试输入字段
  - 测试按钮操作

### 6.3 集成测试
- 测试实际 API 请求（使用 MockWebServer）
- 测试错误处理
- 测试响应解析

## 实现步骤

1. **创建字符串资源** (4 套 locale)
2. **创建数据类和 ViewModel**
   - `ApiDebugState`
   - `ApiDebugToolViewModel`
   - `ApiDebugRepository`
3. **实现 Activity**
   - `ApiDebugToolActivity`
   - Compose UI 组件
4. **更新 DevelopSettingsActivity**
   - 添加 "API Tool" 按钮
   - 添加导航逻辑
5. **测试**
   - 单元测试
   - UI 测试
   - 手动测试
6. **文档更新**
   - 更新 AGENTS.md（如果需要）
   - 添加使用说明

## 潜在问题和解决方案

### 问题 1: JSON 格式化性能
- **解决**: 限制响应大小，超过限制时显示原始文本

### 问题 2: 网络权限
- **解决**: 确保 AndroidManifest.xml 中有 INTERNET 权限（应该已存在）

### 问题 3: 证书验证错误
- **解决**: 提供选项禁用 SSL 验证（仅限 debug 模式）

### 问题 4: 大响应体显示
- **解决**: 
  - 截断显示（前 10000 字符）
  - 提供"显示全部"选项
  - 或者实现虚拟滚动

### 问题 5: 请求历史
- **未来功能**: 可以考虑添加请求历史记录（使用 Room）

## 附加功能（未来考虑）

1. **请求历史**: 保存最近的请求
2. **请求模板**: 预设常用的 API 请求
3. **环境切换**: Dev/Staging/Prod 环境快速切换
4. **cURL 导出**: 将请求导出为 cURL 命令
5. **响应保存**: 将响应保存到文件
6. **语法高亮**: JSON/XML 语法高亮
7. **证书管理**: 自定义证书支持

## 时间估算

- 字符串资源: 30 分钟
- ViewModel + Repository: 1 小时
- UI 实现: 2 小时
- 集成和测试: 1 小时
- 总计: 约 4.5 小时

## 依赖检查

需要确认项目中已有的依赖：
- ✅ OkHttp（用于网络请求）
- ✅ Kotlin Coroutines（用于异步操作）
- ✅ Jetpack Compose（用于 UI）
- ✅ ViewModel（用于状态管理）
- ? Gson/Kotlinx Serialization（用于 JSON 格式化）

## 完成标准

1. ✅ 可以从 DevelopSettingsActivity 导航到 API Debug Tool
2. ✅ 可以输入 URL、方法、body、headers
3. ✅ 可以发送请求并显示响应
4. ✅ 响应格式化美观（JSON pretty print）
5. ✅ 错误处理完善
6. ✅ 所有字符串已国际化（4 套 locale）
7. ✅ UI 与应用主题一致
8. ✅ 基本测试通过
