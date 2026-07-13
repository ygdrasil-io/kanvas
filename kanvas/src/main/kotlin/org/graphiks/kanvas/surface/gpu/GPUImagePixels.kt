package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

internal fun Image.expandToRgbaForGpu(): ByteArray {
    val sourcePixels = pixels ?: return byteArrayOf()
    if (colorType == ColorType.RGBA_8888) return sourcePixels
    if (colorType == ColorType.BGRA_8888) {
        return sourcePixels.copyOf().also { rgba ->
            for (offset in rgba.indices step 4) {
                val blue = rgba[offset]
                rgba[offset] = rgba[offset + 2]
                rgba[offset + 2] = blue
            }
        }
    }

    if (colorType == ColorType.ALPHA_8) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val alpha = sourcePixels[i]
            val off = i * 4
            rgba[off] = alpha
            rgba[off + 1] = alpha
            rgba[off + 2] = alpha
            rgba[off + 3] = alpha
        }
        return rgba
    }

    val bitmap = Bitmap.fromImage(this)
    val rgba = ByteArray(width * height * 4)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = bitmap.getPixel(x, y)
            val off = (y * width + x) * 4
            rgba[off] = (color.r * 255f).toInt().coerceIn(0, 255).toByte()
            rgba[off + 1] = (color.g * 255f).toInt().coerceIn(0, 255).toByte()
            rgba[off + 2] = (color.b * 255f).toInt().coerceIn(0, 255).toByte()
            rgba[off + 3] = (color.a * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }
    return rgba
}
