package io.github.ozkanpakdil.paint;

import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Tests for opening image files via CLI (command-line arguments).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenImageFromCLITest {
    private Main frame;
    private File tempImageFile;

    @BeforeEach
    void setUp() throws Exception {
        // Skip in headless environments (CI without DISPLAY)
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests require a display (not headless)");
    }

    @AfterEach
    void tearDown() {
        if (frame != null) {
            try {
                frame.dispose();
            } catch (Exception ignored) {
            }
        }
        if (tempImageFile != null && tempImageFile.exists()) {
            try {
                Files.delete(tempImageFile.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    void opensValidImageFileFromCLI() throws Exception {
        // Create a temporary test image
        tempImageFile = File.createTempFile("test_image", ".png");
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 100, 100);
        g2d.dispose();
        ImageIO.write(testImage, "png", tempImageFile);

        // Open Main with filename argument
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                frame = new Main(tempImageFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Main window did not initialize in time");

        // Wait until frame is visible and ready
        waitFor(() -> frame != null && frame.isShowing(), 5000, "Main frame not visible");

        // Find draw area
        Component c = findByName(frame, "drawArea");
        assertNotNull(c, "drawArea component not found");
        assertInstanceOf(DrawArea.class, c, "drawArea is not a DrawArea instance");
        DrawArea canvas = (DrawArea) c;

        // Give some time for image placement to start
        sleep(200);

        // The image is in placement mode - commit it via the component's ActionMap to avoid focus flakiness
        canvas.requestFocusInWindow();
        sleep(100);
        SwingUtilities.invokeAndWait(() -> {
            Action a = canvas.getActionMap().get("commitPlacement");
            if (a != null) {
                a.actionPerformed(new java.awt.event.ActionEvent(canvas, java.awt.event.ActionEvent.ACTION_PERFORMED, "commit"));
            }
        });
        sleep(200);

        // Now verify the image is on the canvas by scanning the flattened image for any non-white pixel
        boolean foundNonWhitePixel = false;
        BufferedImage flattened = DrawArea.getFlattenedImage();
        outer:
        for (int x = 0; x < flattened.getWidth(); x += 5) {
            for (int y = 0; y < flattened.getHeight(); y += 5) {
                int rgb = flattened.getRGB(x, y);
                if ((rgb & 0x00FFFFFF) != (Color.WHITE.getRGB() & 0x00FFFFFF)) { // ignore alpha
                    foundNonWhitePixel = true;
                    break outer;
                }
            }
        }
        assertTrue(foundNonWhitePixel, "Expected image to be loaded on canvas");
    }

    @Test
    @Order(2)
    void handlesNonExistentFileGracefully() throws Exception {
        String nonExistentFile = "/tmp/definitely_does_not_exist_12345.png";

        // Open Main with non-existent file. Note: constructor will show a modal error dialog
        // which blocks returning from the constructor; therefore, "frame" assignment inside
        // this Runnable would be delayed until the dialog is dismissed. To avoid flakiness,
        // we locate the showing Main frame from AWT's frames list instead of relying on the
        // constructor to return.
        SwingUtilities.invokeLater(() -> {
            try {
                new Main(nonExistentFile); // don't assign here; we'll discover it below
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait until the Main frame becomes visible, even if a modal dialog is shown
        waitFor(() -> {
            Main f = findVisibleMainFrame();
            if (f != null) {
                frame = f;
                return true;
            }
            return false;
        }, 5000, "Main frame not visible");

        // Dismiss any error dialogs (e.g., "File not found") so EDT is unblocked for later steps
        SwingUtilities.invokeAndWait(OpenImageFromCLITest::dismissAnyShowingJOptionPaneDialogs);

        // The application should still open (just without loading the image)
        assertNotNull(frame, "Frame should be created even with invalid file");
        assertTrue(frame.isShowing(), "Frame should be visible");
    }

    @Test
    @Order(3)
    void handlesNullFilenameGracefully() throws Exception {
        // Open Main with null filename (normal startup)
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                frame = new Main(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Main window did not initialize in time");

        // Wait until frame is visible
        waitFor(() -> frame != null && frame.isShowing(), 5000, "Main frame not visible");

        assertNotNull(frame, "Frame should be created with null filename");
        assertTrue(frame.isShowing(), "Frame should be visible");
    }

    @Test
    @Order(4)
    void handlesEmptyFilenameGracefully() throws Exception {
        // Open Main with empty filename
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                frame = new Main("");
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Main window did not initialize in time");

        // Wait until frame is visible
        waitFor(() -> frame != null && frame.isShowing(), 5000, "Main frame not visible");

        assertNotNull(frame, "Frame should be created with empty filename");
        assertTrue(frame.isShowing(), "Frame should be visible");
    }

    @Test
    @Order(5)
    void handlesInvalidImageFileGracefully() throws Exception {
        // Create a temporary text file (not an image)
        tempImageFile = File.createTempFile("not_an_image", ".txt");
        Files.writeString(tempImageFile.toPath(), "This is not an image file");

        // Open Main with invalid image file. The constructor will show a modal error dialog
        // (title: "Open Image") which blocks returning from the constructor. To avoid waiting
        // on the constructor, launch it without assigning to "frame" and discover the visible
        // Main frame via AWT once it shows up.
        SwingUtilities.invokeLater(() -> {
            try {
                new Main(tempImageFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait until the Main frame becomes visible, even if a modal dialog is shown
        waitFor(() -> {
            Main f = findVisibleMainFrame();
            if (f != null) {
                frame = f;
                return true;
            }
            return false;
        }, 5000, "Main frame not visible");

        // Dismiss any error dialogs (e.g., "Unsupported or corrupted image") so EDT is unblocked
        SwingUtilities.invokeAndWait(OpenImageFromCLITest::dismissAnyShowingJOptionPaneDialogs);

        // The application should still open (just without loading the corrupted image)
        assertNotNull(frame, "Frame should be created even with invalid image file");
        assertTrue(frame.isShowing(), "Frame should be visible");
    }

    // ---------- helpers ----------

    private static Main findVisibleMainFrame() {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof Main m && m.isShowing()) {
                return m;
            }
        }
        return null;
    }

    private static void dismissAnyShowingJOptionPaneDialogs() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog jd) {
                if (jd.isShowing()) {
                    String title = jd.getTitle();
                    if ("Open Image".equals(title)) {
                        jd.dispose();
                    }
                }
            }
        }
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

    private static void waitFor(Check cond, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.ok()) return;
            sleep(50);
        }
        fail(message);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static void dispatchKey(Component target, int id, int keyCode, char keyChar, boolean ctrl, boolean shift, boolean alt) {
        long when = System.currentTimeMillis();
        int modifiers = 0;
        if (ctrl) modifiers |= KeyEvent.CTRL_DOWN_MASK;
        if (shift) modifiers |= KeyEvent.SHIFT_DOWN_MASK;
        if (alt) modifiers |= KeyEvent.ALT_DOWN_MASK;
        KeyEvent ev = new KeyEvent(target, id, when, modifiers, keyCode, keyChar);
        EventQueue.invokeLater(() -> target.dispatchEvent(ev));
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }
}
