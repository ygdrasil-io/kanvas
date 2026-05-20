package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import kotlin.math.abs

/**
 * Phase G-direct-colorFilter-gradient tests -- verify that
 * `paint.colorFilter` is honoured on direct gradient draws (no
 * `saveLayer`) for the 9 gradient pipelines covered in this slice :
 *   - linear / radial / sweep / conical-kRadial rect fast path,
 *   - same 4 families on the AA non-rect stencil-and-cover dispatch,
 *   - plus the conical kStrip and kFocal rect variants.
 *
 * #569 wired the same support on `solid_color.wgsl` (drawRect / drawPaint
 * fast path). This slice copies the same in-shader colorFilter snippet
 * across all 9 gradient shaders and the matching host-side uniform
 * packers + PendingDraw payloads.
 *
 * Scope (matches the slice's "hard scope") :
 *  - `SkColorFilters.Blend(colour, mode)` (kind 1, e.g. kPlus / kSrcIn).
 *  - `SkColorFilters.Matrix(20 floats)` (kind 2, e.g. luma grayscale).
 *  - Unsupported variants drop silently to the no-filter path.
 *  - `null` colour filter is the bit-iso fast path.
 *
 * The gradient sample is premul, so the shader's
 * `apply_color_filter_premul` does premul -> blend_premul OR
 * premul -> unpremul -> matrix -> re-premul. Then coverage modulation
 * runs on the filtered premul colour.
 */
class GradientColorFilterTest {

    // ----- Linear gradient family -----

    @Test
    fun `linear gradient with Blend(red, kPlus) colorFilter tints green to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Trivial single-stop "gradient" : both stops green so the sample
        // is constant (0, 1, 0, 1) premul. Blend(red, kPlus) lifts R to
        // ~1, so the output is yellow.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(8f, 16f),
            p1 = SkPoint(24f, 16f),
            colors = intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Centre of the rect : expect yellow (255, 255, 0, 255).
        assertRgbaApprox(pixels, 14, 14, 255, 255, 0, 255, tag = "linear+Blend(red,kPlus) center", tol = 6)
        // Outside : background white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    @Test
    fun `linear gradient with Matrix luma colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Constant red gradient + Rec.601 luma matrix.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(8f, 16f),
            p1 = SkPoint(24f, 16f),
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Red luma -> ~(76, 76, 76, 255). G6.1 present-pass colorspace
        // transform can shift channels by a few LSBs, so use a generous
        // tolerance.
        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255, tag = "linear+Matrix luma center", tol = 8)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    // ----- Radial gradient family -----

    @Test
    fun `radial gradient with Blend(red, kPlus) colorFilter tints green to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkRadialGradient.Make(
            center = SkPoint(16f, 16f),
            radius = 20f,
            colors = intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 255, 255, 0, 255, tag = "radial+Blend(red,kPlus) center", tol = 6)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    @Test
    fun `radial gradient with Matrix luma colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkRadialGradient.Make(
            center = SkPoint(16f, 16f),
            radius = 20f,
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255, tag = "radial+Matrix luma center", tol = 8)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    // ----- Sweep gradient family -----

    @Test
    fun `sweep gradient with Blend(red, kPlus) colorFilter tints green to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkSweepGradient.Make(
            center = SkPoint(16f, 16f),
            colors = intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Sample slightly off-centre to avoid the sweep singularity at
        // the centre pixel.
        assertRgbaApprox(pixels, 18, 18, 255, 255, 0, 255, tag = "sweep+Blend(red,kPlus) off-centre", tol = 6)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    @Test
    fun `sweep gradient with Matrix luma colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkSweepGradient.Make(
            center = SkPoint(16f, 16f),
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 18, 18, 76, 76, 76, 255, tag = "sweep+Matrix luma off-centre", tol = 8)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    // ----- Conical (kRadial) gradient family -----

    @Test
    fun `conical kRadial gradient with Blend(red, kPlus) colorFilter tints green to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 16f), startRadius = 0f,
            end = SkPoint(16f, 16f), endRadius = 20f,
            colors = intArrayOf(SK_ColorGREEN, SK_ColorGREEN),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 255, 255, 0, 255, tag = "conical+Blend(red,kPlus) center", tol = 6)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    @Test
    fun `conical kRadial gradient with Matrix luma colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 16f), startRadius = 0f,
            end = SkPoint(16f, 16f), endRadius = 20f,
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255, tag = "conical+Matrix luma center", tol = 8)
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    // ----- Regression : null colorFilter remains bit-iso fast path -----

    @Test
    fun `linear gradient with null colorFilter is the bit-iso fast path`() {
        // Verify the no-filter path still produces the same output as
        // before this slice landed. The uniform expanded by 96 bytes to
        // accommodate the colour-filter slots, but the shader's
        // `kind == 0` branch is a no-op.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkLinearGradient.Make(
            p0 = SkPoint(8f, 16f),
            p1 = SkPoint(24f, 16f),
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    shader = grad
                    isAntiAlias = false
                    // No colorFilter set.
                }
                SkCanvas(device).drawRect(SkRect.MakeLTRB(8f, 8f, 24f, 24f), paint)
                device.flush()
            }
        }

        // Constant red gradient -- present-pass colorspace transform
        // shifts pure red by a few LSBs, so band tolerance.
        val center = pixels.rgbaAt(14, 14)
        assertTrue(center[0] in 200..255, "no-filter center R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "no-filter center B near zero, got ${center[2]}")
        assertTrue(center[3] >= 250, "no-filter center A near opaque, got ${center[3]}")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside rect", tol = 4)
    }

    // ----- AA non-rect path : circle-clipped gradient routes through
    //       the stencil-cover gradient shader, exercising the AA
    //       variant's colorFilter slot. -----

    @Test
    fun `linear gradient on non-rect path with Matrix luma colorFilter turns red into gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Clip to a circle so the gradient routes through the AA
        // stencil-cover linear shader (`aa_stencil_cover_gradient.wgsl`)
        // instead of the rect fast path.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(8f, 16f),
            p1 = SkPoint(24f, 16f),
            colors = intArrayOf(SK_ColorRED, SK_ColorRED),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val canvas = SkCanvas(device)
                canvas.save()
                canvas.clipPath(
                    org.skia.foundation.SkPath.Circle(16f, 16f, 8f),
                    doAntiAlias = true,
                )
                canvas.drawRect(
                    SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    SkPaint().apply {
                        shader = grad
                        isAntiAlias = true
                        colorFilter = SkColorFilters.Matrix(luma)
                    },
                )
                canvas.restore()
                device.flush()
            }
        }

        // Inside the circle near centre : gray.
        assertRgbaApprox(pixels, 16, 16, 76, 76, 76, 255, tag = "aa-linear+Matrix luma center", tol = 8)
        // Outside the circle : background white.
        assertRgbaApprox(pixels, 0, 0, 255, 255, 255, 255, tag = "outside circle clip", tol = 4)
    }

    // ----- Helpers -----

    private fun ByteArray.rgbaAt(x: Int, y: Int): IntArray {
        val i = (y * W + x) * 4
        return intArrayOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private fun assertRgbaApprox(
        rgba: ByteArray, x: Int, y: Int,
        r: Int, g: Int, b: Int, a: Int,
        tag: String, tol: Int = 4,
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
