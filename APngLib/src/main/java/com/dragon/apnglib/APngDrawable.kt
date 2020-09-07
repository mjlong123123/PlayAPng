package com.dragon.apnglib

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream
import java.util.*

/**
 * @author dragon
 */
class APngDrawable internal constructor(
        val file: String,
        sharedHolders: APngHolderPool?,
        scope: CoroutineScope,
        streamCreator: () -> InputStream
) : Drawable(), Animatable, Observer {

    private val holder: APngHolder = sharedHolders?.require(scope, file, streamCreator) ?: APngHolder(file, false, scope, streamCreator)

    var callback: AnimationCallback? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.obj) {
                APngHolder.PlayEvent.START -> {
                    callback?.onAnimationStart(this@APngDrawable)
                }
                APngHolder.PlayEvent.FRAME -> {
                    invalidateSelf()
                }
                APngHolder.PlayEvent.REPEAT -> {
                    callback?.onAnimationRepeat(this@APngDrawable)
                }
                APngHolder.PlayEvent.END -> {
                    callback?.onAnimationEnd(this@APngDrawable)
                }
                APngHolder.PlayEvent.CANCELED -> {
                    callback?.onAnimationCanceled(this@APngDrawable)
                }
            }
        }
    }

    init {
        holder.addObserver(this)
    }

    override fun draw(canvas: Canvas) {
        holder.draw(canvas)
    }

    override fun getIntrinsicWidth(): Int {
        return holder.columns
    }

    override fun getIntrinsicHeight(): Int {
        return holder.rows
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (visible) holder.resume() else holder.pause()
        return super.setVisible(visible, restart)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun start() {
        holder.start()
    }

    override fun stop() {
        holder.stop()
    }

    override fun isRunning() = holder.isRunning

    override fun update(holder: Observable?, event: Any?) {
        handler.sendMessage(handler.obtainMessage().apply { obj = event })
    }

    fun release() {
        holder.deleteObserver(this)
    }

    fun finalize() {
        release()
    }
}