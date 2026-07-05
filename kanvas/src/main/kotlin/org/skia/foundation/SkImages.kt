package org.skia.foundation

import org.graphiks.math.SkColorSetARGB

public object SkImages {
    public fun RasterFromCompressedTextureData(
        data: ByteArray,
        width: Int,
        height: Int,
        compressionType: SkTextureCompressionType,
    ): SkImage? = RasterFromCompressedTextureData(
        SkData.MakeWithCopy(data),
        width,
        height,
        compressionType,
    )

    public fun RasterFromCompressedTextureData(
        data: SkData,
        width: Int,
        height: Int,
        compression: SkTextureCompressionType,
    ): SkImage? {
        if (width <= 0 || height <= 0) return null
        return when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM -> decodeBC1(data, width, height, honorAlpha = false)
            SkTextureCompressionType.kBC1_RGBA8_UNORM -> decodeBC1(data, width, height, honorAlpha = true)
            SkTextureCompressionType.kETC2_RGB8_UNORM -> decodeETC2(data, width, height)
            SkTextureCompressionType.kNone -> null
        }
    }

    private fun decodeBC1(data: SkData, width: Int, height: Int, honorAlpha: Boolean): SkImage? {
        val blockW = (width + 3) / 4
        val blockH = (height + 3) / 4
        val needed = blockW * blockH * 8
        if (data.size < needed) return null
        val src = data.toByteArray()
        val out = IntArray(width * height)
        var off = 0
        for (by in 0 until blockH) {
            for (bx in 0 until blockW) {
                val c0 = u16(src, off)
                val c1 = u16(src, off + 2)
                var bits = u32(src, off + 4)
                val pal = bc1Palette(c0, c1, honorAlpha)
                for (ly in 0 until 4) {
                    val py = by * 4 + ly
                    if (py >= height) {
                        bits = bits ushr 8
                        continue
                    }
                    for (lx in 0 until 4) {
                        val px = bx * 4 + lx
                        val idx = bits and 0x3
                        bits = bits ushr 2
                        if (px < width) out[py * width + px] = pal[idx]
                    }
                }
                off += 8
            }
        }
        return SkImage(width, height, out, SkColorType.kRGBA_8888)
    }

    private fun bc1Palette(c0: Int, c1: Int, honorAlpha: Boolean): IntArray {
        val p0 = rgb565ToColor(c0)
        val p1 = rgb565ToColor(c1)
        val r0 = (p0 ushr 16) and 0xFF
        val g0 = (p0 ushr 8) and 0xFF
        val b0 = p0 and 0xFF
        val r1 = (p1 ushr 16) and 0xFF
        val g1 = (p1 ushr 8) and 0xFF
        val b1 = p1 and 0xFF
        val p2: Int
        val p3: Int
        if (c0 > c1) {
            p2 = SkColorSetARGB(255, (2 * r0 + r1) / 3, (2 * g0 + g1) / 3, (2 * b0 + b1) / 3)
            p3 = SkColorSetARGB(255, (r0 + 2 * r1) / 3, (g0 + 2 * g1) / 3, (b0 + 2 * b1) / 3)
        } else {
            p2 = SkColorSetARGB(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2)
            p3 = if (honorAlpha) 0 else SkColorSetARGB(255, 0, 0, 0)
        }
        return intArrayOf(p0, p1, p2, p3)
    }

    private fun rgb565ToColor(v: Int): Int {
        val r5 = (v ushr 11) and 0x1F
        val g6 = (v ushr 5) and 0x3F
        val b5 = v and 0x1F
        val r = (r5 shl 3) or (r5 ushr 2)
        val g = (g6 shl 2) or (g6 ushr 4)
        val b = (b5 shl 3) or (b5 ushr 2)
        return SkColorSetARGB(255, r, g, b)
    }

    private fun decodeETC2(data: SkData, width: Int, height: Int): SkImage? {
        val blockW = (width + 3) / 4
        val blockH = (height + 3) / 4
        val needed = blockW * blockH * 8
        if (data.size < needed) return null
        val src = data.toByteArray()
        val out = IntArray(width * height)
        var off = 0
        for (by in 0 until blockH) {
            for (bx in 0 until blockW) {
                etc2DecodeBlock(src, off, width, height, bx, by, out)
                off += 8
            }
        }
        return SkImage(width, height, out, SkColorType.kRGBA_8888)
    }

    private fun etc2DecodeBlock(src: ByteArray, off: Int, w: Int, h: Int, bx: Int, by: Int, out: IntArray) {
        val hi = ((src[off].toInt() and 0xFF) shl 24) or
            ((src[off + 1].toInt() and 0xFF) shl 16) or
            ((src[off + 2].toInt() and 0xFF) shl 8) or
            (src[off + 3].toInt() and 0xFF)
        val lo = ((src[off + 4].toInt() and 0xFF) shl 24) or
            ((src[off + 5].toInt() and 0xFF) shl 16) or
            ((src[off + 6].toInt() and 0xFF) shl 8) or
            (src[off + 7].toInt() and 0xFF)

        val flipped = (hi shr 31) and 1
        val diff = (hi shr 30) and 1
        val baseR1: Int
        val baseG1: Int
        val baseB1: Int
        val baseR2: Int
        val baseG2: Int
        val baseB2: Int
        val table1: Int
        val table2: Int

        if (diff == 0) {
            val r1 = (hi shr 15) and 0xF
            val g1 = (hi shr 10) and 0xF
            val b1 = (hi shr 5) and 0xF
            val r2 = hi and 0xF
            baseR1 = r1 * 17
            baseG1 = g1 * 17
            baseB1 = b1 * 17
            baseR2 = r2 * 17
            baseG2 = baseG1
            baseB2 = baseB1
            table1 = (hi shr 1) and 0x7
            table2 = (hi shr 4) and 0x7
        } else {
            val r1 = (hi shr 25) and 0x1F
            val g1 = (hi shr 20) and 0x1F
            val b1 = (hi shr 15) and 0x1F
            val dr = ((hi shr 12) and 0x7).let { if (it >= 4) it - 8 else it }
            val dg = ((hi shr 9) and 0x7).let { if (it >= 4) it - 8 else it }
            val db = ((hi shr 6) and 0x7).let { if (it >= 4) it - 8 else it }
            baseR1 = (r1 shl 3) or (r1 ushr 2)
            baseG1 = (g1 shl 3) or (g1 ushr 2)
            baseB1 = (b1 shl 3) or (b1 ushr 2)
            val r2 = (r1 + dr).coerceIn(0, 31)
            val g2 = (g1 + dg).coerceIn(0, 31)
            val b2 = (b1 + db).coerceIn(0, 31)
            baseR2 = (r2 shl 3) or (r2 ushr 2)
            baseG2 = (g2 shl 3) or (g2 ushr 2)
            baseB2 = (b2 shl 3) or (b2 ushr 2)
            table1 = (hi shr 3) and 0x7
            table2 = hi and 0x7
        }

        val pixelIndices = IntArray(16) { (lo ushr (it * 2)) and 3 }
        for (i in 0 until 16) {
            val lx: Int
            val ly: Int
            if (flipped == 0) {
                lx = i % 4
                ly = i / 4
            } else {
                lx = (i % 8) % 2 + (i / 8) * 2
                ly = (i % 8) / 2
            }
            val px = bx * 4 + lx
            val py = by * 4 + ly
            if (px >= w || py >= h) continue
            val subBlock = if (flipped == 0) {
                if (ly < 2) 0 else 1
            } else {
                if (lx < 2) 0 else 1
            }
            val table = if (subBlock == 0) table1 else table2
            val baseR = if (subBlock == 0) baseR1 else baseR2
            val baseG = if (subBlock == 0) baseG1 else baseG2
            val baseB = if (subBlock == 0) baseB1 else baseB2
            val mod = ETC2_MODIFIER_TABLES[table][pixelIndices[i]]
            out[py * w + px] = SkColorSetARGB(
                255,
                (baseR + mod).coerceIn(0, 255),
                (baseG + mod).coerceIn(0, 255),
                (baseB + mod).coerceIn(0, 255),
            )
        }
    }

    private val ETC2_MODIFIER_TABLES = arrayOf(
        intArrayOf(2, 8, -8, -2),
        intArrayOf(5, 17, -17, -5),
        intArrayOf(9, 29, -29, -9),
        intArrayOf(13, 42, -42, -13),
        intArrayOf(18, 60, -60, -18),
        intArrayOf(24, 80, -80, -24),
        intArrayOf(33, 106, -106, -33),
        intArrayOf(47, 183, -183, -47),
    )

    private fun u16(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or ((src[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or
            ((src[off + 1].toInt() and 0xFF) shl 8) or
            ((src[off + 2].toInt() and 0xFF) shl 16) or
            ((src[off + 3].toInt() and 0xFF) shl 24)
}
