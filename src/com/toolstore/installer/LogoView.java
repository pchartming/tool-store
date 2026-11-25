package com.toolstore.installer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Brand tile used by the market shell and app cards. */
public final class LogoView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private String letter = "T";
    private int color = 0xFF2F7DFF;
    private boolean shell;

    public LogoView(Context context) {
        super(context);
        init();
    }

    public LogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LogoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1f));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
    }

    public void setLetter(String value) {
        letter = value == null || value.isEmpty() ? "T" : value;
        invalidate();
    }

    public void setBrandColor(int value) {
        color = value;
        shell = false;
        invalidate();
    }

    public void setShellLogo() {
        shell = true;
        color = 0xFF2F7DFF;
        letter = "T";
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fallback = (int) dp(46f);
        int width = resolveSize(fallback, widthMeasureSpec);
        int height = resolveSize(fallback, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = Math.min(w, h) * 0.30f;
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);

        if (shell) {
            fill.setShader(new LinearGradient(0f, 0f, w, h,
                    new int[]{0xFF5A9BFF, 0xFF2F7DFF, 0xFF2469E8},
                    null, Shader.TileMode.CLAMP));
            fill.setColor(Color.WHITE);
            stroke.setColor(0x55246BE8);
            textPaint.setColor(Color.WHITE);
        } else {
            fill.setShader(new LinearGradient(0f, 0f, w, h,
                    tint(color, 0.10f), tint(color, 0.19f), Shader.TileMode.CLAMP));
            fill.setColor(color);
            stroke.setColor(withAlpha(color, 0.22f));
            textPaint.setColor(color);
        }
        canvas.drawRoundRect(rect, radius, radius, fill);
        if (!shell) {
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }

        textPaint.setTextSize(h * (letter.length() > 1 ? 0.30f : 0.42f));
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(letter, w / 2f, baseline, textPaint);
    }

    private static int tint(int color, float fraction) {
        int r = Math.round(Color.red(color) + (255 - Color.red(color)) * (1f - fraction));
        int g = Math.round(Color.green(color) + (255 - Color.green(color)) * (1f - fraction));
        int b = Math.round(Color.blue(color) + (255 - Color.blue(color)) * (1f - fraction));
        return Color.rgb(clamp(r), clamp(g), clamp(b));
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}