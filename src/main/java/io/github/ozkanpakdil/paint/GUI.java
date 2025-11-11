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
        
        // Top Ribbon like MS Paint
        add(new RibbonBar(sidemenu), java.awt.BorderLayout.NORTH);

        // Center holder to keep canvas centered when smaller than viewport
        JPanel holder = new JPanel(new java.awt.GridBagLayout());
        holder.setBackground(new Color(230, 230, 230));
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = java.awt.GridBagConstraints.CENTER;
        holder.add(drawAreaPanel, gbc);
        add(holder, java.awt.BorderLayout.CENTER);

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
        // Listen for canvas size changes so status bar stays in sync (e.g., crop/undo/redo)
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
                } catch (Exception ignored) {}
            }
        });
        JButton apply = new JButton("Resize");
        apply.setToolTipText("Resize the canvas to the specified width and height");
        status.add(apply);

        message = new JLabel("Ready");
        status.add(Box.createHorizontalStrut(12));
        status.add(message);
        add(status, java.awt.BorderLayout.SOUTH);

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
        wSpin.addChangeListener(_ -> message.setText("W=" + ((Number) wSpin.getValue()).intValue() + ", H=" + ((Number) hSpin.getValue()).intValue()));
        hSpin.addChangeListener(_ -> message.setText("W=" + ((Number) wSpin.getValue()).intValue() + ", H=" + ((Number) hSpin.getValue()).intValue()));
        wSpin.addKeyListener(new java.awt.event.KeyAdapter() { public void keyPressed(java.awt.event.KeyEvent e){ if(e.getKeyCode()==java.awt.event.KeyEvent.VK_ENTER) doResize.run(); }});
        hSpin.addKeyListener(new java.awt.event.KeyAdapter() { public void keyPressed(java.awt.event.KeyEvent e){ if(e.getKeyCode()==java.awt.event.KeyEvent.VK_ENTER) doResize.run(); }});

        // One-time auto-size of canvas to (almost) available center size
        holder.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (initialSized) return;
                java.awt.Dimension size = holder.getSize();
                if (size.width <= 0 || size.height <= 0) return;
                int margin = 40; // keep a small margin
                int newW = Math.max(1, Math.min(20000, size.width - margin));
                int newH = Math.max(1, Math.min(20000, size.height - margin));
                // Avoid pointless change if already near this size
                if (Math.abs(drawAreaPanel.getCanvasWidth() - newW) > 5 || Math.abs(drawAreaPanel.getCanvasHeight() - newH) > 5) {
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
}
