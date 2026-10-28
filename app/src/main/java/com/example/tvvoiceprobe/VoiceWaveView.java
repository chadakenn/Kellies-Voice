package com.example.tvvoiceprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Tiny, original procedural TV waveform. Animation reflects assistant state,
 * not recorded sound amplitude; no audio is sampled here.
 */
public final class VoiceWaveView extends View {
    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int LISTENING = 2;
    public static final int THINKING = 3;
    public static final int SPEAKING = 4;

    public static final int LOOK_COPPER = 0;
    public static final int LOOK_SIGNAL = 1;
    public static final int LOOK_AURORA = 2;
    public static final int LOOK_OBSIDIAN = 3;

    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int mode = IDLE;
    private int look = LOOK_COPPER;
    private boolean running;
    private long frame = 0L;
    private final Runnable redraw = new Runnable() {
        @Override public void run() {
            if (!running) return;
            frame++;
            invalidate();
            handler.postDelayed(this, 42);
        }
    };

    public VoiceWaveView(Context context) {
        super(context);
        setFocusable(false);
        baseline.setColor(0x44DDA984);
        baseline.setStrokeWidth(dp(1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void setMode(int next) {
        mode = next;
        invalidate();
    }

    public void setLook(int next) {
        look = next == LOOK_SIGNAL || next == LOOK_AURORA || next == LOOK_OBSIDIAN
                ? next : LOOK_COPPER;
        baseline.setColor(look == LOOK_OBSIDIAN ? 0x448B5CFF
                : look == LOOK_AURORA ? 0x337DAAFF
                : look == LOOK_SIGNAL ? 0x227B5843 : 0x44DDA984);
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        handler.post(redraw);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(redraw);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Respect the requested 64/68/88 dp height. A fixed 88 dp ignored
        // EXACTLY constraints and made the compact Night Signal card taller
        // than the overlay layout intended.
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = heightMode == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : dp(88);
        if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth();
        final float height = getHeight();
        if (width < 2 || height < 2) return;

        float middle = height * 0.52f;
        if (look == LOOK_SIGNAL) {
            drawSignal(canvas, width, height, middle);
            return;
        }
        canvas.drawLine(0, middle, width, middle, baseline);
        float phase = frame * 0.105f;
        float intensity = mode == LISTENING ? 1.0f
                : mode == SPEAKING ? 0.9f
                : mode == THINKING ? 0.60f
                : mode == CONNECTING ? 0.30f : 0.21f;
        int[] colors = look == LOOK_AURORA
                ? new int[] {0xffB9DFFF, 0xff6D9EFF, 0xffD4C3FF, 0xff9D73FF}
                : look == LOOK_OBSIDIAN
                ? new int[] {0xff00E5FF, 0xff8B5CFF, 0xff7FF6FF, 0xff4F37A8}
                : new int[] {0xffffc39f, 0xffd28a64, 0xfff5d6b7, 0xff9d563e};
        for (int layer = 0; layer < colors.length; layer++) {
            Path path = new Path();
            float amplitude = (height * 0.33f) * intensity
                    * (0.66f + 0.18f * layer);
            float frequency = 2.4f + layer * 0.65f;
            for (int i = 0; i <= 96; i++) {
                float x = width * i / 96f;
                float t = i / 96f;
                float envelope = (float) Math.pow(Math.sin(Math.PI * t), 1.7);
                float wave = (float) (Math.sin(t * Math.PI * 2 * frequency
                        + phase * (0.9 + 0.11 * layer) + layer * 1.2)
                        + 0.27 * Math.sin(t * Math.PI * 2 * 1.3 - phase * 0.7));
                float y = middle + wave * amplitude * envelope;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            brush.setStyle(Paint.Style.STROKE);
            brush.setStrokeCap(Paint.Cap.ROUND);
            brush.setStrokeJoin(Paint.Join.ROUND);
            brush.setStrokeWidth(dp(layer == 0 ? 2 : 1));
            brush.setColor(colors[layer]);
            brush.setAlpha(layer == 0 ? 225 : 148);
            canvas.drawPath(path, brush);
        }
    }

    private void drawSignal(Canvas canvas, float width, float height, float middle) {
        int bars = 17;
        float gap = dp(5);
        float barWidth = Math.max(dp(3), (width - gap * (bars + 1)) / bars);
        float phase = frame * 0.22f;
        float intensity = mode == LISTENING ? 1.0f
                : mode == SPEAKING ? 0.85f
                : mode == THINKING ? 0.55f
                : mode == CONNECTING ? 0.28f : 0.16f;
        brush.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bars; i++) {
            float across = (i + 0.5f) / bars;
            float envelope = (float) Math.sin(Math.PI * across);
            float wobble = mode == IDLE
                    ? 0.22f
                    : 0.35f + 0.65f * (float) Math.abs(Math.sin(phase + i * 0.62));
            float barHeight = Math.max(dp(3), height * 0.78f * intensity * envelope * wobble);
            float left = gap + i * (barWidth + gap);
            float top = middle - barHeight / 2f;
            brush.setColor(i % 6 == 3 ? 0xffFADEB9 : 0xffD59E69);
            canvas.drawRoundRect(left, top, left + barWidth, top + barHeight,
                    dp(2), dp(2), brush);
        }
    }
}
