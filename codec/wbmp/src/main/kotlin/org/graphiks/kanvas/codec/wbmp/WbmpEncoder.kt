package org.graphiks.kanvas.codec.wbmp

import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object WbmpEncoder {

    public fun encode(bitmap: SkBitmap): ByteArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(0)
        writeMultiByteInt(out, w)
        writeMultiByteInt(out, h)

        for (y in 0 until h) {
            var byte = 0
            var bitCount = 0
            for (x in 0 until w) {
                val argb = bitmap.getPixel(x, y)
                val luma = SkColorGetR(argb) * 299 +
                    SkColorGetG(argb) * 587 +
                    SkColorGetB(argb) * 114
                val bit = if (luma > THRESHOLD_TIMES_1000) 1 else 0
                byte = (byte shl 1) or bit
                bitCount++
                if (bitCount == 8) {
                    out.write(byte)
                    byte = 0
                    bitCount = 0
                }
            }
            if (bitCount > 0) {
                byte = byte shl (8 - bitCount)
                out.write(byte)
            }
        }

        return out.toByteArray()
    }

    public fun encode(dst: OutputStream, bitmap: SkBitmap): Boolean {
        val data = encode(bitmap) ?: return false
        return try {
            dst.write(data)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private const val THRESHOLD_TIMES_1000 = 127_500

    private fun writeMultiByteInt(out: OutputStream, value: Int) {
        require(value >= 0) { "WBMP multi-byte int must be non-negative ; got $value" }
        val chunks = ArrayList<Int>(5)
        var v = value
        chunks += v and 0x7F
        v = v ushr 7
        while (v > 0) {
            chunks += v and 0x7F
            v = v ushr 7
        }
        for (i in chunks.indices.reversed()) {
            val isLast = (i == 0)
            val b = chunks[i] or (if (isLast) 0 else 0x80)
            out.write(b)
        }
    }
}
