package sikang_demo.rotate;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private RotateView mTouchView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTouchView= (RotateView) this.findViewById(R.id.mTouchView);
        mTouchView.setSrc(R.mipmap.ic_launcher);
    }
}
