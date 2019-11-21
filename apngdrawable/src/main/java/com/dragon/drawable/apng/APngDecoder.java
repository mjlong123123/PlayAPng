package com.dragon.drawable.apng;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.zip.CRC32;

import ar.com.hjg.pngj.ChunkReader;
import ar.com.hjg.pngj.ChunkSeqReaderPng;
import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngHelperInternal;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.chunks.ChunkHelper;
import ar.com.hjg.pngj.chunks.ChunkRaw;
import ar.com.hjg.pngj.chunks.PngChunk;
import ar.com.hjg.pngj.chunks.PngChunkACTL;
import ar.com.hjg.pngj.chunks.PngChunkFCTL;
import ar.com.hjg.pngj.chunks.PngChunkFDAT;
import ar.com.hjg.pngj.chunks.PngChunkIDAT;
import ar.com.hjg.pngj.chunks.PngChunkIEND;
import ar.com.hjg.pngj.chunks.PngChunkIHDR;

import static ar.com.hjg.pngj.ChunkReader.ChunkReaderMode.PROCESS;
import static ar.com.hjg.pngj.chunks.PngChunkFCTL.APNG_DISPOSE_OP_BACKGROUND;
import static ar.com.hjg.pngj.chunks.PngChunkFCTL.APNG_DISPOSE_OP_NONE;

/**
 * @author chenjiulong
 */
public class APngDecoder extends PngReader {
    private static String TAG = "APngDecoder";
    private static boolean DEBUG = false;
    private Bitmap frameBitmap;
    private Bitmap cachedBitmap;
    private ReaderCallback readerCallback;
    private FrameData mainFrameData;

    private byte[] commonInfoArray = null;
    private PngChunkIHDR pngChunkIHDR;
    private PngChunkACTL pngChunkACTL;

    private ByteArrayOutputStreamExt dataInfo = new ByteArrayOutputStreamExt();
    private PngChunkFCTL pngChunkFCTL;
    private boolean needWriteEndBeforeWriteHeader = false;
    private int frameIndex = -1;

    private boolean fetchFrameDone = false;

    public APngDecoder(File file) {
        super(file);
    }

    public APngDecoder(InputStream inputStream) {
        super(inputStream);
    }

    public void prepare(Bitmap frameBitmap, Bitmap cachedBitmap, @NonNull ReaderCallback readerCallback) {
        this.readerCallback = readerCallback;
        this.frameBitmap = frameBitmap;
        this.cachedBitmap = cachedBitmap;
        if (this.chunkseq.firstChunksNotYetRead()) {
            this.readFirstChunks();
        }
        if (this.chunkseq.getIdatSet() != null && !this.chunkseq.getIdatSet().isDone()) {
            this.chunkseq.getIdatSet().done();
        }
    }

    public int advance(FrameData mainFrameData) {
        this.mainFrameData = mainFrameData;
        fetchFrameDone = false;
        while (!this.chunkseq.isDone() && this.streamFeeder.feed(this.chunkseq) > 0 && !fetchFrameDone)
            ;
        return frameIndex;
    }

    @Override
    protected ChunkSeqReaderPng createChunkSeqReader() {
        return new ChunkSeqReaderPng(false) {
            @Override
            protected ChunkReader createChunkReaderForNewChunk(final String id, final int lenOut, long offset, final boolean skip) {
                if (id.equals(PngChunkIDAT.ID) || id.equals(PngChunkFDAT.ID)) {
                    return new ChunkReader(lenOut, id, offset, PROCESS) {
                        CRC32 crc32 = null;
                        public byte[] crcval = new byte[4];

                        protected void chunkDone() {
                            PngHelperInternal.writeInt4tobytes((int) crc32.getValue(), crcval, 0);
                            dataInfo.write(crcval, 0, 4);
                            crc32 = null;
                        }

                        protected void processData(int offsetinChhunk, byte[] buf, int off, int len) {
                            if (crc32 == null) {
                                crc32 = new CRC32();
                                crc32.update(ChunkHelper.b_IDAT);
                                PngHelperInternal.writeInt4(dataInfo, id.equals(PngChunkIDAT.ID) ? lenOut : lenOut - 4);
                                PngHelperInternal.writeBytes(dataInfo, ChunkHelper.b_IDAT);
                            }
                            if (id.equals(PngChunkIDAT.ID)) {
                                crc32.update(buf, off, len);
                                dataInfo.write(buf, off, len);
                            } else {
                                if (offsetinChhunk >= 4) {
                                    crc32.update(buf, off, len);
                                    dataInfo.write(buf, off, len);
                                } else {
                                    int skip = (4 - offsetinChhunk);
                                    if (len <= skip) {
                                        //do nothing.
                                    } else {
                                        crc32.update(buf, off + skip, len - skip);
                                        dataInfo.write(buf, off + skip, len - skip);
                                    }
                                }
                            }
                        }
                    };
                }
                return super.createChunkReaderForNewChunk(id, lenOut, offset, skip);
            }

            @Override
            protected void startNewChunk(int len, String id, long offset) {
                super.startNewChunk(len, id, offset);
            }

            @Override
            public boolean shouldSkipContent(int len, String id) {
                return false;
            }

            @Override
            protected boolean isIdatKind(String id) {
                return false;
            }

            @Override
            protected void postProcessChunk(ChunkReader chunkR) {
                super.postProcessChunk(chunkR);
                processChunk(chunkR);
            }

            private void processChunk(ChunkReader chunkReader) {
                final String id = chunkReader.getChunkRaw().id;
                if (DEBUG) {
                    Log.d(TAG, "processChunk " + id);
                }
                switch (id) {
                    case PngChunkIHDR.ID: {
                        pngChunkIHDR = (PngChunkIHDR) chunksList.getChunks().get(chunksList.getChunks().size() - 1);
                        break;
                    }
                    case PngChunkACTL.ID: {
                        pngChunkACTL = (PngChunkACTL) chunksList.getChunks().get(chunksList.getChunks().size() - 1);
                        break;
                    }
                    case PngChunkFCTL.ID: {
                        initFrameCommonInfo();
                        //write previous frame onDecodeEnd.
                        if (needWriteEndBeforeWriteHeader) {
                            writeEnd();
                        }
                        //write current header.
                        writeHeader();
                        //write common.
                        dataInfo.write(commonInfoArray, 0, commonInfoArray.length);
                        needWriteEndBeforeWriteHeader = true;
                        break;
                    }
                    case PngChunkIDAT.ID: {
                        readerCallback.onDecodeStart(APngDecoder.this);
                        //write data.
                        chunkReader.getChunkRaw().writeChunk(dataInfo);
                        break;
                    }
                    case PngChunkFDAT.ID: {
                        //write data
                        ChunkRaw chunkRaw = new ChunkRaw(chunkReader.getChunkRaw().len - 4, ChunkHelper.b_IDAT, true);
                        System.arraycopy(chunkReader.getChunkRaw().data, 4, chunkRaw.data, 0, chunkRaw.data.length);
                        chunkRaw.writeChunk(dataInfo);
                        break;
                    }
                    case PngChunkIEND.ID:
                        writeEnd();
                        readerCallback.onDecodeEnd(APngDecoder.this);
                        break;
                    default:
                        break;
                }
            }

            private void writeHeader() {
                frameIndex++;
                pngChunkFCTL = (PngChunkFCTL) chunksList.getChunks().get(chunksList.getChunks().size() - 1);
                ImageInfo frameInfo = pngChunkFCTL.getEquivImageInfo();
                dataInfo.reset();
                //write signature.
                byte[] pngIdSignature = PngHelperInternal.getPngIdSignature();
                dataInfo.write(pngIdSignature, 0, pngIdSignature.length);
                //write header.
                new PngChunkIHDR(frameInfo).createRawChunk().writeChunk(dataInfo);
            }

            private void writeEnd() {
                fetchFrameDone = true;
                new PngChunkIEND(null).createRawChunk().writeChunk(dataInfo);
                FrameData frameData = generateFrameDate(dataInfo.buffer(), dataInfo.size());
                mainFrameData = null;
                if (frameData != null) {
                    readerCallback.onDecodeFrame(APngDecoder.this, frameData);
                }
            }

            private FrameData generateFrameDate(byte[] data, int length) {
                frameBitmap = BitmapFactory.decodeByteArray(data, 0, length, generateOptions(frameBitmap));
                final Canvas canvas = new Canvas(mainFrameData.bitmap);
                //Clear the canvas and draw cached bitmap(Previous content in cached bitmap).
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                if (frameIndex != 0) {
                    canvas.drawBitmap(cachedBitmap, 0, 0, null);
                } else {
                    canvas.drawBitmap(frameBitmap, 0, 0, null);
                }
                //Clear color in current frame position.
                if (pngChunkFCTL.getBlendOp() == PngChunkFCTL.APNG_BLEND_OP_SOURCE) {
                    canvas.save();
                    canvas.clipRect(pngChunkFCTL.getxOff(), pngChunkFCTL.getyOff(), pngChunkFCTL.getxOff() + frameBitmap.getWidth(), pngChunkFCTL.getyOff() + frameBitmap.getHeight());
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    canvas.restore();
                }
                //Cached the background before draw current frame.
                if (pngChunkFCTL.getDisposeOp() == APNG_DISPOSE_OP_BACKGROUND) {
                    Canvas tempCanvas = new Canvas(cachedBitmap);
                    tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    tempCanvas.drawBitmap(mainFrameData.bitmap, 0, 0, null);
                }
                //Draw current frame.
                canvas.drawBitmap(frameBitmap, pngChunkFCTL.getxOff(), pngChunkFCTL.getyOff(), null);
                //Cached the whole content after draw current frame.
                if (pngChunkFCTL.getDisposeOp() == APNG_DISPOSE_OP_NONE) {
                    Canvas tempCanvas = new Canvas(cachedBitmap);
                    tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    tempCanvas.drawBitmap(mainFrameData.bitmap, 0, 0, null);
                }

                mainFrameData.index = frameIndex;
                mainFrameData.delay = (pngChunkFCTL.getDelayNum() * 1000 / pngChunkFCTL.getDelayDen());
                mainFrameData.firstDrawTime = 0;
                mainFrameData.frameCount = pngChunkACTL.getNumFrames();
                return mainFrameData;
            }

            private BitmapFactory.Options generateOptions(Bitmap reuseBitmap) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inBitmap = reuseBitmap;
                return options;
            }


            /**
             * only record common information except (PngChunkIHDR.ID/PngChunkFCTL.ID/PngChunkACTL.ID)
             */
            private void initFrameCommonInfo() {
                if (commonInfoArray != null) {
                    return;
                }
                ByteArrayOutputStream commonInfo = new ByteArrayOutputStream();
                List<PngChunk> chunks = chunksList.getChunks();
                PngChunk pngChunk;
                for (int index = 0; index < chunks.size(); index++) {
                    pngChunk = chunks.get(index);
                    switch (pngChunk.id) {
                        case PngChunkIHDR.ID:
                        case PngChunkACTL.ID:
                            continue;
                        case PngChunkFCTL.ID:
                            commonInfoArray = commonInfo.toByteArray();
                            return;
                        default:
                            pngChunk.getRaw().writeChunk(commonInfo);
                            break;
                    }
                }
            }

        };
    }


    public interface ReaderCallback {
        void onDecodeStart(APngDecoder aPngDecoder);

        void onDecodeFrame(APngDecoder aPngDecoder, FrameData frameData);

        void onDecodeEnd(APngDecoder aPngDecoder);
    }
}
