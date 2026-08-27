package com.spavlov.globalnotes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(
    name = "GlobalNotesState",
    storages = [Storage("GlobalNotes.xml")]
)
class NotesService : PersistentStateComponent<NotesService.NotesState> {

    private var notesState = NotesState()

    data class NotesState(
        var notes: MutableList<NoteState> = mutableListOf(),
        var selectedNoteId: String = "",
        // Kept for migration from versions that stored one global text value.
        var text: String = ""
    )

    data class NoteState(
        var id: String = "",
        var name: String = "",
        var text: String = ""
    )

    init {
        migrateLegacyState()
    }

    val notes: List<NoteState>
        get() = notesState.notes

    var selectedNoteId: String
        get() = notesState.selectedNoteId
        private set(value) {
            notesState.selectedNoteId = value
        }

    val selectedNote: NoteState
        get() = notesState.notes.first { it.id == selectedNoteId }

    fun selectNote(id: String) {
        if (notesState.notes.any { it.id == id }) {
            selectedNoteId = id
        }
    }

    fun addNote(name: String): NoteState {
        val note = NoteState(newId(), name.trim().ifEmpty { "Untitled note" })
        notesState.notes.add(note)
        selectedNoteId = note.id
        return note
    }

    fun renameNote(id: String, name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        return notesState.notes.firstOrNull { it.id == id }?.let {
            it.name = trimmedName
            true
        } ?: false
    }

    fun deleteNote(id: String): Boolean {
        if (notesState.notes.size <= 1) return false
        val index = notesState.notes.indexOfFirst { it.id == id }
        if (index < 0) return false
        notesState.notes.removeAt(index)
        if (selectedNoteId == id) {
            selectedNoteId = notesState.notes[index.coerceAtMost(notesState.notes.lastIndex)].id
        }
        return true
    }

    var text: String
        get() = selectedNote.text
        set(value) {
            selectedNote.text = value
        }

    private fun migrateLegacyState() {
        if (notesState.notes.isEmpty()) {
            val note = NoteState(newId(), "Note 1", notesState.text)
            notesState.notes.add(note)
            notesState.selectedNoteId = note.id
            notesState.text = ""
        } else if (notesState.notes.none { it.id == notesState.selectedNoteId }) {
            notesState.selectedNoteId = notesState.notes.first().id
        }
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString()

    override fun getState(): NotesState = notesState

    override fun loadState(state: NotesState) {
        notesState = state
        migrateLegacyState()
    }

    companion object {
        fun getInstance(): NotesService = service()
    }
}
