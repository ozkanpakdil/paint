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
public class HighlighterDrawTest {
    private DrawArea canvas;
    private SideMenu sideMenu;
    private JFrame frame; // optional visualization frame

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments (CI without DISPLAY) for UI-coupled rendering
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
        SwingUtilities.invokeAndWait(() -> {
            try {
                sideMenu = new SideMenu();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            canvas = new DrawArea(null); // DrawArea consults SideMenu's static state
            // Start from a known canvas size
            canvas.resizeCanvas(200, 120);
            // Reset static layers to ensure test isolation (no residue from previous tests)
            int w = canvas.getCanvasWidth();
            int h = canvas.getCanvasHeight();
            DrawArea.cache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gInit = DrawArea.cache.createGraphics();
            try {
                gInit.setColor(Color.WHITE);
                gInit.fillRect(0, 0, w, h);
            } finally {
                gInit.dispose();
            }
            DrawArea.highlightLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

            // Always show a window for this UI-coupled test (when not headless)
            frame = new JFrame("HighlighterDrawTest Viewer");
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
    void highlighter_drawsOnSeparateLayer_andDoesNotAccumulate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Configure highlighter
            sideMenu.setHighlighterOpacity(30); // 30%
            SideMenu.setForeColor(new Color(255, 255, 0)); // yellow
            sideMenu.selectTool(Tool.HIGHLIGHTER);
            sideMenu.setStrokeSize(12);

            // Draw the same stroke twice over the same path
            press(canvas, 20, 30);
            drag(canvas, 20, 30, 140, 30);
            release(canvas, 140, 30);

            press(canvas, 20, 30);
            drag(canvas, 20, 30, 140, 30);
            release(canvas, 140, 30);

            // Check that base canvas is untouched (white) beneath the stroke area
            int baseRgb = DrawArea.cache.getRGB(80, 30);
            assertEquals(Color.WHITE.getRGB(), baseRgb, "Base layer should remain white under highlight");

            // Check highlight layer has a translucent pixel at the stroke location
            int hlArgb = DrawArea.highlightLayer.getRGB(80, 30);
            int alpha = (hlArgb >>> 24) & 0xFF;
            assertTrue(alpha > 0, "Highlight layer should contain alpha at drawn pixel");

            // Non-accumulating behavior: drawing twice should NOT double alpha beyond configured opacity
            // Expected alpha approximately equals configured 30% of 255 = 76.5 -> around 77 (+/- tolerance due to antialiasing)
            int expectedAlpha = Math.round(255 * 0.30f);
            assertTrue(Math.abs(alpha - expectedAlpha) <= 30, "Alpha should be near configured opacity (got=" + alpha + ")");

            // Optional screenshot when visualization is enabled
            maybeSaveScreenshot("highlighter-layer");
        });
    }

    @Test
    @Order(2)
    void flattenedImage_containsHighlightOverWhite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Configure
            sideMenu.setHighlighterOpacity(50); // 50%
            SideMenu.setForeColor(new Color(0, 255, 0)); // green
            sideMenu.selectTool(Tool.HIGHLIGHTER);
            sideMenu.setStrokeSize(10);

            // Draw a short stroke
            press(canvas, 10, 60);
            drag(canvas, 10, 60, 60, 60);
            release(canvas, 60, 60);

            // Flatten
            Image img = DrawArea.getFlattenedImage();
            assertNotNull(img, "Flattened image should not be null");
            int out = ((java.awt.image.BufferedImage) img).getRGB(30, 60);
            // Over white background with 50% green highlight -> resulting green channel should be high
            int r = (out >> 16) & 0xFF;
            int g = (out >> 8) & 0xFF;
            int b = (out) & 0xFF;
            assertTrue(g > r && g > b, "Flattened pixel should be greenish (g>r,b)");
        });
    }

    @Test
    @Order(3)
    void undoRedo_restoresHighlightLayer_independently() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Draw a base pixel with pencil
            sideMenu.selectTool(Tool.PENCIL);
            SideMenu.setForeColor(Color.BLACK);
            sideMenu.setStrokeSize(3);
            press(canvas, 50, 20);
            drag(canvas, 50, 20, 55, 20);
            release(canvas, 55, 20);
            int baseAfterPencil = DrawArea.cache.getRGB(50, 20);
            assertEquals(Color.BLACK.getRGB(), baseAfterPencil, "Expected base pixel drawn by pencil");

            // Draw a highlight
            sideMenu.selectTool(Tool.HIGHLIGHTER);
            sideMenu.setHighlighterOpacity(40);
            SideMenu.setForeColor(Color.YELLOW);
            press(canvas, 50, 22);
            drag(canvas, 50, 22, 90, 22);
            release(canvas, 90, 22);
            int hlAlpha = (DrawArea.highlightLayer.getRGB(60, 22) >>> 24) & 0xFF;
            assertTrue(hlAlpha > 0, "Highlight pixel should exist before undo");

            // Undo should remove highlight but keep base pencil.
            canvas.undo();
            int hlAlphaAfterUndo = (DrawArea.highlightLayer.getRGB(60, 22) >>> 24) & 0xFF;
            // Be tolerant across platforms: require a significant drop in alpha
            assertTrue(hlAlphaAfterUndo < hlAlpha / 2, "Highlight should be significantly reduced after undo (before=" + hlAlpha + ", after=" + hlAlphaAfterUndo + ")");
            assertEquals(Color.BLACK.getRGB(), DrawArea.cache.getRGB(50, 20), "Base pencil should remain after undo");

            // Redo should restore highlight (alpha increases again)
            canvas.redo();
            int hlAlphaAfterRedo = (DrawArea.highlightLayer.getRGB(60, 22) >>> 24) & 0xFF;
            assertTrue(hlAlphaAfterRedo >= hlAlpha - 20, "Highlight should be restored by redo");
        });
    }

    // --- mouse helpers (dispatch minimal events to trigger DrawArea logic) ---
    private static void press(Component c, int x, int y) {
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
    private static void drag(Component c, int x1, int y1, int x2, int y2) {
        int steps = 12;
        for (int i = 1; i <= steps; i++) {
            int xi = x1 + (x2 - x1) * i / steps;
            int yi = y1 + (y2 - y1) * i / steps;
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK, xi, yi, 0, false, java.awt.event.MouseEvent.BUTTON1));
        }
    }
    private static void release(Component c, int x, int y) {
        c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    // --- optional screenshot helper (enabled when -DshowUITests=true) ---
    private static void maybeSaveScreenshot(String prefix) {
        if (!Boolean.parseBoolean(System.getProperty("showUITests", "false"))) return;
        try {
            BufferedImage out = DrawArea.getFlattenedImage();
            if (out != null) {
                new File("target/test-screenshots").mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new Date());
                File f = new File("target/test-screenshots/" + prefix + "-" + ts + ".png");
                ImageIO.write(out, "png", f);
                System.out.println("[DEBUG_LOG] Saved highlighter test screenshot to: " + f.getAbsolutePath());
            }
        } catch (Exception ignore) {}
    }
}
