package com.example.tvvoiceprobe;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Neon Obsidian card: near-black glass with soft cyan, violet and magenta light
 * and a thin cyan-to-magenta gradient edge. Static, never blurs or captures TV content.
 */
public final class ObsidianGlassDrawable extends Drawable {
    private static final int BASE = 0xF206070C;
    private static final int BASE_CLEAR = 0x0006070C;
    private static final int CYAN = 0x6600E5FF;
    private static final int VIOLET = 0x558B5CFF;
    private static final int MAGENTA = 0x40FF2E88;
    private static final int EDGE_CYAN = 0xB300E5FF;
    private static final int EDGE_VIOLET = 0xB38B5CFF;
    private static final int EDGE_MAGENTA = 0xB3FF2E88;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float radius;
    private int opacity = 255;
    private ColorFilter filter;

    public ObsidianGlassDrawable(float radius) { this.radius = radius; }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        RectF area = new RectF(bounds);
        clip.reset();
        clip.addRoundRect(area, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clip);
        paint.setColorFilter(filter);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BASE);
        paint.setAlpha(Math.round(242 * opacity / 255f));
        canvas.drawRect(area, paint);

        float[] x = {0.04f, 0.50f, 0.96f};
        int[] colors = {CYAN, VIOLET, MAGENTA};
        for (int i = 0; i < x.length; i++) {
            int light = canvas.save();
            canvas.translate(bounds.left + area.width() * x[i], bounds.top + area.height() * 0.5f);
            canvas.scale(0.34f, 1f);
            paint.setShader(new RadialGradient(0, 0, Math.max(1, area.height()),
                    colors[i], BASE_CLEAR, Shader.TileMode.CLAMP));
            paint.setAlpha(opacity);
            canvas.drawRect(-area.height(), -area.height(), area.height(), area.height(), paint);
            canvas.restoreToCount(light);
        }
        canvas.restoreToCount(saved);

        paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[] {EDGE_CYAN, EDGE_VIOLET, EDGE_MAGENTA}, null, Shader.TileMode.CLAMP));
        paint.setAlpha(Math.round(190 * opacity / 255f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        area.inset(1, 1);
        canvas.drawRoundRect(area, radius, radius, paint);
        paint.setShader(null);
    }

    @Override public void setAlpha(int alpha) { opacity = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
