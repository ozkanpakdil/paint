package io.github.ozkanpakdil.paint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Non-recursive scanline flood fill.
 * <p>
 * Simplifications/modernizations:
 * - Always works on an ARGB copy of the input Image (keeps previous behavior stable).
 * - Uses ArrayDeque instead of LinkedList for the work queue.
 * - Early exits for out-of-bounds and same-color seeds.
 * - Uses consistent precomputed row offsets to reduce repeated multiplications.
 */
public class ScanlineFloodFill {

    /**
     * Fills the contiguous region starting at (xSeed,ySeed) that has the same color as the seed pixel,
     * replacing it with the provided color. Returns a new {@link BufferedImage} (TYPE_INT_ARGB)
     * containing the result. The original image is not modified.
     */
    public BufferedImage fill(Image img, int xSeed, int ySeed, Color col) {
        Objects.requireNonNull(img, "img");
        Objects.requireNonNull(col, "col");

        int w = Math.max(1, img.getWidth(null));
        int h = Math.max(1, img.getHeight(null));

        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.getGraphics().drawImage(img, 0, 0, null);

        if (xSeed < 0 || xSeed >= w || ySeed < 0 || ySeed >= h) {
            return bi; // seed out of bounds -> nothing to fill
        }

        int[] pixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        int oldColor = pixels[ySeed * w + xSeed];
        int fillColor = col.getRGB();

        if (oldColor == fillColor) {
            return bi; // nothing to do
        }

        floodIt(pixels, xSeed, ySeed, w, h, oldColor, fillColor);
        return bi;
    }

    private void floodIt(int[] pixels, int x, int y, int width, int height, int oldColor, int fillColor) {
        // Work queue of points to process
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.addLast(new int[]{x, y});

        while (!stack.isEmpty()) {
            int[] p = stack.pollLast();
            x = p[0];
            y = p[1];

            int yp = y * width;
            // Skip if this seed is no longer target color (can happen due to overlaps)
            if (y < 0 || y >= height || x < 0 || x >= width || pixels[yp + x] != oldColor) {
                continue;
            }

            // Expand to the right
            int xr = x;
            while (xr < width && pixels[yp + xr] == oldColor) {
                pixels[yp + xr] = fillColor;
                xr++;
            }

            // Expand to the left
            int xl = x - 1;
            while (xl >= 0 && pixels[yp + xl] == oldColor) {
                pixels[yp + xl] = fillColor;
                xl--;
            }

            // Now [xl+1, xr-1] is the filled horizontal span at row y
            int left = xl + 1;
            int right = xr - 1;

            // Check the two neighboring rows for spans that still have oldColor.
            boolean upPending = false;
            boolean downPending = false;

            int yUp = y - 1;
            int yDown = y + 1;
            int ypUp = yUp * width;
            int ypDown = yDown * width;

            for (int xi = left; xi <= right; xi++) {
                // Row above
                if (yUp >= 0) {
                    if (pixels[ypUp + xi] == oldColor) {
                        if (!upPending) {
                            stack.addLast(new int[]{xi, yUp});
                            upPending = true;
                        }
                    } else {
                        upPending = false;
                    }
                }
                // Row below
                if (yDown < height) {
                    if (pixels[ypDown + xi] == oldColor) {
                        if (!downPending) {
                            stack.addLast(new int[]{xi, yDown});
                            downPending = true;
                        }
                    } else {
                        downPending = false;
                    }
                }
            }
        }
    }
}