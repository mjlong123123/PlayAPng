package com.dragon.apnglib

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.dragon.apnglib.APngHolder
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream

/**
 * @author dragon
 */
class APngHolderPool(private val lifecycle: Lifecycle) : LifecycleObserver {
    private val holders = mutableMapOf<String, APngHolder>()

    init {
        lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        holders.forEach {
            it.value.resume(true)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        holders.forEach {
            it.value.pause(true)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        holders.clear()
        lifecycle.removeObserver(this)
    }

    internal fun require(scope: CoroutineScope, file: String, streamCreator: () -> InputStream) =
        holders[file] ?: APngHolder(file, true, scope, streamCreator)
            .apply {
                holders[file] = this
                if (lifecycle.currentState >= Lifecycle.State.STARTED) {
                    resume(true)
                }
            }
}