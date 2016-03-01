package sikang_demo.rotate;

/**
 * Created by SiKang on 2016/3/1.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

class RotateView extends ImageView {
    private final String TAG = "RotateViewDebug";
    private int mWidth, mHeight;
    private boolean mEndle;//编辑状态
    private Paint mLinePaint, mButtonPaint;
    private Path mPath;
    private Point mCancleBtnPoint, mMoveBtnPoint, mRotateBtnPoint, mScaleBtnPoint;//按钮坐标
    private float mButtonRadius;//按钮半径
    private int mButtonCode;
    private int mTouchSlop;
    private float viewRotation;
    //Button Code
    public static final int CANCLE_BUTTON = 1;
    public static final int MOVE_BUTTON = 2;
    public static final int ROTATE_BUTTON = 3;
    public static final int SCALE_BUTTON = 4;
    public static final int VIEW_CONTENT = 5;

    public RotateView(Context context) {
        super(context);
        init(context);
    }

    public RotateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mEndle = true;
        mLinePaint = new Paint();
        mButtonPaint = new Paint();
        mPath = new Path();
        //init path
        mLinePaint.setColor(Color.RED);
        mLinePaint.setStrokeWidth(4);
        mLinePaint.setAntiAlias(true);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mButtonPaint.setAntiAlias(true);
        mButtonPaint.setColor(Color.BLUE);

        //init Point
        mCancleBtnPoint = new Point();
        mRotateBtnPoint = new Point();
        mMoveBtnPoint = new Point();
        mScaleBtnPoint = new Point();

        mButtonCode = VIEW_CONTENT;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = measureHanlder(widthMeasureSpec);
        mHeight = measureHanlder(heightMeasureSpec);
        mButtonRadius = mWidth < mHeight ? mWidth / 15 : mHeight / 15;
        resetPoint((int) mButtonRadius);
        Log.d(TAG, "radius:" + mButtonRadius);
    }

    private int measureHanlder(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else if (specMode == MeasureSpec.AT_MOST) {
            result = Math.min(100, specSize);
        } else {
            result = 100;
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mEndle) {
            mPath.reset();
            mPath.moveTo(mCancleBtnPoint.x, mCancleBtnPoint.y);
            mPath.lineTo(mMoveBtnPoint.x, mMoveBtnPoint.y);
            mPath.lineTo(mScaleBtnPoint.x, mScaleBtnPoint.y);
            mPath.lineTo(mRotateBtnPoint.x, mRotateBtnPoint.y);
            mPath.lineTo(mCancleBtnPoint.x, mCancleBtnPoint.y);
            canvas.drawPath(mPath, mLinePaint);

            canvas.drawCircle(mCancleBtnPoint.x, mCancleBtnPoint.y, mButtonRadius, mButtonPaint);
            canvas.drawCircle(mMoveBtnPoint.x, mMoveBtnPoint.y, mButtonRadius, mButtonPaint);
            canvas.drawCircle(mScaleBtnPoint.x, mScaleBtnPoint.y, mButtonRadius, mButtonPaint);
            canvas.drawCircle(mRotateBtnPoint.x, mRotateBtnPoint.y, mButtonRadius, mButtonPaint);
        }

    }

    private void resetPoint(int radius) {
        mCancleBtnPoint.set(0 + radius, 0 + radius);
        mMoveBtnPoint.set(0 + radius, mHeight - radius);
        mScaleBtnPoint.set(mWidth - radius, mHeight - radius);
        mRotateBtnPoint.set(mWidth - radius, 0 + radius);
    }

    private float startX;
    private float startY;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX() - mWidth / 2;
                startY = event.getY() - mHeight / 2;
                Log.d(TAG, "startX:"+startX+"----startY:"+startY);
                mButtonCode = whichOneTouched(event);
                break;
            case MotionEvent.ACTION_MOVE:
                switch (mButtonCode) {
                    case VIEW_CONTENT:
                        super.onTouchEvent(event);
                        break;
                    case CANCLE_BUTTON:
                        mEndle = false;
                        invalidate();
                        break;
                    case ROTATE_BUTTON:
                        float endX = event.getX() - mWidth / 2;
                        float endY = event.getY() - mHeight / 2;
                        Log.d(TAG, "endX:"+endX+"----endY:"+startY);
                        float moveX = startX - endX;
                        float moveY = startY - endY;

                        if (Math.abs(moveX) > mTouchSlop || Math.abs(moveY) > mTouchSlop) {
                            float angle = getAngle(moveX, moveY);
                            float a = (this.getRotation() + angle) % 360;
                            Log.d(TAG, "angle:"+a);
                            this.setRotation(a);
                        }
                        break;

                }

                break;
        }
        return true;
    }


    //判断触摸点在哪个区域
    private int whichOneTouched(MotionEvent event) {
        int buttonCode = VIEW_CONTENT;
        float x = event.getX();
        float y = event.getY();
        if (x < mButtonRadius * 2) {
            if (y < mButtonRadius * 2) {
                buttonCode = CANCLE_BUTTON;
            } else if (y > mHeight - (mButtonRadius * 2)) {
                buttonCode = MOVE_BUTTON;
            }
        } else if (x > mWidth - (mButtonRadius * 2)) {
            if (y < mButtonRadius * 2) {
                buttonCode = ROTATE_BUTTON;
            } else if (y > mHeight - (mButtonRadius * 2)) {
                buttonCode = SCALE_BUTTON;
            }
        }
        return buttonCode;
    }

    /**
     * 根据移动前后的坐标计算角度
     */
    public float getAngle(float x, float y) {
        double z = Math.sqrt(x * x + y * y);
        return Math.round((float) (Math.asin(y / z) / Math.PI * 180));//最终角度
    }
}