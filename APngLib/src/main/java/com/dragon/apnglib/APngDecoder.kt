package com.dragon.apnglib

import android.graphics.*
import android.util.Log
import ar.com.hjg.pngj.ChunkReader
import ar.com.hjg.pngj.ChunkSeqReaderPng
import ar.com.hjg.pngj.PngHelperInternal
import ar.com.hjg.pngj.PngReader
import ar.com.hjg.pngj.chunks.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32

/**
 * @author dragon
 */
internal class APngDecoder(inputStream: InputStream) : PngReader(inputStream) {
    private var frameBitmap: Bitmap
    private var cachedBitmap: Bitmap
    private var processingFrameData: FrameData? = null
    private var commonInfoArray: ByteArray? = null
    private val dataInfo = ByteArrayOutputStreamExt()
    private lateinit var pngChunkIHDR: PngChunkIHDR
    private lateinit var pngChunkACTL: PngChunkACTL
    private lateinit var pngChunkFCTL: PngChunkFCTL
    private var needWriteEndBeforeWriteHeader = false
    private var frameIndex = -1
    private var fetchFrameDone = false
    val columns: Int
    val rows: Int
    val frames: Int
    val plays: Int

    init {
        if (chunkseq.firstChunksNotYetRead()) {
            readFirstChunks()
        }
        if (chunkseq.idatSet != null && !chunkseq.idatSet.isDone) {
            chunkseq.idatSet.done()
        }
        columns = pngChunkIHDR.cols
        rows = pngChunkIHDR.rows
        frames = pngChunkACTL.numFrames
        plays = pngChunkACTL.numPlays
        frameBitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
        cachedBitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    }

    fun advance(mainFrameData: FrameData?) {
        this.processingFrameData = mainFrameData
        fetchFrameDone = false
        while (!chunkseq.isDone && streamFeeder.feed(chunkseq) > 0 && !fetchFrameDone);
        this.processingFrameData = null
    }

    override fun close() {
        super.close()
        frameBitmap.recycle()
        cachedBitmap.recycle()
    }

    override fun createChunkSeqReader(): ChunkSeqReaderPng {
        return object : ChunkSeqReaderPng(false) {
            override fun createChunkReaderForNewChunk(
                id: String,
                lenOut: Int,
                offset: Long,
                skip: Boolean
            ): ChunkReader {
                return if (id == PngChunkIDAT.ID || id == PngChunkFDAT.ID) {
                    object : ChunkReader(lenOut, id, offset, ChunkReaderMode.PROCESS) {
                        var crc32: CRC32? = null
                        var crcval = ByteArray(4)
                        override fun chunkDone() {
                            PngHelperInternal.writeInt4tobytes(crc32!!.value.toInt(), crcval, 0)
                            dataInfo.write(crcval, 0, 4)
                            crc32 = null
                        }

                        override fun processData(
                            offsetinChhunk: Int,
                            buf: ByteArray,
                            off: Int,
                            len: Int
                        ) {
                            if (crc32 == null) {
                                crc32 = CRC32()
                                crc32!!.update(ChunkHelper.b_IDAT)
                                PngHelperInternal.writeInt4(
                                    dataInfo,
                                    if (id == PngChunkIDAT.ID) lenOut else lenOut - 4
                                )
                                PngHelperInternal.writeBytes(dataInfo, ChunkHelper.b_IDAT)
                            }
                            if (id == PngChunkIDAT.ID) {
                                crc32!!.update(buf, off, len)
                                dataInfo.write(buf, off, len)
                            } else {
                                if (offsetinChhunk >= 4) {
                                    crc32!!.update(buf, off, len)
                                    dataInfo.write(buf, off, len)
                                } else {
                                    val skipSize = 4 - offsetinChhunk
                                    if (len <= 4 - offsetinChhunk) {
                                        //do nothing.
                                    } else {
                                        crc32!!.update(buf, off + skipSize, len - skipSize)
                                        dataInfo.write(buf, off + skipSize, len - skipSize)
                                    }
                                }
                            }
                        }
                    }
                } else super.createChunkReaderForNewChunk(id, lenOut, offset, skip)
            }

            override fun shouldSkipContent(len: Int, id: String): Boolean {
                return false
            }

            override fun isIdatKind(id: String): Boolean {
                return false
            }

            override fun postProcessChunk(chunkR: ChunkReader) {
                super.postProcessChunk(chunkR)
                processChunk(chunkR)
            }

            private fun processChunk(chunkReader: ChunkReader) {
                val id = chunkReader.chunkRaw.id
                if (DEBUG) {
                    Log.d(TAG, "processChunk $id")
                }
                when (id) {
                    PngChunkIHDR.ID -> {
                        pngChunkIHDR = chunksList.chunks[chunksList.chunks.size - 1] as PngChunkIHDR
                    }
                    PngChunkACTL.ID -> {
                        pngChunkACTL = chunksList.chunks[chunksList.chunks.size - 1] as PngChunkACTL
                    }
                    PngChunkFCTL.ID -> {
                        initFrameCommonInfo()
                        //write previous frame onDecodeEnd.
                        if (needWriteEndBeforeWriteHeader) {
                            writeEnd()
                        }
                        //write current header.
                        writeHeader()
                        //write common.
                        dataInfo.write(commonInfoArray!!, 0, commonInfoArray!!.size)
                        needWriteEndBeforeWriteHeader = true
                    }
                    PngChunkIEND.ID -> {
                        writeEnd()
                    }
                    else -> {
                    }
                }
            }

            private fun writeHeader() {
                frameIndex++
                pngChunkFCTL = chunksList.chunks[chunksList.chunks.size - 1] as PngChunkFCTL
                val frameInfo = pngChunkFCTL.equivImageInfo
                dataInfo.reset()
                //write signature.
                val pngIdSignature = PngHelperInternal.getPngIdSignature()
                dataInfo.write(pngIdSignature, 0, pngIdSignature.size)
                //write header.
                PngChunkIHDR(frameInfo).createRawChunk().writeChunk(dataInfo)
            }

            private fun writeEnd() {
                fetchFrameDone = true
                PngChunkIEND(null).createRawChunk().writeChunk(dataInfo)
                generateFrameDate(dataInfo.buffer(), dataInfo.size())
            }

            private fun generateFrameDate(data: ByteArray, length: Int) {
                frameBitmap = BitmapFactory.decodeByteArray(data, 0, length, generateOptions(frameBitmap))
                val canvas = Canvas(processingFrameData!!.bitmap)
                //Clear the canvas and draw cached bitmap(Previous content in cached bitmap).
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                if (frameIndex != 0) {
                    canvas.drawBitmap(cachedBitmap, 0f, 0f, null)
                } else {
                    canvas.drawBitmap(frameBitmap, 0f, 0f, null)
                }
                //Clear color in current frame position.
                if (pngChunkFCTL.blendOp == PngChunkFCTL.APNG_BLEND_OP_SOURCE) {
                    canvas.save()
                    canvas.clipRect(
                        pngChunkFCTL.getxOff(),
                        pngChunkFCTL.getyOff(),
                        pngChunkFCTL.getxOff() + frameBitmap.width,
                        pngChunkFCTL.getyOff() + frameBitmap.height
                    )
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    canvas.restore()
                }
                //Cached the background before draw current frame.
                if (pngChunkFCTL.disposeOp == PngChunkFCTL.APNG_DISPOSE_OP_BACKGROUND) {
                    val tempCanvas = Canvas(cachedBitmap)
                    tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    tempCanvas.drawBitmap(processingFrameData!!.bitmap, 0f, 0f, null)
                }
                //Draw current frame.
                canvas.drawBitmap(
                    frameBitmap,
                    pngChunkFCTL.getxOff().toFloat(),
                    pngChunkFCTL.getyOff().toFloat(),
                    null
                )
                //Cached the whole content after draw current frame.
                if (pngChunkFCTL.disposeOp == PngChunkFCTL.APNG_DISPOSE_OP_NONE) {
                    val tempCanvas = Canvas(cachedBitmap)
                    tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    tempCanvas.drawBitmap(processingFrameData!!.bitmap, 0f, 0f, null)
                }
                processingFrameData!!.index = frameIndex
                processingFrameData!!.delay = (pngChunkFCTL.delayNum * 1000 / pngChunkFCTL.delayDen).toLong()
                processingFrameData!!.frameCount = pngChunkACTL.numFrames
            }

            private fun generateOptions(reuseBitmap: Bitmap?): BitmapFactory.Options {
                val options = BitmapFactory.Options()
                options.inBitmap = reuseBitmap
                return options
            }

            /**
             * only record common information except (PngChunkIHDR.ID/PngChunkFCTL.ID/PngChunkACTL.ID)
             */
            private fun initFrameCommonInfo() {
                if (commonInfoArray != null) {
                    return
                }
                val commonInfo = ByteArrayOutputStream()
                val chunks = chunksList.chunks
                var pngChunk: PngChunk
                for (index in chunks.indices) {
                    pngChunk = chunks[index]
                    when (pngChunk.id) {
                        PngChunkIHDR.ID, PngChunkACTL.ID -> {
                        }
                        PngChunkFCTL.ID -> {
                            commonInfoArray = commonInfo.toByteArray()
                            return
                        }
                        else -> pngChunk.raw.writeChunk(commonInfo)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "APngDecoder"
        private const val DEBUG = false
    }
}