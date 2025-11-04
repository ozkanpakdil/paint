Paint
=======

Paint Application in Java using Swing Components.

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
     -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
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
