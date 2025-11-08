package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ArrowToolTest {
    private DrawArea canvas;
    private SideMenu sideMenu;
    private JFrame frame; // optional visualization frame

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
        SwingUtilities.invokeAndWait(() -> {
            try {
                sideMenu = new SideMenu();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            canvas = new DrawArea(null);
            canvas.resizeCanvas(200, 120);

            // Always show a window for this UI-coupled test (when not headless)
            frame = new JFrame("ArrowToolTest Viewer");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(canvas, BorderLayout.CENTER);
            frame.setSize(320, 240);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() {
        if (frame != null) {
            try { frame.dispose(); } catch (Exception ignored) {}
            frame = null;
        }
        canvas = null;
        sideMenu = null;
    }

    @Test
    @Order(1)
    void arrow_drawsLineAndHead() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Select Arrow tool and set stroke
            sideMenu.selectTool(Tool.ARROW);
            SideMenu.setForeColor(Color.BLACK);
            sideMenu.setStrokeSize(6);

            // Draw from (30,30) to (150,80)
            press(canvas, 30, 30);
            drag(canvas, 30, 30, 150, 80);
            release(canvas, 150, 80);

            // Allow repaint to flush
            canvas.revalidate();
            canvas.repaint();

            // Check a pixel along the line is not white
            int midX = 90;
            int midY = 55; // roughly midway
            int rgbMid = DrawArea.cache.getRGB(midX, midY);
            assertNotEquals(Color.WHITE.getRGB(), rgbMid, "Expected line pixel to be drawn");

            // Check near the head endpoint there are painted pixels
            int headRgb = DrawArea.cache.getRGB(148, 79);
            // Allow either black stroke or anti-aliased non-white
            assertNotEquals(Color.WHITE.getRGB(), headRgb, "Expected arrow head area to be drawn");

            // If visualization is enabled, dump a screenshot for manual inspection
            if (Boolean.parseBoolean(System.getProperty("showUITests", "false"))) {
                try {
                    BufferedImage out = DrawArea.getFlattenedImage();
                    if (out != null) {
                        new File("target/test-screenshots").mkdirs();
                        String ts = new SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new Date());
                        File f = new File("target/test-screenshots/arrow-test-" + ts + ".png");
                        ImageIO.write(out, "png", f);
                        System.out.println("[DEBUG_LOG] Saved arrow test screenshot to: " + f.getAbsolutePath());
                    }
                } catch (Exception ignore) {}
            }
        });
    }

    // --- simple mouse helpers ---
    private static void press(Component c, int x, int y) {
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
    private static void drag(Component c, int x1, int y1, int x2, int y2) {
        int steps = 14;
        for (int i = 1; i <= steps; i++) {
            int xi = x1 + (x2 - x1) * i / steps;
            int yi = y1 + (y2 - y1) * i / steps;
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK, xi, yi, 0, false, java.awt.event.MouseEvent.BUTTON1));
        }
    }
    private static void release(Component c, int x, int y) {
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
}
