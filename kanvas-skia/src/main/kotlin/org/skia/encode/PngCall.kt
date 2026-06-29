package org.skia.encode

import org.skia.foundation.SkBitmap

public object PngCall {
    @Volatile
    private var encoder: ((SkBitmap) -> ByteArray?)? = null

    fun encode(bitmap: SkBitmap): ByteArray? {
        val enc = encoder ?: loadEncoder()
        return enc?.invoke(bitmap)
    }

    @Synchronized
    private fun loadEncoder(): ((SkBitmap) -> ByteArray?)? {
        encoder?.let { return it }
        try {
            Class.forName("org.graphiks.kanvas.codec.png.PngEncoder")
        } catch (_: ClassNotFoundException) {
        }
        return encoder
    }

    fun setEncoder(fn: (SkBitmap) -> ByteArray?) {
        if (encoder == null) encoder = fn
    }
}
