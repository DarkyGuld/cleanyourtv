package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/** A frame whose left corners follow the Now Playing capsule; the right edge stays flush. */
public final class LeftRoundedFrameLayout extends FrameLayout {
    private final Path clip = new Path();
    private final float radius;

    public LeftRoundedFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = 22 * getResources().getDisplayMetrics().density;
        // Software keeps the clipPath's edge anti-aliased on this Android version.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h),
                // TL, TR, BR, BL. Both left corners round; the right edge stays flush.
                new float[]{radius, radius, 0, 0, 0, 0, radius, radius}, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
