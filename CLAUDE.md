# AIFolderPath

## 项目愿景

JetBrains IntelliJ IDEA 插件，将编辑器中选中的类名、方法名或代码片段以 AI IDE 友好的格式（`@模块名/路径 方法名` + 行号）复制到剪贴板。支持 Maven/Gradle 多模块项目的路径解析。

## 架构总览

单模块 IntelliJ Platform 插件，核心只有两个类：

- **CopyAIPathAction** -- 注册为 Editor/ProjectView 右键菜单 Action，处理用户选中内容的判断（标识符 vs 代码片段），格式化输出并写入剪贴板。
- **PathResolver** -- 工具对象（Kotlin `object`），从文件向上查找最近的 `pom.xml` / `build.gradle(.kts)` 确定模块根，生成 `@模块名/相对路径` 格式。

```
用户选中内容 --> CopyAIPathAction.actionPerformed()
                     |
                     +--> PathResolver.resolve() --> 生成 @模块/路径
                     |
                     +--> buildFromEditor()       --> 追加方法名/行号
                     |
                     +--> CopyPasteManager        --> 写入剪贴板
```

## 模块结构图

本项目为单模块结构，无子模块拆分。

```
AIFolderPath/
  build.gradle.kts          -- 构建配置
  settings.gradle.kts       -- 项目设置
  gradle.properties          -- Gradle 属性
  src/main/
    kotlin/com/github/aifolderpath/
      CopyAIPathAction.kt   -- 主 Action
      PathResolver.kt       -- 路径解析
    resources/META-INF/
      plugin.xml             -- 插件描述符
```

## 模块索引

| 路径 | 语言 | 职责 | 入口文件 |
|------|------|------|----------|
| (根) | Kotlin | IntelliJ 插件，AI 路径复制 | `src/main/kotlin/.../CopyAIPathAction.kt` |

## 运行与开发

### 环境要求

| 项目 | 版本 |
|------|------|
| JDK | 21（注意：plugin verification 报告建议 17，见下方已知问题） |
| Gradle | 8.14（Wrapper） |
| Kotlin | 1.9.25 |
| IntelliJ Platform | 2024.1+ (sinceBuild=241) |
| IntelliJ Platform Gradle Plugin | 2.2.1 |

### 常用命令

```bash
# 构建插件
./gradlew build

# 在沙箱 IDE 中运行调试
./gradlew runIde

# 验证插件兼容性
./gradlew verifyPluginConfiguration
```

### 快捷键

`Ctrl + Shift + Alt + C` -- 复制 AI 路径（默认绑定）

### 注册位置

- Editor 右键菜单（EditorPopupMenu）
- Project 视图右键菜单（ProjectViewPopupMenu）

## 测试策略

当前项目**无测试目录**（`src/test` 不存在）。建议后续补充以下测试：

- `PathResolver.resolve()` 的单模块/多模块路径拼接
- `CopyAIPathAction.buildFromEditor()` 的标识符 vs 代码片段判断
- `isIdentifierSelection()` 的边界用例

## 编码规范

- 语言：Kotlin
- 风格：Kotlin Coding Conventions（JetBrains 默认）
- 包结构：`com.github.aifolderpath`
- 关键约定：
  - `PathResolver` 为 `object` 单例，无状态
  - Action 类继承 `AnAction`，遵循 IntelliJ Action System 规范
  - `update()` 方法控制菜单项可见性

## AI 使用指引

- 核心路径格式：`@模块名/src/main/java/包路径/类名.java 方法名`
- 代码片段格式：路径后追加 `\n行号line    代码内容`
- 修改路径格式逻辑 --> `PathResolver.kt`
- 修改输出拼接逻辑 --> `CopyAIPathAction.kt` 的 `buildFromEditor()`
- 添加新 Action --> `plugin.xml` 注册 + 新建 Action 类

## 已知问题

- `verifyPluginConfiguration` 报告 JDK 21 与 sinceBuild=241 (IntelliJ 2024.1) 不兼容，IntelliJ 2024.1 要求 targetCompatibility=17。需根据实际运行环境决定是否降级 JDK 或提升 sinceBuild。

## 变更记录 (Changelog)

| 时间 | 操作 |
|------|------|
| 2026-02-27 14:08:36 | 初始化架构扫描，生成根级 CLAUDE.md |
