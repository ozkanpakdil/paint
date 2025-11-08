package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CropSelectionTest {
    private JFrame frame;
    private DrawArea canvas;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments (CI without DISPLAY)
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");

        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                frame = new Main();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Main window did not initialize in time");

        // Wait until frame is visible and ready
        waitFor(() -> frame != null && frame.isShowing(), 5000, "Main frame not visible");

        // Find draw area by name
        Component c = findByName(frame, "drawArea");
        assertNotNull(c, "drawArea component not found");
        assertTrue(c instanceof DrawArea, "drawArea is not a DrawArea instance");
        canvas = (DrawArea) c;

        // Give Swing a moment to lay out
        sleep(150);
    }

    @AfterEach
    void tearDown() {
        if (frame != null) {
            try { frame.dispose(); } catch (Exception ignored) {}
        }
    }

    @Test
    @Order(1)
    void cropToSelection_duringPlacement_resizesToSelection() throws Exception {
        // 1) Draw a simple stroke to have some non-white content
        clickByName(frame, "T0"); // Pencil
        sleep(60);
        drag(canvas, 40, 40, 160, 40);
        // Basic sanity: we have some non-white on canvas
        assertTrue(regionHasNonWhite(canvas, 0, 0, Math.max(200, canvas.getWidth()), Math.max(200, canvas.getHeight())),
                "Expected drawn content before selection");

        // 2) Switch to MOVE and drag a selection rectangle; on release it enters placement state
        int sx1 = 30, sy1 = 20, sx2 = 170, sy2 = 80; // selection bounds fully within canvas
        int expectedW = Math.abs(sx2 - sx1);
        int expectedH = Math.abs(sy2 - sy1);
        clickByName(frame, "T11"); // Move tool
        sleep(60);
        drag(canvas, sx1, sy1, sx2, sy2); // mouseReleased will cut into pendingImage and enter placement
        sleep(120);

        // 3) Invoke cropToSelection while in placement state
        SwingUtilities.invokeAndWait(() -> canvas.cropToSelection());
        sleep(80);

        // 4) Assert canvas resized to selection size
        assertNotNull(DrawArea.cache, "cache should exist after crop");
        assertEquals(expectedW, DrawArea.cache.getWidth(), "Cache width should match selection width");
        assertEquals(expectedH, DrawArea.cache.getHeight(), "Cache height should match selection height");

        // 5) And there should be some non-white content inside
        assertTrue(regionHasNonWhite(canvas, 0, 0, expectedW, expectedH),
                "Expected non-white pixels after crop");
    }

    @Test
    @Order(2)
    void cropToSelection_withoutAnySelection_isNoOpForSize() throws Exception {
        // Record current size
        int wBefore = canvas.getCanvasWidth();
        int hBefore = canvas.getCanvasHeight();

        // Ensure we are not in placement and no marquee exists; select PENCIL and click once
        clickByName(frame, "T0"); // Pencil
        sleep(60);
        click(canvas, 10, 10);
        sleep(40);

        // Call cropToSelection (should beep/no-op), and verify size unchanged
        SwingUtilities.invokeAndWait(() -> canvas.cropToSelection());
        sleep(40);

        assertEquals(wBefore, canvas.getCanvasWidth(), "Canvas width should remain unchanged without selection");
        assertEquals(hBefore, canvas.getCanvasHeight(), "Canvas height should remain unchanged without selection");
    }

    @Test
    @Order(3)
    void cropToSelection_duringPastedImagePlacement_resizesToImage() throws Exception {
        // Create a small in-memory image with distinct opaque content
        BufferedImage img = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(Color.BLACK);
            g.fillRect(5, 5, 40, 30);
        } finally {
            g.dispose();
        }

        // Start image placement but DO NOT commit (no double-click / Enter)
        SwingUtilities.invokeAndWait(() -> canvas.startImagePlacement(img));
        sleep(120);

        // Directly invoke cropToSelection while placement is active
        SwingUtilities.invokeAndWait(() -> canvas.cropToSelection());
        sleep(80);

        // Verify the canvas now matches the pasted image dimensions
        assertNotNull(DrawArea.cache, "cache should exist after crop during pasted placement");
        assertEquals(img.getWidth(), DrawArea.cache.getWidth(), "Width should match pasted image width");
        assertEquals(img.getHeight(), DrawArea.cache.getHeight(), "Height should match pasted image height");

        // And ensure there are non-white pixels (the black rect)
        assertTrue(regionHasNonWhite(canvas, 0, 0, img.getWidth(), img.getHeight()),
                "Expected non-white content from pasted image after crop");
    }

    // ---------- helpers (mirroring patterns from existing UI tests) ----------

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

    private static void clickByName(Component root, String name) {
        Component c = findByName(root, name);
        assertNotNull(c, "Component '" + name + "' not found");
        Rectangle b = c.getBounds();
        // Target center of the component
        int cx = b.width / 2;
        int cy = b.height / 2;
        // Dispatch events to the component itself
        dispatchMouse(c, MouseEvent.MOUSE_MOVED, cx, cy, 0);
        dispatchMouse(c, MouseEvent.MOUSE_ENTERED, cx, cy, 0);
        dispatchMouse(c, MouseEvent.MOUSE_PRESSED, cx, cy, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(c, MouseEvent.MOUSE_RELEASED, cx, cy, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(c, MouseEvent.MOUSE_CLICKED, cx, cy, 0);
        sleep(30);
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
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1_DOWN_MASK);
        int steps = 16;
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

    private static void waitFor(Check cond, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.ok()) return;
            sleep(50);
        }
        fail(message);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @FunctionalInterface
    private interface Check { boolean ok(); }
}
