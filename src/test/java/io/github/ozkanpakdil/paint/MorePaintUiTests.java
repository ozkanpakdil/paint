package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MorePaintUiTests {
    private JFrame frame;
    private DrawArea canvas;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments (CI without DISPLAY)
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");

        SwingUtilities.invokeAndWait(() -> {
            try {
                frame = new Main();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertNotNull(frame, "Main frame not created");
        assertTrue(frame.isShowing(), "Main frame not visible");

        Component c = findByName(frame, "drawArea");
        assertNotNull(c, "drawArea component not found");
        assertTrue(c instanceof DrawArea, "drawArea is not a DrawArea instance");
        canvas = (DrawArea) c;
        // Clear canvas to isolate each test
        SwingUtilities.invokeAndWait(canvas::clearCanvas);
        sleep(120);
    }

    @AfterEach
    void tearDown() {
        if (frame != null) frame.dispose();
    }

    @Test
    @Order(1)
    void pencilDrawsWithSelectedBlueColor() {
        // Select BLUE color (F1)
        clickByName("F1");
        // Select Pencil (T0)
        clickByName("T0");
        sleep(60);
        // Draw a short stroke
        drag(canvas, 50, 50, 120, 50);
        int rgb = canvas.getPixelRGB(80, 50);
        assertEquals(Color.BLUE.getRGB(), rgb, "Expected blue stroke pixel");
    }

    @Test
    @Order(2)
    void eraserErasesToWhite() {
        // Draw black line first (default color black)
        clickByName("T1"); // line tool draws outline on release
        drag(canvas, 40, 80, 180, 80);
        int before = canvas.getPixelRGB(100, 80);
        assertNotEquals(Color.WHITE.getRGB(), before, "Expected drawn content before erase");

        // Erase over the line
        clickByName("T5"); // eraser
        drag(canvas, 90, 76, 110, 84); // small sweep across the line
        int after = canvas.getPixelRGB(100, 80);
        assertEquals(Color.WHITE.getRGB(), after, "Expected erased pixel to be white");
    }

    @Test
    @Order(3)
    void drawsOvalOutline() {
        clickByName("F0"); // ensure BLACK color
        clickByName("T3"); // oval outline
        drag(canvas, 200, 40, 320, 120);
        // Allow EDT to commit the shape to the cache
        sleep(200);
        // Scan a broad area including perimeter to tolerate stroke placement
        boolean found = regionHasNonWhite(canvas, 198, 38, 126, 86);
        assertTrue(found, "Expected oval perimeter to be drawn (non-white pixels within bounding area)");
    }

    @Test
    @Order(4)
    void drawsFilledOvalWithSelectedOrange() {
        clickByName("F8"); // ORANGE
        clickByName("T8"); // filled oval
        drag(canvas, 360, 40, 480, 120);
        // Sample an interior point (center)
        int rgb = canvas.getPixelRGB(420, 80);
        assertEquals(Color.ORANGE.getRGB(), rgb, "Expected filled oval interior to be Color.ORANGE");
    }

    @Test
    @Order(5)
    void writesTextWithSelectedRed() throws Exception {
        clickByName("F10"); // RED
        clickByName("T6");  // TEXT tool
        // Click to create inline editor at (100, 180)
        click(canvas, 100, 180);
        sleep(120);
        // Find the inline JTextField inside canvas
        JTextField editor = (JTextField) findChildOfType(canvas, JTextField.class);
        assertNotNull(editor, "Inline text editor not found");
        // Type text and press Enter to commit
        SwingUtilities.invokeAndWait(() -> {
            editor.requestFocusInWindow();
            editor.setText("Hi");
        });
        // Simulate ENTER key press+release to commit
        dispatchKey(editor, KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n');
        dispatchKey(editor, KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n');
        sleep(220);
        // Verify pixels around the text area are close to RED (allow anti-aliased shades)
        boolean foundRed = regionHasColorNear(canvas, 90, 160, 140, 70, Color.RED.getRGB(), 60);
        assertTrue(foundRed, "Expected to find red-ish pixels from committed text");
    }

    // ---------- helpers ----------

    private void clickByName(String name) {
        Component c = findByName(frame, name);
        assertNotNull(c, "Component '" + name + "' not found");
        click(c, 5, 5);
    }

    private static Component findByName(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof JMenuBar mb) {
            for (int i = 0; i < mb.getMenuCount(); i++) {
                Component c = findByName(mb.getMenu(i), name);
                if (c != null) return c;
            }
        }
        if (root instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                Component c = findByName(child, name);
                if (c != null) return c;
            }
        }
        return null;
    }

    private static Component findChildOfType(Component root, Class<?> type) {
        if (type.isInstance(root)) return root;
        if (root instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                Component c = findChildOfType(child, type);
                if (c != null) return c;
            }
        }
        return null;
    }

    private static void click(Component target, int x, int y) {
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x, y, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x, y, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_CLICKED, x, y, 0);
        sleep(30);
    }

    private static void drag(Component target, int x1, int y1, int x2, int y2) {
        int steps = 12;
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= steps; i++) {
            int xi = x1 + (x2 - x1) * i / steps;
            int yi = y1 + (y2 - y1) * i / steps;
            dispatchMouse(target, MouseEvent.MOUSE_DRAGGED, xi, yi, MouseEvent.BUTTON1_DOWN_MASK);
            sleep(8);
        }
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, x2, y2, MouseEvent.BUTTON1_DOWN_MASK);
        sleep(30);
    }

    private static void dispatchMouse(Component target, int id, int x, int y, int modifiers) {
        long when = System.currentTimeMillis();
        int clickCount = (id == MouseEvent.MOUSE_CLICKED) ? 1 : 0;
        MouseEvent ev = new MouseEvent(target, id, when, modifiers, x, y, clickCount, false, MouseEvent.BUTTON1);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
    }

    private static void dispatchKey(Component target, int id, int keyCode, char keyChar) {
        long when = System.currentTimeMillis();
        KeyEvent ev = new KeyEvent(target, id, when, 0, keyCode, keyChar);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
    }

    private static boolean regionHasColor(DrawArea area, int x, int y, int w, int h, int rgb) {
        int x2 = x + w;
        int y2 = y + h;
        for (int i = Math.max(0, x); i < x2; i++) {
            for (int j = Math.max(0, y); j < y2; j++) {
                try {
                    if (area.getPixelRGB(i, j) == rgb) return true;
                } catch (IllegalArgumentException ignored) {
                    // outside image; skip
                }
            }
        }
        return false;
    }

    private static boolean regionHasNonWhite(DrawArea area, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        for (int i = Math.max(0, x); i < x2; i++) {
            for (int j = Math.max(0, y); j < y2; j++) {
                try {
                    if (area.getPixelRGB(i, j) != Color.WHITE.getRGB()) return true;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return false;
    }

    private static boolean regionHasColorNear(DrawArea area, int x, int y, int w, int h, int targetRgb, int tolerancePerChannel) {
        int x2 = x + w;
        int y2 = y + h;
        int tr = (targetRgb >> 16) & 0xFF;
        int tg = (targetRgb >> 8) & 0xFF;
        int tb = (targetRgb) & 0xFF;
        for (int i = Math.max(0, x); i < x2; i++) {
            for (int j = Math.max(0, y); j < y2; j++) {
                try {
                    int rgb = area.getPixelRGB(i, j);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = (rgb) & 0xFF;
                    if (Math.abs(r - tr) <= tolerancePerChannel && Math.abs(g - tg) <= tolerancePerChannel && Math.abs(b - tb) <= tolerancePerChannel) {
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // --- Additional helpers for new tests ---
    private static boolean regionHasColorAnywhere(DrawArea area, int rgb) {
        int w = Math.max(1, area.getWidth());
        int h = Math.max(1, area.getHeight());
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                try {
                    if (area.getPixelRGB(i, j) == rgb) return true;
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return false;
    }

    // --- New tests covering Open/Placement, Move tool, and Esc/Cancel cases ---

    @Test
    @Order(6)
    void imagePlacement_enterCommits_escCancels() throws Exception {
        // Create a small solid image to place (distinct color)
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, 40, 30);
        } finally { g.dispose(); }

        // Start placement (simulates File > Open… without JFileChooser UI)
        SwingUtilities.invokeAndWait(() -> canvas.startImagePlacement(img));
        // Commit with Enter
        canvas.requestFocusInWindow();
        dispatchKey(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n');
        dispatchKey(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n');
        sleep(200);
        assertTrue(regionHasColorAnywhere(canvas, Color.MAGENTA.getRGB()), "Expected placed image pixels after Enter to commit");

        // Clear and try Esc cancel path
        SwingUtilities.invokeAndWait(canvas::clearCanvas);
        SwingUtilities.invokeAndWait(() -> canvas.startImagePlacement(img));
        canvas.requestFocusInWindow();
        dispatchKey(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, (char)27);
        dispatchKey(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, (char)27);
        sleep(200);
        assertFalse(regionHasColorAnywhere(canvas, Color.MAGENTA.getRGB()), "Expected no image pixels after Esc to cancel");
    }

    @Test
    @Order(7)
    void moveSelection_enterCommits_sourceCleared_destinationHasPixels() {
        // Draw a filled rectangle (RED) to move
        clickByName("F10"); // RED
        clickByName("T7");  // filled rectangle
        drag(canvas, 40, 40, 80, 70);
        sleep(120);
        assertTrue(regionHasNonWhite(canvas, 35, 35, 60, 50), "Expected some non-white pixels in the drawn area");

        // Select MOVE tool
        clickByName("T11");
        // Create selection around the red rect
        drag(canvas, 35, 35, 85, 75);
        sleep(80);
        // Drag the selection to a new position
        drag(canvas, 50, 50, 120, 120);
        sleep(80);
        // Commit with Enter
        canvas.requestFocusInWindow();
        dispatchKey(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n');
        dispatchKey(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n');
        sleep(200);
        // Destination should have non-white pixels
        assertTrue(regionHasNonWhite(canvas, 110, 110, 50, 40), "Expected moved content at destination after Enter");
        // Source should be cleared to white
        assertFalse(regionHasNonWhite(canvas, 40, 40, 50, 40), "Expected source area cleared after move commit");
    }

    @Test
    @Order(8)
    void moveSelection_escCancels_restoresSource_noDestination() {
        // Draw a filled rectangle (BLUE) to move
        clickByName("F1"); // BLUE
        clickByName("T7");  // filled rectangle
        drag(canvas, 60, 90, 110, 130);
        sleep(120);
        assertTrue(regionHasNonWhite(canvas, 58, 88, 55, 45), "Expected non-white pixels in the drawn area");

        // Select MOVE tool and create selection around it
        clickByName("T11");
        drag(canvas, 55, 85, 115, 135);
        sleep(80);
        // Drag to new place but then cancel
        drag(canvas, 70, 100, 160, 180);
        canvas.requestFocusInWindow();
        dispatchKey(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, (char)27);
        dispatchKey(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, (char)27);
        sleep(200);
        // Original area remains (still non-white)
        assertTrue(regionHasNonWhite(canvas, 58, 88, 55, 45), "Expected original area restored after Esc");
        // Destination area should be white
        assertFalse(regionHasNonWhite(canvas, 150, 170, 60, 60), "Expected no moved content at destination after Esc");
    }

    @Test
    @Order(9)
    void textTool_escapeCancels_noPixelsCommitted() throws Exception {
        clickByName("F10"); // RED
        clickByName("T6");  // TEXT tool
        click(canvas, 200, 220);
        sleep(120);
        JTextField editor = (JTextField) findChildOfType(canvas, JTextField.class);
        assertNotNull(editor, "Inline text editor not found for text tool");
        SwingUtilities.invokeAndWait(() -> {
            editor.requestFocusInWindow();
            editor.setText("CancelMe");
        });
        // Press ESC to cancel editing (no commit)
        dispatchKey(editor, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, (char)27);
        dispatchKey(editor, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE, (char)27);
        sleep(220);
        // Verify region around where text would appear remains white (no red-ish pixels)
        boolean redish = regionHasColorNear(canvas, 190, 200, 160, 80, Color.RED.getRGB(), 60);
        assertFalse(redish, "Expected no red-ish pixels after Esc cancels text editing");
    }
}
