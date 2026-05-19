package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import kotlin.math.abs

/**
 * Phase H2 paint-colorFilter tests -- verify `paint.colorFilter` is
 * honoured on both bitmap pipelines :
 *  - rect pipeline (`bitmap_shader.wgsl`, [SkWebGpuDevice.drawImageRect]
 *    + `paint.shader is SkBitmapShader` on an axis-aligned rect).
 *  - AA stencil-and-cover pipeline
 *    (`aa_stencil_cover_bitmap_shader.wgsl`, [SkWebGpuDevice.drawPath]
 *    with `paint.shader is SkBitmapShader` on a non-rect AA path).
 *
 * Mirrors [DrawRectColorFilterTest] but routes the colour filter
 * through the 2 bitmap pipelines. The slice extends the per-draw
 * uniform's tail with the same 6-vec4f payload shared with
 * `solid_color.wgsl` / `layer_composite.wgsl`. Order in the shader :
 * `shader sample -> colorspace transform -> colorFilter -> premul +
 * paintColor -> clipShape coverage`, matching Skia's `shader ->
 * colorFilter -> maskFilter -> clip -> blend` stage order.
 *
 * Hard-scope filter coverage :
 *  - `SkColorFilters.Blend(colour, mode)` (kind 1).
 *  - `SkColorFilters.Matrix(20 floats)` (kind 2).
 *  - `null` colour filter -> the bit-iso fast path (shader's
 *    `kind == 0` branch, identity uniform payload).
 */
class BitmapColorFilterTest {

    @Test
    fun `drawImageRect with Blend(red, kSrcIn) tints the image red`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Blend(red, kSrcIn) on an opaque (alpha = 1) image :
                // shader output is filtered to `s * da = (1, 0, 0, 1) * 1`
                // for every sampled texel ; the whole footprint paints red.
                val paint = SkPaint().apply {
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
                }
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.nearest(),
                    paint,
                )
                device.flush()
            }
        }

        // All four image quadrants paint as red after the filter.
        assertRgbaApprox(pixels, 11, 11, 255, 0, 0, 255, tag = "Blend(red,kSrcIn) red quadrant", tol = 2)
        assertRgbaApprox(pixels, 13, 11, 255, 0, 0, 255, tag = "Blend(red,kSrcIn) green quadrant", tol = 2)
        assertRgbaApprox(pixels, 11, 13, 255, 0, 0, 255, tag = "Blend(red,kSrcIn) blue quadrant", tol = 2)
        assertRgbaApprox(pixels, 13, 13, 255, 0, 0, 255, tag = "Blend(red,kSrcIn) black quadrant", tol = 2)
        // Outside the dst rect : background untouched.
        assertRgbaApprox(pixels, 1, 1, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawImageRect with Matrix luma colorFilter converts colours to grayscale`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Rec.601 luma weights -- collapses each colour channel
                // into its luminance equivalent. Red -> 76, green -> 150,
                // blue -> 29, black -> 0.
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.nearest(),
                    paint,
                )
                device.flush()
            }
        }

        // Each quadrant carries its luminance.
        assertRgbaApprox(pixels, 11, 11, 76, 76, 76, 255, tag = "luma red -> 76", tol = 2)
        assertRgbaApprox(pixels, 13, 11, 150, 150, 150, 255, tag = "luma green -> 150", tol = 2)
        assertRgbaApprox(pixels, 11, 13, 29, 29, 29, 255, tag = "luma blue -> 29", tol = 2)
        assertRgbaApprox(pixels, 13, 13, 0, 0, 0, 255, tag = "luma black -> 0", tol = 2)
        // Outside the dst rect : background untouched.
        assertRgbaApprox(pixels, 1, 1, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawImageRect with null colorFilter is the bit-iso fast path`() {
        // Regression -- the colour-filter slot expanded the bitmap-rect
        // uniform from 224 to 320 bytes. Verify the no-filter path still
        // produces the same image as pre-slice (matches ImageRectTest
        // 1to1).
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
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.nearest(),
                )
                device.flush()
            }
        }

        // Bit-iso : same as pre-slice -- four quadrants paint as raw
        // image pixels.
        assertRgbaApprox(pixels, 11, 11, 255, 0, 0, 255, tag = "raw red", tol = 0)
        assertRgbaApprox(pixels, 13, 11, 0, 255, 0, 255, tag = "raw green", tol = 0)
        assertRgbaApprox(pixels, 11, 13, 0, 0, 255, 255, tag = "raw blue", tol = 0)
        assertRgbaApprox(pixels, 13, 13, 0, 0, 0, 255, tag = "raw black", tol = 0)
    }

    @Test
    fun `paint shader on rect with Blend(red, kSrcIn) tints the tiled bitmap`() {
        // Routes through `drawRect` with `paint.shader is SkBitmapShader`
        // -> `drawBitmapShaderFillRect` -> `enqueueImageRectDrawInternal`.
        // Same shader pipeline as drawImageRect, exercised through the
        // shader-routing path so the colorFilter must thread through both
        // entry points.
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
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
                }
                canvas.translate(10f, 10f)
                canvas.drawRect(
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    paint,
                )
                device.flush()
            }
        }

        // All quadrants tinted red.
        assertRgbaApprox(pixels, 11, 11, 255, 0, 0, 255, tag = "shader rect red q", tol = 2)
        assertRgbaApprox(pixels, 13, 11, 255, 0, 0, 255, tag = "shader rect green q", tol = 2)
        assertRgbaApprox(pixels, 11, 13, 255, 0, 0, 255, tag = "shader rect blue q", tol = 2)
        assertRgbaApprox(pixels, 13, 13, 255, 0, 0, 255, tag = "shader rect black q", tol = 2)
    }

    @Test
    fun `paint shader on circle path with Matrix luma colorFilter masks bitmap to grayscale`() {
        // Routes through `drawPath` with `paint.shader is SkBitmapShader`
        // on a non-rect AA path -> [StencilCoverAaBitmapShaderDraw] +
        // `aa_stencil_cover_bitmap_shader.wgsl`. The colour filter must
        // route through the AA stencil-and-cover bitmap pipeline as well.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    shader = image.makeShader(
                        tileX = SkTileMode.kClamp,
                        tileY = SkTileMode.kClamp,
                        sampling = SkSamplingOptions.nearest(),
                    )
                    colorFilter = SkColorFilters.Matrix(luma)
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Four image quadrant centres inside the disk : luma applied.
        assertRgbaApprox(pixels, 1, 1, 76, 76, 76, 255, tag = "luma red q", tol = 2)
        assertRgbaApprox(pixels, 3, 1, 150, 150, 150, 255, tag = "luma green q", tol = 2)
        assertRgbaApprox(pixels, 1, 3, 29, 29, 29, 255, tag = "luma blue q", tol = 2)
        assertRgbaApprox(pixels, 3, 3, 0, 0, 0, 255, tag = "luma black q", tol = 2)
    }

    @Test
    fun `paint shader on circle path with Blend(blue, kSrcIn) masks bitmap to blue`() {
        // Same AA stencil-cover path, Blend variant. Replaces every
        // sampled colour with blue (opaque src in alpha = 1) inside the
        // disk, leaves the background untouched outside the disk.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

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
                    colorFilter = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kSrcIn)
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Every quadrant centre inside the disk paints blue.
        assertRgbaApprox(pixels, 1, 1, 0, 0, 255, 255, tag = "blend blue red q", tol = 2)
        assertRgbaApprox(pixels, 3, 1, 0, 0, 255, 255, tag = "blend blue green q", tol = 2)
        assertRgbaApprox(pixels, 1, 3, 0, 0, 255, 255, tag = "blend blue blue q", tol = 2)
        assertRgbaApprox(pixels, 3, 3, 0, 0, 255, 255, tag = "blend blue black q", tol = 2)
    }

    @Test
    fun `paint shader on circle path with null colorFilter is the bit-iso fast path`() {
        // Regression -- the colour-filter slot expanded the AA stencil-
        // cover bitmap uniform from 4368 to 4464 bytes (edge tail shifted
        // forward by 96 bytes). Verify the no-filter path still produces
        // the same image as pre-slice (matches BitmapShaderPathTest
        // kClamp circle).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = makeQuadrantImage(SIDE)
        val path = SkPath.Circle(8f, 8f, 12f, SkPathDirection.kCW)

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
                    isAntiAlias = true
                }
                canvas.drawPath(path, paint)
                device.flush()
            }
        }

        // Bit-iso : same as BitmapShaderPathTest's pre-slice expectations.
        assertRgbaApprox(pixels, 1, 1, 255, 0, 0, 255, tag = "raw red q", tol = 0)
        assertRgbaApprox(pixels, 3, 1, 0, 255, 0, 255, tag = "raw green q", tol = 0)
        assertRgbaApprox(pixels, 1, 3, 0, 0, 255, 255, tag = "raw blue q", tol = 0)
        assertRgbaApprox(pixels, 3, 3, 0, 0, 0, 255, tag = "raw black q", tol = 0)
    }

    private fun assertRgbaApprox(
        rgba: ByteArray, x: Int, y: Int,
        r: Int, g: Int, b: Int, a: Int,
        tag: String, tol: Int = 1,
    ) {
        val i = (y * W + x) * 4
        val ar = rgba[i].toInt() and 0xFF
        val ag = rgba[i + 1].toInt() and 0xFF
        val ab = rgba[i + 2].toInt() and 0xFF
        val aa = rgba[i + 3].toInt() and 0xFF
        val ok = abs(ar - r) <= tol && abs(ag - g) <= tol &&
            abs(ab - b) <= tol && abs(aa - a) <= tol
        assertTrue(ok) {
            "$tag at ($x, $y) : expected RGBA=($r, $g, $b, $a) +-$tol got ($ar, $ag, $ab, $aa)"
        }
    }

    /**
     * 4x4 image split into 4 quadrants : R / G / B / Bk. Duplicate of
     * [BitmapShaderPaintRectTest.makeQuadrantImage].
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

    private companion object {
        const val W: Int = 32
        const val H: Int = 32
        const val SIDE: Int = 4
    }
}
