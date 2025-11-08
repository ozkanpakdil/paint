package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RibbonOpacityControlTest {
    private JFrame frame;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
        SwingUtilities.invokeAndWait(() -> {
            try {
                frame = new Main();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        waitForShowing(frame);
    }

    @AfterEach
    void tearDown() {
        if (frame != null) {
            try { frame.dispose(); } catch (Exception ignored) {}
        }
        frame = null;
    }

    @Test
    @Order(1)
    void colorsGroup_presentOnTopRight() {
        Component colorPreview = findByName(frame, "C0");
        assertNotNull(colorPreview, "Color preview (C0) should be present in Ribbon Colors group");
    }

    @Test
    @Order(2)
    void opacityEnabledOnlyForHighlighter_strokeAlwaysEnabled() {
        // Find sliders by name
        JSlider stroke = (JSlider) findByName(frame, "stroke");
        JSlider opacity = (JSlider) findByName(frame, "opacity");
        assertNotNull(stroke, "Stroke slider not found");
        assertNotNull(opacity, "Opacity slider not found");

        // Initially, default tool is Pencil; Opacity should be disabled, Stroke enabled
        assertTrue(stroke.isEnabled(), "Stroke must be enabled for all tools");
        assertFalse(opacity.isEnabled(), "Opacity must be disabled when Highlighter is not selected");

        // Click Highlighter tool button (T12)
        Component highlighterBtn = findByName(frame, "T12");
        assertNotNull(highlighterBtn, "Highlighter button (T12) not found");
        click(highlighterBtn);
        sleep(120);

        assertTrue(stroke.isEnabled(), "Stroke should remain enabled for Highlighter");
        assertTrue(opacity.isEnabled(), "Opacity should be enabled for Highlighter");

        // Click Line tool (T1)
        Component lineBtn = findByName(frame, "T1");
        assertNotNull(lineBtn, "Line tool (T1) not found");
        click(lineBtn);
        sleep(120);

        assertTrue(stroke.isEnabled(), "Stroke should be enabled for non-highlighter tools");
        assertFalse(opacity.isEnabled(), "Opacity should be disabled for non-highlighter tools");
    }

    // ---- helpers (same style as PaintUiTest) ----
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

    private static void click(Component target) {
        dispatchMouse(target, MouseEvent.MOUSE_MOVED, 5, 5, 0);
        dispatchMouse(target, MouseEvent.MOUSE_ENTERED, 5, 5, 0);
        dispatchMouse(target, MouseEvent.MOUSE_PRESSED, 5, 5, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_RELEASED, 5, 5, MouseEvent.BUTTON1_DOWN_MASK);
        dispatchMouse(target, MouseEvent.MOUSE_CLICKED, 5, 5, 0);
        sleep(40);
    }

    private static void dispatchMouse(Component target, int id, int x, int y, int modifiers) {
        long when = System.currentTimeMillis();
        int clickCount = (id == MouseEvent.MOUSE_CLICKED) ? 1 : 0;
        MouseEvent ev = new MouseEvent(target, id, when, modifiers, x, y, clickCount, false, MouseEvent.BUTTON1);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
    }

    private static void waitForShowing(Window w) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (w != null && w.isShowing()) return;
            sleep(50);
        }
        fail("Main frame not visible");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
