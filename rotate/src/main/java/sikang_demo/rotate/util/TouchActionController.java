package sikang_demo.rotate.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SiKang on 2016/3/3.
 */
public class TouchActionController {
    private List<ViewTouchActionListener> mActionListener=new ArrayList<ViewTouchActionListener>(12);
    public static TouchActionController mTouchActionController=null;
    private TouchActionController(){}

    public static TouchActionController getInstance(){
        if(mTouchActionController==null) {
            synchronized (TouchActionController.class) {
                if (mTouchActionController == null) {
                    mTouchActionController=new TouchActionController();
                }
            }
        }
        return mTouchActionController;
    }
    public void setActionListener(ViewTouchActionListener listener){
        if(!mActionListener.contains(listener)){
            mActionListener.add(listener);
        }
    }

    public void removeActionListener(ViewTouchActionListener listener){
        if(mActionListener.contains(listener)){
            mActionListener.remove(listener);
        }
    }

    public void notifyListener(ViewTouchActionListener sender,int ACTION_ID,Object ...args){
        for (ViewTouchActionListener listener : mActionListener)
        {
            if(sender!=listener){
                listener.onTouchAction(ACTION_ID,args);
            }
        }
    }
}
