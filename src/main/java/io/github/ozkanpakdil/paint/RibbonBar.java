package io.github.ozkanpakdil.paint;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A simple top ribbon inspired by MS Paint.
 * It delegates actions to the existing SideMenu listeners by
 * attaching the SideMenu instance as Mouse/Change listener to controls.
 * This lets us reuse the current state management without rewriting drawing code.
 */
public class RibbonBar extends JPanel {
    private final SideMenu controller; // reused for event handling/state
    private JPanel textGroup;
    private JComboBox<String> fontCombo;
    private JSpinner sizeSpinner;
    private JPanel textColorBtn;
    private JPanel colorPreview;

    // Size group controls: separate Stroke and Opacity sliders
    private JLabel strokeLabel;
    private JSlider strokeSlider;
    private JLabel opacityLabel;
    private JSlider opacitySlider;

    public RibbonBar(SideMenu controller) throws IOException {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(new MatteBorder(0, 0, 1, 0, new Color(210, 210, 210)));
        add(buildRibbon(), BorderLayout.CENTER);
        setBackground(new Color(245, 247, 250));
    }

    private static String capitalizeFirstLetter(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String sanitizeAndFormat(String s) {
        return capitalizeFirstLetter(s.replace('-', ' ')).replace("_", " ");
    }

    private JComponent buildRibbon() throws IOException {
        // TOP AREA: left groups (Tools/Shapes/Size) and fixed Colors group on the right
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topLeft.setOpaque(false);
        topLeft.add(groupTools());
        topLeft.add(groupShapes());
        topLeft.add(groupSize());

        JComponent colorsGroup = groupColors();

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBorder(new EmptyBorder(6, 8, 0, 8));
        topRow.setOpaque(false);
        topRow.add(topLeft, BorderLayout.CENTER);
        topRow.add(colorsGroup, BorderLayout.EAST);

        // CONTEXT ROW: groups that appear conditionally (e.g., Text formatting)
        JPanel contextRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        contextRow.setBorder(new EmptyBorder(0, 8, 6, 8));
        contextRow.setOpaque(false);
        contextRow.add(groupText());

        // Container that stacks rows vertically
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(topRow);
        container.add(contextRow);

        // listen for tool changes to toggle text group visibility
        controller.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case "tool" -> {
                    Tool tool = (Tool) evt.getNewValue();
                    boolean showText = (tool == Tool.TEXT);
                    if (textGroup != null) textGroup.setVisible(showText);
                    // Keep the context row visible only if at least one child is visible
                    contextRow.setVisible(showText);
                    // Reconfigure size slider between Stroke and Opacity
                    configureSizeSliderForTool(tool);
                    revalidate();
                    repaint();
                }
                case "color" -> {
                    Color c = SideMenu.getSelectedForeColor();
                    if (textColorBtn != null) textColorBtn.setBackground(c);
                    if (colorPreview != null) {
                        colorPreview.setBackground(c);
                        colorPreview.repaint();
                    }
                }
                case "strokeSize" -> {
                    if (strokeSlider != null) {
                        strokeSlider.setValue(SideMenu.getStrokeSize());
                    }
                }
                case "opacity" -> {
                    if (opacitySlider != null) {
                        opacitySlider.setValue(SideMenu.getHighlighterOpacity());
                    }
                }
                case "font" -> {
                    if (fontCombo != null) {
                        int idx = (int) evt.getNewValue();
                        if (idx >= 0 && idx < fontCombo.getItemCount()) {
                            fontCombo.setSelectedIndex(idx);
                        }
                    }
                }
                case "fontSize" -> {
                    if (sizeSpinner != null) {
                        sizeSpinner.setValue(SideMenu.getFontSize());
                    }
                }
            }
        });
        // initial state
        boolean showText = (SideMenu.getSelectedTool() == Tool.TEXT);
        if (textGroup != null) textGroup.setVisible(showText);
        contextRow.setVisible(showText);

        // No scrollbars: rows wrap using FlowLayout when space is tight
        return container;
    }

    record ToolDef(String name, String icon, int index) {
        ToolDef(String name, int index) {
            this(name, name + ".png", index);
        }
    }

    private JComponent groupTools() throws IOException {
        JPanel g = titledGroup("Tools");
        ToolDef[] tools = {
            new ToolDef("pencil", 0),
            new ToolDef("highlighter", "highlight.png", 12),
            new ToolDef("eraser", 5),
            new ToolDef("text", 6),
            new ToolDef("bucket", 10),
            new ToolDef("move", 11),
            new ToolDef("arrow", "arrow.png", 13)
        };
        for (ToolDef tool : tools) {
            JLabel b = makeIconButton(tool.icon, "T" + tool.index, capitalizeFirstLetter(tool.name));
            b.addMouseListener(controller);
            g.add(b);
        }
        return g;
    }

    private JComponent groupShapes() throws IOException {
        JPanel g = titledGroup("Shapes");
        ToolDef[] shapes = {
            new ToolDef("rectangle", 2),
            new ToolDef("oval", 3),
            new ToolDef("polygon", 4),
            new ToolDef("rectangle_fill", 7),
            new ToolDef("oval_fill", 8),
            new ToolDef("polygon_fill", 9),
            new ToolDef("line-tool", 1),
            new ToolDef("arrow", "arrow.png", 13)
        };
        for (ToolDef shape : shapes) {
            String tip = shape.name.equals("line-tool") ? "Line" : sanitizeAndFormat(shape.name);
            JLabel b = makeIconButton(shape.icon, "T" + shape.index, tip);
            b.addMouseListener(controller);
            g.add(b);
        }
        return g;
    }

    private void configureSizeSliderForTool(Tool tool) {
        // Enable opacity slider only for Highlighter; Stroke is always enabled
        if (opacitySlider != null) {
            boolean enableOpacity = (tool == Tool.HIGHLIGHTER);
            opacitySlider.setEnabled(enableOpacity);
            opacityLabel.setEnabled(enableOpacity);
            if (enableOpacity) {
                opacitySlider.setToolTipText("Highlighter opacity (%)");
            } else {
                opacitySlider.setToolTipText("Opacity is only used by Highlighter");
            }
        }
        if (strokeSlider != null) {
            strokeSlider.setToolTipText("Stroke width (px)");
        }
    }

    private JComponent groupSize() {
        JPanel g = titledGroup("Size");

        // A vertical stack: Stroke row on top, Opacity row below it
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        // Stroke width controls
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row1.setOpaque(false);
        strokeLabel = new JLabel("Stroke");
        strokeSlider = new JSlider(JSlider.HORIZONTAL, 1, 20, SideMenu.getStrokeSize());
        strokeSlider.setName("stroke");
        strokeSlider.setPreferredSize(new Dimension(140, 28));
        strokeSlider.setPaintTicks(false);
        strokeSlider.setPaintLabels(false);
        strokeSlider.addChangeListener(controller);
        row1.add(strokeLabel);
        row1.add(strokeSlider);
        rows.add(row1);

        // Opacity controls (for Highlighter)
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row2.setOpaque(false);
        opacityLabel = new JLabel("Opacity");
        opacitySlider = new JSlider(JSlider.HORIZONTAL, 5, 100, SideMenu.getHighlighterOpacity());
        opacitySlider.setName("opacity");
        opacitySlider.setPreferredSize(new Dimension(140, 28));
        opacitySlider.setPaintTicks(false);
        opacitySlider.setPaintLabels(false);
        opacitySlider.addChangeListener(controller);
        row2.add(opacityLabel);
        row2.add(opacitySlider);
        rows.add(row2);

        g.add(rows);

        // initialize enabled state according to current tool
        configureSizeSliderForTool(SideMenu.getSelectedTool());
        return g;
    }

    private JComponent groupColors() {
        JPanel g = titledGroup("Colors");
        // Foreground preview swatch
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(26, 26));
        colorPreview.setBackground(SideMenu.getSelectedForeColor());
        colorPreview.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        colorPreview.setName("C0");
        colorPreview.addMouseListener(controller);
        g.add(colorPreview);

        // small palette two rows
        JPanel palette = new JPanel(new GridLayout(2, 7, 3, 3));
        Color[] cols = new Color[]{Color.BLACK, Color.BLUE, Color.CYAN, Color.DARK_GRAY, Color.GRAY, Color.GREEN, Color.LIGHT_GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.WHITE, Color.YELLOW};
        for (int i = 0; i < cols.length; i++) {
            JPanel sw = new JPanel();
            sw.setPreferredSize(new Dimension(18, 18));
            sw.setBackground(cols[i]);
            sw.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            sw.setName("F" + i);
            sw.addMouseListener(controller);
            palette.add(sw);
        }
        g.add(palette);

        JButton more = new JButton("More…");
        more.setMargin(new Insets(2, 6, 2, 6));
        more.setFocusable(false);
        // Use the color chooser via SideMenu: clicking a pseudo component named C* will trigger controller.colorchooser()
        more.addActionListener(_ -> {
            // fabricate a component with name starting with 'C' and dispatch mouseClicked
            // Delegate to controller via a fake component name starting with 'C'
            JLabel hidden = new JLabel();
            hidden.setName("C1");
            controllerMouseClick(hidden);
        });
        g.add(more);
        return g;
    }

    private JComponent groupText() {
        textGroup = titledGroup("Text");
        // Font family
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fonts = ge.getAvailableFontFamilyNames();
        fontCombo = new JComboBox<>(fonts);
        // Constrain combo width so Size and Color controls remain visible on the same row
        fontCombo.setPrototypeDisplayValue("WWWWWWWWWWWWWWWW"); // fixes very long preferred width on some LAFs
        Dimension comboSize = fontCombo.getPreferredSize();
        comboSize = new Dimension(Math.max(180, comboSize.width), comboSize.height);
        fontCombo.setPreferredSize(comboSize);
        fontCombo.setMaximumSize(comboSize);
        fontCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setFont(new Font(String.valueOf(value), Font.PLAIN, 12));
                return l;
            }
        });
        if (SideMenu.getSelectedFont() >= 0 && SideMenu.getSelectedFont() < fonts.length) {
            fontCombo.setSelectedIndex(SideMenu.getSelectedFont());
        }
        fontCombo.addActionListener(_ -> controller.setFontIndex(fontCombo.getSelectedIndex()));
        textGroup.add(new JLabel("Font:"));
        textGroup.add(fontCombo);

        // Size
        sizeSpinner = new JSpinner(new SpinnerNumberModel(Math.max(6, SideMenu.getFontSize()), 6, 200, 1));
        sizeSpinner.setPreferredSize(new Dimension(60, sizeSpinner.getPreferredSize().height));
        sizeSpinner.addChangeListener(_ -> controller.setFontSize((Integer) sizeSpinner.getValue()));
        textGroup.add(new JLabel("Size:"));
        textGroup.add(sizeSpinner);

        // Color shortcut specific for text
        textColorBtn = new JPanel();
        textColorBtn.setPreferredSize(new Dimension(22, 22));
        textColorBtn.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        textColorBtn.setToolTipText("Text color");
        textColorBtn.setBackground(SideMenu.getSelectedForeColor());
        textColorBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // trigger the same color chooser
                JLabel hidden = new JLabel();
                hidden.setName("C1");
                controllerMouseClick(hidden);
            }
        });
        textGroup.add(new JLabel("Color:"));
        textGroup.add(textColorBtn);

        textGroup.setVisible(false);
        return textGroup;
    }

    private void controllerMouseClick(JComponent comp) {
        // Create and dispatch a MouseEvent so the controller handles 'C' name path
        MouseEvent ev = new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1);
        comp.setName("C1");
        // Call controller directly
        controller.mouseClicked(ev);
    }

    private JPanel titledGroup(String title) {
        return new RibbonGroup(title);
    }

    private JLabel makeIconButton(String resource, String name, String tooltip) {
        BufferedImage img = null;
        try {
            java.io.InputStream in = SideMenu.class.getResourceAsStream("/images/" + resource);
            if (in != null) {
                img = ImageIO.read(in);
            }
        } catch (Exception ignored) {
        }
        if (img == null) {
            // Fallbacks for missing resources
            if ("move.png".equalsIgnoreCase(resource)) {
                img = generateMoveIcon(24, 24);
            } else {
                img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                try {
                    g.setColor(new Color(245, 245, 245));
                    g.fillRect(0, 0, 24, 24);
                    g.setColor(new Color(120, 120, 120));
                    g.drawRect(3, 3, 24 - 6, 24 - 6);
                } finally {
                    g.dispose();
                }
            }
        }
        Image scaled = img.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        JLabel lab = new JLabel(new ImageIcon(scaled));
        lab.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, new Color(220, 220, 220)),
                new EmptyBorder(3, 3, 3, 3)));
        lab.setOpaque(true);
        lab.setBackground(new Color(250, 250, 250));
        lab.setName(name);
        lab.setToolTipText(tooltip);
        return lab;
    }

    private BufferedImage generateMoveIcon(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 245, 245));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(60, 60, 60));
            int cx = w / 2;
            int cy = h / 2;
            int arm = Math.min(w, h) / 3;
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx - arm, cy, cx + arm, cy);
            g.drawLine(cx, cy - arm, cx, cy + arm);
            Polygon left = new Polygon(new int[]{cx - arm, cx - arm + 6, cx - arm + 6}, new int[]{cy, cy - 4, cy + 4}, 3);
            Polygon right = new Polygon(new int[]{cx + arm, cx + arm - 6, cx + arm - 6}, new int[]{cy, cy - 4, cy + 4}, 3);
            Polygon up = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy - arm, cy - arm + 6, cy - arm + 6}, 3);
            Polygon down = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy + arm, cy + arm - 6, cy + arm - 6}, 3);
            g.fill(left);
            g.fill(right);
            g.fill(up);
            g.fill(down);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static class RibbonGroup extends JPanel {
        private final JPanel content;

        RibbonGroup(String title) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new MatteBorder(0, 1, 0, 1, new Color(230, 230, 230)));

            content = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            content.setOpaque(false);

            JPanel pad = new JPanel();
            pad.setOpaque(false);
            pad.setLayout(new BorderLayout());
            pad.setBorder(new EmptyBorder(4, 6, 4, 6));
            pad.add(content, BorderLayout.CENTER);

            JLabel t = new JLabel(title);
            t.setFont(t.getFont().deriveFont(Font.PLAIN, 11f));
            t.setBorder(new EmptyBorder(0, 4, 0, 4));

            add(pad, BorderLayout.CENTER);
            add(t, BorderLayout.SOUTH);
        }

        @Override
        public Component add(Component comp) {
            return content.add(comp);
        }
    }
}
