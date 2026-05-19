package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import kotlin.math.abs

/**
 * Phase G-direct-colorFilter tests -- verify that `paint.colorFilter`
 * is honoured on direct draws (no `saveLayer`) for the solid-color
 * fast path (`drawRect` fill + `drawPaint`). #568 added the same
 * support on the layer-composite paint ; this slice extends it to the
 * primary draw entry points by extending `solid_color.wgsl`'s uniform
 * with the same 6-vec4f payload used by `layer_composite.wgsl`.
 *
 * Scope (matches the slice's "hard scope") :
 *  - `SkColorFilters.Blend(colour, mode)` (kind 1).
 *  - `SkColorFilters.Matrix(20 floats)` (kind 2).
 *  - Unsupported variants (Compose, Lerp, Table, sRGB-gamma, working-
 *    CS wrapper) drop silently to the no-filter path -- mirrors the
 *    saveLayer behaviour.
 *  - `null` colour filter is the bit-iso fast path (kind == 0, shader
 *    no-op).
 *
 * Out of scope (deferred follow-ups) :
 *  - Gradient / bitmap-shader / drawPath colourFilter -- need the
 *    Approach-B offscreen composite path or per-shader inline.
 *  - Stroke-style rects -- the stroke decomposes into 4 sub-rects that
 *    each hit `drawFillRect`, so the filter applies correctly on each
 *    sub-rect ; not exercised here.
 */
class DrawRectColorFilterTest {

    @Test
    fun `drawRect with Blend(red, kPlus) colorFilter tints green to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Direct drawRect (no saveLayer) with paint.colorFilter =
                // Blend(red, kPlus). The shader pre-filters the source
                // colour : blendPremul(src = (R=1,G=0,B=0,A=1) premul,
                //                     dst = (R=0,G=1,B=0,A=1) premul,
                //                     kPlus) = min(1, s+d) = (1,1,0,1).
                // SrcOver onto white -> yellow.
                val paint = SkPaint().apply {
                    color = SK_ColorGREEN
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 12, 12, 255, 255, 0, 255, tag = "Blend(red,kPlus) center", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 255, 0, 255, tag = "Blend(red,kPlus) center #2", tol = 2)
        // Outside : background white untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawRect with Blend(blue, kModulate) colorFilter masks green to black`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // colorFilter = Blend(blue, kModulate) on a green fill.
                //   src premul = (0,0,1,1) ; dst premul = (0,1,0,1)
                //   modulate = s * d = (0,0,0,1) -- opaque black.
                // SrcOver onto white -> opaque black.
                val paint = SkPaint().apply {
                    color = SK_ColorGREEN
                    colorFilter = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kModulate)
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 0, 0, 0, 255, tag = "Blend(blue,kModulate) center", tol = 2)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawRect with Blend(red, kSrcIn) replaces rgb but keeps source alpha`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Blend(red, kSrcIn) -- src = red premul, dst = green premul ;
                //   kSrcIn = s * da -- da = 1 since source is opaque.
                // Result : opaque red. SrcOver onto white -> red.
                val paint = SkPaint().apply {
                    color = SK_ColorGREEN
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "Blend(red,kSrcIn) center", tol = 2)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawRect with Matrix grayscale colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Rec.601 luma weights -- applied to opaque red yields
                // (0.299, 0.299, 0.299, 1) = (~76, ~76, ~76, 255).
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255, tag = "Matrix luma center", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 76, 76, 76, 255, tag = "Matrix luma center #2", tol = 2)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawRect with Matrix bias colorFilter shifts blue to white`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLUE)
                val canvas = SkCanvas(device)
                // Identity coefs + bias on R and G -- shifts blue
                // (0, 0, 1, 1) to (1, 1, 1, 1) = white.
                val biasOnly = floatArrayOf(
                    1f, 0f, 0f, 0f, 1f,
                    0f, 1f, 0f, 0f, 1f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val paint = SkPaint().apply {
                    color = SK_ColorBLUE
                    colorFilter = SkColorFilters.Matrix(biasOnly)
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 255, 255, 255, 255, tag = "Matrix bias center", tol = 2)
        assertRgbaApprox(pixels, 2, 2, 0, 0, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawRect with null colorFilter is the bit-iso fast path`() {
        // Regression -- the colour-filter slot expanded the rect
        // uniform from 80 to 176 bytes. Verify the no-filter path still
        // produces bit-iso output (matches the original rect-fill tests
        // like RectFillCrossTest / ClearRedTest).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Paint with NO colour filter -- the uniform ships the
                // identity payload, shader's kind == 0 fast path runs.
                val paint = SkPaint().apply { color = SK_ColorRED }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Bit-iso : same as pre-slice solid-color rect-fill.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "null-filter center", tol = 0)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 0)
    }

    @Test
    fun `drawRect with unsupported colorFilter (Compose) falls back to no-filter`() {
        // Phase G-direct-colorFilter -- variants outside (modeFilter /
        // matrixFilter) drop silently and draw as if no filter was set.
        // Compose(Matrix, Matrix) is a concrete unsupported case that
        // the host-side `asMatrixFilter` / `asBlendModeFilter` return
        // null for. Two identity matrices compose to identity so the
        // dropped-filter output matches what the supported path would
        // have produced -- the assertion exists primarily to catch a
        // buggy extractor that picks Compose up as something else.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val composed = SkColorFilters.Compose(
                    SkColorFilters.Matrix(identity),
                    SkColorFilters.Matrix(identity),
                )
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    colorFilter = composed
                }
                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Filter dropped -- same as no-filter SrcOver of red over white.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "Compose-fallback center")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect")
    }

    @Test
    fun `drawPaint with Matrix grayscale colorFilter fills viewport with gray`() {
        // drawPaint routes through drawRect (solid-colour fast path)
        // when paint.shader is null. Same uniform layout, same colour
        // filter logic.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

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
                    color = SK_ColorRED
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                canvas.drawPaint(paint)
                device.flush()
            }
        }

        // The whole viewport gets the grayscale of red.
        assertRgbaApprox(pixels, 1,  1,  76, 76, 76, 255, tag = "drawPaint corner", tol = 2)
        assertRgbaApprox(pixels, 15, 15, 76, 76, 76, 255, tag = "drawPaint center", tol = 2)
        assertRgbaApprox(pixels, 30, 30, 76, 76, 76, 255, tag = "drawPaint far corner", tol = 2)
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

    private companion object {
        const val W: Int = 32
        const val H: Int = 32
    }
}
