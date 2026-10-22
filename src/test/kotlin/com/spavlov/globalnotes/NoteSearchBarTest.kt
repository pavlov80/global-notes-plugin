package com.spavlov.globalnotes

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.Color
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

class NoteSearchBarTest : BasePlatformTestCase() {
    fun testNavigationRemainsVisibleWithoutEditorFocus() {
        val editor = JBTextArea("Match one\nMatch two\nMatch three").apply {
            setSize(400, 160)
            background = Color.WHITE
            foreground = Color.BLACK
        }
        val bar = NoteSearchBar(editor)
        bar.open()
        val query = bar.components.filterIsInstance<JBTextField>().single()
        query.text = "match"

        // Model the selection being hidden after focus moves to the search field.
        // The painted active match must still differ from the other matches.
        fun matchColors(): List<Int> {
            editor.caret.isSelectionVisible = false
            val image = BufferedImage(400, 160, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                editor.paint(graphics)
            } finally {
                graphics.dispose()
            }
            return listOf(0, 10, 20).map { offset ->
                val bounds = editor.modelToView2D(offset).bounds
                image.getRGB(bounds.x + 1, bounds.y + 1)
            }
        }

        val first = matchColors()
        assertTrue("Active match should be distinct without editor focus", first[0] != first[1])
        assertEquals(first[1], first[2])

        fun pressEnter(modifiers: Int = 0) {
            val key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifiers)
            val action = query.getInputMap(JComponent.WHEN_FOCUSED).get(key)
            assertNotNull("Search field should handle the key directly", action)
            query.actionMap.get(action).actionPerformed(ActionEvent(query, ActionEvent.ACTION_PERFORMED, ""))
        }

        pressEnter()
        assertEquals(10, editor.selectionStart)
        assertEquals(listOf(first[1], first[0], first[1]), matchColors())
        pressEnter(InputEvent.SHIFT_DOWN_MASK)
        assertEquals(0, editor.selectionStart)

        val buttons = bar.components.filterIsInstance<Container>()
            .flatMap { it.components.toList() }.filterIsInstance<JButton>()
        val previous = buttons.single { it.accessibleContext.accessibleName == "Previous match" }
        val next = buttons.single { it.accessibleContext.accessibleName == "Next match" }
        previous.doClick()
        assertEquals(20, editor.selectionStart)
        assertEquals(listOf(first[1], first[1], first[0]), matchColors())
        next.doClick()
        assertEquals(0, editor.selectionStart)
        assertEquals("Match", editor.selectedText)

        bar.toggle()
        assertFalse(bar.isVisible)
        editor.caret.isSelectionVisible = false
        assertEmpty(editor.highlighter.highlights.toList())
        bar.toggle()
        assertTrue(bar.isVisible)
    }
}
