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

    public enum class Compression {
        NONE,
        RLE8,
        RLE4,
    }

    public data class Options(
        val format: BmpFormat = BmpFormat.kBGRA_8888,
        val compression: Compression = Compression.NONE,
    )

    private val defaultOptions = Options()

    public fun encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        if (options.compression != Compression.NONE) {
            return encodeRle(bitmap, options)
        }

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

    private fun encodeRle(bitmap: SkBitmap, options: Options): ByteArray? {
        val colorSet = mutableSetOf<Int>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            colorSet.add(bitmap.getPixel(x, y) and 0x00FFFFFF)
        }
        if (colorSet.size > 256) return null

        val palette = colorSet.toList()
        val w = bitmap.width
        val h = bitmap.height

        val bpp = if (options.compression == Compression.RLE4) 4 else 8
        val paletteSize = palette.size * 4
        val dataOffset = FILE_HEADER_SIZE + DIB_HEADER_SIZE + paletteSize
        val rleData = ByteArrayOutputStream()

        for (y in 0 until h) {
            val indices = IntArray(w) { x -> palette.indexOf(bitmap.getPixel(x, y) and 0x00FFFFFF) }
            var i = 0
            while (i < w) {
                val colorIdx = indices[i]
                var runLen = 1
                while (i + runLen < w && indices[i + runLen] == colorIdx && runLen < 255) runLen++

                if (runLen >= if (bpp == 4) 2 else 1) {
                    rleData.write(runLen)
                    if (bpp == 8) {
                        rleData.write(colorIdx)
                    } else {
                        rleData.write(((colorIdx and 0x0F) shl 4) or (colorIdx and 0x0F))
                    }
                    i += runLen
                } else {
                    var absCount = 0
                    val absStart = i
                    while (i < w && absCount < 255) {
                        if (i + 2 <= w && indices[i] == indices[i + 1]) break
                        absCount++
                        i++
                    }
                    rleData.write(0)
                    rleData.write(absCount)
                    var j = absStart
                    while (j < absStart + absCount) {
                        if (bpp == 8) {
                            rleData.write(indices[j])
                            j++
                        } else {
                            val hi = indices[j] and 0x0F
                            val lo = if (j + 1 < absStart + absCount) indices[j + 1] and 0x0F else 0
                            rleData.write((hi shl 4) or (lo and 0x0F))
                            j += 2
                        }
                    }
                    if (bpp == 4 && (absCount and 1) != 0) {
                        rleData.write(0)
                    }
                }
            }
            rleData.write(0); rleData.write(0)
        }
        rleData.write(0); rleData.write(1)

        val rleBytes = rleData.toByteArray()
        val fileSize = dataOffset + rleBytes.size
        val out = ByteArrayOutputStream(fileSize)

        out.write('B'.code); out.write('M'.code)
        writeU32LE(out, fileSize)
        writeU16LE(out, 0); writeU16LE(out, 0)
        writeU32LE(out, dataOffset)

        writeU32LE(out, DIB_HEADER_SIZE)
        writeU32LE(out, w)
        writeU32LE(out, h)
        writeU16LE(out, 1)
        writeU16LE(out, bpp)
        writeU32LE(out, if (bpp == 8) 1 else 2)
        writeU32LE(out, rleBytes.size)
        writeU32LE(out, 2835)
        writeU32LE(out, 2835)
        writeU32LE(out, palette.size)
        writeU32LE(out, 0)

        for (c in palette) {
            out.write(c and 0xFF)
            out.write((c ushr 8) and 0xFF)
            out.write((c ushr 16) and 0xFF)
            out.write(0)
        }

        out.write(rleBytes)
        return out.toByteArray()
    }
}
