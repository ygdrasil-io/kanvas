package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.foundation.SkPaint

/**
 * `drawPoints` end-to-end on the WebGPU backend. The implementation lives
 * on [SkCanvas.drawPoints] and routes through [SkCanvas.drawCircle] /
 * [SkCanvas.drawRect] / [SkCanvas.drawLine], all of which dispatch to
 * [SkDevice.drawPath] / [SkDevice.drawRect]. These tests assert each
 * [SkCanvas.PointMode] paints sensible coverage through [SkWebGpuDevice].
 *
 * Non-AA, identity CTM, opaque black on white background — keeps the
 * pixel assertions byte-exact (premul == non-premul for opaque source ;
 * the readback bytes are directly comparable to BLACK / WHITE).
 */
class DrawPointsWebGpuTest {

    @Test
    fun `kPoints round cap stamps filled circles`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kRound_Cap
            isAntiAlias = false
        }
        // 4 points on a 2x2 grid, well-separated so their disks don't
        // overlap. Diameter ~6 device pixels at strokeWidth = 6.
        val pts = arrayOf(
            SkPoint(15f, 15f),
            SkPoint(45f, 15f),
            SkPoint(15f, 45f),
            SkPoint(45f, 45f),
        )

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPoints(SkCanvas.PointMode.kPoints, pts, paint)
                device.flush()
            }
        }

        // Each disk centre must be black.
        assertEquals(BLACK, pixels.rgbaAt(15, 15), "centre of disk 0")
        assertEquals(BLACK, pixels.rgbaAt(45, 15), "centre of disk 1")
        assertEquals(BLACK, pixels.rgbaAt(15, 45), "centre of disk 2")
        assertEquals(BLACK, pixels.rgbaAt(45, 45), "centre of disk 3")
        // Halfway between disks (outside radius=3) stays white.
        assertEquals(WHITE, pixels.rgbaAt(30, 30), "between disks stays white")
        assertEquals(WHITE, pixels.rgbaAt(0, 0), "corner stays white")
    }

    @Test
    fun `kPoints square cap stamps axis-aligned squares`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kSquare_Cap
            isAntiAlias = false
        }
        val pts = arrayOf(SkPoint(30f, 30f))

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPoints(SkCanvas.PointMode.kPoints, pts, paint)
                device.flush()
            }
        }

        // The square spans [27, 33) x [27, 33) device-pixels (centred,
        // side = strokeWidth = 6). Centre is black ; corners just inside
        // are black ; pixels just outside are white.
        assertEquals(BLACK, pixels.rgbaAt(30, 30), "square centre")
        assertEquals(BLACK, pixels.rgbaAt(27, 27), "inside top-left corner")
        assertEquals(BLACK, pixels.rgbaAt(32, 32), "inside bottom-right corner")
        assertEquals(WHITE, pixels.rgbaAt(26, 26), "just outside top-left")
        assertEquals(WHITE, pixels.rgbaAt(33, 33), "just outside bottom-right")
    }

    @Test
    fun `kLines pairs points into independent line segments`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 4 points -> 2 segments :
        //   (10, 30) -> (50, 30) horizontal
        //   (30, 10) -> (30, 50) vertical
        val pts = arrayOf(
            SkPoint(10f, 30f), SkPoint(50f, 30f),
            SkPoint(30f, 10f), SkPoint(30f, 50f),
        )
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPoints(SkCanvas.PointMode.kLines, pts, paint)
                device.flush()
            }
        }

        // Each hairline lands on either of two adjacent rows / columns
        // depending on rounding. Check both candidate pixels.
        assertTrue(
            pixels.rgbaAt(20, 30) == BLACK || pixels.rgbaAt(20, 29) == BLACK,
            "horizontal segment paints near (20, 30)",
        )
        assertTrue(
            pixels.rgbaAt(30, 20) == BLACK || pixels.rgbaAt(29, 20) == BLACK,
            "vertical segment paints near (30, 20)",
        )
        // Far from both segments stays white.
        assertEquals(WHITE, pixels.rgbaAt(5, 5), "corner stays white")
        assertEquals(WHITE, pixels.rgbaAt(55, 55), "opposite corner stays white")
    }

    @Test
    fun `kPolygon connects every adjacent pair (open polyline)`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 5-point zigzag : every adjacent pair forms a segment, no
        // implicit close from last back to first.
        val pts = arrayOf(
            SkPoint(10f, 10f),
            SkPoint(20f, 30f),
            SkPoint(30f, 10f),
            SkPoint(40f, 30f),
            SkPoint(50f, 10f),
        )
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPoints(SkCanvas.PointMode.kPolygon, pts, paint)
                device.flush()
            }
        }

        // Polyline endpoints must be black (or adjacent pixel — hairline
        // rounding).
        assertTrue(
            pixels.rgbaAt(10, 10) == BLACK || pixels.rgbaAt(10, 9) == BLACK ||
                pixels.rgbaAt(9, 10) == BLACK,
            "first endpoint",
        )
        assertTrue(
            pixels.rgbaAt(50, 10) == BLACK || pixels.rgbaAt(50, 9) == BLACK ||
                pixels.rgbaAt(49, 10) == BLACK,
            "last endpoint",
        )
        // No implicit close : the (10, 10) -> (50, 10) chord must NOT
        // be painted. The midpoint of that chord (30, 10) lies on the
        // 3rd vertex but the row between the zigzag valleys (y = 20)
        // halfway along x must mostly be white -- in particular (15, 25)
        // is well off any segment.
        assertEquals(WHITE, pixels.rgbaAt(0, 0), "corner stays white")
        assertEquals(WHITE, pixels.rgbaAt(50, 50), "below zigzag stays white")
    }

    @Test
    fun `empty point array is a no-op on GPU device`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kRound_Cap
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPoints(SkCanvas.PointMode.kPoints, emptyArray(), paint)
                device.flush()
            }
        }

        // Every pixel stays at the background colour.
        for (y in 0 until H step 8) {
            for (x in 0 until W step 8) {
                assertEquals(WHITE, pixels.rgbaAt(x, y), "($x, $y) stays white")
            }
        }
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
        val BLACK: List<Int> = listOf(0, 0, 0, 255)
        val WHITE: List<Int> = listOf(255, 255, 255, 255)
    }
}
