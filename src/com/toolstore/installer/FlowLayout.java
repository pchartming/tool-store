package com.toolstore.installer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** Wrapping row used for feature chips and status badges. */
public final class FlowLayout extends ViewGroup {

    private int horizontalGap;
    private int verticalGap;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setGap(int horizontalPx, int verticalPx) {
        horizontalGap = Math.max(0, horizontalPx);
        verticalGap = Math.max(0, verticalPx);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int available = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : Math.max(0, width - getPaddingLeft() - getPaddingRight());

        int lineWidth = 0;
        int lineHeight = 0;
        int usedWidth = 0;
        int totalHeight = 0;
        List<View> line = new ArrayList<View>();

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int nextWidth = line.isEmpty() ? childWidth : lineWidth + horizontalGap + childWidth;
            if (!line.isEmpty() && nextWidth > available) {
                usedWidth = Math.max(usedWidth, lineWidth);
                totalHeight += lineHeight + verticalGap;
                line.clear();
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth = nextWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
            line.add(child);
        }
        if (!line.isEmpty()) {
            usedWidth = Math.max(usedWidth, lineWidth);
            totalHeight += lineHeight;
        }

        int measuredWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? usedWidth + getPaddingLeft() + getPaddingRight()
                : width;
        setMeasuredDimension(measuredWidth, totalHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int nextX = x == getPaddingLeft() ? x + childWidth : x + horizontalGap + childWidth;
            if (x != getPaddingLeft() && nextX - getPaddingLeft() > available) {
                x = getPaddingLeft();
                y += lineHeight + verticalGap;
                lineHeight = 0;
                nextX = x + childWidth;
            }
            if (x != getPaddingLeft()) {
                x += horizontalGap;
            }
            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}