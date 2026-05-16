package org.skia.encode

import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * R-suivi.19 — WBMP (Wireless Bitmap) encoder for kanvas-skia.
 *
 * WBMP is a 1-bit-per-pixel monochrome format defined by the
 * [WAP Forum](https://en.wikipedia.org/wiki/Wireless_Application_Protocol_Bitmap_Format)
 * and used historically by feature phones. Skia upstream ships a
 * **decoder** (`include/codec/SkWbmpDecoder.h`) ; the encoder is a
 * kanvas-skia addition so call sites can produce monochrome blobs
 * symmetrically.
 *
 * Framing :
 *  - Byte 0 : `Type` field — `0` = B/W, no compression. The only
 *    type the WAP spec defines.
 *  - Byte 1 : `FixHeaderField` — always `0`.
 *  - Bytes 2…N : `Width` as a multi-byte unsigned integer (each byte
 *    carries 7 data bits ; the high bit signals "more bytes follow").
 *  - Bytes N+1…M : `Height` in the same multi-byte format.
 *  - Bytes M+1… : pixel data, MSB-first, 1 bit per pixel, byte-aligned
 *    per row (rows pad with `0` bits when the width is not a multiple
 *    of 8).
 *
 * Pixel quantisation : we compute Rec.601 luminance
 * `Y = (299·R + 587·G + 114·B) / 1000` and threshold at 0.5 (i.e.
 * `Y > 127.5`). Alpha is dropped — WBMP has no alpha channel.
 *
 * Pure Kotlin — no JNI.
 */
public object SkWbmpEncoder {

    /**
     * Encode [image]'s pixels into a WBMP byte stream wrapped in
     * [SkData], or `null` on failure.
     */
    public fun Encode(image: SkImage): SkData? {
        val bitmap = SkBitmap(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                bitmap.pixels[y * image.width + x] = image.peekPixel(x, y)
            }
        }
        return Encode(bitmap)
    }

    /**
     * Encode [bitmap] into a WBMP byte stream wrapped in [SkData].
     * Returns `null` on degenerate dimensions.
     */
    public fun Encode(bitmap: SkBitmap): SkData? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val out = ByteArrayOutputStream()
        out.write(0) // Type = 0 (B/W, no compression)
        out.write(0) // FixHeaderField (reserved)
        writeMultiByteInt(out, w)
        writeMultiByteInt(out, h)

        // Pixel data : 1 bit per pixel, MSB first, byte-aligned per row.
        for (y in 0 until h) {
            var byte = 0
            var bitCount = 0
            for (x in 0 until w) {
                val argb = bitmap.getPixel(x, y)
                // Rec.601 luminance × 1000 to keep the threshold integer.
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
                // Pad the trailing bits with zeros on the right so the
                // row remains MSB-aligned.
                byte = byte shl (8 - bitCount)
                out.write(byte)
            }
        }

        return SkData.MakeWithCopy(out.toByteArray())
    }

    /**
     * Encode [bitmap] into [dst] directly. Returns `true` on success ;
     * caller retains [dst] ownership.
     */
    public fun Encode(dst: OutputStream, bitmap: SkBitmap): Boolean {
        val data = Encode(bitmap) ?: return false
        return try {
            dst.write(data.toByteArray())
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Threshold for the Rec.601 luminance times 1000 — equivalent to `Y > 0.5`. */
    private const val THRESHOLD_TIMES_1000 = 127_500

    /**
     * Write [value] as a WAP multi-byte unsigned integer : each byte
     * carries 7 data bits ; the most-significant bit signals
     * "another byte follows". The bytes are emitted big-endian.
     */
    private fun writeMultiByteInt(out: OutputStream, value: Int) {
        require(value >= 0) { "WBMP multi-byte int must be non-negative ; got $value" }
        // Collect 7-bit chunks low → high, then emit them high → low,
        // setting the high bit on every byte except the last.
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
