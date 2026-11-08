package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** An ImageView that measures itself square: its side is the smaller of the space it is given. */
public final class SquareImageView extends ImageView {
    public SquareImageView(Context context) { super(context); }

    public SquareImageView(Context context, AttributeSet attrs) { super(context, attrs); }

    public SquareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int side;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            side = height;
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            side = width;
        } else {
            side = Math.min(width, height);
        }
        setMeasuredDimension(side, side);
    }
}
