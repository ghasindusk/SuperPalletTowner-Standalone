package starlight.trainer;

/**
 * Pixel layout of the resonance screen that does not depend on the font: the panel, its texture
 * panes, the link page rows, the HUD settings rows, the info box and the key-hint line. Pure Java,
 * so overlap and clipping are unit tested for small (320x240 GUI) and large windows.
 */
public final class AffinityLayout {
    /** Pane interiors in the 512x256 texture, from art/generate_affinity_device.py. */
    public static final int[] HEADER = {66, 18, 380, 22};
    public static final int[] LEFT = {24, 68, 250, 166};
    public static final int[] RIGHT_TOP = {302, 68, 186, 76};
    public static final int[] RIGHT_BOTTOM = {302, 170, 186, 64};

    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public boolean intersects(Rect other) {
            return other != null && x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean inside(Rect outer) {
            return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
        }
    }

    /** The largest 2:1 panel that fits with a small margin, never wider than the 512 px texture. */
    public static Rect panel(int screenWidth, int screenHeight) {
        int width = Math.max(2, Math.min(512, Math.min(screenWidth - 8, (screenHeight - 8) * 2))) & ~1;
        return new Rect((screenWidth - width) / 2, (screenHeight - width / 2) / 2, width, width / 2);
    }

    /** A texture pane mapped onto the panel (inner edges rounded inwards). */
    public static Rect pane(Rect panel, int[] texture) {
        double scale = panel.w() / 512.0;
        int x0 = panel.x() + (int) Math.ceil(texture[0] * scale);
        int y0 = panel.y() + (int) Math.ceil(texture[1] * scale);
        int x1 = panel.x() + (int) Math.floor((texture[0] + texture[2]) * scale);
        int y1 = panel.y() + (int) Math.floor((texture[1] + texture[3]) * scale);
        return new Rect(x0, y0, x1 - x0, y1 - y0);
    }

    /**
     * Link page, top to bottom: one status line, the chips of the resonance-slot types (manual
     * link), a caption when there is room, then the suggestion list.
     */
    public record LinkPage(int statusY, Rect chips, int captionY, Rect list, int listRows) {}

    public static LinkPage linkPage(Rect page, int lineHeight, int chipRow, int suggestionRow, int scrollbar) {
        int y = page.y() + 2;
        int statusY = y;
        y += lineHeight + 2;
        Rect chips = new Rect(page.x() + 1, y, page.w() - scrollbar - 3, chipRow);
        y += chipRow + 3;
        boolean caption = page.bottom() - y >= lineHeight + 1 + 3 * suggestionRow;
        int captionY = caption ? y : -1;
        if (caption) y += lineHeight + 1;
        int listHeight = Math.max(0, page.bottom() - y);
        Rect list = new Rect(page.x(), y, page.w(), listHeight);
        return new LinkPage(statusY, chips, captionY, list, Math.max(1, listHeight / Math.max(1, suggestionRow)));
    }

    /** Rows of the HUD settings page, top to bottom, all inside the page (rows that do not fit are dropped). */
    public static Rect[] stack(Rect page, int rowHeight, int gap, int count) {
        int fit = Math.max(1, Math.min(count, (page.h() - 2 + gap) / (rowHeight + gap)));
        Rect[] rows = new Rect[fit];
        int y = page.y() + 2;
        for (int i = 0; i < fit; i++) {
            rows[i] = new Rect(page.x() + 2, y, page.w() - 4, rowHeight);
            y += rowHeight + gap;
        }
        return rows;
    }

    /** Info box: the text area above the two buttons and the buttons themselves. */
    public record InfoBox(Rect text, Rect leftButton, Rect rightButton, int lines) {}

    public static InfoBox infoBox(Rect box, int lineHeight) {
        int buttonHeight = Math.min(16, Math.max(12, box.h() / 3));
        int buttonWidth = (box.w() - 8) / 2;
        int buttonY = box.bottom() - buttonHeight - 2;
        Rect text = new Rect(box.x() + 3, box.y() + 2, box.w() - 6, Math.max(0, buttonY - 1 - box.y() - 2));
        return new InfoBox(text,
                new Rect(box.x() + 2, buttonY, buttonWidth, buttonHeight),
                new Rect(box.right() - 2 - buttonWidth, buttonY, buttonWidth, buttonHeight),
                Math.max(1, text.h() / lineHeight));
    }

    /** The key-hint line under the panel, or {@code null} when the window has no room for it. */
    public static Rect hintLine(int screenWidth, int screenHeight, Rect panel, int lineHeight) {
        int y = panel.bottom() + 2;
        if (y + lineHeight > screenHeight) return null;
        return new Rect(4, y, Math.max(0, screenWidth - 8), lineHeight);
    }
}
