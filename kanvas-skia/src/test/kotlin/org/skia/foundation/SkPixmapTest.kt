package org.skia.foundation



import org.graphiks.math.between
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase R2 — exercises [SkPixmap]'s low-level pixel-access API. Covers
 * construction, reset, `getColor`, `readPixels`, `erase`, and
 * `computeByteSize`. The pixmap is tested against a host-LE
 * `kRGBA_8888` buffer because that's the path most downstream consumers
 * follow.
 */
class SkPixmapTest {

    private fun allocBytes(info: SkImageInfo): ByteBuffer =
        ByteBuffer.allocate(info.minRowBytes() * info.height)
            .order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `empty pixmap reports zero dimensions and unknown colorType`() {
        val p = SkPixmap()
        assertEquals(0, p.width())
        assertEquals(0, p.height())
        assertEquals(SkColorType.kUnknown, p.colorType())
        assertEquals(SkAlphaType.kUnknown, p.alphaType())
        assertEquals(0, p.rowBytes())
        assertEquals(0L, p.computeByteSize())
        assertEquals(SkIRect.MakeWH(0, 0), p.bounds())
    }

    @Test
    fun `info-backed constructor populates accessors`() {
        val info = SkImageInfo.Make(4, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocBytes(info)
        val p = SkPixmap(info, buf, info.minRowBytes())
        assertEquals(4, p.width())
        assertEquals(3, p.height())
        assertEquals(SkColorType.kRGBA_8888, p.colorType())
        assertEquals(SkAlphaType.kUnpremul, p.alphaType())
        assertEquals(16, p.rowBytes(), "minRowBytes for 4×8888 must be 16")
    }

    @Test
    fun `reset clears info and addr`() {
        val info = SkImageInfo.Make(2, 2)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        assertEquals(2, p.width())
        p.reset()
        assertEquals(0, p.width())
        assertEquals(SkColorType.kUnknown, p.colorType())
        assertEquals(0, p.rowBytes())
    }

    @Test
    fun `reset to new info rebinds addr and rowBytes`() {
        val info = SkImageInfo.Make(4, 4)
        val p = SkPixmap()
        p.reset(info, allocBytes(info), info.minRowBytes())
        assertEquals(4, p.width())
        assertEquals(4, p.height())
        assertEquals(16, p.rowBytes())
    }

    @Test
    fun `computeByteSize matches Skia formula`() {
        // 5×3, 4 bpp, rowBytes=20 → (3-1)*20 + 5*4 = 60.
        val info = SkImageInfo.Make(5, 3)
        val rb = info.minRowBytes()
        val p = SkPixmap(info, allocBytes(info), rb)
        assertEquals(60L, p.computeByteSize())
    }

    @Test
    fun `erase fills RGBA_8888 pixmap with the requested colour and getColor reads it back`() {
        val info = SkImageInfo.Make(3, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        assertTrue(p.erase(0x80123456.toInt()))
        for (y in 0 until p.height()) {
            for (x in 0 until p.width()) {
                assertEquals(0x80123456.toInt(), p.getColor(x, y), "at ($x, $y)")
            }
        }
    }

    @Test
    fun `erase fails for unknown colorType`() {
        val p = SkPixmap()
        assertFalse(p.erase(SK_ColorRED))
    }

    @Test
    fun `erase with a disjoint subset returns false`() {
        val info = SkImageInfo.Make(4, 4)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        assertFalse(p.erase(SK_ColorRED, SkIRect.MakeLTRB(100, 100, 200, 200)))
    }

    @Test
    fun `erase with a subset only writes inside the subset`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        p.erase(SK_ColorBLUE)
        p.erase(SK_ColorRED, SkIRect.MakeLTRB(1, 1, 3, 3))
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val inside = x in 1..2 && y in 1..2
                val expected = if (inside) SK_ColorRED else SK_ColorBLUE
                assertEquals(expected, p.getColor(x, y), "at ($x, $y)")
            }
        }
    }

    @Test
    fun `getAlphaf returns alpha normalized to zero-one range`() {
        val info = SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        p.erase(0xCC123456.toInt())
        assertEquals(0xCC / 255f, p.getAlphaf(0, 0), 1e-6f)
    }

    @Test
    fun `readPixels copies between same-type pixmaps`() {
        val info = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = SkPixmap(info, allocBytes(info), info.minRowBytes())
        src.erase(0x40112233.toInt(), SkIRect.MakeLTRB(0, 0, 3, 3))
        val dst = SkPixmap(info, allocBytes(info), info.minRowBytes())
        assertTrue(src.readPixels(dst))
        for (y in 0 until 3) {
            for (x in 0 until 3) {
                assertEquals(0x40112233.toInt(), dst.getColor(x, y))
            }
        }
    }

    @Test
    fun `readPixels with offset copies a subregion`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = SkPixmap(info, allocBytes(info), info.minRowBytes())
        // 0 outside, RED inside [1,3)x[1,3)
        src.erase(0)
        src.erase(SK_ColorRED, SkIRect.MakeLTRB(1, 1, 3, 3))
        val dstInfo = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dst = SkPixmap(dstInfo, allocBytes(dstInfo), dstInfo.minRowBytes())
        assertTrue(src.readPixels(dst, srcX = 1, srcY = 1))
        for (y in 0 until 2) {
            for (x in 0 until 2) {
                assertEquals(SK_ColorRED, dst.getColor(x, y), "at ($x, $y)")
            }
        }
    }

    @Test
    fun `readPixels across colorTypes RGBA and BGRA round trips`() {
        val srcInfo = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = SkPixmap(srcInfo, allocBytes(srcInfo), srcInfo.minRowBytes())
        src.erase(0xFF334455.toInt())
        val dstInfo = SkImageInfo.Make(2, 2, SkColorType.kBGRA_8888, SkAlphaType.kUnpremul)
        val dst = SkPixmap(dstInfo, allocBytes(dstInfo), dstInfo.minRowBytes())
        assertTrue(src.readPixels(dst))
        for (y in 0 until 2) {
            for (x in 0 until 2) {
                assertEquals(0xFF334455.toInt(), dst.getColor(x, y))
            }
        }
    }

    @Test
    fun `extractSubset intersects with bounds`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        p.erase(0)
        p.erase(SK_ColorGREEN, SkIRect.MakeLTRB(1, 1, 3, 3))
        val sub = SkPixmap()
        assertTrue(p.extractSubset(sub, SkIRect.MakeLTRB(1, 1, 4, 4)))
        assertEquals(3, sub.width())
        assertEquals(3, sub.height())
        assertEquals(SK_ColorGREEN, sub.getColor(0, 0))
        assertEquals(SK_ColorGREEN, sub.getColor(1, 1))
        assertEquals(0, sub.getColor(2, 2))
    }

    @Test
    fun `extractSubset disjoint returns false`() {
        val info = SkImageInfo.Make(4, 4)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        val sub = SkPixmap()
        assertFalse(p.extractSubset(sub, SkIRect.MakeLTRB(10, 10, 20, 20)))
    }

    @Test
    fun `scalePixels nearest fallback doubles a 1x1 source to 2x2`() {
        val srcInfo = SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = SkPixmap(srcInfo, allocBytes(srcInfo), srcInfo.minRowBytes())
        src.erase(SK_ColorCYAN)
        val dstInfo = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dst = SkPixmap(dstInfo, allocBytes(dstInfo), dstInfo.minRowBytes())
        assertTrue(src.scalePixels(dst, SkSamplingOptions.Default))
        for (y in 0 until 2) {
            for (x in 0 until 2) {
                assertEquals(SK_ColorCYAN, dst.getColor(x, y))
            }
        }
    }

    @Test
    fun `colorSpace mirrors info colorSpace`() {
        val info = SkImageInfo.Make(1, 1)
        val p = SkPixmap(info, allocBytes(info), info.minRowBytes())
        assertNotEquals(null as SkColorSpace?, p.colorSpace())
        val empty = SkPixmap()
        // info on empty pixmap defaults to sRGB-backed unknown — guard
        // only that the call doesn't NPE.
        empty.colorSpace()
    }

    @Test
    fun `rowBytes too small throws`() {
        val info = SkImageInfo.Make(4, 1)
        val bad = info.minRowBytes() - 1
        try {
            SkPixmap(info, allocBytes(info), bad)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `addr returns a buffer positioned at zero`() {
        val info = SkImageInfo.Make(2, 2)
        val buf = allocBytes(info)
        val p = SkPixmap(info, buf, info.minRowBytes())
        assertEquals(0, p.addr().position())
        // Mutating the duplicated buffer's cursor must not leak.
        p.addr().position(4)
        assertEquals(0, p.addr().position())
    }
}
