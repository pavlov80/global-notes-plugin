package com.spavlov.globalnotes

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

class NotesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val notesService = NotesService.getInstance()
        var updatingEditor = false

        val textArea = JBTextArea(notesService.text).apply {
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        }

        fun changeLineIndent(outdent: Boolean) {
            val selectionStart = textArea.selectionStart
            val selectionEnd = textArea.selectionEnd

            if (!outdent && selectionStart == selectionEnd) {
                textArea.replaceSelection("\t")
                return
            }

            val text = textArea.text
            val blockStart = if (selectionStart == 0) {
                0
            } else {
                text.lastIndexOf('\n', selectionStart - 1).let { if (it < 0) 0 else it + 1 }
            }
            val blockEnd = if (selectionStart == selectionEnd) {
                text.indexOf('\n', selectionEnd).let { if (it < 0) text.length else it }
            } else {
                selectionEnd
            }
            val block = text.substring(blockStart, blockEnd)
            val transformed = if (outdent) {
                block.replace(Regex("(?m)^(?:\\t| {1,4})"), "")
            } else {
                buildString(block.length + 8) {
                    var atLineStart = true
                    block.forEach { character ->
                        if (atLineStart) append('\t')
                        append(character)
                        atLineStart = character == '\n'
                    }
                }
            }

            textArea.replaceRange(transformed, blockStart, blockEnd)
            if (selectionStart == selectionEnd) {
                val removedCharacters = block.length - transformed.length
                textArea.caretPosition = (selectionStart - removedCharacters).coerceAtLeast(blockStart)
            } else {
                textArea.select(blockStart, blockStart + transformed.length)
            }
        }

        textArea.getInputMap(JComponent.WHEN_FOCUSED).apply {
            put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "indent-selected-lines")
            put(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "outdent-selected-lines"
            )
        }
        textArea.actionMap.apply {
            put("indent-selected-lines", object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) = changeLineIndent(outdent = false)
            })
            put("outdent-selected-lines", object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) = changeLineIndent(outdent = true)
            })
        }

        val selectedNoteLabel = JBLabel().apply {
            border = BorderFactory.createEmptyBorder(0, 8, 0, 4)
        }
        val notesButton = JButton(AllIcons.General.ChevronDown).apply {
            toolTipText = "Choose, add, rename, or delete a note"
            margin = JBUI.insets(6)
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            preferredSize = JBUI.size(36, 32)
            minimumSize = JBUI.size(36, 32)
            maximumSize = JBUI.size(36, 32)
        }
        val header = JPanel(BorderLayout()).apply {
            add(selectedNoteLabel, BorderLayout.CENTER)
            add(notesButton, BorderLayout.EAST)
            border = JBUI.Borders.empty(4, 0, 4, 8)
        }

        fun updateHeaderAppearance() {
            val isDarkTheme = !JBColor.isBright()
            selectedNoteLabel.foreground = if (isDarkTheme) Color(180, 224, 189) else Color(36, 88, 49)
            header.background = UIUtil.getTreeBackground()
            header.repaint()
        }

        fun updateTextAreaAppearance() {
            val editorScheme = EditorColorsManager.getInstance().globalScheme
            val editorFont = editorScheme.getFont(EditorFontType.PLAIN).deriveFont(
                editorScheme.getFont(EditorFontType.PLAIN).size2D + 1f
            )
            textArea.font = editorFont
            textArea.foreground = editorScheme.defaultForeground
            textArea.background = UIUtil.getTreeBackground()
            textArea.caretColor = editorScheme.getColor(EditorColors.CARET_COLOR)
                ?: editorScheme.defaultForeground
            editorScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)?.let {
                textArea.selectionColor = it
            }
            editorScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR)?.let {
                textArea.selectedTextColor = it
            }
            textArea.repaint()
        }

        fun refreshEditor() {
            updatingEditor = true
            textArea.text = notesService.text
            updatingEditor = false
            selectedNoteLabel.text = notesService.selectedNote.name
        }

        var notesMenu: JPopupMenu? = null
        var suppressNextMenuOpen = false

        fun currentMouseEventTargetsNotesButton(): Boolean {
            val event = EventQueue.getCurrentEvent() as? MouseEvent ?: return false
            if (!notesButton.isShowing) return false
            val buttonLocation = notesButton.locationOnScreen
            return event.xOnScreen >= buttonLocation.x &&
                event.xOnScreen < buttonLocation.x + notesButton.width &&
                event.yOnScreen >= buttonLocation.y &&
                event.yOnScreen < buttonLocation.y + notesButton.height
        }

        fun showNotesMenu(anchor: Component) {
            val menu = JPopupMenu()
            notesMenu = menu
            menu.addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit

                override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                    suppressNextMenuOpen = currentMouseEventTargetsNotesButton()
                    if (notesMenu === menu) notesMenu = null
                }

                override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
            })

            fun addMenuItem(item: JMenuItem) {
                // Preserve the IDE's menu font and colors while providing a larger,
                // consistently scaled target for every action.
                item.border = BorderFactory.createCompoundBorder(
                    item.border,
                    JBUI.Borders.empty(5, 10)
                )
                menu.add(item)
            }

            notesService.notes.forEach { note ->
                val item = JMenuItem(note.name).apply {
                    if (note.id == notesService.selectedNoteId) {
                        icon = AllIcons.Actions.Checked
                    }
                }
                item.addActionListener {
                    notesService.selectNote(note.id)
                    refreshEditor()
                }
                addMenuItem(item)
            }

            menu.add(JSeparator())
            val addItem = JMenuItem("Add note…")
            addItem.addActionListener {
                val name = Messages.showInputDialog(
                    project, "Note name:", "Add Note", Messages.getQuestionIcon(), "", null
                )
                if (name != null) {
                    notesService.addNote(name)
                    refreshEditor()
                }
            }
            addMenuItem(addItem)

            val renameItem = JMenuItem("Rename current note…")
            renameItem.addActionListener {
                val current = notesService.selectedNote
                val name = Messages.showInputDialog(
                    project, "Note name:", "Rename Note", Messages.getQuestionIcon(), current.name, null
                )
                if (name != null && notesService.renameNote(current.id, name)) {
                    refreshEditor()
                }
            }
            addMenuItem(renameItem)

            val deleteItem = JMenuItem("Delete current note")
            deleteItem.isEnabled = notesService.notes.size > 1
            deleteItem.addActionListener {
                val current = notesService.selectedNote
                val answer = Messages.showYesNoDialog(
                    project,
                    "Delete \"${current.name}\"? Its text will be lost.",
                    "Delete Note",
                    Messages.getWarningIcon()
                )
                if (answer == Messages.YES) {
                    notesService.deleteNote(current.id)
                    refreshEditor()
                }
            }
            addMenuItem(deleteItem)
            menu.show(anchor, 0, anchor.height)
        }

        notesButton.addActionListener {
            val currentMenu = notesMenu
            if (currentMenu == null && !suppressNextMenuOpen) {
                showNotesMenu(notesButton)
            } else {
                currentMenu?.isVisible = false
                notesMenu = null
            }
            suppressNextMenuOpen = false
        }

        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = save()
            override fun removeUpdate(event: DocumentEvent) = save()
            override fun changedUpdate(event: DocumentEvent) = save()

            private fun save() {
                if (!updatingEditor) notesService.text = textArea.text
            }
        })

        updateTextAreaAppearance()
        updateHeaderAppearance()
        refreshEditor()
        val scrollPane = JBScrollPane(textArea).apply {
            border = BorderFactory.createEmptyBorder()
        }
        val headerWithSeparator = JPanel(BorderLayout()).apply {
            add(JSeparator(), BorderLayout.NORTH)
            add(header, BorderLayout.CENTER)
            add(JSeparator(), BorderLayout.SOUTH)
        }
        val panel = JPanel(BorderLayout()).apply {
            add(headerWithSeparator, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            background = UIUtil.getTreeBackground()
        }

        val content = ContentFactory.getInstance().createContent(panel, "", false).apply {
            preferredFocusableComponent = textArea
        }
        toolWindow.contentManager.addContent(content)
        ApplicationManager.getApplication().messageBus.connect(content).subscribe(LafManagerListener.TOPIC, LafManagerListener {
            updateTextAreaAppearance()
            updateHeaderAppearance()
            panel.background = UIUtil.getTreeBackground()
            panel.repaint()
        })
    }
}
