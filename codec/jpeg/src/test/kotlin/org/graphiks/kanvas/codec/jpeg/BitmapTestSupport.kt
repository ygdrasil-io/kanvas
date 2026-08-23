package org.graphiks.kanvas.codec.jpeg
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ImageInfo

import org.graphiks.kanvas.image.Bitmap

internal operator fun Bitmap.get(index: Int): Int = getArgb(index % width, index / width)

internal operator fun Bitmap.set(index: Int, argb: Int) {
    setArgb(index % width, index / width, argb)
}

internal fun Bitmap.getPixelF16(x: Int, y: Int, out: FloatArray): Boolean =
    getPremulRgbaF16(x, y, out)

internal fun Bitmap.argbPixels(): IntArray = IntArray(width * height) { this[it] }
