package com.dragon.apnglib

import android.graphics.Bitmap

/**
 * @author dragon
 */
internal class FrameBuffer(w: Int, h: Int) {
    var prFrameData: FrameData = FrameData(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    var fgFrameData: FrameData = FrameData(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    var bgFrameData: FrameData = FrameData(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))

    fun swap() {
        val temp = prFrameData
        prFrameData = fgFrameData
        fgFrameData = bgFrameData
        bgFrameData = temp
    }

    fun reset() {
        fgFrameData.reset()
        prFrameData.reset()
        bgFrameData.reset()
    }

    fun release() {
        fgFrameData.release()
        prFrameData.release()
        bgFrameData.release()
    }

    fun cloneFgBuffer() = FrameData(Bitmap.createBitmap(fgFrameData.bitmap))
}