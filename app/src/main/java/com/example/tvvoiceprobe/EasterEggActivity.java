package com.example.tvvoiceprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.Random;

/**
 * Deliberately local-only, full-screen TV Easter egg. No microphone, HA calls,
 * service binding, permission prompts, sounds or persistent app settings.
 */
public final class EasterEggActivity extends Activity {
    private PartyView party;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        party = new PartyView();
        setContentView(party);
        party.setFocusable(true);
        party.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (party != null) party.resume();
    }

    @Override protected void onPause() {
        if (party != null) party.pause();
        super.onPause();
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (!event.isCanceled() && party != null) party.nextMode();
            return true;
        }
        // BACK, including the Android TV system Back dispatcher, exits
        // normally and leaves the underlying Settings navigation intact.
        return super.onKeyUp(code, event);
    }

    private final class PartyView extends View {
        private static final int CAT_COUNT = 15;
        private static final int CONFETTI_COUNT = 170;
        private final Random random = new Random(48151623L);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path ears = new Path();
        private final Cat[] cats = new Cat[CAT_COUNT];
        private final Confetti[] confetti = new Confetti[CONFETTI_COUNT];
        private final int[] fur = {
                0xFFFFC278, 0xFFBCA3FF, 0xFFFF9CBD, 0xFF9CE5D2,
                0xFFF7E88B, 0xFFD2D8F9
        };
        private final int[] partyColors = {
                0xFFFFC44F, 0xFFFF6DAE, 0xFF7BE7D1, 0xFF9D91FF,
                0xFF8FC9FF, 0xFFFF8A70
        };
        private int mode = 0; // 0: bouncing cats, 1: serious confetti emergency
        private boolean animating;
        private long lastFrame;

        PartyView() { super(EasterEggActivity.this); }

        void resume() {
            animating = true;
            lastFrame = 0L;
            postInvalidateOnAnimation();
        }

        void pause() {
            animating = false;
            lastFrame = 0L;
        }

        void nextMode() {
            mode = (mode + 1) % 2;
            invalidate();
        }

        @Override protected void onDetachedFromWindow() {
            pause();
            super.onDetachedFromWindow();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (width <= 0 || height <= 0) return;
            float unit = Math.min(width, height);
            float minRadius = Math.max(22f, unit * 0.039f);
            for (int i = 0; i < CAT_COUNT; i++) {
                Cat cat = new Cat();
                cat.r = minRadius * (0.80f + random.nextFloat() * 0.58f);
                cat.x = cat.r + random.nextFloat() * Math.max(1f, width - cat.r * 2f);
                cat.y = cat.r * 1.6f
                        + random.nextFloat() * Math.max(1f, height - cat.r * 3.2f);
                cat.dx = (45f + random.nextFloat() * 86f)
                        * (random.nextBoolean() ? 1f : -1f);
                cat.dy = (35f + random.nextFloat() * 76f)
                        * (random.nextBoolean() ? 1f : -1f);
                cat.color = fur[i % fur.length];
                cat.angle = random.nextFloat() * 24f - 12f;
                cat.spin = (random.nextFloat() - 0.5f) * 22f;
                cats[i] = cat;
            }
            for (int i = 0; i < CONFETTI_COUNT; i++) {
                Confetti item = new Confetti();
                item.x = random.nextFloat() * width;
                item.y = random.nextFloat() * height;
                item.dx = (random.nextFloat() - 0.5f) * 95f;
                item.dy = 55f + random.nextFloat() * 115f;
                item.size = Math.max(4f, unit * (0.005f + random.nextFloat() * 0.008f));
                item.color = partyColors[i % partyColors.length];
                item.angle = random.nextFloat() * 360f;
                item.spin = (random.nextFloat() - 0.5f) * 140f;
                confetti[i] = item;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth(), height = getHeight();
            if (width == 0 || height == 0) return;
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0L ? 0f
                    : Math.max(0f, Math.min(0.04f, (now - lastFrame) / 1000f));
            lastFrame = now;

            canvas.drawColor(mode == 0 ? 0xFF161C36 : 0xFF231538);
            if (mode == 0) {
                for (Cat cat : cats) {
                    if (cat == null) continue;
                    updateCat(cat, width, height, dt);
                    drawCat(canvas, cat);
                }
            }
            int pieces = mode == 0 ? CONFETTI_COUNT / 2 : CONFETTI_COUNT;
            for (int i = 0; i < pieces; i++) {
                Confetti piece = confetti[i];
                if (piece == null) continue;
                updateConfetti(piece, width, height, dt);
                canvas.save();
                canvas.translate(piece.x, piece.y);
                canvas.rotate(piece.angle);
                paint.setColor(piece.color);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(-piece.size / 2f, -piece.size / 3f,
                        piece.size / 2f, piece.size / 3f, paint);
                canvas.restore();
            }

            float unit = Math.min(width, height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            paint.setShadowLayer(4f, 1f, 2f, Color.BLACK);
            paint.setTextSize(Math.max(25f, unit * 0.054f));
            canvas.drawText(mode == 0 ? "CAT-ASTROPHIC DIAGNOSTICS"
                    : "CONFETTI CONTAINMENT FAILURE", width / 2f, height * 0.12f, paint);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(Math.max(16f, unit * 0.023f));
            canvas.drawText(mode == 0 ? "No actual cats were inconvenienced."
                    : "Please remain irresponsibly festive.", width / 2f, height * 0.18f, paint);
            paint.setTextSize(Math.max(15f, unit * 0.020f));
            canvas.drawText("OK / LEFT / RIGHT: SWITCH PARTY     \u2022     BACK: ESCAPE",
                    width / 2f, height * 0.95f, paint);
            paint.clearShadowLayer();

            if (animating) postInvalidateOnAnimation();
        }

        private void updateCat(Cat c, int width, int height, float dt) {
            c.x += c.dx * dt;
            c.y += c.dy * dt;
            c.angle += c.spin * dt;
            float minX = c.r * 1.08f, maxX = Math.max(minX, width - minX);
            float minY = c.r * 1.26f, maxY = Math.max(minY, height - minY);
            if (c.x < minX) { c.x = minX; c.dx = Math.abs(c.dx); }
            if (c.x > maxX) { c.x = maxX; c.dx = -Math.abs(c.dx); }
            if (c.y < minY) { c.y = minY; c.dy = Math.abs(c.dy); }
            if (c.y > maxY) { c.y = maxY; c.dy = -Math.abs(c.dy); }
            if (c.angle > 18f || c.angle < -18f) c.spin = -c.spin;
        }

        private void updateConfetti(Confetti c, int width, int height, float dt) {
            c.x += c.dx * dt;
            c.y += c.dy * dt;
            c.angle += c.spin * dt;
            if (c.y > height + c.size) {
                c.y = -c.size;
                c.x = random.nextFloat() * width;
            }
            if (c.x > width + c.size) c.x = -c.size;
            else if (c.x < -c.size) c.x = width + c.size;
        }

        private void drawCat(Canvas canvas, Cat c) {
            canvas.save();
            canvas.translate(c.x, c.y);
            canvas.rotate(c.angle);
            float r = c.r;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(c.color);
            ears.reset();
            ears.moveTo(-r * 0.83f, -r * 0.45f);
            ears.lineTo(-r * 0.78f, -r * 1.25f);
            ears.lineTo(-r * 0.18f, -r * 0.89f);
            ears.close();
            ears.moveTo(r * 0.83f, -r * 0.45f);
            ears.lineTo(r * 0.78f, -r * 1.25f);
            ears.lineTo(r * 0.18f, -r * 0.89f);
            ears.close();
            canvas.drawPath(ears, paint);
            canvas.drawCircle(0f, 0f, r, paint);

            paint.setColor(0xFF28243E);
            canvas.drawCircle(-r * .35f, -r * .12f, r * .095f, paint);
            canvas.drawCircle(r * .35f, -r * .12f, r * .095f, paint);
            paint.setColor(0xFFEE6C88);
            canvas.drawCircle(0f, r * .18f, r * .12f, paint);
            paint.setColor(0xFF3B2E47);
            paint.setStrokeWidth(Math.max(1.7f, r * 0.032f));
            canvas.drawLine(0f, r * .29f, 0f, r * .42f, paint);
            canvas.drawLine(0f, r * .42f, -r * .14f, r * .48f, paint);
            canvas.drawLine(0f, r * .42f, r * .14f, r * .48f, paint);
            for (int sign : new int[]{-1, 1}) {
                float sx = sign;
                canvas.drawLine(sx * r * .35f, r * .20f,
                        sx * r * 1.15f, r * .09f, paint);
                canvas.drawLine(sx * r * .36f, r * .30f,
                        sx * r * 1.20f, r * .36f, paint);
            }
            canvas.restore();
        }
    }

    private static final class Cat {
        float x, y, dx, dy, r, angle, spin;
        int color;
    }

    private static final class Confetti {
        float x, y, dx, dy, size, angle, spin;
        int color;
    }
}
