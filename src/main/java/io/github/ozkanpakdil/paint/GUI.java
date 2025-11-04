package io.github.ozkanpakdil.paint;

import javax.swing.*;
import java.io.IOException;

public class GUI extends JPanel {
    static DrawArea drawAreaPanel;
    static SideMenu sidemenu;
    public JLabel message;

    public GUI() throws IOException {
        this.setLayout(new java.awt.BorderLayout());
        sidemenu = new SideMenu();
        drawAreaPanel = new DrawArea(sidemenu);
        drawAreaPanel.setName("drawArea");
        // Top Ribbon like MS Paint
        add(new RibbonBar(sidemenu), java.awt.BorderLayout.NORTH);
        add(drawAreaPanel, java.awt.BorderLayout.CENTER);
        // Status bar
        JPanel status = new JPanel();
        status.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
        message = new JLabel("Ready");
        status.add(message);
        add(status, java.awt.BorderLayout.SOUTH);
        message = new JLabel("X = 0" + "Y = 0");
    }

    public DrawArea getDrawArea() {
        return drawAreaPanel;
    }

    public SideMenu getSideMenu() {
        return sidemenu;
    }
}
