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

public class HorizontalBarGraphView extends View {
    float max = 100.0f;
    float min = 0.0f;
    float current = 50.0f;
    int color = 0xff00ffff;

    boolean averageSet = false;
    float average = 0.0f;

    Paint paint = new Paint();
    Paint averagePaint = new Paint();
    private int averageAlpha = 96;

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

    public void setAverage(float average) {
        this.average = average;
        averageSet = true;
        invalidate();
    }

    public void setColorResource(@ColorRes int color) {
        setColor(getResources().getColor(color));
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
        paint.setColor(this.color);
        averagePaint.setColor(this.color);
        averagePaint.setAlpha(averageAlpha);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int x = (int) (canvas.getWidth() * ((Math.min(current, max) - min) / (max - min)));
        int height = getHeight();
        canvas.drawLine(x, 0, x, height, paint);

        if (averageSet) {
            int averageX = (int) (canvas.getWidth() * ((Math.min(average, max) - min) / (max - min)));
            canvas.drawRect(0, height - height / 4, averageX, height, averagePaint);
        }
    }

    void setupPaint() {
        paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(dp2px(2));
        paint.setStyle(Paint.Style.STROKE);

        averagePaint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        averagePaint.setAlpha(averageAlpha);
//        paint.setStrokeJoin(Paint.Join.ROUND);
//        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
