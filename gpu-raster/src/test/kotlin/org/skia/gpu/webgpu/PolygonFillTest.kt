package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * G3.3a acceptance tests — `drawPath` on GPU for convex polygons,
 * non-AA, fill style.
 *
 * `SkWebGpuDevice.drawPath` walks the path's verb stream (Move + Line
 * + Close only — curves throw with a pointer to G3.3b), transforms
 * each point by the CTM, and fan-tessellates the resulting polygon
 * into a triangle list uploaded to a vertex buffer and rendered
 * through the new `solid_polygon.wgsl` pipeline.
 *
 * Triangle = 3 vertices, fan tessellation gives 1 triangle.
 * Quad = 4 vertices, fan gives 2 triangles. Both convex, both
 * straightforward to assert on.
 */
class PolygonFillTest {

    @Test
    fun `drawPath of a square polygon fills the rectangle area`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 4-vertex polygon equivalent to a rect [10, 10, 30, 30] -- the
        // exact shape SkCanvas synthesises when it routes a rotated-CTM
        // drawRect through drawPath.
        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(30f, 10f)
            .lineTo(30f, 30f)
            .lineTo(10f, 30f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kFill_Style
            isAntiAlias = false
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Inside the polygon (e.g. (20, 20)) : blue opaque
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 20), "polygon interior")
        // Outside the polygon : white untouched
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5), "above-left of polygon")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "below-right of polygon")
    }

    @Test
    fun `drawPath of a triangle fills the triangular area`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Right triangle with vertices (20, 10), (40, 10), (20, 40).
        // Interior pixel (25, 15) is well inside the hypotenuse.
        val path = SkPathBuilder()
            .moveTo(20f, 10f)
            .lineTo(40f, 10f)
            .lineTo(20f, 40f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kFill_Style
            isAntiAlias = false
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Well inside the triangle
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(22, 15), "triangle interior")
        // The vertex (20, 10) and its top-row neighbours sit on the
        // triangle's edge -- non-AA rasterizers may include or exclude
        // them depending on the fill-rule. Skip them for this smoke
        // test ; the next assertion checks a clearly-outside pixel
        // beyond the hypotenuse.
        // Outside the hypotenuse (40-x > y-10 means x + y < 50 inside ;
        // pixel (35, 35) -> x+y = 70, outside).
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(35, 35), "beyond hypotenuse")
        // Clearly outside (in the corner not covered by the triangle)
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(50, 50), "far outside")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5), "above-left of triangle")
    }

    @Test
    fun `unsupported curve verbs throw with a pointer to G3 3b`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Path with a cubic Bezier -- not supported in G3.3a.
        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .cubicTo(20f, 5f, 30f, 35f, 40f, 10f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kFill_Style
            isAntiAlias = false
        }

        val thrown = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException::class.java,
        ) {
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    device.setBackground(SK_ColorWHITE)
                    SkCanvas(device).drawPath(path, paint)
                    device.flush()
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(
            thrown.message?.contains("G3.3b") == true,
            "expected pointer to G3.3b in error message, got: ${thrown.message}",
        )
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
