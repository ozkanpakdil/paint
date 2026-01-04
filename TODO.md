# Migration of Paint Project to Rust Tauri

This document outlines the steps to replicate the Java Paint application in Rust using Tauri.

## Project Structure
- **Frontend**: HTML5 Canvas with Vanilla JS/TypeScript for drawing logic and UI components.
- **Backend**: Rust (Tauri) for file system operations, native menus, and potential performance-critical logic.

## Step 1: Frontend UI Setup
- [x] Create main layout using HTML/CSS (Skeleton created in `index.html` and `styles.css`).
    - [x] Top Menu Bar (File, Edit, Tools, Help).
    - [x] Left Sidebar for Tools and Shapes.
    - [x] Center Canvas Area with scrollable container.
    - [x] Bottom Ribbon Bar for Size and Opacity sliders.
    - [x] Bottom Color Palette.
    - [x] Status Bar with Canvas dimensions and "Resize" button.
- [x] Implement responsive design to match the Java UI look and feel (FlatLaf style).

## Step 2: Drawing Logic (Frontend)
- [x] Initialize HTML5 Canvas and context.
- [x] Implement drawing tools:
    - [x] **Pencil**: Freehand drawing.
    - [x] **Highlighter**: Semi-transparent freehand drawing.
    - [x] **Eraser**: Clearing pixels.
    - [x] **Line**: Straight lines.
    - [x] **Shapes**: Rectangle, Oval, Arrow.
    - [x] **Shapes**: Polygon.
    - [x] **Shapes**: Rounded Rectangle (Outlined & Filled).
    - [x] **Crop**: Crop to Selection and Crop to Image Size.
    - [x] **Text**: Inline text editor.
    - [x] **Bucket (Flood Fill)**: Implement scanline flood fill algorithm.
- [x] Implement Move tool and selection logic.
- [x] Implement Undo/Redo system using a state stack (snapshot-based).

## Step 3: Backend Integration (Rust/Tauri)
- [x] Set up Tauri commands for:
    - [x] **Opening Files**: Use native file dialog to read image data and send to frontend.
    - [x] **Saving Files**: Use native file dialog to save canvas data to disk.
- [x] Configure `tauri.conf.json`:
    - [x] Set window title to "Paint".
    - [x] Configure permissions for file system access (Corrected for Tauri 2).
    - [x] Add icons and branding.

## Step 4: Asset Migration
- [x] Copy all icons from `src/main/resources/images/` to `rust/src/assets/images/`.
- [x] Kept Java `.ucls`, `.useq`, and `.uml` files for reference (no conversion needed).

## Step 5: Advanced Features
- [x] Implement Paste from Clipboard.
- [x] Implement Drag and Drop support for images.
- [x] Port any specific Java logic that might be missing (e.g., custom cursor management).
- [x] Implement missing keyboard shortcuts (Ctrl+C, Ctrl+A, Ctrl+M, Ctrl+Q).
- [x] Implement "Select All" functionality.
- [x] Implement "Copy to Clipboard" support.

## Step 6: Testing & Verification
- [x] Port existing tests to Vitest (frontend).
- [x] Verify UI consistency against the Java version.
- [x] Ensure cross-platform compatibility (Windows, macOS, Linux).
- [x] Refactor drawing logic into modular `drawing.js` and `canvasActions.js`.

## Step 7: UI Polish & Bug Fixes
- [x] Make UI more compact to match Java version
    - [x] Reduce menu bar padding (1px 6px) and font size (12px)
    - [x] Reduce toolbar width to 68px with 28x28px buttons
    - [x] Reduce ribbon bar padding (2px 8px) and gaps (10px)
    - [x] Reduce status bar padding (2px 8px) and gaps (8px)
    - [x] Reduce color swatches to 18x18px
- [x] Fix highlighter opacity initialization (default to 30% instead of 100%)
- [x] Fix opacity persistence (reset to 100% when switching from highlighter to other tools)
- [x] Fix image paste functionality (added preventDefault and null checks)
- [x] Fix text tool (improved positioning and styling)
- [x] Fix exit behavior (added confirmation dialog for Exit menu and Ctrl+Q)
