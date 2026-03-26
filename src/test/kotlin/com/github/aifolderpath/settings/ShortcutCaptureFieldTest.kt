package com.github.aifolderpath.settings

import com.intellij.openapi.actionSystem.KeyboardShortcut
import junit.framework.TestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ShortcutCaptureFieldTest : TestCase() {
    fun testFirstStrokeFinalizesAsSingleShortcut() {
        val field = ShortcutCaptureField()

        field.acceptStroke(KeyStroke.getKeyStroke("alt shift P"))
        field.commitPendingStroke()

        assertEquals("Alt+Shift+P", field.displayText())
        assertNull(field.errorText())
    }

    fun testSecondStrokeTurnsSequenceIntoValidationError() {
        var latestShortcut: KeyboardShortcut? = KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null)
        var latestError: String? = null
        val field = ShortcutCaptureField { shortcut, error ->
            latestShortcut = shortcut
            latestError = error
        }

        field.acceptStroke(KeyStroke.getKeyStroke("ctrl K"))
        field.acceptStroke(KeyStroke.getKeyStroke("ctrl C"))

        assertNull(latestShortcut)
        assertEquals("只支持单击组合键", latestError)
        assertEquals("只支持单击组合键", field.errorText())
        assertEquals("", field.displayText())
    }

    fun testPureModifierTurnsIntoValidationError() {
        var latestShortcut: KeyboardShortcut? = KeyboardShortcut(KeyStroke.getKeyStroke("alt P"), null)
        var latestError: String? = null
        val field = ShortcutCaptureField { shortcut, error ->
            latestShortcut = shortcut
            latestError = error
        }

        field.acceptStroke(KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK))

        assertNull(latestShortcut)
        assertEquals("不支持纯修饰键", latestError)
        assertEquals("不支持纯修饰键", field.errorText())
        assertEquals("", field.displayText())
    }

    fun testClearShortcutResetsFieldAndClearsError() {
        val field = ShortcutCaptureField()
        field.acceptStroke(KeyStroke.getKeyStroke("alt shift P"))
        field.commitPendingStroke()

        field.clearShortcut()

        assertNull(field.currentShortcut())
        assertNull(field.errorText())
        assertEquals("", field.displayText())
    }
}
