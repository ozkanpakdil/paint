package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaintUiTest {
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
    void drawsALineOnTheCanvas() {
        // Select Line tool (T1)
        Component lineTool = findByName(frame, "T1");
        assertNotNull(lineTool, "Line tool (T1) not found");
        click(lineTool, 5, 5);
        sleep(100);

        // Drag on the canvas from (20,20) to (120,120)
        drag(canvas, 20, 20, 120, 120);

        // Assert a pixel changed from white
        int rgb = canvas.getPixelRGB(60, 60);
        assertNotEquals(Color.WHITE.getRGB(), rgb, "Expected drawn pixel to be not white");
    }

    @Test
    @Order(2)
    void bucketFillsInsideADrawnRectangle() {
        // Rectangle tool (T2)
        Component rectTool = findByName(frame, "T2");
        assertNotNull(rectTool, "Rectangle tool (T2) not found");
        click(rectTool, 5, 5);
        sleep(100);

        // Draw rectangle outline
        drag(canvas, 30, 30, 180, 140);
        sleep(100);

        // Bucket tool (T10)
        Component bucketTool = findByName(frame, "T10");
        assertNotNull(bucketTool, "Bucket tool (T10) not found");
        click(bucketTool, 5, 5);
        sleep(80);

        // Click inside rectangle to fill
        click(canvas, 40, 40);
        sleep(150);

        int rgb = canvas.getPixelRGB(40, 40);
        assertEquals(Color.BLACK.getRGB(), rgb, "Expected filled pixel to be black");
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

    private static void drag(Component target, int x1, int y1, int x2, int y2) {
        // Simple straight-line drag with some steps
        int steps = 12;
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, x1, y1, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, x1, y1, MouseEvent.BUTTON1_DOWN_MASK);
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
