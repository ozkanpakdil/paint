package io.github.ozkanpakdil.paint;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;


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
        setLocation(100, 0);
        setResizable(true);
        setVisible(true);
    }

    public void Menu() {
        JMenuBar jMenuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenu edit = new JMenu("Edit");
        JMenu tools = new JMenu("Tools");
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

        // Tools > Move tool selector
        JMenuItem moveToolItem = new JMenuItem("Move");
        moveToolItem.setName("moveTool");
        moveToolItem.setToolTipText("Select Move tool");
        moveToolItem.addActionListener(_ -> {
            if (gui != null) gui.getSideMenu().selectTool(Tool.MOVE);
        });
        tools.add(moveToolItem);

        // Help > Keyboard Shortcuts
        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts...");
        shortcutsItem.addActionListener(_ -> {
            String msg = """
                    Shortcuts:
                    - New: Ctrl+N
                    - Save: Ctrl+S
                    - Undo: Ctrl+Z
                    - Redo: Ctrl+Y or Ctrl+Shift+Z
                    - Exit: Ctrl+Q (Cmd+Q on macOS)""";
            JOptionPane.showMessageDialog(this, msg, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
        });
        help.add(shortcutsItem);

        file.add(newMenuItem);
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