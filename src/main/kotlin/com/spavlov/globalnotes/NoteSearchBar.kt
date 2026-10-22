package com.spavlov.globalnotes

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultHighlighter

/** Search state belongs to this editor, rather than the persisted notes. */
internal class NoteSearchBar(private val editor: JTextArea) : JPanel(BorderLayout(4, 0)) {
    private val query = JBTextField().apply {
        toolTipText = "Find in current note (case-insensitive)"
        accessibleContext.accessibleName = "Find in current note"
    }
    private val count = object : JBLabel() {
        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            val metrics = getFontMetrics(font)
            val digits = maxOf(2, matches.size.toString().length)
            val digitWidth = ('0'..'9').maxOf { metrics.charWidth(it) }
            // Reserve both numbers using the total's digit count, so navigation
            // never changes the field width (even with proportional digits).
            size.width = maxOf(
                digitWidth * digits * 2 + metrics.stringWidth(" of "),
                metrics.stringWidth("No matches")
            ) + insets.left + insets.right
            return size
        }

        override fun getMinimumSize(): Dimension = preferredSize
    }
    private val previous = JButton(AllIcons.General.ArrowLeft).apply {
        toolTipText = "Previous match (Shift+Enter)"
        accessibleContext.accessibleName = "Previous match"
        margin = JBUI.insets(2)
        isFocusable = false
    }
    private val next = JButton(AllIcons.General.ArrowRight).apply {
        toolTipText = "Next match (Enter)"
        accessibleContext.accessibleName = "Next match"
        margin = JBUI.insets(2)
        isFocusable = false
    }
    val navigationButtonWidth: Int
        get() = maxOf(previous.preferredSize.width, next.preferredSize.width)

    private val highlights = mutableListOf<Any>()
    private var matches = emptyList<Int>()
    private var current = -1
    private val currentPainter = DefaultHighlighter.DefaultHighlightPainter(
        JBColor(Color(255, 175, 60), Color(150, 100, 35))
    )
    private val painter = DefaultHighlighter.DefaultHighlightPainter(
        JBColor(Color(255, 225, 135), Color(105, 85, 30))
    )

    init {
        isVisible = false
        border = JBUI.Borders.empty(4)
        add(query, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(count)
            add(previous)
            add(next)
        }, BorderLayout.EAST)
        previous.addActionListener { navigate(-1) }
        next.addActionListener { navigate(1) }
        bind(query, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next-match") { navigate(1) }
        bind(query, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "previous-match") {
            navigate(-1)
        }
        query.document.addDocumentListener(listener { refresh(selectMatch = true) })
        editor.document.addDocumentListener(listener { refresh(selectMatch = false) })
    }

    fun installShortcuts(container: JComponent) {
        object : DumbAwareAction() {
            override fun actionPerformed(event: AnActionEvent) = open()
        }.registerCustomShortcutSet(
            ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).shortcutSet, container
        )
        bind(container, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-note-search") { close() }
        bind(container, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "next-note-match") { navigate(1) }
        bind(container, KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK), "previous-note-match") {
            navigate(-1)
        }
    }

    fun open() {
        val selection = editor.selectedText
        isVisible = true
        if (!selection.isNullOrEmpty() && !selection.contains('\n')) query.text = selection
        refresh(selectMatch = true)
        revalidate()
        query.requestFocusInWindow()
        query.selectAll()
    }

    fun toggle() {
        if (isVisible) close() else open()
    }

    private fun close() {
        if (!isVisible) return
        isVisible = false
        clearHighlights()
        revalidate()
        parent?.repaint()
        editor.requestFocusInWindow()
    }

    private fun refresh(selectMatch: Boolean) {
        clearHighlights()
        if (!isVisible) return
        val needle = query.text
        val text = editor.text
        matches = buildList {
            if (needle.isNotEmpty()) {
                var offset = text.indexOf(needle, ignoreCase = true)
                while (offset >= 0) {
                    add(offset)
                    offset = text.indexOf(needle, offset + needle.length, ignoreCase = true)
                }
            }
        }
        current = matches.indexOfFirst { it >= editor.selectionStart }
        if (current < 0 && matches.isNotEmpty()) current = 0
        updateCount()
        highlightCurrent()
        if (selectMatch) selectCurrent()
    }

    private fun navigate(direction: Int) {
        if (!isVisible || matches.isEmpty()) return
        current = Math.floorMod(current + direction, matches.size)
        highlightCurrent()
        selectCurrent()
        updateCount()
    }

    private fun selectCurrent() {
        if (current < 0) return
        val start = matches[current]
        editor.select(start, start + query.text.length)
        // DefaultCaret hides the selection when the search field owns focus.
        editor.caret.isSelectionVisible = true
        editor.modelToView2D(start)?.bounds?.let { editor.scrollRectToVisible(it) }
    }

    private fun highlightCurrent() {
        clearHighlights()
        // Layered highlights paint in reverse order. An overlapping ordinary
        // highlight can cover the current match when the editor loses focus.
        // Give each match exactly one painter so the active color stays visible.
        matches.forEachIndexed { index, start ->
            highlights.add(editor.highlighter.addHighlight(
                start, start + query.text.length, if (index == current) currentPainter else painter
            ))
        }
    }

    private fun updateCount() {
        count.text = when {
            query.text.isEmpty() -> ""
            matches.isEmpty() -> "No matches"
            else -> "${current + 1} of ${matches.size}"
        }
        previous.isEnabled = matches.isNotEmpty()
        next.isEnabled = matches.isNotEmpty()
    }

    private fun clearHighlights() {
        highlights.forEach { editor.highlighter.removeHighlight(it) }
        highlights.clear()
    }

    private fun listener(onChange: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) = onChange()
        override fun removeUpdate(event: DocumentEvent) = onChange()
        override fun changedUpdate(event: DocumentEvent) = onChange()
    }

    private fun bind(component: JComponent, key: KeyStroke, name: String, action: () -> Unit) {
        val condition = if (component === query) WHEN_FOCUSED else WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        component.getInputMap(condition).put(key, name)
        component.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = action()
        })
    }
}
