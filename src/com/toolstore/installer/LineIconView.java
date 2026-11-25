package com.toolstore.installer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small framework-drawn line icon set. Keeping these as native Canvas paths
 * avoids shipping a web font or depending on Material icon assets.
 */
public final class LineIconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private String icon = "tv";
    private int color = 0xFF8391A3;

    public LineIconView(Context context) {
        super(context);
        init();
    }

    public LineIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.8f);
        paint.setColor(color);
        setWillNotDraw(false);
    }

    public void setIcon(String value) {
        icon = value == null ? "" : value;
        invalidate();
    }

    public void setIconColor(int value) {
        color = value;
        paint.setColor(value);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fallback = Math.round(20f * getResources().getDisplayMetrics().density);
        int width = resolveSize(fallback, widthMeasureSpec);
        int height = resolveSize(fallback, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        float size = Math.min(getWidth(), getHeight());
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        float scale = size / 24f;
        canvas.scale(scale, scale);
        paint.setStrokeWidth(1.8f);
        paint.setColor(color);
        path.reset();

        if ("check".equals(icon)) {
            path.moveTo(5f, 12.5f);
            path.lineTo(9.5f, 17f);
            path.lineTo(19f, 6.5f);
            canvas.drawPath(path, paint);
        } else if ("close".equals(icon)) {
            path.moveTo(18f, 6f);
            path.lineTo(6f, 18f);
            path.moveTo(6f, 6f);
            path.lineTo(18f, 18f);
            canvas.drawPath(path, paint);
        } else if ("chevron".equals(icon)) {
            path.moveTo(9f, 6f);
            path.lineTo(15f, 12f);
            path.lineTo(9f, 18f);
            canvas.drawPath(path, paint);
        } else if ("back".equals(icon)) {
            path.moveTo(15f, 6f);
            path.lineTo(9f, 12f);
            path.lineTo(15f, 18f);
            canvas.drawPath(path, paint);
        } else if ("tv".equals(icon)) {
            rect.set(3f, 7f, 21f, 19f);
            canvas.drawRoundRect(rect, 2.6f, 2.6f, paint);
            path.moveTo(8.5f, 21f);
            path.lineTo(12f, 19f);
            path.lineTo(15.5f, 21f);
            path.moveTo(8f, 3f);
            path.lineTo(12f, 7f);
            path.lineTo(16f, 3f);
            canvas.drawPath(path, paint);
        } else if ("warning".equals(icon)) {
            path.moveTo(10.3f, 3.9f);
            path.lineTo(1.8f, 18f);
            path.cubicTo(1.2f, 19.2f, 2f, 21f, 3.5f, 21f);
            path.lineTo(20.5f, 21f);
            path.cubicTo(22f, 21f, 22.8f, 19.2f, 22.2f, 18f);
            path.lineTo(13.7f, 3.9f);
            path.cubicTo(12.9f, 2.5f, 11.1f, 2.5f, 10.3f, 3.9f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(12f, 9f, 12f, 13f, paint);
            canvas.drawPoint(12f, 17f, paint);
        } else if ("shield".equals(icon)) {
            path.moveTo(12f, 22f);
            path.cubicTo(12f, 22f, 20f, 18f, 20f, 12f);
            path.lineTo(20f, 5f);
            path.lineTo(12f, 2f);
            path.lineTo(4f, 5f);
            path.lineTo(4f, 12f);
            path.cubicTo(4f, 18f, 12f, 22f, 12f, 22f);
            path.close();
            path.moveTo(9f, 12f);
            path.lineTo(11f, 14f);
            path.lineTo(15f, 10f);
            canvas.drawPath(path, paint);
        } else if ("info".equals(icon)) {
            canvas.drawCircle(12f, 12f, 9f, paint);
            canvas.drawLine(12f, 11f, 12f, 16f, paint);
            canvas.drawPoint(12f, 8f, paint);
        } else if ("trash".equals(icon)) {
            canvas.drawLine(3f, 6f, 21f, 6f, paint);
            path.moveTo(8f, 6f);
            path.lineTo(8f, 4f);
            path.lineTo(16f, 4f);
            path.lineTo(16f, 6f);
            path.moveTo(19f, 6f);
            path.lineTo(18f, 20f);
            path.lineTo(6f, 20f);
            path.lineTo(5f, 6f);
            path.moveTo(10f, 11f);
            path.lineTo(10f, 17f);
            path.moveTo(14f, 11f);
            path.lineTo(14f, 17f);
            canvas.drawPath(path, paint);
        } else if ("download".equals(icon)) {
            path.moveTo(12f, 3f);
            path.lineTo(12f, 15f);
            path.moveTo(7f, 10f);
            path.lineTo(12f, 15f);
            path.lineTo(17f, 10f);
            path.moveTo(5f, 21f);
            path.lineTo(19f, 21f);
            canvas.drawPath(path, paint);
        } else if ("copy".equals(icon)) {
            rect.set(9f, 9f, 20f, 20f);
            canvas.drawRoundRect(rect, 2f, 2f, paint);
            path.moveTo(5f, 15f);
            path.lineTo(4f, 15f);
            path.cubicTo(2.9f, 15f, 2f, 14.1f, 2f, 13f);
            path.lineTo(2f, 4f);
            path.cubicTo(2f, 2.9f, 2.9f, 2f, 4f, 2f);
            path.lineTo(13f, 2f);
            path.cubicTo(14.1f, 2f, 15f, 2.9f, 15f, 4f);
            path.lineTo(15f, 5f);
            canvas.drawPath(path, paint);
        } else if ("external".equals(icon)) {
            path.moveTo(15f, 3f);
            path.lineTo(21f, 3f);
            path.lineTo(21f, 9f);
            path.moveTo(10f, 14f);
            path.lineTo(21f, 3f);
            path.moveTo(21f, 14f);
            path.lineTo(21f, 19f);
            path.cubicTo(21f, 20.1f, 20.1f, 21f, 19f, 21f);
            path.lineTo(5f, 21f);
            path.cubicTo(3.9f, 21f, 3f, 20.1f, 3f, 19f);
            path.lineTo(3f, 5f);
            path.cubicTo(3f, 3.9f, 3.9f, 3f, 5f, 3f);
            path.lineTo(10f, 3f);
            canvas.drawPath(path, paint);
        } else if ("terminal".equals(icon)) {
            path.moveTo(4f, 17f);
            path.lineTo(10f, 11f);
            path.lineTo(4f, 5f);
            path.moveTo(12f, 19f);
            path.lineTo(20f, 19f);
            canvas.drawPath(path, paint);
        } else if ("refresh".equals(icon)) {
            rect.set(3f, 3f, 21f, 21f);
            canvas.drawArc(rect, 40f, 280f, false, paint);
            path.moveTo(19f, 3f);
            path.lineTo(21f, 9f);
            path.lineTo(15f, 8f);
            canvas.drawPath(path, paint);
        } else if ("wifi".equals(icon)) {
            path.moveTo(5f, 12.55f);
            path.cubicTo(8.9f, 9.1f, 15.1f, 9.1f, 19f, 12.55f);
            path.moveTo(8.5f, 16.05f);
            path.cubicTo(10.4f, 14.4f, 13.6f, 14.4f, 15.5f, 16.05f);
            canvas.drawPath(path, paint);
            canvas.drawPoint(12f, 20f, paint);
        } else if ("lock".equals(icon)) {
            rect.set(4f, 10f, 20f, 20f);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, paint);
            path.moveTo(8f, 10f);
            path.lineTo(8f, 7f);
            path.cubicTo(8f, 4.8f, 9.8f, 3f, 12f, 3f);
            path.cubicTo(14.2f, 3f, 16f, 4.8f, 16f, 7f);
            path.lineTo(16f, 10f);
            canvas.drawPath(path, paint);
        } else if ("play".equals(icon)) {
            path.moveTo(8f, 5f);
            path.lineTo(19f, 12f);
            path.lineTo(8f, 19f);
            path.close();
            canvas.drawPath(path, paint);
        } else if ("power".equals(icon)) {
            canvas.drawLine(12f, 2f, 12f, 12f, paint);
            path.moveTo(18.4f, 6.6f);
            path.cubicTo(21.8f, 10.7f, 21f, 16.6f, 16.9f, 20f);
            path.cubicTo(12.8f, 23.4f, 6.9f, 22.6f, 3.5f, 18.5f);
            path.cubicTo(0.1f, 14.4f, 0.9f, 8.5f, 5f, 5.1f);
            canvas.drawPath(path, paint);
        } else if ("search".equals(icon)) {
            canvas.drawCircle(11f, 11f, 7f, paint);
            canvas.drawLine(16f, 16f, 20f, 20f, paint);
        } else if ("plus".equals(icon)) {
            canvas.drawCircle(12f, 12f, 9f, paint);
            canvas.drawLine(12f, 8f, 12f, 16f, paint);
            canvas.drawLine(8f, 12f, 16f, 12f, paint);
        } else if ("book".equals(icon)) {
            path.moveTo(4f, 5.5f);
            path.cubicTo(4f, 4.1f, 5.1f, 3f, 6.5f, 3f);
            path.lineTo(20f, 3f);
            path.lineTo(20f, 19f);
            path.lineTo(6.5f, 19f);
            path.cubicTo(5.1f, 19f, 4f, 20.1f, 4f, 21.5f);
            path.close();
            path.moveTo(8f, 7f);
            path.lineTo(16f, 7f);
            path.moveTo(8f, 11f);
            path.lineTo(14f, 11f);
            canvas.drawPath(path, paint);
        } else if ("sliders".equals(icon)) {
            canvas.drawLine(4f, 7f, 20f, 7f, paint);
            canvas.drawLine(4f, 12f, 20f, 12f, paint);
            canvas.drawLine(4f, 17f, 20f, 17f, paint);
            canvas.drawCircle(9f, 7f, 2f, paint);
            canvas.drawCircle(15f, 12f, 2f, paint);
            canvas.drawCircle(8f, 17f, 2f, paint);
        } else if ("store".equals(icon)) {
            path.moveTo(5.2f, 8.2f);
            path.lineTo(18.8f, 8.2f);
            path.lineTo(17.7f, 19.3f);
            path.lineTo(6.3f, 19.3f);
            path.close();
            path.moveTo(9f, 8.2f);
            path.lineTo(9f, 6.8f);
            path.cubicTo(9f, 5.1f, 10.3f, 3.8f, 12f, 3.8f);
            path.cubicTo(13.7f, 3.8f, 15f, 5.1f, 15f, 6.8f);
            path.lineTo(15f, 8.2f);
            path.moveTo(9.4f, 12.5f);
            path.lineTo(14.6f, 12.5f);
            canvas.drawPath(path, paint);
        } else if ("usb".equals(icon)) {
            canvas.drawLine(12f, 21f, 12f, 7f, paint);
            canvas.drawCircle(12f, 4.5f, 1.6f, paint);
            path.moveTo(8.5f, 9f);
            path.lineTo(12f, 5.5f);
            path.lineTo(15.5f, 9f);
            canvas.drawPath(path, paint);
            rect.set(5.5f, 14f, 8.5f, 17f);
            canvas.drawRect(rect, paint);
            rect.set(15.5f, 14f, 18.5f, 17f);
            canvas.drawRect(rect, paint);
            canvas.drawLine(8.5f, 15.5f, 5.5f, 15.5f, paint);
            canvas.drawLine(15.5f, 15.5f, 18.5f, 15.5f, paint);
        } else if ("link".equals(icon)) {
            rect.set(8f, 8f, 20f, 16f);
            canvas.drawRoundRect(rect, 4f, 4f, paint);
            rect.set(4f, 8f, 16f, 16f);
            canvas.drawRoundRect(rect, 4f, 4f, paint);
        } else if ("settings".equals(icon)) {
            canvas.drawCircle(12f, 12f, 3.4f, paint);
            canvas.drawCircle(12f, 12f, 7.2f, paint);
            path.moveTo(12f, 2f);
            path.lineTo(12f, 4.2f);
            path.moveTo(12f, 19.8f);
            path.lineTo(12f, 22f);
            path.moveTo(2f, 12f);
            path.lineTo(4.2f, 12f);
            path.moveTo(19.8f, 12f);
            path.lineTo(22f, 12f);
            path.moveTo(4.9f, 4.9f);
            path.lineTo(6.5f, 6.5f);
            path.moveTo(17.5f, 17.5f);
            path.lineTo(19.1f, 19.1f);
            path.moveTo(19.1f, 4.9f);
            path.lineTo(17.5f, 6.5f);
            path.moveTo(6.5f, 17.5f);
            path.lineTo(4.9f, 19.1f);
            canvas.drawPath(path, paint);
        } else if ("home".equals(icon)) {
            path.moveTo(3f, 11f);
            path.lineTo(12f, 3f);
            path.lineTo(21f, 11f);
            path.moveTo(5.5f, 9.5f);
            path.lineTo(5.5f, 20f);
            path.lineTo(18.5f, 20f);
            path.lineTo(18.5f, 9.5f);
            path.moveTo(9.5f, 20f);
            path.lineTo(9.5f, 14f);
            path.lineTo(14.5f, 14f);
            path.lineTo(14.5f, 20f);
            canvas.drawPath(path, paint);
        } else if ("dot".equals(icon)) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(12f, 12f, 3.2f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        canvas.restoreToCount(save);
    }
}