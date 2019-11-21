package com.dragon.drawable.apng;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;

import static android.graphics.PixelFormat.TRANSPARENT;

/**
 * @author chenjiulong
 */
public class APngDrawable extends Drawable implements Animatable, AnimationCallback {
    private APngHolder aPngHolder;
    private AnimationCallback animationCallback;

    private Runnable invalidateRunnable = () -> invalidateSelf();
    private Handler handler = new Handler();
    private boolean mStarting = false;
    private boolean alreadyEnded = false;

    public APngDrawable(APngHolder.IStream iStream, ExecutorService executorService) {
        aPngHolder = new APngHolder(iStream, executorService);
    }

    public APngDrawable(APngHolder aPngHolder) {
        this.aPngHolder = aPngHolder;
    }

    public void setAnimationCallback(AnimationCallback animationCallback) {
        this.animationCallback = animationCallback;
    }

    @Override
    public int getIntrinsicWidth() {
        return aPngHolder.getImageW();
    }

    @Override
    public int getIntrinsicHeight() {
        return aPngHolder.getImageH();
    }

    @Override
    public void setBounds(@NonNull Rect bounds) {
        super.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mStarting) {
            mStarting = false;
            animationStart();
        }
        final long time = aPngHolder.draw(canvas);
        if (time >= 0) {
            scheduleSelf(invalidateRunnable, time);
        } else if ((time == -1 || time == -2) && !alreadyEnded) {
            alreadyEnded = true;
            animationEnd();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        //do nothing
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        //do nothing
    }

    @Override
    public int getOpacity() {
        return TRANSPARENT;
    }


    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean ret = super.setVisible(visible, restart);
        return ret;
    }

    @Override
    public void start() {
        mStarting = true;
        alreadyEnded = false;
        aPngHolder.start();
        invalidateSelf();
    }

    @Override
    public void stop() {
        aPngHolder.stop();
    }

    @Override
    public boolean isRunning() {
        return aPngHolder.isRunning();
    }

    @Override
    public void animationStart() {
        if (animationCallback == null) {
            return;
        }
        handler.post(() -> {
            if (animationCallback != null) {
                animationCallback.animationStart();
            }
        });
    }

    @Override
    public void animationEnd() {
        if (animationCallback == null) {
            return;
        }
        handler.post(() -> {
            if (animationCallback != null) {
                animationCallback.animationEnd();
            }
        });
    }
}
