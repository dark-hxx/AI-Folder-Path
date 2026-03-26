plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.github.aifolderpath"
version = "1.0.3"

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
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
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
            <p>Copy AI-friendly reference paths for files, directories, classes, methods, and code snippets in IntelliJ IDEA.</p>
            <p><b>中文</b></p>
            <ul>
              <li>在编辑器或项目视图中，一键复制 AI 友好引用路径</li>
              <li>支持文件、目录、类、方法和代码片段</li>
              <li>支持 Maven / Gradle 多模块项目路径解析</li>
              <li>适合粘贴到 Claude、Cursor、Copilot Chat 等 AI IDE / AI 助手</li>
              <li>帮助快速定位上下文与源码位置</li>
            </ul>
            <p><b>English</b></p>
            <ul>
              <li>Copy AI-friendly reference paths from the editor or project view</li>
              <li>Supports files, directories, classes, methods, and code snippets</li>
              <li>Supports Maven / Gradle multi-module path resolution</li>
              <li>Designed for Claude, Cursor, Copilot Chat, and other AI IDEs / coding assistants</li>
              <li>Helps locate code context and source positions quickly</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "251"
        }
        vendor {
            name = "AIFolderPath"
        }
    }
}
