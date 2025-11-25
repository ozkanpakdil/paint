package io.github.ozkanpakdil.paint;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
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

    private JSlider strokeSlider;
    private JLabel opacityLabel;
    private JSlider opacitySlider;

    public RibbonBar(SideMenu controller) throws IOException {
        this.controller = controller;
        setLayout(new BorderLayout());
        add(buildRibbon(), BorderLayout.CENTER);
        setBackground(new Color(245, 247, 250));
    }

    record ToolDef(String name, String icon, int index) {
        ToolDef(String name, int index) {
            this(name, name + ".png", index);
        }
    }

    private JComponent buildRibbon() {
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(2, 4, 2, 4));
        topBar.add(buildSizeBar());
        topBar.add(buildColorBar());
        textGroup = createTextGroup();
        topBar.add(textGroup);
        
        controller.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case "tool" -> {
                    Tool tool = (Tool) evt.getNewValue();
                    if (textGroup != null) textGroup.setVisible(tool == Tool.TEXT);
                    configureSizeSliderForTool(tool);
                    topBar.revalidate();
                    topBar.repaint();
                }
                case "color" -> {
                    Color c = SideMenu.getSelectedForeColor();
                    if (textColorBtn != null) textColorBtn.setBackground(c);
                    // Ensure the large preview swatch reflects the selected color immediately
                    if (colorPreview != null) {
                        colorPreview.setBackground(c);
                        colorPreview.repaint();
                    }
                }
                case "strokeSize" -> {
                    if (strokeSlider != null) strokeSlider.setValue(SideMenu.getStrokeSize());
                }
                case "opacity" -> {
                    if (opacitySlider != null) opacitySlider.setValue(SideMenu.getHighlighterOpacity());
                }
                case "font" -> {
                    if (fontCombo != null) {
                        int idx = (int) evt.getNewValue();
                        if (idx >= 0 && idx < fontCombo.getItemCount()) fontCombo.setSelectedIndex(idx);
                    }
                }
                case "fontSize" -> {
                    if (sizeSpinner != null) sizeSpinner.setValue(SideMenu.getFontSize());
                }
            }
        });
        
        if (textGroup != null) textGroup.setVisible(SideMenu.getSelectedTool() == Tool.TEXT);
        return topBar;
    }
    
    private JComponent buildSizeBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        p.setOpaque(false);
        JLabel sl = new JLabel("S:");
        sl.setFont(new Font("Dialog", Font.PLAIN, 10));
        strokeSlider = new JSlider(JSlider.HORIZONTAL, 1, 20, SideMenu.getStrokeSize());
        strokeSlider.setName("stroke");
        strokeSlider.setPreferredSize(new Dimension(80, 24));
        strokeSlider.addChangeListener(controller);
        p.add(sl);
        p.add(strokeSlider);
        
        p.add(Box.createHorizontalStrut(6));
        JLabel ol = new JLabel("O:");
        ol.setFont(new Font("Dialog", Font.PLAIN, 10));
        opacityLabel = ol;
        opacitySlider = new JSlider(JSlider.HORIZONTAL, 5, 100, SideMenu.getHighlighterOpacity());
        opacitySlider.setName("opacity");
        opacitySlider.setPreferredSize(new Dimension(80, 24));
        opacitySlider.addChangeListener(controller);
        p.add(ol);
        p.add(opacitySlider);
        configureSizeSliderForTool(SideMenu.getSelectedTool());
        return p;
    }
    
    private JComponent buildColorBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.setOpaque(false);
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(24, 24));
        colorPreview.setBackground(SideMenu.getSelectedForeColor());
        colorPreview.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        colorPreview.setName("C0");
        colorPreview.addMouseListener(controller);
        p.add(colorPreview);
        
        JPanel palette = new JPanel(new GridLayout(2, 7, 2, 2));
        Color[] cols = new Color[]{Color.BLACK, Color.BLUE, Color.CYAN, Color.DARK_GRAY, Color.GRAY, Color.GREEN, Color.LIGHT_GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.WHITE, Color.YELLOW};
        for (int i = 0; i < cols.length; i++) {
            JPanel sw = new JPanel();
            sw.setPreferredSize(new Dimension(14, 14));
            sw.setBackground(cols[i]);
            sw.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            sw.setName("F" + i);
            sw.addMouseListener(controller);
            palette.add(sw);
        }
        p.add(palette);
        
        JButton more = new JButton("▼");
        more.setMargin(new Insets(1, 3, 1, 3));
        more.setFocusable(false);
        more.setFont(new Font("Dialog", Font.PLAIN, 9));
        more.addActionListener(_ -> {
            JLabel hidden = new JLabel();
            hidden.setName("C1");
            controllerMouseClick(hidden);
        });
        p.add(more);
        return p;
    }
    
    private JPanel createTextGroup() {
        JPanel g = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        g.setOpaque(false);
        g.setBorder(new EmptyBorder(2, 4, 2, 4));
        
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fonts = ge.getAvailableFontFamilyNames();
        fontCombo = new JComboBox<>(fonts);
        fontCombo.setPrototypeDisplayValue("WWWWWWWWWW");
        fontCombo.setMaximumSize(new Dimension(120, 24));
        fontCombo.setFont(new Font("Dialog", Font.PLAIN, 10));
        if (SideMenu.getSelectedFont() >= 0 && SideMenu.getSelectedFont() < fonts.length) {
            fontCombo.setSelectedIndex(SideMenu.getSelectedFont());
        }
        fontCombo.addActionListener(_ -> controller.setFontIndex(fontCombo.getSelectedIndex()));
        g.add(fontCombo);
        
        sizeSpinner = new JSpinner(new SpinnerNumberModel(Math.max(6, SideMenu.getFontSize()), 6, 200, 1));
        sizeSpinner.setPreferredSize(new Dimension(50, 24));
        sizeSpinner.addChangeListener(_ -> controller.setFontSize((Integer) sizeSpinner.getValue()));
        g.add(sizeSpinner);
        
        textColorBtn = new JPanel();
        textColorBtn.setPreferredSize(new Dimension(20, 20));
        textColorBtn.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        textColorBtn.setBackground(SideMenu.getSelectedForeColor());
        textColorBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                JLabel hidden = new JLabel();
                hidden.setName("C1");
                controllerMouseClick(hidden);
            }
        });
        g.add(textColorBtn);
        
        g.setVisible(false);
        return g;
    }

    private void configureSizeSliderForTool(Tool tool) {
        if (opacitySlider != null) {
            boolean enableOpacity = (tool == Tool.HIGHLIGHTER);
            opacitySlider.setEnabled(enableOpacity);
            opacityLabel.setEnabled(enableOpacity);
            opacitySlider.setToolTipText(enableOpacity ? "Highlighter opacity (%)" : "Opacity for highlighter");
        }
    }

    private void controllerMouseClick(JComponent comp) {
        MouseEvent ev = new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1);
        comp.setName("C1");
        controller.mouseClicked(ev);
    }
}
