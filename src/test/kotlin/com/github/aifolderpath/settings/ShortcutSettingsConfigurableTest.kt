package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class ShortcutSettingsConfigurableTest : BasePlatformTestCase() {
    fun `test configurable exposes searchable id and default constructor`() {
        val configurable = ShortcutSettingsConfigurable()

        assertEquals("com.github.aifolderpath.settings.shortcuts", configurable.id)
    }

    fun `test plugin xml configurable id matches searchable id`() {
        val pluginXml = requireNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(pluginXml.contains("id=\"com.github.aifolderpath.settings.shortcuts\""))
    }

    fun `test apply blocks when active keymap instance changed with same name`() {
        val firstKeymap = TestKeymap(
            schemeName = "SameName",
            modifiable = true,
        )
        val secondKeymap = TestKeymap(
            schemeName = "SameName",
            modifiable = true,
        )
        var activeKeymap: Keymap = firstKeymap
        val requestedShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null)
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { activeKeymap },
            service = ShortcutKeymapService { activeKeymap },
        )

        configurable.createComponent()
        configurable.panel().setShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)
        activeKeymap = secondKeymap

        val error = try {
            configurable.apply()
            fail("Expected ConfigurationException")
            null
        } catch (exception: ConfigurationException) {
            exception
        }

        assertEquals("当前 Keymap 已变化，请刷新页面后重试", error?.localizedMessage)
        assertFalse(secondKeymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
    }

    fun `test apply no ops when nothing changed`() {
        val keymap = com.intellij.openapi.keymap.KeymapManager.getInstance().activeKeymap.deriveKeymap("ShortcutSettingsNoOp")
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        assertFalse(configurable.isModified)

        configurable.apply()

        assertFalse(configurable.isModified)
    }

    fun `test reset reloads current keymap values after local edits`() {
        val keymap = com.intellij.openapi.keymap.KeymapManager.getInstance().activeKeymap.deriveKeymap("ShortcutSettingsReset")
        val originalShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null)
        val editedShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null)
        keymap.removeAllActionShortcuts(PluginShortcutDefinitions.copyAiPath.actionId)
        keymap.addShortcut(PluginShortcutDefinitions.copyAiPath.actionId, originalShortcut)
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        configurable.panel().setShortcut(PluginShortcutDefinitions.copyAiPath.actionId, editedShortcut)
        assertTrue(configurable.isModified)

        configurable.reset()

        assertFalse(configurable.isModified)
        assertEquals("Alt+P", configurable.panel().displayText(PluginShortcutDefinitions.copyAiPath.actionId))
    }

    fun `test apply blocks external conflict and keeps keymap unchanged`() {
        val keymap = com.intellij.openapi.keymap.KeymapManager.getInstance().activeKeymap.deriveKeymap("ShortcutSettingsExternalConflict")
        val requestedShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null)
        keymap.addShortcut("External.Action", requestedShortcut)
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        configurable.panel().setShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)

        val error = try {
            configurable.apply()
            fail("Expected ConfigurationException")
            null
        } catch (exception: ConfigurationException) {
            exception
        }

        assertTrue(error?.localizedMessage?.contains("External.Action") == true)
        assertTrue(error?.localizedMessage?.contains("无法保存") == true)
        assertTrue(keymap.getActionIdList(requestedShortcut).contains("External.Action"))
        assertFalse(keymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
    }

    fun `test invalid captured shortcut shows card error and blocks apply`() {
        val keymap = com.intellij.openapi.keymap.KeymapManager.getInstance().activeKeymap.deriveKeymap("ShortcutSettingsInvalidCapture")
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        configurable.panel().captureShortcut(
            PluginShortcutDefinitions.copyAiPath.actionId,
            KeyStroke.getKeyStroke("ctrl K"),
            KeyStroke.getKeyStroke("ctrl C"),
        )

        assertEquals("只支持单击组合键", configurable.panel().statusMessage(PluginShortcutDefinitions.copyAiPath.actionId))

        try {
            configurable.apply()
            fail("Expected ConfigurationException")
        } catch (_: ConfigurationException) {
        }
    }

    fun `test apply blocks existing two stroke shortcut until user fixes it`() {
        val invalidShortcut = KeyboardShortcut(
            KeyStroke.getKeyStroke("ctrl K"),
            KeyStroke.getKeyStroke("ctrl C"),
        )
        val keymap = TestKeymap(
            schemeName = "ShortcutSettingsExistingTwoStroke",
            modifiable = true,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf(invalidShortcut),
            ),
        )
        val requestedShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null)
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        configurable.panel().setShortcut(PluginShortcutDefinitions.copyAiRefPath.actionId, requestedShortcut)

        val error = try {
            configurable.apply()
            fail("Expected ConfigurationException")
            null
        } catch (exception: ConfigurationException) {
            exception
        }

        assertEquals("只支持单击组合键", error?.localizedMessage)
        assertTrue(keymap.getActionIdList(invalidShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
        assertFalse(keymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiRefPath.actionId))
    }

    fun `test write failure shows clear message`() {
        val keymap = TestKeymap(
            schemeName = "ShortcutSettingsWriteFailure",
            modifiable = true,
            failOnWrite = true,
        )
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { keymap },
            service = ShortcutKeymapService { keymap },
        )

        configurable.createComponent()
        configurable.panel().setShortcut(
            PluginShortcutDefinitions.copyAiPath.actionId,
            KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null),
        )

        val error = try {
            configurable.apply()
            fail("Expected ConfigurationException")
            null
        } catch (exception: ConfigurationException) {
            exception
        }

        assertEquals("快捷键设置保存失败：simulated write failure", error?.localizedMessage)
    }

    fun `test read only keymap stays editable and saves into derived keymap`() {
        val keymap = TestKeymap(
            schemeName = "Default",
            modifiable = false,
            shortcutsByActionId = mapOf(
                PluginShortcutDefinitions.copyAiPath.actionId to listOf(
                    KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null),
                ),
            ),
        )
        var activeKeymap: Keymap = keymap
        val registry = object : ShortcutKeymapRegistry {
            private val keymaps = mutableListOf<Keymap>(keymap)
            var activatedKeymap: Keymap? = null

            override fun allKeymaps(): List<Keymap> = keymaps.toList()

            override fun addKeymap(keymap: Keymap) {
                keymaps.add(keymap)
            }

            override fun activateKeymap(keymap: Keymap) {
                activatedKeymap = keymap
                activeKeymap = keymap
            }
        }
        val requestedShortcut = KeyboardShortcut(KeyStroke.getKeyStroke("alt shift P"), null)
        val configurable = ShortcutSettingsConfigurable(
            keymapProvider = { activeKeymap },
            service = ShortcutKeymapService({ activeKeymap }, registry),
        )

        configurable.createComponent()

        assertTrue(configurable.panel().isCardControlsEnabled(PluginShortcutDefinitions.copyAiPath.actionId))
        assertTrue(configurable.panel().isCardControlsEnabled(PluginShortcutDefinitions.copyAiRefPath.actionId))
        assertTrue(configurable.panel().pageMessage().contains("保存时会创建并切换到可编辑副本"))

        configurable.panel().setShortcut(PluginShortcutDefinitions.copyAiPath.actionId, requestedShortcut)
        configurable.apply()

        val derivedKeymap = registry.activatedKeymap
        assertNotNull(derivedKeymap)
        assertNotSame(keymap, derivedKeymap)
        assertTrue(derivedKeymap!!.canModify())
        assertTrue(derivedKeymap.getActionIdList(requestedShortcut).contains(PluginShortcutDefinitions.copyAiPath.actionId))
        assertEquals("", configurable.panel().pageMessage())
    }

    private class TestKeymap(
        private val schemeName: String,
        private val modifiable: Boolean,
        private val failOnWrite: Boolean = false,
        shortcutsByActionId: Map<String, List<Shortcut>> = emptyMap(),
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
            check(!failOnWrite) { "simulated write failure" }
            shortcutsByActionId.getOrPut(actionId) { mutableListOf() }.add(shortcut)
        }

        override fun removeShortcut(actionId: String, shortcut: Shortcut) {
            check(!failOnWrite) { "simulated write failure" }
            shortcutsByActionId[actionId]?.remove(shortcut)
        }

        override fun getConflicts(
            actionId: String,
            keyboardShortcut: KeyboardShortcut,
        ): Map<String, List<KeyboardShortcut>> = emptyMap()

        override fun removeAllActionShortcuts(actionId: String) {
            check(!failOnWrite) { "simulated write failure" }
            shortcutsByActionId.remove(actionId)
        }

        override fun deriveKeymap(name: String): Keymap {
            return TestKeymap(
                schemeName = name,
                modifiable = true,
                failOnWrite = failOnWrite,
                shortcutsByActionId = shortcutsByActionId.mapValues { it.value.toList() },
            )
        }

        override fun hasActionId(actionId: String, mouseShortcut: MouseShortcut): Boolean = false
    }
}
