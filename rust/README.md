# Paint (Rust Tauri)

This is the Rust/Tauri implementation of the Paint application. It aims to provide the same functionality and look-and-feel as the original Java version while benefiting from native performance and a modern web-based frontend.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js** (and npm)
- **Rust** (via rustup)
- **Tauri Prerequisites**: Follow the [Tauri Getting Started](https://tauri.app/start/prerequisites/) guide for your operating system (Windows, macOS, or Linux).

## Getting Started

1.  **Install Dependencies**:
    Navigate to the `rust` directory and install the Node.js dependencies.
    ```bash
    # Debian Linux Prerequisites
    sudo apt update
    sudo apt install libwebkit2gtk-4.1-dev \
    build-essential \
    curl \
    wget \
    file \
    libxdo-dev \
    libssl-dev \
    libayatana-appindicator3-dev \
    librsvg2-dev
    
    cd rust
    npm install
    ```

2.  **Run in Development Mode**:
    Start the application in development mode with hot-reloading for the frontend.
    ```bash
    npm run tauri dev
    ```

## Building for Native Platforms

To build a production-ready native executable, run the following command:

```bash
npm run tauri build
```

The build artifacts (installers and binaries) will be located in `src-tauri/target/release/bundle/`.

## Running Tests

This project uses **Vitest** for frontend testing.

```bash
npm test
```

## Project Structure

- `src/`: Frontend code (HTML, CSS, Vanilla JS).
- `src-tauri/`: Backend Rust code and Tauri configuration.
- `src/assets/images/`: Icons and images migrated from the Java version.
