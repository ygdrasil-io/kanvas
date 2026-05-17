package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * G3.4.3 acceptance tests — true 1-device-pixel hairlines via `drawPath`
 * with `paint.strokeWidth = 0`.
 *
 * `SkStroker` operates in source space, so a naive `width = 1f` produces
 * a CTM-scaled outline (e.g. 2 device pixels under `scale(2, 2)`). The
 * fix in `SkWebGpuDevice.drawPath` synthesises `1f / resScale` so the
 * resulting outline is ~1 device pixel wide regardless of the CTM.
 *
 * These tests assert the stroke covers points on the path and leaves
 * points well off the path untouched, for an open polyline path under
 * identity CTM (non-AA).
 */
class PathHairlineStrokeTest {

    @Test
    fun `hairline drawPath paints pixels on the path under identity CTM`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Open polyline : (10, 20) -> (50, 20). One horizontal segment,
        // hairline stroke -> a ~1px-tall blue band on row 20 (or 19/20
        // boundary, non-AA grid centres).
        val path: SkPath = SkPathBuilder()
            .moveTo(10f, 20f)
            .lineTo(50f, 20f)
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Hairline must paint at least one pixel along the path. Skia
        // hairline rasterisation places coverage on either row 19 or
        // row 20 (non-AA, integer y) depending on rounding ; assert at
        // least one of the two is blue at the midpoint x = 30.
        val midRow19 = pixels.rgbaAt(30, 19)
        val midRow20 = pixels.rgbaAt(30, 20)
        val onPath = (midRow19 == BLUE) || (midRow20 == BLUE)
        assertTrue(onPath, "hairline must paint a pixel on the path at midpoint")

        // 5 rows above and below the path must remain background.
        assertEquals(WHITE, pixels.rgbaAt(30, 15), "5 rows above stays white")
        assertEquals(WHITE, pixels.rgbaAt(30, 25), "5 rows below stays white")
        // Well outside the path (left of x=10) : background.
        assertEquals(WHITE, pixels.rgbaAt(5, 20), "left of path stays white")
        assertEquals(WHITE, pixels.rgbaAt(55, 20), "right of path stays white")
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
        val BLUE: List<Int> = listOf(0, 0, 255, 255)
        val WHITE: List<Int> = listOf(255, 255, 255, 255)
    }
}
