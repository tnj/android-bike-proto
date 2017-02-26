package sh.nothing.droidbike.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import static android.R.attr.x;

/**
 * Created by tnj on 2/26/17.
 */

public class CircularRevolutionsView extends View {
    float rpm = 0.0f;
    int color = 0xff0000ff;
    Paint paint = new Paint();
    ValueAnimator animator;

    public CircularRevolutionsView(Context context) {
        this(context, null);
    }

    public CircularRevolutionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircularRevolutionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CircularRevolutionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupPaint();

        animator = new ValueAnimator();
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setFloatValues(0.0f, 1.0f);
        animator.addUpdateListener((animator) -> invalidate());
        animator.start();
        setRpm(rpm);
    }

    public void setRpm(float rpm) {
        long playTime = animator.getCurrentPlayTime();
        long lastDuartion = animator.getDuration();
        long newDuration = (long) (60000 / rpm);
        animator.setDuration(newDuration);
        animator.setCurrentPlayTime((long)(playTime * ((double)newDuration/lastDuartion)));
        this.rpm = rpm;
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
        int width = getWidth();
        int height = getHeight();
        int x = 0;
        int y = 0;
        int split = 16;
        if (width > height) {
            x = (width - height) / 2;
            width = height;
        } else {
            y = (height - width) / 2;
            height = width;
        }

        int centerX = width / 2 + x;
        int centerY = height / 2 + y;
        canvas.drawCircle(centerX, centerY, width / 16, paint);

        if (rpm != 0.0f) {
            int step = (int) ((Float) animator.getAnimatedValue() * split);
            if (step % 2 == 0) {
                float degree = step / (float) split;
                // canvas.drawArc(x, y, x + width, y + height, degree * 360 - 90, 360 / split, true, paint);

                canvas.drawCircle(
                    centerX - (float) Math.cos(2 * Math.PI * degree) * (width / 2) * 0.75f,
                    centerY - (float) Math.sin(2 * Math.PI * degree) * (height / 2) * 0.75f,
                    width / 8,
                    paint);
            }
        }

//        int centerX = width / 2;
//        int centerY = height / 2;
//            centerX - (float)Math.cos(2 * Math.PI * degree) * centerX,
//            centerY - (float)Math.sin(2 * Math.PI * degree) * centerY,
//            centerX - (float)Math.cos(2 * Math.PI * (degree + 0.5f)) * centerX,
//            centerY - (float)Math.sin(2 * Math.PI * (degree + 0.5f)) * centerY,
    }

    void setupPaint() {
        paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        // paint.setStrokeWidth(dp2px(2));
        // paint.setStrokeJoin(Paint.Join.ROUND);
        // paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
