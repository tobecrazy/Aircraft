# Aircraft v1.4.2 发布指南

## ✅ 已完成的步骤

### 1. 代码修复和构建
- ✅ 修复了 ML Kit native 库缺失导致的崩溃
- ✅ 添加了崩溃测试确认对话框
- ✅ 更新了版本号到 1.4.2 (versionCode 14)
- ✅ 成功构建 Release APK (35 MB)
- ✅ 更新了所有文档（README.md, ChangeLogs.md）

### 2. Git 提交
- ✅ 所有更改已提交到本地 `develop` 分支
- ✅ 创建了 Git 标签 `V1.4.2`
- ⏳ 等待推送到 GitHub（需要认证）

## 📋 待完成的步骤

### 选项 A：使用 GitHub CLI（推荐，自动化）

如果您安装了 GitHub CLI：

```bash
# 1. 确保已安装 GitHub CLI
brew install gh

# 2. 登录 GitHub（只需一次）
gh auth login

# 3. 运行自动发布脚本
cd /Users/I321533/AndroidStudioProjects/Aircraft
./create-release-gh.sh
```

这将自动完成：
- 推送代码到 `develop` 分支
- 推送标签 `V1.4.2`
- 创建 GitHub Release
- 上传 APK 文件
- 使用准备好的 Release Notes

---

### 选项 B：手动操作

#### 步骤 1: 推送代码到 GitHub

```bash
cd /Users/I321533/AndroidStudioProjects/Aircraft

# 推送 develop 分支
git push origin develop

# 推送标签
git push origin V1.4.2
```

如果遇到认证问题，您可以：
- 使用 SSH: `git remote set-url origin git@github.com:tobecrazy/Aircraft.git`
- 或使用个人访问令牌: https://github.com/settings/tokens

#### 步骤 2: 创建 GitHub Release

1. **访问创建 Release 页面**：
   https://github.com/tobecrazy/Aircraft/releases/new?tag=V1.4.2

2. **填写 Release 表单**：
   - **Tag**: V1.4.2 (已选定)
   - **Target**: develop
   - **Title**: `Aircraft v1.4.2`
   - **Description**: 复制 `RELEASE_NOTES_V1.4.2.md` 的内容

3. **上传 APK**：
   - 点击"Attach binaries"
   - 上传文件: `app/build/outputs/apk/release/app-release.apk`
   - 或使用命令: 
     ```bash
     open /Users/I321533/AndroidStudioProjects/Aircraft/app/build/outputs/apk/release/
     ```

4. **发布**：
   - 确认信息无误
   - 点击 "Publish release"

---

## 📁 重要文件位置

```
📦 Aircraft/
├── 📄 RELEASE_NOTES_V1.4.2.md           # Release 说明（复制到 GitHub）
├── 🔧 create-release-gh.sh               # 自动发布脚本（使用 GitHub CLI）
├── 🔧 release-v1.4.2.sh                  # 半自动脚本（推送代码和标签）
├── 📄 ChangeLogs.md                      # 完整变更日志
├── 📄 README.md                          # 已更新版本信息
└── 📱 app/build/outputs/apk/release/
    └── app-release.apk                   # Release APK (35 MB)
```

## 🔍 发布后验证

完成发布后，验证以下内容：

### 1. GitHub Release 页面
- [ ] 访问: https://github.com/tobecrazy/Aircraft/releases/tag/V1.4.2
- [ ] 确认 Release Notes 显示正确
- [ ] 确认 APK 可下载
- [ ] 确认 APK 大小约为 35 MB

### 2. README 链接
- [ ] 访问: https://github.com/tobecrazy/Aircraft
- [ ] 确认 Download 部分指向 V1.4.2
- [ ] 确认下载链接有效

### 3. APK 功能测试
```bash
# 在实际设备上安装测试
adb install -r app/build/outputs/apk/release/app-release.apk

# 测试要点：
# 1. 打开应用，检查版本号是否为 1.4.2
# 2. 进入 Settings → QR Code Tool
# 3. 点击 "Scan QR Code"
# 4. 扫描二维码，确认不会崩溃
# 5. 进入 Developer Settings（如果是 debug 构建）
# 6. 点击 "Test Crash"，确认显示确认对话框
```

## 📊 版本对比

| 版本 | APK 大小 | QR 扫描 | 关键变化 |
|------|---------|---------|----------|
| 1.4.0 | ~16 MB | ❌ 崩溃 | 初始版本 |
| 1.4.1 | ~16 MB | ❌ 崩溃 | 开发者解锁 |
| **1.4.2** | ~35 MB | ✅ 正常 | **修复 ML Kit 崩溃** |

## 🎯 关键改进

### 修复的崩溃
```
Fatal Exception: java.lang.UnsatisfiedLinkError: 
dlopen failed: library "libbarhopper_v3.so" not found
```

### 解决方案
- 移除了 native 库排除规则
- 包含所有 ML Kit 依赖
- APK 大小增加但功能稳定

## 📞 支持

如果遇到问题：
1. 检查 GitHub Actions 构建状态
2. 查看 [Issues](https://github.com/tobecrazy/Aircraft/issues)
3. 验证 APK 签名和完整性

---

## 🚀 快速开始

**最简单的方法**（如果已安装 GitHub CLI）：

```bash
cd /Users/I321533/AndroidStudioProjects/Aircraft
./create-release-gh.sh
```

**手动方法**：

```bash
cd /Users/I321533/AndroidStudioProjects/Aircraft
./release-v1.4.2.sh  # 推送代码和标签
# 然后手动在 GitHub 上创建 Release
```

祝发布顺利！🎉
