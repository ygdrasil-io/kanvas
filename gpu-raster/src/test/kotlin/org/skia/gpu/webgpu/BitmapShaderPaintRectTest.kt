package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * G5.2 acceptance tests -- `paint.shader is SkBitmapShader` routing on
 * rect paths. The shader-driven `canvas.drawRect` path (SkCanvas sees
 * a non-null shader and forwards to `drawPath(SkPath.Rect(rect))`),
 * so the dispatch under test lives in [SkWebGpuDevice.drawPath]'s
 * `shader is SkBitmapShader && path.isRect()` gate. Reuses the G5.1 /
 * G5.1.1 bitmap pipeline (sampler cache, pipeline cache,
 * bitmap_shader.wgsl) -- the slice is structural, the math is shared.
 *
 * Scope :
 *  - axis-aligned CTM only,
 *  - shader local matrix = identity or axial scale/translate,
 *  - filter / tile / blend support inherited from G5.1.1.
 *
 * Each test renders a small SkImage (4x4 quadrant pattern :
 * R/G/B/Bk) into a known device region via `paint.shader =
 * image.makeShader(...)`, flushes, asserts the readback at the
 * expected coordinates. The expected output mirrors the existing
 * [ImageRectTest] assertions one-for-one : same image, same dst,
 * same tile modes -- the routing must reproduce the same pixels.
 */
class BitmapShaderPaintRectTest {

    @Test
    fun `paint shader kClamp on rect renders the image at the rect`() {
        // Mirror of `ImageRectTest.drawImageRect 1to1 ...` via the
        // paint.shader route. Identity local matrix : the image
        // occupies its native pixels in user space ; with the dst
        // rect at (10, 10)..(10+SIDE, 10+SIDE) the kClamp tile mode
        // is irrelevant (no out-of-rect sampling).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                // The shader treats image pixel (0, 0) as user-space
                // (0, 0). To land the image at device (10, 10) we
                // translate the canvas first ; the CTM applies to
                // the rect AND the shader-local origin.
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // Background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0), "background")
        // Four quadrant centres -- match `ImageRectTest 1to1`.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "black")
    }

    @Test
    fun `paint shader kRepeat on oversized rect tiles the image`() {
        // Mirror of `ImageRectTest with kRepeat ...` via paint.shader.
        // The rect is 2*SIDE square at device (10, 10) ; with kRepeat
        // the image tiles 2x2 inside the rect (the shader does the
        // wrap via the sampler's Repeat addressMode).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kRepeat,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // Same expected pattern as `ImageRectTest kRepeat ...`.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "tile (0, 0) red")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(15, 11), "tile (1, 0) red wraps")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 15), "tile (0, 1) red wraps")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(17, 17), "tile (1, 1) black wraps")
    }

    @Test
    fun `paint shader kMirror on oversized rect reflects the image`() {
        // Mirror of `ImageRectTest with kMirror ...` via paint.shader.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kMirror,
                        tileY = SkTileMode.kMirror,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // Same expected pattern as `ImageRectTest kMirror ...`.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "tile (0, 0) red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(15, 11), "tile (1, 0) green (mirrored)")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 15), "tile (0, 1) blue (mirrored)")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(17, 17), "tile (1, 1) red (double mirror)")
    }

    @Test
    fun `paint shader kDecal on oversized rect transparent outside image`() {
        // Mirror of `ImageRectTest with kDecal ...` via paint.shader.
        // The dst rect is 2*SIDE square ; with kDecal the second
        // row/column of tiles falls outside the image footprint and
        // the shader emits (0, 0, 0, 0) -- the white background
        // shows through under the device's default kSrcOver blend.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kDecal,
                        tileY = SkTileMode.kDecal,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // In-bounds (tile (0, 0)) : image colours.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "in-bounds : red")
        assertEquals(listOf(0, 255, 0, 255), pixels.rgbaAt(13, 11), "in-bounds : green")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(11, 13), "in-bounds : blue")
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(13, 13), "in-bounds : black")
        // Outside image footprint : white background shows through.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(15, 11), "decal : right neighbour bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(11, 15), "decal : bottom neighbour bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(17, 17), "decal : bottom-right bg")
    }

    @Test
    fun `paint shader per-axis tile kRepeat kDecal`() {
        // G5.2 specific : the per-axis decal check in `bitmap_shader.wgsl`
        // splits `tileX` / `tileY`. Combine `(kRepeat, kDecal)` so the
        // horizontal axis tiles and the vertical axis decals to the
        // background. The first row of tiles repeats horizontally
        // (red corners at every multiple of SIDE) ; the second row
        // (below the image's vertical footprint) is fully transparent.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val side2 = SIDE * 2

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kDecal,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(side2.toFloat(), side2.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // First tile row (within image's vertical footprint) : horizontal
        // repeat -- red corner at every SIDE-aligned column.
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(11, 11), "tile (0, 0) red")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(15, 11), "tile (1, 0) red wraps")
        // Second tile row : decal vertically -- background shows through
        // even though horizontal repeat would otherwise paint a colour.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(11, 15), "decal y : bg")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(15, 15), "decal y : bg (also repeat x)")
    }

    @Test
    fun `paint shader through drawPaint tiles the image over the entire clip`() {
        // G5.2 -- `SkCanvas.drawPaint(paint with bitmap shader)`
        // routes through `SkWebGpuDevice.drawPaint`, which now
        // dispatches to drawPath (rect path) when `paint.shader !=
        // null` so the bitmap-shader gate fires. Setup : a 4x4
        // quadrant image with kRepeat on both axes ; drawPaint
        // covers the whole device, so the image tiles W/SIDE *
        // H/SIDE times across the readback. Identity canvas matrix
        // so user-space and device-space coincide -- the shader's
        // identity localMatrix lines image pixel (0, 0) up with
        // device (0, 0).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kRepeat,
                        tileY = SkTileMode.kRepeat,
                        sampling = SkSamplingOptions.nearest(),
                    )
                }
                canvas.drawPaint(paint)
                device.flush()
            }
        }

        // The image repeats every SIDE = 4 pixels both ways. The
        // top-left of every tile is red (image pixel (0, 0)). Check
        // a handful of tile origins across the device : (0, 0),
        // (SIDE, 0), (0, SIDE), (W - SIDE, H - SIDE).
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, 0), "tile (0, 0) red")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(SIDE, 0), "tile (1, 0) red wraps")
        assertEquals(listOf(255, 0, 0, 255), pixels.rgbaAt(0, SIDE), "tile (0, 1) red wraps")
        // Bottom-right tile : pixel (W-1, H-1) sits at the black
        // quadrant of the wrapping tile (image pixel (3, 3) modulo 4).
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(W - 1, H - 1), "tile (W/SIDE-1, H/SIDE-1) black")
    }

    @Test
    fun `drawImageRect source outside image clips but user kClamp shader extends edge`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeRightBlueImage()
        val src = SkRect.MakeLTRB(1f, 0f, 3f, 2f)
        val imageRectDst = SkRect.MakeXYWH(4f, 4f, 20f, 20f)
        val shaderDst = SkRect.MakeXYWH(4f, 32f, 20f, 20f)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    src,
                    imageRectDst,
                    SkSamplingOptions.nearest(),
                )

                val localMatrix = SkMatrix.MakeRectToRect(
                    src,
                    shaderDst,
                    SkMatrix.ScaleToFit.kFill_ScaleToFit,
                )!!
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.nearest(),
                        localMatrix = localMatrix,
                    )
                }
                canvas.drawRect(shaderDst, paint)
                device.flush()
            }
        }

        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(8, 14), "drawImageRect intersected source")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 14), "drawImageRect clipped destination")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(8, 42), "kClamp shader samples the image")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 42), "user kClamp shader keeps edge extension")
    }

    /**
     * 4x4 image split into 4 quadrants : R / G / B / Bk. Same helper
     * as [ImageRectTest.makeQuadrantImage] -- duplicated here to keep
     * the two test classes independent.
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

    private fun makeRightBlueImage(): SkImage {
        val bitmap = SkBitmap(2, 2).also { it.eraseColor(SK_ColorGREEN) }
        bitmap.setPixel(1, 0, SK_ColorBLUE)
        bitmap.setPixel(1, 1, SK_ColorBLUE)
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
