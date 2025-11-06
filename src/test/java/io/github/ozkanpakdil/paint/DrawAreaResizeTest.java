package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DrawAreaResizeTest {
    private DrawArea canvas;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments (CI without DISPLAY)
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
        SwingUtilities.invokeAndWait(() -> canvas = new DrawArea(null));
    }

    @AfterEach
    void tearDown() {
        // nothing special to dispose; canvas not shown in a frame here
        canvas = null;
    }

    @Test
    @Order(1)
    void enlarge_preservesPixels_and_fillsNewAreaWhite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            canvas.resizeCanvas(50, 40);
            assertEquals(50, canvas.getCanvasWidth());
            assertEquals(40, canvas.getCanvasHeight());

            // draw a black pixel at (10,10)
            assertNotNull(DrawArea.cache, "cache should be initialized by resizeCanvas");
            DrawArea.cache.setRGB(10, 10, Color.BLACK.getRGB());

            // Enlarge
            canvas.resizeCanvas(80, 60);
            assertEquals(80, canvas.getCanvasWidth());
            assertEquals(60, canvas.getCanvasHeight());

            // preserved pixel
            assertEquals(Color.BLACK.getRGB(), DrawArea.cache.getRGB(10, 10));
            // new area should be white (pick a coord that was outside old bounds)
            assertEquals(Color.WHITE.getRGB(), DrawArea.cache.getRGB(70, 50));
        });
    }

    @Test
    @Order(2)
    void shrink_clipsOutsideArea() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            canvas.resizeCanvas(60, 40);
            assertEquals(60, canvas.getCanvasWidth());
            assertEquals(40, canvas.getCanvasHeight());

            // Set a pixel near the old edge
            DrawArea.cache.setRGB(55, 35, Color.BLACK.getRGB());

            // Shrink
            canvas.resizeCanvas(30, 20);
            assertEquals(30, canvas.getCanvasWidth());
            assertEquals(20, canvas.getCanvasHeight());

            // Pixel beyond the new bounds should be clipped (can't read it anymore). Instead verify edge pixel is white.
            assertEquals(Color.WHITE.getRGB(), DrawArea.cache.getRGB(29, 19));
        });
    }

    @Test
    @Order(3)
    void undoRedo_restoresSizesAndPixels() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            canvas.resizeCanvas(20, 20);
            DrawArea.cache.setRGB(5, 5, Color.BLACK.getRGB());

            canvas.resizeCanvas(40, 40);
            assertEquals(40, canvas.getCanvasWidth());

            // Undo goes back to 20x20 with the black pixel still present
            canvas.undo();
            assertEquals(20, canvas.getCanvasWidth());
            assertEquals(20, canvas.getCanvasHeight());
            assertEquals(Color.BLACK.getRGB(), DrawArea.cache.getRGB(5, 5));

            // Redo returns to 40x40; pixel at (5,5) must remain
            canvas.redo();
            assertEquals(40, canvas.getCanvasWidth());
            assertEquals(40, canvas.getCanvasHeight());
            assertEquals(Color.BLACK.getRGB(), DrawArea.cache.getRGB(5, 5));
        });
    }
}
