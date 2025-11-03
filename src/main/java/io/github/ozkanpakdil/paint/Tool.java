package io.github.ozkanpakdil.paint;

/**
 * Drawing tools used by the application.
 * The order/index is preserved for compatibility with toolbar button indices.
 */
public enum Tool {
    PENCIL(0),
    LINE(1),
    RECT(2),
    OVAL(3),
    ROUNDED_RECT(4),
    ERASER(5),
    TEXT(6),
    RECT_FILLED(7),
    OVAL_FILLED(8),
    ROUNDED_RECT_FILLED(9),
    BUCKET(10),
    MOVE(11);

    private final int index;

    Tool(int index) { this.index = index; }

    public int toIndex() { return index; }

    public static Tool fromIndex(int idx) {
        for (Tool t : values()) {
            if (t.index == idx) return t;
        }
        throw new IllegalArgumentException("Unknown tool index: " + idx);
    }
}
