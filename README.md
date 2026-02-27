# AI Folder Path

IntelliJ IDEA 插件，将编辑器中选中的类名、方法名或代码片段以 AI IDE 友好的格式复制到剪贴板。

## 输出格式

| 场景 | 输出示例 |
|------|----------|
| 选中类名 / 光标在类名上 | `@module/src/main/java/com/example/UserService.java` |
| 选中方法名 / 光标在方法名上 | `@module/src/main/java/com/example/UserService.java login` |
| 选中代码片段 | `@module/.../UserService.java login`<br>`42line    if (user == null) {`<br>`43line        throw new RuntimeException();`<br>`44line    }` |
| Project 视图选中文件 | `@module/src/main/java/com/example/UserService.java` |

多模块项目自动识别最近的 `pom.xml` / `build.gradle(.kts)` 确定模块根目录。

## 使用方式

- **快捷键**：`Alt + P`（可在 Settings > Keymap 中搜索 "Copy AI Path" 自定义）
- **右键菜单**：编辑器 / Project 视图右键 > Copy AI Path

复制成功后会弹出气泡通知。

## 环境要求

| 项目 | 版本 |
|------|------|
| IntelliJ IDEA | 2025.1+ |
| JDK | 21 |
| Kotlin | 2.1.0 |
| Gradle | 8.14 (Wrapper) |

## 构建

```bash
./gradlew build
```

## 项目结构

```
src/main/kotlin/com/github/aifolderpath/
    CopyAIPathAction.kt   -- 主 Action，处理选中内容判断与格式化输出
    PathResolver.kt        -- 路径解析，生成 @模块名/相对路径 格式
src/main/resources/META-INF/
    plugin.xml             -- 插件描述符，注册 Action 与通知组
```

## License

MIT
