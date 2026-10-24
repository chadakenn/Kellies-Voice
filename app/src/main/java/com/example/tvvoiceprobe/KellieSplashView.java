package com.example.tvvoiceprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Warm copper branding for intentional full-screen Kellie's Voice launches. */
public final class KellieSplashView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap portrait;
    private final boolean aurora;
    private final AppPalette colors;
    public KellieSplashView(Context context) {
        super(context);
        String skin = new OverlayPreferences(context).skin();
        colors = AppPalette.forSkin(skin);
        aurora = OverlayPreferences.SKIN_AURORA.equals(skin);
        portrait = BitmapFactory.decodeResource(getResources(), R.drawable.kellie_portrait);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.scale(getWidth() / 1000f, getHeight() / 560f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 1000, 560,
                aurora ? 0xff070C1A : 0xff110b09,
                aurora ? 0xff17284A : 0xff342018, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 1000, 560, paint);
        paint.setShader(new RadialGradient(500, 280, 390,
                aurora ? new int[] {0x995071BD, 0x443F3982, 0x00000000}
                       : new int[] {0x995c3020, 0x44301c13, 0x00000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 1000, 560, paint);
        paint.setShader(null);
        // One small, personal portrait on the welcome splash only.
        // Keep the always-on floating overlay icon-only and uncluttered.
        if (portrait != null) {
            canvas.save();
            Path clipping = new Path();
            clipping.addCircle(210, 284, 100, Path.Direction.CW);
            canvas.clipPath(clipping);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(240);
            canvas.drawBitmap(portrait, null, new RectF(110, 184, 310, 384), paint);
            paint.setAlpha(255);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(aurora ? colors.accent : 0xffDDA37E);
            paint.setStrokeWidth(4f);
            canvas.drawCircle(210, 284, 102, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(aurora ? colors.stroke : 0xffca8b69);
        canvas.drawRoundRect(new RectF(65, 48, 935, 515), 35, 35, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(aurora ? colors.accent : 0xffE8AE88);
        canvas.drawRoundRect(new RectF(492, 111, 508, 190), 8, 8, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawArc(new RectF(471, 142, 529, 215), 0, 180, false, paint);
        canvas.drawLine(500, 215, 500, 230, paint);
        canvas.drawLine(480, 230, 520, 230, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(aurora ? colors.text : 0xffF4D7C0);
        paint.setTextSize(76);
        canvas.drawText("Kellie's", 500, 335, paint);
        paint.setTextSize(40);
        paint.setColor(aurora ? colors.accent : 0xffE9AE87);
        canvas.drawText("V O I C E", 500, 392, paint);
        paint.setTextSize(23);
        paint.setColor(aurora ? colors.muted : 0xffE9D0BE);
        canvas.drawText("Your home. Your voice. Your way.", 500, 452, paint);
        paint.setStyle(Paint.Style.STROKE);
        Path wave = new Path();
        wave.moveTo(155, 478);
        wave.cubicTo(250, 472, 314, 467, 369, 478);
        wave.cubicTo(436, 498, 455, 436, 500, 477);
        wave.cubicTo(545, 518, 567, 462, 641, 478);
        wave.cubicTo(705, 486, 775, 471, 845, 478);
        paint.setColor(aurora ? colors.focus : 0xffDE9772);
        paint.setStrokeWidth(2);
        canvas.drawPath(wave, paint);
        canvas.restoreToCount(save);
    }
}