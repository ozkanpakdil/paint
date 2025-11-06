package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CropBehaviorTest {
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
            } catch (IOException e) {
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
    void cropToLastPastedImage_thenResizeWindow_noWhiteMarginsAndNoRegrow() throws Exception {
        // Create a small in-memory image (distinct size) to place
        BufferedImage img = new BufferedImage(80, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(Color.BLACK);
            g.fillRect(10, 10, 60, 40); // some opaque content
        } finally {
            g.dispose();
        }

        // Start image placement on EDT
        SwingUtilities.invokeAndWait(() -> canvas.startImagePlacement(img));
        sleep(100);

        // Double-click anywhere on canvas to commit placement
        doubleClick(canvas, 15, 15);
        sleep(80);

        // Perform crop to last pasted image
        SwingUtilities.invokeAndWait(() -> canvas.cropToImageSize());
        sleep(50);

        // Assert cache dimensions equal to pasted image
        assertNotNull(DrawArea.cache, "cache should be initialized after placement and crop");
        assertEquals(img.getWidth(), DrawArea.cache.getWidth(), "Cache width should match cropped image width");
        assertEquals(img.getHeight(), DrawArea.cache.getHeight(), "Cache height should match cropped image height");

        int wBefore = DrawArea.cache.getWidth();
        int hBefore = DrawArea.cache.getHeight();

        // Enlarge the window/component size and force layout/paint
        SwingUtilities.invokeAndWait(() -> {
            canvas.setPreferredSize(new Dimension(600, 400));
            frame.pack();
            canvas.revalidate();
            canvas.repaint();
        });
        sleep(120);

        // Force a paint pass
        SwingUtilities.invokeAndWait(() -> canvas.paintImmediately(0, 0, canvas.getWidth(), canvas.getHeight()));
        sleep(50);

        // Ensure cache did NOT regrow with the window size
        assertEquals(wBefore, DrawArea.cache.getWidth(), "Cache width should not change after window resize");
        assertEquals(hBefore, DrawArea.cache.getHeight(), "Cache height should not change after window resize");

        // Save cache to a temp file and verify saved image dimensions match
        File tmp = File.createTempFile("paint-crop-test", ".png");
        try {
            assertTrue(ImageIO.write(DrawArea.cache, "png", tmp), "Should write PNG successfully");
            BufferedImage readBack = ImageIO.read(tmp);
            assertNotNull(readBack, "Saved image should be readable");
            assertEquals(wBefore, readBack.getWidth(), "Saved PNG width should match cache width");
            assertEquals(hBefore, readBack.getHeight(), "Saved PNG height should match cache height");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    // ---------- helpers ----------

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

    private static void click(Component target, int x, int y) {
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x, y, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x, y, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_CLICKED, x, y, 0);
        sleep(30);
    }

    private static void doubleClick(Component target, int x, int y) {
        // First click
        click(target, x, y);
        // Second click with clickCount=2
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x, y, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x, y, 0);
        long when = System.currentTimeMillis();
        int modifiers = MouseEvent.BUTTON1_DOWN_MASK;
        MouseEvent pressed = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, when, modifiers, x, y, 2, false, MouseEvent.BUTTON1);
        MouseEvent released = new MouseEvent(target, MouseEvent.MOUSE_RELEASED, when, modifiers, x, y, 2, false, MouseEvent.BUTTON1);
        MouseEvent clicked = new MouseEvent(target, MouseEvent.MOUSE_CLICKED, when, 0, x, y, 2, false, MouseEvent.BUTTON1);
        EventQueue.invokeLater(() -> { target.dispatchEvent(pressed); target.dispatchEvent(released); target.dispatchEvent(clicked); });
        sleep(50);
    }

    private static void dispatchMouse(Component target, int id, int x, int y, int modifiers) {
        long when = System.currentTimeMillis();
        int clickCount = (id == MouseEvent.MOUSE_CLICKED) ? 1 : 0;
        MouseEvent ev = new MouseEvent(target, id, when, modifiers, x, y, clickCount, false, MouseEvent.BUTTON1);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
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
