package com.jpc.intervalometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent full-screen text overlay drawn on top of the HAL preview
 * SurfaceView (a few info lines, top-left). Never covers the live view.
 *
 * Two paint paths: setLines(String[]) paints plain white rows (RUNNING HUD,
 * summary, wake HUD); setRows(Row[]) paints the styled menus (0.4.0) - fixed
 * columns, flowing runs (COL_FLOW), rules, and the amber selection chip.
 * Rows flow from the top unless pinned (Row.pin) to the screen's bottom
 * edge (the config menu's hint + key legend).
 */
public class OverlayView extends View {
    private static final int X = 12, Y0 = 34, LINE_H = 32;

    /** Text styles: white (default), dimmed (secondary), amber chip (selection/titles). */
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_DIM = 1;
    public static final int STYLE_ACCENT = 2;

    /**
     * Fixed columns: left margin, then width fractions (the font is not
     * monospace - no space padding). COL_FLOW places a part just after the
     * previous one in the row (the tab bar's runs).
     */
    public static final int COL_A = 0, COL_B = 1, COL_C = 2, COL_FLOW = -1;

    /** One styled text run at a fixed column. */
    public static final class Part {
        public final String text;
        public final int col;
        public final int style;

        public Part(String text, int col, int style) {
            this.text = text;
            this.col = col;
            this.style = style;
        }
    }

    /**
     * One overlay row: styled parts at fixed columns, optional rule under
     * it. pin 0 (default) flows from the top in array order; pin n >= 1
     * anchors the row counting up from the screen's bottom edge (1 = the
     * bottom row, 2 = the one above it) - used for the config menu's
     * hint + key legend, so they stay put under every item walk.
     */
    public static final class Row {
        public final Part[] parts;
        public final boolean rule;
        public final int pin;

        public Row(Part[] parts, boolean rule) {
            this(parts, rule, 0);
        }

        public Row(Part[] parts, boolean rule, int pin) {
            this.parts = parts;
            this.rule = rule;
            this.pin = pin;
        }

        public Row(Part[] parts) {
            this(parts, false);
        }

        /** Convenience: one plain white part at the left margin (legacy shape). */
        public Row(String text) {
            this(new Part[]{new Part(text, COL_A, STYLE_NORMAL)}, false);
        }
    }

    private volatile Row[] rows = new Row[0];
    private final Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setupText(normalPaint, 0xffffffff);
        setupText(dimPaint, 0xffaaaaaa);
        setupText(chipTextPaint, 0xff000000); // black on the chip - no shadow inside the amber bar
        chipFillPaint.setColor(0xfff5b800);   // Sony menu amber
        rulePaint.setColor(0xff888888);
    }

    private static void setupText(Paint p, int color) {
        p.setColor(color);
        p.setTextSize(26);
        p.setShadowLayer(3, 1, 1, 0xff000000);
    }

    /** Replaces all overlay lines (drawn top-left, one per row) - plain white rows. */
    public void setLines(String[] newLines) {
        if (newLines == null)
            newLines = new String[0];
        Row[] r = new Row[newLines.length];
        for (int i = 0; i < newLines.length; i++)
            r[i] = new Row(newLines[i]);
        setRows(r);
    }

    /** Replaces the overlay with fully styled rows (config grid, help cards). */
    public void setRows(Row[] newRows) {
        rows = newRows == null ? new Row[0] : newRows;
        postInvalidate();
    }

    private static float colX(int col, int w) {
        if (col == COL_B)
            return X + (w - 2 * X) * 55 / 100;
        if (col == COL_C)
            return X + (w - 2 * X) * 80 / 100;
        return X;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        Paint.FontMetrics fm = normalPaint.getFontMetrics();
        int flow = 0; // sequential index of the top-flowing rows only
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            float y = row.pin > 0
                    ? h - Y0 - (row.pin - 1) * LINE_H // pinned: counted up from the bottom edge
                    : Y0 + flow++ * LINE_H;
            boolean hasParts = row.parts != null && row.parts.length > 0;
            float maxEnd = X;
            for (int j = 0; hasParts && j < row.parts.length; j++) {
                Part part = row.parts[j];
                float x = part.col == COL_FLOW ? maxEnd + 14 : colX(part.col, w);
                if (part.style == STYLE_ACCENT) {
                    canvas.drawRect(x - 7, y + fm.ascent - 3,
                            x + chipTextPaint.measureText(part.text) + 7, y + fm.descent + 5,
                            chipFillPaint);
                    canvas.drawText(part.text, x, y, chipTextPaint);
                    maxEnd = Math.max(maxEnd, x + chipTextPaint.measureText(part.text));
                } else {
                    Paint paint = part.style == STYLE_DIM ? dimPaint : normalPaint;
                    canvas.drawText(part.text, x, y, paint);
                    maxEnd = Math.max(maxEnd, x + paint.measureText(part.text));
                }
            }
            if (row.rule) // a bare-rule row spans the grid; a labelled one runs on from its text
                canvas.drawRect(hasParts ? maxEnd + 12 : X, y + fm.descent + 6, w - X,
                        y + fm.descent + 8, rulePaint);
        }
    }
}