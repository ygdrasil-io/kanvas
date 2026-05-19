package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkRect
import kotlin.math.abs

/**
 * Phase MaskFilter-blur acceptance tests -- `paint.maskFilter =
 * SkBlurMaskFilter(style, sigma)` on the GPU backend, for all four
 * [SkBlurStyle] variants : kNormal / kSolid / kOuter / kInner.
 *
 * The GPU path :
 *  1. Allocates a child layer device sized to the path bounds + 3*sigma
 *     padding.
 *  2. Renders the path white-tinted onto the child's intermediate.
 *  3. Drains the child via [SkWebGpuDevice.flushDrawsOnly].
 *  4. Enqueues a [SkWebGpuDevice.BlurredPathDraw] that the parent's
 *     flush replays as two render passes (H Gaussian blur on a scratch
 *     texture, then V Gaussian blur + style-driven composition + paint-
 *     colour modulation onto the parent's intermediate).
 *
 * The V pass binds both the H-pass scratch (the convolution source)
 * and the original shape mask (the sharp M(p)) -- the four styles
 * differ only in how M and B are combined per the formulas :
 *  - kNormal : B
 *  - kSolid  : min(M + B, 1)
 *  - kOuter  : max(B - M, 0)
 *  - kInner  : B * M
 *
 * Coverage in this suite :
 *  - `drawRect` + kNormal : soft edges, opaque centre, monotone decay.
 *  - `drawPath(circle)` + kNormal : edge-softness on a curved boundary.
 *  - paint.alpha modulation : halving alpha halves output alpha.
 *  - background bleed-through : far outside the path, untouched.
 *  - kSolid : interior stays fully solid, halo extends outward.
 *  - kOuter : interior transparent (B - M = 0), only outer halo.
 *  - kInner : blur clipped to interior, exterior fully transparent.
 *  - paint without maskFilter : sharp fill, dispatch gate doesn't fire.
 */
class MaskFilterBlurTest {

    @Test
    fun `drawRect with kNormal blur produces soft edges and opaque interior`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Rect [20, 20, 44, 44] (axis-aligned, 24x24) blurred with
        // sigma = 4 ; the blur radius is 12 pixels.
        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : still opaque black (the blurred mass at
        // the centre approaches the kernel sum on a saturated 24x24
        // interior, which is >>1 px from any edge for sigma = 4).
        val centre = pixels.rgbaAt(32, 32)
        // The output is premul SrcOver-on-white : (R, G, B, 255) where
        // R = G = B come from `1*(1-cov) + 0*cov = 1 - cov`. For a
        // fully-covered pixel cov = 1 -> bytes (0, 0, 0, 255).
        assertTrue(centre[0] < 20, "rect centre R should be near 0, got ${centre[0]}")
        assertTrue(centre[1] < 20, "rect centre G should be near 0, got ${centre[1]}")
        assertTrue(centre[2] < 20, "rect centre B should be near 0, got ${centre[2]}")

        // Far outside the blur extent : background white, untouched.
        // Sample (5, 5) -- 15 px outside the rect's left edge, well
        // past the 12-pixel blur radius.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5),
            "outside the blur extent : background")

        // Sample a row crossing the right edge at y = 32 (centre row).
        // The blur radius is 12 ; rect's right edge is at x = 44. So
        // x = 32 (12 px inside the edge) should be (nearly) opaque,
        // x = 44 (the edge) should be midway (greyish), x = 56 should
        // be (nearly) the background. The R/G/B luminance should
        // decrease monotonically along x as the coverage falls off.
        val luminanceProfile = IntArray(31) { dx ->
            val x = 30 + dx  // x = 30..60 (covers 14 px inside -> 16 px outside)
            pixels.rgbaAt(x, 32)[0]
        }
        // First sample (x = 30 ; 14 px inside the right edge) should be
        // near-opaque black (R close to 0) ; the last sample (x = 60 ;
        // 16 px outside the rect, > 1 radius beyond the edge) should be
        // very close to white.
        assertTrue(luminanceProfile[0] < 30,
            "x = 30 (inside rect) R should be near 0, got ${luminanceProfile[0]}")
        assertTrue(luminanceProfile[30] >= 250,
            "x = 60 (well past blur extent) R should be near 255, got ${luminanceProfile[30]}")

        // Monotonic non-decrease along the row from x = 30 to x = 60 :
        // the alpha gradient must be smooth. Allow up to 1 unit of
        // 8-bit quantisation noise.
        for (dx in 1 until luminanceProfile.size) {
            assertTrue(
                luminanceProfile[dx] >= luminanceProfile[dx - 1] - 1,
                "row profile must be (nearly) monotone : " +
                    "luminance[${dx - 1}] = ${luminanceProfile[dx - 1]}, " +
                    "luminance[$dx] = ${luminanceProfile[dx]}",
            )
        }

        // The edge transition has visible "soft" pixels in the
        // (rect right edge .. rect right edge + radius) band -- i.e.
        // x in [44, 56]. At least one pixel in that band must have a
        // luminance strictly between the interior (~0) and the bg (255).
        // Otherwise the blur isn't producing partial coverage.
        val edgeBand = (44..56).map { x -> pixels.rgbaAt(x, 32)[0] }
        val partials = edgeBand.count { it in 20..240 }
        assertTrue(partials >= 3,
            "expected at least 3 partially-covered pixels in the right-edge " +
                "blur band, got ${partials} (band luminances : $edgeBand)")
    }

    @Test
    fun `drawPath with circle and kNormal blur softens the boundary`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Circle of radius 16 centred at (32, 32), blurred with sigma 4.
        val sigma = 4f
        val cx = 32f; val cy = 32f; val r = 16f
        val circle = SkPath.Circle(cx, cy, r)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(circle, paint)
                device.flush()
            }
        }

        // Circle centre : near-opaque black.
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] < 30,
            "circle centre R should be near 0, got ${centre[0]}")

        // Far outside (top-left corner) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(2, 2),
            "outside the blur extent")

        // Radial profile along the +x axis from the centre. Boundary
        // sits at x = 48 (cx + r). Sample x = 32, 36, 40, 44, 48, 52,
        // 56, 60 -- the luminance must increase monotonically and
        // cross the bg threshold past the blur extent.
        val radialProfile = listOf(32, 36, 40, 44, 48, 52, 56, 60).map { x ->
            pixels.rgbaAt(x, cy.toInt())[0]
        }
        // Monotonic non-decrease.
        for (i in 1 until radialProfile.size) {
            assertTrue(
                radialProfile[i] >= radialProfile[i - 1] - 1,
                "radial profile must be (nearly) monotone : $radialProfile " +
                    "(violation at index $i)",
            )
        }
        // First sample (x = cx = 32) near-opaque, last (x = 60, 12 px
        // beyond the boundary at full sigma) near-background.
        assertTrue(radialProfile.first() < 30,
            "radial profile centre should be near 0, got ${radialProfile.first()}")
        assertTrue(radialProfile.last() >= 245,
            "radial profile far should be near 255, got ${radialProfile.last()}")
    }

    @Test
    fun `paint alpha modulates the blurred output proportionally`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)

        // Render the same rect twice with paint alpha = 255 (opaque)
        // and alpha = 128 (half) ; the blurred output's alpha should
        // halve, and the resulting SrcOver-on-white luminance should
        // move from ~0 (rect centre) to ~128 (half the background
        // bleeds through).
        val opaquePixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
                }
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
        // Use a fresh context for the second pass (avoid context reuse
        // semantics and keep the test self-contained).
        val context2 = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context2 != null, "No WebGPU adapter for second pass")
        val halfPixels = context2!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = (0x80 shl 24) or (SK_ColorBLACK and 0x00FFFFFF)
                    maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
                }
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre pixel of the rect, opaque pass : near-black.
        val opaqueCentre = opaquePixels.rgbaAt(32, 32)
        // Half-alpha pass : centre luminance shifts toward the
        // background (white). Expect ~128 +/- some quantisation.
        val halfCentre = halfPixels.rgbaAt(32, 32)
        assertTrue(opaqueCentre[0] < halfCentre[0] - 50,
            "half-alpha centre ($halfCentre) must be much brighter than " +
                "opaque centre ($opaqueCentre)")
        // Sanity : the half-alpha centre is in the 100-180 range
        // (the centre is near-saturated coverage, so halving alpha
        // gives roughly bg/2 = 127). Bounds are generous to absorb
        // F16 precision and any blur tail loss.
        assertTrue(
            halfCentre[0] in 100..180,
            "half-alpha centre R should be ~128, got ${halfCentre[0]}",
        )
    }

    @Test
    fun `drawRect with kSolid blur keeps interior fully solid and halos outward`() {
        // kSolid : output = min(M + B, 1). The sharp shape stays
        // fully opaque (M = 1 inside the rect), and a soft outer halo
        // (B > 0 outside) extends past the original edges. Inside the
        // rect there's NO partial-coverage band (kNormal would have
        // one near the inner edge as the kernel mass leaks out) -- M
        // re-saturates every interior pixel.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : fully opaque black (coverage = 1).
        val centre = pixels.rgbaAt(32, 32)
        assertEquals(listOf(0, 0, 0, 255), centre,
            "kSolid centre must be fully opaque")

        // Just inside the right edge (x = 43, the last rect column) :
        // still fully opaque under kSolid since M = 1. Under kNormal,
        // this column would already be partially transparent because
        // the blurred kernel is leaking the mass outward.
        val justInside = pixels.rgbaAt(43, 32)
        assertEquals(listOf(0, 0, 0, 255), justInside,
            "kSolid interior must stay fully solid up to the edge")

        // Just outside the right edge (x = 44) : partial coverage --
        // the halo starts. Should be partially transparent (luminance
        // between 0 and 255).
        val justOutside = pixels.rgbaAt(44, 32)
        assertTrue(justOutside[0] in 20..240,
            "kSolid halo at x = 44 must show partial coverage, got $justOutside")

        // Far outside (x = 60, 16 px past the edge) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kSolid far-outside must be background")
    }

    @Test
    fun `drawRect with kOuter blur shows halo only outside the shape`() {
        // kOuter : output = max(B - M, 0). Inside the shape M = 1
        // and B is also high (kernel is fully covered) so B - M = 0
        // -> the interior is transparent (background bleeds through).
        // Outside the shape M = 0 and B > 0 -> only the halo remains.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kOuter, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : transparent (B - M = 0). Background
        // bleeds through -> white.
        val centre = pixels.rgbaAt(32, 32)
        assertEquals(listOf(255, 255, 255, 255), centre,
            "kOuter centre must be background (halo subtracted by M)")

        // Just outside the right edge : halo present.
        val haloBand = (45..56).map { x -> pixels.rgbaAt(x, 32)[0] }
        val partials = haloBand.count { it in 20..240 }
        assertTrue(partials >= 3,
            "kOuter must have partial halo pixels outside the rect, " +
                "got band luminances $haloBand")

        // Far outside : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kOuter far-outside must be background")
    }

    @Test
    fun `drawRect with kInner blur shows blur clipped inside the shape`() {
        // kInner : output = B * M. Outside the shape (M = 0) the
        // output is 0 -> background. Inside the shape the blurred
        // coverage B is < 1 near the inner edges (the kernel partly
        // leaks past the boundary) -> a darker centre, brighter near
        // the inner edges (B falls off going inward toward 0 at the
        // edge of M).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kInner, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : near-opaque (M = 1, B saturated since
        // the 24x24 interior is wider than 2*radius = 24 for sigma 4,
        // borderline -- the centre tap mass is essentially full).
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] < 30,
            "kInner centre R should be near 0 (full B * 1), got ${centre[0]}")

        // Just outside the right edge (x = 44) : background. M = 0
        // there, so no contribution regardless of B.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(44, 32),
            "kInner just-outside must be background (clipped by M = 0)")

        // Far outside : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kInner far-outside must be background")

        // Inside the rect, near the right edge (x = 43, the last
        // interior column) : the blur kernel mostly straddles the
        // edge so B is roughly half. Output should be lighter than
        // the centre -- the inner falloff.
        val justInside = pixels.rgbaAt(43, 32)
        assertTrue(justInside[0] > centre[0] + 20,
            "kInner must fade toward the interior edge : justInside R " +
                "(${justInside[0]}) should exceed centre R (${centre[0]}) " +
                "by at least 20 luminance units")
    }

    @Test
    fun `blur draw with no maskFilter falls through to ordinary fill`() {
        // Sanity : the blur dispatch gate must NOT intercept a paint
        // without maskFilter. Regression guard for the dispatch's
        // early-return logic. Render a plain rect ; verify the result
        // matches the existing solid-fill behaviour (full alpha
        // everywhere inside, untouched background outside).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply { color = SK_ColorBLACK }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(32, 32),
            "no maskFilter : centre should be opaque black")
        // Edges sharp (no blur) : (44, 32) is just past the rect's
        // right edge ; non-AA rect doesn't visit it -> background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(44, 32),
            "no maskFilter : just past edge is unblurred background")
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
