package org.graphiks.kanvas.codec.bmp

import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object BmpEncoder {

    public enum class BmpFormat {
        kBGRA_8888,
        kBGR_888,
    }

    public data class Options(
        val format: BmpFormat = BmpFormat.kBGRA_8888,
    )

    private val defaultOptions = Options()

    public fun encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val bpp = if (options.format == BmpFormat.kBGRA_8888) 4 else 3
        val rowSize = (w * bpp + 3) and 3.inv()
        val pixelDataSize = rowSize * h
        val fileSize = FILE_HEADER_SIZE + DIB_HEADER_SIZE + pixelDataSize

        val out = ByteArrayOutputStream(fileSize)
        out.write('B'.code)
        out.write('M'.code)
        writeU32LE(out, fileSize)
        writeU16LE(out, 0); writeU16LE(out, 0)
        writeU32LE(out, FILE_HEADER_SIZE + DIB_HEADER_SIZE)

        writeU32LE(out, DIB_HEADER_SIZE)
        writeU32LE(out, w)
        writeU32LE(out, -h)
        writeU16LE(out, 1)
        writeU16LE(out, bpp * 8)
        writeU32LE(out, 0)
        writeU32LE(out, pixelDataSize)
        writeU32LE(out, 2835)
        writeU32LE(out, 2835)
        writeU32LE(out, 0)
        writeU32LE(out, 0)

        val pad = rowSize - w * bpp
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = bitmap.getPixel(x, y)
                out.write(SkColorGetB(argb))
                out.write(SkColorGetG(argb))
                out.write(SkColorGetR(argb))
                if (bpp == 4) out.write(SkColorGetA(argb))
            }
            for (i in 0 until pad) out.write(0)
        }

        return out.toByteArray()
    }

    public fun encode(dst: OutputStream, bitmap: SkBitmap, options: Options = defaultOptions): Boolean {
        val data = encode(bitmap, options) ?: return false
        return try {
            dst.write(data)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private const val FILE_HEADER_SIZE = 14
    private const val DIB_HEADER_SIZE = 40

    private fun writeU32LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeU16LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }
}
