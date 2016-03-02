package sikang_demo.rotate;

/**
 * Created by SiKang on 2016/3/1.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import java.io.InputStream;

class RotateView extends ImageView {
    private final String TAG = "RotateViewDebug";
    private int mWidth, mHeight;
    private boolean mEndle;//编辑状态
    private Paint mLinePaint, mButtonPaint, mTextPaint;
    private Path mPath;
    private Point mCancleBtnPoint, mMoveBtnPoint, mRotateBtnPoint, mScaleBtnPoint;//按钮坐标
    private PointF mCenterPoint;
    private float mButtonRadius;//按钮半径
    private int mButtonCode;
    private int mTouchSlop;
    private Bitmap mSrcBm, mCancleBm, mMoveBm, mRotateBm, mScaleBm;
    private Matrix mButtonMatrix, mSrcMatrix;
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
        mTextPaint = new Paint();
        mPath = new Path();
        //init path
        mLinePaint.setColor(Color.RED);
        mLinePaint.setStrokeWidth(4);
        mLinePaint.setAntiAlias(true);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mButtonPaint.setAntiAlias(true);
        mButtonPaint.setColor(Color.BLUE);

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(10);

        //init Point
        mCancleBtnPoint = new Point();
        mRotateBtnPoint = new Point();
        mMoveBtnPoint = new Point();
        mScaleBtnPoint = new Point();
        mCenterPoint = new PointF();
        startPoint = new PointF();
        endPoint = new PointF();

        //init Bitmap
        Resources res = context.getResources();
        mCancleBm = BitmapFactory.decodeResource(res, R.mipmap.cancle);
        mRotateBm = BitmapFactory.decodeResource(res, R.mipmap.rotate);
        mMoveBm = BitmapFactory.decodeResource(res, R.mipmap.move);
        mScaleBm = BitmapFactory.decodeResource(res, R.mipmap.scale);
        mButtonMatrix = new Matrix();
        mSrcMatrix = new Matrix();
        mButtonCode = VIEW_CONTENT;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = measureHanlder(widthMeasureSpec);
        mHeight = measureHanlder(heightMeasureSpec);
        if (mWidth < 300) {
            mWidth = 300;
        }
        if (mHeight < 300) {
            mHeight = 300;
        }
        mButtonRadius = mWidth < mHeight ? mWidth / 15 : mHeight / 15;
        resetPoint((int) mButtonRadius);

        setMeasuredDimension(mWidth, mHeight);

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
        if (mSrcBm != null) {
            mSrcMatrix.setTranslate(mCancleBtnPoint.x, mCancleBtnPoint.y);
            canvas.drawBitmap(mSrcBm, mSrcMatrix, mButtonPaint);
            Log.d(TAG, "ondraw");
        }
        if (mEndle) {
            //绘制编辑框
            mPath.reset();
            mPath.moveTo(mCancleBtnPoint.x, mCancleBtnPoint.y);
            mPath.lineTo(mMoveBtnPoint.x, mMoveBtnPoint.y);
            mPath.lineTo(mScaleBtnPoint.x, mScaleBtnPoint.y);
            mPath.lineTo(mRotateBtnPoint.x, mRotateBtnPoint.y);
            mPath.lineTo(mCancleBtnPoint.x, mCancleBtnPoint.y);
            canvas.drawPath(mPath, mLinePaint);

            float scale = 50 / mCancleBm.getWidth();
            mButtonMatrix.setScale(scale, scale);
            //绘制功能按钮
            mButtonMatrix.setTranslate(0, 0);
            canvas.drawBitmap(mCancleBm, mButtonMatrix, mButtonPaint);

            mButtonMatrix.setTranslate(0, mHeight - mButtonRadius * 2);
            canvas.drawBitmap(mMoveBm, mButtonMatrix, mButtonPaint);

            mButtonMatrix.setTranslate(mWidth - mButtonRadius * 2, mHeight - mButtonRadius * 2);
            canvas.drawBitmap(mScaleBm, mButtonMatrix, mButtonPaint);

            mButtonMatrix.setTranslate(mWidth - mButtonRadius * 2, 0);
            canvas.drawBitmap(mRotateBm, mButtonMatrix, mButtonPaint);

//            canvas.drawBitmap(mCancleBm, mCancleBtnPoint.x, mCancleBtnPoint.y, mButtonPaint);
//            canvas.drawBitmap(mMoveBm, mMoveBtnPoint.x, mMoveBtnPoint.y, mButtonPaint);
//            canvas.drawBitmap(mScaleBm, mScaleBtnPoint.x, mScaleBtnPoint.y, mButtonPaint);
//            canvas.drawBitmap(mRotateBm, c, mButtonPaint);

//            canvas.drawCircle(mCancleBtnPoint.x, mCancleBtnPoint.y, mButtonRadius, mButtonPaint);
//            canvas.drawCircle(mMoveBtnPoint.x, mMoveBtnPoint.y, mButtonRadius, mButtonPaint);
//            canvas.drawCircle(mScaleBtnPoint.x, mScaleBtnPoint.y, mButtonRadius, mButtonPaint);
//            canvas.drawCircle(mRotateBtnPoint.x, mRotateBtnPoint.y, mButtonRadius, mButtonPaint);
        }

    }

    private void resetPoint(int radius) {
        mCancleBtnPoint.set(0 + radius, 0 + radius);
        mMoveBtnPoint.set(0 + radius, mHeight - radius);
        mScaleBtnPoint.set(mWidth - radius, mHeight - radius);
        mRotateBtnPoint.set(mWidth - radius, 0 + radius);
        mCenterPoint.set(mWidth / 2 + getLeft(), mHeight / 2 + getTop());
    }

    private PointF startPoint;
    private PointF endPoint;
    private float startRawX, startRawY, startTranX, startTranY;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float moveX;
        float moveY;
        switch (action) {
            //判断触点坐标所属区域（旋转、移动、缩放、取消、内容）
            case MotionEvent.ACTION_DOWN:
                startPoint.x = event.getX();
                startPoint.y = event.getY();
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startTranX = getTranslationX();
                startTranY = getTranslationY();
                mButtonCode = whichOneTouched(event);
                break;
            //移动手指时
            case MotionEvent.ACTION_MOVE:
                endPoint.x = event.getX();
                endPoint.y = event.getY();
                switch (mButtonCode) {
                    //操作旋转按钮时，旋转
                    case ROTATE_BUTTON:
                        //得到目标角度
                        float angle = (this.getRotation() + getAngle(startPoint, endPoint)) % 360;
                        this.setRotation(angle);
                        break;
                    //操作缩放按钮时
                    case SCALE_BUTTON:
                        //计算缩放比例
                        moveX = endPoint.x - startPoint.x;
                        moveY = endPoint.y - startPoint.y;
                        float scale = 1f + (Math.abs(moveX) > Math.abs(moveY) ? moveX / (startPoint.x - mCenterPoint.x) : moveY / (startPoint.y - mCenterPoint.y));
                        Log.d(TAG, scale + "");
                        mSrcMatrix.setScale(scale, scale);
                        invalidate();
                        break;
                    case MOVE_BUTTON:
                        float targetX = startTranX + (event.getRawX() - startRawX);
                        float targetY = startTranY + (event.getRawY() - startRawY);
                        //确保不会移除屏幕
                        if (targetX + getLeft() > 0 && targetX + getLeft() < getDimension(R.dimen.x320) - mWidth && targetY + getTop() > 0 && targetY + getTop() < getDimension(R.dimen.y480) - mHeight) {
                            setTranslationX(targetX);
                            setTranslationY(targetY);
                        }
                        break;
                }
                break;
            //抬起手指
            case MotionEvent.ACTION_UP:
                moveX = event.getX() - startPoint.x;
                moveY = event.getY() - startPoint.y;
                if (Math.abs(moveX) < mTouchSlop || Math.abs(moveY) < mTouchSlop) {
                    switch (mButtonCode) {
                        case CANCLE_BUTTON:
                            mEndle = false;
                            invalidate();
                            break;
                        case VIEW_CONTENT:
                            if (!mEndle) {
                                mEndle = true;
                                invalidate();
                            }
                            break;
                    }
                }
                break;
        }
        return true;
    }


    //判断触摸点在哪个区域
    private int whichOneTouched(MotionEvent event) {
        if (!mEndle) {
            return VIEW_CONTENT;
        }
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

    private float getDimension(int id) {
        return getResources().getDimension(id);

    }

    /**
     * 根据移动前后的坐标计算角度
     */
    public float getAngle(float x, float y) {
        double z = Math.sqrt(x * x + y * y);
        return Math.round((float) (Math.asin(y / z) / Math.PI * 180));//最终角度
    }

    /**
     * 计算两点之间的距离
     */
    private double pointDistance(PointF p1, PointF p2) {
        float x = p2.x - p1.x;
        float y = p2.y - p1.y;
        return Math.sqrt(x * x + y * y);
    }

    public void setSrc(int id) {
        mSrcBm = BitmapFactory.decodeResource(getContext().getResources(), id);
    }

    /**
     * 得到两点之间的角度
     */
    public float getAngle(PointF start, PointF end) {
        //根据起始点和结束点以及View中心点求出三角形的三条边长
        double side1 = pointDistance(mCenterPoint, start);
        double side2 = pointDistance(start, end);
        double side3 = pointDistance(mCenterPoint, end);

        //得出弧度
        double cosb = (side1 * side1 + side3 * side3 - side2 * side2) / (2 * side1 * side3);
        if (cosb >= 1) {
            cosb = 1f;
        }
        double radian = Math.acos(cosb);

        //弧度换算为角度
        float angle = (float) (radian * 180 / Math.PI);

        //center -> proMove的向量， 我们使用PointF来实现
        PointF centerToProMove = new PointF((start.x - mCenterPoint.x), (start.y - mCenterPoint.y));
        //center -> curMove 的向量
        PointF centerToCurMove = new PointF((end.x - mCenterPoint.x), (end.y - mCenterPoint.y));
        //向量叉乘结果, 如果结果为负数， 表示为逆时针， 结果为正数表示顺时针
        float result = centerToProMove.x * centerToCurMove.y - centerToProMove.y * centerToCurMove.x;

        if (result < 0) {
            angle = -angle;
        }
        return angle;

    }
}