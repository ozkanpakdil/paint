package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class SelectAllCopyTest {
    private JFrame frame;
    private DrawArea canvas;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments
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
        waitFor(() -> frame != null && frame.isShowing(), 5000, "Main frame not visible");

        Component c = findByName(frame, "drawArea");
        canvas = (DrawArea) c;
        sleep(150);
    }

    @AfterEach
    void tearDown() {
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    void testSelectAllAndCopy() throws Exception {
        // 1. Draw something on the canvas
        Component pencilTool = findByName(frame, "T0");
        click(pencilTool, 5, 5);
        drag(canvas, 10, 10, 50, 50);
        
        // Verify something was drawn
        int rgb = canvas.getPixelRGB(30, 30);
        assertNotEquals(Color.WHITE.getRGB(), rgb, "Should have drawn something");

        // 2. Press Ctrl+A (Select All)
        typeKeyWithMask(canvas, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
        sleep(200);

        // 3. Press Ctrl+C (Copy)
        typeKeyWithMask(canvas, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        sleep(200);

        // 4. Verify clipboard contains an image
        Image clipboardImage = (Image) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
        assertNotNull(clipboardImage, "Clipboard should contain an image");
        
        BufferedImage bi = toBufferedImage(clipboardImage);
        assertEquals(canvas.getWidth(), bi.getWidth(), "Clipboard image width should match canvas");
        assertEquals(canvas.getHeight(), bi.getHeight(), "Clipboard image height should match canvas");
        
        // Check if the drawn pixel is in the copied image
        int copiedRgb = bi.getRGB(30, 30);
        assertEquals(rgb, copiedRgb, "Copied image should contain the drawn pixels");
    }

    // ---------- helpers ----------

    private static Component findByName(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                Component c = findByName(child, name);
                if (c != null) return c;
            }
        }
        return null;
    }

    private static void click(Component target, int x, int y) {
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1_DOWN_MASK);
        sleep(30);
    }

    private static void drag(Component target, int x1, int y1, int x2, int y2) {
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1_DOWN_MASK);
        int steps = 5;
        for (int i = 1; i <= steps; i++) {
            int xi = x1 + (x2 - x1) * i / steps;
            int yi = y1 + (y2 - y1) * i / steps;
            dispatchMouse(target, MouseEvent.MOUSE_DRAGGED, xi, yi, MouseEvent.BUTTON1_DOWN_MASK);
            sleep(10);
        }
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, x2, y2, MouseEvent.BUTTON1_DOWN_MASK);
        sleep(30);
    }

    private static void dispatchMouse(Component target, int id, int x, int y, int modifiers) {
        MouseEvent ev = new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, MouseEvent.BUTTON1);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
    }

    private static void typeKeyWithMask(Component target, int keyCode, int modifiers) {
        KeyEvent pressed = new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
        KeyEvent released = new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
        EventQueue.invokeLater(() -> {
            target.dispatchEvent(pressed);
            target.dispatchEvent(released);
        });
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

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return bi;
    }

    @FunctionalInterface
    private interface Check { boolean ok(); }
}
