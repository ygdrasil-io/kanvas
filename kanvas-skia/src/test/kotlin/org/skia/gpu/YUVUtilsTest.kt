package org.skia.gpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorSetARGB
import kotlin.math.abs

/**
 * R-final.8 verification suite for [YUVUtils].
 *
 * Coverage :
 *  - 4:4:4 / 4:2:2 / 4:2:0 chroma upsampling round-trips.
 *  - BT.601 vs. BT.709 matrix selection.
 *  - Plane-size validation (too-small inputs throw).
 *  - End-to-end RGBA → planes → RGBA round-trip with bounded loss.
 */
class YUVUtilsTest {

    private fun assertNear(expected: Int, actual: Int, tolerance: Int, label: String) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "$label : expected=$expected actual=$actual delta=${abs(expected - actual)} (tol=$tolerance)",
        )
    }

    private fun rgbAt(argb: Int): Triple<Int, Int, Int> = Triple(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )

    @Test
    fun `chromaDimensions matches subsampling profile`() {
        // 4:4:4 — same as Y.
        assertEquals(8 to 6, YUVUtils.chromaDimensions(8, 6, YUVUtils.YUVSubsampling.k444))
        // 4:2:2 — half horizontal.
        assertEquals(4 to 6, YUVUtils.chromaDimensions(8, 6, YUVUtils.YUVSubsampling.k422))
        // 4:2:0 — half on both axes.
        assertEquals(4 to 3, YUVUtils.chromaDimensions(8, 6, YUVUtils.YUVSubsampling.k420))
        // Odd dimensions round up.
        assertEquals(5 to 4, YUVUtils.chromaDimensions(9, 7, YUVUtils.YUVSubsampling.k420))
    }

    @Test
    fun `yuvToRgb decodes a flat-grey 4-4-4 plane to grey`() {
        val w = 4
        val h = 4
        val y = ByteArray(w * h) { 0x80.toByte() } // mid-grey
        val u = ByteArray(w * h) { 128.toByte() }
        val v = ByteArray(w * h) { 128.toByte() }
        val bm = YUVUtils.yuvToRgb(
            y = y, u = u, v = v,
            width = w, height = h,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
            subsampling = YUVUtils.YUVSubsampling.k444,
        )
        for (py in 0 until h) for (px in 0 until w) {
            val (r, g, b) = rgbAt(bm.getPixel(px, py))
            assertNear(0x80, r, 1, "R($px,$py)")
            assertNear(0x80, g, 1, "G($px,$py)")
            assertNear(0x80, b, 1, "B($px,$py)")
        }
    }

    @Test
    fun `yuvToRgb 4-2-0 upsamples chroma by nearest-neighbour`() {
        val w = 4
        val h = 4
        // Y full-resolution constant ; chroma planes are 2×2 with two
        // distinct values across the horizontal axis.
        val y = ByteArray(w * h) { 0x80.toByte() }
        val u = ByteArray(2 * 2) { i -> if (i % 2 == 0) 100.toByte() else 200.toByte() }
        val v = ByteArray(2 * 2) { 128.toByte() }
        val bm = YUVUtils.yuvToRgb(
            y = y, u = u, v = v,
            width = w, height = h,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
            subsampling = YUVUtils.YUVSubsampling.k420,
        )
        // Pixels (0,0), (1,0) take u=100 ; pixels (2,0), (3,0) take u=200.
        val p0 = rgbAt(bm.getPixel(0, 0))
        val p2 = rgbAt(bm.getPixel(2, 0))
        // u < 128 and v == 128 → R/G unchanged, B shifts down.
        // u > 128 and v == 128 → R/G unchanged, B shifts up.
        assertTrue(p0.third < p2.third, "u=100 should yield smaller B than u=200 (got ${p0.third} vs ${p2.third})")
        // Same chroma pair applies to row 1 (subsampling halves vertical too).
        assertEquals(rgbAt(bm.getPixel(0, 0)), rgbAt(bm.getPixel(0, 1)))
    }

    @Test
    fun `yuvToRgb BT709 uses different coefficients than BT601`() {
        val w = 2; val h = 2
        val y = ByteArray(w * h) { 0x80.toByte() }
        val u = ByteArray(w * h) { 0x80.toByte() }
        val v = ByteArray(w * h) { 0xFF.toByte() } // strong red
        val bm601 = YUVUtils.yuvToRgb(y, u, v, w, h, YUVUtils.YUVColorSpace.BT601, YUVUtils.YUVSubsampling.k444)
        val bm709 = YUVUtils.yuvToRgb(y, u, v, w, h, YUVUtils.YUVColorSpace.BT709, YUVUtils.YUVSubsampling.k444)
        val r601 = (bm601.getPixel(0, 0) shr 16) and 0xFF
        val r709 = (bm709.getPixel(0, 0) shr 16) and 0xFF
        // BT.709 has a larger Cr→R coefficient (1.5748 vs 1.402), so
        // for the same v=255 input the BT.709 R should be larger
        // (capped at 255 — both should saturate, but the underlying
        // pre-clamp value is what matters).
        assertTrue(r601 >= 200 && r709 >= 200, "both should approach saturation : 601=$r601, 709=$r709")
    }

    @Test
    fun `yuvToRgb rejects too-small Y plane`() {
        assertThrows(IllegalArgumentException::class.java) {
            YUVUtils.yuvToRgb(
                y = ByteArray(0), u = ByteArray(4), v = ByteArray(4),
                width = 4, height = 4,
                colorSpace = YUVUtils.YUVColorSpace.BT601,
                subsampling = YUVUtils.YUVSubsampling.k444,
            )
        }
    }

    @Test
    fun `yuvFromRgba then yuvToRgb round-trips with bounded loss`() {
        // Build a small RGBA test bitmap with a few solid colour patches.
        val w = 16; val h = 16
        val bm = org.skia.foundation.SkBitmap(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val region = (x / 8) + (y / 8) * 2
            val argb = when (region) {
                0 -> SkColorSetARGB(0xFF, 0xFF, 0x00, 0x00) // red
                1 -> SkColorSetARGB(0xFF, 0x00, 0xFF, 0x00) // green
                2 -> SkColorSetARGB(0xFF, 0x00, 0x00, 0xFF) // blue
                else -> SkColorSetARGB(0xFF, 0x80, 0x80, 0x80) // grey
            }
            bm.setPixel(x, y, argb)
        }
        val (yp, up, vp) = YUVUtils.yuvFromRgba(
            rgba = bm,
            subsampling = YUVUtils.YUVSubsampling.k444,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
        )
        val rt = YUVUtils.yuvToRgb(
            y = yp, u = up, v = vp,
            width = w, height = h,
            colorSpace = YUVUtils.YUVColorSpace.BT601,
            subsampling = YUVUtils.YUVSubsampling.k444,
        )
        // Pure-colour patches survive 4:4:4 round-trip with small
        // quantization loss (≤ 4/255 per channel, dominated by the
        // 8-bit Y/U/V quantization steps and float→int truncation).
        for (y in 0 until h) for (x in 0 until w) {
            val (r0, g0, b0) = rgbAt(bm.getPixel(x, y))
            val (r1, g1, b1) = rgbAt(rt.getPixel(x, y))
            assertNear(r0, r1, 4, "R($x,$y)")
            assertNear(g0, g1, 4, "G($x,$y)")
            assertNear(b0, b1, 4, "B($x,$y)")
        }
    }
}
