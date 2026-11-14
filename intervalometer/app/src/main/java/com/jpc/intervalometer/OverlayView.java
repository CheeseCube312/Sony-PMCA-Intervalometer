package com.jpc.intervalometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent full-screen text overlay drawn on top of the HAL preview
 * SurfaceView (a few info lines, top-left). Never covers the live view.
 */
public class OverlayView extends View {
    private static final int X = 12, Y0 = 34, LINE_H = 32;

    private volatile String[] lines = new String[0];
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setColor(0xffffffff);
        textPaint.setTextSize(26);
        textPaint.setShadowLayer(3, 1, 1, 0xff000000);
    }

    /** Replaces all overlay lines (drawn top-left, one per row). */
    public void setLines(String[] newLines) {
        lines = newLines == null ? new String[0] : newLines;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < lines.length; i++)
            canvas.drawText(lines[i], X, Y0 + i * LINE_H, textPaint);
    }
}