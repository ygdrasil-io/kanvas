package org.skia.encode

import org.skia.foundation.SkBitmap

public object JpegCall {
    @Volatile
    private var encoder: ((SkBitmap, Int) -> ByteArray?)? = null

    fun encode(bitmap: SkBitmap, quality: Int): ByteArray? {
        val enc = encoder ?: loadEncoder()
        return enc?.invoke(bitmap, quality)
    }

    @Synchronized
    private fun loadEncoder(): ((SkBitmap, Int) -> ByteArray?)? {
        encoder?.let { return it }
        try {
            Class.forName("org.graphiks.kanvas.codec.jpeg.JpegEncoder")
        } catch (_: ClassNotFoundException) {
        }
        return encoder
    }

    fun setEncoder(fn: (SkBitmap, Int) -> ByteArray?) {
        if (encoder == null) encoder = fn
    }
}
