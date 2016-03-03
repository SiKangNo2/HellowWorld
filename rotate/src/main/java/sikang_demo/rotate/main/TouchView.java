package sikang_demo.rotate.main;

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
import android.view.ViewGroup;
import android.widget.ImageView;

import sikang_demo.rotate.R;
import sikang_demo.rotate.util.Constants;
import sikang_demo.rotate.util.TouchActionController;
import sikang_demo.rotate.util.ViewTouchActionListener;


class TouchView extends ImageView implements ViewTouchActionListener {
    private final String TAG = "RotateViewDebug";
    private int mWidth, mHeight, mSrcWidth, mSrcHeight;
    private boolean mEndle;//编辑状态
    private Paint mLinePaint, mButtonPaint, mTextPaint, mSrcPaint;
    private Path mPath;
    private Point mCancleBtnPoint, mMoveBtnPoint, mRotateBtnPoint, mScaleBtnPoint;//按钮坐标
    private PointF mCenterPoint;
    private float mButtonRadius;//按钮半径
    private int mButtonCode;
    private int mTouchSlop;
    private Bitmap mSrcBm, mCancleBm, mMoveBm, mRotateBm, mScaleBm;
    private Matrix mButtonMatrix, mSrcMatrix;
    private TouchActionController mActionController;

    private int mQuadrant;

    public TouchView(Context context) {
        super(context);
        init(context);
    }

    public TouchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void init(Context context) {
        mActionController = TouchActionController.getInstance();
        mActionController.setActionListener(this);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mEndle = false;
        mLinePaint = new Paint();
        mButtonPaint = new Paint();
        mTextPaint = new Paint();
        mSrcPaint = new Paint();
        mPath = new Path();
        //init path
        mLinePaint.setColor(Color.RED);
        mLinePaint.setStrokeWidth(4);
        mLinePaint.setAntiAlias(true);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mButtonPaint.setAntiAlias(true);
        mButtonPaint.setColor(Color.BLUE);
        mSrcPaint.setAlpha(30);
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
        mButtonCode = Constants.ACTION_VIEW_CLICK;

        nowTranX = getTranslationX();
        nowTranY = getTranslationY();
        setSrc(1);
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
        mButtonRadius = getDimension(R.dimen.x10);
        resetPoint((int) mButtonRadius);
        initMatrix();
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

    private void resetPoint(int radius) {
        mCancleBtnPoint.set(0 + radius, 0 + radius);
        mMoveBtnPoint.set(0 + radius, mHeight - radius);
        mScaleBtnPoint.set(mWidth - radius, mHeight - radius);
        mRotateBtnPoint.set(mWidth - radius, 0 + radius);
        mCenterPoint.set(mWidth / 2, mHeight / 2);
    }

    private void initMatrix() {
        float scale = mWidth < mHeight ? (float) mWidth / (float) mSrcWidth : (float) mHeight / (float) mSrcHeight;
        mSrcMatrix.postScale(scale, scale);
        mSrcMatrix.postRotate(srcAngle, mCenterPoint.x, mCenterPoint.y);
//        mSrcMatrix.setTranslate(-(mSrcWidth - mWidth) / 2, -(mSrcHeight - mHeight) / 2);


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSrcBm != null) {
            canvas.drawBitmap(mSrcBm, mSrcMatrix, mSrcPaint);
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

        }

    }

    private PointF startPoint;
    private PointF endPoint;
    private float startRawX, startRawY, nowTranX, nowTranY;
    private boolean isClick;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float moveX;
        float moveY;
        switch (action) {
            //判断触点坐标所属区域（旋转、移动、缩放、取消、内容）
            case MotionEvent.ACTION_DOWN:
                isClick = true;
                //记录相对坐标
                startPoint.x = event.getX();
                startPoint.y = event.getY();
                //记录绝对坐标
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                //记录初始偏移值
                nowTranX = getTranslationX();
                nowTranY = getTranslationY();
                mButtonCode = whichOneTouched(event);
                break;
            //移动手指时
            case MotionEvent.ACTION_MOVE:
                endPoint.x = event.getX();
                endPoint.y = event.getY();
                moveX = event.getRawX() - startRawX;
                moveY = event.getRawY() - startRawY;
                if (Math.abs(moveX) > mTouchSlop || Math.abs(moveY) > mTouchSlop) {
                    isClick = false;
                }
                switch (mButtonCode) {
                    //操作旋转按钮时，旋转
                    case Constants.ACTION_ROTATE:
                        //得到目标角度
                        float angle = (this.getRotation() + getAngle(startPoint, endPoint)) % 360;
                        this.setRotation(angle);
                        mActionController.notifyListener(this, Constants.ACTION_ROTATE, angle);
                        break;
                    //操作缩放按钮时
                    case Constants.ACTION_SCALE:
                        //计算缩放比例
                        float scale = 1f + (Math.abs(moveX) > Math.abs(moveY) ? moveX / (startPoint.x - mCenterPoint.x) : moveY / (startPoint.y - mCenterPoint.y));
                        mSrcMatrix.postScale(scale, scale);
                        invalidate();
                        break;
                    case Constants.ACTION_MOVE:
                        getTargetTranslation(moveX, moveY);
                        break;
                }
                break;
            //抬起手指
            case MotionEvent.ACTION_UP:
                Log.d(TAG, "111111111111111111");
                moveX = event.getRawX() - startRawX;
                moveY = event.getRawY() - startRawY;

                switch (mButtonCode) {
                    case Constants.ACTION_CANCLE:
                        if (Math.abs(moveX) < mTouchSlop || Math.abs(moveY) < mTouchSlop)
                            ((ViewGroup) getParent()).removeView(this);
                        break;
                    case Constants.ACTION_MOVE:
                        Log.d(TAG, "555555555555555555");
                        if (isClick) {
                            Log.d(TAG, "333333333333333333");
                            mEndle = !mEndle;
                            if (mEndle)
                                mActionController.notifyListener(this, Constants.ACTION_UNEDIT);
                            invalidate();
                        } else {
                            Log.d(TAG, nowTranX + "-/////////////-" + nowTranX);
                            mActionController.notifyListener(null, Constants.MOVE_FINISH);
                        }
                        break;
                }
                break;
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void getTargetTranslation(float tranX, float tranY) {
        float targetTran = 0;
        boolean xBigger = Math.abs(tranX) >= Math.abs(tranY);
        //目标偏移值
        switch (mQuadrant) {
            case Constants.QUADRANT_ONE:
                targetTran = xBigger ? tranX : tranY;
                break;
            case Constants.QUADRANT_TWO:
                targetTran = xBigger ? -tranX : tranY;
                break;
            case Constants.QUADRANT_THREE:
                targetTran = xBigger ? -tranX : -tranY;
                break;
            case Constants.QUADRANT_FOUR:
                targetTran = xBigger ? tranX : -tranY;
                break;
            case Constants.X_LEFT:
                targetTran = -tranX;
                break;
            case Constants.X_RIGHT:
                targetTran = tranX;
                break;
            case Constants.Y_TOP:
                targetTran = -tranY;
                break;
            case Constants.Y_BOTTOM:
                targetTran = tranY;
                break;
        }
        mActionController.notifyListener(this, Constants.ACTION_MOVE, targetTran);
        moveView(targetTran);

    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean moveView(float translation) {
        //目标偏移值
        float newTranX = nowTranX;
        float newTranY = nowTranY;
        switch (mQuadrant) {
            case Constants.QUADRANT_ONE:
                newTranX += translation;
                newTranY += translation;
                break;
            case Constants.QUADRANT_TWO:
                newTranX -= translation;
                newTranY += translation;
                break;
            case Constants.QUADRANT_THREE:
                newTranX -= translation;
                newTranY -= translation;
                break;
            case Constants.QUADRANT_FOUR:
                newTranX += translation;
                newTranY -= translation;
                break;
            case Constants.X_LEFT:
                newTranX -= translation;
                break;
            case Constants.X_RIGHT:
                newTranX += translation;
                break;
            case Constants.Y_TOP:
                newTranY -= translation;
                break;
            case Constants.Y_BOTTOM:
                newTranY += translation;
                break;

        }
        //确保不会移出屏幕
        if (newTranX + getLeft() > 0 && newTranX + getLeft() < getDimension(R.dimen.x320) - mWidth && newTranY + getTop() > 0 && newTranY + getTop() < getDimension(R.dimen.y480) - mHeight) {
            setTranslationX(newTranX);
            setTranslationY(newTranY);
            return true;
        }
        return false;
    }

    private float srcAngle;

    public void setSrcAngle(float angle) {
        if (mSrcBm != null) {
            this.srcAngle = angle;
            mSrcBm = Bitmap.createBitmap(mSrcBm, 0, 0, mSrcWidth, mSrcHeight, mSrcMatrix, false);
        }
    }

    //判断触摸点在哪个区域
    private int whichOneTouched(MotionEvent event) {
        if (!mEndle) {
            return Constants.ACTION_MOVE;
        }
        int buttonCode = Constants.ACTION_MOVE;
        float x = event.getX();
        float y = event.getY();
        if (x < mButtonRadius * 2) {
            if (y < mButtonRadius * 2) {
                buttonCode = Constants.ACTION_CANCLE;
            } else if (y > mHeight - (mButtonRadius * 2)) {
                buttonCode = -1;
            }
        } else if (x > mWidth - (mButtonRadius * 2)) {
            if (y < mButtonRadius * 2) {
                buttonCode = Constants.ACTION_ROTATE;
            } else if (y > mHeight - (mButtonRadius * 2)) {
                buttonCode = Constants.ACTION_SCALE;
            }
        }
        return buttonCode;
    }

    private float getDimension(int id) {
        return getResources().getDimension(id);
    }


    /**
     * 计算两点之间的距离
     */
    private double pointDistance(PointF p1, PointF p2) {
        float x = p2.x - p1.x;
        float y = p2.y - p1.y;
        return Math.sqrt(x * x + y * y);
    }

    //设置图片
    public void setSrc(int id) {
        mSrcBm = BitmapFactory.decodeResource(getContext().getResources(), R.mipmap.img);
        mSrcWidth = mSrcBm.getWidth();
        mSrcHeight = mSrcBm.getHeight();
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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onTouchAction(int ACTION_ID, Object... args) {
        switch (ACTION_ID) {
            case Constants.ACTION_ROTATE:
                this.setRotation((float) args[0]);
                break;
            case Constants.ACTION_MOVE:
                moveView((float) args[0]);
                break;
            case Constants.ACTION_UNEDIT:
                if (mEndle) {
                    mEndle = false;
                    invalidate();
                }
                break;
            case Constants.MOVE_FINISH:
                nowTranX = getTranslationX();
                nowTranY = getTranslationY();
                Log.d(TAG, nowTranX + "-------" + nowTranX);
                break;
        }
    }

    public void setQuadrant(int quadrant) {
        this.mQuadrant = quadrant;
    }
}