# Rust Paint Migration TODO

## Phase 1: Project Setup and UI Shell
- [x] Create project structure under `rust/`
- [x] Copy assets (icons) to `rust/assets/`
- [x] Configure `Cargo.toml` with `eframe`, `egui`, `image`, etc.
- [x] Implement basic `eframe` application shell
- [x] Create Side Panel for tools (Pencil, Eraser, etc.)
- [x] Create Central Panel for drawing canvas
- [x] Create Bottom/Top Panel for Ribbon (Stroke size, Color picker)
- [x] Create Status Bar

## Phase 2: Drawing Core
- [x] Implement `Canvas` state with a backing image buffer
- [x] Mouse event handling for drawing
- [x] Tool logic:
    - [x] Pencil
    - [x] Line
    - [x] Rectangle (and Filled)
    - [x] Oval (and Filled)
    - [x] Rounded Rectangle (and Filled)
    - [x] Eraser
    - [x] Highlighter
    - [x] Arrow
- [x] Stroke size and Opacity support

## Phase 3: Advanced Tools & Features
- [x] Flood Fill (Bucket tool) implementation in Rust
- [x] Text tool (Input and rendering on canvas)
- [x] Move tool (Selection and dragging)
- [x] Undo/Redo history (Layer snapshotting)

## Phase 4: IO and Integration
- [x] Save as PNG
- [x] Open image file
- [x] Command line argument support for opening files
- [x] Native styling/themes (Light/Dark mode)

## Phase 5: Verification
- [x] Match UI/UX with the original Java version
- [x] Performance optimization for large canvases
- [x] Final bug fixes
- [x] Keyboard shortcuts parity (Ctrl+N/O/S, Undo/Redo, Copy/Paste/Select All, Crop)
- [x] Menu parity with Java (File/Edit/Tools/Text/Help, About dialog)
