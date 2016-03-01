package sikang_demo.ipctest;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private final String TAG="MyServiceDebug";
    IUser mUserBinder;
    ServiceConnection mServiceConnection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected" );
            mUserBinder=IUser.Stub.asInterface(service);
            try {
                Log.d(TAG,"login result"+mUserBinder.login("userName","userPwd"));
                mUserBinder.logout("userName");
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "oncreate" );
        setContentView(R.layout.activity_main);
        Intent intent=new Intent(MainActivity.this,MyService.class);
        bindService(intent,mServiceConnection,BIND_AUTO_CREATE);
    }

}
