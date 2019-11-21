package com.dragon.drawable.apng;

import android.graphics.Bitmap;

/**
 * @author chenjiulong
 */
public class FrameData {
    public FrameData(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Bitmap bitmap;
    public int frameCount;
    public int index = 0;
    public long delay = 0;
    public long firstDrawTime = 0;
}
