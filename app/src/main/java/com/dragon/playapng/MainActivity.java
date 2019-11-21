package com.dragon.playapng;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.dragon.drawable.apng.APngDrawable;
import com.dragon.drawable.apng.AnimationCallback;

import java.io.InputStream;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    ImageView imageView;
    ImageView imageView1;
    ImageView imageView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        imageView1 = findViewById(R.id.image1);
        imageView2 = findViewById(R.id.image2);
        try {
            imageView1.setImageDrawable(Drawable.createFromStream(getAssets().open("google.png"), null));
            imageView2.setImageDrawable(Drawable.createFromStream(getAssets().open("blued.png"), null));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    APngDrawable aPngDrawable;

    public void onClickView(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.image1:
                setDrawable("google.png");
                break;
            case R.id.image2:
                setDrawable("blued.png");
                break;
            case R.id.imageView:
                if (aPngDrawable != null) {
                    if (aPngDrawable.isRunning()) {
                        aPngDrawable.stop();
                    } else {
                        aPngDrawable.start();
                    }
                }
                break;
        }
    }

    private void setDrawable(final String assetsName) {
        aPngDrawable = new APngDrawable(() -> {
            InputStream inputStream = null;
            try {
                inputStream = getAssets().open(assetsName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return inputStream;
        }, Executors.newFixedThreadPool(2));
        aPngDrawable.setAnimationCallback(new AnimationCallback() {
            @Override
            public void animationStart() {
                Log.d("callback", "animationStart ");
            }

            @Override
            public void animationEnd() {
                Log.d("callback", "animationEnd ");
            }
        });
        aPngDrawable.start();
        imageView.setImageDrawable(aPngDrawable);

    }
}
