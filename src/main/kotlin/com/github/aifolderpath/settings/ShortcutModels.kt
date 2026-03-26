package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap

data class ShortcutActionState(
    val actionId: String,
    val label: String,
    val defaultShortcut: KeyboardShortcut,
    val shortcutText: String,
    val hasMultipleBindings: Boolean,
)

data class ShortcutPageState(
    val keymapName: String,
    val isReadOnly: Boolean,
    val actions: List<ShortcutActionState>,
)

data class EditableShortcutCard(
    val definition: ShortcutActionDefinition,
    val originalShortcut: KeyboardShortcut?,
    val editedShortcut: KeyboardShortcut?,
    val hasMultipleBindings: Boolean,
    val statusMessage: String? = null,
    val validationMessage: String? = null,
)

data class EditableShortcutPage(
    val originalKeymapName: String,
    val originalKeymap: Keymap,
    val editableKeymapName: String,
    val editableKeymap: Keymap,
    val readOnly: Boolean,
    val cards: List<EditableShortcutCard>,
) {
    val actions: List<EditableShortcutActionState>
        get() = cards.map { card ->
            EditableShortcutActionState(
                actionId = card.definition.actionId,
                originalShortcut = card.originalShortcut,
                editedShortcut = card.editedShortcut,
            )
        }

    val pageMessage: String
        get() = if (readOnly) {
            "当前 Keymap 为只读，保存时会创建并切换到可编辑副本：$editableKeymapName"
        } else {
            ""
        }

    fun replaceShortcut(actionId: String, shortcut: KeyboardShortcut?): EditableShortcutPage {
        return copy(
            cards = cards.map { card ->
                if (card.definition.actionId == actionId) {
                    card.copy(editedShortcut = shortcut, validationMessage = null)
                } else {
                    card
                }
            },
        )
    }

    fun replaceCard(actionId: String, shortcut: KeyboardShortcut?, validationMessage: String?): EditableShortcutPage {
        return copy(
            cards = cards.map { card ->
                if (card.definition.actionId == actionId) {
                    card.copy(editedShortcut = shortcut, validationMessage = validationMessage)
                } else {
                    card
                }
            },
        )
    }

    fun hasValidationErrors(): Boolean = cards.any { it.validationMessage != null }
}

data class EditableShortcutActionState(
    val actionId: String,
    val originalShortcut: KeyboardShortcut?,
    val editedShortcut: KeyboardShortcut?,
)

data class ShortcutConflict(
    val ownerActionId: String,
    val requestedByActionId: String,
    val requestedShortcut: KeyboardShortcut,
    val ownerDisplayName: String = ownerActionId,
    val requestedByDisplayName: String = requestedByActionId,
)
