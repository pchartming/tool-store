package com.toolstore.installer;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** A bottom-sheet scroller that never grows past a share of the screen height. */
public final class MaxHeightScrollView extends ScrollView {

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxHeight = Math.round(screenHeight * 0.66f);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }
}