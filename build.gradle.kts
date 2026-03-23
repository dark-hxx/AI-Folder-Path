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
        description = "Copy file path in AI-friendly format for referencing in AI IDE tools."
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "999.*"
        }
        vendor {
            name = "AIFolderPath"
        }
    }
}
