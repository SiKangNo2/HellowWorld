package sikang_demo.rotate.main;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import sikang_demo.rotate.R;
import sikang_demo.rotate.util.SaveImage;

public class MainActivity extends Activity {
    private Button mSaveBtn;
    private SeekBar mSeekBar;
    private CircleLayout mCircleLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSaveBtn = (Button) this.findViewById(R.id.activity_main_save_btn);
        mSaveBtn.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            try {
                                                SaveImage save = new SaveImage();
                                                save.shoot(MainActivity.this);
                                            } catch (Exception e) {

                                            }

                                        }
                                    });
        mCircleLayout = (CircleLayout) this.

                findViewById(R.id.activity_main_circleLayout);

        mCircleLayout.setmHandler(mHandler);
        mSeekBar = (SeekBar) this.

                findViewById(R.id.activity_main_seekbar);

        mSeekBar.setMax(11);
        mSeekBar.setProgress(9);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()

                                            {
                                                @Override
                                                public void onProgressChanged(SeekBar seekBar,
                                                                              int progress, boolean fromUser) {
                                                    Log.d("Main", progress + "");
                                                    mCircleLayout.setChildCount(progress + 1);
                                                }

                                                @Override
                                                public void onStartTrackingTouch(SeekBar seekBar) {

                                                }

                                                @Override
                                                public void onStopTrackingTouch(SeekBar seekBar) {

                                                }
                                            }

        );
    }

    Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            mSeekBar.setProgress(mSeekBar.getProgress() - 1);
            return false;
        }
    });
}
