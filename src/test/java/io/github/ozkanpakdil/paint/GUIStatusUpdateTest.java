package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GUIStatusUpdateTest {
    private GUI gui;

    @BeforeEach
    void setUp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
        SwingUtilities.invokeAndWait(() -> {
            try {
                gui = new GUI();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @AfterEach
    void tearDown() {
        gui = null;
    }

    // Helper to find first JSpinner in GUI component tree
    private JSpinner findFirstSpinner(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JSpinner) return (JSpinner) comp;
            if (comp instanceof Container) {
                JSpinner s = findFirstSpinner((Container) comp);
                if (s != null) return s;
            }
        }
        return null;
    }

    @Test
    @Order(1)
    void statusBar_updates_whenCanvasCroppedToPlacement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DrawArea canvas = gui.getDrawArea();

            // Ensure a known starting size
            canvas.resizeCanvas(200, 150);
            assertEquals(200, canvas.getCanvasWidth());
            assertEquals(150, canvas.getCanvasHeight());

            // Start placing a small image (this sets placingImage and pendingImage)
            BufferedImage img = new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB);
            canvas.startImagePlacement(img);

            // Now crop to selection/placement (this should set canvas size to image size and fire property)
            canvas.cropToSelection();
        });

        // Let any invokeLater updates apply on EDT
        SwingUtilities.invokeAndWait(() -> {});

        // Now inspect GUI status components (spinners and message)
        SwingUtilities.invokeAndWait(() -> {
            // Collect all spinners and ensure one has the new width and one has the new height
            java.util.List<JSpinner> all = new java.util.ArrayList<>();
            collectSpinners(gui, all);
            boolean sawW = false, sawH = false;
            for (JSpinner s : all) {
                int v = ((Number) s.getValue()).intValue();
                if (v == 30) sawW = true;
                if (v == 20) sawH = true;
            }
            assertEquals(true, sawW, "width spinner should update to 30");
            assertEquals(true, sawH, "height spinner should update to 20");

            // Message label should reflect canvas size
            assertEquals("Canvas: 30 x 20", gui.message.getText());
        });

        // Now undo the crop and verify status returns to original canvas size
        SwingUtilities.invokeAndWait(() -> {
            DrawArea canvas = gui.getDrawArea();
            canvas.undo();
        });

        // Allow EDT updates and then check status
        SwingUtilities.invokeAndWait(() -> {});

        SwingUtilities.invokeAndWait(() -> {
            // Collect spinners and check for original size values
            java.util.List<JSpinner> all = new java.util.ArrayList<>();
            collectSpinners(gui, all);
            boolean sawOrigW = false, sawOrigH = false;
            for (JSpinner s : all) {
                int v = ((Number) s.getValue()).intValue();
                if (v == 200) sawOrigW = true;
                if (v == 150) sawOrigH = true;
            }
            assertEquals(true, sawOrigW, "width spinner should return to 200 after undo");
            assertEquals(true, sawOrigH, "height spinner should return to 150 after undo");
            assertEquals("Canvas: 200 x 150", gui.message.getText());
        });
    }

    private void collectSpinners(Container c, java.util.List<JSpinner> out) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JSpinner) out.add((JSpinner) comp);
            if (comp instanceof Container) collectSpinners((Container) comp, out);
        }
    }

    private JSpinner findSpinnerWithValue(Container c, int val) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JSpinner) {
                if (((Number) ((JSpinner) comp).getValue()).intValue() == val) return (JSpinner) comp;
            }
            if (comp instanceof Container) {
                JSpinner s = findSpinnerWithValue((Container) comp, val);
                if (s != null) return s;
            }
        }
        return null;
    }
}
