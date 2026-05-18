package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkLattice
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage

/**
 * GPU acceptance tests — `SkCanvas.drawImageLattice` on the WebGPU
 * backend.
 *
 * `drawImageLattice` is implemented at the [SkCanvas] level (Phase
 * R-suivi.50 / S7-C) as an N × M tessellation that emits one
 * [SkCanvas.drawImageRect] per non-skipped cell. Even-indexed slices
 * (rows + cols) are fixed (corner-like, 1:1 source pixels) ; odd-
 * indexed slices stretch to fill remaining destination space. Since
 * the GPU device implements `drawImageRect` (G5.1 / G5.1.1),
 * `drawImageLattice` requires no backend-specific code — these tests
 * verify the tessellation routes correctly through the WebGPU
 * pipeline and that the `kTransparent` rect-type skip semantics are
 * preserved on this backend.
 */
class DrawImageLatticeTest {

    @Test
    fun `lattice equivalent to nine-patch tessellates on the GPU`() {
        // 6x6 source with 3 div bands (xDivs = yDivs = [2,4]) :
        // corners are 2x2 red, edges are 2x{2,4,2} green, centre is
        // 2x2 blue. Drawn into a 30x30 dst : corners stay 2x2,
        // centre/edges stretch to fill 26 dst pixels on the flexible
        // axis.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeNinePatchTilesImage()

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageLattice(
                    image,
                    SkLattice(xDivs = intArrayOf(2, 4), yDivs = intArrayOf(2, 4)),
                    SkRect.MakeWH(30f, 30f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // Corners (each 2x2) — solid red blocks.
        for (y in 0..1) {
            for (x in 0..1) {
                assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, y), "TL corner @($x,$y)")
            }
        }
        for (y in 0..1) {
            for (x in 28..29) {
                assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, y), "TR corner @($x,$y)")
            }
        }
        for (y in 28..29) {
            for (x in 0..1) {
                assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, y), "BL corner @($x,$y)")
            }
        }
        for (y in 28..29) {
            for (x in 28..29) {
                assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(x, y), "BR corner @($x,$y)")
            }
        }

        // Centre (stretched 26x26 blue from a 2x2 source) — sample one
        // interior pixel well clear of edges.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(15, 15), "centre blue")

        // Top edge (green, stretched horizontally between corners).
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(15, 0), "top edge green")
        // Left edge (green, stretched vertically between corners).
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(0, 15), "left edge green")

        // Background untouched outside the dst rect.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(50, 50), "background")
    }

    @Test
    fun `empty lattice falls back to a plain drawImageRect on the GPU`() {
        // No divs -> single-cell lattice -> SkCanvas degenerates to
        // a plain drawImageRect over the full destination.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage()

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageLattice(
                    image,
                    SkLattice(xDivs = intArrayOf(), yDivs = intArrayOf()),
                    SkRect.MakeXYWH(10f, 10f, 4f, 4f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // 1:1 placement — exact quadrant readback.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "TL red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "TR green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "BL blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "BR black")
        // Background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background")
    }

    @Test
    fun `lattice kTransparent rect-type skips a cell on the GPU`() {
        // 6x6 source ; lattice with xDivs=[2,4], yDivs=[2,4] -> 3x3
        // cells. Centre cell (index 4 in row-major) marked kTransparent
        // so it does not draw — the readback at that cell must
        // show the background colour.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeNinePatchTilesImage()

        // 9-cell rectTypes array : centre = kTransparent, rest =
        // kDefault. Row-major : centre cell is at index (row=1,
        // col=1) = 1 * 3 + 1 = 4.
        val rectTypes = Array(9) { SkLattice.RectType.kDefault }
        rectTypes[4] = SkLattice.RectType.kTransparent

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLACK)
                val canvas = SkCanvas(device)
                canvas.drawImageLattice(
                    image,
                    SkLattice(
                        xDivs = intArrayOf(2, 4),
                        yDivs = intArrayOf(2, 4),
                        rectTypes = rectTypes,
                    ),
                    SkRect.MakeWH(30f, 30f),
                    SkFilterMode.kNearest,
                    null,
                )
                device.flush()
            }
        }

        // Corners painted normally (red, 2x2).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, 0), "TL corner red")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(29, 29), "BR corner red")

        // Centre is the kTransparent cell — destination stays at the
        // black background. The centre cell spans device pixels
        // (2..28, 2..28) since xDivs map 1:1 at the corners.
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(15, 15), "centre kTransparent : background")

        // Edge cells (not transparent) still painted green.
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(15, 0), "top edge green")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(0, 15), "left edge green")
    }

    /**
     * 6×6 image laid out as a 3×3 grid of 2×2 cells :
     *  - 4 corners = red
     *  - 4 edges = green
     *  - 1 centre = blue
     */
    private fun makeNinePatchTilesImage(): SkImage {
        val bm = SkBitmap(6, 6)
        fun fill(x: Int, y: Int, w: Int, h: Int, c: Int) {
            for (yy in y until y + h) for (xx in x until x + w) bm.setPixel(xx, yy, c)
        }
        // Corners (red).
        fill(0, 0, 2, 2, SK_ColorRED)
        fill(4, 0, 2, 2, SK_ColorRED)
        fill(0, 4, 2, 2, SK_ColorRED)
        fill(4, 4, 2, 2, SK_ColorRED)
        // Edges (green).
        fill(2, 0, 2, 2, SK_ColorGREEN)
        fill(2, 4, 2, 2, SK_ColorGREEN)
        fill(0, 2, 2, 2, SK_ColorGREEN)
        fill(4, 2, 2, 2, SK_ColorGREEN)
        // Centre (blue).
        fill(2, 2, 2, 2, SK_ColorBLUE)
        return SkImage.Make(bm)
    }

    /**
     * 4×4 image split into 4 equal quadrants : red / green / blue /
     * black (matches [ImageRectTest.makeQuadrantImage]).
     */
    private fun makeQuadrantImage(): SkImage {
        val bm = SkBitmap(4, 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val c = when {
                    x < 2 && y < 2 -> SK_ColorRED
                    x >= 2 && y < 2 -> SK_ColorGREEN
                    x < 2 && y >= 2 -> SK_ColorBLUE
                    else -> SK_ColorBLACK
                }
                bm.setPixel(x, y, c)
            }
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
