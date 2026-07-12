package org.graphiks.kanvas.codec.jpegls

import java.io.ByteArrayOutputStream
import org.skia.foundation.SkBitmap

/**
 * Static JPEG-LS encoder for verified grayscale 8-bit, lossless LOCO-I data.
 * A non-gray or translucent bitmap is refused rather than silently converted.
 */
public object JpegLsEncoder {
    public data class Options(
        val nearLossless: Int = 0,
    ) {
        init {
            require(nearLossless == 0) {
                "near-lossless JPEG-LS is not evidence-backed in this encoder"
            }
        }
    }

    public fun encode(source: SkBitmap, options: Options = Options()): ByteArray? {
        if (source.width !in 1..0xFFFF || source.height !in 1..0xFFFF || options.nearLossless != 0) return null
        val samples = IntArray(source.width * source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getPixel(x, y)
                val a = argb ushr 24 and 0xFF
                val r = argb ushr 16 and 0xFF
                val g = argb ushr 8 and 0xFF
                val b = argb and 0xFF
                if (a != 0xFF || r != g || g != b) return null
                samples[y * source.width + x] = r
            }
        }
        val entropy = JpegLsEntropy.encode(source.width, source.height, samples)
        return ByteArrayOutputStream(entropy.size + 32).also { output ->
            output.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            output.write(byteArrayOf(0xFF.toByte(), 0xF7.toByte(), 0x00, 0x0B, 0x08))
            writeU16(output, source.height)
            writeU16(output, source.width)
            output.write(byteArrayOf(0x01, 0x01, 0x11, 0x00))
            output.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00))
            output.write(entropy)
            output.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        }.toByteArray()
    }

    private fun writeU16(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 8)
        output.write(value and 0xFF)
    }
}
