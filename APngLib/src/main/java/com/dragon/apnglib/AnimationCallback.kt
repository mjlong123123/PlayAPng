package com.dragon.apnglib

/**
 * @author dragon
 */
interface AnimationCallback {
    fun onAnimationStart(drawable: APngDrawable)
    fun onAnimationRepeat(drawable: APngDrawable)
    fun onAnimationEnd(drawable: APngDrawable)
    fun onAnimationCanceled(drawable: APngDrawable)
}