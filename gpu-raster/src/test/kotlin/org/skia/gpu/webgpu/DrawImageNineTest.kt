package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage

/**
 * GPU acceptance tests — `SkCanvas.drawImageNine` on the WebGPU backend.
 *
 * `drawImageNine` is implemented at the [SkCanvas] level (Phase R2.13)
 * as a decomposition of the 9-patch into per-cell [SkCanvas.drawImageRect]
 * calls. Since the GPU device implements `drawImageRect` (G5.1 / G5.1.1),
 * `drawImageNine` requires no backend-specific code — these tests verify
 * that the decomposition routes correctly through the WebGPU pipeline
 * (texture cache, sampler cache, bitmap shader) and that corner +
 * edge + middle patches land at the expected device pixels with no
 * regression on the existing `drawImageRect` path.
 *
 * Sampling stays at `kNearest` so we can assert exact byte values at
 * each readback coordinate (no bilinear smoothing across patch
 * boundaries).
 */
class DrawImageNineTest {

    @Test
    fun `9-patch corners stay 1to1 and middle stretches on the GPU`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 3x3 source : 1-px red border around a 1-px green centre.
        // center = (1,1,2,2). With dst = (0,0,10,10) :
        //  - corner columns 0..1 + 9..10  : 1-px wide, 1:1
        //  - middle column   1..9         : stretched 1-px green/red
        //  - same for rows.
        val image = makeNinePatchImage()

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageNine(
                    image,
                    SkIRect(1, 1, 2, 2),
                    SkRect.MakeLTRB(0f, 0f, 10f, 10f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // Far outside the dst region : background stays white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "background")

        // 4 corner pixels — must be red (1:1 from source corners).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, 0), "TL corner")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(9, 0), "TR corner")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, 9), "BL corner")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(9, 9), "BR corner")

        // Top/bottom edges (middle x-band, y=0 or y=9) — red, 1-px-tall
        // source stretched horizontally.
        for (x in 1..8) {
            assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, 0), "top edge @($x,0)")
            assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, 9), "bot edge @($x,9)")
        }
        // Left/right edges (middle y-band, x=0 or x=9) — red, 1-px-wide
        // source stretched vertically.
        for (y in 1..8) {
            assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, y), "left edge @(0,$y)")
            assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(9, y), "right edge @(9,$y)")
        }

        // Middle quad must be green (the centre pixel stretched across
        // both axes).
        for (y in 1..8) {
            for (x in 1..8) {
                assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(x, y), "middle @($x,$y)")
            }
        }
    }

    @Test
    fun `9-patch with quadrant source stretches edges along their flexible axis`() {
        // 3-row source with distinct row colours :
        //   row 0 : red (top corners + top edge)
        //   row 1 : green (left/right edges + middle)
        //   row 2 : blue (bottom corners + bottom edge)
        // center = (1,1,2,2) -> top/middle/bottom rows split correctly.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeRowStripedNinePatchImage()

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageNine(
                    image,
                    SkIRect(1, 1, 2, 2),
                    SkRect.MakeLTRB(0f, 0f, 8f, 8f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // Row 0 (top corners + top edge) : red across the full width.
        for (x in 0..7) {
            assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, 0), "row 0 @($x,0) red")
        }
        // Row 7 (bottom corners + bottom edge) : blue across the full
        // width.
        for (x in 0..7) {
            assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(x, 7), "row 7 @($x,7) blue")
        }
        // Rows 1..6 (middle band) : green at every column.
        for (y in 1..6) {
            for (x in 0..7) {
                assertEquals(
                    listOf(0, 255, 0, 255),
                    pixels.rgbaAt(x, y),
                    "middle row @($x,$y) green",
                )
            }
        }
    }

    @Test
    fun `degenerate center falls back to drawImageRect on the GPU`() {
        // center = (1,1,1,1) — empty rect, SkCanvas falls back to a
        // plain drawImageRect over the full destination. The result is
        // a stretched 3x3 image filling the entire dst — every dst
        // pixel maps to some source pixel (red border or green
        // centre).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeNinePatchImage()

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLACK)
                val canvas = SkCanvas(device)
                canvas.drawImageNine(
                    image,
                    SkIRect(1, 1, 1, 1),
                    SkRect.MakeLTRB(0f, 0f, 6f, 6f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // The dst rect was painted (not background-black anymore) and
        // covers the full (0,0,6,6) area. Each readback pixel must
        // be one of the source colours, not the black background.
        for (y in 0..5) {
            for (x in 0..5) {
                val px = pixels.rgbaAt(x, y)
                val isRed = px == listOf(255, 0, 0, 255)
                val isGreen = px == listOf(0, 255, 0, 255)
                assert(isRed || isGreen) {
                    "fallback dst @($x,$y) must be red or green, got $px"
                }
            }
        }
        // Background outside the dst stays black.
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(20, 20), "background")
    }

    /**
     * 3×3 image — 1-px red border around a 1-px green centre.
     *
     * ```
     *   R R R
     *   R G R
     *   R R R
     * ```
     */
    private fun makeNinePatchImage(): SkImage {
        val bm = SkBitmap(3, 3)
        val r = SK_ColorRED
        val g = SK_ColorGREEN
        bm.setPixel(0, 0, r); bm.setPixel(1, 0, r); bm.setPixel(2, 0, r)
        bm.setPixel(0, 1, r); bm.setPixel(1, 1, g); bm.setPixel(2, 1, r)
        bm.setPixel(0, 2, r); bm.setPixel(1, 2, r); bm.setPixel(2, 2, r)
        return SkImage.Make(bm)
    }

    /**
     * 3×3 image with 3 distinct rows : red / green / blue.
     */
    private fun makeRowStripedNinePatchImage(): SkImage {
        val bm = SkBitmap(3, 3)
        for (x in 0..2) {
            bm.setPixel(x, 0, SK_ColorRED)
            bm.setPixel(x, 1, SK_ColorGREEN)
            bm.setPixel(x, 2, SK_ColorBLUE)
        }
        return SkImage.Make(bm)
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
