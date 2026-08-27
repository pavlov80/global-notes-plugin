# Global Notes

Global Notes is an IntelliJ Platform plugin that provides a persistent, IDE-wide notes tool window. Keep short reminders, working notes, project context, or anything else you want available while you work.

## Features

- Create and maintain multiple named notes.
- Select one active note to edit at a time.
- Add, rename, and delete notes from the **Notes** menu in the tool-window header.
- Automatically save note content and the active-note selection.
- Keep notes globally available across projects and IDE restarts.
- Use editor-theme-aware colors, font, and background for a comfortable writing area in both light and dark themes.

## Using the plugin

1. Open the **Global Notes** tool window from the right-hand tool-window bar.
2. Write in the active note; changes save automatically.
3. Click **Notes** in the header to choose another note or manage notes.
4. Select **Add note...** to create a note, **Rename current note...** to rename it, or **Delete current note** to remove it.

At least one note is always kept, so there is always an active place to write.

## Development

The project is written in Kotlin and uses the IntelliJ Platform Gradle Plugin. It currently targets IntelliJ IDEA `2025.3.5`.

### Build

Run the Gradle wrapper from the project root:

```powershell
.\gradlew.bat build
```

### Run in a sandbox IDE

```powershell
.\gradlew.bat runIde
```

This starts a separate IntelliJ IDEA instance with Global Notes installed for testing.

## Project layout

```
src/main/kotlin/com/spavlov/globalnotes/
  NotesService.kt             Persisted note storage and note-management logic
  NotesToolWindowFactory.kt   Global Notes tool-window interface
src/main/resources/META-INF/
  plugin.xml                  Plugin metadata and tool-window registration
```

## License

License information has not yet been added to this repository.
