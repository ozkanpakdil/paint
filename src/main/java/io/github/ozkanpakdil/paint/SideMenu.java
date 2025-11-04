package io.github.ozkanpakdil.paint;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


public class SideMenu extends JPanel implements MouseListener, ChangeListener {

    private static final Color[] colors = {Color.BLACK, Color.BLUE, Color.CYAN, Color.DARK_GRAY, Color.GRAY, Color.GREEN, Color.LIGHT_GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.WHITE, Color.YELLOW};
    private static Tool draw_tool = Tool.PENCIL;
    private static String text;
    private static int pencil_size = 2;
    private static Color for_color = colors[0];
    private static int font;
    private static int fontSize = 15;
    private final JPanel colorChooserPanel = new JPanel();

    SideMenu() throws IOException {

        // Make the side menu compact like a modern toolbar
        setPreferredSize(new Dimension(160, 100));

        /*
         * Color Picker (compact palette like MS Paint)
         */
        colorChooserPanel.setPreferredSize(new Dimension(30, 30));
        colorChooserPanel.addMouseListener(this);
        colorChooserPanel.setName("C" + colors.length);
        colorChooserPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        colorChooserPanel.setBackground(Color.black);
        JPanel color = new JPanel(new GridLayout(3, 1, 4, 4));
        // Foreground preview
        color.add(colorChooserPanel);
        // Palette grid
        JPanel palette = new JPanel(new GridLayout(2, 7, 3, 3));
        JPanel[] fore_color_panel = new JPanel[colors.length];
        for (int i = 0; i < fore_color_panel.length; i++) {
            fore_color_panel[i] = new JPanel();
            fore_color_panel[i].setPreferredSize(new Dimension(18, 18));
            fore_color_panel[i].setBackground(colors[i]);
            fore_color_panel[i].addMouseListener(this);
            fore_color_panel[i].setName("F" + i);
            palette.add(fore_color_panel[i]);
        }
        color.add(palette);
        add(color);
        /*
         * 	Color Picker Ends
         * 	Tool Picker Starts
         */

        String[] tool_names = {"pencil", "line-tool", "rectangle", "oval", "polygon", "eraser", "text", "rectangle_fill", "oval_fill", "polygon_fill", "bucket", "move"};
        // Compact tools grid similar to MS Paint
        JPanel tool_panel = new JPanel(new GridLayout(0, 4, 4, 4));
        for (int i = 0; i < tool_names.length; i++) {
            BufferedImage myPicture = null;
            try {
                java.io.InputStream in = SideMenu.class.getResourceAsStream("/images/" + tool_names[i] + ".png");
                if (in != null) {
                    myPicture = ImageIO.read(in);
                }
            } catch (Exception ignored) {
            }
            if (myPicture == null) {
                // Fallback: generate a simple icon if resource is missing (e.g., for Move)
                myPicture = generateToolIcon(tool_names[i], 28, 28);
            }
            Image dimg = myPicture.getScaledInstance(28, 28, Image.SCALE_SMOOTH);
            JLabel toolLabel = new JLabel(new ImageIcon(dimg));
            toolLabel.setOpaque(true);
            toolLabel.setBackground(new Color(245, 245, 245));
            toolLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            toolLabel.setName("T" + i);
            toolLabel.addMouseListener(this);
            tool_panel.add(toolLabel);
        }
        add(tool_panel);

        /*
         * Tool picker Ends
         * Pencil Size Chooser starts
         */
        JLabel sliderLabel = new JLabel("Stroke", JLabel.LEFT);

        JSlider stroke_size = new JSlider(JSlider.HORIZONTAL, 1, 20, 2);
        stroke_size.setPreferredSize(new Dimension(140, 32));
        add(sliderLabel);
        add(stroke_size);
        stroke_size.addChangeListener(this);
        // Make the slider compact: no ticks/labels
        stroke_size.setPaintTicks(false);
        stroke_size.setPaintLabels(false);
        Font font = new Font("Dialog", Font.PLAIN, 12);
        stroke_size.setFont(font);

        /*
         * Pencil Size Chooser Ends
         * 		Import Export
         */
        JPanel i_e = new JPanel();
        BufferedImage myPicture = ImageIO.read(SideMenu.class.getResourceAsStream("/images/Save.png"));
        Image dimg = myPicture.getScaledInstance(28, 28,
                Image.SCALE_SMOOTH);
        JLabel picLabel = new JLabel(new ImageIcon(dimg));
        picLabel.setSize(10, 10);
        picLabel.addMouseListener(this);
        picLabel.setName("SAVE");
        i_e.add(picLabel);
        myPicture = ImageIO.read(SideMenu.class.getResourceAsStream("/images/upload.jpeg"));
        dimg = myPicture.getScaledInstance(50, 50,
                Image.SCALE_SMOOTH);
        JLabel upload = new JLabel(new ImageIcon(dimg));
        upload.setSize(10, 10);
        upload.addMouseListener(this);
        upload.setName("UPLOAD");
        //i_e.add(upload);
        add(i_e);

    }

    public static int getSelectedFont() {
        return font;
    }

    public static Color getSelectedForeColor() {
        return for_color;
    }

    public static String getInputText() {
        return text;
    }

    public static Tool getSelectedTool() {
        return draw_tool;
    }

    public static int getStrokeSize() {
        return pencil_size;
    }

    public static void setForeColor(Color c) {
        Color old = for_color;
        for_color = c;
        // notify
        // Using null as source is fine; this is a Swing component so firePropertyChange exists
        // Triggered when eraser or chooser changes programmatically
        // Consumers should also listen to explicit chooser events
    }

    public static int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int size) {
        int old = fontSize;
        fontSize = Math.max(6, size);
        firePropertyChange("fontSize", old, fontSize);
    }

    // Fallback icon generator (simple Adwaita-like glyphs for certain tools)
    private BufferedImage generateToolIcon(String name, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Light toolbar bg
            g.setColor(new Color(245, 245, 245));
            g.fillRect(0, 0, w, h);
            if ("move".equals(name)) {
                // Draw a cross move cursor similar to Adwaita
                g.setColor(new Color(60, 60, 60));
                int cx = w / 2;
                int cy = h / 2;
                int arm = Math.min(w, h) / 3;
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // vertical and horizontal lines
                g.drawLine(cx - arm, cy, cx + arm, cy);
                g.drawLine(cx, cy - arm, cx, cy + arm);
                // arrowheads
                Polygon left = new Polygon(new int[]{cx - arm, cx - arm + 6, cx - arm + 6}, new int[]{cy, cy - 4, cy + 4}, 3);
                Polygon right = new Polygon(new int[]{cx + arm, cx + arm - 6, cx + arm - 6}, new int[]{cy, cy - 4, cy + 4}, 3);
                Polygon up = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy - arm, cy - arm + 6, cy - arm + 6}, 3);
                Polygon down = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy + arm, cy + arm - 6, cy + arm - 6}, 3);
                g.fill(left);
                g.fill(right);
                g.fill(up);
                g.fill(down);
            } else {
                // Generic placeholder
                g.setColor(new Color(80, 80, 80));
                g.drawRect(4, 4, w - 8, h - 8);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private void changeForeColor(int color) {
        Color old = for_color;
        for_color = colors[color];
        System.out.println("Color Changed :" + color);
        colorChooserPanel.setBackground(for_color);
        // notify listeners (e.g., RibbonBar) about color change
        firePropertyChange("color", old, for_color);
    }

    private void changeTool(Tool tool) {
        // Do not mutate the global color when switching tools. Eraser will render in white locally.
        Tool old = draw_tool;
        draw_tool = tool;
        System.out.println("Tool Changed " + tool);
        // notify listeners (e.g., RibbonBar/DrawArea) that tool changed
        firePropertyChange("tool", old, draw_tool);
    }

    /**
     * Programmatically select a tool (used by menu actions). Fires the same property change
     * as clicking a tool icon so listeners update consistently.
     */
    public void selectTool(Tool tool) {
        changeTool(tool);
    }

    @Override
    public void mouseClicked(MouseEvent ev) {

        if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'F') // foreground color
        {
            changeForeColor(Integer.parseInt(ev.getComponent().getName().substring(1)));
        } else if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'T') // Tools color
            changeTool(Tool.fromIndex(Integer.parseInt(ev.getComponent().getName().substring(1))));
        else if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'P') // Pencil color
            ;
        else if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'S') {
            saveImage();
        } else if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'U') {
            uploadImage();
        } else if (ev.getComponent().getName() != null && ev.getComponent().getName().charAt(0) == 'C') {
            colorchooser();
        }
    }

    private void colorchooser() {
        Color chosen = JColorChooser.showDialog(this, "Select a color", for_color);
        if (chosen != null) {
            Color old = for_color;
            for_color = chosen;
            colorChooserPanel.setBackground(for_color);
            System.out.println("Color changed");
            firePropertyChange("color", old, for_color);
        }
    }

    private void uploadImage() {
		/*	BufferedImage myPicture = ImageIO.read(new File("path-to-file"));
		JLabel picLabel = new JLabel(new ImageIcon(myPicture));
		add(picLabel);
		 */
    }

    private void saveImage() {
        try {
            if (DrawArea.cache == null) {
                JOptionPane.showMessageDialog(this, "Nothing to save yet.", "Save", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JFileChooser jf = new JFileChooser(new File(System.getProperty("user.home", ".")));
            jf.setSelectedFile(new File("image.png"));
            int actionDialog = jf.showSaveDialog(this);
            if (actionDialog == JFileChooser.APPROVE_OPTION) {
                File file = jf.getSelectedFile();
                String path = file.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".png")) {
                    file = new File(path + ".png");
                }
                if (file.exists()) {
                    int answer = JOptionPane.showConfirmDialog(this, "Replace existing file?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (answer != JOptionPane.YES_OPTION) return;
                }
                ImageIO.write(DrawArea.cache, "png", file);
                System.out.println("File Saved: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Public wrapper to trigger save from menus/shortcuts
    public void triggerSave() {
        saveImage();
    }

    // New setters to allow ribbon to control text formatting
    public void setFontIndex(int idx) {
        int old = font;
        font = Math.max(0, idx);
        firePropertyChange("font", old, font);
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mousePressed(MouseEvent ev) {
    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }


    @Override
    public void stateChanged(ChangeEvent e) {

        JSlider slider = (JSlider) e.getSource();
        System.out.println("Stroke Size Changed" + slider.getValue());
        pencil_size = slider.getValue();

    }

}

class FontCellRenderer extends DefaultListCellRenderer {

    public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
        Font font = new Font((String) value, Font.PLAIN, 20);
        label.setFont(font);
        return label;
    }
}