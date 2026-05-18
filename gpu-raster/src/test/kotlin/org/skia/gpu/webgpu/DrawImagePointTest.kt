package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint

/**
 * G5.4 acceptance tests — `drawImage(image, x, y, paint)` point-positioned
 * on the GPU backend.
 *
 * The canvas-layer implementation routes [SkCanvas.drawImage] of a single
 * (x, y) anchor to [SkCanvas.drawImageRect] with `src = (0, 0, w, h)` and
 * `dst = (x, y, x+w, y+h)`, defaulting `sampling = SkSamplingOptions.Default`
 * (== `kNearest / kNone`) and `constraint = kFast`. These tests verify
 * that the GPU device pipeline (bitmap-shader, G5.1 / G5.1.1) handles the
 * routing end-to-end :
 *
 *  - point-positioned 1:1 blit with no paint,
 *  - alpha modulation via `paint.alpha`,
 *  - blend-mode honoured via `paint.blendMode` (kSrc).
 */
class DrawImagePointTest {

    @Test
    fun `drawImage at point places source pixels at the offset`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImage(image, 10f, 20f)
                device.flush()
            }
        }

        // Background outside the dst rect : white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background top-left")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40), "background past dst")

        // Image quadrants placed at (10, 20) -> (14, 24) on a 4x4 image.
        // Quadrant (0, 0) red at device (11, 21), (1, 0) green at (13, 21),
        // (0, 1) blue at (11, 23), (1, 1) black at (13, 23).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 21), "red quadrant (TL)")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 21), "green quadrant (TR)")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 23), "blue quadrant (BL)")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 23), "black quadrant (BR)")
    }

    @Test
    fun `drawImage with alpha 128 paint modulates source over background`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply { alpha = 128 }
                canvas.drawImage(image, 10f, 20f, paint = paint)
                device.flush()
            }
        }

        // Background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background untouched")

        // alpha = 128 source over opaque white background. With premul
        // SrcOver : out = src + (1 - src.a) * dst. For src = red premul'd
        // by 128/255 : (128, 0, 0, 128) ; dst = (255, 255, 255, 255).
        //  out.r = 128 + (1 - 128/255) * 255 = 128 + 127 = 255.
        //  out.g = 0   + (1 - 128/255) * 255 =       127.
        //  out.b = 0   + (1 - 128/255) * 255 =       127.
        // (similar for the other quadrant colours.)
        val tol = 2

        val red = pixels.rgbaAt(11, 21)
        assertNear(255, red[0], tol, "alpha red.r")
        assertNear(127, red[1], tol, "alpha red.g")
        assertNear(127, red[2], tol, "alpha red.b")
        assertEquals(255, red[3], "alpha red.a (background opaque)")

        val green = pixels.rgbaAt(13, 21)
        assertNear(127, green[0], tol, "alpha green.r")
        assertNear(255, green[1], tol, "alpha green.g")
        assertNear(127, green[2], tol, "alpha green.b")

        val black = pixels.rgbaAt(13, 23)
        assertNear(127, black[0], tol, "alpha black.r")
        assertNear(127, black[1], tol, "alpha black.g")
        assertNear(127, black[2], tol, "alpha black.b")
    }

    @Test
    fun `drawImage with kSrc blend mode overwrites the destination`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
                canvas.drawImage(image, 10f, 20f, paint = paint)
                device.flush()
            }
        }

        // Background outside the dst : white (kSrc only touches the rect).
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "kSrc background")

        // Inside : pure image colours (kSrc overwrites white).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 21), "kSrc red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 21), "kSrc green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 23), "kSrc blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 23), "kSrc black overwrites white")
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int, label: String) {
        val ok = kotlin.math.abs(actual - expected) <= tolerance
        if (!ok) {
            assertEquals(expected, actual, "$label (|delta| > $tolerance)")
        }
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
