package com.dragon.drawable.apng;

import java.io.ByteArrayOutputStream;

/**
 * @author chenjiulong
 */
public class ByteArrayOutputStreamExt extends ByteArrayOutputStream {

	public byte[] buffer() {
		return buf;
	}
}
