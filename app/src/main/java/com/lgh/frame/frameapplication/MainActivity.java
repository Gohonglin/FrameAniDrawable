package com.lgh.frame.frameapplication;

import android.graphics.Bitmap;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.lgh.frame.frameanimlibrary.FrameAniDrawable;

public class MainActivity extends AppCompatActivity {
    private ImageView mFrameAniView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFrameAniView = (ImageView)findViewById(R.id.frame_drawable_view);
        final FrameAniDrawable aniDrawable = FrameAniDrawable.getDrawable(this,R.drawable.test_frame_drawable,Bitmap.Config.RGB_565);
        mFrameAniView.setImageDrawable(aniDrawable);
        aniDrawable.setMaxRepeatTimes(10000);
        aniDrawable.start();
    }
}
