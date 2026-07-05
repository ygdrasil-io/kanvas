package org.skia.foundation

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkISize

public enum class SkTextureCompressionType {
    kNone,
    kETC2_RGB8_UNORM,
    kBC1_RGB8_UNORM,
    kBC1_RGBA8_UNORM,
}

public val SkTextureCompressionType.kETC1_RGB8_UNORM: SkTextureCompressionType
    get() = SkTextureCompressionType.kETC2_RGB8_UNORM

public object SkCompressedDataUtils {
    public fun SkCompressedDataSize(
        compression: SkTextureCompressionType,
        dimensions: SkISize,
        mipMapOffsetsAndSizes: IntArray? = null,
        mipMapped: Boolean = false,
    ): Long {
        if (dimensions.width <= 0 || dimensions.height <= 0) return 0L
        val blockBytes = when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
            SkTextureCompressionType.kETC2_RGB8_UNORM,
                -> 8
            SkTextureCompressionType.kNone -> return 0L
        }

        var total = 0L
        var level = 0
        var w = dimensions.width
        var h = dimensions.height
        while (true) {
            val bw = (w + 3) / 4
            val bh = (h + 3) / 4
            val levelSize = bw.toLong() * bh.toLong() * blockBytes.toLong()
            if (mipMapOffsetsAndSizes != null && level * 2 + 1 < mipMapOffsetsAndSizes.size) {
                mipMapOffsetsAndSizes[level * 2] = total.toInt()
                mipMapOffsetsAndSizes[level * 2 + 1] = levelSize.toInt()
            }
            total += levelSize
            if (!mipMapped || (w == 1 && h == 1)) break
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
            level++
        }
        return total
    }

    public fun SkCompressedBlockWidth(compression: SkTextureCompressionType): Int =
        when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
            SkTextureCompressionType.kETC2_RGB8_UNORM,
                -> 4
            SkTextureCompressionType.kNone -> 0
        }

    public fun SkCompressedBlockHeight(compression: SkTextureCompressionType): Int =
        SkCompressedBlockWidth(compression)

    public fun Etc1EncodeImage(
        srcBitmap: SkBitmap,
        dst: ByteArray,
        dstOffset: Int,
    ) {
        require(dstOffset >= 0 && dstOffset < dst.size) { "dstOffset out of range: $dstOffset" }
        val blockWidth = (srcBitmap.width + 3) / 4
        val blockHeight = (srcBitmap.height + 3) / 4
        val needed = blockWidth * blockHeight * 8
        require(dstOffset + needed <= dst.size) {
            "destination too small for ETC1 payload: need=${dstOffset + needed}, have=${dst.size}"
        }
        var write = dstOffset
        for (by in 0 until blockHeight) {
            for (bx in 0 until blockWidth) {
                encodeEtc1Block(srcBitmap, bx, by, dst, write)
                write += 8
            }
        }
    }

    public fun TwoColorBC1Compress(
        srcBitmap: SkBitmap,
        otherColor: Int,
        dst: ByteArray,
        dstOffset: Int,
    ) {
        require(dstOffset >= 0 && dstOffset < dst.size) { "dstOffset out of range: $dstOffset" }
        val blockWidth = (srcBitmap.width + 3) / 4
        val blockHeight = (srcBitmap.height + 3) / 4
        val needed = blockWidth * blockHeight * 8
        require(dstOffset + needed <= dst.size) {
            "destination too small for BC1 payload: need=${dstOffset + needed}, have=${dst.size}"
        }

        val other565 = to565(otherColor)
        val black565 = 0
        var write = dstOffset
        for (by in 0 until blockHeight) {
            for (bx in 0 until blockWidth) {
                var hasTransparent = false
                var indices = 0
                var bitPos = 0
                for (ly in 0 until 4) {
                    for (lx in 0 until 4) {
                        val x = bx * 4 + lx
                        val y = by * 4 + ly
                        val c = if (x < srcBitmap.width && y < srcBitmap.height) srcBitmap.getPixel(x, y) else 0
                        val idx = if (SkColorGetA(c) < 128) {
                            hasTransparent = true
                            3
                        } else if (approxSameRgb(c, otherColor)) {
                            1
                        } else {
                            0
                        }
                        indices = indices or (idx shl bitPos)
                        bitPos += 2
                    }
                }

                val c0: Int
                val c1: Int
                if (hasTransparent) {
                    c0 = minOf(black565, other565)
                    c1 = maxOf(black565, other565)
                } else {
                    c0 = maxOf(black565, other565)
                    c1 = minOf(black565, other565)
                }
                put16(dst, write, c0)
                put16(dst, write + 2, c1)
                put32(dst, write + 4, indices)
                write += 8
            }
        }
    }

    private fun encodeEtc1Block(src: SkBitmap, bx: Int, by: Int, dst: ByteArray, off: Int) {
        var srSum = 0L
        var sgSum = 0L
        var sbSum = 0L
        var srSum2 = 0L
        var sgSum2 = 0L
        var sbSum2 = 0L
        var count1 = 0
        var count2 = 0
        for (ly in 0 until 4) {
            for (lx in 0 until 4) {
                val x = bx * 4 + lx
                val y = by * 4 + ly
                val c = if (x < src.width && y < src.height) src.getPixel(x, y) else 0
                val r = SkColorGetR(c)
                val g = SkColorGetG(c)
                val b = SkColorGetB(c)
                if (ly < 2) {
                    srSum += r
                    sgSum += g
                    sbSum += b
                    count1++
                } else {
                    srSum2 += r
                    sgSum2 += g
                    sbSum2 += b
                    count2++
                }
            }
        }
        if (count1 == 0) count1 = 1
        if (count2 == 0) count2 = 1
        val avgR1 = (srSum / count1).toInt()
        val avgG1 = (sgSum / count1).toInt()
        val avgB1 = (sbSum / count1).toInt()
        val avgR2 = (srSum2 / count2).toInt()
        val avgG2 = (sgSum2 / count2).toInt()
        val avgB2 = (sbSum2 / count2).toInt()

        val r1 = (avgR1 * 31 + 127) / 255
        val g1 = (avgG1 * 31 + 127) / 255
        val b1 = (avgB1 * 31 + 127) / 255
        val r2 = (avgR2 * 31 + 127) / 255
        val g2 = (avgG2 * 31 + 127) / 255
        val b2 = (avgB2 * 31 + 127) / 255
        val dr = (r2 - r1).coerceIn(-4, 3)
        val dg = (g2 - g1).coerceIn(-4, 3)
        val db = (b2 - b1).coerceIn(-4, 3)

        val hi = (1 shl 30) or
            (r1 shl 25) or
            (g1 shl 20) or
            (b1 shl 15) or
            ((dr and 0x7) shl 12) or
            ((dg and 0x7) shl 9) or
            ((db and 0x7) shl 6)
        put32BE(dst, off, hi)
        put32BE(dst, off + 4, 0)
    }

    private fun approxSameRgb(a: Int, b: Int): Boolean {
        val dr = kotlin.math.abs(SkColorGetR(a) - SkColorGetR(b))
        val dg = kotlin.math.abs(SkColorGetG(a) - SkColorGetG(b))
        val db = kotlin.math.abs(SkColorGetB(a) - SkColorGetB(b))
        return dr <= 2 && dg <= 2 && db <= 2
    }

    private fun to565(c: Int): Int {
        val r = (SkColorGetR(c) * 31 + 127) / 255
        val g = (SkColorGetG(c) * 63 + 127) / 255
        val b = (SkColorGetB(c) * 31 + 127) / 255
        return (r shl 11) or (g shl 5) or b
    }

    private fun put16(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun put32(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v ushr 8) and 0xFF).toByte()
        dst[off + 2] = ((v ushr 16) and 0xFF).toByte()
        dst[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun put32BE(dst: ByteArray, off: Int, v: Int) {
        dst[off] = ((v ushr 24) and 0xFF).toByte()
        dst[off + 1] = ((v ushr 16) and 0xFF).toByte()
        dst[off + 2] = ((v ushr 8) and 0xFF).toByte()
        dst[off + 3] = (v and 0xFF).toByte()
    }
}
