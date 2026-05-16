package org.skia.foundation


import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase R2.11 — covers the four byte-buffer-aware accessors on
 * [SkBitmap] : [SkBitmap.installPixels] (both overloads),
 * [SkBitmap.extractSubset], [SkBitmap.extractAlpha], and
 * [SkBitmap.peekPixels].
 */
class SkBitmapPixelAccessTest {

    // ─── installPixels ─────────────────────────────────────────────

    @Test
    fun `installPixels round-trips 8888 buffer into typed array`() {
        val w = 3
        val h = 2
        val rowBytes = w * 4
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        // Lay out a deterministic gradient — pixel (x, y) has byte
        // pattern (R=x*30, G=y*60, B=x*40, A=255). Buffer order for
        // RGBA_8888 is R, G, B, A.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = y * rowBytes + x * 4
                buf.put(o, (x * 30).toByte())
                buf.put(o + 1, (y * 60).toByte())
                buf.put(o + 2, (x * 40).toByte())
                buf.put(o + 3, 0xFF.toByte())
            }
        }
        val bm = SkBitmap(w, h)
        val info = SkImageInfo.Make(w, h)
        assertTrue(bm.installPixels(info, buf, rowBytes))
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = bm.getPixel(x, y)
                assertEquals(0xFF, SkColorGetA(c))
                assertEquals(x * 30, SkColorGetR(c))
                assertEquals(y * 60, SkColorGetG(c))
                assertEquals(x * 40, SkColorGetB(c))
            }
        }
        // pixelRef must be wired up — buffer round-trips via peekPixels.
        assertNotNull(bm.pixelRef())
    }

    @Test
    fun `installPixels with size mismatch returns false`() {
        val bm = SkBitmap(4, 4)
        val info = SkImageInfo.Make(3, 4) // width mismatch
        val buf = ByteBuffer.allocate(3 * 4 * 4).order(ByteOrder.LITTLE_ENDIAN)
        assertFalse(bm.installPixels(info, buf, 3 * 4))
        assertNull(bm.pixelRef())
    }

    @Test
    fun `installPixels rejects rowBytes below minRowBytes`() {
        val bm = SkBitmap(4, 4)
        val info = SkImageInfo.Make(4, 4)
        val buf = ByteBuffer.allocate(4 * 4 * 4).order(ByteOrder.LITTLE_ENDIAN)
        assertFalse(bm.installPixels(info, buf, /* rowBytes = */ 8)) // 4 px × 4 bpp = 16 minimum
    }

    @Test
    fun `installPixels rejects undersized buffer`() {
        val bm = SkBitmap(4, 4)
        val info = SkImageInfo.Make(4, 4)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * 2).order(ByteOrder.LITTLE_ENDIAN) // 2 rows, not 4
        assertFalse(bm.installPixels(info, buf, rowBytes))
    }

    @Test
    fun `installPixels rejects colorType mismatch`() {
        val bm = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val info = SkImageInfo.Make(4, 4) // RGBA_8888
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        assertFalse(bm.installPixels(info, buf, 16))
    }

    @Test
    fun `installPixels Alpha8 round-trips single-byte pixels`() {
        val w = 4
        val h = 3
        val rowBytes = w
        val buf = ByteBuffer.allocate(rowBytes * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                buf.put(y * rowBytes + x, ((x + 1) * 30 + y * 5).toByte())
            }
        }
        val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val info = SkImageInfo.Make(w, h, SkColorType.kAlpha_8, SkAlphaType.kPremul)
        assertTrue(bm.installPixels(info, buf, rowBytes))
        for (y in 0 until h) {
            for (x in 0 until w) {
                val expected = ((x + 1) * 30 + y * 5).toByte().toInt() and 0xFF
                assertEquals(expected, bm.pixelsA8[y * w + x].toInt() and 0xFF)
            }
        }
    }

    // ─── installPixels(pixmap) overload ────────────────────────────

    @Test
    fun `installPixels(pixmap) delegates to three-arg overload`() {
        val w = 2
        val h = 2
        val rowBytes = w * 4
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        // Fill with red (R=255, G=0, B=0, A=255).
        for (i in 0 until w * h) {
            val o = i * 4
            buf.put(o, 0xFF.toByte())
            buf.put(o + 1, 0x00.toByte())
            buf.put(o + 2, 0x00.toByte())
            buf.put(o + 3, 0xFF.toByte())
        }
        val info = SkImageInfo.Make(w, h)
        val pixmap = SkPixmap(info, buf, rowBytes)

        val bm = SkBitmap(w, h)
        assertTrue(bm.installPixels(pixmap))
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(SK_ColorRED, bm.getPixel(x, y))
            }
        }
    }

    // ─── extractSubset ──────────────────────────────────────────────

    @Test
    fun `extractSubset copies subset pixels`() {
        val src = SkBitmap(4, 4)
        // Encode (x + y * 4) into the red channel for the easy spot check.
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                src.setPixel(x, y, SkColorSetARGB(0xFF, x + y * 4, 0, 0))
            }
        }
        val dst = SkBitmap(2, 2)
        val subset = SkIRect.MakeLTRB(1, 1, 3, 3)
        assertTrue(src.extractSubset(dst, subset))
        // dst(0, 0) = src(1, 1) = 5, dst(1, 0) = src(2, 1) = 6,
        // dst(0, 1) = src(1, 2) = 9, dst(1, 1) = src(2, 2) = 10.
        assertEquals(5, SkColorGetR(dst.getPixel(0, 0)))
        assertEquals(6, SkColorGetR(dst.getPixel(1, 0)))
        assertEquals(9, SkColorGetR(dst.getPixel(0, 1)))
        assertEquals(10, SkColorGetR(dst.getPixel(1, 1)))
    }

    @Test
    fun `extractSubset propagates SkPixelRef from source`() {
        val src = SkBitmap(4, 4)
        val info = SkImageInfo.Make(4, 4)
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) {
            val o = i * 4
            buf.put(o, 0x10.toByte())
            buf.put(o + 1, 0x20.toByte())
            buf.put(o + 2, 0x30.toByte())
            buf.put(o + 3, 0xFF.toByte())
        }
        assertTrue(src.installPixels(info, buf, 16))
        val srcRef = src.pixelRef()
        assertNotNull(srcRef)

        val dst = SkBitmap(2, 2)
        assertTrue(src.extractSubset(dst, SkIRect.MakeLTRB(1, 1, 3, 3)))
        assertSame(srcRef, dst.pixelRef(),
            "extractSubset must propagate the SkPixelRef so the subset and parent share gen-id")
    }

    @Test
    fun `extractSubset returns false when subset disjoint from bounds`() {
        val src = SkBitmap(4, 4)
        val dst = SkBitmap(2, 2)
        // Subset entirely to the right of src — empty intersection.
        assertFalse(src.extractSubset(dst, SkIRect.MakeLTRB(10, 10, 12, 12)))
    }

    @Test
    fun `extractSubset returns false when dst geometry mismatches`() {
        val src = SkBitmap(4, 4)
        val dst = SkBitmap(3, 3) // intersect would be 2×2, not 3×3
        assertFalse(src.extractSubset(dst, SkIRect.MakeLTRB(1, 1, 3, 3)))
    }

    @Test
    fun `extractSubset clamps to bounds when subset partially outside`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                src.setPixel(x, y, SkColorSetARGB(0xFF, x * 16, y * 16, 0))
            }
        }
        // Subset (2, 2)..(6, 6) clamps to (2, 2)..(4, 4) → 2×2.
        val dst = SkBitmap(2, 2)
        assertTrue(src.extractSubset(dst, SkIRect.MakeLTRB(2, 2, 6, 6)))
        assertEquals(2 * 16, SkColorGetR(dst.getPixel(0, 0)))
        assertEquals(2 * 16, SkColorGetG(dst.getPixel(0, 0)))
    }

    // ─── extractAlpha ───────────────────────────────────────────────

    @Test
    fun `extractAlpha copies the alpha channel`() {
        val src = SkBitmap(4, 3)
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                src.setPixel(x, y, SkColorSetARGB((x + 1) * 60, 0xAA, 0xBB, 0xCC))
            }
        }
        val dst = SkBitmap(4, 3, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val offset = SkIPoint(99, 99) // sentinel
        assertTrue(src.extractAlpha(dst, null, offset))
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                val a = dst.pixelsA8[y * 4 + x].toInt() and 0xFF
                assertEquals((x + 1) * 60 and 0xFF, a, "($x, $y)")
            }
        }
        assertEquals(0, offset.fX)
        assertEquals(0, offset.fY)
    }

    @Test
    fun `extractAlpha with maskFilter cropped to source bounds softens edges`() {
        // R-suivi.18 — extractAlpha now honours paint.maskFilter. Build a
        // small opaque square, run a Gaussian blur of sigma=1.0 (margin=3)
        // and verify the corner pixel has been attenuated relative to the
        // unfiltered alpha (which would be 255).
        val src = SkBitmap(4, 4)
        src.eraseColor(SK_ColorWHITE)
        val dst = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val paint = SkPaint().apply {
            maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 1f)
        }
        val offset = SkIPoint()
        assertTrue(src.extractAlpha(dst, paint, offset))
        // Margin for sigma=1 is ceil(3·1) = 3 → offset is (-3, -3).
        assertEquals(-3, offset.fX)
        assertEquals(-3, offset.fY)
        // Center pixel stays close to fully opaque (blur of a fully
        // opaque interior near the centre).
        val centre = dst.pixelsA8[1 * 4 + 1].toInt() and 0xFF
        assertTrue(centre > 200, "centre alpha should remain high, got $centre")
        // Corner pixel sits on the path's outer boundary and has been
        // attenuated by the blur kernel.
        val corner = dst.pixelsA8[0].toInt() and 0xFF
        assertTrue(corner < 255, "corner alpha should be < 255, got $corner")
    }

    @Test
    fun `extractAlpha with maskFilter into margin-expanded dst preserves halo`() {
        // When dst is sized to the margin-expanded bounds, the entire blur
        // halo lands inside dst (no cropping). Verify pixels outside the
        // source's source-coordinate bounds (i.e. inside the margin band)
        // carry non-zero coverage from the blur.
        val src = SkBitmap(2, 2)
        src.eraseColor(SK_ColorWHITE)
        val sigma = 1f
        val margin = kotlin.math.ceil(3.0 * sigma).toInt() // = 3
        val expW = 2 + 2 * margin
        val expH = 2 + 2 * margin
        val dst = SkBitmap(expW, expH, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        val paint = SkPaint().apply {
            maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, sigma)
        }
        val offset = SkIPoint()
        assertTrue(src.extractAlpha(dst, paint, offset))
        assertEquals(-margin, offset.fX)
        assertEquals(-margin, offset.fY)
        // The pixel one row above the source's top edge (inside the halo)
        // must carry some non-zero coverage from the blur.
        val haloAbove = dst.pixelsA8[(margin - 1) * expW + margin].toInt() and 0xFF
        assertTrue(haloAbove > 0, "halo-above pixel should be > 0, got $haloAbove")
    }

    @Test
    fun `extractAlpha returns false when dst dimensions mismatch`() {
        val src = SkBitmap(4, 4)
        val dst = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        assertFalse(src.extractAlpha(dst, null, null))
    }

    @Test
    fun `extractAlpha returns false when dst colorType is not Alpha8`() {
        val src = SkBitmap(2, 2)
        val dst = SkBitmap(2, 2) // RGBA_8888
        assertFalse(src.extractAlpha(dst, null, null))
    }

    // ─── peekPixels ─────────────────────────────────────────────────

    @Test
    fun `peekPixels fills out-param after installPixels`() {
        val w = 3
        val h = 2
        val rowBytes = w * 4
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until w * h) {
            val o = i * 4
            buf.put(o, 0x11.toByte())
            buf.put(o + 1, 0x22.toByte())
            buf.put(o + 2, 0x33.toByte())
            buf.put(o + 3, 0xFF.toByte())
        }
        val bm = SkBitmap(w, h)
        assertTrue(bm.installPixels(SkImageInfo.Make(w, h), buf, rowBytes))

        val pm = SkPixmap()
        assertTrue(bm.peekPixels(pm))
        assertEquals(w, pm.width())
        assertEquals(h, pm.height())
        assertEquals(SkColorType.kRGBA_8888, pm.colorType())
        assertEquals(rowBytes, pm.rowBytes())
        // Spot-check pixel via the pixmap's own getColor path.
        val c = pm.getColor(0, 0)
        assertEquals(0x11, SkColorGetR(c))
        assertEquals(0x22, SkColorGetG(c))
        assertEquals(0x33, SkColorGetB(c))
    }

    @Test
    fun `peekPixels returns false when no SkPixelRef is attached`() {
        val bm = SkBitmap(2, 2)
        val pm = SkPixmap()
        // Fresh SkBitmap with typed-array-only storage — no PixelRef.
        assertFalse(bm.peekPixels(pm))
    }
}
