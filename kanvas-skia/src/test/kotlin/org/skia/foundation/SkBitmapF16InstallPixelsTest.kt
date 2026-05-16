package org.skia.foundation


import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * R-suivi.17 — verifies the F16 (half-float) branch of
 * [SkBitmap.installPixels]. Pre-R-suivi.17 this branch returned `false`
 * and rejected the ingress ; now the buffer is decoded as 4×binary16
 * channels per pixel (R, G, B, A premul) and stored in the bitmap's
 * float-32 backing array.
 *
 * The tests also cover the [SkBitmap.halfToFloat] / [SkBitmap.floatToHalf]
 * primitives — bit-exact for normals, lossy-but-bounded for subnormals
 * and across the round-trip boundary.
 */
class SkBitmapF16InstallPixelsTest {

    private fun packHalf(buf: ByteBuffer, offset: Int, r: Float, g: Float, b: Float, a: Float) {
        // Premultiply the channels before encoding — F16 default alpha
        // type in Skia is `kPremul`, so the buffer carries premul data.
        val pr = SkBitmap.floatToHalf(r * a)
        val pg = SkBitmap.floatToHalf(g * a)
        val pb = SkBitmap.floatToHalf(b * a)
        val pa = SkBitmap.floatToHalf(a)
        buf.putShort(offset, pr)
        buf.putShort(offset + 2, pg)
        buf.putShort(offset + 4, pb)
        buf.putShort(offset + 6, pa)
    }

    @Test
    fun `installPixels decodes a single F16 pixel and getPixel returns expected ARGB`() {
        // Single pixel, opaque red (1.0, 0.0, 0.0, 1.0).
        val w = 1; val h = 1
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_F16Norm)
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_F16Norm)
        val rowBytes = info.minRowBytes() // 8
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        packHalf(buf, 0, r = 1f, g = 0f, b = 0f, a = 1f)

        assertTrue(bm.installPixels(info, buf, rowBytes), "F16 installPixels should succeed")

        val color = bm.getPixel(0, 0)
        // Half-float of 1.0 round-trips bit-exact ; alpha should be 255.
        assertEquals(0xFF, SkColorGetA(color), "alpha should be opaque")
        assertEquals(0xFF, SkColorGetR(color), "red should be saturated")
        assertEquals(0x00, SkColorGetG(color), "green should be zero")
        assertEquals(0x00, SkColorGetB(color), "blue should be zero")
    }

    @Test
    fun `installPixels decodes a 2x2 F16 buffer with mixed colours`() {
        val w = 2; val h = 2
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_F16Norm)
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_F16Norm)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        // (0,0) red ; (1,0) green ; (0,1) blue ; (1,1) opaque white.
        packHalf(buf, 0, r = 1f, g = 0f, b = 0f, a = 1f)
        packHalf(buf, 8, r = 0f, g = 1f, b = 0f, a = 1f)
        packHalf(buf, rowBytes, r = 0f, g = 0f, b = 1f, a = 1f)
        packHalf(buf, rowBytes + 8, r = 1f, g = 1f, b = 1f, a = 1f)

        assertTrue(bm.installPixels(info, buf, rowBytes))

        // ARGB hex constants — packed `0xAARRGGBB` Int.
        val red = bm.getPixel(0, 0)
        val green = bm.getPixel(1, 0)
        val blue = bm.getPixel(0, 1)
        val white = bm.getPixel(1, 1)
        assertEquals(SkColorSetARGB(0xFF, 255, 0, 0), red, "(0,0) red")
        assertEquals(SkColorSetARGB(0xFF, 0, 255, 0), green, "(1,0) green")
        assertEquals(SkColorSetARGB(0xFF, 0, 0, 255), blue, "(0,1) blue")
        assertEquals(SkColorSetARGB(0xFF, 255, 255, 255), white, "(1,1) white")
    }

    @Test
    fun `installPixels honours rowBytes padding for F16`() {
        val w = 2; val h = 2
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_F16Norm)
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_F16Norm)
        // 8 bytes per pixel × 2 pixels = 16 min ; pad to 24 bytes/row.
        val rowBytes = 24
        val buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.LITTLE_ENDIAN)
        // Row 0 : red, green ; row 1 : padding-only colours.
        packHalf(buf, 0, r = 1f, g = 0f, b = 0f, a = 1f)
        packHalf(buf, 8, r = 0f, g = 1f, b = 0f, a = 1f)
        // Padding bytes 16..23 in row 0 are zero (untouched).
        packHalf(buf, rowBytes + 0, r = 0f, g = 0f, b = 1f, a = 1f)
        packHalf(buf, rowBytes + 8, r = 1f, g = 1f, b = 0f, a = 1f)

        assertTrue(bm.installPixels(info, buf, rowBytes))

        assertEquals(SkColorSetARGB(0xFF, 255, 0, 0), bm.getPixel(0, 0))
        assertEquals(SkColorSetARGB(0xFF, 0, 255, 0), bm.getPixel(1, 0))
        assertEquals(SkColorSetARGB(0xFF, 0, 0, 255), bm.getPixel(0, 1))
        assertEquals(SkColorSetARGB(0xFF, 255, 255, 0), bm.getPixel(1, 1))
    }

    @Test
    fun `installPixels with rowBytes below minRowBytes is rejected for F16`() {
        val w = 4; val h = 4
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_F16Norm)
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_F16Norm)
        // 4 px × 8 bpp = 32 minimum ; pass 16.
        val buf = ByteBuffer.allocate(16 * h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(false, bm.installPixels(info, buf, /* rowBytes = */ 16))
    }

    @Test
    fun `halfToFloat round-trips bit-exact for a sampling of normals`() {
        val samples = floatArrayOf(0f, 1f, -1f, 0.5f, -0.5f, 2f, 65504f, -65504f, 0.25f, 0.75f)
        for (f in samples) {
            val h = SkBitmap.floatToHalf(f)
            val back = SkBitmap.halfToFloat(h)
            assertEquals(f, back, 1e-3f, "half round-trip drift for $f -> $h -> $back")
        }
    }

    @Test
    fun `floatToHalf saturates overflow to infinity`() {
        // 1e6 exceeds half's 65504 max — saturates to +inf.
        val h = SkBitmap.floatToHalf(1e6f)
        val back = SkBitmap.halfToFloat(h)
        assertTrue(back.isInfinite(), "1e6 should saturate to +inf, got $back")
        assertTrue(back > 0, "saturated value should be positive")
    }

    @Test
    fun `halfToFloat preserves NaN`() {
        val nanHalf: Short = 0x7E00.toShort() // qNaN bit pattern
        val back = SkBitmap.halfToFloat(nanHalf)
        assertTrue(back.isNaN(), "NaN half should decode to NaN float, got $back")
    }

    @Test
    fun `installPixels F16 stores premul values into pixelsF16 backing array`() {
        // 50%-alpha red — buffer carries premul (0.5, 0, 0, 0.5).
        val w = 1; val h = 1
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_F16Norm)
        val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_F16Norm)
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        packHalf(buf, 0, r = 1f, g = 0f, b = 0f, a = 0.5f)

        assertTrue(bm.installPixels(info, buf, 8))

        // Backing array stores premul floats. Half-float of 0.5 is not
        // bit-exact (1/2 has a finite binary representation so it is, in
        // fact, exact) — but allow tiny tolerance.
        assertTrue(abs(bm.pixelsF16[0] - 0.5f) < 1e-3f, "premul R = ${bm.pixelsF16[0]}")
        assertEquals(0f, bm.pixelsF16[1], 1e-3f, "premul G")
        assertEquals(0f, bm.pixelsF16[2], 1e-3f, "premul B")
        assertTrue(abs(bm.pixelsF16[3] - 0.5f) < 1e-3f, "alpha = ${bm.pixelsF16[3]}")
    }

    @Test
    fun `installPixels F16 different from RGBA8888 (sanity)`() {
        // Two bitmaps of identical pixel shape, one 8888 / one F16. Their
        // raw storage arrays must differ.
        val bm8 = SkBitmap(2, 2, colorType = SkColorType.kRGBA_8888)
        val bmF = SkBitmap(2, 2, colorType = SkColorType.kRGBA_F16Norm)
        assertNotEquals(bm8.colorType, bmF.colorType)
        assertEquals(0, bmF.pixels8888.size, "F16 bitmap has empty 8888 store")
        assertEquals(16, bmF.pixelsF16.size, "F16 bitmap has 4 floats × 4 pixels")
    }
}
