package com.dragon.apnglib

import android.graphics.Bitmap

/**
 * @author dragon
 */
internal class FrameData(val bitmap: Bitmap) {
    var frameCount = 0
    var index = 0
    var delay = 0L
    fun release() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    fun reset() {
        frameCount = 0
        index = 0
        delay = 0
    }
}