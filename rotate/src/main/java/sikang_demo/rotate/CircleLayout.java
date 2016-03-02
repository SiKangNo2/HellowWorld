package sikang_demo.rotate;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

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
        super.onViewAdded(child);

        Log.d(TAG, "onViewAdded()");
    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//        //计算布局半径
//        layoutRadius = mWidth < mHeight ? mWidth / 2 : mHeight / 2;
//        Point circleLayoutCenterPoint = new Point(getWidth() / 2, getHeight() / 2);
//        //开始角度，0度为(radiusLength,0)，这里从(0,radiusLength)，即正上方开始(360-90)
//        double nowAngle = 270;
//        // the angle between child and child
//        int len = getChildCount();
//        for (int i = 0; i < len; i++) {
//            View view = getChildAt(i);
//            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//            layoutParams.width = childWidth;
//            layoutParams.height = childWidth;
//            //get child's center Point
//            Point point = getPoint(circleLayoutCenterPoint, nowAngle, radiusLength);
//            layoutParams.setMargins(point.x - (childWidth / 2), point.y - (childWidth / 2), 0, 0);
//            view.setLayoutParams(layoutParams);
//            nowAngle += childAngle;
//        }
//    }

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

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout()");
        int childCount = getChildCount();
        double childAngle = 360 / childCount;
        double nowAngle = 270;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            int widthSPec=MeasureSpec.makeMeasureSpec((int) (mLayoutRadius / 3), MeasureSpec.UNSPECIFIED);
            childView.measure(widthSPec,widthSPec                              );
            int childHalfWidth=childView.getMeasuredWidth();
            int childHalfHeight=childView.getMeasuredHeight();
            Point nextPoint=getPoint(mCenterPoint, nowAngle, mLayoutRadius);
            Log.d(TAG,"x:"+nextPoint.x+"  y:"+nextPoint.y+"   width:"+childHalfWidth+"    height:"+childHalfHeight);
            childView.layout(nextPoint.x - childHalfWidth, nextPoint.y - childHalfHeight, nextPoint.x , nextPoint.y );
            nowAngle=nowAngle+childAngle;
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


}
