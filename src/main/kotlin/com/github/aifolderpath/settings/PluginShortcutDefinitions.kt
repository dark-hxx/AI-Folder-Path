package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import javax.swing.KeyStroke

data class ShortcutActionDefinition(
    val actionId: String,
    val label: String,
    val defaultShortcut: KeyboardShortcut,
    val description: String,
) {
    val title: String
        get() = label
}

object PluginShortcutDefinitions {
    val copyAiPath = ShortcutActionDefinition(
        actionId = "AIFolderPath.CopyAction",
        label = "Copy AI Path",
        defaultShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null),
        description = "编辑器 / 项目视图复制 AI 路径",
    )

    val copyAiRefPath = ShortcutActionDefinition(
        actionId = "AIFolderPath.CopyRefAction",
        label = "Copy AI Ref Path",
        defaultShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt P"), null),
        description = "复制实现 / 定义引用路径",
    )

    val all = listOf(copyAiPath, copyAiRefPath)
}
