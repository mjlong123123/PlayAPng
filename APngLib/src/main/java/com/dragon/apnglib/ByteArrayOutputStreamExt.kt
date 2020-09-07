package com.dragon.apnglib

import java.io.ByteArrayOutputStream

/**
 * @author dragon
 */
internal class ByteArrayOutputStreamExt : ByteArrayOutputStream() {
    fun buffer(): ByteArray {
        return buf
    }
}