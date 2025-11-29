package io.github.ozkanpakdil.paint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class GUI extends JPanel {
    static DrawArea drawAreaPanel;
    static SideMenu sidemenu;
    public JLabel message;
    private final JSpinner wSpin;
    private final JSpinner hSpin;
    private boolean initialSized = false;

    public GUI() throws IOException {
        this.setLayout(new java.awt.BorderLayout());
        sidemenu = new SideMenu();
        drawAreaPanel = new DrawArea(sidemenu);
        drawAreaPanel.setName("drawArea");

        // Ribbon and status will live at the bottom so the canvas stays central

        // Left sidebar with tools
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new javax.swing.border.EmptyBorder(2, 2, 2, 4));
        sidebar.add(buildToolColumn());
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(buildShapeColumn());
        sidebar.add(Box.createVerticalGlue());

        // Center with canvas
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        // Center holder to keep canvas centered when smaller than viewport
        JPanel holder = new JPanel(new java.awt.GridBagLayout());
        // Use theme color if dark theme, otherwise light gray
        Color holderBg = isDarkTheme() ? UIManager.getColor("Panel.background") : new Color(230, 230, 230);
        holder.setBackground(holderBg);
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = java.awt.GridBagConstraints.CENTER;
        holder.add(drawAreaPanel, gbc);

        centerPanel.add(sidebar, BorderLayout.WEST);
        centerPanel.add(holder, BorderLayout.CENTER);
        add(centerPanel, java.awt.BorderLayout.CENTER);

        // Status bar
        JPanel status = new JPanel();
        status.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));

        // Canvas size controls
        status.add(new JLabel("Canvas:"));
        int cw = drawAreaPanel.getCanvasWidth();
        int ch = drawAreaPanel.getCanvasHeight();
        wSpin = new JSpinner(new SpinnerNumberModel(cw, 1, 20000, 10));
        hSpin = new JSpinner(new SpinnerNumberModel(ch, 1, 20000, 10));
        wSpin.setToolTipText("Canvas width in pixels");
        hSpin.setToolTipText("Canvas height in pixels");
        status.add(new JLabel("W:"));
        status.add(wSpin);
        status.add(new JLabel("H:"));
        status.add(hSpin);
        // Listen for canvas size changes so status bar stays in sync (e.g.,
        // crop/undo/redo)
        drawAreaPanel.addPropertyChangeListener(evt -> {
            if ("canvasSize".equals(evt.getPropertyName())) {
                try {
                    Dimension d = (Dimension) evt.getNewValue();
                    SwingUtilities.invokeLater(() -> {
                        wSpin.setValue(d.width);
                        hSpin.setValue(d.height);
                        message.setText("Canvas: " + d.width + " x " + d.height);
                        // force layout refresh so holder can update if necessary
                        revalidate();
                    });
                } catch (Exception ignored) {
                }
            }
        });
        JButton apply = new JButton("Resize");
        apply.setToolTipText("Resize the canvas to the specified width and height");
        status.add(apply);

        message = new JLabel("Ready");
        status.add(Box.createHorizontalStrut(12));
        status.add(message);
        // combine ribbon and status at the bottom
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(new RibbonBar(sidemenu), BorderLayout.NORTH);
        southPanel.add(status, BorderLayout.SOUTH);
        add(southPanel, java.awt.BorderLayout.SOUTH);

        // Apply handler
        Runnable doResize = () -> {
            try {
                int newW = ((Number) wSpin.getValue()).intValue();
                int newH = ((Number) hSpin.getValue()).intValue();
                drawAreaPanel.resizeCanvas(newW, newH);
                message.setText("Canvas: " + newW + " x " + newH);
                drawAreaPanel.revalidate();
                holder.revalidate();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Unable to resize canvas: " + ex.getMessage(),
                        "Resize Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        };
        apply.addActionListener((ActionEvent _) -> doResize.run());
        // Update message on spinner change; Enter to apply
        wSpin.addChangeListener(_ -> message.setText(
                "W=" + ((Number) wSpin.getValue()).intValue() + ", H=" + ((Number) hSpin.getValue()).intValue()));
        hSpin.addChangeListener(_ -> message.setText(
                "W=" + ((Number) wSpin.getValue()).intValue() + ", H=" + ((Number) hSpin.getValue()).intValue()));
        wSpin.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
                    doResize.run();
            }
        });
        hSpin.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
                    doResize.run();
            }
        });

        // One-time auto-size of canvas to (almost) available center size
        holder.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (initialSized)
                    return;
                java.awt.Dimension size = holder.getSize();
                if (size.width <= 0 || size.height <= 0)
                    return;
                int margin = 40; // keep a small margin
                int newW = Math.max(1, Math.min(20000, size.width - margin));
                int newH = Math.max(1, Math.min(20000, size.height - margin));
                // Avoid pointless change if already near this size
                if (Math.abs(drawAreaPanel.getCanvasWidth() - newW) > 5
                        || Math.abs(drawAreaPanel.getCanvasHeight() - newH) > 5) {
                    drawAreaPanel.resizeCanvas(newW, newH);
                    wSpin.setValue(newW);
                    hSpin.setValue(newH);
                    message.setText("Canvas: " + newW + " x " + newH);
                }
                initialSized = true;
            }
        });
    }

    public DrawArea getDrawArea() {
        return drawAreaPanel;
    }

    public SideMenu getSideMenu() {
        return sidemenu;
    }

    private JPanel buildToolColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        Object[] tools = {
                new Object[] { "pencil", "pencil.png", 0 },
                new Object[] { "highlighter", "highlight.png", 12 },
                new Object[] { "eraser", "eraser.png", 5 },
                new Object[] { "text", "text.png", 6 },
                new Object[] { "bucket", "bucket.png", 10 },
                new Object[] { "move", "move.png", 11 },
                new Object[] { "arrow", "arrow.png", 13 }
        };
        for (Object tool : tools) {
            Object[] def = (Object[]) tool;
            String name = (String) def[0];
            String icon = (String) def[1];
            int idx = (int) def[2];
            JLabel b = makeIconButton(icon, "T" + idx, capitalize(name));
            b.addMouseListener(sidemenu);
            b.setAlignmentX(0.5f);
            col.add(b);
            col.add(Box.createVerticalStrut(2));
        }
        return col;
    }

    private JPanel buildShapeColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        Object[] shapes = {
                new Object[] { "rectangle", "rectangle.png", 2 },
                new Object[] { "oval", "oval.png", 3 },
                new Object[] { "polygon", "polygon.png", 4 },
                new Object[] { "rectangle_fill", "rectangle_fill.png", 7 },
                new Object[] { "oval_fill", "oval_fill.png", 8 },
                new Object[] { "polygon_fill", "polygon_fill.png", 9 },
                new Object[] { "line-tool", "line-tool.png", 1 },
        };
        for (Object shape : shapes) {
            Object[] def = (Object[]) shape;
            String name = (String) def[0];
            String icon = (String) def[1];
            int idx = (int) def[2];
            String tip = "line-tool".equals(name) ? "Line" : formatName(name);
            JLabel b = makeIconButton(icon, "T" + idx, tip);
            b.addMouseListener(sidemenu);
            b.setAlignmentX(0.5f);
            col.add(b);
            col.add(Box.createVerticalStrut(2));
        }
        return col;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String formatName(String s) {
        return capitalize(s.replace('-', ' ').replace("_", " "));
    }

    private boolean isDarkTheme() {
        // Check if FlatLaf dark theme is active
        String lafClass = UIManager.getLookAndFeel().getClass().getName();
        return lafClass.contains("FlatDark");
    }

    private JLabel makeIconButton(String resource, String name, String tooltip) {
        java.awt.image.BufferedImage img = null;
        try {
            java.io.InputStream in = SideMenu.class.getResourceAsStream("/images/" + resource);
            if (in != null)
                img = javax.imageio.ImageIO.read(in);
        } catch (Exception ignored) {
        }
        if (img == null) {
            img = new java.awt.image.BufferedImage(24, 24, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            // Use theme-aware colors for fallback icon
            Color bgColor = isDarkTheme() ? new Color(60, 60, 60) : new Color(245, 245, 245);
            Color fgColor = isDarkTheme() ? new Color(180, 180, 180) : new Color(120, 120, 120);
            g.setColor(bgColor);
            g.fillRect(0, 0, 24, 24);
            g.setColor(fgColor);
            g.drawRect(3, 3, 18, 18);
            g.dispose();
        }
        Image scaled = img.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        JLabel lab = new JLabel(new ImageIcon(scaled));
        // Use theme colors if dark theme, otherwise light colors
        Color borderColor = isDarkTheme() ? UIManager.getColor("Component.borderColor") : new Color(220, 220, 220);
        Color bgColor = isDarkTheme() ? UIManager.getColor("Button.background") : new Color(250, 250, 250);
        if (borderColor == null) borderColor = new Color(220, 220, 220);
        if (bgColor == null) bgColor = new Color(250, 250, 250);
        lab.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.MatteBorder(1, 1, 1, 1, borderColor),
                new javax.swing.border.EmptyBorder(3, 3, 3, 3)));
        lab.setOpaque(true);
        lab.setBackground(bgColor);
        lab.setName(name);
        lab.setToolTipText(tooltip);
        return lab;
    }
}
