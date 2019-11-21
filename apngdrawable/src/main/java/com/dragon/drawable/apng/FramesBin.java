package com.dragon.drawable.apng;

import android.graphics.Bitmap;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author chenjiulong
 */
public class FramesBin {

	private final LinkedBlockingDeque<FrameData> queue = new LinkedBlockingDeque<>();
	private int width;
	private int height;
	private int initialCapacity;

	public FramesBin(int width, int height, int initialCapacity) {
		this.width = width;
		this.height = height;
		this.initialCapacity = initialCapacity;
		enlargeQueue(initialCapacity);
	}

	private void enlargeQueue(final int size) {
		for (int i = 0; i < size; i++) {
			queue.add(new FrameData(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)));
		}
	}

	public void checkWidthAndHeight(int width, int height, int initialCapacity) {
		if (width != this.width || height != this.height) {
			this.width = width;
			this.height = height;
			this.initialCapacity = initialCapacity;
			clear();
			enlargeQueue(initialCapacity);
			return;
		}

		int enlargeCount = initialCapacity - this.initialCapacity;
		if (enlargeCount > 0) {
			enlargeQueue(enlargeCount);
		}
	}

	/**
	 * recycle frame.
	 *
	 * @param frameData
	 */
	public void recycleFrame(FrameData frameData) {
		if (frameData == null) {
			return;
		}
		queue.offer(frameData);
	}


	/**
	 * this method maybe be blocked if there is no data.
	 *
	 * @return
	 */
	public FrameData generateFrame(){
		return queue.poll();
	}

	/**
	 * clear all data.
	 */
	public void clear() {
		FrameData frameData;
		while ((frameData = queue.poll()) != null) {
			if (frameData.bitmap != null && !frameData.bitmap.isRecycled()) {
				frameData.bitmap.recycle();
			}
		}
	}
}
