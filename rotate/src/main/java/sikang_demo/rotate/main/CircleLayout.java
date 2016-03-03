package sikang_demo.rotate.main;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import sikang_demo.rotate.R;
import sikang_demo.rotate.util.Constants;

/**
 * Created by SiKang on 2016/3/2.
 */
public class CircleLayout extends ViewGroup {
    private final String TAG = "CircleLayoutDebug";
    private float childAngle;
    private int mWidth;
    private int mHeight;
    private double mLayoutRadius;
    private Point mCenterPoint;

    public CircleLayout(Context context) {
        super(context);
        init();
    }

    public CircleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mCenterPoint = new Point();
    }

    @Override
    public void onViewAdded(View child) {
        if (getChildCount() <= 12) {
            super.onViewAdded(child);
        } else {
            removeViewAt(getChildCount() - 1);
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = measureHanlder(widthMeasureSpec);
        mHeight = measureHanlder(heightMeasureSpec);
        mCenterPoint.x = mWidth / 2;
        mCenterPoint.y = mHeight / 2;
        mLayoutRadius = mWidth < mHeight ? mWidth / 2 : mHeight / 2;
        Log.d(TAG, "onMeasure()");
    }

    //设置子View的位置
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!changed)
            return;
        int childCount = getChildCount();
        double childAngle = 360 / childCount;
        double layoutAngle = 270;
        double rotateAngle = 0;
        for (int i = 0; i < childCount; i++) {
            //得到子View
            TouchView childView = (TouchView) getChildAt(i);
            childView.setSrcAngle((float) rotateAngle);
            //为子View设置宽高
            int widthSPec = MeasureSpec.makeMeasureSpec((int) getDimension(R.dimen.x200), MeasureSpec.UNSPECIFIED);
            childView.measure(widthSPec, widthSPec);
            int childHalfWidth = childView.getMeasuredWidth() / 2;
            int childHalfHeight = childView.getMeasuredHeight() / 2;
            //计算半径
            double radius = mLayoutRadius - (childHalfWidth < childHalfHeight ? childHalfWidth : childHalfHeight);
            //计算元素的坐标
            Point viewPoint = getPoint(mCenterPoint, layoutAngle, radius);
            //计算所在象限
            childView.setQuadrant(getQuadrant(viewPoint));
            Log.d(TAG, "x:" + viewPoint.x + "  y:" + viewPoint.y + "   width:" + childHalfWidth + "    height:" + childHalfHeight);
            //设置子View的位置
            childView.layout(viewPoint.x - childHalfWidth, viewPoint.y - childHalfHeight, viewPoint.x + childHalfWidth, viewPoint.y + childHalfHeight);
            layoutAngle = (layoutAngle + childAngle) % 360;//更新角度
            rotateAngle = (rotateAngle + childAngle) % 360;
        }
    }

    /**
     * 根据圆心和角度计算下一个view的位置
     *
     * @param center :circleLayout center point
     * @param angle  :child angle
     * @param radius :radius length
     */
    private Point getPoint(Point center, double angle, double radius) {
        Point point = new Point();
        double angleHude = angle * Math.PI / 180;
        point.x = (int) (radius * Math.cos(angleHude)) + center.x;
        point.y = (int) (radius * Math.sin(angleHude)) + center.y;
        return point;
    }

    //处理Spec
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

    private float getDimension(int id) {
        return getResources().getDimension(id);

    }

    //获取所在象限
    private int getQuadrant(Point point) {
        int quadrant = -1;
        if (point.x > mCenterPoint.x) {
            if (point.y > mCenterPoint.y) {
                quadrant = Constants.QUADRANT_ONE;
            } else if (point.y < mCenterPoint.y) {
                quadrant = Constants.QUADRANT_FOUR;
            } else if (point.y == mCenterPoint.y) {
                quadrant = Constants.X_RIGHT;
            }
        } else if (point.x < mCenterPoint.x) {
            if (point.y > mCenterPoint.y) {
                quadrant = Constants.QUADRANT_TWO;
            } else if (point.y < mCenterPoint.y) {
                quadrant = Constants.QUADRANT_THREE;
            } else if (point.y == mCenterPoint.y) {
                quadrant = Constants.X_LEFT;
            }
        } else if (point.x == mCenterPoint.x) {
            if (point.y > mCenterPoint.y) {
                quadrant = Constants.Y_BOTTOM;
            } else if (point.y < mCenterPoint.y) {
                quadrant = Constants.Y_TOP;
            }
        }
        return quadrant;
    }

}
