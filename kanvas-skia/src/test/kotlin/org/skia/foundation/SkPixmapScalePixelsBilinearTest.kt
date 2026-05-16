package org.skia.foundation


import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Phase R-suivi.11 — exercises [SkPixmap.scalePixels] with
 * [SkSamplingOptions.linear] (4-tap bilinear blend) over a 2×2
 * checkerboard upscaled to 3×3. The four texel centres land at the
 * destination corners (no blending — both source neighbours clamp
 * to the edge texel); the dead-centre pixel samples at the corner
 * shared by all four source texels and must yield a 50% blend of
 * the two checker colours.
 */
class SkPixmapScalePixelsBilinearTest {

    private fun allocBytes(info: SkImageInfo): ByteBuffer =
        ByteBuffer.allocate(info.minRowBytes() * info.height).order(ByteOrder.LITTLE_ENDIAN)

    private fun rgba(info: SkImageInfo): SkPixmap =
        SkPixmap(info, allocBytes(info), info.minRowBytes())

    /** Direct kRGBA_8888 LE-host byte poke for test fixture setup. */
    private fun writeRgba(p: SkPixmap, x: Int, y: Int, c: SkColor) {
        val bytes = p.addr()
        val off = y * p.rowBytes() + x * 4
        bytes.put(off, SkColorGetR(c).toByte())
        bytes.put(off + 1, SkColorGetG(c).toByte())
        bytes.put(off + 2, SkColorGetB(c).toByte())
        bytes.put(off + 3, SkColorGetA(c).toByte())
    }

    private fun near(expected: Int, actual: Int, tol: Int): Boolean = abs(expected - actual) <= tol

    @Test
    fun `bilinear upscale of 2x2 checkerboard yields 50 percent blend at the centre`() {
        // Source : (black, white)
        //          (white, black) — a 2×2 checker.
        val srcInfo = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = rgba(srcInfo)
        writeRgba(src, 0, 0, SK_ColorBLACK)
        writeRgba(src, 1, 0, SK_ColorWHITE)
        writeRgba(src, 0, 1, SK_ColorWHITE)
        writeRgba(src, 1, 1, SK_ColorBLACK)
        assertEquals(SK_ColorBLACK, src.getColor(0, 0))
        assertEquals(SK_ColorWHITE, src.getColor(1, 0))

        // Destination 3×3. Texel-centre mapping :
        //   sx = ((dx + 0.5) * 2 / 3) - 0.5
        //   dx=0 → -0.167 (clamp ⇒ samples x=0 only)
        //   dx=1 →  0.5   (tx=0.5, blends x=0 and x=1)
        //   dx=2 →  1.167 (clamp ⇒ samples x=1 only)
        // The dead-centre pixel (1, 1) blends all four checker corners
        // with weights (0.25, 0.25, 0.25, 0.25) → mid-grey (128, ±1).
        val dstInfo = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val dst = rgba(dstInfo)
        assertTrue(src.scalePixels(dst, SkSamplingOptions.linear()))

        // Corners replicate the source — clamp samples to the edge.
        assertEquals(SK_ColorBLACK, dst.getColor(0, 0))
        assertEquals(SK_ColorWHITE, dst.getColor(2, 0))
        assertEquals(SK_ColorWHITE, dst.getColor(0, 2))
        assertEquals(SK_ColorBLACK, dst.getColor(2, 2))

        // Mid-edges are 50% blends along one axis only.
        val midTop = dst.getColor(1, 0)
        assertTrue(near(128, SkColorGetR(midTop), 2), "mid-top R=${SkColorGetR(midTop)} ~ 128")
        assertEquals(0xFF, SkColorGetA(midTop))

        // Dead centre is a 4-way 50/50 blend → mid-grey.
        val centre = dst.getColor(1, 1)
        assertTrue(near(128, SkColorGetR(centre), 2), "centre R=${SkColorGetR(centre)} ~ 128")
        assertTrue(near(128, SkColorGetG(centre), 2), "centre G=${SkColorGetG(centre)} ~ 128")
        assertTrue(near(128, SkColorGetB(centre), 2), "centre B=${SkColorGetB(centre)} ~ 128")
        assertEquals(0xFF, SkColorGetA(centre), "alpha must be opaque")
    }

    @Test
    fun `bilinear preserves identity for matching destination size`() {
        val info = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val src = rgba(info)
        src.erase(SK_ColorRED)
        val dst = rgba(info)
        assertTrue(src.scalePixels(dst, SkSamplingOptions.linear()))
        for (y in 0 until 3) for (x in 0 until 3) {
            assertEquals(SK_ColorRED, dst.getColor(x, y))
        }
    }

    @Test
    fun `bilinear fails on unknown colorType`() {
        val empty = SkPixmap()
        val dstInfo = SkImageInfo.Make(2, 2)
        val dst = rgba(dstInfo)
        assertEquals(false, empty.scalePixels(dst, SkSamplingOptions.linear()))
    }
}
