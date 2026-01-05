# TODO: Rust Paint App (GTK4)

## Project Overview
Porting a Java Swing paint application to Rust using GTK4.

## Progress
- [x] Scan Java code and understand logic
- [x] Initialize Rust project with GTK4 dependencies
- [x] Create basic window and drawing canvas
- [x] Port drawing tools logic
    - [x] Pencil
    - [x] Line
    - [x] Rectangles (Empty and Filled)
    - [x] Ovals (Empty and Filled)
    - [x] Eraser
    - [x] Bucket Fill
    - [x] Rounded Rectangle (Empty and Filled)
    - [x] Text
    - [x] Highlighter
    - [x] Arrow
- [x] Port UI components
    - [x] Sidebar with Tool selection
    - [x] Color Selection
    - [x] Stroke Size
- [x] Implement Undo/Redo history
- [x] Implement File I/O (Save, New)
- [x] Implement Clipboard operations (Copy/Paste, Select All)
- [x] Implement Cropping functionality
    - [x] Crop to Image Size
    - [x] Crop to Selection
- [x] Port Selection and Move logic
- [x] Final polishing and testing
- [x] Implement GitHub Actions for multi-platform installers (DEB, RPM, DMG, MSI)

## Implementation History
- 2026-01-05: Scanned Java codebase.
- 2026-01-05: Created TODO.md.
- 2026-01-05: Initialized Rust project with GTK4.
- 2026-01-05: Implemented Pencil and Eraser tools.
- 2026-01-05: Implemented Line, Rectangle, and Oval tools with previews.
- 2026-01-05: Ported Bucket Fill tool from Java to Rust.
- 2026-01-05: Implemented Undo/Redo history mechanism.
- 2026-01-05: Added Color and Stroke Size selection.
- 2026-01-05: Implemented File "New" and "Save as PNG" functionality.
- 2026-01-05: Fixed project directory structure.
- 2026-01-05: Implemented Crop, Clipboard, and Selection logic.
- 2026-01-05: Added missing tools (Rounded Rect, Highlighter, Arrow, Text).
- 2026-01-05: Completed porting all core features from Java to Rust.
