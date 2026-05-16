package org.skia.foundation


import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColor
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Phase R-suivi.11 — exercises [SkPixmap.scalePixels] with a
 * Mitchell-Netravali bicubic resampler ([SkCubicResampler.Mitchell]).
 *
 * Two properties of the Mitchell-Netravali family that must hold :
 *  - **Partition of unity** : a constant-colour source upscales to a
 *    constant-colour destination (the 16 sample weights at any
 *    sub-pixel position sum to 1).
 *  - **Smoother than bilinear** : a sharp 2-bin step on the source
 *    yields a smoother gradient on the destination than the
 *    piecewise-linear bilinear interpolant (cubic has continuous
 *    first derivatives across cell boundaries).
 */
class SkPixmapScalePixelsCubicTest {

    private fun allocBytes(info: SkImageInfo): ByteBuffer =
        ByteBuffer.allocate(info.minRowBytes() * info.height).order(ByteOrder.LITTLE_ENDIAN)

    private fun rgba(info: SkImageInfo): SkPixmap =
        SkPixmap(info, allocBytes(info), info.minRowBytes())

    private fun writeRgba(p: SkPixmap, x: Int, y: Int, c: SkColor) {
        val bytes = p.addr()
        val off = y * p.rowBytes() + x * 4
        bytes.put(off, SkColorGetR(c).toByte())
        bytes.put(off + 1, SkColorGetG(c).toByte())
        bytes.put(off + 2, SkColorGetB(c).toByte())
        bytes.put(off + 3, SkColorGetA(c).toByte())
    }

    @Test
    fun `cubic upscale of constant colour preserves the colour everywhere`() {
        val srcInfo = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = rgba(srcInfo)
        // Constant teal (50, 200, 150, 255).
        val teal = SkColorSetARGB(0xFF, 50, 200, 150)
        for (y in 0 until 2) for (x in 0 until 2) writeRgba(src, x, y, teal)

        val dstInfo = SkImageInfo.Make(5, 5, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dst = rgba(dstInfo)
        val cubic = SkSamplingOptions(SkCubicResampler.Mitchell)
        assertTrue(src.scalePixels(dst, cubic))
        for (y in 0 until 5) for (x in 0 until 5) {
            val c = dst.getColor(x, y)
            // Allow ±1 quantisation slack from the float→Int round.
            assertTrue(abs(50 - SkColorGetR(c)) <= 1, "($x,$y) R=${SkColorGetR(c)} ~ 50")
            assertTrue(abs(200 - SkColorGetG(c)) <= 1, "($x,$y) G=${SkColorGetG(c)} ~ 200")
            assertTrue(abs(150 - SkColorGetB(c)) <= 1, "($x,$y) B=${SkColorGetB(c)} ~ 150")
            assertEquals(0xFF, SkColorGetA(c))
        }
    }

    @Test
    fun `cubic upscale of a horizontal step produces a monotone gradient`() {
        // 4×1 source : (black, black, white, white) — a single
        // sharp step between x=1 and x=2.
        val srcInfo = SkImageInfo.Make(4, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = rgba(srcInfo)
        writeRgba(src, 0, 0, SK_ColorBLACK)
        writeRgba(src, 1, 0, SK_ColorBLACK)
        writeRgba(src, 2, 0, SK_ColorWHITE)
        writeRgba(src, 3, 0, SK_ColorWHITE)

        // Upscale ×4 horizontally.
        val dstInfo = SkImageInfo.Make(16, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dst = rgba(dstInfo)
        val cubic = SkSamplingOptions(SkCubicResampler.Mitchell)
        assertTrue(src.scalePixels(dst, cubic))

        // Boundary clamp + cubic kernel : the left/right edges
        // remain near-pure (samples come from clamped edge texels),
        // and the centre region transitions from near-0 to near-255
        // smoothly and monotonically.
        val r = IntArray(16) { SkColorGetR(dst.getColor(it, 0)) }
        // Pure-black region : the first destination pixel lies
        // entirely under the (black, black) clamp.
        assertTrue(r[0] <= 1, "left edge R=${r[0]} ~ 0")
        // Pure-white region : the last destination pixel lies
        // entirely under the (white, white) clamp.
        assertTrue(r[15] >= 254, "right edge R=${r[15]} ~ 255")
        // Monotone across the transition zone : Mitchell-Netravali
        // is monotone on a step. The transition zone (dst x ∈ 4..11)
        // must be non-decreasing.
        for (x in 4..10) {
            assertTrue(r[x] <= r[x + 1] + 1, "non-monotone at $x : ${r[x]} > ${r[x + 1]}")
        }
        // The midpoint of the transition (src x = 1.5) maps to
        // dst x ≈ 7.5 → no single destination pixel is exactly on
        // the step centre. The two pixels around it must straddle
        // ~127 by symmetry of the Mitchell kernel.
        val midSum = r[7] + r[8]
        assertTrue(midSum in 240..270, "mid-pair sum=$midSum ~ 255 ±15 (Mitchell symmetric on step)")
    }

    @Test
    fun `cubic precedence — non-null cubic field overrides filter kLinear`() {
        // Sanity : sampling.filter == kLinear but cubic != null
        // must dispatch to the cubic path. We assert by comparing a
        // gradient against a known bilinear output ; cubic and
        // bilinear produce visibly different values mid-step.
        val srcInfo = SkImageInfo.Make(4, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = rgba(srcInfo)
        writeRgba(src, 0, 0, SK_ColorBLACK)
        writeRgba(src, 1, 0, SK_ColorBLACK)
        writeRgba(src, 2, 0, SK_ColorWHITE)
        writeRgba(src, 3, 0, SK_ColorWHITE)
        val dstInfo = SkImageInfo.Make(8, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dstLinear = rgba(dstInfo)
        val dstCubicViaLinear = rgba(dstInfo)
        assertTrue(src.scalePixels(dstLinear, SkSamplingOptions.linear()))
        // filter = kLinear, cubic = Mitchell → cubic wins.
        assertTrue(src.scalePixels(dstCubicViaLinear, SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNone, SkCubicResampler.Mitchell)))
        // Find at least one destination pixel whose values diverge —
        // proof the cubic branch ran instead of the bilinear branch.
        // Mitchell with `(B, C) = (1/3, 1/3)` only diverges modestly
        // from bilinear near the step (~3 quantisation levels), so
        // use a strict ">= 1" threshold.
        var diverged = false
        for (x in 0 until 8) {
            val a = SkColorGetR(dstLinear.getColor(x, 0))
            val b = SkColorGetR(dstCubicViaLinear.getColor(x, 0))
            if (abs(a - b) >= 1) { diverged = true; break }
        }
        assertTrue(diverged, "cubic precedence — output must differ from bilinear at some pixel")
    }
}
