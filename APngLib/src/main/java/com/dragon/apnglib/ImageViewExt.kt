package com.dragon.apnglib

import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import java.io.FileInputStream
import java.io.InputStream

/**
 * @author dragon
 */

fun ImageView.playAPngFile(
    scope: CoroutineScope,
    file: String,
    animationCallback: AnimationCallback? = object : AnimationCallback {
        override fun onAnimationStart(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationStart ${drawable.file}")
        }

        override fun onAnimationRepeat(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationRepeat ${drawable.file}")
        }

        override fun onAnimationEnd(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationEnd ${drawable.file}")
        }

        override fun onAnimationCanceled(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationCanceled ${drawable.file}")
        }
    },
    sharedHolders: APngHolderPool? = null
) {
    playAPng(scope, file, { FileInputStream(file) }, animationCallback, sharedHolders)
}

fun ImageView.playAPngAsset(
    scope: CoroutineScope,
    file: String,
    animationCallback: AnimationCallback? = object : AnimationCallback {
        override fun onAnimationStart(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationStart ${drawable.file}")
        }

        override fun onAnimationRepeat(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationRepeat ${drawable.file}")
        }

        override fun onAnimationEnd(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationEnd ${drawable.file}")
        }

        override fun onAnimationCanceled(drawable: APngDrawable) {
            Log.d("ImageViewExt", "onAnimationCanceled ${drawable.file}")
        }
    },
    sharedHolders: APngHolderPool? = null
) {
    playAPng(scope, file, { resources.assets.open(file) }, animationCallback, sharedHolders)
}

fun ImageView.playAPng(
        scope: CoroutineScope,
        file: String,
        streamCreator: () -> InputStream,
        animationCallback: AnimationCallback? = null,
        sharedHolders: APngHolderPool? = null
) {
    (drawable as? APngDrawable)?.release()
    setImageDrawable(APngDrawable(file, sharedHolders, scope, streamCreator).apply {
        callback = animationCallback
        start()
    })
}

fun ImageView.replayAPng() {
    (drawable as? APngDrawable)?.let { drawable ->
        drawable.start()
    }
}

fun ImageView.stopAPng() {
    (drawable as? APngDrawable)?.let { drawable ->
        drawable.stop()
    }
}

fun ImageView.clearAPng() {
    (drawable as? APngDrawable)?.let { drawable ->
        drawable.release()
    }
    setImageDrawable(null)
}