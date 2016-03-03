package sikang_demo.rotate.main;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import sikang_demo.rotate.R;
import sikang_demo.rotate.util.SaveImage;

public class MainActivity extends Activity {
    private Button mSaveBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSaveBtn= (Button) this.findViewById(R.id.activity_main_save_btn);
        mSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaveImage save=new SaveImage();
                save.shoot(MainActivity.this);
            }
        });
    }
}
