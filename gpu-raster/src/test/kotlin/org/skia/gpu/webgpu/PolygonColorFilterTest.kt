package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import kotlin.math.abs

/**
 * Phase G-direct-colorFilter (polygon closing slice) -- verify that
 * `paint.colorFilter` is honoured on `drawPath` for the 3 polygon /
 * stencil-cover pipelines :
 *
 *  - `solid_polygon.wgsl` (non-AA convex polygon fill + stencil-write
 *    + kWinding cover sub-pass).
 *  - `aa_polygon.wgsl` (AA convex single-contour polygon).
 *  - `aa_stencil_cover.wgsl` (AA multi-contour / concave, both
 *    `fs_inside` and `fs_outside`).
 *
 * Companion to [DrawRectColorFilterTest], which covers the
 * `solid_color.wgsl` rect / drawPaint fast path opened by #569. Same
 * filter dispatch table (kind 1 = Blend, kind 2 = Matrix, kind 0 =
 * no-op fast path) ; the polygon shaders share the host-side packer
 * (`packLayerCompositeColorFilter`) verbatim.
 *
 * Scope :
 *  - drawPath(convex shape) + paint.colorFilter Blend     -> tinted.
 *  - drawPath(concave path) + paint.colorFilter Matrix     -> matrix-filtered.
 *  - drawPath(multi-contour donut) + paint.colorFilter     -> multi-contour
 *    + filtered.
 *  - clipPath(circle) + drawPath + colorFilter              -> clipped +
 *    filtered (verifies the clip-shape modulation and the colour-filter
 *    modulation interact correctly).
 *  - drawPath with null colorFilter                         -> fast path
 *    bit-iso (regression guard for the uniform expansion 64 -> 160 and
 *    4176 -> 4272 bytes).
 */
class PolygonColorFilterTest {

    @Test
    fun `drawPath convex non-AA with Blend(red, kPlus) tints green to yellow`() {
        // Non-AA, convex, single-contour : routes through
        // `solid_polygon.wgsl`'s fan-tess fill + cover pass.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val square = SkPathBuilder()
            .moveTo(12f, 12f)
            .lineTo(40f, 12f)
            .lineTo(40f, 40f)
            .lineTo(12f, 40f)
            .close()
            .detach()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorGREEN
                    isAntiAlias = false
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                SkCanvas(device).drawPath(square, paint)
                device.flush()
            }
        }

        // Interior : Blend(red, kPlus) over green -> yellow (255, 255, 0, 255).
        assertRgbaApprox(pixels, 24, 24, 255, 255, 0, 255, tag = "convex non-AA center")
        assertRgbaApprox(pixels, 30, 30, 255, 255, 0, 255, tag = "convex non-AA center #2")
        // Outside : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside polygon")
    }

    @Test
    fun `drawPath convex AA with Matrix grayscale turns red into gray`() {
        // AA, convex, single-contour : routes through `aa_polygon.wgsl`
        // (perimeter-edge coverage min, premul fold). The grayscale
        // matrix is applied per-pixel before coverage modulation.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Pentagon : convex non-rect path that fan-tessellates cleanly.
        val pentagon = SkPathBuilder()
            .moveTo(32f, 8f)
            .lineTo(56f, 26f)
            .lineTo(48f, 54f)
            .lineTo(16f, 54f)
            .lineTo(8f, 26f)
            .close()
            .detach()
        val luma = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    isAntiAlias = true
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawPath(pentagon, paint)
                device.flush()
            }
        }

        // Deep interior pixels : Rec.601 luma of pure red = 0.299 * 255 = 76.
        // Coverage = 1.0 in the deep interior so the per-pixel output is
        // (76, 76, 76, 255).
        assertRgbaApprox(pixels, 32, 32, 76, 76, 76, 255, tag = "AA pentagon center", tol = 2)
        assertRgbaApprox(pixels, 28, 36, 76, 76, 76, 255, tag = "AA pentagon center #2", tol = 2)
        // Far outside : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside pentagon", tol = 2)
    }

    @Test
    fun `drawPath concave AA with Blend(blue, kSrcIn) replaces rgb but keeps alpha`() {
        // Concave AA single-contour : routes through
        // `aa_stencil_cover.wgsl` (stencil-and-cover with fs_inside +
        // fs_outside). The stencil pass writes winding ; the cover pass
        // applies the colour filter per fragment before the coverage
        // modulation.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Concave arrow / chevron : pulls in at the centre so the
        // single contour is concave. Fan tess does not preserve fill
        // area for concave shapes ; the dispatcher detects concavity
        // and routes through stencil-cover.
        val chevron = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(32f, 24f)
            .lineTo(54f, 10f)
            .lineTo(54f, 54f)
            .lineTo(32f, 40f)
            .lineTo(10f, 54f)
            .close()
            .detach()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    isAntiAlias = true
                    // Blend(blue, kSrcIn) : src = (0,0,1,1), dst = (1,0,0,1)
                    // (red premul) -> s * da = (0,0,1,1) -> opaque blue.
                    colorFilter = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kSrcIn)
                }
                SkCanvas(device).drawPath(chevron, paint)
                device.flush()
            }
        }

        // Deep interior pixels of the chevron : safely in the middle of
        // the left and right wings. The bottom edges from (10, 54) up to
        // (32, 40) and (54, 54) up to (32, 40) angle inward, so we pick
        // sample points in the deep-interior body (well above the bottom
        // notch and below the top notch's edges). Filter -> opaque blue,
        // SrcOver onto white -> blue.
        assertRgbaApprox(pixels, 20, 30, 0, 0, 255, 255, tag = "chevron left wing body", tol = 4)
        assertRgbaApprox(pixels, 44, 30, 0, 0, 255, 255, tag = "chevron right wing body", tol = 4)
        // The concave pocket near the top : (32, 16) is between the
        // two upper edges, OUTSIDE the chevron -> white background.
        assertRgbaApprox(pixels, 32, 16, 255, 255, 255, 255, tag = "chevron top pocket", tol = 2)
        // Far outside : white untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside chevron")
    }

    @Test
    fun `drawPath multi-contour donut AA with Matrix grayscale fills donut with gray`() {
        // Multi-contour AA : outer CW square + inner CCW square,
        // kWinding fill cancels in the hole. Routes through
        // `aa_stencil_cover.wgsl` (multi-contour stencil-and-cover).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val donut = SkPathBuilder()
            .moveTo(8f, 8f)
            .lineTo(56f, 8f)
            .lineTo(56f, 56f)
            .lineTo(8f, 56f)
            .close()
            // Inner hole, reverse winding.
            .moveTo(24f, 24f)
            .lineTo(24f, 40f)
            .lineTo(40f, 40f)
            .lineTo(40f, 24f)
            .close()
            .detach()
        val luma = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    isAntiAlias = true
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                SkCanvas(device).drawPath(donut, paint)
                device.flush()
            }
        }

        // Donut body : grayscale of red = ~76.
        assertRgbaApprox(pixels, 12, 32, 76, 76, 76, 255, tag = "donut left band", tol = 3)
        assertRgbaApprox(pixels, 52, 32, 76, 76, 76, 255, tag = "donut right band", tol = 3)
        assertRgbaApprox(pixels, 32, 12, 76, 76, 76, 255, tag = "donut top band", tol = 3)
        assertRgbaApprox(pixels, 32, 52, 76, 76, 76, 255, tag = "donut bottom band", tol = 3)
        // Inner hole (kWinding cancels) : white background visible.
        assertRgbaApprox(pixels, 32, 32, 255, 255, 255, 255, tag = "donut hole center", tol = 2)
        // Outside outer square : white background.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside donut")
    }

    @Test
    fun `clipPath circle plus drawPath plus Matrix grayscale composes clip and filter`() {
        // Composition test : clipPath captures a circle into the
        // SkClipShape ; drawPath of a square fills under it. The shaders
        // multiply per-pixel coverage by (clip_cov * filter_applied) so
        // both modulations stack correctly. Geometry : clip circle at
        // (32, 32, r=20) ; square covers (8..56) on both axes -- so the
        // square's centre sees clip = 1 and the corners are outside the
        // clip (white background visible).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val square = SkPathBuilder()
            .moveTo(8f, 8f)
            .lineTo(56f, 8f)
            .lineTo(56f, 56f)
            .lineTo(8f, 56f)
            .close()
            .detach()
        val luma = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.clipPath(SkPath.Circle(32f, 32f, 20f), doAntiAlias = false)
                val paint = SkPaint().apply {
                    color = SK_ColorRED
                    isAntiAlias = true
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                canvas.drawPath(square, paint)
                device.flush()
            }
        }

        // Centre of clip + inside square : filtered red -> gray (76).
        assertRgbaApprox(pixels, 32, 32, 76, 76, 76, 255, tag = "clip+filter center", tol = 3)
        // Inside clip circle, well-inside square : gray.
        assertRgbaApprox(pixels, 24, 32, 76, 76, 76, 255, tag = "clip+filter left of center", tol = 3)
        assertRgbaApprox(pixels, 32, 40, 76, 76, 76, 255, tag = "clip+filter below center", tol = 3)
        // Outside clip circle but inside square : clipped out (white).
        assertRgbaApprox(pixels, 10, 10, 255, 255, 255, 255, tag = "clipped corner", tol = 2)
        assertRgbaApprox(pixels, 54, 54, 255, 255, 255, 255, tag = "clipped opposite corner", tol = 2)
    }

    @Test
    fun `drawPath convex non-AA with null colorFilter is the fast path`() {
        // Regression : the uniform expanded from 64 to 160 bytes for the
        // non-AA polygon shader. Verify the no-filter path still produces
        // the same output (matches the pre-slice solid-polygon fill).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val square = SkPathBuilder()
            .moveTo(12f, 12f)
            .lineTo(40f, 12f)
            .lineTo(40f, 40f)
            .lineTo(12f, 40f)
            .close()
            .detach()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                // No colorFilter -> shader's kind == 0 fast path.
                val paint = SkPaint().apply {
                    color = SK_ColorBLUE
                    isAntiAlias = false
                }
                SkCanvas(device).drawPath(square, paint)
                device.flush()
            }
        }

        // Interior : blue (0, 0, 255, 255).
        assertRgbaApprox(pixels, 24, 24, 0, 0, 255, 255, tag = "null-filter interior", tol = 0)
        // Outside : white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside polygon", tol = 0)
    }

    @Test
    fun `drawPath convex AA with null colorFilter is the fast path`() {
        // Regression : the uniform expanded from 4176 to 4272 bytes for
        // the AA polygon shader. Verify the no-filter path still
        // produces the same output.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pentagon = SkPathBuilder()
            .moveTo(32f, 8f)
            .lineTo(56f, 26f)
            .lineTo(48f, 54f)
            .lineTo(16f, 54f)
            .lineTo(8f, 26f)
            .close()
            .detach()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorBLUE
                    isAntiAlias = true
                }
                SkCanvas(device).drawPath(pentagon, paint)
                device.flush()
            }
        }

        // Deep interior : blue. AA may give sub-pixel softening near
        // edges so we sample well inside.
        assertRgbaApprox(pixels, 32, 32, 0, 0, 255, 255, tag = "AA null-filter center", tol = 2)
        // Far outside : white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside pentagon", tol = 0)
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
        const val W: Int = 64
        const val H: Int = 64
    }
}
