package com.toolstore.installer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView with a rounded clip and an optional hairline border. Used for the
 * official app icons and screenshots; no external image library is required.
 */
public final class RoundedImageView extends ImageView {

    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final RectF borderBounds = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;
    private boolean border = true;

    public RoundedImageView(Context context) {
        super(context);
        init();
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(0x1816212F);
        borderPaint.setStrokeWidth(Math.max(1f, 0.8f * getResources().getDisplayMetrics().density));
    }

    public void setCornerRadius(float radiusPx) {
        radius = Math.max(0f, radiusPx);
        invalidate();
    }

    public void setCornerRadiusDp(float radiusDp) {
        setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
    }

    public void setShowBorder(boolean show) {
        border = show;
        invalidate();
    }

    public void setBorderColor(int color) {
        borderPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0f) {
            super.onDraw(canvas);
            return;
        }
        int save = canvas.save();
        bounds.set(0f, 0f, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        super.onDraw(canvas);
        canvas.restoreToCount(save);

        if (border && getWidth() > 0 && getHeight() > 0) {
            float inset = borderPaint.getStrokeWidth() / 2f;
            borderBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(borderBounds, radius, radius, borderPaint);
        }
    }
}