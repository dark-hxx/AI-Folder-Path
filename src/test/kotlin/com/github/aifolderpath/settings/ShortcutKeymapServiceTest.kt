package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.KeyStroke

class ShortcutKeymapServiceTest : BasePlatformTestCase() {
    fun `test BasePlatformTestCase is available`() {
        assertNotNull(project)
    }

    fun `test buildEditableState derives writable copy for read only keymap`() {
        val keymap = TestKeymap(
            schemeName = "Default",
            modifiable = false,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf<Shortcut>(
                    keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
                )
            )
        )
        val service = ShortcutKeymapService(
            activeKeymapProvider = { keymap },
            keymapRegistry = object : ShortcutKeymapRegistry {
                override fun allKeymaps(): List<Keymap> = listOf(keymap)
                override fun addKeymap(keymap: Keymap) = Unit
                override fun activateKeymap(keymap: Keymap) = Unit
            },
        )

        val state = service.buildEditableState()

        assertTrue(state.readOnly)
        assertNotSame(keymap, state.editableKeymap)
        assertTrue(state.editableKeymap.canModify())
        assertEquals("Default (AI Folder Path)", state.editableKeymapName)
        assertTrue(state.pageMessage.contains("保存时会创建并切换到可编辑副本"))
    }

    fun `test applyChanges registers and activates derived keymap for read only source`() {
        val sourceKeymap = TestKeymap(
            schemeName = "Default",
            modifiable = false,
            shortcutsByActionId = emptyMap(),
        )
        val requestedShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        val registry = object : ShortcutKeymapRegistry {
            private val keymaps = mutableListOf<Keymap>(sourceKeymap)
            var activatedKeymap: Keymap? = null

            override fun allKeymaps(): List<Keymap> = keymaps.toList()

            override fun addKeymap(keymap: Keymap) {
                keymaps.add(keymap)
            }

            override fun activateKeymap(keymap: Keymap) {
                activatedKeymap = keymap
            }
        }
        val service = ShortcutKeymapService(
            activeKeymapProvider = { sourceKeymap },
            keymapRegistry = registry,
        )
        val editablePage = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)

        val savedKeymap = service.applyChanges(editablePage)

        assertNotSame(sourceKeymap, savedKeymap)
        assertSame(savedKeymap, registry.activatedKeymap)
        assertTrue(savedKeymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
    }

    fun `test readPageState marks immutable keymap as read only`() {
        val keymap = TestKeymap(
            schemeName = "Default",
            modifiable = false,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf<Shortcut>(
                    keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
                )
            )
        )

        val state = ShortcutKeymapService { keymap }.readPageState()

        assertEquals("Default", state.keymapName)
        assertTrue(state.isReadOnly)
    }

    fun `test readPageState chooses stable shortcut text for multiple bindings`() {
        val firstShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        val secondShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val thirdShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiRefPath.actionId to listOf<Shortcut>(firstShortcut, secondShortcut, thirdShortcut)
            )
        )

        val state = ShortcutKeymapService { keymap }.readPageState()
        val actionState = state.actions.single { it.actionId == PluginShortcutDefinitions.copyAiRefPath.actionId }
        val expectedShortcutText = listOf(firstShortcut, secondShortcut, thirdShortcut)
            .map(KeymapUtil::getShortcutText)
            .sorted()
            .first()

        assertEquals(expectedShortcutText, actionState.shortcutText)
        assertTrue(actionState.hasMultipleBindings)
    }

    fun `test readPageState returns empty shortcut text when action has no binding`() {
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = emptyMap()
        )

        val state = ShortcutKeymapService { keymap }.readPageState()
        val actionState = state.actions.single { it.actionId == PluginShortcutDefinitions.copyAiPath.actionId }

        assertEquals("", actionState.shortcutText)
        assertEquals(
            KeymapUtil.getShortcutText(PluginShortcutDefinitions.copyAiPath.defaultShortcut),
            KeymapUtil.getShortcutText(actionState.defaultShortcut)
        )
        assertFalse(actionState.hasMultipleBindings)
    }

    fun `test readPageState ignores non keyboard bindings`() {
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf<Shortcut>(
                    MouseShortcut(MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK, 1)
                )
            )
        )

        val state = ShortcutKeymapService { keymap }.readPageState()
        val actionState = state.actions.single { it.actionId == PluginShortcutDefinitions.copyAiPath.actionId }

        assertEquals("", actionState.shortcutText)
        assertFalse(actionState.hasMultipleBindings)
    }

    fun `test detectConflicts ignores managed shortcuts that will be released during swap`() {
        val copyAiPathShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val copyAiRefShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf(copyAiPathShortcut),
                PluginShortcutDefinitions.copyAiRefPath.actionId to listOf(copyAiRefShortcut),
            ),
        )
        val service = ShortcutKeymapService { keymap }

        val conflicts = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, copyAiRefShortcut)
            .replaceShortcut(PluginShortcutDefinitions.copyAiRefPath.actionId, copyAiPathShortcut)
            .let(service::detectConflicts)

        assertTrue(conflicts.isEmpty())
    }

    fun `test detectConflicts releases hidden extra binding in final state`() {
        val keptShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val releasedShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf(keptShortcut, releasedShortcut),
                PluginShortcutDefinitions.copyAiRefPath.actionId to emptyList(),
            ),
        )
        val service = ShortcutKeymapService { keymap }

        val conflicts = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, keptShortcut)
            .replaceShortcut(PluginShortcutDefinitions.copyAiRefPath.actionId, releasedShortcut)
            .let(service::detectConflicts)

        assertTrue(conflicts.isEmpty())
    }

    fun `test applyChanges binds managed shortcut when no conflict exists`() {
        val requestedShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = emptyMap(),
        )
        val service = ShortcutKeymapService { keymap }
        val editablePage = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)

        service.applyChanges(editablePage)

        assertTrue(keymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
    }

    fun `test detectConflicts reports external action owner`() {
        val requestedShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                "External.Action" to listOf(requestedShortcut),
            ),
        )
        val service = ShortcutKeymapService { keymap }

        val conflicts = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)
            .let(service::detectConflicts)

        assertEquals(listOf("External.Action"), conflicts.map { it.ownerActionId })
        assertEquals(PluginShortcutDefinitions.copyAiPath.actionId, conflicts.single().requestedByActionId)
    }

    fun `test detectConflicts reports external two stroke owner for same first keystroke`() {
        val requestedShortcut = keyboardShortcut(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
        val externalTwoStrokeShortcut = KeyboardShortcut(
            KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
        )
        val keymap = com.intellij.openapi.keymap.KeymapManager.getInstance().activeKeymap.deriveKeymap("ShortcutSettingsTwoStrokeConflict")
        keymap.addShortcut("External.Action", externalTwoStrokeShortcut)
        val service = ShortcutKeymapService { keymap }

        val conflicts = service.buildEditableState()
            .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)
            .let(service::detectConflicts)

        assertTrue(
            conflicts.any {
                it.ownerActionId == "External.Action" &&
                    it.requestedByActionId == PluginShortcutDefinitions.copyAiPath.actionId
            }
        )
    }

    fun `test applyChanges preserves non keyboard shortcuts`() {
        val existingKeyboardShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val newKeyboardShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        val mouseShortcut = MouseShortcut(MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK, 1)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf(existingKeyboardShortcut, mouseShortcut),
            ),
        )
        val service = ShortcutKeymapService { keymap }

        service.applyChanges(
            service.buildEditableState()
                .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, newKeyboardShortcut)
        )

        val shortcuts = keymap.getShortcuts(PluginShortcutDefinitions.copyAiPath.actionId).toList()
        assertTrue(shortcuts.contains(mouseShortcut))
        assertTrue(shortcuts.contains(newKeyboardShortcut))
        assertFalse(shortcuts.contains(existingKeyboardShortcut))
    }

    fun `test detectConflicts rejects duplicate shortcut inside managed actions`() {
        val sharedShortcut = keyboardShortcut(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK)
        val keymap = TestKeymap(
            schemeName = "Custom",
            modifiable = true,
            shortcutsByActionId = emptyMap(),
        )
        val service = ShortcutKeymapService { keymap }

        val error = try {
            service.detectConflicts(
                service.buildEditableState()
                    .replaceShortcut(PluginShortcutDefinitions.copyAiPath.actionId, sharedShortcut)
                    .replaceShortcut(PluginShortcutDefinitions.copyAiRefPath.actionId, sharedShortcut)
            )
            fail("Expected IllegalArgumentException")
            null
        } catch (exception: IllegalArgumentException) {
            exception
        }

        assertEquals("两个插件动作不能共用同一个快捷键", error?.message)
    }

    private fun keyboardShortcut(keyCode: Int, modifiers: Int): KeyboardShortcut {
        return KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, modifiers), null)
    }

    private class TestKeymap(
        private val schemeName: String,
        private val modifiable: Boolean,
        shortcutsByActionId: Map<String, List<Shortcut>>,
    ) : Keymap {
        private val shortcutsByActionId = shortcutsByActionId
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        override fun getName(): String = schemeName

        override fun getPresentableName(): String = schemeName

        override fun getParent(): Keymap? = null

        override fun canModify(): Boolean = modifiable

        override fun getActionIdList(): Collection<String> = shortcutsByActionId.keys

        override fun getActionIds(): Array<String> = shortcutsByActionId.keys.toTypedArray()

        override fun getShortcuts(actionId: String?): Array<Shortcut> {
            return shortcutsByActionId[actionId]?.toTypedArray() ?: Shortcut.EMPTY_ARRAY
        }

        override fun getActionIds(keyStroke: KeyStroke): Array<String> = emptyArray()

        override fun getActionIds(firstKeyStroke: KeyStroke, secondKeyStroke: KeyStroke?): Array<String> = emptyArray()

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getActionIds(shortcut: Shortcut): Array<String> = getActionIdList(shortcut).toTypedArray()

        override fun getActionIdList(shortcut: Shortcut): List<String> {
            return shortcutsByActionId.entries
                .filter { entry -> shortcut in entry.value }
                .map { it.key }
        }

        override fun getActionIds(mouseShortcut: MouseShortcut): List<String> = getActionIdList(mouseShortcut)

        override fun addShortcut(actionId: String, shortcut: Shortcut) {
            shortcutsByActionId.getOrPut(actionId) { mutableListOf() }.add(shortcut)
        }

        override fun removeShortcut(actionId: String, shortcut: Shortcut) {
            shortcutsByActionId[actionId]?.remove(shortcut)
        }

        override fun getConflicts(
            actionId: String,
            keyboardShortcut: KeyboardShortcut,
        ): Map<String, List<KeyboardShortcut>> = emptyMap()

        override fun removeAllActionShortcuts(actionId: String) {
            shortcutsByActionId.remove(actionId)
        }

        override fun deriveKeymap(name: String): Keymap {
            return TestKeymap(
                schemeName = name,
                modifiable = true,
                shortcutsByActionId = shortcutsByActionId.mapValues { it.value.toList() },
            )
        }

        override fun hasActionId(actionId: String, mouseShortcut: MouseShortcut): Boolean = false
    }
}
