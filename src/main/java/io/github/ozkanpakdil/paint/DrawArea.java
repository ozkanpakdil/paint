package io.github.ozkanpakdil.paint;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DrawArea extends JPanel implements MouseListener, MouseMotionListener {

    // ----- Undo/Redo history -----
    private static final int HISTORY_LIMIT = 25;
    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private final Deque<BufferedImage> redoStack = new ArrayDeque<>();

    private static final String[][] TOOL_ICON_MAP = new String[][]{
            {"PENCIL", "pencil.png"},
            {"LINE", "line-tool.png"},
            {"RECT", "rectangle.png"},
            {"RECT_FILLED", "rectangle_fill.png"},
            {"ROUNDED_RECT", "rectangle.png"},
            {"ROUNDED_RECT_FILLED", "rectangle_fill.png"},
            {"OVAL", "oval.png"},
            {"OVAL_FILLED", "oval_fill.png"},
            {"ERASER", "eraser.png"},
            {"TEXT", "text.png"},
            {"BUCKET", "bucket.png"},
            {"MOVE", "move.png"}
    };
    private static final int ROUNDED_ARC = 10;
    // Backing canvas; kept static to preserve existing usages (e.g., SideMenu save)
    static BufferedImage cache;
    // Cache of custom cursors per tool
    private final Map<Tool, Cursor> toolCursorCache = new EnumMap<>(Tool.class);
    // Text tool inline editor
    private final java.util.function.Supplier<SideMenu> controllerSupplier;
    // Mouse and drawing state (kept package-private compatibility minimal)
    public int x1, x2, y1, y2; // kept names to avoid broad refactor
    public boolean ispressed = false;
    public boolean isdragged = false;
    // Brush cursor preview state
    private int cursorX = -1;
    private int cursorY = -1;
    private boolean cursorVisible = false;
    // Tracks the bounds of the most recently pasted image (for cropping)
    private Rectangle lastPastedRect = null;
    // Temporary placement state for pasted/dropped images OR selection move
    private BufferedImage pendingImage = null;
    private int pendingX = 0;
    private int pendingY = 0;
    private boolean placingImage = false;
    private int pendingDragOffsetX = 0;
    private int pendingDragOffsetY = 0;
    // Rectangular selection state for Move tool
    private boolean selecting = false;
    private int selStartX = 0;
    private int selStartY = 0;
    private int selEndX = 0;
    private int selEndY = 0;
    private Rectangle selectionRect = null;
    private boolean selectionPlacement = false; // true when pendingImage came from a selection cut
    private BufferedImage selectionCutBackup = null; // pixels removed from cache for restoration on cancel
    private Rectangle selectionCutRect = null;
    private JTextField textEditor;

    // ----- History helpers -----
    private BufferedImage copyImage(BufferedImage src) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        try {
            g2.drawImage(src, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return dst;
    }

    private void pushUndoSnapshot() {
        ensureCache();
        undoStack.push(copyImage(cache));
        // Cap history size
        while (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        // New action invalidates redo history
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (!canUndo()) return;
        // Drop any transient overlays (selection/paste placement) so UI matches history state
        dropOverlayAndSelection();
        ensureCache();
        // Save current canvas to redo stack
        redoStack.push(copyImage(cache));
        // Restore previous canvas state
        cache = undoStack.pop();
        setPreferredSize(new Dimension(cache.getWidth(), cache.getHeight()));
        revalidate();
        repaint();
    }

    public void redo() {
        if (!canRedo()) return;
        // Drop any transient overlays before changing history state
        dropOverlayAndSelection();
        ensureCache();
        // Save current canvas to undo stack
        undoStack.push(copyImage(cache));
        // Restore next canvas state
        cache = redoStack.pop();
        setPreferredSize(new Dimension(cache.getWidth(), cache.getHeight()));
        revalidate();
        repaint();
    }

    DrawArea() {
        this(() -> null);
    }

    DrawArea(SideMenu controller) {
        this(() -> controller);
    }

    private DrawArea(java.util.function.Supplier<SideMenu> controllerSupplier) {
        this.controllerSupplier = controllerSupplier;
        initialize();
    }

    private Cursor buildCursorFromImage(BufferedImage img, String name, Point hotspot) {
        try {
            if (img == null) return Cursor.getDefaultCursor();
            Toolkit tk = Toolkit.getDefaultToolkit();
            Dimension best = tk.getBestCursorSize(32, 32);
            int w = best.width <= 0 ? 32 : best.width;
            int h = best.height <= 0 ? 32 : best.height;
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            Point hs = hotspot != null ? hotspot : new Point(1, 1);
            return tk.createCustomCursor(scaled, hs, name != null ? name : "tool");
        } catch (Exception ex) {
            return Cursor.getDefaultCursor();
        }
    }

    private BufferedImage loadToolIcon(Tool tool) {
        String res = null;
        for (String[] m : TOOL_ICON_MAP) {
            if (m[0].equals(tool.name())) {
                res = m[1];
                break;
            }
        }
        if (res == null) return null;
        try {
            java.io.InputStream in = SideMenu.class.getResourceAsStream("/images/" + res);
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (Exception ignored) {
        }
        // Fallback for move icon: draw simple cross-arrows
        if (tool == Tool.MOVE) {
            int size = 32;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0, 0, 0, 0));
                g.fillRect(0, 0, size, size);
                g.setColor(new Color(40, 40, 40));
                int cx = size / 2, cy = size / 2, arm = size / 3;
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(cx - arm, cy, cx + arm, cy);
                g.drawLine(cx, cy - arm, cx, cy + arm);
                Polygon left = new Polygon(new int[]{cx - arm, cx - arm + 6, cx - arm + 6}, new int[]{cy, cy - 4, cy + 4}, 3);
                Polygon right = new Polygon(new int[]{cx + arm, cx + arm - 6, cx + arm - 6}, new int[]{cy, cy - 4, cy + 4}, 3);
                Polygon up = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy - arm, cy - arm + 6, cy - arm + 6}, 3);
                Polygon down = new Polygon(new int[]{cx, cx - 4, cx + 4}, new int[]{cy + arm, cy + arm - 6, cy + arm - 6}, 3);
                g.fill(left);
                g.fill(right);
                g.fill(up);
                g.fill(down);
            } finally {
                g.dispose();
            }
            return img;
        }
        return null;
    }

    private Cursor getCursorForTool(Tool tool) {
        // Placement or selection move uses system MOVE cursor for clarity
        if (placingImage || tool == Tool.MOVE) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
        Cursor cached = toolCursorCache.get(tool);
        if (cached != null) return cached;
        BufferedImage img = loadToolIcon(tool);
        Cursor cur = buildCursorFromImage(img, tool.name().toLowerCase(), new Point(1, 1));
        toolCursorCache.put(tool, cur);
        return cur;
    }

    private void updateCursorForCurrentTool() {
        try {
            if (!isShowing()) return; // no need if not visible
            Tool t = SideMenu.getSelectedTool();
            if (placingImage) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return;
            }
            setCursor(getCursorForTool(t));
        } catch (Exception ignored) {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void initialize() {
        // Setup key bindings for Undo/Redo
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undoAction");
        getActionMap().put("undoAction", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undo();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redoAction");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "redoAction");
        getActionMap().put("redoAction", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                redo();
            }
        });
        setBackground(Color.WHITE);
        addMouseListener(this);
        addMouseMotionListener(this);
        setPreferredSize(new Dimension(700, 100));
        // Use default OS cursor initially; we will update based on selected tool
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        // Set initial cursor based on current tool
        SwingUtilities.invokeLater(this::updateCursorForCurrentTool);
        setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        // Allow absolute positioning of inline editor
        setLayout(null);

        // Listen to SideMenu property changes when available
        SideMenu ctrl = controllerSupplier.get();
        if (ctrl != null) {
            ctrl.addPropertyChangeListener(evt -> {
                switch (evt.getPropertyName()) {
                    case "tool" -> {
                        Tool newTool = (Tool) evt.getNewValue();
                        if (newTool != Tool.TEXT) {
                            // Commit current editor when leaving Text tool
                            commitEditorIfAny(true);
                        }
                        // Manage cursor and placement behavior on tool switch
                        if (newTool == Tool.MOVE) {
                            // Enter move mode: keep pending image as-is and use move cursor
                            if (!placingImage) {
                                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                            }
                        } else {
                            // Leaving Move: drop selection marquee if any
                            if (selecting) {
                                selecting = false;
                                selectionRect = null;
                                repaint();
                            }
                            // For any other tool, auto-commit pending placement if any
                            if (placingImage) {
                                commitPlacement();
                            }
                        }
                        // Update cursor to reflect selected tool (or placement state)
                        updateCursorForCurrentTool();
                    }
                    case "font", "fontSize" -> updateEditorFontFromState();
                    case "color" -> updateEditorColorFromState();
                }
            });
        }

        // Key binding: Ctrl+V to paste image from clipboard
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "pasteImage");
        getActionMap().put("pasteImage", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pasteFromClipboard();
            }
        });
        // Placement accept/abort keys
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commitPlacement");
        getActionMap().put("commitPlacement", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                commitPlacement();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPlacement");
        getActionMap().put("cancelPlacement", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelPlacement();
            }
        });

        // Enable drag-and-drop of images/files
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) return false;
                return support.isDataFlavorSupported(DataFlavor.imageFlavor)
                        || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Point p = support.getDropLocation().getDropPoint();
                    if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        Image img = (Image) support.getTransferable().getTransferData(DataFlavor.imageFlavor);
                        BufferedImage bi = toBufferedImage(img);
                        if (bi != null) {
                            enterPlacement(bi, p.x, p.y);
                            return true;
                        }
                    } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        for (File f : files) {
                            try {
                                BufferedImage bi = ImageIO.read(f);
                                if (bi != null) {
                                    enterPlacement(bi, p.x, p.y);
                                    return true;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                return false;
            }
        });
    }

    private void updateEditorFontFromState() {
        if (textEditor == null) return;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fonts = ge.getAvailableFontFamilyNames();
        int idx = SideMenu.getSelectedFont();
        int size = SideMenu.getFontSize();
        String family = (idx >= 0 && idx < fonts.length) ? fonts[idx] : textEditor.getFont().getFamily();
        textEditor.setFont(new Font(family, Font.PLAIN, size));
        // Adjust height to font metrics
        FontMetrics fm = getFontMetrics(textEditor.getFont());
        int h = fm.getHeight() + 6;
        textEditor.setSize(textEditor.getWidth(), h);
        repaint(textEditor.getBounds());
    }

    private void updateEditorColorFromState() {
        if (textEditor == null) return;
        textEditor.setForeground(SideMenu.getSelectedForeColor());
        repaint(textEditor.getBounds());
    }

    public String getName() {
        return super.getName();
    }

    public java.awt.image.BufferedImage getCacheImage() {
        ensureCache();
        return cache;
    }

    // Paste image from system clipboard at current cursor location (or 0,0)
    private void pasteFromClipboard() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                if (img == null) return;
                BufferedImage bi = toBufferedImage(img);
                if (bi == null) return;
                int px = (cursorX >= 0 ? cursorX : 0);
                int py = (cursorY >= 0 ? cursorY : 0);
                enterPlacement(bi, px, py);
            }
        } catch (Exception ignore) {
            // Silently ignore paste errors
        }
    }

    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage b) return b;
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage bimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bimg.createGraphics();
        try {
            g2.drawImage(img, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return bimg;
    }

    // ---------- Image placement mode ----------
    private void enterPlacement(BufferedImage img, int x, int y) {
        pendingImage = img;
        pendingX = x;
        pendingY = y;
        placingImage = true;
        updateCursorForCurrentTool();
        repaint();
    }

    private void commitPlacement() {
        if (!placingImage || pendingImage == null) return;
        // History snapshot before placing
        pushUndoSnapshot();
        ensureCache();
        int needW = Math.max(cache.getWidth(), pendingX + pendingImage.getWidth());
        int needH = Math.max(cache.getHeight(), pendingY + pendingImage.getHeight());
        if (needW > cache.getWidth() || needH > cache.getHeight()) {
            BufferedImage grown = (BufferedImage) createImage(needW, needH);
            var g = grown.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, needW, needH);
                g.drawImage(cache, 0, 0, null);
            } finally {
                g.dispose();
            }
            cache = grown;
        }
        var g2 = cache.createGraphics();
        try {
            applyRenderHints(g2);
            g2.drawImage(pendingImage, pendingX, pendingY, null);
        } finally {
            g2.dispose();
        }
        lastPastedRect = new Rectangle(pendingX, pendingY, pendingImage.getWidth(), pendingImage.getHeight());
        // Selection move was confirmed; discard backup
        selectionPlacement = false;
        selectionCutBackup = null;
        selectionCutRect = null;
        cancelPlacementInternal();
        repaint();
    }

    private void cancelPlacement() {
        if (!placingImage) return;
        cancelPlacementInternal();
        repaint();
    }

    private void cancelPlacementInternal() {
        // If we were moving a selection, restore the cut area
        if (selectionPlacement && selectionCutBackup != null && selectionCutRect != null) {
            ensureCache();
            Graphics2D g2 = cache.createGraphics();
            try {
                g2.drawImage(selectionCutBackup, selectionCutRect.x, selectionCutRect.y, null);
            } finally {
                g2.dispose();
            }
        }
        selectionPlacement = false;
        selectionCutBackup = null;
        selectionCutRect = null;
        placingImage = false;
        pendingImage = null;
        pendingDragOffsetX = 0;
        pendingDragOffsetY = 0;
        updateCursorForCurrentTool();
    }

    // Clear transient UI overlays (pending placement and selection marquee) without changing the canvas
    private void dropOverlayAndSelection() {
        // Do NOT restore selection cut back to cache here, otherwise redo history will break.
        placingImage = false;
        pendingImage = null;
        pendingDragOffsetX = 0;
        pendingDragOffsetY = 0;
        selectionPlacement = false;
        selectionCutBackup = null;
        selectionCutRect = null;
        selecting = false;
        selectionRect = null;
        updateCursorForCurrentTool();
    }

    private boolean pointInPending(int px, int py) {
        if (!placingImage || pendingImage == null) return false;
        return px >= pendingX && py >= pendingY && px < pendingX + pendingImage.getWidth() && py < pendingY + pendingImage.getHeight();
    }

    // Crop canvas to the last pasted image's size and position
    public void cropToImageSize() {
        if (cache == null || lastPastedRect == null) return;
        // History snapshot before crop
        pushUndoSnapshot();
        Rectangle r = lastPastedRect;
        // Clamp within cache bounds
        int x = Math.max(0, Math.min(r.x, cache.getWidth() - 1));
        int y = Math.max(0, Math.min(r.y, cache.getHeight() - 1));
        int w = Math.max(1, Math.min(r.width, cache.getWidth() - x));
        int h = Math.max(1, Math.min(r.height, cache.getHeight() - y));
        BufferedImage sub = cache.getSubimage(x, y, w, h);
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.drawImage(sub, 0, 0, null);
        } finally {
            g2.dispose();
        }
        cache = copy;
        setPreferredSize(new Dimension(w, h));
        revalidate();
        repaint();
    }

    public int getPixelRGB(int x, int y) {
        ensureCache();
        if (x < 0 || y < 0 || x >= cache.getWidth() || y >= cache.getHeight()) {
            throw new IllegalArgumentException("Coordinates out of bounds: " + x + "," + y);
        }
        return cache.getRGB(x, y);
    }

    private void ensureCache() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight() + 100);
        if (cache == null) {
            cache = (BufferedImage) createImage(w, h);
            var gc = cache.createGraphics();
            try {
                gc.setColor(Color.WHITE);
                gc.fillRect(0, 0, w, h);
            } finally {
                gc.dispose();
            }
        } else if (cache.getWidth() < w || cache.getHeight() < h) {
            var grown = (BufferedImage) createImage(w, h);
            var gg = grown.createGraphics();
            try {
                gg.setColor(Color.WHITE);
                gg.fillRect(0, 0, w, h);
                gg.drawImage(cache, 0, 0, null);
            } finally {
                gg.dispose();
            }
            cache = grown;
        }
    }

    private void startTextEditorAt(int x, int y) {
        if (textEditor == null) {
            textEditor = new JTextField();
            textEditor.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
            textEditor.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        commitEditorIfAny(true);
                        e.consume();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        commitEditorIfAny(false);
                        e.consume();
                    }
                }
            });
            textEditor.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    commitEditorIfAny(true);
                }
            });
            add(textEditor);
        }
        // Size based on current font
        updateEditorFontFromState();
        updateEditorColorFromState();
        FontMetrics fm = getFontMetrics(textEditor.getFont());
        int h = fm.getHeight() + 6;
        textEditor.setBounds(x, y, Math.max(120, 10 * fm.charWidth('M')), h);
        textEditor.setText("");
        textEditor.requestFocusInWindow();
        textEditor.setCaretPosition(0);
        repaint();
    }

    private void commitEditorIfAny(boolean commit) {
        if (textEditor == null) return;
        String value = textEditor.getText();
        Rectangle r = textEditor.getBounds();
        remove(textEditor);
        repaint(r);
        if (commit && value != null && !value.isEmpty()) {
            // Snapshot before committing text onto canvas
            pushUndoSnapshot();
            ensureCache();
            var g2 = cache.createGraphics();
            try {
                applyRenderHints(g2);
                // Font
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                String[] fonts = ge.getAvailableFontFamilyNames();
                int idx = SideMenu.getSelectedFont();
                String family = (idx >= 0 && idx < fonts.length) ? fonts[idx] : g2.getFont().getFamily();
                int size = SideMenu.getFontSize();
                g2.setFont(new Font(family, Font.PLAIN, size));
                g2.setColor(SideMenu.getSelectedForeColor());
                FontMetrics fm = g2.getFontMetrics();
                int baselineY = r.y + fm.getAscent();
                g2.drawString(value, r.x, baselineY);
            } finally {
                g2.dispose();
            }
            repaint();
        }
        textEditor = null;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g;

        // Ensure cache exists and matches (or exceeds) current component size
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight() + 100); // historical +100 kept for compatibility
        if (cache == null) {
            cache = (BufferedImage) createImage(w, h);
            var gc = cache.createGraphics();
            try {
                gc.setColor(Color.WHITE);
                gc.fillRect(0, 0, w, h);
            } finally {
                gc.dispose();
            }
        } else if (cache.getWidth() < w || cache.getHeight() < h) {
            // Grow canvas preserving existing content
            var grown = (BufferedImage) createImage(w, h);
            var gg = grown.createGraphics();
            try {
                gg.setColor(Color.WHITE);
                gg.fillRect(0, 0, w, h);
                gg.drawImage(cache, 0, 0, null);
            } finally {
                gg.dispose();
            }
            cache = grown;
        }

        g2.drawImage(cache, 0, 0, null);

        // While placing an image, render it above the cache
        if (placingImage && pendingImage != null) {
            Graphics2D pg = (Graphics2D) g2.create();
            try {
                applyRenderHints(pg);
                pg.drawImage(pendingImage, pendingX, pendingY, null);
                // Draw a dashed border to indicate placement
                float[] dash = {5f, 5f};
                pg.setColor(new Color(0, 0, 0, 180));
                pg.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
                pg.drawRect(pendingX, pendingY, pendingImage.getWidth(), pendingImage.getHeight());
            } finally {
                pg.dispose();
            }
        } else if (selecting && selectionRect != null) {
            // Draw selection marquee
            Graphics2D sg = (Graphics2D) g2.create();
            try {
                applyRenderHints(sg);
                float[] dash = {4f, 4f};
                sg.setColor(new Color(0, 0, 0, 200));
                sg.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
                sg.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);
            } finally {
                sg.dispose();
            }
        } else if (isdragged) {
            // Preview current shape on top of cache
            drawShape(g2);
        }

        // Draw brush cursor overlay last so it's above everything
        drawBrushCursor(g2);
    }

    private void applyRenderHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private Rectangle normalizedRect(int ax, int ay, int bx, int by) {
        int x = Math.min(ax, bx);
        int y = Math.min(ay, by);
        int w = Math.abs(bx - ax);
        int h = Math.abs(by - ay);
        return new Rectangle(x, y, w, h);
    }

    private void drawBrushCursor(Graphics2D g2) {
        if (!cursorVisible || placingImage) return;
        Tool tool = SideMenu.getSelectedTool();
        // Skip tools where brush preview is not meaningful
        if (tool == Tool.TEXT || tool == Tool.BUCKET || tool == Tool.MOVE) return; // text, bucket, move
        int size = Math.max(1, SideMenu.getStrokeSize());
        int r = Math.max(1, size / 2);
        int cx = cursorX;
        int cy = cursorY;

        Graphics2D g = (Graphics2D) g2.create();
        try {
            applyRenderHints(g);
            // soft fill for visibility regardless of background
            g.setColor(new Color(0, 0, 0, 40));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            // high-contrast outline (white then black)
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(255, 255, 255, 200));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
        } finally {
            g.dispose();
        }
    }

    private void drawShape(Graphics2D g2) {
        applyRenderHints(g2);
        g2.setColor(SideMenu.getSelectedForeColor());
        g2.setStroke(new BasicStroke(SideMenu.getStrokeSize()));

        Tool tool = SideMenu.getSelectedTool();
        switch (tool) {
            case PENCIL -> { // Pencil (free draw, commits as we drag)
                g2.drawLine(x1, y1, x2, y2);
                x1 = x2;
                y1 = y2;
            }
            case LINE -> { // Straight line preview/commit
                g2.drawLine(x1, y1, x2, y2);
            }
            case RECT, RECT_FILLED, ROUNDED_RECT, ROUNDED_RECT_FILLED, OVAL,
                 OVAL_FILLED -> { // Rectangle/rounded/oval (+ filled variants)
                int x = Math.min(x1, x2);
                int y = Math.min(y1, y2);
                int w = Math.abs(x2 - x1);
                int h = Math.abs(y2 - y1);

                if (tool == Tool.RECT) g2.drawRect(x, y, w, h);
                if (tool == Tool.RECT_FILLED) g2.fillRect(x, y, w, h);
                if (tool == Tool.ROUNDED_RECT) g2.drawRoundRect(x, y, w, h, ROUNDED_ARC, ROUNDED_ARC);
                if (tool == Tool.ROUNDED_RECT_FILLED) g2.fillRoundRect(x, y, w, h, ROUNDED_ARC, ROUNDED_ARC);
                if (tool == Tool.OVAL) g2.drawOval(x, y, w, h);
                if (tool == Tool.OVAL_FILLED) g2.fillOval(x, y, w, h);
            }
            case ERASER -> { // Eraser draws in white and moves like pencil
                // Do not mutate global color; just render with white locally
                g2.setColor(Color.WHITE);
                g2.drawLine(x1, y1, x2, y2);
                x1 = x2;
                y1 = y2;
            }
            case TEXT -> { // Text
                // Inline editor handles rendering on commit; no preview drawing here
            }
            case BUCKET -> { // Bucket fill
                cache = new ScanlineFloodFill().fill(cache, x1, y1, SideMenu.getSelectedForeColor());
            }
            default -> {
                // no-op
            }
        }
    }

    // MouseMotionListener
    @Override
    public void mouseDragged(MouseEvent ev) {
        cursorX = ev.getX();
        cursorY = ev.getY();
        cursorVisible = true;
        if (placingImage && pendingImage != null) {
            // Drag moves the pending image
            pendingX = ev.getX() - pendingDragOffsetX;
            pendingY = ev.getY() - pendingDragOffsetY;
            repaint();
            return;
        }
        Tool toolNow = SideMenu.getSelectedTool();
        if (toolNow == Tool.MOVE && selecting) {
            selEndX = ev.getX();
            selEndY = ev.getY();
            selectionRect = normalizedRect(selStartX, selStartY, selEndX, selEndY);
            repaint();
            return;
        }
        isdragged = true;
        x2 = ev.getX();
        y2 = ev.getY();

        Tool tool = SideMenu.getSelectedTool();
        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
            // Ensure backing cache exists before drawing
            ensureCache();
            // Commit continuous tools directly to cache for smooth drawing
            var cg = cache.createGraphics();
            try {
                drawShape(cg);
            } finally {
                cg.dispose();
            }
        }
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        cursorX = e.getX();
        cursorY = e.getY();
        cursorVisible = true;
        repaint();
    }

    // MouseListener
    @Override
    public void mouseClicked(MouseEvent e) {
        if (placingImage && e.getClickCount() >= 2) {
            commitPlacement();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        cursorVisible = true;
        cursorX = e.getX();
        cursorY = e.getY();
        updateCursorForCurrentTool();
        repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
        cursorVisible = false;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    @Override
    public void mousePressed(MouseEvent ev) {
        x1 = ev.getX();
        y1 = ev.getY();
        if (placingImage && pendingImage != null) {
            // Begin dragging the pending image if clicked inside it
            if (pointInPending(x1, y1)) {
                pendingDragOffsetX = x1 - pendingX;
                pendingDragOffsetY = y1 - pendingY;
            } else {
                // If clicked outside, re-anchor so that the click point grabs the top-left
                pendingDragOffsetX = Math.min(Math.max(0, pendingImage.getWidth() / 2), pendingImage.getWidth());
                pendingDragOffsetY = Math.min(Math.max(0, pendingImage.getHeight() / 2), pendingImage.getHeight());
            }
            ispressed = true;
            isdragged = false;
            return;
        }
        Tool tool = SideMenu.getSelectedTool();
        if (tool == Tool.MOVE) {
            // Start rectangular selection
            selecting = true;
            selStartX = x1;
            selStartY = y1;
            selEndX = x1;
            selEndY = y1;
            selectionRect = normalizedRect(selStartX, selStartY, selEndX, selEndY);
            ispressed = true;
            isdragged = false;
            repaint();
            return;
        }
        if (tool == Tool.TEXT) {
            startTextEditorAt(x1, y1);
            ispressed = false;
            isdragged = false;
            return;
        }
        // For continuous tools, capture snapshot at the beginning of the stroke
        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
            pushUndoSnapshot();
        }
        ispressed = true;
    }

    @Override
    public void mouseReleased(MouseEvent ev) {
        isdragged = false;
        ispressed = false;
        x2 = ev.getX();
        y2 = ev.getY();

        if (placingImage && pendingImage != null) {
            // Stop dragging; do not auto-commit
            repaint();
            return;
        }

        Tool toolNow = SideMenu.getSelectedTool();
        if (toolNow == Tool.MOVE) {
            if (selecting) {
                selEndX = x2;
                selEndY = y2;
                selectionRect = normalizedRect(selStartX, selStartY, selEndX, selEndY);
                selecting = false;
                if (selectionRect.width <= 0 || selectionRect.height <= 0) {
                    selectionRect = null;
                    repaint();
                    return;
                }
                // Cut selection into pending image
                // History snapshot before cutting selection from cache
                pushUndoSnapshot();
                ensureCache();
                int rx = Math.max(0, Math.min(selectionRect.x, cache.getWidth() - 1));
                int ry = Math.max(0, Math.min(selectionRect.y, cache.getHeight() - 1));
                int rw = Math.max(1, Math.min(selectionRect.width, cache.getWidth() - rx));
                int rh = Math.max(1, Math.min(selectionRect.height, cache.getHeight() - ry));
                BufferedImage sub = cache.getSubimage(rx, ry, rw, rh);
                // backup the cut region for cancel
                selectionCutBackup = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D bg = selectionCutBackup.createGraphics();
                try {
                    bg.drawImage(sub, 0, 0, null);
                } finally {
                    bg.dispose();
                }
                selectionCutRect = new Rectangle(rx, ry, rw, rh);
                // copy to pending image for placement
                pendingImage = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D pg = pendingImage.createGraphics();
                try {
                    pg.drawImage(sub, 0, 0, null);
                } finally {
                    pg.dispose();
                }
                // clear original area (cut)
                Graphics2D cg2 = cache.createGraphics();
                try {
                    cg2.setColor(Color.WHITE);
                    cg2.fillRect(rx, ry, rw, rh);
                } finally {
                    cg2.dispose();
                }
                pendingX = rx;
                pendingY = ry;
                placingImage = true;
                selectionPlacement = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                selectionRect = null;
                repaint();
                return;
            } else {
                // Nothing selected and not placing; nothing to commit
                repaint();
                return;
            }
        }

        if (toolNow == Tool.TEXT) {
            // Inline text editor handles its own commit; nothing to draw here
            return;
        }

        // Commit the final shape onto the backing image
        // Snapshot before finalizing non-continuous shape or bucket
        if (toolNow != Tool.PENCIL && toolNow != Tool.ERASER) {
            pushUndoSnapshot();
        }
        ensureCache();
        var cg = cache.createGraphics();
        try {
            drawShape(cg);
        } finally {
            cg.dispose();
        }
        repaint();
    }

    // Utility API for future uses (e.g., File > New)
    public void clearCanvas() {
        if (cache == null) return;
        // Snapshot before clearing the canvas
        pushUndoSnapshot();
        var g = cache.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, cache.getWidth(), cache.getHeight());
        } finally {
            g.dispose();
        }
        repaint();
    }
}
