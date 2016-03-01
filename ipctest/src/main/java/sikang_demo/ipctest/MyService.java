package sikang_demo.ipctest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by SiKang on 2016/2/29.
 */
public class MyService extends Service{
    private final String TAG="MyServiceDebug";
    IBinder mUserBinder= new IUser.Stub() {
        @Override
        public boolean login(String userName, String userPwd) throws RemoteException {
            Log.d(TAG,"log in success!");
            return true;
        }

        @Override
        public void logout(String userName) throws RemoteException {
            Log.d(TAG,"log out success!");
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mUserBinder;
    }
}
