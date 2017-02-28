package sh.nothing.droidbike.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Created by tnj on 2/26/17.
 */

public class AscentGraphView extends View {
    float ascent = 0.0f;
    int color = 0xff0000ff;
    Paint paint = new Paint();

    public AscentGraphView(Context context) {
        this(context, null);
    }

    public AscentGraphView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AscentGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AscentGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupPaint();
    }

    public void setAscent(float ascent) {
        if (this.ascent == ascent)
            return;

        this.ascent = ascent;
        invalidate();
    }

    public void setColorResource(@ColorRes int color) {
        setColor(getResources().getColor(color));
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
        paint.setColor(this.color);
        paint.setAlpha(96);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2;
        float ascentY = width / 2 * ascent;
        canvas.drawLine(0.0f, centerY + ascentY, (float) width, centerY - ascentY, paint);
    }

    void setupPaint() {
        paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp2px(2));
    }

    public float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
