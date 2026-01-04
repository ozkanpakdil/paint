package io.github.ozkanpakdil.paint;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
// macOS application integration (Java 9+)
import java.awt.desktop.*;

public class Main extends JFrame {
    private GUI gui;

    public Main() throws IOException {
        this(null);
    }

    public Main(String filename) throws IOException {
        initializeGUI();
        Menu();
        initializeWindow();

        // If a filename was provided, attempt to open it
        if (filename != null && !filename.isEmpty()) {
            openImageFile(filename);
        }
    }

    public static void main(String[] args) {
        // Workaround for GraalVM native image: set encoding early before any native library loading
        if (System.getProperty("file.encoding") == null) {
            System.setProperty("file.encoding", "UTF-8");
        }

        // GraalVM native image requires java.home and fontconfig.properties for font initialization
        setupFontConfigForNativeImage();

        System.setProperty("java.awt.headless", "false");
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("""
                    This application requires a graphical desktop session. Headless mode detected.
                    Hint: On Linux, ensure you are running under X11/Wayland and that the DISPLAY variable is set (e.g., :0).
                    Example: export DISPLAY=:0""");
            System.exit(1);
        }

        // Setup modern, OS-aware look and feel (FlatLaf) with robust fallback for GraalVM native-image
        initLookAndFeelWithFallback();

        // Check if a filename was provided as command-line argument
        String fileToOpen = (args.length > 0) ? args[0] : null;

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                new Main(fileToOpen);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Initialize Look & Feel in a way that is resilient under GraalVM native-image.
     */
    private static void initLookAndFeelWithFallback() {
        // Anti-aliasing hints regardless of LAF
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");

        // Try FlatLaf (works in both regular JVM and native-image)
        boolean flatOk = false;
        try {
            boolean useDark = isDarkThemePreferred();
            if (useDark) {
                FlatDarkLaf.setup();
                UIManager.setLookAndFeel(new FlatDarkLaf());
                System.setProperty("swing.defaultlaf", "com.formdev.flatlaf.FlatDarkLaf");
            } else {
                FlatLightLaf.setup();
                UIManager.setLookAndFeel(new FlatLightLaf());
                System.setProperty("swing.defaultlaf", "com.formdev.flatlaf.FlatLightLaf");
            }
            // Ensure UIManager defaults are loaded
            UIManager.getLookAndFeelDefaults();
            flatOk = true;
        } catch (Throwable t) {
            System.err.println("FlatLaf initialization failed: " + t.getMessage());
            flatOk = false;
        }

        // Verify that essential UI defaults are present; if not, switch to Metal
        if (!flatOk || !hasEssentialUIDefaults()) {
            try {
                // Cross-platform (Metal) LAF as fallback
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                System.setProperty("swing.defaultlaf", UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception e) {
                // As a last resort, try system LAF
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignore) {
                    // give up; Swing may still work if defaults are lazily initialized later
                }
            }
        }
    }
    
    /**
     * Setup font configuration for GraalVM native-image.
     * Extracts fontconfig.properties from resources to a temp directory
     * and sets java.home to point there.
     */
    private static void setupFontConfigForNativeImage() {
        if (System.getProperty("java.home") != null) {
            return; // Already set, don't override
        }

        try {
            // Create a temporary directory structure: tempdir/lib/fontconfig.properties
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("paint-fonts");
            java.nio.file.Path libDir = tempDir.resolve("lib");
            java.nio.file.Files.createDirectories(libDir);

            // Copy fontconfig.properties from resources to temp lib directory
            try (java.io.InputStream is = Main.class.getResourceAsStream("/lib/fontconfig.properties")) {
                if (is != null) {
                    java.nio.file.Files.copy(is, libDir.resolve("fontconfig.properties"));
                }
            }

            // Set java.home to our temp directory
            System.setProperty("java.home", tempDir.toString());

            // Register shutdown hook to clean up temp files
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    java.nio.file.Files.deleteIfExists(libDir.resolve("fontconfig.properties"));
                    java.nio.file.Files.deleteIfExists(libDir);
                    java.nio.file.Files.deleteIfExists(tempDir);
                } catch (Exception ignored) {}
            }));
        } catch (Exception e) {
            // Fallback: set java.home to current directory
            System.setProperty("java.home", System.getProperty("user.dir", "."));
        }
    }

    /**
     * Detect if running inside a GraalVM native-image.
     */
    private static boolean isRunningInNativeImage() {
        // Check for GraalVM native-image specific property
        String imageName = System.getProperty("org.graalvm.nativeimage.imagecode");
        if (imageName != null) {
            return true;
        }
        // Alternative check: native-image sets this class
        try {
            Class.forName("org.graalvm.nativeimage.ImageInfo");
            return (Boolean) Class.forName("org.graalvm.nativeimage.ImageInfo")
                    .getMethod("inImageCode")
                    .invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }
    

    private static boolean hasEssentialUIDefaults() {
        try {
            UIDefaults d = UIManager.getDefaults();
            return d.get("PanelUI") != null
                    && d.get("LabelUI") != null
                    && d.get("SpinnerUI") != null
                    && d.get("FormattedTextFieldUI") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isDarkThemePreferred() {
        // 1) Allow user override via system property or env var
        String override = System.getProperty("paint.theme");
        if (override == null) override = System.getenv("PAINT_THEME");
        if (override != null) {
            return override.equalsIgnoreCase("dark") || override.equalsIgnoreCase("darcula");
        }

        // 2) Try FlatLaf Extras (Windows/macOS OS dark mode) via reflection if present
        try {
            Class<?> flatDesktop = Class.forName("com.formdev.flatlaf.extras.FlatDesktop");
            boolean supported = (Boolean) flatDesktop.getMethod("isDarkThemeSupported").invoke(null);
            if (supported) {
                return (Boolean) flatDesktop.getMethod("isDarkThemeEnabled").invoke(null);
            }
        } catch (Throwable ignore) {
            // extras not on classpath or not supported on this OS
        }

        // 3) Linux heuristic: check GTK theme name for "dark"
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            String gtk = execCmd("gsettings", "get", "org.gnome.desktop.interface","color-scheme");
            return gtk != null && gtk.toLowerCase().contains("dark");
        }

        return false;
    }

    static String execCmd(String... cmd) {
        try {
            return new String(new ProcessBuilder(cmd).start().getInputStream().readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }


    public void initializeGUI() throws IOException {
        gui = new GUI();
        add(gui);
    }

    public void initializeWindow() {
        setTitle("Paint");
        setName("mainFrame");
        setSize(1200, 640);
        // Ask for confirmation on close via window listener
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmAndExit();
            }
        });

        // On macOS, intercept Cmd+Q (application quit) and show the same confirmation dialog.
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac") && Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                // Register a QuitHandler if the API is available
                desktop.setQuitHandler((QuitEvent e, QuitResponse response) -> {
                    if (confirmExitApproved()) {
                        // Dispose and allow the OS to quit the app
                        try { dispose(); } catch (Throwable ignore) {}
                        response.performQuit();
                    } else {
                        response.cancelQuit();
                    }
                });
            }
        } catch (UnsupportedOperationException | SecurityException ignore) {
            // Best-effort: if not supported, default behavior remains
        }

        // Set app/window icon so Alt-Tab/taskbar shows our custom icon instead of the Java Duke
        try {
            URL iconUrl = getClass().getResource("/images/app.png");
            if (iconUrl != null) {
                BufferedImage icon = ImageIO.read(iconUrl);
                if (icon != null) {
                    // Window icon (affects Windows taskbar and many Linux WMs)
                    setIconImage(icon);
                    // Taskbar/Dock icon (Java 9+) where supported (e.g., macOS Dock, some Linux desktops)
                    try {
                        if (Taskbar.isTaskbarSupported()) {
                            Taskbar taskbar = Taskbar.getTaskbar();
                            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                                taskbar.setIconImage(icon);
                            }
                        }
                    } catch (UnsupportedOperationException | SecurityException ignore) {
                        // Best-effort; safely ignore if not allowed/supported
                    }
                }
            }
        } catch (IOException ignore) {
            // If the icon can't be loaded, continue without failing the app
        }

        setLocation(100, 0);
        setResizable(true);
        setVisible(true);
    }

    public void Menu() {
        JMenuBar jMenuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenu edit = new JMenu("Edit");
        JMenu tools = new JMenu("Tools");
        JMenu textMenu = new JMenu("Text");
        JMenu help = new JMenu("Help");
        tools.setMnemonic(KeyEvent.VK_T);
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setMnemonic(KeyEvent.VK_E);
        exitMenuItem.setToolTipText("Exit ");
        // Add platform-aware accelerator (Ctrl+Q on Windows/Linux, Cmd+Q on macOS)
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuMask));
        exitMenuItem.addActionListener(_ -> confirmAndExit());

        JMenuItem newMenuItem = new JMenuItem("New");
        newMenuItem.setMnemonic(KeyEvent.VK_N);
        newMenuItem.setToolTipText("New");
        newMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newMenuItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().clearCanvas();
        });

        JMenuItem saveMenuItem = new JMenuItem("Save");
        saveMenuItem.setToolTipText("Save current image as PNG");
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveMenuItem.addActionListener(_ -> {
            if (gui != null) gui.getSideMenu().triggerSave();
        });

        // Crop to Image Size menu item
        JMenuItem cropMenuItem = new JMenuItem("Crop to Image Size");
        cropMenuItem.setName("cropToImage");
        cropMenuItem.setToolTipText("Crop canvas to the last pasted image size");
        cropMenuItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().cropToImageSize();
        });
        edit.add(cropMenuItem);

        // Crop to Selection menu item
        JMenuItem cropSelItem = new JMenuItem("Crop to Selection");
        cropSelItem.setName("cropToSelection");
        int menuMask2 = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        cropSelItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask2 | KeyEvent.SHIFT_DOWN_MASK));
        cropSelItem.setToolTipText("Crop canvas to the current selection rectangle");
        cropSelItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().cropToSelection();
        });
        edit.add(cropSelItem);

        // Copy menu item
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().copyToClipboard();
        });
        edit.add(copyItem);

        // Select All menu item
        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        selectAllItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().selectAll();
        });
        edit.add(selectAllItem);

        // Edit > Undo / Redo
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setToolTipText("Undo the last action");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().undo();
        });
        edit.add(undoItem);

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setToolTipText("Redo the last undone action");
        // Primary accelerator Ctrl+Y (Ctrl+Shift+Z also works via canvas handlers)
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(_ -> {
            if (gui != null) gui.getDrawArea().redo();
        });
        edit.add(redoItem);

        // Tools > All tool selectors to mirror the RibbonBar
        JMenuItem pencilToolItem = new JMenuItem("Pencil");
        pencilToolItem.setToolTipText("Select Pencil tool");
        pencilToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.PENCIL); });
        tools.add(pencilToolItem);

        JMenuItem lineToolItem = new JMenuItem("Line");
        lineToolItem.setToolTipText("Select Line tool");
        lineToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.LINE); });
        tools.add(lineToolItem);

        JMenuItem rectToolItem = new JMenuItem("Rectangle");
        rectToolItem.setToolTipText("Select Rectangle tool");
        rectToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.RECT); });
        tools.add(rectToolItem);

        JMenuItem ovalToolItem = new JMenuItem("Oval");
        ovalToolItem.setToolTipText("Select Oval tool");
        ovalToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.OVAL); });
        tools.add(ovalToolItem);

        JMenuItem polygonToolItem = new JMenuItem("Polygon");
        polygonToolItem.setToolTipText("Select Polygon tool");
        polygonToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.ROUNDED_RECT); });
        tools.add(polygonToolItem);

        JMenuItem eraserToolItem = new JMenuItem("Eraser");
        eraserToolItem.setToolTipText("Select Eraser tool");
        eraserToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.ERASER); });
        tools.add(eraserToolItem);

        JMenuItem textToolItem = new JMenuItem("Text");
        textToolItem.setToolTipText("Select Text tool");
        textToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.TEXT); });
        tools.add(textToolItem);

        JMenuItem rectFilledToolItem = new JMenuItem("Rectangle (Filled)");
        rectFilledToolItem.setToolTipText("Select Filled Rectangle tool");
        rectFilledToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.RECT_FILLED); });
        tools.add(rectFilledToolItem);

        JMenuItem ovalFilledToolItem = new JMenuItem("Oval (Filled)");
        ovalFilledToolItem.setToolTipText("Select Filled Oval tool");
        ovalFilledToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.OVAL_FILLED); });
        tools.add(ovalFilledToolItem);

        JMenuItem polygonFilledToolItem = new JMenuItem("Polygon (Filled)");
        polygonFilledToolItem.setToolTipText("Select Filled Polygon tool");
        polygonFilledToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.ROUNDED_RECT_FILLED); });
        tools.add(polygonFilledToolItem);

        JMenuItem bucketToolItem = new JMenuItem("Bucket");
        bucketToolItem.setToolTipText("Select Bucket tool");
        bucketToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.BUCKET); });
        tools.add(bucketToolItem);

        // Move tool menu item
        JMenuItem moveToolItem = new JMenuItem("Move");
        moveToolItem.setName("moveTool");
        moveToolItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        moveToolItem.setToolTipText("Select Move tool");
        moveToolItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().selectTool(Tool.MOVE); });
        tools.add(moveToolItem);

        // Colors chooser (matches ribbon "More…")
        JMenuItem colorChooserItem = new JMenuItem("Choose Color…");
        colorChooserItem.setToolTipText("Open color chooser");
        colorChooserItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().triggerColorChooser(); });
        tools.addSeparator();
        tools.add(colorChooserItem);

        // Stroke Size submenu (common presets)
        JMenu strokeMenu = new JMenu("Stroke Size");
        int[] strokes = {1, 2, 3, 5, 8, 12, 20};
        for (int sz : strokes) {
            JMenuItem sItem = new JMenuItem(sz + " px");
            sItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().setStrokeSize(sz); });
            strokeMenu.add(sItem);
        }
        tools.add(strokeMenu);

        // Text menu mirrors ribbon text options
        // Text > Choose Font…
        JMenuItem chooseFontItem = new JMenuItem("Choose Font…");
        chooseFontItem.addActionListener(_ -> {
            if (gui == null) return;
            String[] fonts = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            JComboBox<String> combo = new JComboBox<>(fonts);
            combo.setSelectedIndex(Math.max(0, Math.min(SideMenu.getSelectedFont(), fonts.length - 1)));
            int res = JOptionPane.showConfirmDialog(this, combo, "Select Font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                int idx = combo.getSelectedIndex();
                gui.getSideMenu().setFontIndex(idx);
            }
        });
        textMenu.add(chooseFontItem);

        // Text > Size presets
        JMenu textSizeMenu = new JMenu("Size");
        int[] sizes = {10, 12, 14, 16, 18, 24, 36};
        for (int fs : sizes) {
            JMenuItem fsItem = new JMenuItem(fs + " pt");
            fsItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().setFontSize(fs); });
            textSizeMenu.add(fsItem);
        }
        JMenuItem sizeMore = new JMenuItem("More…");
        sizeMore.addActionListener(_ -> {
            if (gui == null) return;
            String input = JOptionPane.showInputDialog(this, "Enter font size (6–200):", SideMenu.getFontSize());
            if (input != null) {
                try {
                    int val = Integer.parseInt(input.trim());
                    gui.getSideMenu().setFontSize(val);
                } catch (NumberFormatException ignored) {}
            }
        });
        textSizeMenu.addSeparator();
        textSizeMenu.add(sizeMore);
        textMenu.add(textSizeMenu);

        // Text > Color…
        JMenuItem textColorItem = new JMenuItem("Text Color…");
        textColorItem.addActionListener(_ -> { if (gui != null) gui.getSideMenu().triggerColorChooser(); });
        textMenu.add(textColorItem);

        // Help > Keyboard Shortcuts
        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts...");
        shortcutsItem.addActionListener(_ -> {
            String msg = """
                    Shortcuts:
                    - New: Ctrl+N
                    - Open: Ctrl+O
                    - Save: Ctrl+S
                    - Copy: Ctrl+C
                    - Select All: Ctrl+A
                    - Move Tool: Ctrl+M
                    - Undo: Ctrl+Z
                    - Redo: Ctrl+Y or Ctrl+Shift+Z
                    - Exit: Ctrl+Q (Cmd+Q on macOS)""";
            JOptionPane.showMessageDialog(this, msg, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
        });
        help.add(shortcutsItem);

        file.add(newMenuItem);

        // File > Open…
        JMenuItem openMenuItem = new JMenuItem("Open…");
        openMenuItem.setToolTipText("Open an image file (all formats supported by Java)");
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMenuItem.addActionListener(_ -> {
            if (gui == null) return;
            JFileChooser chooser = new JFileChooser();
            // Dynamically include all image types supported by the current JVM
            String[] suffixes = ImageIO.getReaderFileSuffixes();
            java.util.Set<String> extSet = new java.util.LinkedHashSet<>();
            for (String s : suffixes) {
                if (s != null && !s.isEmpty()) extSet.add(s.toLowerCase());
            }
            // Fallback to common types if none detected (very unlikely)
            if (extSet.isEmpty()) {
                extSet.add("png");
                extSet.add("jpg");
                extSet.add("jpeg");
                extSet.add("bmp");
                extSet.add("gif");
            }
            String[] exts = extSet.toArray(new String[0]);
            String label = "Image Files (" + String.join(", ", exts) + ")";
            chooser.setFileFilter(new FileNameExtensionFilter(label, exts));
            int res = chooser.showOpenDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) {
                        JOptionPane.showMessageDialog(this, "Unsupported or corrupted image.", "Open Image", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    gui.getDrawArea().startImagePlacement(img);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to open image: " + ex.getMessage(), "Open Image", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        file.add(openMenuItem);

        file.add(saveMenuItem);
        file.add(exitMenuItem);
        jMenuBar.add(file);
        jMenuBar.add(edit);
        jMenuBar.add(tools);
        jMenuBar.add(help);
        setJMenuBar(jMenuBar);
    }

    private boolean confirmExitApproved() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }

    private void confirmAndExit() {
        if (confirmExitApproved()) {
            // Dispose window and exit
            dispose();
            System.exit(0);
        }
    }

    private void openImageFile(String filename) {
        if (gui == null) return;

        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("File not found: " + filename);
            JOptionPane.showMessageDialog(this, 
                "File not found: " + filename, 
                "Open Image", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                System.err.println("Unsupported or corrupted image: " + filename);
                JOptionPane.showMessageDialog(this, 
                    "Unsupported or corrupted image: " + filename, 
                    "Open Image", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            gui.getDrawArea().startImagePlacement(img);
            System.out.println("Loaded image: " + filename);
        } catch (Exception ex) {
            System.err.println("Failed to open image: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Failed to open image: " + ex.getMessage(), 
                "Open Image", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

}