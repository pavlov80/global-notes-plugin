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
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class NotesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val notesService = NotesService.getInstance()
        var updatingEditor = false

        val textArea = JBTextArea(notesService.text).apply {
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        }

        val selectedNoteLabel = JBLabel().apply {
            border = BorderFactory.createEmptyBorder(0, 8, 0, 4)
        }
        val notesButton = JButton(AllIcons.General.ChevronDown).apply {
            toolTipText = "Choose, add, rename, or delete a note"
            margin = Insets(0, 0, 0, 0)
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            preferredSize = Dimension(24, 24)
            minimumSize = Dimension(24, 24)
            maximumSize = Dimension(24, 24)
        }
        val header = JPanel(BorderLayout()).apply {
            add(selectedNoteLabel, BorderLayout.CENTER)
            add(notesButton, BorderLayout.EAST)
            border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
        }

        fun updateHeaderAppearance() {
            val isDarkTheme = !JBColor.isBright()
            selectedNoteLabel.foreground = if (isDarkTheme) Color(180, 224, 189) else Color(36, 88, 49)
            header.background = if (isDarkTheme) Color(45, 61, 49) else Color(234, 246, 234)
            header.repaint()
        }

        fun updateTextAreaAppearance() {
            val editorScheme = EditorColorsManager.getInstance().globalScheme
            val editorFont = editorScheme.getFont(EditorFontType.PLAIN).deriveFont(
                editorScheme.getFont(EditorFontType.PLAIN).size2D + 1f
            )
            textArea.font = editorFont
            textArea.foreground = editorScheme.defaultForeground
            textArea.background = editorScheme.defaultBackground
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

        fun showNotesMenu(anchor: Component) {
            val menu = JPopupMenu()
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
                menu.add(item)
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
            menu.add(addItem)

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
            menu.add(renameItem)

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
            menu.add(deleteItem)
            menu.show(anchor, 0, anchor.height)
        }

        notesButton.addActionListener { showNotesMenu(notesButton) }

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
        }

        val content = ContentFactory.getInstance().createContent(panel, "", false).apply {
            preferredFocusableComponent = textArea
        }
        toolWindow.contentManager.addContent(content)
        ApplicationManager.getApplication().messageBus.connect(content).subscribe(LafManagerListener.TOPIC, LafManagerListener {
            updateTextAreaAppearance()
            updateHeaderAppearance()
        })
    }
}
