package org.graphiks.kanvas.gpu.evidence.oracle

/** Small validation-only premultiplied RGBA8 rasterizer for deterministic rectangle fixtures. */
class ReferenceRaster(private val width: Int, private val height: Int) {
    private val pixels = ByteArray(width * height * 4)

    fun clear(rgba: IntArray) = fillRect(0, 0, width, height, rgba)

    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, rgba: IntArray) {
        require(rgba.size == 4) { "RGBA color must have four channels" }
        for (y in top.coerceIn(0, height) until bottom.coerceIn(0, height)) {
            for (x in left.coerceIn(0, width) until right.coerceIn(0, width)) {
                val offset = (y * width + x) * 4
                for (channel in 0..3) pixels[offset + channel] = rgba[channel].toByte()
            }
        }
    }

    fun srcOver(left: Int, top: Int, right: Int, bottom: Int, rgba: IntArray) {
        require(rgba.size == 4) { "RGBA color must have four channels" }
        val alpha = rgba[3].coerceIn(0, 255)
        for (y in top.coerceIn(0, height) until bottom.coerceIn(0, height)) {
            for (x in left.coerceIn(0, width) until right.coerceIn(0, width)) {
                val offset = (y * width + x) * 4
                for (channel in 0..2) {
                    val source = rgba[channel].coerceIn(0, 255)
                    val destination = pixels[offset + channel].toInt() and 0xff
                    pixels[offset + channel] = (source + destination * (255 - alpha) / 255).coerceIn(0, 255).toByte()
                }
                val destinationAlpha = pixels[offset + 3].toInt() and 0xff
                pixels[offset + 3] = (alpha + destinationAlpha * (255 - alpha) / 255).coerceIn(0, 255).toByte()
            }
        }
    }

    fun rgba(): ByteArray = pixels.copyOf()
}
