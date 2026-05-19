package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.foundation.asBlurImageFilter
import org.skia.foundation.asColorFilterImageFilter
import kotlin.math.abs

/**
 * Phase G-saveLayer-imageFilter scaffolding tests -- verify that
 * `SkCanvas.saveLayer(paint with imageFilter) + drawRect + restore`
 * either routes through the existing colour-filter pipeline (for the
 * supported `SkImageFilters.ColorFilter(cf, input = null)` wrap) or
 * raises a clear error for any other variant.
 *
 * **Scope (scaffolding slice).**
 *  - Routed : `SkImageFilters.ColorFilter(cf, input = null)` with `cf`
 *    one of the supported `SkColorFilters.Blend` / `SkColorFilters.
 *    Matrix` variants. The composite shader runs `cf` per pixel after
 *    the paintColor scale, mirroring `paint.colorFilter`.
 *  - Thrown : `SkImageFilters.Blur`, `SkImageFilters.Offset`,
 *    `SkImageFilters.DropShadow`, ColorFilter wrap with non-null child,
 *    ColorFilter wrap **and** `paint.colorFilter` set together. The
 *    error message names the variant + points at the follow-up slice.
 *
 * The unit tests render onto a raw sRGB-coded readback target
 * (`applyColorspaceTransform = false`), so RGB bytes are directly
 * comparable to the source colours -- same conventions as
 * [SaveLayerTest].
 */
class SaveLayerImageFilterTest {

    @Test
    fun `saveLayer with ColorFilter Matrix grayscale imageFilter turns red layer to gray`() {
        // Phase G-saveLayer-imageFilter -- the routed path. Wrap a
        // grayscale matrix colour filter into an SkImageFilter via
        // SkImageFilters.ColorFilter(cf, input = null) ; the GPU
        // backend unwraps and dispatches it through the same uniform
        // packing as paint.colorFilter. Pixel result is identical to
        // the SaveLayerTest's `Matrix grayscale` case.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val cf = SkColorFilters.Matrix(luma)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(cf, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : gray (~ 76, 76, 76, 255) -- bit-iso with the
        // direct paint.colorFilter Matrix grayscale test.
        assertRgbaApprox(
            pixels, 14, 14,
            76, 76, 76, 255,
            tag = "ImageFilter ColorFilter Matrix grayscale", tol = 2,
        )
        // Outside layer : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with ColorFilter Blend red kPlus imageFilter tints green layer to yellow`() {
        // Phase G-saveLayer-imageFilter -- routed path with the second
        // supported SkColorFilter variant (Blend(colour, mode)). Mirrors
        // SaveLayerTest's `Blend(red, kPlus) colorFilter` case but with
        // the filter expressed as an SkImageFilter wrap.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val cf = SkColorFilters.Blend(SK_ColorRED, org.skia.foundation.SkBlendMode.kPlus)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(cf, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Draw an opaque green rect : (0, 255, 0, 255).
                canvas.drawRect(layerBounds, SkPaint().apply {
                    color = (255 shl 24) or (0 shl 16) or (255 shl 8) or 0
                })
                canvas.restore()
                device.flush()
            }
        }

        // Filter math : red (255, 0, 0, 255) premul as src kPlus green
        // (0, 255, 0, 255) as dst -> (255, 255, 0, 255) yellow.
        // SrcOver composite onto white background : src.a == 255 ->
        // dst replaced by src -> (255, 255, 0, 255).
        assertRgbaApprox(
            pixels, 14, 14,
            255, 255, 0, 255,
            tag = "ImageFilter ColorFilter Blend red kPlus", tol = 2,
        )
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with null imageFilter and null colorFilter is the fast path`() {
        // Regression : the imageFilter detection must not break the
        // existing no-filter composite path. Both slots null ->
        // identity payload, same as SaveLayerTest's `null paint
        // colorFilter is a no-op` case.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint() // both filter slots null.
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Plain SrcOver of opaque red over white -> red inside layer.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "no-filter center")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Blur imageFilter throws clear error`() {
        // Phase G-saveLayer-imageFilter -- scaffolding raises for any
        // non-supported variant. Verify the error message names the
        // filter type and points at the follow-up slice rather than
        // silently dropping the filter.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 4f, sigmaY = 4f,
                        tileMode = SkTileMode.kDecal, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Blur imageFilter ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("Blur") && msg.contains("sigmaX = 4.0") && msg.contains("sigmaY = 4.0")) {
            "Error message should name the Blur filter + its sigmas ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with Offset imageFilter throws clear error`() {
        // Phase G-saveLayer-imageFilter -- another non-supported
        // variant. The fallback error path is the generic "not yet
        // supported" message that names the concrete class.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Offset imageFilter ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("SkOffsetImageFilter") || msg.contains("not yet supported")) {
            "Error message should surface the unsupported filter class ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with ColorFilter wrap and paint colorFilter both set throws clear error`() {
        // Phase G-saveLayer-imageFilter -- the composite shader's
        // colour-filter uniform is single-occupancy. Setting both
        // paint.colorFilter and paint.imageFilter = ColorFilter wrap
        // would need a Compose extractor that the scaffolding doesn't
        // ship -- surface the conflict early.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Matrix(identity)
                    imageFilter = SkImageFilters.ColorFilter(
                        SkColorFilters.Matrix(identity), input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for double colour filter ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("single-occupancy") || msg.contains("both")) {
            "Error message should explain the conflict ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with ColorFilter wrap input nonNull throws clear error`() {
        // Phase G-saveLayer-imageFilter -- the ColorFilter wrap's
        // `input` child means a structural transform runs upstream of
        // the per-pixel colour transform. The scaffolding pipeline
        // can't render-to-texture pre-pass yet -- surface the conflict.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(
                        SkColorFilters.Matrix(identity),
                        input = inner,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for ColorFilter wrap with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("input == null") || msg.contains("non-null child")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `asColorFilterImageFilter returns null for non-ColorFilter wrap`() {
        // Unit test of the extractor : Blur and Offset filters must
        // return null, not silently misinterpret as a ColorFilter wrap.
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )
        assertEquals(null, blur?.asColorFilterImageFilter()) {
            "Blur must not extract as ColorFilter wrap"
        }
        val offset = SkImageFilters.Offset(dx = 1f, dy = 0f, input = null)
        assertEquals(null, offset.asColorFilterImageFilter()) {
            "Offset must not extract as ColorFilter wrap"
        }
    }

    @Test
    fun `asBlurImageFilter returns null for non-Blur wrap`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(identity)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        assertEquals(null, wrap.asBlurImageFilter()) {
            "ColorFilter wrap must not extract as Blur"
        }
        val offset = SkImageFilters.Offset(dx = 1f, dy = 0f, input = null)
        assertEquals(null, offset.asBlurImageFilter()) {
            "Offset must not extract as Blur"
        }
    }

    @Test
    fun `asBlurImageFilter extracts sigmas and tileMode from Blur`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 2.5f, sigmaY = 1.25f, tileMode = SkTileMode.kClamp, input = null,
        )!!
        val params = blur.asBlurImageFilter()
        assertTrue(params != null) { "Blur must extract" }
        assertEquals(2.5f, params!!.sigmaX, "sigmaX")
        assertEquals(1.25f, params.sigmaY, "sigmaY")
        assertEquals(SkTileMode.kClamp, params.tileMode, "tileMode")
        assertEquals(null, params.input, "input")
    }

    @Test
    fun `asColorFilterImageFilter extracts colorFilter and input from ColorFilter wrap`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(identity)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        val params = wrap.asColorFilterImageFilter()
        assertTrue(params != null) { "ColorFilter wrap must extract" }
        assertEquals(cf, params!!.colorFilter, "inner colorFilter identity")
        assertEquals(null, params.input, "input must be null")
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
            "$tag at ($x, $y) : expected RGBA=($r, $g, $b, $a) ±$tol got ($ar, $ag, $ab, $aa)"
        }
    }

    private companion object {
        const val W: Int = 32
        const val H: Int = 32
    }
}
