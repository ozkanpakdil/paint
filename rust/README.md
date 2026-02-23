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

### Packaging and Signing for macOS
For detailed instructions on how to create a signed `.app` bundle and `.dmg` for macOS, see [MACOS_SIGNING.md](../MACOS_SIGNING.md).

## Features
- Drawing tools: Pencil, Line, Rectangles, Ovals, Rounded Rectangles, Eraser, Bucket Fill, Highlighter, Arrow, and Text.
- UI: Sidebar for tool selection, Color selection, Stroke size selection.
- Advanced: Undo/Redo, File Save (PNG), Selection & Move, Clipboard operations, and Cropping.

---

![Preview](https://github.com/user-attachments/assets/28a850a8-d472-4914-8eff-3e756bc3c3c7)

## macOS runtime requirements & troubleshooting

- GTK cannot be fully statically linked on macOS when using Homebrew/gtk4-rs. The correct approach is to ship a self‑contained `.app` that bundles the non‑system `.dylib`s.
- Preferred: build the `.app` with the provided script so dependencies are bundled under `Contents/Frameworks`:
  ```bash
  cd rust
  ./build_macos.sh
  open target/release/RustPaint.app
  ```
- If you run the raw CLI binary directly (outside of the `.app`) and see an error like:
  ```
  Library not loaded: /opt/homebrew/.../libgtk-4.1.dylib
  Reason: tried: '/opt/homebrew/...'
  ```
  then install GTK4 locally:
  ```bash
  brew install gtk4
  ```
  After installing, re‑launch the program, or use the `.app` bundle created by the script above.

Notes
- Showing a dialog for the above error is not possible: the process fails in the macOS dynamic loader (dyld) before the application code runs. That’s why we either bundle the libraries in the `.app` or ensure GTK4 is installed on the target Mac.
