# Rust Paint (GTK4)

This is a Rust implementation of a Paint application using GTK4.

## Installation

To build and run this project, you need to install the following system dependencies:

### macOS (using Homebrew)
```bash
brew install pkg-config gtk4 cairo
```

### Ubuntu/Debian
```bash
sudo apt-get install pkg-config libgtk-4-dev libcairo2-dev
```

### Fedora
```bash
sudo dnf install pkgconf-pkg-config gtk4-devel cairo-devel
```

## Build and Run

Once the dependencies are installed, navigate to the `rust` directory and use `cargo`:

```bash
cd rust
cargo run
```

## Features
- Drawing tools: Pencil, Line, Rectangles, Ovals, Rounded Rectangles, Eraser, Bucket Fill, Highlighter, Arrow, and Text.
- UI: Sidebar for tool selection, Color selection, Stroke size selection.
- Advanced: Undo/Redo, File Save (PNG), Selection & Move, Clipboard operations, and Cropping.

---

![Preview](https://github.com/user-attachments/assets/28a850a8-d472-4914-8eff-3e756bc3c3c7)