package com.dragon.drawable.apng;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Animatable;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngReaderApng;

/**
 * @author chenjiulong
 */
public class APngHolder implements Animatable, APngDecoder.ReaderCallback {
    private final String TAG = "APngHolder";
    private final boolean DEBUG_DRAW = false;
    private final boolean DEBUG_DECODE = false;
    private final boolean DEBUG_DECODE_CALLBACK = false;
    private final IStream iStream;
    //Apng information.
    private final ImageInfo imageInfo;
    private final int frameCount;
    private final int playCount;

    private final FramesBin framesBin;
    private Bitmap frameBitmap;
    private Bitmap cachedBitmap;

    private final ExecutorService executorService;
    private final LinkedBlockingQueue<FrameData> frameQueue = new LinkedBlockingQueue<>();
    private volatile boolean isRunning = false;
    private volatile int currentPlayCount = 0;
    private volatile boolean ended = false;
    private APngDecoder decoder;
    private ReentrantLock reentrantLock = new ReentrantLock();
    private Runnable decodeRunnable = new Runnable() {

        @Override
        public void run() {
            reentrantLock.lock();
            try {
                if(ended){
                    return;
                }
                if (decoder == null) {
                    decoder = new APngDecoder(iStream.open());
                    decoder.prepare(frameBitmap, cachedBitmap, APngHolder.this);
                }
                FrameData frameData = framesBin.generateFrame();
                if (frameData == null) {
                    Log.d(TAG, "decode thread break2");
                    return;
                }
                long time = Debug.threadCpuTimeNanos() / 1000000;
                decoder.advance(frameData);
                if (DEBUG_DECODE) {
                    Log.d(TAG, "advance time " + (Debug.threadCpuTimeNanos() / 1000000 - time));
                }
            } finally {
                reentrantLock.unlock();
            }
        }
    };


    public APngHolder(IStream iStream, ExecutorService executorService) {
        this.iStream = iStream;
        this.executorService = executorService;
        //init apng information.
        PngReaderApng pngReaderApng = new PngReaderApng(iStream.open());
        imageInfo = pngReaderApng.imgInfo;
        frameCount = pngReaderApng.getApngNumFrames();
        playCount = pngReaderApng.getApngNumPlays() == 0 ? Integer.MAX_VALUE : pngReaderApng.getApngNumPlays();
        pngReaderApng.close();
        framesBin = new FramesBin(imageInfo.cols, imageInfo.rows, 2);
        frameBitmap = Bitmap.createBitmap(imageInfo.cols, imageInfo.rows, Bitmap.Config.ARGB_8888);
        cachedBitmap = Bitmap.createBitmap(imageInfo.cols, imageInfo.rows, Bitmap.Config.ARGB_8888);
    }

    public long draw(Canvas canvas) {
        FrameData frameData = frameQueue.peek();
        long delayUpdateTime = -3;
        final long currentTime = SystemClock.uptimeMillis();
        if (frameData != null) {
            if (frameData.firstDrawTime == 0) {//only for first frame
                frameData.firstDrawTime = currentTime;
                delayUpdateTime = frameData.firstDrawTime + frameData.delay;
                requestNewFrame();
                if (DEBUG_DRAW) {
                    Log.d(TAG, "draw frame first currentTime " + currentTime + " index " + frameData.index +" delayUpdateTime "+(delayUpdateTime - currentTime));
                }
            } else if (currentTime >= frameData.firstDrawTime + frameData.delay - 10) {//should seek to next frame.
                if (frameQueue.size() >= 2) { //seek to next frame
                    //remove old frame.
                    framesBin.recycleFrame(frameQueue.poll());
                    frameData = frameQueue.peek();
                    frameData.firstDrawTime = currentTime;
                    delayUpdateTime = frameData.firstDrawTime + frameData.delay;
                    requestNewFrame();
                    if (DEBUG_DRAW) {
                        Log.d(TAG, "draw frame update currentTime " + currentTime + " index " + frameData.index +" delayUpdateTime "+(delayUpdateTime - currentTime));
                    }
                } else {//there is no frame for seek
                    if (isRunning && !ended) {
                        delayUpdateTime = currentTime + 10;
                    } else {//to the end
                        isRunning = false;
                        delayUpdateTime = -1;
                    }
                    if (DEBUG_DRAW) {
                        Log.d(TAG, "draw frame failed currentTime " + currentTime + " index " + frameData.index +" delayUpdateTime "+(delayUpdateTime - currentTime));
                    }
                }
            } else {//update current frame more than one times
                if (isRunning) {
                    delayUpdateTime = frameData.firstDrawTime + frameData.delay;
                }else{
                    delayUpdateTime = -2;
                }
                if (DEBUG_DRAW) {
                    Log.d(TAG, "draw frame internal currentTime " + currentTime + " index " + frameData.index +" delayUpdateTime "+(delayUpdateTime - currentTime));
                }
            }
            canvas.drawBitmap(frameData.bitmap, 0, 0, null);
        } else {
            if (DEBUG_DRAW) {
                Log.d(TAG, "draw frame no data");
            }
            if (isRunning) {
                delayUpdateTime = currentTime + 20;
            }
        }
        return delayUpdateTime;
    }

    public void requestNewFrame() {
        if (isRunning && !ended) {
            executorService.execute(decodeRunnable);
        }
    }

    @Override
    public void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        if (ended) {
            ended = false;
            currentPlayCount = 0;
        }
        requestNewFrame();
    }

    @Override
    public void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public int getImageW() {
        return imageInfo.cols;
    }

    public int getImageH() {
        return imageInfo.rows;
    }

    @Override
    public void onDecodeStart(APngDecoder aPngDecoder) {
        if (DEBUG_DECODE_CALLBACK) {
            Log.d(TAG, "onDecodeStart playCount " + playCount + " current play count " + currentPlayCount);
        }
    }

    @Override
    public void onDecodeFrame(APngDecoder aPngDecoder, FrameData frameData) {
        if (DEBUG_DECODE_CALLBACK) {
            Log.d(TAG, "onFrame " + playCount + " index " + frameData.index);
        }
        frameQueue.offer(frameData);
    }

    @Override
    public void onDecodeEnd(APngDecoder aPngDecoder) {
        currentPlayCount++;
        if (DEBUG_DECODE_CALLBACK) {
            Log.d(TAG, "onDecodeEnd playCount " + playCount + " current play count " + currentPlayCount);
        }
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
        if (playCount == currentPlayCount) {
            ended = true;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        Log.d(TAG, "finalize");
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            cachedBitmap.recycle();
            cachedBitmap = null;
        }
        if (frameBitmap != null && !frameBitmap.isRecycled()) {
            frameBitmap.recycle();
            frameBitmap = null;
        }
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
        if (framesBin != null) {
            framesBin.clear();
        }
        super.finalize();
    }

    public interface IStream {
        InputStream open();
    }
}
