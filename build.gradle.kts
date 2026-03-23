plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.github.aifolderpath"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")

    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.github.aifolderpath"
        name = "AI Folder Path"
        version = project.version.toString()
        description = """
            在编辑器或项目视图中，一键复制文件、目录、类、方法或代码片段的 AI 友好引用路径。支持 Maven/Gradle 多模块项目路径解析，适合粘贴到 Claude、Cursor、Copilot Chat 等 AI IDE / AI 助手中，帮助快速定位上下文与源码位置。

            Copy files, directories, classes, methods, or code snippets from the editor or project view in an AI-friendly reference format. Supports Maven/Gradle multi-module path resolution and is designed for pasting precise code context into Claude, Cursor, Copilot Chat, and other AI IDEs or coding assistants.
        """.trimIndent()
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "999.*"
        }
        vendor {
            name = "AIFolderPath"
        }
    }
}
