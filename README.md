Paint
=======

Paint Application in Java using Swing Components.
<img width="1304" height="990" alt="Image" src="https://github.com/user-attachments/assets/7bb794af-6295-4570-a230-c3bc3413e9fd" />

Following are the features implemented.
* Bucket Tool ( Using **Flood Fill Algorithm**)
*  Foreground Color chooser ( Using **JFileChooser**)
*  Save as PNG image.
*  Pencil Tool.
*  Eraser Tool.
*  Text Tool ( Using **JOptionPane** )
*  Shapes Supported- Rectangle , Rounded Rectangles , Ovals , Line .
*  Stroke Size Selection (Using **JSlider**).

Build and run with Maven
------------------------

Requirements:
- Java 8+ (JDK)
- Maven 3.6+

Commands:

- Compile:
  ```bash
  mvn -q clean compile
  ```

- Package runnable JAR:
  ```bash
  mvn -q clean package
  ```
  The artifact will be created at `target/paint-1.0.0.jar` and is executable:
  ```bash
  java -jar target/paint-1.0.0.jar
  ```

Notes:
- Images are loaded from classpath under `src/main/resources/images`, so the app runs correctly from the built JAR.
- Source code is under `src/main/java` and resources under `src/main/resources` following standard Maven layout.


Build as a native binary with GraalVM
-------------------------------------

Requirements:
- GraalVM JDK (22+ recommended) installed and on PATH
- `native-image` installed (`gu install native-image`)
- Linux or macOS recommended for GUI native images (AWT/Swing). On Linux make sure X11/GTK libs are present.
- Windows should have https://aka.ms/vs/17/release/vc_redist.x64.exe for native-image to work.

Steps:
1) Point Maven to GraalVM (either set `JAVA_HOME` or run Maven from the GraalVM installation):
   ```bash
   export JAVA_HOME=/path/to/graalvm
   export PATH="$JAVA_HOME/bin:$PATH"
   gu install native-image   # if not already installed
   ```
2) Build the native executable using the provided Maven profile:
   ```bash
   mvn -Pnative -DskipTests -q clean package
   ```
   This invokes the GraalVM Native Build Tools plugin and produces a binary at:
   ```
   target/paint
   ```
3) Run the app:
   ```bash
   ./target/paint
   ```

Notes for native-image:
- Resources under `src/main/resources/images` are included automatically in the native image (configured via the plugin).
- If you move the `Main` class into a package, update the `graalvm.native.mainClass` property in `pom.xml`.
- Windows support exists but may require additional setup for GUI apps; Linux/macOS tend to be smoother for AWT/Swing.


Collect metadata with GraalVM tracing agent (recommended)
--------------------------------------------------------

The safest way to build GUI apps as native images is to collect runtime metadata (reflection/JNI/resources) using the GraalVM tracing agent, then rebuild the native image using the generated configs.

Prereqs:
- Use GraalVM as your JDK and have `native-image` installed.
- Run in a desktop session (on Linux, `$DISPLAY` must be set; make sure X11/Wayland is available).

Steps:
1) Build the runnable JAR first:
   ```bash
   mvn -q -DskipTests clean package
   ```
2) Run the app with the agent enabled and exercise the UI (open windows, click tools, add text, pick colors, save dialog, etc.). The agent will write configuration files under `src/main/resources/META-INF/native-image` so they are picked up on the next build.
   ```bash
   export JAVA_HOME=/path/to/graalvm
   export PATH="$JAVA_HOME/bin:$PATH"
   java \
     -Djava.awt.headless=false \
     -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image \
     -jar target/paint-1.0.0.jar

   ```
   - When you are done trying features, close the app. You should see files like `reflect-config.json`, `jni-config.json`, `resource-config.json`, etc. under `src/main/resources/META-INF/native-image/`.
3) Rebuild the native image using the collected configs:
   ```bash
   mvn -Pnative -DskipTests -q clean package
   ./target/paint
   ```

Troubleshooting
---------------
- If the native binary fails to start with AWT errors, verify your GUI environment:
  ```bash
  echo $DISPLAY
  echo $XDG_SESSION_TYPE
  which xeyes >/dev/null 2>&1 || sudo apt-get install -y x11-apps # optional check on Debian/Ubuntu
  ```
- Ensure you ran the app with the agent and exercised key features so configs are generated.
- If problems persist, run the native binary with `GRAALVM_OPTIONS` to increase logging:
  ```bash
  GRAALVM_OPTIONS="-Djava.awt.headless=false" ./target/paint
  ```
- Share the first ~100 lines of output along with your distro and session type (x11/wayland).

# Paint

Simple Swing paint application.

## Build

- Standard jar: `mvn -B -DskipTests package`
- GraalVM native (requires GraalVM + native-image): `mvn -B -Pnative -DskipTests package`

## Installers with jpackage

You can generate OS-level installers (bundled runtime) using `jpackage` via Maven.

Prerequisites per OS:
- Linux (Debian/Ubuntu): `sudo apt-get install fakeroot rpm` (rpm only if building RPM additionally)
- Windows: Install WiX Toolset (v3.x) and make sure it's on PATH
- macOS: Xcode command line tools (for codesign if you plan to sign)

Commands:
- One-shot (recommended): cleans, builds the jar, then runs jpackage in one go:
  - Linux (DEB): `mvn -B -Pinstaller -Dinstaller.type=DEB -DskipTests clean package jpackage:jpackage`
  - macOS (DMG): `mvn -B -Pinstaller -Dinstaller.type=DMG -DskipTests clean package jpackage:jpackage`
  - Windows (MSI): `mvn -B -Pinstaller -Dinstaller.type=MSI -DskipTests clean package jpackage:jpackage`

- Two-step (alternative):
  1) Build jar: `mvn -B -DskipTests clean package`
  2) Build installer: `mvn -B -Pinstaller -Dinstaller.type=<DEB|DMG|MSI> -DskipTests jpackage:jpackage`

Outputs are written to `target/installers`.

Tip: Installer types are case-insensitive in jpackage. You can use `DEB/deb`, `DMG/dmg`, or `MSI/msi`.

Notes:
- Installers are unsigned by default. To sign/notarize on macOS or sign on Windows, configure your local environment and add the appropriate `jpackage` options in `pom.xml` (codesign not yet configured in this project).
- The app bundles a trimmed runtime (`java.base`, `java.desktop`) built via `jlink`.

## CI

Two GitHub Actions workflows are provided:
- `.github/workflows/graalvm-native-build.yml` builds GraalVM native executables on Linux, macOS, and Windows, and uploads artifacts.
- `.github/workflows/jpackage-installers.yml` builds platform-specific installers using `jpackage` on Linux (DEB), macOS (DMG), and Windows (MSI), and uploads artifacts.

Development Release uploads:
- On pushes to `main/master`, tags `v*`, and manual dispatch (not on PRs), both workflows also publish their outputs to a rolling GitHub Release with tag `development` (marked as a prerelease).
- What you get there:
  - Native builds: a packaged archive per OS (`tar.gz` on Linux/macOS, `zip` on Windows) and the raw binary/exe when present.
  - Installers: all files from `target/installers/**` (e.g., `.deb`, `.dmg`, `.msi`).
- Find them at: GitHub → Releases → "Development Build" (tag `development`).


### Formatting the DEB description (multi-line and blank lines)

The Debian control file has strict rules for the `Description` field:
- First line is a short synopsis (no leading space).
- Subsequent lines must start with a single space.
- A blank line must be represented as a line containing a single space and a dot: ` .`

When authoring the description in `src/main/resources/installer.properties`, use literal `\n` to insert newlines and include the required leading space after each newline. To insert a blank line, use `\n .\n`.

Example (used by this project):
```
app.description=Paint - simple Swing paint application\n A simple Swing paint program with common drawing tools.\n - Pencil\n - Eraser, lines/rectangles/ovals/polygons (stroke or filled)\n - Bucket fill\n - Text tool with font and size selection\n - Color palette and custom colors\n - Adjustable stroke width\n - Image open/save.\n .\n Project website: https://github.com/ozkanpakdil/paint
```

This ensures `jpackage` produces a DEB with a properly formatted multi-line description and a paragraph break before the website line.
