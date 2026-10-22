# Global Notes Changelog

## [1.1.0] - 2026-10-22

### Added

- Search within the current note, with case-insensitive matching, highlighted results, and an active match that stays visible while the search field has focus.
- A single-row search bar with a match counter that stays the same width while navigating results.
- Previous/next result navigation with wraparound using left/right arrow buttons, Enter/Shift+Enter, or F3/Shift+F3.
- Open search with the IDE's Find shortcut or the header search button; close it with Escape or by clicking the search button again.
- Live search updates when editing text or switching notes.

### Changed

- Matched the header button widths to the search navigation buttons.
- Replaced hard-coded note-title colors with the IDE's label color, bold font, and panel background.
- Simplified the README to focus on the main features.

## [1.0.4] - 2026-09-18

### Changed

- Sorted the notes menu by most recently used, with the active note first.

## [1.0.3] - 2026-09-02

### Fixed

- Made Tab indent multi-line selections instead of replacing the selected text; Shift+Tab now outdents them.
- Made the notes menu close when its chevron button is clicked again.
- Removed compiler-generated references to deprecated and experimental `ToolWindowFactory` compatibility methods.

## [1.0.2] - 2026-08-31

### Changed

- Increased the notes-menu action spacing and dropdown-button hit area.
- Matched the notes tool window background to the active IDE theme's Project-tree background.

## [1.0.1] - 2026-08-27

### Fixed

- Made the active-note indicator clearly visible in the notes menu in both light and dark themes.

## [1.0.0] - 2026-08-27

### Added

- Persistent notes shared across projects in the IDE.
- Multiple named notes with one active note available for editing.
- Note creation, selection, renaming, and deletion.
- Automatic saving of note text and the active-note selection.
- Theme-aware editor colors, caret, selection, and note header.
- Light and dark plugin logos and tool-window icons.
