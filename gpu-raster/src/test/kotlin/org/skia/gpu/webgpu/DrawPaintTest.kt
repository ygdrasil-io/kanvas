package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint

/**
 * G3.2 acceptance tests — drawPaint on GPU.
 *
 * `drawPaint` is the simplest device entry point : fill the entire
 * clip rectangle with the paint's color (under blend mode). The G3.2
 * implementation routes straight to `drawRect` on a rect spanning the
 * clip, so all G2.x logic (alpha, blend mode, AA-when-asked) applies.
 */
class DrawPaintTest {

    @Test
    fun `drawPaint fills the full clip with opaque source`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply { color = SK_ColorBLUE }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPaint(paint)
                device.flush()
            }
        }

        // Every pixel of the 64x64 viewport must now be opaque blue.
        for (y in 0 until H step 17) {        // sample sparse grid
            for (x in 0 until W step 17) {
                assertEquals(
                    listOf(0, 0, 255, 255), pixels.rgbaAt(x, y),
                    "drawPaint expected to paint every pixel — ($x, $y) missed",
                )
            }
        }
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(0, 0), "top-left corner")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(W - 1, H - 1), "bottom-right corner")
    }

    @Test
    fun `drawPaint after clipRect only fills inside the clip`() {
        // SkCanvas.clipRect intersects the device clip ; drawPaint then
        // sees the smaller clip and paints only inside it.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply { color = SK_ColorBLUE }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.clipRect(org.skia.math.SkRect.MakeLTRB(10f, 10f, 30f, 30f))
                canvas.drawPaint(paint)
                device.flush()
            }
        }

        // Inside the clip : blue.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(15, 15), "inside clip")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(29, 29), "inside clip near corner")
        // Outside the clip : white (background untouched).
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5), "outside top-left")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "outside bottom-right")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(30, 15), "just outside right edge")
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
