package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions

/**
 * G5.1 acceptance tests — `drawImageRect` on the GPU via the bitmap
 * shader.
 *
 * Scope of the first slice (see `MIGRATION_PLAN_GPU_WEBGPU.md` Phase
 * G5) :
 *  - filter = kLinear ; tile mode = kClamp ; blend mode = kSrcOver.
 *  - source image is uploaded as RGBA8Unorm to a per-device GPU
 *    texture cache, sampled via a `Linear / ClampToEdge` sampler.
 *  - dst rect is pixelEdge-rounded ; out-of-rect pixels stay at the
 *    device background (no overdraw).
 *
 * Each test renders a small SkImage (constructed by setting per-pixel
 * colors on an SkBitmap then snapshotting) into a known device region,
 * flushes, and asserts the readback pixels at the expected coordinates.
 */
class ImageRectTest {

    @Test
    fun `drawImageRect 1to1 places source pixels at the destination corner`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 4x4 source image with 4 quadrants : red / green / blue / black.
        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.linear(),
                )
                device.flush()
            }
        }

        // Background, far from the dst rect : white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background top-left")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "background bottom-right")

        // Sample the 4 quadrant centres in the destination region.
        // Image quadrant (x, y) for x, y in {0, 1} : red, green, blue, black.
        // 4x4 image placed at device (10, 10) : centre of quadrant (0, 0)
        // is at device (11, 11) ; quadrant (1, 1) at (13, 13).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "top-left quadrant : red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "top-right quadrant : green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "bottom-left quadrant : blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "bottom-right quadrant : black")
    }

    @Test
    fun `drawImageRect 2x upscale samples the source via bilinear filter`() {
        // 2x upscale : a 4x4 source -> 8x8 device rect. With kLinear the
        // interior of each source-quadrant region stays the source color ;
        // the boundary between quadrants softens via the bilinear lerp.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, (SIDE * 2).toFloat(), (SIDE * 2).toFloat()),
                    SkSamplingOptions.linear(),
                )
                device.flush()
            }
        }

        // 2x upscale : dst rect spans device (10, 10) -> (18, 18).
        // Background untouched outside the dst rect.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(30, 30), "background past dst")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 20), "background just outside dst")

        // Far inside the red quadrant -- device (11, 11) maps back to src
        // (~0.75, 0.75), well inside src pixel (0, 0) which is red. With
        // ClampToEdge the bilinear lerp at the corner clamps to red.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "red quadrant interior")

        // Far inside the black quadrant -- device (17, 17) is the dst
        // bottom-right corner pixel ; maps back to src (~3.75, 3.75),
        // inside src pixel (3, 3) which is black.
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(17, 17), "black quadrant interior")
    }

    /**
     * Build a `side x side` image split into 4 equal quadrants :
     *   top-left = red, top-right = green,
     *   bottom-left = blue, bottom-right = black.
     */
    private fun makeQuadrantImage(side: Int): SkImage {
        val bitmap = SkBitmap(side, side)
        val half = side / 2
        for (y in 0 until side) {
            for (x in 0 until side) {
                val color = when {
                    x < half && y < half -> SK_ColorRED
                    x >= half && y < half -> SK_ColorGREEN
                    x < half && y >= half -> SK_ColorBLUE
                    else -> SK_ColorBLACK
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return SkImage.Make(bitmap)
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
        const val SIDE: Int = 4
    }
}
