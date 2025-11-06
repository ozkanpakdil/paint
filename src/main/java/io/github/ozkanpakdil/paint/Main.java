package io.github.ozkanpakdil.paint;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;


public class Main extends JFrame {
    private GUI gui;

    public Main() throws IOException {
        initializeGUI();
        Menu();
        initializeWindow();
    }

    static void main(String[] args) {
        // Workaround for GraalVM native image: AWT/Swing FontConfiguration requires 'java.home' to be set.
        if (System.getProperty("java.home") == null) {
            System.setProperty("java.home", "/");
        }

        System.setProperty("java.awt.headless", "false");
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("""
                    This application requires a graphical desktop session. Headless mode detected.
                    Hint: On Linux, ensure you are running under X11/Wayland and that the DISPLAY variable is set (e.g., :0).
                    Example: export DISPLAY=:0""");
            System.exit(1);
        }
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            // Fallback to default LAF if Nimbus is not available
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                new Main();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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

        JMenuItem moveToolItem = new JMenuItem("Move");
        moveToolItem.setName("moveTool");
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

    private void confirmAndExit() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION) {
            // Dispose window and exit
            dispose();
            System.exit(0);
        }
    }

}