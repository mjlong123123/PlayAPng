package com.dragon.apnglib

import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*

/**
 * @author dragon
 */
internal class APngHolder(
    private val file: String = "",
    private val isShared: Boolean = false,
    scope: CoroutineScope,
    private val streamCreator: () -> InputStream) : Animatable, Observable(), CoroutineScope by scope {
    companion object {
        const val DEBUG = true
        const val DEBUG_FRAME = false
    }

    /**
     * the job of decoding apng file.
     */
    private var playJob: Job? = null

    /**
     * The frame buffer contain three frames container.
     * the one is background frame for decoding the apng file.
     * the second one is foreground frame for drawing the content to screen.
     * the third one is recycled frame.
     */
    private var frameBuffer: FrameBuffer? = null

    /**
     * save the last frame data when play end.
     */
    private var lastFrameData: FrameData? = null

    /**
     * state flag
     */
    private var isStarted = false
    private var isResumed = false

    /**
     * record the played frame count.
     * When we restart the play,we will skip the frames according to this value.
     */
    private var skipFrameCount = 0

    /**
     * the information of apng file.
     */
    val columns: Int
    val rows: Int
    val plays: Int
    val frames: Int

    init {
        val decoder = APngDecoder(streamCreator.invoke())
        columns = decoder.columns
        rows = decoder.rows
        plays = if (decoder.plays > 0) decoder.plays else Int.MAX_VALUE
        frames = decoder.frames
        decoder.close()
        log { "create++++++++++++++++++++++++ tag $file columns $columns rows $rows plays $plays frames $frames" }
    }

    override fun start() {
        log { "start()" }
        isStarted = true
        tryToPlay()
    }

    fun resume(force: Boolean = false) {
        log { "resume()" }
        if (!force && isShared) {
            log { "resume() break" }
            return
        }
        isResumed = true
        tryToPlay()
    }

    fun pause(force: Boolean = false) {
        log { "pause()" }
        if (!force && isShared) {
            log { "pause() break" }
            return
        }
        isResumed = false
        tryToCancelPlay()
    }

    override fun stop() {
        log { "stop()" }
        isStarted = false
        tryToCancelPlay()
    }

    private fun tryToPlay() {
        log { "tryToPlay()" }
        if (!isResumed) {
            log { "tryToPlay() isResumed $isResumed" }
            return
        }
        if (!isStarted) {
            log { "tryToPlay() isStarted $isStarted" }
            return
        }
        if (playJob?.isActive == true) {
            log { "tryToPlay() playJob isActive $isActive" }
            return
        }
        playJob = launch(Dispatchers.IO) {

            /**
             * for decode the apng file.
             */
            var aPngDecoder: APngDecoder? = null
            frameBuffer = FrameBuffer(columns, rows)
            try {
                // send start event.
                sendEvent(PlayEvent.START)
                //Loop playback.
                repeat(plays) { playCounts ->
                    log { "play start play count : $playCounts" }
                    if (playCounts > 0) {
                        //send repeat event.
                        sendEvent(PlayEvent.REPEAT)
                    }
                    //init apng decoder and frame buffer.
                    if (aPngDecoder == null) {
                        aPngDecoder = APngDecoder(streamCreator.invoke())
                        frameBuffer!!.reset()
                    }
                    aPngDecoder?.let { decoder ->
                        log { "decode start decoder ${decoder.hashCode()} skipFrameCount $skipFrameCount" }
                        //seek to the last played frame.
                        repeat(skipFrameCount) {
                            decoder.advance(frameBuffer!!.bgFrameData)
                        }
                        //decode the left frames
                        repeat(frames - skipFrameCount) {
                            var time = System.currentTimeMillis()
                            decoder.advance(frameBuffer!!.bgFrameData)
                            time = System.currentTimeMillis() - time
                            //compute the delay time. We need to minus the decode time.
                            val delay = frameBuffer!!.fgFrameData.delay - time
                            skipFrameCount = frameBuffer!!.bgFrameData.index + 1
                            logFrame { "decode frame index ${frameBuffer!!.bgFrameData.index} skipFrameCount $skipFrameCount time $time delay $delay" }
                            delay(delay)
                            //swap the frame between fg frame and bg frame.
                            frameBuffer?.swap()
                            //send frame event.
                            sendEvent(PlayEvent.FRAME)
                        }
                        //close the apng decoder.
                        decoder.close()
                        skipFrameCount = 0
                        aPngDecoder = null
                        log { "decode end release decoder ${decoder.hashCode()}" }
                    }
                    log { "play end play count : $playCounts" }
                }
                //play end, reset the start state for next time to restart again.
                isStarted = false
                sendEvent(PlayEvent.END)
            } catch (e: Exception) {
                log { "launch  Exception ${e.message}" }
                //send cancel event.
                sendEvent(PlayEvent.CANCELED)
            } finally {
                log { "release decoder and frameBuffer in finally" }
                aPngDecoder?.close()
                lastFrameData?.release()
                lastFrameData = frameBuffer?.cloneFgBuffer()
                frameBuffer?.release()
            }
        }
    }

    private fun tryToCancelPlay() {
        log { "tryToCancelPlay()" }
        playJob?.cancel()
    }

    private fun sendEvent(event: PlayEvent) {
        setChanged()
        notifyObservers(event)
    }

    override fun isRunning(): Boolean {
        return playJob?.isActive ?: false
    }

    fun draw(canvas: Canvas) {
        when {
            frameBuffer?.fgFrameData?.bitmap?.isRecycled != true -> {
                frameBuffer?.fgFrameData
            }
            lastFrameData?.bitmap?.isRecycled != true -> {
                lastFrameData
            }
            else -> {
                null
            }
        }?.let {
            canvas.drawBitmap(it.bitmap, 0f, 0f, null)
        }
    }

    enum class PlayEvent {
        START,
        FRAME,
        END,
        CANCELED,
        REPEAT
    }

    fun finalize() {
        log { "finalize --------------------------" }
        tryToCancelPlay()
        lastFrameData?.release()
        lastFrameData = null
    }

    private inline fun log(action: () -> String) {
        if (DEBUG) Log.d("APngHolder[$file][${this.hashCode()}]", action.invoke())
    }

    private inline fun logFrame(action: () -> String) {
        if (DEBUG_FRAME) Log.d("APngHolder[$file][${this.hashCode()}]", action.invoke())
    }
}