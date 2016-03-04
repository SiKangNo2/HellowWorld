package sikang_demo.rotate.main;

/**
 * Created by SiKang on 2016/3/1.
 */

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
    private Point mCancleBtnPoint, mMoveBtnPoint, mRotateBtnPoint, mScaleBtnPoint, mPointInParent;//按钮坐标
    private PointF mCenterPoint, mParentCenterPoint;
    private float mButtonRadius;//按钮半径
    private double nowAngle;
    private int mButtonCode, mTouchSlop, mLeft, mTop;
    private Bitmap mSrcBm, mCancleBm, mMoveBm, mRotateBm, mScaleBm;
    private Matrix mButtonMatrix, mSrcMatrix;
    private TouchActionController mActionController;
//    private int mQuadrant;

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
        startRawPoint = new PointF();
        startForParentPoint = new PointF();
        endForParentPoint = new PointF();

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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mLeft = getLeft();
        mTop = getTop();

        Log.d(TAG, mLeft + "  " + mTop);
    }

    private void initMatrix() {
        float scale = mWidth < mHeight ? (float) mWidth / (float) mSrcWidth : (float) mHeight / (float) mSrcHeight;
        mSrcMatrix.postScale(scale, scale);
        mSrcMatrix.postRotate(srcAngle, mCenterPoint.x, mCenterPoint.y);


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

    private PointF startPoint, startRawPoint, endPoint, startForParentPoint, endForParentPoint;
    private float nowTranX, nowTranY;
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
                startRawPoint.x = event.getRawX();
                startRawPoint.y = event.getRawY();
                //记录初始偏移值
                nowTranX = getTranslationX();
                nowTranY = getTranslationY();
                mButtonCode = whichOneTouched(event);
                break;
            //移动手指时
            case MotionEvent.ACTION_MOVE:
                endPoint.x = event.getX();
                endPoint.y = event.getY();
                moveX = event.getRawX() - startRawPoint.x;
                moveY = event.getRawY() - startRawPoint.y;
                if (Math.abs(moveX) > mTouchSlop || Math.abs(moveY) > mTouchSlop) {
                    isClick = false;
                }
                switch (mButtonCode) {
                    //操作旋转按钮时，旋转
                    case Constants.ACTION_ROTATE:
                        //得到目标角度
                        float angle = (this.getRotation() + getAngle(mCenterPoint, startPoint, endPoint)) % 360;
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
                        float targetX = nowTranX + moveX;
                        float targetY = nowTranY + moveY;
                        startForParentPoint.x = startPoint.x + targetX + mLeft;
                        startForParentPoint.y = startPoint.y + targetY + mTop;
                        endForParentPoint.x = endPoint.x + targetX + mLeft;
                        endForParentPoint.y = endPoint.y + targetY + mTop;
                        double moveAngle = getAngle(mParentCenterPoint, startForParentPoint, endForParentPoint);
                        double radius = pointDistance(mParentCenterPoint, endForParentPoint);
                        setTranslationX(targetX);
                        setTranslationY(targetY);

                        nowAngle = (nowAngle + moveAngle) % 360;
                        mActionController.notifyListener(this, Constants.ACTION_MOVE, moveAngle, radius);
                        Log.d(TAG, "半径： " + radius);
//                        getTargetTranslation(moveX, moveY);
                        break;
                }
                break;
            //抬起手指
            case MotionEvent.ACTION_UP:
                moveX = event.getRawX() - startRawPoint.x;
                moveY = event.getRawY() - startRawPoint.y;
                if (Math.abs(moveX) < mTouchSlop || Math.abs(moveY) < mTouchSlop) {
                    switch (mButtonCode) {
                        case Constants.ACTION_CANCLE:
                            ((ViewGroup) getParent()).removeView(this);
                            break;
                        case Constants.ACTION_MOVE:
                            if (isClick) {
                                mEndle = !mEndle;
                                if (mEndle)
                                    mActionController.notifyListener(this, Constants.ACTION_UNEDIT);
                                invalidate();
                            }
//                        else {
//                            mActionController.notifyListener(null, Constants.MOVE_FINISH);
//                        }
                            break;
                        case Constants.ACTION_FLIP:
                            Log.d(TAG, "sssssssssssssssssssss");
                            mSrcMatrix.postScale(-1, 1);
                            mSrcMatrix.postTranslate(mWidth, 0);
                            invalidate();
                            break;
                    }
                    break;
                }
        }
        return true;
    }

//
//    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//    private void getTargetTranslation(float tranX, float tranY) {
//        //目标偏移值
//        switch (mQuadrant) {
//            case Constants.QUADRANT_TWO:
//                tranX = -tranX;
//                break;
//            case Constants.QUADRANT_THREE:
//                tranX = -tranX;
//                tranY = -tranY;
//                break;
//            case Constants.QUADRANT_FOUR:
//                tranY = -tranY;
//                break;
//            case Constants.X_LEFT:
//                targetTran = -tranX;
//                break;
//            case Constants.X_RIGHT:
//                targetTran = tranX;
//                break;
//            case Constants.Y_TOP:
//                targetTran = -tranY;
//                break;
//            case Constants.Y_BOTTOM:
//                targetTran = tranY;
//                break;
//        }
//        mActionController.notifyListener(this, Constants.ACTION_MOVE, tranX, tranY);
//        moveView(tranX, tranY);
//
//    }

//    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//    private void moveView(float tranX, float tranY) {
//        if(mActionController.isMaxMove()&& translation>0)
//            return;
//        else if(mActionController.isMinMove() &&translation<0)
//            return;
    //目标偏移值
//        float newTranX = nowTranX;
//        float newTranY = nowTranY;
//        switch (mQuadrant) {
//            case Constants.QUADRANT_ONE:
//                newTranX += tranX;
//                newTranY += tranY;
//                break;
//            case Constants.QUADRANT_TWO:
//                newTranX -= tranX;
//                newTranY += tranY;
//                break;
//            case Constants.QUADRANT_THREE:
//                newTranX -= tranX;
//                newTranY -= tranY;
//                break;
//            case Constants.QUADRANT_FOUR:
//                newTranX += tranX;
//                newTranY -= tranY;
//                break;
//            case Constants.X_LEFT:
//                newTranX -= tranX;
//                break;
//            case Constants.X_RIGHT:
//                newTranX += translation;
//                break;
//            case Constants.Y_TOP:
//                newTranY -= translation;
//                break;
//            case Constants.Y_BOTTOM:
//                newTranY += translation;
//                break;
//
//        }
    //确保不会移出屏幕
//        if (newTranX + getLeft() > 0 && newTranX + getLeft() < getDimension(R.dimen.x320) - mWidth && newTranY + getTop() > 0 && newTranY + getTop() < getDimension(R.dimen.y480) - mHeight) {
//        setTranslationX(newTranX);
//        setTranslationY(newTranY);
//            mActionController.setIsMaxMove(false);
//            Log.d(TAG,mQuadrant+ "   no");
//        }else{
//            Log.d(TAG,mQuadrant+"   yes");
//            mActionController.setIsMaxMove(true);
//        }

//    }

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
                buttonCode = Constants.ACTION_FLIP;
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
     * 根据圆心和角度计算下一个view的位置
     *
     * @param center :circleLayout center point
     * @param angle  :child angle
     * @param radius :radius length
     */
    private void setNewPoint(PointF center, double angle, double radius) {
        double angleHude = angle * Math.PI / 180;
        mPointInParent.x = (int) (radius * Math.cos(angleHude) + center.x);
        mPointInParent.y = (int) (radius * Math.sin(angleHude) + center.y);
    }

    /**
     * 得到两点之间的角度
     */
    public float getAngle(PointF centerPoint, PointF start, PointF end) {
        //根据起始点和结束点以及View中心点求出三角形的三条边长
        double side1 = pointDistance(centerPoint, start);
        double side2 = pointDistance(start, end);
        double side3 = pointDistance(centerPoint, end);

        //得出弧度
        double cosb = (side1 * side1 + side3 * side3 - side2 * side2) / (2 * side1 * side3);
        if (cosb >= 1) {
            cosb = 1f;
        }
        double radian = Math.acos(cosb);

        //弧度换算为角度
        float angle = (float) (radian * 180 / Math.PI);

        float beforeX = start.x - centerPoint.x;
        float beforeY = start.y - centerPoint.y;
        float afterX = end.x - centerPoint.x;
        float afterY = end.y - centerPoint.y;
        //向量叉乘结果, 如果结果为负数， 表示为逆时针， 结果为正数表示顺时针
        float result = beforeX * afterY - beforeY * afterX;

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
                //更新坐标
                nowAngle = (nowAngle + (double) args[0]) % 360;
                setNewPoint(mParentCenterPoint, nowAngle, (double) args[1]);
                nowTranX = mPointInParent.x - mLeft - mWidth / 2;
                nowTranY = mPointInParent.y - mTop - mHeight / 2;
                setTranslationX(nowTranX);
                setTranslationY(nowTranY);
                break;
            case Constants.ACTION_UNEDIT:
                if (mEndle) {
                    mEndle = false;
                    invalidate();
                }
                break;
//            case Constants.MOVE_FINISH:
//                nowTranX = getTranslationX();
//                nowTranY = getTranslationY();
//                Log.d(TAG,mQuadrant+":   "+ nowTranX + "-------" + nowTranX);
//                break;
        }
    }

    public void setmParentCenterPoint(PointF mParentCenterPoint) {
        this.mParentCenterPoint = mParentCenterPoint;
    }

    public void setmPointInParent(Point mPointInParent) {
        this.mPointInParent = mPointInParent;
    }

    public void setNowAngle(double nowAngle) {
        this.nowAngle = nowAngle;
    }

//    public void setQuadrant(int quadrant) {
//        this.mQuadrant = quadrant;
//    }
}