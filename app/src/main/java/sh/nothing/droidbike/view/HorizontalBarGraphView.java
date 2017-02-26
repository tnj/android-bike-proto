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

import sh.nothing.droidbike.R;

/**
 * Created by tnj on 2/26/17.
 */

public class HorizontalBarGraphView extends View {
    float max = 100.0f;
    float min = 0.0f;
    float current = 50.0f;
    int color = 0xff0000ff;
    Paint paint = new Paint();

    public HorizontalBarGraphView(Context context) {
        this(context, null);
    }

    public HorizontalBarGraphView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalBarGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HorizontalBarGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupPaint();
    }

    public void setMax(float max) {
        this.max = max;
        invalidate();
    }

    public void setMin(float min) {
        this.min = min;
        invalidate();
    }

    public void setCurrent(float current) {
        this.current = current;
        invalidate();
    }

    public void setColorResource(@ColorRes int color) {
        this.color = getResources().getColor(color);
        paint.setColor(this.color);
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
        paint.setColor(this.color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int x = (int) ( canvas.getWidth() * ( (Math.min(current, max) - min) / (max - min)));
        canvas.drawLine(x, 0, x, getHeight(), paint);
    }

    void setupPaint() {
        paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(dp2px(2));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
